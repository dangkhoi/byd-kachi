package com.byd.clusternav.launcher.testbridge

import android.content.Context
import com.byd.clusternav.launcher.voice.VoiceUtteranceLog

/**
 * ═══ T-BRIDGE · LỆNH `voice_dump` — NÉN `voice-log/` RA THẺ, TRẢ VỀ ĐƯỜNG DẪN ════════════════════════════════
 *
 * Tách khỏi [KachiTestBridge] vì trần 500 dòng (CLAUDE.md §4.1; bài canh `TestBridgeSafetyContractTest`) — cùng
 * hình dạng với [TestBridgeWav]/[TestBridgeCtl]/[TestBridgeHal]: receiver lo cổng + vòng đời, tệp này lo một lệnh.
 *
 * ## Vì sao nó đi qua ĐÚNG hàm mà nút trong Cài đặt đi
 * [VoiceUtteranceLog.exportZip] là **một** thân hàm cho cả hai lối vào (nút *Xuất nhật ký voice* · lệnh này). Một
 * đường nén thứ hai ở đây sẽ lệch ở đúng lần ai đó sửa một tính chất — vd lượt lùi khi ROM chặn ghi vào `Download`
 * — và lúc ấy phép đo bằng máy sẽ nói về một tệp mà người trên xe không bao giờ nhận được (cùng luật
 * [TestBridgeWav]: không mở một đường đọc thứ hai cho cùng một việc).
 *
 * ## Luồng NỀN, không phải luồng nhận broadcast
 * Nén vài chục MB mất hàng giây. `onReceive` chạy trên luồng chính của app; nén ở đó là treo màn hình người lái
 * đang nhìn — và nền tảng sẽ tự bắn ANR trước khi tệp zip xong. `goAsync()` của [TestBridgeReply] giữ lượt chạy
 * sống cho tới khi có lời đáp (trần [KachiTestBridge.CAP_MS] vẫn áp: hết giờ thì lời đáp là `timeout`, không phải
 * im lặng).
 *
 * ## Cổng `auto_confirm` — vì quyền riêng tư, không vì cơ khí
 * Lệnh không đụng xe, nhưng nó **chép tiếng cabin** (WAV từng lượt nói) ra `Download/` — thư mục mọi app đọc được —
 * và receiver này `exported=true`. [SCAN §6 1.69, W5]: một app lạ trên đầu xe chỉ cần bật được chế độ kiểm thử là kéo
 * được tiếng nói của người lái ra ngoài. Nên lệnh đi qua đúng cổng `--ez auto_confirm true` của `hal set`/`ctl`, và
 * để dấu `AUTO-CONFIRM` trong logcat như mọi lượt qua cổng.
 */
internal object TestBridgeVoiceDump {

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        if (!cmd.autoConfirm) {
            reply.fail(ERR_NEEDS_CONFIRM, "hint" to NOTE_CONFIRM)
            return
        }
        android.util.Log.i(TestBridgeReply.TAG, "AUTO-CONFIRM: voice_dump (xuat tieng cabin ra Download)")
        Thread({
            when (val r = VoiceUtteranceLog.exportZip(app)) {
                is VoiceUtteranceLog.Export.Ok -> reply.ok(
                    listOf(
                        "path" to r.path,
                        "files" to r.entries,
                        "bytes" to r.bytes,
                        // Thư mục NGUỒN đi kèm để lượt đo sau còn `adb shell run-as … ls` được chính nó — lời đáp
                        // phải đủ để đi bước tiếp theo mà không phải tra mã.
                        "dir" to VoiceUtteranceLog.dir(app).absolutePath,
                        "keep_log" to VoiceUtteranceLog.enabled(app),
                    ),
                )
                is VoiceUtteranceLog.Export.Failed -> reply.fail(
                    ERR_EXPORT,
                    "reason" to r.reason,
                    "keep_log" to VoiceUtteranceLog.enabled(app),
                )
            }
        }, "KachiVoiceDump").start()
    }

    /** Không nén được (chưa có lượt nào / đĩa hỏng) — ASCII, không dịch (cùng luật `TestBridgeParse.Err`). */
    const val ERR_EXPORT = "voice_dump_failed"

    /** Thiếu cổng — cùng mã lỗi với `hal set`/`ctl` để script đo nhận ra một họ. */
    const val ERR_NEEDS_CONFIRM = "needs_confirm"
    const val NOTE_CONFIRM = "them --ez auto_confirm true de xuat tieng cabin ra Download (du lieu ca nhan)"
}
