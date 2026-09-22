package com.byd.clusternav.launcher.automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ AUTOMATION #1 · MƯA → TỰ SẤY KÍNH ════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R1.3 · R1.4 · R1.5. Thuần ⇒ chạy off-car.
 *
 * ## Bài này khoá lại BỐN bài học, không chỉ kiểm bảng chân lý
 *  1. **Ngưỡng mưa neo vào số ĐO ĐƯỢC** — [ĐO] on-car 2026-09-20: `SETTING_FRONT_RAIN_WIPER_SPEED` = 1 khô (4/4
 *     reads) · 2 khi tạt nước (7/7). Bài canh dùng đúng hai con số ấy, không dùng số tự nghĩ.
 *  2. **Sentinel không được thành "trời mưa"** — họ getter gạt mưa của chính ROM này trả `65535` và `-10011` khi
 *     hỏng; `65535 > 1` nên nếu lọt vào thì automation bật sấy giữa trời nắng, im lặng.
 *  3. **Không giành nút với người lái** (R1.5) — người lái tắt sấy giữa cơn mưa thì automation phải im tới lần mưa
 *     sau, chứ không bật lại ở nhịp poll kế tiếp.
 *  4. **Không tắt hộ cái sấy của người lái** — sấy do người lái bật thì hết mưa automation không được tắt.
 */
class RainDefrostPolicyTest {

    private val dry = RainDefrostPolicy.DRY
    private val rain = RainDefrostPolicy.RAIN_ON

    // ══ (1) Bảng chân lý một nhịp — spec R1.3 · R1.4 · R1.5 ══════════════════════════════════════════════

    @Test
    fun `mua ma say chua chay thi bat`() {
        assertEquals(
            RainDefrostAction.TurnOn,
            RainDefrostPolicy.decide(rainSpeed = rain, defrostOn = false, ownedByAuto = false),
        )
        // Mưa to hơn (thang mở lên trên) vẫn là mưa.
        assertEquals(
            RainDefrostAction.TurnOn,
            RainDefrostPolicy.decide(rainSpeed = 5, defrostOn = false, ownedByAuto = false),
        )
    }

    @Test
    fun `mua ma say dang chay thi khong dung`() {
        assertEquals(
            RainDefrostAction.Leave,
            RainDefrostPolicy.decide(rainSpeed = rain, defrostOn = true, ownedByAuto = true),
        )
        assertEquals(
            RainDefrostAction.Leave,
            RainDefrostPolicy.decide(rainSpeed = rain, defrostOn = true, ownedByAuto = false),
        )
    }

    @Test
    fun `het mua thi chi tat cai say cua chinh automation`() {
        assertEquals(
            RainDefrostAction.TurnOff,
            RainDefrostPolicy.decide(rainSpeed = dry, defrostOn = true, ownedByAuto = true),
        )
        assertEquals(
            RainDefrostAction.Leave,
            RainDefrostPolicy.decide(rainSpeed = dry, defrostOn = true, ownedByAuto = false),
            "sấy của NGƯỜI LÁI — automation không được tắt hộ (R1.5)",
        )
    }

    @Test
    fun `troi kho va say dang tat thi khong lam gi`() {
        assertEquals(
            RainDefrostAction.Leave,
            RainDefrostPolicy.decide(rainSpeed = dry, defrostOn = false, ownedByAuto = false),
        )
        assertEquals(
            RainDefrostAction.Leave,
            RainDefrostPolicy.decide(rainSpeed = dry, defrostOn = false, ownedByAuto = true),
            "không có gì để tắt — cờ chủ quyền sót lại không được sinh ra một lệnh ghi",
        )
    }

    /** Ngưỡng neo vào *"1 = khô"* ([ĐO]), nên đúng 1 là khô và đúng 2 là mưa — không có vùng xám. */
    @Test
    fun `nguong mua dung tai hai con so da do duoc`() {
        assertFalse(RainDefrostPolicy.isRaining(dry))
        assertTrue(RainDefrostPolicy.isRaining(rain))
        assertEquals(1, dry)
        assertEquals(2, rain)
    }

    // ══ (2) Lọc lần đọc HAL — sentinel KHÔNG được thành "trời mưa" ═══════════════════════════════════════

    @Test
    fun `sentinel va doc loi bi loc, gia tri that di qua`() {
        assertEquals(rain, RainDefrostPolicy.plausible(rain))
        assertEquals(dry, RainDefrostPolicy.plausible(dry))
        assertNull(RainDefrostPolicy.plausible(null), "không đọc được")
        assertNull(RainDefrostPolicy.plausible(65535), "[ĐO] WIPER_FRONT_WIPER_LEVEL latch — >1 nên sẽ bật sấy oan")
        assertNull(RainDefrostPolicy.plausible(-10011), "[ĐO] WIPER_AREA_FRONT_STATE invalid")
        assertNull(RainDefrostPolicy.plausible(0), "0 dưới cả mức khô = không phải một mức gạt")
    }

    /** Không đọc được ⇒ giữ nguyên MỌI thứ: không ghi xe, không đổi ký ức (không được coi như trời khô). */
    @Test
    fun `nhip khong doc duoc thi giu nguyen ca hanh dong lan ky uc`() {
        val owned = RainDefrostState(owned = true)
        val step = RainDefrostOwner.stepUnknown(owned)
        assertEquals(RainDefrostAction.Leave, step.action)
        assertEquals(owned, step.state, "đọc lỗi KHÔNG được làm mất chủ quyền — mất là hết mưa không tắt hộ nữa")
    }

    // ══ (3) Chủ quyền qua thời gian — R1.5 ══════════════════════════════════════════════════════════════

    /** Cơn mưa đầy đủ: bật khi mưa → giữ → tắt khi khô → ký ức sạch. */
    @Test
    fun `mot con mua tron ven - bat roi tat, ky uc sach`() {
        var s = RainDefrostState()

        val on = RainDefrostOwner.step(s, rainSpeed = rain, defrostOn = false)
        assertEquals(RainDefrostAction.TurnOn, on.action)
        assertTrue(on.state.owned, "nhịp RA LỆNH bật là nhịp nhận chủ quyền")
        s = on.state

        val hold = RainDefrostOwner.step(s, rainSpeed = rain, defrostOn = true)
        assertEquals(RainDefrostAction.Leave, hold.action)
        assertTrue(hold.state.owned)
        s = hold.state

        val off = RainDefrostOwner.step(s, rainSpeed = dry, defrostOn = true)
        assertEquals(RainDefrostAction.TurnOff, off.action)
        assertEquals(RainDefrostState(), off.state, "hết mưa = hết cơn ⇒ ký ức sạch")
    }

    /**
     * ⚠⚠ HÀNH VI ĐỔI (owner 2026-09-22): đang mưa mà sấy tắt ⇒ LUÔN bật lại, KHÔNG phân biệt "người tắt".
     *
     * [ĐO xe owner] thủ phạm tắt sấy giữa mưa là XE TỰ TIMEOUT, và một bit sấy=tắt không phân biệt được timeout
     * với người lái tự tắt. Owner chốt: "cứ luôn bật, tắt thì bật lại; ai không thích thì tắt tính năng trong
     * Cài đặt". Nên R1.5 cũ ("người tắt thì nhả quyền, im tới hết mưa") bị BỎ — đi mưa lâu không bao giờ mất sấy.
     */
    @Test
    fun `dang mua ma say tat thi LUON bat lai - du la timeout hay nguoi tat`() {
        var s = RainDefrostOwner.step(RainDefrostState(), rainSpeed = rain, defrostOn = false).state
        assertTrue(s.owned)

        // Sấy tắt (xe timeout HOẶC người tắt — không phân biệt) ⇒ nhịp sau BẬT LẠI.
        val reassert = RainDefrostOwner.step(s, rainSpeed = rain, defrostOn = false)
        assertEquals(RainDefrostAction.TurnOn, reassert.action, "đang mưa + sấy tắt ⇒ luôn bật lại")
        assertFalse(reassert.state.suppressed, "không còn cơ chế suppressed")
        s = reassert.state

        // Cứ tắt là bật lại, nhiều lần cũng vậy (timeout 2 lần, 3 lần… đều bật lại).
        repeat(3) {
            val again = RainDefrostOwner.step(s, rainSpeed = rain, defrostOn = false)
            assertEquals(RainDefrostAction.TurnOn, again.action, "mỗi lần sấy tắt trong mưa ⇒ bật lại")
            s = again.state
        }
    }

    /** Trời khô ⇒ hết cơn ⇒ ký ức sạch (nếu là sấy của mình thì tắt hộ). */
    @Test
    fun `troi kho thi het con, ky uc sach`() {
        var s = RainDefrostOwner.step(RainDefrostState(), rainSpeed = rain, defrostOn = false).state
        assertTrue(s.owned)

        val dryTick = RainDefrostOwner.step(s, rainSpeed = dry, defrostOn = true)
        assertEquals(RainDefrostAction.TurnOff, dryTick.action, "khô + sấy của mình ⇒ tắt hộ")
        assertEquals(RainDefrostState(), dryTick.state, "khô ⇒ hết cơn ⇒ ký ức sạch")
    }

    /**
     * Người lái tự bật sấy TRƯỚC khi automation thấy mưa ⇒ automation không nhận chủ quyền, nên hết mưa **không
     * tắt hộ**. Đây là nửa còn lại của R1.5 (nửa kia là ca tắt ở trên).
     */
    @Test
    fun `say do nguoi lai bat thi het mua khong bi tat ho`() {
        var s = RainDefrostState()

        val seen = RainDefrostOwner.step(s, rainSpeed = rain, defrostOn = true)
        assertEquals(RainDefrostAction.Leave, seen.action)
        assertFalse(seen.state.owned, "không phải mình bật ⇒ không nhận chủ quyền")
        s = seen.state

        val dryTick = RainDefrostOwner.step(s, rainSpeed = dry, defrostOn = true)
        assertEquals(RainDefrostAction.Leave, dryTick.action, "sấy của người lái — để nguyên")
    }

    /**
     * Người lái tắt sấy khi TRỜI ĐÃ KHÔ (automation vẫn còn chủ quyền từ cơn mưa vừa xong): không có gì để tắt,
     * và ký ức phải sạch — nếu còn `owned` thì lần mưa sau automation tưởng sấy đang là của mình.
     */
    @Test
    fun `tat say khi da kho thi khong ghi gi va ky uc sach`() {
        val step = RainDefrostOwner.step(RainDefrostState(owned = true), rainSpeed = dry, defrostOn = false)
        assertEquals(RainDefrostAction.Leave, step.action)
        assertEquals(RainDefrostState(), step.state)
    }

    // ══ (5) Lệnh bật KHÔNG tới được xe — không được nhận chủ quyền ═══════════════════════════════════════
    //
    // Ca này do lượt soát 1.85 bắt: `step` nhận chủ quyền ở nhịp nó RA LỆNH (nó thuần, không biết xe có nhận
    // hay không). Nếu chỗ gọi nhận `owned` trong khi xe từ chối, nhịp sau đọc `sấy=tắt` + `owned=true` = đúng
    // dấu hiệu "người lái tự tắt" ⇒ `suppressed` ⇒ mất cả cơn mưa vì MỘT lần rc ≠ 0, im lặng.

    @Test
    fun `ghi bat hong thi nha chu quyen de nhip sau thu lai`() {
        val before = RainDefrostState()
        val step = RainDefrostOwner.step(before, rainSpeed = rain, defrostOn = false)
        assertEquals(RainDefrostAction.TurnOn, step.action)
        assertTrue(step.state.owned, "nhịp ra lệnh thì step nhận chủ quyền")

        // Xe từ chối ⇒ chỗ gọi hoàn nguyên ký ức.
        val after = RainDefrostOwner.unclaim(before, step)
        assertFalse(after.owned, "ghi hỏng thì KHÔNG được giữ chủ quyền")
        assertFalse(after.suppressed, "và cũng không được tự khoá mình lại")

        // Nhịp sau: vẫn mưa, sấy vẫn tắt ⇒ THỬ LẠI (đang mưa thì luôn bật lại).
        val retry = RainDefrostOwner.step(after, rainSpeed = rain, defrostOn = false)
        assertEquals(RainDefrostAction.TurnOn, retry.action)
    }

    /** `unclaim` chỉ chạm nhánh TurnOn: nhánh hết-mưa đã xoá ký ức vì HẾT CƠN, không vì lệnh thành công. */
    @Test
    fun `unclaim khong hoan nguyen nhanh tat`() {
        val before = RainDefrostState(owned = true)
        val step = RainDefrostOwner.step(before, rainSpeed = dry, defrostOn = true)
        assertEquals(RainDefrostAction.TurnOff, step.action)
        assertEquals(RainDefrostState(), RainDefrostOwner.unclaim(before, step))
    }
}
