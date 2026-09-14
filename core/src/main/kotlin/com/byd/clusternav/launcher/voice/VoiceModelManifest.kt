package com.byd.clusternav.launcher.voice

/**
 * ═══ V1 pha NGHE · GÓI MÔ HÌNH NHẬN DẠNG — KHAI **MỘT CHỖ**, GHIM BẰNG SỐ ĐO THẬT ════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R9**. Thuần Kotlin (`:core`) ⇒ mọi luật ở đây kiểm off-car; phần
 * chạm đĩa/mạng nằm ở `:app` (`VoiceModelStore`).
 *
 * ## Vì sao mô hình KHÔNG đóng vào APK
 * [ĐO] gói nén 33.656.337 byte (32,1 MiB), giải ra 53.290.365 byte. APK Kachi 1.48 nặng 9,06 MB và đi qua kênh
 * OTA `apk/` trên GitHub ([com.byd.clusternav.UpdateChecker]) — nhét mô hình vào là **mỗi bản vá một dòng chữ**
 * cũng bắt người dùng tải lại 32 MB qua mạng 4G của xe. Tải riêng một lần, kiểm toàn vẹn, dùng mãi.
 *
 * ## Vì sao ghim CẢ sha256 LẪN kích thước, không chỉ một
 * Kích thước bắt được ca hay gặp nhất (**tải cụt** — mạng xe rớt giữa chừng) mà không tốn gì, và bắt được nó
 * **trước** khi băm xong 32 MB. sha256 bắt ca còn lại: tệp đủ dài nhưng sai nội dung (proxy chèn trang lỗi, gói
 * bị thay ở máy chủ). Thiếu sha thì một trang HTML 33 MB vẫn qua cửa; thiếu kích thước thì phải băm xong mới
 * biết. [ĐO] `curl -L … | shasum -a 256` trên máy soạn thảo 2026-09-14 ⇒ [ZIP_SHA256].
 *
 * ## Giấy phép
 * Apache-2.0 (alphacephei). Ghi vào `CREDITS.md` khi bật kênh tải cho người dùng.
 */
object VoiceModelManifest {

    /** Tên thư mục **bên trong** gói nén, cũng là tên thư mục sau khi giải nén. */
    const val ID = "vosk-model-small-vn-0.4"

    /** Thư mục con của `filesDir` chứa mô hình đã giải nén. */
    const val DIR = "vosk/$ID"

    /** Kích thước gói nén — byte, [ĐO] 2026-09-14. */
    const val ZIP_BYTES = 33_656_337L

    /** sha256 của gói nén — [ĐO] 2026-09-14 (`curl -L … && shasum -a 256`). */
    const val ZIP_SHA256 = "efe5c8494212110471a79befc48c79da679e5b1fc52a4ffb500222ff86d622e5"

    /** Tổng cỡ sau khi giải nén — byte, [ĐO] `unzip -l`. Dùng để nói trước cho người dùng cần bao nhiêu chỗ. */
    const val UNPACKED_BYTES = 53_290_365L

    /**
     * Nguồn tải, thử theo **thứ tự khai**.
     *
     * ⚠ URL TRẦN, không dựng từ mảnh: một hằng `BASE` + một hằng `NAME` ghép lại đọc lên không còn thấy được địa
     * chỉ thật, mà đây là dòng mà người soát bảo mật phải đọc bằng mắt trước khi duyệt (CLAUDE.md §6).
     *
     * Nguồn 2 là **bản sao của chính dự án** — cùng repo với kênh OTA (`dangkhoi/byd-kachi`). Nó chưa được tải
     * lên (xem TODO) nên hôm nay chỉ là đường lùi khi alphacephei chết; có nó trong danh sách thì ngày owner đăng
     * tệp lên là tự chạy, không phải sửa mã.
     *
     * TODO(owner): tải `vosk-model-small-vn-0.4.zip` lên GitHub Release `model-vn-0.4` của `dangkhoi/byd-kachi`
     * (tên tệp giữ NGUYÊN, sha256 phải khớp [ZIP_SHA256] — nếu không khớp thì đường lùi này tự bị từ chối, đúng ý).
     */
    val URLS: List<String> = listOf(
        "https://alphacephei.com/vosk/models/vosk-model-small-vn-0.4.zip",
        "https://github.com/dangkhoi/byd-kachi/releases/download/model-vn-0.4/vosk-model-small-vn-0.4.zip",
    )

    /**
     * Tệp PHẢI có sau khi giải nén — bản kê lấy từ `unzip -l` của chính gói đã băm.
     *
     * Kiểm cái này chứ không kiểm *"thư mục có tồn tại không"*: một lần giải nén bị giết giữa chừng (hệ thống thu
     * hồi tiến trình lúc đang ghi) để lại một thư mục **có thật mà thiếu ruột**, và Vosk sẽ ngã bằng `KALDI_ERR`
     * trong mã native — tức không bắt được bằng `try/catch` Kotlin ở một số ROM. Thà từ chối trước.
     */
    val REQUIRED_FILES: List<String> = listOf(
        "am/final.mdl",
        "conf/mfcc.conf",
        "conf/model.conf",
        "graph/Gr.fst",
        "graph/HCLr.fst",
        "graph/disambig_tid.int",
        "graph/phones/word_boundary.int",
        "ivector/final.dubm",
        "ivector/final.ie",
        "ivector/final.mat",
        "ivector/global_cmvn.stats",
        "ivector/online_cmvn.conf",
        "ivector/splice.conf",
    )

    /**
     * Tệp FST mang bảng ký hiệu nhúng — nguồn của từ điển (xem [VoskWordList]).
     *
     * ⚠ Đây là đường dẫn **kiểu V2** của Vosk (`model.cc:200-204`: `graph/HCLr.fst` · `graph/Gr.fst` ·
     * `graph/words.txt`). Mô hình kiểu V1 để chúng ở gốc — Kachi chưa gặp mô hình nào như vậy nên chưa hỗ trợ, và
     * `VoiceModelStore` nói thẳng ra điều đó thay vì đoán.
     */
    const val GRAPH_FST = "graph/Gr.fst"

    /**
     * Nơi Kachi **ghi lại** từ điển đã rút ra, để lần mở sau khỏi đọc lại 25 MB tệp FST.
     *
     * Cùng tên và cùng chỗ với tệp mà Vosk sẽ tự tìm nếu `Gr.fst` không nhúng bảng (`model.cc:204`) — tức nếu về
     * sau đổi sang một mô hình có sẵn `words.txt`, chỗ này đọc được luôn, không cần nhánh riêng.
     */
    const val WORDS_FILE = "graph/words.txt"

    /** Gói tải về có đúng là gói đã ghim không. Cả hai phép kiểm, xem KDoc lớp về vì sao không bỏ cái nào. */
    fun matches(sha256: String, bytes: Long): Boolean =
        bytes == ZIP_BYTES && sha256.equals(ZIP_SHA256, ignoreCase = true)

    /**
     * Đường dẫn TƯƠNG ĐỐI an toàn cho một mục trong gói nén, hoặc `null` nếu mục đó **không được phép** giải ra.
     *
     * ## Vì sao đây là phép kiểm bắt buộc, không phải cẩn thận thừa
     * CLAUDE.md §4.1: *"user input → file path → LUÔN kiểm, reject `..`"*. Tên mục trong một tệp zip tải từ mạng
     * **là dữ liệu của người khác**; một mục tên `../../../../data/data/com.byd.launcher/shared_prefs/x.xml`
     * sẽ ghi đè cấu hình của chính app (lỗ "zip slip", CVE-2018-1000544 và họ hàng). sha256 chặn được ca gói bị
     * đổi ở máy chủ, nhưng lớp này **cũng** phải đứng vững khi ai đó sau này thêm một URL thứ ba.
     *
     * Luật: bỏ tiền tố thư mục gốc [ID]; từ chối đường tuyệt đối, từ chối mọi đoạn `..` hoặc `.`, từ chối tên
     * rỗng, từ chối `\` (zip trên Windows) — nghĩa là chỉ nhận **một đường đi xuống** thuần tuý.
     */
    fun safeEntryPath(rawName: String): String? {
        if (rawName.isBlank()) return null
        if (rawName.startsWith('/') || rawName.contains('\\') || rawName.contains(':')) return null
        val stripped = rawName.removePrefix("$ID/")
        if (stripped.isBlank() || stripped.endsWith('/')) return null       // thư mục: tự tạo khi ghi tệp con
        val parts = stripped.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }
}
