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
private const val K_TG_OFFSET = "telegram_offset"        // con trỏ getUpdates (bền, theo XE) — P2-10
private const val K_TG_PENDING_MUSIC = "telegram_pending_music"  // link nhạc chờ (một-lần) — R8/P1-9
private const val K_TG_PENDING_MUSIC_AT = "telegram_pending_music_at"  // mốc đặt nhạc chờ (hạn 24 h)
private const val K_TG_NONCE = "telegram_board_nonce"    // nonce bảng nơi đã lưu (bền — bảng cũ không hồi sinh)
private const val K_TG_KB_VERSION = "telegram_kb_version"  // phiên bản bàn phím nút đã gửi (TelegramReplyKeyboard.VERSION)

fun Prefs.telegramConfig(ctx: Context): TelegramConfig {
    val p = Prefs.sp(ctx)
    return TelegramConfig(
        botToken = p.getString(K_TG_TOKEN, "") ?: "",
        allowedChatId = p.getLong(K_TG_CHAT, 0L),
        enabled = p.getBoolean(K_TG_ENABLED, false),
    )
}

fun Prefs.setTelegramConfig(ctx: Context, cfg: TelegramConfig) {
    Prefs.sp(ctx).edit()
        .putString(K_TG_TOKEN, cfg.botToken)
        .putLong(K_TG_CHAT, cfg.allowedChatId)
        .putBoolean(K_TG_ENABLED, cfg.enabled)
        .apply()
}

/** Con trỏ getUpdates (`offset`), bền qua lần BYD giết app — P2-10 (at-most-once). */
fun Prefs.telegramOffset(ctx: Context): Long = Prefs.sp(ctx).getLong(K_TG_OFFSET, 0L)
fun Prefs.setTelegramOffset(ctx: Context, offset: Long) {
    Prefs.sp(ctx).edit().putLong(K_TG_OFFSET, offset).apply()
}

/**
 * Nhạc chờ (R8): đặt khi xe chưa lên, mở MỘT LẦN lúc màn bật rồi xoá. `null` = không có, hoặc đã quá
 * [PENDING_MUSIC_TTL_MS] (review 26/10 P1: một `/play` gửi lúc 23h không được bật nhạc vài ngày sau — cùng hạn 24 h với
 * điểm đến chờ). Mốc đặt lưu cạnh; bản cũ không có mốc ⇒ coi như đã quá hạn.
 */
fun Prefs.pendingMusicUrl(ctx: Context, nowMs: Long = System.currentTimeMillis()): String? {
    val p = Prefs.sp(ctx)
    val v = p.getString(K_TG_PENDING_MUSIC, null)?.takeIf { it.isNotBlank() } ?: return null
    val at = p.getLong(K_TG_PENDING_MUSIC_AT, 0L)
    return v.takeIf { at > 0L && nowMs - at in 0..PENDING_MUSIC_TTL_MS }
}
fun Prefs.setPendingMusicUrl(ctx: Context, url: String?, nowMs: Long = System.currentTimeMillis()) {
    val e = Prefs.sp(ctx).edit()
    if (url.isNullOrBlank()) e.remove(K_TG_PENDING_MUSIC).remove(K_TG_PENDING_MUSIC_AT)
    else e.putString(K_TG_PENDING_MUSIC, url).putLong(K_TG_PENDING_MUSIC_AT, nowMs)
    e.apply()
}

/** Hạn của nhạc chờ — 24 h, bằng hạn điểm đến chờ. */
const val PENDING_MUSIC_TTL_MS: Long = 24L * 60 * 60 * 1000

/** Nonce bảng SỐNG (P1-2): bền qua lần BYD giết app ⇒ nút của bảng cũ (nonce khác) không bao giờ hồi sinh. */
fun Prefs.telegramBoardNonce(ctx: Context): Int = Prefs.sp(ctx).getInt(K_TG_NONCE, 0)
fun Prefs.bumpTelegramBoardNonce(ctx: Context): Int {
    val next = telegramBoardNonce(ctx) + 1
    Prefs.sp(ctx).edit().putInt(K_TG_NONCE, next).apply()
    return next
}

/** Phiên bản bàn phím nút dưới ô gõ đã gửi cho chat (0 = chưa gửi). Thấp hơn [TelegramReplyKeyboard.VERSION] ⇒ gửi lại. */
fun Prefs.telegramKbVersion(ctx: Context): Int = Prefs.sp(ctx).getInt(K_TG_KB_VERSION, 0)
fun Prefs.setTelegramKbVersion(ctx: Context, v: Int) { Prefs.sp(ctx).edit().putInt(K_TG_KB_VERSION, v).apply() }
