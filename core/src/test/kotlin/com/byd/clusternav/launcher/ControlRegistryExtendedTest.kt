package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControlRegistryExtendedTest {

    @Test fun `co du 5 ControlKind`() {
        val kinds = ControlRegistry.ALL.map { it.kind }.toSet()
        assertEquals(ControlKind.values().toSet(), kinds, "thieu ControlKind: ${ControlKind.values().toSet() - kinds}")
    }

    @Test fun `19 nut goc con nguyen (id + thu tu + co default)`() {
        // ⚠ 1.85 · **20 → 19**: `hood` (vị trí 12) đã xoá — [ĐO xe 2026-09-20 §4] owner xác nhận xe KHÔNG có
        // ca-pô điện, chỉ cốp sau điện; từ 1.66 nút đã bị ẩn khỏi bộ chọn vì đúng lý do ấy, lượt này bỏ hẳn.
        // Đây là mã ĐẦU TIÊN rời khối gốc. Ý của bài canh KHÔNG đổi: **thứ tự các nút còn lại phải nguyên vẹn**
        // (chúng quyết định `defaultEnabledIds()` và thứ tự thanh nút mà người dùng đã quen), nên nếu ai đó
        // chèn/đảo một nút trong khối này thì bài vẫn đỏ. Xoá thêm một mã ⇒ phải sửa danh sách Ở ĐÂY và nói rõ
        // lý do, chứ không lặng lẽ hạ con số.
        // ⚠ UX-OVERHAUL · WP8 2026-09-20 · **19 → 18**: `wiper` (gạt mưa, vị trí 18) purge theo triage owner
        // (#34 — [ĐO xe] ba getter gạt mưa chết, và nút thì owner chấm BỎ). Mã thứ HAI rời khối gốc sau `hood`.
        // ⚠⚠ 1.90 · 2026-09-21 · **18 → 16**: `vol` (Âm lượng) và `cast` (Chiếu cụm) xoá — owner chốt cho bản
        // release production. `vol` vì âm lượng đã có núm cứng + thanh Android; `cast` vì [ĐO grep] đường GHI
        // `AutoContainer.sendInfo` **chưa bao giờ được nối** (nút chết), còn việc chiếu cụm THẬT nằm ở
        // `SimpleCastRuntime` + nút nổi + nhóm Cài đặt (0 tham chiếu tới registry). Mã thứ BA và TƯ rời khối gốc.
        // ⚠⚠ 1.94 · 2026-09-22 · **16 → 15**: `window` (kính cửa lái nhị-phân, vị trí 2) XOÁ — trùng hệt `win_lf`
        // ("Kính lái", cùng `setBodyWindowCtrlState`, cùng cửa 1). Owner tái cấu trúc kính thành nút tường minh
        // (win_lf/rf/lr/rr + windows_all + 5 nút 50% + đóng-tất-cả) ⇒ `window` là bản sao ⇒ gỡ (chống anti-pattern
        // "hai mã một lệnh"). `win_lf` kế thừa chỗ dock mặc định (enabledByDefault) nhưng nằm ở khối kính (không ở
        // 15 nút gốc đầu). Khối gốc nay còn **15**.
        // ⚠ 1.94 · 2026-09-22 · gỡ thêm `lock`+`door` (NOT_PROVISIONED, owner "bỏ hẳn") ⇒ khối gốc còn **13**.
        val original = listOf(
            "trunk", "readl", "pm25", "seatc", "temp", "fan",
            "defrost", "cam", "sunroof", "headl", "seath", "recirc", "drl",
        )
        assertEquals(original, ControlRegistry.ALL.take(13).map { it.id })
    }

    @Test fun `defaultEnabledIds bat bien - 8 nut mac dinh dung thu tu`() {
        assertEquals(
            listOf("trunk", "readl", "pm25", "seatc", "temp", "fan", "win_lf"),
            ControlRegistry.defaultEnabledIds(),
        )
        // Nut moi KHONG duoc tu bat (giu dock mac dinh gon). ⚠ 1.90: hai mốc cũ `cast` + `powertrain_mode` đã xoá
        // (owner 2026-09-21) ⇒ dùng hai nút mở-rộng còn sống: `ac_auto` (gió tự động) và `wireless_charge`.
        assertTrue("ac_auto" !in ControlRegistry.defaultEnabledIds())
        assertTrue("wireless_charge" !in ControlRegistry.defaultEnabledIds())
    }

    @Test fun `moi control bindingKey khong rong`() {
        val empty = ControlRegistry.ALL.filter { it.bindingKey.isBlank() }
        assertTrue(empty.isEmpty(), "bindingKey rong: ${empty.map { it.id }}")
    }

    @Test fun `moi control id duy nhat`() {
        val ids = ControlRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `SELECT co danh sach args`() {
        val selects = ControlRegistry.ALL.filter { it.kind == ControlKind.SELECT }
        assertTrue(selects.isNotEmpty())
        selects.forEach { assertTrue(it.args.isNotEmpty(), "${it.id} SELECT thieu args") }
    }

    @Test fun `COVER va BUTTON co mat`() {
        assertTrue(ControlRegistry.ALL.any { it.kind == ControlKind.COVER })
        assertTrue(ControlRegistry.ALL.any { it.kind == ControlKind.BUTTON })
        // 1.94: kính nay là nút TOGGLE tường minh (mở/đóng), KHÔNG còn COVER 3-mức. COVER còn `sunshade`.
        val winLf = ControlRegistry.byId("win_lf")!!
        assertEquals(ControlKind.TOGGLE, winLf.kind)
        assertEquals(EvidenceTier.PROVEN, winLf.tier)
        assertEquals(ControlKind.COVER, ControlRegistry.byId("sunshade")!!.kind)
        // Đóng-tất-cả-kính + Lọc-ngay = BUTTON.
        assertEquals(ControlKind.BUTTON, ControlRegistry.byId("windows_close_all")!!.kind)
        assertEquals(ControlKind.BUTTON, ControlRegistry.byId("pm25_clean_now")!!.kind)
    }

    @Test fun `control gom nhieu domain (panel)`() {
        val domains = ControlRegistry.ALL.map { it.domain }.toSet()
        // ⚠ 1.90 · sàn hạ **6 → 5**: `powertrain_mode` là nút DUY NHẤT của `Domain.DRIVETRAIN`, và owner xoá nó
        // 2026-09-21 (xe thuần điện) ⇒ lĩnh vực Động lực nay **không có nút nào**, chỉ có datum ĐỌC (`speed`,
        // `gear`). Đó là kết luận đúng, không phải lỗ hổng: launcher không đổi chế độ lái/hệ truyền động nữa.
        assertTrue(domains.size >= 5, "control chi phu $domains")
        assertTrue(Domain.DRIVETRAIN !in domains, "Động lực KHÔNG còn nút nào — nếu có nút mới thì phải nói ra ở đây")
        // (`Domain.SAFETY` da go 2026-09-16 cung toan bo ADAS/an toan.)
        listOf(Domain.ENERGY, Domain.INFOTAINMENT, Domain.BODY, Domain.LIGHTS, Domain.CLIMATE)
            .forEach { d -> assertTrue(ControlRegistry.byDomain(d).isNotEmpty(), "domain $d khong co control") }
    }

    @Test fun `nut proven cluster nav dung tier`() {
        listOf("win_lf", "trunk", "pm25", "seatc", "seath", "pm25_clean_now").forEach { id ->
            assertEquals(EvidenceTier.PROVEN, ControlRegistry.byId(id)?.tier, "$id phai PROVEN")
        }
    }
}
