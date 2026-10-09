package com.byd.clusternav.launcher.escape

/**
 * ═══ Mã binder `activity_task` theo đời ROM — CHỈ còn cho việc dọn dấu R7 ([LegacyFreeformUndo]) ═══════════════════════════
 *
 * 2.98 · R7 (đã SUPERSEDED bởi R18) đưa app thoát ô sang FREEFORM bằng `setTaskWindowingMode` (mã 89 trên A10 r47 —
 * [ĐO xe Seal DL3 fw 2606 09/10 + máy ảo A10]; `docs/diagnostics/oncar-freeform-waze-2026-10-09.md` §2 mục 4–5). Máy đã chạy bản R7
 * có thể còn task freeform + dấu `kachi_slot_escape`; đường TRẢ duy nhất đã đo là mã 89 mode 1 toTop 0 (toàn màn, KHÔNG đưa lên trên,
 * cùng pid). Mã sinh từ thứ tự AIDL ⇒ chỉ đúng cho ĐÚNG đời API [sdk]; DL5 (Android 12) ⇒ hồ sơ không mang bảng (CLAUDE.md §7).
 */
data class TaskBinderCodes(val sdk: Int, val setTaskWindowingMode: Int) {

    /** Bảng chỉ dùng được trên đúng đời API đã đo — sai đời thì mã có thể là một hàm GHI khác. */
    fun usableOn(sdkInt: Int): Boolean = sdkInt == sdk

    /** Đổi chế độ cửa sổ của task; [toTop] = đưa lên trên cùng display của nó. */
    fun modeCmd(task: Int, mode: Int, toTop: Boolean): String =
        "service call activity_task $setTaskWindowingMode i32 $task i32 $mode i32 ${if (toTop) 1 else 0}"

    companion object {
        /** Android 10 `android-10.0.0_r47` — [ĐO xe 09/10 + máy ảo 09/10]. */
        val ANDROID_10_R47 = TaskBinderCodes(sdk = 29, setTaskWindowingMode = 89)

        /** `WindowConfiguration.WINDOWING_MODE_FULLSCREEN`. */
        const val MODE_FULLSCREEN = 1

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
    }
}
