package com.byd.clusternav.launcher

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.voice.TaughtName
import com.byd.clusternav.launcher.voice.TaughtNames
import com.byd.clusternav.launcher.voice.TaughtSource
import com.byd.clusternav.launcher.voice.TeachGuard
import com.byd.clusternav.launcher.voice.TeachSample
import com.byd.clusternav.launcher.voice.VoiceAppIndex
import com.byd.clusternav.launcher.voice.VoiceModelStore
import com.byd.clusternav.launcher.voice.VoicePlaces
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.launcher.voice.VoiceTeachRelay
import com.byd.clusternav.launcher.voice.VoiceTeachSession
import com.byd.clusternav.launcher.voice.VoiceWiring
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 2.91 VOICE-APP-NAMES · A5 — HỘP DẠY một app: 🎤 2–3 lượt · gõ tên · phán quyết từng mẫu · máy dò hồi quy ═══════
 *
 * Spec §4.3 *"Hộp dạy"* · R2/R3/R7. Mỗi lượt 🎤 đi [VoiceTeachRelay] (tiến trình đang giữ mô hình, R-nf1) ⇒ chữ Kachi nghe
 * được ⇒ [TeachSample.normalize] ⇒ [TeachGuard.check] ⇒ hiện phán quyết: ✓ Mới · = lần trước · Kachi đã hiểu sẵn ·
 * ⚠ Cảnh báo · ⛔ Bị chặn. Lưu bật khi đã ≥ 2 lượt nói (hoặc có tên gõ) — OQ4; tối đa 3 lượt nói mỗi lần dạy. Bấm Lưu ⇒
 * CẢNH BÁO phải qua *Vẫn lưu* (OQ9) ⇒ [TeachGuard.regression] trên luồng nền (mọi câu mẫu, có/không tên mới) ⇒ ghi qua
 * [VoiceNamesPort.save] (ViewModel). Đóng hộp giữa chừng ⇒ huỷ lượt nghe đang mở (không để phiên treo).
 */
internal class SettingsVoiceNamesDialog(
    private val context: Context,
    private val deps: SettingsDeps,
    private val page: SettingsVoiceNamesPage,
    private val pkg: String,
    private val label: String,
    private val prefill: String?,
    private val onClosed: (changed: Boolean) -> Unit,
) {
    /** Một mẫu của lần dạy này; [takes] = số lượt mô hình in ra CÙNG [norm] (gộp theo chuỗi chuẩn hoá — spec §7 OQ4). */
    private class Sample(
        val accented: String,
        val norm: String,
        val source: TaughtSource,
        var verdict: TeachGuard.Verdict,
        var takes: Int = 1,
    )

    private val ui = Handler(Looper.getMainLooper())
    private val port get() = deps.voiceNames
    /** Hồ sơ lúc mở hộp — lưu chỉ khi hồ sơ đang dùng vẫn là nó (tên dạy là chữ của giọng MỘT người lái). */
    private val profile: String = deps.state().activeProfile
    private val samples = ArrayList<Sample>()
    private var speechTakes = 0
    private var changed = false
    private val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val status = TextView(context)
    private var speak: TextView? = null
    private var dialog: AlertDialog? = null
    private val relay = VoiceTeachRelay(context, local = { localSession })
    private val localSession by lazy {
        VoiceTeachSession(
            context,
            profiles = { deps.state().profiles },
            appsByLabel = { VoiceWiring.appsByLabel(context) },
            places = { VoicePlaces.labelsOf(deps.state().savedPlaces) },
        )
    }

    fun open() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dpi(context, Sp.L); setPadding(p, dpi(context, Sp.S), p, 0)
        }
        val rows = SettingsRows(context)
        root.addView(rows.note(context.getString(R.string.kachi_vn_dialog_hint, label)))
        status.apply { setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION) }
        root.addView(status)
        root.addView(box)
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            speak = page.pill(context.getString(R.string.kachi_vn_speak), active = true) { listen() }.also { addView(it) }
            addView(page.pill(context.getString(R.string.kachi_vn_type), active = false) { typeName() })
        })
        if (!VoiceModelStore.isReady(context)) { speak?.isEnabled = false; speak?.alpha = DISABLED; status.text = context.getString(R.string.kachi_vn_no_model) }
        if (port.readOnly()) { speak?.isEnabled = false; status.text = context.getString(R.string.kachi_vn_readonly) }
        val d = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.kachi_vn_dialog_title, label))
            .setView(android.widget.ScrollView(context).apply { addView(root) })
            .setPositiveButton(context.getString(R.string.kachi_save), null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        d.setOnDismissListener { relay.cancel(); onClosed(changed) }
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
            repaint()
        }
        dialog = d
        d.show()
        // Lối (c): mẫu đang chờ LÀ một lượt nói thật của người dùng (chữ mô hình in ở câu "mở …" không hiểu) ⇒ tính là lượt 1.
        prefill?.let { speechTakes = 1; addSample(it, TaughtSource.SPEECH) }
    }

    /** ▶ Thử — một lượt nghe, phân tích với từ vựng đầy đủ, nói Kachi hiểu thành gì. KHÔNG mở app. */
    fun tryOnce() {
        val view = TextView(context).apply {
            text = context.getString(R.string.kachi_vn_listening); setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
            val p = dpi(context, Sp.L); setPadding(p, p, p, p)
        }
        val d = AlertDialog.Builder(context).setTitle(context.getString(R.string.kachi_vn_try_title, label)).setView(view)
            .setPositiveButton(android.R.string.ok, null).create()
        d.setOnDismissListener { relay.cancel() }
        d.show()
        val ok = relay.listen(object : VoiceTeachSession.Listener {
            override fun onState(state: VoiceTeachSession.State) { view.text = stateText(state) }
            override fun onResult(result: VoiceTeachSession.Result) {
                if (result.error != null) { view.text = errorText(result.error); return }
                Thread({
                    val ctx = page.teachContext()
                    val intents = ctx.parse(result.heard)
                    val one = intents.singleOrNull()
                    val lang = voiceLangOf(Strings.current)
                    val said = intents.joinToString(" · ") { VoiceReply.preview(it, lang) }
                    val good = one != null && ctx.pkgOf(one) == pkg
                    ui.post {
                        view.text = context.getString(R.string.kachi_vn_heard, result.heard)
                        view.append(System.lineSeparator())
                        view.append(context.getString(if (good) R.string.kachi_vn_try_ok else R.string.kachi_vn_try_bad, said))
                    }
                }, "KachiTeachTry").start()
            }
        })
        if (!ok) view.text = context.getString(R.string.kachi_vn_e_busy)
    }

    private fun listen() {
        if (speechTakes >= MAX_TAKES) return
        val ok = relay.listen(object : VoiceTeachSession.Listener {
            override fun onState(state: VoiceTeachSession.State) { status.text = stateText(state) }
            override fun onResult(result: VoiceTeachSession.Result) {
                status.text = ""
                if (result.error != null) { status.text = errorText(result.error); return }
                speechTakes++
                when (val s = TeachSample.normalize(result.heard)) {
                    is TeachSample.Sample -> addSample(s.accented, TaughtSource.SPEECH)
                    is TeachSample.Rejected -> { status.text = context.getString(R.string.kachi_vn_heard_bad, result.heard); repaint() }
                }
            }
        })
        if (!ok) status.text = context.getString(R.string.kachi_vn_e_busy)
    }

    private fun typeName() {
        if (port.readOnly()) return
        SettingsDialogs.askName(context, context.getString(R.string.kachi_vn_type_title, label), "") { typed ->
            when (val s = TeachSample.shape(typed)) {
                is TeachSample.Sample -> addSample(s.accented, TaughtSource.TYPED)
                is TeachSample.Rejected -> status.text = context.getString(R.string.kachi_vn_r_shape)
            }
        }
    }

    /**
     * Phán quyết A–D chạy trên luồng nền (bảng gọi app = một lượt `PackageManager` + phân tích thử). Mẫu trùng CHUỖI CHUẨN
     * HOÁ với một mẫu đã có (cùng nguồn) ⇒ gộp, đếm lượt — đó là thước "Kachi nghe ổn định" và là điều kiện lưu tên ngắn.
     */
    private fun addSample(accented: String, source: TaughtSource) {
        val norm = VoiceAppIndex.normOf(accented)
        if (repeatOf(norm, source)) return
        Thread({
            val v = TeachGuard.check(page.teachContext(), pkg, accented, source)
            // Lượt khác cùng chuỗi tới trong lúc phán ⇒ gộp vào nó, không thành hai dòng.
            ui.post { if (!repeatOf(norm, source)) { samples.add(Sample(accented, norm, source, v)); repaint() } }
        }, "KachiTeachCheck").start()
    }

    /** `true` = đã gộp vào mẫu cùng [norm]; tên ngắn đang chờ lượt thứ hai ⇒ phán LẠI với số lượt mới. */
    private fun repeatOf(norm: String, source: TaughtSource): Boolean {
        val s = samples.firstOrNull { it.norm == norm && it.source == source } ?: return false
        s.takes++
        if (!s.verdict.has(TeachGuard.Code.SHORT_ONE_TAKE)) { repaint(); return true }
        val takes = s.takes
        Thread({
            val v = TeachGuard.check(page.teachContext(), pkg, s.accented, s.source, takes)
            ui.post { if (s in samples) { s.verdict = v; repaint() } }
        }, "KachiTeachCheck").start()
        return true
    }

    private fun repaint() {
        box.removeAllViews()
        var take = 0
        samples.forEach { s ->
            take++
            val title = context.getString(R.string.kachi_vn_sample, take, s.accented)
            // Phán quyết LUÔN hiện (bản đầu: lặp lại ⇒ chỉ "= lần trước" — một mẫu bị CHẶN mà lặp lại trông như mẫu tốt).
            val same = if (s.takes > 1) " · " + context.getString(R.string.kachi_vn_v_same) else ""
            val chip = verdictText(s.verdict.level) + same
            box.addView(line(title, (listOf(chip) + s.verdict.reasons.map { reasonText(it) }).joinToString(NL)) {
                samples.remove(s); repaint()
            })
        }
        TaughtNames.of(port.names(), pkg).forEach { n ->
            box.addView(line(context.getString(R.string.kachi_vn_saved_name, n.accented), "") {
                if (port.save(TaughtNames.remove(port.names(), pkg, n.norm))) { changed = true; repaint() }
            })
        }
        speak?.let { it.isEnabled = speechTakes < MAX_TAKES && it.alpha != DISABLED }
        val canSave = (speechTakes >= MIN_TAKES || samples.any { it.source == TaughtSource.TYPED }) && savable().isNotEmpty()
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = canSave && !port.readOnly()
        val needTwo = context.getString(R.string.kachi_vn_need_two)
        if (!canSave && samples.isNotEmpty() && status.text.isNullOrEmpty()) status.text = needTwo
        if (canSave && status.text?.toString() == needTwo) status.text = ""
    }

    private fun savable(): List<Sample> = samples.filter {
        (it.verdict.level == TeachGuard.Level.NEW || it.verdict.level == TeachGuard.Level.WARN) && !it.verdict.has(TeachGuard.Code.DUPLICATE)
    }

    private fun save() {
        val picks = savable()
        val warned = picks.filter { it.verdict.level == TeachGuard.Level.WARN }
        if (warned.isEmpty()) { commit(picks); return }
        val why = warned.joinToString(NL) { s -> context.getString(R.string.kachi_vn_named_reason, s.accented, s.verdict.reasons.joinToString(NL) { reasonText(it) }) }
        SettingsDialogs.confirm(context, context.getString(R.string.kachi_vn_warn_title), why, context.getString(R.string.kachi_vn_still_save)) { commit(picks) }
    }

    /**
     * Máy dò hồi quy (E) cho từng tên trên luồng nền, rồi ghi MỘT lượt qua cổng ViewModel trên luồng vẽ — GỘP vào danh sách
     * đọc LẠI lúc ghi (không ghi đè bằng bản chụp lúc bắt đầu: một lượt xoá tên trong lúc soát sẽ bị mất), và chỉ khi hồ
     * sơ đang dùng vẫn là hồ sơ lúc mở hộp.
     */
    private fun commit(picks: List<Sample>) {
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
        Thread({
            var list = port.names()
            var ctx = page.teachContext(list)
            val passed = ArrayList<TaughtName>()
            val notes = ArrayList<String>()
            picks.forEach { s ->
                val name = TaughtName(pkg, s.source, s.accented, label)
                val reg = TeachGuard.regression(ctx, name)
                ui.post { status.text = context.getString(R.string.kachi_vn_checking, reg.checked) }
                when {
                    reg.changed.isNotEmpty() -> notes += context.getString(R.string.kachi_vn_named_reason, s.accented, context.getString(R.string.kachi_vn_r_regression, reg.changed.take(3).joinToString(" · ")))
                    !reg.reachable -> notes += context.getString(R.string.kachi_vn_named_reason, s.accented, context.getString(R.string.kachi_vn_r_unreachable))
                    else -> when (val r = TaughtNames.add(list, name)) {
                        is TaughtNames.Added -> { list = r.names; ctx = ctx.copy(taught = list); passed += name }
                        is TaughtNames.Refused -> notes += refusedNote(s.accented, r.why)
                    }
                }
            }
            ui.post { store(passed, notes) }
        }, "KachiTeachSave").start()
    }

    /** Luồng vẽ: gộp [passed] vào danh sách ĐỌC LẠI rồi ghi một lượt. */
    private fun store(passed: List<TaughtName>, notes: MutableList<String>) {
        var added = 0
        var ok = false
        if (deps.state().activeProfile != profile) {
            notes += context.getString(R.string.kachi_vn_profile_changed)
        } else {
            var cur = port.names()
            passed.forEach { n ->
                when (val r = TaughtNames.add(cur, n)) {
                    is TaughtNames.Added -> { cur = r.names; added++ }
                    is TaughtNames.Refused -> notes += refusedNote(n.accented, r.why)
                }
            }
            ok = added > 0 && port.save(cur)
        }
        if (ok) changed = true
        samples.clear(); speechTakes = 0
        val head = if (ok) context.getString(R.string.kachi_vn_saved_ok, added, label) else ""
        status.text = (listOf(head) + notes.map { context.getString(R.string.kachi_vn_not_saved, it) }).filter { it.isNotBlank() }.joinToString(NL)
        repaint()
    }

    private fun refusedNote(accented: String, why: TaughtNames.Why): String = context.getString(
        R.string.kachi_vn_named_reason, accented,
        context.getString(if (why == TaughtNames.Why.DUPLICATE) R.string.kachi_vn_r_duplicate else R.string.kachi_vn_full),
    )

    private fun line(title: String, sub: String, onRemove: () -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dpi(context, Sp.XS), 0, dpi(context, Sp.XS))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = title; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY) })
            if (sub.isNotEmpty()) addView(TextView(context).apply { text = sub; setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!port.readOnly()) addView(page.pill(context.getString(R.string.kachi_vn_delete), active = false) { onRemove() })
    }

    private fun stateText(s: VoiceTeachSession.State): String = context.getString(
        when (s) {
            VoiceTeachSession.State.LOADING_MODEL -> R.string.kachi_voice_loading_model
            VoiceTeachSession.State.LISTENING -> R.string.kachi_vn_listening
            VoiceTeachSession.State.DECODING -> R.string.kachi_vn_decoding
        },
    )

    private fun errorText(e: VoiceTeachSession.Error): String = context.getString(
        when (e) {
            VoiceTeachSession.Error.BUSY -> R.string.kachi_vn_e_busy
            VoiceTeachSession.Error.NO_MIC -> R.string.kachi_voice_no_mic
            VoiceTeachSession.Error.NO_MODEL -> R.string.kachi_vn_no_model
            VoiceTeachSession.Error.ENGINE -> R.string.kachi_voice_engine_failed
            VoiceTeachSession.Error.NOTHING_HEARD, VoiceTeachSession.Error.CANCELLED -> R.string.kachi_voice_nothing_heard
        },
    )

    private fun verdictText(l: TeachGuard.Level): String = context.getString(
        when (l) {
            TeachGuard.Level.NEW -> R.string.kachi_vn_v_new
            TeachGuard.Level.ALREADY -> R.string.kachi_vn_v_already
            TeachGuard.Level.WARN -> R.string.kachi_vn_v_warn
            TeachGuard.Level.BLOCK -> R.string.kachi_vn_v_block
        },
    )

    /** Lý do theo MÃ (spec §4.7) — chữ máy ([TeachGuard.Reason.detail]) chỉ chèn khi là tên app / dòng hotword. */
    private fun reasonText(r: TeachGuard.Reason): String = when (r.code) {
        TeachGuard.Code.SHAPE -> context.getString(R.string.kachi_vn_r_shape)
        TeachGuard.Code.COMMAND -> context.getString(R.string.kachi_vn_r_command)
        TeachGuard.Code.COMMAND_PREFIX -> context.getString(R.string.kachi_vn_r_command_prefix)
        TeachGuard.Code.RESERVED_WORD -> context.getString(R.string.kachi_vn_r_reserved)
        TeachGuard.Code.SLOT -> context.getString(R.string.kachi_vn_r_slot)
        TeachGuard.Code.PROFILE -> context.getString(R.string.kachi_vn_r_profile)
        TeachGuard.Code.PLACE -> context.getString(R.string.kachi_vn_r_place)
        TeachGuard.Code.WAKE -> context.getString(R.string.kachi_vn_r_wake)
        TeachGuard.Code.OTHER_APP -> context.getString(R.string.kachi_vn_r_other_app, r.detail)
        TeachGuard.Code.IS_COMMAND -> context.getString(R.string.kachi_vn_r_is_command)
        TeachGuard.Code.REGRESSION -> context.getString(R.string.kachi_vn_r_regression, r.detail)
        TeachGuard.Code.UNREACHABLE -> context.getString(R.string.kachi_vn_r_unreachable)
        TeachGuard.Code.SHORT_ONE_TAKE -> context.getString(R.string.kachi_vn_r_short_once, r.detail)
        TeachGuard.Code.ALREADY_KNOWN -> context.getString(R.string.kachi_vn_r_known, r.detail)
        TeachGuard.Code.DUPLICATE -> context.getString(R.string.kachi_vn_r_duplicate)
        TeachGuard.Code.STEALS_FUZZY -> context.getString(R.string.kachi_vn_r_steals, r.detail)
        TeachGuard.Code.MISMATCH_WORD -> context.getString(R.string.kachi_vn_r_mismatch)
        TeachGuard.Code.NO_BIAS -> context.getString(R.string.kachi_vn_r_no_bias, r.detail)
        TeachGuard.Code.NEAR_COMMAND -> context.getString(R.string.kachi_vn_r_near_command)
        TeachGuard.Code.NEAR_OTHER_APP -> context.getString(R.string.kachi_vn_r_near_app, r.detail)
        TeachGuard.Code.ONE_WORD -> context.getString(R.string.kachi_vn_r_one_word)
    }

    private companion object {
        /** OQ4 — tối thiểu 2, tối đa 3 lượt nói mỗi lần dạy. */
        const val MIN_TAKES = 2
        const val MAX_TAKES = 3
        const val DISABLED = 0.4f

        /** Ngắt dòng giữa các lý do — ký tự điều khiển, không phải chữ (không qua tài nguyên). */
        val NL: String = System.lineSeparator()
    }
}
