package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byd.clusternav.launcher.KachiLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ═══ T-BRIDGE · `camera_frame` — CHỤP KHUNG CAMERA GỐC RA PNG ════════════════════════════════════════════════
 *
 * `camera_frame [--es name <W>x<H> | raw]` (mặc định = cỡ fisheye 4-in-1, xem [TestBridgeFrameSize]).
 *
 * ## Câu hỏi nó sinh ra để trả lời
 * *"Vòng ảnh fisheye TRÒN (còn viền đen quanh) hay đã KÍN khung?"* — không có câu trả lời nào cho nó từ một ảnh
 * chụp màn: overlay là một ô VUÔNG đã đi qua ma trận crop + xoay (`CameraOverlayTransform`), tức đúng cái đang bị
 * nghi là sai. Lệnh này đọc **layer** của `TextureView`, nơi ma trận đó CHƯA được áp — bằng chứng AOSP `file:line`
 * nằm ở KDoc `CameraOverlayView.captureFrame`, không nhắc lại ở đây (một chỗ khai).
 *
 * ## [ARG_RAW] — và vì sao đường `GL` BẮT BUỘC phải có nó (R8-B · 2.74)
 * Trên đường `TV`, `getBitmap` đọc **layer** nên ảnh ra là khung **GỐC** — đúng thứ cần để đo vòng ảnh. Trên đường
 * `GL` thì shader ghi thẳng vào cửa ra, nên cùng một `getBitmap` trả khung **ĐÃ NẮN**: dùng nó để đo bán kính/tâm
 * vòng ảnh là đo trên ảnh đã bị biến đổi bởi chính bộ tham số mình đang muốn chốt — một vòng lặp tự xác nhận.
 *
 * `--es name raw` vẽ **một lượt thứ hai** với `amount = 0`, nguyên khung, không xoay, vào một FBO cỡ luồng gốc rồi
 * `glReadPixels` ([CameraGlRenderer.grabRaw]) ⇒ khung thô **bất kể** đường kết xuất nào đang treo. Lời đáp luôn nói
 * `content` (`raw`/`dewarped`) để không ai phải đoán mình đang nhìn ảnh gì.
 *
 * ## Chỉ đường kết xuất `TextureView` chụp được
 * Đường phụ `SurfaceView` (`CameraSignalPolicy.RENDER_SURFACE`, chip *Kết xuất* trong Cài đặt) ghép layer **ngoài**
 * cây view ⇒ `TextureView.getBitmap` không tồn tại ở đó. Lệnh trả [ERR_NO_FRAME] kèm `reason` nói THẲNG điều đó và
 * kèm luôn cách đổi về đường chụp được — không để người đang ngồi trong xe đọc một mã lỗi trống rồi đi mò xi-nhan
 * (CLAUDE.md §15: dữ liệu, không mò UI).
 *
 * ## Vì sao PNG, và vì sao ghi vào `kachi-logs/`
 * PNG **không mất mát**: JPEG sẽ thêm vào ảnh đúng loại nhiễu mà mắt người dễ đọc thành "viền mờ", tức làm bẩn
 * chính phép đo. Thư mục = [KachiLog.dir] (`getExternalFilesDir(null)/kachi-logs/`) vì đó là chỗ `adb pull` lấy
 * được **không cần `run-as`, không cần quyền bộ nhớ nào** — cùng lựa chọn đã ghi ở KDoc [KachiLog] và
 * [TestBridgeReply.write].
 *
 * ## Ba luồng, mỗi việc một chỗ (CLAUDE.md §4.1 · trần thời gian [KachiTestBridge.CAP_MS] = 20 s)
 *  1. **main** — `getBitmap`: bắt buộc (cây view + đồng bộ với render thread);
 *  2. **luồng nền** — nén PNG + ghi tệp: một khung cỡ luồng gốc nén mất hàng trăm ms, làm việc đó trên main là một
 *     cú giật hình ngay trên xe đang lăn bánh (và `ANR` nếu đĩa chậm);
 *  3. **`reply`** — chốt ở luồng nền SAU khi đã ghi xong, vì `path`/`bytes` chỉ có thật lúc đó. [TestBridgeReply]
 *     tự chống chốt-hai-lần nên đường hết-giờ chạy song song vẫn an toàn.
 *
 * KHÔNG ghi nhật ký theo từng khung: một dòng cho MỘT lượt lệnh (lệnh này không nằm trong đường frame).
 */
internal object TestBridgeCameraFrame {

    /** Tiền tố tệp ảnh trong `kachi-logs/` — một tên, một chỗ khai (script `adb pull` grep theo chuỗi này). */
    private const val PREFIX = "camera-frame-"

    private const val EXT = ".png"

    /** Số ảnh giữ lại trong `kachi-logs/` — xem KDoc [prune]. Mười khung đủ cho một buổi xe, và là ~10–60 MB. */
    private const val KEEP_PNG = 10

    /** Mốc thời gian tới GIÂY: một lượt chụp/giây là nhiều hơn mọi nhịp xi-nhan thật. */
    private const val STAMP = "yyyyMMdd-HHmmss"

    /** Chất lượng PNG — `compress` bỏ qua tham số này với PNG (không mất mát), ghi 100 cho rõ ý định. */
    private const val QUALITY = 100

    /** Overlay chưa hiện ⇒ không có gì để chụp. Mã ASCII cho script; câu cho người đọc đi trong `reason`. */
    private const val ERR_NO_OVERLAY = "overlay_not_showing"

    /** Có overlay nhưng không ra ảnh: đường `SurfaceView`, chưa có `SurfaceTexture`, hoặc GPU từ chối cỡ đã xin. */
    private const val ERR_NO_FRAME = "capture_failed"

    /** Nén/ghi tệp hỏng (đĩa đầy, thẻ chưa gắn). */
    private const val ERR_WRITE = "write_failed"

    /** `--es name raw` — chụp khung THÔ qua FBO thay vì `getBitmap` (xem KDoc lớp). */
    const val ARG_RAW = "raw"

    /** Đường GL chưa vẽ khung nào / không phải đường GL ⇒ không có FBO nào để đọc. */
    private const val ERR_NO_RAW = "raw_unavailable"

    fun run(app: Context, cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        val size = TestBridgeFrameSize.parse(cmd.arg)
        if (cmd.arg.trim().lowercase() == ARG_RAW) {
            runRaw(app, hooks, size, reply)
            return
        }
        // `getBitmap` là op cây view ⇒ main thread (KDoc AOSP `TextureView.getBitmap`). Cùng khuôn [TestBridgeCamera].
        Handler(Looper.getMainLooper()).post {
            val shot = hooks.cameraFrame(size.w, size.h)
            if (shot == null || !shot.overlayShowing) {
                // Tên cờ là `--es name` (KHÔNG phải `--es arg`): [TestBridgeCommands.EXTRA_ARG] == "name".
                // Câu này là thứ người đang ngồi trong xe đọc ⇒ gõ sai cờ ở đây là một vòng mò vô ích.
                reply.fail(ERR_NO_OVERLAY, "reason" to NO_OVERLAY_HINT, "asked" to "${size.w}x${size.h}")
                return@post
            }
            val bmp = shot.bitmap
            if (bmp == null) {
                reply.fail(
                    ERR_NO_FRAME,
                    "reason" to whyNoFrame(shot),
                    "available" to shot.available,
                    "capturable" to shot.capturable,
                    "render" to shot.render,
                    "overlay_showing" to true,
                    "asked" to "${size.w}x${size.h}",
                    "view" to shot.view,
                    "gl_stats" to shot.glStats,
                    "gl" to shot.glInfo,
                )
                return@post
            }
            // Luồng nền cho lượt nén + ghi (xem KDoc lớp, mục 2). `isDaemon` ⇒ không giữ tiến trình sống thêm.
            Thread({ save(app, bmp, shot, size, reply, shot.content) }, THREAD).apply { isDaemon = true }.start()
        }
    }

    /**
     * Đường [ARG_RAW]: đọc ngữ cảnh + pixel thô trong **cùng một nhịp main thread**, rồi nén ở luồng nền.
     *
     * `cameraFrame(0, 0)` lấy ngữ cảnh mà **không** chụp gì: `CameraOverlayView.captureFrame` trả `null` ngay khi
     * `w < 1` ⇒ không một byte bitmap nào được cấp phát, nhưng mọi trường *"đang hiện cam nào, crop nào"* vẫn đúng
     * nhịp. Hỏi ngữ cảnh bằng một lời gọi thứ hai sau đó là mở đường cho một nhịp xi-nhan chen vào giữa — đúng cái
     * KDoc `CameraFrameShot` sinh ra để chặn.
     */
    private fun runRaw(
        app: Context,
        hooks: TestBridgeHooks,
        size: TestBridgeFrameSize.Size,
        reply: TestBridgeReply,
    ) {
        Handler(Looper.getMainLooper()).post {
            val shot = hooks.cameraFrame(0, 0)
            if (shot == null || !shot.overlayShowing) {
                reply.fail(ERR_NO_OVERLAY, "reason" to NO_OVERLAY_HINT, "asked" to "${size.w}x${size.h} raw")
                return@post
            }
            val px = hooks.cameraFrameRaw(size.w, size.h)
            if (px == null) {
                reply.fail(
                    ERR_NO_RAW,
                    "reason" to "render=${shot.render} has no GL framebuffer to read back —" +
                        " switch with `prefs_set --es key camera_render --es text" +
                        " ${com.byd.clusternav.launcher.camera.CameraSignalPolicy.RENDER_GL}`," +
                        " re-trigger the turn signal, wait for one frame (gl_stats), then retry",
                    "render" to shot.render,
                    "gl_stats" to shot.glStats,
                    "gl" to shot.glInfo,
                    "asked" to "${size.w}x${size.h}",
                )
                return@post
            }
            Thread({
                // ═══ [P1 · SOÁT Opus 2026-09-27] BỀ RỘNG phải suy bằng ĐÚNG phép kẹp của `grabRaw` ═══════════════
                //
                // `CameraGlRenderer.grabRaw` kẹp **CẢ HAI** cạnh theo `GL_MAX_TEXTURE_SIZE` (`CameraGlInfo.cap`).
                // Bản trước tin `size.w` rồi suy `h = px.size / w` — và một bề rộng **không** suy ra được từ số
                // pixel. Trên đầu máy có trần 4096 mà xin 5120×960: `grabRaw` trả 4096×960 = 3 932 160 px, dòng cũ
                // dựng bitmap **5120×768** ⇒ mỗi hàng lệch 1024 px ⇒ ảnh fisheye **xiên chéo**. `createBitmap`
                // KHÔNG ném (`w*h ≤ px.size`), không một dòng log nào, và người đo lấy tâm/bán kính vòng ảnh trên
                // một ảnh đã bị xé. Đúng loại *"câu trả lời sai trông y như câu trả lời đúng"* mà CLAUDE.md §2 cấm.
                val cap = com.byd.clusternav.launcher.camera.CameraGlInfo.cap(size.w)
                val w = size.w.coerceIn(1, cap)
                val h = if (w > 0) px.size / w else 0
                val bmp = runCatching { Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888) }.getOrNull()
                if (bmp == null) reply.fail(ERR_WRITE, "reason" to "createBitmap ${w}x$h failed", "pixels" to px.size)
                else save(app, bmp, shot, size, reply, CONTENT_RAW_FBO)
            }, THREAD).apply { isDaemon = true }.start()
        }
    }

    /** Ảnh đến từ FBO pass-through, KHÔNG từ `getBitmap` — nói rõ trong lời đáp để lượt đo không lẫn hai đường. */
    private const val CONTENT_RAW_FBO = "raw_fbo"

    /** Câu dặn khi chưa có overlay — một chỗ khai, hai đường dùng. */
    private const val NO_OVERLAY_HINT =
        "overlay not showing - run `camera --es name left` first (pref camera_signal_enabled must be true)"


    /**
     * Vì sao không có ảnh — hai lý do KHÁC NHAU mà cùng một mã lỗi sẽ trộn lẫn.
     *
     * Đường `SurfaceView` là ca *"không bao giờ chụp được"*: chờ thêm, bật thêm xi-nhan, xin cỡ khác đều vô ích ⇒
     * phải nói ra ngay cách thoát (đổi pref kết xuất), nếu không lượt đo trên xe sẽ đi đúng vòng mò mà CLAUDE.md
     * §15 cấm. Còn `capturable=true` mà vẫn `null` là ca *"chưa tới lúc"* / *"GPU từ chối cỡ"* — chờ hoặc hạ cỡ.
     */
    private fun whyNoFrame(shot: com.byd.clusternav.launcher.camera.CameraFrameShot): String =
        if (!shot.capturable) {
            "render=${shot.render} draws into a SurfaceView layer - TextureView.getBitmap does not exist there;" +
                " switch with `prefs_set --es key camera_render --es text" +
                " ${com.byd.clusternav.launcher.camera.CameraSignalPolicy.RENDER_TEXTURE}`" +
                " then re-trigger the turn signal"
        } else {
            "no SurfaceTexture yet (available=${shot.available}) or GPU refused the asked size -" +
                " retry after ~1s, or ask a smaller size (GL_MAX_TEXTURE_SIZE)"
        }

    /**
     * ═══ [P2 · SOÁT Opus 2026-09-27] Trần số ảnh giữ lại — `kachi-logs/` KHÔNG có ai dọn ảnh ══════════════════
     *
     * `TestBridgeReply.prune` chỉ dọn tệp `.json` trong `files/test` ([ĐO] `TestBridgeReply.kt` `KEEP = 50`), còn ảnh đi vào
     * `files/kachi-logs/` — cùng thư mục với nhật ký phiên, nhưng một khung fisheye `5120×960` nén ra **hàng MB**,
     * tức lớn hơn một tệp log 100–1000 lần. Chỉ riêng CAM-A4 của runbook đã chụp 4+ lượt mỗi buổi. Không có trần thì
     * bộ nhớ đầu máy là thứ trả giá, và nó im lặng.
     *
     * Chỉ chạm tệp của **chính lệnh này** (`camera-frame-*.png`) — nhật ký phiên và mọi tệp khác trong thư mục là
     * của chủ khác, không được xoá hộ.
     */
    private fun prune(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(EXT) }
            ?: return
        if (files.size <= KEEP_PNG) return
        files.sortedBy { it.lastModified() }.take(files.size - KEEP_PNG).forEach { runCatching { it.delete() } }
    }

    /**
     * Nén [bmp] ra PNG rồi chốt lời đáp. Chạy ở LUỒNG NỀN.
     *
     * `recycle` trong `finally`: một khung cỡ luồng gốc ARGB_8888 là hàng chục MB, và đường hỏng (đĩa đầy) cũng phải
     * nhả nó — bỏ sót ở đúng nhánh lỗi là cách một lệnh chẩn đoán làm hết bộ nhớ của launcher sau vài lượt gọi.
     */
    private fun save(
        app: Context,
        bmp: Bitmap,
        shot: com.byd.clusternav.launcher.camera.CameraFrameShot,
        size: TestBridgeFrameSize.Size,
        reply: TestBridgeReply,
        content: String,
    ) {
        val w = bmp.width
        val h = bmp.height
        try {
            val dir = KachiLog.dir(app)
            if (dir == null) {
                reply.fail(ERR_WRITE, "reason" to "no external files dir")
                return
            }
            prune(dir)
            val stamp = SimpleDateFormat(STAMP, Locale.US).format(Date())
            val out = File(dir, "$PREFIX$stamp$EXT")
            val wrote = runCatching {
                out.outputStream().buffered().use { bmp.compress(Bitmap.CompressFormat.PNG, QUALITY, it) }
            }.onFailure { Log.w(TAG, "write ${out.name} failed: ${it.javaClass.simpleName}") }.isSuccess
            if (!wrote) {
                reply.fail(ERR_WRITE, "path" to out.absolutePath, "reason" to "compress/write threw")
                return
            }
            reply.ok(
                "path" to out.absolutePath,
                "width" to w,
                "height" to h,
                "asked" to "${size.w}x${size.h}",
                "bytes" to out.length(),
                "available" to shot.available,
                "overlay_showing" to shot.overlayShowing,
                "render" to shot.render,
                "view" to shot.view,
                "cam_id" to shot.camId,
                "crop" to shot.crop,
                "rotation_deg" to shot.rotationDeg,
                // `content` là trường QUAN TRỌNG NHẤT của lời đáp này khi đường GL đang chạy — xem KDoc lớp.
                "content" to content,
                "capturable" to shot.capturable,
                "gl_stats" to shot.glStats,
                "synth" to shot.synthSize,
                "gl" to shot.glInfo,
            )
        } finally {
            runCatching { bmp.recycle() }
        }
    }

    private const val TAG = TestBridgeReply.TAG

    private const val THREAD = "kachi-camframe"
}
