# RE — PM2.5 sensor read + AC air-purify (device 1008 / BYDAutoAcDevice)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-04 · **Mục đích**: Ghi lại RE cho tính năng "Tự lọc bụi mịn (PM2.5)" — API ĐỌC mức bụi (device 1008), API GHI "tự lọc không popup" trên AC device, lý do gọi bằng reflection, và các ẩn số CHỈ chốt được trên xe.

**(VI)** Nguồn: `javap` trên SDK BYDAuto (`android.hardware.bydauto.*`) + jadx trên stub OpenBYD. Đây là **[SUY]/RE tĩnh** (đọc chữ ký + stub), CHƯA có [ĐO] trên xe — cả read lẫn write mới verify off-car (device null → no-op). Ẩn số on-car ở §5.

---

## 1. ĐỌC — `android.hardware.bydauto.pm2p5.BYDAutoPM2p5Device` (device_type 1008)

| Method | Kiểu | Nghĩa |
|--------|------|-------|
| `getInstance(Context)` | static → device | Lấy instance (như mọi BYDAuto device). Cần bypass-permission context (đã có `BydHal.bypass`). |
| `getPM2p5Level()` | `int[]` | **`[0]` = MỨC bụi** (bảng dưới). Càng cao càng bẩn. |
| `getPM2p5Value()` | `int[]` | `[0]` = raw µg/m³ (0..3000). (Không dùng cho UI hiện tại — chỉ hiện MỨC.) |
| `getPM2p5OnlineState()` | `int` | cảm biến online? `PM2P5_STATE_ON=1` / `OFF=0`. |

**Bảng mức (`getPM2p5Level()[0]`):**

| Hằng | Giá trị | Nghĩa |
|------|---------|-------|
| `INVALID` | 0 | không đọc được / chưa có |
| `EXCELLENT` | 1 | rất sạch |
| `GOOD` | 2 | tốt |
| `LOW_GRADE` | 3 | khá |
| `MIDDLE` | 4 | trung bình |
| `HEAVY` | 5 | nặng ← **ngưỡng "bẩn" mặc định** (`Pm25Filter.DEFAULT_THRESHOLD`) |
| `SERIOUS` | 6 | nghiêm trọng |

Lớp thuần `com.byd.clusternav.comfort.Pm25Filter` (:core) giữ bảng này + `isDirty(level, threshold=HEAVY)` (true khi `level in threshold..SERIOUS`) + nhãn song ngữ `levelLabelVi/En` (INVALID/không rõ → `"—"`). Test off-car: `Pm25FilterTest`.

---

## 2. GHI — `android.hardware.bydauto.ac.BYDAutoAcDevice` (tự lọc, không popup)

> ⚠ **Các method này CÓ trên ROM xe nhưng KHÔNG có trong SDK jar** ⇒ **PHẢI gọi bằng reflection** (`getMethod(name, int).invoke(dev, arg)`), mỗi lời gọi bọc `runCatching` riêng. Gọi biên dịch trực tiếp sẽ không compile off-car (lớp SDK không có method) và crash trên ROM thiếu method.

| Method | Arg dùng | Nghĩa |
|--------|----------|-------|
| `enablePurificationFunctionPrompt(int)` | `0` | **Tắt POPUP** nhắc lọc (owner: "không hiện popup gì hết"). `1` = khôi phục. |
| `setAutoCleanAirState(int)` | `1` | Bật **lọc-liên-tục** — xe TỰ theo dõi PM2.5 + lọc. `0` = tắt. |
| `setQuickCleanAirState(int)` | `1` | **Lọc-ngay** (một nhịp) — dùng khi lúc bật mà mức đã ≥ HEAVY. |
| `getAutoCleanAirState()` / `getQuickCleanAirState()` / `getAcPromptBoxShownState()` | — | Readers (int). Chưa dùng; để lại cho self-test on-car. |

**❌ KHÔNG dùng `setAcCycleMode`** — arg đầu chưa rõ nghĩa (RE chưa chốt), rủi ro đặt sai chế độ gió.

---

## 3. Cơ chế owner chốt ("tự bật lọc, không hiện popup gì hết")

- **Bật** → `enablePurificationFunctionPrompt(0)` + `setAutoCleanAirState(1)`; nếu `getPM2p5Level()[0] >= HEAVY(5)` thì thêm `setQuickCleanAirState(1)`.
- **Tắt** → `setAutoCleanAirState(0)` + `enablePurificationFunctionPrompt(1)` (khôi phục).
- **KHÔNG cần service polling nền** — xe tự giám sát PM2.5 qua autoClean. App chỉ ĐỌC mức để HIỂN THỊ.

Áp ~5 s sau mở app + lúc boot (giống ghế: `SeatComfortApplier`) qua `Pm25FilterApplier` (:app), degrade-safe: `BydHal.device(...)` null (off-car) → log + return, KHÔNG ném.

---

## 4. Lý do reflection (không thêm compiled call)

- SDK jar trên classpath build **không có** `enablePurificationFunctionPrompt/setAutoCleanAirState/setQuickCleanAirState` → compiled call = lỗi biên dịch.
- Cùng lối đã dùng cho toàn bộ HAL của ClusterNav (`BydHal` reflect `getInstance/set/get`), và cho ghế (`SeatComfortApplier`).
- `BydHal` chỉ thêm hằng FQN `PM2P5` cho device đọc; device AC đã có hằng `AC`. KHÔNG thêm lời gọi biên dịch method ROM-only.

---

## 5. Ẩn số CHỈ chốt được trên xe (on-car unknowns)

| # | Ẩn số | Cách chốt |
|---|-------|-----------|
| OQ1 | `getPM2p5Level()[0]` có thật là index mức (không phải `getPM2p5Value`)? | `logcat -s Pm25Filter` khi bật → so mức in ra với chất lượng không khí thực tế. |
| OQ2 | `setAutoCleanAirState(1)` có bật lọc mà KHÔNG bung popup không? | Bật công tắc trên xe, xem có popup điều-hoà/lọc hiện không. |
| OQ3 | `enablePurificationFunctionPrompt(0)` có đủ dập MỌI popup không, hay còn popup khác? | Quan sát trên xe; nếu còn → RE thêm method prompt khác. |
| OQ4 | rc trả về của 3 method (0 = OK?) trên trim owner. | `logcat -s Pm25Filter` in `method(arg) rc=…`. |

Chưa có cái nào lên [ĐO] — off-car `device()` trả null nên chỉ chứng minh **degrade-safe** (no-op, không crash), CHƯA chứng minh xe lọc thật.

---

## 6. File liên quan

- `core/src/main/kotlin/com/byd/clusternav/comfort/Pm25Filter.kt` (+ `Pm25FilterTest.kt`) — model thuần + test.
- `app/src/main/java/com/byd/clusternav/comfort/Pm25FilterApplier.kt` — reflection GHI/ĐỌC, degrade-safe.
- `app/src/main/java/com/byd/clusternav/modules/hal/BydHal.kt` — hằng `AC` (đã có) + `PM2P5` (mới).
- `app/src/main/java/com/byd/clusternav/Prefs.kt` — `pm25FilterEnabled` (default false).
- `app/src/main/java/com/byd/clusternav/MainActivity.kt` — `setupPm25FilterControls` + `applyOnStart`.
- `app/src/main/java/com/byd/clusternav/BootSetupService.kt` — `applyOnStart` lúc boot.
- `app/src/main/res/layout[-w960dp]/activity_main.xml` — công tắc + nhãn mức (parity id).
- Spec: `docs/specs/pm25-auto-filter.html`.
