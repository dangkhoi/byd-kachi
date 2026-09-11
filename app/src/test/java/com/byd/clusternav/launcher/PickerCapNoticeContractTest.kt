package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KIỂM TOÁN UX mục 5 — BẢNG CHỌN KHÔNG ĐƯỢC CHẶN IM LẶNG ══════════════════════════════════════════════════
 *
 * ## Bệnh nó chữa — [ĐO] máy ảo 2026-09-12
 * Ngăn kéo chọn nội dung ô có trần **8 mục**. Đang chọn 8 rồi bấm thêm *Lốp* / *Kính* / *Khí hậu*: vẫn 8 mục,
 * **không một lời nào** — không thông báo, không đổi màu ô, không câu nhắc. Bỏ một mục xuống 7 thì lại bấm được. Tức
 * cú bấm của người dùng **biến mất** và không có gì giải thích.
 *
 * Đây đúng họ lỗi mà dự án đã trả giá ở `DockConfig.setEnabled` (mã không phải nút ⇒ `return this`, bỏ qua im lặng —
 * RW0 phải nới nó ra mới đặt được ô đọc lên thanh nút). Bài này canh **nguyên nhân**: nhánh *"quá trần thì thôi"*
 * không được tồn tại mà không có đường nói cho người dùng.
 *
 * ## Vì sao bài nằm ở `:app`
 * Nó quét **mã nguồn của `:app`**. Bài quét mã module X mà đặt ở module Y thì Gradle không coi tệp của X là đầu vào
 * của `Y:test` ⇒ báo `UP-TO-DATE` và **không bao giờ chạy lại** — cái bẫy đã cho một dấu xanh sai ở S1.
 */
class PickerCapNoticeContractTest {

    private val drawer by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/AppDrawer.kt") }

    /**
     * Nhánh *"đầy trần thì bỏ qua"* phải **nói ra**, và phải nằm ở ĐÚNG MỘT chỗ.
     *
     * Mẫu bị cấm là chính mẫu cũ: `else if (selected.size < MAX) selected.add(...)` — một `if` không có `else`, tức
     * mọi cú bấm quá trần rơi vào hư không.
     */
    @Test
    fun `tran o khong duoc chan im lang`() {
        assertFalse(
            Regex("""else if \(selected\.size < MAX\)""").containsMatchIn(drawer),
            "nhánh 'quá trần thì thôi' không có else = bỏ qua IM LẶNG. Đi qua toggleSelection() để có câu nói.",
        )
        val fn = SourceRoots.body(drawer, "private fun toggleSelection(")
        assertTrue(fn.contains("Toast"), "quá trần phải NÓI ra cho người vừa bấm")
        assertTrue(fn.contains("CAP_NOTE"), "và nói bằng đúng một câu dùng chung (không viết hai bản chữ)")
        assertTrue(fn.contains("return"), "và KHÔNG âm thầm đi tiếp như thể đã thêm")
    }

    /** Câu nói phải nêu **cả trần lẫn đường đi tiếp** — "đã đủ 8" một mình không cho người dùng biết làm gì. */
    @Test
    fun `cau nhac tran neu ca so va cach di tiep`() {
        val note = Regex("""const val CAP_NOTE = "([^"]*)"""").find(drawer)?.groupValues?.get(1)
        assertTrue(note != null) { "phải khai CAP_NOTE ở một chỗ" }
        assertTrue(note!!.contains("\$MAX"), "số trần phải lấy từ hằng MAX, không gõ lại (gõ lại là hai bản sao)")
        assertTrue(note.contains("bỏ"), "phải nói cách đi tiếp: bỏ một mục ra")
    }

    /**
     * Cả HAI loại ô (widget dựng tay + mục khả năng) phải đi qua **cùng** đường chọn.
     *
     * Hai bản sao của một luật là cách chắc chắn để một bản được sửa và bản kia không — lỗi cũ chính là hai chỗ viết
     * cùng một nhánh trần.
     */
    @Test
    fun `hai loai o dung chung mot duong chon`() {
        assertEquals(
            2, Regex("""toggleSelection\(""").findAll(SourceRoots.codeOf(
                "src/main/java/com/byd/clusternav/launcher/AppDrawer.kt",
            )).count() - 1,
            "đúng hai chỗ GỌI toggleSelection (ô widget + ô khả năng), ngoài chính chỗ khai",
        )
    }

    /**
     * Trạng thái *"không còn chọn được"* phải nhìn ra được **trước khi bấm**.
     *
     * Toast là lớp thứ hai (cho người đã bấm); lớp thứ nhất là ô mờ đi. Không có lớp thứ nhất thì người dùng vẫn
     * phải bấm-rồi-đọc mới biết, tức vẫn là "thử xem có được không".
     */
    @Test
    fun `o het cho phai mo di truoc khi bam`() {
        val fn = SourceRoots.body(drawer, "private fun applyTileState(")
        assertTrue(fn.contains("alpha"), "ô không còn chọn được phải mờ đi")
        assertTrue(fn.contains("selected.size < MAX"), "và điều kiện mờ phải là chính cái trần")
    }

    /**
     * ⚠⚠ Nút áp cấu hình phải nằm **NGOÀI** vùng cuộn (mục 5a).
     *
     * [ĐO] trước bản vá nó nằm trong thân cuộn: cuộn xuống là mất nút (điểm sáng vùng nút 7242 → 83 → 0). Bài này
     * canh **thứ tự dựng**: nút được thêm vào `panel` SAU `ScrollView`, nên nó là một hàng riêng ở đáy bảng.
     */
    @Test
    fun `nut ap cau hinh ghim ngoai vung cuon`() {
        val scrollAt = drawer.indexOf("ScrollView(context)")
        val barAt = drawer.indexOf("panel.addView(placeBar()")
        assertTrue(scrollAt > 0, "bảng phải có vùng cuộn")
        assertTrue(barAt > scrollAt, "thanh nút phải được thêm SAU vùng cuộn ⇒ nằm ngoài nó, ghim ở đáy")
        val bar = SourceRoots.body(drawer, "private fun placeBar()")
        assertTrue(bar.contains("onPickWidgets("), "vẫn là MỘT đường ghi cấu hình như trước")
        // Và thân cuộn KHÔNG được chứa nút nữa.
        assertFalse(
            drawer.substring(0, scrollAt).contains("body.addView(head)"),
            "nút không được nằm trong thân cuộn nữa",
        )
    }

    /** Mép vùng cuộn phải MỜ dần, không cắt ngang chữ (mục 5c). */
    @Test
    fun `mep vung cuon mo dan khong cat chu`() {
        assertTrue(drawer.contains("isVerticalFadingEdgeEnabled = true"), "mép cuộn phải mờ dần")
        assertTrue(drawer.contains("setFadingEdgeLength("), "và phải khai độ dài dải mờ")
        assertTrue(drawer.contains("clipToPadding = false"), "đệm trên/dưới không được bị cắt theo vùng cuộn")
    }
}
