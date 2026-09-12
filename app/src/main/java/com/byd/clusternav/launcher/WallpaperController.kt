package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import com.byd.clusternav.R

/**
 * BỘ ĐIỀU KHIỂN HÌNH NỀN (U4) — sở hữu [WallView] + trạng thái trình chiếu, tách khỏi [KachiHomeActivity].
 *
 * ## Vì sao tách
 * [ĐO] sau vòng vá, `KachiHomeActivity` phình lên **678 dòng** (trần dự án 500, thiết kế ban đầu ~250) và mang 8
 * chức trách. Khối hình nền là mảng **liền mạch và tự chứa** nhất (quét thư mục · nhịp trình chiếu · thẻ thế hệ ·
 * ảnh đang nạp · cỡ cần), nên tách nó ra là cắt đúng đường khớp — cùng mẫu đã dùng cho [LauncherWindows] /
 * [DrawerController] / [KachiTopStrip]: nhận **cổng vào bằng lambda**, không tự biết Activity.
 *
 * Hành vi giữ **y nguyên từng dòng** — chỉ đổi chỗ ở, không đổi luật. Các bất biến đã trả giá để có:
 *  • thẻ thế hệ **RIÊNG** cho quét thư mục và cho giải mã (dùng chung ⇒ lượt quét bị bỏ oan);
 *  • tắt hình nền phải **huỷ lượt giải mã đang bay** VÀ callback phải kiểm lại công tắc (không thì ảnh quay lại);
 *  • cỡ cần lấy theo **màn hình**, không theo cỡ View (View chưa đo xong ⇒ giải mã ở cỡ 1 ⇒ vệt màu loang);
 *  • chưa tới hạn thì giữ NGUYÊN cả chỉ số LẪN mốc thời gian (cập nhật mốc mà không đổi ảnh ⇒ ảnh không bao giờ đổi).
 *
 * @param prefs lựa chọn hình nền hiện hành — đọc từ **nguồn sự thật** (`HomeUiState`), KHÔNG giữ bản sao.
 * @param submitIo cửa đẩy việc xuống thread nền **riêng cho I/O ảnh** (xem `KachiHomeActivity.submitIo`).
 * @param onUi chạy khối trên thread chính.
 * @param gone màn đã huỷ/đang huỷ ⇒ callback về muộn phải im.
 * @param onPhotoSource đẩy danh sách ảnh xuống widget trình chiếu — chạy **kể cả khi nền đang tắt** (widget độc lập).
 */
class WallpaperController(
    private val ctx: Context,
    private val wall: WallView,
    private val prefs: () -> WallpaperPrefs,
    private val submitIo: (() -> Unit) -> Unit,
    private val onUi: (() -> Unit) -> Unit,
    private val gone: () -> Boolean,
    private val onPhotoSource: (List<String>, Int) -> Unit,
) {
    private var slide = SlideshowState()
    private var wallImages: List<String> = emptyList()

    /** Thẻ thế hệ RIÊNG cho hai việc nền — xem KDoc lớp. */
    private var wallScanGen = 0
    private var wallDecodeGen = 0

    /** Ảnh đang nạp (đường dẫn) — chặn nạp trùng khi nhịp tới trước lúc nạp xong. */
    private var wallLoading: String? = null
    private var wallBitmap: Bitmap? = null

    /**
     * Nạp lại lựa chọn + danh sách ảnh rồi vẽ ngay. Gọi lúc mở màn và mỗi khi người dùng đổi lựa chọn.
     *
     * TẮT (mặc định) ⇒ nhả ảnh và để [WallView] vẽ nền gradient như trước ⇒ người không dùng tính năng này
     * **không thấy gì khác**.
     */
    fun reload() {
        // Lựa chọn đọc từ state (nguồn duy nhất). Lượt mở màn đầu tiên đã được `repository.load()` nạp vào state.
        // Tạo thư mục ảnh NGAY, kể cả khi tính năng đang tắt. [ĐO] máy ảo 2026-09-11: nếu chỉ tạo lúc bật thì người
        // dùng gặp vòng lặp chết — muốn thấy ảnh phải bật, muốn bật có nghĩa phải bỏ ảnh vào trước, mà thư mục lại
        // chưa tồn tại để mà bỏ vào.
        if (!prefs().enabled) {
            wall.setPhoto(null)
            release()
            wallImages = emptyList()
            // [SOÁT P1-3] Phải HUỶ luôn lượt giải mã đang bay. Trước bản vá này chỗ này chỉ tăng thẻ QUÉT, nên một
            // lượt giải mã bắt đầu TRƯỚC khi tắt vẫn về sau và gọi `wall.setPhoto(next…)` ⇒ **ảnh quay trở lại** sau
            // khi người dùng đã tắt, và ở đó tới lần nạp kế tiếp. Cùng loại hồi quy thẻ-thế-hệ đã vá trước đó, chỉ
            // đổi hướng ⇒ tăng thẻ GIẢI MÃ + nhả chốt đang-nạp.
            wallDecodeGen++
            wallLoading = null
        }
        // [SOÁT P2-2] Tạo thư mục + quét thư mục + giải mã ảnh đều là I/O. Trước đây cả ba chạy trên thread chính
        // NGAY trong lúc về màn chính ⇒ đứng hình mỗi lần về HOME. Nay đẩy sang thread nền có sẵn (cùng nơi các
        // lệnh cửa sổ đã dùng), chỉ đưa ảnh vào View trên thread chính.
        val gen = ++wallScanGen
        submitIo {
            WallpaperStore.folder(ctx)
            val paths = WallpaperStore.images(ctx)
            onUi {
                // Lượt QUÉT cũ về muộn hơn lượt quét mới ⇒ bỏ. Dùng thẻ riêng của việc quét: dùng chung thẻ với việc
                // giải mã thì nhịp trình chiếu sẽ làm lượt quét bị bỏ oan (xem KDoc wallScanGen).
                if (gen != wallScanGen || gone()) return@onUi
                onPhotoSource(paths, prefs().intervalSec)
                if (!prefs().enabled) return@onUi
                wallImages = paths
                slide = SlideshowState()          // đổi lựa chọn ⇒ bắt đầu lại từ ảnh đầu
                step(force = true)
            }
        }
    }

    /**
     * Một nhịp trình chiếu. Chạy trên nhịp 10 giây có sẵn của thanh trên — cố ý KHÔNG dựng thêm vòng đếm riêng
     * (thêm một vòng nữa là thêm một thứ phải nhớ dừng lúc huỷ màn).
     *
     * Nhịp 10 giây với chu kỳ ngắn nhất 15 giây ⇒ sai số tối đa 10 giây. Với trình chiếu ảnh thì đó là **không ai
     * thấy**; đổi lấy việc không có vòng đếm thứ hai là đáng.
     */
    fun step(force: Boolean = false) {
        if (!prefs().enabled) return
        if (wallImages.isEmpty()) {
            // Bật mà chưa có ảnh: KHÔNG im lặng — nói chỗ bỏ ảnh vào, vì người dùng không có cách nào tự đoán.
            if (force) {
                Log.i("Wallpaper", "bật nhưng chưa có ảnh; bỏ ảnh vào: ${WallpaperStore.folderHint(ctx)}")
                runCatching {
                    Toast.makeText(
                    ctx, ctx.getString(R.string.kachi_wall_no_photos, WallpaperStore.folderHint(ctx)),
                    Toast.LENGTH_LONG,
                ).show()
                }
            }
            wall.setPhoto(null)
            return
        }
        val before = slide
        slide = Slideshow.next(slide, wallImages.size, System.currentTimeMillis(), prefs().intervalSec)
        if (!force && slide.index == before.index && wall.hasPhoto()) return   // chưa tới hạn ⇒ không nạp lại
        val path = Slideshow.pick(wallImages, slide.index) ?: return
        if (wallLoading == path) return    // đang nạp đúng ảnh này rồi
        val reqW = wallReqW(); val reqH = wallReqH()
        val shown = slide.index + 1; val total = wallImages.size
        // [SOÁT P2-2] Giải mã ảnh là việc nặng nhất ở đây (ảnh nhiều megapixel) ⇒ chạy ở thread nền.
        val gen = ++wallDecodeGen
        wallLoading = path
        submitIo {
            val next = WallpaperStore.loadScaled(path, reqW, reqH)
            onUi {
                if (wallLoading == path) wallLoading = null
                if (gen != wallDecodeGen || gone()) { next?.recycle(); return@onUi }
                // [SOÁT P1-3] lớp thứ HAI: kiểm lại công tắc ngay lúc ảnh về. Thẻ thế hệ chặn ca "đổi lựa chọn",
                // còn cờ này chặn ca "người dùng vừa TẮT" và ca "đổi hồ sơ" — hai đường tới cùng một hậu quả.
                if (!prefs().enabled) { next?.recycle(); return@onUi }
                if (next == null) {
                    Log.w("Wallpaper", "ảnh không giải mã được, giữ nền hiện tại: ảnh thứ $shown/$total")
                    return@onUi
                }
                val old = wallBitmap
                wallBitmap = next
                wall.setPhoto(next, prefs().fit, prefs().dim)
                // Nhả ảnh CŨ sau khi đã đưa ảnh mới vào View — nhả trước thì lần vẽ kế tiếp dùng ảnh đã thu hồi và sập.
                old?.recycle()
            }
        }
    }

    /**
     * Cỡ cần cho ảnh nền — lấy theo **MÀN HÌNH**, không theo cỡ View.
     *
     * ## [SOÁT P1-2] Vì sao không dùng cỡ View
     * Ảnh nền được nạp trong `onResume`, mà **lượt đo cây view chạy SAU `onResume`** ⇒ lần mở đầu View còn rộng 0
     * ⇒ cỡ cần = 1 ⇒ ảnh bị giảm tới mức tối đa (còn 1–2 điểm ảnh) rồi kéo lên phủ kín màn = **một vệt màu loang**,
     * và không có gì nạp lại cho tới lần đổi ảnh kế tiếp (mặc định 60 giây, chọn được tới 30 phút).
     *
     * ⚠ Phép đo của tôi **không bắt được** vì lượt đầu tôi dùng **ảnh đơn sắc** — ảnh 1 điểm kéo lên trông y hệt ảnh
     * thật. Bài học: ảnh đơn sắc che được cả méo hình LẪN mất chi tiết.
     */
    private fun wallReqW(): Int = maxOf(wall.width, ctx.resources.displayMetrics.widthPixels, 1)

    private fun wallReqH(): Int = maxOf(wall.height, ctx.resources.displayMetrics.heightPixels, 1)

    fun release() {
        wallBitmap?.recycle()
        wallBitmap = null
    }
}
