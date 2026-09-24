package com.byd.clusternav.launcher.automation

import com.byd.clusternav.launcher.SavedPlace
import com.byd.clusternav.launcher.SavedPlaces

/**
 * MỘT luật **tự động dẫn đường theo lịch** — spec `docs/specs/kachi-automation.html` R2.2.
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. Đọc GPS, tra sổ địa chỉ và bắn ý-định dẫn đường là việc
 * của `ScheduledNavApplier` (`:app`).
 *
 * @property id khoá NHẬN DẠNG bền của luật. **Cũng là khoá của sổ đã-dẫn** (`lastFiredDay[id]`) ⇒ hai luật trùng
 *   `id` thì một cái sẽ chặn cái kia im lặng; xem [NavAutomationBook.decode] về chỗ chặn ca đó.
 * @property startMin phút-trong-ngày bắt đầu khung giờ (0 = 00:00 · 450 = 07:30).
 * @property endMin phút-trong-ngày kết thúc, **bao gồm** cả phút này (xem [ScheduledNavRules.inWindow]).
 * @property days thứ trong tuần theo ISO-8601: 1 = Thứ Hai … 7 = Chủ Nhật ([ScheduledNavRules.MON]…[ScheduledNavRules.SUN]).
 * @property requireGps spec R2.2 — *"chỉ khi có GPS"*. `false` ⇒ dẫn đúng giờ kể cả khi chưa có định vị.
 * @property placeId mục trong sổ địa chỉ, dạng khoá của [SavedPlaces.keyOf] (xem [ScheduledNavRules.placeIdOf]).
 * @property navApp mã app dẫn đường — khoá của `VoiceAppTargets` (`gmaps` · `vietmap` · `waze`).
 */
data class ScheduledNavRule(
    val id: String,
    val enabled: Boolean,
    val startMin: Int,
    val endMin: Int,
    val days: Set<Int>,
    val requireGps: Boolean,
    val placeId: String,
    /** Storage: một hoặc NHIỀU mã app nối bằng `+` ("gmaps+vietmap") — owner 2026-09-24 multi-choice. Đọc qua [navApps]. */
    val navApp: String,
) {
    /** Danh sách mã app dẫn đường (tách `+`, bỏ rỗng). Luật cũ 1-app ("gmaps") → `["gmaps"]` (backward-compat). */
    val navApps: List<String> get() = navApp.split('+').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * ═══ LUẬT DẪN ĐƯỜNG THEO LỊCH · MÔ HÌNH + MÃ HOÁ MỘT DÒNG, **NGUỒN DUY NHẤT** ═════════════════════════════════
 *
 * Cùng vai `SlotCodec` / `SavedPlaces` / `TopStripConfig`: dạng chuỗi của một thứ lưu bền khai **đúng một chỗ**, ở
 * `:core`, nơi bài kiểm chạm tới được. `WorkspacePrefs` (`:app`) chỉ gọi [encode]/[decode] — nó không được biết
 * một dấu phân cách nào.
 *
 * ## Dạng lưu: một luật MỘT DÒNG, tám trường ngăn bằng `|`
 * ```
 * r1|1|450|540|1,2,3,4,5|1|công ty|gmaps
 * r2|1|1020|1140|1,2,3,4,5|0|nhà|vietmap
 * ```
 * Đọc được bằng mắt (cứu tay được qua `adb shell run-as … cat`), cùng lệ `grid_layout` / `top_strip` /
 * `saved_places`.
 *
 * ## ⚠ Ký tự ngăn bị KHỬ ở cửa VÀO, không thoát ở cửa RA
 * Bài học [ĐO] 2026-09-12 `SlotCodec.SEP`: một `|` lọt vào chuỗi lưu làm **mất nguyên một bản ghi** trong im
 * lặng. Ở đây [ScheduledNavRule.placeId] đến từ **tên người dùng gõ** trong sổ địa chỉ, nên nó là đúng chỗ nguy
 * hiểm ấy. [SavedPlaces.sanitize] đã khử `|` khi lưu tên nơi, nhưng phép khử **phải lặp lại ở cửa này**: chuỗi
 * luật cũng đọc từ đĩa và sửa tay được, và một `|` thêm vào biến dòng 8 trường thành 9 ⇒ [decode] bỏ cả luật.
 * Hai cửa cùng khử thì dữ liệu trên đĩa **không thể** có dòng hỏng do chữ người dùng.
 */
object ScheduledNavRules {

    /** Thứ Hai — ISO-8601, khớp `java.time.DayOfWeek.value` để `:app` không phải dựng bảng đổi thứ hai. */
    const val MON = 1
    const val TUE = 2
    const val WED = 3
    const val THU = 4
    const val FRI = 5
    const val SAT = 6
    const val SUN = 7

    /** Cả tuần — dùng làm mặc định khi thêm luật mới (người dùng bỏ tick bớt thì dễ hơn tick thêm từ trống). */
    val ALL_DAYS: Set<Int> = (MON..SUN).toSet()

    /** T2–T6, tức ca đi làm — usecase gốc của spec R2.1 (*"sáng đến cty, chiều về nhà"*). */
    val WEEKDAYS: Set<Int> = (MON..FRI).toSet()

    /** Phút-trong-ngày lớn nhất (23:59). */
    const val MAX_MIN = 24 * 60 - 1

    /** Ngăn TRƯỜNG. Bị khử khỏi mọi chữ người dùng gõ ([sanitize]) nên nó không thể xuất hiện trong dữ liệu. */
    private const val FIELD = '|'

    /** Ngăn phần tử trong tập THỨ. Cũng bị khử khỏi chữ người dùng, cùng lý do [FIELD]. */
    private const val DAY_SEP = ','

    /** Số trường của một dòng hợp lệ: id · bật · đầu · cuối · thứ · gps · nơi · app. */
    private const val FIELDS = 8

    private const val TRUE = "1"
    private const val FALSE = "0"

    /**
     * Bỏ ký tự **làm hỏng cấu trúc** khỏi một chuỗi, rồi gộp khoảng trắng. Cùng phép của [SavedPlaces.sanitize],
     * thêm [DAY_SEP] vì ở đây dấu phẩy cũng mang nghĩa.
     *
     * Thay bằng khoảng trắng (không xoá hẳn) — xoá hẳn là đổi nghĩa chuỗi, còn dấu cách thì vẫn đọc ra đúng chỗ ấy.
     */
    fun sanitize(raw: String): String =
        raw.map { if (it == FIELD || it == DAY_SEP || it == '\n' || it == '\r') ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Khoá sổ địa chỉ của một mục — **nguồn duy nhất** của phép nối luật ↔ nơi đến.
     *
     * Uỷ quyền [SavedPlaces.keyOf] chứ không chép luật *"trim + lowercase"* sang đây: sổ địa chỉ đã quyết định
     * *"cùng một nơi nghĩa là gì"* (và cố ý **không** bỏ dấu — *"Bà Nội"* ≠ *"Ba Nội"*). Chép lại là mở đường cho
     * hai định nghĩa lệch nhau, tức một luật trỏ tới một nơi mà sổ không tìm ra.
     */
    fun placeIdOf(place: SavedPlace): String = SavedPlaces.keyOf(place.name)

    /**
     * Dựng một luật, hoặc `null` khi dữ liệu không dùng được. **Đường dựng DUY NHẤT** — [decode] cũng đi qua đây.
     *
     * Bốn ca trả `null`, mỗi ca là một luật **không bao giờ chạy** nếu cho qua:
     *  - `id` / `placeId` / `navApp` rỗng sau khi khử ⇒ không tra ra nơi đến, hoặc không có sổ đã-dẫn.
     *  - [days] rỗng, hoặc có thứ ngoài 1..7 ⇒ không ngày nào khớp.
     *  - khung giờ ngoài `0..`[MAX_MIN].
     *  - `startMin > endMin` ⇒ xem KDoc [inWindow] về vì sao khung **qua đêm** bị từ chối thay vì đoán.
     *
     * Trả `null` thay vì một luật "gần đúng" để tầng UI **nói ra được** chỗ sai; một luật lưu xuống rồi nằm im là
     * đúng thứ người dùng tưởng đã đặt xong (cùng lẽ [SavedPlaces.of]).
     */
    fun of(
        id: String,
        enabled: Boolean,
        startMin: Int,
        endMin: Int,
        days: Set<Int>,
        requireGps: Boolean,
        placeId: String,
        navApp: String,
    ): ScheduledNavRule? {
        val cleanId = sanitize(id)
        val cleanPlace = SavedPlaces.keyOf(sanitize(placeId))
        val cleanApp = sanitize(navApp)
        if (cleanId.isEmpty() || cleanPlace.isEmpty() || cleanApp.isEmpty()) return null
        if (days.isEmpty() || days.any { it < MON || it > SUN }) return null
        if (startMin < 0 || endMin < 0 || startMin > MAX_MIN || endMin > MAX_MIN) return null
        if (startMin > endMin) return null
        return ScheduledNavRule(
            id = cleanId,
            enabled = enabled,
            startMin = startMin,
            endMin = endMin,
            days = days.toSortedSet(),
            requireGps = requireGps,
            placeId = cleanPlace,
            navApp = cleanApp,
        )
    }

    /**
     * Phút hiện tại có nằm trong khung giờ của luật — **hai đầu đều tính** (spec R2.3: *"trong khung giờ"*).
     *
     * ## Vì sao KHÔNG hỗ trợ khung qua đêm (22:00 → 02:00)
     * Đo được thì dễ, nhưng nó làm **luật một-lần-mỗi-ngày mất nghĩa**: sổ đã-dẫn đóng dấu theo *ngày lịch tại
     * lúc dẫn* ([ScheduledNavPolicy]), nên một khung bắc qua nửa đêm nằm trên **hai** ngày lịch ⇒ nó được phép nổ
     * hai lần cho cùng một lượt đi, và tập [ScheduledNavRule.days] cũng không còn trả lời được *"đêm Chủ Nhật
     * sang Thứ Hai là thứ mấy"*. Hai câu hỏi đó không có câu trả lời **đúng duy nhất**, nên đây là chỗ phải
     * **từ chối** ([of] trả `null`) chứ không phải chỗ đoán — usecase của spec là khung đi làm trong ngày.
     */
    fun inWindow(rule: ScheduledNavRule, nowMinOfDay: Int): Boolean =
        nowMinOfDay >= rule.startMin && nowMinOfDay <= rule.endMin

    /** Một luật ra MỘT dòng. Tập thứ luôn ghi **đã sắp xếp** ⇒ chuỗi ổn định, so sánh/diff được. */
    fun encodeLine(rule: ScheduledNavRule): String = listOf(
        rule.id,
        if (rule.enabled) TRUE else FALSE,
        rule.startMin.toString(),
        rule.endMin.toString(),
        rule.days.sorted().joinToString(DAY_SEP.toString()),
        if (rule.requireGps) TRUE else FALSE,
        rule.placeId,
        rule.navApp,
    ).joinToString(FIELD.toString())

    /**
     * Một dòng ra một luật; **hỏng ⇒ `null`**, không ném (chuỗi này đến từ đĩa và sửa tay được).
     *
     * Mọi phép kiểm đi qua [of] ⇒ không có đường thứ hai để một luật không dùng được lọt vào bộ nhớ.
     */
    fun decodeLine(line: String): ScheduledNavRule? {
        val f = line.split(FIELD)
        if (f.size != FIELDS) return null
        val start = f[2].trim().toIntOrNull() ?: return null
        val end = f[3].trim().toIntOrNull() ?: return null
        val days = f[4].split(DAY_SEP).mapNotNull { it.trim().toIntOrNull() }.toSet()
        return of(
            id = f[0],
            enabled = f[1].trim() == TRUE,
            startMin = start,
            endMin = end,
            days = days,
            requireGps = f[5].trim() == TRUE,
            placeId = f[6],
            navApp = f[7],
        )
    }
}
