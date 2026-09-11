package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * BẢNG VẼ BỐ CỤC (P9 bước 2) — chứa [GridEditorView] cùng các nút và phần báo lỗi.
 *
 * Dựng **bằng code** như bảng Tuỳ biến ⇒ 0 tệp XML bị đụng (không cần xin đóng dấu lại layout niêm phong).
 *
 * ## Ba luật của bề mặt này
 *  1. **Lưu bị CHẶN khi bố cục đang lỗi**, và nói rõ lỗi gì. Lưu một bố cục đè nhau thì màn chính sẽ lùi về bố cục
 *     sẵn — người dùng bấm Lưu mà không thấy gì đổi, tưởng app hỏng.
 *  2. **"Còn ô trống" KHÔNG phải lỗi** — chỉ là thông tin. Ép phủ kín màn là ép người dùng theo ý mình.
 *  3. **Nói thẳng trần khung của bản này** thay vì im lặng chặn nút Thêm. Người dùng cần biết vì sao không thêm được.
 */
class LayoutEditorPanel(
    context: Context,
    initial: GridLayout,
    private val fallbackPreset: LayoutPreset,
    private val onSave: (GridLayout) -> Unit,
    /** Bỏ bố cục tự vẽ, quay về bố cục sẵn. */
    private val onClear: () -> Unit,
    private val onClose: () -> Unit,
    /** Trần khung của bản hiện tại — bước sau sẽ nới. */
    private val cap: Int = WorkspaceState.SLOT_CAP,
) : FrameLayout(context) {

    private val editor = GridEditorView(context)
    private val problem: TextView
    private val info: TextView
    private val saveBtn: TextView
    private val addBtn: TextView
    private val delBtn: TextView

    private var current: GridLayout =
        if (initial.frames.isNotEmpty()) initial
        else WorkspaceGrid.fromPreset(fallbackPreset) ?: GridLayout(listOf(GridFrame(0, 0, 12, 6)))

    init {
        // [ĐO] nền trong mờ làm giao diện launcher lọt xuyên qua: tiêu đề đè chữ ngày của thanh trên, nút Đóng đè
        // nút "Cài đặt", và thanh nút xe nằm ngay dưới hàng nút của bảng ⇒ rối, khó đọc. Đây là bề mặt làm-một-việc,
        // thấy màn chính phía sau không được gì. ⇒ nền ĐỤC.
        setBackgroundColor(Color.parseColor(KachiTheme.BG))
        isClickable = true    // chặn chạm lọt xuống màn chính bên dưới

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        // ── Đầu bảng ──
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "Vẽ bố cục"
                setTextColor(Color.parseColor(KachiTheme.INK))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(pill("Đóng") { onClose() })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(context).apply {
            text = "Kéo giữa khung để di chuyển · kéo góc dưới-phải để đổi cỡ. Khung luôn bám ô lưới."
            setTextColor(Color.parseColor(KachiTheme.MUT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(4), 0, dp(10))
        })

        // ── Trình vẽ ──
        editor.layout = current
        editor.selected = 0
        editor.onChanged = { l -> current = l; refresh() }
        editor.onSelected = { refresh() }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ── Báo lỗi + thông tin ──
        problem = TextView(context).apply {
            text = " "        // giữ một dòng chiều cao ngay từ đầu
            minLines = 1; maxLines = 1
            setTextColor(Color.parseColor(KachiTheme.RED))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(8), 0, 0)
        }
        info = TextView(context).apply {
            setTextColor(Color.parseColor(KachiTheme.MUT2))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, dp(8))
        }
        root.addView(problem); root.addView(info)

        // ── Nút ──
        addBtn = pill("Thêm khung") { addFrame() }
        delBtn = pill("Xoá khung đang chọn") { removeSelected() }
        saveBtn = pill("Lưu") { save() }
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(addBtn); addView(space()); addView(delBtn); addView(space())
            addView(pill("Về bố cục sẵn") { onClear(); onClose() })
            addView(space()); addView(saveBtn)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        refresh()
    }

    // ── Việc ─────────────────────────────────────────────────────────────────────────────────────

    private fun addFrame() {
        if (current.frames.size >= cap) return
        // Đặt khung mới vào chỗ TRỐNG rộng nhất nếu tìm được, không thì đặt giữa — để nó không sinh ra đã đè.
        val spot = firstFreeSpot() ?: GridFrame(4, 2, 4, 2)
        current = GridLayout(current.frames + spot)
        editor.layout = current
        editor.selected = current.frames.size - 1
        refresh()
    }

    /** Ô trống đầu tiên đủ chỗ cho một khung nhỏ nhất — quét theo dòng, dừng ở chỗ đầu tiên vừa. */
    private fun firstFreeSpot(): GridFrame? {
        val w = 3; val h = 2
        for (r in 0..WorkspaceGrid.ROWS - h) for (c in 0..WorkspaceGrid.COLS - w) {
            val cand = GridFrame(c, r, w, h)
            if (current.frames.none { it.overlaps(cand) }) return cand
        }
        return null
    }

    private fun removeSelected() {
        val i = editor.selected
        if (i !in current.frames.indices || current.frames.size <= 1) return
        current = GridLayout(current.frames.toMutableList().also { it.removeAt(i) })
        editor.layout = current
        editor.selected = (i - 1).coerceAtLeast(0)
        refresh()
    }

    private fun save() {
        if (current.problems().isNotEmpty() || current.frames.size > cap) return
        onSave(current)
        onClose()
    }

    private fun refresh() {
        val probs = current.problems()
        val overCap = current.frames.size > cap
        problem.text = when {
            overCap -> "Quá $cap khung — bản này đỡ tối đa $cap khung."
            probs.isEmpty() -> ""
            else -> "Chưa lưu được: " + probs.joinToString(" · ")
        }
        // Giữ CHỖ của dòng lỗi (INVISIBLE, không GONE): ẩn hẳn thì khung vẽ giãn ra rồi co lại mỗi lần lỗi
        // xuất hiện/mất ⇒ hình đang kéo bị giật cỡ giữa lúc kéo.
        problem.visibility = if (problem.text.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE

        val empty = current.uncoveredCells()
        info.text = buildString {
            append("${current.frames.size} khung · ")
            // "Còn ô trống" là THÔNG TIN, không phải lỗi: nền sẽ hiện ra ở đó.
            append(if (empty == 0) "phủ kín màn" else "còn $empty ô trống (nền hiện ra ở đó)")
            if (current.frames.size >= cap) append(" · đã đủ $cap khung, bản sau sẽ nới")
        }

        val canSave = probs.isEmpty() && !overCap
        saveBtn.alpha = if (canSave) 1f else 0.4f
        addBtn.alpha = if (current.frames.size < cap) 1f else 0.4f
        delBtn.alpha = if (current.frames.size > 1) 1f else 0.4f
    }

    // ── Dựng nút ─────────────────────────────────────────────────────────────────────────────────

    private fun pill(text: String, onTap: () -> Unit) = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor(KachiTheme.INK))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(14), dp(9), dp(14), dp(9))
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Color.parseColor(KachiTheme.CARD2))
            setStroke(dp(1), Color.parseColor(KachiTheme.LINE))
        }
        setOnClickListener { onTap() }
    }

    private fun space() = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(8), 1)
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()
}
