# PLAYBOOK LÊN XE — Kachi 1.55 (56) — DELTA trên 1.53

> **Trạng thái**: Historical — playbook cho 1.55; runbook hiện hành là `oncar-runbook-2.73.md` · **Cập nhật**: 2026-09-14 · **Mục đích**: phần DELTA trên playbook 1.53 — cách cài/nhận bản 1.55 + bốn thứ MỚI cần đo.

> Bản này **KHÔNG thay** `oncar-playbook-kachi-1.53.md` — nó là phần **thêm** cho những gì đổi từ 1.53 (54) → 1.55
> (56). Toàn bộ quy trình kết nối, baseline, bật cầu kiểm thử, OTA, và 19 mục test rủi-ro-tăng-dần vẫn đọc ở 1.53.
> Ở đây chỉ có: (a) cách cài/nhận bản 1.55, (b) bốn thứ MỚI cần đo, mỗi thứ trỏ về đúng mục của 1.53 để chạy.

Chủ: dangkhoi · Ngày soạn: 2026-09-14 · Xe: DiLink3 (DL3), Kachi uid 10135, chữ ký 177b2fc5 (KHÔNG platform).

Quy ước bằng chứng **[ĐO]/[SUY]/[ĐOÁN]/[CHƯA BIẾT]** như 1.53 §Quy ước (CLAUDE.md §2). Bản này viết trước khi
lên xe ⇒ mọi kỳ vọng dưới đây là **[SUY] từ RE off-car**, buổi test biến chúng thành [ĐO] hoặc thành lỗi thật.

---

## A. CÀI / NHẬN BẢN 1.55

- Kênh OTA không đổi: `apk/Kachi-1.55-release.apk` trên `dangkhoi/byd-kachi` nhánh `main`. Bản đang cài (1.54)
  bấm *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* → nhận 1.55 (xem 1.53 §2.5).
- Ký cùng keystore `~/.kachi/kachi-release.keystore` ⇒ `pm install -r` KHÔNG mất dữ liệu (hồ sơ/ô/cấu hình giữ).
- Kiểm bản đang chạy: `dumpsys package com.byd.launcher | grep versionName` → phải là `1.55`.

---

## B. CÓ GÌ MỚI TỪ 1.53 → 1.55 (bốn thứ)

| Việc | Đo ở mục (dưới) | Vì sao KHÔNG bỏ qua được |
|---|---|---|
| **HAL-🚗 — sửa ROUTE/METHOD 5 control theo RE** (đèn đọc · nhiệt độ AC · ion · nhớ ghế · giới hạn sạc) | §B.1 (qua sweep 1.53 §2.18) | Đây là **giả thuyết trung tâm** của bản này: RE off-car chứng minh chúng route SAI thiết bị / gọi method không tồn tại. Chỉ xe mới nói được route đúng có làm chúng CHẠY không |
| **S1b — ẩn/hiện thanh nút xe** (công tắc thứ 5 cạnh 4 viền) | §B.2 | Vùng ô lấp trọn màn khi ẩn — hình học đó máy ảo đo được, nhưng "đổi hồ sơ có mang theo trạng thái ẩn không" thì phải xe/đủ hồ sơ |
| **S1b — margin: khe ô 75% + lề ngoài đều 4 cạnh** | §B.3 | Thẩm mỹ owner chốt bằng mắt trên cụm thật; máy ảo chỉ xác nhận số px, không xác nhận "nhìn đã đều chưa" |
| **SpeedBadge — bám màn cụm ĐỘNG (không ghim display 1)** | §B.4 | [ĐO] cụm xe này là **display 2** (fission_bg_xdja), không phải 1 ⇒ badge cũ ghim display 1 có thể chưa từng lên đúng chỗ |

---

## B.1 — HAL: 5 CONTROL SỬA ROUTE/METHOD (giả thuyết trung tâm)

**Chạy đúng bộ sweep 1.53 §2.18** (`scripts/vehicle/kachi/71-hal-sweep.sh <ip-xe>:5555`, cầu kiểm thử phải SỐNG).
Bản 1.55 đổi route/method của 5 control theo `docs/diagnostics/byd-hal-permission-RE-2026-09-14.md` §4/§5a.
Cột `route`/`device` trong CSV nay phải khớp bảng dưới — và câu hỏi thật là cột `accepted` + owner nhìn mắt.

| id | Trước 1.55 (SAI) | Bản 1.55 (theo RE) | Kỳ vọng trên xe | Tin cậy cổng chữ ký |
|---|---|---|---|---|
| `readl` đèn đọc | feature `0x4f50003a` → LIGHT (1004) ⇒ *"no permission device 1004"* | → **SETTING (1023)** (`halDevice`) | Đèn cabin BẬT/TẮT thật | **CAO** — SETTING_SET đã chạy (ghế mát) [ĐO] |
| `temp` nhiệt độ AC | `setTemprature` (KHÔNG tồn tại) ⇒ xe không nhận | → `setAcTemperature(0, °C, 0, 1)` trên AC (1000) | Nhiệt độ AC đổi theo −/+ | **CAO** — AC_SET đã qua (gió) [ĐO] |
| `anion` ion âm | feature → AC (1000) | → **PM2P5 (1008)** | Ion bật/tắt (khó thấy — nghe tiếng/đèn báo) | **CAO** — PM2P5_SET đã chạy (lọc bụi) [ĐO] |
| `seat_memory` nhớ ghế | feature → BODYWORK (1001) | → **SETTING (1023)** | Ghi/gọi vị trí ghế lái | CAO (SETTING_SET) |
| `charge_cap` giới hạn sạc | feature → STATISTIC (1014) | → **SETTING (1023)** | Bật/tắt giới hạn dòng sạc | CAO |

- **Pass (giả thuyết đúng)**: 5 control này chuyển từ `accepted:false`/*"no permission"* (bản 1.54) sang
  `accepted:true` + hiệu ứng vật lý THẬT. Ghi CSV `carlog/hal-sweep.csv`, đối chiếu cột `device`.
- **Fail một phần**: nếu `accepted:true` mà xe im (rc=0 no-op) → route đúng thiết bị nhưng **method/giá trị** còn
  sai; ghi cột `ghi_chu_owner`, KHÔNG kết luận vội (§2/§14 — cần đo thêm, không đoán).
- **`temp` cần chú ý riêng**: đây là control DUY NHẤT đổi **method** (không chỉ device). Nếu `setAcTemperature`
  vẫn không có trên trim này → HAL trả sentinel; đọc `hal_line` để phân biệt "method trượt" với "device chặn".
- **KHÔNG đổi**: các control [SUY]/[CHƯA BIẾT] của RE (`headl`, `child_lock`, `wireless_charge`, `wiper`,
  `adas_cpd`, `mirror_fold_btn`) — thiết bị đích của chúng (INSTRUMENT/DOOR_LOCK/CHARGING/WIPER) **chưa đo cổng
  chữ ký**, nên bản 1.55 giữ nguyên; sweep hôm nay là lượt đo tầng-1 (§14) cho chúng, chưa phải lượt sửa.

## B.2 — S1b: ẩn/hiện thanh nút xe

- Đường: *Cài đặt › Thanh trạng thái & thanh nút › **Hiện thanh nút xe*** (công tắc, mặc định BẬT).
- **Tắt** → thanh nút biến mất, vùng ô lấp trọn màn (không còn khe dock). **Bật lại** → thanh về ĐÚNG viền +
  đúng bộ nút đã chọn trước đó (không quên về BOTTOM).
- Theo hồ sơ: đổi hồ sơ khác (đang bật thanh) → thanh hiện; quay lại hồ sơ vừa tắt → vẫn ẩn. (Cùng đường
  `dock_visible` trong ảnh chụp hồ sơ — 1.53 §2.8.)
- Kiểm nhanh bằng cầu: JSON trạng thái có `bars.dock_visible: false` sau khi tắt.

## B.3 — S1b: margin (khe ô 75% + lề ngoài đều)

- Chỉ **nhìn mắt** trên cụm: khe giữa các ô hẹp lại một nhịp (9dp thay 12dp); lề quanh toàn màn **đều nhau ở
  cả 4 cạnh** (trước: trái/phải 16, trên 4, dưới 12 — lệch). Rãnh quanh cửa sổ app trong ô cũng bằng khe giữa ô.
- Không có lệnh đo — đây là câu hỏi thẩm mỹ owner. Chụp một ảnh màn chính gửi về nếu thấy còn lệch.

## B.4 — SpeedBadge: bám màn cụm động (display 2)

- Bối cảnh: [ĐO] cụm xe này là **display 2** (fission_bg_xdja). Bản ≤1.54 ghim `CLUSTER_DISPLAY_ID=1` ở vòng
  đời badge ⇒ khi cụm không phải display 1, badge có thể không lên/không tự gắn lại. 1.55 theo dõi
  `resolvedDisplayId` (id thật đã gắn) cho cả `onDisplayAdded`/`onDisplayRemoved`.
- Đo: bật chiếu-cụm (1.53 §2.12), bật badge tốc độ (*Cài đặt › Dẫn đường*), chạy VietMap có biển báo tốc độ →
  badge phải hiện **trên cụm**. Tắt→bật lại chiếu → badge tự gắn lại (không cần mở lại app).
- Nếu badge vẫn không lên: đọc logcat `SpeedBadgeOverlay` — dòng *"overlay initialized for display N"* nói nó gắn
  vào display nào; nếu N=−1/không có dòng ⇒ `resolveClusterDisplay` không thấy màn PRESENTATION nào (cụm fission
  có thể KHÔNG khai CATEGORY_PRESENTATION) → đây là số đo mới cần mang về (đường shell của X2 `ClusterDisplayResolver`
  mới là đường phát hiện đã chứng minh, badge dùng DisplayManager API là đường thứ hai — [CHƯA BIẾT] hợp nhất).

---

## C. THỨ TỰ ĐỀ NGHỊ (chèn vào lịch 1.53)

1. Làm hết 1.53 §1 (kết nối + baseline + bật cầu kiểm thử) như thường.
2. §B.1 chạy CÙNG bước 1.53 §2.18 (sweep) — chỉ đọc thêm 5 dòng route mới trong CSV.
3. §B.2 + §B.3 chèn vào 1.53 §2.6 (Settings ghi thật) và §2.8 (hồ sơ) — cùng chỗ đang thao tác Cài đặt.
4. §B.4 chèn vào 1.53 §2.12 (sống chung chiếu-cụm) — cùng lúc bật cast.

## D. Nguồn / liên quan
- `oncar-playbook-kachi-1.53.md` — quy trình đầy đủ (bản này chỉ là delta).
- `byd-hal-permission-RE-2026-09-14.md` — RE cơ chế route/method, nguồn của §B.1.
- `oncar-session-2026-09-14-findings.md` — findings buổi 09-14 (cụm = display 2, cast, HAL per-feature).
- `scripts/vehicle/kachi/71-hal-sweep.sh` — bộ quét HAL từng control.
