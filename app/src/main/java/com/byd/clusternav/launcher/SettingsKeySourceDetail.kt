package com.byd.clusternav.launcher

import android.content.Context
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.voicekey.KeySourceEntry
import com.byd.clusternav.voicekey.KeySourceKind
import com.byd.clusternav.voicekey.KeySourceLog
import com.byd.clusternav.voicekey.KeySourceProbes
import com.byd.clusternav.voicekey.KeySourceVerdict

/**
 * ═══ L7 · KEY-SOURCE-SPLIT tầng 1 — DÒNG CHI TIẾT dưới tên nút vừa học (hộp "Đặt tên cho nút vừa bấm") ════════════
 *
 * Một dòng để anh em CHỤP MÀN HÌNH gửi về (CLAUDE.md §11 — không bắt ai gõ adb), ví dụ:
 * *"mã 291 · scan 115 · thiết bị "simulate-keys"#7 · nguồn: núm yên ngựa (AUDIO_VOLUME_CTRL_MODE=1) · đọc 3 ms"*.
 *
 * Chỉ HIỆN — không đổi tên gợi ý. (2.88 · tầng 2: lúc bấm Lưu, `SettingsKeysSection.learn` đọc lại CÙNG dòng nhật ký
 * để lưu nút kèm nguồn — xem `kachi-288-key-source-split` R1.)
 *
 * ## Vì sao làm mới vài nhịp
 * Hộp mở ngay khi bus học phím báo mã, còn lượt đọc HAL chạy ở luồng riêng với trần
 * [com.byd.clusternav.voicekey.KeySourceProbes.DEFAULT_BUDGET_MS] — lúc hộp mở có thể dòng đo chưa xong. Đọc lại mỗi
 * [STEP_MS] tới khi xong, tối đa [MAX_TRIES] nhịp (≈1,5 s ≫ trần 250 ms); quá thì ghi rõ "không đọc được (pending)"
 * thay vì treo chữ "đang đọc…" mãi. `postDelayed` trên chính TextView: hộp đóng ⇒ view rời cửa sổ ⇒ nhịp sau vô hại.
 */
internal object KeySourceDetailText {

    private const val STEP_MS = 150L
    private const val MAX_TRIES = 10

    /** Lý do hiện khi không có dòng đo nào cho lần học này (bộ đo chưa chạy). */
    private const val NO_SAMPLE = "no_sample"

    /** Lý do hiện khi lượt đo không xong sau [MAX_TRIES] nhịp. */
    private const val STILL_PENDING = "pending"

    fun bind(tv: TextView, code: Int, lookup: () -> KeySourceEntry?) {
        fun render(tries: Int) {
            val e = lookup()
            val settled = e?.reading != null
            val gaveUp = !settled && tries >= MAX_TRIES
            tv.text = text(tv.context, code, e, gaveUp)
            if (!settled && !gaveUp) tv.postDelayed({ render(tries + 1) }, STEP_MS)
        }
        render(0)
    }

    fun text(ctx: Context, code: Int, e: KeySourceEntry?, gaveUp: Boolean): String {
        val scan = e?.sample?.scanCode?.toString() ?: "?"
        val device = e?.let { KeySourceLog.deviceLabel(it) } ?: "?"
        val read = e?.reading?.takeIf { it.probe != null && it.readMs >= 0 }?.let { "${it.readMs} ms" } ?: "—"
        return ctx.getString(R.string.kachi_key_src_detail, code, scan, device, sourceLabel(ctx, e, gaveUp), read)
    }

    private fun sourceLabel(ctx: Context, e: KeySourceEntry?, gaveUp: Boolean): String {
        val failed = { reason: String -> ctx.getString(R.string.kachi_key_src_failed, reason) }
        val pending = { if (gaveUp) failed(if (e == null) NO_SAMPLE else STILL_PENDING) else ctx.getString(R.string.kachi_key_src_pending) }
        e ?: return pending()
        val reading = e.reading
        return when (val v = KeySourceProbes.verdict(reading)) {
            KeySourceVerdict.Pending -> pending()
            KeySourceVerdict.NotMeasured -> ctx.getString(R.string.kachi_key_src_not_measured)
            is KeySourceVerdict.Source -> "${kindLabel(ctx, v.kind)} (${KeySourceLog.tag(reading)})"
            is KeySourceVerdict.UnknownValue -> ctx.getString(R.string.kachi_key_src_unknown, KeySourceLog.tag(reading))
            is KeySourceVerdict.Failed -> failed(reading?.let { KeySourceLog.reason(it) } ?: v.failure.code)
        }
    }

    /** Nhãn người đọc của một nguồn — dùng chung cho dòng chi tiết và hậu tố tên nút học kèm nguồn (2.88 · R1/R4). */
    fun kindLabel(ctx: Context, kind: KeySourceKind): String = ctx.getString(
        when (kind) {
            KeySourceKind.CONSOLE_KNOB -> R.string.kachi_key_src_knob
            KeySourceKind.STEERING_WHEEL -> R.string.kachi_key_src_wheel
        },
    )
}
