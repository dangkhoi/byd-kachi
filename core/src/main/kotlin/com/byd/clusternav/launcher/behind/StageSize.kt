package com.byd.clusternav.launcher.behind

/**
 * ═══ Soát vòng 3 [P3] — ĐỌC dòng `KachiBehind stage create vd=… WxH@dpi phys=PxQ rot=R so=…` cho đúng (🚗 DL5) ═══════════════
 *
 * Câu cần trả lời trên xe: cỡ màn ảo ẩn (`getRealMetrics` của display 0 — A12 có thể trả `maxBounds` của cấu hình tiến trình,
 * tức cỡ CỤM khi đang chiếu, KDoc `StagingDisplay.create`) có đúng là cỡ display 0 không.
 *
 * Luật cũ *"WxH ≠ PxQ ⇒ phải sửa"* báo động GIẢ ở hai ca có nguồn [ĐO nguồn]:
 *  - màn XOAY: cỡ logic hoán W/H khi rotation 90/270 — DL5: android-12.0.0_r34
 *    `services/core/java/com/android/server/wm/DisplayContent.java:1953-1971` (`updateDisplayAndOrientation`: `rotation =
 *    getRotation()` :1955, `dw = rotated ? mBaseDisplayHeight : mBaseDisplayWidth` :1957-1958, `logicalWidth = dw` :1970-1971);
 *    cùng luật ở android-10.0.0_r47 `DisplayContent.java:1591-1606` (đọc `mRotation`). Còn `Display.Mode` là chế độ tấm nền,
 *    KHÔNG xoay (android-12.0.0_r34 `Display.java:1750-1761` `getPhysicalWidth` = `mWidth` của mode);
 *  - `wm size` / OEM co giãn UI: cỡ logic = cỡ ép (`mBaseDisplayWidth`), mode vẫn là tấm nền — chính tài liệu `getPhysicalWidth`
 *    nói số pixel app nhận "may differ from the mode's actual resolution".
 *
 * ⇒ [verdict] chỉ phân loại; KẾT LUẬN theo bảng: [Verdict.SAME]/[Verdict.ROTATED] = khớp display 0 (không phải bằng chứng).
 * [Verdict.DIFFERENT] = CHƯA phải bằng chứng: so tiếp với `wm size` (dòng *Override size*) và cỡ cụm của đời xe — chỉ một độ
 * lệch KHÔNG giải thích được bằng override mà TRÙNG cỡ cụm mới chứng minh cỡ đi theo `maxBounds` (CLAUDE.md §2: cơ chế ≠ quy kết).
 */
object StageSize {

    enum class Verdict(val tag: String) {
        /** Cỡ logic = mode (không xoay). */
        SAME("khớp"),

        /** Cỡ logic = mode hoán W/H — display 0 đang xoay 90/270. */
        ROTATED("xoay"),

        /** Khác cả hai chiều — wm size / co giãn UI / hoặc maxBounds (cỡ cụm): phải đối chiếu thêm, xem KDoc lớp. */
        DIFFERENT("lệch-đối-chiếu-wm-size-và-cỡ-cụm"),

        /** Không đọc được mode. */
        UNKNOWN("?"),
    }

    fun verdict(width: Int, height: Int, physWidth: Int?, physHeight: Int?): Verdict = when {
        physWidth == null || physHeight == null || physWidth <= 0 || physHeight <= 0 -> Verdict.UNKNOWN
        width == physWidth && height == physHeight -> Verdict.SAME
        width == physHeight && height == physWidth -> Verdict.ROTATED
        else -> Verdict.DIFFERENT
    }
}
