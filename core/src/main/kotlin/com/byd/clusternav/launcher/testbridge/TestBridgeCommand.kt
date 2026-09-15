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

    // ── Mã lỗi (ASCII, không dịch) ──────────────────────────────────────────────────────────────

    const val ERR_NO_CMD = "no_cmd"
    const val ERR_UNKNOWN_CMD = "unknown_cmd"

    /** Thiếu một extra bắt buộc — nối thêm TÊN extra để script biết thiếu cái gì, không phải chỉ "sai cú pháp". */
    const val ERR_MISSING = "missing_extra:"
    const val ERR_BAD_SLOT = "bad_slot"
    const val ERR_BAD_PREFS_FILE = "bad_prefs_file"

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
                op = (extras[EXTRA_OP] as? String).orEmpty().trim().lowercase(),
            ),
        )
    }
}
