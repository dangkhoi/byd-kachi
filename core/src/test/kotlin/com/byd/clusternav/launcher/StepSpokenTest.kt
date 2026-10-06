package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 `A11Y-STEPPER-NAME` — [ĐO máy ảo QA4 04/10] TalkBack đọc nút bước gió/nhiệt chỉ là `4` / `−` / `+`. Bài khoá chữ
 * trợ năng "tên + giá trị" của ba view ô bước ở cả 5 tiếng (tiền tố dùng lại cặp đã dịch "Tăng "/"Giảm " — không dòng
 * dịch mới), và rằng MỌI nút STEP của bộ đăng ký có tên để nói.
 */
class StepSpokenTest {

    @Test
    fun `ba cau tro nang - ten truoc, gia tri, dung tien to da dich`() {
        assertEquals(StepSpoken.Words("Gió 4", "Giảm Gió (4)", "Tăng Gió (4)"), StepSpoken.of("Gió", "4", Lang.VI))
        assertEquals(StepSpoken.Words("Fan 4", "Decrease Fan (4)", "Increase Fan (4)"), StepSpoken.of("Fan", "4", Lang.EN))
        assertEquals("调低风量 (22°)", StepSpoken.of("风量", "22°", Lang.ZH).down)
        assertEquals("เพิ่มพัดลม (4)", StepSpoken.of("พัดลม", "4", Lang.TH).up)
        assertEquals("Turunkan Kipas (AUTO)", StepSpoken.of("Kipas", "AUTO", Lang.MS).down)
    }

    @Test
    fun `moi nut STEP cua bo dang ky co ten de noi o moi tieng`() {
        val steps = ControlRegistry.ALL.filter { it.kind == ControlKind.STEP }
        assertTrue(steps.isNotEmpty())
        Lang.values().forEach { lang ->
            steps.forEach { def ->
                val w = StepSpoken.of(def.labelIn(lang), ControlVisuals.stepText(def, def.min), lang)
                assertTrue(def.labelIn(lang).isNotBlank() && w.up.contains(def.labelIn(lang)), "${def.id}/$lang: ${w.up}")
                assertTrue(w.down != w.up, "${def.id}/$lang: hai nút phải nói khác nhau")
            }
        }
    }
}
