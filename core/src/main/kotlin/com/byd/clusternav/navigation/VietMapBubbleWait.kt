package com.byd.clusternav.navigation

/**
 * ═══ 2.89 · B2 — CHỜ bóng VietMap trước khi hạ VietMap xuống nền (thuần, `:core`) ═════════════════════════════════════
 *
 * Owner 05/10: *"lúc vietmap chạy nhanh qua splash thì OK, có lúc mạng chậm nó đứng ở đó, hạ xuống thì bóng không lên"*.
 * [SUY đọc mã 2.88 `VietMapAutostart.pollUntilInMap`] hết 25 s mà chưa đủ điều kiện ⇒ hàm trả `false` nhưng bên gọi VẪN
 * hạ nền (về Kachi / HOME) ⇒ bóng không lên.
 *
 * ## Màn chờ nằm TRONG `MainActivity` — tên activity KHÔNG phân biệt được màn chờ với map
 * [ĐO manifest VietMap 3.4.3 + mod 3.4.0] activity MAIN/LAUNCHER duy nhất là `vn.vietmap.live.MainActivity`
 * (`theme=@style/LaunchTheme`, meta-data `io.flutter.embedding.android.SplashScreenDrawable`) — không có `SplashActivity`.
 * Giả định 09-21 ("màn flash mang tên khác") là sai. Dấu hiệu Android thấy được duy nhất là service dựng bóng:
 * [ĐO jadx 3.4.3 `vn/vietmap/live/MainActivity.java:689-690` → `M()` `:436-453`] `VMBluetoothService` chỉ được
 * `bindService` + `startForegroundService` khi phần Dart gọi kênh `vml_main_channel` › `startVMBluetoothService`;
 * bóng cũng do Dart bật/tắt (`showFloatingView` / `hideFloatingView`). Dart gọi lúc nào so với lúc rời màn chờ:
 * [CHƯA BIẾT] (không dịch ngược được Dart AOT) — chốt trên xe bằng dòng `chờ bóng VietMap gần nhất` ở màn Chẩn đoán.
 *
 * ## Luật, từng nhịp một ảnh [Tick], quyết bằng [next]
 * ("trên cùng" = activity resumed của DISPLAY 0 — [topOnDefaultDisplay], Pass 2 · vietmap-dock-r1-1; KHÔNG phải dòng tổng.)
 *  1. **Sẵn sàng** = trên cùng là `…/.MainActivity` của VietMap VÀ (khi cần bóng) service bóng chạy MỚI ở lượt mở này ([freshBubble],
 *     Pass 3 · vietmap-dock-r2-3), giữ liền ≥ [SETTLE_MS] ⇒ [Outcome.READY] (hạ nền). Chỉ biển tốc độ (không cần bóng) ⇒ chỉ cần
 *     `MainActivity` ở trên cùng.
 *  2. **Người dùng đã rời** = đã từng thấy VietMap trên cùng, nay trên cùng là gói KHÁC ⇒ [Outcome.USER_LEFT]: dừng
 *     ngay, KHÔNG gửi HOME / không kéo Kachi lên (không giật người dùng khỏi app họ vừa mở — kể cả hộp thoại hệ thống).
 *  3. **Hết [TIMEOUT_MS]** (60 s — cold start trên mạng chậm mà owner tả; 2.88 là 25 s và chính là chỗ hụt): VietMap vẫn
 *     ở trên cùng mà chưa đủ (1) ⇒ [Outcome.STILL_SPLASH] — KHÔNG hạ (không chứng minh được đã qua màn chờ; hạ lúc này
 *     đúng là bệnh owner tả — để VietMap tự xong, người dùng tự về HOME là bóng lên); chưa từng thấy VietMap trên cùng ⇒
 *     [Outcome.NEVER_FOREGROUND] (không hạ gì). Không nhánh nào mở lại VietMap.
 *
 * (Lịch sử — 2.89 bản đầu) "TOP-RESUMED" = dòng tổng `  ResumedActivity: ActivityRecord{…}` của `dumpsys activity activities` — AOSP r47
 * `ActivityTaskManagerService.java:4953-4955` (`mRootActivityContainer.getTopResumedActivity()`) và r34 `:3833-3835`
 * (`mRootWindowContainer.getTopResumedActivity()`), cùng tiền tố. KHÔNG dùng dòng `mResumedActivity:` của từng stack:
 * mỗi màn ảo (ô Kachi, cụm) có một dòng như thế [ĐO máy ảo 01/10 `behind-home-emulator-2026-10-01/00-baseline.txt:12-13`:
 * VietMap resumed trong ô VÀ Kachi resumed cùng lúc] — dòng đó không nói ai đang ở TRƯỚC người lái. Dòng theo màn
 * (`" ResumedActivity:"`, r47 `RootActivityContainer.java:2385-2386`) không có dấu cách sau dấu hai chấm ⇒ không khớp.
 *
 * ## Review 2.89 Pass 2 · vietmap-dock-r1-1 — quyết theo DISPLAY 0, không theo dòng tổng
 * Dòng tổng là activity của display đang giữ TIÊU ĐIỂM, không phải thứ trước mặt người lái [ĐO máy ảo 05/10
 * `vm-prereq-emulator-2026-10-05/13-resumed-per-display.txt`: dòng tổng = VietMap ở màn ảo 3 trong khi display 0 = Kachi]. Mở một
 * activity lên màn KHÁC trong 60 s chờ (ô Kachi phục hồi, ô 7 dàn nhạc, ClusterBlack/app lên cụm) là tiêu điểm đổi ⇒ đọc thành
 * "người dùng đã rời" dù VietMap vẫn trên cùng display 0 ⇒ không hạ ⇒ bóng không lên. Nên nhịp đọc dùng [topOnDefaultDisplay]
 * (lệnh [PER_DISPLAY_GREP]): dòng resumed TRONG khối `Display #0` — [ĐO nguồn] A10 r47 dòng theo màn ` ResumedActivity:`
 * (`RootActivityContainer.java:2370-2386`, `printThisActivity` in `prefix` + `ActivityRecord`); A12 r34 dòng theo vùng
 * `    Resumed: ` (`RootWindowContainer.java:3640-3655` › `ActivityTaskSupervisor.printThisActivity` `:1973-1989`). Không có ⇒
 * dòng `mResumedActivity:` đầu khối (khối in "from top to bottom"). Không đọc được khối display 0 ⇒ rơi về dòng tổng, NHƯNG
 * dòng tổng mà nằm ở khối màn khác ⇒ `null` (chưa biết, không bao giờ là "đã rời").
 */
object VietMapBubbleWait {

    /** Nhịp đọc giữa hai lần `dumpsys` (giữ đúng 2.88: đường nhanh không đổi). */
    const val POLL_INTERVAL_MS = 500L

    /** Trần chờ — owner 05/10 "có lúc mạng chậm nó đứng ở đó"; cold start + map SDK + service bóng trên mạng chậm. */
    const val TIMEOUT_MS = 60_000L

    /** Phải đủ điều kiện liền bấy nhiêu mới hạ (2.88 giữ nguyên — đường nhanh không đổi). */
    const val SETTLE_MS = 2_500L

    /** Hạ nền xong chờ bấy nhiêu rồi đọc service bóng MỘT lần để ghi kết quả (không vòng, không mở lại). */
    const val RECHECK_AFTER_MS = 3_000L

    /** Activity chính (chứa CẢ màn chờ lẫn map) — [ĐO manifest] MAIN/LAUNCHER duy nhất `vn.vietmap.live.MainActivity`. */
    const val MAIN_ACTIVITY_MARK = "MainActivity"

    data class Top(val pkg: String, val activity: String)

    /** Một nhịp: activity trên cùng của display 0 ([topOnDefaultDisplay]; `null` = chưa biết) + điều kiện bóng (đã gộp "không cần bóng"). */
    data class Tick(val top: Top?, val bubbleReady: Boolean)

    /** Trạng thái giữa các nhịp — tất cả là số đo của lượt này, không mang sang lượt khác. */
    data class State(val seenVietMap: Boolean = false, val readySinceMs: Long = -1L, val lastTop: Top? = null)

    enum class Outcome(val background: Boolean, val why: String) {
        READY(true, "MainActivity trên cùng + service bóng chạy liền ≥ $SETTLE_MS ms"),
        STILL_SPLASH(
            false,
            "hết ${TIMEOUT_MS / 1000} s, VietMap còn trên cùng mà service bóng chưa chạy — chưa chứng minh qua màn chờ, KHÔNG hạ",
        ),
        USER_LEFT(false, "người dùng đã chuyển sang app khác — dừng, không gửi HOME"),
        NEVER_FOREGROUND(false, "hết ${TIMEOUT_MS / 1000} s mà VietMap chưa lên trên cùng — không hạ gì"),
    }

    sealed interface Step {
        data class Wait(val state: State) : Step
        data class Done(val outcome: Outcome, val state: State) : Step
    }

    /**
     * Dòng tổng TOP-RESUMED ⇒ gói/activity; không có dòng ⇒ `null`. ⚠ CHỈ CHO TEST — KHÔNG dùng để quyết định (Pass 3 ·
     * vietmap-dock-r2-7): dòng tổng là display giữ TIÊU ĐIỂM, quyết theo nó là mang lại lỗi r1-1 (tiêu điểm sang ô / cụm đọc thành
     * USER_LEFT). Vòng chờ dùng [topOnDefaultDisplay]. `internal` ⇒ `:app` không gọi được.
     */
    internal fun topResumed(dumpsysResumedGrep: String?): Top? {
        if (dumpsysResumedGrep.isNullOrBlank()) return null
        for (raw in dumpsysResumedGrep.lineSequence()) {
            val m = TOP.find(raw) ?: continue
            return Top(m.groupValues[2], m.groupValues[3])
        }
        return null
    }

    /**
     * Lệnh đọc của vòng chờ (dòng `Display #N` + mọi dòng *ResumedActivity + dòng `Resumed: ` của A12) — [ĐO máy ảo A10] fixture
     * `13-resumed-per-display` dùng `'^Display #|ResumedActivity'`; thêm `Resumed: ` cho A12 [ĐO nguồn r34, chưa đo máy]. Là tập
     * TRÊN của lệnh cũ (`ResumedActivity` khớp cả `mResumedActivity`/`topResumedActivity`) ⇒ chốt "đã foreground" cũ đọc cùng đầu
     * ra vẫn đúng.
     */
    const val PER_DISPLAY_GREP = "dumpsys activity activities | grep -E '^Display #|ResumedActivity|Resumed: '"

    /**
     * Activity resumed TRƯỚC MẶT NGƯỜI LÁI (display 0) từ đầu ra [PER_DISPLAY_GREP] — KDoc lớp, mục Pass 2. `null` = chưa biết.
     * Thứ tự: dòng theo màn ` ResumedActivity:` trong khối `Display #0` → dòng `mResumedActivity:` đầu khối → dòng tổng nếu nó
     * KHÔNG nằm ở khối của màn khác.
     */
    fun topOnDefaultDisplay(perDisplayGrep: String?): Top? {
        if (perDisplayGrep.isNullOrBlank()) return null
        var display = -1
        var perDisplay: Top? = null
        var firstStack: Top? = null
        val elsewhere = HashSet<String>()     // bản ghi resumed ở màn ≠ 0 (mã băm `ActivityRecord{…}`)
        for (raw in perDisplayGrep.lineSequence()) {
            val header = DISPLAY_HEADER.find(raw)
            if (header != null) { display = header.groupValues[1].toInt(); continue }
            if (TOP.containsMatchIn(raw)) continue   // dòng tổng in SAU mọi khối — không thuộc khối nào
            val perLine = PER_DISPLAY_LINE.find(raw)
            val m = perLine ?: STACK_LINE.find(raw) ?: continue
            if (display != 0) {
                if (display > 0) elsewhere += m.groupValues[1]   // chưa thấy tiêu đề nào ⇒ không gán màn (định dạng lạ)
                continue
            }
            val top = Top(m.groupValues[2], m.groupValues[3])
            if (perLine != null) perDisplay = perDisplay ?: top else firstStack = firstStack ?: top
        }
        perDisplay?.let { return it }
        firstStack?.let { return it }
        val global = perDisplayGrep.lineSequence().firstNotNullOfOrNull { TOP.find(it) } ?: return null
        return if (global.groupValues[1] in elsewhere) null else Top(global.groupValues[2], global.groupValues[3])
    }

    /**
     * Review 2.89 Pass 2 · whole-r1-1 — có hạ VietMap xuống nền sau lượt chờ không. [bootPath] = lượt nổ máy (đích là HOME, không
     * phải đưa Kachi lên lại); [tripPending] = chuyến lên xe của LẦN NỔ MÁY NÀY có việc và chưa xong (sổ chuyến bền).
     * [Outcome.STILL_SPLASH] thường KHÔNG hạ (B2 — hạ lúc còn màn chờ là bóng không lên), TRỪ lượt nổ máy mà chuyến còn chờ: chuyến
     * đợi HOME của Kachi đứng yên trên display 0 (`TripPlan.waitFor` › `HOME_STEADY`), VietMap đè lên ⇒ cả chuyến hết hạn 180 s
     * không mở gì [SUY đọc mã] — đúng bệnh A2. Đánh đổi (bóng có thể không lên lượt đó) ghi ở B2-OQ2 cho owner chốt.
     */
    fun backgroundAfter(outcome: Outcome, bootPath: Boolean, tripPending: Boolean, dialogExpected: Boolean = false): Boolean =
        outcome.background || (bootPath && tripPending && mayGoHome(outcome, dialogExpected))

    /**
     * Review 2.89 Pass 3 · vietmap-dock-r2-1 — kết cục nào (ngoài [Outcome.READY]) còn được về HOME khi chuyến lên xe đang chờ: luôn
     * [Outcome.STILL_SPLASH] (whole-r1-1); [Outcome.USER_LEFT] / [Outcome.NEVER_FOREGROUND] CHỈ khi [dialogExpected] = miễn pin của
     * VietMap CHƯA được chứng minh CÓ sau lượt AUTOSTART_PASS ⇒ hộp "IVI không hỗ trợ" (`UnsupportActivity` của CarSetting, mở từ
     * `MainActivity.onCreate` của VietMap [ĐO nguồn E3]) là thứ đè display 0, không phải người dùng. HOME cũng đóng hộp đó
     * (`UnsupportActivity.onStop()` gọi `finish()` [ĐO jadx CarSetting]) — đúng như 2.88 (luôn HOME sau 25 s). Generic: không so tên
     * gói của hộp (CLAUDE.md §7), quyết theo SỰ THẬT miễn pin.
     */
    fun mayGoHome(outcome: Outcome, dialogExpected: Boolean): Boolean = when (outcome) {
        Outcome.STILL_SPLASH -> true
        Outcome.USER_LEFT, Outcome.NEVER_FOREGROUND -> dialogExpected
        Outcome.READY -> false
    }

    /**
     * Review 2.89 Pass 3 · vietmap-dock-r2-3 — mốc `lastActivity=` của ServiceRecord chứa [marker] trong đầu ra `dumpsys activity
     * services <gói>`, đổi ra "bao nhiêu ms trước" (`null` = không thấy record / không có dòng / `--`). [ĐO nguồn] A10 r47
     * `ServiceRecord.java:408-409` (A12 r34 `:476-477`) in `lastActivity=` bằng `TimeUtils.formatDuration(lastActivity, now)`
     * (`TimeUtils.java:316-322` · `:209-279`: dấu, rồi `Nd` `Nh` `Nm` `Ns` tuỳ có, LUÔN kết bằng `Nms`; `0` khi bằng nay; `--` khi
     * chưa đặt); `ActiveServices.java:553` (start) · `:1731/:1787` (bind tự tạo) đặt lại nó = `uptimeMillis()`. Khối của record =
     * dòng `  * ServiceRecord{… <gói>/<lớp>}` (`ActiveServices.java:4102-4108`, `ServiceRecord.java:962-971`) tới record kế.
     */
    fun serviceLastActivityAgoMs(servicesDump: String?, marker: String): Long? {
        if (servicesDump.isNullOrBlank()) return null
        var inRecord = false
        for (line in servicesDump.lineSequence()) {
            if (line.contains("ServiceRecord{")) { inRecord = line.contains(marker); continue }
            if (!inRecord) continue
            val m = LAST_ACTIVITY.find(line) ?: continue
            return durationAgoMs(m.groupValues[1])
        }
        return null
    }

    /** `-1m2s345ms` ⇒ 62 345 (ms TRƯỚC); `0` ⇒ 0; dấu `+` (tương lai) / sai dạng ⇒ `null`. */
    internal fun durationAgoMs(raw: String): Long? {
        if (raw == "0") return 0L
        val m = DURATION.matchEntire(raw) ?: return null
        if (m.groupValues[1] != "-") return null
        fun g(i: Int) = m.groupValues[i].takeIf { it.isNotEmpty() }?.toLong() ?: 0L
        return ((g(2) * 24 + g(3)) * 60 + g(4)) * 60_000 + g(5) * 1_000 + g(6)
    }

    /**
     * Review 2.89 Pass 3 · vietmap-dock-r2-3 — service bóng có phải BẰNG CHỨNG cho lượt mở NÀY không. Không chạy trước lượt mở
     * ([runningBeforeLaunch] = `false`) ⇒ có mặt là đủ (đường cũ). Đã chạy từ trước (FGS sống lâu hơn activity — ca back khỏi VietMap)
     * hoặc chưa đọc được ⇒ có mặt KHÔNG nói gì về lượt Dart mới; chỉ nhận khi `lastActivity` mới hơn lượt mở ([lastActivityAgoMs] ≤
     * [sinceLaunchMs]: Dart gọi `startVMBluetoothService` ⇒ `startForegroundService` đặt lại mốc). Không đọc được mốc ⇒ không nhận
     * (rơi về luật STILL_SPLASH — không hạ khi chưa chứng minh).
     */
    fun freshBubble(present: Boolean, runningBeforeLaunch: Boolean?, lastActivityAgoMs: Long?, sinceLaunchMs: Long): Boolean =
        present && (runningBeforeLaunch == false || (lastActivityAgoMs != null && lastActivityAgoMs in 0..sinceLaunchMs))

    /** VietMap đang ở trên cùng bằng activity chính (màn chờ HOẶC map — xem KDoc lớp). */
    fun mainOnTop(top: Top?, pkg: String): Boolean =
        top != null && top.pkg == pkg && top.activity.contains(MAIN_ACTIVITY_MARK)

    /**
     * QUYẾT một nhịp. [elapsedMs] tính từ lúc gửi lệnh mở VietMap. Thứ tự: người dùng rời (sớm nhất) → sẵn sàng →
     * hết hạn. Không bao giờ ra lệnh mở lại VietMap.
     */
    fun next(state: State, tick: Tick, nowMs: Long, elapsedMs: Long, pkg: String): Step {
        val top = tick.top
        if (top != null && top.pkg != pkg && state.seenVietMap) {
            return Step.Done(Outcome.USER_LEFT, state.copy(lastTop = top))
        }
        val seen = state.seenVietMap || top?.pkg == pkg
        val lastTop = top ?: state.lastTop
        val ready = mainOnTop(top, pkg) && tick.bubbleReady
        val since = if (!ready) -1L else if (state.readySinceMs >= 0) state.readySinceMs else nowMs
        val s = State(seen, since, lastTop)
        if (ready && nowMs - since >= SETTLE_MS) return Step.Done(Outcome.READY, s)
        if (elapsedMs < TIMEOUT_MS) return Step.Wait(s)
        return Step.Done(if (seen && lastTop?.pkg == pkg) Outcome.STILL_SPLASH else Outcome.NEVER_FOREGROUND, s)
    }

    /** `lastActivity=-1s234ms restartTime=…` — nhóm 1 = thời lượng (r47 `ServiceRecord.java:408-409`). */
    private val LAST_ACTIVITY = Regex("""^\s*lastActivity=(\S+)""")

    /** Thời lượng của `TimeUtils.formatDuration` (fieldLen 0): dấu · ngày · giờ · phút (không lẫn `ms`) · giây · ms. */
    private val DURATION = Regex("""^([+-])(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m(?!s))?(?:(\d+)s)?(\d+)ms$""")

    /** `  ResumedActivity: ActivityRecord{e694363 u0 vn.vietmap.live/.MainActivity t2038}` (dấu cách sau `:`). Nhóm 3 = mã băm. */
    private val TOP = Regex("""^\s*ResumedActivity: ActivityRecord\{(\S+) u\d+ ([A-Za-z0-9_.]+)/(\S+)""")

    /** `Display #0 (activities from top to bottom):` (r47 `RootActivityContainer.java` › `dumpActivities`). */
    private val DISPLAY_HEADER = Regex("""^Display #(\d+)""")

    /**
     * Dòng theo màn: A10 MỘT dấu cách đầu, KHÔNG dấu cách sau `:` (r47 `RootActivityContainer.java:2385-2386`); A12 `    Resumed: `
     * (r34 `RootWindowContainer.java:3652`). Không khớp dòng tổng `  ResumedActivity: ` (hai dấu cách / chữ `Activity`).
     */
    private val PER_DISPLAY_LINE = Regex("""^(?: ResumedActivity:|\s+Resumed: )ActivityRecord\{(\S+) u\d+ ([A-Za-z0-9_.]+)/(\S+)""")

    /** Dòng của từng stack: `    mResumedActivity: ActivityRecord{…}`. */
    private val STACK_LINE = Regex("""^\s+mResumedActivity: ActivityRecord\{(\S+) u\d+ ([A-Za-z0-9_.]+)/(\S+)""")
}
