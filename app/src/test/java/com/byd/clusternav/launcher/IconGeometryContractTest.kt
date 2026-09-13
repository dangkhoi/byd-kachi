package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ U7 · MỘT HỆ ⇒ MỌI ICON PHẢI **CÙNG MỘT Ô QUANG HỌC** ════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-icon-set-v2.html` §3 **R3** (*"ô quang học 20×20"*) + **R4** (*"cùng phong cách, cùng
 * tỉ lệ khối"*).
 *
 * ## Vì sao [IconStyleContractTest] CHƯA đủ
 * Bài kia canh **thuộc tính**: một độ dày nét, đầu/khớp nét tròn, khung vẽ 24×24, không màu riêng. Cả bốn đều
 * xanh với một bộ icon **lệch cỡ nhau**, vì không phép nào trong đó nhìn tới HÌNH HỌC của đường vẽ. [ĐO]
 * 2026-09-13, chạy phép đo dưới đây lần đầu trên bộ icon lúc ấy:
 *  • **4 tệp TRÀN** khỏi ô 20×20 (`ic_motor` 22.2 · `ic_temp_out` 23.8 · `ic_window_open`/`ic_window_close` 23.2)
 *    — nét sát mép bị ô chứa cắt cụt hoặc dính vào ô bên cạnh;
 *  • **10 tệp QUÁ NHỎ** (cạnh lớn nhất chỉ 10–14.6 trên ô 20) — `ic_play`/`ic_next`/`ic_prev` đứng cạnh
 *    `ic_battery` (18.3) thì đọc ra "hai cỡ chữ khác nhau" dù cùng 24dp và cùng nét 1.6.
 * Không có phép đo này thì cả hai loại lệch đó **không có gì bắt được**, và chúng chính là thứ owner gọi là
 * *"bộ icon chưa đồng bộ"*.
 *
 * ## Đo bằng cách nào — LẤY MẪU đường cong, không đọc toạ độ thô
 * Hộp bao tính từ **toạ độ đã cộng nửa nét** (nét 1.6 ⇒ mỗi phía nở 0.8 — đó mới là mực thật trên màn). Đường
 * cong Bézier và cung tròn được **lấy mẫu** ([SAMPLES] điểm/đoạn) thay vì lấy điểm điều khiển: bao lồi của điểm
 * điều khiển luôn RỘNG HƠN đường thật, nên nó báo tràn ở những tệp không hề tràn (thử lần đầu: 9 báo động giả).
 */
class IconGeometryContractTest {

    /** Ô quang học: hình phải nằm trong [MARGIN]..(24 − [MARGIN]). Dung sai 0.05 cho số thập phân của path. */
    private val margin = 2.0
    private val tolerance = 0.05

    /**
     * Cạnh lớn nhất TỐI THIỂU của một icon trong ô 20×20.
     *
     * 16/20 = 80% ô. Không phải 20 (icon tròn/vuông phủ kín ô sẽ nặng hơn hẳn icon mảnh cùng cỡ) và không phải
     * 14 (bằng đúng số ĐO ĐƯỢC của nhóm tệp lệch, tức ghim lại chính cái lỗi). 16 là ngưỡng mà [ĐO] ảnh contact
     * sheet 2026-09-13 không còn đọc ra "ô này nhỏ hơn ô kia".
     */
    private val minMajor = 16.0

    private val samples = 48

    /** Tệp được phép PHÁ hai luật trên — mỗi dòng phải có lý do TẠI CHỖ. */
    private val exceptions: Map<String, String> = mapOf(
        "ic_corner_cut.xml" to
            "MẶT NẠ che góc vuông, không phải icon: nó PHẢI phủ trọn 24×24, chừa lề là hở góc vuông ra. " +
                "Cùng lý do đã ghi ở IconStyleContractTest.fullBleedExceptions",
    )

    // ── đọc tệp ────────────────────────────────────────────────────────────────────────────────────

    private fun drawableDir(): Path = SourceRoots.path("src/main/res/drawable")

    private fun icons(): List<Pair<String, String>> =
        Files.list(drawableDir()).use { s ->
            s.filter { it.fileName.toString().let { n -> n.startsWith("ic_") && n.endsWith(".xml") } }
                .sorted()
                .map { it.fileName.toString() to it.toFile().readText() }
                .toList()
        }

    private fun attr(el: String, name: String): String? =
        Regex("android:$name=\"([^\"]*)\"").find(el)?.groupValues?.get(1)

    // ── lấy mẫu pathData ───────────────────────────────────────────────────────────────────────────

    private data class Box(var x0: Double, var y0: Double, var x1: Double, var y1: Double) {
        fun add(x: Double, y: Double) {
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
        }

        fun grow(by: Double) = Box(x0 - by, y0 - by, x1 + by, y1 + by)

        fun union(o: Box) = Box(min(x0, o.x0), min(y0, o.y0), max(x1, o.x1), max(y1, o.y1))

        val w: Double get() = x1 - x0
        val h: Double get() = y1 - y0
        override fun toString() = "[%.2f,%.2f]-[%.2f,%.2f]".format(x0, y0, x1, y1)
    }

    private val numRe = Regex("[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?")

    private fun argCount(c: Char) = when (c) {
        'M', 'L', 'T' -> 2; 'H', 'V' -> 1; 'C' -> 6; 'S', 'Q' -> 4; 'A' -> 7; else -> 0
    }

    /** Hộp bao của một `pathData`, đường cong đã được lấy mẫu. `null` nếu không có điểm nào. */
    private fun bounds(d: String): Box? {
        val parts = Regex("([MmLlHhVvCcSsQqTtAaZz])").split(d)
        val cmds = Regex("([MmLlHhVvCcSsQqTtAaZz])").findAll(d).map { it.value }.toList()
        var box: Box? = null
        fun put(x: Double, y: Double) {
            box = box?.also { it.add(x, y) } ?: Box(x, y, x, y)
        }

        var cx = 0.0; var cy = 0.0; var sx = 0.0; var sy = 0.0
        var pcx = 0.0; var pcy = 0.0; var prev = ' '
        cmds.forEachIndexed { idx, raw ->
            val c = raw[0].uppercaseChar()
            val rel = raw[0].isLowerCase()
            val args = numRe.findAll(parts[idx + 1]).map { it.value.toDouble() }.toList()
            val n = argCount(c)
            if (c == 'Z') { put(sx, sy); cx = sx; cy = sy; prev = c; return@forEachIndexed }
            var k = 0
            var cc = c
            while (k + n <= args.size) {
                val a = args.subList(k, k + n); k += n
                when (cc) {
                    'M' -> {
                        cx = if (rel) cx + a[0] else a[0]; cy = if (rel) cy + a[1] else a[1]
                        sx = cx; sy = cy; put(cx, cy); cc = 'L'
                    }
                    'L' -> { cx = if (rel) cx + a[0] else a[0]; cy = if (rel) cy + a[1] else a[1]; put(cx, cy) }
                    'H' -> { cx = if (rel) cx + a[0] else a[0]; put(cx, cy) }
                    'V' -> { cy = if (rel) cy + a[0] else a[0]; put(cx, cy) }
                    'C', 'S' -> {
                        val p1x: Double; val p1y: Double
                        if (cc == 'C') {
                            p1x = if (rel) cx + a[0] else a[0]; p1y = if (rel) cy + a[1] else a[1]
                        } else {
                            p1x = if (prev == 'C' || prev == 'S') 2 * cx - pcx else cx
                            p1y = if (prev == 'C' || prev == 'S') 2 * cy - pcy else cy
                        }
                        val o = if (cc == 'C') 2 else 0
                        val p2x = if (rel) cx + a[o] else a[o]; val p2y = if (rel) cy + a[o + 1] else a[o + 1]
                        val p3x = if (rel) cx + a[o + 2] else a[o + 2]; val p3y = if (rel) cy + a[o + 3] else a[o + 3]
                        for (i in 0..samples) {
                            val t = i.toDouble() / samples; val u = 1 - t
                            put(
                                u * u * u * cx + 3 * u * u * t * p1x + 3 * u * t * t * p2x + t * t * t * p3x,
                                u * u * u * cy + 3 * u * u * t * p1y + 3 * u * t * t * p2y + t * t * t * p3y,
                            )
                        }
                        pcx = p2x; pcy = p2y; cx = p3x; cy = p3y
                    }
                    'Q', 'T' -> {
                        val p1x: Double; val p1y: Double
                        if (cc == 'Q') {
                            p1x = if (rel) cx + a[0] else a[0]; p1y = if (rel) cy + a[1] else a[1]
                        } else {
                            p1x = if (prev == 'Q' || prev == 'T') 2 * cx - pcx else cx
                            p1y = if (prev == 'Q' || prev == 'T') 2 * cy - pcy else cy
                        }
                        val o = if (cc == 'Q') 2 else 0
                        val p2x = if (rel) cx + a[o] else a[o]; val p2y = if (rel) cy + a[o + 1] else a[o + 1]
                        for (i in 0..samples) {
                            val t = i.toDouble() / samples; val u = 1 - t
                            put(u * u * cx + 2 * u * t * p1x + t * t * p2x, u * u * cy + 2 * u * t * p1y + t * t * p2y)
                        }
                        pcx = p1x; pcy = p1y; cx = p2x; cy = p2y
                    }
                    'A' -> {
                        val ex = if (rel) cx + a[5] else a[5]; val ey = if (rel) cy + a[6] else a[6]
                        arc(cx, cy, a[0], a[1], a[2], a[3].toInt(), a[4].toInt(), ex, ey, ::put)
                        cx = ex; cy = ey; put(cx, cy)
                    }
                }
                prev = cc
            }
        }
        return box
    }

    /** Cung tròn SVG: đổi từ dạng "hai đầu mút" sang tâm–góc rồi lấy mẫu (không xấp xỉ bằng hộp bán kính). */
    private fun arc(
        x1: Double, y1: Double, rxIn: Double, ryIn: Double, deg: Double,
        laf: Int, sf: Int, x2: Double, y2: Double, put: (Double, Double) -> Unit,
    ) {
        if (rxIn == 0.0 || ryIn == 0.0 || (x1 == x2 && y1 == y2)) { put(x2, y2); return }
        val phi = Math.toRadians(deg); val cp = cos(phi); val sp = sin(phi)
        val dx2 = (x1 - x2) / 2; val dy2 = (y1 - y2) / 2
        val x1p = cp * dx2 + sp * dy2; val y1p = -sp * dx2 + cp * dy2
        var rx = abs(rxIn); var ry = abs(ryIn)
        val lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if (lam > 1) { val s = sqrt(lam); rx *= s; ry *= s }
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val num = rx * rx * ry * ry - den
        var co = if (den == 0.0) 0.0 else sqrt(max(0.0, num / den))
        if (laf == sf) co = -co
        val cxp = co * rx * y1p / ry; val cyp = -co * ry * x1p / rx
        val ccx = cp * cxp - sp * cyp + (x1 + x2) / 2
        val ccy = sp * cxp + cp * cyp + (y1 + y2) / 2
        fun ang(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val d = hypot(ux, uy) * hypot(vx, vy)
            if (d == 0.0) return 0.0
            val a = acos(max(-1.0, min(1.0, (ux * vx + uy * vy) / d)))
            return if (ux * vy - uy * vx < 0) -a else a
        }
        val ux = (x1p - cxp) / rx; val uy = (y1p - cyp) / ry
        val vx = (-x1p - cxp) / rx; val vy = (-y1p - cyp) / ry
        val t1 = ang(1.0, 0.0, ux, uy)
        var dt = ang(ux, uy, vx, vy)
        if (sf == 0 && dt > 0) dt -= 2 * Math.PI
        if (sf == 1 && dt < 0) dt += 2 * Math.PI
        for (i in 0..samples) {
            val t = t1 + dt * i / samples
            put(ccx + rx * cos(t) * cp - ry * sin(t) * sp, ccy + rx * cos(t) * sp + ry * sin(t) * cp)
        }
    }

    /** Hộp bao của CẢ tệp — mực thật trên màn, tức đã cộng nửa nét cho những đường có vẽ nét. */
    private fun inkBox(xml: String): Box? {
        var all: Box? = null
        Regex("<path\\b.*?/>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            val el = m.value
            val d = attr(el, "pathData") ?: return@forEach
            val stroked = attr(el, "strokeColor")?.replace("#", "")?.lowercase()
                .let { it != null && it != "00000000" }
            val half = if (stroked) (attr(el, "strokeWidth")?.toDoubleOrNull() ?: 0.0) / 2 else 0.0
            val b = bounds(d)?.grow(half) ?: return@forEach
            all = all?.union(b) ?: b
        }
        return all
    }

    // ── 1 · mọi icon nằm TRONG ô quang học 20×20 ───────────────────────────────────────────────────

    @Test
    fun `moi icon nam trong o quang hoc 20x20`() {
        val bad = icons().filterNot { it.first in exceptions }.mapNotNull { (name, xml) ->
            val b = inkBox(xml) ?: return@mapNotNull "$name → không đọc được đường nào"
            val lo = margin - tolerance
            val hi = 24 - margin + tolerance
            if (b.x0 >= lo && b.y0 >= lo && b.x1 <= hi && b.y1 <= hi) null else "$name → $b"
        }
        assertEquals(
            emptyList<String>(), bad,
            "hình tràn khỏi ô quang học ${margin}..${24 - margin} ⇒ nét sát mép bị ô chứa cắt cụt, và hai icon " +
                "cạnh nhau trong lưới thì dính vào nhau",
        )
    }

    // ── 2 · và KHÔNG icon nào nhỏ hơn hẳn phần còn lại ─────────────────────────────────────────────

    @Test
    fun `khong icon nao nho hon han phan con lai`() {
        val bad = icons().filterNot { it.first in exceptions }.mapNotNull { (name, xml) ->
            val b = inkBox(xml) ?: return@mapNotNull "$name → không đọc được đường nào"
            val major = max(b.w, b.h)
            if (major >= minMajor - tolerance) null else "$name → cạnh lớn nhất %.2f".format(major)
        }
        assertEquals(
            emptyList<String>(), bad,
            "cạnh lớn nhất phải ≥ $minMajor trên ô 20 (80%): icon nhỏ hơn thì đứng cạnh icon khác trong CÙNG " +
                "một lưới sẽ đọc ra 'hai cỡ', dù cả hai cùng 24dp và cùng nét 1.6",
        )
    }

    // ── 3 · chốt chống bộ quét hỏng + danh sách loại trừ tự rữa ────────────────────────────────────

    /**
     * Và mọi hình xe mới phải có **một dòng trong [KachiTheme.iconRes]** — không thì nó là tệp mồ côi (R5).
     *
     * ⚠ Bài này cũng là lý do tệp test nằm ở `:app`: nó quét tài nguyên **và** bảng tra của `:app`
     * (`LayeringRulesTest` bắt test nào chỉ dùng logic `:core` phải dời sang `:core`, và ngược lại thì luật
     * "bài quét module X phải nằm trong module X" của [IconStyleContractTest] giữ nó ở đây).
     */
    @Test
    fun `moi hinh xe deu co mot dong trong bang tra cua KachiTheme`() {
        val table = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiTheme.kt")
        val cars = icons().map { it.first.removeSuffix(".xml") }.filter { it.startsWith("ic_car_") }
        assertTrue(cars.size >= 55) { "chỉ thấy ${cars.size} hình xe — bài đang quét vùng sai" }
        assertEquals(
            emptyList<String>(),
            cars.filterNot { Regex("R\\.drawable\\.$it\\b").containsMatchIn(table) },
            "hình xe không có dòng trong KachiTheme.iconRes ⇒ tệp mồ côi (spec R5: 0 icon mồ côi)",
        )
    }

    @Test
    fun `phep do that su chay tren ca bo icon`() {
        val all = icons()
        assertTrue(all.size >= 100) { "chỉ thấy ${all.size} tệp icon — bài đang quét vùng sai" }
        val measured = all.count { inkBox(it.second) != null }
        assertEquals(all.size, measured, "có tệp icon không đo được hộp bao — pathData lạ hoặc bộ đọc hỏng")
        val names = all.map { it.first }.toSet()
        exceptions.forEach { (f, why) ->
            assertTrue(f in names) { "$f không còn tồn tại — bỏ khỏi exceptions" }
            assertTrue(why.length >= 40) { "$f: lý do quá mỏng" }
        }
    }
}
