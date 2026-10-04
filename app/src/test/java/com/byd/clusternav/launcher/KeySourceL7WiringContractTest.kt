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
 *  - L4 hộp "Học phím mới" hiện dòng chi tiết;
 *  - L5 (2.88 · tầng 2, thay bài "tầng 1 không đổi gán/khớp/JSON"): khớp theo nguồn CHỈ qua hàm tra của matcher — dây nối
 *    chi tiết ở `KeySourceSplitWiringContractTest`;
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

    /**
     * 2.88: chữ ký chép trên MỌI DOWN (đầu hàm); đường KHÔNG khớp (đuôi học / đang học / tắt) ghi nhật ký ngay — trước bus
     * học phím — còn đường khớp ghi SAU matcher để mang số đọc nguồn đồng bộ (R-nf2). Hai lời gọi phủ đúng hai đường đó.
     */
    @Test
    fun `L1 - onKeyEvent chep chu ky tren DOWN roi day do di, truoc khi bus hoc phim bao ma`() {
        val down = SourceRoots.body(onKey, "val sample = if (event.action == KeyEvent.ACTION_DOWN) {")
        assertTrue(down.contains("KeySourceRecorder.sampleOf(event)"), "mỗi DOWN phải được chép chữ ký ngay")
        val plain = "if (sample != null && !matching) keySource?.onDown(sample, learned = learning)"
        assertTrue(onKey.contains(plain), "đường không khớp: đo y như 2.87, kèm cờ đang-học để hộp đặt tên tìm đúng dòng")
        assertTrue(onKey.contains("val matching = !tail && !learning && Prefs.voiceKeyEnabled(app)"))
        assertTrue(onKey.indexOf(plain) in 0 until onKey.indexOf("VoiceKeyLearnBus.publish(event.keyCode)"),
            "ghi nhật ký TRƯỚC khi bus báo mã ⇒ hộp đặt tên luôn thấy dòng của lần học")
        assertTrue(onKey.contains("if (sample != null) keySource?.onDown(sample, learned = false, preRead = preRead)"),
            "đường khớp: mỗi DOWN vẫn được ghi nhật ký, dùng lại số đọc của đường gán")
    }

    @Test
    fun `L1 - luong nhan phim KHONG I-O, KHONG HAL, KHONG tra InputDevice`() {
        val sampleOf = SourceRoots.body(recorder, "fun sampleOf(e: KeyEvent): KeySample")
        val onDown = SourceRoots.body(recorder, "fun onDown(sample: KeySample, learned: Boolean, preRead: KeySourceReading? = null)")
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
        assertTrue(onDown.contains("h.post { settle(seq, sample, preRead) }"), "phần đo phải sang luồng `kachi-keysrc`")
        // Event bị recycle sau onKeyEvent ⇒ chỉ được chép field, không giữ tham chiếu event.
        assertFalse(recorder.contains("KeyEvent.obtain") || Regex("""val\s+\w+\s*:\s*KeyEvent""").containsMatchIn(recorder))
    }

    /**
     * 2.87 · SOÁT vòng 1 · P2: LUẬT busy/trần/quá hạn/không chạy + thứ tự HAL-trước-thiết-bị nay là [KeySourceMeter] ở
     * `:core`, CHẠY THẬT trong `KeySourceMeterTest` (bộ thi hành thật + gateway chặn bằng chốt). Ở đây chỉ còn DÂY:
     * luồng đo gọi đúng bộ đo đó, với đúng bộ thi hành HAL riêng + đồng hồ cùng gốc với `KeyEvent.eventTime`.
     */
    @Test
    fun `L2 - doc HAL tren luong rieng, co tran, khong xep chong khi treo`() {
        assertTrue(recorder.contains("KeySourceMeter(gateway = gateway, exec = { halExec }, clockMs = SystemClock::uptimeMillis)"),
            "bộ đo thuần dùng ĐÚNG luồng HAL riêng + đồng hồ uptime (cùng gốc eventTime)")
        assertTrue(SourceRoots.body(recorder, "fun start()").contains("halExec = Executors.newSingleThreadExecutor"),
            "lượt đọc HAL chạy trên MỘT luồng riêng — không trên luồng đo, không trên luồng phím")
        assertFalse(recorder.contains("inFlight") || recorder.contains("f.get("), "luật chống chồng lượt chỉ sống ở KeySourceMeter (:core)")
        val settle = SourceRoots.body(recorder, "private fun settle(seq: Long, sample: KeySample, preRead: KeySourceReading?)")
        assertTrue(settle.contains("meter.measure(sample, devices, preRead)"), "2.88 · R-nf2: số đọc sẵn đi vào bộ đo (không đọc lần hai)")
        assertTrue(settle.contains("Log.i(KeySourceLog.TAG, KeySourceLog.line(entry))"), "một dòng KachiKey mỗi DOWN ⇒ usage-*.log")
        assertTrue(settle.contains("catch (e: RuntimeException)"), "lỗi đo không được làm chết tiến trình giữ dịch vụ phím")
        assertTrue(recorder.contains("private val devices = KeyDeviceCache(::lookupDevice)"), "P3: nhớ cả ca 'không có thiết bị'")
        listOf("onInputDeviceAdded", "onInputDeviceRemoved", "onInputDeviceChanged").forEach {
            assertTrue(Regex("""override fun $it\(deviceId: Int\) \{ devices\.forget\(deviceId\) \}""").containsMatchIn(recorder),
                "$it phải bỏ nhớ thiết bị (kể cả 'thêm': id đã nhớ là 'không có')")
        }
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
        // SOÁT vòng 1 · P3: lượt KHÔNG đọc (`busy` · `not_running`) mang `readMs = -1` (KeySourceMeterTest) ⇒ hiện "—",
        // không bao giờ *"đọc 0 ms"*.
        assertTrue(text.contains("it.probe != null && it.readMs >= 0"), "chỉ hiện thời lượng khi CÓ lượt đọc")
        val label = SourceRoots.body(detail, "private fun sourceLabel(ctx: Context, e: KeySourceEntry?, gaveUp: Boolean): String")
        listOf("Pending", "NotMeasured", "Source", "UnknownValue", "Failed").forEach {
            assertTrue(label.contains("KeySourceVerdict.$it"), "nhánh $it thiếu nhãn")
        }
        listOf("kachi_key_src_knob", "kachi_key_src_wheel", "kachi_key_src_unknown", "kachi_key_src_failed",
            "kachi_key_src_not_measured", "kachi_key_src_pending").forEach { assertTrue(detail.contains("R.string.$it"), it) }
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        assertTrue(vi.contains(">núm yên ngựa<") && vi.contains(">vô-lăng<"))
    }

    /**
     * Tầng 1 từng khoá "gán/khớp/JSON KHÔNG đổi". 2.88 (tầng 2, spec `kachi-288-key-source-split`) đổi CÓ CHỦ Ý ba thứ đó,
     * nên bài này khoá phần thay thế: khớp vẫn theo keyCode + downTime và CHỈ thêm hàm tra nguồn (matcher quyết khi nào gọi —
     * `VoiceKeyMatcherSourceTest`); bus học phím vẫn mang mã Int; tên nút không nguồn vẫn đúng khuôn 2.87; luồng phím không
     * tự đọc HAL ngoài hàm tra.
     */
    @Test
    fun `L5 - tang 2 khop theo nguon chi qua ham tra cua matcher`() {
        assertTrue(onKey.contains("voiceKeyMatcher.onKey(cfg, action, event.keyCode, event.downTime) {"), "khớp vẫn theo keyCode + downTime")
        assertTrue(onKey.contains("VoiceKeyLearnBus.publish(event.keyCode)"), "bus học phím vẫn mang mã Int")
        assertTrue(SourceRoots.body(section, "private fun learn()")
            .contains("context.getString(R.string.kachi_key_custom_name, name, code)"), "nút học không nguồn giữ khuôn tên 2.87")
        assertFalse(code("$base/modules/voicekey/VoiceKeyLearnBus.kt").contains("KeySource"), "bus học phím không mang nguồn")
        listOf("KeySourceProbes.read", "KeySourceResolver(", "featureRead").forEach {
            assertFalse(a11y.contains(it), "NavAccessibilityService không tự đọc HAL ($it) — chỉ qua bộ ghi")
        }
    }

    @Test
    fun `L6 - moi ham moi co loi goi that ngoai dinh nghia`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.name to it.readText() }.toList()
        }
        fun callers(token: String, defFile: String) = all.filter { (name, src) -> name != defFile && src.contains(token) }.map { it.first }.distinct()
        assertEquals(listOf("NavAccessibilityService.kt"), callers("KeySourceRecorder.sampleOf(", "KeySourceRecorder.kt"))
        assertEquals(listOf("NavAccessibilityService.kt"), callers("keySource?.onDown(sample", "KeySourceRecorder.kt"))
        assertEquals(listOf("SettingsSectionsKeys.kt"), callers("bridge.learnedKeySource(", "ClusterNavBridgeKeys.kt"))
        assertEquals(listOf("SettingsSectionsKeys.kt"), callers("KeySourceDetailText.bind(", "SettingsKeySourceDetail.kt"))
        assertTrue("KeySourceProbe.kt" in callers("gateway.featureRead(", "HalRoutes.kt"))
        // SOÁT vòng 1 · P2: lượt đọc nay đi qua bộ đo thuần `:core`; bộ ghi gọi bộ đo.
        assertTrue("KeySourceMeter.kt" in callers("KeySourceProbes.read(", "KeySourceProbe.kt"))
        // 2.88: KeySourceResolver dựng bản đo THỨ HAI (trần 100 ms) cho đường gán — dùng lại luật, không chép.
        assertEquals(listOf("KeySourceRecorder.kt", "KeySourceResolver.kt"), callers("KeySourceMeter(", "KeySourceMeter.kt").sorted())
        assertEquals(listOf("KeySourceRecorder.kt"), callers("KeyDeviceCache(", "KeySourceMeter.kt"))
        assertTrue("KeySourceRecorder.kt" in callers(".halGateway", "AppContainer.kt") ||
            "NavAccessibilityService.kt" in callers(".halGateway", "AppContainer.kt"))
    }
}
