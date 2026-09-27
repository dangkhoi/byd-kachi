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

    /** `internal` (không `private`) từ 2.76: `PrefsBadge.kt` dùng lại đúng hàm này — một hàm, một literal tên tệp. */
    internal fun sp(ctx: Context) =
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
    // mà KHÔNG bung màn hình nào trên màn chính (bonus: né size-compat của dudu). MẶC ĐỊNH BẬT. Khi TẮT → giữ
    // hành vi 1.14 I5 (tự mở Home lúc nổ máy). RebindReceiver đọc cờ này lúc boot/OTA: BẬT → BootSetupService
    // (chạy nền, dời accessibility grant + re-assert làn cụm), TẮT → launchHome. KHÔNG đụng auto-cast (castBootWork).
    fun headlessAutostart(ctx: Context): Boolean = sp(ctx).getBoolean("headless_autostart", true)
    fun setHeadlessAutostart(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("headless_autostart", v).apply()

    // ─── UX-OVERHAUL WP1 · R1.3 — "Kính thật (làm mờ nền)" ─────────────────────────────────────────
    // Glass GIẢ (mặc định) = gradient dọc + fill trong mờ, 0 mép/viền/blur (KachiTheme.surface). Glass THẬT (option) =
    // RenderEffect.createBlurEffect làm mờ nền phía sau (chỉ API 31+; API thấp lùi về giả — KachiGlassMode). MẶC
    // ĐỊNH TẮT: nền GPU đầu máy DiLink yếu, và [ĐO] xe là API 29 nên glass-thật ở đó luôn lùi về giả. Theo XE.
    fun glassReal(ctx: Context): Boolean = sp(ctx).getBoolean("ui_glass_real", false)
    fun setGlassReal(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("ui_glass_real", v).apply()

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

    /**
     * V1 pha NGHE — đích *"Kachi nghe"*: phím vô-lăng mở **phiên nghe của chính Kachi**, không mở app nào.
     *
     * ## Vì sao là một sentinel THỨ TƯ, không phải tên gói của chính mình
     * Đặt `com.byd.launcher` làm đích thì [com.byd.clusternav.modules.voicekey.AssistantLauncher] sẽ đi nhánh
     * "mở app theo launch-intent" — tức về màn chính và **không** nghe gì. Ý nghĩa *"mở phiên nghe"* không phải
     * là *"mở app Kachi"*, nên nó phải có mã riêng, đúng như ba sentinel kia.
     *
     * ⚠ **KHÔNG** đổi [VK_TARGET_DEFAULT]: nút 328 trên vô-lăng owner vẫn thuộc Kiki và đang chạy tốt
     * (CLAUDE.md §6 — không đảo đường đã chạy tốt ngoài hiện trường). Đây chỉ là một dòng **thêm vào danh sách
     * chọn**; ai muốn đổi thì tự gán.
     */
    const val VK_TARGET_KACHI_VOICE = "__KACHI_VOICE__"

    // ─── V1 pha NGHE: nút mic trên thanh trạng thái ─────────────────────────────────────────────
    // Khoá THEO XE (không theo hồ sơ), cùng họ với `headless_autostart` ở trên: nó phụ thuộc thứ thuộc về
    // MÁY — mô hình nhận dạng đã tải hay chưa — chứ không phụ thuộc người đang lái. Mặc định BẬT: thanh trên
    // chỉ vẽ nút này khi mô hình đã có (xem `KachiTopStrip.voicePill`), nên trên máy chưa tải nó vô hình,
    // còn trên máy đã tải thì người ta vừa chủ động tải xong — giấu đi mới là bất ngờ.
    private const val K_VOICE_PILL = "voice_mic_pill"

    fun voiceMicPill(ctx: Context): Boolean = sp(ctx).getBoolean(K_VOICE_PILL, true)
    fun setVoiceMicPill(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_VOICE_PILL, v).apply()

    // ─── V1 pha NÓI · R4 (spec `docs/specs/kachi-voice-feedback.html` T9) ──────────────────────
    // Hai công tắc THEO XE, cùng họ `voice_mic_pill` ngay trên: cái quyết định chúng trả lời được hay không là
    // **máy này có giọng gì** (engine hệ thống · gói offline đã lắp chưa), không phải người đang lái. Để chúng
    // theo hồ sơ nghĩa là đổi hồ sơ xong loa im, mà không ai hiểu vì sao (`ProfileScope.DEVICE_KEYS` ghi lý do).
    private const val K_VOICE_SPEAK = "voice_speak_replies"
    private const val K_VOICE_OFFLINE = "voice_prefer_offline"

    /**
     * Có ĐỌC câu trả lời thành tiếng không. **Mặc định BẬT** — R1 của spec: câu trả lời chỉ hiện chữ thì người
     * lái phải rời mắt khỏi đường để đọc nó, tức đúng thứ mà một trợ lý giọng nói sinh ra để khỏi phải làm.
     * Tắt vẫn còn nguyên tấm chữ + âm báo, không mất chức năng nào.
     */
    fun voiceSpeakReplies(ctx: Context): Boolean = sp(ctx).getBoolean(K_VOICE_SPEAK, true)
    fun setVoiceSpeakReplies(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_VOICE_SPEAK, v).apply()

    /**
     * Ưu tiên **giọng offline tại máy** (gói Piper) hơn máy đọc của hệ thống. Mặc định TẮT: đường hệ thống rẻ
     * hơn hẳn (0 byte đĩa, 0 byte RAM) và trên đầu xe có sẵn `vi-VN` thì nó đọc ngay. Công tắc chỉ có tác dụng
     * khi gói đã lắp — `VoiceSpeakerSelector` tự lùi về đường còn dùng được, không bao giờ im lặng vì một cờ.
     */
    fun voicePreferOffline(ctx: Context): Boolean = sp(ctx).getBoolean(K_VOICE_OFFLINE, false)
    fun setVoicePreferOffline(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_VOICE_OFFLINE, v).apply()

    // ─── OQ4 · ĐỌC câu hỏi xác nhận rồi mới mở micro — **MẶC ĐỊNH TẮT** (owner chốt 2026-09-16) ───
    private const val K_VOICE_ASK_ALOUD = "voice_ask_aloud"

    /**
     * Có đọc to **câu hỏi xác nhận** (rồi mới mở micro) không. Mặc định **false**.
     *
     * ## Vì sao một công tắc riêng, không đi chung [voiceSpeakReplies]
     * Hai việc khác hẳn nhau về cái giá của lỗi. Đọc **phản hồi** (R4) xảy ra khi việc đã xong — đọc thừa chỉ tốn
     * hai giây. Đọc **câu hỏi** thì nằm ngay trước một lượt mở micro, nên nó kéo theo cả chuỗi *đọc → chờ mốc
     * xong → mở micro*: dài hơn, và có thêm một đường để hỏng. Owner 2026-09-16 chốt **KHÔNG** đọc câu hỏi (chỉ
     * đọc phản hồi sau lệnh) ⇒ mặc định tắt.
     *
     * Đường mã của OQ4 (`VoiceSession.askAloudThenListen` + hợp đồng `VoiceSpeaker.speak(text, onDone)`) **giữ
     * nguyên**, không gỡ: nó đã có bài canh, và quyết định này là một **lựa chọn hành vi** chứ không phải một
     * kết luận *"cơ chế ấy sai"*. Chưa có hàng trong Cài đặt (owner xếp vào batch sau, cùng mục *"chọn nút nào
     * phải hỏi"*); tới lúc đó khoá này lên UI và ra khỏi [SettingsCatalog.CLUSTERNAV_HIDDEN_KEYS].
     */
    fun voiceAskAloud(ctx: Context): Boolean = sp(ctx).getBoolean(K_VOICE_ASK_ALOUD, false)
    fun setVoiceAskAloud(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_VOICE_ASK_ALOUD, v).apply()

    // ─── APP DẪN ĐƯỜNG MẶC ĐỊNH (owner 2026-09-18) — nói "dẫn đường" không nêu app thì dùng cái này ───
    private const val K_VOICE_NAV_APP = "voice_nav_default_app"

    /**
     * Mã app dẫn đường MẶC ĐỊNH (`gmaps`/`vietmap`/`waze`) khi câu KHÔNG nêu tên app. Mặc định **`gmaps`** —
     * đường tin cậy nhất (nhận CHỮ thẳng, Google tự geocode, không kẹt mạng xe). Owner đổi được trong Cài đặt ›
     * Dẫn đường. Giá trị = `VoiceAppTargets` key; `VoiceTargetDispatch` tự lùi về [VoiceAppTargets] nếu app chưa cài.
     */
    fun voiceNavDefaultApp(ctx: Context): String = sp(ctx).getString(K_VOICE_NAV_APP, "gmaps") ?: "gmaps"
    fun setVoiceNavDefaultApp(ctx: Context, key: String) = sp(ctx).edit().putString(K_VOICE_NAV_APP, key).apply()

    // ─── APP NHẠC MẶC ĐỊNH (owner 2026-09-21) — nói "phát nhạc" không nêu app + không có nhạc đang phát thì dùng ───
    private const val K_VOICE_MUSIC_APP = "voice_music_default_app"

    /**
     * Mã app nhạc MẶC ĐỊNH khi câu KHÔNG nêu app. Mặc định **rỗng** = "tự chọn" (giữ hành vi cũ: app đang phát,
     * rồi app nhạc đầu tiên đã cài). Owner đổi trong Cài đặt › Giọng nói. Giá trị = `VoiceAppTargets` key
     * (`ytmusic`/`spotify`/`zing`/…); `VoiceTargetDispatch` tự lùi nếu app chưa cài.
     */
    fun voiceMusicDefaultApp(ctx: Context): String = sp(ctx).getString(K_VOICE_MUSIC_APP, "") ?: ""
    fun setVoiceMusicDefaultApp(ctx: Context, key: String) = sp(ctx).edit().putString(K_VOICE_MUSIC_APP, key).apply()

    // "Hey Kachi" wake-word (W-WAKE) — theo XE (ProfileScope.DEVICE_KEYS), mặc định **TẮT** (nghe nền = rủi ro CPU
    // → opt-in). Câu gọi = preset id ([VoiceWakePhrase]). VoiceWakeService.sync() đọc cờ này để bật/tắt FGS.
    //
    // ⚠ [P2 2026-09-19] Cờ owner `voice_wake_enabled` nằm trong `clusternav_prefs` (tệp CHUNG, 38 key = mọi
    // setting launcher). MODE_PRIVATE **không** an toàn đa-tiến-trình: nếu tiến trình `:wake` ghi tệp này bằng
    // map-RAM cũ của nó, `apply()` ghi đè cả tệp ⇒ **xoá mọi setting launcher đã đổi từ lúc :wake khởi động**.
    // ⇒ `:wake` CHỈ được ghi marker RIÊNG [wakeDisabledMarker]; `clusternav_prefs` chỉ tiến trình launcher ghi.
    //
    // ⚠ [SOÁT 2026-09-19] Marker là TỆP RỖNG, KHÔNG phải một tệp prefs thứ hai: `SharedPreferences` cache map
    // theo tiến trình và KHÔNG thấy tiến trình khác ghi ⇒ nếu cờ tự-tắt nằm trong prefs, launcher (đã cache)
    // đọc lại vẫn thấy cũ ⇒ công tắc hiện ON dù bộ nghe đã tắt. `File.exists()` đọc thẳng hệ tệp (không cache)
    // ⇒ launcher thấy NGAY. Một tệp create/delete = op FS đơn, an toàn hai tiến trình.
    private fun wakeDisabledMarker(ctx: Context) =
        java.io.File(ctx.applicationContext.filesDir, "kachi_wake_disabled")

    /** Cầu chì false-accept: `:wake` tự-tắt bằng cách tạo marker RIÊNG (KHÔNG đụng `clusternav_prefs`). */
    fun wakeServiceDisabled(ctx: Context): Boolean =
        runCatching { wakeDisabledMarker(ctx).exists() }.getOrDefault(false)
    fun setWakeServiceDisabled(ctx: Context, disabled: Boolean) {
        runCatching { wakeDisabledMarker(ctx).let { if (disabled) it.createNewFile() else it.delete() } }
    }

    /** BẬT-HIỆU-LỰC = owner bật (`clusternav_prefs`) **VÀ** service chưa tự-tắt (marker `kachi_wake_disabled`). */
    fun wakeEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean("voice_wake_enabled", false) && !wakeServiceDisabled(ctx)

    /** CHỈ tiến trình LAUNCHER gọi (công tắc Cài đặt). Bật lại ⇒ xoá marker tự-tắt của `:wake` để service chạy lại. */
    fun setWakeEnabled(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("voice_wake_enabled", on).apply()
        if (on) setWakeServiceDisabled(ctx, false)
    }
    fun wakePhraseId(ctx: Context): String = sp(ctx).getString("voice_wake_phrase", "hey_kachi") ?: "hey_kachi"
    fun setWakePhraseId(ctx: Context, id: String) = sp(ctx).edit().putString("voice_wake_phrase", id).apply()

    /** Engine wake: true = ASR no-train (mặc định, owner 2026-09-22), false = KWS gigaspeech. */
    fun wakeEngineAsr(ctx: Context): Boolean = sp(ctx).getBoolean("voice_wake_engine_asr", true)
    fun setWakeEngineAsr(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("voice_wake_engine_asr", v).apply()

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

    // ─── Biển báo tốc độ trên cụm + bong bóng VietMap → `PrefsBadge.kt` (tách THUẦN theo trần 500 dòng, L6-debt
    // 2026-09-27): hàm mở rộng của [Prefs] cùng tệp `clusternav_prefs` qua [sp] (cùng khuôn `PrefsAutomation`/`PrefsInputd`).
    // Bốn hằng công khai dưới đây ở lại vì chúng là API của chính `Prefs`.
    // Default CENTRE = top-right-ish on the default 1920×720 cluster (1920−140, 80): visible, out of the way of
    // the centre nav/ETA. The overlay re-clamps with the real display size + density, so it is always on-screen.
    const val BADGE_DEFAULT_CENTER_X = 1780
    const val BADGE_DEFAULT_CENTER_Y = 80
    // Default cluster dims used ONLY for the one-time legacy migration (real dims come from display 1 at render).
    const val BADGE_MIGRATE_CLUSTER_W = 1920
    const val BADGE_MIGRATE_CLUSTER_H = 720

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

    // ─── Tự LẤY GIÓ TRONG khi nổ máy (W3 — spec kachi-unified-capability-tile §4.5) ───────────────
    // MẶC ĐỊNH TẮT — cài mới KHÔNG đụng HAL tới khi owner tự bật. BẬT ⇒ RecircApplier bật chế độ lấy gió
    // trong ~5s sau nổ máy (xe quên mỗi lần khởi động; đi trong phố lấy gió ngoài là hít khói).
    // ⚠ MỨC BẰNG CHỨNG: nút này ở tier OVERDRIVE (đọc từ mã nguồn khác), CHƯA kiểm trên xe owner — KHÁC ghế
    // mát và lọc bụi (đã chạy thật). Bật xong vẫn có thể xe không làm gì; chỉ trên xe mới biết.
    private const val K_RECIRC_ON_START = "recirc_on_start_enabled"
    fun recircOnStartEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(K_RECIRC_ON_START, false)
    fun setRecircOnStartEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_RECIRC_ON_START, v).apply()

    // ─── Công tắc ẨN: ép đường lùi của CHẠM trong ô (1.69, spec kachi-open-app-correctly §4.6) ────
    // MẶC ĐỊNH TẮT. Bật ⇒ `InputDaemonClient` KHÔNG khởi daemon bơm chạm và mọi cú chạm trong ô đi đường lùi
    // theo cử chỉ (`GestureFallback` + `input -d`). Tồn tại vì một lý do DUY NHẤT: trên máy ảo daemon lên bình
    // thường, nên nếu không ép được thì đúng cái nhánh mà XE đang mắc kẹt ([ĐO xe 2026-09-16] §9.1: daemon không
    // lên lần nào) sẽ chỉ kiểm được bằng... một chiếc xe. Không có bề mặt UI (xem SettingsCatalog HIDDEN_KEYS) —
    // đặt bằng `run-as` trên bản vehicleTest, đọc lại bằng cầu kiểm thử (`state.inputd` / `prefs`).
    // Đọc MỘT lần mỗi tiến trình ở AppContainer ⇒ đổi xong phải khởi động lại app.
    // inputd (công tắc ẩn `inputd_disabled` + token TCP loopback) tách sang `PrefsInputd.kt` (trần 500 dòng).

    // Toggle theo module (key namespaced "mod_" — không thể đụng các key lõi ở trên). Mặc định TẮT
    // (experiment phải bật tay). Key mồ côi sau khi xoá module = dead data vô hại, không cần dọn.
    fun moduleEnabled(ctx: Context, title: String): Boolean =
        sp(ctx).getBoolean("mod_" + title.hashCode(), false)
    fun setModuleEnabled(ctx: Context, title: String, v: Boolean) =
        sp(ctx).edit().putBoolean("mod_" + title.hashCode(), v).apply()
}
