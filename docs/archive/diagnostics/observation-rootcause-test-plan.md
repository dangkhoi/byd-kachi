# Kịch bản lên xe — chẩn đoán observation failure

**Nguyên tắc: ĐO rồi mới sửa. Mỗi test ≤ 2 phút. Dừng ngay khi tìm ra root cause.**

---

## Chuẩn bị (1 phút)

```bash
export ADB=~/Library/Android/sdk/platform-tools/adb
$ADB connect <vehicle-ip>:5555
$ADB -s <vehicle-ip>:5555 shell echo "connected"
```

App đã cài sẵn (KHÔNG pm clear). Nếu chưa cài:
```bash
$ADB -s <vehicle-ip>:5555 install -r app/build/outputs/apk/release/app-release.apk
```

---

## Phase 1: Loại trừ nhanh (không cần sửa code)

### Test A — Ngắt laptop, cast thử (2 phút)

Giả thuyết: laptop chiếm ADB session → app không connect được localhost.

```bash
$ADB disconnect <vehicle-ip>:5555
```

Bấm cast trên xe (nút nổi). Chờ 15s. Reconnect:

```bash
$ADB connect <vehicle-ip>:5555
$ADB -s <vehicle-ip>:5555 shell run-as com.byd.clusternav cat files/cast-v2/session.env | grep -E "stable=|tx="
```

- **Nếu `tx=...RECOVERING`** → laptop KHÔNG phải nguyên nhân. Đi Test B.
- **Nếu KHÔNG có `tx=` hoặc `stable=...ACTIVE`** → 🎯 **ROOT CAUSE: concurrent ADB session.** Fix = app retry / serialize connections.

---

### Test B — Key file có tồn tại? (30 giây)

```bash
$ADB -s <vehicle-ip>:5555 shell run-as com.byd.clusternav ls -la files/adb.key files/adb.pub
```

- **"No such file"** → 🎯 **ROOT CAUSE: key bị xoá.** Fix = đừng pm clear, hoặc pre-seed key.
- **Có cả 2 file** → Đi Test C.

---

### Test C — TIME_WAIT connections (30 giây)

```bash
$ADB -s <vehicle-ip>:5555 shell "ss -tn state time-wait | grep 5555 | wc -l"
$ADB -s <vehicle-ip>:5555 shell "ss -tn state established | grep 5555"
```

- **TIME_WAIT > 20** → 🎯 **ROOT CAUSE: connection exhaustion.** App tạo quá nhiều connection không reuse.
- **≤ 5** → Đi Test D.

---

### Test D — App key có được xe trust? (1 phút)

```bash
# Lấy pub key app
$ADB -s <vehicle-ip>:5555 shell run-as com.byd.clusternav cat files/adb.pub > /tmp/app_adb.pub
echo "App key (50 chars):"
head -c 50 /tmp/app_adb.pub

# Kiểm tra xe trust key nào (cần root hoặc shell uid)
$ADB -s <vehicle-ip>:5555 shell cat /data/misc/adb/adb_keys 2>&1 | head -5
```

- **Permission denied** → không đọc được, thử gián tiếp (Test E).
- **Key app CÓ trong adb_keys** → Key trusted. Đi Phase 2.
- **Key app KHÔNG CÓ** → 🎯 **ROOT CAUSE: key chưa trust.** Fix = push key hoặc bấm Allow.

---

### Test E — Prompt "Allow USB debugging" có đang hiện? (30 giây)

Nhìn màn hình xe: có popup "Allow USB debugging" / "Cho phép gỡ lỗi USB" không?

- **CÓ** → 🎯 **ROOT CAUSE: chưa bấm Allow.** Bấm Allow (tick Always) → quay lại cast → check.
- **KHÔNG** → Đi Phase 2.

---

## Phase 2: Diagnostic code (cần 1 build mới)

Chỉ đến đây nếu Phase 1 KHÔNG tìm ra root cause (= key OK, trust OK, không concurrent issue).

### Test F — Ghi observation result ra file

Thêm vào `CastActivityRefresh.kt` dòng 97 (trước reconcileAbandoned):

```kotlin
// DIAGNOSTIC — xoá sau
try {
    val obs = facade.observeBoth()
    java.io.File(facade.filesDir(), "observation-diag.txt").writeText(
        "time=${System.currentTimeMillis()}\nfirst=${obs.first?.javaClass?.simpleName}\n" +
        "reason=${(obs.first as? com.byd.clusternav.modules.clustercast.v2.ObservationValue.Unknown)?.reason}\n" +
        "observed=${obs.second?.coarseState}\ntarget=${obs.second?.target}\n"
    )
} catch (_: Exception) {}
```

Build, install (KHÔNG clear), cast, đọc:

```bash
$ADB -s <vehicle-ip>:5555 shell run-as com.byd.clusternav cat files/observation-diag.txt
```

**Kết quả cho biết:**
- `first=Known` → observation THẬT SỰ work! Vấn đề ở verification logic.
- `first=Unknown, reason=...` → đọc reason:
  - `"AM_STACK_LIST timed out"` → command 1 timeout
  - `"AM_STACK_LIST failed: ..."` → command 1 lỗi (permission?)
  - `"APP_OPS_STATE timed out"` → command 6 timeout (100KB output)
  - `"target app-ops block unavailable"` → parser fail (appops format)
  - `"expected exactly one named cluster display"` → display parser fail

---

### Test G — Từng command đo riêng (nếu Test F trả Unknown)

```bash
# Đo từng command mà app chạy, từ shell uid (giống dadb):
echo "=== am stack list ===" && time $ADB -s <vehicle-ip>:5555 shell "am stack list" | wc -c
echo "=== dumpsys window displays ===" && time $ADB -s <vehicle-ip>:5555 shell "dumpsys window displays" | wc -c
echo "=== dumpsys display ===" && time $ADB -s <vehicle-ip>:5555 shell "dumpsys display" | wc -c
echo "=== am get-current-user ===" && time $ADB -s <vehicle-ip>:5555 shell "am get-current-user"
echo "=== animations ===" && time $ADB -s <vehicle-ip>:5555 shell "settings get global window_animation_scale; settings get global transition_animation_scale; settings get global animator_duration_scale"
echo "=== dumpsys appops ===" && time $ADB -s <vehicle-ip>:5555 shell "dumpsys appops" | wc -c
```

So sánh thời gian mỗi command. Nếu 1 command > 4s → đó là bottleneck.

---

## Decision tree tổng hợp

```
Test A: ngắt laptop → cast pass?
  └── CÓ → ROOT CAUSE: concurrent sessions. Stop.
  └── KHÔNG ↓

Test B: key file tồn tại?
  └── KHÔNG → ROOT CAUSE: key mất. Stop.
  └── CÓ ↓

Test C: TIME_WAIT > 20?
  └── CÓ → ROOT CAUSE: connection pool exhaustion. Stop.
  └── KHÔNG ↓

Test D/E: key trusted / prompt hiện?
  └── Prompt hiện → ROOT CAUSE: chưa Allow. Bấm → test lại. Stop.
  └── Key không trust → ROOT CAUSE: xe quên key. Push key → test lại. Stop.
  └── Key trusted, no prompt ↓

Test F: observation-diag.txt?
  └── Known → ROOT CAUSE: verification logic (đã fix bằng ledger). Test cast.
  └── Unknown(reason) → đọc reason → fix chính xác command/parser đó. Stop.
```

---

## Sau khi tìm root cause

1. Ghi vào `docs/diagnostics/observation-rootcause-result.md`: nguyên nhân + bằng chứng
2. Implement ĐÚNG 1 fix cho nguyên nhân đó
3. Test lại ĐÚNG kịch bản fail → phải pass
4. Mới test bài 10 lượt cast/trả

---

## Thời gian ước tính

- Phase 1 (Test A–E): **5 phút** — không cần build
- Phase 2 (Test F–G): **10 phút** — cần 1 build mới
- Tổng tối đa: **15 phút** chẩn đoán → biết root cause
