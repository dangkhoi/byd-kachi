package com.byd.clusternav.carexec

internal object CarExecClusterDiagnosticsCatalog {
    val profileSteps: List<CarStep> = listOf(
        CarStep(
            id = "probe-profile",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Nhận diện đời máy: tên service OEM, kích thước và density cụm",
            precondition = "không có",
            candidates = listOf(
                StepCandidate(
                    id = "probe.services-and-display",
                    purpose = "Liệt kê service OEM và đọc thông số display cụm",
                    commands = listOf("service list", "dumpsys display"),
                    evidence = "tìm được đúng một service container và display cụm; suy ra svcName của profile",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "DL3 có AutoContainer/AutoContainerNative/FissionGeneraySvc/FissionHostSvc",
                ),
                StepCandidate(
                    id = "probe.autocontainer-whitelist",
                    purpose = "Đọc whitelist client được phép gọi thẳng AutoContainer (bỏ qua Manager)",
                    commands = listOf("service list | grep -i autocontainer", "cat /system/etc/container_comm_cfg.json"),
                    evidence = "thấy service AutoContainer sống + nội dung whitelist (RE: chỉ com.xdja.clusterdemo được liệt kê)",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "RE 2026-07-29 (dashcast-src/ClusterManager.java + firmware jadx-l3-new/AutoContainerManager.java): " +
                        "getSystemService(\"AutoContainer\") qua Java API bị chặn theo whitelist gói đọc từ file này; " +
                        "DashCast né bằng gọi Binder trực tiếp (bypass Manager). Chưa xác nhận ClusterNav (uid app thường, " +
                        "không phải uid 2000 của adb shell) có nằm trong danh sách này không.",
                ),
                StepCandidate(
                    id = "probe.magicwindow-service",
                    purpose = "Dò service 'magicwindow' (IMagicWindowManager) — nghi ngờ là API windowing đa-cụm trên DL5",
                    commands = listOf("service check magicwindow", "dumpsys magicwindow"),
                    evidence = "service check trả về khác 'not found'; dumpsys in ra nội dung (dù không hiểu được) xác nhận service sống",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "RE 2026-07-29 (dashcast-src/CHANGELOG.md, dò DL5 build 183/184): service có thật, ServiceManager " +
                        "slot id=140 lúc dò, nhưng DashCast tự thừa nhận CHƯA BAO GIỜ gọi xa hơn bước sống-hay-chết này — " +
                        "sản phẩm DL5 sau đó quay về `am start --display N --windowingMode 5`. Đây là READ_ONLY, chỉ để biết " +
                        "service còn tồn tại trên DL3 hay không, KHÔNG suy ra nó làm gì.",
                ),
                StepCandidate(
                    id = "probe.trafficmonitor-service",
                    purpose = "Xác nhận com.byd.trafficmonitor (dịch vụ mute/allow TSR theo từng app) có cài và đang chạy",
                    commands = listOf(
                        "pm list packages | grep -i trafficmonitor",
                        "dumpsys activity services com.byd.trafficmonitor",
                        "service list | grep -i traffic",
                    ),
                    evidence = "thấy package com.byd.trafficmonitor + service đang chạy (pid, uid=system)",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "RE 2026-07-29 (carsettings-apk TSRCellular.java + IAppTrafficInterface.java) + đối chiếu dump thật " +
                        "docs/diagnostics/runs/20260621-141543: package NÀY đã xác nhận SỐNG trên xe thật (log tag " +
                        "TrafficMonitorService, ReceiverList uid=1000). AIDL: action " +
                        "com.byd.trafficmonitor.aidl.apptrafficremote, interface IAppTrafficInterface{getTrafficState(pkg), " +
                        "setRestrictByUser(pkg,restrict)} — cho phép BẬT/TẮT một app cụ thể làm nguồn TSR theo package name. " +
                        "Package cứng trong code RE ra (com.telenav.app.isa) KHÔNG có trên xe test — ai là provider thật trên " +
                        "xe này còn chưa biết; chỉ bind Service được (app-bound, không qua ServiceManager) nên không gọi được " +
                        "bằng `service call` thô — cần code app_process/app mới gọi bindService thật.",
                ),
                StepCandidate(
                    id = "probe.naviserviceapi-service",
                    purpose = "Dò xem AIDL com.byd.naviserviceapi.INaviService (BYD định nghĩa cho app nav bên thứ 3) có ai host không",
                    commands = listOf(
                        "dumpsys activity services | grep -i naviservice",
                        "pm list packages | grep -i tmap",
                        "dumpsys package com.tmap.auto.byd | grep -A5 -i service",
                    ),
                    evidence = "thấy NaviService của com.tmap.auto.byd (nếu TMap có cài) hoặc bất kỳ package nào khác host cùng interface",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "RE 2026-07-29 (jadx-tmap com/tmap/auto/byd/NaviService.java + com/byd/naviserviceapi/*): TMap TỰ " +
                        "làm server (onBind trả Stub), tức BYD side bind VÀO TMap, không phải ngược lại — ClusterNav không thể " +
                        "dùng contract này để đẩy dữ liệu hộ VietMap trừ khi tự host y hệt interface này VÀ có gì đó phía BYD " +
                        "chịu bind tới (chưa biết có hardcode package/class hay không). Chỉ dùng candidate này để XÁC NHẬN sự " +
                        "tồn tại, không phải để khai thác ngay.",
                ),
                StepCandidate(
                    id = "probe.vehiclesettings-installed",
                    purpose = "Xác nhận com.byd.vehiclesettings (chủ công tắc HUD thật) có cài trên xe test",
                    commands = listOf(
                        "pm list packages | grep -i vehiclesettings",
                        "dumpsys package com.byd.vehiclesettings | grep -E 'versionName|versionCode'",
                    ),
                    evidence = "thấy package + versionName/versionCode",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Tiền đề bắt buộc trước khi thử bất kỳ candidate nào của CarFeature.HUD_SWITCH (step hud-probe).",
                ),
            ),
        ),
        // ── Biển báo giới hạn tốc độ ─────────────────────────────────────────────────────────────────
        //
        // Chuỗi khám phá, theo đúng thứ tự phải biết: (1) ai sinh giá trị, (2) nó đi đường nào, (3) ai vẽ
        // nó, (4) lấy được giá trị đúng từ đâu, rồi mới (5) tắt nguồn sai và (6) ghi nguồn đúng vào.
        //
        // Không đảo thứ tự: ghi vào một đường chưa biết ai nghe thì không đọc được kết quả, mà vẫn mang đủ
        // rủi ro. Bốn step đầu CHỈ ĐỌC nên chạy được trong lúc lái.
    )

    val preHudSteps: List<CarStep> = listOf(
        CarStep(
            id = "reissue-policy",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Xác định khi nào được phát lại chuỗi mở chiếu, và opcode nào nguy hiểm",
            precondition = "XE ĐỖ, máy nổ; có người nhìn cụm; sẵn sàng khởi động lại head unit nếu treo",
            candidates = listOf(
                StepCandidate(
                    id = "reissue.full-while-warm",
                    purpose = "Phát cả 30,16,35 khi cụm ĐANG có app — đúng ca V1 nói sẽ treo",
                    commands = listOf(
                        "service call {svc} 2 i32 1000 i32 30 s16 \"\"",
                        "service call {svc} 2 i32 1000 i32 16 s16 \"\"",
                        "service call {svc} 2 i32 1000 i32 35 s16 \"\"",
                    ),
                    evidence = "cụm vẫn hiện app và máy không treo, HOẶC treo — cả hai đều là kết quả cần biết",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_HANG_SYSTEM,
                    fieldNote = "Nếu treo: đây là bằng chứng cho luật của V1. Nếu không: V2 được phép phát lại vô điều kiện, và đường app đơn giản hẳn",
                ),
                StepCandidate(
                    id = "reissue.16-only-while-warm",
                    purpose = "Chỉ phát 16 khi cụm đang có app — tách riêng opcode bị nghi tái tạo display",
                    commands = listOf("service call {svc} 2 i32 1000 i32 16 s16 \"\""),
                    evidence = "cụm còn app và máy không treo",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_HANG_SYSTEM,
                    fieldNote = "V1 bỏ cmd16 khỏi đường warm ở v0.61 vì cho rằng nó tái tạo virtual display",
                ),
                StepCandidate(
                    id = "reissue.35-only-while-warm",
                    purpose = "Chỉ phát 35 khi cụm đang có app — opcode ít bị nghi nhất",
                    commands = listOf("service call {svc} 2 i32 1000 i32 35 s16 \"\""),
                    evidence = "cụm còn app và máy không treo",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                ),
                StepCandidate(
                    id = "reissue.return-then-recast",
                    purpose = "Đường an toàn giả định: trả task về màn giữa cho cụm rỗng, rồi chiếu lại từ đầu",
                    commands = listOf(
                        "am start --display 0 -n {comp}",
                        "am start --display 1 --windowingMode 5 -n {comp}",
                        "am task resize {taskId} {left} {top} {right} {bottom}",
                        "service call {svc} 2 i32 1000 i32 30 s16 \"\"",
                        "service call {svc} 2 i32 1000 i32 16 s16 \"\"",
                        "service call {svc} 2 i32 1000 i32 35 s16 \"\"",
                    ),
                    evidence = "cụm hiện app trở lại, không treo",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "Đây là đường phục hồi khi người dùng nói 'không thấy map'; cần chứng minh trước khi đưa vào app",
                ),
                StepCandidate(
                    id = "reissue.task-placed-projection-closed",
                    purpose = "Dựng lại đúng trạng thái lệch: task trên cụm nhưng chiếu đã đóng",
                    commands = listOf(
                        "service call {svc} 2 i32 1000 i32 18 s16 \"\"",
                        "service call {svc} 2 i32 1000 i32 0 s16 \"\"",
                        "am stack list",
                    ),
                    evidence = "task vẫn trên display cụm với visible=true trong khi cụm hiện đồng hồ",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "Đã dựng được 2026-07-27 chiều; đây là trạng thái mà app KHÔNG đo được, nên phải hỏi người dùng",
                ),
            ),
        ),

        // ── Kính lái thật (HUD) ──────────────────────────────────────────────────────────────────────
        //
        // RE 2026-07-29: chuỗi gọi thật từ com.byd.vehiclesettings đã lần ra tới tận ranh giới stub —
        // xem KDoc của CarFeature.HUD_SWITCH. TẤT CẢ candidate dưới đây là lệnh app_process/navopen-v2.jar
        // (reflection thuần, không cần sửa gì trong ClusterNav) — KHÔNG PHẢI `service call` thô, vì
        // BYDAutoSettingDevice.getInstance() là singleton trong-tiến-trình, không phải Binder service tên
        // riêng gọi được qua ServiceManager.
    )

    val postHudSteps: List<CarStep> = listOf(
        CarStep(
            id = "cluster-overlay-toggles",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Bật/tắt lớp phủ ADAS và đèn cảnh báo trên cụm — độc lập với đường chiếu app",
            precondition = "cụm đang hiện đồng hồ (không cần đang chiếu app nào)",
            candidates = listOf(
                StepCandidate(
                    id = "overlay.adas-window-show",
                    purpose = "Hiện cửa sổ ADAS 2D/3D của Qt cluster",
                    commands = listOf("service call {svc} 2 i32 1000 i32 12 s16 \"\""),
                    evidence = "cụm hiện thêm lớp phủ ADAS (radar/làn đường...)",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "RE 2026-07-29 từ com.byd.clusterdebug (app chẩn đoán CHÍNH CHỦ của BYD), xác nhận qua nhiều đời máy.",
                ),
                StepCandidate(
                    id = "overlay.adas-window-hide",
                    purpose = "Ẩn lại cửa sổ ADAS vừa hiện",
                    commands = listOf("service call {svc} 2 i32 1000 i32 13 s16 \"\""),
                    evidence = "lớp phủ ADAS biến mất",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "Cặp đôi trực tiếp với overlay.adas-window-show.",
                ),
                StepCandidate(
                    id = "overlay.warning-lamps-on",
                    purpose = "Bật SÁNG toàn bộ đèn cảnh báo trên cụm cùng lúc",
                    commands = listOf("service call {svc} 2 i32 1000 i32 2 s16 \"\""),
                    evidence = "mọi đèn cảnh báo (ắc quy, phanh tay, ABS...) sáng đồng loạt",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "RE 2026-07-29, field-tested trên DL5 (owner tài liệu gốc): opcode 2 rồi đợi 3s rồi opcode 3. " +
                        "Chỉ thử khi ĐỖ — đèn cảnh báo giả có thể khiến người khác trong xe hoảng. LIVE-CONFIRMED 2026-07-29 " +
                        "trên Seal DL3: opcode 2 sáng thật vài chục cảnh báo cùng lúc trên cụm — nhưng xem SỰ CỐ THẬT ở " +
                        "overlay.warning-lamps-off, opcode 3 KHÔNG đảm bảo dọn sạch được những gì opcode 2 bật lên.",
                ),
                StepCandidate(
                    id = "overlay.warning-lamps-off",
                    purpose = "Tắt lại toàn bộ đèn cảnh báo vừa bật",
                    commands = listOf("service call {svc} 2 i32 1000 i32 3 s16 \"\""),
                    evidence = "mọi đèn cảnh báo tắt, cụm về trạng thái bình thường",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "SỰ CỐ THẬT 2026-07-29 trên Seal DL3: gửi opcode 3 HAI LẦN + opcode 0 (video refresh, " +
                        "teardown.0-only) MỘT LẦN — vẫn còn sót icon ADAS + biển đường cấm trên cụm, và km/h giới hạn " +
                        "nhảy sai thành 60 (HUD vẫn đúng 30, hai màn lệch nhau). CHỈ tắt máy xe rồi khởi động lại (power " +
                        "cycle thật) mới dọn sạch hoàn toàn. KẾT LUẬN: opcode 3 KHÔNG đảm bảo là nghịch đảo sạch của " +
                        "opcode 2 như tên gọi — đừng coi cặp on/off này là hoàn tác được thuần bằng phần mềm. Trước khi " +
                        "thử lại overlay.warning-lamps-on, PHẢI sẵn sàng tắt/mở máy xe làm phương án dọn cuối cùng, và " +
                        "không nên đoán thêm opcode khác khi 3/0 đã không ăn — đúng bài học CLAUDE.md §5 (đường trả về " +
                        "phải có sẵn TRƯỚC khi đổi trạng thái, không phải đi tìm sau khi đã kẹt).",
                ),
                StepCandidate(
                    id = "overlay.adas-debug-legacy-on",
                    purpose = "Thử 'chế độ debug ADAS bí mật' từ build DashCast v0.3.2-alpha cũ — chưa rõ còn hoạt động trên ROM hiện tại không",
                    commands = listOf("service call {svc} 2 i32 1000 i32 47 s16 \"\""),
                    evidence = "quan sát khác biệt so với overlay.adas-window-show (nếu có, mới đáng ghi nhận là cơ chế riêng)",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "RE 2026-07-29: chỉ thấy trong CHANGELOG.md lịch sử của DashCast (versionCode 68), KHÔNG còn " +
                        "trong source hiện tại của dashcast-src — có thể đã bị bỏ vì trùng/kém hơn opcode 12/13 mới hơn. Thử để " +
                        "loại trừ, không kỳ vọng khác biệt.",
                ),
                StepCandidate(
                    id = "overlay.adas-debug-legacy-off",
                    purpose = "Tắt lại nếu overlay.adas-debug-legacy-on có hiệu ứng nhìn thấy được",
                    commands = listOf("service call {svc} 2 i32 1000 i32 48 s16 \"\""),
                    evidence = "trở lại như trước overlay.adas-debug-legacy-on",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "Chạy ngay sau overlay.adas-debug-legacy-on bất kể có thấy hiệu ứng hay không.",
                ),
            ),
        ),
    )
}
