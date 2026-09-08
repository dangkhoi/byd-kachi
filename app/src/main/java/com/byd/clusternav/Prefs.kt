package com.byd.clusternav

import android.content.Context
import android.content.SharedPreferences
import com.byd.clusternav.contracts.SpeedLimitSource
import com.byd.clusternav.modules.voicekey.VoiceKeyBindingStore
import com.byd.clusternav.voicekey.VoiceKeyBinding
import com.byd.clusternav.voicekey.VoiceKeyBindings

/** Lưu lựa chọn người dùng (bật/tắt đẩy cụm + chế độ chọn nguồn). Đọc trực tiếp trong listener. */
object Prefs {
    // Giá trị thật nằm ở :core (NavSourceMode) để bộ quyết định không phải phụ thuộc Android chỉ vì hai
    // con số. Giữ alias ở đây nên caller cũ không đổi và dữ liệu đã lưu vẫn đọc đúng.
    const val AUTO = com.byd.clusternav.navigation.NavSourceMode.AUTO
    const val PREFER_GMAPS = com.byd.clusternav.navigation.NavSourceMode.PREFER_GMAPS
    const val PREFER_WAZE = com.byd.clusternav.navigation.NavSourceMode.PREFER_WAZE
    const val PREFER_VIETMAP = com.byd.clusternav.navigation.NavSourceMode.PREFER_VIETMAP

    private const val FILE = "clusternav_prefs"
    private const val K_ENABLED = "enabled"
    private const val K_SOURCE = "source_mode"
    private const val K_MARQUEE = "marquee"

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ★ 1.13 (Option B, owner 2026-08-13): MẶC ĐỊNH TẮT. Mở app KHÔNG đụng adb/dadb lúc khởi động (tránh đua
    // nhiều client dadb + tránh popup "Allow USB debugging" khi user chưa cần nav). Chỉ khi user gạt công tắc
    // BẬT mới tự cấp quyền notification (NavConnect.selfGrant) + kết nối. Quyền đã cấp PERSIST qua reboot nên
    // các lần bật sau không phải chạy adb lại (listener tự bind; RebindReceiver lo phần khởi động lại).
    fun enabled(ctx: Context): Boolean = sp(ctx).getBoolean(K_ENABLED, false)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_ENABLED, v).apply()

    fun sourceMode(ctx: Context): Int = sp(ctx).getInt(K_SOURCE, AUTO)
    fun setSourceMode(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_SOURCE, v).apply()

    /**
     * Nguồn biển báo tốc độ. **Chỉ còn MỘT nguồn có thật** nên đây là hằng, không phải lựa chọn.
     *
     * 2026-08-22: gỡ hẳn lựa chọn "Waze Mod (HLP)" khỏi code + UI. Đường đó đọc tag logcat `WazeHudLink`
     * của WazeMod, mà WazeMod chỉ phát tag này khi có **peer HUD BT/BLE** kết nối. Đo trên máy không có
     * HUD BLE, Waze ĐANG dẫn: **0 dòng**. Chọn nó = badge trắng im lặng, người dùng không hiểu vì sao.
     * Nguồn còn lại (widget VietMap) đã proven bằng data thật (50/60/70/80 + đếm lùi cự ly).
     *
     * Khoá prefs cũ (chuỗi "speed_source") KHÔNG còn được đọc; máy đã lưu giá trị Waze cũng tự về VietMap.
     */
    fun speedLimitSource(ctx: Context): SpeedLimitSource = SpeedLimitSource.VIETMAP

    // Nav-on-cluster: op 39 "simple navigation" (Giữa + ETA) là chế độ DUY NHẤT (owner chốt 2026-08-12).
    // Bỏ hẳn biến thể "nhỏ/ở trên" (không dò được opcode trên xe) + nút chọn mode + nút test trên UI.

    // ★ 1.14 (owner on-car): MẶC ĐỊNH BẬT lại marquee — tên đường >~8 ký tự bị firmware cụm hard-cut, nên cho
    // chạy cuộn PHẢI→TRÁI. Bước cuộn nay TÍNH THEO THỜI GIAN (ClusterBroadcaster.MARQUEE_STEP_MS, reset mỗi
    // đường mới) → đều, chậm, MƯỢT (bản cũ tăng scrollTick không đều theo emission → dựt). Có toggle UI (cb_marquee).
    fun marquee(ctx: Context): Boolean = sp(ctx).getBoolean(K_MARQUEE, true)    // true = chạy marquee mượt
    fun setMarquee(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_MARQUEE, v).apply()

    // Nav-on-cluster DISPLAY MODE — ghi SET_NAVI_SCREEN_STATUS_SET (0x4C10E015 · BYDAutoSettingDevice), đúng
    // menu OEM "Đơn giản / Màn hình nhỏ / Toàn màn hình / OFF" (mở khoá 2026-08-13 qua BydHal). op39 ch1000 KHÔNG
    // đổi được cái này (no-op trên xe). NavigationHudOwner đọc pref này mỗi frame → selector áp dụng LIVE.
    // ⚠️ value↔menu CHƯA map chắc trên xe: navopen=3 (rc=0, ứng viên "Toàn màn hình"); "Đơn giản" đoán=1 — dò trên xe.
    // Default = FULL(3) = value đã-proven rc=0 (ít nhất hiện nav ở GIỮA thay vì dải nhỏ ở đỉnh).
    const val NAV_SCREEN_OFF = 0
    const val NAV_SCREEN_SIMPLE = 1       // "Đơn giản" (đoán=1, CHƯA proven trên trim này); back-compat only — UI không ghi (TASK 4)
    const val NAV_SCREEN_SMALL = 2        // back-compat only; no longer user-selectable (TASK 4)
    const val NAV_SCREEN_FULL = 3         // PROVEN rc=0 (= BydHal.NAV_SCREEN_MODE_ON, navopen/AmapService=3 → nav ở GIỮA) — the ON value 'Bật (Giữa+ETA)' for the ON/OFF selector (TASK 4)
    private const val K_NAV_SCREEN_MODE = "nav_cluster_screen_mode"
    // TASK 4 (R3 · closeout-1.28): the selector is reduced to ON/OFF — on-car only OFF ever changed anything
    // (the 3 layout modes hit the no-root wall). The ON value is FULL(3) = the PROVEN rc=0 value (navopen /
    // AmapService use 3; it renders nav in the CENTRE "Giữa+ETA", not the small top strip). SIMPLE(1)/SMALL(2)
    // are unproven guesses on this trim, so the UI never writes them. Read-migration: any non-OFF stored value
    // (incl. legacy SIMPLE/SMALL) collapses to FULL so old installs land on 'Bật' with the proven value; OFF is
    // preserved. The SIMPLE/SMALL constants stay for back-compat — they are simply no longer written by the UI.
    fun navClusterScreenMode(ctx: Context): Int =
        when (sp(ctx).getInt(K_NAV_SCREEN_MODE, NAV_SCREEN_FULL)) {
            NAV_SCREEN_OFF -> NAV_SCREEN_OFF
            else -> NAV_SCREEN_FULL   // any non-OFF (incl. legacy SIMPLE/SMALL) → proven ON value 'Bật'
        }
    fun setNavClusterScreenMode(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_NAV_SCREEN_MODE, v).apply()

    // Cluster-lane output is independently switchable while the shared Navigation session/HUD remain active.
    fun lane(ctx: Context): Boolean = sp(ctx).getBoolean("lane", true)
    fun setLane(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("lane", v).apply()

    // ★ 2026-08-12 (owner "1B"): MẶC ĐỊNH BẬT lại "tự bù theo tốc độ". Noti GMaps thưa → gửi RAW làm cự ly đứng im
    // rồi nhảy khi tới ngã rẽ/điểm đến ("trễ"). Bật nội suy: TurnDistanceInterpolator trừ dần cự ly theo TỐC ĐỘ XE
    // thật (SpeedProvider) mỗi nhịp tim 400ms; bộ đọc màn Maps (accBooster → refine()) kéo mốc về số thật. Interpolator
    // đã bảo thủ (FACTOR 0.95, slew-limit 2 chiều, dừng→giữ số, maneuver→snap) nên không tái diễn "số nhảy tán loạn"
    // của bản 2026-07-13. Giữ toggle để TẮT nếu overlay cụm tự animate rồi đánh nhau (cần verify trên xe).
    fun interpolate(ctx: Context): Boolean = sp(ctx).getBoolean("interpolate", true)
    fun setInterpolate(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("interpolate", v).apply()

    // ★ HUD kính lái: T7 chỉ feeds request/output lifecycle vào HudMirrorController UNKNOWN/no-op.
    // Mặc định TẮT; không có direct HAL content write hoặc physical-OFF ownership in production.
    fun hud(ctx: Context): Boolean = sp(ctx).getBoolean("hud", false)
    fun setHud(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("hud", v).apply()

    // Booster đọc UI GMaps trên màn (accessibility) -> tinh chỉnh cự ly tới rẽ chính xác hơn noti.
    // Chỉ chạy khi GMaps đang HIỆN trên màn; bị app khác (YouTube) che -> tự câm, nội suy gánh tiếp.
    fun accBooster(ctx: Context): Boolean = sp(ctx).getBoolean("acc_booster", true)
    fun setAccBooster(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("acc_booster", v).apply()

    // Tự hiện NÚT NỔI (bong bóng chiếu) khi mở app / khởi động máy. Mặc định BẬT (user: "luôn hiện bubble").
    // Cần quyền overlay 1 lần; chưa cấp thì service tự báo. User tắt → lưu false.
    fun bubbleAuto(ctx: Context): Boolean = sp(ctx).getBoolean("bubble_auto", true)
    fun setBubbleAuto(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("bubble_auto", v).apply()

    // "Mượt UI head-unit": set 3 animation scale = 0.5 GLOBAL qua dadb lúc mở app (tweak hội BYD hay xài). Mặc định BẬT.
    // KHÔNG phải tăng tốc CPU — chỉ rút ngắn animation cho snappy. Tắt → app set lại 1.0.
    fun animOpt(ctx: Context): Boolean = sp(ctx).getBoolean("anim_opt", true)
    fun setAnimOpt(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("anim_opt", v).apply()

    // ★ 1.21 Item 1 (owner): "Tự khởi động nền" — nổ máy → app tự làm việc (nav lên cụm · voice-key · auto-cast)
    // mà KHÔNG bung MainActivity trên màn chính (bonus: né size-compat của dudu). MẶC ĐỊNH BẬT. Khi TẮT → giữ
    // hành vi 1.14 I5 (tự mở Home lúc nổ máy). RebindReceiver đọc cờ này lúc boot/OTA: BẬT → BootSetupService
    // (chạy nền, dời accessibility grant + re-assert làn cụm), TẮT → launchHome. KHÔNG đụng auto-cast (castBootWork).
    fun headlessAutostart(ctx: Context): Boolean = sp(ctx).getBoolean("headless_autostart", true)
    fun setHeadlessAutostart(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("headless_autostart", v).apply()

    // ─── T3 (1.13): Nút vật lý → Trợ lý giọng nói ───────────────────────────────────────────────
    // 1.19: KHÔNG thay chức năng gốc — onKeyEvent chỉ "nuốt" đúng keycode đã cấu hình, còn lại pass-through.
    // Bỏ cử chỉ (Nhấn/Nhấn-giữ) vì nút short/long ra keycode khác nhau. Đích lưu STRING (package/sentinel);
    // nút tự học lưu JSON. MẶC ĐỊNH TẮT. Xem VoiceKeyMatcher (:core).
    private const val K_VK_ENABLED = "voicekey_enabled"
    private const val K_VK_KEYCODE = "voicekey_keycode"
    private const val K_VK_TARGET = "voicekey_target"          // 1.19: STRING (package hoặc sentinel __ASSIST__/__RECOGNIZER__)
    private const val K_VK_LEARN = "voicekey_learn"
    private const val K_VK_CUSTOM = "voicekey_custom_buttons"  // 1.19: JSON [{"n":name,"k":keycode}] nút tự học
    private const val K_VK_BINDINGS = "voicekey_bindings"      // F3: JSON [{"k":keycode,"t":target}] danh sách gán
    const val VK_KEYCODE_DEFAULT = 328   // nút mic vô-lăng giữ trên xe này (đo on-car 2026-08-13). "Học phím mới" nếu xe khác.
    const val VK_TARGET_ASSIST = "__ASSIST__"
    const val VK_TARGET_RECOGNIZER = "__RECOGNIZER__"
    // 1.20: phát KEYCODE_VOICE_ASSIST (231) qua dadb shell (như app 8hare) → route tới trợ lý hệ thống.
    // Sạch hơn ACTION_ASSIST (không chooser, không nhầm intent). Chọn target này cũng đặt trợ lý hệ thống = Google/Gemini.
    const val VK_TARGET_GEMINI_KEY = "__VOICEKEY231__"
    const val VK_TARGET_DEFAULT = "ai.zalo.kiki.car"           // mặc định Kiki (khớp default cũ 0=Kiki)

    fun voiceKeyEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(K_VK_ENABLED, false)
    fun setVoiceKeyEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_VK_ENABLED, v).apply()

    /** CŨ (trước F3) — mã phím DUY NHẤT. Từ F3 chỉ còn dùng để **migrate** sang [voiceKeyBindings]. */
    fun voiceKeyCode(ctx: Context): Int = voiceKeyCode(sp(ctx))

    /** Bản nhận thẳng ô nhớ — xem ghi chú "vì sao có nạp chồng" ở [voiceKeyBindings]. */
    fun voiceKeyCode(p: SharedPreferences): Int = p.getInt(K_VK_KEYCODE, VK_KEYCODE_DEFAULT)

    /**
     * CŨ (trước F3) — đích DUY NHẤT: package name hoặc sentinel. Migrate cấu hình đời đầu (int ordinal →
     * string) rồi ghi lại. Từ F3 chỉ còn dùng để **migrate** sang [voiceKeyBindings].
     */
    fun voiceKeyTargetSpec(ctx: Context): String = voiceKeyTargetSpec(sp(ctx))

    /** Bản nhận thẳng ô nhớ — xem ghi chú "vì sao có nạp chồng" ở [voiceKeyBindings]. */
    fun voiceKeyTargetSpec(p: SharedPreferences): String =
        when (val raw = p.all[K_VK_TARGET]) {
            is String -> raw
            is Int -> (when (raw) { 1 -> "com.byd.autovoice"; 2 -> VK_TARGET_RECOGNIZER; 3 -> VK_TARGET_ASSIST; else -> VK_TARGET_DEFAULT })
                .also { p.edit().putString(K_VK_TARGET, it).apply() }
            else -> VK_TARGET_DEFAULT
        }

    // ─── F3 (owner 2026-08-24): DANH SÁCH gán (nhiều phím → nhiều app) ──────────────────────────
    // Owner: "chọn nút + chọn app xong → add, thì ra 1 dòng đã binding nút và app, xong có thể chọn thêm
    // add thêm, mình listen thì listen theo cái danh sách đã save đó thôi".
    // Đây là NGUỒN CHÂN LÝ DUY NHẤT cho khớp phím (NavAccessibilityService.onKeyEvent). Danh sách RỖNG ⇒
    // không phím nào bị nuốt, không app nào được mở — đúng ý "rỗng thì không có gì chạy".

    /**
     * Đọc danh sách gán. Lần đọc ĐẦU TIÊN trên một máy chưa có khoá danh sách sẽ **migrate cấu hình
     * một-cặp** của 1.19 rồi ghi xuống ngay — nâng cấp KHÔNG được làm mất cấu hình owner đang chạy.
     *
     * Điều kiện migrate nằm ở [VoiceKeyBindings.migrateLegacy] (thuần, test off-device được); ở đây chỉ
     * cấp cho nó **dấu vết thật** trong file prefs (`contains`), vì cả hai khoá cũ đều có giá trị mặc định
     * nên "đọc ra được" không chứng minh owner từng cấu hình.
     *
     * Sau khi ghi khoá danh sách (kể cả khi migrate ra RỖNG → ghi `"[]"`), migrate KHÔNG chạy lại: nếu
     * chạy lại thì owner xoá hết dòng gán rồi mở lại app sẽ thấy dòng cũ sống lại.
     *
     * ── VÌ SAO CÓ NẠP CHỒNG NHẬN THẲNG `SharedPreferences` (2026-08-24) ─────────────────────────
     * Bản chỉ-nhận-`Context` **không chạy được trong test off-device** (`getSharedPreferences` là API
     * Android; repo không dùng Robolectric), nên đường migrate — đúng chỗ nguy hiểm nhất, sai một lần là
     * **mất vĩnh viễn cấu hình owner** — trước đó chỉ được khoá bằng cách *quét chuỗi source*, tức vẫn
     * xanh nếu ai đó đổi thân hàm thành `writeVoiceKeyBindings(ctx, emptyList())`. Tách ô nhớ ra thành
     * tham số cho phép `VoiceKeyBindingMigrationTest` **chạy thật** cả 4 ca (chưa có khoá + có dấu vết /
     * chưa có khoá + không dấu vết / đã có `"[]"` / đã có danh sách) với một ô nhớ giả.
     * Bản `Context` chỉ còn là lớp vỏ một dòng — không còn logic nào nằm ngoài tầm test.
     */
    fun voiceKeyBindings(ctx: Context): List<VoiceKeyBinding> = voiceKeyBindings(sp(ctx))

    fun voiceKeyBindings(p: SharedPreferences): List<VoiceKeyBinding> {
        VoiceKeyBindingStore.rawOrNull(p, K_VK_BINDINGS)?.let { return VoiceKeyBindingStore.decode(it) }
        val migrated = VoiceKeyBindings.migrateLegacy(
            hasLegacyKeyCode = p.contains(K_VK_KEYCODE),
            hasLegacyTarget = p.contains(K_VK_TARGET),
            enabled = p.getBoolean(K_VK_ENABLED, false),
            keyCode = voiceKeyCode(p),
            targetSpec = voiceKeyTargetSpec(p),
        )
        writeVoiceKeyBindings(p, migrated)
        return migrated
    }

    /**
     * Thêm một dòng gán. Mã phím đã được gán ⇒ **GHI ĐÈ** (giữ nguyên vị trí dòng) và trả về đích CŨ để UI
     * báo cho owner biết đã thay cái gì — cấm im lặng. Dòng mới ⇒ trả `null`.
     */
    fun addVoiceKeyBinding(ctx: Context, keyCode: Int, targetSpec: String): String? =
        addVoiceKeyBinding(sp(ctx), keyCode, targetSpec)

    fun addVoiceKeyBinding(p: SharedPreferences, keyCode: Int, targetSpec: String): String? {
        val result = VoiceKeyBindings.put(voiceKeyBindings(p), keyCode, targetSpec)
        writeVoiceKeyBindings(p, result.bindings)
        return result.replaced
    }

    /** Xoá dòng gán của [keyCode] (nút xoá trên từng dòng). */
    fun removeVoiceKeyBinding(ctx: Context, keyCode: Int) = removeVoiceKeyBinding(sp(ctx), keyCode)

    fun removeVoiceKeyBinding(p: SharedPreferences, keyCode: Int) =
        writeVoiceKeyBindings(p, VoiceKeyBindings.remove(voiceKeyBindings(p), keyCode))

    private fun writeVoiceKeyBindings(p: SharedPreferences, list: List<VoiceKeyBinding>) =
        VoiceKeyBindingStore.write(p, K_VK_BINDINGS, list)

    /** "Học phím mới": khi BẬT, onKeyEvent kế tiếp bắt keycode nút vừa bấm rồi tự tắt cờ + báo Activity đặt tên. */
    fun voiceKeyLearn(ctx: Context): Boolean = sp(ctx).getBoolean(K_VK_LEARN, false)
    fun setVoiceKeyLearn(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_VK_LEARN, v).apply()

    /** Nút tự học (tên, keycode) — lưu JSON để dropdown dựng lại + xoá được. */
    fun voiceKeyCustomButtons(ctx: Context): List<Pair<String, Int>> = runCatching {
        val arr = org.json.JSONArray(sp(ctx).getString(K_VK_CUSTOM, "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); o.getString("n") to o.getInt("k") }
    }.getOrDefault(emptyList())
    fun addVoiceKeyCustomButton(ctx: Context, name: String, code: Int) =
        writeCustomButtons(ctx, voiceKeyCustomButtons(ctx).filterNot { it.second == code } + (name to code))
    fun removeVoiceKeyCustomButton(ctx: Context, code: Int) =
        writeCustomButtons(ctx, voiceKeyCustomButtons(ctx).filterNot { it.second == code })
    private fun writeCustomButtons(ctx: Context, items: List<Pair<String, Int>>) {
        val arr = org.json.JSONArray()
        items.forEach { arr.put(org.json.JSONObject().put("n", it.first).put("k", it.second)) }
        sp(ctx).edit().putString(K_VK_CUSTOM, arr.toString()).apply()
    }

    // ─── Nhật ký chi tiết (verbose) + miễn trừ lần đầu (closeout 1.28) ──────────────────────────
    // Verbose-log gate for the app's OWN diagnostics (GMaps notification CSV [NavNotifLog]/[NavNotifRawLog],
    // ManeuverSignature notes, per-frame logs) + the [DiagStorageCap] periodic sweep. Controlled SOLELY by the
    // build flag now: the runtime UI switch + hidden long-press were removed 2026-08-28 (the VietMap/Waze
    // capture they collected is gone — see NavAccessibilityService/NavLog), so nothing writes this pref anymore.
    // MẶC ĐỊNH TẮT. NavLog mirrors it into a @Volatile field so hot paths never touch SharedPreferences.
    private const val K_NAV_VERBOSE_LOG = "nav_verbose_log"
    // Default = BuildConfig.DIAG_LOG. In a NORMAL release/debug build DIAG_LOG is FALSE → this returns false
    // → A8/D3 preserved (normal use collects NO logs/PNGs, privacy default unchanged). Only a DIAG build
    // (`-PdiagLog=true`) makes DIAG_LOG true → verbose pre-ON for a teammate's drive-test. The pref key is kept
    // as the backing store but is now read-only (no setter): with the toggle gone it always resolves to the
    // DIAG_LOG default. Read by [NavLog.init]; that gate is load-bearing for the remaining GMaps diagnostics.
    fun navVerboseLog(ctx: Context): Boolean = sp(ctx).getBoolean(K_NAV_VERBOSE_LOG, BuildConfig.DIAG_LOG)

    // Miễn trừ lần đầu (no-warranty / không liên kết BYD / tự chịu rủi ro) — hiện MỘT lần rồi ghim cờ.
    private const val K_DISCLAIMER_SHOWN = "disclaimer_shown"
    fun disclaimerShown(ctx: Context): Boolean = sp(ctx).getBoolean(K_DISCLAIMER_SHOWN, false)
    fun setDisclaimerShown(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_DISCLAIMER_SHOWN, v).apply()

    // ─── Cluster speed-limit badge overlay: ABSOLUTE POSITION + SIZE (persisted, live-adjustable) ─
    // 2026-08-17 (spec speed-badge-placement-vietmap-logging §4.3): the old 4-corner model (corner + dp
    // nudge) was REPLACED by an absolute CENTRE in cluster px so the driver can drag the badge anywhere on
    // the cluster, not just to a corner the cast app kept covering. badgeCenterX/Y is the CENTRE of the badge
    // in display-1 pixel coords; the overlay clamps it on-screen (BadgeLayout.clampCenter) and converts to a
    // TOP|LEFT x/y (BadgeLayout.topLeftFromCenter). Size still clamps 60..240 dp on BOTH read & write via the
    // tested BadgeLayout.clampSizeDp so a corrupt stored value can never blow the overlay up or shrink it away.
    private const val K_BADGE_SIZE_DP = "badge_size_dp"
    private const val K_BADGE_CENTER_X = "badge_center_x"
    private const val K_BADGE_CENTER_Y = "badge_center_y"
    private const val K_BADGE_ENABLED = "badge_enabled"

    // ★ Badge on/off: MẶC ĐỊNH TẮT (owner 2026-08-28: mặc định tắt biển báo tốc độ VietMap trên cụm; trước
    // đây 2026-08-18 mặc định BẬT). Gate riêng cho biển báo tốc độ trên CỤM (overlay display 1) — độc lập với
    // nguồn tốc độ/HUD. Khi TẮT: SpeedBadgeOverlay.show() gỡ overlay + không attach (real pipeline lẫn debug
    // force-show đều tôn trọng vì cả hai đi qua show()). Đọc trực tiếp trong overlay trên main handler
    // (SharedPreferences cache sẵn nên rẻ, không chạm notification thread).
    fun badgeEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(K_BADGE_ENABLED, false)
    fun setBadgeEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_BADGE_ENABLED, v).apply()

    // ★ Biển "giới hạn sắp tới" (spec upcoming-speed-limit-badge, owner 2026-08-18): MẶC ĐỊNH BẬT (theo tiền lệ
    // badge). Gate riêng cho biển nhỏ (~70%) + nhãn cự ly đếm lùi, vẽ NGAY DƯỚI badge chính trên CỤM. KHÔNG có
    // ngưỡng cự ly riêng (OQ2 — hiện/ẩn theo VietMap). TẮT → SpeedBadgeOverlay không bao giờ vẽ biển sắp-tới.
    private const val K_SHOW_UPCOMING_BADGE = "show_upcoming_badge"
    fun showUpcomingBadge(ctx: Context): Boolean = sp(ctx).getBoolean(K_SHOW_UPCOMING_BADGE, true)
    fun setShowUpcomingBadge(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_SHOW_UPCOMING_BADGE, v).apply()
    // B3.20 — road-alert / speed-camera chip toggle. Default OFF: it adds a THIRD element on the cluster, so it
    // stays opt-in and never disturbs the owner's existing badge layout until turned on.
    private const val K_SHOW_ALERT_CHIP = "show_alert_chip"
    fun showAlertChip(ctx: Context): Boolean = sp(ctx).getBoolean(K_SHOW_ALERT_CHIP, false)
    fun setShowAlertChip(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_SHOW_ALERT_CHIP, v).apply()

    // ─── VietMap bubble-on-cluster toggle (owner 2026-08-28) ────────────────────────────────────
    // Gate cho VỊ TRÍ bong bóng VietMap trên cụm (panel kéo-thả VmBubblePlacementView + VmOverlayPosition).
    // MẶC ĐỊNH TẮT (opt-in). Giống badge tốc độ: khi BẬT sẽ auto-start VietMap MỘT LẦN ([VietMapAutostart],
    // dedup bằng pidof) để bản mod VietMap có mặt mà nhận broadcast VM_BUBBLE_POS. runNow() auto-start nếu
    // badge HOẶC cờ này bật — hai cờ độc lập, dedup pidof đảm bảo chỉ start một lần dù cả hai bật.
    private const val K_VM_BUBBLE_ENABLED = "vm_bubble_enabled"
    fun vmBubbleEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(K_VM_BUBBLE_ENABLED, false)
    fun setVmBubbleEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_VM_BUBBLE_ENABLED, v).apply()

    // One-time guard: the modded VietMap draws the cluster bubble, and the BYD IVI refuses an overlay from
    // any package NOT in the global CSV `byd_float_app_list` (the "Hệ thống IVI không hỗ trợ hoạt động này"
    // toast). [VietMapAutostart] appends VietMap to that list + grants SYSTEM_ALERT_WINDOW over the dadb
    // uid-shell ONCE — the same proven recipe [AssistantLauncher] uses for Google/Gemini (see
    // com.byd.clusternav.core.FloatAppList). This flag pins that it ran so the recipe is not re-applied every
    // autostart; it is set ONLY on success, so a failed attempt (e.g. no dadb loopback yet) retries next time.
    // Mirrors the doze one-time guard (SimpleCastRuntime.doze_whitelist_applied). MẶC ĐỊNH FALSE.
    private const val K_VM_FLOAT_WHITELIST_APPLIED = "vm_float_whitelist_applied"
    fun vmFloatWhitelistApplied(ctx: Context): Boolean = sp(ctx).getBoolean(K_VM_FLOAT_WHITELIST_APPLIED, false)
    fun setVmFloatWhitelistApplied(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean(K_VM_FLOAT_WHITELIST_APPLIED, v).apply()
    // Legacy keys (4-corner model) — read once by [migrateBadgeIfNeeded] to seed the centre, never written.
    private const val K_BADGE_CORNER = "badge_corner"
    private const val K_BADGE_DX = "badge_dx"
    private const val K_BADGE_DY = "badge_dy"

    // Default CENTRE = top-right-ish on the default 1920×720 cluster (1920−140, 80): visible, out of the way of
    // the centre nav/ETA. The overlay re-clamps with the real display size + density, so it is always on-screen.
    const val BADGE_DEFAULT_CENTER_X = 1780
    const val BADGE_DEFAULT_CENTER_Y = 80
    // Default cluster dims used ONLY for the one-time legacy migration (real dims come from display 1 at render).
    const val BADGE_MIGRATE_CLUSTER_W = 1920
    const val BADGE_MIGRATE_CLUSTER_H = 720

    fun badgeSizeDp(ctx: Context): Int =
        com.byd.clusternav.speedbadge.BadgeLayout.clampSizeDp(
            sp(ctx).getInt(K_BADGE_SIZE_DP, com.byd.clusternav.speedbadge.BadgeLayout.SIZE_DEFAULT_DP),
        )
    fun setBadgeSizeDp(ctx: Context, v: Int) =
        sp(ctx).edit().putInt(K_BADGE_SIZE_DP, com.byd.clusternav.speedbadge.BadgeLayout.clampSizeDp(v)).apply()

    fun badgeCenterX(ctx: Context): Int {
        migrateBadgeIfNeeded(ctx)
        return sp(ctx).getInt(K_BADGE_CENTER_X, BADGE_DEFAULT_CENTER_X)
    }
    fun setBadgeCenterX(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_BADGE_CENTER_X, v).apply()

    fun badgeCenterY(ctx: Context): Int {
        migrateBadgeIfNeeded(ctx)
        return sp(ctx).getInt(K_BADGE_CENTER_Y, BADGE_DEFAULT_CENTER_Y)
    }
    fun setBadgeCenterY(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_BADGE_CENTER_Y, v).apply()

    /**
     * ONE-TIME migration from the legacy 4-corner model to an absolute centre. Fires only when the old
     * corner key is present AND the new centre keys are absent, so it runs at most once per install and never
     * clobbers a user's chosen absolute position (a fresh install just uses the defaults). Computes the centre
     * from old corner + dp nudge + size on the default 1920×720 cluster, then clamps it on-screen. Approximate
     * by design (treats stored dp as px on the migration cluster) — it only needs to keep existing users near
     * their old corner rather than jumping to the default.
     */
    private fun migrateBadgeIfNeeded(ctx: Context) {
        val p = sp(ctx)
        if (!p.contains(K_BADGE_CORNER)) return                                   // fresh install → defaults
        if (p.contains(K_BADGE_CENTER_X) || p.contains(K_BADGE_CENTER_Y)) return  // already migrated / user-set
        val corner = p.getInt(K_BADGE_CORNER, 1)                                  // legacy ids: 0=TL,1=TR,2=BL,3=BR
        val dx = p.getInt(K_BADGE_DX, 24)
        val dy = p.getInt(K_BADGE_DY, 24)
        val sizePx = com.byd.clusternav.speedbadge.BadgeLayout.clampSizeDp(
            p.getInt(K_BADGE_SIZE_DP, com.byd.clusternav.speedbadge.BadgeLayout.SIZE_DEFAULT_DP),
        )
        val half = sizePx / 2
        val isLeft = corner == 0 || corner == 2
        val isBottom = corner == 2 || corner == 3
        val cx = if (isLeft) dx + half else BADGE_MIGRATE_CLUSTER_W - dx - half
        val cy = if (isBottom) BADGE_MIGRATE_CLUSTER_H - dy - half else dy + half
        val (ccx, ccy) = com.byd.clusternav.speedbadge.BadgeLayout.clampCenter(
            cx, cy, sizePx, BADGE_MIGRATE_CLUSTER_W, BADGE_MIGRATE_CLUSTER_H,
        )
        p.edit().putInt(K_BADGE_CENTER_X, ccx).putInt(K_BADGE_CENTER_Y, ccy).apply()
    }

    // ─── Ghế: làm mát / sưởi tự động (spec seat-comfort-auto) ────────────────────────────────────
    // MẶC ĐỊNH TẮT — cài mới KHÔNG làm gì (không đụng HAL) tới khi owner tự bật. `mode` int: 0=COOL (làm
    // mát, mặc định), 1=HEAT (sưởi) — khớp SeatComfort.SeatMode.ordinal. `level` mỗi ghế: 0=Tắt/1=Mức1/2=Mức2.
    // Áp bằng SeatComfortApplier (~5s sau khi mở app / boot). Làm mát ↔ sưởi loại trừ nhau (xe reset cái kia).
    private const val K_SEAT_ENABLED = "seat_comfort_enabled"
    private const val K_SEAT_MODE = "seat_comfort_mode"
    fun seatComfortEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(K_SEAT_ENABLED, false)
    fun setSeatComfortEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_SEAT_ENABLED, v).apply()
    fun seatComfortMode(ctx: Context): Int = sp(ctx).getInt(K_SEAT_MODE, 0)               // 0=COOL default
    fun setSeatComfortMode(ctx: Context, v: Int) = sp(ctx).edit().putInt(K_SEAT_MODE, v).apply()
    fun seatComfortLevel(ctx: Context, seatIndex: Int): Int = sp(ctx).getInt("seat_level_$seatIndex", 0)
    fun setSeatComfortLevel(ctx: Context, seatIndex: Int, v: Int) =
        sp(ctx).edit().putInt("seat_level_$seatIndex", v).apply()

    // ─── Lọc bụi mịn PM2.5 tự động (spec pm25-auto-filter) ───────────────────────────────────────
    // MẶC ĐỊNH TẮT — cài mới KHÔNG đụng HAL tới khi owner tự bật. BẬT ⇒ Pm25FilterApplier gọi
    // enablePurificationFunctionPrompt(0)+setAutoCleanAirState(1) (~5s sau mở app / boot) để xe tự lọc
    // LIÊN TỤC, KHÔNG hiện popup; TẮT ⇒ setAutoCleanAirState(0)+enablePurificationFunctionPrompt(1) (khôi
    // phục). KHÔNG có ngưỡng chỉnh trong UI (dùng Pm25Filter.DEFAULT_THRESHOLD=HEAVY cho lọc-ngay lúc bật).
    private const val K_PM25_ENABLED = "pm25_filter_enabled"
    fun pm25FilterEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(K_PM25_ENABLED, false)
    fun setPm25FilterEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_PM25_ENABLED, v).apply()

    // Toggle theo module (key namespaced "mod_" — không thể đụng các key lõi ở trên). Mặc định TẮT
    // (experiment phải bật tay). Key mồ côi sau khi xoá module = dead data vô hại, không cần dọn.
    fun moduleEnabled(ctx: Context, title: String): Boolean =
        sp(ctx).getBoolean("mod_" + title.hashCode(), false)
    fun setModuleEnabled(ctx: Context, title: String, v: Boolean) =
        sp(ctx).edit().putBoolean("mod_" + title.hashCode(), v).apply()
}
