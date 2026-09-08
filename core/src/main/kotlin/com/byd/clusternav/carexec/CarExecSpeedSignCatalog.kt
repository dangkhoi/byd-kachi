package com.byd.clusternav.carexec

internal object CarExecSpeedSignCatalog {
    val steps: List<CarStep> = listOf(
        CarStep(
            id = "sign-inventory",
            feature = CarFeature.SPEED_SIGN,
            purpose = "Liệt kê ứng viên sinh/vẽ biển báo: package, service, tiến trình",
            precondition = "không có",
            candidates = listOf(
                StepCandidate(
                    id = "sign-inventory.packages",
                    purpose = "Tìm app liên quan nhận diện biển, ADAS, cụm, HUD",
                    commands = listOf("pm list packages | grep -iE 'adas|tsr|sign|dvr|cluster|hud|hmi|meter|camera'"),
                    evidence = "ra danh sách package ứng viên, ghi lại để các step sau nhắm đúng",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                ),
                StepCandidate(
                    id = "sign-inventory.services",
                    purpose = "Tìm service hệ thống liên quan xe, cụm, HUD",
                    commands = listOf("service list | grep -iE 'vehicle|car|adas|tsr|hud|meter|cluster|byd|xdja'"),
                    evidence = "ra tên service ứng viên kèm descriptor",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                ),
                StepCandidate(
                    id = "sign-inventory.hal",
                    purpose = "Có Vehicle HAL kiểu Android Automotive không, hay OEM tự làm",
                    commands = listOf("lshal 2>/dev/null | grep -iE 'vehicle|automotive'", "dumpsys car_service 2>&1 | head -40"),
                    evidence = "biết được giá trị đi qua HAL chuẩn hay qua service riêng của OEM",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "DiLink3 là Android 10 thường (QKQ1.210910.001), rất có thể KHÔNG có car_service",
                ),
                StepCandidate(
                    id = "sign-inventory.processes",
                    purpose = "Tiến trình nào đang chạy liên tục — nhận diện biển phải chạy thường trú",
                    commands = listOf("ps -A -o PID,USER,NAME | grep -iE 'adas|tsr|sign|dvr|meter|cluster|hud'"),
                    evidence = "ra pid để soi dumpsys và logcat theo pid",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                ),
            ),
        ),
        CarStep(
            id = "sign-watch-live",
            feature = CarFeature.SPEED_SIGN,
            purpose = "Bắt giá trị giới hạn tốc độ lúc nó ĐỔI, khi xe đi qua biển thật",
            precondition = "xe đang chạy, đi qua ít nhất hai biển khác số",
            candidates = listOf(
                StepCandidate(
                    id = "sign-watch.logcat-keywords",
                    purpose = "Xoá log, chạy qua biển, rồi lọc từ khoá",
                    commands = listOf(
                        "logcat -c",
                        "logcat -d -v time | grep -iE 'speed.?limit|speedlimit|tsr|traffic.?sign|isa|slif|limit=|maxspeed'",
                    ),
                    evidence = "có dòng log chứa số trùng với biển vừa đi qua",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Người phải nhớ biển vừa qua là bao nhiêu để đối chiếu; đây là mắt xích quyết định",
                ),
                StepCandidate(
                    id = "sign-watch.logcat-raw-window",
                    purpose = "Không lọc gì, lấy nguyên cửa sổ log để soi off-car",
                    commands = listOf("logcat -c", "logcat -d -v threadtime"),
                    evidence = "file log đủ lớn để tìm mẫu số đổi theo thời điểm qua biển",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Dùng khi lọc từ khoá ra rỗng — OEM có thể đặt tên khác hẳn",
                ),
                StepCandidate(
                    id = "sign-watch.props-diff",
                    purpose = "Giá trị có nằm trong system property không",
                    commands = listOf("getprop | grep -iE 'speed|limit|tsr|sign|adas'"),
                    evidence = "chụp hai lần ở hai vùng biển khác nhau và thấy property đổi",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                ),
                StepCandidate(
                    id = "sign-watch.settings-diff",
                    purpose = "Giá trị có nằm trong settings không",
                    commands = listOf("settings list global | grep -iE 'speed|limit|tsr|sign|adas'"),
                    evidence = "chụp hai lần ở hai vùng biển khác nhau và thấy key đổi",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                ),
            ),
        ),
        CarStep(
            id = "sign-consumer",
            feature = CarFeature.SPEED_SIGN,
            purpose = "Ai VẼ biển lên cụm và HUD — đó là đích cần gửi vào",
            precondition = "đã có danh sách package/service ứng viên",
            candidates = listOf(
                StepCandidate(
                    id = "sign-consumer.receivers",
                    purpose = "Package ứng viên khai báo receiver nào, action nào",
                    commands = listOf("dumpsys package {pkg} | grep -A3 -iE 'receiver|action'"),
                    evidence = "ra action broadcast mà bên vẽ đang nghe",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                ),
                StepCandidate(
                    id = "sign-consumer.service-dump",
                    purpose = "Service ứng viên tự dump trạng thái không",
                    commands = listOf("dumpsys activity service {pkg}", "dumpsys {svc}"),
                    evidence = "dump có trường giới hạn tốc độ hiện hành",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Bài học từ Cluster Cast: dumpsys của service OEM có thể trả rỗng hoàn toàn",
                ),
                StepCandidate(
                    id = "sign-consumer.descriptor",
                    purpose = "Lấy descriptor AIDL của service để biết đường ghi",
                    commands = listOf("service call {svc} 1598968902"),
                    evidence = "đọc được tên interface, suy ra khả năng có setter",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Cách này đã dùng được với AutoContainer, ra android.os.IAutoContainer…",
                ),
            ),
        ),
        CarStep(
            id = "sign-source-vietmap",
            feature = CarFeature.SPEED_SIGN,
            purpose = "Lấy giới hạn tốc độ ra khỏi VietMap mà không phải đọc ảnh",
            precondition = "VietMap đang dẫn đường trên đoạn có giới hạn",
            candidates = listOf(
                StepCandidate(
                    id = "sign-source.notification",
                    purpose = "Giới hạn có nằm trong notification của VietMap không",
                    commands = listOf("dumpsys notification --noredact | grep -A40 vietmap"),
                    evidence = "extras của notification chứa số giới hạn",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Rẻ nhất và dùng lại được đường NavNotificationListener đã có sẵn trong app",
                ),
                StepCandidate(
                    id = "sign-source.exported-surface",
                    purpose = "VietMap có phát ra broadcast hay mở provider nào không",
                    commands = listOf("dumpsys package vn.vietmap.live | grep -iE 'exported=true|provider|receiver' "),
                    evidence = "có bề mặt đọc được mà không cần quyền đặc biệt",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                ),
                StepCandidate(
                    id = "sign-source.logcat",
                    purpose = "VietMap tự ghi log giới hạn không",
                    commands = listOf("logcat -c", "logcat -d | grep -iE 'vietmap.*(speed|limit)|limit.*vietmap'"),
                    evidence = "log chứa số đúng bằng biển đang đi qua",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.READ_ONLY,
                ),
                StepCandidate(
                    id = "sign-source.screen-crop",
                    purpose = "Cách cuối: chụp vùng badge giới hạn rồi đọc số off-car",
                    commands = listOf("screencap -d {display} -p /sdcard/vietmap-limit.png"),
                    evidence = "ảnh có badge rõ, đọc được số bằng mắt hoặc OCR ngoài xe",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Chỉ dùng nếu ba cách trên rỗng: đọc ảnh thì trễ và dễ sai, đúng thứ đang muốn bỏ",
                ),
            ),
        ),
        CarStep(
            id = "sign-mute-camera",
            feature = CarFeature.SPEED_SIGN,
            purpose = "Tắt đường đọc biển bằng camera để hai nguồn không tranh nhau",
            precondition = "đã biết ai sinh giá trị; XE ĐỖ",
            candidates = listOf(
                StepCandidate(
                    id = "sign-mute.settings-key",
                    purpose = "Tắt bằng đúng khoá setting nếu OEM có khai",
                    commands = listOf("settings get global {key}", "settings put global {key} 0"),
                    evidence = "cụm ngừng hiện biển do camera đọc, và đặt lại giá trị cũ thì hiện lại",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                ),
                StepCandidate(
                    id = "sign-mute.appops-camera",
                    purpose = "Chặn quyền camera của riêng app nhận diện",
                    commands = listOf("appops get {pkg} CAMERA", "appops set {pkg} CAMERA ignore"),
                    evidence = "app mất nguồn ảnh nên ngừng sinh giá trị; trả quyền thì chạy lại",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "Phải kiểm cùng app đó có lo việc khác không, ví dụ camera lùi hay cảnh báo làn",
                ),
                StepCandidate(
                    id = "sign-mute.pm-disable",
                    purpose = "Vô hiệu hoá cả app nhận diện",
                    commands = listOf("pm disable-user --user 0 {pkg}", "pm enable {pkg}"),
                    evidence = "biển camera tắt hẳn; bật lại được về nguyên trạng",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_HANG_SYSTEM,
                    fieldNote = "Rủi ro cao nhất trong nhóm: app đó có thể là một phần của HMI cụm. Chỉ khi đỗ",
                ),
            ),
        ),
        CarStep(
            id = "sign-inject",
            feature = CarFeature.SPEED_SIGN,
            purpose = "Ghi giá trị của mình vào đúng đường mà cụm/HUD đang nghe",
            precondition = "đã biết đường và đích; XE ĐỖ ở lần thử đầu",
            candidates = listOf(
                StepCandidate(
                    id = "sign-inject.sla-state-probe",
                    purpose = "ĐỌC (không ghi) trạng thái nhận diện biển báo hiện tại qua BYDAutoADASDevice — bước bắt buộc TRƯỚC mọi ghi",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen getraw adas 31600025",
                    ),
                    evidence = "trả về một trong 0(tắt)/1(fusion)/2(vision)/3(nv-only)/4(defect), không phải lỗi/exception",
                    verdictSource = VerdictSource.MEASURED,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "RE 2026-07-29 (carsettings-apk TrafficSign.java + jadx-tmap BYDAutoADASDevice.java): feature-id " +
                        "ADAS_SLA_STATE=0x31600025=828375077, đối chiếu chéo giữa BYDAutoFeatureIds thật (firmware) và call site " +
                        "TrafficSign.java khớp nhau. Đây là CÔNG TẮC BẬT/TẮT CẢ TÍNH NĂNG TSR (5 giá trị enum), KHÔNG PHẢI nơi " +
                        "ghi một con số km/h tuỳ ý — đừng kỳ vọng set giá trị này = hiện được số giới hạn tốc độ tuỳ chọn.",
                ),
                StepCandidate(
                    id = "sign-inject.sla-state-toggle",
                    purpose = "Bật/tắt toàn bộ tính năng TSR qua ADAS_SLA_STATE — xem có ảnh hưởng gì tới cụm/HUD không",
                    commands = listOf(
                        "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen setraw adas 31600025 1",
                    ),
                    evidence = "NHÌN cụm/HUD: biểu tượng TSR (nếu có) bật/tắt theo; đọc lại bằng sign-inject.sla-state-probe để xác nhận giá trị đã ghi",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                    fieldNote = "Toggle một tính năng an toàn thật (dù chỉ on/off) trong lúc xe đang chạy — chỉ thử khi đỗ ở lần đầu, " +
                        "và trả lại giá trị đọc được từ sign-inject.sla-state-probe ngay sau khi quan sát xong.",
                ),
                StepCandidate(
                    id = "sign-inject.broadcast",
                    purpose = "Gửi broadcast đúng action đã tìm ra",
                    commands = listOf("am broadcast -a {key} --ei value {value}"),
                    evidence = "cụm hoặc HUD hiện đúng số vừa gửi",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_DISRUPT_DRIVER,
                ),
                StepCandidate(
                    id = "sign-inject.service-call",
                    purpose = "Gọi thẳng setter của service nếu descriptor có",
                    commands = listOf("service call {svc} {key} i32 {value}"),
                    evidence = "cụm hoặc HUD hiện đúng số vừa gửi",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.MAY_HANG_SYSTEM,
                    fieldNote = "Gọi transaction lạ trên service hệ thống là chỗ dễ làm treo nhất; chỉ khi đỗ",
                ),
                StepCandidate(
                    id = "sign-inject.settings-key",
                    purpose = "Ghi vào chính khoá mà bước watch thấy đổi theo biển",
                    commands = listOf("settings put global {key} {value}"),
                    evidence = "cụm hoặc HUD hiện đúng số vừa ghi",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                ),
            ),
        ),
        CarStep(
            id = "sign-stale-guard",
            feature = CarFeature.SPEED_SIGN,
            purpose = "Ngừng gửi thì giá trị cũ có dính lại mãi không",
            precondition = "đã ghi được một giá trị vào cụm/HUD",
            candidates = listOf(
                StepCandidate(
                    id = "sign-stale.stop-sending",
                    purpose = "Ngừng gửi rồi chờ, xem số cũ có tự mất",
                    commands = listOf("sleep 60"),
                    evidence = "số cũ tự mất, hoặc dính lại — cả hai đều là kết quả cần biết",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.READ_ONLY,
                    fieldNote = "Số dính lại là nguy hiểm nhất: tài xế tin một giới hạn đã hết hiệu lực từ lâu",
                ),
                StepCandidate(
                    id = "sign-stale.clear-value",
                    purpose = "Có cách xoá hiển thị không, hay chỉ ghi được số",
                    commands = listOf("am broadcast -a {key} --ei value 0", "settings put global {key} 0"),
                    evidence = "hiển thị trở về trạng thái không có giới hạn",
                    verdictSource = VerdictSource.HUMAN,
                    risk = CandidateRisk.REVERSIBLE,
                ),
            ),
        ),

        // ── Chính sách phát lại chuỗi mở chiếu ───────────────────────────────────────────────────────
        //
        // Câu hỏi chặn đường app: khi nào được phát 30,16,35?
        //
        // CẬP NHẬT 2026-07-29: điều kiện "durable envelope pristine epoch 0" đã bị bỏ khỏi
        // CastColdBootstrapPreflight.inspect — epoch 0 trước giờ chỉ TRÙNG với "chưa ghi gì", không phải
        // sự thật cần kiểm. Nay V2 phát chuỗi này mỗi khi (a) envelope không còn ghi gì
        // (stableSession/transaction/stopRequested/pendingIntent/adjustmentDraft/pendingUiRollback đều
        // rỗng) VÀ (b) dumpsys display KHÔNG tìm thấy display cụm nào. Nếu display cụm đã có mà đang
        // trống thì adopt (không phát lại); nếu nó đang có task thì preflight chặn hẳn. Nghĩa là ca nguy
        // hiểm mà V1 cảnh báo (phát lại lúc cụm ĐANG có app) vẫn không thể xảy ra qua đường app.
        //
        // V1 dùng luật khác: cụm chưa có app thì phát, cụm đang có app thì hot-swap, KHÔNG phát lại. V1 ghi
        // tại chỗ rằng phát lại lúc cụm đang có app gây WM NPE và treo head unit, có kiểm 2026-07-23.
        //
        // KHÔNG lấy đó làm chân lý. V1 cũng sai nhiều chỗ, và lời ghi trong mã không phải bằng chứng. Vì
        // thế đây là step riêng để tự kiểm, và tách từng opcode để biết CHÍNH XÁC cái nào nguy hiểm thay vì
        // cấm cả chuỗi vì một câu chú thích.
    )
}
