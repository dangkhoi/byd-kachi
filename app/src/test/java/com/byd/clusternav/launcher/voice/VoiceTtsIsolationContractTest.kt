package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ #0 · PIPER PHẢI Ở TIẾN TRÌNH RIÊNG — bài canh cho bản vá "crash TTS giết launcher" ═══════════════════════
 *
 * Doc gốc `docs/diagnostics/oncar-piper-crash-binding-2026-09-18.md`. [ĐO tombstone xe 2026-09-18, Kachi 1.76]:
 * `OfflineTts.generate` **SIGSEGV** (SEGV_MAPERR) trên luồng `KachiSpeak` ⇒ chết **cả tiến trình launcher** ⇒
 * `NavAccessibilityService` unbind ⇒ rebind kẹt *"Binding"* ⇒ **phím gán chết**.
 *
 * ## Vì sao bài canh này quét SOURCE + MANIFEST chứ không chạy hành vi
 * Hai nửa của bản vá đều **không có mặt trong JVM**: `android:process` là một thuộc tính do nền tảng thi hành
 * lúc cài, và `bindService`/`Messenger`/`onServiceDisconnected` cần một hệ thống Android thật (dự án không dùng
 * Robolectric). Thứ *kiểm được* off-car là **cấu hình** và **đường dây** — và đó cũng đúng là hai thứ hay bị một
 * lượt refactor sau này gỡ mất trong im lặng: đổi một dòng ở [VoiceSpeakerRouter] là kéo con trỏ native về lại
 * tiến trình launcher, compile vẫn xanh, và **chỉ chiếc xe** biết.
 *
 * Phép kiểm THẬT SỰ chạy được off-car là [SherpaTtsSpeaker.voiceFilesPresent] — khối cuối cùng của bài.
 *
 * Mọi lượt quét đi qua `SourceRoots.codeOf` (đã bỏ chú thích) nên một nhắc-tới trong KDoc không thể thoả hợp
 * đồng, và qua `SourceRoots.body` (nổ nếu mốc không còn) để không có vùng quét tràn.
 */
class VoiceTtsIsolationContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val router by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSpeakerRouter.kt") }
    private val link by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/RemotePiperSpeaker.kt") }
    private val service by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/PiperTtsService.kt") }
    private val application by lazy { code("src/main/java/com/byd/clusternav/KachiApplication.kt") }
    private val manifest by lazy { SourceRoots.text("src/main/AndroidManifest.xml") }

    // ══ (1) Launcher KHÔNG được cầm engine ONNX ═══════════════════════════════════════════════════════════

    /**
     * Đường Piper của [VoiceSpeakerRouter] phải là [RemotePiperSpeaker], **không** [SherpaTtsSpeaker].
     *
     * Đây là một dòng duy nhất, và nó là toàn bộ sự khác nhau giữa *"Piper nổ thì mất một câu trả lời"* và
     * *"Piper nổ thì mất phím gán + phải force-stop bằng tay trên xe"*.
     */
    @Test
    fun `duong Piper cua Router di qua tien trinh rieng`() {
        assertTrue(
            router.contains("RemotePiperSpeaker(ctx)"),
            "VoiceSpeakerRouter phải cầm `RemotePiperSpeaker` — engine ONNX không được sống trong tiến trình launcher",
        )
        assertFalse(
            router.contains("SherpaTtsSpeaker("),
            "VoiceSpeakerRouter KHÔNG được dựng `SherpaTtsSpeaker` (kéo `OfflineTts.generate` về lại launcher = " +
                "dựng lại đúng lỗi SIGSEGV làm chết binding phím)",
        )
        // Giọng bé vẫn nhường đường Piper (chia sẻ, không dựng engine thứ hai) — cùng biểu thức như trước khi tách.
    }

    /**
     * Chốt ngược: **không tệp nào của tiến trình launcher** được dựng [SherpaTtsSpeaker] — chỉ [PiperTtsService].
     *
     * Thiếu vế này thì bài trên chỉ gác một tệp, và ngày ai đó thêm một máy đọc "dự phòng" ở chỗ khác
     * (`VoiceSession`, `VoiceWiring`, một bảng Cài đặt) thì con trỏ native quay về launcher mà không ai kêu.
     */
    @Test
    fun `chi PiperTtsService duoc dung SherpaTtsSpeaker`() {
        // ⚠ Dùng lookbehind loại `class SherpaTtsSpeaker(` — chính DÒNG KHAI của lớp cũng khớp chuỗi thô, và
        // [ĐO] lượt đầu của bài này đỏ đúng vì thế (`thấy: [PiperTtsService.kt, SherpaTtsSpeaker.kt]`). Không
        // loại bằng cách bỏ qua tệp đó: giữ nó trong phạm vi quét thì một lượt dựng THẬT ngay trong tệp ấy
        // (một `companion` "tiện tay" dựng một instance dùng chung, chẳng hạn) vẫn bị bắt.
        val construct = Regex("""(?<!class )SherpaTtsSpeaker\(""")
        val users = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .filter { f ->
                    val src = f.readText()
                        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
                        .lines().joinToString("\n") { it.substringBefore("//") }
                    construct.containsMatchIn(src)
                }
                .map { it.name }.toList()
        }.sorted()
        assertEquals(
            listOf("PiperTtsService.kt"),
            users,
            "chỉ `PiperTtsService` (tiến trình `:tts`) được dựng máy đọc Piper; thấy: $users",
        )
    }

    // ══ (2) Manifest: tiến trình `:tts` tồn tại, và tên nó khai MỘT chỗ ══════════════════════════════════

    @Test
    fun `manifest khai PiperTtsService o tien trinh tts va khong exported`() {
        val at = manifest.indexOf(".launcher.voice.PiperTtsService")
        assertTrue(at > 0, "AndroidManifest phải khai báo PiperTtsService")
        // Cắt đúng khối <service> của nó — không quét cả manifest (khối khác cũng có exported="false").
        val block = manifest.substring(at, manifest.indexOf("</service>", at))
        assertTrue(
            block.contains("android:process=\":tts\""),
            "PiperTtsService PHẢI chạy ở tiến trình riêng `:tts` — thiếu thuộc tính này là bản vá không tồn tại " +
                "(mã vẫn compile, IPC vẫn chạy, nhưng crash native lại giết launcher như 1.76)",
        )
        assertTrue(block.contains("android:exported=\"false\""), "service nội bộ — không được exported")
        // Bẫy [ĐO xe 2026-09-15] ở đầu khối <service> của manifest: packageinstaller Android 10 không hiểu
        // element lạ dưới <service> ⇒ trình cài GUI báo "Fail in installation". Giữ khối này ĐƠN GIẢN.
        assertFalse(block.contains("<property"), "KHÔNG thêm <property> (Android 10 packageinstaller không hiểu)")
        assertFalse(block.contains("<intent-filter"), "service này chỉ được bind tường minh — không cần intent-filter")
    }

    /** Hằng hậu tố tiến trình khai MỘT chỗ; manifest và mã so bằng chính nó, không chép chuỗi lần thứ hai. */
    @Test
    fun `hau to tien trinh khai mot cho va duoc dung that`() {
        assertTrue(
            service.contains("const val PROCESS_SUFFIX = \":tts\""),
            "PiperTtsService phải khai PROCESS_SUFFIX = \":tts\" (nguồn duy nhất cho manifest + KachiApplication)",
        )
        assertTrue(
            application.contains("PiperTtsService.PROCESS_SUFFIX"),
            "KachiApplication phải so tên tiến trình bằng chính hằng đó, không viết \":tts\" lần thứ hai",
        )
    }

    /**
     * **Tiến trình `:tts` KHÔNG được nạp mô hình NGHE.**
     *
     * `Application.onCreate` chạy ở MỌI tiến trình. Không có cổng này thì `:tts` cũng gọi `VoiceEngine.preload`
     * (74 MB int8) + `VoiceVad.preload`: vô ích (nó chỉ đọc), và **đúng thứ gây ra lỗi đang vá** — SIGSEGV của
     * `OfflineTts.generate` là `SEGV_MAPERR` dưới áp lực RAM. Cô lập tiến trình mà nhân đôi RAM là cô lập hỏng.
     *
     * ⚠ 2026-09-19: cổng đổi tên `isTtsProcess` → `isBackgroundVoiceProcess` khi "Hey Kachi" ra tiến trình thứ
     * ba (`:wake`) — **một** cổng cho cả hai, vì cả hai đều không cần mô hình NGHE. Vế `:wake` có bài canh riêng
     * ở [VoiceWakeIsolationContractTest]; bài này giữ vế `:tts`.
     */
    @Test
    fun `tien trinh tts khong nap mo hinh nghe`() {
        val fn = SourceRoots.body(application, "override fun onCreate()")
        assertTrue(
            fn.contains("if (isBackgroundVoiceProcess()) return"),
            "phải thoát sớm khi đang ở tiến trình voice nền (`:tts`/`:wake`)",
        )
        val gate = fn.indexOf("isBackgroundVoiceProcess()")
        listOf("AppContainer.get(this)", "VoiceEngine.preload(this)", "VoiceVad.preload(this)").forEach {
            val at = fn.indexOf(it)
            assertTrue(at > 0, "vẫn phải giữ `$it` cho tiến trình launcher")
            assertTrue(at > gate, "`$it` phải nằm SAU cổng `isBackgroundVoiceProcess()` — nếu không `:tts` cũng nạp nó")
        }
    }

    // ══ (3) Hợp đồng "onDone đúng một lần" phải sống sót qua ranh giới tiến trình ═════════════════════════

    /**
     * Đường hỏng MỚI mà bản trong-tiến-trình không có: **đầu kia biến mất giữa câu** — và đó lại chính là ca
     * đang vá, tức ca *chắc chắn sẽ xảy ra*. Cả ba callback mất-kết-nối phải mở mọi chỗ đang chờ, nếu không
     * `VoiceSession` treo im lặng (overlay không tắt, cổng xác nhận không bao giờ mở micro).
     */
    @Test
    fun `mat tien trinh tts thi moi cho dang cho duoc mo`() {
        listOf("onServiceDisconnected", "onBindingDied", "onNullBinding").forEach { cb ->
            val fn = SourceRoots.body(link, "override fun $cb(")
            assertTrue(
                fn.contains("onRemoteGone("),
                "`$cb` phải đi qua `onRemoteGone` — mất `:tts` mà không mở chỗ đang chờ là treo VoiceSession",
            )
        }
        val gone = SourceRoots.body(link, "private fun onRemoteGone(")
        assertTrue(gone.contains("pending.clear()"), "onRemoteGone phải dọn sổ chờ")
        assertTrue(gone.contains("waiters.forEach { fire(it) }"), "…và phải THỰC SỰ gọi từng việc chờ, không chỉ xoá")
        assertTrue(gone.contains("bound = false"), "phải đánh dấu unbound để câu sau nối lại")
        assertTrue(gone.contains("unbindService(conn)"), "onBindingDied buộc phải unbind mới bind lại được")
    }

    /** Bind hỏng ⇒ degrade NGAY (không có gì sẽ về từ `:tts`), và trả `false` để nhật ký nói đúng sự thật. */
    @Test
    fun `bind hong thi dong so ngay va tra false`() {
        val fn = SourceRoots.body(link, "private fun speakInternal(")
        assertTrue(fn.contains("if (bindRemote()) return true"), "bind được ⇒ câu đã xếp, chờ onServiceConnected")
        val at = fn.indexOf("if (bindRemote()) return true")
        val tail = fn.substring(at)
        // ⚠ [SOÁT senior 2026-09-18 · P2] Bài này trước đây đòi `settle(my)` — **chưa đủ**. `bound` được đặt `true`
        // trong khoá TRƯỚC lời gọi `bindRemote()`, nên một câu thứ hai tới giữa hai bước ấy chỉ xếp vào
        // `queued`/`pending` rồi trả `true`; đóng sổ riêng cho `my` thì `onDone` của câu kia **không bao giờ chạy**
        // = đúng cái treo im lặng cả lớp này sinh ra để chặn. Nay đường hỏng đi qua [onRemoteGone] (mở MỌI sổ +
        // unbind + `bound = false`) — chặt hơn, không lỏng hơn.
        assertTrue(
            tail.contains("onRemoteGone()"),
            "bind HỎNG phải mở MỌI chỗ đang chờ (onRemoteGone), không chỉ câu này — xem KDoc tại chỗ",
        )
        assertTrue(tail.contains("return false"), "…và phải trả false (vế (3) của hợp đồng VoiceSpeaker)")
        // Không đọc được / câu rỗng cũng phải đóng sổ (vế (1): *"đọc xong là ngay bây giờ"*).
        assertTrue(
            fn.contains("onDone?.let { fire(it) }"),
            "ca không đọc được phải gọi onDone ngay, không im lặng trả false",
        )
    }

    /** Câu bị **đè** trong lúc chờ nối cũng phải được đóng sổ — chỗ gọi của nó đang chờ một mốc không bao giờ tới. */
    @Test
    fun `cau bi de trong luc cho noi cung duoc dong so`() {
        val fn = SourceRoots.body(link, "private fun speakInternal(")
        assertTrue(
            fn.contains("superseded = queued?.let { pending.remove(it.gen) }"),
            "câu đang chờ nối bị đè ⇒ phải rút việc chờ của nó ra",
        )
        assertTrue(fn.contains("superseded?.let { fire(it) }"), "…rồi gọi nó (ngoài khoá)")
    }

    /** [stop] = *"cắt câu"*, và cắt câu **là** một kiểu đọc xong — Router gọi `stop()` trước MỖI câu. */
    @Test
    fun `stop mo moi cho dang cho va gui lenh cat sang tts`() {
        val fn = SourceRoots.body(link, "override fun stop()")
        assertTrue(fn.contains("generation.incrementAndGet()"), "phải tăng số thế hệ (câu cũ hết đời)")
        assertTrue(fn.contains("PiperTtsService.MSG_STOP"), "phải bảo `:tts` cắt câu đang đọc")
        assertTrue(fn.contains("pending.clear()"), "và mở sổ chờ")
        assertTrue(fn.contains("waiters.forEach { fire(it) }"), "…bằng cách gọi thật từng việc chờ")
    }

    /**
     * Phía `:tts`: **một** đường báo xong, dựa thẳng vào hợp đồng đã có của [SherpaTtsSpeaker.speak] (nó hứa gọi
     * `onDone` đúng một lần ở mọi đường thoát). Thêm đường thứ hai là mở cửa cho hai `MSG_DONE` cùng id.
     */
    @Test
    fun `service doc xong thi bao ve dung replyTo`() {
        val fn = SourceRoots.body(service, "private fun onSpeak(")
        assertTrue(fn.contains("msg.replyTo"), "phải trả lời về CHÍNH hộp thư đã gửi câu, không giữ một tham chiếu riêng")
        assertTrue(
            fn.contains("sherpa.speak(text) { sendDone(reply, id) }"),
            "phải dùng đúng biến thể speak(text, onDone) — biến thể một tham số không có mốc 'đọc xong'",
        )
        assertEquals(
            2, Regex("""sendDone\(reply, id\)""").findAll(fn).count(),
            "đúng hai chỗ báo xong: đường thường (onDone) + đường `speak` NÉM ở tầng JVM; thêm nữa là báo trùng",
        )
        val destroy = SourceRoots.body(service, "override fun onDestroy()")
        assertTrue(destroy.contains("sherpa.shutdown()"), "chỉ service này cầm engine ⇒ chỉ nó nhả được phiên ONNX")
    }

    /** Hai đầu phải dùng CÙNG một bộ mã tin — hằng khai ở service, launcher tham chiếu (không chép số). */
    @Test
    fun `hai dau dung cung bo ma tin`() {
        listOf("MSG_SPEAK", "MSG_STOP", "MSG_DONE", "KEY_ID", "KEY_TEXT").forEach {
            assertTrue(service.contains("const val $it"), "PiperTtsService phải khai `$it` (nguồn duy nhất)")
            assertTrue(
                link.contains("PiperTtsService.$it"),
                "RemotePiperSpeaker phải tham chiếu `PiperTtsService.$it`, không chép giá trị",
            )
        }
    }

    /** Trần 500 dòng (CLAUDE.md §4.1) cho hai tệp mới của lượt này. */
    @Test
    fun `hai tep moi khong vuot tran 500 dong`() {
        mapOf("RemotePiperSpeaker.kt" to link, "PiperTtsService.kt" to service).forEach { (name, src) ->
            assertTrue(src.lines().size <= 500, "$name dài ${src.lines().size} dòng — trần là 500")
        }
    }

    // ══ (4) Phép kiểm gói CHẠY THẬT (off-car, thư mục tạm) ═══════════════════════════════════════════════

    private fun tmpDir(): File = Files.createTempDirectory("piper-voice").toFile().apply { deleteOnExit() }

    private val voice = SherpaTtsCatalog.PIPER_VI_VAIS1000

    /** Lắp đủ ba thứ engine trỏ tới: tệp `.onnx` · bảng token · **thư mục** dữ liệu espeak-ng. */
    private fun install(root: File) {
        File(root, voice.model).apply { parentFile?.mkdirs() }.writeText("onnx")
        File(root, voice.tokens).apply { parentFile?.mkdirs() }.writeText("tokens")
        File(root, voice.dataDir).mkdirs()
    }

    @Test
    fun `du ba thu thi goi duoc coi la da lap`() {
        val root = tmpDir()
        install(root)
        assertTrue(
            SherpaTtsSpeaker.voiceFilesPresent(root, voice),
            "đủ model + tokens + thư mục dữ liệu ⇒ phải nhận là đã lắp",
        )
    }

    @Test
    fun `thieu bat ky thu nao thi coi la chua lap`() {
        listOf(voice.model, voice.tokens).forEach { missing ->
            val root = tmpDir()
            install(root)
            assertTrue(File(root, missing).delete(), "phải xoá được $missing để dựng ca thiếu")
            assertFalse(
                SherpaTtsSpeaker.voiceFilesPresent(root, voice),
                "thiếu `$missing` ⇒ phải trả false (nếu không `RemotePiperSpeaker` bind `:tts` rồi đọc rỗng)",
            )
        }
        val noData = tmpDir()
        install(noData)
        assertTrue(File(noData, voice.dataDir).deleteRecursively(), "phải xoá được thư mục dữ liệu")
        assertFalse(
            SherpaTtsSpeaker.voiceFilesPresent(noData, voice),
            "họ Piper BẮT BUỘC có `espeak-ng-data` (xem KDoc SherpaTtsCatalog) — thiếu là chưa lắp",
        )
    }

    /** Thư mục rỗng / chưa tồn tại ⇒ chưa lắp. Ca của **mọi máy mới cài** — và nó không được bind `:tts`. */
    @Test
    fun `goi chua tai thi chua lap`() {
        assertFalse(SherpaTtsSpeaker.voiceFilesPresent(tmpDir(), voice), "thư mục rỗng ⇒ chưa lắp")
        assertFalse(
            SherpaTtsSpeaker.voiceFilesPresent(File(tmpDir(), "khong-ton-tai"), voice),
            "thư mục chưa tồn tại ⇒ chưa lắp, và KHÔNG được ném",
        )
    }

    /**
     * ⚠ `dataDir` phải là **THƯ MỤC**, không phải tệp — một gói side-load chép dở (tệp cùng tên) không được
     * tính là đã lắp, vì engine sẽ dựng xong rồi mới nổ khi đọc dữ liệu ngôn ngữ.
     */
    @Test
    fun `dataDir la tep chu khong phai thu muc thi chua lap`() {
        val root = tmpDir()
        install(root)
        val data = File(root, voice.dataDir)
        assertTrue(data.deleteRecursively())
        data.writeText("khong-phai-thu-muc")
        assertFalse(SherpaTtsSpeaker.voiceFilesPresent(root, voice), "`${voice.dataDir}` là tệp ⇒ chưa lắp")
    }
}
