package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.voice.VoiceCommandCatalog
import com.byd.clusternav.launcher.voice.VoicePlaces
import com.byd.clusternav.launcher.voice.VoiceWiring

/**
 * ═══ NHÓM CÀI ĐẶT **GIỌNG NÓI** (owner 2026-09-21) ═══════════════════════════════════════════════════════════
 *
 * Owner: *"phần voice nên tách thành 1 menu setting riêng, đang chung hệ thống hơi lộn xộn, khó tìm"*.
 *
 * Tách khỏi [SettingsSections.system] (nhóm *Hệ thống & quyền*) thành [SettingsGroup.VOICE] riêng. BỐN khối (2.74
 * thêm khối 4), tất cả là bề mặt NGƯỜI DÙNG (đồ dev như *Gõ lệnh chữ* vẫn ở Hệ thống › Nâng cao, sau cổng test-mode):
 *  1. **Hey Kachi** — công tắc nghe câu gọi rảnh tay (mặc định TẮT; nghe nền tốn CPU/pin).
 *  2. **Nói với xe / giọng đọc** — [voice.VoiceModelSettings]: tải mô hình NGHE, gói giọng ĐỌC Piper,
 *     công tắc đọc phản hồi, hỏi-xác-nhận, nguồn micro. (Nhật ký lượt nói bên trong nó vẫn gác sau test-mode.)
 *  3. **Nhạc** — app nhạc mặc định.
 *  4. **Câu lệnh nói được** (2.74 · R3) — danh sách gập/mở, SINH từ [VoiceCommandCatalog]. Đứng **CUỐI** theo yêu
 *     cầu owner: nó là phần để ĐỌC, không phải để cài, nên nó không được chen giữa các công tắc.
 *
 * KHÔNG mang thêm khoá lưu bền mới: mọi công tắc đi qua `deps.bridge` (theo XE) như trước — chỉ đổi CHỖ ĐỨNG trong
 * cây Cài đặt, không đổi cách lưu.
 */
class SettingsVoiceSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {
    fun build(body: LinearLayout) {
        // kachi-i18n-zh-th-ms R4 — câu đầu nhóm: giọng nói chỉ hiểu tiếng Việt, ở MỌI ngôn ngữ giao diện (một gói ASR
        // zipformer-vi). Đứng TRƯỚC mọi công tắc: người chọn 简体中文/ไทย/Melayu phải biết điều này trước khi bật mic.
        body.addView(rows.note(context.getString(R.string.kachi_voice_lang_only)))
        // "Hey Kachi" wake-word (W-WAKE). Mặc định TẮT. Gạt ⇒ ghi pref (theo XE) + VoiceWakeService.sync.
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_wake_section)))
        body.addView(rows.checkRow(
            on = deps.bridge.wakeEnabled(),
            title = context.getString(R.string.kachi_wake_title),
            sub = context.getString(R.string.kachi_wake_sub),
        ) { on -> deps.bridge.setWakeEnabled(on) })

        // Engine nhận "Hey Kachi" (owner 2026-09-22): ASR no-train (mặc định) vs KWS. ASR nghe "kachi" bằng chính
        // mô hình tiếng Việt nên không cần train/thu mẫu.
        body.addView(rows.checkRow(
            on = deps.bridge.wakeEngineAsr(),
            title = context.getString(R.string.kachi_wake_engine_title),
            sub = context.getString(R.string.kachi_wake_engine_sub),
        ) { on -> deps.bridge.setWakeEngineAsr(on) })

        // Dòng TRẠNG THÁI model câu gọi (owner 2026-09-21: "không có gì để biết đã tải xong chưa").
        // Tự làm mới mỗi 1.5s để thấy % tải + lúc "sẵn sàng". Dừng poll khi view rời cửa sổ.
        val st0 = deps.bridge.wakeModelStatus()
        val wakeStatus = rows.statusRow(KachiTheme.MUT2, st0.second)
        body.addView(wakeStatus.view)
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                val (state, text) = deps.bridge.wakeModelStatus()
                val color = when (state) {
                    WakeModelState.READY -> KachiTheme.GREEN
                    WakeModelState.DOWNLOADING -> KachiTheme.AMBER
                    WakeModelState.NOT_DOWNLOADED -> KachiTheme.MUT2
                }
                wakeStatus.update(color, text)
                if (wakeStatus.view.isAttachedToWindow) h.postDelayed(this, 1500L)
            }
        }
        wakeStatus.view.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) { h.post(tick) }
            override fun onViewDetachedFromWindow(v: android.view.View) { h.removeCallbacks(tick) }
        })

        // Nói với xe: mô hình NGHE + giọng ĐỌC + công tắc + hỏi-xác-nhận + nguồn micro.
        com.byd.clusternav.launcher.voice.VoiceModelSettings(context, rows, deps).build(body)

        // App NHẠC mặc định (owner 2026-09-21) — nói "phát nhạc" không nêu app + không có nhạc đang phát ⇒ dùng cái
        // này; có tên ⇒ app đó; "Tự chọn" (rỗng) ⇒ giữ hành vi cũ (app đang phát / app nhạc đầu tiên đã cài).
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_music_section)))
        body.addView(rows.chipRow(
            label = context.getString(R.string.kachi_music_default_app),
            options = deps.bridge.musicAppChoices().map { it to musicAppLabel(it) },
            current = deps.bridge.musicDefaultApp(),
        ) { key -> deps.bridge.setMusicDefaultApp(key) })
        SettingsTripMusicSection(context, rows, deps).section(body)   // F3 — nhạc khi lên xe (SettingsSectionsTrip.kt)

        commandListRows(body)
    }

    /**
     * R3 — *"Câu lệnh nói được"*: nhóm gập/mở ở CUỐI trang, sinh từ [VoiceCommandCatalog].
     *
     * ## Ba nguồn dữ liệu động phải ĐÚNG ba nguồn mà bộ phân tích dùng
     * `VoiceDispatcher.parse` (cửa DUY NHẤT của `:app` vào bộ phân tích) đọc `state().profiles` ·
     * [VoiceWiring.appsByLabel] · [VoicePlaces.labelsOf]`(state().savedPlaces)`. Dựng danh sách từ một bộ khác là
     * quảng cáo những câu mà lượt nói thật **không** nhận — đúng họ lỗi *"màn hình nói nó hiểu đúng"* mà KDoc
     * `VoiceDispatcher` dựng ra để chặn. Nên ba dòng dưới đọc y hệt ba nguồn ấy, không thêm đường thứ hai nào.
     */
    private fun commandListRows(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_voice_cmds_section)))
        // kachi-i18n-zh-th-ms R4/R5 — đầu danh sách: các câu dưới đây là tiếng Việt ở MỌI tiếng giao diện
        // (`VoiceCommandCatalog` dựng câu bằng Lang.VI); cột "Kachi làm gì" theo tiếng màn. Danh sách nằm cuối một
        // trang dài, nên dòng ở đầu nhóm Giọng nói không đủ — người cuộn thẳng xuống đây phải thấy lại.
        body.addView(rows.note(context.getString(R.string.kachi_voice_lang_only)))
        body.addView(rows.note(context.getString(R.string.kachi_voice_cmds_sub)))
        val st = deps.state()
        VoiceCommandCatalog.groups(
            profiles = st.profiles,
            apps = appLabels,
            places = VoicePlaces.labelsOf(st.savedPlaces),
            confirmIds = deps.bridge.voiceConfirmIds(),
        ).forEach { g ->
            // `getQuantityString` chứ không `getString`: bản một-chuỗi in *"1 phrases"* ở tiếng Anh (finding #18).
            val n = g.examples.size
            val count = context.resources.getQuantityString(R.plurals.kachi_voice_cmds_count, n, n)
            body.addView(rows.disclosureRow(Disclosure(g.title, count) { box ->
                g.examples.forEach { e ->
                    val does = if (e.asksFirst) context.getString(R.string.kachi_voice_cmds_asks, e.does) else e.does
                    box.addView(rows.disclosureLine(e.phrase, does))
                }
            }))
        }
    }

    /**
     * Nhãn app đã cài — đi qua [VoiceWiring.appsByLabel] (cùng bảng mà phiên NGHE dùng), `by lazy` vì một lượt
     * dựng trang chỉ cần dò `PackageManager` **một** lần (cùng lối `VoiceTextConsole`).
     *
     * ## ⚠ [SOÁT 2.74] Thứ tự khoá là thứ có ý nghĩa ở đây
     * Bảng ấy mang **cả cách đọc âm Việt** của mỗi nhãn ([VoiceWiring.withPhonetics]) — cố ý, để lượt NGHE nhận
     * *"mở du túp"*. Danh sách trong Cài đặt chỉ bày vài ví dụ đầu, và nó **đọc được chữ**: nó chỉ đẹp vì
     * `withPhonetics` chèn **hết nhãn thật trước**, rồi mới tới bí danh (`LinkedHashMap`). Đảo hai vòng lặp ở đó
     * ⇒ màn Cài đặt bỗng quảng cáo *"mở du túp"* như một tên app. Cần đúng-nhãn-thật thì phải lọc ở đây, đừng đổi
     * thứ tự bên ấy (bên ấy là đường NGHE đã đo trên xe).
     */
    private val appLabels: List<String> by lazy { VoiceWiring.appsByLabel(context).keys.toList() }

    /** Nhãn app nhạc — rỗng = "Tự chọn"; còn lại là tên thương hiệu (danh từ riêng, VI=EN). */
    private fun musicAppLabel(key: String): String = when (key) {
        "" -> context.getString(R.string.kachi_music_auto)
        "ytmusic" -> "YT Music"
        "youtube" -> "YouTube"
        "spotify" -> "Spotify"
        "zing" -> "Zing MP3"
        else -> key
    }
}
