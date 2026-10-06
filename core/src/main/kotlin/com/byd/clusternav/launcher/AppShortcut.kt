package com.byd.clusternav.launcher

/**
 * ═══ F1 — LỐI TẮT ỨNG DỤNG: mô hình + mã hoá (thuần, `:core`) ═══════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.4.1 (C1). Owner 01/10: *"cho chọn ứng dụng hiện trên
 * shortcut, chỉ hiện icon. Với mỗi ứng dụng, cho chọn cách hiển thị: 1, đặt vào ô số n, 2, mở full màn hình"* +
 * *"thêm option mở chạy ngầm nữa"*.
 *
 * Một danh sách theo hồ sơ (khoá `app_shortcuts`, ghi/đọc ở tầng `:app` — C9), mỗi app một [ShortcutMode]; KHÔNG có
 * trần người dùng từ 2.92 (owner 06/10 *"không nên giới hạn 8 app … bao nhiêu kệ người ta thôi"*) — chỉ còn trần kỹ
 * thuật [MAX]. Danh sách đi theo bản chia sẻ hồ sơ ⇒ chuỗi mã hoá chỉ chứa tên gói + kiểu, không vị trí.
 */
data class AppShortcut(val pkg: String, val mode: ShortcutMode)

/** Kiểu mở của một lối tắt. */
sealed interface ShortcutMode {
    /** Đặt TẠM vào ô số [n] (1-based, như người dùng thấy) — không ghi bố cục hồ sơ (đính chính owner 01/10). */
    data class Slot(val n: Int) : ShortcutMode

    /** Mở toàn màn bằng Intent (không cần kênh). */
    object Full : ShortcutMode { override fun toString() = "Full" }

    /** Chạy phía sau màn nhà, không che màn nhà (R0.3). */
    object Background : ShortcutMode { override fun toString() = "Background" }
}

/**
 * Mã hoá danh sách lối tắt: `pkg|S2,pkg|F,pkg|B`.
 *
 * ## Dễ dãi khi ĐỌC (chuỗi đến từ đĩa / tệp hồ sơ nhập, có thể bị sửa tay)
 *  - gói không khớp [ShellAppLauncher.PKG] ⇒ bỏ mục (tên gói còn đi vào lệnh shell ở tầng sau — chuỗi lạ dừng ở đây);
 *  - kiểu lạ, hoặc `S<n>` với n ngoài 1…[MAX_SLOT] ⇒ [ShortcutMode.Full] (mở toàn màn là kiểu duy nhất không đụng
 *    cửa sổ nào khác, nên là chỗ lùi an toàn);
 *  - gói trùng ⇒ giữ lần đầu; quá trần kỹ thuật [MAX] ⇒ cắt (bên gọi biết nhờ [decodeReport]).
 *
 * ## Chặt khi GHI
 * Chỉ ghi mục hợp lệ. Chuỗi kết quả không bao giờ chứa tab hay xuống dòng (dấu ngăn của `ProfileTransfer`) vì mọi
 * ký tự đều thuộc [ShellAppLauncher.PKG] hoặc `|,SFB0-9`.
 */
object AppShortcutCodec {

    /**
     * Trần KỸ THUẬT số lối tắt — không phải trần người dùng (2.92, spec `kachi-292-shortcut-widget.html` R2.2; trần cũ 8
     * gỡ theo owner 06/10). Vì sao vẫn có một con số: chuỗi này còn đến từ TỆP hồ sơ nhập (≤ 1 MB —
     * `ProfileFiles.MAX_READ_BYTES` ⇒ tới ~170 000 mục `a.b|F`), mà mỗi mục là một icon dựng trên MÀN NHÀ ⇒ tệp hỏng/độc
     * không được treo HOME. 256 ≫ số app có màn khởi chạy (máy ảo `clusternav10`: 23 [ĐO 06/10]) ⇒ chọn bằng tay không
     * bao giờ chạm; bảng chọn vẫn dùng ĐÚNG trần này qua đường nói-ra (`AppDrawer.toggleSelection`), không chặn im lặng.
     * Khối thanh nút cuộn sẵn (`DockAreaLayout.scrollWrap`), widget `w_apps` cuộn khi icon chạm sàn ([ShortcutGridFit]).
     */
    const val MAX = 256

    /** Ô lớn nhất người dùng chọn được — bằng trần số ô [WorkspaceState.SLOT_CAP]. */
    const val MAX_SLOT = WorkspaceState.SLOT_CAP

    private const val ITEM = ','
    private const val FIELD = '|'

    /** Kết quả giải mã kèm số mục bị bỏ vì quá trần — để tầng gọi ghi một dòng log. */
    data class Decoded(val items: List<AppShortcut>, val truncated: Int)

    fun encode(items: List<AppShortcut>): String =
        sanitize(items).joinToString(ITEM.toString()) { "${it.pkg}$FIELD${modeCode(it.mode)}" }

    fun decode(raw: String?): List<AppShortcut> = decodeReport(raw).items

    fun decodeReport(raw: String?): Decoded {
        if (raw.isNullOrBlank()) return Decoded(emptyList(), 0)
        val parsed = raw.split(ITEM).mapNotNull { item ->
            val cut = item.indexOf(FIELD)
            val pkg = (if (cut < 0) item else item.substring(0, cut)).trim()
            if (!validPkg(pkg)) return@mapNotNull null
            AppShortcut(pkg, if (cut < 0) ShortcutMode.Full else modeOf(item.substring(cut + 1).trim()))
        }.distinctBy { it.pkg }
        return Decoded(parsed.take(MAX), (parsed.size - MAX).coerceAtLeast(0))
    }

    /** Danh sách hợp lệ để GHI: gói hợp lệ, không trùng, ô trong tầm, tối đa [MAX] (trần kỹ thuật). */
    fun sanitize(items: List<AppShortcut>): List<AppShortcut> =
        items.filter { validPkg(it.pkg) }
            .map { if (it.mode is ShortcutMode.Slot && it.mode.n !in 1..MAX_SLOT) it.copy(mode = ShortcutMode.Full) else it }
            .distinctBy { it.pkg }
            .take(MAX)

    fun validPkg(pkg: String): Boolean = pkg.isNotEmpty() && pkg.matches(ShellAppLauncher.PKG)

    /** Mã MỘT kiểu (`S2` · `F` · `B`) — cũng là mã chip kiểu mở ở trang Cài đặt (một bảng mã, không hai). */
    fun modeCode(m: ShortcutMode): String = when (m) {
        is ShortcutMode.Slot -> "S${m.n}"
        ShortcutMode.Full -> "F"
        ShortcutMode.Background -> "B"
    }

    /** Ngược [modeCode], dễ dãi: mã lạ / ô ngoài 1…[MAX_SLOT] ⇒ [ShortcutMode.Full]. */
    fun modeOf(code: String): ShortcutMode = when {
        code == "F" -> ShortcutMode.Full
        code == "B" -> ShortcutMode.Background
        code.startsWith("S") -> code.substring(1).toIntOrNull()
            ?.takeIf { it in 1..MAX_SLOT }?.let { ShortcutMode.Slot(it) } ?: ShortcutMode.Full
        else -> ShortcutMode.Full
    }
}
