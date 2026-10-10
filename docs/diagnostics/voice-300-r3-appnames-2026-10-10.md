# 3.0 R3 — mô hình nghe tên app Anh thế nào (Waze · Google Maps · VietMap · Spotify · Zing)

> Spec [`specs/kachi-300-plan.html`](../specs/kachi-300-plan.html) R3 (`VOICE-TEACH-WAZE` + `VOICE-EN-APPNAME-HOTWORD-MEASURE`).
> Nhãn: **[ĐO]** đo host lượt này · **[SUY]** suy từ source · **[ĐOÁN]** · **[CHƯA BIẾT]**.
> ⚠ Mức bằng chứng: giọng TTS macOS (Linh / Samantha) ≠ giọng người + mic xe + ồn cabin. Số dưới đây nói về **mô hình + tệp hotword**, không nói về độ chính xác trên xe.

## 0. Cách đo (lặp lại được)

- Công cụ: `scripts/voice/hotword-matrix.py` (sherpa-onnx 1.13.8 · beam 4 · score 3,0 · `--trim vad` như app), cùng 4 tệp mô hình `zipformer-vi-int8-2025-04-20` (sha khớp `SherpaModelCatalog`, như 2.99 R5) + `bpe_vocab.txt` của app.
- Tệp tham chiếu **ship** = `core/build/hotwords/hotwords-phrases-with-labels.txt` của cây 2.99 (81c8357) — trùng từng dòng với tệp `v2` đã đo ở 2.99 R5 (`diff` rỗng), 2 457 dòng.
- WAV (324 tệp):
  - hồi quy cũ của 2.99 R5: `voice-wavgen` (33) · câu xe `wav-car` (20) · quét tốc độ `wav-sweep` (20) · YouTube `wav-yt` (21);
  - `scripts/voice/label-wavgen.sh <dir> nhan|ten|lenh` (28 + 21 + 14);
  - mới: `wav-app` 26 câu × 5 tốc độ (150/175/220/250/280, `say -v Linh`) — 12 câu viết tên Anh (`a01–a12`) + 14 câu viết theo âm Việt (`v01–v14`: *quây · uây · guây · gu gồ máp · việt máp · spô ti phai · zing em pê ba*);
  - mới: `wav-mix` 11 câu × 2 tốc độ (175/220) — phần tiếng Việt giọng Linh, **tên app giọng Anh Samantha** ghép liền (Linh đọc *"Waze"* ra *"HOA" / "LOA E"* — không phải cách người Việt nói tên ấy);
  - mới: `wav-live` 5 câu *"VietMap Live / việt máp lay / máp lay"* × 3 tốc độ (canh luật tiền tố).
- Lệnh: `<venv>/bin/python -I scripts/voice/hotword-matrix.py --model <model> --wav … --ref ship none ship=<ship> c_waze=… c_gm=… c_vm4=… c_all4=…`.

## 1. Mô hình in gì cho từng tên (tệp ship, 309 WAV mới + cũ) [ĐO host]

| App | Chuỗi mô hình in (số lượt) | Tầng chữ hôm nay hiểu? [SUY `VoiceAppTargets`/`VoiceTailClause`] |
|---|---|---|
| **Waze** (giọng Anh) | `WAYS` 10 · `WAY` 1 (cùng dạng log xe 10/10 *"bằng way"*) · *"mở Waze"* trần ⇒ `MOIS`/`MOYS` (động từ rụng) | **Không** — `way`/`ways` không có trong `APP_TARGETS` ⇒ *"bằng way"* bị nuốt vào điểm đến |
| Waze (đọc *"quây"*) | `QUÂY` 11 · `QUÊ` 6 (*"quê"* là từ thường ⇒ không nhận) | `quây` ⇒ `quay` ✓ |
| Waze (đọc *"uây"*) | `HƠI` · `HAY` · `LAY` · `WAY` | không |
| **Google Maps** | `GOOGLE MAP` 29 (cả giọng Anh lẫn *"gu gồ máp"*) · nhưng *"mở Google Maps"* (Linh) với tệp ship ⇒ `MỞ GU` / `MỞ GU MÁT` / `MỞ GUP` 5/5 — **tệp ship làm hỏng** (không hotword: `MỞ GOOGLE MAP` 4/5) | `google map` ✓ · `gu` ✗ |
| **VietMap** | `VIETMA` 12 · `VIET MAP` 2 · `VIETT` 2 · `VIETMAP` (khi có dòng `… VIETMAP LIVE`) | sau *"bằng"* ✓ (rụng âm / mờ) · sau *"mở"* `VIETMA` ✗ (chỉ khớp chính xác) |
| **Spotify** | `SPOTY FILE` 12 · `SPOTIFI` 5 · `SPODYFI(T)` 4 · `SCOTY/SCOPY/STY FILE` | sau *"bằng"*: `spotifi` ✓ (mờ, lệch 1) · `spoty file` ✗ · sau *"mở"* ✗ |
| **Zing MP3** | rác: `DÃY BA` · `R BA` · `INTER BA` · `MOING MYTROY` · `MỞ YO` | ✗ — không cách viết nào ổn định |

Câu kiểm `NAV-MULTI-PROVIDER-ALL3` [ĐO host]:
- viết tên Anh, giọng Linh: `… BẰNG GOOGLE MAP MAP VÀ WA / WAVE / LOA / LABER` — VietMap rụng thành `MAP`;
- tên Anh giọng Samantha (`m05`): `… GOOGLE MAP VÀ WAYS` — rụng cả *"bằng"* và VietMap;
- đọc âm Việt (`v05` *"bằng gu gồ máp, việt máp, và quây"*): `… BẰNG GOOGLE MAP VIỆT MAP VÀ QUÂY` ở 175 · 220 · 250 (150: rụng *"việt máp"*; 280: rụng với tệp ship) ⇒ **WAV harness cho câu kiểm nên dùng dạng `v05` tốc độ 220**.

## 2. Ứng viên hotword — ma trận lên/tụt so với ship [ĐO host]

Mỗi ứng viên = tệp ship + `MỞ/ĐƯA/BẬT <TÊN>` (đúng khuôn bộ sinh tạo ra khi chuyển tên từ `NO_VI_FORM` sang `ACCENTED`), có tính luật `SherpaHotwords.dropPrefixes`.

| Ứng viên | Tụt (324 WAV) | Lên | Ghi chú |
|---|---|---|---|
| `c_waze` + `WAZE` | 0 | 3 (`m02r175` · `m02r220` · `m11r175`: `M WAYS VÀO Ô …` ⇒ `MỞ WAZE VÀO Ô …` — giữ được động từ + ô) | `m11r220` vẫn `M WAYS` |
| `c_way` + `WAY` | 0 | 0 | bias `WAY` vô ích ⇒ `way`/`ways` chỉ cần ở tầng chữ |
| `c_gm` + `GOOGLE MAPS` | 0 | 6 (`a09` ×5 `MỞ GU…` ⇒ `MỞ GOOGLE MAP(S)`, `v09r150`) | `MỞ GOOGLE MAP` sẽ bị `dropPrefixes` bỏ (tiền tố của `… MAPS`) ⇒ chỉ đo dòng `MAPS` |
| `c_vm2` + `VIỆT MÁP`/`VIETMAP`, bỏ MỌI dòng `…LAY/LIVE` | 0 chữ · **3 tụt nghĩa** | 7 | *"mở máp lay"* `MÁP LIVE` ⇒ `MAP LINE` (mất app) ⇒ **BÁC** |
| `c_vm4` + `VIETMAP`, chỉ bỏ dòng `VIETMAP LIVE/LAY` | 0 nghĩa (9 đổi chữ `VIETMAP LIVE` ⇒ `VIETMAP LINE/LAI`, đầu câu vẫn `VIETMAP`) | 7 (`a10r220/250`, `v10` ×5: `MỞ VIETMA` ⇒ `MỞ VIETMAP`) | `MỞ VIETMAP` phải không là tiền tố ⇒ hai dòng `vietmap live/lay` rời `ACCENTED` |
| `c_spot` + `SPOTIFY` | 0 | 0 | vô ích |
| `c_zing` + `ZING` | 0 | 0 | vô ích |
| `bang` + `BẰNG WAZE/GOOGLE MAP/VIỆT MÁP/QUÂY` | 0 chữ | 0 | đổi qua lại vô hướng (`BÀNG LAY`↔`LAY`) ⇒ bác, ngoài khuôn bộ sinh |
| **`c_all4` = c_waze + c_gm + c_vm4** | **0** trên 151 WAV hồi quy 2.99 + 63 `label-wavgen` | **16** | `createStream` trung vị 7,7 ms vs ship 7,5 ms (15 lượt) |

## 3. Đề xuất sửa (đều có số ở §2)

`SherpaSpokenWords.kt`:
- `"waze"`: `NO_VI_FORM` ⇒ `ACCENTED` `"waze" to "waze"` (+3 / 0).
- `"google maps"`: `NO_VI_FORM` ⇒ `ACCENTED` `"google maps" to "google maps"`; `"google map"` giữ `NO_VI_FORM` (dòng của nó bị luật tiền tố bỏ) (+6 / 0).
- `"vietmap"`: `NO_VI_FORM` ⇒ `ACCENTED` `"vietmap" to "vietmap"`; `"vietmap live"` và `"vietmap lay"` rời `ACCENTED` sang `NO_VI_FORM` (+7 / 0 nghĩa; giữ `việt máp lay/live` · `máp lay/live`).
- Spotify · Zing: **không đổi** hotword (0 / 0).

`VoiceSynonyms.APP_TARGETS` (tầng chữ; khai thêm vào `NO_VI_FORM` để bài canh phủ xanh):
- Waze: thêm `"way"`, `"ways"` — [ĐO] xe 10/10 *"bằng way"* + host `WAYS` 10/10 lượt giọng Anh; không phải tiếng Việt ⇒ không cướp câu (luật 3 KDoc `VoiceSynonyms`). **Không** thêm `"que"` (*"quê"* từ thường).
- Spotify: thêm `"spoty file"`, `"spotifi"` — [ĐO host TTS] 17/22 lượt; không phải tiếng Việt. `spodyfi` đã được phép so mờ sau *"bằng"* lo [SUY `VoiceNameFuzzy`].
- Zing: không thêm (chuỗi không ổn định) — đường generic là **dạy tên app** (2.91).

## 4. Còn mở

- [CHƯA BIẾT] giọng owner thật nói *"Waze"*: log xe có `way` và `quay/quây` — mới đủ cho tầng chữ; số lên/tụt hotword cần một buổi xe (nói *"mở Waze vào ô một"* · *"dẫn đường đến chợ Bến Thành bằng Waze"* ×3).
- [SUY, cần test `:core`] `MỞ VIETMAP LINE` / `ĐƯA VIETMAP LAI VÀO Ô HAI` vẫn ra `OpenApp(VietMap[, ô 2])` (khớp đầu `vietmap`, đuôi qua `slotAt`).
