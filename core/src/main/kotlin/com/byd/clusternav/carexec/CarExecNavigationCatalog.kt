package com.byd.clusternav.carexec

internal object CarExecNavigationCatalog {
    val steps: List<CarStep> = listOf(
        CarStep(
            id = "nav-listener",
            feature = CarFeature.NAVIGATION,
            purpose = "Quyền đọc notification của app dẫn đường",
            precondition = "không có",
            candidates = listOf(
                StepCandidate(
                    id = "nav.listener-allow",
                    purpose = "Cấp quyền notification listener bằng cmd",
                    commands = listOf("cmd notification allow_listener {comp}"),
                    evidence = "settings secure enabled_notification_listeners chứa {comp}",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "NavConnect tự-heal dùng đúng đường này",
                ),
                StepCandidate(
                    id = "nav.listener-read",
                    purpose = "Đọc danh sách listener hiện tại",
                    commands = listOf("settings get secure enabled_notification_listeners"),
                    evidence = "đọc được danh sách, xác nhận có hoặc không có {comp}",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                ),
            ),
        ),
        CarStep(
            id = "nav-source",
            feature = CarFeature.NAVIGATION,
            purpose = "App dẫn đường có đang phát dữ liệu chỉ đường không",
            precondition = "listener đã được cấp quyền; đang có tuyến",
            candidates = listOf(
                StepCandidate(
                    id = "nav.notification-dump",
                    purpose = "Đọc notification đang hiện của app dẫn đường",
                    commands = listOf("dumpsys notification --noredact"),
                    evidence = "thấy notification của {pkg} kèm nội dung chỉ đường",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "CHƯA kiểm trên xe qua runner — cần xác nhận cờ --noredact có được phép",
                ),
            ),
        ),
        // ── Cổng render nav zin (semon) ──────────────────────────────────────────────────────────────
        //
        // KHÁM PHÁ CŨ, CHƯA CHẠY XONG: phiên 2026-06-22 (docs/plans/cluster-nav-render-gate.html +
        // docs/diagnostics/verify-on-car.sh, TRƯỚC refactor V2 này rất lâu) đã LIVE-CONFIRM trên chính
        // Seal DL3 test rằng broadcast AUTONAVI_STANDARD_BROADCAST_SEND (TYPE=1, IS_BYD_MAP=false) khiến
        // AmapService thật sự GHI dữ liệu vào cụm (mIsGAODENaving=true, CAN + AutoContainerManager.sendInfo2)
        // — nhưng RENDER bị chặn bởi "semon", một kernel security monitor bật qua property
        // sys.init.navi_protect (tắt = mở cổng, theo chính init.rc rút từ system.img thật của xe này).
        // 7 cách mở cổng (M1-M8) đã viết sẵn trong verify-on-car.sh nhưng lần chạy 2026-06-22 để lại thư
        // mục verify-runs/20260622-221938 RỖNG — script không hoàn tất được lần đó (không rõ lý do, có thể
        // mất kết nối). Trạng thái đúng: "chưa biết cổng có mở được không", KHÔNG PHẢI "đã thử và thất bại".
        //
        // ĐÃ ĐO 2026-07-29 trên chính xe test (Seal DL3, navi_protect=1 KHÔNG đổi, whitelist=0 và
        // change_navi_auth=1 đã sẵn có từ trước — KHÔNG do phiên này set): gate.broadcast-full-render (bên
        // dưới) làm CỤM hiện icon + khoảng cách + tên đường THẬT, KHÔNG cần setprop bất kỳ property nào.
        // gate.setprop-navi-protect (M1) và gate.navopen-open (M8) CHƯA từng được thử trong phiên này vì
        // không cần thiết nữa cho phần cụm — vẫn giữ lại trong catalog cho trường hợp property đã bị đổi về
        // mặc định ở phiên khác, hoặc cho phần HUD (vẫn chưa hiện gì dù cụm đã render — xem hud-probe).
        CarStep(
            id = "nav-render-gate",
            feature = CarFeature.NAVIGATION,
            purpose = "Mở cổng render nav zin của cụm (semon/navi_protect) — TRƯỚC KHI đổ công sức vào bất kỳ đường nav nào khác",
            precondition = "cụm đang hiện đồng hồ hoặc app khác; chưa biết navi_protect/whitelist đang bật hay tắt",
            candidates = listOf(
                StepCandidate(
                    id = "gate.probe",
                    purpose = "Đọc 4 property quyết định cổng, trước khi đổi gì",
                    commands = listOf(
                        "getprop ro.build.system.fission_single_os",
                        "getprop sys.init.navi_protect",
                        "getprop sys.init.whitelist",
                        "getprop sys.change_navi_auth",
                    ),
                    evidence = "đọc được 4 giá trị hiện tại làm mốc so sánh trước/sau",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Mốc 2026-06-22 trên chính xe này: fission_single_os=0, 1for2=true (nghĩa là CẢ hai đường " +
                        "FlatBuffer/AutoContainerManager.sendInfo2 VÀ HAL/CAN đều chạy song song cho mỗi update).",
                ),
                StepCandidate(
                    id = "gate.baseline-broadcast-only",
                    purpose = "Bơm 1 frame KHÔNG mở cổng trước — dựng lại đúng baseline 'data vào cụm nhưng không hiện' để so sánh với các bước mở cổng bên dưới",
                    commands = listOf(
                        "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false --ei NEW_ICON 2 --ei SEG_REMAIN_DIS 250 --es NEXT_ROAD_NAME 'Nguyen Hue' --ei ROUTE_REMAIN_DIS 5000 --ei ROUTE_REMAIN_TIME 480",
                    ),
                    evidence = "logcat AmapService log mIsGAODENaving=true (grep 'amap|navi|cluster|1for2|fission|guide|instrument'); NHÌN cụm — mong đợi KHÔNG đổi gì nếu cổng vẫn đóng",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "SAI, LIVE-CONFIRMED 2026-07-29 trên chính xe test: giả thuyết 'không đổi gì' KHÔNG đúng — " +
                        "cụm HIỆN icon + tên đường ngay từ candidate này (chỉ riêng khoảng cách kẹt ở -1, xem " +
                        "gate.broadcast-full-render để có số thật). Cơ chế TYPE=1+IS_BYD_MAP=false đã chứng minh qua source " +
                        "2026-07-25 và giờ tự tay đo lại khớp — nhưng tiền đề 'cổng đóng nên không hiện' của phiên 2026-06-22 " +
                        "không còn đúng ở trạng thái property hiện tại của xe này (xem gate.probe).",
                ),
                StepCandidate(
                    id = "gate.broadcast-full-render",
                    purpose = "Y hệt baseline nhưng thêm 4 field chuỗi _AUTO — bản ĐÃ CHỨNG MINH render đủ icon+khoảng cách+tên đường trên cụm, không cần setprop gì",
                    commands = listOf(
                        "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false --ei NEW_ICON 3 --ei SEG_REMAIN_DIS 444 --es NEXT_ROAD_NAME 'Ba Test Le Loi' --ei ROUTE_REMAIN_DIS 6000 --ei ROUTE_REMAIN_TIME 300 --es SEG_REMAIN_DIS_AUTO '444 m' --es ROUTE_REMAIN_DIS_AUTO '6.0 km' --es ROUTE_REMAIN_TIME_AUTO '5 min' --es ROUTE_REMAIN_TIME_STRING '5 min'",
                    ),
                    evidence = "CỤM hiện đúng icon rẽ + '444 m' (không phải -1) + 'Ba Test Le Loi' cùng lúc",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "LIVE-CONFIRMED 2026-07-29 trên chính xe test (navi_protect=1 suốt, không setprop gì): thiếu " +
                        "SEG_REMAIN_DIS_AUTO/ROUTE_REMAIN_DIS_AUTO/ROUTE_REMAIN_TIME_AUTO/ROUTE_REMAIN_TIME_STRING thì khoảng " +
                        "cách kẹt ở -1 dù icon+tên đường vẫn lên đúng (xem gate.baseline-broadcast-only) — thêm 4 field chuỗi " +
                        "này là đủ, không cần bất kỳ M1/M2/M3/M8 nào. HUD kính lái vẫn KHÔNG hiện gì từ đường này (xem hud-probe " +
                        "hud.nav-content-toggle-on) — cụm và HUD rõ ràng là hai đường render riêng.",
                ),
                StepCandidate(
                    id = "gate.setprop-navi-protect",
                    purpose = "M1 — tắt semon trực tiếp qua navi_protect, rồi bơm lại frame để xem cổng có mở không",
                    commands = listOf(
                        "setprop sys.init.navi_protect 0",
                        "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false --ei NEW_ICON 2 --ei SEG_REMAIN_DIS 250 --es NEXT_ROAD_NAME 'Nguyen Hue' --ei ROUTE_REMAIN_DIS 5000 --ei ROUTE_REMAIN_TIME 480",
                    ),
                    evidence = "NHÌN cụm: làn nav (mũi tên + tên đường + khoảng cách) hiện RA, và đồng hồ/ADAS/D-R-P vẫn còn đủ cùng lúc",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "Rẻ nhất trong 3 cách setprop. Ẩn số: shell uid 2000 có được SELinux cho phép setprop key này " +
                        "không — tự lộ ngay khi đọc lại bằng gate.probe. Không đảo thứ tự: đo cổng trước, injec sau.",
                ),
                StepCandidate(
                    id = "gate.setprop-whitelist",
                    purpose = "M2 — tắt enforcement theo whitelist (ngả khác cùng tắt semon)",
                    commands = listOf(
                        "setprop sys.init.whitelist 0",
                        "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false --ei NEW_ICON 2 --ei SEG_REMAIN_DIS 250 --es NEXT_ROAD_NAME 'Nguyen Hue' --ei ROUTE_REMAIN_DIS 5000 --ei ROUTE_REMAIN_TIME 480",
                    ),
                    evidence = "như gate.setprop-navi-protect",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "Chỉ thử nếu M1 trượt hoặc để xác nhận cả hai property đều điều khiển cùng một semon switch.",
                ),
                StepCandidate(
                    id = "gate.setprop-change-auth",
                    purpose = "M3 — cấp cờ 'đổi nav được phép' cấp BYD (có thể là cờ đúng thay vì tắt cả monitor)",
                    commands = listOf(
                        "setprop sys.change_navi_auth 1",
                        "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ez IS_BYD_BAIDU_MAP false --ei NEW_ICON 2 --ei SEG_REMAIN_DIS 250 --es NEXT_ROAD_NAME 'Nguyen Hue' --ei ROUTE_REMAIN_DIS 5000 --ei ROUTE_REMAIN_TIME 480",
                    ),
                    evidence = "như gate.setprop-navi-protect",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "Không có tài liệu công khai nào về key này — tên thật rút từ chính init.rc của xe. Giá trị mặc định là 0.",
                ),
                StepCandidate(
                    id = "gate.navopen-probe",
                    purpose = "M8 bước 1 — probe thuần bằng reflection (navopen-v2.jar), KHÔNG ghi gì, chỉ xem HAL có cấp device không",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen probe",
                    ),
                    evidence = "output liệt kê InstrumentDevice/SettingDevice khác null, và các feature-id INSTRUMENT_SEND_NAVI_STATUS_SET v.v. có giá trị thật (khác 'không có field')",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "TIỀN ĐỀ: đã `adb push apks/navopen-v2.jar /data/local/tmp/navopen.jar` trước (v2 = bản 2026-07-29, " +
                        "thêm getraw/adas so với navopen.jar gốc, xem NavOpen/src/com/byd/navopen/NavOpen.java). " +
                        "app_process chạy từ adb shell đã sẵn uid 2000 (như proxy OpenBYD dùng), không cần root, không cần daemon.",
                ),
                StepCandidate(
                    id = "gate.navopen-open",
                    purpose = "M8 bước 2 — replica chính xác cách AmapService tự mở cổng qua HAL (SET_NAVI_SCREEN_STATUS=3) rồi bơm 1 frame",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen full 'Nguyen Hue'",
                    ),
                    evidence = "NHÌN cụm: làn nav hiện RA (đường mạnh nhất nếu M1-M3 bị SELinux chặn, vì đây không phải setprop mà là đúng lệnh HAL map zin tự gọi)",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "RE 2026-07-29: đây LÀ cơ chế map hệ thống dùng thật (AmapService gọi đúng setNaviScreenStatus(SET_NAVI_SCREEN_STATUS_SET,3)) " +
                        "— không phải suy đoán. Đóng lại bằng gate.navopen-close ngay sau khi quan sát xong.",
                ),
                StepCandidate(
                    id = "gate.navopen-close",
                    purpose = "M8 bước 3 — đóng lại nav đã mở ở gate.navopen-open, trả cụm về trạng thái trước đó",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen close",
                    ),
                    evidence = "cụm hết hiện làn nav vừa bơm (không đảm bảo trả nguyên trạng cờ navi_protect/whitelist nếu đã setprop ở candidate khác — tự setprop lại giá trị đọc được ở gate.probe)",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "Chỉ đóng phần INSTRUMENT_SEND_NAVI_STATUS/GUIDE_INFO — KHÔNG trả navi_protect/whitelist về giá trị cũ; " +
                        "nếu đã chạy gate.setprop-* thì phải tự setprop lại giá trị đã ghi ở gate.probe.",
                ),
            ),
        ),
        CarStep(
            id = "nav-cluster-lane",
            feature = CarFeature.NAVIGATION,
            purpose = "Làn cụm có hiện hướng rẽ và khoảng cách không",
            precondition = "nav-source đang phát",
            candidates = listOf(
                StepCandidate(
                    id = "nav.cluster-lane-visual",
                    purpose = "Xác nhận bằng mắt trên cụm",
                    commands = listOf("dumpsys display"),
                    evidence = "CỤM hiện mũi tên rẽ và khoảng cách đúng như app dẫn đường",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Chưa có cách đo output của làn cụm; giống Q1 của Cast",
                ),
            ),
        ),
    )
}
