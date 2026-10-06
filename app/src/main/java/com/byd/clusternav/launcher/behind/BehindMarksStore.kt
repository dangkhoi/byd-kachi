package com.byd.clusternav.launcher.behind

import android.annotation.SuppressLint
import android.content.Context
import com.byd.clusternav.launcher.trip.TripStart
import com.byd.clusternav.system.FreeformSeedStore

/**
 * Nơi ghi bền [BehindMarks] — tệp theo XE `clusternav_state` (cùng chỗ `kachi_shell_approval`, `kachi_floating_opened`),
 * khoá [KEY]. Khai ở `ProfileScope.DEVICE_KEYS` + `SettingsCatalog.NOT_SETTINGS`: không đi theo hồ sơ, không lên UI.
 *
 * `commit()` ĐỒNG BỘ là cố ý (CLAUDE.md §5): dấu phải nằm trên đĩa TRƯỚC lệnh `move-task`, vì lần BYD giết Kachi có thể
 * tới ngay sau lệnh — `apply()` mất dấu đúng ca cần nó.
 *
 * 2.93 · BEHIND-MARKS-BOOT (spec `docs/specs/kachi-293-slot.html` R5): ghi KÈM khoá lần khởi động máy ([boot] =
 * `TripStart.bootKey` — cùng khoá của sổ chuyến lên xe, không phép đọc thứ hai) và chỉ ĐỌC dấu của đúng lần khởi động này
 * ([BehindMarks.forBoot]) ⇒ mọi bên đọc (lượt trả lại `BehindHomeRecovery`, K8 về ô `SlotReturnRun`, các chuỗi ghi dấu) không
 * bao giờ thấy dấu của đời máy trước. Dấu cũ không bị xoá ở lượt đọc (đọc không có tác dụng phụ) — lượt ghi kế đè lên.
 */
internal class BehindMarksStore(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(FreeformSeedStore.PREF, Context.MODE_PRIVATE)

    /**
     * Khoá lần khởi động hiện tại — đọc MỘT lần mỗi kho (kho sống ngắn, một lượt việc). `BOOT_COUNT` không đọc được ⇒
     * `TripStart.bootKey` tự lùi về khoá giờ tường (`w…`) mà [BehindMarks.forBoot] không dùng để bỏ dấu (= như bản ≤ 2.92).
     */
    private val boot: String by lazy { TripStart.bootKey(app) }

    fun read(): Map<Int, String> = BehindMarks.forBoot(prefs.getString(KEY, null), boot)

    fun add(taskId: Int, pkg: String): Boolean = write(BehindMarks.add(read(), taskId, pkg))

    fun remove(taskId: Int) {
        val cur = read()
        if (taskId in cur) write(cur - taskId)
    }

    @SuppressLint("ApplySharedPref")   // commit() đồng bộ là cố ý — xem KDoc lớp
    fun write(marks: Map<Int, String>): Boolean =
        if (marks.isEmpty()) prefs.edit().remove(KEY).commit() else prefs.edit().putString(KEY, BehindMarks.encode(marks, boot)).commit()

    companion object {
        /** Khoá trên đĩa — đổi tên là mất dấu của máy đang chạy. */
        const val KEY = "kachi_behind_marks"
    }
}
