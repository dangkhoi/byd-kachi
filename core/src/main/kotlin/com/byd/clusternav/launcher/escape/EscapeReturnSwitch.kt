package com.byd.clusternav.launcher.escape

/**
 * ═══ 2.98 · R18 — CÔNG TẮC người dùng cho "kéo app thoát ô về lại ô" (thuần, `:core`) ═══════════════════════════════════
 *
 * Owner 10/10 (*"OTA để lên xe test"*, chọn phương án OTA với R18 TẮT mặc định): R18 chưa có phép đo trên xe (vụ NPE
 * `createTaskSnapshot` 08-01 trên DiLink3) ⇒ bản 2.98 đi ra với công tắc *Cài đặt › Màn hình chính* MẶC ĐỊNH TẮT. TẮT ⇒ Kachi gửi
 * bảng [EscapeReturnConfig.OFF] (không api, không ô) ⇒ daemon không đăng ký bộ nghe, không dời gì ⇒ ô đi đúng đường 2.93.
 *
 * 2.99 — MẶC ĐỊNH BẬT ([DEFAULT_ON]). Bằng chứng xe 10/10 (`docs/diagnostics/oncar-seal-2026-10-10-r18-sla-can.md` §1, Seal DiLink3
 * fw 2606, 2.98 OTA, owner bật tay): Waze thoát ô 2 lần ⇒ `esc moved` ×2, KHÔNG `NullPointerException`/`createTaskSnapshot`, KHÔNG
 * cầu chì; owner *"waze OK"* — [ĐO 2 lượt · 1 app · 1 xe]; app khác với R18 [CHƯA BIẾT] (`_handoff/oncar-plan-2.99.md` §2). Vẫn là
 * CÔNG TẮC (tắt được một chạm) và vẫn đứng SAU cầu chì bền + cổng máy-hỗ-trợ ([resolve]): DiLink 5 / đời API lạ ⇒ [Status.Unsupported]
 * dù mặc định bật; daemon ném ⇒ [Status.Tripped] thắng mặc định. Giá trị đã lưu của người dùng (owner đã bật tay) không bị đổi.
 *
 * Một nơi quyết [Status] cho cả hai chỗ đọc (bảng gửi daemon · dòng mô tả trên màn Cài đặt) — để chữ trên màn không bao giờ nói
 * "đang bật" trong lúc bảng gửi đi là TẮT. Thứ tự xét: máy không hỗ trợ → cầu chì bền (THẮNG công tắc) → công tắc → bật.
 */
object EscapeReturnSwitch {

    /** Khoá công tắc — tệp `clusternav_prefs` (`Prefs`), theo XE (`ProfileScopeSlotEscape.DEVICE_KEYS`). */
    const val PREF_KEY = "escape_return_enabled"

    /** 2.98: TẮT (chưa đo xe). 2.99: BẬT — sau bằng chứng xe 10/10 (KDoc lớp); đổi lại là một hằng. */
    const val DEFAULT_ON = true

    sealed interface Status {
        /** Đầu xe này không có bảng tên hàm cho đời API đang chạy (DiLink 5 / Android 12 chưa đo) — công tắc không có tác dụng. */
        data object Unsupported : Status

        /** Cầu chì bền đã ngắt cho bản cài này ([why] = lý do daemon báo) — thắng công tắc; bản cài sau thử lại. */
        data class Tripped(val why: String) : Status

        /** Người dùng để TẮT ⇒ đường 2.93. */
        data object Off : Status

        /** Bật và dùng được — bảng gửi daemon mang [api]. */
        data class On(val api: EscapeReturnApi) : Status
    }

    fun resolve(switchOn: Boolean, profileApi: EscapeReturnApi?, sdkInt: Int, tripStored: String?, versionCode: Int): Status {
        if (profileApi == null || !profileApi.usableOn(sdkInt)) return Status.Unsupported
        tripWhy(tripStored, versionCode)?.let { return Status.Tripped(it) }
        return if (switchOn) Status.On(profileApi) else Status.Off
    }

    /** Bảng gửi daemon: chỉ [Status.On] mang api + ô; mọi trạng thái khác ⇒ [EscapeReturnConfig.OFF] (không lộ cả danh sách ô). */
    fun config(status: Status, slots: Map<Int, String>): EscapeReturnConfig =
        if (status is Status.On) EscapeReturnConfig(status.api, slots) else EscapeReturnConfig.OFF

    /** Lý do của dấu bền còn hiệu lực cho [versionCode] (`null` = không ngắt). Dấu hỏng ⇒ không ngắt (khớp [EscapeReturnBreaker.persistActive]). */
    fun tripWhy(stored: String?, versionCode: Int): String? {
        if (!EscapeReturnBreaker.persistActive(stored, versionCode)) return null
        return stored!!.substringAfter('|', "").trim().ifEmpty { "?" }
    }
}
