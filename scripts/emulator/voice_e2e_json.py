#!/usr/bin/env python3
"""Bộ đọc JSON + dựng bảng cho `voice-e2e.sh`.

Vì sao tách khỏi bash: lời đáp của T-BRIDGE là JSON (`TestBridgeReply.finish`) đi qua `am broadcast` dưới dạng
`Broadcast completed: result=0, data="{…}"` — cắt bằng sed/grep là chỗ hỏng im lặng đầu tiên (chuỗi tiếng Việt,
dấu ngoặc lồng, xuống dòng trong `replies`). Ở đây dùng đúng một bộ phân tích JSON.

Lệnh con:
  extract              — đọc stdout của `am broadcast` trên stdin, in JSON thuần.
  get <đường.dẫn>      — in một trường của JSON trên stdin (vd `voice_model.ready`, `test_mode_minutes_left`).
  pick_profile         — in một hồ sơ KHÁC hồ sơ đang dùng (rỗng nếu chỉ có một).
  slot <n>:<pkg>       — in nội dung ô n (chuỗi `SlotCodec.encode`) của JSON `state` trên stdin.
  (bố cục đang dùng đọc bằng `get layout.preset` — xem cột `side` kiểu `preset:<TEN>`)
  report <t1.tsv> <t2.tsv> — in bảng Markdown + tổng kết.
"""
import json
import re
import sys


def _from_am(raw: str):
    """JSON nằm trong `data="…"` của `am broadcast`; lấy từ dấu `{` đầu tới `}` cuối."""
    m = re.search(r'data="(.*)"\s*$', raw, re.S)
    body = m.group(1) if m else raw
    i, j = body.find("{"), body.rfind("}")
    if i < 0 or j < 0:
        return None
    return body[i:j + 1]


def _load(raw: str):
    txt = _from_am(raw)
    if not txt:
        return None
    try:
        return json.loads(txt)
    except json.JSONDecodeError:
        return None


def _dig(obj, path):
    cur = obj
    for part in path.split("."):
        if not isinstance(cur, dict) or part not in cur:
            return None
        cur = cur[part]
    return cur


def _dig2(obj, path):
    """Như [_dig] nhưng thử cả dưới khoá `state` — lời đáp của lệnh `state` bọc mọi thứ trong đó."""
    v = _dig(obj, path)
    return v if v is not None else _dig(obj, "state." + path)


def _kinds(d):
    return [i.get("kind", "?") for i in (d.get("intents") or [])]


def _previews(d):
    return " | ".join(i.get("preview", "") for i in (d.get("intents") or []))


def _verdict(row, d):
    """PASS/FAIL cho một ca T1 + lý do. Chỉ kiểm thứ ca đó KHAI muốn kiểm ("-" = bỏ qua)."""
    why = []
    if d is None:
        return "FAIL", "không đọc được lời đáp (broadcast rỗng/timeout)"
    if not d.get("ok", False):
        return "FAIL", "bridge lỗi: %s" % d.get("error")
    kinds = _kinds(d)
    want_kinds = row["kinds"]
    if want_kinds != "-":
        if kinds != want_kinds.split(","):
            why.append("kind=%s (mong %s)" % (",".join(kinds) or "∅", want_kinds))
    want_sub = row["want"]
    if want_sub != "-" and want_sub.lower() not in _previews(d).lower():
        why.append('preview thiếu "%s"' % want_sub)
    need_confirm = row["conf"] == "1"
    asked = d.get("needs_confirm") or []
    if need_confirm and not asked:
        why.append("KHÔNG hỏi lại (rủi ro CONFIRM bị bỏ qua)")
    if not need_confirm and asked and row["auto"] != "1":
        why.append("hỏi lại ngoài dự kiến: %s" % asked[0][:40])
    side = row["side"]
    real = row["sidereal"]
    if side.startswith("resumed:"):
        pkg = side.split(":", 1)[1]
        if pkg not in (real or ""):
            why.append("app KHÔNG lên màn (%s)" % (real or "-"))
    elif side.startswith("slot:"):
        _, pkg = side[len("slot:"):].split(":", 1)
        if pkg not in (real or ""):
            why.append("ô không mang %s (đang là %s)" % (pkg, real or "∅"))
    elif side.startswith("preset:"):
        want = side.split(":", 1)[1]
        if (real or "").strip() != want:
            why.append("bố cục đang là %s (mong %s)" % (real or "∅", want))
    elif side.startswith("profile:"):
        want = side.split(":", 1)[1]
        if (real or "").strip() != want:
            why.append("hồ sơ đang là %s (mong %s)" % (real, want))
    return ("PASS", "") if not why else ("FAIL", "; ".join(why))


def _md_cell(s, cap=90):
    s = (s or "").replace("\n", " ⏎ ").replace("|", "/").strip()
    return s if len(s) <= cap else s[:cap] + "…"


def report(t1_path, t2_path):
    out = []
    rows = []
    try:
        with open(t1_path, encoding="utf-8") as fh:
            for line in fh:
                p = line.rstrip("\n").split("\t")
                if len(p) < 9:
                    continue
                row = dict(zip(
                    ["id", "lop", "text", "kinds", "want", "conf", "side", "sidereal", "json"], p))
                row["auto"] = "1" if '"auto_confirm":true' in row["json"] else "0"
                d = _load(row["json"])
                v, why = _verdict(row, d)
                rows.append((row, d, v, why))
    except FileNotFoundError:
        pass

    if rows:
        out.append("### T1 — chữ → ý định → thi hành (`say`)\n")
        out.append("| id | lớp | câu | intent | reply | hỏi lại | tác dụng phụ | kết quả |")
        out.append("|---|---|---|---|---|---|---|---|")
        for row, d, v, why in rows:
            kinds = ",".join(_kinds(d)) if d else "-"
            prev = _previews(d) if d else ""
            replies = " ⏎ ".join(d.get("replies") or []) if d else ""
            asked = " ⏎ ".join(d.get("needs_confirm") or []) if d else ""
            out.append("| %s | %s | %s | %s | %s | %s | %s | %s |" % (
                row["id"], row["lop"], _md_cell(row["text"], 46),
                _md_cell("%s · %s" % (kinds, prev), 70),
                _md_cell(replies, 70), _md_cell(asked, 40),
                _md_cell(row["sidereal"], 46),
                v if v == "PASS" else "**FAIL** — " + _md_cell(why, 80)))
        p = sum(1 for _, _, v, _ in rows if v == "PASS")
        out.append("\n**T1: %d/%d PASS** (%d FAIL)\n" % (p, len(rows), len(rows) - p))

    t2 = []
    try:
        with open(t2_path, encoding="utf-8") as fh:
            for line in fh:
                p = line.rstrip("\n").split("\t")
                if len(p) < 3:
                    continue
                t2.append((p[0], p[1], _load(p[2])))
    except FileNotFoundError:
        pass

    if t2:
        out.append("### T2 — tiếng → nhận dạng → ý định (`wav`)\n")
        out.append("| id | câu gốc | heard | ngữ pháp (lượt 1) | tự do (lượt 2) | intent | khớp |")
        out.append("|---|---|---|---|---|---|---|")
        ok = 0
        for cid, text, d in t2:
            if d is None or not d.get("ok"):
                out.append("| %s | %s | — | — | — | — | **LỖI** %s |" % (
                    cid, _md_cell(text, 40), _md_cell((d or {}).get("error", "không đọc được"), 30)))
                continue
            heard = d.get("heard", "")
            same = _norm(heard) == _norm(text)
            kinds = ",".join(_kinds(d)) or "∅"
            prev = _previews(d)
            hit = "✅" if same else ("≈" if _norm(text).split() and
                                    _overlap(_norm(heard), _norm(text)) >= 0.6 else "❌")
            if same:
                ok += 1
            out.append("| %s | %s | %s | %s | %s | %s | %s |" % (
                cid, _md_cell(text, 40), _md_cell(heard, 40),
                _md_cell(d.get("grammar", ""), 34), _md_cell(d.get("free", ""), 24),
                _md_cell("%s · %s" % (kinds, prev), 54), hit))
        out.append("\n**T2: %d/%d nghe ĐÚNG NGUYÊN VĂN** (xem cột intent cho ca nghe lệch mà ý định vẫn đúng)\n" % (ok, len(t2)))
    return "\n".join(out)


def _norm(s):
    import unicodedata
    s = unicodedata.normalize("NFD", (s or "").lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = s.replace("đ", "d")
    return " ".join(re.findall(r"[a-z0-9]+", s))


def _overlap(a, b):
    sa, sb = set(a.split()), set(b.split())
    return len(sa & sb) / max(1, len(sb))


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "report":
        print(report(sys.argv[2], sys.argv[3]))
        return
    raw = sys.stdin.read()
    if cmd == "extract":
        txt = _from_am(raw)
        sys.stdout.write(txt.replace("\n", "").replace("\r", "") if txt else "")
        return
    d = _load(raw)
    if d is None:
        return
    if cmd == "get":
        v = _dig2(d, sys.argv[2])
        sys.stdout.write("" if v is None else str(v))
    elif cmd == "pick_profile":
        pr = _dig2(d, "profile") or {}
        allp = pr.get("all") or []
        act = pr.get("active")
        other = [p for p in allp if p != act]
        sys.stdout.write(other[0] if other else (act or ""))
    elif cmd == "slot":
        n = int(sys.argv[2].split(":", 1)[0])
        slots = _dig2(d, "layout.slots") or []
        for s in slots:
            if s.get("n") == n:
                sys.stdout.write(s.get("content", ""))
                return
        sys.stdout.write("")


if __name__ == "__main__":
    main()
