package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VOICE-OPEN-TURN · bài kiểm dựng từ **chuỗi THẬT của xe** 2026-09-26 ═════════════════════════════════════
 *
 * Mọi chuỗi ở `mo vietmap …` / `chuyen sang ho so` dưới đây là **nguyên văn** chuỗi mà mô hình in ra khi phát lại
 * 30 bản thu của buổi xe 26/09 trên máy ảo (`docs/diagnostics/offcar-2026-09-26/voice-tail-fuzzy-phonetic.md`
 * §1.2 · §3.1) — không phải câu dựng tay. Bài này khoá hai vế:
 *  • câu **còn dở** thì chờ thêm ([VoiceOpenTurn.isOpen] = `true`);
 *  • câu **đủ nghĩa** thì tuyệt đối không (nếu không, mỗi lệnh xe phải chờ thêm 1,2 s — xem
 *    [VoiceOpenTurnCasesTest] quét cả bộ ca E2E).
 */
class VoiceOpenTurnTest {

    // ── (a) VẾ DỞ — chuỗi thật của xe 26/09 ─────────────────────────────────────────────────────

    @Test
    fun `menh de o mat con so la ve do`() {
        // [ĐO §1.2] hai chuỗi này là kết quả giải mã THẬT của khúc đầu bản thu …-184201 / …-182614.
        listOf("mở vietmap vào ô", "mở vietmap vào ô số", "đưa youtube vào ô", "mở youtube ô số").forEach {
            assertTrue(VoiceOpenTurn.isOpen(it), "\"$it\" thiếu con số ô ⇒ phải chờ vế sau")
        }
    }

    @Test
    fun `cum danh dau ho so dung cuoi cau la ve do`() {
        // [ĐO §3.1] 8/8 lượt của xe rụng đúng cái tên, cho ra hai chuỗi này.
        listOf("chuyển sang hồ sơ", "đổi sang hồ sơ", "hồ sơ").forEach {
            assertTrue(VoiceOpenTurn.isOpen(it), "\"$it\" thiếu tên hồ sơ ⇒ phải chờ vế sau")
        }
    }

    @Test
    fun `cum dan duong mat diem den la ve do`() {
        listOf("dẫn đường tới", "dẫn đường đến", "chỉ đường đến", "đi tới").forEach {
            assertTrue(VoiceOpenTurn.isOpen(it), "\"$it\" thiếu điểm đến ⇒ phải chờ vế sau")
        }
    }

    @Test
    fun `cum chon app mat ten app la ve do, nhung chi khi ve truoc gan duoc`() {
        assertTrue(VoiceOpenTurn.isOpen("phát nhạc bằng"), "vế trước là một lệnh nhạc thật ⇒ *bằng* đang chờ tên app")
        // Cổng tiền tố: `quá` bỏ dấu ra `qua`, trùng một cụm đánh dấu chọn app (`VoiceLexicon.BY_APP_MARKERS`).
        assertFalse(VoiceOpenTurn.isOpen("hôm nay trời đẹp quá"), "câu tán gẫu không được biến thành câu dở")
    }

    @Test
    fun `dong tu tran la ve do, dong tu tron ven thi khong`() {
        listOf("mở", "bật", "đóng").forEach { assertTrue(VoiceOpenTurn.isOpen(it), "\"$it\" chưa có đối tượng") }
        // *"tạm dừng"* cũng là một cụm động từ trọn vẹn của `VoiceGrammar.VERBS`, nhưng nó LÀ một câu lệnh đủ.
        assertFalse(VoiceOpenTurn.isOpen("tạm dừng"), "tạm dừng là lệnh PAUSE đủ nghĩa — không được chờ thêm")
    }

    // ── (b) CÂU ĐỦ NGHĨA — không được thêm một mili-giây nào ─────────────────────────────────────

    @Test
    fun `cau du nghia khong bao gio cho them`() {
        // Bảy chuỗi: bốn chuỗi THẬT của xe 26/09 (§1.5) + ba lớp lệnh hay dùng nhất.
        listOf(
            "mở vietmap",            // [ĐO §1.5] bản thu …-182920, câu không nêu ô — đúng là một lệnh đủ
            "mở vietmap một",        // [ĐO §1.5] chuỗi TRƯỚC bản vá hotword: đã có con số ⇒ đóng lượt
            "mở vietmap vào ô số một",
            "mở youtube vào ô hai",
            "bật gió tự động",
            "mấy giờ rồi",
            "phát nhạc",
        ).forEach { assertFalse(VoiceOpenTurn.isOpen(it), "\"$it\" đủ nghĩa ⇒ phải đóng lượt NGAY") }
    }

    @Test
    fun `khong nghe thay gi thi khong cho`() {
        listOf("", "   ", "ừ", "ừm ạ").forEach {
            assertFalse(VoiceOpenTurn.isOpen(it), "\"$it\" là ca im lặng — đã có đường riêng, không giữ micro")
        }
    }

    // ── (c) GHÉP — vế sau đi qua **bộ phân tích thật**, không qua một phép kiểm chuỗi ────────────

    @Test
    fun `ghep ve sau roi phan tich ra dung o`() {
        val joined = VoiceOpenTurn.join("mở vietmap vào ô", "số hai")
        assertEquals("mở vietmap vào ô số hai", joined)
        val intent = VoiceIntentParser.parseOne(joined, apps = listOf("VietMap"))
        assertEquals(VoiceIntent.OpenApp("VietMap", slot = 2), intent, "câu ghép phải ra đúng app + đúng ô")
    }

    @Test
    fun `ve sau nhac lai cum thi khong doc menh de o hai lan`() {
        // [ĐO §1.2] khúc `[1400..3400]` của bản thu …-184201 ra *"ô số một"*, khúc `[900..2600]` ra *"… vào ô số một"*.
        assertEquals("mở vietmap vào ô số một", VoiceOpenTurn.join("mở vietmap vào ô", "vào ô số một"))
        assertEquals("mở vietmap ô số một", VoiceOpenTurn.join("mở vietmap ô số", "ô số một"))
        val intent = VoiceIntentParser.parseOne(
            VoiceOpenTurn.join("mở vietmap vào ô", "vào ô số một"), apps = listOf("VietMap"),
        )
        assertEquals(VoiceIntent.OpenApp("VietMap", slot = 1), intent)
    }

    @Test
    fun `ghep ten ho so roi phan tich ra dung ho so`() {
        val joined = VoiceOpenTurn.join("chuyển sang hồ sơ", "test")
        assertEquals("chuyển sang hồ sơ test", joined)
        assertEquals(VoiceIntent.Profile("Test"), VoiceIntentParser.parseOne(joined, profiles = listOf("Test")))
    }

    @Test
    fun `khong co ve sau thi giu nguyen ve truoc`() {
        assertEquals("mở vietmap vào ô", VoiceOpenTurn.join("mở vietmap vào ô", ""))
        assertEquals("mở vietmap vào ô", VoiceOpenTurn.join("mở vietmap vào ô", "  ừ  "))
        // Vế sau là bản giải mã LẠI của cùng khúc tiếng ⇒ giữ một bản, không nhân đôi câu.
        assertEquals("mở vietmap vào ô", VoiceOpenTurn.join("mở vietmap vào ô", "mở vietmap vào ô"))
    }

    @Test
    fun `bo tu dem o dau ve sau`() {
        assertEquals("mở vietmap vào ô số hai", VoiceOpenTurn.join("mở vietmap vào ô", "ừ số hai"))
    }

    // ── (d) Hai con số thời gian — bài kiểm đọc được, không rải rác trong `:app` ─────────────────

    @Test
    fun `cua so ghep phu het nhom ngung de nghi da do`() {
        // [ĐO §1.4] bốn quãng ngừng-để-nghĩ: 720 · 820 · 820 · 1 060 ms, tính từ lúc HẾT TIẾNG.
        val thinkPauses = listOf(720, 820, 820, 1_060)
        val afterEndpoint = thinkPauses.map { it - VoiceVadTrim.MIN_SILENCE_MS }
        assertEquals(listOf(120, 220, 220, 460), afterEndpoint, "điểm ngắt nổ sau 600 ms im lặng")
        afterEndpoint.forEach {
            assertTrue(
                it < VoiceOpenTurn.OPEN_JOIN_WINDOW_MS,
                "vế sau bắt đầu ở +$it ms sau điểm ngắt — cửa sổ ${VoiceOpenTurn.OPEN_JOIN_WINDOW_MS} ms phải phủ",
            )
        }
        assertTrue(
            VoiceOpenTurn.OPEN_MAX_EXTRA_MS > VoiceOpenTurn.OPEN_JOIN_WINDOW_MS,
            "trần cứng phải rộng hơn cửa sổ, nếu không khối đọc cuối của cửa sổ rơi ngoài trần",
        )
        assertTrue(VoiceOpenTurn.OPEN_MAX_EXTRA_MS <= 1_500L, "câu bị bỏ giữa phải ra kết quả trong ≤ 1,5 s thêm")
    }

    // ── (e) Khúc mẫu của vế sau — số học thuần ───────────────────────────────────────────────────

    @Test
    fun `khuc ve sau lay tu dau doan, khong lay tu diem ngat`() {
        // Bản thu …-184201 quy về mẫu @16 kHz: vế trước [0..2040] ms, vế sau [2760..4400] ms, cửa sổ 5 000 ms.
        val a = VoiceVadTrim.Segment(startSample = 3_200, lengthSamples = 29_440)     // 200 → 2 040 ms
        val b = VoiceVadTrim.Segment(startSample = 44_160, lengthSamples = 26_240)    // 2 760 → 4 400 ms
        val window = 80_000
        val range = VoiceVadTrim.tailRange(listOf(a, b), fromIndex = 1, windowSamples = window)
        assertEquals(44_160 until 70_400, range, "phải bắt đầu ở mốc đoạn, không kéo theo quãng ngừng")
        assertEquals(null, VoiceVadTrim.tailRange(listOf(a), fromIndex = 1, windowSamples = window), "chưa có vế sau")
        assertEquals(null, VoiceVadTrim.tailRange(listOf(a, b), fromIndex = 1, windowSamples = 0), "cửa sổ rỗng")
        // Kẹp theo cửa sổ: đoạn dài hơn phần đã thu (chốt bằng `flush` lúc chạm trần) không đọc ra ngoài vùng.
        assertEquals(44_160 until 50_000, VoiceVadTrim.tailRange(listOf(a, b), 1, 50_000))
    }
}
