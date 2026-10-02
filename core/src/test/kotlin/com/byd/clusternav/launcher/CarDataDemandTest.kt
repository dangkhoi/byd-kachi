package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá bài học H1 (PERF 2026-09-16): vòng poll chỉ được đọc datum **đang hiện**, và phép tính "đang hiện" phải
 * đến từ [HomeUiState] chứ không từ một danh sách chép tay ở tầng vẽ.
 *
 * ⚠ Bài quan trọng nhất ở đây là [demand khong bao gio nuot mot datum dang hien]: một lỗi trong bảng
 * [CarDataDemand.CURATED] KHÔNG làm gì đỏ ở chỗ khác — nó chỉ làm một ô im lặng hiện "—" trên xe.
 */
class CarDataDemandTest {

    private fun state(
        chips: List<String> = emptyList(),
        slots: List<SlotContent> = List(WorkspaceState.SLOT_CAP) { SlotContent.Empty },
        dock: List<String> = emptyList(),
    ) = HomeUiState(
        workspace = WorkspaceState(slots = slots),
        dock = DockConfig(enabled = dock),
        topStrip = TopStripConfig(ids = chips),
    )

    private fun slotsWith(vararg widgetIds: String): List<SlotContent> =
        (listOf(SlotContent.Widget(widgetIds.toList())) +
            List(WorkspaceState.SLOT_CAP - 1) { SlotContent.Empty })

    @Test
    fun `chip tong hop mang du datum cua no`() {
        val d = CarDataDemand.of(state(chips = listOf(TopStripConfig.ENERGY)))
        assertNotNull(d)
        // `chip_energy` vẽ "82% · 418 km" — HAI datum trong một chip; thiếu một cái là chip hiện "—" một nửa.
        assertEquals(setOf("soc", "ev_range_km"), d)
    }

    @Test
    fun `widget dung tay mang du datum cua bo ve`() {
        val d = CarDataDemand.of(state(slots = slotsWith("w_tire")))
        assertNotNull(d)
        assertTrue(d!!.containsAll(listOf("tyre_p_fl", "tyre_p_fr", "tyre_p_rl", "tyre_p_rr")))
        assertTrue(d.containsAll(listOf("tyre_t_fl", "tyre_t_fr", "tyre_t_rl", "tyre_t_rr")))
    }

    @Test
    fun `o nhom lay dung danh sach reads cua nhom`() {
        val g = CapabilityGroups.ALL.first { it.reads.isNotEmpty() }
        val d = CarDataDemand.of(state(slots = slotsWith(g.id)))
        assertNotNull(d)
        assertTrue(d!!.containsAll(g.reads), "nhóm ${g.id} phải kéo theo mọi mã ĐỌC của nó")
    }

    @Test
    fun `datum thuong tren thanh nut duoc tinh`() {
        val d = CarDataDemand.of(state(dock = listOf("soc")))
        assertEquals(setOf("soc"), d)
    }

    @Test
    fun `nut bam khong keo theo datum nao`() {
        val ctl = ControlRegistry.ALL.first()
        val d = CarDataDemand.of(state(dock = listOf(ctl.id)))
        assertNotNull(d)
        assertTrue(d!!.isEmpty(), "ô nút giữ trạng thái trong RAM, không đọc lại HAL mỗi nhịp")
    }

    // ═══ controlsOf — các Ô ĐIỀU KHIỂN đang hiện MÀ CÓ đường đọc (2026-09-17 · realtime) ══════════════════

    @Test
    fun `controlsOf lay nut co readKey dang hien tren thanh nut`() {
        // `fan`/`temp`/`recirc` có readKey (đường đọc) ⇒ vào tập để poll đọc giá trị THẬT của xe.
        // ⚠ UX4 — `fan` kéo thêm `ac_auto` (mặt TỰ ĐỘNG của chính nó): thiếu nó thì ô không biết xe đang AUTO hay
        // chỉnh tay, và nấc đáy của thang câm. Xem `o co mat tu dong keo theo nut phu vao tap NUT phai doc`.
        val out = CarDataDemand.controlsOf(state(dock = listOf("fan", "temp", "recirc")))
        assertEquals(setOf("fan", "ac_auto", "temp", "recirc"), out)
    }

    @Test
    fun `controlsOf lay ca nut o giua man`() {
        val out = CarDataDemand.controlsOf(state(slots = slotsWith("fan")))
        assertEquals(setOf("fan", "ac_auto"), out, "UX4 — ô giữa màn cũng kéo theo mặt tự động của nút")
    }

    @Test
    fun `controlsOf bo qua nut CHUA co duong doc`() {
        // `windows_close_all` BUTTON không readKey; `readl` cố ý rỗng readKey ⇒ không vào tập (ô lùi về RAM).
        val out = CarDataDemand.controlsOf(state(dock = listOf("windows_close_all", "readl")))
        assertTrue(out.isEmpty(), "nút chưa nối đường đọc không được ép poll đọc: $out")
    }

    @Test
    fun `controlsOf bo qua datum va gói lenh`() {
        // datum thường (soc) + gói lệnh (mac_*) không phải control có readKey ⇒ không vào tập control.
        val out = CarDataDemand.controlsOf(state(dock = listOf("soc"), slots = slotsWith("w_energy")))
        assertTrue(out.isEmpty())
    }

    /**
     * [SOÁT OCR 2026-09-16 · P1] **Hành động của launcher trên thanh nút KHÔNG được tắt cổng H1.**
     *
     * `DockConfig.setEnabled` nhận mọi mã có trong [CapabilityCatalog], và [LauncherActions] là bộ đăng ký thứ
     * SÁU (S4 · R12) — `DockPickerContractTest` khoá đúng ca người dùng đặt *Ứng dụng* · *Cài đặt* lên thanh nút.
     * [CarDataDemand.expand] không biết bộ đăng ký ấy thì [CarDataDemand.of] trả `null` = *"đọc hết"*, tức cổng
     * H1 tắt **im lặng** và mọi nhịp poll quay lại đọc cả bảng datum (đúng 77 % tải HAL mà 1.67 vừa cắt), đồng
     * thời `needsFast(null)` = true bật lại cả vòng 1 Hz. Gương của `nut bam khong keo theo datum nao`.
     */
    @Test
    fun `hanh dong launcher tren thanh nut khong keo theo datum nao`() {
        // F1: `placeable` = ba việc gọi bằng lời + khối lối tắt (`LauncherActions.BLOCKS`) — cả bốn đặt được lên thanh.
        LauncherActions.placeable.forEach { a ->
            val d = CarDataDemand.of(state(dock = listOf(a.id)))
            assertNotNull(d, "mã ${a.id} làm nhu cầu về null ⇒ cổng H1 tắt im lặng, đọc lại cả bảng datum")
            assertTrue(d!!.isEmpty(), "${a.id} không chạm CarControlPort nên không bày một số nào của xe")
            assertFalse(CarDataDemand.needsFast(d), "không có datum nhanh ⇒ vòng 1 Hz phải nằm im")
        }
    }

    /** Ca THẬT của owner: một ô ĐỌC + nút *Ứng dụng* cùng trên thanh — nhu cầu vẫn đúng tập tối thiểu. */
    @Test
    fun `nut Ung dung dung canh mot o doc khong lam phinh nhu cau`() {
        val d = CarDataDemand.of(state(dock = listOf("soc", LauncherActions.APPS)))
        assertEquals(setOf("soc"), d)
    }

    @Test
    fun `ma la khong tinh duoc thi doc het (fail-open)`() {
        // Một mã widget tương lai chưa khai ở CURATED ⇒ phải rơi về "đọc hết", KHÔNG được im lặng bỏ đọc.
        assertNull(CarDataDemand.of(state(slots = slotsWith("w_khong_co_that"))))
    }

    @Test
    fun `man mac dinh khong can nhip nhanh`() {
        val d = CarDataDemand.of(state(chips = TopStripConfig.DEFAULT_IDS))
        assertNotNull(d)
        assertFalse(
            CarDataDemand.needsFast(d),
            // UX5b (2026-09-27): mặc định nay có thêm hai chip GỘP ghế ⇒ 4 datum ghế. Chúng cũng là datum nhịp
            // CHẬM (mức ghế không đổi theo giây), nên kết luận của bài không đổi — nhưng con số datum thì đổi, và
            // đó là thứ phải nói ra: mặc định nay kéo 3 + 4 = 7 datum mỗi nhịp chậm thay vì 4.
            "chip mặc định (pin·bụi·nhiệt ngoài + hai chip ghế) không có datum nhịp nhanh nào ⇒ vòng 1 Hz là thuần lãng phí",
        )
    }

    /**
     * ⚠ Cặp datum của mỗi chip ghế GỘP khai ở [TopStripConfig.SEAT_PAIRS]; [CarDataDemand.CHIPS] là bản **CHÉP TAY**
     * của nó (cố ý — đọc một `val` của `TopStripConfig` lúc nạp `object CarDataDemand` sẽ thêm một cạnh thứ tự khởi
     * tạo giữa hai bộ đăng ký, đúng họ lỗi `BUILT_IN`/`DEFAULT` đã làm 27 bài đỏ). Bản chép thì phải được **ĐO**:
     * lệch một mã ⇒ chip hiện "—" một nửa trên xe mà không có gì đỏ.
     */
    @Test
    fun `cap datum cua chip ghe khop SEAT_PAIRS`() {
        TopStripConfig.SEAT_PAIRS.forEach { (chip, pair) ->
            assertEquals(
                setOf(pair.first, pair.second), CarDataDemand.CHIPS[chip],
                "bảng nhu cầu của chip '$chip' lệch khỏi SEAT_PAIRS ⇒ một nửa chip sẽ không bao giờ được đọc",
            )
        }
        assertEquals(
            5, CarDataDemand.CHIPS.size,
            "năm chip TỔNG HỢP (bụi · nhiệt ngoài · năng lượng · ghế lái · ghế phụ) — thêm chip thì khai nhu cầu",
        )
    }

    @Test
    fun `o toc do bat lai nhip nhanh`() {
        val d = CarDataDemand.of(state(slots = slotsWith("w_speed")))
        assertTrue(CarDataDemand.needsFast(d))
    }

    @Test
    fun `khong tinh duoc thi van chay nhip nhanh`() {
        assertTrue(CarDataDemand.needsFast(null))
    }

    @Test
    fun `demand khong bao gio nuot mot datum dang hien`() {
        // Mọi mã trong CURATED/CHIPS phải là datum THẬT: gõ sai một mã ở bảng = ô câm trên xe mà không ai biết.
        (CarDataDemand.CURATED.values + CarDataDemand.CHIPS.values).flatten().forEach { id ->
            assertNotNull(TelemetryRegistry.byId(id), "mã '$id' trong bảng nhu cầu không có trong TelemetryRegistry")
        }
        WidgetRegistry.ALL.forEach { w ->
            assertTrue(w.id in CarDataDemand.CURATED, "widget ${w.id} chưa khai nhu cầu (sẽ rơi về đọc-hết)")
        }
        // Widget đọc dữ liệu XE thì bảng không được rỗng — rỗng nghĩa là "không cần đọc gì", tức ô luôn "—".
        WidgetRegistry.ALL.filter { it.kind != WidgetKind.LOCAL }.forEach { w ->
            assertTrue(CarDataDemand.CURATED.getValue(w.id).isNotEmpty(), "widget xe ${w.id} khai nhu cầu RỖNG")
        }
    }

    /**
     * [SOÁT P1-1 · 2026-09-16] Đường câu hỏi bằng giọng ghim một datum NGOÀI màn vào nhu cầu, và ghim phải **tự
     * gỡ** — kể cả khi việc bên trong ném.
     *
     * Nếu ghim không gỡ: cái tải mà H1 vừa cắt mọc lại im lặng, chỉ lộ ra ở bộ đếm sau hàng chục phút. Nếu ghim
     * **thay** thay vì **hợp**: một nhịp poll chạy song song sẽ bỏ đói đúng những ô đang hiện trên màn.
     */
    @Test
    fun `ghim datum ngoai man la HOP va tu go`() {
        val h = CarDataDemand.Holder()
        h.set(setOf("soc"))
        val inside = h.withExtra(setOf("speed")) { h.get() }
        assertEquals(setOf("soc", "speed"), inside, "ghim phải HỢP với nhu cầu màn, không thay nó")
        assertEquals(setOf("soc"), h.get(), "ghim phải tự gỡ sau lượt đọc")

        runCatching { h.withExtra(setOf("gear")) { throw IllegalStateException("đọc hỏng") } }
        assertEquals(setOf("soc"), h.get(), "việc bên trong ném mà ghim còn lại ⇒ tải mọc lại im lặng")

        h.set(null)
        assertNull(h.withExtra(setOf("speed")) { h.get() }, "chưa tính được nhu cầu thì vẫn là ĐỌC HẾT, không thu hẹp")
    }

    @Test
    fun `FAST_IDS khop dung danh sach ma readFast doc`() {
        // Hai nơi (CarDataDemand.FAST_IDS và CarDataAdapter.readFast) phải nói cùng một danh sách; lệch nhau thì
        // một datum nhanh lên màn mà vòng nhanh không bao giờ được bật lại.
        // Đo qua chính cổng nhu cầu: cho nhu cầu RỖNG thì MỌI id của `readFast` bị bỏ qua, và bộ đếm
        // [KachiPerf.Counter.HAL_SKIP_OFFSCREEN] nói đúng có bao nhiêu id trong hàm ấy.
        val table = HalBindingTable(FakeHalGateway())
        val before = KachiPerf.value(KachiPerf.Counter.HAL_SKIP_OFFSCREEN)
        CarDataAdapter(table, demand = { emptySet() }).readFast(CarStatus())
        val skipped = KachiPerf.value(KachiPerf.Counter.HAL_SKIP_OFFSCREEN) - before
        assertEquals(
            CarDataDemand.FAST_IDS.size.toLong(), skipped,
            "readFast đọc $skipped datum nhưng FAST_IDS khai ${CarDataDemand.FAST_IDS.size}",
        )
        // Chiều QUAN TRỌNG hơn: mọi id mà `readFast` đọc phải NẰM TRONG FAST_IDS. Thiếu một id ở đây nghĩa là ô
        // dùng nó lên màn mà vòng nhanh vẫn ngủ ⇒ số đứng im, không ai báo lỗi.
        val before2 = KachiPerf.value(KachiPerf.Counter.HAL_SKIP_OFFSCREEN)
        CarDataAdapter(table, demand = { CarDataDemand.FAST_IDS }).readFast(CarStatus())
        assertEquals(
            before2, KachiPerf.value(KachiPerf.Counter.HAL_SKIP_OFFSCREEN),
            "readFast đọc một id KHÔNG có trong FAST_IDS",
        )
    }

    // ── UX4 · datum BẠN ĐỒNG HÀNH + nút phụ của ô có mặt tự động ─────────────────────────────────

    /**
     * ⚠ Bài khoá đúng khâu **chết người** của UX4: không có nó thì mọi thứ khác chỉ là chữ chết — chip `ac_wind`
     * một mình không bao giờ đọc `ac_wind_auto` ⇒ chữ AUTO **không bao giờ** hiện ra, im lặng (CLAUDE.md §8).
     */
    @Test
    fun `chip gio keo theo chi bao AUTO, du dat bang duong nao`() {
        assertEquals(
            setOf("ac_wind", "ac_wind_auto"), CarDataDemand.of(state(chips = listOf("ac_wind"))),
            "datum gió trên thanh trên phải kéo theo chỉ báo auto",
        )
        // ⚠ Và phải nổ cho CẢ đường Ô NHÓM: `expand` return ngay ở nhánh đầu khớp, nên nếu bảng bạn-đồng-hành áp
        // trong `expand` thì ô nhóm hiện "1" trong khi chip hiện "AUTO 1" — hai bề mặt nói hai điều.
        val group = CapabilityGroups.ALL.firstOrNull { "ac_wind" in it.reads }
        assertNotNull(group) { "tiền đề: có một nhóm khả năng bày mức gió" }
        assertTrue(
            CarDataDemand.of(state(slots = slotsWith(group!!.id)))!!.contains("ac_wind_auto"),
            "ô nhóm cũng phải kéo theo chỉ báo auto",
        )
    }

    @Test
    fun `bang ban dong hanh chi chua datum CO THAT`() {
        CarDataDemand.COMPANION.forEach { (k, v) ->
            assertNotNull(TelemetryRegistry.byId(k)) { "$k không phải datum" }
            v.forEach { assertNotNull(TelemetryRegistry.byId(it)) { "$it (bạn của $k) không phải datum" } }
        }
    }

    /** Ô stepper có mặt tự động phải đọc CẢ nút phụ, nếu không `ClimateAuto` chỉ nhận `null` và ô câm. */
    @Test
    fun `o co mat tu dong keo theo nut phu vao tap NUT phai doc`() {
        val fan = ControlRegistry.byId("fan")!!
        assertEquals("ac_auto", fan.autoId, "tiền đề: gió khai mặt tự động")
        assertEquals(setOf("fan", "ac_auto"), CarDataDemand.controlsOf(state(dock = listOf("fan"))))
        // Nút KHÔNG khai autoId thì không kéo theo gì — generic bằng dữ liệu, không nhánh rẽ theo mã.
        assertEquals(setOf("temp"), CarDataDemand.controlsOf(state(dock = listOf("temp"))))
    }
}
