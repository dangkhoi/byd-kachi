package com.byd.clusternav.launcher.automation

/**
 * Kế hoạch lịch dẫn đường của MỘT chuyến (một lần nổ máy).
 *
 * @property trip mã chuyến (`TripGate.tripId` ở `:app` — đổi ở mỗi lần tắt máy, giữ nguyên khi BYD giết Kachi giữa đường).
 * @property ruleId luật lịch đã gắn cho chuyến này; rỗng = chuyến này KHÔNG có lịch (khi [decided]).
 * @property fired lịch đã giao điểm đến cho app dẫn đường (xong việc của chuyến).
 * @property decided đã CHỌN luật cho chuyến chưa. `false` = chỉ mới ghi mốc thức ([wakeAt]) lúc nổ máy, chưa có nhịp lịch nào.
 * @property wakeAt `elapsedRealtime` của lần thức đầu của chuyến (ghi bởi màn chính lúc nổ máy, SỐNG qua lượt BYD giết
 *   tiến trình giữa đường — review 27/10 P1: mốc thức của RIÊNG tiến trình mới sẽ trông như vừa nổ máy); `-1` = không biết.
 * @property ruleSig dấu vân tay của luật lúc gắn ([ScheduledNavPolicy.sig]) — `newId` cấp lại id đã rảnh, nên chỉ so id
 *   là thi hành nhầm luật MỚI mang id cũ giữa chuyến (review 27/10 P2).
 */
data class NavTripPlan(
    val trip: String,
    val ruleId: String,
    val fired: Boolean,
    val decided: Boolean = true,
    val wakeAt: Long = -1L,
    val ruleSig: String = "",
) {
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
     * Chuyến chỉ được gắn lịch khi nhịp lịch đầu tiên tới trong 5 phút đầu kể từ lần thức của chuyến ([NavTripPlan.wakeAt],
     * ghi bởi [noteWake] lúc màn chính thức; không có mốc ⇒ lùi về mốc thức của tiến trình). Quá mốc này nghĩa là vòng
     * automation không chạy lúc nổ máy (vd vừa thêm luật giữa đường) ⇒ không biết giờ nổ máy thật ⇒ không gắn lịch.
     */
    const val START_GRACE_MS: Long = 5L * 60_000

    /** Luật đầu tiên (theo thứ tự trong sổ) khớp giờ nổ máy [startMin] + thứ [dow]; `null` = chuyến không có lịch. */
    fun pickAtStart(rules: List<ScheduledNavRule>, startMin: Int, dow: Int): ScheduledNavRule? =
        rules.firstOrNull { it.enabled && dow in it.days && ScheduledNavRules.inWindow(it, startMin) }

    /** Kế hoạch "chuyến này không có lịch" — dùng khi điểm đến chủ động đã dẫn ([NavTripStep] sau đó luôn `Hold`). */
    fun blocked(trip: String): NavTripPlan = NavTripPlan(trip, "", fired = true)

    /** Dấu vân tay luật: đích + khung + app (không gồm id). Hai luật cùng id khác dấu = luật KHÁC. */
    fun sig(rule: ScheduledNavRule): String =
        "${rule.placeId}#${rule.startMin}-${rule.endMin}#${rule.navApp}".hashCode().toUInt().toString(16)

    /**
     * Màn chính vừa THỨC (nổ máy / màn bật) ở mốc [nowElapsed]: trả kế hoạch cần lưu, hoặc `null` khi chuyến này đã có
     * kế hoạch (tiến trình bật lại giữa đường — GIỮ mốc cũ, không ghi đè). Gọi từ `:app` ở mọi đường "thức" của màn chính,
     * KHÔNG phụ thuộc vòng automation có chạy hay không.
     */
    fun noteWake(saved: NavTripPlan?, trip: String, nowElapsed: Long): NavTripPlan? {
        if (trip.isBlank() || saved?.trip == trip) return null
        return NavTripPlan(trip, "", fired = false, decided = false, wakeAt = nowElapsed)
    }

    /**
     * Một nhịp.
     *
     * @param saved kế hoạch đã lưu (có thể của chuyến TRƯỚC, hoặc mốc thức chưa quyết của chuyến này).
     * @param trip mã chuyến hiện tại; rỗng ⇒ [NavHoldReason.NO_TRIP].
     * @param nowMinOfDay phút-trong-ngày của nhịp này · @param dow thứ ISO-8601 (1 = T2 … 7 = CN).
     * @param nowElapsed `elapsedRealtime` của nhịp này.
     * @param processWakeAt mốc thức đầu của TIẾN TRÌNH (`-1` = chưa thức) — chỉ dùng khi chuyến chưa có mốc thức lưu.
     * @param gpsOk đang có định vị dùng được.
     */
    fun step(
        saved: NavTripPlan?,
        trip: String,
        rules: List<ScheduledNavRule>,
        nowMinOfDay: Int,
        dow: Int,
        nowElapsed: Long,
        processWakeAt: Long,
        gpsOk: Boolean,
    ): NavTripStep {
        if (trip.isBlank()) return NavTripStep.Hold(NavHoldReason.NO_TRIP, null)
        val known = saved?.takeIf { it.trip == trip }
        val plan = if (known != null && known.decided) known else {
            val wakeAt = known?.wakeAt?.takeIf { it >= 0 } ?: processWakeAt.takeIf { it >= 0 } ?: nowElapsed
            val age = (nowElapsed - wakeAt).coerceAtLeast(0)
            if (age > START_GRACE_MS) {
                return NavTripStep.Hold(NavHoldReason.LATE, NavTripPlan(trip, "", fired = false, wakeAt = wakeAt))
            }
            // Chọn theo giờ NỔ MÁY (= giờ nhịp này lùi lại đúng tuổi chuyến), không theo giờ nhịp.
            val startMin = ((nowMinOfDay - (age / 60_000).toInt()) % 1440 + 1440) % 1440
            val rule = pickAtStart(rules, startMin, dow)
            NavTripPlan(trip, rule?.id.orEmpty(), fired = false, wakeAt = wakeAt, ruleSig = rule?.let(::sig).orEmpty())
        }
        if (!plan.hasRule) return NavTripStep.Hold(NavHoldReason.NO_RULE, plan)
        if (plan.fired) return NavTripStep.Hold(NavHoldReason.FIRED, plan)
        val rule = rules.firstOrNull { it.id == plan.ruleId && it.enabled && sig(it) == plan.ruleSig }
            ?: return NavTripStep.Hold(NavHoldReason.RULE_GONE, plan)
        if (rule.requireGps && !gpsOk) return NavTripStep.Hold(NavHoldReason.NO_GPS, plan)
        return NavTripStep.Launch(rule, plan)
    }

    /** Sổ luật vừa sửa: luật của kế hoạch không còn (id + dấu vân tay) ⇒ bỏ lịch của chuyến, không quyết lại. */
    fun pruned(plan: NavTripPlan?, live: List<ScheduledNavRule>): NavTripPlan? =
        if (plan != null && plan.hasRule && live.none { it.id == plan.ruleId && sig(it) == plan.ruleSig }) {
            plan.copy(ruleId = "", ruleSig = "")
        } else plan

    /** Dạng lưu một dòng `trip|ruleId|fired 0/1|decided 0/1|wakeAt|ruleSig`. */
    fun encode(plan: NavTripPlan): String =
        "${plan.trip}|${plan.ruleId}|${if (plan.fired) 1 else 0}|${if (plan.decided) 1 else 0}|${plan.wakeAt}|${plan.ruleSig}"

    /** Chuỗi hỏng / rỗng / dạng cũ (sổ `id=ngày` ≤ 3.1.21) ⇒ `null` (coi như chưa có kế hoạch). */
    fun decode(raw: String?): NavTripPlan? {
        val parts = raw?.trim()?.split('|') ?: return null
        if (parts.size != 6 || parts[0].isBlank()) return null
        fun flag(v: String) = when (v) { "1" -> true; "0" -> false; else -> null }
        val fired = flag(parts[2]) ?: return null
        val decided = flag(parts[3]) ?: return null
        val wakeAt = parts[4].toLongOrNull() ?: return null
        return NavTripPlan(parts[0], parts[1], fired, decided, wakeAt, parts[5])
    }
}
