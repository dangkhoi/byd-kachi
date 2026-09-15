package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.Localized
import com.byd.clusternav.launcher.TelemetryRegistry

/** Động từ mà bộ phân tích nhận ra. Bộ **nhỏ và đều** — đúng hình dạng đã đo ở Kiki (RE §7c). */
enum class VoiceVerb { ON, OFF, OPEN, CLOSE, UP, DOWN, SET, READ, SWITCH, NAV, PLAY, PAUSE, NEXT, PREV }

/** Loại đích của một cụm từ trong từ vựng — quyết định ý định nào được dựng ra. */
enum class VoiceTermKind { CONTROL, TELEMETRY, MACRO, LAUNCHER, PROFILE, APP, MEDIA, NAV }

/**
 * Một cụm từ **đã chuẩn hoá** trỏ tới một đích.
 * @property words các từ đã bỏ dấu (khớp theo dãy); dài hơn ⇒ được xét trước (xem [VoiceGrammar.matchAt]).
 */
data class VoiceTerm(val words: List<String>, val kind: VoiceTermKind, val id: String)

/**
 * ═══ V1 · TỪ ĐỒNG NGHĨA — KHAI **MỘT CHỖ** ════════════════════════════════════════════════════════════════════
 *
 * ## Vì sao ở đây mà không rải vào từng dòng registry
 * `ControlRegistry`/`TelemetryRegistry` là **hợp đồng với màn hình**: `label` là chữ hiện trên nút, và hàng trăm
 * bài test đang assert đúng chuỗi đó (KDoc `Localized.label`). Nhét thêm một danh sách "còn gọi là…" vào mỗi dòng
 * sẽ (a) làm 188 dòng dữ liệu phình ra vì một tính năng duy nhất dùng tới, và (b) mời người sau đặt cách-gọi-miệng
 * vào ô `label` cho tiện — tức đổi chữ trên nút. Ở đây thì nhãn vẫn là nhãn, cách nói là cách nói.
 *
 * ## Luật khai
 *  • **Chỉ khai cái mà nhãn KHÔNG phủ.** Nhãn *"Đèn đọc"* đã tự khớp, không cần khai lại — [VoiceGrammar] sinh cụm
 *    từ nhãn VI/EN/ngắn của **mọi** dòng (đó là điều kiện để bài canh độ phủ đòi *"mọi nút có ít nhất một câu"* có
 *    ý nghĩa: nó phải xanh **do sinh ra**, không do ai đó chép tay 65 dòng).
 *  • Viết **không dấu, chữ thường** — cùng dạng [VoiceLexicon.deaccent] trả về, để khỏi có hai luật chuẩn hoá.
 *  • Cụm trùng nhau giữa hai mã là **hợp lệ**: [VoiceIntentParser] chọn theo loại động từ (xem KDoc ở đó).
 */
object VoiceSynonyms {

    /** Cách nói thêm cho NÚT (`ControlRegistry`). */
    val CONTROL: Map<String, List<String>> = mapOf(
        "lock" to listOf("khoa xe", "khoa cua", "lock car", "central lock"),
        "door" to listOf("mo khoa", "mo khoa xe", "mo khoa cua", "unlock", "unlock car"),
        "trunk" to listOf("cop", "cop xe", "boot", "tailgate"),
        "readl" to listOf("den trong xe", "den doc sach", "cabin light"),
        "pm25" to listOf("loc bui", "loc khong khi", "air filter", "purifier"),
        "seatc" to listOf("thoi ghe", "ghe thoang", "seat cooling"),
        "seath" to listOf("suoi ghe", "ghe am"),
        "temp" to listOf("nhiet do dieu hoa", "nhiet do trong xe", "cabin temperature"),
        "fan" to listOf("quat", "quat gio", "toc do quat", "suc gio", "blower"),
        "defrost" to listOf("say kinh truoc", "xa bang"),
        "cam" to listOf("camera", "camera 360 do", "camera quanh xe"),
        "sunroof" to listOf("noc xe", "cua noc", "cua so noc"),
        "headl" to listOf("den chieu xa", "high beam"),
        "recirc" to listOf("gio trong", "tuan hoan trong", "recirc"),
        "vol" to listOf("tieng", "am thanh", "volume"),
        "wiper" to listOf("can gat", "gat nuoc", "windscreen wiper"),
        // [SOÁT P2] *"dừng chiếu"* — người ta bỏ chữ *"cụm"*. Một từ `chieu` là đủ vì luật **dãy dài nhất thắng**
        // giữ nguyên mọi cụm dài hơn có chứa nó (*"chiếu cụm"*, *"đèn chiếu xa"*), nên không nuốt nhãn nào.
        "cast" to listOf("chieu", "chieu len cum", "chieu man", "cast cluster"),
        "ac_auto" to listOf("dieu hoa", "may lanh", "dieu hoa tu dong", "air con", "ac", "aircon"),
        // [SOÁT P2] *"mở cửa sổ"* / *"mở kính"* — hai câu đời thường nhất về kính, trước đây **không** trỏ tới đâu:
        // *"cửa"* không có trong từ vựng, còn *"sổ"* thì khớp nhãn *"Số"* của datum `gear` ⇒ ra MISMATCH (một câu
        // báo lỗi sai chỗ). Trỏ về nút GỘP là lựa chọn an toàn nhất trong ba lựa chọn: nó thuộc diện CONFIRM
        // (`VoiceRiskTable`) nên người lái thấy đúng hộp *"hạ HẾT 4 kính?"* trước khi có gì xảy ra, và câu trả lời
        // kèm dấu *"chưa kiểm trên xe"* (`windows_all` ở mức OVERDRIVE). Đoán một kính cụ thể mới là đoán mò.
        "windows_all" to listOf("het kinh", "toan bo kinh", "moi kinh", "every window",
            "kinh", "cua so", "cac cua so", "windows"),
        "win_lf" to listOf("kinh ben lai", "kinh ghe lai"),
        "win_rf" to listOf("kinh ben phu", "kinh ghe phu"),
        "drive_mode" to listOf("che do chay", "kieu lai"),
        "ambient_power" to listOf("den vien", "den noi that", "ambient"),
        "ambient_color" to listOf("mau den noi that"),
        "brightness_gear" to listOf("do sang man hinh", "sang man"),
        "target_soc_set" to listOf("muc tieu sac", "gioi han phan tram sac"),
        "start_charging" to listOf("sac xe", "bat dau sac", "start charge"),
        "pm25_clean_now" to listOf("loc khong khi ngay", "clean air now"),
        // ── Pha NGHE (R10): nhãn có CHỮ VIẾT TẮT / CHỮ SỐ thì mô hình tiếng Việt không có từ để nghe ──
        // [ĐO] 2026-09-14, từ điển `vosk-model-small-vn-0.4` (19.529 mục): `ev` · `hev` · `itac` · `avh` đều
        // KHÔNG có mặt ⇒ ba nút này trước đó **gõ được mà không nói được**, và cái thiếu ấy im lặng. Thêm một
        // cách gọi thuần Việt là cách sửa đúng: nó cũng là cách người ta nói ngoài đời, không phải một mẹo cho
        // bộ nhận dạng. Bài canh `VoiceGrammarPhrasesTest.moi kha nang deu co it nhat mot cum noi duoc` đòi
        // MỌI dòng registry phải có ít nhất một cụm nói được, nên thêm nhãn viết tắt mới là nó đỏ ngay.
        "powertrain_mode" to listOf("che do dong co", "xang dien", "che do nang luong"),
        "itac" to listOf("kiem soat mo men", "kiem soat luc keo"),
        "avh" to listOf("giu phanh", "giu phanh tu dong"),
    )

    /** Cách nói thêm cho THÔNG TIN ĐỌC (`TelemetryRegistry`). */
    val TELEMETRY: Map<String, List<String>> = mapOf(
        "soc" to listOf("pin", "phan tram pin", "muc pin", "battery", "state of charge"),
        "ev_range_km" to listOf("tam hoat dong", "di duoc bao xa", "con di duoc bao nhieu", "range"),
        "speed" to listOf("dang chay bao nhieu", "van toc"),
        "ext_temp" to listOf("nhiet do ngoai troi", "ngoai troi", "outside temperature"),
        "pm25_level" to listOf("bui min", "chat luong khong khi", "air quality"),
        "odometer" to listOf("so km da di", "odo", "mileage"),
        "tyre_p_fl" to listOf("ap suat lop truoc trai"),
        "tyre_p_fr" to listOf("ap suat lop truoc phai"),
        "tyre_p_rl" to listOf("ap suat lop sau trai"),
        "tyre_p_rr" to listOf("ap suat lop sau phai"),
        // ── Pha NGHE (R10) — cùng lý do với khối cuối của [CONTROL]: nhãn mang `PM2.5` · `SOH` · `MCU` · `12V` ·
        // `50km` · `%` · `drift`, mà mô hình tiếng Việt không có từ nào trong số đó. [ĐO] 2026-09-14.
        // ⚠ `pm25_value` KHÔNG lấy cụm `"bui min"` — cụm ấy đã thuộc `pm25_level` (mức 0–3) ở trên; hai datum
        // khác nhau mà cùng một cách gọi thì câu *"xem bụi mịn"* trở thành xổ số. `nong do` là chữ phân biệt
        // đúng nghĩa: một bên là MỨC, bên kia là NỒNG ĐỘ µg/m³.
        "pm25_value" to listOf("nong do bui min"),
        "soh_oem" to listOf("suc khoe pin", "do chai pin"),
        "charging_pct" to listOf("phan tram sac"),
        "consumption_50km" to listOf("muc tieu thu", "tieu thu dien"),
        "drift_mode" to listOf("che do truot"),
        "mcu_status" to listOf("trang thai nguon"),
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
     * Spec R17(d). Cùng hợp đồng với [CONTROL]/[TELEMETRY]: **không dấu, chữ thường**, và cụm nào không dùng được
     * thì lộ ra bằng số đo chứ không bằng suy đoán.
     *
     * ## [ĐO] 2026-09-14 — từ điển `vosk-model-small-vn-0.4` (19.529 mục) **không** có mọi tên thương hiệu
     * Tra ngược từng từ: `youtube` ✓ · `music` ✓ · `google` ✓ · `map` ✓ · `yt` ✓ · `ô` ✓ — nhưng `maps` ✗ ·
     * `waze` ✗ · `spotify` ✗ · `zing` ✗ · `mp3` ✗ · `vietmap` ✗. Cụm chứa một từ ✗ bị [VoicePhrases] loại **cả
     * cụm** (đúng thiết kế), nên mỗi app phải có ít nhất một cách nói mà mô hình đọc nổi:
     *  • *"google maps"* ✗ ⇒ thêm **"google map"** ✓ và **"ban do google"** ✓ (cách người Việt hay nói hơn);
     *  • *"waze"* ✗ ⇒ thêm **"quay"** — chính là âm Việt *"quây"* mà người ta vẫn gọi app này (`quay`/`quây`/
     *    `quẩy` đều có trong từ điển, và [VoicePhrases] nở cả họ thanh điệu nên nói thanh nào cũng nhận);
     *  • *"vietmap"* ✗ ⇒ thêm **"viet map"** ✓✓ (hai từ rời);
     *  • *"spotify"* · *"zing mp3"* ✗ và **không có âm Việt nào tự nhiên** ⇒ chấp nhận: hai app này **gõ được mà
     *    chưa nói được**, con số ghi ở §9 spec. Bịa ra một cách viết theo âm (*"sờ pô ti phi"*) là bịa một cách
     *    nói không ai dùng — tệ hơn là nói thẳng rằng chưa nói được.
     *
     * ⚠ Các cụm này CỐ Ý **không** vào từ vựng chung ([VoiceGrammar.terms]): chúng chỉ được tra ở đúng một vị trí
     * — ngay sau cụm đánh dấu *"bằng / trên / với"* (xem [VoiceIntentParser.appAfterMarker]). Thả *"quay"* hay
     * *"youtube"* vào từ vựng chung là đổi cách hiểu của những câu đang chạy tốt (*"quay lại bài"* là lệnh PREV).
     */
    val APP_TARGETS: Map<String, List<String>> = mapOf(
        VoiceAppTargets.YT_MUSIC to listOf("youtube music", "yt music", "nhac youtube", "youtube nhac"),
        VoiceAppTargets.YOUTUBE to listOf("youtube", "yt"),
        VoiceAppTargets.SPOTIFY to listOf("spotify"),
        VoiceAppTargets.ZING to listOf("zing mp3", "zing"),
        // [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L6 (t45): *"mở bản đồ"* → `Unknown` trên
        // máy có nhãn hệ thống tiếng Anh (*"Maps"*). *"bản đồ"* CHÍNH LÀ nhãn tiếng Việt của app này
        // (`PackageManager` trả *"Bản đồ"* ở máy đặt tiếng Việt), nên đây không phải một biệt danh bịa ra — và
        // nhãn thật vẫn được xét TRƯỚC (xem [VoiceIntentParser.appByTargetName]), nên máy nào có nhãn *"Bản đồ"*
        // thì vẫn đi đường nhãn như cũ.
        VoiceAppTargets.GMAPS to listOf("google map", "google maps", "ban do google", "ban do", "google"),
        VoiceAppTargets.WAZE to listOf("waze", "quay"),
        VoiceAppTargets.VIETMAP to listOf("viet map", "vietmap"),
    )
}

/**
 * ═══ V1 · TỪ VỰNG **SINH TỪ BỘ ĐĂNG KÝ** ══════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R2. Thuần Kotlin (`:core`).
 *
 * ## Ràng buộc số một: KHÔNG chép tay nhãn
 * 65 nút + 123 datum + 4 gói lệnh + 2 hành động launcher, mỗi cái đã có nhãn VI, nhãn EN và (một số) nhãn ngắn hai
 * thứ tiếng. Chép chúng sang một bảng "câu lệnh" là dựng **bản sao thứ hai của nhãn** — đúng họ lỗi mà dự án đã
 * trả giá bốn lần (`unitPrefs` ×4, `customLayout` ×2): ai đó đổi nhãn nút, màn hình đổi, còn câu lệnh thì không, và
 * **không gì báo lỗi**. Ở đây từ vựng được **sinh** mỗi lần gọi từ chính bộ đăng ký ⇒ thêm một dòng registry là
 * tự nhiên nói được, xoá một dòng là tự nhiên hết nói.
 *
 * ## Vì sao khớp theo DÃY TỪ, dài trước ngắn
 * [ĐO] `docs/PROJECT-BACKLOG.md` L-RE2 — có ba cặp nút mà nhãn cái này **chứa** nhãn cái kia: *"Chế độ đèn pha"* ⊃
 * *"Đèn pha"*, *"Độ sáng HUD"* vs *"Độ sáng màn"*, *"Góc camera"* vs *"Camera 360"*. Khớp từ-đơn hoặc khớp ngắn
 * trước thì *"chế độ đèn pha auto"* sẽ bật/tắt đèn pha — sai nút, và là nút ảnh hưởng tầm nhìn ban đêm. Luật
 * **dãy dài nhất thắng** giải cả ba cặp mà không cần một dòng `if` nào cho từng cặp (CLAUDE.md §7).
 */
object VoiceGrammar {

    /**
     * Bảng ĐỘNG TỪ — cụm dài đặt trước (khớp dài nhất thắng, cùng luật với [matchAt]).
     *
     * *"Mở"* cố ý **không** có nghĩa cố định: nó vào đây là [VoiceVerb.OPEN], rồi [VoiceIntentParser] mới quyết
     * theo **kiểu đối tượng** (cửa/kính = COVER · app = mở app · Cài đặt = launcher · nhạc = phát). Đây là ràng
     * buộc thiết kế lấy thẳng từ RE Kiki §7(c): *"Mở bị quá tải nặng ⇒ phân biệt bằng KIỂU ĐỐI TƯỢNG, không bằng
     * động từ"*.
     */
    val VERBS: List<Pair<List<String>, VoiceVerb>> = listOf(
        listOf("dan", "duong", "den") to VoiceVerb.NAV,
        listOf("dan", "duong", "toi") to VoiceVerb.NAV,
        listOf("chi", "duong", "den") to VoiceVerb.NAV,
        listOf("chi", "duong", "toi") to VoiceVerb.NAV,
        listOf("tim", "duong", "den") to VoiceVerb.NAV,
        listOf("dan", "duong") to VoiceVerb.NAV,
        listOf("chi", "duong") to VoiceVerb.NAV,
        listOf("navigate", "to") to VoiceVerb.NAV,
        listOf("directions", "to") to VoiceVerb.NAV,
        // ⚠⚠ *"về nhà"* · *"đi làm"* · *"đến công ty"* CỐ Ý **không** có mặt trong bảng này — xem
        // [VoicePlaces.PLACE_VERBS]. Thêm `ve`/`di`/`den` vào đây là biến chúng thành động từ dẫn đường **vô điều
        // kiện**, và [ĐO] ngay trong bộ test đang có: *"về bố cục 2 cột"* (một câu cố ý để NO_VERB, spec §7 OQ1)
        // sẽ thành *"dẫn đường tới «bố cục 2 cột»"* — máy mở app bản đồ tìm một cái tên vô nghĩa thay vì nói thẳng
        // là chưa làm được. Ba từ ấy quá thường để mang một nghĩa cố định; chúng chỉ có nghĩa dẫn đường khi phần
        // đuôi **thật sự là một nơi trong sổ**, và đó đúng là điều kiện mà [VoicePlaces.PLACE_VERBS] gác.
        listOf("bai", "tiep", "theo") to VoiceVerb.NEXT,
        listOf("bai", "ke", "tiep") to VoiceVerb.NEXT,
        listOf("chuyen", "bai") to VoiceVerb.NEXT,
        // [SOÁT P2] *"bài tiếp"* (không có chữ *"theo"*) rơi vào NO_VERB: cụm *"bai"* là một từ khoá NHẠC, mà
        // `headMatch` cố ý chỉ nhận MACRO/CONTROL/LAUNCHER ⇒ không ai đỡ. Nó là cách nói ngắn phổ biến nhất.
        listOf("bai", "tiep") to VoiceVerb.NEXT,
        listOf("tiep", "theo") to VoiceVerb.NEXT,
        listOf("next", "track") to VoiceVerb.NEXT,
        listOf("bai", "truoc") to VoiceVerb.PREV,
        listOf("quay", "lai", "bai") to VoiceVerb.PREV,
        listOf("previous", "track") to VoiceVerb.PREV,
        listOf("tam", "dung") to VoiceVerb.PAUSE,
        listOf("doi", "sang") to VoiceVerb.SWITCH,
        listOf("chuyen", "sang") to VoiceVerb.SWITCH,
        listOf("kiem", "tra") to VoiceVerb.READ,
        listOf("cho", "xem") to VoiceVerb.READ,
        listOf("doc", "to") to VoiceVerb.READ,
        listOf("turn", "on") to VoiceVerb.ON,
        listOf("turn", "off") to VoiceVerb.OFF,
        listOf("switch", "to") to VoiceVerb.SWITCH,
        listOf("bat") to VoiceVerb.ON,
        listOf("on") to VoiceVerb.ON,
        listOf("enable") to VoiceVerb.ON,
        listOf("tat") to VoiceVerb.OFF,
        listOf("off") to VoiceVerb.OFF,
        listOf("disable") to VoiceVerb.OFF,
        listOf("mo") to VoiceVerb.OPEN,
        listOf("open") to VoiceVerb.OPEN,
        // V1.1 — *"đưa YouTube vào ô số 2"*. Người ta nói *"đưa … vào …"* nhiều hơn *"mở … vào …"* khi ý là
        // GẮN chứ không phải MỞ. Không đụng nhãn nào: không cụm nào trong từ vựng bắt đầu bằng `dua`.
        listOf("dua") to VoiceVerb.OPEN,
        listOf("dong") to VoiceVerb.CLOSE,
        listOf("close") to VoiceVerb.CLOSE,
        listOf("tang") to VoiceVerb.UP,
        listOf("increase") to VoiceVerb.UP,
        listOf("raise") to VoiceVerb.UP,
        listOf("giam") to VoiceVerb.DOWN,
        listOf("decrease") to VoiceVerb.DOWN,
        listOf("lower") to VoiceVerb.DOWN,
        listOf("dat") to VoiceVerb.SET,
        listOf("chinh") to VoiceVerb.SET,
        listOf("set") to VoiceVerb.SET,
        listOf("xem") to VoiceVerb.READ,
        listOf("doc") to VoiceVerb.READ,
        listOf("hien") to VoiceVerb.READ,
        listOf("show") to VoiceVerb.READ,
        listOf("read") to VoiceVerb.READ,
        listOf("check") to VoiceVerb.READ,
        listOf("chuyen") to VoiceVerb.SWITCH,
        listOf("doi") to VoiceVerb.SWITCH,
        listOf("switch") to VoiceVerb.SWITCH,
        listOf("phat") to VoiceVerb.PLAY,
        listOf("nghe") to VoiceVerb.PLAY,
        listOf("play") to VoiceVerb.PLAY,
        listOf("dung") to VoiceVerb.PAUSE,
        listOf("pause") to VoiceVerb.PAUSE,
        listOf("stop") to VoiceVerb.PAUSE,
        listOf("tiep") to VoiceVerb.NEXT,
        listOf("next") to VoiceVerb.NEXT,
        listOf("truoc") to VoiceVerb.PREV,
        listOf("previous") to VoiceVerb.PREV,
    ).sortedByDescending { it.first.size }

    /** Động từ nào là *"đọc thông tin"* — dùng để chọn ứng viên khi một cụm trỏ tới CẢ datum lẫn nút. */
    fun isRead(v: VoiceVerb): Boolean = v == VoiceVerb.READ

    /** Động từ nào là *"làm gì đó với xe/app"*. */
    fun isAction(v: VoiceVerb): Boolean = !isRead(v)

    /**
     * TOÀN BỘ từ vựng đối tượng, **sinh ra** từ 4 bộ đăng ký + [VoiceSynonyms] + danh sách động (hồ sơ, app).
     *
     * @param profiles tên hồ sơ tài xế đang có (`HomeUiState.profiles`) — danh sách **động**, không thể sinh từ
     *   registry vì người dùng tự đặt tên.
     * @param apps nhãn ứng dụng đã cài. Cũng động, và cũng là lý do bộ phân tích **không** hardcode tên gói nào
     *   (CLAUDE.md §7): app nào có mặt thì gọi được app đó, không app nào được viết cứng vào mã.
     */
    fun terms(profiles: List<String> = emptyList(), apps: List<String> = emptyList()): List<VoiceTerm> {
        if (profiles.isEmpty() && apps.isEmpty()) return STATIC_SORTED
        val dyn = ArrayList<VoiceTerm>(profiles.size + apps.size)
        profiles.forEach { p -> term(p, VoiceTermKind.PROFILE, p)?.let { dyn.add(it) } }
        apps.forEach { a -> term(a, VoiceTermKind.APP, a)?.let { dyn.add(it) } }
        // Thứ tự ghép giữ NGUYÊN như bản dựng-mỗi-lần: …registry → hồ sơ → app → từ khoá nhạc/dẫn đường. Nó là
        // thứ tự phân xử khi hai cụm **bằng nhau về độ dài** (`VoiceIntentParser.choose` lấy phần tử đầu), nên
        // đảo nó là lặng lẽ đổi cách hiểu của một câu.
        return (STATIC_HEAD + dyn + STATIC_TAIL).distinct().sortedByDescending { it.words.size }
    }

    /** Một cụm đã chuẩn hoá, hoặc `null` nếu chuỗi không còn từ nào sau khi tách (vd tên app chỉ có ký hiệu). */
    private fun term(phrase: String, kind: VoiceTermKind, id: String): VoiceTerm? =
        VoiceLexicon.tokenize(phrase).map { it.norm }.takeIf { it.isNotEmpty() }?.let { VoiceTerm(it, kind, id) }

    /**
     * Phần từ vựng **không đổi trong suốt đời tiến trình** — 4 bộ đăng ký + [VoiceSynonyms], dựng MỘT lần.
     *
     * ## [SOÁT P1] Vì sao phải `lazy`, không dựng lại mỗi câu
     * Mỗi lần dựng là ~600 cụm × (`Regex.split` + `Normalizer.normalize` NFD + duyệt ký tự). Bản đầu gọi nó
     * **trong** [VoiceIntentParser.parse], tức mỗi câu gõ vào là một lần dựng lại toàn bộ — và chỗ gọi là **thread
     * giao diện** (nút *"Chạy câu lệnh"*). Danh mục thì đứng yên (`ControlRegistry.ALL` là hằng), nên đó là công
     * làm lại y nguyên. Phần THẬT SỰ động (hồ sơ, app đã cài) vẫn dựng mỗi lần — nó rẻ (vài cụm) và nó phải mới.
     *
     * `by lazy` mặc định là `SYNCHRONIZED` ⇒ an toàn khi bộ phân tích bị gọi từ nhiều thread (màn thử gọi trên
     * thread giao diện, gói lệnh chạy trên thread nền).
     */
    private val STATIC_HEAD: List<VoiceTerm> by lazy {
        val out = ArrayList<VoiceTerm>(600)
        fun add(phrase: String, kind: VoiceTermKind, id: String) { term(phrase, kind, id)?.let { out.add(it) } }
        fun addLocalized(l: Localized, id: String, kind: VoiceTermKind, extraShort: List<String?> = emptyList()) {
            add(l.label, kind, id)
            l.labelEn?.let { add(it, kind, id) }
            extraShort.filterNotNull().forEach { add(it, kind, id) }
        }

        ControlRegistry.ALL.forEach { c ->
            addLocalized(c, c.id, VoiceTermKind.CONTROL, listOf(c.short, c.shortEn))
            VoiceSynonyms.CONTROL[c.id]?.forEach { add(it, VoiceTermKind.CONTROL, c.id) }
        }
        TelemetryRegistry.ALL.forEach { t ->
            addLocalized(t, t.id, VoiceTermKind.TELEMETRY, listOf(t.short, t.shortEn))
            VoiceSynonyms.TELEMETRY[t.id]?.forEach { add(it, VoiceTermKind.TELEMETRY, t.id) }
        }
        ActionMacros.ALL.forEach { m -> addLocalized(m, m.id, VoiceTermKind.MACRO) }
        LauncherActions.ALL.forEach { a -> addLocalized(a, a.id, VoiceTermKind.LAUNCHER) }
        out
    }

    /** Từ khoá LOẠI (nhạc / điểm đến) — đứng CUỐI như bản dựng-mỗi-lần, xem ghi chú thứ tự ở [terms]. */
    private val STATIC_TAIL: List<VoiceTerm> by lazy {
        val out = ArrayList<VoiceTerm>(VoiceSynonyms.MEDIA_WORDS.size + VoiceSynonyms.NAV_WORDS.size)
        VoiceSynonyms.MEDIA_WORDS.forEach { w -> term(w, VoiceTermKind.MEDIA, w)?.let { out.add(it) } }
        VoiceSynonyms.NAV_WORDS.forEach { w -> term(w, VoiceTermKind.NAV, w)?.let { out.add(it) } }
        out
    }

    /**
     * Từ vựng khi KHÔNG có hồ sơ/app nào (mọi bài kiểm thuần, và ca xe chưa nạp xong danh sách app).
     *
     * Dài trước ngắn — xem KDoc lớp (L-RE2). `distinct()` vì nhãn EN có thể trùng nhãn VI (vd "EV / HEV").
     */
    private val STATIC_SORTED: List<VoiceTerm> by lazy {
        (STATIC_HEAD + STATIC_TAIL).distinct().sortedByDescending { it.words.size }
    }

    /**
     * Mọi cụm khớp tại đúng vị trí [i] của [t], **theo thứ tự dài → ngắn**.
     *
     * Trả về cả danh sách (không chỉ cái đầu) vì một cụm có thể trỏ tới nhiều đích cùng lúc — [ĐO] 18 nhãn trùng
     * giữa mục ĐỌC và HÀNH ĐỘNG (`CapabilityCatalog.collidingLabels`, vd *"Kính trước-trái"* vừa là datum % mở vừa
     * là nút đóng/mở). Ai chọn trong số đó là việc của [VoiceIntentParser], vì chỉ nó mới biết động từ.
     */
    fun matchAt(t: List<VoiceLexicon.Token>, i: Int, terms: List<VoiceTerm>): List<VoiceTerm> =
        terms.filter { VoiceLexicon.phraseAt(t, i, it.words) }

    /**
     * ═══ PHA NGHE · cùng danh mục này, nhưng viết cho **bộ nhận dạng** ════════════════════════════════════════
     *
     * [terms] trả từ vựng **đã bỏ dấu** để so khớp chữ; [phrases] trả cùng nội dung đó ở dạng **có dấu** mà mô
     * hình nhận dạng hiểu được. Hai đầu ra, **một nguồn** (4 bộ đăng ký + [VoiceSynonyms] + danh sách động) — nên
     * thêm một dòng registry là vừa gõ được vừa nói được, không phải sửa hai chỗ.
     *
     * Cách biến không-dấu thành có-dấu **không** phải một bảng chép tay: nó tra ngược qua chính từ điển của mô
     * hình. Xem [VoicePhrases] — đó là chỗ giải thích đầy đủ, và là chỗ có bài canh.
     *
     * @param vocabulary từ điển mô hình ([VoskWordList.readOutputSymbols]).
     * @param installed TÊN GÓI đang có trên máy — quyết định tên [VoiceAppTargets] nào được khai với bộ nhận
     *   dạng. Rỗng ⇒ không khai tên app đích nào (xem `VoicePhrases.labelPhrases`).
     * @param places nhãn trong **sổ địa chỉ** của hồ sơ đang dùng (spec `kachi-voice-addresses.html` R6). Rỗng ⇒
     *   không khai cách nói nào của sổ — cùng luật với [installed]: ngữ pháp chỉ khai thứ gọi được thật.
     */
    fun phrases(
        vocabulary: Set<String>,
        profiles: List<String> = emptyList(),
        apps: List<String> = emptyList(),
        installed: Set<String> = emptySet(),
        places: List<String> = emptyList(),
    ): VoicePhraseSet = VoicePhrases.build(vocabulary, profiles, apps, installed, places)
}
