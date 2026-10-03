package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ L7 · KEY-SOURCE-SPLIT tầng 1 — bài canh DÂY NỐI (mã nguồn) ═════════════════════════════════════════════════════
 *
 * Phần thuần (bảng đầu dò, phép đọc, nhãn, vòng đệm, dòng log) khoá ở `:core` (`KeySourceProbeTest` ·
 * `KeySourceJournalTest`). Ở đây khoá những thứ chỉ thấy được trong mã `:app`:
 *  - L1 `onKeyEvent` chép chữ ký + đẩy đo đi, KHÔNG chạm I/O/HAL/InputDevice (main looper, hạn 500 ms —
 *    `AccessibilityService.java:1622,1873-1890` · `KeyEventDispatcher.java:51` r47);
 *  - L2 lượt đọc HAL chạy trên luồng riêng, có TRẦN thời gian, không xếp chồng khi HAL treo;
 *  - L3 bộ đo sống theo một lần bind và đọc qua gateway DUY NHẤT của tiến trình;
 *  - L4 hộp "Học phím mới" hiện dòng chi tiết; L5 gán/khớp/JSON KHÔNG đổi (tầng 2 mới làm);
 *  - L6 §8 — mọi hàm mới có lời gọi thật ngoài định nghĩa.
 */
class KeySourceL7WiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val base = "src/main/java/com/byd/clusternav"
    private val a11y by lazy { code("$base/modules/navaccess/NavAccessibilityService.kt") }
    private val recorder by lazy { code("$base/modules/voicekey/KeySourceRecorder.kt") }
    private val section by lazy { code("$base/launcher/SettingsSectionsKeys.kt") }
    private val dialogs by lazy { code("$base/launcher/SettingsDialogs.kt") }
    private val detail by lazy { code("$base/launcher/SettingsKeySourceDetail.kt") }
    private val bridgeKeys by lazy { code("$base/launcher/ClusterNavBridgeKeys.kt") }
    private val gateway by lazy { code("$base/launcher/BydHalGateway.kt") }

    private val onKey by lazy { SourceRoots.body(a11y, "override fun onKeyEvent(event: KeyEvent?): Boolean") }

    @Test
    fun `L1 - onKeyEvent chep chu ky tren DOWN roi day do di, truoc khi bus hoc phim bao ma`() {
        val down = SourceRoots.body(onKey, "if (event.action == KeyEvent.ACTION_DOWN) {")
        assertTrue(down.contains("keySource?.onDown(KeySourceRecorder.sampleOf(event), learned = Prefs.voiceKeyLearn(app))"),
            "mỗi DOWN phải được đo, kèm cờ đang-học để hộp đặt tên tìm đúng dòng")
        assertTrue(onKey.indexOf("keySource?.onDown(") in 0 until onKey.indexOf("VoiceKeyLearnBus.publish(event.keyCode)"),
            "ghi nhật ký TRƯỚC khi bus báo mã ⇒ hộp đặt tên luôn thấy dòng của lần học")
    }

    @Test
    fun `L1 - luong nhan phim KHONG I-O, KHONG HAL, KHONG tra InputDevice`() {
        val sampleOf = SourceRoots.body(recorder, "fun sampleOf(e: KeyEvent): KeySample")
        val onDown = SourceRoots.body(recorder, "fun onDown(sample: KeySample, learned: Boolean)")
        val forbidden = listOf(
            "InputDevice", "InputManager", "getDevice(", "featureRead", "featureGet", "BydHal", "gateway(",
            "KeySourceProbes.read", ".get(", "Thread.sleep", "Log.", "File(", "Prefs.", "AppContainer",
        )
        forbidden.forEach { tok ->
            assertFalse(onKey.contains(tok) && tok !in setOf("Log.", "Prefs.", ".get("), "onKeyEvent: $tok")
            assertFalse(sampleOf.contains(tok), "sampleOf (luồng phím): $tok")
            assertFalse(onDown.contains(tok), "onDown (luồng phím): $tok")
        }
        assertFalse(onKey.contains("InputDevice") || onKey.contains("featureRead") || onKey.contains("BydHal"))
        assertTrue(onDown.contains("journal.begin(sample, learned)"))
        assertTrue(onDown.contains("h.post { settle(seq, sample) }"), "phần đo phải sang luồng `kachi-keysrc`")
        // Event bị recycle sau onKeyEvent ⇒ chỉ được chép field, không giữ tham chiếu event.
        assertFalse(recorder.contains("KeyEvent.obtain") || Regex("""val\s+\w+\s*:\s*KeyEvent""").containsMatchIn(recorder))
    }

    @Test
    fun `L2 - doc HAL tren luong rieng, co tran, khong xep chong khi treo`() {
        val read = SourceRoots.body(recorder, "private fun readBounded(spec: KeySourceProbeSpec, sample: KeySample): KeySourceReading")
        assertTrue(read.contains("if (inFlight?.isDone == false) return KeySourceReading.failed(spec, KeySourceFailure.BUSY"),
            "HAL treo ⇒ các lần bấm sau ghi `busy` ngay, không chồng lượt đọc")
        assertTrue(read.contains("exec.submit(Callable { KeySourceProbes.read(spec, gateway(), SystemClock::uptimeMillis, sample.eventTime) })"))
        assertTrue(read.contains("f.get(spec.budgetMs, TimeUnit.MILLISECONDS)"), "chờ có TRẦN")
        assertTrue(read.contains("KeySourceFailure.TIMEOUT"))
        val settle = SourceRoots.body(recorder, "private fun settle(seq: Long, sample: KeySample)")
        assertTrue(settle.contains("readBounded(spec, sample)"))
        assertTrue(settle.contains("Log.i(KeySourceLog.TAG, KeySourceLog.line(entry))"), "một dòng KachiKey mỗi DOWN ⇒ usage-*.log")
        assertTrue(settle.contains("catch (e: RuntimeException)"), "lỗi đo không được làm chết tiến trình giữ dịch vụ phím")
        val start = SourceRoots.body(recorder, "fun start()")
        assertTrue(start.contains("registerInputDeviceListener(deviceListener, h)"), "nhớ InputDevice theo id, làm mới qua listener")
        assertTrue(SourceRoots.body(recorder, "fun stop()").contains("unregisterInputDeviceListener(deviceListener)"))
    }

    @Test
    fun `L3 - song theo mot lan bind, doc qua gateway DUY NHAT`() {
        val connected = SourceRoots.body(a11y, "override fun onServiceConnected()")
        assertTrue(connected.contains("keySource = KeySourceRecorder(app) { AppContainer.get(app).halGateway }.also { it.start() }"))
        assertTrue(connected.indexOf("keySource?.stop()") in 0 until connected.indexOf("KeySourceRecorder(app)"),
            "bind lại không được để luồng cũ mồ côi")
        listOf("override fun onUnbind(intent: android.content.Intent?): Boolean", "override fun onDestroy()").forEach {
            assertTrue(SourceRoots.body(a11y, it).contains("keySource?.stop()"), "$it phải dừng bộ đo")
        }
        assertFalse(recorder.contains("BydHalGateway("), "gateway thứ hai = cache device thứ hai, log-một-lần thứ hai")
        val read = SourceRoots.body(gateway, "override fun featureRead(deviceFqn: String, id: Int): HalFeatureRead")
        assertTrue(read.contains("device(deviceFqn)") && read.contains("BydHal.readFeature(dev, id)"), "cùng đường đọc với featureGet")
        assertTrue(read.contains("HalFeatureRead.NoDevice") && read.contains("HalFeatureRead.Failed("))
    }

    @Test
    fun `L4 - hop hoc phim hien dong chi tiet nguon phim`() {
        val learn = SourceRoots.body(section, "private fun learn()")
        assertTrue(learn.contains("detail = { tv -> KeySourceDetailText.bind(tv, code) { bridge.learnedKeySource(code) } }"))
        assertTrue(SourceRoots.body(bridgeKeys, "fun ClusterNavBridge.learnedKeySource(code: Int): KeySourceEntry?")
            .contains("KeySourceRecorder.journal.lastLearned(code)"))
        val ask = SourceRoots.body(dialogs, "fun askName(")
        assertTrue(ask.contains("if (detail == null) input else") && ask.contains("detail(this)"),
            "không có detail ⇒ hộp y nguyên như cũ; có ⇒ thêm một dòng dưới ô tên")
        val text = SourceRoots.body(detail, "fun text(ctx: Context, code: Int, e: KeySourceEntry?, gaveUp: Boolean): String")
        assertTrue(text.contains("R.string.kachi_key_src_detail"))
        val label = SourceRoots.body(detail, "private fun sourceLabel(ctx: Context, e: KeySourceEntry?, gaveUp: Boolean): String")
        listOf("Pending", "NotMeasured", "Source", "UnknownValue", "Failed").forEach {
            assertTrue(label.contains("KeySourceVerdict.$it"), "nhánh $it thiếu nhãn")
        }
        listOf("kachi_key_src_knob", "kachi_key_src_wheel", "kachi_key_src_unknown", "kachi_key_src_failed",
            "kachi_key_src_not_measured", "kachi_key_src_pending").forEach { assertTrue(detail.contains("R.string.$it"), it) }
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        assertTrue(vi.contains(">núm yên ngựa<") && vi.contains(">vô-lăng<"))
    }

    @Test
    fun `L5 - tang 1 KHONG doi gan, khop, JSON`() {
        assertTrue(onKey.contains("voiceKeyMatcher.onKey(cfg, action, event.keyCode, event.downTime)"), "khớp vẫn theo keyCode")
        assertTrue(onKey.contains("VoiceKeyLearnBus.publish(event.keyCode)"), "bus học phím vẫn mang mã Int")
        assertTrue(SourceRoots.body(section, "private fun learn()")
            .contains("bridge.addCustomButton(context.getString(R.string.kachi_key_custom_name, name, code), code)"))
        listOf(
            "src/main/java/com/byd/clusternav/voicekey/VoiceKeyBindings.kt",
            "src/main/java/com/byd/clusternav/voicekey/VoiceKeyMatcher.kt",
            "$base/modules/voicekey/VoiceKeyBindingStore.kt",
            "$base/modules/voicekey/VoiceKeyLearnBus.kt",
        ).forEach { f -> assertFalse(code(f).contains("KeySource"), "$f: tầng 1 không được chạm danh tính/khớp/lưu") }
    }

    @Test
    fun `L6 - moi ham moi co loi goi that ngoai dinh nghia`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.name to it.readText() }.toList()
        }
        fun callers(token: String, defFile: String) = all.filter { (name, src) -> name != defFile && src.contains(token) }.map { it.first }.distinct()
        assertEquals(listOf("NavAccessibilityService.kt"), callers("KeySourceRecorder.sampleOf(", "KeySourceRecorder.kt"))
        assertEquals(listOf("NavAccessibilityService.kt"), callers(".onDown(KeySourceRecorder", "KeySourceRecorder.kt"))
        assertEquals(listOf("SettingsSectionsKeys.kt"), callers("bridge.learnedKeySource(", "ClusterNavBridgeKeys.kt"))
        assertEquals(listOf("SettingsSectionsKeys.kt"), callers("KeySourceDetailText.bind(", "SettingsKeySourceDetail.kt"))
        assertTrue("KeySourceProbe.kt" in callers("gateway.featureRead(", "HalRoutes.kt"))
        assertTrue("KeySourceRecorder.kt" in callers("KeySourceProbes.read(", "KeySourceProbe.kt"))
        assertTrue("KeySourceRecorder.kt" in callers(".halGateway", "AppContainer.kt") ||
            "NavAccessibilityService.kt" in callers(".halGateway", "AppContainer.kt"))
    }
}
