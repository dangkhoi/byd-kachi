package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.DisplayParse

/**
 * Chọn id logical-display của CỤM một cách GENERIC — dò `fission`/`xdja` từ `dumpsys display`, KHÔNG hardcode
 * `1` (regression X2: cụm là display **2** trên DiLink3.0 2026-09-14, `fission_bg_xdjaVirtualSurface`).
 *
 * Vì sao tồn tại (CLAUDE.md §5 "kiểm bằng sự thật, không cờ RAM" · §7 generic không hardcode): đường chiếu cũ
 * đã-chứng-minh-trên-xe (`ClusterCast.cast()`, git HEAD:471-478) LUÔN dò VD động bằng
 * [DisplayParse.clusterDisplayId] và không bao giờ giả định id. Đây là parser THUẦN (không đụng Android) →
 * unit-test off-device (CLAUDE.md §10).
 *
 * **Regression 2026-09-15 (spec `kachi-hal187-cast-remediation` §4.1, R1/R2)** — bản trước có `savedId`/`fallback`:
 * dò hụt → trả seed (1). [ĐO] sau reboot, display 1 = `kachi-slot-0` (VD của CHÍNH launcher), cụm = display 2;
 * VD cụm chỉ được AutoContainer tạo SAU khi mở projection, mà coordinator dò TRƯỚC khi mở ⇒ hụt ⇒ seed 1 ⇒
 * ClusterBlack + GMaps đặt vào ô launcher. Từ nay:
 *   - **KHÔNG fallback.** Dò hụt ⇒ trả `-1`; caller KHÔNG được đặt gì lên seed/saved (R1).
 *   - **Guard owner (R2).** id dò ra mà thuộc [DisplayParse.ownedVirtualDisplayIds] của [selfPackage] ⇒ coi như
 *     CHƯA dò được (`-1`) — cụm không bao giờ là VD của chính launcher.
 *   - **Dò SAU khi mở projection, có vòng lặp** ([awaitAndPersist]) — đúng thứ tự của đường proven cũ.
 */
object ClusterDisplayResolver {

    /**
     * Lệnh dò CỤM — mở rộng từ đường proven trong `ClusterCast.cast()`/`stop()`
     * (`dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'`) thêm `virtual:` để output mang cả dòng
     * `uniqueId "virtual:<pkg>,<uid>,<name>,<n>"` (kèm `displayId N`) của MỌI VirtualDisplay ⇒ guard owner (R2)
     * chạy trên cùng một output, không cần lệnh thứ hai. Dùng `grep -iE` (extended) vì toybox trên xe không nhận
     * `\|` cơ bản (session-findings 2026-09-14). Dòng thêm vào không chứa fission/xdja nên
     * [DisplayParse.clusterDisplayId] không đổi kết quả (test fixture nguyên văn 2026-09-15).
     */
    const val DETECT_CMD: String = "dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja|virtual:'"

    /** Số lần dò sau khi mở projection (đường cũ 16×500 ms; giữ tổng < castTimeout 15 s − 5 s mở = 6 s). */
    const val AWAIT_ATTEMPTS: Int = 12
    /** Nghỉ giữa hai lần dò. */
    const val AWAIT_SLEEP_MS: Long = 500L

    /**
     * @param detectGrepOut output của [DETECT_CMD].
     * @param selfPackage gói của CHÍNH launcher (BuildConfig.APPLICATION_ID) — VD do gói này sở hữu bị loại.
     * @return id display cụm ≥ 1, hoặc `-1` khi không dò thấy fission/xdja HOẶC id dò ra là VD của launcher.
     *   KHÔNG BAO GIỜ trả 0 (màn giữa) và KHÔNG có fallback (R1).
     */
    fun resolve(detectGrepOut: String, selfPackage: String): Int {
        val detected = DisplayParse.clusterDisplayId(detectGrepOut)
        if (detected < 1) return -1
        if (DisplayParse.isOwnedVirtualDisplay(detectGrepOut, detected, selfPackage)) return -1
        return detected
    }

    /**
     * Chạy [DETECT_CMD] một lần qua [shell], [resolve], persist id qua [persist] khi dò được, trả id hoặc `-1`.
     * Best-effort: shell ném/thất bại ⇒ `-1` (không bao giờ ném). KHÔNG có fallback về giá trị cũ — caller phải
     * tự quyết "không đặt" khi nhận `-1` (R1).
     */
    fun detectAndPersist(shell: SimpleCastShell, selfPackage: String, persist: (Int) -> Unit): Int {
        val out = runCatching { shell.execute(DETECT_CMD) }.getOrNull() ?: return -1
        if (!out.success) return -1
        val resolved = resolve(out.stdout, selfPackage)
        if (resolved >= 1) runCatching { persist(resolved) }
        return resolved
    }

    /**
     * Dò LẶP sau khi mở projection — VD cụm do AutoContainer tạo bất đồng bộ sau profile 35, nên lần dò đầu có
     * thể hụt (đường cũ `ClusterCast.cast()` cũng lặp 16×500 ms rồi mới đặt app). Trả id ≥ 1 ngay khi dò được,
     * `-1` sau [attempts] lần hụt. [sleepMs] tách ra để test không ngủ thật.
     */
    fun awaitAndPersist(
        shell: SimpleCastShell,
        selfPackage: String,
        attempts: Int = AWAIT_ATTEMPTS,
        sleepMs: (Long) -> Unit = { Thread.sleep(it) },
        persist: (Int) -> Unit,
    ): Int {
        repeat(attempts.coerceAtLeast(1)) { i ->
            val id = detectAndPersist(shell, selfPackage, persist)
            if (id >= 1) return id
            if (i < attempts - 1) sleepMs(AWAIT_SLEEP_MS)
        }
        return -1
    }
}
