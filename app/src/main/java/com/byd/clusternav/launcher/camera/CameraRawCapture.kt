package com.byd.clusternav.launcher.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Lang
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraSignalEnabled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ═══ NÚT *📷 Khung thô trái / phải* của màn Chẩn đoán — chụp khung HAL THÔ không cần adb (2.92 · R8) ═══════════════
 *
 * Spec `docs/specs/kachi-292-camera-full-view.html` R8 · research `camera-rear-coverage-2026-10-06.md` §5.3. Khi cắm
 * CarPlay/Android Auto đầu xe TẮT WiFi ⇒ adb từ ngoài không vào được, mà đó đúng lúc cần dữ liệu (CLAUDE.md §11): app
 * tự chụp, người lái chỉ cần một ảnh màn hình + mở Thư viện ảnh. Khung thô là thứ duy nhất chốt được f-θ, tâm vòng ảnh,
 * góc chúc của camera gương — bộ số mà *Thẳng rộng* và *gương ảo* (2.93) cần.
 *
 * ## Đi đúng đường đã có, chỉ ĐỌC ống camera
 *  1. **Mở** bên ấy bằng [CameraSignalController.previewSide] — đúng lượt *xem thử* của Cài đặt (dải hình / thử camera
 *     số): cùng `AVMCamera`, cùng dỡ khi đóng. Không lệnh `am`/`wm`/`service call` nào; không ghi pref camera nào
 *     (CLAUDE.md §4: phạm vi = luồng camera của chính Kachi; hoàn tác = [CameraSignalController.endPreview]).
 *  2. **Chờ** khung đầu (≤ [WAIT_MS]): đọc [CameraSignalController.grabFrame] trên main — GL: `frames=N > 0`; TV:
 *     `available`. Không có khung ⇒ nói thẳng, không chụp một ô đen rồi gọi là "đã lưu".
 *  3. **Chụp** đúng đường của lệnh `camera_frame`: GL ⇒ [CameraSignalController.grabRawFrame] (FBO, nguyên khung, không
 *     nắn — khung đang hiện có thể đã nắn); TV ⇒ `getBitmap` của layer (khung GỐC — KDoc `CameraOverlayView.captureFrame`).
 *     SurfaceView ⇒ không chụp được, nói lý do.
 *  4. **Ghi** PNG vào `kachi-logs/` + bản sao `Pictures/Kachi/` ([CameraFrameFiles]); nén ở luồng nền.
 *  5. **Đóng** lượt xem thử mà chính nút đã mở.
 *
 * Công tắc *Bật camera khi xi-nhan* TẮT ⇒ báo chữ, KHÔNG tự bật (một nút chẩn đoán không được đổi cấu hình người lái).
 */
internal object CameraRawCapture {

    /** Trần chờ khung đầu — HAL lên khung thật trong ~0,3–1 s [SUY log xe 26–27/09]; 3 s là rộng tay. */
    private const val WAIT_MS = 3_000L

    private const val POLL_MS = 100L

    /** Trần chờ một lượt việc trên main (đọc ngữ cảnh / chụp FBO — [CameraGlRenderer.grabRaw] tự có trần 5 s). */
    private const val MAIN_MS = 7_000L

    private const val THREAD = "kachi-camraw"

    private val FRAMES = Regex("""frames=(\d+)""")

    /**
     * Chụp khung thô bên [left]; [onDone] nhận câu đã dịch + cờ cảnh báo, gọi trên LUỒNG NỀN (chỗ gọi tự về UI).
     * Không ném: mọi hỏng hóc thành một câu (launcher không được chết vì một nút chẩn đoán).
     */
    fun capture(ctx: Context, left: Boolean, onDone: (String, Boolean) -> Unit) {
        val app = ctx.applicationContext
        if (!Prefs.cameraSignalEnabled(app)) {
            onDone(Lang.t("Bật «Bật camera khi xi-nhan» trong Cài đặt trước", "Turn on «Camera on turn signal» in Settings first"), true)
            return
        }
        Thread({
            val text = runCatching { run(app, left) }.getOrElse {
                Log.w(PanoramaHal.TAG, "khung thô: ${it.javaClass.simpleName} ${it.message}")
                Lang.f("Chụp hỏng: {0}", "Capture failed: {0}", it.javaClass.simpleName) to true
            }
            onDone(text.first, text.second)
        }, THREAD).apply { isDaemon = true }.start()
    }

    private fun run(app: Context, left: Boolean): Pair<String, Boolean> {
        val c = AppContainer.get(app).cameraSignal
        onMain { c.previewSide(left) }
        try {
            val shot = awaitFrame(c) ?: return Lang.t(
                "Không có khung nào trong 3 giây — camera chưa lên",
                "No frame within 3 s — the camera did not start",
            ) to true
            val gl = CameraSignalPolicy.rotatesInShader(shot.render)
            if (!gl && !shot.capturable) {
                return Lang.t(
                    "Đường vẽ SurfaceView không chụp được — đổi sang TextureView hoặc GL",
                    "The SurfaceView path cannot be captured — switch to TextureView or GL",
                ) to true
            }
            val bmp: Bitmap = (if (gl) {
                onMain { c.grabRawFrame(CameraPanoCrop.PANO_W, CameraPanoCrop.PANO_H) }?.let {
                    CameraFrameFiles.rawBitmap(it, CameraPanoCrop.PANO_W)
                }
            } else {
                onMain { c.grabFrame(CameraPanoCrop.PANO_W, CameraPanoCrop.PANO_H).bitmap }
            }) ?: return Lang.f("Chụp hỏng: {0}", "Capture failed: {0}", "no pixels") to true
            val note = onMain { c.sessionNote() }.orEmpty()
            try {
                return when (val saved = CameraFrameFiles.savePng(app, bmp, if (left) "-raw-left" else "-raw-right")) {
                    is CameraFrameFiles.Saved.Failed -> Lang.f("Chụp hỏng: {0}", "Capture failed: {0}", saved.reason) to true
                    is CameraFrameFiles.Saved.Ok -> {
                        val gallery = CameraFrameFiles.copyToPictures(app, saved.file)
                        val head = Lang.f("Đã lưu khung thô {0}×{1}: {2}", "Saved raw frame {0}×{1}: {2}",
                            bmp.width, bmp.height, saved.file.absolutePath)
                        val tail = if (gallery != null) Lang.f("Thư viện ảnh: {0}", "Gallery: {0}", gallery)
                        else Lang.t("(không chép được vào Thư viện ảnh)", "(could not copy to the gallery)")
                        Log.i(PanoramaHal.TAG, "khung thô ${bmp.width}x${bmp.height} ${saved.file.name} thư-viện=$gallery $note")
                        "$head\n$tail\n$note" to (gallery == null)
                    }
                }
            } finally {
                runCatching { bmp.recycle() }
            }
        } finally {
            onMain { c.endPreview() }
        }
    }

    /** Chờ khung đầu của phiên vừa mở — đọc ngữ cảnh trên main mỗi [POLL_MS]; `null` = hết [WAIT_MS]. */
    private fun awaitFrame(c: CameraSignalController): CameraFrameShot? {
        val until = SystemClock.elapsedRealtime() + WAIT_MS
        while (SystemClock.elapsedRealtime() < until) {
            val shot = onMain { c.grabFrame(0, 0) }
            if (shot != null && shot.overlayShowing) {
                val frames = FRAMES.find(shot.glStats)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val ready = if (CameraSignalPolicy.rotatesInShader(shot.render)) frames > 0 else shot.available
                if (ready) return shot
            }
            SystemClock.sleep(POLL_MS)
        }
        return null
    }

    /** Chạy [block] trên main, CHẶN tới khi xong (≤ [MAIN_MS]) — mọi thứ chạm overlay/`TextureView` phải ở main. */
    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var out: T? = null
        val done = CountDownLatch(1)
        if (!Handler(Looper.getMainLooper()).post { out = runCatching(block).getOrNull(); done.countDown() }) return null
        return if (runCatching { done.await(MAIN_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)) out else null
    }
}
