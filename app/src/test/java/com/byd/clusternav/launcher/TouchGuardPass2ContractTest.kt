package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Review 3.1 Pass 2 — hai chốt chạm nhỏ, khoá bằng hình dạng mã (app không có Robolectric — cùng kiểu các bài hợp đồng khác).
 *
 *  1. Bảng chạm trên hình xe ([CarIconBoardView] · [ActionMatrixCompact], TAPFIX): ngón thứ hai chạm xuống ⇒ HUỶ chủ cú chạm.
 *     Không huỷ thì `ACTION_UP` cuối mang toạ độ ngón CÒN LẠI: ngón 1 nhấn chấm A, ngón 2 thả trên A ⇒ bắn lệnh A.
 *  2. Nhấn giữ lặp ([SettingsRepeatTouch]): nhịp lặp đã xếp trên Handler không tự huỷ khi nút rời cửa sổ ⇒ phải tự kiểm
 *     `isAttachedToWindow` — mỗi bước có thể là một lệnh `am task resize` (−/+ khung cụm).
 */
class TouchGuardPass2ContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name.kt")

    @Test
    fun `bang cham hinh xe - ngon thu hai huy chu cu cham`() {
        for (f in listOf("CarIconBoardView", "ActionMatrixCompact")) {
            val src = code(f)
            assertTrue(
                Regex("""MotionEvent\.ACTION_POINTER_DOWN[^\n]*->\s*down\s*=\s*null""").containsMatchIn(src),
                "$f: ACTION_POINTER_DOWN phải đặt down = null",
            )
        }
    }

    @Test
    fun `nhan giu lap dung khi nut roi cua so`() {
        val src = code("SettingsRepeatTouch")
        assertTrue(
            Regex("""if \(!button\.isAttachedToWindow \|\| !button\.isEnabled \|\| !button\.isPressed\) return""").containsMatchIn(src),
            "nhịp lặp phải dừng khi nút rời cửa sổ",
        )
    }
}
