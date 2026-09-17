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
}
