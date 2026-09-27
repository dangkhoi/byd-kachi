package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R7 (2.76) · TIỀN TỐ ≥ 4 KÝ TỰ + MỆNH ĐỀ Ô — ba cổng, mỗi cổng một bài ═════════════════════════════════════
 *
 * Chuỗi họ D là chuỗi **mô hình in ra trên xe** ([ĐO xe 2026-09-27 10:30:24 · 10:35:35 · 10:35:46 · 10:36:36],
 * `docs/diagnostics/offcar-2026-09-26/voice-car-0927.md` §1), chép nguyên văn. Bài đi qua `VoiceIntentParser.parseOne`
 * thật — không gọi [VoiceAppPrefix] trực tiếp — để khoá cả **vị trí** của nhánh (sau khớp chính xác và khớp mờ).
 */
class VoiceAppPrefixTest {

    /** Nhãn của chính xe owner ở buổi đo (cùng bộ với `VoiceCarWav0927Test`). */
    private val apps = listOf("YouTube", "YouTube Music", "Cài đặt", "Bản đồ")

    private fun one(text: String, apps: List<String> = this.apps) = VoiceIntentParser.parseOne(text, apps = apps)

    private fun openApp(text: String, apps: List<String> = this.apps): VoiceIntent.OpenApp =
        one(text, apps) as? VoiceIntent.OpenApp ?: error("không ra OpenApp: \"$text\" ⇒ ${one(text, apps)}")

    // ── Ca dương — đúng hai chuỗi của spec R7 ────────────────────────────────────────────────────

    @Test
    fun `viet vao o so hai mo VietMap vao o 2`() {
        // [ĐO xe 10:35:46] *"viet vào ô số hai"* — 2.75 ⇒ Unknown (cố ý). R7: tiền tố 4 ký tự, duy nhất, có mệnh đề ô.
        val vm = openApp("viet vào ô số hai")
        assertEquals("VietMap", vm.appName)
        assertEquals(2, vm.slot)
        assertEquals("vietmap", vm.appKey, "phải giữ mã bảng đích để `runOpenApp` tra được gói khi nhãn máy không trùng")
        // Có động từ vẫn đi cùng đường.
        assertEquals(2, openApp("mở viet vào ô số hai").slot)
    }

    @Test
    fun `yout vao o hai mo YouTube, du YouTube Music cung bat dau bang yout`() {
        // [ĐO xe 10:36:36] *"có yout vào ô số một"*. Hai nhãn khớp lồng nhau theo từ (*YouTube* ⊂ *YouTube Music*)
        // ⇒ nhãn ngắn hơn thắng: người nói đã rụng đuôi của chính từ *youtube*, không phải bỏ cả từ *music*.
        val yt = openApp("yout vào ô hai")
        assertEquals("YouTube", yt.appName)
        assertEquals(2, yt.slot)
        assertEquals(1, openApp("có yout vào ô số một").slot, "một từ lạ đứng trước (VoiceSlotNoVerb bỏ qua) vẫn tới được")
    }

    // ── Cổng 2 — tiền tố quá ngắn ────────────────────────────────────────────────────────────────

    @Test
    fun `mat va ap duoi 4 ky tu thi giu Unknown de hoi lai`() {
        // [ĐO xe 10:30:24 · 10:35:23 · 10:35:35] — hai âm tiết này không còn liên hệ gì tới một cái tên.
        listOf("mát vào ô số một", "mát vào ô số hai", "áp vào ô số một").forEach {
            assertTrue(one(it) is VoiceIntent.Unknown, "\"$it\" phải giữ Unknown: ${one(it)}")
        }
    }

    // ── Cổng 1 — phải có mệnh đề ô CÓ SỐ ─────────────────────────────────────────────────────────

    @Test
    fun `tien to ma khong co menh de o thi khong mo gi`() {
        assertTrue(one("mở viet") is VoiceIntent.Unknown, "tiền tố trần không đủ bằng chứng để mở app")
        assertTrue(one("viet") is VoiceIntent.Unknown)
        assertTrue(one("mở viet vào ô") is VoiceIntent.Unknown, "mệnh đề ô THIẾU SỐ không mở khe này")
        assertTrue(one("mở yout hai") is VoiceIntent.Unknown, "con số trần không phải mệnh đề ô đủ cho một tiền tố")
    }

    // ── Cổng 3 — duy nhất theo nhãn ──────────────────────────────────────────────────────────────

    @Test
    fun `hai nhan khong long nhau cung tien to thi Unknown`() {
        val two = apps + listOf("Netflix", "Netflax")
        assertTrue(one("netf vào ô số một", two) is VoiceIntent.Unknown, "nhập nhằng thật ⇒ hỏi lại, không đoán")
        // …và khi chỉ còn một nhãn thì mở được — chứng minh cổng 3 là thứ chặn, không phải cổng nào khác.
        assertEquals("Netflix", openApp("netf vào ô số một", apps + "Netflix").appName)
    }

    @Test
    fun `dong tu dong khong mo app`() {
        assertTrue(one("đóng viet vào ô số hai") !is VoiceIntent.OpenApp, "động từ đóng không được mở app qua khe này")
    }

    // ── Không đổi gì đã chạy ─────────────────────────────────────────────────────────────────────

    @Test
    fun `cau du ten van di duong cu`() {
        assertEquals(VoiceIntent.OpenApp("YouTube", 2), one("mở youtube vào ô số hai"))
        assertEquals(2, openApp("mở vietmap vào ô số hai").slot)
        assertEquals(2, openApp("mở vietma vào ô số hai").slot, "khớp mờ (VoiceNameFuzzy) vẫn đứng trước tiền tố")
    }
}
