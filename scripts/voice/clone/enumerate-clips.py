# -*- coding: utf-8 -*-
"""
═══ LIỆT KÊ TẬP CLIP CHO GÓI PHÁT ÂM SẴN "Giọng Kachi bé" ═════════════════════════════════════════════
Spec: docs/specs/kachi-voice-clone.html — R2 (liệt kê BẰNG MÁY, không chép tay dòng nào), §4.3 ba tầng.

Chạy:  python3 scripts/voice/clone/enumerate-clips.py [--out clips.tsv]
Đọc :  core/build/catalog/registry.json  (sinh bởi FeatureCatalogDumpTest — chạy lại test đó trước khi
       chạy script này nếu vừa đổi chuỗi trả lời trong Kotlin; file này KHÔNG tự đoán chuỗi mới)
Ghi :  clips.tsv — 4 cột, có dòng tiêu đề:
         id    text                tier                    say
         ^khoá ^chuỗi TRA BẢNG     ^fixed|head|unit|num    ^chữ đem đi TỔNG HỢP

## Vì sao `text` khác `say`
`text` là **khoá tra bảng** — đúng từng ký tự chuỗi mà `VoiceFeedbackPhrase.merge` sinh ra (hoặc đúng
mảnh của nó). Có khoá không đọc lên được nguyên văn: đơn vị `%` phải đọc là *"phần trăm"*, `°C` là
*"độ C"*. `say` là chữ đem cho bộ tổng hợp. Hai cột, vì trộn chúng là mất một trong hai sự thật.

## Ba tầng (§4.3) — vì sao không phải một tầng
Câu KHÔNG có số ⇒ một clip trọn vẹn (`fixed`). Câu CÓ số ⇒ ba mảnh: `head` (phần trước số) + `num`
(số 0–999 đọc thành chữ) + `unit` (phần sau số: đơn vị, hoặc cái đuôi *", chưa kiểm trên xe"*).
Liệt kê nguyên câu cho mọi giá trị số thì tầng thông tin nổ tung (112 nhãn × 0–999).

## Chỗ CỐ Ý không phủ — rơi xuống Piper (R4), không im lặng
  • câu ghép nhiều vế (*"Đã bật đèn đọc, mở kính lái"*) — tổ hợp, không đếm được;
  • câu chứa **từ vựng mở** do người dùng nói ra (tên bài hát, điểm đến, tên app chưa có trong bảng);
  • câu hỏi lại của `VoiceClarify.ambiguity` — sinh từ `VoiceGrammar`/`VoiceLexicon` (Kotlin), bản Python
    này KHÔNG dựng lại được mà không đoán ⇒ để trống có chủ ý, T3 (`VoiceClipInventory.kt`) đóng nốt;
  • giá trị số không nguyên (*"3,5 kWh"*) và số ≥ 1000.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(REPO, "scripts", "voice"))
from vi_text import vi_number  # noqa: E402  (đường dẫn phải cắm trước)
sys.path.insert(0, HERE)
import tts_pronunciation as TTS  # noqa: E402

REGISTRY = os.path.join(REPO, "core", "build", "catalog", "registry.json")

# ── Cổng ra tiếng: bản Python của `VoiceFeedbackPhrase` (core/…/voice/VoiceFeedbackPhrase.kt) ─────────
# Giữ ĐÚNG từng phép thay. Lệch một ký tự ở đây là cả gói không bao giờ tra trúng.
OK_MARK, FAIL_MARK, INTERIM = "✓", "✗", "…"
MAX_WORDS = 12


def kind_of(line: str) -> str:
    s = line.strip()
    if not s:
        return "INTERIM"
    if s.endswith(INTERIM):
        return "INTERIM"
    if s.startswith(OK_MARK):
        return "OK"
    if s.startswith(FAIL_MARK):
        return "FAIL"
    return "PLAIN"


def body(line: str) -> str:
    s = line.strip()
    for mark in (OK_MARK, FAIL_MARK):      # `removePrefix(✓).removePrefix(✗)` — nối tiếp, không rẽ nhánh
        if s.startswith(mark):
            s = s[len(mark):]
    s = (s.replace("«", "").replace("»", "").replace(" = ", " ").replace(" — ", ", ").replace("\n", " "))
    return s.strip().strip(",").strip()


def decap(s: str) -> str:
    return s[:1].lower() + s[1:] if s else s


def words(s: str) -> int:
    return len([w for w in re.split(r"[ \n\t]", s) if w.strip()])


def clamp_words(s: str, n: int) -> str:
    parts = [p for p in s.split(" ") if p.strip()]
    if len(parts) <= n:
        return s
    return " ".join(parts[:n]).rstrip(",") + INTERIM


def merge_one(line: str) -> str | None:
    """`VoiceFeedbackPhrase.merge` cho **một** dòng — ca duy nhất gói này phủ."""
    k = kind_of(line)
    if k == "INTERIM":
        return None
    if k == "OK":
        s = "Đã " + decap(body(line))
        return "Đã xong 1 việc" if words(s) > MAX_WORDS else s
    head = ("Chưa " + decap(body(line))) if k == "FAIL" else body(line)
    return clamp_words(head, MAX_WORDS) if words(head) > MAX_WORDS else head


PLACEHOLDER = "\x00"  # ký tự không bao giờ có trong chuỗi thật ⇒ dùng làm chỗ cắm số


def clip_id(tier: str, text: str) -> str:
    """Mã clip = **băm của chính chuỗi**, không phải số thứ tự.

    ## Vì sao không đánh số 0001, 0002…
    [ĐO 2026-09-17] registry mọc thêm 6 dòng thông tin giữa lúc đang dựng gói ⇒ mọi số thứ tự sau chỗ
    chèn **dịch một bậc**. Với một lượt sinh dài mấy giờ có `progress.tsv` để nối lại, số dịch nghĩa là
    tệp WAV của câu A bị nhận là của câu B — hỏng **lặng lẽ**, đúng thứ không ai phát hiện ra cho tới
    khi nghe thấy xe đọc nhầm câu. Băm theo nội dung thì câu không đổi giữ nguyên mã: registry mọc thêm
    ⇒ chỉ những clip THẬT SỰ mới phải sinh.
    """
    if tier == "num":
        return "num/%s" % text
    h = hashlib.sha256(("%s\x1f%s" % (tier, text)).encode("utf-8")).hexdigest()[:10]
    return "%s/%s" % (tier, h)

# ── Đơn vị `TtsPronunciation` CHƯA phủ ───────────────────────────────────────────────────────────────
# [ĐO 2026-09-17] quét 17 đơn vị thật của `TelemetryRegistry` qua `TtsPronunciation.normalise`: 16 đơn vị
# ra chữ đọc được, **`°` thì không** — nó đi qua nguyên xi (bảng có `°c` và `độ c`, không có `°` trơn), và
# 5 dòng thông tin đang dùng đúng đơn vị ấy (góc lái/hướng). Máy đọc gặp một ký hiệu trần thì hoặc nuốt,
# hoặc đánh vần. Vá tạm ở đây cho gói clip, và ghi vào §9 của spec: chỗ sửa THẬT là thêm một dòng
# `"°" to "độ"` vào `TtsPronunciation.PHRASES` — lúc đó xoá bảng này đi.
UNIT_GAP = {"°": "độ"}


def speakable(text: str) -> str:
    """Chữ TRA BẢNG → chữ ĐỌC LÊN, qua **đúng** cổng mà cửa ra tiếng dùng.

    ## Vì sao gọi `TtsPronunciation.normalise` chứ không tự nghĩ một phép đổi
    `TtsPronunciation.kt` (KDoc, mục 3) nói thẳng rằng bảng clip của gói clone khoá theo **chuỗi gốc**, còn
    phép phiên âm là việc của **cửa ra tiếng**. Nếu gói tự nghĩ một cách đọc khác thì cùng một câu sẽ nghe
    khác nhau giữa giọng bé và đường lùi Piper — và chỗ lệch ấy không bài canh nào bắt được. Bảng bên đó
    còn là **kết quả đo** (n=5 qua vòng Piper→ASR: *"e vê"* 5/5 · *"e-vê"* 0/5), không phải sở thích.

    Hai phép **thêm**, cả hai đều thuộc về việc sinh clip chứ không thuộc bảng phiên âm:
     • [UNIT_GAP] — vá một đơn vị bảng chưa phủ (xem chú thích trên);
     • bỏ dấu hai chấm/phẩy **ở cuối** mảnh. Đầu câu của một khung có số là *"Pin (SOC):"*; dấu hai chấm ở
       đó là chỗ ngắt hơi, và [ĐO host 2026-09-16] đưa nguyên nó cho F5 (*"pin:."*) cho ra 0,58 s tiếng mà
       ASR đọc lại thành *"À"*. Khoá tra bảng vẫn giữ nguyên dấu — chỉ chữ đem đi tổng hợp là bỏ.
    """
    s = TTS.normalise(UNIT_GAP.get(text.strip(), text))
    return re.sub(r"\s+", " ", s).strip().rstrip(":,").strip()


class Clips:
    """Bộ gom clip — khử trùng lặp theo (tier, text); giữ `say` đầu tiên thấy được."""

    def __init__(self) -> None:
        self.rows: dict[tuple[str, str], str] = {}
        self.sources: dict[tuple[str, str], str] = {}

    def add(self, tier: str, text: str, say: str | None = None, src: str = "") -> None:
        text = text.strip() if tier in ("fixed", "num") else text
        if not text:
            return
        say = speakable(say if say is not None else text).strip().strip(INTERIM).strip()
        if not say:
            return
        key = (tier, text)
        if key not in self.rows:
            self.rows[key] = say
            self.sources[key] = src

    def add_sentence(self, spoken: str | None, src: str) -> None:
        """Một câu đã qua `merge`. Có [PLACEHOLDER] ⇒ cắt thành head/num/unit, không thì là một clip trọn."""
        if not spoken:
            return
        if PLACEHOLDER not in spoken:
            self.add("fixed", spoken, src=src)
            return
        parts = spoken.split(PLACEHOLDER)
        head, tails = parts[0], parts[1:]
        self.add("head", head.rstrip(), src=src)
        for t in tails:
            if not t.strip():
                continue
            self.add("unit", t, say=unit_say(t), src=src)


def unit_say(tail: str) -> str:
    """Chữ đọc cho một mảnh ĐUÔI — dấu phẩy mở đầu là chỗ ngắt, không phải một tiếng."""
    return tail.strip().lstrip(",").strip()


def enumerate_clips(reg: dict, whole_ranges: bool = False) -> Clips:
    c = Clips()

    # ── (A) 54 nút điều khiển ────────────────────────────────────────────────────────────────────────
    for it in reg["controls"]:
        step_val = str(it["value"]) if it["kind"] == "STEP" else None
        for field in ("replyPreview", "replyDone", "replyFailed", "confirmQuestion"):
            raw = it.get(field)
            if not raw:
                continue
            # Nút STEP: con số trong câu là **giá trị mặc định** của nút, phải thành chỗ cắm.
            # Thay đúng dạng " = 22" / " 22" một lần — KHÔNG regex \d+ toàn câu: nhãn có thể chứa số
            # ("Camera 360", "Ắc-quy 12V") và thay nhầm ở đó là hỏng một clip cố định.
            if step_val:
                raw = raw.replace(" = " + step_val, " = " + PLACEHOLDER, 1)
            c.add_sentence(merge_one(raw), f"control/{it['id']}/{field}")

        # Nút STEP nói theo **nấc tương đối** (VoiceReply.controlPreview, nhánh i.relative != 0)
        if it["kind"] == "STEP":
            name = it["label"]
            for verb in ("Tăng", "Giảm"):
                base = f"{verb} {name} {PLACEHOLDER} nấc"
                c.add_sentence(merge_one("✓ " + base + tail_unverified(it)), f"control/{it['id']}/rel_done")
                c.add_sentence(merge_one("✗ " + base + " — xe không nhận lệnh"), f"control/{it['id']}/rel_failed")
            # VoiceReply.doneActual — xe báo một con số KHÁC con số đã gửi (HAI chỗ cắm trong một câu).
            # ⚠ [ĐO] chuỗi thật mở đầu bằng "✓ Đã gửi…", mà `merge` lại gắn thêm "Đã " ⇒ câu đọc ra là
            # *"Đã đã gửi…"*. Ở đây liệt kê ĐÚNG thứ merge sinh ra (gói phải tra trúng), không sửa hộ —
            # chỗ sửa là `VoiceReply.doneActual`, ghi vào §9 của spec.
            two = f"✓ Đã gửi {name} {PLACEHOLDER} — xe báo {PLACEHOLDER}" + tail_unverified(it)
            c.add_sentence(merge_one(two), f"control/{it['id']}/actual")

    # ── (B) dòng thông tin (112 tại 2026-09-17) ───────────────────────────────────────────────────────────────────────
    for it in reg["telemetry"]:
        for field in ("replyPreview", "replyNoReading"):
            if it.get(field):
                c.add_sentence(merge_one(it[field]), f"telemetry/{it['id']}/{field}")
        # Câu ĐỌC SỐ THẬT — `VoiceDispatcher.runRead`: "<nhãn>: <số> <đơn vị>" (đơn vị rỗng ⇒ chỉ số).
        unit = it.get("unit") or ""
        frame = f"{it['label']}: {PLACEHOLDER}" + (f" {unit}" if unit else "")
        c.add_sentence(merge_one(frame), f"telemetry/{it['id']}/reading")

    # ── (C) gói lệnh · (D) hành động launcher · (E) câu chung ─────────────────────────────────────────
    for bucket in ("macros", "launcher", "generic"):
        for it in reg[bucket]:
            for field in ("replyPreview", "replyDone", "replyFailed", "confirmQuestion"):
                if not it.get(field):
                    continue
                # ⚠ Dòng `unknown_*` của bảng danh mục là **hiện vật của bảng**, không phải câu chạy thật:
                # `FeatureCatalogDumpTest` bơm một `VoiceIntent.Unknown` qua `failed()`/`preview()` cho đủ
                # cột, sinh ra những câu không đường nào phát ra được (*"Chưa chưa rõ cần làm gì…"*).
                # Chỉ `replyDone` (= `VoiceReply.unknown`) là chuỗi thật.
                if it["id"].startswith("unknown_") and field != "replyDone":
                    continue
                c.add_sentence(merge_one(it[field]), f"{bucket}/{it['id']}/{field}")

    # ── (F) Câu của VoiceReply gắn với MỘT nút / MỘT gói — bao đóng, đếm được ─────────────────────────
    # `notOnThisCar`: mã không có đường điều khiển trên chiếc xe này (ca ac_auto đã [ĐO] trên xe).
    for it in reg["controls"]:
        done = it.get("replyDone") or ""
        if not done.startswith(OK_MARK):
            continue
        act = done[1:].split(" — ")[0].strip()          # phần "Bật Khóa / mở khóa" của câu ✓
        if re.search(r"\d", act) and it["kind"] == "STEP":
            continue                                     # câu có số đã vào khung ở (A)
        c.add_sentence(merge_one(f"✗ {act} — chưa điều khiển được trên xe này"), f"control/{it['id']}/notOnThisCar")
        # `cancelled` chỉ phát được sau một hộp HỎI LẠI ⇒ chỉ nút [VoiceRisk.CONFIRM] mới cần clip.
        # Sau lượt gỡ ADAS 2026-09-16 danh sách ấy đang RỖNG — và đó là lý do phải hỏi registry chứ
        # không liệt kê cho cả 54 nút: 54 clip không đường nào phát ra là ~0,3 MB tải qua 4G vô ích.
        if it.get("risk") == "CONFIRM":
            c.add_sentence(merge_one(f"✗ {act} — đã huỷ"), f"control/{it['id']}/cancelled")
    for it in reg["macros"]:
        done = it.get("replyDone") or ""
        act = done[1:].split(" — ")[0].strip()
        c.add_sentence(merge_one(f"✗ {act} — việc trước còn đang chạy"), f"macro/{it['id']}/busy")
        if it.get("risk") == "CONFIRM":
            c.add_sentence(merge_one(f"✗ {act} — đã huỷ"), f"macro/{it['id']}/cancelled")

    # ── (G) Câu KHÔNG mang nhãn — hằng số của VoiceReply / VoiceClarify / VoiceFeedbackPhrase ────────
    for s in GLOBAL_FIXED:
        c.add_sentence(merge_one(s), "global")
    for s in GLOBAL_FRAMES:
        c.add_sentence(merge_one(s), "global")

    # ── (F2) OQ2(a) — NGUYÊN CÂU cho các dải BẬC có trần, khi chỗ ghép nghe không đạt ────────────────
    # Bật bằng `--whole-ranges`. Chỉ áp cho nút [ControlKind.STEP]: dải của chúng **có trần** trong
    # registry (nhiệt 17–33, gió 0–7, âm lượng 0–30…) nên liệt kê nguyên câu là ~100 giá trị, không nổ.
    # ⚠ KHÔNG áp được cho câu đọc thông tin (*"Pin: 46 %"*): 112 nhãn × 0–999 là chỗ mà tầng ba sinh ra
    # để tránh. Tức OQ2(a) chữa được **đặt bậc**, không chữa được **đọc số đo** — ghi rõ ở đây để người
    # sau không tưởng là đã phủ hết.
    if whole_ranges:
        for it in reg["controls"]:
            if it["kind"] != "STEP":
                continue
            lo, hi, step = int(it["min"]), int(it["max"]), max(1, int(it.get("step") or 1))
            tail = tail_unverified(it)
            for v in range(lo, hi + 1, step):
                c.add_sentence(merge_one(f"✓ Đặt {it['label']} = {v}" + tail), f"control/{it['id']}/whole{v}")
                c.add_sentence(merge_one(f"✗ Đặt {it['label']} = {v} — xe không nhận lệnh"),
                               f"control/{it['id']}/wholefail{v}")
            for verb in ("Tăng", "Giảm"):
                for n in range(1, 6):
                    c.add_sentence(merge_one(f"✓ {verb} {it['label']} {n} nấc" + tail),
                                   f"control/{it['id']}/whole{verb}{n}")

    # ── (H) Tầng 2 — số 0–999 đọc thành chữ ──────────────────────────────────────────────────────────
    for n in range(0, 1000):
        c.add("num", str(n), say=vi_number(n), src="num")

    # ── (I) Đơn vị đứng một mình: mọi đơn vị của registry đều phải có clip, kể cả khi khung ở (B) đã
    #        sinh ra nó — phòng ca ClipSpeaker ghép từ một khung khác.
    for u in sorted({(t.get("unit") or "") for t in reg["telemetry"]} - {""}):
        c.add("unit", f" {u}", src="unit")
    c.add("unit", " nấc", src="unit")
    return c


def tail_unverified(it: dict) -> str:
    """Đuôi *" — chưa kiểm trên xe"* — đọc lại từ chính câu replyDone của nút, không đoán."""
    return " — chưa kiểm trên xe" if "chưa kiểm trên xe" in (it.get("replyDone") or "") else ""


# Câu hằng của `VoiceReply.unknown` (8 lý do), `VoiceClarify`, `VoiceFeedbackPhrase` — chép **nguyên
# văn** từ Kotlin ngày 2026-09-16. Đây là chỗ DUY NHẤT trong file có chuỗi gõ tay; mỗi dòng kèm
# file:line để soát lại được. (T3 sẽ thay hẳn bằng bản sinh từ Kotlin.)
GLOBAL_FIXED = [
    "Chưa có câu lệnh nào",                                                          # VoiceReply.kt:401
    "Chưa rõ cần làm gì — thử \"bật…\", \"mở…\", \"xem…\"",                          # VoiceReply.kt:403
    "Không tìm thấy thứ đó trong xe hay trong launcher",                             # VoiceReply.kt:407
    "Việc đó không đi với thứ đó — thử nêu mức, hoặc đổi động từ",                   # VoiceReply.kt:411
    "Phần này Kachi không tự làm offline (tên bài hát / điểm đến)",                   # VoiceReply.kt:415
    "Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app khác đè lên",         # VoiceReply.kt:421
    "Đã bỏ qua vế không hiểu",                                                       # VoiceReply.kt:425
    "Chưa rõ — nói lại giúp",                                                        # VoiceClarify.kt:86
    "Vẫn chưa rõ — thử nói \"bật đèn đọc\"",                                          # VoiceClarify.kt:93
]

GLOBAL_FRAMES = [
    f"Đã xong {PLACEHOLDER} việc",                                                   # VoiceFeedbackPhrase.kt:98
    f"Bố cục hiện chỉ có {PLACEHOLDER} ô",                                           # VoiceReply.kt:242
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(HERE, "clips.tsv"))
    ap.add_argument("--registry", default=REGISTRY)
    ap.add_argument("--whole-ranges", action="store_true",
                    help="OQ2(a): thêm clip NGUYÊN CÂU cho mọi giá trị của nút bậc (dùng khi chỗ ghép hụt)")
    args = ap.parse_args()

    if not os.path.exists(args.registry):
        print("thiếu %s — chạy `./gradlew :core:test --tests '*FeatureCatalogDumpTest*'` trước" % args.registry)
        return 2
    reg = json.load(open(args.registry, encoding="utf-8"))
    c = enumerate_clips(reg, whole_ranges=args.whole_ranges)

    order = {"fixed": 0, "head": 1, "unit": 2, "num": 3}
    rows = sorted(c.rows.items(), key=lambda kv: (order[kv[0][0]],
                                                  int(kv[0][1]) if kv[0][0] == "num" else 0, kv[0][1]))
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("id\ttext\ttier\tsay\n")
        for (tier, text), say in rows:
            assert "\t" not in text and "\t" not in say
            f.write("%s\t%s\t%s\t%s\n" % (clip_id(tier, text), text, tier, say))

    counts: dict[str, int] = {}
    for (tier, _), _ in rows:
        counts[tier] = counts.get(tier, 0) + 1
    print("%-8s %6s" % ("tầng", "clip"))
    for t in ("fixed", "head", "unit", "num"):
        print("%-8s %6d" % (t, counts.get(t, 0)))
    print("%-8s %6d   → %s" % ("TỔNG", len(rows), args.out))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
