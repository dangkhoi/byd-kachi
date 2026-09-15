package com.byd.clusternav.launcher.voice

/**
 * ═══ V1 pha NÓI · GÓI GIỌNG ĐỌC sherpa-onnx — GHIM BẰNG SỐ ĐO THẬT, KHÔNG BỊA ═════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R2b**. Thuần Kotlin (`:core`) ⇒ luật ghim kiểm off-car; phần
 * chạm đĩa/mạng nằm ở `:app`.
 *
 * ## Vì sao một danh mục RIÊNG, không thêm vào [SherpaModelCatalog]
 * [SherpaModelCatalog] mô tả mô hình **NGHE** (transducer: encoder/decoder/joiner + tokens), và `ALL` của nó là
 * danh sách hiện trong *Cài đặt › Mô hình nhận dạng*. Gói **ĐỌC** là một họ khác hẳn (VITS: một tệp `.onnx` +
 * tokens + một **thư mục** dữ liệu ngôn ngữ), và nó không được phép rơi vào danh sách chọn mô hình nghe. Hai
 * danh mục, hai vòng đời, cùng một **luật ghim** ([SherpaModelCatalog.ModelFile]) — luật mới là thứ phải dùng
 * chung, không phải cái bảng.
 *
 * ## ⚠ VÌ SAO HÔM NAY CHƯA TẢI ĐƯỢC — và đó là một sự thật ĐO được, không phải một hạng mục bỏ quên
 * [ĐO] 2026-09-15 (tải thật + `tar -tjf` + `shasum -a 256`): gói Piper vi_VN phát hành **chỉ** dưới dạng một
 * `tar.bz2` **397 mục**, trong đó **393 mục là `espeak-ng-data/`** (18 MB) — bắt buộc, vì Piper chuyển chữ thành
 * âm vị bằng espeak-ng (`vi_VN-vais1000-medium.onnx.json` khai `"espeak": {"voice": "vi"}`). Không có URL cho
 * từng tệp rời, nên đường tải **từng tệp có ghim sha256** của `VoiceModelStore` không lắp vào được.
 *
 * ⇒ [PIPER_VI_VAIS1000] ghim **cả gói nén** ([archiveSha256] + [archiveBytes], số ĐO thật) và khai
 * [needsArchiveExtract] = `true`. `downloadable` = **false** cho tới khi tầng tải biết giải nén `.tar.bz2` —
 * đúng nếp fail-safe mà [SherpaModelCatalog.HATAPHU_VI] đã dựng: **chưa ghim/chưa lắp được thì KHÔNG tải**,
 * không có đường im lặng nào tải về một thứ chưa kiểm.
 *
 * ## Giấy phép (đọc `MODEL_CARD` thật trong gói đã tải, 2026-09-15 — rule global §1.1)
 *  • Dữ liệu VAIS-1000 = **CC-BY-4.0** ([ĐO] `MODEL_CARD`) ⇒ dùng được, **phải ghi công** ([attribution]).
 *  • Engine sherpa-onnx (AAR) = Apache-2.0.
 *  • Hai gói vi_VN còn lại trên cùng bản phát hành (`25hours_single-low`, `vivos-x_low`) cũng là Piper/espeak
 *    ⇒ cùng ràng buộc gói nén; chưa tải nên **[CHƯA BIẾT]** sha, không khai ở đây.
 */
object SherpaTtsCatalog {

    /** Gốc thư mục chứa gói giọng đọc — song song `sherpa/` của [SherpaModelCatalog.DIR_ROOT]. */
    const val DIR_ROOT = "sherpa-tts"

    /**
     * Một gói giọng đọc VITS (họ Piper).
     *
     * @param id nhãn ổn định, cũng là tên thư mục con của `filesDir/sherpa-tts/<id>`.
     * @param archiveUrl URL gói nén (HTTPS, trần).
     * @param archiveSha256 sha256 chữ thường của **gói nén**; `""` = chưa ghim ⇒ cấm tải.
     * @param archiveBytes kích thước gói nén; `0` = chưa ghim.
     * @param rootInArchive thư mục gốc bên trong gói nén (mọi tệp nằm dưới nó).
     * @param model tệp `.onnx` (đường dẫn TƯƠNG ĐỐI so với thư mục mô hình sau khi giải nén).
     * @param tokens bảng token.
     * @param dataDir thư mục dữ liệu espeak-ng — **bắt buộc** với họ Piper, xem KDoc lớp.
     * @param sampleRate tần số lấy mẫu giọng ([ĐO] `MODEL_CARD` + `onnx.json`) — `AudioTrack` cần biết trước.
     * @param needsArchiveExtract gói chỉ phát hành dạng nén ⇒ tầng tải phải giải nén, chưa có ở 1.63.
     * @param attribution câu ghi công bắt buộc theo giấy phép; hiện ở màn CREDITS.
     */
    @Suppress("LongParameterList")
    data class TtsVoice(
        val id: String,
        val label: String,
        val license: String,
        val archiveUrl: String,
        val archiveSha256: String,
        val archiveBytes: Long,
        val rootInArchive: String,
        val model: String,
        val tokens: String,
        val dataDir: String,
        val sampleRate: Int,
        val needsArchiveExtract: Boolean,
        val attribution: String,
    ) {
        /** Thư mục con của `filesDir` chứa gói này sau khi đã lắp. */
        val dir: String get() = "$DIR_ROOT/$id"

        /** Gói đã ghim đủ để tải an toàn chưa (cả sha256 lẫn kích thước) — cùng luật với `ModelFile.pinned`. */
        val pinned: Boolean get() = archiveSha256.isNotBlank() && archiveBytes > 0L

        /**
         * Được phép tải **tự động** chưa.
         *
         * Ghim đủ vẫn chưa đủ: gói còn cần một tầng giải nén `.tar.bz2` mà 1.63 chưa có. Trả `false` ở đây là để
         * lối tải không bao giờ được mở nửa vời — người dùng bấm *Tải* rồi nhận một thư mục thiếu `espeak-ng-data`
         * sẽ thấy máy đọc **im lặng không báo lỗi**, đúng loại hỏng khó lần nhất.
         */
        val downloadable: Boolean get() = pinned && !needsArchiveExtract
    }

    /**
     * Giọng Việt duy nhất đã **tải thật + băm thật** (2026-09-15).
     *
     * Số ghim [ĐO] bằng `curl` + `shasum -a 256` trên máy soạn thảo; `tar -tjf` cho 397 mục. Tệp `.onnx` bên
     * trong: `df1512ef3265609f147ae23726b8c8867c6d28e60acb9ffca3545e11783b809f`, 63 149 198 byte —
     * ghi ở đây dưới dạng chú thích chứ chưa thành trường riêng, vì tầng lắp gói (pha 2) mới là chỗ dùng tới nó.
     */
    val PIPER_VI_VAIS1000 = TtsVoice(
        id = "piper-vi_VN-vais1000-medium",
        label = "Piper VN — VAIS-1000 (medium)",
        license = "CC-BY-4.0",
        archiveUrl =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
                "vits-piper-vi_VN-vais1000-medium.tar.bz2",
        archiveSha256 = "fa1367710767d36ed5cf13b4a449e20c35ffd12791c2e47c2e64142bfa55551a",
        archiveBytes = 67_154_040L,
        rootInArchive = "vits-piper-vi_VN-vais1000-medium",
        model = "vi_VN-vais1000-medium.onnx",
        tokens = "tokens.txt",
        dataDir = "espeak-ng-data",
        sampleRate = 22_050,
        needsArchiveExtract = true,
        attribution = "VAIS-1000 Vietnamese speech synthesis corpus (CC-BY-4.0) · Piper · sherpa-onnx",
    )

    /** Mọi gói giọng đọc, thứ tự hiện trong Cài đặt (pha 2). */
    val ALL: List<TtsVoice> = listOf(PIPER_VI_VAIS1000)

    /** Gói mặc định cho tiếng Việt. */
    const val DEFAULT_ID = "piper-vi_VN-vais1000-medium"

    /** Tra theo id; id lạ/rỗng ⇒ gói mặc định (fail-safe cho pref cũ) — cùng nếp [SherpaModelCatalog.byId]. */
    fun byId(id: String?): TtsVoice = ALL.firstOrNull { it.id == id } ?: PIPER_VI_VAIS1000

    /**
     * Tốc độ đọc mặc định cho `OfflineTts.generate(text, sid, speed)`.
     *
     * `1.0` = tốc độ gốc của gói. Chưa có phép đo trên xe nên **không** tự ý đẩy nhanh: một câu đọc nhanh trong
     * phòng yên là một câu không nghe ra trong ca-bin 80 km/h. Số này lên/xuống sau phép đo V-oncar (spec §6).
     */
    const val DEFAULT_SPEED = 1.0f

    /** Số luồng cho phiên suy diễn ONNX — cùng số với đường NGHE, ca-bin chỉ có một việc chạy tại một lúc. */
    const val NUM_THREADS = 2
}
