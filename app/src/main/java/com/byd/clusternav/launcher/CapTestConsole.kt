package com.byd.clusternav.launcher

import android.app.AlertDialog
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ CÔNG CỤ KIỂM TRA TỪNG NÚT XE (owner 2026-09-15) ══════════════════════════════════════════════════════
 *
 * Bảng đi hết mọi **thông tin + hành động** ([CapabilityTestPlan]) để soát cạn trên xe. Mỗi mục:
 *  - **Hành động** → popup "Đang test: X" + diễn giải + nút **Chạy** (gửi lệnh qua [SettingsDeps.runAction]) →
 *    người dùng tự chấm **OK / Không OK** → ghi vào [CapTestStore].
 *  - **Thông tin** → popup hiện diễn giải + **giá trị đọc được** ([SettingsDeps.readInfo]); không có nút Chạy.
 *  - Nút "**Bắt đầu / Tiếp tục**" chạy TUẦN TỰ từ mục chưa soát đầu tiên, xong cái này tự sang cái kế.
 *
 * Vì sao ở đây (một console trong Cài đặt › Hệ thống) chứ không phải Activity riêng: cùng lối [VoiceTextConsole] —
 * tái dùng khung màn Cài đặt (cuộn, nhớ trang) thay vì mở một vòng đời thứ hai. Chữ giao diện qua `R.string` (bài
 * canh i18n cấm chuỗi Việt viết cứng ở `:app`); nhãn + diễn giải của từng mục đã song ngữ sẵn từ `:core`.
 */
class CapTestConsole(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {
    private val store = CapTestStore(context)
    private val items = CapabilityTestPlan.items()
    /** Bản kết quả đã giải mã, GIỮ trong bộ nhớ — tránh giải mã lại cả khối prefs cho từng hàng (187 hàng) mỗi lần
     *  dựng/tô lại huy hiệu. Làm tươi sau mỗi lần ghi ([mark]) / xoá ([confirmClear]). */
    private var results: Map<String, CapTestResult> = store.load()
    private var summaryView: TextView? = null
    private val badges = HashMap<String, TextView>()

    fun build(body: LinearLayout) {
        body.addView(rows.sectionLabel(s(R.string.kachi_captest_title)))
        body.addView(rows.note(s(R.string.kachi_captest_note)))

        summaryView = TextView(context).apply {
            setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
            setPadding(0, dpi(context, Sp.S), 0, dpi(context, Sp.S))
        }
        body.addView(summaryView)

        body.addView(rows.button(s(R.string.kachi_captest_start)) { startSequential() })
        body.addView(rows.button(s(R.string.kachi_captest_export)) { showReport() })
        body.addView(rows.button(s(R.string.kachi_captest_snapshot)) { snapshotLogs() })
        body.addView(rows.button(s(R.string.kachi_captest_clear)) { confirmClear() })
        // Owner 2026-09-15: hiện ĐƯỜNG DẪN log trên thẻ + lệnh adb pull ngay tại đây, khỏi phải nhớ.
        body.addView(rows.note(context.getString(
            R.string.kachi_captest_logs_where, KachiLog.pullPath(context), KachiLog.pullCommand(context),
        )))

        var lastDomain: Domain? = null
        items.forEach { item ->
            if (item.domain != lastDomain) {
                body.addView(rows.subHeader(item.domain.displayLabel)); lastDomain = item.domain
            }
            body.addView(itemRow(item))
        }
        refreshSummary()
    }

    // ── Một hàng trong danh sách: [diễn giải] … [huy hiệu trạng thái] · chạm để test riêng mục đó ──
    private fun itemRow(item: CapTestItem): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = dpi(context, Sp.S); setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.bottomMargin = dpi(context, Sp.XS) }
            setOnClickListener { showItemDialog(item, onDone = null) }
        }
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(context).apply {
            text = kindTag(item) + item.displayLabel
            setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
        })
        col.addView(TextView(context).apply {
            text = item.displayDesc
            setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
        })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        val badge = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
            val p = dpi(context, Sp.S); setPadding(p, dpi(context, Sp.XS), p, dpi(context, Sp.XS))
        }
        badges[item.id] = badge
        paintBadge(item.id, badge)
        row.addView(badge)
        return row
    }

    private fun kindTag(item: CapTestItem): String {
        val k = if (item.kind == CapTestKind.INFO) s(R.string.kachi_captest_kind_info) else s(R.string.kachi_captest_kind_act)
        return "[$k] "
    }

    private fun paintBadge(id: String, badge: TextView) {
        val (label, color) = when (results[id]?.verdict) {
            CapTestVerdict.OK -> s(R.string.kachi_captest_badge_ok) to KachiTheme.GREEN
            CapTestVerdict.NOT_OK -> s(R.string.kachi_captest_badge_notok) to KachiTheme.RED
            else -> s(R.string.kachi_captest_badge_untested) to KachiTheme.MUT2
        }
        badge.text = label
        badge.setTextColor(c(color))
    }

    private fun refreshSummary() {
        val sum = CapTestSummary.of(items, results)
        summaryView?.text = context.getString(R.string.kachi_captest_summary, sum.tested, sum.total, sum.ok, sum.notOk)
    }

    // ── Popup cho MỘT mục. onDone != null ⇒ đang chạy tuần tự (chấm xong tự sang mục kế). ──
    private fun showItemDialog(item: CapTestItem, onDone: (() -> Unit)?) {
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dpi(context, Sp.L); setPadding(p, dpi(context, Sp.M), p, dpi(context, Sp.S))
        }
        view.addView(TextView(context).apply {
            text = item.domain.displayLabel + "  ·  " + kindTag(item).trim()
            setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
        })
        view.addView(TextView(context).apply {
            text = item.displayDesc
            setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
            setPadding(0, dpi(context, Sp.S), 0, dpi(context, Sp.S))
        })
        if (!item.isRouted) {
            view.addView(hint(s(R.string.kachi_captest_unmapped), KachiTheme.MUT2))
        }
        val feedback = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION); visibility = View.GONE
            setPadding(0, dpi(context, Sp.S), 0, 0)
        }

        if (item.kind == CapTestKind.INFO) {
            val valueView = TextView(context).apply {
                text = s(R.string.kachi_captest_reading)
                setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
            }
            view.addView(valueView)
            // Đọc HAL = binder reflection CHẶN (xem `AppContainer.carScope` chạy trên Dispatchers.IO vì "IPC CHẶN"):
            // KHÔNG đọc trên luồng vẽ — đọc trên luồng nền rồi `post` kết quả về (cùng lối [VoiceTextConsole]).
            Thread({
                val value = runCatching { deps.readInfo(item.id) }.getOrNull()
                valueView.post {
                    valueView.text = if (value != null) context.getString(R.string.kachi_captest_value, value)
                    else s(R.string.kachi_captest_value_none)
                    valueView.setTextColor(c(if (value != null) KachiTheme.GREEN else KachiTheme.MUT))
                }
            }, "KachiCapRead").start()
        } else {
            if (item.needsConfirm) view.addView(hint(s(R.string.kachi_captest_warn_body), KachiTheme.RED))
            view.addView(feedback)
            addRunButtons(view, item, feedback)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.kachi_captest_running, item.displayLabel))
            .setView(wrapScroll(view))
            .setPositiveButton(s(R.string.kachi_captest_ok)) { _, _ -> mark(item.id, CapTestVerdict.OK, onDone) }
            .setNegativeButton(s(R.string.kachi_captest_notok)) { _, _ -> mark(item.id, CapTestVerdict.NOT_OK, onDone) }
            .setNeutralButton(s(R.string.kachi_captest_skip)) { _, _ -> onDone?.invoke() }
            .create()
        dialog.show()
    }

    /**
     * Nút Chạy cho một mục HÀNH ĐỘNG.
     *
     * ⚠ [ĐO xe 2026-09-15] Trước đây MỘT nút "Chạy" bắn `item.runArg = defaultPrimary` = **1 (mở/bật)** cho mọi
     * control ⇒ với kính (COVER) chỉ bao giờ gửi được lệnh **MỞ**, chiều ĐÓNG không có đường test → owner tưởng
     * *"kính đóng không được"* (thực tế `setBodyWindowCtrlState(w,2)` đóng tin cậy cả 4 kính — đo qua T-BRIDGE `hal`).
     *
     * Sửa GENERIC (CLAUDE.md §7, không hardcode tên gói): control khai từ **2 chiều/lựa chọn trở lên**
     * ([ControlDef.displayArgs] ≥ 2 — kính "Đóng"/"Mở", SELECT nhiều mức) thì render **mỗi chiều một nút**, `arg` =
     * chỉ số chiều (khớp `CarControlPort.actByKind`: COVER `arg>0`, SELECT = chỉ số). Control một chiều (BUTTON bấm,
     * STEP một giá trị, TOGGLE không khai nhãn chiều) giữ nút "Chạy" đơn với `runArg` như cũ.
     */
    private fun addRunButtons(container: LinearLayout, item: CapTestItem, feedback: TextView) {
        val dirs = ControlRegistry.byId(item.id)?.displayArgs ?: emptyList()
        if (dirs.size >= 2) {
            dirs.forEachIndexed { index, dirLabel ->
                container.addView(rows.button(dirLabel) { fireAction(item, index, feedback) })
            }
        } else {
            container.addView(rows.button(s(R.string.kachi_captest_run)) { fireAction(item, item.runArg, feedback) })
        }
    }

    private fun fireAction(item: CapTestItem, arg: Int, feedback: TextView) {
        val ok = runCatching { deps.runAction(item.id, arg) }.getOrDefault(false)
        feedback.visibility = View.VISIBLE
        feedback.text = s(if (ok) R.string.kachi_captest_sent else R.string.kachi_captest_notsent)
        feedback.setTextColor(c(if (ok) KachiTheme.GREEN else KachiTheme.MUT))
    }

    private fun mark(id: String, verdict: CapTestVerdict, onDone: (() -> Unit)?) {
        store.record(id, verdict)
        results = store.load()
        badges[id]?.let { paintBadge(id, it) }
        refreshSummary()
        onDone?.invoke()
    }

    // ── Chạy tuần tự từ mục CHƯA SOÁT đầu tiên; xong tự sang mục kế. ──
    private fun startSequential() {
        results = store.load()
        val start = items.indexOfFirst { results[it.id]?.verdict.let { v -> v == null || v == CapTestVerdict.UNTESTED } }
        if (start < 0) { toastDone(); return }
        step(start)
    }

    private fun step(index: Int) {
        if (index >= items.size) { toastDone(); return }
        showItemDialog(items[index]) { step(index + 1) }
    }

    private fun toastDone() {
        AlertDialog.Builder(context)
            .setMessage(context.getString(R.string.kachi_captest_done, items.size))
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showReport() {
        val report = store.exportReport()
        val saved = KachiLog.saveCaptestReport(context, report)   // xuất báo cáo = cũng ghi ra thẻ ngay
        val header = if (saved != null) context.getString(R.string.kachi_captest_report_saved, saved.absolutePath) + "\n\n" else ""
        val tv = TextView(context).apply {
            text = header + report
            setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
            typeface = android.graphics.Typeface.MONOSPACE
            val p = dpi(context, Sp.L); setPadding(p, dpi(context, Sp.M), p, dpi(context, Sp.M))
        }
        AlertDialog.Builder(context)
            .setTitle(s(R.string.kachi_captest_report_title))
            .setView(wrapScroll(tv))
            .setPositiveButton(android.R.string.ok, null).show()
    }

    /**
     * Chụp một phát toàn bộ logcat ra thẻ (luồng nền — exec logcat có thể chặn), rồi báo đường dẫn.
     *
     * ## [SOÁT OCR 2026-09-16 · P2] Vì sao phải hỏi màn còn sống không TRƯỚC khi `show()`
     * [KachiLog.snapshot] chạy `logcat -d` — CHẶN, đo được vài giây trên xe. Trong khoảng ấy người dùng đóng
     * được bảng Cài đặt, hoặc đổi bảng màu (⇒ `recreate()`). `AlertDialog…show()` trên một activity đã huỷ ném
     * `WindowManager.BadTokenException`, mà đây là luồng nền `post` về — không ai bắt ⇒ **sập launcher trên xe
     * đang chạy**. Cùng lá chắn mà [VoiceTextConsole.ask] đã dựng cho đúng họ lỗi này: kiểm vòng đời rồi mới
     * bung, và `runCatching` cho khe hở còn lại giữa phép kiểm và lời gọi.
     */
    private fun snapshotLogs() {
        Thread({
            val f = KachiLog.snapshot(context)
            summaryView?.post {
                val host = context as? android.app.Activity
                if (host != null && (host.isFinishing || host.isDestroyed)) return@post
                runCatching {
                    AlertDialog.Builder(context)
                        .setMessage(
                            if (f != null) context.getString(R.string.kachi_captest_snapshot_done, f.absolutePath)
                            else s(R.string.kachi_captest_value_none),
                        )
                        .setPositiveButton(android.R.string.ok, null).show()
                }
            }
        }, "KachiLogSnap").start()
    }

    private fun confirmClear() {
        SettingsDialogs.confirm(
            context, s(R.string.kachi_captest_clear), s(R.string.kachi_captest_clear_confirm),
            s(R.string.kachi_captest_clear),
        ) {
            store.clear()
            results = store.load()
            badges.forEach { (id, b) -> paintBadge(id, b) }
            refreshSummary()
        }
    }

    private fun hint(text: String, color: String) = TextView(context).apply {
        this.text = text
        setTextColor(c(color)); setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
        setPadding(0, dpi(context, Sp.XS), 0, dpi(context, Sp.XS))
    }

    private fun wrapScroll(inner: View): View = ScrollView(context).apply {
        addView(inner); isFillViewport = true
        // Trần chiều cao popup = 60% màn (px, không qua thang dp) — cuộn nếu báo cáo/diễn giải dài.
        val h = (context.resources.displayMetrics.heightPixels * 0.6f).toInt()
        layoutParams = android.view.ViewGroup.LayoutParams(MATCH, h)
    }

    private fun s(id: Int): String = context.getString(id)

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
