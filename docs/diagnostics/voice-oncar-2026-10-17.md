# Hey Kachi "lúc lên lúc không" — chuyến xe 17/10 (3.1.11) · `WAKE-INTERMITTENT-1017`

Owner 17/10, xe chạy 3.1.11 (`dumpsys package` đọc trên xe: `versionName=3.1.11`), YouTube/YT Music phát lúc có lúc không.
Nguồn: logcat tiến trình `:wake` trong chuyến (≈ 11:05–12:14) + đọc CHỈ ĐỌC trên xe lúc 12:04 (`/proc/<pid>/stat`, `/proc/<pid>/task/<tid>/stat`).
Không ghi lại lời nói trong xe ở đây; chỉ ghi chữ ASR của các cửa sổ câu gọi (ngắn, ≤ 3 từ).

## 1. Dòng thời gian các lần gọi [ĐO log `:wake`]

Chữ của mọi cửa sổ giải mã wake **đã có sẵn** trong log: `KachiVoiceRec: sherpa ra: "…"` trên luồng `kachi-wake` (bộ đếm `giai_ma` khớp
đúng số dòng). Nhờ đó tách được từng lần gọi:

| Giờ | Chữ cửa sổ | Kết quả | Gốc |
|---|---|---|---|
| 11:57:47 | `hay` → `hay ca chê` | nổ | — |
| 11:58:57 | `hay ca trên` | **không nổ** | bộ khớp: đuôi "chi" nghe thành `trên` |
| 11:59:01 | `hai ca trên` | **không nổ** | như trên; cửa sổ kế cách 4–5 s vì ngân sách nợ (§3) |
| 11:59:06 | `chi` | không nổ | đuôi đứng một mình; dòng `ngan_sach … du=-1943` |
| 11:59:30 | — | người lái bấm phím (`lối vào=phim`) | |
| 12:00:25 | `hay ca` | nổ | |
| 12:05:55–57 | `hai ca trên` → `chi` | **không nổ** | như 11:58 |
| 12:06:07 | — | bấm phím | |
| 12:11:30 | chuyện trong xe có chữ *"khách"* | **NỔ NHẦM** ⇒ mở phiên lệnh | bộ khớp luật (a): `khach` = `kh`+`ch` |

⇒ Trong chuyến này mọi lần gọi nhận ra được đều **TỚI** ASR; 3 lần trượt là do **bộ khớp chữ**, không phải cổng tiếng người.

## 2. Ba nghi vấn, từng mức bằng chứng

### 2a. Cổng tiếng người (Silero 0,45 / hold 1,2 s) — **không phải gốc** [ĐO host]
`bo_khong_tieng_nguoi` tăng lẻ tẻ (+3/+2/+1/+4) không gắn được với lần gọi nào; các lần gọi nhận ra được đều có lượt giải mã.
Đo cổng trên host (`scripts/voice/wake-harness/gate-sweep.py`: dựng lại đúng nhánh trước `rec.decodeAll`, Silero asset thật qua onnxruntime,
60 câu gọi TTS × cabin yên / nhạc không lời / nhạc có lời): ngưỡng 0,45·0,35·0,25·0,15 × hold 1 200·1 600·2 000 ms ở giọng 0,12 / 0,03 /
0,015 và SNR +3,5 · 0 · −3 dB ⇒ **0 câu gọi bị cổng tiếng người bỏ** ở mọi ô. Hạ ngưỡng chỉ thêm lượt giải mã nhạc có lời (45 s ở −3 dB:
83 lượt @0,45 → 89 @0,25). ⇒ **Giữ 0,45 / 1 200 ms** (chọn theo số đo, không đổi).

### 2b. Cổng RMS cửa sổ 0,010 — **lỗ thật, im lặng** [ĐO host · SUY cho xe]
Cùng bản dựng: giọng RMS 0,010 ⇒ chỉ **25/60** câu gọi tới ASR; 0,007 ⇒ **0/60** — và lượt bị bỏ ở cổng này không có bộ đếm nào.
[ĐO xe] micro phiên lệnh `rms 423–956/32767` (0,013–0,029) trên cả khúc 4,8–7,2 s ⇒ cửa sổ 1,6 s của câu gọi nói nhẹ nằm ngay quanh 0,010
[SUY]. Quét cổng: 0,007 vẫn mất ở giọng 0,007 (26/60); **0,005** và 0,0037 (sàn cổng khung) cho 60/60 ở mọi mức ⇒ chọn **0,005**.

### 2c. CPU phiên lệnh rò vào ngân sách — **bác bỏ** [ĐO xe]
- Tiến trình `:wake` (sống ~60 phút): tổng CPU 398,5 s; ngân sách đã tính 304,5 s. Theo luồng: `kachi-wake` (luồng nghe; gồm 45 s nạp
  mô hình lúc 11:05, không tính vào ngân sách) 175 s · **9 luồng onnxruntime** của recognizer (tên `kachi-wake`) 201 s (3 luồng encoder
  123 s, 6 luồng decoder/joiner 78 s) · mọi luồng khác + luồng đã chết (phiên lệnh) ≈ 22 s.
- Phần không tính (94 s) ≈ nạp mô hình + giải mã phiên lệnh ⇒ không có dấu rò. Theo mã: mỗi lần tính là hiệu CPU quanh đúng một lời gọi
  engine, chỉ chạy khi bộ nghe giữ micro; phiên lệnh không chạy lúc ấy. Nợ ở 11:59:06 xảy ra **52 s sau** khi phiên lệnh trước đã kết thúc.
- Đổi sang CPU-của-luồng sẽ **sai**: bỏ sót hơn nửa giá thật (luồng onnx). Giữ đồng hồ cả tiến trình; khoá hình dạng bằng bài canh.
- Phát hiện kèm [ĐO]: giá một lượt giải mã wake trên xe ≈ **2,2–4,4 s CPU** (11:06–11:20, có người nói: 13,3–17,7 s/phút cho 4–6 lượt),
  gấp 2–3 lần số 1 350 ms mà `VoiceWakeCpuBudget.BURST_WINDOW_MS` dựa vào ("xô đầy ≈ 3 lượt") ⇒ xô 4 000 ms thực tế chỉ ~1 lượt. 6 luồng
  decoder/joiner 78 s là chi phí quay chờ của onnxruntime trên mô hình rất nhỏ [SUY]. **Không sửa ở bản này** — việc của owner (§5).

## 3. Sửa (generic, đúng tầng)

| # | Tầng | Thay đổi | Tệp |
|---|---|---|---|
| 1 | Cổng RMS trước ASR | `RMS_GATE` 0,010 → **0,005** (số đo §2b) + bộ đếm `skippedQuiet` = cổng RMS bỏ lúc cổng tiếng người đang mở; dòng phút thêm `bo_nho=` | `VoiceWakeAsr.kt` · `VoiceWakeListener.kt` · `TestBridgeKws.kt` (`skipped_quiet`) |
| 2 | Bộ khớp — đuôi `tr` | luật (h): đúng ba từ *lời gọi đầu + head + "trên"* (cặp `tr`↔`ch` đã khai ở `VoicePhoneticConfusions`); "trên" KHÔNG vào `TAIL` (chữ thường của câu lệnh) | `WakeAsrMatcher.kt` |
| 3 | Bộ khớp — `kh` | luật (a): từ một-chữ bắt đầu `kh` không còn được "nổ luôn" như k-start (cùng lẽ luật (g) loại "kha") | `WakeAsrMatcher.kt` |
| 4 | Đo | `gate-sweep.py` (cổng, `DUMP=` đặc trưng, `ASR_MODEL=`+`TEXTS=` chữ ASR host) · `mix.py` thêm `SPEECH_RMS`/`MUSIC_RMS` (mặc định = bộ cũ) | `scripts/voice/wake-harness/` |

Không thêm dòng log chữ ASR thứ hai — dòng `sherpa ra` đã có (§1).

## 4. Số đo trước/sau [ĐO host: sherpa-onnx 1.13.8 + `zipformer-vi-int8` sha khớp catalog, cùng tham số `openFree`; 60 câu gọi + 63 câu thường TTS `Linh`]

Câu gọi nổ (/60) — trước = cổng 0,010 + bộ khớp HEAD · sau = 0,005 + bộ khớp mới. Ngân sách không áp (cô lập cổng).

| Mức | cabin yên | nhạc không lời | nhạc có lời | câu thường nổ nhầm | lượt giải mã/câu thường |
|---|---|---|---|---|---|
| giọng 0,12 (mức harness) | 50 → 50 | 53 → 53 | 42 → 42 | 0 → 0 | 2,79 → 2,79 |
| giọng 0,03, nhạc −3 dB | 48 → 48 | 55 → 55 | 10 → 10 | 0 → 0 | 2,65 → 2,65 |
| giọng 0,010 | **22 → 45** | **21 → 39** | **20 → 35** | 1 → 1 ¹ | 1,51 → 2,51 |
| giọng 0,007 | **0 → 37** | **0 → 35** | **0 → 40** | 0 → 1 ¹ | 0,00 → 2,24 |

¹ cùng một câu thường dựng-gần-giống (*"con cá chìu"*, ASR ra *"con cá chì"* = biến thể câu gọi trong golden 23/09) — đã nổ ở bản cũ; không chặn
được mà không mất câu gọi thật.

- Bài khoá chữ harness 16/10 (`WakeAsrHarnessTextsTest`): câu gọi ≥ 49/57, câu thường 0/60 — xanh. `WakeAsrGoldenTest` xanh.
- Chữ ASR wake của chuyến 17/10 qua bộ khớp (cục bộ, không đưa vào repo): chữ câu gọi khác nhau nổ 2 → 4 (`hay ca`, `hay ca chê` + `hay ca trên`, `hai ca trên`), nổ nhầm 1 → 0 (103 chữ khác nhau).
- CPU `:wake` lúc nghỉ: không đổi (khung im không tới engine; cổng RMS chỉ xét sau `feed`). Giá thêm: tiếng người nhỏ / nhạc có lời nhỏ
  được giải mã (45 s nhạc có lời mức 0,008: 0 → 2 lượt), vẫn dưới ngân sách 25 %.

Bài mới: `VoiceWakeGateFixtureTest` (40 câu gọi, đặc trưng ghi bằng `gate-sweep.py DUMP=`, phát lại qua đúng `VoiceWakeSpeechGate` +
`RMS_GATE`; đỏ khi trả 0,010) · `VoiceWakeBudgetChargeContractTest` (mỗi lần tính bọc đúng một lời gọi engine; đỏ khi bọc thêm `handOff`) ·
`WakeAsrMatcherTest` hai ca mới (đỏ trên bộ khớp HEAD).

⚠ Mức bằng chứng: giọng TTS + nhạc tổng hợp + ASR host ≠ micro xe; số tuyệt đối chỉ để so trước/sau trên cùng bộ.

## 5. Còn mở

- 🚗 Chuyến sau: đọc `bo_nho=` trong dòng `ngan_sach` mỗi phút (≠ 0 ⇒ còn câu nói nhỏ hơn 0,005) và `sherpa ra` của các lần gọi.
- Owner chọn: ngân sách dựa trên giá 1 350 ms/lượt nhưng xe đo 2,2–4,4 s ⇒ xô đầy chỉ ~1 lượt giải mã; lựa chọn (a) xô theo giá đo
  (giữ DUTY 0,25 dài hạn, tăng đỉnh ngắn), (b) giảm giá thật — luồng decoder/joiner riêng ít luồng hơn (đổi `VoiceEngine`, ảnh hưởng phiên lệnh).

## 6. Giá CPU một lượt giải mã wake — `WAKE-DECODE-COST` (3.1.12, chưa bump)

Tiếp §2c/§5(b). Gốc [ĐO host] — worker intra-op của onnxruntime **quay chờ sau khi `Run()` đã trả**: ở 4 luồng (đúng số
`threadsForDecode()` trên xe 8 lõi), CPU một cửa sổ wake 1,6 s ≈ **8 lần** thời gian tường của chính lượt ấy. Khớp hình dạng
đo trên xe (9 worker 201 s; 6 worker decoder/joiner 78 s cho mô hình rất nhỏ) [SUY: cùng cơ chế, chưa đo lại trên xe].

### 6a. Cấu hình hiện có [ĐO source]
- Wake KHÔNG có engine riêng: `VoiceWakeAsr.create` → `VoiceRecognizer.openFree` → `VoiceEngine.recognizer` = **một**
  `OfflineRecognizer` cho cả tiến trình `:wake`, dùng chung với phiên lệnh. `numThreads = threadsForDecode()` (lõi/2, kẹp [2, 4]),
  `provider = "cpu"`. Tách bản wake riêng = thêm ~74 MB RAM (đã ghi ở `VOICE-WAKE-CPU-BUDGET`) ⇒ không làm.
- sherpa-onnx **v1.13.8** (`app/build.gradle.kts`; GitHub API `releases/latest` đọc 17/10: `v1.13.8`, phát hành 2026-09-10 ⇒ vẫn là bản mới
  nhất). `csrc/session.cc` tại tag v1.13.8, `GetSessionOptionsImpl`: `SetIntraOpNumThreads(n)` +
  `SetInterOpNumThreads(n)` cho cả 3 session; chuỗi `provider` dạng `cpu:<tệp>` đọc tệp `khoá=giá trị`, dòng `SessionConfig.<khoá>`
  chuyển nguyên sang `AddConfigEntry`; **không mở được tệp ⇒ `SHERPA_ONNX_EXIT`** (thoát tiến trình). Khoá
  `session.force_spinning_stop` / `session.intra_op.allow_spinning` có trong `libonnxruntime.so` arm64 của AAR v1.13.8 [ĐO `strings`].

### 6b. Số đo [ĐO host M5 Pro: sherpa-onnx 1.13.8 + `zipformer-vi-int8`, tham số `VoiceEngine.build`, `scripts/voice/wake-harness/decode-cost.py`]

Wake = 837 cửa sổ 1,6 s (nhịp 500 ms) của 60 câu gọi + 63 câu thường (bộ `mix.py` mặc định, `clean_*`). Lệnh = 121 câu 3,3–3,6 s +
63 cặp nối ~7 s. CPU = cả tiến trình quanh một lượt + 50 ms sau (phần quay chờ sau `Run()`). Hai lượt chạy, số lượt 1 / lượt 2.

| luồng · chế độ | wake tường ms | wake CPU ms/lượt | lệnh 3,5 s tường | lệnh 7 s tường | lệnh CPU 3,5 s / 7 s | chữ ra so với 4·mặc định |
|---|---|---|---|---|---|---|
| 4 · mặc định (**trước**) | 15,4 / 17,8 | **124 / 132** | 35,5 / 36,3 | 66,9 / 68,0 | 225 / 382 | — |
| 4 · `force_spinning_stop=1` (**chọn**) | 15,1 / 14,8 | **48 / 47** | 34,0 / 34,5 | 66,0 / 64,0 | 106 / 199 | trùng byte (837 + 184) |
| 4 · `allow_spinning=0` | 16,2 / 16,3 | 26 / 25 | 38,9 / 37,3 | 73,9 / 73,2 | 62 / 126 | trùng byte |
| 2 · mặc định | 15,6 | 51 | 41,7 | 72,3 | 107 / 179 | khác 1/837 cửa sổ |
| 2 · `allow_spinning=0` | 16,2 | 22 | 43,9 | 77,1 | 55 / 108 | khác 1/837 |
| 1 | 17,1 | 17 | 46,6 | 94,2 | 46 / 93 | khác 1/837 |

Tỉ lệ (dùng cái này, không dùng số tuyệt đối — host ≠ xe 8 lõi ARM): **chọn** ⇒ CPU/lượt wake × 0,37 (−62 %), tường wake × 0,93–0,98;
lệnh: tường không đổi (−1…−6 %, trong nhiễu), CPU × 0,47–0,52. `allow_spinning=0` rẻ hơn nữa (× 0,20) nhưng lệnh chậm **+7…+10 %**
tường; giảm luồng đổi chữ ra (`hê ca` ↔ `hê ch`) và lệnh 7 s chậm +40 % ở 1 luồng ⇒ cả hai trái luật "không làm chậm phiên lệnh".

### 6c. Sửa
| Tầng | Thay đổi | Tệp |
|---|---|---|
| `:core` danh mục | `SherpaModelCatalog.ORT_SESSION_CONFIG` = `SessionConfig.session.force_spinning_stop=1` (một chỗ khai, KDoc + số đo) + `provider(path)` | `SherpaModelCatalog.kt` |
| app engine | `VoiceOrtConfig.provider(ctx)`: ghi tệp vào `filesDir`, **đọc lại khớp mới trả `cpu:<tệp>`**, hỏng ⇒ `cpu` (hành vi cũ, có log) — tránh `SHERPA_ONNX_EXIT` | `VoiceOrtConfig.kt` (mới) · `VoiceRecognizer.kt` (`provider`, dòng log nạp thêm `ort_cfg=`) |
| ngân sách | GIỮ `DUTY` 0,25 / xô 4 000 ms: [SUY] giá xe 2,2–4,4 s × 0,37 ≈ 0,8–1,6 s/lượt ⇒ xô lại phủ 2,5–5 lượt (≥ 2–3). Ghi lý do ở KDoc `BURST_WINDOW_MS` | `VoiceWakeCpuBudget.kt` (chỉ KDoc) |
| đo | `decode-cost.py` (wake/cmd × luồng × on/stop/off, ghi chữ ra để so byte) | `scripts/voice/wake-harness/` |

Bài: `SherpaModelCatalogTest` (tệp chỉ đúng một dòng `force_spinning_stop=1`, không `allow_spinning`; `provider` rơi về `cpu`) ·
`VoiceDecodeCostWiringContractTest` (recognizer chung lấy provider qua `VoiceOrtConfig`, không còn literal `"cpu"`; số luồng phiên lệnh
không đổi; đọc-lại trước khi trả). Đỏ-khi-gỡ: bản HEAD của `VoiceRecognizer.kt` có `provider = "cpu"` và 0 chỗ `VoiceOrtConfig.provider` ⇒ bài 1 đỏ.

### 6d. Còn mở
- 🚗 Chuyến sau: dòng `KachiVoiceTiming` *"nạp sherpa … ort_cfg=true"* (tệp đã nạp) và giá mỗi lượt trong dòng `ngan_sach` mỗi phút (kỳ vọng
  ≈ 0,8–1,6 s, [SUY]). Còn > 2 s/lượt ⇒ cỡ xô theo giá đo (giữ DUTY 0,25 dài hạn). Đọc `/proc/<pid>/task/*/stat` như §2c để so 201 s.
- Không đụng: TTS `SherpaTtsCatalog.NUM_THREADS = 2` (tiến trình `:tts`, cùng cơ chế quay chờ [SUY]) — ghi backlog, chưa đo.
