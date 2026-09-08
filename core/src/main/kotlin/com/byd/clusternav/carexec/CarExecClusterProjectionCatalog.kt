package com.byd.clusternav.carexec

internal object CarExecClusterProjectionCatalog {
    val steps: List<CarStep> = listOf(
        CarStep(
            id = "observe",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Đọc trạng thái cụm: display nào là cụm, ai đang chiếm, geometry, globals",
            precondition = "không có",
            candidates = listOf(
                StepCandidate(
                    id = "observe.dumpsys",
                    purpose = "Đọc qua am stack list + dumpsys display + settings",
                    commands = listOf("am stack list", "dumpsys display", "dumpsys SurfaceFlinger --list"),
                    evidence = "parse ra đúng display cụm 1920x720 và danh sách occupant",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Đã chạy được qua runner 2026-07-27, 2.315 ms trên thiết bị thật",
                ),
            ),
        ),
        CarStep(
            id = "place",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Đưa task của app lên display của cụm",
            // "ĐỖ" bắt buộc từ 2026-08-01: candidate leo thang `place.movestack` mang nhãn MAY_HANG_SYSTEM
            // (sập system_server 3/3 lần, đo thật). Hai candidate đầu vẫn an toàn khi đang chạy, nhưng
            // precondition được canh ở mức STEP nên phải nói theo candidate nguy hiểm nhất.
            precondition = "xe ĐỖ (bước leo thang có thể treo hệ thống); biết display cụm; app đã cài",
            candidates = listOf(
                StepCandidate(
                    id = "place.freeform-then-resize",
                    purpose = "App resizeable: mở freeform rồi kéo full khung cụm",
                    commands = listOf(
                        "am start --display {display} --windowingMode 5 -n {comp}",
                        "am task resize {taskId} 0 0 1920 720",
                    ),
                    evidence = "xuất hiện Stack ... displayId={display} với taskId của {pkg}, bounds [0,0][1920,720]",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "vn.vietmap.live: lands 440x720 rồi resize đầy khung (2026-07-26)",
                ),
                StepCandidate(
                    id = "place.freeform-only",
                    purpose = "App khai unresizeable: chỉ cần freeform khi force_resizable_activities=1",
                    commands = listOf("am start --display {display} --windowingMode 5 -n {comp}"),
                    evidence = "task lên đúng 1920x720 ngay, không cần resize",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "com.byd.auto_photo landed 1920x720; am task resize bị từ chối nhưng không cần (2026-07-26)",
                ),
                StepCandidate(
                    id = "place.movestack",
                    purpose = "Đường leo thang khi hai cách trên không bám — ĐANG BỊ CẤM, xem fieldNote",
                    // LỖI THAM SỐ, cố ý GIỮ NGUYÊN chuỗi: tham số thứ nhất của `am display move-stack`
                    // là STACK id chứ không phải task id (ActivityManagerShellCommand.runDisplayMoveStack).
                    // Không thêm placeholder {stackId} mới cho một lệnh đang bị CẤM DÙNG — sẽ là nối dây
                    // cho thứ không ai được phép chạy. Ai gỡ lệnh cấm sau này phải sửa cả hai lỗi cùng lúc.
                    commands = listOf("am display move-stack {taskId} {display}"),
                    evidence = "task chuyển sang display cụm mà không tạo orphan",
                    verdictSource = VerdictSource.MEASURED,
                    // ĐO 2026-08-01 trên DiLink3, 3/3 lần: lệnh này làm system_server ném NPE giữa chừng
                    // (TaskSnapshotController.createTaskSnapshot ← AppWindowToken.initializeChangeTransition
                    // ← DisplayContent.moveStackToDisplay), task BIẾN MẤT khỏi hệ thống, phiên CarPlay
                    // rớt hẳn phải cắm lại cáp. Nhãn READ_ONLY cũ là SAI NGHIÊM TRỌNG — nó nói với người
                    // vận hành rằng lệnh này không đổi gì.
                    //
                    // Gốc: `DisplayContent.java:2401-2402` (AOSP android-10) gỡ stack khỏi display cũ
                    // (⇒ TaskStack.mDisplayContent = null) rồi để sóng đổi cấu hình nổ TRONG LÚC gắn vào
                    // display mới, trước khi mDisplayContent mới kịp được gán. Kích hoạt khi vượt ranh
                    // giới FREEFORM — mà display 0 là fullscreen còn display cụm là freeform, nên lần
                    // nào cũng vượt. Android 10 không có bản vá ở bất kỳ nhánh release nào.
                    // Chi tiết: docs/diagnostics/carplay-aa-cluster-placement-research-2026-08-01.md
                    risk = CandidateRisk.MAY_HANG_SYSTEM,
                    fieldNote = "CẤM DÙNG cho tới khi có phép đo khác: sập system_server 3/3 lần (2026-08-01). " +
                        "Hai lỗi phải sửa cùng lúc nếu gỡ cấm: (1) tham số 1 phải là STACK id, chuỗi hiện " +
                        "tại truyền {taskId} là sai; (2) chọn đường khác. Đường thay thế cần đo là " +
                        "`am stack move-task` — chuyển TASK vào stack đã nằm sẵn trên display đích, không " +
                        "chuyển cả stack qua display, nên không rơi vào cửa sổ mDisplayContent=null.",
                ),
            ),
        ),
        CarStep(
            id = "open-projection",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Bảo OEM route bề mặt cụm ra màn hình vật lý",
            precondition = "task đã ở trên display cụm",
            candidates = listOf(
                StepCandidate(
                    id = "open.seal-30-16-35",
                    purpose = "Seal DL3: giữ kiểu cong, chiếu, DI40",
                    commands = listOf(
                        "service call {svc} 2 i32 1000 i32 30 s16 \"\"",
                        "service call {svc} 2 i32 1000 i32 16 s16 \"\"",
                        "service call {svc} 2 i32 1000 i32 35 s16 \"\"",
                    ),
                    evidence = "CỤM VẬT LÝ hiện app — cần người nhìn, chưa có cách đo",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "Gõ tay 2026-07-27 09:58: cả ba trả Parcel(0,0), owner xác nhận 'lên rồi'",
                ),
                StepCandidate(
                    id = "open.seal-16-only",
                    purpose = "Hình dạng DiLink5: chỉ opcode chiếu, không có opcode kiểu",
                    commands = listOf("service call {svc} 2 i32 1000 i32 16 s16 \"\""),
                    evidence = "cụm hiện app trên đời máy không hỗ trợ đổi kiểu",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "Chưa thử; suy từ ClusterProfile DiLink5 castSeq=[16]",
                ),
            ),
        ),
        CarStep(
            id = "teardown",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Đóng đường chiếu, trả cụm về đồng hồ gốc",
            precondition = "đường chiếu đang mở",
            candidates = listOf(
                StepCandidate(
                    id = "teardown.18-then-0",
                    purpose = "Đóng chiếu rồi refresh video",
                    commands = listOf(
                        "service call {svc} 2 i32 1000 i32 18 s16 \"\"",
                        "service call {svc} 2 i32 1000 i32 0 s16 \"\"",
                    ),
                    evidence = "cụm hiện lại đồng hồ gốc, không cần reboot, không force-stop app",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "Gõ tay 2026-07-26 tối: owner xác nhận 'về rồi' (kiểu cong)",
                ),
                StepCandidate(
                    id = "teardown.0-only",
                    purpose = "Chỉ refresh video, xem có đủ để trả đồng hồ không",
                    commands = listOf("service call {svc} 2 i32 1000 i32 0 s16 \"\""),
                    evidence = "cụm về đồng hồ mà không cần opcode 18",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "Chưa thử — để biết opcode nào thật sự cần",
                ),
            ),
        ),
        CarStep(
            id = "restore",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Trả app về màn giữa và đưa globals về mốc đã ghi",
            precondition = "đã journal mốc globals trước khi đổi",
            candidates = listOf(
                StepCandidate(
                    id = "restore.main-standard",
                    purpose = "Đưa task về display 0 ở chế độ chuẩn",
                    commands = listOf("am start --display 0 --windowingMode 1 -n {comp}"),
                    evidence = "0 stack trên display cụm; app còn sống trên màn giữa",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "Đã dùng 2026-07-27: VietMap về màn giữa, display 1 rỗng",
                ),
                StepCandidate(
                    id = "restore.globals",
                    purpose = "Đưa bốn global về mốc",
                    commands = listOf(
                        "settings put global force_resizable_activities 1",
                        "settings put global transition_animation_scale 0.5",
                        "settings put global window_animation_scale 0.5",
                        "settings put global animator_duration_scale 1.0",
                    ),
                    evidence = "bốn global khớp mốc đã journal",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "Mốc đo trên xe: 1 / 0.5 / 0.5 / 1.0",
                ),
            ),
        ),
        CarStep(
            id = "switch",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Đổi app đang chiếu sang app khác mà không trả cụm về đồng hồ",
            precondition = "đang có một app trên cụm; đường chiếu đang mở",
            candidates = listOf(
                StepCandidate(
                    id = "switch.reparent-warm",
                    purpose = "App đích đã có task fullscreen ở màn giữa: một lệnh là đủ",
                    commands = listOf("am start --display {display} --windowingMode 5 -n {comp}"),
                    evidence = "occupant của cụm đổi sang {pkg}, app cũ còn sống ở màn giữa, chiếu không tắt",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "Đã chứng minh 2026-07-26: warm reparent bằng một lệnh",
                ),
                StepCandidate(
                    id = "switch.place-then-fit",
                    purpose = "App đích chưa chạy: mở rồi kéo khung",
                    commands = listOf(
                        "am start --display {display} --windowingMode 5 -n {comp}",
                        "am task resize {taskId} 0 0 1920 720",
                    ),
                    evidence = "occupant đổi và bounds đúng khung cụm",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                ),
            ),
        ),
        CarStep(
            id = "adjust-geometry",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Chỉnh từng cạnh khung hiển thị trên cụm",
            precondition = "app đang trên cụm",
            candidates = listOf(
                StepCandidate(
                    id = "geometry.task-resize",
                    purpose = "Đặt bốn cạnh trực tiếp lên task",
                    commands = listOf("am task resize {taskId} {left} {top} {right} {bottom}"),
                    evidence = "bounds đọc lại đúng bằng bốn giá trị vừa đặt; nội dung vẫn render, không méo",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "Số đo bounds là MEASURED, nhưng 'render có ổn không' thì phải nhìn",
                ),
                StepCandidate(
                    id = "geometry.overscan",
                    purpose = "Đường dự phòng của V1 khi freeform không sống",
                    commands = listOf("wm overscan {left},{top},{right},{bottom} -d {display}"),
                    evidence = "khung co lại đúng và app vẫn vẽ đủ",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "V1 dùng overscan 0,90,0,90 làm fallback",
                ),
            ),
        ),
        CarStep(
            id = "adjust-dpi",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Đổi density của cụm cho app đang chiếu",
            precondition = "app đang trên cụm",
            candidates = listOf(
                StepCandidate(
                    id = "dpi.wm-density",
                    purpose = "Đặt density riêng cho display cụm",
                    commands = listOf("wm density {dpi} -d {display}"),
                    evidence = "chữ và icon đổi kích thước, layout không bị cắt",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Mốc đo trên xe: density cụm mặc định 320",
                ),
                StepCandidate(
                    id = "dpi.reset",
                    purpose = "Trả density về mặc định",
                    commands = listOf("wm density reset -d {display}"),
                    evidence = "density trở lại 320 theo dumpsys display",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                ),
            ),
        ),
        CarStep(
            id = "set-style",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Đổi kiểu cụm (cong giữ km/h ↔ phẳng) — HOẶC kích thước vật lý cụm, xem fieldNote xung đột",
            precondition = "đời máy hỗ trợ đổi kiểu (styleOps khác null)",
            candidates = listOf(
                StepCandidate(
                    id = "style.curved-30",
                    purpose = "Kiểu cong, giữ đồng hồ km/h",
                    commands = listOf("service call {svc} 2 i32 1000 i32 30 s16 \"\""),
                    evidence = "cụm hiện kiểu cong và vẫn thấy km/h",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "Owner chấp nhận kiểu cong sau Stop; opcode 30 nằm trong castSeq DL3. XUNG ĐỘT chưa giải quyết " +
                        "(RE 2026-07-29, dashcast-src/data/prefs/ClusterPrefs.java + ui/settings/SettingsActivity.java + " +
                        "CHANGELOG.md:12): DashCast tự field-test và label 29/30/31 là KÍCH THƯỚC VẬT LÝ cụm theo TỪNG ĐỜI XE " +
                        "(29=8.8\" Atto3/Dolphin, 30=12.3\" Seal EU mặc định, 31=10.25\" Seal U DMI) — KHÔNG PHẢI cong/phẳng. " +
                        "Có thể cả hai đều đúng (một field vừa quyết kích thước vừa đổi hình dạng do khung khác nhau), hoặc " +
                        "quan sát cũ 'owner xác nhận cong' đã bị đọc nhầm. CHƯA đối chiếu lại trên chính xe test — xem thêm " +
                        "candidate style.probe-screen-size-29.",
                ),
                StepCandidate(
                    id = "style.flat-31",
                    purpose = "Kiểu phẳng, khung rộng hơn (hoặc: kích thước 10.25\" theo DashCast — xem xung đột ở style.curved-30)",
                    commands = listOf("service call {svc} 2 i32 1000 i32 31 s16 \"\""),
                    evidence = "cụm đổi sang kiểu phẳng",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "styleOps DL3 = 30 to 31. Xem fieldNote của style.curved-30 về xung đột cong/phẳng vs kích thước.",
                ),
                StepCandidate(
                    id = "style.probe-screen-size-29",
                    purpose = "Thử opcode 29 (chưa từng gửi trên xe này) để chốt xung đột cong/phẳng vs kích thước",
                    commands = listOf("service call {svc} 2 i32 1000 i32 29 s16 \"\""),
                    evidence = "NHÌN kỹ: nếu hình dạng cong/phẳng đổi -> ủng hộ giả thuyết cũ (style). Nếu độ phân giải/kích thước " +
                        "vẽ đổi mà hình dạng giữ nguyên -> ủng hộ giả thuyết DashCast (screen size). Chụp ảnh cả hai lần so sánh.",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "RE 2026-07-29: opcode 29 = 8.8\" theo DashCast (Atto 3/Dolphin) — trên Seal DL3 (12.3\" mặc định " +
                        "theo cùng bảng) có thể không có hiệu ứng nhìn thấy được nếu đúng là size-per-model, vì DL3 vốn không " +
                        "phải máy 8.8\". Vẫn đáng thử để loại trừ.",
                ),
            ),
        ),
    )
}
