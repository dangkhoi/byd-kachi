package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ V-CLUSTER · VC-R4 — BỘ KIỂM hình học chiếu TRƯỚC khi nó chạm shell [P0] ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §11.4.4. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Bệnh nó chữa
 * [ĐO code b5c0e87] bốn chuỗi của [DisplayConfig] đi **nguyên văn** vào lệnh shell: `wm size ${wmSize}`,
 * `wm overscan ${overscan}`, `wm density ${density}` ([DisplayConfigurator.apply]) và `am task resize … ${bounds}`
 * (`CastGeometryController.applySavedProfile`, nay `applyPinned`); còn khung được đọc bằng `toInt()` trần (`SimpleCastRuntime`), tức một
 * ký tự lạ là `NumberFormatException` trong dịch vụ đang chạy. Tới 2.83 chỉ mã app ghi các khoá này nên chưa ai
 * khai thác được. V-CLUSTER đưa họ `config_*` vào **ảnh chụp hồ sơ** ⇒ nó đi theo **tệp xuất/nhập** mà anh em gửi
 * nhau trong nhóm — một đường vào từ bên ngoài. `240;reboot` trong một tệp nhập là một lệnh thứ hai chạy bằng shell
 * adb của đầu xe.
 *
 * Cùng khuôn *"chuỗi hồ sơ là dữ liệu KHÔNG TIN CẬY, nó đi thẳng vào lệnh shell"* của `ClusterProfile.SVC_OK` +
 * dải `1..8192`: **regex neo hai đầu trước, số sau**. Regex dùng lớp `[0-9]` tường minh chứ không để `toIntOrNull`
 * quyết một mình — hàm đó nhận cả chữ số Unicode (`"٣٢٠"` → 320), tức một chuỗi không phải ASCII lọt qua.
 *
 * ## Không hàm nào ở đây NÉM
 * Chỗ gọi là lượt đọc cấu hình của dịch vụ chiếu và lượt đổi hồ sơ. Giá trị sai ⇒ trả `null` (hoặc mặc định an
 * toàn) và để chỗ gọi GHI LOG; một ký tự hỏng không được làm sập cụm trước mặt người lái.
 */
object CastGeometryGuard {

    /** Dải DPI hợp lệ của `wm density` — nguồn DUY NHẤT (trước đây `80..640` viết tay ở `CastDensityControl`). */
    val DENSITY_RANGE: IntRange = 80..640

    /** Giá trị `density` nghĩa là *"trả DPI về mặc định của màn"* (`wm density reset`). */
    const val DENSITY_RESET = "reset"

    /** Trần một cạnh (px) — cùng dải `1..8192` mà `ClusterProfile.parse` ép cho kích cụm. */
    const val DIMENSION_MAX = 8192

    /** Trần trị tuyệt đối một cạnh overscan. CarPlay dùng số ÂM thật (`10,-120,10,50`, [DisplayConfig.CARPLAY]). */
    const val OVERSCAN_ABS_MAX = 4096

    /** Overscan khi khoá vắng hoặc hỏng — đúng giá trị `SimpleCastRuntime` lùi về từ trước. */
    const val OVERSCAN_NONE = "0,0,0,0"

    /**
     * Tên gói Android — CÙNG mẫu `CastAppCatalog.PACKAGE` (`:app`): mỗi đoạn bắt đầu bằng chữ, ít nhất một dấu chấm.
     * Không có `$`, `(`, `;`, khoảng trắng ⇒ một khoá họ `config_*` không thể mang lệnh trong **tên** của nó.
     */
    val PACKAGE: Regex = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    /** Bốn trường của MỘT bản ghi hình học ([DisplayConfig]) — cả bốn luôn đi cùng nhau (spec K4). */
    enum class Field(val prefix: String) {
        SIZE("config_size_"),
        OVERSCAN("config_overscan_"),
        DENSITY("config_density_"),
        BOUNDS("config_bounds_"),
    }

    /** Tiền tố khoá của họ `cast_geometry`, sinh từ [Field] — không chép tay chỗ khác. */
    val KEY_PREFIXES: List<String> = Field.values().map { it.prefix }

    /**
     * Khoá hợp lệ của họ, neo hai đầu: `config_<trường>_<gói>` (FULL) hoặc `…__L<pct>`/`…__R<pct>` (một nửa).
     * Dải tỉ lệ **sinh từ** [CastProfile.SPLIT_PERCENTS] — nới dải ở đó là regex tự theo.
     */
    val FAMILY_KEY: Regex = Regex(
        "^config_(size|overscan|density|bounds)_" + PACKAGE.pattern +
            "(__[LR](" + CastProfile.SPLIT_PERCENTS.joinToString("|") + "))?$",
    )

    private val SIZE_RE = Regex("^[0-9]{1,4}x[0-9]{1,4}$")
    private val OVERSCAN_RE = Regex("^-?[0-9]{1,4}(,-?[0-9]{1,4}){3}$")
    private val DENSITY_RE = Regex("^[0-9]{2,3}$")
    private val BOUNDS_RE = Regex("^[0-9]{1,5}(,[0-9]{1,5}){3}$")

    /** Khoá [key] có thuộc họ `cast_geometry` không (tên gói + hậu tố tỉ lệ đều hợp lệ). */
    fun isFamilyKey(key: String): Boolean = FAMILY_KEY.matches(key)

    /** Trường của một khoá họ, theo tiền tố. `null` = không thuộc họ. */
    fun fieldOf(key: String): Field? = Field.values().firstOrNull { key.startsWith(it.prefix) }

    /** `"reset"` hoặc số nguyên trong [DENSITY_RANGE], dạng chuẩn; mọi thứ khác ⇒ `null`. */
    fun density(raw: String?): String? {
        if (raw == DENSITY_RESET) return raw
        if (raw == null || !DENSITY_RE.matches(raw)) return null
        return raw.toInt().takeIf { it in DENSITY_RANGE }?.toString()
    }

    /** `WxH`, mỗi cạnh `1..`[DIMENSION_MAX]; mọi thứ khác ⇒ `null`. */
    fun wmSize(raw: String?): String? {
        if (raw == null || !SIZE_RE.matches(raw)) return null
        val (w, h) = raw.split('x').map { it.toInt() }
        return raw.takeIf { w in 1..DIMENSION_MAX && h in 1..DIMENSION_MAX }
    }

    /** Bốn số `l,t,r,b` (cho phép âm), mỗi số trong `±`[OVERSCAN_ABS_MAX]; mọi thứ khác ⇒ `null`. */
    fun overscan(raw: String?): String? {
        if (raw == null || !OVERSCAN_RE.matches(raw)) return null
        return raw.takeIf { raw.split(',').all { it.toInt() in -OVERSCAN_ABS_MAX..OVERSCAN_ABS_MAX } }
    }

    /** Bốn số không âm `l,t,r,b` với `l<r`, `t<b`, mọi số ≤ [DIMENSION_MAX]; mọi thứ khác ⇒ `null`. */
    fun bounds(raw: String?): CastBounds? {
        if (raw == null || !BOUNDS_RE.matches(raw)) return null
        val (l, t, r, b) = raw.split(',').map { it.toInt() }
        return CastBounds(l, t, r, b).takeIf { boundsOk(it) }
    }

    /** Bất biến của một khung đã dựng (đi cả đường không qua chuỗi, ví dụ khung tính từ UI). */
    fun boundsOk(b: CastBounds): Boolean =
        b.left >= 0 && b.top >= 0 && b.left < b.right && b.top < b.bottom &&
            b.right <= DIMENSION_MAX && b.bottom <= DIMENSION_MAX

    /** Giá trị [value] có hợp lệ cho khoá họ [key] không — lớp 1/2 (lúc nhập, lúc áp ảnh chụp). */
    fun isValidValue(key: String, value: String): Boolean = when (fieldOf(key)) {
        Field.SIZE -> wmSize(value) != null
        Field.OVERSCAN -> overscan(value) != null
        Field.DENSITY -> density(value) != null
        Field.BOUNDS -> bounds(value) != null
        null -> false
    }

    /**
     * **Chốt cuối** ngay trước nội suy vào lệnh: mọi trường của [config] đã ở dạng chuẩn hợp lệ.
     *
     * So bằng `==` với dạng chuẩn chứ không chỉ "parse được": `"0240"` parse ra 240 nhưng không phải chuỗi sẽ đi vào
     * lệnh — chốt cuối phải nói về đúng byte sắp gửi.
     */
    fun isShellSafe(config: DisplayConfig): Boolean =
        wmSize(config.wmSize) == config.wmSize &&
            overscan(config.overscan) == config.overscan &&
            density(config.density) == config.density &&
            (config.bounds == null || boundsOk(config.bounds))

    /**
     * Lớp 3 — dựng [DisplayConfig] từ bốn chuỗi đọc ở tệp sống, **không bao giờ ném**.
     *
     * Hành vi giữ nguyên với bản cũ ở mọi giá trị hợp lệ: vắng `size` ⇒ cả bản ghi `null` (bản cũ `?: return null`),
     * vắng `overscan` ⇒ [OVERSCAN_NONE], vắng `density` ⇒ [DENSITY_RESET], vắng `bounds` ⇒ `null`. Giá trị **hỏng**
     * được đối xử như vắng mặt và báo qua [log] — trước đây `bounds` hỏng là `NumberFormatException`.
     */
    fun readConfig(
        size: String?,
        overscan: String?,
        density: String?,
        bounds: String?,
        log: (String) -> Unit,
    ): DisplayConfig? {
        if (size == null) return null
        val wm = wmSize(size) ?: run {
            log("bỏ config_size hỏng: '${shown(size)}' ⇒ cả bản ghi coi như vắng")
            return null
        }
        val os = overscan?.let { raw -> overscan(raw) ?: OVERSCAN_NONE.also { log("bỏ overscan hỏng: '${shown(raw)}'") } }
        val dpi = density?.let { raw -> density(raw) ?: DENSITY_RESET.also { log("bỏ density hỏng: '${shown(raw)}'") } }
        val box = bounds?.let { raw -> bounds(raw).also { if (it == null) log("bỏ bounds hỏng: '${shown(raw)}'") } }
        return DisplayConfig(wmSize = wm, overscan = os ?: OVERSCAN_NONE, density = dpi ?: DENSITY_RESET, bounds = box)
    }

    /**
     * Lớp ghi — chuẩn hoá [config] trước khi lưu: trường phụ hỏng ⇒ mặc định an toàn; `wmSize` hỏng ⇒ `null` (chỗ
     * gọi KHÔNG ghi, vì thiếu kích thước thì cả bản ghi vô nghĩa, spec K4).
     *
     * Ca thật: `CastDensityControl.set` lưu `dpi.toString()` kể cả khi `dpi` ngoài [DENSITY_RANGE] (lệnh đã chạy là
     * `wm density reset`) ⇒ trên đĩa là `"700"` trong khi màn đang ở mặc định. Chuẩn hoá thành `"reset"` là lưu
     * **đúng thứ đã áp**.
     */
    fun sanitizeForSave(config: DisplayConfig): DisplayConfig? {
        val wm = wmSize(config.wmSize) ?: return null
        return DisplayConfig(
            wmSize = wm,
            overscan = overscan(config.overscan) ?: OVERSCAN_NONE,
            density = density(config.density) ?: DENSITY_RESET,
            bounds = config.bounds?.takeIf { boundsOk(it) },
        )
    }

    /**
     * Kích màn từ đầu ra `wm size -d <vd>` — gom HAI bản chép cũ (`AppMover.queryDisplaySize` ưu tiên *Override*,
     * `CastGeometryController.queryDisplayPhysicalSize` chỉ đọc *Physical*) về một chỗ, giữ đúng hành vi của mỗi bên
     * qua [preferOverride]. Cạnh ≤ 0 hoặc > [DIMENSION_MAX] ⇒ `null` (đầu ra lạ không được thành biên kẹp).
     */
    fun parseDisplaySize(stdout: String, preferOverride: Boolean): Pair<Int, Int>? {
        val matches = Regex("(Override|Physical) size:\\s*([0-9]{1,5})x([0-9]{1,5})").findAll(stdout).toList()
        val match = (if (preferOverride) matches.firstOrNull { it.groupValues[1] == "Override" } else null)
            ?: matches.firstOrNull { it.groupValues[1] == "Physical" }
            ?: (if (preferOverride) matches.firstOrNull() else null)
            ?: return null
        val w = match.groupValues[2].toInt()
        val h = match.groupValues[3].toInt()
        return if (w in 1..DIMENSION_MAX && h in 1..DIMENSION_MAX) w to h else null
    }

    /**
     * Kẹp [bounds] vào dải `[bandMin, bandMax]` × `[0, frameHeight]`, mỗi cạnh ít nhất [MIN_SPAN] — dời NGUYÊN THÂN
     * từ `ClusterNavBridgeGeometry.clampBounds` (`:app`) để tầng UI và lượt áp dùng CHUNG một hàm (spec §11.4.4).
     *
     * ## Vì sao [MIN_SPAN] phải tự co lại, không được dùng thẳng
     * `coerceIn(min, max)` **NÉM** khi `min > max`. Dải hẹp hơn 80 px là thật: tỉ lệ chia thấp nhất 10 %
     * ([CastProfile.SPLIT_PERCENTS]) trên cụm < 800 px, hoặc một `wmSize` lạ đưa chiều cao về 0. Dải hẹp thì ô nhỏ
     * đi, chứ màn không được chết.
     */
    fun clampBounds(bounds: CastBounds, bandMin: Int, bandMax: Int, frameHeight: Int): CastBounds {
        val hi = bandMax.coerceAtLeast(bandMin)
        val height = frameHeight.coerceAtLeast(0)
        val spanX = MIN_SPAN.coerceAtMost(hi - bandMin)
        val spanY = MIN_SPAN.coerceAtMost(height)
        val left = bounds.left.coerceIn(bandMin, hi - spanX)
        val right = bounds.right.coerceIn(left + spanX, hi)
        val top = bounds.top.coerceIn(0, height - spanY)
        val bottom = bounds.bottom.coerceIn(top + spanY, height)
        return CastBounds(left, top, right, bottom)
    }

    /** Cạnh nhỏ nhất còn nhìn thấy được — dưới mức này thì app coi như biến mất khỏi cụm. */
    const val MIN_SPAN = 80

    /** Chuỗi hỏng đưa vào log: cắt ngắn + thoát ký tự điều khiển, để một tệp độc không phun rác vào logcat. */
    private fun shown(raw: String): String =
        raw.take(40).map { c -> if (c.code in 0x20..0x7e) c.toString() else "\\u%04x".format(c.code) }.joinToString("")
}
