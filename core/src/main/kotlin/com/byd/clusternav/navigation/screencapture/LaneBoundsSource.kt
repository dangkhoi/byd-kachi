package com.byd.clusternav.navigation.screencapture

/**
 * Vùng dải làn ĐÃ ĐO ĐƯỢC, kèm **CHỦ SỞ HỮU** (§R-BI) + số làn + mốc.
 *
 * `pkg` là package RUNTIME của cửa sổ mà rect được đo trong đó (`root.packageName`), KHÔNG phải tiền tố
 * resource-id (`NavApps.WAZE_RES_PREFIX` = `com.waze` cũng là tiền tố của WazeMod — dán nhãn bằng nó thì
 * WazeMod bị loại im lặng ở consumer, mất làn mà không có lỗi nào).
 */
data class LaneBounds(
    val pkg: String,
    val rect: CropRect,
    /** Số làn (child count của view lane-guidance) — 0 nếu không rõ (LaneSignature tự dò). */
    val laneCount: Int,
    val capturedAtMs: Long,
)

/**
 * B3 T1b — bounds của VIEW lane-guidance (a11y `findAccessibilityNodeInfosByViewId`, vd
 * `com.waze:id/laneGuidanceView`) + số làn (child count) — do `NavAccessibilityService` GHI, đọc bởi
 * `ScreenCaptureNavSource` để crop đúng vùng dải làn → [com.byd.clusternav.navigation.LaneSignature].
 *
 * Thuần `@Volatile`, không khoá (a11y ghi, capture đọc) — cùng khuôn [CaptureBoundsSource]. Toạ độ TUYỆT ĐỐI
 * trong không gian ảnh display. Chưa publish / rỗng → snapshot null → không đọc làn (degrade-safe).
 *
 * ── VÌ SAO PHẢI MANG pkg (B-I, đo 08-22) ──────────────────────────────────────────────────────────────────
 * Consumer lấy rect này rồi crop ảnh của app **đang capture** và `publishLane(pkg, …)` theo app đó. Nếu rect
 * đến từ cửa sổ app KHÁC thì crop vẫn ra pixel HỢP LỆ (một vùng bất kỳ của app đang chụp) ⇒ sinh ra dữ liệu
 * BỊA: nhãn app A, pixel app B. Tầng quyết định phía sau không có cách nào phát hiện. Vì vậy rect phải đi
 * kèm chủ, và consumer chốt bằng `NavFrameIdentity.sameFrame` ngay tại NƠI SẢN XUẤT.
 *
 * Một snapshot BẤT BIẾN (không phải 6 `@Volatile` rời) để rect / số làn / chủ không bao giờ lệch nhau.
 */
object LaneBoundsSource {

    const val FRESH_MS = 1500L

    @Volatile private var snap: LaneBounds? = null

    /**
     * a11y GHI. [pkg] KHÔNG có giá trị mặc định — mặc định = cho phép caller lặng lẽ bỏ danh tính, đúng loại
     * lỗi B-I sinh ra để chặn. pkg rỗng / rect rỗng ⇒ coi như không có bounds (snapshot null).
     */
    fun publish(pkg: String, l: Int, t: Int, r: Int, b: Int, lanes: Int, now: Long) {
        val rect = CropRect(l, t, r, b)
        snap = if (pkg.isEmpty() || rect.isEmpty() || now <= 0L) null
        else LaneBounds(pkg, rect, lanes, now)
    }

    /** Snapshot vùng dải làn. null nếu chưa publish / rỗng / vô chủ. Caller tự chấm freshness + danh tính. */
    fun snapshot(): LaneBounds? = snap

    fun clear() {
        snap = null
    }
}
