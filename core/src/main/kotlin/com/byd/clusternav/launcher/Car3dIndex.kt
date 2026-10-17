package com.byd.clusternav.launcher

/**
 * ═══ 3.1.14 `PERF-SL6-3113` R3 — bảng tra BẤT BIẾN của một tài sản xe 3D + trạng thái tư thế, sống trên LUỒNG CHÍNH ════════════════
 *
 * Tách Filament sang luồng car3d-gl ([Car3dOwnerThread]) rồi để luồng chính hỏi từng câu qua bước nhảy đồng bộ thì vẫn đơ:
 * [ĐO máy ảo 2 lõi 17/10] dựng neo/chấm sau khi nạp = 201 bước nhảy · 234 ms việc trên car3d-gl mà luồng chính phải chờ; 2 khung vẽ =
 * 102 bước nhảy · 136 ms chờ (đánh thức luồng ~1,3 ms/bước khi 2 lõi đều bận). Mà mọi câu hỏi đó (tên · nhóm · hộp riêng · bản lề ·
 * vật liệu của từng thực thể) là dữ liệu BẤT BIẾN của tài sản ⇒ car3d-gl đọc MỘT lần lúc nạp ([Part], qua JNI + `extras`), giao cho luồng
 * chính bảng này; từ đó neo/hộp/đích chạm/chấm tính thuần ở đây (không JNI, không nhảy luồng). Việc ĐỔI cảnh (ẩn/hiện, biến đổi nút,
 * sơn, phát sáng) cũng tính ở đây — bên vẽ chỉ chuyển danh sách lệnh kết quả sang car3d-gl theo thứ tự, không chờ.
 *
 * Logic chép nguyên từ bộ vẽ cũ (`Car3dRenderer` ≤ 3.1.13): hộp riêng `extras` thay hộp accessor chung của Filament; tư thế chỉ gồm
 * BẢN LỀ (kính trượt không dời hộp — 3.1.5); kính tụt theo tên nút. Chỉ luồng chính gọi. `Car3dIndexTest`.
 */
class Car3dIndex(private val parts: List<Part>, private val assetBounds: Car3dBounds?) {

    /**
     * Một thực thể vẽ được. [box] = hộp riêng (min xyz, max xyz) từ `extras`; [aabb] = hộp của Filament (cùng dạng) khi không có
     * `extras`; [material]/[glow] = khai báo vật liệu sơn / đèn khoang của nút ([Car3dBox]).
     */
    class Part(
        val id: Int, val name: String?, val group: String?, val box: FloatArray?, val aabb: FloatArray?,
        val hinge: Car3dHinge.Hinge?, val material: String?, val glow: FloatArray?,
    )

    private val hidden = HashSet<Int>()
    private val slideOf = HashMap<Int, Float>()
    private val swingOf = HashMap<Int, Pair<Car3dHinge.Hinge, Float>>()
    private val hits = HashMap<String, List<Part>>()
    private var glowing: Boolean? = null

    fun bounds(): Car3dBounds? = assetBounds

    /** Hộp bao của các bộ phận ĐANG HIỆN (hộp riêng); thiếu `extras` ⇒ hộp tài sản (hộp tài sản tính cả bản cửa-MỞ đang ẩn). */
    fun visibleBounds(): Car3dBounds? {
        val mn = DoubleArray(3) { Double.MAX_VALUE }; val mx = DoubleArray(3) { -Double.MAX_VALUE }; var hit = false
        for (p in parts) {
            if (p.id in hidden) continue
            val b = p.box ?: return assetBounds
            for (k in AXES) { if (b[k] < mn[k]) mn[k] = b[k].toDouble(); if (b[k + 3] > mx[k]) mx[k] = b[k + 3].toDouble() }
            hit = true
        }
        if (!hit) return assetBounds
        return Car3dBounds(V3((mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2), V3((mx[0] - mn[0]) / 2, (mx[1] - mn[1]) / 2, (mx[2] - mn[2]) / 2))
    }

    fun partNames(): List<String> = parts.mapNotNull { it.name }.distinct()

    /** Nút khớp [pattern] theo TÊN hoặc NHÓM diễn hoạt (`extras.grp` — nắp cốp cắt từ thân giữ tên gốc). Nhớ theo mẫu. */
    private fun hitsOf(pattern: String): List<Part> = hits.getOrPut(pattern) {
        parts.filter { Car3dNames.matches(pattern, it.name) || Car3dNames.matches(pattern, it.group) }
    }

    fun hasPart(pattern: String): Boolean = hitsOf(pattern).isNotEmpty()
    fun hasHinge(pattern: String): Boolean = hitsOf(pattern).any { it.hinge != null }

    /** Neo → điểm khung MÔ HÌNH. Nút: hộp hợp của bộ phận khớp mẫu, độ cao theo `ty`. Không khớp ⇒ `null`. */
    fun anchor(a: Car3dAnchor?): V3? = when (a) {
        null -> null
        is Car3dAnchor.Point -> V3(a.x, a.y, a.z)
        is Car3dAnchor.FirstOf -> a.options.firstNotNullOfOrNull { anchor(it) }
        is Car3dAnchor.Node -> nodeBox(a.pattern)?.let { (mn, mx) -> V3((mn.x + mx.x) / 2, mn.y + (mx.y - mn.y) * a.ty, (mn.z + mx.z) / 2) }
    }

    fun box(a: Car3dAnchor?, posed: Boolean = false): Pair<V3, V3>? = when (a) {
        null -> null
        is Car3dAnchor.Point -> V3(a.x, a.y, a.z).let { it to it }
        is Car3dAnchor.FirstOf -> a.options.firstNotNullOfOrNull { box(it, posed) }
        is Car3dAnchor.Node -> nodeBox(a.pattern, posed)
    }

    /** Hộp THẾ GIỚI (min, max) của neo [a]: 8 góc hộp mô hình qua phép đặt [pl]. [posed] = cửa/nắp quay theo bản lề đang vẽ. */
    fun worldBox(pl: Car3dOrient.Placement, a: Car3dAnchor, posed: Boolean = false): Pair<V3, V3>? {
        val (bmn, bmx) = box(a, posed) ?: return null
        val wc = (0 until CORNERS).map { i -> pl.apply(V3(if (i and 1 == 0) bmn.x else bmx.x, if (i and 2 == 0) bmn.y else bmx.y, if (i and 4 == 0) bmn.z else bmx.z)) }
        return V3(wc.minOf { it.x }, wc.minOf { it.y }, wc.minOf { it.z }) to V3(wc.maxOf { it.x }, wc.maxOf { it.y }, wc.maxOf { it.z })
    }

    fun target(pl: Car3dOrient.Placement, h: Car3dHotspot, a: V3, posed: Boolean): Car3dPick.Target? =
        worldBox(pl, h.anchor, posed)?.let { (lo, hi) -> Car3dPick.Target(h.id, lo, hi, a) }

    /** Hộp khung MÔ HÌNH của mẫu; [posed] = theo bản lề hiện tại (KHÔNG theo kính trượt — chấm kính ở lại ô kính, 3.1.5). */
    private fun nodeBox(pattern: String, posed: Boolean = false): Pair<V3, V3>? {
        val mn = DoubleArray(3) { Double.MAX_VALUE }; val mx = DoubleArray(3) { -Double.MAX_VALUE }
        var hit = false
        for (p in hitsOf(pattern)) {
            val b = p.box ?: p.aabb ?: continue
            val pose = if (posed) swingOf[p.id] else null
            repeat(CORNERS) { i ->
                var q = V3(b[if (i and 1 == 0) 0 else 3].toDouble(), b[if (i and 2 == 0) 1 else 4].toDouble(), b[if (i and 4 == 0) 2 else 5].toDouble())
                if (pose != null) q = pose.first.apply(q, pose.second)
                val w = doubleArrayOf(q.x, q.y, q.z)
                for (k in AXES) { if (w[k] < mn[k]) mn[k] = w[k]; if (w[k] > mx[k]) mx[k] = w[k] }
            }
            hit = true
        }
        return if (!hit) null else V3(mn[0], mn[1], mn[2]) to V3(mx[0], mx[1], mx[2])
    }

    // ── Đổi cảnh: trả lệnh cho car3d-gl ──────────────────────────────────────────────────────────────────────────────────────────

    /** Hiện/ẩn theo [match] (tên → hiện?; `null` = không đụng). Trả các thay đổi (thực thể → hiện?); rỗng = không đổi. */
    fun show(match: (String?) -> Boolean?): Map<Int, Boolean> {
        val out = LinkedHashMap<Int, Boolean>()
        for (p in parts) {
            val want = match(p.name) ?: continue
            val isHidden = p.id in hidden
            if (want && isHidden) { hidden -= p.id; out[p.id] = true }
            if (!want && !isHidden) { hidden += p.id; out[p.id] = false }
        }
        return out
    }

    /**
     * Ẩn/hiện theo biến thể cửa/đèn (cùng luật [Car3dNames.visibleAll]: hiện khi MỌI biến thể nhận nút đồng ý). Bảng "nút ↔ biến thể"
     * dựng MỘT lần cho danh sách [variants] ([prepare] — car3d-gl gọi lúc nạp): [ĐO máy ảo 2 lõi 17/10] khớp mẫu từng tên × từng biến
     * thể mỗi lượt = 127 ms lượt đầu sau nạp, ~36 ms MỖI nhịp đổ số trên luồng chính. Nay mỗi lượt chỉ đọc bảng số.
     */
    fun showVariants(variants: List<Car3dVariant>, open: List<Boolean>): Map<Int, Boolean> {
        if (membFor !== variants) prepare(variants)
        val m = memb
        var i = 0
        return show { _ ->
            val codes = m[i++]
            var any = false; var all = true
            for (c in codes) { val v = if (c > 0) open[c - 1] else !open[-c - 1]; any = true; if (!v) all = false }
            if (any) all else null
        }
    }

    /** Dựng bảng "nút ↔ biến thể": mỗi nút một dãy mã `+(i+1)` = bản MỞ của biến thể i, `-(i+1)` = bản ĐÓNG (khớp đóng, không khớp mở). */
    fun prepare(variants: List<Car3dVariant>) {
        memb = parts.map { p ->
            variants.indices.mapNotNull { i ->
                when { Car3dNames.matches(variants[i].open, p.name) -> i + 1; Car3dNames.matches(variants[i].closed, p.name) -> -(i + 1); else -> null }
            }.toIntArray()
        }
        membFor = variants
    }
    private var memb: List<IntArray> = emptyList()
    private var membFor: List<Car3dVariant>? = null

    /** Kính TỤT [fraction] × chiều cao kính (theo TÊN nút — bản đóng + bản cửa mở). Trả biến đổi cục bộ mới; rỗng = không bộ phận nào. */
    fun slide(pattern: String, fraction: Float): Map<Int, FloatArray> {
        val out = LinkedHashMap<Int, FloatArray>()
        for (p in parts) {
            if (!Car3dNames.matches(pattern, p.name)) continue
            val b = p.box ?: continue
            slideOf[p.id] = -fraction.coerceIn(0f, 1f) * (b[4] - b[1]) * GLASS_TRAVEL
            out[p.id] = compose(p.id)
        }
        return out
    }

    /** Cửa/nắp mở dở: bộ phận khớp [pattern] CÓ bản lề quay tỉ lệ [s]. Trả biến đổi cục bộ mới; rỗng = không có bản lề. */
    fun swing(pattern: String, s: Float): Map<Int, FloatArray> {
        val out = LinkedHashMap<Int, FloatArray>()
        for (p in hitsOf(pattern)) {
            val h = p.hinge ?: continue
            swingOf[p.id] = h to s
            out[p.id] = compose(p.id)
        }
        return out
    }

    /** Biến đổi cục bộ = bản lề(s) · dịch kính(dy) — ma trận cột-chính. */
    private fun compose(e: Int): FloatArray {
        val dy = slideOf[e] ?: 0f
        val m = swingOf[e]?.let { (h, s) -> h.matrix(s) } ?: floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        // M · T(0,dy,0): cột 4 += dy · cột 2.
        m[12] += dy * m[4]; m[13] += dy * m[5]; m[14] += dy * m[6]
        return m
    }

    /** Thực thể nhận màu sơn ([PAINT_MATERIALS] theo `extras.mat` của nút — gồm cánh cửa mở). */
    fun paintTargets(): List<Int> = parts.filter { it.material in PAINT_MATERIALS }.map { it.id }

    /** Đèn khoang: `null` = không đổi trạng thái; khác ⇒ thực thể → `emissiveFactor` (bật = `extras.glow`, tắt = 0). */
    fun glow(on: Boolean): Map<Int, FloatArray>? {
        if (glowing == on) return null
        glowing = on
        return parts.mapNotNull { p -> p.glow?.let { g -> p.id to (if (on) g else OFF_GLOW) } }.toMap()
    }

    companion object {
        /** Kính mở hẳn tụt chừng này × chiều cao kính (chừa mép trên trong gioăng như kính thật hạ hết). */
        const val GLASS_TRAVEL = 0.92f
        /** Vật liệu sơn của lưới AVM (bộ đỉnh `paint` + `open` — cánh cửa mở). */
        val PAINT_MATERIALS = setOf("paint", "open")
        private val OFF_GLOW = floatArrayOf(0f, 0f, 0f)
        /** Ba trục x/y/z · 8 đỉnh hộp bao (không viết khoảng số cứng — bài `GridSeamGuardTest`). */
        private val AXES = IntRange(0, 2)
        private const val CORNERS = 8
    }
}
