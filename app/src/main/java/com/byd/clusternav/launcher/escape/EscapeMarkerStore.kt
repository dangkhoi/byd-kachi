package com.byd.clusternav.launcher.escape

import android.annotation.SuppressLint
import android.content.Context
import com.byd.clusternav.system.FreeformSeedStore

/**
 * Nơi ghi bền dấu 2.98 · R7 ([EscapeMarkers]) — tệp THEO XE `clusternav_state` (cùng chỗ `kachi_behind_marks`,
 * `kachi_floating_opened`): task freeform + chế độ Android nhớ theo app nằm trên CHIẾC XE này, không đi theo hồ sơ.
 * `commit()` đồng bộ vì dấu phải nằm trên đĩa TRƯỚC mã 89 (CLAUDE.md §5). Gọi trên luồng nền của `SlotEscape`.
 */
internal class EscapeMarkerStore(context: Context) : SlotEscapeRun.MarkerStore {

    private val prefs = context.applicationContext.getSharedPreferences(FreeformSeedStore.PREF, Context.MODE_PRIVATE)

    override fun read(): List<EscapeMarker> = EscapeMarkers.decode(prefs.getString(KEY, null))

    @SuppressLint("ApplySharedPref")   // commit() đồng bộ là cố ý — xem KDoc lớp
    override fun write(list: List<EscapeMarker>): Boolean =
        if (list.isEmpty()) prefs.edit().remove(KEY).commit() else prefs.edit().putString(KEY, EscapeMarkers.encode(list)).commit()

    companion object {
        /** Khoá trên đĩa — đổi tên là mất dấu của máy đang chạy. */
        const val KEY = "kachi_slot_escape"
    }
}
