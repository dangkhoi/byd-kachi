package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LayoutPreset
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R5 · CÂU ĐỌC LẠI GIÁ TRỊ THẬT + L7 · CÂU BỐ CỤC — `:core` thuần, kiểm off-car ══════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R5 · T10** và `kachi-voice-command.html` **L7**.
 *
 * Bài chạy-thật cho phần **hành vi** (đọc lại mấy lần, gọi lambda nào) nằm ở `:app`
 * (`VoiceStepReadbackTest`); ở đây chỉ khoá **câu chữ** — nó ở `:core` nên kiểm được cả bản EN mà không cần máy.
 *
 * ## ⚠ TỰ DỌN [Strings.current]
 * Nó là `var` toàn cục; bài nào đổi mà không trả lại sẽ làm bài chạy SAU đọc nhãn tiếng Anh trong khi nó assert
 * tiếng Việt — đỏ ở một tệp không liên quan, mà chạy riêng lẻ thì xanh (xem KDoc `LangCoverageTest`).
 */
class VoiceReplyActualTest {

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    private fun temp(v: Int) = VoiceIntent.Control("temp", v)

    /** Khớp ⇒ **đúng** câu của [VoiceReply.done], không thêm một ký tự nào. */
    @Test
    fun `khop thi khong them mot chu nao`() {
        assertEquals(VoiceReply.done(temp(24)), VoiceReply.doneActual(temp(24), 24))
    }

    /**
     * Lệch ⇒ nói ra **cả hai** con số, và vẫn là ✓.
     *
     * Ba tính chất, mỗi cái chặn một cách nói dối khác nhau: giữ ✓ (lệnh KHÔNG hỏng), giữ số đã gửi (không giấu
     * việc vừa xảy ra), thêm số xe báo (không hứa một thứ chưa xảy ra).
     */
    @Test
    fun `lech thi noi ca so da gui lan so xe bao`() {
        val s = VoiceReply.doneActual(temp(24), 23)
        assertTrue(s.startsWith("✓"), "lệnh được nhận ⇒ ✓, không phải ✗: $s")
        assertTrue(s.contains("24"), "phải giữ số ĐÃ GỬI: $s")
        assertTrue(s.contains("23"), "phải nói số XE BÁO: $s")
        assertTrue(s.contains("Nhiệt độ"), "phải gọi tên nút bằng nhãn của bộ đăng ký: $s")
    }

    /** Câu lệch cũng phải đọc được bằng tiếng Anh — `:app` không được chứa chuỗi này (LauncherI18nContractTest). */
    @Test
    fun `cau lech co ban tieng Anh`() {
        Strings.current = Lang.EN
        val s = VoiceReply.doneActual(temp(24), 23)
        assertTrue(s.contains("24") && s.contains("23"), "bản EN phải giữ đủ hai con số: $s")
        assertFalse(
            s.contains("Đã gửi") || s.contains("xe báo"),
            "bản EN còn chữ Việt ⇒ máy tiếng Anh nghe một câu nửa Việt nửa Anh: $s",
        )
    }

    /**
     * ⚠ Nút chưa kiểm trên xe vẫn phải đeo dấu *"chưa kiểm"* ở **cả hai** nhánh.
     *
     * Bỏ sót ở nhánh lệch là ca dễ quên nhất (nó là nhánh mới), mà đó lại đúng là ca đáng nghi nhất: một nút
     * chưa kiểm + một con số không khớp thường là *"nút này không ăn trên trim đó"*.
     */
    @Test
    fun `dau chua kiem tren xe con nguyen o ca hai nhanh`() {
        val unverified = com.byd.clusternav.launcher.ControlRegistry.ALL
            .firstOrNull { it.kind == com.byd.clusternav.launcher.ControlKind.STEP &&
                com.byd.clusternav.launcher.CarCapabilities.needsBadge(it.id) }
            ?: return   // danh mục không còn nút STEP chưa kiểm nào ⇒ không có gì để canh
        val i = VoiceIntent.Control(unverified.id, unverified.max)
        val tail = "chưa kiểm trên xe"
        assertTrue(VoiceReply.done(i).contains(tail))
        assertTrue(
            VoiceReply.doneActual(i, unverified.min).contains(tail),
            "nhánh LỆCH quên dấu 'chưa kiểm' ⇒ một nút chưa từng chạy trên xe lại nghe chắc chắn hơn nút đã kiểm",
        )
    }

    // ══ L7 · câu bố cục ═══════════════════════════════════════════════════════════════════════════════════

    /** Câu bố cục đọc **nhãn của chính enum** — không có bảng chữ thứ hai để lệch với chip ở Cài đặt. */
    @Test
    fun `cau bo cuc doc nhan cua enum, ca hai ngon ngu`() {
        LayoutPreset.values().forEach { p ->
            assertTrue(
                VoiceReply.preview(VoiceIntent.Layout(p)).contains(p.label),
                "bố cục ${p.name}: câu xem-trước không mang nhãn của enum",
            )
        }
        Strings.current = Lang.EN
        val en = VoiceReply.preview(VoiceIntent.Layout(LayoutPreset.TWO_COL))
        assertTrue(en.contains(LayoutPreset.TWO_COL.label), "bản EN phải theo nhãn EN của enum: $en")
        assertFalse(en.contains("Bố cục"), "bản EN còn chữ Việt: $en")
    }

    /** Không đổi được ⇒ ✗ **và** nói ra chỗ làm được — không phải một câu "không hiểu". */
    @Test
    fun `khong doi duoc bo cuc thi noi thang va chi cho lam duoc`() {
        val s = VoiceReply.layoutNotHere(VoiceIntent.Layout(LayoutPreset.QUAD))
        assertTrue(s.startsWith("✗"), "chưa đổi được ⇒ không được mang dấu ✓: $s")
        assertTrue(s.contains(LayoutPreset.QUAD.label), "phải nói rõ bố cục nào: $s")
    }
}
