package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R25 — `car.type` → đường dẫn lưới 3D trong `assets/` của app AVM BYD (`com.byd.avc`) — DỮ LIỆU ═══════════
 *
 * [ĐO — đọc mã đã dịch ngược của AutoVideo.apk lấy từ firmware, 13/10]:
 *  • thư mục nhóm = `DeviceStates.CarStatus.getCarBodyType()` (`DeviceStates.java:1956–2075`, bảng [GROUPS] dưới đây
 *    chép nguyên từ `switch(modelValue)`, chuỗi lấy từ `CarTypeList.java`);
 *  • thư mục con = `PanoResourceController.getCarParamPath()` (`PanoResourceController.java:8–106`, hàm [folder]);
 *  • đường đủ = `"resource_pi/" + nhóm + "/CarModel" + thư mục con` (`PIAPI.java:194–196`, `RESOURCE_DIR = "/CarModel"`).
 *  • 37 tệp `car.mesh` có thật trong APK (liệt kê `unzip -l` 13/10) khớp bảng này; tệp vắng ⇒ bên gọi lùi 2D.
 *
 * Khác AVM có chủ đích (fail-safe, không đoán): AVM rơi `default → "sa20"` cho mã lạ; ta trả `null` (không vẽ xe SAI đời).
 *
 * [SUY] `modelValue` (AVM đọc `CarSettings.UserTableData "model_value"`) = `persist.sys.car.type` (Kachi đọc): Seal = 138
 * ở cả hai (`CAR_EKE_TYPE = 138`; xe owner `car.type = 138` [ĐO oncar-2026-10-13/01-read.txt]).
 * [CHƯA BIẾT] nguồn "tay lái nghịch": AVM gọi `android.widget.CustomVehicleConfig.isRightDriver()` — lớp riêng của ROM BYD,
 * không có trong bản stub framework của workspace ⇒ bên gọi mặc định tay lái THUẬN ([of] `rightDriver = false`).
 */
object BydAvmModels {

    const val PACKAGE = "com.byd.avc"

    /** @property group thư mục dưới `resource_pi/` · [folder] thư mục dưới `CarModel/` (rỗng = lưới nằm thẳng trong `CarModel/`). */
    data class Entry(val carType: Int, val group: String, val folder: String) {
        private val dir: String get() = "resource_pi/$group/CarModel" + if (folder.isEmpty()) "" else "/$folder"
        val meshPath: String get() = file("car.mesh")
        /** Tệp cùng thư mục với lưới (`wheel.mesh`, ảnh vật liệu, `car_body_color.csv`). */
        fun file(name: String): String = "$dir/$name"
    }

    /** `getCarBodyType` — mã đời xe → nhóm. Chép nguyên `DeviceStates.java:1957–2073` (trừ nhánh `default`). */
    val GROUPS: Map<Int, String> = buildMap {
        fun g(name: String, vararg v: Int) = v.forEach { put(it, name) }
        g("mars", 14, 16); g("hc", 15, 92, 93, 121, 135); g("hd", 31, 98, 99); g("me", 42, 43, 44, 67)
        g("stf", 47, 52, 53, 54, 55, 59, 114, 115); g("ha", 50, 51, 66, 85); g("sa20", 68, 71, 72); g("sced", 69)
        g("sa", 73, 82, 83, 84, 88, 89, 94); g("sc3ebe", 81); g("sa2hpq", 90)
        g("el", 108); g("ha_3ui", 101, 102, 122); g("sa_3ui", 110, 111, 112); g("me_3ui", 113); g("hde", 124)
        g("em_3ui", 127, 186); g("sc2e", 128); g("ster", 137); g("eke", 138); g("ha2hj", 141); g("paha", 143)
        g("sc3e", 160); g("sa3hkl", 162); g("sa3ef", 173); g("vde", 175); g("ew", 191); g("eqeal", 215); g("ha6h", 217)
        g("meey", 220); g("eqe", 230); g("hx", 244); g("eqedu", 251); g("sthny", 353)
        // 96 / 117: AVM tách "el"/"elec" theo `car_type_el_vin_value` (cấu hình riêng ROM) — không mesh nào trong APK ⇒ bỏ.
    }

    /** `getCarParamPath` — nhóm + mã + tay lái → thư mục con (không dấu `/`); "" = lưới nằm thẳng trong `CarModel/`. */
    fun folder(group: String, value: Int, rightDriver: Boolean): String = when (group) {
        "mars" -> if (value == 14) "bev" else "phev"
        "hc" -> when (value) { 92 -> "bev"; 135 -> "hcef"; else -> "phev" }
        "stf" -> when (value) { 59, 47, 114 -> "bev"; 52, 115 -> "phev"; else -> "fpv" }
        "sa20" -> when (value) { 72 -> "bev"; 68 -> "fpv"; else -> "phev" }
        "em_3ui" -> if (value == 127) "em_3ui" else if (rightDriver) "em2eu_right" else "em2eu"
        "sc2e" -> if (value == 128) "bev" else ""
        "eke", "sa3hkl", "meey", "vde", "eqedu", "sc3ebe", "sa2hpq", "ew" -> if (rightDriver) "${group}_right" else group
        else -> ""
    }

    /** Dòng cho [carType] (chuỗi thập phân của `persist.sys.car.type`); `null` = đời xe AVM không có bảng. */
    fun of(carType: String?, rightDriver: Boolean = false): Entry? {
        val id = carType?.trim()?.toIntOrNull() ?: return null
        val group = GROUPS[id] ?: return null
        return Entry(id, group, folder(group, id, rightDriver))
    }
}
