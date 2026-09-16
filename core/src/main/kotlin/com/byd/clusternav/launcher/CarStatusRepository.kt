package com.byd.clusternav.launcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Nguồn ĐỌC 2 nhịp cho [CarStatusRepository]. [CarDataAdapter] hiện thực; test dùng reader giả.
 * MỖI nhịp NHẬN [CarStatus] hiện tại và TRẢ bản mới (copy-based) — field không đọc được để `null`.
 */
interface CarStatusReader {
    /** Nhịp NHANH (~1s): tốc độ / động lực / công suất. */
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
 * Mỗi nhịp đi qua [publish]: đọc MỘT lần dưới một khoá rồi phát. 2 vòng nhanh/chậm ghi các cụm field khác
 * nhau nên gộp không mất dữ liệu. (KHÔNG dùng `MutableStateFlow.update` — nó gọi lại lambda khi có tranh chấp,
 * mà lambda ở đây bắn cả một lượt đọc HAL; xem KDoc [publish].)
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

    /**
     * Khoá cho MỌI lượt đọc-rồi-phát. Xem [publish].
     */
    private val readLock = Any()

    /**
     * Đọc MỘT lần rồi phát — thay cho `_status.update { … }`.
     *
     * ## ⚠ Vì sao KHÔNG dùng [MutableStateFlow.update] ở đây
     * `update` là một vòng **compare-and-set**: nó gọi LẠI lambda mỗi khi có người ghi chen vào giữa lúc nó
     * đang tính giá trị mới. Lambda ở đây **không thuần** — nó bắn cả một lượt đọc HAL (18 datum ở nhịp nhanh,
     * mỗi datum một binder IPC). Từ bản vá P1-1 thì đường **câu hỏi bằng giọng** gọi [refreshNow] trên luồng
     * của nó, nên ba chỗ ghi cùng lúc là có thật; và vì một lượt đọc kéo dài gần bằng chính nhịp poll, cửa sổ
     * chen nhau rộng đúng bằng thứ ta muốn tránh. Mỗi lần chen = **một lượt quét HAL thừa**, tức đúng cái tải
     * mà H1 vừa cắt.
     *
     * Ở đây: một khoá duy nhất ⇒ [read] chạy đúng MỘT lần cho mỗi nhịp. Phép gộp hai vòng nhanh/chậm không đổi
     * (vòng sau vẫn đọc `value` mới nhất của vòng trước), và `getOrDefault(prev)` vẫn giữ ảnh cũ khi đọc ném.
     */
    private fun publish(read: (CarStatus) -> CarStatus): CarStatus = synchronized(readLock) {
        val prev = _status.value
        val next = runCatching { read(prev) }.getOrDefault(prev)
        _status.value = next
        next
    }

    /** Khởi 2 vòng poll (đọc NGAY 1 lần rồi mới delay → HOME có dữ liệu tức thì). Gọi lại = restart sạch. */
    fun start() {
        stop()
        fastJob = scope.launch {
            while (isActive) {
                // H1: màn không bày datum nhanh nào ⇒ KHÔNG đọc, và lùi về nhịp chậm thay vì thức dậy mỗi giây
                // để không làm gì. Vẫn có một lượt hỏi lại mỗi [slowMs] nên khi người dùng kéo ô Tốc độ lên màn,
                // nhịp nhanh sống lại trong vòng một nhịp chậm — không cần ai đánh thức nó.
                if (!reader.fastNeeded()) { delay(slowMs); continue }
                publish { reader.readFast(it) }
                delay(fastMs)
            }
        }
        slowJob = scope.launch {
            while (isActive) {
                publish { reader.readSlow(it) }
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
     * Đi qua [publish] như hai vòng poll: lượt đọc chạy đúng MỘT lần và không bị một nhịp poll chen vào giữa
     * bắt tính lại (xem KDoc [publish]).
     */
    fun refreshNow(): CarStatus = publish { reader.readSlow(reader.readFast(it)) }

    /** Dừng poll (huỷ 2 job). Idempotent — gọi nhiều lần an toàn. */
    fun stop() {
        fastJob?.cancel(); fastJob = null
        slowJob?.cancel(); slowJob = null
    }
}
