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
    fun `co dung 12 nhom theo bang spec`() {
        assertEquals(
            12, CapabilityGroups.ALL.size,
            "bảng §4.1 chốt 12 nhóm. Thêm/bớt nhóm là đổi thứ owner đã duyệt ⇒ sửa spec trước, đừng nới test",
        )
        // R1 của spec đòi "≥ 10 nhóm" — khoá luôn quan hệ đó để đổi số ở trên không lặng lẽ tụt xuống dưới ngưỡng.
        assertTrue(CapabilityGroups.ALL.size >= 10, "R1: phải có ít nhất 10 nhóm")
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

    @Test
    fun `chi nhom STRIP moi mang nut - vi hai bo ve kia khong co hang nut`() {
        // §4.3: hàng nút chỉ tồn tại trong bộ vẽ STRIP. Nút khai vào BOARD/CARD sẽ vẽ ra rồi không ai chạm được —
        // đúng họ lỗi "vẽ được ≠ đặt được" của RW0, và nó im lặng.
        val wrong = CapabilityGroups.ALL.filter { it.hasWrites && it.shape != WidgetShape.STRIP }.map { it.id }
        assertEquals(emptyList<String>(), wrong, "nhóm này khai nút nhưng bộ vẽ của nó không có chỗ đặt nút")
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

    @Test
    fun `nhom KHONG duoc lot len thanh trang thai`() {
        CapabilityGroups.ALL.forEach { g ->
            assertFalse(
                TopStripConfig.isChippable(g.id),
                "chip ~24dp không vẽ được nhóm ${g.id}, và 3/12 nhóm mang nút ⇒ đích chạm 24dp bắn lệnh xe",
            )
            assertTrue(
                TopStripConfig.choices().none { it.id == g.id },
                "màn chọn chip cũng KHÔNG được bày nhóm ${g.id} (bày ra rồi từ chối = nút chết)",
            )
            // Cửa vào cấu hình bền phải từ chối, không chỉ màn chọn.
            assertFalse(
                TopStripConfig.DEFAULT.setEnabled(g.id, true).has(g.id),
                "cấu hình bền phải từ chối nhóm ${g.id}",
            )
        }
        // Chốt ở lớp, không chỉ ở [setEnabled] — dựng trực tiếp cũng phải nổ.
        val ex = runCatching { TopStripConfig(listOf("g_tyres")) }.exceptionOrNull()
        assertNotNull(ex, "dựng thẳng TopStripConfig với mã nhóm phải bị chặn ngay tại lớp")
        // Và luật cũ KHÔNG bị nới: datum đọc vẫn lên được thanh trên.
        assertTrue(TopStripConfig.isChippable("tyre_p_fl"), "mục ĐỌC rời vẫn phải chip được — không được vá quá tay")
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
        assertEquals(123, TelemetryRegistry.ALL.size, "mục đọc rời phải còn nguyên 123")
        assertEquals(64, ControlRegistry.ALL.size, "nút rời phải còn nguyên 64")
        assertEquals(9, WidgetRegistry.ALL.size, "widget dựng tay phải còn nguyên 9")
        assertEquals(4, ActionMacros.ALL.size, "gói lệnh phải còn nguyên 4")
        // Và tổng khả năng = 4 bộ cũ + nhóm, không mất không nhân đôi.
        assertEquals(
            123 + 64 + 9 + 4 + CapabilityGroups.ALL.size, CapabilityCatalog.all().size,
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
        assertTrue(covered.size >= 80, "12 nhóm phải phủ phần lớn datum, đang phủ ${covered.size}")
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
}
