# KỊCH BẢN LÊN XE — Test bảng tốc độ tùy chọn trên cluster (#3) — 2026-08-11

> ⛳ **ĐIỂM VÀO CHUNG (cả 2 probe on-car chưa test) = `docs/diagnostics/oncar-probes-2026-08-11.md`.**
> File này là **runbook CHI TIẾT của Probe A** (speed-limit). Probe B (nav đè lên cast) nằm cùng chỗ ở doc gộp.


> Xe: BYD Seal DiLink 3.0 (fw-2602), Android 10, dual-OS (`fission_single_os=0`), **không root**.
> Chủ: Đăng Khôi (dangkhoi). Để đó — khi nào lên xe thì mở file này làm theo.
> Nền tảng: `apks/navopen-v4.jar` + `scripts/vehicle/hud3-speedlimit-v4.sh` (đã build & verify off-car 2026-08-11).
> Chi tiết/bằng chứng: `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §22–26.

---

## 0. Tóm tắt 30 giây
- **Mục tiêu:** hiện **số giới hạn tốc độ tùy chọn** (vd **88**) lên biển báo tốc độ ở cụm đồng hồ.
- **Vì sao khó:** số đó đi theo đường CAN → data provider → ZMQ → cluster (data-item **564 / 0x234** `trafficSignValue`). ADB **không set thẳng** được (HAL bị bỏ qua; SLA output read-only). Đã chứng minh cạn kiệt (§24).
- **2 cửa KHÔNG root (mới, 2026-08-11):**
  - **Cửa A** — đọc file cấu hình CAN `/collect2/byd_datasource_config.xml` qua **Binder service của DiCarServer** (chạy quyền hệ thống → vượt "Permission denied" của shell). Lấy được **CAN id + layout bit** của biển báo.
  - **Cửa B** — **nghe lén khung CAN** ngay trên máy qua HAL `BYDAutoBigDataDevice` (nếu Cửa A bị chặn).
  - Rồi **bơm lại khung CAN** với số của mình qua TEST device `0xAA00020F`.
- **"Được" = ** biển tốc độ trên cluster hiện đúng số mình chọn + có ảnh chụp.

---

## 1. AN TOÀN (bắt buộc — đọc trước)
- ✅ **Chỉ test khi xe ĐỖ, cần số P, phanh tay.** Không test khi đang chạy.
- ✅ **Không root.** Mọi lệnh dưới đây chạy quyền shell (uid 2000) qua navopen.
- ✅ **Dọn sạch = power-cycle head unit bằng NÚT NGUỒN vật lý** (không tính `adb reboot`). Bơm CAN chỉ là frame giả tạm thời; reboot là biển về giá trị SLA thật.
- ⚠️ **Chỉ bơm ĐÚNG CAN id của biển báo tốc độ.** KHÔNG bơm arbitration id lạ (tránh nhiễu bus).
- ⚠️ Nếu màn cluster loạn/kẹt → power-cycle là hết.

---

## 2. TIÊU CHÍ NGHIỆM THU
- [ ] Biển giới hạn tốc độ trên cluster hiện **đúng số mình chọn** (vd 88), **khác** giá trị SLA thật lúc đó.
- [ ] Có **ảnh `fission_screencap`** của cluster làm bằng chứng.
- [ ] **Lặp lại được** (chạy lại ra lại số đó).

---

## 3. CHUẨN BỊ (làm ở nhà, trước khi ra xe)
- Laptop có `adb`, đã kết nối được Wi-Fi của xe.
- Có sẵn: `apks/navopen-v4.jar`, `scripts/vehicle/hud3-speedlimit-v4.sh`.
- Biết IP:port adb của xe.

```bash
adb connect <ip>:5555
adb devices                    # thấy đúng 1 device "device"
export VEH=<ip>:5555
cd <repo>/ClusterNav/scripts/vehicle
```

Prefix navopen (khi gõ tay, không qua script):
```bash
NAV="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
adb -s $VEH push ../../../apks/navopen-v4.jar /data/local/tmp/navopen.jar
```

---

## 4. PHA 0 — Sanity (2 phút)
1. Chụp cluster gốc để so sánh:
   ```bash
   adb -s $VEH shell "fission_screencap -d 1 -p /data/local/tmp/c0.png"
   adb -s $VEH pull /data/local/tmp/c0.png ./c0_before.png
   ```
2. (Tùy chọn) chứng minh **đường render biển báo còn sống**: opcode 2 làm biển hiện **60** (đồng thời mọi đèn cảnh báo sáng — đây là artifact đã biết 2026-07-29):
   ```bash
   adb -s $VEH shell "$NAV ac 1000 2 \"\""     # sign -> 60
   # ... nhìn cluster ... rồi:
   adb -s $VEH shell "$NAV ac 1000 3 \"\""     # tắt (nếu không sạch -> power-cycle)
   ```
   → Nếu hiện 60 = đường render OK, chỉ còn thiếu "giá trị tùy chọn" (đó là việc của Cửa A/B). **Nhớ power-cycle sau bước này.**

---

## 5. PHA 1 — CỬA A: đọc config (ĐƯỜNG CHÍNH)
Chạy script recon (tự push jar, dump providers, đọc 2 file config):
```bash
VEH=$VEH ./hud3-speedlimit-v4.sh
```
Kết quả lưu ra: `./doorA_datasource_config.txt`, `./doorA_datacollectioncfg.txt`, `./doorB_canmon.txt`.

**Đọc `./doorA_datasource_config.txt`:**
- Nếu **có nội dung** (XML/text) → TÌM entry của biển báo tốc độ (`trafficSign`, `speedLimit`, `LimitTrafficSymbol`, hoặc data-item **564/0x234**). Ghi lại:
  - `CANID = 0x______` (arbitration id)
  - `start-bit = ____`, `length = ____ bit`, `factor = ____`, `offset = ____`
- Đối chiếu `./doorA_datacollectioncfg.txt` xem cùng CANID có trong danh sách thu thập không (xác nhận).

**Nếu thấy `query -> null` hoặc `no binder`** (service không gọi được / sai authority):
- Xem phần `dumpsys package providers` script in ra → tìm authority khác chứa `collect2 / spi / CarServiceProvider`.
- Chạy lại với authority đó:
  ```bash
  AUTH=content://<authority-khác> VEH=$VEH ./hud3-speedlimit-v4.sh
  ```
- Vẫn không được → sang **PHA 2 (Cửa B)**.

**Gõ tay tương đương (nếu cần):**
```bash
adb -s $VEH shell "$NAV readcfg /collect2/byd_datasource_config.xml"
adb -s $VEH shell "$NAV readcfg /collect2/dataCollect/datacollectioncfg"
adb -s $VEH shell "dumpsys package providers | grep -iE 'byd|spi|collect2|CarServiceProvider'"
```

---

## 6. PHA 2 — CỬA B: nghe lén CAN (FALLBACK nếu A bị chặn)
Cửa B nằm trong cùng lần chạy recon. Đặt `CANIDS` để đăng ký id nghi ngờ (lấy từ Cửa A hoặc đoán từ dải ADAS):
```bash
VEH=$VEH CANIDS=0x<id_nghi_ngo>[,0x<id2>] SECS=25 ./hud3-speedlimit-v4.sh
```
Không có id nào → nghe thụ động (dựa trên id CanDataCollect đã thu sẵn):
```bash
VEH=$VEH SECS=25 ./hud3-speedlimit-v4.sh
```
Đọc `./doorB_canmon.txt`: mỗi dòng `[can] id=0x.... sub=.. ch=.. data=....`.

**Cách tìm frame biển báo khi xe ĐỖ (giá trị SLA đứng yên):**
- Xem cluster đang hiện giới hạn bao nhiêu (vd 50). Tìm frame có **byte = 0x32 (50)** → nhiều khả năng byte đó chứa số. Ghi lại `CANID` + vị trí byte.
- (Wiggle bằng cách đổi giới hạn thật cần xe chạy/nhìn biển → **không làm khi đỗ**; ưu tiên Cửa A.)

**Gõ tay:** `adb -s $VEH shell "$NAV canmon 25 0x<id>"` hoặc `canreg 0x<id>` để chỉ nạp bảng.

---

## 7. PHA 3 — GHÉP FRAME + BƠM + KIỂM CHỨNG
Từ `CANID` + layout bit (Cửa A) hoặc frame bắt được (Cửa B), tạo **wholeFrame** dạng comma-hex, đặt số **88** vào đúng byte/bit. **CAN id nằm ngay trong các byte** (giống ClusterDebug `--es wholeFrame`).

> Ví dụ MINH HOẠ (KHÔNG phải giá trị thật — thay bằng của bạn): canid `0x234`, số ở byte cuối, `88 = 0x58`:
> `FRAME=00,00,02,34,00,00,58`

Bơm + tự chụp trước/sau:
```bash
VEH=$VEH FRAME=00,00,02,34,00,00,58 ./hud3-speedlimit-v4.sh
```
Script lặp bơm `HOLD` lần (mặc định 8) + thử cả chiều UP `0xAA000210`, rồi chụp `./hud3_00_before_inject.png` + `./hud3_10_after_inject.png`.

**Nếu chưa ra số:**
- Giá trị SLA thật ghi đè → tắt fusion trước rồi bơm (từ script cũ `hud3-speedlimit.sh`):
  ```bash
  adb -s $VEH shell "$NAV setraw setting d61b6746 0"     # safety-aid fusion OFF
  VEH=$VEH FRAME=<frame> HOLD=15 ./hud3-speedlimit-v4.sh
  adb -s $VEH shell "$NAV setraw setting d61b6746 1"     # bật lại (hoặc power-cycle)
  ```
- Thử dịch byte/bit khác (đọc lại layout), hoặc thử chiều UP thủ công:
  ```bash
  for i in $(seq 1 8); do adb -s $VEH shell "$NAV setbytes test AA000210 <frame>"; sleep 0.4; done
  ```

---

## 8. CÂY QUYẾT ĐỊNH
- Cửa A đọc được config → có `CANID`+bit → **PHA 3** → hiện 88 = ✅ XONG.
- Cửa A `null`/`no binder` → thử `AUTH` khác từ providers dump → vẫn chặn → **Cửa B**.
- Cửa B thấy frame khớp số đang hiện → suy ra `CANID`+byte → **PHA 3**.
- Cả A và B đều chặn → **hết đường ADB** (đúng kết luận §24). Còn lại: root (đã loại vì không an toàn) hoặc CAN hardware. **Giữ lại toàn bộ `./doorA_*.txt`, `./doorB_canmon.txt`** để phân tích tiếp off-car.

---

## 9. DỌN DẸP (bắt buộc trước khi rời xe)
- **Power-cycle head unit bằng nút nguồn vật lý** → xóa mọi frame bơm, biển về SLA thật.
- Nếu có `setraw setting d61b6746 0` (tắt fusion) mà chưa bật lại → `... d61b6746 1` hoặc power-cycle.
- `navopen.jar` để lại `/data/local/tmp/` là vô hại (có thể `adb shell rm /data/local/tmp/navopen.jar`).

---

## 10. BẢNG GHI KẾT QUẢ (điền khi test)
| Bước | Lệnh chính | Output tóm tắt | Ảnh | Verdict |
|------|------------|----------------|-----|---------|
| Pha 0 sanity | `fission_screencap -d 1` | | c0_before.png | ☐ |
| Pha 0 opcode2→60 | `ac 1000 2` | biển = ____ | | ☐ render OK |
| Pha 1 Cửa A cfg | `readcfg …datasource_config.xml` | CANID=____ bit=____ | doorA_datasource_config.txt | ☐ đọc được ☐ null |
| Pha 1 Cửa A cfg2 | `readcfg …datacollectioncfg` | có CANID? | doorA_datacollectioncfg.txt | ☐ |
| Pha 1 authority | dumpsys providers | authority=____ | | ☐ |
| Pha 2 Cửa B sniff | `canmon 25 0x____` | frame khớp số hiện=____ | doorB_canmon.txt | ☐ |
| Pha 3 inject | `FRAME=____` | | hud3_10_after_inject.png | ☐ hiện 88 ☐ chưa |
| Dọn | power-cycle | biển về thật | | ☐ |

Số mình chọn để test: **____** (vd 88). Giá trị SLA thật lúc test: **____**.

---

## 11. ẨN SỐ & FALLBACK (những thứ chỉ biết trên xe)
1. **`CarServiceProvider.query` có gọi được không / có gate quyền?** → nếu `null`, đổi `AUTH` theo providers dump.
2. **`readcfg` trả về text không?** Nếu `isFileExist=true` mà `readTextFile=null` → service chặn nội dung → dùng Cửa B.
3. **`canmon` (BigData) có bị chặn server-side (như Panorama) không?** → nếu `registerListener` lỗi/`BigDataDevice null` → chỉ còn Cửa A hoặc CAN hardware.
4. **Layout bit** trong config có thể cần thử vài vị trí byte trước khi số hiện đúng.

---

## 12. THAM CHIẾU
- Công cụ: `apks/navopen-v4.jar` (verbs: `readcfg`, `canmon`, `canreg`, `setbytes test AA00020F`).
- Runner: `scripts/vehicle/hud3-speedlimit-v4.sh`.
- Bằng chứng/RE: `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §22–26.
- State: `docs/_handoff/cluster-hud-injection-STATE.md`.
- Artifact cũ liên quan: `scripts/vehicle/hud3-speedlimit.sh` (combo tắt SLA/fusion), incident "60" (§20).
