# ON-CAR HANDOFF — sau phiên TỐI 2026-08-12 (verify 1.10 + B6 done)

> **Supersedes** `oncar-handoff-2026-08-12.md` (bản sáng, verify 1.07/1.08 — nay đã lỗi thời).
> Xe: BYD Seal DiLink 3.0, Android 10 (API 29), **không root**. Chủ: Đăng Khôi (dangkhoi).
> **1 CHỖ DUY NHẤT** cho lần lên xe tới. Tất cả **parked-only** (số P, phanh tay). Dọn = **power-cycle nút nguồn vật lý** (không tính `adb reboot`).

---

## 0. TRẠNG THÁI (sau phiên tối 2026-08-12)

| Hạng mục | Trạng thái |
|---|---|
| **1.10** (op39 self-diagnose · 3 cast fix · DPI-label fix · gộp 1.08 OTA WIP) | **Ở working tree local — CHƯA commit.** Off-car green (core 89 + app 50 test files, 0 fail). **Đã verify ON-CAR** (§2). |
| op39 "Giữa + ETA" (bug gốc buổi sáng) | ✅ **FIXED, verified on-car** |
| #1 resize không lưu size | ✅ **FIXED, verified** (bounds giữ qua reinstall + re-cast) |
| #2 DPI split không lưu + default 360 | ✅ **FIXED, verified** (DPI tự áp lại khi re-cast; default 240) |
| #3 nút nổi trái/phải (return-vs-cast) | ✅ **PASS** (owner xác nhận) |
| DPI label kẹt "320" | ✅ **FIXED** (1.10 hiện giá trị thật) |
| **B6** nav-mode opcode probe | ✅ **DONE** — mapping §3. Kết luận: "Màn hình nhỏ" **không phải opcode**. |
| B7 overlay biển tốc độ (coexistence) | ⏳ **chưa làm** |
| B8 bug-b CarPlay trigger | ⏳ **chưa làm** (cần CarPlay cắm vào) |
| Probe A (bơm speed-limit) | ⛔ **BỊ CHẶN — đã chốt** (khỏi mò) |

**Bản đang cài trên xe cuối phiên:** 1.10 (versionCode 110). Xe trước đó là **1.06** (1.07/1.08/1.09 chưa từng lên xe).

---

## 1. CHUẨN BỊ (nối máy) — cho phiên sau
```bash
export VEH=<vehicle-ip>:5555        # HỎI lại IP, ĐỪNG đoán (hotspot có thể đổi)
adb connect $VEH && adb devices     # thấy DiLink3.0, KHÔNG nhầm emulator
# cài lại bản mới nhất:
adb -s $VEH install -r app/build/outputs/apk/release/app-release.apk
```
- **Cụm = `fission_screencap -d 0`** trên trim này (help ghi ngược). Chụp `-d 0` thấy đồng hồ ⇒ đúng. IVI = `-d 1`.
- **Loopback in-app (dadb localhost:5555) chạy tốt khi đã `adb tcpip 5555`** — verify tối nay: log `SimpleCast: shell OK: exit=0` mỗi ~1s.

### Opcode CẤM (mở rộng SAU B6 — app phải blocklist)
`1` (ngắt video cụm) · `18` (tắt cast) · `41` (stress) · `91/92` (crash)
— **THÊM MỚI từ B6:** **`43` (bơm cảnh báo "Hệ thống phanh bị lỗi" GIẢ)** · `42` (bật overlay FPS + màn điện năng) · `45` (overlay cấu hình ADAS). Ba op này KHÔNG được app bắn.

---

## 2. VERIFIED ON-CAR — 1.10, phiên tối 2026-08-12 (tất cả PASS)

| Case | Kết quả | Bằng chứng |
|---|---|---|
| op39 "Giữa + ETA" | ✅ FIXED | status D3 = ASSERTED (xanh "đang hiện Giữa + ETA") + overlay giữa cụm; loopback OK |
| D3 status line | ✅ | thấy cả ASSERTED (xanh) lẫn GATED_CAST (hổ phách "Cast đang bật — nav nhường cụm") |
| #1 resize giữ size | ✅ | GMaps `[20,62][1536,560]` khôi phục qua cả **reinstall + re-cast** |
| #2 DPI split lưu | ✅ | log re-cast: `am task resize … 560` + `wm density 160 -d 1` **tự áp lại cho CẢ 2 nửa** |
| #2 default DPI 240 | ✅ | openProjection áp `wm density 240` (không còn native ~360) |
| #3 nút nổi trái/phải | ✅ PASS | owner xác nhận: nhấn Trái = trả trái, Phải = trả phải; trống = cast app đang mở |
| DPI label | ✅ | nút "DPI:" hiện giá trị thật (không kẹt 320) |

**Giới hạn phần cứng (A10):** `wm density` là **display-global** — cả cụm 1 giá trị DPI. DPI split lưu per-nửa nhưng áp chung 1 số (không để trái/phải khác DPI thật). Muốn per-side thật → per-task scaling (scope riêng, lớn).

---

## 3. B6 RESULT — nav-mode opcode mapping (DONE, 17 screencap)

| op | Quan sát cụm | Loại |
|----|---|---|
| **39** | Nav **GIỮA + ETA** (đầy đủ) · gauge tròn | ✅ nav-force center (mode DUY NHẤT đáng tin) |
| 8 | Nav vẫn giữa · gauge "classic" (tròn) | đổi gauge |
| 9 | Nav vẫn giữa · gauge "tech" (vuông) | đổi gauge |
| 34 / 35 / 40 | Nav **gọn hơn** (mũi tên nhỏ + tên đường + ETA) · gauge vuông | đổi dashboard, nav compact (ứng viên "Nhỏ" gần nhất) |
| 42 | Màn **điện năng (kWh/100km)** thay nav + **bật FPS debug** | ⛔ debug → blocklist |
| **43** | **Cảnh báo "Hệ thống phanh bị lỗi" GIẢ (inject)** | ⛔ NGUY HIỂM → blocklist |
| 44 / 36 / 37 / 38 | Center trống (nav mất — do tích lũy, không kết luận sạch) | — |
| 12 / 13 | show/hide ADAS · center trống | ADAS toggle |
| 45 | Overlay **cấu hình ADAS** (list đỏ ACC/AEB/LKA/…) | ⛔ debug diag → blocklist |
| 6 / 7 | **Day (nền sáng) / Night (nền tối)** — đổi rõ | ✅ sanity kênh 1000 OK |

**3 câu B6 trả lời:**
1. **op39 = "Giữa + ETA"** (center đầy đủ). op34/35/40 = nav gọn hơn (kèm đổi kiểu gauge).
2. **Opcode ra "Màn hình nhỏ" (dải nav nhỏ ở đỉnh) = KHÔNG CÓ** trên trim DiLink3.0 này → là **setting trong menu AMAP OEM**, app không ép được bằng ch1000 opcode.
3. **Revert op39:** chưa có opcode revert sạch → tạm dùng **op0 (refresh)** hoặc **power-cycle**.

> Screencap B6 (17 ảnh) đang ở **/tmp/nm_op*.png trên máy dev — CHƯA lưu repo** (chứa tên đường thật → cần redact/review trước khi commit vào `docs/diagnostics/`).

---

## 4. PENDING OFF-CAR (agent làm — chờ owner quyết)

1. **Nút "Nhỏ / ở trên"** — vì không có opcode "small-top":
   - (a) **Giữ provisional + sửa label trung thực** ("theo cài đặt OEM AMAP") — an toàn, đúng thực tế; hoặc
   - (b) **Map op34/35** (nav gọn hơn) — nhưng kèm đổi kiểu gauge (side-effect).
   - → **CHỜ OWNER CHỌN a/b.**
2. **Blocklist opcode trong app:** thêm **43, 42, 45** vào guard (giống 1/18/41/91/92) — không bao giờ bắn.
3. **Teardown op39** khi chuyển sang "Nhỏ/ở trên": wire thử **op0 (refresh)**; nếu không sạch → chỉ power-cycle.
4. **Commit 1.10** + security-scan (workflow §6) + update `README.md` (đang ghi 1.05) + specs. **HIỆN CHƯA commit gì.**

---

## 5. PENDING ON-CAR PROBE (buổi sau)

- **B7 — overlay biển tốc độ (vòng tròn xanh) coexistence:** vẽ 1 surface nhỏ trên cụm (display 1) có sống chung với gauges/nav không, hay bắt buộc full-cast? Thử `am start --display 1 -n com.byd.clusternav/.ClusterNavActivity` (demo=true) + cast-half (op17) xem còn chỗ gauges/nav không. → quyết định feasibility `OverlaySpeedSignPort`.
- **B8 — bug-b:** cast CarPlay → trả về → cast CP lại; nếu ClusterNav kẹt trên cụm → `adb -s $VEH shell "am stack list" > bugb_stacklist.txt` NGAY lúc kẹt.

---

## 6. DỌN DẸP (bắt buộc trước khi rời/lái)
- **Power-cycle nút nguồn vật lý** — dọn opcode tích lũy + inject debug (FPS/cảnh báo giả) + trạng thái compositor.
- Sau B6, cụm có thể còn FPS overlay / màn lạ → **KHÔNG lái khi cụm chưa sạch.**

---

## 7. THAM CHIẾU
- Specs: `docs/specs/nav-cluster-op39-selfdiagnose.html` · `docs/specs/cast-resize-dpi-bubble-fixes.html`
- op39 wiring: `app/.../modules/clustercast/ClusterNavLaneWidget.kt` + `NavClusterOp39Status.kt` + hook `NavNotificationListener.kt`
- Cast fixes: `core/.../simplified/{SimpleCastCoordinator,CastDensityControl,SimpleCastModels,BubbleGesturePlanner}.kt` · `app/.../BubbleActionDispatcher.kt` · `CastGeometryEditor.kt` (DPI label)
- Probe nav-mode: `scripts/vehicle/nav-mode-probe.sh`
- B6 screencaps (tạm, off-car): `/tmp/nm_op*.png`
- Handoff cũ (lịch sử): `oncar-handoff-2026-08-12.md`
