package com.byd.clusternav.modules.clustercast.simplified

/** Kiểu cụm Kachi TIN đang hiện — `UNKNOWN` khi không có bằng chứng (sổ trống, `pending`, tiến trình trước). */
enum class BelievedStyle {
    CURVED, RECT, UNKNOWN;

    companion object {
        fun of(style: CastStyle?): BelievedStyle = when (style) {
            CastStyle.CURVED -> CURVED
            CastStyle.RECT -> RECT
            null -> UNKNOWN
        }
    }
}

/**
 * ═══ CLUSTER-THEME-SAFE B1a (2.89) — QUYẾT ĐỊNH KIỂU CỤM của một lượt mở chiếu (thuần, không chạy lệnh) ═══════════════════
 *
 * Đầu vào: kiểu người lái chọn (`desired`), công thức đời xe (`styleOps` · `nativeStyle`), sổ theme ([ThemeLedger]), lời
 * đáp của cổng ([ThemeVerdict], cổng = [ClusterThemeGuard] + [ClusterThemePlan]). Đầu ra ([Plan]): opcode theme cần gửi
 * TRƯỚC `castSeq` (hoặc không), kiểu cụm tin là đang hiện sau lượt mở, và có DỪNG lượt mở không.
 *
 * ## Luật (bảng B.3 của nghiên cứu 05/10)
 *  1. Kiểu áp được ([effective]): `desired` nếu đời xe cho ([ProjectionRecipe.offers]); RECT bị ẩn ⇒ CURVED; không có opcode
 *     kiểu nào (DiLink 5) ⇒ không gửi gì, kiểu UNKNOWN.
 *  2. `desired ≠ native` (Bo tròn trên Seal 10.25", hoặc native chưa biết) ⇒ cần `styleOps[desired]` — đúng hành vi đã chạy
 *     ở lần mở đầu sau nổ máy từ 08/02 [ĐO F2].
 *  3. 2.90 · R2 — GỠ luật "sổ ghi `<opcode gốc>;ok` ⇒ không gửi": [ĐO xe 06/10] theme giữ qua nổ máy, sổ của tiến trình trước
 *     không nói được cụm đang ở kiểu nào. Kiểu nào cũng xin opcode của nó; lượt trùng trong CÙNG tiến trình do cổng đỡ bằng dấu
 *     RAM ([ClusterThemePlan.Reason.SAME_THEME]).
 *  4. Cổng [ThemeVerdict.SEND] ⇒ gửi; kiểu tin = kiểu đã ép.
 *  5. Cổng [ThemeVerdict.SKIP_KNOWN] (màn ảo cụm có từ trước) ⇒ bỏ opcode, đi tiếp 16/35; kiểu tin = [ThemeLedger.believed]
 *     (chỉ cùng tiến trình — 2.90 · R2; không thì UNKNOWN ⇒ khung trọn cụm, `CastSessionStyle.fullFrame`).
 *  6. Cổng [ThemeVerdict.ABORT] ⇒ bỏ opcode; đi tiếp CHỈ KHI sổ (cùng tiến trình) chứng minh cụm đã ở đúng kiểu cần — không thì
 *     DỪNG lượt mở.
 *
 * ## Vì sao luật 6 giữ DỪNG (đối chiếu yêu cầu "UNREADABLE ⇒ bỏ theme, đi tiếp 16/35 trừ khi 16/35 không an toàn")
 *  • Về SẬP: 16/35 an toàn ở mọi trạng thái màn ảo — [ĐO xe 05/10 §4] gửi lại `16`/`35` khi Maps đang trên màn ảo 8 ⇒
 *    "không đổi gì"; DashCast mặc định luôn `16 →3s→ 35` không opcode theme [ĐO source F12]; cả hai lần sập đều do opcode
 *    THEME (30) [ĐO]. ⇒ khi sổ chứng minh cụm đã đúng kiểu (vd đã ép 30 trong tiến trình này) thì bỏ theme + đi tiếp.
 *  • Về NGƯỜI LÁI: không chứng minh được thì lượt mở có thể rơi vào theme gốc — Seal 10.25" ⇒ "m/h lạc góc, MẤT số km/h"
 *    [ĐO xe 05/10 §4, `cluster-rect-seal-2026-10-05.md` §3] cả phiên, mà người lái chọn Bo tròn. Không còn đồng hồ tốc độ
 *    trên cụm là KHÔNG an toàn cho người đang lái (CLAUDE.md: đúng > an toàn > nhanh) ⇒ DỪNG (Error có lý do, bật lại được) —
 *    giữ đúng chốt R1-1 của review Pass 1. 2.90: Kachi KHÔNG vẽ km/h (owner 06/10 — HUD, bản đồ, bóng VietMap đều có).
 */
object ClusterStylePlan {

    /**
     * @param themeOp opcode theme gửi TRƯỚC `castSeq`; `null` = không gửi.
     * @param believed kiểu cụm tin là đang hiện sau lượt mở.
     * @param abort DỪNG lượt mở (không 16/35).
     * @param why một dòng cho log / `ClusterDiag`.
     */
    data class Plan(val themeOp: Int?, val believed: BelievedStyle, val abort: Boolean, val why: String)

    /** Luật 1 — kiểu áp được trên đời xe này; `null` = đời xe không có opcode kiểu nào. */
    fun effective(recipe: ProjectionRecipe, desired: CastStyle): CastStyle? = when {
        recipe.offers(desired) -> desired
        recipe.offers(CastStyle.CURVED) -> CastStyle.CURVED
        else -> null
    }

    /** Luật 2–3 — opcode theme cần xin cổng; `null` = không cần gửi theme. */
    @Suppress("UNUSED_PARAMETER")   // [ledger] giữ cho chữ ký ổn định (ThemeGatePreview / ProjectionManager) — 2.90 không còn dùng
    fun wanted(recipe: ProjectionRecipe, desired: CastStyle, ledger: ThemeLedger.Entry?): Int? {
        val style = effective(recipe, desired) ?: return null
        return recipe.styleOps[style]
    }

    /**
     * Luật 1–6. [want] PHẢI là [wanted] của cùng đầu vào; [verdict] là lời cổng cho [want] (`null` khi [want] `null` —
     * [want] khác `null` mà [verdict] `null` là lỗi gọi ⇒ coi như [ThemeVerdict.ABORT], hướng an toàn).
     */
    fun decide(
        recipe: ProjectionRecipe,
        desired: CastStyle,
        ledger: ThemeLedger.Entry?,
        want: Int?,
        verdict: ThemeVerdict?,
        now: ThemeLedger.Now,
    ): Plan {
        val style = effective(recipe, desired)
            ?: return Plan(null, BelievedStyle.UNKNOWN, abort = false, why = "đời xe không có opcode kiểu — chỉ chuỗi chiếu")
        val target = BelievedStyle.of(style)
        if (want == null) {
            return Plan(null, BelievedStyle.UNKNOWN, abort = false, why = "kiểu $style không có opcode ⇒ không gửi theme")
        }
        val fromLedger = ThemeLedger.believed(ledger, recipe, now)
        return when (verdict ?: ThemeVerdict.ABORT) {
            ThemeVerdict.SEND -> Plan(want, target, abort = false, why = "gửi $want ⇒ $style")
            ThemeVerdict.SKIP_KNOWN ->
                Plan(null, fromLedger, abort = false, why = "bỏ $want (màn ảo cụm có từ trước), 16/35 đi tiếp; kiểu theo sổ = $fromLedger")
            ThemeVerdict.ABORT ->
                if (fromLedger == target) {
                    Plan(null, fromLedger, abort = false, why = "bỏ $want; sổ (tiến trình này) cho biết cụm đã ở $style ⇒ 16/35 đi tiếp")
                } else {
                    Plan(null, fromLedger, abort = true, why = "DỪNG: bỏ $want mà không chứng minh được cụm ở $style (sổ: $fromLedger)")
                }
        }
    }
}
