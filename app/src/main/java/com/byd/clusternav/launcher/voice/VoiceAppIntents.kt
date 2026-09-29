package com.byd.clusternav.launcher.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * ═══ V1.1 · DỊCH [VoiceLaunch] (dữ liệu, `:core`) THÀNH MỘT `Intent` VÀ BẮN ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R17**. Đây là **toàn bộ** phần Android của bảng đích: bảy app,
 * một hàm. Mọi khác biệt giữa chúng nằm ở dữ liệu ([VoiceAppTargets]), không ở đây — CLAUDE.md §7.
 *
 * ## Ba quyết định, mỗi cái chặn một ca hỏng đã thấy
 *  1. **Luôn `setPackage`.** [ĐO] máy ảo 2026-09-14: cả Google Maps lẫn Waze đều bắt `geo:`, nên một ý-định trần
 *     bung hộp *"Open with"*. Giữa lúc lái, một hộp chọn app còn tệ hơn không làm gì — người ta sẽ nhìn xuống.
 *  2. **`resolveActivity` TRƯỚC khi bắn.** Không ai nhận thì `startActivity` ném `ActivityNotFoundException`;
 *     bắt ngoại lệ vẫn được, nhưng hỏi trước cho phép **lùi sang [VoiceAppTarget.fallback]** rồi mới lùi tiếp
 *     sang *"mở app trơn"*, tức người lái nhận được câu trả lời đúng chứ không phải một dấu ✗ chung chung.
 *  3. **`NEW_TASK`, KHÔNG `CLEAR_TOP`.** [ĐO] VietMap là `singleTask`: ý-định thứ hai được giao vào task đang có
 *     (*"brought to the front"*). `CLEAR_TOP` ở đây là thay đổi ngăn xếp của app khác mà ta chưa kiểm được hậu
 *     quả — đúng thứ CLAUDE.md §4 dặn phải trả lời được phạm vi trước khi làm.
 *  4. **`CLEAR_TASK` chỉ khi DỮ LIỆU của đích bảo thế** ([VoiceAppTarget.clearTaskOnNav] — hôm nay chỉ Google Maps,
 *     [ĐO xe 29/09]). Cờ dựng ở đúng một chỗ ([launchFlags]); mọi lượt giao ĐIỂM ĐẾN (giọng nói theo tên/địa
 *     chỉ/toạ độ, nơi đã lưu, dẫn theo lịch) dựng [Handoff] qua đúng một cửa ([destinationHandoff]). Chỉ đường
 *     CHÍNH mang cờ; ý-định dự phòng chưa đo ⇒ `NEW_TASK` trơn (xem [send]).
 *     Phạm vi (CLAUDE.md §4): chỉ task của CHÍNH gói đích (`setPackage`); AOSP `android-10.0.0_r47`
 *     `ActivityStarter.java:2211-2225` (`setTaskFromIntentActivity`) **tái dùng** TaskRecord đó
 *     (`performClearTaskLocked` rồi `mReuseTask = task`) ⇒ không đổi display/stack; `Intent.java:6085-6092` — cờ này
 *     chỉ có nghĩa khi đi cùng `NEW_TASK`. Không cần hoàn tác: dọn phiên dẫn cũ chính là việc người lái vừa yêu cầu.
 */
object VoiceAppIntents {

    private const val TAG = "KachiVoiceIntents"

    /**
     * Toạ độ đã giải ra từ một tên địa điểm.
     *
     * @property place tên mà bên tra cứu trả về — để **đọc lại cho người dùng** trước khi bắn.
     *   ⚠ Tên trường CỐ Ý không phải `label`: `LauncherI18nContractTest.tang ve khong doc nhan GOC cua core` quét
     *   mọi lần đọc `.label` ở `:app` để bắt chỗ dùng nhãn GỐC (luôn tiếng Việt) của `:core`. Chuỗi này là dữ
     *   liệu của một máy chủ bản đồ, không thuộc diện đó — đặt tên khác để bài canh kia khỏi phải mang thêm một
     *   mục loại trừ, tức khỏi phải mở thêm một lỗ (cùng lý do với `VoiceIntent.OpenApp.appName`).
     */
    data class Coords(val lat: Double, val lng: Double, val place: String)

    /**
     * Một lượt giao việc cho app đích — gói **mọi** thứ cần để bắn vào một đối tượng.
     *
     * Gộp lại thay vì truyền năm tham số rời vì nó đi qua một lambda của [com.byd.clusternav.launcher.VoiceDispatcher]:
     * một lambda năm tham số cùng kiểu (hai `String`, hai `VoiceLaunch?`) là năm cơ hội truyền nhầm thứ tự, và
     * trình biên dịch không đỡ được ca nào trong số đó.
     */
    data class Handoff(
        val pkg: String,
        val launch: VoiceLaunch,
        val query: String,
        val coords: Coords? = null,
        val fallback: VoiceLaunch? = null,
        /** Thêm `FLAG_ACTIVITY_CLEAR_TASK` — lấy từ [VoiceAppTarget.clearTaskOnNav] qua [destinationHandoff]. */
        val clearTask: Boolean = false,
    )

    /**
     * Lượt giao **ĐIỂM ĐẾN** cho [target] — cửa DUY NHẤT dựng [Handoff] dẫn đường (quyết định 4).
     *
     * Giọng nói (`VoiceTargetDispatch.deliver`: theo tên/địa chỉ, toạ độ đã tra, nơi đã lưu) và dẫn theo lịch
     * (`ScheduledNavApplier`) cùng đi qua đây, nên cờ của đích không thể lệch giữa hai đường.
     *
     * @return `null` khi app không có đường nhận điểm đến với dữ liệu đang có ([VoiceAppTarget.destinationLaunch]).
     */
    fun destinationHandoff(target: VoiceAppTarget, pkg: String, query: String, coords: Coords?): Handoff? {
        val launch = target.destinationLaunch(coords != null) ?: return null
        return Handoff(pkg, launch, query, coords, target.fallback, clearTask = target.clearTaskOnNav)
    }

    /**
     * Cờ khởi chạy của MỌI ý-định giao việc — chỗ DUY NHẤT dựng cờ.
     *
     * `NEW_TASK` luôn có (bắn từ `Context` không phải Activity); `CLEAR_TASK` chỉ khi [clearTask] (quyết định 4).
     * Hằng của nền tảng: `NEW_TASK` = `0x10000000`, `CLEAR_TASK` = `0x00008000` ⇒ cả hai = `0x10008000`.
     */
    fun launchFlags(clearTask: Boolean): Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or (if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)

    /**
     * Dựng ý-định cho một đường [launch].
     *
     * @param pkg gói đích — **bắt buộc**, xem quyết định (1).
     * @param query chuỗi chữ (tên bài / điểm đến), nguyên văn.
     * @param coords toạ độ khi khuôn URI cần; `null` ⇒ khuôn cần toạ độ sẽ trả `null`.
     * @param clearTask thêm `CLEAR_TASK` — xem [launchFlags].
     * @return `null` khi đường này không dựng được ý-định nào (vd [VoiceLaunch.OpenOnly], hoặc thiếu toạ độ).
     */
    fun build(
        launch: VoiceLaunch,
        pkg: String,
        query: String,
        coords: Coords? = null,
        clearTask: Boolean = false,
    ): Intent? = when (launch) {
        is VoiceLaunch.Action -> Intent(launch.action).apply {
            setPackage(pkg)
            putExtra(launch.extra, query)
            launch.extras.forEach { (k, v) -> putExtra(k, v) }
        }
        is VoiceLaunch.Uri -> uri(launch, query, coords)?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(pkg) }
        VoiceLaunch.OpenOnly -> null
    }?.addFlags(launchFlags(clearTask))

    /**
     * Thay chỗ trống trong khuôn URI.
     *
     * Mã hoá phần trăm **chỉ cho phần chữ**: toạ độ là số nên không cần, và mã hoá dấu `,` của
     * `google.navigation:ll=10.7,106.7` sẽ làm hỏng chính tham số ấy.
     */
    private fun uri(launch: VoiceLaunch.Uri, query: String, coords: Coords?): String? {
        if (launch.needsCoords && coords == null) return null
        return launch.template
            .replace(VoiceLaunch.SLOT, Uri.encode(query))
            .replace(VoiceLaunch.LAT, coords?.lat?.toString().orEmpty())
            .replace(VoiceLaunch.LNG, coords?.lng?.toString().orEmpty())
    }

    /**
     * Bắn đường [launch] cho [pkg]; thất bại thì thử [fallback].
     *
     * **Gọi trên luồng VẼ** (`startActivity` từ một `Context` không phải Activity vẫn cần `NEW_TASK`, đã có).
     *
     * `CLEAR_TASK` ([Handoff.clearTask]) CHỈ đi với đường chính: [ĐO xe 29/09] đo trên đúng deep-link bắt đầu dẫn
     * của đích; ý-định dự phòng (vd `geo:` — chỉ mở màn kết quả, không bắt đầu dẫn) CHƯA đo lần nào với cờ này, mà
     * cờ xoá phiên dẫn đang chạy của app khác không hoàn tác được ⇒ giữ `NEW_TASK` trơn (spec 2.83 §4.6, CLAUDE.md §14).
     *
     * @return `true` khi một ý-định thật sự được giao đi.
     */
    fun send(ctx: Context, h: Handoff): Boolean {
        if (fire(ctx, build(h.launch, h.pkg, h.query, h.coords, h.clearTask))) return true
        return h.fallback != null && fire(ctx, build(h.fallback, h.pkg, h.query, h.coords))
    }

    private fun fire(ctx: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        // Hỏi trước — xem quyết định (2). `resolveActivity` trả `null` khi gói không khai cửa nào cho ý-định này.
        if (intent.resolveActivity(ctx.packageManager) == null) {
            Log.i(TAG, "gói ${intent.`package`} không nhận ${intent.action} ${intent.data ?: ""}")
            return false
        }
        return runCatching { ctx.startActivity(intent); true }
            .onFailure { Log.w(TAG, "không bắn được ý-định ${intent.action}", it) }
            .getOrDefault(false)
    }
}
