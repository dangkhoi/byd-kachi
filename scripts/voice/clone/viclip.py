# -*- coding: utf-8 -*-
"""
═══ PHẦN DÙNG CHUNG CỦA XƯỞNG GIỌNG NHÂN BẢN ═════════════════════════════════════════════════════════
Spec: docs/specs/kachi-voice-clone.html §4.1 (pha xưởng), §6 V-pack (trọng tài ASR).

Ba việc dùng ở CẢ ba script (`synth-pack.py` sinh clip · `compose-check.py` nghe thử chỗ ghép ·
`build-pack.py` đóng gói) nên chúng ở đây, không chép ba bản:
  • [Judge] — đọc lại bằng ĐÚNG bộ nhận dạng đang ship (zipformer-vi + hotword thật);
  • [trim_silence] / [stitch] — luật ghép ba mảnh (crossfade 15 ms + cân RMS) mà `ClipSpeaker` sẽ
    làm lại trên xe; nếu hai bên lệch luật thì phép nghe thử T5 không nói gì về thứ xe sẽ phát;
  • [clip_seed] / [gen_text] — seed theo clip (chạy lại ⇒ audio y hệt, R1) và bẫy "mọc đuôi";
  • [same] / [close] — trọng tài HAI MỨC: mức chặt để **báo cáo**, mức bỏ dấu thanh để quyết **có sinh
    lại không**. Xem KDoc [close] về vì sao hai mức chứ không một.
"""
from __future__ import annotations

import hashlib
import os
import sys

import numpy as np

REPO = os.environ.get("REPO") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
if os.path.join(REPO, "scripts", "voice") not in sys.path:
    sys.path.insert(0, os.path.join(REPO, "scripts", "voice"))
from vi_text import norm as vi_norm  # noqa: E402

XFADE_MS = 15
PEAK = 0.89


def clip_seed(cid: str, attempt: int) -> int:
    """Seed **của clip**, không phải của lượt chạy ⇒ chạy lại cho audio y hệt (R1), và một clip phải
    sinh lại không kéo theo clip khác đổi giọng."""
    h = hashlib.sha256(f"{cid}#{attempt}".encode()).digest()
    return int.from_bytes(h[:4], "big") % (2 ** 31 - 1)


def gen_text(t: str) -> str:
    """Chữ đem cho F5. Câu không có dấu kết bị **mọc đuôi** của chính clip mẫu (*"đã bật đèn đọc"* →
    *"đã bật đèn quá"*, §4.6) ⇒ thêm dấu chấm."""
    t = t.strip()
    if not t:
        return t
    return t if t[-1] in ".!?" else t + "."


def split_on_silence(x: np.ndarray, sr: int, n: int, weights: list[float] | None = None,
                     min_gap_ms: int = 55, floors: tuple[float, ...] = (0.05, 0.10, 0.03, 0.16, 0.02),
                     ) -> list[np.ndarray] | None:
    """Cắt một lượt sinh GỘP thành đúng [n] mảnh tại [n]-1 quãng lặng; `None` nếu không đủ chỗ cắt.

    ## Vì sao phải gộp nhiều câu vào một lượt suy diễn
    [ĐO host 2026-09-16] một lượt `infer` tốn ~20 s **bất kể câu dài hay ngắn**, vì mô hình sinh lại cả
    clip mẫu 8,29 s rồi mới cắt bỏ phần đầu. Với 1 000 clip số (mỗi clip ~1 s) thì 93 % thời gian máy
    chạy là để sinh đi sinh lại cùng một clip mẫu. Gộp 10 câu một lượt ⇒ cùng cái giá ấy chia cho 10.

    Chỉ đúng vì có **trọng tài ASR** ở sau: mảnh nào cắt trượt thì nó nghe ra chữ khác và bị sinh lại
    riêng. Không có phép kiểm ấy thì đây là một tối ưu mù.
    """
    if n <= 1:
        return [x]
    hop = max(1, int(sr * 0.01))
    frames = len(x) // hop
    if frames < n * 8:
        return None
    env = np.array([float(np.sqrt(np.mean(x[i * hop:(i + 1) * hop] ** 2)) + 1e-12) for i in range(frames)])
    peak = float(env.max())
    # ⚠ MỘT ngưỡng là quá mỏng. [ĐO 2026-09-17] cùng 10 chữ số, seed này cho 10 quãng lặng ở mức 0,05
    # (đủ cắt), seed kia cho 8 (hụt một) — và hụt thì cả lượt phải sinh lại từng câu, mất đúng cái lợi
    # của phép gộp. Thử một THANG ngưỡng, lấy ngưỡng đầu tiên đủ chỗ cắt.
    for floor in floors:
        quiet = env < floor * peak
        runs, i = [], 0
        while i < frames:
            if quiet[i]:
                j = i
                while j < frames and quiet[j]:
                    j += 1
                runs.append((i, j))
                i = j
            else:
                i += 1
        inner = [(a, b) for a, b in runs if a > 0 and b < frames and (b - a) * 10 >= min_gap_ms]
        if len(inner) < n - 1:
            continue
        # ⚠ KHÔNG lấy "n-1 quãng lặng dài nhất". Một câu như *"Chưa mở được cốp sau, xe không nhận lệnh"*
        # có quãng nghỉ **bên trong** (dấu phẩy) dài hơn quãng giữa hai câu ⇒ luật dài-nhất cắt vào giữa
        # câu và bỏ mất một ranh giới thật. Thay vào đó: ước chỗ cắt theo **độ dài chữ** của từng mảnh
        # ([weights]) rồi lấy quãng lặng GẦN mốc ấy nhất, mỗi quãng dùng một lần, theo thứ tự.
        mids = [(a + b) // 2 for a, b in inner]
        if weights and len(weights) == n and sum(weights) > 0:
            acc, want, tot = 0.0, [], float(sum(weights))
            for w in weights[:-1]:
                acc += w
                want.append(acc / tot * frames)
            chosen, used = [], set()
            for target in want:
                cand = [(abs(m - target), k) for k, m in enumerate(mids) if k not in used]
                if not cand:
                    break
                _, k = min(cand)
                used.add(k)
                chosen.append(mids[k])
            if len(chosen) < n - 1:
                continue
            cuts = sorted(c * hop for c in chosen)
        else:
            order = sorted(range(len(inner)), key=lambda k: inner[k][0] - inner[k][1])
            cuts = sorted(mids[k] * hop for k in order[:n - 1])
        bounds = [0] + cuts + [len(x)]
        parts = [x[bounds[k]:bounds[k + 1]] for k in range(n)]
        # Mảnh rỗng/quá ngắn ⇒ cắt trúng giữa một tiếng, không phải giữa hai câu. Thà bỏ cả lượt.
        if min(len(p) for p in parts) < sr * 0.15:
            continue
        return parts
    return None


def rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(x.astype(np.float64) ** 2)) + 1e-12)


def trim_silence(x: np.ndarray, sr: int, thresh: float = 0.02, keep_ms: int = 25) -> np.ndarray:
    """Cắt lặng hai đầu, chừa [keep_ms]. Mỗi mảnh sinh riêng mang theo quãng lặng riêng; nối thẳng thì
    một câu ba mảnh nghe thành ba câu rời."""
    a = np.abs(x)
    idx = np.where(a > thresh * max(a.max(), 1e-9))[0]
    if idx.size == 0:
        return x
    k = int(sr * keep_ms / 1000)
    return x[max(0, int(idx[0]) - k):min(len(x), int(idx[-1]) + k)]


def stitch(parts: list[np.ndarray], sr: int, ms: int = XFADE_MS) -> np.ndarray:
    """Nối các mảnh, crossfade [ms] mỗi mối. **Cân RMS trước**: ba mảnh sinh rời có độ to khác nhau, và
    một bậc âm lượng ngay giữa câu nghe rõ hơn cả một cú click ở chỗ nối."""
    parts = [p for p in parts if len(p)]
    if not parts:
        return np.zeros(0, dtype=np.float32)
    target = float(np.median([rms(p) for p in parts]))
    parts = [(p * min(3.0, target / rms(p))).astype(np.float32) for p in parts]
    n = int(sr * ms / 1000)
    out = parts[0]
    for p in parts[1:]:
        if len(out) < n or len(p) < n:
            out = np.concatenate([out, p])
            continue
        fade_out = out[-n:] * np.linspace(1.0, 0.0, n, dtype=np.float32)
        fade_in = p[:n] * np.linspace(0.0, 1.0, n, dtype=np.float32)
        out = np.concatenate([out[:-n], fade_out + fade_in, p[n:]])
    return (out / max(float(np.abs(out).max()), 1e-9) * PEAK).astype(np.float32)


class Judge:
    """Trọng tài của V-pack: ĐÚNG bộ nhận dạng đang ship + hotword thật (`scripts/voice/hotword-matrix.py`).

    Dùng bộ khác — kể cả bộ *tốt hơn* — là chấm một thứ không ai sẽ nghe: clip nào xe nghe nhầm thì
    người lái cũng nghe nhầm ở đúng chỗ đó."""

    def __init__(self, model_dir: str, hotwords: str) -> None:
        import sherpa_onnx
        self.rec = sherpa_onnx.OfflineRecognizer.from_transducer(
            tokens=model_dir + "/tokens.txt", encoder=model_dir + "/encoder.onnx",
            decoder=model_dir + "/decoder.onnx", joiner=model_dir + "/joiner.onnx",
            num_threads=2, sample_rate=16000, feature_dim=80,
            decoding_method="modified_beam_search", max_active_paths=4, provider="cpu",
            hotwords_file=hotwords, hotwords_score=3.0,
            modeling_unit="bpe", bpe_vocab=model_dir + "/bpe_vocab.txt")

    def hear(self, x: np.ndarray, sr: int = 24000) -> str:
        from math import gcd
        if sr != 16000:
            from scipy.signal import resample_poly
            g = gcd(sr, 16000)
            x = resample_poly(x, 16000 // g, sr // g).astype(np.float32)
        st = self.rec.create_stream()
        st.accept_waveform(16000, np.ascontiguousarray(x, dtype=np.float32))
        self.rec.decode_stream(st)
        return st.result.text.strip()


def _deaccent(s: str) -> str:
    import unicodedata
    s = unicodedata.normalize("NFD", s).replace("đ", "d").replace("Đ", "D")
    return "".join(c for c in s if not unicodedata.combining(c))


def close(said: str, heard: str) -> bool:
    """Nghe ra **đúng chuỗi tiếng** không, **bỏ qua dấu thanh**.

    ## Vì sao cần mức thứ hai, và nó dùng để làm gì
    Bộ nhận dạng viết dấu **không ổn định** cho cùng một tiếng: [ĐO 2026-09-17] cùng clip *"ki-lô-mét"*,
    lượt này nó ghi `KI LÔ MÉT`, lượt kia `KILOMET`; *"pê mờ"* nó ghi thẳng `PM`. Mức [same] (giữ dấu)
    coi cả hai là sai ⇒ mỗi lần như thế tốn thêm **ba lượt sinh lại** cho một clip **không sai**.

    ⇒ Hai mức, hai việc khác nhau:
     • [same] là con số **báo cáo** — tỉ lệ đọc lại đúng nguyên văn, giữ dấu thanh, vì *"nhé"* ra *"nhá"*
       là lỗi thật (§4.6 · OQ4) và phải hiện ra trong báo cáo;
     • [close] là cổng **sinh lại** — chỉ khi nghe ra một TIẾNG KHÁC (mất chữ, thừa chữ, sai phụ âm) thì
       mới đáng tốn thêm lượt máy. Lệch mỗi dấu thanh thì sinh lại cũng ra thế (đã [ĐO] ở §4.6: ổn định
       qua mọi seed), nên sinh lại chỉ là đốt giờ.
    """
    return _deaccent(vi_norm(said)).replace(" ", "") == _deaccent(vi_norm(heard)).replace(" ", "")


def same(said: str, heard: str) -> bool:
    """Clip có đọc ĐÚNG chữ không.

    Nền là `vi_text.norm` (NFC · số thành chữ · dấu thanh kiểu mới · bỏ dấu câu · gộp trắng), rồi **bỏ
    nốt khoảng trắng**.

    ## Vì sao bỏ khoảng trắng — và vì sao KHÔNG bỏ dấu thanh
    Chỗ ngắt từ là **quy ước viết của bộ nhận dạng**, không phải thứ tai người nghe ra: cùng một tiếng,
    nó ghi *"KILOMET"* nơi ta viết *"ki lô mét"*, *"Ô ĐO TỔNG"* nơi ta viết *"Odo tổng"* ([ĐO] 2026-09-17,
    và [ĐO] 2026-09-16 với chính bản Piper đang ship — §4.6). Bắt lỗi ở đó là **chấm trọng tài**, và mỗi
    lần chấm sai tốn thêm ba lượt sinh lại cho một clip vốn không sai.

    Dấu thanh thì ngược lại — giữ. *"nhé"* đọc ra *"nhá"* là một lỗi THẬT, ổn định qua mọi seed (§4.6,
    OQ4); một phép so bỏ dấu sẽ nuốt mất đúng loại lỗi mà V-pack sinh ra để bắt."""
    return vi_norm(said).replace(" ", "") == vi_norm(heard).replace(" ", "")


def _selftest() -> int:
    """Canary cho hai luật KHÔNG cần mô hình: cắt theo lặng và ghép crossfade.

    Dựng bằng tín hiệu tổng hợp (4 đoạn sin, lặng 120 ms xen giữa) nên chạy được ở bất kỳ máy nào,
    không cần F5, không cần xe. Khoá đúng hai thứ đã trả giá để học:
      • cắt phải bám **vị trí mong đợi**, không bám quãng lặng dài nhất (một quãng nghỉ trong câu dài
        hơn quãng giữa hai câu là ca THẬT — xem KDoc [split_on_silence]);
      • ghép phải **cân độ to** giữa các mảnh, nếu không nghe thành bậc âm lượng giữa câu.
    """
    sr = 24000
    rng = np.random.default_rng(0)
    segs, gaps = [], []
    lens = [0.9, 0.35, 0.8, 0.5]                       # mảnh dài ngắn khác nhau — đúng hình dạng thật
    for i, sec in enumerate(lens):
        t = np.arange(int(sr * sec)) / sr
        amp = 0.2 if i == 1 else 0.8                   # một mảnh nhỏ tiếng hẳn ⇒ thử phép cân RMS
        segs.append((amp * np.sin(2 * np.pi * 220 * t)).astype(np.float32))
        gaps.append(np.zeros(int(sr * (0.30 if i == 0 else 0.12)), dtype=np.float32))
    x = np.concatenate([p for i, s in enumerate(segs) for p in (s, gaps[i])][:-1])
    x += rng.normal(0, 1e-4, len(x)).astype(np.float32)

    bad = []
    parts = split_on_silence(x, sr, len(segs), weights=[float(len(s)) for s in segs])
    if parts is None:
        bad.append("split_on_silence: không cắt được tín hiệu 4 đoạn rõ ràng")
    else:
        got = [len(trim_silence(p, sr)) / sr for p in parts]
        for i, (want, have) in enumerate(zip(lens, got)):
            if abs(want - have) > 0.12:
                bad.append("mảnh %d dài %.2fs, mong ~%.2fs" % (i, have, want))
    y = stitch([trim_silence(s, sr) for s in segs], sr)
    if not (0.85 <= float(np.abs(y).max()) <= 0.90):
        bad.append("stitch: đỉnh %.3f ngoài dải chuẩn hoá" % float(np.abs(y).max()))
    # cân RMS: mảnh nhỏ tiếng phải được kéo lên, không còn chênh 4 lần như đầu vào
    q = len(y) // 4
    r = [rms(y[k * q:(k + 1) * q]) for k in range(4)]
    if max(r) / min(r) > 2.5:
        bad.append("stitch: chênh độ to giữa các phần còn %.1f lần" % (max(r) / min(r)))

    for b in bad:
        print("  ✗ " + b)
    print("viclip selftest: %s" % ("ĐỎ (%d lỗi)" % len(bad) if bad else "xanh"))
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(_selftest())
