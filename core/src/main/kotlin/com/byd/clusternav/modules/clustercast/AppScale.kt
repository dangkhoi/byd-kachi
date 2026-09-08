package com.byd.clusternav.modules.clustercast

/**
 * CẤU HÌNH SCALE PER-APP khi chiếu lên cụm (R6). Thay model global DPI+inset cố định.
 *
 *  • [dpi]   = `wm density -d <VD>` — độ lớn nội dung (120..320).
 *  • rect L/T/R/B = bounds `am task resize` theo px cụm THẬT (vd Seal 1920×720).
 *    rect = -1 (bất kỳ cạnh) → AUTO = full VD [0,0,W,H] (GIỮ hành vi cũ cho app chưa cấu hình).
 *
 * Serialize 1 app: "dpi,l,t,r,b". Map nhiều app: "pkg=dpi,l,t,r,b|pkg2=...". Round-trip qua [parse]/[serialize].
 * Data class thuần (không phụ thuộc Android) → unit-test off-device được.
 */
data class AppScale(
    val dpi: Int = 200,
    val rectL: Int = -1,
    val rectT: Int = -1,
    val rectR: Int = -1,
    val rectB: Int = -1,
) {
    /** true = chưa cấu hình rect (bất kỳ cạnh nào < 0) → dùng full VD. */
    val isAuto: Boolean get() = rectL < 0 || rectT < 0 || rectR < 0 || rectB < 0

    /** Bounds THỰC trên VD kích thước [w]×[h]. Auto → full VD [0,0,w,h]. Trả [l,t,r,b]. */
    fun boundsOn(w: Int, h: Int): IntArray =
        if (isAuto) intArrayOf(0, 0, w, h) else intArrayOf(rectL, rectT, rectR, rectB)

    /** "dpi,l,t,r,b" — dùng trong map serialize. */
    fun serialize(): String = "$dpi,$rectL,$rectT,$rectR,$rectB"

    /**
     * Overscan insets TƯƠNG ĐƯƠNG bounds trên VD [w]×[h]: `[left, top, w−right, h−bottom]`, clamp ≥ 0
     * (rect từ model cụm khác/to hơn không cho inset âm). Auto/full VD → `[0,0,0,0]`.
     * Dùng làm FALLBACK chỉnh kích thước khi freeform CHƯA sống (`am task resize` bị từ chối — xem
     * ClusterCast.applyBounds): `wm overscan l,t,ri,bi -d VD` co vùng hiển thị về đúng khung rect,
     * ăn ở TẦNG DISPLAY nên không cần freeform (đã chứng minh ăn trên xe 2026-07-20).
     */
    fun overscanOn(w: Int, h: Int): IntArray {
        val b = boundsOn(w, h)
        return intArrayOf(
            b[0].coerceAtLeast(0), b[1].coerceAtLeast(0),
            (w - b[2]).coerceAtLeast(0), (h - b[3]).coerceAtLeast(0),
        )
    }

    /**
     * Nới/thu khung QUANH TÂM. [dW]/[dH] = tổng thay đổi ngang/dọc (px; + = to ra, − = nhỏ lại).
     * Đang AUTO → materialize từ full VD [0,0,w,h] trước rồi mới nudge. Clamp trong [0,w]/[0,h] + [MIN_PX].
     */
    fun nudgeRect(w: Int, h: Int, dW: Int, dH: Int): AppScale {
        val b = boundsOn(w, h)
        // ★ v0.37 SỬA LỖI TRÔI KHUNG: bản cũ kẹp TỪNG CẠNH độc lập, nên khi đã chạm đáy MIN_PX mà user bấm tiếp
        //   thì cạnh trên vẫn dịch được còn cạnh dưới bị suy ra lại → khung KHÔNG nhỏ thêm mà LỪ LỪ TRÔI xuống/
        //   sang phải. Đúng vết hiện trường: khung lưu trên xe là 1296×160 (chạm sàn) và lệch tâm (536,224).
        //   Giờ kẹp KÍCH THƯỚC trước rồi mới đặt lại quanh TÂM CŨ → chạm sàn là đứng yên hẳn.
        val cx = (b[0] + b[2]) / 2
        val cy = (b[1] + b[3]) / 2
        val nw = ((b[2] - b[0]) + dW).coerceIn(MIN_PX.coerceAtMost(w), w)
        val nh = ((b[3] - b[1]) + dH).coerceIn(MIN_PX.coerceAtMost(h), h)
        val l = (cx - nw / 2).coerceIn(0, w - nw)
        val t = (cy - nh / 2).coerceIn(0, h - nh)
        return copy(rectL = l, rectT = t, rectR = l + nw, rectB = t + nh)
    }

    /**
     * Kích thước LOGIC của VD để tái tạo khung này bằng `wm size` (tầng sizing KHÔNG cần freeform).
     * ⚠ `wm size` chỉ đổi được KÍCH THƯỚC — LogicalDisplay letterbox căn giữa và giữ tỉ lệ, nên VỊ TRÍ lệch tâm
     * KHÔNG tái tạo được. Chấp nhận: với app chiếu điện thoại thì cỡ mới là thứ người dùng cần.
     */
    fun forcedSizeOn(w: Int, h: Int): IntArray {
        val b = boundsOn(w, h)
        return intArrayOf(
            (b[2] - b[0]).coerceIn(MIN_PX.coerceAtMost(w), w),
            (b[3] - b[1]).coerceIn(MIN_PX.coerceAtMost(h), h),
        )
    }

    /** DPI bù lại phần phóng to mà LogicalDisplay áp khi thu logical size → chữ giữ nguyên cỡ vật lý. */
    fun dpiForForcedSize(w: Int, h: Int): Int {
        val s = forcedSizeOn(w, h)
        if (s[0] <= 0 || s[1] <= 0) return dpi
        val k = minOf(w.toFloat() / s[0], h.toFloat() / s[1])
        return (dpi * k).toInt().coerceIn(DPI_MIN, DPI_MAX)
    }

    /** Đổi DPI ± [d] nấc, GIỮ nguyên rect. Clamp [DPI_MIN,DPI_MAX]. */
    fun nudgeDpi(d: Int): AppScale = copy(dpi = (dpi + d).coerceIn(DPI_MIN, DPI_MAX))

    /**
     * ★ KHUNG = PHẦN TRĂM CỦA CỤM, CĂN GIỮA (v0.42) — thay cặp preset 16:9 / 21:9.
     *
     * VÌ SAO BỎ 16:9 và 21:9: cụm là một DẢI NGANG rất dẹt — Seal đo được 1920×720 = **8:3 = 2.667:1**.
     * Mọi tỉ lệ HẸP hơn 2.667 (16:9 = 1.78, 21:9 = 2.33) khi căn vào dải này đều bị kẹp theo CHIỀU CAO, nghĩa là
     * khung luôn cao hết cụm và chỉ khác nhau ở chỗ bị cắt bao nhiêu HAI BÊN. Không bao giờ tạo ra viền trên/dưới.
     * Nói cách khác: chúng chỉ biết làm hẹp bề ngang — đúng như anh em phản ánh là "vô nghĩa".
     *
     * [scaled] lấy [pct]% của cụm THẬT rồi căn giữa, nên không phụ thuộc đời xe: Seal 1920×720, hay bất kỳ cụm
     * nào khác, đều ra khung đúng tỉ lệ của chính nó. Đây mới là thứ người dùng cần trên một dải ngang.
     */
    fun scaled(w: Int, h: Int, pct: Int): AppScale {
        if (w <= 0 || h <= 0) return this
        val q = pct.coerceIn(20, 100)
        val bw = (w * q / 100).coerceIn(MIN_PX.coerceAtMost(w), w)
        val bh = (h * q / 100).coerceIn(MIN_PX.coerceAtMost(h), h)
        val l = (w - bw) / 2
        val t = (h - bh) / 2
        return copy(rectL = l, rectT = t, rectR = l + bw, rectB = t + bh)
    }

    /**
     * ★ KHUNG THEO TỈ LỆ, CĂN GIỮA (v0.36) — giữ lại cho app VIDEO thật sự cần tỉ lệ cố định.
     * ⚠ Trên cụm dạng dải 2.667:1 thì mọi tỉ lệ hẹp hơn đều bị kẹp theo chiều cao (xem [scaled]).
     * Cụm 1920×720 là 2.67:1; trừ tiếp inset dọc mặc định 90px còn 1920×540 = **3.56:1**. Không layout điện thoại
     * nào sống nổi ở tỉ lệ đó: riêng chrome của YouTube (thanh trên + chips + nav dưới) đã ăn gần hết chiều cao,
     * phần nội dung còn lại gần bằng 0 → app tự thu về mini-player, nhìn như bị bóp méo.
     * [fit] cắt khung về đúng [arW]:[arH] căn giữa nên nội dung có tỉ lệ người ta thiết kế cho.
     * VD 1920×720 · 16:9 · insetV=0 → [480,0,1440,720] (1280×720). insetV=40 → [531,40,1389,680] (858×640... )
     * @param insetV chừa trên/dưới bao nhiêu px cho thanh OEM của cụm (0 = dùng trọn chiều cao).
     */
    fun fit(w: Int, h: Int, arW: Int = 16, arH: Int = 9, insetV: Int = 0): AppScale {
        if (w <= 0 || h <= 0 || arW <= 0 || arH <= 0) return this
        val availH = (h - 2 * insetV).coerceIn(MIN_PX.coerceAtMost(h), h)
        var bh = availH
        var bw = bh * arW / arH
        if (bw > w) { bw = w; bh = (bw * arH / arW).coerceAtMost(availH) }   // rộng quá → khớp theo bề ngang
        bw = bw.coerceIn(MIN_PX.coerceAtMost(w), w)
        bh = bh.coerceIn(MIN_PX.coerceAtMost(h), h)
        val l = (w - bw) / 2
        val t = (h - bh) / 2
        return copy(rectL = l, rectT = t, rectR = l + bw, rectB = t + bh)
    }

    /**
     * Chỉnh 1 CẠNH độc lập: dời cạnh [edge] đi [delta] px, TỰ TÍNH LẠI kích thước (không căn giữa).
     * delta<0 = cạnh dịch sang TRÁI/LÊN · delta>0 = sang PHẢI/XUỐNG. AUTO/full → materialize [0,0,w,h] trước.
     * Clamp trong [0,w]/[0,h], không để 2 cạnh đối vượt nhau (giữ tối thiểu [MIN_PX]).
     */
    fun nudgeEdge(w: Int, h: Int, edge: Edge, delta: Int): AppScale {
        val b = boundsOn(w, h)
        var l = b[0]; var t = b[1]; var r = b[2]; var bot = b[3]
        when (edge) {
            Edge.LEFT   -> l = (l + delta).coerceIn(0, r - MIN_PX)
            Edge.RIGHT  -> r = (r + delta).coerceIn(l + MIN_PX, w)
            Edge.TOP    -> t = (t + delta).coerceIn(0, bot - MIN_PX)
            Edge.BOTTOM -> bot = (bot + delta).coerceIn(t + MIN_PX, h)
        }
        return copy(rectL = l, rectT = t, rectR = r, rectB = bot)
    }

    /**
     * DỜI cả khung đi ([dx],[dy]) px, GIỮ NGUYÊN kích thước (dùng cho D-pad "Vị trí" ◀▲▼▶).
     * dx<0 = sang TRÁI · dx>0 = PHẢI · dy<0 = LÊN · dy>0 = XUỐNG. AUTO/full → materialize [0,0,w,h] trước.
     * Clamp để khung không tràn ra ngoài VD (dán mép khi chạm biên). Khung đã full VD → không còn chỗ dời (giữ nguyên).
     */
    fun nudgeMove(w: Int, h: Int, dx: Int, dy: Int): AppScale {
        val b = boundsOn(w, h)
        val bw = b[2] - b[0]
        val bh = b[3] - b[1]
        val l = (b[0] + dx).coerceIn(0, (w - bw).coerceAtLeast(0))
        val t = (b[1] + dy).coerceIn(0, (h - bh).coerceAtLeast(0))
        return copy(rectL = l, rectT = t, rectR = l + bw, rectB = t + bh)
    }

    /** 4 cạnh khung để chỉnh độc lập. */
    enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

    companion object {
        const val DPI_MIN = 120
        const val DPI_MAX = 320
        const val MIN_PX = 160   // khung tối thiểu (không co về 0)
        const val STEP_WH = 16   // 1 nấc mũi tên chỉnh cạnh Trái/Phải/Trên/Dưới (px)
        const val STEP_DPI = 10  // 1 nấc mũi tên DPI

        /** parse "dpi,l,t,r,b". null nếu sai định dạng (số phần ≠ 5 hoặc không phải số). */
        fun parse(s: String): AppScale? {
            val p = s.split(",")
            if (p.size != 5) return null
            val n = IntArray(5)
            for (i in 0 until 5) n[i] = p[i].trim().toIntOrNull() ?: return null
            return AppScale(n[0], n[1], n[2], n[3], n[4])
        }

        /** map pkg→AppScale → "pkg=dpi,l,t,r,b|...". Bỏ pkg rỗng. */
        fun serializeMap(m: Map<String, AppScale>): String =
            m.entries.filter { it.key.isNotEmpty() }
                .joinToString("|") { "${it.key}=${it.value.serialize()}" }

        /** "pkg=dpi,l,t,r,b|..." → map. Bỏ qua entry hỏng (không ném). */
        fun parseMap(s: String): Map<String, AppScale> {
            if (s.isBlank()) return emptyMap()
            val out = LinkedHashMap<String, AppScale>()
            for (part in s.split("|")) {
                if (part.isBlank()) continue
                val eq = part.indexOf('=')
                if (eq <= 0) continue
                val pkg = part.substring(0, eq).trim()
                if (pkg.isEmpty()) continue
                val sc = parse(part.substring(eq + 1)) ?: continue
                out[pkg] = sc
            }
            return out
        }
    }
}
