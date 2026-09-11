package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá MODEL BỐ CỤC ĐỘNG 12×6 (P9 bước nền — spec `kachi-dynamic-grid.html`).
 *
 * Test quan trọng nhất là `bo cuc san co ra DUNG TUNG PIXEL qua luoi`: nó **chứng minh** lưới có thể thay tầng vẽ về
 * sau mà bố cục cũ không xê dịch một pixel. Không có phép chứng minh đó thì việc đổi tầng vẽ là đánh cược.
 */
class WorkspaceGridTest {

    // ── Chứng minh tương đương với bố cục sẵn có ─────────────────────────────────────────────────

    @Test
    fun `bo cuc san co ra DUNG TUNG PIXEL qua luoi`() {
        // Quét nhiều cỡ màn + nhiều khe hở, gồm cả cỡ lẻ để bắt lỗi làm tròn.
        val sizes = listOf(1920 to 1080, 1000 to 600, 1366 to 768, 1921 to 1081, 777 to 333)
        val gaps = listOf(0, 1, 7, 10, 24)
        var checked = 0
        WorkspaceGrid.presetsRepresentable().forEach { preset ->
            val grid = WorkspaceGrid.fromPreset(preset)!!
            sizes.forEach { (w, h) ->
                gaps.forEach { g ->
                    val old = WorkspaceLayout.slots(preset, w, h, g)
                    val new = grid.slots(w, h, g)
                    assertEquals(
                        old, new,
                        "Bố cục $preset ở ${w}x$h khe=$g KHÔNG khớp. Lưới phải ra ĐÚNG khung cũ, nếu không thì " +
                            "đổi tầng vẽ sang lưới sẽ làm bố cục người dùng xê dịch.",
                    )
                    checked++
                }
            }
        }
        assertTrue(checked >= 100, "phải quét đủ nhiều tổ hợp, đã quét $checked")
    }

    @Test
    fun `bo cuc 3 o KHONG bieu dien duoc bang luoi va noi thang ra`() {
        // [ĐO] cột trái của nó = 1.55/2.55 × 12 = 7.294 cột — không phải số nguyên.
        // Trả null thay vì làm tròn là CỐ Ý: làm tròn âm thầm sẽ đổi bố cục owner đã duyệt mà không ai biết.
        assertNull(WorkspaceGrid.fromPreset(LayoutPreset.THREE), "phải nói thẳng là không biểu diễn được")
        assertFalse(LayoutPreset.THREE in WorkspaceGrid.presetsRepresentable())
        assertEquals(4, WorkspaceGrid.presetsRepresentable().size, "4/5 bố cục biểu diễn được")
    }

    @Test
    fun `bo cuc san co bieu dien duoc thi phu KIN luoi`() {
        WorkspaceGrid.presetsRepresentable().forEach { p ->
            val g = WorkspaceGrid.fromPreset(p)!!
            assertEquals(WorkspaceGrid.TOTAL_CELLS, g.coveredCells(), "$p phải phủ kín 72 ô")
            assertEquals(0, g.uncoveredCells())
            assertTrue(g.valid, "$p phải hợp lệ: ${g.problems()}")
        }
    }

    // ── Kiểm tra bố cục ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `bat khung DE len nhau va noi dich danh cap nao`() {
        val g = GridLayout(listOf(GridFrame(0, 0, 6, 3), GridFrame(4, 1, 6, 3)))
        val p = g.problems()
        assertFalse(g.valid)
        assertTrue(p.any { it.contains("đè lên nhau") }, "phải báo đè: $p")
        assertTrue(p.any { it.contains("khung 1") && it.contains("khung 2") },
            "trình vẽ cần biết ĐÍCH DANH hai khung nào để tô đỏ")
    }

    @Test
    fun `bat khung ra ngoai luoi`() {
        listOf(
            GridFrame(10, 0, 4, 2),   // vượt cột
            GridFrame(0, 5, 4, 2),    // vượt dòng
            GridFrame(-1, 0, 4, 2),   // âm
        ).forEach { f ->
            val p = GridLayout(listOf(f)).problems()
            assertTrue(p.any { it.contains("ra ngoài lưới") }, "khung $f phải bị bắt: $p")
        }
    }

    @Test
    fun `bat khung nho qua`() {
        val p = GridLayout(listOf(GridFrame(0, 0, 1, 1))).problems()
        assertTrue(p.any { it.contains("nhỏ quá") },
            "khung rộng 1 cột ≈ 160px trên màn 1920 — không hiện nổi thứ gì có nghĩa")
    }

    @Test
    fun `bo cuc rong bi bat`() {
        assertFalse(GridLayout(emptyList()).valid)
        assertTrue(GridLayout(emptyList()).problems().any { it.contains("rỗng") })
    }

    @Test
    fun `CON O TRONG khong phai loi - do la quyen cua nguoi dung`() {
        // Người dùng vẽ 1 khung nhỏ rồi để trống phần còn lại là hợp lệ; nền vẫn hiện ra ở đó.
        val g = GridLayout(listOf(GridFrame(0, 0, 4, 2)))
        assertTrue(g.valid, "còn ô trống KHÔNG được coi là lỗi: ${g.problems()}")
        assertEquals(8, g.coveredCells())
        assertEquals(WorkspaceGrid.TOTAL_CELLS - 8, g.uncoveredCells(), "nhưng phải BÁO ĐƯỢC còn bao nhiêu ô trống")
    }

    // ── Đổi sang pixel ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `khung sat mep an het phan lam tron - khong ho vach nao`() {
        // Cỡ lẻ + khe lẻ là ca dễ hở nhất.
        val g = GridLayout(
            listOf(GridFrame(0, 0, 5, 6), GridFrame(5, 0, 7, 6)),
        )
        val s = g.slots(1921, 1081, 7)
        assertEquals(0, s[0].left, "khung đầu phải bắt đầu ở 0")
        assertEquals(1921, s[1].right, "khung cuối phải chạm đúng mép phải")
        assertEquals(1081, s[0].bottom, "khung cao full phải chạm đúng mép dưới")
        assertEquals(7, s[1].left - s[0].right, "khe giữa hai khung phải đúng bằng khe đã đặt")
    }

    @Test
    fun `bien xa cua o cuoi CHAM DUNG mep`() {
        // Tính chất chịu lực: nhờ nó mà khung sát mép không cần ca đặc biệt nào. Quét cả cỡ lẻ + khe lẻ.
        listOf(1920 to 12, 1081 to 6, 777 to 12, 333 to 6, 1000 to 12).forEach { (total, count) ->
            listOf(0, 1, 7, 10, 23).forEach { g ->
                assertEquals(
                    total, WorkspaceGrid.farEdge(count, count, total, g),
                    "biên xa của ô cuối (count=$count, total=$total, khe=$g) phải chạm ĐÚNG mép — " +
                        "nếu không thì mép phải/dưới hở một vạch mảnh",
                )
                assertEquals(0, WorkspaceGrid.edge(0, count, total, g), "biên gần của ô đầu phải là 0")
            }
        }
    }

    @Test
    fun `khung khong chong nhau tren pixel`() {
        val g = WorkspaceGrid.fromPreset(LayoutPreset.QUAD)!!
        val s = g.slots(1920, 1080, 10)
        for (i in s.indices) for (j in i + 1 until s.size) {
            val a = s[i]; val b = s[j]
            val overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
            assertFalse(overlap, "ô $i và $j chồng nhau trên pixel: $a vs $b")
        }
    }

    @Test
    fun `cỡ khong hop le thi nem loi ro rang`() {
        val g = WorkspaceGrid.fromPreset(LayoutPreset.ONE)!!
        listOf(0 to 100, 100 to 0, -1 to 100).forEach { (w, h) ->
            val ex = runCatching { g.slots(w, h) }.exceptionOrNull()
            assertNotNull(ex, "cỡ ${w}x$h phải bị từ chối")
        }
    }

    // ── Lưu bền ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `luu roi doc lai duoc nguyen ven`() {
        val g = GridLayout(listOf(GridFrame(0, 0, 7, 4), GridFrame(7, 0, 5, 2), GridFrame(7, 2, 5, 4)))
        val back = WorkspaceGrid.decode(WorkspaceGrid.encode(g))
        assertEquals(g, back)
    }

    @Test
    fun `chuoi luu tu doc duoc bang mat thuong`() {
        // Bố cục là thứ người dùng bỏ công vẽ — khi cần cứu dữ liệu bằng tay thì đọc được quan trọng hơn tiết kiệm byte.
        assertEquals("0,0,7,4;7,0,5,6", WorkspaceGrid.encode(
            GridLayout(listOf(GridFrame(0, 0, 7, 4), GridFrame(7, 0, 5, 6))),
        ))
    }

    @Test
    fun `chuoi rac thi bo qua chu khong sap`() {
        listOf(null, "", "  ", "rác", "1,2;3", "a,b,c,d", "0,0,4,2;rác;4,0,4,2").forEach { s ->
            val g = runCatching { WorkspaceGrid.decode(s) }.getOrNull()
            assertNotNull(g, "chuỗi '$s' không được làm sập")
        }
        assertEquals(2, WorkspaceGrid.decode("0,0,4,2;rác;4,0,4,2").frames.size, "token rác bị BỎ, phần đúng vẫn giữ")
    }

    @Test
    fun `luoi dung so owner chot`() {
        assertEquals(12, WorkspaceGrid.COLS, "owner chốt 12 cột")
        assertEquals(6, WorkspaceGrid.ROWS, "owner chốt 6 dòng")
        assertEquals(72, WorkspaceGrid.TOTAL_CELLS)
    }
}
