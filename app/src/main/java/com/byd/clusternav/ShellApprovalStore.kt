package com.byd.clusternav

import android.annotation.SuppressLint
import android.content.Context
import com.byd.clusternav.carexec.ShellApprovalLedger
import com.byd.clusternav.system.FreeformSeedStore

/**
 * ═══ READY-AT-HOME §4.4.1 — KHO BỀN của dấu "xe này đã duyệt khoá adb này" ═══════════════════════════════════════
 *
 * Tệp prefs THEO XE `clusternav_state` (dùng lại tệp của [FreeformSeedStore] — như `FloatingLedgerStore`; không thuộc
 * ảnh chụp hồ sơ nào, `SettingsCatalog.PREFS_FILES`), khoá riêng [KEY] (khai `SettingsCatalog.NOT_SETTINGS` +
 * `ProfileScope` DEVICE ⇒ không vào xuất/nhập hồ sơ). `allowBackup=false` sẵn ⇒ cài lại thì mất cả khoá adb lẫn dấu,
 * hai thứ vẫn khớp nhau.
 *
 * Chỉ HAI đường ghi: [markUp] (sau một phép đo "bắt tay xong") và [forget] (sau một phép đo "adbd hỏi lại / từ chối").
 * Cả hai chỉ được gọi từ `ShellReadiness` — bài canh `ReadyAtHomeWiringContractTest` khoá điều đó. `commit()` đồng bộ
 * CỐ Ý (CLAUDE.md §5): tiến trình có thể bị BYD giết ngay sau khi kênh lên.
 */
internal class ShellApprovalStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FreeformSeedStore.PREF, Context.MODE_PRIVATE)

    fun read(): ShellApprovalLedger = ShellApprovalLedger.decode(prefs.getString(KEY, null))

    /** Ghi dấu "đã duyệt" cho vân tay [fp] lúc [nowWallMs], hạn [windowMs]. `false` = ghi hỏng. */
    @SuppressLint("ApplySharedPref")   // commit() đồng bộ là cố ý — xem KDoc lớp
    fun markUp(fp: String, nowWallMs: Long, windowMs: Long): Boolean {
        val v = ShellApprovalLedger.encode(ShellApprovalLedger.Approved(fp, nowWallMs, windowMs)) ?: return false
        return prefs.edit().putString(KEY, v).commit()
    }

    /** Xoá dấu: có dấu ⇒ bia mộ (MẤT DUYỆT); chưa từng có ⇒ để nguyên (CHƯA TỪNG). `false` = ghi hỏng. */
    @SuppressLint("ApplySharedPref")   // commit() đồng bộ là cố ý — xem KDoc lớp
    fun forget(nowWallMs: Long): Boolean {
        val cur = read()
        val v = ShellApprovalLedger.encode(ShellApprovalLedger.forgotten(cur, nowWallMs)) ?: return true
        return prefs.edit().putString(KEY, v).commit()
    }

    companion object {
        /** Khoá trên đĩa — đổi tên là mất dấu của máy đang chạy. Khai ở `SettingsCatalog.NOT_SETTINGS` + `ProfileScope`. */
        const val KEY = "kachi_shell_approval"
    }
}
