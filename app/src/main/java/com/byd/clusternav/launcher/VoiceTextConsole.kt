package com.byd.clusternav.launcher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.AppContainer
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.system.PackageQueries
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ V1 · ĐƯỜNG THỬ BẰNG CHỮ ══════════════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R6. Một hàng trong *Cài đặt › Hệ thống & quyền › **Nâng cao***:
 * gõ một câu như khi nói → bộ phân tích ở `:core` → thi hành qua [VoiceDispatcher] → hiện câu phản hồi.
 *
 * ## Vì sao có bề mặt này khi mic còn chưa có
 * Hai lý do, cả hai đều là lý do **kỹ thuật**, không phải demo:
 *  1. **Nó làm tầng chữ hết là code chết.** `CastShell.evictVd` từng viết cẩn thận, compile sạch, và **chưa từng
 *     được gọi** (CLAUDE.md §8). Một bộ phân tích 194 mã mà không có chỗ nào gọi tới thì đúng hình dạng đó. Ở đây
 *     mỗi nhánh ý định có một đích thật, và bài canh dây nối đếm được từng nhánh.
 *  2. **Nó là cách rẻ nhất để đo ngữ pháp trên xe thật.** Lên xe, gõ câu, xem nó hiểu gì — không cần mic, không
 *     cần mô hình, không cần mạng. Đúng tinh thần §11 (*"app tự chụp, không bắt user gõ adb"*): phần kỹ thuật gom
 *     vào một hàng trong Cài đặt, anh em chỉ việc chụp màn hình gửi về.
 *
 * ## Vì sao nằm trong *Nâng cao* chứ không phải một nhóm riêng
 * Nó là **màn chẩn đoán**, cùng loại với *Dữ liệu VietMap* và *Chẩn đoán* đứng ngay cạnh — không phải một bề mặt
 * cấu hình. Một nhóm riêng ở rail sẽ hứa với người dùng rằng Kachi đã có điều khiển bằng giọng nói, mà nó thì
 * chưa (R8).
 */
class VoiceTextConsole(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    /** Nhãn app → tên gói, nạp MỘT lần cho mỗi lần dựng trang (mở màn Cài đặt lại thì nạp lại). */
    private val appsByLabel: Map<String, String> by lazy {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        PackageQueries.queryActivities(pm, intent)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                ri.loadLabel(pm).toString() to pkg
            }
            .toMap()
    }

    fun build(body: LinearLayout) {
        body.addView(rows.note(context.getString(R.string.kachi_voice_note)))

        val out = TextView(context).apply {
            setTextColor(c(KachiTheme.MUT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.CAPTION)
            setPadding(0, dpi(context, Sp.S), 0, 0)
            text = context.getString(R.string.kachi_voice_idle)
        }

        val input = EditText(context).apply {
            hint = context.getString(R.string.kachi_voice_hint)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(c(KachiTheme.INK))
            setHintTextColor(c(KachiTheme.MUT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiType.BODY)
            background = KachiTheme.card(context, Sp.RADIUS_XL, KachiTheme.FIELD, KachiTheme.LINE)
            val p = dpi(context, Sp.M)
            setPadding(p, p, p, p)
        }
        body.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpi(context, Sp.S)
        })

        val log = ReplyLog(out)
        val dispatcher = dispatcher { line -> out.post { log.add(line) } }
        body.addView(rows.button(context.getString(R.string.kachi_voice_run)) {
            val text = input.text?.toString().orEmpty()
            if (text.isBlank()) {
                log.add(context.getString(R.string.kachi_voice_empty))
            } else {
                log.clear()
                // Hiện "đã hiểu là…" TRƯỚC khi thi hành: người đọc thấy máy hiểu đúng hay sai ngay cả khi lệnh
                // không ăn (off-car mọi lệnh xe đều no-op) — đó mới là thứ cần đo ở pha này.
                // Phân tích ĐÚNG MỘT lần rồi chạy chính danh sách vừa hiện (xem KDoc `VoiceDispatcher.execute`).
                val intents = dispatcher.preview(text)
                intents.forEach { log.add(ARROW + VoiceReply.preview(it)) }
                dispatcher.execute(intents)
            }
        })
        body.addView(out)
    }

    /**
     * Sổ dòng phản hồi.
     *
     * Giữ **danh sách dòng** rồi ghép lại, thay vì nối chuỗi vào `TextView.text`: đọc lại `text` để nối là lấy một
     * `CharSequence` đã qua tầng vẽ làm nguồn sự thật — và nó cũng làm `LauncherI18nContractTest.moi chu tren man
     * deu di qua tai nguyen` báo đỏ (đúng: một literal đi thẳng vào bề mặt chữ).
     */
    private class ReplyLog(private val view: TextView) {
        private val lines = ArrayList<String>()
        fun add(line: String) { lines.add(line); paint() }
        fun clear() { lines.clear(); paint() }
        private fun paint() { view.text = lines.joinToString(System.lineSeparator()) }
    }

    /**
     * Dựng cầu sang các đường đang chạy. Mọi lambda ở đây trỏ tới **đúng** thứ mà một cú chạm dùng — xem KDoc
     * [VoiceDispatcher] về vì sao không được có đường thứ hai.
     */
    private fun dispatcher(say: (String) -> Unit): VoiceDispatcher = VoiceDispatcher(
        control = { AppContainer.get(context).carControl },
        state = deps.state,
        media = { MediaBridge(context) },
        appsByLabel = { appsByLabel },
        openApp = { pkg -> deps.openAppByPackage(pkg) },
        openAppList = deps.openAppList,
        openSettings = { deps.openSettingsGroup(SettingsGroup.SYSTEM) },
        onSwitchProfile = deps.onSwitchProfile,
        confirm = { question, onYes, onNo -> ask(question, onYes, onNo) },
        say = say,
    )

    /**
     * Hộp hỏi lại cho việc cần xác nhận ([com.byd.clusternav.launcher.voice.VoiceRisk.CONFIRM]).
     *
     * `setOnCancelListener` chứ không chỉ nút **Huỷ**: bấm ra ngoài hộp / bấm Back cũng là *"không đồng ý"*, và
     * `VoiceDispatcher` đang **chờ đúng một** trong hai lambda để biết có đi tiếp các vế sau hay không — nuốt mất
     * đường thoát đó là treo nửa cuối câu ghép không lời giải thích. `single` chặn gọi cả hai khi người dùng bấm
     * Huỷ (nút Huỷ ⇒ `onCancel` cũng nổ theo trên một số ROM).
     */
    private fun ask(question: String, onYes: () -> Unit, onNo: () -> Unit) {
        var answered = false
        fun single(block: () -> Unit) { if (!answered) { answered = true; block() } }
        AlertDialog.Builder(context)
            .setTitle(R.string.kachi_voice_confirm_title)
            .setMessage(question)
            .setPositiveButton(R.string.kachi_voice_confirm_yes) { _, _ -> single(onYes) }
            .setNegativeButton(R.string.kachi_voice_confirm_no) { _, _ -> single(onNo) }
            .setOnCancelListener { single(onNo) }
            .show()
    }

    private companion object {
        /** Dấu dẫn của dòng *"đã hiểu là…"* — ký hiệu, không phải chữ, nên không đi qua tài nguyên. */
        const val ARROW = "\u2192 "
    }
}
