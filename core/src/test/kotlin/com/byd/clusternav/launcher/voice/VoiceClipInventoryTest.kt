package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ T3 · R2/R3 — TRA GÓI CLIP KHÔNG CẦN THIẾT BỊ ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-clone.html`. Dựng một gói **giả** (đúng khuôn `index.tsv`/`num.tsv` thật: cột
 * `text\tfile\tms`, tầng lấy từ tiền tố đường dẫn) rồi kiểm ba việc: trúng nguyên câu, ghép ba mảnh, và — quan
 * trọng nhất — **phát hiện được** khi một câu trả lời KHÔNG có clip (registry mọc nhãn mới mà gói thiếu clip).
 */
class VoiceClipInventoryTest {

    // Gói giả — mỗi dòng đúng khuôn thật. ⚠ dòng đơn vị BẮT ĐẦU bằng dấu cách (" °C"): đó là chuỗi thật của gói.
    private val index = listOf(
        "Đã bật Đèn đọc\tfixed/a1.aac\t1000",
        "Nhiệt trong cabin: chưa đọc được\tfixed/a2.aac\t1800",
        "Nhiệt trong cabin:\thead/h1.aac\t900",
        "Nhiệt\thead/h5.aac\t400",                 // đầu câu NGẮN — để kiểm 'dài trước'
        "Đã đặt Nhiệt độ\thead/h2.aac\t900",
        "Số:\thead/h3.aac\t400",
        "Chế độ lái:\thead/h4.aac\t800",
        " °C\tunit/u1.aac\t500",
        ", chưa kiểm trên xe\tunit/u2.aac\t1100",
    ).joinToString("\n")

    private val nums = listOf(
        "3\tnum/3.aac\t400",
        "22\tnum/22.aac\t800",
        "24\tnum/24.aac\t900",
    ).joinToString("\n")

    private val inv = VoiceClipInventory.parse(index, nums)

    private fun files(reply: String): List<String>? =
        (inv.resolve(reply) as? VoiceClipInventory.Resolution.Hit)?.clips?.map { it.file }

    @Test
    fun `parse dem dung tung tang`() {
        assertEquals(2, inv.fixedCount)
        assertEquals(5, inv.headCount)
        assertEquals(2, inv.unitCount)
        assertEquals(3, inv.numCount)
    }

    @Test
    fun `cau tron ven trung mot clip fixed`() {
        assertEquals(listOf("fixed/a1.aac"), files("Đã bật Đèn đọc"))
    }

    @Test
    fun `ghep dau cau + so + don vi`() {
        assertEquals(listOf("head/h1.aac", "num/24.aac", "unit/u1.aac"), files("Nhiệt trong cabin: 24 °C"))
    }

    @Test
    fun `ghep dau cau + so KHONG don vi`() {
        // Ca đọc số không đơn vị (*"Số: 3"*) — hai mảnh, không có clip đơn vị nào để nối.
        assertEquals(listOf("head/h3.aac", "num/3.aac"), files("Số: 3"))
    }

    @Test
    fun `ghep dau cau + so + duoi cau co dau phay`() {
        assertEquals(
            listOf("head/h2.aac", "num/22.aac", "unit/u2.aac"),
            files("Đã đặt Nhiệt độ 22, chưa kiểm trên xe"),
        )
    }

    @Test
    fun `dau cau DAI thang dau cau ngan`() {
        // Có cả "Nhiệt" lẫn "Nhiệt trong cabin:" — phải khớp cái cụ thể nhất, không phải cái ngắn.
        assertEquals("head/h1.aac", files("Nhiệt trong cabin: 24 °C")?.first())
    }

    @Test
    fun `cau tron ven thang duong ghep`() {
        // Câu này BẮT ĐẦU bằng đầu câu "Nhiệt trong cabin:" nhưng là một câu FIXED trọn vẹn ⇒ tra fixed trước.
        assertEquals(listOf("fixed/a2.aac"), files("Nhiệt trong cabin: chưa đọc được"))
    }

    @Test
    fun `so ngoai 0-999 trong goi thi truot`() {
        // 999 không có clip số ⇒ Miss (nhường Piper), KHÔNG ghép nửa vời.
        assertEquals(VoiceClipInventory.Resolution.Miss, inv.resolve("Nhiệt trong cabin: 999 °C"))
    }

    @Test
    fun `gia tri khong phai so thi truot`() {
        // *"Chế độ lái: Thể thao"* — đầu câu khớp nhưng phần sau không phải số ⇒ Miss.
        assertEquals(VoiceClipInventory.Resolution.Miss, inv.resolve("Chế độ lái: Thể thao"))
    }

    @Test
    fun `cau la hoan toan thi truot`() {
        assertEquals(VoiceClipInventory.Resolution.Miss, inv.resolve("Dẫn đường tới Bitexco"))
        assertEquals(VoiceClipInventory.Resolution.Miss, inv.resolve(""))
    }

    @Test
    fun `chuan hoa khoang trang thua van trung`() {
        assertEquals(listOf("head/h1.aac", "num/24.aac", "unit/u1.aac"), files("Nhiệt trong cabin:  24   °C"))
        assertEquals(listOf("fixed/a1.aac"), files("  Đã bật Đèn đọc  "))
    }

    /**
     * ⚠ BÀI CANH R2 — *"registry mọc nhãn mới mà gói thiếu clip → phát hiện được"*.
     *
     * *"Tốc độ:"* KHÔNG có clip `head/` trong gói giả này (giả lập một nhãn thông tin vừa được thêm). Câu đọc số
     * đo của nó phải rơi vào [VoiceClipInventory.missing] — đó chính là tín hiệu để bài canh của gói THẬT đỏ khi
     * gói tụt lại sau registry, thay vì im lặng đọc Piper trên xe.
     */
    @Test
    fun `phat hien cau thieu clip khi registry moc nhan moi`() {
        val replies = listOf(
            "Đã bật Đèn đọc",                       // có
            "Nhiệt trong cabin: 24 °C",             // có (ghép)
            "Tốc độ: 60 km/h",                      // THIẾU: không có head "Tốc độ:" lẫn clip số 60/đơn vị
        )
        assertEquals(listOf("Tốc độ: 60 km/h"), inv.missing(replies))
    }

    @Test
    fun `goi rong thi moi cau deu truot`() {
        val empty = VoiceClipInventory.parse("", "")
        assertEquals(0, empty.fixedCount)
        assertTrue(empty.resolve("Đã bật Đèn đọc") is VoiceClipInventory.Resolution.Miss)
    }

    @Test
    fun `dong hong trong tsv bi bo qua khong nem`() {
        val bad = VoiceClipInventory.parse("chỉ một cột\n\nĐã bật Đèn đọc\tfixed/a1.aac\t1000", "rác")
        assertEquals(1, bad.fixedCount)
        assertEquals(0, bad.numCount) // "rác" một cột ⇒ bỏ
    }
}
