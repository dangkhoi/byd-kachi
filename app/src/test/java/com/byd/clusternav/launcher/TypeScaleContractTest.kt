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

    /** `setTextSize(…, <chữ số>)` — cỡ chữ số TAY. `setTextSize(…, KachiType.BODY)` (chữ) KHÔNG khớp. */
    private val rawSp = Regex("""setTextSize\([^)]*COMPLEX_UNIT_SP\s*,\s*\d""")

    /** Bỏ chú thích khối + dòng để không bắt con số nằm trong câu giải thích. */
    private fun code(name: String): String =
        SourceRoots.text("src/main/java/com/byd/clusternav/launcher/$name")
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines().joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `Settings khong dat co chu bang so tay - phai qua KachiType`() {
        val offenders = mutableListOf<String>()
        SURFACES.forEach { f ->
            code(f).lines().forEachIndexed { i, l ->
                if (rawSp.containsMatchIn(l)) offenders += "$f:${i + 1}:${l.trim()}"
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Cỡ chữ trong Settings phải đi qua KachiType (thang cỡ chữ), KHÔNG setTextSize số tay:\n" +
                offenders.joinToString("\n"),
        )
    }

    /** Type scale phải giữ đúng 5 bậc — thêm bậc tuỳ tiện là quay lại "18 cỡ chữ" mà nó sinh ra để chống. */
    @Test
    fun `type scale co dung 5 bac`() {
        val src = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiType.kt")
        val levels = Regex("""const val (DISPLAY|TITLE|SECTION|BODY|CAPTION)\s*=""").findAll(src).count()
        assertTrue(levels == 5, "KachiType phải có đúng 5 bậc DISPLAY/TITLE/SECTION/BODY/CAPTION, thấy $levels")
    }
}
