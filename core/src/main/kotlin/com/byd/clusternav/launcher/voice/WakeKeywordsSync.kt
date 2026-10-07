package com.byd.clusternav.launcher.voice

/**
 * ═══ 2.96 R11 · WAKE-RECOPY-LOOP — khi nào phải xoá gói KWS trên đĩa và chép lại từ APK (thuần) ════════════════════════════
 *
 * ## Lỗi [ĐO log xe 07/10 + repo]
 * Mọi lần tiến trình launcher bật (= mọi lần nổ máy, BYD giết Kachi lúc tắt máy) đều in
 * `keywords.txt trên đĩa KHÁC bản ghim … xoá gói + tải lại` rồi chép lại 5 tệp (5 131 KB) và gọi
 * `VoiceWakeService.sync(reloadModel = true)` (dựng lại bộ nghe "Hey Kachi" ở `:wake`): 20:48:44.967→45.870 và
 * 21:04:47.193→47.553. Gốc: `keywordsMatchPin` so tệp trên đĩa với sha GHIM trong [WakeModelCatalog] (650 B, `d40dff2e…` =
 * `voice/kws/keywords.txt` của 2.05) nhưng tệp ĐÓNG TRONG APK (`app/src/main/assets/voice/kws/keywords.txt`) vẫn là bản cũ
 * 348 B (`286f7400…`) — 2.05 sửa bảng ghim + `voice/kws/` mà không chép sang `assets/`. Chép xong vẫn lệch ⇒ lần bật sau
 * lại xoá + chép + nạp lại, vô hạn.
 *
 * ## Luật
 * Từ 1.89 gói KWS đi THEO APK (`copyFromAssets`, 0 mạng) ⇒ sự thật cần khớp là **bản trong APK**, không phải bảng ghim:
 *  • đọc được bản APK ⇒ cần chép lại ⟺ đĩa thiếu hoặc khác từng byte bản APK (vẫn bắt đúng ca 09-21 "keywords.txt cũ sai
 *    vocab nằm lại trên đĩa sau khi nâng cấp APK");
 *  • không đọc được bản APK ⇒ giữ luật cũ (so ghim) — không bao giờ tệ hơn trước.
 * Không đổi nội dung từ khoá nào: bộ nghe tiếp tục chạy ĐÚNG bản đang chạy ngoài xe (bản APK). Việc chọn bản nào (348 B
 * hiện chạy hay 650 B owner duyệt 2.05) là quyết định của owner — ghi ở báo cáo R11, không tự chọn ở đây.
 */
object WakeKeywordsSync {

    /**
     * @param disk nội dung `keywords.txt` trên đĩa, `null` = không có / đọc hỏng.
     * @param shipped nội dung bản trong APK, `null` = không đọc được.
     * @param diskMatchesPin luật cũ (so sha ghim) — chỉ hỏi khi không đọc được bản APK.
     */
    fun needsRecopy(disk: ByteArray?, shipped: ByteArray?, diskMatchesPin: () -> Boolean): Boolean = when {
        shipped != null -> disk == null || !disk.contentEquals(shipped)
        else -> !diskMatchesPin()
    }
}
