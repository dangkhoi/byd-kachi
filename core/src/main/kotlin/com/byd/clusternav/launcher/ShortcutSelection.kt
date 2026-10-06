package com.byd.clusternav.launcher

/**
 * ═══ F1 — LỐI TẮT ỨNG DỤNG: phép sửa danh sách + hình học khối (thuần, `:core`) ════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R1.1 · R1.2 · R1.4 (U2/U3). Tầng UI (ngăn kéo chế độ
 * `PICK_SHORTCUTS`, trang Cài đặt, khối trên thanh nút, widget `w_apps`) chỉ gọi vào đây — không tự gấp danh sách, không
 * tự tính bề dài khối. Mọi kết quả đi qua [AppShortcutCodec.sanitize] ⇒ không gói trùng, không ô ngoài tầm, không quá
 * trần KỸ THUẬT [AppShortcutCodec.MAX] (2.92: không còn trần 8 của người dùng).
 */
object ShortcutSelection {

    /**
     * Kiểu mặc định của app MỚI chọn: **Toàn màn** — kiểu duy nhất không đụng cửa sổ nào khác và không cần kênh (R1.5),
     * nên chạm thử ngay sau khi chọn không bao giờ đá một app khỏi ô. Người dùng đổi sang *Ô n* / *Chạy ngầm* ở Cài đặt.
     */
    val DEFAULT_MODE: ShortcutMode = ShortcutMode.Full

    /**
     * Danh sách sau khi người dùng chốt [picked] ở ngăn kéo (cùng hình dạng `DockSelection.apply`): mục CŨ còn được chọn
     * giữ nguyên CHỖ và KIỂU; mục MỚI nối vào cuối theo thứ tự [picked] (thứ tự người dùng chạm) với [DEFAULT_MODE]; mục
     * bỏ tích rời danh sách. Quá trần kỹ thuật ⇒ cắt phần cuối ([AppShortcutCodec.sanitize]; bảng chọn nói ra trước khi
     * tới đó — `AppDrawer.toggleSelection`).
     */
    fun apply(current: List<AppShortcut>, picked: List<String>): List<AppShortcut> {
        val keep = current.filter { it.pkg in picked }
        val have = keep.mapTo(HashSet()) { it.pkg }
        val added = picked.filter { it !in have }.distinct().map { AppShortcut(it, DEFAULT_MODE) }
        return AppShortcutCodec.sanitize(keep + added)
    }

    /** Đổi kiểu mở của [pkg]. Gói không có trong danh sách ⇒ trả CHÍNH [items] (không ghi bền một lượt vô nghĩa). */
    fun setMode(items: List<AppShortcut>, pkg: String, mode: ShortcutMode): List<AppShortcut> {
        if (items.none { it.pkg == pkg && it.mode != mode }) return items
        return AppShortcutCodec.sanitize(items.map { if (it.pkg == pkg) it.copy(mode = mode) else it })
    }

    /** Dời [pkg] [delta] bậc — CÙNG phép [BarOrder.move] của hai thanh (không dời được ⇒ CHÍNH [items]). */
    fun move(items: List<AppShortcut>, pkg: String, delta: Int): List<AppShortcut> {
        val item = items.firstOrNull { it.pkg == pkg } ?: return items
        return BarOrder.move(items, item, delta)
    }

    /**
     * Chip *Ô n* bày cho một lối tắt ở Cài đặt (R1.4): Ô 1…Ô [slotCount] của bố cục ĐANG dùng, cộng ô đang chọn nếu nó
     * nằm ngoài bố cục (để người dùng THẤY lựa chọn hiện tại, không bị giấu đi). `second = false` ⇒ ô ngoài bố cục ⇒ chip
     * mờ + câu "bố cục hiện có k ô" (chạm lúc đó sẽ mở toàn màn — dòng 2 của bảng §4.4.3).
     */
    fun slotChips(slotCount: Int, mode: ShortcutMode): List<Pair<Int, Boolean>> {
        val n = slotCount.coerceIn(0, AppShortcutCodec.MAX_SLOT)
        val inLayout = (1..n).map { it to true }
        val chosen = (mode as? ShortcutMode.Slot)?.n?.takeIf { it > n }
        return if (chosen == null) inLayout else inLayout + (chosen to false)
    }
}

/**
 * Hình dạng (không đơn vị) của khối icon trên THANH NÚT (R1.2). Số dp là việc của tầng vẽ (`KachiBars.SHORTCUT_*` ở
 * `:app` — `SpacingScaleContractTest.core khong giu so dp`); ở đây chỉ còn phép ĐẾM: khối có mấy khe. Khối dài theo trục
 * thanh — thanh ngang thì rộng ra, thanh dọc thì cao ra — và không tự cuộn: tràn thì khung cuộn sẵn có của thanh
 * (`DockAreaLayout.scrollWrap`) cuộn (không cuộn lồng).
 *
 * Lưới widget `w_apps` KHÔNG còn ở đây: 2.87 R-SI1 thay số cột cố định `min(n, 4)` (`gridCols`, không biết khung to
 * hay nhỏ) bằng phép khớp theo khung thật [ShortcutGridFit].
 */
object ShortcutStrip {

    /**
     * Số khe của khối cho [n] app. Rỗng vẫn chiếm MỘT khe (ô "chọn lối tắt" — chạm ⇒ Cài đặt); không quá trần kỹ thuật
     * [AppShortcutCodec.MAX] (2.92: hết trần 8 — khối dài theo số app, tràn thì khung cuộn của thanh cuộn).
     */
    fun cells(n: Int): Int = n.coerceIn(1, AppShortcutCodec.MAX)
}

/** Kiểu cần kênh điều khiển cửa sổ (R1.5): *Ô n* và *Chạy ngầm*. *Toàn màn* mở bằng Intent, không cần kênh. */
val ShortcutMode.needsChannel: Boolean get() = this !is ShortcutMode.Full
