package com.byd.clusternav.launcher.voice

/**
 * ═══ T6 (bản locator) · CHỖ ĐẶT GÓI CLIP "Giọng Kachi bé" TRÊN MÁY ═══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-clone.html` T6/T8. Thuần Kotlin (`:core`) — chỉ là **hằng địa chỉ**, không chạm
 * đĩa. [ClipSpeaker] (`:app`) đọc gói từ `filesDir/<DIR>/`.
 *
 * ## Vì sao đây KHÔNG phải một [VoicePack] đủ (chưa ghim 1 546 tệp)
 * [VoicePack.files] là **danh sách tĩnh** để tầng cài biết cần tải gì; gói này có **1 546** tệp (§4.4) và ghim
 * sha256 từng tệp bằng tay là bất khả (và sinh mã > trần 500 dòng). Nguồn ghim thật là `manifest.json` của chính
 * gói — nên đường **tải qua OTA** (đăng gói + sinh danh sách ghim từ manifest) là việc T9, tách khỏi lượt này.
 *
 * Ở lượt này gói **có mặt qua side-load / cây repo**; [ClipSpeaker] tự đọc `index.tsv`/`num.tsv` trong thư mục
 * để dựng [VoiceClipInventory], và **tự lo sự sẵn sàng** ([ClipSpeaker.available]) thay vì đi qua
 * `VoiceModelStore.isReady` — cái đó với `files` rỗng sẽ báo "sẵn sàng" cho một thư mục trống (cạm bẫy `all{}`
 * trên tập rỗng), sai đúng lúc nguy hiểm nhất.
 */
object KachiClipVoiceCatalog {

    /** Nhãn ổn định, cũng là tên thư mục con — trùng `manifest.json.id` của gói. */
    const val ID = "kachi-giong-be-v1"

    /** Tên hiện trong Cài đặt. */
    const val LABEL = "Giọng Kachi bé"

    /**
     * Thư mục con của `filesDir` chứa gói. Họ RIÊNG `clip-voice/` (tách khỏi `sherpa-tts/` của Piper): hai loại
     * gói khác hẳn nhau (một bên là mô hình VITS 61 MB, một bên là 1 546 clip AAC), gộp chung một gốc là mời một
     * lượt gỡ nhầm.
     */
    const val DIR = "clip-voice/$ID"

    /** Bảng tra tầng 1+3a+3b: `<chuỗi>\t<đường dẫn clip>\t<ms>`. */
    const val INDEX_FILE = "index.tsv"

    /** Bảng số 0–999: `<số>\t<num/N.aac>\t<ms>`. */
    const val NUM_FILE = "num.tsv"

    /**
     * ═══ WP9 · DẤU HIỆU "ĐÃ LẮP" của gói này — hai bảng tra, KHÔNG phải cái `.zip` ════════════════════════════
     *
     * [VoicePack.files] của gói archive chỉ có **một** thành viên là cái `.zip`, và `VoiceModelStore` **xoá** nó
     * sau khi bung (nếu không thì `renameTo` đưa cả tệp nén vào thư mục gói). Nên phép *"đã lắp đủ chưa"* mặc định
     * — *mọi tệp trong `files` còn trên đĩa* — sẽ trả **false vĩnh viễn** cho một gói đã lắp xong.
     *
     * Hậu quả nếu để nguyên: `install()` mất luôn đường thoát sớm `if (isReady) return Done`, mà việc đầu tiên nó
     * làm sau đó là `remove(pack)` ⇒ một chỗ gọi vô tình (hoặc một lượt bấm lại) **xoá gói đang chạy tốt** rồi tải
     * lại 10,8 MB qua 4G của xe; mất sóng giữa chừng là mất hẳn giọng bé.
     *
     * ⇒ Gói archive khai dấu hiệu riêng ở ĐÂY, và `VoiceModelStore.isReady` hỏi đúng bảng này. Một nguồn sự thật
     * cho cả ba chỗ hỏi (store · hàng Cài đặt · [ClipSpeaker]) — chép phép kiểm sang tầng UI là bản sao thứ hai của
     * một luật lưu bền, đúng bẫy dự án đã trả giá bốn lần.
     */
    val EXTRACTED_FILES: List<String> = listOf(INDEX_FILE, NUM_FILE)

    // ═══ WP9 (T9) · ĐƯỜNG TẢI OTA — MỘT ARCHIVE .zip ══════════════════════════════════════════════════════════
    //
    // Ghim sha256 từng-tệp cho 1 546 clip là bất khả (KDoc trên). Cách đúng theo spec R9.1: đóng CẢ gói thành
    // **một** `.zip` (java.util.zip có sẵn — không viết decompressor), ghim sha256 của CHÍNH cái zip, tải như một
    // [VoicePack] một-tệp, rồi [VoiceModelStore] giải nén vào `filesDir/<DIR>/`. Nguồn ghim là chính cái zip đã
    // dựng (`voice/tts/<id>.zip` trong repo, cùng kênh raw như OTA `apk/`), không phải manifest per-file.

    /** Tên tệp archive (cũng là tệp đặt trong staging trước khi bung). */
    const val ZIP_NAME = "$ID.zip"

    private const val ZIP_URL =
        "https://raw.githubusercontent.com/dangkhoi/byd-kachi/main/voice/tts/$ZIP_NAME"

    /** [ĐO 2026-09-21] `zip -X -0` gói 1 550 tệp (index/num/manifest + 1 546 clip AAC + notes). */
    private const val ZIP_BYTES = 10_849_310L
    private const val ZIP_SHA256 = "3a9390171afb4d3df2ccfe69f0739efb3f7f7cbfcd55a52c56a45ca4dce81ddd"

    /**
     * Gói clip dưới dạng [VoicePack] **một tệp .zip** — để [VoiceModelStore] tải + so sha256 + bung, dùng CHUNG
     * đường cài với mô hình NGHE/ĐỌC (không dựng đường tải thứ hai).
     *
     * `dir` trỏ đúng [DIR] để sau khi bung, [ClipSpeaker] đọc `index.tsv`/`num.tsv` ở chỗ cũ — side-load và OTA
     * cho ra **cùng một cây thư mục**.
     */
    val OTA_PACK: VoicePack = ClipVoicePack

    private object ClipVoicePack : VoicePack {
        override val id = ID
        override val label = LABEL
        override val dir = DIR
        override val files = listOf(
            SherpaModelCatalog.ModelFile(ZIP_NAME, ZIP_URL, ZIP_SHA256, ZIP_BYTES),
        )
        override val totalBytes = ZIP_BYTES
        override val downloadable = true

        /** Đánh dấu để [VoiceModelStore] bung `.zip` sau khi tải xong, thay vì để nguyên tệp nén. */
        val isArchive = true
    }

    /** `true` nếu [pack] là gói clip đóng gói .zip (cần bước giải nén). */
    fun isArchivePack(pack: VoicePack): Boolean = pack === ClipVoicePack
}
