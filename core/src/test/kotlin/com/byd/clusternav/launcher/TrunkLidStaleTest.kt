package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * ═══ 3.1.9 · TRUNK-3D-HOLD-CONTRADICT — nắp cốp 3D đóng sập ~1 s sau khi mở trong khi cốp thật đang MỞ ═══════════════════════════
 *
 * [ĐO logcat xe 16/10] 10:41:11.77 phím Đảo cốp `ctl #8 args=[1]` · 10:41:12.049 bus `2940001a = 1` · nắp 3D mở xong 12.79 · 12.650 bụi
 * mịn `4f600010` (sự kiện KHÁC) · nắp đóng lại xong 13.81 · cốp thật chỉ chốt lại lúc 22.68 (sau lệnh đóng #9). Doc
 * `docs/diagnostics/trunk-state-read-2026-10-15.md` §8.
 *
 * Gốc: [CarDataAdapter.readSlow] dựng `CarStatus.Body(...)` MỚI, chép tay bốn cửa mà quên `tailgateOpen` ⇒ mọi lượt chậm và mọi lượt
 * [CarDataAdapter.readEvents] của datum KHÁC cốp xoá cốp về `null`; nhịp thân xe nghỉ (cốp có sự kiện) nên không ai đọc lại. Nắp 3D
 * (ô `tailgate_status`) về "chưa đọc" = đóng; ô cốp (`controls["trunk"]`, `mirrorBody` giữ số cũ khi `null`) vẫn MỞ.
 *
 * Thử ĐỎ [ĐO 16/10]: trả `readSlow` về `CarStatus.Body(...)` dựng mới ⇒ hai bài đầu ĐỎ (`tailgateOpen` = `null`). Bỏ vế thả-khi-khớp
 * hoặc vế hết-hạn của [LampHold.held] ⇒ bài thứ ba ĐỎ.
 */
class TrunkLidStaleTest {

    private val names = mapOf(
        "BODYWORK_LEFT_HAND_FRONT_DOOR" to 692060168, "BODYWORK_RIGHT_HAND_FRONT_DOOR" to 692060170,
        "BODYWORK_LEFT_HAND_REAR_DOOR" to 692060172, "BODYWORK_RIGHT_HAND_REAR_DOOR" to 692060174,
        "BODYWORK_LUGGAGE_DOOR" to TRUNK_FID, "PM2P5_VALUE_IN" to PM_FID, "PM2P5_VALUE_OUT" to 202,
    )
    private var now = 1_000_000L

    /** Nắp 3D: CHÍNH phép của `Car3dTaps.active` — đích giữ thắng, không thì ô `tailgate_status` của nhóm Cửa. */
    private fun lid(s: CarStatus, hold: LampHold, t: Long): Boolean {
        val cells = GroupBoard.of(Car3dCatalog.GROUPS.last(), s)?.cells.orEmpty()
        val read = Car3dHotspots.activeAny("tailgate_status") { id -> cells.firstOrNull { it.id == id } }
        val mine = cells.firstOrNull { it.id == "tailgate_status" }?.takeIf { it.available }?.let(Car3dHotspots::active)
        return hold.held("trunk", mine, t) ?: read
    }

    /** Ô cốp: CHÍNH phép của `ControlTileFactory.tileCover` (đích giữ › số nút). */
    private fun tile(s: CarStatus, hold: LampHold, t: Long): Int? = hold.shown("trunk", s.controls["trunk"], t)

    /** Mọi field của `Body` có giá trị ⇒ lượt chậm không màn nào bày (cổng nhu cầu rỗng) phải trả NGUYÊN thân xe. */
    @Test fun `luot cham khong xoa field than xe cua nhip khac`() {
        val full = CarStatus(
            body = CarStatus.Body(
                windowLfPct = 10, windowRfPct = 20, windowLrPct = 30, windowRrPct = 40,
                doorLfOpen = true, doorRfOpen = false, doorLrOpen = true, doorRrOpen = false, tailgateOpen = true,
                sunshadePct = 50, powerLevel = 2, vehicleType = "x", sunroofOpen = false, emergencyAlarm = false,
            ),
        )
        val adapter = CarDataAdapter(HalBindingTable(FakeHalGateway()), { emptySet() })
        assertEquals(full.body, adapter.readSlow(full).body, "nhịp chậm chỉ ghi field CỦA NÓ — cửa/cốp thuộc nhịp thân xe")
    }

    /** Phát lại chuỗi xe 16/10 10:41:11.77 → 13.8 qua adapter + bộ nhớ sự kiện THẬT (gateway giả = getter cốp đọc 0 trước lệnh). */
    @Test fun `xe 16-10 su kien bui min sau khi cop mo khong dong nap 3D, nap va o cung noi MO`() {
        val g = FakeHalGateway(
            gettersByArg = mapOf("getDoorState" to mapOf(1 to "0", 2 to "0", 3 to "0", 4 to "0", 6 to "0")),
            getters = mapOf("getPM2p5Value" to "[10, 20]"), featureNames = names,
        )
        val c = HalEventCache(clock = { now })
        val demand = CarDataDemand.CURATED.getValue("w_car3d") + "pm25_value"
        val adapter = CarDataAdapter(HalBindingTable(EventCachedGateway(g, c)), { demand }, clock = { now },
            controlDemand = { setOf("trunk") }, covered = c::covers)
        val plan = demand.mapNotNull { HalEventMap.bindingOf(it, g) }
        c.setPlan(plan); c.onHello(); c.onReg(plan.flatMap { it.ids }.toSet(), emptySet())
        c.onEvent(TRUNK_FID, 0, 0.0)                                         // 10:40:58.6 cốp chốt (0)
        val hold = LampHold()
        var s = adapter.readBody(adapter.readSlow(adapter.readFast(CarStatus())))   // 10:41:11.73 lượt đọc tươi của phím
        assertEquals(false, s.body.tailgateOpen)
        hold.put("trunk", true, now)                                         // 11.777 ControlWriteEcho: đích MỞ
        assertEquals(true, lid(s, hold, now), "đích giữ: nắp mở ngay")
        now += 300
        s = adapter.readEvents(s, c.onEvent(TRUNK_FID, 1, 1.0).datums)       // 12.049 bus 2940001a = 1
        assertEquals(true, s.body.tailgateOpen)
        assertEquals(true, lid(s, hold, now)); assertEquals(1, tile(s, hold, now))
        now += 600
        s = adapter.readEvents(s, c.onEvent(PM_FID, 8, 0.0).datums)          // 12.650 bụi mịn — sự kiện KHÁC cốp
        assertEquals(true, s.body.tailgateOpen, "sự kiện datum khác không được xoá cốp")
        assertEquals(true, lid(s, hold, now), "nắp 3D còn MỞ (trước bản vá: đóng sập lúc 12.85)")
        assertEquals(1, tile(s, hold, now), "ô cốp và nắp 3D nói CÙNG một điều")
        now += 10_000
        s = adapter.readSlow(s)                                              // nhịp chậm 10 s (nhịp thân xe nghỉ vì cốp có sự kiện)
        assertEquals(true, lid(s, hold, now), "lượt chậm cũng không xoá cốp"); assertEquals(1, tile(s, hold, now))
    }

    /**
     * Ba ca của đích giữ (bộ chung cho nắp 3D + ô): đích giữ CHỈ che lúc CHƯA có xác nhận, không bao giờ đè một số đọc trái chiều MỚI.
     * Với số đọc hai mức, đích chỉ sống khi số đọc = ngược đích (số đọc khớp ⇒ thả ngay) ⇒ mọi sự kiện mới (= đổi mức) đều thả nó.
     */
    @Test fun `dich giu chi che luc chua xac nhan, su kien moi luon thang`() {
        val open = CarStatus(body = CarStatus.Body(tailgateOpen = true), controls = mapOf("trunk" to 1))
        val shut = CarStatus(body = CarStatus.Body(tailgateOpen = false), controls = mapOf("trunk" to 0))
        // (1) giữ ĐÓNG, xe đang đóng (đọc 0 — khớp, thả) rồi bus báo 1 (mở lại bằng tay) ⇒ MỞ.
        LampHold().let { h ->
            h.put("trunk", false, 0)
            assertEquals(false, lid(shut, h, 100))
            assertEquals(true, lid(open, h, 2_000), "sự kiện MỞ sau lệnh đóng ⇒ số đọc thắng"); assertEquals(1, tile(open, h, 2_000))
        }
        // (2) giữ MỞ, bus báo 1 ⇒ MỞ (thả sớm), rồi không gì đè được số đọc.
        LampHold().let { h ->
            h.put("trunk", true, 0)
            assertEquals(true, lid(open, h, 300)); assertNull(h.held("trunk", true, 301), "khớp ⇒ đã thả")
        }
        // (3) giữ ĐÓNG, xe không làm theo (đọc 1 suốt, không sự kiện) ⇒ trong hạn hiện ĐÓNG, hết hạn số đọc (MỞ) thắng.
        LampHold().let { h ->
            h.put("trunk", false, 0)
            assertEquals(false, lid(open, h, ControlRegistry.TRUNK_CONFIRM_MS - 1)); assertEquals(0, tile(open, h, ControlRegistry.TRUNK_CONFIRM_MS - 1))
            assertEquals(true, lid(open, h, ControlRegistry.TRUNK_CONFIRM_MS)); assertEquals(1, tile(open, h, ControlRegistry.TRUNK_CONFIRM_MS))
        }
    }

    private companion object {
        const val TRUNK_FID = 692060186   // 0x2940001A
        const val PM_FID = 201
    }
}
