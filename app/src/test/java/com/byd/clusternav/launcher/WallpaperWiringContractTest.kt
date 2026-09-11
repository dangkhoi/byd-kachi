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
    fun `co o doi thi nap lai o dung co, khong phong anh cu`() {
        assertTrue(photo.contains("override fun onSizeChanged"), "cỡ ô đổi thì phải nạp lại")
    }

    // ── Độc lập giữa nền và widget ───────────────────────────────────────────────────────────────

    @Test
    fun `widget trinh chieu chay DOC LAP voi hinh nen`() {
        val fn = act.substringAfter("private fun reloadWallpaper()")
        val gate = fn.indexOf("if (!wallPrefs.enabled)")
        val photoLoad = fn.indexOf("setPhotoSource(")
        assertTrue(photoLoad in 1 until gate,
            "nguồn ảnh cho widget phải nạp TRƯỚC cổng bật/tắt nền — người dùng có thể muốn khung ảnh trong ô mà " +
                "KHÔNG đổi nền màn hình")
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
        val fn = act.substringAfter("private fun reloadWallpaper()")
        val mk = fn.indexOf("WallpaperStore.folder(")
        val gate = fn.indexOf("if (!wallPrefs.enabled)")
        assertTrue(mk in 1 until gate, "phải tạo thư mục TRƯỚC cổng bật/tắt")
    }
}
