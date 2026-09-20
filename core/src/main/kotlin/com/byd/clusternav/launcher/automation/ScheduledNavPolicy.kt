package com.byd.clusternav.launcher.automation

/**
 * Vì sao một luật dẫn đường **không** nổ ở nhịp này. Là `enum` (không phải chuỗi) để nhật ký chẩn đoán trên xe
 * nói được đúng một trong các lý do đã lường, và để bài canh so được bằng giá trị.
 */
enum class NavSkipReason {
    /** Luật đang tắt. */
    DISABLED,

    /** Hôm nay không nằm trong [ScheduledNavRule.days]. */
    WRONG_DAY,

    /** Đúng ngày nhưng chưa tới / đã qua khung giờ. */
    OUTSIDE_WINDOW,

    /** Đã dẫn trong khung này hôm nay rồi (spec R2.4). */
    ALREADY_FIRED,

    /**
     * Luật đòi GPS mà chưa có định vị — spec R2.4, usecase **hầm**: vẫn trong khung 7–9h, chờ ra khỏi hầm có GPS
     * mới dẫn. Đây là lý do **chờ**, không phải lý do bỏ: nhịp sau vẫn hỏi lại.
     */
    NO_GPS,

    /**
     * Không dựng được khoá ngày (`today` rỗng) ⇒ **không thể** biết hôm nay đã dẫn chưa.
     *
     * Không phải ca lý thuyết vô hại: nếu cho qua, phép so *"đã dẫn hôm nay"* mất nghĩa và luật nổ lại **mỗi
     * nhịp** — tức app dẫn đường bị mở lại mỗi phút suốt cả khung giờ, ngay trước mặt người đang lái. Thà không
     * dẫn.
     */
    NO_DAY_KEY,
}

/** Việc cần làm với MỘT luật ở MỘT nhịp. */
sealed interface NavAutomationDecision {
    /** Bắn ý-định dẫn đường tới [ScheduledNavRule.placeId] bằng [ScheduledNavRule.navApp]. */
    object Launch : NavAutomationDecision

    /** Không dẫn, kèm lý do. */
    data class Skip(val reason: NavSkipReason) : NavAutomationDecision
}

/**
 * ═══ AUTOMATION #2 · DẪN ĐƯỜNG THEO LỊCH · LUẬT QUYẾT ĐỊNH ════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2.3 · R2.4 (§Design › :core). Thuần Kotlin (`:core`, cấm `android.*`) ⇒
 * bơm giờ/ngày/GPS giả mà kiểm off-car; đọc `LocationManager`, tra sổ địa chỉ và bắn ý-định là việc của
 * `ScheduledNavApplier` (`:app`).
 *
 * ## Sổ đã-dẫn là một CHUỖI NGÀY, không phải một cờ boolean
 * *"Đã dẫn"* phải tự hết hiệu lực sang ngày mới (spec R2.4). Một cờ `Boolean` thì cần ai đó **xoá** nó lúc nửa
 * đêm — mà nửa đêm là lúc không có gì chạy (xe tắt máy), nên cờ ấy sẽ còn nguyên sáng hôm sau và luật **không bao
 * giờ chạy lần thứ hai**. Lưu *"ngày đã dẫn"* rồi so với ngày hôm nay thì việc hết hiệu lực xảy ra **tự nó**,
 * không cần ai chạy đúng lúc. [lastFiredDay] rỗng = chưa dẫn bao giờ.
 *
 * ⚠ Dạng chuỗi ngày là **quy ước của chỗ gọi** (`:app` dùng `yyyy-MM-dd`) — hàm này chỉ so **bằng nhau**, nên nó
 * không mang một phép định dạng ngày thứ hai vào `:core`, và cũng không cần đồng hồ.
 */
object ScheduledNavPolicy {

    /**
     * Quyết định cho MỘT luật ở MỘT nhịp — spec R2.3.
     *
     * Thứ tự kiểm là **có chủ ý**, vì nó quyết định câu nhật ký nào hiện ra khi nhiều điều kiện cùng sai: đi từ
     * *"luật này không liên quan lúc này"* (tắt · sai ngày · ngoài giờ) tới *"đã xong việc"* (đã dẫn) rồi mới tới
     * *"đang chờ"* ([NavSkipReason.NO_GPS]). Đặt `NO_GPS` **sau** `ALREADY_FIRED` vì khi đã dẫn rồi thì GPS không
     * còn là câu hỏi — báo *"chờ GPS"* lúc ấy là nói sai việc đang xảy ra.
     *
     * @param nowMinOfDay phút-trong-ngày hiện tại (0..1439).
     * @param dow thứ hôm nay theo ISO-8601 (1 = Thứ Hai … 7 = Chủ Nhật), khớp `java.time.DayOfWeek.value`.
     * @param gpsOk đang có định vị dùng được (`GpsAvailability` ở `:app` quyết định *"dùng được"* nghĩa là gì).
     * @param lastFiredDay khoá ngày của lần dẫn gần nhất **của chính luật này**; rỗng = chưa bao giờ.
     * @param today khoá ngày hôm nay, cùng dạng với [lastFiredDay].
     */
    fun decide(
        rule: ScheduledNavRule,
        nowMinOfDay: Int,
        dow: Int,
        gpsOk: Boolean,
        lastFiredDay: String,
        today: String,
    ): NavAutomationDecision = when {
        !rule.enabled -> NavAutomationDecision.Skip(NavSkipReason.DISABLED)
        dow !in rule.days -> NavAutomationDecision.Skip(NavSkipReason.WRONG_DAY)
        !ScheduledNavRules.inWindow(rule, nowMinOfDay) -> NavAutomationDecision.Skip(NavSkipReason.OUTSIDE_WINDOW)
        today.isBlank() -> NavAutomationDecision.Skip(NavSkipReason.NO_DAY_KEY)
        lastFiredDay == today -> NavAutomationDecision.Skip(NavSkipReason.ALREADY_FIRED)
        rule.requireGps && !gpsOk -> NavAutomationDecision.Skip(NavSkipReason.NO_GPS)
        else -> NavAutomationDecision.Launch
    }

    /**
     * Luật ĐẦU TIÊN được dẫn ở nhịp này, hoặc `null` nếu không luật nào.
     *
     * ## Vì sao phải có phép chọn "một luật mỗi nhịp" ở đây
     * Hai khung giờ chồng nhau là chuyện người dùng đặt được (vd *"7–9h đến cty"* và một luật khác *"8–8h30 đón
     * con"*). Nếu `:app` cứ duyệt danh sách và bắn mọi luật nào [decide] trả `Launch`, xe sẽ nhận **hai ý-định dẫn
     * đường trong cùng một giây** — app dẫn đường sau ghi đè tuyến của app trước, và người lái thấy tuyến nhảy
     * một cách không giải thích được. Chọn một (theo **thứ tự trong sổ** = thứ tự người dùng tạo, không phải thứ
     * tự "gần giờ nhất" — thứ tự tạo là thứ duy nhất người dùng nhìn thấy và sắp được) rồi để luật kia nổ ở nhịp
     * sau (nó vẫn còn trong khung, và sổ đã-dẫn của nó vẫn trống).
     *
     * @param firedDays sổ đã-dẫn: `id luật` → khoá ngày; thiếu khoá = chưa bao giờ dẫn.
     */
    fun firstToLaunch(
        rules: List<ScheduledNavRule>,
        nowMinOfDay: Int,
        dow: Int,
        gpsOk: Boolean,
        firedDays: Map<String, String>,
        today: String,
    ): ScheduledNavRule? = rules.firstOrNull { rule ->
        decide(
            rule = rule,
            nowMinOfDay = nowMinOfDay,
            dow = dow,
            gpsOk = gpsOk,
            lastFiredDay = firedDays[rule.id].orEmpty(),
            today = today,
        ) == NavAutomationDecision.Launch
    }
}
