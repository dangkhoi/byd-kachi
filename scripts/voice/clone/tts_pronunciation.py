# -*- coding: utf-8 -*-
"""
═══ BẢN PYTHON CỦA `TtsPronunciation.normalise` — CHỮ HIỂN THỊ → CHỮ ĐỌC ĐƯỢC ═════════════════════════
Nguồn duy nhất của bảng: `core/src/main/kotlin/com/byd/clusternav/launcher/voice/TtsPronunciation.kt`.
Spec: `docs/specs/kachi-voice-fast-natural.html` §9 · `docs/specs/kachi-voice-clone.html` §4.5.

## Vì sao ĐỌC bảng từ Kotlin thay vì chép sang đây
Bảng ấy không phải ý thích: mỗi dòng của nó là một phép đo n=5 qua vòng Piper→ASR đang ship (*"Ka-chi"*
đúng 5/5 còn *"Ka chi"* 0/5; *"e vê"* 5/5 còn *"e-vê"* 0/5). Chép sang Python là dựng **bản sao thứ hai**
của một bảng đo được — và bản sao sẽ lệch đúng vào ngày ai đó sửa một dòng bên Kotlin. Ở đây chỉ đọc.

## Vì sao gói clip PHẢI đi qua đúng phép này
Khoá tra clip là **chuỗi gốc** (`TtsPronunciation.kt` KDoc, mục 3: *"bảng clip khoá theo chuỗi gốc"*),
nhưng **tiếng** trong clip phải là thứ mà cửa ra tiếng đọc lên. Nếu gói clone tự nghĩ một cách đọc khác,
thì cùng một câu sẽ nghe khác nhau giữa giọng bé và đường lùi Piper — và chỗ lệch đó không ai canh.

## Chỗ CỐ Ý lệch khỏi bản Kotlin — một chỗ, ghi rõ
`soundOut` (tra `VoiceAppPhonetics.SYLLABLES`, âm tiếng Anh) **không** được port. Bản Kotlin thử nó
TRƯỚC `spellOut`, nên một từ như `TV` bên đó ra *"ti vi"* còn bên này ra *"tê vê"*.
[ĐO 2026-09-17] quét toàn bộ 1 607 chuỗi của gói: mọi cụm Latin đều đã nằm trong `PHRASES`
(`YouTube` · `AUTO` · `PM2.5` · `SOC` · `SOH` · `VIN` · `Odo` · `Bluetooth` · `Ion` · `Camera`) hoặc là
chữ viết tắt HOA mà `spellOut` phủ đúng (`HUD` · `EV` · `HEV` · `MCU` · `TB`) ⇒ khe này **hiện đang rỗng**.
Cách chốt nếu ai muốn chắc: chạy [audit] dưới đây; nó in ra mọi cụm Latin lạ còn sót.
"""
from __future__ import annotations

import os
import re

REPO = os.environ.get("REPO") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
KT = os.path.join(REPO, "core", "src", "main", "kotlin", "com", "byd", "clusternav",
                  "launcher", "voice", "TtsPronunciation.kt")

_PAIR = re.compile(r'"((?:[^"\\]|\\.)*)"\s+to\s+"((?:[^"\\]|\\.)*)"')
_CHAR_PAIR = re.compile(r"'(.)'\s+to\s+\"((?:[^\"\\]|\\.)*)\"")
_SPACES = re.compile(" {2,}")
MAX_ACRONYM = 5


def _block(src: str, start_marker: str) -> str:
    i = src.index(start_marker)
    j = src.index("\n    )", i)
    return src[i:j]


def _load() -> tuple[dict[str, str], dict[str, str]]:
    src = open(KT, encoding="utf-8").read()
    phrases = dict(_PAIR.findall(_block(src, "val PHRASES")))
    letters = dict(_CHAR_PAIR.findall(_block(src, "val LETTERS")))
    if len(phrases) < 20 or len(letters) < 26:
        raise SystemExit("đọc bảng từ %s hụt (%d cụm · %d chữ cái) — cấu trúc tệp Kotlin đã đổi?"
                         % (KT, len(phrases), len(letters)))
    return phrases, letters


PHRASES, LETTERS = _load()
MAX_KEY = max(len(k) for k in PHRASES)


def _lower(s: str) -> str:
    """Hạ chữ **từng ký tự**, giữ nguyên độ dài — chỉ số của bản thường và bản gốc phải trùng nhau."""
    return "".join(c.lower() if len(c.lower()) == 1 else c for c in s)


def _phrase_at(hay: str, i: int) -> str | None:
    if i > 0 and hay[i - 1].isalpha():
        return None
    for ln in range(min(MAX_KEY, len(hay) - i), 0, -1):
        end = i + ln
        if end < len(hay) and (hay[end].isalpha() or hay[end].isdigit()):
            continue
        cand = hay[i:end]
        if cand in PHRASES:
            return cand
    return None


def _latin_token_end(hay: str, i: int) -> int:
    if not ("a" <= hay[i] <= "z"):
        return i
    if i > 0 and hay[i - 1].isalpha():
        return i
    j = i
    while j < len(hay) and (("a" <= hay[j] <= "z") or hay[j].isdigit()):
        j += 1
    return j


def spell_out(token: str) -> str | None:
    """Đánh vần một chữ viết tắt HOA (2…[MAX_ACRONYM] chữ) bằng tên chữ cái tiếng Việt."""
    if not (2 <= len(token) <= MAX_ACRONYM):
        return None
    if not all("A" <= c <= "Z" for c in token):
        return None
    out = []
    for c in token:
        name = LETTERS.get(c.lower())
        if name is None:
            return None
        out.append(name)
    return " ".join(out)


def _append(out: list[str], piece: str) -> None:
    if not piece:
        return
    prev = out[-1][-1] if out and out[-1] else None
    if prev is not None and (prev.isalpha() or prev.isdigit()) and (piece[0].isalpha() or piece[0].isdigit()):
        out.append(" ")
    out.append(piece)


def normalise(text: str) -> str:
    """Chuỗi hiển thị → chuỗi đọc được. Quét MỘT lượt trái→phải; phần đã chép ra không quét lại."""
    if not text:
        return text
    hay = _lower(text)
    out: list[str] = []
    i = 0
    while i < len(text):
        key = _phrase_at(hay, i)
        if key is not None:
            _append(out, PHRASES[key])
            i += len(key)
            continue
        end = _latin_token_end(hay, i)
        if end > i:
            token = text[i:end]
            _append(out, spell_out(token) or token)
            i = end
            continue
        out.append(text[i])
        i += 1
    return _SPACES.sub(" ", "".join(out))


def audit(texts) -> list[str]:
    """Cụm Latin nào đi qua [normalise] mà **vẫn còn nguyên** — tức khe `soundOut` đang thật sự rỗng hay không."""
    left = []
    for t in texts:
        for m in re.finditer(r"(?<![A-Za-z])[A-Za-z][A-Za-z0-9]*", normalise(t)):
            w = m.group(0)
            if w.lower() in PHRASES or len(w) == 1:
                continue
            if any(ch.isupper() for ch in w):
                left.append(w)
    return sorted(set(left))


# ── bài tự kiểm: các vector của `TtsPronunciationTest` KHÔNG phụ thuộc `soundOut` ────────────────────
_VECTORS = [
    ("Kachi", "Ka-chi"), ("YouTube Music", "Du-túp Mu-dích"), ("Google Maps", "Gu-gồ Máp"),
    ("KACHI", "Ka-chi"), ("KaChI", "Ka-chi"), ("KM/H", "ki-lô-mét trên giờ"),
    ("kWh/100km", "ki-lô-oát giờ trên một trăm ki-lô-mét"), ("Android Auto", "An-đroi Ô-tô"),
    ("Pin (SOC)", "pin"), ("Sức khỏe pin (SOH)", "sức khoẻ pin"), ("Số VIN", "số vin"),
    ("Điều hòa AUTO", "Điều hòa au-tô"), ("Tầm hoạt động EV", "Tầm hoạt động e vê"),
    ("Bụi mịn PM2.5", "Bụi mịn pê mờ hai chấm năm"), ("Odo tổng", "ô-đô tổng"),
    ("Chìa Bluetooth", "Chìa blu-tút"), ("Ion âm", "i-on âm"), ("Camera 360", "ca-mê-ra 360"),
    ("tốc độ cao", "tốc độ cao"), ("Chế độ Comfort", "Chế độ Comfort"), ("okay", "okay"),
    ("Khoang lái", "Khoang lái"), ("kmx", "kmx"), ("pin", "pin"), ("Mở hết kính", "Mở hết kính"),
    ("46%", "46 phần trăm"), ("46 %", "46 phần trăm"), ("50km", "50 ki-lô-mét"),
    ("24°C", "24 độ"), ("24 độ C", "24 độ"), ("257 km", "257 ki-lô-mét"),
]


def selftest() -> int:
    bad = [(a, b, normalise(a)) for a, b in _VECTORS if normalise(a) != b]
    for a, b, got in bad:
        print("  ✗ «%s» → «%s» (mong «%s»)" % (a, got, b))
    # luỹ đẳng — cùng lời hứa mà bài canh Kotlin giữ
    idem = [s for s in list(PHRASES) + list(PHRASES.values()) if normalise(normalise(s)) != normalise(s)]
    for s in idem:
        print("  ✗ không luỹ đẳng: «%s» → «%s» → «%s»" % (s, normalise(s), normalise(normalise(s))))
    print("tts_pronunciation: %d/%d vector đúng · %d cụm không luỹ đẳng · %d dòng bảng · %d chữ cái"
          % (len(_VECTORS) - len(bad), len(_VECTORS), len(idem), len(PHRASES), len(LETTERS)))
    return 1 if (bad or idem) else 0


if __name__ == "__main__":
    raise SystemExit(selftest())
