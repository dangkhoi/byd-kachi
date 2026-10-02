package com.byd.clusternav.launcher.trip

import android.annotation.SuppressLint
import android.content.Context
import com.byd.clusternav.system.FreeformSeedStore

/**
 * Nơi ghi bền của chuyến lên xe — tệp THEO XE `clusternav_state` (cùng chỗ `kachi_behind_marks`, `kachi_shell_approval`),
 * ba khoá [TripGate.KEY_LEDGER] · [TripGate.KEY_LAST] · [TripGate.KEY_BOOT_SEEN] (khai `ProfileScope.DEVICE_KEYS` +
 * `SettingsCatalog.NOT_SETTINGS` qua [TripGate.DEVICE_KEYS]: không đi theo hồ sơ, không lên UI như một lựa chọn).
 *
 * `commit()` ĐỒNG BỘ là cố ý (CLAUDE.md §5): sổ CLAIMED phải nằm trên đĩa TRƯỚC lệnh đầu của chuyến — BYD có thể giết
 * Kachi ngay sau đó (tắt máy), hoặc lượt chữa phím force-stop Kachi trong ân hạn khởi động; `apply()` mất sổ đúng ca cần.
 */
internal class TripLedgerStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FreeformSeedStore.PREF, Context.MODE_PRIVATE)

    fun ledger(): TripGate.Ledger? = TripGate.decode(prefs.getString(TripGate.KEY_LEDGER, null))

    /** Ghi sổ (CLAIMED trước việc / FIRED khi xong). `false` = đĩa từ chối ⇒ bên gọi KHÔNG làm gì (fail-safe). */
    @SuppressLint("ApplySharedPref")   // commit() đồng bộ là cố ý — xem KDoc lớp
    fun write(l: TripGate.Ledger): Boolean = prefs.edit().putString(TripGate.KEY_LEDGER, TripGate.encode(l)).commit()

    /** Đóng chuyến: sổ FIRED + kết quả một lượt ghi. */
    @SuppressLint("ApplySharedPref")
    fun close(l: TripGate.Ledger, r: TripGate.Result): Boolean = prefs.edit()
        .putString(TripGate.KEY_LEDGER, TripGate.encode(l))
        .putString(TripGate.KEY_LAST, TripGate.encodeResult(r))
        .commit()

    fun last(): TripGate.Result? = TripGate.decodeResult(prefs.getString(TripGate.KEY_LAST, null))

    /** Nhánh BOOT_COMPLETED của `RebindReceiver` ghi khoá lần khởi động hiện tại (R2.3a). */
    @SuppressLint("ApplySharedPref")
    fun markBootSeen(bootKey: String): Boolean = prefs.edit().putString(TripGate.KEY_BOOT_SEEN, bootKey).commit()

    fun bootSeen(): String? = prefs.getString(TripGate.KEY_BOOT_SEEN, null)
}
