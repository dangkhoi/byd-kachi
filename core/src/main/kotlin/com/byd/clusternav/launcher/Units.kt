package com.byd.clusternav.launcher

import java.util.Locale

/**
 * ĐƠN VỊ DO NGƯỜI DÙNG CHỌN — thuần Kotlin (`:core`, cấm `android.*`) ⇒ test off-car.
 *
 * Owner chốt 2026-09-10: *"mấy cái mà có đơn vị, thì cho user họ chọn đơn vị luôn, đừng ép chi tội họ"*.
 * Spec: `docs/specs/kachi-unified-capability-tile.html` §4.6 (R11–R13).
 *
 * ## Ba quyết định thiết kế (ghi để đời sau khỏi đoán)
 *
 * 1. **KHÔNG sửa 61 dòng [TelemetryRegistry]**. Loại đại lượng được **suy ra từ chính chuỗi đơn vị đã khai**
 *    ([quantityOf]). Thêm datum mới vào registry ⇒ tự có lựa chọn đơn vị, không phải khai thêm gì.
 *
 * 2. **Chọn theo LOẠI đại lượng, không theo từng mục** (R11): 7 lựa chọn thay vì 61. Đổi "áp suất → psi" thì cả 4
 *    mục lốp đổi cùng lúc.
 *
 * 3. **Đường ĐỌC ([TelemetryReadout.of]) giữ nguyên đơn vị GỐC của registry** — đổi đơn vị là **bước định dạng
 *    RIÊNG** ([UnitFormat.apply]). Lý do: `TelemetryReadoutTest` đang khoá `tyre_p_fl` = "241" (kPa làm tròn); nếu
 *    nhét chuyển đổi vào đường đọc thì đổi hành vi của tầng dữ liệu chỉ vì lý do trình bày. Tách ra thì tầng dữ liệu
 *    = số thật của xe, tầng trình bày = cái người dùng muốn thấy.
 *
 * ⚠ **[ĐO] tự mâu thuẫn ĐANG CÓ mà lớp này dọn**: registry khai `tyre_p_*` đơn vị `kPa`, nhưng widget lốp ở `:app`
 * tự chia 100 để hiện `bar`. Vì vậy [UnitPrefs.DEFAULT] chọn **bar** cho áp suất — để bề mặt người dùng THẬT SỰ
 * thấy hôm nay không đổi một ký tự (R12), thay vì giữ hai bề mặt nói hai đơn vị khác nhau.
 */

/** LOẠI đại lượng có thể đổi đơn vị. Chỉ gồm loại **có lựa chọn thay thế hợp lý** (R13). */
enum class Quantity(val label: String) {
    PRESSURE("Áp suất"),
    TEMPERATURE("Nhiệt độ"),
    DISTANCE("Khoảng cách"),
    SPEED("Tốc độ"),
    CONSUMPTION("Tiêu thụ điện"),
    TORQUE("Mô-men xoắn"),
    LENGTH("Chiều dài"),
}

/**
 * Một đơn vị chọn được.
 * @property code chuỗi hiển thị kèm số (vd `"bar"`), cũng là khoá LƯU.
 * @property decimals số chữ số thập phân khi hiện — mỗi đơn vị một mức, **không bịa độ chính xác** (R13).
 */
data class UnitOption(val code: String, val decimals: Int)

/** Bảng tra đơn vị: loại ↔ đơn vị gốc xe trả về ↔ các lựa chọn ↔ hệ số quy đổi. */
object Units {

    /**
     * Đơn vị GỐC (đúng như [TelemetryRegistry] khai) của từng loại. Mọi quy đổi đi qua đây làm trung gian.
     */
    val BASE: Map<Quantity, String> = mapOf(
        Quantity.PRESSURE to "kPa",
        Quantity.TEMPERATURE to "°C",
        Quantity.DISTANCE to "km",
        Quantity.SPEED to "km/h",
        Quantity.CONSUMPTION to "kWh/100km",
        Quantity.TORQUE to "Nm",
        Quantity.LENGTH to "m",
    )

    /** Các lựa chọn cho từng loại; phần tử ĐẦU = mặc định của loại đó (xem [UnitPrefs.DEFAULT]). */
    private val OPTIONS: Map<Quantity, List<UnitOption>> = mapOf(
        // bar ĐẦU TIÊN vì đó là thứ widget lốp đang hiện — giữ R12 (không đổi bề mặt người dùng thấy)
        Quantity.PRESSURE to listOf(UnitOption("bar", 1), UnitOption("kPa", 0), UnitOption("psi", 1)),
        Quantity.TEMPERATURE to listOf(UnitOption("°C", 0), UnitOption("°F", 0)),
        Quantity.DISTANCE to listOf(UnitOption("km", 0), UnitOption("mile", 0)),
        Quantity.SPEED to listOf(UnitOption("km/h", 0), UnitOption("mph", 0)),
        Quantity.CONSUMPTION to listOf(UnitOption("kWh/100km", 1), UnitOption("km/kWh", 1)),
        Quantity.TORQUE to listOf(UnitOption("Nm", 0), UnitOption("lb·ft", 0)),
        Quantity.LENGTH to listOf(UnitOption("m", 0), UnitOption("ft", 0)),
    )

    /** Tra ngược đơn vị gốc → loại. Đơn vị KHÔNG có lựa chọn (`%`, `°`, `V`, `rpm`, `kWh`, `kW`, `h`, `min`,
     *  `µg/m³`…) ⇒ `null` ⇒ [UnitFormat] để nguyên, và UI KHÔNG bày lựa chọn giả (R13). */
    fun quantityOf(unit: String): Quantity? = BASE.entries.firstOrNull { it.value == unit }?.key

    /** Danh sách lựa chọn của một loại (rỗng nếu loại lạ). */
    fun options(q: Quantity): List<UnitOption> = OPTIONS[q] ?: emptyList()

    /** Lựa chọn theo mã, hoặc null nếu mã không thuộc loại đó. */
    fun option(q: Quantity, code: String): UnitOption? = options(q).firstOrNull { it.code == code }

    /** Đơn vị mặc định của một loại = phần tử đầu danh sách. */
    fun defaultUnit(q: Quantity): String = options(q).firstOrNull()?.code ?: (BASE[q] ?: "")

    /**
     * Quy đổi [v] từ đơn vị GỐC của [q] sang [to]. `null` nếu [to] không thuộc [q], hoặc quy đổi không xác định
     * (vd tiêu thụ 0 ⇒ chia cho 0).
     */
    fun fromBase(q: Quantity, v: Double, to: String): Double? {
        if (option(q, to) == null) return null
        val base = BASE[q] ?: return null
        // Vào KHÔNG hữu hạn ⇒ ra "không xác định". `String.toDoubleOrNull()` CHẤP NHẬN "NaN"/"Infinity" (đúng đặc tả
        // `Double.valueOf`), nên nếu không chặn ở đây thì một giá trị rác từ HAL sẽ được [format] in ra thành chuỗi
        // "NaN"/"Infinity" — tức bịa ra một con số không có (R13). Thà trả null để chỗ gọi giữ nguyên chuỗi cũ.
        if (!v.isFinite()) return null
        if (to == base) return v
        val out = when (q) {
            Quantity.PRESSURE -> when (to) {
                "bar" -> v / 100.0
                "psi" -> v * 0.1450377377
                else -> null
            }
            Quantity.TEMPERATURE -> if (to == "°F") v * 9.0 / 5.0 + 32.0 else null
            Quantity.DISTANCE -> if (to == "mile") v * 0.6213711922 else null
            Quantity.SPEED -> if (to == "mph") v * 0.6213711922 else null
            // kWh/100km ↔ km/kWh là quan hệ NGHỊCH ĐẢO, không phải hệ số ⇒ 0 là không xác định (không bịa ∞)
            Quantity.CONSUMPTION -> if (to == "km/kWh") (if (v == 0.0) null else 100.0 / v) else null
            Quantity.TORQUE -> if (to == "lb·ft") v * 0.7375621493 else null
            Quantity.LENGTH -> if (to == "ft") v * 3.280839895 else null
        }
        // Nhân/chia có thể tràn (vd 1e308 × 3.28) ⇒ kiểm LẠI ở đầu ra, không tin đầu vào hữu hạn là đủ.
        return if (out == null || !out.isFinite()) null else out
    }

    /** Định dạng [v] theo số chữ số thập phân của đơn vị [code] thuộc loại [q]. Locale.ROOT ⇒ luôn dấu chấm. */
    fun format(q: Quantity, v: Double, code: String): String {
        val dec = option(q, code)?.decimals ?: 0
        return String.format(Locale.ROOT, "%.${dec}f", v)
    }
}

/**
 * Lựa chọn đơn vị của người dùng. Bất biến; lưu CHUNG mọi hồ sơ tài xế (đơn vị là thói quen của người ĐỌC, không
 * phải của hồ sơ — cùng lối với giao diện sáng/tối).
 *
 * Chỉ giữ những loại người dùng đã ĐỔI ⇒ [DEFAULT] là map rỗng, và mặc định luôn lấy từ [Units.defaultUnit] nên
 * thêm loại mới về sau không phải chuyển đổi dữ liệu đã lưu (R4/R12).
 */
data class UnitPrefs(val chosen: Map<Quantity, String> = emptyMap()) {

    /** Đơn vị đang dùng cho [q]: người dùng chọn (nếu hợp lệ), không thì mặc định của loại. */
    fun unitFor(q: Quantity): String {
        val c = chosen[q]
        return if (c != null && Units.option(q, c) != null) c else Units.defaultUnit(q)
    }

    /** Bản mới với [q] đặt sang [code]; mã lạ ⇒ trả về CHÍNH nó (bỏ qua im lặng, không sập). */
    fun with(q: Quantity, code: String): UnitPrefs =
        if (Units.option(q, code) == null) this else copy(chosen = chosen + (q to code))

    /** Chuỗi lưu bền: `LOAI=ma` cách nhau bằng `;`. Bỏ qua loại không đổi để chuỗi ngắn + tương thích tiến. */
    fun encode(): String = Quantity.values()
        .mapNotNull { q -> chosen[q]?.let { "${q.name}=$it" } }
        .joinToString(";")

    companion object {
        /** Mặc định = y như bề mặt người dùng thấy hôm nay (áp suất bar, còn lại đơn vị gốc) — R12. */
        val DEFAULT = UnitPrefs()

        /** Giải mã chuỗi của [encode]; null/rỗng/rác ⇒ [DEFAULT]. Mục lạ hoặc mã sai bị BỎ, không làm sập. */
        fun decode(s: String?): UnitPrefs {
            if (s.isNullOrBlank()) return DEFAULT
            val map = LinkedHashMap<Quantity, String>()
            s.split(";").forEach { part ->
                val i = part.indexOf('=')
                if (i <= 0) return@forEach
                val q = runCatching { Quantity.valueOf(part.substring(0, i).trim()) }.getOrNull() ?: return@forEach
                val code = part.substring(i + 1).trim()
                if (Units.option(q, code) != null) map[q] = code
            }
            return UnitPrefs(map)
        }
    }
}

/**
 * ÁP lựa chọn đơn vị lên một [TelemetryView] — **bước trình bày**, tách khỏi đường đọc dữ liệu.
 *
 * Để NGUYÊN (không đụng) khi: đơn vị rỗng · đơn vị không có loại (⇒ không có lựa chọn) · chưa đọc được giá trị
 * (`valueText == null` ⇒ vẫn "—") · giá trị không phải số (vd "Có"/"Không"/tên chế độ) · quy đổi không xác định.
 * Nhờ vậy mặc định KHÔNG đổi gì ngoài đúng những chỗ có lựa chọn thật (R12/R13).
 */
object UnitFormat {

    /** [v] sau khi đổi sang đơn vị người dùng chọn; nếu không áp được thì trả về CHÍNH [v]. */
    fun apply(v: TelemetryView, prefs: UnitPrefs = UnitPrefs.DEFAULT): TelemetryView {
        if (v.unit.isEmpty()) return v
        val q = Units.quantityOf(v.unit) ?: return v
        val target = prefs.unitFor(q)
        // Đích TRÙNG đơn vị gốc ⇒ trả về NGUYÊN VẸN, KHÔNG định dạng lại. Đây là điều kiện đủ cho R12: định dạng lại
        // sẽ áp số-chữ-số-thập-phân của bảng lên chuỗi mà tầng đọc đã format (vd "28.5" °C → "29"), tức là ÂM THẦM
        // đổi thứ người dùng đang thấy dù họ không chọn gì.
        if (target == v.unit) return v
        val raw = v.valueText ?: return v.copy(unit = target)
        val num = raw.toDoubleOrNull() ?: return v
        // ⚠ `toDoubleOrNull()` nhận cả "NaN" và "Infinity" (đặc tả `Double.valueOf`). Đổi đơn vị một giá trị như thế
        // rồi in ra là bịa số ⇒ để NGUYÊN chuỗi tầng đọc đã đưa, không nhận là đã quy đổi.
        if (!num.isFinite()) return v
        val conv = Units.fromBase(q, num, target) ?: return v
        return v.copy(unit = target, valueText = Units.format(q, conv, target))
    }

    /**
     * Các loại đại lượng THẬT SỰ có mặt trong [TelemetryRegistry] (để màn cài đặt chỉ bày lựa chọn dùng được).
     * Thứ tự = thứ tự khai [Quantity].
     */
    fun quantitiesInUse(): List<Quantity> {
        val used = TelemetryRegistry.ALL.mapNotNull { Units.quantityOf(it.unit) }.toSet()
        return Quantity.values().filter { it in used }
    }
}
