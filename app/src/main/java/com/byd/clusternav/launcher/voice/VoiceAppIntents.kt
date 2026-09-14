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
    )

    /**
     * Dựng ý-định cho một đường [launch].
     *
     * @param pkg gói đích — **bắt buộc**, xem quyết định (1).
     * @param query chuỗi chữ (tên bài / điểm đến), nguyên văn.
     * @param coords toạ độ khi khuôn URI cần; `null` ⇒ khuôn cần toạ độ sẽ trả `null`.
     * @return `null` khi đường này không dựng được ý-định nào (vd [VoiceLaunch.OpenOnly], hoặc thiếu toạ độ).
     */
    fun build(launch: VoiceLaunch, pkg: String, query: String, coords: Coords? = null): Intent? = when (launch) {
        is VoiceLaunch.Action -> Intent(launch.action).apply {
            setPackage(pkg)
            putExtra(launch.extra, query)
            launch.extras.forEach { (k, v) -> putExtra(k, v) }
        }
        is VoiceLaunch.Uri -> uri(launch, query, coords)?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(pkg) }
        VoiceLaunch.OpenOnly -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
     * @return `true` khi một ý-định thật sự được giao đi.
     */
    fun send(ctx: Context, h: Handoff): Boolean {
        if (fire(ctx, build(h.launch, h.pkg, h.query, h.coords))) return true
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
