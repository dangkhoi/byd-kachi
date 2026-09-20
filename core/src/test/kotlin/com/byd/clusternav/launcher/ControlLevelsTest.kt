package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ THANG MỨC GHẾ MÁT / GHẾ SƯỞI — khoá lại ĐÚNG phép đo, và khoá luôn cả chỗ nó còn thiếu ══════════════
 *
 * ## [ĐO xe 2026-09-16 — xe owner, DL3, 1.68 vehicleTest]
 * Màn hình gốc của xe hiện ghế mát **MỨC 2** trong khi `BYDAutoSettingDevice.getSeatVentilatingState(1)` trả về
 * **3**; ghế sưởi đọc **1** khi đang **TẮT**.
 *
 * ⇒ thang là `1 = tắt · 2 = mức 1 · 3 = mức 2 · 4 = mức 3`, **KHÔNG** phải `OFF/LOW/HIGH = 1/2/3` như tài liệu cũ
 * trong repo còn ghi (`docs/specs/kachi-live-state-ux.html` §4.5 · §4.6 · OQ3). `1/2/3` là tên **hằng** của khung
 * (`BYDAutoSettingDevice.java:326-332`), không phải thang mức mà người lái nhìn thấy — và phép đo trên mâu thuẫn
 * trực tiếp với cách đọc cũ (màn hiện "mức 2" mà raw = 3).
 *
 * ## ⚠ TODO [CHƯA BIẾT] — thang này đứng trên ĐÚNG MỘT điểm đo
 * Một điểm (mức 2 ↔ raw 3) cộng một mốc (tắt ↔ raw 1). Mức 1 và mức 3 là **nội suy**. Cần **điểm đo thứ hai** trước
 * khi được gọi là [ĐO] cho cả thang: trên xe, đặt ghế mát sang một mức KHÁC ở màn BYD gốc rồi đọc lại
 * `getSeatVentilatingState(1)` (spec OQ3 · T10). Mức 1 trả 2 ⇒ thang đúng; trả 4 ⇒ bảng phải sửa theo SỐ ĐO.
 *
 ## ✅ Cập nhật 2026-09-16 (H1 · T2) — bảng này nay nằm trên ĐƯỜNG CHẠY THẬT
 * Lượt trước đây là **chỗ dùng duy nhất** của [ControlLevels] (`seatc`/`seath` chưa có datum), và điều đó được nói
 * thẳng ra thay vì giấu đi (CLAUDE.md §8). Nay `seatc`/`seath` đọc qua `seat_vent_state`/`seat_heat_state` ⇒
 * `HalBindingTable.readState` gọi [ControlLevels.levelOf] rồi quy về 0/1 cho nút bật/tắt, và [TelemetryReadout]
 * dùng nó để hiện chữ *"Mức 2"*. Bài `thang muc nam tren duong doc that…` dưới đây khoá đúng điều đó. Lượt T6
 * (`ControlKind.CYCLE`) vẫn là chỗ sẽ tiêu thụ đủ cả thang (chọn từng mức), không chỉ 0/1.
 */
class ControlLevelsTest {

    @Test
    fun `raw 1 la tat va raw 3 la muc 2 - dung phep do tren xe 2026-09-16`() {
        assertEquals(0, ControlLevels.levelOf("seatc", 1), "raw 1 = TẮT (đo trên ghế sưởi, cùng họ hằng)")
        assertEquals(2, ControlLevels.levelOf("seatc", 3), "màn xe hiện MỨC 2 khi getter trả 3 — [ĐO xe 2026-09-16]")
        assertEquals(0, ControlLevels.levelOf("seath", 1), "[ĐO xe 2026-09-16] ghế sưởi đọc 1 lúc đang tắt")
    }

    @Test
    fun `thang seatc la tat + 2 muc - dung phep do thu hai tren xe 2026-09-17`() {
        // [ĐO xe 2026-09-17] điểm đo THỨ HAI: getSeatVentilatingState(1|2) = OFF 1 · mức1 2 · mức2 3, trim owner
        // KHÔNG có mức 3. Nếu ai "sửa lại cho khớp tài liệu cũ" (1/2/3 = tắt/thấp/cao) thì thang vẫn 3 nấc nhưng
        // levelOf(seatc,3) phải là **mức 2** (dòng trên đã khoá), không phải mức "cao nhất" của một thang 1/2/3
        // hiểu theo tên hằng. `raw 4` nay NGOÀI thang: xe này không có mức đó.
        assertEquals(3, ControlLevels.levelCount("seatc"), "tắt + 2 mức — trim owner không có mức 3 (raw 4)")
        assertNull(ControlLevels.levelOf("seatc", 4), "raw 4 ngoài thang trên trim này — hiện ⚠, không bịa mức 3")
        // seath CHƯA đo thang ⇒ giữ [SUY] 4 nấc; đây là chỗ khoá "đừng lẫn hai ghế thành một thang".
        assertEquals(4, ControlLevels.levelCount("seath"), "ghế sưởi chưa đo thang ⇒ giữ [SUY] tắt + 3 mức")
    }

    @Test
    fun `ma tho la khong nam trong thang thi tra null, KHONG lam tron thanh muc 1`() {
        assertNull(ControlLevels.levelOf("seatc", 6), "biến thể LEVEL3_ON=6 chưa đo ⇒ ⚠, không đoán")
        assertNull(ControlLevels.levelOf("seatc", 0))
        assertNull(ControlLevels.levelOf("fan", 3), "nút không chạy theo thang mức thì không có bảng")
        assertEquals(0, ControlLevels.levelCount("fan"))
    }

    @Test
    fun `rawOf la phep nghich dao dung cua levelOf`() {
        ControlLevels.RAW_BY_LEVEL.forEach { (id, raws) ->
            raws.forEachIndexed { level, raw ->
                assertEquals(level, ControlLevels.levelOf(id, raw), "$id: raw $raw phải ra mức $level")
                assertEquals(raw, ControlLevels.rawOf(id, level), "$id: mức $level phải ra raw $raw")
            }
            assertEquals(raws.size, raws.toSet().size, "$id: một mã thô không được ứng với hai mức")
        }
        assertNull(ControlLevels.rawOf("seatc", 9))
    }

    @Test
    fun `thang phai khop voi ma GHI ma bang noi dang dung`() {
        // `HalBindingTable.writeArgs` gửi [seatId=1, bật ? 2 : 1] cho ghế mát/sưởi. Nếu thang nói "tắt = 1, mức 1 = 2"
        // thì hai chỗ đang nói CÙNG một điều — đó là bất biến cần giữ, vì đọc và ghi phải cùng một bảng số.
        listOf("seatc", "seath").forEach { id ->
            val def = ControlRegistry.byId(id)!!
            assertEquals(
                ControlLevels.rawOf(id, 0), HalBindingTable.writeArgs(def, 0).last(),
                "$id: mã GHI lúc tắt phải bằng mã thô của mức 0",
            )
            assertEquals(
                ControlLevels.rawOf(id, 1), HalBindingTable.writeArgs(def, 1).last(),
                "$id: mã GHI lúc bật phải bằng mã thô của mức 1",
            )
        }
    }

    /**
     * ⚠ Bài này ĐẢO CHIỀU ngày 2026-09-16 (H1 · T2). Bản cũ khoá *"`seatc`/`seath` CHƯA đọc được"* — nó đúng ở
     * lượt trước, khi bảng mức chỉ có mỗi bài kiểm dùng (đúng hình dạng `CastShell.evictVd` mà CLAUDE.md §8 nói
     * tới). Nay hai nút đã có datum ([TelemetryRegistry] `seat_vent_state`/`seat_heat_state`) nên điều phải khoá
     * là điều ngược lại: bảng mức PHẢI nằm trên đường đọc thật.
     */
    @Test
    fun `thang muc nam tren duong doc that, khong con la bang chi bai test dung`() {
        listOf("seatc" to "seat_vent_state", "seath" to "seat_heat_state").forEach { (id, datum) ->
            assertEquals(datum, ControlRegistry.byId(id)!!.readKey, "$id phải đọc qua datum $datum")
            // [2026-09-20] seatc/seath nay là SELECT (3 mức) ⇒ readState trả THẲNG mức (không quy 0/1 như TOGGLE):
            // raw 3 = mức 2, raw 1 = mức 0 (Tắt). [ĐO xe 2026-09-16] raw 3 ⇐ màn xe "mức 2".
            val on = HalBindingTable(FakeHalGateway(getters = mapOf(getterOf(datum) to "3"))).readState(id)
            val off = HalBindingTable(FakeHalGateway(getters = mapOf(getterOf(datum) to "1"))).readState(id)
            assertEquals(2, on, "$id: mã 3 = mức 2 (SELECT trả thẳng level)")
            assertEquals(0, off, "$id: mã 1 = mức 0 ⇒ Tắt")
            // Mã ngoài thang ⇒ ⚠ (null), KHÔNG làm tròn thành "mức 1" — thang mới có MỘT điểm đo.
            assertNull(
                HalBindingTable(FakeHalGateway(getters = mapOf(getterOf(datum) to "9"))).readState(id),
                "$id: mã 9 không nằm trong thang ⇒ phải trả null, không đoán một mức",
            )
        }
    }

    /**
     * ⚠ [SOÁT 1.69 · P3] Bất biến registry đi kèm bản vá ở [HalBindingTable.readState]: ba phép đổi (thang mức →
     * 0/1 → [ControlDef.readInverted]) nay chạy đủ cả ba trên nhánh thang mức, nhưng **chỉ** khi nút là
     * [ControlKind.TOGGLE] — một *mức* không có mặt đối nghịch nào để lật (đảo "mức 2" thành 0 là bịa ra một con
     * số). Nút thang mức KHÔNG phải TOGGLE mà khai cờ đảo ⇒ ĐỎ ngay ở đây, thay vì ra một con số sai trên ô.
     *
     * ⚠ Hôm nay **chưa nút nào** khai cả hai (thang + đảo) nên bản vá kia không đổi một con số thật nào; nó chặn
     * lượt sửa SAU — lượt T6 (`ControlKind.CYCLE`) là lúc điều kiện này thật sự có thể bị vi phạm.
     */
    @Test
    fun `thang muc khong-TOGGLE thi khong duoc khai co dao`() {
        val bad = ControlRegistry.ALL.filter {
            ControlLevels.levelCount(it.id) > 0 && it.kind != ControlKind.TOGGLE && it.readInverted
        }.map { it.id }
        assertTrue(bad.isEmpty(), "nút thang mức không-TOGGLE khai readInverted ⇒ số mức bị lật thành 0/1 vô nghĩa: $bad")
    }

    /** Getter thật của một datum — lấy từ chính registry để bài kiểm không chép tay tên method. */
    private fun getterOf(datumId: String): String =
        TelemetryRegistry.byId(datumId)!!.bindingKey.substringAfter('.')
}
