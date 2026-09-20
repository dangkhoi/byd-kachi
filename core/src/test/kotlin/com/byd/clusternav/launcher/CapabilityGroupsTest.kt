package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá **12 NHÓM KHẢ NĂNG** (G1 — spec `kachi-capability-groups.html` §4.1/§4.2/§4.3).
 *
 * Bài quan trọng nhất là `moi thanh vien deu ton tai that`: nó chặn loại lỗi mà tài liệu KHÔNG chặn được — nhóm trỏ
 * vào một mã bịa hoặc một mã đã đổi tên. Hậu quả im lặng: ô hiện ra thiếu thành viên (bảng lốp còn 3 bánh) mà không
 * có thông báo nào. Dự án đã gặp đúng chuyện này ở khoá `recirc_on_start` (spec ghi một tên, mã nguồn tên khác).
 *
 * Bài thứ hai đáng nói là `nhom KHONG duoc lot len thanh trang thai`: nhóm khai [CapabilityKind.READ] nên nó **đủ điều
 * kiện** lên thanh trên theo luật cũ; chỉ có một chốt riêng ngăn lại. Không có bài này thì gỡ chốt đó chẳng ai biết.
 */
class CapabilityGroupsTest {

    // ── §4.1: đủ nhóm, thành viên là mã THẬT ─────────────────────────────────────────────────────

    @Test
    fun `co dung 9 nhom theo bang spec`() {
        assertEquals(
            9, CapabilityGroups.ALL.size,
            "bảng §4.1 chốt 9 nhóm (2026-09-16 owner gỡ ADAS/an toàn — trước đó 12, bỏ g_adas · g_occupants · " +
                "g_parking). Thêm/bớt nhóm là đổi thứ owner đã duyệt ⇒ sửa spec trước, đừng nới test",
        )
        // Ba mã nhóm đã gỡ KHÔNG được mọc lại — owner gỡ chúng vì lý do AN TOÀN, không phải vì gọn.
        listOf("g_adas", "g_occupants", "g_parking").forEach {
            assertNull(CapabilityGroups.byId(it), "nhóm ADAS/an toàn '$it' đã gỡ theo lệnh owner 2026-09-16")
        }
    }

    @Test
    fun `moi thanh vien XEM deu ton tai that trong TelemetryRegistry`() {
        val bad = CapabilityGroups.ALL.flatMap { g ->
            g.reads.filter { TelemetryRegistry.byId(it) == null }.map { "${g.id} → $it" }
        }
        assertEquals(
            emptyList<String>(), bad,
            "nhóm trỏ vào datum KHÔNG tồn tại ⇒ ô thiếu thành viên mà không báo gì",
        )
    }

    @Test
    fun `moi thanh vien BAM deu ton tai that trong ControlRegistry hoac ActionMacros`() {
        val bad = CapabilityGroups.ALL.flatMap { g ->
            g.writes
                .filter { ControlRegistry.byId(it) == null && ActionMacros.byId(it) == null }
                .map { "${g.id} → $it" }
        }
        assertEquals(emptyList<String>(), bad, "nhóm trỏ vào nút/gói lệnh KHÔNG tồn tại ⇒ nút bấm không làm gì")
    }

    @Test
    fun `khong nhom nao rong va moi nhom co nhan icon`() {
        CapabilityGroups.ALL.forEach { g ->
            assertTrue(g.members.isNotEmpty(), "nhóm ${g.id} rỗng ⇒ ô hiện ra một khung trắng")
            assertTrue(g.label.isNotBlank(), "nhóm ${g.id} thiếu nhãn")
            // Đầu `:core` của HỢP ĐỒNG tên icon với `KachiTheme.iconRes` (T2). Bên đó tra không thấy thì rơi vào
            // `else -> 0` = ô nhóm KHÔNG có icon, và sai đó **im lặng** (không ném gì). Nên khoá tiền tố ở đây, còn
            // việc "cả 12 tên tra ra drawable" thì bài canh phía `:app` giữ.
            assertTrue(
                g.icon.startsWith("ic-group-"),
                "icon nhóm phải theo hợp đồng 'ic-group-*' (KachiTheme.iconRes), đang là '${g.icon}'",
            )
        }
        assertEquals(
            CapabilityGroups.ALL.size, CapabilityGroups.ALL.map { it.icon }.distinct().size,
            "mỗi nhóm một icon riêng — hai nhóm cùng icon thì icon không giúp phân biệt gì (bệnh U1 đi chữa)",
        )
    }

    // ── R1 nghiệm thu: "đặt 1 ô Lốp thấy đủ 4 bánh; đặt 1 ô Kính thấy đủ 4 kính" ──────────────────

    @Test
    fun `nhom Lop chua du 8 ma lop - 4 ap va 4 nhiet`() {
        val tyres = CapabilityGroups.byId("g_tyres")!!
        assertEquals(8, tyres.reads.size, "phải đủ 4 áp + 4 nhiệt — đây LÀ lý do nhóm tồn tại")
        listOf("fl", "fr", "rl", "rr").forEach { corner ->
            assertTrue("tyre_p_$corner" in tyres.reads, "thiếu áp suất bánh $corner")
            assertTrue("tyre_t_$corner" in tyres.reads, "thiếu nhiệt độ bánh $corner")
        }
        assertEquals(WidgetShape.BOARD, tyres.shape, "4 bánh phải vẽ theo hình học thật của xe (§4.3)")
        assertFalse(tyres.hasWrites, "lốp không có gì để bấm")
    }

    @Test
    fun `nhom Kinh chua du 4 kinh va bam duoc ca 4 lan hai goi lenh`() {
        val win = CapabilityGroups.byId("g_windows")!!
        assertEquals(listOf("window_lf", "window_rf", "window_lr", "window_rr"), win.reads, "phải đủ 4 kính")
        listOf("win_lf", "win_rf", "win_lr", "win_rr").forEach {
            assertTrue(it in win.writes, "thiếu nút $it — R3 đòi XEM và BẤM trong CÙNG một ô")
        }
        assertTrue("mac_win_open_all" in win.writes && "mac_win_close_all" in win.writes, "thiếu gói mở/đóng hết")
        // Cố ý KHÔNG dùng nút gộp `windows_all`: nó CHƯA kiểm trên xe, còn 4 nút riêng đã chạy thật.
        assertFalse("windows_all" in win.writes, "nút gộp chưa-kiểm KHÔNG được thay hai gói dựng từ nút đã chạy thật")
        assertEquals(WidgetShape.STRIP, win.shape)
    }

    /**
     * ## ⚠ U9 pha 2 — luật NỚI từ `STRIP` sang `STRIP + BOARD`, và đây là lý do (không phải "nới cho xanh")
     * Nhóm *Cửa & khoang* đổi sang [WidgetShape.BOARD] để vẽ hình xe (owner 2026-09-13). Hàng nút chưa bao giờ phụ
     * thuộc vào hình — `GroupTileView.bind` chỉ hỏi `model.hasActions`; thứ từng thiếu là **chỗ**, vì ô vẽ Canvas
     * khai `MATCH_PARENT` nuốt trọn phần cao còn lại. Pha 2 chữa bằng `weight` cho thân ô BOARD-có-nút ⇒
     * `LinearLayout` đo hàng nút (chi phí CỐ ĐỊNH) trước.
     *
     * [WidgetShape.CARD] **vẫn** đứng ngoài: chưa nhóm nào cần, và không có ảnh chụp nào chứng minh hàng nút còn
     * nguyên ở đó.
     */
    @Test
    fun `chi nhom co hang nut moi mang nut - vi bo ve CARD khong co hang nut`() {
        val wrong = CapabilityGroups.ALL
            .filter { it.hasWrites && it.shape !in CapabilityGroups.SHAPES_WITH_ACTIONS }.map { it.id }
        assertEquals(emptyList<String>(), wrong, "nhóm này khai nút nhưng bộ vẽ của nó không có chỗ đặt nút")
        assertEquals(
            setOf(WidgetShape.STRIP, WidgetShape.BOARD), CapabilityGroups.SHAPES_WITH_ACTIONS,
            "thêm một hình vào danh sách có-hàng-nút là một quyết định về BỐ CỤC — phải có chỗ đo được cho hàng nút " +
                "trước, không phải nới luật cho xanh",
        )
        assertEquals(
            WidgetShape.BOARD, CapabilityGroups.DOORS.shape,
            "U9 pha 2: *Cửa & khoang* phải là BOARD (hình xe), không quay về dải STRIP mười ô chữ giống nhau",
        )
        assertEquals(
            listOf("g_windows", "g_doors", "g_lights"),
            CapabilityGroups.ALL.filter { it.hasWrites }.map { it.id },
            "đúng 3 nhóm mang nút (kính · cửa & khoang · đèn) — spec §4.1 ghi '+ nút' cho ba dòng đó",
        )
        assertTrue(
            CapabilityGroups.ALL.all { it.shape in CapabilityGroups.SHAPES },
            "chỉ có 3 bộ vẽ (BOARD/STRIP/CARD); hình khác thì không bộ nào nhận và ô ra trống",
        )
    }

    // ── Không gian mã phẳng: nhóm KHÔNG được trùng 4 bộ mã cũ ────────────────────────────────────

    @Test
    fun `ma nhom khong trung voi bat ky ma nao dang co`() {
        val existing = TelemetryRegistry.ALL.map { it.id } + ControlRegistry.ALL.map { it.id } +
            WidgetRegistry.ALL.map { it.id } + ActionMacros.ALL.map { it.id }
        val clash = CapabilityGroups.ALL.map { it.id }.filter { it in existing }
        assertEquals(
            emptyList<String>(), clash,
            "mã trùng ⇒ nối chéo âm thầm: ô tưởng bày nhóm lại bày một datum (hoặc ngược lại)",
        )
        assertTrue(
            CapabilityGroups.ALL.all { it.id.startsWith(CapabilityGroups.ID_PREFIX) },
            "tiền tố '${CapabilityGroups.ID_PREFIX}' là thứ làm việc trùng mã KHÔNG THỂ xảy ra do cấu tạo",
        )
        // Và phép kiểm gốc của dự án cũng phải phủ nhóm, không chỉ bài này.
        assertEquals(emptyList<String>(), CapabilityCatalog.collisions(), "collisions() phải xét cả nhóm")
    }

    // ── §4.2 + RW0: nhóm tra được như một khả năng, và mục rời còn nguyên ─────────────────────────

    @Test
    fun `CapabilityCatalog tra ra nhom nhu mot kha nang`() {
        CapabilityGroups.ALL.forEach { g ->
            assertEquals(CapabilityKind.READ, CapabilityCatalog.kindOf(g.id), "nhóm ${g.id} phải phân loại được")
            val pick = CapabilityCatalog.pick(g.id)
            assertNotNull(pick, "nhóm ${g.id} phải tra ra được ⇒ mới đặt được vào ô giữa màn")
            assertEquals(g.label, pick!!.label)
            assertEquals(g.icon, pick.icon)
            assertEquals(g.domain, pick.domain, "nhóm phải có domain, nếu không nó biến mất khỏi màn chọn")
            assertTrue(pick.group, "phải đánh dấu là NHÓM để UI dựng bộ vẽ riêng")
            assertFalse(pick.curated, "nhóm KHÔNG phải widget dựng tay — hai khái niệm khác nhau")
            assertFalse(CapabilityCatalog.isWrite(g.id), "nhóm ra ô XEM (có nội dung đọc), không phải ô một-nút")
        }
        // Có đường từ tay người dùng tới nhóm (bài học "vẽ được ≠ đặt được" — CapabilityReachabilityTest).
        val reachable = CapabilityCatalog.byDomain().flatMap { it.second }.map { it.id }.toSet()
        val hidden = CapabilityGroups.ALL.map { it.id }.filterNot { it in reachable }
        assertEquals(emptyList<String>(), hidden, "nhóm không bày ở màn chọn nào ⇒ người dùng không đặt được")
    }

    @Test
    fun `man chon bay NHOM len TRUOC muc roi`() {
        // §4.2: nhóm là thứ người dùng GẶP TRƯỚC. Sắp ở `:core` thì mọi màn chọn tự đúng — hai màn không thể sắp khác
        // nhau, và không màn nào phải nhớ tự sắp.
        CapabilityCatalog.byDomain().forEach { (domain, picks) ->
            val firstNonGroup = picks.indexOfFirst { !it.group }
            val lastGroup = picks.indexOfLast { it.group }
            if (lastGroup >= 0 && firstNonGroup >= 0) {
                assertTrue(lastGroup < firstNonGroup, "nhóm của $domain phải nằm trước mọi mục rời")
            }
        }
    }

    // ── Nhóm KHÔNG được lên thanh trạng thái — MỘT BÀI CHO MỖI LỚP CHẶN (SOÁT P3-6) ──────────────
    //
    // Tài liệu dự án ghi *"nhóm bị chặn khỏi thanh trạng thái ở 4 lớp"*. Trước lượt soát, cả bốn lớp nằm trong **một**
    // bài, nên (a) một lớp vỡ thì thông báo không nói lớp nào, và (b) không ai đếm được là 4 hay 1 — con số trong tài
    // liệu không có gì đối chiếu. Tách ra một bài cho mỗi lớp: số bài **chính là** con số tài liệu nói, và mỗi bài đỏ
    // nêu đích danh lớp bị hở. Lượt tách cũng lộ ra lớp thứ **năm** chưa ai canh: [TopStripConfig.decode].

    /** Lớp 1 — phép **quyết định** (`isChippable`): nguồn duy nhất của luật, bốn lớp dưới đều hỏi nó. */
    @Test
    fun `chan nhom len chip - lop 1 phep quyet dinh`() {
        CapabilityGroups.ALL.forEach { g ->
            assertFalse(
                TopStripConfig.isChippable(g.id),
                "chip ~24dp không vẽ được nhóm ${g.id}, và 3/9 nhóm mang nút ⇒ đích chạm 24dp bắn lệnh xe",
            )
        }
    }

    /** Lớp 2 — **màn chọn** (`choices`): bày ra rồi từ chối = nút chết (bấm mà không có gì xảy ra). */
    @Test
    fun `chan nhom len chip - lop 2 man chon khong bay ra`() {
        val shown = TopStripConfig.choices().map { it.id }
        CapabilityGroups.ALL.forEach { g ->
            assertTrue(g.id !in shown, "màn chọn chip KHÔNG được bày nhóm ${g.id}")
        }
    }

    /** Lớp 3 — **cửa vào cấu hình bền** (`setEnabled`): đường mà cú chạm của người dùng đi qua. */
    @Test
    fun `chan nhom len chip - lop 3 cua vao cau hinh ben`() {
        CapabilityGroups.ALL.forEach { g ->
            assertFalse(
                TopStripConfig.DEFAULT.setEnabled(g.id, true).has(g.id),
                "cấu hình bền phải từ chối nhóm ${g.id}",
            )
        }
    }

    /** Lớp 4 — **chốt tại LỚP** (`init require`): dựng thẳng cũng phải nổ, không dựa vào "mọi chỗ gọi đều nhớ". */
    @Test
    fun `chan nhom len chip - lop 4 chot tai lop`() {
        CapabilityGroups.ALL.forEach { g ->
            assertNotNull(
                runCatching { TopStripConfig(listOf(g.id)) }.exceptionOrNull(),
                "dựng thẳng TopStripConfig với mã nhóm ${g.id} phải bị chặn ngay tại lớp",
            )
        }
    }

    /**
     * Lớp 5 — **cửa đọc từ đĩa** (`decode`). ⚠ Lớp này TRƯỚC lượt soát P3-6 **không có bài nào canh**.
     *
     * Đây là đường của dữ liệu người dùng sửa tay và của **bản cũ**: một máy đã chạy bản trước G1 (lúc nhóm chưa bị
     * chặn, hoặc chỉ cần một chuỗi gõ tay) sẽ có `g_tyres` nằm trong `top_strip` trên đĩa. `decode` phải **bỏ mục đó**
     * — không bỏ thì `TopStripConfig(...)` ném ngay lúc nạp ⇒ **launcher sập khi mở**, chứ không phải một chip xấu.
     */
    @Test
    fun `chan nhom len chip - lop 5 cua doc tu dia`() {
        val cfg = TopStripConfig.decode("chip_pm25,g_tyres,tyre_p_fl")
        assertEquals(listOf("chip_pm25", "tyre_p_fl"), cfg.ids, "decode phải bỏ MỤC nhóm, giữ các chip hợp lệ")
        // Chuỗi chỉ có nhóm ⇒ lùi về mặc định, KHÔNG ném và KHÔNG để thanh trên trắng.
        assertEquals(TopStripConfig.DEFAULT.ids, TopStripConfig.decode("g_tyres,g_lights").ids)
    }

    /**
     * Và luật cũ KHÔNG bị vá quá tay: mục ĐỌC rời vẫn lên được thanh trên, mặc định vẫn đúng 3 chip.
     *
     * ⚠ (V) FEATURE-FILTER 2026-09-17: mốc cũ là `tyre_p_fl` — tám ô lốp LẺ nay **ẩn khỏi bộ chọn**
     * ([CapabilityCatalog.HIDDEN_FROM_PICKER]) theo lệnh owner *"gôm lại thành 1 widget"*, nên nó không còn
     * chứng minh được vế thứ hai. Đổi sang `batt_temp`: cùng là mục ĐỌC rời, không ẩn. Luật *"ẩn ≠ cấm chip"*
     * vẫn giữ — `isChippable` vẫn nói CÓ cho `tyre_p_fl`, chỉ màn chọn không bày nó nữa.
     */
    @Test
    fun `chan nhom len chip - khong va qua tay`() {
        assertTrue(TopStripConfig.isChippable("batt_temp"), "mục ĐỌC rời vẫn phải chip được")
        assertTrue(TopStripConfig.choices().any { it.id == "batt_temp" }, "và màn chọn vẫn phải bày nó")
        assertTrue(TopStripConfig.isChippable("tyre_p_fl"), "ẩn khỏi bộ chọn KHÔNG phải cấm chip (mã vẫn sống)")
        assertEquals(3, TopStripConfig.DEFAULT.ids.size, "mặc định vẫn đúng 3 chip như owner đang thấy")
    }

    @Test
    fun `groupsContaining tra nguoc dung nhom cho ca muc XEM va muc BAM`() {
        assertEquals(listOf("g_tyres"), CapabilityGroups.groupsContaining("tyre_p_rr").map { it.id })
        assertEquals(listOf("g_windows"), CapabilityGroups.groupsContaining("window_lf").map { it.id })
        assertEquals(
            listOf("g_windows"), CapabilityGroups.groupsContaining("win_lf").map { it.id },
            "tra ngược phải thấy cả thành viên BẤM, không chỉ thành viên XEM",
        )
        assertEquals(
            listOf("g_windows"), CapabilityGroups.groupsContaining("mac_win_close_all").map { it.id },
            "gói lệnh cũng là thành viên của nhóm",
        )
        assertTrue(
            CapabilityGroups.groupsContaining("speed").isEmpty(),
            "mục không thuộc nhóm nào phải ra danh sách RỖNG, không phải nhóm bừa",
        )
        assertTrue(CapabilityGroups.groupsContaining("khong_ton_tai").isEmpty(), "mã lạ ⇒ rỗng, không sập")
        assertNull(CapabilityGroups.byId("g_khong_ton_tai"), "mã nhóm lạ ⇒ null")
    }

    // ── R2: 0 mục rời bị xoá ─────────────────────────────────────────────────────────────────────

    @Test
    fun `khong muc roi nao bi xoa khi gom nhom`() {
        // [ĐO] 2026-09-11 trước G1: 123 datum · 64 nút · 9 widget · 4 gói lệnh. Gom nhóm là việc CỘNG THÊM; ai gom
        // xong xoá mục rời "cho gọn" là làm MẤT khả năng (§4.2 — có người chỉ muốn một con số tốc độ to giữa màn).
        // ⚠ 2026-09-16 owner gỡ ADAS/an toàn: 123 → 106 datum, 64 → 54 nút. Đó là một **quyết định của owner**,
        // không phải việc gom nhóm làm mất mục — bài này vẫn canh đúng điều nó sinh ra để canh.
        // ⚠ (V) 2026-09-17 owner gỡ 19 mã chấm NO: 112 → 100 datum, 54 → 47 nút. Cùng loại quyết định như trên.
        assertEquals(101, TelemetryRegistry.ALL.size, "mục đọc rời phải còn nguyên 101 (+1 pm25_outside 2026-09-20)")
        assertEquals(47, ControlRegistry.ALL.size, "nút rời phải còn nguyên 47")
        assertEquals(9, WidgetRegistry.ALL.size, "widget dựng tay phải còn nguyên 9")
        assertEquals(4, ActionMacros.ALL.size, "gói lệnh phải còn nguyên 4")
        // Và tổng khả năng = 4 bộ cũ + nhóm, không mất không nhân đôi.
        assertEquals(
            // U6: `all()` là thứ MÀN CHỌN bày ra nên nó trừ đi các mã cố ý ẩn ([CapabilityCatalog.HIDDEN_FROM_PICKER]);
            // phép kiểm "gom nhóm chỉ CỘNG THÊM" vẫn nguyên ý, chỉ nói đúng nguồn hơn.
            // S4 · R12 thêm nguồn thứ SÁU (hành động của chính launcher — [LauncherActions]). Kể nó vào ĐÂY chứ
            // không nới con số: bài này canh *"gom nhóm chỉ CỘNG THÊM"*, nên mọi nguồn phải hiện tên ra.
            101 + 47 + 9 + 4 + CapabilityGroups.ALL.size + LauncherActions.ALL.size -
                CapabilityCatalog.HIDDEN_FROM_PICKER.size,
            CapabilityCatalog.all().size,
            "gộp nhóm vào catalog không được làm mất hay nhân đôi mục nào",
        )
    }

    @Test
    fun `nhom phu duoc phan lon datum nhung KHONG phu het - va do la binh thuong`() {
        val covered = CapabilityGroups.coveredReadIds()
        val ungrouped = CapabilityGroups.ungroupedReadIds()
        assertEquals(
            TelemetryRegistry.ALL.size, covered.size + ungrouped.size,
            "mỗi datum phải hoặc thuộc nhóm hoặc nằm trong danh sách chưa-thuộc-nhóm, không rơi đâu mất",
        )
        assertTrue(covered.size >= 65, "9 nhóm phải phủ phần lớn datum, đang phủ ${covered.size}")
        // KHÔNG đòi phủ 100%: động lực/danh tính/GPS chưa có nhóm là đúng bảng §4.1, và mục rời vẫn đặt được.
        assertTrue("speed" in ungrouped, "tiền đề: tốc độ chưa thuộc nhóm nào (vẫn đặt được như mục rời)")
        // Không datum nào bị đếm hai lần trong CÙNG một nhóm.
        CapabilityGroups.ALL.forEach { g ->
            assertEquals(g.reads.distinct(), g.reads, "nhóm ${g.id} khai trùng mã XEM")
            assertEquals(g.writes.distinct(), g.writes, "nhóm ${g.id} khai trùng mã BẤM")
        }
    }

    // ── Dấu "chưa kiểm trên xe" phải chảy đúng ───────────────────────────────────────────────────

    @Test
    fun `muc bang chung cua nhom lay theo thanh vien YEU NHAT`() {
        // Nhóm Lốp: 4 áp suất đã chạy thật (PROVEN) nhưng 4 nhiệt độ chưa kiểm (NEEDS_CAR). Lấy mức cao sẽ khiến cả
        // bảng trông như đã chạy thật trong khi một nửa số ô của nó chắc chắn ra "—" ⇒ hứa quá.
        val tyres = CapabilityCatalog.pick("g_tyres")!!
        assertEquals(EvidenceTier.NEEDS_CAR, tyres.tier, "nhóm phải mang mức của thành viên yếu nhất")
        assertTrue(tyres.needsBadge, "⇒ ô phải mang dấu chưa-kiểm")
        // Nhóm toàn PROVEN thì KHÔNG mang dấu — nếu không thì dấu mất nghĩa vì ô nào cũng có.
        val allProven = CapabilityGroup(
            id = "g_test", label = "Thử", icon = "ic-grid", domain = Domain.TYRES, shape = WidgetShape.BOARD,
            reads = listOf("tyre_p_fl", "tyre_p_fr"),
        )
        assertTrue(
            allProven.reads.all { TelemetryRegistry.byId(it)!!.tier == EvidenceTier.PROVEN },
            "tiền đề: áp suất lốp đã chạy thật trên xe owner",
        )
    }

    // ── Hai nút cùng nhóm KHÔNG được gửi y hệt nhau lên bus (SOÁT P1-1) ──────────────────────────

    /**
     * ⚠⚠ **LỖI P1 ĐÃ CÓ THẬT**: nhóm Đèn từng đặt `headl` ("Đèn pha", TOGGLE) và `headlight_mode` ("Chế độ đèn pha",
     * SELECT) **cạnh nhau, cùng icon**, mà cả hai ghi `bindingKey` 1276153912 với **tham số y hệt**.
     *
     * Là **mục rời** thì cặp đó được miễn trừ (`ControlWriteArgsTest.COLLISION_PENDING_CAR`) với lý do *"hai mục RỜI,
     * người dùng phải cố ý đặt riêng"*. **G1 làm lý do đó hết đúng**: trong một nhóm, người dùng không chọn gì — ô tự
     * bày cả hai ra, trông như hai việc khác nhau. Bản vá bỏ `headl` khỏi nhóm (nó **vẫn còn** là mục rời) và giữ
     * `headlight_mode` vì nó nói được cả bốn trạng thái.
     */
    @Test
    fun `khong nhom nao co hai nut gui y het nhau len bus`() {
        assertEquals(
            emptyList<String>(), CapabilityGroups.sameWireWrites(CapabilityGroups.ALL),
            "hai nút cạnh nhau trong một ô, khác nhãn mà cùng một byte ⇒ người dùng không có cách nào biết",
        )
        // Và cụ thể: nhóm Đèn không được chứa `headl` nữa, nhưng `headl` vẫn phải CÒN là mục rời (không mất khả năng).
        assertTrue("headl" !in CapabilityGroups.LIGHTS.writes, "`headl` phải rời khỏi nhóm Đèn")
        assertTrue("headlight_mode" in CapabilityGroups.LIGHTS.writes, "và `headlight_mode` phải ở lại (phủ cả 4 trạng thái)")
        assertNotNull(ControlRegistry.byId("headl"), "`headl` KHÔNG được xoá khỏi registry — đó là mất khả năng")
        assertTrue(CapabilityGroups.groupsContaining("headl").isEmpty(), "và nó không thuộc nhóm nào khác")
    }

    /**
     * ⚠ Phép so là (**lệnh + THAM SỐ**), không phải chỉ "cùng lệnh" — bài này chốt rằng luật không bắt oan.
     *
     * Bốn nút kính dùng chung `setBodyWindowCtrlState` nhưng khác **chỉ số cửa**; `lock`/`door` dùng chung
     * `setDoorLockState` nhưng khác **giá trị** (2 vs 1, sau bản vá P0). Cả hai ca đều ĐÚNG và đều đang ở cùng một
     * nhóm — một luật chỉ so `bindingKey` sẽ đòi xé chúng ra, và cách "sửa" nhanh nhất lúc đó là nới luật.
     */
    @Test
    fun `luat khong bat oan nut cung lenh nhung khac tham so`() {
        assertEquals(
            emptyList<String>(), CapabilityGroups.sameWireWrites(listOf(CapabilityGroups.WINDOWS)),
            "4 nút kính cùng lệnh nhưng khác chỉ số cửa ⇒ hợp lệ",
        )
        assertEquals(
            emptyList<String>(), CapabilityGroups.sameWireWrites(listOf(CapabilityGroups.DOORS)),
            "`lock`/`door` cùng lệnh nhưng khác giá trị (2 vs 1) ⇒ hợp lệ",
        )
    }

    /**
     * ⚠⚠ Chốt phép kiểm **CÓ RĂNG**: dựng lại đúng tình huống P1-1 bằng hai nút GIẢ.
     *
     * Chạy luật trên registry thật (đã sạch sau bản vá) chỉ trả tập rỗng, nên không phân biệt được *"luật đúng"* với
     * *"luật không bao giờ chạy"* — đúng loại test trang trí mà dự án đã tìm ra ở ba chỗ khác. Hai nút giả không có
     * nhánh riêng trong `writeArgs` nên cả hai đi qua `else`, y như `headl`/`headlight_mode`.
     */
    @Test
    fun `phep kiem co rang - dung lai ca P1-1 bang nut gia thi phai bat duoc`() {
        val key = "BYDAutoFakeLightDevice.setSomething"
        val fake = mapOf(
            "fake_toggle" to ControlDef("fake_toggle", "Đèn pha", "ic-light", ControlKind.TOGGLE, bindingKey = key),
            "fake_select" to ControlDef("fake_select", "Chế độ đèn pha", "ic-light", ControlKind.SELECT, bindingKey = key),
        )
        val group = CapabilityGroup(
            id = "g_fake", label = "Giả", icon = "ic-light", domain = Domain.LIGHTS, shape = WidgetShape.STRIP,
            reads = listOf("light_side"), writes = fake.keys.toList(),
        )
        val found = CapabilityGroups.sameWireWrites(listOf(group), emptySet()) { fake[it] }
        assertEquals(1, found.size, "hai nút giả cùng lệnh, cùng tham số, cùng nhóm ⇒ luật phải nêu đúng 1 cặp: $found")
        assertTrue(found.single().contains("fake_toggle"), "thông báo phải nêu ĐÍCH DANH cặp vi phạm")
        assertTrue(found.single().contains("g_fake"), "và nêu cả nhóm nào — người sửa cần biết chỗ bỏ mục")

        // Khai vào danh sách cho phép thì luật im — nới CÓ KIỂM SOÁT, không phải nới mù.
        assertEquals(
            emptyList<String>(),
            CapabilityGroups.sameWireWrites(listOf(group), setOf(setOf("fake_toggle", "fake_select"))) { fake[it] },
        )
    }

    /** Danh sách miễn trừ phải **luôn còn đúng**: mỗi mục vẫn phải thật sự là một cặp trùng byte trong một nhóm. */
    @Test
    fun `danh sach mien tru khong duoc muc rua`() {
        val stale = CapabilityGroups.SAME_WIRE_ALLOWED.keys.filterNot { pair ->
            CapabilityGroups.sameWireWrites(CapabilityGroups.ALL, allowed = emptySet())
                .any { line -> pair.all { id -> id in line } }
        }
        assertEquals(
            emptyList<Set<String>>(), stale,
            "cặp này KHÔNG còn trùng byte trong nhóm nào ⇒ đã sửa được thì xoá khỏi SAME_WIRE_ALLOWED",
        )
    }
}
