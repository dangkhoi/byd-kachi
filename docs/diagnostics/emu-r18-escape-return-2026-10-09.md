# 2.98 R18 — app thoát ô ⇒ stack về lại màn ảo ô: E2E máy ảo bản thật (2026-10-09 đêm)

> **Trạng thái**: Evidence · **Cập nhật**: 2026-10-09 · **Chủ**: dangkhoi · **Liên quan**: spec `docs/specs/kachi-298-plan.html` R18/T18 ·
> `diagnostics/emu-slot-escape-approaches-2026-10-09.md` (đo hướng A bằng nguyên mẫu) · backlog `SLOT-ESCAPE-POLICY` · `SLOT-ESCAPE-VD-RETURN-ONCAR`.
> Máy ảo `emulator-5556` (AVD `kachi_play`, Android 10), bản `vehicleTest` 2.98 (201) cây làm việc trên `116f239` (chưa commit). Không chạm xe.
> Ảnh chụp có tài khoản Google của máy ảo ⇒ không đưa vào repo.

Mức bằng chứng: mọi số dưới đây [ĐO] từ `logcat` (dòng hệ `Failed to put TaskRecord … on display N` của `ActivityStackSupervisor.java:2436`
và dòng daemon `esc moved <gói> <task> <stack> <vd> <ms>`; `ms` = từ lúc daemon nhận sự kiện tới lúc đọc lại xác nhận xong) + `am stack list`.

## 1. Các ca

| # | Ca | Kết quả |
|---|---|---|
| 1 | Máy đã chạy bản R7 (Waze freeform đang được quản, dấu `kachi_slot_escape = com.waze\|0\|439\|…`) ⇒ `adb install -r` bản R18 | `dọn R7: com.waze task [439] ⇒ toàn màn OK`, dấu xoá; Kachi mở lại Waze vào ô (K8 app đang sống) — đường trả CLAUDE.md §5 chạy |
| 2 | Daemon: dây thế hệ 2 | Kachi khởi daemon mới ở cổng 39154; daemon cũ (cổng 38xxx, R7) không còn client ⇒ tự thoát (không còn trong `ps` ~10 phút sau) |
| 3 | 6 app nguội vào ô (cầu kiểm thử `slot` = force-stop + `am start --display`) | Waze 46 ms · VietMap 52 ms · Gmail 9 ms · Messages 35 ms · Drive 11 ms · Meet 2 lần thoát 18 + 14 ms — **7/7 về ô**, màn chào/đăng nhập hiện TRONG ô |
| 4 | VietMap chưa cấp vị trí (`pm revoke`) | hộp `GrantPermissionsActivity` (task VietMap) thoát ⇒ về ô; bấm *Allow* trong ô ⇒ cấp thật (đã trả quyền như cũ) |
| 5 | Waze tương tác: *Drive there* (`TripOverviewActivity`) · tìm "gas" bằng bàn phím · chọn kết quả (`LocationPreviewActivity`) | về ô 15 ms · 15 ms; chạm qua daemon bơm chạm chạy; Gboard hiện ở đáy display 0, chữ gõ vào ô tìm Waze; toast hệ *"App does not support launch on secondary displays."* ~2 s mỗi lần (OQ7-b) |
| 6 | Waze *Share → Gmail* (`ComposeActivityGmailExternal` `NEW_TASK`) | `esc skip com.google.android.gm 447 21 NOT_SLOT_PKG` — task Gmail toàn màn display 0, không chạm (§4 câu 2) |
| 7 | Ba nút đầu ô | *chạy nền* ⇒ đỗ ô 7 + bảng gỡ màn đó · *⇄* ⇒ ngăn kéo, chọn VietMap ⇒ vào ô + thoát về ô 42 ms · *✕*: chạm thử trên máy ảo không trúng nút (nút tự ẩn trước lượt chạm) — mã đường đóng không đổi (`relinquish` + gỡ bảng) |
| 8 | Lấy lại từ ô 7 (cầu `slot` Waze) | `KachiPark: nhận lại` ⇒ bảng `vd 21 com.waze` ⇒ thoát kế về ô 20 ms |
| 9 | Bão: 12 lượt *mở địa điểm ↔ quay lại* trong ~48 s | 10 lần về ô (5–43 ms) ⇒ `esc trip pkg com.waze rate>=10/60s` ⇒ lần 11 `esc skip … TRIPPED_PKG`, Waze ở display 0 toàn màn = đường 2.93 |
| 10 | HOME sau khi ngắt gói | màn chính dựng lại ⇒ ô mở lại Waze (K8 kéo task đang sống vào màn ảo mới) — phát hiện: bảng còn màn ảo đã nhả của host cũ (`slot-taken`) ⇒ **sửa gốc**: gỡ bảng theo MÀN ẢO ở `VdLease.free` (`SlotEscapeReturn.revokeDisplay`), bài canh `SlotEscapeReturnWiringContractTest` |
| 11 | `am force-stop` Kachi khi Waze trong ô | daemon `esc off client-gone` (xoá bảng, gỡ bộ nghe); Kachi lên lại ⇒ nối daemon thường trú lượt 0 ⇒ bảng ⇒ `esc ready` ⇒ Waze mở lại, thoát về ô 6 ms |
| 12 | Daemon bị giết khi Kachi đang nối | luồng đọc báo cáo thấy EOF ⇒ hạ cờ ⇒ khởi daemon mới ngay (bảng còn ô) |
| 13 | Đổi bố cục ONE → TWO_COL → ONE (dựng lại cả ô) | đỗ ⇒ gỡ bảng; nhận lại ⇒ `vd 25 com.waze`; thoát sau đó về ô 44 ms |
| 14 | Cầu chì BỀN mô phỏng: ghi `kachi_escape_return_trip = 201\|sim` (run-as), khởi lại Kachi | bảng gửi KHÔNG api ⇒ daemon không nghe; 2 lần Waze thoát ở lại display 0 (đường 2.93). Xoá khoá ⇒ `esc ready` + về ô 8 ms |
| 15 | Khởi động lại máy ảo (sau khi dựng lại kênh `tcpip 5555` + `reverse tcp:5555 tcp:5557`) | daemon nguội lên ~0,5 s, bảng tới trước lần thoát đầu ⇒ Waze về ô 76 ms |

Tổng: **27 lần thoát được dời — 27 về đúng ô, 0 lần mất task, 0 crash/ANR**; 2 lần bỏ đúng luật (gói khác · gói đã ngắt); 2 lần không dời
do cầu chì bền mô phỏng.

## 2. Hiệu năng (perf-snapshot 120 s, cùng máy ảo)

| Trạng thái | Kachi CPU % · PSS | Daemon | Ghi chú |
|---|---|---|---|
| R18 — Waze trong ô, đứng yên | 0,07 % · 68 MB · 276 khung/120 s | 0 tick CPU / 120 s · PSS 22,9 MB | R7 cùng ca (perf-298 §2.5): 0,08–0,09 % + **9 cửa sổ che** + burst ≤ 6 `am stack list` mỗi lượt mở |
| R18 — YouTube trong ô | 0,62–0,70 % · 58–62 MB | — | `shell=4/phút` (nhịp đo ô, như trước) |
| 116f239 — YouTube trong ô (trước khi cài) | 2,38 % · 79 MB · 2 950 khung | — | KHÔNG so ngang được: lượt đó YouTube đang phát khung xem trước (Kachi vẽ 2 950 khung) |

Mỗi lần thoát: **0 lệnh shell** phía Kachi; trong daemon 2 lời gọi đọc (`getAllStackInfos` · `getWindowingMode`) + 1 `moveStackToDisplay` + 1 đọc lại.
Kachi: một khung điều khiển mỗi lần bảng đổi; một luồng đọc báo cáo ngủ chặn trên socket.

## 3. Để lại máy ảo

Kachi HOME, ô 1 = YouTube (như lúc nhận); không dấu `kachi_slot_escape` / `kachi_escape_return_trip`; `enable_freeform_support` = 1 (như lúc nhận,
R18 không đổi cờ này); VietMap đã cấp lại vị trí; kênh shell máy ảo `tcpip 5555` + `reverse tcp:5555 tcp:5557`; chế độ kiểm thử bật ~23:03 (tự tắt sau 60 phút).
