package com.byd.clusternav.launcher.voice

/**
 * ═══ V1 · Ý ĐỊNH rút ra từ MỘT câu chữ ════════════════════════════════════════════════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. Spec `docs/specs/kachi-voice-command.html` R1.
 *
 * ## Vì sao tầng này CHỈ nhận CHỮ, không nhận tiếng
 * [ĐO] `docs/diagnostics/kiki-car-RE-2026-09-14.md` §2.1: chính Kiki Car (đội trợ lý tiếng Việt mạnh nhất thị
 * trường) **không** chạy nổi ASR mở tại máy trên lớp phần cứng đầu xe — họ đẩy hết lên mây, tại máy chỉ còn wake
 * word 4 token. Ta không có mây, mà điều khiển xe thì không được phụ thuộc 4G ⇒ bài toán phải **thu hẹp về tập
 * đóng**. Nhưng ngân sách mic/ASR/TTS trên xe owner thì **chưa đo** (playbook §2.14 + K1–K3 còn 🔲), nên theo
 * CLAUDE.md §14 tầng 1 chưa xanh ⇒ **chưa được viết một dòng mic/ASR/TTS nào**. Thứ viết được ngay, và viết xong
 * thì không phải viết lại dù tầng tiếng về sau là Vosk hay sherpa-onnx hay bàn phím, là **chữ → hành động**.
 *
 * ## Vì sao là `sealed` chứ không phải một `data class` có mười trường nullable
 * Mỗi nhánh mang **đúng** thứ nó cần, và `when` trên nó là **exhaustive** ⇒ thêm một loại ý định mà quên nối dây ở
 * `:app` thì **không biên dịch được**. Đó là cùng cơ chế đã cứu màn Cài đặt khỏi "trang trắng im lặng"
 * (`SettingsSections.build`) — ở đây hậu quả của một nhánh bị quên còn tệ hơn: người lái nói một câu và **không có
 * gì xảy ra, cũng không có gì báo**.
 */
sealed interface VoiceIntent {

    /**
     * Bấm một nút trong [com.byd.clusternav.launcher.ControlRegistry].
     *
     * @property id mã nút (`lock`, `fan`, `win_lf`…).
     * @property value giá trị TUYỆT ĐỐI đã tính xong theo [com.byd.clusternav.launcher.ControlKind]: TOGGLE/COVER
     *   1 hoặc 0 · SELECT chỉ số 0-based · STEP giá trị đích. `null` = câu không nêu đích (xem [relative]).
     * @property relative số BƯỚC tương đối khi câu nói *"tăng/giảm"* mà không nêu số (±1, hoặc ±n với *"tăng 2 nấc"*).
     *   0 = không phải lệnh tương đối.
     *
     * ## Vì sao phải có [relative] chứ không quy về [value] ngay tại đây
     * *"Tăng gió"* chỉ có nghĩa khi biết gió **đang** ở mức nào — mà mức đang dùng nằm ở `ControlTileState.shared`
     * (`:app`), không ở `:core`. Bộ phân tích tự bịa một mốc (vd lấy `ControlDef.value` mặc định) sẽ cho ra *"đặt
     * gió = 5"* trong khi xe đang ở 7, tức **giảm** đúng lúc người ta bảo tăng. Giữ nguyên ý *"đi lên một nấc"* và
     * để tầng biết-trạng-thái cộng vào là cách duy nhất không nói dối.
     */
    data class Control(val id: String, val value: Int? = null, val relative: Int = 0) : VoiceIntent

    /** Chạy một gói lệnh của [com.byd.clusternav.launcher.ActionMacros]. */
    data class Macro(val id: String) : VoiceIntent

    /** Hành động của chính launcher ([com.byd.clusternav.launcher.LauncherActions]) — ngăn kéo / Cài đặt. */
    data class Launcher(val id: String) : VoiceIntent

    /** Đổi hồ sơ tài xế. [name] là tên GỐC (khoá lưu bền), không phải nhãn đã dịch — xem `ProfileNames`. */
    data class Profile(val name: String) : VoiceIntent

    /**
     * Đọc một datum của [com.byd.clusternav.launcher.TelemetryRegistry].
     *
     * @property aloud `true` khi người dùng nói *"đọc"* (chờ nghe), `false` khi nói *"xem/hiện"* (chờ nhìn).
     *   Hôm nay cả hai đều ra **chữ** (chưa có TTS — R8), nhưng ý định thì khác nhau và phải giữ lại: ngày TTS bật
     *   lên, thông tin này đã có sẵn thay vì phải phân tích lại câu.
     */
    data class Read(val datumId: String, val aloud: Boolean = false) : VoiceIntent

    /**
     * Dẫn đường tới [query] — **từ vựng MỞ**, không nằm trong tập đóng của Kachi.
     *
     * [ĐO] RE Kiki §8.2: ranh giới đã chốt (phương án C) là *"điểm đến do Kiki lo"*. Kachi giữ ý định này để còn
     * **mở đúng app dẫn đường** và nói ra rằng điểm đến chưa được chuyển giao — im lặng hoặc "không hiểu" đều sai.
     */
    data class Nav(val query: String) : VoiceIntent

    /** Điều khiển phát nhạc. [query] chỉ có nghĩa với [VoiceMediaOp.QUERY] (tên bài/ca sĩ/thể loại — từ vựng mở). */
    data class Media(val op: VoiceMediaOp, val query: String = "") : VoiceIntent

    /**
     * Mở một ứng dụng theo TÊN người dùng nói (tập đóng: tên lấy từ danh sách app đã cài, truyền vào lúc phân tích).
     *
     * ⚠ Trường tên là `appName`, **không phải `label`**: `LauncherI18nContractTest.tang ve khong doc nhan GOC cua
     * core` quét mọi lần đọc `.label` ở `:app` để bắt chỗ dùng nhãn GỐC (luôn tiếng Việt) thay cho `displayLabel`.
     * Tên app thì do `PackageManager` cấp — hệ thống đã dịch sẵn, không thuộc diện đó — nên đặt tên khác để bài canh
     * kia khỏi phải mang thêm một mục loại trừ, tức khỏi phải mở thêm một lỗ.
     */
    data class OpenApp(val appName: String) : VoiceIntent

    /** Không hiểu. [text] giữ nguyên câu gốc để màn thử + nhật ký còn nói được *"không hiểu CÁI GÌ"*. */
    data class Unknown(val reason: VoiceUnknownReason, val text: String) : VoiceIntent
}

/** Việc cần làm với nhạc. [QUERY] = "mở bài/nhạc <tên>" — từ vựng mở, Kachi KHÔNG tự làm (RE Kiki §8.2). */
enum class VoiceMediaOp { PLAY, PAUSE, NEXT, PREV, QUERY }

/**
 * Vì sao không hiểu — để câu báo lỗi nói được điều hữu ích thay vì *"không hiểu"* chung chung.
 *
 * [ĐO] mẫu UX của Kiki (§5 RE): app đó phát hiện người dùng **vật lộn** (nói lại nhiều lần) rồi mới đổi câu gợi ý.
 * Không phân loại được lý do thì không bao giờ làm được việc đó.
 */
enum class VoiceUnknownReason {
    /** Câu rỗng / chỉ có từ đệm. */
    EMPTY,

    /** Có đối tượng nhưng không có động từ nào nhận ra được. */
    NO_VERB,

    /** Có động từ nhưng không khớp đối tượng nào trong sáu bộ đăng ký. */
    NO_OBJECT,

    /** Động từ và đối tượng không đi được với nhau (vd *"đọc"* một nút không có datum). */
    MISMATCH,

    /** Thuộc **từ vựng mở** (bài hát/điểm đến/hỏi đáp) — Kachi cố ý không làm offline (phương án C). */
    OPEN_VOCAB,
}
