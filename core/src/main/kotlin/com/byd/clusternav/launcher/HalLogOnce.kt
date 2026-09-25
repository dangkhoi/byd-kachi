package com.byd.clusternav.launcher

/**
 * Sổ "đã log chưa" cho lỗi HAL — **log-once theo khoá `fqn#method`**, có trần bộ nhớ.
 *
 * Hardening 2026-09-25 · audit F5 [P2]: `BydHalGateway`/`BydHal` nuốt mọi lỗi đọc/ghi thành `null` — binder chết
 * ⇒ "mọi ô hiện —" tới 5 phút mà logcat **không một dòng** (KDoc gateway tự thừa nhận). Đường đọc ~1 400 lượt/phút
 * nên không thể log mỗi lần; một dòng W **cho mỗi khoá** là đủ để chẩn đoán qua `ClusterDiag` (CLAUDE.md §11).
 *
 *  • [first] — lần đầu thấy khoá ⇒ `true` (chỗ gọi log), lần sau ⇒ `false`.
 *  • [forgetDevice] — gateway gọi khi resolve lại device THÀNH CÔNG: HAL vừa sống lại, lỗi sau đó là lỗi mới
 *    ⇒ được log lại một lần nữa (đường phục hồi hữu hạn, cùng tinh thần `HIT_TTL_MS`).
 *  • Trần [CAP] khoá: đầy thì bỏ khoá cũ nhất (LRU theo thứ tự chèn) — lỗi mới vẫn lộ, bộ nhớ không phình.
 *
 * THUẦN (không Android; `android.util.Log` gọi ở gateway) ⇒ `HalLogOnceTest` khoá off-device.
 */
object HalLogOnce {
    const val CAP = 200

    private val seen = LinkedHashSet<String>()

    /** `true` đúng một lần cho mỗi [key] (cho tới khi [forgetDevice]/[resetForTest]). */
    @Synchronized
    fun first(key: String): Boolean {
        if (key in seen) return false
        if (seen.size >= CAP) seen.iterator().let { it.next(); it.remove() }
        seen.add(key)
        return true
    }

    /** Quên mọi khoá của [deviceFqn] (`"$fqn#…"`) — device vừa resolve lại được. */
    @Synchronized
    fun forgetDevice(deviceFqn: String) {
        seen.removeAll { it.startsWith("$deviceFqn#") }
    }

    @Synchronized
    fun sizeForTest(): Int = seen.size

    @Synchronized
    internal fun resetForTest() = seen.clear()
}
