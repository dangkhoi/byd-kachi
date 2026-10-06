package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.88 · KEY-SOURCE-SPLIT tầng 2 — bài canh DÂY NỐI (mã nguồn), spec `kachi-288-key-source-split` T6 ══════════════════
 *
 * Luật thuần đã chạy thật ở `:core` (`VoiceKeyMatcherSourceTest` · `KeySourceResolverTest` · `VoiceKeyBindingsTest`) và
 * lưu trữ ở `VoiceKeySourceStoreTest`. Ở đây khoá những mắt xích chỉ thấy trong mã `:app` — mỗi bài nói rõ nó khoá gì:
 *  - W1 luồng phím chỉ đọc nguồn QUA hàm tra của matcher (matcher quyết khi nào gọi ⇒ mã không có dòng theo nguồn không
 *    bao giờ chạm HAL — R3/R-nf1); số đọc đi tiếp vào nhật ký tầng 1 (R-nf2);
 *  - W2 luồng HAL thứ hai `kachi-keysrc-sync` sống/chết cùng bộ ghi, đọc mồi chỉ gửi việc;
 *  - W3 giao diện: học kèm nguồn dùng khuôn tên mới + truyền nguồn vào thêm/xoá theo (mã, nguồn) (R1/R2/R4);
 *  - W4 nhật ký `voice-key … src=` (R6);
 *  - W5 CLAUDE.md §8 — mọi hàm mới có lời gọi thật ngoài định nghĩa.
 */
class KeySourceSplitWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val base = "src/main/java/com/byd/clusternav"
    private val a11y by lazy { code("$base/modules/navaccess/NavAccessibilityService.kt") }
    private val recorder by lazy { code("$base/modules/voicekey/KeySourceRecorder.kt") }
    private val section by lazy { code("$base/launcher/SettingsSectionsKeys.kt") }
    private val bridgeKeys by lazy { code("$base/launcher/ClusterNavBridgeKeys.kt") }
    private val prefs by lazy { code("$base/Prefs.kt") }
    private val onKey by lazy { SourceRoots.body(a11y, "override fun onKeyEvent(event: KeyEvent?): Boolean") }

    @Test
    fun `W1 - luong phim chi doc nguon qua ham tra cua matcher, so doc di tiep vao nhat ky`() {
        val lambda = SourceRoots.body(onKey, "voiceKeyMatcher.onKey(cfg, action, event.keyCode, event.downTime) {")
        assertTrue(lambda.contains("lookupSource(sample).also { preRead = it.reading }"), "hàm tra nguồn = lambda của matcher")
        assertEquals(1, Regex("""lookupSource\(""").findAll(onKey).count(), "onKeyEvent KHÔNG được tự gọi tra nguồn ngoài lambda")
        val helper = SourceRoots.body(a11y, "private fun lookupSource(sample: KeySample?): KeySourceLookup")
        assertTrue(helper.contains("rec.lookupSource(sample)"), "tra qua bộ ghi (luồng sync + trần 100 ms)")
        assertTrue(helper.contains("catch (e: RuntimeException)") && helper.contains("KeySourceLookup.NO_LOOKUP"),
            "lỗi bất ngờ ⇒ không biết nguồn, không ném khỏi onKeyEvent của dịch vụ giữ phím")
        val match = onKey.indexOf("voiceKeyMatcher.onKey(")
        val journal = onKey.indexOf("keySource?.onDown(sample, learned = false, preRead = preRead)")
        assertTrue(journal > match, "nhật ký đường khớp ghi SAU matcher để mang số đọc (R-nf2)")
        assertTrue(SourceRoots.body(recorder, "fun lookupSource(sample: KeySample): KeySourceLookup").contains("resolver.lookup(sample)"))
    }

    @Test
    fun `W2 - luong sync rieng song chet cung bo ghi, doc moi chi gui viec`() {
        assertTrue(recorder.contains("KeySourceResolver(gateway = gateway, exec = { syncExec }, clockMs = SystemClock::uptimeMillis)"),
            "resolver dùng ĐÚNG luồng sync riêng (không xếp hàng sau nhật ký) + đồng hồ uptime (cùng gốc eventTime)")
        assertTrue(SourceRoots.body(recorder, "fun start()").contains("syncExec = Executors.newSingleThreadExecutor"))
        assertTrue(recorder.contains("SYNC_THREAD_NAME = \"kachi-keysrc-sync\""))
        val stop = SourceRoots.body(recorder, "fun stop()")
        assertTrue(stop.contains("syncExec?.shutdownNow()") && stop.contains("syncExec = null"), "stop() phải dừng cả luồng sync")
        assertTrue(SourceRoots.body(recorder, "fun primeSource(): Boolean").contains("resolver.prime()"))
        val connected = SourceRoots.body(a11y, "override fun onServiceConnected()")
        val prime = connected.indexOf("if (VoiceKeyBindings.anySource(Prefs.voiceKeyBindings(app))) keySource?.primeSource()")
        assertTrue(prime > connected.indexOf(".also { it.start() }"), "đọc mồi SAU khi bộ ghi đã dựng luồng, chỉ khi có dòng theo nguồn")
    }

    @Test
    fun `W3 - giao dien hoc kem nguon va them xoa theo ma va nguon`() {
        val learn = SourceRoots.body(section, "private fun learn()")
        assertTrue(learn.contains("val source = learnedSource(code)"))
        assertTrue(learn.contains("R.string.kachi_key_custom_name_src, name, code, KeySourceDetailText.kindLabel(context, source)"),
            "R1: tên nút có hậu tố nguồn từ tài nguyên (đủ 5 tiếng)")
        assertTrue(learn.contains("bridge.addCustomButton(label, code, source)"))
        assertTrue(SourceRoots.body(section, "private fun learnedSource(code: Int): KeySourceKind?")
            .contains("KeySourceProbes.verdict(bridge.learnedKeySource(code)?.reading) as? KeySourceVerdict.Source"),
            "chỉ nguồn RÕ mới được lưu — chưa xong/hụt/lạ ⇒ lưu như 2.87")
        assertTrue(section.contains("bridge.addBinding(option.code, spec, option.source)"), "R2: gán từ nút có nguồn ⇒ dòng theo nguồn")
        assertTrue(section.contains("bridge.removeBinding(binding.keyCode, binding.source)"), "R4: xoá đúng (mã, nguồn)")
        assertTrue(section.contains("bridge.removeCustomButton(button.keyCode, button.source)"), "R4: xoá nút đúng (mã, nguồn)")
        assertTrue(section.contains("title = buttonLabel(binding.keyCode, binding.source, buttons)"), "R4: nhãn tra theo (mã, nguồn)")
        assertTrue(bridgeKeys.contains("ButtonOption(it.keyCode, it.name, it.source)"), "hộp Thêm gán phải thấy nguồn của nút")
        assertTrue(bridgeKeys.contains("Prefs.removeVoiceKeyBinding(app, keyCode, source)"))
        assertTrue(bridgeKeys.contains("Prefs.removeVoiceKeyCustomButton(app, code, source)"))
        assertTrue(prefs.contains("putString(K_VK_CUSTOM, VoiceKeyCustomButtonStore.encode(items))"), "một khoá, một chỗ ghi")
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { folder ->
            val xml = SourceRoots.text("src/main/res/$folder/strings_kachi.xml")
            val line = Regex("""<string name="kachi_key_custom_name_src">([^<]*)</string>""").find(xml)?.groupValues?.get(1)
            assertTrue(line != null && line.contains("%1\$s") && line.contains("%2\$d") && line.contains("%3\$s"),
                "$folder: thiếu/sai khuôn kachi_key_custom_name_src (%1\$s tên · %2\$d mã · %3\$s nguồn): $line")
        }
    }

    @Test
    fun `W4 - nhat ky voice-key ghi nguon va ly do`() {
        assertTrue(onKey.contains("Log.i(TAG, \"voice-key fire → target=\$spec key=\${event.keyCode} src=\${decision.reason ?: NO_SOURCE_READ}\")"))
        assertTrue(onKey.contains("Log.i(TAG, \"voice-key pass key=\${event.keyCode} src=\${decision.reason}\")"))
        assertTrue(onKey.contains("!decision.consume && decision.reason != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0"),
            "dòng pass chỉ cho mã có dòng theo nguồn (reason ≠ null), một dòng mỗi lần nhấn")
    }

    @Test
    fun `W5 - moi ham moi co loi goi that ngoai dinh nghia`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.name to stripComments(it.readText()) }.toList()
        }
        fun callers(token: String, defFile: String) = all.filter { (name, src) -> name != defFile && src.contains(token) }.map { it.first }.distinct().sorted()
        assertEquals(listOf("NavAccessibilityService.kt"), callers("rec.lookupSource(", "KeySourceRecorder.kt"))
        assertEquals(listOf("NavAccessibilityService.kt"), callers(".primeSource()", "KeySourceRecorder.kt"))
        assertEquals(listOf("KeySourceRecorder.kt"), callers("KeySourceResolver(", "KeySourceResolver.kt"))
        assertEquals(listOf("KeySourceResolver.kt"), callers("meter.read(", "KeySourceMeter.kt"))
        assertEquals(listOf("KeySourceResolver.kt"), callers("meter.prime()", "KeySourceMeter.kt"))
        assertTrue("VoiceKeyMatcher.kt" in callers("VoiceKeyBindings.needsSource(", "VoiceKeyBindings.kt"))
        assertEquals(listOf("NavAccessibilityService.kt"), callers("VoiceKeyBindings.anySource(", "VoiceKeyBindings.kt"))
        assertTrue(all.first { it.first == "KeySourceResolver.kt" }.second.contains("KeySourceLookup.of(meter.read(sample))"),
            "kết luận nguồn đi qua CÙNG KeySourceProbes.verdict mà hộp Học phím hiện")
        assertEquals(listOf("Prefs.kt"), callers("VoiceKeyCustomButtons.put(", "VoiceKeyCustomButtons.kt"))
        assertEquals(listOf("Prefs.kt"), callers("VoiceKeyCustomButtons.remove(", "VoiceKeyCustomButtons.kt"))
        assertEquals(listOf("Prefs.kt"), callers("VoiceKeyCustomButtonStore.read(", "VoiceKeyCustomButtonStore.kt"))
        assertEquals(listOf("Prefs.kt"), callers("VoiceKeyCustomButtonStore.encode(", "VoiceKeyCustomButtonStore.kt"))
        assertTrue("SettingsSectionsKeys.kt" in callers("KeySourceDetailText.kindLabel(", "SettingsKeySourceDetail.kt"))
        assertTrue("SettingsSectionsKeys.kt" in callers("KeySourceKind", "KeySourceProbe.kt"))
    }

    /** Bỏ chú thích (ĐÚNG phép của `SourceRoots.codeOf` — [KotlinSource.stripComments]) — một KDoc nhắc tên hàm KHÔNG phải lời gọi thật. */
    private fun stripComments(t: String): String = KotlinSource.stripComments(t)
}
