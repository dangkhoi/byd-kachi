package com.byd.clusternav.launcher.testbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byd.clusternav.BuildConfig
import com.byd.clusternav.launcher.EffectiveLayout
import com.byd.clusternav.launcher.LayoutPreset
import com.byd.clusternav.launcher.SlotCodec
import com.byd.clusternav.launcher.reapplyAll
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.launcher.voice.VoiceWavProbe
import com.byd.clusternav.launcher.voice.VoiceWiring
import com.byd.clusternav.modules.clustercast.ClusterDiag
import com.byd.clusternav.system.PackageQueries
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * ═══ T-BRIDGE · CẦU KIỂM THỬ QUA adb ═════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html`. Owner 2026-09-14: *"sẽ adb vào xe, nên chuẩn bị toàn bộ script test
 * automation trên xe thông qua adb"*.
 *
 * ```
 * adb shell am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd state
 * ```
 *
 * ## Vì sao `exported="true"` — và bốn thứ bù lại
 * [ĐO] nghiên cứu 09-14: uid shell (2000) **không** gửi được vào một receiver `exported=false` (`SecurityException`),
 * y như `am start` vào activity non-exported trên ROM BYD (chú thích manifest của `.ClusterNavActivity` đã ghi ca
 * đó từ 2026-07-20). Mà một receiver thì **không đọc được uid của bên gửi** — `onReceive` không có
 * `getCallingUid`. Nghĩa là "exported" ở đây thật sự là *"mọi app trên xe đều bắn vào được"*, và bốn lớp dưới
 * đây là toàn bộ thứ đứng giữa:
 *
 *  1. **Công tắc do NGƯỜI trong xe bật**, tự tắt sau 60 phút và chết theo lần nổ máy ([TestBridgeStore]). Tắt ⇒
 *     mọi lệnh trả `test_mode_off` và **không nhánh nào chạy**. Không có đường bật bằng broadcast — có chủ ý.
 *  2. **Không mở đường thứ hai tới bất cứ thứ gì**: mọi lệnh đi qua đúng đường mà một cú chạm đi
 *     ([TestBridgeHooks]). Cầu này không tự gọi `am`/`wm`, không tự dựng `VirtualDisplay`, không chạm màn cụm.
 *  3. **Cổng xác nhận không tự mở**: việc mức `CONFIRM` (mở khoá cửa, hạ kính, đổi hồ sơ…) bị **từ chối** và báo
 *     về, trừ khi lệnh nói rõ `--ez auto_confirm true` — xem [runSay].
 *  4. **Mọi lượt chạy đều để lại dấu**: một dòng `KachiTest` trong logcat + một tệp JSON trong `files/test/`.
 *     Không có lệnh nào chạy im lặng.
 *
 * ## Cái cầu này KHÔNG làm
 * Không chạm **display 1** (màn cụm trước mặt người lái) dưới bất kỳ hình thức nào, và không có nhánh nào gọi
 * tầng chiếu-cụm. `LauncherWindowingGuardTest` canh chiều đó cho cả thư mục `launcher/`;
 * `TestBridgeSafetyContractTest` canh riêng cho cầu này.
 */
class KachiTestBridge : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        val extras = readExtras(intent)
        val reply = TestBridgeReply(app, goAsync(), fileTag(extras[TestBridgeCommands.EXTRA_CMD] as? String))
        Log.i(TAG, "cmd " + describe(extras))
        when {
            intent?.action != action() -> reply.fail(ERR_BAD_ACTION)
            !TestBridgeStore.isOn(app) -> reply.fail(ERR_TEST_MODE_OFF)
            else -> when (val parsed = TestBridgeCommands.parse(extras, TestBridgeState.READABLE_PREFS_FILES)) {
                is TestBridgeParse.Err -> reply.fail(parsed.code)
                is TestBridgeParse.Ok -> runCatching { dispatch(app, parsed.cmd, reply) }
                    .onFailure { t ->
                        Log.w(TAG, "lenh ${parsed.cmd.name} nem: ${t.javaClass.simpleName}", t)
                        reply.fail(ERR_THREW, "exception" to t.javaClass.simpleName)
                    }
            }
        }
    }

    // ── Đọc extra ────────────────────────────────────────────────────────────────────────────────

    /**
     * Gỡ extra khỏi Intent thành `Map` thuần — **ranh giới Android/thuần** của cầu này.
     *
     * Chỉ đọc những tên đã khai ([TestBridgeCommands.SPECS] dùng đúng tập này) và đọc **đúng kiểu**: `--ei n 2`
     * cho ra `Int`, `--ez auto_confirm true` cho ra `Boolean`. Gọi `getStringExtra` lên một extra kiểu số thì
     * Android trả `null` chứ không ném, nên một lệnh gõ nhầm cờ (`--es n 2`) rơi vào nhánh *thiếu đối số* với
     * mã lỗi nói đúng tên đối số — không phải một `ClassCastException` giữa broadcast.
     */
    private fun readExtras(intent: Intent?): Map<String, Any?> =
        // ⚠ Cả thân hàm bọc trong `runCatching`, KHÔNG chỉ từng phép đọc: gói extra đến từ một tiến trình khác,
        // và một `Parcelable` lạ (lớp không có trong app này) làm `hasExtra`/`getStringExtra` ném
        // `BadParcelableException` ngay ở lượt bung gói đầu tiên. Ném trong `onReceive` = **launcher chết** —
        // tức bất kỳ app nào trên xe cũng hạ được màn chính bằng một broadcast, kể cả khi chế độ kiểm thử TẮT.
        runCatching { readExtrasOrThrow(intent) }.getOrDefault(emptyMap())

    private fun readExtrasOrThrow(intent: Intent?): Map<String, Any?> {
        if (intent == null) return emptyMap()
        val out = LinkedHashMap<String, Any?>()
        STRING_EXTRAS.forEach { k -> runCatching { intent.getStringExtra(k) }.getOrNull()?.let { out[k] = it } }
        if (intent.hasExtra(TestBridgeCommands.EXTRA_SLOT)) {
            runCatching { intent.getIntExtra(TestBridgeCommands.EXTRA_SLOT, 0) }
                .getOrNull()?.let { out[TestBridgeCommands.EXTRA_SLOT] = it }
        }
        if (intent.hasExtra(TestBridgeCommands.EXTRA_AUTO_CONFIRM)) {
            runCatching { intent.getBooleanExtra(TestBridgeCommands.EXTRA_AUTO_CONFIRM, false) }
                .getOrNull()?.let { out[TestBridgeCommands.EXTRA_AUTO_CONFIRM] = it }
        }
        return out
    }

    /** Một dòng nhật ký ghi **nguyên văn** lệnh vừa nhận — để đối chiếu với thứ người gõ tưởng mình đã gửi. */
    private fun describe(extras: Map<String, Any?>): String =
        if (extras.isEmpty()) "(rong)" else extras.entries.joinToString(" ") { (k, v) -> "$k=$v" }

    /**
     * Tên lệnh rút gọn dùng để **đặt tên tệp**.
     *
     * ⚠ Đây là đường *dữ liệu người dùng → đường dẫn tệp* (CLAUDE.md §4.1): chuỗi này đến từ một app bất kỳ trên
     * xe. Lọc về đúng chữ/số/gạch dưới nên `../../etc` hay một tên 4 kB không bao giờ tới được `File(...)`.
     */
    private fun fileTag(raw: String?): String =
        raw.orEmpty().filter { it.isLetterOrDigit() || it == '_' }.take(TAG_CAP).ifEmpty { UNNAMED }

    // ── Điều phối ────────────────────────────────────────────────────────────────────────────────

    private fun dispatch(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        // `prefs` là lệnh DUY NHẤT không cần màn chính: nó chỉ đọc đĩa. Cho nó chạy khi launcher chưa lên là
        // đúng thứ cần lúc chẩn đoán *"vì sao launcher không lên"*.
        if (cmd.name == TestBridgeCommands.PREFS) {
            reply.ok("file" to cmd.file, "values" to TestBridgeState.prefsSnapshot(app, cmd.file))
            return
        }
        val hooks = KachiTestHooks.get()
        if (hooks == null) {
            reply.fail(ERR_NO_HOME)
            return
        }
        armTimeout(reply)
        when (cmd.name) {
            TestBridgeCommands.STATE -> reply.ok("state" to TestBridgeState.build(app, hooks))
            TestBridgeCommands.SAY -> runSay(cmd, hooks, reply)
            TestBridgeCommands.WAV -> runWav(app, cmd, hooks, reply)
            TestBridgeCommands.LISTEN -> runListen(hooks, reply)
            TestBridgeCommands.PROFILES -> reply.ok(
                "active" to hooks.state().activeProfile,
                "all" to TestBridgeJson.Raw(TestBridgeJson.arr(hooks.state().profiles)),
            )
            TestBridgeCommands.PROFILE -> runProfile(cmd, hooks, reply)
            TestBridgeCommands.PRESET -> runPreset(cmd, hooks, reply)
            TestBridgeCommands.SLOT -> runSlot(app, cmd, hooks, reply)
            TestBridgeCommands.SLOT_CLEAR -> runSlotClear(cmd, hooks, reply)
            TestBridgeCommands.OPEN -> runOpen(app, cmd, hooks, reply)
            TestBridgeCommands.REAPPLY -> runReapply(hooks, reply)
            TestBridgeCommands.DIAG -> runDiag(app, hooks, reply)
            else -> reply.fail(TestBridgeCommands.ERR_UNKNOWN_CMD)
        }
    }

    /**
     * Trần cứng cho một lượt chạy.
     *
     * Cần vì ba lệnh chạy **không đồng bộ** (`say` chờ câu trả lời về muộn, `wav` giải mã ở luồng nền, `diag` mở
     * kênh dadb). Không có trần thì một lượt kẹt sẽ giữ `PendingResult` cho tới khi nền tảng tự bắn
     * *"BroadcastQueue timeout"* — lúc đó người gõ lệnh không nhận được gì cả, kể cả một chữ giải thích.
     */
    private fun armTimeout(reply: TestBridgeReply) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!reply.isAnswered()) reply.fail(ERR_TIMEOUT)
        }, CAP_MS)
    }

    // ── Giọng nói ────────────────────────────────────────────────────────────────────────────────

    /**
     * Một câu lệnh chữ, đi **đúng** đường của ô *Gõ lệnh chữ* (`VoiceTextConsole`): phân tích một lần rồi thi
     * hành chính danh sách vừa phân tích.
     *
     * ## Cổng CONFIRM: mặc định **TỪ CHỐI**, không hiện hộp thoại
     * Đây là sai lệch có chủ ý so với hai bề mặt kia, và lý do là **cầu này không có ai ngồi trước màn**. Bung
     * một hộp thoại từ một broadcast nghĩa là đè một câu hỏi lên bất cứ thứ gì người lái đang nhìn, rồi đứng chờ
     * một cú chạm mà bên gửi (một script) không bao giờ thực hiện được — hết giờ, và cả lượt đo báo `timeout`
     * thay vì báo đúng việc. Từ chối thì lượt đo trả về **câu hỏi nguyên văn** trong `needs_confirm`, và script
     * chạy lại với `--ez auto_confirm true` nếu người viết script thật sự muốn việc đó.
     *
     * `--ez auto_confirm true` ghi một dòng `AUTO-CONFIRM` **kèm nguyên văn câu hỏi** vào logcat trước khi đồng
     * ý — một việc mức CONFIRM không bao giờ được xảy ra mà không có dấu vết.
     */
    private fun runSay(cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        val said = Collections.synchronizedList(ArrayList<String>())
        val asked = Collections.synchronizedList(ArrayList<String>())
        val dispatcher = hooks.dispatcher(
            { line -> said.add(line) },
            { question, onYes, onNo ->
                if (cmd.autoConfirm) {
                    Log.i(TAG, "AUTO-CONFIRM: $question")
                    onYes()
                } else {
                    asked.add(question)
                    onNo()
                }
            },
        )
        val intents = dispatcher.preview(cmd.text)
        dispatcher.execute(intents)
        // Một số vế trả lời MUỘN (gói lệnh và câu dẫn đường chạy ở luồng nền). Nán lại một nhịp ngắn rồi mới
        // chốt: trả lời ngay thì `replies` rỗng ở đúng những lệnh đáng đo nhất.
        Handler(Looper.getMainLooper()).postDelayed({
            reply.ok(
                listOf(
                    "said" to cmd.text,
                    "auto_confirm" to cmd.autoConfirm,
                    "intents" to TestBridgeJson.Raw(TestBridgeJson.arr(intents.map { previewOf(it) })),
                    "replies" to TestBridgeJson.Raw(TestBridgeJson.arr(said.toList())),
                    "needs_confirm" to TestBridgeJson.Raw(TestBridgeJson.arr(asked.toList())),
                ),
            )
        }, GRACE_MS)
    }

    /** Một ý định: **mã loại** (ổn định, để script so) + câu *"đã hiểu là…"* (cho người đọc). */
    private fun previewOf(intent: com.byd.clusternav.launcher.voice.VoiceIntent): TestBridgeJson.Raw =
        TestBridgeJson.Raw(
            TestBridgeJson.obj(
                "kind" to (intent::class.simpleName ?: UNNAMED),
                "preview" to VoiceReply.preview(intent),
            ),
        )

    /**
     * Một tệp WAV đi qua **đúng** [VoiceWavProbe] mà ô *Thử bằng WAV* dùng (R14 của spec giọng nói).
     *
     * `--es path` được phục vụ bằng cách **chép** tệp vào đúng chỗ mà [VoiceWavProbe] dò (tên cố định
     * [VoiceWavProbe.FILE_NAME] trong thư mục ngoài của riêng app). Chép chứ không mở một đường đọc thứ hai:
     * đường đọc thứ hai sẽ không đi qua cùng phép kiểm khuôn WAV, và lúc đó phép đo nói về một con đường mã mà
     * phiên nghe thật không dùng — đúng thứ KDoc [VoiceWavProbe] cấm.
     */
    private fun runWav(app: Context, cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        Thread({
            val stageError = stageWav(app, cmd.path)
            if (stageError != null) {
                reply.fail(stageError, "path" to cmd.path)
                return@Thread
            }
            val apps = VoiceWiring.appsByLabel(app)
            val probe = VoiceWavProbe.run(app, hooks.state().profiles, apps.keys.toList(), apps.values.toSet())
            val intents = if (probe.heard.isBlank()) {
                emptyList()
            } else {
                // CHỈ phân tích, KHÔNG thi hành: đây là một phép đo tai nghe, không phải một lệnh.
                hooks.dispatcher({ }, { _, _, onNo -> onNo() }).preview(probe.heard)
            }
            reply.ok(
                listOf(
                    "path" to probe.path,
                    "staged_from" to cmd.path.ifBlank { null },
                    "heard" to probe.heard,
                    "grammar" to probe.grammarText,
                    "free" to probe.freeText,
                    "probe_error" to probe.error,
                    "where" to VoiceWavProbe.whereToPut(app),
                    "intents" to TestBridgeJson.Raw(TestBridgeJson.arr(intents.map { previewOf(it) })),
                ),
            )
        }, "KachiTestWav").start()
    }

    /**
     * Chép tệp WAV do lệnh chỉ định vào chỗ [VoiceWavProbe] dò. Trả **mã lỗi** hoặc `null` khi xong/không cần.
     *
     * Ba phép kiểm, mỗi phép chặn một ca thật: đường dẫn có `..` (chuỗi này đến từ ngoài tiến trình), tệp không
     * đọc được (gõ nhầm tên — hay gặp nhất), tệp quá lớn (đẩy nhầm một bản ghi dài làm đầy bộ nhớ xe). Tên tệp
     * ĐÍCH là hằng của [VoiceWavProbe] nên không có phần nào của chuỗi vào được đường dẫn ghi.
     */
    private fun stageWav(app: Context, path: String): String? {
        if (path.isBlank()) return null
        if (path.contains("..")) return ERR_BAD_PATH
        val src = File(path)
        if (!src.isFile || !src.canRead()) return ERR_WAV_NOT_FOUND
        if (src.length() > MAX_WAV_BYTES) return ERR_WAV_TOO_BIG
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        return runCatching {
            src.copyTo(File(dir, VoiceWavProbe.FILE_NAME), overwrite = true)
            null
        }.getOrElse { t ->
            Log.w(TAG, "chep WAV hong: ${t.javaClass.simpleName}")
            ERR_WAV_COPY
        }
    }

    /**
     * Mở một phiên nghe thật — CÙNG đường mà nút mic dùng.
     *
     * Trả lời **ngay**, không chờ phiên kết thúc: `VoiceSession` không phơi ra sự kiện *"phiên đã xong"* qua API
     * công khai, nên chờ ở đây chỉ là đoán. Kết quả thật của phiên đọc ở `adb logcat -s KachiVoice` (và trên tấm
     * chữ ở góc màn). Ghi thẳng điều đó vào lời đáp thay vì im lặng để người đọc tự phát hiện — [CHƯA BIẾT] về
     * một đường móc kết quả phiên, xem §Open Questions của spec.
     */
    private fun runListen(hooks: TestBridgeHooks, reply: TestBridgeReply) {
        if (hooks.activity() == null) {
            reply.fail(ERR_NO_HOME)
            return
        }
        hooks.listen()
        reply.ok("started" to true, "outcome" to NOTE_LISTEN)
    }

    // ── Hồ sơ · bố cục · ô ───────────────────────────────────────────────────────────────────────

    private fun runProfile(cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        val all = hooks.state().profiles
        val target = all.firstOrNull { it == cmd.arg } ?: all.firstOrNull { it.equals(cmd.arg, ignoreCase = true) }
        if (target == null) {
            reply.fail(ERR_UNKNOWN_PROFILE, "all" to TestBridgeJson.Raw(TestBridgeJson.arr(all)))
            return
        }
        hooks.switchProfile(target)
        reply.ok("active" to hooks.state().activeProfile)
    }

    private fun runPreset(cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        val target = LayoutPreset.entries.firstOrNull { it.name.equals(cmd.arg, ignoreCase = true) }
        if (target == null) {
            reply.fail(
                ERR_UNKNOWN_PRESET,
                "all" to TestBridgeJson.Raw(TestBridgeJson.arr(LayoutPreset.entries.map { it.name })),
            )
            return
        }
        hooks.setPreset(target)
        val now = hooks.state()
        reply.ok(
            "preset" to now.workspace.preset.name,
            "slot_count" to EffectiveLayout.slotCount(now.workspace.preset, now.customLayout),
        )
    }

    /**
     * Gói có **thật sự cài trên máy này** không — cổng đứng trước MỌI lệnh mang `--es pkg`.
     *
     * ⚠ Đây là một cổng an toàn, không phải một phép kiểm tiện nghi. Chuỗi `pkg` đến từ ngoài tiến trình và
     * đường đi tiếp của nó có một đoạn **shell**: `AppOpener.openByShell` nhét nguyên chuỗi vào
     * `FreeformLaunch.resolveCmd` (`cmd package resolve-activity … $pkg`) rồi chạy qua dadb, và `placeApp` cũng
     * vậy. Một gói KHÔNG cài thì `openByIntent` trả `false` ⇒ rơi đúng xuống nhánh shell ⇒ một chuỗi như
     * `x; <lệnh khác>` trở thành lệnh chạy thật. Hỏi `PackageManager` trước thì mọi chuỗi không phải tên một app
     * đang cài đều dừng ở đây — generic, không cần danh sách tên gói (CLAUDE.md §4.1 · §7).
     */
    private fun installed(app: Context, pkg: String): Boolean =
        pkg.isNotBlank() && PackageQueries.packageInfo(app.packageManager, pkg) != null

    private fun runSlot(app: Context, cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        if (!installed(app, cmd.pkg)) {
            reply.fail(ERR_UNKNOWN_PKG, "pkg" to cmd.pkg)
            return
        }
        val index = slotIndex(cmd, hooks, reply) ?: return
        val assigned = hooks.assignAppToSlot(index, cmd.pkg)
        reply.ok(
            "n" to cmd.slot,
            "pkg" to cmd.pkg,
            "assigned" to assigned,
            "content" to contentOf(hooks, index),
        )
    }

    private fun runSlotClear(cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        val index = slotIndex(cmd, hooks, reply) ?: return
        hooks.clearSlot(index)
        reply.ok("n" to cmd.slot, "content" to contentOf(hooks, index))
    }

    /**
     * Số ô 1-based → chỉ số 0-based, **sau khi** kiểm trần theo bố cục ĐANG dùng.
     *
     * Trần trên không thể kiểm ở `:core` (bố cục tự vẽ đổi được giữa hai lệnh) — cùng phân công với
     * `VoiceDispatcher.runOpenApp`, và lời đáp nói ra **con số thật** để script không phải đoán.
     */
    private fun slotIndex(cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply): Int? {
        val s = hooks.state()
        val count = EffectiveLayout.slotCount(s.workspace.preset, s.customLayout)
        if (cmd.slot > count) {
            reply.fail(ERR_SLOT_RANGE, "slot_count" to count)
            return null
        }
        return cmd.slot - 1
    }

    /** Nội dung một ô, dạng chuỗi ĐÚNG NHƯ trên đĩa (xem KDoc [TestBridgeState]). */
    private fun contentOf(hooks: TestBridgeHooks, index: Int): String =
        hooks.state().slots.getOrNull(index)?.let { SlotCodec.encode(it) }.orEmpty()

    private fun runOpen(app: Context, cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        if (!installed(app, cmd.pkg)) {
            reply.fail(ERR_UNKNOWN_PKG, "pkg" to cmd.pkg)
            return
        }
        hooks.openApp(cmd.pkg)
        // `requested`, KHÔNG phải `opened`: `KachiHomeSlots.openAppFullscreen` thử đường API rồi mới tới đường
        // shell ở luồng nền, nên "đã lên màn chưa" là câu hỏi của `am stack list`, không phải của cầu này. Nói
        // "opened" ở đây là hứa một thứ chưa đo được.
        reply.ok("pkg" to cmd.pkg, "requested" to true)
    }

    private fun runReapply(hooks: TestBridgeHooks, reply: TestBridgeReply) {
        runCatching { hooks.bridge().reapplyAll() }
            .onSuccess { reply.ok("reapplied" to true) }
            .onFailure { t ->
                Log.w(TAG, "reapplyAll nem: ${t.javaClass.simpleName}", t)
                reply.fail(ERR_THREW, "exception" to t.javaClass.simpleName)
            }
    }

    /**
     * Chụp chẩn đoán bằng **đúng** [ClusterDiag] mà màn *Chẩn đoán* dùng.
     *
     * Đòi kênh shell TRƯỚC: `ClusterDiag.capture` mở một phiên dadb và nếu không có kênh thì nó chỉ trả về một
     * tệp toàn dòng rỗng sau nhiều giây chờ — tức một lượt đo trông như "đã chụp" mà không có gì bên trong.
     * Nói `no_shell_channel` ngay thì người đo biết phải đi sửa cái gì.
     */
    private fun runDiag(app: Context, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        if (!hooks.shellUsable()) {
            reply.fail(ERR_NO_SHELL)
            return
        }
        Thread({
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            runCatching { ClusterDiag.capture(app, BuildConfig.APPLICATION_ID, NO_VD, stamp) }
                .onSuccess { (path, summary) -> reply.ok("file" to path, "summary" to summary) }
                .onFailure { t ->
                    Log.w(TAG, "ClusterDiag nem: ${t.javaClass.simpleName}", t)
                    reply.fail(ERR_THREW, "exception" to t.javaClass.simpleName)
                }
        }, "KachiTestDiag").start()
    }

    internal companion object {

        const val TAG = TestBridgeReply.TAG

        /**
         * Hậu tố của action. Tên đầy đủ dựng từ [BuildConfig.APPLICATION_ID] và manifest dùng
         * `${'$'}{applicationId}` — MỘT nguồn cho cả hai, nên đổi tên gói không làm cầu câm lặng.
         */
        const val ACTION_SUFFIX = ".TEST"

        fun action(): String = BuildConfig.APPLICATION_ID + ACTION_SUFFIX

        /** Trần một lượt chạy. Dưới hẳn trần 60 s của hàng đợi broadcast nền — hết giờ phải là LỜI ĐÁP, không phải im. */
        const val CAP_MS = 20_000L

        /** Nhịp nán lại chờ câu trả lời về muộn của `say`. */
        const val GRACE_MS = 700L

        /** Trần tệp WAV nhận qua `--es path` (16 MB ≈ 8 phút PCM16 16 kHz — dài hơn mọi câu lệnh). */
        const val MAX_WAV_BYTES = 16L * 1024L * 1024L

        /** `ClusterDiag` nhận cờ RAM "đang chiếu ở VD nào"; cầu này không chiếu gì ⇒ để nó TỰ ĐO (xem KDoc ClusterDiag). */
        const val NO_VD = -1

        private const val TAG_CAP = 24
        private const val UNNAMED = "cmd"

        private val STRING_EXTRAS = listOf(
            TestBridgeCommands.EXTRA_CMD,
            TestBridgeCommands.EXTRA_TEXT,
            TestBridgeCommands.EXTRA_PATH,
            TestBridgeCommands.EXTRA_PKG,
            TestBridgeCommands.EXTRA_ARG,
            TestBridgeCommands.EXTRA_FILE,
        )

        // ── Mã lỗi riêng của tầng này (ASCII, không dịch — xem `TestBridgeParse.Err`) ────────────
        const val ERR_TEST_MODE_OFF = "test_mode_off"
        const val ERR_NO_HOME = "home_not_running"
        const val ERR_BAD_ACTION = "bad_action"
        const val ERR_TIMEOUT = "timeout"
        const val ERR_THREW = "threw"
        const val ERR_NO_SHELL = "no_shell_channel"
        const val ERR_UNKNOWN_PROFILE = "unknown_profile"

        /** `--es pkg` không phải một app đang cài — xem [installed]. */
        const val ERR_UNKNOWN_PKG = "unknown_pkg"
        const val ERR_UNKNOWN_PRESET = "unknown_preset"
        const val ERR_SLOT_RANGE = "slot_out_of_range"
        const val ERR_BAD_PATH = "bad_path"
        const val ERR_WAV_NOT_FOUND = "wav_not_found"
        const val ERR_WAV_TOO_BIG = "wav_too_big"
        const val ERR_WAV_COPY = "wav_copy_failed"

        /** Câu chỉ đường cho người đo — ASCII, cố ý KHÔNG dịch (nó là một lệnh để gõ, không phải chữ trên màn). */
        const val NOTE_LISTEN = "adb logcat -s KachiVoice"
    }
}
