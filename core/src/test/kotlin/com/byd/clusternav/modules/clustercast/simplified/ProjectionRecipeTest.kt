package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CLUSTER-THEME-SAFE (2.89) — công thức lệnh theo hồ sơ: Seal giữ đúng chuỗi cũ; lệnh luôn kèm `s16 ""` ([ĐO 05/10] thiếu
 * ⇒ EX_NULL_POINTER); opcode cấm 17 (ghi bền `SetConfig_UINT8(53,3)` + CAN — [ĐO disasm 05/10]) và 41 (ghi bền
 * `theme_index/navi_type` + CAN — [ĐO disasm @0x146ea8]) không bao giờ đi ra. B1a: opcode theme tách khỏi chuỗi chiếu.
 */
class ProjectionRecipeTest {

    @Test
    fun `Seal DL3 giu dung chuoi da chay tren xe - 30 o styleOps, 16 35, 18 0`() {
        val r = ProjectionRecipe.SEAL_DL3
        assertEquals(listOf(16, 35), r.castSeq)
        assertEquals(listOf(18, 0), r.teardownSeq)
        assertEquals(30, r.styleOps[CastStyle.CURVED])
        assertEquals("service call AutoContainer 2 i32 1000 i32 30 s16 \"\"", r.command(30))
        assertEquals(
            ProjectionRecipe.SEAL_DL3,
            ProjectionRecipe.of("AutoContainer", listOf(30, 16, 35), listOf(18, 0), mapOf(CastStyle.CURVED to 30, CastStyle.RECT to 31), null),
        )
    }

    @Test
    fun `boc opcode theme cua chuoi cu - dau tien thanh CURVED, chuoi chieu sach theme`() {
        assertEquals(30 to listOf(16, 35), ProjectionRecipe.peelTheme(listOf(30, 16, 35)))
        assertEquals(31 to listOf(16, 35), ProjectionRecipe.peelTheme(listOf(17, 31, 16, 35)), "17 bị bỏ trước")
        assertEquals(29 to listOf(16, 35), ProjectionRecipe.peelTheme(listOf(16, 29, 35, 30)), "mọi opcode theme rời chuỗi chiếu")
        assertEquals(null to listOf(16, 35), ProjectionRecipe.peelTheme(listOf(16, 35)))
        // Chuỗi cũ dựng tay không khai styleOps: opcode đầu tiên của chuỗi chiếu là CURVED (đúng thứ chuỗi cũ gửi).
        val r = ProjectionRecipe.of("AutoContainer", listOf(31, 16, 35), listOf(18, 0), emptyMap(), null)
        assertEquals(mapOf(CastStyle.CURVED to 31), r.styleOps)
        assertEquals(listOf(16, 35), r.castSeq)
    }

    @Test
    fun `opcode cam 17 va 41 bi loc o moi cho, chuoi rong sau loc thi ve chuoi Seal, service la thi ve AutoContainer`() {
        assertEquals(setOf(17, 41), ProjectionRecipe.FORBIDDEN_OPS)
        val r = ProjectionRecipe.of("Auto;rm -rf", listOf(17, 41), listOf(17, 41, 18, 0), mapOf(CastStyle.CURVED to 41), null)
        assertEquals(listOf(16, 35), r.castSeq)
        assertEquals(listOf(18, 0), r.teardownSeq)
        assertEquals("AutoContainer", r.svcName)
        assertTrue(r.styleOps.isEmpty(), "41 khai làm kiểu cũng bị bỏ")
        assertFalse(listOf(r.castSeq, r.teardownSeq, r.styleOps.values.toList()).flatten().any { it in ProjectionRecipe.FORBIDDEN_OPS })
    }

    @Test
    fun `styleOps chi giu opcode theme da biet - opcode kieu la khong gui nhu theme va cung roi chuoi chieu`() {
        val r = ProjectionRecipe.of("AutoContainer", listOf(42, 16, 35), listOf(18, 0), mapOf(CastStyle.CURVED to 42, CastStyle.RECT to 43), null)
        assertTrue(r.styleOps.isEmpty())
        assertEquals(listOf(16, 35), r.castSeq, "42 khai là kiểu ⇒ không bao giờ gửi ngoài cổng")
        assertTrue(r.isTheme(29) && r.isTheme(30) && r.isTheme(31))
        assertFalse(r.isTheme(16) || r.isTheme(35) || r.isTheme(18) || r.isTheme(0))
    }

    @Test
    fun `offers - RECT chi khi kieu goc la RECT va co opcode ep - con lai an`() {
        assertTrue(ProjectionRecipe.SEAL_DL3.offers(CastStyle.CURVED))
        assertFalse(ProjectionRecipe.SEAL_DL3.offers(CastStyle.RECT), "kiểu gốc chưa biết ⇒ RECT ẩn")
        assertTrue(ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT).offers(CastStyle.RECT))
        assertFalse(ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT, styleOps = mapOf(CastStyle.CURVED to 30)).offers(CastStyle.RECT))
        assertEquals(CastStyle.RECT, ProjectionRecipe.SEAL_DL3.styleOf(31))
        assertNull(ProjectionRecipe.SEAL_DL3.styleOf(16))
    }

    @Test
    fun `themeOnVacantVd MAC DINH tat`() {
        assertFalse(ProjectionRecipe.SEAL_DL3.themeOnVacantVd)
        assertFalse(ProjectionRecipe.of("AutoContainer", listOf(30, 16, 35), listOf(18, 0), emptyMap(), null).themeOnVacantVd)
    }

    @Test
    fun `DiLink5 - service auto_container, chi 16, khong kieu`() {
        val r = ProjectionRecipe.of("auto_container", listOf(16), listOf(18, 0), emptyMap(), null)
        assertEquals("service call auto_container 2 i32 1000 i32 16 s16 \"\"", r.command(16))
        assertEquals(listOf(16), r.castSeq)
        assertFalse(r.offers(CastStyle.CURVED) || r.offers(CastStyle.RECT))
    }
}
