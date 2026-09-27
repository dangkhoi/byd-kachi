package com.byd.clusternav.launcher.voice

/**
 * ═══ VOICE-WRITE-LANE · MỘT LÀN GHI TUẦN TỰ CHO MỌI LỆNH GHI TỪ GIỌNG NÓI ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-276-closing.html` R5; backlog `VOICE-WRITE-LANE` (review Pass 1 của 2.74, [P2]).
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car, không luồng, không đồng hồ.
 *
 * ## Bệnh nó chữa — câu ghép + một vế BẤT ĐỒNG BỘ
 * `VoiceDispatcher.runFrom` (tới 2.75) chạy các vế của một câu ghép **tuần tự trên luồng gọi**: vế N xong là vế
 * N+1 chạy ngay. Nhưng từ UX4 (2.74) vế *rời AUTO* của `VoiceClimateStep` là **hai** lệnh HAL cách nhau một nhịp
 * 400 ms (`ActionMacros.DEFAULT_GAP_MS`) nằm trên luồng NỀN — hàm trả về **trước khi** lệnh thứ hai được ghi. Với
 * *"tăng gió rồi tắt điều hoà"*: vế 1 bắn `ac_auto=OFF` rồi ngủ; vế 2 bắn ngay `ac_auto=OFF` lần nữa — **rơi đúng
 * vào nhịp chờ**; rồi vế 1 tỉnh và bắn `fan=+1`. Theo đúng tiền đề [ĐO] mà `DEFAULT_GAP_MS` dựa vào (*"bắn liên
 * tiếp thì lệnh sau rơi"*) thì lệnh mức gió có thể bị xe BỎ, mà Kachi vẫn đọc ✓ (`finish(ok = true)`).
 *
 * ## Cơ chế — một việc *"xong"* khi nó **gọi lại**, không phải khi hàm trả về
 * [submit] nhận một việc dạng `(done) -> Unit`. Việc kế tiếp chỉ bắt đầu khi việc trước gọi `done()` — kể cả khi
 * `done` đến từ một luồng nền hàng trăm ms sau. Đây là hình dạng **continuation** mà review Pass 1 gọi tên
 * (*"`runFrom` chạy tiếp từ callback"*): với các vế đồng bộ, `done` được gọi ngay trong lượt gọi ⇒ **không đổi một
 * byte** thứ tự/lời đáp của đường đơn mệnh đề (bài canh cũ giữ xanh); với vế bất đồng bộ, `done` đến sau nhịp chờ
 * ⇒ vế sau tự động đứng đợi.
 *
 * ## Vì sao KHÔNG phải một `Thread`/executor riêng, và không phải singleton toàn tiến trình
 *  • Không dựng luồng: `:core` phải kiểm được off-car với thứ tự tất định (cùng lẽ `MacroExec` **nhận** `sleep`
 *    làm tham số). Chỗ chờ thật vẫn nằm đúng ở lambda nền của `VoiceClimateStep` (bài canh
 *    `VoiceCommandWiringContractTest.nhip cho 400ms…` không đổi); làn này chỉ giữ **thứ tự**.
 *  • Không singleton: một việc quên gọi `done` sẽ **ghim** làn vĩnh viễn. Làn theo từng `VoiceDispatcher` (dựng
 *    lại mỗi lượt nói) thì cái giá xấu nhất là *phần còn lại của MỘT câu* không chạy — không phải *mọi câu lệnh
 *    giọng nói từ đây tới khi khởi động lại đầu xe*. Ưu tiên an toàn (CLAUDE.md mở đầu). Chồng lấn **giữa hai
 *    lượt nói** không xảy ra: một lượt giải mã mất ≥ 1,3 s ([ĐO xe]) > nhịp 400 ms, nên lượt sau không thể bắt đầu
 *    ghi khi lượt trước còn trong nhịp chờ ([SUY] từ hai con số đo, ghi ở doc `voice-write-lane-tail-prefix.md`).
 *
 * ## Ba bất biến (bài `VoiceWriteLaneTest`)
 *  1. FIFO: thứ tự nộp = thứ tự chạy; việc N+1 không bắt đầu trước `done` của việc N.
 *  2. `done` **đúng một lần**: gọi lần hai bị bỏ qua (không thả làn hai lần cho hai việc khác nhau).
 *  3. Việc **ném** trước khi gọi `done` ⇒ làn tự thả rồi ném tiếp — một ngoại lệ không được ghim mọi vế sau.
 */
class VoiceWriteLane {

    private val pending = ArrayDeque<(() -> Unit) -> Unit>()
    private var running = false

    /** Làn đang rỗi không — cho bài kiểm và nhật ký; **không** dùng để quyết định gì (cờ RAM, CLAUDE.md §5). */
    val idle: Boolean get() = synchronized(this) { !running && pending.isEmpty() }

    /**
     * Nộp một việc. Chạy **ngay trên luồng gọi** nếu làn rỗi (⇒ đường đơn mệnh đề y nguyên); nếu không, xếp hàng và
     * chạy trên luồng đã gọi `done` của việc trước.
     */
    fun submit(job: (done: () -> Unit) -> Unit) {
        val startNow = synchronized(this) {
            if (running) { pending.addLast(job); false } else { running = true; true }
        }
        if (startNow) start(job)
    }

    private fun start(job: (() -> Unit) -> Unit) {
        var fired = false
        val done: () -> Unit = {
            val next = synchronized(this) {
                if (fired) null else {
                    fired = true
                    pending.removeFirstOrNull().also { if (it == null) running = false }
                }
            }
            next?.let(::start)
        }
        try {
            job(done)
        } catch (t: Throwable) {
            done()
            throw t
        }
    }
}
