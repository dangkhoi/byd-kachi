# Runbook: Hey Kachi — validate + test trên xe

> **Trạng thái**: Current · **Ngày**: 2026-09-19 · **Mục đích**: Quy trình chốt "Hey Kachi" có DÙNG ĐƯỢC trên xe không — validate rẻ TRƯỚC, rồi mới host/nối OTA. · **App**: cần **1.79 (vc80)** trở lên (lớp Android wake ship 1.77; xe đang 1.76 ⇒ OTA trước).

## 0. Hiện trạng (đọc trước khi test)
- **Đã xong + nối dây**: FGS `VoiceWakeService`, `VoiceWakeListener` (mic→RMS→controller→KWS), adapter `VoiceWakeKws`, công tắc Cài đặt › Hệ thống › **"Hey Kachi"** (mặc định TẮT), lõi an toàn `VoiceLoadGuard`/`VoiceWakeGate`/`VoiceWakeController`, tự bật lúc boot.
- **Chưa dùng được**: `VoiceWakeKws.build` **degrade về chỉ-RMS** (không bắt cụm gọi) vì thiếu (a) 4 tệp model ở `filesDir/kws/` (`encoder.int8.onnx`·`decoder.int8.onnx`·`joiner.int8.onnx`·`tokens.txt`) + (b) `keywords.txt` = "Hey Kachi" ĐÃ tokenize. **Chưa nối đường TẢI** model (khác model NGHE/Piper).

## ⚠ Hai rủi ro phải chốt bằng ĐO (không đoán)
1. **Model `sherpa-onnx-kws-zipformer-gigaspeech-3.3M` là TIẾNG ANH.** "Kachi" là từ tự chế; "Hey Kachi" giọng Việt → **[CHƯA BIẾT] bắt nổi không**. → Phase 0 phải trả lời trước.
2. **Xe bão hoà CPU (load 12-14** GMaps-in-slot+VietMap+cdr+surfaceflinger). KWS streaming chạy nền, mà `VoiceLoadGuard` **tự treo bộ nghe khi tải cao** ⇒ có thể "đang gọi thì nó ngủ". → Phase 2 đo.

---

## PHASE 0 — VALIDATE off-car TRƯỚC (rẻ nhất, làm trước khi đổ công host)
**Mục tiêu**: gigaspeech KWS có bắt "Hey Kachi" giọng Việt không.
1. **Thu mẫu**: owner thu **10 mẫu "Hey Kachi"** (giọng thật, 3 mức ồn: im/nhạc/đang chạy) → WAV 16k mono. Cách nhanh: bật test-bridge `wav` hoặc ghi bằng app ghi âm rồi copy ra `/sdcard/Download/heykachi-*.wav`.
2. **Kachi (off-car) tải model** gigaspeech-3.3M (Apache-2.0, HuggingFace k2-fsa) + tokenize:
   ```
   # tokenize cụm gọi theo tokens.txt của model (BPE)
   sherpa-onnx-cli text2token --tokens tokens.txt --tokens-type bpe --bpe-model bpe.model \
       <(echo "HEY KACHI") keywords.txt
   ```
   (Kachi làm bước này khi có model; chuỗi token ghim vào `keywords.txt` + `VoiceWakePhrase`.)
3. **Chạy `KeywordSpotter` off-car** trên 10 mẫu → đếm **hit / 10** + thử 30 phút tiếng nói THƯỜNG (không gọi) đếm **false-accept**.
4. **Cổng quyết định**:
   - hit ≥ **8/10** ở mức im + ≥ **6/10** khi có nhạc, false-accept ≤ **1 / 30 phút** ⇒ **ĐI TIẾP** (Phase 1).
   - Kém hơn ⇒ **KHÔNG host**: đổi cụm gọi (từ có âm tiếng-Anh rõ hơn, vd "Hey Google-like"), hoặc đổi model, hoặc **gác** (dùng phím vô-lăng).

---

## PHASE 1 — Đưa model lên xe (chỉ khi Phase 0 đạt)
⚠ **Ràng buộc**: `filesDir/kws/` là `/data/data/com.byd.launcher/files/kws/` — **không `adb push` thẳng vào được** trên bản release (không root, data dir của app khác). Hai đường:
- **(A) Nối OTA tải** (production): Kachi thêm entry KWS vào `VoiceModelStore`/manifest (ghim sha256 từng tệp), owner **host** 5 tệp (`encoder/decoder/joiner.int8.onnx`+`tokens.txt`+`keywords.txt`) lên repo OTA `dangkhoi/byd-kachi` (thư mục `voice/kws/`), app tự tải về `filesDir/kws/`. **Việc của Kachi** (chưa làm — chờ Phase 0 đạt).
- **(B) Side-load nhanh để test** (chỉ bản `vehicleTest` — debuggable): cài APK `vehicleTest` rồi
  ```
  adb push encoder.int8.onnx decoder.int8.onnx joiner.int8.onnx tokens.txt keywords.txt /sdcard/kws/
  adb shell run-as com.byd.launcher mkdir -p files/kws
  adb shell run-as com.byd.launcher sh -c 'cp /sdcard/kws/* files/kws/'
  ```
  (bản release **không** `run-as` được ⇒ chỉ dùng đường A cho bản chính thức.)
- ⚠ Tên tệp phải ĐÚNG (`encoder.int8.onnx`…) — model tải về tên dài (`encoder-epoch-12-...int8.onnx`) ⇒ **đổi tên**.

---

## PHASE 2 — Bật + đo trên xe
1. Cài đặt › Hệ thống › **Hey Kachi** = BẬT.
2. **Xác nhận KWS chạy (không phải chỉ-RMS)**:
   ```
   python3 /tmp/adb_raw.py <car-ip> 5555 'logcat -d | grep -iE "VoiceWakeKws|thiếu model KWS|chỉ-RMS|KWS"'
   ```
   Thấy dựng KWS (không thấy "chạy chế độ chỉ-RMS") ⇒ OK.
3. **Đo NHẬN CỤM GỌI**: nói "Hey Kachi" **20 lần** ở 3 mức ồn → đếm lần **overlay voice bật lên** (log `EXTRA_START_VOICE`/`KachiHome...voice.start`). Ghi hit/20 mỗi mức.
4. **Đo CPU + LOAD-GUARD** (rủi ro #2):
   - Baseline: wake TẮT → `top` lấy %CPU launcher + `uptime`.
   - Wake BẬT (im): `top` lại → chênh %CPU = giá KWS nền.
   - Khi xe tải cao (mở GMaps-in-slot + VietMap): `logcat | grep -iE "VoiceLoadGuard|suspend|treo"` → xem bộ nghe có bị **treo** không, và lúc treo gọi "Hey Kachi" có ăn không (dự kiến KHÔNG — đó là điểm chốt).
5. **False-accept**: để yên 30 phút nói chuyện thường / nghe nhạc → đếm số lần overlay tự bật oan.

## PHASE 3 — Cổng nghiệm thu (quyết giữ/bỏ)
| Tiêu chí | Ngưỡng giữ |
|---|---|
| Nhận cụm gọi (im) | ≥ 16/20 |
| Nhận cụm gọi (có nhạc) | ≥ 12/20 |
| False-accept | ≤ 1 / 30 phút |
| CPU nền KWS (xe rảnh) | ≤ ~8% 1 lõi |
| Khi tải cao | chấp nhận load-guard treo, NHƯNG phải nói rõ cho owner |
- Đạt hết ⇒ giữ Hey Kachi, nối OTA (đường A), làm T5 (UI chọn preset câu gọi).
- Không đạt ⇒ **phím vô-lăng → trợ lý là đường gọi chính** (đã tin cậy sau 1.79 sửa gốc crash Piper); Hey Kachi gác tới khi giảm tải CPU (bớt GMaps-in-slot+VietMap).

## Đường lùi / lưu ý
- Hey Kachi **mặc định TẮT** ⇒ không bật thì 0 ảnh hưởng.
- Phím vô-lăng gọi trợ lý vẫn là đường chắc nhất trên xe tải nặng.
- Client ADB thô: `/tmp/adb_raw.py` (chưa lưu repo — cân nhắc `scripts/vehicle/kachi/adb_raw.py`).
- Doc liên quan: `oncar-piper-crash-binding-2026-09-18.md` (gốc crash Piper — nay đã cô lập `:tts`), spec `docs/specs/kachi-wake-word.html`.
