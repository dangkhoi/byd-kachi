package com.byd.clusternav.launcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Nguồn ĐỌC 2 nhịp cho [CarStatusRepository]. [CarDataAdapter] hiện thực; test dùng reader giả.
 * MỖI nhịp NHẬN [CarStatus] hiện tại và TRẢ bản mới (copy-based) — field không đọc được để `null`.
 */
interface CarStatusReader {
    /** Nhịp NHANH (~1s): tốc độ / động lực / công suất / cảnh báo ADAS. */
    fun readFast(prev: CarStatus): CarStatus

    /** Nhịp CHẬM (~10s): pin/tầm/sạc · khí hậu · lốp · thân xe · đèn · an toàn(bền) · danh tính. */
    fun readSlow(prev: CarStatus): CarStatus

    /**
     * H1 (PERF 2026-09-16) — nhịp NHANH có đáng chạy ở lượt này không.
     *
     * [ĐO máy ảo 2026-09-16]: nhịp nhanh mang **18 datum × 1 Hz = 1 080 lượt đọc/phút**, tức **77 %** toàn bộ tải
     * HAL của app — trong khi màn mặc định (pin · bụi mịn · nhiệt độ ngoài) KHÔNG bày một datum nhanh nào. Mặc
     * định `true` ⇒ mọi reader cũ (và mọi test dùng reader giả) giữ nguyên hành vi; chỉ [CarDataAdapter] biết
     * cách trả lời câu này bằng nhu cầu THẬT của màn hình.
     */
    fun fastNeeded(): Boolean = true
}

/**
 * REPOSITORY trạng thái xe LIVE (W1b, R8) — poll 2 nhịp qua [reader] → phát 1 `StateFlow<CarStatus>` (nguồn cho
 * UDF của HOME, Stage 3 collect qua `repeatOnLifecycle`). Nhịp NHANH [fastMs] (mặc định 1s) + nhịp CHẬM [slowMs]
 * (mặc định 10s), CHẠY TRÊN [scope] (Stage 3 tiêm scope gắn vòng đời).
 *
 * ── Copy-based + đồng thời an toàn ─────────────────────────────────────────────────────────────────
 * Mỗi nhịp `_status.update { reader.readX(it) }` — [MutableStateFlow.update] cập nhật NGUYÊN TỬ (Context7:
 * "safe for concurrent use"), 2 vòng nhanh/chậm ghi các cụm field khác nhau nên gộp không mất dữ liệu.
 *
 * ── Degrade-safe (R9) ──────────────────────────────────────────────────────────────────────────────
 * Đọc lỗi/ném (không nên, adapter đã null-safe) → GIỮ [CarStatus] cũ (`getOrDefault(prev)`), KHÔNG crash vòng.
 * Off-car → adapter trả toàn null ⇒ mọi field null ⇒ UI "—". [stop] huỷ sạch 2 job (idempotent).
 */
class CarStatusRepository(
    private val reader: CarStatusReader,
    private val scope: CoroutineScope,
    private val fastMs: Long = 1_000L,
    private val slowMs: Long = 10_000L,
) {
    private val _status = MutableStateFlow(CarStatus())

    /** Trạng thái xe hiện tại (bất biến, mọi field nullable). null-field ⇒ "—". */
    val status: StateFlow<CarStatus> = _status.asStateFlow()

    private var fastJob: Job? = null
    private var slowJob: Job? = null

    /** Khởi 2 vòng poll (đọc NGAY 1 lần rồi mới delay → HOME có dữ liệu tức thì). Gọi lại = restart sạch. */
    fun start() {
        stop()
        fastJob = scope.launch {
            while (isActive) {
                // H1: màn không bày datum nhanh nào ⇒ KHÔNG đọc, và lùi về nhịp chậm thay vì thức dậy mỗi giây
                // để không làm gì. Vẫn có một lượt hỏi lại mỗi [slowMs] nên khi người dùng kéo ô Tốc độ lên màn,
                // nhịp nhanh sống lại trong vòng một nhịp chậm — không cần ai đánh thức nó.
                if (!reader.fastNeeded()) { delay(slowMs); continue }
                _status.update { runCatching { reader.readFast(it) }.getOrDefault(it) }
                delay(fastMs)
            }
        }
        slowJob = scope.launch {
            while (isActive) {
                _status.update { runCatching { reader.readSlow(it) }.getOrDefault(it) }
                delay(slowMs)
            }
        }
    }

    /**
     * [SOÁT P1-1 · 2026-09-16] Đọc NGAY một lượt (chậm + nhanh) trên luồng của chỗ gọi, trả ảnh chụp vừa đọc.
     *
     * Sinh ra cho đường **câu hỏi bằng giọng**: từ 1.67, datum không nằm trên màn thì vòng poll GIỮ giá trị cũ
     * (xem [CarDataDemand]), nên trả lời bằng ảnh chụp là trả lời bằng một con số có thể đã cũ hàng giờ. Chỗ gọi
     * ghim datum cần hỏi vào nhu cầu ([CarDataDemand.Holder.withExtra]) rồi gọi hàm này.
     *
     * ⚠ **ĐỒNG BỘ, KHÔNG phải một nhịp poll thứ ba**: người vừa hỏi đang đợi câu trả lời, mà nhịp chậm còn tới
     * 10 s nữa. Chi phí có trần rõ ràng — đúng tập nhu cầu đang ghim (vài datum), không phải cả 123 — vì chính
     * cổng H1 lọc bên trong [reader]. Chỗ gọi phải tự bảo đảm không gọi khi nhu cầu là `null` (= đọc hết).
     *
     * Dùng `update`+`value` chứ không `updateAndGet`: [MutableStateFlow.update] đã nguyên tử, và `value` đọc
     * ngay sau đó cho đúng ảnh mới nhất (một nhịp poll chen vào giữa chỉ làm nó **mới hơn**, không cũ đi).
     */
    fun refreshNow(): CarStatus {
        _status.update { runCatching { reader.readSlow(reader.readFast(it)) }.getOrDefault(it) }
        return _status.value
    }

    /** Dừng poll (huỷ 2 job). Idempotent — gọi nhiều lần an toàn. */
    fun stop() {
        fastJob?.cancel(); fastJob = null
        slowJob?.cancel(); slowJob = null
    }
}
