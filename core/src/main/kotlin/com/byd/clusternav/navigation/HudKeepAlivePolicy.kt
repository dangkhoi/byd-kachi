package com.byd.clusternav.navigation

/**
 * Chính sách KEEP-ALIVE THUẦN (không Android) cho đường ghi HAL cụm/HUD
 * (`INSTRUMENT_GUIDE_INFO_SIMPLE_SET` qua [com.byd.clusternav.NavigationHudOwner]).
 *
 * VẤN ĐỀ (J1, đo trên xe 1.14): HUD kính lái + nav "Giữa+ETA" của OEM TỰ TẮT nếu quá lâu không nhận frame
 * mới — trên đoạn dài không rẽ, GMaps giãn notification → `NavRepository.ingest` → `push()` không được gọi,
 * và frame trùng còn bị dedup nuốt → OEM chớp/mất ~1s rồi hiện lại khi có noti kế. Làn cụm KHÔNG bị vì có
 * nhịp tim 400ms riêng (AmapEmissionArbiter); đường HAL này thì CHƯA có nhịp tim nào.
 *
 * Policy này chỉ QUYẾT ĐỊNH "khi nào re-assert / khi nào clear" — nó không tự đọc giờ và không tự ghi HAL
 * (owner làm). Thuần → JVM-testable. Clock (monotonic, vd `SystemClock.elapsedRealtime`) do caller truyền vào.
 *
 * HAI ĐỒNG HỒ, HAI NGỮ NGHĨA (tách bởi handoff 2026-08-15 §1.3):
 *  - [lastWriteAtMs]  — cập nhật MỌI lần ghi HAL thành công (real push HOẶC keep-alive re-assert). Dùng để
 *    NHỊP re-assert: chỉ re-assert khi đã ≥ [intervalMs] kể từ lần ghi bất kỳ (đừng dồn HAL mỗi tick).
 *  - [lastRealPushAtMs] — CHỈ cập nhật khi NGUỒN đẩy frame MỚI (real push). Dùng làm TRẦN TUỔI: nguồn im
 *    quá lâu thì phải nhả frame cũ. Re-assert KHÔNG được chạm đồng hồ này — nếu không, nhịp tim keep-alive
 *    (250ms) sẽ TỰ LÀM TƯƠI trần tuổi của chính nó và frame chết bị ghim vô hạn (đây chính là Lỗ 1 của handoff).
 *
 * TRẦN TUỔI = [DEFAULT_MAX_AGE_MS] = **180_000 ms (180 s)**, CỐ Ý khớp `ClusterBroadcaster.STALE_MS` (một hằng
 * số, một ngữ nghĩa "nguồn coi như chết"). **ĐỪNG SIẾT XUỐNG 15–20 s.** Đo log lái thật 62 phút
 * (`docs/diagnostics/nav-logs/commute-2026-08-14-pm.csv`, 390 lần GMaps đổi cự ly): ~30 lần khoảng cách giữa
 * 2 noti **> 15 s**, DỒN đúng lúc bò trong hầm / kẹt xe (dài nhất **108 s** ở 'Hầm Nguyễn Hữu Cảnh', speed
 * 2,8 m/s — GMaps chỉ đẩy noti mỗi bậc 100 m, bò 2,8 m/s thì 100 m mất ~36 s). Trần 15–20 s ⇒ cụm TRẮNG
 * ~30 lần/chuyến ĐÚNG lúc người lái cần nhất. Trần 180 s: cả 62 phút chỉ 1 khoảng vượt, mà đó là lúc ĐÃ ĐỖ
 * (kết thúc phiên, không phải nav thật). Tín hiệu CHÍNH để tắt cụm là tín hiệu DƯƠNG (disconnect / gỡ noti /
 * arrival) ở `NavNotificationListener`; trần tuổi chỉ là LƯỚI ĐỠ CUỐI (backstop), đặt cao hơn mọi khoảng thật
 * đã quan sát. Nguồn số: `docs/diagnostics/handoff-b1-b4-hud-stale-ingest-gate-2026-08-15.md` §1.2.
 *
 * Ngữ nghĩa gọi: sau mỗi lần GHI frame thành công gọi [onFrameWritten] (real push để mặc định `realPush=true`;
 * keep-alive re-assert truyền `realPush=false`); khi CLEAR (hết dẫn / mode OFF / stop / quá trần tuổi) gọi
 * [onCleared]. [shouldReassert] = ĐANG hiện 1 frame VÀ đã ≥ [intervalMs] kể từ lần ghi cuối VÀ nguồn còn
 * trong trần tuổi → re-assert (bypass dedup). [shouldClear] = ĐANG hiện 1 frame VÀ nguồn đã im quá trần tuổi
 * → owner nhả frame cũ.
 */
class HudKeepAlivePolicy(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
) {
    init {
        require(intervalMs > 0L) { "intervalMs must be > 0 (was $intervalMs)" }
        require(maxAgeMs > intervalMs) { "maxAgeMs must be > intervalMs (was $maxAgeMs vs $intervalMs)" }
    }

    private val lock = Any()
    private var lastWriteAtMs = 0L
    private var lastRealPushAtMs = 0L
    private var hasFrame = false

    /**
     * Gọi SAU mỗi lần ghi thành công 1 frame nav lên HAL.
     * @param realPush true = NGUỒN đẩy frame mới → làm tươi cả nhịp lẫn TRẦN TUỔI. false = keep-alive
     *   re-assert → CHỈ nhịp lại [lastWriteAtMs], KHÔNG chạm trần tuổi (nếu không sẽ tự ghim frame chết vô hạn).
     */
    fun onFrameWritten(nowMs: Long, realPush: Boolean = true) = synchronized(lock) {
        lastWriteAtMs = nowMs
        if (realPush) lastRealPushAtMs = nowMs
        hasFrame = true
    }

    /** Gọi khi frame bị CLEAR — không còn gì để giữ sống. */
    fun onCleared() = synchronized(lock) {
        hasFrame = false
        lastWriteAtMs = 0L
        lastRealPushAtMs = 0L
    }

    /**
     * true khi đang hiện 1 frame, đã ≥ [intervalMs] kể từ lần ghi cuối, VÀ nguồn còn trong trần tuổi
     * ([maxAgeMs]) → owner nên re-assert (bypass dedup) để OEM không blank. Nguồn im quá trần tuổi thì
     * KHÔNG re-assert nữa (xem [shouldClear]).
     */
    fun shouldReassert(nowMs: Long): Boolean = synchronized(lock) {
        hasFrame &&
            nowMs - lastWriteAtMs >= intervalMs &&
            nowMs - lastRealPushAtMs <= maxAgeMs
    }

    /** true khi đang hiện 1 frame nhưng nguồn đã im > trần tuổi ([maxAgeMs]) → owner phải NHẢ (clear) frame cũ. */
    fun shouldClear(nowMs: Long): Boolean = synchronized(lock) {
        hasFrame && nowMs - lastRealPushAtMs > maxAgeMs
    }

    /** Chu kỳ tick khuyến nghị cho scheduler của owner (ms). */
    fun intervalMs(): Long = intervalMs

    /** Trần tuổi (ms) — nguồn im quá ngưỡng này thì nhả frame. Xem KDoc class về lý do 180 s. */
    fun maxAgeMs(): Long = maxAgeMs

    companion object {
        /**
         * 250ms (giảm từ 400ms — TASK 2 closeout 1.28) = re-assert NHANH HƠN để THU HẸP cửa sổ blank của OEM
         * trên đoạn dài không rẽ (owner báo HUD/centre "Giữa+ETA" vẫn chớp vài đoạn DÙ 1.15 đã thêm heartbeat
         * 400ms). Vẫn thoải mái dưới ngưỡng blank ~1s quan sát được và cùng bậc nhịp tim làn-cụm đã proven trên
         * xe. Nếu SAU khi hạ vẫn nháy → nguyên nhân là OEM render-layer (ngoài tầm app), đóng như giới hạn. Giữ
         * [DEFAULT_MAX_AGE_MS] = 180s KHÔNG đổi (trần tuổi có số liệu thực nghiệm — xem KDoc dưới).
         */
        const val DEFAULT_INTERVAL_MS = 250L

        /**
         * 180_000ms (180s) = TRẦN TUỔI backstop, CỐ Ý khớp `ClusterBroadcaster.STALE_MS`. Đo log lái thật:
         * gap noti > 15s xảy ra ~30 lần/chuyến (kẹt xe/hầm, dài nhất 108s) → 15–20s sẽ trắng cụm SAI. 180s:
         * chỉ 1 gap/62phút vượt, và đó là lúc đã đỗ. Đọc KDoc class + handoff §1.2 TRƯỚC KHI đổi số này.
         */
        const val DEFAULT_MAX_AGE_MS = 180_000L
    }
}
