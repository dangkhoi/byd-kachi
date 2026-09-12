package com.byd.clusternav.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import com.byd.clusternav.R

/**
 * Một mục widget bên thứ ba trong màn chọn: nhãn + icon + việc làm khi chạm.
 *
 * Tồn tại để [AppDrawer] **không phải biết** `AppWidgetProviderInfo`: màn chọn chỉ cần ba thứ này, còn việc đọc nhà
 * cung cấp / nạp icon là của [AppWidgetSlotHost]. Nhờ vậy màn chọn không kéo theo `android.appwidget` vào chỗ nó
 * đang chỉ vẽ ô.
 *
 * ⚠ Tên là `title`, **không** phải `label` — và đó là chủ ý. Trong dự án này `.label` có nghĩa hẹp: nhãn **GỐC tiếng
 * Việt** của một mục `:core` (giao kèo `Localized.label`), thứ mà tầng vẽ **không được** đọc trực tiếp. Nhãn ở đây
 * đến từ `AppWidgetProviderInfo.loadLabel` ⇒ **hệ thống đã dịch** theo ngôn ngữ máy. `LauncherI18nContractTest` đã
 * bắt đúng chỗ này khi nó còn tên `label`; chữa bằng cách đặt tên thật thay vì nới danh sách loại trừ của bài canh.
 */
data class AppWidgetPick(val title: String, val icon: Drawable?, val onTap: () -> Unit)

/**
 * CHỦ SỞ HỮU widget Android bên thứ ba trong ô giữa màn (P6 · T4) — phần chạm Android.
 * Phép tính "id nào hết dùng" nằm ở `:core` ([AppWidgetIds]); file này chỉ **làm** những gì phải qua nền tảng.
 *
 * Spec: `docs/specs/kachi-scenes-and-widgets.html` §3.2.
 *
 * ## [ĐO] Đường ràng buộc — đo trước khi viết một dòng giao diện (2026-09-12, `emulator-5554`)
 * Spec cảnh báo `BIND_APPWIDGET` là *signature|privileged* nên "shell KHÔNG cấp được". **Số đo nói rõ hơn thế**, và
 * chỗ khác biệt là toàn bộ lý do tính năng này giao được:
 *  1. `pm list permissions -f` ⇒ `protectionLevel:signature|privileged` — đúng như spec.
 *  2. `dumpsys package com.byd.launcher` ⇒ `BIND_APPWIDGET` nằm ở *requested permissions* nhưng **KHÔNG** ở
 *     *install permissions* ⇒ **chưa được cấp**.
 *  3. `pm grant … BIND_APPWIDGET` ⇒ `SecurityException: … is not a changeable permission type` (exit 255). Đúng:
 *     đường `pm grant` **đóng** — nó chỉ cấp được quyền *runtime*.
 *  4. **`appwidget grantbind --package com.byd.launcher --user 0` ⇒ exit 0**, và `dumpsys appwidget` chuyển từ
 *     `Grants:` (rỗng) sang `Grants: [0] user=0 package=com.byd.launcher`.
 *
 * ⇒ Nền tảng có **đường thứ hai** không đi qua hệ thống quyền: một *bind-grant* riêng của `AppWidgetService`, cấp
 * bằng shell. Dự án **đã dùng chính đường đó** cho badge tốc-độ VietMap (`LocalDeviceShell.grantAppWidgetBind`,
 * proven **trên xe** — xem `docs/specs/vietmap-widget-bridge.html`). Nên đây KHÔNG phải đường mới phải cầu may:
 * nó là đường đã chạy trên xe thật, chỉ được dùng lại cho một mục đích khác.
 *
 * ⇒ **Không cần hộp thoại hệ thống** (`ACTION_APPWIDGET_BIND`) — và đó là điều đáng mừng, vì trên IVI khoá hộp thoại
 * hệ thống trả toast *"Hệ thống IVI không hỗ trợ hoạt động này"* (tiền lệ màn "Notification access", P8).
 *
 * ## Vì sao cấp quyền LÚC CHỌN, không cấp ở vòng kiểm quyền lúc mở app
 * Vòng kiểm quyền (P8) có 6 điều kiện và luật **"đủ thì IM LẶNG"**. Widget bên thứ ba là mục *tuỳ chọn* mà phần lớn
 * người dùng không bao giờ chạm tới ⇒ thêm nó thành điều kiện thứ 7 là mở một phiên kênh shell mỗi lần nổ máy cho
 * một thứ chưa ai dùng (trái ràng buộc C4: *không mở kênh shell chỉ để đọc*). Cấp lúc người dùng **thật sự chọn**
 * một widget: đúng lúc, và nếu thất bại thì có ngay chỗ để nói ra.
 *
 * ## Vòng đời — hai việc, đừng lẫn
 *  - [startListening]/[stopListening]: **nhận cập nhật**. Nghe khi màn hiện, thôi khi màn ẩn. Nghe mãi = nhà cung cấp
 *    cứ đẩy RemoteViews cho một màn không ai xem.
 *  - [reclaim]: **thu hồi id**. Không dính gì tới việc nghe — id sống lâu hơn cả tiến trình.
 */
class AppWidgetSlotHost(
    private val ctx: Context,
    /** Chạy một lệnh qua kênh shell (dadb loopback). `null` = kênh chưa sẵn sàng. Chạy trên thread NỀN. */
    private val shell: () -> ((String) -> String)?,
    /** Đẩy một việc xuống thread nền của màn chính (nó biết luật "màn đã huỷ thì thôi"). */
    private val background: (() -> Unit) -> Unit,
    /** Nói cho người dùng một câu ngắn (thất bại phải NÓI RA, không im lặng để lại ô trống). */
    private val notify: (String) -> Unit,
) {

    private val manager: AppWidgetManager = AppWidgetManager.getInstance(ctx)

    /**
     * HostId **RIÊNG**, khác cầu VietMap.
     *
     * ⚠ [ĐO] `dumpsys appwidget` cho thấy `com.byd.launcher` ĐÃ có một host `hostId:22093` — đó là cầu badge tốc-độ
     * VietMap (`VietMapWidgetBridge.HOST_ID = 0x564D`) đi theo mã kế thừa từ ClusterNav. Dùng lại đúng số đó thì hai
     * bộ **chia nhau một tập id**: `appWidgetIds` của host này sẽ trả về cả id của VietMap ⇒ lượt dọn rác lúc khởi
     * động ([reclaim] với [AppWidgetIds.unused]) sẽ **xoá id của badge tốc-độ** vì bố cục launcher không hề dùng nó.
     * Badge chết mà không ai hiểu vì sao. Hai chức năng khác nhau ⇒ hai host khác nhau.
     */
    private val host = object : AppWidgetHost(ctx, HOST_ID) {
        // Bề mặt RIÊNG: nó phải tự khai cỡ cho nhà cung cấp, không thì RemoteViews co về ~0 (xem [SlotHostView]).
        override fun onCreateView(c: Context, id: Int, info: AppWidgetProviderInfo?): AppWidgetHostView =
            SlotHostView(c)
    }

    private var listening = false

    /** Bắt đầu nhận cập nhật (gọi khi màn hiện). Gọi nhiều lần không sao. */
    fun startListening() {
        if (listening) return
        // `startListening` đọc dữ liệu liên-tiến-trình và ROM lạ có thể ném; ném ở đây là **giết launcher lúc mở màn**
        // cho một tính năng tuỳ chọn ⇒ không đáng.
        runCatching { host.startListening() }
            .onSuccess { listening = true }
            .onFailure { Log.w(TAG, "startListening lỗi: ${it.javaClass.simpleName}") }
    }

    /** Thôi nhận cập nhật (gọi khi màn ẩn/huỷ). */
    fun stopListening() {
        if (!listening) return
        listening = false
        runCatching { host.stopListening() }
            .onFailure { Log.w(TAG, "stopListening lỗi: ${it.javaClass.simpleName}") }
    }

    /** Danh sách nhà cung cấp widget máy đang có, đã sắp theo nhãn. Rỗng = máy không có widget nào. */
    fun providers(): List<AppWidgetProviderInfo> =
        runCatching { manager.installedProviders }.getOrDefault(emptyList())
            .sortedBy { label(it).lowercase() }

    /** Nhãn người đọc được của một nhà cung cấp (rơi về tên lớp nếu ROM không trả nhãn). */
    fun label(info: AppWidgetProviderInfo): String =
        runCatching { info.loadLabel(ctx.packageManager) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: info.provider.shortClassName.trimStart('.')

    /**
     * Mục cho màn chọn. Rỗng ⇒ màn chọn nói *"máy chưa có app nào cung cấp widget"* (KHÔNG bày mục trống).
     *
     * Icon nạp bằng `loadIcon`, một lời gọi liên-tiến-trình cho **mỗi** nhà cung cấp ([ĐO] máy ảo có 19). Chấp nhận
     * vì nó chỉ chạy lúc mở màn chọn, không nằm trong nhịp vẽ; icon nào lỗi thì mục đó không có icon chứ không mất mục.
     */
    fun picks(onPick: (AppWidgetProviderInfo) -> Unit): List<AppWidgetPick> = providers().map { info ->
        AppWidgetPick(
            title = label(info),
            icon = runCatching { info.loadIcon(ctx, ctx.resources.displayMetrics.densityDpi) }.getOrNull(),
        ) { onPick(info) }
    }

    /**
     * Cấp id + ràng buộc [info] vào nó. Trả `null` = **không ràng buộc được** (chỗ gọi phải nói ra, đừng đặt ô trống).
     *
     * Chạy trên thread CHÍNH và có thể phải mở kênh shell (chặn) ⇒ nhận kết quả qua [done] chứ không trả thẳng.
     *
     * ## Trình tự — thử trước, xin sau
     * Thử `bindAppWidgetIdIfAllowed` **trước**: nếu bind-grant đã có từ lần trước thì xong ngay, không mở kênh shell
     * (đường nhanh, và là đường của mọi lần chọn thứ hai trở đi). Chỉ khi bị từ chối mới xin bind-grant rồi **thử lại
     * đúng MỘT lần** — thất bại lần hai là thất bại thật, thử vòng nữa chỉ làm người dùng đợi.
     */
    fun bind(info: AppWidgetProviderInfo, done: (SlotContent.AppWidget?) -> Unit) {
        val id = runCatching { host.allocateAppWidgetId() }.getOrElse {
            Log.w(TAG, "cấp id lỗi: ${it.javaClass.simpleName}")
            notify(ctx.getString(R.string.kachi_appwidget_err_alloc)); done(null); return
        }
        if (tryBind(id, info.provider)) { done(bound(id, info)); return }
        // Bị từ chối ⇒ xin bind-grant. Kênh shell là lệnh CHẶN ⇒ thread nền.
        val sh = shell()
        if (sh == null) {
            release(id)
            notify(ctx.getString(R.string.kachi_appwidget_err_no_shell)); done(null); return
        }
        notify(ctx.getString(R.string.kachi_appwidget_granting))
        background {
            val out = runCatching { sh(GRANT_CMD) }.getOrElse { "lỗi: ${it.javaClass.simpleName}" }
            Log.i(TAG, "xin bind-grant: '$out'")
            // Về thread chính: `bindAppWidgetIdIfAllowed` + dựng view là việc của thread chính.
            main {
                if (tryBind(id, info.provider)) {
                    done(bound(id, info))
                } else {
                    release(id)
                    notify(ctx.getString(R.string.kachi_appwidget_err_bind))
                    done(null)
                }
            }
        }
    }

    private fun bound(id: Int, info: AppWidgetProviderInfo) =
        SlotContent.AppWidget(id, info.provider.flattenToString())

    private fun tryBind(id: Int, provider: ComponentName): Boolean = runCatching {
        manager.bindAppWidgetIdIfAllowed(id, provider)
    }.getOrElse {
        // `SecurityException` = chưa có bind-grant (ca chờ đợi). `IllegalArgumentException` = nhà cung cấp từ chối.
        Log.w(TAG, "bind id=$id lỗi: ${it.javaClass.simpleName}")
        false
    }

    /**
     * Dựng view cho một ô đã ràng buộc. Trả `null` khi **id đã chết** (app bị gỡ/vô hiệu) ⇒ chỗ gọi hiện thẻ *"widget
     * không còn"* thay vì ô trống bí ẩn.
     *
     * ⚠ Kiểm `getAppWidgetInfo` TRƯỚC khi gọi `createView`: với id đã chết, `createView` trả về một view **rỗng
     * không có gì cả** (không ném) — đúng cái "ô trống bí ẩn" mà nhiệm vụ này cấm.
     */
    fun createView(slot: SlotContent.AppWidget): AppWidgetHostView? {
        val info = runCatching { manager.getAppWidgetInfo(slot.widgetId) }.getOrNull() ?: return null
        return runCatching { host.createView(ctx, slot.widgetId, info) }
            .onFailure { Log.w(TAG, "createView id=${slot.widgetId} lỗi: ${it.javaClass.simpleName}") }
            .getOrNull()
    }

    /**
     * Bề mặt widget **tự khai cỡ của mình cho nhà cung cấp**.
     *
     * ## ⚠⚠ [ĐO] Vì sao lớp này phải tồn tại — mọi dấu hiệu nói "chạy được" mà Ô THÌ TRỐNG
     * Bản đầu trả thẳng `host.createView(...)`. Bằng chứng lúc đó đều dương: `dumpsys appwidget` có
     * `id=650 … provider=…DigitalAppWidgetProvider … views=RemoteViews@9c57124`, và cây view có hẳn `LinearLayout`
     * của `com.google.android.deskclock` **nằm trong** `AppWidgetHostView` của launcher, kèm `text="12:44"` +
     * `text="SAT, SEP 12"` (giờ và ngày THẬT). Nhưng **ảnh chụp ô thì gần như trống**: [ĐO] vùng 1242×684 có
     * **99.84%** điểm đúng một màu nền `(23,26,32)`, độ sáng cao nhất chỉ **110** (không một điểm nào > 120), và hai
     * `TextView` giờ/ngày đo được **4×1 px** và **8×1 px**.
     *
     * Nguyên nhân: `AppWidgetHostView` **không tự nói cỡ cho nhà cung cấp**. Provider chọn/đo layout theo cỡ mà host
     * khai qua `updateAppWidgetSize`; không khai thì nó dùng 0 và cây RemoteViews **co về ~0** — hiện ra đúng như một
     * ô chưa gán gì. Tức "ô trống bí ẩn" (thứ [deadLabel]/`deadWidgetCard` sinh ra để chặn) lại đến từ đường **thành
     * công**, chỗ không ai nghĩ phải canh.
     *
     * Khai cỡ ở [onSizeChanged] chứ KHÔNG ở lúc `createView`: lúc dựng view **chưa ai biết ô rộng bao nhiêu** — bố
     * cục tự vẽ (P9) cho mỗi khung một cỡ, và đổi bố cục là đổi cỡ. Làm ở đây thì mọi ca (bố cục sẵn · bố cục tự vẽ ·
     * đổi preset · gọi cảnh) tự đúng, không chỗ nào phải nhớ gọi.
     *
     * **Bài học chung**: `dumpsys` nói *"đã ràng buộc"*, cây view nói *"nội dung có đó"* — cả hai ĐÚNG mà ô vẫn trống.
     * Chỉ phép đo **pixel** bác được. Lần thứ tư của dự án: mã đúng + test xanh ≠ đã giao được.
     */
    private inner class SlotHostView(c: Context) : AppWidgetHostView(c) {
        private var toldW = 0
        private var toldH = 0

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            val d = resources.displayMetrics.density
            val wDp = (w / d).toInt()
            val hDp = (h / d).toInt()
            // Cỡ chưa đổi ⇒ không khai lại. `updateAppWidgetSize` là lời gọi LIÊN-TIẾN-TRÌNH và nó làm nhà cung cấp
            // dựng lại RemoteViews; `onSizeChanged` còn bắn khi chỉ lệch vài pixel do đo lại, nên thiếu chốt này là
            // mỗi lượt đo một lượt cập nhật — loại lãng phí mà nhịp 1 giây trên xe sẽ nhân lên.
            if (wDp == toldW && hDp == toldH) return
            toldW = wDp
            toldH = hDp
            // ⚠ `Bundle()` MỚI, KHÔNG phải `Bundle.EMPTY`: `updateAppWidgetSize` GHI các khoá cỡ (`OPTION_APPWIDGET_*`)
            // **vào chính bundle được truyền**, mà `Bundle.EMPTY` là bundle KHÔNG ĐỔI ĐƯỢC.
            // [ĐO] bản trước truyền `Bundle.EMPTY` ⇒ logcat `KachiAppWidget: khai cỡ widget lỗi:
            // UnsupportedOperationException` ⇒ cỡ không bao giờ tới nhà cung cấp ⇒ ô vẫn trống y như chưa vá.
            // Tìm ra được là vì nhánh lỗi có GHI LOG kèm tên ngoại lệ; `runCatching` im lặng thì bản vá này đã trông
            // như "đã làm mà không ăn" và tôi sẽ đi sửa sai chỗ.
            runCatching { updateAppWidgetSize(Bundle(), wDp, hDp, wDp, hDp) }
                .onFailure { Log.w(TAG, "khai cỡ widget lỗi: ${it.javaClass.simpleName}") }
        }
    }

    /** Nhãn để hiện khi id đã chết — lấy từ provider đã lưu (đó là lý do provider được lưu cùng id). */
    fun deadLabel(slot: SlotContent.AppWidget): String =
        ComponentName.unflattenFromString(slot.provider)?.packageName ?: slot.provider

    /**
     * Thu hồi id không còn ai dùng khi trạng thái đổi từ [old] sang [new].
     *
     * Phép chọn id nằm ở [AppWidgetIds.orphaned] (`:core`) và nó đọc **cả sổ cảnh**, không chỉ bố cục — kể cả ca
     * **đổi chỗ hai ô** (cùng id sang ô khác thì KHÔNG được thu hồi; so theo tập hợp, không theo vị trí ô) và ca
     * **id đang nằm trong một cảnh đã lưu** (không được thu hồi, không thì gọi lại cảnh đó ra thẻ "widget không còn").
     *
     * ## ⚠⚠ Lọc theo CHỦ SỞ HỮU trước khi xoá — chốt bảo vệ badge tốc-độ VietMap
     * Số id ở đây đến từ **chuỗi trên đĩa** (`slot_*` / `scenes`), tức người dùng sửa tay được. Cùng gói
     * `com.byd.launcher` còn có host thứ hai — cầu badge tốc-độ VietMap (`hostId 0x564D`, [ĐO] `dumpsys appwidget`
     * đang giữ id 647/648/649, **proven trên xe**). `AppWidgetService.deleteAppWidgetId` chỉ kiểm **uid/gói** gọi, KHÔNG
     * kiểm id có thuộc đúng host đang gọi, nên một chuỗi hỏng mang số 647 sẽ **giết badge** — hỏng một tính năng đã
     * chạy trên xe, im lặng, không hoàn lại được. Giao nhau với `appWidgetIds` của **host này** làm ca đó thành bất khả
     * thi thay vì "không ai gõ sai số". Giá: một lời gọi liên-tiến-trình, chỉ khi thật sự có gì cần thu hồi.
     */
    fun reclaim(old: HomeUiState, new: HomeUiState) {
        val orphans = AppWidgetIds.orphaned(old, new)
        if (orphans.isEmpty()) return
        val mine = runCatching { host.appWidgetIds.toSet() }.getOrDefault(emptySet())
        val foreign = orphans - mine
        if (foreign.isNotEmpty()) Log.w(TAG, "bỏ qua id KHÔNG thuộc host này: $foreign")
        orphans.intersect(mine).forEach { release(it) }
    }

    /**
     * Dọn id rác của những lần chạy TRƯỚC (gọi **SAU** khi đã nạp trạng thái).
     *
     * ⚠ Thứ tự là bắt buộc: gọi lúc trạng thái còn rỗng thì [AppWidgetIds.unused] trả về **mọi** id ⇒ xoá sạch widget
     * của người dùng. Bảo vệ tại chỗ: trạng thái **không có** widget bên thứ ba nào (kể cả trong cảnh đã lưu) thì đây
     * không phân biệt được *"chưa nạp"* với *"người dùng đã bỏ hết"* ⇒ **không xoá gì**. Cái giá là một id rác có thể
     * sống thêm tới lần sau; rẻ hơn nhiều so với xoá widget đang dùng.
     *
     * Không cần lọc chủ sở hữu như [reclaim]: `appWidgetIds` đã là id của **riêng host này** ([ĐO] `dumpsys appwidget`
     * — `hostId 19265` giữ 0 id trong khi `hostId 22093` của cầu VietMap giữ 3).
     */
    fun sweep(loaded: HomeUiState) {
        if (AppWidgetIds.used(loaded).isEmpty()) return
        val allocated = runCatching { host.appWidgetIds.toSet() }.getOrDefault(emptySet())
        AppWidgetIds.unused(allocated, loaded).forEach { release(it) }
    }

    private fun release(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
            .onFailure { Log.w(TAG, "thu hồi id=$id lỗi: ${it.javaClass.simpleName}") }
    }

    private fun main(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }

    /** Ô này có phải widget bên thứ ba không — dùng ở tầng vẽ để chọn nhánh dựng view. */
    fun isAppWidget(v: View?): Boolean = v is AppWidgetHostView

    private companion object {
        const val TAG = "KachiAppWidget"

        /** `0x4B41` = "KA" (Kachi). Cố ý KHÁC `0x564D` ("VM") của cầu VietMap — xem KDoc [host]. */
        const val HOST_ID = 0x4B41

        /**
         * Lệnh xin bind-grant — **đúng công thức đã proven trên xe** (bản 1.13 · `LocalDeviceShell.grantAppWidgetBind`),
         * không phát minh lệnh mới. [ĐO] trên `emulator-5554`: exit 0, `dumpsys appwidget` hiện
         * `Grants: [0] user=0 package=com.byd.launcher`.
         *
         * ⚠ Gói ghi **cứng** chứ không lấy từ `ctx.packageName`: lệnh này chỉ mở đúng cho launcher, và nếu một ngày
         * ai đó gọi từ một tiến trình khác thì lệnh phải vẫn nói đúng gói cần cấp, không "cấp cho chính mình".
         */
        const val GRANT_CMD = "appwidget grantbind --package com.byd.launcher --user 0"
    }
}
