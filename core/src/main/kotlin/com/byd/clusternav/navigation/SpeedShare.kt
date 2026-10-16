package com.byd.clusternav.navigation

/**
 * ═══ 3.1.9 · HAL-POLL-COVERED — MỘT lượt đọc tốc độ cho hai người đọc KHÔNG-an-toàn (thuần, `:core`) ═══════════════════════════
 *
 * [ĐO xe 15/10 18:02–18:05, log SDK] `BYDAutoSpeedDevice.getCurrentSpeed` ≈ 180 lượt/phút, ≈ 170 trên luồng chính: nhịp tim dẫn đường
 * 400 ms (`ClusterBroadcaster` → `SpeedProvider.mps()`) + nhịp nhanh 1 s của `CarDataAdapter` khi màn bày tốc độ — hai binder độc lập
 * cho cùng một con số. Nay: lượt đọc THẬT nào (của ai) cũng [offer] vào đây; nhịp tim dẫn đường hỏi [recent] trước, chỉ gọi binder khi
 * mẫu đã già hơn [ttlMs].
 *
 * ## Vì sao [ttlMs] = 1 s (không phải 300 ms)
 * Người tiêu thụ duy nhất là nội suy cự ly tới ngã rẽ (`TurnDistanceInterpolator`): nó ƯU TIÊN tốc độ khép tự suy từ hai thông báo,
 * tốc độ xe chỉ để kẹp ([0, 1,2·v + 3] m/s) và làm dự phòng; cự ly hiện ra được lượng tử hoá (`NavParse.quantizeDisplay`). Tốc độ già
 * ≤ 1 s lệch ≤ ~3 m/s ở gia tốc mạnh — nằm trong khoảng dư +3 m/s của phép kẹp. 300 ms ngắn hơn nhịp tim 400 ms ⇒ nhịp tim không bao
 * giờ dùng lại mẫu của chính nó (gần như không cắt được gì). 1 s ⇒ nhịp tim ≤ 60 lượt/phút, và khi màn bày tốc độ thì hai bên chung
 * nhau ≈ 60 lượt/phút thay vì ≈ 150 + 60.
 *
 * ## ⚠ Cổng an toàn KHÔNG đọc ở đây
 * `CtlSafetyPolicy.readMotion` (cốp) đọc qua `AppContainer.readFresh` → `CarDataAdapter.readFast` → binder — đường ấy chỉ [offer], không
 * bao giờ [recent] (khoá bằng `HalPollCoveredTest`). Một mẫu cũ 1 s là đủ cho nội suy, KHÔNG đủ để quyết *"xe đang đứng"*.
 */
class SpeedShare(
    private val ttlMs: Long = NAV_TTL_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    @Volatile private var sample: Pair<Long, Double>? = null

    /** Một lượt đọc THẬT vừa xong ([kmh] đã qua phép chặn sentinel của người đọc); `null` (đọc hỏng) không ghi đè mẫu tốt. */
    fun offer(kmh: Double?) {
        if (kmh == null || kmh.isNaN() || kmh < 0) return
        sample = clock() to kmh
    }

    /** km/h của lượt đọc thật gần nhất nếu nó chưa già quá [ttlMs]; `null` ⇒ người hỏi tự đọc binder (rồi [offer]). */
    fun recent(): Double? {
        val (at, v) = sample ?: return null
        return v.takeIf { clock() - at in 0 until ttlMs }
    }

    companion object {
        const val NAV_TTL_MS = 1_000L

        /** Một mẫu cho cả tiến trình (nhịp tim dẫn đường và vòng poll cùng ở tiến trình chính). */
        val PROCESS: SpeedShare = SpeedShare()
    }
}
