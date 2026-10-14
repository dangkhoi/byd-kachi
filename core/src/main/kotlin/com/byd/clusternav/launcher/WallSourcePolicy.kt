package com.byd.clusternav.launcher

/**
 * 3.1.5 — NGUỒN của hình nền đang hiện, phần QUYẾT ĐỊNH thuần (`:core`) ⇒ test off-car.
 *
 * Owner 14/10: bộ ảnh của owner (ảnh riêng + hình nền BYD Seal) là nền MẶC ĐỊNH của Kachi — nhưng ảnh BYD có bản quyền mà
 * repo công khai ⇒ bộ ảnh CHỈ được đóng vào APK dựng tại máy owner (`assets/wall-default/`, gitignored). Bản dựng từ một
 * bản clone sạch không có thư mục đó ⇒ phải tự lùi về nền vẽ bằng mã ([Source.DRAWN], `WallDefault`) mà không lỗi gì.
 *
 * Thứ tự ưu tiên (không bao giờ đảo):
 *  1. [Source.USER] — người dùng/hồ sơ BẬT *Dùng ảnh làm hình nền* VÀ thư mục ảnh của họ có ảnh ⇒ luôn thắng;
 *  2. [Source.BUNDLED] — chưa có lựa chọn của người dùng (tắt, hoặc bật mà thư mục trống) VÀ APK có bộ ảnh đóng gói;
 *  3. [Source.DRAWN] — còn lại: nền "phòng trưng bày" vẽ bằng mã (3.01 R22), như trước.
 */
object WallSourcePolicy {

    enum class Source { USER, BUNDLED, DRAWN }

    /** Kết quả: nguồn + danh sách ảnh sẽ chiếu (rỗng ⇔ [Source.DRAWN]). */
    data class Resolved(val source: Source, val paths: List<String>)

    fun resolve(userEnabled: Boolean, userImages: List<String>, bundled: List<String>): Resolved = when {
        userEnabled && userImages.isNotEmpty() -> Resolved(Source.USER, userImages)
        bundled.isNotEmpty() -> Resolved(Source.BUNDLED, bundled)
        else -> Resolved(Source.DRAWN, emptyList())
    }

    /**
     * Tên ảnh đóng gói từ danh sách thư mục asset ([names] = `AssetManager.list`, `null` = không đọc được) — cùng bộ lọc +
     * thứ tự ổn định với thư mục ảnh của người dùng ([Slideshow.imagesFrom]). Không có danh sách cứng nào trong mã.
     */
    fun bundledFrom(names: List<String>?): List<String> = names?.let { Slideshow.imagesFrom(it) } ?: emptyList()
}
