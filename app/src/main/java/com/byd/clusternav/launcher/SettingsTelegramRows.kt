package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R

/**
 * TELEGRAM-REMOTE (spec `kachi-telegram-remote.html` T5 · T10) — khối Cài đặt › Dẫn đường:
 *  - **Điểm đến cho chuyến tới** (T5): chọn từ sổ địa chỉ ⇒ `setPendingDestination`; nút Xoá ⇒ `clearPendingDestination`.
 *  - **Điều khiển qua Telegram** (T10): công tắc bật/tắt bridge · token · chat-id · nút Kiểm tra · công tắc điều khiển xe.
 *
 * Tệp riêng vì `SettingsSectionsNav` sát trần 500 dòng (cùng cách `SettingsNowPlayingRows`). Mọi lượt ghi đi qua
 * cầu ([ClusterNavBridge] extensions ở `ClusterNavBridgeTelegram.kt`); FGS bridge + vòng ưu tiên tự đồng bộ.
 */
internal class SettingsTelegramRows(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {
    private val bridge get() = deps.bridge
    private lateinit var container: LinearLayout

    fun build(body: LinearLayout) {
        container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(container)
        rebuild()
    }

    private fun rebuild() {
        container.removeAllViews()
        pending(container)
        telegram(container)
    }

    // ── T5 · Điểm đến cho chuyến tới ────────────────────────────────────────────────────────────
    private fun pending(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_pending_title)))
        val summary = bridge.pendingDestinationSummary()
        body.addView(rows.statusRow(
            // ⚠ statusRow nhận MÃ MÀU (#rrggbb, qua Color.parseColor) — KHÔNG phải tên. "MUT"/"GREEN" chữ trần làm
            // trang Dẫn đường sập mỗi lần mở (lỗi xe 3.1.18, 26/10). Luôn truyền KachiTheme.*.
            if (summary.isEmpty()) KachiTheme.MUT2 else KachiTheme.GREEN,
            if (summary.isEmpty()) context.getString(R.string.kachi_pending_none)
            else context.getString(R.string.kachi_pending_set, summary),
        ).view)
        body.addView(rows.button(context.getString(R.string.kachi_pending_pick)) { pickPlace() })
        if (summary.isNotEmpty()) {
            body.addView(rows.button(context.getString(R.string.kachi_pending_clear)) {
                bridge.clearPendingDestination()
                rebuild()
            })
        }
    }

    /** Chọn một nơi trong sổ địa chỉ làm điểm đến chờ (dẫn bằng app mặc định của hồ sơ). */
    private fun pickPlace() {
        val places = deps.state().savedPlaces
        if (places.isEmpty()) {
            SettingsDialogs.notice(context, context.getString(R.string.kachi_pending_title),
                context.getString(R.string.kachi_pending_no_places))
            return
        }
        SettingsDialogs.pick(
            context, context.getString(R.string.kachi_pending_pick),
            places.map { it.name }, context.getString(R.string.kachi_pending_no_places),
        ) { idx ->
            val place = places[idx]
            val apps = listOf(com.byd.clusternav.Prefs.voiceNavDefaultApp(context))
            if (!bridge.setPendingDestination(place, apps)) {
                SettingsDialogs.notice(context, context.getString(R.string.kachi_pending_title),
                    context.getString(R.string.kachi_pending_need_coords, place.name))
                return@pick
            }
            rebuild()
        }
    }

    // ── T10 · Điều khiển qua Telegram ───────────────────────────────────────────────────────────
    private fun telegram(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_tg_title)))
        body.addView(rows.checkRow(
            on = bridge.telegramEnabled(),
            title = context.getString(R.string.kachi_tg_enabled_title),
            sub = context.getString(R.string.kachi_tg_enabled_sub),
        ) { on -> bridge.setTelegramEnabled(on); rebuild() })

        if (bridge.telegramEnabled()) {
            body.addView(rows.button(tokenLabel()) {
                SettingsDialogs.askText(context, context.getString(R.string.kachi_tg_token), bridge.telegramToken()) {
                    bridge.setTelegramToken(it); rebuild()
                }
            })
            body.addView(rows.button(chatLabel()) {
                SettingsDialogs.askText(context, context.getString(R.string.kachi_tg_chat), bridge.telegramChatId()) {
                    bridge.setTelegramChatId(it); rebuild()
                }
            })
            body.addView(rows.button(context.getString(R.string.kachi_tg_test)) {
                bridge.telegramTestConnection { ok ->   // P1-6: chạy nền, kết quả về main
                    SettingsDialogs.notice(context, context.getString(R.string.kachi_tg_test),
                        when (ok) {
                            null -> context.getString(R.string.kachi_tg_test_incomplete)
                            true -> context.getString(R.string.kachi_tg_test_ok)
                            false -> context.getString(R.string.kachi_tg_test_fail)
                        })
                }
            })
        }
    }

    private fun tokenLabel(): String {
        val t = bridge.telegramToken()
        val shown = if (t.isEmpty()) context.getString(R.string.kachi_tg_token_unset)
        else "…" + t.takeLast(4)
        return context.getString(R.string.kachi_tg_token) + ": " + shown
    }

    private fun chatLabel(): String {
        val c = bridge.telegramChatId()
        return context.getString(R.string.kachi_tg_chat) + ": " +
            c.ifEmpty { context.getString(R.string.kachi_tg_chat_unset) }
    }
}
