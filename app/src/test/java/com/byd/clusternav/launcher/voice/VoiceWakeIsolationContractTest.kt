package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ "HEY KACHI" PHẢI Ở TIẾN TRÌNH RIÊNG `:wake` + ĐƯỜNG TẢI MODEL PHẢI CÓ THẬT ════════════════════════════════
 *
 * Em sinh đôi của [VoiceTtsIsolationContractTest], cùng một lẽ và cùng một cách kiểm. Bộ nghe câu gọi cầm
 * `KeywordSpotter` — **cùng** `libonnxruntime.so` mà [ĐO tombstone xe 2026-09-18] đã một lần SIGSEGV trong
 * `OfflineTts.generate` và giết CẢ tiến trình launcher (⇒ a11y unbind ⇒ phím gán chết,
 * `docs/diagnostics/oncar-piper-crash-binding-2026-09-18.md`).
 *
 * ## Vì sao bài canh này quét SOURCE + MANIFEST chứ không chạy hành vi
 * `android:process` là thuộc tính do **nền tảng** thi hành lúc cài; `startForegroundService`/`AudioRecord`/
 * `Handler.postDelayed` cần một hệ Android thật (dự án không dùng Robolectric). Thứ *kiểm được* off-car là **cấu
 * hình** + **đường dây** — và đó cũng đúng là hai thứ một lượt refactor sau này gỡ mất trong im lặng: bỏ một dòng
 * manifest là kéo engine native về lại tiến trình launcher, compile vẫn xanh, và **chỉ chiếc xe** biết.
 *
 * Quét đi qua `SourceRoots.codeOf` (đã bỏ chú thích) nên một nhắc-tới trong KDoc không thoả hợp đồng, và qua
 * `SourceRoots.body` (nổ nếu mốc không còn) để không có vùng quét tràn.
 */
class VoiceWakeIsolationContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val service by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeService.kt") }
    private val listener by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeListener.kt") }
    private val application by lazy { code("src/main/java/com/byd/clusternav/KachiApplication.kt") }
    private val bridge by lazy { code("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeWake.kt") }
    private val prefs by lazy { code("src/main/java/com/byd/clusternav/Prefs.kt") }
    private val manifest by lazy { SourceRoots.text("src/main/AndroidManifest.xml") }

    // ══ (1) Manifest: tiến trình `:wake` tồn tại, và khối giữ ĐƠN GIẢN ═══════════════════════════════════

    @Test
    fun `manifest khai VoiceWakeService o tien trinh wake va khong exported`() {
        val at = manifest.indexOf(".launcher.voice.VoiceWakeService")
        assertTrue(at > 0, "AndroidManifest phải khai báo VoiceWakeService")
        val block = manifest.substring(at, manifest.indexOf("</service>", at))
        assertTrue(
            block.contains("android:process=\":wake\""),
            "VoiceWakeService PHẢI chạy ở tiến trình riêng `:wake` — thiếu thuộc tính này là bản vá không tồn tại " +
                "(mã vẫn compile, wake vẫn nghe, nhưng KWS nổ lại giết launcher + phím gán như 1.76)",
        )
        assertTrue(block.contains("android:exported=\"false\""), "service nội bộ — không được exported")
        assertTrue(
            block.contains("android:foregroundServiceType=\"microphone\""),
            "FGS thu mic nền phải khai type `microphone` (API 29 có hằng; API 30+ bắt buộc để được thu nền)",
        )
        // Bẫy [ĐO xe 2026-09-15]: packageinstaller Android 10 không hiểu element lạ dưới <service> ⇒ trình cài
        // GUI báo "Fail in installation". Giữ khối này ĐƠN GIẢN.
        assertFalse(block.contains("<property"), "KHÔNG thêm <property> (Android 10 packageinstaller không hiểu)")
        assertFalse(
            block.contains("<intent-filter"),
            "service này chỉ được start tường minh qua sync() — không cần intent-filter",
        )
    }

    /** Hằng hậu tố tiến trình khai MỘT chỗ; manifest và mã so bằng chính nó, không chép chuỗi lần thứ hai. */
    @Test
    fun `hau to tien trinh khai mot cho va duoc dung that`() {
        assertTrue(
            service.contains("const val PROCESS_SUFFIX = \":wake\""),
            "VoiceWakeService phải khai PROCESS_SUFFIX = \":wake\" (nguồn duy nhất cho manifest + KachiApplication)",
        )
        assertTrue(
            application.contains("VoiceWakeService.PROCESS_SUFFIX"),
            "KachiApplication phải so tên tiến trình bằng chính hằng đó, không viết \":wake\" lần thứ hai",
        )
    }

    /**
     * **Cả hai tiến trình voice nền KHÔNG được nạp mô hình NGHE / dựng đồ thị DI của launcher.**
     *
     * `Application.onCreate` chạy ở MỌI tiến trình. Không có cổng này thì `:wake` cũng gọi `VoiceEngine.preload`
     * (74 MB int8) + `VoiceVad.preload` + `AppContainer`: vô ích (nó chỉ cần KWS ~5 MB, và **tự** nạp cái đó), và
     * **đúng thứ gây ra lỗi đang vá** — SIGSEGV là `SEGV_MAPERR` dưới áp lực RAM. Cô lập tiến trình mà nhân ba
     * RAM là cô lập hỏng.
     */
    @Test
    fun `hai tien trinh voice nen khong nap mo hinh nghe`() {
        val fn = SourceRoots.body(application, "override fun onCreate()")
        assertTrue(
            fn.contains("if (isBackgroundVoiceProcess()) return"),
            "phải thoát sớm khi đang ở tiến trình voice nền (`:tts` hoặc `:wake`)",
        )
        val gate = fn.indexOf("isBackgroundVoiceProcess()")
        listOf("AppContainer.get(this)", "VoiceEngine.preload(this)", "VoiceVad.preload(this)").forEach {
            val at = fn.indexOf(it)
            assertTrue(at > 0, "vẫn phải giữ `$it` cho tiến trình launcher")
            assertTrue(at > gate, "`$it` phải nằm SAU cổng `isBackgroundVoiceProcess()` — nếu không tiến trình nền cũng nạp nó")
        }
        // Cổng phải nhận CẢ HAI hậu tố: chặn một cái thôi là nửa bản vá, và nửa thiếu im lặng.
        val cond = SourceRoots.body(application, "private fun isBackgroundVoiceProcess()")
        listOf("PiperTtsService.PROCESS_SUFFIX", "VoiceWakeService.PROCESS_SUFFIX").forEach {
            assertTrue(cond.contains(it), "cổng phải so với `$it`")
        }
    }

    /**
     * `:wake` phải **tự lo được** — không chạm [com.byd.clusternav.AppContainer] (đồ thị DI của launcher: dadb,
     * cast runtime, nav repository).
     *
     * Đây là điều kiện để cổng ở [KachiApplication] là ĐÚNG chứ chỉ là tiết kiệm RAM: nếu một trong ba tệp wake
     * gọi `AppContainer.get(...)`, tiến trình `:wake` sẽ dựng một đồ thị **thứ hai** (một `ShellTransport` thứ hai
     * mở thêm một kết nối dadb `localhost:5555` — đúng nghi phạm của BUG2 "adb wireless chết"), hoặc ngã vì
     * container chưa được khởi tạo trong tiến trình đó.
     */
    @Test
    fun `ba tep wake khong cham do thi DI cua launcher`() {
        val kws = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeKws.kt")
        mapOf("VoiceWakeService.kt" to service, "VoiceWakeListener.kt" to listener, "VoiceWakeKws.kt" to kws)
            .forEach { (name, src) ->
                listOf("AppContainer", "ShellTransport", "WindowCommandDispatcher", "NavRepository", "SimpleCastRuntime")
                    .forEach { banned ->
                        assertFalse(
                            src.contains(banned),
                            "$name chạm `$banned` — tiến trình `:wake` sẽ dựng đồ thị DI thứ hai (hoặc ngã vì chưa có)",
                        )
                    }
            }
    }

    // ══ (1b) [P2] Tiến trình `:wake` KHÔNG ghi tệp prefs CHUNG ═════════════════════════════════════════════

    /**
     * ⚠ [P2 2026-09-19] Cầu chì tự-tắt của `:wake` phải đi qua tệp RIÊNG, KHÔNG ghi `clusternav_prefs`.
     *
     * `voice_wake_enabled` nằm trong `clusternav_prefs` — tệp CHUNG 38 key = mọi setting launcher (biển tốc độ,
     * ghế, phím thoại, chế độ nav…). MODE_PRIVATE **không** an toàn đa-tiến-trình: nếu `:wake` `apply()` tệp này
     * bằng map-RAM cũ của nó (nạp lúc `:wake` khởi động), nó ghi đè CẢ tệp ⇒ **xoá mọi setting launcher đã đổi
     * kể từ đó**. Đây là bản vá KHÔNG nhìn thấy được off-car nếu không chốt bằng chính đường ghi.
     */
    @Test
    fun `wake khong ghi tep prefs chung, chi ghi tep rieng`() {
        // (1) auto-disable ghi cờ tệp RIÊNG, và `:wake` TUYỆT ĐỐI không gọi đường ghi clusternav_prefs.
        val fn = SourceRoots.body(service, "private fun autoDisable()")
        assertTrue(
            fn.contains("Prefs.setWakeServiceDisabled(this, true)"),
            "auto-disable phải ghi cờ tệp RIÊNG (setWakeServiceDisabled), không tắt công tắc chung",
        )
        // ⚠ [SOÁT 2026-09-19] Guard phải chặn NGUYÊN NHÂN, không chặn MỘT CÁI TÊN. `Prefs` có ~30 setter ghi
        // `clusternav_prefs`, **và hai READER tự GHI** khi migrate (`badgeCenterX/Y` → `migrateBadgeIfNeeded`;
        // `voiceKeyBindings`/`voiceKeyTargetSpec` → `migrateLegacy`). Một guard chỉ soi `setWakeEnabled` vẫn XANH
        // khi ai đó thêm `Prefs.setWakePhraseId(...)` hay `Prefs.badgeCenterX(...)` vào `:wake` — tức đúng họ lỗi
        // "lá chắn mang hình dạng lá chắn" của dự án, và cái clobber nó để lọt thì **im lặng**.
        val kwsSrc = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWakeKws.kt")
        val allowedWrites = setOf("Prefs.setWakeServiceDisabled")
        val readersThatWrite = listOf(
            "Prefs.badgeCenterX", "Prefs.badgeCenterY", "Prefs.voiceKeyBindings", "Prefs.voiceKeyTargetSpec",
        )
        mapOf("VoiceWakeService.kt" to service, "VoiceWakeListener.kt" to listener, "VoiceWakeKws.kt" to kwsSrc)
            .forEach { (name, src) ->
                Regex("""Prefs\.set[A-Za-z0-9_]*""").findAll(src).map { it.value }.toSet().forEach { call ->
                    assertTrue(
                        call in allowedWrites,
                        "$name gọi `$call` — mọi setter ngoài setWakeServiceDisabled ghi `clusternav_prefs` (38 key) " +
                            "bằng map-RAM cũ của tiến trình `:wake` ⇒ apply() ghi đè CẢ tệp ⇒ xoá mọi setting launcher",
                    )
                }
                readersThatWrite.forEach { reader ->
                    assertFalse(
                        src.contains(reader),
                        "$name gọi `$reader` — READER này tự GHI `clusternav_prefs` khi migrate ⇒ cùng một clobber",
                    )
                }
            }
        // (2) [SOÁT 2026-09-19] Cầu chì = MARKER FILE (`kachi_wake_disabled`), KHÔNG phải một tệp prefs thứ hai:
        //     `SharedPreferences` cache map per-process ⇒ launcher không thấy `:wake` ghi (công tắc hiện ON dù đã
        //     tắt). `File.exists()` đọc thẳng hệ tệp ⇒ launcher thấy NGAY. Vẫn tách khỏi `clusternav_prefs`.
        assertTrue(prefs.contains("\"kachi_wake_disabled\""), "cầu chì phải là marker file kachi_wake_disabled")
        assertTrue(
            prefs.contains("java.io.File(ctx.applicationContext.filesDir, \"kachi_wake_disabled\")"),
            "marker phải nằm trong filesDir, tách khỏi clusternav_prefs",
        )
        assertTrue(
            prefs.contains("wakeDisabledMarker(ctx).exists()"),
            "wakeServiceDisabled phải đọc bằng File.exists() — KHÔNG cache per-process như SharedPreferences",
        )
        assertFalse(
            prefs.contains("getSharedPreferences(\"kachi_wake_state\""),
            "KHÔNG dùng tệp prefs cho cầu chì — sẽ tái lập lỗi cache cross-process (F2)",
        )
        // (3) wakeEnabled = owner-intent (clusternav_prefs) AND KHÔNG service-tự-tắt (tệp riêng).
        assertTrue(
            prefs.contains("getBoolean(\"voice_wake_enabled\", false) && !wakeServiceDisabled(ctx)"),
            "wakeEnabled phải kết hợp owner-intent AND !service_disabled",
        )
        // (4) owner BẬT lại ⇒ xoá cờ tự-tắt (nếu không, service_disabled còn thì bật lại vô hiệu, im lặng).
        assertTrue(
            prefs.contains("if (on) setWakeServiceDisabled(ctx, false)"),
            "setWakeEnabled(on=true) phải xoá cờ tự-tắt của service để bật lại có hiệu lực",
        )
    }

    // ══ (2) Nhường micro cho phiên lệnh — cross-process thì phải theo THỜI GIAN ═══════════════════════════

    /**
     * **Thứ tự trong [fireWake] là hợp đồng**: nhả mic TRƯỚC `startActivity`, hẹn nghe lại SAU.
     *
     * Từ lượt tách `:wake`, `VoiceSingleFlight` có **hai bản** (mỗi tiến trình một) và chúng không thấy nhau ⇒
     * cái chốt một-mic không còn bắc qua ranh giới. Nếu `:wake` giữ `AudioRecord` khi phiên lệnh mở ra thì hai
     * bên giành phần cứng, và triệu chứng là *"gọi được nhưng nó không nghe mình nói gì"*.
     */
    @Test
    fun `fireWake nha mic truoc roi moi mo phien lenh`() {
        val fn = SourceRoots.body(service, "private fun fireWake()")
        val release = fn.indexOf("listener?.setListening(false)")
        val start = fn.indexOf("startActivity(")
        assertTrue(release > 0, "fireWake phải nhả micro (`setListening(false)`) — xem KDoc tại chỗ")
        assertTrue(start > 0, "fireWake vẫn phải mở phiên nghe lệnh")
        assertTrue(
            release < start,
            "nhả micro phải nằm TRƯỚC startActivity — mở phiên lệnh trong lúc `:wake` còn giữ mic là hai mic",
        )
    }

    /**
     * …và phải **hẹn nghe lại vô điều kiện**, vì `fireWake` có thể bị nền tảng chặn **im lặng**.
     *
     * Một cổng chờ tín hiệu "phiên lệnh xong" từ tiến trình kia là một cổng có thể không bao giờ mở — và khi ấy
     * "Hey Kachi" chết im tới lần nổ máy sau. Hẹn giờ thì xấu hơn về lý thuyết nhưng **không chết**.
     */
    @Test
    fun `fireWake hen nghe lai theo thoi gian, khong cho tin hieu`() {
        val fn = SourceRoots.body(service, "private fun fireWake()")
        assertTrue(fn.contains("main.removeCallbacks(resumeTask)"), "phải huỷ hẹn cũ trước khi hẹn mới (không xếp đống)")
        assertTrue(
            fn.contains("main.postDelayed(resumeTask, WAKE_HANDOFF_MS)"),
            "phải hẹn nghe lại sau WAKE_HANDOFF_MS — mốc THỜI GIAN, không IPC",
        )
        assertTrue(service.contains("const val WAKE_HANDOFF_MS = 18_000L"), "hằng handoff phải khai tường minh")
        // Việc nghe lại phải tự kiểm hai cổng của chính service — công tắc có thể đã tắt, màn có thể đã tối.
        val resume = SourceRoots.body(service, "private val resumeTask = Runnable")
        assertTrue(resume.contains("enabled()"), "nghe lại phải kiểm công tắc (người dùng có thể đã tắt trong 18 s)")
        assertTrue(resume.contains("screenOn()"), "nghe lại phải kiểm màn sáng (gate màn-sáng vẫn phải đúng)")
    }

    /** Hẹn giờ không được sống lâu hơn service — họ lỗi "đường sống lâu hơn thứ nó phục vụ" của dự án. */
    @Test
    fun `onDestroy huy hen nghe lai`() {
        val fn = SourceRoots.body(service, "override fun onDestroy()")
        assertTrue(fn.contains("main.removeCallbacks(resumeTask)"), "phải huỷ resumeTask khi service chết")
        val cancel = fn.indexOf("main.removeCallbacks(resumeTask)")
        val stop = fn.indexOf("stopListening()")
        assertTrue(cancel in 1 until stop, "huỷ hẹn phải nằm TRƯỚC stopListening (không để nó dựng lại bộ nghe vừa dừng)")
    }

    /**
     * **Lượt nhường micro không được bị hai đường chẳng liên quan huỷ** (soát 2026-09-19).
     *
     * Lượt nhường được biểu diễn bằng `listening = false` — mà cờ ấy cũng là **cổng màn-sáng** và cũng bị
     * `onStartCommand` đặt lại mỗi lần service được start. Nên nếu chỉ có hẹn giờ thì một `SCREEN_ON` (bấm nút
     * nguồn / màn hết giờ rồi được chạm) hay một cú `sync()` bất kỳ (gạt công tắc · boot · tải xong model) sẽ bật
     * nghe lại **giữa lúc phiên lệnh đang ghi** ⇒ hai tiến trình cùng UID cùng mở `AudioRecord` ⇒ đúng triệu
     * chứng mà cả lượt tách `:wake` sinh ra để chặn: *"gọi được nhưng nó không nghe mình nói gì"*.
     *
     * Mốc phải **tự hết hạn** để không tạo ngõ cụt: quá hạn thì mọi đường bật nghe chạy như thường, kể cả khi
     * `resumeTask` đã bỏ lượt vì màn tối đúng lúc hết hạn.
     */
    @Test
    fun `luot nhuong micro khong bi SCREEN_ON hay sync huy giua duong`() {
        val fire = SourceRoots.body(service, "private fun fireWake()")
        assertTrue(
            fire.contains("handoffUntil = SystemClock.elapsedRealtime() + WAKE_HANDOFF_MS"),
            "fireWake phải ghi MỐC nhường, không chỉ hẹn giờ — hẹn giờ một mình không chặn được hai đường kia",
        )
        val screen = SourceRoots.body(service, "private fun onScreen(on: Boolean)")
        assertTrue(
            screen.contains("if (on && handoffActive())"),
            "SCREEN_ON trong lúc đang nhường thì KHÔNG được bật nghe lại (mà SCREEN_OFF vẫn phải thi hành ngay)",
        )
        val cmd = SourceRoots.body(service, "override fun onStartCommand(")
        assertTrue(
            cmd.contains("setListening(screenOn() && !handoffActive())"),
            "một cú sync() giữa lượt nhường không được giành lại mic",
        )
        val resume = SourceRoots.body(service, "private val resumeTask = Runnable")
        assertTrue(
            resume.indexOf("handoffUntil = 0L") in 0 until maxOf(resume.indexOf("enabled()"), 1),
            "phải xoá mốc TRƯỚC khi kiểm hai cổng — màn tối đúng lúc hết hạn không được khoá mốc lại mãi",
        )
        val active = SourceRoots.body(service, "private fun handoffActive()")
        assertTrue(
            active.contains("SystemClock.elapsedRealtime() < handoffUntil"),
            "mốc phải TỰ HẾT HẠN theo thời gian — một cờ boolean thuần là một ngõ cụt chờ xảy ra",
        )
    }

    // ══ (3) Đường tải model KWS phải có thật, và phải ở đúng chỗ ══════════════════════════════════════════

    /**
     * Bật công tắc ⇒ **tải model nếu chưa có**, trên **luồng nền**, và **không chặn** việc bật.
     *
     * `VoiceModelStore.install` chặn (mạng + sha256); gọi trên luồng chính là treo màn Cài đặt / ANR. Còn chặn
     * việc bật lại là mất mất chế độ degrade chỉ-RMS (đo baseline CPU trên xe) mà cả thiết kế wake dựa vào.
     */
    @Test
    fun `bat cong tac thi tai model KWS tren luong nen`() {
        val fn = SourceRoots.body(bridge, "fun ClusterNavBridge.setWakeEnabled(")
        assertTrue(fn.contains("Prefs.setWakeEnabled(app, on)"), "vẫn phải ghi pref qua cầu")
        assertTrue(fn.contains("VoiceWakeService.sync(app)"), "vẫn phải đồng bộ FGS NGAY (không chờ model)")
        assertTrue(fn.contains("if (on)"), "chỉ tải khi BẬT — tắt công tắc không được kéo 5 MB về")
        val fetch = SourceRoots.body(bridge, "fun ensure(app: Context)")
        assertTrue(
            fetch.contains("VoiceModelStore.isReady(app, WakeModelCatalog)"),
            "phải hỏi 'đã có chưa' trước khi tải — ca thường không được dựng luồng nào",
        )
        assertTrue(fetch.contains("compareAndSet(false, true)"), "phải có chốt một-lượt-tải (gạt tắt–bật nhiều lần)")
        assertTrue(fetch.contains("Thread("), "lượt tải phải chạy trên luồng nền riêng")
        assertTrue(fetch.contains("isDaemon = true"), "luồng nền không được giữ tiến trình sống")
    }

    /** Tải xong phải **dựng lại bộ nghe**, nếu không gói vừa về chỉ có tác dụng sau lần nổ máy sau. */
    @Test
    fun `tai xong thi dung lai bo nghe de nap model`() {
        val fn = SourceRoots.body(bridge, "private fun run(app: Context)")
        assertTrue(fn.contains("VoiceModelStore.install(app, WakeModelCatalog)"), "phải gọi đúng đường cài đã có")
        assertTrue(
            fn.contains("VoiceWakeService.sync(app, reloadModel = true)"),
            "tải xong PHẢI dựng lại bộ nghe — luồng nghe chỉ thử nạp model một lần cho cả vòng đời của nó",
        )
        assertTrue(
            fn.contains("Prefs.wakeEnabled(app)"),
            "người dùng có thể đã TẮT trong lúc tải ⇒ phải đọc lại công tắc, không bật bộ nghe sau lưng họ",
        )
        assertTrue(fn.contains("fetching.set(false)"), "chốt phải nhả trong `finally` (không thì tắc vĩnh viễn)")
        // Cờ reload phải được service THI HÀNH, không chỉ nhận rồi bỏ.
        val cmd = SourceRoots.body(service, "override fun onStartCommand(")
        assertTrue(
            cmd.contains("getBooleanExtra(EXTRA_RELOAD, false)") && cmd.contains("stopListening()"),
            "onStartCommand phải thực sự dừng bộ nghe cũ khi có cờ reload",
        )
    }

    /**
     * **Mỗi lần nổ máy phải THỬ LẠI lượt tải** nếu công tắc đang bật mà gói chưa có (soát 2026-09-19).
     *
     * Cú gạt công tắc là lần duy nhất ta biết chắc người dùng muốn tính năng này, nhưng **không** phải lúc chắc
     * chắn có mạng — trên xe thì thường là không (garage, 4G chập chờn). Hỏng một lượt ⇒ degrade chỉ-RMS ⇒
     * "Hey Kachi" bật mà **không bao giờ nhận**, không câu nào nói vì sao, và không đường nào thử lại. Với một
     * lượt crowd-test mà mục đích DUY NHẤT là đo tỉ lệ nhận, đó là khác biệt giữa "đo được" và "không ai chạy
     * được mà không ai biết tại sao".
     */
    @Test
    fun `no may thi thu lai luot tai model neu cong tac dang bat`() {
        val autostart = code("src/main/java/com/byd/clusternav/KachiAutostart.kt")
        assertTrue(
            autostart.contains("ensureWakeModelIfEnabled(app)"),
            "KachiAutostart phải thử lại lượt tải model KWS mỗi lần nổ máy — một cú gạt công tắc là không đủ",
        )
        val fn = SourceRoots.body(bridge, "internal fun ensureWakeModelIfEnabled(app: Context)")
        assertTrue(fn.contains("Prefs.wakeEnabled(app)"), "chỉ thử lại khi công tắc đang BẬT")
        assertTrue(
            fn.contains("WakeModelFetch.ensure(app)"),
            "phải đi qua CÙNG đường tải đã có (chốt một-lượt + luồng nền + bắt Throwable), không dựng đường thứ hai",
        )
    }

    /**
     * Tên thư mục + 5 tên tệp mà engine đọc phải lấy từ **cùng bảng** mà đường tải ghim sha256.
     *
     * Chép tên tệp lần thứ hai là mở đúng cái khe im lặng: gói tải về đủ 5 tệp, engine soi một tên khác,
     * `VoiceWakeKws.build` trả `null`, và "Hey Kachi" chạy mà **không bao giờ nhận** — không log nào nói vì sao.
     */
    @Test
    fun `engine doc model tu chinh bang ghim`() {
        val fn = SourceRoots.body(listener, "private fun defaultKws(ctx: Context)")
        assertTrue(
            fn.contains("File(ctx.filesDir, WakeModelCatalog.dir)"),
            "thư mục model phải lấy từ `WakeModelCatalog.dir`, không viết \"kws\" lần thứ hai",
        )
        listOf("ENCODER", "DECODER", "JOINER", "TOKENS", "KEYWORDS").forEach {
            assertTrue(fn.contains("WakeModelCatalog.$it"), "tên tệp phải lấy từ `WakeModelCatalog.$it`")
        }
    }

    // ══ (4) Nhãn thử nghiệm + trần 500 dòng ══════════════════════════════════════════════════════════════

    /**
     * Công tắc phải **nói ra** rằng đây là bản thử nghiệm, ở CẢ hai ngôn ngữ.
     *
     * Anh em crowd-test bật một tính năng nghe nền: nếu nó nhận kém mà nhãn không nói gì thì họ kết luận "app
     * hỏng" thay vì báo lại — mà báo lại mới là toàn bộ lý do gửi bản này ra.
     */
    @Test
    fun `nhan cong tac noi ro dang thu nghiem o ca hai ngon ngu`() {
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        val viTitle = vi.substringAfter("<string name=\"kachi_wake_title\">").substringBefore("</string>")
        val enTitle = en.substringAfter("<string name=\"kachi_wake_title\">").substringBefore("</string>")
        assertTrue(viTitle.contains("thử nghiệm"), "nhãn VI phải nói \"thử nghiệm\"; thấy: $viTitle")
        assertTrue(enTitle.lowercase().contains("experimental"), "nhãn EN phải nói \"experimental\"; thấy: $enTitle")
        val viSub = vi.substringAfter("<string name=\"kachi_wake_sub\">").substringBefore("</string>")
        val enSub = en.substringAfter("<string name=\"kachi_wake_sub\">").substringBefore("</string>")
        assertTrue(viSub.contains("chưa nhận tốt"), "phụ đề VI phải nói có thể chưa nhận tốt; thấy: $viSub")
        assertTrue(viSub.contains("báo lại"), "…và phải mời báo lại; thấy: $viSub")
        assertTrue(enSub.lowercase().contains("report"), "phụ đề EN phải mời báo lại; thấy: $enSub")
        // Chuỗi phải nằm ở strings_kachi.xml — `strings.xml` đang NIÊM PHONG theo byte (T11).
        assertFalse(
            SourceRoots.text("src/main/res/values/strings.xml").contains("kachi_wake_title"),
            "chuỗi launcher KHÔNG được thêm vào strings.xml (tệp niêm phong T11)",
        )
    }

    /** Trần 500 dòng (CLAUDE.md §4.1) cho các tệp lượt này chạm tới. */
    @Test
    fun `cac tep cua luot nay khong vuot tran 500 dong`() {
        mapOf(
            "VoiceWakeService.kt" to service,
            "VoiceWakeListener.kt" to listener,
            "ClusterNavBridgeWake.kt" to bridge,
            "KachiApplication.kt" to application,
            "WakeModelCatalog.kt" to code("src/main/java/com/byd/clusternav/launcher/voice/WakeModelCatalog.kt"),
        ).forEach { (name, src) ->
            assertTrue(src.lines().size <= 500, "$name dài ${src.lines().size} dòng — trần là 500")
        }
    }
}
