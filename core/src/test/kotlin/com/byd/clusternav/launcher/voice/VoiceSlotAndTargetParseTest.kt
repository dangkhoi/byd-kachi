package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * ═══ V1.1 · HAI MỆNH ĐỀ ĐUÔI MỚI — *"vào ô N"* và *"bằng &lt;app&gt;"* ════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R15 · R17**. Tách khỏi [VoiceIntentParserTest] để giữ trần 500
 * dòng (CLAUDE.md §4.1) và để đọc được: cả tệp này nói về đúng hai câu hỏi owner đặt ra 2026-09-14 —
 * *"mở YouTube vào ô số 2 được không"* và *"mở nhạc bằng YT Music, dẫn đường bằng GMaps được không"*.
 *
 * Danh sách app truyền vào là **nhãn hệ thống** (như `PackageManager` trả về), đúng hình dạng mà
 * `VoiceWiring.appsByLabel` cấp — không phải một danh sách bịa cho dễ khớp.
 */
class VoiceSlotAndTargetParseTest {

    private val apps = listOf("YouTube", "YouTube Music", "Bản đồ", "VTV Go")

    private fun one(text: String): VoiceIntent = VoiceIntentParser.parseOne(text, apps = apps)

    private fun openApp(text: String): VoiceIntent.OpenApp =
        one(text) as? VoiceIntent.OpenApp ?: error("«$text» không ra OpenApp mà ra ${one(text)}")

    private fun media(text: String): VoiceIntent.Media =
        one(text) as? VoiceIntent.Media ?: error("«$text» không ra Media mà ra ${one(text)}")

    private fun nav(text: String): VoiceIntent.Nav =
        one(text) as? VoiceIntent.Nav ?: error("«$text» không ra Nav mà ra ${one(text)}")

    // ══ (1) *"vào ô N"* — câu hỏi gốc của owner ══════════════════════════════════════════════════════════

    /** Sáu cách nói cùng một ý, cả sáu phải ra **cùng một** ô. Đây là bài chính của R15. */
    @Test
    fun `moi cach noi ve mot O deu ra cung mot so`() {
        listOf(
            "mở youtube vào ô số 2",
            "mở youtube vào ô số hai",
            "mở youtube vào ô thứ hai",
            "mở youtube vào ô 2",
            "mở YouTube ô số hai",
            "đưa youtube vào ô số hai",
        ).forEach { text ->
            val hit = openApp(text)
            assertEquals("YouTube", hit.appName, "«$text» phải trỏ đúng app")
            assertEquals(2, hit.slot, "«$text» phải ra ô 2")
        }
    }

    /** Tiếng Anh — cùng hai từ khoá `slot` / `in`. */
    @Test
    fun `tieng Anh open X in slot N`() {
        listOf("open youtube in slot 2", "open youtube slot number 2", "open YouTube in slot two").forEach {
            assertEquals(2, openApp(it).slot, "«$it»")
        }
    }

    /** Số ô giữ **đúng như người ta nói** (1-based), và **không bị kẹp** — xem KDoc [VoiceIntent.OpenApp.slot]. */
    @Test
    fun `so o giu nguyen 1-based va khong bi kep`() {
        assertEquals(1, openApp("mở youtube vào ô số một").slot)
        assertEquals(6, openApp("mở youtube vào ô số sáu").slot)
        assertEquals(9, openApp("mở youtube vào ô số chín").slot, "ngoài dải vẫn phải giữ nguyên — tầng biết bố cục mới được kẹp")
    }

    /** Câu KHÔNG nêu ô ⇒ `null`, tức y như hành vi 1.49 (mở toàn màn). Không được tự bịa một ô. */
    @Test
    fun `khong neu o thi slot la null`() {
        listOf("mở youtube", "mở ứng dụng VTV Go", "mở Bản đồ").forEach {
            assertNull(openApp(it).slot, "«$it» không nêu ô mà lại ra một ô")
        }
    }

    /** *"Mở ứng dụng X vào ô N"* — nhánh đi qua từ khoá launcher cũng phải nhận ra ô. */
    @Test
    fun `mo ung dung X vao o N cung nhan ra o`() {
        val hit = openApp("mở ứng dụng VTV Go vào ô số ba")
        assertEquals("VTV Go", hit.appName)
        assertEquals(3, hit.slot)
    }

    /** *"ô tối đa"* không phải một số ô — sentinel chỉ có nghĩa với nút có dải giá trị. */
    @Test
    fun `o toi da khong phai mot so o`() {
        assertNull(openApp("mở youtube vào ô tối đa").slot)
        assertNull(openApp("mở youtube vào ô").slot, "thiếu số thì không có ô nào")
    }

    // ══ (2) *"bằng &lt;app&gt;"* cho NHẠC ════════════════════════════════════════════════════════════════

    @Test
    fun `ten bai va app nhac tach dung`() {
        val hit = media("phát bài diễm xưa bằng youtube music")
        assertEquals(VoiceMediaOp.QUERY, hit.op)
        assertEquals("diễm xưa", hit.query)
        assertEquals(VoiceAppTargets.YT_MUSIC, hit.app)
    }

    /** Bốn cụm đánh dấu, bốn cách nói tên app — cùng một kết quả. */
    @Test
    fun `moi cum danh dau va moi cach goi ten deu ra cung mot app`() {
        listOf(
            "phát bài diễm xưa bằng yt music",
            "phát bài diễm xưa trên youtube music",
            "mở bài diễm xưa với youtube music",
            "nghe bài diễm xưa qua nhạc youtube",
        ).forEach {
            assertEquals(VoiceAppTargets.YT_MUSIC, media(it).app, "«$it»")
            assertEquals("diễm xưa", media(it).query, "«$it»")
        }
    }

    /** *"youtube music"* (2 từ) phải thắng *"youtube"* (1 từ) — luật dãy dài nhất thắng. */
    @Test
    fun `youtube music thang youtube`() {
        assertEquals(VoiceAppTargets.YT_MUSIC, media("phát bài diễm xưa bằng youtube music").app)
        assertEquals(VoiceAppTargets.YOUTUBE, media("phát bài diễm xưa bằng youtube").app)
    }

    @Test
    fun `app nhac khac deu nhan ra`() {
        assertEquals(VoiceAppTargets.SPOTIFY, media("phát bài hạ trắng bằng spotify").app)
        assertEquals(VoiceAppTargets.ZING, media("phát bài hạ trắng bằng zing mp3").app)
    }

    /** Không nêu app ⇒ `null` (tầng thi hành tự chọn theo phiên nhạc đang chạy). */
    @Test
    fun `khong neu app nhac thi de null`() {
        val hit = media("phát bài diễm xưa")
        assertEquals("diễm xưa", hit.query)
        assertNull(hit.app)
    }

    /** *"phát nhạc bằng spotify"* — có app mà không có tên bài ⇒ lệnh PHÁT, không phải QUERY rỗng. */
    @Test
    fun `co app ma khong co ten bai thi la lenh phat`() {
        val hit = media("phát nhạc bằng spotify")
        assertEquals(VoiceMediaOp.PLAY, hit.op)
        assertEquals(VoiceAppTargets.SPOTIFY, hit.app)
    }

    // ══ (3) *"bằng &lt;app&gt;"* cho DẪN ĐƯỜNG ═══════════════════════════════════════════════════════════

    @Test
    fun `diem den va app dan duong tach dung`() {
        val hit = nav("dẫn đường tới chợ Bến Thành bằng google maps")
        assertEquals("chợ Bến Thành", hit.query)
        assertEquals(VoiceAppTargets.GMAPS, hit.app)
    }

    @Test
    fun `ba app dan duong deu goi duoc bang nhieu ten`() {
        mapOf(
            "dẫn đường tới sân bay bằng google map" to VoiceAppTargets.GMAPS,
            "dẫn đường tới sân bay bằng bản đồ google" to VoiceAppTargets.GMAPS,
            "chỉ đường đến sân bay bằng waze" to VoiceAppTargets.WAZE,
            // [ĐO] mô hình không có chữ `waze`; *"quây"* là âm Việt người ta vẫn dùng (xem VoiceSynonyms.APP_TARGETS).
            "chỉ đường đến sân bay bằng quây" to VoiceAppTargets.WAZE,
            "dẫn đường tới sân bay bằng việt map" to VoiceAppTargets.VIETMAP,
            "dẫn đường tới sân bay bằng vietmap" to VoiceAppTargets.VIETMAP,
        ).forEach { (text, key) -> assertEquals(key, nav(text).app, "«$text»") }
    }

    @Test
    fun `khong neu app dan duong thi de null`() {
        val hit = nav("dẫn đường tới chợ Bến Thành")
        assertEquals("chợ Bến Thành", hit.query)
        assertNull(hit.app)
    }

    // ══ (4) KHÔNG cắt nhầm — ba cái bẫy của luật này ═════════════════════════════════════════════════════

    /**
     * *"bằng"* nằm GIỮA tên địa điểm thì không được cắt.
     *
     * Đây là lý do luật đòi cụm phải chạm **cuối câu** và phải theo sau bởi một app **đã biết** — xem KDoc
     * `VoiceIntentParser.withTarget`.
     */
    @Test
    fun `chu bang trong ten dia diem khong bi cat`() {
        listOf(
            "dẫn đường tới cầu Bằng Lăng",
            "dẫn đường tới phố Bằng Liệt",
            "dẫn đường tới chợ Bến Thành bằng xe máy",
        ).forEach {
            assertNull(nav(it).app, "«$it» không nêu app nào mà lại bị cắt")
        }
        assertEquals("cầu Bằng Lăng", nav("dẫn đường tới cầu Bằng Lăng").query)
        assertEquals("chợ Bến Thành bằng xe máy", nav("dẫn đường tới chợ Bến Thành bằng xe máy").query)
    }

    /** Tên app đứng GIỮA câu (không phải cuối) cũng không được cắt — nó có thể là một phần của tên. */
    @Test
    fun `ten app dung giua cau khong bi cat`() {
        val hit = nav("dẫn đường tới quán cà phê google maps ở quận một")
        assertNull(hit.app)
        assertEquals("quán cà phê google maps ở quận một", hit.query)
    }

    /** Cụm nhạc không được nhận app dẫn đường và ngược lại — [VoiceAppKind] là cổng. */
    @Test
    fun `khong lay nham app khac loai`() {
        assertNull(media("phát bài diễm xưa bằng waze").app, "Waze không phải app nhạc")
        assertNull(nav("dẫn đường tới sân bay bằng spotify").app, "Spotify không phải app dẫn đường")
    }

    // ══ (5) Câu lệnh xe KHÔNG được đổi nghĩa vì hai luật mới ═════════════════════════════════════════════

    /**
     * Bài **hồi quy**: mọi câu điều khiển xe vẫn ra đúng ý định cũ.
     *
     * Hai bảng mới ([VoiceLexicon.SLOT_WORDS] có từ một chữ cái `o`, [VoiceLexicon.BY_APP_MARKERS] có `tren`
     * `qua` — những từ rất thường) là đúng loại thay đổi có thể lặng lẽ đổi nghĩa một câu đang chạy tốt.
     */
    @Test
    fun `cau lenh xe khong doi nghia`() {
        assertEquals(VoiceIntent.Control("readl", 1), one("bật đèn đọc"))
        assertEquals(VoiceIntent.Control("temp", 24), one("đặt nhiệt độ hai mươi tư"))
        assertEquals(VoiceIntent.Control("fan", null, 1), one("tăng gió"))
        assertEquals(VoiceIntent.Read("soc"), one("xem pin"))
        assertEquals(VoiceIntent.Media(VoiceMediaOp.NEXT), one("bài tiếp theo"))
        assertEquals(VoiceIntent.Media(VoiceMediaOp.PAUSE), one("tạm dừng"))
    }
}
