package com.byd.clusternav.launcher.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.get
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Lang
import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraSignalEnabled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
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
 *     (CLAUDE.md §4: phạm vi = luồng camera của chính Kachi; hoàn tác = [CameraSignalController.endPreview]). Ghi lại
 *     **số hiệu phiên** vừa mở ([CameraSignalController.sessionSeq]) — chụp và đóng CHỈ phiên ấy.
 *  2. **Chờ** khung đầu (≤ [WAIT_MS]) — xem [awaitFrame]: GL đang vẽ ⇒ `frames=N > 0`; TextureView (đường `TV` và đường
 *     GL đã RƠI) ⇒ layer phải có ít nhất một điểm ảnh thật. Không có khung ⇒ nói thẳng, không chụp một ô đen (hay
 *     trong suốt) rồi gọi là "đã lưu".
 *  3. **Chụp** đúng đường của lệnh `camera_frame`: GL ⇒ [CameraSignalController.grabRawFrame] (FBO, nguyên khung, không
 *     nắn — khung đang hiện có thể đã nắn); TextureView ⇒ `getBitmap` của layer (khung GỐC — KDoc
 *     `CameraOverlayView.captureFrame`). SurfaceView ⇒ không chụp được, nói lý do.
 *  4. **Ghi** PNG vào `kachi-logs/` + bản sao `Pictures/Kachi/` ([CameraFrameFiles]); nén ở luồng nền.
 *  5. **Đóng** lượt xem thử mà chính nút đã mở ([CameraSignalController.endPreview] theo số hiệu phiên).
 *
 * Mọi lượt chụp xếp hàng trên MỘT luồng ([worker]); công tắc *Bật camera khi xi-nhan* TẮT ⇒ báo chữ, KHÔNG tự bật (một
 * nút chẩn đoán không được đổi cấu hình người lái).
 */
internal object CameraRawCapture {

    /** Trần chờ khung đầu — HAL lên khung thật trong ~0,3–1 s [SUY log xe 26–27/09]; 3 s là rộng tay. */
    private const val WAIT_MS = 3_000L

    private const val POLL_MS = 100L

    /** Trần chờ một lượt việc trên main (đọc ngữ cảnh / chụp FBO — [CameraGlRenderer.grabRaw] tự có trần 5 s). */
    private const val MAIN_MS = 7_000L

    private const val THREAD = "kachi-camraw"

    /** Luồng chụp rảnh quá bấy nhiêu giây thì tự tắt ([worker]). */
    private const val IDLE_S = 30L

    /** Ảnh DÒ tí hon của đường TextureView (≈ tỉ lệ khung ghép 16:3) — chỉ để biết layer đã nhận buffer chưa. */
    private const val PROBE_W = 32
    private const val PROBE_H = 6

    /** Số điểm lấy mẫu mỗi trục khi hỏi "bitmap có điểm ảnh thật không" ([hasPixels]). */
    private const val SAMPLES = 16

    /** Lý do (ASCII, đi vào câu "Chụp hỏng: {0}") khi phiên camera đã bị thay giữa chừng — xi-nhan thật / lượt khác. */
    private const val SESSION_CHANGED = "camera session changed (turn signal?)"

    /** Chưa có số hiệu phiên (lượt mở ném / quá giờ) — số hiệu thật bắt đầu từ 1 ([CameraSignalController.sessionSeq]). */
    private const val NO_SEQ = -1L

    private val FRAMES = Regex("""frames=(\d+)""")

    /**
     * MỘT luồng cho mọi lượt chụp ([P2 · soát Opus 06/10]): controller có MỘT phiên camera, nên hai lượt chồng nhau (chạm
     * *trái* rồi *phải* liền tay) là lượt trái chụp nhầm phiên phải (tệp `-raw-left` chứa dải phải) rồi đóng mất phiên
     * ấy. Xếp hàng ⇒ lượt sau mở phiên của mình khi lượt trước đã đóng xong. Lõi 0 + hạn rảnh [IDLE_S] ⇒ hết việc thì
     * luồng tự tắt — một nút chẩn đoán không để lại luồng nằm chờ suốt đời tiến trình launcher.
     */
    private val worker: ExecutorService = ThreadPoolExecutor(0, 1, IDLE_S, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
        Thread(r, THREAD).apply { isDaemon = true }
    }

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
        worker.execute {
            val text = runCatching { run(app, left) }.getOrElse {
                Log.w(PanoramaHal.TAG, "khung thô: ${it.javaClass.simpleName} ${it.message}")
                failed(it.javaClass.simpleName)
            }
            onDone(text.first, text.second)
        }
    }

    private fun run(app: Context, left: Boolean): Pair<String, Boolean> {
        val c = AppContainer.get(app).cameraSignal
        var seq = NO_SEQ
        try {
            // Số hiệu đọc trong CÙNG nhịp main với lượt mở ⇒ đúng phiên xem thử của nút này, không phải phiên nào khác.
            seq = onMain { c.previewSide(left); c.sessionSeq() } ?: return failed("preview did not open")
            val shot = awaitFrame(c, seq)
            if (shot == null) {
                if (onMain { c.sessionSeq() } != seq) return failed(SESSION_CHANGED)
                return Lang.t("Không có khung nào trong 3 giây — camera chưa lên", "No frame within 3 s — the camera did not start") to true
            }
            val shader = drawsInShader(shot)
            if (!shader && !shot.capturable) {
                return Lang.t(
                    "Đường vẽ SurfaceView không chụp được — đổi sang TextureView hoặc GL",
                    "The SurfaceView path cannot be captured — switch to TextureView or GL",
                ) to true
            }
            // Dòng uniform đọc TRƯỚC lượt chụp, cùng phép so phiên ⇒ chụp được thì dòng này chắc chắn của đúng phiên ấy.
            val note = onMain { if (c.sessionSeq() == seq) c.sessionNote() else null }.orEmpty()
            val bmp: Bitmap = grab(c, seq, shader)
                ?: return failed(if (onMain { c.sessionSeq() } != seq) SESSION_CHANGED else "no pixels")
            try {
                return when (val saved = CameraFrameFiles.savePng(app, bmp, if (left) "-raw-left" else "-raw-right")) {
                    is CameraFrameFiles.Saved.Failed -> failed(saved.reason)
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
            // Lượt mở NÉM / quá giờ (không có số hiệu) ⇒ đóng theo số hiệu lúc khối này chạy trên main — nó xếp hàng SAU
            // lượt mở đã post, nên vẫn là đúng phiên vừa mở; như bản gốc, lượt xem thử không bao giờ bị bỏ treo.
            onMain { c.endPreview(if (seq != NO_SEQ) seq else c.sessionSeq()) }
        }
    }

    /**
     * Chụp khung THÔ trong CÙNG nhịp main với phép so số hiệu phiên ⇒ phiên đã bị thay thì `null` (không chụp nhầm bên).
     * GL đang vẽ ⇒ FBO (bitmap dựng ở luồng này, không ở main); TextureView ⇒ `getBitmap` layer, và một bitmap không có
     * điểm ảnh thật nào (layer chưa có buffer — KDoc [awaitFrame]) bị bỏ, không ghi thành "khung thô".
     */
    private fun grab(c: CameraSignalController, seq: Long, shader: Boolean): Bitmap? {
        if (shader) {
            return onMain { if (c.sessionSeq() == seq) c.grabRawFrame(CameraPanoCrop.PANO_W, CameraPanoCrop.PANO_H) else null }
                ?.let { CameraFrameFiles.rawBitmap(it, CameraPanoCrop.PANO_W) }
        }
        val b = onMain { if (c.sessionSeq() == seq) c.grabFrame(CameraPanoCrop.PANO_W, CameraPanoCrop.PANO_H).bitmap else null }
        if (b != null && !hasPixels(b)) {
            b.recycle()
            return null
        }
        return b
    }

    /**
     * Chờ khung ĐẦU của phiên [seq] — đọc ngữ cảnh trên main mỗi [POLL_MS]; `null` = hết [WAIT_MS] hoặc phiên đã bị thay.
     *  • GL đang vẽ ⇒ bộ đếm `frames=N` của luồng vẽ (`> 0` = đã có một lượt vẽ thật).
     *  • TextureView — đường `TV`, và đường GL đã RƠI ([CameraVideoLayer.GL_FELL_BACK]: không có bộ đếm nào, chờ nó là
     *    báo "camera chưa lên" trong khi ô đang có hình) ⇒ `isAvailable` CHƯA đủ, nó chỉ nói `SurfaceTexture` đã có.
     *    [ĐO AOSP `android-10.0.0_r47`] layer chưa nhận buffer nào thì `LayerDrawable::DrawLayer` trả `false`
     *    (`libs/hwui/pipeline/skia/LayerDrawable.cpp:145` `return layerImage != nullptr`) ⇒ `Readback::copyLayerInto` hỏng
     *    (`libs/hwui/Readback.cpp:184-188`) ⇒ `TextureView.getBitmap` trả đúng bitmap MỚI nguyên số 0
     *    (`core/java/android/view/TextureView.java:574-579`, *"If an error occurs, the bitmap is left unchanged"* `:590`).
     *    ⇒ dò bằng một ảnh tí hon và đòi ít nhất một điểm alpha ≠ 0 ([hasPixels]).
     */
    private fun awaitFrame(c: CameraSignalController, seq: Long): CameraFrameShot? {
        val until = SystemClock.elapsedRealtime() + WAIT_MS
        while (SystemClock.elapsedRealtime() < until) {
            val shot = onMain { if (c.sessionSeq() == seq) c.grabFrame(PROBE_W, PROBE_H) else null } ?: return null
            val probe = shot.bitmap
            val ready = shot.overlayShowing &&
                if (drawsInShader(shot)) framesOf(shot) > 0 else probe != null && hasPixels(probe)
            probe?.recycle()
            if (ready) return shot.copy(bitmap = null)
            SystemClock.sleep(POLL_MS)
        }
        return null
    }

    /** Đường GL có luồng vẽ THẬT đang chạy — không phải GL đã rơi về TextureView ([CameraVideoLayer.GL_FELL_BACK]). */
    private fun drawsInShader(shot: CameraFrameShot): Boolean =
        CameraSignalPolicy.rotatesInShader(shot.render) && CameraVideoLayer.GL_FELL_BACK !in shot.glStats

    private fun framesOf(shot: CameraFrameShot): Long =
        FRAMES.find(shot.glStats)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    /** Có ít nhất một điểm ảnh alpha ≠ 0 (lấy mẫu thưa [SAMPLES]×[SAMPLES]) — layer chưa có buffer cho bitmap nguyên số 0. */
    private fun hasPixels(b: Bitmap): Boolean {
        val stepX = maxOf(1, b.width / SAMPLES)
        val stepY = maxOf(1, b.height / SAMPLES)
        for (y in 0 until b.height step stepY) {
            for (x in 0 until b.width step stepX) if (b[x, y] ushr 24 != 0) return true
        }
        return false
    }

    private fun failed(reason: String): Pair<String, Boolean> =
        Lang.f("Chụp hỏng: {0}", "Capture failed: {0}", reason) to true

    /** Chạy [block] trên main, CHẶN tới khi xong (≤ [MAIN_MS]) — mọi thứ chạm overlay/`TextureView` phải ở main. */
    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var out: T? = null
        val done = CountDownLatch(1)
        if (!Handler(Looper.getMainLooper()).post { out = runCatching(block).getOrNull(); done.countDown() }) return null
        return if (runCatching { done.await(MAIN_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)) out else null
    }
}
