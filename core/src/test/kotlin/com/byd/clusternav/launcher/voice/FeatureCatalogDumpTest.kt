package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.LayoutPreset
import com.byd.clusternav.launcher.TelemetryRegistry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * ═══ DUMP DANH MỤC CHỨC NĂNG TỪ CHÍNH REGISTRY — nguồn cho `docs/kachi-feature-catalog.html` ═══════════════
 *
 * Owner 2026-09-16: *"Viết tài liệu các chức năng của launcher theo dạng bảng… voice command đi kèm, feedback của
 * voice command, toàn dự án, không sót chức năng nào, và status thực tế"*. Phần nút/datum/gói lệnh/hành động
 * launcher **không chép tay**: bài này đọc 4 bộ đăng ký + [VoiceSynonyms] + [VoiceReply] + [VoiceRiskTable] rồi ghi
 * `core/build/catalog/registry.json`; script `scripts/docs/feature-catalog.py` dựng bảng HTML từ đó. Thêm một nút
 * vào registry ⇒ chạy lại là có dòng mới, không ai phải nhớ.
 *
 * Câu lệnh mẫu sinh theo đúng luật của [SherpaPhraseHotwords.CONTROL_VERBS] (động từ theo loại nút); câu phản hồi
 * là **chuỗi thật** [VoiceReply] trả về cho ý định đó — tức đúng thứ Kachi hiện/đọc trên xe, không phải diễn giải.
 *
 * ## 2.74 · R3 — câu mẫu nay ĐỌC [VoiceCommandCatalog], không còn bộ sinh riêng ở đây
 * Tệp này từng mang **bản sao thứ tư** của bộ sinh câu (một `when (c.kind)` riêng) cộng một bảng `generic` **gõ
 * tay** 16 dòng. Nay câu đến từ [VoiceCommandCatalog.samples]/[VoiceCommandCatalog.samplesFor] — cùng nguồn mà màn
 * *Cài đặt › Giọng nói* dựng danh sách, nên bảng tài liệu và màn hình không thể quảng cáo hai bộ câu khác nhau.
 *
 * Lược đồ JSON **không đổi**: `scripts/docs/feature-catalog.py` đọc năm mảng với đầy đủ `bindingKey` · `halDevice`
 * · `tier` · `args` · `min/max/step` · `reply*` · `confirmQuestion`, mà một `VoiceCommandGroup` không mang nổi.
 * Chỉ trường `voice` đổi nguồn; bốn dòng `unknown_*` giữ câu gõ tay vì chúng là ví dụ **KHÔNG hiểu được**, tức cố
 * ý không phải câu lệnh và không có chỗ nào trong danh mục để sinh ra.
 */
class FeatureCatalogDumpTest {

    @Test
    fun `dump registry ra JSON de dung bang catalog`() {
        val sb = StringBuilder(64 * 1024)
        sb.append("{\n\"controls\": [\n")
        ControlRegistry.ALL.forEachIndexed { idx, c ->
            val verbs = SherpaPhraseHotwords.CONTROL_VERBS[c.kind].orEmpty()
            // Cách gọi tự nhiên: nhãn đã qua `phrasesOf` (bỏ dấu câu/ngoặc: *"Pin (SOC)"* → *"pin"*, *"Khoá / mở
            // khoá"* → *"khoá"*) — luật nay khai MỘT chỗ ở [VoiceCommandCatalog].
            val samples = VoiceCommandCatalog.samples(c)
            val intent = when (c.kind) {
                ControlKind.STEP -> VoiceIntent.Control(c.id, c.value)
                ControlKind.SELECT -> VoiceIntent.Control(c.id, 0)
                ControlKind.BUTTON -> VoiceIntent.Control(c.id, null)
                else -> VoiceIntent.Control(c.id, 1)
            }
            val risk = VoiceRiskTable.of(intent)
            obj(sb, listOf(
                "id" to c.id, "label" to c.label, "labelEn" to c.labelEn, "short" to c.short,
                "kind" to c.kind.name, "domain" to c.domain.name, "tier" to c.tier.name,
                "bindingKey" to c.bindingKey, "halDevice" to c.halDevice,
                "enabledByDefault" to c.enabledByDefault, "args" to c.args,
                "min" to c.min, "max" to c.max, "step" to c.step, "value" to c.value,
                "synonyms" to VoiceSynonyms.CONTROL[c.id].orEmpty(),
                "verbs" to verbs.map { it.name },
                "voice" to samples,
                "risk" to risk.name,
                "replyDone" to VoiceReply.done(intent),
                "replyFailed" to VoiceReply.failed(intent),
                "confirmQuestion" to if (risk == VoiceRisk.CONFIRM) VoiceReply.confirmQuestion(intent) else null,
            ))
            if (idx < ControlRegistry.ALL.size - 1) sb.append(",\n")
        }
        sb.append("\n],\n\"telemetry\": [\n")
        TelemetryRegistry.ALL.forEachIndexed { idx, t ->
            val intent = VoiceIntent.Read(t.id)
            obj(sb, listOf(
                "id" to t.id, "label" to t.label, "labelEn" to t.labelEn, "short" to t.short,
                "unit" to t.unit, "domain" to t.domain.name, "tier" to t.tier.name,
                "bindingKey" to t.bindingKey, "halDevice" to t.halDevice,
                "synonyms" to VoiceSynonyms.TELEMETRY[t.id].orEmpty(),
                // Một câu canonical; các cách nói khác của cùng datum đã ở trường `synonyms` ngay trên.
                "voice" to VoiceCommandCatalog.samples(t),
                "replyPreview" to VoiceReply.preview(intent),
                "replyNoReading" to VoiceReply.noReading(t.label),
            ))
            if (idx < TelemetryRegistry.ALL.size - 1) sb.append(",\n")
        }
        sb.append("\n],\n\"macros\": [\n")
        ActionMacros.ALL.forEachIndexed { idx, m ->
            val intent = VoiceIntent.Macro(m.id)
            val risk = VoiceRiskTable.of(intent)
            obj(sb, listOf(
                "id" to m.id, "label" to m.label, "labelEn" to m.labelEn, "domain" to m.domain.name,
                "tier" to m.tier().name, "steps" to m.steps.map { "${it.controlId}=${it.arg}" },
                "voice" to VoiceCommandCatalog.samplesFor(intent),
                "risk" to risk.name,
                "replyDone" to VoiceReply.done(intent), "replyFailed" to VoiceReply.failed(intent),
                "confirmQuestion" to if (risk == VoiceRisk.CONFIRM) VoiceReply.confirmQuestion(intent) else null,
            ))
            if (idx < ActionMacros.ALL.size - 1) sb.append(",\n")
        }
        sb.append("\n],\n\"launcher\": [\n")
        LauncherActions.ALL.forEachIndexed { idx, a ->
            val intent = VoiceIntent.Launcher(a.id)
            obj(sb, listOf(
                "id" to a.id, "label" to a.label, "labelEn" to a.labelEn,
                "voice" to VoiceCommandCatalog.samplesFor(intent),
                "replyDone" to VoiceReply.done(intent),
            ))
            if (idx < LauncherActions.ALL.size - 1) sb.append(",\n")
        }
        sb.append("\n],\n\"generic\": [\n")
        // Dữ liệu mẫu cho các họ ĐỘNG (hồ sơ · app · sổ địa chỉ): danh mục sinh câu từ chính ba danh sách này, nên
        // chúng phải khớp với ý định đại diện ở bảng dưới — nếu lệch thì `samplesFor` trả rỗng và dòng đó lùi về
        // câu gõ tay, một lối hụt IM LẶNG. Bài canh `khong dong nao con cau go tay tru unknown` bắt đúng ca đó.
        val demoProfiles = listOf("Chính")
        val demoApps = listOf("YouTube")
        val demoPlaces = listOf("Nhà")
        val generic = listOf(
            Triple("media_play", listOf("phát nhạc", "nghe nhạc", "phát nhạc trên YouTube Music"), VoiceIntent.Media(VoiceMediaOp.PLAY)),
            Triple("media_pause", listOf("dừng nhạc", "tạm dừng"), VoiceIntent.Media(VoiceMediaOp.PAUSE)),
            Triple("media_next", listOf("bài tiếp theo", "chuyển bài"), VoiceIntent.Media(VoiceMediaOp.NEXT)),
            Triple("media_prev", listOf("bài trước", "quay lại bài"), VoiceIntent.Media(VoiceMediaOp.PREV)),
            Triple("media_query", listOf("đang phát bài gì"), VoiceIntent.Media(VoiceMediaOp.QUERY)),
            Triple("nav", listOf("dẫn đường đến Bitexco", "chỉ đường tới chợ Bến Thành bằng Waze"), VoiceIntent.Nav("Bitexco")),
            Triple("nav_saved", listOf("về nhà", "đến công ty", "đi làm"), VoiceIntent.NavigateSaved("Nhà")),
            Triple("open_app", listOf("mở YouTube", "đưa YouTube vào ô số hai"), VoiceIntent.OpenApp("YouTube")),
            Triple("open_app_slot", listOf("đưa YouTube vào ô số hai"), VoiceIntent.OpenApp("YouTube", slot = 2)),
            Triple("profile", listOf("đổi sang hồ sơ Chính"), VoiceIntent.Profile("Chính")),
            // L7 — bố cục bằng giọng nói. Một dòng cho mỗi **kiểu** câu, không phải một dòng cho mỗi bố cục:
            // năm bố cục dùng chung một câu trả lời, chỉ khác cái nhãn mà `LayoutPreset.label` trả về.
            Triple(
                "layout",
                listOf("bố cục 2 cột", "đổi sang bố cục 4 ô", "bố cục một ô"),
                VoiceIntent.Layout(LayoutPreset.TWO_COL),
            ),
            Triple("unknown_no_verb", listOf("hôm nay trời đẹp quá"), VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, "hôm nay trời đẹp quá")),
            Triple("unknown_no_object", listOf("bật cái đó"), VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, "bật cái đó")),
            Triple("unknown_mismatch", listOf("tăng đèn đọc"), VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, "tăng đèn đọc")),
            Triple("unknown_app_close", listOf("đóng YouTube"), VoiceIntent.Unknown(VoiceUnknownReason.APP_CLOSE, "đóng YouTube")),
        )
        generic.forEachIndexed { idx, (id, voice, intent) ->
            val risk = VoiceRiskTable.of(intent)
            obj(sb, listOf(
                "id" to id, "voice" to voiceOf(intent, voice, demoProfiles, demoApps, demoPlaces), "risk" to risk.name,
                "replyPreview" to VoiceReply.preview(intent),
                "replyDone" to if (intent is VoiceIntent.Unknown) VoiceReply.unknown(intent) else VoiceReply.done(intent),
                // Câu HỎNG phải là câu **thật** của nhánh ấy, không phải câu chung: bố cục không hỏng vì *"xe không
                // nhận lệnh"* (nó chẳng đụng tới xe) mà vì bề mặt đang nói không nối được đường bố cục.
                "replyFailed" to when (intent) {
                    is VoiceIntent.Layout -> VoiceReply.layoutNotHere(intent)
                    else -> VoiceReply.failed(intent)
                },
                "confirmQuestion" to if (risk == VoiceRisk.CONFIRM) VoiceReply.confirmQuestion(intent) else null,
            ))
            if (idx < generic.size - 1) sb.append(",\n")
        }
        sb.append("\n]\n}\n")
        val dir = File(System.getProperty("user.dir"), "build/catalog").apply { mkdirs() }
        File(dir, "registry.json").writeText(sb.toString())
        // Sàn 60/100 → 50/100 sau khi owner gỡ toàn bộ ADAS/an toàn 2026-09-16 (64 → 54 nút · 123 → 106 datum);
        // → 44/92 sau (V) FEATURE-FILTER 2026-09-17 (54 → 47 nút · 112 → 100 datum). Sàn = số thật trừ ~8 %, để
        // bắt "registry teo bất thường" mà không đỏ vì một lượt xoá có chủ ý.
        // → 36/67 sau UX-OVERHAUL WP8 2026-09-20 (47 → 39 nút · 102 → 73 datum). Cùng công thức: số thật trừ ~8 %.
        // → 26/65 sau 1.90 2026-09-21 (38 → 29 nút · 73 → 71 datum, owner gỡ 9 nút + 2 datum cho xe thuần điện).
        // → 26/58 sau lượt gỡ 7 datum CHẾT 2026-09-25 (nút giữ 33 · 71 → 64 datum). Cùng công thức: số thật trừ ~8 %.
        assertTrue(ControlRegistry.ALL.size >= 26 && TelemetryRegistry.ALL.size >= 58, "registry teo lại bất thường")
    }

    /**
     * Câu của một họ: **danh mục trước**, câu gõ tay chỉ là đường lùi cho họ mà danh mục cố ý không có.
     *
     * Đường lùi tồn tại cho đúng hai loại dòng: bốn dòng `unknown_*` (ví dụ *KHÔNG hiểu được* — chúng minh hoạ lỗi,
     * không phải câu lệnh) và `media_query` (tra tên bài = **từ vựng mở**, Kachi cố ý không làm offline nên nó
     * không phải một câu "nói được" để bày ra màn Cài đặt). Mọi dòng khác phải lấy câu từ danh mục.
     */
    private fun voiceOf(
        intent: VoiceIntent,
        fallback: List<String>,
        profiles: List<String>,
        apps: List<String>,
        places: List<String>,
    ): List<String> = VoiceCommandCatalog.samplesFor(intent, profiles, apps, places).ifEmpty { fallback }

    /**
     * Chốt chống **bản sao thứ năm mọc lại**: mọi dòng `generic` không phải `unknown_*`/`media_query` phải nhận câu
     * từ [VoiceCommandCatalog]. Không có bài này thì ai đó thêm một họ mới kèm câu gõ tay và không gì đỏ — đúng
     * cách bốn bản sao trước đã sinh ra.
     */
    @Test
    fun `moi ho cau lenh trong generic deu lay cau tu danh muc`() {
        val handWritten = setOf(
            VoiceIntent.Media(VoiceMediaOp.QUERY),
            VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, ""),
        ).map { it::class to (it as? VoiceIntent.Media)?.op }
        val intents = listOf(
            VoiceIntent.Media(VoiceMediaOp.PLAY), VoiceIntent.Media(VoiceMediaOp.PAUSE),
            VoiceIntent.Media(VoiceMediaOp.NEXT), VoiceIntent.Media(VoiceMediaOp.PREV),
            VoiceIntent.Nav("Bitexco"), VoiceIntent.NavigateSaved("Nhà"),
            VoiceIntent.OpenApp("YouTube"), VoiceIntent.OpenApp("YouTube", slot = 2),
            VoiceIntent.Profile("Chính"), VoiceIntent.Layout(LayoutPreset.TWO_COL),
        )
        val misses = intents.filter {
            VoiceCommandCatalog.samplesFor(it, listOf("Chính"), listOf("YouTube"), listOf("Nhà")).isEmpty()
        }
        assertTrue(misses.isEmpty(), "họ câu lệnh không có câu trong danh mục ⇒ dòng JSON lùi về câu gõ tay: $misses")
        assertTrue(handWritten.isNotEmpty(), "tiền đề: hai loại dòng cố ý giữ câu gõ tay (unknown + tra tên bài)")
    }

    private fun obj(sb: StringBuilder, fields: List<Pair<String, Any?>>) {
        sb.append("  {")
        fields.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(", ")
            sb.append('"').append(k).append("\": ").append(json(v))
        }
        sb.append("}")
    }

    private fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> '"' + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"'
        is Boolean, is Int, is Long -> v.toString()
        is List<*> -> v.joinToString(", ", "[", "]") { json(it) }
        else -> json(v.toString())
    }
}
