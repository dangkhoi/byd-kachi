#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Chạy bộ nhận dạng THẬT trên corpus tự dựng ⇒ bảng NGHE NHẦM + đề xuất bí danh/hotword, bằng máy.

Cùng model + cùng tham số với `VoiceRecognizer.kt` (modified_beam_search · beam 4 · score 3.0 · bpe), y như
`hotword-matrix.py` — tệp này chỉ khác ở chỗ nó chạy trên **hàng nghìn** câu của `data/variants.tsv` thay vì 25
câu, và nó **tổng hợp** kết quả thay vì in từng dòng.

## Ba câu hỏi tệp này trả lời (và chỉ ba câu đó)
 1. *"Nói nhanh có hỏng không?"* ⇒ tỉ lệ nghe đúng theo **tốc độ đọc** (5 mức `say` + 3 mức piper + 2 mức
    nén thời gian). Đây là phép đo thay cho giả thuyết *"phải nói chậm"* của tester.
 2. *"Nó nghe thành cái gì?"* ⇒ bảng `ref → hyp hay gặp nhất` kèm số lần. Chỉ lấy chuỗi model **thật sự** in ra,
    không bịa thêm cách viết nào.
 3. *"Lỗi ở TAI hay ở TỪ VỰNG?"* ⇒ với mỗi câu sai, thử **phân tích xấp xỉ** (dưới) xem chuỗi nghe được có
    trỏ về đúng mã không. Nghe sai mà vẫn trỏ đúng ⇒ vô hại. Nghe ĐÚNG mà không trỏ về đâu ⇒ lỗi TỪ VỰNG,
    thêm một cụm vào `VoiceSynonyms` là xong, không cần đụng tới model.

## ⚠ "Phân tích xấp xỉ" là XẤP XỈ, không phải `VoiceIntentParser`
Đây là host, không chạy được Kotlin. Hàm [approx_parse] dựng lại **đúng nguyên tắc** của bộ phân tích thật
(bỏ dấu · khớp dãy từ · dãy dài nhất thắng · động từ READ chọn nhánh ĐỌC) từ chính `registry.json` — nhưng nó
KHÔNG có các nhánh đặc biệt (câu ghép, `appAfterMarker`, sổ địa chỉ, bố cục). Nên kết quả cột "parse" đọc ở
mức [SUY]: nó dùng để **xếp loại lỗi**, không dùng để tuyên bố một câu chạy được trên xe.

⚠ Và như mọi số ở tầng này: giọng TTS ≠ giọng thật + mic 4 kênh trên xe (CLAUDE.md §2).

Dùng:
  /tmp/sherpa-venv/bin/python scripts/voice/mishear-table.py --model <MODEL_DIR> \
      --corpus /tmp/kachi-voice-corpus --wav /tmp/kachi-voice-wav \
      --hotwords none core/build/hotwords/hotwords-phrases.txt \
      --out docs/diagnostics/voice-mishear-2026-09-16.md \
      --aliases scripts/voice/data/aliases-proposed.tsv
"""
from __future__ import annotations

import argparse
import collections
import json
import os
import sys
import time
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))

# Bản sao (chỉ-đọc) của `VoiceGrammar.VERBS` — bỏ dấu, chữ thường, dài trước ngắn. Nguồn:
# core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceGrammar.kt
VERBS: list[tuple[tuple[str, ...], str]] = [
    (("dan", "duong", "den"), "NAV"), (("dan", "duong", "toi"), "NAV"),
    (("chi", "duong", "den"), "NAV"), (("chi", "duong", "toi"), "NAV"),
    (("tim", "duong", "den"), "NAV"), (("dan", "duong"), "NAV"), (("chi", "duong"), "NAV"),
    (("bai", "tiep", "theo"), "NEXT"), (("bai", "ke", "tiep"), "NEXT"), (("chuyen", "bai"), "NEXT"),
    (("bai", "tiep"), "NEXT"), (("tiep", "theo"), "NEXT"), (("bai", "truoc"), "PREV"),
    (("quay", "lai", "bai"), "PREV"), (("tam", "dung"), "PAUSE"), (("doi", "sang"), "SWITCH"),
    (("chuyen", "sang"), "SWITCH"), (("kiem", "tra"), "READ"), (("cho", "xem"), "READ"),
    (("doc", "to"), "READ"), (("turn", "on"), "ON"), (("turn", "off"), "OFF"),
    (("bat",), "ON"), (("on",), "ON"), (("tat",), "OFF"), (("mo",), "OPEN"), (("dua",), "OPEN"),
    (("dong",), "CLOSE"), (("tang",), "UP"), (("giam",), "DOWN"), (("dat",), "SET"), (("chinh",), "SET"),
    (("xem",), "READ"), (("doc",), "READ"), (("hien",), "READ"), (("check",), "READ"),
    (("chuyen",), "SWITCH"), (("doi",), "SWITCH"), (("phat",), "PLAY"), (("nghe",), "PLAY"),
    (("dung",), "PAUSE"), (("tiep",), "NEXT"), (("truoc",), "PREV"),
]
READ_VERBS = {"READ"}

# Bản sao của `VoiceSynonyms.APP_TARGETS` (cùng tệp Kotlin) — chỉ dùng để xếp loại lỗi tên app.
APP_TARGETS = {
    "yt_music": ["youtube music", "yt music", "nhac youtube", "youtube nhac"],
    "youtube": ["youtube", "yt"],
    "spotify": ["spotify"],
    "zing": ["zing mp3", "zing"],
    "gmaps": ["google map", "google maps", "ban do google", "ban do", "google"],
    "waze": ["waze", "quay"],
    "vietmap": ["viet map", "vietmap"],
}

sys.path.insert(0, HERE)
from vi_text import canon_tone, norm, tokens  # noqa: E402


def build_terms(registry: dict) -> list[tuple[tuple[str, ...], str, str]]:
    """(dãy từ bỏ dấu, kind, id) — dài trước ngắn, đúng luật *dãy dài nhất thắng* của `VoiceGrammar`."""
    out: list[tuple[tuple[str, ...], str, str]] = []
    seen: set[tuple] = set()

    def add(phrase, kind, rid):
        w = tuple(tokens(phrase or ""))
        if w and (w, kind, rid) not in seen:
            seen.add((w, kind, rid))
            out.append((w, kind, rid))

    for c in registry["controls"]:
        for p in [c["label"], c.get("labelEn"), c.get("short"), c.get("shortEn")] + (c.get("synonyms") or []):
            add(p, "CONTROL", c["id"])
    for t in registry["telemetry"]:
        for p in [t["label"], t.get("labelEn"), t.get("short"), t.get("shortEn")] + (t.get("synonyms") or []):
            add(p, "TELEMETRY", t["id"])
    for m in registry["macros"]:
        add(m["label"], "MACRO", m["id"])
        add(m.get("labelEn"), "MACRO", m["id"])
    for l in registry["launcher"]:
        add(l["label"], "LAUNCHER", l["id"])
    for key, names in APP_TARGETS.items():
        for n in names:
            add(n, "APP", key)
    # `VoiceSynonyms.MEDIA_WORDS` / `NAV_WORDS` — cụm chỉ LOẠI, ý định do động từ quyết (xem dưới).
    for w in ["bai hat", "bai", "nhac", "ca khuc", "song", "music", "track"]:
        add(w, "MEDIA", "media")
    for w in ["duong den", "duong toi", "destination"]:
        add(w, "NAV", "nav")
    out.sort(key=lambda x: -len(x[0]))
    return out


# Động từ nhạc/dẫn đường quyết định mã ý định chung (bộ `generic` của registry).
MEDIA_BY_VERB = {"PLAY": "media_play", "PAUSE": "media_pause", "NEXT": "media_next", "PREV": "media_prev"}


def approx_parse(text: str, terms) -> tuple[str | None, str | None]:
    """(verb, id) xấp xỉ — xem cảnh báo ở KDoc đầu tệp. `id` là mã registry hoặc khoá app."""
    t = tokens(text)
    verb = None
    vi = -1
    for i in range(len(t)):
        for words, v in VERBS:
            if tuple(t[i:i + len(words)]) == words:
                verb, vi = v, i
                break
        if verb:
            break
    cands = []
    # Cố ý KHÔNG bỏ qua vị trí của động từ: nhãn *"Mở hết kính"* / cách nói *"mở khoá cửa"* bắt đầu BẰNG một
    # động từ, và luật dãy-dài-nhất phải thấy được chúng (đúng như `headMatch` của bộ phân tích thật).
    for i in range(len(t)):
        for words, kind, rid in terms:
            if tuple(t[i:i + len(words)]) == words:
                cands.append((len(words), kind, rid))
                break
    if not cands:
        # Không có đối tượng: động từ NEXT/PREV tự đủ nghĩa (*"bài tiếp theo"* · *"bài trước"*).
        return verb, MEDIA_BY_VERB.get(verb) if verb in ("NEXT", "PREV") else None
    best = max(c[0] for c in cands)
    top = [c for c in cands if c[0] == best]
    want = "TELEMETRY" if verb in READ_VERBS else "CONTROL"
    for n, kind, rid in top:
        if kind == want:
            return verb, rid
    kind, rid = top[0][1], top[0][2]
    if kind == "MEDIA":
        return verb, MEDIA_BY_VERB.get(verb, "media_query")
    if kind == "NAV":
        return verb, "nav"
    if kind == "APP":
        return verb, "open_app"      # mã corpus là `open_app`; khoá app cụ thể không dùng ở tầng này
    return verb, rid


def tone_audit(hotwords_path: str, model_dir: str):
    """Soát CHỖ ĐẶT DẤU của tệp hotword so với `tokens.txt` của mô hình.

    Mọi dòng hotword rốt cuộc được mã hoá bằng BPE của chính mô hình. Một từ viết kiểu đặt dấu mà `tokens.txt`
    KHÔNG có (vd `HOÀ` trong khi mô hình chỉ có `▁HÒA`) vẫn mã hoá được — nhưng bằng các mảnh nhỏ, tức bias
    một đường token **khác** đường mô hình thật sự đi. Đây là phép soát bằng máy cho chuyện đó.
    """
    lines = [l.rstrip("\n") for l in open(hotwords_path, encoding="utf-8") if l.strip()]
    toks = {t.split()[0].lstrip("\u2581") for t in open(os.path.join(model_dir, "tokens.txt"),
                                                        encoding="utf-8") if t.split()}
    changed, pairs = 0, collections.Counter()
    for l in lines:
        c = canon_tone(l)
        if c != l:
            changed += 1
            for a, b in zip(l.split(), c.split()):
                if a != b:
                    pairs[(a, b)] += 1
    rows_ = [[a, b, n, "có" if a in toks else "**không**", "có" if b in toks else "**không**"]
             for (a, b), n in pairs.most_common()]
    return len(lines), changed, rows_


def read_wave(path: str):
    import numpy as np
    with wave.open(path, "rb") as f:
        if f.getframerate() != 16000 or f.getnchannels() != 1 or f.getsampwidth() != 2:
            sys.exit(f"{path}: cần PCM16 · mono · 16 kHz")
        data = f.readframes(f.getnframes())
    return np.frombuffer(data, dtype="<i2").astype("float32") / 32768.0


def make_recognizer(model_dir: str, hotwords_file: str | None, score: float):
    import sherpa_onnx
    kw = dict(
        tokens=os.path.join(model_dir, "tokens.txt"),
        encoder=os.path.join(model_dir, "encoder.onnx"),
        decoder=os.path.join(model_dir, "decoder.onnx"),
        joiner=os.path.join(model_dir, "joiner.onnx"),
        num_threads=2, sample_rate=16000, feature_dim=80,
        decoding_method="modified_beam_search", max_active_paths=4, provider="cpu", debug=False,
    )
    if hotwords_file:
        kw.update(hotwords_file=hotwords_file, hotwords_score=score, modeling_unit="bpe",
                  bpe_vocab=os.path.join(model_dir, "bpe_vocab.txt"))
    return sherpa_onnx.OfflineRecognizer.from_transducer(**kw)


def load_items(corpus: str, wav_dir: str | None):
    items = []
    man = os.path.join(corpus, "manifest.tsv")
    for line in open(man, encoding="utf-8"):
        if line.startswith("#") or "\t" not in line:
            continue
        w, uid, rid, ikind, region, style, voice, rate, tier, ref = line.rstrip("\n").split("\t")
        items.append(dict(path=os.path.join(corpus, w), uid=uid, id=rid, kind=ikind, region=region,
                          style=style, voice=voice, rate=rate, tier=tier, ref=ref, src="corpus"))
    if wav_dir and os.path.isfile(os.path.join(wav_dir, "cases.tsv")):
        for line in open(os.path.join(wav_dir, "cases.tsv"), encoding="utf-8"):
            if "\t" in line:
                wid, ref = line.rstrip("\n").split("\t", 1)
                items.append(dict(path=os.path.join(wav_dir, f"{wid}.wav"), uid=wid, id=f"wav_{wid}",
                                  kind="wav25", region="chung", style="ngan", voice="linh", rate="180",
                                  tier="W", ref=ref, src="wav25"))
    return items


def decode_all(items, model, hotwords, score, label):
    rec = make_recognizer(model, hotwords, score)
    t0 = time.time()
    cache: dict[str, str] = {}
    out = []
    for k, it in enumerate(items):
        p = it["path"]
        if p not in cache:
            st = rec.create_stream()
            st.accept_waveform(16000, read_wave(p))
            rec.decode_stream(st)
            cache[p] = st.result.text.strip()
        out.append(cache[p])
        if k and k % 500 == 0:
            print(f"  [{label}] {k}/{len(items)} … {time.time()-t0:.0f}s", flush=True)
    print(f"  [{label}] xong {len(items)} tệp trong {time.time()-t0:.0f}s", flush=True)
    return out


def pct(ok: int, n: int) -> str:
    return f"{100.0*ok/n:.1f}%" if n else "—"


def table(rows, head):
    out = ["| " + " | ".join(head) + " |", "|" + "|".join(["---"] * len(head)) + "|"]
    out += ["| " + " | ".join(str(c) for c in r) + " |" for r in rows]
    return "\n".join(out)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True)
    ap.add_argument("--corpus", default="/tmp/kachi-voice-corpus")
    ap.add_argument("--wav", default="/tmp/kachi-voice-wav")
    ap.add_argument("--score", type=float, default=3.0)
    ap.add_argument("--registry", default=os.path.join(REPO, "core/build/catalog/registry.json"))
    ap.add_argument("--hotwords", nargs="+", required=True, help="`none` hoặc đường dẫn tệp hotword")
    ap.add_argument("--out", default=os.path.join(REPO, "docs/diagnostics/voice-mishear-2026-09-16.md"))
    ap.add_argument("--aliases", default=os.path.join(HERE, "data/aliases-proposed.tsv"))
    ap.add_argument("--dump", default="", help="ghi kết quả thô (TSV) để chạy lại phân tích không cần giải mã")
    ap.add_argument("--summary-only", action="store_true", help="chỉ in bảng tổng, không ghi doc/alias")
    ap.add_argument("--primary", default="", help="tên tệp hotword dùng cho bảng nghe nhầm + đề xuất bí danh "
                    "(mặc định: cấu hình CUỐI). Nên trỏ vào tệp ĐANG SHIP — bí danh phải tả đúng cái người "
                    "dùng đang gặp, không phải cái ta đang thử.")
    a = ap.parse_args()

    registry = json.load(open(a.registry, encoding="utf-8"))
    terms = build_terms(registry)
    items = load_items(a.corpus, a.wav)
    print(f"== {len(items)} WAV · {len(a.hotwords)} cấu hình hotword")

    hyps: dict[str, list[str]] = {}
    for h in a.hotwords:
        name = "none" if h == "none" else os.path.basename(h)
        hyps[name] = decode_all(items, a.model, None if h == "none" else h, a.score, name)

    names = list(hyps)
    primary = a.primary if a.primary in hyps else names[-1]

    # ── Tổng theo cấu hình ──────────────────────────────────────────────────────────────────────
    overall = []
    for nm in names:
        ok = sum(1 for it, hy in zip(items, hyps[nm]) if norm(hy) == norm(it["ref"]))
        okw = sum(1 for it, hy in zip(items, hyps[nm]) if it["src"] == "wav25" and norm(hy) == norm(it["ref"]))
        nw = sum(1 for it in items if it["src"] == "wav25")
        overall.append([nm, len(items), f"{ok} ({pct(ok, len(items))})", f"{okw}/{nw}"])

    def group(key, nm):
        g = collections.defaultdict(lambda: [0, 0])
        for it, hy in zip(items, hyps[nm]):
            if it["src"] != "corpus":
                continue
            k = key(it)
            g[k][1] += 1
            g[k][0] += norm(hy) == norm(it["ref"])
        return g

    by_rate = {nm: group(lambda it: f"{it['voice']} {it['rate']}", nm) for nm in names}
    by_kind = {nm: group(lambda it: it["kind"], nm) for nm in names}
    by_region = {nm: group(lambda it: it["region"], nm) for nm in names}
    by_style = {nm: group(lambda it: it["style"], nm) for nm in names}
    by_id = group(lambda it: it["id"], primary)

    # ── Bảng nghe nhầm + đề xuất ────────────────────────────────────────────────────────────────
    mis = collections.Counter()
    heard_by_id = collections.defaultdict(collections.Counter)
    for it, hy in zip(items, hyps[primary]):
        r, h = norm(it["ref"]), norm(hy)
        if r != h:
            mis[(r, h)] += 1
        heard_by_id[it["id"]][h] += 1

    alias_rows = []
    for rid, counter in sorted(heard_by_id.items()):
        if rid.startswith("wav_") or rid.startswith("old_") or rid.startswith("unknown"):
            continue
        for heard, cnt in counter.most_common():
            if not heard:
                continue
            _v, pid = approx_parse(heard, terms)
            if pid == rid:
                continue                      # nghe khác chữ nhưng vẫn trỏ đúng mã ⇒ không cần bí danh
            # Câu gốc có trỏ đúng mã không? Nếu KHÔNG thì gốc bệnh là TỪ VỰNG, không phải bí danh.
            action = "hotword" if cnt >= 3 and len(heard.split()) >= 2 else "clarify"
            if pid is None and cnt >= 2:
                action = "synonym"
            alias_rows.append((rid, heard, cnt, action))
    alias_rows.sort(key=lambda r: (-r[2], r[0], r[1]))

    # ── Mã yếu: < 50 % ngay cả ở tốc độ CHẬM (say 140 / piper 0.9) ─────────────────────────────
    slow = collections.defaultdict(lambda: [0, 0])
    for it, hy in zip(items, hyps[primary]):
        if it["src"] != "corpus" or (it["voice"], it["rate"]) not in (("linh", "140"), ("piper", "0.9")):
            continue
        slow[it["id"]][1] += 1
        slow[it["id"]][0] += norm(hy) == norm(it["ref"])
    weak = []
    for rid, (ok, n) in sorted(slow.items()):
        if n and ok / n < 0.5:
            # Phân loại: câu gốc (chữ đúng 100 %) có trỏ về đúng mã không?
            refs = {it["ref"] for it in items if it["id"] == rid}
            parsed = sum(1 for r in refs if approx_parse(r, terms)[1] == rid)
            cls = "TỪ VỰNG" if parsed < len(refs) * 0.5 else "ÂM HỌC"
            weak.append([rid, f"{ok}/{n}", pct(ok, n), cls, f"{parsed}/{len(refs)}"])

    if a.dump:
        with open(a.dump, "w", encoding="utf-8") as f:
            for it, hy in zip(items, hyps[primary]):
                f.write(f"{it['uid']}\t{it['voice']}\t{it['rate']}\t{it['ref']}\t{hy}\n")

    if a.summary_only:
        print(table(overall, ["tệp hotword", "WAV", "đúng nguyên văn", "25 WAV cũ"]))
        return 0

    os.makedirs(os.path.dirname(a.aliases), exist_ok=True)
    with open(a.aliases, "w", encoding="utf-8") as f:
        f.write("# BÍ DANH ĐỀ XUẤT — sinh bằng `scripts/voice/mishear-table.py`, CHỈ từ chuỗi model thật sự in ra.\n")
        f.write("# Cột: id | heard_text | count | proposed_action (synonym|hotword|clarify)\n")
        f.write(f"# Cấu hình chính: {primary} · corpus {a.corpus}\n")
        for r in alias_rows:
            f.write("\t".join(str(x) for x in r) + "\n")

    n_corpus = sum(1 for it in items if it["src"] == "corpus")
    audit_path = next((h for h in a.hotwords if h != "none" and os.path.basename(h) == primary), None)
    audit = tone_audit(audit_path, a.model) if audit_path else None
    doc = [
        "# [ĐO host 2026-09-16] Bảng NGHE NHẦM trên corpus tự dựng — voice 1.66",
        "",
        "> Sinh bằng máy: `scripts/voice/mishear-table.py`. Không sửa tay — chạy lại là ra y hệt.",
        "> Corpus: `scripts/voice/data/variants.tsv` (LLM soạn) → WAV bằng `scripts/voice/synth-corpus.py`.",
        "",
        "## 0. Mức bằng chứng",
        "",
        "| Nhánh | Mức | Vì sao |",
        "|---|---|---|",
        "| `linh` (macOS `say`) | [ĐO host] | giọng tổng hợp, đọc rõ, KHÔNG ồn đường — nói về *model + hotword*, "
        "không nói về xe |",
        "| `piper` (vi_VN-vais1000-medium) | [ĐO host] | giọng thứ hai; câu sai ở CẢ HAI giọng ⇒ lỗi từ vựng, "
        "sai ở một giọng ⇒ lỗi âm học của giọng đó |",
        "| `linh-ola` (nén thời gian 1.3×/1.6×) | [SUY] | âm tổng hợp từ âm tổng hợp; mô phỏng *nhịp* nói nhanh, "
        "KHÔNG mô phỏng nuốt phụ âm |",
        "| cột `parse` | [SUY] | `approx_parse` dựng lại nguyên tắc của `VoiceIntentParser`, KHÔNG phải chính nó |",
        "",
        f"Quy mô: **{n_corpus} WAV corpus** + 25 WAV của ma trận cũ · {len(names)} cấu hình hotword · "
        f"score {a.score}.",
        "",
        "## 1. Tổng theo tệp hotword",
        "",
        table(overall, ["tệp hotword", "WAV", "đúng nguyên văn", "25 WAV cũ"]),
        "",
        "## 2. Nói NHANH có hỏng không — tỉ lệ đúng theo giọng × tốc độ",
        "",
        "`say -r N` = N từ/phút (180 là mặc định macOS). `piper speed` > 1 là nhanh hơn.",
        "",
        table(
            [[k] + [pct(by_rate[nm][k][0], by_rate[nm][k][1]) for nm in names] + [by_rate[primary][k][1]]
             for k in sorted(by_rate[primary], key=lambda s: (s.split()[0], float(s.split()[1])))],
            ["giọng · tốc độ"] + names + ["số WAV"]),
        "",
        "## 3. Theo LOẠI ý định",
        "",
        table([[k] + [pct(by_kind[nm][k][0], by_kind[nm][k][1]) for nm in names] + [by_kind[primary][k][1]]
               for k in sorted(by_kind[primary])], ["intent_kind"] + names + ["số WAV"]),
        "",
        "## 4. Theo VÙNG MIỀN và KIỂU NÓI",
        "",
        table([[k] + [pct(by_region[nm][k][0], by_region[nm][k][1]) for nm in names] + [by_region[primary][k][1]]
               for k in sorted(by_region[primary])], ["region"] + names + ["số WAV"]),
        "",
        table([[k] + [pct(by_style[nm][k][0], by_style[nm][k][1]) for nm in names] + [by_style[primary][k][1]]
               for k in sorted(by_style[primary])], ["style"] + names + ["số WAV"]),
        "",
        "## 5. 30 cặp NGHE NHẦM hay gặp nhất",
        "",
        f"Cấu hình `{primary}`. `ref` là câu đưa cho TTS (đã đọc chữ số thành chữ), `hyp` là chuỗi model in ra.",
        "",
        table([[r, h, c] for (r, h), c in mis.most_common(30)], ["ref", "hyp (model nghe ra)", "lần"]),
        "",
        "## 6. Mã yếu — dưới 50 % ngay cả ở tốc độ CHẬM",
        "",
        "`loại` = TỪ VỰNG khi chính câu gốc (chữ đúng 100 %) cũng không trỏ về đúng mã qua `approx_parse` ⇒ sửa "
        "bằng `VoiceSynonyms`, không cần đụng model. ÂM HỌC khi câu gốc trỏ đúng mà model nghe ra chữ khác.",
        "",
        table(weak, ["id", "đúng/tổng (chậm)", "%", "loại", "câu gốc parse đúng"]) if weak
        else "_Không mã nào dưới 50 % ở tốc độ chậm._",
        "",
        "## 7. Đề xuất bí danh",
        "",
        f"`scripts/voice/data/aliases-proposed.tsv` — **{len(alias_rows)} dòng**, chỉ từ chuỗi model thật sự in "
        "ra (không bịa cách viết). `synonym` = thêm dạng bỏ dấu vào `VoiceSynonyms`; `hotword` = thêm cụm HOA "
        "có dấu vào tệp hotword; `clarify` = quá hiếm để mã hoá, để `VoiceClarify` hỏi lại.",
        "",
        table([[r[0], r[1], r[2], r[3]] for r in alias_rows[:30]],
              ["id", "heard_text", "lần", "đề xuất"]) if alias_rows else "_Không có._",
        "",
        "## 8. Soát CHỖ ĐẶT DẤU của tệp hotword so với `tokens.txt`",
        "",
        "*«hoà»* và *«hòa»* là cùng một chữ, khác chỗ đặt dấu thanh. Dự án viết kiểu CŨ; từ điển của mô "
        "hình chỉ có kiểu MỚI. Dòng hotword viết sai kiểu vẫn **mã hoá được** bằng mảnh BPE, nhưng nó bias "
        "một đường token khác đường mô hình thật sự đi — im lặng, không báo lỗi gì.",
        "",
    ]
    if audit:
        n_lines, n_changed, audit_rows = audit
        doc += [
            f"Tệp soát: `{primary}` — **{n_changed}/{n_lines} dòng** viết kiểu cũ.",
            "",
            table(audit_rows, ["viết trong tệp", "kiểu mô hình dùng", "số dòng",
                               "có trong tokens.txt?", "dạng mới có?"]) if audit_rows
            else "_Không dòng nào lệch._",
            "",
            "⚠ [ĐO] Sửa chỗ đặt dấu **một mình** KHÔNG đổi tổng số (xem §1: `tonefix` = `phrases`); nó chỉ "
            "nhích ở nhánh `piper 0.9`. Tức giả thuyết *\"sai chỗ đặt dấu ⇒ hotword vô hiệu\"* **bị bác** — "
            "BPE vẫn kéo được. Vẫn nên sửa vì đó là chữ viết đúng chuẩn của từ điển mô hình, nhưng KHÔNG "
            "được bán nó như một bản vá cải thiện độ chính xác.",
            "",
        ]
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    open(a.out, "w", encoding="utf-8").write("\n".join(doc) + "\n")
    print(f"== ghi {a.out}\n== ghi {a.aliases} ({len(alias_rows)} dòng)")
    print(table(overall, ["tệp hotword", "WAV", "đúng nguyên văn", "25 WAV cũ"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
