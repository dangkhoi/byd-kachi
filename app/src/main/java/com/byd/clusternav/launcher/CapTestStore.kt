package com.byd.clusternav.launcher

import android.content.Context

/**
 * Lưu bền kết quả **kiểm tra từng nút** (owner 2026-09-15) — id → OK/Không OK/chưa soát + thời điểm + ghi chú.
 *
 * Tệp prefs RIÊNG `kachi_captest` (không đi theo hồ sơ — kết quả soát là của **chiếc xe này**, không phải lựa chọn
 * của một tài xế; cùng lẽ `kachi_test_bridge`/`kachi_voice`). Mã hoá/giải mã ở `:core` ([CapTestCodec]) nên phần
 * bền test được off-car; lớp này chỉ là vỏ prefs mỏng.
 */
class CapTestStore(context: Context) {

    private val ctx = context.applicationContext
    private val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Bảng kết quả hiện có (id → dòng chấm). Rỗng = chưa soát gì. */
    fun load(): Map<String, CapTestResult> =
        CapTestCodec.decodeAll(sp.getString(KEY, "") ?: "")

    /** Chấm một mục — ghi đè kết quả cũ của id đó, đóng dấu thời gian bây giờ. */
    fun record(id: String, verdict: CapTestVerdict, note: String = "") {
        val m = load().toMutableMap()
        m[id] = CapTestResult(id, verdict, System.currentTimeMillis(), note)
        sp.edit().putString(KEY, CapTestCodec.encodeAll(m)).apply()
        // Owner 2026-09-15: tự lưu báo cáo ra THẺ sau mỗi lần chấm, để lên xe soát tới đâu là có file tới đó (kể cả
        // khi app chết giữa chừng thì báo cáo đã nằm trên thẻ). Ghi THẺ = I/O ⇒ ĐẨY SANG LUỒNG NỀN, không chặn luồng
        // vẽ: chấm tuần tự (startSequential) gọi record() liên tiếp; SD chậm có thể ghì khung nếu writeText chạy trên
        // main thread. Cùng lẽ đọc HAL off-thread ở CapTestConsole. Snapshot map bất biến để luồng nền đọc an toàn.
        val reportSnapshot = m.toMap()
        Thread({ runCatching { KachiLog.saveCaptestReport(ctx, CapTestReport.build(reportSnapshot)) } },
            "KachiCapReport").apply { isDaemon = true }.start()
    }

    /** Xoá toàn bộ kết quả (bắt đầu lại một lượt soát). */
    fun clear() {
        sp.edit().remove(KEY).apply()
    }

    /**
     * Xuất BÁO CÁO chữ (để dán vào chẩn đoán) — dựng ở `:core` ([CapTestReport], song ngữ + pure) trên kết quả
     * hiện có. Đây là "tài liệu" owner muốn, luôn TƯƠI theo kết quả vừa soát trên xe.
     */
    fun exportReport(): String = CapTestReport.build(load())

    companion object {
        const val PREFS = "kachi_captest"
        private const val KEY = "captest_results"
    }
}
