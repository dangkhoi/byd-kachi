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
     *
     * ## V1.1 — [app] là **mã đích** trong [VoiceAppTargets], không phải tên gói
     * Owner 2026-09-14 hỏi *"dẫn đường bằng gmaps, vietmap, waze"* ⇒ câu nói có quyền chọn app. Nhưng `:core`
     * KHÔNG được biết tên gói nào (CLAUDE.md §7), và nhãn app thì đổi theo bản cài. Mã đích (`gmaps`/`waze`/
     * `vietmap`) là lớp ở giữa: nó ổn định, tra được bằng test thuần, và bảng [VoiceAppTargets] là chỗ DUY NHẤT
     * biết mã ấy ứng với gói nào + mở bằng ý-định nào.
     */
    data class Nav(val query: String, val app: String? = null) : VoiceIntent

    /**
     * Dẫn đường tới một nơi **ĐÃ LƯU trong sổ địa chỉ của hồ sơ** (*"về nhà"* · *"đến công ty"* · *"đi &lt;nhãn&gt;"*).
     *
     * Spec `docs/specs/kachi-voice-addresses.html` R2. Khác [Nav] ở đúng chỗ quan trọng nhất: đây là **tập ĐÓNG**
     * — nhãn đến từ sổ mà chính người dùng đã gõ, không từ nhận dạng tự do. Vì vậy nó cũng không đi qua cổng hỏi
     * lại của từ vựng mở (xem [VoiceRiskTable.of]).
     *
     * ## Vì sao mang **nhãn**, không mang địa chỉ/toạ độ
     * Một ý định là thứ đi qua hàng đợi: nó được dựng lúc phân tích và thi hành sau đó (màn thử hiện *"đã hiểu
     * là…"* rồi mới chạy; câu ghép chạy từng vế; hộp xác nhận chen vào giữa). Chép nội dung sổ vào ý định là
     * dựng **bản sao thứ hai** của một thứ lưu bền — đúng bẫy dự án đã trả giá bốn lần — và bản sao ấy sẽ cũ
     * đúng vào lúc người dùng vừa sửa địa chỉ xong. Nhãn là thứ **duy nhất** ổn định giữa hai lượt, và tầng thi
     * hành tra sổ bản MỚI.
     *
     * ⚠ Trường tên là [placeName], **không phải `label`** — cùng lý do [OpenApp.appName] và
     * `VoiceAppIntents.Coords.place`: `LauncherI18nContractTest.tang ve khong doc nhan GOC cua core` quét mọi lần
     * đọc `.label` ở `:app`, mà chuỗi này do **người dùng gõ** (không phải nhãn đã dịch của một bộ đăng ký) nên
     * nó không thuộc diện đó. Đặt tên khác để bài canh kia khỏi phải mang thêm một mục loại trừ.
     *
     * @property placeName tên để tra `SavedPlaces.find`; có thể là tên CHUẨN (`VoicePlaces.HOME`) khi người dùng
     *   chưa lưu gì — ca đó tầng thi hành nói thẳng *"chưa lưu"* (R4), không im lặng.
     * @property app **mã đích** trong [VoiceAppTargets] khi câu nêu *"bằng &lt;app&gt;"*; `null` = để tầng thi hành
     *   chọn theo **dữ liệu đang có** của mục (có toạ độ hay không — R3).
     */
    data class NavigateSaved(val placeName: String, val app: String? = null) : VoiceIntent

    /**
     * Điều khiển phát nhạc. [query] chỉ có nghĩa với [VoiceMediaOp.QUERY] (tên bài/ca sĩ/thể loại — từ vựng mở).
     *
     * @property app **mã đích** trong [VoiceAppTargets] khi câu có nêu *"bằng &lt;app&gt;"* (`ytmusic` · `youtube` …),
     *   `null` = *"app nào cũng được"* ⇒ tầng thi hành tự chọn (phiên nhạc đang chạy, rồi tới app đã cài).
     *   ⚠ Là **mã đích**, KHÔNG phải nhãn app của hệ thống: nhãn thì đổi theo ngôn ngữ máy và theo bản cài, còn
     *   mã thì là hợp đồng giữa `:core` và bảng đích — thứ duy nhất viết được test off-car.
     */
    data class Media(val op: VoiceMediaOp, val query: String = "", val app: String? = null) : VoiceIntent

    /**
     * Mở một ứng dụng theo TÊN người dùng nói (tập đóng: tên lấy từ danh sách app đã cài, truyền vào lúc phân tích).
     *
     * ⚠ Trường tên là `appName`, **không phải `label`**: `LauncherI18nContractTest.tang ve khong doc nhan GOC cua
     * core` quét mọi lần đọc `.label` ở `:app` để bắt chỗ dùng nhãn GỐC (luôn tiếng Việt) thay cho `displayLabel`.
     * Tên app thì do `PackageManager` cấp — hệ thống đã dịch sẵn, không thuộc diện đó — nên đặt tên khác để bài canh
     * kia khỏi phải mang thêm một mục loại trừ, tức khỏi phải mở thêm một lỗ.
     *
     * ## V1.1 — [slot] = **số ô người dùng NÓI** (1-based), `null` khi câu không nêu ô
     * Owner 2026-09-14: *"có voice command mở youtube vào ô số 2 được không"*. Số ở đây giữ nguyên như người ta
     * nói (*"ô số hai"* ⇒ `2`), **không** đổi sang chỉ số mảng ở `:core` và **không** kẹp về số ô đang có:
     *  • đổi sang 0-based tại đây thì mọi câu trả lời (*"bố cục hiện chỉ có 4 ô"*) phải cộng lại 1 — hai phép
     *    quy đổi ngược chiều nằm ở hai tầng là chỗ sinh lỗi lệch-một kinh điển;
     *  • kẹp tại đây thì *"mở youtube vào ô số chín"* lặng lẽ thành ô 6 — máy **làm một việc khác** việc được
     *    bảo, đúng họ lỗi mà `VoiceLexicon.VI_TENS_SHORT` đã phải chữa. Số ô thật chỉ tầng biết-bố-cục mới
     *    biết (`EffectiveLayout.slotCount`), nên nó kiểm và nó nói ra.
     *
     * ## [appKey] — **mã đích** khi câu gọi app bằng CÁCH NÓI TIẾNG VIỆT, không bằng nhãn hệ thống
     * [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L6 (t45): *"mở bản đồ"* → `Unknown`, trong khi
     * *"mở Maps"* chạy — vì nhãn app do `PackageManager` cấp và trên máy đó nó là tiếng Anh. Nhãn thật vẫn được
     * **ưu tiên**; chỉ khi không nhãn nào khớp thì [VoiceIntentParser] mới tra [VoiceSynonyms.APP_TARGETS] và
     * đặt mã vào đây. Mang **mã** chứ không mang tên gói: `:core` không được biết gói nào (CLAUDE.md §7), và
     * [appName] lúc đó là nhãn của bảng đích (*"Google Maps"*) nên câu trả lời vẫn đọc được.
     *
     * `null` = câu đã khớp một nhãn app thật ⇒ tầng thi hành tra [appName] như cũ.
     */
    data class OpenApp(val appName: String, val slot: Int? = null, val appKey: String? = null) : VoiceIntent

    /**
     * L7 — đổi **bố cục màn chính** (*"bố cục 2 cột"*, *"đổi sang bố cục 4 ô"*).
     *
     * Mang thẳng [com.byd.clusternav.launcher.LayoutPreset] chứ không mang một chuỗi: đây là một **tập ĐÓNG**
     * đã có sẵn ở `:core`, nên `when` trên nó exhaustive và không có ca *"tên bố cục lạ"* nào để tầng thi hành
     * phải đoán. Khác hẳn [OpenApp]/[NavigateSaved] — hai cái đó mang chuỗi vì tập của chúng **động**.
     *
     * ⚠ Cố ý KHÔNG mang bố cục **tự vẽ**: nó là dữ liệu của người dùng, không có tên để gọi. Nói một bố cục sẵn
     * sẽ BỎ bố cục tự vẽ — đúng như bấm chip bố cục ở Cài đặt (`KachiHomeActivity.selectPreset`), vì đó là cùng
     * một đường, không phải một đường thứ hai (KDoc [com.byd.clusternav.launcher.VoiceDispatcher]).
     */
    data class Layout(val preset: com.byd.clusternav.launcher.LayoutPreset) : VoiceIntent

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

    /**
     * *"đóng/tắt/dừng &lt;app&gt;"* — hiểu đúng câu, nhưng **chưa làm được**.
     *
     * [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L1 (t41/t42): `đóng YouTube` trước đây ra
     * `OpenApp` ⇒ máy **MỞ** app đó, tức làm đúng việc ngược lại. Nhánh APP của [VoiceIntentParser] dựng ý định
     * mà không xét động từ (mọi nhánh khác đều xét).
     *
     * Vì sao là một lý do RIÊNG chứ không phải [MISMATCH]: câu *"đóng YouTube"* hoàn toàn hợp lệ với người nói,
     * thứ thiếu là **cơ chế** phía dưới. Đóng một app thật (`am force-stop` / `am task`) là **cơ chế mới** ⇒
     * CLAUDE.md §14 tầng 1 (đo bằng shell thô trên xe) trước khi viết một dòng `:core` nào — chưa đo thì câu
     * trả lời phải nói thẳng là chưa làm được, không được mở app ra.
     */
    APP_CLOSE,

    /**
     * Câu ghép có một vế **không hiểu được** và cả câu đã được hiểu theo cách khác ⇒ vế đó bị bỏ.
     *
     * [ĐO] cùng tài liệu §3 L5: *"mở cửa và đèn đọc"* chỉ ra **một** ý định `Bật Đèn đọc`, không câu nào nói
     * rằng vế *"mở cửa"* đã bị bỏ — người lái tưởng cả hai việc đã chạy. Ý định này đi **kèm** ý định thật (luôn
     * đứng sau nó) để tầng trả lời nói thêm đúng một dòng.
     */
    DROPPED_CLAUSE,
}
