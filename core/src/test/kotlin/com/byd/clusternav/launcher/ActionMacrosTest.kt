package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá LỚP GỘP LỆNH (W2 — spec `kachi-action-macros.html`, R1–R5).
 *
 * Hai test quan trọng nhất:
 *  • `mot buoc hong KHONG lam chet ca goi` — bỏ dở giữa gói còn tệ hơn (đóng 2 kính rồi dừng).
 *  • `muc bang chung cua goi la THAP NHAT` — lấy mức cao nhất là hứa quá, đúng cái bẫy dự án đã trả giá.
 */
class ActionMacrosTest {

    /** Bộ ghi lại thứ tự lệnh; [failOn] = các mã sẽ báo hỏng. */
    private class Recorder(private val failOn: Set<String> = emptySet()) {
        val calls = ArrayList<Pair<String, Int>>()
        val sleeps = ArrayList<Long>()
        fun emit(id: String, arg: Int): Boolean {
            calls.add(id to arg); return id !in failOn
        }
        fun sleep(ms: Long) { sleeps.add(ms) }
    }

    private val macro3 = ActionMacro(
        "mac_test", "Thử", "ic-window", Domain.BODY,
        listOf(MacroStep("win_lf", 1), MacroStep("win_rf", 1), MacroStep("win_lr", 0, waitAfterMs = 0)),
    )

    // ── R1: đúng thứ tự, đúng tham số ────────────────────────────────────────────────────────────

    @Test
    fun `chay dung thu tu va dung tham so`() {
        val r = Recorder()
        val res = MacroRunner.run(macro3, r::emit, r::sleep)
        assertEquals(listOf("win_lf" to 1, "win_rf" to 1, "win_lr" to 0), r.calls, "phải đúng thứ tự + tham số")
        assertEquals(3, res.total)
        assertTrue(res.allOk, "không hỏng bước nào")
    }

    @Test
    fun `cho giua cac buoc nhung KHONG cho sau buoc cuoi`() {
        val r = Recorder()
        MacroRunner.run(macro3, r::emit, r::sleep)
        // 3 bước ⇒ tối đa 2 khoảng chờ; bước cuối waitAfterMs=0 nên cũng không tính
        assertEquals(listOf(ActionMacros.DEFAULT_GAP_MS, ActionMacros.DEFAULT_GAP_MS), r.sleeps,
            "chờ SAU bước 1 và 2; KHÔNG chờ sau bước cuối (chờ xong chẳng làm gì nữa là phí)")
    }

    @Test
    fun `goi rong thi khong goi lenh nao`() {
        val r = Recorder()
        val res = MacroRunner.run(ActionMacro("m", "l", "i", Domain.BODY, emptyList()), r::emit, r::sleep)
        assertTrue(r.calls.isEmpty(), "gói rỗng không được bắn lệnh")
        assertFalse(res.allOk, "gói rỗng KHÔNG được coi là thành công")
        assertEquals("gói rỗng", res.summary())
    }

    // ── R2: một bước hỏng không làm chết cả gói ──────────────────────────────────────────────────

    @Test
    fun `mot buoc hong KHONG lam chet ca goi`() {
        val r = Recorder(failOn = setOf("win_rf"))
        val res = MacroRunner.run(macro3, r::emit, r::sleep)
        assertEquals(3, r.calls.size, "bước 3 VẪN phải chạy dù bước 2 hỏng — bỏ dở giữa gói còn tệ hơn")
        assertEquals(listOf("win_rf"), res.failed, "phải biết ĐÍCH DANH bước nào hỏng")
        assertEquals(2, res.okCount)
        assertFalse(res.allOk)
        assertFalse(res.allFailed)
        assertTrue(res.summary().contains("win_rf"), "câu tóm tắt phải nêu bước hỏng")
    }

    @Test
    fun `buoc nem loi cung chi tinh la hong, khong lam sap`() {
        val res = MacroRunner.run(macro3, { _, _ -> throw IllegalStateException("xe từ chối") })
        assertEquals(3, res.total, "vẫn chạy hết các bước")
        assertTrue(res.allFailed, "mọi bước tính là hỏng")
    }

    @Test
    fun `off-car moi lenh no-op thi bao khong buoc nao an`() {
        val res = MacroRunner.run(macro3, { _, _ -> false })
        assertTrue(res.allFailed, "off-car là ca BÌNH THƯỜNG, không phải lỗi")
        assertTrue(res.summary().contains("không bước nào ăn"))
    }

    // ── R3: chỉ dùng lại nút đã khai ─────────────────────────────────────────────────────────────

    @Test
    fun `moi goi dinh san deu chi tro toi nut DA KHAI`() {
        assertEquals(emptyList<String>(), ActionMacros.invalid(),
            "Gói trỏ tới mã nút không tồn tại (hoặc gói rỗng). Sửa khai báo, ĐỪNG nới test này.")
        ActionMacros.ALL.forEach { m ->
            assertTrue(m.steps.isNotEmpty(), "gói ${m.id} không được rỗng")
            assertEquals(emptyList<String>(), m.invalidSteps(), "gói ${m.id} có bước trỏ mã lạ")
        }
    }

    @Test
    fun `ma la bi bat luc KHAI BAO chu khong phai luc chay`() {
        val bad = ActionMacro("m", "l", "i", Domain.BODY, listOf(MacroStep("khong_ton_tai", 1)))
        assertEquals(listOf("khong_ton_tai"), bad.invalidSteps(), "phải phát hiện được mà không cần chạy")
    }

    // ── R5: mức bằng chứng = THẤP NHẤT ───────────────────────────────────────────────────────────

    @Test
    fun `muc bang chung cua goi la THAP NHAT trong cac buoc`() {
        // win_lf = đã chạy trên xe; door = chỉ xác nhận được trên xe ⇒ gói phải lấy mức YẾU
        val mixed = ActionMacro("m", "l", "i", Domain.BODY,
            listOf(MacroStep("win_lf", 1), MacroStep("door", 1)))
        assertEquals(EvidenceTier.PROVEN, ControlRegistry.byId("win_lf")!!.tier, "tiền đề: kính đã chạy trên xe")
        assertEquals(EvidenceTier.NEEDS_CAR, ControlRegistry.byId("door")!!.tier, "tiền đề: cửa chưa xác nhận")
        assertEquals(EvidenceTier.NEEDS_CAR, mixed.tier(),
            "gói phải lấy mức YẾU nhất — lấy mức cao nhất là hứa quá")
    }

    @Test
    fun `2026-09-21 badge goi lenh da bo - luon false`() {
        // owner chốt bỏ hẳn chấm. tier() vẫn là dữ liệu (mức yếu nhất), chỉ needsBadge() luôn false.
        val mixed = ActionMacro("m", "l", "i", Domain.BODY,
            listOf(MacroStep("win_lf", 1), MacroStep("door", 1)))
        assertEquals(EvidenceTier.NEEDS_CAR, mixed.tier(), "tier vẫn là dữ liệu")
        assertFalse(mixed.needsBadge(), "chấm đã bỏ hẳn")
        ActionMacros.ALL.forEach {
            assertFalse(it.needsBadge(), "gói ${it.id}: không gói nào còn mang dấu")
        }
    }

    @Test
    fun `thu tu khai cua EvidenceTier LA thu hang tin cay giam dan`() {
        // `tier()` xếp hạng bằng `ordinal` nên phép so phủ ĐỦ mọi giá trị enum (không có danh sách viết tay để quên).
        // Đổi thứ tự khai, hoặc chèn một tier mới vào giữa, sẽ đổi nghĩa "yếu nhất" ⇒ test này phải đỏ để bắt buộc
        // xem lại. Bản đầu dùng `RANK.indexOf(...)`: tier ngoài danh sách trả -1 ⇒ bị coi là yếu nhất âm thầm.
        assertEquals(
            listOf(EvidenceTier.PROVEN, EvidenceTier.OVERDRIVE, EvidenceTier.DASHCAST, EvidenceTier.NEEDS_CAR),
            EvidenceTier.values().toList(),
            "mạnh → yếu; thêm tier mới thì phải quyết định nó đứng đâu",
        )
        val proven = ActionMacro("m", "l", "i", Domain.BODY, listOf(MacroStep("win_lf", 1)))
        assertEquals(EvidenceTier.PROVEN, proven.tier(), "một bước PROVEN ⇒ gói PROVEN")
        // `readl` = OVERDRIVE (`lock` từng đứng đây, nay NEEDS_CAR vì setter không có trong stub — remediation 2026-09-15).
        val withOverdrive = ActionMacro("m", "l", "i", Domain.BODY,
            listOf(MacroStep("win_lf", 1), MacroStep("readl", 1)))
        assertEquals(EvidenceTier.OVERDRIVE, withOverdrive.tier(), "PROVEN + OVERDRIVE ⇒ OVERDRIVE")
    }

    @Test
    fun `goi toan buoc da chay tren xe thi giu muc do`() {
        val open = ActionMacros.byId("mac_win_open_all")!!
        assertEquals(EvidenceTier.PROVEN, open.tier(),
            "4 kính riêng đều đã chạy trên xe ⇒ gói cũng vậy")
        assertFalse(open.needsBadge(), "không cần dấu chưa-kiểm")
    }

    @Test
    fun `goi rong coi nhu chua xac nhan duoc`() {
        assertEquals(EvidenceTier.NEEDS_CAR, ActionMacro("m", "l", "i", Domain.BODY, emptyList()).tier(),
            "không có gì chứng minh nó chạy")
    }

    // ── Nội dung các gói định sẵn ────────────────────────────────────────────────────────────────

    @Test
    fun `mo het kinh gom dung 4 kinh rieng chu khong dung nut gop chua kiem`() {
        val m = ActionMacros.byId("mac_win_open_all")!!
        assertEquals(listOf("win_lf", "win_rf", "win_lr", "win_rr"), m.steps.map { it.controlId })
        assertTrue(m.steps.all { it.arg == 1 }, "mở = 1")
        assertFalse(m.steps.any { it.controlId == "windows_all" },
            "KHÔNG dùng nút gộp — nó ở mức chưa kiểm, còn 4 nút riêng đã chạy trên xe")
    }

    @Test
    fun `dong het kinh la cap doi cua mo het kinh`() {
        val open = ActionMacros.byId("mac_win_open_all")!!
        val close = ActionMacros.byId("mac_win_close_all")!!
        assertEquals(open.steps.map { it.controlId }, close.steps.map { it.controlId }, "cùng 4 kính")
        assertTrue(close.steps.all { it.arg == 0 }, "đóng = 0")
    }

    @Test
    fun `goi mo cua kem den dung dung yeu cau owner`() {
        val m = ActionMacros.byId("mac_door_light")!!
        assertEquals(listOf("door", "readl"), m.steps.map { it.controlId }, "mở cửa RỒI bật đèn đọc")
        assertTrue(m.steps.first().waitAfterMs > 0, "phải chờ sau khi mở cửa — xe cần thời gian")
    }

    @Test
    fun `goi roi xe chi gom viec dao lai duoc`() {
        val m = ActionMacros.byId("mac_leave")!!
        val ids = m.steps.map { it.controlId }
        assertTrue(ids.containsAll(listOf("win_lf", "win_rf", "win_lr", "win_rr")), "đóng hết kính")
        assertTrue("lock" in ids, "khoá xe ở bước cuối")
        assertEquals("lock", ids.last(), "khoá phải là bước CUỐI (khoá trước rồi đóng kính là vô nghĩa)")
        // C5: không gộp việc không đảo lại được
        assertFalse("trunk" in ids, "không gộp cốp vào gói rời xe")
    }

    @Test
    fun `ma goi KHONG duoc trung voi bat ky ma nao khac`() {
        // Cùng không gian mã phẳng với datum/nút/widget ⇒ trùng là nối chéo âm thầm
        val others = (WidgetRegistry.ALL.map { it.id } + TelemetryRegistry.ALL.map { it.id } +
            ControlRegistry.ALL.map { it.id }).toSet()
        ActionMacros.ALL.forEach { m ->
            assertFalse(m.id in others, "mã gói ${m.id} trùng với một mã đã có")
            assertTrue(m.id.startsWith("mac_"), "mã gói phải có tiền tố mac_ để người đọc nhận ra ngay")
        }
        assertEquals(ActionMacros.ALL.size, ActionMacros.ALL.map { it.id }.toSet().size, "mã gói không trùng nhau")
    }

    // ── R6: nhãn sai đã sửa (kiểm GIÁ TRỊ THẬT, không quét chữ trong source) ─────────────────────

    @Test
    fun `nut kinh lai KHONG hua phan tram`() {
        // 1.94: control cũ "window" (trùng win_lf) đã gỡ. Nút kính lái nay là `win_lf` (TOGGLE mở/đóng) —
        // nhãn KHÔNG mang "%"/"50" (nó chỉ mở hết/đóng). Việc 50% nằm ở control RIÊNG `win_half_lf` (nhãn có
        // "50%" là đúng vì nó CÓ đường ghi WINDOW_OPEN_HALF=4 — xem test `khong nut nao hua phan tram...`).
        val winLf = ControlRegistry.byId("win_lf")!!
        assertFalse(winLf.label.contains("%"), "nút kính lái mở/đóng KHÔNG hứa phần trăm")
        assertFalse(winLf.label.contains("50"), "nhãn cũ 'Kính 50%' hứa thứ nút mở/đóng không làm")
    }

    @Test
    fun `khong nut nao hua phan tram trong khi chi doc duoc phan tram`() {
        // 1.94 (owner 2026-09-22): nay CÓ nút 50% tường minh (`win_half_*`) — chúng ghi WINDOW_OPEN_HALF=4
        // (đường GHI 50% THẬT, enum proven per-window). Nhãn "%" của CHÚNG là đúng, không phải hứa suông.
        // Test vẫn chặn nút KHÁC hứa "%" mà không có đường ghi 50% (đúng bệnh nhãn cũ "Kính 50%").
        val allowed = ControlRegistry.ALL.filter { it.id.startsWith("win_half_") }.map { it.id }.toSet()
        val writesPromisingPercent = ControlRegistry.ALL
            .filter { it.label.contains("%") && it.id !in allowed }.map { it.id }
        assertEquals(emptyList<String>(), writesPromisingPercent,
            "Nút hứa phần trăm nhưng không có đường ghi phần trăm (nút 50% kính có đường WINDOW_OPEN_HALF được miễn).")
    }

    // ── Đóng 3 điểm treo (soát xét lượt 2) ───────────────────────────────────────────────────────

    @Test
    fun `thanh cong thi IM LANG, hong thi PHAI bao cho nguoi dung`() {
        val r = Recorder()
        assertNull(MacroRunner.run(macro3, r::emit, r::sleep).notice("Thử"),
            "mọi bước ăn ⇒ không thông báo (không ai muốn bị báo mỗi lần bấm đúng)")

        val partial = MacroRunner.run(macro3, Recorder(failOn = setOf("win_rf"))::emit)
        val msg = partial.notice("Rời xe")!!
        assertTrue(msg.contains("Rời xe"), "phải nói gói nào")
        assertTrue(msg.contains("Kính phụ"),
            "phải gọi bước hỏng bằng NHÃN (câu cho người đọc), không bằng mã")
        assertFalse(msg.contains("win_rf"), "không phơi mã kỹ thuật ra cho người dùng")
    }

    @Test
    fun `khong buoc nao an thi noi ro la xe khong nhan lenh`() {
        val msg = MacroRunner.run(macro3, { _, _ -> false }).notice("Rời xe")!!
        assertTrue(msg.contains("không nhận lệnh"),
            "off-car / xe từ chối hết ⇒ nói thẳng, đừng để người ta đoán là tính năng hỏng")
    }

    @Test
    fun `goi rong thi khong bao gi`() {
        val empty = MacroRunner.run(ActionMacro("m", "l", "i", Domain.BODY, emptyList()), { _, _ -> true })
        assertNull(empty.notice("x"), "gói rỗng không có gì để báo")
    }

    @Test
    fun `tra goi theo ma`() {
        assertNotNull(ActionMacros.byId("mac_leave"))
        assertNull(ActionMacros.byId("khong_co"), "mã lạ ⇒ null, không sập")
    }
}
