package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá DÂY NỐI của hình nền + widget trình chiếu (U4 — spec `kachi-wallpaper.html`).
 *
 * Hai test quan trọng nhất:
 *  • `nhip cua widget phai TU DON khi o bi thao` — nhịp sống lâu hơn ô là rò rỉ, và nó nạp ảnh mãi.
 *  • `widget trinh chieu chay DOC LAP voi hinh nen` — người dùng có thể muốn khung ảnh trong ô mà không đổi nền.
 */
class WallpaperWiringContractTest {

    private fun code(relative: String): String =
        SourceRoots.text(relative)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private val store by lazy { code("src/main/java/com/byd/clusternav/launcher/WallpaperStore.kt") }
    private val wall by lazy { code("src/main/java/com/byd/clusternav/launcher/WallView.kt") }
    private val photo by lazy { code("src/main/java/com/byd/clusternav/launcher/PhotoWidgetView.kt") }
    private val act by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val ws by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt") }
    private val panel by lazy { code("src/main/java/com/byd/clusternav/launcher/CustomizePanel.kt") }

    // ── R7: không thêm quyền, không dùng màn hệ thống ────────────────────────────────────────────

    @Test
    fun `khong dung man chon tep cua he thong`() {
        // [ĐO] màn hệ thống trên xe bị khoá ⇒ nút "chọn ảnh" kiểu thường dẫn người dùng vào chỗ không làm được gì.
        listOf("ACTION_GET_CONTENT", "ACTION_OPEN_DOCUMENT", "ACTION_PICK", "startActivityForResult").forEach {
            assertFalse(store.contains(it), "không được dùng '$it' để chọn ảnh")
            assertFalse(act.contains(it), "không được dùng '$it' để chọn ảnh")
        }
    }

    @Test
    fun `doc anh tu thu muc rieng cua app - khong can quyen`() {
        assertTrue(store.contains("getExternalFilesDir"),
            "thư mục riêng của app đọc được mà KHÔNG cần quyền — khác với quét bộ nhớ chung")
        listOf("READ_EXTERNAL_STORAGE", "MediaStore", "READ_MEDIA_IMAGES").forEach {
            assertFalse(store.contains(it), "không được cần quyền/kho chung ('$it')")
        }
    }

    // ── R6: ảnh to không được làm hết bộ nhớ ─────────────────────────────────────────────────────

    @Test
    fun `luon nap anh GIAM CO, khong nap nguyen ban`() {
        assertTrue(store.contains("inJustDecodeBounds"),
            "phải đọc kích thước trước (không nạp pixel) rồi mới nạp với tỉ lệ giảm")
        assertTrue(store.contains("inSampleSize"), "phải nạp giảm cỡ")
        assertTrue(store.contains("fun sampleSize("), "phải có phép tính tỉ lệ giảm")
    }

    // ── Vòng đời ảnh ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nha anh CU sau khi da thay, khong truoc`() {
        // Nhả trước thì lần vẽ kế tiếp dùng ảnh đã thu hồi và SẬP.
        listOf(act, photo).forEach { src ->
            val i = src.indexOf("old?.recycle()")
            assertTrue(i > 0, "phải nhả ảnh cũ")
            val assignIdx = src.lastIndexOf("= next", i)
            assertTrue(assignIdx in 1 until i, "phải gán ảnh MỚI trước rồi mới nhả ảnh cũ")
        }
    }

    @Test
    fun `nhip cua widget phai TU DON khi o bi thao`() {
        // Ô có thể bị removeView bất cứ lúc nào (đổi bố cục / đơn vị / hồ sơ). Nhịp sống lâu hơn ô = rò rỉ + nạp ảnh mãi.
        assertTrue(photo.contains("override fun onDetachedFromWindow()"), "phải bắt lúc ô bị tháo")
        val det = photo.substringAfter("override fun onDetachedFromWindow()").substringBefore("private fun step(")
        assertTrue(det.contains("removeCallbacks(tick)"), "phải dừng nhịp")
        assertTrue(det.contains("recycle()"), "phải nhả ảnh")
        assertTrue(det.contains("running = false"), "phải hạ cờ để nhịp đang chờ không chạy tiếp")
    }

    @Test
    fun `doc va giai ma anh KHONG chay tren thread chinh`() {
        // [SOÁT P2-2] quét thư mục + giải mã ảnh nhiều megapixel trên thread chính ngay lúc về HOME ⇒ đứng hình.
        val fn = act.substringAfter("private fun reloadWallpaper()").substringBefore("private fun stepWallpaper")
        assertTrue(fn.contains("winExec.execute"), "quét thư mục phải chạy ở thread nền")
        assertTrue(fn.contains("runOnUiThread"), "chỉ phần đưa vào View mới ở thread chính")
        val st = act.substringAfter("private fun stepWallpaper").substringBefore("private fun wallReqW")
        assertTrue(st.contains("winExec.execute"), "giải mã ảnh phải chạy ở thread nền")
        assertTrue(st.contains("WallpaperStore.loadScaled") &&
            st.indexOf("winExec.execute") < st.indexOf("WallpaperStore.loadScaled"),
            "lệnh giải mã phải nằm BÊN TRONG khối thread nền")
    }

    @Test
    fun `luot nap cu ve muon thi bo, khong ghi de luot moi`() {
        val st = act.substringAfter("private fun stepWallpaper").substringBefore("private fun wallReqW")
        assertTrue(st.contains("gen != wallDecodeGen"), "phải có thẻ thế hệ cho việc giải mã")
        assertTrue(st.contains("next?.recycle()"), "lượt bị bỏ phải nhả ảnh, không thì rò rỉ")
        assertTrue(st.contains("isDestroyed"), "màn đã huỷ thì không được chạm View")
    }

    @Test
    fun `quet thu muc va giai ma dung HAI the the he RIENG`() {
        // ⚠ Bản vá đầu dùng MỘT thẻ chung ⇒ nhịp trình chiếu (tăng thẻ) chạy trước lúc quét thư mục về ⇒ lượt quét
        // bị BỎ OAN ⇒ ảnh mới thêm / ảnh vừa xoá / chu kỳ vừa đổi không được nhận, im lặng dùng dữ liệu cũ.
        assertTrue(act.contains("wallScanGen") && act.contains("wallDecodeGen"),
            "phải có hai thẻ riêng cho hai việc chạy ở thread nền")
        val re = act.substringAfter("private fun reloadWallpaper()").substringBefore("private fun stepWallpaper")
        assertTrue(re.contains("wallScanGen") && !re.contains("wallDecodeGen"),
            "lượt quét thư mục chỉ được dùng thẻ quét")
        val st = act.substringAfter("private fun stepWallpaper").substringBefore("private fun wallReqW")
        assertTrue(st.contains("wallDecodeGen") && !st.contains("wallScanGen"),
            "lượt giải mã chỉ được dùng thẻ giải mã")
    }

    @Test
    fun `nhip dung khi HOME bi che`() {
        // [SOÁT P2-1] HOME bị app khác che thì ô KHÔNG bị tháo ⇒ nhịp cũ vẫn đọc đĩa + giải mã ảnh cho thứ không ai
        // xem. Lái ba giờ là hàng trăm lượt vô ích.
        assertTrue(photo.contains("override fun onWindowVisibilityChanged"),
            "phải dừng nhịp khi cửa sổ không còn hiện")
        val fn = photo.substringAfter("override fun onWindowVisibilityChanged").substringBefore("private fun tickDelayMs")
        assertTrue(fn.contains("removeCallbacks(tick)"), "phải dừng nhịp")
        assertTrue(fn.contains("running = false"), "phải hạ cờ")
    }

    @Test
    fun `nen giai ma theo co MAN HINH, khong theo co view chua do`() {
        // [SOÁT P1-2] ảnh nền nạp trong onResume, mà lượt đo cây view chạy SAU onResume ⇒ lần mở đầu view rộng 0
        // ⇒ ảnh bị giảm còn 1–2 điểm rồi kéo lên phủ kín màn = một vệt màu loang, không có gì nạp lại.
        // [ĐO] chứng minh: hoàn nguyên bản vá ⇒ nền phẳng biên độ 0; có bản vá ⇒ biên độ 116, 27 lần đổi sáng/tối.
        assertTrue(act.contains("fun wallReqW()") && act.contains("fun wallReqH()"),
            "phải có cỡ cần riêng, lấy theo màn hình")
        val fn = act.substringAfter("private fun wallReqW()").substringBefore("private fun releaseWallBitmap")
        assertTrue(fn.contains("displayMetrics"), "phải lấy cỡ theo MÀN HÌNH, không chỉ theo view")
        assertFalse(act.contains("loadScaled(path, wall.width"),
            "không được lấy cỡ view chưa qua lượt đo")
    }

    @Test
    fun `co o doi thi nap lai o dung co, khong phong anh cu`() {
        assertTrue(photo.contains("override fun onSizeChanged"), "cỡ ô đổi thì phải nạp lại")
    }

    // ── Độc lập giữa nền và widget ───────────────────────────────────────────────────────────────

    @Test
    fun `widget trinh chieu chay DOC LAP voi hinh nen`() {
        // Kiểm LUẬT, không kiểm vị trí dòng: nguồn ảnh cho widget phải được nạp **kể cả khi nền đang tắt**.
        // (Test cũ so vị trí nên vỡ khi việc nạp chuyển sang thread nền, dù luật không đổi.)
        val fn = act.substringAfter("private fun reloadWallpaper()").substringBefore("private fun stepWallpaper")
        assertTrue(fn.contains("setPhotoSource("), "phải nạp nguồn ảnh cho widget")
        // Nhánh "nền đang tắt" KHÔNG được thoát hàm — thoát là widget mất nguồn ảnh.
        val off = fn.substringAfter("if (!wallPrefs.enabled) {").substringBefore("}")
        assertFalse(off.contains("return"),
            "nhánh 'nền đang tắt' không được thoát hàm, không thì widget trình chiếu mất nguồn ảnh")
        // Và trong khối chạy ở thread chính, nguồn ảnh phải đặt TRƯỚC chỗ bỏ qua phần nền.
        val ui = fn.substringAfter("runOnUiThread {")
        assertTrue(ui.indexOf("setPhotoSource(") in 0 until ui.indexOf("if (!wallPrefs.enabled) return"),
            "nguồn ảnh cho widget phải đặt trước chỗ bỏ qua phần hình nền")
    }

    @Test
    fun `doi nguon anh chi dung lai o widget, khong dung lai o dang chieu app`() {
        val fn = ws.substringAfter("fun setPhotoSource(").substringBefore("init {")
        assertTrue(fn.contains("rebuildWidgetSlots()"),
            "phải dựng lại CHỈ ô widget — dựng lại ô đang chiếu app là ngắt kênh chạm (C5)")
        assertTrue(fn.contains("if (changed)"), "gọi lại với cùng nguồn thì không được làm gì")
    }

    // ── Mặc định không đổi gì ────────────────────────────────────────────────────────────────────

    @Test
    fun `tat thi ve dung nen cu`() {
        assertTrue(wall.contains("RadialGradient"), "nền vẽ sẵn phải còn nguyên làm đường lùi")
        val draw = wall.substringAfter("override fun onDraw").substringBefore("private fun drawPhoto")
        assertTrue(draw.contains("p != null && !p.isRecycled"), "chỉ vẽ ảnh khi thật sự có ảnh dùng được")
    }

    @Test
    fun `bang Tuy bien noi RO cho bo anh vao`() {
        // Người dùng không có cách nào tự đoán đường dẫn.
        assertTrue(panel.contains("wallpaperFolderHint"), "phải nhận đường dẫn để hiện ra")
        assertTrue(act.contains("WallpaperStore.folderHint("), "chỗ gọi phải truyền đường dẫn thật")
    }

    @Test
    fun `luon tao san thu muc anh - tranh vong lap chet`() {
        // [ĐO] nếu chỉ tạo lúc bật thì: muốn thấy ảnh phải bật, muốn bật phải bỏ ảnh vào trước, mà thư mục chưa có.
        // Kiểm LUẬT: lệnh tạo thư mục KHÔNG được nằm trong nhánh "đang bật".
        val fn = act.substringAfter("private fun reloadWallpaper()").substringBefore("private fun stepWallpaper")
        assertTrue(fn.contains("WallpaperStore.folder("), "phải tạo thư mục")
        val off = fn.substringAfter("if (!wallPrefs.enabled) {").substringBefore("}")
        assertFalse(off.contains("return"), "nhánh tắt không được thoát trước khi tạo thư mục")
        // Lệnh tạo thư mục nằm trong khối nền, chạy vô điều kiện (không bị bọc bởi cổng bật/tắt nào).
        val bg = fn.substringAfter("winExec.execute {").substringBefore("runOnUiThread {")
        assertTrue(bg.contains("WallpaperStore.folder("),
            "tạo thư mục phải chạy vô điều kiện ở thread nền, không bị cổng bật/tắt che")
    }
}
