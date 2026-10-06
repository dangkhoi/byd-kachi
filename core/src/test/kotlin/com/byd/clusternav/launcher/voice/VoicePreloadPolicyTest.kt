package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá bài học H6 (PERF 2026-09-16): nạp sẵn mô hình là một phép ĐỔI CHÁC (RAM lấy 15 s chờ), nên nó phải hỏi
 * xem còn RAM không — và phải hỏi theo **cỡ gói đang chọn**, không theo một con số cứng.
 */
class VoicePreloadPolicyTest {

    private val mb = 1024L * 1024L

    @Test
    fun `xe chat RAM thi khong nap san goi fp32`() {
        // [ĐO xe 2026-09-16]: còn 60 MB trống, gói fp32 266 MB.
        assertFalse(VoicePreloadPolicy.shouldPreload(60 * mb, lowMemory = false, modelBytes = 266 * mb))
    }

    @Test
    fun `he thong bao thieu thi khong nap du goi nho`() {
        assertFalse(VoicePreloadPolicy.shouldPreload(500 * mb, lowMemory = true, modelBytes = 74 * mb))
    }

    @Test
    fun `nguong theo CO GOI chu khong phai so cung`() {
        // Cùng 200 MB trống: gói int8 (74 + thở 111 = 185 MB) nạp được, gói fp32 (266 + 399) thì không. Một ngưỡng
        // cứng "còn < 200 MB thì thôi" sẽ chặn nhầm cả hai. (180 → 200 ở 2026-09-25 khi thở đổi sang 1,5 × gói.)
        assertTrue(VoicePreloadPolicy.shouldPreload(200 * mb, lowMemory = false, modelBytes = 74 * mb))
        assertFalse(VoicePreloadPolicy.shouldPreload(200 * mb, lowMemory = false, modelBytes = 266 * mb))
    }

    // ── 2026-09-25 · wake: thở theo đỉnh transient lúc nạp (audit RAM §4.2) ────────────────────────────────

    @Test
    fun `tho bang 1,5 lan goi - int8 74 MB can 111, fp32 266 can 399, goi 0 giu san 96`() {
        assertEquals(111 * mb, VoicePreloadPolicy.headroomBytes(74 * mb))
        assertEquals(399 * mb, VoicePreloadPolicy.headroomBytes(266 * mb))
        assertEquals(VoicePreloadPolicy.HEADROOM_BYTES, VoicePreloadPolicy.headroomBytes(0))
        // Gói nhỏ (< 64 MB) không được tụt dưới sàn 96 MB.
        assertEquals(VoicePreloadPolicy.HEADROOM_BYTES, VoicePreloadPolicy.headroomBytes(40 * mb))
    }

    @Test
    fun `dinh transient - 74 MB goi tren may con 180 MB thi KHONG nap nua`() {
        // Trước 2026-09-25: 74 + 96 = 170 ≤ 180 ⇒ nạp ⇒ đỉnh +71…+126 MB chạm LMK dù policy cho qua.
        assertFalse(VoicePreloadPolicy.shouldPreload(180 * mb, lowMemory = false, modelBytes = 74 * mb))
        assertTrue(VoicePreloadPolicy.shouldPreload(185 * mb, lowMemory = false, modelBytes = 74 * mb))
    }

    @Test
    fun `ly do noi dung so tho theo goi`() {
        val r = VoicePreloadPolicy.skip(100 * mb, lowMemory = false, modelBytes = 74 * mb).text(Lang.VI)
        assertTrue(r.contains("cần 185 MB"), r)
        assertTrue(r.contains("thở 111 MB"), r)
    }

    // ── 2026-09-25 · wake: một mô hình cho cả máy ────────────────────────────────────────────────────────

    @Test
    fun `wake BAT thi tien trinh chinh khong nap san - mo hinh song o wake`() {
        assertFalse(VoicePreloadPolicy.shouldPreloadInMain(modelInWake = true))
    }

    @Test
    fun `wake TAT thi giu nap san nhu cu (V3 R4)`() {
        assertTrue(VoicePreloadPolicy.shouldPreloadInMain(modelInWake = false))
    }

    /**
     * FIX286 · VK1 — bảng 4 ca (wake × phím): mô hình ở `:wake` (WAKE/HOLD) ⇒ chính KHÔNG nạp sẵn (một bản cho cả máy);
     * OFF ⇒ nạp sẵn như V3 R4. Ca (wake TẮT, có phím) là ca đổi hành vi của 2.86: 2.85 nạp sẵn ở chính mà phím thì nghe
     * ở `:wake` ⇒ bản nằm sai chỗ.
     */
    @Test
    fun `bang 4 ca wake x phim - chi OFF moi nap san o tien trinh chinh`() {
        listOf(false to false, false to true, true to false, true to true).forEach { (wake, key) ->
            val mode = VoiceWakeMode.of(wake, key)
            assertEquals(mode == VoiceWakeMode.OFF, VoicePreloadPolicy.shouldPreloadInMain(mode.modelInWake), "wake=$wake phím=$key")
        }
    }

    /**
     * [Senior review FIX286 Pass 2 · P2] chiều ngược của bảng trên: chế độ chuyển sang HOLD/WAKE GIỮA đời tiến trình thì
     * bản đã nạp sẵn ở chính phải được TRẢ — nhưng không bao giờ dưới chân một phiên nghe đang chạy (giải mã trả rỗng).
     * Bảng 4 ca (wake × phím) × có/không phiên: chỉ nhả khi mô hình ở `:wake` và không phiên nào chạy.
     */
    @Test
    fun `tra ban cua tien trinh chinh khi mo hinh da o wake va khong co phien nao dang chay`() {
        listOf(false to false, false to true, true to false, true to true).forEach { (wake, key) ->
            val mode = VoiceWakeMode.of(wake, key)
            assertEquals(mode.modelInWake, VoicePreloadPolicy.shouldHandOverToWake(mode.modelInWake, sessionRunning = false), "wake=$wake phím=$key")
            assertFalse(VoicePreloadPolicy.shouldHandOverToWake(mode.modelInWake, sessionRunning = true), "phiên đang chạy ⇒ không nhả (wake=$wake phím=$key)")
        }
        // Hai chiều không bao giờ cùng đúng: nạp sẵn ở chính và trả bản của chính là hai quyết định loại trừ nhau.
        listOf(true, false).forEach { inWake ->
            assertFalse(VoicePreloadPolicy.shouldPreloadInMain(inWake) && VoicePreloadPolicy.shouldHandOverToWake(inWake, sessionRunning = false))
        }
    }

    /**
     * [Senior review FIX286 Pass 3 · P2] lượt trả bản không xong phải được HỎI LẠI — bản Pass 2 thử một lần rồi thôi nên
     * lọt (a) chế độ đổi lúc chính đang nạp (BUSY) và (b) "đổi hồ sơ" nói bằng giọng từ phiên in-process (SKIPPED, không
     * `onResume` nào tới sau đó). Hỏi lại chỉ khi chế độ vẫn ở `:wake` và chưa quá trần; xong (RELEASED/EMPTY) thì thôi.
     */
    @Test
    fun `tra ban khong xong thi hoi lai khi che do van o wake va chua qua tran`() {
        val busy = listOf(ModelHolder.Release.BUSY, ModelHolder.Release.SKIPPED)
        busy.forEach { r ->
            assertTrue(VoicePreloadPolicy.shouldRetryHandOver(r, modelInWake = true, waitedMs = 0), "$r ⇒ hỏi lại")
            assertTrue(VoicePreloadPolicy.shouldRetryHandOver(r, true, VoiceWakeStandDown.MAX_WAIT_MS - 1), "$r còn trong trần")
            assertFalse(VoicePreloadPolicy.shouldRetryHandOver(r, true, VoiceWakeStandDown.MAX_WAIT_MS), "$r quá trần ⇒ thôi")
            assertFalse(VoicePreloadPolicy.shouldRetryHandOver(r, modelInWake = false, waitedMs = 0), "$r mà chế độ về OFF ⇒ chính GIỮ bản")
        }
        listOf(ModelHolder.Release.RELEASED, ModelHolder.Release.EMPTY).forEach { r ->
            assertFalse(VoicePreloadPolicy.shouldRetryHandOver(r, modelInWake = true, waitedMs = 0), "$r ⇒ xong, không hỏi lại")
        }
        assertEquals(ModelHolder.Release.entries.toSet(), (busy + listOf(ModelHolder.Release.RELEASED, ModelHolder.Release.EMPTY)).toSet(),
            "mọi kết cục của tryRelease đều có luật — thêm kết cục mới thì bài này đỏ, phải quyết nó")
    }

    /**
     * QA 2.87 [P2] — ghi chú Cài đặt *"Kachi bỏ qua bước nạp sẵn mô hình (%1$s)"* đã dịch đủ 5 tiếng nhưng `%1$s` là CÂU tiếng
     * Việt từ `:core` ⇒ máy EN/ZH/TH/MS thấy nửa câu tiếng Việt ([ĐO máy ảo `{en,zh,th,ms} · g09-p00.xml` (bằng chứng phiên, ngoài repo)]). Nay lý do là MÃ
     * ([PreloadSkip]) dịch lúc hiện. Bản tiếng Việt PHẢI giữ đúng từng byte câu cũ (log xe đọc như trước) — chuỗi cũ chép tay
     * ở đây, không suy ra từ mã.
     */
    @Test
    fun `ly do bo nap san la MA - ban tieng Viet giu nguyen tung byte`() {
        assertEquals(
            "\"Hey Kachi\" đang bật hoặc phím vô-lăng gán Kachi nghe ⇒ mô hình sống ở tiến trình :wake, không nạp bản thứ hai",
            VoicePreloadPolicy.WAKE_OWNS_MODEL.text(Lang.VI),
        )
        // Soát vòng 4 [P3]: ghim qua `skip(...).text(Lang.VI)` — đúng biểu thức dòng log `VoiceRecognizer.preload` dùng.
        assertEquals("hệ thống báo thiếu bộ nhớ (lowMemory=true)", VoicePreloadPolicy.skip(500 * mb, lowMemory = true, modelBytes = 74 * mb).text(Lang.VI))
        assertEquals("chưa biết cỡ mô hình ⇒ nạp như cũ", VoicePreloadPolicy.skip(10 * mb, lowMemory = false, modelBytes = 0).text(Lang.VI))
        assertEquals(
            "còn 100 MB, cần 185 MB (mô hình 74 MB + thở 111 MB)",
            VoicePreloadPolicy.skip(100 * mb, lowMemory = false, modelBytes = 74 * mb).text(Lang.VI),
        )
    }

    /** Mọi mã có câu ở MỌI tiếng, không còn dấu tiếng Việt ngoài VI, và số MB đi qua đủ ở mọi tiếng. */
    @Test
    fun `ly do bo nap san dich du 5 tieng, khong lot tieng Viet`() {
        val samples = PreloadSkip.Code.entries.map { code ->
            if (code == PreloadSkip.Code.NOT_ENOUGH_RAM) VoicePreloadPolicy.skip(100 * mb, false, 74 * mb) else PreloadSkip(code)
        }
        val viMarks = Regex("[ạảãàáâậầấẩẫăắằặẳẵẹẻẽèéêếềệểễịỉĩìíọỏõòóôốồộổỗơớờợởỡụủũùúưứừựửữỳýỵỷỹđ]", RegexOption.IGNORE_CASE)
        samples.forEach { s ->
            Lang.entries.filter { it != Lang.VI }.forEach { lang ->
                val t = s.text(lang)
                assertFalse(viMarks.containsMatchIn(t), "${s.code} · $lang còn tiếng Việt: $t")
                assertTrue(t != s.text(Lang.VI), "${s.code} · $lang trùng bản VI")
                if (s.code == PreloadSkip.Code.NOT_ENOUGH_RAM) {
                    listOf("100", "185", "74", "111").forEach { n -> assertTrue(n in t, "${s.code} · $lang mất số $n: $t") }
                }
            }
        }
        assertTrue(Regex("\\p{IsHan}").containsMatchIn(VoicePreloadPolicy.WAKE_OWNS_MODEL.text(Lang.ZH)), "zh phải là chữ Hán")
        assertTrue(Regex("\\p{IsThai}").containsMatchIn(VoicePreloadPolicy.WAKE_OWNS_MODEL.text(Lang.TH)), "th phải là chữ Thái")
    }

    @Test
    fun `chua biet co goi thi giu hanh vi cu`() {
        assertTrue(VoicePreloadPolicy.shouldPreload(10 * mb, lowMemory = false, modelBytes = 0))
    }

    @Test
    fun `con du cho mo hinh cong khoang tho thi nap`() {
        val model = 74 * mb
        val need = model + VoicePreloadPolicy.headroomBytes(model)
        assertTrue(VoicePreloadPolicy.shouldPreload(need, false, model))
        assertFalse(VoicePreloadPolicy.shouldPreload(need - 1, false, model))
    }
}
