package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [SOÁT 2026-09-16 · P3] NHÃN APP TRÙNG — chọn CÓ LUẬT, và luật ấy phải kiểm được off-car ═════════════════
 *
 * Bệnh: `VoiceWiring.appsByLabel` kết thúc bằng `.toMap()`, tức hai app cùng nhãn gộp im lặng về gói mà
 * `PackageManager` trả về **sau cùng** — người lái nói một cái tên, **app khác** mở lên, không một dòng log.
 * Bài này khoá ba thứ mà mắt thường không thấy: gói nào thắng, thắng **ổn định** qua mọi thứ tự đầu vào, và
 * nhãn nhập nhằng có được **nêu ra** để tầng Android ghi log hay không.
 */
class VoiceAppLabelPickTest {

    private fun e(label: String, pkg: String, system: Boolean = false) =
        VoiceAppLabelPick.Entry(label, pkg, system)

    @Test
    fun `nhan trung KHONG duoc gop im lang — gia tri sau cung khong duoc thang`() {
        val picked = VoiceAppLabelPick.of(
            listOf(e("Cài đặt", "com.byd.settings", system = true), e("Cài đặt", "com.acme.settings")),
        )
        val map = picked.labels.toMap()
        assertEquals("com.acme.settings", map["Cài đặt"], "gói KHÔNG phải hệ thống thắng — xem luật (1)")
        assertEquals(
            listOf("com.acme.settings", "com.byd.settings"),
            picked.ambiguous["Cài đặt"],
            "phải NÊU RA nhãn nhập nhằng, gói thắng đứng đầu, để tầng Android ghi log được",
        )
    }

    @Test
    fun `dao thu tu dau vao KHONG duoc doi ket qua — PackageManager khong hua thu tu nao`() {
        val a = e("Nhạc", "com.z.music")
        val b = e("Nhạc", "com.a.music")
        val xuoi = VoiceAppLabelPick.of(listOf(a, b)).labels.toMap()
        val nguoc = VoiceAppLabelPick.of(listOf(b, a)).labels.toMap()
        assertEquals(nguoc, xuoi, "cùng danh sách, khác thứ tự ⇒ PHẢI cùng kết quả")
        assertEquals("com.a.music", xuoi["Nhạc"], "hoà (cùng loại) ⇒ tên gói nhỏ nhất theo chữ cái, để kết quả ổn định")
    }

    @Test
    fun `hai loi vao cua CUNG mot goi khong phai nhap nhang — khong sinh canh bao vo ich`() {
        val picked = VoiceAppLabelPick.of(listOf(e("Bản đồ", "com.map"), e("Bản đồ", "com.map")))
        assertEquals("com.map", picked.labels.toMap()["Bản đồ"])
        assertTrue(picked.ambiguous.isEmpty(), "chỉ có MỘT gói để mở ⇒ không có gì nhập nhằng")
    }

    @Test
    fun `ca thuong khong doi gi — moi nhan giu nguyen goi va giu nguyen thu tu`() {
        val entries = listOf(e("Zalo", "com.zing.zalo"), e("ChatGPT", "com.openai.chatgpt"), e("Nhạc", "com.music"))
        val picked = VoiceAppLabelPick.of(entries)
        assertEquals(entries.map { it.label to it.pkg }, picked.labels, "không trùng ⇒ giữ nguyên cả thứ tự")
        assertTrue(picked.ambiguous.isEmpty())
    }

    @Test
    fun `hai goi he thong cung nhan van chon duoc, va van bao nhap nhang`() {
        val picked = VoiceAppLabelPick.of(
            listOf(e("Đài", "com.byd.radio2", system = true), e("Đài", "com.byd.radio1", system = true)),
        )
        assertEquals("com.byd.radio1", picked.labels.toMap()["Đài"])
        assertEquals(listOf("com.byd.radio1", "com.byd.radio2"), picked.ambiguous["Đài"])
    }
}
