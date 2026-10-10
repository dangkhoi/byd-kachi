package com.byd.clusternav.launcher.testbridge

/**
 * ═══ T-BRIDGE · MỘT LỆNH ĐÃ PHÂN TÍCH ════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` R3. Thuần Kotlin ⇒ kiểm off-device.
 *
 * Soát 2.99 Pass 15 (CLAUDE.md §4.1 trần 500 dòng — tệp này 516 sau op `setarr`/`setisa`): danh mục lệnh + bộ phân tích
 * ([TestBridgeCommands]) dời sang `TestBridgeCommands.kt`; tệp này chỉ còn HAI kiểu dữ liệu *"một lệnh đã phân tích"*
 * (vai khác *"hiểu một lệnh"*). Không đổi tên/API — mọi chỗ gọi `TestBridgeCommands.*` giữ nguyên.
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
 * @property halArgs đối số int cho `hal` op scalar (`set`/`setev`), phân tách phẩy (`"1"` · `"1,2"`). Getter 0-đối để rỗng.
 * @property data payload MẢNG int cho `hal` op `setarr`/`setisa` — xem KDoc [TestBridgeCommands.EXTRA_DATA].
 * @property op `get` (đọc getter) hay `set` (ghi named-method) cho `hal`; rỗng ⇒ suy theo tiền tố `get` của
 *   [method]. `set` là lượt GHI thân xe nên đi qua đúng cổng CONFIRM như `ctl` (cần `--ez auto_confirm true`).
 * @property key tên khoá prefs cho lệnh `prefs_set`, đã kiểm nằm trong [TestBridgeCommands.WRITABLE_PREFS_KEYS].
 *   Giá trị đi trong [text] (một chuỗi cho MỌI kiểu — xem KDoc [TestBridgeCommands.PREFS_SET]).
 * @property tail số dòng cuối của nhật ký cho lệnh `a11ylog` / `ctllog`, **đã kẹp** ở [TestBridgeCommands.parse]
 *   (đọc từ `--ei n`, xem [TestBridgeCommands.a11yLogTail]); `0` với mọi lệnh khác. Trường riêng chứ không mượn
 *   [slot]: `slot` là số Ô 1-based, và một tầng thi hành đọc `cmd.slot` ra số dòng là chỗ đọc nhầm không ai thấy.
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
    val data: String = "",
    val op: String = "",
    val key: String = "",
    val tail: Int = 0,
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
