package com.byd.clusternav.core

/**
 * So phiên bản của kênh OTA (`apk/Kachi-<ver>-release.apk`) với bản đang cài — THUẦN, một nguồn cho `:app`
 * `UpdateChecker` (hộp *"Có bản mới"*) và [UpdateDot] (chấm *có bản mới* trên nút Cài đặt).
 *
 * 2.98 · OTA-UPDATE-DOT — chuyển nguyên văn từ `UpdateChecker` (2.89 · B4 OTA-SUFFIX-COMPARE) xuống `:core` để chấm và hộp
 * thoại quyết bằng CÙNG một luật (CLAUDE.md §4.1 DRY): hai bản sao của luật so sánh là đúng chỗ lần sau sẽ lệch nhau
 * (chấm đỏ mà bấm vào lại báo *"đang ở bản mới nhất"*). `UpdateChecker.cmp/offers/…` giữ chữ ký, chỉ chuyển tiếp về đây.
 */
object OtaVersion {

    /**
     * So sánh PHẦN SỐ của hai phiên bản ("0.56" / "1.2.3" / "2.89-thử1" → 2.89). >0 nếu a mới hơn b; đuôi sau phần số bị
     * bỏ qua (xét đuôi ở [offers]).
     *
     * [ĐO máy ảo 05/10] bản cũ `split('.')` + `toIntOrNull() ?: 0` đọc `"89-thử1"` thành 0 ⇒ 2.89-thử1 = 2.0 < 2.88 ⇒ mời HẠ
     * cấp. Nay mỗi khúc lấy SỐ ĐẦU ([numericPrefix]).
     */
    fun cmp(a: String, b: String): Int {
        val pa = numericPrefix(a)
        val pb = numericPrefix(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrElse(i) { 0 }) - (pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** Phần số đầu phiên bản: "2.89-thử1" → [2, 89] · "1.2.3" → [1, 2, 3] · không bắt đầu bằng số ("?") → rỗng (= 0). */
    fun numericPrefix(v: String): List<Int> =
        NUMERIC_PREFIX.find(v.trim())?.value?.split('.')?.map { it.toIntOrNull() ?: 0 } ?: emptyList()

    /** `true` nếu phiên bản có đuôi sau phần số — bản THỬ cài tay (vd `2.89-thử1`, CLAUDE.md §9), không phải bản kênh. */
    fun hasSuffix(v: String): Boolean {
        val t = v.trim()
        val head = NUMERIC_PREFIX.find(t)?.value ?: return false
        return t.length > head.length
    }

    /**
     * Kênh có bản [channel] ĐÁNG mời cài đè lên bản đang dùng [installed] không (kênh chỉ lộ TÊN tệp, không versionCode):
     *  • số kênh > số đang dùng ⇒ mời;
     *  • số BẰNG nhau ⇒ chỉ mời khi đang dùng bản THỬ có đuôi mà kênh là bản chính thức cùng số;
     *  • số kênh < số đang dùng ⇒ KHÔNG BAO GIỜ mời.
     * Đang dùng đọc không ra (`"?"`) ⇒ phần số rỗng ⇒ kênh nào cũng "mới hơn" — hành vi cũ của hộp thoại, giữ nguyên.
     */
    fun offers(channel: String, installed: String): Boolean {
        val d = cmp(channel, installed)
        return d > 0 || (d == 0 && hasSuffix(installed) && !hasSuffix(channel))
    }

    /** `true` nếu [v] có phần số đọc được (không phải `"?"`/rỗng). */
    fun readable(v: String): Boolean = numericPrefix(v).isNotEmpty()

    /** Phần số đầu chuỗi phiên bản (một hay nhiều khúc số cách bằng dấu chấm). */
    private val NUMERIC_PREFIX = Regex("""^[0-9]+(?:\.[0-9]+)*""")
}
