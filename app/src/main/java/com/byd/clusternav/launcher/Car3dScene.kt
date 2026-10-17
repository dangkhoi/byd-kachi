package com.byd.clusternav.launcher

import android.view.Surface
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer

/**
 * ═══ 3.01 R25 — lớp Filament của widget xe 3D (chỉ vẽ; quyết định ở `:core` Car3d*) ═════════════════════════════
 *
 * 3.1.14 `PERF-SL6-3113` R3: lớp này CHỈ sống trên luồng sở hữu [Car3dRenderer.GL] ([Car3dOwnerThread] — Engine tạo ở đó, huỷ ở
 * đó, `createAsset`/`loadResources` ở đó); luồng chính nói chuyện qua tay cầm [Car3dRenderer]. Hàm dựng + [load] + [destroy] tự
 * kiểm luồng ([Car3dOwnerThread.check]) — gọi nhầm luồng ném (fail-loud ⇒ widget `FAULT`), không bao giờ âm thầm chạy trên luồng chính.
 * Câu HỎI về tài sản (neo · hộp · bản lề · tên) không đến đây: [load] đọc một lần thành [Car3dIndex] cho luồng chính; ở đây chỉ còn
 * LỆNH đổi cảnh (ẩn/hiện · biến đổi nút · sơn · phát sáng · camera) và vẽ, chạy theo đúng thứ tự tay cầm xếp.
 *
 * Cách làm (học CÁCH từ bộ vẽ xe của Kim/Kinex — Filament vào bề mặt, trần khung, ánh sáng hướng, tắt bóng — không
 * chép mã/tài sản): `TextureView` (widget nằm trong vùng làm việc có cuộn/chồng lớp ⇒ không SurfaceView) qua `UiHelper`;
 * nền TRONG SUỐT (thẻ kính của Kachi lộ ra sau xe); không bóng đổ, không SSAO, không bloom; ánh sáng = nền SH 1 dải dựng
 * bằng số (không cần `filament-utils`/KTX) + 2 đèn hướng. Mức LOW = không khử răng cưa · MEDIUM = FXAA.
 *
 * KHÔNG tự vẽ theo nhịp: chủ (widget) gọi [render] khi có thay đổi/hoạt cảnh, trần ~24 khung/giây ở chủ. [destroy] trả hết
 * tài nguyên GPU — gọi khi view rời cửa sổ.
 */
internal class Car3dScene private constructor(private val engine: Engine, medium: Boolean, night: Boolean) {
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view: View = engine.createView()
    private val camEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(camEntity)
    private var swap: SwapChain? = null
    private val materials = UbershaderProvider(engine)
    private val loader = AssetLoader(engine, materials, EntityManager.get())
    private val resources = ResourceLoader(engine)
    private var asset: FilamentAsset? = null
    private val lights = IntArray(3) { EntityManager.get().create() }
    private var ibl: IndirectLight? = null
    private var grading: ColorGrading? = null
    var width = 0; private set
    var height = 0; private set

    init {
        Car3dRenderer.GL.check()
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true; setClearColor(0.0, 0.0, 0.0, 0.0) }
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.setShadowingEnabled(false)
        aa(medium)
        camera.setExposure(16f, 1f / 125f, 100f)
        // Ánh xạ tông GIỮ MÀU (Khronos PBR Neutral): mặc định của Filament làm đèn đỏ/hổ phách sáng ngả trắng khi gần bão hoà
        // (owner 13/10: đèn đuôi "trắng cháy"). Màu nền ≤ ~0,8 đi qua gần như nguyên vẹn ⇒ sơn đúng màu tệp AVM.
        grading = ColorGrading.Builder().toneMapper(ToneMapper.PBRNeutral()).build(engine).also { view.colorGrading = it }
        lighting(night)
    }

    /**
     * Khử răng cưa. Owner 13/10 *"viền trên kính nhoè"* · *"đèn sau sáng nhoè cạnh"*: bản đầu = KHÔNG khử ở mức LOW (mọi đầu
     * xe) ⇒ cạnh mảnh tương phản cao (gờ kính đen trên thân trắng, dải đèn đuôi sáng) bậc thang + lấp lánh khi xoay; FXAA ở
     * MEDIUM thì làm MỜ cạnh (lọc ảnh sau). Nay MSAA 4× (mẫu hình học, không làm mờ kết cấu) ở mọi mức; chi phí đo
     * [ĐO máy ảo — xem diagnostics §5]. Chỉ tốn khi VẼ (rảnh = 0 khung).
     */
    private fun aa(@Suppress("UNUSED_PARAMETER") medium: Boolean) {
        view.antiAliasing = View.AntiAliasing.NONE
        view.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply { enabled = true; sampleCount = MSAA_SAMPLES }
    }

    /** Nền SH 1 dải (một màu trời đều) + nắng chéo trước-trái-trên + đèn phụ sau-phải. Đêm: dịu + lạnh hơn. */
    private fun lighting(night: Boolean) {
        val k = if (night) NIGHT_K else 1f
        ibl = IndirectLight.Builder().irradiance(1, if (night) floatArrayOf(0.85f, 0.9f, 1f) else floatArrayOf(1f, 1f, 1f))
            .intensity(IBL_LUX * k).build(engine)
        scene.indirectLight = ibl
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(1f, 0.98f, 0.95f).intensity(SUN_LUX * k)
            .direction(-0.5f, -0.8f, -0.4f).castShadows(false).build(engine, lights[0])
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(0.85f, 0.9f, 1f).intensity(FILL_LUX * k)
            .direction(0.6f, -0.3f, 0.6f).castShadows(false).build(engine, lights[1])
        // POLISH2 (owner 13/10 "mâm phía dưới mất màu"): ánh dội mặt đất — đèn yếu chiếu LÊN, mặt chúi xuống (nửa dưới mâm, mặt dưới
        // nắp cốp mở) không còn đen kịt dưới nắng + SH đều.
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(0.95f, 0.95f, 1f).intensity(BOUNCE_LUX * k)
            .direction(0.1f, 1f, 0.05f).castShadows(false).build(engine, lights[2])
        lights.forEach { scene.addEntity(it) }
    }

    /**
     * Nạp `.glb` (ByteBuffer trực tiếp) — CHỈ trên luồng [Car3dRenderer.GL]. `null` ⇒ tệp hỏng (chủ `FAULT` + bỏ đệm). Ảnh nhúng
     * giải mã trong `loadResources`. Xong thì đọc MỘT lần dữ liệu bất biến của mọi thực thể (JNI + `extras`) thành [Car3dIndex] —
     * luồng chính hỏi neo/hộp/chấm trên bảng đó, không nhảy luồng. Trả kèm số ms từng bước để chủ ghi log.
     */
    fun load(glb: ByteBuffer): Built? {
        Car3dRenderer.GL.check()
        val t0 = System.nanoTime()
        val a = loader.createAsset(glb) ?: return null
        val t1 = System.nanoTime()
        resources.loadResources(a)
        val t2 = System.nanoTime()
        a.releaseSourceData()
        scene.addEntities(a.entities)
        asset = a
        val idx = index(a)
        return Built(idx, Timing(ms(t0, t1), ms(t1, t2), ms(t2, System.nanoTime())))
    }

    class Built(val index: Car3dIndex, val timing: Timing)

    /** Số ms của một lượt [load] trên luồng [Car3dRenderer.GL]: `createAsset` · `loadResources` · đọc bảng tra. */
    data class Timing(val createMs: Long, val resourcesMs: Long, val indexMs: Long) {
        override fun toString() = "createAsset $createMs ms · loadResources $resourcesMs ms · index $indexMs ms"
    }

    /**
     * Bảng tra của tài sản. Hộp RIÊNG của bộ phận lấy từ `extras` (Car3dGlb ghi) — hộp của Filament = min/max của accessor CHUNG ⇒ mọi
     * bộ phận cùng bộ đỉnh trùng hộp [ĐO máy ảo 13/10]; không có `extras` (tệp glTF khác) ⇒ hộp của Filament.
     * ⚠ KHÔNG gọi `Box.set(...)`: [ĐO máy ảo 13/10 19:43] native `nSet` abort cả tiến trình HOME ("JNI DETECTED ERROR … invalid jobject").
     */
    private fun index(a: FilamentAsset): Car3dIndex {
        val rm = engine.renderableManager; val tm = engine.transformManager
        val box = Box(); val c = FloatArray(3); val h = FloatArray(3)
        val parts = a.renderableEntities.map { e ->
            val x = a.getExtras(e)
            val own = Car3dBox.parse(x)
            val aabb = if (own != null) null else {
                rm.getAxisAlignedBoundingBox(rm.getInstance(e), box); box.getCenter(c); box.getHalfExtent(h)
                floatArrayOf(c[0] - h[0], c[1] - h[1], c[2] - h[2], c[0] + h[0], c[1] + h[1], c[2] + h[2])
            }
            // Nút mang lưới không tên ⇒ tên nút cha.
            val name = a.getName(e)?.takeIf { it.isNotEmpty() } ?: tm.getParent(tm.getInstance(e)).let { p -> if (p != 0) a.getName(p) else null }
            Car3dIndex.Part(e, name, Car3dBox.group(x), own, aabb, Car3dHinge.parse(x), Car3dBox.material(x), Car3dBox.glow(x))
        }
        val bounds = a.boundingBox.let { b ->
            Car3dBounds(V3(b.centerX.toDouble(), b.centerY.toDouble(), b.centerZ.toDouble()), V3(b.halfExtentX.toDouble(), b.halfExtentY.toDouble(), b.halfExtentZ.toDouble()))
        }
        return Car3dIndex(parts, bounds)
    }

    // ── Lệnh đổi cảnh (tính ở [Car3dIndex] trên luồng chính, áp ở đây theo thứ tự) ───────────────────────────────────────────────

    /** Hiện/ẩn thực thể. */
    fun show(changes: Map<Int, Boolean>) { for ((e, on) in changes) if (on) scene.addEntity(e) else scene.remove(e) }

    /** Biến đổi cục bộ của nút (kính tụt · cửa/nắp quay). */
    fun transforms(m: Map<Int, FloatArray>) { val tm = engine.transformManager; for ((e, x) in m) tm.setTransform(tm.getInstance(e), x) }

    /** Đặt gốc mô hình (ma trận cột-chính từ [Car3dOrient.Placement.matrix]). */
    fun place(matrix: FloatArray) {
        val a = asset ?: return
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(a.root), matrix)
    }

    /**
     * Màu sơn (tuyến tính 0..1) cho mọi primitive của thực thể sơn ([Car3dIndex.paintTargets] — theo NÚT `extras.mat`: thân, cửa đóng,
     * CÁNH CỬA MỞ cùng nhận màu; bản lọc theo tên vật liệu gltfio để cửa giữ màu cũ [ĐO máy ảo 13/10]). Đổi tại chỗ — không dựng lại GLB.
     */
    fun paint(ids: List<Int>, rgb: List<Double>) {
        val rm = engine.renderableManager
        for (e in ids) {
            val inst = rm.getInstance(e)
            for (p in 0 until rm.getPrimitiveCount(inst)) rm.getMaterialInstanceAt(inst, p).setParameter("baseColorFactor", rgb[0].toFloat(), rgb[1].toFloat(), rgb[2].toFloat(), 1f)
        }
    }

    /**
     * POLISH2 — đèn khoang: đổi `emissiveFactor` tại chỗ. Giá trị KHI BẬT là dữ liệu `:core` theo đúng bản đã dựng (`extras.glow`,
     * 3.1.7 §15). [ĐO máy ảo 13/10] `MaterialInstance.material` của thực thể gltfio là null ⇒ không hỏi hasParameter được; nút có
     * `extras.glow` thì vật liệu KHAI `emissiveFactor` trong GLB ([Car3dGlb]) ⇒ có tham số.
     */
    fun glow(values: Map<Int, FloatArray>) {
        val rm = engine.renderableManager
        for ((e, k) in values) {
            val inst = rm.getInstance(e)
            for (p in 0 until rm.getPrimitiveCount(inst)) rm.getMaterialInstanceAt(inst, p).setParameter(EMISSIVE, k[0], k[1], k[2])
        }
    }

    /** Đổi ánh sáng ngày/đêm tại chỗ (không dựng lại engine). */
    fun night(night: Boolean) {
        val k = if (night) NIGHT_K else 1f
        ibl?.setIntensity(IBL_LUX * k)
        val lm = engine.lightManager
        lm.setIntensity(lm.getInstance(lights[0]), SUN_LUX * k)
        lm.setIntensity(lm.getInstance(lights[1]), FILL_LUX * k)
        lm.setIntensity(lm.getInstance(lights[2]), BOUNCE_LUX * k)
    }

    fun setCamera(c: Car3dCamera) {
        if (width <= 0 || height <= 0) return
        camera.setProjection(c.fovDeg, width.toDouble() / height, c.near, c.far, Camera.Fov.VERTICAL)
        val e = c.eye; val t = c.target
        camera.lookAt(e.x, e.y, e.z, t.x, t.y, t.z, 0.0, 1.0, 0.0)
    }

    /** Một khung. `false` = chưa vẽ được (bề mặt chưa sẵn / Filament bỏ khung) ⇒ tay cầm xin khung lại. */
    fun render(frameTimeNanos: Long): Boolean {
        val s = swap ?: return false
        if (!renderer.beginFrame(s, frameTimeNanos)) return false
        renderer.render(view)
        renderer.endFrame()
        return true
    }

    /** Bề mặt mới của TextureView (tay cầm nhận từ `UiHelper` trên luồng chính rồi chuyển sang đây). */
    fun createSwapChain(surface: Surface, flags: Long) {
        swap?.let { engine.destroySwapChain(it) }
        swap = engine.createSwapChain(surface, flags)
    }

    /** Bề mặt sắp mất: huỷ swap chain + chờ GPU xong TRƯỚC khi luồng chính trả bề mặt. */
    fun dropSwapChain() {
        swap?.let { engine.destroySwapChain(it); engine.flushAndWait() }
        swap = null
    }

    fun viewport(w: Int, h: Int) {
        width = w; height = h
        view.viewport = Viewport(0, 0, w, h)
    }

    /**
     * Trả hết GPU, MỘT lần (chủ bỏ tham chiếu ngay sau). Thứ tự: bề mặt → tài sản (thực thể + vật liệu con) → bộ cấp vật liệu
     * gltfio → đèn/nền → renderer/view/scene/camera → Engine. `Engine.destroy` nằm trong `finally`: một bước giữa chừng ném lỗi
     * Java thì Engine VẪN được huỷ — Engine tự giải phóng mọi tài nguyên người dùng còn sót (tài liệu `Engine.destroy` 1.77:
     * *"frees any user-allocated resources not already destroyed (logs a warning)"*), không giữ ngữ cảnh GL + luồng driver mồ côi.
     */
    fun destroy() {
        Car3dRenderer.GL.check()
        try {
            swap?.let { engine.destroySwapChain(it) }; swap = null
            asset?.let { loader.destroyAsset(it) }; asset = null
            materials.destroyMaterials(); materials.destroy()
            resources.destroy(); loader.destroy()
            lights.forEach { engine.destroyEntity(it); EntityManager.get().destroy(it) }
            ibl?.let { engine.destroyIndirectLight(it) }
            grading?.let { engine.destroyColorGrading(it) }
            engine.destroyRenderer(renderer); engine.destroyView(view); engine.destroyScene(scene)
            engine.destroyCameraComponent(camEntity); EntityManager.get().destroy(camEntity)
        } finally {
            engine.destroy()
        }
    }

    companion object {
        /**
         * Dựng cảnh — CHỈ trên luồng [Car3dRenderer.GL] (luồng này thành luồng của Engine: `Engine.cpp:307,341`). Soát Pass 2: hàm
         * dựng ném lỗi giữa chừng (vật liệu/IBL) ⇒ trước đây Engine (ngữ cảnh GL + luồng driver) mồ côi. Nay huỷ Engine rồi ném tiếp.
         */
        fun create(medium: Boolean, night: Boolean): Car3dScene {
            Car3dRenderer.GL.check()
            val e = Engine.create()
            return try { Car3dScene(e, medium, night) } catch (t: Throwable) { runCatching { e.destroy() }; throw t }
        }

        private fun ms(a: Long, b: Long) = (b - a) / 1_000_000

        /** [ĐO máy ảo 13/10] 30k/90k/30k ⇒ thân xe trắng "cháy", mất nếp gấp ⇒ hạ còn ~60 %. */
        const val IBL_LUX = 20_000f
        const val SUN_LUX = 55_000f
        const val FILL_LUX = 15_000f
        const val BOUNCE_LUX = 25_000f
        const val NIGHT_K = 0.7f
        const val MSAA_SAMPLES = 4
        private const val EMISSIVE = "emissiveFactor"
    }
}
