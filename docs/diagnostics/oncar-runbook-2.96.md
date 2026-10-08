# On-car runbook 2.96 — xác nhận 2.96 + thử đẩy giới hạn tốc độ vào ô gốc của cụm (fw 2606)

> **Trạng thái**: Current · **Cập nhật**: 2026-10-08 · **Loại**: Runbook (on-car) · **Owner**: dangkhoi ·
> **Liên quan**: spec [`../specs/kachi-296-plan.html`](../specs/kachi-296-plan.html) §6 · script
> [`../../scripts/vehicle/isa-speedlimit-2606.sh`](../../scripts/vehicle/isa-speedlimit-2606.sh) · RE fw 2606 (ngoài repo,
> `../firmware/fw-2606/RE-2606-findings.md` §1.3) · bằng chứng cũ
> [`../archive/diagnostics/oncar-inject-sweep-2026-08-15.md`](../archive/diagnostics/oncar-inject-sweep-2026-08-15.md) §S3

Mức bằng chứng theo `CLAUDE.md` §2: **[ĐO]** · **[SUY]** · **[ĐOÁN]** · **[CHƯA BIẾT]**. Mỗi bước ghi kết quả vào cột cuối.

## 0. Chuẩn bị (5 phút)

| # | Việc | Ghi chú |
|---|---|---|
| 0.1 | Laptop + đầu xe chung hotspot; `adb connect <ip-xe>:5555` | macOS chặn mạng cục bộ cho adb ⇒ dùng cầu nc (memory `kachi-adb-car-tunnel`) |
| 0.2 | `adb shell dumpsys package com.byd.launcher \| grep versionName` = **2.96** | Không đoán bản (CLAUDE.md §9) |
| 0.3 | `adb shell getprop ro.build.version.incremental` = `eng.build.20260610…` (fw 2606) | |
| 0.4 | Bắt log nền: `adb logcat -v time > logs/oncar-296-$(date +%H%M).txt &` | `logs/` không vào git |

## A. Xác nhận 2.96 (spec §6) — CHỈ quan sát, không đổi gì ngoài thao tác người dùng

| # | Thử | Kỳ vọng | Đọc ở đâu | Kết quả |
|---|---|---|---|---|
| A1 | Tắt máy → nổ lại (×3) → bấm HOME | Về **Kachi** | log `KachiHomeGuard` "HOME taken by … reassert result=OK" (~15–20 s sau nổ máy) | |
| A2 | Tự chiếu chia đôi 5:5 lúc nổ máy (×3) | Cụm đủ **hai nửa**, không lớp bản đồ cũ phía sau | `am stack list` (display cụm: ClusterBlack + 2 app) · log không có `DISCARDED: cast` · `KachiReady cast-hold … -> GO_*` | |
| A3 | Xem YouTube vài phút → tắt máy → nổ lại | Phát tiếp **đúng bài, đúng giây, toàn màn** | log trip `resume:found:view(…) resume-seek=…:ok` (không còn `SLOT_NOT_READY` → NOOP) | |
| A4 | Đang chiếu cụm → Cài đặt xe đổi Sáng ↔ Tối | Nền quanh bản đồ đổi **ngay** (ngày `#B5C6D4`/`#C0CFDB`, đêm đen) | log `ClusterBlack nền màn chiếu (đổi cấu hình)` | |
| A5 | "Hey Kachi" ×10 (máy nổ, nhạc nhỏ) | Nhận ≥ 8/10; ghi số lần nổ nhầm khi im lặng 10 phút | log `:wake` | |
| A6 | Sau 1 lệnh, nói "tạm biệt" / "cảm ơn Kachi" / "bai bai" | Kết thúc, đáp "Tạm biệt, hẹn gặp lại" | màn voice | |
| A7 | "đóng kính lái" · "tắt sưởi ghế phụ" · "gió mức 3" | Câu đáp "Đã/Đang …" tự nhiên | màn voice | |
| A8 | Đo khởi động (×3 nổ máy) | Cụm xong sớm hơn 2.95 (2.95: +38 s / +71 s sau sáng màn) | `docs/diagnostics/startup-timeline-2026-10-07.md` cách đọc · `KachiReady summary` | |
| A9 | Tắt máy chờ standby 10 phút → nổ lại | Không chiếu cụm hai lần; log standby thưa (repin ≥ 30 s) | `perf-inventory-2026-10-07.md` | |
| A10 | Chẩn đoán › "ANR gần nhất của Kachi" | Có/không ANR, không lỗi | DiagActivity | |
| A11 | Nút giọng nói vô-lăng: màn BẬT rồi màn TẮT (V2.0.4 tối ưu #4 đổi đường này) | Ra đúng thứ đã gán trong Kachi (Kachi nghe / Trợ lý BYD) ở cả hai trạng thái | log `NavAccess onKeyEvent` + màn | |
| A12 | Mở/tắt camera theo yêu cầu của Kachi ×3 (V2.0.4 tối ưu #5 đổi logic thoát 360) | Camera 360 của xe không tự bật/tắt sai | màn + log camera | |

**Còn mở cần số đo xe (không chặn)**: OQ3 trần 25 s `HealCastDeferral` (đọc `KachiReady cast-hold` + `keys-defer` ở A1/A8) · alarm 60 s `RebindReceiver` khi standby (A9) · `VietMapAutostart.awaitBubble` 2 `dumpsys`/500 ms (A8).

## B. Giới hạn tốc độ vào ô GỐC của cụm (thay vì Kachi tự vẽ) — fw 2606

**Câu hỏi**: có cửa nào để Kachi đẩy giới hạn tốc độ (từ VietMap/GMaps) vào biển tốc độ gốc của cụm / đèn "dự báo giới hạn
tốc độ" mới của 2606?

**Đã biết**
- [ĐO xe 15/08, fw 2602] biển trên cụm = data-item 564 nuôi bằng CAN camera ADAS (`ADAS_SLA_OUTPUT_SPEED_LIMIT`); mọi id HAL
  ghi tốc độ (`STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET`, `SETTING_SPEED_LIMIT`, 33 id khác) **rc=0 mà cụm không đổi**.
- [ĐO log 07/10] Kachi đang ghi `STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET` (`HalSpeedSignPort`) nhưng chỉ giá trị **0**.
- [ĐO nguồn 2606] MỚI: 8 setter `BYDAutoSettingDevice.setIsaMap*` (`(I)I`, không kiểm dải, quyền `BYDAUTO_SETTING_SET`) →
  `SETTING_ISA_MAP_*`; đèn mới "dự báo giới hạn tốc độ" (`speed_limit_forecast.png`) hiện khi khung `0x237` online, giá trị
  `0x2370002E`. [ĐOÁN] luồng IVI → ECU ADAS → `0x237` → cụm. Ai phát `0x237`: [CHƯA BIẾT].

**An toàn (CLAUDE.md §4)**: pha GHI chạm ECU ADAS (hệ an toàn) ⇒ chỉ khi xe **đỗ, số P, phanh tay**, owner nói "ok thử ghi";
mỗi bước ghi 1 giá trị → quan sát → script **luôn trả mọi ô về 0** khi thoát (cả Ctrl-C). Thấy bất thường (cảnh báo lạ,
ADAS báo lỗi) ⇒ **dừng ngay**, không thử lại (memory "thấy sập lần đầu là dừng"). KHÔNG dùng TEST device `0xAA0002xx`.

| # | Lệnh | Khi nào | Quan sát | Kết quả |
|---|---|---|---|---|
| B1 | `VEH=<ip-xe>:5555 ./scripts/vehicle/isa-speedlimit-2606.sh` | Bất kỳ (chỉ đọc) | Tên hằng nào đọc được / `bad_feature`; `0x237` online? | |
| B2 | `VEH=<ip-xe>:5555 PHASE=watch SECS=180 ./scripts/vehicle/isa-speedlimit-2606.sh` | Người ngồi ghế phụ chạy, **xe đi qua 2 biển tốc độ khác nhau** | `SLA=` đổi theo biển? `0x237`/`dựbáo` có lúc nào online? Chụp ảnh cụm lúc đèn dự báo (nếu có) | |
| B3 | `VEH=<ip-xe>:5555 PHASE=write ./scripts/vehicle/isa-speedlimit-2606.sh` (gõ `ISA` để tiếp) | Đỗ, P, phanh tay, owner đồng ý | W1 ô ISA = 60 · W2 + bản đồ hợp lệ · W3 `setIsaMap*` 60/200 m — mỗi bước script hỏi "cụm/HUD có hiện 60?" | |

**Đọc kết quả → quyết định**

| Kết quả | Nghĩa | Bước sau |
|---|---|---|
| W1/W2 làm biển cụm/HUD hiện 60 | Cửa ISA mở trên 2606 (khác 2602) | Spec 2.97: `HalSpeedSignPort` ghi giá trị thật từ VietMap/GMaps, overlay Kachi thành dự phòng |
| W3 làm đèn "dự báo" hiện 60 | Cửa `setIsaMap*` mở | Spec 2.97: đẩy "giới hạn sắp tới" (VietMap có khoảng cách tới biển kế) |
| Không bước nào đổi cụm | Đóng như 2602 | Giữ overlay Kachi; ghi kết luận vào backlog `ISA-NATIVE-SIGN` |
| B2: `0x237` online khi xe tự có dự báo | Có nguồn khác phát 0x237 | Đo thêm ai phát (không ghi) |

Gửi về: thư mục `logs/oncar-isa-*/session.log` + ảnh cụm (giữ ngoài repo).
