package com.byd.clusternav.launcher.behind

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import com.byd.clusternav.launcher.SlotVdOwner
import com.byd.clusternav.launcher.VdLease
import com.byd.clusternav.system.WindowCommandDispatcher
import java.util.concurrent.atomic.AtomicInteger

/**
 * ═══ L4 · D2(a) — CHỖ DÀN DỰNG ẨN: màn ảo riêng của Kachi, không gắn vào ô nào ═══════════════════════════════════════
 *
 * Bố cục không có ô app sống (chỉ widget) ⇒ trước L4 chạy nền ra `NO_STAGE`, 0 lệnh, mà chuyến vẫn ghi "đã chạy" ([ĐO máy
 * ảo] `e2e/e6-no-app-slot`) — đúng ca owner báo 03/10. Lớp này cấp phần Android cho
 * [BehindHomeSequence.startBehindHidden] (`:core` giữ thứ tự + mọi quyết định):
 *  - [create]: `createVirtualDisplay` CÙNG cờ của màn ảo ô (`VdAppHost`: 8 = OWN_CONTENT_ONLY · 256 =
 *    DESTROY_CONTENT_ON_REMOVAL), mặt vẽ là một `ImageReader` không ai xem (khung được lấy-rồi-bỏ trên luồng riêng — không
 *    có người nhận thì app trong màn ảo nghẽn hàng đệm), cỡ + mật độ = display 0 thật (X ra sau màn nhà không phải đổi cấu
 *    hình). Đăng ký id với cổng ownership của kênh (`registerLauncherVirtualDisplay`) — không đăng ký thì `am start
 *    --display <id>` bị chặn ở `launcherSeam`. KHÔNG có lệnh `wm` nào (R0.7: không thêm trạng thái hệ thống bền — `wm`
 *    theo display ghi `display_settings.xml`).
 *  - [cover]: mở [StageCoverActivity] (của CHÍNH Kachi) lên đỉnh màn ảo bằng API trong tiến trình — được phép vì Kachi là
 *    chủ màn ảo riêng tư (A10 r47 `ActivityStackSupervisor.isCallerAllowedToLaunchOnDisplay` `:1067-1130`: activity cùng
 *    uid chủ + chủ gọi ⇒ cho). Nó đóng vai app C của ô: X không được ở ĐỈNH màn ảo nguồn lúc `move-task` (R0.2).
 *  - [uncover] / [release]: gỡ lớp che, nhả màn ảo — `:core` chỉ gọi [release] khi bản đọc thấy màn ảo đã TRỐNG.
 *  - L8 — [cover] / [uncover] cũng là [BehindHomeSequence.CoverPort] của nút *chạy nền* đầu ô
 *    (`BehindHomeSequence.evictCovered`): lớp che lên màn ảo CỦA Ô (cũng do Kachi tạo, cùng cờ 8|256, cùng luật
 *    `isCallerAllowedToLaunchOnDisplay`). Lượt đó KHÔNG gọi [create] / [release] — màn ảo của ô là của host ô.
 *
 * Bốn câu CLAUDE.md §4 cho các lệnh shell nhắm màn ảo này (K4 · K4-VIEW · K6 nguồn): **display** = đúng id vừa tạo (≥ 2,
 * đăng ký, Kachi sở hữu, không bao giờ display 0/1); **app** = đúng gói X người dùng chọn (+ lớp che của chính Kachi);
 * **loại stack** = `standard` (task mới của X / của lớp che); **hoàn tác** = X ra sau màn nhà bằng chuỗi BEHIND-HOME, không
 * ra được ⇒ K7 + dấu + K12; Kachi chết giữa chừng ⇒ hệ nhả màn ảo theo tiến trình, cờ 256 KẾT THÚC activity trên đó (A10 r47
 * `ActivityDisplay.remove` `:1120-1160`) thay vì đẩy lên display 0.
 */
internal class StagingDisplay(ctx: Context) : BehindHomeSequence.HiddenStagePort {

    private val app = ctx.applicationContext
    private val cover = ComponentName(app, StageCoverActivity::class.java)
    private var vdId: Int? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null

    /**
     * Khoá của màn ảo này ở [SlotVdOwner] (chủ DUY NHẤT của mọi màn ảo Kachi — luật `SlotHostingLifecycleContractTest`):
     * chủ [OWNER], "ô" ÂM riêng cho từng lượt — không bao giờ trùng ô thật (0…5, `adopt` giải phóng màn ảo CÙNG ô), và hai
     * lượt dàn không bao giờ nhả màn ảo của nhau (rào nhả D2: màn ảo bị GIỮ vì còn app người dùng thì lượt sau không đụng).
     */
    private var key: Int? = null

    override fun create(): Int? {
        vdId?.let { return it }
        return try {
            val dm = app.getSystemService(DisplayManager::class.java) ?: return null
            val m = DisplayMetrics().also { dm.getDisplay(Display.DEFAULT_DISPLAY)?.getRealMetrics(it) }
            if (m.widthPixels <= 0 || m.heightPixels <= 0 || m.densityDpi <= 0) return null
            val t = HandlerThread("kachi-stage").apply { start() }
            thread = t
            val r = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 2)
            reader = r
            r.setOnImageAvailableListener({ rr -> runCatching { rr.acquireLatestImage()?.close() } }, Handler(t.looper))
            val name = "kachi-stage-${System.currentTimeMillis()}"
            // lint WrongConstant: 256 = DESTROY_CONTENT_ON_REMOVAL (@hide) — CÙNG cờ, cùng lý do với `VdAppHost`.
            @SuppressLint("WrongConstant")
            val v = dm.createVirtualDisplay(name, m.widthPixels, m.heightPixels, m.densityDpi, r.surface, FLAGS)
            val id = v?.display?.displayId
            if (v == null || id == null) { runCatching { v?.release() }; release(); return null }
            val dispatcher = WindowCommandDispatcher.get(app)
            dispatcher.registerLauncherVirtualDisplay(id)
            // Tay cầm giao cho chủ sở hữu chung NGAY (cùng khuôn `VdAppHost`): gỡ đăng ký + `release` nằm trong `VdLease.free()`.
            val k = NEXT_KEY.getAndDecrement()
            SlotVdOwner.adopt(OWNER, k, name, VdLease(v, id, dispatcher::unregisterLauncherVirtualDisplay))
            key = k
            vdId = id
            Log.i(BehindHomeRunner.TAG, "stage create vd=$id ${m.widthPixels}x${m.heightPixels}@${m.densityDpi} key=$k")
            id
        } catch (e: RuntimeException) {
            Log.w(BehindHomeRunner.TAG, "stage create failed", e)
            release()
            null
        }
    }

    /**
     * Mở lớp che rồi CHỜ nó `onResume` trên đúng màn ảo (≤ [COVER_RESUME_MS]) — KDoc [StageCoverActivity]: giữ chỗ dựng
     * trước lượt resume đó bị hệ tỉa khỏi danh sách gần đây ([ĐO máy ảo `p3/e2e-L4/e6-hidden` lượt 1]).
     */
    override fun cover(vd: Int): Boolean = try {
        StageCoverActivity.arm(vd)
        val opts = ActivityOptions.makeBasic().setLaunchDisplayId(vd).toBundle()
        val i = Intent().setComponent(cover)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        app.startActivity(i, opts)
        StageCoverActivity.awaitResumed(COVER_RESUME_MS).also { if (!it) Log.w(BehindHomeRunner.TAG, "stage cover not resumed in ${COVER_RESUME_MS}ms vd=$vd") }
    } catch (e: RuntimeException) {
        Log.w(BehindHomeRunner.TAG, "stage cover failed vd=$vd", e)
        false
    }

    override fun uncover(): Int {
        StageCoverActivity.disarm()
        val am = app.getSystemService(ActivityManager::class.java) ?: return 0
        var n = 0
        for (t in runCatching { am.appTasks }.getOrDefault(emptyList())) {
            val base = runCatching { t.taskInfo?.baseIntent?.component }.getOrNull()
            if (base == cover && runCatching { t.finishAndRemoveTask() }.isSuccess) n++
        }
        return n
    }

    override fun release(vd: Int) = release()

    /** Nhả màn ảo của lượt (qua [SlotVdOwner] — gỡ đăng ký + `release`) rồi mặt vẽ + luồng. Idempotent. */
    fun release() {
        key?.let { k -> SlotVdOwner.release(OWNER, k) }
        key = null
        reader?.let { r -> runCatching { r.close() } }
        reader = null
        thread?.quitSafely()
        thread = null
        Log.i(BehindHomeRunner.TAG, "stage release vd=$vdId")
        vdId = null
    }

    private companion object {
        /** Chủ của các màn ảo dàn dựng ẩn ở [SlotVdOwner] — tách khỏi chủ của cây ô (`ws@…`). */
        const val OWNER = "stage"

        /** "Ô" âm, giảm dần mỗi lượt — xem KDoc [key]. */
        val NEXT_KEY = AtomicInteger(-1)

        /** 8 = OWN_CONTENT_ONLY · 256 = DESTROY_CONTENT_ON_REMOVAL — đúng cờ màn ảo ô (`VdAppHost`). */
        const val FLAGS = 8 or 256

        /** Trần chờ lớp che `onResume` — [ĐO máy ảo] ≈ 0,5 s sau lúc tạo (`e6-hidden` lượt 1: tạo 53.683 → resume 54.250). */
        const val COVER_RESUME_MS = 3_000L
    }
}
