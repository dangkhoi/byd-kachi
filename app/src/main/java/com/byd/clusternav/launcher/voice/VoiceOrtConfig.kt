package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log
import java.io.File

/**
 * WAKE-DECODE-COST (3.1.12) — đặt tệp tuỳ chọn phiên onnxruntime [SherpaModelCatalog.ORT_SESSION_CONFIG] xuống đĩa và trả chuỗi
 * `provider` cho [VoiceEngine] (số đo + lý do: KDoc của hằng ở `:core`).
 *
 * Fail-safe: sherpa-onnx **thoát tiến trình** khi không mở được tệp nêu trong provider (`SHERPA_ONNX_EXIT`, `session.cc`
 * v1.13.8) ⇒ chỉ trả `cpu:<tệp>` sau khi đã đọc lại được đúng nội dung; ghi/đọc hỏng ⇒ `cpu` (hành vi trước 3.1.12, có log).
 */
internal object VoiceOrtConfig {

    private const val TAG = "KachiVoiceEngine"

    /** Gọi trên luồng dựng recognizer (luồng nền). */
    fun provider(ctx: Context): String {
        val path = runCatching {
            val f = File(ctx.filesDir, SherpaModelCatalog.ORT_SESSION_CONFIG_FILE)
            val want = SherpaModelCatalog.ORT_SESSION_CONFIG
            if (!f.isFile || f.readText() != want) {
                // Ghi NGUYÊN TỬ (tệp tạm + rename): tiến trình chính và `:wake` cùng dựng recognizer trên CÙNG tệp — ghi đè tại chỗ
                // có thể để sherpa của tiến trình kia mở đúng lúc tệp đang rỗng/dở dòng (dòng dở bị bỏ qua, nhưng quay chờ trở lại
                // mà không ai thấy). rename trên cùng thư mục là nguyên tử ⇒ chỉ thấy bản cũ trọn vẹn hoặc bản mới trọn vẹn.
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(want)
                check(tmp.renameTo(f)) { "rename hỏng" }
            }
            check(f.canRead() && f.readText() == want) { "đọc lại không khớp" }
            f.absolutePath
        }.onFailure { Log.w(TAG, "không ghi được tuỳ chọn onnxruntime — chạy provider=cpu như cũ", it) }.getOrNull()
        return SherpaModelCatalog.provider(path)
    }
}
