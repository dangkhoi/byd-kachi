package com.byd.clusternav.launcher

/**
 * ═══ TÊN MÀN ẢO Ô — ỔN ĐỊNH, KHÔNG DẤU THỜI GIAN (2.98 · R4, VD-DISPLAY-SETTINGS-GROWTH) ═══════════════════════
 *
 * ## Bệnh [ĐO máy ảo 08/10]
 * Mỗi màn ảo ô mang tên `kachi-slot-<ô>-<currentTimeMillis>` và ngay sau khi tạo, `VdAppHost.launchInto` chạy
 * `wm set-user-rotation lock -d <id> 0` + `wm set-fix-to-user-rotation -d <id> enabled` (khoá chống "YouTube co vào
 * giữa", CLAUDE.md §6 — GIỮ). Mỗi lệnh ghi một mục bền vào `/data/system/display_settings.xml`, khoá theo
 * `DisplayInfo.uniqueId` — mà uniqueId của màn ảo CHỨA TÊN:
 *  - `VirtualDisplayAdapter.java:91-96` (android10-release): `uniqueId = "virtual:" + ownerPackageName + "," + ownerUid
 *    + "," + name + "," + getNextUniqueIndex(...)`;
 *  - `DisplayWindowSettings.java:677-684` `getIdentifier` ⇒ `displayInfo.uniqueId` (cấu hình mặc định);
 *  - `DisplayWindowSettings.java:566-574` `writeSettingsIfNeeded` ⇒ thêm mục + ghi LẠI CẢ TỆP (AtomicFile);
 *  - không có đường nào gỡ mục khi màn ảo bị huỷ — chỉ khi mục trở về rỗng (`isEmpty`, `:667-674` `removeEntry`).
 * Tên có dấu thời gian không bao giờ lặp ⇒ mỗi lần dựng ô (mỗi lần nổ máy — BYD giết Kachi khi tắt máy, mỗi lần đổi
 * hồ sơ/bố cục) để lại thêm một mục ~124 byte vĩnh viễn [ĐO: +124 B/lần khởi động nguội, máy ảo 08/10], và mỗi lệnh
 * `wm` sau đó ghi lại cả tệp đã phình. Android 12 (DL5) cũng khoá theo uniqueId
 * (`DisplayWindowSettingsProvider.java:196-203`, android12-release).
 *
 * ## Sửa
 * Tên ổn định theo ô: `kachi-slot-<ô>`; chỉ khi một màn ảo Kachi CÙNG tên còn sống (màn Kachi đời trước chưa nhả —
 * H2·1; app đỗ ở ô 7 mang tên ô cũ) mới thêm hậu tố thế hệ `-g<n>` nhỏ nhất còn trống. Tập tên ⇒ hữu hạn (số ô × số
 * màn ảo cùng ô sống ĐỒNG THỜI), nên số mục trong `display_settings.xml` có trần thay vì tăng theo số chuyến.
 *
 * Vì sao phải tránh trùng tên với màn ảo đang sống chứ không để hệ thống tự đánh `uniqueIndex`: `SlotVdLedger.adopt`
 * coi **cùng tên = cùng một màn ảo** (chỉ đổi khoá, không giải phóng). Hai màn ảo sống trùng tên ⇒ màn cũ rời sổ mà
 * không ai `release()` — đúng kiểu rò H2·1. Hệ thống thì không cần né: trùng tên chỉ làm `uniqueIndex` tăng (`:160-178`).
 *
 * Tác dụng phụ (có lợi, [SUY] từ `DisplayWindowSettings.java:395-413` `applySettingsToDisplayLocked`): màn ảo mới cùng
 * uniqueId được WM khôi phục khoá xoay ngay lúc thêm display, trước cả hai lệnh `wm` (vẫn chạy y nguyên, §6).
 */
object SlotVdName {

    /** Tiền tố — `TestBridgeState.VD_NAME_PREFIX` là gương của hằng này (khoá bởi `TestBridgeSafetyContractTest`). */
    const val PREFIX = "kachi-slot-"

    /** Hậu tố thế hệ khi tên gốc của ô đang bận. */
    private const val GEN = "-g"

    /** Tên gốc ổn định của ô [slot]. */
    fun base(slot: Int): String = "$PREFIX$slot"

    /**
     * Tên cho màn ảo MỚI của ô [slot], né mọi tên trong [live] (màn ảo Kachi đang sống — `SlotVdOwner.liveNames`).
     * Tất định: cùng đầu vào ⇒ cùng tên, nên cùng ô qua các chuyến dùng lại đúng một mục `display_settings.xml`.
     */
    fun pick(slot: Int, live: Set<String>): String {
        val base = base(slot)
        if (base !in live) return base
        var g = 1
        while ("$base$GEN$g" in live) g++
        return "$base$GEN$g"
    }
}
