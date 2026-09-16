package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ H3(b) · ĐỌC TÊN APP THEO ÂM VIỆT — khoá cả hai chiều ════════════════════════════════════════════════════
 *
 * Bài này khoá đúng hai lời hứa của [VoiceAppPhonetics], và lời hứa thứ hai mới là lời hứa khó:
 *  1. **có** cách đọc cho mọi nhãn thương hiệu hay gặp trên xe (kể cả app không ai khai tay, vd *ChatGPT*);
 *  2. **không** bịa cách đọc cho nhãn tiếng Việt (*"Cài đặt"*, *"Ứng dụng của tôi"*) — một bí danh bịa ra là một
 *     cụm lạ nằm trong bản đồ nhãn→gói, và nó cướp câu của người khác **mà im lặng**.
 *
 * Nguồn chữ đối chiếu: `scripts/voice/data/apps.tsv` (người soạn) + §5 của
 * `docs/diagnostics/voice-mishear-2026-09-16.md` (chuỗi mô hình THẬT SỰ in ra).
 */
class VoiceAppPhoneticsTest {

    private fun forms(label: String) = VoiceAppPhonetics.spokenForms(label)

    @Test
    fun `moi nhan thuong hieu deu ra cach doc uu tien cua apps tsv`() {
        // Cột phải là cách đọc mà `apps.tsv` ghi (hoặc §5 của bảng nghe nhầm) — không phải cách bài này tự nghĩ.
        val expected = listOf(
            "ChatGPT" to "chát gi pi ti",
            "Google Maps" to "gu gồ mép",
            "Google Map" to "gu gồ máp",
            "Google" to "gu gồ",
            "Maps" to "mép",
            "YouTube" to "du túp",
            "YouTube Music" to "du túp miu dích",
            "Zalo" to "za lô",
            "Waze" to "quây",
            "CarPlay" to "ca plây",
            "Android Auto" to "an đroi ô tô",
            "Vietmap" to "việt máp",
            "Spotify" to "spô ti phai",
            "Zing MP3" to "zing em pê ba",
            "TikTok" to "tích tốc",
            "Netflix" to "nét phờ lích",
        )
        expected.forEach { (label, want) ->
            assertTrue(want in forms(label), "«$label» phải đọc được là «$want» — thật ra ra: ${forms(label)}")
        }
    }

    @Test
    fun `bien the vung mien deu co mat, khong chi mot cach doc`() {
        // `apps.tsv` ghi cả ba: `za lô` (chung) · `gia lô` (nam) · `da lô` (bắc). Hai cái đầu nằm trong bảng âm;
        // cái thứ ba do [VoiceSynonyms.APP_TARGETS] lo (Zalo không có dòng nào trong bảng đích ⇒ xem báo cáo).
        assertTrue(forms("Zalo").containsAll(listOf("za lô", "gia lô")), forms("Zalo").toString())
        assertTrue("iu túp" in forms("YouTube"), forms("YouTube").toString())
        assertTrue("pô ti phai" in forms("Spotify"), forms("Spotify").toString())
    }

    @Test
    fun `nhan viet KHONG duoc de ra mot cach doc nao`() {
        listOf("Cài đặt", "Ứng dụng của tôi", "Máy tính", "Tin nhắn", "Điện thoại", "Thư viện ảnh")
            .forEach { assertEquals(emptyList<String>(), forms(it), "«$it» không được bịa cách đọc") }
    }

    @Test
    fun `phu MOT NUA thi tra rong — khong bao gio nua Viet nua Anh`() {
        // `vtv` không có trong bảng âm ⇒ cả nhãn bỏ, dù `go` thì có. Nửa vời là thứ không ai đọc như thế.
        assertEquals(emptyList<String>(), forms("VTV Go"))
        assertEquals(emptyList<String>(), forms("Grab"))
        assertEquals(emptyList<String>(), forms(""))
    }

    @Test
    fun `nhan viet thuong luon co mat o cuoi, va khong cach doc nao qua ngan`() {
        assertEquals("chatgpt", forms("ChatGPT").last(), "mô hình hay in ra dạng thường của chính nhãn")
        VoiceAppPhonetics.SYLLABLES.keys.forEach { k ->
            forms(k).forEach { assertTrue(it.length >= 3, "cách đọc «$it» quá ngắn ⇒ khớp bừa") }
        }
    }

    @Test
    fun `tat dinh — hai luot goi ra y het nhau, ke ca thu tu`() {
        listOf("ChatGPT", "Google Maps", "Zalo", "Cài đặt").forEach {
            assertEquals(forms(it), forms(it), "cùng nhãn phải ra cùng danh sách, cùng thứ tự")
        }
    }

    @Test
    fun `bang am la DU LIEU — moi cach doc deu la chu thuong, khong rong`() {
        VoiceAppPhonetics.SYLLABLES.forEach { (k, v) ->
            assertEquals(k.lowercase(), k, "khoá bảng âm phải chữ thường: «$k»")
            assertTrue(v.isNotEmpty(), "âm «$k» không có cách đọc nào")
            v.forEach { assertEquals(it.lowercase(), it, "cách đọc phải chữ thường: «$it»") }
        }
    }
}
