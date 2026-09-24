package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.voice.VoiceWavProbe
import com.byd.clusternav.launcher.voice.WakeAsrMatcher
import com.byd.clusternav.launcher.voice.VoiceWiring
import java.io.File

/**
 * ═══ T-BRIDGE · LỆNH `wav` — MỘT TỆP WAV ĐI QUA ĐÚNG [VoiceWavProbe] ═══════════════════════════════════════════
 *
 * Tách khỏi [KachiTestBridge] vì trần 500 dòng (CLAUDE.md §4.1; bài canh `TestBridgeSafetyContractTest`) — cùng
 * hình dạng với [TestBridgeCtl]/[TestBridgeHal]/[TestBridgeSweep]: receiver lo cổng + vòng đời, tệp này lo một lệnh.
 * Thân hàm giữ NGUYÊN (chỉ dời chỗ) — không đổi hành vi.
 *
 * `--es path` được phục vụ bằng cách **chép** tệp vào đúng chỗ mà [VoiceWavProbe] dò (tên cố định
 * [VoiceWavProbe.FILE_NAME] trong thư mục ngoài của riêng app). Chép chứ không mở một đường đọc thứ hai: đường đọc
 * thứ hai sẽ không đi qua cùng phép kiểm khuôn WAV, và lúc đó phép đo nói về một con đường mã mà phiên nghe thật
 * không dùng — đúng thứ KDoc [VoiceWavProbe] cấm.
 */
internal object TestBridgeWav {

    fun run(app: Context, cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        Thread({
            val stageError = stage(app, cmd.path)
            if (stageError != null) {
                reply.fail(stageError, "path" to cmd.path)
                return@Thread
            }
            val apps = VoiceWiring.appsByLabel(app)
            val probe = VoiceWavProbe.run(app, hooks.state().profiles, apps.keys.toList(), apps.values.toSet())
            val intents = if (probe.heard.isBlank()) {
                emptyList()
            } else {
                // CHỈ phân tích, KHÔNG thi hành: đây là một phép đo tai nghe, không phải một lệnh.
                hooks.dispatcher({ }, { _, _, onNo -> onNo() }).preview(probe.heard)
            }
            reply.ok(
                listOf(
                    "path" to probe.path,
                    "staged_from" to cmd.path.ifBlank { null },
                    "heard" to probe.heard,
                    "wake" to WakeAsrMatcher.isWake(probe.heard),
                    "grammar" to probe.grammarText,
                    "free" to probe.freeText,
                    "probe_error" to probe.error,
                    "where" to VoiceWavProbe.whereToPut(app),
                    "intents" to TestBridgeJson.Raw(TestBridgeJson.arr(intents.map { KachiTestBridge.previewOf(it) })),
                ),
            )
        }, "KachiTestWav").start()
    }

    /**
     * Chép tệp WAV do lệnh chỉ định vào chỗ [VoiceWavProbe] dò. Trả **mã lỗi** hoặc `null` khi xong/không cần.
     *
     * Ba phép kiểm, mỗi phép chặn một ca thật: đường dẫn có `..` (chuỗi này đến từ ngoài tiến trình), tệp không
     * đọc được (gõ nhầm tên — hay gặp nhất), tệp quá lớn (đẩy nhầm một bản ghi dài làm đầy bộ nhớ xe). Tên tệp
     * ĐÍCH là hằng của [VoiceWavProbe] nên không có phần nào của chuỗi vào được đường dẫn ghi.
     */
    private fun stage(app: Context, path: String): String? {
        if (path.isBlank()) return null
        if (path.contains("..")) return KachiTestBridge.ERR_BAD_PATH
        val src = File(path)
        if (!src.isFile || !src.canRead()) return KachiTestBridge.ERR_WAV_NOT_FOUND
        if (src.length() > KachiTestBridge.MAX_WAV_BYTES) return KachiTestBridge.ERR_WAV_TOO_BIG
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        return runCatching {
            src.copyTo(File(dir, VoiceWavProbe.FILE_NAME), overwrite = true)
            null
        }.getOrElse { t ->
            Log.w(KachiTestBridge.TAG, "chep WAV hong: ${t.javaClass.simpleName}")
            KachiTestBridge.ERR_WAV_COPY
        }
    }
}
