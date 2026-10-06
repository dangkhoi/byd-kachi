package com.byd.clusternav.launcher

/**
 * ═══ 2.88 · PHÁN XÉT TỪNG BÁNH BẰNG CHÍNH LỜI CỦA XE — luật M1–M7, thuần (`:core`) ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-288-tyre-car-state.html` §3 R1/R2 · §4.2.
 *
 * ## Vì sao không còn một con số ngưỡng nào (owner 04/10)
 * *"Cảnh báo nó theo tùy loại xe đó nha, không hardcode số đâu"*. Ngưỡng 2.0 / 3.2 / 0.3 bar cũ là số agent tự chọn
 * (spec gói 2 OQ1, owner chưa từng trả lời) và [ĐO sweep 09-16/09-21] xe owner chạy trục sau cao hơn trục trước
 * ~0.2 bar ⇒ luật "lệch" sai ngay từ thiết kế. Xe đã có HAI lời phán cho từng bánh, hiệu chỉnh theo đời xe:
 *  1. **màu cụm đồng hồ** `INSTRUMENT_2IN1_{LF,RF,LB,RB}_TYRE_COLOR` — [ĐO source] launcher gốc BYD (L3
 *     `TyreCardView.java:127-167`) tô thẻ lốp CHỈ bằng nguồn này, không có ngưỡng nào;
 *  2. **mã trạng thái TPMS** của `BYDAutoTyreDevice`: áp suất · rò khí · hệ thống ([ĐO source] jadx-tmap
 *     `tyre/BYDAutoTyreDevice.java:20-22, 36-38, 43-47`; SDK: *"exceeds / below normal range"* — người gọi không
 *     truyền giới hạn nào, nên "bình thường" là của xe).
 *
 * ## Hằng dưới đây là MÃ OEM, không phải ngưỡng
 * Mỗi hằng chép đúng một giá trị enum của stub (dẫn `file:line`). Không hằng nào là một con số áp suất.
 *
 * ## Luật (khớp luật ĐẦU TIÊN thì dừng)
 *  M1 C hợp lệ ⇒ C quyết MÀU (3 đỏ · 2 vàng · 1 xanh); chữ lý do (2.93 `TYRE-BURST-REASON`, mã báo CHỈ khi màu cụm CÙNG
 *     CHIỀU): ĐỎ ⇒ 4092 "nổ lốp" › PS/LK › 4093 "bất thường" › "báo đỏ"; VÀNG ⇒ PS/LK › 4093 › "chú ý"; TRẮNG ⇒ không chữ.
 *  M2 SYS ∈ {1 tự kiểm, 4 bị che} ⇒ XÁM.
 *  M3 SYS ∈ {2 tín hiệu bất thường, 3 hỏng} ⇒ VÀNG "lỗi cảm biến".
 *  M4 LK = 1 hoặc PS = 2 hoặc PS = 1 ⇒ ĐỎ.
 *  M5 LK = 2 ⇒ VÀNG.
 *  M6 PS = 0 và LK = 0 và SYS = 0 (cả ba HỢP LỆ) ⇒ XANH.
 *  M7 còn lại ⇒ XÁM; gặp mã lạ (không phải sentinel) ⇒ lý do "mã N".
 * Off-car (mọi mã `null`) rơi thẳng M7 ⇒ **không bao giờ XANH**.
 */
object TyreJudge {

    // ── Màu cụm đồng hồ — [ĐO source] jadx-tmap `instrument/BYDAutoInstrumentDevice.java:134-137` ──
    /** `COLOR_INVALID` (:134) — cụm chưa có màu cho bánh này. Tầng đọc đã lọc thành `null` (`HalReadTables.INVALID_VALUES`). */
    const val COLOUR_INVALID = 0
    /** `COLOR_WHITE` (:136) — cụm vẽ bánh màu trắng = bình thường. */
    const val COLOUR_WHITE = 1
    /** `COLOR_YELLOW` (:137). */
    const val COLOUR_YELLOW = 2
    /** `COLOR_RED` (:135). */
    const val COLOUR_RED = 3

    // ── TPMS — [ĐO source] jadx-tmap `tyre/BYDAutoTyreDevice.java` ──
    /** `getTyrePressureState` — `TYRE_PRESSURE_STATE_NORMAL` (:36). */
    const val PRESSURE_NORMAL = 0
    /** `_OVERPRESSURE` — SDK: *"exceeds normal range"* (:37). */
    const val PRESSURE_OVER = 1
    /** `_UNDERPRESSURE` — SDK: *"below normal range"* (:38). */
    const val PRESSURE_UNDER = 2

    /** `getTyreAirLeakState` — `TYRE_AIR_LEAK_STATE_NORMAL`, không rò (:20). */
    const val LEAK_NONE = 0
    /** `_QUICK` — rò NHANH (:21). */
    const val LEAK_FAST = 1
    /** `_SLOW` — rò CHẬM (:22). */
    const val LEAK_SLOW = 2

    /** `getTyreSystemState()` — `TYRE_SYSTEM_STATE_NORMAL` (:45). */
    const val SYS_NORMAL = 0
    /** `_SELF_CHECKING` — đang tự kiểm (:46). */
    const val SYS_SELF_CHECK = 1
    /** `_SIGNAL_ANOMAL` — tín hiệu bất thường (:47). */
    const val SYS_SIGNAL_ABNORMAL = 2
    /** `_BREAKDOWN` — hỏng (:43). */
    const val SYS_BREAKDOWN = 3
    /** `_MASKED` — bị che (:44). */
    const val SYS_MASKED = 4

    // ── Áp suất: bốn MÃ TRẠNG THÁI nằm trong dải số của `getTyrePressureValue` — không phải áp suất ──
    // [ĐO source] launcher gốc BYD L3 `customcard/TyreCardView.java:34-37` khai bốn hằng này và (:140-161) KHÔNG BAO
    // GIỜ in chúng thành số: 4092 ⇒ chữ "nổ lốp", 4093 ⇒ chữ "bất thường", 4094 / 4095 ⇒ không có số. ~41 bar không
    // thể là áp suất thật của một bánh ⇒ coi là mã không đổi được một con số thật nào. Lọc ở TẦNG ĐỌC
    // (`HalReadTables.INVALID_VALUES`, cùng cơ chế `tyre_c_*` → {0}) để MỌI bề mặt (chip · bảng · ô nhóm · ô nhỏ ·
    // câu hỏi bằng giọng) dùng chung một luật. ⚠ L3 đọc kênh CỤM `INSTRUMENT_2IN1_*_TYRE_PRESSURE`; việc getter
    // `BYDAutoTyreDevice.getTyrePressureValue` mà Kachi đọc dùng CÙNG bộ mã là [SUY] — spec §6.1 lớp 1 chốt.
    /** `BURST_VALUE` (L3 :35) — lốp nổ. */
    const val PRESSURE_BURST = 4092
    /** `ABNORMAL_VALUE` (L3 :34). */
    const val PRESSURE_ABNORMAL = 4093
    /** `NONE_VALUE` (L3 :37) — chưa có số (cũng là `TYRE_PRESSURE_VALUE_MAX` của stub jadx-tmap :39). */
    const val PRESSURE_NONE = 4094
    /** `DEFAULT_VALUE` (L3 :36). */
    const val PRESSURE_DEFAULT = 4095

    private val COLOURS = setOf(COLOUR_WHITE, COLOUR_YELLOW, COLOUR_RED)
    private val PRESSURE_STATES = setOf(PRESSURE_NORMAL, PRESSURE_OVER, PRESSURE_UNDER)
    private val LEAK_STATES = setOf(LEAK_NONE, LEAK_FAST, LEAK_SLOW)
    private val SYS_STATES = setOf(SYS_NORMAL, SYS_SELF_CHECK, SYS_SIGNAL_ABNORMAL, SYS_BREAKDOWN, SYS_MASKED)

    /**
     * Luật M cho MỘT bánh. Mọi tham số là mã THÔ đã đọc (hoặc `null` = chưa đọc / đọc hỏng). Mã ngoài tập hợp lệ —
     * kể cả bốn sentinel `-2147482648..-2147482645` lỡ lọt qua tầng đọc — bị coi như không có.
     */
    fun judge(c: Int?, ps: Int?, lk: Int?, sys: Int?, pc: Int? = null): TyreJudgement {
        val cv = c?.takeIf { it in COLOURS }
        val psv = ps?.takeIf { it in PRESSURE_STATES }
        val lkv = lk?.takeIf { it in LEAK_STATES }
        val sysv = sys?.takeIf { it in SYS_STATES }
        // M1 — cụm đã phán theo đời xe của nó: MÀU là của cụm, chữ là của TPMS (nếu có). 2.93 `TYRE-BURST-REASON`: mã báo
        // trong dải áp suất ([pc]) chỉ thành CHỮ khi màu cụm CÙNG CHIỀU với nó ([burstUnder]/[abnormalUnder] — KDoc ở đó).
        // Không màu cụm ⇒ mã này không nói gì (M2–M7 như cũ; [ĐO source] L3 `TyreCardView.java:133-138` cũng "--" rồi dừng).
        when (cv) {
            COLOUR_RED -> return cluster(
                burstUnder(cv, pc) ?: reasonOf(psv, lkv) ?: abnormalUnder(cv, pc) ?: TyreStatus.CAR_ALERT, TyreSeverity.ALERT,
            )
            COLOUR_YELLOW -> return cluster(reasonOf(psv, lkv) ?: abnormalUnder(cv, pc) ?: TyreStatus.CAR_WARN, TyreSeverity.WARN)
            COLOUR_WHITE -> return cluster(TyreStatus.OK, TyreSeverity.OK)   // cụm nói bình thường ⇒ không chữ báo (KDoc [burstUnder])
        }
        // M2 — hệ TPMS đang tự kiểm / bị che: số và mã lúc này chưa phải lời phán.
        if (sysv == SYS_SELF_CHECK || sysv == SYS_MASKED) return tyre(TyreStatus.UNKNOWN, TyreSeverity.NONE)
        // M3 — cảm biến báo lỗi: VÀNG cho mọi bánh (SYS không có tham số bánh).
        if (sysv == SYS_SIGNAL_ABNORMAL || sysv == SYS_BREAKDOWN) return tyre(TyreStatus.SENSOR, TyreSeverity.WARN)
        // M4 — một mã bất thường HỢP LỆ là đủ để báo, kể cả khi SYS đọc hỏng.
        if (lkv == LEAK_FAST) return tyre(TyreStatus.LEAK_FAST, TyreSeverity.ALERT)
        if (psv == PRESSURE_UNDER) return tyre(TyreStatus.UNDER, TyreSeverity.ALERT)
        if (psv == PRESSURE_OVER) return tyre(TyreStatus.OVER, TyreSeverity.ALERT)
        // M5
        if (lkv == LEAK_SLOW) return tyre(TyreStatus.LEAK_SLOW, TyreSeverity.WARN)
        // M6 — XANH chỉ khi ĐỦ ba mã hợp lệ và cả ba bình thường.
        if (psv == PRESSURE_NORMAL && lkv == LEAK_NONE && sysv == SYS_NORMAL) {
            return tyre(TyreStatus.OK, TyreSeverity.OK)
        }
        // M7 — không phán được. Mã lạ (không phải sentinel, không phải `COLOR_INVALID`) ⇒ nói ra để anh em chụp gửi.
        val odd = odd(c, COLOURS + COLOUR_INVALID) ?: odd(ps, PRESSURE_STATES) ?: odd(lk, LEAK_STATES)
            ?: odd(sys, SYS_STATES)
        return if (odd != null) TyreJudgement(TyreStatus.CODE, TyreSeverity.NONE, TyreSource.NONE, odd)
        else TyreJudgement(TyreStatus.UNKNOWN, TyreSeverity.NONE, TyreSource.NONE)
    }

    /** [v] nếu nó là mã LẠ của chính bảng [known] (không phải sentinel) — xét theo TỪNG bảng: `ps = 3` là lạ dù 3 có nghĩa ở bảng SYS. */
    private fun odd(v: Int?, known: Set<Int>): Int? =
        v?.takeIf { it !in known && !HalBindingTable.isSentinelRc(it.toLong()) }

    /**
     * 2.93 `TYRE-BURST-REASON` — chữ "nổ lốp" từ MÃ BÁO 4092 trong dải số áp suất ([pc] = lời đáp thô của getter áp suất,
     * `CarStatus.Tyres.pcFl`…) — CHỈ khi cụm ĐỎ.
     *
     * ## Vì sao cổng là màu CÙNG CHIỀU, không phải "màu hợp lệ bất kỳ" như L3 (senior review 2.93 Pass 1 — chặn báo động giả)
     *  - L3 đọc mã VÀ màu từ CÙNG kênh cụm (`INSTRUMENT_2IN1_*_TYRE_PRESSURE`/`_COLOR` — [ĐO source] `BydAutoHelper.java:25-54`)
     *    nên `:140-158` không cần xét màu. Kachi đọc mã từ getter TPMS KHÁC (`BYDAutoTyreDevice.getTyrePressureValue`, stub
     *    khai dải hợp lệ 0..4094 — jadx-tmap `:39-40`); hai getter cùng bộ mã mới chỉ là [SUY] 🚗. Một nguồn [SUY] không được
     *    nói "nổ lốp" với người đang lái khi lời phán [ĐO] của cụm là TRẮNG (bình thường) — đó là báo động giả.
     *  - Chính L3 ghép cặp màu ↔ mã: màu 3 ⇒ icon `card_tyre_pressure_tyre_burst_icon`, màu 2 ⇒ `…_abnormal_icon`
     *    ([ĐO source] `TyreCardView.java:210-216`) ⇒ nổ lốp đi với ĐỎ, bất thường đi với VÀNG (hoặc ĐỎ).
     *  - Cụm TRẮNG ⇒ 2.88 tin cụm hoàn toàn (không đọc cả PS/LK — `CarDataAdapter.readTyres` `ifNeed`) ⇒ mã cũng im lặng.
     *  Mã vẫn vào dòng `TYRE raw … pc=` ở MỌI màu (bằng chứng 🚗), chỉ không thành chữ.
     *
     * ⚠ L3 trên xe vùng ROW (`VehicleUtils.isRow`: `ro.build.region = ROW` hoặc `ro.build.car.region = oversea`) KHÔNG in chữ
     * "nổ lốp" mà in "--" + icon cảnh báo nổ lốp (`TyreCardView.java:140-150`); Kachi không có icon riêng ⇒ nói bằng chữ ở mọi vùng.
     */
    private fun burstUnder(cv: Int?, pc: Int?): TyreStatus? = TyreStatus.BURST.takeIf { pc == PRESSURE_BURST && cv == COLOUR_RED }

    /**
     * Chữ "bất thường" từ MÃ BÁO 4093 — khi cụm VÀNG hoặc ĐỎ (cổng cùng chiều: KDoc [burstUnder]). Đứng SAU chữ TPMS
     * ([reasonOf] — mã trạng thái [ĐO source] của SDK, cụ thể hơn): "bất thường" chỉ thay chữ CHUNG "báo đỏ"/"chú ý", không
     * che "xì nhanh"/"non" mà 2.92 đã nói đúng.
     */
    private fun abnormalUnder(cv: Int?, pc: Int?): TyreStatus? =
        TyreStatus.ABNORMAL.takeIf { pc == PRESSURE_ABNORMAL && (cv == COLOUR_YELLOW || cv == COLOUR_RED) }

    /** Chữ lý do từ TPMS cho nhánh M1 — thứ tự = thứ nguy trước (rò nhanh ▸ non ▸ căng ▸ rò chậm). */
    private fun reasonOf(ps: Int?, lk: Int?): TyreStatus? = when {
        lk == LEAK_FAST -> TyreStatus.LEAK_FAST
        ps == PRESSURE_UNDER -> TyreStatus.UNDER
        ps == PRESSURE_OVER -> TyreStatus.OVER
        lk == LEAK_SLOW -> TyreStatus.LEAK_SLOW
        else -> null
    }

    private fun cluster(s: TyreStatus, sev: TyreSeverity) = TyreJudgement(s, sev, TyreSource.CLUSTER)
    private fun tyre(s: TyreStatus, sev: TyreSeverity) = TyreJudgement(s, sev, TyreSource.TYRE)
}

/** Lời phán đến từ đâu — cho dòng log `TYRE raw … src=` (kênh 2) và bài kiểm. */
enum class TyreSource { CLUSTER, TYRE, NONE }

/**
 * Kết quả luật M cho một bánh.
 *
 * @property status chữ lý do (non · căng · xì nhanh…).
 * @property severity MÀU. Tách khỏi [status] có chủ ý: ở nhánh M1 màu là của **cụm đồng hồ** còn chữ là của TPMS,
 *   nên cụm báo VÀNG mà TPMS nói "non" thì bánh hiện VÀNG chữ "non" — không đổi màu theo bảng mặc định của chữ.
 * @property code mã lạ khi [status] là [TyreStatus.CODE].
 */
data class TyreJudgement(
    val status: TyreStatus,
    val severity: TyreSeverity,
    val source: TyreSource,
    val code: Int? = null,
)

/**
 * Mã datum lốp — MỘT chỗ khai bánh nào mang mã nào, cho bảng nhu cầu ([CarDataDemand.COMPANION]), bộ chọn
 * ([CapabilityCatalog.HIDDEN_FROM_PICKER]) và bảng chữ ([TelemetryEnums]). Thứ tự luôn TT · TP · ST · SP.
 *
 * ⚠ Đối tượng LÁ (không đọc một `val` của bộ đăng ký nào) ⇒ không thêm cạnh thứ tự khởi tạo nào (bẫy `BUILT_IN`).
 */
object TyreIds {
    val PRESSURE: List<String> = listOf("tyre_p_fl", "tyre_p_fr", "tyre_p_rl", "tyre_p_rr")
    val COLOUR: List<String> = listOf("tyre_c_fl", "tyre_c_fr", "tyre_c_rl", "tyre_c_rr")
    val PRESSURE_STATE: List<String> = listOf("tyre_ps_fl", "tyre_ps_fr", "tyre_ps_rl", "tyre_ps_rr")
    val AIR_LEAK: List<String> = listOf("tyre_lk_fl", "tyre_lk_fr", "tyre_lk_rl", "tyre_lk_rr")
    const val SYSTEM = "tyre_sys"

    /** 13 mã trạng thái THÔ — chỉ để chip/bảng phán xét, không phải ô để đặt. */
    val RAW_STATES: List<String> = COLOUR + PRESSURE_STATE + AIR_LEAK + SYSTEM

    /** Áp suất bánh i ⇒ đọc kèm màu cụm + trạng thái + rò khí của bánh i và trạng thái hệ thống. */
    val COMPANIONS: Map<String, Set<String>> =
        PRESSURE.indices.associate { i -> PRESSURE[i] to setOf(COLOUR[i], PRESSURE_STATE[i], AIR_LEAK[i], SYSTEM) }

    /**
     * Lý do ẩn 13 mã thô khỏi MỌI bộ chọn ([CapabilityCatalog.HIDDEN_FROM_PICKER]). Datum GIỮ trong bộ đăng ký: cầu
     * kiểm thử `sweep` · câu hỏi bằng giọng · chip/bảng lốp vẫn đọc chúng.
     */
    const val HIDDEN_WHY =
        "2.88 (owner 04/10 \"cảnh báo theo tùy loại xe, không hardcode số\"): 13 mã trạng thái THÔ của xe (màu cụm " +
            "đồng hồ · trạng thái áp · rò khí · hệ thống TPMS) là ĐẦU VÀO cho chip/bảng lốp phán màu, không phải một " +
            "ô để đặt — một con số 0/1/2 đứng một mình không nói được gì với người lái."
}
