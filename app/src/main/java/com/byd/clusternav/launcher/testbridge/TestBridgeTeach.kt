package com.byd.clusternav.launcher.testbridge

import android.content.Context
import com.byd.clusternav.launcher.voice.AppAltLabels
import com.byd.clusternav.launcher.voice.TaughtName
import com.byd.clusternav.launcher.voice.TaughtNames
import com.byd.clusternav.launcher.voice.TaughtSource
import com.byd.clusternav.launcher.voice.TeachContext
import com.byd.clusternav.launcher.voice.TeachGuard
import com.byd.clusternav.launcher.voice.TeachSample
import com.byd.clusternav.launcher.voice.VoicePlaces
import com.byd.clusternav.launcher.voice.VoiceWakePhrase
import com.byd.clusternav.launcher.voice.VoiceWavProbe
import com.byd.clusternav.launcher.voice.VoiceWiring
import com.byd.clusternav.launcher.voice.dynVocabOf
import java.util.concurrent.ConcurrentHashMap

/**
 * ═══ 2.91 VOICE-APP-NAMES · A7 — LỆNH CẦU `teach` · `teach_text` · `teach_clear` ═══════════════════════════════════
 *
 * Spec §4.11. Chỉ chạy khi test-mode bật (cổng chung của [KachiTestBridge]). Mã lệnh khai ở `:core`
 * ([TestBridgeTeachCommands]).
 *  • `teach` — WAV đi ĐÚNG đường giải mã của lệnh `wav` ([TestBridgeWav.stage] + [VoiceWavProbe], cùng recognizer + hotword
 *    của phiên lệnh) ⇒ chữ nghe được ⇒ [TeachSample.normalize] ⇒ [TeachGuard.check]. `op=save` ⇒ máy dò hồi quy
 *    ([TeachGuard.regression]) ⇒ ghi qua CỔNG VIEWMODEL như UI ([TestBridgeHooks.voiceNames]).
 *  • `teach_text` — tên gõ (nguồn [TaughtSource.TYPED]), cùng cổng.
 *  • `teach_clear` — xoá tên của một gói (vắng gói ⇒ cả hồ sơ đang dùng).
 * Trả về `heard` (chữ máy nghe — đây là bề mặt ĐO của chính người chạy harness, không phải logcat) · `name` · `takes` ·
 * `verdict` · `reasons` (mã) · `saved`. KHÔNG thi hành câu nói nào.
 *
 * `takes` = số lượt `teach` của gói (từ lần lưu/xoá gần nhất của gói ấy) mà mô hình in ra CÙNG chuỗi chuẩn hoá — đúng
 * thước hộp dạy đếm (gộp mẫu theo `norm`); tên GIỌNG 3 chữ cái cần ≥ [TeachGuard.MIN_TAKES_SHORT] (spec §7 OQ4). Một
 * lượt `op=save` cũng là một lượt nói (WAV được giải mã lại), nên harness dạy tên ngắn bằng hai WAV khác nhau rồi mới lưu.
 */
internal object TestBridgeTeach {

    /** Chuỗi chuẩn hoá của từng lượt `teach` (giọng) theo gói — chỉ sống trong RAM tiến trình chính (cầu kiểm thử). */
    private val takesByPkg = ConcurrentHashMap<String, MutableList<String>>()

    /** Ghi một lượt [norm] của [pkg] rồi trả số lượt ra CÙNG chuỗi ấy (tính cả lượt này). */
    private fun recordTake(pkg: String, norm: String): Int {
        val list = takesByPkg.getOrPut(pkg) { java.util.Collections.synchronizedList(ArrayList()) }
        synchronized(list) { list += norm; return list.count { it == norm } }
    }

    fun run(app: Context, cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        Thread({
            when (cmd.name) {
                TestBridgeTeachCommands.TEACH_CLEAR -> clear(cmd, hooks, reply)
                TestBridgeTeachCommands.TEACH -> {
                    val stageError = TestBridgeWav.stage(app, cmd.path)
                    if (stageError != null) { reply.fail(stageError, "path" to cmd.path); return@Thread }
                    val labels = VoiceWiring.appsByLabel(app)
                    val st = hooks.state()
                    val vocab = dynVocabOf(app, st.profiles, labels, VoicePlaces.labelsOf(st.savedPlaces))   // 2.93 DYNVOCAB
                    val probe = VoiceWavProbe.run(app, st.profiles, labels.keys.toList(), labels.values.toSet(), vocab)
                    val sample = TeachSample.normalize(probe.heard)
                    val takes = (sample as? TeachSample.Sample)?.let { recordTake(cmd.pkg, it.norm) } ?: 0
                    judge(app, cmd, hooks, reply, probe.heard, sample, TaughtSource.SPEECH, probe.error, takes)
                }
                else -> judge(app, cmd, hooks, reply, cmd.text, TeachSample.shape(cmd.text), TaughtSource.TYPED, null, 1)
            }
        }, "KachiTestTeach").start()
    }

    @Suppress("LongParameterList")
    private fun judge(
        app: Context,
        cmd: TestBridgeCommand,
        hooks: TestBridgeHooks,
        reply: TestBridgeReply,
        heard: String,
        sample: TeachSample.Result,
        source: TaughtSource,
        probeError: String?,
        takes: Int,
    ) {
        val port = hooks.voiceNames()
        val names = port.names()
        val ctx = contextOf(app, hooks, names)
        val s = sample as? TeachSample.Sample
        val label = ctx.keys.entries.firstOrNull { it.value == cmd.pkg }?.key.orEmpty()
        val verdict = s?.let { TeachGuard.check(ctx, cmd.pkg, it.accented, source, takes) }
        var saved = false
        var changed: List<String> = emptyList()
        var reachable: Boolean? = null
        var refused: String? = null
        if (cmd.op == TestBridgeTeachCommands.OP_SAVE && s != null && verdict != null &&
            (verdict.level == TeachGuard.Level.NEW || verdict.level == TeachGuard.Level.WARN)
        ) {
            val name = TaughtName(cmd.pkg, source, s.accented, label)
            val reg = TeachGuard.regression(ctx, name)
            changed = reg.changed; reachable = reg.reachable
            if (reg.ok) {
                when (val r = TaughtNames.add(names, name)) {
                    is TaughtNames.Added -> {
                        saved = onMain { port.save(r.names) }
                        if (saved) takesByPkg.remove(cmd.pkg)   // lần dạy kế của gói đếm lại từ đầu (như hộp dạy sau khi lưu)
                    }
                    is TaughtNames.Refused -> refused = r.why.name
                }
            }
        }
        reply.ok(
            listOf(
                "pkg" to cmd.pkg,
                "label" to label,
                "heard" to heard,
                "probe_error" to probeError,
                "name" to s?.accented,
                "takes" to takes,
                "reject" to (sample as? TeachSample.Rejected)?.why?.name,
                "verdict" to verdict?.level?.name,
                "reasons" to TestBridgeJson.Raw(TestBridgeJson.arr(verdict?.reasons.orEmpty().map { it.code.name })),
                "saved" to saved,
                "refused" to refused,
                "regression_changed" to TestBridgeJson.Raw(TestBridgeJson.arr(changed)),
                "reachable" to reachable,
                "taught_count" to TaughtNames.of(port.names(), cmd.pkg).size,
            ),
        )
    }

    private fun clear(cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        val port = hooks.voiceNames()
        val next = if (cmd.pkg.isBlank()) emptyList() else TaughtNames.removeApp(port.names(), cmd.pkg)
        if (cmd.pkg.isBlank()) takesByPkg.clear() else takesByPkg.remove(cmd.pkg)
        val ok = onMain { port.save(next) }
        reply.ok("cleared" to ok, "pkg" to cmd.pkg.ifBlank { null }, "left" to port.names().size)
    }

    /** CÙNG nguồn với trang Cài đặt ([com.byd.clusternav.launcher.SettingsVoiceNamesPage.teachContext]). */
    private fun contextOf(app: Context, hooks: TestBridgeHooks, names: List<TaughtName>): TeachContext {
        val st = hooks.state()
        return TeachContext(
            keys = VoiceWiring.appIndex(app, names).keys,
            taught = names,
            profiles = st.profiles,
            places = VoicePlaces.labelsOf(st.savedPlaces),
            wakePhrases = VoiceWakePhrase.PRESETS.map { it.spoken },
        )
    }

    /** Ghi qua ViewModel trên luồng vẽ (StateFlow + prefs), chờ kết quả ≤ 3 s. */
    private fun onMain(block: () -> Boolean): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        var out = false
        android.os.Handler(android.os.Looper.getMainLooper()).post { out = runCatching(block).getOrDefault(false); latch.countDown() }
        latch.await(MAIN_WAIT_S, java.util.concurrent.TimeUnit.SECONDS)
        return out
    }

    /** Đếm tên đã dạy theo gói + nhãn phụ trong bộ nhớ — cho `state` (chỉ SỐ, không chữ). */
    fun stateJson(hooks: TestBridgeHooks): TestBridgeJson.Raw {
        val names = runCatching { hooks.voiceNames().names() }.getOrDefault(emptyList())
        val (altApps, altLabels) = AppAltLabels.counts()
        return TestBridgeJson.Raw(
            TestBridgeJson.obj(
                "taught_total" to names.size,
                "taught_by_pkg" to TestBridgeJson.Raw(TestBridgeJson.obj(names.groupBy { it.pkg }.map { (p, ns) -> p to ns.size })),
                "read_only" to runCatching { hooks.voiceNames().readOnly() }.getOrDefault(false),
                "alt_label_apps" to altApps,
                "alt_labels" to altLabels,
                "alt_sample" to TestBridgeJson.Raw(TestBridgeJson.arr(AppAltLabels.sample(ALT_SAMPLE))),
                // R-nf6 — µs của lượt dựng bảng gọi app gần nhất: riêng `VoiceAppIndex.build` · cả `appIndex` (gồm PackageManager).
                "index_build_us" to VoiceWiring.lastBuildMicros,
                "index_total_us" to VoiceWiring.lastIndexMicros,
            ),
        )
    }

    private const val MAIN_WAIT_S = 3L
    private const val ALT_SAMPLE = 30
}
