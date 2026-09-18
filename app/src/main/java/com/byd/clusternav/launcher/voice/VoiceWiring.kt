package com.byd.clusternav.launcher.voice

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.launcher.HomeUiState
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
    fun appsByLabel(ctx: Context): Map<String, String> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = PackageQueries.queryActivities(pm, intent)
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
        return withPhonetics(picked.labels)
    }

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
     * H3(c) — bản đồ nhãn→gói **cộng thêm cách đọc âm Việt** của từng nhãn ([VoiceAppPhonetics.spokenForms]).
     *
     * ## Vì sao ở tầng này, và vì sao không phải một lượt hỏi `PackageManager` thứ hai
     * Owner 2026-09-16: *"mở app chatgpt → có mở được không, có lấy được các app đang có trong xe để mở không?"*.
     * Bảng đích ([VoiceSynonyms.APP_TARGETS]) chỉ phủ bảy app được khai tay; mọi app **khác** đang cài chỉ gọi
     * được bằng **nhãn hệ thống** (*"ChatGPT"*), mà mô hình `zipformer-vi` là mô hình tiếng Việt nên nó in ra
     * *"chát gi pi ti"* ([ĐO] `voice-mishear-2026-09-16.md` §3: loại `app` đúng 12,8 %, thấp nhất bảng).
     * Cách đọc **sinh từ chính cái nhãn** ⇒ không tên gói nào bị viết cứng (CLAUDE.md §7), và app mới cài hôm nay
     * là gọi được ngay hôm nay.
     *
     * ## Hai ràng buộc, mỗi cái chặn một lỗi im lặng
     *  1. **Không đè nhãn thật.** `putIfAbsent`: nếu một cách đọc trùng đúng nhãn của app khác (*"maps"* của một
     *     app tên *Maps*) thì nhãn thật giữ nguyên gói của nó. Nhãn là chữ người dùng NHÌN THẤY; một bí danh suy
     *     ra được không bao giờ thắng nó.
     *  2. **Một lượt hỏi `PackageManager` duy nhất.** Hàm này nhận danh sách đã đọc xong, không tự hỏi lại —
     *     lượt hỏi ấy tốn ~100 ms trên đầu xe và mỗi phiên nghe đã gọi nó một lần.
     *
     * Nhãn thuần Việt (*"Cài đặt"*, *"Ứng dụng của tôi"*) tự không sinh cách đọc nào ⇒ bản đồ không phình vô ích.
     */
    fun withPhonetics(labels: List<Pair<String, String>>): Map<String, String> {
        val out = LinkedHashMap<String, String>(labels.size * 2)
        labels.forEach { (label, pkg) -> out[label] = pkg }
        labels.forEach { (label, pkg) ->
            VoiceAppPhonetics.spokenForms(label).forEach { form -> out.putIfAbsent(form, pkg) }
        }
        return out
    }

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
    ): VoiceDispatcher = VoiceDispatcher(
        control = { AppContainer.get(ctx).carControl },
        state = state,
        media = { MediaBridge(ctx) },
        appsByLabel = appsByLabel,
        openApp = openApp,
        openAppList = openAppList,
        openSettings = openSettings,
        onSwitchProfile = onSwitchProfile,
        onListen = onListen,
        confirm = confirm,
        // V3 · R7 — đọc lại prefs ở MỖI vế (lambda, không phải giá trị): người dùng vừa tích một ô trong Cài đặt
        // thì câu ngay sau đó đã đi luật mới. `runCatching` + rỗng: không đọc được prefs thì hành vi đúng là
        // **mặc định của owner** (không hỏi gì), không phải hỏi mọi thứ.
        confirmIds = { runCatching { Prefs.voiceConfirmIds(ctx) }.getOrDefault(emptySet()) },
        say = say,
        assignAppToSlot = assignAppToSlot,
        onLayout = onLayout,
        // V1.1 — ba đường của bảng đích. Dựng ở ĐÂY, không ở hai bề mặt: xem KDoc lớp (một bộ dây, một chỗ khai).
        sendToApp = { handoff -> VoiceAppIntents.send(ctx, handoff) },
        geocode = { place -> VoiceGeocoder.resolveBounded(ctx, place) },
        mediaPackage = { MediaBridge(ctx).activePackage() },
        onUi = { block ->
            if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
        },
        // [SOÁT P1-1 · 2026-09-16] Cổng H1 giữ giá trị cũ cho datum ngoài màn ⇒ câu hỏi bằng giọng phải ghim
        // datum đó vào nhu cầu rồi đọc NGAY một lượt. `AppContainer.refreshForRead` tự trả `null` khi ảnh chụp
        // vốn đã tươi, nên chỗ này không phải biết gì về lịch poll.
        freshCar = { id -> runCatching { AppContainer.get(ctx).refreshForRead(id) }.getOrNull() },
        // App dẫn đường mặc định (owner chọn trong Cài đặt › Dẫn đường) — đọc mỗi lượt để đổi là ăn ngay.
        navDefault = { com.byd.clusternav.Prefs.voiceNavDefaultApp(ctx) },
    )
}
