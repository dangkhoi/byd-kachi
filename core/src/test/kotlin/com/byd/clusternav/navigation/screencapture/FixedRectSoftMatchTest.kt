package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.ArrayPixelFrame
import com.byd.clusternav.navigation.ManeuverRegistry
import com.byd.clusternav.navigation.ManeuverSignature
import com.byd.clusternav.navigation.PixelFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * KHOÁ B3.53 — **nhánh NCC MỀM của đường rect CỐ ĐỊNH ra SAI HƯỚNG trên khung của app khác**.
 *
 * ── LỖI CÓ THẬT, ĐO ĐƯỢC (không phải phòng xa) ────────────────────────────────────────────────────────
 * [CaptureCalibration] tra rect ARROW theo `Key(target, displayW, displayH)` — **KHÔNG theo package**. Vì
 * vậy `WAZE_ARROW_BANNER_D240 = (78,50,158,163)`, hiệu chuẩn trên banner **Waze**, cũng được áp nguyên xi
 * lên khung **VietMap** có cùng kích thước màn. Crop sai chỗ vẫn có mực ⇒ vẫn ra chữ ký 225-bit; mà
 * [ManeuverSignature.classifyDetailed] = `match(bits)` **?: `matchNCC(fill)`** với `NCC_MIN = 0.45` là khớp
 * MỀM và registry KHÔNG có lớp "không biết" ⇒ nó gần như luôn tìm được một cái tên.
 *
 * [ĐO] 08-23 trên 87 khung VietMap ghép từ asset APK ([VietMapGlyphFrames]), crop qua ĐÚNG rect cố định:
 * ```
 * depart_right      -> maneuver_roundabout_enter_and_exit_cw_normal_left  amap=11  (đúng: 3)   Hamming 27
 * fork_slight_right -> maneuver_turn_normal_right                         amap=3   (đúng: 5)   Hamming 37
 * fork              -> maneuver_turn_normal_right                         amap=3   (họ mập mờ) Hamming 38
 * ```
 * Cả ba trượt Hamming **rất xa** ngưỡng 18 ⇒ chỉ NCC mới cho ra tên ⇒ hướng hiển thị là bốc thăm. Mũi tên
 * SAI HƯỚNG trên xe đang lăn bánh nguy hiểm hơn hẳn không hiện gì.
 *
 * ── VÌ SAO VÁ Ở TIER RECT-CỐ-ĐỊNH, KHÔNG SIẾT NCC ────────────────────────────────────────────────────────
 * Hai ứng viên đã ĐO trước khi chọn (bảng số ở `docs/specs/b3-53-fixed-rect-ncc.html`):
 *   · **Siết NCC bằng trần Hamming rộng hơn** (vd ≤ 30) — **BÁC**. Ba ca sai nằm ở 27/37/38 nên trần phải
 *     < 27 mới chặn được; mà trên 418 khung GMaps nhiễu-hình-học (đường notification ĐÃ CHẠY ngoài hiện
 *     trường) có **256/418** khung đi qua NCC với minD trải tới 68 ⇒ trần 26 đã làm **162/418** khung đổi
 *     kết quả. CLAUDE.md §6 cấm.
 *   · **Rect cố định chỉ áp cho bố cục nó được hiệu chuẩn, nhận biết bằng ĐO** — CHỌN. Phép đo chính là
 *     "crop có rơi vào quả cầu Hamming 18 bit của một template không". Rect ĐO ĐƯỢC (a11y node / bbox mực
 *     `NavGlyphLocator`) giữ nguyên đường cũ; rect HIỆU CHUẨN SẴN chỉ chấp nhận khớp CỨNG
 *     ([ManeuverSignature.classifyStrict]). Không rẽ nhánh theo tên gói (CLAUDE.md §7).
 *
 * ⚠ Đây là bản sao off-car của quyết định; dây nối thật ở `ScreenCaptureNavSource.handleArrow`, khoá bằng
 * `ScreenCaptureNavSourceContractTest` (:app).
 */
class FixedRectSoftMatchTest {

    /** Rect ARROW cố định cho geometry của fixture VietMap (1920×1080) — cùng hằng số Waze dùng. */
    private val fixedRect: CropRect by lazy {
        val geom = DisplayGeometry(VietMapGlyphFrames.width, VietMapGlyphFrames.height, densityDpi = VietMapGlyphFrames.DPI)
        CaptureCalibration.fixedBounds(CaptureTarget.ARROW, geom)!!
    }

    // ── (1) 87 khung VietMap qua rect cố định ───────────────────────────────────────────────────────────

    /**
     * TRƯỚC/SAU của B3.53, đo trên CÙNG một bộ khung.
     *
     * Cột TRƯỚC ([ManeuverSignature.classifyDetailed], còn nhánh NCC) được giữ trong test **có chủ ý**: nó là
     * bằng chứng lỗi, và nếu ai đó chỉnh NCC thì test đỏ ở đây buộc phải ĐO LẠI thay vì đổi thầm lặng.
     */
    @Test
    fun `rect co dinh — khong con khung VietMap nao ra ma qua khop MEM (B3_53)`() {
        assertEquals(
            CaptureCalibration.WAZE_ARROW_BANNER_D240, fixedRect,
            "rect ARROW của khung VietMap đang KHÔNG phải hằng số hiệu chuẩn trên banner Waze — " +
                "gốc của lỗi này đã đổi, đo lại toàn bộ trước khi sửa số ở dưới",
        )

        val soft = LinkedHashMap<String, Int>()      // đường CŨ: classifyDetailed (Hamming ?: NCC)
        val strict = LinkedHashMap<String, Int>()    // đường MỚI: classifyStrict (chỉ Hamming)
        val wrongFamily = LinkedHashMap<String, String>()
        for (n in VietMapGlyphFrames.maneuverNames) {
            val c = VietMapGlyphFrames.crop(VietMapGlyphFrames.compose(n), fixedRect)
            ManeuverSignature.classifyDetailed(c).amap?.let { a ->
                soft[n] = a
                val want = VietMapGlyphFrames.expectedAmap(n)
                if (want != null && want != a) wrongFamily[n] = "ra $a, đúng phải $want"
            }
            ManeuverSignature.classifyStrict(c).amap?.let { strict[n] = it }
        }

        assertEquals(
            87, VietMapGlyphFrames.maneuverNames.size,
            "bộ fixture đã đổi số khung maneuver — mọi con số dưới đây phải đo lại",
        )
        // TRƯỚC — chốt cứng để không ai "sửa" bằng cách làm lỗi biến mất một cách tình cờ.
        assertEquals(
            listOf("depart_right", "fork", "fork_slight_right"), soft.keys.toList(),
            "đường khớp MỀM ra mã cho tập khung khác với lúc đo B3.53: $soft",
        )
        assertEquals(
            mapOf("depart_right" to "ra 11, đúng phải 3", "fork_slight_right" to "ra 3, đúng phải 5"),
            wrongFamily,
            "tập khung SAI HỌ của đường khớp mềm đã đổi: $wrongFamily",
        )
        // SAU — nghiệm thu B3.53: không còn khung VietMap nào ra mã qua rect cố định.
        assertEquals(
            emptyMap<String, Int>(), strict,
            "rect cố định (hiệu chuẩn trên banner Waze) vẫn ra mã trên khung VietMap ⇒ hướng là bốc thăm. " +
                "Mũi tên SAI HƯỚNG nguy hiểm hơn im lặng — CLAUDE.md.",
        )
    }

    // ── (2) ĐƯỜNG PROVEN: 3 khung Waze THẬT không được đổi một kết quả nào ──────────────────────────────

    /**
     * Rect cố định + Waze là đường **đã chạy thật** (fixture chụp nguyên văn trên emulator ở đúng kích thước
     * cụm/màn chính — xem [ClusterArrowCalibrationTest]). CLAUDE.md §6: bản vá không được đổi hành vi của nó.
     *
     * [ĐO] 08-23: hai khung khớp bằng **Hamming 15 và 18** (tức không nhờ NCC), khung thứ ba ("compact", bố
     * cục banner hẹp) vốn đã `(không khớp)` vì NCC cũng không cứu được ⇒ delta của bản vá = **0/3**.
     */
    @Test
    fun `3 khung Waze THAT qua rect co dinh — khop CUNG cho ket qua y het khop MEM`() {
        val fixtures = listOf(
            Triple("/diagnostics/waze-nav-cluster-1920x720-2026-08-21.png", 1920, 720),
            Triple("/diagnostics/waze-nav-main-1920x1080-2026-08-22.png", 1920, 1080),
            Triple("/diagnostics/waze-nav-main-1920x1080-compact-2026-08-22.png", 1920, 1080),
        )
        val soft = LinkedHashMap<String, String>()
        val strict = LinkedHashMap<String, String>()
        for ((res, w, h) in fixtures) {
            val f = frameOf(res)
            val r = CaptureCalibration.fixedBounds(CaptureTarget.ARROW, DisplayGeometry(w, h))!!
            val c = VietMapGlyphFrames.crop(f, r)
            val d = ManeuverSignature.classifyDetailed(c)
            val s = ManeuverSignature.classifyStrict(c)
            soft[res] = "${d.name}/${d.amap}/${d.maneuver}"
            strict[res] = "${s.name}/${s.amap}/${s.maneuver}"
        }
        assertEquals(soft, strict, "bản vá làm đổi kết quả của đường Waze + rect cố định — CLAUDE.md §6")
        // Chốt cứng giá trị (không chỉ "hai bên bằng nhau" — bằng nhau ở trạng thái CÂM cũng bằng nhau).
        assertEquals(
            "maneuver_turn_normal_right/3/null",
            strict["/diagnostics/waze-nav-cluster-1920x720-2026-08-21.png"],
            "khung cụm 1920×720 phải vẫn ra rẽ PHẢI",
        )
        assertEquals(
            "maneuver_turn_normal_right/3/null",
            strict["/diagnostics/waze-nav-main-1920x1080-2026-08-22.png"],
            "khung màn chính 1920×1080 phải vẫn ra rẽ PHẢI",
        )
    }

    // ── (2b) CÁI GIÁ THẬT CỦA BẢN VÁ TRÊN CHÍNH TIER BỊ ĐỔI ────────────────────────────────────────────

    /**
     * **Giá phủ sóng của B3.53, đo trên ĐÚNG tier bị đổi** (rect cố định × Waze) — không phải con số 418
     * khung GMaps ở test (3), vì đó là tier khác (notification) và không bị bản vá chạm tới.
     *
     * VÌ SAO ĐO BẰNG CÁCH NHÍCH RECT: `WAZE_ARROW_BANNER_D240` là hằng số, còn banner Waze thì KHÔNG cố định
     * tuyệt đối — chính KDoc của nó tự khai mức bằng chứng "nhiều khả năng" và ghi rõ **density khác 240
     * CHƯA đo**. Nhích rect ±6 px quanh giá trị hiệu chuẩn (lưới 7×7 = 49 vị trí) là mô hình rẻ nhất cho
     * "rect trượt khỏi glyph một chút".
     *
     * [ĐO] 08-23 trên 2 khung Waze THẬT:
     * ```
     *                              khớp MỀM ra mã   trong đó SAI HỌ   khớp CỨNG ra mã
     * cụm 1920×720                 40/49            6                 8/49
     * màn chính 1920×1080          39/49            7                 5/49
     * ```
     * Đọc con số này theo CẢ HAI chiều, đừng chỉ một chiều:
     *   · **Giá**: bản vá bỏ 66 lượt ra-mã (phần lớn ĐÚNG hướng) trên lưới nhiễu này ⇒ nếu rect trượt thật
     *     trên xe (density ≠ 240), kênh mũi tên rect-cố-định sẽ IM nhiều hơn trước.
     *   · **Lý do vẫn vá**: 13 lượt trong số đó là **SAI HỌ** — `maneuver_off_ramp_normal_right` (amap 5) và
     *     `maneuver_roundabout_enter_and_exit_cw_slight_right` (**amap 11 = vòng xuyến**) trong khi khung
     *     thật là rẽ phải thường (amap 3). Tức NCC trên rect trượt KHÔNG phải "suy giảm dần" mà nhảy sang
     *     họ maneuver khác. Khớp cứng trả `null` ở **đúng cả 13 lượt đó**. CLAUDE.md: im lặng > sai hướng.
     *
     * Đây là bảng để OWNER chốt đánh đổi, không phải bằng chứng "không mất gì". Đường phục hồi phủ sóng đã
     * ghi ở `docs/PROJECT-BACKLOG.md` (OQ2: khoá `CaptureCalibration.TABLE` theo package thay vì chỉ
     * `(target,W,H)`) — vá đó chữa NGUYÊN NHÂN, còn bản vá này chặn HẬU QUẢ.
     */
    @Test
    fun `gia phu song cua B3_53 tren tier rect co dinh — nhich rect ±6px tren 2 khung Waze THAT`() {
        data class Row(val soft: Int, val softWrongFamily: Int, val strict: Int, val strictOnWrong: Int)

        fun measure(res: String, geom: DisplayGeometry): Row {
            val f = frameOf(res)
            val base = CaptureCalibration.fixedBounds(CaptureTarget.ARROW, geom)!!
            var soft = 0
            var wrong = 0
            var strict = 0
            var strictOnWrong = 0
            for (dx in -6..6 step 2) for (dy in -6..6 step 2) {
                val r = CropRect(base.left + dx, base.top + dy, base.right + dx, base.bottom + dy)
                val c = VietMapGlyphFrames.crop(f, r)
                val d = ManeuverSignature.classifyDetailed(c)
                val s = ManeuverSignature.classifyStrict(c)
                if (d.amap != null) {
                    soft++
                    // Khung thật là rẽ PHẢI thường (amap 3) — mọi mã khác là SAI HỌ.
                    if (d.amap != AMAP_TURN_RIGHT) {
                        wrong++
                        if (s.amap != null) strictOnWrong++
                    }
                }
                if (s.amap != null) strict++
            }
            return Row(soft, wrong, strict, strictOnWrong)
        }

        val cluster = measure("/diagnostics/waze-nav-cluster-1920x720-2026-08-21.png", DisplayGeometry(1920, 720))
        val main = measure("/diagnostics/waze-nav-main-1920x1080-2026-08-22.png", DisplayGeometry(1920, 1080))

        assertEquals(Row(40, 6, 8, 0), cluster, "bảng giá/lợi của khung CỤM đã đổi — đo lại trước khi kết luận")
        assertEquals(Row(39, 7, 5, 0), main, "bảng giá/lợi của khung MÀN CHÍNH đã đổi — đo lại trước khi kết luận")

        // Nghiệm thu AN TOÀN: mọi lượt khớp mềm ra SAI HỌ đều bị khớp cứng chuyển thành IM LẶNG.
        assertEquals(
            0, cluster.strictOnWrong + main.strictOnWrong,
            "khớp CỨNG vẫn ra mã ở một vị trí mà khớp mềm ra SAI HỌ ⇒ bản vá không còn đạt mục tiêu an toàn",
        )
        assertTrue(
            cluster.softWrongFamily + main.softWrongFamily >= 10,
            "nhánh NCC trên rect trượt nay gần như không còn ra SAI HỌ (${cluster.softWrongFamily + main.softWrongFamily}) — " +
                "nếu đúng vậy thì cái giá phủ sóng của khớp cứng đã hết lý do; ĐO LẠI rồi trình owner",
        )
    }

    // ── (3) NHÁNH NCC PHẢI CÒN NGUYÊN CHO ĐƯỜNG NOTIFICATION ────────────────────────────────────────────

    /**
     * Chứng minh **vì sao [ManeuverSignature.classifyStrict] chỉ được dùng ở tier rect-cố-định**: đem nó
     * thay [ManeuverSignature.classifyDetailed] ở đường notification large-icon sẽ làm câm hàng loạt.
     *
     * Bộ 418 khung = 38 template GMaps × (dịch 1 ô theo 8 hướng + dilate + dilate-rồi-dịch) — cùng bộ nhiễu
     * hình học mà `WazeArrowRegistryTest.khung GMaps NHIEU HINH HOC…` dùng, mô phỏng GMaps đổi style icon /
     * large-icon bị upscale / đổi dpi. Đây chính là "vực im lặng" mà nhánh NCC sinh ra để lấp.
     */
    @Test
    fun `nhanh NCC van la thiet yeu cho duong notification — 418 khung GMaps nhieu hinh hoc`() {
        val cases = geometricNoiseCases()
        assertEquals(418, cases.size, "bộ nhiễu hình học đã đổi kích thước")
        var soft = 0
        var strict = 0
        for (q in cases) {
            val f = frameFromBits(q)
            if (ManeuverSignature.classifyDetailed(f).amap != null) soft++
            if (ManeuverSignature.classifyStrict(f).amap != null) strict++
        }
        assertEquals(274, soft, "đường notification (có NCC) đã đổi số khung ra mã — đo lại B3.53")
        assertEquals(18, strict, "khớp CỨNG một mình trên bộ nhiễu này đã đổi số khung ra mã")
        assertTrue(
            soft - strict >= 200,
            "chênh lệch NCC ở đường notification nay chỉ còn ${soft - strict}/418 — nếu nhánh NCC đã hết " +
                "tác dụng thì hãy ĐO LẠI rồi cân nhắc gỡ hẳn, đừng để nó nằm đó như một rủi ro không lời lãi",
        )
    }

    // ── (4) ỨNG VIÊN BỊ BÁC — giữ số đo để không ai đề xuất lại ─────────────────────────────────────────

    /**
     * BẢNG SỐ ĐO của ứng viên **(b) "chỉ cho vào nhánh NCC khi Hamming của ứng viên tốt nhất ≤ một trần
     * rộng hơn"** (đề xuất trong `docs/PROJECT-BACKLOG.md` B3.53) — **BÁC**, và đây là lý do bằng số.
     *
     * Ba ca sai hướng trên khung VietMap nằm ở Hamming **27 / 37 / 38**, nên trần phải **≤ 26** mới chặn
     * được cả ba. Nhưng trên 418 khung GMaps nhiễu-hình-học — đường notification ĐANG CHẠY ngoài hiện
     * trường — có **256** khung đi qua NCC với minD trải tới **68**, nên trần 26 làm **162/418** khung đổi
     * kết quả. Nghiệm thu B3.53 đòi 0/418 ⇒ mọi trần chặn được ba ca kia đều trượt.
     *
     * Test tính lại bảng mỗi lần chạy: nếu bộ template hoặc thuật toán chữ ký đổi, bảng đỏ ⇒ ĐO LẠI trước
     * khi kết luận, đừng chép số cũ (CLAUDE.md §2 — dữ liệu cũ tối đa là "nghi là").
     */
    @Test
    fun `ung vien BAC — tran Hamming cho nhanh NCC khong co cua so an toan`() {
        val cases = geometricNoiseCases()
        val viaNcc = ArrayList<Int>()
        for (q in cases) {
            val f = frameFromBits(q)
            if (ManeuverSignature.classifyDetailed(f).amap == null) continue
            val bits = ManeuverSignature.signatureBits(f) ?: continue
            if (ManeuverSignature.classifyStrict(f).amap == null) viaNcc += minHammingToGMaps(bits)
        }
        assertEquals(256, viaNcc.size, "số khung GMaps nhiễu đi qua NCC đã đổi")
        assertEquals(68, viaNcc.max(), "trần trên của minD ở nhánh NCC (đường notification) đã đổi")

        val table = listOf(19, 22, 26, 30, 35).associateWith { c -> viaNcc.count { it > c } }
        assertEquals(
            mapOf(19 to 251, 22 to 220, 26 to 162, 30 to 130, 35 to 93), table,
            "bảng 'trần Hamming → số khung GMaps đổi kết quả' đã đổi",
        )
        // Trần phải ≤ 26 mới chặn được ca sai hướng thấp nhất (depart_right, Hamming 27).
        assertTrue(
            table.getValue(26) > 0,
            "nếu trần 26 không còn làm khung GMaps nào đổi kết quả thì ứng viên (b) đã trở nên khả thi — đo lại",
        )
    }

    // ── (2c) NỬA CỬA CÒN LẠI: rect a11y đo cho CAMERA đi vào kênh ARROW ────────────────────────────────

    /**
     * KHOÁ [P1] B3.53 vòng review — **bằng chứng PIXEL cho nửa cửa mà bản vá tier-rect-cố-định KHÔNG chạm**.
     *
     * Quyết định định tuyến đã khoá ở [CaptureRouterTest]; ở đây khoá phần *vì sao nó nguy hiểm*: khi rect
     * của node CAMERA được đem chấm mũi tên, khớp MỀM vẫn tìm ra một cái tên. Dùng
     * [CaptureCalibration.VIETMAP_CAMERA_SEED] làm đại diện cho "một rect KHÔNG phải mũi tên trong khung
     * VietMap" — rect thật của node camera trên xe là VERIFY-ON-CAR (OQ2/OQ4), nhưng cơ chế không phụ thuộc
     * vào con số cụ thể.
     */
    @Test
    fun `rect KHONG phai mui ten trong khung VietMap — khop MEM van ra ma, khop CUNG im (B3_53 review)`() {
        val r = CaptureCalibration.VIETMAP_CAMERA_SEED
        val soft = LinkedHashMap<String, Int>()
        val strict = LinkedHashMap<String, Int>()
        for (n in VietMapGlyphFrames.maneuverNames) {
            val c = VietMapGlyphFrames.crop(VietMapGlyphFrames.compose(n), r)
            ManeuverSignature.classifyDetailed(c).amap?.let { soft[n] = it }
            ManeuverSignature.classifyStrict(c).amap?.let { strict[n] = it }
        }
        assertEquals(
            mapOf("arrive_straight" to 12), soft,
            "tập khung ra mã qua rect KHÔNG-phải-mũi-tên đã đổi — đo lại trước khi sửa số",
        )
        assertEquals(
            9, VietMapGlyphFrames.expectedAmap("arrive_straight"),
            "họ hướng đúng của khung này đã đổi — bằng chứng 'sai họ' bên dưới phải đo lại",
        )
        assertEquals(
            emptyMap<String, Int>(), strict,
            "khớp CỨNG vẫn ra mã trên crop KHÔNG phải mũi tên ⇒ cổng target ở CaptureRouter là lớp bảo vệ DUY NHẤT",
        )
    }

    // ── helper ─────────────────────────────────────────────────────────────────────────────────────────

    /** amap 3 = rẽ PHẢI thường — họ đúng của cả hai khung Waze fixture (xem [ClusterArrowCalibrationTest]). */
    private val AMAP_TURN_RIGHT = 3


    /** Hamming nhỏ nhất từ chữ ký [bits] tới 38 template GMaps (quy ước khung-vẽ). */
    private fun minHammingToGMaps(bits: String): Int =
        ManeuverRegistry.RAW.minOf { (b, _) -> bits.indices.count { bits[it] != b[it] } }


    private fun frameOf(res: String): PixelFrame {
        val url = javaClass.getResource(res)
        assertNotNull(url, "thiếu fixture $res")
        val img = javax.imageio.ImageIO.read(url)!!
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return ArrayPixelFrame(img.width, img.height, px)
    }

    private fun frameFromBits(bits: String) =
        ArrayPixelFrame(15, 15, IntArray(225) { i -> if (bits[i] == '1') 0xFFFFFFFF.toInt() else 0x00000000 })

    /** 38 template × (8 dịch + dilate + 2 dilate-rồi-dịch) = 418 — GIỮ KHỚP `WazeArrowRegistryTest`. */
    private fun geometricNoiseCases(): List<String> {
        fun shift(bits: String, dx: Int, dy: Int): String {
            val a = CharArray(225) { '0' }
            for (y in 0 until 15) for (x in 0 until 15) {
                val sx = x - dx
                val sy = y - dy
                if (sx in 0..14 && sy in 0..14) a[y * 15 + x] = bits[sy * 15 + sx]
            }
            return String(a)
        }
        fun dilate(bits: String): String {
            val a = CharArray(225) { '0' }
            for (y in 0 until 15) for (x in 0 until 15) {
                val on = bits[y * 15 + x] == '1' ||
                    (x > 0 && bits[y * 15 + x - 1] == '1') || (x < 14 && bits[y * 15 + x + 1] == '1') ||
                    (y > 0 && bits[(y - 1) * 15 + x] == '1') || (y < 14 && bits[(y + 1) * 15 + x] == '1')
                if (on) a[y * 15 + x] = '1'
            }
            return String(a)
        }
        val shifts = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
        val out = ArrayList<String>()
        ManeuverRegistry.RAW.forEach { (bits, _) ->
            shifts.forEach { (dx, dy) -> out.add(shift(bits, dx, dy)) }
            out.add(dilate(bits))
            shifts.take(2).forEach { (dx, dy) -> out.add(shift(dilate(bits), dx, dy)) }
        }
        return out
    }
}
