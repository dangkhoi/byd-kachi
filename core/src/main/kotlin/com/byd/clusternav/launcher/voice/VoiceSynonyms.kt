package com.byd.clusternav.launcher.voice

/**
 * ═══ V1 · TỪ ĐỒNG NGHĨA — KHAI **MỘT CHỖ** ════════════════════════════════════════════════════════════════════
 *
 * Tách khỏi `VoiceGrammar.kt` ngày 2026-09-16 (vòng H3/H4): bảng này nhận thêm cách gọi **giọng Nam** và cách nói
 * **tên app theo âm Việt**, và tệp cũ chạm trần 500 dòng (CLAUDE.md §4.1 · `VoiceCommandWiringContractTest`).
 * Tách theo VAI, không cắt cho đủ số: tệp kia nói về *cách dựng từ vựng*, tệp này chỉ là **dữ liệu cách gọi**.
 *
 * ## Vì sao ở đây mà không rải vào từng dòng registry
 * `ControlRegistry`/`TelemetryRegistry` là **hợp đồng với màn hình**: `label` là chữ hiện trên nút, và hàng trăm
 * bài test đang assert đúng chuỗi đó (KDoc `Localized.label`). Nhét thêm một danh sách "còn gọi là…" vào mỗi dòng
 * sẽ (a) làm 188 dòng dữ liệu phình ra vì một tính năng duy nhất dùng tới, và (b) mời người sau đặt cách-gọi-miệng
 * vào ô `label` cho tiện — tức đổi chữ trên nút. Ở đây thì nhãn vẫn là nhãn, cách nói là cách nói.
 *
 * ## Luật khai
 *  • **Chỉ khai cái mà nhãn KHÔNG phủ.** Nhãn *"Đèn đọc"* đã tự khớp, không cần khai lại — [VoiceGrammar] sinh cụm
 *    từ nhãn VI/EN/ngắn của **mọi** dòng.
 *  • Viết **không dấu, chữ thường** — cùng dạng [VoiceLexicon.deaccent] trả về, để khỏi có hai luật chuẩn hoá.
 *  • Cụm trùng nhau giữa hai mã là **hợp lệ**: [VoiceIntentParser] chọn theo loại động từ (xem KDoc ở đó).
 *  • Mọi cụm khai ở đây **phải** có dạng có dấu ở [SherpaSpokenWords.ACCENTED] hoặc được khai là không có
 *    ([SherpaSpokenWords.NO_VI_FORM]) — `SherpaBiasingCoverageTest` ép bằng máy.
 */
object VoiceSynonyms {

    /**
     * Cách nói thêm cho NÚT (`ControlRegistry`).
     *
     * ## Vòng 2026-09-16 · giọng NAM — vì sao đây là lỗi TỪ VỰNG, không phải lỗi mô hình
     * [ĐO] `docs/diagnostics/voice-mishear-2026-09-16.md` §4: vùng `nam` đúng **21,8 %** so với `chung` 53,1 %;
     * §6 xếp loại các mã hụt là **TỪ VỰNG** — tức *chính câu gốc* (chữ đúng 100 %) cũng không trỏ về đúng mã.
     * Sửa bằng bảng này, không cần đụng mô hình. Nguồn chữ: `scripts/voice/data/nouns.tsv` cột `region = nam`
     * (người soạn tay, mỗi dòng đã gắn sẵn một mã) — **không** lấy từ chuỗi mô hình nghe nhầm.
     *
     * ### Luật LOẠI khi nhập (giống hệt luật của [APP_TARGETS], xem KDoc ở đó)
     *  1. mã phải **có thật** trong `ControlRegistry`/`TelemetryRegistry` — `ac_on`, `wiper_state`,
     *     `sunroof_state`, `tailgate_status`, `mac_*`, `launcher_*` là mã của **bộ khác** ⇒ bỏ;
     *  2. cụm mà **nhãn đã phủ** thì bỏ (*"máy lạnh"*, *"gạt nước"*, *"nóc xe"*, *"tiếng"*, *"sáng màn"*,
     *     *"chế độ chạy"*, *"đang chạy bao nhiêu"* — đã có sẵn ở dưới hoặc trùng nhãn);
     *  3. cụm **một từ trùng từ thường** thì bỏ: `volt_12v ← "bình"` (bình thường / bình tĩnh / bình xăng) —
     *     một bí danh một từ như thế cướp câu của người khác mà **im lặng**;
     *  4. cụm biến một hotword NGẮN đã đo thành **tiền tố** thì bỏ. [SherpaHotwords.dropPrefixes] bỏ mọi dòng là
     *     tiền tố theo từ của dòng khác, nên `soc ← "pin còn nhiêu"` sẽ giết đúng dòng `XEM PIN` — câu được đo
     *     nhiều nhất của cả dự án — và `ac_auto ← "máy lạnh tự động"` giết `BẬT MÁY LẠNH`. Cả hai đều đã bị nhãn
     *     phủ sẵn (luật 2), nên cái giá phải trả là **không có gì** đổi lấy một hồi quy im lặng.
     */
    val CONTROL: Map<String, List<String>> = mapOf(
        // ⚠ **KHÔNG** khai `"khoa cua xe"` ở đây, dù giọng Nam nói *"tắt khoá cửa xe đi"*. [ĐO 2026-09-16, lượt
        // sinh tệp hotword đầu tiên sau khi nhập từ vựng Nam bộ]: thêm nó đẻ ra dòng `MỞ KHÓA CỬA XE`, và
        // [SherpaHotwords.dropPrefixes] lập tức **nuốt mất** `MỞ KHÓA CỬA` — một trong bốn cụm đã ĐO là hay
        // nghe sai nhất (`SherpaBiasingCoverageTest` bắt được ngay, đúng vai của bài canh ấy). Cái giá đổi lấy
        // là 0: luật khớp **dãy dài nhất** của [VoiceGrammar.matchAt] tìm `khoa cua` ngay ở vị trí 0 của câu
        // *"khoá cửa xe"*, chữ `xe` thừa ra không đổi kết quả. Tức cụm dài này **không thêm câu nào nói được**,
        // nó chỉ phá một cụm đã đo. Đây là bẫy "tiền tố giết tiền tố" thứ ba của phiên — hai cái kia
        // (`pin còn nhiêu`, `máy lạnh tự động`) bị chặn ngay lúc nhập, cái này lọt vì nó chạm một cụm ở **bộ
        // đăng ký khác** (`door`) chứ không phải cụm của chính nó.
        "lock" to listOf("khoa xe", "khoa cua", "lock car", "central lock"),
        "door" to listOf("mo khoa", "mo khoa xe", "mo khoa cua", "unlock", "unlock car", "chot cua", "mo cua xe"),
        "trunk" to listOf("cop", "cop xe", "boot", "tailgate", "cua hau", "thung sau"),
        // *"đang đọc sách"* = chuỗi mô hình in ra cho *"đèn đọc sách"* — [ĐO XE 2026-09-16, DL3 bản 1.68, ×2]
        // (bằng chứng mạnh hơn corpus host). NHẬN vì nó là **cụm ba từ**, không phải chữ `đang` đứng trần: không
        // câu lệnh xe nào chứa đúng dãy *"đang đọc sách"*, nên nó không cướp được câu nào. Chữ `đang` một mình thì
        // **BỊ LOẠI** theo đúng luật §2 dưới (một từ đời thường ⇒ nuốt câu người khác mà im lặng).
        "readl" to listOf("den trong xe", "den doc sach", "cabin light", "den tran", "den noc", "dang doc sach"),
        "pm25" to listOf("loc bui", "loc khong khi", "air filter", "purifier",
            "may loc khong khi", "may loc bui", "loc gio cabin",
            // [ĐO xe 2026-09-17 · log] «tắt bụi mịn» ra `OpenApp(Maps)`: *"bụi mịn"* chỉ khớp `pm25_level`
            // (telemetry, chỉ-đọc) ⇒ TẮT nó = MISMATCH ⇒ rơi xuống "mở app". Cho NÚT lọc nhận *"bụi mịn"* để
            // *"tắt/bật bụi mịn"* điều khiển máy lọc; câu HỎI *"bụi mịn bao nhiêu"* vẫn về telemetry (read/action tách).
            "bui min"),
        // *"quạt ghế"* / *"làm mát ghế"* đứng cạnh `fan ← "quat"`: luật **dãy dài nhất thắng** giữ đúng nút ghế,
        // và *"quạt"* một mình vẫn là quạt gió — không cần một dòng `if` nào.
        "seatc" to listOf("thoi ghe", "ghe thoang", "seat cooling", "quat ghe", "lam mat ghe", "thong gio ghe"),
        "seath" to listOf("suoi ghe", "ghe am", "ghe nong"),
        "temp" to listOf("nhiet do dieu hoa", "nhiet do trong xe", "cabin temperature",
            "nhiet do may lanh", "do lanh"),
        "fan" to listOf("quat", "quat gio", "toc do quat", "suc gio", "blower", "muc gio"),
        "defrost" to listOf("say kinh truoc", "xa bang", "say kieng"),
        "cam" to listOf("camera", "camera 360 do", "camera quanh xe", "cam ba sau muoi"),
        "sunroof" to listOf("noc xe", "cua noc", "cua so noc"),
        "headl" to listOf("den chieu xa", "high beam", "den cot pha"),
        "recirc" to listOf("gio trong", "tuan hoan trong", "recirc"),
        "vol" to listOf("tieng", "am thanh", "volume"),
        // [SOÁT P2] *"dừng chiếu"* — người ta bỏ chữ *"cụm"*. Một từ `chieu` là đủ vì luật **dãy dài nhất thắng**
        // giữ nguyên mọi cụm dài hơn có chứa nó (*"chiếu cụm"*, *"đèn chiếu xa"*), nên không nuốt nhãn nào.
        "cast" to listOf("chieu", "chieu len cum", "chieu man", "cast cluster"),
        // ⚠ 1.85 · nhãn nút này đổi *"Điều hòa AUTO"* → *"Gió tự động"* ([ĐO xe §4] xe không có nhiệt-auto), nhưng
        // các cụm *"điều hoà"/"máy lạnh"* **Ở LẠI ĐÂY** có chủ ý: đó là nút điều hoà DUY NHẤT bật/tắt được (không có
        // control nào cho `getAcStartState`), và owner đã chốt ở 1.82 rằng *"bật/tắt điều hoà"* (không kèm số độ) là
        // nút này. Bỏ chúng đi thì câu người ta hay nói nhất về điều hoà thành NO_OBJECT. Câu có *"<số> độ"* vẫn rẽ
        // sang nút `temp` ở `VoiceControlParse` (fix (c) 1.82) — đường đó không đụng tới nhãn.
        "ac_auto" to listOf("dieu hoa", "may lanh", "dieu hoa tu dong", "air con", "ac", "aircon", "gio auto"),
        // ═══ D (owner test xe 2026-09-19) · CỤM MƠ HỒ TRỎ VỀ **MỘT** KÍNH, KHÔNG PHẢI CẢ BỐN ══════════════
        // Owner nói *"mở kính"* và xe hạ **cả 4**. Đây là hồi quy của chính bản vá [SOÁT P2] trước đó: lúc ấy
        // *"mở kính"* / *"mở cửa sổ"* chưa trỏ tới đâu (ra MISMATCH), nên bốn cụm mơ hồ được gắn vào nút GỘP với
        // lập luận *"nó thuộc diện CONFIRM nên người lái còn thấy hộp hỏi lại"*. Lập luận đó **sập** ở mặc định
        // thật: `voice_confirm_ids` mặc định **RỖNG** (owner chốt *"không hỏi xác nhận gì cả"*), nên không có hộp
        // nào hiện — câu mơ hồ nhất đi thẳng tới việc rộng nhất.
        //
        // Nguyên tắc chọn: cụm **mơ hồ** ⇒ phạm vi **HẸP NHẤT** hợp lý (kính lái = chỗ người nói đang ngồi, và
        // `window` tier PROVEN); muốn cả bốn thì phải nói TƯỜNG MINH (*"hết / toàn bộ / mọi / bốn kính"*). Hẹp
        // đoán sai thì thiếu một việc, còn rộng đoán sai thì **hạ ba cửa kính không ai xin** — hai cái giá không
        // cùng hạng.
        //
        // ⚠ [SOÁT lượt D · P2] Chính nguyên tắc trên khoanh lại phạm vi của lượt dời: nó áp cho cụm **MƠ HỒ**, mà
        // *"các cửa sổ"* và *"windows"* thì **không** mơ hồ — `các` là dấu hiệu SỐ NHIỀU của tiếng Việt và `windows`
        // là dạng số nhiều tiếng Anh, tức cả hai đã **tường minh-nhiều-cửa** y như *"bốn kính"*. Bản vá D đầu dời cả
        // bốn cụm sang [window], nên *"mở các cửa sổ"* chỉ hạ MỘT cửa — không phải cái giá rẻ hơn, chỉ là cái sai đổi
        // chiều. Ở lại đây đúng hai cụm số nhiều; hai cụm mơ hồ (`kinh` · `cua so`) sang [window].
        //
        // Luật **dãy dài nhất thắng** giữ hai bên không cướp nhau: *"mở các cửa sổ"* khớp `cac cua so` (3 từ) ở vị
        // trí 1 nên nó thắng `cua so` (2 từ) ở vị trí 2 — bài canh `VoiceWindowScopeTest` khoá đúng cặp này.
        "windows_all" to listOf("het kinh", "toan bo kinh", "moi kinh", "every window",
            "het kieng", "bon kinh", "cac cua so", "windows"),
        // ═══ V3 · R10 — *"mở kính lái"*, câu [ĐO xe 2026-09-16] mà máy hiểu SAI ═══════════════════════
        // Owner nói *"mở kính lái"*; sherpa nghe **đúng**, nhưng từ vựng không có cụm nào bắt đầu bằng `kinh lai`
        // ⇒ luật dãy-dài-nhất chỉ còn `kinh` ⇒ trỏ về nút GỘP `windows_all` ⇒ hộp *"Hạ hết 4 kính?"*. Tức một câu
        // chỉ về MỘT cửa kính lại thành lệnh cho bốn.
        //
        // ⚠ Hai cụm cuối (`kinh` · `cua so`) dời từ `windows_all` sang đây ở lượt D 2026-09-19 — xem khối ghi chú
        // ngay trên. Chỉ **hai**, không phải bốn: `cac cua so` / `windows` là dạng SỐ NHIỀU nên ở lại nút gộp
        // ([SOÁT lượt D · P2], lý do đầy đủ ghi ở khối `windows_all`). Luật **dãy dài nhất thắng** giữ nguyên mọi
        // cụm dài hơn: *"mở hết kính"* vẫn về nút gộp vì `het kinh` (2 từ) thắng `kinh` (1 từ) tại cùng vị trí.
        "window" to listOf("kinh lai", "cua kinh lai", "kinh tai xe", "cua so lai",
            "kieng lai", "cua kieng lai", "kieng tai xe",
            "kinh", "cua so"),
        "win_lf" to listOf("kinh ben lai", "kinh ghe lai", "kieng truoc trai"),
        "win_rf" to listOf("kinh ben phu", "kinh ghe phu", "kieng truoc phai"),
        "win_lr" to listOf("kieng sau trai", "kinh sau ben trai"),
        "win_rr" to listOf("kieng sau phai", "kinh sau ben phai"),
        "brightness_gear" to listOf("do sang man hinh", "sang man"),
        "pm25_clean_now" to listOf("loc khong khi ngay", "clean air now", "loc nhanh"),
        // ⚠ 1.85: `hood` ("nap ca po"/"nap may") đã xoá cùng mã — xe không có ca-pô điện ([ĐO xe 2026-09-20 §4]).
        // Không để lại cách nói mồ côi: `VoiceGrammarCoverageTest` đòi mọi cụm trỏ về một mã có thật.
        "defrost_rear" to listOf("say kieng sau"),
        "anion" to listOf("khu mui"),
        "steer_heat" to listOf("vo lang nong"),
        "sunshade" to listOf("rem noc", "man che nang"),
        // 1.85 · khoá trẻ em nay có HAI nút (trái/phải — [ĐO xe 2026-09-20 §3] RE cả hai id).
        // Cụm **MƠ HỒ** (*"khoá trẻ em"*, *"khoá con nít"* — không nêu bên) trỏ về nút TRÁI: đúng tiền lệ owner đã
        // duyệt ở 1.80 cho *"mở kính"* → kính LÁI (`window`), thay vì hỏi lại hay tự ý bắn cả hai bên. Nhãn của nút
        // nói rõ *"trái"* nên câu trả lời đọc lên không giấu chuyện nó chỉ khoá một bên.
        "child_lock" to listOf("khoa con nit", "khoa tre em", "khoa tre em ben trai"),
        "child_lock_r" to listOf("khoa con nit ben phai", "khoa tre em ben phai"),
        "seat_memory" to listOf("luu vi tri ghe"),
        "headlight_mode" to listOf("kieu den pha"),
        "wireless_charge" to listOf("sac dien thoai"),
        "screen_rotation" to listOf("huong man hinh"),
        "camera_view" to listOf("huong camera"),
        "cluster_music" to listOf("nhac tren dong ho"),
        // ── Pha NGHE (R10): nhãn có CHỮ VIẾT TẮT / CHỮ SỐ thì mô hình tiếng Việt không có từ để nghe ──
        // [ĐO] 2026-09-14, từ điển `vosk-model-small-vn-0.4` (19.529 mục): `ev` · `hev` KHÔNG có mặt ⇒ nút này
        // trước đó **gõ được mà không nói được**, và cái thiếu ấy im lặng.
        "powertrain_mode" to listOf("che do dong co", "xang dien", "che do nang luong"),
    )

    /** Cách nói thêm cho THÔNG TIN ĐỌC (`TelemetryRegistry`) — cùng luật nhập với [CONTROL]. */
    val TELEMETRY: Map<String, List<String>> = mapOf(
        "soc" to listOf("pin", "phan tram pin", "muc pin", "battery", "state of charge"),
        "ev_range_km" to listOf("tam hoat dong", "di duoc bao xa", "con di duoc bao nhieu", "range",
            "con chay duoc bao nhieu"),
        "fuel_range_km" to listOf("xang con chay duoc bao xa"),
        // [ĐO xe 2026-09-18 · log] «chỉ số xăng» ra Unknown và «xăng còn bao nhiêu» cũng vậy: chữ *"xăng"* đứng
        // trần không trỏ tới đâu (nhãn là *"Mức xăng"* / *"Tầm hoạt động xăng"*, đều cần từ thứ hai). Cụm MỘT từ
        // ở đây an toàn theo đúng luật §3 của KDoc: *"xăng"* không phải từ đời thường đa nghĩa, và đường NAV
        // không tra từ vựng nên *"chỉ đường đến trạm xăng gần nhất"* (có bài canh) không bị đụng. Luật dãy dài
        // nhất thắng giữ nguyên *"tầm hoạt động xăng"* → `fuel_range_km`.
        "fuel_pct" to listOf("xang", "nhien lieu", "muc nhien lieu"),
        "speed" to listOf("dang chay bao nhieu", "van toc"),
        "ext_temp" to listOf("nhiet do ngoai troi", "ngoai troi", "outside temperature", "ngoai troi nong khong"),
        // [ĐO xe 2026-09-17 · log] «nhiệt độ đang bao nhiêu» ra RỖNG (không datum), «máy lạnh bao nhiêu độ» ra
        // media_vol: *"nhiệt độ"* chỉ khớp NÚT `temp`, không có telemetry nào; *"máy lạnh"* chỉ khớp `ac_auto`.
        // `inside_temp` = nhiệt AC ĐANG ĐẶT ("Nhiệt cài đặt") ⇒ đúng câu hỏi. Câu HỎI về telemetry, câu LỆNH
        // *"tăng nhiệt độ"* vẫn về nút `temp` (choose ưu tiên control cho động từ hành động).
        "inside_temp" to listOf("nhiet do", "may lanh", "nhiet do may lanh", "dieu hoa bao nhieu do"),
        "cabin_temp" to listOf("nhiet trong xe", "nhiet do trong cabin"),
        // [ĐO xe 2026-09-18 · log] *"quạt gió đang mất máy"* + *"quạt điều hòa đang mất máy"* (2 lượt, cùng người)
        // = *"quạt gió đang **mức mấy**"* nghe rụng chữ. Câu ra MISMATCH vì *"quạt gió"* chỉ khớp NÚT `fan`, còn
        // datum `ac_wind` mang nhãn *"Mức quạt gió"* nên nó cần từ *"mức"* mới khớp — mà không ai nói đủ chữ ấy.
        // Cụm trùng với nút `fan` là **hợp lệ** và là cơ chế đã có: `VoiceIntentParser.choose` lấy datum cho động
        // từ ĐỌC, lấy nút cho động từ hành động ⇒ *"tăng quạt gió"* vẫn là nút (cùng khuôn `inside_temp` ↔ `temp`).
        "ac_wind" to listOf("quat gio", "quat dieu hoa", "muc gio", "toc do quat"),
        "pm25_level" to listOf("bui min", "chat luong khong khi", "air quality"),
        "odometer" to listOf("so km da di", "odo", "mileage"),
        "tyre_p_fl" to listOf("ap suat lop truoc trai", "hoi banh truoc trai"),
        "tyre_p_fr" to listOf("ap suat lop truoc phai", "hoi banh truoc phai"),
        "tyre_p_rl" to listOf("ap suat lop sau trai", "hoi banh sau trai"),
        "tyre_p_rr" to listOf("ap suat lop sau phai", "hoi banh sau phai"),
        "gear" to listOf("can so"),
        "vin" to listOf("so khung"),
        // ── Pha NGHE (R10) — nhãn mang `PM2.5` · `SOH` · `MCU` · `12V` · `50km` · `%` · `drift`, mà mô hình tiếng
        // Việt không có từ nào trong số đó. [ĐO] 2026-09-14.
        // ⚠ `pm25_value` KHÔNG lấy cụm `"bui min"` — cụm ấy đã thuộc `pm25_level` (mức 0–3); hai datum khác nhau
        // mà cùng một cách gọi thì câu *"xem bụi mịn"* trở thành xổ số.
        "pm25_value" to listOf("nong do bui min"),
        "soh_oem" to listOf("suc khoe pin", "do chai pin"),
        "consumption_50km" to listOf("muc tieu thu", "tieu thu dien"),
        "volt_12v" to listOf("ac quy", "dien ap ac quy"),
        "volt_12v_level" to listOf("muc ac quy"),
    )

    /** Cụm chỉ **loại đối tượng**, không chỉ một mã — dùng để gỡ nghĩa cho động từ quá tải (RE Kiki §7c: *"Mở"*). */
    val MEDIA_WORDS: List<String> = listOf("bai hat", "bai", "nhac", "ca khuc", "song", "music", "track")

    /** Cụm mở đầu một ĐIỂM ĐẾN (đứng sau một động từ không phải NAV, vd *"tìm đường tới …"*). */
    val NAV_WORDS: List<String> = listOf("duong den", "duong toi", "destination")

    /**
     * ═══ V1.1 · CÁCH NÓI TÊN **APP ĐÍCH** ([VoiceAppTargets]) — khai MỘT chỗ, như mọi cách nói khác ═══════════
     *
     * Spec R17(d). Cùng hợp đồng với [CONTROL]/[TELEMETRY]: **không dấu, chữ thường**.
     *
     * ## Vòng 2026-09-16 — phản hồi tester 1.66: *"Mở Google được mà Google Map chưa hiểu"*
     * [ĐO] `docs/diagnostics/voice-mishear-2026-09-16.md` §3: loại ý định `app` đúng **12,8 %** — thấp nhất bảng,
     * kém thứ nhì tới 4 lần. §5 cho biết vì sao: mô hình `zipformer-vi` là mô hình **tiếng Việt**, nên nó in ra
     * đúng cái nó có — *"mở gu gồ máp"* → `mở google map`, *"mở du túp"* → `mở youtube`, *"mở za lô"* → `mở zalô`.
     * Tức chuỗi model in ra **đã đúng brand**, chỉ là từ vựng của Kachi không có cách viết ấy.
     *
     * Nguồn chữ: `scripts/voice/data/apps.tsv` (người soạn tay, có cột vùng miền) + các dòng `open_app` của
     * `scripts/voice/data/aliases-proposed.tsv` — nhưng **không nhập cả 101 dòng `synonym`**.
     *
     * ### Luật LOẠI (viết ra để lần sau khỏi cãi)
     * Chỉ nhận một chuỗi khi nó là **cách đọc nhận ra được của chính thương hiệu**. Bỏ khi:
     *  1. **≤ 2 chữ cái** hoặc ≤ 1 từ mà không phải tên thương hiệu (`mở r`, `mở ip`, `mv at`, `mở g ba`);
     *  2. **trùng một từ tiếng Việt thường** (`mở ra`, `mở hoa`, `mở mắt`, `mở các`, `mở dựa`, `mở dây`, `mở se`,
     *     `ở quê`, `ở hà nội`) — một bí danh như thế cướp câu đang chạy tốt **mà im lặng**, đúng lỗi §7 CLAUDE.md;
     *  3. **rác nhận dạng** không đọc ra thương hiệu nào (`goovel`, `mở sy`, `mở sei`, `myat ubusc`, `mở rưng ba`);
     *  4. **mã không có trong bảng đích** (`zalo`, `carplay`, `androidauto` — xem [VoiceAppPhonetics] về đường đi
     *     của chúng).
     *
     * ⇒ Đếm bằng máy trên đúng 101 dòng `synonym`: nhận **4**, bỏ **97**. Bốn dòng nhận là `mở du tu` (youtube) ·
     * `xem còn mấy phút nữa đầy` (`charging_eta_min`) · `đọc ngày` và `các bụi` (hai dòng sau vào [MISHEARD], có
     * điều kiện). Tỉ lệ thấp ấy **không** phải sự cẩn thận quá mức: 97 dòng kia là chuỗi mô hình nghe **hỏng**
     * (`goovel`, `mở sy`, `myat ubusc`) hoặc trùng từ thường (`mở ra`, `mở hoa`, `mở mắt`) — nhận chúng là dựng
     * một từ vựng theo lỗi của một mô hình, và cái sai ấy sẽ sống lâu hơn chính mô hình đó.
     *
     * Phần còn lại của bảng dưới lấy từ `apps.tsv` — cách **đọc** do người soạn tay, không phải chuỗi máy nghe
     * nhầm; §5 của bảng nghe nhầm dùng để **xác nhận** rằng mô hình thật sự in ra chúng.
     *
     * ⚠ Các cụm này CỐ Ý **không** vào từ vựng chung ([VoiceGrammar.terms]): chúng chỉ được tra ở hai vị trí —
     * ngay sau cụm đánh dấu *"bằng / trên / với"* ([VoiceTailClause.appAfterMarker]) và ở đầu phần đuôi sau động
     * từ ([VoiceTailClause.spokenApp]). Thả *"quây"* hay *"youtube"* vào từ vựng chung là đổi cách hiểu của những
     * câu đang chạy tốt (*"quay lại bài"* là lệnh PREV).
     */
    val APP_TARGETS: Map<String, List<String>> = mapOf(
        VoiceAppTargets.YT_MUSIC to listOf(
            "youtube music", "yt music", "nhac youtube", "youtube nhac",
            "du tup miu dich", "nhac du tup",
        ),
        VoiceAppTargets.YOUTUBE to listOf(
            "youtube", "yt",
            "du tup", "iu tup", "diu tup", "dut tup", "du tu",
        ),
        VoiceAppTargets.SPOTIFY to listOf("spotify", "spo ti phai", "so po ti phai", "po ti phai", "spo ti phy"),
        VoiceAppTargets.ZING to listOf("zing mp3", "zing", "zing em pe ba", "ding mo pe ba"),
        // [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L6 (t45): *"mở bản đồ"* → `Unknown` trên
        // máy có nhãn hệ thống tiếng Anh (*"Maps"*). *"bản đồ"* CHÍNH LÀ nhãn tiếng Việt của app này, nên đây
        // không phải một biệt danh bịa ra — và nhãn thật vẫn được xét TRƯỚC.
        VoiceAppTargets.GMAPS to listOf(
            "google map", "google maps", "ban do google", "ban do", "google",
            "gu go map", "gu go mep", "gu go", "cai ban do",
        ),
        VoiceAppTargets.WAZE to listOf("waze", "quay", "guay", "guey"),
        // [owner 2026-09-19] "vietmap" là tên tự chế, ASR tiếng Việt nghe "hên xui" (việt máp/mép/mốp/mụp/láp…)
        // ⇒ thêm biến thể phiên âm như các app tiếng Anh khác (xem GMAPS "gu go mep"). Toàn 2-từ nên không đụng
        // từ thường; APP_TARGETS chỉ dùng cho đường mở-app/chọn-app-nav nên không rớt vào vựng chung. (Biến thể
        // rụng-1-âm-cuối như "vietma" đã do VoiceAppTargets.bySpokenLoose lo — KHÔNG khai exact ở đây kẻo phá nó.)
        VoiceAppTargets.VIETMAP to listOf(
            "viet map", "vietmap", "viet mat", "ban do viet",
            "viet mep", "viet mop", "viet mup", "viet lap",
        ),
    )

    /**
     * Một cụm **nghe nhầm** chỉ được coi là cách gọi khi câu có sẵn một từ NGỮ CẢNH.
     *
     * @property id mã control/telemetry mà cụm trỏ tới khi điều kiện thoả.
     * @property words cụm đã bỏ dấu, đúng như [VoiceLexicon.deaccent] trả về.
     * @property context ít nhất một trong các từ này phải có mặt **trong cùng câu** (kể cả nằm trong chính
     *   [words]) thì cụm mới được bật.
     */
    data class Misheard(val id: String, val words: List<String>, val context: List<String>)

    /**
     * ═══ H4 · CHUỖI MÔ HÌNH NGHE NHẦM — bí danh CÓ ĐIỀU KIỆN ═════════════════════════════════════════════════
     *
     * [ĐO] `docs/diagnostics/voice-mishear-2026-09-16.md` §5 + `aliases-proposed.tsv`: `lọc bụi` → *"các bụi"*
     * (3 lần) · `lọc ngay` → *"đọc ngày"* (2 lần) · *"học này"*. Owner 1.66: *"nói 'lọc ngay' nó chả hiểu"*.
     *
     * ## Vì sao KHÔNG khai thẳng vào [CONTROL] như một cách nói bình thường
     * `đọc` là **động từ ĐỌC** của [VoiceGrammar.VERBS] và `ngày` là một từ đời thường. Một bí danh trần
     * `"doc ngay"` → `pm25_clean_now` sẽ nuốt mọi câu dạng *"đọc ngày …"*, và cái sai đó **im lặng** — đúng họ
     * lỗi mà luật loại của [APP_TARGETS] §2 dựng ra để chặn. `các` cũng vậy (*"các cửa sổ"*).
     *
     * ⇒ Cụm chỉ được bật khi câu **còn** mang một từ của chính khái niệm ấy (`bui` / `loc`). Với *"các bụi"* điều
     * kiện tự thoả (chữ `bụi` nằm ngay trong cụm) ⇒ câu ấy chạy ngay. Với *"đọc ngày"* / *"học này"* thì
     * **[CHƯA BIẾT]** mô hình có bao giờ in ra chúng cạnh một từ ngữ cảnh không — hai dòng ấy nằm đây ở dạng
     * **dữ liệu chờ**, và cái giá của việc chờ (một câu hiếm chưa chạy) rẻ hơn hẳn cái giá của việc đoán (động từ
     * ĐỌC bị cướp). Đường chữa thật cho chúng là **hotword** `LỌC NGAY`, đã có trong tệp ship.
     */
    val MISHEARD: List<Misheard> = listOf(
        Misheard("pm25", listOf("cac", "bui"), listOf("bui", "loc")),
        Misheard("pm25_clean_now", listOf("doc", "ngay"), listOf("bui", "loc")),
        Misheard("pm25_clean_now", listOf("hoc", "nay"), listOf("bui", "loc")),
    )
}
