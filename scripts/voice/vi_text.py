#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Chuẩn hoá chữ tiếng Việt dùng CHUNG cho mọi công cụ voice trên host (`mishear-table.py`,
`propose-hotwords.py`, `gen-variants.py`).

Một chỗ duy nhất, vì hai công cụ so cùng một thứ mà chuẩn hoá khác nhau thì số của chúng không ghép được — và
đó đúng là họ lỗi mà `CLAUDE.md` §4.1 (DRY) cấm.

Ba việc, mỗi việc có một lý do ĐÃ ĐO:
 • [canon_tone] — *"hoà"* và *"hòa"* là cùng một chữ, khác chỗ đặt dấu. Dự án viết kiểu CŨ, mô hình
   `zipformer-vi` chỉ có kiểu MỚI trong `tokens.txt` (`▁HÒA` · `▁KHÓA` · `▁KHỎE`; không có `▁HOÀ` · `▁KHOÁ`
   · `▁KHOẺ`). Không quy về một mối thì (a) phép so đếm nhầm chữ đúng thành sai, và (b) 54/1757 dòng hotword
   đang bias một chuỗi token mô hình KHÔNG BAO GIỜ xuất ra.
 • [spell_digits] — mô hình chỉ xuất CHỮ, nên câu tham chiếu có chữ số phải đọc thành chữ mới so được.
 • [deaccent] — cùng dạng `VoiceLexicon.deaccent` trả về, để mô phỏng tầng so khớp chữ của bộ phân tích.
"""
from __future__ import annotations

import re
import unicodedata

# Kiểu CŨ → kiểu MỚI, chỉ cho cụm nguyên âm ĐỨNG CUỐI âm tiết (xem [canon_tone]).
TONE_OLD_NEW = {
    "oà": "òa", "oá": "óa", "oả": "ỏa", "oã": "õa", "oạ": "ọa",
    "oè": "òe", "oé": "óe", "oẻ": "ỏe", "oẽ": "õe", "oẹ": "ọe",
    "uỳ": "ùy", "uý": "úy", "uỷ": "ủy", "uỹ": "ũy", "uỵ": "ụy",
}

DIGITS = ["không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"]


def canon_tone(s: str) -> str:
    """*"hoà"* → *"hòa"*, nhưng *"hoàn"* · *"ngoài"* · *"toàn"* GIỮ NGUYÊN.

    Điều kiện là cụm nguyên âm phải đứng **cuối âm tiết**: có phụ âm cuối thì dấu vốn đã nằm đúng chỗ, và
    `tokens.txt` cũng ghi đúng như vậy (`▁HOÀN` · `▁NGOÀI` · `▁THOÁNG` đều có mặt). Đổi mù cả cụm là biến ba
    từ đang đúng thành ba chuỗi không tồn tại — đã thử và bắt được ở lượt đầu 2026-09-16.
    """
    for old, new in TONE_OLD_NEW.items():
        s = re.sub(old + r"(?![\wÀ-ỹ])", new, s)
        s = re.sub(old.upper() + r"(?![\wÀ-ỹ])", new.upper(), s)
    return s


def vi_number(n: int) -> str:
    """0–999 thành chữ theo cách người Việt ĐỌC (21 = *hai mươi mốt*, 25 = *hai mươi lăm*)."""
    if n < 10:
        return DIGITS[n]
    if n < 100:
        t, u = divmod(n, 10)
        head = "mười" if t == 1 else f"{DIGITS[t]} mươi"
        if u == 0:
            return head
        if u == 1:
            return head + (" một" if t == 1 else " mốt")
        if u == 5:
            return head + " lăm"
        return f"{head} {DIGITS[u]}"
    h, r = divmod(n, 100)
    tail = "" if r == 0 else (f" lẻ {DIGITS[r]}" if r < 10 else " " + vi_number(r))
    return f"{DIGITS[h]} trăm{tail}"


def spell_digits(s: str) -> str:
    """Chữ số và `%` trong câu → CHỮ. Số > 3 chữ số đọc từng con số (biển số, mã) thay vì đọc thành lượng."""
    s = re.sub(r"(\d+)\s*%", lambda m: vi_number(int(m.group(1))) + " phần trăm", s)
    return re.sub(r"\d+", lambda m: vi_number(int(m.group(0))) if len(m.group(0)) <= 3 else
                  " ".join(DIGITS[int(c)] for c in m.group(0)), s)


def norm(s: str) -> str:
    """Dạng để SO SÁNH: NFC · số thành chữ · dấu thanh kiểu mới · thường · bỏ dấu câu · gộp khoảng trắng."""
    s = canon_tone(spell_digits(unicodedata.normalize("NFC", s)))
    return re.sub(r"\s+", " ", re.sub(r"[^\w\s]", " ", s.lower())).strip()


def deaccent(s: str) -> str:
    s = unicodedata.normalize("NFD", s).replace("đ", "d").replace("Đ", "D")
    return "".join(c for c in s if not unicodedata.combining(c)).lower()


def tokens(s: str) -> list[str]:
    return [t for t in re.split(r"[^\w]+", deaccent(norm(s))) if t]
