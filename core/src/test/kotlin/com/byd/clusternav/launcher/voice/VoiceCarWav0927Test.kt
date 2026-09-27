package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BUỔI XE 27/09 — khoá bằng ĐÚNG chuỗi mà mô hình đã in ra trên xe ════════════════════════════════════════
 *
 * Nguồn: nhật ký thật của xe (Seal, 2.74 (175), `KachiVoiceSession` 10:30–10:45, `nguong_im=800ms`, nguồn mic
 * `MIC (1)`). Chuỗi trong bài này là chuỗi **mô hình in ra**, chép nguyên văn — không phải câu người ta nói. Bảng
 * từng lượt + cách đo: `docs/diagnostics/offcar-2026-09-26/voice-car-0927.md`.
 *
 * [ĐO] 22 lượt owner nói *"mở &lt;app&gt; vào ô số N"* ⇒ **10 lượt hiểu sai**. Bốn họ, và bài này khoá cả bốn
 * (kể cả hai họ mà bản 2.75 **cố ý không** chữa — một bài canh cho *"chưa chữa"* cũng là một bài canh, nó bắt được
 * ngày ai đó nới phép khớp mờ rồi vô tình mở app cho một câu không có tên app):
 *
 * | họ | chuỗi thật | 2.74 | 2.75 |
 * |---|---|---|---|
 * | rụng **động từ** đầu câu | *"vietmap vào ô số một"* | `NO_VERB` | `OpenApp(VietMap → ô 1)` ✅ |
 * | động từ nghe **nhầm** | *"bỏ youtube vào ô số một"* | `NO_VERB` | `OpenApp(YouTube → ô 1)` ✅ |
 * | rụng chữ **"vào ô"**, còn con số | *"mở vietmap hai"* | `OpenApp(VietMap)` — mất ô | `OpenApp(VietMap → ô 2)` ✅ |
 * | rụng **tên app** | *"mát vào ô số hai"* · *"áp vào ô số một"* · *"viet vào ô số hai"* | `NO_VERB` | `NO_VERB` (giữ — xem `ten app rung thi KHONG duoc doan`) |
 */
class VoiceCarWav0927Test {

    /** Nhãn app của **chính xe owner** ở buổi đo (`VietMap` đến từ bảng đích, không phải nhãn máy). */
    private val apps = listOf("YouTube", "YouTube Music", "Cài đặt", "Bản đồ")

    private fun one(text: String, apps: List<String> = this.apps) =
        VoiceIntentParser.parseOne(text, apps = apps)

    private fun openApp(text: String): VoiceIntent.OpenApp =
        one(text) as? VoiceIntent.OpenApp ?: error("không ra OpenApp: \"$text\" ⇒ ${one(text)}")

    // ── Họ 1 — RỤNG ĐỘNG TỪ: mệnh đề ô tự nó đã là động từ ([VoiceSlotNoVerb]) ────────────────────

    @Test
    fun `cau khong con dong tu nhung con menh de o van mo dung app dung o`() {
        // [ĐO xe 10:31:30] chuỗi thật; 2.74 ⇒ `không hiểu: NO_VERB`.
        val vm = openApp("vietmap vào ô số một")
        assertEquals("VietMap", vm.appName)
        assertEquals(1, vm.slot)
        // Cùng họ, app khác + cách nói *"vào ô N"* (không chữ *"số"*).
        assertEquals(VoiceIntent.OpenApp("YouTube", 1, appKey = "youtube"), one("youtube vào ô số một"))
        assertEquals(VoiceIntent.OpenApp("YouTube", 2, appKey = "youtube"), one("youtube vào ô hai"))
    }

    @Test
    fun `ten app tran KHONG duoc mo`() {
        // Cổng 1 của [VoiceSlotNoVerb]: không có mệnh đề ô ⇒ một cái tên đứng một mình KHÔNG là một lệnh.
        // Bỏ cổng này là biến mọi câu chỉ chứa một tên app (*"vietmap"* lọt vào từ một mẩu hội thoại) thành lệnh mở.
        assertTrue(one("vietmap") is VoiceIntent.Unknown, "tên app trần không được mở app")
        assertTrue(one("youtube") is VoiceIntent.Unknown)
        assertTrue(one("vietmap vào ô") is VoiceIntent.Unknown, "mệnh đề ô THIẾU SỐ không được nhận")
        assertTrue(one("vietmap vào ô số") is VoiceIntent.Unknown)
    }

    @Test
    fun `menh de o tran KHONG duoc mo app nao - do la cau tra loi cua vong hoi lai`() {
        // Không có tên app ở đầu ⇒ `null`. Đây là câu trả lời cho *"Ô nào?"*; [VoiceClarify] mang vế trước theo rồi
        // ghép lại. Nhận nó ở đây là cướp đường của vòng hỏi-đáp và mở **app gần nhất trong bảng đích**.
        assertTrue(one("vào ô số một") is VoiceIntent.Unknown)
        assertTrue(one("ô số hai") is VoiceIntent.Unknown)
    }

    // ── Họ 2 — ĐỘNG TỪ NGHE NHẦM: một từ lạ trước tên app ────────────────────────────────────────

    @Test
    fun `mot tu la dung truoc ten app khong lam mat menh de o`() {
        // [ĐO xe 10:36:27] owner nói *"đặt"*, mô hình in ra *"bỏ"*. Tiếng Việt cũng nói *"cho X vào Y"*.
        assertEquals(VoiceIntent.OpenApp("YouTube", 1, appKey = "youtube"), one("bỏ youtube vào ô số một"))
        assertEquals(VoiceIntent.OpenApp("YouTube", 1, appKey = "youtube"), one("cho youtube vào ô số một"))
        val vm = openApp("bỏ vietmap vào ô số hai")
        assertEquals("VietMap", vm.appName)
        assertEquals(2, vm.slot)
    }

    @Test
    fun `chi bo qua DUNG MOT tu, khong quet mu ca cau`() {
        // Hai từ lạ ⇒ không nhận: đó không còn là *"một động từ nghe nhầm"* mà là một câu khác.
        assertTrue(one("hôm nay youtube vào ô số một") is VoiceIntent.Unknown)
    }

    @Test
    fun `chu BO van la BO CUC - cau bo cuc KHONG duoc bien thanh lenh mo app`() {
        // ⚠ Đây là lý do [VoiceSlotNoVerb] bỏ-qua-một-từ thay vì thêm `bo` vào [VoiceGrammar.VERBS]: *"bố cục"* bỏ
        // dấu ra `bo cuc`, nên một động từ `bo` ở vị trí 0 sẽ làm MỌI câu bố cục không bao giờ tới
        // [VoiceLayouts.match]. Bài này là cái chốt của điều đó.
        assertTrue(one("bố cục 2 cột") is VoiceIntent.Layout, "câu bố cục bị đổi nghĩa: ${one("bố cục 2 cột")}")
        assertTrue(one("đổi sang bố cục 4 ô") is VoiceIntent.Layout)
    }

    // ── Họ 3 — RỤNG CHỮ "VÀO Ô", CÒN CON SỐ ([VoiceTailClause] `bareSlot`) ───────────────────────

    @Test
    fun `con so tran ngay sau ten app la so O`() {
        // [ĐO xe 10:36:52] *"mở vietmap hai"* — cửa sổ chạm trần 8 s với 6 012 ms im lặng dẫn đầu; 2.74 mở VietMap
        // vào ô CŨ và không ai biết vì sao.
        val vm = openApp("mở vietmap hai")
        assertEquals("VietMap", vm.appName)
        assertEquals(2, vm.slot)
        // *"số hai"* (rụng đúng chữ *"ô"*) — dạng mà [VoiceOpenTurn.join] sinh ra khi vế sau là *"số hai"*.
        assertEquals(2, openApp("mở vietmap số hai").slot)
        assertEquals(2, openApp("mở youtube thứ hai").slot)
        assertEquals(VoiceIntent.OpenApp("YouTube", 2), one("mở youtube hai"))
    }

    @Test
    fun `con so tran chi duoc nhan khi la TOAN BO phan duoi va tro toi mot o co that`() {
        // Cổng 1 — còn từ khác ở đuôi ⇒ không phải mệnh đề ô.
        assertNull(openApp("mở youtube tập hai").slot, "*tập hai* không phải số ô")
        // Cổng 2 — vượt số ô lớn nhất của mọi bố cục ⇒ chứng cứ quá yếu để nhận.
        assertNull(openApp("mở youtube hai mươi").slot)
        assertNull(openApp("mở youtube chín").slot)
        // Mệnh đề ô ĐẦY ĐỦ thì vẫn KHÔNG bị kẹp (hợp đồng cũ của [VoiceIntent.OpenApp.slot] — chỗ gọi mới nói
        // *"bố cục hiện chỉ có N ô"*).
        assertEquals(9, openApp("mở youtube vào ô số chín").slot)
    }

    @Test
    fun `cau khong co ten app thi con so tran khong sinh ra o nao`() {
        // Đường lùi này chỉ tới được các nhánh `OpenApp`; lệnh xe / datum / nhạc không có mệnh đề ô.
        assertTrue(one("bật đèn đọc") is VoiceIntent.Control)
        assertTrue(one("chỉnh gió mức hai") is VoiceIntent.Control)
        assertTrue(one("sưởi ghế lái mức hai") is VoiceIntent.Control)
    }

    // ── Họ 4 — RỤNG TÊN APP: cố ý KHÔNG chữa ────────────────────────────────────────────────────

    @Test
    fun `ten app rung thi KHONG duoc doan`() {
        // [ĐO xe 10:30:24 · 10:35:23 · 10:35:35 · 10:35:46 · 10:36:36] năm lượt: *"áp"* · *"mát"* ×2 · *"viet"* ·
        // *"có yout"*. Không còn đủ chữ để biết app nào ⇒ phải giữ *"không hiểu"* để [VoiceClarify] hỏi lại. Mở
        // *"app gần nhất"* cho một câu như thế là mở NHẦM app — tệ hơn hẳn không hiểu (KDoc [VoiceLastResort] cổng 3).
        listOf(
            "áp vào ô số một", "mát vào ô số hai", "viet vào ô số hai", "có yout vào ô số một",
        ).forEach { assertTrue(one(it) is VoiceIntent.Unknown, "\"$it\" không được đoán ra app: ${one(it)}") }
    }

    // ── Vẫn đúng: mọi lượt 27/09 đã chạy ĐÚNG không được đổi ────────────────────────────────────

    @Test
    fun `chin luot da dung cua buoi xe 2709 khong doi`() {
        // Chép từ JSON đi kèm bản thu + nhật ký: đây là cái lưới chống hồi quy của cả ba bản vá trên.
        assertEquals(2, openApp("mở vietmap vào ô số hai").slot)
        assertEquals(1, openApp("mở vietmap vào ô số một").slot)
        assertEquals(1, openApp("mở youtube vào ô số một").slot)
        assertEquals(2, openApp("mở youtube vào ô số hai").slot)
        assertEquals(2, openApp("mở vietmap ô số hai").slot)
        assertEquals(2, openApp("mở vietma vào ô số hai").slot, "khớp mờ *vietma* + ô 2")
        assertNull(openApp("mở vietmap").slot, "câu không nêu ô ⇒ không được tự sinh ra một ô")
        assertTrue(one("bật gió tự động") is VoiceIntent.Control)
        assertTrue(one("bật gió mức hai") is VoiceIntent.Control)
    }

    // ── Lẫn âm `sưởi` → `chửi` ([VoicePhoneticConfusions]) ──────────────────────────────────────

    @Test
    fun `chui ghe lai la SUOI ghe lai`() {
        // [ĐO xe 10:44:02 · 10:44:11] hai lượt liền ra *"chửi ghế lái"* ⇒ NO_VERB; lượt thứ ba mô hình in đúng
        // *"sưởi ghế lái"* và câu chạy (`Control(seath=1)`) ⇒ sai ở tầng NGHE, không ở câu nói.
        assertEquals(one("sưởi ghế lái"), one("chửi ghế lái"))
        assertTrue(one("chửi ghế lái") is VoiceIntent.Control)
    }

    // ── Vế DỞ mà xe đã gặp: tầng chữ nhận ra ⇒ lượt nghe phải chờ ───────────────────────────────

    @Test
    fun `ve do cua buoi xe 2709 duoc nhan ra o tang chu`() {
        // [ĐO xe 10:43:11] *"mở vietmap vào ô số"* — tầng chữ **đã** biết đây là vế dở từ 2.73; cái hụt là lượt NỐI
        // không bật `openTurn` (vá ở `VoiceSessionTurns.listenOnce`, 2.75). Bài này khoá nửa `:core` của vế đó.
        assertTrue(VoiceOpenTurn.isOpen("mở vietmap vào ô số"))
        assertTrue(VoiceOpenTurn.isOpen("mở vietmap vào ô"))
        // …và câu ĐỦ thì không được coi là dở (nếu không thì mọi lượt nói đều cộng thêm một cửa sổ chờ).
        assertTrue(!VoiceOpenTurn.isOpen("mở vietmap vào ô số hai"))
        assertTrue(!VoiceOpenTurn.isOpen("mở vietmap hai"))
    }
}
