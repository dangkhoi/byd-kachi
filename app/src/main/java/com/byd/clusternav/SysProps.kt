package com.byd.clusternav

/**
 * ═══ `getprop <key>` TRONG tiến trình — cửa DUY NHẤT của reflection vào lớp hidden `SystemProperties` (gói `android.os`) ═══
 *
 * L6-debt 2026-09-27 (CLAUDE.md §4.1 DRY): ba bản sao `private` của đúng phép reflection này từng sống ở
 * `AvmCamera.systemProp` · `SeatComfortApplier.systemProp` · `ClusterProfile.getProp` (nợ ghi ở KDoc `AvmCamera` 2.75).
 * Gộp về một chỗ; ba nơi cũ giữ nguyên tên hàm của chúng và uỷ quyền xuống đây. `SysPropsContractTest` khoá: đúng MỘT
 * chuỗi tên lớp đầy đủ (`android.os` + `SystemProperties`) trong `app/src/main` + `core/src/main` — chính là dòng `Class.forName` dưới.
 *
 * Hành vi KHÔNG đổi so với ba bản sao: lớp hidden nhưng đọc được không cần root; mọi lỗi (off-car, ROM cắt lớp, khoá
 * không có) ⇒ `""` — caller tự quyết `""` nghĩa là gì (`SeatComfortApplier` coi rỗng là *không có manh mối*).
 *
 * Ở `:app` chứ không `:core` vì `:core` bị cấm nhắc `android` (LayeringRulesTest `core khong duoc biet Android`) — kể cả
 * trong một chuỗi tên lớp.
 */
object SysProps {
    /** Giá trị của [key], `""` khi lỗi/off-car (không ném). */
    fun get(key: String): String = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        (c.getMethod("get", String::class.java).invoke(null, key) as? String).orEmpty()
    }.getOrDefault("")
}
