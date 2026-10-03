package com.byd.clusternav.navigation

/**
 * ═══ FIX286 R-HUD (S4) — lần GHI HAL dẫn đường gần nhất (đọc cho dòng tình trạng *Cài đặt › Dẫn đường*) ══════════════
 *
 * Chỉ `NavigationHudOwner` (`:app`) gọi [note], trên luồng giao khung của nó — chủ sở hữu duy nhất của lệnh ghi khung HAL
 * (`PhysicalHudOwnershipTest`). Trạng thái RAM của tiến trình — chỉ để HIỂN THỊ (CLAUDE.md §5: cờ chỉ để hiển thị), không
 * quyết định gì. Giờ TƯỜNG vì người đọc là anh em nhìn đồng hồ xe khi chụp màn. Thuần (không Android) ⇒ ở `:core`
 * (`LayeringRulesTest`).
 */
object HudWriteRecord {

    enum class Kind { GUIDE, KEEPALIVE, CLEAR, MODE_OFF }

    data class Write(val wallMs: Long, val kind: Kind, val rc: String)

    @Volatile var last: Write? = null
        private set

    fun note(kind: Kind, rc: String) {
        last = Write(System.currentTimeMillis(), kind, rc)
    }
}
