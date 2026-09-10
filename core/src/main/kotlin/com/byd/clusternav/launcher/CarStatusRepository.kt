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

    /** Dừng poll (huỷ 2 job). Idempotent — gọi nhiều lần an toàn. */
    fun stop() {
        fastJob?.cancel(); fastJob = null
        slowJob?.cancel(); slowJob = null
    }
}
