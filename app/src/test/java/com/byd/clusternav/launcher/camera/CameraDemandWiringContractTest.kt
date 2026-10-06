package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · CAMERA-ON-DEMAND — bài canh DÂY NỐI của `:app` (spec `docs/specs/kachi-293-cam.html` R1 · §4.4) ═════════════
 *
 * Luật (toggle · thay · tắt · *"sự kiện mới nhất thắng"*) đã có bài chạy thật ở `:core` (`CameraDemandTest`,
 * `CameraDemandStateTest`). Ở đây canh những mắt xích mà gỡ đi thì build vẫn xanh và không bài `:core` nào đỏ (CLAUDE.md
 * §8 — bẫy `CastShell.evictVd`): BỐN lối vào (phím vật lý · nút thanh nút · giọng nói · cầu kiểm thử) đi về MỘT đường thi
 * hành, đường ấy tới đúng controller của tiến trình chính (kể cả từ `:wake`), và controller đưa camera theo yêu cầu qua
 * CÙNG cửa sổ + cùng chuỗi dỡ đã có bài canh của camera xi-nhan — không hẹn giờ tắt nào.
 */
class CameraDemandWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val dispatch by lazy { app("launcher/camera/CameraDemandDispatch.kt") }
    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }

    /** Phím vật lý: rẽ `cam:` TRƯỚC mọi nhánh mở app (tên gói không có `:`), cùng chỗ rẽ `ctl:` của FIX286. */
    @Test fun `phim vat ly - dich cam re truoc moi nhanh mo app`() {
        val launcher = app("modules/voicekey/AssistantLauncher.kt")
        val fn = SourceRoots.body(launcher, "fun launch(ctx: Context, spec: String): Boolean")
        val cam = fn.indexOf("if (CameraDemand.isKey(spec)) return CameraDemandDispatch.fireKey(ctx, spec)")
        assertTrue(cam >= 0, "đích camera phải rẽ sang đường camera chung")
        assertTrue(cam < fn.indexOf("TARGET_KACHI_VOICE") && cam < fn.indexOf("getLaunchIntentForPackage"),
            "rẽ SAU nhánh mở app ⇒ `cam:rear` bị coi là tên gói và chết im lặng")
        val key = SourceRoots.body(dispatch, "fun fireKey(ctx: Context, spec: String): Boolean")
        assertTrue("CameraDemand.parseKey(spec)" in key && "return false" in key, "mã hỏng ⇒ false (phím đi tiếp như chưa gán)")
    }

    /** Nút thanh nút: nhánh còn lại của `controlDock` → `tap`, và `tap` bỏ qua mã không phải camera. */
    @Test fun `nut thanh nut - mot duong, ma la khong lam gi`() {
        val dock = SourceRoots.body(app("launcher/KachiHomeWiring.kt"), "internal fun Activity.controlDock(")
        assertTrue("CameraDemandDispatch.tap(this@controlDock, id)" in dock, "ô camera trên thanh nút phải nối vào đường chung")
        val tap = SourceRoots.body(dispatch, "fun tap(ctx: Context, launcherId: String): Boolean")
        assertTrue(tap.indexOf("if (!LauncherActions.isCamera(launcherId)) return false") in 0 until tap.indexOf("fire("),
            "mã launcher lạ ⇒ không làm gì (mở nhầm còn tệ hơn)")
        assertTrue("CameraDemand.tap(LauncherActions.cameraOf(launcherId))" in tap, "chạm = bật/tắt; *Tắt camera* = tắt hết (`:core`)")
    }

    /**
     * Giọng nói: câu MỞ = bật/tắt, câu TẮT = tắt, *"tắt cam"* = tắt mọi camera — phép dịch + câu trả lời ở `:core`
     * (`LauncherActions.cameraOp` · `VoiceCameraTurn`, bài `VoiceCameraTurnTest`). wave 2B · D1: vế camera CHỜ kết quả thật
     * của controller (giữ `next` của làn ghi), và *"tắt camera"* trần mà không có gì để tắt CHẠY LẠI vế thành nút Camera 360
     * qua ĐÚNG cổng của `runFrom` (hỏi lại + làn ghi) — không bao giờ bắn lệnh xe từ receiver camera.
     */
    @Test fun `giong noi - cho ket qua that, khong co gi de tat thi chay lai qua cong runFrom`() {
        val vd = app("launcher/VoiceDispatcher.kt")
        val run = SourceRoots.body(vd, "private fun runLauncher(i: VoiceIntent.Launcher, next: () -> Unit, rerun: (VoiceIntent) -> Unit)")
        assertTrue("else -> { VoiceCameraTurn.run(i, lang, onCamera, onUi, say, next, rerun); return }" in run,
            "nhánh camera giao `next` + `rerun` cho `:core` và KHÔNG tự gọi `next` (kết quả về sau mới đi tiếp)")
        assertTrue("private val onCamera: (CameraDemand.Op, (CameraDemand.Outcome) -> Unit) -> Unit = { _, done -> done(CameraDemand.Outcome.UNREACHABLE) }" in vd,
            "mặc định (test/bề mặt khác) = KHÔNG tới được ⇒ *chưa làm được*, không ✓ giả")
        val from = SourceRoots.body(vd, "private fun runFrom(")
        assertTrue("val rerun = { sub: VoiceIntent -> runFrom(intents.toMutableList().also { it[from] = sub }, from, labels, done, settled) }" in from,
            "vế thay thế đi lại TỪ ĐẦU cổng của chính vị trí này (VoiceRiskTable · làn ghi), không gọi thẳng nút xe")
        assertTrue("{ run(intent, labels, next, rerun) }" in from &&
            from.trimEnd().removeSuffix("}").trimEnd().endsWith("run(intent, labels, next, rerun)"),
            "cả nhánh hỏi lại lẫn nhánh thường đều chuyển `rerun` xuống")
        val wiring = app("launcher/voice/VoiceWiring.kt")
        assertTrue("onCamera = { op, done -> com.byd.clusternav.launcher.camera.CameraDemandDispatch.fireForResult(ctx, op, done) }" in wiring,
            "bộ dây giọng nói (chính + `:wake`) phải nối đúng đường thi hành chung, CÓ kết quả")
    }

    /**
     * Cầu `:wake` → chính: chỉ trong gói, receiver KHÔNG export, đăng ký MỘT lần ở tiến trình chính, trước dòng chốt cuối.
     * wave 2B · D1: broadcast CÓ THỨ TỰ, receiver đặt `resultCode` = kết quả thật; không ai trả lời ⇒ mã khởi đầu = không tới
     * được; hạn chờ ngắn hơn lưới an toàn của lượt nói; tiến trình chính chết ⇒ luật `:core` `withoutMain` (TẮT = không có gì).
     * wave 2C · CAM-WAKE-COLD-MAIN: không ai trả lời ở lượt đầu ⇒ gửi lại MỘT lần trong cùng hạn (`:core` `CameraWakeAsk`).
     */
    @Test fun `cau wake sang tien trinh chinh - trong goi, khong export, co thu tu, doc resultCode`() {
        val fire = SourceRoots.body(dispatch, "fun fireForResult(ctx: Context, op: CameraDemand.Op, onResult: (Outcome) -> Unit)")
        val main = fire.indexOf("VoiceGrammarSnapshotStore.isMainProcess(app)")
        val alive = fire.indexOf("VoiceWakeService.isMainProcessAlive(app)")
        val send = fire.indexOf("sendForResult(app, op, onResult)")
        assertTrue(main in 0 until alive && alive < send, "tiến trình chính áp thẳng; tiến trình khác chỉ gửi khi chính còn sống")
        assertTrue("onMain { onResult(applyHere(app, op)) }" in fire, "tiến trình chính: áp trên luồng chính rồi báo kết quả")
        assertTrue("CameraDemand.withoutMain(op)" in fire, "chính không sống ⇒ luật `:core` (TẮT = không có gì để tắt)")
        val apply = SourceRoots.body(dispatch, "private fun applyHere(app: Context, op: CameraDemand.Op): Outcome")
        assertTrue("AppContainer.get(app).cameraSignal" in apply, "đúng controller của tiến trình")
        assertTrue(apply.indexOf("val before = c.demanded()") in 0 until apply.indexOf("c.demand(op)") &&
            "CameraDemand.outcome(op, before, c.demanded())" in apply, "kết quả đọc TRƯỚC/SAU đúng một lượt áp")
        // `demand` trên luồng chính chạy ĐỒNG BỘ — tiền đề của phép đọc trước/sau.
        assertTrue(SourceRoots.body(controller, "fun demand(op: CameraDemand.Op)").trimStart().removePrefix("{").trimStart()
            .startsWith("if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {"), "chỉ luồng khác mới bị đẩy về main")
        // 2.93 wave 2C · CAM-WAKE-COLD-MAIN — lượt hỏi (hạn chờ · đúng một lần · mã khởi đầu ⇒ gửi lại MỘT lần) dời về `:core`
        // `CameraWakeAsk` (bài `CameraWakeAskTest` CHẠY THẬT: hẹn hạn trước khi gửi, kết quả về muộn bị bỏ, gửi hỏng không
        // gửi lại); ở đây canh phần Android: một lượt gửi = MỘT broadcast có thứ tự trong gói, mã thô đi thẳng sang `:core`.
        val sfr = SourceRoots.body(dispatch, "private fun sendForResult(app: Context, op: CameraDemand.Op, onResult: (Outcome) -> Unit)")
        assertTrue("CameraWakeAsk.run(" in sfr && "totalMs = RESULT_TIMEOUT_MS," in sfr, "hạn TỔNG = hạn chờ cũ (không nới)")
        assertTrue("schedule = { ms, block -> main.postDelayed({ block() }, ms) }," in sfr, "hẹn giờ trên luồng CHÍNH (cùng luồng kết quả về)")
        assertTrue("mainAlive = { VoiceWakeService.isMainProcessAlive(app) }," in sfr, "lượt gửi lại hỏi lại chính còn sống không")
        assertTrue("send = { reply -> sendOnce(app, code, deadline, reply) }," in sfr && "onResult = onResult," in sfr, "nối đúng một lượt gửi + kết quả")
        // Soát senior wave 2B/2C [P2] — lượt gửi mang HẠN TỔNG (đồng hồ khởi động, chung mọi tiến trình); receiver tới sau hạn bỏ.
        assertTrue("val deadline = SystemClock.elapsedRealtime() + RESULT_TIMEOUT_MS" in sfr, "hạn của receiver = hạn tổng bên gửi")
        val once = SourceRoots.body(dispatch, "private fun sendOnce(app: Context, code: String, deadline: Long, reply: (Int?) -> Unit)")
        assertTrue(".putExtra(EXTRA_DEADLINE, deadline)" in once, "lệnh mang hạn sang tiến trình chính")
        assertTrue("app.sendOrderedBroadcast(" in once && "sendBroadcast(" !in once.replace("sendOrderedBroadcast(", ""), "CÓ THỨ TỰ (đọc resultCode)")
        assertTrue(".setPackage(app.packageName)" in once, "broadcast chỉ trong gói")
        assertTrue("Intent.FLAG_RECEIVER_FOREGROUND" in once, "hàng đợi tiền cảnh — không xếp sau broadcast nền của app khác")
        assertTrue("Outcome.UNREACHABLE.code," in once, "mã khởi đầu = không tới được (không receiver nào trả lời)")
        assertTrue("reply(resultCode)" in once, "kết quả = mã receiver đặt (`:core` `CameraDemand.outcomeOf` quy đổi)")
        assertTrue("catch (e: RuntimeException)" in once && "reply(null)" in once, "gửi hỏng ⇒ không tới được, không ném vào phiên giọng nói")
        assertFalse("AtomicBoolean" in sfr || "postDelayed(timeout" in sfr, "không còn bản thứ hai của luật một-lần/hạn chờ ở `:app`")
        val timeout = Regex("""const val RESULT_TIMEOUT_MS = ([\d_]+)L""").find(dispatch)!!.groupValues[1].replace("_", "").toLong()
        val settle = Regex("""const val TURN_SETTLE_MS = ([\d_]+)L""").find(app("launcher/voice/VoiceSession.kt"))!!.groupValues[1].replace("_", "").toLong()
        assertTrue(timeout * 2 <= settle, "hạn chờ ($timeout ms) phải để chỗ cho đường Camera 360 trước lưới an toàn lượt nói ($settle ms)")
        val reg = SourceRoots.body(dispatch, "fun receiveInMain(ctx: Context)")
        assertTrue("ContextCompat.RECEIVER_NOT_EXPORTED" in reg, "app khác không được bật camera của Kachi")
        assertTrue("ContextCompat.registerReceiver(app, Receiver(), IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)" in reg,
            "không truyền Handler ⇒ onReceive chạy trên LUỒNG CHÍNH (điều `applyHere` cần)")
        val recv = SourceRoots.body(dispatch, "private class Receiver : BroadcastReceiver() {")
        assertTrue("CameraDemand.decode(" in recv && "if (op == null)" in recv, "lệnh lạ ⇒ bỏ + log, không đoán")
        assertTrue(recv.indexOf("applyHere(") in 0 until recv.indexOf("if (isOrderedBroadcast) resultCode = o.code"),
            "receiver ÁP rồi mới trả mã — mã nói điều đã xảy ra")
        // Soát senior wave 2B/2C [P2] — lệnh tới SAU hạn bên gửi (câu ✗ đã nói) bị bỏ TRƯỚC khi áp: lệnh MỞ muộn là "mới nhất"
        // và sẽ đè camera xi-nhan bật trong lúc chờ. Luật thuần + bài chạy thật: `:core` `CameraWakeAskTest.lenh toi receiver…`.
        assertTrue(recv.indexOf("CameraWakeAsk.expired(deadline, SystemClock.elapsedRealtime())") in 0 until recv.indexOf("applyHere("),
            "kiểm hạn TRƯỚC khi áp (bỏ ⇒ giữ mã khởi đầu, không áp gì)")
        assertFalse("carControl" in dispatch || "actByKind" in dispatch, "lệnh nút xe (Camera 360) KHÔNG bao giờ bắn từ đường camera")
        val appOnCreate = SourceRoots.body(app("KachiApplication.kt"), "override fun onCreate()")
        val relay = appOnCreate.indexOf("ControlSentRelay.receiveInMain(this)")
        val cam = appOnCreate.indexOf("CameraDemandDispatch.receiveInMain(this)")
        val last = appOnCreate.indexOf("EarlyShellChannel.start(this)")
        assertTrue(relay in 0 until cam && cam < last, "đăng ký sau cổng tiến trình nền (cùng chỗ ControlSentRelay), trước dòng chốt cuối")
    }

    /** Cầu kiểm thử (QA máy ảo): `demand:/open:/close:` đi ĐÚNG đường chung; đối số cũ (`left/right/none`) giữ nghĩa cũ. */
    @Test fun `cau kiem thu - cung duong chung, lenh cu giu nghia, state doc lai`() {
        val cmd = SourceRoots.body(app("launcher/testbridge/TestBridgeCamera.kt"), "fun run(")
        val parse = cmd.indexOf("CameraDemand.parseBridge(a)")
        assertTrue(parse in 0 until cmd.indexOf("hooks.cameraTick("), "lệnh theo yêu cầu rẽ TRƯỚC đường xi-nhan giả")
        assertTrue("CameraDemandDispatch.fireForResult(app, op)" in cmd, "không đường thứ hai cho QA")
        assertTrue("\"outcome\" to o.name.lowercase()" in cmd, "wave 2B: lời đáp mang CÙNG kết quả mà câu trả lời giọng nói đọc")
        val bridge = app("launcher/testbridge/KachiTestBridge.kt")
        assertTrue("TestBridgeCommands.CAMERA -> TestBridgeCamera.run(app, cmd, hooks, reply)" in bridge)
        val state = app("launcher/testbridge/TestBridgeState.kt")
        assertTrue("\"camera\" to TestBridgeJson.Raw(cameraDemand(ctx))" in state, "`state` phải đọc lại demand/showing")
        val read = SourceRoots.body(state, "private fun cameraDemand(ctx: Context): String")
        assertTrue("if (c.cameraSignalCreated) c.cameraSignal else null" in read, "một lệnh đo không được DỰNG controller")
    }

    /**
     * Controller: lệnh theo yêu cầu đi qua CÙNG cửa `show` (một cửa sổ · một AVMCamera · chuỗi dỡ có bài canh), về main,
     * báo người nghe; KHÔNG một hẹn giờ nào (owner *"không nên timeout"*); tắt công tắc xi-nhan không tắt camera theo yêu cầu.
     */
    @Test fun `controller - mot cua show, khong hen gio, hai nguon doc lap`() {
        val demand = SourceRoots.body(controller, "fun demand(op: CameraDemand.Op)")
        assertTrue("main.post { demand(op) }; return" in demand, "phím ở luồng dịch vụ, cầu ở luồng broadcast ⇒ về main")
        // Nút = toggle theo thứ ĐANG HIỆN: controller phải đưa camera của phiên đang treo vào luật `:core`.
        val apply = demand.indexOf("od.apply(op, visible = showing)")
        val show = demand.indexOf("show(want())")
        val notify = demand.indexOf("od.notifyChanged()")
        assertTrue(apply in 0 until show && show < notify, "áp lệnh → đưa cửa sổ về camera nên hiện → báo nút Xem thử")
        listOf("postDelayed", "removeCallbacks", "Timer", "schedule").forEach {
            assertFalse(it in demand, "camera theo yêu cầu KHÔNG có hẹn giờ tắt: `$it`")
        }
        assertEquals(1, Regex("""main\.postDelayed\(""").findAll(controller).count(), "hẹn giờ DUY NHẤT là mốc HOLD của xi-nhan")
        val tick = SourceRoots.body(controller, "private fun tickMain(")
        assertTrue("if (!Prefs.cameraSignalEnabled(appCtx)) { if (current != Turn.NONE) endBlinker(); return }" in tick,
            "công tắc xi-nhan TẮT chỉ hạ phần xi-nhan — camera theo yêu cầu là nguồn độc lập")
        assertTrue("od.newer = false" in tick, "xi-nhan bật/đổi bên = sự kiện mới nhất ⇒ camera xi-nhan hiện như cũ")
        assertTrue("od.shown(CameraWhich.ofTurn(current))" in SourceRoots.body(controller, "private fun want("), "luật chọn ở `:core`")
        val end = SourceRoots.body(controller, "private fun endBlinker(")
        assertTrue("dropBlinker()" in end && "show(want())" in end, "hết xi-nhan ⇒ camera theo yêu cầu (nếu bật) quay lại")
        // Xem thử khi công tắc xi-nhan TẮT không được biến camera theo yêu cầu thành "bật mà không hiện" (soát 2.93).
        val preview = SourceRoots.body(controller, "fun previewSide(left: Boolean)")
        assertTrue(preview.indexOf("tickMain(left = left, right = !left)") < preview.indexOf("if (showing == null) show(want())"))
        // Camera đổi nguồn ảnh (`camera_synth`) giữa lúc camera theo yêu cầu đang hiện ⇒ dựng lại NGAY.
        assertTrue("rebuild()" in SourceRoots.body(controller, "fun setSynth("))
    }

    /** Nhãn overlay của bốn camera — đọc từ tài nguyên theo ngôn ngữ của launcher, không chữ cứng. */
    @Test fun `nhan overlay du bon camera`() {
        val mask = SourceRoots.body(app("launcher/camera/CameraOverlayMask.kt"), "internal fun labelFor(ctx: Context, which: CameraWhich?)")
        listOf("REAR" to "kachi_camera_rear", "LEFT" to "kachi_camera_left", "RIGHT" to "kachi_camera_right", "FRONT" to "kachi_camera_front")
            .forEach { (w, res) -> assertTrue("CameraWhich.$w -> com.byd.clusternav.R.string.$res" in mask, "nhãn $w") }
        assertTrue("which = which," in SourceRoots.body(controller, "private fun openSession("), "controller nói camera nào cho overlay")
    }
}
