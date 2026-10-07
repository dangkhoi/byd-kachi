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

    /** Số lần dò sau khi mở projection (đường cũ 16×500 ms; tổng ngủ 6 s — hạn lượt mở: `BoundedCastExecutor.OPEN_TIMEOUT_MS`). */
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
        val detected = newestSameName(detectGrepOut, DisplayParse.clusterDisplayId(detectGrepOut))
        if (detected < 1) return -1
        if (DisplayParse.isOwnedVirtualDisplay(detectGrepOut, detected, selfPackage)) return -1
        return detected
    }

    private val RE_INFO_NAME = Regex("""DisplayInfo\{"([^"]+), displayId (\d+)"""")

    /**
     * 2.90 · R5 — màn ảo cụm được DỰNG LẠI (theme đổi ⇒ id mới, [ĐO xe 06/10] 4 → 9) mà bản đọc còn thấy cả màn cũ CÙNG TÊN
     * ⇒ lấy id LỚN nhất trong nhóm cùng tên với [first]: id logical display không bao giờ tái dùng, cấp tăng dần
     * ([ĐO nguồn A10 r47] `DisplayManagerService.java:1033-1034` `assignDisplayIdLocked` — `mNextNonDefaultDisplayId++`) ⇒ id lớn hơn là màn mới hơn.
     * Tên khác nhau (vd DL5 `fission_bg_XDJAScreenProjection_0/_1`) ⇒ giữ đúng [first] — thứ tự cũ (CLAUDE.md §6).
     * Tên lấy từ dòng `DisplayInfo{"<tên>, displayId N"` của chính [DETECT_CMD]; không đọc được tên ⇒ [first].
     */
    internal fun newestSameName(detectGrepOut: String, first: Int): Int {
        if (first < 1) return first
        val names = RE_INFO_NAME.findAll(detectGrepOut).mapNotNull { m ->
            m.groupValues[2].toIntOrNull()?.let { it to m.groupValues[1] }
        }.toList()
        val name = names.firstOrNull { it.first == first }?.second ?: return first
        return names.filter { it.second == name }.maxOf { it.first }
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
     * `-1` sau [attempts] lần hụt. [sleepMs] tách ra để test không ngủ thật. 2.90 · R5: [exclude] ≥ 1 = id màn ảo TRƯỚC một lượt
     * đổi theme ([ĐO xe 06/10] theme đổi ⇒ màn ảo dựng lại với id mới 4 → 9) — bỏ qua id đó tới khi thấy id khác.
     * 2.96 · R6: chỉ thấy id cũ ở [excludeGrace] lượt dò ⇒ nhận nó NGAY (không chờ hết [attempts]) — xem [EXCLUDE_GRACE_POLLS].
     * Lượt dò hụt (`-1`, màn cũ đã gỡ mà màn mới chưa có) KHÔNG tính vào khoảng ân hạn.
     */
    fun awaitAndPersist(
        shell: SimpleCastShell,
        selfPackage: String,
        attempts: Int = AWAIT_ATTEMPTS,
        sleepMs: (Long) -> Unit = { Thread.sleep(it) },
        exclude: Int = -1,
        excludeGrace: Int = EXCLUDE_GRACE_POLLS,
        log: (String) -> Unit = {},
        persist: (Int) -> Unit,
    ): Int {
        var seenExcluded = 0
        val grace = excludeGrace.coerceAtLeast(1)
        repeat(attempts.coerceAtLeast(1)) { i ->
            // 2.90 · R5: không persist id bị loại (màn ảo TRƯỚC lượt đổi theme) — chỉ id mới được ghi.
            val id = detectAndPersist(shell, selfPackage) { if (it != exclude) persist(it) }
            if (id >= 1 && id != exclude) return id
            if (id >= 1 && ++seenExcluded >= grace) {
                log("dò sau theme: chỉ thấy id cũ $exclude ở $seenExcluded lượt ⇒ nhận (màn ảo không dựng lại — R6)")
                runCatching { persist(exclude) }
                return exclude
            }
            if (i < attempts - 1) sleepMs(AWAIT_SLEEP_MS)
        }
        // Hết lượt mà chỉ thấy id cũ (ít hơn [grace] lượt, phần còn lại hụt) ⇒ màn ảo KHÔNG dựng lại ⇒ id cũ vẫn là cụm.
        if (seenExcluded > 0) { runCatching { persist(exclude) }; return exclude }
        return -1
    }

    /**
     * 2.96 · R6 — số lượt dò SAU khi mở mà chỉ thấy id màn ảo CŨ thì nhận nó.
     *
     * Bằng chứng: [ĐO log xe 07/10 (fw 2606), 4/4 lượt mở có gửi theme 31 — 20:48:36, 21:03:24, 21:03:55, 21:04:53] màn ảo GIỮ
     * id 4; lượt chờ id mới đốt cả 12 lượt dò (~7,5 s ở 21:04:59.7 → 21:05:07.2) và ở 20:48 làm lượt mở chạm hạn 25 s TRƯỚC khi
     * kịp đặt ClusterBlack. [ĐO xe 06/10, fw 2602] màn ảo dựng lại với id mới (4 → 9) — chưa đo khi nào id mới xuất hiện.
     * [SUY] lượt dò đầu chạy ≥ 5 s sau opcode theme (31 → ngủ 2 s → 16 → 2 s → 35 → ngủ → điều kiện nền: 20:48:36.1 → ≥ :41.8),
     * cộng 3 lượt (2 × 0,5 s ngủ + 3 lệnh ≈ 0,3–0,6 s) ⇒ màn ảo có ≥ ~7 s để dựng lại trước khi ta nhận id cũ. Nhận nhầm (2602 dựng
     * lại chậm hơn thế) vẫn có lưới: mỗi lượt ĐẶT dò tươi trước khi đặt (R1, `newestSameName` lấy id mới), và lượt chiếu đầu từ
     * Idle đặt bù ClusterBlack trên id dò tươi (R7 · `ensureClusterPlaceholder`).
     */
    const val EXCLUDE_GRACE_POLLS: Int = 3
}
