package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 · DÂY NỐI CỦA ĐƯỜNG LỆNH · vai TẦNG NGHE (R9–R14 · R13) ══════════════════════════════════════════════════
 *
 * Tách từ [VoiceCommandWiringContractTest] (DEBT-500-TEST, 2026-09-26; nguyên văn, không nới assert). Bài THẬT ở đây:
 * *"tầng tiếng nằm đúng chỗ và không ra mạng"* — R13 là một **cam kết về phạm vi**, không phải một câu trong tài liệu.
 * Ngày ai đó thêm `SpeechRecognizer` (đường của Google, cần mạng) hoặc mở một socket trong một tệp `Voice*`, bài này đỏ.
 * Bản trước cấm cả `AudioRecord` vì pha ấy CHƯA có micro; owner mở cổng 2026-09-14 nên câu hỏi đổi, **phạm vi kiểm thì
 * không được mất**. Cộng: một tệp mở micro · mô hình tải/side-load · ba lối vào phiên nghe · phiên có trần và chết theo màn.
 */
class VoiceListenWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }

    /**
     * **Bài canh phạm vi, bản pha NGHE.**
     *
     * R8 cũ nói *"chưa có mic"* và bài canh này từ chối mọi `AudioRecord`. Từ 1.49 owner đã mở cổng đó
     * (2026-09-14: *"Kiki nó chạy được, Gemini chạy được trên xe thì app mình cũng chạy được"* — micro cho app
     * thường được coi là [ĐO] qua Kiki, `docs/diagnostics/kiki-car-RE-2026-09-14.md`), nên câu hỏi phải đổi. Nó
     * KHÔNG được biến mất: cam kết mới hẹp hơn và kiểm được y như cũ.
     *
     * Ba điều còn bị cấm, mỗi điều một lý do khác nhau:
     *  • **`SpeechRecognizer` / `RecognitionListener`** — đường của Google, cần mạng và cần dịch vụ Google trên
     *    đầu xe. Cả hai đều là thứ Kachi cố ý không phụ thuộc (RE Kiki §2.1: điều khiển xe không được phụ thuộc
     *    4G). Có nó trong mã là tính năng **âm thầm** đổi từ tại-máy sang trên-mây.
     *  • ~~**`TextToSpeech`**~~ — **cổng này ĐÃ MỞ** (owner 2026-09-15: *"Sau khi làm xong việc thì có phản hồi
     *    lại bằng voice cho user chưa?"*), và nó mở **đúng cách mà bài này đòi**: spec trước
     *    (`docs/specs/kachi-voice-feedback.html`), mã sau. Lệnh cấm không biến mất mà **hẹp lại** — xem
     *    [chi tiep tieng chi duoc mo o DUNG MOT TEP]: cấm **dựng** máy đọc ở bất kỳ tệp `Voice*` nào; tên lớp
     *    trong KDoc thì không cấm (chặn cả tên là chặn nhầm đúng chỗ đang giải thích luật).
     *  • **`MediaRecorder(`** — ghi âm ra TỆP. Nhận dạng tại máy không cần một tệp âm thanh nào tồn tại; có tệp
     *    là có thứ để rò rỉ. Cấm **dựng** lớp đó, không cấm nhắc tên nó: `MediaRecorder.AudioSource.VOICE_RECOGNITION`
     *    chỉ là bảng hằng NGUỒN ÂM mà `AudioRecord` đọc — chặn cả tên là chặn nhầm đúng thứ ta muốn dùng.
     */
    @Test
    fun `khong dung ASR tren may chu, khong ghi am ra tep`() {
        val banned = listOf("SpeechRecognizer", "RecognitionListener", "MediaRecorder(")
        voiceSources().forEach { (name, src) ->
            banned.forEach { token ->
                assertFalse(
                    src.contains(token),
                    "$name dùng `$token` — pha NGHE chốt là nhận dạng TẠI MÁY (spec §4.4). Đổi quyết định đó " +
                        "phải sửa spec trước, không sửa mã trước.",
                )
            }
        }
    }

    /**
     * **TIẾNG NÓI KHÔNG RỜI KHỎI XE** — lời hứa hạng nhất của cả pha này, nên nó có một bài canh riêng.
     *
     * Quét mọi tệp `Voice*` của cả ba module tìm dấu vết đi ra ngoài: socket, HTTP, WebSocket, OkHttp, Retrofit.
     * Một dòng như thế là tính năng đổi bản chất — từ *"xe tự nghe"* thành *"xe gửi giọng bạn đi đâu đó"* — mà
     * người dùng không có cách nào biết. Đây đúng là loại thay đổi phải **không biên dịch được**, chứ không phải
     * loại chờ ai đó soát ra.
     *
     * ⚠ [VoiceModelStore] **có** tải mô hình qua mạng và đó là ngoại lệ ĐÚNG: nó tải **xuống** một tệp đã ghim
     * sha256, không gửi gì **lên**. Nên nó đi qua `HttpConn` (cửa duy nhất, chỉ HTTPS) và bài canh dưới chỉ tha
     * đúng một tên đó, không tha cả tệp.
     */
    @Test
    fun `khong tep Voice nao gui tieng noi ra mang`() {
        val banned = listOf("Socket(", "DatagramSocket", "WebSocket", "OkHttp", "Retrofit", "URLConnection", "URL(")
        voiceSources().forEach { (name, src) ->
            banned.forEach { token ->
                assertFalse(src.contains(token), "$name mở một đường ra mạng (`$token`) — tiếng nói phải ở lại trong xe")
            }
        }
        val store = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt")
        assertTrue(store.contains("HttpConn.open("), "đường TẢI MÔ HÌNH phải đi qua cửa HTTPS duy nhất `HttpConn`")
        assertFalse(store.contains("outputStream.write(") && store.contains("conn.outputStream"),
            "cửa tải mô hình chỉ được ĐỌC xuống, không gửi gì lên")
    }

    /**
     * **V1.1 — NGOẠI LỆ MẠNG THỨ HAI, và nó phải ở lại đúng kích cỡ hiện tại.**
     *
     * [VoiceGeocoder] gửi một **tên địa điểm** đi để lấy về toạ độ ([ĐO] VietMap chỉ nhận toạ độ — nguồn Kiki,
     * xem KDoc `VoiceAppTargets`). Lời hứa *"tiếng nói không rời khỏi xe"* vẫn nguyên: thứ đi ra là cùng loại
     * chữ mà người ta gõ vào ô tìm kiếm bản đồ mười lần một ngày. Bài này khoá đúng ba chốt của KDoc lớp ấy —
     * ngày ai đó gửi thêm gì khác, hoặc gửi từ một tệp khác, nó đỏ.
     */
    @Test
    fun `chi dung mot cua mang cho tra cuu dia diem, va no khong cham toi tieng`() {
        val geo = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceGeocoder.kt")
        assertTrue(geo.contains("HttpConn.open("), "phải đi qua cửa HTTPS duy nhất của dự án")
        assertFalse(geo.contains("outputStream"), "chỉ GET — không gửi thân yêu cầu nào")
        listOf("AudioRecord", "ShortArray", "pcm", "Recognizer").forEach {
            assertFalse(geo.contains(it), "lớp tra cứu địa điểm chạm tới tiếng (`$it`) — hai việc này phải tách hẳn")
        }
        // Và chỉ ĐÚNG BA tệp Voice* được phép có `HttpConn`: tải mô hình (xuống) · tra cứu địa điểm (chữ) ·
        // giải video_id nhạc (chữ). YoutubeResolver gửi một **chuỗi tìm** đi lấy về `video_id` để "phát luôn"
        // (owner 2026-09-18, cơ chế Kiki) — cùng loại chữ như geocoder, KHÔNG gửi tiếng; degrade-safe.
        val yt = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceYoutubeResolver.kt")
        assertFalse(yt.contains("outputStream"), "VoiceYoutubeResolver chỉ GET — không gửi thân yêu cầu nào")
        listOf("AudioRecord", "ShortArray", "pcm", "Recognizer").forEach {
            assertFalse(yt.contains(it), "VoiceYoutubeResolver chạm tới tiếng (`$it`) — phải tách hẳn")
        }
        val users = voiceSources().filter { (_, src) -> src.contains("HttpConn") }.map { it.first }.sorted()
        assertEquals(
            listOf("VoiceGeocoder.kt", "VoiceModelStore.kt", "VoiceYoutubeResolver.kt"),
            users, "có tệp Voice* thứ tư ra mạng: $users",
        )
    }

    /** Và bộ nhận dạng phải là **sherpa-onnx tại máy**, giải mã tự do + biasing — không gửi tiếng ra mạng. */
    @Test
    fun `bo nhan dang la sherpa tai may, giai ma tu do co biasing`() {
        val rec = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt")
        assertTrue(rec.contains("com.k2fsa.sherpa.onnx.OfflineRecognizer"), "phải dùng sherpa-onnx `OfflineRecognizer` (tại máy)")
        assertTrue(rec.contains("createStream(hotwords)"),
            "phải bơm hotwords per-stream — đó là biasing kéo giải mã tự do về tập lệnh (thay ngữ pháp FST của Vosk)")
        // ⚠ Needle bỏ dấu ngoặc ĐÓNG (A1, 2026-09-15): `hotwordsFile` nay nhận nhãn **sổ địa chỉ** của hồ sơ đang
        // dùng (`hotwordsFile(places)` — docs/specs/kachi-voice-addresses.html R6). Tính chất bài canh KHÔNG đổi:
        // hotwords vẫn phải sinh từ [SherpaBiasing], không phải một nguồn thứ hai.
        assertTrue(rec.contains("SherpaBiasing.hotwordsFile("),
            "hotwords phải sinh từ danh mục control ([SherpaBiasing]) — cùng NGUỒN với tầng chữ")
        assertFalse(rec.contains("AudioRecord"),
            "bộ nhận dạng KHÔNG tự mở micro — micro chỉ ở [VoiceCapture] (trong trần 8 s + tầm bài canh mạng)")
    }

    /**
     * **V1.1 (R16) — bộ giải mã TỰ DO tồn tại, nhưng chỉ cho LƯỢT 2 và chỉ sau một cụm kích hoạt.**
     *
     * Cam kết *"lượt 1 ràng bằng ngữ pháp"* không đổi một chữ: câu lệnh xe vẫn dựng từ tập đóng. Cái bài này
     * khoá là **điều kiện chạy** của lượt 2 — nếu ai đó gỡ cổng `VoiceOpenVocab.triggerOf` thì mọi câu nói sẽ
     * đi qua một bộ giải mã 19.529 từ, tức đúng thứ pha NGHE bỏ công tránh, mà **không có gì báo**.
     */
    @Test
    fun `bo giai ma tu do chi chay o luot 2, sau mot cum kich hoat`() {
        val rec = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt")
        assertTrue(rec.contains("fun openFree("), "R16 cần một bộ giải mã tự do cho phần đuôi từ vựng mở")
        assertTrue(rec.contains("VoiceRecognizer(rec, \"\")"), "bộ giải mã tự do dựng KHÔNG kèm hotwords (biasing rỗng)")

        // ⚠ voice pha 2 (2026-09-16) — khối này tách khỏi `VoiceSession.kt` sang `VoiceFreeTail.kt` (trần 500 dòng, xem KDoc lớp đó).
        // Bài canh đi theo VAI, nên nó quét đúng nơi vai ấy đang ở; tính chất được canh không đổi một chữ.
        val fn = SourceRoots.body(
            code("src/main/java/com/byd/clusternav/launcher/voice/VoiceFreeTail.kt"),
            "fun decode(",
        )
        assertTrue(
            fn.indexOf("VoiceOpenVocab.triggerOf(") in 0 until fn.indexOf("VoiceRecognizer.openFree("),
            "phải hỏi cụm kích hoạt TRƯỚC khi dựng bộ giải mã tự do — thứ tự này chính là cái cổng",
        )
        assertTrue(fn.contains("?: return plain"), "không có cụm kích hoạt ⇒ lùi về bản ngữ pháp, không chạy lượt 2")
        assertTrue(fn.contains("heard.pcm"), "lượt 2 chạy trên khúc PCM ĐÃ THU, không mở micro lần nữa")

        // Chỉ hai chỗ được gọi: phiên nghe thật và đường đo bằng WAV (phải đi cùng một con đường — R14).
        val users = voiceSources().filter { (_, src) -> src.contains("openFree(") }.map { it.first }.sorted()
        assertEquals(listOf("VoiceFreeTail.kt", "VoiceRecognizer.kt", "VoiceWakeAsr.kt", "VoiceWavProbe.kt"), users,
            "bộ giải mã tự do bị gọi ở chỗ lạ: $users")
    }

    /**
     * **[SOÁT Pass 3 · P1] — cổng hỏi-lại VỀ MUỘN không được mở thêm một lượt nghe.**
     *
     * Tới 1.49 mọi lượt `confirm` xảy ra **đồng bộ** trong `execute`, tức chắc chắn còn trong phiên. V1.1 mở một
     * đường bất đồng bộ: câu dẫn đường tới app chỉ-nhận-toạ-độ đi tra cứu mạng (tới ~20 s với `HttpConn`) rồi
     * MỚI hỏi lại. Không có khoá thế hệ thì lượt về muộn sẽ đặt lại `pendingConfirm` của phiên đang chạy, vẽ câu
     * hỏi cũ đè lên tấm chữ mới, và mở `AudioRecord` **thứ hai** trong lúc micro phiên mới còn đang mở.
     */
    @Test
    fun `hoi lai ve muon sau khi phien da qua thi khong mo them luot nghe`() {
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        val fn = SourceRoots.body(session, "internal fun execute(")
        assertTrue("val my = generation.get()" in fn, "phải ghim THẾ HỆ của phiên tại lúc thi hành")
        assertTrue(
            "if (stale(my) || overlay == null) onNo() else confirm(question, onYes, onNo)" in fn,
            "phiên đã qua / tấm chữ đã đóng ⇒ trả lời KHÔNG, tuyệt đối không mở thêm một lượt nghe xác nhận",
        )
    }

    /** Tệp chạm micro phải đúng MỘT — hai chỗ mở `AudioRecord` là hai câu trả lời cho "mic đang bật tới bao giờ". */
    @Test
    fun `chi mot tep duy nhat mo micro`() {
        val users = voiceSources().filter { (_, src) -> src.contains("AudioRecord(") }.map { it.first }
        assertEquals(listOf("VoiceCapture.kt", "VoiceWakeListener.kt"), users.sorted(), "chỉ VoiceCapture (lệnh) + VoiceWakeListener (wake) được mở micro; một-mic do VoiceSingleFlight đảm bảo; thấy: $users")
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }
                .filter { it.readText().contains("AudioRecord(") }.map { it.name }.toList()
        }
        assertEquals(listOf("VoiceCapture.kt", "VoiceWakeListener.kt"), all.sorted(), "chỉ hai tệp Voice* mở micro (lệnh + wake); thấy: $all")
    }

    /** Manifest phải xin `RECORD_AUDIO` — đảo đúng bài canh cũ của R8, cùng một chỗ, cùng một tệp. */
    @Test
    fun `manifest xin quyen ghi am va khai micro la khong bat buoc`() {
        val text = Files.readString(SourceRoots.path("src/main/AndroidManifest.xml"))
        assertTrue(text.contains("android.permission.RECORD_AUDIO"), "pha NGHE cần quyền micro")
        assertTrue(
            text.contains("""android:name="android.hardware.microphone" android:required="false""""),
            "đầu xe không có micro vẫn phải CÀI được app — chỉ mất đúng tính năng này",
        )
    }

    /**
     * Ba lối vào phải trỏ về **cùng một** phiên.
     *
     * Đây là bài chống đúng bệnh `CastShell.evictVd` (CLAUDE.md §8) ở dạng nguy hiểm hơn: một ô/nút **hiện ra
     * trên màn hình** mà bấm không ra gì. Ba dòng dưới là ba chỗ người dùng chạm được.
     */
    @Test
    fun `ba loi vao phien nghe deu noi that`() {
        assertTrue(wiring.contains("LauncherActions.VOICE -> onVoice()"),
            "ô *Nói với xe* trên thanh nút phải có nhánh thật trong `controlDock`")
        assertTrue(activity.contains("onVoice = { voice.start() }"), "nút mic trên thanh trên phải gọi phiên nghe")
        assertTrue(activity.contains("{ voice.start() },"), "ô thanh nút phải nhận CÙNG lambda với nút mic")
        assertTrue(activity.contains("startVoiceIfRequested(intent, voice)"),
            "đích phím vô-lăng *Kachi nghe* vào màn chính qua extra ⇒ phải có chỗ đọc extra đó")
        val launcher = code("src/main/java/com/byd/clusternav/modules/voicekey/AssistantLauncher.kt")
        assertTrue(launcher.contains("spec == TARGET_KACHI_VOICE"), "bộ gán phím phải nhận đích *Kachi nghe*")
        // Owner 2026-09-25: phím-thoại mở phiên nghe HEADLESS (overlay nổi trên app đang xem), KHÔNG kéo
        // KachiHomeActivity lên. `VoiceWakeService.listenNow` là đường phiên-nghe thật (fireWake headless), nên
        // vẫn "nói thật" — chỉ đổi CƠ CHẾ (service overlay) chứ không thành nút chết.
        assertTrue(launcher.contains("VoiceWakeService.listenNow"),
            "và phải mở phiên nghe headless (overlay), không kéo launcher lên đè app đang xem")
    }

    /** Nút mic chỉ hiện khi **mô hình đã có** — hứa một việc chưa làm được là tệ hơn không hứa. */
    @Test
    fun `nut mic tren thanh tren gate boi mo hinh da tai`() {
        assertTrue(
            activity.contains("Prefs.voiceMicPill(this) && VoiceModelStore.isReady(this)"),
            "nút mic phải gate bởi CẢ công tắc người dùng LẪN việc mô hình đã tải",
        )
        val bars = code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsBars.kt")
        assertTrue(bars.contains("deps.bridge.setVoiceMicPill(on)"),
            "công tắc nút mic phải ghi qua cầu, không mở cửa riêng vào nơi lưu bền")
    }

    /** Quyền micro phải nằm trong vòng kiểm, và tự cấp đúng bằng `pm grant`. */
    @Test
    fun `quyen micro nam trong vong kiem va tu cap duoc`() {
        val preflight = code("src/main/java/com/byd/clusternav/launcher/PermissionPreflight.kt")
        assertTrue(preflight.contains("LauncherRequirements.MICROPHONE.id -> micGranted(ctx)"),
            "vòng kiểm phải ĐỌC được trạng thái quyền micro")
        assertTrue(preflight.contains("\"pm grant \$PKG android.permission.RECORD_AUDIO\""),
            "phải tự cấp bằng ĐÚNG câu lệnh mà máy ảo dùng — một câu lệnh cho cả hai môi trường")
        assertEquals(FixBy.SELF, LauncherRequirements.MICROPHONE.fixBy, "tự cấp được ⇒ không hỏi người dùng")
        assertFalse(LauncherRequirements.MICROPHONE.coreFeature,
            "thiếu micro KHÔNG làm mất tính năng lõi ⇒ không được nổ toast ở màn chính mỗi lần mở")
    }

    /** Mô hình tải riêng: ghim sha256 + cỡ, và có đường GỠ. */
    @Test
    fun `mo hinh tai rieng co ghim sha va co duong go`() {
        val store = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt")
        assertTrue(store.contains("got.bytes != mf.bytes") && store.contains("got.sha256.equals(mf.sha256"),
            "MỖI tệp tải về phải qua CẢ hai phép kiểm (sha256 + cỡ) trước khi đặt vào chỗ")
        assertTrue(store.contains("requireSafe("),
            "tên MỖI tệp (từ mạng) phải qua luật chống leo thư mục (CLAUDE.md §4.1)")
        assertTrue(store.contains("fun remove("), "phải có đường GỠ — vài trăm MB không được ở lại vĩnh viễn")
        val settings = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt")
        assertTrue(settings.contains("VoiceModelStore.install(context)"), "màn Cài đặt phải gọi đường cài THẬT")
        assertTrue(settings.contains("VoiceEngine.release()"),
            "gỡ phải trả mô hình khỏi bộ nhớ TRƯỚC khi xoá tệp, không thì mã native còn giữ bản cũ")
        assertTrue(
            code("src/main/java/com/byd/clusternav/launcher/SettingsVoiceSection.kt").contains("VoiceModelSettings(context, rows, deps).build(body)"),
            "hàng tải mô hình phải có mặt trong màn Cài đặt (nhóm Giọng nói) — một lớp không ai dựng là mã chết",
        )
    }

    /**
     * Đường **side-load** (xe không internet, owner 2026-09-15) phải CÒN ĐƯỢC GỌI và phải qua ĐÚNG phép kiểm của
     * đường mạng. Năm bài JVM của `VoiceModelSideloadTest` kiểm phần thuần — nhưng chúng vẫn XANH nếu một lần viết
     * lại `fetch`/`install` nuốt mất call site (đúng bệnh `CastShell.evictVd`, CLAUDE.md §8). Bài này khoá dây nối.
     */
    @Test
    fun `duong side-load duoc goi that va khong am tham roi ve mang`() {
        val store = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt")
        assertTrue(store.contains("VoiceModelSideload.candidate(importDir, safe)"),
            "install phải hỏi tệp side-load bằng ĐÚNG tên đã qua requireSafe, không phải tên thô")
        assertTrue(store.contains("VoiceModelSideload.IMPORT_SUBDIR") && store.contains("getExternalFilesDir"),
            "thư mục đặt tệp phải là thư mục ngoài của app (adb push / USB), không phải một chỗ cần quyền")
        assertTrue(store.contains("VoiceModelSideload.copyVerified("),
            "có tệp side-load ⇒ fetch phải đi đường chép-và-băm, không có nhánh nhận tệp mà không kiểm")
        val sideload = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSideload.kt")
        assertTrue(sideload.contains("got.bytes != expectedBytes") &&
            sideload.contains("got.sha256.equals(expectedSha256"),
            "tệp chép từ USB phải qua CẢ hai phép kiểm (sha256 + cỡ) y như tệp tải từ mạng")
        assertTrue(sideload.contains("out.delete()"),
            "tệp side-load sai phải bị XOÁ — không để lại tệp hỏng cho bước Verifying/onnxruntime")
        // Sai ⇒ báo thẳng. Nếu ai đó cho nó rơi về `download(...)` thì trên xe không mạng người chép USB sẽ chỉ
        // thấy "lỗi mạng" và không bao giờ biết tệp mình chép sai.
        assertFalse(sideload.contains("download("),
            "lớp side-load KHÔNG được biết tới đường mạng — sai là báo sai, không âm thầm rơi về mạng")
    }

    /** Phiên nghe có TRẦN thời gian, và hộp xác nhận mặc định là KHÔNG. */
    @Test
    fun `phien nghe co tran thoi gian va cong xac nhan mac dinh la KHONG`() {
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        assertTrue(session.contains("MAX_LISTEN_MS = 8_000L"), "phải có trần cứng cho một phiên nghe")
        assertTrue(session.contains("capture.listen(it, MAX_LISTEN_MS"), "và trần đó phải được TRUYỀN vào vòng nghe")
        // ⚠ 1.66 — lượt nghe "đồng ý/huỷ" nằm ở `VoiceSessionTurns.kt` (xem chú thích ở bài OQ4 phía trên).
        val turns = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt")
        assertTrue(turns.contains("answerConfirm(answer == true)"),
            "nghe không ra `đồng ý` ⇒ KHÔNG. Im lặng không bao giờ được hiểu là đồng ý.")
        assertTrue(turns.contains("VoiceLexicon.confirmAnswer("),
            "câu trả lời có/không phải đọc bằng bảng ở `:core` (kiểm off-car), không bằng một `if` ở tầng vẽ")
        // 1.70 — vai tiêu điểm âm thanh tách khỏi VoiceCapture sang VoiceAudioFocus (trần 500 dòng).
        val audioFocus = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceAudioFocus.kt")
        assertTrue(audioFocus.contains("AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"),
            "chỉ HẠ tiếng nhạc, không dừng hẳn — xem KDoc VoiceAudioFocus")
    }

    /**
     * [SOÁT Pass 2 · P1] **Phiên nghe phải chết theo màn chính.**
     *
     * Tấm chữ là một cửa sổ `TYPE_APPLICATION_OVERLAY` ⇒ nó KHÔNG chết cùng activity (đúng họ với
     * `DrawerController`, xem chú thích trong `onDestroy`). Thiếu lời gọi này thì sau khi màn chính huỷ vẫn còn
     * một cửa sổ phủ toàn màn ăn mọi cú chạm, một `AudioRecord` đang mở, và một `VoiceDispatcher` trỏ vào
     * activity đã huỷ — thứ chỉ hết bằng cách khởi động lại đầu xe.
     */
    @Test
    fun `phien nghe chet theo man chinh va huy duoc khi khong con vong nghe`() {
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        assertTrue(session.contains("fun stop()"), "`VoiceSession` phải có đường tắt phiên ngay lập tức")
        val destroy = SourceRoots.body(activity, "override fun onDestroy()")
        assertTrue(destroy.contains("voice.stop()"), "`onDestroy` phải tắt phiên nghe — cửa sổ overlay sống lâu hơn màn")
        assertTrue(destroy.contains("voiceLazy.isInitialized()"),
            "và hỏi `isInitialized` trước: chạm vào `voice` ở đây sẽ DỰNG một phiên ngay lúc màn đang chết")
        // Huỷ ở trạng thái KHÔNG có vòng nghe (đang báo thiếu quyền / đang nán lại sau câu trả lời) phải đóng
        // được tấm chữ; nếu không, `running` khoá tới 8 giây và nút mic bấm không ra gì.
        assertTrue(SourceRoots.body(session, "fun cancel()").contains("if (!capturing.get()) close()"),
            "chạm ra ngoài phải đóng được tấm chữ khi không có vòng nghe nào tự đóng nó")
    }

    /**
     * [SOÁT Pass 2 · P1/P2] **Đường tải mô hình: một lượt tại một thời điểm · đủ chỗ · không rơi sang `http`.**
     *
     * Nút trong Cài đặt tự khoá khi đang tải, nhưng nó chỉ là một `TextView` của **trang đang dựng**: mở lại màn
     * Cài đặt cho ra một nút mới bật sẵn trong khi luồng cũ còn chạy ⇒ hai luồng cùng ghi một `model.zip`.
     */
    @Test
    fun `tai mo hinh co chot mot luot, kiem cho trong va chot HTTPS sau chuyen huong`() {
        val store = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt")
        assertTrue(store.contains("installing.compareAndSet(false, true)"),
            "phải có chốt một-lượt-cài: hai lượt song song ghi đè nhau rồi cùng hỏng sha")
        assertTrue(store.contains("usableSpace"),
            "phải hỏi chỗ trống TRƯỚC khi tải 32 MB — hết đĩa giữa chừng là mất tiền mạng 4G mà không ai biết vì sao")
        assertTrue(store.contains("conn.url.protocol"),
            "sau chuyển hướng phải kiểm lại giao thức: `HttpConn` chỉ chốt được địa chỉ ta GÕ VÀO")
    }

    /**
     * [SOÁT Pass 2 · P1] Trần 500 dòng (CLAUDE.md §4.1 · spec R-nf4) cho **những tệp pha NGHE đã chạm**.
     *
     * Không quét cả repo: vài tệp đã trên trần từ lâu và là nợ riêng. Bài này chỉ giữ đúng một lời hứa — thêm
     * tính năng thì thêm **tệp**, không thêm dòng vào hai tệp vốn đã sát trần.
     */
    @Test
    fun `pha NGHE khong day tep nao qua tran 500 dong`() {
        val touched = listOf(
            "src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiHomeSlots.kt",
            "src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt",
            "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeKeys.kt",
        )
        touched.forEach { rel ->
            val lines = code(rel).lines().size
            assertTrue(lines <= 500, "$rel dài $lines dòng — trần là 500 (CLAUDE.md §4.1); tách theo VAI, đừng nén dòng")
        }
        voiceSources().forEach { (name, src) ->
            assertTrue(src.lines().size <= 500, "$name dài ${src.lines().size} dòng — trần là 500")
        }
    }
}

/** Mọi tệp `Voice*` của `:app` và `:core` — một chỗ dựng danh sách cho mọi bài quét. */
internal fun voiceSources(): List<Pair<String, String>> {
    val out = SourceRoots.moduleSourceRoots().flatMap { root ->
        root.toFile().walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") && it.name.startsWith("Voice") }
            .map { it.name to it.readText() }.toList()
    }
    assertTrue(out.size >= 14, "không tìm thấy đủ tệp Voice*.kt để quét; thấy: ${out.map { it.first }}")
    return out
}
