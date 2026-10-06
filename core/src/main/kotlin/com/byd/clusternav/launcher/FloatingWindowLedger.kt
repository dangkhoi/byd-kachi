package com.byd.clusternav.launcher

/**
 * ═══ PROFILE-SWITCH-SLOTS · R-B2/R-B4 — DẤU BỀN "Kachi đã mở gói này thành CỬA SỔ NỔI trên màn chính" ═══════════
 *
 * Thuần Kotlin (`:core`, không `android.*`) ⇒ test off-device. Spec `docs/specs/kachi-profile-switch-slots.html` §4.2.
 *
 * ## Bệnh nó chữa
 * [ĐO máy ảo 2026-10-01, fixture `am-stack-list-emulator-2026-10-01-noshell-A-back.txt`] chưa có kênh shell thì
 * Kachi (bản ≤ 2.92) mở app khách thành cửa sổ nổi (`IntentAppLauncher.openInSlot`) nhưng KHÔNG đóng được nó khi gỡ
 * khỏi ô (`closeSlot` rỗng). 2.93 · READY-AT-HOME-OQ6 gỡ hẳn đường mở nổi ấy; 2.93 wave 2C · SLOT-DEAD-FREEFORM-REST gỡ
 * luôn lượt GHI `markOpened` (+ hàm phụ `add`) — [ĐO grep 07/10] 0 chỗ gọi sản phẩm (main · vehicleTest). Sổ nay CHỈ ĐỌC +
 * QUÊN: để DỌN cửa sổ nổi do bản cũ để lại sau nâng cấp ([FloatingOrphanSweep]). Kênh lên sau đó thì
 * `am stack remove <id>` đóng được ([FloatingOrphanPlan]) — nhưng phải biết
 * cửa sổ nổi NÀO là của Kachi. Không được đoán "mọi cửa sổ nổi trừ…": cửa sổ nổi do người dùng tự mở (hoặc của
 * app khác) không phải việc của ta (CLAUDE.md §4 — allow-list, không phải "mọi thứ trừ…").
 *
 * ## Vì sao là dấu BỀN (CLAUDE.md §5) — bản ≤ 2.92 ghi TRƯỚC khi mở
 * Cửa sổ nổi là state ngoài hệ thống, sống dai hơn tiến trình Kachi. Cờ RAM chết theo tiến trình ⇒ lần sau không ai
 * biết cửa sổ đó do ai mở. Bản ≤ 2.92 ghi đồng bộ (`commit()` ở [Store]) TRƯỚC lệnh mở ⇒ tiến trình chết ngay sau lệnh
 * mở thì dấu vẫn còn; dấu ấy sống qua nâng cấp, nên bản 2.93+ vẫn đọc được để dọn rồi [forget] dần.
 *
 * ## Theo XE, không theo hồ sơ
 * Cửa sổ nổi nằm trên màn của CHIẾC XE này. Dấu theo hồ sơ thì đổi hồ sơ — đúng lúc cần dọn — là mất dấu. Không đi
 * qua xuất/nhập hồ sơ (`ProfileScope` DEVICE, `SettingsCatalog.NOT_SETTINGS`).
 *
 * ## Định dạng
 * Một chuỗi, các gói ngăn bằng [SEP]. Tên gói hợp lệ ([ShellAppLauncher.PKG]) không bao giờ chứa dấu phẩy ⇒ không
 * cần thoát ký tự. Đọc vào luôn qua [decode]: tên sai bị bỏ, trùng bị gộp, tối đa [CAP] gói mới nhất — một tệp bị
 * sửa tay hay hỏng không làm phình hay làm sai kế hoạch.
 */
class FloatingWindowLedger(private val store: Store) {

    /** Cổng lưu bền — đọc/ghi ĐỒNG BỘ (bên :app là SharedPreferences `commit()`). */
    interface Store {
        fun read(): String?
        /** Ghi xong mới trả về; `false` = không ghi được (đĩa đầy…). */
        fun write(value: String): Boolean
    }

    /** Các gói trong dấu, cũ trước mới sau. */
    fun opened(): List<String> = synchronized(LOCK) { decode(store.read()) }

    /** Bỏ [pkgs] khỏi dấu. Không có gì để bỏ ⇒ không ghi. */
    fun forget(pkgs: Set<String>) {
        if (pkgs.isEmpty()) return
        synchronized(LOCK) {
            val cur = decode(store.read())
            val next = cur.filterNot { it in pkgs }
            if (next.size != cur.size) store.write(encode(next))
        }
    }

    companion object {
        /**
         * Khoá TOÀN TIẾN TRÌNH cho đọc-sửa-ghi: dấu là MỘT tệp của cả xe, mà màn nhà dựng lại (đổi ngôn ngữ ⇒
         * `recreate()`) thì có lúc hai bộ dấu cùng sống. Khoá theo đối tượng sẽ để hai bộ đó ghi đè nhau.
         */
        private val LOCK = Any()

        /** Trần số gói trong dấu (R-B4). Một màn nhà có tối đa [WorkspaceState.SLOT_CAP] ô; 16 là đủ rộng cho đổi hồ sơ. */
        const val CAP = 16

        const val SEP = ","

        fun isValid(pkg: String): Boolean = pkg.matches(ShellAppLauncher.PKG)

        /** Chuỗi lưu bền → danh sách sạch: bỏ tên sai, gộp trùng (giữ lần cuối), tối đa [CAP] gói mới nhất. */
        fun decode(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            val valid = raw.split(SEP).map { it.trim() }.filter { isValid(it) }
            return valid.asReversed().distinct().asReversed().takeLast(CAP)
        }

        fun encode(pkgs: List<String>): String = pkgs.joinToString(SEP)
    }
}
