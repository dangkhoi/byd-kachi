package com.byd.clusternav.launcher

/**
 * ═══ T-BRIDGE · SỔ GHI KẾT QUẢ GHI HAL (trong tiến trình) ════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` §9 (grab-list HAL). CLAUDE.md §11 (app tự chụp, không bắt user gõ adb)
 * + §14 (đo bằng dữ liệu THẬT trên xe).
 *
 * ## Vì sao cần lớp này — và vì sao nó KHÔNG phải "đường thứ hai"
 * Lời gọi HAL đi qua đúng một đường: `CarControlPort` → [HalBindingTable.write] → [BydHalGateway] → reflection. Cái
 * đường đó **nén** kết quả về `Long?` rc (rồi về `Boolean` ở port), nên câu chữ THẬT mà HAL trả — `rc=0`, hay chuỗi
 * ngoại lệ *"no permission to use the feature"* của `AbsBYDAutoDevice` — bị mất TRƯỚC khi tới cầu kiểm thử. [ĐO]
 * 2026-09-14: `callNamedInt` bắt ngoại lệ rồi trả `root(it)`, nhưng `parseRc` thấy chuỗi không mở đầu `rc=` nên
 * trả `null` ⇒ lý do bị nuốt.
 *
 * Sổ này chỉ **đọc trộm** câu chữ đó ngay tại [BydHalGateway] — nơi duy nhất còn thấy nó — chứ KHÔNG mở một đường
 * ghi mới. Nó là quan sát bị động trên cùng một đường, đúng như một dòng logcat; khác ở chỗ đọc được TRONG tiến
 * trình (CLAUDE.md không cho spawn `logcat` từ app).
 *
 * ## Vì sao process-global, last-write-wins
 * Cùng hình dạng [ControlTileState.shared] / `KachiTestHooks` / `SlotVdOwner`: receiver được nền tảng dựng mới cho
 * mỗi broadcast nên không giữ được tham chiếu tới gateway (gateway nằm trong `AppContainer`, dựng lười). Một
 * `object` là cầu nối rẻ nhất. Cầu kiểm thử [clear] TRƯỚC khi bắn rồi đọc [last] SAU — buổi quét HAL chạy tuần tự
 * một control một lúc (owner nhìn mắt), nên last-write-wins là đủ; nếu có lượt ghi khác chen vào thì [last] mang
 * `seq` để chỗ đọc biết "đã có một lượt ghi kể từ khi tôi xoá".
 *
 * ⚠ Đường ghi nav-frame (`writeNavFrame`) KHÔNG đi qua [BydHalGateway] (nó gọi thẳng `BydHal.setInt`/`setBytes`),
 * nên sổ này KHÔNG bị ~4 khung/giây của nav làm nhiễu — chỉ ghi lượt CONTROL.
 */
object HalWriteProbe {

    /**
     * Kết quả một lượt ghi control.
     *
     * @property raw câu chữ THẬT quan sát được ở tầng gateway/reflection: `"rc=0"`, `"rc=-2147482648"` (sentinel),
     *   chuỗi ngoại lệ rút gọn (`"SecurityException: no permission to use the feature ... 1004"`), hoặc `"off_car"`
     *   khi device null (emulator / ngoài xe). Đây là thứ đi vào trường `hal_line` của JSON.
     * @property device FQN thiết bị mà lượt ghi nhắm tới (đối chiếu với route ở lời đáp).
     * @property method tên method / mã feature đã gọi.
     * @property seq số thứ tự tăng dần toàn cục — chỗ đọc so với giá trị lúc [clear] để chắc "lượt ghi này là của
     *   tôi", không phải một lượt cũ còn sót.
     */
    data class Outcome(val raw: String, val device: String, val method: String, val seq: Long)

    @Volatile
    private var value: Outcome? = null
    private val counter = java.util.concurrent.atomic.AtomicLong(0)

    /** Kết quả ghi control gần nhất, hoặc `null` nếu chưa có lượt nào kể từ [clear]. */
    val last: Outcome? get() = value

    /** Số `seq` hiện tại — cầu kiểm thử đọc trước khi bắn để biết mốc "kể từ đây". */
    fun mark(): Long = counter.get()

    /** Xoá kết quả cũ. Cầu kiểm thử gọi TRƯỚC mỗi lệnh `ctl`. */
    fun clear() { value = null }

    /** [BydHalGateway] gọi sau MỖI lượt ghi control (kể cả lượt off-car/ngoại lệ) — nguồn duy nhất ghi vào sổ. */
    fun record(device: String, method: String, raw: String) {
        value = Outcome(raw = raw, device = device, method = method, seq = counter.incrementAndGet())
    }
}
