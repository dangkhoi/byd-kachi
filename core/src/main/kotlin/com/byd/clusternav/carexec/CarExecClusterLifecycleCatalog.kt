package com.byd.clusternav.carexec

internal object CarExecClusterLifecycleCatalog {
    val steps: List<CarStep> = listOf(
        CarStep(
            id = "bootstrap-cold",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Lần chiếu đầu từ đồng hồ: tạo/đánh thức display cụm trước khi đặt app",
            precondition = "cụm đang hiện đồng hồ; chưa có phiên nào",
            candidates = listOf(
                StepCandidate(
                    id = "bootstrap.seal-cold",
                    purpose = "Phát chuỗi seal lạnh rồi đọc lại display",
                    commands = listOf(
                        "service call {svc} 2 i32 1000 i32 30 s16 \"\"",
                        "service call {svc} 2 i32 1000 i32 16 s16 \"\"",
                        "dumpsys display",
                    ),
                    evidence = "display cụm 1920x720 tồn tại và ở trạng thái ON",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                ),
            ),
        ),
        CarStep(
            id = "probe-target",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "App đích có cài, có launcher, có resizeable hay không",
            precondition = "biết tên package",
            candidates = listOf(
                StepCandidate(
                    id = "target.package-info",
                    purpose = "Đọc thông tin gói và cờ resize",
                    commands = listOf(
                        "pm path {pkg}",
                        "dumpsys package {pkg}",
                        "cmd package resolve-activity --brief {pkg}",
                    ),
                    evidence = "biết chắc: đã cài hay chưa, có activity khởi chạy hay không, có cờ unresizeable hay không",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Cờ PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE quyết định chọn candidate place nào",
                ),
            ),
        ),
        CarStep(
            id = "resume-protected",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Đưa phiên CarPlay/Android Auto lên cụm mà KHÔNG tắt app của người ta",
            precondition = "sink đang kết nối",
            candidates = listOf(
                StepCandidate(
                    id = "protected.resume-no-kill",
                    purpose = "Chỉ reparent task đang sống, không force-stop, không clear task",
                    commands = listOf("am start --display {display} --windowingMode 5 -n {comp}"),
                    evidence = "phiên điện thoại KHÔNG bị ngắt; pid của app đích không đổi",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "V1 tuyệt đối không force-stop CP/AA; đây là ràng buộc sản phẩm",
                ),
            ),
        ),
        CarStep(
            id = "return-protected",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Trả app về màn giữa một cách nhẹ, kể cả khi nó cưỡng lại",
            // "ĐỖ" bắt buộc từ 2026-08-01: cùng lý do như step `place` — candidate leo thang
            // `return.movestack-main` dùng `am display move-stack`, đã đo là sập system_server.
            // Candidate đầu (`am start --display 0`) vẫn an toàn khi đang chạy.
            precondition = "xe ĐỖ (bước leo thang có thể treo hệ thống); app đang trên cụm",
            candidates = listOf(
                StepCandidate(
                    id = "return.gentle-main",
                    purpose = "Mở lại ở display 0 chế độ chuẩn",
                    commands = listOf("am start --display 0 --windowingMode 1 -n {comp}"),
                    evidence = "app về màn giữa, pid không đổi, cụm rỗng",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                ),
                StepCandidate(
                    id = "return.movestack-main",
                    purpose = "Đường leo thang nếu mở lại không đủ — ĐANG BỊ CẤM, xem fieldNote",
                    // Cùng lỗi tham số như `place.movestack` (phải là STACK id) — giữ nguyên chuỗi vì
                    // lệnh đang bị cấm; xem ghi chú ở đó.
                    commands = listOf("am display move-stack {taskId} 0"),
                    evidence = "task về display 0 mà không tạo orphan (đây là lệnh từng gây NPE ở V1)",
                    verdictSource = VerdictSource.MEASURED,
                    // Cùng lỗi framework với `place.movestack`. Đáng chú ý: fieldNote V1 dưới đây ghi đúng
                    // điều kiện kích hoạt mà mãi 2026-08-01 mới đọc ra được từ source AOSP — "task
                    // FREEFORM đang HIỆN" chính là hai guard trong `AppWindowToken.initializeChangeTransition`.
                    // Một xác nhận độc lập từ hiện trường cũ, và là lý do nhãn READ_ONLY cũ càng khó chấp nhận.
                    risk = CandidateRisk.MAY_HANG_SYSTEM,
                    fieldNote = "V1: move-stack một task freeform đang hiện từng gây half-reparent. " +
                        "Xác nhận lại 2026-08-01: sập system_server 3/3 lần, task biến mất khỏi hệ thống.",
                ),
            ),
        ),
        CarStep(
            id = "pip-guard",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Chặn và trả lại chế độ PIP của app đích",
            precondition = "đã journal app-op PIP trước khi đổi",
            candidates = listOf(
                StepCandidate(
                    id = "pip.block",
                    purpose = "Chặn PIP trong lúc chiếu",
                    commands = listOf("appops set {pkg} PICTURE_IN_PICTURE ignore"),
                    evidence = "app không nhảy PIP khi bị reparent",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                ),
                StepCandidate(
                    id = "pip.restore",
                    purpose = "Trả app-op về mốc",
                    commands = listOf("appops set {pkg} PICTURE_IN_PICTURE allow"),
                    evidence = "appops đọc lại đúng mốc đã journal",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                ),
            ),
        ),
        CarStep(
            id = "animation-quiesce",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Tắt/khôi phục animation để tránh reparent giữa lúc transition",
            precondition = "đã journal ba global animation",
            candidates = listOf(
                StepCandidate(
                    id = "animation.disable",
                    purpose = "Đưa ba scale về 0 trong lúc thao tác",
                    commands = listOf(
                        "settings put global transition_animation_scale 0",
                        "settings put global window_animation_scale 0",
                        "settings put global animator_duration_scale 0",
                    ),
                    evidence = "ba global bằng 0; reparent không rơi vào lúc đang transition",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                    fieldNote = "NPE A của V1 gắn với VD bị huỷ/tái tạo khi token còn transition",
                ),
                StepCandidate(
                    id = "animation.restore",
                    purpose = "Trả ba scale về mốc",
                    commands = listOf(
                        "settings put global transition_animation_scale 0.5",
                        "settings put global window_animation_scale 0.5",
                        "settings put global animator_duration_scale 1.0",
                    ),
                    evidence = "ba global khớp mốc đo trên xe: 0.5 / 0.5 / 1.0",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.REVERSIBLE,
                ),
            ),
        ),
        CarStep(
            id = "orphan-inspect",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Phát hiện task/stack mồ côi trên cụm",
            precondition = "không có",
            candidates = listOf(
                StepCandidate(
                    id = "orphan.list",
                    purpose = "Đối chiếu stack, layer và tiến trình",
                    commands = listOf(
                        "am stack list",
                        "dumpsys SurfaceFlinger --list",
                        "pidof {pkg}",
                    ),
                    evidence = "biết chắc có stack nào trỏ tới tiến trình đã chết hay không",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Layer bydAdd-<pkg> tồn tại KHÔNG chứng minh đang chiếu (đo 2026-07-27)",
                ),
            ),
        ),
        CarStep(
            id = "target-process",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Giết hoặc khởi động lại app đích để thử ca app chết giữa lúc chiếu",
            precondition = "app đang trên cụm; app KHÔNG phải CarPlay/Android Auto",
            candidates = listOf(
                StepCandidate(
                    id = "process.force-stop",
                    purpose = "Giết app đích có chủ ý để xem hệ thống xử lý ra sao",
                    commands = listOf("am force-stop {pkg}", "pidof {pkg}"),
                    evidence = "app chết; cụm và journal phản ứng đúng thay vì treo",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "KHÔNG dùng candidate này cho CP/AA — ràng buộc sản phẩm",
                ),
            ),
        ),
        CarStep(
            id = "power-state",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Ngủ/thức màn hình để thử ca head unit sleep-wake",
            precondition = "xe đỗ hoặc an toàn để màn tắt",
            candidates = listOf(
                StepCandidate(
                    id = "power.sleep-wake",
                    purpose = "Tắt rồi bật màn",
                    commands = listOf("input keyevent 223", "input keyevent 224", "dumpsys display"),
                    evidence = "sau khi thức, display cụm và phiên chiếu ở trạng thái xác định, không nửa vời",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "CHƯA thử: keyevent 223/224 (sleep/wakeup) có tác dụng trên head unit hay không",
                ),
            ),
        ),
        CarStep(
            id = "capture-state",
            feature = CarFeature.CLUSTER_CAST,
            purpose = "Chụp toàn bộ trạng thái hiển thị để so sánh giữa hai thời điểm",
            precondition = "không có",
            candidates = listOf(
                StepCandidate(
                    id = "capture.full-surface-flinger",
                    purpose = "Dump đầy đủ SurfaceFlinger + display, để so mốc chiếu-mở với chiếu-đóng",
                    commands = listOf(
                        "dumpsys SurfaceFlinger",
                        "dumpsys display",
                        "appops get {pkg}",
                    ),
                    evidence = "có đủ khối DisplayDevice của cụm kèm numLayers để đối chiếu hai trạng thái",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Mốc chiếu-ĐÓNG đã có: fixtures/sf-FULL-projection-CLOSED.txt, numLayers=0. " +
                        "Thiếu mốc chiếu-MỞ — đây là dữ liệu duy nhất còn cần để trả lời Q1",
                ),
            ),
        ),
    )
}
