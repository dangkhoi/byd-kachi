# Playbook lượt xe — xác nhận bản 1.63 (không mò, mỗi mục ≤ 1-2 lệnh)

- **Ngày viết:** 2026-09-15 · **Xe:** DiLink3 (DL3), Android 10, Sealion 6 · **Bản:** 1.63 (versionCode 64), CHƯA push (remote 1.59).
- **Spec:** `docs/specs/kachi-hal187-cast-remediation.html` · **RE 187:** `hal-binding-remediation-2026-09-15.md`.
- **Nguyên tắc:** mọi thứ làm được off-car đã làm + test xanh. Lên xe chỉ **xác nhận** theo thứ tự dưới. Kết nối: `adb connect <ip-xe>:5555` (nếu `offline` → `adb kill-server; adb start-server; adb connect …`). Bật **chế độ kiểm thử** trong Cài đặt (tự tắt 60 phút) để dùng T-BRIDGE.
- Lệnh bridge: `B='am broadcast -a com.byd.launcher.TEST -p com.byd.launcher'` (PHẢI có `-p`).

---

## 0. Cài + nâng cấp (2 phút) — [ĐO cost một lần của HOME-alias]
```
adb install -r app/build/outputs/apk/release/app-release.apk     # 1.62 → 1.63, giữ data
adb shell dumpsys package com.byd.launcher | grep versionName    # = 1.63
```
- **Kỳ vọng:** sau nâng cấp, bấm Home có thể về **launcher3** (alias HOME mới tắt sẵn, chưa có marker). → Mở Kachi › Cài đặt › Hệ thống › **"Đặt làm màn hình chính"** bấm 1 lần. Quan sát: (a) ROM có hiện **hộp chọn launcher3/Kachi** không (như DuDu)? (b) sau đó bấm Home → Kachi.
```
adb shell "dumpsys package com.byd.launcher | grep -A2 KachiHome"   # alias enabled=true sau khi bấm
adb shell "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME" | grep -i kachi
```
- Ghi: hộp chọn có/không · Home về Kachi có/không.

## 1. Cast qua cụm (P0) — [phải PASS trước khi OTA]
Reboot (nút nguồn vật lý). Bật cast một app (vd GMaps) qua bong bóng/nút cast.
```
adb shell am stack list | grep -E "displayId|ClusterBlack|maps"
adb shell "logcat -d -s SimpleCast | grep -iE 'cluster display|detect|profile 35|-d [0-9]'" | tail -20
```
- **PASS:** task app + `ClusterBlackActivity` ở **display 2** (fission), KHÔNG ở display 1 (`kachi-slot-*`); log "cluster display … → 2" xuất hiện SAU profile 35.
- **FAIL:** nếu log "detect … -1 / bỏ cuộc" nhiều lần → VD fission lên chậm hơn 6 s → dán log, chỉnh `AWAIT_ATTEMPTS`.
- Chụp 1 lần output `DETECT_CMD` mới (có `|virtual:`): `adb shell "dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja|virtual:'"` → lưu vào `car-logs/`.

## 2. `sweep` — quét cạn 187 trong MỘT lệnh (thay bấm tay)
```
adb shell "$B --es cmd sweep"                       # trả tóm tắt + đường tệp
adb pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ ./car-logs/   # lấy sweep-<stamp>.json
```
- Đọc JSON: `info[].value` rỗng = vẫn không đọc được (so với danh sách NEEDS-ONCAR); có số = kiểm **đơn vị/scale** với táp-lô (nhiệt cabin, áp lốp kPa, tốc độ vô-lăng…).
- Đây là bằng chứng để chốt ~40 mục NEEDS-ONCAR — không cần mở màn "Kiểm tra từng nút xe".

## 3. Kính "Mở 50%" (T7) — 1 lệnh chốt enum
```
adb shell "$B --es cmd hal --es m setBodyWindowCtrlState --es args 1,4 --ez auto_confirm true"; sleep 5
adb shell "$B --es cmd hal --es m getWindowOpenPercent --es args 1"      # kỳ vọng ≈ 50
```
- PASS ⇒ nút "Nửa" trên dock/CapTest đúng. FAIL (0 hoặc 98) ⇒ enum 4 không phải half trên trim này → sweep 3/5.

## 4. YouTube trong ô (density-lever)
Mở YouTube vào ô, **bấm play** một video.
```
adb shell "logcat -d | grep slot-density"                      # kỳ vọng: dpi=200→199 (598.4dp→601.4dp)
adb shell "dumpsys activity activities | grep -B2 -A6 youtube | grep -iE 'mSizeCompatScale|smallestScreenWidthDp|screenOrientation'"
```
- **PASS:** app đầy khung, KHÔNG co dải dọc giữa; không có `mSizeCompatScale`. (Video 16:9 chỉ phủ kín pixel khi bấm fullscreen — đúng kỳ vọng.)
- **FAIL:** vẫn dải dọc → thử `wm density 192 -d <vd>` rồi play lại; nếu hết ⇒ tăng `thresholdDp` (620). Vẫn không ⇒ cơ chế khác, dán dump.

## 5. GUI-install (HOME-alias) — xác nhận cùng cơ chế DuDu
Chép `app-release.apk` vào USB → cài bằng file manager BYD.
- **PASS:** cài như app thường (không "Fail in installation of desktop apps"). Sau đó bấm "Đặt làm màn hình chính" như mục 0.
- **FAIL:** dán thông báo lỗi + `logcat | grep -i StopLauncher`.

## 6. Nhóm NEEDS-ONCAR ưu tiên (từ RE) — mỗi mục 1 lệnh `hal get`
| Mục | Lệnh | Chốt |
|---|---|---|
| Ambient ×8 (device?) | `hal --es dev BYDAutoSettingDevice --es m <getter>` theo `hal-binding-remediation` | SETTING hay LIGHT; scale 0–100? màu 31? |
| Motor rpm/torque/power | sweep (feature-id zero-hoá) | id thật |
| cell temp ×3 / SOH / batt_temp | sweep | có giá trị? scale |
| trip km/h/kWh | `hal --es dev BYDAutoStatisticDevice --es m getDrivingTimeValue` vs đồng hồ | phút hay giờ |
| regen_level enum | `hal set BYDAutoSettingDevice.setEnergyFeedback 0..3` + `get` | mốc thật |
| lock/door setter | `hal set BYDAutoDoorLockDevice.setDoorLockState 2` → xem `HalWriteProbe` ngoại lệ | có method không |
| engine_code/oil | nổ máy xăng rồi `hal --es dev BYDAutoEngineDevice --es m getEngineCode` | PHEV ngủ? |

## 6b. VOICE "hoàn toàn không work" + LAG — ĐO trước khi đổ cho phần cứng (owner 2026-09-15)
Fact đã đọc từ source (off-car): chạm vào ô đi `injectInputEvent` (**không** fork `input` mỗi sự kiện — `VdAppHost.kt:231`); voice cần quyền **`RECORD_AUDIO` runtime** (`VoiceCapture.kt:46`, thiếu ⇒ tấm chữ báo "thiếu quyền" 8 s rồi im); 2 poller nền qua dadb: `SlotLiveProbe` (1 `am stack list`/nhịp) + SimpleCast watchdog (`am stack list` ~4 s/lần — thấy trong logcat xe). Bisect 3 lệnh:
```
# (a) quyền mic có được cấp chưa? (A10 = runtime; xe không có hộp thoại xin quyền)
adb shell dumpsys package com.byd.launcher | grep -E "RECORD_AUDIO|granted=true|granted=false" | head
#   chưa cấp → adb shell pm grant com.byd.launcher android.permission.RECORD_AUDIO   (PermissionPreflight:104 cũng in đúng lệnh này)
# (b) bộ nhận dạng + mô hình có chạy không — KHÔNG cần mic: đẩy 1 WAV qua ĐÚNG đường VoiceWavProbe
adb push <câu-mẫu.wav> /sdcard/Download/kachi.wav
adb shell "$B --es cmd wav --es path /sdcard/Download/kachi.wav"     # đọc heard/grammar/probe_error
# (c) phiên nghe thật + log
adb shell "$B --es cmd listen"; adb shell "logcat -d -s KachiVoice" | tail -30
```
- (a) sai ⇒ **gốc là quyền**, không phải phần cứng. (b) nghe được ⇒ mô hình/CPU OK, lỗi ở mic/phím vô-lăng (đọc `AccessibilityForceBind`/key binding). (b) `probe_error` "chưa tải mô hình" ⇒ mô hình Vosk thiếu/lỗi tải. (b) chậm nhiều giây ⇒ mới nói tới CPU.

**Lag chạm VietMap trong ô** — nghi **chạm lệch do size-compat** (app co lại nhưng toạ độ map cả ô), cùng gốc YouTube, KHÔNG phải lag:
```
adb shell "dumpsys activity activities | grep -B2 -A6 vietmap | grep -iE 'mSizeCompatScale|mBounds|screenOrientation'"
```
- Có `mSizeCompatScale` ⇒ density-lever (mục 4) chữa luôn; không có mà vẫn khó ⇒ đo tiếp `dumpsys gfxinfo com.byd.launcher` (jank) + `top -n 1 -m 10` (CPU ai ăn).

**CPU/độ trễ toàn launcher — 3 số đo, 1 phút:**
```
adb shell top -n 1 -m 12                                   # ai ăn CPU khi Kachi idle (kỳ vọng: không phải com.byd.launcher)
adb shell dumpsys gfxinfo com.byd.launcher | grep -A12 "Janky"   # % khung giật của launcher
adb shell "logcat -d -s SimpleCast | grep -c 'am stack list'"    # số lần poll/phút — nếu cao ⇒ giảm nhịp/đổi sang event
```
Ứng viên tối ưu (chỉ sau khi đo): gộp 2 poller `am stack list` thành 1 + kéo dài nhịp khi không cast; tắt `KachiLog` ghi logcat ra thẻ khi không ở chế độ kiểm thử; đo lại.

## 7. Sau khi 1 + 2 + 3 PASS
→ báo về: em chạy lại senior review + security scan (đã chạy off-car) với log thật → OTA 1.63. Mục FAIL: dán nguyên output — sửa đúng chỗ.

## Dọn
- `appops set com.android.shell REQUEST_INSTALL_PACKAGES deny` (grant tạm hôm 09-15 để test cài) — nếu còn.
