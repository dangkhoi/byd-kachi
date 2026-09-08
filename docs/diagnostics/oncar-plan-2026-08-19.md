# On-car session plan — 2026-08-19 (xe owner)

> **Loại:** Diagnostics (on-car plan) · **Trạng thái:** Current · **Xe:** owner (Seal, DiLink3, `40d`=138).
> Chuẩn bị đã **verify off-car** (APK/probe/adb/syntax). Việc chính: **C1** cài APK B-batch + **C6** probe HUD-nav + kiểm B-batch. Kết quả → cây quyết định cuối.

## Sẵn sàng (đã verify 2026-08-19)
- ✅ APK: `~/Desktop/ClusterNav2.0-B-batch-20260819.apk` (5.24MB) — B1/B2/B4 + logging-off + upcoming-badge.
- ✅ Probe: `~/Desktop/PROBE-HUD-NAV-XE-MINH.command` (exec) → gọi `scripts/vehicle/hud-nav-enable-probe.sh` + `apks/navopen-v4.jar` (đủ verb). Syntax OK; đủ PHASE A/B/C/D + register 420A1010/32B1102E/8e2fcdbf/d61b6746/30100030.
- ✅ adb 1.0.41.
- ⚠️ **KHÔNG rebuild APK** — cây làm việc đang có **B3 dở (7 file uncommitted)**; dùng APK đã build sẵn.

## Các bước (thứ tự)
0. **Kết nối:** `adb devices` thấy xe (WiFi IP hoặc USB).
1. **C1 — Cài APK:** `adb install -r ClusterNav2.0-B-batch-20260819.apk`.
2. **Test B-batch (nhìn cụm/màn):**
   - B1: bật speed badge → **VietMap tự mở**?
   - B2: badge "giới hạn sắp tới" = **xám · 80% · chéo 45° dưới-trái** badge chính?
   - Badge qua **cast** lên cụm — còn sống?
3. **C6 — HUD-nav probe (việc chính):**
   - **Mở GMaps dẫn TRƯỚC** (để PHASE D đọc check-state tên đường `0x420A1010`).
   - Bấm đúp `PROBE-HUD-NAV-XE-MINH.command` → nhập IP xe (trống = USB).
   - **PHASE A/B/D = ĐỌC-ONLY (an toàn)**: capability HUD (`service list`/`dumpsys`) + `30100030`/`38B0002E`/`420A1010` + check-state tên đường.
   - **PHASE C = GHI có rollback** (`32B1102E` _SET + 2 fusion switch): script **dừng hỏi Enter** trước — chỉ muốn đọc thì **Ctrl-C** tại đó. Nhìn kính/cụm khi chạy.
4. **C2 (tiện thì):** qua vòng xuyến → cụm vẽ generic hay directional.

## Mang về
- **File log probe:** `scripts/vehicle/hud-nav-enable-probe-<timestamp>.txt` (nằm cạnh script trong repo).
- **Quan sát:** B2 đúng xám/45°/80%? · VietMap tự mở? · PHASE C register nào ghi được (rc≥0) + kính/cụm có phản ứng?

## Cây quyết định sau khi có kết quả
| Kết quả | → làm gì |
|---|---|
| C6 PHASE B: **cụm CÓ báo mode nav** | → tìm thợ **VDS2100/Autel/XTOOL** bật equipment HUD-nav cụm (`0x38B00030`/vehicleCode) |
| C6: **32B1102E/fusion GHI ĐƯỢC + kính nhúc nhích** | → có đường **no-root** → đào tiếp |
| C6: **từ chối hết + không báo mode nav** | → HUD zin chịu → **HUD BYD rời** (đường anh em) |
| B2 lệch (không xám/45°/80%) | → báo lại, chỉnh code |
| Xe anh em: `hud-roadname-test.bat` biến thể nào hiện | → cách đẩy tên đường lên HUD (fix app nếu là format) |

## Nguồn / liên quan
- Probe: `scripts/vehicle/hud-nav-enable-probe.sh` · Recipe + 3 bí mật: `diagnostics/hud-coding-recipe-2026-08-19.md` · Đường RE: `diagnostics/factory-hud-nav-RE-avenues-2026-08-19.md` · ADR `decisions/0002-hud-nav-coding-locked.md` · Backlog C1/C2/C6/C7 + D6.
