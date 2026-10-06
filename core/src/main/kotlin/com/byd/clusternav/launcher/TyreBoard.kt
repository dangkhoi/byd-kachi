package com.byd.clusternav.launcher

/**
 * BẢNG ÁP SUẤT LỐP 4 BÁNH (W4) — phần QUYẾT ĐỊNH, thuần Kotlin (`:core`, cấm `android.*`) ⇒ test off-car.
 * Phần VẼ nằm ở `:app` (ô vẽ tay, cùng lối với vòng đo và sơ đồ ghế); chip *Áp suất lốp* ([TopStripChips]) đọc CÙNG
 * kết quả này.
 *
 * Spec: `docs/specs/kachi-unified-capability-tile.html` §4.4 (R6–R8) · **2.88** `docs/specs/kachi-288-tyre-car-state.html`.
 *
 * ## Dữ liệu
 *  • **4 áp suất** — [EvidenceTier.PROVEN] (`getTyrePressureValue`, kPa). Chỉ để HIỆN, không để phán xét.
 *  • **4 nhiệt độ** — [EvidenceTier.NEEDS_CAR] ⇒ off-car luôn "—".
 *  • **2.88 · 13 mã trạng thái THÔ của xe** (màu cụm · áp · rò khí ×4 + hệ thống) — [EvidenceTier.NEEDS_CAR]; phán
 *    xét đi qua [TyreJudge] (luật M1–M7). **Không còn số ngưỡng nào** (owner 04/10: *"không hardcode số"*).
 * Xe trả **kPa**; hiển thị đi qua lựa chọn đơn vị của người dùng ([UnitPrefs], mặc định `bar`).
 */

/** MÀU của một bánh, theo mức nặng. Thứ tự khai = thứ tự nặng dần (dùng `>=`/`maxOf`). */
enum class TyreSeverity {
    /** XÁM — chưa phán được (chưa đọc / off-car / mã lạ). KHÔNG bao giờ tô xanh. */ NONE,
    /** XANH — xe nói bình thường. */ OK,
    /** VÀNG. */ WARN,
    /** ĐỎ. */ ALERT;

    /** Có cần làm nổi bật cảnh báo không (vàng/đỏ). */
    val alert: Boolean get() = this == WARN || this == ALERT
}

/**
 * Chữ lý do của một bánh + MÀU mặc định khi chữ ấy do TPMS nói ([TyreJudge] nhánh M2–M7; nhánh M1 lấy màu của cụm).
 * Thứ tự khai = thứ tự [TyreBoard.verdict] nêu lỗi trong cùng mức nặng (nguy trước).
 */
enum class TyreStatus(val severity: TyreSeverity) {
    /** Chưa phán được ⇒ không chữ, không bịa (R7 gói 2). */ UNKNOWN(TyreSeverity.NONE),
    /** Xe nói bình thường. */ OK(TyreSeverity.OK),
    /**
     * 2.93 `TYRE-BURST-REASON` — getter áp suất trả MÃ 4092 ([TyreJudge.PRESSURE_BURST]): NỔ LỐP. Chỉ đặt ở nhánh M1 khi cụm
     * ĐỎ (cổng màu CÙNG CHIỀU — senior review 2.93 Pass 1, KDoc `TyreJudge.burstUnder`: cụm trắng/vàng ⇒ không bao giờ "nổ
     * lốp") ⇒ màu luôn là màu cụm; mức mặc định chỉ để xếp thứ tự câu kết luận.
     */
    BURST(TyreSeverity.ALERT),
    /** Rò khí NHANH. */ LEAK_FAST(TyreSeverity.ALERT),
    /** Non — TPMS: dưới khoảng bình thường của xe. */ UNDER(TyreSeverity.ALERT),
    /** Căng — TPMS: trên khoảng bình thường của xe. */ OVER(TyreSeverity.ALERT),
    /** Cụm báo ĐỎ mà TPMS không nói vì sao. */ CAR_ALERT(TyreSeverity.ALERT),
    /** Rò khí CHẬM. */ LEAK_SLOW(TyreSeverity.WARN),
    /** 2.93 — getter áp suất trả MÃ 4093 ([TyreJudge.PRESSURE_ABNORMAL]): áp suất BẤT THƯỜNG. Nhánh M1 khi cụm VÀNG/ĐỎ, sau chữ TPMS. */
    ABNORMAL(TyreSeverity.WARN),
    /** Hệ TPMS báo tín hiệu bất thường / hỏng. */ SENSOR(TyreSeverity.WARN),
    /** Cụm báo VÀNG mà TPMS không nói vì sao. */ CAR_WARN(TyreSeverity.WARN),
    /** Mã lạ (không phải sentinel) — bánh vẫn XÁM; chữ cụ thể "mã N" ở [TyreReading.reason]. */ CODE(TyreSeverity.NONE);

    /**
     * Chữ NGẮN nói **SAI CÁI GÌ** (nợ gói 2). `null` khi không có gì để nói (bình thường / chưa phán) ⇒ bộ vẽ không
     * hiện chữ nào, không chiếm chỗ. Với [CODE] đây là chữ CHUNG cho câu kết luận (*"1 bánh mã lạ"*); ô bánh dùng
     * [TyreReading.reason] để in đúng con số.
     */
    val reason: String?
        get() = when (this) {
            BURST -> Strings.t("nổ lốp", "burst")
            ABNORMAL -> Strings.t("bất thường", "abnormal")
            LEAK_FAST -> Strings.t("xì nhanh", "fast leak")
            UNDER -> Strings.t("non", "low")
            OVER -> Strings.t("căng", "high")
            CAR_ALERT -> Strings.t("báo đỏ", "red alert")
            LEAK_SLOW -> Strings.t("xì chậm", "slow leak")
            SENSOR -> Strings.t("lỗi cảm biến", "sensor fault")
            CAR_WARN -> Strings.t("chú ý", "caution")
            CODE -> Strings.t("mã lạ", "unknown code")
            OK, UNKNOWN -> null
        }
}

/**
 * Vị trí bánh — thứ tự cố định để bộ vẽ đặt đúng góc.
 *
 * ⚠ [shortLabel] là **hai chữ viết tắt** (`TT`/`TP`/`ST`/`SP` — trước/sau × trái/phải) và nó nằm trong ô bánh xe của
 * bảng lốp, nên bản tiếng Anh cũng phải hai chữ (`FL`/`FR`/`RL`/`RR` — chuẩn ngành).
 */
enum class TyreCorner(
    override val label: String,
    val shortLabel: String,
    override val labelEn: String,
    val shortLabelEn: String,
) : Localized {
    FRONT_LEFT("Trước trái", "TT", "Front left", "FL"),
    FRONT_RIGHT("Trước phải", "TP", "Front right", "FR"),
    REAR_LEFT("Sau trái", "ST", "Rear left", "RL"),
    REAR_RIGHT("Sau phải", "SP", "Rear right", "RR");

    /** Viết tắt theo [Strings.current] — dùng ở ô bánh của bảng lốp. */
    val displayShortLabel: String get() = Strings.pick(shortLabel, shortLabelEn)
}

/**
 * Một bánh sau khi quyết định.
 * @property pressureKpa số THÔ từ xe (kPa) — `null` = chưa đọc / mã OEM 4092..4095 (đã lọc ở tầng đọc — xem
 *   [TyreJudge.PRESSURE_BURST]; 2.93: mã 4092/4093 thành CHỮ lý do qua [status] khi màu cụm cùng chiều) / số âm. Bộ vẽ tự
 *   đổi đơn vị.
 * @property tempC nhiệt độ (°C) — `null` = chưa đọc (mức bằng chứng chưa kiểm trên xe).
 * @property severity MÀU đã phán (xem [TyreJudgement.severity] — có thể khác màu mặc định của [status] ở nhánh M1).
 */
data class TyreReading(
    val corner: TyreCorner,
    val pressureKpa: Double?,
    val tempC: Int?,
    val status: TyreStatus,
    val severity: TyreSeverity = status.severity,
    val source: TyreSource = TyreSource.NONE,
    val code: Int? = null,
) {
    /** Chữ lý do cho Ô bánh: như [TyreStatus.reason], riêng mã lạ thì in đúng con số (*"mã 7"*) để anh em chụp gửi. */
    val reason: String? get() = code?.takeIf { status == TyreStatus.CODE }?.let { TelemetryEnums.unknown(it) } ?: status.reason
}

/**
 * Quyết định trạng thái 4 bánh từ [CarStatus.Tyres] — **0 số ngưỡng**: màu từng bánh là lời phán của chính xe
 * ([TyreJudge]). Áp suất chỉ được HIỆN.
 */
object TyreBoard {

    /**
     * 4 bánh theo thứ tự [TyreCorner]. Luôn trả về ĐÚNG 4 phần tử — bánh chưa đọc được là [TyreStatus.UNKNOWN]
     * (bộ vẽ hiện "—"), KHÔNG bỏ bớt phần tử để bố cục không bị nhảy.
     */
    fun readings(t: CarStatus.Tyres): List<TyreReading> = listOf(
        reading(TyreCorner.FRONT_LEFT, t.pFlKpa, t.tFlC, t.cFl, t.psFl, t.lkFl, t.sys, t.pcFl),
        reading(TyreCorner.FRONT_RIGHT, t.pFrKpa, t.tFrC, t.cFr, t.psFr, t.lkFr, t.sys, t.pcFr),
        reading(TyreCorner.REAR_LEFT, t.pRlKpa, t.tRlC, t.cRl, t.psRl, t.lkRl, t.sys, t.pcRl),
        reading(TyreCorner.REAR_RIGHT, t.pRrKpa, t.tRrC, t.cRr, t.psRr, t.lkRr, t.sys, t.pcRr),
    )

    private fun reading(corner: TyreCorner, kpa: Double?, temp: Int?, c: Int?, ps: Int?, lk: Int?, sys: Int?, pc: Int?): TyreReading {
        val j = TyreJudge.judge(c, ps, lk, sys, pc)
        // Bốn mã OEM trong dải số (4092..4095 — [TyreJudge.PRESSURE_BURST]) đã thành `null` ở TẦNG ĐỌC
        // (`HalReadTables.INVALID_VALUES`) để mọi bề mặt chung một luật; ở đây chỉ còn lưới hữu hạn/không âm.
        val shown = kpa?.takeIf { it.isFinite() && it >= 0 }
        return TyreReading(corner, shown, temp, j.status, j.severity, j.source, j.code)
    }

    /** Có bánh nào cần cảnh báo không (vàng/đỏ) — cho ô thu nhỏ / chip tóm tắt. */
    fun anyAlert(t: CarStatus.Tyres): Boolean = readings(t).any { it.severity.alert }

    /** Mức nặng NHẤT của 4 bánh — một màu cho icon chip / ô thu nhỏ. Rỗng ⇒ [TyreSeverity.NONE]. */
    fun worst(readings: List<TyreReading>): TyreSeverity = readings.maxOfOrNull { it.severity } ?: TyreSeverity.NONE

    /**
     * KẾT LUẬN TỔNG một dòng — trả lời đúng câu *"lốp tao ổn không"* mà không buộc người xem tự so bốn con số.
     *
     * ## ⚠ Vì sao nó ở `:core` chứ không ghép chuỗi trong ô vẽ
     * Nó là một **phán xét**, không phải cách trình bày: nó đọc [TyreStatus] và đếm. Ở đây thì kết luận và màu ô luôn
     * nói **cùng một điều**, và kiểm được off-car.
     *
     * Bốn ca, theo thứ tự:
     *  1. có bánh có lỗi ⇒ đếm theo TỪNG loại, **nặng trước** (đỏ ▸ vàng ▸ mã lạ), trong cùng mức theo thứ tự khai.
     *     Mức nặng của một loại = mức **ĐÃ PHÁN** nặng nhất ([TyreReading.severity]) trong các bánh mang nó, KHÔNG
     *     phải màu mặc định [TyreStatus.severity]: ở nhánh M1 màu là của cụm còn chữ là của TPMS (cụm VÀNG + TPMS
     *     "non" ⇒ UNDER/WARN), nên xếp theo màu mặc định sẽ đọc bánh vàng trước bánh đỏ (soát 2.88 truth-3);
     *  2. CẢ BỐN bánh xe nói bình thường ⇒ *"lốp ổn"* — chỉ lúc này, không bao giờ suy từ con số;
     *  3. không bánh nào có số và không bánh nào phán được ⇒ *"chưa đọc được áp suất"* (off-car mà báo ổn là **bịa**);
     *  4. còn lại (có số mà xe chưa phán) ⇒ *"chưa đọc được trạng thái lốp từ xe"*.
     */
    fun verdict(readings: List<TyreReading>): String {
        val faults = readings.filter { it.status.reason != null }
            .groupBy { it.status }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<TyreStatus, List<TyreReading>>> { e -> e.value.maxOf { it.severity } }
                    .thenBy { it.key.ordinal },   // cùng mức ⇒ thứ tự khai (nguy trước)
            )
            .map { it.value.size to it.key }
        if (faults.isNotEmpty()) {
            return faults.joinToString(" · ") { (n, st) ->
                // Số ít/nhiều của tiếng Anh = HAI mẫu trọn vẹn (cả hai là khoá bảng dịch), không rẽ nhánh trong đối số.
                // Dạng LIỆT KÊ "{0} wheel: {1}": lý do 2.88 là DANH TỪ ("red alert", "slow leak", "unknown code") nên
                // "1 wheel caution" sai ngữ pháp (soát 2.88 ui-2). Khoá VI giữ nguyên.
                if (n == 1) Strings.f("{0} bánh {1}", "{0} wheel: {1}", n, st.reason)
                else Strings.f("{0} bánh {1}", "{0} wheels: {1}", n, st.reason)
            }
        }
        if (readings.isNotEmpty() && readings.all { it.status == TyreStatus.OK }) return Strings.t("lốp ổn", "tyres OK")
        if (readings.none { it.pressureKpa != null } && readings.all { it.status == TyreStatus.UNKNOWN }) {
            return Strings.t("chưa đọc được áp suất", "pressure not read yet")
        }
        return Strings.t("chưa đọc được trạng thái lốp từ xe", "tyre status not read from the car")
    }

    /**
     * ═══ 2.88 · KÊNH 2 — một dòng log THÔ cho tester đời xe khác (spec §4.4 C3) ═══════════════════════════════
     *
     * `TYRE raw p=<số bánh có số>/4 c=…|ps=…|lk=…|sys=… → <màu 4 bánh> src=<nguồn>` — mã thô theo TT,TP,ST,SP (`-` =
     * chưa đọc), màu `G` xanh · `Y` vàng · `R` đỏ · `N` xám, nguồn = nơi đã phán (`cluster` · `tyre` · `none`).
     * Cố ý KHÔNG in con số áp suất: nó nhảy ±1 kPa mỗi nhịp, mà dòng này chỉ được ghi khi ĐỔI (`TyreRawLog` ở `:app`).
     *
     * `null` = không có MỘT mã nào (kể cả áp suất) ⇒ màn không bày lốp / off-car ⇒ không có gì để ghi.
     */
    fun rawLine(t: CarStatus.Tyres): String? {
        val pressures = listOf(t.pFlKpa, t.pFrKpa, t.pRlKpa, t.pRrKpa)
        val c = listOf(t.cFl, t.cFr, t.cRl, t.cRr)
        val ps = listOf(t.psFl, t.psFr, t.psRl, t.psRr)
        val lk = listOf(t.lkFl, t.lkFr, t.lkRl, t.lkRr)
        if ((pressures + c + ps + lk + t.sys + listOf(t.pcFl, t.pcFr, t.pcRl, t.pcRr)).all { it == null }) return null
        fun codes(xs: List<Int?>) = xs.joinToString(",") { it?.toString() ?: "-" }
        val r = readings(t)
        val colours = r.joinToString(",") {
            when (it.severity) {
                TyreSeverity.OK -> "G"
                TyreSeverity.WARN -> "Y"
                TyreSeverity.ALERT -> "R"
                TyreSeverity.NONE -> "N"
            }
        }
        val src = r.map { it.source }.filter { it != TyreSource.NONE }.distinct()
            .joinToString("+") { it.name.lowercase() }.ifEmpty { "none" }
        // 2.93 TYRE-BURST-REASON — mã báo trong dải áp suất (4092/4093) CHỈ in khi có (dòng cũ giữ nguyên byte khi không có):
        // ảnh chụp/nhật ký từ xe chốt được [SUY] "getter TPMS dùng cùng bộ mã với kênh cụm của launcher gốc".
        val pc = listOf(t.pcFl, t.pcFr, t.pcRl, t.pcRr)
        val tail = if (pc.any { it != null }) " pc=${codes(pc)}" else ""
        return "TYRE raw p=${pressures.count { it != null }}/4 c=${codes(c)}|ps=${codes(ps)}|lk=${codes(lk)}|" +
            "sys=${t.sys ?: "-"} → $colours src=$src$tail"
    }

    /** Mức bằng chứng của phần NHIỆT ĐỘ (chưa kiểm trên xe) ⇒ bộ vẽ gắn dấu "chưa kiểm". */
    val tempTier: EvidenceTier = EvidenceTier.NEEDS_CAR
}
