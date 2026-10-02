package com.byd.clusternav.launcher.behind

import android.annotation.SuppressLint
import android.content.Context
import com.byd.clusternav.system.FreeformSeedStore

/**
 * Nơi ghi bền [BehindMarks] — tệp theo XE `clusternav_state` (cùng chỗ `kachi_shell_approval`, `kachi_floating_opened`),
 * khoá [KEY]. Khai ở `ProfileScope.DEVICE_KEYS` + `SettingsCatalog.NOT_SETTINGS`: không đi theo hồ sơ, không lên UI.
 *
 * `commit()` ĐỒNG BỘ là cố ý (CLAUDE.md §5): dấu phải nằm trên đĩa TRƯỚC lệnh `move-task`, vì lần BYD giết Kachi có thể
 * tới ngay sau lệnh — `apply()` mất dấu đúng ca cần nó.
 */
internal class BehindMarksStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FreeformSeedStore.PREF, Context.MODE_PRIVATE)

    fun read(): Map<Int, String> = BehindMarks.decode(prefs.getString(KEY, null))

    fun add(taskId: Int, pkg: String): Boolean = write(BehindMarks.add(read(), taskId, pkg))

    fun remove(taskId: Int) {
        val cur = read()
        if (taskId in cur) write(cur - taskId)
    }

    @SuppressLint("ApplySharedPref")   // commit() đồng bộ là cố ý — xem KDoc lớp
    fun write(marks: Map<Int, String>): Boolean =
        if (marks.isEmpty()) prefs.edit().remove(KEY).commit() else prefs.edit().putString(KEY, BehindMarks.encode(marks)).commit()

    companion object {
        /** Khoá trên đĩa — đổi tên là mất dấu của máy đang chạy. */
        const val KEY = "kachi_behind_marks"
    }
}
