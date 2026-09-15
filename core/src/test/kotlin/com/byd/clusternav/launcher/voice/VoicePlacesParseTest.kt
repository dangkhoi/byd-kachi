package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.SavedPlace
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.navigation.NavApps
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ SỔ ĐỊA CHỈ · CÂU NÓI → Ý ĐỊNH → APP ĐÍCH ════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-addresses.html` R2 · R3 · R4. Thuần Kotlin ⇒ chạy off-car.
 *
 * ## Hai nhóm bài, nhóm thứ hai quan trọng không kém
 * Nhóm A kiểm **thứ mới chạy được**. Nhóm B (*"không được phá"*) khoá lại đúng những câu mà bảy động từ nơi chốn
 * có thể cướp mất: *"về bố cục 2 cột"* (cố ý để NO_VERB), *"đèn đọc"* (bỏ dấu ra `den`, trùng *"đến"*), và điểm
 * đến MỞ (*"dẫn đường tới Bitexco"*). Đó là lý do động từ nơi chốn được gác bằng [VoicePlaces.match] thay vì thả
 * vào [VoiceGrammar.VERBS] — xem KDoc [VoicePlaces.PLACE_VERBS].
 */
class VoicePlacesParseTest {

    private val home = SavedPlace("Nhà", "123 Nguyễn Trãi, Hà Nội", 21.0045, 105.8412)
    private val work = SavedPlace("Công ty", "Keangnam Landmark 72")
    private val granny = SavedPlace("Nhà ngoại", "Thôn Đoài, Bắc Ninh")

    private val book = listOf(home, work, granny)
    private val labels = VoicePlaces.labelsOf(book)

    private val profiles = listOf("Mặc định", "Vợ")
    private val apps = listOf("VietMap Live", "YouTube")

    private fun one(s: String, places: List<String> = labels): VoiceIntent =
        VoiceIntentParser.parseOne(s, profiles, apps, places)

    private fun saved(s: String, places: List<String> = labels): VoiceIntent.NavigateSaved =
        one(s, places) as? VoiceIntent.NavigateSaved
            ?: throw AssertionError("câu \"$s\" phải ra NavigateSaved, thực tế: ${one(s, places)}")

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    // ══ A · CÂU NÓI → Ý ĐỊNH ═════════════════════════════════════════════════════════════════════════════

    /** Bốn cách nói cùng một ý *"về nhà"* — người ta đổi giữa chúng trong cùng một ngày. */
    @Test
    fun `bon cach noi ve nha deu ra cung mot noi`() {
        listOf("về nhà", "đi về nhà", "đến nhà", "về Nhà").forEach {
            assertEquals(home.name, saved(it).placeName, "câu: \"$it\"")
        }
    }

    /** *"đi làm"* là cách nói **thường ngày** của *"đến công ty"* — cả hai phải về cùng một mục. */
    @Test
    fun `di lam va den cong ty la mot`() {
        listOf("đến công ty", "đi làm", "tới cơ quan", "đi đến chỗ làm").forEach {
            assertEquals(work.name, saved(it).placeName, "câu: \"$it\"")
        }
    }

    /** Nhãn do người dùng TỰ ĐẶT khớp bằng chính tên nó — không cần khai thêm gì (CLAUDE.md §7). */
    @Test
    fun `nhan tu dat khop bang chinh ten no`() {
        assertEquals(granny.name, saved("đi Nhà ngoại").placeName)
        assertEquals(granny.name, saved("dẫn đường tới nhà ngoại").placeName, "không dấu/khác hoa thường vẫn khớp")
    }

    /** Động từ dẫn đường **đã có sẵn** cũng phải nhận ra một nơi đã lưu, không chỉ mấy động từ mới. */
    @Test
    fun `dong tu dan duong cu cung nhan ra noi da luu`() {
        listOf("dẫn đường đến nhà", "chỉ đường tới nhà", "tìm đường đến nhà").forEach {
            assertEquals(home.name, saved(it).placeName, "câu: \"$it\"")
        }
    }

    /** Mệnh đề *"bằng &lt;app&gt;"* vẫn được cắt đúng — nó đứng ở cuối, sau tên nơi. */
    @Test
    fun `van chon duoc app dich trong cau`() {
        val i = saved("về nhà bằng google map")
        assertEquals(home.name, i.placeName)
        assertEquals(VoiceAppTargets.GMAPS, i.app)
    }

    /**
     * **Sổ TRỐNG vẫn ra [VoiceIntent.NavigateSaved]** với nhãn chuẩn — R4.
     *
     * Rơi về [VoiceIntent.Nav] ở đây là bắn chữ *"nhà"* cho app bản đồ, tức dẫn người ta tới một quán ăn tên
     * *"Nhà"*. Tầng thi hành tra sổ, không thấy, rồi **nói thẳng** — xem [VoiceReply.placeNotSaved].
     */
    @Test
    fun `cach noi dung san van ra mot nhan du so trong`() {
        assertEquals(VoicePlaces.HOME, saved("về nhà", emptyList()).placeName)
        assertEquals(VoicePlaces.WORK, saved("đi làm", emptyList()).placeName)
        assertTrue(
            VoiceReply.placeNotSaved(saved("về nhà", emptyList()), VoicePlaces.HOME).contains(VoicePlaces.HOME),
            "câu trả lời phải nói ĐÚNG NHÃN còn thiếu, không phải 'không hiểu'",
        )
    }

    // ══ B · KHÔNG ĐƯỢC PHÁ ═══════════════════════════════════════════════════════════════════════════════

    /**
     * ⚠⚠ Câu này được `VoiceIntentParserTest` cố ý giữ ở [VoiceUnknownReason.NO_VERB] (bố cục chưa nằm trong tập
     * đóng của giọng nói). Nếu `về` thành động từ dẫn đường **vô điều kiện** thì nó lặng lẽ thành *"dẫn đường tới
     * «bố cục 2 cột»"*. Bài này khoá đúng chỗ đó.
     */
    @Test
    fun `dong tu noi chon KHONG duoc cuop cau khong phai dia diem`() {
        assertEquals(
            VoiceUnknownReason.NO_VERB,
            (one("về bố cục 2 cột") as? VoiceIntent.Unknown)?.reason,
            "phần đuôi không khớp nơi nào ⇒ phải đi tiếp đúng đường cũ",
        )
    }

    /** Bỏ dấu thì *"đến"* = *"đèn"* — nhãn nút dài hơn phải thắng (`headMatch`), kể cả khi sổ có mục tên *"Đọc"*. */
    @Test
    fun `den va den - nhan nut van thang`() {
        assertEquals(VoiceIntent.Control("readl", 1), one("đèn đọc"))
        assertEquals(VoiceIntent.Control("readl", 1), one("đèn đọc", listOf("Đọc")))
        assertEquals(VoiceIntent.Control("readl", 0), one("tắt đèn đọc"))
    }

    /** Điểm đến MỞ không đổi một chữ: không khớp sổ ⇒ vẫn là [VoiceIntent.Nav] nguyên văn. */
    @Test
    fun `diem den mo van di duong cu`() {
        assertEquals(VoiceIntent.Nav("Bitexco"), one("dẫn đường tới Bitexco"))
        assertEquals(VoiceIntent.Nav("chợ Bến Thành"), one("chỉ đường đến chợ Bến Thành"))
    }

    /**
     * Nơi đã lưu **KHÔNG** phải hỏi lại, khác điểm đến mở.
     *
     * Lý do đầy đủ ở [VoiceRiskTable.of]: cổng hỏi-lại của [VoiceIntent.Nav] sinh ra vì **nguồn** (chuỗi do nhận
     * dạng tự do đọc), mà nhãn thì đến từ một tập đóng người dùng đã gõ.
     */
    @Test
    fun `noi da luu khong phai hoi lai`() {
        assertEquals(VoiceRisk.NORMAL, VoiceRiskTable.of(saved("về nhà")))
        assertEquals(VoiceRisk.CONFIRM, VoiceRiskTable.of(VoiceIntent.Nav("Bitexco")), "điểm đến MỞ thì vẫn hỏi")
    }

    // ══ C · CHỌN APP THEO DỮ LIỆU CỦA MỤC (R3) ═══════════════════════════════════════════════════════════

    private val preference: List<String> =
        listOf(NavApps.VIETMAP_LIVE) + NavApps.GMAPS.toList() + NavApps.WAZE.toList()

    private val allInstalled: Set<String> = NavApps.VIETMAP + NavApps.GMAPS + NavApps.WAZE

    /**
     * ⚠⚠ Bài quan trọng nhất của R3. VietMap đứng ĐẦU thứ tự ưu tiên và [ĐO] nó **chỉ nhận toạ độ**; một mục chỉ
     * có chữ mà đẩy vào đó là mở app rồi bảo người ta tự gõ — trong khi Google Maps ngay dưới nhận được nguyên văn.
     */
    @Test
    fun `muc chi co chu thi chon app NHAN DUOC CHU`() {
        val target = VoiceAppTargets.navFor(hasCoords = false, preference, allInstalled)
        assertEquals(VoiceAppTargets.GMAPS, target?.key)
        assertFalse(target?.needsCoords ?: true)
    }

    /** Có toạ độ ⇒ VietMap dùng được, và nó là app đứng đầu thứ tự ưu tiên trên xe owner. */
    @Test
    fun `muc co toa do thi VietMap dung duoc va duoc uu tien`() {
        val target = VoiceAppTargets.navFor(hasCoords = true, preference, allInstalled)
        assertEquals(VoiceAppTargets.VIETMAP, target?.key)
        assertTrue(target?.destinationLaunch(true) != null, "app đứng đầu phải THẬT SỰ giao được, không chỉ được chọn")
    }

    /** Chỉ cài VietMap + mục không toạ độ ⇒ vẫn trả VietMap để chỗ gọi **mở app và nói rõ**, không trả `null`. */
    @Test
    fun `khong app nao giao duoc thi van tra app de con noi ra`() {
        val target = VoiceAppTargets.navFor(hasCoords = false, preference, NavApps.VIETMAP)
        assertEquals(VoiceAppTargets.VIETMAP, target?.key)
        assertTrue(target!!.needsCoords, "…và chỗ gọi đọc cờ này để nói 'thêm lat/lng cho mục này'")
    }

    /** Không có app dẫn đường nào trên máy ⇒ `null` (chỗ gọi nói `noNavApp`), không sập. */
    @Test
    fun `khong co app dan duong nao thi tra null`() {
        assertEquals(null, VoiceAppTargets.navFor(hasCoords = true, preference, emptySet()))
    }

    // ══ D · NGỮ PHÁP ĐỘNG (R6) ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sổ trống ⇒ **không khai cách nói nào**; có mục ⇒ khai nhãn + cách nói dựng sẵn + một cụm có động từ.
     *
     * Cùng luật với tên app đích: khai *"công ty"* trên một chiếc xe chưa lưu địa chỉ công ty là mở thêm một đường
     * cho bộ giải mã nghe nhầm vào một mục **không tồn tại**.
     */
    @Test
    fun `ngu phap chi khai cach noi cua noi DA LUU`() {
        assertEquals(emptyList<String>(), VoicePlaces.spokenPhrases(emptyList()))
        val phrases = VoicePlaces.spokenPhrases(labels)
        assertTrue("Nhà ngoại" in phrases, "nhãn người dùng tự đặt phải nói được")
        assertTrue("đến Nhà ngoại" in phrases, "LM bigram cần biết động từ đi cạnh được nhãn")
        assertTrue("đi làm" in phrases, "cách nói dựng sẵn của nhãn chuẩn đi kèm")
        assertFalse(phrases.any { it.contains("[") }, "không mục rác nào")
    }

    /** Nhãn địa chỉ là tiếng Việt đời thường ⇒ vào được hotwords (khác tên app tiếng Anh). */
    @Test
    fun `nhan dia chi di vao hotwords cua bo nhan dang`() {
        val none = SherpaBiasing.hotwordsFile()
        val withBook = SherpaBiasing.hotwordsFile(labels)
        assertTrue(withBook.contains("NHÀ NGOẠI"), "hotwords phải là chữ HOA có dấu — xem KDoc SherpaHotwords")
        assertFalse(none.contains("NHÀ NGOẠI"), "sổ trống thì không khai gì thêm")
    }
}
