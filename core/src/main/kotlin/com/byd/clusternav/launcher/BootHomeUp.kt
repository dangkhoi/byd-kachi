package com.byd.clusternav.launcher

import java.util.concurrent.atomic.AtomicInteger

/**
 * 2.96 · R18 — bước (4) của `KachiAutostart.runBoot` (`am start -n <màn chính>`) chỉ chạy khi CHƯA có màn chính nào đang
 * resumed trong tiến trình.
 *
 * [ĐO log xe 07/10 20:48:44] BOOT_COMPLETED tới ~24 s sau màn bật — lúc đó màn chính đã lên từ lâu (`home adopt` +2,7 s) mà
 * `runBoot` vẫn chạy `am start` qua kênh shell (20:48:44.40 → 44.90 ≈ 0,5 s) đúng lúc lượt mở chiếu cụm đang xếp hàng trên
 * CÙNG kênh. Màn đã resumed ⇒ lệnh đó không làm gì ngoài `onNewIntent` rỗng. Ca nó sinh ra để chữa (MY_PACKAGE_REPLACED:
 * trình cài giết tiến trình, không ai dựng lại màn) luôn là tiến trình MỚI ⇒ bộ đếm = 0 ⇒ vẫn chạy y như cũ.
 * Bộ đếm là sự thật TRONG tiến trình (màn của chính Kachi), không phải trạng thái hệ thống ngoài (CLAUDE.md §5).
 */
object BootHomeUp {
    fun needsStart(resumedHomes: Int): Boolean = resumedHomes <= 0
}

/**
 * Số `KachiHomeActivity` đang RESUMED trong tiến trình (đếm, không cờ: có lúc nhiều màn cùng sống — cùng lẽ bộ đếm của
 * `SlotLiveProbe`). `onResume` → [up], `onPause` → [down]. Chỉ dùng cho [BootHomeUp].
 */
object HomeResumed {
    private val n = AtomicInteger(0)
    fun up() { n.incrementAndGet() }
    fun down() { if (n.decrementAndGet() < 0) n.set(0) }
    fun count(): Int = n.get()
}
