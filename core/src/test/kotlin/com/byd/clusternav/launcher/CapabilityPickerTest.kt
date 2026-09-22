package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * G1 · T4 — **màn chọn bày NHÓM TRƯỚC** ([CapabilityPicker]).
 *
 * Yêu cầu R2 của spec: *"Nhóm là thứ người dùng gặp trước, mục rời vẫn còn cho ai cần"*. Bài này canh phần QUYẾT
 * ĐỊNH (thuần, off-car); phần dây nối ở tầng vẽ do `GroupPickerWiringContractTest` (`:app`) canh.
 *
 * Bốn nhóm bất biến:
 *  1. **12 nhóm bày ra, đúng thứ tự khai** — bày thiếu một nhóm là nhóm đó không giao được.
 *  2. **Không lặp** — nhóm KHÔNG được xuất hiện lần thứ hai trong lĩnh vực (hai ô cùng một mã ⇒ bảng tra
 *     `mã → view` bị ghi đè, đúng ba lỗi cùng lúc của RW0).
 *  3. **Không mất mục rời** (§4.2 + OQ2) — 123 · 64 · 9 · 4 còn nguyên sau khi lọc nhóm.
 *  4. **Dòng phụ nói thật** — số đếm khớp dữ liệu, chữ không chép tay số.
 */
class CapabilityPickerTest {

    // ── 1 · Bày đủ 12 nhóm, đúng thứ tự ──────────────────────────────────────────────────────────

    @Test
    fun `man chon bay du 12 nhom theo dung thu tu khai`() {
        assertEquals(
            CapabilityGroups.ALL.map { it.id },
            CapabilityPicker.groupPicks().map { it.id },
            "mục Nhóm phải bày ĐỦ và theo thứ tự khai (thứ hỏi thường xuyên trước) — thứ tự là quyết định của :core, " +
                "không phải của tầng vẽ",
        )
        assertTrue(CapabilityPicker.groupPicks().all { it.group }, "mục Nhóm chỉ được chứa nhóm")
    }

    @Test
    fun `moi o nhom co du nhan icon va dong phu`() {
        CapabilityPicker.groupPicks().forEach { p ->
            assertTrue(p.label.isNotBlank(), "${p.id} thiếu nhãn")
            assertTrue(p.icon.isNotBlank(), "${p.id} thiếu icon")
            assertTrue(p.sub.isNotBlank(), "${p.id} thiếu dòng phụ ⇒ người dùng phải ĐOÁN bên trong ô có gì")
        }
    }

    // ── 2 · Nhóm không xuất hiện hai lần ─────────────────────────────────────────────────────────

    @Test
    fun `nhom KHONG lap lai trong linh vuc - hai o cung mot ma la loi da tra gia`() {
        CapabilityCatalog.byDomain().forEach { (domain, picks) ->
            val singles = CapabilityPicker.singlesOf(picks)
            assertTrue(
                singles.none { it.group },
                "lĩnh vực $domain còn nhóm ⇒ cùng một mã có hai ô ⇒ bảng tra 'mã → view' bị ghi đè, chỉ ô sau được tô",
            )
        }
        // Và toàn cục: mỗi mã xuất hiện ĐÚNG một lần trên cả màn chọn (mục Nhóm + mọi lĩnh vực).
        val shown = CapabilityPicker.groupPicks().map { it.id } +
            CapabilityCatalog.byDomain().flatMap { CapabilityPicker.singlesOf(it.second) }.map { it.id }
        val dup = shown.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(emptySet<String>(), dup, "mã hiện hai lần trên màn chọn: $dup")
    }

    // ── 3 · Mục rời còn nguyên ───────────────────────────────────────────────────────────────────

    @Test
    fun `loc nhom KHONG lam mat mot muc roi nao`() {
        // §4.2 + OQ2: xoá mục rời để "cho gọn" là làm mất khả năng. Phép lọc chỉ được bỏ ĐÚNG 12 mã nhóm.
        val before = CapabilityCatalog.byDomain().flatMap { it.second }.map { it.id }
        val after = CapabilityCatalog.byDomain().flatMap { CapabilityPicker.singlesOf(it.second) }.map { it.id }
        assertEquals(
            CapabilityGroups.ALL.map { it.id }.toSet(),
            (before - after.toSet()).toSet(),
            "phép lọc chỉ được bỏ mã NHÓM, không bỏ gì khác",
        )
        // Đếm tuyệt đối: mục rời có nhóm hiển thị = tổng khả năng − nhóm − 9 widget dựng tay (không thuộc lĩnh vực).
        // U6: trừ các mã cố ý ẩn khỏi màn chọn (có lý do, tra cứu vẫn được — xem `HIDDEN_FROM_PICKER`).
        // 100 + 47 ((V) FEATURE-FILTER 2026-09-17 owner gỡ 19 mã NO — trước đó 112 + 54; trước 09-16 là 123 + 64).
        // 1.85: **102** đọc (+1 `ac_wind_auto`) · nút giữ 47 (−`hood` +`child_lock_r`) · bảng ẩn còn 9 mục
        // (mục `hood` rời đi CÙNG lượt xoá mã — nó không còn gì để ẩn).
        // ⚠ WP8 2026-09-20: **73** đọc + **39** nút (owner purge 37 mã BỎ) · bảng ẩn +1 mục (`cast` — giữ
        // feature, chỉ ẩn ô khỏi bộ chọn) ⇒ 10 mục ẩn.
        assertEquals(
            71 + 33 + 2 - CapabilityCatalog.HIDDEN_FROM_PICKER.size, after.size,
            "mục rời theo lĩnh vực phải còn nguyên 71 đọc + 29 nút + 4 gói lệnh (trừ mã ẩn có lý do)",
        )
    }

    @Test
    fun `muc roi van con ca HANH DONG - loc nhom khong lam mat nut hay goi lenh`() {
        val after = CapabilityCatalog.byDomain().flatMap { CapabilityPicker.singlesOf(it.second) }.map { it.id }.toSet()
        // V3 · R12 (1.66): mã trong [CapabilityCatalog.HIDDEN_FROM_PICKER] được TRỪ RA (hôm nay: `temp_unit` +
        // tám ô lốp lẻ). Trừ theo chính danh sách ấy — KHÔNG chép tên mã nào vào đây — nên mục ẩn sau này tự
        // được tính, và mục ẩn KHÔNG có lý do thì bài canh ở `CapabilityCatalogTest` đỏ trước.
        // ⚠ 1.85: `hood` từng là ví dụ của luật này; nay nó đã bị **xoá hẳn** khỏi registry (xe không có ca-pô
        // điện) nên nó không còn "ẩn" mà là "không tồn tại" — hai trạng thái khác nhau, đừng lẫn khi đọc lại.
        val hidden = CapabilityCatalog.HIDDEN_FROM_PICKER.keys
        val missingControls = ControlRegistry.ALL.map { it.id }.filterNot { it in after || it in hidden }
        assertEquals(emptyList<String>(), missingControls, "nút bị mất khỏi màn chọn: $missingControls")
        val missingMacros = ActionMacros.ALL.map { it.id }.filterNot { it in after }
        assertEquals(emptyList<String>(), missingMacros, "gói lệnh bị mất khỏi màn chọn: $missingMacros")
    }

    // ── 4 · Dòng phụ nói thật ────────────────────────────────────────────────────────────────────

    @Test
    fun `dong phu lay so tu DU LIEU, khong chep tay`() {
        CapabilityGroups.ALL.forEach { g ->
            assertTrue(
                g.contentLine.startsWith("${g.visibleReadCount} mục"),
                "dòng phụ của ${g.id} phải mở đầu bằng số mục ĐẾM ĐƯỢC, không phải số viết tay: '${g.contentLine}'",
            )
            if (g.hasWrites) assertTrue(
                g.contentLine.contains("${g.writes.size} nút"),
                "nhóm có nút thì dòng phụ phải nói số nút thật: '${g.contentLine}'",
            ) else assertFalse(
                g.contentLine.contains("nút"),
                "nhóm không có nút thì đừng hứa có nút: '${g.contentLine}'",
            )
            assertTrue(g.contentLine.endsWith(g.sub), "dòng phụ phải kết bằng câu nói nội dung: '${g.contentLine}'")
            assertFalse(
                g.sub.any { it.isDigit() },
                "câu nội dung của ${g.id} chép tay số ⇒ thêm/bớt thành viên là nó nói sai mà không bài nào đỏ",
            )
        }
    }

    @Test
    fun `dong phu doi theo du lieu - them mot thanh vien la so doi`() {
        // Chứng minh số ĐẾM chứ không phải chuỗi cố định: cùng câu `sub`, thêm một thành viên ⇒ dòng phụ đổi.
        val g = CapabilityGroups.TYRES
        val plus = g.copy(reads = g.reads + "speed")
        assertFalse(plus.contentLine == g.contentLine, "dòng phụ phải đổi khi số thành viên đổi")
        assertTrue(plus.contentLine.startsWith("${g.reads.size + 1} mục"), "và đổi ĐÚNG theo số mới")
    }

    /**
     * ⚠⚠ [KIỂM TOÁN UX mục 6] Số đếm là số mục **NGƯỜI DÙNG THẤY**.
     *
     * Ca duy nhất từng lệch (`g_parking`: hai mã nhưng `radar_zones` nở ra 8 ô vùng) đã biến mất 2026-09-16 cùng
     * toàn bộ ADAS/an toàn. Bài này giữ nguyên vai: chốt rằng **hôm nay không mã nào nở ra**, để ngày ai thêm một
     * mã như thế mà quên khai số nở thì đỏ tại đây chứ không im lặng nói sai số trên màn chọn.
     */
    @Test
    fun `so muc dem theo thu NGUOI DUNG THAY, khong theo so ma datum`() {
        CapabilityGroups.ALL.forEach { g ->
            assertEquals(g.reads.size, g.visibleReadCount, "${g.id}: không có mã nở ra ⇒ hai cách đếm phải bằng nhau")
        }
    }

    @Test
    fun `chi NHOM co dong phu - muc roi de rong`() {
        // Nếu mục rời cũng có dòng phụ thì cả lưới cao thêm một dòng mỗi ô — làm chật đúng chỗ đang chật.
        val singles = CapabilityCatalog.byDomain().flatMap { CapabilityPicker.singlesOf(it.second) }
        val noisy = singles.filter { it.sub.isNotEmpty() }.map { it.id }
        assertEquals(emptyList<String>(), noisy, "mục rời không cần dòng phụ (nhãn của nó đã tự nói): $noisy")
        // U6: dòng phụ HIỂN THỊ thì mục rời được phép có — nhưng CHỈ ở đúng những ô trùng tên, không phải cả lưới.
        val withDisplaySub = singles.filter { it.displaySub.isNotEmpty() }
        assertTrue(
            withDisplaySub.all { it.label in CapabilityCatalog.collidingLabels() },
            "mục rời mọc dòng phụ mà tên KHÔNG trùng: ${withDisplaySub.filterNot { it.label in CapabilityCatalog.collidingLabels() }.map { it.id }}",
        )
        assertTrue(
            withDisplaySub.size < singles.size / 4,
            "quá nhiều ô có dòng phụ (${withDisplaySub.size}/${singles.size}) — lưới ô 40dp sẽ cao thêm một dòng " +
                "ở khắp nơi, đúng chỗ đang chật",
        )
        assertTrue(
            WidgetRegistry.ALL.mapNotNull { CapabilityCatalog.pick(it.id) }.all { it.sub.isEmpty() },
            "widget dựng tay cũng không có dòng phụ",
        )
    }

    // ── Gợi ý nhóm ở từng lĩnh vực ───────────────────────────────────────────────────────────────

    @Test
    fun `linh vuc co nhom phu thi NOI RA, khong thi im lang`() {
        // ⚠ (V) FEATURE-FILTER 2026-09-17: lĩnh vực **Lốp** không còn ô LẺ nào trong bộ chọn (tám mã lốp đã vào
        // [CapabilityCatalog.HIDDEN_FROM_PICKER] theo lệnh owner *"gôm lại thành 1 widget"*) ⇒ nó không còn là ví
        // dụ cho gợi ý-chỉ-về-nhóm. Ví dụ mới: **Kính** (`g_windows` phủ 4 ô `window_*` vẫn bày ra).
        val tyres = CapabilityCatalog.byDomain().firstOrNull { it.first == Domain.TYRES }?.second.orEmpty()
        assertEquals(
            emptyList<String>(), CapabilityPicker.singlesOf(tyres).map { it.id },
            "tám ô lốp lẻ phải KHÔNG còn trong bộ chọn — nhóm Lốp là bề mặt duy nhất cho dữ liệu lốp",
        )
        val body = CapabilityCatalog.byDomain().first { it.first == Domain.BODY }.second
        assertTrue(
            CapabilityPicker.groupHint(body).startsWith(CapabilityPicker.HINT_PREFIX),
            "lĩnh vực có nhóm phủ phải NÓI RA — đây là chỗ trả lời câu 'không ai xem 1 ô lẻ cả'",
        )
        // Lĩnh vực KHÔNG có nhóm nào phủ ⇒ im lặng (cùng luật với vòng kiểm quyền: đủ thì không nói gì).
        val drivetrain = CapabilityCatalog.byDomain().first { it.first == Domain.DRIVETRAIN }.second
        assertEquals("", CapabilityPicker.groupHint(drivetrain), "không có gì để nói thì đừng vẽ thêm một dòng")
        assertEquals("", CapabilityPicker.groupHint(emptyList()), "danh sách rỗng ⇒ rỗng")
    }

    @Test
    fun `goi y di qua tra-nguoc duy nhat va doc duoc khi co nhieu nhom`() {
        val body = CapabilityCatalog.byDomain().first { it.first == Domain.BODY }.second
        val hint = CapabilityPicker.groupHint(body)
        listOf(CapabilityGroups.WINDOWS, CapabilityGroups.DOORS).forEach {
            assertTrue(it.label in hint, "lĩnh vực thân xe phải chỉ về nhóm ${it.label}: '$hint'")
        }
        // Thứ tự theo THỨ TỰ KHAI, không theo thứ tự gặp trong danh sách ô — hai lĩnh vực không được nói cùng một cặp
        // nhóm theo hai thứ tự khác nhau.
        assertTrue(
            hint.indexOf(CapabilityGroups.WINDOWS.label) < hint.indexOf(CapabilityGroups.DOORS.label),
            "gợi ý phải theo thứ tự khai của CapabilityGroups.ALL: '$hint'",
        )
        // Nhãn nhóm có thể chứa " · " (vd "Cửa & khoang") nên dấu ngăn cách PHẢI khác, không thì đọc thành nhiều nhóm.
        assertTrue(
            CapabilityGroups.ALL.none { "," in it.label },
            "nhãn nhóm không được chứa dấu phẩy — đó là dấu ngăn cách của gợi ý",
        )
    }

    @Test
    fun `goi y bat ca nhom BAT CHEO linh vuc`() {
        // `volt_12v` khai ở ENERGY và thuộc nhóm Sức khoẻ pin (cũng ENERGY) — nhưng phép tra ngược vẫn phải đi theo
        // THÀNH VIÊN, không theo domain: đó là tính chất bài này khoá.
        val energy = CapabilityCatalog.byDomain().first { it.first == Domain.ENERGY }.second
        assertTrue(
            CapabilityGroups.BATTERY.label in CapabilityPicker.groupHint(energy),
            "gợi ý phải theo THÀNH VIÊN, không theo domain của nhóm",
        )
        assertEquals(
            listOf(CapabilityGroups.BATTERY.id),
            CapabilityGroups.groupsContaining("volt_12v").map { it.id },
            "và phải đi qua phép tra ngược DUY NHẤT (groupsContaining)",
        )
    }

    // ── Câu chữ của hai tiêu đề ──────────────────────────────────────────────────────────────────

    @Test
    fun `hai tieu de noi VIEC, khong noi kien truc va khong chep tay so`() {
        assertTrue(CapabilityPicker.GROUPS_TITLE.isNotBlank() && CapabilityPicker.SINGLES_TITLE.isNotBlank())
        listOf(CapabilityPicker.GROUPS_TITLE, CapabilityPicker.GROUPS_NOTE, CapabilityPicker.SINGLES_TITLE).forEach {
            assertFalse(it.any { ch -> ch.isDigit() }, "câu chữ màn chọn không được chép tay số: '$it'")
        }
        assertTrue(
            CapabilityPicker.GROUPS_TITLE.contains("cùng lúc"),
            "tiêu đề phải nói ĐƯỢC GÌ ('xem cả cụm cùng lúc'), không chỉ ghi 'Nhóm'",
        )
    }
}
