package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.GridFit.Form
import com.byd.clusternav.launcher.GridFit.Shape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ QA 04/10 (làn H1) — widget 6 nút kính: chỉ-icon méo + nút 24dp, tiếng Mã Lai mất hết nhãn ═══════════════════
 *
 * Bằng chứng QA ([ĐO] máy ảo, bản 149dcab, nhật ký `l5/ms-dock-apps1-widgetfit.log` và các tệp cùng họ): khung 301×123 ⇒ `6x1 cell=36x99
 * ICON_ONLY k=2.000 touch=false`; tiếng Mã Lai ⇒ `ICON_ONLY` ở MỌI khung kể cả 615×123.
 *
 * Bốn bài khoá:
 *  1. bốn bóng xe kính KHÔNG phân biệt được khi bỏ nhãn ⇒ lưới kính không bao giờ rơi về chỉ-icon ([IconRepeat]);
 *  2. không ứng viên nào đạt 48dp ⇒ ô gần đích chạm hơn (ít cột, nhiều hàng) đứng trước cỡ chữ ([GridFit], bước 2);
 *  3. dạng nhãn DỰ PHÒNG (ngang 2 dòng) chỉ dùng khi không dạng nhãn chính nào đọc được, luôn trước chỉ-icon;
 *  4. tầng không đọc được: hộp ở sàn còn vừa CAO ô đứng trước (tràn ngang ⇒ `…`, tràn dọc ⇒ mất nửa dòng).
 *
 * Hộp tự nhiên của ô `TileSize.DOCK` ở mật độ 1,5 là [SUY]: bề rộng chữ đo bằng Roboto-Regular 17,25px (11,5sp) trên
 * máy dev (PIL, `scratchpad/h1/m.py`) + hình học ô (đệm 4dp, icon 20dp, lề 4dp, dòng ≈ 20,2px + 2,7px đệm phông) — KHÔNG
 * phải số đo trên xe. Đối chiếu nhật ký QA tiếng Việt cùng khung: mô hình lệch ≤ 5 % (vd 301×259 ra đúng `3x2 VERTICAL/2
 * k=1.031`; 458×123 QA `HORIZONTAL/1 k=0.875`, mô hình 0,84 — mô hình hơi bi quan).
 */
class GridFitWindowWidgetTest {

    private val floorK = 10.0 / 11.5
    private val spec = GridFit.Spec(gapPx = 12, slackPx = 2, maxScale = 2.0, minCellPx = 72, quantum = 1.0 / 32)

    /** Hộp (rộng × cao, px ở thang 1) của dọc-2 · dọc-1 · ngang-1 · ngang-2 dự phòng — thứ tự `FitProbe.OPTIONS`. */
    private fun labelled(v2: Int, v1: Int, h1: Int, h2: Int): List<Shape> = listOf(
        Shape(Form.VERTICAL, v2.toDouble(), 91.0, floorK, lines = 2),
        Shape(Form.VERTICAL, v1.toDouble(), 71.0, floorK, lines = 1),
        Shape(Form.HORIZONTAL, h1.toDouble(), 42.0, floorK, lines = 1),
        Shape(Form.HORIZONTAL, h2.toDouble(), 55.0, floorK, lines = 2, reserve = true),
    )

    private val icon = Shape(Form.ICON_ONLY, 42.0, 48.0, 0.8)

    // Nhãn: VI "Kính lái…Đóng hết kính" · EN "Driver window…Close all" · MS trước/sau bản dịch 04/10.
    private val vi = labelled(79, 120, 156, 115)
    private val en = labelled(95, 159, 195, 131)
    private val msBefore = labelled(135, 200, 236, 171)     // "Tingkap belakang kanan" …
    private val msAfter = labelled(105, 148, 184, 141)      // "Kaca blkg kanan" …

    private val dock = listOf(615 to 123, 301 to 123, 458 to 123, 301 to 259)
    private val noDock = listOf(615 to 148, 301 to 148, 458 to 148, 301 to 310)

    private fun fit(w: Int, h: Int, shapes: List<Shape>) = GridFit.fit(6, w, h, shapes, spec)

    private fun label(f: GridFit.Fit) =
        "${f.cols}x${f.rows} ${f.shape?.form}/${f.shape?.lines} k=${f.scale} legible=${f.legible} touch=${f.touchOk}"

    // ── 1 · bóng hình ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `bon nut kinh khong phan biet duoc khi bo nhan - luoi kinh khong co chi-icon`() {
        val windows = listOf("win_lf", "win_rf", "win_lr", "win_rr", "mac_win_open_all", "mac_win_close_all")
        // Tên tệp khác nhau (bản cũ cho chỉ-icon vì đếm theo tên) …
        val names = windows.mapNotNull { CapabilityCatalog.pick(it)?.icon }
        assertEquals(6, names.size, "đủ hình cho 6 nút: $names")
        assertTrue(IconRepeat.distinguishable(names), "theo TÊN thì 4 tệp kính khác nhau: $names")
        // … nhưng cùng một bóng xe ⇒ cổng chỉ-icon phải đóng.
        assertFalse(IconRepeat.ofIds(windows), "bốn bóng xe chỉ khác một dấu kính 1–2px — bỏ nhãn là bấm nhầm kính")
        assertEquals("ic-car-top-window", IconRepeat.silhouette("ic-car-top-window-rr"))
        assertEquals("ic-car-top-door-shut", IconRepeat.silhouette("ic-car-top-door-lf-shut"))
        // Hình THẬT SỰ khác nhau vẫn được bỏ nhãn (đèn đọc · lọc bụi · nhiệt độ · gió · sấy kính · mát ghế).
        val distinct = listOf("readl", "pm25", "temp", "fan", "defrost", "seatc")
        assertEquals(6, distinct.mapNotNull { CapabilityCatalog.pick(it)?.icon }.size, "bài không được rỗng nghĩa")
        assertTrue(IconRepeat.ofIds(distinct))
        // Một CẶP trái/phải (ghế sưởi lái/phụ) vẫn là cặp, không phải dãy — luật CAP = 3 giữ nguyên.
        assertTrue(IconRepeat.distinguishableWithoutLabels(listOf("ic-seat-heat-left", "ic-seat-heat-right", "ic-fan")))
    }

    // ── 2 · gần đích chạm ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `khong ung vien nao dat 48dp - o gan dich cham hon dung truoc co chu`() {
        // Hộp chỉ-icon HẸP (như bản 149dcab đo nhầm 17px): 6×1 cho k chạm trần 2,0 nhưng ô rộng 36px = 24dp; 3×2 cho ô
        // 84×43px (cạnh ngắn 43px). Bản cũ chọn 6×1 vì cỡ lớn hơn — đúng ảnh `l5/icononly-zoom.png`.
        val narrow = listOf(Shape(Form.ICON_ONLY, 16.0, 48.0, 0.8))
        val f = GridFit.fit(6, 301, 123, narrow, spec)
        assertEquals(3 to 2, f.cols to f.rows, label(f))
        assertFalse(f.touchOk, "khung 301×123 không chứa nổi 6 ô 48dp — ghi nhận, không bịa")
        // Có ứng viên đạt chạm thì luật cũ giữ nguyên: đạt chạm đứng trước mọi thứ khác trong cùng tầng.
        val roomy = GridFit.fit(6, 615, 148, listOf(icon), spec)
        assertTrue(roomy.touchOk, label(roomy))
        // Bậc so sánh: chênh dưới 1/16 đích chạm (3dp) không phải khác biệt về chạm.
        assertEquals(GridFit.nearTouch(36.0, 72), GridFit.nearTouch(39.0, 72))
        assertNotEquals(GridFit.nearTouch(36.0, 72), GridFit.nearTouch(43.5, 72))
        assertEquals(0, GridFit.nearTouch(10.0, 0), "không xét chạm ⇒ không đổi thứ tự")
    }

    // ── 3 · nhãn dự phòng ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nhan du phong chi khi khong dang nhan chinh nao doc duoc - va luon truoc chi-icon`() {
        // Khung có dạng nhãn chính đọc được ⇒ dự phòng KHÔNG được chọn dù nó cho cỡ lớn hơn (thêm nó không đổi bố cục
        // nào đang đọc được — VI 301×310 vẫn dọc-1 như QA đã thấy).
        val primary = GridFit.fit(6, 301, 310, vi, spec)
        assertTrue(primary.legible && !primary.shape!!.reserve, label(primary))
        val reserveAlone = GridFit.fit(6, 301, 310, vi.filter { it.reserve }, spec)
        assertTrue(reserveAlone.legible && reserveAlone.scale > primary.scale, "dự phòng cỡ lớn hơn mà vẫn đứng sau")
        // Không dạng chính nào đọc được ⇒ dự phòng, đứng TRƯỚC chỉ-icon (EN 301×259: trước đây ra chỉ-icon).
        val f = GridFit.fit(6, 301, 259, en + icon, spec)
        assertTrue(f.legible && f.shape!!.reserve, label(f))
        assertEquals(Form.HORIZONTAL, f.shape!!.form)
        assertEquals(2, f.shape!!.lines)
        assertTrue(GridFit.capacity(301, 259, en, spec, 8) >= 0, "sức chứa vẫn tính được với dạng dự phòng")
    }

    // ── 4 · tầng không đọc được ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `khong doc duoc - uu tien hop o san van vua cao o`() {
        // 301×123, 6 nút kính VI, không chỉ-icon: không bố cục nào đọc được. 3×2 (gần chạm nhất) — ngang-2 cho k lớn
        // hơn (0,71) nhưng ở sàn cao 48px > ô 41px (mất nửa dòng dưới); ngang-1 ở sàn cao 37px ⇒ vừa, nhãn `…` ngang.
        val f = fit(301, 123, vi)
        assertFalse(f.legible, label(f))
        assertEquals(3 to 2, f.cols to f.rows, label(f))
        assertEquals(Form.HORIZONTAL, f.shape!!.form, label(f))
        assertEquals(1, f.shape!!.lines, label(f))
        assertTrue(f.scale * f.shape!!.heightPx + 2 <= f.cellH, "ở sàn vẫn vừa chiều cao ô — ${label(f)}")
        assertEquals(floorK, f.scale, 1e-9, "không bóp chữ dưới sàn 10sp (R-WF3)")
    }

    // ── 5 · trước / sau theo khung ──────────────────────────────────────────────────────────────────────────

    /**
     * Ghi theo khung (dock 615×123 · 301×123 · 458×123 · 301×259; không dock 615×148 · 301×148 · 458×148 · 301×310):
     *  - TRƯỚC (QA 149dcab): MS chỉ-icon ở mọi khung trừ 301×310; VI/ZH chỉ-icon ở 301×123/301×148 (ô 36px);
     *  - SAU: không còn chỉ-icon nào (lưới kính); MS có nhãn đọc được ở 615×123, 301×259, 615×148, 458×148, 301×310.
     * Ba khung 301×123 · 458×123 · 301×148 KHÔNG đọc được ở mọi tiếng (6 nút có nhãn ≥ 10sp không vừa khung ≈ 200×82dp)
     * ⇒ nhãn giữ sàn 10sp + `…`, không icon mơ hồ; sức chứa ghi ở dòng `WidgetFit` (`cap=`).
     */
    @Test
    fun `tieng Ma Lai - truoc khong khung nao co nhan doc duoc o 615x123, sau co nhan o 5 khung`() {
        val before = fit(615, 123, msBefore)
        assertFalse(before.legible, "nhãn 'Tingkap belakang kanan' không vừa 615×123 ở dạng nào ≥ 10sp — ${label(before)}")
        for ((w, h) in listOf(615 to 123, 301 to 259, 615 to 148, 458 to 148, 301 to 310)) {
            val f = fit(w, h, msAfter)
            assertTrue(f.legible, "MS ${w}x$h phải có nhãn đọc được — ${label(f)}")
            assertNotEquals(Form.ICON_ONLY, f.shape!!.form)
        }
        val owner = fit(615, 123, msAfter)
        assertEquals(3 to 2, owner.cols to owner.rows, label(owner))
        assertEquals(Form.HORIZONTAL, owner.shape!!.form, "ngang 1 dòng như tiếng Thái cùng khung — ${label(owner)}")
        // Hộp `msAfter` dựng từ ĐÚNG các chữ này — bản dịch đổi ⇒ phải đo lại hộp (đỏ ở đây là nhắc việc đó).
        val shown = I18nPairs.inLang(Lang.MS) {
            listOf("win_lf", "win_rf", "win_lr", "win_rr").map { ControlRegistry.byId(it)!!.displayLabel } +
                listOf("mac_win_open_all", "mac_win_close_all").map { ActionMacros.byId(it)!!.displayLabel }
        }
        assertEquals(
            listOf("Kaca pemandu", "Kaca penumpang", "Kaca blkg kiri", "Kaca blkg kanan", "Buka semua", "Tutup semua"), shown,
        )
    }

    @Test
    fun `luoi kinh khong bao gio chi-icon, ca anh owner 615x148 giu nguyen`() {
        for (shapes in listOf(vi, en, msAfter)) for ((w, h) in dock + noDock) {
            val f = fit(w, h, shapes)
            assertNotEquals(Form.ICON_ONLY, f.shape!!.form, "${w}x$h ⇒ ${label(f)}")
            if (!f.legible) assertEquals(floorK, f.scale, 1e-9, "không đọc được ⇒ giữ sàn — ${w}x$h ${label(f)}")
        }
        // R-WF2 · ảnh owner 03/10 (VI, 615×148): một hàng 6 ô dọc 2 dòng, đạt 48dp — không đổi.
        val owner = fit(615, 148, vi)
        assertEquals(6 to 1, owner.cols to owner.rows)
        assertEquals(Form.VERTICAL to 2, owner.shape!!.form to owner.shape!!.lines)
        assertTrue(owner.legible && owner.touchOk && owner.scale >= 1.0, label(owner))
    }
}
