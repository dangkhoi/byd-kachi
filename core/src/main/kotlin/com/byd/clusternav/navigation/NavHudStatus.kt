package com.byd.clusternav.navigation

/**
 * ═══ FIX286 R-HUD (S4) — trạng thái nguồn HUD cho dòng *Cài đặt › Dẫn đường* (thuần, `:core`) ══════════════════════
 *
 * Câu trả lời anh em cần chụp màn TRƯỚC khi gạt công tắc (§11): nguồn thông báo — nguồn DUY NHẤT của HUD, chỉ Google
 * Maps ([NavApps.NOTIFICATION]) — đang gắn hay không. Ưu tiên SỰ THẬT của NMS (lượt đọc dump gần nhất, nếu nó MỚI hơn
 * callback `onListenerConnected` gần nhất); không có thì mới nói theo callback trong tiến trình.
 */
object NavHudStatus {

    /**
     * [UNREADABLE] (senior review FIX286 Pass 4 · P3): lượt đọc dump CÓ chạy (kênh đã lên) mà không thấy khối Live — ca
     * OC-HUD1 cần thấy trên ROM BYD (khuôn dump lạ ⇒ Kachi không tự chữa). Trước đó ca này rơi vào [UNKNOWN] và dòng
     * tình trạng nói *"kênh lệnh của xe chưa sẵn"* — sai nguyên nhân đúng lúc anh em chụp màn để chẩn đoán (§11).
     */
    enum class Source { OFF, NO_ACCESS, LIVE, NOT_LIVE, BOUND_IN_PROCESS, UNREADABLE, UNKNOWN }

    /**
     * @param connectedAtWallMs giờ tường của `onListenerConnected` gần nhất; `0` = chưa lần nào trong tiến trình.
     * @param truth / [truthAtWallMs] kết luận + giờ của lượt đọc dump gần nhất (`null` = chưa đọc).
     */
    fun source(
        navEnabled: Boolean,
        granted: Boolean?,
        boundInProcess: Boolean,
        connectedAtWallMs: Long,
        truth: NlsLiveDump.Verdict?,
        truthAtWallMs: Long,
    ): Source = when {
        !navEnabled -> Source.OFF
        granted != true -> Source.NO_ACCESS
        truth == NlsLiveDump.Verdict.LIVE && truthAtWallMs >= connectedAtWallMs -> Source.LIVE
        truth == NlsLiveDump.Verdict.NOT_LIVE && truthAtWallMs >= connectedAtWallMs -> Source.NOT_LIVE
        boundInProcess -> Source.BOUND_IN_PROCESS
        // Không có callback để nói thay ⇒ nói đúng điều đã biết: kênh lên, đọc được dump, nhưng KHÔNG đọc ra khối Live.
        truth == NlsLiveDump.Verdict.UNREADABLE -> Source.UNREADABLE
        else -> Source.UNKNOWN
    }
}
