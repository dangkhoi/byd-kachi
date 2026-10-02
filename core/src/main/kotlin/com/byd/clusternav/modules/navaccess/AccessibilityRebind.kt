package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.launcher.HomeActivityCmd
import com.byd.clusternav.launcher.camera.CameraGuard

/**
 * PURE (no-Android) decision + string logic for FORCE-REBINDING the accessibility service.
 *
 * The bug (measured on-car 2026-08-14, see docs/diagnostics/oncar-handoff-voicekey-2026-08-14.md §8):
 * after a reboot [NavAccessibilityService] is ENABLED (present in `settings get secure
 * enabled_accessibility_services`, `accessibility_enabled=1`) but NOT actually BOUND (absent from the
 * `dumpsys accessibility` "Bound services" section). `onServiceConnected` never runs, so `onKeyEvent`
 * (mic-hold voice key) and the screen-read booster are both dead. Writing the setting only makes it
 * *enabled*; it does not force a *bind*.
 *
 * The proven live fix is to TOGGLE the service OUT then IN, which forces the framework to rebind it:
 *   1. write enabled_accessibility_services WITHOUT ClusterNav (OEM services preserved),
 *   2. brief pause,
 *   3. write it back WITH ClusterNav appended, then `accessibility_enabled 1`.
 *
 * This object holds only the parts that can be decided without a device — which settings-write commands to
 * run, and whether the dump says we are bound — so they are covered by a JVM unit test
 * ([AccessibilityRebindTest]). The dadb I/O, pausing and never-leave-removed recovery live in
 * `com.byd.clusternav.NavConnect.doGrantAccessibility` in `:app` (which owns the Android + dadb transport).
 */
object AccessibilityRebind {
    /** ClusterNav's accessibility-service component, exactly as it appears in enabled_accessibility_services. */
    const val ACC_COMP = "com.byd.clusternav/com.byd.clusternav.modules.navaccess.NavAccessibilityService"

    private const val KEY = "enabled_accessibility_services"

    /**
     * Hình dạng HỢP LỆ của một mục `pkg/cls` trong `enabled_accessibility_services`. Chỉ ký tự mà tên gói +
     * tên lớp Java được phép mang; cố ý KHÔNG nhận `$` (lớp lồng) vì `$` nằm trong dấu nháy kép của lệnh shell
     * sẽ bị nội suy — mà dịch vụ Hỗ trợ bắt buộc là lớp top-level nên không mất gì.
     */
    private val SAFE_ENTRY = Regex("[A-Za-z0-9._/-]+")

    /**
     * Hai chuỗi `pkg/cls` có cùng ComponentName không, chịu dạng SHORT (`pkg/.Cls` = `pkg.Cls`) lẫn FULL
     * (`pkg/pkg.sub.Cls`). #9 (deep-pass 2026-09-23): nếu enabled list OS lưu SHORT mà lệnh remove dùng FULL thì
     * so-sánh-chuỗi-trần TRƯỢT ⇒ không remove ⇒ framework thấy 'không đổi' ⇒ KHÔNG rebind (phím chết mà toggle
     * tưởng đã làm). Chuẩn hoá cả hai vế về pkg + class-đầy-đủ rồi so.
     */
    internal fun sameComponent(a: String, b: String): Boolean = normalizeComponent(a) == normalizeComponent(b)

    private fun normalizeComponent(s: String): String {
        val slash = s.indexOf('/')
        if (slash < 0) return s.trim()
        val pkg = s.substring(0, slash).trim()
        var cls = s.substring(slash + 1).trim()
        if (cls.startsWith(".")) cls = pkg + cls          // dạng short `/.Cls` → `pkg.Cls`
        else if (!cls.contains(".")) cls = "$pkg.$cls"    // dạng chỉ tên lớp trần → `pkg.Cls`
        return "$pkg/$cls"
    }

    /**
     * The ordered `settings put secure ...` commands that force a REBIND via a remove -> re-add toggle.
     *
     * Returns an EMPTY list when [boundContainsClusterNav] is already true — if the service is genuinely bound
     * we must do NOTHING (no flicker). Otherwise the returned order is exactly:
     *   - `[0]` remove ClusterNav from the enabled list (every other service, incl. the OEM ones, is preserved),
     *   - `[1]` re-add ClusterNav appended after the preserved services,
     *   - `[2]` `accessibility_enabled 1`.
     *
     * The caller MUST pause between `[0]` and the rest, and MUST guarantee `[1..]` run even on failure so the
     * setting is never left in the removed state (see `NavConnect`).
     *
     * [current] is the raw value of `enabled_accessibility_services` (colon-separated, possibly `"null"`,
     * blank, or with stray/duplicate colons). It is normalised: entries are trimmed, blanks and the literal
     * `null` are dropped, and every ClusterNav entry is removed before exactly one is re-appended — so the
     * output never contains a dangling/leading/trailing/double colon, and OEM services keep their exact
     * original strings and relative order. Values are quoted so an empty remove-list is written as `""`.
     */
    fun accessibilityRebindWrites(current: String?, boundContainsClusterNav: Boolean, component: String = ACC_COMP): List<String> {
        if (boundContainsClusterNav) return emptyList()
        val entries = (current ?: "")
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "null" }
        val without = entries.filter { !sameComponent(it, component) }
        val readd = without + component
        return listOf(
            "settings put secure $KEY \"${without.joinToString(":")}\"",
            "settings put secure $KEY \"${readd.joinToString(":")}\"",
            "settings put secure accessibility_enabled 1",
        )
    }

    /**
     * Whether ClusterNav appears in the **Bound services** section of `dumpsys accessibility` — i.e. the
     * service is really running, not merely listed under "Enabled services". On BYD DiLink (Android 10) the
     * dump prints `Bound services:{ ... }` (each bound service dumped with its label and/or `ComponentInfo{
     * pkg/cls}`) and, separately, `Enabled services:{ ... }`. After a reboot ClusterNav is only in the latter.
     *
     * We scope the search to the balanced braces immediately following the "Bound services" header (nested
     * `ComponentInfo{...}` braces are handled), so a ClusterNav entry in a LATER section (Enabled) is never
     * mistaken for being bound. Matching is case-insensitive on the distinctive token `clusternav`, which
     * appears whether the dump prints the package (`com.byd.clusternav/...`) or the label (`ClusterNav ...`).
     *
     * Fails SAFE: when the dump is null/blank or the section can't be located, returns `true` (treated as
     * bound) so the caller does NOT toggle — never risk a flicker on an unreadable/unexpected dump. The real
     * on-car dump is readable by the uid=shell dadb session, so the heal path still triggers when needed.
     */
    /**
     * Whether **THIS app's** accessibility service appears in the **Bound services** section of
     * `dumpsys accessibility` — i.e. really running, not merely "Enabled".
     *
     * ⚠⚠ GỐC 2×[P0] (deep-pass 2026-09-23): bản cũ khớp token `"clusternav"` dùng CHUNG — mà app anh em
     * `com.byd.clusternav2` cài SONG SONG có CÙNG FQN lớp (`com.byd.clusternav.modules.navaccess...`) + CÙNG
     * label ⇒ nếu a11y của clusternav2 bound thì Kachi tưởng MÌNH bound → KHÔNG BAO GIỜ heal (log báo ổn). Nay
     * scope theo **package của chính component** ([pkg] tách từ [component] = phần trước dấu `/`). Package là duy
     * nhất (`com.byd.launcher` vs `com.byd.clusternav2`), không trùng.
     *
     * ⚠ Đổi FAIL-MODE: dump null/blank/không thấy section ⇒ **KHÔNG XÁC NHẬN ĐƯỢC bound** ⇒ trả `false` (chưa
     * bound). Bản cũ `return true` (fail-safe "no flicker") = báo OK DỐI + heal không chạy khi dump lỗi. Với đường
     * HEAL: không đọc được thì THỬ toggle (flicker nhẹ, hoạ hoằn) tốt hơn phím chết mãi. Với REPORT ("Sửa ngay"):
     * không xác nhận = FAIL, không nói dối OK.
     */
    fun isClusterNavBound(dumpsysAccessibility: String?, component: String = ACC_COMP): Boolean {
        val dump = dumpsysAccessibility
        if (dump.isNullOrBlank()) return false
        val pkg = component.substringBefore('/').ifBlank { component }
        val header = dump.indexOf("Bound services", ignoreCase = true, startIndex = 0)
        if (header < 0) return false
        val open = dump.indexOf('{', header)
        if (open < 0) return false
        var depth = 0
        var close = -1
        var i = open
        while (i < dump.length) {
            when (dump[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) { close = i; break }
                }
            }
            i++
        }
        val section = if (close > open) dump.substring(open, close + 1) else dump.substring(open)
        // Bound section IN THEO ROM khác nhau:
        //  • ROM in COMPONENT: `ComponentInfo{com.byd.launcher/…}` → match `pkg/` / full component (phân biệt clusternav2).
        //  • ROM DiLink [ĐO xe 2026-09-24]: Bound CHỈ in LABEL (`Service[label=ClusterNav — booster…, capabilities=9]`),
        //    KHÔNG in package ⇒ match `pkg/` TRƯỢT dù đang bound thật (regression fix deep-pass gây ra). Phải match
        //    LABEL token `clusternav`. Để KHÔNG nhận nhầm app anh em `com.byd.clusternav2` (cùng label): chỉ nhận
        //    label-token khi PACKAGE CỦA MÌNH có trong "Enabled services" (chỉ app enabled mới bound được). Nếu cả
        //    hai app cùng enabled+bound+label (cài song song, cả hai bật voice-key — ca hiếm) thì Bound thiếu package
        //    nên vẫn mơ hồ; ưu tiên ĐÚNG cho ca thực tế (một app) + không dối.
        val byComponent = section.contains(component, ignoreCase = true) || section.contains("$pkg/", ignoreCase = true)
        if (byComponent) return true
        // label fallback CHỈ khi Bound section KHÔNG in component nào (ROM label-only). Nếu Bound có `.../` (componentInfo)
        // thì nó ĐÃ in package → component-match ở trên là đủ; dùng label lúc đó sẽ nhận nhầm componentInfo của
        // clusternav2 (chứa token 'clusternav'). Dấu hiệu label-only: section không có ký tự '/'.
        if (section.contains('/')) return false
        val selfEnabled = run {
            val eh = dump.indexOf("Enabled services", ignoreCase = true)
            eh >= 0 && dump.substring(eh).contains("$pkg/", ignoreCase = true)
        }
        return section.contains("clusternav", ignoreCase = true) && selfEnabled
    }

    /**
     * NẤC CUỐI của thang chữa: lệnh TỰ `force-stop` gói CỦA CHÍNH MÌNH rồi lắp lại dịch vụ Hỗ trợ, chạy TÁCH
     * RỜI trên xe. Trả chuỗi rỗng khi không được phép dựng lệnh (caller bỏ qua).
     *
     * ⚠ VÌ SAO PHẢI ĐẾN MỨC NÀY — [ĐO xe 2026-09-28, 2.79 (180), gói cài lại lúc 2026-09-27 23:04]: khi mục của
     * mình KẸT trong `Binding services` của `dumpsys accessibility` (có trong "Enabled", vắng khỏi "Bound",
     * phía ActivityManager KHÔNG còn ServiceRecord nào, kèm nhiều `ConnectionRecord … DEAD` mồ côi do `system`
     * giữ), thì đo được:
     *  • GỠ mình khỏi `enabled_accessibility_services` → mục trong `Binding services` VẪN CÒN NGUYÊN ⇒ toàn bộ
     *    đường toggle ở [accessibilityRebindWrites] KHÔNG THỂ gỡ trạng thái này. Đo trực tiếp, không suy luận.
     *  • `cmd accessibility` trên ROM DiLink chỉ phơi `get/set-bind-instant-service-allowed`, không có lệnh
     *    reset; mà đổi cờ đó cũng chỉ chảy vào cùng một lượt cập nhật trạng thái như ghi settings.
     *  • `am force-stop <gói mình>` → `Binding services:{}` NGAY, hệ tự gỡ mình khỏi danh sách enabled; lắp lại
     *    thì dịch vụ vào Bound THẬT (`received=true hasBound=true`, log `accessibility booster connected`).
     * ⇒ Đây là đường phục hồi DUY NHẤT chứng minh được trên xe, trùng kết luận hồ sơ hiện trường
     * `docs/diagnostics/oncar-piper-crash-binding-2026-09-18.md` ("toggle a11y KHÔNG đủ… force-stop → re-enable").
     *
     * PHẢI chạy TÁCH RỜI (`nohup … &`, đóng cả ba luồng chuẩn): lệnh do CHÍNH tiến trình sắp bị giết phát ra qua
     * dadb, nên nếu còn dính phiên shell thì nửa sau (lắp lại dịch vụ) chết theo ⇒ máy ở lại trạng thái "đã gỡ"
     * = phím chết hẳn. Tách rời thì lệnh đã lọt vào xe là chạy trọn, bất kể client còn sống hay không. Cùng lý lẽ
     * với lệnh gộp ở `NavConnect.forceRebindIfNeeded`, chỉ siết hơn một bậc vì ở đây client CHẮC CHẮN chết.
     *
     * @param current giá trị thô `enabled_accessibility_services` ĐỌC TRƯỚC khi giết. Danh sách lắp lại giữ
     *   nguyên mọi dịch vụ của hãng, bỏ mọi bản trùng của mình rồi thêm đúng một lần vào cuối — cùng quy tắc
     *   chuẩn hoá với [accessibilityRebindWrites], nên không bao giờ sinh dấu `:` thừa.
     * @param pkg gói sẽ bị force-stop. PHẢI TRÙNG phần gói của [component]; lệch hoặc rỗng ⇒ trả `""`. Đây là
     *   chốt cứng ở tầng THI HÀNH (CLAUDE.md §4/§5) để không đường nào giết nhầm gói của người khác.
     *
     * CHỐT THỨ HAI — lệnh này là chuỗi shell ghép từ GIÁ TRỊ ĐỌC NGOÀI (`settings get`), nên mọi mục trong danh
     * sách phải khớp [SAFE_ENTRY] (chỉ chữ/số/`.`/`_`/`-`/`/`). Một mục chứa `'`, `"`, `` ` ``, `$`, `;`, `&`,
     * `|`, khoảng trắng… là **thoát khỏi dấu nháy** của `sh -c '…'` hoặc bị shell nội suy ⇒ lệnh khác hẳn ý định,
     * mà đây lại đúng là lệnh tự giết tiến trình (CLAUDE.md §4.1 "user input → shell"). Gặp mục như vậy thì
     * **từ chối dựng lệnh** (`""`) thay vì bỏ mục đó ra: bỏ ra là vô tình TẮT một dịch vụ Hỗ trợ của hãng.
     *
     * ĐUÔI MỚI (2.83) — sau khi lắp lại xong, chờ [HOME_SETTLE_SEC] rồi chạy đuôi về màn nhà do [homeTail] chọn
     * ([HomeTail]): lượt TỰ ĐỘNG (lớp 1/2) thì [RETURN_HOME_IF_ORPHANED] — ĐO đỉnh display 0, CHỈ khi đó là cửa sổ
     * app khách mồ côi mới đưa màn nhà ra trước; lượt NGƯỜI DÙNG TỰ BẤM thì LUÔN về màn nhà ([HomeTail.ALWAYS]).
     * Lý do, bằng chứng, và bốn câu trả lời CLAUDE.md §4 nằm ở KDoc của [RETURN_HOME_IF_ORPHANED] và
     * [HomeTail.ALWAYS]. Đuôi đứng CUỐI (CLAUDE.md §6: đường mới xuống cuối) và nối bằng `;`: việc lắp lại dịch vụ
     * — thứ giữ phím sống — không bao giờ phải chờ hay phụ thuộc vào một bước chỉ để làm đẹp màn hình.
     *
     * @param homeTail ai yêu cầu lượt chữa này — [homeTailFor]. Mặc định [HomeTail.IF_ORPHANED] (bảo thủ: không
     *   bao giờ bấm Home mù) để một đường gọi mới không lặng lẽ có quyền che app người dùng đang mở.
     */
    fun forceStopRebindCommand(
        current: String?,
        pkg: String,
        component: String = ACC_COMP,
        pauseSec: Int = 4,
        homeTail: HomeTail = HomeTail.IF_ORPHANED,
    ): String {
        val owner = component.substringBefore('/').trim()
        if (pkg.isBlank() || owner.isBlank() || pkg.trim() != owner) return ""
        if (!SAFE_ENTRY.matches(component)) return ""
        val entries = (current ?: "")
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "null" }
        if (entries.any { !SAFE_ENTRY.matches(it) }) return ""
        val readd = (entries.filter { !sameComponent(it, component) } + component).joinToString(":")
        val pause = pauseSec.coerceIn(1, 30)
        val tail = when (homeTail) {
            HomeTail.IF_ORPHANED -> RETURN_HOME_IF_ORPHANED
            HomeTail.ALWAYS -> "$GO_HOME_UNLESS_CAMERA ; sleep $ORPHAN_RECHECK_SEC ; $RETURN_HOME_IF_ORPHANED"
        }
        val inner = "am force-stop ${pkg.trim()} ; sleep $pause ; " +
            "settings put secure $KEY \"$readd\" ; " +
            "settings put secure accessibility_enabled 1 ; " +
            "sleep $HOME_SETTLE_SEC ; $tail"
        return "nohup sh -c '$inner' >/dev/null 2>&1 </dev/null &"
    }

    /**
     * Đuôi về màn nhà của [forceStopRebindCommand] — chọn theo AI yêu cầu lượt chữa ([homeTailFor]), không theo pha.
     */
    enum class HomeTail {
        /**
         * Lớp 1 (tắt máy) / lớp 2 (mở xe): ĐO rồi mới Home — [RETURN_HOME_IF_ORPHANED]. Hành vi 2.83 gốc, cộng rào
         * camera ([GO_HOME_UNLESS_CAMERA]) trước lần Home duy nhất của nó.
         */
        IF_ORPHANED,

        /**
         * Người dùng TỰ BẤM "Kiểm tra / Sửa ngay" (hoặc gạt BẬT phím-thoại): sau khi lắp lại, về màn nhà qua
         * [GO_HOME_UNLESS_CAMERA] — Home với MỌI đỉnh display 0 (Maps toàn màn, cửa sổ mồ côi, chính màn nhà) TRỪ khi
         * màn camera của xe đang hiện trên display 0 hoặc không đọc được — rồi sau [ORPHAN_RECHECK_SEC] thêm MỘT lượt
         * [RETURN_HOME_IF_ORPHANED] (cũng qua rào camera) — tối đa hai lần Home.
         *
         * ## Vì sao còn một lượt đo SAU Home (senior review 2 — [P2])
         * [ĐO xe 3/3: 28/09 18:25:43.688 · 29/09 11:25:57.261 · 11:33:26.526] mỗi lần KachiHome được dựng TƯƠI khi kênh
         * shell chưa lên, chính Kachi mở lại app ô thành cửa sổ nổi trên display 0 (~0,27 s sau resume ở c2). Ca 5c gỡ
         * task home ⇒ KachiHome được dựng tươi BỞI chính lần Home này ⇒ [SUY mạnh] cửa sổ nổi rơi TRÊN màn nhà vừa mở,
         * giữ tiêu điểm nên cổng kênh shell không dò ⇒ đúng dạng KEY-7 (app mồ côi, launcher không lên). Lượt hai làm
         * đúng việc owner đã làm tay (*"bấm HOME … 1 lần là xong"*): KachiHome lúc đó ĐÃ có nên không bị dựng lại, không
         * đẻ thêm cửa sổ nổi. Máy ảo không tự sinh ca này ([ĐO] 0 lượt mở cờ `805306368` của Kachi trong log E2E) ⇒
         * khoá bằng fixture xe + `sh` thật (`ForceStopReturnHomeTest`).
         *
         * ## Vì sao — owner chốt 29/09: *"nút tự chữa đó phải trả về home, ko để app mồ côi"*
         * [ĐO máy ảo E2E 2.83 ca 5c, 13:58:27] lượt "Sửa ngay" giết Kachi ⇒ task home bị gỡ (`am_remove_task 1809`)
         * ⇒ `am_resume_activity … MapsActivity` — Google Maps TOÀN MÀN nằm ngay dưới lên đỉnh display 0. Dấu vân tay
         * mồ côi ([ORPHAN_SIGNATURE]) KHÔNG khớp stack toàn màn nên đuôi đo-rồi-Home không làm gì ⇒ người dùng bấm
         * nút ở màn nhà mà kết thúc ở Maps. Người vừa bấm đang nhìn màn Kachi và đã được báo "giao diện khởi động lại
         * một nhịp" ⇒ về lại đúng màn họ đang đứng là hành vi họ chờ. Lớp 1/2 KHÔNG đổi: không ai bấm gì, người lái
         * có thể vừa tự mở một app — đá họ khỏi app đó là sai.
         *
         * ## Bốn câu CLAUDE.md §4
         *  1. **Display**: chỉ display 0 — `am start` không `--display` ⇒ `DEFAULT_DISPLAY` (`ActivityStarter.java:1484-1486`,
         *     KDoc [HomeActivityCmd.GO_HOME]). Không đụng màn ảo của Ô, không quét display nào.
         *  2. **App**: màn hình chính MẶC ĐỊNH do hệ phân giải, như phím Home — không tên gói viết cứng.
         *  3. **Loại stack**: chỉ đưa stack `home` lên trước (`ActivityRecord.java:1283` → `moveTaskToFrontLocked`);
         *     stack `standard` bên dưới (Maps, cửa sổ mồ côi) không bị gỡ, không bị giết, không đổi chế độ cửa sổ — chỉ
         *     bị che. Stack `alwaysOnTop` (`pinned`) vẫn ở trên (`ActivityDisplay.getTopInsertPosition` `:302-322`).
         *  4. **Hoàn tác**: không có gì để hoàn tác — không ghi settings/`wm`/prefs, chỉ đổi thứ tự z; mở lại app từ
         *     màn nhà là xong. Đuôi đứng SAU `accessibility_enabled 1` và nối bằng `;` ⇒ Home hỏng thì dịch vụ Hỗ trợ
         *     vẫn đã được lắp lại.
         *
         * Vẫn chờ [HOME_SETTLE_SEC]: Home phải đến SAU cú mở lại app ô do chính Kachi bắn khi vừa sống lại ([ĐO] tới
         * muộn nhất 3,0 s sau `am_kill`), không thì cửa sổ mồ côi lại nổi lên trên màn nhà vừa mở.
         *
         * ## Không bao giờ Home đè camera lùi (vá trước OTA 2.83, 29/09)
         * Bản đầu của nhánh này bấm Home MÙ ~7 s sau khi bấm nút. Người lái vào số lùi trong khoảng đó thì màn nhà có thể
         * đè lên camera lùi — Home che camera hay không là [CHƯA ĐO], mà chưa đo thì không đánh cược (CLAUDE.md: "đúng
         * > an toàn > nhanh"). Nên MỌI lần Home của đuôi (cả lần đầu lẫn lần trong [RETURN_HOME_IF_ORPHANED]) đi qua
         * [GO_HOME_UNLESS_CAMERA]: màn camera ([CAMERA_SCREEN_SIGNATURE]) đang HIỆN ở bất kỳ đâu trên display 0, hoặc
         * không đọc được ⇒ bỏ Home, dịch vụ Hỗ trợ vẫn đã lắp lại. Camera bật SAU lần Home thì nó tự lên trên màn nhà.
         *
         * Senior review rào camera [P2]: bản đầu của rào chỉ nhìn stack TRÊN CÙNG — hụt khi một stack khác nằm trên camera mà
         * không che nó: cửa sổ PIP (`pinned`, luôn trên cùng — `ActivityDisplay.getTopInsertPosition` `:302-322`) hoặc
         * chính cửa sổ mồ côi freeform mà lượt đo thứ hai sinh ra để dọn. Home khi đó chèn stack home NGAY DƯỚI PIP /
         * TRÊN camera ⇒ camera bị che hẳn. [ĐO máy ảo 29/09, Settings đóng vai camera] PIP trên "camera" ⇒ rào cũ bấm
         * Home ⇒ "camera" `visible=true` → `false`; cửa sổ freeform mồ côi trên "camera" ⇒ đuôi mồ côi cũ bấm Home ⇒
         * y hệt. Vì vậy rào đọc cờ `visible` của MỌI stack display 0, không chỉ đỉnh — xem KDoc [GO_HOME_UNLESS_CAMERA].
         */
        ALWAYS,
    }

    /**
     * Dấu hiệu màn CAMERA của xe (lùi / 360) đang hiện trên display 0 — không lần Home nào của lượt chữa được đè lên nó.
     *
     * [ĐO] `com.byd.avc/com.byd.avc.AutoVideoActivity` là activity camera của BYD trong hai dump SurfaceFlinger chụp từ
     * xe (`docs/refactor-car-execution/fixtures/sf-FULL-HUMAN-CONFIRMED-cluster-shows-{app,gauges}.txt`); app hệ thống
     * `/system/app/AutoVideo` = gói `com.byd.avc` [ĐO dịch ngược 28/09]. Khớp theo tiền tố gói (`com.byd.avc/`) để bắt
     * mọi activity camera của app đó. Đây là RÀO AN TOÀN, không phải rẽ nhánh tính năng theo tên app (CLAUDE.md §7);
     * đời xe nào dùng app camera khác thì dấu hiệu đó phải vào `ClusterProfile`.
     */
    const val CAMERA_SCREEN_SIGNATURE = "com.byd.avc/"

    /**
     * Home CHỈ KHI đọc được display 0 VÀ không stack nào của display 0 đang HIỆN màn camera ([CAMERA_SCREEN_SIGNATURE])
     * — cửa duy nhất mà mọi lần Home của đuôi lượt chữa đi qua ([HomeTail.ALWAYS] và [RETURN_HOME_IF_ORPHANED]).
     *
     * ## Phép đo
     * `grep -A2 "displayId=0 "` lấy MỌI stack của display 0, mỗi stack ba dòng: tiêu đề `Stack id=…`, `configuration=…`,
     * rồi dòng task đầu `taskId=…: <gốc task> … visible=<cờ stack> topActivity=<activity chạy trên cùng của stack>`
     * [ĐO fixture xe `am-stack-list-oncar-2026-09-29-*`]. Grep thứ hai chỉ giữ các dòng `visible=true`. Cờ `visible` +
     * `topActivity` là của STACK (`RootActivityContainer.java:1276,1303-1304`), `StackInfo.toString` in lại chúng trên
     * MỌI dòng task (`ActivityManager.java:2539,2546-2548`; Android 12 `RootTaskInfo` y hệt — `ActivityTaskManager.java:
     * 553,560-562` tag `android-12.0.0_r34`) ⇒ dòng task đầu là đủ, kể cả khi camera không phải task gốc.
     *
     * ## Vì sao đọc cờ `visible` của MỌI stack, không chỉ đỉnh (senior review rào camera — [P2])
     * Stack chỉ bị tính là khuất khi có stack TOÀN MÀN đục nằm trên (`ActivityStack.java:2014-2034`); PIP (`pinned`)
     * và cửa sổ freeform nằm trên camera KHÔNG làm camera khuất. Còn Home thì chèn stack home ngay dưới các stack
     * `alwaysOnTop` (`ActivityDisplay.java:302-322`) ⇒ nằm TRÊN camera và che hẳn nó. Chỉ nhìn đỉnh là bỏ lọt đúng
     * hai ca đó — [ĐO máy ảo 29/09] cả hai (xem [HomeTail.ALWAYS]). Stack đỉnh có activity đang chạy thì luôn
     * `visible=true` (`ActivityStack.java:2000-2006`) ⇒ ca "camera ở đỉnh" của bản đầu vẫn được giữ. Camera còn trong
     * một stack đã khuất (`visible=false`, người lái đã ra khỏi số lùi) KHÔNG chặn Home — rào không được khoá chết nút
     * "Sửa ngay".
     *
     * Đọc hỏng ⇒ KHÔNG Home: không biết màn đang hiện gì thì không đánh cược với camera lùi. "Đọc được" = có ít nhất MỘT
     * dòng `visible=true` trên display 0 — stack đỉnh có activity chạy luôn in ra dòng đó (trên), nên không có dòng nào
     * nghĩa là `am stack list` rỗng / không có trên ROM / không có stack display 0 / ROM in dòng task KHÁC định dạng. Ca
     * cuối là lý do không dùng tiêu đề `Stack id=` làm dấu "đọc được" (senior review lượt 2 — [P3]): tiêu đề khớp mà dòng
     * task đổi dạng thì cờ `visible` biến mất, rào sẽ thấy "không camera" và Home — hỏng theo chiều MỞ.
     *
     * Giới hạn còn lại [SUY]: camera bật TRONG khoảng giữa lúc đọc và lúc `am start` tới hệ (một lần khởi động `am`,
     * cỡ dưới 1 s) thì vẫn có thể bị che — hẹp hơn nhiều so với ~7 s Home mù của bản đầu; đóng hẳn cần làm trong tiến
     * trình hệ, không làm được bằng `sh`.
     *
     * Bốn câu CLAUDE.md §4 như [HomeTail.ALWAYS] (chỉ display 0 · Home mặc định của hệ · chỉ đưa stack `home` lên ·
     * không state bền). Không có dấu `'` — cả chuỗi nằm trong `sh -c '…'`. Dựng bằng [CameraGuard] (bộ dựng rào DUY
     * NHẤT, spec shortcuts-autostart C8); `ForceStopReturnHomeTest` khoá chuỗi trùng từng byte bản 2.83.
     */
    val GO_HOME_UNLESS_CAMERA: String = CameraGuard.unlessCamera(CAMERA_SCREEN_SIGNATURE, null, HomeActivityCmd.GO_HOME)

    /** Người dùng tự bấm ⇒ [HomeTail.ALWAYS]; mọi đường TỰ ĐỘNG (lớp 1/2) ⇒ [HomeTail.IF_ORPHANED]. */
    fun homeTailFor(userAsked: Boolean): HomeTail = if (userAsked) HomeTail.ALWAYS else HomeTail.IF_ORPHANED

    /**
     * Nhịp chờ giữa lúc lắp lại dịch vụ và lúc đo đỉnh display 0. KHÔNG phải để "chờ hệ dựng lại launcher" (việc đó
     * xong từ lâu: [ĐO c2-logcat 29/09] `am_kill` 11:33:25.182 → `am_proc_start` KachiHome 25.515 → resume
     * 26.257, tức ~1,1 s), mà để phép đo đứng SAU cú mở lại app ô do chính Kachi bắn ra khi vừa sống lại — [ĐO] cú
     * đó tới muộn nhất 3,0 s sau `am_kill` (xem [RETURN_HOME_IF_ORPHANED]). Với `pauseSec` mặc định 4, phép đo
     * rơi vào ~7 s sau khi giết (4 + hai lệnh `settings` + 2): dư hơn gấp đôi mức đo được, kể cả khi xe tải nặng.
     */
    private const val HOME_SETTLE_SEC = 2

    /**
     * [HomeTail.ALWAYS]: nhịp chờ từ lần Home VÔ ĐIỀU KIỆN tới lượt đo-rồi-Home. Cửa sổ nổi do KachiHome dựng tươi tới
     * ~0,27 s sau resume [ĐO c2 11:33:26.257 → .526] và muộn nhất 1,85 s sau khi tiến trình sinh [ĐO KEY-7
     * 11:25:55.415 → 57.261]; lần Home này dựng activity trong tiến trình ĐÃ sống ⇒ 3 s dư hơn gấp đôi, cùng lẽ
     * [HOME_SETTLE_SEC].
     */
    private const val ORPHAN_RECHECK_SEC = 3

    /**
     * Dấu vân tay của cửa sổ app khách mồ côi, đúng thứ tự `WindowConfiguration.toString` in ra trên dòng
     * `configuration=` của `am stack list` — [ĐO fix-stacks 29/09] stack 32 của YouTube: `… mWindowingMode=freeform
     * mDisplayWindowingMode=fullscreen mActivityType=standard mAlwaysOnTop=undefined …`. Nghĩa: một cửa sổ NỔI,
     * loại `standard` (không bao giờ khớp `home`/`recents`/`pinned`), nằm trên một display chạy TOÀN MÀN — tức là
     * một thứ lạc chỗ, không phải bố cục chủ ý. Cùng định nghĩa với `StackParse.floatingOnMain` (lỗi hiện
     * trường 22/07), chỉ khác là ở đây phải khớp bằng `sh` vì lúc chạy, tiến trình Kotlin đã chết.
     */
    internal const val ORPHAN_SIGNATURE = " mWindowingMode=freeform mDisplayWindowingMode=fullscreen mActivityType=standard "

    /**
     * Đuôi của lệnh tách rời: nếu stack TRÊN CÙNG của display 0 mang [ORPHAN_SIGNATURE] thì về màn nhà QUA RÀO
     * CAMERA [GO_HOME_UNLESS_CAMERA]; ngược lại không làm gì.
     *
     * ## Triệu chứng và cơ chế
     * [ĐO owner 29/09] Sau "Sửa ngay": YouTube của một Ô hiện thành cửa sổ nổi trên màn chính, launcher không lên
     * trước; bấm Home một lần là "OK ngay, đẹp". [ĐO fix-stacks 11:28:13] display 0 từ trên xuống: stack 32
     * (YouTube, freeform) → stack 0 (home, KachiHome) → stack 31 (VietMap). [ĐO fix-logcat] đó KHÔNG phải stack
     * của Ô bị đẩy sang: màn ảo của Ô chết kéo theo activity (`am_destroy_activity` lý do
     * `finish-imm:finishAllActivitiesLocked`, 11:25:54.720); 3,0 s sau `am_kill`, **chính `com.byd.launcher`**
     * (trường 904 của `sysui_multi_action` = gói gọi; đối chứng: lượt `am start` từ shell in `904,com.android.shell`)
     * mở LẠI YouTube: `am_create_activity … 805306368` (= `NEW_TASK|SINGLE_TOP`) vào stack freeform MỚI
     * 32 trên display 0, khung = khung Ô. Lượt tắt máy (c2-logcat 11:33:26.526, 1,3 s sau `am_kill`) cũng y hệt.
     * [SUY, khớp cờ + khung + gói gọi] người mở là `IntentAppLauncher.openInSlot` — đường dự phòng khi kênh shell
     * chưa lên, bắt đầu bằng `setLaunchWindowingMode(5)` mà không có display đích. Chữa tận gốc chỗ đó là việc khác;
     * đuôi này chỉ bảo đảm một lượt chữa của CHÍNH MÌNH không bỏ người dùng lại trước một cửa sổ lạc chỗ.
     *
     * ## Vì sao bấm Home thì cửa sổ nổi biến mất [ĐO source android-10.0.0_r47]
     * Ý định HOME không component ⇒ loại HOME (`ActivityRecord.java:1283`); task home đã có ⇒
     * `setTargetStackAndMoveToFrontIfNeeded` thấy task trên cùng khác (`ActivityStarter.java:2109`) ⇒
     * `moveTaskToFrontLocked` (`:2143`) ⇒ `moveFocusableActivityToTop` (`ActivityStack.java:4909`) ⇒
     * `moveToFront` ⇒ `ActivityDisplay.positionChildAtTop`. `getTopInsertPosition` (`ActivityDisplay.java:302-322`)
     * chỉ nhường chỗ cho stack `alwaysOnTop` — stack mồ côi là `mAlwaysOnTop=undefined` [ĐO fix-stacks] ⇒ home lên
     * trên nó. Một stack nằm dưới stack TOÀN MÀN đục thì `getVisibility` trả `STACK_VISIBILITY_INVISIBLE`
     * (`ActivityStack.java:2014-2034`) — [ĐO fix-stacks] đúng luật đó trên ROM này: VietMap (toàn màn, dưới home)
     * đang `visible=false`.
     *
     * ## Vì sao phải ĐO trước, không bắn Home mù (đường TỰ ĐỘNG; bấm tay xem [HomeTail.ALWAYS])
     * Đường tự chữa lúc mở xe (lớp 2) cũng có thể dùng lệnh này, đúng lúc tài xế hay lùi xe ra khỏi chỗ đỗ. Camera
     * lùi của BYD là một ACTIVITY ([ĐO] lớp `com.byd.avc/com.byd.avc.AutoVideoActivity` trong dump SurfaceFlinger của
     * repo); Home mù có che nó hay không thì [CHƯA ĐO] — chưa đo thì không đánh cược. Khi app khác đang ở đỉnh
     * display 0 (toàn màn, camera, app người dùng vừa mở) hoặc home đã ở đỉnh, dấu vân tay không khớp ⇒ không làm gì.
     * Dấu vân tay khớp cũng CHƯA đủ: cửa sổ mồ côi freeform có thể nằm TRÊN camera đang hiện (camera bật trong vài
     * giây trước khi Kachi mở lại app ô) mà không che nó — Home lúc đó mới che camera [ĐO máy ảo 29/09, KDoc
     * [HomeTail.ALWAYS]]. Nên lần Home ở đây đi qua [GO_HOME_UNLESS_CAMERA] (đọc lại `am stack list` một lần nữa,
     * chỉ khi đã thấy mồ côi).
     *
     * ## Bốn câu CLAUDE.md §4
     *  1. **Display**: chỉ display 0 — phép đo lấy stack ĐẦU TIÊN có `displayId=0 ` (trong một display,
     *     `getAllStackInfos` in từ trên xuống: `RootActivityContainer.java:1321-1331`), còn `am start` không
     *     `--display` ⇒ display mặc định (xem [HomeActivityCmd.GO_HOME]). Không đụng màn ảo, không quét display nào khác.
     *  2. **App**: màn hình chính MẶC ĐỊNH của hệ (hệ tự phân giải, như phím Home) — không tên gói viết cứng. Kachi
     *     không làm home thì launcher gốc lên, vẫn đúng nghĩa "về màn nhà".
     *  3. **Loại stack**: chỉ đưa stack `home` lên trước; stack mồ côi (`standard`) không bị gỡ, không bị giết,
     *     không đổi chế độ cửa sổ — chỉ bị che. `recents`/`pinned` không bao giờ khớp dấu vân tay.
     *  4. **Hoàn tác**: không có gì để hoàn tác — không ghi settings/`wm`/prefs, chỉ đổi thứ tự z; thao tác kế tiếp
     *     của người dùng ghi đè. Hỏng giữa chừng (`am stack list` không có trên ROM, grep không khớp) ⇒ `t` rỗng ⇒
     *     không mở gì — lùi về đúng hành vi 2.82, dịch vụ Hỗ trợ đã lắp lại từ trước.
     *
     * Chỉ dùng `grep` (`-A`) + `head -n 2` + `case` lồng `case` và không có dấu `'` nào, vì cả chuỗi nằm trong
     * `sh -c '…'`. Đã chạy thật trên `sh` (bash) và `dash` với fixture xe (`ForceStopReturnHomeTest`), và [ĐO máy ảo
     * Android 10 29/09] trên đúng bộ công cụ của ROM: mksh R57 + BSD grep 2.5.1 + toybox `head`, với `am` giả in
     * fixture xe — cùng kết quả. Xe thật: chốt bằng một lượt "Sửa ngay" có app trong Ô, đọc `am stack list` sau ~8 s.
     */
    val RETURN_HOME_IF_ORPHANED: String =
        "t=\$(am stack list | grep -A1 \"displayId=0 \" | head -n 2) ; " +
            "case \"\$t\" in *\"$ORPHAN_SIGNATURE\"*) $GO_HOME_UNLESS_CAMERA ;; esac"

    /**
     * Cắt đúng khối `{...}` cân bằng ngoặc đi ngay sau tiêu đề [header] trong bản dump. Trả `null` khi không
     * thấy tiêu đề hoặc không thấy ngoặc mở. Tách riêng để [isInBindingServices] dùng CÙNG cách quét với
     * [isClusterNavBound] mà không phải sửa hàm cũ (hàm cũ đang chạy ngoài hiện trường, CLAUDE.md §6).
     */
    private fun braceSection(dump: String, header: String): String? {
        val h = dump.indexOf(header, ignoreCase = true)
        if (h < 0) return null
        val open = dump.indexOf('{', h)
        if (open < 0) return null
        var depth = 0
        var i = open
        while (i < dump.length) {
            when (dump[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return dump.substring(open, i + 1) }
            }
            i++
        }
        return dump.substring(open)
    }

    /**
     * Dịch vụ của MÌNH có đang KẸT trong khối `Binding services:{…}` của `dumpsys accessibility` không.
     *
     * VÌ SAO PHẢI TÁCH RIÊNG khỏi [isClusterNavBound] — hai trạng thái NHÌN GIỐNG NHAU ("đã bật mà chưa gắn")
     * nhưng cách chữa NGƯỢC NHAU:
     *  • (A) có trong Enabled, KHÔNG trong Bound, KHÔNG trong Binding — ca thường sau khi nổ máy. Ghi lại
     *    settings là hệ gọi `bindLocked` thật ⇒ đường toggle ở [accessibilityRebindWrites] CHỮA ĐƯỢC.
     *  • (B) có trong Enabled, KHÔNG trong Bound, CÓ trong Binding — ca KẸT. [ĐO AOSP android-10.0.0_r47
     *    `AccessibilityManagerService.java:1630-1631`] `updateServicesLocked` mở đầu vòng lặp bằng
     *    `if (mBindingServices.contains(componentName)) continue;` — dòng này nằm TRÊN cả `bindLocked()`
     *    (`:1642`) lẫn `unbindLocked()` (`:1645`) ⇒ mục kẹt vừa KHÔNG gắn lại được vừa KHÔNG gỡ được bằng bất kỳ
     *    lệnh ghi settings nào. Đo khớp trên xe 2026-09-28: gỡ hẳn khỏi `enabled_accessibility_services` mà mục
     *    trong `Binding services` VẪN CÒN. Ở ca này toggle là vô ích, phải leo thẳng lên
     *    [forceStopRebindCommand].
     *
     * Khớp theo PACKAGE của chính mình (phần trước `/` của [component]) chứ không theo nhãn: khối Binding luôn
     * in component đầy đủ `{pkg/cls}`, khác khối Bound (ROM DiLink chỉ in label — xem [isClusterNavBound]).
     *
     * FAIL-MODE: dump rỗng/không đọc được/không có khối Binding ⇒ `false` = "không khẳng định là kẹt". Cùng tinh
     * thần với [isClusterNavBound]: không xác nhận được thì KHÔNG leo lên nấc đắt nhất (force-stop giết launcher),
     * để đường rẻ chạy trước. Không bao giờ giết tiến trình dựa trên một bản dump không đọc nổi.
     */
    fun isInBindingServices(dumpsysAccessibility: String?, component: String = ACC_COMP): Boolean {
        val dump = dumpsysAccessibility
        if (dump.isNullOrBlank()) return false
        val section = braceSection(dump, "Binding services") ?: return false
        val pkg = component.substringBefore('/').trim().ifBlank { return false }
        if (section.contains(component, ignoreCase = true)) return true
        return section.contains("$pkg/", ignoreCase = true)
    }
}
