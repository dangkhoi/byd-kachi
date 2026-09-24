package com.byd.clusternav.launcher.testbridge

/**
 * ═══ T-BRIDGE · MỘT LỆNH ĐÃ PHÂN TÍCH ════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` R3. Thuần Kotlin ⇒ kiểm off-device.
 *
 * @property name mã lệnh ([TestBridgeCommands.SAY]…), đã kiểm là có thật.
 * @property text câu lệnh chữ (`say`).
 * @property path đường dẫn tệp WAV (`wav`), rỗng = để bên Android tự dò chỗ quen thuộc.
 * @property pkg tên gói (`slot` · `open`).
 * @property arg tên hồ sơ / tên bố cục sẵn (`profile` · `preset`) — **không** đặt tên `name` vì trường đó đã là
 *   mã lệnh; hai thứ trùng tên trong cùng một data class là chỗ truyền nhầm không ai thấy.
 * @property file tên tệp prefs (`prefs`), đã kiểm nằm trong danh sách cho phép.
 * @property slot số ô **1-based** đúng như người ta nói/gõ (`slot` · `slot_clear`); phép đổi sang 0-based nằm ở
 *   tầng thi hành, đúng một chỗ (cùng luật `VoiceDispatcher.runOpenApp`).
 * @property id mã một control trong `ControlRegistry` (`ctl`). **KHÔNG** kiểm tồn tại ở tầng phân tích (cùng luật
 *   `pkg`/`profile`/`preset`): danh mục control nằm ở `:core` nhưng phép kiểm ngữ nghĩa dồn về tầng thi hành để
 *   một lời đáp có thể liệt kê mã hợp lệ khi gõ sai — xem KDoc `KachiTestBridge.runCtl`.
 * @property v giá trị chính của control (`ctl` · `--ei v`): TOGGLE/COVER 1/0 · STEP giá trị · SELECT chỉ số ·
 *   BUTTON bỏ qua. **null = không truyền** ⇒ tầng thi hành chọn mặc định theo kind (bật/mở/bấm) — khác hẳn `0`
 *   (tắt/đóng), nên phải là `Int?` chứ không ép về `0` ở đây.
 * @property autoConfirm `--ez auto_confirm true` — xem KDoc [TestBridgeCommands.EXTRA_AUTO_CONFIRM].
 * @property dev tên ĐƠN GIẢN device BYDAuto cho lệnh `hal` (`BYDAutoBodyworkDevice`) — rỗng ⇒ tầng thi hành mặc
 *   định `BYDAutoBodyworkDevice` (thân xe: kính/cửa/đèn/rèm). FQN đầy đủ dựng ở tầng thi hành qua
 *   `HalBindingTable.deviceFqn` — MỘT converter, không viết cứng hai chỗ.
 * @property method tên method HAL thô cho lệnh `hal` (`getWindowState` · `setBodyWindowCtrlState`). Bắt buộc.
 * @property halArgs đối số int cho `hal`, phân tách bằng dấu phẩy (`"1"` · `"1,2"`). Getter 0-đối để rỗng.
 * @property op `get` (đọc getter) hay `set` (ghi named-method) cho `hal`; rỗng ⇒ suy theo tiền tố `get` của
 *   [method]. `set` là lượt GHI thân xe nên đi qua đúng cổng CONFIRM như `ctl` (cần `--ez auto_confirm true`).
 * @property key tên khoá prefs cho lệnh `prefs_set`, đã kiểm nằm trong [TestBridgeCommands.WRITABLE_PREFS_KEYS].
 *   Giá trị đi trong [text] (một chuỗi cho MỌI kiểu — xem KDoc [TestBridgeCommands.PREFS_SET]).
 */
data class TestBridgeCommand(
    val name: String,
    val text: String = "",
    val path: String = "",
    val pkg: String = "",
    val arg: String = "",
    val file: String = "",
    val slot: Int = 0,
    val id: String = "",
    val v: Int? = null,
    val autoConfirm: Boolean = false,
    val dev: String = "",
    val method: String = "",
    val halArgs: String = "",
    val op: String = "",
    val key: String = "",
)

/** Kết quả phân tích: hoặc một lệnh dùng được, hoặc một **mã lỗi ASCII** cho script đọc. */
sealed interface TestBridgeParse {
    data class Ok(val cmd: TestBridgeCommand) : TestBridgeParse

    /**
     * @property code mã lỗi — **ASCII, không dịch** (cùng luật `PermissionReport.logLine`: hai lượt đo trên hai
     *   máy khác ngôn ngữ phải grep được bằng MỘT chuỗi).
     */
    data class Err(val code: String) : TestBridgeParse
}

/**
 * ═══ T-BRIDGE · DANH MỤC LỆNH + BỘ PHÂN TÍCH EXTRA ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` R3. Đây là **toàn bộ** phần "hiểu một lệnh" của cầu kiểm thử, tách
 * khỏi Android có chủ ý.
 *
 * ## Vì sao tách, khi nó chỉ có một chỗ gọi
 * Vì đây là phần **sai được mà không ai thấy**: thiếu một extra, số ô âm, tên tệp prefs lạ — cả ba đều cho ra
 * một lệnh "chạy được" nhưng làm sai việc, và trên xe thì hậu quả là một ô bị thay app trong lúc đang lái. Để
 * nó nằm trong `BroadcastReceiver` thì cách duy nhất kiểm là cắm máy rồi bắn `am broadcast` — tức không bao giờ
 * kiểm hết được 13 lệnh × các ca thiếu đối số. Ở `:core` thì một bài `TestBridgeCommandTest` đi hết bảng.
 *
 * ## Danh mục là DỮ LIỆU ([SPECS]), không phải một `when` dài
 * Nhờ vậy hai câu hỏi khác nhau dùng chung một nguồn: *"lệnh này cần đối số gì"* (phép kiểm ở [parse]) và
 * *"cầu có những lệnh nào"* (câu trả lời của lệnh `help`/tài liệu). Viết hai lần là hai bản sẽ lệch — đúng bẫy
 * hai-bản-sao mà dự án đã trả giá bốn lần.
 */
object TestBridgeCommands {

    // ── Tên extra (khớp cờ của `am broadcast`) ───────────────────────────────────────────────────

    const val EXTRA_CMD = "cmd"
    const val EXTRA_TEXT = "text"
    const val EXTRA_PATH = "path"
    const val EXTRA_PKG = "pkg"
    const val EXTRA_ARG = "name"
    const val EXTRA_FILE = "file"
    const val EXTRA_SLOT = "n"

    /** `--es id <controlId>` — mã một control trong `ControlRegistry` cho lệnh [CTL]. */
    const val EXTRA_ID = "id"

    /** `--ei v <value>` — giá trị chính của control cho lệnh [CTL] (tuỳ chọn; vắng ⇒ mặc định theo kind). */
    const val EXTRA_V = "v"

    /** `--es dev <simpleClass>` — device BYDAuto cho lệnh [HAL] (tuỳ chọn; vắng ⇒ `BYDAutoBodyworkDevice`). */
    const val EXTRA_DEV = "dev"

    /** `--es m <method>` — tên method HAL thô cho lệnh [HAL] (bắt buộc). */
    const val EXTRA_METHOD = "m"

    /** `--es args <csv-ints>` — đối số int (phân tách phẩy) cho lệnh [HAL]. */
    const val EXTRA_HAL_ARGS = "args"

    /** `--es op <get|set>` — kiểu thao tác cho lệnh [HAL] (tuỳ chọn; vắng ⇒ suy theo tiền tố `get`). */
    const val EXTRA_OP = "op"

    /** `--es key <tên khoá>` — khoá prefs cho lệnh [PREFS_SET] (bắt buộc, phải nằm trong [WRITABLE_PREFS_KEYS]). */
    const val EXTRA_KEY = "key"

    /**
     * `--ez auto_confirm true` — **chỉ** có tác dụng khi chế độ kiểm thử đang bật (bản thân cả cầu này cũng vậy).
     *
     * Nó KHÔNG phải một đường tắt "bỏ qua mọi câu hỏi": nó là cách nói *"lượt chạy này do máy điều khiển, không
     * có ai ngồi trước màn để bấm"*. Lệnh chạm mức rủi ro `CONFIRM` mà **không** có cờ này thì bị **từ chối**,
     * không phải im lặng chạy — xem spec §R5.
     */
    const val EXTRA_AUTO_CONFIRM = "auto_confirm"

    // ── Mã lệnh ─────────────────────────────────────────────────────────────────────────────────

    const val SAY = "say"
    const val WAV = "wav"
    const val KWS = "kws"
    const val CAMERA = "camera"
    const val TTS = "tts"
    const val LISTEN = "listen"
    const val PROFILE = "profile"
    const val PROFILES = "profiles"
    const val PRESET = "preset"
    const val SLOT = "slot"
    const val SLOT_CLEAR = "slot_clear"
    const val OPEN = "open"
    const val STATE = "state"
    const val PREFS = "prefs"
    const val REAPPLY = "reapply"
    const val DIAG = "diag"

    /** Bắn MỘT control theo mã registry, đi qua ĐÚNG applier mà một cú chạm ô nút đi (`CarControlPort`). */
    const val CTL = "ctl"

    /**
     * Gọi MỘT method HAL BYDAuto **thô** (đọc getter / ghi named-method) để CHẨN ĐOÁN cơ chế — không đi qua
     * `ControlRegistry`. Sinh ra vì `ctl` chỉ bắn được value đã map (kính: mở=1/đóng=2) nên không đọc được
     * `getWindowPermitState`/`getWindowState` cũng không thử được state khác (STOP=3…). Đúng tinh thần §14: một
     * đầu dò shell-thô trên xe THẬT để chốt cơ chế trước khi mã hoá thành policy. Lượt GHI (`set`) chạm thân xe ⇒
     * đi qua cổng CONFIRM y như `ctl` ([CtlSafetyPolicy] không áp được vì không có control-id, nên cổng nằm ở
     * tầng thi hành `TestBridgeHal`).
     */
    const val HAL = "hal"

    /**
     * Quét MỘT LƯỢT: đọc raw MỌI telemetry (`readRaw`) + mô tả route MỌI control (KHÔNG bắn) → JSON trên thẻ.
     * Thay cho việc bấm tay 187 mục trên xe (owner 2026-09-15). Chỉ-đọc ⇒ không cần confirm. `--es op info|ctl|all`.
     */
    const val SWEEP = "sweep"

    /**
     * V3 · R11(c) — đổ **bảng feature-id thật của chiếc xe này** (`BYDAutoFeatureIds` + `BYDAutoDeviceFeaturesMap`)
     * ra JSON trên thẻ. Chỉ-đọc ⇒ không cần confirm, không đối số.
     *
     * Sinh ra sau [ĐO nguồn fw-dl3 2026-09-16]: hằng feature-id **không cố định** (gán theo `isCanFD`/`isToyota`
     * lúc nạp lớp), nên bản decompile chỉ cho biết các *khả năng* — số THẬT chỉ chiếc xe biết. Một lượt lệnh này
     * đổi *"mỗi dòng bind ngờ vực = một lượt lên xe"* thành *"tra off-car trong tệp JSON"*.
     */
    const val FEATMAP = "featmap"

    /**
     * ═══ [SOÁT Pass 1 · P2] GHI một khoá prefs trong **danh sách trắng** — chỉ chế độ kiểm thử ═══════════
     *
     * `am broadcast … --es cmd prefs_set --es key voice_confirm_ids --es text "control:door"`
     *
     * ## Vì sao lệnh này phải tồn tại
     * 1.66 đổi mặc định của cổng xác nhận sang *"không hỏi gì cả"*, và tập việc-phải-hỏi (`voice_confirm_ids`)
     * do người dùng tích trong Cài đặt. Cầu kiểm thử **không có đường ghi prefs** ⇒ 8 ca `confirm=1` của
     * `voice-cases.tsv` phải bị hạ về 0 và **cả cổng an toàn quan trọng nhất của tính năng mất luôn lớp canh
     * E2E** (ghi lại ở §9 của spec: *"cầu kiểm thử không có đường ghi prefs"*). Một lệnh ghi có danh sách trắng
     * trả lại lớp canh ấy mà không mở một cửa chung vào SharedPreferences.
     *
     * ## Ba ràng buộc, và không cái nào là trang trí
     *  1. **Danh sách trắng cứng** ([WRITABLE_PREFS_KEYS]) — khai ở `:core`, kiểm ngay trong [parse]. Không có
     *     đường ghi *"khoá bất kỳ"*: receiver này `exported=true` (mọi app trên xe bắn vào được — KDoc
     *     `KachiTestBridge`), nên một lệnh ghi tuỳ ý là một đường sửa cấu hình xe cho bất cứ app nào, ngay cả khi
     *     nó vẫn phải qua công tắc chế độ kiểm thử.
     *  2. **Chỉ khoá của đường GIỌNG NÓI + nhãn chip** — năm khoá, tất cả đều đảo lại được bằng một cú chạm trong
     *     Cài đặt. Không có khoá nào chạm tới cast/cụm/phím vô-lăng.
     *  3. **Giá trị là MỘT CHUỖI** cho mọi kiểu (`"1"`/`"0"` cho công tắc, số cho `int`, danh sách ngăn phẩy cho
     *     tập). Tầng thi hành mới biết kiểu thật của từng khoá; nhét ba loại extra vào đây là ba đường phân tích
     *     cho cùng một việc.
     */
    const val PREFS_SET = "prefs_set"

    /**
     * ═══ H2 · ĐỔ NHẬT KÝ LƯỢT NÓI (`voice-log/`) RA MỘT TỆP ZIP TRÊN THẺ ════════════════════════════════════
     *
     * `am broadcast … --es cmd voice_dump` ⇒ lời đáp mang **đường dẫn tệp zip** để `adb pull` về máy soạn thảo.
     *
     * ## Vì sao là một lệnh của cầu, không chỉ một nút trong Cài đặt
     * Nút trong Cài đặt là đường của NGƯỜI trên xe (chụp màn, gửi Zalo — CLAUDE.md §11). Lệnh này là đường của
     * **máy**: một vòng đo E2E nói vài chục câu rồi muốn kéo cả chỗ tiếng ấy về host để chạy lại off-car
     * (`scripts/voice/replay-car-log.py`) — bắt người đo bấm tay giữa vòng là làm hỏng chính phép đo. Cả hai đi
     * qua **một** thân hàm (`VoiceUtteranceLog.exportZip`), không phải hai đường nén.
     *
     * Chỉ-ĐỌC đối với trạng thái XE — nhưng **xuất tiếng cabin (dữ liệu cá nhân) ra `Download/` công khai**, qua một
     * receiver `exported=true` mà app nào trên đầu xe cũng gửi được ⇒ [SCAN §6 1.69, W5] lệnh này **đi qua cổng
     * `auto_confirm`** như mọi lượt ghi thân xe: mức rủi ro ở đây là quyền riêng tư, không phải cơ khí. Không đối số.
     */
    const val VOICE_DUMP = "voice_dump"

    /**
     * ═══ WP7 · CÔNG CỤ "KIỂM TRA TỪNG NÚT XE" QUA adb ═══════════════════════════════════════════════════════
     *
     * `am broadcast … --es cmd captest --es op list|ok|notok|skip|report|clear [--es id <mã>] [--es text <ghi chú>]`
     *
     * ## Vì sao cần đường adb cho một công cụ đã có bề mặt bấm tay
     * UX-OVERHAUL · WP7 đưa bảng bấm tay ([CapTestConsole]) vào sau cổng [DevMode] — nó vẫn còn, và buổi RE vẫn
     * cần nó để **nhìn** (đèn có sáng không, cốp có mở không). Nhưng cái nó KHÔNG làm được là để **script** đi hết
     * 25 mục RE: `op list` trả đủ mã + loại + đã-map-chưa để vòng lặp bash biết phải sweep những gì, `op ok/notok`
     * đóng dấu kết quả ngay sau khi `hal`/`ctl` vừa chạy, `op report` xuất đúng một báo cáo như nút *Xuất* —
     * **cùng một** `CapTestReport.build`, không phải một bộ dựng chữ thứ hai.
     *
     * ## Hai điều cố ý KHÔNG có ở đây
     *  1. **Không có `op run`** — bắn một hành động xe đã có `ctl` (đi đúng applier của một cú chạm) và `hal` (thô).
     *     Thêm `captest run` là đường thứ ba tới cùng một chỗ, và nó sẽ là đường quên mất cổng [CtlSafetyPolicy].
     *  2. **Không cần `auto_confirm`** — cả sáu op chỉ đọc/ghi *nhật ký chấm điểm của chính Kachi*, không chạm xe,
     *     không xuất dữ liệu cá nhân (khác [VOICE_DUMP] — nó nén tiếng cabin nên phải qua cổng).
     */
    const val CAPTEST = "captest"

    /** Op của [CAPTEST] — ASCII, script đọc. `list` là mặc định khi `--es op` vắng. */
    object CapTestOps {
        const val LIST = "list"
        const val OK = "ok"
        const val NOT_OK = "notok"
        const val SKIP = "skip"
        const val REPORT = "report"
        const val CLEAR = "clear"

        /** Ba op đóng dấu một mục ⇒ bắt buộc có `--es id`. */
        val MARKS: Set<String> = setOf(OK, NOT_OK, SKIP)

        val ALL: Set<String> = setOf(LIST, OK, NOT_OK, SKIP, REPORT, CLEAR)
    }

    /**
     * Khoá prefs mà [PREFS_SET] được phép ghi — **danh sách trắng**, xem KDoc [PREFS_SET] ràng buộc (1).
     *
     * Bốn khoá đầu là khoá THEO XE của đường giọng nói (`PrefsVoiceV3.kt` + `Prefs.voiceAskAloud`); khoá
     * `top_strip_labels` là công tắc nhãn chip **theo hồ sơ** (`WorkspacePrefs.setTopStrip`) — đường duy nhất đo
     * được R14 bằng máy thay vì bằng một ảnh chụp màn hình.
     *
     * ## H5 (2026-09-16) — **bốn núm chỉnh bộ nghe**, tất cả đều mặc định = hằng đang chạy
     * `voice_endpoint_silence_ms` · `voice_endpoint_min_speech_ms` ([VoiceEndpointer]) và `voice_beam` ·
     * `voice_hotword_score` ([SherpaModelCatalog]). Chúng vào đây vì đúng câu hỏi chúng sinh ra để trả lời —
     * *"cabin 80 km/h thì 800 ms im là sớm hay muộn"*, *"beam 8 có nghe ra hơn không"* — chỉ đo được bằng cách
     * đổi giá trị **giữa hai lượt `wav`/`listen` trên xe**, tức bằng máy, không phải bằng một lượt build lại APK
     * cho mỗi con số. Mặc định của cả bốn **bằng đúng hằng hôm nay** ⇒ danh sách này dài ra mà hành vi không đổi
     * một ly; và cả bốn vẫn nằm trong đường GIỌNG NÓI, không chạm cast/cụm/phím (ràng buộc (2) của KDoc trên).
     */
    val WRITABLE_PREFS_KEYS: Set<String> = setOf(
        "voice_confirm_ids",
        "voice_ask_aloud",
        "voice_follow_up_ms",
        "voice_mic_source",
        "top_strip_labels",
        "voice_endpoint_silence_ms",
        "voice_endpoint_min_speech_ms",
        "voice_endpoint_floor_cap",
        // Ba núm của Silero VAD — đường ngắt câu CHÍNH từ 1.69 (docs/diagnostics/voice-stream-eval-2026-09-16.md
        // §8). Cùng lý do với ba khoá trên: bộ tham số chốt bằng lưới trên host, còn cabin thật thì chỉ đo được
        // bằng cách đổi số **giữa hai lượt nói** trên xe.
        "voice_vad_threshold",
        "voice_vad_min_speech_ms",
        "voice_vad_min_silence_ms",
        "voice_beam",
        "voice_hotword_score",
        // Tốc độ đọc Piper (owner 2026-09-17 "nói nhanh quá") — chỉnh mức chậm đúng ý trên xe không cần build.
        "voice_tts_speed",
        // owner 2026-09-21 (bản release production) — công tắc GIỮ NHẬT KÝ lượt nói. Vào đây vì ô tích của nó vừa
        // bị gỡ khỏi Cài đặt cùng mọi bề mặt dev/log: không có dòng này thì khoá thành **bất khả chỉnh**, tức dọn
        // bề mặt hoá ra dọn luôn khả năng. Đây cũng là khoá DUY NHẤT của danh sách này không còn đường đảo lại
        // bằng một cú chạm trong Cài đặt (xem ràng buộc (3) ở KDoc trên) — nó vẫn nằm trong đường GIỌNG NÓI và vẫn
        // chỉ ghi được khi chế độ kiểm thử đang mở, nên hai ràng buộc còn lại không đổi.
        "voice_keep_log",
        // Camera theo xi-nhan (findings 2026-09-23) — bật/tắt + đổi phương án LVDS (A–J) test nhanh trên xe.
        "camera_signal_enabled",
        "camera_lvds_option",
    )

    // ── Mã lỗi (ASCII, không dịch) ──────────────────────────────────────────────────────────────

    const val ERR_NO_CMD = "no_cmd"
    const val ERR_UNKNOWN_CMD = "unknown_cmd"

    /** Thiếu một extra bắt buộc — nối thêm TÊN extra để script biết thiếu cái gì, không phải chỉ "sai cú pháp". */
    const val ERR_MISSING = "missing_extra:"
    const val ERR_BAD_SLOT = "bad_slot"
    const val ERR_BAD_PREFS_FILE = "bad_prefs_file"

    /** `--es key` của [PREFS_SET] không nằm trong [WRITABLE_PREFS_KEYS] — nối tên khoá để script biết gõ sai đâu. */
    const val ERR_BAD_PREFS_KEY = "bad_prefs_key:"

    /** `--es op` của [CAPTEST] không thuộc [CapTestOps.ALL] — nối op đã gõ. */
    const val ERR_BAD_OP = "bad_op:"

    /**
     * Một lệnh: cần extra gì, nhận thêm extra gì.
     *
     * [optional] có mặt để [parse] **từ chối extra lạ**? Không — nó chỉ để tài liệu/`help` liệt kê đúng. Từ chối
     * extra lạ sẽ làm mọi script cũ gãy khi cầu thêm một cờ mới, mà lợi ích thì bằng không: extra không ai đọc
     * thì không làm gì cả.
     */
    data class Spec(val name: String, val required: List<String>, val optional: List<String> = emptyList())

    /** Toàn bộ bảng lệnh, theo thứ tự dùng thật (chạy một câu → xem máy hiểu gì → đọc trạng thái → đổi bố cục). */
    val SPECS: List<Spec> = listOf(
        Spec(SAY, listOf(EXTRA_TEXT), listOf(EXTRA_AUTO_CONFIRM)),
        Spec(WAV, emptyList(), listOf(EXTRA_PATH)),
        Spec(KWS, emptyList(), listOf(EXTRA_PATH)),
        Spec(CAMERA, emptyList(), listOf(EXTRA_ARG)),
        Spec(TTS, listOf(EXTRA_TEXT)),
        Spec(LISTEN, emptyList()),
        Spec(STATE, emptyList()),
        Spec(PROFILES, emptyList()),
        Spec(PROFILE, listOf(EXTRA_ARG)),
        Spec(PRESET, listOf(EXTRA_ARG)),
        Spec(SLOT, listOf(EXTRA_SLOT, EXTRA_PKG)),
        Spec(SLOT_CLEAR, listOf(EXTRA_SLOT)),
        Spec(OPEN, listOf(EXTRA_PKG)),
        Spec(PREFS, listOf(EXTRA_FILE)),
        Spec(REAPPLY, emptyList()),
        Spec(DIAG, emptyList()),
        Spec(CTL, listOf(EXTRA_ID), listOf(EXTRA_V, EXTRA_AUTO_CONFIRM)),
        Spec(HAL, listOf(EXTRA_METHOD), listOf(EXTRA_DEV, EXTRA_HAL_ARGS, EXTRA_OP, EXTRA_AUTO_CONFIRM)),
        Spec(SWEEP, emptyList(), listOf(EXTRA_OP)),
        Spec(FEATMAP, emptyList()),
        Spec(VOICE_DUMP, emptyList(), listOf(EXTRA_AUTO_CONFIRM)),
        // `text` là **tuỳ chọn** có chủ ý: vắng ⇒ giá trị rỗng ⇒ *"trả khoá về mặc định"* (tập rỗng / tắt), đúng
        // thứ `trap` của harness cần để dọn sau mỗi ca mà không phải biết mặc định của từng khoá.
        Spec(PREFS_SET, listOf(EXTRA_KEY), listOf(EXTRA_TEXT)),
        // WP7 — `op` tuỳ chọn (vắng ⇒ `list`), `id` chỉ bắt buộc với ba op đóng dấu; phép kiểm đó nằm trong
        // [parse] vì nó phụ thuộc GIÁ TRỊ của một extra khác, thứ mà [Spec.required] không diễn tả được.
        Spec(CAPTEST, emptyList(), listOf(EXTRA_OP, EXTRA_ID, EXTRA_TEXT)),
    )

    /** Tên mọi lệnh — cho tài liệu và cho bài canh "mã lệnh không trùng nhau". */
    val NAMES: List<String> = SPECS.map { it.name }

    private fun specOf(name: String): Spec? = SPECS.firstOrNull { it.name == name }

    /**
     * Phân tích một tập extra thành lệnh.
     *
     * @param extras giá trị đã được tầng Android gỡ khỏi `Intent` — chỉ ba kiểu: `String` · `Int` · `Boolean`.
     *   Nhận `Map` chứ không nhận `Intent` chính là chỗ khiến hàm này kiểm được off-device.
     * @param prefsFiles tên tệp prefs được phép ĐỌC. Truyền vào chứ không viết cứng: danh sách thật nằm ở
     *   [com.byd.clusternav.launcher.SettingsCatalog] và nó còn dài ra; chép lại ở đây là bản sao thứ hai.
     */
    fun parse(extras: Map<String, Any?>, prefsFiles: Set<String>): TestBridgeParse {
        val name = (extras[EXTRA_CMD] as? String)?.trim().orEmpty()
        if (name.isEmpty()) return TestBridgeParse.Err(ERR_NO_CMD)
        val spec = specOf(name) ?: return TestBridgeParse.Err(ERR_UNKNOWN_CMD)

        spec.required.forEach { key ->
            val v = extras[key]
            val missing = when (key) {
                EXTRA_SLOT -> v !is Int
                else -> (v as? String)?.isNotBlank() != true
            }
            if (missing) return TestBridgeParse.Err(ERR_MISSING + key)
        }

        val slot = extras[EXTRA_SLOT] as? Int ?: 0
        // Trần TRÊN không kiểm ở đây có chủ ý: chỉ tầng thi hành biết bố cục đang dùng có mấy ô (bố cục tự vẽ đổi
        // được giữa hai lệnh). Cùng phân công với `VoiceDispatcher.runOpenApp`.
        if (EXTRA_SLOT in spec.required && slot < 1) return TestBridgeParse.Err(ERR_BAD_SLOT)

        val file = (extras[EXTRA_FILE] as? String)?.trim().orEmpty()
        if (EXTRA_FILE in spec.required && file !in prefsFiles) return TestBridgeParse.Err(ERR_BAD_PREFS_FILE)

        // Danh sách trắng kiểm ở TẦNG PHÂN TÍCH, không ở tầng thi hành: một khoá lạ không bao giờ được dựng
        // thành một lệnh "chạy được" rồi mới bị từ chối — xem KDoc [PREFS_SET] ràng buộc (1).
        val key = (extras[EXTRA_KEY] as? String)?.trim().orEmpty()
        if (name == PREFS_SET && key !in WRITABLE_PREFS_KEYS) return TestBridgeParse.Err(ERR_BAD_PREFS_KEY + key)

        val op = (extras[EXTRA_OP] as? String).orEmpty().trim().lowercase()
        // WP7 · [CAPTEST]: op lạ bị chặn ở TẦNG PHÂN TÍCH (cùng luật danh sách trắng của `prefs_set`) — gõ sai
        // `--es op mark` mà lệnh vẫn trả `ok:true` thì script đọc thành "đã đóng dấu" trong khi không có gì được ghi.
        val cap = if (name == CAPTEST) op.ifEmpty { CapTestOps.LIST } else op
        if (name == CAPTEST) {
            if (cap !in CapTestOps.ALL) return TestBridgeParse.Err(ERR_BAD_OP + cap)
            val id = (extras[EXTRA_ID] as? String)?.trim().orEmpty()
            if (cap in CapTestOps.MARKS && id.isEmpty()) return TestBridgeParse.Err(ERR_MISSING + EXTRA_ID)
        }

        return TestBridgeParse.Ok(
            TestBridgeCommand(
                name = name,
                text = (extras[EXTRA_TEXT] as? String).orEmpty(),
                path = (extras[EXTRA_PATH] as? String).orEmpty().trim(),
                pkg = (extras[EXTRA_PKG] as? String).orEmpty().trim(),
                arg = (extras[EXTRA_ARG] as? String).orEmpty().trim(),
                file = file,
                slot = slot,
                id = (extras[EXTRA_ID] as? String).orEmpty().trim(),
                // `as? Int` giữ nguyên null khi `--ei v` vắng ⇒ tầng thi hành phân biệt "không truyền" với `0`.
                v = extras[EXTRA_V] as? Int,
                autoConfirm = extras[EXTRA_AUTO_CONFIRM] as? Boolean ?: false,
                dev = (extras[EXTRA_DEV] as? String).orEmpty().trim(),
                method = (extras[EXTRA_METHOD] as? String).orEmpty().trim(),
                halArgs = (extras[EXTRA_HAL_ARGS] as? String).orEmpty().trim(),
                // `cap` thay `op` thô: nó đã điền mặc định `list` cho [CAPTEST] (tầng thi hành không phải nhớ lại
                // mặc định lần thứ hai — đúng luật một-chỗ-quyết-định của dự án). Lệnh khác: `cap == op`.
                op = cap,
                key = key,
            ),
        )
    }
}
