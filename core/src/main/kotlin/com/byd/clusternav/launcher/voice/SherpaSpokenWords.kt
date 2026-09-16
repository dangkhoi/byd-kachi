package com.byd.clusternav.launcher.voice

/**
 * ═══ V2 pha NGHE · DẠNG **CÓ DẤU** CỦA TỪ VỰNG KHÔNG DẤU ═════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html` §Design. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Vì sao tệp này phải tồn tại (một chỗ hụt ĐÃ ĐO, không phải phòng xa)
 * [VoiceSynonyms] và [VoiceGrammar.VERBS] khai **không dấu, chữ thường** — đúng hợp đồng của tầng so khớp CHỮ
 * ([VoiceLexicon.deaccent] trả về dạng đó). Hotwords của sherpa thì ngược lại: mô hình VN xuất **CHỮ HOA CÓ DẤU**
 * (`tokens.txt`: `▁ĐÈN`, `▁PIN`…) nên một hotword không dấu **không mã hoá được** bằng bảng BPE ⇒ native bỏ lặng
 * lẽ (xem KDoc [SherpaHotwords]). Đổ thẳng `VoiceSynonyms` vào tệp hotwords vì thế **không** giúp gì — nó chỉ
 * trông như đã giúp.
 *
 * [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L3 (25 câu WAV, biasing=true, model
 * `zipformer-vi-2025-04-20`): `xem pin` → nghe *"xem tin"* · `mở kính trước trái` → *"mở kín trước trái"* ·
 * `dừng nhạc` → *"rừng nhạc"* — cả ba đều **ra `Unknown`**. Ba từ trung tâm của bộ lệnh (`pin` · `kính` ·
 * `dừng`) chưa bao giờ là hotword: hai từ đầu vì nhãn *"Pin (SOC)"* / *"Kính trước-trái"* bị dấu câu giết cả cụm
 * ([SherpaHotwords] đã vá), từ thứ ba vì **động từ chưa từng là nguồn hotword**.
 *
 * ## Vì sao KHÔNG khôi phục dấu bằng máy
 * Đã thử [ĐO 2026-09-15, script off-car]: dựng bảng `bỏ dấu → có dấu` từ chính nhãn của 4 bộ đăng ký rồi tra
 * ngược. Kết quả sai ở đúng những từ cần nhất — `dung` → *"dụng"* (từ *"ứng dụng"*), `tim` → *"tím"*,
 * `duong` → *"dương"*, `chuyen` → *"chuyến"*. Một hotword **sai dấu** còn tệ hơn không có: nó kéo câu về một từ
 * khác hẳn. Nên dạng có dấu phải được **khai**, và được **canh bằng máy** (xem `SherpaBiasingCoverageTest`):
 *  • mỗi giá trị phải bỏ dấu ra ĐÚNG khoá của nó ⇒ không ai lén thêm một cách nói mới vào đây;
 *  • mỗi khoá phải có thật trong [VoiceSynonyms] / [VoiceGrammar.VERBS] ⇒ không có mục chết;
 *  • MỌI cụm của [VoiceSynonyms] phải nằm ở [ACCENTED] hoặc [NO_VI_FORM] ⇒ thêm một cách nói mà quên dạng có dấu
 *    thì bài canh ĐỎ, không im lặng;
 *  • MỌI [VoiceVerb] phải có ít nhất một dạng có dấu ⇒ thêm động từ mới cũng vậy.
 */
object SherpaSpokenWords {

    /**
     * Dạng **có dấu** của từng cụm trong [VoiceSynonyms] — khoá là ĐÚNG chuỗi đã khai ở đó (không dấu, thường).
     *
     * Đây không phải một từ vựng thứ hai: nó là **cùng một cụm, viết đúng chính tả**. Bài canh ép điều đó
     * (`VoiceLexicon.deaccent(giá trị) == khoá`), nên chỗ này không thể trở thành nơi lén thêm câu lệnh.
     */
    val ACCENTED: Map<String, String> = mapOf(
        "khoa xe" to "khoá xe",
        "khoa cua" to "khoá cửa",
        "mo khoa" to "mở khoá",
        "mo khoa xe" to "mở khoá xe",
        "mo khoa cua" to "mở khoá cửa",
        "cop" to "cốp",
        "cop xe" to "cốp xe",
        "den trong xe" to "đèn trong xe",
        "den doc sach" to "đèn đọc sách",
        "loc bui" to "lọc bụi",
        "loc khong khi" to "lọc không khí",
        "thoi ghe" to "thổi ghế",
        "ghe thoang" to "ghế thoáng",
        "suoi ghe" to "sưởi ghế",
        "ghe am" to "ghế ấm",
        "nhiet do dieu hoa" to "nhiệt độ điều hoà",
        "nhiet do trong xe" to "nhiệt độ trong xe",
        "quat" to "quạt",
        "quat gio" to "quạt gió",
        "toc do quat" to "tốc độ quạt",
        "suc gio" to "sức gió",
        "say kinh truoc" to "sấy kính trước",
        "xa bang" to "xả băng",
        "camera" to "camera",
        "camera quanh xe" to "camera quanh xe",
        "noc xe" to "nóc xe",
        "cua noc" to "cửa nóc",
        "cua so noc" to "cửa sổ nóc",
        "den chieu xa" to "đèn chiếu xa",
        "gio trong" to "gió trong",
        "tuan hoan trong" to "tuần hoàn trong",
        "tieng" to "tiếng",
        "am thanh" to "âm thanh",
        "can gat" to "cần gạt",
        "gat nuoc" to "gạt nước",
        "chieu" to "chiếu",
        "chieu len cum" to "chiếu lên cụm",
        "chieu man" to "chiếu màn",
        "dieu hoa" to "điều hoà",
        "may lanh" to "máy lạnh",
        "dieu hoa tu dong" to "điều hoà tự động",
        "het kinh" to "hết kính",
        "toan bo kinh" to "toàn bộ kính",
        "moi kinh" to "mọi kính",
        "kinh" to "kính",
        "cua so" to "cửa sổ",
        "cac cua so" to "các cửa sổ",
        // V3 · R10 — bốn cách nói về KÍNH LÁI ([ĐO xe 2026-09-16]: owner nói *"mở kính lái"*, máy nghe đúng
        // nhưng từ vựng không có cụm nào bắt đầu bằng `kinh lai` ⇒ rơi về nút gộp 4 kính).
        "kinh lai" to "kính lái",
        "cua kinh lai" to "cửa kính lái",
        "kinh tai xe" to "kính tài xế",
        "cua so lai" to "cửa sổ lái",
        "kinh ben lai" to "kính bên lái",
        "kinh ghe lai" to "kính ghế lái",
        "kinh ben phu" to "kính bên phụ",
        "kinh ghe phu" to "kính ghế phụ",
        "che do chay" to "chế độ chạy",
        "kieu lai" to "kiểu lái",
        "den vien" to "đèn viền",
        "den noi that" to "đèn nội thất",
        "mau den noi that" to "màu đèn nội thất",
        "do sang man hinh" to "độ sáng màn hình",
        "sang man" to "sáng màn",
        "muc tieu sac" to "mục tiêu sạc",
        "gioi han phan tram sac" to "giới hạn phần trăm sạc",
        "sac xe" to "sạc xe",
        "bat dau sac" to "bắt đầu sạc",
        "loc khong khi ngay" to "lọc không khí ngay",
        "che do dong co" to "chế độ động cơ",
        "xang dien" to "xăng điện",
        "che do nang luong" to "chế độ năng lượng",
        "kiem soat mo men" to "kiểm soát mô men",
        "kiem soat luc keo" to "kiểm soát lực kéo",
        "giu phanh" to "giữ phanh",
        "giu phanh tu dong" to "giữ phanh tự động",
        "pin" to "pin",
        "phan tram pin" to "phần trăm pin",
        "muc pin" to "mức pin",
        "tam hoat dong" to "tầm hoạt động",
        "di duoc bao xa" to "đi được bao xa",
        "con di duoc bao nhieu" to "còn đi được bao nhiêu",
        "dang chay bao nhieu" to "đang chạy bao nhiêu",
        "van toc" to "vận tốc",
        "nhiet do ngoai troi" to "nhiệt độ ngoài trời",
        "ngoai troi" to "ngoài trời",
        "bui min" to "bụi mịn",
        "chat luong khong khi" to "chất lượng không khí",
        "so km da di" to "số km đã đi",
        "odo" to "odo",
        "ap suat lop truoc trai" to "áp suất lốp trước trái",
        "ap suat lop truoc phai" to "áp suất lốp trước phải",
        "ap suat lop sau trai" to "áp suất lốp sau trái",
        "ap suat lop sau phai" to "áp suất lốp sau phải",
        "nong do bui min" to "nồng độ bụi mịn",
        "suc khoe pin" to "sức khoẻ pin",
        "do chai pin" to "độ chai pin",
        "phan tram sac" to "phần trăm sạc",
        "muc tieu thu" to "mức tiêu thụ",
        "tieu thu dien" to "tiêu thụ điện",
        "che do truot" to "chế độ trượt",
        "trang thai nguon" to "trạng thái nguồn",
        "ac quy" to "ắc quy",
        "dien ap ac quy" to "điện áp ắc quy",
        "muc ac quy" to "mức ắc quy",
        "bai hat" to "bài hát",
        "bai" to "bài",
        "nhac" to "nhạc",
        "ca khuc" to "ca khúc",
        "duong den" to "đường đến",
        "duong toi" to "đường tới",
    )

    /**
     * Cụm **cố ý không** có dạng nói tiếng Việt ⇒ không vào tệp hotwords.
     *
     * Hai loại, cùng một lý do: mô hình VN không phát ra được token đó nên bias vô nghĩa (KDoc [SherpaBiasing]).
     *  • tên/chữ tiếng Anh (*"purifier"*, *"state of charge"*…) — [VoiceIntentParser] khớp chữ lo phần này;
     *  • cụm mang **chữ số** (*"camera 360 do"*) — [SherpaHotwords] bỏ token số, phần còn lại không còn nghĩa.
     */
    val NO_VI_FORM: Set<String> = setOf(
        "ac",
        "air con",
        "air filter",
        "air quality",
        "aircon",
        "ambient",
        "battery",
        "blower",
        "boot",
        "cabin light",
        "cabin temperature",
        "camera 360 do",
        "cast cluster",
        "central lock",
        "clean air now",
        "destination",
        "every window",
        "high beam",
        "lock car",
        "mileage",
        "music",
        "outside temperature",
        "purifier",
        "range",
        "recirc",
        "seat cooling",
        "song",
        "start charge",
        "state of charge",
        "tailgate",
        "track",
        "unlock",
        "unlock car",
        "volume",
        "windows",
        "windscreen wiper",
    )

    /**
     * Dạng **có dấu** của [VoiceGrammar.VERBS], nhóm theo [VoiceVerb].
     *
     * Vì sao động từ cũng phải bias: [ĐO] w09 `dừng nhạc` → *"rừng nhạc"*. Danh từ nghe đúng, **động từ** nghe
     * sai — mà động từ mới là thứ quyết định việc gì xảy ra. Mỗi dạng ở đây phải bỏ dấu ra đúng một cụm đã khai
     * cho CHÍNH [VoiceVerb] đó (bài canh ép), nên bảng này không thể lệch khỏi bộ động từ thật.
     */
    val VERBS: Map<VoiceVerb, List<String>> = mapOf(
        VoiceVerb.ON to listOf("bật"),
        VoiceVerb.OFF to listOf("tắt"),
        VoiceVerb.OPEN to listOf("mở", "đưa"),
        VoiceVerb.CLOSE to listOf("đóng"),
        VoiceVerb.UP to listOf("tăng"),
        VoiceVerb.DOWN to listOf("giảm"),
        VoiceVerb.SET to listOf("đặt", "chỉnh"),
        VoiceVerb.READ to listOf("xem", "đọc", "hiện", "kiểm tra", "cho xem", "đọc to"),
        VoiceVerb.SWITCH to listOf("đổi", "chuyển", "đổi sang", "chuyển sang"),
        VoiceVerb.NAV to listOf(
            "dẫn đường", "chỉ đường", "dẫn đường đến", "dẫn đường tới",
            "chỉ đường đến", "chỉ đường tới", "tìm đường đến",
        ),
        VoiceVerb.PLAY to listOf("phát", "nghe"),
        VoiceVerb.PAUSE to listOf("dừng", "tạm dừng"),
        VoiceVerb.NEXT to listOf("tiếp", "tiếp theo", "bài tiếp", "bài tiếp theo", "bài kế tiếp", "chuyển bài"),
        VoiceVerb.PREV to listOf("trước", "bài trước", "quay lại bài"),
    )

    /**
     * Mọi cụm **có dấu** đáng đưa vào tệp hotwords: động từ trước (chúng quyết định VIỆC), rồi cách nói đời
     * thường. Thứ tự ổn định để tệp hotwords `diff` được giữa hai lượt đo.
     */
    val ALL: List<String> get() = VERBS.values.flatten() + ACCENTED.values
}
