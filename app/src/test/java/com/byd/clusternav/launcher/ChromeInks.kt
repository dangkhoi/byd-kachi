package com.byd.clusternav.launcher

/**
 * ═══ 2.87 · R-OP3 (soát P2/P3) — DANH SÁCH KHAI các màu chữ CÓ MÀU vẽ lên nền mờ của màn chính ═══════════════════
 *
 * Bài quét độ đục (`ChromeOpacityContrastContractTest`) phải đo những màu chữ mà màn chính THẬT SỰ vẽ — không chép bộ
 * mực của bộ giải (`ChromeRoles`, `KachiGlass.veilInks`), vì chép thì quên một màu ở bộ giải là quên luôn ở bài đo
 * (đúng lỗ `w_board` ACCENT 4.5 → 3.86:1). Danh sách sống ở ĐÂY (phía test) và bài canh nguồn
 * `KachiChromeContractTest.the o nen khai mau chu…` đòi mọi `KachiTheme.X` mà chỗ dựng truyền vào phải nằm trong nó —
 * thêm màu mới ở chỗ dựng mà quên ở đây ⇒ đỏ.
 *
 * Lúc chạy, mỗi thẻ tự khai đúng màu nó vẽ (`MiniCard`/`BoardCell` → `KachiGlass.apply(extraInks)` + `addInk`).
 */
internal object ChromeInks {

    /** Màu chữ ngoài bộ trung tính (mut/mut2/ink) mà ô nén/ô bảng vẽ lên thẻ kính (`miniCard`/`BoardCell`/`MiniValue`). */
    val CARD: Map<String, (KachiPalette) -> String> = mapOf(
        "GREEN" to { p -> p.green }, "CYAN" to { p -> p.cyan }, "RED" to { p -> p.red },
        "AMBER" to { p -> p.amber }, "ACCENT" to { p -> p.accent },
    )

    /**
     * Phần của [CARD] mà widget to vẽ THẲNG lên khay ô (không qua thẻ có khai mực) — hiện chỉ dòng cửa của widget xe
     * (`WidgetViews.carState`: xanh/hổ phách). Khay KHÔNG khai mực (khai thì mọi khay đục thêm vì màu nó không vẽ) ⇒ bài
     * quét đo đúng các màu này trên khay với quyết định hiện có.
     */
    val WELL: Set<String> = setOf("GREEN", "AMBER")
}
