package com.byd.clusternav.launcher

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * QA3 (2.87, 04/10) — luật GIÁ TRỊ ưu tiên hơn CHÚ THÍCH ([FitValues]).
 *
 * Số đo [ĐO máy ảo QA3, `uiautomator dump` + nhật ký `WidgetFit`, mật độ 1,5]:
 *  - 3×1 có dock `cell=136x99 HORIZONTAL/1 k=0.952`: TextView giờ `06:58` 39×21 (`06:…`) + ngày `04/10` 40×21 ⇒ phần hàng co
 *    giãn = 39 + 40 = 79px; ở sàn 15px giờ VẪN `…` trong 39px ⇒ bề rộng thật của `06:58` ở 15px > 39px;
 *  - 2×1 không dock `VERTICAL/2`, ô 84×124: TextView giờ 62×33 ở cỡ lưới 17sp × 0,952 (≈ 24,3px) hiện `06:…` ⇒ bề rộng thật
 *    của `06:59` ở 24,3px > 62px;
 *  - 2×1 có dock `cell=84x99 HORIZONTAL/1`: mỗi chữ 13–14px ⇒ phần hàng co giãn ≈ 26–27px.
 *
 * Mô hình bề rộng chữ ([em] — Roboto đậm, [SUY]) được CHỈNH cho khớp hai sự thật đo được ở trên (bài đầu tiên khoá điều đó):
 * ước lượng PIL "15,2px ≥ 15px" của bài J1 sai đúng ở chỗ này. Bài khoá LUẬT, không khoá phông.
 */
class FitValuesTest {

    /** Bề rộng ký tự theo em (đậm, chữ số đồng bề trừ `1`). [SUY] — chỉnh theo hai sự thật [ĐO] ở KDoc lớp. */
    private fun em(c: Char): Float = when {
        c == '1' -> 0.50f
        c.isDigit() -> 0.585f
        c == ':' -> 0.30f
        c == '/' -> 0.40f
        c == '.' || c == '·' -> 0.27f
        c == ' ' -> 0.25f
        c == '—' -> 1.0f
        c == '–' -> 0.55f
        c == '%' -> 0.85f
        c == 'µ' -> 0.57f
        c in '฀'..'๿' -> 0.60f
        c.code >= 0x2E80 -> 1.0f
        c.isUpperCase() -> 0.65f
        else -> 0.52f
    }

    private fun width(text: String, px: Float): Float = text.fold(0f) { s, c -> s + em(c) } * px

    /** Như `FitValueRow.contentNeed`: chữ đã chừa chữ số, làm tròn lên + 1px. */
    private fun need(text: String, px: Float): Int {
        if (text.isEmpty()) return 0
        val widest = FitValues.widestDigit { em(it) * px }
        return FitValues.needPx(width(FitValues.headroom(text, widest), px))
    }

    private val k = 10.0f / 10.5f
    private val clockPx = 17f * 1.5f * k          // ≈ 24,3px — cỡ lưới của số ô nén
    private val captionPx = 10.5f * 1.5f * k      // = 15px — chú thích ở sàn
    private val floorPx = 15f
    private val gapPx = FitValues.gapPx(captionPx)      // soát QA4 — khe giá trị/chú thích (2.93 FIT-GRAVITY: lề NGOÀI giá trị)

    /**
     * Soát QA4 — khe NHÌN THẤY (px, ca tệ nhất: chữ rộng ĐÚNG nhu cầu) giữa giá trị và chú thích của phép chia [s] hàng [flex]: cả hai
     * chữ căn GIỮA ô của nó (`WidgetViews.tv` — `Gravity.CENTER`), chú thích `…` lấp đầy ô của nó. 2.93 `FIT-GRAVITY` (đổi có lý
     * do): khe [gap] là lề NGOÀI giữa hai ô (không còn trong bề rộng giá trị) ⇒ ô chú thích = `flex − valueW − khe`.
     */
    private fun visibleGap(flex: Int, s: FitValues.Share, v: Int, c: Int, gap: Int = gapPx): Double {
        val g = if (s.captions && c > 0) gap else 0
        val cw = flex - s.valueW - g
        return (s.valueW - v) / 2.0 + g + (cw - minOf(c, cw)) / 2.0
    }

    @Test
    fun `mo hinh khop hai su that do tren may ao QA3`() {
        assertTrue(width("06:58", floorPx) > 39f, "QA3: `06:58` ở 15px vẫn `…` trong 39px")
        assertTrue(width("06:59", clockPx) > 62f, "QA3: `06:59` ở ≈24,3px `…` trong 62px")
        // Bản J1 so `cần ≤ chỗ + 1px` ⇒ chữ thiếu dưới 1px tính là vừa; nay làm tròn LÊN + 1px.
        assertEquals(40, FitValues.needPx(38.7f))
        assertEquals(40, FitValues.needPx(39.0f))
        assertEquals(0, FitValues.needPx(0f))
    }

    @Test
    fun `QA3 3x1 co dock - gio hien tron, ngay nhuong - ca 5 tieng cung mot chu`() {
        val flex = 79      // [ĐO] 39 + 40
        // Bản lỗi: dạng ngang CHIA ĐÔI ⇒ số được 39px — không vừa kể cả ở sàn 15px.
        assertTrue(need("06:58", floorPx) > flex / 2, "chia đôi thì giờ bị cắt kể cả ở sàn")
        val px = FitValues.valuePx(clockPx, floorPx, flex) { need("06:58", it) }
        assertEquals(clockPx, px, "cả phần hàng đủ cho giờ ở cỡ lưới ⇒ không co")
        val s = FitValues.share(flex, need("06:58", px), need("04/10", captionPx), FitValues.captionMinPx(captionPx), gapPx)
        assertTrue(s.valueW >= need("06:58", px), "giờ hiện TRỌN: ${s.valueW}")
        assertFalse(s.captions, "ngày còn ${flex - need("06:58", px)}px < 2 em ⇒ ẩn hẳn (2.86 để còn 6px vô nghĩa)")
        // Qua phút / qua giờ: chừa chữ số ⇒ không chia lại (không lượt đo mỗi nhịp).
        listOf("06:59", "07:00", "09:59", "10:00", "23:59", "00:00").forEach {
            assertFalse(FitValues.wouldClip(s.valueW, need(it, px)), "$it không được làm chia lại hàng")
        }
    }

    @Test
    fun `QA3 2x1 khong dock xep doc - gio co theo bac toi vua, khong duoi san`() {
        val avail = 62    // [ĐO] TextView giờ rộng 62px (khối dọc, chữ MATCH)
        val px = FitValues.valuePx(clockPx, floorPx, avail) { need("06:59", it) }
        assertTrue(px < clockPx && px >= floorPx, "phải co nhưng không dưới sàn: $px")
        assertTrue(need("06:59", px) <= avail, "ở cỡ mới chữ vừa chỗ: ${need("06:59", px)}")
        val steps = px / clockPx * FitValues.STEPS
        assertEquals(Math.round(steps).toFloat(), steps, 1e-3f, "bậc 1/32 của cỡ lưới")
        val next = clockPx * (Math.round(steps) + 1) / FitValues.STEPS
        assertTrue(need("06:59", next) > avail, "bậc LỚN NHẤT còn vừa")
        assertEquals(px, FitValues.valuePx(clockPx, floorPx, avail) { need("10:00", it) }, "qua giờ không đổi cỡ (chừa chữ số)")
    }

    @Test
    fun `QA3 2x1 co dock - o san van khong vua thi giu san (bo giai phai doi bo cuc)`() {
        val flex = 26
        assertEquals(floorPx, FitValues.valuePx(clockPx, floorPx, flex) { need("06:57", it) })
        val s = FitValues.share(flex, need("06:57", floorPx), need("04/10", captionPx), FitValues.captionMinPx(captionPx), gapPx)
        assertEquals(Pair(flex, false), s.valueW to s.captions, "chú thích nhường hết, giá trị nhận cả hàng — vẫn không đủ ⇒ GridFit 2c")
        assertTrue(need("06:57", floorPx) > flex)
    }

    @Test
    fun `toc do 99 len 100 chia lai dung mot lan, xuong lai 99 khong chia`() {
        val px = clockPx
        val flex = 79
        val cap = need("km/h", captionPx)
        // 2.93 FIT-GRAVITY (đổi có lý do): khe là lề NGOÀI ⇒ chỗ của CHỮ = trọn valueW (đúng phép của `FitValueRow.fit`).
        fun room(s: FitValues.Share) = s.valueW
        val s99 = FitValues.share(flex, need("99", px), cap, FitValues.captionMinPx(captionPx), gapPx)
        assertTrue(s99.captions)
        assertTrue(FitValues.wouldClip(room(s99), need("100", px)), "thêm chữ số ⇒ sẽ cắt ⇒ chia lại")
        val s100 = FitValues.share(flex, need("100", px), cap, FitValues.captionMinPx(captionPx), gapPx)
        assertTrue(s100.valueW >= need("100", px))
        assertFalse(FitValues.wouldClip(room(s100), need("99", px)), "ngắn đi KHÔNG chia lại")
        assertFalse(FitValues.wouldClip(room(s99), need("98", px)))
        assertFalse(FitValues.wouldClip(-1, 500), "khối dọc (không bề rộng tĩnh) không áp")
        // `—` (chưa đọc) → số: một lần chia lại khi số rộng hơn dấu gạch.
        val dash = FitValues.share(flex, need("—", px), cap, FitValues.captionMinPx(captionPx), gapPx)
        assertTrue(FitValues.wouldClip(room(dash), need("100", px)) || room(dash) >= need("100", px))
    }

    @Test
    fun `chu so rong nhat - 09 sang 10, 59 sang 00 khong can cho moi`() {
        val widest = FitValues.widestDigit(::em)
        assertTrue(widest != '1', "`1` hẹp nhất không bao giờ là chữ số chừa")
        assertEquals("88:88".map { if (it == '8') widest else it }.joinToString(""), FitValues.headroom("06:59", widest))
        assertEquals("—", FitValues.headroom("—", widest))
        assertEquals(need("10:00", clockPx), need("06:59", clockPx), "mọi giờ cùng một bề rộng dành")
        assertTrue(width("10:00", clockPx) < width("06:59", clockPx), "chữ thật khác bề rộng — vì vậy mới phải chừa")
    }

    /** Chữ phụ đã dịch của ô PM2.5 (`µg · <mức>`) đọc thẳng tài nguyên 5 tiếng — đúng chữ người lái thấy. */
    private fun pmCaptions(): Map<String, List<String>> {
        val res = listOf(Paths.get("app/src/main/res"), Paths.get("../app/src/main/res")).first(Files::isDirectory)
        return mapOf("vi" to "values", "en" to "values-en", "zh" to "values-zh-rCN", "th" to "values-th", "ms" to "values-ms")
            .mapValues { (_, dir) ->
                val xml = res.resolve(dir).resolve("strings_kachi.xml").toFile().readText()
                listOf("good", "fair", "poor").map { lv ->
                    Regex("""name="kachi_pm_$lv">([^<]*)<""").find(xml)!!.groupValues[1].let { "µg · $it" }
                }
            }
    }

    @Test
    fun `5 tieng - moi cap gia tri chu thich, moi be rong hang - gia tri khong bao gio bi cat khi con cho`() {
        val pairs = pmCaptions().flatMap { (_, caps) -> caps.map { "35" to it } } + listOf(
            "06:58" to "04/10", "100" to "km/h", "85%" to "420 km", "230–250" to "kPa", "2.3–2.5" to "bar", "—" to "µg · —",
        )
        assertEquals(5 * 3 + 6, pairs.size)
        for ((value, caption) in pairs) for (flex in 10..220) {
            val v = need(value, clockPx)
            val c = need(caption, captionPx)
            val min = FitValues.captionMinPx(captionPx)
            val s = FitValues.share(flex, v, c, min, gapPx)
            assertTrue(s.valueW <= flex, "$value/$caption @$flex: không vượt hàng")
            if (flex >= v) assertTrue(s.valueW >= v, "$value/$caption @$flex: giá trị phải TRỌN (${s.valueW} < $v)")
            if (s.captions) assertTrue(flex - s.valueW - gapPx >= minOf(min, c), "$value/$caption @$flex: chú thích hiện thì trọn, hoặc còn ≥ 2 em")
            // Soát QA4 — chú thích hiện thì KHÔNG BAO GIỜ dán sát giá trị (`08:3104/10`, `—km/h`).
            if (s.captions) assertTrue(visibleGap(flex, s, v, c) >= FitValues.GAP_EM * captionPx, "$value/$caption @$flex: khe ${visibleGap(flex, s, v, c)}")
            // Soát QA4 — ĐỔI có lý do: "đủ chỗ" nay gồm cả khe (hàng vừa khít v + c là đúng ca dán chữ của QA4).
            if (flex >= v + c + gapPx) assertTrue(s.captions && flex - s.valueW - gapPx >= c, "$value/$caption @$flex: đủ chỗ thì cả hai trọn")
        }
    }

    @Test
    fun `du cho thi chia phan du deu hai ben, khong chu thich thi gia tri giu nhu cau + nua phan du`() {
        // Khe 0 = phép chia của QA3 (giữ nguyên); khe 8 = soát QA4 (giữ TRƯỚC chú thích) — 2.93 FIT-GRAVITY: khe là lề NGOÀI
        // giá trị ⇒ không còn cộng vào valueW (đổi có lý do: hai ca đầu dưới mất đúng 8 px khe so với bản QA4).
        assertEquals(FitValues.Share(40 + 10, true), FitValues.share(100, 40, 40, 30, 0))
        assertEquals(FitValues.Share(40, true), FitValues.share(75, 40, 40, 30, 0), "chú thích `…` còn 35px ≥ 30")
        assertEquals(FitValues.Share(75, false), FitValues.share(75, 40, 40, 36, 0), "còn 35 < 36 ⇒ ẩn, giá trị lấy cả hàng")
        assertEquals(FitValues.Share(40 + 30, true), FitValues.share(100, 40, 0, 0, 0), "không chú thích đang hiện")
        assertEquals(FitValues.Share(0, false), FitValues.share(-5, 40, 0, 0, 0))
        assertEquals(FitValues.Share(40 + 6, true), FitValues.share(100, 40, 40, 30, 8), "đủ cả khe ⇒ nhu cầu + nửa phần dư (khe ngoài)")
        assertEquals(FitValues.Share(40, true), FitValues.share(85, 40, 40, 30, 8), "sau khe chú thích còn 37 ≥ 30 ⇒ `…`")
        assertEquals(FitValues.Share(75, false), FitValues.share(75, 40, 40, 30, 8), "sau khe còn 27 < 30 ⇒ ẩn (không dán sát)")
        assertEquals(FitValues.Share(40 + 30, true), FitValues.share(100, 40, 0, 0, 8), "không chú thích có chữ ⇒ không giữ khe")
    }

    @Test
    fun `khoi doc - chu thich nhuong khi khoi cao hon o`() {
        assertFalse(FitValues.stackYields(contentPx = 100, boxPx = 99), "sai số 1px")
        assertTrue(FitValues.stackYields(contentPx = 104, boxPx = 99))
        assertFalse(FitValues.stackYields(contentPx = 85, boxPx = 99))
    }

    @Test
    fun `valuePx - vua thi giu co luoi, chu da o san khong co, khong vua o san thi giu san`() {
        assertEquals(clockPx, FitValues.valuePx(clockPx, floorPx, 200) { need("06:58", it) })
        assertEquals(66, need("06:58", clockPx))
        assertEquals(clockPx, FitValues.valuePx(clockPx, floorPx, 66) { need("06:58", it) }, "vừa khít ⇒ giữ cỡ lưới")
        assertTrue(FitValues.valuePx(clockPx, floorPx, 65) { need("06:58", it) } < clockPx, "thiếu 1px là THIẾU (QA3) — không sai số")
        assertEquals(captionPx, FitValues.valuePx(captionPx, floorPx, 5) { need("04/10", it) }, 1e-4f, "chú thích ở sàn không co")
        assertEquals(floorPx, FitValues.valuePx(clockPx, floorPx, 3) { need("06:58", it) })
        assertEquals(clockPx, FitValues.valuePx(clockPx, floorPx, 0) { need("", it) }, "chữ rỗng")
        var calls = 0
        FitValues.valuePx(clockPx, floorPx, 50) { calls++; need("06:58", it) }
        assertTrue(calls <= 6, "tìm nhị phân, không quét 32 bậc: $calls")
    }

    /**
     * Soát QA4 [P3, ĐO máy ảo] khung 3×1 hai mục [đồng hồ, lốp]: giờ nhận ĐÚNG nhu cầu, ngày nhận phần còn lại bắt đầu ngay pixel kế
     * ⇒ trên màn đọc thành một chuỗi `08:3104/10`. Hàng VỪA KHÍT (giá trị + chú thích, không dư) phải giữ khe — hoặc chú thích
     * nhường; không bao giờ dán sát.
     */
    @Test
    fun `hang vua khit - giu khe giua gia tri va chu thich, khong dan sat`() {
        val v = need("08:31", clockPx)
        val c = need("04/10", captionPx)
        val min = FitValues.captionMinPx(captionPx)
        val flex = v + c
        assertEquals(0.0, visibleGap(flex, FitValues.share(flex, v, c, min, 0), v, c, gap = 0), "bản QA3 (không khe): dán sát — điều QA4 thấy")
        val s = FitValues.share(flex, v, c, min, gapPx)
        assertTrue(s.valueW >= v, "giá trị vẫn TRỌN")
        if (s.captions) assertTrue(visibleGap(flex, s, v, c) >= FitValues.GAP_EM * captionPx, "khe ${visibleGap(flex, s, v, c)}")
        // 2.93 FIT-GRAVITY (đổi có lý do): khe là lề NGOÀI có chủ ⇒ dành ĐÚNG một khe (bản QA4 dành gấp đôi trong bề rộng giá trị
        // vì chữ căn giữa — nửa phần dành nằm phía ngoài, phí ≈ 0,25 em mỗi hàng).
        assertTrue(gapPx >= FitValues.GAP_EM * captionPx && gapPx < 2 * FitValues.GAP_EM * captionPx, "một khe, không gấp đôi")
        // Đủ chỗ cho cả khe ⇒ cả hai trọn.
        val wide = FitValues.share(flex + gapPx, v, c, min, gapPx)
        assertTrue(wide.captions && flex + gapPx - wide.valueW - gapPx >= c && visibleGap(flex + gapPx, wide, v, c) >= FitValues.GAP_EM * captionPx)
    }

    /**
     * Soát vòng 6 [P3] — khối DỌC (lưới không đọc được), bề rộng chữ nằm giữa nhu cầu của `99` và `100` ở cỡ lưới: nhịp đổ tại chỗ
     * chỉ CO (`FitScale.fitValues` tick + [FitValues.holds]) ⇒ 99 → 100 đổi cỡ MỘT lần, 100 → 99 giữ cỡ (luật R-WF2c "ngắn đi KHÔNG
     * chia lại"). Bản lỗi: mỗi lần qua lại đổi cỡ = `requestLayout` + số nhảy cỡ mỗi giây khi chạy quanh 100 km/h.
     */
    @Test
    fun `khoi doc - 99 len 100 co mot lan, xuong 99 giu co`() {
        val avail = 36
        assertTrue(need("99", clockPx) <= avail && need("100", clockPx) > avail, "bề rộng nằm giữa hai nhu cầu")
        fun writes(hold: Boolean): Int {
            var size = FitValues.valuePx(clockPx, floorPx, avail) { need("99", it) }     // lượt khớp: 99 vừa ở cỡ lưới
            assertEquals(clockPx, size)
            var n = 0
            listOf("100", "99", "100", "99", "98", "100").forEach { text ->
                val needAt = { px: Float -> need(text, px) }
                if (hold && FitValues.holds(size, avail, needAt)) return@forEach
                val px = FitValues.valuePx(clockPx, floorPx, avail, needAt)
                if (abs(px - size) > 0.01f) { size = px; n++ }
                assertTrue(needAt(size) <= avail, "$text: giá trị luôn TRỌN")
            }
            return n
        }
        assertEquals(1, writes(hold = true), "chỉ CO một lần (99 → 100); ngắn đi giữ cỡ")
        assertEquals(5, writes(hold = false), "bản lỗi: mỗi lần qua lại 99 ↔ 100 đổi cỡ")
        assertFalse(FitValues.holds(0f, avail) { 0 }, "chữ cỡ 0 (chưa áp) không giữ")
    }

    /**
     * Soát vòng 6 [P3] — chú thích NHƯỜNG (0×0) không còn trong cây trợ năng ⇒ chữ của nút GIÁ TRỊ mang cả đơn vị / tên datum
     * (`FitValueRow` — delegate trợ năng của giá trị). Tên datum đứng trước, đơn vị đứng sau.
     */
    @Test
    fun `chu thich nhuong - chu tro nang cua gia tri chua ca hai`() {
        assertEquals("100 km/h", FitValues.spoken("100", listOf("km/h"), emptyList()))
        assertEquals("08:31 04/10", FitValues.spoken("08:31", listOf("04/10"), emptyList()))
        assertEquals("Fan level —", FitValues.spoken("—", emptyList(), listOf("Fan level")))
        assertEquals("Seat ventilation level 2 bar", FitValues.spoken(" 2 ", listOf("bar"), listOf("Seat ventilation level", "")))
        val say = FitValues.spoken("23°", listOf("µg · Tốt"), listOf("Nhiệt độ"))
        listOf("23°", "µg · Tốt", "Nhiệt độ").forEach { assertTrue(it in say, "$it phải có trong `$say`") }
    }
}
