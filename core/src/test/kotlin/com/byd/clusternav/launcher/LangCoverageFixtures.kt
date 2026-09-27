package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertFalse

/**
 * Hằng + trợ giúp của [LangCoverageTest] — tách THUẦN khỏi `companion object` (507 dòng → trần 500, L6-debt 2026-09-27).
 * Nội dung giữ nguyên byte; lớp test nhập từng tên (`import …Fixtures.<tên>`). `SAME_ON_PURPOSE` vẫn là danh-sách-kèm-lý-do.
 */
internal object LangCoverageFixtures {

    /** Trần ký tự cho nhãn NGẮN tiếng Anh — chip thanh trạng thái cao ~24dp. */
    const val SHORT_CAP = 14

    /**
     * Nhãn/lựa chọn **được phép** giống nhau ở hai thứ tiếng → lý do.
     *
     * Mỗi mục là một quyết định phải giải thích được (cùng khuôn [SettingsCatalog.NOT_SETTINGS]): nếu danh sách
     * này chỉ là một tập chuỗi thì nó sẽ thành chỗ nhét mã vào cho bài canh im.
     */
    val SAME_ON_PURPOSE: Map<String, String> = mapOf(
        "PM2.5" to "ký hiệu ngành cho bụi mịn 2.5µm — dịch thành câu dài là sai chuẩn (spec §6 OQ2)",
        // ⚠ Ba mục "ESP" · "LDW" · "LDP" đã gỡ 2026-09-16 cùng toàn bộ ADAS/an toàn, và mục "Eco" đã gỡ
        // (V) 2026-09-17 cùng nút `drive_mode` — bài
        // `moi muc trong danh sach cho phep trung deu co ly do, va deu dung toi` bắt ngay nếu để lại.
        // ⚠⚠ 1.90 — 4 mục nữa gỡ cùng luật: "EV / HEV"/"EV"/"HEV" (`powertrain_mode`) + "Auto" (`headlight_mode`).
    )

    /** Mọi dòng có nhãn, ở đúng một chỗ để các bài không lệch phạm vi quét. */
    fun localizedRows(): List<Localized> =
        TelemetryRegistry.ALL + ControlRegistry.ALL + CapabilityGroups.ALL + WidgetRegistry.ALL +
            ActionMacros.ALL + SettingsCatalog.GROUPS + SettingsCatalog.ENTRIES +
            Domain.values().toList() + Quantity.values().toList() + TyreCorner.values().toList() +
            LauncherRequirements.ALL

    /**
     * Dấu tiếng Việt — chữ có mặt trong tiếng Việt mà **không** có trong tiếng Anh.
     *
     * Liệt kê tường minh thay vì dùng phép chuẩn hoá Unicode: bảng này đọc được, và nó cũng chính là bảng để
     * người sau thêm chữ nếu phát hiện sót. Không gồm dấu `–`/`·`/`°` — chúng là dấu câu/ký hiệu, dùng chung.
     */
    const val VIETNAMESE_MARKS =
        "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ" +
            "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ"

    fun hasVietnameseMark(s: String): Boolean = s.any { it in VIETNAMESE_MARKS }

    /** Ảnh chụp MỌI nhãn hiện ra — dùng để chứng minh đổi ngôn ngữ không để lại vết. */
    fun snapshot(): List<String> =
        localizedRows().map { it.displayLabel } +
            TelemetryRegistry.ALL.map { it.displayShortLabel } +
            ControlRegistry.ALL.flatMap { it.displayArgs } +
            CapabilityGroups.ALL.map { it.contentLine } +
            listOf(
                CapabilityPicker.GROUPS_TITLE, CapabilityPicker.GROUPS_NOTE,
                CapabilityPicker.SINGLES_TITLE, CapabilityPicker.HINT_PREFIX,
            ) +
            ThemeMode.values().map { it.label() } +
            DockEdge.values().map { it.label } +
            LayoutPreset.values().map { it.label } +
            ImageFit.values().map { it.label } +
            TyreStatus.values().mapNotNull { it.reason }

    fun assertNotEquals(a: Any?, b: Any?) =
        assertFalse(a == b, "hai bên phải khác nhau — nếu giống thì phép đo này không chứng minh gì")
}
