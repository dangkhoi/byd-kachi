package com.byd.clusternav.testsupport

/**
 * ═══ LUẬT CHỮ VIẾT cho bản dịch ZH/TH/MS — dùng chung cho bảng dịch `:core` và tài nguyên `:app` (TEST-ONLY) ═══════
 *
 * Spec `docs/specs/kachi-i18n-zh-th-ms.html` §4.2 (c) · §4.3. Hai bài canh soát hai kho chữ khác nhau — bảng cặp
 * `core/src/main/resources/i18n/*.tsv` (`I18nCoverageTest` qua `I18nPairs.audit`) và `values-*/strings_kachi.xml`
 * (`LauncherI18nLocalesContractTest`) — nhưng câu hỏi là MỘT: "bản dịch này có đúng chữ viết của tiếng đích không,
 * hay chỉ là tên riêng Latin được phép đứng nguyên?". Hai bản sao của luật đó sẽ lệch nhau đúng lúc ai đó thêm một
 * tên vào một bên (CLAUDE.md §4.1 DRY) ⇒ một chỗ, ở testFixtures để cả `:core` test lẫn `:app` test cùng đọc.
 */
object I18nScripts {

    /**
     * Từ Latin được phép đứng nguyên trong bản zh/th (tên riêng, đơn vị) — mỗi từ một lý do. Từ VIẾT HOA HẾT (HUD,
     * ECO, SOH, PM) và từ CamelCase (VietMap, YouTube) tự được phép, không cần khai. Từ tiếng Anh thường (Open, Driver
     * seat) KHÔNG được phép: đó chính là ca "quên dịch" mà bài kiểm chữ viết sinh ra để bắt.
     */
    val NAME_WORDS: Map<String, String> = mapOf(
        "Kachi" to "tên sản phẩm",
        "Google" to "thương hiệu (Google Maps)",
        "Maps" to "một phần tên app Google Maps",
        "Music" to "một phần tên app YouTube Music",
        "Spotify" to "tên app",
        "Waze" to "tên app",
        "Android" to "tên hệ điều hành",
        "Bluetooth" to "tên chuẩn kết nối",
        "km" to "đơn vị (km, km/h)",
        "h" to "đơn vị giờ trong km/h",
        "Hey" to "một phần câu gọi \"Hey Kachi\" — người dùng phải NÓI đúng chữ này (từ đánh thức), dịch là hỏng",
        "dp" to "đơn vị mật độ màn Android (kachi_dp_value)",
        "px" to "đơn vị điểm ảnh (kachi_px_value)",
    )

    /**
     * Bản dịch chỉ gồm TÊN Latin (thương hiệu, viết tắt, đơn vị) — được miễn kiểm chữ Hán/Thái. Điều kiện: mọi chữ
     * cái là ASCII, và mọi từ (dãy chữ cái liền) là viết hoa hết, CamelCase, hoặc nằm trong [NAME_WORDS]. Chuỗi không
     * có chữ cái nào (chỉ số/ký hiệu) cũng thoả.
     */
    fun nameOnly(value: String): Boolean {
        if (value.any { it.isLetter() && it.code >= 128 }) return false
        return Regex("[A-Za-z]+").findAll(value).map { it.value }.all { w ->
            w.all(Char::isUpperCase) || w.drop(1).any(Char::isUpperCase) || w in NAME_WORDS
        }
    }

    /** [s] có ít nhất một ký tự thuộc [script] (vd `HAN`, `THAI`). */
    fun hasScript(s: String, script: Character.UnicodeScript): Boolean =
        s.codePoints().anyMatch { Character.UnicodeScript.of(it) == script }

    /**
     * Dải dấu tiếng Việt (chuyển nguyên từ `LauncherI18nContractTest.VN` để bài canh tài nguyên 5 thư mục dùng chung).
     * ⚠ KHÔNG dùng để ĐẾM (chữ không dấu như "khung" lọt) — chỉ để CHẶN.
     */
    val VIETNAMESE_MARK = Regex("[ăâđêôơưàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ]", RegexOption.IGNORE_CASE)
}
