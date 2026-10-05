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
 *  3. `desired == native` (Chữ nhật trên Seal 10.25"): sổ ghi `<opcode gốc>;ok` ⇒ KHÔNG gửi (cụm đã ở kiểu gốc: ép bằng chính
 *     opcode gốc trong lần nổ máy này, hoặc đã về gốc sau nổ máy [ĐO-gv F3]) — đi thẳng `16 → 35` [ĐO-gv F4]. Sổ trống /
 *     `pending` / opcode khác ⇒ cần opcode gốc (gửi thừa ở mức A vô hại [SUY], cùng điều kiện với F2).
 *  4. Cổng [ThemeVerdict.SEND] ⇒ gửi; kiểu tin = kiểu đã ép.
 *  5. Cổng [ThemeVerdict.SKIP_KNOWN] (màn ảo cụm có từ trước) ⇒ bỏ opcode, đi tiếp 16/35; kiểu tin = [ThemeLedger.believed].
 *  6. Cổng [ThemeVerdict.ABORT] ⇒ bỏ opcode; đi tiếp CHỈ KHI sổ chứng minh cụm đã ở đúng kiểu cần ([ThemeLedger.believed]: cùng
 *     tiến trình, hoặc `<opcode gốc>;ok` bất kể tiến trình — Pass 3 · cluster-r2-3) — không thì DỪNG lượt mở.
 *
 * ## Vì sao luật 6 giữ DỪNG (đối chiếu yêu cầu "UNREADABLE ⇒ bỏ theme, đi tiếp 16/35 trừ khi 16/35 không an toàn")
 *  • Về SẬP: 16/35 an toàn ở mọi trạng thái màn ảo — [ĐO xe 05/10 §4] gửi lại `16`/`35` khi Maps đang trên màn ảo 8 ⇒
 *    "không đổi gì"; DashCast mặc định luôn `16 →3s→ 35` không opcode theme [ĐO source F12]; cả hai lần sập đều do opcode
 *    THEME (30) [ĐO]. ⇒ khi sổ chứng minh cụm đã đúng kiểu (vd đã ép 30 trong tiến trình này) thì bỏ theme + đi tiếp.
 *  • Về NGƯỜI LÁI: không chứng minh được thì lượt mở có thể rơi vào theme gốc — Seal 10.25" ⇒ "m/h lạc góc, MẤT số km/h"
 *    [ĐO xe 05/10 §4, `cluster-rect-seal-2026-10-05.md` §3] cả phiên, mà người lái chọn Bo tròn. Không còn đồng hồ tốc độ
 *    trên cụm là KHÔNG an toàn cho người đang lái (CLAUDE.md: đúng > an toàn > nhanh) ⇒ DỪNG (Error có lý do, bật lại được) —
 *    giữ đúng chốt R1-1 của review Pass 1. Số km/h do Kachi vẽ (B.7) chưa có ở B1a.
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
    fun wanted(recipe: ProjectionRecipe, desired: CastStyle, ledger: ThemeLedger.Entry?): Int? {
        val style = effective(recipe, desired) ?: return null
        val op = recipe.styleOps[style] ?: return null
        val nativeAlready = style == recipe.nativeStyle && ledger?.state == ThemeLedger.State.OK && ledger.op == op
        return if (nativeAlready) null else op
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
            return Plan(null, target, abort = false, why = "kiểu $style = kiểu gốc, sổ ghi lần ép gần nhất đã là opcode gốc ⇒ không gửi theme")
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
