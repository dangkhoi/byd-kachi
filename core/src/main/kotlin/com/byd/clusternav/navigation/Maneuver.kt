package com.byd.clusternav.navigation

/**
 * Maneuver TRUNG LẬP — quyết định hướng rẽ DUY NHẤT của một khung dẫn đường, độc lập với NGUỒN
 * (Google Maps / Waze / VietMap) và với ĐẦU RA (làn cụm / HUD).
 *
 * VÌ SAO tồn tại: kiến trúc "một đầu vào → nhiều đầu ra độc lập" chỉ chống lệch nếu đầu vào mang một
 * QUYẾT ĐỊNH đã chốt, không phải dữ liệu thô để mỗi đầu ra tự diễn giải lại. Trước đây khung mang mã
 * AMAP NEW_ICON (ngôn ngữ RIÊNG của làn cụm) nên làn cụm đọc thẳng đúng, còn HUD phải suy lại từ chữ →
 * mọi cua rớt về "đi thẳng". Giờ khung mang [Maneuver]; mỗi đầu ra là một ENCODER thuần:
 *   - [toAmapIcon] cho làn cụm (AmapService remap CAN qua TurnIdMapToCAN).
 *   - [toHudIcon] cho HUD (ghi thẳng mã CAN INSTRUMENT_GUIDE_INFO_SIMPLE_SET).
 * Hai đầu ra là HÀM của cùng một Maneuver → KHÔNG thể lệch hướng rẽ; không đầu ra nào "quyết định lại".
 *
 * Từ 2026-08-14 (Track B — RE docs/diagnostics/re-maneuver-icon-tables-2026-08-14.md) enum được LÀM GIÀU
 * thêm quyết định phân biệt (MERGE · RAMP · FORK · KEEP trái/phải · UTURN_RIGHT · ROUNDABOUT_EXIT ·
 * TUNNEL · SERVICE_AREA · TOLL · WAYPOINT) để MANG/LOG đúng ý đồ GMaps kể cả khi glyph vẽ ra phải fallback.
 * Hai nhóm:
 *   - "reuse-mã" (encode-only): MERGE→9(thẳng); RAMP/FORK/KEEP →4/5(chếch). KHÔNG có trong
 *     [fromAmapIcon] để 9/4/5 vẫn NGHỊCH về STRAIGHT/SLIGHT_LEFT/SLIGHT_RIGHT — khứ hồi CHÍNH XÁC, làn
 *     cụm bảo toàn nguyên trạng.
 *   - "mã DUY NHẤT" (10/12/13/14/16/19): có trong [fromAmapIcon] → làn cụm khứ hồi được các glyph mới.
 *
 * Từ 2026-08-16 (TASK 1 closeout 1.28) enum thêm HỌ VÒNG XUYẾN CÓ HƯỚNG (encode-only): ROUNDABOUT_LEFT ·
 * ROUNDABOUT_RIGHT · ROUNDABOUT_STRAIGHT · ROUNDABOUT_UTURN + 4 biến thể _CW. GMaps large-icon phân biệt
 * hướng ra vòng xuyến; trước đây AMAP-int (fromAmapIcon) gộp hết về generic. Cụm-strip KHÔNG có glyph vòng
 * xuyến có hướng nên [toAmapIcon] của cả 8 = 11 (generic); HƯỚNG chỉ hiện trên HUD/centre qua [toHudIcon].
 *
 * Bất biến `toHudIcon(m) == TurnIdMapToCAN[toAmapIcon(m)]` giữ cho MỌI maneuver, TRỪ HỌ VÒNG XUYẾN — tức
 * ROUNDABOUT (generic) + 8 thành viên có hướng ở trên. Nhóm này HUD ghi mã CAN TRỰC TIẾP (generic 20;
 * có hướng 15/16/17/18/19/20/21/22 per OpenBYD `w40.a` + `HudController` TURN_ICON_ROUNDABOUT_*,
 * cross-validate on-car 2026-08-16) chứ KHÔNG qua remap cụm `TurnIdMapToCAN[11]=13`, vì cụm-strip không có
 * glyph vòng xuyến có hướng ([toAmapIcon] các thành viên này = 11 generic, KHÔNG vào [fromAmapIcon] để AMAP
 * 11 vẫn nghịch về ROUNDABOUT generic cho làn cụm). Đây là cùng kiểu carve-out mà ROUNDABOUT generic đã dùng.
 */
enum class Maneuver {
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    UTURN,
    ROUNDABOUT,
    CONTINUE,
    DESTINATION,
    // ── Track B enrichment (2026-08-14): mã lấy TỪ bảng THẬT AMAP NEW_ICON 0..28 + CAN 1..49 (RE §1/§2).
    MERGE,            // nhập làn — 0..28 KHÔNG có glyph merge → đi thẳng (sửa bug owner "merge → rẽ phải")
    RAMP_LEFT,        // on/off-ramp trái = tách làn NHẸ ≈ chếch trái (không phải cua 90°)
    RAMP_RIGHT,       // on/off-ramp phải ≈ chếch phải
    FORK_LEFT,        // ngã ba chữ Y giữ trái ≈ chếch trái (không có glyph fork riêng)
    FORK_RIGHT,       // ngã ba chữ Y giữ phải ≈ chếch phải
    KEEP_LEFT,        // giữ làn trái ≈ chếch trái
    KEEP_RIGHT,       // giữ làn phải ≈ chếch phải
    UTURN_RIGHT,      // quay đầu PHẢI (LHT/hiếm) — NEW_ICON 19 (trước UNUSED) → CAN 10
    ROUNDABOUT_EXIT,  // ra khỏi vòng xuyến — NEW_ICON 12 (trước UNUSED) → CAN 24
    TUNNEL,           // sắp vào hầm — NEW_ICON 16 → CAN 49
    SERVICE_AREA,     // tới trạm dừng nghỉ — NEW_ICON 13 → CAN 46
    TOLL,             // tới trạm thu phí — NEW_ICON 14 → CAN 47
    WAYPOINT,         // tới điểm dừng trung gian — NEW_ICON 10 → CAN 45

    // ── Vòng xuyến CÓ HƯỚNG (encode-only, 2026-08-16 — TASK 1 closeout 1.28). GMaps large-icon phân biệt
    //   hướng ra + chiều (CCW mặc định VN / CW cho LHT). AMAP-int (fromAmapIcon) TRƯỚC ĐÂY gộp hết về generic.
    //   toAmapIcon = 11 (cụm-strip không có glyph vòng xuyến có hướng); HƯỚNG chỉ ra trên HUD/centre qua
    //   toHudIcon = mã CAN GHI-THẲNG (OpenBYD w40.a + HudController TURN_ICON_ROUNDABOUT_*, on-car 2026-08-16).
    //   KHÔNG có trong fromAmapIcon → AMAP 11 vẫn nghịch về ROUNDABOUT generic cho làn cụm.
    ROUNDABOUT_LEFT,        // CCW rẽ trái  — CAN 15 (TURN_ICON_ROUNDABOUT_3_4_LEFT)
    ROUNDABOUT_RIGHT,       // CCW rẽ phải  — CAN 18 (TURN_ICON_ROUNDABOUT_1_4_RIGHT)
    ROUNDABOUT_STRAIGHT,    // CCW đi thẳng — CAN 20 (TURN_ICON_ROUNDABOUT_STRAIGHT_R)
    ROUNDABOUT_UTURN,       // CCW quay đầu — CAN 22 (TURN_ICON_ROUNDABOUT_R_TO_L)
    ROUNDABOUT_LEFT_CW,     // CW  rẽ trái  — CAN 16 (TURN_ICON_ROUNDABOUT_1_4_LEFT)
    ROUNDABOUT_RIGHT_CW,    // CW  rẽ phải  — CAN 17 (TURN_ICON_ROUNDABOUT_3_4_RIGHT)
    ROUNDABOUT_STRAIGHT_CW, // CW  đi thẳng — CAN 19 (TURN_ICON_ROUNDABOUT_STRAIGHT_L)
    ROUNDABOUT_UTURN_CW;    // CW  quay đầu — CAN 21 (TURN_ICON_ROUNDABOUT_L_TO_R)

    /** -> mã AMAP NEW_ICON cho làn cụm (0..28; AmapService tự remap CAN, KHÔNG remap ở đây). */
    fun toAmapIcon(): Int = when (this) {
        TURN_LEFT -> 2
        TURN_RIGHT -> 3
        SLIGHT_LEFT -> 4
        SLIGHT_RIGHT -> 5
        SHARP_LEFT -> 6
        SHARP_RIGHT -> 7
        UTURN -> 8
        STRAIGHT -> 9
        ROUNDABOUT -> 11
        DESTINATION -> 15
        CONTINUE -> 20
        // Track B "reuse-mã" (encode-only — KHÔNG vào fromAmapIcon: 9/4/5 phải nghịch về STRAIGHT/SLIGHT_*).
        MERGE -> 9
        RAMP_LEFT -> 4
        RAMP_RIGHT -> 5
        FORK_LEFT -> 4
        FORK_RIGHT -> 5
        KEEP_LEFT -> 4
        KEEP_RIGHT -> 5
        // Track B "mã DUY NHẤT" (có vào fromAmapIcon để làn cụm khứ hồi).
        UTURN_RIGHT -> 19
        ROUNDABOUT_EXIT -> 12
        TUNNEL -> 16
        SERVICE_AREA -> 13
        TOLL -> 14
        WAYPOINT -> 10
        // Vòng xuyến CÓ HƯỚNG: cụm-strip generic (không có glyph hướng) → 11. Encode-only, KHÔNG vào
        //   fromAmapIcon (AMAP 11 vẫn nghịch về ROUNDABOUT generic — làn cụm bảo toàn nguyên trạng).
        ROUNDABOUT_LEFT, ROUNDABOUT_RIGHT, ROUNDABOUT_STRAIGHT, ROUNDABOUT_UTURN,
        ROUNDABOUT_LEFT_CW, ROUNDABOUT_RIGHT_CW, ROUNDABOUT_STRAIGHT_CW, ROUNDABOUT_UTURN_CW -> 11
    }

    /** -> mã icon CAN HUD (INSTRUMENT_GUIDE_INFO_SIMPLE_SET, enum HudController 1..49). */
    fun toHudIcon(): Int = when (this) {
        TURN_LEFT -> 1
        TURN_RIGHT -> 2
        SLIGHT_LEFT -> 3
        SLIGHT_RIGHT -> 5
        SHARP_LEFT -> 7
        SHARP_RIGHT -> 8
        UTURN -> 9          // AMAP gộp T/P → mặc định trái (9), chuẩn RHT (VN)
        STRAIGHT -> 11
        // Vòng xuyến trên HUD: 15 (≤1.22) và 13 (1.23) ĐỀU vẽ MŨI TÊN BẺ MÉO (owner báo + ảnh 2026-08-16), KHÔNG ra
        //   glyph vòng xuyến. OpenBYD 2.3 (field-proven, defpackage/w40.a: maneuver_roundabout_enter_ccw) dùng CAN 20.
        //   ⇒ bảng CAN GHI-THẲNG-HUD khác TurnIdMapToCAN của cụm ở RIÊNG vòng xuyến (13 là remap cụm; 20 là mã HUD thật).
        //   Cụm (toAmapIcon=11) không đổi. Số nhánh vẫn 24+N (25..34, cũng khớp OpenBYD).
        ROUNDABOUT -> 20    // glyph vòng xuyến (OpenBYD proven; 13/15 đều méo trên HUD)
        // Parity làn cụm: CAN 12 = 顺行 continue (= TurnIdMapToCAN[20]); trước 11 (đi thẳng) lệch với làn cụm.
        CONTINUE -> 12      // 顺行 continue/follow (khớp làn cụm)
        DESTINATION -> 48
        // Track B — bất biến toHudIcon == TurnIdMapToCAN[toAmapIcon]:
        MERGE -> 11         // TurnIdMapToCAN[9]
        RAMP_LEFT -> 3      // TurnIdMapToCAN[4]
        RAMP_RIGHT -> 5     // TurnIdMapToCAN[5]
        FORK_LEFT -> 3
        FORK_RIGHT -> 5
        KEEP_LEFT -> 3
        KEEP_RIGHT -> 5
        UTURN_RIGHT -> 10       // TurnIdMapToCAN[19]
        ROUNDABOUT_EXIT -> 24   // TurnIdMapToCAN[12]
        TUNNEL -> 49            // TurnIdMapToCAN[16]
        SERVICE_AREA -> 46      // TurnIdMapToCAN[13]
        TOLL -> 47              // TurnIdMapToCAN[14]
        WAYPOINT -> 45          // TurnIdMapToCAN[10]
        // Vòng xuyến CÓ HƯỚNG — CAN GHI-THẲNG HUD (carve-out: KHÁC remap cụm TurnIdMapToCAN[11]=13; xem KDoc
        //   class). Nguồn: OpenBYD w40.a (name→CAN) + HudController TURN_ICON_ROUNDABOUT_* (cross-validate on-car
        //   2026-08-16): 15=RAB trái CCW, 18=RAB phải CCW, 20=generic/thẳng CCW, 22=u-turn CCW; CW=16/17/19/21.
        ROUNDABOUT_LEFT -> 15
        ROUNDABOUT_RIGHT -> 18
        ROUNDABOUT_STRAIGHT -> 20
        ROUNDABOUT_UTURN -> 22
        ROUNDABOUT_LEFT_CW -> 16
        ROUNDABOUT_RIGHT_CW -> 17
        ROUNDABOUT_STRAIGHT_CW -> 19
        ROUNDABOUT_UTURN_CW -> 21
    }

    companion object {
        /**
         * Cầu nối AMAP NEW_ICON -> [Maneuver] cho các classifier/nguồn còn phát mã AMAP
         * (ManeuverSignature/ArrowClassifier/IconResource/maneuverVerbIcon). null nếu là 0/none, -1
         * hoặc mã ngoài từ vựng (caller coi như "chưa có maneuver"). Khứ hồi:
         * `fromAmapIcon(x).toAmapIcon() == x` với mọi x pipeline phát ra {2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,19,20}.
         * (10/12/13/14/16/19 thêm ở Track B 2026-08-14 — glyph途经点/驶出环岛/服务区/收费站/隧道/右转掉头.)
         */
        fun fromAmapIcon(amap: Int): Maneuver? = when (amap) {
            2 -> TURN_LEFT
            3 -> TURN_RIGHT
            4 -> SLIGHT_LEFT
            5 -> SLIGHT_RIGHT
            6 -> SHARP_LEFT
            7 -> SHARP_RIGHT
            8 -> UTURN
            9 -> STRAIGHT
            10 -> WAYPOINT          // Track B: NEW_ICON 10 (到达途经点) → CAN 45
            11 -> ROUNDABOUT
            12 -> ROUNDABOUT_EXIT   // Track B: NEW_ICON 12 (驶出环岛) → CAN 24
            13 -> SERVICE_AREA      // Track B: NEW_ICON 13 (到达服务区) → CAN 46
            14 -> TOLL              // Track B: NEW_ICON 14 (到达收费站) → CAN 47
            15 -> DESTINATION
            16 -> TUNNEL            // Track B: NEW_ICON 16 (进入隧道) → CAN 49
            19 -> UTURN_RIGHT       // Track B: NEW_ICON 19 (右转掉头) → CAN 10
            20 -> CONTINUE
            else -> null
        }
    }
}
