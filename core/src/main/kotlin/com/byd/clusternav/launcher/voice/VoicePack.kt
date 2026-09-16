package com.byd.clusternav.launcher.voice

/**
 * ═══ V2 pha 2 · MỘT HỢP ĐỒNG CÀI GÓI CHO CẢ ĐƯỜNG **NGHE** LẪN ĐƯỜNG **ĐỌC** ═════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **T8**. Thuần Kotlin (`:core`) ⇒ kiểm off-car; phần chạm đĩa/mạng
 * nằm ở `:app` (`VoiceModelStore`).
 *
 * ## Vì sao tách ra một interface thay vì để hai bảng tự lo
 * Tới 1.65 `VoiceModelStore` biết **đúng một** loại gói: mô hình NGHE ([SherpaModelCatalog.SherpaModel]). Gói
 * ĐỌC ([SherpaTtsCatalog.TtsVoice]) cần **cùng bốn việc** — tải từng tệp có ghim sha256, side-load từ USB, hỏi
 * *"đã lắp đủ chưa"*, gỡ — nhưng là một họ dữ liệu khác. Hai lựa chọn: chép `VoiceModelStore` thành bản thứ hai
 * cho TTS, hoặc rút ra cái mà hai bên **thật sự** dùng chung.
 *
 * Bản sao thứ hai là đúng họ lỗi mà dự án đã trả giá bốn lần (`unitPrefs` ×4, `customLayout` ×2): một bên vá
 * *"tải cụt thì xoá tệp"*, bên kia không, và cái lệch ấy **im lặng** cho tới khi một chiếc xe mất sóng giữa
 * chừng. Nên hợp đồng nằm ở đây, và cả hai bảng cùng thực thi nó.
 *
 * ## Tại sao chỉ có 6 thuộc tính
 * Đây là **tất cả** những gì tầng cài cần biết: gói tên gì ([id]/[label]), đặt ở thư mục con nào của `filesDir`
 * ([dir]), gồm những tệp nào ([files]), tổng bao nhiêu byte ([totalBytes]), và **có được phép tải tự động
 * không** ([downloadable]). Mọi thứ khác (tệp nào là encoder, tệp nào là `espeak-ng-data`) chỉ có nghĩa với
 * chính engine đọc/nghe, nên nó ở lại bảng riêng của từng họ.
 */
interface VoicePack {

    /** Nhãn ổn định, cũng là tên thư mục con dưới gốc của họ gói (`sherpa/<id>` · `sherpa-tts/<id>`). */
    val id: String

    /** Tên hiện trong Cài đặt. */
    val label: String

    /** Thư mục con của `filesDir` chứa gói sau khi đã lắp — `<gốc họ>/<id>`. */
    val dir: String

    /**
     * Các tệp thành phần. [SherpaModelCatalog.ModelFile.name] là **đường dẫn TƯƠNG ĐỐI** so với [dir] và có thể
     * nhiều đoạn (`espeak-ng-data/lang/aav/vi`) — xem KDoc ở đó về luật chống leo thư mục.
     */
    val files: List<SherpaModelCatalog.ModelFile>

    /** Tổng byte phải lấy về — nói trước cho người dùng cần bao nhiêu chỗ và bao nhiêu 4G. */
    val totalBytes: Long

    /**
     * Được phép tải **qua mạng** chưa: mọi tệp đã ghim đủ `sha256` + `bytes`.
     *
     * `false` KHÔNG có nghĩa là gói vô dụng — đường **side-load** (chép tệp qua USB/adb) vẫn chạy, vì nó dùng
     * chung phép kiểm ghim. Nó chỉ có nghĩa *"chưa có nguồn tải an toàn"*, và tầng cài phải nói ra điều đó thay
     * vì tải mù (fail-safe — CLAUDE.md §4.1).
     */
    val downloadable: Boolean
}
