package com.byd.clusternav.launcher.escape

/**
 * ═══ 2.98 · R7 (SLOT-ESCAPE-POLICY) — mã binder `activity_task` theo đời ROM (thuần, `:core`) ══════════════════════════
 *
 * App Kachi đặt vào ô mà tự rơi ra display 0 (Waze `launchToSide` — `waze-into-slot-research-2026-09-14.md`) được đưa về đúng
 * khung ô bằng FREEFORM. Hai việc không có lệnh `am` công khai trên A10 nên đi `service call activity_task <mã>`:
 *  - [getTaskBounds] — CHỈ ĐỌC (`IActivityTaskManager.getTaskBounds(int)`), dùng làm phép thử bảng mã trước mọi lệnh ghi.
 *  - [setTaskWindowingMode] — `setTaskWindowingMode(int taskId, int mode, boolean toTop)`.
 *
 * [ĐO xe Seal DL3 fw 2606, 09/10] mã 59 trả đúng khung, mã 89 mode 5 + `am task resize` đưa Waze vào khung ô cùng pid
 * (`docs/diagnostics/oncar-freeform-waze-2026-10-09.md` §2 mục 4–5). [ĐO máy ảo A10 kachi_play 09/10] cùng hai mã; mã 89
 * mode 1 toTop 0 trả toàn màn mà KHÔNG đưa lên trên (sau Home ⇒ app nằm dưới Kachi, cùng pid). Mã sinh từ thứ tự AIDL ⇒ chỉ
 * đúng cho ĐÚNG đời API [sdk]; đời khác (DL5 = Android 12) ⇒ hồ sơ không mang bảng ⇒ R7 tắt (CLAUDE.md §7 — khác biệt đời
 * xe nằm ở `ClusterProfile`, không rải trong mã).
 */
data class TaskBinderCodes(val sdk: Int, val getTaskBounds: Int, val setTaskWindowingMode: Int) {

    /** Bảng chỉ dùng được trên đúng đời API đã đo — sai đời thì mã 59 có thể là một hàm GHI khác. */
    fun usableOn(sdkInt: Int): Boolean = sdkInt == sdk

    /** Đọc khung task (chỉ đọc). */
    fun boundsCmd(task: Int): String = "service call activity_task $getTaskBounds i32 $task"

    /** Đổi chế độ cửa sổ của task; [toTop] = đưa lên trên cùng display của nó. */
    fun modeCmd(task: Int, mode: Int, toTop: Boolean): String =
        "service call activity_task $setTaskWindowingMode i32 $task i32 $mode i32 ${if (toTop) 1 else 0}"

    companion object {
        /** Android 10 `android-10.0.0_r47` — [ĐO xe 09/10 + máy ảo 09/10]. */
        val ANDROID_10_R47 = TaskBinderCodes(sdk = 29, getTaskBounds = 59, setTaskWindowingMode = 89)

        /** `WindowConfiguration.WINDOWING_MODE_FULLSCREEN` — trả về toàn màn, xoá chế độ freeform Android nhớ theo app. */
        const val MODE_FULLSCREEN = 1

        /** `WindowConfiguration.WINDOWING_MODE_FREEFORM`. */
        const val MODE_FREEFORM = 5

        /**
         * Các từ 32 bit của một `Result: Parcel(...)` theo thứ tự. Mỗi dòng chỉ lấy phần TRƯỚC dấu `'` (phần ASCII in kèm có thể
         * chứa chữ giống hex); bỏ địa chỉ `0x...:`. [ĐO máy ảo 09/10] hai dạng: một dòng `Result: Parcel(00000000    '....')` và
         * nhiều dòng `0x00000000: 00000000 00000001 00000013 00000059 '…'`.
         */
        fun words(out: String): List<Int> {
            val at = out.indexOf("Parcel(")
            if (at < 0) return emptyList()
            val res = ArrayList<Int>()
            for (raw in out.substring(at + "Parcel(".length).lineSequence()) {
                val line = raw.substringBefore('\'')
                for (tok in line.split(' ', '\t')) {
                    val t = tok.trim()
                    if (t.length == 8 && t.all { it in '0'..'9' || it in 'a'..'f' }) res += t.toLong(16).toInt()
                }
            }
            return res
        }

        /** Lệnh `void` trả về không có ngoại lệ (từ đầu = 0). Đọc hỏng / có ngoại lệ ⇒ `false`. */
        fun parseOk(out: String): Boolean = words(out).firstOrNull() == 0

        /**
         * Khung trả về từ [boundsCmd]: `0 (không ngoại lệ) · 1 (khác null) · trái · trên · phải · dưới`. Khung rỗng = task không tồn tại
         * ([ĐO máy ảo] task 99999 ⇒ `0 0 0 0`) ⇒ `null`, như mọi dạng đọc hỏng.
         */
        fun parseRect(out: String): PxRect? {
            val w = words(out)
            if (w.size < 6 || w[0] != 0 || w[1] != 1) return null
            return PxRect(w[2], w[3], w[4], w[5]).takeUnless { it.isEmpty }
        }
    }
}

/** Hình chữ nhật pixel màn hình (trái, trên, phải, dưới) — thuần. */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /** Đối số cho `am task resize <task> L T R B`. */
    fun args(): String = "$left $top $right $bottom"

    override fun toString(): String = "[$left,$top][$right,$bottom]"

    companion object {
        private val RE = Regex("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")

        /** Khung đầu tiên dạng `[l,t][r,b]` trong [s]; không có ⇒ `null`. */
        fun parse(s: String): PxRect? = RE.find(s)?.let { m ->
            val v = m.groupValues.drop(1).map { it.toInt() }
            PxRect(v[0], v[1], v[2], v[3])
        }
    }
}
