package com.byd.clusternav.launcher.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.byd.clusternav.launcher.ControlLastSent

/**
 * ═══ 2.87 · SOÁT vòng 1 · P2 — CẦU `:wake` → tiến trình CHÍNH cho bảng *"lệnh cuối Kachi đã gửi"* ═══════════════════
 *
 * R-FL2 (spec `kachi-287-look-and-keys.html` §4.3) hứa MỘT trí nhớ cho ô trên màn + phím Đảo + giọng nói. Nhưng [ĐO đọc
 * mã] ba lối vào giọng nói chạy phiên ở `:wake` (phím vô-lăng gán Kachi nghe — LUÔN; nút mic khi mô hình ở `:wake`; câu
 * gọi Hey Kachi — danh sách + dẫn chứng ở KDoc [ControlLastSent]) và ghi vào [ControlLastSent.shared] CỦA `:wake`. Không
 * có cầu thì nói *"mở cốp"* bằng phím vô-lăng xong, ô cốp vẫn vẽ "đóng" và phím Đảo cốp lại MỞ một cốp đã mở.
 *
 * ## Cơ chế — CÙNG khuôn đã chạy của `VoiceEntry.ACTION_LISTEN_ACK` (`:wake` → chính)
 * `sendBroadcast(Intent(ACTION).setPackage(gói))` + `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)`: chỉ
 * trong gói, một chiều, mang dữ liệu chứ không phải kênh lệnh (không đổi gì ngoài một dòng RAM). [ĐO máy ảo 2026-09-26,
 * `docs/diagnostics/perf-closeout-2026-09-25.md`] chính đường `:wake` → chính ấy tới sau 52 ms. 🚗 Chưa đo trên xe —
 * xác nhận bằng `logcat -s KachiCtlRelay` (dòng "nhận" ở tiến trình chính sau một câu "mở cốp" qua phím vô-lăng).
 * Không dùng `VoiceWakeHomeRelay`: cầu đó đưa `KachiHomeActivity` LÊN (startActivity) — sai cho một dòng trí nhớ.
 *
 * ## Phạm vi (CLAUDE.md §4) · hoàn tác (§5)
 * Gửi: chỉ `:wake` ([forwardFromWake], gọi ở `VoiceWakeSessionFactory.buildSession`). Nhận: chỉ tiến trình chính
 * ([receiveInMain], `KachiApplication.onCreate` SAU cổng tiến trình nền). Ghi: một dòng RAM qua [ControlLastSent.absorb]
 * (kiểm hợp lệ, KHÔNG chuyển tiếp ⇒ không thể có vòng). Không đổi gì ngoài tiến trình, không bền ⇒ không cần hoàn tác.
 */
internal object ControlSentRelay {

    private const val TAG = "KachiCtlRelay"

    /** `:wake` → chính: "nút `control_id` vừa thành chỉ số `index`". Chỉ trong gói (`setPackage`), không phải API cho ai khác. */
    const val ACTION_CONTROL_SENT = "com.byd.launcher.CONTROL_SENT"
    private const val EXTRA_ID = "control_id"
    private const val EXTRA_INDEX = "index"

    /** `:wake`: nối bảng của tiến trình NÀY sang tiến trình chính. Gọi lại nhiều lần an toàn (lần sau thay lần trước). */
    fun forwardFromWake(ctx: Context) {
        val app = ctx.applicationContext ?: ctx
        ControlLastSent.shared.forwardTo { id, index -> send(app, id, index) }
    }

    /**
     * Một lượt gửi. `sendBroadcast` hỏng (system_server lỗi — ném dạng RuntimeException) thì chỉ mất một dòng trí nhớ ở
     * tiến trình chính: KHÔNG được làm hỏng câu trả lời của phiên giọng nói đang gọi `record` ⇒ bắt hẹp + log.
     */
    private fun send(ctx: Context, id: String, index: Int) {
        try {
            ctx.sendBroadcast(
                Intent(ACTION_CONTROL_SENT).setPackage(ctx.packageName).putExtra(EXTRA_ID, id).putExtra(EXTRA_INDEX, index),
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "chuyển $id=$index sang tiến trình chính hỏng — ô/phím giữ trí nhớ cũ cho nút này", e)
        }
    }

    /**
     * Tiến trình CHÍNH: nhận và ghi vào [ControlLastSent.shared] (cùng bảng ô + phím đọc). Một lần mỗi tiến trình, trên
     * applicationContext (tin của TIẾN TRÌNH, không của màn). Hỏng đăng ký ⇒ log, launcher vẫn chạy (tính năng phụ không
     * được giết `Application.onCreate`) — hệ quả chỉ là giới hạn cũ (trí nhớ `:wake` riêng).
     */
    fun receiveInMain(ctx: Context) {
        val app = ctx.applicationContext ?: ctx
        try {
            ContextCompat.registerReceiver(app, Receiver(), IntentFilter(ACTION_CONTROL_SENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: RuntimeException) {
            Log.w(TAG, "không đăng ký được receiver cầu `:wake` → chính — trí nhớ ô/phím không thấy lệnh giọng nói `:wake`", e)
        }
    }

    private class Receiver : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action != ACTION_CONTROL_SENT) return
            val id = i.getStringExtra(EXTRA_ID)
            val index = i.getIntExtra(EXTRA_INDEX, -1)
            if (ControlLastSent.shared.absorb(id, index)) Log.i(TAG, "nhận từ `:wake`: $id=$index")
            else Log.w(TAG, "bỏ dòng không hợp lệ từ `:wake`: $id=$index")
        }
    }
}
