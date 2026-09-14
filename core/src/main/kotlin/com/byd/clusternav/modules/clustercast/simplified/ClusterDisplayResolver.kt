package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.DisplayParse

/**
 * Chọn id logical-display của CỤM một cách GENERIC — dò `fission`/`xdja` từ `dumpsys display`, KHÔNG hardcode
 * `1` (regression X2: cụm là display **2** trên DiLink3.0 2026-09-14, `fission_bg_xdjaVirtualSurface`).
 *
 * Vì sao tồn tại (CLAUDE.md §5 "kiểm bằng sự thật, không cờ RAM" · §7 generic không hardcode): đường chiếu cũ
 * đã-chứng-minh-trên-xe ([com.byd.clusternav.modules.clustercast.ClusterCast]) LUÔN dò VD động bằng
 * [DisplayParse.clusterDisplayId] và không bao giờ giả định id. Bản simplified (SimpleCast) lại hardcode
 * `savedDisplayId ?: 1` ⇒ mọi lệnh `wm size/overscan/density -d 1` + `am start --display 1` nhắm sai màn.
 * Đây là parser THUẦN (không đụng Android) → unit-test off-device (CLAUDE.md §10).
 *
 * Thứ tự (ưu tiên sự thật LIVE, tự sửa khi VD id đổi theo profile 30/31 — xem ClusterCast KDoc mục 1):
 *   1. detection live ([DisplayParse.clusterDisplayId] trên output grep) nếu ≥ 1;
 *   2. else [savedId] nếu hợp lệ (≥ 1);
 *   3. else [fallback] (mặc định 1 — chỉ là phao cuối, KHÔNG phải giá trị tin cậy).
 */
object ClusterDisplayResolver {

    /**
     * Lệnh dò CỤM — GIỐNG HỆT đường proven trong `ClusterCast.cast()`/`stop()`
     * (`dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'`). Dùng `grep -iE` (extended) vì toybox
     * trên xe không nhận `\|` cơ bản (session-findings 2026-09-14).
     */
    const val DETECT_CMD: String = "dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'"

    /**
     * @param detectGrepOut output của [DETECT_CMD] (đã lọc dòng fission/xdja).
     * @param savedId id đã lưu (prefs.lastDisplayId), null nếu chưa có.
     * @param fallback phao cuối khi không dò được và chưa lưu (mặc định 1).
     * @return id display cụm — LUÔN ≥ 1 (display 0 là màn giữa, không bao giờ là cụm).
     */
    fun resolve(detectGrepOut: String, savedId: Int?, fallback: Int = 1): Int {
        val detected = DisplayParse.clusterDisplayId(detectGrepOut)
        if (detected >= 1) return detected
        if (savedId != null && savedId >= 1) return savedId
        return if (fallback >= 1) fallback else 1
    }

    /**
     * Chạy [DETECT_CMD] qua [shell], [resolve] với [currentId] làm cả saved lẫn fallback, persist id đã chọn
     * qua [persist], và trả id để caller cập nhật state SỐNG. Dò/parse HỤT → trả nguyên [currentId] (best-effort,
     * không bao giờ ném). Tách khỏi coordinator để giữ nó gọn (§4.1) và để test được với fake shell.
     */
    fun detectAndPersist(shell: SimpleCastShell, currentId: Int, persist: (Int) -> Unit): Int {
        val out = runCatching { shell.execute(DETECT_CMD) }.getOrNull() ?: return currentId
        if (!out.success) return currentId
        val resolved = resolve(out.stdout, savedId = currentId, fallback = currentId)
        if (resolved >= 1) runCatching { persist(resolved) }
        return if (resolved >= 1) resolved else currentId
    }
}
