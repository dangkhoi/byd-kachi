#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Chạy lại nhật ký lượt nói LẤY TỪ XE trên HOST — "xe nghe ra gì" đặt cạnh "host nghe ra gì".

Vì sao công cụ này tồn tại: `hotword-matrix.py` đo trên 25 tệp WAV sinh bằng `say -v Linh` (macOS TTS), và
CLAUDE.md §2 đã ghi rõ số ấy nói về *model + hotword*, KHÔNG nói về giọng thật + mic 4 kênh + 80 km/h + điều hoà
+ nhạc. H2 (1.69) cho app tự ghi mỗi lượt nói thành một cặp `<stamp>.wav` + `<stamp>.json` trong
`filesDir/voice-log/`, và một nút *Xuất nhật ký voice* (hoặc lệnh cầu `voice_dump`) nén cả thư mục ra thẻ. Script
này mở gói zip ấy ra và trả lời đúng một câu hỏi: **host nghe khác xe ở chỗ nào, và vì sao.**

Ba cột của bảng cuối, mỗi cột chốt một loại nguyên nhân khác hẳn nhau:
  • xe nghe ra ≠ host nghe ra (cùng tệp WAV)  ⇒ khác CẤU HÌNH (hotword/beam/score/int8-fp32) hoặc khác bản model
  • xe = host, và cả hai đều sai              ⇒ AUDIO (mic câm/méo) hoặc chính model — nghe thử tệp WAV
  • xe = host, cả hai đều đúng                ⇒ lỗi nằm ở tầng NLU/ngữ pháp, không phải ở tai

Chuẩn bị (một lần) — GIỐNG HỆT `hotword-matrix.py`, cố ý dùng chung venv + thư mục model:
  python3 -m venv /tmp/sherpa-venv && /tmp/sherpa-venv/bin/pip install sherpa-onnx==1.13.8 numpy
  MODEL_DIR = thư mục có encoder.onnx · decoder.onnx · joiner.onnx · tokens.txt + bpe_vocab.txt
  (bpe_vocab.txt chép từ app/src/main/assets/voice/zipformer-vi-2025-04-20.bpe_vocab.txt)

Lấy gói zip từ xe:
  adb shell am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd voice_dump   # in ra `path`
  adb pull /sdcard/Download/kachi-voice-<stamp>.zip /tmp/

Dùng:
  /tmp/sherpa-venv/bin/python scripts/voice/replay-car-log.py /tmp/kachi-voice-<stamp>.zip --model <MODEL_DIR>
        [--hotwords core/build/hotwords/hotwords-phrases.txt] [--score 3.0] [--beam 4] [--keep /tmp/car-log]

⚠ Mức bằng chứng: đây là tiếng THẬT trên xe thật, nên nó là [ĐO] cho câu hỏi *"xe nghe ra gì"*. Phần host chạy
  lại vẫn là host — nó chỉ [ĐO] được *"cùng khúc tiếng ấy, cấu hình này cho ra gì"*, không nói thay cabin.
"""
from __future__ import annotations  # `str | None` chạy được cả trên python 3.9 (macOS hệ thống)

import argparse
import json
import os
import re
import shutil
import sys
import tempfile
import unicodedata
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))


def norm(s: str) -> str:
    """Cùng phép thường hoá với `hotword-matrix.py` — hai công cụ phải chấm điểm giống hệt nhau."""
    return re.sub(r"[^\w\s]", "", unicodedata.normalize("NFC", s or "").lower()).strip()


def die(msg: str) -> "None":
    """Hỏng thì nói ĐỦ để sửa được ngay, không chỉ nói là hỏng (bài học CLAUDE.md §11)."""
    sys.exit(f"replay-car-log: {msg}")


def load_matrix():
    """
    Nạp `hotword-matrix.py` cạnh tệp này và dùng lại `make_recognizer`/`read_wave` của nó.

    Vì sao import chứ không chép: hai công cụ PHẢI dựng recognizer bằng cùng tham số (`modified_beam_search` ·
    beam · score · bpe · num_threads · feature_dim). Chép sang đây là dựng bản sao thứ hai của một cấu hình —
    và bản sao ấy sẽ lệch đúng vào lần ai đó chỉnh một tham số, biến mọi phép so sau đó thành vô nghĩa.
    """
    import importlib.util
    path = os.path.join(HERE, "hotword-matrix.py")
    if not os.path.isfile(path):
        die(f"thiếu {path} — script này dùng lại make_recognizer/read_wave của nó")
    spec = importlib.util.spec_from_file_location("hotword_matrix", path)
    mod = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(mod)  # type: ignore[union-attr]
    except ImportError as e:
        die(f"không nạp được hotword-matrix.py ({e}) — thiếu sherpa-onnx/numpy? "
            f"python3 -m venv /tmp/sherpa-venv && /tmp/sherpa-venv/bin/pip install sherpa-onnx==1.13.8 numpy")
    return mod


def check_model(model_dir: str, hotwords: "str | None") -> None:
    if not os.path.isdir(model_dir):
        die(f"--model '{model_dir}' không phải thư mục — cần thư mục có encoder.onnx/decoder.onnx/joiner.onnx/tokens.txt")
    need = ["encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt"]
    missing = [n for n in need if not os.path.isfile(os.path.join(model_dir, n))]
    if missing:
        die(f"thiếu trong '{model_dir}': {', '.join(missing)} — tải như VoiceModelStore, hoặc đổi tên tệp cho khớp")
    if hotwords:
        if not os.path.isfile(hotwords):
            die(f"--hotwords '{hotwords}' không đọc được — sinh bằng "
                f"./gradlew :core:test --tests '*SherpaBiasingCoverageTest*' "
                f"(ra core/build/hotwords/hotwords-phrases.txt)")
        if not os.path.isfile(os.path.join(model_dir, "bpe_vocab.txt")):
            die(f"có --hotwords nhưng thiếu {model_dir}/bpe_vocab.txt — chép từ "
                f"app/src/main/assets/voice/zipformer-vi-2025-04-20.bpe_vocab.txt")


def unpack(zip_path: str, out_dir: str) -> "list[tuple[str, dict]]":
    """Mở gói → danh sách (đường dẫn WAV, JSON đi kèm), theo thứ tự mốc. Mốc thiếu một nửa bị bỏ, có báo."""
    if not os.path.isfile(zip_path):
        die(f"không mở được '{zip_path}' — chạy `--es cmd voice_dump` rồi `adb pull` trước")
    try:
        with zipfile.ZipFile(zip_path) as z:
            # Gói do app nén là PHẲNG (không thư mục con); `os.path.basename` chặn luôn mọi mục kiểu `../`.
            for info in z.infolist():
                name = os.path.basename(info.filename)
                if not name:
                    continue
                with z.open(info) as src, open(os.path.join(out_dir, name), "wb") as dst:
                    shutil.copyfileobj(src, dst)
    except zipfile.BadZipFile:
        die(f"'{zip_path}' không phải tệp zip hợp lệ — lượt `adb pull` có thể đã cụt")

    out = []
    for wav in sorted(f for f in os.listdir(out_dir) if f.endswith(".wav")):
        stamp = wav[:-4]
        meta_path = os.path.join(out_dir, stamp + ".json")
        if not os.path.isfile(meta_path):
            print(f"[bỏ qua] {stamp}: có .wav mà không có .json (tiến trình bị giết giữa lượt ghi?)")
            continue
        try:
            with open(meta_path, encoding="utf-8") as f:
                out.append((os.path.join(out_dir, wav), json.load(f)))
        except (OSError, json.JSONDecodeError) as e:
            print(f"[bỏ qua] {stamp}: JSON hỏng ({e})")
    if not out:
        die(f"gói '{zip_path}' không có cặp .wav/.json nào — công tắc 'Giữ nhật ký lượt nói' có đang BẬT không?")
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("zip", help="tệp kachi-voice-<stamp>.zip lấy từ xe")
    ap.add_argument("--model", required=True, help="thư mục model sherpa (như VoiceModelStore lắp)")
    ap.add_argument("--hotwords", default=None, help="tệp hotword (bỏ trống = chạy KHÔNG biasing, làm mốc)")
    ap.add_argument("--score", type=float, default=3.0, help="hotwords_score — mặc định = SherpaModelCatalog")
    ap.add_argument("--beam", type=int, default=4, help="max_active_paths — mặc định = SherpaModelCatalog")
    ap.add_argument("--keep", default=None, help="giải nén vào thư mục này thay vì thư mục tạm (để nghe lại WAV)")
    a = ap.parse_args()

    check_model(a.model, a.hotwords)
    mx = load_matrix()

    tmp = a.keep or tempfile.mkdtemp(prefix="kachi-voice-")
    os.makedirs(tmp, exist_ok=True)
    entries = unpack(a.zip, tmp)

    rec = mx.make_recognizer(a.model, a.hotwords, a.score)
    # `make_recognizer` không nhận beam (nó ghim 4 như bản build). Chỉ báo khi người dùng xin khác — im lặng bỏ
    # qua một cờ là để cả một lượt đo nói về một cấu hình KHÁC thứ người đo tưởng.
    if a.beam != 4:
        print(f"⚠ --beam {a.beam} chưa được hotword-matrix.make_recognizer hỗ trợ (nó ghim max_active_paths=4); "
              f"bảng dưới đây chạy với beam 4.")

    rows = []
    for wav_path, meta in entries:
        samples = mx.read_wave(wav_path)
        st = rec.create_stream()
        st.accept_waveform(16000, samples)
        rec.decode_stream(st)
        host = st.result.text.strip().lower()
        car = (meta.get("heard") or "").strip()
        same = norm(car) == norm(host)
        rows.append((meta, car, host, same))
        print(
            f"[{meta.get('stamp', '?')}] {'=' if same else '≠'}\n"
            f"    xe  : {car!r}  (câu cuối: {meta.get('sentence', '')!r})\n"
            f"    host: {host!r}\n"
            f"    ngắt {meta.get('endpoint_ms', 0)}ms · tiếng {meta.get('speech_ms', 0)}ms · "
            f"im {meta.get('silence_ms', 0)}ms · chốt-bởi-{'ngắt-câu' if meta.get('endpoint_fired') else 'trần'} · "
            f"mic {meta.get('mic_source_name') or meta.get('mic_source')} · {meta.get('model_id', '?')} · "
            f"{meta.get('hotwords', 0)} cụm · giải mã {meta.get('decode_ms', 0)}ms\n"
            f"    ý định {meta.get('intents') or []} → {meta.get('replies') or []}",
            flush=True,
        )

    same_n = sum(1 for *_, s in rows if s)
    print("\n=== BẢNG TÓM TẮT ===")
    print(f"{'mốc':<22} | {'khớp':^5} | xe nghe ra / host nghe ra")
    for meta, car, host, same in rows:
        print(f"{meta.get('stamp', '?'):<22} | {'=' if same else '≠':^5} | {car!r} / {host!r}")
    print(f"\nxe ≡ host: {same_n}/{len(rows)} lượt "
          f"({'cấu hình host khớp xe' if same_n == len(rows) else 'có lượt lệch — xem ba nhánh nguyên nhân ở đầu tệp'})")
    if not a.keep:
        print(f"(WAV đã giải nén ở {tmp} — dùng --keep <dir> nếu muốn giữ lại để nghe)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
