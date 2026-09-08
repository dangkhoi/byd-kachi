package com.byd.clusternav.carexec

internal object CarExecHudCatalog {
    val steps: List<CarStep> = listOf(
        CarStep(
            id = "hud-probe",
            feature = CarFeature.HUD_SWITCH,
            purpose = "Đọc xem xe test có kính lái vật lý không, rồi mới thử bật/tắt/đổi nội dung",
            precondition = "đã xác nhận com.byd.vehiclesettings có cài (probe.vehiclesettings-installed); đã push navopen-v2.jar",
            candidates = listOf(
                StepCandidate(
                    id = "hud.config-read",
                    purpose = "ĐỌC (không ghi) SET_HUD_CONFIG — 0/không có field=không có HUD, 1=W-mode (kính lái thường), 2=AR-mode",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen getraw setting 38B00015",
                    ),
                    evidence = "trả về 0, 1 hoặc 2 mà không lỗi — 0 nghĩa là BƯỚC DỪNG LẠI Ở ĐÂY, xe không có HUD để thử tiếp",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "RE 2026-07-29 (carsettings-apk HudFuncVisibleUtils.java + firmware Setting.java): feature-id " +
                        "SET_HUD_CONFIG=0x38B00015, đọc-only theo đúng thiết kế của com.byd.vehiclesettings — app đó cũng chỉ " +
                        "đọc field này để QUYẾT ĐỊNH có hiện màn cài đặt HUD hay không, không bao giờ ghi vào nó.",
                ),
                StepCandidate(
                    id = "hud.switch-feedback-read",
                    purpose = "ĐỌC trạng thái công tắc HUD hiện tại (1=đang bật/2=đang tắt) trước khi đổi",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen getraw setting 38B0001C",
                    ),
                    evidence = "trả về 1 hoặc 2",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "feature-id SET_HUD_SWITCH_STATUS_FEEDBACK=0x38B0001C (RE 2026-07-29, cùng nguồn với hud.config-read). " +
                        "Đọc trước để biết giá trị trả về sau khi ghi hud.switch-on/off có đúng là đã đổi hay không.",
                ),
                StepCandidate(
                    id = "hud.switch-on",
                    purpose = "Bật công tắc HUD (nếu hud.config-read xác nhận có kính lái)",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen setraw setting 4C10E023 1",
                    ),
                    evidence = "NHÌN kính lái vật lý: có hiện gì không; đọc lại bằng hud.switch-feedback-read xem có đổi thành 1 không",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "RE 2026-07-29 (HudSwitchModel.java: true->mcuState=1). Chuỗi gọi: UI toggle -> HudSwitchModel -> " +
                        "HalSetter.set(BYDAutoSettingDevice.class, SET_HUD_SWITCH_SET, 1) -> BYDAutoSettingDevice.getInstance(ctx)" +
                        ".set(int[]{0x4C10E023}, EventValue{intValue=1}) — CÙNG hình dạng lệnh BydHal.kt đã dùng cho INSTRUMENT, " +
                        "khác class (SETTING). CHƯA xác nhận quyền set() có được cấp cho tiến trình gọi (app_process qua adb " +
                        "shell = uid 2000; ClusterNav app thật chạy uid khác, có thể bị chặn khác nhau).",
                ),
                StepCandidate(
                    id = "hud.switch-off",
                    purpose = "Tắt lại công tắc HUD — dùng để hoàn tác hud.switch-on hoặc tự nó là một phép thử độc lập",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen setraw setting 4C10E023 2",
                    ),
                    evidence = "kính lái tắt hẳn (nếu đang bật); đọc lại bằng hud.switch-feedback-read xem có đổi thành 2 không",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "mcuState=2 (RE 2026-07-29, HudSwitchModel.java: false->mcuState=2 — LƯU Ý không phải 0).",
                ),
                StepCandidate(
                    id = "hud.nav-content-toggle-on",
                    purpose = "Bật cờ hiển thị NỘI DUNG dẫn đường trên HUD (khác với công tắc HUD tổng)",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen setraw setting 4C10E03A 1",
                    ),
                    evidence = "sau khi bật cờ này VÀ HUD đã bật (hud.switch-on) VÀ đang có nav thật (NavOpen full/open), xem HUD có hiện hướng rẽ không",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "RE 2026-07-29 (HudOptionDisplayModel.java): SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET=0x4C10E03A. " +
                        "CẢNH BÁO chưa kiểm chứng: đây chỉ là cờ 'CÓ hiện nav trên HUD hay không', KHÔNG có bằng chứng nó đổi " +
                        "NGUỒN dữ liệu — rất có thể chỉ gate cho app nav OEM riêng, không liên quan gì tới ghi INSTRUMENT_GUIDE_INFO_SIMPLE_SET " +
                        "mà BydHal.kt/navopen.jau đang dùng. Đừng mặc định 'bật cờ này thì làn cụm tự nhảy sang HUD'.",
                ),
                StepCandidate(
                    id = "hud.adas-content-toggle-on",
                    purpose = "Bật cờ hiển thị cảnh báo ADAS trên HUD",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen setraw setting 4C10E030 1",
                    ),
                    evidence = "NHÌN HUD sau khi bật — icon ADAS (nếu có cảnh báo đang active) có hiện không",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "RE 2026-07-29 (HudOptionDisplayModel.java): SET_SAFE_DRIVING_ASSIST_STATUS_SET=0x4C10E030. " +
                        "Nhóm icon ADAS chung (FCW/LDW...), không riêng cho biển báo tốc độ.",
                ),
            ),
        ),

        // ── Toggle phụ trên cụm (opcode AutoContainer chưa có candidate) ────────────────────────────
        //
        // RE 2026-07-29 (dashcast-src/ui/diag/DiagActivity.java, đối chiếu app com.byd.clusterdebug của
        // chính BYD, xác nhận đa đời máy DL3/Di4/DL5/DL6): các opcode 2/3/12/13 khác opcode chiếu/teardown
        // đã biết (16/18/30/31/35). 47/48 là build cũ hơn, CHƯA xác nhận còn tồn tại trên ROM hiện tại.
    )
}
