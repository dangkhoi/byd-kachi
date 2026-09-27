# Handoff — 27/09/2026 trưa: buổi xe 2.74 xong, 2.75 (176) đóng off-car

> **Trạng thái**: Superseded bởi `session-2026-09-27-closing.md` · **Cập nhật**: 2026-09-27 (trưa) · **Mục đích**: người mở phiên sau biết xe đã đo gì, 2.75 sửa gì theo số đo đó, còn gì phải đo. Spec: `docs/specs/kachi-274-ux-voice-camera.html` (§9 nhật ký, §10 Pass 2). Runbook xe kế: `docs/diagnostics/oncar-runbook-2.75.md`. Số đo buổi xe: `docs/PROJECT-BACKLOG.md` dòng `ONCAR-2026-09-27`.

## 1. Bản
| Ở đâu | Bản | Ghi chú |
|---|---|---|
| Xe owner (Seal) | **2.74 (175)** cài tay 09:57 27/09 | đo 09:57–11:56; owner off-car 11:56 |
| Kênh OTA `main` | **2.75 (176)** `apk/Kachi-2.75-release.apk` | 43 791 047 B, sha256 `c11274be…1ceb` |
| Máy ảo `clusternav10` | bản vehicleTest camera 2.75 (agent camera cài) | có harness `camera-dewarp-e2e.sh` |

## 2. Buổi xe 27/09 — đã chốt (chi tiết + [ĐO] ở backlog)
- **Camera**: khung thô 5120×960 = 4 dải fisheye đứng; dải 1 trái, dải 2 phải; bộ tham số owner duyệt STRIP/GL/**F55/K100/S130**/cx=cy=0, trái xoay L90 (đuôi dưới); dịch tâm làm cong, scale ≥ 140 "heavy"; kênh đơn halMode 2/3; GL 11 % janky vs TV 1 %; **P1→P0** `BufferQueue abandoned` 16/s.
- **Voice** E2 9/22 đúng → 3 lớp lỗi (mất đầu câu, thiếu động từ, mất đuôi); F2 vế trần "vào ô" bị bộ giải mã bỏ; E4 hỏi lại tên hồ sơ PASS 3/3; E1 taskbar PASS.
- **UX** G1–G5 OK; OQ5 chốt `getAcWindLevelManualSign` 0 = AUTO; chip AUTO n → owner đòi `auto` thường + icon lấy gió.
- **Trip**: `getCurrentJourneyDriveTime` GIỜ bậc 0,1 h (1.8→1.9→2.0 cách ~6 phút) ⇒ widget "6 phút nhảy 1 lần" là dữ liệu.
- **E6** KachiMem purge không giảm RSS; **E12** PSS 300 s launcher 75 MB, :wake 237, :tts 176.
- Bẫy: `hal get` không truyền `--es args` cho getter không tham số; chế độ kiểm thử tắt khi launcher restart; pref camera chỉ áp sau `camera none`→`left`.

## 3. 2.75 sửa gì (mỗi làn có doc + test + reviewer Pass 2)
| Làn | Việc | Doc |
|---|---|---|
| Voice | pre-roll 300 ms cắt im lặng đầu (A/B 29/30 WAV xe y hệt) · `VoiceSlotNoVerb` (app + "vào ô số N" không động từ) · `bareSlot` ("mở vietmap hai") · lượt nối bật open-turn + có bản thu · chửi→sưởi · log `hỏi lại:` | `offcar-2026-09-26/voice-car-0927.md` |
| UX8 | chip *Lấy gió* / *Cảm biến PM2.5* bằng hình (`TelemetryView.state`, `CapabilityIcons.STATE`) · `auto n` thường trên chip · soát máy 53 nhánh, 7 datum để lại có lý do | `offcar-2026-09-26/ux-ux8-header-state-icons.md` |
| Camera | [P0] đổi bên không đóng `AVMCamera` ⇒ `closeSession(keepPano)` · núm pan (dấu theo bên — reviewer) · nguồn `CHANNEL` (contentWidth = streamW/4) · trần 15 fps · xoay ±90 KHÔNG lỗi (affine trước radial, 3,1/255) · `camera_synth file:` | `offcar-2026-09-26/camera-dewarp-gl.md` §"Xe 27/09" |
| Trip | `TripTimeSmoother`: phút nội suy, kẹp +5/60 h, re-anchor khi HAL nhảy, không lùi; `hoursToHm` floor+eps (4.1 h từng ra "4:05") | `offcar-2026-09-26/trip-time-smoother.md` |

## 4. Lên xe lần sau — `oncar-runbook-2.75.md` §2 (thứ tự theo giá trị/phút)
CAM-B5 [P0] → CAM-B6 → CAM-C1 → CAM-C2 → G7 (pan, cả 2 bên) → UX8 → E2' → E3/E10 (còn từ 2.74) → TRIP (≥ 7 phút đậu) → E12' → E11. Không adb vẫn làm được camera/UX8/TRIP qua Cài đặt.

## 5. Kết quả đóng 2.75 (điền lúc ship)
- Review Pass 2 (Opus, spec §10): APPROVED — 1 [P1] dấu pan theo bên + 2 [P2] + 6 [P3] vá; sau đó vá P3 #10 (null một nhịp không lùi số trip).
- Test **4 541 / 0 fail** (app 1 464 · core 2 892 · car-integration 64 · vehicle-contracts 22 · offcar-planner 99) · lint 0/522 · APK strings 0 hit · quét bảo mật: backlog dòng SCAN-2.75.

## 6. Câu chờ owner
1. G7 giá trị pan cuối (làm mặc định `ClusterProfile` Seal) · 2. CAM-C1 có đổi mặc định sang một kênh? · 3. VOICE-WRITE-LANE [P2] · 4. ghép vế khi vế trước đủ nghĩa (F2) · 5. OQ7 thẻ lốp · 6. dedupe `ac_mode_auto`/`ac_wind_auto` · 7. dấu thứ hai cho ghế · 8. chip hồ sơ 34 vs 40 dp.

## 7. Nợ kỹ thuật mới (backlog)
CAM-BQ consumer SurfaceTexture sống theo tiến trình (thiết kế mạnh hơn, chưa làm) · CHANNEL tự lùi PANO khi `rc=false` · `camera_frame` content chưa ghi "anamorphic ×4" · G8 mã HAL 1003 · `power_level`/`headlight_feedback` in mã thô [P3] · 4 cửa + cửa sổ trời + 2 chip AUTO-mode chưa có icon trạng thái (có lý do trong test) · 2.76: SETTINGS-CAMERA-IA + hình theo cụm.
