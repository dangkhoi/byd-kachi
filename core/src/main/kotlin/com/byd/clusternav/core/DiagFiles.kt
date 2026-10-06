package com.byd.clusternav.core

/**
 * ═══ DANH SÁCH CHO PHÉP của bộ dọn chẩn đoán — DIAG-CAP-USERDATA (2.92, spec `kachi-292-diag-cap.html`) ═══
 *
 * Bộ dọn `DiagStorageCap` (`:app`) giữ thư mục ngoài của app (`getExternalFilesDir(null)`) dưới ~150 MB bằng
 * cách xoá tệp CŨ NHẤT trước. Tới 2.91 nó quét **cả cây** — mà cây ấy còn chứa **dữ liệu người dùng**: ảnh trình
 * chiếu (`photos/`), hình nền (`wallpapers/`), ảnh xe (`car/`), hồ sơ xuất (`profiles/`), gói giọng side-load
 * (`sherpa/import/…`). Một chuyến thu log dài là đủ để những tệp ấy — thường là CŨ NHẤT — bị xoá trước log [ĐO mã].
 *
 * Từ 2.92 luật đảo lại: **chỉ** những đường dưới đây là của bộ dọn; mọi đường khác — kể cả thư mục mới ai đó
 * thêm sau này — **không bao giờ** bị chạm. Thêm một bộ ghi chẩn đoán mới ⇒ thêm đường của nó vào đây (bài canh
 * `DiagStorageCapWiringContractTest` bắt mọi lời gọi `getExternalFilesDir` phải được xếp loại chẩn đoán/người dùng).
 *
 * Đường ở đây là **tương đối** gốc thư mục ngoài, phân cách `/`, phân biệt hoa thường — đúng chuỗi bộ ghi tạo ra.
 */
object DiagFiles {

    /** `KachiLog` — usage/snapshot/crash/captest + tệp đo của cầu kiểm thử (camera/featmap/sweep) + log inputd. */
    const val KACHI_LOGS = "kachi-logs"

    /** `ClusterDiag` — nút "Chẩn đoán" (`diag-<stamp>.txt`, tự giữ 20 tệp). */
    const val DIAG = "diag"

    /** `TestBridgeReply` — tệp kết quả lệnh cầu kiểm thử (`<stamp>-<lệnh>.json`, tự giữ một số tệp). */
    const val TEST = "test"

    /** `ClusterCast` TEE (`castlog/cast_<tag>_v<ver>_<ts>.txt`) — bộ ghi đã gỡ ở 1.63, chỉ còn tàn dư để dọn. */
    const val CASTLOG = "castlog"

    /** Thư mục chẩn đoán: MỌI tệp bên trong (mọi độ sâu) thuộc bộ dọn. */
    val DIRS: List<String> = listOf(KACHI_LOGS, DIAG, TEST, CASTLOG)

    /** Tệp mẫu tiếng của cầu kiểm thử (`TestBridgeWav`/`TestBridgeKws` chép vào; = `VoiceWavProbe.FILE_NAME`). */
    const val VOICE_TEST_WAV = "kachi-voice-test.wav"

    /**
     * Tệp chẩn đoán ở **gốc**, dạng `<tiền tố><mốc><hậu tố>` với mốc chỉ gồm chữ số và `-`, có ít nhất MỘT chữ số (đúng
     * mọi bộ ghi: `currentTimeMillis` hoặc `yyyyMMdd-HHmmss-SSS`). Mốc hẹp vậy để một tệp người dùng tình cờ cùng tiền tố
     * (vd `kachi-voice-backup.zip`, `kachi-voice--.zip`) vẫn KHÔNG khớp.
     */
    val ROOT_FILES: List<Pair<String, String>> = listOf(
        "nav_notif_log_" to ".csv",   // NavNotifLog
        "nav_notif_raw_" to ".csv",   // NavNotifRawLog
        "kachi-voice-" to ".zip",     // VoiceUtteranceLog — đường lùi khi ROM không cho ghi vào Download
    )

    /**
     * `true` ⇔ [relativePath] (tương đối gốc thư mục ngoài, phân cách `/`) là tệp do bộ ghi chẩn đoán tạo ra.
     *
     * Từ chối mọi thứ mơ hồ — rỗng, tuyệt đối (`/…`), có đoạn rỗng/`.`/`..`, có `\` — vì câu trả lời sai ở hướng
     * `true` là mất dữ liệu người dùng, còn sai ở hướng `false` chỉ là một tệp log sống lâu hơn.
     */
    fun isDiagnostic(relativePath: String): Boolean {
        if (relativePath.isEmpty() || relativePath.startsWith("/") || '\\' in relativePath) return false
        val parts = relativePath.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return false
        if (parts.size >= 2) return parts[0] in DIRS
        val name = parts[0]
        if (name == VOICE_TEST_WAV) return true
        return ROOT_FILES.any { (prefix, suffix) ->
            name.length > prefix.length + suffix.length && name.startsWith(prefix) && name.endsWith(suffix) &&
                name.substring(prefix.length, name.length - suffix.length).let { stamp ->
                    stamp.all { it in '0'..'9' || it == '-' } && stamp.any { it in '0'..'9' }
                }
        }
    }
}
