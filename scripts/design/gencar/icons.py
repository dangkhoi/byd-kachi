# -*- coding: utf-8 -*-
# Tách THUẦN từ scripts/design/gen-car.py (882 dòng → trần 500, L6-debt 2026-09-27). Thân hàm giữ nguyên byte;
# `gen-car.py` chỉ còn là điểm vào CLI + re-export cho render-car.py. `--check` so byte đầu ra: khớp 41/41 sau khi tách.
"""Bảng ICON + FACE_VD và bộ sinh icon 24dp (`ic_car_*.xml`)."""
from __future__ import annotations

from .model import Face
from .paths import ALPHA_CTX, ALPHA_SUB, MAX_ICON_PATHS, STROKE_MAIN, STROKE_SUB

# ── Bảng ICON: id → (mặt, PHỤ, CHÍNH, vì-sao) ─────────────────────────────────────────────────────────────────
# CHÍNH: chuỗi = một mảnh; tuple = gộp nhiều mảnh thành MỘT path; hậu tố ":stroke" = vẽ bằng nét dù mảnh là vùng tô.
TOP_SUB = ["windscreen", "glass_rear"]
TOP_WS = ["windscreen"]
FRONT_SUB = ["windscreen", "headlamp_l", "headlamp_r", "bonnet"]
REAR_SUB = ["windscreen", "tail_bar", "plate"]

ICONS: dict[str, tuple[str, list[str], list, str]] = {
    # — mặt trên · lốp —
    "ic_car_top_tyre_fl": ("top", TOP_SUB, ["wheel_fl"], "ÁP SUẤT LỐP FL — khung xe + CHỈ bánh này, tô đặc. Ba bánh kia BỎ HẲN ([ĐO Pass 1 U7] vẽ kèm ba bánh kia thì bốn ô chỉ khác nhau 0.8dp ⇒ 4/8 đúng)."),
    "ic_car_top_tyre_fr": ("top", TOP_SUB, ["wheel_fr"], "ÁP SUẤT LỐP FR — khung xe + CHỈ bánh này, tô đặc. Ba bánh kia BỎ HẲN."),
    "ic_car_top_tyre_rl": ("top", TOP_SUB, ["wheel_rl"], "ÁP SUẤT LỐP RL — khung xe + CHỈ bánh này, tô đặc. Ba bánh kia BỎ HẲN."),
    "ic_car_top_tyre_rr": ("top", TOP_SUB, ["wheel_rr"], "ÁP SUẤT LỐP RR — khung xe + CHỈ bánh này, tô đặc. Ba bánh kia BỎ HẲN."),
    "ic_car_top_tyre_temp_fl": ("top", TOP_SUB, ["wheel_fl", "therm_stem", "therm_bulb"], "NHIỆT LỐP FL — bánh tô đặc + NHIỆT KẾ giữa xe (bầu tròn + cán) ⇒ hai họ lốp không lẫn."),
    "ic_car_top_tyre_temp_fr": ("top", TOP_SUB, ["wheel_fr", "therm_stem", "therm_bulb"], "NHIỆT LỐP FR — bánh tô đặc + NHIỆT KẾ giữa xe (bầu tròn + cán)."),
    "ic_car_top_tyre_temp_rl": ("top", TOP_SUB, ["wheel_rl", "therm_stem", "therm_bulb"], "NHIỆT LỐP RL — bánh tô đặc + NHIỆT KẾ giữa xe (bầu tròn + cán)."),
    "ic_car_top_tyre_temp_rr": ("top", TOP_SUB, ["wheel_rr", "therm_stem", "therm_bulb"], "NHIỆT LỐP RR — bánh tô đặc + NHIỆT KẾ giữa xe (bầu tròn + cán)."),
    # — mặt trên · cửa & kính —
    "ic_car_top_door_lf": ("top", TOP_SUB, ["door_lf"], "CỬA LF — vạt cửa mở ra ngoài đúng góc xe."),
    "ic_car_top_door_rf": ("top", TOP_SUB, ["door_rf"], "CỬA RF — vạt cửa mở ra ngoài đúng góc xe."),
    "ic_car_top_door_lr": ("top", TOP_SUB, ["door_lr"], "CỬA LR — vạt cửa mở ra ngoài đúng góc xe."),
    "ic_car_top_door_rr": ("top", TOP_SUB, ["door_rr"], "CỬA RR — vạt cửa mở ra ngoài đúng góc xe."),
    "ic_car_top_door_all": ("top", TOP_SUB, ["door_lf", "door_lr", "door_rf", "door_rr"], "TẤT CẢ CỬA — cả bốn vạt cửa mở."),
    # 2.76 R8 · cặp trạng thái MỞ/ĐÓNG cho chip thanh trên (CapabilityIcons.STATE): MỞ = vạt xoè (bốn dòng trên), ĐÓNG =
    # vạch cửa sát thân, cùng góc xe. Chưa đọc được cũng dùng hình ĐÓNG (mở là trạng thái đáng báo, không vẽ sẵn).
    "ic_car_top_door_lf_shut": ("top", TOP_SUB, ["door_lf_shut"], "CỬA LF ĐÓNG — vạch cửa nằm SÁT thân đúng góc xe, không vạt xoè; cặp với ic_car_top_door_lf (mở)."),
    "ic_car_top_door_rf_shut": ("top", TOP_SUB, ["door_rf_shut"], "CỬA RF ĐÓNG — vạch cửa nằm SÁT thân đúng góc xe; cặp với ic_car_top_door_rf (mở)."),
    "ic_car_top_door_lr_shut": ("top", TOP_SUB, ["door_lr_shut"], "CỬA LR ĐÓNG — vạch cửa nằm SÁT thân đúng góc xe; cặp với ic_car_top_door_lr (mở)."),
    "ic_car_top_door_rr_shut": ("top", TOP_SUB, ["door_rr_shut"], "CỬA RR ĐÓNG — vạch cửa nằm SÁT thân đúng góc xe; cặp với ic_car_top_door_rr (mở)."),
    "ic_car_top_window_lf": ("top", TOP_SUB, ["glass_lf"], "KÍNH LF — ô kính cửa trong thân, đúng góc xe, tô đặc trên nền hai ô kính cố định mờ."),
    "ic_car_top_window_rf": ("top", TOP_SUB, ["glass_rf"], "KÍNH RF — ô kính cửa trong thân, đúng góc xe."),
    "ic_car_top_window_lr": ("top", TOP_SUB, ["glass_lr"], "KÍNH LR — ô kính cửa trong thân, đúng góc xe."),
    "ic_car_top_window_rr": ("top", TOP_SUB, ["glass_rr"], "KÍNH RR — ô kính cửa trong thân, đúng góc xe."),
    "ic_car_top_window_all": ("top", TOP_SUB, ["glass_lf", "glass_rf", "glass_lr", "glass_rr"], "TẤT CẢ KÍNH — cả bốn ô kính cửa."),
    # ⚠ UX-OVERHAUL · WP8 2026-09-20 — TÁM icon xe đã XOÁ cùng mã của chúng (owner purge 37 mã BỎ):
    #   ic_car_top_mirror (gương #30) · ic_car_top_trunk_pos (vị trí cốp #29) · ic_car_top_ambient + 5 biến thể
    #   đèn viền (#39-43 · #46-49). Giữ dòng sinh cho một mã không còn tồn tại = sinh ra tệp mồ côi, và
    #   `IconStyleContractTest` bắt đúng ca đó ở cả hai chiều (tệp không ai dùng · bảng tra trỏ vào hư không).
    "ic_car_top_trunk": ("top", TOP_SUB, ["boot"], "CỐP SAU — mảng đuôi xe tô đặc (nắp ĐANG ĐÓNG, liền khối)."),
    "ic_car_top_sunroof": ("top", TOP_SUB, ["sunroof"], "CỬA SỔ TRỜI — ô nóc tô đặc (tấm kính ĐANG ĐÓNG, kín ô)."),
    "ic_car_top_sunshade": ("top", TOP_SUB, ["sunshade"], "RÈM CHE NẮNG — THANH CUỘN ở mép trước nóc + TẤM PHỦ có HAI NẾP GẤP khoét rỗng (even-odd)."),
    # 2.76 R8 · CỬA SỔ TRỜI MỞ — cặp trạng thái với ic_car_top_sunroof (đóng, tô đặc). Không mũi tên: mũi tên là
    # "vị trí/hành động" của sunroof_pos (datum đã gỡ 2026-09-25). Xe owner không có cửa sổ trời ⇒ 🚗 N/A, khai generic.
    "ic_car_top_sunroof_open": ("top", TOP_SUB, ["sunroof:stroke", "sunroof_gap"], "CỬA SỔ TRỜI MỞ — ô nóc NÉT + khe hở TÔ ở mép trước (kính đã trượt); cặp với ic_car_top_sunroof (đóng, kín ô)."),
    "ic_car_top_sunroof_pos": ("top", TOP_WS, ["sunroof:stroke", "sunroof_gap", "arrow_v_stem", "arrow_v_heads"], "VỊ TRÍ CỬA SỔ TRỜI — ô nóc NÉT + khe hở TÔ ở mép trước (kính đã trượt) + MŨI TÊN ĐÔI dọc xe (kính hậu bỏ để chừa chỗ mũi tên)."),
    "ic_car_top_lock": ("top", TOP_SUB, ["lock_shackle", "lock_body"], "KHOÁ XE — thân xe + ổ khoá giữa khoang."),
    "ic_car_top_seat_fl": ("top", TOP_SUB, ["seat_fl"], "GHẾ FL — đệm + tựa, đúng chỗ trong khoang."),
    # — mặt trên · đèn viền —
    # — mặt trước · đèn —
    "ic_car_front_highbeam": ("front", ["windscreen", "headlamp_r", "bonnet"], ["headlamp_l", "rays_high"], "ĐÈN PHA — MỘT đèn sáng + hai tia NGANG DÀI xuyên ra trước, nằm HẲN ngoài thân xe; đèn kia mờ."),
    "ic_car_front_lowbeam": ("front", ["windscreen", "headlamp_r", "bonnet"], ["headlamp_l", "rays_low"], "ĐÈN CỐT — MỘT đèn sáng + ba tia NGẮN CHÚC XUỐNG mặt đường, nằm HẲN ngoài thân xe."),
    "ic_car_front_headlight_mode": ("front", ["windscreen", "bonnet"], [("headlamp_l", "headlamp_r"), "mode_rays"], "CHẾ ĐỘ ĐÈN PHA — hai đèn cùng sáng + CẢ HAI KIỂU CHÙM bày cạnh nhau dưới cản: cốt (chúc) trái, pha (ngang) phải."),
    "ic_car_front_drl": ("front", FRONT_SUB, ["drl"], "ĐÈN BAN NGÀY — DẢI SÁNG dài tô đặc (LED strip) vắt qua hai hốc đèn mờ."),
    "ic_car_front_fog": ("front", FRONT_SUB, ["fog", "fog_beam"], "ĐÈN SƯƠNG MÙ TRƯỚC — đèn DƯỚI CẢN (thấp, sát mép vỏ) + tia thấp bị VỆT SƯƠNG cắt ngang."),
    "ic_car_front_sidelight": ("front", FRONT_SUB, ["sidelight"], "ĐÈN HÔNG — hốc đèn chính mờ (tắt) + hai chấm sáng nằm trên VÁCH HÔNG xe."),
    "ic_car_front_turn_l": ("front", FRONT_SUB, ["turn_l"], "XI-NHAN TRÁI — mũi tên TÔ ở cụm đèn trái, chỉ RA NGOÀI; bên phải không tô gì (chưa sáng)."),
    "ic_car_front_turn_r": ("front", FRONT_SUB, ["turn_r"], "XI-NHAN PHẢI — mũi tên TÔ ở cụm đèn phải, chỉ RA NGOÀI; bên trái không tô gì."),
    # — mặt sau (cùng bóng thân với mặt trước) —
    "ic_car_rear_fog": ("front", REAR_SUB, ["fog_rear", "fog_beam"], "ĐÈN SƯƠNG MÙ SAU — khung NHÌN TỪ SAU (đèn hậu DÀI NGANG + BIỂN SỐ mờ) + đèn sương mù THẤP bên trái, tô đặc + vệt sương."),
    "ic_car_rear_defrost": ("front", REAR_SUB, ["defrost_waves"], "SẤY KÍNH SAU — khung NHÌN TỪ SAU (đèn hậu + biển số mờ) + SÓNG NHIỆT trên kính hậu."),
}

# Thành phần bốn mặt 48dp có chuyển sắc — theo THỨ TỰ vẽ (bánh dưới thân ở mặt trước/sau; trên thân ở mặt ngang).
FACE_VD: dict[str, tuple[str, list[str]]] = {
    # Xi-nhan · sương mù · đèn hông là LỚP TRẠNG THÁI (chỉ vẽ khi bật) ⇒ không nằm trong mặt "nghỉ".
    "top": ("top", ["glow_front", "glow_rear", "body", "windscreen", "glass_rear", "glass_lf", "glass_rf", "glass_lr", "glass_rr",
                    "sunroof", "headlamp", "drl", "tail", "wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr",
                    "bonnet", "boot", "mirror"]),
    "front": ("front", ["shadow", "glow_front", "wheel_l", "wheel_r", "body", "bonnet", "windscreen", "headlamp_l", "headlamp_r",
                        "drl", "mirror"]),
    "rear": ("front", ["shadow", "glow_rear", "wheel_l", "wheel_r", "body", "windscreen", "tail_bar", "plate", "fog_rear", "mirror"]),
    "side": ("side", ["shadow", "glow_front", "glow_rear", "body", "bonnet", "windscreen", "glass_front", "glass_rear", "glass_quarter",
                      "headlamp", "tail", "wheel_front", "wheel_rear", "rim_front", "rim_rear", "door_front", "door_rear", "mirror"]),
}


# ── Sinh icon 24dp ────────────────────────────────────────────────────────────────────────────────────────────

def _resolve(face: Face, spec) -> tuple[str, bool, bool, int]:
    """spec → (path, stroke?, evenOdd?, số mảnh). Tuple = gộp; ':stroke' = ép nét."""
    ids = list(spec) if isinstance(spec, tuple) else [spec]
    force_stroke = False
    paths, ev, n = [], False, 0
    by = face.by_id()
    for s in ids:
        if s.endswith(":stroke"):
            s = s[: -len(":stroke")]
            force_stroke = True
        p = by[s]
        if p.ref:
            p = by[p.ref]
        paths.append(p.path)
        ev = ev or p.even_odd
        n += 1
    stroke = force_stroke or all((by[s.split(":")[0]]).stroke_only for s in ids)
    return " ".join(paths), stroke, ev, n


def _path_xml(path: str, *, fill_alpha: str | None, stroke: bool, stroke_w: str | None, stroke_alpha: str | None,
              even_odd: bool, fill_and_stroke: bool = False) -> str:
    a = [f'android:pathData="{path}"']
    if stroke and not fill_and_stroke:
        a.append('android:fillColor="#00000000"')
    else:
        a.append('android:fillColor="#FFFFFF"')
        if fill_alpha:
            a.append(f'android:fillAlpha="{fill_alpha}"')
        if even_odd:
            a.append('android:fillType="evenOdd"')
    if stroke or fill_and_stroke:
        a.append('android:strokeColor="#FFFFFF"')
        a.append(f'android:strokeWidth="{stroke_w}"')
        if stroke_alpha:
            a.append(f'android:strokeAlpha="{stroke_alpha}"')
        a.append('android:strokeLineCap="round"')
        a.append('android:strokeLineJoin="round"')
    return "    <path " + "\n        ".join(a) + " />"


def gen_icon(faces: dict[str, Face], name: str) -> tuple[str, dict]:
    face_name, sub, main, why = ICONS[name]
    face = faces[face_name]
    by = face.by_id()
    body = by["body"]
    paths: list[str] = []
    # NỀN: thân tô 0.16 + viền nét phụ 0.38 — MỘT path (fillAlpha + strokeAlpha cùng phần tử)
    paths.append(_path_xml(body.path, fill_alpha=ALPHA_CTX, stroke=True, stroke_w=STROKE_SUB, stroke_alpha=ALPHA_SUB,
                           even_odd=False, fill_and_stroke=True))
    # PHỤ: vùng tô gộp một path · nét gộp một path
    sub_fill = [by[s].path for s in sub if not by[s].stroke_only]
    sub_stroke = [by[s].path for s in sub if by[s].stroke_only]
    if sub_fill:
        paths.append(_path_xml(" ".join(sub_fill), fill_alpha=ALPHA_SUB, stroke=False, stroke_w=None, stroke_alpha=None,
                               even_odd=False))
    if sub_stroke:
        paths.append(_path_xml(" ".join(sub_stroke), fill_alpha=None, stroke=True, stroke_w=STROKE_SUB, stroke_alpha=ALPHA_SUB,
                               even_odd=False))
    # CHÍNH: mỗi mục một path, alpha 1.0
    pieces_used = []
    for spec in main:
        path, stroke, ev, n = _resolve(face, spec)
        paths.append(_path_xml(path, fill_alpha=None, stroke=stroke, stroke_w=STROKE_MAIN if stroke else None,
                               stroke_alpha=None, even_odd=ev))
        pieces_used.append(spec if isinstance(spec, str) else "+".join(spec))
    if len(paths) > MAX_ICON_PATHS:
        raise ValueError(f"{name}: {len(paths)} path > trần {MAX_ICON_PATHS} (AC5.1)")
    xml = (
        "<!--\n"
        f"  SINH BỞI scripts/design/gen-car.py từ design/car/{face_name}.svg — KHÔNG SỬA TAY (sửa SVG rồi sinh lại; cờ check của script so byte).\n"
        "  VISUAL-REFRESH P3 · spec docs/specs/kachi-visual-refresh.html §4.2: NỀN thân 0.16 + viền 1.2/0.38 · PHỤ 0.38 · CHÍNH 1.0/nét 1.8.\n"
        "  Khung 24×24 · một tông #FFFFFF (chỗ dùng tint) · cap/join tròn · ≤ 6 path.\n"
        f"  {why}\n"
        "-->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp" android:height="24dp"\n'
        '    android:viewportWidth="24" android:viewportHeight="24">\n'
        + "\n".join(paths)
        + "\n</vector>\n"
    )
    meta = {"face": face_name, "sub": list(sub), "main": pieces_used, "paths": len(paths)}
    return xml, meta
