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

    /** Ba khoá THEO XE của chuyến (tệp `clusternav_state`, cùng chỗ `kachi_behind_marks`). Đổi tên = mất sổ của máy đang chạy. */
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

    /** Kết quả chuyến hiện ở Cài đặt + màn Chẩn đoán (R2.7). */
    enum class Code { RAN, NOTHING, EXPIRED, GAVE_UP }

    /** Khoá lần khởi động máy — xem KDoc lớp. [bootCount] `null`/âm = không đọc được. */
    fun bootKey(bootCount: Int?, wallNow: Long, elapsedNow: Long): String =
        if (bootCount != null && bootCount >= 0) "n$bootCount" else "w${(wallNow - elapsedNow) / 60_000L}"

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

    /** Kết quả chuyến gần nhất ([Code] + giờ tường + một dòng ASCII ngắn cho màn Chẩn đoán). */
    data class Result(val trip: String, val code: Code, val atWall: Long, val detail: String)

    fun encodeResult(r: Result): String =
        "v=1;trip=${r.trip};code=${r.code};at=${r.atWall};d=${r.detail.filter { it in SAFE_DETAIL }.take(DETAIL_MAX)}"

    fun decodeResult(raw: String?): Result? {
        val f = fields(raw) ?: return null
        val trip = f["trip"]?.takeIf { it.matches(ID) } ?: return null
        val code = Code.values().firstOrNull { it.name == f["code"] } ?: return null
        return Result(trip, code, f["at"]?.toLongOrNull() ?: 0L, f["d"].orEmpty())
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
