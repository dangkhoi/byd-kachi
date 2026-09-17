package com.byd.clusternav.launcher

/**
 * ═══ SINH BỞI `scripts/design/gen-car.py` từ `design/car/{top,front,side}.svg` — KHÔNG SỬA TAY ═══════════════════
 *
 * VISUAL-REFRESH P3 (spec `docs/specs/kachi-visual-refresh.html` §4.3 · R1 · AC1.2): đây là nửa "sinh, không gõ tay" của
 * [CarFrames]. Mỗi mảnh = một bộ phận có tên trong SVG nguồn; chuỗi path CHÍNH LÀ chuỗi `android:pathData` của các
 * `ic_car_*.xml` cùng lượt sinh (icon gộp nhiều mảnh thì pathData = các chuỗi nối bằng MỘT dấu cách, theo thứ tự).
 * Hộp bao tính bằng script trên hình học THẬT (làm phẳng cung/bezier) — không phải hộp bao điểm điều khiển như
 * `Path.computeBounds`, nên có thể hụt ≤ 0.3 so với số Android trả về cho thân xe (điểm điều khiển 7.2 vs mép 7.35).
 * Không có mã màu ở đây (luật 0-hex của `ThemePaletteContractTest`): `role` là TÊN vai, `CarPartStyle` đổi ra token.
 * Sửa: sửa SVG rồi `python3 scripts/design/gen-car.py`; `--check` so byte (đường ống §4.6).
 */
internal object CarFramesGenerated {

    /**
     * Một mảnh hình. [face] top|front|side · [layer] lớp §4.3 · [role] vai màu (paint · glass · lamp · drl · turn · tail ·
     * glow · glowtail · tyre · rim · flap · panel · mirror · shadow · plate · shade · glyph) · [path] hệ 24×24 ·
     * [bounds] `[trái, trên, phải, dưới]` · [evenOdd] tô even-odd (rèm) · [strokeOnly] vẽ bằng nét · [glyph] ký hiệu
     * chỉ dành cho icon (không thuộc khung xe) · [subpaths] số nhánh M đã gộp.
     */
    internal class Piece(
        val face: String,
        val id: String,
        val layer: String,
        val role: String,
        val path: String,
        val bounds: FloatArray,
        val evenOdd: Boolean,
        val strokeOnly: Boolean,
        val glyph: Boolean,
        val subpaths: Int,
    )

    /** Mọi mảnh của ba mặt, đúng THỨ TỰ VẼ trong SVG (lớp dưới trước). Lớp highlight dùng lại thân — xem [HIGHLIGHT_REF]. */
    val PIECES: List<Piece> = listOf(
        // ── top ──
        Piece("top", "glow_front", "glow", "glow", "M6.6,4.7 A5.4,1.8 0 1 1 17.4,4.7 A5.4,1.8 0 1 1 6.6,4.7 Z", floatArrayOf(6.6f, 2.9f, 17.4f, 6.5f), false, false, false, 1),
        Piece("top", "glow_rear", "glow", "glowtail", "M7.2,20.2 A4.8,1.5 0 1 1 16.8,20.2 A4.8,1.5 0 1 1 7.2,20.2 Z", floatArrayOf(7.2f, 18.7f, 16.8f, 21.7f), false, false, false, 1),
        Piece("top", "body", "body", "paint", "M12,4.6 C9.9,4.6 8.35,5.4 7.95,7.2 L7.5,10.2 C7.2,12.6 7.2,15.6 7.55,18.1 C7.8,19.7 9,20.3 12,20.3 C15,20.3 16.2,19.7 16.45,18.1 C16.8,15.6 16.8,12.6 16.5,10.2 L16.05,7.2 C15.65,5.4 14.1,4.6 12,4.6 Z", floatArrayOf(7.28f, 4.6f, 16.72f, 20.3f), false, false, false, 1),
        Piece("top", "windscreen", "glass", "glass", "M8.7,8.7 C9.9,7.9 14.1,7.9 15.3,8.7 L15.05,10.4 L8.95,10.4 Z", floatArrayOf(8.7f, 8.1f, 15.3f, 10.4f), false, false, false, 1),
        Piece("top", "glass_rear", "glass", "glass", "M8.95,15.2 L15.05,15.2 L15.3,16.6 C14.1,17.4 9.9,17.4 8.7,16.6 Z", floatArrayOf(8.7f, 15.2f, 15.3f, 17.2f), false, false, false, 1),
        Piece("top", "glass_lf", "glass", "glass", "M8.95,10.6 L9.35,10.6 A0.35,0.35 0 0 1 9.7,10.95 L9.7,12.05 A0.35,0.35 0 0 1 9.35,12.4 L8.95,12.4 A0.35,0.35 0 0 1 8.6,12.05 L8.6,10.95 A0.35,0.35 0 0 1 8.95,10.6 Z", floatArrayOf(8.6f, 10.6f, 9.7f, 12.4f), false, false, false, 1),
        Piece("top", "glass_rf", "glass", "glass", "M14.65,10.6 L15.05,10.6 A0.35,0.35 0 0 1 15.4,10.95 L15.4,12.05 A0.35,0.35 0 0 1 15.05,12.4 L14.65,12.4 A0.35,0.35 0 0 1 14.3,12.05 L14.3,10.95 A0.35,0.35 0 0 1 14.65,10.6 Z", floatArrayOf(14.3f, 10.6f, 15.4f, 12.4f), false, false, false, 1),
        Piece("top", "glass_lr", "glass", "glass", "M8.95,12.9 L9.35,12.9 A0.35,0.35 0 0 1 9.7,13.25 L9.7,14.45 A0.35,0.35 0 0 1 9.35,14.8 L8.95,14.8 A0.35,0.35 0 0 1 8.6,14.45 L8.6,13.25 A0.35,0.35 0 0 1 8.95,12.9 Z", floatArrayOf(8.6f, 12.9f, 9.7f, 14.8f), false, false, false, 1),
        Piece("top", "glass_rr", "glass", "glass", "M14.65,12.9 L15.05,12.9 A0.35,0.35 0 0 1 15.4,13.25 L15.4,14.45 A0.35,0.35 0 0 1 15.05,14.8 L14.65,14.8 A0.35,0.35 0 0 1 14.3,14.45 L14.3,13.25 A0.35,0.35 0 0 1 14.65,12.9 Z", floatArrayOf(14.3f, 12.9f, 15.4f, 14.8f), false, false, false, 1),
        Piece("top", "sunroof", "glass", "glass", "M11.1,10.9 L12.9,10.9 A0.8,0.8 0 0 1 13.7,11.7 L13.7,13.8 A0.8,0.8 0 0 1 12.9,14.6 L11.1,14.6 A0.8,0.8 0 0 1 10.3,13.8 L10.3,11.7 A0.8,0.8 0 0 1 11.1,10.9 Z", floatArrayOf(10.3f, 10.9f, 13.7f, 14.6f), false, false, false, 1),
        Piece("top", "sunshade", "glass", "shade", "M11.05,11.2 L12.95,11.2 A0.45,0.45 0 0 1 13.4,11.65 L13.4,11.65 A0.45,0.45 0 0 1 12.95,12.1 L11.05,12.1 A0.45,0.45 0 0 1 10.6,11.65 L10.6,11.65 A0.45,0.45 0 0 1 11.05,11.2 Z M11.2,12.4 L12.8,12.4 A0.4,0.4 0 0 1 13.2,12.8 L13.2,14 A0.4,0.4 0 0 1 12.8,14.4 L11.2,14.4 A0.4,0.4 0 0 1 10.8,14 L10.8,12.8 A0.4,0.4 0 0 1 11.2,12.4 Z M11.37,13 L12.63,13 A0.17,0.17 0 0 1 12.8,13.17 L12.8,13.18 A0.17,0.17 0 0 1 12.63,13.35 L11.37,13.35 A0.17,0.17 0 0 1 11.2,13.18 L11.2,13.17 A0.17,0.17 0 0 1 11.37,13 Z M11.37,13.7 L12.63,13.7 A0.17,0.17 0 0 1 12.8,13.87 L12.8,13.88 A0.17,0.17 0 0 1 12.63,14.05 L11.37,14.05 A0.17,0.17 0 0 1 11.2,13.88 L11.2,13.87 A0.17,0.17 0 0 1 11.37,13.7 Z", floatArrayOf(10.6f, 11.2f, 13.4f, 14.4f), true, false, false, 4),
        Piece("top", "headlamp", "lights", "lamp", "M8.25,6.45 L9.75,5 L10.15,5.75 L8.55,7.05 Z M15.75,6.45 L14.25,5 L13.85,5.75 L15.45,7.05 Z", floatArrayOf(8.25f, 5f, 15.75f, 7.05f), false, false, false, 2),
        Piece("top", "drl", "lights", "drl", "M8.6,7.35 L10.2,6.55 L10.35,6.9 L8.75,7.7 Z M15.4,7.35 L13.8,6.55 L13.65,6.9 L15.25,7.7 Z", floatArrayOf(8.6f, 6.55f, 15.4f, 7.7f), false, false, false, 2),
        Piece("top", "turn_l", "lights", "turn", "M7.55,7.9 L8.65,7.35 L8.65,8.45 Z", floatArrayOf(7.55f, 7.35f, 8.65f, 8.45f), false, false, false, 1),
        Piece("top", "turn_r", "lights", "turn", "M16.45,7.9 L15.35,7.35 L15.35,8.45 Z", floatArrayOf(15.35f, 7.35f, 16.45f, 8.45f), false, false, false, 1),
        Piece("top", "tail", "lights", "tail", "M7.85,18.25 L9.6,18.85 L9.45,19.7 L7.95,19.05 Z M16.15,18.25 L14.4,18.85 L14.55,19.7 L16.05,19.05 Z", floatArrayOf(7.85f, 18.25f, 16.15f, 19.7f), false, false, false, 2),
        Piece("top", "wheel_fl", "tyres", "tyre", "M4.3,6.8 L4.5,6.8 A1.2,1.2 0 0 1 5.7,8 L5.7,10.2 A1.2,1.2 0 0 1 4.5,11.4 L4.3,11.4 A1.2,1.2 0 0 1 3.1,10.2 L3.1,8 A1.2,1.2 0 0 1 4.3,6.8 Z", floatArrayOf(3.1f, 6.8f, 5.7f, 11.4f), false, false, false, 1),
        Piece("top", "wheel_fr", "tyres", "tyre", "M19.5,6.8 L19.7,6.8 A1.2,1.2 0 0 1 20.9,8 L20.9,10.2 A1.2,1.2 0 0 1 19.7,11.4 L19.5,11.4 A1.2,1.2 0 0 1 18.3,10.2 L18.3,8 A1.2,1.2 0 0 1 19.5,6.8 Z", floatArrayOf(18.3f, 6.8f, 20.9f, 11.4f), false, false, false, 1),
        Piece("top", "wheel_rl", "tyres", "tyre", "M4.3,13.6 L4.5,13.6 A1.2,1.2 0 0 1 5.7,14.8 L5.7,17 A1.2,1.2 0 0 1 4.5,18.2 L4.3,18.2 A1.2,1.2 0 0 1 3.1,17 L3.1,14.8 A1.2,1.2 0 0 1 4.3,13.6 Z", floatArrayOf(3.1f, 13.6f, 5.7f, 18.2f), false, false, false, 1),
        Piece("top", "wheel_rr", "tyres", "tyre", "M19.5,13.6 L19.7,13.6 A1.2,1.2 0 0 1 20.9,14.8 L20.9,17 A1.2,1.2 0 0 1 19.7,18.2 L19.5,18.2 A1.2,1.2 0 0 1 18.3,17 L18.3,14.8 A1.2,1.2 0 0 1 19.5,13.6 Z", floatArrayOf(18.3f, 13.6f, 20.9f, 18.2f), false, false, false, 1),
        Piece("top", "door_lf", "doors", "flap", "M7.55,9.1 L3.9,10.4 L3.9,13.1 L7.35,12.5 Z", floatArrayOf(3.9f, 9.1f, 7.55f, 13.1f), false, false, false, 1),
        Piece("top", "door_rf", "doors", "flap", "M16.45,9.1 L20.1,10.4 L20.1,13.1 L16.65,12.5 Z", floatArrayOf(16.45f, 9.1f, 20.1f, 13.1f), false, false, false, 1),
        Piece("top", "door_lr", "doors", "flap", "M7.35,13.9 L3.9,15.2 L3.9,17.9 L7.6,17.3 Z", floatArrayOf(3.9f, 13.9f, 7.6f, 17.9f), false, false, false, 1),
        Piece("top", "door_rr", "doors", "flap", "M16.65,13.9 L20.1,15.2 L20.1,17.9 L16.4,17.3 Z", floatArrayOf(16.4f, 13.9f, 20.1f, 17.9f), false, false, false, 1),
        Piece("top", "bonnet", "doors", "panel", "M10.4,5.6 L13.6,5.6 A0.9,0.9 0 0 1 14.5,6.5 L14.5,6.7 A0.9,0.9 0 0 1 13.6,7.6 L10.4,7.6 A0.9,0.9 0 0 1 9.5,6.7 L9.5,6.5 A0.9,0.9 0 0 1 10.4,5.6 Z", floatArrayOf(9.5f, 5.6f, 14.5f, 7.6f), false, false, false, 1),
        Piece("top", "boot", "doors", "panel", "M10,17.7 L14,17.7 A0.8,0.8 0 0 1 14.8,18.5 L14.8,18.8 A0.8,0.8 0 0 1 14,19.6 L10,19.6 A0.8,0.8 0 0 1 9.2,18.8 L9.2,18.5 A0.8,0.8 0 0 1 10,17.7 Z", floatArrayOf(9.2f, 17.7f, 14.8f, 19.6f), false, false, false, 1),
        Piece("top", "mirror", "doors", "mirror", "M7.6,8.9 L4.7,9.7 L7.4,10.9 Z M16.4,8.9 L19.3,9.7 L16.6,10.9 Z", floatArrayOf(4.7f, 8.9f, 19.3f, 10.9f), false, false, false, 2),
        Piece("top", "lock_shackle", "glyph", "glyph", "M10.7,11.9 L10.7,10.8 A1.3,1.3 0 0 1 13.3,10.8 L13.3,11.9", floatArrayOf(10.7f, 9.5f, 13.3f, 11.9f), false, true, true, 1),
        Piece("top", "lock_body", "glyph", "glyph", "M10.5,11.9 L13.5,11.9 A0.8,0.8 0 0 1 14.3,12.7 L14.3,14.6 A0.8,0.8 0 0 1 13.5,15.4 L10.5,15.4 A0.8,0.8 0 0 1 9.7,14.6 L9.7,12.7 A0.8,0.8 0 0 1 10.5,11.9 Z", floatArrayOf(9.7f, 11.9f, 14.3f, 15.4f), false, false, true, 1),
        Piece("top", "seat_fl", "glyph", "glyph", "M9.2,9.2 L10.6,9.2 A0.6,0.6 0 0 1 11.2,9.8 L11.2,10.8 A0.6,0.6 0 0 1 10.6,11.4 L9.2,11.4 A0.6,0.6 0 0 1 8.6,10.8 L8.6,9.8 A0.6,0.6 0 0 1 9.2,9.2 Z M9.25,11.7 L10.55,11.7 A0.45,0.45 0 0 1 11,12.15 L11,12.25 A0.45,0.45 0 0 1 10.55,12.7 L9.25,12.7 A0.45,0.45 0 0 1 8.8,12.25 L8.8,12.15 A0.45,0.45 0 0 1 9.25,11.7 Z", floatArrayOf(8.6f, 9.2f, 11.2f, 12.7f), false, false, true, 2),
        Piece("top", "therm_stem", "glyph", "glyph", "M12,9.6 L12,12.9", floatArrayOf(12f, 9.6f, 12f, 12.9f), false, true, true, 1),
        Piece("top", "therm_bulb", "glyph", "glyph", "M12,12.95 A1.35,1.35 0 1 1 12,15.65 A1.35,1.35 0 1 1 12,12.95 Z", floatArrayOf(10.65f, 12.95f, 13.35f, 15.65f), false, false, true, 1),
        Piece("top", "sunroof_gap", "glyph", "glyph", "M11.2,11.3 L12.8,11.3 A0.4,0.4 0 0 1 13.2,11.7 L13.2,11.9 A0.4,0.4 0 0 1 12.8,12.3 L11.2,12.3 A0.4,0.4 0 0 1 10.8,11.9 L10.8,11.7 A0.4,0.4 0 0 1 11.2,11.3 Z", floatArrayOf(10.8f, 11.3f, 13.2f, 12.3f), false, false, true, 1),
        Piece("top", "arrow_v_stem", "glyph", "glyph", "M12,16.6 L12,17.8", floatArrayOf(12f, 16.6f, 12f, 17.8f), false, true, true, 1),
        Piece("top", "arrow_v_heads", "glyph", "glyph", "M12,15.1 L13.34,16.59 L10.66,16.59 Z M12,19.3 L10.66,17.81 L13.34,17.81 Z", floatArrayOf(10.66f, 15.1f, 13.34f, 19.3f), false, false, true, 2),
        Piece("top", "chev_front", "glyph", "glyph", "M9.1,9.85 L11.5,10.7 L9.1,11.55 Z M14.9,9.85 L12.5,10.7 L14.9,11.55 Z", floatArrayOf(9.1f, 9.85f, 14.9f, 11.55f), false, false, true, 2),
        Piece("top", "chev_rear", "glyph", "glyph", "M9.1,16.45 L11.5,17.3 L9.1,18.15 Z M14.9,16.45 L12.5,17.3 L14.9,18.15 Z", floatArrayOf(9.1f, 16.45f, 14.9f, 18.15f), false, false, true, 2),
        Piece("top", "dots", "glyph", "glyph", "M9.8,13.5 A0.5,0.5 0 1 1 9.8,14.5 A0.5,0.5 0 1 1 9.8,13.5 Z M11.8,13.15 A0.85,0.85 0 1 1 11.8,14.85 A0.85,0.85 0 1 1 11.8,13.15 Z M13.95,12.75 A1.25,1.25 0 1 1 13.95,15.25 A1.25,1.25 0 1 1 13.95,12.75 Z", floatArrayOf(9.3f, 12.75f, 15.2f, 15.25f), false, false, true, 3),
        Piece("top", "drop", "glyph", "glyph", "M12,11.5 C13.12,12.47 13.55,13.5 13.55,14.35 A1.55,1.55 0 0 1 10.45,14.35 C10.45,13.5 10.88,12.47 12,11.5 Z", floatArrayOf(10.45f, 11.5f, 13.55f, 15.9f), false, false, true, 1),
        Piece("top", "note_head", "glyph", "glyph", "M11.25,14.25 A0.95,0.95 0 1 1 11.25,16.15 A0.95,0.95 0 1 1 11.25,14.25 Z", floatArrayOf(10.3f, 14.25f, 12.2f, 16.15f), false, false, true, 1),
        Piece("top", "note_stem", "glyph", "glyph", "M12.1,15.1 L12.1,12.2 C13.3,12.5 13.9,13.1 13.95,13.9", floatArrayOf(12.1f, 12.2f, 13.95f, 15.1f), false, true, true, 1),
        // ── front ──
        Piece("front", "shadow", "shadow", "shadow", "M4.4,19.7 A7.6,1 0 1 1 19.6,19.7 A7.6,1 0 1 1 4.4,19.7 Z", floatArrayOf(4.4f, 18.7f, 19.6f, 20.7f), false, false, false, 1),
        Piece("front", "glow_front", "glow", "glow", "M3.5,12.5 A8.5,2.6 0 1 1 20.5,12.5 A8.5,2.6 0 1 1 3.5,12.5 Z", floatArrayOf(3.5f, 9.9f, 20.5f, 15.1f), false, false, false, 1),
        Piece("front", "wheel_l", "tyres", "tyre", "M6.1,17.3 L7.7,17.3 A0.7,0.7 0 0 1 8.4,18 L8.4,18.8 A0.7,0.7 0 0 1 7.7,19.5 L6.1,19.5 A0.7,0.7 0 0 1 5.4,18.8 L5.4,18 A0.7,0.7 0 0 1 6.1,17.3 Z", floatArrayOf(5.4f, 17.3f, 8.4f, 19.5f), false, false, false, 1),
        Piece("front", "wheel_r", "tyres", "tyre", "M16.3,17.3 L17.9,17.3 A0.7,0.7 0 0 1 18.6,18 L18.6,18.8 A0.7,0.7 0 0 1 17.9,19.5 L16.3,19.5 A0.7,0.7 0 0 1 15.6,18.8 L15.6,18 A0.7,0.7 0 0 1 16.3,17.3 Z", floatArrayOf(15.6f, 17.3f, 18.6f, 19.5f), false, false, false, 1),
        Piece("front", "body", "body", "paint", "M4.6,17.3 L4.6,10.8 C4.6,9.7 5,9 5.7,8.6 L7.4,5.6 C7.7,5 8.3,4.7 9,4.7 L15,4.7 C15.7,4.7 16.3,5 16.6,5.6 L18.3,8.6 C19,9 19.4,9.7 19.4,10.8 L19.4,17.3 C19.4,17.9 19,18.3 18.4,18.3 L5.6,18.3 C5,18.3 4.6,17.9 4.6,17.3 Z", floatArrayOf(4.6f, 4.7f, 19.4f, 18.3f), false, false, false, 1),
        Piece("front", "bonnet", "body", "panel", "M6,9.3 C9.2,10.5 14.8,10.5 18,9.3", floatArrayOf(6f, 9.3f, 18f, 10.2f), false, true, false, 1),
        Piece("front", "windscreen", "glass", "glass", "M7.5,8.8 L8.4,6.1 C8.55,5.65 8.85,5.45 9.3,5.45 L14.7,5.45 C15.15,5.45 15.45,5.65 15.6,6.1 L16.5,8.8 Z", floatArrayOf(7.5f, 5.45f, 16.5f, 8.8f), false, false, false, 1),
        Piece("front", "headlamp_l", "lights", "lamp", "M5.4,12.5 C6.41,10.83 7.99,10.83 9,12.5 C7.99,14.17 6.41,14.17 5.4,12.5 Z", floatArrayOf(5.4f, 11.25f, 9f, 13.75f), false, false, false, 1),
        Piece("front", "headlamp_r", "lights", "lamp", "M15,12.5 C16.01,10.83 17.59,10.83 18.6,12.5 C17.59,14.17 16.01,14.17 15,12.5 Z", floatArrayOf(15f, 11.25f, 18.6f, 13.75f), false, false, false, 1),
        Piece("front", "drl", "lights", "drl", "M5.6,11.6 L18.4,11.6 A0.7,0.7 0 0 1 19.1,12.3 L19.1,12.3 A0.7,0.7 0 0 1 18.4,13 L5.6,13 A0.7,0.7 0 0 1 4.9,12.3 L4.9,12.3 A0.7,0.7 0 0 1 5.6,11.6 Z", floatArrayOf(4.9f, 11.6f, 19.1f, 13f), false, false, false, 1),
        Piece("front", "fog", "lights", "lamp", "M6.4,14.7 L7.8,14.7 A0.6,0.6 0 0 1 8.4,15.3 L8.4,15.7 A0.6,0.6 0 0 1 7.8,16.3 L6.4,16.3 A0.6,0.6 0 0 1 5.8,15.7 L5.8,15.3 A0.6,0.6 0 0 1 6.4,14.7 Z M16.2,14.7 L17.6,14.7 A0.6,0.6 0 0 1 18.2,15.3 L18.2,15.7 A0.6,0.6 0 0 1 17.6,16.3 L16.2,16.3 A0.6,0.6 0 0 1 15.6,15.7 L15.6,15.3 A0.6,0.6 0 0 1 16.2,14.7 Z", floatArrayOf(5.8f, 14.7f, 18.2f, 16.3f), false, false, false, 2),
        Piece("front", "turn_l", "lights", "turn", "M4.8,13.3 L8.4,11.2 L8.4,15.4 Z", floatArrayOf(4.8f, 11.2f, 8.4f, 15.4f), false, false, false, 1),
        Piece("front", "turn_r", "lights", "turn", "M19.2,13.3 L15.6,11.2 L15.6,15.4 Z", floatArrayOf(15.6f, 11.2f, 19.2f, 15.4f), false, false, false, 1),
        Piece("front", "sidelight", "lights", "lamp", "M4.9,12.5 A1.1,1.1 0 1 1 4.9,14.7 A1.1,1.1 0 1 1 4.9,12.5 Z M19.1,12.5 A1.1,1.1 0 1 1 19.1,14.7 A1.1,1.1 0 1 1 19.1,12.5 Z", floatArrayOf(3.8f, 12.5f, 20.2f, 14.7f), false, false, false, 2),
        Piece("front", "mirror", "doors", "mirror", "M3,9.3 L4,9.3 A0.5,0.5 0 0 1 4.5,9.8 L4.5,10.1 A0.5,0.5 0 0 1 4,10.6 L3,10.6 A0.5,0.5 0 0 1 2.5,10.1 L2.5,9.8 A0.5,0.5 0 0 1 3,9.3 Z M20,9.3 L21,9.3 A0.5,0.5 0 0 1 21.5,9.8 L21.5,10.1 A0.5,0.5 0 0 1 21,10.6 L20,10.6 A0.5,0.5 0 0 1 19.5,10.1 L19.5,9.8 A0.5,0.5 0 0 1 20,9.3 Z", floatArrayOf(2.5f, 9.3f, 21.5f, 10.6f), false, false, false, 2),
        Piece("front", "glow_rear", "rear", "glowtail", "M5,12.1 A7,2 0 1 1 19,12.1 A7,2 0 1 1 5,12.1 Z", floatArrayOf(5f, 10.1f, 19f, 14.1f), false, false, false, 1),
        Piece("front", "tail_bar", "rear", "tail", "M7,11.2 L17,11.2 A0.8,0.8 0 0 1 17.8,12 L17.8,12.1 A0.8,0.8 0 0 1 17,12.9 L7,12.9 A0.8,0.8 0 0 1 6.2,12.1 L6.2,12 A0.8,0.8 0 0 1 7,11.2 Z", floatArrayOf(6.2f, 11.2f, 17.8f, 12.9f), false, false, false, 1),
        Piece("front", "plate", "rear", "plate", "M10,13.9 L14,13.9 A0.4,0.4 0 0 1 14.4,14.3 L14.4,15.1 A0.4,0.4 0 0 1 14,15.5 L10,15.5 A0.4,0.4 0 0 1 9.6,15.1 L9.6,14.3 A0.4,0.4 0 0 1 10,13.9 Z", floatArrayOf(9.6f, 13.9f, 14.4f, 15.5f), false, false, false, 1),
        Piece("front", "fog_rear", "rear", "tail", "M6.8,13.9 L8,13.9 A0.5,0.5 0 0 1 8.5,14.4 L8.5,15 A0.5,0.5 0 0 1 8,15.5 L6.8,15.5 A0.5,0.5 0 0 1 6.3,15 L6.3,14.4 A0.5,0.5 0 0 1 6.8,13.9 Z", floatArrayOf(6.3f, 13.9f, 8.5f, 15.5f), false, false, false, 1),
        Piece("front", "rays_high", "glyph", "glyph", "M3,18.9 L13.4,18.9 M3,21.1 L13.4,21.1", floatArrayOf(3f, 18.9f, 13.4f, 21.1f), false, true, true, 1),
        Piece("front", "rays_low", "glyph", "glyph", "M5.4,18.9 L3.2,21.1 M8.8,18.9 L6.6,21.1 M12.2,18.9 L10,21.1", floatArrayOf(3.2f, 18.9f, 12.2f, 21.1f), false, true, true, 1),
        Piece("front", "fog_beam", "glyph", "glyph", "M3.2,20 L11.6,20 M14.6,18.2 C13.2,19.1 15.2,20 13.8,20.9", floatArrayOf(3.2f, 18.2f, 14.6f, 20.9f), false, true, true, 1),
        Piece("front", "mode_rays", "glyph", "glyph", "M4.4,18.9 L2.9,21 M7,18.9 L5.5,21 M14.6,19.1 L21.1,19.1 M14.6,21.1 L21.1,21.1", floatArrayOf(2.9f, 18.9f, 21.1f, 21.1f), false, true, true, 1),
        Piece("front", "defrost_waves", "glyph", "glyph", "M7.8,6.6 C9.1,5.8 10.2,7.4 11.5,6.6 C12.8,5.8 13.9,7.4 15.2,6.6 M7.8,9 C9.1,8.2 10.2,9.8 11.5,9 C12.8,8.2 13.9,9.8 15.2,9", floatArrayOf(7.8f, 6.37f, 15.2f, 9.23f), false, true, true, 1),
        Piece("front", "hinge", "glyph", "glyph", "M7,9.9 L16.8,9.9 M7.4,7.8 L16.4,6.4", floatArrayOf(7f, 6.4f, 16.8f, 9.9f), false, true, true, 1),
        Piece("front", "arrow_h_stem", "glyph", "glyph", "M7.2,12.6 L7.2,14.3", floatArrayOf(7.2f, 12.6f, 7.2f, 14.3f), false, true, true, 1),
        Piece("front", "arrow_h_heads", "glyph", "glyph", "M7.2,11.1 L8.4,12.44 L6,12.44 Z M7.2,15.8 L6,14.46 L8.4,14.46 Z", floatArrayOf(6f, 11.1f, 8.4f, 15.8f), false, false, true, 2),
        // ── side ──
        Piece("side", "shadow", "shadow", "shadow", "M2.6,18.6 A9.6,0.8 0 1 1 21.8,18.6 A9.6,0.8 0 1 1 2.6,18.6 Z", floatArrayOf(2.6f, 17.8f, 21.8f, 19.4f), false, false, false, 1),
        Piece("side", "glow_front", "glow", "glow", "M0.8,11.5 A2.4,1.6 0 1 1 5.6,11.5 A2.4,1.6 0 1 1 0.8,11.5 Z", floatArrayOf(0.8f, 9.9f, 5.6f, 13.1f), false, false, false, 1),
        Piece("side", "glow_rear", "glow", "glowtail", "M19.6,11.4 A2,1.4 0 1 1 23.6,11.4 A2,1.4 0 1 1 19.6,11.4 Z", floatArrayOf(19.6f, 10f, 23.6f, 12.8f), false, false, false, 1),
        Piece("side", "body", "body", "paint", "M2.6,15.4 L2.9,12.6 C3,11.6 3.6,10.9 4.6,10.6 L7.6,9.9 L10,6.6 C10.4,6.1 11,5.8 11.7,5.8 L16.6,5.8 C17.2,5.8 17.7,6.1 18,6.6 L19.7,9.6 L21,10.2 C21.6,10.5 21.9,11 21.9,11.7 L21.8,15.4 C21.8,16 21.4,16.4 20.8,16.4 L3.6,16.4 C3,16.4 2.6,16 2.6,15.4 Z", floatArrayOf(2.6f, 5.8f, 21.9f, 16.4f), false, false, false, 1),
        Piece("side", "bonnet", "body", "panel", "M4.7,10.75 L7.5,10.1", floatArrayOf(4.7f, 10.1f, 7.5f, 10.75f), false, true, false, 1),
        Piece("side", "windscreen", "glass", "glass", "M8.3,9.75 L10.5,6.75 L12.2,6.75 L12.2,9.75 Z", floatArrayOf(8.3f, 6.75f, 12.2f, 9.75f), false, false, false, 1),
        Piece("side", "glass_front", "glass", "glass", "M12.95,6.75 L15.55,6.75 A0.25,0.25 0 0 1 15.8,7 L15.8,9.5 A0.25,0.25 0 0 1 15.55,9.75 L12.95,9.75 A0.25,0.25 0 0 1 12.7,9.5 L12.7,7 A0.25,0.25 0 0 1 12.95,6.75 Z", floatArrayOf(12.7f, 6.75f, 15.8f, 9.75f), false, false, false, 1),
        Piece("side", "glass_rear", "glass", "glass", "M16.55,6.75 L17.55,6.75 A0.25,0.25 0 0 1 17.8,7 L17.8,9.5 A0.25,0.25 0 0 1 17.55,9.75 L16.55,9.75 A0.25,0.25 0 0 1 16.3,9.5 L16.3,7 A0.25,0.25 0 0 1 16.55,6.75 Z", floatArrayOf(16.3f, 6.75f, 17.8f, 9.75f), false, false, false, 1),
        Piece("side", "glass_quarter", "glass", "glass", "M18.2,6.95 L19.35,9 L19.35,9.75 L18.2,9.75 Z", floatArrayOf(18.2f, 6.95f, 19.35f, 9.75f), false, false, false, 1),
        Piece("side", "headlamp", "lights", "lamp", "M2.95,11.1 L5,10.55 L5.3,11.7 L3.05,12.1 Z", floatArrayOf(2.95f, 10.55f, 5.3f, 12.1f), false, false, false, 1),
        Piece("side", "tail", "lights", "tail", "M20.4,10.55 L21.75,11.05 L21.75,12.1 L20.4,11.8 Z", floatArrayOf(20.4f, 10.55f, 21.75f, 12.1f), false, false, false, 1),
        Piece("side", "wheel_front", "tyres", "tyre", "M6.6,14 A2.1,2.1 0 1 1 6.6,18.2 A2.1,2.1 0 1 1 6.6,14 Z", floatArrayOf(4.5f, 14f, 8.7f, 18.2f), false, false, false, 1),
        Piece("side", "wheel_rear", "tyres", "tyre", "M17.9,14 A2.1,2.1 0 1 1 17.9,18.2 A2.1,2.1 0 1 1 17.9,14 Z", floatArrayOf(15.8f, 14f, 20f, 18.2f), false, false, false, 1),
        Piece("side", "rim_front", "tyres", "rim", "M6.6,15 A1.1,1.1 0 1 1 6.6,17.2 A1.1,1.1 0 1 1 6.6,15 Z", floatArrayOf(5.5f, 15f, 7.7f, 17.2f), false, false, false, 1),
        Piece("side", "rim_rear", "tyres", "rim", "M17.9,15 A1.1,1.1 0 1 1 17.9,17.2 A1.1,1.1 0 1 1 17.9,15 Z", floatArrayOf(16.8f, 15f, 19f, 17.2f), false, false, false, 1),
        Piece("side", "door_front", "doors", "panel", "M12.9,10.2 L15.6,10.2 A0.3,0.3 0 0 1 15.9,10.5 L15.9,15.4 A0.3,0.3 0 0 1 15.6,15.7 L12.9,15.7 A0.3,0.3 0 0 1 12.6,15.4 L12.6,10.5 A0.3,0.3 0 0 1 12.9,10.2 Z", floatArrayOf(12.6f, 10.2f, 15.9f, 15.7f), false, false, false, 1),
        Piece("side", "door_rear", "doors", "panel", "M16.6,10.2 L18.9,10.2 A0.3,0.3 0 0 1 19.2,10.5 L19.2,13.5 A0.3,0.3 0 0 1 18.9,13.8 L16.6,13.8 A0.3,0.3 0 0 1 16.3,13.5 L16.3,10.5 A0.3,0.3 0 0 1 16.6,10.2 Z", floatArrayOf(16.3f, 10.2f, 19.2f, 13.8f), false, false, false, 1),
        Piece("side", "mirror", "doors", "mirror", "M9.2,9 L9.8,9 A0.3,0.3 0 0 1 10.1,9.3 L10.1,9.6 A0.3,0.3 0 0 1 9.8,9.9 L9.2,9.9 A0.3,0.3 0 0 1 8.9,9.6 L8.9,9.3 A0.3,0.3 0 0 1 9.2,9 Z", floatArrayOf(8.9f, 9f, 10.1f, 9.9f), false, false, false, 1),
    )

    /** Lớp highlight của mỗi mặt dùng LẠI hình của mảnh nào (viền theo tone, không phải path mới). */
    val HIGHLIGHT_REF: Map<String, String> = mapOf("top" to "body", "front" to "body", "side" to "body")
}
