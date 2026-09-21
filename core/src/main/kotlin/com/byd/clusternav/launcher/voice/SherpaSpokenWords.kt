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
        "khoa xe" to "khóa xe",
        "khoa cua" to "khóa cửa",
        "mo khoa" to "mở khóa",
        "mo khoa xe" to "mở khóa xe",
        "mo khoa cua" to "mở khóa cửa",
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
        "nhiet do dieu hoa" to "nhiệt độ điều hòa",
        "nhiet do trong xe" to "nhiệt độ trong xe",
        "nhiet do" to "nhiệt độ",
        "dieu hoa bao nhieu do" to "điều hòa bao nhiêu độ",
        "quat" to "quạt",
        "quat gio" to "quạt gió",
        // [ĐO xe 2026-09-18 · log] cách gọi ĐỌC của `ac_wind` (*"quạt điều hòa đang mất máy"*, 2 lượt).
        "quat dieu hoa" to "quạt điều hòa",
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
        // ⚠ 1.90 · dạng có dấu của `vol` (*"tiếng"/"âm thanh"*) và `cast` (*"chiếu"/"chiếu lên cụm"/"chiếu màn"*)
        // gỡ cùng hai nút (owner 2026-09-21). `SherpaBiasingCoverageTest` đỏ HAI CHIỀU, nên phải gỡ ở đây nữa.
        // (*"đèn chiếu xa"* ở trên là cách nói của `headl` — nút đó còn, cụm đó Ở LẠI.)
        "dieu hoa" to "điều hòa",
        "may lanh" to "máy lạnh",
        "dieu hoa tu dong" to "điều hòa tự động",
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
        // ⚠ 1.90 · dạng có dấu của `brightness_gear` và `powertrain_mode` gỡ cùng hai nút (owner 2026-09-21).
        "loc khong khi ngay" to "lọc không khí ngay",
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
        "suc khoe pin" to "sức khỏe pin",
        "do chai pin" to "độ chai pin",
        "muc tieu thu" to "mức tiêu thụ",
        "tieu thu dien" to "tiêu thụ điện",
        "ac quy" to "ắc quy",
        "dien ap ac quy" to "điện áp ắc quy",
        "muc ac quy" to "mức ắc quy",
        "bai hat" to "bài hát",
        "bai" to "bài",
        "nhac" to "nhạc",
        "ca khuc" to "ca khúc",
        "duong den" to "đường đến",
        "duong toi" to "đường tới",

        // ═══ 2026-09-16 · GIỌNG NAM ═════════════════════════════════════════════════════════════════
        // Nguồn: `scripts/voice/data/nouns.tsv` (region = nam). Vì sao phải có: [ĐO]
        // `voice-mishear-2026-09-16.md` §4 — vùng `nam` đúng 21,8 % so với `chung` 53,1 %, và §6 xếp loại
        // là **TỪ VỰNG** (chính câu gốc cũng không trỏ đúng mã) ⇒ đây là chỗ chữa, không phải mô hình.
        // ⚠ `"khoa cua xe"` đã bị GỠ khỏi [VoiceSynonyms.CONTROL] — nó nuốt cụm đã đo `MỞ KHÓA CỬA` qua luật
        // tiền tố. Lý do đầy đủ ở chỗ khai (`VoiceSynonyms.kt`, mục `lock`). Bỏ luôn ở đây vì bài canh
        // `SherpaBiasingCoverageTest` đòi bảng này **không có mục chết**.
        "chot cua" to "chốt cửa",
        "mo cua xe" to "mở cửa xe",
        "cua hau" to "cửa hậu",
        "thung sau" to "thùng sau",
        "den tran" to "đèn trần",
        "dang doc sach" to "đang đọc sách",
        "den noc" to "đèn nóc",
        "may loc khong khi" to "máy lọc không khí",
        "may loc bui" to "máy lọc bụi",
        "loc gio cabin" to "lọc gió cabin",
        "quat ghe" to "quạt ghế",
        "lam mat ghe" to "làm mát ghế",
        "thong gio ghe" to "thông gió ghế",
        "ghe nong" to "ghế nóng",
        "nhiet do may lanh" to "nhiệt độ máy lạnh",
        "do lanh" to "độ lạnh",
        "muc gio" to "mức gió",
        "say kieng" to "sấy kiếng",
        "cam ba sau muoi" to "cam ba sáu mươi",
        "den cot pha" to "đèn cốt pha",
        "het kieng" to "hết kiếng",
        "bon kinh" to "bốn kính",
        "kieng lai" to "kiếng lái",
        "cua kieng lai" to "cửa kiếng lái",
        "kieng tai xe" to "kiếng tài xế",
        "kieng truoc trai" to "kiếng trước trái",
        "kieng truoc phai" to "kiếng trước phải",
        "kieng sau trai" to "kiếng sau trái",
        "kinh sau ben trai" to "kính sau bên trái",
        "kieng sau phai" to "kiếng sau phải",
        "kinh sau ben phai" to "kính sau bên phải",
        "loc nhanh" to "lọc nhanh",
        // ⚠ 1.85: "nap ca po"/"nap may" đã xoá cùng nút `hood` — bảng này KHÔNG được giữ mục chết
        // (`SherpaBiasingCoverageTest` đỏ hai chiều: thiếu dạng có dấu, và có dạng có dấu mà cụm đã biến mất).
        "say kieng sau" to "sấy kiếng sau",
        // ⚠ 1.90 · dạng có dấu của `anion` (*"khử mùi"*) gỡ cùng nút (owner 2026-09-21).
        "vo lang nong" to "vô lăng nóng",
        "rem noc" to "rèm nóc",
        "man che nang" to "màn che nắng",
        // 1.85 · khoá trẻ em hai bên + cụm mơ hồ. Dạng CÓ DẤU là thứ mô hình VN mã hoá được bằng BPE — thiếu nó thì
        // cụm không được bias và sự thiếu ấy im lặng (đúng bài học `MEDIA_WORDS` khai không dấu ở 1.75).
        "khoa con nit" to "khóa con nít",
        "khoa tre em" to "khóa trẻ em",
        "khoa tre em ben trai" to "khóa trẻ em bên trái",
        "khoa tre em ben phai" to "khóa trẻ em bên phải",
        "khoa con nit ben phai" to "khóa con nít bên phải",
        // ⚠ Bảng này CHỈ được viết lại dấu — bỏ dấu phải ra ĐÚNG khoá (`SherpaBiasingCoverageTest` khoá hai chiều).
        // [ĐO] bản đầu viết *"gió au-tô"* (cách ĐỌC) ⇒ bỏ dấu ra "gio au-to" ≠ "gio auto" ⇒ đỏ đúng chỗ. Cách đọc
        // là việc của `TtsPronunciation`, không phải của bảng bias.
        "gio auto" to "gió auto",
        // ⚠ 1.90 · dạng có dấu của `headlight_mode` · `screen_rotation` · `camera_view` · `cluster_music` gỡ cùng
        // bốn nút (owner 2026-09-21).
        "sac dien thoai" to "sạc điện thoại",

        // ═══ 1.91 · MỞ RỘNG DICTIONARY (owner 2026-09-21: *"nhiều câu tương tự nhau cho 1 command"*) ══════════
        // Dạng CÓ DẤU của các cách nói mới ở [VoiceSynonyms]. Bảng này **chỉ được viết lại dấu** — bài canh
        // `dang co dau phai bo dau ra dung khoa cua no` ép `deaccent(giá trị) == khoá`, nên đây không phải chỗ
        // đẻ cách nói mới. Chỗ đặt dấu theo kiểu MỚI ở âm tiết MỞ (`khóa` không phải `khoá`) — bài canh
        // `luat dat dau chi ap cho am tiet MO` ép luôn.
        // ── VIỆC 1 · nhánh *"cửa sổ" / "cửa kính"* của nút GỘP 4 kính (sửa bug *"mở hết cửa sổ"*) ────────────
        "het cua so" to "hết cửa sổ",
        "toan bo cua so" to "toàn bộ cửa sổ",
        "tat ca cua so" to "tất cả cửa sổ",
        "moi cua so" to "mọi cửa sổ",
        "bon cua so" to "bốn cửa sổ",
        "het cua kinh" to "hết cửa kính",
        "toan bo cua kinh" to "toàn bộ cửa kính",
        "tat ca cua kinh" to "tất cả cửa kính",
        "bon cua kinh" to "bốn cửa kính",
        "tat ca kieng" to "tất cả kiếng",
        // ── VIỆC 2 · thân xe · khoá · cốp · cửa ──────────────────────────────────────────────────────────────
        "khoa het cua" to "khóa hết cửa",
        "chot xe" to "chốt xe",
        "mo chot cua" to "mở chốt cửa",
        "cop hau" to "cốp hậu",
        "cua cop" to "cửa cốp",
        "khoang hanh ly" to "khoang hành lý",
        // ── kính từng cửa + cửa sổ trời + rèm ────────────────────────────────────────────────────────────────
        "kinh nguoi lai" to "kính người lái",
        "cua so tai xe" to "cửa sổ tài xế",
        "kieng ben lai" to "kiếng bên lái",
        "kieng ben phu" to "kiếng bên phụ",
        "kinh noc" to "kính nóc",
        "rem troi" to "rèm trời",
        "che nang" to "che nắng",
        // ── đèn ──────────────────────────────────────────────────────────────────────────────────────────────
        "den cabin" to "đèn cabin",
        "chieu xa" to "chiếu xa",
        "den lon" to "đèn lớn",
        "den chay ban ngay" to "đèn chạy ban ngày",
        // ── ghế mát / ghế sưởi / vô-lăng ─────────────────────────────────────────────────────────────────────
        "mat ghe" to "mát ghế",
        "mat dit" to "mát đít",
        "mat mong" to "mát mông",
        "thoi mat ghe" to "thổi mát ghế",
        "ghe lai mat" to "ghế lái mát",
        "am ghe" to "ấm ghế",
        "lam am ghe" to "làm ấm ghế",
        "suoi dit" to "sưởi đít",
        "suoi mong" to "sưởi mông",
        "vo lang am" to "vô lăng ấm",
        "lam am vo lang" to "làm ấm vô lăng",
        // ── điều hòa · gió · nhiệt · sấy · lọc ───────────────────────────────────────────────────────────────
        "quat tu dong" to "quạt tự động",
        "lam mat xe" to "làm mát xe",
        "do nong" to "độ nóng",
        "nhiet do xe" to "nhiệt độ xe",
        "muc quat" to "mức quạt",
        "toc do gio" to "tốc độ gió",
        "gio dieu hoa" to "gió điều hòa",
        "tan suong" to "tan sương",
        "khu suong" to "khử sương",
        "say kinh hau" to "sấy kính hậu",
        "tuan hoan gio" to "tuần hoàn gió",
        "tuan hoan khi" to "tuần hoàn khí",
        "khu bui" to "khử bụi",
        "loc khi" to "lọc khí",
        "loc gap" to "lọc gấp",
        "camera toan canh" to "camera toàn cảnh",
        "de sac" to "đế sạc",
        // ── thông tin đọc ────────────────────────────────────────────────────────────────────────────────────
        "dung luong pin" to "dung lượng pin",
        "quang duong con lai" to "quãng đường còn lại",
        "toc do xe" to "tốc độ xe",
        "nhiet do ben ngoai" to "nhiệt độ bên ngoài",
        "muc bui min" to "mức bụi mịn",
        "so km xe da chay" to "số km xe đã chạy",
        "binh xang" to "bình xăng",
        "lop truoc trai" to "lốp trước trái",
        "lop truoc phai" to "lốp trước phải",
        "lop sau trai" to "lốp sau trái",
        "lop sau phai" to "lốp sau phải",
        "con chay duoc bao nhieu" to "còn chạy được bao nhiêu",
        "xang con chay duoc bao xa" to "xăng còn chạy được bao xa",
        // [ĐO xe 2026-09-18] ba cách nói về nhiên liệu (log: «chỉ số xăng» · «xăng còn bao nhiêu» ra Unknown).
        "xang" to "xăng",
        "nhien lieu" to "nhiên liệu",
        "muc nhien lieu" to "mức nhiên liệu",
        "ngoai troi nong khong" to "ngoài trời nóng không",
        "nhiet trong xe" to "nhiệt trong xe",
        "nhiet do trong cabin" to "nhiệt độ trong cabin",
        "hoi banh truoc trai" to "hơi bánh trước trái",
        "hoi banh truoc phai" to "hơi bánh trước phải",
        "hoi banh sau trai" to "hơi bánh sau trái",
        "hoi banh sau phai" to "hơi bánh sau phải",
        "can so" to "cần số",
        "so khung" to "số khung",

        // ═══ H3 · TÊN APP ĐỌC THEO ÂM VIỆT ([VoiceSynonyms.APP_TARGETS]) ═══════════════════════════════
        // Nguồn: `scripts/voice/data/apps.tsv` + §5 của `voice-mishear-2026-09-16.md` (chuỗi mô hình THẬT SỰ
        // in ra). Tên thuần tiếng Anh (*"youtube"*, *"spotify"*…) nằm ở [NO_VI_FORM]: mô hình VN không phát
        // ra token ấy nên bias vô nghĩa — đúng luật đã có, chỉ nay áp cho cả bảng đích.
        "nhac youtube" to "nhạc youtube",
        "youtube nhac" to "youtube nhạc",
        "du tup miu dich" to "du túp miu dích",
        "nhac du tup" to "nhạc du túp",
        "du tup" to "du túp",
        "iu tup" to "iu túp",
        "diu tup" to "diu túp",
        "dut tup" to "dút túp",
        "du tu" to "du tu",
        "spo ti phai" to "spô ti phai",
        "so po ti phai" to "sờ pô ti phai",
        "po ti phai" to "pô ti phai",
        "spo ti phy" to "spo ti phy",
        "zing em pe ba" to "zing em pê ba",
        "ding mo pe ba" to "ding mờ pê ba",
        "ban do google" to "bản đồ google",
        "ban do" to "bản đồ",
        "gu go map" to "gu gồ máp",
        "gu go mep" to "gu gồ mép",
        "gu go" to "gu gồ",
        "cai ban do" to "cái bản đồ",
        "quay" to "quây",
        "guay" to "guây",
        "guey" to "guêy",
        "viet map" to "việt máp",
        "viet mep" to "việt mép",
        "viet mop" to "việt mốp",
        "viet mup" to "việt mụp",
        "viet lap" to "việt láp",
        "viet mat" to "việt mát",
        "ban do viet" to "bản đồ việt",
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
        "battery",
        "blower",
        "boot",
        "cabin light",
        "cabin temperature",
        "camera 360 do",
        "central lock",
        "clean air now",
        "destination",
        // 1.91 — chữ tắt/chữ Anh của hai nút vừa được mở rộng cách nói: mô hình VN không phát ra token ấy.
        "drl",
        "sunroof",
        // 1.91 — dạng viết bằng CHỮ SỐ của nút gộp 4 kính. [SherpaHotwords] bỏ token số, nên bias chúng vô nghĩa
        // (cùng luật `camera 360 do` ở trên); tầng CHỮ vẫn khớp, nên *"mở 4 cửa sổ"* gõ vào vẫn ra cả bốn.
        "4 cua so",
        "4 kinh",
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
        "state of charge",
        "tailgate",
        "track",
        "unlock",
        "unlock car",
        "windows",
        // ── H3 · tên app viết NGUYÊN BẢN tiếng Anh ───────────────────────────────────────────────────
        // Cùng lý do với khối trên, có thêm một phép đo: [ĐO] `voice-mishear-2026-09-16.md` §4 — kiểu nói
        // `tieng_anh_viet` đúng **11,3 %** (thấp nhất trong 5 kiểu). Đường chữa của chúng là **cách đọc âm
        // Việt** ở [ACCENTED], không phải bias một chuỗi mà mô hình VN không phát ra được.
        "google",
        "google map",
        "google maps",
        "spotify",
        "vietmap",
        "waze",
        "youtube",
        "youtube music",
        "yt",
        "yt music",
        "zing",
        "zing mp3",
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
