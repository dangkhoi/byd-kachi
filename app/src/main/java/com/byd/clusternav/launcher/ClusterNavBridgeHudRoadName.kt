package com.byd.clusternav.launcher

import android.content.Context
import com.byd.clusternav.modules.hal.HudRoadNameTransport
import com.byd.clusternav.navigation.ClusterDebugRoadName.Availability
import com.byd.clusternav.navigation.HudRoadNameMode

/**
 * 3.1.12 · HUD-ROADNAME-ALT-PATH — *"Đường gửi tên đường lên HUD"* (`hud_road_name_mode`, theo XE, mặc định A).
 * Cầu trả dữ liệu, không trả câu (hợp đồng [ClusterNavBridge]); câu ghép ở `SettingsHudRoadNameRows`.
 */
fun ClusterNavBridge.hudRoadNameMode(): HudRoadNameMode = HudRoadNameTransport.mode(app)

/** `null` = đã lưu; khác `null` = lý do xe này không dùng được B/C (không lưu). */
fun ClusterNavBridge.setHudRoadNameMode(mode: HudRoadNameMode): Availability? =
    HudRoadNameTransport.setMode(app, mode, source = "settings")

/** Sự thật PackageManager đọc MỚI — trang Cài đặt gọi lúc dựng/hiện lại. */
fun ClusterNavBridge.hudRoadNameAvailability(): Availability = HudRoadNameTransport.availability(app, fresh = true)

fun ClusterNavBridge.hudRoadNameStatus(): HudRoadNameTransport.Status = HudRoadNameTransport.status(app)

/**
 * Mặt tiền cho bên KHÔNG cầm [ClusterNavBridge] (cầu kiểm thử `hudroad` — chạy cả khi màn chính chưa lên). Cùng MỘT đường
 * ([HudRoadNameTransport.setMode]) với hàng Cài đặt; trả dữ liệu thô ASCII cho lời đáp JSON (không tên đường).
 */
object HudRoadNameControl {
    fun setMode(ctx: Context, mode: HudRoadNameMode): Availability? = HudRoadNameTransport.setMode(ctx, mode, source = "testbridge")

    fun statusFields(ctx: Context): List<Pair<String, Any?>> {
        val s = HudRoadNameTransport.status(ctx)
        val plan = HudRoadNameTransport.plan(ctx)
        return listOf(
            "mode" to s.mode.code,
            "availability" to HudRoadNameTransport.availability(ctx, fresh = true).name,
            "plan_hal_bytes" to plan.halMaxBytes,
            "plan_cluster_debug" to plan.clusterDebug,
            "plan_degraded" to plan.degraded,
            "last_mode" to s.lastMode?.code,
            "last_units" to s.lastUnits,
            "last_rc" to s.lastRc,
            "last_at_ms" to s.lastAtMs,
        )
    }
}
