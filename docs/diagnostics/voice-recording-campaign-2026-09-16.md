# Đợt thu giọng THẬT cho Kachi — 30 câu, ai cũng đọc được (2026-09-16)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-16 · **Mục đích**: đợt thu giọng THẬT cho Kachi — 30 câu, ai cũng đọc được, không cần cắm cáp.

> **Việc của anh em đọc:** bật ghi âm trên điện thoại, đọc 30 câu ở dưới, gửi file về. Hết. Không cần cắm cáp,
> không gõ lệnh, không cần biết adb là gì.
>
> **Vì sao cần:** máy đang tự sinh hàng nghìn câu bằng giọng máy để dò lỗi
> (`docs/diagnostics/voice-mishear-2026-09-16.md`). Giọng máy đọc **quá chuẩn** — nó không nuốt chữ, không có
> tiếng điều hoà, không có giọng vùng miền thật. Nên mọi con số ở đó chỉ nói về *model + từ vựng*, **không**
> nói được Kachi nghe anh em thế nào. Chỉ giọng người mới trả lời được câu đó.

---

## 1. Chuẩn bị (2 phút)

1. Mở app ghi âm có sẵn trên máy (iPhone: *Ghi âm* / Voice Memos · Android: *Ghi âm* / Recorder).
2. Cầm điện thoại cách miệng **một gang tay** (~20 cm). Đừng để sát miệng.
3. Đọc **giọng bình thường của mình** — người Nam cứ nói giọng Nam, người Trung cứ giọng Trung. Đừng cố nói
   "giọng phổ thông": cái ta cần đo chính là giọng thật.

Mỗi câu ghi **một file riêng**, hoặc ghi liền một mạch cũng được (nói số thứ tự trước mỗi câu, ví dụ
*"câu bảy"* rồi mới đọc) — bên kỹ thuật tự cắt.

---

## 2. Ba lượt đọc — cùng 30 câu, khác điều kiện

| Lượt | Điều kiện | Vì sao cần |
|---|---|---|
| **A. bình thường** | xe đứng yên, không nhạc, nói tốc độ thường | mốc so sánh |
| **B. nhanh** | đọc nhanh như lúc đang lái vội, nuốt chữ tự nhiên | tester báo *"phải nói chậm mới nhận"* — lượt này là thứ chứng minh hoặc bác bỏ |
| **C. có nhạc/ồn** | bật nhạc trên xe mức nghe bình thường, hoặc mở cửa sổ khi đang chạy | trên xe thật lúc nào cũng có nền ồn; đo trong phòng yên là đo một thứ không tồn tại |

Ai bận thì làm **lượt A và B** trước, lượt C để sau. Ba người ba vùng miền còn hơn một người ba lượt.

---

## 3. Đặt tên file

```
<tên>-<vùng>-<lượt>-<số câu>.m4a
```

- `<tên>`: tên không dấu, viết liền — `nam1`, `bac1`, `trung1` (bí danh, không cần tên thật)
- `<vùng>`: `bac` · `trung` · `nam`
- `<lượt>`: `a` (bình thường) · `b` (nhanh) · `c` (có nhạc)
- `<số câu>`: 2 chữ số, đúng số thứ tự trong bảng dưới — `01` … `30`

Ví dụ: `nam1-nam-b-07.m4a` = người nam1, giọng Nam, đọc nhanh, câu số 7.

Ghi liền một mạch thì đặt `nam1-nam-b-all.m4a` là đủ.

Đuôi file `.m4a` · `.wav` · `.mp3` đều nhận — bên kỹ thuật tự đổi định dạng.

---

## 4. 30 câu cần đọc

Đọc **đúng như viết**. Câu nào thấy ngượng mồm thì cứ đọc theo cách mình hay nói và **nhắn lại** là đã đổi thế
nào — chính chỗ đó là dữ liệu quý.

| # | Câu | Vì sao có mặt |
|---|---|---|
| 01 | lọc ngay | tester 1.66 báo **không hiểu** |
| 02 | bật lọc bụi | tester 1.66 báo **không hiểu** |
| 03 | điều hoà | tester 1.66 báo **không hiểu** |
| 04 | mở Google Map | tester 1.66 báo **không hiểu** (nói *"Google"* thì được) |
| 05 | bật đèn đọc | tester báo **nghe được** — câu đối chứng |
| 06 | mở YouTube | tester báo **nghe được** — câu đối chứng |
| 07 | mở máy lạnh | cách nói miền Nam của câu 03 |
| 08 | bật điều hoà hai mươi hai độ | số đọc bằng chữ |
| 09 | nhiệt độ hai mươi bốn độ | số đọc bằng chữ, câu của ma trận cũ |
| 10 | tăng gió | lệnh một nấc, rất ngắn |
| 11 | giảm âm lượng | lệnh một nấc, rất ngắn |
| 12 | mở kính bên lái | một cửa kính cụ thể |
| 13 | hạ kiếng trước trái | cách nói miền Nam của câu 12 |
| 14 | đóng hết kính | gói lệnh |
| 15 | mở hết kính giùm | gói lệnh + tiểu từ miền Nam |
| 16 | xem pin | câu hay dùng nhất |
| 17 | pin còn bao nhiêu | cùng ý, hỏi kiểu khác |
| 18 | còn đi được bao xa | cùng ý, hỏi kiểu khác nữa |
| 19 | xem áp suất lốp trước trái | câu dài, nhiều từ giống nhau |
| 20 | bật ghế sưởi | nút hay dùng mùa lạnh |
| 21 | mở cốp sau | cách nói phổ thông |
| 22 | mở cửa hậu | cách nói miền Nam của câu 21 |
| 23 | phát nhạc | lệnh nhạc |
| 24 | dừng nhạc | lệnh nhạc — bản cũ nghe thành *"rừng nhạc"* |
| 25 | bài tiếp theo | lệnh nhạc |
| 26 | dẫn đường đến chợ Bến Thành | dẫn đường + tên riêng |
| 27 | mở quây | *Waze* đọc theo âm Việt |
| 28 | mở du túp | *YouTube* đọc theo âm Việt |
| 29 | chế độ lái thể thao | câu mà hotword từng làm mất đuôi |
| 30 | hôm nay trời đẹp quá | **không phải lệnh** — Kachi phải im, không được tự bật gì |

---

## 5. Gửi về

Zip cả thư mục rồi gửi vào nhóm, kèm **một dòng**: tên, vùng miền, đọc ở đâu (trong xe / trong phòng), có bật
nhạc không. Không cần viết gì thêm.

---

## 6. Phần của bên kỹ thuật (anh em đọc không cần quan tâm)

### 6.1 Đổi về đúng khuôn WAV

`VoiceWavProbe` **cố ý không có bộ chuyển đổi** — chỉ nhận WAV PCM 16-bit · 1 kênh · 16 kHz (xem KDoc
`scripts/emulator/voice-wavgen.sh`). Nên bước đầu luôn là:

```bash
mkdir -p /tmp/kachi-voice-real
afconvert -f WAVE -d LEI16@16000 -c 1 nam1-nam-b-07.m4a /tmp/kachi-voice-real/r07.wav
# ffmpeg cũng được: ffmpeg -i in.m4a -ac 1 -ar 16000 -sample_fmt s16 out.wav
```

Rồi viết `cases.tsv` (id<TAB>câu) đúng như `voice-wavgen.sh` sinh ra, để hai công cụ dưới đọc được:

```
r07	mở máy lạnh
r24	dừng nhạc
```

### 6.2 Đo trên HOST (không cần xe, không cần máy ảo)

```bash
/tmp/sherpa-venv/bin/python scripts/voice/hotword-matrix.py --model <MODEL_DIR> \
    --wav /tmp/kachi-voice-real none core/build/hotwords/hotwords-phrases.txt
```

Ra ngay bảng ✓/✗ theo câu, so được cột *không hotword* với cột *tệp đang ship*. Đây là chỗ **đầu tiên** phải
nhìn: nếu giọng thật hỏng ở đúng những câu giọng máy cũng hỏng ⇒ lỗi nằm ở model/từ vựng, chưa cần lên xe.

### 6.3 Đo qua chính app (máy ảo hoặc xe)

```bash
adb push /tmp/kachi-voice-real/r07.wav /sdcard/Android/data/<pkg>/files/wavin/r07.wav
adb shell am broadcast … --es cmd wav --es path /sdcard/Android/data/<pkg>/files/wavin/r07.wav
```

Đường `--es cmd wav` đi **đúng** đường mà phiên mic đi (`scripts/emulator/voice-e2e.sh` §T2), nên nó đo được
cả khúc sau: nhận dạng → ý định → câu trả lời. Chạy hàng loạt thì dùng thẳng
`scripts/emulator/voice-e2e.sh --only wav --wavdir /tmp/kachi-voice-real`.

### 6.4 Nhập kết quả về đâu

Câu nào giọng thật nghe sai mà giọng máy nghe đúng ⇒ thêm vào `scripts/voice/data/nouns.tsv` /
`apps.tsv` đúng cách nói người đó đã dùng, rồi chạy lại `gen-variants.py` + `mishear-table.py`. Đó là vòng
khép kín: **giọng thật chỉ ra chỗ hỏng → corpus máy đo lại chỗ đó hàng trăm lần → sửa từ vựng/hotword**.
