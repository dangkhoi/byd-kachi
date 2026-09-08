# Handoff phiên 01/8 sáng → phiên tiếp — Cluster Cast verification & scaling

## Trạng thái khi bàn giao

- APK trên xe: build cuối cùng của phiên (nhiều fix thử, chưa ổn định)
- Xe đã dọn sạch (GMaps trả về display 0, projection đóng)
- 10+ test fail do các thay đổi thử nghiệm chưa revert sạch
- HEAD chưa commit

## Gốc rễ ĐÃ XÁC ĐỊNH

**Observation transport (`ObservedStateReader`) trả `Unknown` ngay trong `verify()`** — `completeVerificationLocked` KHÔNG BAO GIỜ được gọi trên xe thật.

Nguyên nhân rất có khả năng: `pm clear` xoá ADB key lưu trong app data → dadb connect localhost:5555 cần auth lại → hiện prompt "Allow USB debugging" → timeout → observation Unknown.

Bằng chứng gián tiếp: user báo "hỏi Allow ADB hoài" suốt phiên.

## Chỉ đạo chủ dự án (3 điểm, 01/8)

### 1. Chiến lược observation: "mở app = dọn sạch, sẵn sàng cast"

> "Nếu chỉ đọc trạng thái từ app thì rủi ro khi tắt xe/tắt app bất ngờ không biết cluster đang có gì. Hoặc bế tắc thì cứ khi mở app phải dọn dẹp sạch sẽ về màn chính, không để gì ở cluster, chỉ trạng thái sẵn sàng cast."

**Hệ quả thiết kế:**
- Mở app → **LUÔN chạy clearCluster** (trả hết app về display 0 + đóng projection) → bắt đầu từ trạng thái sạch
- Không cần observation để "đọc lại" trạng thái cụm — observation chỉ cần sau khi CAST để xác nhận
- Nếu observation fail → **vẫn ghi ACTIVE_VERIFIED** dựa trên ledger (9 bước OBSERVED = app đã lên)
- Nút nổi hiện trạng thái dựa trên **journal** (stableSession), không dựa trên observation realtime

### 2. Scaling: dải vẽ thật < 1920×720

> "Tỷ lệ cluster bé hơn, thực tế chỉ chiếu được 1 làn giữa (mất trên dưới do chừa thông tin xe), cần đo chính xác (ví dụ 1920×680). Mọi app chưa có setting phải default scale về kích thước thật. 50-50 = 960×680, tương tự 30-70, 70-30."

**Cần đo trên xe:**
- `dumpsys display` đã cho: display 1920×720, overscan (0,90,0,90) → dải vẽ = 1920×(720-180) = **1920×540**? Hoặc overscan chỉ áp 1 chiều?
- Dump thật bounds task: `[0,90][1920,810]` → cao = 810-90 = 720. Vậy dải vẽ = 1920×720 nhưng DỊCH xuống 90px.
- **Cần xác nhận bằng mắt:** phần nào của 1920×720 thật sự NHÌN THẤY trên cụm vật lý. Có thể bị che bởi gauge/tachometer.

**Thiết kế:**
- Mỗi app có `preferredBounds: CastRect?` trong config (null = full dải vẽ)
- Default = full dải vẽ ĐO ĐƯỢC (sẽ hardcode sau khi đo)
- 50-50 / 30-70 / 70-30 tính từ dải vẽ, không từ display

### 3. Cast nửa trái GMaps: báo lỗi "cold gì đó"

Khả năng: khi cast nửa (slot), cần đã có stableSession ACTIVE hoặc IDLE_VERIFIED **với seal**. Nếu session đang RECOVERING hoặc mới pm clear → cổng `SLOT_NEVER_SURVIVES_BOOTSTRAP` chặn (spec §R7: slot không sống qua bootstrap replan). 

**Fix:** Cast full trước (tạo session) → rồi mới cast slot. Hoặc: cho slot tự trigger bootstrap nếu cần.

## Kế hoạch phiên tiếp (OFF-CAR trước, ON-CAR sau)

### Off-car (ưu tiên theo thứ tự):

1. **Revert các thay đổi thử nghiệm** của phiên này, giữ lại:
   - Bubble size 28dp ✓
   - CarPlay transport commands (CommandKind + CastPlacementCommands) ✓
   - CarPlay planner integration ✓
   - Lifecycle reconcile stale recovery ✓
   - Bubble state fix (fullPainted) ✓
   
2. **Fix verification theo hướng mới:**
   - CAST/SWITCH: khi ledger cho thấy MỌI bước OBSERVED → ghi ACTIVE_VERIFIED ngay
   - KHÔNG cần observation confirm
   - Observation chỉ dùng cho BOOTSTRAP (cần biết cụm đã mở) và STOP (cần biết đã trả về)

3. **Fix "mở app = dọn sạch":**
   - `CastAndroidLifecycle.rehydrate` / Activity onCreate → gọi clearCluster
   - Sau clear → session pristine → sẵn sàng cast

4. **Fix ADB key persistence:**
   - Lưu key ở chỗ không bị `pm clear` xoá
   - Hoặc: bỏ cần observation realtime (theo #2)

5. **Đo dải vẽ thật** (cần xe):
   - Ghi screencap cụm + ảnh chụp cụm vật lý → so offset

### On-car (chỉ khi off-car xong, test suite green):

1. Cài APK mới
2. Test 10 lượt cast/trả
3. Test CarPlay
4. Test 2 app

## Files đã sửa trong phiên (cần review/revert cẩn thận)

- `core/.../CastCoordinator.kt` — nhiều thay đổi verification (relaxed, grace, println debug)
- `core/.../CastBubbleProjection.kt` — fullPainted fix
- `core/.../CastDeviceParsers.kt` — appops "No operations" fix
- `core/.../CastModels.kt` — 4 CommandKind mới
- `core/.../CastPlanner.kt` — move-task/density steps cho protected, stop ladder
- `car-integration/.../CastPlacementCommands.kt` — transport cho 4 commands mới
- `app/.../FloatingBubbleService.kt` — bubble size 28dp
- `app/.../CastAndroidLifecycle.kt` — rehydrate/revalidate fix
- `app/.../CastFacade.kt` — reconcileStaleRecovery
- `app/.../CastActivityRefresh.kt` — wire reconcileStaleRecovery
- Tests: ExpectedLadder, CastClusterSlotTest, CastNormalSliceTest, CastFieldParityTest, CastAccessibilityTest, CastDiagnosticsContractTest, golden file

## CarPlay — ĐÃ CHỨNG MINH, đường rõ ràng

| Fact | Evidence |
|------|----------|
| `am stack move-task` đặt CarPlay lên cụm | T1 pass, 0 crash |
| `am task resize` bị từ chối (UNRESIZEABLE) | T2 fail |
| `wm density` scale app vừa cụm | T2c pass |
| `am stack move-task` trả về display 0 | T3 pass |

Chi tiết: `docs/diagnostics/carplay-move-task-success-2026-08-01.md`
