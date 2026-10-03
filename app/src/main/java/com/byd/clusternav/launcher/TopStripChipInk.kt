package com.byd.clusternav.launcher

/**
 * ═══ MÀU CHỮ + ICON của một chip thanh trên theo SẮC THÁI — MỘT chỗ khai ═══════════════════════════════════════
 *
 * Hai chỗ đọc: [KachiTopStrip] (vẽ — chữ lẫn icon, `applyChipFace` tint icon bằng CHÍNH màu này) và `ChromeRoles`
 * (sàn đọc được của thanh trên khi nền mờ, spec `kachi-287-look-and-keys` R-OP3). Trước soát 2.87 bảng map nằm trong
 * `KachiTopStrip.refreshChips` nên bộ giải độ đục chỉ biết mut/mut2/ink ⇒ [ĐO bài quét] ACCENT_INK của chip BẬT tụt
 * 5.55 → 4.29:1 ở bảng sáng (chỉ được dải đầu màn che, mô hình không tính dải đó). Bảng màu chỉ ở `:app` — `:core`
 * chỉ nói SẮC THÁI ([ChipTone]).
 *
 * `when` VÉT CẠN, không `else` (`TopStripWiringContractTest`): sắc thái mới mà quên map là lỗi biên dịch, không phải
 * chip xám im lặng.
 */
internal fun chipInk(tone: ChipTone): String = when (tone) {
    ChipTone.ENERGY -> KachiTheme.GREEN
    // Trạng thái bật/tắt của datum boolean = MÀU, không phải chữ (owner 2026-09-21). Cả chữ lẫn icon đổi
    // màu vì `applyChipFace` tint icon bằng CHÍNH màu này — đó là thứ làm "icon sáng / icon mờ".
    //
    // Vì sao hai vai này: [KachiTheme.ACCENT_INK] là vai *"màu nhấn dùng làm CHỮ"* — [ĐO] `accent` thuần
    // (`#4c7dff`) làm chữ thì không đạt tương phản, nên bảng màu đã tách riêng vai này và cho nó đi qua
    // `ContrastGuard.fitInk`. [KachiTheme.MUT2] là vai chữ mờ nhất còn đạt sàn tương phản. Dùng lại hai
    // vai có sẵn thay vì thêm vai mới: "mờ" và "nhấn" đã được định nghĩa và đã được bài canh tương phản
    // đo ở CẢ HAI bảng (tối + sáng) — thêm vai mới là thêm hai hex phải tự chứng minh lại.
    ChipTone.ACTIVE -> KachiTheme.ACCENT_INK
    ChipTone.INACTIVE -> KachiTheme.MUT2
    ChipTone.NEUTRAL -> KachiTheme.INK2   // chip trung tính
}
