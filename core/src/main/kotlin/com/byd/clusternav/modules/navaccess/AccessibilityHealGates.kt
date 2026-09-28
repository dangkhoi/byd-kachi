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
     * Đã leo nấc [HealStep.FORCE_STOP] trong LẦN NỔ MÁY NÀY chưa.
     *
     * Nhận biết máy đã khởi động lại mà KHÔNG cần shell: mốc lưu là `SystemClock.elapsedRealtime()`, đồng hồ
     * này đếm từ lúc bật máy và **về 0 khi khởi động lại**. Nên mốc lưu LỚN HƠN mốc hiện tại ⇒ máy đã khởi động
     * lại ⇒ coi như CHƯA leo, cho phép leo lại. Mốc `< 0` = chưa từng leo.
     */
    fun escalatedThisBoot(escalatedAtElapsed: Long, nowElapsed: Long): Boolean =
        escalatedAtElapsed in 0..nowElapsed

    /**
     * Chọn nấc cho MỘT lượt chữa. Thuần logic, không chạm hệ thống, test off-device.
     *
     * Vì sao có cổng này thay vì cứ thử lần lượt — [ĐO xe 2026-09-28] nấc [HealStep.FORCE_STOP] **giết tiến
     * trình launcher**, tức màn hình chính của xe chớp một nhịp. Cái giá đó chỉ chấp nhận được khi đúng là ca
     * KẸT, nơi nấc rẻ đã chứng minh là vô ích ([AccessibilityRebind.isInBindingServices] có trích dẫn AOSP).
     * Mọi ca còn lại phải đi nấc rẻ.
     *
     * @param bound dịch vụ đã gắn thật chưa (hỏi `AccessibilityManager`, không đoán).
     * @param stuckInBinding đang KẸT trong khối `Binding services` không ([AccessibilityRebind.isInBindingServices]).
     * @param wanted đường TỰ ĐỘNG có được phép leo không — **cùng cổng với watchdog 30 s** (`phím-thoại BẬT`),
     *   theo R-nf5 của spec. Vì sao đúng cổng đó chứ không phải "bật phím-thoại HOẶC booster": sau khi tự giết,
     *   thứ lắp lại `enabled_accessibility_services` nếu nửa sau của lệnh tách rời không chạy CHÍNH LÀ vòng
     *   watchdog đó — mà nó chỉ chạy khi phím-thoại bật ([com.byd.clusternav.VoiceKeyKeepAliveService], và
     *   [alarmShouldHeal] cũng vậy). Cho phép leo ngoài cổng ấy là mở cửa "giết xong không ai lắp lại" = phím
     *   chết hẳn, tệ hơn chính cái bệnh đang chữa. Người dùng tự bấm thì vẫn được ([userAsked]).
     * @param userAsked người dùng vừa TỰ BẤM "Kiểm tra / Sửa ngay" (hoặc vừa gạt BẬT phím-thoại). Bấm tay là
     *   đồng ý rõ ràng ⇒ bỏ qua mọi cổng giữ (một-lần-mỗi-lần-nổ-máy, đang-chứa-app), vì họ đang ngồi đó và chủ
     *   động yêu cầu — và câu thông báo nói trước rằng giao diện khởi động lại, ô đang mở phải vẽ lại.
     * @param guestAppVisible có app của NGƯỜI KHÁC đang hiện ở chỗ mà việc giết tiến trình này làm hỏng không —
     *   màn chính, hoặc một màn ảo DO CHÍNH MÌNH tạo (ô). Đo bằng
     *   [com.byd.clusternav.modules.clustercast.StackParse.noGuestAppVisible] (`am stack list` +
     *   `dumpsys display`), không dùng cờ RAM (CLAUDE.md §5). [ĐO xe 2026-09-28] giết launcher lúc một ô đang
     *   chứa app làm chết màn ảo của ô, cửa sổ app rơi lại thành **mảng đen phủ kín nhà**. Nên khi phát hiện
     *   MUỘN (đang dùng app) thì KHÔNG tự ý giết — báo thật rồi để người dùng chọn. Lúc xe vừa thức thì ô còn
     *   rỗng, cờ này `false`, chữa tự động không để lại vết. Cụm KHÔNG tính vì màn ảo cụm không do ta tạo nên
     *   không chết theo ta ([ĐO] "màn cụm không hề hấn") — miễn theo chủ sở hữu thật, không theo id đoán trước.
     * @param escalatedAtElapsed mốc `elapsedRealtime` của lần leo trước (ghi vào prefs TRƯỚC khi bắn lệnh, vì
     *   tiến trình sắp chết); `< 0` = chưa từng.
     * @param nowElapsed `SystemClock.elapsedRealtime()` hiện tại.
     */
    fun healStep(
        bound: Boolean,
        stuckInBinding: Boolean,
        wanted: Boolean,
        userAsked: Boolean,
        guestAppVisible: Boolean,
        escalatedAtElapsed: Long,
        nowElapsed: Long,
    ): HealStep {
        if (bound) return HealStep.NONE
        if (!wanted && !userAsked) return HealStep.NONE
        if (!stuckInBinding) return HealStep.TOGGLE
        if (userAsked) return HealStep.FORCE_STOP
        if (guestAppVisible) return HealStep.NONE
        if (escalatedThisBoot(escalatedAtElapsed, nowElapsed)) return HealStep.NONE
        return HealStep.FORCE_STOP
    }
}
