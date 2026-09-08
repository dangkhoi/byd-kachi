package com.byd.clusternav.modules.clustercast

/**
 * PARSE `dumpsys display` — PURE (không đụng Android) → unit-test off-device được.
 * Tách khỏi ClusterCast để (a) test được kích cụm/dò VD mà không cần xe, (b) giữ ClusterCast dưới ngưỡng LOC.
 */
object DisplayParse {
    // ★ biên dịch 1 LẦN (trước đây nằm trong thân hàm → biên dịch lại mỗi lần dò VD, mà vòng dò chạy tới 16 lần).
    private val RE_DISPLAY_ANY = Regex("(?:Display |mDisplayId=|Display Id=)(\\d+)")
    private val RE_DISPLAY_HDR = Regex("Display (\\d+):")
    private val RE_DISPLAY_ID = Regex("(?:displayId|Display) (\\d+)")
    private val RE_REAL = Regex("real (\\d+) x (\\d+)")
    private val RE_WM_DISPLAY = Regex("Display: mDisplayId=(\\d+)")
    private val RE_CUR = Regex("cur=(\\d+)x(\\d+)")
    private val RE_WIN_HDR = Regex("^Window #\\d+ Window\\{")
    private val RE_INIT_DPI = Regex("init=\\d+x\\d+ (\\d+)dpi")
    private val RE_BASE_DPI = Regex("base=\\d+x\\d+ (\\d+)dpi")
    private val RE_FRAME = Regex("mFrame=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
    private val RE_OVERSCAN = Regex("mDisplayInfoOverscan=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")

    /**
     * Như [realSize] nhưng trả null khi KHÔNG đọc được, thay vì bịa.
     * `realSize` fallback 1920×720 (kích cụm Seal) — tiện cho đường tính khung, nhưng TAI HẠI khi đem in ra
     * dưới nhãn "kích thước thật": người đọc log tưởng đã đo được, trong khi đó là hằng số đoán mò và nó
     * chỉ đúng-nhờ-may-mắn trên đúng một đời xe. Chỗ nào BÁO CÁO cho người đọc thì dùng hàm này.
     */
    fun realSizeOrNull(dump: String, vd: Int): Pair<Int, Int>? {
        var cur = -1
        for (line in dump.lineSequence()) {
            RE_DISPLAY_ANY.find(line)?.let { cur = it.groupValues[1].toIntOrNull() ?: cur }
            if (cur == vd) RE_REAL.find(line)?.let {
                return it.groupValues[1].toInt() to it.groupValues[2].toInt()
            }
        }
        return null
    }

    /**
     * Cửa sổ của [pkg] đang nằm trên display nào (dòng `mDisplayId=` ngay trong block cửa sổ). null = không thấy.
     * Bắt buộc phải biết điều này trước khi kết luận bất cứ gì về overscan: kết luận "app phớt lờ overscan"
     * rút ra từ một cửa sổ nằm trên MÀN GIỮA là vô nghĩa — màn giữa có bao giờ bị đặt overscan đâu.
     */
    fun appWindowDisplay(dump: String, pkg: String): Int? {
        var inBlock = false
        for (line in dump.lineSequence()) {
            val t = line.trim()
            if (RE_WIN_HDR.containsMatchIn(t)) inBlock = t.contains("$pkg/")
            if (inBlock) Regex("mDisplayId=(\\d+)").find(t)?.let { return it.groupValues[1].toIntOrNull() }
        }
        return null
    }

    /** Kích thước THẬT của VD [vd] ("real W x H" trong block của display id). Fallback [fw]×[fh] (cụm Seal). */
    fun realSize(dump: String, vd: Int, fw: Int = 1920, fh: Int = 720): Pair<Int, Int> {
        var cur = -1
        for (line in dump.lineSequence()) {
            RE_DISPLAY_ANY.find(line)?.let { cur = it.groupValues[1].toIntOrNull() ?: cur }
            if (cur == vd) RE_REAL.find(line)?.let {
                return it.groupValues[1].toInt() to it.groupValues[2].toInt()
            }
        }
        return fw to fh
    }

    /**
     * Logical size ĐANG áp của display [vd], đọc từ `dumpsys window displays` (dòng "cur=WxH").
     * Dùng để kiểm chứng `wm size` có thật sự ăn không — KHÔNG tin exit code của shell.
     */
    fun logicalSize(dump: String, vd: Int): Pair<Int, Int>? {
        var cur = -1
        for (line in dump.lineSequence()) {
            RE_WM_DISPLAY.find(line)?.let { cur = it.groupValues[1].toIntOrNull() ?: cur }
            if (cur == vd) RE_CUR.find(line)?.let {
                return it.groupValues[1].toInt() to it.groupValues[2].toInt()
            }
        }
        return null
    }

    /**
     * Density ĐANG áp và density GỐC của [vd], từ dòng "init=WxH NNNdpi base=WxH MMMdpi" trong
     * `dumpsys window displays`. Trả (đang, gốc). base thiếu (chưa ép) → đang == gốc.
     * Dùng để (a) log giá trị THẬT thay vì giá trị mình vừa gửi đi, (b) biết VD còn bẩn hay không.
     */
    fun density(dump: String, vd: Int): Pair<Int, Int>? {
        var cur = -1
        for (line in dump.lineSequence()) {
            RE_WM_DISPLAY.find(line)?.let { cur = it.groupValues[1].toIntOrNull() ?: cur }
            if (cur == vd) {
                val init = RE_INIT_DPI.find(line)?.groupValues?.get(1)?.toIntOrNull()
                if (init != null) {
                    val base = RE_BASE_DPI.find(line)?.groupValues?.get(1)?.toIntOrNull()
                    return (base ?: init) to init
                }
            }
        }
        return null
    }

    /**
     * Khung THẬT của cửa sổ app [pkg] (dòng "mFrame=[l,t][r,b]" trong `dumpsys window windows`).
     * Đây là cách DUY NHẤT biết `wm overscan` có thực sự làm app nhỏ lại hay app phớt lờ: app khai
     * FLAG_LAYOUT_IN_OVERSCAN/FLAG_FULLSCREEN được cấp thẳng khung nguyên vẹn, overscan không đụng tới.
     * null = không tìm thấy cửa sổ của app.
     */
    fun appWindowFrame(dump: String, pkg: String): IntArray? {
        var inBlock = false
        for (line in dump.lineSequence()) {
            val t = line.trim()
            if (RE_WIN_HDR.containsMatchIn(t)) inBlock = t.contains("$pkg/")
            if (inBlock) RE_FRAME.find(t)?.let {
                return IntArray(4) { k -> it.groupValues[k + 1].toInt() }
            }
        }
        return null
    }

    /**
     * Overscan ĐANG áp trên [vd] (dòng `mDisplayInfoOverscan=[l,t][r,b]` trong `dumpsys window displays`).
     * null = không đọc được. Cần thiết vì `wm overscan` cũng được WM lưu vào /data/system/display_settings.xml
     * theo uniqueId ⇒ SỐNG QUA REBOOT; mà `reconcileOnStart` trước đây chỉ dò rác của `wm size`, nên VD để lại
     * overscan 90px vẫn bị tuyên bố là "sạch" (thấy thật ở diag-0722-073807: overscan [0,90][0,90], dpi 200,
     * mà KHÔNG có app nào trên cụm).
     */
    fun overscan(dump: String, vd: Int): IntArray? {
        var cur = -1
        for (line in dump.lineSequence()) {
            RE_WM_DISPLAY.find(line)?.let { cur = it.groupValues[1].toIntOrNull() ?: cur }
            if (cur == vd) RE_OVERSCAN.find(line)?.let {
                return IntArray(4) { k -> it.groupValues[k + 1].toInt() }
            }
        }
        return null
    }

    private val RE_ACT_PKG = Regex("ActivityRecord\\{\\S+ u\\d+ ([A-Za-z][A-Za-z0-9_.]*)/")
    private val RE_SCS = Regex("mSizeCompatScale=([0-9.]+)")

    /**
     * Tỉ lệ size-compat của app [pkg] (dòng `mSizeCompatScale=` trong `dumpsys window displays`), null nếu app
     * KHÔNG ở size-compat. Trường này chỉ in ra khi activity đang size-compat (có mSizeCompatBounds) → hiện diện
     * = đang bị đóng băng cấu hình ⇒ **wm density / am task resize KHÔNG ăn**.
     *
     * Vì sao cần: log SL6 22/07 chứng minh Android Auto vào size-compat trên cụm (scale 0.727, pillarbox). Không
     * check thì nút DPI/kích thước im lặng no-op và người dùng tưởng app hỏng. Có nó thì báo đúng bệnh.
     * Ghép pkg theo ActivityRecord đứng TRƯỚC dòng scale (scale nằm trong block token của đúng app đó).
     */
    fun sizeCompatScale(windowDisplaysDump: String, pkg: String): Float? {
        var cur: String? = null
        for (line in windowDisplaysDump.lineSequence()) {
            RE_ACT_PKG.find(line)?.let { cur = it.groupValues[1] }
            if (cur == pkg) RE_SCS.find(line)?.let {
                return it.groupValues[1].toFloatOrNull()
            }
        }
        return null
    }

    /**
     * id logical display của CỤM — tên chứa fission/xdja. Luôn ≥1: display 0 là màn hình giữa, KHÔNG BAO GIỜ trả 0
     * (mọi lệnh `wm density`/`wm overscan`/`am task resize` sau đó đều nhắm vào id này).
     */
    fun clusterDisplayId(grepOut: String): Int {
        var cur = -1
        for (line in grepOut.lineSequence()) {
            RE_DISPLAY_HDR.find(line)?.let { cur = it.groupValues[1].toIntOrNull() ?: cur }
            val l = line.lowercase()
            if (l.contains("fission") || l.contains("xdja")) {
                RE_DISPLAY_ID.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { if (it >= 1) return it }
                if (cur >= 1) return cur
            }
        }
        return -1
    }
}
