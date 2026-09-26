package com.byd.clusternav.modules.hal

import com.byd.clusternav.modules.hal.BydHal.CAMERA_DISPLAY_STATE_ID
import com.byd.clusternav.modules.hal.BydHal.CROSSING_DIST_OVERSEA_ID
import com.byd.clusternav.modules.hal.BydHal.EASY_NAVI_GUIDE_OVERSEA_ID
import com.byd.clusternav.modules.hal.BydHal.GUIDE_INFO_CAMERA_ID
import com.byd.clusternav.modules.hal.BydHal.LANE_1_GUIDANCE_ARROW_ID
import com.byd.clusternav.modules.hal.BydHal.LANE_1_RECOMMENDED_ID
import com.byd.clusternav.modules.hal.BydHal.LANE_ID_STRIDE
import com.byd.clusternav.modules.hal.BydHal.MAX_CLUSTER_LANES
import com.byd.clusternav.modules.hal.BydHal.NAVI_CAM_REMAINING_MILEAGE_ID
import com.byd.clusternav.modules.hal.BydHal.NAV_DISTANCE_BLANK
import com.byd.clusternav.modules.hal.BydHal.PATHNAME_OVERSEA_ID
import com.byd.clusternav.modules.hal.BydHal.cachedSetBytes
import com.byd.clusternav.modules.hal.BydHal.cachedSetInt
import com.byd.clusternav.modules.hal.BydHal.featureId
import com.byd.clusternav.navigation.LaneInfo
import com.byd.clusternav.navigation.NavOutputDecision

/**
 * ═══ VAI "đẩy CONTENT cụm/HUD" của [BydHal] — B3 T4 (spec b3-full-nav-capture §R3/§R4/§R5) ═════════════════════
 *
 * Tách khỏi `BydHal.kt` (666 dòng → trần 500, CLAUDE.md §4.1) theo VAI. [BydHal] giữ nguyên MẶT TIỀN
 * (`BydHal.pushNavigation` · `blankNavDistance` · `pushLane` · `pushCamera` uỷ quyền một dòng xuống đây); các
 * hằng id (`LANE_1_*` · `GUIDE_INFO_CAMERA_ID` · `NAV_DISTANCE_BLANK` …) VẪN ở [BydHal] vì là bề mặt công khai.
 *
 * ⚠ REFLECTION HAL — bốn hàm chép NGUYÊN VĂN, không sửa một tên feature (`INSTRUMENT_*`) hay raw-id nào. Mọi write
 * vẫn đi qua [BydHal.cachedSetInt]/[BydHal.cachedSetBytes] (skip id đã bị HAL từ chối) — hai hàm đó mở `internal`
 * đúng cho việc này. CONTENT-only: KHÔNG chạm session latch (SEND_NAVI_STATUS / SET_NAVI_SCREEN_STATUS / 3 SDK) —
 * latch vẫn là ĐỘC QUYỀN của `writeNavFrame` ở [BydHal] ([com.byd.clusternav.NavigationHudOwner],
 * `PhysicalHudOwnershipTest`). KDoc gốc của khối này (mức bằng chứng OQ3/OQ13) ở lại trên các hằng trong [BydHal].
 */
object BydHalContentPush {
    /** THÊM (B3 T4): đẩy CONTENT mũi tên + cự-ly + tên-đường vào cụm-centre + HUD (domestic 0x43F + oversea 0x1F7),
     *  KHÔNG chạm session latch/SDK (đó là NavigationHudOwner). [segMeters] < 0 → bỏ ghi cự-ly (không xoá trắng số
     *  đang hiện); [road] null/blank → bỏ ghi tên. Degrade-safe qua cachedSetInt/cachedSetBytes. */
    fun pushNavigation(instr: Any, icon: Int, segMeters: Int = -1, road: String? = null): String {
        val rc = StringBuilder()
        fun w(name: String, v: Int) { featureId(name)?.let { id -> rc.append(" $name=").append(cachedSetInt(instr, id, v)) } }
        w("INSTRUMENT_GUIDE_INFO_SIMPLE_SET", icon)
        w("INSTRUMENT_GUIDE_INFO_AND_ROAD_AHEAD_DISTANCE_SET", icon)   // OpenBYD dualIcon (0x43F01030)
        (featureId("INSTRUMENT_EASY_NAVI_GUIDE_INFOR_SET") ?: EASY_NAVI_GUIDE_OVERSEA_ID).let { id ->
            rc.append(" GUIDE_OVERSEA=").append(cachedSetInt(instr, id, icon))
        }
        if (segMeters >= 0) {
            w("INSTRUMENT_FRONT_CROSSING_DISTANCE_SET", segMeters)
            (featureId("INSTRUMENT_DISTANCE_TARGET_HEAD_SET") ?: CROSSING_DIST_OVERSEA_ID).let { id ->
                rc.append(" DIST_OVERSEA=").append(cachedSetInt(instr, id, segMeters))
            }
        }
        if (!road.isNullOrBlank()) {
            val bytes = road.toByteArray(Charsets.UTF_16LE)
            featureId("INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET")?.let { id -> rc.append(" PATHNAME=").append(cachedSetBytes(instr, id, bytes)) }
            (featureId("INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_OVERASEA_SET") ?: PATHNAME_OVERSEA_ID).let { id ->
                rc.append(" PATHNAME_OVERSEA=").append(cachedSetBytes(instr, id, bytes))
            }
        }
        return rc.toString().trim()
    }

    /**
     * THÊM (B-III, 2026-08-22): **XOÁ TRẮNG ô cự-ly** trên cụm/HUD, giữ nguyên mọi thứ khác.
     *
     * VÌ SAO cần một hàm riêng thay vì "cứ ghi -1 qua [pushNavigation]": [pushNavigation] coi `segMeters < 0` là
     * "BỎ GHI, giữ số cũ" (nhánh `if (segMeters >= 0)` ngay trên). Nên khi [com.byd.clusternav.NavOutputOwner]
     * mất tin vào cự-ly (guard [com.byd.clusternav.navigation.TurnDistancePlausibility] chưa warmup xong / vừa
     * đổi nguồn), nếu chỉ truyền -1 thì **số của nguồn CŨ nằm lại trên cụm** — tệ hơn hiện trạng.
     *
     * ⚠ MỨC BẰNG CHỨNG (CLAUDE.md §2) — ĐỌC KỸ, ĐỪNG THĂNG HẠNG:
     *  • **ĐÃ CHỨNG MINH**: `clearNavFrame` (ở trên, cùng file) ghi -1 vào ĐÚNG feature id này và chạy tốt
     *    trên xe hôm nay.
     *  • **CHƯA BIẾT**: `clearNavFrame` ghi -1 **kèm `INSTRUMENT_SEND_NAVI_STATUS_SET = 4` trong cùng lời
     *    gọi**, tức cụm ẨN HẲN widget nav nên chưa bao giờ phải *render* số -1. Hàm này cố ý KHÔNG chạm latch,
     *    nên đây là lần đầu -1 được ghi vào ô cự-ly **trong khi widget đang hiện**. Firmware coi <0 là "ẩn ô"
     *    hay render raw (`-1` / `0xFFFFFFFF`) thì **chưa probe on-car** — OQ13 trong spec.
     *  • Vì vậy KHÔNG được nói "xấu nhất là no-op ⇒ không bao giờ tệ hơn hiện trạng" (câu đó đã bị gỡ khỏi
     *    KDoc này 08-22 vòng 1): nếu cụm render raw thì tài xế thấy một con số BỊA, tệ hơn hẳn "giữ số cũ".
     *    Phải chạy probe OQ13 TRƯỚC khi ship đường này ra xe thật.
     *
     * CONTENT-only: KHÔNG chạm session latch (SEND_NAVI_STATUS / SET_NAVI_SCREEN_STATUS / SDK — độc quyền của
     * [com.byd.clusternav.NavigationHudOwner]) và KHÔNG chạm icon (mũi tên vẫn phải hiện: hướng còn đáng tin,
     * chỉ cự-ly là không).
     */
    fun blankNavDistance(instr: Any): String {
        val rc = StringBuilder()
        featureId("INSTRUMENT_FRONT_CROSSING_DISTANCE_SET")?.let { id ->
            rc.append(" FRONT_CROSSING=").append(cachedSetInt(instr, id, NAV_DISTANCE_BLANK))
        }
        (featureId("INSTRUMENT_DISTANCE_TARGET_HEAD_SET") ?: CROSSING_DIST_OVERSEA_ID).let { id ->
            rc.append(" DIST_OVERSEA=").append(cachedSetInt(instr, id, NAV_DISTANCE_BLANK))
        }
        return rc.toString().trim()
    }

    /** THÊM (B3 T4): đẩy dải làn vào register cụm — mỗi làn: LANE_n_GUIDANCE_ARROW_SET (mã hướng qua
     *  [NavOutputDecision.laneArrowCode]) + IS_LANE_n_RECOMMENDED_SET (1 sáng / 0 mờ, R4). Tối đa
     *  [MAX_CLUSTER_LANES]. Degrade-safe; làn rỗng → no-op. */
    fun pushLane(instr: Any, info: LaneInfo): String {
        if (info.isEmpty()) return ""
        val rc = StringBuilder()
        val n = minOf(info.count, MAX_CLUSTER_LANES)
        for (i in 0 until n) {
            val lane = info.lanes[i]
            val laneNo = i + 1
            val arrowId = featureId("INSTRUMENT_LANE_${laneNo}_GUIDANCE_ARROW_SET") ?: (LANE_1_GUIDANCE_ARROW_ID + i * LANE_ID_STRIDE)
            val recId = featureId("IS_LANE_${laneNo}_RECOMMENDED_SET") ?: (LANE_1_RECOMMENDED_ID + i * LANE_ID_STRIDE)
            rc.append(" L$laneNo=").append(cachedSetInt(instr, arrowId, NavOutputDecision.laneArrowCode(lane.arrows)))
            rc.append(" L${laneNo}R=").append(cachedSetInt(instr, recId, if (lane.recommended) 1 else 0))
        }
        return rc.toString().trim()
    }

    /** THÊM (B3 T4): đẩy icon camera + cự-ly (R5a) — INSTRUMENT_GUIDE_INFO_CAMERA_SET (0x43F03010) + display-state
     *  + remaining-mileage. [iconCode] 0 = tắt icon; [distanceMeters] < 0 → bỏ ghi cự-ly. Degrade-safe. */
    fun pushCamera(instr: Any, iconCode: Int, distanceMeters: Int = -1): String {
        val rc = StringBuilder()
        (featureId("INSTRUMENT_GUIDE_INFO_CAMERA_SET") ?: GUIDE_INFO_CAMERA_ID).let { id ->
            rc.append(" CAM=").append(cachedSetInt(instr, id, iconCode))
        }
        (featureId("INSTRUMENT_CAMERA_DISPLAY_STATE_SET") ?: CAMERA_DISPLAY_STATE_ID).let { id ->
            rc.append(" CAM_STATE=").append(cachedSetInt(instr, id, if (iconCode != 0) 1 else 0))
        }
        if (distanceMeters >= 0) (featureId("INSTRUMENT_NAVI_CAM_REMAINING_MILEAGE_SET") ?: NAVI_CAM_REMAINING_MILEAGE_ID).let { id ->
            rc.append(" CAM_DIST=").append(cachedSetInt(instr, id, distanceMeters))
        }
        return rc.toString().trim()
    }
}
