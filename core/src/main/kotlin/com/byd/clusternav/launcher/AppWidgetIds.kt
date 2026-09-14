package com.byd.clusternav.launcher

/**
 * THU HỒI ID WIDGET bên thứ ba (P6 · T4) — phép tính **thuần** (`:core`, cấm `android.*`) nên kiểm được off-car.
 *
 * ## Vì sao việc này phải có một chỗ riêng, và phải thuần
 * Mỗi widget bên thứ ba chiếm một **id do nền tảng cấp** (`allocateAppWidgetId`). Id đó KHÔNG tự chết khi ô đổi nội
 * dung: nhà cung cấp vẫn coi nó là một ô đang sống và **vẫn đẩy cập nhật** cho nó (VietMap đẩy mỗi 100 ms —
 * `updatePeriodMillis=100` đo được trên máy ảo). Không gọi `deleteAppWidgetId` thì mỗi lần người dùng đổi ô là
 * một id rác nữa, tiêu pin và bộ nhớ **mãi mãi**, và tệ nhất là **im lặng** — không có gì trên màn hình cho thấy.
 *
 * Phép "id nào không còn ai dùng" là **phép tập hợp**, không phải việc của Android. Để nó ở đây thì:
 *  - kiểm được đủ ca off-car (kể cả ca **đổi chỗ hai ô**, thứ dễ tính sai nhất);
 *  - phía Android chỉ còn đúng một việc: gọi `deleteAppWidgetId` cho từng số nhận được.
 *
 * ## ⚠ Ca dễ sai nhất — nhớ kỹ
 * **Kéo-thả đổi chỗ hai ô**: cùng một id chuyển từ ô 0 sang ô 1. Nếu tính "orphan" theo *từng ô* (ô 0 trước có id X,
 * giờ không có ⇒ thu hồi X) thì ta **xoá đúng cái widget vừa kéo** — nó biến thành ô trống ngay sau khi thả. Phải
 * so theo **TẬP HỢP toàn bố cục**, không theo vị trí. [orphaned] làm đúng thế và có bài canh riêng cho ca này.
 *
 * ## ⚠⚠ Và ca đã LỌT hai lần: "còn dùng" bị hỏi HẸP HƠN sự thật
 * Cả hai lỗi mất-widget-vĩnh-viễn của tính năng này đều KHÔNG nằm ở nhánh code mà ở **phạm vi dữ liệu** được hỏi:
 *  1. hỏi bố cục mà không hỏi **dữ liệu cảnh đời cũ chưa chuyển** ⇒ widget trong cảnh ra thẻ "app đã bị gỡ" (KDoc
 *     [idsInLegacyScenes]);
 *  2. hỏi hồ sơ đang dùng mà không hỏi **hồ sơ khác** ⇒ đổi hồ sơ là xoá widget của hồ sơ kia (KDoc [idsInStored]).
 *
 * Hai lần cùng một hình dạng: bài canh chứng minh *"có gọi đúng hàm"* nhưng không hỏi *"hàm có thấy đủ dữ liệu"*.
 * Vì thế mọi vế của "còn dùng" gom về ĐÚNG MỘT hàm ([used]) và nó nhận [HomeUiState] — kiểu dữ liệu mang đủ ba vế,
 * nên phạm vi không thể bị thu hẹp bằng cách quên một tham số.
 */
object AppWidgetIds {

    /**
     * Mọi id widget bên thứ ba đang được [state] dùng — kể cả ở những ô **đang ẩn** theo bố cục.
     *
     * ⚠ Cố ý quét `slots` (đủ trần ô) chứ KHÔNG quét `visibleSlots()`: ô ẩn vẫn **nhớ** nội dung (đó là thiết kế của
     * `WorkspaceState`), nên id ở ô ẩn vẫn đang được dùng. Quét theo ô đang hiện sẽ thu hồi id của ô ẩn ⇒ đổi bố cục
     * từ 6 ô về 2 ô rồi quay lại là **mất sạch** widget ở 4 ô kia.
     */
    fun idsIn(state: WorkspaceState): Set<Int> =
        state.slots.filterIsInstance<SlotContent.AppWidget>().map { it.widgetId }.toSet()

    /**
     * Mọi id mà **dữ liệu CẢNH đời cũ còn nằm trên đĩa** đang giữ (chuỗi khoá `<hồ sơ>__scenes`, dạng
     * `SceneBook.encode`). S4 · R1 đã bỏ khái niệm cảnh, nhưng chuỗi đó vẫn nằm trên đĩa của xe đang chạy **cho tới
     * khi** [ScenesMigration] chuyển xong — nên trong cửa sổ đó id trong cảnh vẫn phải tính là *"còn dùng"*.
     *
     * ## ⚠⚠ [ĐO] Thiếu hàm này thì "widget trong cảnh" chết ngay lần khởi động đầu tiên của bản mới
     * Cảnh lưu **nguyên nội dung ô**, kể cả ô widget bên thứ ba — tức nó lưu chính con số id. Nhưng id là thứ nền
     * tảng cấp và **không cấp lại được**: `deleteAppWidgetId(651)` rồi thì không có cách nào làm cho 651 sống lại.
     * Đo trên `emulator-5554` (bản trước bản vá): đặt widget đồng hồ vào ô ⇒ lưu cảnh *CoWidget* ⇒ đổi ô đó sang một
     * app ⇒ `dumpsys appwidget` cho thấy **id 651 đã bị xoá** ⇒ gọi lại cảnh *CoWidget* thì ô hiện đúng câu
     * *"widget của com.google.android.deskclock không còn — app đã bị gỡ hoặc bị tắt"* trong khi app **vẫn còn nguyên
     * trên máy**. Tức launcher tự xoá widget của người dùng rồi báo sai nguyên nhân.
     *
     * ⇒ "Còn ai dùng" phải tính trên **cả** bố cục đang sống **lẫn** dữ liệu cảnh chưa chuyển — xem [idsInStored].
     * Sau khi [ScenesMigration] chạy, mỗi cảnh đã thành một hồ sơ nên id của nó đi vào vế *"hồ sơ tài xế khác"* của
     * [used] một cách tự nhiên; hàm này chỉ còn là lưới an toàn cho dữ liệu chưa chuyển.
     */
    fun idsInLegacyScenes(scenesRaw: String?): Set<Int> =
        ScenesMigration.parse(scenesRaw).flatMapTo(mutableSetOf()) { idsIn(it.workspace) }

    /**
     * Mọi id nằm trong dữ liệu ĐÃ LƯU của **một** hồ sơ — đọc từ chính hai chuỗi mà `WorkspacePrefs` giữ.
     *
     * ## ⚠⚠ [SOÁT P0-1] Vì sao phép này phải tồn tại, và vì sao nó THUẦN
     * Id widget là thứ nền tảng cấp **cho một HOST**, không cho một hồ sơ tài xế. Nhưng [HomeUiState] chỉ mang dữ
     * liệu của **hồ sơ đang dùng** (`WorkspacePrefs.key()` = `"<hồ sơ>__<hậu tố>"`), nên "còn ai dùng" tính trên
     * state là một câu trả lời **hẹp hơn sự thật**. Hậu quả đo được trên `emulator-5554` (bản trước bản vá): đặt
     * widget đồng hồ ở hồ sơ *Mặc định* ⇒ id 654; **đổi sang hồ sơ *Vợ*** ⇒ 654 biến khỏi host; quay lại *Mặc định*
     * ⇒ ô hiện *"widget của com.google.android.deskclock không còn — app đã bị gỡ hoặc bị tắt"* trong khi app **vẫn
     * còn cài** (`pm list packages` có, `pm list packages -d` đếm 0). `force-stop` rồi mở lại vẫn vậy = **vĩnh viễn**.
     *
     * Đường thứ hai còn nặng hơn vì **không cần ai chạm gì**: [unused] lúc khởi động lấy `allocated` =
     * `host.appWidgetIds` (mọi id của host = mọi hồ sơ) trừ đi "còn dùng" (chỉ hồ sơ đang dùng) ⇒ chỉ cần nổ máy với
     * hồ sơ A là widget của hồ sơ B chết.
     *
     * Việc giải mã (chỗ **có thể sai**) để ở đây, thuần, thay vì viết trong `WorkspacePrefs` (cần `Context` ⇒ không
     * kiểm được off-car). Phía Android chỉ còn việc đọc chuỗi ra khỏi SharedPreferences.
     *
     * @param slotRaw chuỗi đã lưu của từng ô (`slot_0`..`slot_N`), đúng dạng [SlotCodec].
     * @param scenesRaw chuỗi sổ cảnh ĐỜI CŨ còn trên đĩa (dạng `SceneBook.encode`); `null`/rỗng = đã chuyển xong
     *   hoặc hồ sơ chưa từng có cảnh nào. Xem [idsInLegacyScenes].
     */
    fun idsInStored(slotRaw: List<String>, scenesRaw: String?): Set<Int> {
        val slots = slotRaw.map { SlotCodec.decode(it) }
            .filterIsInstance<SlotContent.AppWidget>()
            .map { it.widgetId }
        // Con trỏ cảnh khởi động không giữ id nào ⇒ không cần truyền vào, và truyền chỉ thêm một đường sai.
        return slots.toSet() + idsInLegacyScenes(scenesRaw)
    }

    /**
     * Id còn **ĐANG ĐƯỢC DÙNG** theo [state]: bố cục đang sống ∪ **mọi hồ sơ tài xế KHÁC** (S4 · R1: vế "mọi cảnh đã
     * lưu" biến mất cùng khái niệm cảnh — cảnh nay LÀ hồ sơ, nên nó đã nằm trong vế thứ hai).
     *
     * Đây là **định nghĩa duy nhất** của "còn dùng" trong toàn bộ tính năng. [orphaned] và [unused] đều đọc nó, nên
     * không có đường nào trả lời câu hỏi đó theo một cách hẹp hơn — và đó là chủ ý: **hai** phiên bản hẹp hơn đã
     * từng tồn tại ở đây và mỗi lần đều là một lỗi mất-widget-vĩnh-viễn (chỉ-xét-bố-cục ⇒ KDoc [idsIn]; chỉ-xét-hồ-
     * sơ-đang-dùng ⇒ KDoc [idsInStored]). Sửa ở đúng một chỗ này bịt cả [orphaned] lẫn [unused].
     *
     * ⚠ Vế thứ ba đến từ [HomeUiState.widgetIdsOtherProfiles], tức **dữ liệu phải có sẵn trong state**. Đó là chủ ý:
     * chốt bằng KIỂU chứ không bằng lời nhắc — hai chỗ gọi (`reclaim`/`sweep`) nhận `HomeUiState` nên không có cách
     * nào hỏi câu này mà "quên" vế đó. Bù lại, chỗ dựng state (`PrefsWorkspaceRepository.load`) **phải** điền nó, và
     * có bài canh riêng đòi đúng điều đó — mặc định `emptySet()` chỉ để test khỏi phải khai khi ca đó không liên quan.
     */
    fun used(state: HomeUiState): Set<Int> =
        idsIn(state.workspace) + state.widgetIdsOtherProfiles

    /**
     * Id có ở [old] mà KHÔNG còn ở [new] ⇒ phải gọi `deleteAppWidgetId`.
     *
     * Nhận [HomeUiState] (không phải [WorkspaceState]) vì phép này phải thấy **cả** id của hồ sơ khác: nhờ vậy một
     * lời gọi phủ đủ các đường đổi — đổi/xoá nội dung ô và **đổi hồ sơ** (ca sau không đổi ô nào của hồ sơ cũ, nên
     * bản chỉ-xét-bố-cục sẽ xoá oan widget của hồ sơ kia — [SOÁT P0-1]).
     *
     * Trả về tập rỗng khi không có gì để thu hồi (chỗ gọi khỏi phải kiểm trước).
     */
    fun orphaned(old: HomeUiState, new: HomeUiState): Set<Int> = used(old) - used(new)

    /**
     * Id nền tảng đã cấp mà **không** bố cục nào dùng tới — dùng lúc khởi động để dọn rác của những lần chạy trước.
     *
     * ## Vì sao cần, ngoài [orphaned]
     * [orphaned] chỉ thấy được thay đổi khi app **đang chạy**. Nhưng id sống lâu hơn tiến trình: app bị giết giữa lúc
     * vừa cấp id mà chưa kịp ghi bền (đổi ngôn ngữ/chủ đề làm `recreate()` — dự án đã [ĐO] tiến trình bị giết thật),
     * hoặc người dùng xoá cấu hình. Những id đó không nằm ở bố cục nào nữa nhưng nhà cung cấp **vẫn đẩy cập nhật**.
     * Lúc khởi động, so id nền tảng đang giữ ([allocated], từ `AppWidgetHost.appWidgetIds`) với id bố cục đang dùng.
     *
     * ⚠ Chỉ gọi khi đã nạp xong bố cục. Gọi lúc [state] còn rỗng thì hàm này trả về **mọi** id ⇒ xoá sạch widget của
     * người dùng. Phép tính này không tự chống được ca đó (nó không có cách nào phân biệt *"chưa nạp"* với *"người
     * dùng đã bỏ hết widget"*) ⇒ **thứ tự là trách nhiệm của chỗ gọi**. Cái bẫy đó được **khoá bằng bài canh ở hai
     * phía**: một bài đóng đúng hành vi này (bố cục rỗng ⇒ mọi id thành rác) để người đọc sau thấy ngay, và một bài
     * canh phía Android đòi lượt dọn phải nằm SAU lượt nạp.
     *
     * ⚠ Đọc [used] — id nằm ở **hồ sơ tài xế khác** vẫn đang được dùng dù bố cục hiện tại không có nó.
     */
    fun unused(allocated: Set<Int>, state: HomeUiState): Set<Int> = allocated - used(state)
}
