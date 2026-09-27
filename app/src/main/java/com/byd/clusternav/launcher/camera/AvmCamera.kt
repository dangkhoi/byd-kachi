package com.byd.clusternav.launcher.camera

import android.util.Log
import android.view.Surface
import java.io.File

/**
 * ═══ AVM CAMERA — lấy hình camera THẬT đổ vào một [Surface] (RE kinex `b1/RunnableC0170d`) ═══════════════════
 *
 * kinex KHÔNG dùng LVDS/`BYDAutoPanoramaDevice` để hiện camera trong app — nó dùng **`android.hardware.AVMCamera`**
 * (nạp từ `/system/framework/bmmcamera.jar`) và đổ preview thẳng vào một `Surface` của chính app:
 * ```
 *   cam = AVMCamera.open(cameraId)   (hoặc new AVMCamera(cameraId))
 *   cam.setCameraFps(15)
 *   cam.addPreviewSurface(surface, mode)   // thử mode 0..3 tới khi được
 *   cam.startPreview()
 *   … cam.stopPreview(); cam.close()
 * ```
 * Đây là lý do LVDS-vào-SurfaceView (cách cũ) ra ĐEN: không có API nào ĐƯA frame vào surface. `addPreviewSurface`
 * mới là đường đó — không cần quyền hệ thống (kinex cũng app thường).
 *
 * Toàn bộ reflection + `runCatching`: off-car / trim không có `AVMCamera` ⇒ mọi hàm no-op, không crash.
 */
internal class AvmCamera(private val classLoaderDex: Boolean = true) {

    private var cam: Any? = null
    private var cls: Class<*>? = null

    /** Nạp lớp AVMCamera (thử addDexPath bmmcamera.jar như kinex nếu classloader thường không thấy). */
    private fun loadClass(): Class<*>? = cls ?: runCatching {
        runCatching { return Class.forName(FQN) }
        if (classLoaderDex) runCatching {
            val cl = javaClass.classLoader
            cl?.javaClass?.getMethod("addDexPath", String::class.java)?.invoke(cl, DEX)
        }
        runCatching { Class.forName(FQN) }.getOrNull()
            ?: ClassLoader.getSystemClassLoader().loadClass(FQN)
    }.getOrNull()?.also { cls = it }

    /**
     * Surface + kênh xem đã đi qua `addPreviewSurface` ở lượt [open] — chỉ để [close] gọi `rmPreviewSurface` cho
     * đúng cặp.
     *
     * ⚠ [SOÁT Opus 2026-09-27] Ở nhánh AUTO, vòng dò **bỏ qua** giá trị trả về (đường 2.73, không được đổi) ⇒ đây là
     * *"đã gọi, không ném"*, **không** phải *"HAL đã nhận"*. Chú thích cũ nói sai vế ấy.
     */
    private var added: Pair<Surface, Int>? = null

    /**
     * ═══ [P1 · SOÁT Opus 2026-09-27] Bước 3 khi dỡ (`rmPreviewSurface`) là một **móc ĐO**, phải có cổng ═════════
     *
     * `false` (mặc định) ⇒ chuỗi dỡ **y 2.73 từng lời gọi**: `stopPreview` → `close`, hết.
     *
     * Vì sao một cổng, khi bước này đã ở CUỐI chuỗi: spec 2.74 R-nf1 nói *"mọi cái mới đứng sau chip, mặc định =
     * hành vi 2.73"*, mà đây là lời gọi HAL **thứ ba** thêm vào một chuỗi đang chạy ngoài hiện trường. Và
     * `android.hardware.AVMCamera` **không có** trong workspace ([ĐO] 0 hit `AVMCamera*` — chỉ có các lớp bọc
     * `DiLink*`) ⇒ hành vi của nó SAU `close()` là **[CHƯA BIẾT]**, đúng thứ CLAUDE.md §3 cấm ship. Một lỗi native ở
     * đó không phải `Throwable` nên `runCatching` dưới kia không bắt được, và [close] chạy trên **luồng main** ⇒ hậu
     * quả xấu nhất là launcher chết giữa lúc xe đang lăn bánh.
     *
     * Cổng là **chế độ kiểm thử đang mở** chứ không phải một khoá prefs mới: đây là móc ĐO, và mọi bề mặt đo của
     * Kachi đã nằm sau đúng cái cổng 60 phút ấy (bật bằng tay trong Cài đặt, không có đường bật từ xa). Nhờ thế
     * buổi xe vẫn đọc được dòng `rmPreviewSurface(mode=…) rc=…` mà runbook CAM-A5 hứa, còn xe của owner lúc chạy
     * bình thường thì **không bao giờ** gọi tới nó — và không phải thêm một khoá nào vào danh sách trắng.
     */
    var rmOnClose: Boolean = false

    /**
     * Mở camera [cameraId] + đổ preview vào [surface]. Trả true nếu startPreview OK (một mode nào đó nhận surface).
     *
     * [halMode] = **kênh xem** truyền cho `addPreviewSurface(Surface, int)`:
     *  • [CameraSignalPolicy.HAL_MODE_AUTO] (mặc định) ⇒ **dò `0..3` y 2.73**, không đổi một byte nào của đường đang
     *    chạy hiện trường (CLAUDE.md §6). Vòng dò ấy **bỏ qua** giá trị trả về nên gần như luôn dừng ở `0`
     *    (`VIEW_DEFAULT` = khung ghép 4-in-1) — RE `electro-camera-RE-2026-09-26.md` §5 K4.
     *  • `0..4` ⇒ gọi **đúng một lần** với kênh đó **và ĐỌC giá trị trả về** — đó mới là một phép đo (§6.3-C1: nếu
     *    `VIEW_CHANNEL_n` bắt HAL trả một kênh camera thay vì khung ghép thì cả tầng crop thành không cần). HAL từ
     *    chối ⇒ rơi về đường `addPreviewSurface(Surface)` một tham số như 2.73, và **dòng log nói rõ rc** để lượt đo
     *    không bị đọc thành "kênh n chạy" khi thật ra ảnh tới từ đường dự phòng.
     */
    fun open(cameraId: Int, surface: Surface, halMode: Int = CameraSignalPolicy.HAL_MODE_AUTO): Boolean {
        val c = loadClass() ?: run { Log.i(TAG, "AVMCamera class không có (off-car/trim khác)"); return false }
        fun m(name: String, vararg types: Class<*>) = runCatching {
            c.getDeclaredMethod(name, *types).apply { isAccessible = true }   // ⚠ kinex setAccessible — method non-public
        }.getOrNull()
        // Dựng: constructor(int) rồi open() (device open) — HOẶC static open(int). setAccessible bắt buộc.
        var obj = runCatching { c.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(cameraId) }.getOrNull()
        if (obj != null) {
            // constructor(int) xong PHẢI open() device (kinex) — thiếu bước này ⇒ "camera has been not open".
            val opened = runCatching { m("open")?.invoke(obj); true }.getOrDefault(false)
            Log.i(TAG, "AVMCamera(cameraId=$cameraId) + open() device=$opened")
        } else {
            obj = runCatching { m("open", Integer.TYPE)?.invoke(null, cameraId) }.getOrNull()
            Log.i(TAG, "AVMCamera.open($cameraId) static → ${obj != null}")
        }
        if (obj == null) { Log.w(TAG, "không tạo/mở được AVMCamera cameraId=$cameraId"); return false }
        cam = obj
        runCatching { m("setCameraFps", Integer.TYPE)?.invoke(obj, FPS) }   // fps có thể bị từ chối — non-fatal
        // addPreviewSurface(Surface, int mode) — thử mode 0..3 như kinex.
        val add = m("addPreviewSurface", Surface::class.java, Integer.TYPE)
        var surfaceOk = false
        // MÓC ĐO (pref `camera_hal_mode`, mặc định AUTO ⇒ nhánh này KHÔNG chạy): một lời gọi, đọc rc thật.
        if (add != null && CameraSignalPolicy.isHalMode(halMode) && halMode >= CameraSignalPolicy.HAL_MODE_MIN) {
            val rc = runCatching { add.invoke(obj, surface, halMode) as? Boolean ?: true }.getOrNull()
            Log.i(TAG, "addPreviewSurface cameraId=$cameraId halMode=$halMode rc=$rc (pref camera_hal_mode)")
            surfaceOk = rc == true
            if (surfaceOk) added = surface to halMode
        }
        if (add != null && !surfaceOk && halMode < CameraSignalPolicy.HAL_MODE_MIN) {
            for (mode in 0..3) {
                if (runCatching { add.invoke(obj, surface, mode); true }.getOrDefault(false)) {
                    Log.i(TAG, "addPreviewSurface ok cameraId=$cameraId mode=$mode"); surfaceOk = true; added = surface to mode; break
                }
            }
        }
        if (!surfaceOk) {
            runCatching { m("addPreviewSurface", Surface::class.java)?.invoke(obj, surface); surfaceOk = true }
        }
        val started = runCatching { m("startPreview")?.invoke(obj); true }.getOrDefault(false)
        Log.i(TAG, "AVMCamera cameraId=$cameraId surfaceOk=$surfaceOk started=$started")
        return started
    }

    /**
     * Cỡ ảnh preview THẬT mà HAL đang đổ ra, `intArrayOf(w, h)` — hoặc `null` khi không hỏi được.
     *
     * [ĐO RE] hai hàm này CÓ THẬT trong lớp framework: firmware DiLink5.1 `com/byd/dilink51_main/hardware/camera/
     * DiLinkAVMCamera.java` bọc thẳng `AVMCamera.getPreviewWidth()` / `getPreviewHeight()`. [CHƯA BIẾT] trim
     * DiLink3.0 trên xe owner có trả số đúng hay trả 0 — vì vậy `null`/`≤ 0` là một câu trả lời **bình thường**, và
     * chỗ gọi ([CameraOverlayView.onStreamMeasured]) phải sống được với nó (giữ gợi ý đang dùng).
     *
     * Dùng cho CAM-ROT-2: cửa sổ overlay lấy đúng tỉ lệ vùng crop sau xoay ⇒ cần cỡ ảnh nguồn, và cỡ ấy phải **đo**
     * chứ không hardcode (xe khác ghép ảnh 4-in-1 cỡ khác — CLAUDE.md §7).
     */
    fun previewSize(): IntArray? {
        val obj = cam ?: return null
        val c = cls ?: return null
        fun read(name: String): Int? = runCatching {
            c.getDeclaredMethod(name).apply { isAccessible = true }.invoke(obj) as? Int
        }.getOrNull()
        val w = read("getPreviewWidth") ?: return null
        val h = read("getPreviewHeight") ?: return null
        if (w <= 0 || h <= 0) { Log.i(TAG, "previewSize HAL trả ${w}x$h ⇒ coi như chưa biết"); return null }
        Log.i(TAG, "previewSize HAL = ${w}x$h")
        return intArrayOf(w, h)
    }

    /**
     * Nhờ HAL xoay hộ khung hình đổ vào [surface] — đường xoay DUY NHẤT còn lại khi kết xuất bằng `SurfaceView`
     * (không có `setTransform`). Trả `true` khi lời gọi được NHẬN (≠ đã có tác dụng).
     *
     * [ĐO RE] `AVMCamera.setDisplayOrientation(Surface, int)` có trong lớp framework (cùng nguồn [previewSize]).
     * ⚠ [CHƯA BIẾT] ROM này có thi hành hay không — và "nhận" ở đây chỉ có nghĩa *reflection không ném và không trả
     * `false`*. Vì vậy chỗ gọi phải báo kết quả lên tầng vẽ để cửa sổ lấy tỉ lệ ĐÚNG với thứ thật sự xảy ra, thay
     * vì giả định đã xoay (CLAUDE.md §2: cơ chế ≠ quy kết).
     */
    fun setDisplayOrientation(surface: Surface, deg: Int): Boolean {
        val obj = cam ?: return false
        val c = cls ?: return false
        val norm = ((deg % 360) + 360) % 360
        val ok = runCatching {
            val m = c.getDeclaredMethod("setDisplayOrientation", Surface::class.java, Integer.TYPE).apply { isAccessible = true }
            m.invoke(obj, surface, norm) as? Boolean ?: true
        }.getOrDefault(false)
        Log.i(TAG, "setDisplayOrientation($norm) nhận=$ok")
        return ok
    }

    /**
     * Dỡ camera. Hai bước của 2.73 (`stopPreview` → `close`) **giữ nguyên thứ tự**; bước đo mới nằm giữa chúng.
     *
     * ## Bước 3 (2.74, RE §5 K6): `rmPreviewSurface(Surface, int)`
     * [ĐO firmware] hàm này có thật trên **chính lớp framework**: `com/byd/dilink51_main/hardware/camera/
     * DiLinkAVMCamera.java:143-144` gọi `f321DDC.rmPreviewSurface(surface, i)` với `f321DDC` khai `android.hardware.
     * AVMCamera` (`:22`, import `:3`); chữ ký trong SDK là `IDiLinkAVMCamera.java:38`. Tên `removePreviewSurface`
     * **không tồn tại** ở đâu cả (grep firmware + jadx-kinex + jadx-electro + jadx-openbyd = 0 hit) ⇒ chỉ thử một
     * tên duy nhất. Kachi tới 2.73 **không gọi** ⇒ ứng viên rò rỉ khi bật/tắt overlay nhiều lần.
     *
     * ## ⚠ Vì sao ĐẶT SAU `close()` dù Electro làm `stop → rm → release`
     * Electro có ba tag lỗi riêng cho ba bước (@0x644e7, @0x644c4, @0x64509) ⇒ thứ tự của **nó** là `rm` trước
     * `release` [SUY]. Nhưng đường `stopPreview → close` của Kachi **đang chạy tốt ngoài hiện trường**, và CLAUDE.md
     * §6 cấm đảo thứ tự một đường như thế để chữa cho một thứ chưa đo. Vì vậy: bước mới **xuống cuối**, `runCatching`
     * riêng, log ở mức DEBUG (camera đã đóng thì HAL có quyền từ chối — đó là ca BÌNH THƯỜNG, không phải lỗi). Nếu
     * buổi xe tới chứng minh rò rỉ thật thì mới bàn tới việc chèn nó vào giữa, kèm phép đo.
     */
    fun close() {
        val obj = cam ?: return
        val c = cls
        runCatching { c?.getDeclaredMethod("stopPreview")?.apply { isAccessible = true }?.invoke(obj) }
        // Cổng của bước 3 — xem KDoc [rmOnClose]. Tắt (mặc định) ⇒ `stopPreview` → `close` là TRỌN chuỗi, y 2.73.
        //
        // ⚠ [ĐO xe 27/09] Bước này nay đứng **GIỮA** `stopPreview` và `close`, không còn sau `close`. Lý do là một
        // phép đo chứ không phải một ý thích: bản 2.74 gọi nó SAU `close()` và bốn lượt trên xe đều ghi
        // `rmPreviewSurface(mode=0) rc=false` — HAL từ chối vì camera đã đóng ⇒ lời gọi ấy **không đo được gì**.
        // Thứ tự mới cũng đúng thứ tự Electro dùng (`stop → rm → release`, ba tag lỗi riêng @0x644e7/@0x644c4/
        // @0x64509 — RE §3.1). Rủi ro của việc đảo được giới hạn bằng chính cái cổng: nhánh này **chỉ** chạy khi
        // chế độ kiểm thử đang mở, tức xe của owner lúc chạy bình thường vẫn đi đúng hai lời gọi của 2.73.
        if (rmOnClose) added?.let { (surface, mode) ->
            val rc = runCatching {
                c?.getDeclaredMethod("rmPreviewSurface", Surface::class.java, Integer.TYPE)
                    ?.apply { isAccessible = true }?.invoke(obj, surface, mode)
            }.getOrNull()
            Log.d(TAG, "rmPreviewSurface(mode=$mode) rc=$rc (trước close; rc=false ở 2.74 là do gọi SAU close)")
        }
        runCatching { c?.getDeclaredMethod("close")?.apply { isAccessible = true }?.invoke(obj) }
        added = null
        cam = null
    }

    companion object {

        /**
         * `getprop <key>` trong tiến trình qua `android.os.SystemProperties` — `""` khi lỗi/off-car.
         *
         * Dùng cho phép thử năng lực pano `vehicle.config.cam_sort` (RE §5 K2): [ĐO firmware] launcher gốc dò camera
         * bằng đúng khoá này (`VehicleUtils.java:187-192` → `SystemProperties.get("vehicle.config.cam_sort","")`,
         * rồi `hasAVMRecorder() = contains("pano_h")` ở `:176`) — rẻ hơn mở camera để xem có ra hình.
         *
         * ⚠ Nợ kỹ thuật ĐÃ BIẾT (CLAUDE.md §4.1 DRY): hai bản sao `private` của đúng phép reflection này đã tồn tại ở
         * `ClusterProfile.kt:193` và `SeatComfortApplier.kt:154`. Gộp cả ba vào một cửa dùng chung là việc phải làm,
         * nhưng nó **đụng hai tệp của làn khác** trong cùng phiên ⇒ ghi lại cho điều phối, không tự sửa ở đây.
         */
        fun systemProp(key: String): String = runCatching {
            val c = Class.forName("android.os.SystemProperties")
            (c.getMethod("get", String::class.java).invoke(null, key) as? String).orEmpty()
        }.getOrDefault("")

        /** Khoá getprop liệt kê `<tag>:<id>;` của mọi luồng camera trên xe [ĐO `VehicleUtils.java:192`]. */
        const val PROP_CAM_SORT = "vehicle.config.cam_sort"

        private const val TAG = "KachiCamera"
        private const val FQN = "android.hardware.AVMCamera"
        private const val DEX = "/system/framework/bmmcamera.jar"
        private const val FPS = 15
    }
}
