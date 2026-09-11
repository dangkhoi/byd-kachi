package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CANH nguồn duy nhất của bố cục (P9 bước 2).
 *
 * ## Vì sao cần canh
 * [ĐO] trước bước này có **sáu chỗ** tự suy ra số ô / khung pixel từ bố cục sẵn: ba chỗ ở màn chính (dựng ô, đo cỡ,
 * đặt chỗ) và ba chỗ ở bộ sắp cửa sổ app. Thêm bố cục tự vẽ mà bỏ sót một chỗ thì **màn hình vẽ theo bố cục mới nhưng
 * cửa sổ app đặt theo bố cục cũ** ⇒ app nằm lệch khỏi ô. Đây đúng là hình dạng của P-bug2, nên chặn bằng test chứ
 * không bằng lời nhắc.
 *
 * Test này quét mã nguồn: hai dạng suy-ra-trực-tiếp **không được xuất hiện** ở phía app.
 */
class GridSeamGuardTest {

    private fun code(relative: String): String =
        SourceRoots.text(relative)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }

    /** Những tệp phía app được phép nói về bố cục. */
    private val files = listOf(
        "src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt",
        "src/main/java/com/byd/clusternav/launcher/LauncherWindows.kt",
    )

    @Test
    fun `khong ai duoc tu tinh khung pixel tu bo cuc san`() {
        files.forEach { f ->
            val n = Regex("WorkspaceLayout\\.slots\\(").findAll(code(f)).count()
            assertEquals(0, n,
                "$f phải đi qua EffectiveLayout.rects(...) — tự gọi WorkspaceLayout.slots là bỏ qua bố cục tự vẽ")
        }
    }

    @Test
    fun `khong ai duoc tu doc so o tu bo cuc san`() {
        files.forEach { f ->
            val n = Regex("preset\\.slotCount").findAll(code(f)).count()
            assertEquals(0, n,
                "$f phải đi qua EffectiveLayout.slotCount(...) — đọc preset.slotCount là bỏ qua bố cục tự vẽ")
        }
    }

    @Test
    fun `ca hai phia deu THUC SU dung nguon duy nhat`() {
        // Chặn cách "đạt test" bằng việc xoá lệnh gọi đi mà không thay bằng gì.
        files.forEach { f ->
            assertTrue(code(f).contains("EffectiveLayout."), "$f phải dùng nguồn duy nhất")
        }
    }

    @Test
    fun `bo sap cua so doc bo cuc tu ve qua HAM, khong phai gia tri chup san`() {
        // Nhận giá trị chụp sẵn thì đổi bố cục xong app bị đặt theo bố cục CŨ.
        val src = code("src/main/java/com/byd/clusternav/launcher/LauncherWindows.kt")
        assertTrue(Regex("custom:\\s*\\(\\)\\s*->\\s*GridLayout\\?").containsMatchIn(src),
            "phải là hàm () -> GridLayout? để luôn đọc giá trị mới nhất")
    }

    @Test
    fun `doi bo cuc tu ve phai SAP LAI cua so app`() {
        // Thiếu bước này thì ô vẽ đúng chỗ mới nhưng cửa sổ app vẫn ở khung cũ.
        val act = code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt")
        val fn = act.substringAfter("private fun applyCustomLayout(").substringBefore("\n    }")
        assertTrue(fn.contains("setCustomLayout("), "phải áp cho màn hình")
        assertTrue(fn.contains("setGridLayout("), "phải lưu bền")
        assertTrue(fn.contains("reflow()"), "phải sắp lại cửa sổ app theo khung mới")
    }

    @Test
    fun `so o giu nguyen thi CHI dat lai cho, khong dung lai o`() {
        // Dựng lại ô là nhả/gắn lại bộ chiếu app trong ô (C5) — chỉ đổi hình dạng thì không cần.
        val src = code("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt")
        val fn = src.substringAfter("fun setCustomLayout(").substringBefore("\n    }")
        assertTrue(fn.contains("if (before != after) rebuild()"), "đổi số ô mới được dựng lại")
        assertTrue(fn.contains("requestLayout()"), "số ô giữ nguyên thì chỉ đặt lại chỗ")
    }
}
