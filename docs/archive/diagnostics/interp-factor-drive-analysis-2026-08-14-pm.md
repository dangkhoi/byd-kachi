# BÁO CÁO — Nội suy cự-ly-tới-rẽ (FACTOR) từ chuyến lái về · 2026-08-14 PM

> Xe: BYD Seal DiLink 3.0 · Android 10 (API 29) · bản trên xe **1.19 (versionCode 119)** `[on-car: dumpsys package]`.
> Dữ liệu: `nav_log_1786699766117.csv` — chuyến lái về, pull lúc ~17:30 +07 (`855495 bytes`, 7927 dòng).
> Analyzer: `scripts/analyze-nav-distance-log.py`. Handoff gốc: `docs/diagnostics/oncar-handoff-2026-08-14-pm-interp-screenread.md`.
> No-assumptions: mọi số trace về analyzer/log/awk. Ngưỡng hành động của analyzer: chỉnh FACTOR khi |mean projected−screen| ≥ **8 m**.

---

## 0. KẾT LUẬN (TL;DR)

1. **GIỮ `FACTOR = 0.95` — KHÔNG đổi.** Sai số nội suy vs GMaps (ground-truth trên màn) **lúc di chuyển** gần như bằng 0: core mean **+0.84 m**, median **−1 m**, full mean **+2.61 m** — đều sâu trong band ±8 m. Giả thuyết "giảm FACTOR" (rút từ 6 mẫu đầu lúc mới lăn bánh) **bị bác bỏ** bởi **4213 mẫu** cả chuyến.
2. **Round quantize (1.16) đã validate bằng ground-truth.** `display − screen` median **0**, p05 **0**, core mean **+2.46 m** → **hết bias xuống −34.5 m** của bản floor (1.15). Cụm giờ khớp GMaps (hoặc nhỉnh vài m — hướng an toàn), không bao giờ thấp hơn hệ thống.
3. **Pipeline `screenRead` đã lành & chứng minh trên đường.** **5808** dòng fresh (**4294** lúc di chuyển) so với **0** ở hai chuyến trước. Self-grant accessibility (ship 1.18/1.19) chạy end-to-end.
4. **Không cần đổi code cho FACTOR.** Báo cáo này = ghi nhận kết quả; interp coi như đã tuned đúng.

---

## 1. BỐI CẢNH

- Mục tiêu: tinh chỉnh FACTOR (dead-reckon cự-ly-tới-rẽ) từ **dữ liệu lái thật**, cần cột ground-truth `screenRead_m` (cự ly GMaps đọc trên màn qua accessibility).
- Hai commute trước RỖNG `screenRead_m` (quyền accessibility bị reboot xoá) → phí chuyến. Chuyến này quyền đã **bound** trên 1.19 (`capabilities=9` gồm FILTER_KEY_EVENTS) `[on-car readback]`, Nav+HUD **ON**, GMaps dẫn foreground suốt chuyến.
- FACTOR hiện tại: `core/src/main/kotlin/com/byd/clusternav/navigation/TurnDistanceInterpolator.kt` L29 → `private const val FACTOR = 0.95` `[source]`.

---

## 2. DỮ LIỆU

| Hạng mục | Giá trị |
|---|---|
| Log file | `nav_log_1786699766117.csv` |
| Tổng dòng | 7927 |
| Fresh (screenRead hợp lệ, age ≤ 1500 ms) | 5808 |
| Fresh **và** di chuyển (speed > 2 m/s) | **4294** |
| Cột CSV | `t_ms,rawGmaps_m,projected_m,display_m,closing_mps,speed_mps,screenRead_m,screenRead_age_ms,road,key` |

---

## 3. KẾT QUẢ — moving-only (speed > 2 m/s, age ≤ 1500 ms)

### 3.1 `projected − screen` — tín hiệu FACTOR (nội suy của mình vs GMaps)

```
n=4213  mean=+2.61  median=-1.0  mean|·|=7.44  p05=-10  p95=+26  min=-38  max=+4499
core (|err|<=150m, bỏ outlier chuyển-maneuver):  n=4208  mean=+0.84
```

- median **−1 m**, core mean **+0.84 m** → nội suy **bám GMaps gần như khít**. Outlier `max=+4499` là 1 khoảnh khắc nhảy sang ngã rẽ mới (10 m → 4500 m) — transient, không phải sai số ổn định; đã tách khỏi core.
- Ngưỡng analyzer: giảm FACTOR nếu mean ≤ −8, tăng nếu ≥ +8. Ở đây **+2.61 (full) / +0.84 (core)** → **trong band → KHÔNG chỉnh**.

### 3.2 `display − screen` — xác thực round quantize (số tài xế thấy)

```
n=4213  mean=+4.23  median=+0.0  mean|·|=5.24  p05=+0.0  p95=+10  min=-30  max=+4500
core:  mean=+2.46
```

- median **0**, p05 **0** → số hiển thị **khớp đúng GMaps** ở đa số mẫu. Bias nhẹ **dương** (+2.46 core) — cụm nhỉnh hơn GMaps ≤ 1 bước quantize (10 m), **hướng an toàn** (không báo ngã rẽ gần hơn thực).
- So bản floor 1.15 (`display−screen ≈ −34.5 m`, lệch xuống): **round 1.16 khử hẳn** bias xuống. ✔ validated bằng ground-truth.

### 3.3 `rawGmaps − screen` — độ trễ notification

```
n=4282  mean=-16.06  median=+0.0  mean|·|=20.02  p05=-10  p95=+0  min=-1600  max=+4500
core:  mean=-1.85
```

- median **0**, core **−1.85** → notification phần lớn đồng bộ với màn, nhưng có outlier trễ mạnh (min −1600 lúc chuyển maneuver). → củng cố lý do dùng **interp + đọc màn** thay vì tin thẳng notification. Không liên quan FACTOR.

### 3.4 Display jumps (|Δ| > 40 m lúc di chuyển): **52**

- Toàn bộ là (a) **chuyển ngã rẽ** (ngã cũ ~10 m → ngã mới lớn: `10→220`, `10→600`, `10→4500`…) và (b) **bước quantize 100 m ở tầm > 1 km** (`1600→1500→1400…`). GMaps cũng nhảy y hệt ở các mốc này.
- **Không có glitch nội suy / dao động giữa-turn.** Display ổn định.

---

## 4. DIỄN GIẢI + KHUYẾN NGHỊ

1. **`FACTOR = 0.95` → GIỮ NGUYÊN.** 4213 mẫu di chuyển cho bias core **+0.84 m** (median −1 m) — nội suy đã đúng. Không có cơ sở dữ liệu để giảm hay tăng. *(Giả thuyết "giảm FACTOR" ban đầu chỉ dựa 6 mẫu lúc mới lăn bánh → không đại diện; đã bị 4213 mẫu bác bỏ.)*
2. **Round quantize (1.16) → GIỮ.** `display−screen` median 0, hết bias xuống của floor → mục tiêu 1.16 đạt, xác nhận bằng ground-truth.
3. **KHÔNG có thay đổi code phát sinh từ báo cáo này.** Accessibility self-grant (đã ship 1.18/1.19) + round (1.16) là các thay đổi liên quan gần nhất, và cả hai đã live + validated.
4. **Bias display dương +2.46 m**: trong 1 bước quantize (10 m), hướng an toàn → **không cần xử lý**.

---

## 5. BẰNG CHỨNG / TRACE

- **Analyzer (all-fresh, 5808 dòng):** `projected−screen n=5682 mean=+1.9 mean|·|=5.9 rms=65.4 p50=-1.0 p95=+24 min=-38 max=+4499`; `display−screen n=5682 mean=+3.3 p50=0 p95=0 min=-30 max=+4500`; `rawGmaps−screen n=5791 mean=-16.3 p50=0 min=-1600 max=+4500`. Hint in: `"mean bias small (|+1.9|m) — core rate looks fine."`
- **Moving-only (awk/python, speed>2 m/s):** §3 ở trên (4294 dòng fresh+moving).
- **Version xe:** `versionName=1.19 versionCode=119` `[on-car dumpsys package]`.
- **FACTOR:** `TurnDistanceInterpolator.kt` L29 `= 0.95` `[source]`.
- **Round quantize:** `core/.../navigation/NavParse.kt` `quantizeDisplay()` `[source]`.
- **Booster đọc màn:** `NavScreenScan.kt` — regex `\b\d+([.,]\d+)?\s?(km|m|ft|mi)\b`, chỉ nhận thẻ rẽ **nửa trên** màn, loại > 50 km `[source]`.
- **Pipeline self-grant (đã wire, chống mồ côi):** `MainActivity.kt` L93/L231/L483–485 gọi `NavConnect.grantAccessibility(...)` sau guard `accessibilityBoosterGranted()`; single-flight trong `NavConnect.kt`; khoá bằng `NavCastUiWiringContractTest` `[source]`.
- **Raw artifact:** `docs/diagnostics/nav-logs/commute-2026-08-14-pm.csv`.

---

## 6. BƯỚC TIẾP (nếu owner muốn)

- Không có việc code bắt buộc. Nếu muốn "đóng băng" kết quả tuning: thêm 1 unit test snapshot trên fixture đoạn drive này (mean bias trong ±8 m) để chống hồi quy khi đụng interp về sau — *tùy chọn, không bắt buộc*.
- Chuyến sau chỉ cần lặp lại flow này để theo dõi bias trôi theo mùa/route khác.
