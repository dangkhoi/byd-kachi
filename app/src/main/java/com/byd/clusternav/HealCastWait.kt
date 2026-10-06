package com.byd.clusternav

import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.HealPhase
import com.byd.clusternav.modules.navaccess.HealCastDeferral

/**
 * ═══ 2.93 · READY-RESTART-MID-CAST — chỗ nối `:app` của [HealCastDeferral] (luật + vòng chờ thuần ở `:core`) ════════════════════
 *
 * Gọi DUY NHẤT từ `A11yLifecycleHeal.healIfStuck`, sau khi đã thấy KẸT BỀN, BAO nấc leo (`NavConnect.escalateOnLifecycle`, mở đầu
 * bằng `am force-stop` chính Kachi). Chiếu cụm đang có thao tác bay / opcode theme chưa đủ 15 s ([SimpleCastRuntime.restartHazard])
 * ⇒ chờ trong pha, có trần; mọi thứ khác của lượt chữa (đo, chờ 5 s, đo lại, nấc leo, lệnh tách rời) giữ nguyên từng bước
 * (CLAUDE.md §6).
 *
 * 2.93 wave 2A · HEAL-DEFER-GATE-RECHECK (spec `kachi-293-wave2a.html` §4.5): lượt tắt-máy, cổng cuối của nấc leo hỏi LẠI mối nguy
 * ngay trước lệnh tách rời — thao tác chiếu bắt đầu trong ~1–2,5 s đọc của nấc leo ⇒ không bắn, chờ rồi leo lại với bản đọc mới
 * ([HealCastDeferral.escalate]). Pha khác: không chờ, cổng = pha (đường cũ).
 *
 * [ĐO log xe 06/10] lượt tắt-máy bắn 15:17:31.392 khi lượt mở chiếu mới tới `16` (31 lúc 27.751) ⇒ tiến trình mới `TOO_SOON`.
 * Dòng `KachiReady` `keys-defer …` (vào `usage-*.log` + màn Chẩn đoán qua tag) cho buổi xe đo được lượt chờ thật (🚗).
 */
internal object HealCastWait {

    private const val TAG = "A11yLifecycle"

    /**
     * Chặn (luồng nối tiếp của `A11yLifecycleHeal`). [fire] chạy nấc leo với cổng cuối nó trao vào; trả kết quả nấc leo cuối, hoặc
     * `null` = pha qua (hoặc luồng bị ngắt) trong lúc chờ ⇒ bên gọi CẮT như nhánh chờ đo lại.
     *
     * @param anchorAt mốc neo của pha — CÙNG giá trị bên gọi trao cho cổng cuối (`stillInPhase`).
     */
    fun <R> escalate(phase: HealPhase, anchorAt: Long, note: String, inPhase: () -> Boolean, fire: (gate: () -> Boolean) -> R): R? =
        HealCastDeferral.escalate(
            phase,
            // Review CAST F1: chỉ lượt tắt-máy được chờ (pha khác: HealCastDeferral.escalate đi thẳng đường cũ, không hỏi, không log).
            until = HealCastDeferral.waitUntil(phase, anchorAt, SystemClock.elapsedRealtime()),
            // Hỏi hỏng (không thể theo mã hiện tại — chỉ đọc RAM + prefs) ⇒ coi như yên: lượt chờ không bao giờ được làm gãy lượt chữa.
            hazard = { runCatching { SimpleCastRuntime.restartHazard()?.name }.getOrNull() },
            inPhase = inPhase,
            nowMs = SystemClock::elapsedRealtime,
            sleepMs = { Thread.sleep(it) },
            onWait = { r ->
                if (r.outcome != HealCastDeferral.Outcome.CLEAR) {
                    KachiReadyLog.line("keys-defer note=$note hazard=${r.hazard} waited=${r.waitedMs} -> ${r.outcome}")
                    Log.i(TAG, "$note: chiếu cụm đang dở (${r.hazard}) ⇒ chờ ${r.waitedMs} ms → ${r.outcome} (${if (r.outcome.fire) "leo" else "cắt"})")
                }
            },
            onRecheck = { h, n ->
                KachiReadyLog.line("keys-defer note=$note gate hazard=$h recheck=$n -> RECHECK")
                Log.i(TAG, "$note: cổng cuối thấy chiếu cụm vừa bắt đầu ($h) ⇒ KHÔNG bắn, chờ rồi leo lại (lần $n)")
            },
            fire = fire,
        )
}
