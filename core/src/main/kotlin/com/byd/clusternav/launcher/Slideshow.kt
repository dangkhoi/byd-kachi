package com.byd.clusternav.launcher

/**
 * HÌNH NỀN + TRÌNH CHIẾU ẢNH (U4) — phần QUYẾT ĐỊNH, thuần Kotlin (`:core`, cấm `android.*`) ⇒ test off-car.
 * Spec: `docs/specs/kachi-wallpaper.html`.
 *
 * Owner muốn hai thứ: **(a)** hình nền cho cả launcher; **(b)** một widget **trình chiếu** — chọn sẵn bộ ảnh, định
 * kỳ tự đổi.
 *
 * ## Vì sao logic đổi ảnh nằm ở đây chứ không ở tầng vẽ
 * "Đã đến lúc đổi ảnh chưa" là một quyết định **theo thời gian**, và nếu để trong View thì muốn test phải chờ thật.
 * Tách ra thì test đủ mọi ca (một ảnh · không ảnh · vừa đúng hạn · quá hạn nhiều lần · chỉ số ra ngoài) **tức thời**.
 *
 * ## ⚠ Ảnh lấy từ đâu — ràng buộc của XE
 * Cố ý **KHÔNG** dùng màn chọn tệp của hệ thống: [ĐO] trên xe các màn hệ thống bị khoá (*"Hệ thống IVI không hỗ trợ
 * hoạt động này"* — xem spec vòng kiểm quyền). Cũng **KHÔNG** thêm quyền đọc bộ nhớ (quyền đó cần cấp lúc chạy).
 * Thay vào đó đọc từ **thư mục riêng của app ở bộ nhớ ngoài** — chỗ **không cần quyền nào** và app đã dùng sẵn cho
 * việc xuất log. Người dùng bỏ ảnh vào đó là xong.
 */

/** Cách phủ ảnh lên khung. */
enum class ImageFit {
    /** Phủ kín khung, cắt phần thừa — dùng cho hình nền (không ai muốn thấy viền đen ở nền). */
    FILL,

    /** Vừa trong khung, giữ nguyên tỉ lệ, có thể còn viền — dùng khi muốn thấy trọn ảnh. */
    FIT;

    val label: String get() = if (this == FILL) "Phủ kín" else "Vừa khung"
}

/** Trạng thái RUNTIME của một trình chiếu: đang ở ảnh nào, đổi lần cuối lúc nào. */
data class SlideshowState(val index: Int = 0, val lastChangeMs: Long = 0L)

/** Quyết định đổi ảnh — thuần, không thời gian thật. */
object Slideshow {

    /** Các chu kỳ cho người dùng chọn (giây). Ngắn nhất 15 giây; ngắn hơn thì thành nhấp nháy, không phải trình chiếu. */
    val INTERVAL_CHOICES_SEC: List<Int> = listOf(15, 30, 60, 300, 900, 1800)

    const val DEFAULT_INTERVAL_SEC = 60

    /** Nhãn cho người đọc, tránh bắt họ tự quy đổi giây. */
    fun intervalLabel(sec: Int): String = when {
        sec < 60 -> "$sec giây"
        sec < 3600 -> "${sec / 60} phút"
        else -> "${sec / 3600} giờ"
    }

    /**
     * Trạng thái kế tiếp.
     *
     * Luật:
     *  • [count] ≤ 1 ⇒ **không bao giờ đổi** (một ảnh thì đổi đi đâu; không ảnh thì không có gì mà đổi);
     *  • chưa tới hạn ⇒ giữ nguyên **cả** chỉ số **và** mốc thời gian (đổi mốc mà không đổi ảnh sẽ làm hạn bị đẩy
     *    lùi mãi ⇒ ảnh không bao giờ đổi — lỗi kinh điển của kiểu đếm này);
     *  • tới hạn ⇒ sang ảnh kế, vòng lại đầu, và **đặt mốc = [nowMs]**;
     *  • chỉ số ra ngoài phạm vi (danh sách ảnh vừa bị bớt) ⇒ **về 0** thay vì sập.
     */
    fun next(state: SlideshowState, count: Int, nowMs: Long, intervalSec: Int = DEFAULT_INTERVAL_SEC): SlideshowState {
        if (count <= 1) return state.copy(index = if (count <= 0) 0 else state.index.coerceIn(0, count - 1))
        val safeIndex = if (state.index in 0 until count) state.index else 0
        val dueMs = state.lastChangeMs + intervalSec.coerceAtLeast(1) * 1000L
        // Mốc = 0 nghĩa là CHƯA từng đổi ⇒ tính là tới hạn ngay, để ảnh đầu tiên hiện mà không phải chờ một chu kỳ.
        val due = state.lastChangeMs <= 0L || nowMs >= dueMs
        return if (due) SlideshowState((safeIndex + 1).mod(count), nowMs) else state.copy(index = safeIndex)
    }

    /** Ảnh đang chọn, hoặc `null` nếu danh sách rỗng / chỉ số sai (chỗ gọi vẽ nền mặc định). */
    fun pick(paths: List<String>, index: Int): String? =
        if (paths.isEmpty()) null else paths[if (index in paths.indices) index else 0]

    /** Đuôi tệp coi là ảnh. Cố ý HẸP: chỉ nhận thứ Android chắc chắn giải mã được. */
    val IMAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp")

    /** Tệp này có phải ảnh (theo đuôi, không phân biệt hoa thường). */
    fun isImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /**
     * Lọc + **sắp thứ tự ổn định** danh sách tệp. Ổn định là quan trọng: nếu thứ tự đổi mỗi lần đọc thư mục thì
     * trình chiếu sẽ nhảy loạn thay vì chạy vòng.
     */
    fun imagesFrom(names: List<String>): List<String> = names.filter { isImage(it) }.sorted()
}

/**
 * Lựa chọn của người dùng cho hình nền + trình chiếu. Bất biến; lưu CHUNG mọi hồ sơ tài xế (hình nền là thứ nhìn
 * thấy cả màn, không phải thuộc tính của một hồ sơ — cùng lối với giao diện sáng/tối và đơn vị).
 *
 * @property enabled bật hình nền từ ảnh. TẮT ⇒ dùng nền vẽ sẵn (gradient) như trước ⇒ **không đổi gì cho người
 *   không quan tâm**.
 * @property intervalSec chu kỳ đổi ảnh.
 * @property fit cách phủ ảnh.
 * @property dimPercent làm tối ảnh bao nhiêu phần trăm — **cần thiết, không phải trang trí**: chữ và ô của launcher
 *   là màu sáng trên nền tối; ảnh sáng sẽ làm chữ không đọc được.
 */
data class WallpaperPrefs(
    val enabled: Boolean = false,
    val intervalSec: Int = Slideshow.DEFAULT_INTERVAL_SEC,
    val fit: ImageFit = ImageFit.FILL,
    val dimPercent: Int = DEFAULT_DIM_PERCENT,
) {
    /** Chỉ số 0..100, cắt về phạm vi hợp lệ. */
    val dim: Int get() = dimPercent.coerceIn(0, 90)

    fun encode(): String = "$enabled;$intervalSec;${fit.name};$dimPercent"

    companion object {
        /** Mặc định làm tối 45%: đủ để chữ sáng đọc được trên phần lớn ảnh, chưa tới mức mất hẳn ảnh. */
        const val DEFAULT_DIM_PERCENT = 45

        val DEFAULT = WallpaperPrefs()

        /** Giải mã; thiếu/rác ⇒ lấy mặc định cho phần đó, KHÔNG sập và KHÔNG mất phần đọc được. */
        fun decode(s: String?): WallpaperPrefs {
            if (s.isNullOrBlank()) return DEFAULT
            val p = s.split(";")
            return WallpaperPrefs(
                enabled = p.getOrNull(0)?.trim()?.toBooleanStrictOrNull() ?: DEFAULT.enabled,
                intervalSec = p.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT.intervalSec,
                fit = p.getOrNull(2)?.trim()?.let { n -> ImageFit.values().firstOrNull { it.name == n } } ?: DEFAULT.fit,
                dimPercent = p.getOrNull(3)?.trim()?.toIntOrNull() ?: DEFAULT.dimPercent,
            )
        }
    }
}
