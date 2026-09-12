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
     * Mọi id mà **các cảnh đã lưu** đang giữ ([SceneBook]).
     *
     * ## ⚠⚠ [ĐO] Thiếu hàm này thì "widget trong cảnh" chết ngay lần đổi cảnh đầu tiên
     * Cảnh lưu **nguyên nội dung ô**, kể cả ô widget bên thứ ba — tức nó lưu chính con số id. Nhưng id là thứ nền
     * tảng cấp và **không cấp lại được**: `deleteAppWidgetId(651)` rồi thì không có cách nào làm cho 651 sống lại.
     * Đo trên `emulator-5554` (bản trước bản vá): đặt widget đồng hồ vào ô ⇒ lưu cảnh *CoWidget* ⇒ đổi ô đó sang một
     * app ⇒ `dumpsys appwidget` cho thấy **id 651 đã bị xoá** ⇒ gọi lại cảnh *CoWidget* thì ô hiện đúng câu
     * *"widget của com.google.android.deskclock không còn — app đã bị gỡ hoặc bị tắt"* trong khi app **vẫn còn nguyên
     * trên máy**. Tức launcher tự xoá widget của người dùng rồi báo sai nguyên nhân.
     *
     * ⇒ "Còn ai dùng" phải tính trên **cả** bố cục đang sống **lẫn** mọi cảnh đã lưu — xem [used].
     */
    fun idsIn(book: SceneBook): Set<Int> =
        book.scenes.flatMapTo(mutableSetOf()) { scene -> idsIn(scene.workspaceState()) }

    /**
     * Id còn **ĐANG ĐƯỢC DÙNG** theo [state]: bố cục đang sống ∪ mọi cảnh đã lưu.
     *
     * Đây là **định nghĩa duy nhất** của "còn dùng" trong toàn bộ tính năng. [orphaned] và [unused] đều đọc nó, nên
     * không có đường nào trả lời câu hỏi đó theo một cách hẹp hơn — và đó là chủ ý: phiên bản chỉ-xét-bố-cục từng tồn
     * tại ở đây và nó là nguyên nhân của lỗi ghi trong KDoc [idsIn]. Trần số id vẫn có chặn trên: 8 cảnh × trần ô.
     */
    fun used(state: HomeUiState): Set<Int> = idsIn(state.workspace) + idsIn(state.scenes)

    /**
     * Id có ở [old] mà KHÔNG còn ở [new] ⇒ phải gọi `deleteAppWidgetId`.
     *
     * Nhận [HomeUiState] (không phải [WorkspaceState]) vì phép này phải thấy **cả** sổ cảnh: nhờ vậy một lời gọi phủ
     * đủ bốn đường đổi — đổi/xoá nội dung ô, **gọi cảnh**, và **xoá cảnh** (ca cuối chỉ đổi sổ cảnh, bố cục không đổi
     * một ô nào, nên bản chỉ-xét-bố-cục sẽ bỏ sót ⇒ id rác sống mãi).
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
     * ⚠ Đọc [used] — id nằm trong một **cảnh đã lưu** vẫn đang được dùng dù bố cục hiện tại không có nó.
     */
    fun unused(allocated: Set<Int>, state: HomeUiState): Set<Int> = allocated - used(state)
}
