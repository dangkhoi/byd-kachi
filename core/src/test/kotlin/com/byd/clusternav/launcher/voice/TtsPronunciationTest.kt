package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ §9 · CHỮ LATIN → ÂM VIỆT — khoá **ba** lời hứa, lời hứa thứ ba mới khó ══════════════════════════════════
 *
 * Nguồn của bảng: owner nghe mẫu Piper `vi_VN-vais1000` ngày 2026-09-16 (*"Kachi"* → **"K-A-cê-hát-i"**) + lượt
 * quét chuỗi THẬT của `TelemetryRegistry` / `ControlRegistry` / `VoiceAppTargets` (spec §9).
 *
 *  1. **đổi** đúng thứ máy đọc không đọc được (mọi dòng của [TtsPronunciation.PHRASES], cụm dài thắng cụm ngắn);
 *  2. **không đổi** chữ tiếng Việt xung quanh (khớp trọn từ, không khớp vào giữa từ);
 *  3. **không rò ra ngoài cửa đọc** — chuỗi hiển thị / ghi nhật ký / tra clip vẫn là chuỗi gốc, và phép đổi chỉ
 *     có **một** chỗ gọi. Lời hứa này là thứ duy nhất mà một bài `assertEquals` thuần không canh nổi, nên nó
 *     được canh bằng một lượt quét source (cùng lệ `VoiceCommandWiringContractTest`, CLAUDE.md §8).
 */
class TtsPronunciationTest {

    private fun say(s: String) = TtsPronunciation.normalise(s)

    // ── (1) BẢNG ────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Mỗi dòng của bảng, đọc một mình, phải ra đúng cách đọc đã khai.
     *
     * Viết bằng chính [TtsPronunciation.PHRASES] chứ không chép lại cột phải: chép lại là dựng **bản sao thứ
     * hai** của bảng, và ngày ai đó sửa một dòng thì bài canh sẽ đỏ vì bản sao chứ không vì hành vi.
     */
    @Test
    fun `moi dong trong bang deu doc ra dung cach doc da khai`() {
        TtsPronunciation.PHRASES.forEach { (written, spoken) ->
            assertEquals(spoken, say(written), "dòng «$written» của bảng không ra đúng cách đọc")
        }
    }

    /** Owner đọc mẫu 2026-09-16 — cột phải là chữ owner nói ra, không phải chữ bài canh tự nghĩ. */
    @Test
    fun `ten rieng owner doc mau ra dung am viet`() {
        val want = listOf(
            "Kachi" to "Ka-chi",
            "YouTube" to "Du-túp",
            "YouTube Music" to "Du-túp Mu-dích",
            "Google" to "Gu-gồ",
            "Google Maps" to "Gu-gồ Máp",
            "Google Map" to "Gu-gồ Máp",
            "Waze" to "Quây",
            "CarPlay" to "Ca-plây",
            "Android Auto" to "An-đroi Ô-tô",
            "Zalo" to "Za-lô",
            "Spotify" to "Spô-ti-phai",
            "Vietmap" to "Việt-máp",
            "TikTok" to "Tíc-tóc",
            "Netflix" to "Nét-phlích",
            "OK" to "ô-kê",
        )
        want.forEach { (written, spoken) -> assertEquals(spoken, say(written), "«$written»") }
    }

    /** Viết HOA / viết thường / viết lẫn đều là **cùng một** cái tên ⇒ cùng một cách đọc. */
    @Test
    fun `khop khong phan biet HOA thuong`() {
        listOf("KACHI", "kachi", "KaChI").forEach { assertEquals("Ka-chi", say(it), "«$it»") }
        listOf("KM/H", "km/h", "Km/H").forEach { assertEquals("ki-lô-mét trên giờ", say(it), "«$it»") }
    }

    // ── (2) DÀI NHẤT TRƯỚC ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Cụm dài thắng cụm ngắn — nếu không thì *"YouTube Music"* ra *"Du-túp Music"* và `km/h` ra
     * *"ki-lô-mét trên h"*: đúng một nửa, tức vẫn đánh vần đúng cái nửa còn lại.
     */
    @Test
    fun `cum dai thang cum ngan`() {
        assertEquals("Du-túp Mu-dích", say("YouTube Music"))
        assertEquals("Gu-gồ Máp", say("Google Maps"))
        assertEquals("ki-lô-mét trên giờ", say("km/h"))
        assertEquals("ki-lô-oát giờ", say("kWh"))
        assertEquals("ki-lô-oát giờ trên một trăm ki-lô-mét", say("kWh/100km"))
        assertEquals("An-đroi Ô-tô", say("Android Auto"))
    }

    /**
     * Nhãn THẬT của bộ đăng ký, không phải chuỗi bịa ra.
     *
     * `t("soc", "Pin (SOC)", …)` · `t("soh_oem", "Sức khỏe pin (SOH)", …)` · `t("vin", "Số VIN", …)` ·
     * `ControlDef("ac_auto", "Điều hòa AUTO", …)`. Đây là chỗ luật dài-nhất-trước trả công: đổi lẻ `SOC`→*"pin"*
     * cho ra *"Pin (pin)"*, đọc lên là *"pin pin"*.
     */
    @Test
    fun `nhan that cua bo dang ky doc ra cau nghe duoc`() {
        assertEquals("pin", say("Pin (SOC)"))
        assertEquals("sức khoẻ pin", say("Sức khỏe pin (SOH)"))
        assertEquals("số vin", say("Số VIN"))
        assertEquals("Điều hòa au-tô", say("Điều hòa AUTO"))
        assertEquals("Tầm hoạt động e vê", say("Tầm hoạt động EV"))
        assertEquals("Bụi mịn pê mờ hai chấm năm", say("Bụi mịn PM2.5"))
        assertEquals("ô-đô tổng", say("Odo tổng"))
        assertEquals("Chìa blu-tút", say("Chìa Bluetooth"))
        assertEquals("i-on âm", say("Ion âm"))
        assertEquals("ca-mê-ra 360", say("Camera 360"))
    }

    // ── (3) RANH GIỚI TỪ ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Khoá của bảng **không** được khớp vào giữa một từ tiếng Việt.
     *
     * *"tốc độ cao"* là ca thật và là ca nguy hiểm nhất: nó chứa nguyên xi khoá `"độ c"`. Cứu nó là vế **phải**
     * của luật ranh giới (ký tự sau là chữ cái ⇒ không khớp) — gỡ vế đó đi thì câu này đỏ.
     */
    @Test
    fun `khong dung vao giua mot tu tieng viet`() {
        assertEquals("tốc độ cao", say("tốc độ cao"))
        assertEquals("Chế độ Comfort", say("Chế độ Comfort"))
        assertEquals("okay", say("okay"))
        assertEquals("Khoang lái", say("Khoang lái"))
        assertEquals("kmx", say("kmx"))
        assertEquals("pin", say("pin"))
        assertEquals("Đang sạc", say("Đang sạc"))
        assertEquals("Mở hết kính", say("Mở hết kính"))
    }

    /** Đơn vị dính ngay sau con số vẫn phải đọc ra — và phải tự có dấu cách, không dính thành một từ lạ. */
    @Test
    fun `don vi dinh ngay sau con so van doc duoc`() {
        assertEquals("46 phần trăm", say("46%"))
        assertEquals("46 phần trăm", say("46 %"))
        assertEquals("50 ki-lô-mét", say("50km"))
        assertEquals("24 độ", say("24°C"))
        assertEquals("24 độ", say("24 độ C"))
        assertEquals("257 ki-lô-mét", say("257 km"))
    }

    // ── (4) LUỸ ĐẲNG ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * `normalise(normalise(s)) == normalise(s)`.
     *
     * Không phải một tính chất trang trí: cửa đọc có thể bị gọi lại trên chính chuỗi nó vừa trả (thử lại một
     * câu, một tầng đệm ghi-rồi-đọc). Mất tính này thì *"Số VIN"* thành *"số số vin"* sau hai lượt — và không ai
     * phát hiện ra vì nó **vẫn đọc được**, chỉ là sai.
     */
    @Test
    fun `chuan hoa hai lan bang mot lan`() {
        val inputs = TtsPronunciation.PHRASES.keys + TtsPronunciation.PHRASES.values + SAMPLES
        inputs.forEach { s ->
            val once = say(s)
            assertEquals(once, say(once), "«$s» → «$once» đổi tiếp ở lượt hai")
        }
    }

    // ── (5) ĐƯỜNG LÙI CHO CHỮ LATIN LẠ ──────────────────────────────────────────────────────────────────────

    /**
     * Từ Latin **không có trong bảng** không được rơi về đánh vần kiểu Piper.
     *
     * Hai đường lùi, theo đúng thứ tự: âm tiếng Anh ([VoiceAppPhonetics]) trước, tên chữ cái tiếng Việt
     * ([TtsPronunciation.LETTERS]) sau — nên `TV` ra *"ti vi"* (một từ người Việt đọc) chứ không ra *"tê-vê"*.
     */
    @Test
    fun `chu latin la di qua duong lui thay vi bi danh van`() {
        assertEquals("chát gi pi ti", say("ChatGPT"))
        assertEquals("em pê ba", say("MP3"))
        assertEquals("ti vi", say("TV"))
        // Chữ viết tắt không có âm nào ⇒ đánh vần bằng TÊN CHỮ CÁI TIẾNG VIỆT (đúng cách người Việt đọc).
        assertEquals("em xê u", say("MCU"))
        assertEquals("hát u dê", say("HUD"))
        assertEquals("e vê", say("EV"))
        assertEquals("hát e vê", say("HEV"))
    }

    /**
     * Đường lùi **không được** đụng vào từ tiếng Việt viết không dấu.
     *
     * Đây là lý do [TtsPronunciation.soundOut] dùng lại luật *"phủ hết hoặc không gì cả"* của
     * [VoiceAppPhonetics] thay vì tự ghép: *"pin"* · *"cao"* · *"trong"* · *"quy"* đều là chữ Latin thuần, và
     * một bộ ghép âm dễ dãi sẽ đọc chúng thành tiếng Anh.
     */
    @Test
    fun `duong lui khong dung vao tu tieng viet viet khong dau`() {
        listOf("pin", "cao", "trong", "xe", "ban", "che", "gian", "quy", "sau", "theo").forEach {
            assertEquals(it, say(it), "«$it» là tiếng Việt, không được phiên âm")
        }
    }

    // ── (6) CHUỖI HIỂN THỊ KHÔNG BỊ ĐỔI ─────────────────────────────────────────────────────────────────────

    /**
     * Phép đổi trả về một chuỗi **MỚI**; chuỗi mà tầng chữ / nhật ký / bảng clip đang cầm không suy suyển.
     *
     * Bảng clip đọc sẵn (spec `kachi-voice-clone.html`) khoá theo **chuỗi gốc**. Nếu phép đổi này chạy sớm hơn
     * một tầng, mọi lượt tra clip trượt — và trượt **im lặng**: máy vẫn đọc được, chỉ là đọc bằng Piper.
     */
    @Test
    fun `chuoi hien thi giu nguyen van sau khi da doc`() {
        val hienThi = "✓ Mở ứng dụng YouTube Music — Kachi đã mở, 257 km"
        val doc = say(hienThi)
        assertEquals("✓ Mở ứng dụng YouTube Music — Kachi đã mở, 257 km", hienThi, "chuỗi gốc bị đổi")
        assertTrue("Kachi" in hienThi && "YouTube Music" in hienThi, "chuỗi gốc phải còn nguyên chữ Latin")
        assertFalse("Kachi" in doc, "chuỗi đưa cho máy đọc phải đã đổi")
        assertTrue("Du-túp Mu-dích" in doc && "Ka-chi" in doc && "ki-lô-mét" in doc, doc)
    }

    /**
     * **Một** chỗ gọi, và nó nằm ở cửa ra tiếng — CLAUDE.md §8 (*"hàm mới phải có call site"*) cộng với vế ngược
     * lại: không được có call site thứ hai ở tầng dựng câu.
     */
    @Test
    fun `chi cua ra tieng goi phep doi nay`() {
        val router = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceSpeakerRouter.kt")
        assertTrue("TtsPronunciation.normalise(" in router, "cửa ra tiếng phải gọi phép đổi")
        val route = SourceRoots.body(router, "private fun route(")
        assertTrue("TtsPronunciation.normalise(" in route, "phép đổi phải nằm NGAY TRƯỚC speak, trong route()")

        listOf(
            "src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceReply.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceFeedbackPhrase.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceClarify.kt",
        ).forEach {
            assertFalse(
                "TtsPronunciation" in SourceRoots.codeOf(it),
                "$it dựng chuỗi cho MẮT — gọi phép đổi ở đây là làm hỏng tấm chữ + bảng tra clip",
            )
        }
    }

    // ── (7) OQ4 — *"nhé"* → *"nha"* ─────────────────────────────────────────────────────────────────────────

    /**
     * [ĐO `docs/specs/kachi-voice-clone.html` §4.6] giọng clone đọc *"nhé"* thành *"nhá"* ở **cả ba** lượt sinh
     * ⇒ không chữa được bằng seed. Owner chốt (OQ4): câu trả lời dùng *"nha"*.
     *
     * Bài này canh **chuỗi thật trong code**, không canh một hằng số bài tự khai: ngày ai đó viết một câu phản
     * hồi mới kết bằng *"nhé"*, nó đỏ ngay ở đây thay vì đỏ ở tai người lái.
     */
    @Test
    fun `khong cau tra loi nao ket bang nhe`() {
        listOf(
            "src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceReply.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceClarify.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/voice/VoicePhrases.kt",
        ).filter(SourceRoots::exists).forEach { rel ->
            val literals = Regex("\"([^\"\\\\]*)\"").findAll(SourceRoots.codeOf(rel)).map { it.groupValues[1] }
            literals.forEach { s ->
                assertFalse(
                    Regex("(^|[^\\p{L}])nhé($|[^\\p{L}])").containsMatchIn(s),
                    "$rel: chuỗi «$s» còn chữ “nhé” — giọng clone đọc ra “nhá” (OQ4), dùng “nha”",
                )
            }
        }
    }

    // ══ [SOÁT chuỗi-lời-đáp 2026-09-17] Hai chuỗi CÓ THẬT trong nhãn/đơn vị mà bảng còn hụt ═══════════════

    /** `°` TRẦN là đơn vị của 5 datum (`steering_deg` · `slope_deg` · 3 mục GPS) — không có `c` đi sau. */
    @Test
    fun `do tran doc ra chu do, va do C van thang nho luat dai nhat truoc`() {
        assertEquals("Góc vô-lăng 12 độ", TtsPronunciation.normalise("Góc vô-lăng 12°"))
        assertEquals("Nhiệt 24 độ", TtsPronunciation.normalise("Nhiệt 24°C"))
        assertEquals(
            TtsPronunciation.normalise("Góc 12°"),
            TtsPronunciation.normalise(TtsPronunciation.normalise("Góc 12°")),
            "luỹ đẳng — xem KDoc lớp",
        )
    }

    /** `cell` có trong nhãn của 5 datum pin; không có dòng bảng thì nó rơi xuống đường đánh vần. */
    @Test
    fun `cell doc ra mot tieng, khong danh van tung chu cai`() {
        val out = TtsPronunciation.normalise("Nhiệt cell cao")
        assertFalse(out.contains("e-lờ"), "«$out» — `cell` đang bị đánh vần từng chữ cái")
        assertEquals("Nhiệt xeo cao", out)
        assertEquals(out, TtsPronunciation.normalise(out), "luỹ đẳng")
    }

    private companion object {
        /** Sáu câu mẫu của lượt kiểm giọng (spec `kachi-voice-clone.html` T2) + câu *"chưa hiểu"*. */
        val SAMPLES = listOf(
            "✓ Mở ứng dụng YouTube Music",
            "Pin (SOC): 46 %",
            "Tầm hoạt động EV: 257 km",
            "✓ Đặt Nhiệt độ = 24 °C, tốc độ 60 km/h",
            "✓ Dẫn đường tới «Chợ Bến Thành» trên Google Maps",
            "Bụi mịn PM2.5: 12 µg/m³, Sức khỏe pin (SOH): 98 %",
            "Kachi chưa hiểu, anh nói lại giúp em nha",
        )
    }
}
