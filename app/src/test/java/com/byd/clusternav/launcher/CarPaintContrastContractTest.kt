package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import com.byd.clusternav.testsupport.Wcag.fmt
import com.byd.clusternav.testsupport.Wcag.ratio
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VISUAL-REFRESH P3 · R8 AC8.3/AC8.5 · §4.8 luật (a) — MÀU SƠN đo tương phản BẰNG MÁY, hai đầu gradient ═══════
 *
 * Với mỗi màu sơn × hai bảng (nền TỐI/SÁNG): đo **cả hai đầu** gradient so với nền; đầu tệ nhất < 3:1 ⇒ app **tự** bật
 * viền `partLine` (không cấm chọn). Bảng ghi ra `docs/diagnostics/visual-refresh-2026-09-16/contrast-table-paint.md`
 * (AC6.6 — sinh bằng máy, không chép tay). Hai bản của cùng bảng — `KachiPaletteSeeds.CAR_PAINTS` (chạy) và
 * `design/car/paint.json` (thiết kế, `gen-car.py` điền `forceOutline`) — phải nói cùng một điều.
 */
class CarPaintContrastContractTest {

    private data class Row(val paint: CarPaint, val theme: String, val bg: String, val top: Double, val bottom: Double) {
        val worst get() = minOf(top, bottom)
        val outline get() = CarPaint.outlineNeeded(worst)
    }

    private fun rows(): List<Row> = CarPaint.values().flatMap { p ->
        val (from, to) = KachiPaletteSeeds.CAR_PAINTS.getValue(p)
        listOf("TỐI" to KachiPalette.DARK.bg, "SÁNG" to KachiPalette.LIGHT.bg).map { (name, bg) ->
            Row(p, name, bg, ratio(from, bg), ratio(to, bg))
        }
    }

    @Test
    fun `moi mau son co du hai dau va viet tu dong bat khi cham nen`() {
        val r = rows()
        assertEquals(CarPaint.values().size * 2, r.size)
        // Trắng ngọc trai (mặc định) phải KHÔNG cần viền trên nền tối — nếu không, mặc định đã là ca xấu nhất.
        assertTrue(!r.first { it.paint == CarPaint.PEARL && it.theme == "TỐI" }.outline, "sơn mặc định cần viền trên nền tối — chọn lại mặc định")
        // Mọi màu có viền bắt buộc ở ít nhất một bảng vẫn phải được CHỌN (AC8.5: không cấm) — chỉ kiểm rằng luật là quyết định được.
        r.forEach { assertTrue(it.worst > 1.0) }
    }

    /** `design/car/paint.json` (đã điền bởi gen-car.py) và bảng chạy phải cùng mã màu và cùng kết luận viền. */
    @Test
    fun `paint json va KachiPaletteSeeds noi cung mot dieu`() {
        val json = JSONObject(SourceRoots.path("../design/car/paint.json").toFile().readText())
        val paints = json.getJSONArray("paints")
        val byId = (0 until paints.length()).map { paints.getJSONObject(it) }.associateBy { it.getString("id") }
        assertEquals(CarPaint.values().map { it.id }.toSet(), byId.keys, "danh sách màu sơn lệch giữa :core và paint.json")
        assertEquals(json.getString("default"), CarPaint.DEFAULT.id)
        assertEquals(json.getDouble("outlineFloor"), CarPaint.OUTLINE_FLOOR, 1e-9)
        CarPaint.values().forEach { p ->
            val j = byId.getValue(p.id)
            val (from, to) = KachiPaletteSeeds.CAR_PAINTS.getValue(p)
            assertEquals(j.getString("from").lowercase(), from.lowercase(), "${p.id}: đỉnh gradient lệch")
            assertEquals(j.getString("to").lowercase(), to.lowercase(), "${p.id}: đáy gradient lệch")
        }
        // Kết luận viền: JSON đo trên hai nền thiết kế (#0a0d13 · #eef1f6) — bằng chính bg của hai bảng ⇒ phải trùng.
        assertEquals(KachiPalette.DARK.bg.lowercase(), json.getJSONObject("backgrounds").getString("dark").lowercase())
        assertEquals(KachiPalette.LIGHT.bg.lowercase(), json.getJSONObject("backgrounds").getString("light").lowercase())
        rows().forEach { r ->
            val want = byId.getValue(r.paint.id).getJSONObject("forceOutline").getBoolean(if (r.theme == "TỐI") "dark" else "light")
            assertEquals(want, r.outline, "${r.paint.id} @ ${r.theme}: viền bắt buộc lệch giữa paint.json và KachiCarPaint (worst ${fmt(r.worst)})")
        }
    }

    @Test
    fun `sinh bang do tuong phan mau son`() {
        val out = StringBuilder()
        out.append("# Tương phản MÀU SƠN xe — sinh bằng máy (`CarPaintContrastContractTest`, VISUAL-REFRESH P3 · §4.8 luật (a))\n\n")
        out.append("Đo **cả hai đầu** gradient so với nền của từng bảng; đầu tệ nhất < 3:1 ⇒ app tự bật viền `partLine`. Không chép tay.\n\n")
        out.append("| Màu sơn | Bảng | nền | đỉnh | đáy | tệ nhất | viền bắt buộc |\n|---|---|---|---|---|---|---|\n")
        rows().forEach { r ->
            out.append("| ${r.paint.id} | ${r.theme} | `${r.bg}` | ${fmt(r.top)} | ${fmt(r.bottom)} | **${fmt(r.worst)}** | ${if (r.outline) "✅ bật" else "—"} |\n")
        }
        val root = generateSequence(
            SourceRoots.path("src/main/java/com/byd/clusternav/launcher/KachiPalette.kt").toAbsolutePath().toFile(),
        ) { it.parentFile }.firstOrNull { java.io.File(it, "docs/diagnostics").isDirectory }
            ?: error("không tìm thấy gốc kho (thư mục chứa docs/diagnostics)")
        val target = java.io.File(root, "docs/diagnostics/visual-refresh-2026-09-16/contrast-table-paint.md")
        target.parentFile.mkdirs()
        target.writeText(out.toString())
        assertTrue(target.length() > 200)
    }
}
