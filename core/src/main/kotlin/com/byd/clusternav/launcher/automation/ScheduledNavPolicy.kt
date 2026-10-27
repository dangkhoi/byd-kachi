package com.byd.clusternav.launcher.automation

/**
 * Kế hoạch lịch dẫn đường của MỘT chuyến (một lần nổ máy).
 *
 * @property trip mã chuyến (`TripGate.tripId` ở `:app` — đổi ở mỗi lần tắt máy, giữ nguyên khi BYD giết Kachi giữa đường).
 * @property ruleId luật lịch đã gắn cho chuyến này; rỗng = chuyến này KHÔNG có lịch.
 * @property fired lịch đã giao điểm đến cho app dẫn đường (xong việc của chuyến).
 */
data class NavTripPlan(val trip: String, val ruleId: String, val fired: Boolean) {
    val hasRule: Boolean get() = ruleId.isNotEmpty()
}

/** Vì sao nhịp này lịch không dẫn. `enum` để nhật ký trên xe nói đúng một lý do đã lường. */
enum class NavHoldReason {
    /** Chuyến này không có lịch: giờ nổ máy ngoài mọi khung / sai thứ / luật tắt / có điểm đến chủ động. */
    NO_RULE,

    /** Lịch của chuyến đã dẫn xong. */
    FIRED,

    /** Luật đòi GPS mà chưa có định vị (usecase hầm) — CHỜ, nhịp sau hỏi lại. */
    NO_GPS,

    /** Luật đã gắn cho chuyến không còn (bị xoá / tắt giữa chuyến). */
    RULE_GONE,

    /** Không biết mã chuyến ⇒ không biết "một lần mỗi chuyến" ⇒ thà không dẫn. */
    NO_TRIP,

    /** Thấy chuyến lần đầu khi đã đi quá [ScheduledNavPolicy.START_GRACE_MS] — không biết giờ nổ máy ⇒ không dẫn. */
    LATE,
}

/** Việc của một nhịp: [Launch] (bắn [rule], lưu [plan] sau khi giao được với `fired = true`) hoặc [Hold]. */
sealed interface NavTripStep {
    val plan: NavTripPlan?

    data class Launch(val rule: ScheduledNavRule, override val plan: NavTripPlan) : NavTripStep

    data class Hold(val reason: NavHoldReason, override val plan: NavTripPlan?) : NavTripStep
}

/**
 * ═══ AUTOMATION #2 · DẪN ĐƯỜNG THEO LỊCH — QUYẾT MỘT LẦN MỖI CHUYẾN (owner 27/10) ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-nav-schedule-per-trip.html`. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Vì sao không còn "xét mỗi phút trong khung giờ"
 * [ĐO mã 27/10] lịch cũ xét lại MỖI NHỊP 60 s: đang đi một chuyến theo yêu cầu, tới 16:00 thì lịch "về nhà" bắn và ĐÈ
 * tuyến đang chạy (owner: *"làm rối trí quá"*). Lịch là điểm đến MẶC ĐỊNH của một chuyến khi người lái chưa chọn gì ⇒
 * nó chỉ được quyết ở ĐẦU chuyến:
 *  - Lần đầu thấy một chuyến mới ⇒ chọn luật theo **giờ nổ máy** (giờ của nhịp này) — một lần, ghi vào [NavTripPlan].
 *  - Các nhịp sau trong cùng chuyến chỉ THI HÀNH kế hoạch đó (chờ GPS rồi dẫn), không bao giờ chọn lại ⇒ đang chạy
 *    mà đồng hồ vào khung giờ thì không có gì xảy ra.
 *  - Điểm đến chủ động (Telegram) thắng: chỗ gọi [blocked] chuyến khi điểm đến đó đã dẫn.
 *  - Mỗi chuyến nổ máy trong khung đều dẫn (owner chọn E1) — không còn sổ "một lần mỗi ngày".
 *
 * Mã chuyến đổi ở mỗi lần tắt máy và GIỮ khi BYD giết Kachi giữa đường ⇒ tiến trình bật lại giữa chuyến đọc kế hoạch
 * đã lưu, không chọn lại.
 */
object ScheduledNavPolicy {

    /**
     * Chuyến chỉ được gắn lịch khi Kachi thấy nó trong 5 phút đầu (tính từ lần thức đầu của tiến trình). Quá mốc này mà
     * chưa có kế hoạch lưu nghĩa là vòng automation không chạy lúc nổ máy (vd vừa thêm luật giữa đường) ⇒ không biết
     * giờ nổ máy thật ⇒ không gắn lịch, tránh đúng lỗi chen giữa chuyến.
     */
    const val START_GRACE_MS: Long = 5L * 60_000

    /** Luật đầu tiên (theo thứ tự trong sổ) khớp giờ nổ máy [startMin] + thứ [dow]; `null` = chuyến không có lịch. */
    fun pickAtStart(rules: List<ScheduledNavRule>, startMin: Int, dow: Int): ScheduledNavRule? =
        rules.firstOrNull { it.enabled && dow in it.days && ScheduledNavRules.inWindow(it, startMin) }

    /** Kế hoạch "chuyến này không có lịch" — dùng khi điểm đến chủ động đã dẫn ([NavTripStep] sau đó luôn `Hold`). */
    fun blocked(trip: String): NavTripPlan = NavTripPlan(trip, "", fired = true)

    /**
     * Một nhịp.
     *
     * @param saved kế hoạch đã lưu (có thể của chuyến TRƯỚC).
     * @param trip mã chuyến hiện tại; rỗng ⇒ [NavHoldReason.NO_TRIP].
     * @param nowMinOfDay phút-trong-ngày của nhịp này · @param dow thứ ISO-8601 (1 = T2 … 7 = CN).
     * @param sinceStartMs ms kể từ lần thức đầu của tiến trình; `null` = không biết (coi như vừa nổ máy).
     * @param gpsOk đang có định vị dùng được.
     */
    fun step(
        saved: NavTripPlan?,
        trip: String,
        rules: List<ScheduledNavRule>,
        nowMinOfDay: Int,
        dow: Int,
        sinceStartMs: Long?,
        gpsOk: Boolean,
    ): NavTripStep {
        if (trip.isBlank()) return NavTripStep.Hold(NavHoldReason.NO_TRIP, null)
        val plan = if (saved?.trip == trip) saved else {
            if (sinceStartMs != null && sinceStartMs > START_GRACE_MS) {
                return NavTripStep.Hold(NavHoldReason.LATE, NavTripPlan(trip, "", fired = false))
            }
            NavTripPlan(trip, pickAtStart(rules, nowMinOfDay, dow)?.id.orEmpty(), fired = false)
        }
        if (!plan.hasRule) return NavTripStep.Hold(NavHoldReason.NO_RULE, plan)
        if (plan.fired) return NavTripStep.Hold(NavHoldReason.FIRED, plan)
        val rule = rules.firstOrNull { it.id == plan.ruleId && it.enabled }
            ?: return NavTripStep.Hold(NavHoldReason.RULE_GONE, plan)
        if (rule.requireGps && !gpsOk) return NavTripStep.Hold(NavHoldReason.NO_GPS, plan)
        return NavTripStep.Launch(rule, plan)
    }

    /** Sổ luật vừa sửa: luật của kế hoạch không còn ⇒ bỏ lịch của chuyến (`newId` có thể cấp lại id đã rảnh). */
    fun pruned(plan: NavTripPlan?, liveIds: Set<String>): NavTripPlan? =
        if (plan != null && plan.hasRule && plan.ruleId !in liveIds) plan.copy(ruleId = "") else plan

    /** Dạng lưu một dòng `trip|ruleId|0/1`. */
    fun encode(plan: NavTripPlan): String = "${plan.trip}|${plan.ruleId}|${if (plan.fired) 1 else 0}"

    /** Chuỗi hỏng / rỗng ⇒ `null` (coi như chưa có kế hoạch — chuyến được quyết lại theo [START_GRACE_MS]). */
    fun decode(raw: String?): NavTripPlan? {
        val parts = raw?.trim()?.split('|') ?: return null
        if (parts.size != 3 || parts[0].isBlank()) return null
        val fired = when (parts[2]) { "1" -> true; "0" -> false; else -> return null }
        return NavTripPlan(parts[0], parts[1], fired)
    }
}
