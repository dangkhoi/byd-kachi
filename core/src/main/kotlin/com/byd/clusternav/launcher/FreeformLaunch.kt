package com.byd.clusternav.launcher

/**
 * Bộ DỰNG LỆNH freeform (thuần JVM, test off-car). Tái dùng công thức ĐÃ PROVEN trên xe trong cluster-cast
 * ([com.byd.clusternav.modules.clustercast.simplified.AppMover] / CastGeometryController): mở app dạng cửa sổ
 * freeform (`am ... --windowingMode 5`) trên display chính rồi `am task resize` theo khung ô ([SlotRect]).
 *
 * :app [AppLauncher] adapter chạy các lệnh này qua dadb uid-shell (ON-CAR). KHÔNG import android.* → test JVM thuần.
 */
object FreeformLaunch {
    const val MAIN_DISPLAY = 0

    /** Mở [component] ("pkg/cls") dạng freeform trên [displayId]. */
    fun launchCmd(component: String, displayId: Int = MAIN_DISPLAY): String =
        "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER" +
            " --display $displayId --windowingMode 5 -n '$component'"

    /** Đặt khung task về đúng ô [slot]. `am task resize` nhận left top right bottom — KHÔNG phải w/h. */
    fun resizeCmd(taskId: Int, slot: SlotRect): String =
        "am task resize $taskId ${slot.left} ${slot.top} ${slot.right} ${slot.bottom}"

    /** Resolve launcher component của [pkg]. */
    fun resolveCmd(pkg: String): String =
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg"

    /** Đưa [component] về FULLSCREEN display chính (đóng ô) — công thức R6 cast (FLAG_ACTIVITY_SINGLE_TOP=0x20000000). */
    fun fullscreenCmd(component: String, displayId: Int = MAIN_DISPLAY): String =
        "am start --display $displayId --windowingMode 1 -f 0x20000000" +
            " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n '$component'"

    /** Cờ freeform (framework chỉ đọc lúc BOOT → có hiệu lực sau khi tắt-mở máy xe 1 lần). */
    val freeformFlagCmds: List<String> = listOf(
        "settings put global enable_freeform_support 1",
        "settings put global force_resizable_activities 1",
    )

    /** Lấy taskId của [pkg] từ output `am stack list` (khớp regex proven trong AppMover). */
    fun parseTaskId(stackList: String, pkg: String): Int? =
        Regex("taskId=(\\d+):[^\\n]*" + Regex.escape(pkg)).find(stackList)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Lấy taskId của [pkg] NHƯNG chỉ trong stack thuộc [displayId] — mirror công thức PROVEN
     * [com.byd.clusternav.modules.clustercast.simplified.AppMover] `findTaskIdOnDisplay` (fix on-car
     * 2026-08-04: regex global từng khớp NHẦM task cùng gói trên display khác ⇒ resize sai cửa sổ).
     * Duyệt từng dòng: gặp "Stack id=… displayId=N" thì nhớ N; chỉ khớp taskId của [pkg] khi N == [displayId].
     * Trả null nếu không thấy trên display đó → caller fallback [parseTaskId] cho định dạng stack khác.
     */
    fun parseTaskIdOnDisplay(stackList: String, pkg: String, displayId: Int): Int? {
        val stackHeader = Regex("Stack id=\\d+.*displayId=(\\d+)")
        val taskLine = Regex("taskId=(\\d+):[^\\n]*" + Regex.escape(pkg))
        var current = -1
        for (line in stackList.lineSequence()) {
            val sm = stackHeader.find(line)
            if (sm != null) { current = sm.groupValues[1].toIntOrNull() ?: -1; continue }
            if (current == displayId) {
                taskLine.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    /** Component từ output [resolveCmd]: dòng cuối có '/' và không có khoảng trắng. */
    fun parseComponent(resolveOutput: String): String? =
        resolveOutput.lineSequence().map { it.trim() }.lastOrNull { it.contains("/") && !it.contains(" ") }
}
