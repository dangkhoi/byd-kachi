# HANDOFF — **byd-cluster-2**: tách repo base + revive Waze/VietMap signal · 2026-08-17

> Handoff ĐỘC LẬP, đủ để phiên sau thực thi không cần hỏi lại. Repo mới = base cho feature/research, tách từ ClusterNav **1.30**.
> Spec đã approve: `docs/specs/waze-vietmap-signal-revival.html`. Quy tắc: plan→approve(✓)→code→test→senior-review→security-scan.

## TL;DR
`byd-cluster-2` = clone local từ ClusterNav 1.30 (main sạch + tag `v1.21-signal-features`). Việc cần làm: **revive TẤT CẢ tín hiệu Waze/VietMap** (nav-source + VietMap widget + speed-limit signal + Cascade C catalog T10) mà 1.22 đã gỡ, **adapt lên API 1.30**, build xanh, rồi push lên GitHub repo mới. Đây là **base để research/fix tiếp** — các phần này ở 1.21 **vốn chưa chạy**.

## 0. Trạng thái repo (đã làm)
- Clone: `git clone <ClusterNav> byd-cluster-2` → có **main 1.30** (`6b1e736`) + **tag `v1.21-signal-features`** (bản local đã filter-repo sạch; 28 file source Waze/VietMap sẵn).
- `origin` hiện trỏ **local path** ClusterNav (do clone). **CHƯA có remote GitHub.**
- Spec revive đã tạo + **approved** (owner "OK" 2026-08-17).

## 1. Quyết định đã chốt (owner)
1. Tên repo: **byd-cluster-2**.
2. Scope: **TẤT CẢ 3 nhóm** (Waze-Mod nav-source · VietMap widget · speed-limit signal) **+ Cascade C** (catalog T10 speed-sign: `CarExecSpeedSignCatalog` + `contracts/SpeedLimit*` + carexec speed scenarios/tests). Open Q1 (vn.vietmap.live + nav-source spinner) = **revive tất cả** (Waze Mod + VietMap + speed-source). Open Q2 = **bao gồm** Cascade C.
3. **Adapt luôn** sang API 1.30 khi khôi phục (KHÔNG giữ verbatim).

## 2. Kế hoạch (revive = ĐẢO NGƯỢC removal-manifest, adapt lên 1.30)
**Nguồn chính xác (reverse-plan):** `docs/archive/diagnostics/removal-manifest-waze-vietmap-1.22.md` — 29 file DELETE (§1) + 14 file EDIT (§2) với `file:line` + coupling map (§4) + risk (§5). Revive làm NGƯỢC.

Thứ tự (đảo removal: restore file TRƯỚC → rewire consumer → build/adapt), làm trên branch `revive/waze-vietmap-signal`:

**S1 — restore 29 file (verbatim từ v1.21):**
```
git checkout v1.21-signal-features -- \
  app/src/main/java/com/byd/clusternav/modules/wazehud/ \
  app/src/main/java/com/byd/clusternav/vietmapwidget/ \
  core/src/main/kotlin/com/byd/clusternav/vietmapwidget/ \
  app/src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt \
  core/src/main/kotlin/com/byd/clusternav/navigation/SpeedSignLifecycleCoordinator.kt \
  core/src/main/kotlin/com/byd/clusternav/navigation/SpeedSignPorts.kt \
  core/src/main/kotlin/com/byd/clusternav/carexec/CarExecSpeedSignCatalog.kt \
  vehicle-contracts/src/main/kotlin/com/byd/clusternav/contracts/SpeedLimitFrame.kt \
  vehicle-contracts/src/main/kotlin/com/byd/clusternav/contracts/SpeedLimitEnums.kt \
  app/src/main/java/com/byd/clusternav/modules/clustercast/VietmapBubbleExperiment.kt
# + test files tương ứng (manifest §1: WazeHudSourceTest, VietMapWidget*Test, SpeedSign*Test, SpeedLimitFrameTest)
```

**S2 — re-wire 14 file KEEP (đảo manifest §2, LÊN BẢN 1.30 hiện tại):**
- `NavNotificationListener.kt` (§2.1): re-add import (SpeedLimitSource, VietMapWidget*, WazeHud*), field (`speedSignOwner`, `speedLimitPusher`, `wazeHudSource`), `startWazeHudSource`/`stopWazeHudSource`, statement trong onListener(Dis)Connected/onDestroy/ensureBridgeStarted, MAPS_PACKAGES += `com.chisadin.wazemod`/`com.waze`/`vn.vietmap.live`. **GIỮ** đường GMaps 1.30 (TASK1 set NavState.maneuver, log-on-change `lastNavLogKey`, reset ở 4 ranh giới phiên).
- `MainActivity.kt` (§2.2): re-add nav-source + speed-source spinner + `speedSign` owner + VietMapWidgetDiag button. **GIỮ** spinner cluster ON/OFF (TASK4), disclaimer, toggle verbose.
- `Prefs.kt` (§2.3): PREFER_WAZE/PREFER_VIETMAP, K_SPEED_SOURCE + speedSource/speedLimitSource.
- `NavSourceMode.kt` (§2.4): PREFER_WAZE=3, PREFER_VIETMAP=4, SPEED_VIETMAP/SPEED_WAZE.
- `SourceArbiter.kt` (§2.5): WAZE_PKGS/VIETMAP_PKGS + branch PREFER_WAZE/VIETMAP.
- `AndroidManifest.xml` (§2.6): READ_LOGS, BIND_APPWIDGET, activity VietMapWidgetDiagActivity.
- 2 layout (§2.7): spinner_speed_source, btn_vietmap_widget_diag, option Waze cho spinner_nav_source. ⚠️ **layout đổi = đổi hash pinned** `ExpansionTransportFenceTest` (T11_HASHES activity_main.xml) → **re-pin hash mới** (như đã làm ở 1.28 TASK4).
- carexec (§2.8/2.9): `CarExecCatalog` addAll(CarExecSpeedSignCatalog.steps); `CarExecScenarios` 3 scenario SPEED_SIGN.
- test (§2.10/2.11/2.12): `CarExecCatalogTest` re-add `sign-*` ids; `HudOutputHiddenContractTest` re-add assertion; `NavigationOutputIsolationTest` re-add speed test method.

**S3 — adapt API 1.30 (build → sửa drift):** điểm đã lường:
- `NavState` có field mới `maneuver: Maneuver?` (TASK1) → mọi nơi WazeHudSource/VietMap dựng NavState phải bổ sung (hoặc null → fromAmapIcon).
- Kiểm chữ ký hiện tại: `ClusterBroadcaster.emitLane(ctx, state, byd=)`, `SimpleCastRuntime.executeShell`, `Maneuver`, `ClusterNavLaneWidget`. WazeHudSource gọi chúng — adapt nếu drift.
- `NavNotificationListener` 1.30 khác 1.21 nhiều (log-on-change, NavLog, maneuver wiring) → chèn Waze/VietMap KHÔNG phá đường mới.

**S4 — verify:** `./gradlew :core:test :app:testDebugUnitTest :vehicle-contracts:test :car-integration:test :offcar-planner:test` xanh; `assembleRelease` + `assembleVehicleTest` ký; `aapt2` release **không** test surface (giữ bất biến 1.30). Fence T11 re-pin nếu layout đổi.

**S5 — review + scan + push:** senior review (boundary GMaps↔Waze/VietMap; adapt đúng; không hồi quy) → security scan diff → tạo GitHub repo + push.

## 3. KHÔNG được đụng (boundary — manifest §3)
- Cast "VietMap-as-target" (`CastShell`, `ClusterCast`, `CastSwapTest`, `WmParseTest`) = cast TARGET pkg `vn.vietmap.live`, KHÁC speed-signal → giữ nguyên.
- Đường GMaps→cụm 1.30 (`NavRepository`, `AmapFrameBuilder`, `ClusterBroadcaster`, `Maneuver` TASK1, keep-alive, `SpeedProvider`/`SpeedReading` = speedometer, KHÁC speed-limit) → chỉ THÊM nhánh Waze/VietMap cạnh nó, KHÔNG revert.

## 4. Còn phải làm (Q3 — tạo remote GitHub)
`gh` CHƯA cài. Chọn:
- (a) owner tạo repo rỗng `dangkhoi/byd-cluster-2` → `git remote set-url origin git@github.com:dangkhoi/byd-cluster-2.git && git push -u origin main && git push origin v1.21-signal-features` + push branch revive.
- (b) `brew install gh` + owner `gh auth login` → `gh repo create dangkhoi/byd-cluster-2 --private --source=. --push`.
Toàn bộ code+test làm LOCAL trước; push sau cùng.

## 5. Lưu ý trung thực (đừng kỳ vọng sai)
Sau revive các feature **VẪN CHƯA CHẠY** (bản chất 1.21): speed port = **Noop** (do-nothing); `WazeHudSource` **poll logcat ~4000×/giờ** (hao pin). Revive chỉ = **lấy lại + adapt cho compile/test xanh trên 1.30** làm nền research. Việc "làm nó chạy" (thay Noop bằng port HAL thật, bỏ poll hao pin, đo tín hiệu thật) = **feature/research riêng SAU**, ngoài scope revive.

## Nguồn
- Spec: `docs/specs/waze-vietmap-signal-revival.html` (approved).
- Reverse-plan: `docs/archive/diagnostics/removal-manifest-waze-vietmap-1.22.md`.
- Base: ClusterNav 1.30 — `docs/CLOSEOUT-2026-08-16.md`, handoff 1.30 closeout.
- Tag nguồn code: `v1.21-signal-features` (local sạch; remote byd-cluster cũ = commit `6785a6a`).
