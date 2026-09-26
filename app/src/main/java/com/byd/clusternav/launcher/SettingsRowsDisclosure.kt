package com.byd.clusternav.launcher

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 2.74 · R3 — KHỐI **GẬP/MỞ** của màn Cài đặt (component gập/mở ĐẦU TIÊN) ══════════════════════════════════
 *
 * Phần mở rộng của [SettingsRows] (tách tệp vì tệp kia đã **495/500 dòng** — CLAUDE.md §4.1; cùng lối
 * `SettingsRowsColor.kt`): *"một nơi dựng component"* vẫn là `SettingsRows` — hai hàm dưới dùng lại `stackLp` /
 * `context` của nó, KHÔNG dựng lề riêng (`SettingsStackMarginContractTest` canh đúng điều đó).
 *
 * ## Vì sao phải gập, và vì sao thân dựng TRỄ
 * Danh sách câu nói được có ~150 dòng (33 nút × 2-3 câu + 64 datum + các họ app/nhạc/bố cục). Bày phẳng hết thì
 * nhóm *Giọng nói* dài gấp nhiều lần mọi nhóm khác — vượt trần *"~2 màn cuộn mỗi nhóm"* của IA v2 R4 — và tầng vẽ
 * phải dựng ~300 `TextView` **ngay lúc mở trang**, trên thread giao diện của đầu xe. Nên: mặc định GẬP hết, tiêu đề
 * nói sẵn **số câu** (người dùng biết mình sắp mở cái gì), và thân chỉ được dựng ở **lần mở đầu tiên** ([fill] gọi
 * một lần, sau đó chỉ lật `visibility`).
 *
 * ## Không lưu bền trạng thái gập
 * Không sinh khoá prefs mới (khỏi đụng `SettingsCoverageContractTest`, và một cái nhóm nào-đang-mở không phải là
 * *cấu hình*). Trang vẫn được `SettingsPanel.pages` giữ nguyên nên đổi nhóm ở rail rồi quay lại vẫn thấy đúng nhóm
 * đang mở; một lượt `invalidateAll()` (state xe đổi) thì dựng lại trang và mọi nhóm gập lại — chấp nhận được, và
 * nói ra ở đây để không ai đi tìm một bug không tồn tại.
 */

/**
 * Một khối gập/mở: [title] tiêu đề đã dịch · [count] chữ đếm bên phải · [fill] dựng thân, gọi **một lần**.
 *
 * `count` là một chuỗi (không phải `Int`) vì số đếm phải đi qua tài nguyên có tham số định dạng — `:app` không
 * được ghép chữ số với chữ bằng tay (`LauncherI18nContractTest`).
 */
internal class Disclosure(val title: String, val count: String, val fill: (LinearLayout) -> Unit)

/**
 * Thẻ gập/mở: hàng tiêu đề bấm được (cao ≥ [KachiSpace.TOUCH]) + thân ẩn.
 *
 * Nền đặt ở **thẻ ngoài** (không ở hàng tiêu đề) để lúc mở ra tiêu đề và thân đọc thành **một** thẻ, chứ không
 * thành hai thẻ dính nhau — đúng lỗi nhịp dọc mà design system §2 lập ra để chữa.
 */
internal fun SettingsRows.disclosureRow(group: Disclosure): View {
    val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setPadding(dpi(context, Sp.M), 0, dpi(context, Sp.M), dpi(context, Sp.M))
    }
    val chevron = TextView(context).apply {
        setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.BODY)
        gravity = Gravity.CENTER
        text = GLYPH_CLOSED
    }
    val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dpi(context, Sp.TOUCH)
        val p = dpi(context, Sp.M); setPadding(p, p, p, p)
        addView(TextView(context).apply {
            text = group.title; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY, bold = true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = group.count; setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
        })
        addView(chevron, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).also { it.marginStart = dpi(context, Sp.S) })
    }
    var filled = false
    header.setOnClickListener {
        val open = body.visibility != View.VISIBLE
        // Dựng thân ở lần mở ĐẦU TIÊN — xem KDoc tệp về ngân sách thread giao diện.
        if (open && !filled) { group.fill(body); filled = true }
        body.visibility = if (open) View.VISIBLE else View.GONE
        chevron.text = if (open) GLYPH_OPEN else GLYPH_CLOSED
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = KachiTheme.surface(context, Sp.RADIUS_L)
        layoutParams = stackLp()
        addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        addView(body, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
    }
}

/**
 * Một dòng trong thân khối gập: câu nói được (đậm) + việc nó làm (mờ, cỡ chú thích).
 *
 * Hai `TextView` chứ không một chuỗi ghép: câu và phần giải thích có **hai vai đọc khác nhau** (một cái người ta
 * đọc để NÓI LẠI, một cái để hiểu), và ghép chúng bằng một dấu gạch trong mã là dựng một mẫu định dạng không đi
 * qua tài nguyên.
 */
internal fun SettingsRows.disclosureLine(phrase: String, does: String): View =
    LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = stackLp(topGap = Sp.S)
        addView(TextView(context).apply {
            text = phrase; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
        })
        addView(TextView(context).apply {
            text = does; setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
        })
    }

/** Dấu gập/mở — ký hiệu hình học, không phải chữ (không đi qua tài nguyên, không dịch). */
private const val GLYPH_CLOSED = "▸"
private const val GLYPH_OPEN = "▾"
