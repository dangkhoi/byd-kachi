package com.byd.clusternav.launcher

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Filament
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Gltfio
import java.nio.ByteBuffer

/**
 * ═══ 3.1.14 `PERF-SL6-3113` R3 — TAY CẦM luồng chính của bộ vẽ xe 3D; Filament sống trên luồng riêng [GL] ([Car3dScene]) ══════
 *
 * [ĐO log Seal 17/10, 3.1.11] trước bản này `Engine.create` + gltfio `createAsset`/`loadResources` (GLB 29,5 MB) chạy TRÊN LUỒNG
 * CHÍNH: 788–1509 ms mỗi lượt dựng ⇒ `Choreographer: Skipped 62–139 frames`. Nay luồng chính KHÔNG BAO GIỜ chờ car3d-gl làm việc nặng:
 *  • [create] chỉ tạo tay cầm (µs) và XẾP việc dựng Engine + cảnh vào [GL]; [load] xếp `createAsset` + `loadResources` + đọc bảng tra
 *    [Car3dIndex] vào [GL] rồi báo về luồng chính bằng `post`.
 *  • Câu HỎI (neo · hộp · đích chạm · bản lề · tên) trả lời trên luồng chính từ [Car3dIndex] — không JNI, không nhảy luồng. [ĐO máy ảo
 *    2 lõi 17/10] bản nhảy-đồng-bộ-mỗi-câu: 201 bước · 234 ms chờ khi dựng neo; 102 bước · 136 ms chờ cho 2 khung.
 *  • LỆNH đổi cảnh (ẩn/hiện · biến đổi nút · sơn · phát sáng · camera) và VẼ: tính ở [Car3dIndex], xếp vào [GL] theo đúng thứ tự,
 *    không chờ. Lệnh vẽ gộp ([drawQueued]/[drawAgain]): không bao giờ quá một khung xếp hàng; đổi cảnh sau khung đang chờ ⇒ vẽ thêm
 *    một khung cuối. Filament bỏ khung / chưa có bề mặt ⇒ xin khung lại qua `onSize` (tối đa [MAX_MISSES] lần liền).
 *  • Chưa nạp xong ⇒ mọi câu hỏi/lệnh trả rỗng NGAY. Widget giữ khung trống (ảnh nền — không bao giờ bảng 2D, 3.1.8) tới khi có xe.
 *  • Nạp xong ⇒ mới gắn `UiHelper` vào TextureView. Callback bề mặt là chỗ DUY NHẤT luồng chính chờ [GL] (đổi/huỷ swap chain —
 *    bề mặt chỉ được trả sau khi Filament thả nó) — lúc đó [GL] chỉ còn khung vẽ nhỏ trong hàng.
 *  • [destroy]: gỡ `UiHelper`, phần còn lại (tài sản · Engine) xếp vào [GL], xong mới gọi `onFreed` (trả trang cho hệ). Huỷ giữa
 *    lúc đang dựng ⇒ việc dựng còn xếp hàng tự bỏ ([dead]).
 *
 * Một [GL] cho cả tiến trình: Engine cũ huỷ và Engine mới dựng nối tiếp trên cùng hàng đợi (không bao giờ hai lượt dựng song song,
 * cùng luật một Engine của [Car3dSeat]).
 */
internal class Car3dRenderer private constructor(
    private val tv: TextureView, private var night: Boolean, private val onSize: (Int, Int) -> Unit,
) : UiHelper.RendererCallback {
    private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    /** CHỈ luồng [GL] đọc/ghi. */
    private var s: Car3dScene? = null
    private var initError: Throwable? = null
    /** Đã [destroy] — việc [GL] còn xếp hàng bỏ qua, kết quả nạp không báo về. */
    @Volatile private var dead = false
    /** Luồng chính: bảng tra của tài sản đã nạp; `null` = chưa sẵn (đang dựng / đã huỷ). */
    private var idx: Car3dIndex? = null
    /** Luồng chính: đã có một khung xếp hàng trên [GL] / có đổi cảnh sau khung đó. */
    private var drawQueued = false
    private var drawAgain = false
    private var misses = 0
    var width = 0; private set
    var height = 0; private set

    /** Xếp một lệnh đổi cảnh vào [GL] (không chờ); khung đang chờ sẽ không thấy lệnh này ⇒ đánh dấu vẽ thêm. */
    private fun cmd(f: (Car3dScene) -> Unit) { if (drawQueued) drawAgain = true; GL.post { if (!dead) s?.let(f) } }

    /**
     * Nạp `.glb` trên [GL]; [done] chạy trên luồng chính với số ms từng bước (`null` = gltfio từ chối tệp — chủ `FAULT` + bỏ đệm;
     * lỗi = Engine/cảnh không dựng được hoặc nạp ném). Không gọi [done] nếu tay cầm đã [destroy].
     */
    fun load(glb: ByteBuffer, variants: List<Car3dVariant>, done: (Result<Car3dScene.Timing?>) -> Unit) {
        GL.post {
            if (dead) return@post
            val res = runCatching {
                val sc = s ?: throw IllegalStateException("Filament init failed: $initError", initError)
                KachiPerf.add(KachiPerf.Counter.CAR3D_BUILD)   // PERF-SL6-3113 R2: một lượt dựng
                sc.load(glb)?.also { it.index.prepare(variants) }   // bảng nút ↔ biến thể dựng ở đây, không trên luồng chính
            }
            MAIN.post { if (dead) return@post; res.getOrNull()?.let { attach(it.index) }; done(res.map { it?.timing }) }
        }
    }

    /** Luồng chính, sau khi nạp xong: nhận bảng tra rồi mới gắn bề mặt. */
    private fun attach(index: Car3dIndex) {
        idx = index
        val n = night; cmd { it.night(n) }   // đổi ngày/đêm trong lúc dựng ⇒ áp lại
        ui.renderCallback = this
        ui.isOpaque = false
        ui.attachTo(tv)
        // PERF 3.1.5 (cổng sống — dựng lại khi widget HIỆN lại): [ĐO bytecode filament-android 1.77.3 `UiHelper$TextureViewHandler.<init>`]
        // bề mặt CÓ SẴN ⇒ `onSurfaceTextureAvailable(st, mDesiredWidth, mDesiredHeight)` = 0×0 khi không `setDesiredSize` ⇒ viewport 0,
        // camera không đặt ⇒ ô trống ([ĐO máy ảo 14/10] 3/4 lượt rời-HOME-rồi-về). Không `setDesiredSize` (nó ghim cỡ đệm khi ô đổi cỡ).
        if (tv.isAvailable && width == 0 && tv.width > 0 && tv.height > 0) onResized(tv.width, tv.height)
    }

    // ── Câu hỏi: bảng tra trên luồng chính ─────────────────────────────────────────────────────────────────────────────────────
    fun visibleBounds(): Car3dBounds? = idx?.visibleBounds()
    fun partNames(): List<String> = idx?.partNames().orEmpty()
    fun anchor(a: Car3dAnchor?): V3? = idx?.anchor(a)
    fun worldBox(pl: Car3dOrient.Placement, a: Car3dAnchor, posed: Boolean = false): Pair<V3, V3>? = idx?.worldBox(pl, a, posed)
    fun target(pl: Car3dOrient.Placement, h: Car3dHotspot, a: V3, posed: Boolean): Car3dPick.Target? = idx?.target(pl, h, a, posed)
    fun hasPart(pattern: String): Boolean = idx?.hasPart(pattern) == true
    fun hasHinge(pattern: String): Boolean = idx?.hasHinge(pattern) == true

    // ── Lệnh: tính trên luồng chính, áp trên [GL]. `true` = có đổi (chủ xin vẽ lại) ─────────────────────────────────────────────
    fun paint(rgb: List<Double>): Boolean {
        val ids = idx?.paintTargets()?.takeIf { it.isNotEmpty() } ?: return false
        cmd { it.paint(ids, rgb) }; return true
    }
    fun night(n: Boolean) { night = n; if (idx != null) cmd { it.night(n) } }
    fun glow(on: Boolean): Boolean { val v = idx?.glow(on) ?: return false; cmd { it.glow(v) }; return true }
    fun slide(pattern: String, fraction: Float): Boolean = transforms(idx?.slide(pattern, fraction))
    fun swing(pattern: String, s: Float): Boolean = transforms(idx?.swing(pattern, s))
    private fun transforms(m: Map<Int, FloatArray>?): Boolean { if (m.isNullOrEmpty()) return false; cmd { it.transforms(m) }; return true }
    fun place(matrix: FloatArray) { if (idx != null) cmd { it.place(matrix) } }
    /** Hiện/ẩn theo biến thể cửa/đèn ([Car3dIndex.showVariants] — bảng dựng sẵn lúc nạp). */
    fun showVariants(variants: List<Car3dVariant>, open: List<Boolean>): Boolean {
        val c = idx?.showVariants(variants, open)?.takeIf { it.isNotEmpty() } ?: return false
        cmd { it.show(c) }; return true
    }
    fun setCamera(c: Car3dCamera) { if (idx != null) cmd { it.setCamera(c) } }

    /**
     * Xếp một khung (không chờ). `false` = chưa vẽ được (chưa nạp / bề mặt chưa sẵn) ⇒ chủ thử lại khung sau. Đã có khung trong hàng
     * ⇒ không xếp thêm (gộp); lệnh đổi cảnh đến sau khung đó ⇒ khung đó vẽ xong tự xếp thêm một khung ([drawAgain]).
     */
    fun render(frameTimeNanos: Long): Boolean {
        if (idx == null || !ui.isReadyToRender) return false
        if (drawQueued) return true
        drawQueued = true; drawAgain = false
        GL.post {
            // Soát Pass 15 (P1): khung ném (Filament/JNI) mà không về MAIN ⇒ [drawQueued] kẹt `true` mãi ⇒ xe đứng hình IM LẶNG (mọi
            // lệnh vẽ sau đều "gộp" vào khung không bao giờ xong). Lỗi ⇒ coi là một khung hụt (xin lại ≤ [MAX_MISSES]), log E.
            val ok = try { !dead && s?.render(frameTimeNanos) == true } catch (t: Throwable) { Log.e(TAG, "khung trên ${Car3dOwnerThread.NAME} hỏng", t); false }
            MAIN.post {
                drawQueued = false
                if (dead) return@post
                if (ok) misses = 0 else if (++misses <= MAX_MISSES) { onSize(width, height); return@post }   // Filament bỏ khung ⇒ xin lại
                if (drawAgain) render(System.nanoTime())
            }
        }
        return true
    }

    override fun onNativeWindowChanged(surface: Surface) {
        val flags = ui.swapChainFlags
        GL.call { s?.createSwapChain(surface, flags) }
        // 3.1.5 [ĐO máy ảo 14/10, 2/2]: tắt/bật màn ⇒ bề mặt dựng lại CÙNG cỡ ⇒ không có onResized ⇒ không ai xin khung ⇒ xe
        // không vẽ (lớp phủ vẫn hiện) tới khi vuốt. Bề mặt mới ⇒ xin khung như lúc đổi cỡ.
        if (width > 0 && height > 0) onSize(width, height)
    }

    override fun onDetachedFromSurface() { GL.call { s?.dropSwapChain() } }

    override fun onResized(w: Int, h: Int) {
        width = w; height = h
        cmd { it.viewport(w, h) }
        Log.i(TAG, "surface ${w}x$h px")
        onSize(w, h)
    }

    /**
     * Trả hết GPU, MỘT lần (chủ bỏ tham chiếu ngay sau). Luồng chính chỉ gỡ `UiHelper` (bề mặt); tài sản → vật liệu → Engine huỷ trên
     * [GL] ([Car3dScene.destroy], `Engine.destroy` trong `finally`), xong thì [onFreed] trên luồng chính.
     */
    fun destroy(onFreed: () -> Unit) {
        if (dead) return
        dead = true
        if (idx != null) { idx = null; ui.detach() }
        GL.post { try { s?.destroy() } finally { s = null; MAIN.post(onFreed) } }
    }

    companion object {
        private const val TAG = "Car3d"
        /** Filament bỏ khung liền chừng này lần ⇒ thôi xin lại (bề mặt chết — chủ xin khi có sự kiện mới). */
        private const val MAX_MISSES = 48
        private val MAIN = Handler(Looper.getMainLooper())

        /** Luồng sở hữu Filament của cả tiến trình ([Car3dOwnerThread]). */
        val GL = Car3dOwnerThread(Car3dOwnerThread.NAME) { Log.e(TAG, "việc trên ${Car3dOwnerThread.NAME} hỏng", it) }

        /**
         * Dựng tay cầm NGAY (luồng chính, không Engine) và xếp việc dựng Engine + cảnh vào [GL]. Dựng hỏng ⇒ [load] báo lỗi (chủ
         * `FAULT` + thử lại có lùi dần). Soát Pass 2: Engine không bao giờ mồ côi — [Car3dScene.create] huỷ Engine khi hàm dựng ném.
         */
        fun create(tv: TextureView, medium: Boolean, night: Boolean, onSize: (Int, Int) -> Unit): Car3dRenderer {
            val t0 = System.nanoTime()
            val r = Car3dRenderer(tv, night, onSize)
            GL.post {
                if (r.dead) return@post
                val g0 = System.nanoTime()
                runCatching { Car3dScene.create(medium, night) }
                    .onSuccess { r.s = it; Log.i(TAG, "Engine + cảnh ${(System.nanoTime() - g0) / 1_000_000} ms (luồng ${Car3dOwnerThread.NAME})") }
                    .onFailure { r.initError = it; Log.w(TAG, "Filament init hỏng", it) }
            }
            mainMs(t0)
            return r
        }

        /** Cộng ms luồng chính từ [t0] vào [KachiPerf] (`3D-dựng … (luồng-chính M ms)`). */
        fun mainMs(t0: Long) = KachiPerf.add(KachiPerf.Counter.CAR3D_MAIN_MS, (System.nanoTime() - t0) / 1_000_000)

        /** Nạp thư viện native một lần/tiến trình; hỏng ⇒ `false` (widget: không khả thi). */
        @Volatile private var libs: Boolean? = null
        fun init(): Boolean = libs ?: synchronized(this) {
            libs ?: runCatching { Filament.init(); Gltfio.init(); true }.getOrElse { false }.also { libs = it }
        }
    }
}
