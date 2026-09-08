package com.byd.clusternav.navigation

import kotlin.math.ceil
import kotlin.math.max

/**
 * B-II — CHỐNG NHẢY NGUỒN giữa hai nhịp enum cửa sổ.
 *
 * ── VÌ SAO (mức bằng chứng: ĐÃ CHỨNG MINH từ source, chưa đo trên xe — CLAUDE.md §2) ─────────────────────
 * Vòng enum cửa sổ chạy mỗi 800 ms (`NavAccessibilityService.WINDOW_ENUM_THROTTLE_MS`) và bầu nguồn theo
 * DIỆN TÍCH cửa sổ ([com.byd.clusternav.navigation.screencapture.NavWindowPicker.rank] — sortedWith
 * compareByDescending{area}). Ở `PREFER_WAZE`, cổng nguồn `allow` đúng cho CẢ `com.waze` LẪN
 * `com.chisadin.wazemod` (cùng thuộc [NavApps.WAZE]) nên [SourceArbiter] không khoá gì — đảo hạng diện tích
 * là đổi nguồn ngay nhịp sau. Mà [NavViewIdSource] / `CaptureBoundsSource` là holder MỘT-Ô: nguồn mới ghi đè
 * cự ly của nguồn cũ, rồi `NavOutputOwner` chỉ nhận cự ly khi pkg khớp `ScreenCaptureSignal.arrowPkg` ⇒ ô cự
 * ly trên HUD nhấp nháy số → gạch → số. Đó chính là triệu chứng "cự ly nhảy giữa hai app".
 *
 * Hai lớp bảo vệ:
 *  1. DWELL — ứng viên MỚI phải thắng [dwellTicks] nhịp LIÊN TIẾP (mặc định 3 quan sát ⇒ 2 khoảng × 800 ms
 *     = 1,6 s kể từ lần đầu thấy; xem [DWELL_TICKS] — KHÔNG phải 2,4 s) mới tiếp quản.
 *  2. CAM-KẾT-RẼ — khi cự ly TƯƠI của nguồn đang giữ ≤ [commitMeters], CẤM đổi nguồn: tài xế đã vào khúc rẽ,
 *     đổi nguồn lúc đó là thay con số ngay giây quyết định. Lớp này có TRẦN CỨNG [COMMIT_MAX_MS] cho một con
 *     số ĐÓNG BĂNG — không có trần thì một banner đọng trong dải cam-kết khoá vĩnh viễn việc bầu nguồn (xem
 *     khối lập luận tại R5 trong [onTick], và test `R5 - cu ly DONG BANG khong duoc giam nguon vinh vien`).
 *
 * DEGRADE-SAFE: KHÔNG BIẾT thì KHÔNG CHẶN. `holderTurnMeters < 0` (không đọc được cự ly của holder) ⇒ guard
 * cam-kết-rẽ đứng ngoài, chỉ còn dwell. Cấm gate một đường phục hồi bằng dữ liệu mà chỉ chính đường đó mới
 * làm mới được (CLAUDE.md §3 — bài học `sats >= 4`).
 *
 * THUẦN (không Android), đồng hồ tiêm được → test off-car. Trạng thái là PHIÊN (per-instance, không
 * singleton) — cùng khuôn [NavArrivalGuard]; service giữ MỘT thể hiện và gọi [reset] khi (re)connect.
 *
 * ⚠ [SourceArbiter.clear] KHÔNG xoá trạng thái ở đây (hai đối tượng khác nhau). Hệ quả vô hại: nhịp sau
 * holder vẫn được dò trước, và ở AUTO `allows()` trả true vì `activeSource == null` nên holder giữ tiếp.
 */
class NavSourceDwell(
    /**
     * Đồng hồ MONOTONIC. Caller PHẢI truyền `SystemClock.elapsedRealtime`: wall-clock đầu xe NHẢY khi đồng bộ
     * giờ GPS ⇒ chuỗi dwell đứt/nhảy sai — đúng cái bẫy hai-miền-đồng-hồ đã ghi ở `ScreenCaptureNavSource`.
     */
    private val clock: () -> Long,
    /** Chu kỳ nhịp enum THẬT (ms) — caller truyền `WINDOW_ENUM_THROTTLE_MS` để hằng số chỉ nằm MỘT nơi. */
    private val tickPeriodMs: Long = 800L,
    private val dwellTicks: Int = DWELL_TICKS,
    private val commitFloorMeters: Int = COMMIT_FLOOR_M,
    private val commitSeconds: Double = COMMIT_SECONDS,
    /** Holder vắng mặt quá lâu = app đã tắt → nhường ngay. Cùng ngưỡng im-lặng với [SourceArbiter]. */
    private val holderGraceMs: Long = SourceArbiter.STALE_MS,
    /** TRẦN CỨNG của R5 — xem [COMMIT_MAX_MS]. Guard cam-kết-rẽ KHÔNG được giam nguồn vô hạn. */
    private val commitMaxMs: Long = COMMIT_MAX_MS,
) {

    /** Vì sao nhịp này ra kết quả đó — chỉ để LOG/diag, không ai rẽ nhánh theo nó. */
    enum class Reason {
        /** Không có ứng viên nào và cũng chưa có holder. */
        IDLE,

        /** Chưa có holder → nhận ứng viên đầu tiên NGAY (không bắt dwell). */
        BOOTSTRAP,

        /** Holder giữ nguyên. */
        HOLD,

        /** Challenger đang leo chuỗi nhưng CHƯA đủ [dwellTicks] → bị giữ lại. */
        DWELL,

        /** Holder đang trong khoảng cam-kết-rẽ → cấm đổi nguồn. */
        COMMITTED,

        /** Holder không còn qua cổng [NavSourceMode] (user đổi PREFER_*) → nhường ngay. */
        MODE,

        /** Challenger tiếp quản (đủ chuỗi, hoặc holder vắng mặt quá [holderGraceMs]). */
        TAKEOVER,

        /** Không còn ứng viên nào và holder đã im quá [holderGraceMs] → nhả khoá. */
        RELEASED,
    }

    data class Decision(
        /** Nguồn được phép nuôi cụm nhịp này. null = không có gì để nuôi (caller return, KHÔNG publish). */
        val source: String?,
        /** true khi [source] vừa ĐỔI so với nhịp trước (log-on-change). */
        val switched: Boolean,
        /** Ứng viên bị giữ lại (log/diag); null nếu không có ai bị chặn. */
        val blocked: String?,
        val reason: Reason,
        /** Số nhịp liên tiếp challenger đã thắng (log — thấy được nó đang leo 1/3, 2/3). */
        val streak: Int,
        /** Ngưỡng cam-kết-rẽ đã dùng nhịp này (m) — đọc log là biết guard chấm bằng số nào. */
        val commitMeters: Int,
    )

    private var _holder: String? = null
    private var holderSeenAt: Long = 0L
    private var challenger: String? = null
    private var streak: Int = 0
    private var challengerAt: Long = 0L

    /** Mốc bắt đầu chuỗi R5 hiện tại (0 = không đang cam-kết). Xem [COMMIT_MAX_MS]. */
    private var committedSince: Long = 0L

    /** Con số cự ly đang giữ chuỗi R5 — đổi số ⇒ dữ liệu CÒN SỐNG ⇒ làm tươi [committedSince]. */
    private var committedMeters: Int = NO_METERS

    /** Nguồn đang giữ (đọc-chỉ, cho [probeOrder] + log). null = chưa có. */
    val holder: String? get() = _holder

    /**
     * Thứ tự DÒ: holder TRƯỚC (nếu còn trong danh sách), phần còn lại GIỮ NGUYÊN hạng của `NavWindowPicker`.
     *
     * VÌ SAO holder trước: (a) bằng chứng "đang dẫn" (đọc được dữ liệu) mạnh hơn phỏng đoán "cửa sổ to hơn";
     * (b) dò holder trước là cách DUY NHẤT lấy được cự ly TƯƠI của holder cho guard cam-kết-rẽ — đảo thứ tự
     * này là guard mù.
     *
     * ── KHỬ TRÙNG PKG (sửa 08-23 vòng 2b) ────────────────────────────────────────────────────────────────
     * Caller truyền `ranked.map { it.pkg }`, mà `NavWindowPicker.rank` trả **một mục cho mỗi CỬA SỔ**:
     * `.distinct()` của nó chạy trên `Pick(pkg, bounds, displayId)` nên chỉ gộp được đúng cặp trùng do
     * `getWindows()`/`getWindowsOnAllDisplays()` trả chồng nhau — KHÔNG gộp cùng một app có cửa sổ trên
     * **hai display** (display 0 + display 1), tức đúng trạng thái ĐANG CHIẾU của app này. Đo thật (probe
     * 08-23): `probeOrder(["com.waze","vn.vietmap.live","com.waze"])` → `[vietmap, waze, waze]`.
     *
     * Hệ quả nếu để nguyên: vòng `for (pkg in order)` của `resolveNavWindowRegardlessOfFocus` chỉ `break` khi
     * ĐỌC ĐƯỢC; một pkg đọc KHÔNG được sẽ bị dò lại nguyên lần nữa — mà mỗi lượt `probeRanked` đã tự quét mọi
     * cửa sổ của pkg đó rồi. Với app đi đường content-desc (VietMap: đi cây tới `MAX_VISIT_NODES` node, mỗi
     * `getChild` là một lượt binder) thì đó là **gấp đôi** ngân sách đi cây mỗi nhịp 800 ms, trên luồng
     * callback a11y (main) của đầu xe — thứ mà trần `MAX_VISIT_NODES` vừa được thêm để chặn.
     *
     * Giữ LẦN GẶP ĐẦU (thứ hạng diện tích cao nhất của pkg đó) — không đổi thứ tự ưu tiên đã đo.
     */
    fun probeOrder(ranked: List<String>): List<String> {
        val h = _holder
        if (h == null || h !in ranked) return ranked.distinct()
        return ArrayList<String>(ranked.size).apply {
            add(h)
            ranked.forEach { if (it != h && it !in this) add(it) }
        }
    }

    /**
     * Một nhịp enum.
     *
     * @param candidate ứng viên thắng nhịp này (ai ĐỌC ĐƯỢC dữ liệu; không ai đọc được thì caller lấy hạng
     *   nhất còn qua cổng nguồn — giữ nguyên lập luận 08-22 trong `resolveNavWindowRegardlessOfFocus`).
     *   null = không có ứng viên nào.
     * @param holderAllowed holder còn qua cổng [NavSourceMode] không (user đổi PREFER_* ⇒ nhường NGAY).
     * @param holderPresent holder có CÒN CỬA SỔ trong danh sách ứng viên nhịp này không.
     *
     *   VÌ SAO là tham số RIÊNG chứ không suy từ [candidate] (lỗi thật, sửa 08-22): `holderSeenAt` trước đây
     *   chỉ được làm tươi ở R2 (`candidate == prev`) và trong [adopt] ⇒ nó đo "lần cuối holder THẮNG BẦU CỬ",
     *   KHÔNG phải "lần cuối còn THẤY holder". Ca hỏng: đang dẫn bằng holder X, một app Y đọc được view-id
     *   thắng bầu cử mỗi nhịp ⇒ `holderSeenAt` ĐÓNG BĂNG ⇒ sau `holderGraceMs` (6 s) R4 `TAKEOVER` dù cửa sổ
     *   X vẫn hiện và X vẫn đang dẫn — và vì R4 đứng TRƯỚC R5, nó vượt mặt luôn guard cam-kết-rẽ: đổi nguồn
     *   đúng giây tài xế vào cua. Với tham số này, R4 chỉ còn bắn khi holder THẬT SỰ mất cửa sổ > 6 s, đúng
     *   như KDoc của nó tuyên bố.
     *
     *   MẶC ĐỊNH `false` là DEGRADE-SAFE: caller không đo được sự hiện diện ⇒ KHÔNG kéo dài quyền giữ của
     *   holder (R4 vẫn có đường nhả). Không bao giờ giam nguồn bằng dữ liệu mà caller không có.
     * @param holderTurnMeters cự ly tới rẽ TƯƠI của holder (m). [NO_METERS] = KHÔNG BIẾT ⇒ guard KHÔNG chặn.
     * @param closingRateMps tốc-độ-tiệm-cận ([TurnDistanceInterpolator.closingRate]); ≤ 0 ⇒ dùng sàn.
     */
    fun onTick(
        candidate: String?,
        holderAllowed: Boolean,
        holderPresent: Boolean = false,
        holderTurnMeters: Int = NO_METERS,
        closingRateMps: Double = 0.0,
    ): Decision {
        val now = clock()
        val commit = commitMeters(closingRateMps, commitFloorMeters, commitSeconds)
        val prev = _holder
        // Còn thấy cửa sổ holder ⇒ mốc "còn thấy" phải tươi, BẤT KỂ ai thắng bầu cử nhịp này. Đặt ở đây (trước
        // mọi luật) để R0/R4 — hai nơi duy nhất đọc `holderSeenAt` — cùng thấy một sự thật.
        if (holderPresent && prev != null) holderSeenAt = now

        // R0 — không có cửa sổ nav nào ⇒ KHÔNG bầu bừa ai. Nhưng phải có đường NHẢ: holder im quá grace thì
        // buông, không giữ vĩnh viễn một app đã tắt.
        if (candidate == null) {
            challenger = null; streak = 0
            val h = prev ?: return Decision(null, false, null, Reason.IDLE, 0, commit)
            if (now - holderSeenAt > holderGraceMs) {
                _holder = null; holderSeenAt = 0L
                return Decision(null, true, null, Reason.RELEASED, 0, commit)
            }
            return Decision(h, false, null, Reason.HOLD, 0, commit)
        }

        // R1 — chưa có holder: nhận NGAY. Bắt dwell ở đây = cụm TRẮNG 2,4 s mở đầu mỗi chuyến.
        if (prev == null) return adopt(candidate, now, Reason.BOOTSTRAP, commit)

        // R2 — holder còn dẫn ⇒ chuỗi của challenger PHẢI đứt (yêu cầu "LIÊN TIẾP").
        if (candidate == prev) {
            holderSeenAt = now
            challenger = null; streak = 0
            return Decision(prev, false, null, Reason.HOLD, 0, commit)
        }

        // R3 — lựa chọn PREFER_* TƯỜNG MINH của user > mọi lớp làm trễ. Dwell không được giam nguồn user chọn.
        if (!holderAllowed) return adopt(candidate, now, Reason.MODE, commit)

        // R4 — TRẦN CỨNG: holder đã tắt / mất cửa sổ thì không guard nào được giữ nữa. Đứng TRƯỚC R5 có chủ ý:
        // app nav bị kill lúc còn 100 m tới rẽ không được phép giam nguồn mãi mãi.
        if (now - holderSeenAt > holderGraceMs) return adopt(candidate, now, Reason.TAKEOVER, commit)

        // R5 — CAM KẾT RẼ. Reset streak có chủ đích: nếu vẫn đếm trong lúc chặn thì vừa qua rẽ là đổi nguồn
        // TỨC THÌ, đúng lúc lệnh rẽ kế xuất hiện — cú nhảy nguy hiểm nhất.
        //
        // ⚠ TRẦN CỨNG [commitMaxMs] (sửa 08-23 vòng 2 — KHOÁ CHẾT có thật, suy ra tất định từ source):
        // `holderTurnMeters` được làm tươi bởi CHÍNH holder mỗi nhịp (`publishViewIdReading` chỉ chạy cho
        // nguồn ĐÃ ĐƯỢC BẦU = holder khi R5 bắn), còn `holderSeenAt` được làm tươi bởi `holderPresent` —
        // mà `NavWindowPicker.rank` KHÔNG lọc cửa sổ nền. Nên một app để nền với banner ĐỨNG IM trong dải
        // cam-kết (vd VietMap do [VietMapAutostart] tự bật lúc boot, banner đọng ở "0m Lý Thường Kiệt" —
        // 0 là giá trị HỢP LỆ theo `VietMapDescParser.Reading.turnMeters`) sẽ: R4 không bao giờ bắn (còn
        // cửa sổ), R3 không bắn (AUTO), R5 bắn mãi ⇒ app đang dẫn THẬT không bao giờ lên được cụm, không có
        // lối thoát nào ngoài kill app. Đúng bẫy CLAUDE.md §3: guard tự nuôi dữ liệu giam chính nó.
        //
        // Trần này KHÔNG rút ngắn một khúc rẽ THẬT: mỗi lần con số ĐỔI (đang đếm ngược) mốc được đặt lại, nên
        // chỉ một con số ĐÓNG BĂNG mới tiêu hết [commitMaxMs]. Hết trần thì rơi xuống R6 (dwell) chứ KHÔNG
        // nhường ngay — challenger vẫn phải thắng đủ [dwellTicks] nhịp liên tiếp.
        if (holderTurnMeters in 0..commit) {
            if (committedSince == 0L || holderTurnMeters != committedMeters) {
                committedSince = now
                committedMeters = holderTurnMeters
            }
            if (now - committedSince <= commitMaxMs) {
                challenger = null; streak = 0
                return Decision(prev, false, candidate, Reason.COMMITTED, 0, commit)
            }
        } else {
            committedSince = 0L
            committedMeters = NO_METERS
        }

        // R6 — DWELL. Chuỗi đứt khi ĐỔI ứng viên HOẶC khi hai nhịp cách nhau > 2 chu kỳ (3 nhịp rải rác trong
        // một phút KHÔNG phải dwell — nav app bắn event thưa vẫn phải chứng minh mình ổn định theo THỜI GIAN).
        if (candidate != challenger || now - challengerAt > 2 * tickPeriodMs) streak = 0
        challenger = candidate
        challengerAt = now
        streak++
        if (streak < dwellTicks) return Decision(prev, false, candidate, Reason.DWELL, streak, commit)
        val won = streak
        return adopt(candidate, now, Reason.TAKEOVER, commit).copy(streak = won)
    }

    /** Bắt đầu phiên sạch (service (re)connect). */
    fun reset() {
        _holder = null
        holderSeenAt = 0L
        challenger = null
        streak = 0
        challengerAt = 0L
        committedSince = 0L
        committedMeters = NO_METERS
    }

    private fun adopt(pkg: String, now: Long, reason: Reason, commit: Int): Decision {
        val switched = pkg != _holder
        _holder = pkg
        holderSeenAt = now
        challenger = null
        streak = 0
        challengerAt = 0L
        // Nguồn mới = chuỗi cam-kết của nguồn cũ hết nghĩa. Không mang mốc cũ sang, kẻo holder mới bị tính
        // là "đã cam-kết từ lâu" rồi mất guard ngay nhịp đầu.
        committedSince = 0L
        committedMeters = NO_METERS
        return Decision(pkg, switched, null, reason, 0, commit)
    }

    companion object {
        /** "KHÔNG BIẾT cự ly" — guard cam-kết-rẽ đứng ngoài (degrade-safe), KHÔNG phải "0 m". */
        const val NO_METERS = -1

        /**
         * 3 **quan sát** liên tiếp. `streak` tăng NGAY tại quan sát đầu (streak=1), nên 3 quan sát chỉ trải
         * **2 khoảng** ⇒ **1,6 s** kể từ lần đầu thấy challenger — KHÔNG phải 2,4 s như bản KDoc 08-22 ghi
         * (off-by-one, sửa 08-22 vòng 1; số 2,4 s cũng bị in ra ở đây làm lý do chọn 3, tức lý do tự mâu thuẫn).
         *
         * VÌ SAO GIỮ 3 chứ không nâng lên 4 để "đúng 2,4 s": đây là THAM SỐ CHỌN, chưa có một phép đo nào trên
         * xe cho biết 1,6 s hay 2,4 s là đúng (CLAUDE.md §2 — không thăng phỏng đoán lên "đã chứng minh").
         * Cái nó phải chặn là **transient một nhịp** (kéo thanh thông báo / bàn phím / overlay đổi cỡ làm đảo
         * hạng diện tích đúng một mẫu), và 3 quan sát liên tiếp đã chặn được ca đó. Chỉnh được qua ctor
         * (`dwellTicks`) khi có log thật — xem OQ trong spec.
         *
         * Giá phải trả khi tài xế đổi app THẬT: ≤ 1,6 s vẫn hiện nguồn cũ — KHÔNG BAO GIỜ trắng, vì mỗi nhịp
         * holder vẫn được publish lại.
         */
        const val DWELL_TICKS = 3

        /**
         * Sàn ngưỡng cam-kết-rẽ (m). 150 m ÷ 8 s = 18,75 m/s = 67,5 km/h ⇒ một con số tĩnh phủ trọn dải đô
         * thị/ven đô (50 km/h → 150 m ≈ 10,8 s; 30 km/h ≈ 18 s). Đối chiếu trong repo (mức NHIỀU KHẢ NĂNG):
         * `NavParse.quantizeDisplay` đã hạ bước hiển thị xuống 10 m khi < 300 m (code coi < 300 m là vùng tiếp
         * cận), và `TurnDistanceInterpolator.ARRIVED_M = 100` là vùng "đã tới/qua rẽ" ⇒ 150 m đóng guard TRƯỚC
         * khi logic tới-nơi bắt đầu. Mức bằng chứng: THAM SỐ CHỌN.
         */
        const val COMMIT_FLOOR_M = 150

        /** Cửa sổ "đã cam kết": liếc gương → xi-nhan → giảm tốc → vào đúng làn. THAM SỐ CHỌN, chưa đo. */
        const val COMMIT_SECONDS = 8.0

        /**
         * TRẦN CỨNG của R5 (ms) — thời gian tối đa một cự ly **ĐÓNG BĂNG** được phép giam nguồn.
         *
         * VÌ SAO PHẢI CÓ: xem khối lập luận tại R5 trong [onTick] — không có trần thì một banner đứng im
         * trong dải cam-kết khoá vĩnh viễn việc bầu nguồn (R3/R4 đều không với tới).
         *
         * VÌ SAO 30 s là AN TOÀN cho khúc rẽ thật (không phải "đủ dài cho một đèn đỏ"): mốc được đặt lại mỗi
         * lần con số ĐỔI, và khi CHỈ CÓ MỘT app dẫn thì R2 (`candidate == prev`) bắn trước — R5 không tham
         * gia. R5 chỉ vào cuộc khi một app KHÁC đang tranh nguồn; giam nó 30 s rồi bắt nó thắng thêm 3 nhịp
         * dwell là đủ chặt. THAM SỐ CHỌN, chưa đo trên xe (CLAUDE.md §2).
         */
        const val COMMIT_MAX_MS = 30_000L

        /**
         * Ngưỡng cam-kết-rẽ (m) = max(sàn, tốc-độ-tiệm-cận × [seconds]). Không biết tốc độ (≤ 0 / NaN / ∞)
         * → SÀN, không phải 0: "không đọc được ≠ đứng yên" (bài học [SpeedProvider] đã ghi ở :app).
         *
         * TÁI DÙNG mô hình tốc độ có sẵn: caller truyền [TurnDistanceInterpolator.closingRate] (= tốc-độ-đồng-hồ
         * × FACTOR 0,95, đã khử over-read). KHÔNG tự tính lại tốc độ, và KHÔNG gọi HAL trong nhịp a11y.
         */
        fun commitMeters(
            closingRateMps: Double,
            floorMeters: Int = COMMIT_FLOOR_M,
            seconds: Double = COMMIT_SECONDS,
        ): Int {
            if (!closingRateMps.isFinite() || closingRateMps <= 0.0) return floorMeters
            return max(floorMeters, ceil(closingRateMps * seconds).toInt())
        }
    }
}
