# Runbook on-car — Kachi 2.19 (vc120)

> **Trạng thái**: chờ test trên xe · **Ngày**: 2026-09-23 · APK `apk/Kachi-2.19-release.apk` (`com.byd.launcher`, KHÔNG debuggable) · feat branch, **CHƯA OTA**.
> Gộp mọi thay đổi 2.15→2.19. Chạy từng test case, ghi PASS/FAIL/số đo vào bảng cuối. Findings gốc: `oncar-findings-2026-09-23.md`.

## 0. Cài + chuẩn bị
```
python3 scripts/vehicle/kachi/adb_raw.py <car-ip> 5555 push apk/Kachi-2.19-release.apk /data/local/tmp/_k.apk
python3 scripts/vehicle/kachi/adb_raw.py <car-ip> 5555 shell 'pm install -r /data/local/tmp/_k.apk; rm -f /data/local/tmp/_k.apk'
python3 scripts/vehicle/kachi/adb_raw.py <car-ip> 5555 shell 'dumpsys package com.byd.launcher | grep versionName'   # phải 2.19
```
Đặt Kachi làm màn chính. Bật test-bridge (Cài đặt › Hệ thống › Nâng cao) nếu cần lệnh adb.

## A. VOICE UX (2.16)
| # | Test | Cách | PASS |
|---|------|------|------|
| A1 | Earcon READY | Mở voice (nút mic / "Nói với xe") | Nghe bíp 2-nốt DỄ CHỊU ngay khi mic mở |
| A2 | Waveform | Nói vào mic | Vòng tròn phập phồng theo giọng (kiểu Siri) |
| A3 | Vị trí overlay | — | Overlay ở GIỮA, phía DƯỚI màn (không góc) |
| A4 | Earcon xong/lỗi | Nói lệnh hiểu được / câu vô nghĩa | Bíp "xong" (hiểu) / bíp "lỗi" (không hiểu) |
| A5 | Feedback nhanh | Nói câu ngắn "bật đèn đọc" | Phản hồi NHANH hơn bản trước (VAD 0.35) — đo `nghe X ms` trong log |

## B. VOICE dẫn đường (2.17)
| # | Test | Cách | PASS |
|---|------|------|------|
| B1 | Sổ "công ty 1" | Nói "dẫn đến công ty một" (ô lưu tên "Công ty 1") | VÀO SỔ (không ra GMaps geocode "công ty một") — log `NavigateSaved` |
| B2 | Số nhà | Nói "dẫn đến 67 hoàng văn thái" | GMaps nhận `q=67 ...` (KHÔNG "sáu bảy") — log `google.navigation:q=` |

## C. GIỮ STATE (2.19, #10) — quan trọng
| # | Test | Cách | PASS |
|---|------|------|------|
| C1 | Đổi theme không restart | Mở app vào 1 ô (YouTube) → Cài đặt › Giao diện → đổi Sáng↔Tối | CHỈ đổi màu; launcher + app trong ô KHÔNG restart (app chạy tiếp) |
| C2 | AUTO ngày/đêm | Để theme "Theo xe", chờ qua mốc ngày/đêm | Đổi màu, không restart |

## D. Hey Kachi (2.15 ASR, chưa chỉnh matcher)
| # | Test | Cách | PASS |
|---|------|------|------|
| D1 | Nghe | Nói "Hey Kachi" 10 lần | Ghi hit `__/10` (matcher chưa chỉnh — thu golden ở G) |
| D2 | Loop | Sau khi wake, nói 1 lệnh | KHÔNG loop mở lại liên tục (R7 overlay độc lập, không kéo launcher) |
| D3 | Không kéo launcher | Đang mở app full màn → nói "Hey Kachi" | Overlay voice nổi lên, KHÔNG kéo Kachi đè app |

## E. Camera theo xi-nhan (2.15/2.19) — thử 10 option qua adb, KHÔNG rebuild
```
prefs_set camera_signal_enabled true    # bật (hoặc Cài đặt › Tiện nghi xe)
```
| # | Test | Cách | PASS/PARTIAL/FAIL |
|---|------|------|-------------------|
| E1 | Xi-nhan trái | Bật xi-nhan trái (đậu xe) | PASS: overlay trái + VIDEO camera · PARTIAL: overlay đen (→E3) · FAIL: không gì (log `KachiCamera`) |
| E2 | Xi-nhan phải | Xi-nhan phải | như E1, bên phải |
| E3 | Nếu PARTIAL (đen, không video) — thử từng option, xem cái nào ra HÌNH: |||
|   | `prefs_set camera_lvds_option B` | z-order media overlay | ra hình? |
|   | `prefs_set camera_lvds_option C` | setLVDS trước | ra hình? |
|   | `prefs_set camera_lvds_option D` | full-screen mode | ra hình? |
|   | `prefs_set camera_lvds_option G` | chiếu lên cụm | ra hình? |
|   | `prefs_set camera_lvds_option H` | chờ workState ON | ra hình? |
| E4 | Tắt | Tắt xi-nhan | overlay đóng |

## F. Chip header (2.18)
| # | Test | Cách | PASS |
|---|------|------|------|
| F1 | Bỏ dấu "· —" | Thêm chip sấy kính (defrost) vào header (Cài đặt) | Chip chỉ icon/nhãn, KHÔNG có "· —" vô nghĩa; icon sát nhau hơn |

## G. Thu GOLDEN Hey Kachi (để chỉnh matcher off-car)
Owner nói loạt vào Kachi (mình grab text ASR từ log `KachiVoiceRec: sherpa ra`):
- ~15 lần "Hey Kachi" (đủ kiểu: nhanh/chậm/xa/gần) + "Kachi ơi" + "OK Kachi".
- ~15 câu THƯỜNG + lệnh ("lấy gió trong", "bật đèn", tán gẫu) — để đo false-accept.
→ Mình về chỉnh `WakeAsrMatcher` cân bằng (nghe được + không loop).

## Việc NGOÀI Kachi
- Bóng VietMap sang cụm: cài lại **APK VietMap mod-cluster** (bản có `VMBluetoothService$posrx`). Kachi đã gửi lệnh đúng.

## Bảng điền kết quả
| Mã | KQ | Ghi chú |
|----|-----|--------|
| A1–A5 |  |  |
| B1 B2 |  |  |
| C1 C2 |  |  |
| D1 hit __/10 · D2 · D3 |  |  |
| E1 E2 · E3 option ra hình: __ · E4 |  |  |
| F1 |  |  |
