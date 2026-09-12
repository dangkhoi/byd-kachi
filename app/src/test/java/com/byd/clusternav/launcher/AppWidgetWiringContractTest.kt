package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T4 — DÂY NỐI của widget Android bên thứ ba.
 *
 * ## ⚠ Vì sao bài này nằm ở `:app`, không ở `:core`
 * Nó quét **mã nguồn `:app`**. Dự án đã [ĐO] hai lần rằng bài quét mã của module X mà đặt ở module Y thì gradle
 * **không coi tệp của X là đầu vào** ⇒ task báo `UP-TO-DATE` và bài **không bao giờ chạy lại** (dấu xanh giả: S1 với
 * hai bài chống-rữa, rồi 11 ca của bộ niêm phong T11). Đặt đúng module là MỘT NỬA; nửa còn lại là gradle phải biết
 * thứ bài này quét — kiểm ở cuối bài.
 */
class AppWidgetWiringContractTest {

    private fun activity() = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt")
    private fun host() = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/AppWidgetSlotHost.kt")
    private fun view() = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt")
    private fun drawer() = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/AppDrawer.kt")

    /**
     * MÃ đã **bỏ chú thích** — bắt buộc cho mọi phép "chuỗi X không được xuất hiện".
     *
     * ## ⚠ Chính hai bài dưới đây đã ĐỎ OAN vì thiếu bước này
     * KDoc của `AppWidgetSlotHost` **ghi lại số đo**, nên nó chứa đúng những chuỗi mà bài canh đi tìm: câu
     * *"`pm grant` trả SecurityException"* và số `0x564D` (hostId của cầu VietMap, nêu ra để giải thích vì sao phải
     * chọn số khác). Quét cả chú thích ⇒ bài kết luận mã sản phẩm dùng `pm grant` và trùng hostId — **cả hai đều
     * sai**. Đây cùng họ với lỗi đã ghi ở gói 3 (*bài canh cắt vùng bằng mốc chú thích trong khi bộ quét đã bỏ hết
     * chú thích* ⇒ quét sai vùng nhưng vẫn xanh). Lần đó sai thành XANH, lần này sai thành ĐỎ; nguyên nhân một.
     *
     * ⇒ Luật: tài liệu-trong-mã được phép nói về thứ mã KHÔNG làm. Bài canh phải soi mã, không soi văn.
     */
    private fun code(src: String): String = src
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    // ── Vòng đời nghe cập nhật ────────────────────────────────────────────────────

    /**
     * `startListening` khi màn HIỆN, `stopListening` khi màn ẨN.
     *
     * Nghe mãi = nhà cung cấp đẩy RemoteViews cho một màn không ai xem ([ĐO] VietMap khai
     * `updatePeriodMillis=100` trên máy ảo — 10 lượt/giây).
     */
    @Test
    fun `nghe cap nhat gan vao onStart va nha o onStop`() {
        val src = activity()
        assertTrue(
            "appWidgets.startListening()" in SourceRoots.body(src, "override fun onStart()"),
            "onStart phải gọi startListening — thiếu thì widget không bao giờ nhận nội dung",
        )
        assertTrue(
            "appWidgets.stopListening()" in SourceRoots.body(src, "override fun onStop()"),
            "onStop phải gọi stopListening — thiếu thì nhà cung cấp đẩy cập nhật cho màn đã ẩn",
        )
    }

    // ── Thu hồi id ────────────────────────────────────────────────────────────────

    /** Thu hồi id phải nằm ở ĐÚNG MỘT chỗ — chỗ diff của `render` — để mọi đường đổi ô đều đi qua. */
    @Test
    fun `thu hoi id nam trong render va chi mot cho`() {
        val src = activity()
        assertTrue(
            "appWidgets.reclaim(" in SourceRoots.body(src, "private fun render(state: HomeUiState)"),
            "render phải thu hồi id: đó là chỗ DUY NHẤT thấy được mọi thay đổi ô (chọn, kéo-thả, xoá, gọi cảnh)",
        )
        assertEquals(
            1, Regex("""appWidgets\.reclaim\(""").findAll(src).count(),
            "đúng MỘT chỗ gọi reclaim — rải rác nhiều chỗ là cách để quên một đường (bẫy hai-bản-sao)",
        )
    }

    /**
     * ⚠ Lượt dọn rác lúc khởi động phải nằm SAU lượt nạp bố cục.
     *
     * `AppWidgetIds.unused` không phân biệt được *"chưa nạp"* với *"đã bỏ hết widget"*, nên gọi khi state còn rỗng là
     * **xoá sạch widget của người dùng**. Bài này khoá thứ tự bằng vị trí trong tệp: `sweep` phải đọc state đã nạp.
     */
    @Test
    fun `don rac luc khoi dong doc state DA nap`() {
        val src = activity()
        val sweep = Regex("""appWidgets\.sweep\(([^)]*)\)""").find(src)
        assertTrue(sweep != null, "phải có lượt dọn rác id của lần chạy trước")
        assertTrue(
            "viewModel.uiState.value" in sweep!!.groupValues[1],
            "sweep phải đọc state ĐÃ NẠP (`viewModel.uiState.value`), không phải một state rỗng dựng tại chỗ",
        )
    }

    /** Chốt tại chỗ trong host: trạng thái không có widget nào ⇒ không xoá gì (chống gọi sai thứ tự). */
    @Test
    fun `sweep tu chan khi trang thai khong co widget nao`() {
        assertTrue(
            "if (AppWidgetIds.used(loaded).isEmpty()) return" in SourceRoots.body(host(), "fun sweep(loaded: HomeUiState)"),
            "sweep phải tự bỏ qua khi không có widget bên thứ ba nào — xem KDoc (xoá oan là không hoàn lại được)",
        )
    }

    /**
     * ⚠⚠ **Thu hồi id phải tính CẢ SỔ CẢNH, không chỉ bố cục** — nếu không thì hai tính năng của phiên này phá nhau.
     *
     * Cảnh lưu chính con số id, mà id đã `deleteAppWidgetId` thì không cấp lại được. [ĐO] `emulator-5554` bản trước
     * bản vá: lưu cảnh có widget ⇒ đổi ô đó sang một app ⇒ id 651 bị xoá ⇒ gọi lại cảnh ra thẻ *"app đã bị gỡ"* trong
     * khi app vẫn còn. Chốt bằng KIỂU dữ liệu: `reclaim`/`sweep` nhận [HomeUiState] nên không có cách nào truyền vào
     * một `WorkspaceState` trơ mà quên sổ cảnh.
     */
    @Test
    fun `thu hoi id tinh ca so canh chu khong chi bo cuc`() {
        val src = code(host())
        assertTrue(
            "fun reclaim(old: HomeUiState, new: HomeUiState)" in src,
            "reclaim phải nhận CẢ trạng thái: xoá một cảnh là đường duy nhất nhả id mà bố cục không đổi ô nào",
        )
        assertTrue("fun sweep(loaded: HomeUiState)" in src, "sweep cũng phải thấy sổ cảnh")
        assertTrue(
            "appWidgets.reclaim(it, state)" in code(activity()),
            "màn chính phải truyền cả state, không phải `it.workspace`/`state.workspace`",
        )
        assertTrue(
            "appWidgets.sweep(viewModel.uiState.value)" in code(activity()),
            "và lượt dọn rác cũng nhận cả state",
        )
    }

    /**
     * ⚠⚠ **CHỐT BẢO VỆ BADGE TỐC-ĐỘ VIETMAP** — id đem xoá phải thuộc host NÀY.
     *
     * Số id mà [AppWidgetSlotHost.reclaim] nhận đến từ **chuỗi trên đĩa** (`slot_*` / `scenes`), tức sửa tay được.
     * Cùng gói còn có host thứ hai của cầu VietMap ([ĐO] `dumpsys appwidget`: `hostId 22093` giữ id 647/648/649,
     * **proven trên xe**), và `AppWidgetService.deleteAppWidgetId` chỉ kiểm **uid/gói** gọi — KHÔNG kiểm id có thuộc
     * đúng host đang gọi. Nghĩa là một chuỗi hỏng mang số 647 sẽ giết badge, im lặng, không hoàn lại được.
     */
    @Test
    fun `chi xoa id thuoc host nay - chan xoa oan id cua cau VietMap`() {
        val body = SourceRoots.body(code(host()), "fun reclaim(old: HomeUiState, new: HomeUiState)")
        assertTrue(
            Regex("""host\.appWidgetIds""").containsMatchIn(body),
            "reclaim phải đọc danh sách id của HOST NÀY để lọc",
        )
        assertTrue(
            Regex("""\.intersect\(\s*mine\s*\)""").containsMatchIn(body),
            "và phải GIAO với danh sách đó trước khi xoá — id đến từ đĩa, không phải từ nền tảng",
        )
    }

    // ── Cấp quyền: đúng công thức đã proven trên xe ───────────────────────────────

    /**
     * Lệnh xin bind-grant phải là **đúng công thức đã proven trên xe** (`appwidget grantbind`), không phải `pm grant`.
     *
     * [ĐO] 2026-09-12 `emulator-5554`: `pm grant … BIND_APPWIDGET` ⇒ `SecurityException: … is not a changeable
     * permission type` (exit 255); `appwidget grantbind --package com.byd.launcher --user 0` ⇒ exit 0 và
     * `dumpsys appwidget` hiện `Grants: [0] user=0 package=com.byd.launcher`.
     */
    @Test
    fun `xin quyen bang appwidget grantbind chu khong pham grant`() {
        val src = code(host())
        assertTrue("appwidget grantbind --package" in src, "phải dùng `appwidget grantbind` (đường proven trên xe)")
        assertTrue("--user 0" in src, "phải nêu user 0 như công thức gốc")
        assertTrue(
            "pm grant" !in src,
            "`pm grant` KHÔNG cấp được BIND_APPWIDGET (nó chỉ cấp quyền runtime) — [ĐO] trả SecurityException",
        )
    }

    /** Thử bind TRƯỚC, chỉ khi bị từ chối mới mở kênh shell — không mở phiên shell cho mọi lần chọn (ràng buộc C4). */
    @Test
    fun `thu bind truoc roi moi xin quyen`() {
        val body = SourceRoots.body(host(), "fun bind(info: AppWidgetProviderInfo, done: (SlotContent.AppWidget?) -> Unit)")
        val firstTry = body.indexOf("tryBind(")
        val grant = body.indexOf("GRANT_CMD")
        assertTrue(firstTry in 0 until grant, "phải thử bind trước khi xin quyền (lần chọn thứ hai trở đi khỏi mở shell)")
    }

    /** Thất bại thì **thu hồi id vừa cấp** — không thì mỗi lần chọn thất bại là một id rác. */
    @Test
    fun `bind that bai thi nha lai id vua cap`() {
        val body = SourceRoots.body(host(), "fun bind(info: AppWidgetProviderInfo, done: (SlotContent.AppWidget?) -> Unit)")
        assertTrue(
            Regex("""release\(id\)""").findAll(body).count() >= 2,
            "mọi nhánh thất bại (không có kênh shell · bind lần hai vẫn bị từ chối) đều phải nhả id",
        )
    }

    /**
     * ⚠⚠ [SOÁT P3-2] **Việc nền bị TỪ CHỐI cũng là một nhánh thất bại** — id đã cấp trước đó phải được nhả.
     *
     * `KachiHomeActivity.submitOn` cố ý **bỏ** việc khi màn đã huỷ. Bản cũ gọi `background { … }` như một câu lệnh nên
     * ca đó nghĩa là: id đã `allocateAppWidgetId` mà **không ai nhả**, và `done` **không bao giờ chạy** (chỗ gọi treo,
     * không ô nào được đặt, không câu nào nói ra). Chốt cả hai đầu của đường dây: kiểu trả về ở màn chính, và nhánh dọn
     * ở đây.
     */
    @Test
    fun `viec nen bi tu choi thi nha id va goi lai done`() {
        val body = SourceRoots.body(code(host()), "fun bind(info: AppWidgetProviderInfo, done: (SlotContent.AppWidget?) -> Unit)")
        assertTrue(
            Regex("""val\s+accepted\s*=\s*background\s*\{""").containsMatchIn(body),
            "phải NHẬN kết quả của `background` — gọi nó như câu lệnh là bỏ việc trong im lặng khi màn đã huỷ",
        )
        assertTrue(
            Regex("""if\s*\(\s*!accepted\s*\)""").containsMatchIn(body),
            "và phải có nhánh cho ca bị từ chối",
        )
        assertTrue(
            "(() -> Unit) -> Boolean" in code(host()),
            "kiểu của cổng vào phải là `-> Boolean`: `-> Unit` thì chỗ gọi KHÔNG THỂ biết việc có được nhận",
        )
        // Đầu kia của dây: màn chính phải thật sự trả kết quả, không thì bài trên chỉ canh một chữ ký giả.
        val act = code(activity())
        assertTrue(
            Regex("""private fun submitOn\([^\n]*\): Boolean""").containsMatchIn(act),
            "submitOn phải trả Boolean",
        )
        assertTrue(
            Regex("""private fun submitBg\([^\n]*\): Boolean""").containsMatchIn(act),
            "và submitBg (thứ được tiêm vào host) cũng vậy",
        )
    }

    /**
     * ⚠ [SOÁT P3-5] Nhãn widget bên thứ ba **TRÙNG NHAU** phải được gỡ trước khi bày ra.
     *
     * [ĐO] máy ảo bày hai mục cùng tên *"Cảnh báo"* (hai widget VietMap) và hai *"Google Play Music"* — người dùng phải
     * bấm thử mới biết mục nào, mà mỗi lần bấm là một lần cấp id + ràng buộc + có thể mở kênh shell. Phép gỡ trùng ở
     * `:core` ([AppWidgetLabels]) nên kiểm được off-car; bài này chỉ canh **dây nối** (khai một phép mà không ai gọi
     * thì nó là mã chết, và màn chọn vẫn trùng y như cũ).
     */
    @Test
    fun `nhan widget ben thu ba trung nhau duoc go truoc khi bay ra`() {
        val body = SourceRoots.body(code(host()), "fun picks(onPick: (AppWidgetProviderInfo) -> Unit): List<AppWidgetPick>")
        assertTrue(
            "AppWidgetLabels.titles(" in body,
            "picks() phải đi qua phép gỡ trùng — [ĐO] không có nó thì màn chọn hiện hai mục CÙNG TÊN 'Cảnh báo'",
        )
        assertTrue(
            "provider.flattenToString()" in body,
            "phép gỡ trùng cần chuỗi provider để sinh gợi ý (nhãn một mình không phân biệt được)",
        )
        // ⚠⚠ Đòi NHÃN THẬT SỰ LẤY TỪ kết quả đó. Bản đầu của bài này chỉ hỏi "có gọi `titles(` không" và [ĐO] THỬ PHÁ
        // cho thấy nó **không thể đỏ**: đổi `title = titles[i]` về `title = label(info)` thì phép gỡ trùng vẫn được
        // gọi (kết quả bị bỏ đi) ⇒ 21 bài vẫn XANH trong khi màn chọn trùng tên y như cũ. Đúng họ "test trang trí"
        // mà dự án đã tìm ra ba lần — và lần này chính tôi vừa viết ra nó.
        assertTrue(
            Regex("""title\s*=\s*titles\[""").containsMatchIn(body),
            "nhãn của mục PHẢI lấy từ danh sách đã gỡ trùng; gọi `titles(...)` rồi bỏ kết quả là không sửa gì",
        )
        assertTrue(
            !Regex("""title\s*=\s*label\(""").containsMatchIn(body),
            "`title = label(info)` là dạng TRƯỚC bản vá (nhãn thô, trùng nhau) — không được quay lại",
        )
    }

    // ── Không được im lặng ────────────────────────────────────────────────────────

    /**
     * Thất bại phải NÓI RA, và nói qua **bảng đang mở** chứ không qua toast.
     *
     * [ĐO] U5 `dumpsys window`: ngăn kéo là `APPLICATION_OVERLAY` lớp **121000**, toast lớp **81000** ⇒ toast nằm
     * DƯỚI, mã có gọi mà người dùng không nhận được gì. Dự án đã có luật *"bề mặt phủ không được nói bằng Toast"*.
     */
    @Test
    fun `noi ket qua qua bang dang mo chu khong qua toast`() {
        assertTrue(
            "drawerController.say(" in activity(),
            "kết quả ràng buộc phải nói vào bảng đang mở (kênh duy nhất thấy được ở bề mặt phủ)",
        )
        assertTrue(
            "Toast" !in code(host()),
            "KHÔNG dùng Toast: ngăn kéo là APPLICATION_OVERLAY (121000) nên toast (81000) nằm dưới, [ĐO] U5",
        )
    }

    /** Ba câu thất bại + một câu đang-làm phải là chuỗi TÀI NGUYÊN (đa ngôn ngữ), không viết cứng. */
    @Test
    fun `moi cau noi voi nguoi dung di qua tai nguyen`() {
        val src = host()
        listOf(
            "kachi_appwidget_err_alloc", "kachi_appwidget_err_no_shell",
            "kachi_appwidget_err_bind", "kachi_appwidget_granting",
        ).forEach { assertTrue("R.string.$it" in src, "thiếu chuỗi tài nguyên $it") }
    }

    /**
     * Id đã chết ⇒ hiện thẻ nói rõ, **KHÔNG** để ô trống.
     *
     * `AppWidgetHost.createView` với id đã chết trả về view rỗng **không báo lỗi**; không chặn thì ô đó trông y như
     * chưa gán gì — người dùng chỉ thấy widget của mình biến mất không lý do.
     */
    @Test
    fun `id da chet thi hien the noi ro chu khong de o trong`() {
        assertTrue("deadWidgetCard(" in code(view()), "phải có thẻ cho ca id đã chết")
        // ⚠ `code(...)` KHÔNG được bỏ: [ĐO] thử phá — thay lời gọi thật bằng `installedProviders.firstOrNull()` mà
        // bài **vẫn XANH**, vì KDoc của `createView` có nhắc chữ `getAppWidgetInfo`. Đúng họ lỗi đã làm hai bài khác
        // ĐỎ OAN ở trên, nhưng lần này sai theo chiều NGUY HIỂM (xanh giả). Chỉ phép thử phá lộ ra được.
        val body = SourceRoots.body(code(host()), "fun createView(slot: SlotContent.AppWidget): AppWidgetHostView?")
        assertTrue(
            "manager.getAppWidgetInfo(slot.widgetId)" in body,
            "phải kiểm getAppWidgetInfo(ĐÚNG id của ô) TRƯỚC createView — createView với id chết trả view RỖNG " +
                "không ném, tức đúng cái 'ô trống bí ẩn' mà thẻ deadWidgetCard sinh ra để chặn",
        )
    }

    /** Mục chọn rỗng thì vẫn nói *"máy chưa có app nào cung cấp widget"* (không bỏ cả mục trong im lặng). */
    @Test
    fun `khong co nha cung cap nao thi van noi ra`() {
        assertTrue(
            "kachi_drawer_note_appwidgets_none" in drawer(),
            "danh sách rỗng phải nói ra — bỏ cả mục trong im lặng là 'tính năng biến mất không lý do'",
        )
    }

    // ── Khai cỡ cho nhà cung cấp: HAI lỗi đã ĐO được, khoá lại ────────────────────

    /**
     * ⚠⚠ Host PHẢI tự khai cỡ cho nhà cung cấp, không thì **ô trống dù ràng buộc thành công**.
     *
     * [ĐO] 2026-09-12 bản không khai cỡ: `dumpsys appwidget` có `id=650 … views=RemoteViews@9c57124`, cây view có
     * `LinearLayout` của `com.google.android.deskclock` **nằm trong** host view kèm `text="12:47"` — mọi dấu hiệu nói
     * "chạy được". Nhưng ảnh chụp ô: **99.84%** điểm đúng một màu nền, độ sáng cao nhất **110**, hai `TextView`
     * giờ/ngày đo được **4×1 px** và **8×1 px**. Sau khi khai cỡ: `503×243 px` và `194×33 px`, 3276 điểm sáng > 220.
     */
    @Test
    fun `host tu khai co cho nha cung cap`() {
        val src = code(host())
        assertTrue(
            "updateAppWidgetSize(" in src,
            "thiếu updateAppWidgetSize ⇒ nhà cung cấp dựng RemoteViews ở cỡ 0 ⇒ ô TRỐNG dù đã ràng buộc xong " +
                "([ĐO] TextView giờ chỉ 4×1 px)",
        )
        assertTrue(
            "override fun onSizeChanged(" in src,
            "phải khai cỡ ở onSizeChanged, không phải lúc createView: lúc dựng view chưa ai biết ô rộng bao nhiêu " +
                "(bố cục tự vẽ P9 cho mỗi khung một cỡ)",
        )
        assertTrue(
            "override fun onCreateView(" in src,
            "host phải dựng bề mặt RIÊNG (lớp tự khai cỡ); AppWidgetHostView trơn không tự nói cỡ",
        )
    }

    /**
     * ⚠⚠ `Bundle.EMPTY` là bundle **KHÔNG ĐỔI ĐƯỢC**, mà `updateAppWidgetSize` **ghi** các khoá cỡ vào chính bundle
     * được truyền.
     *
     * [ĐO] bản dùng `Bundle.EMPTY` ⇒ logcat `KachiAppWidget: khai cỡ widget lỗi: UnsupportedOperationException` ⇒ cỡ
     * không bao giờ tới nhà cung cấp ⇒ ô vẫn trống **y như chưa vá**. Lỗi này đội lốt "bản vá không ăn", nên nó sẽ
     * đẩy người sửa đi tìm sai chỗ; khoá lại tại đây.
     */
    @Test
    fun `khai co bang Bundle MOI chu khong phai Bundle EMPTY`() {
        val src = code(host())
        assertTrue(
            "Bundle.EMPTY" !in src,
            "Bundle.EMPTY không ghi được ⇒ updateAppWidgetSize ném UnsupportedOperationException ([ĐO] logcat) " +
                "⇒ cỡ không tới nhà cung cấp ⇒ ô trống. Truyền `Bundle()` mới.",
        )
        assertTrue("updateAppWidgetSize(Bundle()" in src, "phải truyền một Bundle MỚI, ghi được")
    }

    /** Cỡ chưa đổi ⇒ không khai lại: đó là lời gọi liên-tiến-trình và nó làm nhà cung cấp dựng lại RemoteViews. */
    @Test
    fun `khong khai lai co khi co khong doi`() {
        val body = SourceRoots.body(code(host()), "override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int)")
        // ⚠ Đòi ĐÚNG phép so, không chỉ đòi thấy tên biến: bản đầu của bài này viết `"toldW" in body` và [ĐO] thử phá
        // (đổi điều kiện thành `if (false) return`) **vẫn XANH**, vì `toldW` còn xuất hiện ở dòng gán bên dưới. Một
        // chốt như thế là trang trí — cùng họ với 44 phép cắt vùng `substringAfter` mà dự án đã phải đi vá.
        assertTrue(
            Regex("""if\s*\(\s*wDp\s*==\s*toldW\s*&&\s*hDp\s*==\s*toldH\s*\)\s*return""").containsMatchIn(body),
            "phải so cỡ mới với cỡ ĐÃ KHAI rồi thoát sớm — onSizeChanged bắn cả khi lệch vài pixel do đo lại, " +
                "mỗi lượt là một lời gọi liên-tiến-trình làm nhà cung cấp dựng lại RemoteViews",
        )
        assertTrue("toldW = wDp" in body && "toldH = hDp" in body, "phải NHỚ lại cỡ vừa khai, không thì chốt vô nghĩa")
    }

    // ── Không lấn sang cầu VietMap ────────────────────────────────────────────────

    /**
     * ⚠ HostId phải KHÁC cầu badge tốc-độ VietMap.
     *
     * [ĐO] `dumpsys appwidget` cho thấy `com.byd.launcher` đã có host `hostId:22093` (`0x564D`, cầu VietMap kế thừa từ
     * ClusterNav). Dùng lại số đó thì hai bộ **chia nhau một tập id** ⇒ lượt dọn rác của launcher sẽ xoá id của badge
     * tốc-độ (bố cục launcher không dùng nó) ⇒ badge chết không ai hiểu vì sao.
     */
    @Test
    fun `hostId khac hostId cua cau VietMap`() {
        val re = Regex("""HOST_ID\s*=\s*(0x[0-9A-Fa-f]+|\d+)""")
        val launcherId = re.find(code(host()))?.groupValues?.get(1)
        val vietmapId = re
            .find(code(SourceRoots.text("src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt")))
            ?.groupValues?.get(1)
        assertTrue(launcherId != null && vietmapId != null, "cả hai bộ phải khai HOST_ID tường minh")
        assertTrue(
            launcherId != vietmapId,
            "hostId trùng cầu VietMap ($vietmapId) ⇒ dọn rác của launcher sẽ xoá id badge tốc-độ",
        )
    }

    /** Tầng vẽ chỉ **là bề mặt**: không được tự dựng `AppWidgetHost` (một host cho mỗi lượt dựng view = rò id). */
    @Test
    fun `tang ve khong tu dung AppWidgetHost`() {
        assertTrue(
            "AppWidgetHost(" !in code(view()),
            "WorkspaceView chỉ nhận hàm dựng view; tự tạo host thì mỗi lần dựng lại ô là một host mới ⇒ rò id",
        )
    }

    // ── Nửa còn lại: gradle có BIẾT thứ bài này quét không ────────────────────────
    /**
     * ⚠⚠ Chốt chống **dấu xanh giả** — bài học 11 ca của bộ niêm phong T11.
     *
     * Đặt bài ở đúng module chỉ là một nửa. Nếu tệp bài này quét không nằm trong đầu vào của task test thì đổi mã sản
     * phẩm sẽ cho `UP-TO-DATE` + `BUILD SUCCESSFUL` mà bài **không hề chạy lại**. Ở đây các tệp bị quét là mã nguồn
     * `:app` và bài này cũng ở `:app`, nên chúng vốn là đầu vào biên dịch — bài này khẳng định lại rằng mọi tệp bị
     * quét **thật sự đọc được**, để một lần đổi đường dẫn/đổi tên không biến bài thành no-op im lặng.
     */
    @Test
    fun `moi tep bi quet deu doc duoc that`() {
        listOf(
            "src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt",
            "src/main/java/com/byd/clusternav/launcher/AppWidgetSlotHost.kt",
            "src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt",
            "src/main/java/com/byd/clusternav/launcher/AppDrawer.kt",
            "src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt",
        ).forEach {
            assertTrue(SourceRoots.exists(it), "tệp bài này quét không tồn tại: $it (bài đã thành no-op)")
            assertTrue(SourceRoots.text(it).isNotBlank(), "đọc ra rỗng: $it")
        }
    }
}
