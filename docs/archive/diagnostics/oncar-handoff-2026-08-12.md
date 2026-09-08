# ON-CAR HANDOFF — verify 1.07/1.08 + probe còn lại (2026-08-12)

> Xe: BYD Seal DiLink 3.0, Android 10 (API 29), **không root**. Chủ: Đăng Khôi (dangkhoi).
> **Đây là 1 CHỖ DUY NHẤT** cho lần lên xe tới — mở file này khi lên xe.
> Tất cả bên dưới **parked-only** (số P, phanh tay). Dọn = **power-cycle nút nguồn vật lý** (không tính `adb reboot`).

---

## 0. TRẠNG THÁI (off-car, tính tới 2026-08-12)

| Hạng mục | Trạng thái |
|---|---|
| **1.07** (nav 2-mode op39 · split 9-nút live · fix nút nổi · fix bug(b) · OTA auto-reopen v1) | **LIVE trên `main`/OTA** — off-car green, senior review APPROVED, security scan CLEAN. **CHƯA verify on-car.** |
| **1.08 fixes** (install() báo đúng thành/bại · relaunch pre-install alarm) | **Đã code + build xanh, CHƯA ship** (đang ở working tree local; chờ quyết định ship). |
| Probe A (bơm số speed-limit) | **BỊ CHẶN — đã chốt**, khỏi mò lại. |

**Cast/nav dùng luồng nào:** cast = `SimpleCastCoordinator` (simplified). op39 = nav-track (`ClusterNavLaneWidget`) phát khi **dẫn đường + Cast TẮT**.

---

## 1. CHUẨN BỊ (nối máy)
```bash
export VEH=<ip:port>            # vd <vehicle-ip>:5555 — HỎI lại IP nếu đổi, ĐỪNG đoán
adb connect $VEH
adb devices                    # thấy đúng device (DiLink3.0), KHÔNG nhầm emulator
cd <repo>/ClusterNav/scripts/vehicle
```
- **Cụm = `fission_screencap -d 0`** trên trim này (help ghi 0:ivi/1:cluster nhưng **đảo** — `-d 0` = CỤM, `-d 1` = màn giữa). Verify: chụp `-d 0` thấy đồng hồ ⇒ đúng.
- **Opcode CẤM**: `1` (ngắt video cụm), `18` (tắt cast), `41` (stress), `91/92` (crash). Script đã guard.

---

## 2. NHÓM A — VERIFY tính năng đã ship (1.07; +1.08 nếu đã ship)

> Cần cài đúng bản **release** trên xe (OTA từ `main`), KHÔNG phải debug.

### A1 — op 39 "Giữa + ETA" (nav-only)
1. App: **Cluster Cast = TẮT**. Card Navigation+HUD: chọn nút **[Giữa + ETA]**.
2. Mở GMaps/VietMap dẫn đường.
3. Nhìn cụm: **tự** hiện mũi tên + cự ly + tên đường + **ETA** (không thao tác tay).
- ☐ PASS  ☐ FAIL (mô tả: ______)

### A2 — Nút "Nhỏ / ở trên"
1. Chọn nút **[Nhỏ / ở trên]** khi đang dẫn đường.
2. Kỳ vọng: cụm về kiểu nav nhỏ/ở trên (default OEM), KHÔNG phải giữa+ETA.
- ⚠️ Biết trước: nếu op39 đã bật (sticky) có thể **chưa revert ngay** → cần opcode "Màn hình nhỏ" (mục B6) hoặc power-cycle.
- ☐ revert được  ☐ phải power-cycle  ☐ không đổi

### A3 — Split 9 nút LIVE
1. Chiếu chia đôi (2 app trái/phải qua nút nổi).
2. Bấm lần lượt vài nút tỷ lệ (`1:9` … `9:1`).
3. Kỳ vọng: 2 app **đổi tỷ lệ NGAY tại chỗ**, KHÔNG trả về rồi cast lại.
- ⚠️ Cần **freeform còn sống**. Nếu không đổi (am task resize bị từ chối) → **power-cycle 1 lần** (flag freeform đọc lúc boot) rồi thử lại.
- ☐ live OK  ☐ phải power-cycle rồi mới OK  ☐ FAIL

### A4 — Nút nổi hiện ngay
1. App: bật **Cluster Cast** → cấp **quyền Overlay** → quay lại app.
2. Kỳ vọng: **nút nổi lên NGAY**, không phải tắt/mở lại app.
- ☐ PASS  ☐ FAIL

### A5 — OTA auto-reopen (chỉ test được khi có bản mới hơn trên main)
1. Bấm "Kiểm tra cập nhật" → Tải & cài.
2. Kỳ vọng (1.08+): cài xong app **tự mở lại** (~5s). Nếu cài fail → hiện **"cài thất bại"** (không còn báo "đã cài" giả).
- ☐ tự mở lại  ☐ không (phải bấm icon)  ☐ báo lỗi đúng khi fail

---

## 3. NHÓM B — PROBE (chưa có lời giải, mò trên xe)

### B6 — Opcode cho các mode nav OEM (quan trọng nhất) → hoàn thiện nút "Nhỏ/ở trên"
Chạy:
```bash
VEH=$VEH bash nav-mode-probe.sh        # DISP=0 mặc định (cụm). DiLink5: SVC=auto_container
```
- Tiền đề: đang **dẫn đường** (nav feed sống), xe **đỗ**.
- Script thử op **39** (anchor) rồi sweep an toàn **8/9/34/35/6/7**, chụp `./navmode_*.png` từng bước.
- **Điền bảng** (mở menu OEM AMAP: Đơn giản / Màn hình nhỏ / Toàn màn hình / OFF, đối chiếu từng op):

| Screencap | Op | Layout nav quan sát | = mode OEM nào? |
|---|---|---|---|
| navmode_00_baseline | — | | |
| navmode_op39 | 39 | | (đoán: Đơn giản / Giữa+ETA) |
| navmode_op08 | 8 | | |
| navmode_op09 | 9 | | |
| navmode_op34 | 34 | | |
| navmode_op35 | 35 | | |

- Câu cần trả lời:
  1. op39 = mode nào?  2. Opcode nào ra **"Màn hình nhỏ"** (nhỏ/ở trên)?  3. Cách **revert op39** về small (opcode? hay power-cycle?).
- **Báo lại 3 câu này** → agent wire nút "Nhỏ/ở trên" set đúng opcode + op-39 teardown.

### B7 — Overlay biển tốc độ (vòng tròn xanh) — probe COEXISTENCE
Câu hỏi duy nhất chặn feasibility: **vẽ 1 surface nhỏ trên cụm (display 1) có sống chung với đồng hồ/nav không, hay bắt buộc full-cast (che gauges)?**
- Thử: `am start --display 0 -n com.byd.clusternav/.ClusterNavActivity` (card cụm sẵn có, `demo=true` để test) → xem nó chiếm full hay chừa chỗ.
- Thử cast-half (op 17) + nhìn còn chỗ cho gauges/nav không.
- Kết luận: (a) coexist được → wire OverlaySpeedSignPort + vòng tròn xanh trên surface app; (b) chỉ full-cast → overlay tốc độ chỉ hiện khi app chiếm cụm (không đè lên nav OEM).
- Đã loại: broadcast AMAP **không có** field speed-limit; op 45 (ADAS) chỉ ra **số đỏ OEM**, không custom/xanh.
- ☐ coexist được  ☐ chỉ full-cast  → ghi chú: ______

### B8 — Trigger bug (b) (nếu còn tái hiện)
- Repro: cast CarPlay → trả về → **cast CP lại**. Nếu ClusterNav lại kẹt trên cụm:
```bash
adb -s $VEH shell "am stack list" > ./bugb_stacklist.txt   # BẮT NGAY lúc kẹt
```
- Gửi `bugb_stacklist.txt` → xác nhận cơ chế MainActivity leo lên display 1 (off-car chưa suy ra được). Fix hiện tại (dọn mọi ClusterNav trừ ClusterBlackActivity) vẫn an toàn kể cả chưa rõ trigger.

---

## 4. NHÓM C — ĐÃ CHỐT (khỏi mò)
- **Probe A (bơm số speed-limit tùy chọn)** = **BỊ CHẶN**: Door A `RemoteException` (ICollect2FileStoreService, cả 2 authority), Door B `frames=0` (passive + active 0x234). Hết đường ADB non-root. Muốn số tùy chọn → đi hướng overlay (B7).

---

## 5. DỌN DẸP (bắt buộc trước khi rời xe)
- **Power-cycle head unit bằng nút nguồn vật lý** (dọn opcode/frame + trạng thái compositor).
- `navopen.jar` để lại `/data/local/tmp/` — vô hại.

---

## 6. SAU KHI CÓ KẾT QUẢ (agent làm off-car)
- B6 → wire nút "Nhỏ/ở trên" set opcode "Màn hình nhỏ" + op-39 teardown (revert).
- B7 → nếu coexist: implement `OverlaySpeedSignPort` (thay NoopSpeedSignPort ở `NavigationSpeedSignOwner.clusterPort`) + vòng tròn xanh trên surface cụm.
- A5/OTA + A1–A4 confirm → hạ nhãn "on-car UNVERIFIED" của 1.07/1.08.

## 7. THAM CHIẾU
- Probe nav-mode: `scripts/vehicle/nav-mode-probe.sh`. Nav-overlay nền: `scripts/vehicle/cast-nav-overlay-probe.sh`.
- op39 wiring: `app/.../modules/clustercast/ClusterNavLaneWidget.kt` + hook `NavNotificationListener.kt`.
- Split live: `SimpleCastCoordinator.applySplitRatioLive` + UI `CastSplitRatioButtons.kt`.
- Bug(b): `CastStackParser.tasksToClean` (giữ ClusterBlackActivity, evict còn lại).
- OTA: `UpdateChecker.install` + `UpdateRelaunch.kt` (1.08). op39 RE: `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` §168/§249.
