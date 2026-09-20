# RESUME — gói fix voice (hoãn 2026-09-18 20:23 để vào xe trace binding + Hey Kachi)

**Trạng thái**: đã dựng xong DAG chia việc, **bị owner hoãn** để vào xe <car-ip>. Làm tiếp SAU.

## Điểm resume
- **Todo list (9 tasks)** đã tạo — là kế hoạch chính thức.
- **Findings đã push** (`origin/main` 23a6d2f): `docs/diagnostics/oncar-voice-cases-findings-2026-09-18.md` (3 lỗi cảm nhận + parse 53 case) + `oncar-voice-music-vietmap-2026-09-18.md` (music UA + vietmap parser).
- **Chưa implement gì** (working tree sạch phần code voice).

## ⚠ #0 PRIORITY MỚI (tìm ra khi trace on-car tối 18-09) — CHẶN PIPER CRASH
`docs/diagnostics/oncar-piper-crash-binding-2026-09-18.md`: launcher **crash native SIGSEGV trong `SherpaTtsSpeaker.OfflineTts.generate`** (thread KachiSpeak) → chết cả tiến trình → **mất binding phím** (rebind kẹt "Binding") + **Piper đứng nửa chừng + overlay biến mất**. GỐC HỢP NHẤT. Fix trước hết:
- (a) Piper chạy tiến trình RIÊNG (`:tts` service) — bền nhất; HOẶC (b) mặc định System TTS (`AndroidTtsSpeaker`), Piper opt-in/TẮT — nhanh.
- Thay phần lớn finding ② (SPEAK_SAFETY) + sửa gốc phím cùng lúc.
- + watchdog: NavAccessibilityService enabled-mà-không-Bound quá N giây → force-stop+re-enable (đã proven recover), không chỉ grant.
Hey Kachi: xe 1.76 chưa có lớp wake (1.77); kể cả OTA 1.78 vẫn cần host model KWS mới nhận cụm gọi.

## DAG đã dựng (chạy lại khi resume) — 3 stage serial (tránh khoá gradle), role crew-worker:
1. **batch-iso**: ① VoiceVadTrim.MIN_SILENCE_MS 150→600 · ③ VoiceOverlay immersive (ẩn taskbar) · ④ VoiceYoutubeResolver UA desktop · ② VoiceSession SPEAK_SAFETY theo độ dài câu.
2. **batch-parser** (depends iso): ⑤ vietmap app-selector "bằng <app>" + fuzzy · D1 an toàn (câu HỎI không bắn WRITE + datum sai) · D2/D3 synonyms + reply "đã bỏ".
3. **review** (depends parser): senior review + patch + full 5-module test.
Sau DAG: main tự re-verify full test + security scan + build APK 1.79 (vc80) + OTA push.

## Sau khi resume, nhớ
- Tự chạy lại full test (không tin report worker).
- Nếu on-car phiên này tìm thêm gốc binding/Hey-Kachi → gộp vào gói fix trước khi build.
