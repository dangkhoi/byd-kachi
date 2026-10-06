package com.byd.clusternav.launcher

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.voice.TaughtName
import com.byd.clusternav.launcher.voice.TaughtNames
import com.byd.clusternav.launcher.voice.TeachContext
import com.byd.clusternav.launcher.voice.VoiceAppIndex
import com.byd.clusternav.launcher.voice.VoiceAppPhonetics
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceLexicon
import com.byd.clusternav.launcher.voice.VoicePlaces
import com.byd.clusternav.launcher.voice.VoiceTeachHint
import com.byd.clusternav.launcher.voice.VoiceWakePhrase
import com.byd.clusternav.launcher.voice.VoiceWiring
import java.util.concurrent.Executors
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 2.91 VOICE-APP-NAMES · A5 — Cài đặt › Giọng nói › **Dạy tên app** (trang danh sách MỌI app) ═══════════════════
 *
 * Spec §4.3 · R1. Danh sách dựng bằng ĐÚNG [AppDrawerApps.load] (cùng truy vấn `ACTION_MAIN`+`CATEGORY_LAUNCHER` của ngăn
 * kéo; nhãn đọc ngay, icon trên luồng nền), trừ chính Kachi. Mỗi hàng: icon · nhãn · tên đã dạy · nút **Thử** · **Xoá**;
 * chạm hàng ⇒ hộp dạy ([SettingsVoiceNamesDialog]). Có ô tìm + lọc *Tất cả / Đã dạy / Nên dạy* + nhóm *App đã gỡ* (tên mồ
 * côi) + cảnh báo trên hàng có tên bị nhãn app khác che ([VoiceAppIndex.shadowed] — cùng phép so của phiên nghe).
 *
 * *Nên dạy* = chưa dạy · nhãn không sinh dạng đọc âm Việt ([VoiceAppPhonetics.spokenForms] rỗng) · không thuộc bảng đích ·
 * nhãn toàn chữ Latin không dấu — [SUY] phép xếp loại GỢI Ý, không phải sự thật về mô hình.
 *
 * Mọi lượt ghi đi qua [VoiceNamesPort.save] → ViewModel; trang không ghi bền trực tiếp. Phiên bản lạ ⇒ chỉ đọc.
 */
internal class SettingsVoiceNamesPage(private val context: Context, private val deps: SettingsDeps) {

    private enum class Filter { ALL, TAUGHT, SUGGEST }

    private class App(val pkg: String, val title: String, val icon: () -> Drawable?)

    private val ui = Handler(Looper.getMainLooper())
    private val rows = SettingsRows(context)
    private val port get() = deps.voiceNames
    private var apps: List<App> = emptyList()
    private var index: VoiceAppIndex = VoiceAppIndex.EMPTY
    private var filter = Filter.ALL
    private var query = ""
    private var pending: VoiceTeachHint.Request = VoiceTeachHint.Request()
    /** Có lượt ghi nào thành công khi trang mở (dạy / xoá) — trang Giọng nói được NHỚ LẠI nên phải dựng lại khi đóng. */
    private var dirty = false
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val banner = TextView(context)

    /** Mở trang; [request] từ lối (b) (gói) hoặc (c) (mẫu đang chờ). */
    fun open(request: VoiceTeachHint.Request = VoiceTeachHint.Request()) {
        pending = request
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dpi(context, Sp.L); setPadding(p, dpi(context, Sp.S), p, 0)
        }
        root.addView(rows.note(context.getString(R.string.kachi_voice_lang_only)))
        root.addView(rows.note(context.getString(R.string.kachi_vn_page_note)))
        if (port.readOnly()) root.addView(rows.note(context.getString(R.string.kachi_vn_readonly)))
        banner.apply { setTextColor(c(KachiTheme.ACCENT)); KachiType.apply(this, KachiType.BODY, bold = true); visibility = View.GONE }
        root.addView(banner)
        root.addView(EditText(context).apply {
            hint = context.getString(R.string.kachi_vn_search_hint); setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty().trim(); paint() }
            })
        })
        root.addView(chips)
        root.addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.kachi_vn_page_title))
            .setView(root)
            .setPositiveButton(android.R.string.ok, null)
            .create().also { d ->
                // Trang Cài đặt được nhớ lại (`SettingsPanel.pages`): dòng "Đã dạy: N app" + nhóm "Tên app đã dạy" của danh sách
                // Câu lệnh nói được sẽ CŨ tới lượt đổi hồ sơ ⇒ có ghi thì dựng lại trang đang xem khi đóng.
                d.setOnDismissListener { if (dirty) deps.refreshSettings() }
                // Không bật bàn phím lúc mở trang (ô tìm là lựa chọn, không phải bước đầu) — trên màn xe bàn phím che nửa danh sách.
                d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                d.show()
                d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        reload()
    }

    /** Danh sách app + bảng gọi app đọc trên luồng nền (`PackageManager` ~100 ms trên đầu xe). */
    private fun reload() {
        IO.execute {
            val own = context.packageName
            val loaded = AppDrawerApps(context, onPickApp = {}).load().filter { it.pkg != own }.map { App(it.pkg, it.label, it.icon) }
            val idx = VoiceWiring.appIndex(context, port.names())
            ui.post {
                apps = loaded; index = idx
                paint()
                // Lối (b): mở hộp dạy cho gói ĐÚNG MỘT lần — tiêu thụ ngay, nếu không lượt `reload()` sau khi lưu mở lại hộp.
                pending.pkg?.let { pkg ->
                    pending = pending.copy(pkg = null)
                    apps.firstOrNull { it.pkg == pkg }?.let { teach(it, null) }
                }
            }
        }
    }

    /** Ngữ cảnh cổng an toàn — CÙNG nguồn với phiên nghe (bảng gọi app · tên hồ sơ · sổ địa chỉ · câu gọi Kachi). */
    fun teachContext(names: List<TaughtName> = port.names()): TeachContext {
        val st = deps.state()
        return TeachContext(
            keys = VoiceWiring.appIndex(context, names).keys,
            taught = names,
            profiles = st.profiles,
            places = VoicePlaces.labelsOf(st.savedPlaces),
            wakePhrases = VoiceWakePhrase.PRESETS.map { it.spoken },
        )
    }

    private fun paint() {
        val names = port.names()
        val installed = apps.mapTo(HashSet()) { it.pkg }
        val suggest = apps.filter { isSuggested(it, names) }
        val taughtApps = apps.filter { a -> names.any { it.pkg == a.pkg } }
        paintChips(apps.size, taughtApps.size, suggest.size)
        val shown = when (filter) {
            Filter.ALL -> apps
            Filter.TAUGHT -> taughtApps
            Filter.SUGGEST -> suggest
        }.filter { query.isEmpty() || VoiceLexicon.deaccent(it.title).contains(VoiceLexicon.deaccent(query)) }
        banner.visibility = if (pending.sample != null) View.VISIBLE else View.GONE
        pending.sample?.let { banner.text = context.getString(R.string.kachi_vn_pending, it) }
        list.removeAllViews()
        shown.forEach { a -> list.addView(appRow(a, TaughtNames.of(names, a.pkg))) }
        val orphans = names.filter { it.pkg !in installed }.groupBy { it.pkg }
        if (orphans.isNotEmpty() && filter != Filter.SUGGEST) {
            list.addView(rows.sectionLabel(context.getString(R.string.kachi_vn_uninstalled, orphans.size)))
            orphans.forEach { (pkg, ns) ->
                val shown = ns.first().shownLabel(pkg)
                list.addView(rows.listRow(shown, context.getString(R.string.kachi_vn_uninstalled_row, ns.size),
                    context.getString(R.string.kachi_vn_delete)) { confirmDelete(pkg, shown) })
            }
        }
    }

    private fun paintChips(all: Int, taught: Int, suggest: Int) {
        chips.removeAllViews()
        listOf(
            Filter.ALL to context.getString(R.string.kachi_vn_filter_all, all),
            Filter.TAUGHT to context.getString(R.string.kachi_vn_filter_taught, taught),
            Filter.SUGGEST to context.getString(R.string.kachi_vn_filter_suggest, suggest),
        ).forEach { (f, label) -> chips.addView(pill(label, active = f == filter) { filter = f; paint() }) }
    }

    private fun isSuggested(a: App, names: List<TaughtName>): Boolean =
        names.none { it.pkg == a.pkg } && VoiceAppPhonetics.spokenForms(a.title).isEmpty() &&
            VoiceAppTargets.ALL.none { a.pkg in it.packages } && VoiceLexicon.deaccent(a.title) == a.title.lowercase()

    private fun appRow(a: App, mine: List<TaughtName>): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = KachiTheme.surface(context, Sp.RADIUS_L)
        val p = dpi(context, Sp.S); setPadding(p, p, p, p)
        layoutParams = rows.stackLp()
        setOnClickListener { teach(a, pending.sample) }
        addView(ImageView(context).also { iv ->
            IO.execute { runCatching { a.icon() }.getOrNull()?.let { d -> ui.post { iv.setImageDrawable(d) } } }
        }, LinearLayout.LayoutParams(dpi(context, Sp.ICON_L), dpi(context, Sp.ICON_L)).also { it.rightMargin = dpi(context, Sp.M) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = a.title; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY) })
            addView(TextView(context).apply {
                text = subOf(a, mine); setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(pill(context.getString(R.string.kachi_vn_try), active = false) { tryOut(a) })
        if (mine.isNotEmpty() && !port.readOnly()) addView(pill(context.getString(R.string.kachi_vn_delete), active = false) { confirmDelete(a.pkg, a.title) })
    }

    /** Dòng phụ của hàng: tên đã dạy · bị che · Kachi hiểu sẵn · nên dạy · chưa dạy. */
    private fun subOf(a: App, mine: List<TaughtName>): String {
        val shadow = index.shadowed.firstOrNull { it.pkg == a.pkg }
        if (shadow != null) return context.getString(R.string.kachi_vn_row_shadowed, shadow.accented)
        if (mine.isNotEmpty()) return context.getString(R.string.kachi_vn_row_taught, mine.size, mine.joinToString(" · ") { it.accented })
        val known = (VoiceAppTargets.ALL.filter { a.pkg in it.packages }.flatMap { it.spoken } + VoiceAppPhonetics.spokenForms(a.title)).distinct()
        if (known.isNotEmpty()) return context.getString(R.string.kachi_vn_known_by, known.take(2).joinToString(", "))
        return context.getString(if (isSuggested(a, mine)) R.string.kachi_vn_row_suggest else R.string.kachi_vn_row_untaught)
    }

    private fun teach(a: App, prefill: String?) {
        SettingsVoiceNamesDialog(context, deps, this, a.pkg, a.title, prefill) { changed ->
            if (prefill != null) pending = VoiceTeachHint.Request()   // mẫu đang chờ đã giao cho hộp dạy
            if (changed) { dirty = true; reload() } else paint()
        }.open()
    }

    private fun tryOut(a: App) = SettingsVoiceNamesDialog(context, deps, this, a.pkg, a.title, null) { reload() }.tryOnce()

    private fun confirmDelete(pkg: String, label: String) {
        if (port.readOnly()) return
        SettingsDialogs.confirm(
            context, context.getString(R.string.kachi_vn_page_title), context.getString(R.string.kachi_vn_delete_q, label),
            context.getString(R.string.kachi_vn_delete),
        ) { if (port.save(TaughtNames.removeApp(port.names(), pkg))) { dirty = true; reload() } }
    }

    /** Viên thuốc nhỏ (chip lọc · nút hàng) — cùng nền/đích chạm của nút Cài đặt ([KachiSpace.TOUCH]). */
    internal fun pill(label: String, active: Boolean, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        setTextColor(c(if (active) KachiTheme.ACCENT else KachiTheme.INK)); KachiType.apply(this, KachiType.BODY, bold = active)
        gravity = Gravity.CENTER; minHeight = dpi(context, Sp.TOUCH)
        setPadding(dpi(context, Sp.M), 0, dpi(context, Sp.M), 0)
        background = KachiTheme.surface(context, Sp.RADIUS_PILL, if (active) SurfaceTone.ACTIVE else SurfaceTone.NEUTRAL)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.leftMargin = dpi(context, Sp.XS)
        }
        setOnClickListener { onClick() }
    }

    private companion object {
        /** Một luồng nền cho cả trang (danh sách + icon + bảng gọi app), daemon — cùng khuôn icon của ngăn kéo. */
        val IO = Executors.newFixedThreadPool(2) { r -> Thread(r, "kachi-voicenames").apply { isDaemon = true } }
    }
}
