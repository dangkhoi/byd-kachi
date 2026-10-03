package com.byd.clusternav.launcher

/**
 * Ba danh sách loại trừ KÈM LÝ DO của [LauncherI18nContractTest] — tách THUẦN (551 dòng → trần 500, L6-debt 2026-09-27).
 *
 * Lệ [SettingsCatalog.NOT_SETTINGS]: mỗi mục phải nói LÝ DO; ba bài *"không bị rữa"* trong lớp test vẫn quét cả ba danh sách
 * này (mục chết là chỗ người sau tưởng đã được canh). Thêm 2.76: hai mảnh nhật ký `overlay show` của `CameraOverlayView` (làn L2).
 */
internal object LauncherI18nAllowlists {
    /**
     * Chuỗi tiếng Việt được phép còn trong mã, **kèm lý do** (lệ [SettingsCatalog.NOT_SETTINGS]: danh sách loại trừ
     * phải bắt viết lý do, không thì nó thành chỗ làm im bài test).
     *
     * Khoá là **một mảnh** của chuỗi thật (đủ để nhận ra, ngắn để không rữa khi câu chữ đổi).
     */
    val allowed: Map<String, String> = mapOf(
        // ── NHẬT KÝ — người dùng không bao giờ đọc; dịch nhật ký làm hỏng việc grep khi gỡ lỗi trên xe ──
        "hỏng giữa lượt chạy" to "nhật ký (Log.w) khi một bước gói lệnh ném — không hiện trên màn",
        "bỏ việc nền vì màn đã huỷ" to "nhật ký (Log.w) của cửa nền — không hiện trên màn",
        "tiến trình chính sống" to "nhãn trong dòng nhật ký đo thời gian ack của VoiceWakeHomeRelay (Log.i) — không hiện trên màn",
        "tiến trình chính lạnh" to "nhãn trong dòng nhật ký đo thời gian ack của VoiceWakeHomeRelay (Log.i) — không hiện trên màn",
        "việc nền bị từ chối" to
            "nhật ký (Log.w) của [SOÁT P3-2]: việc nền bị từ chối nên id widget vừa cấp được nhả — không hiện trên màn",
        "bỏ việc cửa sổ vì thread nền đã tắt" to "nhật ký (Log.w) của LauncherWindows — không hiện trên màn",
        "lỗi: " to "chuỗi mã lỗi shell, đi thẳng vào nhật ký của vòng kiểm quyền",
        "tự cấp " to "nhật ký (Log.i) của vòng kiểm quyền — không hiện trên màn",
        "sau khi tự cấp" to "nhật ký (Log.i) của vòng kiểm quyền — không hiện trên màn",
        "bật nhưng chưa có ảnh" to "nhật ký (Log.i) của hình nền; câu hiện trên màn là `kachi_wall_no_photos`",
        "ảnh không giải mã được" to "nhật ký (Log.w) của hình nền — không hiện trên màn",
        "không đọc được thư mục ảnh" to "nhật ký (Log.w) của WallpaperStore — không hiện trên màn",
        "không giải mã được ảnh" to "nhật ký (Log.w) của WallpaperStore — không hiện trên màn",
        // ── CAMERA 2.76 (làn L2 · hình "theo cụm") — hai chuỗi ghép trong dòng nhật ký `overlay show …` của
        // `CameraOverlayView` (khung/dải/vị trí trên cụm, để grep khi đo xe: `dải=140,136-1780,560 tại=140,136`) ──
        "dải=" to "nhật ký (Log.i) `overlay show` của CameraOverlayView — dải cụm + toạ độ, để grep khi đo xe; không hiện trên màn",
        "vùng=" to "nhật ký (Log.i) `overlay show` của CameraOverlayView — cỡ vùng đo; không hiện trên màn",
        // ── ÂM BÁO (1.70) — đi qua `VoiceChime.trip(why)` → `Log.w`; câu chẩn đoán cầu chì, không hiện trên màn ──
        "AudioTrack không khởi tạo được" to "lý do cầu chì âm báo (VoiceChime.trip → Log.w) — không hiện trên màn",
        "play() ném" to "lý do cầu chì âm báo (VoiceChime.trip → Log.w) — không hiện trên màn",
        "play() mất \$startMs ms (> \$MAX_START_MS)" to
            "lý do cầu chì âm báo (VoiceChime.trip → Log.w) — không hiện trên màn",
        "bỏ qua id KHÔNG thuộc host này" to
            "nhật ký (Log.w) của chốt bảo vệ badge tốc-độ VietMap: id đem thu hồi mà không thuộc host của launcher " +
                "thì bỏ qua. Ca này chỉ tới từ dữ liệu hỏng nên nó là dấu vết để GREP khi gỡ lỗi trên xe, " +
                "không phải câu nói với người lái",

        // ── LỖI LẬP TRÌNH — chỉ nổ khi mã sai, người dùng không bao giờ thấy ──
        "HomeViewModelFactory chỉ tạo HomeViewModel" to
            "thông điệp ngoại lệ cho LẬP TRÌNH VIÊN (ViewModel sai kiểu) — không phải chữ trên màn",

        // ── KHOÁ LƯU BỀN — dịch là MẤT DỮ LIỆU ──
        "Mặc định" to
            "TÊN HỒ SƠ MẶC ĐỊNH = TIỀN TỐ KHOÁ LƯU (`\"<hồ sơ>__preset\"`). Dịch thành \"Default\" làm mọi khoá cũ " +
                "(`Mặc định__preset`, `Mặc định__slot_0`…) thành mồ côi ⇒ người dùng mở lên thấy mất sạch cấu hình " +
                "mà KHÔNG có gì báo lỗi. Xem KDoc `HomeUiState.DEFAULT_PROFILE`",
    )


    /**
     * Biểu thức `.label` / `.sub` được phép còn trong tầng vẽ, **kèm lý do**.
     *
     * ## ⚠⚠ Bệnh nó chữa — [ĐO] máy ảo 2026-09-12, bằng ẢNH chứ không bằng test
     * Sau khi 118 chuỗi đã vào tài nguyên và bộ chọn ngôn ngữ đã chạy, ảnh chụp màn Cài đặt cho thấy **cột nhóm bên
     * trái tiếng VIỆT trong khi cột nội dung bên phải tiếng ANH** — trên cùng một màn hình. Nguyên nhân:
     * `SettingsPanel` đọc `group.label` (nhãn **GỐC**, luôn tiếng Việt theo giao kèo [Localized.label]) thay vì
     * `group.displayLabel`. Bài canh "0 chuỗi viết cứng" **không thể** thấy lỗi này: trong mã `:app` không có một chữ
     * tiếng Việt nào cả, chữ nằm ở `:core` và đi vào qua một thuộc tính đọc sai.
     *
     * [ĐO] cùng lối đó có **9 chỗ**: `SettingsPanel` ×2 (nhãn + câu phụ của rail) · `SettingsRows` (tên đại lượng) ·
     * `SettingsSectionsHome` + `AppDrawer` (tên lĩnh vực) · `AppDrawer` (tên widget) · `ControlTileFactory` ×2 (nhãn ô
     * + câu báo của gói lệnh) · `WorkspaceView` (tên widget trong ô). Tức đây là một **họ lỗi**, không phải một chỗ
     * lỡ tay — nên phải canh bằng máy.
     *
     * ## Vì sao canh bằng VĂN BẢN chứ không bằng kiểu
     * Bộ quét đọc mã nguồn, không có bảng kiểu, nên nó không biết `x.label` là `Localized` hay không. Đảo lại thành
     * *"mọi `.label` đều đáng ngờ, ai đúng thì khai lý do"* thì phép kiểm **không bỏ sót** — giá phải trả là danh sách
     * dưới đây, mà mỗi dòng của nó lại là một câu trả lời hữu ích cho câu hỏi *"nhãn này dịch ở đâu"*.
     */
    val rawLabelAllowed: Map<String, String> = mapOf(
        "it.name to it.label" to
            "LayoutPreset · ImageFit · DockEdge — ba enum này TỰ dịch bên trong `:core` bằng `Strings.t`, nên " +
                "`label` của chúng ĐÃ theo ngôn ngữ (khác hẳn `Localized.label` vốn luôn là tiếng Việt gốc)",
        "it.name to it.label()" to "ThemeMode · LangMode — nhãn là HÀM, tự dịch bằng `Strings.t` bên trong `:core`",
        "m.label" to
            "`GroupCell`/`GroupActionCell` của `GroupBoard` — nhãn đã được `:core` điền từ `displayLabel`/" +
                "`displayShortLabel` lúc dựng bảng (xem `GroupBoard`), nên tầng vẽ chỉ chép lại",
        "cell.label" to "cùng lý do `m.label`: ô của `GroupBoard`, nhãn đã dịch từ trước khi tới tầng vẽ",
        // ⚠ Hai mục `c.label` (bảng sơ đồ bên) và `it.label} ·` (dòng chân bảng BOARD) đã gỡ 2026-09-16 cùng
        // `SideBoardView`/`RadarBoardView` — owner gỡ toàn bộ ADAS/an toàn nên hai ô vẽ đó không còn.
        "v.label" to
            "`TelemetryView` — `TelemetryReadout.of` điền `spec.displayLabel` vào đó, nên nhãn đã theo ngôn ngữ",
        "item.label" to "tên ứng dụng từ `PackageManager` — do HỆ THỐNG dịch, không phải chuỗi của dự án",
        "it.label," to "tên ứng dụng từ `PackageManager` (dựng `GridItem`) — cùng lý do `item.label`",
        "model.label" to
            "`SherpaModel.label` là TÊN RIÊNG của mô hình ASR (vd \"Zipformer VN (Apache-2.0, 70k h)\"), một danh " +
                "hiệu sản phẩm/giấy phép — KHÔNG phải `Localized.label` VI-gốc cần dịch. Cùng loại với `item.label` " +
                "(tên ứng dụng): danh từ riêng, không đổi theo ngôn ngữ giao diện",
        "pack.label" to
            "`VoicePack.label` — cùng lý do `model.label`, chỉ là dạng TỔNG QUÁT của nó (T8 gộp gói nghe + gói đọc " +
                "vào một hợp đồng). Vẫn là tên riêng: \"Piper VN — VAIS-1000 (medium)\"",
        // VOICE-HOTFIX 1.69 (H6 "đổi sang mô hình nhẹ") — thêm ba cách gọi của **cùng** `SherpaModel.label`, phải
        // khai riêng vì phép dò ở dưới so theo CHUỖI CON (`"model.label"` không phủ `model?.label`, dấu `?` chen
        // vào). ⚠ Hai mục `current.label` + `light.label` đã GỠ 2026-09-21 cùng khối chọn-mô-hình
        // (`VoiceModelSettings.lightModelRows`): danh mục mô hình nghe thu về một gói nên hai tên biến ấy không
        // còn tồn tại. Chính bài này bắt chúng ở lượt đó — đúng việc nó sinh ra để làm.
        "model?.label" to
            "cầu kiểm thử `state.voice_model.label`: một trường MÁY ĐỌC. Ở đây phải là tên riêng ỔN ĐỊNH, không " +
                "được đổi theo ngôn ngữ giao diện — nếu không thì một phép đo chạy ở máy tiếng Anh và một phép đo " +
                "ở máy tiếng Việt cho ra hai chuỗi khác nhau cho cùng một gói",
        "ttsPack.label" to
            "`SherpaTtsCatalog.TtsVoice.label` — tên riêng của gói giọng Piper, cùng lý do `model.label`",
        // U6 đã bỏ mục `"pick.sub"`: ngăn kéo nay đọc `pick.displaySub` (gợi ý loại + câu "gồm gì"), tức nó KHÔNG
        // còn chạm vào trường gốc nữa nên không cần được tha. Danh sách này phải tự rữa — giữ một dòng không còn ai
        // khớp là để dành sẵn một lỗ hổng cho lần sau.
        "def.args.size" to
            "SỐ LƯỢNG lựa chọn, không phải chữ — `displayArgs` lùi về `args` khi lệch số phần tử nên đếm trên `args` " +
                "là con số ổn định duy nhất; dùng `displayArgs.size` sẽ nói cùng con số nhưng che mất ý *đếm*",
    )


    /**
     * Chuỗi trần được phép đi vào bề mặt chữ — mẫu định dạng và ký hiệu, không phải câu chữ.
     *
     * ⚠ So bằng chuỗi **đã bỏ nội suy** (xem KDoc bài trên), nên mục `µg` phủ cả `"${'$'}{it}µg"`.
     */
    val uiLiteralAllowed: Map<String, String> = mapOf(
        "HH:mm" to "mẫu định dạng giờ của SimpleDateFormat — ký hiệu API, không phải chữ cho người đọc",
        "dd/MM" to "mẫu định dạng ngày (không có tên thứ nên không phụ thuộc ngôn ngữ)",
        // Mã icon tra trong `KachiTheme.iconRes` — định danh tài nguyên, không phải chữ cho người đọc.
        // S4 · R12 thêm `ic-apps`/`ic-settings`: hai pill của thanh trên nay CHỈ có icon, và tên hình được truyền
        // thẳng vào `pill(...)` (chữ đã chuyển sang `contentDescription` lấy từ `R.string`).
        // V1 pha NGHE thêm `ic-mic`: nút thứ ba của thanh trên, cùng khuôn chỉ-icon với hai nút kia.
        *listOf("ic-sun", "ic-grid", "ic-bolt", "ic-leaf", "ic-speed", "ic-tire", "ic-music", "ic-lock",
            "ic-apps", "ic-settings", "ic-mic")
            .map { it to "mã icon tra trong `KachiTheme.iconRes`, không phải chữ" }.toTypedArray(),
        // Ký hiệu đơn vị SI + tên chuẩn của chỉ số bụi — viết y hệt ở mọi ngôn ngữ, dịch là làm sai.
        *listOf("km/h", " km/h", " km", "µg", "µg · ", "µg/m³", "PM2.5 · ", "PM2.5 ")
            .map { it to "ký hiệu đơn vị / tên chuẩn quốc tế — không dịch" }.toTypedArray(),
    )

}
