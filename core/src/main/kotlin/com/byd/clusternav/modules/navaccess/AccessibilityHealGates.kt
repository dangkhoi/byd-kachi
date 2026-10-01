package com.byd.clusternav.modules.navaccess

/**
 * CỔNG THUẦN cho hai đường tự-heal accessibility (B1 · `docs/specs/kachi-closeout-hardening.html` R3, BG-11/BG-14).
 *
 * ## Vì sao có cổng này
 * [ĐO máy ảo 2.65, đứng yên] alarm `REBIND_WATCHDOG` (window 45 s, lặp 60 s) + watchdog in-process 30 s của FGS
 * keep-alive cùng gọi `grantAccessibility`, và mỗi lượt grant — KỂ CẢ khi service đã bound — đi trọn đường dadb:
 * `settings get` → `settings put secure accessibility_enabled 1` (GHI Secure Settings) → sleep 1,2 s → `settings get`
 * → `dumpsys accessibility` = ≥ 4 lệnh shell + 1 ghi mỗi phút để rồi log "đã BOUND — không toggle".
 *
 * ## Vì sao gate bằng AccessibilityManager là ĐÚNG (đọc source, không nhớ — CLAUDE.md §3)
 * [ĐO AOSP `android-10.0.0_r47` `services/accessibility/.../AccessibilityManagerService.java`]
 *  - `:653-679` `getEnabledAccessibilityServiceList(feedbackType, userId)` duyệt **`userState.mBoundServices`**
 *    (lọc `mFeedbackType & feedbackType != 0`; app khai `feedbackGeneric` ⇒ luôn khớp `FEEDBACK_ALL_MASK`).
 *  - `:2563-2573` `dump()` in `"Bound services:{"` cũng từ **cùng** `userState.mBoundServices`.
 *  ⇒ Danh sách AccessibilityManager trả về **== danh sách `dumpsys accessibility` "Bound services"** mà
 *  [AccessibilityRebind.isClusterNavBound] parse qua dadb. Hai nguồn là MỘT; nguồn binder rẻ hơn ~4 lệnh shell.
 *  Android 12 (`android-12.0.0_r1` `:904-933`) giữ nguyên logic. Cờ RAM `NavAccessibilitySource.connected` thì
 *  KHÔNG dùng để gate (có thể kẹt true — KDoc `NavConnect.isAccessibilityBound`).
 *
 * Chỉ khi binder **trả lời được** và nói "bound" mới bỏ shell; binder ném/null (`null`) ⇒ đi đường shell như cũ
 * (không mất đường tự-heal — fix on-car 1.78, `docs/diagnostics/oncar-final-runbook-1.87.md:132`).
 */
object AccessibilityHealGates {

    /**
     * Một lượt grant: [boundPerManager] = kết quả AccessibilityManager (`true`/`false`, `null` = không hỏi được).
     * Đã bound ⇒ chạy [skipped] (0 lệnh shell, 0 ghi Secure Settings). Còn lại ⇒ chạy [shell] (đường cũ, đủ bước).
     */
    inline fun <R> grantOrSkip(boundPerManager: Boolean?, skipped: () -> R, shell: () -> R): R =
        if (boundPerManager == true) skipped() else shell()

    /**
     * Alarm 60 s có nên gọi heal không. Alarm là LƯỚI PHỤ: khi FGS keep-alive đang sống thì watchdog in-process
     * (30 s, không bị ROM `ssc_skip` drop) đã lo ⇒ alarm no-op, không tốn thêm binder/shell. FGS chết ⇒ cờ tĩnh
     * chết theo tiến trình ⇒ `inProcessWatchdogAlive=false` ⇒ alarm heal như cũ (đúng ca alarm sinh ra để chữa).
     */
    fun alarmShouldHeal(voiceKeyEnabled: Boolean, inProcessWatchdogAlive: Boolean): Boolean =
        voiceKeyEnabled && !inProcessWatchdogAlive

    /**
     * Nấc thang chữa dịch vụ Hỗ trợ. Rẻ → đắt, KHÔNG BAO GIỜ nhảy cóc.
     *  • [NONE] — không làm gì (đã gắn, hoặc không được phép leo).
     *  • [TOGGLE] — ghi lại `enabled_accessibility_services` (đường cũ, đang chạy ngoài hiện trường).
     *  • [FORCE_STOP] — tự giết gói mình rồi lắp lại. GIẾT LAUNCHER. Chỉ dùng cho ca KẸT.
     */
    enum class HealStep { NONE, TOGGLE, FORCE_STOP }

    /**
     * Mốc `elapsedRealtime` [markerElapsed] có thuộc LẦN NỔ MÁY NÀY không (tên giữ từ 2.79 — nay dùng cho mọi mốc
     * `elapsedRealtime` lưu bền, không riêng mốc leo thang).
     *
     * Nhận biết máy đã khởi động lại mà KHÔNG cần shell: mốc lưu là `SystemClock.elapsedRealtime()`, đồng hồ
     * này đếm từ lúc bật máy và **về 0 khi khởi động lại**. Nên mốc lưu LỚN HƠN mốc hiện tại ⇒ máy đã khởi động
     * lại ⇒ coi như mốc của đời máy trước. Mốc `< 0` = chưa từng.
     */
    fun escalatedThisBoot(markerElapsed: Long, nowElapsed: Long): Boolean =
        markerElapsed in 0..nowElapsed

    /**
     * Lượt chữa này chạy trong PHA nào của vòng đời xe — owner chốt 2026-09-29 cho 2.83 (quyết định A).
     *
     * GỐC [ĐO xe 29/09, tái hiện 2 lần từ trạng thái sạch]: mỗi lần TẮT MÁY, `AccModeManagerService` của BYD giết cả
     * ba tiến trình Kachi (`am_kill … stop com.byd.launcher`) mà KHÔNG phát `PACKAGE_RESTARTED` ⇒ dịch vụ Hỗ trợ đang
     * gắn bị đẩy vào `mBindingServices` và không bao giờ gắn lại ([ĐO AOSP android-10.0.0_r47
     * `AccessibilityManagerService.java:4114-4117`, `:1630-1631`]; `onHandleForceStop` `:453-484` chỉ chạy khi CÓ
     * `PACKAGE_RESTARTED`). Android tự dựng lại Kachi ~0,3 s sau vì Kachi là HOME. Tức là **kẹt sinh ra đúng lúc
     * tắt máy**, và lúc đó (cũng như vài giây đầu sau khi mở xe) giết launcher là gần như miễn phí.
     */
    enum class HealPhase {
        /**
         * LỚP 3 — xe đang chạy (watchdog 30 s, alarm, Preflight). Owner: *"lúc đang chạy mà lỗi thì user tự chữa
         * ok"* ⇒ KHÔNG tự force-stop nữa; nút *"Kiểm tra / Sửa ngay"* vẫn chữa được ([healStep] `userAsked`).
         */
        RUNNING,

        /** LỚP 1 — tiến trình khởi động khi màn KHÔNG tương tác (tắt máy). Không ai nhìn màn, không app nào trong ô. */
        TAT_MAY,

        /**
         * LỚP 2 — vừa mở xe: trong [MO_XE_GRACE_MS] sau `ACTION_SCREEN_ON`, và CHỈ khi màn bật đi sau một lần tắt
         * máy ([moXeFollowsTatMay]). [SUY từ mốc c2] lượt này nhiều khả năng rơi ngay SAU lúc app vào ô (KDoc
         * [MO_XE_GRACE_MS]); owner chấp nhận màn nhà load lại một nhịp, cửa sổ mồ côi do đuôi về nhà của lệnh tách rời dọn.
         */
        MO_XE,

        /**
         * LỚP 2 MỞ RỘNG — ÂN HẠN KHỞI ĐỘNG (READY-AT-HOME, 02/10): tiến trình launcher được DỰNG LẠI lúc màn ĐANG bật
         * (không phải tiến trình đầu tiên của lần nổ máy, không sinh từ lượt chữa của chính mình — [bootGraceMayRun]),
         * trong [BOOT_GRACE_MS] kể từ lúc tiến trình bật. Coi như lớp 2: HOME vừa dựng lại, người lái chưa kịp dùng ô.
         *
         * Vì sao — [ĐO E2E máy ảo 02/10 ca 1] giết kiểu BYD (`killApplication`, không `PACKAGE_RESTARTED`) lúc màn bật
         * ⇒ tiến trình mới `tương tác=true` ⇒ lớp 1 không mở (màn bật), lớp 2 không mở (không có `ACTION_SCREEN_ON` mới)
         * ⇒ `force-rebind xong: bound=false` → `nấc NONE, KHÔNG leo` (pha RUNNING) ⇒ phím chết ~70 s tới lần tắt màn sau.
         * Ngoài ân hạn thì vẫn là [RUNNING] — luật 2.83 không đổi.
         */
        KHOI_DONG,
    }

    /**
     * Chọn nấc cho MỘT lượt chữa. Thuần logic, không chạm hệ thống, test off-device.
     *
     * Vì sao có cổng này thay vì cứ thử lần lượt — [ĐO xe 2026-09-28] nấc [HealStep.FORCE_STOP] **giết tiến
     * trình launcher**, tức màn hình chính của xe chớp một nhịp. Cái giá đó chỉ chấp nhận được khi đúng là ca
     * KẸT, nơi nấc rẻ đã chứng minh là vô ích ([AccessibilityRebind.isInBindingServices] có trích dẫn AOSP).
     * Mọi ca còn lại phải đi nấc rẻ.
     *
     * ## 2.83 — đổi so với 2.79–2.82 (owner 2026-09-29, quyết định A)
     * Bản cũ cho đường TỰ ĐỘNG leo khi "không app khách + chưa leo trong lần nổ máy này". [ĐO xe 29/09] cổng "không
     * app khách" chặn đúng ca cần chữa: lúc lượt chữa tới nơi thì ô ĐÃ có app (`không app khách=false, ô của
     * mình=[6]` — nhật ký 11:34:29), còn hạn mức một-lần-mỗi-lần-nổ-máy thì vô nghĩa vì kẹt sinh ra ở MỖI lần tắt
     * máy. Nay:
     *  • [HealPhase.RUNNING] kẹt ⇒ [HealStep.NONE] — không bao giờ tự giết khi xe đang dùng; người dùng bấm nút.
     *  • [HealPhase.TAT_MAY] / [HealPhase.MO_XE] / [HealPhase.KHOI_DONG] (READY-AT-HOME 02/10 — lớp 2 mở rộng sang
     *    ân hạn khởi động) kẹt ⇒ [HealStep.FORCE_STOP], BỎ QUA cổng app khách (màn tắt / vừa
     *    mở xe — lúc mở xe ô có thể ĐÃ có app, xem [MO_XE_GRACE_MS]; owner chấp nhận màn nhà load lại một nhịp).
     *    Hạn mức là **một lượt mỗi SỰ KIỆN**, giữ ở [tatMayMayRun]/[moXeFresh] + marker ghi `commit()`
     *    trước khi đo (không phải ở đây, vì sự kiện chỉ tầng trên nhìn thấy).
     *  • `userAsked` giữ nguyên: bấm tay là đồng ý rõ ràng ⇒ leo ở mọi pha.
     *
     * @param bound dịch vụ đã gắn thật chưa (hỏi `AccessibilityManager`, không đoán).
     * @param stuckInBinding đang KẸT trong khối `Binding services` không ([AccessibilityRebind.isInBindingServices]).
     * @param wanted đường TỰ ĐỘNG có được phép leo không — **cùng cổng với watchdog 30 s** (`phím-thoại BẬT`),
     *   theo R-nf5 của spec. Vì sao đúng cổng đó chứ không phải "bật phím-thoại HOẶC booster": sau khi tự giết,
     *   thứ lắp lại `enabled_accessibility_services` nếu nửa sau của lệnh tách rời không chạy CHÍNH LÀ vòng
     *   watchdog đó — mà nó chỉ chạy khi phím-thoại bật ([com.byd.clusternav.VoiceKeyKeepAliveService], và
     *   [alarmShouldHeal] cũng vậy). Cho phép leo ngoài cổng ấy là mở cửa "giết xong không ai lắp lại" = phím
     *   chết hẳn, tệ hơn chính cái bệnh đang chữa. Người dùng tự bấm thì vẫn được ([userAsked]). Áp cho CẢ ba pha:
     *   sau một lượt [HealPhase.TAT_MAY] thì keep-alive dựng lại lúc mở xe và watchdog của nó là lưới lắp lại.
     * @param userAsked người dùng vừa TỰ BẤM "Kiểm tra / Sửa ngay" (hoặc vừa gạt BẬT phím-thoại). Bấm tay là
     *   đồng ý rõ ràng ⇒ leo ở mọi pha, vì họ đang ngồi đó và chủ động yêu cầu — và câu thông báo nói trước rằng
     *   giao diện khởi động lại, ô đang mở phải vẽ lại.
     * @param phase pha vòng đời của lượt này — xem [HealPhase]. KHÔNG có giá trị mặc định: mọi chỗ gọi phải nói rõ
     *   mình là lớp nào, để một đường gọi mới không lặng lẽ thừa hưởng quyền tự giết launcher.
     */
    fun healStep(
        bound: Boolean,
        stuckInBinding: Boolean,
        wanted: Boolean,
        userAsked: Boolean,
        phase: HealPhase,
    ): HealStep {
        if (bound) return HealStep.NONE
        if (!wanted && !userAsked) return HealStep.NONE
        if (!stuckInBinding) return HealStep.TOGGLE
        if (userAsked) return HealStep.FORCE_STOP
        return when (phase) {
            HealPhase.RUNNING -> HealStep.NONE
            HealPhase.TAT_MAY, HealPhase.MO_XE, HealPhase.KHOI_DONG -> HealStep.FORCE_STOP
        }
    }

    // ─── LỚP 3 (2.83) — watchdog 30 s khi ĐÃ BIẾT là kẹt ────────────────────────────────────────────────────

    /**
     * Việc của watchdog 30 s ở một nhịp.
     *  • [NONE] — đã gắn; hoặc đang KẸT đã đo bằng dump mà chưa tới nhịp kiểm lại: 0 lệnh shell, 0 ghi settings.
     *  • [GRANT] — chưa gắn và KHÔNG biết là kẹt: đường grant cũ (toggle chữa được ca "bật mà chưa gắn") — giữ nguyên.
     *  • [RECHECK] — đang KẸT đã đo, tới nhịp [STUCK_RECHECK_MS]: MỘT lần `dumpsys accessibility` CHỈ ĐỌC.
     */
    enum class WatchdogStep { NONE, GRANT, RECHECK }

    /**
     * Kẹt đã ĐO (dump) thì bao lâu mới đo lại. [ĐO máy ảo 29/09, E2E 2.83 ca 4] trước bản này, kẹt lúc đang chạy ⇒ mỗi
     * 30 s watchdog re-grant ⇒ TẮT/BẬT `enabled_accessibility_services` (vô ích: `mBindingServices` chặn cả bind lẫn
     * unbind — [ĐO AOSP `AccessibilityManagerService.java:1630-1631`]) + ~12 lệnh shell. 10 phút: đủ thưa để không đổi
     * cài đặt hệ thống vô cớ, đủ dày để một lần hết kẹt tự nhiên được ghi trong cùng chuyến. Hết kẹt thật thì binder
     * (hỏi MỖI nhịp, không shell) thấy NGAY — nhịp này chỉ để bắt ca "hết kẹt mà chưa gắn" (khi đó toggle lại hữu ích).
     */
    const val STUCK_RECHECK_MS = 10 * 60_000L

    /**
     * Chọn việc cho một nhịp watchdog. Thuần, test off-device.
     *
     * @param bound hỏi binder `AccessibilityManager` ở nhịp này (cùng nguồn `mBoundServices` với dump — KDoc đối tượng).
     * @param stuckSeenAt mốc `elapsedRealtime` của lần ĐO bằng dump gần nhất nói KẸT; `< 0` = không biết là kẹt. Mốc
     *   chỉ đặt từ dump ([AccessibilityRebind.isInBindingServices]) — binder không phân biệt được kẹt với chưa-gắn.
     *
     * Không gate vòng tròn (CLAUDE.md §3): mốc được làm mới bởi phép ĐO (grant cũ hoặc [WatchdogStep.RECHECK]), không
     * bởi đường chữa; lỡ không làm mới được thì quá [STUCK_RECHECK_MS] là đo lại, không bao giờ im vĩnh viễn. Đường chữa
     * THẬT của ca kẹt (force-stop: nút bấm, lớp 1, lớp 2) không đi qua cổng này.
     */
    fun watchdogStep(bound: Boolean, stuckSeenAt: Long, nowElapsed: Long): WatchdogStep = when {
        bound -> WatchdogStep.NONE
        !escalatedThisBoot(stuckSeenAt, nowElapsed) -> WatchdogStep.GRANT
        nowElapsed - stuckSeenAt >= STUCK_RECHECK_MS -> WatchdogStep.RECHECK
        else -> WatchdogStep.NONE
    }

    /**
     * Trạng thái watchdog ghi nhật ký ở nhịp này: binder nói đã gắn ⇒ BOUND; chưa gắn mà dump gần nhất nói kẹt ⇒ STUCK
     * (cùng sự thật với dòng STUCK trước ⇒ không ghi thêm; nhịp tim theo giờ ghi STUCK, không phải NOT_BOUND giả); còn
     * lại ⇒ NOT_BOUND. Không có nhánh này thì mỗi nhịp tim là một cặp NOT_BOUND↔STUCK.
     */
    fun watchdogState(bound: Boolean, stuckSeenAt: Long, nowElapsed: Long): A11yBindJournal.State = when {
        bound -> A11yBindJournal.State.BOUND
        escalatedThisBoot(stuckSeenAt, nowElapsed) -> A11yBindJournal.State.STUCK
        else -> A11yBindJournal.State.NOT_BOUND
    }

    /**
     * Mốc "kẹt" sau MỘT lần kiểm lại ([WatchdogStep.RECHECK]): kẹt ⇒ mốc của phép đo; đã gắn / hết kẹt / không đo được
     * (`null`) ⇒ `-1` ⇒ nhịp sau đi đường grant cũ. Không đọc được thì KHÔNG coi là còn kẹt: im lặng vì một bản đọc
     * hỏng là gate đường chữa bằng dữ liệu không có (CLAUDE.md §3).
     */
    fun stuckMarkOf(o: BindObservation?): Long = if (o != null && o.stuck) o.atElapsed else -1L

    // ─── LỚP 1 / LỚP 2 (2.83) — kẹt BỀN + một lượt mỗi sự kiện ─────────────────────────────────────────────

    /**
     * Hai quan sát "kẹt" phải cách nhau ít nhất chừng này mới gọi là KẸT BỀN.
     *
     * Vì sao phải hai lần: `binderDied()` → `serviceDisconnectedLocked` đẩy component vào `mBindingServices` ở MỌI
     * lần đứt ([ĐO AOSP `AccessibilityManagerService.java:4114-4117`]); nếu đứt vì tiến trình crash (không phải bị
     * BYD giết kiểu `stop …`) thì ActivityManager tự dựng lại dịch vụ và `onServiceConnected` gỡ nó ra — tức một
     * ảnh chụp đơn lẻ có thể bắt đúng khoảnh khắc "đang gắn bình thường". Ngay SAU một lượt force-stop của chính
     * mình cũng vậy: lệnh tách rời lắp lại dịch vụ sau 4 s, hệ gắn nó qua đúng khối `Binding` đó. 5 s = lớn hơn
     * khoảng lắp-lại ấy, vẫn nhỏ so với [MO_XE_GRACE_MS].
     */
    const val STUCK_CONFIRM_GAP_MS = 5_000L

    /**
     * LỚP 2 — quá chừng này kể từ `ACTION_SCREEN_ON` thì KHÔNG tự giết nữa (để người dùng bấm nút).
     *
     * [ĐO xe c2 29/09, `c2-logcat` + `usage-cycle2`] SCREEN_ON phát ~11:34:13.2 (`power_screen_state [1…]` 14.236,
     * `power_screen_broadcast_done` 15.242) → Kachi tạo màn ảo của ô (display 6) 11:34:19.114 → `am_create_activity`
     * YouTube vào ô 11:34:20.849 (≈ +7,6 s) → `KachiAutostart` "requested HOME up" 11:34:24.880. [SUY — ước lượng,
     * lớp 2 CHƯA chạy trên xe] lượt kiểm lớp 2 bắn ở ≈ +7…8 s (một lần đọc binder + hai lần `dumpsys accessibility`
     * cách [STUCK_CONFIRM_GAP_MS] + ~4 lệnh shell của nấc leo, mỗi lệnh ~0,2–0,5 s lúc mở xe [ĐO usage-cycle2]) ⇒
     * nhiều khả năng rơi NGAY SAU lúc app vào ô, không trước. (Lượt review 3: bản trước ghi "+10,6 s = lúc dựng app
     * vào ô" [ĐO] là đọc nhầm dòng "requested HOME up" — sửa theo CLAUDE.md §2.)
     *
     * Nên 10 s KHÔNG phải ranh "trước khi dựng app vào ô". Nghĩa thật: TRẦN để lượt tự giết không rơi muộn vào lúc
     * người lái đã bắt đầu dùng xe. App đã vào ô thì cửa sổ mồ côi do đuôi
     * [AccessibilityRebind.RETURN_HOME_IF_ORPHANED] của lệnh tách rời dọn — owner chấp nhận màn nhà load lại một nhịp.
     * Hoãn gắn ô tới khi Bound (R-A2 gốc của spec) là quyết định còn mở (spec §9 D-1), không làm ở đây. Dưới tải nặng
     * mà trễ quá trần thì bỏ lượt — lớp 3 (nút) còn đó.
     */
    const val MO_XE_GRACE_MS = 10_000L

    /**
     * LỚP 1 — các lần tiến trình khởi động khi màn tắt nằm trong cửa sổ này kể từ lượt "tắt máy" trước (và KHÔNG có
     * lần mở xe nào xen giữa) là CÙNG một lần tắt máy. Chính lượt force-stop của lớp 1 làm Android dựng lại Kachi
     * (HOME) lúc màn vẫn tắt — không có cửa sổ này thì tiến trình mới lại mở lượt mới cho cùng sự kiện.
     *
     * Vì sao có hạn chứ không chờ mãi một lần mở xe: bộ thu màn bật chỉ sống khi tiến trình sống; nếu lỡ mất một lần
     * mở xe thì cổng tự nhả sau 10 phút thay vì khoá vĩnh viễn (không gate đường phục hồi bằng dữ liệu mà chỉ chính
     * nó làm mới được — CLAUDE.md §3).
     */
    const val TAT_MAY_SAME_EVENT_MS = 10 * 60_000L

    /** Một lần nhìn trạng thái gắn: [bound] theo khối `Bound services`, [inBinding] theo khối `Binding services`. */
    data class BindObservation(val atElapsed: Long, val bound: Boolean, val inBinding: Boolean) {
        /** Ảnh chụp này là "kẹt": chưa gắn MÀ đang nằm trong `Binding services`. */
        val stuck: Boolean get() = !bound && inBinding
    }

    /**
     * Đọc MỘT bản `dumpsys accessibility` bằng đúng hai parser đang chạy ngoài hiện trường
     * ([AccessibilityRebind.isClusterNavBound] đọc TRỌN khối Bound — [ĐO xe 29/09] mỗi dịch vụ Bound in thêm một
     * dòng riêng; [AccessibilityRebind.isInBindingServices]). Dump rỗng/hỏng ⇒ không bound, không binding ⇒ KHÔNG
     * kẹt: không bao giờ giết tiến trình dựa trên một bản đọc hỏng.
     */
    fun observe(dumpsysAccessibility: String?, atElapsed: Long, component: String): BindObservation =
        BindObservation(
            atElapsed = atElapsed,
            bound = AccessibilityRebind.isClusterNavBound(dumpsysAccessibility, component),
            inBinding = AccessibilityRebind.isInBindingServices(dumpsysAccessibility, component),
        )

    /** KẸT BỀN: hai ảnh chụp đều kẹt và cách nhau ≥ [minGapMs] (đo bằng đồng hồ thật, không tin lời hẹn giờ). */
    fun stuckPersistent(first: BindObservation, second: BindObservation, minGapMs: Long = STUCK_CONFIRM_GAP_MS): Boolean =
        first.stuck && second.stuck && second.atElapsed - first.atElapsed >= minGapMs

    /**
     * LỚP 1 — lần khởi động tiến trình này có được mở MỘT lượt "tắt máy" không.
     *
     * @param interactive `PowerManager.isInteractive()`; `null` = không hỏi được ⇒ KHÔNG chạy (không đoán màn tắt).
     * @param lastTatMayAt mốc claim của lượt "tắt máy" trước (ghi `commit()` TRƯỚC khi đo); `< 0` = chưa từng.
     * @param lastMoXeAt mốc lần mở xe gần nhất mà bộ thu màn bật đã ghi; `< 0` = chưa từng.
     */
    fun tatMayMayRun(interactive: Boolean?, lastTatMayAt: Long, lastMoXeAt: Long, nowElapsed: Long): Boolean {
        if (interactive != false) return false
        if (!escalatedThisBoot(lastTatMayAt, nowElapsed)) return true
        if (lastMoXeAt > lastTatMayAt && lastMoXeAt <= nowElapsed) return true
        return nowElapsed - lastTatMayAt >= TAT_MAY_SAME_EVENT_MS
    }

    /**
     * LỚP 2 — sự kiện màn bật lúc [screenOnAt] chưa ai claim: [lastMoXeAt] là mốc của một lần mở xe TRƯỚC (nhỏ hơn),
     * hoặc của đời máy trước (lớn hơn "bây giờ"). Bằng đúng mốc này ⇒ đã có lượt lo ⇒ không mở lượt thứ hai.
     *
     * Tách khỏi [withinMoXeGrace] có chủ ý: sự kiện mới thì LUÔN được claim (claim đồng thời là bằng chứng "đã mở xe"
     * cho [tatMayMayRun]), kể cả khi đã lỡ ân hạn và không được chữa nữa.
     */
    fun moXeFresh(screenOnAt: Long, lastMoXeAt: Long, nowElapsed: Long): Boolean =
        lastMoXeAt !in screenOnAt..nowElapsed

    /** Còn trong ân hạn [MO_XE_GRACE_MS] kể từ `ACTION_SCREEN_ON` lúc [screenOnAt]. */
    fun withinMoXeGrace(screenOnAt: Long, nowElapsed: Long): Boolean =
        nowElapsed - screenOnAt in 0..MO_XE_GRACE_MS

    /**
     * LỚP 2 — lần màn bật này có ĐÚNG là MỞ XE không: kể từ lần màn bật TRƯỚC ([prevMoXeAt], đọc TRƯỚC khi claim lần
     * này) đã có một lần tiến trình launcher khởi động lúc màn TẮT — tức có claim lớp 1 [lastTatMayAt] mới hơn.
     *
     * VÌ SAO — `ACTION_SCREEN_ON` chỉ nói "màn vừa tương tác lại", không nói "vừa mở xe". Kẹt CHỈ sinh ra khi tiến
     * trình chết lúc dịch vụ đang gắn ([ĐO AOSP `:4114-4117`]); [ĐO xe c1 11:20:48–50 + c2 11:33:23–25 29/09] mỗi lần
     * tắt máy đều đi đúng một chuỗi: `power_sleep_requested` → `screen_toggled 0` → `am_kill … stop com.byd.launcher`
     * → `am_proc_start` KachiHome lúc màn ĐÃ tắt ⇒ lớp 1 LUÔN claim trước khi màn bật lại. Còn một lần tắt/bật màn
     * GIỮA LÚC LÁI (nếu ROM có đổi `isInteractive` — [CHƯA BIẾT]) không giết Kachi ⇒ không có claim mới ⇒ đây là lớp 3:
     * owner chốt *"lúc đang chạy mà lỗi thì user tự chữa"*, ô đang có app, giết launcher lúc đó là mảng đen phủ nhà.
     *
     * Không gate vòng tròn (CLAUDE.md §3): claim lớp 1 được làm mới bởi MỌI lần khởi động lúc màn tắt, không phụ thuộc
     * lượt chữa lớp 2 có ăn hay không; [tatMayMayRun] nhả cổng nhờ claim mở-xe (ghi TRƯỚC cổng này) hoặc sau
     * [TAT_MAY_SAME_EVENT_MS]. Mốc của đời máy trước (lớn hơn "bây giờ") hay `< 0` ⇒ coi như không có.
     */
    fun moXeFollowsTatMay(lastTatMayAt: Long, prevMoXeAt: Long, nowElapsed: Long): Boolean {
        if (!escalatedThisBoot(lastTatMayAt, nowElapsed)) return false
        if (!escalatedThisBoot(prevMoXeAt, nowElapsed)) return true
        return lastTatMayAt > prevMoXeAt
    }

    /**
     * Cổng CUỐI, hỏi lại NGAY TRƯỚC khi bắn lệnh tự giết (tầng thi hành — CLAUDE.md §5): pha còn đúng không.
     *  • [HealPhase.TAT_MAY]: màn phải VẪN tắt. Máy có thể ngủ giữa hai lần đo và thức dậy đúng lúc người lái mở
     *    xe — khi đó lượt tắt-máy phải bỏ, để lớp 2 lo theo luật của nó.
     *  • [HealPhase.MO_XE]: màn phải VẪN bật và còn trong ân hạn.
     *  • [HealPhase.KHOI_DONG]: màn phải VẪN bật và còn trong [BOOT_GRACE_MS] kể từ lúc tiến trình bật — quá ân hạn
     *    là người lái đã dùng xe ⇒ luật lớp 3 (không tự giết). Màn TẮT giữa chừng ⇒ trao lớp 1 ([bootGraceHandsOffToTatMay]).
     *  • [HealPhase.RUNNING]: đường tự động không bao giờ tới đây với FORCE_STOP; đường bấm tay không qua cổng này.
     */
    fun lifecycleFireAllowed(phase: HealPhase, interactiveNow: Boolean?, screenOnAt: Long, nowElapsed: Long): Boolean =
        when (phase) {
            HealPhase.RUNNING -> false
            HealPhase.TAT_MAY -> interactiveNow == false
            HealPhase.MO_XE -> interactiveNow == true && withinMoXeGrace(screenOnAt, nowElapsed)
            // [screenOnAt] mang mốc NEO của lượt: ở pha này là lúc tiến trình bật (xem [bootGraceMayRun]).
            HealPhase.KHOI_DONG -> interactiveNow == true && withinBootGrace(screenOnAt, nowElapsed)
        }

    /**
     * Tiến trình này có phải vừa được dựng lại vì CHÍNH lượt force-stop trước không (mốc leo [escalatedAt] trong
     * lần nổ máy này và mới hơn [windowMs]) — để chấm điểm "chữa xong có ăn không" cho lượt tắt-máy, lượt mà
     * watchdog không chấm được (keep-alive chỉ lên lại lúc mở xe; nếu xe ngủ dài thì nhịp đầu đã nhả mốc).
     */
    fun justHealed(escalatedAt: Long, nowElapsed: Long, windowMs: Long): Boolean =
        escalatedThisBoot(escalatedAt, nowElapsed) && nowElapsed - escalatedAt <= windowMs

    /**
     * Tiến trình này có được CHẤM ĐIỂM lượt leo [escalatedAt] không: [justHealed] VÀ chưa tiến trình nào nhận chấm
     * mốc đó ([scoredFor] = mốc leo mà một tiến trình trước đã nhận; `< 0` = chưa từng).
     *
     * Chỉ tiến trình khởi động ĐẦU TIÊN sau lượt leo mới là tiến trình do chính lượt force-stop đó dựng lại; mọi lần
     * khởi động sau (vẫn trong [windowMs]) là do chuyện KHÁC. [ĐO máy ảo 29/09] lượt tắt-máy 13:36:20 đã chấm
     * `sau-chua-ON` (13:36:35); 95 s sau một lần giết kiểu BYD khác (`am_kill … stop com.byd.launcher`, không
     * `PACKAGE_RESTARTED`) dựng tiến trình mới — cửa sổ 2 phút một mình cho chấm lại ⇒ nhật ký ghi oan
     * `sau-chua-VAN-TAT` cho một lượt chữa đã ăn. Trên xe: tắt máy → lớp 1 chữa → mở xe rồi tắt lại trong 2 phút.
     */
    fun firstStartAfterHeal(escalatedAt: Long, scoredFor: Long, nowElapsed: Long, windowMs: Long): Boolean =
        justHealed(escalatedAt, nowElapsed, windowMs) && scoredFor != escalatedAt

    // ─── LỚP 2 MỞ RỘNG — ÂN HẠN KHỞI ĐỘNG (READY-AT-HOME, 02/10) ────────────────────────────────────────────

    /**
     * Cửa sổ "tiến trình sinh ra từ lượt chữa của chính mình" sau một mốc leo: dùng để chấm điểm (`A11yLifecycleHeal`)
     * VÀ để nhận ra con của lượt chữa cho ân hạn khởi động ([ownHealChild]) — MỘT nguồn số cho cả hai, không chép hằng.
     */
    const val OWN_HEAL_WINDOW_MS = 2 * 60_000L

    /**
     * TRẦN ân hạn khởi động, tính từ lúc tiến trình bật. Quá chừng này ⇒ [HealPhase.RUNNING] (người dùng bấm *Sửa ngay*).
     *
     * [ĐO E2E máy ảo 02/10 ca 1] kênh lên +0,27 s sau tiến trình bật; một lượt đo-chờ-đo-leo = dump + [STUCK_CONFIRM_GAP_MS]
     * + dump + ~4 lệnh của nấc leo ⇒ bắn ≈ +7 s trên máy ảo; [SUY từ ĐO xe 29/09: kênh nền +1,0–1,3 s, mỗi lệnh dadb
     * 0,2–0,5 s] ≈ +8…10 s trên xe. 20 s = lề gấp đôi cho xe tải nặng mà vẫn trong lúc HOME vừa dựng lại; trễ hơn thì bỏ
     * lượt (fail-safe), không giết muộn.
     */
    const val BOOT_GRACE_MS = 20_000L

    /** Còn trong ân hạn khởi động kể từ lúc tiến trình bật [startedAt]. Đồng hồ lùi (âm) ⇒ KHÔNG. */
    fun withinBootGrace(startedAt: Long, nowElapsed: Long): Boolean =
        nowElapsed - startedAt in 0..BOOT_GRACE_MS

    /**
     * Lần khởi động tiến trình này có được mở MỘT lượt [HealPhase.KHOI_DONG] không. Mọi đầu vào là sự thật ĐO/ghi bền ở lúc
     * tiến trình bật, không cờ RAM xuyên tiến trình (CLAUDE.md §5):
     *
     * @param interactive `PowerManager.isInteractive()` lúc tiến trình bật. `false` = lớp 1 lo; `null` = không hỏi được ⇒
     *   KHÔNG (không đoán).
     * @param prevProcStartAt mốc bật của tiến trình launcher TRƯỚC (đọc trước khi ghi mốc của tiến trình này). Phải thuộc
     *   lần nổ máy này và nhỏ hơn [startedAt] ⇒ tiến trình này là DỰNG LẠI (tiến trình trước đã chết: BYD giết, crash, OOM,
     *   nâng cấp). Tiến trình ĐẦU TIÊN của lần nổ máy ⇒ KHÔNG: kẹt chỉ sinh ra khi một tiến trình chết lúc dịch vụ đang
     *   gắn ([ĐO AOSP `AccessibilityManagerService.java:4114-4117`]), còn `Binding` lúc vừa khởi động máy là lúc hệ đang
     *   gắn lần đầu bình thường.
     * @param ownHealChild tiến trình này do CHÍNH một lượt chữa dựng lại ([ownHealChild]) ⇒ KHÔNG leo nữa: chốt chống vòng
     *   giết–dựng–giết "trong cùng sự kiện" (2.83 R-A4). Một lần giết MỚI từ bên ngoài sau đó vẫn được chữa.
     */
    fun bootGraceMayRun(interactive: Boolean?, prevProcStartAt: Long, ownHealChild: Boolean, startedAt: Long): Boolean {
        if (interactive != true) return false
        if (prevProcStartAt !in 0 until startedAt) return false
        return !ownHealChild
    }

    /**
     * Tiến trình bật lúc [startedAt] có phải CON của lượt chữa có mốc leo [escalatedAt] không (mốc ghi `commit()` TRƯỚC mọi
     * lần bắn — lớp 1/2, ân hạn khởi động, nút *Sửa ngay*). Đúng 2.83 R-A4: *"sinh từ chính lượt chữa ⇒ không leo; bị giết
     * từ bên ngoài ⇒ được leo một lần"*.
     *
     * @param claimedHere tiến trình này vừa NHẬN chấm điểm mốc leo đó ([firstStartAfterHeal] + claim `a11y_scored_for_elapsed`
     *   ghi `commit()` ở `A11yLifecycleHeal.onProcessStart`) ⇒ nó là tiến trình ĐẦU TIÊN sau lượt leo = con.
     * @param scoredFor mốc leo mà một tiến trình đã nhận chấm (đọc SAU lượt nhận của chính mình).
     *
     * Không nhận được mà mốc leo còn trong [OWN_HEAL_WINDOW_MS] và CHƯA ai nhận ⇒ claim ghi hỏng ⇒ COI LÀ CON (fail-safe:
     * thà sót một lượt chữa còn hơn mở cửa vòng lặp). Mốc trong cửa sổ nhưng một tiến trình KHÁC đã nhận ⇒ mình sinh do một
     * lần giết MỚI ⇒ không phải con. [ĐO E2E máy ảo 02/10 01:51:43] BYD giết lần hai 61 s sau một lượt chữa: bản cửa-sổ-thuần
     * bỏ lượt ⇒ `nấc NONE, KHÔNG leo`, phím chết — luật này chữa được ca đó mà vẫn chặn con của lượt chữa.
     */
    fun ownHealChild(claimedHere: Boolean, escalatedAt: Long, scoredFor: Long, startedAt: Long): Boolean =
        claimedHere || (justHealed(escalatedAt, startedAt, OWN_HEAL_WINDOW_MS) && scoredFor != escalatedAt)

    /**
     * Lượt [HealPhase.KHOI_DONG] đã thấy KẸT rồi bị CẮT vì màn TẮT giữa chừng ⇒ có TRAO cho LỚP 1 không.
     *
     * [ĐO E2E máy ảo 02/10 ca C6] giết lúc màn bật, màn tắt ở +2 s ⇒ `khoi-dong: pha đã qua … → bỏ` ⇒ không lớp nào nhận:
     * lớp 1 chỉ mở lúc tiến trình BẬT khi màn tắt, lớp 2 đòi claim lớp 1 mới hơn ([moXeFollowsTatMay]) ⇒ phím chết tới khi
     * bấm tay (112 s; trên xe — nếu BYD giết TRƯỚC khi tắt màn, [CHƯA BIẾT] — là cả chuyến sau). Màn vừa tắt trong ân hạn =
     * đúng tình huống lớp 1 ("không ai nhìn màn") ⇒ dùng NGUYÊN cổng [tatMayMayRun] (một lượt mỗi lần tắt máy) — claim của
     * nó chặn tiến trình con (dựng lại lúc màn vẫn tắt) leo lần hai, và làm lần màn bật kế là MỞ XE cho lớp 2.
     * Chỉ khi màn tắt TRONG ân hạn: quá [BOOT_GRACE_MS] là luật lớp 3, không đổi.
     */
    fun bootGraceHandsOffToTatMay(
        interactiveNow: Boolean?,
        startedAt: Long,
        lastTatMayAt: Long,
        lastMoXeAt: Long,
        nowElapsed: Long,
    ): Boolean = withinBootGrace(startedAt, nowElapsed) && tatMayMayRun(interactiveNow, lastTatMayAt, lastMoXeAt, nowElapsed)

    /**
     * Ngay trước khi bắn: mốc leo (`a11y_forcestop_elapsed` — chốt chống vòng lặp của mọi lượt TỰ ĐỘNG, và là gốc của
     * [ownHealChild] cho [HealPhase.KHOI_DONG]) đã ghi được chưa. Ghi hỏng ⇒ lượt tự động KHÔNG bắn (CLAUDE.md §5: marker trước, đổi state
     * sau — không có marker là tiến trình dựng lại không biết mình sinh từ lượt chữa ⇒ leo lần nữa). Người dùng tự bấm thì
     * vẫn bắn: không có lượt tự động nào nối theo một lần bấm tay mà không qua chính chốt này.
     */
    fun autoFireAllowed(userAsked: Boolean, markerWritten: Boolean): Boolean = userAsked || markerWritten
}
