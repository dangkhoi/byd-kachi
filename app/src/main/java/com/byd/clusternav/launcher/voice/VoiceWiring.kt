package com.byd.clusternav.launcher.voice

import android.content.Context
import android.content.Intent
import com.byd.clusternav.AppContainer
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.launcher.VoiceDispatcher
import com.byd.clusternav.system.PackageQueries

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

    /**
     * Nhãn app → tên gói, đọc từ [PackageQueries] (cửa DUY NHẤT của dự án tới `PackageManager`).
     *
     * Chỗ gọi tự quyết định nhớ lại bao lâu: màn Cài đặt nhớ theo lượt dựng trang, phiên nghe đọc mỗi lần (một
     * phiên chỉ xảy ra vài lần một chuyến, mà app mới cài phải gọi được ngay).
     */
    fun appsByLabel(ctx: Context): Map<String, String> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return PackageQueries.queryActivities(pm, intent)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                ri.loadLabel(pm).toString() to pkg
            }
            .toMap()
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
        say = say,
    )
}
