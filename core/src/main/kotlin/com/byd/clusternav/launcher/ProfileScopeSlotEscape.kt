package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.escape.EscapeReturnSwitch

/**
 * Khoá THEO XE của họ "app thoát ô / cửa sổ của Kachi trên màn xe" → lý do. Tách khỏi [ProfileScope.DEVICE_KEYS] vì trần 500
 * dòng (CLAUDE.md §4.1 — `ProfileScope.kt` đã đúng 500 dòng khi 2.98 thêm công tắc R18); cùng khuôn [ProfileScopeCluster].
 *
 * ⚠ Không đọc [ProfileScope] ở đây (vòng khởi tạo `object` cho `null` giữa chừng — bài học `TopStripConfig.BUILT_IN`).
 */
internal object ProfileScopeSlotEscape {

    val DEVICE_KEYS: Map<String, String> = buildMap {
        put(
            "kachi_floating_opened",
            "PROFILE-SWITCH-SLOTS R-B4 — dấu 'Kachi đã mở gói này thành cửa sổ nổi' ([FloatingWindowLedger]). Theo XE: " +
                "cửa sổ nằm trên màn của CHIẾC XE này; theo hồ sơ thì đổi hồ sơ — đúng lúc cần dọn — là mất dấu. Cũng " +
                "khai ở [SettingsCatalog.NOT_SETTINGS]",
        )
        put("kachi_slot_escape", "2.98 R7 (SUPERSEDED) — dấu freeform cũ, chỉ đọc để trả rồi xoá (`LegacyFreeformUndo`); theo XE")
        put("kachi_escape_return_trip", "2.98 R18 — cầu chì bền (`EscapeReturnBreaker`); theo XE: lỗi của ROM xe này")
        put(
            EscapeReturnSwitch.PREF_KEY,
            "2.98 R18 — công tắc 'Kéo app thoát ô về lại ô (thử nghiệm)', mặc định TẮT. Theo XE: nó bật một cơ chế dời stack " +
                "của daemon trên ROM của CHIẾC XE này (đo được hay chưa là chuyện của xe, không phải của người lái), và đi cùng " +
                "phạm vi với cầu chì bền `kachi_escape_return_trip` ngay trên — đổi hồ sơ không được bật lại thứ xe này vừa ngắt",
        )
        put(
            "kachi_behind_marks",
            "BEHIND-HOME — dấu 'task này do Kachi đẩy ra sau màn nhà' (`BehindMarks`). Theo XE: task nằm trên màn của " +
                "CHIẾC XE này và sống qua lần BYD giết Kachi; theo hồ sơ thì đổi hồ sơ là mất dấu. Cũng khai ở " +
                "[SettingsCatalog.NOT_SETTINGS]",
        )
    }
}
