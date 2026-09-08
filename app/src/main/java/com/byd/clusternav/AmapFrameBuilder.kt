package com.byd.clusternav

import android.util.Log
import com.byd.clusternav.navigation.ManeuverSignature
import com.byd.clusternav.navigation.ArrowClassifier
import com.byd.clusternav.navigation.NavFormat
import com.byd.clusternav.navigation.NavParse
import android.content.Intent

/**
 * ⚠️⚠️ WIRE FORMAT — LIVE-CONFIRMED trên BYD Seal DL3 (ảnh chụp cụm 2026-06-23). ⚠️⚠️
 * Builder THUẦN (không state, không Handler): NavState -> Intent AUTONAVI gửi cho AmapService.
 * MỌI putExtra (key/value/kiểu) PHẢI giữ y nguyên — đổi 1 cái là cụm tắt mà KHÔNG báo lỗi compile.
 *
 * Recipe (đã RE từ jadx-amap2/AmapService.java):
 *  - guidance: KEY_TYPE=10001, TYPE=1, IS_BYD_MAP=false -> AmapService flip mIsGAODENaving=true, ghi CAN cụm.
 *  - state/stop: KEY_TYPE=10019, EXTRA_STATE=9. Khử cờ kẹt: gửi IS_BYD_MAP=true rồi =false.
 *  - DashCast TYPE=8 KHÔNG ăn trên unit này; phải TYPE=1.
 *  - NEW_ICON = index AMAP 0..28 (AmapService tự remap CAN qua TurnIdMapToCAN — KHÔNG tự remap ở đây).
 */
object AmapFrameBuilder {

    // Gắn logger Android vào seam của thuật toán trong :core. Thuật toán không biết Android; app quyết
    // định ghi log ở đâu.
    // D4 (closeout 1.28): note() fires per classify (per frame on the cluster-lane worker + NavArrowLog). Gate it
    // behind the runtime verbose flag (default OFF) so it stops spamming logcat ~4×/s; owner flips it on to tune.
    init {
        ManeuverSignature.note = { if (NavLog.verbose) Log.i("ManeuverSig", it) }
    }

    const val ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND"
    const val KEY_TYPE_GUIDE = 10001
    const val KEY_TYPE_STATE = 10019
    const val TYPE_ACTIVE = 1
    const val STATE_STOP = 9

    /** Frame guidance (10001). null nếu distance không đọc được. roadOverride: tên đường (marquee).
     *  segOverride>=0: dùng cự ly ĐÃ NỘI SUY (dead-reckoning theo tốc độ) thay cho parse thô từ noti. */
    fun buildGuidanceFrame(s: NavState, byd: Boolean, roadOverride: String? = null, segOverride: Int = -1, hasDistance: Boolean = true): Intent? {
        val seg = if (segOverride >= 0) segOverride else NavParse.parseMeters(s.distance)
        if (hasDistance && seg < 0) return null   // có cự ly nhưng parse hỏng -> bỏ; chỉ-hướng (hasDistance=false) thì vẫn dựng
        val (routeDis, routeTime) = NavParse.parseEta(s.eta)
        // icon rẽ, ưu tiên: (1) tên small-icon (chỉ bản GMaps cũ có) -> (2) CHỮ KÝ TRI GIÁC large-icon
        //   (cách reference Open BYD/DashCast, mạnh nhất cho GMaps này) -> (3) động từ chữ
        //   -> (4) trọng-tâm large-icon (COM cũ, dự phòng) -> (5) thẳng.
        val exit = NavFormat.roundaboutExit(s.maneuverText.ifBlank { s.road })
        var icon = if (exit in 1..10) 11    // vòng xuyến CÓ số nhánh -> ÉP icon 11 để AmapService remap glyph nhánh-ra (11+exit)
            else s.maneuverIcon.takeIf { it in 0..28 }
                ?: ManeuverSignature.classify(s.arrow?.asPixelFrame())
                ?: NavFormat.maneuverVerbIcon(s.maneuverText.ifBlank { s.road })
                ?: ArrowClassifier.classify(s.arrow?.asPixelFrame())
                ?: 9
        // GUARD "hình ghim + xe" lúc bắt đầu đi: nếu icon = 15 (điểm đến) SUY TỪ classifier (không phải cờ
        // đích tường minh maneuverIcon=15 do NavNotificationListener cắm khi ĐÃ ĐẾN) mà vẫn còn cự ly phía trước
        // -> đây là glyph start/pin bị đọc nhầm -> ép ĐI THẲNG (9). Cờ đích thật vẫn qua vì s.maneuverIcon==15.
        if (icon == 15 && s.maneuverIcon != 15 && hasDistance && seg > 50) icon = 9
        val road = roadOverride ?: NavFormat.fitRoadName(s.road)   // marquee window hoặc bản rút gọn
        return Intent(ACTION).apply {
            putExtra("KEY_TYPE", KEY_TYPE_GUIDE)
            putExtra("TYPE", TYPE_ACTIVE)
            putExtra("EXTRA_STATE", 1)
            putExtra("EXTRA_IS_FOREGROUND", 0)
            putExtra("IS_BYD_MAP", byd)
            putExtra("IS_BYD_BAIDU_MAP", false)
            putExtra("NEW_ICON", icon)
            if (exit in 1..10) putExtra("ROUNG_ABOUT_NUM", exit)   // vòng xuyến: chọn glyph nhánh-ra theo số
            // CHỈ gắn cự ly khi GMaps THẬT SỰ gửi m/km. Chỉ-hướng (hasDistance=false) → gửi -1 để firmware XOÁ số
            // (firmware giữ giá trị cuối nếu mình bỏ trường → kẹt "0m"; gửi -1 thì nó render trống).
            if (hasDistance && seg > 0) {
                putExtra("SEG_REMAIN_DIS", seg)
                putExtra("SEG_REMAIN_DIS_AUTO", NavParse.formatMeters(seg))
            } else {
                putExtra("SEG_REMAIN_DIS", -1)
                putExtra("SEG_REMAIN_DIS_AUTO", "")
            }
            putExtra("NEXT_ROAD_NAME", road)
            putExtra("ROUTE_REMAIN_DIS", routeDis)            // tổng KM còn lại -> cụm MILEAGE (int, OK)
            putExtra("ROUTE_REMAIN_TIME", routeTime)          // -1 nếu thiếu
            if (routeDis >= 0) putExtra("ROUTE_REMAIN_DIS_AUTO", NavParse.formatMeters(routeDis))
            if (routeTime >= 0) {
                // ⚠ thời gian còn lại: firmware parseTime CHỈ đọc tiếng Trung (时/分) -> phải format CN.
                putExtra("ROUTE_REMAIN_TIME_AUTO", NavParse.formatRemainTimeCn(routeTime))
                putExtra("ROUTE_REMAIN_TIME_STRING", NavParse.formatSeconds(routeTime))   // (1for2/cosmetic)
            }
            // Giờ TỚI (ETA đồng hồ) -> cụm HOUR/MINUTE. Cần dạng "预计今天HH:MM到达"; lấy HH:MM từ ETA noti.
            NavParse.extractArrivalClock(s.eta)?.let { putExtra("ETA_TEXT", NavParse.formatEtaCn(it)) }
        }
    }

    /** Frame state/stop (10019 + EXTRA_STATE). Dùng cho reset cờ kẹt (true rồi false) + dừng. */
    fun buildStateFrame(keyType: Int, state: Int, byd: Boolean): Intent =
        Intent(ACTION).apply {
            putExtra("KEY_TYPE", keyType)
            putExtra("EXTRA_STATE", state)
            putExtra("IS_BYD_MAP", byd)
        }
}
