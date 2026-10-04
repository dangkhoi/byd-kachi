package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.extension
import kotlin.io.path.readText

/**
 * ═══ 2.88 · KHÔNG CÒN MỘT SỐ NGƯỠNG LỐP NÀO Ở MAIN — gộp hai bài canh cũ ═══════════════════════════════════════════
 *
 * Khoá lệnh owner 2026-10-04 (nguyên văn): *"Cảnh báo nó theo tùy loại xe đó nha, không hardcode số đâu"*. Spec
 * `docs/specs/kachi-288-tyre-car-state.html` R1. Thay cho hai bài cũ chỉ cấm TỪNG tên/TỪNG chuỗi
 * (`Goi2FeatureWiringContractTest` cấm "2.0"/"3.2" trong ô vẽ · `GroupTileWiringContractTest` cấm `TyreBoard.LOW_BAR`…
 * trong tầng vẽ nhóm): bài này quét MỌI tệp main của cả ba module, nên ngưỡng mọc lại ở chỗ thứ ba cũng đỏ.
 *
 * Mọi phép quét chạy trên mã đã BỎ CHÚ THÍCH (lịch sử "2.0 / 3.2 bar" được phép nằm trong KDoc để giải thích).
 */
class TyreNoThresholdContractTest {

    private fun mainFiles(): List<Path> = SourceRoots.moduleSourceRoots().flatMap { root ->
        Files.walk(root).use { s -> s.filter { it.extension == "kt" }.toList() }
    }

    private fun stripComments(src: String): String = src
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }

    /** Tên của bộ ngưỡng cũ và mọi biến thể đặt tên theo đơn vị áp suất (`*_BAR` · `*_KPA` · `*_PSI` · `KPA_PER_*`). */
    private val thresholdName = Regex("""\b(?:LOW_BAR|HIGH_BAR|SPREAD_BAR|KPA_PER_BAR|\w+_(?:BAR|KPA|PSI)|KPA_PER_\w+)\b""")

    /** Trạng thái theo ngưỡng của bản cũ (`TyreStatus.LOW/HIGH/UNEVEN`) và hàm phán theo số `statusOf(bar, spread, …)`. */
    private val oldStatus = Regex("""\bTyreStatus\.(?:LOW|HIGH|UNEVEN)\b|\bUNEVEN\b|\bstatusOf\s*\(\s*bar""")

    /** So một con số áp suất (tên chứa `Kpa`/`Bar`) với một hằng số — đúng hình `t[i] < 2.2` của widget lốp cũ. */
    private val numericCompare = Regex("""\b\w*(?:[Kk]pa|[Bb]ar)\w*\s*(?:!!)?\s*[<>]=?\s*-?\d""")

    @Test
    fun `khong tep main nao con hang nguong lop hay trang thai theo nguong`() {
        val hits = mainFiles().flatMap { p ->
            val code = stripComments(p.readText())
            (thresholdName.findAll(code) + oldStatus.findAll(code) + numericCompare.findAll(code))
                .map { "${p.fileName}: «${it.value}»" }.toList()
        }
        assertEquals(emptyList<String>(), hits, "ngưỡng lốp viết cứng đã quay lại — màu lốp phải là lời phán của XE")
    }

    @Test
    fun `bo phan quyet dinh lop khong chua mot so thap phan nao`() {
        // TyreBoard + TyreJudge chỉ được nói bằng MÃ OEM (0/1/2/3…) — một số thập phân ở đây gần như chắc chắn là ngưỡng bar.
        listOf(
            "src/main/java/com/byd/clusternav/launcher/TyreBoard.kt",
            "src/main/java/com/byd/clusternav/launcher/TyreJudge.kt",
        ).forEach { f ->
            // Bỏ cả chuỗi ký tự (lý do ẩn `TyreIds.HIDDEN_WHY` có chữ "2.88" — số phiên bản, không phải ngưỡng).
            val code = SourceRoots.codeOf(f).replace(Regex(""""(?:\\.|[^"\\])*""""), "\"\"")
            val decimals = Regex("""(?<![\w.])\d+\.\d+""").findAll(code).map { it.value }.toList()
            assertEquals(emptyList<String>(), decimals, "$f chứa số thập phân: $decimals")
        }
        // Đường dẫn quét phải trỏ đúng: bộ phán có thật và có luật M (chống bài canh xanh vì quét nhầm tệp rỗng).
        assertTrue(SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/TyreJudge.kt").contains("fun judge("))
    }

    /** Thử-phá: mỗi mẫu quét phải BẮT được đúng hình lỗi nó canh (bài canh chưa từng đỏ là bài canh mù — P5). */
    @Test
    fun `cac mau quet bat duoc loi that`() {
        assertTrue(thresholdName.containsMatchIn("const val LOW_BAR = 2.0"))
        assertTrue(thresholdName.containsMatchIn("private const val KPA_PER_BAR = 100.0"))
        assertTrue(thresholdName.containsMatchIn("const val UNDER_KPA = 200"))
        assertTrue(oldStatus.containsMatchIn("TyreStatus.UNEVEN -> colAmber"))
        assertTrue(oldStatus.containsMatchIn("fun statusOf(bar: Double?, spread: Double"))
        assertTrue(numericCompare.containsMatchIn("if (pFlKpa < 200) red"))
        assertTrue(numericCompare.containsMatchIn("bar < 2.2"))
        assertFalse(numericCompare.containsMatchIn("it.isFinite() && it >= 0"), "lưới hữu hạn/không âm không phải ngưỡng")
        assertFalse(thresholdName.containsMatchIn("const val PRESSURE_NONE = 4094"), "mã OEM (L3) không phải ngưỡng")
    }

    @Test
    fun `chip lop to mau bang bang chung - vang hoac do la hai vai cua bang mau`() {
        val ink = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/TopStripChipInk.kt")
        val map = SourceRoots.body(ink, "internal fun chipInk(tone: ChipTone): String = when (tone) {")
        assertTrue(map.contains("ChipTone.WARN -> KachiTheme.AMBER"), "vàng = vai AMBER (theo ngày/đêm)")
        assertTrue(map.contains("ChipTone.ALERT -> KachiTheme.RED"), "đỏ = vai RED")
        assertTrue(map.contains("ChipTone.ENERGY -> KachiTheme.GREEN"), "bình thường = CÙNG mực chip pin (owner: \"như màu range lái\")")
        // Khoá chữ gồm MÀU đã phân giải của từng đoạn ⇒ đổi màu mà chữ đứng yên vẫn vẽ lại (R6).
        val key = SourceRoots.body(ink, "internal fun chipTextKey(")
        assertTrue(key.contains("chipInk(it.tone)"), "khoá so phải gồm màu của từng đoạn")
        assertTrue(SourceRoots.body(ink, "internal fun chipText(").contains("ForegroundColorSpan(c(chipInk(r.tone)))"))
    }

    @Test
    fun `cau kiem thu getid bao sentinel bang bo parse chung`() {
        // [ĐO mã] `raw?.toLongOrNull()` trên chuỗi "int=…" luôn null ⇒ cờ `sentinel` của `getid` từng LUÔN false.
        val hal = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeHal.kt")
        val getId = SourceRoots.body(hal, "private fun runGetId(")
        assertTrue(getId.contains("HalBindingTable.rawIsSentinel(it)"), "phải dùng bộ parse chung của đường đọc")
        assertFalse(getId.contains("toLongOrNull()"), "không parse chuỗi EventValue bằng toLongOrNull")
        // Soát 2.88 regress-5: lời đáp rẽ theo MÃ — BUSY/TIMEOUT tạm thời không thành "trim không có" (CLAUDE.md §2).
        assertTrue(getId.contains("HalBindingTable.sentinelMeaning("), "lời đáp sentinel phải nói đúng nghĩa từng mã")
        assertFalse(getId.contains("feature khong co tren trim nay\""), "không gắn cứng một quy kết cho mọi sentinel")
        assertTrue(HalBindingTable.rawIsSentinel("int=-2147482648 float=0.0"))
    }

    @Test
    fun `dong log TYRE raw co cho goi that va chi ghi khi doi`() {
        val wiring = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt")
        assertTrue(wiring.contains("TyreRawLog.note(it.tyres)"), "kênh 2 phải được gọi từ luồng thu trạng thái xe")
        val log = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/TyreRawLog.kt")
        assertTrue(log.contains("LogLineThrottle(windowMs = HEARTBEAT_MS, maxKeys = 1)"), "chỉ-khi-đổi qua bộ tiết chế dùng chung")
        assertTrue(log.contains("TyreBoard.rawLine(tyres)"), "dòng dựng ở :core (thuần, có test)")
    }
}
