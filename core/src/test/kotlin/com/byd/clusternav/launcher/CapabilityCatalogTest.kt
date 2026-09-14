package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá lớp TRA CỨU KHẢ NĂNG hợp nhất — nền của RW0 (spec `kachi-unified-capability-tile.html` §4.3, R1 + R5).
 *
 * Test quan trọng nhất là `khong co ma nao trung` — nó chặn loại lỗi **nối chéo âm thầm** giữa "xem" và "bấm":
 * một mã trùng sẽ khiến ô hiện số trong khi người dùng tưởng bấm được (hoặc ngược lại), rất khó lần ra từ hiện tượng.
 */
class CapabilityCatalogTest {

    // ── R5: chống xung đột mã (test khoá) ────────────────────────────────────────────────────────

    @Test
    fun `khong co ma nao trung giua ba bo dang ky`() {
        assertEquals(
            emptyList<String>(), CapabilityCatalog.collisions(),
            "Mã bị trùng giữa các bộ đăng ký ⇒ nối chéo âm thầm giữa ĐỌC và HÀNH ĐỘNG. " +
                "Đổi tên mã mới, ĐỪNG nới test này.",
        )
    }

    @Test
    fun `tong so ma bang tong ba bo va deu phan giai duoc`() {
        // W2 thêm nguồn thứ TƯ: gói lệnh (cũng là HÀNH ĐỘNG). G1 thêm nguồn thứ NĂM: NHÓM khả năng (đọc — xem KDoc
        // [CapabilityCatalog.kindOf]). Ý định của phép kiểm không đổi — gộp không được làm MẤT hay NHÂN ĐÔI mục nào;
        // chỉ cập nhật con số kỳ vọng cho đúng số nguồn hiện tại.
        // S4 · R12 thêm nguồn thứ SÁU: hành động của CHÍNH launcher ([LauncherActions]) — bấm được nhưng không
        // gửi gì xuống xe. Ý định phép kiểm không đổi: gộp không được làm MẤT hay NHÂN ĐÔI mục nào.
        val total = CapabilityGroups.ALL.size + WidgetRegistry.ALL.size + TelemetryRegistry.ALL.size +
            ControlRegistry.ALL.size + ActionMacros.ALL.size + LauncherActions.ALL.size
        assertEquals(total, CapabilityCatalog.allIncludingHidden().size, "gộp không được làm mất hay nhân đôi mục nào")
        // U6: `all()` = bản kê ĐẦY ĐỦ trừ đúng những mã cố ý ẩn khỏi màn chọn — không được trừ thêm gì khác.
        assertEquals(
            total - CapabilityCatalog.HIDDEN_FROM_PICKER.size, CapabilityCatalog.all().size,
            "màn chọn mất mục ngoài danh sách ẩn có lý do",
        )
        CapabilityCatalog.allIncludingHidden().forEach { p ->
            assertNotNull(CapabilityCatalog.kindOf(p.id), "mã ${p.id} phải phân loại được")
            assertNotNull(CapabilityCatalog.pick(p.id), "mã ${p.id} phải tra cứu được")
        }
    }

    /**
     * S4 · R12 — hành động của launcher: loại RIÊNG, PROVEN, không badge, không lĩnh vực.
     *
     * Bốn tính chất này là **hợp đồng với ba tầng vẽ** (thanh nút dựng ô bấm · bộ chọn bày khối riêng · thanh trên
     * từ chối). Đổi một cái ở đây mà không đổi ở đó thì sai IM LẶNG: ô không hiện, hoặc hiện kèm một dấu cảnh báo
     * nói sai, hoặc lọt lên chip 24dp.
     */
    @Test
    fun `hanh dong launcher la loai rieng - PROVEN, khong badge, khong linh vuc`() {
        // V1 pha NGHE (R12): +1 — *Nói với xe*. Con số ghim ở đây là bản kê "launcher làm được mấy việc\n        // KHÔNG chạm vào xe"; thêm một việc mà không đọc lại bốn tính chất dưới là cách để một ô mới lọt lên\n        // chip 24dp hoặc mang dấu cảnh báo nói sai.
        assertEquals(3, LauncherActions.ALL.size, "ba hành động: Ứng dụng · Cài đặt · Nói với xe")
        LauncherActions.ALL.forEach { a ->
            assertEquals(CapabilityKind.LAUNCHER, CapabilityCatalog.kindOf(a.id), "${a.id} phải là loại LAUNCHER")
            assertFalse(CapabilityCatalog.isWrite(a.id), "KHÔNG được coi là hành động ghi vào XE")
            val pick = CapabilityCatalog.pick(a.id)
            assertNotNull(pick, "${a.id} phải tra ra được như một khả năng")
            assertEquals(EvidenceTier.PROVEN, pick!!.tier, "đường mở ngăn kéo/Cài đặt là đường dùng hằng ngày")
            assertFalse(pick.needsBadge, "không được mang dấu 'chưa kiểm trên xe'")
            assertNull(pick.domain, "không thuộc lĩnh vực nào của xe ⇒ byDomain() không bày")
            assertEquals(a.label, pick.label, "nhãn chảy từ bộ đăng ký, không gõ lại ở catalog")
            assertEquals(a.labelEn, pick.labelEn, "nhãn EN cũng vậy")
            assertTrue(a.id.startsWith("launcher_"), "tiền tố `launcher_` để đọc mã là biết nó không chạm vào xe")
        }
    }

    // ── R1: phân loại đúng đọc / hành động ───────────────────────────────────────────────────────

    @Test
    fun `thong tin xe la DOC va nut la HANH DONG`() {
        assertEquals(CapabilityKind.READ, CapabilityCatalog.kindOf("tyre_p_fl"), "áp suất lốp = chỉ xem")
        assertEquals(CapabilityKind.READ, CapabilityCatalog.kindOf("soc"), "phần trăm pin = chỉ xem")
        assertEquals(CapabilityKind.READ, CapabilityCatalog.kindOf("w_tire"), "widget dựng tay = chỉ xem")
        assertEquals(CapabilityKind.WRITE, CapabilityCatalog.kindOf("recirc"), "lấy gió trong = bấm được")
        assertTrue(CapabilityCatalog.isWrite("recirc"), "phải nhận ra là hành động")
        assertFalse(CapabilityCatalog.isWrite("tyre_p_fl"), "thông tin đọc KHÔNG được coi là hành động")
    }

    @Test
    fun `ma la thi tra ve rong chu khong sap`() {
        // Suy giảm an toàn: prefs có thể còn mã của bản cũ đã xoá
        listOf("khong_ton_tai", "", "  ", "W_TIRE").forEach {
            assertNull(CapabilityCatalog.kindOf(it), "mã lạ '$it' phải cho null")
            assertNull(CapabilityCatalog.pick(it), "mã lạ '$it' phải cho null")
        }
    }

    @Test
    fun `moi nut deu phan giai ra HANH DONG va moi thong tin ra DOC`() {
        ControlRegistry.ALL.forEach {
            assertEquals(CapabilityKind.WRITE, CapabilityCatalog.kindOf(it.id), "nút ${it.id} phải là HÀNH ĐỘNG")
        }
        TelemetryRegistry.ALL.forEach {
            assertEquals(CapabilityKind.READ, CapabilityCatalog.kindOf(it.id), "mục ${it.id} phải là ĐỌC")
        }
    }

    // ── Hình dạng dữ liệu cho UI ─────────────────────────────────────────────────────────────────

    @Test
    fun `widget dung tay duoc danh dau rieng va khong thuoc nhom nao`() {
        val w = CapabilityCatalog.pick("w_tire")!!
        assertTrue(w.curated, "widget dựng tay phải được đánh dấu để UI hiện riêng")
        assertNull(w.domain, "widget dựng tay không thuộc nhóm nào")
        val t = CapabilityCatalog.pick("tyre_p_fl")!!
        assertFalse(t.curated, "mục registry KHÔNG phải widget dựng tay")
        assertEquals(Domain.TYRES, t.domain, "mục lốp phải thuộc nhóm Lốp")
    }

    @Test
    fun `gom theo nhom co ca doc va hanh dong trong cung mot nhom`() {
        val climate = CapabilityCatalog.byDomain().firstOrNull { it.first == Domain.CLIMATE }?.second
        assertNotNull(climate, "nhóm khí hậu phải có mục")
        assertTrue(climate!!.any { it.kind == CapabilityKind.READ }, "nhóm khí hậu phải có thứ xem được")
        assertTrue(climate.any { it.kind == CapabilityKind.WRITE }, "nhóm khí hậu phải có thứ bấm được")
    }

    @Test
    fun `gom theo nhom khong chua widget dung tay`() {
        val ids = CapabilityCatalog.byDomain().flatMap { it.second }.map { it.id }
        WidgetRegistry.ALL.forEach {
            assertFalse(it.id in ids, "widget dựng tay ${it.id} KHÔNG được lọt vào nhóm domain")
        }
    }

    // ── Nhãn trùng giữa ĐỌC và HÀNH ĐỘNG (lộ ra từ khi gói 2 gộp hai loại vào MỘT lưới) ──────────

    @Test
    fun `sau khi phan biet thi KHONG con O nao trung nhau`() {
        // Đây là phép kiểm mạnh nhất: [ĐO] 2026-09-11 có 18 nhãn trùng giữa mục ĐỌC và HÀNH ĐỘNG (vd hai ô đều ghi
        // "Kính trước-trái": một để XEM độ mở %, một để BẤM đóng/mở). Trước gói 2 chúng ở hai màn khác nhau nên
        // trùng không sao; nay nằm cạnh nhau trong cùng lưới ⇒ người dùng không phân biệt được.
        //
        // ⚠ U6 đổi ĐƠN VỊ SO SÁNH, không nới lỏng luật: gợi ý loại đã rời khỏi nhãn chính xuống DÒNG PHỤ (owner đọc
        // được "Charge target · view" trên ảnh và gọi đúng tên — thuật ngữ nội bộ trong tên khả năng). Thứ người
        // dùng nhìn thấy ở một ô nay là CẶP (nhãn, dòng phụ), nên phép "không hai ô nào giống hệt nhau" phải chạy
        // trên đúng cặp đó. So mỗi nhãn sẽ đỏ với 18 cặp vừa được chữa đúng cách; so cặp vẫn bắt được ca thật.
        val shown = CapabilityCatalog.all().map { it.displayLabel to it.displaySub }
        val dup = shown.groupBy { it }.filterValues { it.size > 1 }.keys.map { "${it.first} / ${it.second}" }.sorted()
        assertEquals(emptyList<String>(), dup, "còn Ô hiển thị y hệt nhau ⇒ người dùng không phân biệt được ô nào")
    }

    /**
     * Và gợi ý loại KHÔNG được quay lại nằm trong nhãn chính (U6).
     *
     * Bài này canh **nguyên nhân**, không canh đúng bốn chữ: bất kỳ nhãn hiển thị nào mang dấu ` · ` rồi kết bằng một
     * trong bốn gợi ý loại đều là dấu hiệu cơ chế cũ bò trở lại.
     */
    @Test
    fun `goi y loai KHONG nam trong nhan chinh`() {
        val hints = setOf("xem", "bấm", "nhóm", "thẻ", "view", "press", "group", "card")
        // ⚠ `try/finally`: [Strings.current] là biến TOÀN CỤC dùng chung cả JVM test. Trả về VI ở dòng cuối thân hàm
        // thì một assert đỏ sẽ ném TRƯỚC khi tới đó ⇒ mọi bài chạy sau trong cùng JVM đọc nhãn tiếng Anh và đỏ theo
        // dây chuyền, che mất lỗi thật. Dọn trong `finally` để bài đỏ chỉ tố cáo đúng một thứ.
        try {
            listOf(Lang.VI, Lang.EN).forEach { lang ->
                Strings.current = lang
                val leaked = CapabilityCatalog.allIncludingHidden()
                    .map { it.displayLabel }
                    .filter { l -> hints.any { l.endsWith(" · $it") } }
                assertEquals(emptyList<String>(), leaked, "gợi ý loại lọt vào NHÃN CHÍNH ($lang) — nó thuộc dòng phụ")
            }
        } finally {
            Strings.current = Lang.VI
        }
    }

    @Test
    fun `chi them goi y loai o dung cho bi trung`() {
        // [SOÁT] bản cũ ĐÒI danh sách nhãn trùng phải KHÁC RỖNG và phải chứa đúng "Kính trước-trái" — tức nó khoá
        // một hiện trạng SAI: ai sửa gốc (đổi nhãn registry cho khỏi trùng) sẽ làm test đỏ dù vừa làm điều đúng.
        // Luật thật cần khoá: CHỖ TRÙNG thì có gợi ý loại, CHỖ KHÔNG TRÙNG thì nhãn giữ nguyên.
        val colliding = CapabilityCatalog.collidingLabels()

        // Chỗ TRÙNG: hai ô cùng TÊN, phân biệt bằng DÒNG PHỤ (U6 — trước đây gợi ý nằm trong nhãn)
        val readWin = CapabilityCatalog.pick("window_lf")!!
        val writeWin = CapabilityCatalog.pick("win_lf")!!
        assertEquals("Kính trước-trái", readWin.displayLabel, "nhãn chính là TÊN, không mang loại")
        assertEquals("Kính trước-trái", writeWin.displayLabel)
        assertEquals("xem", readWin.displaySub)
        assertEquals("bấm", writeWin.displaySub)

        // Chỗ KHÔNG trùng: giữ NGUYÊN nhãn, KHÔNG thêm dòng phụ cho cả 187 mục
        val soc = CapabilityCatalog.pick("soc")!!
        assertEquals(soc.label, soc.displayLabel, "nhãn không trùng thì không được thêm gì")
        assertEquals("", soc.displaySub, "ô không trùng tên thì không được mọc thêm một dòng chữ")
        assertFalse("Pin (SOC)" in colliding)
    }

    @Test
    fun `nhan goc KHONG bi doi - goi y loai la chuyen TRINH BAY`() {
        // Nhãn gốc là dữ liệu; gợi ý loại chỉ là chuyện trình bày. Trộn hai thứ sẽ làm bẩn bộ đăng ký.
        // U6: chỗ trình bày đó nay là `displaySub`, và `sub` (TRƯỜNG dữ liệu) vẫn rỗng với mọi mục rời.
        val w = CapabilityCatalog.pick("window_lf")!!
        assertEquals("Kính trước-trái", w.label, "nhãn GỐC phải nguyên vẹn")
        assertEquals(w.label, w.displayLabel, "nhãn hiển thị = TÊN, gợi ý không được chen vào")
        assertEquals("", w.sub, "gợi ý loại KHÔNG được ghi vào trường dữ liệu")
        assertEquals("xem", w.displaySub, "nó chỉ xuất hiện ở tầng trình bày")
    }

    // ── U6: mã ẩn khỏi bộ chọn nhưng KHOÁ LƯU vẫn sống ──────────────────────────────────────────

    @Test
    fun `ma an khoi bo chon van tra cuu duoc - khoa luu ben khong duoc mat`() {
        assertTrue(CapabilityCatalog.HIDDEN_FROM_PICKER.isNotEmpty(), "tiền đề: đang có mã cố ý ẩn")
        CapabilityCatalog.HIDDEN_FROM_PICKER.forEach { (id, why) ->
            assertNotNull(
                CapabilityCatalog.pick(id),
                "ẩn khỏi màn chọn KHÔNG được làm mất mã: ô người dùng đã đặt từ bản trước phải tiếp tục vẽ ($id)",
            )
            assertNotNull(CapabilityCatalog.kindOf(id), "mã ẩn vẫn phải phân loại được ($id)")
            assertTrue(why.length >= 20, "$id: ẩn một mục thì phải nói được VÌ SAO, không thì đây là chỗ xoá lén")
            assertTrue(
                CapabilityCatalog.all().none { it.id == id },
                "$id vẫn lọt vào danh sách màn chọn — lọc phải nằm ở CỬA DUY NHẤT là `all()`",
            )
            assertTrue(
                CapabilityCatalog.allIncludingHidden().any { it.id == id },
                "$id phải còn trong bản kê ĐẦY ĐỦ (nếu không thì không phép kiểm nào còn thấy nó)",
            )
        }
    }

    @Test
    fun `nhan va dau chua kiem tren xe giu dung theo bo dang ky goc`() {
        val recirc = CapabilityCatalog.pick("recirc")!!
        assertEquals("Lấy gió trong", recirc.label, "nhãn phải lấy từ bộ đăng ký gốc")
        assertEquals(EvidenceTier.OVERDRIVE, recirc.tier, "lấy gió trong CHƯA kiểm trên xe owner")
        assertTrue(recirc.needsBadge, "phải mang dấu chưa-kiểm để không hứa quá (R10)")
        val soc = CapabilityCatalog.pick("soc")!!
        assertFalse(soc.needsBadge, "phần trăm pin đã chạy thật ⇒ không cần dấu")
    }
}

/**
 * Khoá phần QUYẾT ĐỊNH của bảng áp suất lốp (W4 — spec §4.4, R6–R8).
 * Ngưỡng là quyết định của agent (OQ1) — test dưới đây khoá HÀNH VI theo ngưỡng, nên đổi ngưỡng thì test đỏ đúng chỗ.
 */
class TyreBoardTest {

    private fun tyres(fl: Double? = null, fr: Double? = null, rl: Double? = null, rr: Double? = null,
                      tfl: Int? = null) =
        CarStatus.Tyres(pFlKpa = fl, pFrKpa = fr, pRlKpa = rl, pRrKpa = rr, tFlC = tfl)

    @Test
    fun `luon tra ve dung bon banh theo thu tu co dinh`() {
        val r = TyreBoard.readings(tyres())
        assertEquals(4, r.size, "phải luôn 4 bánh để bố cục không nhảy")
        assertEquals(
            listOf(TyreCorner.FRONT_LEFT, TyreCorner.FRONT_RIGHT, TyreCorner.REAR_LEFT, TyreCorner.REAR_RIGHT),
            r.map { it.corner },
        )
    }

    @Test
    fun `off-car khong doc duoc gi thi khong biet chu khong bia`() {
        val r = TyreBoard.readings(tyres())
        assertTrue(r.all { it.status == TyreStatus.UNKNOWN }, "chưa đọc được ⇒ UNKNOWN")
        assertTrue(r.all { it.pressureKpa == null && it.tempC == null }, "KHÔNG được bịa số")
        assertFalse(TyreBoard.anyAlert(tyres()), "không biết gì thì KHÔNG được kêu cảnh báo")
    }

    @Test
    fun `bon banh deu binh thuong thi khong canh bao`() {
        val r = TyreBoard.readings(tyres(240.0, 240.0, 235.0, 235.0))
        assertTrue(r.all { it.status == TyreStatus.OK }, "2.35–2.40 bar là bình thường, chênh 0.05 < 0.3")
        assertFalse(TyreBoard.anyAlert(tyres(240.0, 240.0, 235.0, 235.0)))
    }

    @Test
    fun `banh non duoc chi ra dung banh do`() {
        val r = TyreBoard.readings(tyres(240.0, 240.0, 240.0, 180.0))
        assertEquals(TyreStatus.LOW, r[3].status, "1.8 bar < 2.0 ⇒ non")
        assertTrue(r.take(3).all { it.status == TyreStatus.OK }, "ba bánh kia vẫn bình thường")
        assertTrue(TyreBoard.anyAlert(tyres(240.0, 240.0, 240.0, 180.0)))
    }

    @Test
    fun `banh qua cang duoc chi ra`() {
        val r = TyreBoard.readings(tyres(330.0, 240.0, 240.0, 240.0))
        assertEquals(TyreStatus.HIGH, r[0].status, "3.3 bar > 3.2 ⇒ quá căng")
    }

    @Test
    fun `lech thi chi danh dau banh THAP NHAT chu khong danh dau banh cao`() {
        // 2.6 / 2.6 / 2.6 / 2.2 — tất cả trong khoảng bình thường, nhưng chênh 0.4 ≥ 0.3
        val r = TyreBoard.readings(tyres(260.0, 260.0, 260.0, 220.0))
        assertEquals(TyreStatus.UNEVEN, r[3].status, "bánh thấp nhất bị đánh dấu lệch")
        assertTrue(r.take(3).all { it.status == TyreStatus.OK },
            "KHÔNG gắn cảnh báo lên bánh đang đủ hơi — gây hiểu sai")
    }

    @Test
    fun `non uu tien hon lech khi ca hai cung dung`() {
        val r = TyreBoard.readings(tyres(260.0, 260.0, 260.0, 190.0))
        assertEquals(TyreStatus.LOW, r[3].status, "vừa non vừa lệch ⇒ báo NON (cụ thể hơn)")
    }

    @Test
    fun `mot banh duy nhat doc duoc thi khong the ket luan lech`() {
        val r = TyreBoard.readings(tyres(fl = 240.0))
        assertEquals(TyreStatus.OK, r[0].status, "một bánh thì không có gì để so ⇒ không kêu lệch")
        assertTrue(r.drop(1).all { it.status == TyreStatus.UNKNOWN })
    }

    // ── KẾT LUẬN TỔNG (kiểm toán UX mục 1: ô phải trả lời "lốp tao ổn không") ────────────────────

    @Test
    fun `off-car ket luan noi la CHUA DOC DUOC, khong noi la on`() {
        assertEquals(
            "chưa đọc được áp suất", TyreBoard.verdict(TyreBoard.readings(tyres())),
            "off-car mà báo 'ổn' là BỊA — và là kiểu bịa tệ nhất, vì nó trấn an sai",
        )
    }

    @Test
    fun `bon banh binh thuong thi ket luan on`() {
        assertEquals("lốp ổn", TyreBoard.verdict(TyreBoard.readings(tyres(240.0, 240.0, 235.0, 235.0))))
    }

    @Test
    fun `ket luan dem tung loai sai va neu thu nguy truoc`() {
        // 1.8 bar (non) + 3.3 bar (căng) ⇒ phải nêu CẢ HAI, non trước (thứ tự ưu tiên của TyreStatus).
        val r = TyreBoard.readings(tyres(180.0, 330.0, 240.0, 240.0))
        assertEquals("1 bánh non · 1 bánh căng", TyreBoard.verdict(r))
    }

    @Test
    fun `hai banh cung loi thi dem gop lai`() {
        assertEquals("2 bánh non", TyreBoard.verdict(TyreBoard.readings(tyres(180.0, 190.0, 240.0, 240.0))))
    }

    @Test
    fun `ket luan dung tu vung cua TyreStatus, khong co bang chu thu hai`() {
        // Khoá giao kèo: chữ trong kết luận phải là chính [TyreStatus.reason]. Nếu ai thêm một bảng chữ riêng cho
        // kết luận thì hai chỗ sẽ nói khác nhau về cùng một trạng thái.
        val v = TyreBoard.verdict(TyreBoard.readings(tyres(260.0, 260.0, 260.0, 220.0)))
        assertTrue(v.contains(TyreStatus.UNEVEN.reason!!), "phải dùng chữ 'lệch' của chính TyreStatus: $v")
    }

    @Test
    fun `nhiet do di kem khi doc duoc va mang dau chua kiem tren xe`() {
        val r = TyreBoard.readings(tyres(fl = 240.0, tfl = 31))
        assertEquals(31, r[0].tempC, "nhiệt độ phải chảy qua")
        assertEquals(EvidenceTier.NEEDS_CAR, TyreBoard.tempTier, "nhiệt lốp CHƯA kiểm trên xe")
        assertTrue(TyreBoard.tempTier.let { it == EvidenceTier.NEEDS_CAR }, "⇒ UI phải hiện dấu/mờ")
    }

    @Test
    fun `hai banh bang nhau o muc thap nhat thi CA HAI bi danh dau lech`() {
        // Ca biên của luật "chỉ đánh dấu bánh THẤP NHẤT": khi hai bánh CÙNG thấp nhất thì cả hai ĐỀU là thấp nhất,
        // bỏ một bánh ra sẽ là chọn bừa. Chênh 0.4 ≥ 0.3 ⇒ lệch.
        val r = TyreBoard.readings(tyres(260.0, 260.0, 220.0, 220.0))
        assertEquals(TyreStatus.UNEVEN, r[2].status, "bánh sau-trái cũng ở mức thấp nhất")
        assertEquals(TyreStatus.UNEVEN, r[3].status, "bánh sau-phải cũng ở mức thấp nhất")
        assertTrue(r.take(2).all { it.status == TyreStatus.OK }, "hai bánh cao KHÔNG bị đánh dấu")
    }

    @Test
    fun `chenh dung bang nguong thi da tinh la lech`() {
        // Khoá phía nào của ngưỡng: tài liệu ghi "chênh TỪ mức này" ⇒ phải là ≥, không phải >.
        // ⚠ Phải gọi THẲNG statusOf: đi qua `readings` thì không tới được ca bằng nhau tuyệt đối — kPa chia 100 ra số
        // nhị phân không chẵn nên 2.6−2.3 = 0.30000000000000004, tức đã LỚN HƠN ngưỡng, và phép thử biên hoá vô nghĩa
        // (đã đo: bản đầu của test này KHÔNG bắt được phép thử phá `>=` → `>`).
        assertEquals(
            TyreStatus.UNEVEN,
            TyreBoard.statusOf(bar = 2.3, spread = TyreBoard.SPREAD_BAR, minBar = 2.3),
            "chênh ĐÚNG BẰNG ngưỡng đã là lệch (≥, không phải >)",
        )
        assertEquals(
            TyreStatus.OK,
            TyreBoard.statusOf(bar = 2.3, spread = TyreBoard.SPREAD_BAR - 0.01, minBar = 2.3),
            "dưới ngưỡng một chút thì KHÔNG kêu lệch",
        )
    }

    @Test
    fun `chi hai banh doc duoc thi van ket luan duoc lech`() {
        val r = TyreBoard.readings(tyres(fl = 260.0, rr = 220.0))
        assertEquals(TyreStatus.UNEVEN, r[3].status, "hai bánh là đủ để so")
        assertEquals(TyreStatus.OK, r[0].status, "bánh cao vẫn bình thường")
        assertTrue(r.slice(1..2).all { it.status == TyreStatus.UNKNOWN }, "hai bánh chưa đọc vẫn UNKNOWN")
    }

    @Test
    fun `nguong nam dung mot cho de doi mot dong`() {
        // Khoá quan hệ giữa 3 ngưỡng, không khoá con số — owner sửa số thì test này vẫn đúng
        assertTrue(TyreBoard.LOW_BAR < TyreBoard.HIGH_BAR, "ngưỡng non phải nhỏ hơn ngưỡng căng")
        assertTrue(TyreBoard.SPREAD_BAR > 0.0, "ngưỡng lệch phải dương")
        assertTrue(TyreBoard.HIGH_BAR - TyreBoard.LOW_BAR > TyreBoard.SPREAD_BAR,
            "dải bình thường phải rộng hơn ngưỡng lệch, nếu không mọi xe đều bị kêu lệch")
    }

    // ── R8 (nợ gói 2): bảng phải nói SAI CÁI GÌ ─────────────────────────────────────────────────────────
    // [ĐO] senior review 2026-09-11: `TyreStatus.reason` ra đời cho R8 nhưng KHÔNG có phép kiểm thuần nào — chỉ có
    // một test ở `:app` khoá điều NGƯỢC LẠI (chuỗi không được viết trong bộ vẽ). Tức nghiệm thu của R8 ("mỗi trạng
    // thái ra một chữ ngắn; off-car không có số ⇒ không hiện chữ nào") chưa ai canh.

    @Test
    fun `moi trang thai co van de ra dung mot chu ngan`() {
        assertEquals("non", TyreStatus.LOW.reason)
        assertEquals("căng", TyreStatus.HIGH.reason)
        assertEquals("lệch", TyreStatus.UNEVEN.reason)
        TyreStatus.values().filter { it.alert }.forEach {
            val w = it.reason
            assertNotNull(w, "trạng thái cảnh báo $it phải nói được sai cái gì")
            assertTrue(w!!.isNotBlank() && w.length <= 6,
                "chữ phải NGẮN — nó nằm cạnh con số trong ô nhỏ, dài là bị cắt (đang là '$w')")
        }
    }

    @Test
    fun `binh thuong va chua doc duoc thi KHONG noi gi`() {
        assertNull(TyreStatus.OK.reason, "bánh bình thường không có gì để nói ⇒ không chiếm chỗ")
        assertNull(TyreStatus.UNKNOWN.reason, "chưa đọc được thì KHÔNG được bịa lý do")
        assertTrue(TyreStatus.values().none { !it.alert && it.reason != null },
            "chỉ trạng thái cảnh báo mới có chữ")
    }

    @Test
    fun `off-car khong co so thi khong banh nao co chu`() {
        assertTrue(TyreBoard.readings(tyres()).all { it.status.reason == null },
            "off-car là ca BÌNH THƯỜNG — bảng không được hiện chữ lỗi nào")
    }

    @Test
    fun `chu di kem dung banh dang co van de`() {
        val r = TyreBoard.readings(tyres(240.0, 240.0, 240.0, 180.0))
        assertEquals("non", r[3].status.reason, "bánh 1.8 bar phải nói 'non'")
        assertTrue(r.take(3).all { it.status.reason == null }, "ba bánh kia không được mang chữ lỗi")
    }
}
