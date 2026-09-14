package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BÀI CANH LỀ STACK (design system §2) ════════════════════════════════════════════════════════════════════
 *
 * Luật: **mỗi component của [SettingsRows] tự mang lề ngoài** qua `layoutParams`; chỗ gọi chỉ `addView(component)`
 * và KHÔNG tự chèn khoảng cách. Đó là thứ chữa "các hàng/thẻ DÍNH vào nhau" — trước design system, `checkRow` là thẻ
 * có nền mà không có lề ngoài (hai thẻ sát 0px) còn `permissionRow` lại có lề, nên nhịp dọc không đều.
 *
 * ## ⚠⚠ Vì sao bài này tồn tại — một lời hứa từng KHÔNG có ai canh
 * KDoc của `SettingsRows.stackLp` viết *"Chỗ gọi KHÔNG được truyền lp riêng khi `addView` … đó là điều test khoá
 * canh"*, nhưng [ĐO] lượt soát 2026-09-12: **không có bài nào** quét điều đó, và ngay lúc đó `SettingsSceneSection`
 * (tệp nay đã xoá cùng khái niệm "cảnh", S4 · R1)
 * **đang vi phạm** (truyền `wrapLp()` cho `rows.button(…)` ⇒ nút nhận lề dưới `Sp.M` thay vì `Sp.S` như hai nút cùng
 * họ). Đúng họ "bài canh là TRANG TRÍ / lời hứa đúng nhờ may mắn dữ liệu" mà dự án đã trả giá nhiều lần — nên luật
 * này nay được khoá bằng MÁY, ở **hai chiều** (component phải tự đặt lề · chỗ gọi không được ghi đè).
 *
 * ## Giới hạn đã biết — nói ra, không giả vờ phủ hết
 * Chiều "chỗ gọi" chỉ bắt dạng TRỰC TIẾP `addView(rows.xxx(…), lp)`. Dạng gián tiếp (`val v = rows.note(…)` rồi
 * `addView(v, lp)`) cần theo dõi luồng dữ liệu nên bài này không bắt; [ĐO] hiện 100% chỗ gọi là dạng trực tiếp
 * (`SettingsSections` · `SettingsSectionsHome`), còn dạng gián tiếp hiện có đều `addView(v)`
 * một tham số.
 */
class SettingsStackMarginContractTest {

    private val surfaces = listOf(
        "SettingsSections.kt", "SettingsSectionsHome.kt",
        // T4 · IA v2 — năm section mới. Chúng gọi `rows.*` dày đặc nên đây đúng là chỗ dễ tái phạm "tự chèn
        // khoảng cách" nhất; để ngoài phạm vi thì luật lề STACK chỉ còn đúng ở ba tệp cũ.
        "SettingsSectionsBars.kt", "SettingsSectionsNav.kt", "SettingsSectionsCast.kt",
        "SettingsSectionsKeys.kt", "SettingsSectionsCar.kt",
    )

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    // ── Chiều 1 · component tự mang lề ───────────────────────────────────────────────────────────

    /**
     * Mọi hàm dựng CÔNG KHAI của [SettingsRows] phải tự đặt `layoutParams`. Thiếu một cái là hàng đó dính hàng bên
     * cạnh — và dính là lỗi IM LẶNG (không ngoại lệ, không log, chỉ trông xấu), nên phải bắt bằng bài canh.
     */
    @Test
    fun `moi component cua SettingsRows tu dat layoutParams`() {
        val src = code("SettingsRows.kt")
        val builders = Regex("""\n    fun (\w+)\(""").findAll(src).map { it.groupValues[1] }.toList()
        assertTrue(builders.size >= 7, "phải thấy đủ bộ dựng công khai, thấy ${builders.size}: $builders")
        val missing = builders.filter { name ->
            !SourceRoots.body(src, "fun $name(").contains("layoutParams")
        }
        assertTrue(
            missing.isEmpty(),
            "Component của Settings phải tự mang lề ngoài (đặt `layoutParams`, thường qua `stackLp()`); " +
                "thiếu ở: $missing",
        )
    }

    /**
     * Bộ component phải có ĐỦ các hàm dựng mà IA v2 §4.4 đòi — và mỗi hàm đó đi qua phép kiểm lề ở trên.
     *
     * ## Vì sao liệt kê TÊN, không chỉ đếm số
     * Bài `moi component cua SettingsRows tu dat layoutParams` **tự tìm** hàm dựng bằng regex, nên nó canh đúng
     * những gì ĐANG có. Nếu một hàm của §4.4 chưa được viết (hoặc bị xoá đi khi dọn dẹp), bài đó vẫn xanh — nó
     * không có cách nào biết cái gì *đáng lẽ* phải tồn tại. Hai bài bổ nhau: bài này canh **phạm vi**, bài kia
     * canh **chất lượng** của từng hàm trong phạm vi đó.
     *
     * Đây cũng là giao kèo mà các section mới (nav · cast · keys · car của T4) gọi tới — đổi tên một hàm ở đây là
     * làm gãy chúng, và bài này bắt được ngay ở `:app:testDebugUnitTest` thay vì lúc biên dịch section.
     */
    @Test
    fun `bo component co du cac ham dung ma IA v2 doi`() {
        val src = code("SettingsRows.kt")
        val builders = Regex("""\n    fun (\w+)\(""").findAll(src).map { it.groupValues[1] }.toSet()
        val required = setOf(
            // pha 1 (design system)
            "sectionLabel", "checkRow", "chipRow", "unitRow", "permissionRow", "note", "button",
            // pha 2 (IA v2 §4.4 — T3)
            "subHeader", "statusRow", "stepperRow", "listRow", "embed",
        )
        assertTrue(
            builders.containsAll(required),
            "SettingsRows thiếu hàm dựng mà IA v2 §4.4 đòi: ${required - builders} (đang có: $builders)",
        )
    }

    /**
     * Hai hàm trả **holder** (`SettingsRows.StatusRow` / `SettingsRows.Stepper`) phải cho chỗ gọi cả `view` lẫn
     * đường cập nhật — nếu không thì section lại phải dựng lại cả hàng để đổi một chữ (và tự phát minh một bảng
     * tra `id → view`, thứ mà lớp này cố ý không có).
     *
     * ⚠ KHÔNG dùng `SourceRoots.body("inner class …")`: nó trả **thân khối** `{…}` của lớp, tức phần **danh sách
     * tham số hàm dựng nằm NGOÀI** — mà `val view: View` lại khai đúng ở đó ⇒ bài canh sẽ đỏ oan. Cắt vùng từ
     * tên lớp tới hàm dựng công khai kế tiếp (holder luôn đứng ngay trước hàm dựng của nó).
     */
    @Test
    fun `holder cua statusRow va stepperRow cho cap nhat tai cho`() {
        val src = code("SettingsRows.kt")
        listOf(
            "StatusRow" to "fun update(",
            "Stepper" to "fun setValue(",
        ).forEach { (holder, updater) ->
            assertTrue(src.contains("inner class $holder"), "SettingsRows phải khai `inner class $holder`")
            val region = src.substringAfter("inner class $holder").substringBefore("\n    fun ")
            assertTrue(region.contains("val view: View"), "$holder phải lộ `val view: View` để chỗ gọi addView")
            assertTrue(region.contains(updater), "$holder phải có `$updater…` để cập nhật mà không dựng lại hàng")
        }
    }

    // ── Chiều 2 · chỗ gọi không ghi đè ───────────────────────────────────────────────────────────

    @Test
    fun `cho goi khong truyen lp rieng cho component cua SettingsRows`() {
        val offenders = surfaces.flatMap { f -> overriddenAddViews(code(f)).map { "$f: $it" } }
        assertTrue(
            offenders.isEmpty(),
            "Truyền lp riêng vào `addView(rows.…, lp)` GHI ĐÈ lề mà component tự mang ⇒ nhịp dọc không đều. " +
                "Bỏ tham số lp đi:\n" + offenders.joinToString("\n"),
        )
    }

    /**
     * Tìm `addView(rows.…, <lp>)` — tức lời gọi `addView` có **tham số thứ hai**.
     *
     * Đếm ngoặc thật chứ không dùng regex: `addView(rows.chipRow(a, b, c) { … })` có dấu phẩy ở TRONG ngoặc của
     * `chipRow` và ở TRONG lambda; chỉ dấu phẩy ở **đúng độ sâu 1 của `addView`, ngoài mọi ngoặc nhọn và ngoài chuỗi**
     * mới là tham số thứ hai. Bỏ ba điều kiện đó là bài canh báo sai hàng loạt và sẽ bị tắt đi — thà không có.
     */
    private fun overriddenAddViews(src: String): List<String> {
        val out = mutableListOf<String>()
        val key = "addView(rows."
        var from = 0
        while (true) {
            val at = src.indexOf(key, from)
            if (at < 0) break
            from = at + key.length
            var paren = 0
            var brace = 0
            var inStr = false
            var extraArg = false
            var i = src.indexOf('(', at)
            while (i < src.length) {
                val ch = src[i]
                when {
                    ch == '"' && src.getOrNull(i - 1) != '\\' -> inStr = !inStr
                    inStr -> Unit
                    ch == '(' -> paren++
                    ch == ')' -> { paren--; if (paren == 0) break }
                    ch == '{' -> brace++
                    ch == '}' -> brace--
                    ch == ',' && paren == 1 && brace == 0 -> extraArg = true
                }
                i++
            }
            if (extraArg) out += src.substring(at, minOf(i + 1, src.length)).lines().first().trim()
        }
        return out
    }
}
