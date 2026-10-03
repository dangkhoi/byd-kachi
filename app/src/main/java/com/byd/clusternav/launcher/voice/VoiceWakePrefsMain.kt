package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.voiceConfirmIds
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ FIX286 · VK1 + VK4 — phía TIẾN TRÌNH CHÍNH của chế độ `:wake` ════════════════════════════════════════════════
 *
 * Tiến trình chính là nơi DUY NHẤT ghi `clusternav_prefs`, nên cache `SharedPreferences` của nó là sự thật; `:wake`
 * thì không ([VoiceWakePrefs] — trích AOSP r47 `ContextImpl.java:447-474`). Bốn việc, cả bốn CHỈ chạy ở tiến trình chính:
 *  1. [mode] — chế độ hiện hành ([VoiceWakeMode]) cho `VoiceWakeService.sync` (bật/tắt FGS), `VoiceEntry` (nút mic
 *     màn đi đâu) và `VoiceEngine.preload` (chính có nạp sẵn không). Một hàm, ba chỗ hỏi — không chép luật lần hai.
 *  2. [collect] — năm giá trị `:wake` cần đọc tươi, đi vào ảnh chụp ngữ pháp (`VoiceGrammarSnapshotStore.write`).
 *  3. [publish] — ghi lại ảnh chụp ngay (setter của các khoá ấy + `sync` gọi). Store tự chặn tiến trình phụ.
 *  4. [handOverToWake] — (senior review Pass 2) trả bản mô hình của chính khi chế độ vừa chuyển sang `:wake`.
 *
 * ⚠ `:wake` KHÔNG gọi tệp này: [keyHold] đọc `Prefs.voiceKeyBindings` — READER tự GHI khi migrate (bài canh
 * `VoiceWakeIsolationContractTest` cấm nó trong mọi tệp của `:wake`). `:wake` đọc chế độ qua [VoiceWakeHold.modeInWake].
 */
object VoiceWakePrefsMain {

    /** Phím vô-lăng nào đang mở phiên Kachi nghe (và công tắc "Nhận nút vật lý" đang bật) — [VoiceWakeMode.keyHold]. */
    fun keyHold(ctx: Context): Boolean =
        VoiceWakeMode.keyHold(Prefs.voiceKeyEnabled(ctx), Prefs.voiceKeyBindings(ctx), Prefs.VK_TARGET_KACHI_VOICE)

    /** Chế độ hiện hành: wake HIỆU LỰC (công tắc ∧ cầu chì chưa nổ) ∨ phím gán Kachi nghe. */
    fun mode(ctx: Context): VoiceWakeMode = VoiceWakeMode.of(Prefs.wakeEnabled(ctx), keyHold(ctx))

    /** Năm giá trị `:wake` đọc tươi. Công tắc lấy BẢN THÔ: cầu chì là tệp marker, `:wake` tự đọc (không cache). */
    fun collect(ctx: Context): VoiceWakePrefs = VoiceWakePrefs(
        wakeSwitch = Prefs.wakeSwitchOn(ctx),
        keyHold = keyHold(ctx),
        confirmIds = Prefs.voiceConfirmIds(ctx),
        navDefault = Prefs.voiceNavDefaultApp(ctx),
        musicDefault = Prefs.voiceMusicDefaultApp(ctx),
    )

    /** Công bố ảnh chụp mới cho `:wake` NGAY (ghi đồng bộ, nguyên tử; trùng nội dung thì không chạm đĩa). */
    fun publish(ctx: Context) = VoiceGrammarSnapshotStore.write(WorkspacePrefs(ctx.applicationContext))

    /** Một lượt trả bản tại một thời điểm (`sync` chạy ở mỗi `onResume`). */
    private val handingOver = AtomicBoolean(false)

    /**
     * ═══ [Senior review FIX286 Pass 2 · P2] — TRẢ bản mô hình của tiến trình chính khi nó đã chuyển sang `:wake` ═══
     *
     * `VoiceWakeService.sync` gọi SAU khi đã bật được `:wake` (chế độ HOLD/WAKE). Chế độ đổi GIỮA đời tiến trình (gán
     * phím · bật công tắc · đổi hồ sơ) thì bản đã nạp sẵn ở đây nằm lại cạnh bản `:wake` sắp nạp ⇒ hai bản trên đầu xe
     * 56–94 MB trống, tới lần tắt máy kế (luật ở [VoicePreloadPolicy.shouldHandOverToWake]). Chiều ngược (về OFF) là
     * `sync` nạp sẵn lại ở đây (D-VK4).
     *
     * KHÔNG chặn ai: luồng nền riêng, [VoiceEngine.tryRelease] không chờ khoá (đang nạp / đang giải mã ⇒ BUSY); điều
     * kiện đọc lại DƯỚI khoá dựng (chế độ có thể vừa đổi ngược; phiên có thể vừa mở). Cấm `VoiceEngine.release()` ở
     * đây — chờ cả lượt nạp/giải mã (cùng lẽ VK3).
     *
     * [Senior review FIX286 Pass 3 · P2] Bận/bỏ thì HỎI LẠI mỗi [VoiceWakeStandDown.POLL_MS] trên chính luồng nền này
     * (luật thuần [VoicePreloadPolicy.shouldRetryHandOver]), và cổng vào tính cả "ĐANG nạp": bản Pass 2 thử một lần rồi
     * trông vào `onResume` kế, nên lọt (a) chế độ đổi lúc chính đang nạp và (b) "đổi hồ sơ" nói bằng giọng từ phiên
     * in-process — tấm chữ là overlay, Activity không pause, không `onResume` nào tới (KDoc chính sách ở `:core`).
     */
    fun handOverToWake(ctx: Context) {
        if (!(VoiceEngine.loaded() || VoiceEngine.loading()) || !handingOver.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        Thread({
            try {
                var waited = 0L
                while (true) {
                    val r = VoiceEngine.tryRelease {
                        VoicePreloadPolicy.shouldHandOverToWake(
                            modelInWake = runCatching { mode(app).modelInWake }.getOrDefault(false),
                            sessionRunning = VoiceSession.anyRunning(),
                        )
                    }
                    val again = VoicePreloadPolicy.shouldRetryHandOver(r, runCatching { mode(app).modelInWake }.getOrDefault(false), waited)
                    if (!again) {
                        Log.i(TAG, "trả bản mô hình của tiến trình chính (mô hình đã ở :wake): $r" + if (waited > 0) " · sau ${waited / 1000} s chờ" else "")
                        break
                    }
                    Thread.sleep(VoiceWakeStandDown.POLL_MS)
                    waited += VoiceWakeStandDown.POLL_MS
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "trả bản mô hình của tiến trình chính lỗi", e)
            } catch (e: InterruptedException) {
                // `Thread.sleep` ném checked — không bắt thì luồng chết bằng ngoại lệ không ai bắt ⇒ sập tiến trình chính.
                Log.w(TAG, "trả bản mô hình của tiến trình chính bị ngắt", e)
                Thread.currentThread().interrupt()
            } finally {
                handingOver.set(false)
            }
        }, "KachiVoiceHandOver").apply { isDaemon = true }.start()
    }

    private const val TAG = "KachiVoiceEngine"
}
