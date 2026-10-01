package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import com.byd.clusternav.system.FreeformSeedStore

/**
 * Kho bền của [FloatingWindowLedger] (PROFILE-SWITCH-SLOTS R-B2/R-B4) — SharedPreferences theo XE.
 *
 * Dùng LẠI tệp `clusternav_state` của [FreeformSeedStore] (tệp dấu theo xe sẵn có, không nằm trong ảnh chụp hồ sơ nào —
 * `SettingsCatalog.PREFS_FILES`) thay vì mở tệp prefs thứ ba. Khoá riêng [KEY], không chạm `freeform_state`.
 *
 * `commit()` đồng bộ là CỐ Ý (CLAUDE.md §5): dấu phải nằm trên đĩa TRƯỚC lệnh mở cửa sổ, chết giữa chừng không mất dấu.
 * Chỉ gọi trên luồng nền (`LauncherWindows.placeApp`/`sweepFloating` chạy trong `winExec`).
 */
class FloatingLedgerStore(context: Context) : FloatingWindowLedger.Store {

    private val prefs = context.applicationContext.getSharedPreferences(FreeformSeedStore.PREF, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    // lint ApplySharedPref: `commit()` đồng bộ là cố ý — xem KDoc lớp.
    @SuppressLint("ApplySharedPref")
    override fun write(value: String): Boolean = prefs.edit().putString(KEY, value).commit()

    companion object {
        /** Khoá trên đĩa — đổi tên là mất dấu của máy đang chạy. Khai ở `SettingsCatalog.NOT_SETTINGS` + `ProfileScope` DEVICE. */
        const val KEY = "kachi_floating_opened"
    }
}
