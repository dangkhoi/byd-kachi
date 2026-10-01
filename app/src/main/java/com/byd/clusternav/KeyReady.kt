package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.modules.navaccess.KeyReadyPlan
import com.byd.clusternav.modules.navaccess.KeyReadyPlan.KeyStep

/**
 * ═══ READY-AT-HOME §4.8 — KIỂM PHÍM VÔ-LĂNG TRONG CHUỖI CHUẨN BỊ ════════════════════════════════════════════════
 *
 * Tái dùng 2.83, KHÔNG có đường chữa thứ hai: binder ([NavConnect.boundPerAccessibilityManager], 0 lệnh shell) → chờ
 * lớp 2 kết luận ([A11yLifecycleHeal.awaitMoXeVerdict] — lần mở xe, hoặc ân hạn khởi động khi tiến trình dựng lại lúc màn
 * đang bật: nếu kẹt BỀN thì chính lượt đó leo, tiến trình này chết và chuỗi chạy lại ở tiến trình mới)
 * → [NavConnect.grantAccessibility] (đúng hàm B1 gọi, single-flight
 * `grantingAcc`, tự bỏ khi đã gắn). Thứ tự + chờ do luật thuần [KeyReadyPlan] (test `:core`). B1 trong
 * `PermissionPreflight` vẫn ở nguyên chỗ làm lưới an toàn — lúc đó nó thấy BOUND (hoặc lượt trùng) và bỏ qua.
 *
 * ⚠ Cả hai hàm CHẶN — chỉ gọi trên luồng nền.
 */
internal object KeyReady {

    /**
     * Chuỗi chuẩn bị: hỏi → (chờ lớp 2) → cấp nếu cần. Trả nhãn kết quả cho dòng `KachiReady keys=…`.
     */
    fun prepare(app: Context): String {
        val voice = Prefs.voiceKeyEnabled(app)
        var waited = false
        var step = KeyReadyPlan.step(
            voice, NavConnect.boundPerAccessibilityManager(app), A11yLifecycleHeal.moXePending(app),
            waitedLayer2 = false, bornFromOwnHeal = A11yLifecycleHeal.bornFromOwnHeal(),
        )
        if (step == KeyStep.WAIT_LAYER2) {
            waited = A11yLifecycleHeal.awaitMoXeVerdict(app, KeyReadyPlan.KEY_HOLD_MAX_MS)
            step = KeyReadyPlan.step(
                voice, NavConnect.boundPerAccessibilityManager(app), A11yLifecycleHeal.moXePending(app),
                waitedLayer2 = true, bornFromOwnHeal = A11yLifecycleHeal.bornFromOwnHeal(),
            )
        }
        // Review lượt 1 [P2] (E2E 02/10 ca 1): bản trước in `keys=GRANT` lúc lượt cấp VỪA BẮT ĐẦU, rồi summary + màn Chẩn
        // đoán giữ nguyên chữ đó trong khi lượt cấp kết thúc NOT_BOUND (kẹt, pha RUNNING không leo — luật 2.83) ⇒ phím chết
        // mà nhật ký nói như đã cấp. Nay: nhãn "đang cấp" trước, KẾT QUẢ thật ghi lại khi lượt cấp xong (cùng hàm B1 —
        // `grantAccessibility` chỉ là vỏ của `grantAccessibilityDetailed`; luồng riêng của NavConnect, single-flight).
        val suffix = if (waited) "(after-layer2)" else ""
        val label = (if (step == KeyStep.GRANT) "GRANT(pending)" else step.name) + suffix
        KachiReadyLog.keys(label)
        if (step == KeyStep.GRANT) NavConnect.grantAccessibilityDetailed(app) { r -> KachiReadyLog.keys("GRANT->$r$suffix"); confirmLateBind(app, r, suffix) }
        return label
    }

    /**
     * `NOT_BOUND` của lượt cấp CHƯA phải sự thật cuối: lượt này có thể THUA single-flight `grantingAcc` (một lượt khác —
     * alarm, keep-alive, B1 — đang toggle và sẽ gắn được), hoặc hệ gắn chậm. [ĐO máy ảo 02/10 01:45:25.824]
     * `keys=GRANT->NOT_BOUND` trong khi lượt thắng ghi `đã BOUND` lúc 27.523 ⇒ dòng summary/màn Chẩn đoán nói phím chết
     * trong khi phím sống. Hỏi binder (0 lệnh shell, cùng nguồn `mBoundServices` với dump) mỗi
     * [KeyReadyPlan.LATE_BIND_POLL_MS] tới [KeyReadyPlan.LATE_BIND_WATCH_MS]: gắn ⇒ `GRANT->BOUND(late)`; không ⇒ giữ
     * `GRANT->NOT_BOUND` (đúng sự thật, vd kẹt ở pha RUNNING). Callback chạy trên luồng CHÍNH ⇒ chờ trên luồng nền ngắn hạn.
     * CHỈ ĐỌC — không cấp, không toggle (không có đường chữa thứ hai).
     */
    private fun confirmLateBind(app: Context, r: NavConnect.GrantResult, suffix: String) {
        if (r != NavConnect.GrantResult.NOT_BOUND) return
        val watch = Thread({
            val until = android.os.SystemClock.elapsedRealtime() + KeyReadyPlan.LATE_BIND_WATCH_MS
            while (android.os.SystemClock.elapsedRealtime() < until) {
                try {
                    Thread.sleep(KeyReadyPlan.LATE_BIND_POLL_MS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@Thread
                }
                if (NavConnect.boundPerAccessibilityManager(app) == true) {
                    KachiReadyLog.keys("GRANT->BOUND(late)$suffix")
                    return@Thread
                }
            }
        }, "kachi-keys-confirm")
        watch.isDaemon = true
        watch.start()
    }

    /**
     * Ô có phải chờ trước khi gắn app không (R-A2 của 2.83): chỉ khi lớp 2 đang/sắp xét một lần mở xe mà binder nói CHƯA
     * gắn — lớp 2 có thể force-stop launcher, gắn ô trước đó là app ô mở hai lần. Chờ tối đa
     * [KeyReadyPlan.KEY_HOLD_MAX_MS] rồi bỏ chờ (fail-open). Ca thường (đã gắn): 0 ms.
     */
    fun holdTileIfEscalating(app: Context) {
        if (!KeyReadyPlan.holdTile(Prefs.voiceKeyEnabled(app), NavConnect.boundPerAccessibilityManager(app), A11yLifecycleHeal.moXePending(app))) return
        val t0 = android.os.SystemClock.elapsedRealtime()
        val done = A11yLifecycleHeal.awaitMoXeVerdict(app, KeyReadyPlan.KEY_HOLD_MAX_MS)
        KachiReadyLog.line("keys=HOLD(${android.os.SystemClock.elapsedRealtime() - t0})->${if (done) "VERDICT" else "TIMEOUT"}")
    }
}
