package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ H3/H4 · TÊN APP NÓI THEO ÂM VIỆT · GIỌNG NAM · CHUỖI NGHE NHẦM ══════════════════════════════════════════
 *
 * Mỗi bài ở đây khoá một câu **đã đo thật**, không phải một ca tưởng tượng:
 *  • [ĐO xe 2026-09-16, tester 1.66] *"Mở Google được mà Google Map chưa hiểu"* — owner nói thẳng;
 *  • [ĐO host] `docs/diagnostics/voice-mishear-2026-09-16.md` §5 — chuỗi mô hình THẬT SỰ in ra
 *    (*"mở gu gồ máp"* ×10, *"mở du túp"* ×7, *"lọc bụi"* → *"các bụi"* ×3);
 *  • [ĐO host] cùng tài liệu §4 — giọng **nam** đúng 21,8 % so với `chung` 53,1 %, §6 xếp loại **TỪ VỰNG**.
 */
class VoiceAppNameAndMishearTest {

    private fun one(text: String, apps: List<String> = emptyList()): VoiceIntent =
        VoiceIntentParser.parseOne(text, apps = apps)

    private fun openApp(text: String, apps: List<String> = emptyList()): VoiceIntent.OpenApp {
        val got = one(text, apps)
        assertTrue(got is VoiceIntent.OpenApp, "«$text» phải là OpenApp, ra: $got")
        return got as VoiceIntent.OpenApp
    }

    private fun control(text: String): VoiceIntent.Control {
        val got = one(text)
        assertTrue(got is VoiceIntent.Control, "«$text» phải là Control, ra: $got")
        return got as VoiceIntent.Control
    }

    private fun read(text: String): VoiceIntent.Read {
        val got = one(text)
        assertTrue(got is VoiceIntent.Read, "«$text» phải là Read, ra: $got")
        return got as VoiceIntent.Read
    }

    // ── H3(a) · cách nói tên app theo âm Việt ────────────────────────────────────────────────────

    @Test
    fun `cach doc am Viet cua ten app deu mo dung app`() {
        // Cột phải là mã đích; cột trái lấy nguyên văn từ `apps.tsv` / §5 bảng nghe nhầm.
        listOf(
            "mở gu gồ máp" to VoiceAppTargets.GMAPS,
            "mở gu gồ mép" to VoiceAppTargets.GMAPS,
            "mở cái bản đồ" to VoiceAppTargets.GMAPS,
            "mở du túp" to VoiceAppTargets.YOUTUBE,
            "mở iu túp" to VoiceAppTargets.YOUTUBE,
            "mở du túp miu dích" to VoiceAppTargets.YT_MUSIC,
            "mở quây" to VoiceAppTargets.WAZE,
            "mở việt máp" to VoiceAppTargets.VIETMAP,
            "mở việt mát" to VoiceAppTargets.VIETMAP,
            "mở pô ti phai" to VoiceAppTargets.SPOTIFY,
            "mở sờ pô ti phai" to VoiceAppTargets.SPOTIFY,
            "mở zing em pê ba" to VoiceAppTargets.ZING,
        ).forEach { (text, key) ->
            assertEquals(key, openApp(text).appKey, "«$text» phải mở $key")
        }
    }

    /**
     * ⚠ Bài quan trọng nhất của vòng này — câu owner nêu tên.
     *
     * Cơ chế lỗi: nhãn app đã cài *"Google"* là một cụm MỘT từ trong từ vựng chung, nên nó khớp tại vị trí 0 và
     * trả ngay một ý định CÓ NGHĨA; vòng quét của [VoiceIntentParser] dừng luôn và cách nói HAI từ *"google map"*
     * không bao giờ được hỏi tới. Luật *"dãy dài nhất thắng"* phải áp cho **cả** tên app (nhánh (d')).
     */
    @Test
    fun `mo Google Map khong con bi nhan app Google mot tu nuot mat`() {
        val installed = listOf("Google", "Cài đặt")
        assertEquals(VoiceAppTargets.GMAPS, openApp("mở google map", installed).appKey)
        assertEquals(VoiceAppTargets.GMAPS, openApp("mở google maps", installed).appKey)
        // …và nhãn THẬT vẫn thắng khi hoà: *"mở google"* vẫn là app Google, không phải Google Maps.
        assertEquals("Google", openApp("mở google", installed).appName)
        assertNotEquals(VoiceAppTargets.GMAPS, openApp("mở google", installed).appKey)
    }

    @Test
    fun `menh de o van con sau ten app noi theo am Viet`() {
        assertEquals(2, openApp("mở gu gồ máp vào ô số 2").slot, "mất mệnh đề ô là làm một việc khác việc được bảo")
    }

    @Test
    fun `dong app van la APP_CLOSE, khong bi nhanh moi bien thanh MO`() {
        // Nhánh (d') cố ý chỉ nhận động từ MỞ/BẬT — *"đóng …"* phải đi tiếp để ra câu *"chưa đóng được app"*.
        val got = one("đóng google map", listOf("Google"))
        assertTrue(got is VoiceIntent.Unknown, "ra: $got")
        assertEquals(VoiceUnknownReason.APP_CLOSE, (got as VoiceIntent.Unknown).reason)
    }

    /**
     * H3(c) — app KHÔNG có trong bảng đích (Zalo · CarPlay · Android Auto) đi qua [VoiceAppPhonetics]: cách đọc
     * sinh từ chính NHÃN, rồi `VoiceWiring.withPhonetics` cắm chúng vào bản đồ nhãn→gói.
     *
     * Bài này dựng lại đúng bộ khoá ấy rồi thả vào bộ phân tích — chứng minh đường đi có thật, không cần Android.
     */
    @Test
    fun `app ngoai bang dich goi duoc nho cach doc sinh tu nhan`() {
        listOf("Zalo" to "mở za lô", "CarPlay" to "mở ca plây", "Android Auto" to "mở an đroi ô tô",
            "ChatGPT" to "mở chát gi pi ti")
            .forEach { (label, say) ->
                val keys = listOf(label) + VoiceAppPhonetics.spokenForms(label)
                val got = openApp(say, keys)
                assertTrue(got.appName in keys, "«$say» phải trỏ về một khoá của «$label», ra: ${got.appName}")
            }
    }

    // ── H3 · giọng NAM ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `cach goi giong Nam tro dung nut`() {
        assertEquals("win_lf" to 1, control("bật kiếng lái lên").let { it.id to it.value })
        assertEquals("trunk" to 1, control("mở cửa hậu").let { it.id to it.value })
        assertEquals("trunk" to 1, control("mở thùng sau").let { it.id to it.value })
        assertEquals("seatc" to 1, control("mở quạt ghế").let { it.id to it.value })
        assertEquals("readl" to 0, control("tắt đèn nóc").let { it.id to it.value })
        assertEquals("pm25" to 1, control("bật máy lọc không khí").let { it.id to it.value })
        assertEquals("win_lf" to 1, control("mở kiếng trước trái").let { it.id to it.value })
    }

    @Test
    fun `quat ghe khong cuop mat quat gio — luat day dai nhat thang`() {
        assertEquals("fan", control("tăng quạt").id, "*\"quạt\"* một mình vẫn là quạt gió")
        assertEquals("seatc", control("mở quạt ghế").id)
    }

    // ── H4 · chuỗi mô hình nghe nhầm, bí danh CÓ ĐIỀU KIỆN ───────────────────────────────────────

    @Test
    fun `cac bui la loc bui — tu ngu canh nam ngay trong cum`() {
        assertEquals("pm25" to 1, control("các bụi").let { it.id to it.value })
    }

    /**
     * Vế NGƯỢC, và nó mới là vế giữ an toàn: `đọc` là **động từ ĐỌC**, `ngày` là một từ đời thường. Một bí danh
     * trần `"doc ngay"` → `pm25_clean_now` sẽ nuốt mọi câu dạng *"đọc ngày …"* mà **không gì báo**.
     */
    @Test
    fun `doc ngay mot minh KHONG bien thanh lenh loc khi thieu tu ngu canh`() {
        val got = one("đọc ngày")
        assertTrue(
            got !is VoiceIntent.Control || got.id != "pm25_clean_now",
            "bí danh nghe nhầm đã bật khi chưa có từ ngữ cảnh: $got",
        )
        // …và động từ ĐỌC vẫn nguyên vẹn ở câu thường gặp nhất.
        assertEquals("soc", read("đọc pin").datumId)
        assertEquals("soc", read("xem pin").datumId)
    }

    @Test
    fun `dieu kien ngu canh doc tren CA CAU, khong tren mot cua so hai tu`() {
        // Có chữ `lọc` ở đâu đó trong câu ⇒ cụm nghe nhầm được bật; không có ⇒ nằm im (bài trên).
        val terms = VoiceGrammar.plusMisheard(VoiceGrammar.terms(), VoiceLexicon.tokenize("lọc đọc ngày"))
        assertTrue(terms.any { it.words == listOf("doc", "ngay") && it.id == "pm25_clean_now" })
        val none = VoiceGrammar.plusMisheard(VoiceGrammar.terms(), VoiceLexicon.tokenize("đọc ngày"))
        assertEquals(VoiceGrammar.terms(), none, "câu không có từ ngữ cảnh phải nhận ĐÚNG danh sách cũ")
    }

    /** [ĐO XE 2026-09-16, DL3 1.68 ×2] mô hình in ra *"đang đọc sách"* cho *"đèn đọc sách"*. */
    @Test
    fun `dang doc sach la den doc sach`() {
        assertEquals("readl", control("đang đọc sách").id)
        assertEquals("readl" to 0, control("tắt đang đọc sách").let { it.id to it.value })
    }
}
