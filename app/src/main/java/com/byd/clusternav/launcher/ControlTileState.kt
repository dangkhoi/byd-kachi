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
 */
class ControlTileState {
    // ConcurrentHashMap, KHÔNG phải HashMap: gói lệnh (W2) ghi trạng thái từ **thread nền** (xem `macroTile`) trong
    // khi thread chính đang đọc để vẽ ô ⇒ HashMap ở đây là tranh chấp dữ liệu thật. Đổi sang map đồng thời là cách
    // rẻ nhất và không đổi API.
    private val on = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val values = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val selIndex = java.util.concurrent.ConcurrentHashMap<String, Int>()

    init { ControlRegistry.ALL.forEach { on[it.id] = it.onByDefault; values[it.id] = it.value } }

    fun isOn(id: String): Boolean = on[id] == true
    fun setOn(id: String, v: Boolean) { on[id] = v }
    fun value(def: ControlDef): Int = values[def.id] ?: def.value
    fun setValue(id: String, v: Int) { values[id] = v }
    fun sel(id: String): Int = selIndex[id] ?: 0
    fun setSel(id: String, i: Int) { selIndex[id] = i }

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
    }
}
