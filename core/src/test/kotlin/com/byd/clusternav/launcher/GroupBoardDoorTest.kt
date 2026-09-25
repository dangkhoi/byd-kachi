package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * U9 pha 2 — phần **quyết định** của bảng *Cửa & khoang* ([GroupBoard.doorPlan]), kiểm off-car.
 *
 * Tách khỏi [GroupBoardTest] vì tệp đó chạm **617 dòng** sau khi thêm tám bài này (trần dự án là 500 — cùng lý do
 * `GroupBoardModel.kt` tách khỏi `GroupBoard.kt`, và `GroupTileTightSpaceContractTest` tách khỏi
 * `GroupTileWiringContractTest`). Đường cắt theo **chủ đề**, không cắt bừa theo số dòng: bài kia canh phần chung
 * của mọi nhóm (số ô con · đơn vị · nhãn ngắn · sắc thái), bài này canh riêng phép gộp datum ⇒ bộ phận và
 * câu kết luận của một bảng.
 *
 * Nền [ALL_SHUT] đi theo sang đây vì **chỉ** các bài cửa dùng nó.
 */
class GroupBoardDoorTest {

    /**
     * Bảng vẽ được **đủ mọi bộ phận mà nhóm còn datum cho**, đúng thứ tự khai của [CarPart].
     *
     * Đếm từ chính [CapabilityGroups.DOORS] chứ không chép một danh sách tên: thêm/bớt một datum ở nhóm mà quên
     * bảng nối `DOOR_PARTS` thì bảng **im lặng vẽ thiếu** một bộ phận — đúng họ lỗi "bảng lốp có 3 bánh".
     *
     * Bộ phận VẮNG thì khai tường minh kèm lý do ([ABSENT_PARTS]) — cùng lối `HIDDEN_FROM_PICKER`/`SAME_ON_PURPOSE`:
     * một chỗ vắng có chủ ý phải đọc ra được lý do, còn vắng vì quên thì ca này ĐỎ.
     */
    @Test
    fun `bang cua ve du moi bo phan cua nhom, dung thu tu`() {
        val plan = GroupBoard.doorPlan(GroupBoard.of(CapabilityGroups.DOORS, CarStatus()))
        val expect = CarPart.values().filterNot { it in ABSENT_PARTS }
        assertEquals(expect, plan.parts.map { it.part }, "thiếu/lệch bộ phận nào là vẽ thiếu ô đó")
        // ⚠ WP8 2026-09-20: 10 datum → 8, và 8 bộ phận → 7 (purge `tailgate_position` #29 + `mirror_fold` #30).
        // ⚠⚠ 2026-09-25: 8 datum → **6**, 7 bộ phận → **6**. Gỡ `tailgate_status` ([ĐO xe] `getHatchDoorStatus`
        // rỗng với MỌI arg ⇒ cốp không có cảm biến trạng thái) làm bộ phận CỐP rời bảng hẳn; gỡ `sunroof_pos`
        // (getter = 65535, xe không có cửa sổ trời) làm nóc chỉ còn một datum. ⇒ **không còn bộ phận nào có hai
        // datum KHÁC nhau**; phép gộp `worst`/"ưu tiên bản có số" vẫn ở lại (rèm dùng nó, xem ca dưới) nhưng nay
        // là một luật đang nghỉ, không phải một luật đang chịu tải.
        assertEquals(6, CapabilityGroups.DOORS.reads.size)
        assertEquals(6, plan.parts.size, "mỗi datum-bộ-phận đúng MỘT ô — không vẽ cùng một nắp hai lần")
    }

    /** Off-car: không bịa "đã đóng", và không bộ phận nào bị tô. */
    @Test
    fun `off-car bang cua noi CHUA DOC, khong noi da dong`() {
        val plan = GroupBoard.doorPlan(GroupBoard.of(CapabilityGroups.DOORS, CarStatus()))
        assertTrue(plan.parts.none { it.open }, "chưa đọc được thì không được tô bộ phận nào")
        assertTrue(plan.parts.none { it.available })
        assertTrue(plan.footer.contains("chưa đọc"), "off-car phải nói CHƯA ĐỌC, không phải 'tất cả đã đóng': ${plan.footer}")
    }

    @Test
    fun `doc du va dong het thi ket luan la TAT CA DA DONG`() {
        val plan = GroupBoard.doorPlan(GroupBoard.of(CapabilityGroups.DOORS, ALL_SHUT))
        assertTrue(plan.parts.all { it.available }, "mọi bộ phận phải đọc được ở ca này")
        assertTrue(plan.parts.none { it.open })
        assertEquals("Tất cả đã đóng", plan.footer)
    }

    /**
     * Cửa thì ĐẾM, khoang thì KỂ TÊN — và bộ phận chưa đọc được vẫn phải được đếm ra.
     *
     * ⚠ Ca *"đóng hết nhưng còn thứ chưa đọc"* là chỗ dễ nói dối nhất: nếu gộp nó vào "Tất cả đã đóng" thì người lái
     * nghe một lời hứa về thứ chưa hề đo được.
     */
    @Test
    fun `ket luan dem cua, ke ten khoang, va khong nuot phan chua doc`() {
        val car = ALL_SHUT.copy(
            body = ALL_SHUT.body.copy(
                doorLfOpen = true, doorRrOpen = true, sunshadePct = 60,
                sunroofOpen = null,
            ),
        )
        val plan = GroupBoard.doorPlan(GroupBoard.of(CapabilityGroups.DOORS, car))
        assertTrue(plan.footer.contains("2 cửa mở"), "bốn cửa là MỘT loại ⇒ đếm: ${plan.footer}")
        assertTrue(plan.footer.contains("Rèm"), "khoang thì kể tên kèm giá trị: ${plan.footer}")
        assertTrue(plan.footer.contains("1 chưa đọc được"), "nóc chưa đọc ⇒ phải đếm ra: ${plan.footer}")

        val shutOnly = GroupBoard.doorPlan(
            GroupBoard.of(
                CapabilityGroups.DOORS,
                ALL_SHUT.copy(body = ALL_SHUT.body.copy(sunroofOpen = null)),
            ),
        )
        assertTrue(shutOnly.footer.startsWith("Đã đóng"), shutOnly.footer)
        assertTrue(shutOnly.footer.contains("1 chưa đọc được"), "đóng hết nhưng CÒN thứ chưa đọc: ${shutOnly.footer}")
    }

    /**
     * Cửa mở = ALERT (đỏ), nóc/rèm = ACTIVE (accent) — bảng chỉ **dùng lại** luật sắc thái đang có, không có luật
     * thứ hai cho riêng hình xe.
     */
    @Test
    fun `bang cua dung LAI sac thai cua o con, khong dat luat thu hai`() {
        val car = ALL_SHUT.copy(
            body = ALL_SHUT.body.copy(doorLrOpen = true, sunroofOpen = true, sunshadePct = 55),
        )
        val m = GroupBoard.of(CapabilityGroups.DOORS, car)
        val plan = GroupBoard.doorPlan(m)
        val by = plan.parts.associateBy { it.part }
        assertEquals(GroupTone.ALERT, by.getValue(CarPart.DOOR_LR).tone, "cửa mở là chuyện an toàn ⇒ ALERT")
        assertEquals(GroupTone.ACTIVE, by.getValue(CarPart.SUNROOF).tone, "nóc mở là lựa chọn của người lái ⇒ ACTIVE")
        assertEquals(GroupTone.ACTIVE, by.getValue(CarPart.SUNSHADE).tone, "rèm mở cũng là lựa chọn ⇒ ACTIVE")
        assertTrue(by.getValue(CarPart.SUNSHADE).open, "mở ⇒ TÔ")
        assertEquals(GroupTone.NEUTRAL, by.getValue(CarPart.DOOR_LF).tone)
        // Và đúng CÙNG sắc thái với ô con tương ứng — nếu lệch thì dự án có hai luật cho một sự thật.
        assertEquals(m.cells.first { it.id == "door_lr" }.tone, by.getValue(CarPart.DOOR_LR).tone)
    }

    /**
     * Bộ phận có PHẦN TRĂM ⇒ số hiển thị phải là con số, và sắc thái CẢNH BÁO không được hạ xuống.
     *
     * ⚠ WP8 đổi bộ phận đo từ CỐP sang NÓC (`tailgate_position` purge). ⚠⚠ 2026-09-25 đổi lần nữa sang RÈM: gỡ
     * `sunroof_pos` (getter = 65535, xe không có cửa sổ trời) làm nóc chỉ còn datum trạng thái ⇒ **không còn bộ
     * phận nào có hai datum KHÁC nhau**. Rèm vẫn đi qua đúng đường `posId` của [GroupBoard] (nó khai `sunshade_pct`
     * làm CẢ trạng thái lẫn phần trăm), nên nửa *"ưu tiên bản CÓ SỐ"* vẫn đo được thật; nửa *"lấy sắc thái nặng
     * hơn"* nay đo bằng chiều nó phải KHÔNG hạ cấp: cửa mở giữ ALERT.
     */
    @Test
    fun `bo phan co phan tram hien SO, va ALERT khong bi ha cap`() {
        val car = ALL_SHUT.copy(body = ALL_SHUT.body.copy(sunshadePct = 30))
        val shade = GroupBoard.doorPlan(GroupBoard.of(CapabilityGroups.DOORS, car)).parts
            .first { it.part == CarPart.SUNSHADE }
        assertEquals(GroupTone.ACTIVE, shade.tone, "rèm mở là lựa chọn của người lái ⇒ ACTIVE")
        assertTrue(shade.note.contains("30"), "nhãn cạnh bộ phận phải là con số: ${shade.note}")
        assertTrue(shade.value.contains("30"), "dòng chân ưu tiên bản CÓ SỐ: ${shade.value}")

        // Chiều NẶNG HƠN đo bằng cửa: cửa mở = ALERT và bảng không được hạ nó xuống ACTIVE/NEUTRAL.
        val door = GroupBoard.doorPlan(
            GroupBoard.of(CapabilityGroups.DOORS, ALL_SHUT.copy(body = ALL_SHUT.body.copy(doorRfOpen = true))),
        ).parts.first { it.part == CarPart.DOOR_RF }
        assertEquals(GroupTone.ALERT, door.tone, "cửa mở giữ ALERT")
    }

    /** Chỉ bộ phận **có số** mới có nhãn cạnh hình — bốn cửa chỉ đóng/mở nên không có gì để ghi. */
    @Test
    fun `chi bo phan co so moi co nhan canh hinh`() {
        val car = ALL_SHUT.copy(body = ALL_SHUT.body.copy(doorLfOpen = true))
        val plan = GroupBoard.doorPlan(GroupBoard.of(CapabilityGroups.DOORS, car))
        val withNote = plan.parts.filter { it.note.isNotEmpty() }.map { it.part }.toSet()
        assertEquals(
            setOf(CarPart.SUNSHADE), withNote,
            "vẽ chữ 'Mở' lên vạt cửa đã tô màu là nói hai lần một điều; nóc nay chỉ còn datum đóng/mở (2026-09-25)",
        )
    }

    /** Nhóm khác gọi nhầm ⇒ danh sách RỖNG, không phải một bảng vẽ bừa. */
    @Test
    fun `nhom khong phai cua thi khong co bo phan nao`() {
        val plan = GroupBoard.doorPlan(GroupBoard.of(CapabilityGroups.TYRES, CarStatus()))
        assertTrue(plan.parts.isEmpty())
        assertEquals("", plan.footer, "không có bộ phận nào thì cũng không có kết luận nào để nói")
    }

    private companion object {
        /**
         * Bộ phận CỐ Ý không có trên bảng, mỗi dòng một lý do đo được — khai tường minh thay vì chép danh sách
         * bộ phận CÓ, để "vắng vì quên" vẫn làm ca đầu tiên ĐỎ.
         *
         * `CarPart.TAILGATE` GIỮ trong enum (hình xe lớn `CarImageView`/`CarLayout` vẫn neo vùng cốp — ảnh vẽ được
         * cái cốp, chỉ không có dữ liệu về nó); nó chỉ rời **bảng dữ liệu** `GroupBoard.DOOR_PARTS`.
         */
        val ABSENT_PARTS = mapOf(
            CarPart.TAILGATE to "datum `tailgate_status` gỡ 2026-09-25 — [ĐO xe] getHatchDoorStatus rỗng mọi arg",
        )

        /**
         * Xe đã đọc được MỌI bộ phận và **đóng/mở hết mức nghỉ** — nền cho các ca bảng cửa.
         *
         * Khai đủ MỌI datum còn lại (không để `null` cái nào) vì chính chỗ `null` là thứ các bài dưới đây bật lên
         * để kiểm ca "chưa đọc được"; nền mà đã có `null` sẵn thì không phân biệt được hai ca.
         *
         * ⚠ UX-OVERHAUL · WP8 — `tailgatePct` (vị trí cốp) và `mirrorFolded` (gương) đã rời `CarStatus.Body` cùng
         * hai datum #29/#30. ⚠⚠ 2026-09-25 — `tailgateOpen` và `sunroofPct` rời tiếp (cốp không cảm biến · xe không
         * có cửa sổ trời); ca "chưa đọc được" nay dựng bằng `sunroofOpen = null`.
         */
        val ALL_SHUT = CarStatus(
            body = CarStatus.Body(
                doorLfOpen = false, doorRfOpen = false, doorLrOpen = false, doorRrOpen = false,
                sunroofOpen = false, sunshadePct = 0,
            ),
        )
    }
}
