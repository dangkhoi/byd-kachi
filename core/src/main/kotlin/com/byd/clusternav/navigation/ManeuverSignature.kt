package com.byd.clusternav.navigation

import com.byd.clusternav.navigation.ManeuverRegistry

/**
 * Suy HƯỚNG RẼ GMaps từ large-icon bằng "chữ ký tri giác" — PORT NGUYÊN VĂN cách Open BYD 2.3 / DashCast
 * (defpackage/wm0.java b()/c()/d() + bảng w40.e). ĐÂY là cách reference app cụm thật dùng, mạnh hơn
 * heuristic trọng-tâm cũ ([ArrowClassifier]) và KHÔNG cần lái thu thập bitmap (registry dựng sẵn 38 mũi tên).
 *
 * Quy trình:
 *   1) Hạ mẫu large-icon -> lưới 15×15 = 225 bit (1 = pixel mực mũi tên) qua composite alpha + ngưỡng cực đại.
 *   2) Khớp với [ManeuverRegistry] bằng khoảng cách Hamming ≤ 18 bit (gần nhất thắng).
 *   3) Tên maneuver -> mã AMAP NEW_ICON (enum của ta, để gửi broadcast — KHÁC mã HAL của app gốc).
 *
 * ⚠ Chữ ký PHẢI tính y hệt app gốc (cùng composite/ngưỡng) nếu không sẽ lệch >18 bit và trượt registry.
 * Mã giữ đúng từng bước của wm0.b với tham số c() dùng (f=1.0, z=false): ngưỡng = max -> đếm pixel sáng nhất.
 */
object ManeuverSignature {

    /**
     * Ghi chú chẩn đoán. Mặc định không làm gì.
     *
     * Trước 2026-07-27 lớp này gọi `android.util.Log` trực tiếp, và đó là một trong hai lý do 226 dòng
     * thuật toán nhận dạng hướng rẽ phải sống trong `:app` — cùng với việc nhận `android.graphics.Bitmap`.
     * Cả hai đều không phải nhu cầu thật của thuật toán. App gắn logger Android vào đây khi khởi tạo.
     */
    var note: (String) -> Unit = {}

    private const val TAG = "ManeuverSig"
    private const val GRID = 15                 // 15×15 = 225 ô
    private const val BITS = GRID * GRID
    private const val WORDS = (BITS + 63) / 64  // = 4 long
    private const val MAX_HAMMING = 18          // ngưỡng khớp của app gốc (wm0.d)
    // B3.11: query có < MIN_SIG_BITS bit set = crop TRỐNG / không có glyph → KHÔNG khớp. Template thật ≥18 bit
    // (Waze-trái=18, GMaps min=21); crop trống ~0-4. Chặn false-positive: trước đây query toàn-0 khớp nhầm
    // template thưa (Hamming 0→template ≤18) → phát tín hiệu rẽ GIẢ (vd màn HOME khi app background).
    private const val MIN_SIG_BITS = 10

    // DEBUG: tên maneuver khớp gần nhất + mã suy ra của lần chấm GẦN NHẤT BẤT KỲ LUỒNG NÀO.
    // ⚠ KHÔNG dùng để ghi dữ liệu chẩn đoán — đọc [classifyDetailed] để lấy tên đúng của CHÍNH khung mình
    //   vừa chấm (grep 2026-07-30: hiện KHÔNG màn hình nào đọc 2 field này, dù comment cũ nói "hiện trên
    //   MainActivity" — giữ lại vì rẻ, nhưng đừng tin nó thuộc về khung nào).
    @Volatile var lastName: String = "-"; private set
    @Volatile var lastAmap: Int = -1; private set

    /**
     * Đóng gói chuỗi 225-bit → LongArray(WORDS) (MSB-first per-word, y như ki0.a). Tách riêng để registry
     * dựng-sẵn ([registry]) và Waze ([wazePackedRegistry]) đóng gói CÙNG cách (DRY, B3.6).
     */
    private fun packBits(bits: String): LongArray {
        val s = LongArray(WORDS)
        for (i in bits.indices) if (bits[i] == '1') s[i ushr 6] = s[i ushr 6] or (1L shl (63 - (i and 63)))
        return s
    }

    /** Grayscale 0/1 của chuỗi bit (cho NCC). CHỈ [grayRegistry] (38 mục GMaps) dùng — xem KDoc [matchNCC]. */
    private fun grayBits(bits: String): FloatArray = FloatArray(bits.length) { if (bits[it] == '1') 1f else 0f }

    /** Registry dựng sẵn: chuỗi 225-bit -> LongArray(4) (đóng gói MSB-first y như ki0.a). */
    private val registry: List<Pair<LongArray, String>> by lazy {
        ManeuverRegistry.RAW.map { (bits, name) -> packBits(bits) to name }
    }

    /**
     * Kết quả MỘT lần khớp, trả TƯỜNG MINH cùng nhau.
     *
     * Vì sao cần, thay vì đọc [lastName]/[lastAmap] sau khi gọi [classify]: hai field đó là state CHUNG của
     * object, và từ 2026-07-30 có HAI call site chấm cùng một tấm ảnh từ HAI luồng khác nhau
     * (`AmapFrameBuilder` chạy trên worker `navigation-cluster-lane-delivery`, `NavArrowLog` chạy thêm trên
     * luồng main của nhịp tim). Đọc field sau lời gọi có thể lấy TÊN của khung ảnh KHÁC — và [classify] còn
     * return sớm mà KHÔNG ghi field khi ảnh null/quá nhỏ, nên tên đọc được có thể là tên CŨ còn sót.
     * Một dòng dữ liệu chẩn đoán mang tên sai còn tệ hơn không có dòng nào.
     */
    data class Match(
        val name: String,
        val amap: Int?,
        /** Mã icon HAL gốc (enum HudController) suy từ [name]; null khi không khớp. */
        val hal: Int? = null,
        /** [Maneuver] CÓ HƯỚNG — CHỈ họ vòng xuyến; null cho mọi tên khác (caller fallback `fromAmapIcon`). */
        val maneuver: Maneuver? = null,
    )

    /** Không có ảnh để chấm (null / nhỏ hơn 8×8) — KHÁC "(không khớp)" (có ảnh, chấm rồi, trượt registry). */
    const val NO_INPUT = "(không ảnh)"

    /** -> mã AMAP NEW_ICON từ ảnh mũi tên, hoặc null nếu mờ/không khớp. */
    fun classify(bmp: PixelFrame?): Int? = classifyDetailed(bmp).amap

    /** Như [classify] nhưng kèm TÊN registry đã khớp — hành vi/side-effect y hệt, chỉ trả thêm tên. */
    fun classifyDetailed(bmp: PixelFrame?): Match {
        if (bmp == null || bmp.width < 8 || bmp.height < 8) return Match(NO_INPUT, null)
        val s = signature(bmp) ?: run { set("(mờ)", -1); return Match("(mờ)", null) }
        val name = match(s.bits) ?: matchNCC(s.fill)   // #3: Hamming trượt → NCC fallback (suy giảm dần)
            ?: run {
                set("(không khớp)", -1); note("no match (Hamming>$MAX_HAMMING, NCC<$NCC_MIN)")
                return Match("(không khớp)", null)
            }
        val amap = nameToAmap(name)
        set(name, amap)
        note("sig '$name' -> amap=$amap")
        return Match(name, amap, nameToHal(name), nameToManeuver(name))
    }

    /**
     * Như [classifyDetailed] nhưng **CHỈ HAMMING — KHÔNG có nhánh NCC** (B3.53, 08-23). Dành cho caller crop
     * bằng một **rect PHỎNG ĐOÁN** thay vì đo được khung glyph.
     *
     * VÌ SAO CÓ HÀM NÀY (lỗi CÓ THẬT, [ĐO] 08-23 — không phải phòng xa). `CaptureRouter` tra rect ARROW cố
     * định theo `(target, W, H)` chứ **không theo package**, nên `CaptureCalibration.WAZE_ARROW_BANNER_D240`
     * — hiệu chuẩn trên banner **Waze** — cũng được áp lên khung **VietMap**. Crop sai chỗ đó vẫn có mực,
     * vẫn ra chữ ký, và [matchNCC] là khớp MỀM (ngưỡng [NCC_MIN] = 0.45, không có lớp "không biết") nên nó
     * **luôn tìm được một cái tên**. Đo trên 87 khung VietMap ghép từ asset (fixture
     * `core/src/test/resources/diagnostics/vietmap-glyph/`), crop qua đúng rect cố định:
     * ```
     * depart_right      -> maneuver_roundabout_enter_and_exit_cw_normal_left  amap=11  (đúng: 3)   Hamming 27
     * fork_slight_right -> maneuver_turn_normal_right                         amap=3   (đúng: 5)   Hamming 37
     * fork              -> maneuver_turn_normal_right                         amap=3               Hamming 38
     * ```
     * Cả 3 đều **trượt Hamming rất xa** (27/37/38 ≫ [MAX_HAMMING] = 18) và chỉ ra được mã nhờ NCC ⇒ hướng
     * hiển thị là bốc thăm. Mũi tên SAI HƯỚNG trên xe đang lăn bánh nguy hiểm hơn hẳn không hiện gì.
     *
     * ⚠ KHÔNG được đem hàm này thay [classifyDetailed] ở đường **notification large-icon** — đó là đường NCC
     * SINH RA ĐỂ PHỤC VỤ (GMaps đổi style icon ⇒ chữ ký lệch > 18 bit ⇒ "vực im lặng") và là đường đã chạy
     * thật ngoài hiện trường (CLAUDE.md §6). [ĐO] trên 418 khung GMaps nhiễu-hình-học (dịch 1 ô / dilate):
     * bỏ NCC ở đó làm **256/418 khung đổi kết quả**. Ngược lại, ở tier rect-cố-định thì trên 3 khung Waze
     * THẬT đã lưu fixture, delta = **0** (hai khung khớp bằng Hamming 15 và 18; khung "compact" vốn đã
     * `(không khớp)` vì NCC cũng không cứu được). Khoá bằng
     * `FixedRectSoftMatchTest` (:core) + `ScreenCaptureNavSourceContractTest` (:app).
     *
     * Ranh giới quyết định: rect ĐO ĐƯỢC **cho đúng target đang chấm** (a11y node chọn cho ARROW, hoặc bbox
     * mực do `NavGlyphLocator` dò) ⇒ giả định "crop đúng quy ước khung vẽ" có căn cứ ⇒ giữ nguyên đường cũ.
     * Rect **hiệu chuẩn sẵn** ⇒ chính cái giả định đó là thứ đang bị nghi ngờ ⇒ chỉ chấp nhận khớp CỨNG. Đây
     * là phép ĐO (khớp trong quả cầu Hamming 18 bit của một template), KHÔNG phải rẽ nhánh theo tên gói
     * (CLAUDE.md §7).
     *
     * ⚠ ĐÍNH CHÍNH 08-23 (vòng review): mệnh đề "rect a11y là bounds đo được của chính node mũi tên" chỉ
     * đúng TỪ bản vá `CaptureBounds.target`. Trước đó holder tier-1 không mang mục tiêu, nên với VietMap
     * (`CaptureTarget.forPackage` = CAMERA) plan ARROW nhận rect **icon camera** mà vẫn mang nhãn
     * `A11Y_DYNAMIC` ⇒ vẫn đi đường khớp MỀM. Xem KDoc
     * `com.byd.clusternav.navigation.screencapture.CaptureBounds.target`.
     *
     * KHÔNG ghi [lastName]/[lastAmap] (giống [classifyWazeInk]) — hai field đó là state chung, xem KDoc của chúng.
     */
    fun classifyStrict(bmp: PixelFrame?): Match {
        if (bmp == null || bmp.width < 8 || bmp.height < 8) return Match(NO_INPUT, null)
        val s = signature(bmp) ?: return Match("(mờ)", null)
        val name = match(s.bits) ?: run {
            note("no strict match (Hamming>$MAX_HAMMING) — rect cố định không khớp bố cục nào đã hiệu chuẩn")
            return Match("(không khớp)", null)
        }
        val amap = nameToAmap(name)
        note("sig-strict '$name' -> amap=$amap")
        return Match(name, amap, nameToHal(name), nameToManeuver(name))
    }

    private fun set(n: String, a: Int) { lastName = n; lastAmap = a }

    /** -> mã icon HAL GỐC (1..49, enum HudController) từ ảnh, hoặc null. Cho đường ghi-thẳng-HAL (dadb/NavOpen). */
    fun classifyHal(bmp: PixelFrame?): Int? {
        if (bmp == null || bmp.width < 8 || bmp.height < 8) return null
        val s = signature(bmp) ?: return null
        val name = match(s.bits) ?: matchNCC(s.fill) ?: return null   // #3: NCC fallback
        return nameToHal(name)
    }

    /**
     * -> [Maneuver] CÓ HƯỚNG cho HỌ VÒNG XUYẾN, hoặc null nếu KHÔNG phải vòng xuyến (caller fallback sang
     * [Maneuver.fromAmapIcon] — KHÔNG đổi hành vi non-roundabout) / ảnh null/quá nhỏ / không khớp registry.
     *
     * VÌ SAO (TASK 1 closeout 1.28): bottleneck AMAP-int ([classify] → 11 → [Maneuver.fromAmapIcon] →
     * ROUNDABOUT generic) VỨT hướng ra vòng xuyến mà GMaps large-icon ĐÃ phân biệt (registry có
     * ..._ccw/_cw_{normal,slight,sharp}_{left,right} · _straight · _u_turn · _exit thuần · enter generic).
     * Đường này giữ NGUYÊN pipeline chữ ký (signature+match/matchNCC — cùng guard null/nhỏ như [classify])
     * nhưng map TÊN → Maneuver có hướng CHỈ cho họ vòng xuyến, để HUD/centre ra CAN đúng
     * (15/18/20/22 CCW · 16/17/19/21 CW). Không đụng [classify]/[classifyDetailed]/[classifyHal].
     */
    fun classifyManeuver(bmp: PixelFrame?): Maneuver? {
        if (bmp == null || bmp.width < 8 || bmp.height < 8) return null
        val s = signature(bmp) ?: return null
        val name = match(s.bits) ?: matchNCC(s.fill) ?: return null   // #3: NCC fallback (như classify/classifyHal)
        return nameToManeuver(name)
    }

    /**
     * Chữ ký 225-bit của [frame] dưới dạng chuỗi '0'/'1' — ĐÚNG format [ManeuverRegistry].RAW lưu (per-word
     * MSB-first). B3.6: PUBLIC + test được để orchestrator sinh template Waze/VietMap THẬT từ ảnh PNG crop rồi
     * nạp vào [WazeArrowRegistry]. TÁI DÙNG [signature] (KHÔNG đổi math của nó) rồi giải-đóng-gói bit về chuỗi.
     *
     * Trả null nếu ảnh quá nhỏ (<8×8, cùng guard [classify]) / mờ (contrast<30 → [signature] null) / không đọc
     * được pixel. Cùng khung [frame] → cùng chuỗi (tất định); nạp CHÍNH chuỗi này vào [WazeArrowRegistry] rồi
     * classify lại khung đó ⇒ Hamming=0 ⇒ khớp (xem WazeArrowRegistryTest).
     */
    fun signatureBits(frame: PixelFrame): String? {
        if (frame.width < 8 || frame.height < 8) return null
        val s = signature(frame) ?: return null
        val sb = StringBuilder(BITS)
        for (i in 0 until BITS) {
            val set = (s.bits[i ushr 6] ushr (63 - (i and 63))) and 1L
            sb.append(if (set == 1L) '1' else '0')
        }
        return sb.toString()
    }

    // ── chữ ký 225-bit (port wm0.c -> wm0.b với f=1.0, z=false) ──
    private fun signature(bmp: PixelFrame): Sig? {
        val w = bmp.width; val h = bmp.height
        val n = w * h
        if (n <= 0) return null
        val px = bmp.argb() ?: return null

        // z2: ảnh có alpha? — app gốc CHỈ xét pixel[0] (giữ y nguyên dù lạ; large-icon góc trên trong suốt).
        val hasAlpha = ((px[0] ushr 24) and 0xFF) < 255

        val gray = IntArray(n)
        val alpha = if (hasAlpha) IntArray(n) else IntArray(0)
        var opaque = 0; var graySum = 0L
        for (i in 0 until n) {
            val c = px[i]
            val g = (((c ushr 16) and 0xFF) + ((c ushr 8) and 0xFF) + (c and 0xFF)) / 3
            gray[i] = g
            if (hasAlpha) {
                val a = (c ushr 24) and 0xFF
                alpha[i] = a
                if (a > 0) { opaque++; graySum += g }
            }
        }

        // z3: cực tính (mũi tên sáng-trên-tối hay tối-trên-sáng) -> chọn nền composite.
        val z3 = if (!hasAlpha || opaque >= n) median(gray, n) >= 128
                 else !(opaque > 0 && graySum.toFloat() / opaque >= 128f)

        // composite + min/max độ sáng.
        val comp: IntArray
        var mn = 255; var mx = 0
        if (hasAlpha) {
            comp = IntArray(n)
            val bg = if (z3) 255 else 47
            for (i in 0 until n) {
                val a = alpha[i]
                val v = ((255 - a) * bg + gray[i] * a) / 255
                comp[i] = v
                if (v > mx) mx = v; if (v < mn) mn = v
            }
        } else {
            comp = gray
            for (i in 0 until n) { val v = gray[i]; if (v > mx) mx = v; if (v < mn) mn = v }
        }
        val contrast = mx - mn
        if (contrast < 30) return null                 // quá mờ -> bỏ (wm0.b trả null)

        val thr = mn + contrast                        // z4=false, f=1.0 -> ngưỡng = max; đếm pixel >= max

        val sig = LongArray(WORDS)
        val fill = FloatArray(BITS)                    // #3: tỉ lệ lấp mỗi ô (0..1) → dùng cho NCC fallback
        var cell = 0
        for (gy in 0 until GRID) {
            val y0 = gy * h / GRID
            var y1 = (gy + 1) * h / GRID; if (y1 > h) y1 = h
            for (gx in 0 until GRID) {
                val x0 = gx * w / GRID
                var x1 = (gx + 1) * w / GRID; if (x1 > w) x1 = w
                val area = (y1 - y0) * (x1 - x0)
                var cnt = 0
                var yy = y0
                while (yy < y1) {
                    val base = yy * w
                    var xx = x0
                    while (xx < x1) { if (comp[base + xx] >= thr) cnt++; xx++ }
                    yy++
                }
                if (area > 0) fill[cell] = cnt.toFloat() / area
                if (cnt > (area * 0.5f).toInt()) sig[cell ushr 6] = sig[cell ushr 6] or (1L shl (63 - (cell and 63)))
                cell++
            }
        }
        return Sig(sig, fill)
    }

    // #3: NCC FALLBACK — khi Hamming trượt (GMaps đổi style icon → chữ ký lệch >18 bit = "vực im lặng"), khớp mềm
    // bằng normalized cross-correlation giữa tỉ-lệ-lấp-ô (grayscale) và 38 template (bit 0/1) → suy giảm dần thay vì null.
    private class Sig(val bits: LongArray, val fill: FloatArray)   // gói bits+fill, truyền tường minh (bỏ field ngầm → thread-safe)
    private val grayRegistry: List<Pair<FloatArray, String>> by lazy {
        ManeuverRegistry.RAW.map { (bits, name) -> grayBits(bits) to name }
    }
    private const val NCC_MIN = 0.45f              // ngưỡng khớp mềm (thực nghiệm; dưới = coi như không ra)

    /**
     * Khớp MỀM chỉ trong 38 mục GMaps — **KHÔNG quét [WazeArrowRegistry]** (gỡ 08-23 vòng 1, [P0]; xem [match]).
     *
     * VÌ SAO NHÁNH NÀY LÀ CHỖ NGUY HIỂM NHẤT để trộn registry: lập luận an toàn duy nhất mà repo có cho việc
     * để hai registry cạnh nhau là bất đẳng thức tam giác trên HAMMING (biên 2×18+1 = 37, khoá bằng
     * `WazeArrowRegistryTest.template muc KHONG BAO GIO lot nguong Hamming cua mot khung GMaps`). NCC KHÔNG
     * phải Hamming — nó là tương quan trên tỉ-lệ-lấp-ô, nên một template cách 39 bit vẫn có thể ghi điểm NCC
     * CAO HƠN người thắng cũ. Đo 08-23 vòng 2 trên nhiễu HÌNH HỌC (dịch 1 ô + dilate) của 38 khung GMaps:
     * **33/418 khung đổi mã AMAP** khi nạp thêm registry mực, gồm 11 ca vòng-xuyến→đi-thẳng và 6 ca
     * im-lặng→có-hướng. Đó là đổi hành vi của đường notification đang chạy ngoài hiện trường (CLAUDE.md §6).
     */
    private fun matchNCC(q: FloatArray): String? = matchNccIn(q, grayRegistry)?.first

    /** Trung vị histogram (port wm0.a). */
    private fun median(arr: IntArray, n: Int): Int {
        val hist = IntArray(256)
        for (i in 0 until n) { var v = arr[i]; if (v < 0) v = 0 else if (v > 255) v = 255; hist[v]++ }
        val half = n / 2 + 1
        var cum = 0
        for (v in 0..255) { cum += hist[v]; if (cum >= half) return v }
        return 0
    }

    /**
     * Khớp gần nhất theo Hamming ≤ [MAX_HAMMING] (port wm0.d) trong ĐÚNG MỘT registry — lõi dùng chung của
     * [match] (38 mục GMaps) và [classifyWazeInk] ([WazeArrowRegistry]). Trả (tên, khoảng cách) hoặc null nếu
     * chữ ký quá thưa / không mục nào trong ngưỡng.
     *
     * ⚠ Tham số [reg] là MỘT registry, không phải danh sách registry: hai bộ template nằm ở hai QUY ƯỚC CROP
     * khác nhau nên không bao giờ được quét chung (xem KDoc [match]).
     */
    private fun matchIn(
        sig: LongArray,
        reg: List<Pair<LongArray, String>>,
        exactWins: Boolean = false,
    ): Pair<String, Int>? {
        var setBits = 0
        for (k in 0 until WORDS) setBits += java.lang.Long.bitCount(sig[k])
        if (setBits < MIN_SIG_BITS) return null      // B3.11: crop trống → không khớp bừa
        var best: String? = null
        var bestD = Int.MAX_VALUE
        for ((r, name) in reg) {
            var d = 0
            for (k in 0 until WORDS) d += java.lang.Long.bitCount(sig[k] xor r[k])
            if (d == 0 && exactWins) return name to 0
            if (d <= MAX_HAMMING && d < bestD) { bestD = d; best = name }
        }
        return best?.let { it to bestD }
    }

    /**
     * Khớp NCC trong ĐÚNG một registry. Trả (tên, điểm NCC) hoặc null nếu không mục nào vượt [NCC_MIN].
     *
     * Call site DUY NHẤT là [matchNCC] (38 mục GMaps). [classifyWazeInk] CỐ Ý không có nhánh mềm nào — xem
     * KDoc của nó.
     */
    private fun matchNccIn(q: FloatArray, reg: List<Pair<FloatArray, String>>): Pair<String, Float>? {
        var mq = 0f; for (v in q) mq += v; mq /= q.size
        var vq = 0f; for (v in q) { val dq = v - mq; vq += dq * dq }
        if (vq < 1e-6f) return null
        var best: String? = null; var bestNcc = NCC_MIN
        for ((t, name) in reg) {
            if (t.size != q.size) continue
            var mt = 0f; for (v in t) mt += v; mt /= t.size
            var cov = 0f; var vt = 0f
            for (i in q.indices) { val dq = q[i] - mq; val dt = t[i] - mt; cov += dq * dt; vt += dt * dt }
            if (vt < 1e-6f) continue
            val ncc = cov / kotlin.math.sqrt(vq * vt)
            if (ncc > bestNcc) { bestNcc = ncc; best = name }
        }
        return best?.let { it to bestNcc }
    }

    /**
     * Khớp Hamming CHỈ trong 38 mục GMaps ([registry]) — quy ước "KHUNG VẼ CÓ LỀ".
     *
     * ⚠ **KHÔNG quét [WazeArrowRegistry]** (gỡ 08-23 vòng 1, [P0]). Trước đó [match]/[matchNCC] quét CHUNG hai
     * registry, tức đem crop quy ước "khung vẽ" đi khớp template quy ước "bbox mực" — đúng cái lai quy ước mà
     * KDoc [classifyWazeInk] đã đo là cho SAI hướng (4/9 khung ra `off_ramp_normal_left` thay vì
     * `turn_normal_left`). Hai quy ước ⇒ HAI matcher, không trộn:
     *
     *   · large-icon notification GMaps + rect cố định screen-capture → [match]/[matchNCC] → 38 mục GMaps;
     *   · bbox mực do `NavGlyphLocator` dò → [classifyWazeInk] → [WazeArrowRegistry].
     *
     * Lợi ích của việc trộn = 0 (đường notification chỉ còn GMaps đi tới), rủi ro thì đo được: 33/418 khung
     * GMaps nhiễu-hình-học đổi mã AMAP — xem [matchNCC]. Khoá bằng
     * `WazeArrowRegistryTest.khung GMaps NHIEU HINH HOC — registry muc khong duoc doi ket qua`.
     */
    private fun match(sig: LongArray): String? = matchIn(sig, registry, exactWins = true)?.first

    /** Tên maneuver (app gốc) -> mã AMAP NEW_ICON của ta (đặc thù trước, generic sau). */
    private fun nameToAmap(name: String): Int = when {
        // Track B: RA KHỎI vòng xuyến (chữ ký "..._exit_ccw/_cw" THUẦN, KHÔNG "enter_and_exit") → NEW_ICON 12
        //   (驶出环岛 → CAN 24), KHÔNG phải "vào" (11 → CAN 13). Guard !enter loại 16 tên "enter_and_exit"
        //   (glyph vòng-xuyến chính vẫn = 11). Sửa defect §4 #37/#38 "exit hiện thành enter" + hết dead-enum
        //   ROUNDABOUT_EXIT. On-car verify: GMaps có phát icon thoát-vòng-xuyến riêng không (cùng caveat nguồn như hầm).
        name.contains("roundabout") && name.contains("exit") && !name.contains("enter") -> 12
        name.contains("roundabout") -> 11    // Q1: xét TRƯỚC u_turn — tên "roundabout..._u_turn" phải ra vòng-xuyến, không phải quay-đầu
        name.contains("u_turn") && name.contains("right") -> 19   // Track B: quay đầu PHẢI (NEW_ICON 19→CAN10); TRƯỚC nhánh u_turn chung (giữ 8=trái)
        name.contains("u_turn") -> 8
        name.contains("destination") -> 15
        // "depart"/"start" = bước đầu GMaps ("Head/Đi về hướng...") = ĐI THẲNG ra đường, KHÔNG phải điểm-mốc.
        // Trước map -> 1 (glyph "hình ghim + xe" start-point) khiến lúc bắt đầu đi cụm hiện ghim thay vì mũi tên thẳng.
        name.contains("depart") || name.contains("start") -> 9
        name.contains("sharp_left") -> 6
        name.contains("sharp_right") -> 7
        name.contains("slight_left") || name.contains("fork_left") -> 4
        name.contains("slight_right") || name.contains("fork_right") -> 5
        // Track B: off/on-ramp = tách làn NHẸ ≈ chếch (4/5), KHÔNG phải cua 90°. TRƯỚC normal_left/right vì tên
        //   "off_ramp_normal_left" chứa cả "ramp" LẪN "normal_left" → phải bắt ramp trước (sửa mismap hard-turn).
        name.contains("ramp") && name.contains("left") -> 4
        name.contains("ramp") && name.contains("right") -> 5
        name.contains("normal_left") || name.contains("turn_left") -> 2
        name.contains("normal_right") || name.contains("turn_right") -> 3
        name.contains("merge") -> 9    // Track B: merge/nhập làn → ĐI THẲNG (0..28 KHÔNG có glyph merge; sửa bug owner "merge hiện rẽ phải"). Trước: 5=chếch phải.
        name.contains("straight") -> 9
        name.endsWith("_left") -> 2
        name.endsWith("_right") -> 3
        else -> 9
    }

    /** Tên maneuver -> mã icon HAL gốc (enum HudController TURN_ICON_*, port w40.a). Vòng xuyến gộp ~đúng. */
    private fun nameToHal(name: String): Int = when {
        // Track B: RA KHỎI vòng xuyến (chữ ký "..._exit" THUẦN) → CAN 24 (= TurnIdMapToCAN[12]), khớp làn cụm
        //   (12 → 24). Guard !enter loại "enter_and_exit". Giữ hai đầu ra KHÔNG lệch cho ROUNDABOUT_EXIT.
        name.contains("roundabout") && name.contains("exit") && !name.contains("enter") -> 24
        name.contains("roundabout") -> 20          // Q1: xét TRƯỚC u_turn (tên roundabout..._u_turn = vòng-xuyến); chi tiết 15-22 để sau
        name.contains("u_turn_left") -> 9
        name.contains("u_turn_right") -> 10
        name.contains("u_turn") -> 9
        name.contains("destination") -> 48
        name.contains("depart") -> 12
        name.contains("sharp_left") -> 7
        name.contains("sharp_right") -> 8
        name.contains("slight_left") || name.contains("fork_left") -> 3
        name.contains("slight_right") || name.contains("fork_right") -> 5
        // Track B: ramp = tách làn NHẸ ≈ chếch (CAN 3/5), KHÔNG phải cua thường (1/2). TRƯỚC normal_left/right.
        name.contains("ramp") && name.contains("left") -> 3
        name.contains("ramp") && name.contains("right") -> 5
        name.contains("normal_left") || name.contains("turn_left") -> 1
        name.contains("normal_right") || name.contains("turn_right") -> 2
        name.contains("merge") -> 11    // đã đúng: merge = đi thẳng (CAN 11). Track B giữ nguyên (chỉ làn cụm/AMAP sai trước đây).
        name.contains("straight") -> 11
        name.endsWith("_left") -> 1
        name.endsWith("_right") -> 2
        else -> 11
    }

    /**
     * Tên maneuver -> [Maneuver] CÓ HƯỚNG cho HỌ VÒNG XUYẾN; null cho MỌI tên khác (caller giữ đường
     * [Maneuver.fromAmapIcon] cũ → KHÔNG đổi hành vi non-roundabout). Thứ tự guard mirror [nameToAmap]/[nameToHal]:
     *   - "_exit" THUẦN (`roundabout` & `exit` & !`enter`) → [Maneuver.ROUNDABOUT_EXIT] (member sẵn có; giữ
     *     đúng ngữ nghĩa nameToHal 24 / nameToAmap 12 cho thoát vòng xuyến, KHÔNG rơi về hướng).
     *   - u_turn / left / right / straight → member có hướng; hậu tố `_cw` = CW (LHT), còn lại (`_ccw` /
     *     không ghi chiều) = CCW (mặc định VN).
     *   - `roundabout` còn lại (enter_ccw generic / enter_and_exit không ghi hướng) → [Maneuver.ROUNDABOUT] generic.
     *   - KHÔNG chứa `roundabout` → null (caller fallback fromAmapIcon).
     *
     * CW detect = `name.contains("_cw")`: chuỗi "_ccw" KHÔNG chứa "_cw" (các substring độ dài 3 của "_ccw"
     * là "_cc" và "ccw", không có "_cw") nên tên CCW đi ĐÚNG nhánh non-CW.
     */
    private fun nameToManeuver(name: String): Maneuver? = when {
        name.contains("roundabout") && name.contains("exit") && !name.contains("enter") -> Maneuver.ROUNDABOUT_EXIT
        name.contains("roundabout") && name.contains("u_turn") ->
            if (name.contains("_cw")) Maneuver.ROUNDABOUT_UTURN_CW else Maneuver.ROUNDABOUT_UTURN
        name.contains("roundabout") && name.contains("left") ->
            if (name.contains("_cw")) Maneuver.ROUNDABOUT_LEFT_CW else Maneuver.ROUNDABOUT_LEFT
        name.contains("roundabout") && name.contains("right") ->
            if (name.contains("_cw")) Maneuver.ROUNDABOUT_RIGHT_CW else Maneuver.ROUNDABOUT_RIGHT
        name.contains("roundabout") && name.contains("straight") ->
            if (name.contains("_cw")) Maneuver.ROUNDABOUT_STRAIGHT_CW else Maneuver.ROUNDABOUT_STRAIGHT
        name.contains("roundabout") -> Maneuver.ROUNDABOUT
        else -> null
    }
}
