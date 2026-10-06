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
 * Bằng chứng QA ([ĐO] máy ảo, bản 149dcab, nhật ký `ms-dock-apps1-widgetfit.log` (bằng chứng phiên, ngoài repo) và các tệp cùng họ): khung 301×123 ⇒ `6x1 cell=36x99
 * ICON_ONLY k=2.000 touch=false`; tiếng Mã Lai ⇒ `ICON_ONLY` ở MỌI khung kể cả 615×123.
 *
 * Bốn bài khoá:
 *  1. bốn bóng xe kính KHÔNG phân biệt được khi bỏ nhãn ⇒ lưới kính không bao giờ rơi về chỉ-icon ([IconRepeat]);
 *  2. không ứng viên nào đạt 48dp ⇒ ô gần đích chạm hơn (ít cột, nhiều hàng) đứng trước cỡ chữ ([GridFit], bước 2);
 *  3. dạng nhãn DỰ PHÒNG (ngang 2 dòng) chỉ dùng khi không dạng nhãn chính nào đọc được, luôn trước chỉ-icon;
 *  4. tầng không đọc được: hộp ở sàn còn vừa CAO ô đứng trước (tràn ngang ⇒ `…`, tràn dọc ⇒ mất nửa dòng).
 *
 * Hộp tự nhiên của ô `TileSize.DOCK` ở mật độ 1,5 là [SUY]: bề rộng chữ đo bằng Roboto-Regular 17,25px (11,5sp) trên
 * máy dev (PIL, công cụ đo ngoài repo) + hình học ô (đệm 4dp, icon 20dp, lề 4dp, dòng ≈ 20,2px + 2,7px đệm phông) — KHÔNG
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
        // ĐỔI GHIM (soát vòng 4 P3, quyết định điều phối J1): bản H1 ghim "một CẶP trái/phải vẫn là cặp — CAP = 3" cho
        // cổng KHÔNG NHÃN. Lý do của CAP 3 ("cặp đọc được NHỜ NHÃN") không tồn tại khi nhãn bị ẩn: cặp kính lái/phụ chỉ
        // khác một hình chữ nhật ≈ 1×2px ⇒ bỏ nhãn là bấm nhầm kính. Nay HAI ô cùng bóng hình cũng chặn chỉ-icon (cặp
        // ghế sưởi trái/phải mất đường chỉ-icon — vẫn có nhãn). Ô nhóm (nhãn luôn hiện) giữ CAP = 3.
        assertFalse(IconRepeat.distinguishableWithoutLabels(listOf("ic-seat-heat-left", "ic-seat-heat-right", "ic-fan")))
        assertFalse(IconRepeat.ofIds(listOf("win_lf", "win_rf", "readl", "fan", "temp", "pm25")), "cặp kính lái/phụ — ca soát vòng 4")
        assertTrue(IconRepeat.distinguishable(listOf("ic-seat-heat-left", "ic-seat-heat-right", "ic-fan")), "ô nhóm: CAP 3 giữ nguyên")
        assertTrue(IconRepeat.distinguishable(listOf("a", "a", "b")) && !IconRepeat.distinguishable(listOf("a", "a", "a")))
    }

    // ── 2 · gần đích chạm ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `khong ung vien nao dat 48dp - o gan dich cham hon dung truoc co chu`() {
        // Hộp chỉ-icon HẸP (như bản 149dcab đo nhầm 17px): 6×1 cho k chạm trần 2,0 nhưng ô rộng 36px = 24dp; 3×2 cho ô
        // 84×43px (cạnh ngắn 43px). Bản cũ chọn 6×1 vì cỡ lớn hơn — đúng ảnh `icononly-zoom.png` (bằng chứng phiên, ngoài repo).
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

    // ── J1 · QA2 04/10 — hộp ĐO từ nhật ký, nhãn ngắn, đích chạm trước loại nhãn ──────────────────────────────────

    /**
     * Hộp tự nhiên (px ở thang 1) SUY NGƯỢC từ nhật ký `WidgetFit` của QA2 ([ĐO] `{vi,zh,th,ms}-{dock,nodock,quad-dock}
     * -widgetfit.log` — bằng chứng phiên, ngoài repo; công cụ ngoài repo `boxes.py`): mỗi dòng `cell + raw` cho một khoảng của hộp dạng được chọn (`raw·w ≤ ô−2`
     * và bậc kế không vừa); giá trị dưới nằm trong GIAO mọi khoảng. Dạng chưa bao giờ được chọn (ZH/TH ngang-2) lấy từ mô
     * hình phông [SUY]. Nhãn NGẮN: VI dọc-2 hẹp hơn 4px (`Kính ST`/`Kính SP` thay `Kính sau trái/phải`, gói lệnh vẫn
     * `Đóng hết kính` — PIL); ZH/TH nhãn ngắn = nhãn đầy, MS `Kaca penumpang` ngắn = đầy ⇒ hộp nhãn ngắn = hộp đầy.
     */
    private fun measured(v2: Pair<Double, Double>, v1: Pair<Double, Double>, h1: Pair<Double, Double>, h2: Pair<Double, Double>, v2s: Double = v2.first) =
        listOf(
            Shape(Form.VERTICAL, v2.first, v2.second, floorK, lines = 2),
            Shape(Form.VERTICAL, v1.first, v1.second, floorK, lines = 1),
            Shape(Form.HORIZONTAL, h1.first, h1.second, floorK, lines = 1),
            Shape(Form.HORIZONTAL, h2.first, h2.second, floorK, lines = 2, reserve = true),
            Shape(Form.VERTICAL, v2s, v2.second, floorK, lines = 2, short = true),
            Shape(Form.VERTICAL, v1.first, v1.second, floorK, lines = 1, short = true),
            Shape(Form.HORIZONTAL, h1.first, h1.second, floorK, lines = 1, short = true),
        )

    private val measuredVi = measured(76.0 to 93.0, 114.0 to 72.0, 150.0 to 42.0, 112.0 to 55.0, v2s = 72.0)
    private val measuredZh = measured(81.5 to 92.0, 111.0 to 72.0, 117.0 to 42.0, 119.0 to 55.0)
    private val measuredTh = measured(141.0 to 92.0, 140.0 to 74.0, 168.0 to 42.0, 168.0 to 55.0)
    private val measuredMs = measured(104.0 to 92.0, 144.5 to 74.0, 179.0 to 42.0, 135.0 to 56.5)

    private fun brief(f: GridFit.Fit) =
        "${f.cols}x${f.rows} ${f.cellW}x${f.cellH} ${f.shape!!.form}/${f.shape!!.lines} k=${"%.3f".format(java.util.Locale.US, f.scale)} " +
            "legible=${f.legible} touch=${f.touchOk}"

    /**
     * Hộp đo tái tạo ĐÚNG 32 dòng nhật ký QA2 (4 tiếng × 8 khung) — và thứ tự mới (đích chạm trước loại nhãn, dạng nhãn
     * ngắn) KHÔNG đổi một bố cục nào trong số đó: nhãn ngắn chỉ đổi CHỮ từng ô ([FitLabels]), không đổi lưới.
     */
    @Test
    fun `J1 - hop do tai tao 32 dong nhat ky QA2, thu tu moi khong doi bo cuc nao`() {
        val qa2 = mapOf(
            measuredVi to listOf(
                "6x1 88x99 VERTICAL/2 k=1.031 legible=true touch=true", "3x2 84x43 HORIZONTAL/1 k=0.870 legible=false touch=false",
                "3x2 136x43 HORIZONTAL/1 k=0.875 legible=true touch=false", "3x2 84x111 VERTICAL/2 k=1.063 legible=true touch=true",
                "6x1 88x124 VERTICAL/2 k=1.125 legible=true touch=true", "3x2 84x56 HORIZONTAL/2 k=0.870 legible=false touch=false",
                "3x2 136x56 HORIZONTAL/1 k=0.875 legible=true touch=false", "2x3 132x87 VERTICAL/1 k=1.125 legible=true touch=true",
            ),
            measuredZh to listOf(
                "6x1 88x99 VERTICAL/2 k=1.031 legible=true touch=true", "3x2 84x43 HORIZONTAL/1 k=0.870 legible=false touch=false",
                "3x2 136x43 HORIZONTAL/1 k=0.969 legible=true touch=false", "3x2 84x111 VERTICAL/2 k=1.000 legible=true touch=true",
                "6x1 88x124 VERTICAL/2 k=1.031 legible=true touch=true", "3x2 84x56 HORIZONTAL/1 k=0.870 legible=false touch=false",
                "3x2 136x56 HORIZONTAL/1 k=1.125 legible=true touch=false", "2x3 132x87 VERTICAL/1 k=1.156 legible=true touch=true",
            ),
            measuredTh to listOf(
                "3x2 189x43 HORIZONTAL/1 k=0.969 legible=true touch=false", "3x2 84x43 HORIZONTAL/1 k=0.870 legible=false touch=false",
                "6x1 62x99 VERTICAL/2 k=0.870 legible=false touch=false", "2x3 132x70 VERTICAL/1 k=0.906 legible=true touch=false",
                "3x2 189x56 HORIZONTAL/1 k=1.094 legible=true touch=false", "3x2 84x56 HORIZONTAL/1 k=0.870 legible=false touch=false",
                "6x1 62x124 VERTICAL/2 k=0.870 legible=false touch=false", "2x3 132x87 VERTICAL/2 k=0.906 legible=true touch=true",
            ),
            measuredMs to listOf(
                "3x2 189x43 HORIZONTAL/1 k=0.969 legible=true touch=false", "3x2 84x43 HORIZONTAL/1 k=0.870 legible=false touch=false",
                "6x1 62x99 VERTICAL/2 k=0.870 legible=false touch=false", "2x3 132x70 VERTICAL/1 k=0.875 legible=true touch=false",
                "3x2 189x56 HORIZONTAL/1 k=1.031 legible=true touch=false", "3x2 84x56 HORIZONTAL/2 k=0.870 legible=false touch=false",
                "3x2 136x56 HORIZONTAL/2 k=0.938 legible=true touch=false", "2x3 132x87 VERTICAL/2 k=0.906 legible=true touch=true",
            ),
        )
        for ((shapes, want) in qa2) (dock + noDock).forEachIndexed { i, (w, h) ->
            val f = fit(w, h, shapes)
            assertEquals(want[i], brief(f), "${w}x$h")
            if (f.legible) assertFalse(f.shape!!.short, "khung đọc được bằng nhãn đầy không đổi sang nhãn ngắn — ${w}x$h")
        }
    }

    /**
     * Khung 2×1 / 3×1 (P2 QA2): không bố cục nào đọc được ⇒ lưới giữ sàn và LUẬT CHỮ TÊN ([FitLabels], `FitLabelsTest`)
     * quyết từng ô. Bài này khoá đầu vào của luật đó: 2×1 có dock ⇒ dạng ngang MỘT dòng ở cả 4 tiếng (được cắt ĐẦU);
     * 2×1 không dock VI/MS ⇒ ngang 2 dòng (nhãn ngắn `Kính ST`/`Kaca BKr` vừa); 3×1 TH/MS ⇒ dọc 2 dòng 62px.
     */
    @Test
    fun `J1 - khung 2x1 va 3x1 - khong doc duoc, dang ma luat chu ten lam viec`() {
        for (shapes in listOf(measuredVi, measuredZh, measuredTh, measuredMs)) {
            val f = fit(301, 123, shapes)
            assertFalse(f.legible, brief(f))
            assertEquals(Form.HORIZONTAL to 1, f.shape!!.form to f.shape!!.lines, "một dòng ⇒ cắt đầu được — ${brief(f)}")
            assertEquals(floorK, f.scale, 1e-9, "giữ sàn 10sp")
        }
        for (shapes in listOf(measuredVi, measuredMs)) assertEquals(2, fit(301, 148, shapes).shape!!.lines)
        for (shapes in listOf(measuredZh, measuredTh)) assertEquals(1, fit(301, 148, shapes).shape!!.lines)
        for (shapes in listOf(measuredTh, measuredMs)) {
            val f = fit(458, 123, shapes)
            assertEquals("6x1 62x99 VERTICAL/2", brief(f).substringBefore(" k="))
            assertFalse(f.legible)
        }
    }

    /**
     * P3 QA2 (khung 4×1 TH/MS ra ô 43px = 28,7dp dù có bố cục ≥ 48dp với nhãn bị cắt) — QUYẾT ĐỊNH J1: trong tầng có nhãn
     * đọc được, ĐÍCH CHẠM đứng trước loại nhãn ⇒ ô ≥ 48dp với nhãn NGẮN thắng ô < 48dp với nhãn đầy. Ghi nhận [ĐO hộp]: với
     * nhãn ngắn hiện có, TH/MS 615×123 KHÔNG đổi (nhãn ngắn TH = nhãn đầy; MS `Kaca penumpang` ngắn = đầy ⇒ hộp dọc nhãn
     * ngắn không hẹp hơn) — luật có hiệu lực ngay khi nhãn ngắn ngắn hơn, vd tiếng Anh (hộp mô hình PIL [SUY]).
     */
    @Test
    fun `J1 - dich cham dung truoc loai nhan - o 48dp nhan ngan thang o 43px nhan day`() {
        for (shapes in listOf(measuredTh, measuredMs)) {
            val f = fit(615, 123, shapes)
            assertEquals("3x2 189x43 HORIZONTAL/1", brief(f).substringBefore(" k="), "ghi nhận: nhãn ngắn TH/MS chưa ngắn hơn")
            assertFalse(f.touchOk)
        }
        // Tiếng Anh (`Driver window` → `Driver win`, `Rear-left window` → `Win RL`): hộp nhãn ngắn hẹp hơn nhiều.
        val en = listOf(
            Shape(Form.VERTICAL, 95.0, 91.0, floorK, lines = 2), Shape(Form.VERTICAL, 159.0, 71.0, floorK, lines = 1),
            Shape(Form.HORIZONTAL, 195.0, 42.0, floorK, lines = 1), Shape(Form.HORIZONTAL, 131.0, 55.0, floorK, lines = 2, reserve = true),
            Shape(Form.VERTICAL, 59.0, 91.0, floorK, lines = 2, short = true), Shape(Form.VERTICAL, 90.0, 71.0, floorK, lines = 1, short = true),
            Shape(Form.HORIZONTAL, 126.0, 42.0, floorK, lines = 1, short = true),
        )
        val before = fit(301, 259, en.filterNot { it.short })
        assertEquals("2x3 132x70 HORIZONTAL/2", brief(before).substringBefore(" k="), "chỉ nhãn đầy: ô 46,7dp")
        assertFalse(before.touchOk)
        val after = fit(301, 259, en)
        assertTrue(after.legible && after.touchOk && after.shape!!.short, "ô ≥ 48dp với nhãn ngắn — ${brief(after)}")
        // Cùng đạt chạm ⇒ nhãn đầy vẫn thắng (khung 615×148: một hàng 6 ô dọc nhãn đầy, không đổi sang nhãn ngắn).
        val roomy = fit(615, 148, en)
        assertTrue(roomy.legible && roomy.touchOk && !roomy.shape!!.short, brief(roomy))
    }
}
