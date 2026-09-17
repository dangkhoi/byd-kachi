package com.byd.clusternav

import android.content.Context

/**
 * ═══ Khoá của daemon bơm chạm (inputd) — tách khỏi [Prefs] theo VAI (1.70, trần 500 dòng) ════════════════════
 *
 * Cùng cách [PrefsVoiceV3] tách nhóm giọng: hàm mở rộng của [Prefs], **cùng tệp `clusternav_prefs`** (mở tệp
 * thứ hai là dựng cửa thứ hai vào cùng chỗ lưu — thứ `SettingsCatalog.PREFS_FILES` sinh ra để bắt). Đọc MỘT lần
 * mỗi tiến trình ở `AppContainer` ⇒ đổi xong phải khởi động lại app.
 */

private fun inputdPrefs(ctx: Context) =
    ctx.applicationContext.getSharedPreferences("clusternav_prefs", Context.MODE_PRIVATE)

private const val K_INPUTD_DISABLED = "inputd_disabled"

/** Ép đường lùi theo cử chỉ (`input -d`), bỏ hẳn daemon — công tắc ẩn để chẩn đoán. */
fun Prefs.inputdDisabled(ctx: Context): Boolean = inputdPrefs(ctx).getBoolean(K_INPUTD_DISABLED, false)

private const val K_INPUTD_TOKEN = "inputd_token"
private const val INPUTD_TOKEN_BYTES = 16

/**
 * Token kênh TCP loopback tới daemon (1.70) — sinh MỘT lần mỗi cài đặt rồi giữ: daemon thường trú của lượt mở
 * app TRƯỚC (cùng cổng theo uid, xem `InputDaemonLaunch.portFor`) nhận đúng token này ⇒ được dùng lại thay vì
 * đẻ một daemon mồ côi mỗi lần mở app. Token đi qua dòng lệnh khởi động (uid shell đọc được) — nó chỉ gác cửa
 * loopback khỏi app KHÁC trên máy, không phải bí mật với chính shell.
 */
fun Prefs.inputdToken(ctx: Context): String {
    inputdPrefs(ctx).getString(K_INPUTD_TOKEN, null)
        ?.takeIf { com.byd.clusternav.system.inputd.InputDaemonLaunch.validToken(it) }
        ?.let { return it }
    val bytes = ByteArray(INPUTD_TOKEN_BYTES).also { java.security.SecureRandom().nextBytes(it) }
    val token = bytes.joinToString("") { "%02x".format(it) }
    inputdPrefs(ctx).edit().putString(K_INPUTD_TOKEN, token).apply()
    return token
}
