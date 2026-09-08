# KỊCH BẢN LÊN XE — Gộp các probe "làm mò" chưa test (2026-08-11)

> Xe: BYD Seal DiLink 3.0 (fw-2602), Android 10, dual-OS (`fission_single_os=0`), **không root**.
> Chủ: Đăng Khôi (dangkhoi). **Đây là 1 CHỖ DUY NHẤT** cho các thí nghiệm on-car chưa chạy — mở file này khi lên xe.
> Cả hai probe đều **parked-only, chưa từng test trên xe**, dọn bằng **power-cycle nút nguồn vật lý**.

Hai probe độc lập, làm cái nào trước cũng được:

| # | Probe | Mục tiêu | Script | Trạng thái |
|---|-------|----------|--------|-----------|
| **A** | Custom speed-limit trên cluster (#3) | Hiện số giới hạn tốc độ tùy chọn (vd 88) lên biển báo cụm | `scripts/vehicle/hud3-speedlimit-v4.sh` | ⏳ chưa test |
| **B** | Nav overlay ĐÈ LÊN cast (item 3) | Vừa cast app sang cụm, vừa thấy nav (OEM AMapService) nổi trên | `scripts/vehicle/cast-nav-overlay-probe.sh` | ⏳ chưa test |

---

## 1. AN TOÀN (bắt buộc — đọc trước, áp dụng cho cả A và B)
- ✅ **Chỉ test khi xe ĐỖ**, số P, phanh tay. Không test khi đang chạy.
- ✅ **Không root.** Mọi thứ chạy quyền shell (uid 2000).
- ✅ **Dọn sạch = power-cycle head unit bằng NÚT NGUỒN vật lý** (không tính `adb reboot`).
- ⚠️ **Opcode AutoContainer nguy hiểm — TUYỆT ĐỐI KHÔNG dùng:** `1` (ngắt video cụm), `18` (tắt cast), `41` (stress test), `91/92` (crash). Script chỉ dùng opcode an toàn.
- ⚠️ Đã biết: `31`/`0` (flip phẳng + refresh) **re-init container OEM → RESTART màn chính** vài giây (không phải reboot xe). Chấp nhận khi probe; không tự động hoá.
- ⚠️ Cụm loạn/kẹt → power-cycle là hết.

## 2. CHUẨN BỊ CHUNG (làm ở nhà trước khi ra xe)
```bash
adb connect <ip>:5555
adb devices                      # thấy đúng 1 device "device"
export VEH=<ip>:5555
cd <repo>/ClusterNav/scripts/vehicle
```
- Có sẵn: `apks/navopen-v4.jar` (cho Probe A), 2 script trong `scripts/vehicle/`.
- (Tùy) cài bản 1.05 mới nhất để có UI Cast phục vụ Probe B: `adb -s $VEH install -r ../../apk/ClusterNav-1.05-v105-fd63d1a346cc-release.apk`.

---

# PROBE A — Custom speed-limit trên cluster (#3)

> Runbook CHI TIẾT: `docs/diagnostics/oncar-speedlimit-test-2026-08-11.md` (đầy đủ cây quyết định, gõ-tay tương đương, bảng ghi).
> Dưới đây là bản rút gọn để chạy nhanh.

**Cơ chế (RE §22–26 hud-cluster-injection-findings):** số biển báo đi CAN → data provider → ZMQ → cluster (data-item **564/0x234** `trafficSignValue`). ADB không set thẳng (HAL bị bỏ qua; SLA output read-only). 2 cửa KHÔNG root:
- **Cửa A** — đọc `/collect2/byd_datasource_config.xml` qua Binder service DiCarServer (quyền hệ thống) → lấy CAN id + bit layout.
- **Cửa B** — nghe lén khung CAN qua `BYDAutoBigDataDevice` (nếu A chặn).
- Rồi **bơm lại frame** với số của mình qua TEST device `0xAA00020F`.

**Chạy:**
```bash
# Pha 0 — sanity: chứng minh đường render còn sống (opcode 2 → biển hiện 60, rồi power-cycle)
adb -s $VEH shell "fission_screencap -d 1 -p /data/local/tmp/c0.png"; adb -s $VEH pull /data/local/tmp/c0.png ./A_c0_before.png

# Pha 1+2 — recon 2 cửa (đọc config + sniff), lưu ./doorA_*.txt ./doorB_canmon.txt
VEH=$VEH ./hud3-speedlimit-v4.sh
# Nếu Door A "query -> null": xem dumpsys providers script in ra → chạy lại với AUTH khác:
#   AUTH=content://<authority-khác> VEH=$VEH ./hud3-speedlimit-v4.sh
# Door B chủ động: VEH=$VEH CANIDS=0x<id_nghi_ngo> SECS=25 ./hud3-speedlimit-v4.sh

# Pha 3 — ghép FRAME (đặt 88=0x58 vào đúng byte theo layout Cửa A) rồi bơm + tự chụp
VEH=$VEH FRAME=<id..,..,58,..> ./hud3-speedlimit-v4.sh
# Nếu bị SLA ghi đè: tắt fusion trước → bơm → bật lại (hoặc power-cycle)
#   adb -s $VEH shell "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen setraw setting d61b6746 0"
```
**Được = ** biển tốc độ cluster hiện đúng số mình chọn (khác SLA thật) + ảnh `./hud3_10_after_inject.png`. Chi tiết cây quyết định + ẩn số: xem doc chi tiết ở trên.

---

# PROBE B — Nav overlay ĐÈ LÊN cast app (item 3)  ← MỚI

**Vì sao có cửa (RE §1,§11,§13 hud-cluster-injection-findings):** nav overlay VÀ app cast **do CÙNG compositor OEM (Qt cluster) ghép**. Compositor này **đã giữ được nội dung CAN (km/h) ĐÈ lên projection** — đó chính là "curved/keepKmh" (opcode 30/16). ⇒ Giữ **lớp nav** đè lên cast là **khả thi về kiến trúc**; việc còn lại là tìm opcode/sequence `ac 1000 <cmd>` nào re-assert được lớp nav lên trên.

**Opcode clusterDebug (RE từ ClusterDebug.apk):** `39`=simple navigation · `12/13`=show/hide ADAS · `16/17/18`=cast full/half/**OFF** · `30/31`=cong/phẳng · `0`=refresh. Script chỉ dùng **39/12/17/16** (an toàn); TRÁNH 1/18/41.

### B.0 — PRECONDITION (làm trong app TRƯỚC)
1. Mở ClusterNav → **bật Cast** → **cast 1 app** (VietMap/GMaps) lên cụm.
2. Mở nguồn dẫn đường (GMaps/VietMap đang dẫn) để ClusterBroadcaster feed AMapService.
3. Nhìn cụm: xác nhận **nav đang bị app cast che** (đây là điểm xuất phát).

### B.1 — Chạy probe (tự chụp screencap sau mỗi bước)
```bash
VEH=$VEH ./cast-nav-overlay-probe.sh
# (DiLink5 thì SVC=auto_container VEH=$VEH ./cast-nav-overlay-probe.sh)
```
Script tuần tự (mỗi bước 1 ảnh `./cnp_*.png`, chỉ dùng opcode an toàn, cuối cùng khôi phục full-cast op 16):
| Ảnh | Hành động | Câu hỏi |
|-----|-----------|---------|
| `cnp_00_baseline` | — | nav đang bị che (điểm xuất phát) |
| `cnp_01_navbcast` | re-broadcast nav frame | chỉ re-assert nav có nổi lại lên trên không? |
| `cnp_02_simplenav39` | `ac 1000 39` + nav | opcode 39 (simple nav) có nâng lớp nav? |
| `cnp_03_adas12_nav` | `ac 1000 12` + nav | show ADAS (12) có kéo overlay lên trước? |
| `cnp_04_casthalf17` | `ac 1000 17` (cast HALF) | half-cast có chừa 1 nửa cho gauges/nav cạnh app? |

### B.2 — Đọc kết quả
- **Bước nào cho thấy nav (mũi tên + cự ly + tên đường) NỔI TRÊN app cast = lời giải.** Ghi lại bước đó.
- Nếu **không bước nào** giữ nav trên → trim này compositor ghim full-cast trên nav; fallback: **cast HALF** (bố cục 1 nửa app + 1 nửa gauges/nav), hoặc **nav-only (Cast off)** để chắc chắn thấy nav.

### B.3 — Sau khi tìm ra (agent làm off-car)
Báo lại bước thắng → agent wire opcode/sequence đó vào **luồng cast** (phát ngay sau khi `AppMover.castToCluster` thành công), để nav luôn hiện khi đang cast. KHÔNG tự động hoá opcode nào làm restart màn chính.

---

## 5. BẢNG GHI KẾT QUẢ (điền khi test)
### Probe A (speed-limit)
| Bước | Output tóm tắt | Ảnh | Verdict |
|------|----------------|-----|---------|
| Pha 0 opcode2→60 | biển = ____ | | ☐ render OK |
| Cửa A config | CANID=____ bit=____ | doorA_datasource_config.txt | ☐ đọc được ☐ null |
| Cửa B sniff | frame khớp số hiện=____ | doorB_canmon.txt | ☐ |
| Pha 3 inject | số hiện=____ | hud3_10_after_inject.png | ☐ ra 88 ☐ chưa |

### Probe B (nav trên cast)
| Bước | Nav có nổi trên cast? | Ảnh | Ghi chú |
|------|-----------------------|-----|---------|
| baseline | (che) | cnp_00_baseline.png | |
| navbcast | ☐ có ☐ không | cnp_01_navbcast.png | |
| op 39 | ☐ có ☐ không | cnp_02_simplenav39.png | |
| op 12 + nav | ☐ có ☐ không | cnp_03_adas12_nav.png | |
| cast HALF 17 | ☐ có ☐ không | cnp_04_casthalf17.png | |
| **Bước thắng** | ____ | | báo agent để wire |

---

## 6. DỌN DẸP (bắt buộc trước khi rời xe)
- **Power-cycle head unit bằng nút nguồn vật lý** (dọn mọi frame bơm + trạng thái compositor).
- Probe A: nếu đã `setraw setting d61b6746 0` (tắt fusion) mà chưa bật lại → bật lại hoặc power-cycle.
- Probe B: script đã khôi phục full-cast (op 16); power-cycle cho chắc.
- `navopen.jar` để lại `/data/local/tmp/` vô hại.

## 7. THAM CHIẾU
- Probe A chi tiết: `docs/diagnostics/oncar-speedlimit-test-2026-08-11.md` · tool `apks/navopen-v4.jar` · script `scripts/vehicle/hud3-speedlimit-v4.sh`.
- Probe B: script `scripts/vehicle/cast-nav-overlay-probe.sh` · rationale RE `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §1,§11,§13.
- Cast/opcode nền: `core/.../simplified/ProjectionManager.kt` (30→16→35 open, 18→0 close), `core/.../carexec/CarExecClusterProjectionCatalog.kt` (opcode map), spec `docs/specs/cast-enable-toggle.html`.
