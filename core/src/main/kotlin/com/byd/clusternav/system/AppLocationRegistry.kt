package com.byd.clusternav.system

/**
 * Vị trí DUY NHẤT của một app: trên [displayId], và nếu ở màn launcher thì ở ô [slot] (0..3); trên cụm cast
 * thì [slot] = `null`.
 */
data class AppLocation(val pkg: String, val displayId: Int, val slot: Int?)

/**
 * Registry VỊ TRÍ APP (thuần JVM :core — KHÔNG android.*, KHÔNG dadb).
 *
 * Bất biến MỘT-VỊ-TRÍ: mỗi app tồn tại ở đúng MỘT nơi tại một thời điểm — KHÔNG thể vừa nằm trong ô launcher
 * vừa nằm trên cụm ([castDisplayId]) và ngược lại. [place] tự thực thi bất biến bằng cách GHI ĐÈ vị trí cũ
 * (bản đồ khóa theo `pkg`): đặt một app lên cụm sẽ tự XÓA nó khỏi ô launcher cũ.
 *
 * Thread-safe: bản đồ vị trí giữ dưới SNAPSHOT bất biến `@Volatile`, cập nhật copy-on-write dưới lock; đọc
 * không cần khoá.
 *
 * @param castDisplayId display coi là "cụm" cho [isCastable] (mặc định [DisplayOwnershipRegistry.CAST_DISPLAY]).
 */
class AppLocationRegistry(
    private val castDisplayId: Int = DisplayOwnershipRegistry.CAST_DISPLAY,
) {
    private val lock = Any()

    @Volatile
    private var locations: Map<String, AppLocation> = emptyMap()

    /**
     * Đặt [pkg] vào [displayId] (+ [slot] nếu ở màn launcher). GHI ĐÈ vị trí cũ ⇒ thực thi bất biến
     * MỘT-VỊ-TRÍ: đặt lên cụm xóa nó khỏi ô launcher cũ (và ngược lại), không bao giờ nhân đôi.
     */
    fun place(pkg: String, displayId: Int, slot: Int? = null) {
        val loc = AppLocation(pkg, displayId, slot)
        synchronized(lock) { locations = locations + (pkg to loc) }
    }

    /** Gỡ [pkg] khỏi mọi vị trí (app đóng). Idempotent. */
    fun remove(pkg: String) {
        synchronized(lock) { locations = locations - pkg }
    }

    /** Vị trí hiện tại của [pkg], hoặc `null` nếu chưa đặt. */
    fun locationOf(pkg: String): AppLocation? = locations[pkg]

    /** Mọi app đang ở trên [displayId], sắp theo [AppLocation.slot] tăng dần rồi `pkg` (thứ tự ổn định). */
    fun onDisplay(displayId: Int): List<AppLocation> =
        locations.values
            .filter { it.displayId == displayId }
            .sortedWith(compareBy({ it.slot ?: Int.MAX_VALUE }, { it.pkg }))

    /** Snapshot mọi vị trí (đọc-only). */
    fun all(): List<AppLocation> = locations.values.toList()

    /**
     * true nếu [pkg] CÓ THỂ được chiếu lên cụm — tức nó CHƯA nằm sẵn trên [castDisplayId]. App đang ở ô
     * launcher (hoặc chưa đặt) là castable; app đã trên cụm thì KHÔNG (đã chiếu rồi). Đây là hệ quả trực tiếp
     * của bất biến MỘT-VỊ-TRÍ: không app nào vừa ở ô vừa trên cụm.
     */
    fun isCastable(pkg: String): Boolean = locationOf(pkg)?.displayId != castDisplayId
}
