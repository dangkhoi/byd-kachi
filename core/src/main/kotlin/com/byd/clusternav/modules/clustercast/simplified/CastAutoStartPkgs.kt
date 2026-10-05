package com.byd.clusternav.modules.clustercast.simplified

/**
 * 2.89 · B2 VM-PREREQ-TRUTH — gói mà Kachi sẽ TỰ chiếu lên cụm khi nổ máy (thuần). Cùng luật với bộ tự chiếu duy nhất
 * `BubbleAutostart.dispatch` (`:app`): chỉ khi Cast BẬT (`FloatingBubbleService.castEnabledNow` dừng dịch vụ khi tắt);
 * `autostart_enabled` (toàn cụm) THẮNG `autostart_split_enabled` (chia đôi: trái rồi phải). Rỗng/khoảng trắng bị bỏ.
 * Dùng để đưa "app tự chiếu của hồ sơ" vào phạm vi điều kiện nền (`AppPrereqPlan.Role.AUTO_CAST`).
 */
object CastAutoStartPkgs {

    fun of(prefs: SimpleCastPrefs): List<String> {
        if (!prefs.castEnabled()) return emptyList()
        val pkgs = when {
            prefs.autoStartEnabled() -> listOf(prefs.autoStartPackage())
            prefs.autoStartSplitEnabled() -> listOf(prefs.autoStartLeftPackage(), prefs.autoStartRightPackage())
            else -> emptyList()
        }
        return pkgs.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
    }
}
