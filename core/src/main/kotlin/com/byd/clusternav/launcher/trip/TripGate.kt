package com.byd.clusternav.launcher.trip

import com.byd.clusternav.modules.navaccess.AccessibilityHealGates

/**
 * ═══ F2/F3 — CHUYẾN LÊN XE: đúng MỘT lượt mỗi lần nổ máy thật (thuần, `:core`) ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R2.2 · R2.3 · §4.5 (C5). Bên thi hành ở `:app`
 * (`TripStart`, móc ở dòng CUỐI `EarlyShellChannel.readyChain` — kênh UP + màn tương tác + kiểm phím xong).
 *
 * ## "Chuyến" là gì — đọc từ SỰ THẬT BỀN, không từ cờ RAM (CLAUDE.md §5)
 * [ĐO xe 29/09] BYD giết Kachi mỗi lần TẮT MÁY và Android dựng lại HOME lúc màn ĐÃ tắt; lớp 1 của 2.83 claim mốc
 * `a11y_tat_may_elapsed` (`commit()` TRƯỚC mọi lượt chữa, không phụ thuộc công tắc phím — `A11yLifecycleHeal.kt`).
 * ⇒ lần màn tương tác đầu tiên SAU một claim mới = một lần nổ máy. Id chuyến = [tripId]:
 *  - `<máy>.t<claim>` khi claim thuộc lần khởi động máy này ([AccessibilityHealGates.escalatedThisBoot]);
 *  - `<máy>.b` khi chưa có claim nào trong lần khởi động này (chuyến đầu sau một lần khởi động lại THẬT).
 *
 * `<máy>` = [bootKey]: `n<Settings.Global.BOOT_COUNT>` — [ĐO nguồn] tăng một lần mỗi lần khởi động ở
 * `PowerManagerService.onBootPhase(PHASE_THIRD_PARTY_APPS_CAN_START)` (A10 r47 `PowerManagerService.java:806-807`,
 * `:3862-3873`; A12 r34 `:1106-1107`, `:4695`), TRƯỚC khi app bên thứ ba nào chạy; API công khai (`api10.txt:38730`,
 * `api12.txt:35255`); [ĐO máy ảo 02/10] `settings get global boot_count` = 39. Vì sao cần nó: mốc `elapsedRealtime` về 0
 * khi khởi động lại, nên một claim của đời máy trước có thể rơi vào khoảng `0..now` và trông như "của lần này" — ghép
 * `<máy>` vào id thì chuyến đầu của đời máy mới không bao giờ trùng sổ của đời trước. Không đọc được ⇒ lùi về phút bật
 * máy theo giờ tường ([SUY] giờ tường có thể bị chỉnh giữa phiên — [ĐO xe 14/09] nhảy > 5 s — nên chỉ là đường lùi).
 *
 * Không dùng `boot_id` làm id chuyến: tắt máy BYD KHÔNG khởi động lại (`oncar-2026-09-29-findings.md:252`).
 *
 * ## Sổ chuyến hai pha (R2.2)
 * `CLAIMED` ghi `commit()` TRƯỚC lệnh đầu; `FIRED` khi xong. Tiến trình mới (BYD giết giữa chuyến, hoặc lượt chữa phím
 * force-stop Kachi trong ân hạn khởi động) thấy `CLAIMED` của ĐÚNG chuyến này ⇒ chạy tiếp, tối đa [MAX_TRIES] lần tổng.
 * Mọi bước của chuyến tự đo trước khi làm (app đang chạy ⇒ 0 lệnh; nhạc đang phát ⇒ không đè; HOME không ở trước ⇒
 * không mở) nên lượt thứ hai không làm lại việc lượt đầu đã xong.
 */
object TripGate {

    /** Hạn chuyến (R2.3, OQ3): quá chừng này từ lần THỨC đầu tiên của tiến trình ⇒ bỏ cả chuyến (nhạc không bật giữa đường). */
    const val TRIP_DEADLINE_MS = 180_000L

    /** Tổng số lượt (lượt đầu + một lượt tiếp ở tiến trình mới). */
    const val MAX_TRIES = 2

    /** R2.3(a) — chưa thấy BOOT_COMPLETED của lần khởi động này thì chờ ít nhất chừng này từ lúc thức. */
    const val BOOT_WAIT_MS = 20_000L

    /** Ba khoá THEO XE của chuyến (+ bài phát tiếp [YoutubeResume.KEY] trong [DEVICE_KEYS]) (tệp `clusternav_state`, cùng chỗ `kachi_behind_marks`). Đổi tên = mất sổ của máy đang chạy. */
    const val KEY_LEDGER = "kachi_trip_ledger"
    const val KEY_LAST = "kachi_trip_last"
    const val KEY_BOOT_SEEN = "kachi_boot_seen"

    /**
     * Khoá → lý do. [com.byd.clusternav.launcher.ProfileScope.DEVICE_KEYS] và
     * [com.byd.clusternav.launcher.SettingsCatalog.NOT_SETTINGS] CỘNG bảng này (khai tại chỗ chủ của nó, cùng khuôn
     * `ProfileScopeCluster.DEVICE_KEYS`; `ProfileScope.kt` sát trần 500 dòng). Bảng này không đọc hai đối tượng kia.
     */
    val DEVICE_KEYS: Map<String, String> = mapOf(
        KEY_LEDGER to "trạng thái máy, không phải cấu hình — sổ chuyến lên xe hai pha (CLAIMED/FIRED, `TripGate`): đúng " +
            "một lượt mỗi lần nổ máy CỦA CHIẾC XE NÀY. Theo hồ sơ thì đổi hồ sơ giữa chuyến là chạy lại; sửa tay là app " +
            "tự mở hai lần hoặc không bao giờ",
        KEY_LAST to "trạng thái máy — kết quả chuyến gần nhất (Cài đặt + màn Chẩn đoán đọc, CLAUDE.md §11); không phải " +
            "một lựa chọn",
        KEY_BOOT_SEEN to "trạng thái máy — khoá lần khởi động mà BOOT_COMPLETED đã tới Kachi (R2.3a); thuộc phần cứng " +
            "đang chạy, không thuộc người lái",
        YoutubeResume.KEY to "2.94 R3 — bài YouTube đang phát + vị trí (để lên xe phát tiếp): sự thật của app YouTube trên chiếc " +
            "xe này, không phải lựa chọn; theo hồ sơ thì đổi hồ sơ giữa hai chuyến là mất bài, và tiêu đề người dùng xem sẽ đi " +
            "theo bản xuất hồ sơ (KDoc `YoutubeResume`)",
    )

    enum class Phase { CLAIMED, FIRED }

    data class Ledger(val trip: String, val phase: Phase, val tries: Int)

    /** Vì sao một lượt KHÔNG chạy — mã ASCII cho dòng log `KachiTrip`. */
    enum class Skip { ALREADY_FIRED, NO_WAKE }

    sealed interface Decision {
        /** Chạy: ghi [claim] (`commit()`) TRƯỚC mọi việc. */
        data class Run(val claim: Ledger) : Decision

        /** Không làm gì, không ghi gì. */
        data class Done(val why: Skip) : Decision

        /** Đóng chuyến không chạy: ghi [fired] + kết quả [code] (hết hạn / đã thử đủ lượt). */
        data class Close(val fired: Ledger, val code: Code) : Decision
    }

    /**
     * Kết quả chuyến hiện ở Cài đặt + màn Chẩn đoán (R2.7). L4 · D1: [RAN] = MỌI bước đạt; [NOOP] = có việc mà không bước nào
     * làm được gì (trước L4 cũng ghi `RAN` ⇒ *"đã chạy lúc HH:mm"* cho một chuyến 0 lệnh); [PARTIAL] = lẫn. Suy bằng
     * [TripOutcome.tripCode] — không bên thi hành nào tự chọn `RAN`.
     */
    enum class Code { RAN, NOTHING, EXPIRED, GAVE_UP, NOOP, PARTIAL }

    /** Khoá lần khởi động máy — xem KDoc lớp. [bootCount] `null`/âm = không đọc được. */
    fun bootKey(bootCount: Int?, wallNow: Long, elapsedNow: Long): String =
        if (bootCount != null && bootCount >= 0) "n$bootCount" else "w${(wallNow - elapsedNow) / 60_000L}"

    /**
     * Khoá [key] ỔN ĐỊNH suốt một lần khởi động: dạng `BOOT_COUNT` (`n…`). Khoá lùi theo giờ tường (`w…`) có thể đổi giữa phiên
     * (giờ tường bị chỉnh — KDoc lớp) ⇒ không đủ để kết luận "khác lần khởi động" (2.93 · BEHIND-MARKS-BOOT, `BehindMarks.forBoot`).
     */
    fun stableBoot(key: String): Boolean = key.length > 1 && key[0] == 'n' && key.substring(1).all { it.isDigit() }

    /** Id chuyến — xem KDoc lớp. [tatMayAt] = `Prefs.a11yTatMayAt` (`< 0` = chưa từng). */
    fun tripId(bootKey: String, tatMayAt: Long, elapsedNow: Long): String =
        if (AccessibilityHealGates.escalatedThisBoot(tatMayAt, elapsedNow)) "$bootKey.t$tatMayAt" else "$bootKey.b"

    /**
     * Có chạy chuyến [trip] không, theo sổ [ledger] (đọc từ đĩa) và mốc thức đầu tiên [firstWakeAt] của tiến trình.
     *
     * Thứ tự: sổ đã FIRED chuyến này ⇒ không gì; chưa có mốc thức ⇒ không gì (chuỗi SẴN chỉ chạy sau một lần thức, nên
     * nhánh này chỉ bảo vệ khỏi gọi nhầm); đã thử đủ lượt ⇒ đóng `GAVE_UP`; quá hạn ⇒ đóng `EXPIRED`; còn lại ⇒ claim.
     */
    fun decide(ledger: Ledger?, trip: String, firstWakeAt: Long, now: Long): Decision {
        val same = ledger?.trip == trip
        if (same && ledger?.phase == Phase.FIRED) return Decision.Done(Skip.ALREADY_FIRED)
        if (firstWakeAt < 0 || firstWakeAt > now) return Decision.Done(Skip.NO_WAKE)
        val tries = (if (same) ledger?.tries ?: 0 else 0) + 1
        if (tries > MAX_TRIES) return Decision.Close(Ledger(trip, Phase.FIRED, tries - 1), Code.GAVE_UP)
        if (now - firstWakeAt > TRIP_DEADLINE_MS) return Decision.Close(Ledger(trip, Phase.FIRED, tries), Code.EXPIRED)
        return Decision.Run(Ledger(trip, Phase.CLAIMED, tries))
    }

    /**
     * L4 · D1 — kết quả đang hiện ở Cài đặt có phải của LẦN NỔ MÁY NÀY không. [ĐO máy ảo 03/10 `e2e-L4 · e12-channel-down` (bằng chứng phiên, ngoài repo)]
     * kênh không lên (`PORT_CLOSED`) ⇒ chuỗi SẴN không chạy ⇒ chuyến không chạy, sổ không đổi ⇒ Cài đặt vẫn hiện kết quả chuyến
     * TRƯỚC như thể là của lần này. [current] = [tripId] của lúc hỏi.
     */
    enum class Now { SHOWN, RUNNING, NOT_RUN }

    fun now(current: String, ledger: Ledger?, last: Result?): Now = when {
        last?.trip == current -> Now.SHOWN
        ledger?.trip == current && ledger.phase == Phase.CLAIMED -> Now.RUNNING
        ledger?.trip == current -> Now.SHOWN      // FIRED mà ghi kết quả hỏng — không nói "chưa chạy" sai
        else -> Now.NOT_RUN
    }

    /** Còn trong hạn chuyến không — hỏi lại ở mỗi nhịp chờ (R2.3). */
    fun withinDeadline(firstWakeAt: Long, now: Long): Boolean =
        firstWakeAt in 0..now && now - firstWakeAt <= TRIP_DEADLINE_MS

    /** R2.3(a) — BOOT_COMPLETED của lần khởi động NÀY đã tới ([seenKey] = khoá máy ghi lúc nhận), hoặc đã đủ lâu từ lúc thức. */
    fun bootReady(seenKey: String?, bootKey: String, firstWakeAt: Long, now: Long): Boolean =
        seenKey == bootKey || (firstWakeAt in 0..now && now - firstWakeAt >= BOOT_WAIT_MS)

    // ── Mã hoá sổ: `v=1;trip=<id>;phase=CLAIMED;tries=1` ──────────────────────────────────────────────────────────

    fun encode(l: Ledger): String = "v=1;trip=${l.trip};phase=${l.phase};tries=${l.tries}"

    /** Dễ dãi: chuỗi hỏng / thiếu trường ⇒ `null` (= chưa có chuyến nào), không ném. */
    fun decode(raw: String?): Ledger? {
        val f = fields(raw) ?: return null
        val trip = f["trip"]?.takeIf { it.matches(ID) } ?: return null
        val phase = Phase.values().firstOrNull { it.name == f["phase"] } ?: return null
        val tries = f["tries"]?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return Ledger(trip, phase, tries)
    }

    /**
     * Kết quả chuyến gần nhất ([Code] + giờ tường + một dòng ASCII ngắn cho màn Chẩn đoán + L4 · D1: MÃ từng bước [steps]
     * — trường `s=` riêng, không cắt, Cài đặt dịch từng mã thành câu). Bản ghi cũ (không `s=`) ⇒ [steps] rỗng.
     */
    /**
     * A2 (4) · 2.89 — [wait] = thứ chuyến chờ CUỐI CÙNG trước khi hết hạn (Cài đặt nói *"Hết hạn chờ màn nhà đứng yên…"* thay
     * cho câu chung). Trường `w=` riêng; bản ghi cũ (không `w=`) ⇒ `null` ⇒ câu chung như trước.
     */
    data class Result(
        val trip: String,
        val code: Code,
        val atWall: Long,
        val detail: String,
        val steps: List<TripStep> = emptyList(),
        val wait: TripWaitMark? = null,
    )

    fun encodeResult(r: Result): String {
        val s = TripOutcome.encode(r.steps)
        return "v=1;trip=${r.trip};code=${r.code};at=${r.atWall};d=${r.detail.filter { it in SAFE_DETAIL }.take(DETAIL_MAX)}" +
            (if (s.isEmpty()) "" else ";s=$s") + (r.wait?.let { ";w=${TripWaitMark.encode(it)}" } ?: "")
    }

    fun decodeResult(raw: String?): Result? {
        val f = fields(raw) ?: return null
        val trip = f["trip"]?.takeIf { it.matches(ID) } ?: return null
        val code = Code.values().firstOrNull { it.name == f["code"] } ?: return null
        return Result(trip, code, f["at"]?.toLongOrNull() ?: 0L, f["d"].orEmpty(), TripOutcome.decode(f["s"]), TripWaitMark.decode(f["w"]))
    }

    private fun fields(raw: String?): Map<String, String>? {
        if (raw.isNullOrBlank()) return null
        val f = raw.split(';').mapNotNull { kv -> kv.indexOf('=').takeIf { it > 0 }?.let { kv.substring(0, it) to kv.substring(it + 1) } }.toMap()
        return f.takeIf { it["v"] == "1" }
    }

    /** Id chỉ gồm chữ/số/`.`/`-` (đúng thứ [tripId] sinh ra) — chuỗi sổ không bao giờ mang `;`/`=` lạc chỗ. */
    private val ID = Regex("[A-Za-z0-9.\\-]{1,64}")
    private const val DETAIL_MAX = 160
    private val SAFE_DETAIL: Set<Char> = (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf(' ', '.', ',', '_', '-', ':', '/', '+', '(', ')', '|')).toSet()
}

/**
 * ═══ A2 (4) · 2.89 — chuyến ĐANG / ĐÃ chờ GÌ: một mã bền, Cài đặt gọi tên được (thuần) ═════════════════════════════════
 *
 * Owner 05/10 trên xe 2.88 thấy Cài đặt ghi *"hết hạn chờ – chuyến này không mở app"* mà không biết chuyến chờ cái gì
 * (chỉ màn Chẩn đoán có `d=expired waiting …`). [wait] = cổng [TripPlan.waitFor] đang chặn; với [TripPlan.Wait.SLOT_APP]
 * thêm [pkg] (app nhạc) + [slot] (ô 0-based của bố cục đang hiện) ⇒ *"chờ YouTube ở ô 1"*.
 *
 * Mã hoá (trường `w=` của sổ kết quả + hiển thị lúc đang chạy): `WAIT` hoặc `SLOT_APP:<pkg>:<slot>` — không `;`/`=` (dấu ngăn
 * của sổ), gói qua [com.byd.clusternav.launcher.ShellAppLauncher.PKG]. Đọc dễ dãi: chuỗi lạ ⇒ `null`, không ném.
 */
data class TripWaitMark(val wait: TripPlan.Wait, val pkg: String = "", val slot: Int = -1) {
    companion object {
        /** Ô hợp lệ của một mốc [TripPlan.Wait.SLOT_APP] (bố cục có tối đa 6 ô; dư chỗ để bản sau thêm ô không vỡ sổ). */
        private const val SLOT_MAX = 15

        fun encode(m: TripWaitMark): String =
            if (m.wait == TripPlan.Wait.SLOT_APP) "${m.wait.name}:${m.pkg}:${m.slot}" else m.wait.name

        /** Mốc chờ ô của app [pkg] ở ô [slot]; gói lạ / ô ngoài dải ⇒ `null` (không ghi mốc sai vào sổ). */
        fun slotApp(pkg: String, slot: Int): TripWaitMark? =
            if (pkg.matches(com.byd.clusternav.launcher.ShellAppLauncher.PKG) && slot in 0..SLOT_MAX) TripWaitMark(TripPlan.Wait.SLOT_APP, pkg, slot) else null

        fun decode(raw: String?): TripWaitMark? {
            if (raw.isNullOrBlank()) return null
            val p = raw.trim().split(':')
            val w = TripPlan.Wait.values().firstOrNull { it.name == p[0] } ?: return null
            if (w != TripPlan.Wait.SLOT_APP) return if (p.size == 1) TripWaitMark(w) else null
            if (p.size != 3) return null
            return slotApp(p[1], p[2].toIntOrNull() ?: return null)
        }
    }
}
