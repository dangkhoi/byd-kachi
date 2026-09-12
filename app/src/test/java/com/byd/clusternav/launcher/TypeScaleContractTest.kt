package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BÀI CANH THANG CỠ CHỮ (type scale) ══════════════════════════════════════════════════════════════════════
 *
 * Quét **mã nguồn** các bề mặt Settings đã áp design system và **đỏ** khi có `setTextSize(…COMPLEX_UNIT_SP, <số>)`
 * viết tại chỗ thay vì đi qua [KachiType]. Spec `docs/specs/kachi-design-system.html` (R1/R5).
 *
 * ## ⚠⚠ Bài này quét MÃ NGUỒN ⇒ phải ở đúng module `:app` (nơi sở hữu mã nó quét)
 * Cùng họ lỗi đã bắt hai lần: bài đặt ở `:core` mà quét mã `:app` ⇒ `:core:test` báo UP-TO-DATE ngay ca nó sinh
 * ra để bắt (S1); và bài đúng module mà gradle không biết đầu vào cũng UP-TO-DATE (G1). `app/build.gradle.kts` đã
 * khai `inputs.dir("src/main/java")` cho tác vụ test nên đổi nội dung tệp là test chạy lại — cùng cơ chế
 * [SpacingScaleContractTest].
 *
 * ## Phạm vi — vì sao chỉ Settings
 * Design system đang áp **Settings trước** (spec §R7). Ngăn kéo / thanh nút / widget còn dựng cỡ chữ tay ở 14 tệp
 * là **pha sau** (OQ1) — đưa chúng vào bài canh bây giờ thì đỏ hàng loạt cho việc chưa tới lượt. Khi lan tới đâu,
 * thêm tệp vào [SURFACES] tới đó.
 *
 * ## KHÔNG thuộc phạm vi
 * Ô vẽ Canvas ([TyreBoardView]/[SideBoardView]) tính cỡ chữ theo **tỉ lệ cạnh ô** (`m * 0.xxx`) — đó là kích
 * thước hình học bất biến với cỡ ô, không phải một bậc chữ giao diện; chúng không dùng `setTextSize(sp)` số tay.
 */
class TypeScaleContractTest {

    /** Các bề mặt Settings đã áp KachiType. `SettingsRows` là NGUỒN component (dùng `KachiType.apply` nội bộ). */
    private val SURFACES = listOf(
        "SettingsSections.kt", "SettingsSectionsHome.kt", "SettingsSceneSection.kt",
        "SettingsPanel.kt", "TopStripPicker.kt", "SettingsRows.kt",
    )

    /**
     * BA cách đặt cỡ chữ bằng số TAY — phải chặn **cả ba**, không chỉ cách hay gặp nhất.
     *
     * ⚠⚠ [SOÁT ĐỘC LẬP 2026-09-12] Bản đầu của bài này chỉ có [rawTwoArg]. [ĐO] thử phá: chèn
     * `textSize = 99f` **và** `setTextSize(88f)` vào `SettingsSections.kt` ⇒ **BUILD SUCCESSFUL, 0 đỏ** — tức bài
     * canh bỏ qua đúng hai trong ba cách viết ra cùng một lỗi. `TextView.setTextSize(Float)` một-tham-số dùng
     * **COMPLEX_UNIT_SP** theo tài liệu Android, và `textSize = …` của Kotlin biên dịch về đúng hàm đó ⇒ cả hai là
     * cỡ chữ SP viết tay, chỉ khác chính tả.
     *
     * Đây **cùng một họ lỗi** với `px()` lách `SpacingScaleContractTest` (bài đó chỉ soi `dp(`/`dpi(` nên một hàm
     * đổi dp mang tên khác đi qua sạch). Luật đã rút ra khi đó: chặn **NGUYÊN NHÂN** (đặt cỡ chữ bằng số) chứ không
     * chặn **HIỆN TƯỢNG** (một chính tả cụ thể).
     */
    private val rawTwoArg = Regex("""setTextSize\s*\(\s*[\w.]*COMPLEX_UNIT_\w+\s*,\s*\d""")
    private val rawOneArg = Regex("""setTextSize\s*\(\s*\d""")
    private val rawProperty = Regex("""(?<![\w])textSize\s*=\s*\d""")
    private val handWritten = listOf(rawTwoArg, rawOneArg, rawProperty)

    /** Bỏ chú thích khối + dòng để không bắt con số nằm trong câu giải thích (cửa DUY NHẤT: [SourceRoots.codeOf]). */
    private fun code(name: String): String =
        SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    /**
     * Số dòng THẬT trong tệp gốc. [SourceRoots.codeOf] gộp chú thích khối nhiều dòng thành một khoảng trắng nên chỉ
     * số dòng của bản đã-bỏ-chú-thích lệch với tệp thật — báo sai chỗ thì người đọc đi tìm nhầm dòng.
     */
    private fun lineOf(name: String, code: String): Int {
        val raw = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/$name").lines()
        return raw.indexOfFirst { it.trim() == code }.let { if (it < 0) 0 else it + 1 }
    }

    @Test
    fun `Settings khong dat co chu bang so tay - phai qua KachiType`() {
        val offenders = mutableListOf<String>()
        SURFACES.forEach { f ->
            val src = code(f)
            src.lines().forEach { l ->
                if (handWritten.any { it.containsMatchIn(l) }) offenders += "$f:${lineOf(f, l.trim())}:${l.trim()}"
            }
            // Lời gọi VẮT NHIỀU DÒNG (`setTextSize(\n  TypedValue.COMPLEX_UNIT_SP,\n  14f)`) không hiện trên một
            // dòng nào ⇒ quét thêm cả tệp một lượt (`\s` khớp cả newline). Không có bước này thì chỉ cần bấm Enter
            // là lách được bài canh.
            if (handWritten.any { it.containsMatchIn(src) } && offenders.none { it.startsWith("$f:") }) {
                offenders += "$f: (lời gọi vắt nhiều dòng)"
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Cỡ chữ trong Settings phải đi qua KachiType (thang cỡ chữ), KHÔNG đặt bằng số tay " +
                "(cả `setTextSize(UNIT, n)`, `setTextSize(n)` lẫn `textSize = n`):\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * Type scale phải giữ **ĐÚNG** 5 bậc — thêm bậc tuỳ tiện là quay lại "18 cỡ chữ" mà nó sinh ra để chống.
     *
     * ⚠⚠ [SOÁT ĐỘC LẬP 2026-09-12] Bản đầu đếm `const val (DISPLAY|TITLE|SECTION|BODY|CAPTION)` rồi đòi `== 5` —
     * tức nó chỉ kiểm *"5 bậc đã biết còn đủ"*, KHÔNG kiểm *"không có bậc thứ sáu"*. [ĐO] thử phá: thêm
     * `const val MICRO = 9.5f` ⇒ **BUILD SUCCESSFUL, 0 đỏ**, đúng ca mà tên bài và KDoc của nó hứa sẽ bắt. Nay so
     * **TẬP** tên bậc đọc được với tập mong đợi ⇒ thêm, xoá hay đổi tên một bậc đều đỏ.
     */
    @Test
    fun `type scale co dung 5 bac`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiType.kt")
        val found = Regex("""const val (\w+)\s*=\s*[\d.]+f""").findAll(src).map { it.groupValues[1] }.toSet()
        val want = setOf("DISPLAY", "TITLE", "SECTION", "BODY", "CAPTION")
        assertTrue(
            found == want,
            "KachiType phải có ĐÚNG 5 bậc $want — thấy $found (thừa ${found - want}, thiếu ${want - found})",
        )
    }
}
