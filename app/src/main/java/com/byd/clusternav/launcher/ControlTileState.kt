package com.byd.clusternav.launcher

// ⚠ [S4 · R12] Tách khỏi `ControlTileFactory.kt` vì tệp đó đã chạm ĐÚNG trần 500 dòng của dự án (CLAUDE.md §4.1)
// khi R12 thêm ô kiểu LAUNCHER. Đường cắt theo VAI, không cắt bừa cho vừa số dòng — cùng lệ `ReadTile.kt`:
// đây là **trạng thái** (cái mà nhiều ô cùng đọc/ghi, sống lâu hơn mọi View), còn `ControlTileFactory` là **bộ
// dựng** ô. Hai vai vốn đã không gọi lẫn nhau: lớp này không biết gì về `android.view`.
//
// ⚠⚠ `ActionMacroWiringContractTest.bang trang thai dung chung phai an toan da luong` cắt vùng theo tệp, nên bài đó
// đổi đường dẫn sang đây trong CÙNG lượt — nếu không, `SourceRoots.body(...)` sẽ `require` hỏng và bài nổ ngay.

/**
 * Trạng thái ô nút mà UI tự giữ (lạc quan): bật/tắt · giá trị −/+ · lựa chọn đang chọn.
 *
 * Vì sao có [shared]: cùng một nút giờ đặt được ở **hai vùng** (thanh nút và ô giữa màn). Nếu mỗi vùng giữ một bảng
 * riêng thì bật "lấy gió trong" ở ô giữa màn xong nhìn sang thanh nút vẫn thấy tắt — hai bề mặt nói hai điều về
 * MỘT cái xe. Một bảng dùng chung cho cả tiến trình khớp với thực tế (chỉ có một cái xe) và không tốn gì.
 *
 * ⚠ Đây KHÔNG phải trạng thái đọc từ xe (phần lớn nút không có đường đọc lại) — nó chỉ là "tôi vừa bấm cái này".
 *
 * ═══ 2.87 · R-FL2 — bật/tắt + lựa chọn sống ở [ControlLastSent] (`:core`), KHÔNG còn map riêng ở đây ═══════════
 * Owner 03/10 muốn MỘT phím Đảo cho cốp/kính/… như cú chạm ô. Phím quyết Đảo bằng lệnh cuối Kachi đã gửi khi không
 * đọc được xe; nếu ô giữ một bảng riêng thì *"mở cốp bằng ô rồi bấm phím Đảo"* sẽ MỞ lần nữa thay vì ĐÓNG — ô và
 * phím nói hai điều về một cái cốp. Nay [isOn]/[setOn]/[sel]/[setSel] đọc/ghi THẲNG bảng dùng chung đó (chỉ số theo
 * nghĩa `actByKind`: TOGGLE 1/0 · COVER mức · SELECT chỉ số); API giữ nguyên nên mọi chỗ gọi cũ (ô · giọng nói · gói
 * lệnh · đọc lại) tự ghi vào cùng một chỗ. Mặc định khi tiến trình bật: [ControlLastSent.startIndex].
 */
class ControlTileState(
    /** Bảng lệnh cuối của tiến trình — mặc định bảng dùng chung mà phím (`KeyCtlPlan`) đọc. */
    private val sent: ControlLastSent = ControlLastSent.shared,
) {
    // ConcurrentHashMap, KHÔNG phải HashMap: gói lệnh (W2) ghi trạng thái từ **thread nền** (xem `macroTile`) trong
    // khi thread chính đang đọc để vẽ ô ⇒ HashMap ở đây là tranh chấp dữ liệu thật. Đổi sang map đồng thời là cách
    // rẻ nhất và không đổi API. (Bật/tắt + lựa chọn: map đồng thời của [ControlLastSent].)
    private val values = java.util.concurrent.ConcurrentHashMap<String, Int>()

    init { ControlRegistry.ALL.forEach { values[it.id] = it.value } }

    fun isOn(id: String): Boolean = sent.index(id) > 0
    fun setOn(id: String, v: Boolean) { sent.record(id, if (v) 1 else 0) }
    fun value(def: ControlDef): Int = values[def.id] ?: def.value
    fun setValue(id: String, v: Int) { values[id] = v }
    fun sel(id: String): Int = sent.index(id)
    fun setSel(id: String, i: Int) { sent.record(id, i) }

    /**
     * ═══ CỬA SỔ ÂN HẠN sau khi NGƯỜI DÙNG vừa bấm (2026-09-17 · đọc realtime) ══════════════════════════════
     *
     * Khi ô control đọc lại giá trị THẬT từ xe theo nhịp poll ([CarStatus.controls]), nó phải bỏ qua trong một
     * khoảnh khắc ngắn sau khi người lái vừa chạm ô: xe cần vài trăm ms tới vài giây để áp lệnh, còn nhịp chậm
     * tới 10 s — nếu reconcile ngay thì bấm "+" xong con số **nháy ngược** về mức cũ rồi mới lên. [touch] ghi mốc
     * mỗi lần chạm; [touchedWithin] trả true trong [GRACE_MS] để refresh nhường cho giá trị lạc quan.
     */
    private val touchedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    fun touch(id: String, now: Long = System.currentTimeMillis()) { touchedAt[id] = now }
    fun touchedWithin(id: String, now: Long = System.currentTimeMillis(), ms: Long = GRACE_MS): Boolean =
        touchedAt[id]?.let { now - it < ms } ?: false

    /**
     * Chốt "gói lệnh này đang chạy" — **dùng chung theo mã gói, KHÔNG theo View**.
     *
     * ## ⚠ [SOÁT P1-1] Vì sao không để cờ trong View
     * Bản trước giữ `AtomicBoolean` **bên trong** ô (`macroTile`). Trên xe, trạng thái xe đổi mỗi giây và ô TRỘN
     * (có mục đọc + gói lệnh) bị **dựng lại** theo nhịp đó ⇒ ô mới có cờ mới `false` ⇒ người dùng bấm lần hai trong
     * lúc lượt một còn đang chạy ⇒ **hai lượt "đóng hết kính" chạy chồng nhau**, đúng thứ cờ này sinh ra để chặn.
     * Chốt theo mã gói thì dựng lại bao nhiêu lần cũng không mở được cửa thứ hai.
     */
    fun beginRun(id: String): Boolean = running.putIfAbsent(id, true) == null

    /** Nhả chốt. PHẢI gọi trong `finally`, trên chính thread đang chạy gói. */
    fun endRun(id: String) { running.remove(id) }

    private val running = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    companion object {
        /** Bảng dùng chung mọi vùng trong cùng tiến trình. */
        val shared = ControlTileState()

        /**
         * Bao lâu sau một cú chạm thì ô còn tin giá trị LẠC QUAN thay vì giá trị đọc từ xe.
         *
         * **2500 ms** = quãng cần cho xe áp lệnh + một nhịp nhanh (1 s) xác nhận, dưới nhịp chậm (10 s) nên con
         * số sẽ về đúng thực tế ở lần poll kế. Ngắn hơn thì nháy ngược khi xe áp chậm; dài hơn thì người lái đổi
         * ở màn BYD gốc ngay sau khi bấm trên launcher sẽ chờ lâu mới thấy.
         */
        const val GRACE_MS = 2_500L
    }
}
