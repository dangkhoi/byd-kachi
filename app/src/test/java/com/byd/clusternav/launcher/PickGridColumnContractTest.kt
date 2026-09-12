package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ SOÁT ĐỘC LẬP 2026-09-12 — MỘT VÙNG CUỘN = MỘT LƯỚI CỘT ═══════════════════════════════════════════════════
 *
 * ## Bệnh đã xảy ra THẬT, và bản vá của nó KHÔNG có bài canh nào
 * Owner báo *"chọn app vào ô lệch loạn"*. Gốc: hai màn chọn ô khả năng, mỗi màn tự chọn số cột, nên ba con số cùng
 * tồn tại cho MỘT quyết định — ngăn kéo để Nhóm **3** cột rồi các phần dưới **4** cột **trong cùng một vùng cuộn**
 * (cuộn xuống là tâm cột nhảy), còn màn Cài đặt để mục lẻ **5**. Commit `375d44f` đã gộp cả ba về 4, nhưng **không
 * thêm phép kiểm nào** ⇒ ai đó đặt lại `cols = 3` "cho dòng phụ rộng" là lỗi mọc lại y nguyên, im lặng.
 *
 * Đây đúng luật mà dự án đã trả giá nhiều lần: *vá xong phải chặn NGUYÊN NHÂN bằng một bài canh*, không thì bản vá
 * chỉ sống được tới lần sửa giao diện kế tiếp (lệ: bài chặn nhãn chứa `%` không bắt được lần thứ hai của cùng họ lỗi).
 *
 * ## Chặn nguyên nhân, không chặn hiện tượng — nên có HAI lớp
 *  1. **Không số trần** trong đối số `cols = …`: số trần là cách con số thứ hai lọt vào.
 *  2. **Tập hợp token phải ĐÚNG** như khai: một số trần bị đổi thành `COLS_NARROW = 3` sẽ qua lớp (1) nhưng vẫn là
 *     lưới cột thứ hai trong cùng vùng cuộn — nên bài đòi biết chính xác mỗi màn dùng những token nào.
 *
 * ## ⚠ Bài này quét mã nguồn `:app` ⇒ nó PHẢI ở `:app`
 * S1 từng đặt hai bài chống-rữa ở `:core` mà quét mã `:app`: `:core:test` báo **UP-TO-DATE** đúng ở ca chúng sinh ra
 * để bắt. `app/build.gradle.kts` đã khai `inputs.dir("src/main/java")` **và** `core/src/main/kotlin`, nên cả phần
 * đọc hằng của `:core` cũng được Gradle theo dõi.
 */
class PickGridColumnContractTest {

    private val drawer by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/AppDrawer.kt") }
    private val home by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSectionsHome.kt") }

    /** Mọi token truyền vào `cols = …` trong [src] (định danh HOẶC số trần). */
    private fun colsArgs(src: String): List<String> =
        Regex("""\bcols\s*=\s*([A-Za-z_][A-Za-z0-9_.]*|\d+)""").findAll(src).map { it.groupValues[1] }.toList()

    // ── Lớp 1: không số trần ─────────────────────────────────────────────────────────────────────

    @Test
    fun `so cot khong duoc la so tran o ca hai man chon`() {
        val offenders = listOf("AppDrawer.kt" to drawer, "SettingsSectionsHome.kt" to home)
            .flatMap { (name, src) -> colsArgs(src).filter { it.all(Char::isDigit) }.map { "$name: cols = $it" } }
        assertEquals(
            emptyList<String>(), offenders,
            "số cột viết tại chỗ ⇒ hai màn chọn sẽ lệch nhau (đúng lỗi 'lệch loạn' owner báo 2026-09-12). " +
                "Dùng CapabilityPicker.COLS cho ô khả năng: $offenders",
        )
    }

    // ── Lớp 2: đúng những token đã khai, không có lưới thứ hai ───────────────────────────────────

    /**
     * Ngăn kéo có **hai** loại lưới, và chỉ hai: ô khả năng/widget ([AppDrawer] `COLS_TILE`) và danh sách **app**
     * (`COLS_APP` — icon nhỏ, khác loại nên cố ý không gộp). Bất kỳ token thứ ba là một lưới cột nữa trong cùng
     * vùng cuộn.
     */
    @Test
    fun `ngan keo chi co dung hai loai luoi`() {
        assertEquals(
            setOf("COLS_TILE", "COLS_APP"), colsArgs(drawer).toSet(),
            "ngăn kéo chỉ được có lưới ô-khả-năng và lưới app; token thứ ba = lưới cột thứ hai trong CÙNG vùng cuộn",
        )
        assertTrue(
            Regex("""const val COLS_TILE = CapabilityPicker\.COLS""").containsMatchIn(drawer),
            "COLS_TILE phải LẤY từ `:core`, không được là một con số thứ hai — màn Cài đặt bày CHÍNH những ô đó",
        )
    }

    @Test
    fun `man Cai dat lay so cot tu cung mot nguon voi ngan keo`() {
        assertEquals(
            setOf("CapabilityPicker.COLS"), colsArgs(home).toSet(),
            "mọi lưới ô khả năng ở màn Cài đặt phải đi qua CapabilityPicker.COLS (trước 2026-09-12 mục lẻ để 5 cột)",
        )
    }

    // ── Nguồn sự thật thật sự tồn tại và ở `:core` ───────────────────────────────────────────────

    /**
     * Đọc **chính hằng** (không quét chữ): nó thuần Kotlin ở `:core` nên có trên classpath test, cùng lối
     * `ThemePaletteContractTest` đọc thẳng [KachiPalette]. Khoá tính chất *"dùng được làm số cột"* chứ KHÔNG khoá
     * con số — khoá con số sẽ chặn cả việc chỉnh hợp lệ về sau.
     */
    @Test
    fun `nguon su that ve so cot nam o core va dung duoc`() {
        assertTrue(CapabilityPicker.COLS >= 2, "số cột phải ≥ 2, không thì lưới không còn là lưới")
        assertTrue(
            CapabilityPicker.COLS <= 6,
            "quá nhiều cột thì ô nhóm không còn chỗ cho DÒNG PHỤ nói nó gồm gì (lý do ban đầu Nhóm để 3 cột)",
        )
    }
}
