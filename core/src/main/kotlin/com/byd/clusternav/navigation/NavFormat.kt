package com.byd.clusternav.navigation

/**
 * Logic NAV-DOMAIN cho chuỗi đưa lên cụm: rút gọn tên đường VN + map lệnh rẽ -> mã icon AMAP.
 * Tách khỏi graphics (BitmapUtil) vì thay đổi vì lý do khác (buffer firmware / ngôn ngữ vs xử lý bitmap).
 */
object NavFormat {

    // ── Tên đường: ô NEXT_PATHNAME trên cụm là buffer cố định UTF-16LE (~16 byte = ~8 ký tự BMP),
    //    firmware Hán hoá -> tên VN dài bị CỤT ("Nguyễn Huệ" -> "Nguyễn "). Cụm hard-cut, KHÔNG marquee.
    //    => rút gọn phía client TRƯỚC khi putExtra. (Số 8 là đo thực nghiệm; chỉnh nếu còn cụt.)
    //    Mỗi ký tự VN có dấu vẫn 1 code-unit UTF-16 = 2 byte, nên BỎ DẤU không giúp tiết kiệm -> giữ dấu.
    //    ĐO TRÊN XE (2026-06-23): ô hiện ~7 ký tự rồi "…" -> đặt 7 cho hiện gọn, không bị "…".
    const val ROAD_MAX_UNITS = 7

    // ── HUD kính lái: ngân sách ký tự cho tên đường đẩy lên HUD (INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET,
    //    buffer BYTE cố định nhỏ). Test on-car: 7 quá nhỏ → firmware bỏ tên hoàn toàn.
    //    Nâng lên 20 (40 bytes UTF-16LE). Nếu vẫn bị bỏ → giảm dần cho tới khi firmware hiện.
    const val HUD_ROAD_MAX_UNITS = 20

    // tiền tố động từ rẽ + FILLER vô nghĩa ("về hướng"...) — bỏ để chỉ còn tên đường.
    private val MANEUVER_PREFIX = Regex(
        "^(rẽ phải vào|rẽ trái vào|re phai vao|re trai vao|đi thẳng( trên)?|di thang( tren)?|" +
        "quay đầu( tại)?|quay dau( tai)?|tiếp tục( trên| đi)?|tiep tuc( tren| di)?|nhập vào|nhap vao|" +
        "về hướng|ve huong|về phía|ve phia|hướng về|huong ve|hướng tới|huong toi|đi về|di ve|" +
        "vào|vao|theo|đi tới|di toi|turn (left|right) onto|onto|continue onto|" +
        "head (north|south|east|west)( on)?|merge onto|take the)\\s+",
        RegexOption.IGNORE_CASE
    )
    // tiền tố loại đường THƯỜNG -> bỏ hẳn (KHÔNG bỏ hầm/cầu/bến — những cái có nghĩa, giữ lại).
    private val ROAD_PREFIX = Regex(
        "^(đường|duong|phố|pho|ngõ|ngo|hẻm|hem|ngách|ngach)\\s+",
        RegexOption.IGNORE_CASE
    )
    // loại đường có-số -> viết tắt, dính liền số: "Quốc lộ 1A" -> "QL1A"
    private val ROAD_CLASS = listOf(
        Regex("^(quốc lộ|quoc lo)\\s*", RegexOption.IGNORE_CASE) to "QL",
        Regex("^(tỉnh lộ|tinh lo)\\s*", RegexOption.IGNORE_CASE) to "TL",
        Regex("^(đường tỉnh|duong tinh)\\s*", RegexOption.IGNORE_CASE) to "DT",
        Regex("^(cao tốc|cao toc)\\s*", RegexOption.IGNORE_CASE) to "CT",
        Regex("^(đại lộ|dai lo)\\s*", RegexOption.IGNORE_CASE) to "ĐL",
    )
    // Từ-loại CÓ NGHĨA ở đầu tên -> GIỮ (không bỏ, không viết tắt): hầm/cầu/bến...
    private val KEEP_CLASS = setOf("hầm", "ham", "cầu", "cau", "bến", "ben")

    // ── Regex COMPILE 1 LẦN (hot-path: cleanRoadName/maneuverVerbIcon/roundaboutExit chạy mỗi frame @400ms).
    //    Input các hàm này đã .lowercase() → giữ pattern lowercase, KHÔNG cần IGNORE_CASE (output y nguyên).
    private val RE_PARENS = Regex("\\s*\\(.*?\\)\\s*")
    private val RE_WS = Regex("\\s+")
    private val RE_UTURN = Regex("quay đầu|quay dau|u-?turn|làm vòng")
    private val RE_SHARP_L = Regex("ngoặt trái|ngoat trai|sharp left")
    private val RE_SHARP_R = Regex("ngoặt phải|ngoat phai|sharp right")
    private val RE_SLIGHT_L = Regex("chếch trái|chech trai|hơi trái|hoi trai|slight left|keep left")
    private val RE_SLIGHT_R = Regex("chếch phải|chech phai|hơi phải|hoi phai|slight right|keep right")
    private val RE_TURN_L = Regex("rẽ trái|re trai|quẹo trái|turn left|left onto")
    private val RE_TURN_R = Regex("rẽ phải|re phai|quẹo phải|turn right|right onto")
    private val RE_ROUNDABOUT = Regex("vòng xuyến|vong xuyen|bùng binh|bung binh|roundabout|rotary")
    private val RE_ARRIVE = Regex("đến nơi|den noi|điểm đến|diem den|arrive|destination")
    private val RE_STRAIGHT = Regex("đi thẳng|di thang|go straight|^straight")
    private val RE_CONTINUE = Regex("tiếp tục|tiep tuc|continue|theo đường|theo duong|follow")
    // Track B (2026-08-14): merge/nhập làn — 0..28 KHÔNG có glyph merge → ĐI THẲNG (9), KHÔNG chếch phải (sửa bug owner).
    private val RE_MERGE = Regex("nhập làn|nhap lan|merge")
    // Track B: sắp vào hầm — NEW_ICON 16 → CAN 49. On-car verify GMaps expose token qua NavArrowLog small_amap/sig_name.
    private val RE_TUNNEL = Regex("hầm|tunnel|đường hầm")
    private val RE_RAB_EXIT = Regex("""(?:lối ra|loi ra|nhánh|nhanh|exit|(?:take|at) the)\s*(?:thứ|thu)?\s*(\d+)""")
    private val RE_RAB_ORD = Regex("""(\d+)\s*(?:st|nd|rd|th)\s+exit""")
    private val RE_DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val RE_NON_ALNUM = Regex("[^A-Za-z0-9]+")

    /** Dọn tên đường (bỏ filler/động từ/ngoặc/Đường-Phố, GIỮ hầm/cầu) — trả tên ĐẦY ĐỦ (dùng cho marquee). */
    fun cleanRoadName(road: String): String {
        var s = road.trim()
        if (s.isEmpty()) return s
        s = s.substringBefore(",").trim()                       // bỏ phần sau dấu phẩy
        s = s.replace(RE_PARENS, " ").trim()                    // bỏ phần trong ngoặc
        s = MANEUVER_PREFIX.replace(s, "").trim()                // bỏ động từ + filler ("về hướng")
        for ((re, abbr) in ROAD_CLASS) if (re.containsMatchIn(s)) { s = re.replace(s, abbr); break }
        s = ROAD_PREFIX.replace(s, "").trim()                    // bỏ "Đường/Phố/Ngõ..." (giữ hầm/cầu)
        return s.replace(RE_WS, " ").trim().ifEmpty { road.trim() }
    }

    /** Rút gọn cho ô cụm/HUD khi KHÔNG cuộn. "Nguyễn Hữu Cảnh"->"NHCảnh" (thang giữ từ cuối); "hầm X Y"->"hầm XY".
     *  [maxUnits] = ngân sách UTF-16 code-unit (mặc định ô cụm [ROAD_MAX_UNITS]; HUD truyền [HUD_ROAD_MAX_UNITS]).
     *  Giữ default → caller cụm (roadWindow/marquee) KHÔNG đổi hành vi (backward-compat).
     *  NFC-normalize TRƯỚC khi đo length / take() / dựng dạng viết tắt (F8): buffer đích là BYTE, quan hệ
     *  "1 ký tự = 1 code-unit = 2 byte" CHỈ đúng với text BMP dạng NFC. Input NFD (dấu tách rời) → 1 ký tự =
     *  ≥2 code-unit = ≥4 byte (phá quan hệ 2×) và take()/take(1) cắt GIỮA base + dấu kết hợp → rác hiển thị.
     *  Chuẩn hoá SỚM (trước cả cleanRoadName) cũng giúp regex loại-đường (viết theo NFC) khớp input NFD. */
    fun fitRoadName(road: String, maxUnits: Int = ROAD_MAX_UNITS): String {
        val nfc = java.text.Normalizer.normalize(road, java.text.Normalizer.Form.NFC)
        val s = cleanRoadName(nfc)
        if (s.length <= maxUnits) return s
        val words = s.split(" ")
        // giữ nguyên từ-loại có nghĩa ở đầu (hầm/cầu) + viết tắt phần còn lại: "hầm Nguyễn Hữu Cảnh" -> "hầm NHC"
        if (words.size >= 2 && words[0].lowercase() in KEEP_CLASS) {
            val acr = words.drop(1).joinToString("") { it.take(1) }
            val cand = "${words[0]} $acr"
            return if (cand.length <= maxUnits) cand else cand.take(maxUnits)
        }
        if (words.size >= 2) {
            // THANG FALLBACK (owner 2026-08-15): chấm → dính-đầu+GIỮ-TỪ-CUỐI → acronym → cắt cứng.
            // Chọn dạng ĐỌC ĐƯỢC NHẤT còn vừa ngân sách; giữ TỪ CUỐI (định danh) càng lâu càng tốt.
            // "Nguyễn Hữu Cảnh" @7: "N.H.Cảnh"(8)>7 → "NHCảnh"(6) — trước đây rớt thẳng "NHC" (mất "Cảnh").
            val dotted = words.dropLast(1).joinToString("") { it.take(1) + "." } + words.last()  // "T.T.Kim" / "N.H.Cảnh"
            if (dotted.length <= maxUnits) return dotted        // "Trần Trọng Kim" -> "T.T.Kim"
            val gluedLast = words.dropLast(1).joinToString("") { it.take(1) } + words.last()      // "NHCảnh" / "VNGiáp"
            if (gluedLast.length <= maxUnits) return gluedLast  // chấm không vừa nhưng vẫn giữ từ cuối
            val acronym = words.joinToString("") { it.take(1) }       // dài hơn -> "NHC"/"CMTT"
            if (acronym.length <= maxUnits) return acronym
            return acronym.take(maxUnits)                       // bí quá (tên > maxUnits từ): cắt acronym
        }
        return s.take(maxUnits)
    }

    /** MARQUEE: cửa sổ [width] ký tự của [name], trượt theo [tick] (mỗi nhịp +1). Tên ngắn -> trả nguyên. */
    fun roadWindow(name: String, tick: Int, width: Int): String {
        if (name.length <= width) return name
        val loop = "$name   "                                        // tên + khoảng nghỉ trước khi lặp
        val off = ((tick % loop.length) + loop.length) % loop.length
        return (loop + loop).substring(off, off + width)
    }

    /**
     * Lệnh rẽ (text VN/EN) -> mã NEW_ICON của AMAP (index 0..28; AmapService TỰ remap qua TurnIdMapToCAN,
     * KHÔNG tự remap ở đây). Bảng từ AmapService.TURN_STRING (đã decompile, chuẩn cho firmware này).
     * VN đi bên phải (RHT) -> dùng hàng RHT (vòng xuyến 11, quay đầu 8).
     */
    fun maneuverToAmapIcon(text: String): Int = maneuverVerbIcon(text) ?: 9

    /** Như trên nhưng trả null nếu chữ KHÔNG có động từ rẽ (để builder fallback sang đọc ẢNH mũi tên). */
    fun maneuverVerbIcon(text: String): Int? {
        val t = text.lowercase()
        return when {
            RE_MERGE.containsMatchIn(t) -> 9      // Track B: merge/nhập làn → đi thẳng. TRƯỚC turn/slight ("merge left onto" không được ăn nhánh rẽ).
            RE_UTURN.containsMatchIn(t) -> 8
            RE_SHARP_L.containsMatchIn(t) -> 6
            RE_SHARP_R.containsMatchIn(t) -> 7
            RE_SLIGHT_L.containsMatchIn(t) -> 4
            RE_SLIGHT_R.containsMatchIn(t) -> 5
            RE_TURN_L.containsMatchIn(t) -> 2
            RE_TURN_R.containsMatchIn(t) -> 3
            RE_TUNNEL.containsMatchIn(t) -> 16    // Track B: sắp vào hầm (→ CAN 49). SAU turn (giữ rẽ), TRƯỚC straight/continue (ưu tiên glyph hầm khi đi thẳng vào hầm).
            RE_ROUNDABOUT.containsMatchIn(t) -> 11
            RE_ARRIVE.containsMatchIn(t) -> 15
            RE_STRAIGHT.containsMatchIn(t) -> 9
            RE_CONTINUE.containsMatchIn(t) -> 20
            else -> null   // không có động từ -> để tên small-icon (IconResource) quyết định
        }
    }

    /** Bỏ dấu + gộp ký tự lạ thành '_' -> token AN TOÀN cho shell-arg (đường dadb/NavOpen, args tách bằng space).
     *  Mirror HudTextSanitizer của reference (NFD strip + đ→d). KHÔNG dùng cho path broadcast (ô đó nhận space/dấu OK). */
    fun asciiToken(s: String): String {
        val noDiac = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(RE_DIACRITICS, "")
            .replace("đ", "d").replace("Đ", "D")
        return noDiac.trim().replace(RE_NON_ALNUM, "_").trim('_').ifEmpty { "Road" }
    }

    /** Số nhánh ra ở vòng xuyến nếu lệnh có ("lối ra thứ 3" / "3rd exit" / "take the 2nd exit"). -1 nếu không có. */
    fun roundaboutExit(text: String): Int {
        val t = text.lowercase()
        // B3: KHÔNG để "the" trơ — nó khớp "on the 1", "the 5 freeway" → ép glyph vòng-xuyến giả cho lệnh đi thẳng.
        // Chỉ nhận "the" khi có ngữ cảnh vòng-xuyến ("take/at the N"); còn lại là lối-ra/nhánh/exit/thứ-tự-số + "exit".
        val m = RE_RAB_EXIT.find(t) ?: RE_RAB_ORD.find(t)
        return m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }
}
