package com.byd.clusternav.launcher

/**
 * ═══ SỔ SỞ HỮU MÀN ẢO THEO Ô (thuần JVM :core — KHÔNG android.*) ══════════════════════════════════════════════
 *
 * Mỗi ô App được chiếu bằng một `VirtualDisplay` do `VdAppHost` tạo. Trước H2, đường **giải phóng duy nhất** là
 * `View.onDetachedFromWindow` — nghĩa là màn ảo chỉ chết khi cây view bị tháo. [ĐO] 2026-09-14 trên
 * `emulator-5554`: `dumpsys display` có **4** thiết bị `kachi-slot-*` cho **2** ô App, trong đó 2 cái cũ ở
 * `state OFF` (mặt vẽ đã rời) — cùng lúc `dumpsys activity activities` cho thấy **4** `KachiHomeActivity` (t790,
 * t796, t810 mang cờ `f` = đang kết thúc, t816 đang chạy). Một màn Kachi "đang kết thúc" vẫn **giữ view chưa
 * tháo** ⇒ màn ảo của nó sống tiếp, chồng lên màn ảo của màn Kachi mới. Vòng đời của một tài nguyên hệ thống
 * KHÔNG được treo vào một sự kiện mà hệ điều hành có quyền hoãn vô thời hạn.
 *
 * Sổ này đặt lại bất biến theo **Ô**, không theo view: **mỗi ô nhiều nhất MỘT màn ảo sống**. Ai [adopt] một ô thì
 * màn ảo cũ của chính ô đó — kể cả do một **chủ khác** (màn Kachi đời trước) tạo — bị trả về cho chỗ gọi giải
 * phóng. Nhờ vậy màn Kachi mới tự dọn rác của màn Kachi cũ, không cần chờ nó chết.
 *
 * Giữ THUẦN (generic [T] thay cho `VirtualDisplay`) để test off-device đếm được số màn ảo sống; lớp bọc Android
 * là `com.byd.clusternav.launcher.SlotVdOwner` ở `:app` — nó mới biết `release()`/`unregister` là gì.
 *
 * Thread-safe: mọi lối vào đều dưới `lock` (host gọi từ luồng UI, nhưng `releaseOwner` có thể tới từ `onDestroy`
 * của một màn khác).
 */
class SlotVdLedger<T> {

    /** Một màn ảo đang sống: [owner] = chủ (một cây workspace), [slot] = chỉ số ô, [name] = tên VD, [handle] = tay cầm. */
    data class Entry<T>(val owner: String, val slot: Int, val name: String, val handle: T)

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry<T>>()

    /**
     * [owner] nhận [slot] với màn ảo [name]/[handle]. Trả về **danh sách phải giải phóng**: mọi entry đang giữ
     * CÙNG ô (kể cả của chủ khác). Gọi lại với cùng [name] là **idempotent** — không tự trả về chính nó.
     */
    fun adopt(owner: String, slot: Int, name: String, handle: T): List<Entry<T>> = synchronized(lock) {
        // Cùng MỘT màn ảo đăng ký lại (vd view gắn lại) ⇒ chỉ đổi khoá, KHÔNG được đem giải phóng.
        entries.values.filter { it.name == name }.forEach { entries.remove(key(it.owner, it.slot)) }
        val stale = entries.values.filter { it.slot == slot }
        stale.forEach { entries.remove(key(it.owner, it.slot)) }
        entries[key(owner, slot)] = Entry(owner, slot, name, handle)
        stale
    }

    /** [owner] nhả [slot]. Trả entry phải giải phóng, hoặc `null` nếu không có (gọi lại lần hai ⇒ `null`). */
    fun release(owner: String, slot: Int): Entry<T>? = synchronized(lock) { entries.remove(key(owner, slot)) }

    /** [owner] nhả MỌI ô của mình (màn Kachi huỷ). Không đụng tới ô mà chủ khác đã nhận. */
    fun releaseOwner(owner: String): List<Entry<T>> = synchronized(lock) {
        val mine = entries.values.filter { it.owner == owner }
        mine.forEach { entries.remove(key(it.owner, it.slot)) }
        mine
    }

    /** Ảnh chụp các màn ảo đang sống (để đếm/ghi nhật ký). */
    fun live(): List<Entry<T>> = synchronized(lock) { entries.values.toList() }

    private fun key(owner: String, slot: Int) = "$owner#$slot"
}
