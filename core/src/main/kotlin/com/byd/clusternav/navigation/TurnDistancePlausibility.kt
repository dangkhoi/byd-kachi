package com.byd.clusternav.navigation

/**
 * B-III — GUARD **HỢP LÝ HOÁ CỰ LY** cho ô cự-ly đọc bằng view-id a11y ([NavViewIdSource]).
 *
 * ── TUYÊN BỐ GIỚI HẠN (đọc TRƯỚC khi tin vào lớp này) ─────────────────────────────────────────────────────
 *
 * > Guard này **KHÔNG giải quyết được "đúng app, sai tuyến"**. Không field nào trong `NavViewIdSource`
 * > (`turnMeters`/`road`/`arrivalClock`/`routeSeconds`/`routeMeters`, xem NavViewIdSource.kt:41-58) mang **danh
 * > tính TUYẾN**. Một tuyến cũ (user mở từ sáng) vẫn phát ra chuỗi mẫu **hoàn toàn hợp lý**: cự ly giảm đều theo
 * > tốc độ, tên đường đổi ở mỗi ngã rẽ. Guard chỉ bắt được **mâu thuẫn nội tại của chuỗi mẫu** (nhảy tăng / đóng
 * > băng / nguồn vừa đổi) — tức là bắt **lúc CHUYỂN TIẾP giữa hai tuyến/hai app**, không bắt được trạng thái ổn
 * > định của tuyến sai. **Giảm xác suất, không loại trừ.** Muốn loại trừ thì phải có định danh tuyến (destination
 * > string / polyline / route-id) — chưa nguồn nào phơi ra ⇒ ghi vào Open Questions, không hứa trong code.
 *
 * Test `KHONG cuu duoc 'dung app sai tuyen'` trong `TurnDistancePlausibilityTest` **khoá đúng tuyên bố này** —
 * nó assert rằng chuỗi mẫu của tuyến sai vẫn được ACCEPT, để không ai đọc code rồi tưởng guard đã cứu ca đó.
 *
 * ── VÌ SAO LÀ LỚP MỚI, KHÔNG TÁI DÙNG [NavArrivalGuard] (CLAUDE.md §6 — không sửa đường đã proven) ─────────
 * [NavArrivalGuard.acceptDistance] đã có luật chống-nhảy-tăng nhưng nó phục vụ **đường notification GMaps đang
 * chạy ngoài hiện trường**, với ngưỡng rất lỏng (`approachMeters=800`, `regressionJumpMeters=1500` —
 * NavArrivalGuard.kt:29-35) và state theo **phiên notification**. Siết ngưỡng của nó = đổi hành vi một đường đã
 * kiểm chứng trên xe thật ⇒ CẤM. Vì vậy trùng lặp nhẹ luật "nhảy tăng" giữa hai lớp là **CỐ Ý** — reviewer đừng
 * gộp lại.
 *
 * ── TÁI DÙNG MÔ HÌNH (không chép công thức) ───────────────────────────────────────────────────────────────
 * Quãng đi giữa hai mẫu tính theo **TỐC-ĐỘ-THẬT**, không theo thời gian trôi — đúng bài học bug α-β
 * (TurnDistanceInterpolator.kt:9-13: "dừng đèn đỏ mà km vẫn giảm rồi nhảy loạn"). Hệ số bảo thủ dùng CHUNG
 * [TurnDistanceInterpolator.FACTOR] (0.95), KHÔNG chép số 0.95 ra file thứ hai. dt bị kẹp như
 * `MAX_EXTRAPOLATE_MS` để một khoảng lặng dài không sinh ra quãng-đi khổng lồ.
 *
 * THUẦN (không Android) → test off-car. Tầng `:app` ([com.byd.clusternav.NavOutputOwner]) chỉ đọc mẫu + tốc độ
 * rồi thi hành phán quyết.
 */
class TurnDistancePlausibility(
    /** (c) Nguồn vừa được chọn phải qua K mẫu HỢP LÝ LIÊN TIẾP mới được lái ô cự-ly. */
    private val warmupSamples: Int = 3,
    /** (a) Dung sai tăng tuyệt đối (m) — nhiễu làm tròn/re-snap. */
    private val riseAbsToleranceM: Int = 30,
    /** (a) Dung sai tăng tương đối (%) — ở tầm km app hiển thị bước 100m nên phải nới theo cự ly. */
    private val riseRelTolerancePct: Int = 10,
    /** (a) Nhảy tăng DAI DẲNG (reroute thật, cùng tên đường) → nhả sau ngần này lần từ chối. */
    private val releaseAfterRejects: Int = 2,
    /** (b) Dưới ngưỡng này coi như xe ĐỨNG (≈5.4 km/h) → cự ly đứng im là HỢP LÝ. */
    private val movingMinMps: Double = 1.5,
    /** (b) Đi quá quãng này mà cự ly không đổi = không tin được nữa (m). */
    private val frozenAbsLimitM: Int = 150,
    private val frozenRelLimitPct: Int = 10,
    /** Hai mẫu cách nhau quá lâu = đứt mạch → coi như nguồn mới (phải warmup lại). */
    private val continuityMs: Long = 4_000L,                            // 4000ms (cũ = NavViewIdSource.FRESH_MS)
    private val speedoFactor: Double = TurnDistanceInterpolator.FACTOR, // 0.95, dùng CHUNG
) {

    /**
     * ⚠ NGƯỠNG LÀ THAM SỐ CHỌN, CHƯA HIỆU CHUẨN bằng log thật (CLAUDE.md §2 — mức "nghi là"). Chúng nằm ở ctor
     * đúng để chỉnh được mà không đụng thuật toán; cách bác bỏ ghi ở §7 Open Questions của spec.
     */
    enum class Verdict {
        /** Mẫu hợp lý và đã qua warmup ⇒ ĐƯỢC lái ô cự-ly. */
        ACCEPT,

        /** Hợp lý nhưng chưa đủ K mẫu liên tiếp ⇒ chưa được lái. */
        WARMUP,

        /** Nhảy TĂNG quá dung sai trong khi cùng một tên đường ⇒ không tin. */
        REJECT_RISE,

        /** Cự ly ĐÓNG BĂNG trong khi xe đã đi được một quãng đáng kể ⇒ không tin. */
        REJECT_FROZEN,

        /** Đọc lại ĐÚNG mẫu cũ (owner tick 4Hz, a11y publish ~1.25Hz) ⇒ trả nguyên phán quyết trước. */
        REPEAT,

        /** Không có mẫu tươi nào ở tick này. */
        NO_SAMPLE,
    }

    /**
     * [meters] = giá trị ĐƯỢC PHÉP ghi ra cụm; [UNKNOWN] = không ghi.
     * [blankDistance] = true ĐÚNG MỘT LẦN ở cạnh xuống (đang hiện số → mất tin) → owner xoá ô cự-ly.
     */
    data class Decision(val verdict: Verdict, val meters: Int, val blankDistance: Boolean)

    // ── State một phiên (per-instance; owner giữ một instance, reset theo vòng đời frame) ──────────────────
    private var sourcePkg: String? = null
    private var lastSampleAt: Long = 0L
    private var lastDecision: Decision? = null
    private var anchorMeters: Int = UNKNOWN
    private var anchorRoad: String = ""
    private var frozenTravelM: Double = 0.0
    private var goodStreak: Int = 0
    private var rejectStreak: Int = 0
    private var displaying: Boolean = false

    /**
     * Xét MỘT mẫu view-id. [sampleAtMs] phải là **mốc publish của chính mẫu** (`NavViewIdSource.Reading.atMs`),
     * KHÔNG phải `now` của tick — nếu dùng `now` thì bước 1 (chống đọc lặp) mù và mọi phép trừ quãng sai.
     *
     * [speedMps] = tốc độ THẬT hoặc `null` khi **không đọc được**. `null` ⇒ luật (b) đóng-băng TẮT hoàn toàn
     * (không đoán bừa). Caller TUYỆT ĐỐI không được thay `null` bằng 0.0: 0.0 nghĩa là "xe đứng", và guard sẽ
     * im lặng mãi mãi mà không ai biết.
     */
    fun accept(pkg: String, meters: Int, road: String, sampleAtMs: Long, speedMps: Double?): Decision =
        accept(pkg, meters, road, sampleAtMs) { speedMps }

    /**
     * Như [accept] nhưng tốc độ đọc **LƯỜI** — chỉ gọi [speedMps] khi thật sự cần tính quãng đi.
     *
     * VÌ SAO (sửa 08-22 vòng 2): ở `:app` lambda này là `SpeedProvider.mpsOrNull()`, tức một lời gọi
     * reflection xuống HAL. Owner tick 4 Hz nhưng a11y chỉ publish ~1,25 Hz, nên phần lớn tick rơi vào bước 1
     * (chống đọc lặp → [Verdict.REPEAT]) và KHÔNG dùng tới tốc độ. Truyền giá trị (`speed()`) thì Kotlin đánh
     * giá tham số TRƯỚC khi vào hàm ⇒ ~3 lời gọi HAL/giây bị đốt vô ích suốt cả chuyến. Chỉ nhánh "đóng băng"
     * (b) mới cần số này.
     */
    @Synchronized
    fun accept(pkg: String, meters: Int, road: String, sampleAtMs: Long, speedMps: () -> Double?): Decision {
        // 1. CHỐNG ĐỌC LẶP — owner tick mỗi HudKeepAlivePolicy.DEFAULT_INTERVAL_MS (250ms) nhưng a11y chỉ publish
        //    mỗi WINDOW_ENUM_THROTTLE_MS (800ms, hằng số trong NavAccessibilityService) ⇒ CÙNG một mẫu bị đọc lại 3-4 lần.
        //    Không chặn thì mẫu cũ tự cộng frozenTravelM và tự thổi warmupSamples ⇒ guard vừa từ chối bậy vừa
        //    "chứng minh" bậy. Trả NGUYÊN meters để keep-alive re-assert đúng số cũ; KHÔNG đổi state.
        //    ⚠ KHÔNG tái dùng cache khi phán quyết gần nhất là [Verdict.NO_SAMPLE] (sửa 08-22 vòng 1):
        //    [noSample] ghi `lastDecision = NO_SAMPLE(UNKNOWN)` mà KHÔNG đụng `lastSampleAt`, nên một nhịp
        //    khung-đổi-chủ thoáng qua sẽ đầu độc cache — mẫu CŨ VẪN CÒN TƯƠI quay lại ở nhịp sau bị trả về
        //    UNKNOWN ⇒ ô cự-ly nằm trắng thêm ~800 ms dù dữ liệu vẫn tốt. Bỏ qua cache thì mẫu đó được xét
        //    lại bình thường (dt = 0 ⇒ travel = 0 ⇒ nhánh "đóng băng" cộng 0 ⇒ ACCEPT lại đúng số cũ).
        val cached = lastDecision
        if (cached != null && cached.verdict != Verdict.NO_SAMPLE &&
            pkg == sourcePkg && sampleAtMs == lastSampleAt
        ) {
            return cached.copy(verdict = Verdict.REPEAT, blankDistance = false)
        }

        // 2. Parse hụt một mẫu ⇒ không có cự ly để hiện, nhưng KHÔNG phá chuỗi warmup đang xây.
        if (meters < 0) return noSample()

        // 3. Đứt mạch / đổi nguồn ⇒ episode MỚI (phải warmup lại). `sampleAtMs < lastSampleAt` = đồng hồ lùi
        //    hoặc process mới → phòng thủ, không tin.
        val newEpisode = pkg != sourcePkg ||
            lastSampleAt == 0L ||
            sampleAtMs < lastSampleAt ||
            sampleAtMs - lastSampleAt > continuityMs

        var verdict: Verdict? = null
        if (newEpisode) {
            sourcePkg = pkg
            reAnchor(meters, road)
            goodStreak = 1
        } else {
            val dtSec = (sampleAtMs - lastSampleAt).coerceIn(0L, continuityMs) / 1000.0
            // Quãng đi tính bằng TỐC-ĐỘ-THẬT (không phải thời gian trôi) — bài học bug α-β. Dưới movingMinMps
            // coi như đứng ⇒ travel = 0 ⇒ cự ly đứng im là HỢP LÝ (dừng đèn đỏ).
            // ĐỌC TỐC ĐỘ Ở ĐÂY, không sớm hơn: đây là chỗ DUY NHẤT cần nó (xem KDoc quá tải lười ở trên).
            val v = speedMps()
            val travel = if (v != null && v >= movingMinMps) v * speedoFactor * dtSec else 0.0
            val key = roadKey(road)
            val riseLimit = maxOf(riseAbsToleranceM, anchorMeters * riseRelTolerancePct / 100)
            val frozenLimit = maxOf(frozenAbsLimitM, anchorMeters * frozenRelLimitPct / 100)
            when {
                // ĐỔI TÊN ĐƯỜNG = maneuver mới ⇒ tăng là HỢP LỆ. KHÔNG bắt warmup lại: bắt thì mỗi ngã rẽ đều
                // mất ô cự-ly, đúng lúc tài xế cần nhất.
                //
                // ⚠ `key.isNotEmpty()` là BẮT BUỘC (sửa 08-22 vòng 1, fail-closed). Nguồn tên đường là
                // `textOf("navBarStreetLine")` (NavAccessibilityService) và nó trả rỗng khi node vắng/blank —
                // hoàn toàn có thể chớp "Quang Trung" → "" → "Quang Trung" giữa hai maneuver. Coi cú chớp đó
                // là "đổi tên đường" thì nhánh này reAnchor VÔ ĐIỀU KIỆN (nhận thẳng một cú nhảy 300 m → 5 km,
                // đồng thời xoá `rejectStreak` + `frozenTravelM`) ⇒ luật (a) chống-nhảy-tăng — luật CHÍNH của
                // guard — bị bypass đúng LÚC CHUYỂN TIẾP, tức lúc duy nhất guard tuyên bố mình bắt được.
                // Rỗng = KHÔNG BIẾT ⇒ giữ nguyên anchorRoad và để luật (a)/(b) chấm như bình thường.
                key.isNotEmpty() && key != anchorRoad -> { reAnchor(meters, road); goodStreak++ }

                // (a) Đang tiến tới điểm rẽ thì cự ly phải GIẢM.
                meters > anchorMeters + riseLimit -> {
                    rejectStreak++
                    if (rejectStreak <= releaseAfterRejects) {
                        goodStreak = 0
                        verdict = Verdict.REJECT_RISE
                    } else {
                        // Nhảy tăng DAI DẲNG = reroute thật (cùng tên đường) → NHẢ, nhưng phải warmup lại.
                        reAnchor(meters, road)
                        goodStreak = 1
                    }
                }

                // (b) Cự ly đứng im trong khi xe đã đi được một quãng ⇒ nguồn không còn cập nhật.
                meters == anchorMeters -> {
                    frozenTravelM += travel
                    if (frozenTravelM > frozenLimit) {
                        // KHÔNG có đường nhả: đóng băng khi đang chạy là SAI, cứ từ chối tới khi số ĐỔI.
                        goodStreak = 0
                        verdict = Verdict.REJECT_FROZEN
                    } else {
                        goodStreak++
                    }
                }

                // Giảm, hoặc tăng TRONG dung sai (nhiễu làm tròn "1.2 km" → "1.3 km") ⇒ hợp lý.
                else -> { reAnchor(meters, road); goodStreak++ }
            }
        }
        return emit(verdict ?: if (goodStreak >= warmupSamples) Verdict.ACCEPT else Verdict.WARMUP, meters, sampleAtMs)
    }

    /**
     * Tick KHÔNG có mẫu tươi nào (nguồn im, hoặc app không phơi view-id — VietMap/GMaps luôn rơi vào đây).
     * KHÔNG phá episode/anchor/streak: việc "mất mạch" để luật timestamp ở [accept] bước 3 xử, vì chỉ nó biết
     * hai mẫu cách nhau bao lâu.
     */
    @Synchronized
    fun noSample(): Decision {
        val blank = displaying
        displaying = false
        return Decision(Verdict.NO_SAMPLE, UNKNOWN, blank).also { lastDecision = it }
    }

    /** Hết phiên (owner nhả frame / dừng) — phiên sau phải chứng minh lại từ đầu. */
    @Synchronized
    fun reset() {
        sourcePkg = null
        lastSampleAt = 0L
        lastDecision = null
        anchorMeters = UNKNOWN
        anchorRoad = ""
        frozenTravelM = 0.0
        goodStreak = 0
        rejectStreak = 0
        displaying = false
    }

    /** Chỉ để log verbose / DiagActivity — KHÔNG dùng để quyết định. */
    @Synchronized
    fun debugState(): String =
        "pkg=$sourcePkg anchor=${anchorMeters}m road='$anchorRoad' good=$goodStreak reject=$rejectStreak " +
            "frozen=${frozenTravelM.toInt()}m displaying=$displaying"

    private fun reAnchor(meters: Int, road: String) {
        anchorMeters = meters
        anchorRoad = roadKey(road)
        frozenTravelM = 0.0
        rejectStreak = 0
    }

    /**
     * Cạnh xuống của [Decision.blankDistance]:
     *  • **VÌ SAO phải xoá chủ động**: `BydHal.pushNavigation` coi `segMeters < 0` là "BỎ GHI, giữ số cũ"
     *    (BydHal.kt:383-398). Nếu warmup chỉ trả -1 thì **số của nguồn CŨ nằm lại trên cụm** — TỆ HƠN hôm nay
     *    (hôm nay đổi nguồn là số nhảy sang giá trị mới ngay).
     *  • **VÌ SAO chỉ khi `displaying`**: guard chỉ được xoá cái CHÍNH NÓ viết ra, không bao giờ giẫm lên ô
     *    cự-ly do đường notification (`BydHal.writeNavFrame`, BydHal.kt:291) ghi.
     */
    private fun emit(verdict: Verdict, meters: Int, sampleAtMs: Long): Decision {
        lastSampleAt = sampleAtMs
        val accepted = verdict == Verdict.ACCEPT
        val blank = !accepted && displaying
        displaying = accepted
        return Decision(verdict, if (accepted) meters else UNKNOWN, blank).also { lastDecision = it }
    }

    private fun roadKey(road: String): String = road.trim().lowercase()

    companion object {
        /** Không có cự ly đáng tin ⇒ owner ghi -1 ⇒ `BydHal.pushNavigation` bỏ ghi ô cự-ly. */
        const val UNKNOWN = -1
    }
}
