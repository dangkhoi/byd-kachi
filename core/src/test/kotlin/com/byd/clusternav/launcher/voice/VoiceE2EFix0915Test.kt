package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KHOÁ BA LỖI ĐÃ ĐO Ở LƯỢT E2E MÁY ẢO 2026-09-15 ══════════════════════════════════════════════════════════
 *
 * Nguồn: `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 — **L1** (đóng app lại MỞ app), **L5** (vế câu
 * ghép bị bỏ im lặng), **L6** (tên app là nhãn hệ thống ⇒ *"mở bản đồ"* câm). Câu trong bài lấy **nguyên văn**
 * từ `scripts/emulator/voice-cases.tsv` (t41 · t42 · t45) và từ bốn phép đo tay ở §2 của tài liệu, để lượt chạy
 * lại trên máy ảo và bài canh off-car nói về **cùng một câu**.
 *
 * Mỗi ca đều kèm ca đối chứng *"thứ đang chạy tốt phải giữ nguyên"* (CLAUDE.md §6): `đóng hết kính` vẫn là gói
 * lệnh, `đóng kính trước trái` vẫn là nút, `mở YouTube` vẫn mở app, câu ghép hiểu được cả hai vế vẫn ra 2 ý định.
 */
class VoiceE2EFix0915Test {

    private val apps = listOf("YouTube", "Maps")

    private fun one(text: String, apps: List<String> = this.apps): VoiceIntent =
        VoiceIntentParser.parseOne(text, apps = apps)

    // ══ L1 — *"đóng/tắt <app>"* KHÔNG được mở app đó ═════════════════════════════════════════════════════

    @Test
    fun `dong hoac tat mot app thi noi thang la chua lam duoc`() {
        listOf("đóng YouTube", "tắt YouTube", "dừng YouTube", "đóng ứng dụng YouTube").forEach { say ->
            val got = one(say)
            assertTrue(
                got is VoiceIntent.Unknown && got.reason == VoiceUnknownReason.APP_CLOSE,
                "«$say» phải nói là chưa đóng được app; nhận được: $got",
            )
        }
    }

    @Test
    fun `cau tra loi cho viec dong app khong duoc hua hen gi`() {
        val reply = VoiceReply.unknown(
            VoiceIntent.Unknown(VoiceUnknownReason.APP_CLOSE, "đóng YouTube"),
        )
        assertTrue(reply.contains("Chưa đóng được app"), "phải nói thẳng là chưa làm được; nhận được: $reply")
        assertTrue(reply.contains("đóng YouTube"), "phải đọc lại câu gốc để người nói biết máy nghe ra gì")
    }

    @Test
    fun `nhung thu dang chay tot voi dong tu DONG giu nguyen`() {
        assertEquals(VoiceIntent.Macro("mac_win_close_all"), one("đóng hết kính"))
        assertEquals(VoiceIntent.Control("win_lf", 0), one("đóng kính trước trái"))
        assertEquals(VoiceIntent.OpenApp("YouTube"), one("mở YouTube"))
        assertEquals(VoiceIntent.Media(VoiceMediaOp.PAUSE), one("dừng nhạc"))
    }

    // ══ L5 — vế câu ghép không hiểu được phải được NÓI RA ════════════════════════════════════════════════

    @Test
    fun `ve cau ghep bi bo thi noi ra, khong im lang`() {
        val got = VoiceIntentParser.parse("mở cửa và đèn đọc", apps = apps)
        assertEquals(2, got.size, "phải có thêm một dòng nói về vế bị bỏ; nhận được: $got")
        assertEquals(VoiceIntent.Control("readl", 1), got[0])
        val note = got[1]
        assertTrue(
            note is VoiceIntent.Unknown && note.reason == VoiceUnknownReason.DROPPED_CLAUSE,
            "dòng thứ hai phải là dòng *đã bỏ qua*; nhận được: $note",
        )
        assertTrue((note as VoiceIntent.Unknown).text.contains("mở cửa"), "phải nói RÕ vế nào bị bỏ: ${note.text}")
    }

    @Test
    fun `ten bai hat co chu VA thi khong bi bao la bo qua`() {
        // [ĐO] mẫu câu Kiki #10 — chữ *"và"* nằm TRONG tên bài; vế sau không bị bỏ, nó được dùng.
        val got = VoiceIntentParser.parse("Mở bài Cỏ dại và hoa dành dành", apps = apps)
        assertEquals(1, got.size, "không có gì bị bỏ mà lại báo; nhận được: $got")
        assertEquals(VoiceIntent.Media(VoiceMediaOp.QUERY, "Cỏ dại và hoa dành dành"), got[0])
    }

    @Test
    fun `cau ghep hieu duoc ca hai ve van ra dung hai y dinh`() {
        // t61 của `voice-cases.tsv`.
        val got = VoiceIntentParser.parse("bật đèn đọc và tắt lọc bụi", apps = apps)
        assertEquals(listOf(VoiceIntent.Control("readl", 1), VoiceIntent.Control("pm25", 0)), got)
    }

    @Test
    fun `ca cau khong hieu thi chi bao MOT lan`() {
        val got = VoiceIntentParser.parse("abcxyz và qwerty", apps = apps)
        assertEquals(1, got.size, "đã có câu báo rồi, thêm dòng thứ hai là nói hai lần một việc; nhận được: $got")
    }

    // ══ L6 — gọi app bằng CÁCH NÓI TIẾNG VIỆT khi nhãn hệ thống là tiếng Anh ═════════════════════════════

    @Test
    fun `mo ban do chay tren may co nhan tieng Anh`() {
        // t45: máy ảo có Google Maps với nhãn *"Maps"* ⇒ trước 1.64 câu này ra Unknown.
        val got = one("mở bản đồ")
        assertTrue(got is VoiceIntent.OpenApp, "nhận được: $got")
        got as VoiceIntent.OpenApp
        assertEquals(VoiceAppTargets.GMAPS, got.appKey, "phải mang MÃ đích để tầng thi hành tra tên gói")
        assertEquals("Google Maps", got.appName, "câu trả lời đọc nhãn của bảng đích, không đọc mã")
    }

    @Test
    fun `nhan that van thang cach noi cua bang dich`() {
        val got = one("mở Maps")
        assertEquals(VoiceIntent.OpenApp("Maps"), got, "nhãn app đã cài phải được ưu tiên; nhận được: $got")
    }

    @Test
    fun `menh de vao o van con nguyen khi goi app bang cach noi`() {
        val got = one("mở bản đồ vào ô số 2") as VoiceIntent.OpenApp
        assertEquals(2, got.slot)
        assertEquals(VoiceAppTargets.GMAPS, got.appKey)
    }

    @Test
    fun `app nav khac cung goi duoc bang cach noi Viet`() {
        val got = one("mở viet map", apps = emptyList()) as VoiceIntent.OpenApp
        assertEquals(VoiceAppTargets.VIETMAP, got.appKey)
        assertEquals("VietMap", got.appName)
    }

    @Test
    fun `cach hieu CO NGHIA van thang cach goi app`() {
        // *"mở nhạc"* là lệnh NHẠC (từ khoá `nhac` trong từ vựng), không phải *"mở một app tên nhạc"*.
        assertEquals(VoiceIntent.Media(VoiceMediaOp.PLAY), one("mở nhạc", apps = emptyList()))
        // Và nhãn registry vẫn thắng: *"mở kính bên lái"* là nút, dù câu bắt đầu bằng cùng động từ.
        assertEquals(VoiceIntent.Control("win_lf", 1), one("mở kính bên lái", apps = emptyList()))
    }

    @Test
    fun `dong mot app goi bang cach noi cung khong duoc mo no`() {
        val got = one("đóng bản đồ")
        assertTrue(
            got is VoiceIntent.Unknown && got.reason == VoiceUnknownReason.APP_CLOSE,
            "nhận được: $got",
        )
    }

    @Test
    fun `cach noi cua bang dich deu tra nguoc ra dung mot dich`() {
        // Bảo đảm nhánh mới không đọc một bảng rỗng: mọi đích phải có ít nhất một cách nói tra được.
        VoiceAppTargets.ALL.forEach { t ->
            assertNotNull(
                t.spoken.firstNotNullOfOrNull { s -> VoiceAppTargets.bySpoken(VoiceLexicon.tokenize(s).map { it.norm }) },
                "đích `${t.key}` không tra ngược được từ cách nói nào",
            )
        }
    }
}
