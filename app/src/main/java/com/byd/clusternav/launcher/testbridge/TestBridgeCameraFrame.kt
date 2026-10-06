package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper

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

    // 2.92: tiền tố tệp / trần 10 ảnh / mốc giờ / chất lượng PNG chuyển sang `CameraFrameFiles` (một chỗ cho cả nút
    // *Khung thô* ở Chẩn đoán — CLAUDE.md §4.1 DRY). Tệp ghi ra + lời đáp THÀNH CÔNG không đổi một byte; hai lời đáp HỎNG
    // hiếm đổi nhẹ (thiếu thư mục ngoài ⇒ thêm `path: ""`; `createBitmap` hỏng ⇒ lý do không còn kèm cỡ, `pixels` vẫn
    // có) — mã lỗi `write_failed` giữ nguyên (soát Opus 06/10 [P3]: chú thích cũ hứa "không đổi một byte" là quá lời).

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
                // Bề rộng suy bằng ĐÚNG phép kẹp của `grabRaw` ([P1 · SOÁT Opus 2026-09-27]) — một chỗ, ở
                // `CameraFrameFiles.rawBitmap` (dùng chung với nút *Khung thô* của Chẩn đoán, 2.92).
                val bmp = com.byd.clusternav.launcher.camera.CameraFrameFiles.rawBitmap(px, size.w)
                if (bmp == null) reply.fail(ERR_WRITE, "reason" to "createBitmap failed", "pixels" to px.size)
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
     * Nén [bmp] ra PNG rồi chốt lời đáp. Chạy ở LUỒNG NỀN. Nén + đặt tên + dọn (≤ 10 ảnh, [P2 · SOÁT Opus 2026-09-27]:
     * `kachi-logs/` không có ai dọn ảnh) nằm ở `CameraFrameFiles.savePng` — dùng chung với nút *Khung thô* (2.92).
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
            val saved = com.byd.clusternav.launcher.camera.CameraFrameFiles.savePng(app, bmp)
            if (saved !is com.byd.clusternav.launcher.camera.CameraFrameFiles.Saved.Ok) {
                val f = saved as com.byd.clusternav.launcher.camera.CameraFrameFiles.Saved.Failed
                reply.fail(ERR_WRITE, "path" to (f.path ?: ""), "reason" to f.reason)
                return
            }
            val out = saved.file
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

    private const val THREAD = "kachi-camframe"
}
