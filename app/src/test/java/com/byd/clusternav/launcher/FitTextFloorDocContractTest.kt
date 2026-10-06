package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · FIT-TEXT-MIN-DOC — KDoc của sàn chữ [KachiBars.FIT_TEXT_MIN] phải nói ĐÚNG cỡ chữ thật trong mã ═════════
 *
 * [ĐO mã 05/10] KDoc cũ nói *"chữ không bao giờ bị co dưới 10sp … 10 = chữ nhỏ nhất đang ship"* trong khi đơn vị của ô
 * đọc (`ReadTile`) dựng ở `labelSp − 2` = 9,5sp và dấu "chưa kiểm" (`WidgetTelemetry.badgeView`) ở 9,5sp — và sàn KHÔNG
 * giữ chúng (`FitProbe.minScale` bỏ qua chữ dựng dưới sàn). Người sửa sau tin KDoc thì tưởng mọi chữ ≥ 10sp.
 *
 * Bài này đọc CỠ THẬT từ mã (độ lệch trong bộ dựng ô + bảng [TileSize]) rồi đòi KDoc nêu đúng con số đó: đổi cỡ chữ vi
 * mô mà không sửa KDoc ⇒ đỏ. Thử ĐỎ: viết lại câu "10 = chữ nhỏ nhất" vào KDoc / bỏ đoạn 9,5sp.
 */
class FitTextFloorDocContractTest {

    private val readTile by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ReadTile.kt") }
    private val factory by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt") }
    private val telemetry by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WidgetTelemetry.kt") }
    private val probe by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/FitProbe.kt") }
    private val barsRaw by lazy { SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiSpaceBars.kt") }

    /** Mọi độ lệch `size.labelSp - <x>f` trong [src] (bộ dựng ô dùng chung bảng [TileSize]). */
    private fun offsets(src: String): List<Float> =
        Regex("""size\.labelSp\s*-\s*([0-9.]+)f""").findAll(src).map { it.groupValues[1].toFloat() }.toList()

    /**
     * KDoc ngay trên `const val FIT_TEXT_MIN` (văn bản gốc — bài này canh CHỮ của KDoc), nối dòng: bỏ lề ` * ` đầu dòng để
     * một câu bị ngắt dòng vẫn khớp (câu sai cũ *"không / bao giờ bị co dưới"* nằm vắt qua hai dòng).
     */
    private val kdoc by lazy {
        val at = barsRaw.indexOf("const val FIT_TEXT_MIN")
        require(at > 0) { "không thấy FIT_TEXT_MIN" }
        barsRaw.substring(barsRaw.lastIndexOf("/**", at), at).lines()
            .joinToString(" ") { it.trim().removePrefix("/**").removePrefix("*/").removePrefix("*").trim() }
    }

    private fun sp(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString().replace('.', ',')

    @Test
    fun `san bang NHAN nho nhat dang ship, khong phai chu nho nhat`() {
        val labelOffset = offsets(readTile).min()            // nhãn ô ĐỌC: labelSp − 1,5
        val minLabel = TileSize.values().minOf { it.labelSp - labelOffset }
        assertEquals(KachiBars.FIT_TEXT_MIN.toFloat(), minLabel, "sàn = nhãn nhỏ nhất trong một ô (DOCK/GROUP)")
        assertTrue(offsets(factory).contains(labelOffset), "nhãn ô SELECT dùng cùng độ lệch ${sp(labelOffset)}")
    }

    @Test
    fun `KDoc neu dung chu vi mo duoi san va vi sao san khong giu chung`() {
        val unitOffset = offsets(readTile).max()             // đơn vị ô đọc: labelSp − 2
        val minUnit = TileSize.values().minOf { it.labelSp - unitOffset }
        assertTrue(minUnit < KachiBars.FIT_TEXT_MIN, "đơn vị ô đọc ${sp(minUnit)}sp đã dưới sàn — đúng ca KDoc phải kể")
        val badge = SourceRoots.body(telemetry, "internal fun badgeView(ctx: Context): View")
        val badgeSp = Regex("""COMPLEX_UNIT_SP,\s*([0-9.]+)f""").find(badge)!!.groupValues[1].toFloat()
        assertTrue(badgeSp < KachiBars.FIT_TEXT_MIN)
        listOf("${sp(minUnit)}sp", "ReadTile", "badgeView", "FitProbe.minScale").forEach {
            assertTrue(kdoc.contains(it), "KDoc FIT_TEXT_MIN phải nêu `$it` (cỡ thật trong mã):\n$kdoc")
        }
        assertTrue(!kdoc.contains("không bao giờ bị co dưới"), "câu cũ sai sự thật (chữ vi mô vẫn co dưới sàn)")
        // Cơ chế mà KDoc kể: chữ dựng DƯỚI sàn không vào phép tính sàn `k`.
        assertTrue(SourceRoots.body(probe, "private fun minScale(fs: FitScale, f: Floors): Double").contains("if (base >= f.textPx)"))
    }
}
