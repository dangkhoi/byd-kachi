package com.byd.clusternav.launcher

/**
 * MỘT mục trong **sổ địa chỉ** của một hồ sơ tài xế.
 *
 * Spec `docs/specs/kachi-voice-addresses.html` R1. Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car.
 *
 * ## Vì sao có CẢ [query] lẫn [lat]/[lng], và vì sao toạ độ là TUỲ CHỌN
 * [ĐO] `VoiceAppTargets` (máy ảo 2026-09-14): Google Maps nhận **chữ** (`geo:0,0?q=…`) còn VietMap Live 3.4.0
 * **không có cửa nào** nhận chữ — nó chỉ nhận `vietmaplive://…?lat=…&lng=…`. Hai app dẫn đường trên cùng một
 * chiếc xe cần **hai loại dữ liệu khác nhau** cho cùng một điểm đến. Bắt buộc phải có toạ độ thì người dùng
 * không lưu nổi một địa chỉ (họ không biết toạ độ nhà mình); bỏ hẳn toạ độ thì VietMap vĩnh viễn không dùng được.
 * ⇒ chữ là bắt buộc, toạ độ là **thêm vào nếu có** — và chỗ chọn app đọc đúng điều đó ([SavedPlace.hasCoords] →
 * `VoiceAppTargets.navFor`).
 *
 * ## ⚠ Toạ độ KHÔNG bao giờ đến từ máy
 * `DeadReckonRetirementTest` cấm mọi quyền vị trí trong manifest (quyết định an toàn đã chốt). Nên ở đây không
 * có, và không được có, đường *"lưu vị trí hiện tại"*: [lat]/[lng] chỉ đến từ chuỗi người dùng **dán vào**
 * (xem [SavedPlaces.parseCoords]).
 *
 * ## ⚠ Trường tên là [name], KHÔNG phải `label` — cùng lý do `VoiceAppIntents.Coords.place`
 * `LauncherI18nContractTest.tang ve khong doc nhan GOC cua core` quét **mọi** lần đọc `.label` ở `:app` để bắt chỗ
 * dùng nhãn GỐC (luôn tiếng Việt) của `:core` thay cho `displayLabel`. Tên nơi này do **người dùng gõ**, không
 * phải nhãn đã dịch của một bộ đăng ký, nên nó không thuộc diện đó — đặt tên khác để bài canh kia khỏi phải mang
 * thêm một mục loại trừ, tức khỏi phải mở thêm một lỗ (cùng lối `VoiceIntent.OpenApp.appName`).
 *
 * @property name tên người dùng gọi nơi này (*"Nhà"*, *"Công ty"*, *"Nhà ngoại"*) — cũng là thứ họ NÓI.
 * @property query văn bản địa chỉ giao nguyên văn cho app dẫn đường.
 */
data class SavedPlace(
    val name: String,
    val query: String,
    val lat: Double? = null,
    val lng: Double? = null,
) {
    /** Có đủ cặp toạ độ để đi đường `{lat}`/`{lng}` không (thiếu một nửa = không có — xem [SavedPlaces.decode]). */
    val hasCoords: Boolean get() = lat != null && lng != null
}

/**
 * ═══ SỔ ĐỊA CHỈ — MÔ HÌNH + MÃ HOÁ, **NGUỒN DUY NHẤT** ════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-addresses.html` §4.3. Cùng vai [SlotCodec]/[TopStripConfig]: dạng chuỗi của một
 * thứ lưu bền được khai **đúng một chỗ**, ở `:core`, nơi bài kiểm chạm tới được. `WorkspacePrefs` (`:app`) chỉ
 * gọi [encode]/[decode] — nó không biết một dấu phân cách nào.
 *
 * ## Dạng lưu: một mục MỘT DÒNG, bốn trường ngăn bằng `|`
 * ```
 * Nhà|123 Nguyễn Trãi, Hà Nội|21.0045|105.8412
 * Công ty|Keangnam Landmark 72||
 * ```
 * Đọc được bằng mắt (cứu tay được qua `adb shell run-as … cat`), cùng lệ `grid_layout`/`top_strip`.
 *
 * ## ⚠ Ký tự ngăn phải bị KHỬ ở cửa VÀO, không phải thoát ở cửa RA
 * [SavedPlace.name]/[SavedPlace.query] là **chữ người dùng gõ**, và một địa chỉ có thể chứa bất cứ thứ gì. Bài
 * học `SlotCodec.SEP`
 * ([ĐO] 2026-09-12: một `|` lọt vào làm **mất cả một bản ghi** trong im lặng) nói rằng chỗ nguy hiểm không phải
 * lúc ghi mà lúc đọc: một dòng 5 trường thì bộ giải mã hoặc bỏ cả dòng, hoặc ghép nhầm trường. Khử ngay lúc
 * dựng ([sanitize]) thì dữ liệu trên đĩa **không thể** có dòng hỏng do người dùng gõ — cùng cách
 * `WorkspacePrefs.addProfile` khử `\r\n` khỏi tên hồ sơ.
 */
object SavedPlaces {

    /**
     * Trần số mục (spec OQ3).
     *
     * 20 đủ cho *"nhà · công ty · trường con · nhà ngoại · bãi đỗ"* và chặn một chuỗi prefs phình vô hạn. Vượt
     * trần thì [upsert] **từ chối thêm mới** (giữ nguyên danh sách) chứ không cắt bớt: cắt một mục cũ để nhét
     * mục mới là mất dữ liệu người dùng không hề yêu cầu — chỗ gọi so độ dài trước/sau để nói ra.
     */
    const val MAX = 20

    /** Ngăn TRƯỜNG. Bị khử khỏi mọi chữ người dùng gõ ([sanitize]) nên nó không thể xuất hiện trong dữ liệu. */
    private const val FIELD = '|'

    /** Số trường của một dòng hợp lệ: nhãn · địa chỉ · lat · lng. */
    private const val FIELDS = 4

    /** Dải hợp lệ của vĩ độ / kinh độ — ngoài dải thì đó không phải toạ độ, dù nó là một con số. */
    private const val LAT_ABS = 90.0
    private const val LNG_ABS = 180.0

    /**
     * Bỏ ký tự có thể **làm hỏng cấu trúc** khỏi một chuỗi người dùng gõ, rồi gộp khoảng trắng.
     *
     * Thay bằng khoảng trắng (không xoá hẳn): một địa chỉ dạng *"số 5|ngõ 2"* mà xoá hẳn thành *"số 5ngõ 2"* là
     * đổi nghĩa; thay bằng dấu cách thì nó vẫn đọc ra đúng chỗ ấy.
     */
    fun sanitize(raw: String): String =
        raw.map { if (it == FIELD || it == '\n' || it == '\r') ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Khoá NHẬN DẠNG của một nhãn — *"cùng một nơi"* nghĩa là gì.
     *
     * Chỉ `trim` + `lowercase`: hai mục *"Nhà"* và *"nhà"* là một (người dùng gõ hoa/thường tuỳ lúc). **Không**
     * bỏ dấu ở đây, có chủ ý: *"Bà Nội"* và *"Ba Nội"* là hai nơi khác nhau với người Việt, và sổ địa chỉ là nơi
     * người ta **gõ**, không phải nơi người ta nói. Phần bỏ dấu là việc của tầng NÓI
     * (`com.byd.clusternav.launcher.voice.VoicePlaces`), nơi nó đúng.
     */
    fun keyOf(name: String): String = name.trim().lowercase()

    /**
     * Dựng một mục từ ba ô nhập, hoặc `null` khi thiếu thứ bắt buộc.
     *
     * `null` chứ không phải một mục rỗng: một dòng không nhãn (hoặc không địa chỉ) thì vừa không gọi được bằng
     * giọng, vừa không dẫn đi đâu được — lưu nó xuống chỉ để nó nằm đó làm người dùng tưởng mình đã lưu xong.
     *
     * @param coords chuỗi *"lat, lng"* dán từ app bản đồ; rỗng/hỏng ⇒ mục **vẫn hợp lệ**, chỉ là không có toạ độ
     *   (xem KDoc [SavedPlace]).
     */
    fun of(name: String, query: String, coords: String? = null): SavedPlace? {
        val l = sanitize(name)
        val q = sanitize(query)
        if (l.isEmpty() || q.isEmpty()) return null
        val c = parseCoords(coords)
        return SavedPlace(l, q, c?.first, c?.second)
    }

    /**
     * *"10.7769, 106.7009"* → cặp số; `null` khi không đọc được.
     *
     * ## Vì sao MỘT ô chứ không hai ô lat/lng riêng
     * Đó đúng dạng người ta **dán**: mở Google Maps → giữ vào một điểm → chép toạ độ. Bắt tách tay ra hai ô là
     * bắt người dùng làm một việc máy làm được, ngay trên xe, bằng bàn phím ảo.
     *
     * Nhận cả dấu phẩy lẫn khoảng trắng làm dấu ngăn, và **từ chối** số ngoài dải: `200, 300` là một cặp số hợp
     * lệ về cú pháp nhưng không phải một chỗ trên Trái Đất — nhận nó vào là để app dẫn đường nhận một điểm đến
     * vô nghĩa rồi im lặng không dẫn.
     */
    fun parseCoords(raw: String?): Pair<Double, Double>? {
        val parts = raw?.trim()?.split(',', ' ')?.filter { it.isNotBlank() } ?: return null
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        if (lat.isNaN() || lng.isNaN()) return null
        if (kotlin.math.abs(lat) > LAT_ABS || kotlin.math.abs(lng) > LNG_ABS) return null
        return lat to lng
    }

    /** Chuỗi toạ độ để **điền sẵn** vào ô sửa; rỗng khi mục chưa có toạ độ. Ngược với [parseCoords]. */
    fun formatCoords(place: SavedPlace): String =
        if (place.hasCoords) "${place.lat}, ${place.lng}" else ""

    fun encode(places: List<SavedPlace>): String = places.joinToString("\n") { p ->
        listOf(p.name, p.query, p.lat?.toString().orEmpty(), p.lng?.toString().orEmpty()).joinToString(FIELD.toString())
    }

    /**
     * Đọc sổ từ chuỗi đã lưu. Dòng hỏng ⇒ **bỏ dòng đó**, không ném và không bỏ cả sổ.
     *
     * Ba ca hỏng đều có thật: người dùng sửa tay tệp prefs, một bản cũ/mới ghi khác số trường, và một nửa cặp
     * toạ độ (chỉ có lat) — ca cuối trả về mục **không toạ độ** thay vì mục nửa vời, vì `{lat}`/`{lng}` chỉ có
     * nghĩa khi có đủ đôi.
     */
    fun decode(raw: String?): List<SavedPlace> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val f = line.split(FIELD)
            if (f.size != FIELDS) return@mapNotNull null
            val name = f[0].trim()
            val query = f[1].trim()
            if (name.isEmpty() || query.isEmpty()) return@mapNotNull null
            val lat = f[2].trim().toDoubleOrNull()
            val lng = f[3].trim().toDoubleOrNull()
            val ok = lat != null && lng != null &&
                kotlin.math.abs(lat) <= LAT_ABS && kotlin.math.abs(lng) <= LNG_ABS
            SavedPlace(name, query, if (ok) lat else null, if (ok) lng else null)
        }.take(MAX)
    }

    /** Mục mang tên [name] (không phân biệt hoa/thường), hoặc `null`. */
    fun find(places: List<SavedPlace>, name: String): SavedPlace? {
        val k = keyOf(name)
        return places.firstOrNull { keyOf(it.name) == k }
    }

    /**
     * Thêm mới, hoặc **ghi đè** mục cùng nhãn (đó là đường SỬA — xem KDoc dưới).
     *
     * ## Vì sao sửa = thêm-đè, không phải một hàm `update` riêng
     * Hai đường ghi cho một bảng là hai chỗ để lệch nhau (bài học `unitPrefs` ×4 bản). Ở đây *"sửa"* theo nghĩa
     * người dùng chỉ là *"lưu lại mục tên này"*, và giữ **nguyên vị trí cũ** trong danh sách là điều họ chờ đợi:
     * sửa địa chỉ nhà không làm nó nhảy xuống cuối sổ.
     *
     * Vượt [MAX] mà là mục MỚI ⇒ trả về danh sách **không đổi**; chỗ gọi so độ dài để nói ra (xem KDoc [MAX]).
     */
    fun upsert(places: List<SavedPlace>, place: SavedPlace): List<SavedPlace> {
        val k = keyOf(place.name)
        val at = places.indexOfFirst { keyOf(it.name) == k }
        if (at >= 0) return places.toMutableList().also { it[at] = place }
        if (places.size >= MAX) return places
        return places + place
    }

    fun remove(places: List<SavedPlace>, name: String): List<SavedPlace> {
        val k = keyOf(name)
        return places.filterNot { keyOf(it.name) == k }
    }
}
