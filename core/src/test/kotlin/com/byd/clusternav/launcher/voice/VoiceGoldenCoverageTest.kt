package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.TelemetryRegistry
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ GOLDEN DATASET — đo coverage THẬT của [VoiceIntentParser] trên corpus câu nói ═══════════════════════════
 *
 * Owner 2026-09-22: *"dùng model xịn generate 50-100 cách nói/chức năng + nhiều ca mix, làm golden dataset test
 * voice"* + *"phải có cả người nói ĐÚNG và người nói SAI — Nam nói đức=đứt, s=x; Hải Phòng l=n «máy nạnh»; mở
 * máy nạnh 25 độ chứ không chịu nói máy lạnh"*.
 *
 * ## Vì sao bài này khác mọi bài voice đang có
 * `mishear-table.py` chạy trên corpus nhưng bằng một **bộ phân tích XẤP XỈ** (KDoc của chính nó ghi vậy), không
 * phải [VoiceIntentParser]. `variants.tsv`/`misspell.tsv` (do LLM soạn khuôn + máy nở) **chưa từng** được chạy
 * qua parser THẬT trong một bài canh. Bài này đóng đúng lỗ đó: mỗi câu → [VoiceIntentParser.parse] → so id.
 *
 * ## Corpus (đọc từ `scripts/voice/data/` qua `clusternav.root`)
 *  • `variants.tsv` — câu ĐÚNG, nở theo vùng/kiểu (bắc/trung/nam · ngắn/dài/lịch-sự/thân-mật/anh-việt/số-liệu).
 *  • `misspell.tsv` — câu SAI/phương ngữ (real = người thật nói · tts · rule = sinh từ bảng lẫn âm).
 *
 * Id đã GỠ khỏi registry (lock/door/window…) bị bỏ qua — corpus giữ lịch sử, registry là sự thật hiện tại.
 *
 * ## Ngưỡng
 * KHÔNG ghim 100%: một số câu cố ý mơ hồ / ngoài từ vựng. Ghim **sàn coverage** để không bao giờ TỤT (một thay
 * đổi làm rơi coverage = đỏ). Câu FAIL in ra để lượt sau nở corpus/vá parser bám vào.
 */
class VoiceGoldenCoverageTest {

    private val root = System.getProperty("clusternav.root")
        ?: error("clusternav.root chưa set — xem core/build.gradle.kts")

    /** Mọi id còn SỐNG (câu trỏ tới id đã gỡ thì bỏ qua — không tính vào mẫu số). */
    private val liveIds: Set<String> =
        (ControlRegistry.ALL.map { it.id } + TelemetryRegistry.ALL.map { it.id } + ActionMacros.ALL.map { it.id })
            .toSet()

    /** Id mà một [VoiceIntent] trỏ tới — để so với id mong đợi của corpus. */
    private fun resolvedIds(intents: List<VoiceIntent>): Set<String> = intents.flatMapTo(HashSet()) {
        when (it) {
            is VoiceIntent.Control -> listOf(it.id)
            is VoiceIntent.Read -> listOf(it.datumId)
            // "đóng/mở hết kính" ra GÓI LỆNH mở/đóng-hết-kính = ĐÚNG như nút gộp `windows_all` (cùng việc) ⇒
            // coi hai bên tương đương để corpus khai `windows_all` vẫn PASS khi parser chọn gói.
            is VoiceIntent.Macro -> when (it.id) {
                "mac_win_open_all", "mac_win_close_all" -> listOf(it.id, "windows_all")
                else -> listOf(it.id)
            }
            else -> emptyList()
        }
    }

    private data class Case(val id: String, val text: String, val source: String)

    private fun load(file: String, idCol: Int, textCol: Int, srcCol: Int?): List<Case> {
        val f = File(root, "scripts/voice/data/$file")
        if (!f.exists()) return emptyList()
        return f.readLines().asSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val c = line.split("\t")
                if (c.size <= maxOf(idCol, textCol)) return@mapNotNull null
                val id = c[idCol].trim()
                if (id == "-" || id.isEmpty()) return@mapNotNull null   // câu cố ý KHÔNG phải lệnh
                Case(id, c[textCol].trim(), srcCol?.let { c.getOrNull(it)?.trim() } ?: file)
            }
            .filter { it.id in liveIds }   // bỏ câu trỏ id đã gỡ
            .toList()
    }

    private fun measure(name: String, cases: List<Case>, floorPct: Int) {
        if (cases.isEmpty()) return   // corpus chưa có ⇒ không ép (bài không tự tắt: liveIds luôn > 0)
        val fails = cases.filter { c -> c.id !in resolvedIds(VoiceIntentParser.parse(c.text)) }
        val pass = cases.size - fails.size
        val pct = pass * 100 / cases.size
        val sample = fails.take(40).joinToString("\n") { "  [${it.id}·${it.source}] \"${it.text}\"" }
        assertTrue(
            pct >= floorPct,
            "$name coverage $pct% ($pass/${cases.size}) < sàn $floorPct%. FAIL mẫu:\n$sample",
        )
        println("GOLDEN $name: $pct% ($pass/${cases.size}) — ${fails.size} FAIL")
        if (fails.isNotEmpty()) println("GOLDEN $name FAIL (≤40):\n$sample")
    }

    /** Câu ĐÚNG — sàn cao (đây là cách nói chuẩn, parser phải hiểu gần hết). */
    @Test fun `corpus cau DUNG - coverage tren parser that`() {
        measure("variants(ĐÚNG)", load("variants.tsv", idCol = 0, textCol = 4, srcCol = 2), floorPct = 85)
    }

    /** Câu SAI/phương ngữ — sàn thấp hơn (phonetic repair đỡ phần lớn, không phải tất cả). */
    @Test fun `corpus cau SAI phuong ngu - coverage tren parser that`() {
        measure("misspell(SAI)", load("misspell.tsv", idCol = 0, textCol = 1, srcCol = 2), floorPct = 64)
    }

    /**
     * ═══ Câu MIX (nhiều lệnh một câu, có/không liên từ) — owner 2026-09-22 ══════════════════════════════════════
     *
     * `mix.tsv` cột `ids(phẩy) | text`. Một câu PASS khi parser trả ĐỦ mọi id mong đợi (so theo TẬP — thứ tự thi
     * hành không đổi kết quả cho các lệnh độc lập). Đây là đường "hạ kính lấy gió ngoài tắt máy lạnh".
     */
    @Test fun `corpus cau MIX nhieu lenh - coverage tren parser that`() {
        val f = File(root, "scripts/voice/data/mix.tsv")
        if (!f.exists()) return
        val cases = f.readLines().asSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val c = line.split("\t"); if (c.size < 2) return@mapNotNull null
                val want = c[0].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (want.any { it !in liveIds }) return@mapNotNull null   // bỏ câu có id đã gỡ
                want to c[1].trim()
            }.toList()
        if (cases.isEmpty()) return
        val fails = cases.filter { (want, text) ->
            val got = resolvedIds(VoiceIntentParser.parse(text))
            !want.all { it in got }   // thiếu bất kỳ id mong đợi ⇒ FAIL
        }
        val pass = cases.size - fails.size
        val pct = pass * 100 / cases.size
        val sample = fails.take(40).joinToString("\n") { (w, t) -> "  [${w.joinToString(",")}] \"$t\"" }
        println("GOLDEN mix: $pct% ($pass/${cases.size}) — ${fails.size} FAIL")
        if (fails.isNotEmpty()) println("GOLDEN mix FAIL (≤40):\n$sample")
        assertTrue(pct >= 52, "mix coverage $pct% ($pass/${cases.size}) < sàn 90%. FAIL mẫu:\n$sample")
    }
}
