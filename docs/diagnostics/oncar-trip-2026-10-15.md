# Chuyến xe sáng 15/10 — Kachi 3.1.5 (210) trên Seal: tóm tắt

> **Ngày**: 2026-10-15 · **Loại**: chẩn đoán sau chuyến, chỉ đọc (không đổi mã) · **Người làm**: agent cho owner dangkhoi
> **Nguồn**:
> - logcat toàn hệ thống 07:45:48 → 08:23:46, kèm bản chụp bộ đệm lúc lên nguồn và lúc tới nơi;
> - mỗi phút một bản chụp PSS + `wm size -d 1`;
> - nhật ký Kachi `usage-*.log` / `usage-wake-*.log`.
>
> **Xe**: fw 2606, `mcu_version` 18.3.5.2606100.2 [ĐO].
> **Kachi**: lúc lên nguồn (07:42:09) là tiến trình 3.1.4 sống qua đêm; **3.1.5 (210) cài đè lúc 07:45:30** (`Force finishing … KachiHome`, đổi mã băm thư mục cài) [ĐO log · bản số lấy từ người cài, log không in versionName].
> Mức bằng chứng theo `CLAUDE.md` §2. Không chép vị trí, tên đường hay nội dung lời nói từ log.

## 1. Mát ghế tự tắt (ưu tiên) → chi tiết ở [`seat-vent-auto-off-2026-10-14.md` §12](seat-vent-auto-off-2026-10-14.md#12-mẫu-1510--hal-2606-viết-2026-10-15)

- Lên nguồn 07:42:09.7. Mát ghế lái đọc 1 → 1 → 3 (07:42:34). Lời ghi là `SeatComfort` của tiến trình 3.1.4, ≈ 07:42:26.5 (+16,8 s) [SUY mạnh: dòng ghi bị mất, suy từ `ctl-after recirc` đi kèm].
- **07:51:19.248 (+9,16′): xe tự tắt** (`postEvent 0x4fa00008 = 1`). ±10 s không có `set featureID` nào ngoài dẫn đường lên cụm, không đổi nguồn, không gói đám mây vào, không có sự kiện tài khoản [ĐO].
- +3 s sau là khung `0x095` (`0x0950000F`/`0x09500022` = 0), lần duy nhất cả chuyến. 14/10 cũng y hệt [ĐO] ⇒ nghi một ECU khởi động lại [ĐOÁN].
- 07:53:19: owner nói "mát ghế lái mức hai" qua Kachi ⇒ `KachiCtl setSeatVentilatingState(1,3)` ⇒ giữ ≥ 30′ tới hết chuyến [ĐO].
- Chuyến 14/10 chiều/tối (nhật ký Kachi): tụt +9,9…10′ (16:43) và +8,5…8,7′ (18:27) [ĐO số đọc · SUY người tắt].

## 2. Ổn định

- **Kachi: 0 crash, 0 ANR** [ĐO: không có `FATAL EXCEPTION` / `ANR in` / tombstone nào của `com.byd.launcher`].
- Hệ thống có 1 tombstone: `mediaextractor` SIGABRT do hẹn giờ (`SI_TIMER`) lúc 08:22:47, tiến trình media của Android, không phải Kachi [ĐO].
- Waze (pid 20846) chết bằng signal 9 lúc 07:50:42 ("fore SVC"). Trong ±15 s không có lệnh `force-stop`/`kill` nào của Kachi, không có lmkd [ĐO]. Bên giết [CHƯA BIẾT]. Waze lên lại bình thường (được chiếu lúc 08:14).

## 3. Widget 3D

| Lúc | Sự kiện | Chi phí |
|---|---|---|
| 07:45:33 | 3.1.5 xoá cache `.glb` cũ (đổi khoá cache) | — |
| 07:45:38 → 07:46:11 | Dựng lại từ `car.mesh` của AVM: đọc + phân tích 4,59 s, **dựng 33,6 s** (155 bộ phận, ~892 k đỉnh), ghi cache mới | một lần sau khi nâng cấp [ĐO] |
| 07:46:26.8 | Nạp từ cache: nguồn 950 ms + gltfio 1 052 ms | [ĐO] |
| 07:50:22.2 | **Nhả** (bung ô 1): 156 khung, purge 17 ms, RSS 277,8 → 250,2 MB | [ĐO] |
| 07:53:46.2 | Nạp lại (thu ô 1): nguồn 240 ms + gltfio 1 059 ms | [ĐO] |
| 08:14:29.3 | Nhả (sang màn chiếu): 858 khung, RSS 260,6 → 248,0 MB | [ĐO] |
| 08:21:46.4 | Nạp lại: nguồn 841 ms + gltfio 967 ms. Chạm 3D: pha bật / tắt, đèn ngày tắt | [ĐO] |
| 08:29:26.9 | Nhả lúc tắt máy: 940 khung, RSS 197,8 → 177,2 MB | [ĐO] |

## 4. R30 bung/thu ô, R31 nút chiếu thay app

- **R30** [ĐO]:
  - 07:50:22.221 `bung ô 1 · 14 ms` (`slot-resize` 590×978 → 1795×978, giữ DPI 157);
  - 07:53:46.158 `thu ô 1 · 47 ms (TAP)` (1795×978 → 590×978).
  - Mỗi lần chỉ có một dòng, không lặp lại.
- **R31**: **mỗi chạm đổi đúng một cặp app** [ĐO].
  - 08:14:49.48 `returnApp` Google Maps về màn chính (`--display 0 --windowingMode 1`), rồi ghim Waze lên cụm. `verifyCastFull: VERIFIED` lúc 08:14:55.6, tức **6,1 s**.
  - 08:15:43.41 chiều ngược: Waze về màn chính, Google Maps lên cụm. VERIFIED lúc 08:15:48.8, tức **5,4 s**.
  - Cả hai lần: `wm size 1920x720` + `overscan 0` chỉ trong lúc đặt khung (`am task resize … 0 40 1920 560`), xong thì `wm size reset` / `overscan reset` (luật V1 gỡ ép hình học, 3.1.1).

## 5. Camera xi-nhan

- 29 lần hiện overlay. Lần nào cũng là `cluster=true … hình=CLUSTER`, kiểu nắn `WIDE`, camera toàn cảnh id 1 (`cam_sort='rear:0;pano_h:1;'`). Trái thì `rot=-90`, phải thì `rot=90` [ĐO].
- Kiểu khung «Theo cụm» đã ra đúng `hình=CLUSTER`; đây là việc 3.1.5 sửa sau 14/10 (`camera-style-cluster-2026-10-14.md`). Hình thực tế trên màn [CHƯA BIẾT: cần ảnh chụp].

## 6. Giọng nói: hạ nhạc

- 5 cặp `hạ nhạc khi nghe: 7 → 4 (đích 4)` / `trả nhạc (release): 4 → 7` (07:48:57, 07:51:56, 07:53:11, 07:53:24, 08:14:19). Cặp nào cũng trả đủ [ĐO].
- Hạ còn 4/7 (≈ 57 %), trên sàn 50 % của 3.1.1 [SUY từ mức âm].

## 7. Bộ nhớ (PSS tiến trình chính) và khung cụm

- PSS mỗi phút, 37 bản chụp [ĐO]:
  - lúc mới cài: 143 MB;
  - **3D đang chạy: 215–277 MB**;
  - 3D nhả (07:50:54–07:52:58): 129–142 MB;
  - màn chiếu, 3D nhả (08:15:36–08:21:45): 120–127 MB;
  - nạp lại 3D lúc 08:22:47: 226 MB.
- Hai đỉnh đơn lẻ: 347 MB (07:57:02) và 361 MB (08:12:29), phút kế tiếp đã về 231 / 226 MB.
- ⇒ **Không thấy rò dần**. Mức nền phụ thuộc 3D có chạy hay không, nhả 3D lấy lại ~90–100 MB [SUY từ chuỗi số].
- Tiến trình `:wake`: RES 250–326 MB, CPU tức thời có lúc 96–228 % đúng lúc nghe/giải mã giọng [ĐO `top`].
- `wm size -d 1`: **37/37 bản chụp = `Physical size: 1920x720`, không có dòng Override** [ĐO]. Lệnh ép 1920×720 lúc chiếu chỉ sống vài giây rồi `reset` (§4), nên lần chụp mỗi phút không bắt được.

## 8. Việc tiếp

- Chốt giả thuyết khung `0x095` / ECU khởi động lại (cách đo ở seat doc §12.3). Nhật ký HAL trong `All*.zip` bị mã hoá AES; cần đọc `/data/logs/autohallogs/` trực tiếp nếu quyền cho phép (§12.6).
- Thí nghiệm A/F của seat doc §9/§11.6 vẫn còn mở.
