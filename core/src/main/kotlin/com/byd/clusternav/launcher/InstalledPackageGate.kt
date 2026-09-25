package com.byd.clusternav.launcher

/**
 * Cổng "gói đích có cài không" cho một thông điệp gửi định kỳ tới app khác — THUẦN, nhận `nowMs`, test đồng hồ giả.
 *
 * F6 (B1 · `kachi-closeout-hardening` R3): [ĐO máy ảo 2.65] `VmOverlayPos gửi VM_BUBBLE_POS … → vn.vietmap.live`
 * mỗi 16 s trong khi VietMap **không cài** — một broadcast + một dòng log cho một người nhận không tồn tại. Hỏi
 * PackageManager mỗi nhịp 2 s cũng là binder thừa ⇒ nhớ kết quả [ttlMs] (gói cài/gỡ giữa chừng thì tối đa [ttlMs]
 * sau là thấy). Log "bỏ vì không cài" chỉ MỘT lần cho mỗi chuỗi vắng liên tiếp — cài xong rồi lại gỡ thì log lại.
 */
class InstalledPackageGate(private val ttlMs: Long) {
    private var checkedAtMs = Long.MIN_VALUE
    private var installed = false
    private var absenceLogged = false

    /**
     * `true` = gói có cài (theo lần dò gần nhất trong [ttlMs]). [probe] chỉ được gọi khi hết hạn nhớ.
     * [onFirstAbsence] chạy đúng một lần khi chuyển sang "không cài" (để log 1 dòng, không spam).
     */
    fun installed(nowMs: Long, probe: () -> Boolean, onFirstAbsence: () -> Unit = {}): Boolean {
        if (checkedAtMs == Long.MIN_VALUE || nowMs - checkedAtMs >= ttlMs) {
            installed = probe()
            checkedAtMs = nowMs
        }
        if (installed) {
            absenceLogged = false
        } else if (!absenceLogged) {
            absenceLogged = true
            onFirstAbsence()
        }
        return installed
    }
}
