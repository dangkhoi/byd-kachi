package com.byd.clusternav.launcher.voice

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LangHost

/**
 * ═══ spec `kachi-i18n-zh-th-ms.html` R9 — TẤM CHỮ của phiên `:wake` theo tiếng NGƯỜI DÙNG chọn, không theo locale MÁY ═══
 *
 * [ĐO mã, log-T2 việc mở #2] Phiên `:wake` dựng [VoiceSession] trên `applicationContext` ⇒ mọi `R.string` của tấm chữ
 * (gợi ý *"Đang nghe…"*, *"Đang chuẩn bị…"*, nút *"Đồng ý"*, *"Mở Cài đặt"*) tra theo locale của MÁY: người dùng chọn
 * English trên xe vi-VN thấy gợi ý tiếng Việt cạnh câu trả lời tiếng Anh; xe đặt tiếng Trung thì thấy chữ Hán.
 *
 * ## Vì sao bọc, và vì sao CHỈ đè [getResources]
 * Phiên dùng cùng `ctx` cho micro (`VoiceCapture`), máy đọc (`bindService`), prefs, tệp mô hình, nhật ký và
 * `WindowManager` của tấm chữ. Đổi cả `Context` sang `createConfigurationContext` là đổi đường của tất cả những thứ
 * ấy cùng lúc — đúng kiểu thay đổi phạm vi không tường minh mà CLAUDE.md §4 cấm. Ở đây mọi lời gọi khác đi thẳng
 * xuống `applicationContext` như trước (dịch vụ hệ thống, `bindService`, prefs, `filesDir` y hệt hành vi cũ — [ĐO
 * AOSP r47 `ContextWrapper.java:703-705, 751-752`] mọi lời gọi ấy chuyển nguyên cho `mBase`); chỉ TÀI NGUYÊN đổi
 * tiếng. [ĐO AOSP r47 `Context.java:638-639`, Android 12 r34 `Context.java:704-705`] `getString` là `final` và gọi
 * `getResources()` ⇒ mọi `ctx.getString(R.string…)` của phiên + mọi `View` dựng bằng `ctx` này (`VoiceOverlay`)
 * đều thấy bản đã đặt locale.
 *
 * ## Tiếng lấy từ đâu
 * Ảnh chụp ngữ pháp (`VoiceGrammarSnapshot.uiLang` — tiến trình chính ghi, ĐÃ GIẢI NGHĨA qua `LangHost.resolved`),
 * cùng nguồn với tiếng giọng nói (`voiceLang`) của chính phiên ⇒ chữ và tiếng nói không thể lấy hai nguồn khác nhau.
 * KHÔNG ghi `Strings.current`, KHÔNG gọi `WorkspacePrefs.langMode()` (tự ghi khi migrate) — bài canh
 * `VoiceWakeUiLangContractTest`. Ảnh chụp chưa mang tiếng (tệp cũ) ⇒ tài nguyên của `applicationContext` = hành vi cũ.
 *
 * ## Làm mới
 * Dựng phiên ⇒ đọc tiếng một lần ([VoiceWakeService.buildSession]). Phiên của `:wake` sống theo TIẾN TRÌNH và được
 * DÙNG LẠI qua nhiều lượt ([VoiceWakeSessions]) ⇒ mỗi lần phát lại một phiên cũ, [refresh] đọc lại ảnh chụp và dựng
 * lại tài nguyên (người dùng có thể vừa đổi tiếng ở màn chính). Cấu hình được chụp TẠI lúc làm mới — tức cũ tối đa
 * một lượt, không cũ cả vòng đời tiến trình.
 */
internal class VoiceWakeUiContext(app: Context, lang: Lang?) : ContextWrapper(app) {

    /** Tài nguyên hiện hành — `@Volatile`: [refresh] chạy ở luồng main của `:wake`, luồng nền của phiên chỉ đọc. */
    @Volatile private var res: Resources = resourcesFor(lang)

    override fun getResources(): Resources = res

    /** Đọc lại tiếng từ ảnh chụp MỚI NHẤT trên đĩa rồi dựng lại tài nguyên. Gọi ở mỗi lượt phát lại phiên cũ. */
    fun refresh() {
        res = resourcesFor(VoiceGrammarSnapshotStore.read(baseContext).uiLang)
    }

    /** Một luật với `VoiceWakeHold.uiRes`: có tiếng ⇒ `LangHost.localized` (chỗ dựng cấu hình duy nhất); không ⇒ gốc. */
    private fun resourcesFor(lang: Lang?): Resources =
        (lang?.let { LangHost.localized(baseContext, it) } ?: baseContext).resources
}
