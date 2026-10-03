package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ spec `kachi-i18n-zh-th-ms.html` — chữ GIÁ TRỊ của chip datum MỨC không mổ chuỗi đã dịch ═══════════════════════
 *
 * [ĐO mã 2026-10-03] Nhánh `!hasLevel` của chip từng làm `value.removePrefix(Strings.t("Mức ", "Level ")).trim()`: bóc
 * một MẢNH dịch riêng khỏi câu dựng bằng mẫu `("Mức {0}", "Level {0}")`. Đúng khi bản dịch là TIỀN TỐ (VI/EN/TH/MS),
 * sai khi là HẬU TỐ — zh `"{0}挡"` ⇒ chip in `"2挡"` thay `"2"`; và hai khoá dịch độc lập nhau thì không gì bắt chúng
 * khớp. Nay [TopStripChips.chipValue] lấy thẳng [TelemetryView.level]. Bài này khoá:
 *  1. ở MỌI [Lang], datum mức (> 0) ⇒ chip in đúng CON SỐ, ở cả hai nhánh (có/không thang mức);
 *  2. VI/EN ra y byte phép bóc cũ (R-nf1) — trên mọi datum mức × mọi mã thô, kể cả mã ngoài thang;
 *  3. mức 0 / chưa đọc ⇒ giữ chữ giá trị của tiếng đó (không lộ mảnh dịch, không rơi về tiếng Việt).
 */
class TopStripChipLevelLangTest {

    @AfterEach fun reset() { Strings.current = Lang.VI }

    private val levelDatums = listOf("seat_vent_state", "seat_heat_state", "seat_vent_state_r", "seat_heat_state_r")

    private fun status(raw: Int?) = CarStatus(
        climate = CarStatus.Climate(seatHeatRaw = raw, seatVentRaw = raw, seatHeatRRaw = raw, seatVentRRaw = raw),
    )

    /** Mọi (datum mức, mã thô) mà tầng đọc cho ra một giá trị — mã ngoài thang ⇒ không có view, bỏ qua. */
    private fun views(lang: Lang): List<TelemetryView> = levelDatums.flatMap { id ->
        (listOf<Int?>(null) + (0..6)).mapNotNull { raw -> TelemetryReadout.of(id, status(raw), lang) }
    }

    @Test
    fun `datum muc in dung con so o moi tieng, ca hai nhanh`() {
        var levelled = 0
        Lang.entries.forEach { lang ->
            Strings.current = lang
            views(lang).forEach { v ->
                val value = v.displayWithUnit()
                val noScale = TopStripChips.chipValue(v, value, hasLevel = false, levelIcon = null)
                val scaled = TopStripChips.chipValue(v, value, hasLevel = true, levelIcon = null)
                val lvl = v.level
                if (lvl != null && lvl > 0) {
                    levelled++
                    assertEquals(lvl.toString(), noScale, "$lang ${v.id} «$value»: chip không thang mức phải in CON SỐ")
                    assertEquals(lvl.toString(), scaled, "$lang ${v.id} «$value»: chip thang mức in con số")
                } else {
                    assertEquals(value.trim(), noScale, "$lang ${v.id}: mức 0 / chưa đọc ⇒ giữ chữ giá trị của tiếng ấy")
                }
            }
        }
        assertTrue(levelled >= Lang.entries.size * levelDatums.size, "bộ mồi quá ít ca mức > 0 ($levelled) — bài này đang rỗng?")
    }

    @Test
    fun `VI va EN y byte phep boc tien to cu`() {
        listOf(Lang.VI, Lang.EN).forEach { lang ->
            Strings.current = lang
            val prefix = Strings.t("Mức ", "Level ", lang)   // đúng mảnh mà bản cũ bóc
            views(lang).forEach { v ->
                val value = v.displayWithUnit()
                assertEquals(
                    value.removePrefix(prefix).trim(),
                    TopStripChips.chipValue(v, value, hasLevel = false, levelIcon = null),
                    "$lang ${v.id} «$value»: VI/EN không được đổi một byte (R-nf1)",
                )
            }
        }
    }

    /** Chip THẬT (qua [TopStripChips.render]) ở mọi tiếng: mức ngoài bảng hình (raw 4 = mức 3) ⇒ nhãn · CON SỐ trần. */
    @Test
    fun `chip that o moi tieng chi in con so muc`() {
        Lang.entries.forEach { lang ->
            Strings.current = lang
            val chip = TopStripChips.render(TopStripConfig(listOf("seat_heat_state"), showLabels = false), status(4)).single()
            assertEquals("3", chip.text, "$lang: chip tắt nhãn ⇒ chỉ con số mức, không '挡'/'Level'/'ระดับ'/'Tahap'")
        }
    }
}
