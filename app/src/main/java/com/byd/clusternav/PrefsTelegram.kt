package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.launcher.telegram.TelegramConfig

/**
 * ═══ Khoá TELEGRAM REMOTE — tách khỏi [Prefs] theo VAI (TELEGRAM-REMOTE, trần 500 dòng) ══════════════════════
 *
 * Spec `docs/specs/kachi-telegram-remote.html` R10. Cùng cách [PrefsAutomation] / `PrefsVoiceV3`: hàm mở rộng của
 * [Prefs], **cùng tệp `clusternav_prefs`** (mở tệp thứ hai là dựng cửa thứ hai vào cùng chỗ lưu — thứ
 * `SettingsCatalog.PREFS_FILES` sinh ra để bắt).
 *
 * ## Bốn khoá theo XE (`ProfileScope.DEVICE_KEYS`)
 * Một chiếc xe một bot: token là secret dùng chung mọi hồ sơ, chat-id là chủ xe. Chép theo hồ sơ = nhập lại token
 * mỗi lần đổi tài xế + export hồ sơ mang token ra ngoài (rủi ro lộ). Cùng họ `cast_enabled`/`voice_wake_enabled`.
 */
private const val K_TG_TOKEN = "telegram_bot_token"
private const val K_TG_CHAT = "telegram_chat_id"
private const val K_TG_ENABLED = "telegram_enabled"
private const val K_TG_CONTROL = "telegram_control_enabled"
private const val K_TG_OFFSET = "telegram_offset"        // con trỏ getUpdates (bền, theo XE) — P2-10
private const val K_TG_PENDING_MUSIC = "telegram_pending_music"  // link nhạc chờ (một-lần) — R8/P1-9
private const val K_TG_PINNED = "telegram_pinned_places"  // tên 2 nơi ghim hàng nhanh (QD-04), ngăn bằng '\n'
private const val K_TG_NONCE = "telegram_board_nonce"    // nonce bảng SỐNG (bền — bảng cũ sau tắt/mở xe không hồi sinh), P1-2
private const val K_TG_BOARD_MSG = "telegram_board_msg"  // message_id bảng điều khiển sống (một-bảng-sống QD-17)

fun Prefs.telegramConfig(ctx: Context): TelegramConfig {
    val p = Prefs.sp(ctx)
    return TelegramConfig(
        botToken = p.getString(K_TG_TOKEN, "") ?: "",
        allowedChatId = p.getLong(K_TG_CHAT, 0L),
        enabled = p.getBoolean(K_TG_ENABLED, false),
        controlEnabled = p.getBoolean(K_TG_CONTROL, true),
    )
}

fun Prefs.setTelegramConfig(ctx: Context, cfg: TelegramConfig) {
    Prefs.sp(ctx).edit()
        .putString(K_TG_TOKEN, cfg.botToken)
        .putLong(K_TG_CHAT, cfg.allowedChatId)
        .putBoolean(K_TG_ENABLED, cfg.enabled)
        .putBoolean(K_TG_CONTROL, cfg.controlEnabled)
        .apply()
}

/** Con trỏ getUpdates (`offset`), bền qua lần BYD giết app — P2-10 (at-most-once). */
fun Prefs.telegramOffset(ctx: Context): Long = Prefs.sp(ctx).getLong(K_TG_OFFSET, 0L)
fun Prefs.setTelegramOffset(ctx: Context, offset: Long) {
    Prefs.sp(ctx).edit().putLong(K_TG_OFFSET, offset).apply()
}

/** Link nhạc chờ (R8): đặt khi xe chưa lên, mở MỘT LẦN lúc màn bật rồi xoá. `null` = không có. */
fun Prefs.pendingMusicUrl(ctx: Context): String? = Prefs.sp(ctx).getString(K_TG_PENDING_MUSIC, null)?.takeIf { it.isNotBlank() }
fun Prefs.setPendingMusicUrl(ctx: Context, url: String?) {
    val e = Prefs.sp(ctx).edit()
    if (url.isNullOrBlank()) e.remove(K_TG_PENDING_MUSIC) else e.putString(K_TG_PENDING_MUSIC, url)
    e.apply()
}

/** Tên 2 nơi ghim hàng nhanh (QD-04). Rỗng ⇒ `:app` lùi về 2 nơi đầu sổ. */
fun Prefs.telegramPinnedPlaces(ctx: Context): List<String> =
    Prefs.sp(ctx).getString(K_TG_PINNED, "")?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
fun Prefs.setTelegramPinnedPlaces(ctx: Context, names: List<String>) {
    Prefs.sp(ctx).edit().putString(K_TG_PINNED, names.take(2).joinToString("\n")).apply()
}

/** Nonce bảng SỐNG (P1-2): bền qua lần BYD giết app ⇒ nút của bảng cũ (nonce khác) không bao giờ hồi sinh. */
fun Prefs.telegramBoardNonce(ctx: Context): Int = Prefs.sp(ctx).getInt(K_TG_NONCE, 0)
fun Prefs.bumpTelegramBoardNonce(ctx: Context): Int {
    val next = telegramBoardNonce(ctx) + 1
    Prefs.sp(ctx).edit().putInt(K_TG_NONCE, next).apply()
    return next
}

/** message_id của bảng điều khiển sống (QD-17 một-bảng-sống); 0 = chưa có. */
fun Prefs.telegramBoardMsg(ctx: Context): Long = Prefs.sp(ctx).getLong(K_TG_BOARD_MSG, 0L)
fun Prefs.setTelegramBoardMsg(ctx: Context, id: Long) {
    Prefs.sp(ctx).edit().putLong(K_TG_BOARD_MSG, id).apply()
}
