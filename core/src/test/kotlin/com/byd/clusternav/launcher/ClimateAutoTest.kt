package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ UX4 — NẤC ĐÁY CỦA GIÓ LÀ **AUTO**: bài khoá dựng từ SỐ THẬT của khung ══════════════════════════════════
 *
 * Mọi hằng dùng ở đây là số **đọc được từ nguồn**, không phải số cho tiện:
 *  • `AC_WINDLEVEL_MANUAL_SIGN_OFF = 0` / `_ON = 1` (`jadx-tmap/.../ac/BYDAutoAcDevice.java:99-108` + javadoc BYD
 *    *"Auto ctrl: OFF(0) · Manual ctrl: ON(1)"*) ⇒ **0 = đang AUTO** ở đường DATUM;
 *  • quy ước `1 = đang bật` của đường NÚT (`applyInverted`, vì `ac_auto` khai [ControlDef.readInverted]);
 *  • `AC_WIND_LEVEL_SET = 0` **bị xe bỏ qua** ([ĐO xe 2026-09-20], `oncar-1.84-session-2026-09-20.md:41`);
 *  • xe báo mức gió ngay khi đang AUTO ([ĐO xe 2026-09-16], `oncar-trace-2026-09-16b/hal-reads.txt:1,6`).
 *
 * Bài đầu tiên là bài quan trọng nhất: **hai cửa `autoOn*` không được gộp**. Gộp = đảo hai lần = ô nói NGƯỢC,
 * rc vẫn 0, im lặng (xem ⚠⚠ ở KDoc [ClimateAuto]).
 */
class ClimateAutoTest {

    private val fan = ControlRegistry.byId("fan")!!
    private val temp = ControlRegistry.byId("temp")!!

    // ── 1 · HAI CỬA, và chúng phải NGƯỢC nhau ở cùng một con số ────────────────────────────────────

    @Test
    fun `so THO va so cua NUT doc nguoc nhau - khong duoc gop hai cua`() {
        // datum giữ số thô của khung: 0 = AUTO, 1 = chỉnh tay.
        assertEquals(true, ClimateAuto.autoOnFromRaw(0), "AC_WINDLEVEL_MANUAL_SIGN_OFF = 0 ⇒ đang AUTO")
        assertEquals(false, ClimateAuto.autoOnFromRaw(1), "…_ON = 1 ⇒ đang chỉnh tay")
        // nút đã qua applyInverted ⇒ quy ước chung "1 = đang bật".
        assertEquals(true, ClimateAuto.autoOnFromControl(1), "đường NÚT đã đảo rồi: 1 = đang bật")
        assertEquals(false, ClimateAuto.autoOnFromControl(0), "0 = đang tắt")
        // Đúng con số 0 cho ra HAI câu trả lời ngược nhau ở hai cửa — đó chính là lý do hai cửa phải tách.
        assertTrue(
            ClimateAuto.autoOnFromRaw(0) != ClimateAuto.autoOnFromControl(0),
            "gộp hai cửa = đảo hai lần = ô hiện AUTO đúng lúc xe đang chỉnh tay (CarStatus đã cảnh báo bẫy này)",
        )
        // Đường NÚT thật sự đảo như bài này giả định — đọc từ chính phép đảo của dự án, không phải từ trí nhớ.
        assertEquals(1, applyInverted(0, inverted = true), "ac_auto khai readInverted ⇒ raw 0 tới ô là 1")
    }

    @Test
    fun `chua doc duoc thi la CHUA BIET, khong phai dang chinh tay`() {
        assertNull(ClimateAuto.autoOnFromRaw(null))
        assertNull(ClimateAuto.autoOnFromControl(null))
    }

    // ── 2 · Chữ của chip: chỉ nói AUTO khi BIẾT CHẮC ───────────────────────────────────────────────

    @Test
    fun `chip noi AUTO n khi va chi khi da doc duoc co auto`() {
        assertEquals("AUTO 1", ClimateAuto.fanText(level = 1, autoRaw = 0), "[ĐO xe] AUTO mà vẫn có mức thật")
        assertEquals("AUTO", ClimateAuto.fanText(level = null, autoRaw = 0), "AUTO mà mức chưa đọc được")
        assertEquals("3", ClimateAuto.fanText(level = 3, autoRaw = 1), "đang chỉnh tay ⇒ chỉ con số")
    }

    @Test
    fun `chua doc duoc co auto thi TUYET DOI khong noi AUTO`() {
        assertEquals("2", ClimateAuto.fanText(level = 2, autoRaw = null), "null ⇒ về đúng hành vi trước UX4")
        assertNull(ClimateAuto.fanText(level = null, autoRaw = null), "không biết gì cả ⇒ ô hiện '—'")
    }

    // ── 3 · Bảng quyết định của cú bấm −/+ ─────────────────────────────────────────────────────────

    @Test
    fun `giam ve muc 1 van la giam binh thuong`() {
        assertEquals(ClimateAuto.StepIntent.SetLevel(1), ClimateAuto.stepIntent(fan, current = 2, delta = -1, autoOn = false))
    }

    @Test
    fun `giam tu muc 1 thi BAT AUTO chu KHONG ghi muc 0`() {
        val want = ClimateAuto.stepIntent(fan, current = 1, delta = -1, autoOn = false)
        assertEquals(ClimateAuto.StepIntent.EnableAuto, want, "[ĐO xe 2026-09-20] AC_WIND_LEVEL_SET=0 bị xe bỏ qua")
        val plan = ClimateAuto.planOf(current = 1, intent = want)
        assertNull(plan.level, "tuyệt đối KHÔNG ghi mức nào — đó là lệnh xe bỏ qua, và ô sẽ nói dối")
        assertEquals(true, plan.auto, "ghi công tắc tự động = BẬT")
        assertEquals(1, plan.shown, "xe vẫn thổi mức 1, chỉ là tự chọn ⇒ ô giữ nguyên mức")
    }

    @Test
    fun `dang AUTO bam cong thi ROI auto roi dat muc dang thoi cong mot`() {
        val want = ClimateAuto.stepIntent(fan, current = 1, delta = 1, autoOn = true)
        assertEquals(ClimateAuto.StepIntent.LeaveAuto(2), want)
        val plan = ClimateAuto.planOf(current = 1, intent = want)
        assertEquals(false, plan.auto); assertEquals(2, plan.level); assertEquals(2, plan.shown)
    }

    @Test
    fun `dang AUTO bam tru thi KHONG lam gi - xe khong co nac tat qua ma nay`() {
        val want = ClimateAuto.stepIntent(fan, current = 3, delta = -1, autoOn = true)
        assertEquals(ClimateAuto.StepIntent.NoOp, want)
        val plan = ClimateAuto.planOf(current = 3, intent = want)
        assertNull(plan.auto); assertNull(plan.level)
        assertEquals(false, plan.act, "không bắn gì ⇒ ô cũng không được đổi chữ/màu")
    }

    @Test
    fun `roi AUTO khong bao gio dat muc 0, va khong bao gio vuot tran cua thang`() {
        assertEquals(ClimateAuto.StepIntent.LeaveAuto(1), ClimateAuto.stepIntent(fan, current = 0, delta = 1, autoOn = true))
        assertEquals(ClimateAuto.StepIntent.LeaveAuto(7), ClimateAuto.stepIntent(fan, current = 7, delta = 1, autoOn = true))
        assertEquals(7, fan.max, "tiền đề: thang gió 0..7 (ControlRegistry)")
    }

    @Test
    fun `chua biet dang auto hay tay - ve day van chon AUTO chu khong ghi so 0`() {
        // Ghi 0 chắc chắn là no-op ([ĐO]); thử auto còn có cơ hội đúng. Ca này xảy ra off-car và khi datum chỉ báo
        // auto còn nguội trong HalAbsentCache.
        assertEquals(ClimateAuto.StepIntent.EnableAuto, ClimateAuto.stepIntent(fan, current = 1, delta = -1, autoOn = null))
        assertEquals(ClimateAuto.StepIntent.SetLevel(2), ClimateAuto.stepIntent(fan, current = 1, delta = 1, autoOn = null))
    }

    // ── 4 · Generic: nút KHÔNG khai autoId phải y hệt trước UX4 ────────────────────────────────────

    @Test
    fun `nut khong khai autoId di dung duong cu - khong mot nhanh re nao theo ma`() {
        assertTrue(temp.autoId.isBlank(), "tiền đề: nhiệt độ không có mặt tự động")
        listOf(-1, 1).forEach { d ->
            (temp.min..temp.max).forEach { v ->
                val plan = ClimateAuto.stepPlan(temp, current = v, delta = d, autoOn = true)   // autoOn phải bị BỎ QUA
                assertNull(plan.auto, "nút không có autoId thì không được đụng công tắc tự động nào")
                assertEquals(temp.clamp(v + d), plan.level, "và mức ghi xuống đúng như trước UX4")
                assertTrue(plan.act)
            }
        }
    }

    @Test
    fun `chi nut khai autoId moi co mat tu dong, va ma do phai co that trong registry`() {
        val withAuto = ControlRegistry.ALL.filter { it.autoId.isNotBlank() }
        assertEquals(listOf("fan"), withAuto.map { it.id }, "hôm nay đúng một cặp; thêm cặp mới thì sửa bài này")
        withAuto.forEach { d ->
            val auto = ControlRegistry.byId(d.autoId)
            assertTrue(auto != null) { "${d.id}.autoId = '${d.autoId}' không có trong registry ⇒ toggle vào hư không" }
            assertEquals(ControlKind.TOGGLE, auto!!.kind, "mặt tự động phải là một nút BẬT/TẮT")
            assertTrue(auto.readKey.isNotBlank()) { "${auto.id} chưa có đường đọc ⇒ ô không bao giờ dám nói AUTO" }
            assertTrue(d.kind == ControlKind.STEP && d.min == 0) { "${d.id}: nấc AUTO thay cho nấc min của một thang" }
        }
    }
}
