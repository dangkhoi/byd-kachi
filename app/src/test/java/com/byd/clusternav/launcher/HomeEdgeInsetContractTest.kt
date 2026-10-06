package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.math.ceil

/**
 * ═══ 2.93 wave 2A · HOME-EDGE-80 — lề NGANG của khung nội dung màn chính = 80 % của [KachiSpace.L]; lề DỌC giữ [KachiSpace.L] ═══
 *
 * Lời giao owner 20/09 (`docs/_handoff/ux-wp1-zero-borders.md` §4): *"canh lại margin header, taskbar, trái phải, bé lại còn
 * 80%"*. [ĐO git f56c508] bản 1.87 khai `EDGE_H = 13` mà KHÔNG nối — khung gốc vẫn `setPadding(L, L, L, L)` (S1b) ⇒ lời giao chưa
 * từng lên màn; 2.93 MISC xoá hằng chết (EDGE-H-DEAD). Wave 2A nối nó qua MỘT hằng có chỗ đọc thật (spec
 * `docs/specs/kachi-293-wave2a.html` §4.1). Bài này khoá: (1) con số = 80 % làm tròn LÊN, không trùng bậc [KachiSpace.M];
 * (2) khung gốc đọc nó cho TRÁI/PHẢI, [KachiSpace.L] cho TRÊN/DƯỚI (không ai "dọn cho đều" mất nửa lời giao); (3) nó chỉ đọc ở
 * đúng khung ấy — thanh trên, vùng ô, thanh nút là con của cùng khung nên cùng mép (không lề âm, không lệch cột); (4) số px ở
 * mật độ của máy ảo QA / đầu xe (1,5×) là 19 px (cũ 24 px) — con số QA máy ảo đo bằng pixel.
 *
 * Quét mã đã bỏ chú thích ([SourceRoots.codeOf] / [KotlinSource.stripComments]) — nhắc tên trong KDoc không tính là "đọc".
 */
class HomeEdgeInsetContractTest {

    private val home by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }

    @Test
    fun `EDGE_H = 80 phan tram cua L lam tron len, nam giua M va L`() {
        assertEquals(ceil(KachiSpace.L * 0.8).toInt(), KachiSpace.EDGE_H, "80 % của ${KachiSpace.L}dp làm tròn LÊN")
        assertTrue(KachiSpace.EDGE_H in (KachiSpace.M + 1) until KachiSpace.L, "không trùng bậc M (lề-trong-thẻ), nhỏ hơn L")
    }

    @Test
    fun `khung goc man chinh - trai phai EDGE_H, tren duoi L`() {
        val content = SourceRoots.body(home, "val content = LinearLayout(this).apply {")
        assertTrue(content.contains("setPadding(dp(Sp.EDGE_H), dp(Sp.L), dp(Sp.EDGE_H), dp(Sp.L))"), content)
        assertTrue(home.contains("content.addView(topStrip.view"), "thanh trên là con của CÙNG khung")
        assertTrue(home.contains("content.addView(mainArea"), "vùng ô + thanh nút là con của CÙNG khung")
    }

    @Test
    fun `EDGE_H chi duoc doc o khung goc man chinh`() {
        val readers = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.mapNotNull { p ->
            val n = Regex("""\bEDGE_H\b""").findAll(KotlinSource.stripComments(p.toFile().readText())).count()
            if (n > 0) p.fileName.toString() to n else null
        }.toMap()
        assertEquals(mapOf("KachiSpace.kt" to 1, "KachiHomeActivity.kt" to 2), readers, "một dòng khai + hai cạnh của một khung")
    }

    @Test
    fun `px o mat do 1_5x - 19 px moi ben (cu 24 px), cung phep doi dp cua man chinh`() {
        val density = 1.5f   // máy ảo QA clusternav10 (240 dpi) — cùng mật độ đầu xe [SUY memory build-on-mac]
        assertEquals(19, (KachiSpace.EDGE_H * density).toInt())
        assertEquals(24, (KachiSpace.L * density).toInt())
    }
}
