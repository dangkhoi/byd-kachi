#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Nở CORPUS CÂU NÓI `scripts/voice/data/variants.tsv` từ danh mục thật + bộ từ vựng do LLM soạn tay.

## Vì sao có tệp này (owner 2026-09-16)
*"có dùng AI LLM này để generate ra nhiều nhiều các mẫu câu, vùng miền, tiếng anh kiểu tiếng việt này kia để
đảm bảo nhận dạng tốt hơn không? tự xây data kiểu vậy thì may ra mới ổn, chứ chờ xe cũng ko biết chờ gì"*.

Chờ chuyến xe để biết câu nào nghe sai là **chờ mù**: mỗi chuyến chỉ cho vài chục câu, không lặp lại được, và
không ai biết câu vừa sai là do âm học hay do thiếu từ đồng nghĩa. Corpus tự dựng thì đo được **hàng nghìn**
câu ngay trên máy, lặp lại y hệt sau mỗi lần sửa tệp hotword — đó là thứ `mishear-table.py` cần.

## Phần nào do NGƯỜI (LLM) làm, phần nào do MÁY làm — tách bạch
 • NGƯỜI: `data/nouns.tsv` (cách gọi theo vùng miền), `data/templates.tsv` (khuôn câu + tiểu từ),
   `data/apps.tsv` (phiên âm Việt của tên app), `data/units.tsv` (trị số nói ra), `data/extra.tsv`
   (câu nguyên văn cho nhạc/dẫn đường/hồ sơ/bố cục/câu-không-phải-lệnh + đúng câu tester báo sai).
   Đây là phần **không sinh được bằng máy**: không có nhãn nào trong registry nói cho ta biết người miền Nam
   gọi *"kính"* là *"kiếng"*, hay *"Waze"* đọc thành *"quây"*.
 • MÁY: nở tổ hợp (khuôn × cách gọi), khử trùng, giữ thứ tự ⇒ chạy lại cho ra **y hệt** (deterministic).

## Luật nở
 • Cách gọi nền lấy từ `core/build/catalog/registry.json` (nhãn + nhãn ngắn), cùng nguồn với `VoiceGrammar` ⇒
   thêm một nút là tự có câu, không ai phải nhớ (CLAUDE.md §7).
 • Khuôn ghép luân phiên: khuôn thứ i đi với cách gọi thứ (i mod số-cách-gọi) ⇒ MỌI khuôn (mọi vùng/kiểu) đều
   xuất hiện, thay vì tiêu hết hạn mức vào 2–3 khuôn đầu.
 • Khuôn có ĐỘNG TỪ đứng ngay trước `{n}` bị bỏ nếu cách gọi đã mang sẵn động từ — cùng luật với
   `SherpaPhraseHotwords.startsWithVerb` (*"mở mở khoá cửa"* là rác).

Dùng:  python3 scripts/voice/gen-variants.py [--registry …] [--out …] [--max-per-id 20]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
REPO = os.path.dirname(os.path.dirname(HERE))

# Từ đầu của một dạng động từ — cùng vai trò `SherpaPhraseHotwords.VERB_HEADS`. `bấm`/`chạy` cố ý KHÔNG có
# mặt: chúng ghép được với cách gọi đã có động từ (*"chạy mở hết kính"* là câu thật, registry cũng khai vậy).
VERB_HEADS = {
    "bật", "tắt", "mở", "đóng", "tăng", "giảm", "đặt", "chỉnh",
    "xem", "đọc", "hiện", "hạ", "kéo", "vào", "vô", "coi", "kiểm",
}

KIND_OF_CONTROL = {
    "TOGGLE": ("TOGGLE", "control_toggle"),
    "COVER": ("COVER", "control_cover"),
    "STEP": ("STEP", "control_step"),
    "SELECT": ("SELECT", "control_select"),
    "BUTTON": ("BUTTON", "control_button"),
}

ONLY = "@only"


def rows(path: str, ncol: int) -> list[list[str]]:
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) != ncol:
                sys.exit(f"{path}: cần {ncol} cột, gặp {len(parts)}: {line!r}")
            out.append([p.strip() for p in parts])
    return out


def base_nouns(label: str, short: str | None) -> list[str]:
    """Cách gọi nền sinh từ nhãn registry: bỏ ngoặc, tách dấu liệt kê, bỏ token có chữ số."""
    out: list[str] = []
    for chunk in re.split(r"[/,;()\[\]|·+–—]", label or ""):
        words = [w for w in re.split(r"[^\wÀ-ỹ]+", chunk) if w and not any(c.isdigit() for c in w)]
        if words:
            cleaned = " ".join(words).lower()
            if len(cleaned) >= 2 and cleaned not in out:
                out.append(cleaned)
    if short:
        s = " ".join(w for w in re.split(r"[^\wÀ-ỹ]+", short) if w and not any(c.isdigit() for c in w)).lower()
        # Nhãn ngắn kiểu *"Kính TT"* / *"Lốp SP"* là chữ viết tắt cho MẮT ĐỌC, không ai nói ra miệng ⇒ bỏ.
        if len(s) >= 4 and s not in out and not re.search(r"\b[a-zA-ZÀ-ỹ]{1,2}\b$", s):
            out.append(s)
    return out


def verb_before_slot(template: str) -> str | None:
    """Từ đứng ngay trước `{n}` trong khuôn, hoặc None."""
    i = template.find("{n}")
    if i <= 0:
        return None
    head = template[:i].strip().split()
    return head[-1].lower() if head else None


def starts_with_verb(noun: str) -> bool:
    return noun.split(" ", 1)[0].lower() in VERB_HEADS


def expand(kind_tmpls, nouns, args, values, max_per_id):
    """Ghép luân phiên khuôn × cách gọi (× lựa chọn / trị số). Giữ thứ tự, khử trùng."""
    out: list[tuple[str, str, str]] = []
    seen: set[str] = set()
    if not nouns:
        return out
    # Nhiều vòng: vòng k ghép khuôn i với cách gọi (i + k) mod n ⇒ mọi khuôn được dùng trước khi lặp lại.
    for k in range(len(nouns)):
        for i, (treg, tsty, tmpl) in enumerate(kind_tmpls):
            noun_reg, noun = nouns[(i + k) % len(nouns)]
            vb = verb_before_slot(tmpl)
            if vb in VERB_HEADS and starts_with_verb(noun):
                continue
            fills = [{}]
            if "{a}" in tmpl:
                if not args:
                    continue
                fills = [{"a": a} for a in args]
            if "{v}" in tmpl:
                if not values:
                    continue
                fills = [{"v": v} for v in values]
            for fill in fills:
                text = tmpl.replace("{n}", noun)
                for key, val in fill.items():
                    text = text.replace("{" + key + "}", val)
                text = re.sub(r"\s+", " ", text).strip()
                if text.lower() in seen:
                    continue
                seen.add(text.lower())
                region = noun_reg if noun_reg != "chung" else treg
                out.append((region, tsty, text))
                if len(out) >= max_per_id:
                    return out
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--registry", default=os.path.join(REPO, "core/build/catalog/registry.json"))
    ap.add_argument("--out", default=os.path.join(DATA, "variants.tsv"))
    ap.add_argument("--max-per-id", type=int, default=20)
    a = ap.parse_args()

    reg = json.load(open(a.registry, encoding="utf-8"))

    tmpls: dict[str, list[tuple[str, str, str]]] = {}
    for kind, region, style, tmpl in rows(os.path.join(DATA, "templates.tsv"), 4):
        tmpls.setdefault(kind, []).append((region, style, tmpl))

    extra_nouns: dict[str, list[tuple[str, str]]] = {}
    drop_base: set[str] = set()
    for rid, region, noun in rows(os.path.join(DATA, "nouns.tsv"), 3):
        if noun == ONLY:
            drop_base.add(rid)
            continue
        extra_nouns.setdefault(rid, []).append((region, noun))

    units: dict[str, list[str]] = {}
    for rid, _style, value in rows(os.path.join(DATA, "units.tsv"), 3):
        units.setdefault(rid, []).append(value)

    lines: list[tuple[str, str, str, str, str]] = []
    seen_global: set[tuple[str, str]] = set()

    def emit(rid: str, ikind: str, region: str, style: str, text: str) -> None:
        key = (rid, text.lower())
        if key in seen_global:
            return
        seen_global.add(key)
        lines.append((rid, ikind, region, style, unicodedata.normalize("NFC", text)))

    def nouns_for(rid: str, label: str, short: str | None) -> list[tuple[str, str]]:
        base = [] if rid in drop_base else [("chung", n) for n in base_nouns(label, short)]
        out, seen = [], set()
        for reg, n in base + extra_nouns.get(rid, []):
            if n.lower() not in seen:
                seen.add(n.lower())
                out.append((reg, n))
        return out

    def templates_for(tkind: str) -> list[tuple[str, str, str]]:
        # Thiếu khuôn cho một loại ⇒ nói ra loại nào và sửa ở đâu. `tmpls[tkind]` trần ném KeyError
        # trụi lủi, người chạy không biết phải thêm dòng nào vào data/templates.tsv.
        if tkind not in tmpls:
            sys.exit(f"data/templates.tsv: thiếu khuôn cho loại {tkind!r} "
                     f"(đang có: {', '.join(sorted(tmpls))})")
        return tmpls[tkind]

    def run(rid, ikind, tkind, label, short, args=None):
        for region, style, text in expand(
            templates_for(tkind), nouns_for(rid, label, short), args or [], units.get(rid, []), a.max_per_id
        ):
            emit(rid, ikind, region, style, text)

    for c in reg["controls"]:
        # Registry sinh bằng máy từ ControlRegistry: thêm một ControlKind mới mà quên bảng này thì
        # trước đây script chết bằng KeyError trần, không nói là kind nào.
        if c["kind"] not in KIND_OF_CONTROL:
            sys.exit(f"ControlKind {c['kind']!r} (nút {c['id']!r}) chưa có trong KIND_OF_CONTROL — "
                     f"thêm vào bảng ở đầu tệp rồi chạy lại")
        tkind, ikind = KIND_OF_CONTROL[c["kind"]]
        run(c["id"], ikind, tkind, c["label"], c.get("short"), c.get("args") or [])
    for t in reg["telemetry"]:
        run(t["id"], "telemetry_read", "READ", t["label"], t.get("short"))
    for m in reg["macros"]:
        run(m["id"], "macro", "MACRO", m["label"], None)
    for l in reg["launcher"]:
        run(l["id"], "launcher", "LAUNCHER", l["label"], None)

    # App: tên đọc theo âm Việt × khuôn mở-app / đưa-vào-ô.
    apps: dict[str, list[tuple[str, str, str]]] = {}
    for key, region, style, name in rows(os.path.join(DATA, "apps.tsv"), 4):
        apps.setdefault(key, []).append((region, style, name))
    # Ghép ĐẦY ĐỦ tên × khuôn: tên app chính là biến số đang hỏng (tester: *"Google Map"* không hiểu), nên
    # đây là chỗ DUY NHẤT cố ý không giới hạn `--max-per-id`.
    for key, names in apps.items():
        for nreg, nsty, name in names:
            for treg, tsty, tmpl in templates_for("APP") + templates_for("SLOT"):
                rid = "open_app_slot" if "ô" in tmpl else "open_app"
                style = nsty if nsty == "tieng_anh_viet" else tsty
                emit(rid, "app", nreg if nreg != "chung" else treg, style, tmpl.replace("{n}", name))

    for rid, ikind, region, style, text in rows(os.path.join(DATA, "extra.tsv"), 5):
        emit(rid, ikind, region, style, text)

    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    with open(a.out, "w", encoding="utf-8") as f:
        f.write("# CORPUS CÂU NÓI — sinh bằng `scripts/voice/gen-variants.py` (đừng sửa tay: sửa data/*.tsv).\n")
        f.write("# Cột: id | intent_kind | region | style | text\n")
        for row in lines:
            f.write("\t".join(row) + "\n")

    per_id: dict[str, int] = {}
    for rid, *_ in lines:
        per_id[rid] = per_id.get(rid, 0) + 1
    thin = sorted(k for k, v in per_id.items() if v < 12)
    print(f"== {len(lines)} câu · {len(per_id)} mã · trung bình {len(lines)/max(1,len(per_id)):.1f} câu/mã")
    print(f"== ghi {a.out}")
    if thin:
        print(f"⚠ {len(thin)} mã có < 12 câu: {', '.join(thin)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
