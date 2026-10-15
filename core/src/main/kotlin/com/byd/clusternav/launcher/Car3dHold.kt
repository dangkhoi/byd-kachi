package com.byd.clusternav.launcher

/**
 * ═══ 3.1.8 · `CAR3D-FALLBACK-STICKY` — vì sao widget xe 3D đang KHÔNG vẽ, và có thử lại không ═══════════════════════════════
 *
 * Owner 15/10 (3.1.6): *"trường hợp nào bị trả về 2D vậy? có nhiều lúc đã lên 3D rồi, nhưng lại bị trả về 2D hơi kỳ cục"* rồi
 * chốt: *"không nên lùi, xe nào cũng có 3D, không có thì thôi, không bao giờ lùi về 2D được không? không chạy thì bỏ qua, coi như
 * không khả thi là được"*. Trước: mọi lý do (kể cả lý do THOÁNG QUA) gọi `fallBack` ⇒ bảng 2D VĨNH VIỄN cho đời view đó.
 * [ĐO máy ảo 15/10, 3.1.6]: tệp đệm GLB dở ⇒ gltfio từ chối ⇒ 2D, tắt/bật màn + 20 s vẫn 2D; hai ô `w_car3d` ⇒ ô chính 2D, bung ô
 * chính (ô kia nhả Engine) vẫn 2D.
 *
 * Nay widget KHÔNG BAO GIỜ đổi sang bảng 2D. Không vẽ được ⇒ giữ khung 3D trống (ảnh nền) và:
 *  • lý do VĨNH VIỄN ([Why.permanent]: máy không kham, thiếu thư viện native, đời xe không có mô hình, mô hình rỗng) ⇒ một dòng
 *    trung tính, log MỘT lần, không thử lại;
 *  • ô nhỏ / thua chỗ Engine ⇒ chờ điều kiện hết (đổi cỡ · [Car3dSeat] đánh thức · hiện lại) — không hẹn giờ;
 *  • lỗi ([Why.FAULT]: ngoại lệ khi đổ số/chạm/vẽ/dựng cảnh, nạp mô hình ném, gltfio từ chối tệp đệm dở) ⇒ thử lại sau
 *    [backoffMs] (lùi dần), tối đa [maxFaults] lỗi cho đời view; quá ⇒ thôi (log), view mới (dựng lại ô) thử lại từ đầu.
 *
 * Thuần — `Car3dHoldTest`. Chỉ luồng chính gọi.
 */
class Car3dHold(private val maxFaults: Int = MAX_FAULTS, private val baseMs: Long = BASE_MS) {

    enum class Why(val permanent: Boolean) {
        SLOT(false), SEAT(false), FAULT(false),
        INCAPABLE(true), NO_LIBS(true), NO_MODEL(true), EMPTY_MODEL(true),
    }

    /** Một lượt dừng: [log] = có ghi log không (đổi lý do / lỗi mới — không lặp mỗi lượt đổi cỡ); [retryMs] = hẹn thử lại (`null` = chờ sự kiện). */
    data class Next(val log: Boolean, val retryMs: Long?, val gaveUp: Boolean = false)

    /** Lý do đang dừng; `null` = đang vẽ / đang nạp. */
    var why: Why? = null; private set
    var faults = 0; private set

    /** Còn được thử dựng không: không vĩnh viễn, chưa quá trần lỗi. */
    fun canStart(): Boolean = why?.permanent != true && faults <= maxFaults

    /** Bắt đầu một lượt dựng (đã qua cổng): lý do tạm xoá — lần dừng sau ghi log lại. */
    fun started() { if (why?.permanent != true) why = null }

    fun park(w: Why): Next {
        if (why?.permanent == true) return Next(log = false, retryMs = null)   // đã chốt không khả thi: im
        val changed = w != why
        why = w
        if (w != Why.FAULT) return Next(log = changed, retryMs = null)
        faults++
        return if (faults > maxFaults) Next(log = true, retryMs = null, gaveUp = true) else Next(log = true, retryMs = backoffMs(faults))
    }

    /** Lùi dần: lỗi thứ n ⇒ [baseMs] × 4^(n-1) (2 s · 8 s · 32 s). */
    fun backoffMs(n: Int): Long = baseMs shl (2 * (n - 1).coerceIn(0, 10))

    companion object {
        const val MAX_FAULTS = 3
        const val BASE_MS = 2_000L
    }
}
