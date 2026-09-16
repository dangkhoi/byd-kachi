#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sinh `scripts/voice/data/misspell.tsv` — bộ câu **SAI CHÍNH TẢ** để đo tầng chịu lỗi (H7).

## Vì sao có tệp này (owner 2026-09-16)
*"cần phải xử lý sai chính tả kiểu này nữa nhé, làm 1 bộ data sai chính tả luôn, anh em nói đâu phải lúc nào
cũng đúng, cốp với cấp khác gì nhau đâu"*.

Ba **nguồn**, và cột `source` giữ chúng tách bạch mãi mãi — trộn chúng lại là mất luôn khả năng phân biệt phép
đo với phép sinh (CLAUDE.md §2):

 • `real` — chuỗi mô hình **thật sự in ra khi người thật nói**, 5 bản thu 2026-09-16
   (`docs/diagnostics/voice-rec-2026-09-16/segments-*-shipping.json`). Đây là bằng chứng mạnh nhất của dự án.
   Cặp (câu-nói-thật → chuỗi-nghe-được) do NGƯỜI ghép, vì tệp JSON chỉ có chuỗi giải mã chứ không có câu gốc;
   máy thì **kiểm lại** rằng mỗi `heard_text` có thật trong tệp JSON (xem [verify_real]) ⇒ không ai chép sai
   được mà không ai biết.
 • `tts` — corpus tổng hợp trên host: `scripts/voice/data/aliases-proposed.tsv` +
   `docs/diagnostics/voice-mishear-2026-09-16.md` §5.
 • `rule` — **sinh bằng máy** từ chính bảng lẫn âm của `:core`
   (`core/src/main/kotlin/.../voice/VoicePhoneticConfusions.kt`, đọc trực tiếp bằng regex ⇒ một nguồn duy nhất,
   không chép tay bảng thứ hai), áp lên mọi nhãn của `core/build/catalog/registry.json`. Có trần **8 dòng mỗi
   nhãn** và thứ tự tất định ⇒ chạy lại cho ra y hệt.

## Dùng
    python3 scripts/voice/gen-misspell.py                    # ghi đè data/misspell.tsv
    python3 scripts/voice/gen-misspell.py --check            # chỉ kiểm, không ghi (dùng cho CI)

Cần `core/build/catalog/registry.json` (sinh bởi `:core:test` — `FeatureCatalogDumpTest`).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(HERE, "data")
KT = os.path.join(REPO, "core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoicePhoneticConfusions.kt")
REGISTRY = os.path.join(REPO, "core/build/catalog/registry.json")
RECDIR = os.path.join(REPO, "docs/diagnostics/voice-rec-2026-09-16")
OUT = os.path.join(DATA, "misspell.tsv")

MAX_PER_LABEL = 8

HEADER = """# CÂU SAI CHÍNH TẢ — sinh bằng `scripts/voice/gen-misspell.py` (đừng sửa tay phần `rule`).
# Cột: id | heard_text | source
#  • source = real : chuỗi mô hình in ra khi NGƯỜI THẬT nói (5 bản thu 2026-09-16) — bằng chứng mạnh nhất
#  • source = tts  : corpus tổng hợp trên host (aliases-proposed.tsv · bảng nghe nhầm §5)
#  • source = rule : máy sinh từ bảng lẫn âm VoicePhoneticConfusions.kt (≤ %d dòng/nhãn, tất định)
# `id` là mã control/telemetry/macro mà câu PHẢI trỏ tới; `-` = câu cố ý KHÔNG phải lệnh.
""" % MAX_PER_LABEL


# ── chuẩn hoá, giống hệt VoiceLexicon.deaccent ───────────────────────────────────────────────────
def deaccent(s: str) -> str:
    out = []
    for ch in unicodedata.normalize("NFD", s.lower()):
        if ch == "đ":
            out.append("d")
        elif 0x0300 <= ord(ch) <= 0x036F:
            continue
        else:
            out.append(ch)
    return "".join(out)


def toks(text: str) -> list[str]:
    return [t for t in "".join(c if (c.isalnum() or c == "%") else " " for c in text).split() if t]


# ── (1) đọc BẢNG LẪN ÂM thẳng từ Kotlin — một nguồn duy nhất ─────────────────────────────────────
def read_confusions() -> tuple[list[tuple[str, str]], list[tuple[str, str]]]:
    """Trả (cặp âm tiết đã quan sát, cặp thành phần theo quy luật) — đều ở dạng ĐÃ BỎ DẤU."""
    src = open(KT, encoding="utf-8").read()
    observed = [(a, b) for a, b in re.findall(r'Observed\("([^"]+)",\s*"([^"]+)"', src)]
    groups: list[list[str]] = []
    for line in src.splitlines():
        if line.lstrip().startswith("//"):
            continue
        for m in re.finditer(r'chain\(((?:"[^"]+",?\s*)+)\)', line):
            groups.append(re.findall(r'"([^"]+)"', m.group(1)))
        for m in re.finditer(r'listOf\(((?:"[^"]+"\s*to\s*"[^"]+",?\s*)+)\)', line):
            for a, b in re.findall(r'"([^"]+)"\s*to\s*"([^"]+)"', m.group(1)):
                groups.append([a, b])
    parts: list[tuple[str, str]] = []
    for g in groups:
        for i in range(len(g)):
            for j in range(len(g)):
                if i != j and g[i] != g[j]:
                    parts.append((deaccent(g[i]), deaccent(g[j])))
    obs = []
    for a, b in observed:
        da, db = deaccent(a), deaccent(b)
        if da != db:
            obs += [(da, db), (db, da)]
    # ⚠ Cùng luật loại với Kotlin: cặp NGUYÊN ÂM đơn-một-chữ-cái bị bỏ (xem KDoc [VOWELS] — sau khi bỏ dấu,
    # `ô`↔`â` thành `o`↔`a`, tức hai nguyên âm khác hẳn). Không lọc ở đây thì tệp sinh ra chứa những cách viết
    # sai mà chính bộ so khớp CỐ Ý từ chối ⇒ con số đo được không còn nói về cùng một thứ.
    vowels = set("aeiouy")
    def kept(a: str, b: str) -> bool:
        if a == b:
            return False
        return not (len(a) == 1 and len(b) == 1 and a in vowels and b in vowels)
    return obs, [(a, b) for a, b in parts if kept(a, b)]


ONSETS = ["ngh", "ng", "nh", "ch", "gh", "gi", "kh", "ph", "qu", "th", "tr",
          "b", "c", "d", "g", "h", "k", "l", "m", "n", "p", "q", "r", "s", "t", "v", "x"]
CODAS = ["nh", "ng", "ch", "c", "m", "n", "p", "t"]


def split_syl(b: str) -> tuple[str, str, str]:
    on = next((o for o in ONSETS if b.startswith(o) and len(b) > len(o)), "")
    rest = b[len(on):]
    co = next((c for c in CODAS if rest.endswith(c) and len(rest) > len(c)), "")
    return on, rest[:len(rest) - len(co)] if co else rest, co


def variants_of(syl: str, obs, parts) -> list[str]:
    """Mọi cách viết SAI của một âm tiết, theo đúng bảng — tất định (đã sắp xếp)."""
    out = {b for a, b in obs if a == syl}
    on, nu, co = split_syl(syl)
    for a, b in parts:
        if on and on == a:
            out.add(b + nu + co)
        if nu == a:
            out.add(on + b + co)
        if co and co == a:
            out.add(on + nu + b)
    out.discard(syl)
    return sorted(out)


# ── (2) nhãn từ registry ─────────────────────────────────────────────────────────────────────────
def labels() -> list[tuple[str, str]]:
    d = json.load(open(REGISTRY, encoding="utf-8"))
    out = []
    for key in ("controls", "telemetry", "macros"):
        for row in d[key]:
            out.append((row["id"], row["label"]))
    return out


# Động từ ghép vào trước nhãn, theo LOẠI nút — để dòng sinh ra là một câu lệnh hợp lệ khi chữ đúng.
VERB_FOR_KIND = {"TOGGLE": "bật", "COVER": "mở", "BUTTON": "bật", "STEP": "tăng", "SELECT": "bật"}


def gen_rule(obs, parts) -> list[tuple[str, str, str]]:
    """≤ MAX_PER_LABEL dòng mỗi nhãn, **luân phiên theo vị trí** để không dồn hết vào chữ đầu."""
    d = json.load(open(REGISTRY, encoding="utf-8"))
    rows = []
    for key in ("controls", "telemetry", "macros"):
        for row in d[key]:
            words = [w.lower() for w in toks(row["label"])]
            if not words:
                continue
            verb = "xem" if key == "telemetry" else VERB_FOR_KIND.get(row.get("kind"), "bật")
            # (hạng của biến thể, vị trí, chữ sai) — sắp theo hạng trước ⇒ mỗi chữ được một lượt rồi mới vòng hai.
            made = []
            for i, w in enumerate(words):
                for rank, v in enumerate(variants_of(deaccent(w), obs, parts)):
                    made.append((rank, i, " ".join(words[:i] + [v] + words[i + 1:])))
            made.sort()
            for _rank, _i, text in made[:MAX_PER_LABEL]:
                rows.append((row["id"], f"{verb} {text}", "rule"))
    return rows


# ── (3) cặp THẬT — người ghép, máy kiểm ──────────────────────────────────────────────────────────
# (mã · chuỗi mô hình in ra · tệp bản thu · số hiệu đoạn) — câu gốc nằm ở kịch bản 30 câu của buổi thu.
REAL = [
    ("trunk", "mở cấp sau", "miennam-a-cham", 39),
    ("trunk", "mở cấp sau", "miennam-b-nhanh", 26),
    ("trunk", "ở cấp sau", "miennam-c-nhac", 31),
    ("trunk", "mở góc sau", "owner", 59),
    ("soc", "xem bên", "miennam-c-nhac", 24),
    ("soc", "biên còn bao nhiêu", "owner", 39),
    ("soc", "biên còn bao nhiêu", "owner", 57),
    ("soc", "bin còn bao nhiêu", "be", 14),
    ("soc", "xem phim", "owner", 38),
    ("seath", "bằng ghế sưởi", "owner", 58),
    ("temp", "mật độ hai mươi hai độ", "owner", 51),
    ("win_lf", "hạ kim trước trái", "be", 51),
    ("win_lf", "hạ kiêng trái", "be", 33),
    ("tyre_p_fl", "xe áp suất lốp trước trái", "be", 36),
    ("tyre_p_fl", "sẽ áp suất lốp trước trái", "owner", 40),
    # ⚠ (V) FEATURE-FILTER 2026-09-17: hai dòng ĐÃ ĐO của `drive_mode` ("chế độ đá/gái thể thao" — be #38,
    # owner #45) gỡ khỏi bảng vì nút `drive_mode` đã bị owner chấm NO và xoá khỏi registry. Phép đo vẫn còn trong
    # `docs/diagnostics/voice-rec-2026-09-16/` (luật dự án: không xoá lịch sử đo), chỉ không sinh câu sai nữa.
    ("media_pause", "dần nhạc", "miennam-c-nhac", 34),
    ("ac_auto", "mã máy lạnh", "owner", 50),
    ("vol", "mầm lượng", "miennam-b-nhanh", 16),
    ("readl", "bật đèn độc", "miennam-a-cham", 10),
    ("readl", "bật đèn độc", "miennam-c-nhac", 9),
    ("mac_win_close_all", "đóng máy kính", "owner", 37),
    ("mac_win_open_all", "mở máy kính giùm", "owner", 37),
    ("waze", "mở quây", "miennam-a-cham", 50),
    ("waze", "mở quạ", "be", 38),
]

TTS = [
    ("pm25", "các bụi"),
    ("pm25_clean_now", "đọc ngay"),
    ("soc", "tin còn bao nhiêu"),
    ("soc", "xem tin"),
    ("win_lf", "mở kín trước trái"),
    ("media_pause", "rừng nhạc"),
    ("seath", "bật ghế sửi"),
    ("seath", "bật ghế sữa"),
    ("readl", "bật đèn đang đọc"),
    ("gmaps", "mở gu gồ máp"),
    ("youtube", "mở du túp"),
    ("-", "hôm nay trời đẹp quá"),
    ("-", "kể cho tôi nghe một câu chuyện"),
    ("-", "bật abcxyz"),
    ("-", "bật cái đó"),
    ("-", "về chỗ nào đó"),
    ("-", "tắt hết đèn"),
    ("-", "xem xe"),
    ("-", "mở cửa"),
    ("-", "đọc ngày"),
]


def verify_real() -> list[str]:
    """Mỗi `heard_text` của nguồn `real` phải có THẬT trong tệp giải mã — không thì báo đỏ."""
    bad = []
    cache: dict[str, str] = {}
    for _id, heard, tag, seg in REAL:
        name = "segments-shipping.json" if tag == "owner" else f"segments-{tag}-shipping.json"
        path = os.path.join(RECDIR, name)
        if path not in cache:
            if not os.path.exists(path):
                bad.append(f"thiếu tệp bản thu: {path}")
                cache[path] = ""
                continue
            cache[path] = " ".join(deaccent(s["text"]) for s in json.load(open(path, encoding="utf-8")))
        if cache[path] and deaccent(heard) not in cache[path]:
            bad.append(f"«{heard}» không có trong {os.path.basename(path)} (đoạn {seg})")
    return bad


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="chỉ kiểm, không ghi")
    args = ap.parse_args()

    bad = verify_real()
    if bad:
        print("SAI — nguồn `real` không khớp bản thu:", *bad, sep="\n  ", file=sys.stderr)
        return 1

    obs, parts = read_confusions()
    rows = [(i, h, "real") for i, h, _t, _s in REAL]
    rows += [(i, h, "tts") for i, h in TTS]
    rows += gen_rule(obs, parts)

    seen = set()
    uniq = []
    for r in rows:
        if (r[0], r[1]) in seen:
            continue
        seen.add((r[0], r[1]))
        uniq.append(r)

    body = HEADER + "".join(f"{i}\t{h}\t{s}\n" for i, h, s in uniq)
    n = {s: sum(1 for r in uniq if r[2] == s) for s in ("real", "tts", "rule")}
    print(f"cặp âm tiết đã quan sát: {len(obs)} · cặp thành phần theo quy luật: {len(parts)}")
    print(f"dòng: real={n['real']} · tts={n['tts']} · rule={n['rule']} · tổng={len(uniq)}")
    if args.check:
        cur = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
        if cur != body:
            print(f"SAI — {OUT} lệch với bản sinh lại", file=sys.stderr)
            return 1
        print("OK — tệp khớp bản sinh lại")
        return 0
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(body)
    print(f"đã ghi {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
