package com.byd.clusternav.launcher.voice

/**
 * ═══ "Hey Kachi" · GÓI MODEL KEYWORD-SPOTTER — GHIM BẰNG SỐ ĐO THẬT CỦA CHÍNH 5 TỆP ĐÃ ĐẶT TRONG REPO ══════════
 *
 * Spec `docs/specs/kachi-wake-word.html`. Thuần Kotlin (`:core`) ⇒ luật ghim kiểm off-car; phần chạm đĩa/mạng
 * nằm ở `:app` (`VoiceModelStore`, qua hợp đồng [VoicePack]).
 *
 * ## Vì sao một danh mục THỨ BA, không nhồi vào hai cái đã có
 * Dự án đã có [SherpaModelCatalog] (mô hình **NGHE** cả câu — transducer *offline*) và [SherpaTtsCatalog] (gói
 * **ĐỌC** — VITS). Đây là họ thứ ba: **keyword-spotter streaming** (`KeywordSpotter`, xem `VoiceWakeKws`). Nó
 * dùng lại đúng hai thứ phải dùng chung — **luật ghim** ([SherpaModelCatalog.ModelFile]) và **hợp đồng cài**
 * ([VoicePack]) — nhưng có vòng đời riêng: tải khi người dùng **bật công tắc** "Hey Kachi", và KHÔNG được xuất
 * hiện trong danh sách chọn mô hình nghe (`SherpaModelCatalog.ALL`), vì chọn nó ở đó là chọn một mô hình không
 * giải mã được câu lệnh nào.
 *
 * ## ⚠ Thư mục là `kws` PHẲNG, không `kws/<id>` — và đó là một hợp đồng với `VoiceWakeKws`
 * `VoiceWakeListener.defaultKws` dựng engine từ `filesDir/kws/<tên tệp>`. Nếu [dir] đổi thành `kws/<id>` thì
 * gói tải về nằm ở một chỗ mà bộ nghe không bao giờ soi tới, và hỏng đó **im lặng**: KWS trả `null` ⇒ degrade
 * chỉ-RMS ⇒ "Hey Kachi" chạy mà không bao giờ nhận. Bài canh khoá cặp đường dẫn này.
 *
 * Hệ quả cần biết: [dir] **không có đoạn cha**, nên thư mục dựng dở của `VoiceModelStore` phải nằm NGOÀI nó —
 * xem KDoc `VoiceModelStore.staging` (đặt trong `kws/` thì bước đổi tên cuối tự xoá mất chính nó).
 *
 * ## Số ghim — [ĐO] 2026-09-19 trên chính 5 tệp trong `voice/kws/` của repo
 * `shasum -a 256` + `stat -f%z`. Tổng **5 253 782 byte ≈ 5,0 MB** ⇒ tải được qua 4G trong cabin, khác hẳn 74 MB
 * của mô hình NGHE. Đây là gói **int8** của `sherpa-onnx-kws-zipformer-gigaspeech-3.3M`.
 *
 * `keywords.txt` là câu gọi **ĐÃ TOKENIZE** theo `tokens.txt` của chính model này (nợ dữ liệu ghi ở KDoc
 * `VoiceWakeKws` — nay đã trả). Nó đi trong cùng gói vì hai tệp ấy chỉ đúng khi **cùng phiên bản**: ghép
 * `keywords.txt` của model khác vào là engine dựng xong rồi không bao giờ khớp.
 *
 * ## Giấy phép
 * Model KWS `sherpa-onnx-kws-zipformer-gigaspeech-3.3M` (k2-fsa) = **Apache-2.0**; engine sherpa-onnx (AAR) =
 * Apache-2.0. Không có gói NC/ND nào ở đây (dự án đã từ chối CC-BY-NC-ND một lần — xem KDoc
 * [SherpaModelCatalog]).
 */
object WakeModelCatalog : VoicePack {

    /** Thư mục con của `filesDir` — PHẲNG, khớp `VoiceWakeListener.defaultKws`. Xem KDoc lớp. */
    override val dir: String = "kws"

    override val id: String = "kws-gigaspeech-3.3M"

    override val label: String = "Hey Kachi — câu gọi (KWS 5 MB)"

    /**
     * Gốc URL asset — thư mục `voice/kws/` trong repo `dangkhoi/byd-kachi` qua raw URL, **cùng kênh** với
     * `apk/` của OTA và với gói giọng đọc. Tách hằng để 5 dòng dưới không lặp một chuỗi dài, và để đổi kênh chỉ
     * phải sửa một chỗ.
     */
    private const val RELEASE_BASE =
        "https://raw.githubusercontent.com/dangkhoi/byd-kachi/main/voice/kws/"

    /**
     * Một dòng của bảng ghim.
     *
     * ⚠ Tên tệp ở đây **phẳng** (`encoder.int8.onnx`), KHÔNG đổi `/` thành `__` như gói Piper từng phải làm cho
     * GitHub Release: gói này nằm trong cây thư mục của repo nên đường dẫn tương đối đi thẳng vào URL.
     */
    private fun pinned(name: String, bytes: Long, sha256: String) =
        SherpaModelCatalog.ModelFile(name, RELEASE_BASE + name, sha256, bytes)

    /** 5 tệp của gói — số [ĐO] 2026-09-19, xem KDoc lớp. */
    override val files: List<SherpaModelCatalog.ModelFile> = listOf(
        pinned("encoder.int8.onnx", 4_807_159L,
            "1e721676515bcd42a186979733981213c66c80db680e1cc582dfedf3be76e678"),
        pinned("decoder.int8.onnx", 277_985L,
            "e40ff43297abe815e8898494c17e71bba2152d9d40fa3eb803f75d0f7533329a"),
        pinned("joiner.int8.onnx", 163_380L,
            "eae9da0c7e1e6c6a3f4cc42d167899c388f6c6701b94cb96320e4f55df79624c"),
        pinned("tokens.txt", 5_006L,
            "fd2ded4050a55d2b1578870ba8697d02371980217806b7558bd0a5cc60f3ba53"),
        pinned("keywords.txt", 223L,
            "be924070d868d18d5ac279f95345e771a631bfacd37254783c2d40a745fe9d0f"),
    )

    /**
     * Tổng byte — ghim **tường minh** thay vì `files.sumOf` có chủ ý.
     *
     * Hai nguồn độc lập cho cùng một con số là cách duy nhất để một dòng ghim bị sửa sai (đổi `bytes` mà quên
     * `sha256`, hay dán lẫn hai tệp) làm **đỏ một bài canh** thay vì lặng lẽ tải về một gói khác. Bài canh so
     * hai vế; lệch là đỏ.
     */
    override val totalBytes: Long = 5_253_753L

    /**
     * Được phép tải qua mạng: cả 5 tệp ghim đủ sha256 + bytes.
     *
     * `true` ở đây **không** hứa máy chủ có tệp — phép so sha256 của `VoiceModelStore` bắt mọi thứ khác bản
     * ghim, và tải hỏng ⇒ "Hey Kachi" degrade **chỉ-RMS** (không crash, không spam) theo `VoiceWakeKws.build`.
     */
    override val downloadable: Boolean = files.isNotEmpty() && files.all { it.pinned }

    // ── Tên tệp mà `VoiceWakeKws` trỏ tới. Khai ở ĐÂY để bảng ghim và engine không thể lệch nhau. ──
    const val ENCODER = "encoder.int8.onnx"
    const val DECODER = "decoder.int8.onnx"
    const val JOINER = "joiner.int8.onnx"
    const val TOKENS = "tokens.txt"
    const val KEYWORDS = "keywords.txt"

    /** Tìm một tệp theo tên; `null` nếu bảng ghim không có (dùng cho bài canh cặp đường dẫn engine). */
    fun file(name: String): SherpaModelCatalog.ModelFile? = files.firstOrNull { it.name == name }
}
