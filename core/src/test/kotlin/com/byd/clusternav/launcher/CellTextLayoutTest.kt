package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.74 · R2 — KHOÁ ba lỗi hình học của bảng lốp + dải feather ăn vào thân xe ═══════════════════════════════
 *
 * Nguồn sự thật của bài này: **ảnh owner chụp trên xe 2026-09-25** (ghi trong `docs/PROJECT-BACKLOG.md`, mục
 * `UI-TYRE-LAYOUT`) và **số đo thật của ảnh xe mặc định** (`app/src/main/assets/car/default-car.png`):
 *
 *  • (A) *"chữ trong CẢ 4 thẻ dán sát mép trên, chừa ~66 px trống dưới"* ⇒ khoảng trống dưới ≈ 5–6× khoảng trống
 *    trên. Bài `khoi 2 dong ... cong thuc CU luon lech LEN` dựng lại **chính công thức cũ** và chứng minh nó lệch
 *    lên với MỌI phông hợp lý — nên nếu ai trả hằng `0.10`/`0.60` về thì đỏ ngay.
 *  • (C)/(D) *"ảnh xe nhô khỏi khối 4 thẻ"*, *"gương xe khít 0 px mép thẻ"* ⇒ thẻ đục chạy vào thân xe. Bài
 *    `the KHONG BAO GIO de len than xe` duyệt lưới cỡ ô dựng từ **chính [WorkspaceLayout]** (cả hai nhánh
 *    letterbox) và đo mức đè của công thức cũ.
 *  • ảnh mặc định 678×1397: **không có mực nào chạm 4 mép** (lề trong suốt 29 px trái/phải, 50 px trên/dưới — đo
 *    pixel 2026-09-26) — trong khi dải feather cũ `0.08 × cạnh ngắn` = **54 px** ⇒ ăn 25 px vào thân xe mỗi bên
 *    (gương mờ nửa alpha). Luật mới quyết định theo **tỉ lệ mực của từng mép**, không theo lề: ảnh cắt nền cắt SÁT
 *    có lề 0 px y như ảnh chụp mà phải xử lý ngược nhau.
 *
 * Mức bằng chứng (CLAUDE.md §2): công thức/hằng/thứ tự vẽ = **[ĐO]** (đọc source, tính lại được); mức lệch mà
 * owner thấy trên ảnh chụp = **[SUY]** (ảnh chụp nghiêng, chưa biết ảnh xe owner đang dùng là ảnh nào) — nên bài
 * này ghim **tính chất** (khối luôn cân, thẻ không bao giờ đè) chứ không ghim số px của ảnh chụp.
 *
 * ## 2.74 · UX7 — cùng ba lỗi ở các widget TỔNG HỢP khác (owner: *"cho nó giống nhau, hết lỗi chứ?"*)
 * Bốn ô vẽ Canvas của launcher dùng CHUNG tệp hình học này, nên các ca dưới đây dựng lại **chính công thức cũ** của
 * từng ô rồi chứng minh nó lệch — trả hằng nào về cũng đỏ:
 *  • `RingView` (widget *Năng lượng* · *Không khí* · mọi datum khai `RING`): `cy + big*0.36f` và
 *    `cy + big*0.9f + small` ⇒ tâm khối 2 dòng **thấp hơn** tâm vòng `0.146 × d`;
 *  • `PhotoWidgetView`: `h/2 − s1*0.4f` và `h/2 + s2*1.4f` — hai hằng tính theo HAI cỡ chữ khác nhau nên chúng chỉ
 *    "gần cân" ở đúng cặp tỉ lệ hiện tại (0.075/0.058) và vỡ ngay khi ai đổi một trong hai;
 *  • `GridEditorView`: `centerY + s*0.35f` xấp xỉ `capHeight/2` của **một** phông (Roboto 0.3555) — sai tới
 *    `0.025 × cỡ chữ` với phông có cap-height khác, mà phông là thứ ROM quyết.
 * Và lỗi (2) của ô dựng bằng `TextView` (`AxisRow` ở `:app`): canh giữa CẢ CỤM `số + đơn vị` đẩy con số lệch trục
 * đúng `(khe + rộng đơn vị)/2`.
 */
class CellTextLayoutTest {

    // ══ hằng của tầng vẽ (TyreBoardView) — chép ở đây để bài kiểm dựng lại ĐÚNG hình học thật ══════════════════
    private val carLeftFrac = 0.30f
    private val carRightFrac = 0.70f
    private val carTopFrac = 0.04f
    private val carBottomFrac = 0.98f
    private val padFrac = 0.02f
    private val gapFrac = 0.03f

    /** Tỉ lệ rộng:cao của ảnh xe mặc định (678×1397) + hai tỉ lệ khác để chắc là không hardcode một ảnh. */
    private val aspects = listOf(0.3f, 678f / 1397f, 0.8f)

    /** Khung ô THẬT: lấy từ [WorkspaceLayout] cho màn 1920×720 (vùng workspace sau thanh trên/taskbar) + ô nhóm. */
    private fun realFrames(): List<Pair<Float, Float>> {
        val out = mutableListOf<Pair<Float, Float>>()
        for (workspaceH in listOf(520, 560, 600)) {
            for (preset in LayoutPreset.values()) {
                WorkspaceLayout.slots(preset, 1920, workspaceH, gap = 9).forEach {
                    out += it.width.toFloat() to it.height.toFloat()
                }
            }
        }
        return out.distinct()
    }

    /** Lưới rộng hơn: mọi tỉ lệ ô từ 0,5 tới 3,0 (bao cả hai nhánh letterbox, ngưỡng ≈ 1,14). */
    private fun syntheticFrames(): List<Pair<Float, Float>> =
        listOf(0.5f, 0.8f, 1.0f, 1.14f, 1.3f, 2.0f, 3.0f).flatMap { r ->
            listOf(300f, 560f, 720f).map { h -> h * r to h }
        }

    /** Khung ảnh xe thật (letterbox trong khung `carDstIn`) — CÙNG phép toán `CarImageLayer.fitRect`. */
    private data class Content(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    private fun content(w: Float, h: Float, aspect: Float): Content {
        val dl = w * carLeftFrac; val dr = w * carRightFrac
        val dt = h * carTopFrac; val db = h * carBottomFrac
        val scale = minOf((dr - dl) / aspect, (db - dt) / 1f)
        val cw = aspect * scale; val ch = 1f * scale
        val cx = (dl + dr) / 2f; val cy = (dt + db) / 2f
        return Content(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
    }

    // ══ V1 · thẻ đục KHÔNG được đè lên thân xe ════════════════════════════════════════════════════════════════

    @Test
    fun `the KHONG BAO GIO de len than xe - moi co o, moi ti le anh`() {
        var worstOldOverlapPct = 0f
        for ((w, h) in realFrames() + syntheticFrames()) {
            for (aspect in aspects) {
                val c = content(w, h, aspect)
                val m = minOf(w, h)
                val gap = m * gapFrac
                val pad = w * padFrac
                for (corner in TyreCorner.values()) {
                    val a = CarLayout.wheel(corner)
                    val wheelX = c.left + a.x * c.width
                    val onLeft = a.x < 0.5f
                    val span = CellTextLayout.cardSpanX(onLeft, w, pad, c.left, c.right, wheelX, gap)
                    assertTrue(span.width >= 0f, "thẻ có bề rộng âm ở ô ${w}×$h (tỉ lệ $aspect)")
                    if (onLeft) {
                        assertTrue(
                            span.end <= c.left + TOL,
                            "thẻ TRÁI ăn vào khung ảnh ${span.end - c.left} px (ô ${w}×$h, tỉ lệ $aspect)",
                        )
                    } else {
                        assertTrue(
                            span.start >= c.right - TOL,
                            "thẻ PHẢI ăn vào khung ảnh ${c.right - span.start} px (ô ${w}×$h, tỉ lệ $aspect)",
                        )
                    }
                    // Mức đè của công thức CŨ (`wheelX ∓ gap`) — đây là lỗi (C)/(D) owner chụp được.
                    val oldOverlap = if (onLeft) (wheelX - gap) - c.left else c.right - (wheelX + gap)
                    if (c.width > 0f) worstOldOverlapPct = maxOf(worstOldOverlapPct, oldOverlap / c.width * 100f)
                }
            }
        }
        // Chứng minh bài này không kiểm một lỗi tưởng tượng: công thức cũ đè ít nhất 5 % bề rộng thân xe MỖI BÊN.
        assertTrue(
            worstOldOverlapPct > 5f,
            "công thức cũ chỉ đè ${"%.1f".format(worstOldOverlapPct)} % — kiểm lại giả định của bài",
        )
    }

    @Test
    fun `o that van con cho ve thu - the khong bi kep het`() {
        for ((w, h) in realFrames()) {
            val c = content(w, h, 678f / 1397f)
            val m = minOf(w, h)
            val span = CellTextLayout.cardSpanX(true, w, w * padFrac, c.left, c.right, c.left + 0.17f * c.width, m * gapFrac)
            assertTrue(span.usable, "ô THẬT ${w}×$h không còn chỗ cho thẻ — kẹp quá tay")
            assertTrue(span.width > c.width * 0.5f, "thẻ ở ô ${w}×$h hẹp bất thường: ${span.width} px")
        }
    }

    @Test
    fun `neo banh nam ngoai than xe thi mep the lui theo neo`() {
        // Generic: nếu ai đổi neo bánh ra NGOÀI khung ảnh (ảnh có lề rộng), mép thẻ phải lùi theo neo — không phải
        // chỉ theo khung ảnh. Không hardcode neo hiện tại vào tầng hình học.
        val span = CellTextLayout.cardSpanX(
            onLeft = true, viewWidth = 1000f, pad = 20f, imageLeft = 400f, imageRight = 600f, anchorX = 380f, gap = 10f,
        )
        assertEquals(370f, span.end, TOL, "mép thẻ phải lùi về neo (380) − gap (10)")
    }

    // ══ V2 · khối 2 dòng canh giữa ô theo SỐ ĐO PHÔNG ═════════════════════════════════════════════════════════

    @Test
    fun `khoi 2 dong nam dung giua o voi moi phong, moi co chu`() {
        for (cellH in listOf(60f, 120f, 141.6f, 186f, 280f, 400f)) {
            val centerY = 1000f + cellH / 2f
            for (big in listOf(cellH * 0.40f, cellH * 0.58f)) {
                for (sub in listOf(big * 0.25f, big * 0.38f)) {
                    for (cap in listOf(0.60f, 0.711f, 0.75f)) {          // cap-height của phông (Roboto ≈ 0,711)
                        for (desc in listOf(0.15f, 0.21f, 0.30f)) {      // descent của phông
                            val topInk = big * cap
                            val lineGap = sub * 1.35f
                            val bottomInk = sub * desc
                            val b = CellTextLayout.twoLineTopBaseline(centerY, topInk, lineGap, bottomInk)
                            val blockTop = b - topInk
                            val blockBottom = b + lineGap + bottomInk
                            assertEquals(
                                centerY, (blockTop + blockBottom) / 2f, 0.01f,
                                "khối 2 dòng lệch tâm (cellH=$cellH big=$big sub=$sub cap=$cap desc=$desc)",
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `khoi 2 dong - cong thuc CU luon lech LEN, khoang trong duoi gap nhieu lan tren`() {
        // Ảnh owner 2026-09-25 (A): "chữ dán sát mép trên, chừa ~66 px trống dưới". Dựng lại công thức cũ
        // `numBase = centerY + big*0.10 − sub*0.60` và đo — với MỌI phông hợp lý nó đều lệch LÊN.
        val cellH = 141.6f
        val centerY = cellH / 2f
        val big = cellH * 0.58f
        val sub = cellH * 0.22f
        for (cap in listOf(0.60f, 0.711f, 0.75f)) {
            for (desc in listOf(0.15f, 0.21f, 0.30f)) {
                val topInk = big * cap
                val lineGap = sub * 1.35f
                val bottomInk = sub * desc
                val old = centerY + big * 0.10f - sub * 0.60f
                val gapTop = (old - topInk) - 0f
                val gapBottom = cellH - (old + lineGap + bottomInk)
                assertTrue(gapBottom > gapTop, "công thức cũ phải lệch LÊN (cap=$cap desc=$desc)")
                assertTrue(
                    gapBottom - gapTop > 0.10f * cellH,
                    "mức lệch cũ phải ≈ 0,11·cellH: trên=$gapTop dưới=$gapBottom (cap=$cap desc=$desc)",
                )
                // …còn công thức mới thì hai khoảng trống bằng nhau (đúng thứ owner đòi: "căn giữa chữ theo thẻ").
                val fixed = CellTextLayout.twoLineTopBaseline(centerY, topInk, lineGap, bottomInk)
                assertEquals(
                    fixed - topInk, cellH - (fixed + lineGap + bottomInk), 0.01f,
                    "khoảng trống trên phải bằng dưới (cap=$cap desc=$desc)",
                )
            }
        }
    }

    // ══ V3 · một trục cho cả hai dòng + co cho vừa thẻ ════════════════════════════════════════════════════════

    @Test
    fun `con so nam giua truc o, don vi treo ben phai - hai dong cung truc`() {
        val left = 100f; val right = 400f; val inset = 10f
        val centerX = (left + right) / 2f
        val numW = 80f; val gapU = 8f; val unitW = 40f
        val x = CellTextLayout.lineStartX(centerX, numW, numW + gapU + unitW, left, right, inset)
        assertEquals(centerX, x + numW / 2f, TOL, "CON SỐ phải nằm giữa trục ô (đơn vị treo bên phải)")
        val subW = 120f
        val sx = CellTextLayout.lineStartX(centerX, subW, subW, left, right, inset)
        assertEquals(centerX, sx + subW / 2f, TOL, "dòng phụ phải cùng trục với con số")
        // Bản CŨ canh giữa CẢ CỤM ⇒ con số lệch trái đúng (gapU+unitW)/2 = 24 px.
        val oldNumCenter = centerX - (numW + gapU + unitW) / 2f + numW / 2f
        assertEquals(24f, centerX - oldNumCenter, TOL, "kiểm lại giả định: cụm-canh-giữa đẩy số lệch trái 24 px")
    }

    @Test
    fun `het cho ben phai thi ca cum nhich sang trai, khong bao gio bi cat`() {
        val left = 100f; val right = 300f; val inset = 10f   // chỗ trống trong thẻ = 180 px
        val centerX = (left + right) / 2f
        val numW = 120f; val pairW = 170f                    // vừa thẻ, nhưng canh giữa SỐ thì đơn vị tràn mép phải
        val x = CellTextLayout.lineStartX(centerX, numW, pairW, left, right, inset)
        assertTrue(x >= left + inset - TOL, "không được tràn mép TRÁI thẻ")
        assertTrue(x + pairW <= right - inset + TOL, "không được tràn mép PHẢI thẻ (đơn vị bị cắt)")
        assertTrue(x < centerX - numW / 2f, "phải nhích sang trái so với vị trí canh giữa lý tưởng")
    }

    @Test
    fun `cum rong hon ca the thi dat o le trong, khong day ra ngoai`() {
        val x = CellTextLayout.lineStartX(centerX = 200f, anchorWidth = 500f, lineWidth = 500f, left = 100f, right = 300f, inset = 10f)
        assertEquals(110f, x, TOL, "cụm rộng hơn thẻ ⇒ bám lề trong bên trái, không đẩy ra ngoài thẻ")
    }

    @Test
    fun `fitScale co vua cho trong, khong bao gio phong to`() {
        assertEquals(1f, CellTextLayout.fitScale(80f, 100f), TOL, "đã vừa thì không đổi")
        assertEquals(1f, CellTextLayout.fitScale(0f, 100f), TOL, "không có chữ thì không co")
        assertEquals(1f, CellTextLayout.fitScale(80f, 0f), TOL, "không có chỗ (ô chưa đo) thì giữ nguyên")
        assertEquals(0.5f, CellTextLayout.fitScale(200f, 100f), TOL, "rộng gấp đôi ⇒ co một nửa")
    }

    // ══ V5 · dải feather không được ăn vào thân xe ════════════════════════════════════════════════════════════

    @Test
    fun `anh xe mac dinh - dai feather cu an vao than xe, nay bang 0`() {
        // [ĐO 2026-09-26 — đo lại pixel trên CHÍNH tệp `app/src/main/assets/car/default-car.png`]: 678×1397; với
        // ngưỡng mực alpha ≥ 8 (`CarImageStore.INK_ALPHA`) hộp mực = (29, 50)…(648, 1346) ⇒ lề trong suốt 29 px
        // trái/phải, 50 px trên/dưới. Số dòng có mực CHẠM mép (dung sai 1 px) = 0/1397 mỗi bên; số điểm mực ở dòng
        // ngoài cùng trên/dưới = 0/678 ⇒ **tỉ lệ mực cả 4 mép = 0**. Dải cũ = 0,08 × cạnh ngắn = 54,24 px ⇒ ăn
        // 54,24 − 29 = 25 px vào THÂN xe mỗi bên (gradient DST_OUT xoá tới ~50 % alpha ngay tại mép thân).
        val w = 678; val h = 1397
        val full = minOf(w, h) * 0.08f
        assertTrue(full > 29f, "kiểm lại giả định: dải cũ (${"%.1f".format(full)} px) phải sâu hơn lề 29 px")
        assertEquals(0f, CarLayout.featherBand(full, 0f), TOL, "không mực nào chạm mép ⇒ KHÔNG feather mép đó")
        // Cỡ giải mã thật cho khung bảng lốp (269×554): tỉ lệ mực KHÔNG đổi theo cỡ (đó là lý do đo tỉ lệ, không
        // đo px) ⇒ kết luận giữ nguyên ở mọi cỡ giải mã.
        assertEquals(0f, CarLayout.featherBand(minOf(269, 554) * 0.08f, 0f), TOL)
    }

    @Test
    fun `anh chup chu nhat dac - van feather du 8 phan tram nhu cu`() {
        val full = minOf(900, 600) * 0.08f
        assertEquals(full, CarLayout.featherBand(full, 1f), TOL, "mép ĐẶC MỰC ⇒ mép CỨNG ⇒ giữ nguyên đường 2.73")
        assertEquals(full, CarLayout.featherBand(full, 0.96f), TOL, "viền khử răng cưa / góc bo nhẹ vẫn là mép cứng")
    }

    @Test
    fun `anh cat nen cat SAT mep - guong cham bien anh thi KHONG duoc feather`() {
        // Đây là ca mà phép đo theo LỀ TRONG SUỐT không phân biệt được (lề = 0 px, y như ảnh chụp chữ nhật) nhưng
        // cần xử lý NGƯỢC lại: ảnh cắt nền cắt sát chỉ có gương/mũi xe chạm biên ảnh ⇒ feather ở đó = xoá alpha của
        // chính gương ⇒ đúng lỗi owner báo 2026-09-25, chỉ khác ảnh (rất dễ gặp: ảnh "car top view png" tải về
        // thường cắt sát). Vì vậy luật đo TỈ LỆ MỰC của mép.
        //
        // [ĐO 2026-09-26] số thật: cắt `default-car.png` sát hộp mực ⇒ 620×1297, tỉ lệ mực 4 mép =
        // 0,0123 · 0,1242 · 0,0116 · 0,0758 (giải mã về 269 px rộng: 0,0214 · 0,2082 · 0,0196 · 0,1673 — tỉ lệ ỔN
        // ĐỊNH theo cỡ, đó là lý do đo tỉ lệ chứ không đo px). Ảnh chụp chữ nhật đặc cùng phép đo = 1,0 cả 4 mép.
        val full = 40f
        for (ratio in listOf(0.0123f, 0.1242f, 0.0116f, 0.0758f, 0.2082f, 0.1673f)) {
            assertEquals(0f, CarLayout.featherBand(full, ratio), TOL, "mép cắt nền (tỉ lệ mực $ratio) ⇒ KHÔNG feather")
        }
        assertEquals(0f, CarLayout.featherBand(full, 0.60f), TOL, "thân xe rộng chạm 60 % mép vẫn KHÔNG phải mép cứng")
        assertTrue(CarLayout.SOLID_EDGE_INK > 0.6f, "ngưỡng phải nghiêng về KHÔNG xoá mực")
    }

    @Test
    fun `dai feather quyet dinh theo TUNG mep - anh nua dac nua cat nen`() {
        val full = 40f
        // Ảnh chụp đã xoá nền một phía: mép còn đặc mực vẫn được làm mềm, mép đã cắt nền thì không.
        assertEquals(full, CarLayout.featherBand(full, 1f), TOL)
        assertEquals(0f, CarLayout.featherBand(full, 0.3f), TOL)
    }

    // ══ UX7 · khối chữ cân giữa Ô THẬT của TỪNG bảng — sai số ≤ 0,5 px ═══════════════════════════════════════

    /**
     * Lưới cỡ ô THẬT của mọi bảng tổng hợp, suy từ [WorkspaceLayout] (không gõ tay một cỡ nào):
     *  • **thẻ lốp**: cao `cellH` theo đúng phép toán `TyreBoardView.onDraw`;
     *  • **vòng đo** (*Năng lượng* · *Không khí*): ô vuông cạnh `d = 0.76 × cạnh ngắn` của khung vòng — khung vòng là
     *    ô trừ lề trong `col()` (Sp.L = 16dp ⇒ 24 px ở density 1.5) và dòng chú thích (~30 px) [SUY hai số đó, vì
     *    chúng do lượt đo cây view quyết]; phép kiểm KHÔNG phụ thuộc chúng (bất biến đúng với mọi `d`);
     *  • **ô ảnh** (*Trình chiếu ảnh*): chính ô đó.
     */
    private fun boardBoxes(): List<Triple<String, Float, Float>> {
        val out = mutableListOf<Triple<String, Float, Float>>()
        for ((w, h) in realFrames()) {
            val c = content(w, h, 678f / 1397f)
            val m = minOf(w, h)
            val wheelSpanY = (CarLayout.wheel(TyreCorner.REAR_LEFT).y - CarLayout.wheel(TyreCorner.FRONT_LEFT).y) * c.height
            val cellH = minOf(h * 0.24f, wheelSpanY - m * 0.035f).coerceAtLeast(m * 0.10f)
            out += Triple("thẻ lốp ${w.toInt()}×${h.toInt()}", w, cellH)
            val ringBox = minOf(w - 48f, h - 48f - 30f).coerceAtLeast(60f)
            out += Triple("vòng đo ${w.toInt()}×${h.toInt()}", ringBox, ringBox * 0.76f)
            out += Triple("ô ảnh ${w.toInt()}×${h.toInt()}", w, h)
        }
        return out
    }

    @Test
    fun `khoi chu can giua O THAT cua moi bang - lech tam khong qua 0,5 px`() {
        var checked = 0
        for ((name, boxW, boxH) in boardBoxes()) {
            val centerY = boxH / 2f
            // Cỡ chữ của cả ba bảng đều suy từ cạnh ô, nên duyệt dải tỉ lệ bao trọn ba bảng (0.075…0.58 × cạnh).
            for (bigRatio in listOf(0.075f, 0.12f, 0.26f, 0.58f)) {
                val big = (minOf(boxW, boxH) * bigRatio).coerceAtLeast(8f)
                for (subRatio in listOf(0.0f, 0.22f, 0.46f, 0.77f)) {   // 0 = một dòng (đơn vị rỗng)
                    val sub = big * subRatio
                    for (cap in listOf(0.60f, 0.711f, 0.75f)) {
                        for (desc in listOf(0.15f, 0.244f, 0.30f)) {
                            val topInk = big * cap
                            val lineGap = if (sub <= 0f) 0f else sub * CellTextLayout.SUB_LINE_GAP
                            val bottomInk = if (sub <= 0f) 0f else sub * desc
                            val base = CellTextLayout.twoLineTopBaseline(centerY, topInk, lineGap, bottomInk)
                            val blockCenter = ((base - topInk) + (base + lineGap + bottomInk)) / 2f
                            assertTrue(
                                kotlin.math.abs(blockCenter - centerY) <= 0.5f,
                                "$name: khối chữ lệch tâm ${blockCenter - centerY} px (big=$big sub=$sub cap=$cap)",
                            )
                            checked++
                        }
                    }
                }
            }
        }
        assertTrue(checked > 1000, "lưới quá mỏng ($checked ca) — phép quét đã hụt, không phải mã sạch")
    }

    @Test
    fun `mot dong can giua bang so do muc - centeredBaseline`() {
        for (size in listOf(12f, 34f, 82f, 130f)) {
            for (cap in listOf(0.60f, 0.711f, 0.75f)) {
                val topInk = size * cap
                val base = CellTextLayout.centeredBaseline(centerY = 100f, topInk = topInk)
                assertEquals(100f, base - topInk / 2f, TOL, "chữ số một dòng phải cân giữa (cap=$cap)")
                assertEquals(
                    CellTextLayout.twoLineTopBaseline(100f, topInk, 0f, 0f), base, TOL,
                    "centeredBaseline phải LÀ twoLineTopBaseline với lineGap = 0 (không phép canh thứ hai)",
                )
                // Hằng cũ của `GridEditorView` (`centerY + size*0.35f`) chỉ đúng với cap-height của Roboto.
                val old = 100f + size * 0.35f
                if (cap != 0.711f) assertTrue(
                    kotlin.math.abs(old - base) > 0.01f * size,
                    "kiểm lại giả định: hằng 0.35 phải lệch rõ với cap=$cap",
                )
            }
        }
    }

    @Test
    fun `RingView - cong thuc CU day khoi chu xuong duoi tam vong 0,146 x d`() {
        // Hằng của `RingView` bản 2.73 (chép ở đây để bài dựng lại đúng hình học cũ).
        val bigRatio = 0.26f; val smallRatio = 0.12f
        for (d in listOf(76f, 152f, 304f, 425.6f)) {
            val big = d * bigRatio; val small = d * smallRatio
            val cap = 0.711f; val desc = 0.244f
            val oldBigBase = big * 0.36f                       // tính từ cy = 0
            val oldSmallBase = big * 0.9f + small
            val oldCenter = ((oldBigBase - cap * big) + (oldSmallBase + desc * small)) / 2f
            assertEquals(0.146f * d, oldCenter, 0.002f * d, "công thức cũ phải đẩy khối chữ xuống ≈ 0,146·d")
            assertTrue(oldCenter > 0.10f * d, "và mức lệch đó phải THẤY ĐƯỢC (>10 % đường kính): $oldCenter px")
            // …còn công thức mới thì cân, với chính nhịp 2 dòng đang dùng.
            val lineGap = small * CellTextLayout.SUB_LINE_GAP
            val base = CellTextLayout.twoLineTopBaseline(0f, cap * big, lineGap, desc * small)
            val center = ((base - cap * big) + (base + lineGap + desc * small)) / 2f
            assertEquals(0f, center, 0.5f, "khối chữ trong vòng phải cân quanh tâm vòng")
        }
    }

    @Test
    fun `PhotoWidgetView - hai hang cu tinh theo HAI co chu nen chi gan can, vo khi doi ti le`() {
        val cap = 0.927f   // dòng trên là CÂU CHỮ có dấu ⇒ mực = ascent của phông, không phải cap-height
        val desc = 0.244f
        fun oldCenter(s1: Float, s2: Float): Float =
            (((-0.4f * s1) - cap * s1) + ((1.4f * s2) + desc * s2)) / 2f
        val m = 560f
        // Cặp tỉ lệ ĐANG dùng (0.075 / 0.058): lệch nhỏ — đây là lý do lỗi này không lộ ra bằng mắt.
        assertTrue(
            kotlin.math.abs(oldCenter(0.075f * m, 0.058f * m)) < 0.01f * m,
            "kiểm lại giả định: cặp tỉ lệ hiện tại chỉ lệch ít",
        )
        // Đổi MỘT trong hai tỉ lệ (việc người sau rất dễ làm) ⇒ lệch ngay, vì hai hằng không chung một cỡ chữ.
        assertTrue(
            kotlin.math.abs(oldCenter(0.075f * m, 0.030f * m)) > 0.02f * m,
            "hằng cũ phải vỡ khi đổi cỡ dòng dưới — đó là lý do phải tính từ số đo phông",
        )
        // Công thức mới: cân với MỌI cặp tỉ lệ.
        for (s1 in listOf(0.05f, 0.075f, 0.12f)) {
            for (s2 in listOf(0.02f, 0.03f, 0.058f, 0.09f)) {
                val t = s1 * m; val b = s2 * m
                val lineGap = b * CellTextLayout.SUB_LINE_GAP
                val base = CellTextLayout.twoLineTopBaseline(0f, cap * t, lineGap, desc * b)
                val center = ((base - cap * t) + (base + lineGap + desc * b)) / 2f
                assertEquals(0f, center, 0.5f, "khối nhắc ảnh phải cân với mọi cặp cỡ chữ ($s1/$s2)")
            }
        }
    }

    // ══ UX7 · một TRỤC cho ô dựng bằng TextView (AxisRow) ════════════════════════════════════════════════════

    @Test
    fun `AxisRow - con so ve dung truc o, cum canh giua cu thi lech nua be rong don vi`() {
        // Bốn chỗ gọi thật + bề rộng đơn vị theo mô hình advance của Roboto ở density 1.5 [SUY cho px, [ĐO] cho
        // công thức]: ô Tốc độ (" km/h" 15sp) · ô đọc chung (" %" 14sp) · số chính thẻ nhóm (" bar" 14sp) ·
        // ô đọc thanh nút ("%" 13sp + khe Sp.XS = 6 px).
        val cases = listOf(
            Triple("Tốc độ", 58.3f, 0f),
            Triple("ô đọc chung", 22.9f, 0f),
            Triple("số chính thẻ nhóm", 35.7f, 0f),
            Triple("ô đọc thanh nút", 16.2f, 6f),
        )
        for ((name, unitW, gap) in cases) {
            val left = 0f; val right = 300f; val inset = 0f
            val centerX = (left + right) / 2f
            val numW = 60f
            val x = CellTextLayout.lineStartX(centerX, numW, numW + gap + unitW, left, right, inset)
            assertEquals(centerX, x + numW / 2f, TOL, "$name: CON SỐ phải ở trục ô")
            // Nhãn/dòng phụ của cùng ô canh giữa đúng trục ⇒ lệch của bản cũ = khoảng cách giữa hai trục.
            val oldNumCenter = centerX - (numW + gap + unitW) / 2f + numW / 2f
            assertEquals((gap + unitW) / 2f, centerX - oldNumCenter, TOL, "$name: lệch cũ = (khe + đơn vị)/2")
            assertTrue(centerX - oldNumCenter > 8f, "$name: lệch cũ phải thấy được (> 8 px)")
        }
    }

    @Test
    fun `AxisRow - o hep thi SO bi cat, don vi khong bao gio bi day ra ngoai`() {
        // Ô hẹp nhất có ô đọc: ô GROUP trong nhóm 5 cột ở preset QUAD (~180 px sau lề). `AxisRow.onMeasure` đo ĐƠN
        // VỊ trước rồi mới cho số phần còn lại (thà `ellipsize` con số còn hơn mất đơn vị) — bài này dựng lại đúng
        // hai bước đó, vì chỉ bước 2 (`lineStartX`) thì cụm rộng hơn ô vẫn tràn.
        val left = 0f; val right = 180f
        val unitW = 30f; val gap = 6f
        val wanted = 150f
        val numW = minOf(wanted, right - left - unitW - gap)      // bước 1: đo số trong phần CÒN LẠI
        assertEquals(144f, numW, TOL, "số phải bị kẹp về phần còn lại, không lấy trọn bề rộng ô")
        val x = CellTextLayout.lineStartX((left + right) / 2f, numW, numW + gap + unitW, left, right, inset = 0f)
        assertTrue(x >= left - TOL, "không tràn mép trái")
        assertTrue(x + numW + gap + unitW <= right + TOL, "đơn vị không bị đẩy ra ngoài ô")
    }

    @Test
    fun `nhip 2 dong la MOT so dung chung, khong moi bang mot so`() {
        assertEquals(1.35f, CellTextLayout.SUB_LINE_GAP, TOL, "nhịp chữ của bảng lốp (2.56) là nhịp chung")
    }

    private companion object {
        const val TOL = 0.01f
    }
}
