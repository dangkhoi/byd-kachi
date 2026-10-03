package com.byd.clusternav.launcher.behind

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Display
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ═══ L4 · D2(a) — LỚP CHE trên màn ảo dàn dựng ẩn ═══════════════════════════════════════════════════════════════════
 *
 * Đóng vai app C của ô trong chuỗi R0.3: X phải KHÔNG ở đỉnh màn ảo nguồn lúc `am stack move-task` (R0.2 — `TaskRecord.reparent`
 * A10 `:728-749`: nguồn ở đỉnh ⇒ S bị đưa lên che màn nhà). Chỉ `StagingDisplay` mở nó (`exported=false`), lên một màn ảo
 * CỦA KACHI: màn ảo ẩn (L4, không ai nhìn) hoặc — L8, nút *chạy nền* của ô — màn ảo CỦA Ô, đứng trên app của ô (thấy được
 * trong ô ≈ thời gian chuỗi chạy: nền đen của theme). Bị gỡ bằng `AppTask.finishAndRemoveTask` ngay sau lượt đẩy. Không
 * dùng `Theme.NoDisplay` (theme đó đòi `finish()` trước `onResume` — lớp che phải SỐNG tới lúc đẩy xong).
 *
 * ## Vì sao bên mở phải CHỜ [onResume] [ĐO máy ảo 03/10, `p3/e2e-L4/e6-hidden` lượt 1]
 * Lớp che `onResume` muộn ≈ 0,5 s sau lúc tạo; giữ chỗ (anchor, `excludeFromRecents`) dựng TRONG khoảng đó ⇒ lượt resume
 * của lớp che đưa task của nó lên đầu danh sách gần đây ⇒ giữ chỗ không còn là task mới nhất ⇒ hệ TỈA nó
 * (`am_finish_activity … BehindAnchorActivity,recent-task-trimmed`; A10 r47 `RecentTasks.isInVisibleRange`: task
 * `excludeFromRecents` chỉ được giữ khi đứng đầu) ⇒ `pickAnchor` = Missing ⇒ không đẩy được. Ô thật không gặp vì K3 đưa
 * app C lên (và resume) TRƯỚC khi dựng giữ chỗ.
 *
 * ## Rào an toàn
 * Lớp che `onResume` trên display KHÁC màn ảo được hẹn (ROM không tôn trọng cờ 256 và đẩy nó lên display 0 khi nhả màn ảo —
 * [CHƯA BIẾT] trên ROM BYD) ⇒ tự gỡ ngay: một màn đen của chính Kachi không bao giờ được đứng trên màn nhà.
 */
class StageCoverActivity : Activity() {

    override fun onResume() {
        super.onResume()
        val on = shownOn()
        val want = expectedVd
        if (want < 1 || on != want) {
            Log.w(BehindHomeRunner.TAG, "stage cover resumed on display $on (want $want) -> self-remove")
            finishAndRemoveTask()
            return
        }
        resumed?.countDown()
    }

    /**
     * Display lớp che đang nằm (soát 2.87 · P3 — mỗi mức API dùng API HIỆN HÀNH của mức đó). API 30+: `Context.getDisplay()`
     * [ĐO nguồn A12 r34 `ContextImpl.java:2820-2843`: Activity là ngữ cảnh gắn display; bị dời display ⇒
     * `ActivityThread.java:5906-5908` `dispatchMovedToDisplay` cập nhật]. Không đọc được ⇒ `INVALID_DISPLAY` ⇒ lệch hẹn ⇒ tự gỡ
     * (an toàn). API 29 (A10): `WindowManager.getDefaultDisplay()` — API hiện hành ở mức đó (deprecated từ 30); WindowManager
     * của Activity gắn đúng display của nó.
     */
    private fun shownOn(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.displayId ?: Display.INVALID_DISPLAY else legacyDisplayId()

    @Suppress("DEPRECATION")   // chỉ chạy ở API 29 (nhánh dưới `R` của [shownOn]) — ở đó đây là API hiện hành
    private fun legacyDisplayId(): Int = windowManager.defaultDisplay.displayId

    companion object {
        /** Màn ảo được hẹn cho lượt che hiện tại (`StagingDisplay` đặt trước khi mở). Mutex `kachi-behind` ⇒ một lượt một lúc. */
        @Volatile internal var expectedVd: Int = -1
            private set

        @Volatile private var resumed: CountDownLatch? = null

        /** Hẹn lượt che trên [vd] — gọi TRƯỚC `startActivity`. */
        internal fun arm(vd: Int) {
            expectedVd = vd
            resumed = CountDownLatch(1)
        }

        /** Chờ lớp che `onResume` trên màn ảo được hẹn, tối đa [timeoutMs]. */
        internal fun awaitResumed(timeoutMs: Long): Boolean = try {
            resumed?.await(timeoutMs, TimeUnit.MILLISECONDS) ?: false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        /** Bỏ hẹn (sau lượt) — lớp che mồ côi resume về sau sẽ tự gỡ. */
        internal fun disarm() {
            expectedVd = -1
            resumed = null
        }
    }
}
