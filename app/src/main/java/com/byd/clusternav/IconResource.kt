package com.byd.clusternav

import android.content.Context
import android.graphics.drawable.Icon
import android.util.Log

/**
 * Lấy HƯỚNG RẼ từ TÊN resource của small-icon notification (cách DashCast — chuẩn, độc lập ngôn ngữ,
 * KHÔNG xử lý ảnh). GMaps đặt small-icon = mũi tên có tên "arrow_right"/"slight_left"... đổi theo cua.
 * Đọc getResourceEntryName(smallIcon.resId) -> map sang mã AMAP NEW_ICON (0..28). -1 nếu không ra.
 */
object IconResource {
    private const val TAG = "IconResource"
    @Volatile private var exempted = false

    // DEBUG cho việc thử nhiều bản GMaps: tên small-icon + mã suy ra gần nhất (hiện trên MainActivity).
    @Volatile var lastName: String = "-"; private set
    @Volatile var lastAmap: Int = -1; private set

    // Tên resource (substring) -> mã AMAP NEW_ICON. ĐẶC THÙ trước, generic (right/left) CUỐI.
    private val NAME_TO_AMAP = listOf(
        "roundabout" to 11,   // Q1(mirror): TRƯỚC slight/sharp/fork/u_turn — "roundabout_slight_left" phải ra 11, không phải 4
        "slight_right" to 5, "slight_left" to 4,
        "sharp_right" to 7, "sharp_left" to 6,
        "u_turn_right" to 8, "u_turn_left" to 8, "uturn_right" to 8, "uturn_left" to 8,
        "u_turn" to 8, "uturn" to 8,
        "merge_right" to 9, "merge_left" to 9,   // Track B: merge = ĐI THẲNG (0..28 không có glyph merge; sửa bug owner "merge → rẽ phải"). Trước: 5/4.
        "ramp_right" to 5, "ramp_left" to 4,      // ramp = tách làn nhẹ ≈ chếch (đã đúng — Track B giữ)
        "fork_right" to 5, "fork_left" to 4,
        "exit_right" to 3, "exit_left" to 2,
        "destination" to 15, "arrive" to 15, "finish" to 15, "flag" to 15,
        "tunnel" to 16,   // Track B: sắp vào hầm (NEW_ICON 16 → CAN 49). On-car verify GMaps expose "tunnel" resource name qua NavArrowLog small_amap/sig_name.
        "depart" to 9, "start" to 9,   // bước đầu = đi thẳng ra đường (KHÔNG map 1 = glyph "ghim + xe")
        "straight" to 9, "continue" to 20, "merge" to 9,   // Track B: generic merge → 9 (đi thẳng). Trước: 5.
        "arrow_right" to 3, "arrow_left" to 2,
        "turn_right" to 3, "turn_left" to 2,
        "_right" to 3, "_left" to 2,        // generic cuối cùng
    )

    /** -> mã AMAP NEW_ICON từ small-icon, hoặc -1. Không xử lý ảnh, chỉ đọc tên resource. */
    fun resolve(ctx: Context, pkg: String, icon: Icon?): Int {
        if (icon == null) return -1
        exempt()
        val (name, amap) = runCatching {
            val resId = Icon::class.java.getMethod("getResId").invoke(icon) as Int
            if (resId == 0) return@runCatching "(no resId)" to -1
            val nm = ctx.createPackageContext(pkg, 0).resources.getResourceEntryName(resId).lowercase()
            nm to (NAME_TO_AMAP.firstOrNull { nm.contains(it.first) }?.second ?: -1)
        }.getOrElse { "(err ${it.javaClass.simpleName})" to -1 }
        lastName = name; lastAmap = amap
        Log.i(TAG, "smallIcon '$name' -> amap=$amap")
        return amap
    }

    /** Mở hidden-API để gọi Icon.getResId() (greylist trên Android 10). Gọi 1 lần. */
    private fun exempt() {
        if (exempted) return
        runCatching {
            val vm = Class.forName("dalvik.system.VMRuntime")
            val rt = vm.getMethod("getRuntime").invoke(null)
            vm.getMethod("setHiddenApiExemptions", Array<String>::class.java).invoke(rt, arrayOf("L") as Any)
        }
        exempted = true
    }
}
