package com.byd.clusternav.core

/**
 * ═══ 2.98 · R6-H — APK OTA (~45 MB) nằm lại `filesDir/update/` sau khi đã cài xong ════════════════════════════════
 *
 * [ĐO mã] `:app` `UpdateChecker.download` chỉ dọn thư mục ở lượt tải KẾ TIẾP; cài thành công thì tiến trình bị `pm`
 * giết giữa lời gọi ⇒ không mã nào chạy sau đó ⇒ bản đã cài nằm lại đến tận lần OTA sau.
 *
 * Luật quyết từng tệp (chạy một lần mỗi tiến trình, luồng nền — `:app` `housekeeping/UpdateApkHousekeeping`):
 *  • tệp vừa sửa < [FRESH_MS] trước ⇒ GIỮ (có thể đang được tải ghi vào);
 *  • đọc được APK, đúng gói mình, versionCode ≤ bản ĐANG CÀI ⇒ XOÁ (đã cài xong — hoặc đã có bản mới hơn);
 *  • đọc được APK, versionCode > bản đang cài ⇒ GIỮ (đã tải mà chưa cài / `pm` từ chối — câu báo lỗi trỏ người dùng
 *    tới đúng tệp này để cài tay, `UpdateChecker.installMessage`);
 *  • đọc được APK nhưng là GÓI KHÁC ⇒ GIỮ (không phải thứ luật này hiểu — không đoán);
 *  • KHÔNG đọc được (tải dở khi tắt máy: thiếu central directory của zip) và không còn tươi ⇒ XOÁ — không bao giờ
 *    cài được, và lượt tải sau cũng xoá nó;
 *  • không biết versionCode đang cài (`null`) ⇒ GIỮ tất cả (fail-safe: không bao giờ xoá trước khi biết đã cài).
 *
 * THUẦN ⇒ `UpdateApkSweepTest`.
 */
object UpdateApkSweep {

    /** Tệp sửa trong vòng 10 phút coi như có thể đang tải — không đụng. */
    const val FRESH_MS: Long = 10L * 60_000L

    enum class Verdict { KEEP, DELETE }

    /**
     * @param ageMs tuổi tệp (giờ tường hiện tại − lastModified); âm (đồng hồ lùi) ⇒ coi là tươi.
     * @param archivePackage gói đọc từ APK, `null` nếu không đọc được.
     * @param archiveVersionCode versionCode đọc từ APK (bỏ qua khi [archivePackage] `null`).
     * @param ownPackage gói của chính app.
     * @param installedVersionCode versionCode đang cài, `null` nếu không đọc được.
     */
    fun verdict(
        ageMs: Long,
        archivePackage: String?,
        archiveVersionCode: Long,
        ownPackage: String,
        installedVersionCode: Long?,
    ): Verdict {
        if (installedVersionCode == null) return Verdict.KEEP
        if (ageMs !in FRESH_MS..Long.MAX_VALUE) return Verdict.KEEP
        if (archivePackage == null) return Verdict.DELETE
        if (archivePackage != ownPackage) return Verdict.KEEP
        return if (archiveVersionCode <= installedVersionCode) Verdict.DELETE else Verdict.KEEP
    }
}
