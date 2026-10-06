package com.byd.clusternav.launcher.voice

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.launcher.VoiceDispatcher
import com.byd.clusternav.system.PackageQueries
import com.byd.clusternav.Prefs
import com.byd.clusternav.voiceConfirmIds

/**
 * ═══ V1 · MỘT CHỖ DỰNG CẦU `VoiceDispatcher` — HAI BỀ MẶT, MỘT BỘ DÂY ════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R6 (gõ) · R11 (nói).
 *
 * ## Vì sao tách ra khi trước đó chỉ có một chỗ gọi
 * Pha NGHE thêm bề mặt **thứ hai** dựng [VoiceDispatcher] (`VoiceSession`) bên cạnh ô *"Gõ lệnh chữ"*
 * (`VoiceTextConsole`). Chép mười lambda sang bề mặt mới là dựng **bản sao thứ hai của bộ dây** — và bản sao
 * ấy sẽ lệch ở đúng lần ai đó thêm một nhánh ý định: gõ thì chạy, nói thì im, **không gì báo**. Đó đúng là họ
 * lỗi mà KDoc [VoiceDispatcher] dựng ra để chặn ("không mở đường thứ hai tới bất cứ thứ gì"), nên khi có bề
 * mặt thứ hai thì chính bộ dây cũng phải có một chỗ khai duy nhất.
 *
 * Thứ **cố ý** để chỗ gọi tự truyền: `say` và `confirm`. Chúng là *cách trả lời*, mà hai bề mặt trả lời khác
 * nhau thật (một bên là sổ dòng chữ trong Cài đặt, một bên là tấm chữ ở góc màn + một lượt nghe có/không).
 */
object VoiceWiring {

    private const val TAG = "KachiVoiceWiring"

    /**
     * Nhãn app → tên gói, đọc từ [PackageQueries] (cửa DUY NHẤT của dự án tới `PackageManager`).
     *
     * Chỗ gọi tự quyết định nhớ lại bao lâu: màn Cài đặt nhớ theo lượt dựng trang, phiên nghe đọc mỗi lần (một
     * phiên chỉ xảy ra vài lần một chuyến, mà app mới cài phải gọi được ngay).
     *
     * ## [SOÁT 2026-09-16 · P3] Nhãn TRÙNG: chọn có luật, và **nói ra**
     * Bản trước kết thúc bằng `.toMap()`, tức hai app cùng nhãn (*"Cài đặt"* của OEM + một bản cài thêm) gộp im
     * lặng về **gói mà `PackageManager` trả về sau cùng** — một thứ tự không có gì bảo đảm. Người lái nói một
     * cái tên, một app **khác** mở lên, không dòng log nào. Luật chọn nay nằm ở [VoiceAppLabelPick] (`:core`,
     * thuần, kiểm off-car); ở đây chỉ còn hai việc mà chỉ tầng Android làm được: **đọc máy** và **ghi log**.
     */
    fun appsByLabel(ctx: Context): Map<String, String> = appIndex(ctx).keys

    /**
     * 2.91 VOICE-APP-NAMES · A2 — bảng gọi app ĐẦY ĐỦ của phiên ([VoiceAppIndex]): nhãn thật › tên đã dạy của hồ sơ đang
     * dùng ([VoiceTaughtSource], đúng tiến trình) › nhãn locale thứ hai ([AppAltLabels], chỉ bộ nhớ) + dạng đọc. Một lượt
     * hỏi `PackageManager`. `keys` giữ nguyên hợp đồng [appsByLabel] cũ: không tên đã dạy + không nhãn phụ ⇒ y nguyên bản
     * đồ H3(c) cũ (nhãn thật trước, dạng đọc `putIfAbsent` sau — bài `VoiceAppIndexTest` khoá). Lượt hỏi
     * `PackageManager` ~100 ms trên đầu xe ⇒ đúng MỘT lượt ở đây, mọi tầng sau nhận danh sách đã đọc.
     */
    fun appIndex(ctx: Context, taught: List<TaughtName> = VoiceTaughtSource.names(ctx)): VoiceAppIndex {
        val t0 = System.nanoTime()
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = PackageQueries.queryActivities(pm, intent)
        val entries = infos
            .mapNotNull { ri ->
                val info = ri.activityInfo ?: return@mapNotNull null
                VoiceAppLabelPick.Entry(ri.loadLabel(pm).toString(), info.packageName, isSystem(info.applicationInfo))
            }
        val picked = VoiceAppLabelPick.of(entries)
        // Log ở mức W chứ không I: đây là một phép đoán thay người dùng (xem luật (1) ở [VoiceAppLabelPick]), và
        // nó là dòng DUY NHẤT trả lời được câu *"vì sao nói tên này lại mở app kia"* khi nó xảy ra trên xe thật.
        picked.ambiguous.forEach { (label, pkgs) ->
            Log.w(TAG, "nhãn \"$label\" trùng ở ${pkgs.size} gói (${pkgs.joinToString(" · ")}) → chọn ${pkgs.first()}")
        }
        val installed = entries.mapTo(LinkedHashSet()) { it.pkg }
        AppAltLabels.warm(ctx, infos)
        val alt = AppAltLabels.cached(installed)
        val tb = System.nanoTime()
        val idx = VoiceAppIndex.build(picked.labels, installed, taught, alt)
        val t1 = System.nanoTime()
        lastBuildMicros = (t1 - tb) / NANOS_PER_MICRO
        lastIndexMicros = (t1 - t0) / NANOS_PER_MICRO
        // Chỉ ĐẾM — không in tên người dùng dạy (R-nf3).
        if (idx.shadowed.isNotEmpty()) Log.i(TAG, "tên đã dạy bị che: ${idx.shadowed.size} (nhãn app khác / trùng giữa hai app)")
        return idx
    }

    private const val NANOS_PER_MICRO = 1_000L

    /** Tên đã dạy còn sống của bảng [keys] — cùng hàm thuần cho mọi bề mặt ([VoiceAppIndex.aliasesOf]). */
    fun aliases(ctx: Context, keys: Map<String, String>): List<VoiceAppAlias> =
        VoiceAppIndex.aliasesOf(keys, VoiceTaughtSource.names(ctx))

    /**
     * Gói có thuộc ảnh hệ thống không — gồm cả bản hệ thống **đã được cập nhật** (`FLAG_UPDATED_SYSTEM_APP`):
     * thiếu cờ thứ hai thì một app OEM vừa nhận bản vá OTA bỗng bị coi là app người dùng tự cài.
     *
     * `applicationInfo` khai kiểu nền tảng (có thể null trên ROM lạ) ⇒ null = **không biết** = xử như app
     * thường, chứ không ném giữa một lượt nghe.
     */
    private fun isSystem(app: ApplicationInfo?): Boolean =
        app != null && (app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    /**
     * 2.91 VOICE-APP-NAMES · R-nf6 — thời gian (µs) của lượt dựng bảng gọi app GẦN NHẤT trong tiến trình: [lastBuildMicros]
     * = riêng [VoiceAppIndex.build] (phần 2.91 thêm: tên đã dạy + nhãn phụ + dạng đọc), [lastIndexMicros] = cả
     * [appIndex] (gồm lượt hỏi `PackageManager` có từ trước). Chỉ để đo (cầu kiểm thử `state.voice_names`); `-1` = chưa dựng.
     */
    @Volatile var lastBuildMicros: Long = -1L
        private set

    @Volatile var lastIndexMicros: Long = -1L
        private set

    /**
     * Dựng cầu sang các đường đang chạy.
     *
     * Mọi lambda ở đây trỏ tới **đúng** thứ mà một cú chạm dùng — xem KDoc [VoiceDispatcher] về vì sao không
     * được có đường thứ hai.
     */
    @Suppress("LongParameterList")
    fun dispatcher(
        ctx: Context,
        state: () -> HomeUiState,
        appsByLabel: () -> Map<String, String>,
        openApp: (String) -> Boolean,
        openAppList: () -> Unit,
        openSettings: () -> Unit,
        onSwitchProfile: (String) -> Unit,
        onListen: () -> Unit,
        confirm: (String, () -> Unit, () -> Unit) -> Unit,
        say: (String) -> Unit,
        /**
         * V1.1 — gắn app vào ô. Mặc định **từ chối** (trả `false`), có chủ ý: bề mặt nào không nối được đường
         * ngăn kéo thì phải nói *"không gắn được"* chứ không được lặng lẽ mở app toàn màn — người ta đã nói rõ
         * là *"vào ô số 2"*, làm một việc khác mà báo ✓ là nói dối. Xem `VoiceDispatcher.assignAppToSlot`.
         */
        assignAppToSlot: (Int, String) -> Boolean = { _, _ -> false },
        /**
         * L7 — đổi bố cục màn chính. Mặc định **từ chối**, cùng lẽ [assignAppToSlot]: bề mặt không nối được thì
         * nói ra, không báo ✓ cho một việc chưa xảy ra. Xem `VoiceDispatcher.onLayout` về vì sao phải là CHÍNH
         * đường mà chip bố cục dùng (nó còn bỏ bố cục tự vẽ trước khi đặt preset).
         */
        onLayout: (com.byd.clusternav.launcher.LayoutPreset) -> Boolean = { false },
        /**
         * VOICE-WAKE-SLOTCOUNT — chỉ bề mặt KHÔNG chung tiến trình với màn chính (`:wake`) truyền: nó giao nguyên lệnh
         * gắn ô cho Activity, nơi giữ bố cục thật. `null` = kiểm dải bằng [state] (xem `VoiceDispatcher.placeInSlot`).
         */
        placeInSlot: ((Int, String) -> SlotPlaceOutcome)? = null,
        /**
         * VOICE-WAKE-SLOTCOUNT — tiến trình của bề mặt này KHÔNG có màn (`:wake`): không vòng poll nào, và [state] không
         * mang số liệu xe (`CarStatus()` rỗng). Khi ấy "nhu cầu màn" của tiến trình là RỖNG — sự thật — chứ không phải
         * `null` (= "vòng poll đang đọc hết"); để `null` thì `AppContainer.refreshForRead` luôn trả `null` và mọi câu hỏi
         * số liệu / cổng tốc độ rơi về ảnh rỗng. `false` (mặc định) = y nguyên 2.85 cho bề mặt chung tiến trình với màn.
         */
        screenless: Boolean = false,
        /**
         * FIX286 · VK4 — bề mặt KHÁC tiến trình với Cài đặt (`:wake`) truyền: tập hỏi xác nhận + app dẫn đường/nhạc mặc
         * định đọc từ ảnh chụp mà tiến trình chính ghi (cache `SharedPreferences` của `:wake` không bao giờ nạp lại —
         * KDoc [VoiceWakePrefs]). Trường nào ảnh chụp không mang (`null`) ⇒ lùi về prefs như cũ. `null` = màn chính.
         */
        fresh: (() -> VoiceWakePrefs)? = null,
        /**
         * spec `kachi-i18n-zh-th-ms.html` R6 — ngôn ngữ GIỌNG NÓI của lượt này (`voiceLangOf(giao diện)`): mọi câu trả
         * lời + geocoder. KHÔNG có mặc định, có chủ ý: tiến trình chính đọc từ `Strings.current`, còn `:wake` PHẢI đọc
         * từ ảnh chụp ngữ pháp (ở đó `Strings.current` luôn là VI mặc định) — một mặc định ở đây là đúng đường để
         * `:wake` của người dùng English lặng lẽ trả lời tiếng Việt như trước bản này.
         */
        lang: Lang,
    ): VoiceDispatcher = VoiceDispatcher(
        control = { AppContainer.get(ctx).carControl },
        state = state,
        media = { MediaBridge(ctx) },
        appsByLabel = appsByLabel,
        // 2.91 VOICE-APP-NAMES — tên đã dạy của CÙNG bảng mà lượt nói này đọc (prefs ở chính · ảnh chụp ở `:wake`).
        appAliases = { keys -> aliases(ctx, keys) },
        openApp = openApp,
        openAppList = openAppList,
        openSettings = openSettings,
        onSwitchProfile = onSwitchProfile,
        onListen = onListen,
        confirm = confirm,
        // V3 · R7 — đọc lại prefs ở MỖI vế (lambda, không phải giá trị): người dùng vừa tích một ô trong Cài đặt
        // thì câu ngay sau đó đã đi luật mới. `runCatching`: không đọc được prefs thì hành vi đúng là **mặc định của
        // owner**, không phải hỏi mọi thứ. [Senior review FIX286 Pass 1 · P3] Từ 2.86 mặc định KHÔNG còn rỗng (mở cửa
        // sổ trời hỏi — SR5) ⇒ lùi về `defaultIds()`, không về tập rỗng: lỗi đọc prefs chỉ được nghiêng về phía hỏi
        // thêm (KDoc `VoiceRiskTable.effectiveIds`), không bao giờ về phía mở nóc mà không hỏi.
        confirmIds = { fresh?.invoke()?.confirmIds ?: runCatching { Prefs.voiceConfirmIds(ctx) }.getOrDefault(VoiceRiskTable.defaultIds()) },
        say = say,
        assignAppToSlot = assignAppToSlot,
        onLayout = onLayout,
        // V1.1 — ba đường của bảng đích. Dựng ở ĐÂY, không ở hai bề mặt: xem KDoc lớp (một bộ dây, một chỗ khai).
        sendToApp = { handoff -> VoiceAppIntents.send(ctx, handoff) },
        geocode = { place -> VoiceGeocoder.resolveBounded(ctx, place, lang) },
        mediaPackage = { MediaBridge(ctx).activePackage() },
        onUi = { block ->
            if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
        },
        // 2.93 · CAMERA-ON-DEMAND — một đường thi hành cho cả tiến trình chính lẫn `:wake` (tự rẽ theo tiến trình); wave 2B:
        // kèm KẾT QUẢ thật (`:wake` = broadcast có thứ tự + `resultCode`) để câu trả lời nói đúng việc đã xảy ra.
        onCamera = { op, done -> com.byd.clusternav.launcher.camera.CameraDemandDispatch.fireForResult(ctx, op, done) },
        // [SOÁT P1-1 · 2026-09-16] Cổng H1 giữ giá trị cũ cho datum ngoài màn ⇒ câu hỏi bằng giọng phải ghim
        // datum đó vào nhu cầu rồi đọc NGAY một lượt. `AppContainer.refreshForRead` tự trả `null` khi ảnh chụp
        // vốn đã tươi, nên chỗ này không phải biết gì về lịch poll.
        // 2.93 VOICE-READ-STALE-BG — tiến trình CHÍNH mà màn đã khuất (nhu cầu `null`, poll dừng): `readFresh` đọc ĐÚNG một
        // datum (cùng luật phím gán nút xe) thay vì trả `null` = "ảnh chụp đã tươi" trong khi nó cũ từ lúc màn còn hiện.
        freshCar = { id ->
            runCatching {
                val c = AppContainer.get(ctx)
                // Chỉ tiến trình KHÔNG màn; vẫn lười (chỉ khi có câu hỏi số liệu), không dựng gì ngoài đường đọc HAL.
                if (screenless && c.carDemand.get() == null) c.carDemand.set(emptySet())
                c.readFresh(id)
            }.getOrNull()
        },
        // App dẫn đường mặc định (owner chọn trong Cài đặt › Dẫn đường) — đọc mỗi lượt để đổi là ăn ngay.
        navDefault = { fresh?.invoke()?.navDefault ?: com.byd.clusternav.Prefs.voiceNavDefaultApp(ctx) },
        // App nhạc mặc định (owner 2026-09-21) — "" nghĩa là tự chọn ⇒ trả null để pickMusic lùi về hành vi cũ.
        musicDefault = { (fresh?.invoke()?.musicDefault ?: com.byd.clusternav.Prefs.voiceMusicDefaultApp(ctx)).ifBlank { null } },
        // Giải video_id bài đầu (YouTube) để "phát luôn" — có thời hạn cứng, hỏng thì lùi search-play.
        resolveVideo = { q -> VoiceYoutubeResolver.firstVideoIdBounded(q) },
        placeInSlot = placeInSlot,
        lang = lang,
    )
}
