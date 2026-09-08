package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.ArrayPixelFrame
import com.byd.clusternav.navigation.PixelFrame
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Nạp bộ fixture **khung VietMap ghép** dùng chung cho các test off-car (B3.47/B3.53).
 *
 * `core/src/test/resources/diagnostics/vietmap-glyph/` gồm khung nền THẬT 1920×1080 (VietMap Live 3.3.4 đang
 * dẫn, dpi 240, đã xoá riêng mũi tên khỏi banner), 93 PNG glyph render từ chính asset APK, và `index.json`
 * (gốc dán + số đo). Dán glyph lên nền tại `origin` ⇒ khung y như máy vẽ ra. Độ trung thực đã kiểm 3 lần
 * độc lập — xem `docs/diagnostics/vietmap-glyph-gate-measurement-2026-08-23.md` §2 và KDoc
 * [VietMapGlyphGateTest].
 *
 * ⚠ [VietMapGlyphGateTest] hiện GIỮ BẢN SAO RIÊNG của cùng logic nạp này (nó ra đời trước). Không gộp ở
 * B3.53 để tránh write-conflict với việc B3.52 đang sửa chính file đó; gộp khi B3.52 chạm tới —
 * ghi trong `docs/PROJECT-BACKLOG.md` mục B3.53.
 */
internal object VietMapGlyphFrames {

    const val DPI = 240

    /** 6 icon KHÔNG phải chỉ dẫn rẽ (nút đóng, cờ đích, biển đường, mũi tên lên-xuống danh sách). */
    private val NON_MANEUVER = setOf("close", "flag", "updown", "road_icon_left", "road_icon_right", "invalid")

    private fun png(path: String): BufferedImage {
        val url = VietMapGlyphFrames::class.java.getResource(path) ?: error("thiếu fixture $path")
        return ImageIO.read(url)!!
    }

    private fun pixels(img: BufferedImage): IntArray =
        IntArray(img.width * img.height).also { img.getRGB(0, 0, img.width, img.height, it, 0, img.width) }

    private val index: String by lazy {
        VietMapGlyphFrames::class.java.getResourceAsStream("/diagnostics/vietmap-glyph/index.json")!!
            .bufferedReader().readText()
    }

    private val origin: Pair<Int, Int> by lazy {
        val m = Regex(""""origin"\s*:\s*\[\s*(\d+)\s*,\s*(\d+)\s*]""").find(index)
            ?: error("index.json thiếu \"origin\"")
        m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    private val base: BufferedImage by lazy { png("/diagnostics/vietmap-glyph/_base-vietmap-1920x1080-d240.png") }

    /** Kích thước khung nền — cũng là kích thước display để tra bảng rect cố định. */
    val width: Int get() = base.width
    val height: Int get() = base.height

    /** Tên 87 maneuver (đã loại 6 icon không phải chỉ dẫn), sắp xếp ổn định. */
    val maneuverNames: List<String> by lazy {
        Regex(""""([a-z_]+)"\s*:\s*\{""").findAll(index)
            .map { it.groupValues[1] }
            .filter { it != "glyphs" && it !in NON_MANEUVER }
            .toList()
            .sorted()
    }

    /** Dán glyph [name] lên khung nền tại `origin` ⇒ khung 1920×1080 để chạy locator/rect cố định. */
    fun compose(name: String): PixelFrame {
        val px = pixels(base)
        val g = png("/diagnostics/vietmap-glyph/$name.png")
        val gp = pixels(g)
        val (ox, oy) = origin
        for (y in 0 until g.height) {
            val ty = oy + y
            if (ty !in 0 until base.height) continue
            for (x in 0 until g.width) {
                val tx = ox + x
                if (tx in 0 until base.width) px[ty * base.width + tx] = gp[y * g.width + x]
            }
        }
        return ArrayPixelFrame(base.width, base.height, px)
    }

    /** Crop [r] khỏi [src] (r phải nằm trọn trong src). */
    fun crop(src: PixelFrame, r: CropRect): PixelFrame {
        val all = src.argb()!!
        val out = IntArray(r.width * r.height)
        for (y in 0 until r.height) System.arraycopy(all, (r.top + y) * src.width + r.left, out, y * r.width, r.width)
        return ArrayPixelFrame(r.width, r.height, out)
    }

    /**
     * Họ hướng suy từ TÊN asset — chỉ những họ có ánh xạ AMAP KHÔNG mập mờ; null = không dùng làm canary.
     * Giữ khớp `ManeuverSignature.nameToAmap` (và bản sao trong [VietMapGlyphGateTest.expectedAmap]).
     */
    fun expectedAmap(name: String): Int? = when {
        name.startsWith("merge") -> 9                                   // nhập làn → ĐI THẲNG
        name.contains("rotary") || name.contains("roundabout") -> null  // vòng xuyến: 11/12, không phải trái/phải
        name.endsWith("uturn") -> null                                  // quay đầu: 8/19 tuỳ chiều đường
        name.contains("slight_left") -> 4
        name.contains("slight_right") -> 5
        name.contains("sharp_left") -> 6
        name.contains("sharp_right") -> 7
        name.endsWith("_left") -> 2
        name.endsWith("_right") -> 3
        name.endsWith("straight") || name == "continue" -> 9
        else -> null
    }
}
