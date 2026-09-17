package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ColorMath.hex
import com.byd.clusternav.launcher.ColorMath.parse

/**
 * ═══ VISUAL-REFRESH P1b · R8 — SUY BẢNG MÀU THEO LỰA CHỌN CỦA NGƯỜI DÙNG ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-visual-refresh.html` §R8: *"vì mọi màu đã đi qua **một** bảng token nên chọn màu = đổi gốc
 * token, không sửa từng màn"*. Tệp này là phép **đổi gốc** đó: nhận [KachiPalette] gốc (TỐI/SÁNG) + [ColorChoice] và
 * trả về một [KachiPalette] mới. Không có mã hex ở đây — hạt giống nằm ở [KachiPaletteSeeds.ACCENT_SEEDS_DARK]/
 * [KachiPaletteSeeds.ACCENT_SEEDS_LIGHT]; phép tính ở `:core` ([ColorMath] · [ContrastGuard]).
 *
 * ## Bất biến
 *  • [ColorChoice.DEFAULT] ⇒ trả về **chính** bảng gốc (cùng thực thể) — người không chọn gì thấy đúng 1.69, không
 *    lệch một byte (`ColorChoiceContractTest.mac dinh la chinh bang goc`).
 *  • Mọi vai nhấn đổi **cùng nhau** qua [ColorMath.recolor]: giữ khoảng cách sắc (accent2 lệch +30°), giữ alpha,
 *    dịch bậc sáng theo hạt giống. Không có `if (accent == VIOLET)` ở chỗ vẽ nào — CLAUDE.md §7.
 *  • AC8.5 — sau khi đổi gốc, bốn cặp chữ/nền hay hỏng nhất đi qua [ContrastGuard] (đổi mực trước, đẩy nền sau):
 *    chữ trên nút nhấn đặc · chữ trên ô BẬT · chữ trên thẻ BẬT · mực nhấn trên thẻ thường. Bảng đo sinh bằng máy
 *    (`contrast-table-colors.md`) chấm ❌ ở bất kỳ cặp nào còn dưới sàn.
 */
internal fun KachiPalette.derive(choice: ColorChoice, artDominant: IntArray?, dark: Boolean): KachiPalette {
    var p = this
    var changed = false
    // Tông TRƯỚC, màu nhấn SAU, rồi mới bảo vệ tương phản: lớp bảo vệ phải nhìn thấy đúng nền (`tile`, `bg`) mà chữ
    // sẽ nằm lên — [ĐO] thứ tự ngược lại để `inkOnAccent` tụt còn 4.22–4.48 ở tông ẤM/LẠNH vì `tile` bị nhuộm sau khi
    // đã đo. Không có gì đổi ⇒ trả về CHÍNH bảng gốc (cùng thực thể).
    if (choice.tone != CardTone.NEUTRAL) { p = p.toned(choice.tone); changed = true }
    seedOf(choice.accent, artDominant, dark)?.let { p = p.recolored(it); changed = true }
    return if (changed) p.guarded() else p
}

/**
 * Hạt giống của một ô chọn: `null` = giữ bảng gốc (ô mặc định, hoặc *theo ảnh nền* khi chưa có ảnh —
 * AC8.1: ô đó không được làm màn trắng tay chỉ vì chưa bật hình nền).
 */
internal fun KachiPalette.seedOf(accent: AccentChoice, artDominant: IntArray?, dark: Boolean): Int? = when (accent) {
    AccentChoice.KACHI_BLUE -> null
    AccentChoice.FROM_ART -> artDominant?.let { DominantColors.accentSeed(it, dark) }
    else -> (if (dark) KachiPaletteSeeds.ACCENT_SEEDS_DARK else KachiPaletteSeeds.ACCENT_SEEDS_LIGHT)[accent]?.let(::parse)
}

/** Màu xem trước cho ô chọn ở Cài đặt; `null` = *theo ảnh nền* mà chưa có ảnh (ô vẽ dấu hỏi thay vì bịa một màu). */
internal fun KachiPalette.accentPreview(accent: AccentChoice, artDominant: IntArray?, dark: Boolean): Int? =
    if (accent == AccentChoice.KACHI_BLUE) parse(this.accent) else seedOf(accent, artDominant, dark)

/** Đổi HỌ MÀU của mọi vai nhấn theo [seed] — chỉ đổi sắc, chưa bảo vệ tương phản (việc của [guarded]). */
private fun KachiPalette.recolored(seed: Int): KachiPalette {
    val base = parse(accent)
    fun rc(role: String): String = hex(ColorMath.recolor(parse(role), base, seed))
    return copy(
        accent = rc(accent), accent2 = rc(accent2), accentInk = rc(accentInk),
        gradFrom = rc(gradFrom), gradTo = rc(gradTo),
        accentSoft = rc(accentSoft), accentLine = rc(accentLine), accentWash = rc(accentWash),
        tileOnFrom = rc(tileOnFrom), tileOnTo = rc(tileOnTo), tileOnLine = rc(tileOnLine),
        surfOnFrom = rc(surfOnFrom), surfOnTo = rc(surfOnTo),
        glow1 = rc(glow1), glow2 = rc(glow2),
        domainTints = domainTints + (IDENTITY to rc(domainTints.getValue(IDENTITY))),
    )
}

/** AC8.5 — bốn cặp chữ/nền hay hỏng nhất đi qua [ContrastGuard]; chạy SAU mọi phép đổi màu để đo đúng nền cuối. */
private fun KachiPalette.guarded(): KachiPalette {
    val bgI = parse(bg); val inkI = parse(ink); val tileI = parse(tile)
    // (1) chữ trên nút nhấn ĐẶC — đo cả hai đầu gradient (AC3.3), đổi mực trước, đẩy nền về phía nền màn sau.
    val onSolid = ContrastGuard.fit(
        parse(onAccent), intArrayOf(parse(gradFrom), parse(gradTo)), altInks = listOf(bgI, inkI), pushToward = bgI,
    )
    // (2) chữ trên ô điều khiển BẬT — vai bán trong suốt trên `tile`: đổi mực trước, chỉnh alpha sau.
    val onTile = fitAlphaPair(parse(inkOnAccent), parse(tileOnFrom), parse(tileOnTo), tileI, listOf(inkI, bgI))
    // (3) chữ trên thẻ BẬT (`surfOn*` trên nền màn) — mực là `ink` theo hợp đồng KDoc [KachiPalette.surfOnFrom], nên
    //     chỉ chỉnh alpha; không đổi mực.
    val onSurf = fitAlphaPair(inkI, parse(surfOnFrom), parse(surfOnTo), bgI, emptyList())
    // (4) mực nhấn trên MỌI nền thường mà `ThemePaletteContractTest.textOn` đòi nó phải đọc được (nền không phải màu
    //     người dùng chọn nên KHÔNG đẩy nền; mực nhấn nhường). [ĐO] bản đầu chỉ đo 4 nền ⇒ bảng SÁNG tụt 4.12–4.47
    //     ở `slotTo`/`fieldSunken` — đúng hai nền mà chính bảng gốc đã ghi là "chạm sàn".
    val grounds = listOf(bg, card, card2, panel, field, cell, tile, slot, slotTo, chipOff, surfFrom, surfTo, fieldSunken)
        .map { ColorMath.over(parse(it), bgI) }.toIntArray()
    val accentInkFit = ContrastGuard.fitInk(parse(accentInk), grounds, toward = inkI)
    return copy(
        accentInk = hex(accentInkFit),
        gradFrom = hex(onSolid.grounds[0]), gradTo = hex(onSolid.grounds[1]), onAccent = hex(onSolid.ink),
        inkOnAccent = hex(onTile.ink), tileOnFrom = hex(onTile.roleA), tileOnTo = hex(onTile.roleB),
        surfOnFrom = hex(onSurf.roleA), surfOnTo = hex(onSurf.roleB),
    )
}

/** Kết quả [fitAlphaPair]: hai vai (đã chỉnh alpha) + **một** mực chung cho cả hai đầu. */
internal data class AlphaPairFit(val roleA: Int, val roleB: Int, val ink: Int, val worst: Double)

/**
 * Hai đầu của một chuyển sắc **phải dùng cùng một mực** (chữ nằm trên cả hai). Thử từng ứng viên mực theo thứ
 * tự ưu tiên; với mỗi ứng viên chỉnh alpha từng đầu (mực cố định); ứng viên đầu tiên đưa **cả hai** đầu qua sàn
 * thắng. Không ứng viên nào đủ ⇒ trả cặp tốt nhất — bảng đo sẽ chấm ❌, không im lặng.
 */
internal fun fitAlphaPair(ink: Int, roleA: Int, roleB: Int, under: Int, altInks: List<Int>): AlphaPairFit {
    var best: AlphaPairFit? = null
    for (candidate in listOf(ink) + altInks.filter { it != ink }) {
        val a = ContrastGuard.fitAlpha(candidate, roleA, under)
        val b = ContrastGuard.fitAlpha(candidate, roleB, under)
        val fit = AlphaPairFit(a.role, b.role, candidate, minOf(a.worst, b.worst))
        if (a.ok && b.ok) return fit
        if (best == null || fit.worst > best.worst) best = fit
    }
    return best!!
}

/** AC8.2 — dịch nhẹ mọi bề mặt về phía ấm/lạnh. [KachiPaletteSeeds.TONE_MIX] đủ để thấy, không đủ để chạm sàn mực. */
private fun KachiPalette.toned(tone: CardTone): KachiPalette {
    val hue = parse(if (tone == CardTone.WARM) KachiPaletteSeeds.TONE_WARM else KachiPaletteSeeds.TONE_COOL)
    fun t(role: String): String = hex(ColorMath.mix(parse(role), hue, KachiPaletteSeeds.TONE_MIX))
    return copy(
        surfFrom = t(surfFrom), surfTo = t(surfTo),
        surfFromOverArt = t(surfFromOverArt), surfToOverArt = t(surfToOverArt),
        slot = t(slot), slotTo = t(slotTo),
        tile = t(tile), cell = t(cell), card = t(card), card2 = t(card2), fieldSunken = t(fieldSunken),
    )
}

private const val IDENTITY = "IDENTITY"
