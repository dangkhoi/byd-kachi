# Kachi Launcher — Clean Architecture Refactor — Autonomous Execution Prompt

> Auto-generated from the senior-architecture review (2026-09-09). Variant A (Kiro CLI, `subagent` tool).
> **Mode: AUTONOMOUS BURN-ALL** — sau khi bắt đầu, chạy HẾT các stage, KHÔNG dừng hỏi. Chỉ dừng khi security scan `[BLOCK]` (secrets), hoặc gặp hard-blocker lặp lại đã thử ≥3 cách khác nhau.
> Stages: 8 | Deliverables: B0–B6 + consolidate.

## TASK
Refactor `byd-launcher` (Kachi car launcher) sang kiến trúc sạch theo review đã chốt: **Domain(:core)/Data(:app)/UI(:app) + UDF + ViewModel/StateFlow + coroutines + manual DI**, với **1 ShellTransport (chủ dadb duy nhất)** + **policy thuần :core** (DisplayOwnershipRegistry/AppLocationRegistry/FreeformSeedPolicy) + **input daemon riêng** + **auto-start service**. Giữ máy LUÔN build/chạy sau mỗi stage. Refactor DẦN, riskiest-first.

## WORKING DIR
`$HOME/Documents/workspaces/experiments/byd/byd-launcher`

## CONTEXT (đọc để code — không cần history dài)
- Kotlin, KHÔNG Compose (View thuần). Multi-module: `:core` (thuần JVM, cấm `android.*` — `LayeringRulesTest` enforce) · `:app` (framework) · `:car-integration` · `:offcar-planner` · vehicle-contracts.
- Fork của "ClusterNav 2.0". Legacy: `MainActivity` (1386 LOC, layout **byte-SEALED** bởi test) = màn Settings cũ → **CÁCH LY, KHÔNG refactor, chỉ gọi qua intent**. `modules/clustercast/*` chiếu app lên **display 1** (cụm) qua dadb `localhost:5555` (`am --windowingMode 5` + `am task resize`) — có **thảm hoạ brick**: ghi density/windowing bền lên VD cụm → kẹt cả hai, phải flash firmware.
- Launcher mới: `KachiHomeActivity` (god-activity), `WorkspaceView` (ô — HIỆN đang GIỮ state), `VdAppHost` (chiếu app vào ô qua VirtualDisplay + dadb `am start --display` + chạm `input -d`), `ControlDockView`, `AppDrawer`, `WidgetViews`; `:core launcher/` có `WorkspaceState/Layout`, `LauncherPorts` (AppLauncher/CarDataPort/CarControlPort + `NoCar`), `WorkspacePrefs`, `FreeformLaunch`, `ShellAppLauncher`.
- Sideload, KHÔNG platform-sign. Android 10. appId `com.byd.launcher`, namespace `com.byd.clusternav`, HOME `com.byd.clusternav.launcher.KachiHomeActivity`.

## TARGET ARCHITECTURE (chốt — mọi stage bám theo)
- **Domain `:core`**: models (đã có) + ports (`AppHost`, `ShellPort`, `CarDataPort`/`CarControlPort`) + **policy THUẦN**: `system/WindowMutation` (sealed command), `DisplayOwnershipRegistry`, `AppLocationRegistry`, `FreeformSeedPolicy`, command-builders (kiểu `FreeformLaunch`). Test JVM.
- **Data `:app`**: `ShellTransport` (**CHỦ dadb DUY NHẤT** — 1 consumer trên `Dispatchers.IO.limitedParallelism(1)`, KHÔNG `actor{}` obsolete) · `InputDaemonClient` (socket riêng, `injectInputEvent`) · `BydHalCarData`/`BydHalCarControl` (thay Demo/NoCar) · `WorkspaceRepository` · **1 `AppContainer`** (object graph duy nhất, gộp `SimpleCastRuntime` vào).
- **UI `:app`**: `HomeViewModel` giữ `StateFlow<HomeUiState>` (**source-of-truth, KHÔNG để `WorkspaceView` giữ state nữa**); `KachiHomeActivity` mỏng, collect `repeatOnLifecycle(STARTED)`, render; tách view-component.
- **DI**: manual (`AppContainer` + `viewModelFactory` + `APPLICATION_KEY`). KHÔNG Hilt/Koin.
- **Concurrency (rủi ro nhất)**: hàng đợi serialize **CHỈ cho lệnh cửa sổ** (priority mailbox: STOP/RESCUE trước NORMAL; preempt = đóng stream in-flight; `withTimeout`/idempotent; latest-wins cho intent gộp được — GIỮ semantics `BoundedCastExecutor`, KHÔNG hạ về FIFO). **Chạm đi đường RIÊNG** qua input daemon (socket), coordinator chỉ quản LIFECYCLE daemon.
- **Coexistence cluster-cast = 4 LUẬT**: (1) 1 transport cho MỌI lệnh cửa sổ (cả cast); (2) cổng chủ-quyền (launcher = display 0 + VD của nó; cast = display 1; **launcher CẤM chạm display ≥1**); (3) **1 nơi ghi state bền** (`enable_freeform_support`/`force_resizable_activities` ở `Settings.Global` boot-read + `wm size/density` ghi `display_settings.xml` — qua `FreeformSeedPolicy` + marker `commit()`-trước-mutate, reset là clearer DUY NHẤT); (4) trọng tài task (`AppLocationRegistry` — 1 pkg = 1 nơi).
- **Auto-start**: foreground `BootService` (KHÔNG ở HOME) → mount ô đã lưu (idempotent + in-flight + cooldown). HOME giữ trivial renderer.
- **Off-car only**: verify JVM + emulator. Việc chạm-xe (freeform fps/z-order, BydHal thật, state-bền sau reboot vật lý) đánh dấu **🚗 pending**, KHÔNG chặn autonomous run, KHÔNG coi `adb reboot` là bằng chứng on-car.

## CONSTRAINTS
- Mỗi stage: build XANH (`:app:assembleDebug`) + test liên quan XANH + máy chạy được → mới sang stage sau.
- Byte-stable các lệnh shell đã proven (chốt bằng `FakeShell` contract test ở B0; mọi refactor sau phải giữ y chuỗi lệnh).
- KHÔNG đụng `MainActivity`/layout seal. KHÔNG đổi appId/namespace. `:core` cấm `android.*`.
- File > 500 LOC → tách. Commit identity GIỮ repo config = **`Đăng Khôi <dangkhoi@users.noreply.github.com>`** (repo cá nhân; KHÔNG override dangkhoi; KHÔNG `-c`/`--author`).
- **Commit per-stage (local)** sau khi security scan CLEAN; **KHÔNG push** (owner tự push). KHÔNG `--no-verify`.
- Context7 verify dep TRƯỚC khi thêm (androidx.lifecycle, kotlinx-coroutines, turbine): version mới nhất, API không deprecated.

## BUILD / TEST / EMULATOR (dùng đúng)
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17; export PATH="$JAVA_HOME/bin:$PATH"
export ADB="$HOME/Library/Android/sdk/platform-tools/adb"
cd $HOME/Documents/workspaces/experiments/byd/byd-launcher
./gradlew :app:assembleDebug --console=plain            # build
./gradlew :core:test --tests "com.byd.clusternav.launcher.*" --console=plain   # test lớp lẻ khi lặp
./gradlew test --console=plain                          # full 5 module (stage cuối)
"$ADB" install -r -t app/build/outputs/apk/debug/app-debug.apk
# per-boot emulator: reverse loopback + dọn mirror + drawer dumpable + set HOME
"$ADB" reverse tcp:5555 tcp:5555
"$ADB" shell settings delete global overlay_display_devices
"$ADB" shell appops set com.byd.launcher SYSTEM_ALERT_WINDOW ignore
"$ADB" shell cmd package set-home-activity com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity
```
Verify ảnh: **luôn qua sub-agent** (đọc ảnh trực tiếp dễ tràn size). Đặt app tin cậy: appops ignore → `input tap` ô → `uiautomator dump` → regex tên tile → tap.

## SDD (song song code)
Tạo/cập nhật `docs/specs/kachi-architecture.html` (Apple 2026, inline CSS, sơ đồ SVG box-and-arrow — KHÔNG mermaid/ascii). Mỗi stage ghi §Design + §Tasks + §Nhật ký triển khai (quyết định/trade-off/sai-lệch). §Reviewer Log append-only.

---

## EXECUTION — 8-STAGE CHAIN
> Orchestrator prompt. Chạy tuần tự bằng `subagent` (blocking). Mỗi stage verify exit-gate rồi ghi handoff `docs/_handoff/arch-stage-N-done.md` rồi sang stage sau.

### Stage 0 (B0) — Safety net (test TRƯỚC)
**1 agent.** Chốt byte các lệnh shell proven thành `FakeShell` contract test (mở rộng mẫu `FreeformLaunchTest`): `am start --display`, `am task resize`, `am --windowingMode 5`, `am force-stop`, freeform-seed, cast swap. **Guard test**: (a) launcher KHÔNG phát lệnh nào target `displayId >= 1`; (b) freeform-global (`enable_freeform_support`/`force_resizable_activities`/`wm density`) có ĐÚNG 1 nơi ghi.
**Exit gate**: `./gradlew :core:test :app:testDebugUnitTest` XANH; contract test tồn tại + chạy. **Handoff**: `arch-stage-0-done.md` (danh sách lệnh đã chốt + tên test).

### Stage 1 (B1) — Gộp transport 3→1
**Reads** stage-0. **1 agent.** Tạo `ShellTransport` (`:app`, chủ dadb DUY NHẤT) sau seam `(String)->String` hiện có. Route `DadbShell` + `DadbSimpleCastShell` + `CastShell(adb)` cũ qua nó (3 kết nối → 1). `VdAppHost` + `winExec` + cast đều đi qua transport. `run()` serialize thật (1 consumer).
**Exit gate**: grep chỉ còn 1 nơi `Dadb.create`/mở kết nối 5555; build XANH; contract test B0 XANH (lệnh byte-stable); cài + đặt 1 app vào ô emulator (sub-agent xác nhận render). **Handoff**: `arch-stage-1-done.md`.

### Stage 2 (B2) — Command model + ownership registry
**Reads** stage-1. **≤2 agents.** `:core system/`: `WindowMutation` (sealed) + `DisplayOwnershipRegistry` + `AppLocationRegistry` (+ test JVM). Route reflow launcher + cast qua lệnh ĐƯỢC VALIDATE (cổng chủ-quyền, reject cross-boundary + log). **Gộp semantics `BoundedCastExecutor`** (priority-stop, latest-wins, timeout) vào hàng đợi transport — behavior-identical (guard bằng test B0).
**Exit gate**: test :core registry XANH; lệnh vượt biên bị reject (test); STOP không kẹt sau NORMAL (test); build XANH; emulator 2 app 2 ô KHÔNG nhảy (sub-agent). **Handoff**: `arch-stage-2-done.md`.

### Stage 3 (B3) — Single-writer state bền (đóng brick vector)
**Reads** stage-2. **1 agent.** `FreeformSeedPolicy` (:core policy + :app writer) = nơi DUY NHẤT ghi freeform-seed + `wm size/density`; marker `commit()`-trước-mutate; `FF_USER_REMOVED` terminal; reset = clearer DUY NHẤT. Cast + launcher đều qua nó.
**Exit gate**: guard test "1 writer" XANH; grep 0 nơi khác ghi `enable_freeform_support`/`force_resizable_activities`/`wm density`/`wm size`; build XANH. **🚗 pending**: verify state-bền sau **reboot vật lý** trên xe (KHÔNG dùng `adb reboot` làm bằng chứng). **Handoff**: `arch-stage-3-done.md`.

### Stage 4 (B4) — Input daemon riêng
**Reads** stage-3. **1 agent.** Dexed jar nhỏ chạy `CLASSPATH=... app_process / <Main>` (lệnh lifecycle qua queue), giữ `localabstract` socket, `InputManager.injectInputEvent(ASYNC)` + `MotionEvent.setDisplayId` (reflection, quyền `INJECT_EVENTS` của uid-2000). `VdAppHost` map toạ độ ô→display, stream event qua socket. **Fallback** `input -d` khi daemon không lên (emulator thiếu reverse). Chạm KHÔNG đi qua queue lệnh.
**Exit gate**: build XANH; emulator — chạm vào app trong ô có tác dụng (sub-agent: tap tab Clock đổi tab); đo latency/event (ghi handoff). Daemon fail → fallback vẫn chạm được. **Handoff**: `arch-stage-4-done.md`.

### Stage 5 (B5) — HomeViewModel + tách god-activity + DI
**Reads** stage-4. **≤2 agents.** Thêm `androidx.lifecycle` ViewModel + `kotlinx-coroutines` (Context7 verify version). `HomeViewModel` giữ `StateFlow<HomeUiState>`; **chuyển state-source từ `WorkspaceView` → ViewModel**; `KachiHomeActivity` collect `repeatOnLifecycle` + render; tách view-component (TopStrip/Dock/Drawer). 1 `AppContainer` (gộp `SimpleCastRuntime`), `viewModelFactory` + `APPLICATION_KEY`. `WorkspaceView` thành view thuần (nhận state, phát event).
**Exit gate**: test ViewModel (Turbine) XANH; build XANH; emulator — đổi preset/đặt app/multi-widget/hồ sơ vẫn chạy (sub-agent); grep `WorkspaceView` KHÔNG còn giữ mutable state. **Handoff**: `arch-stage-5-done.md`.

### Stage 6 (B6) — Auto-start service
**Reads** stage-5. **1 agent.** Foreground `BootService` (BOOT_COMPLETED + khi mở app) → coordinator mount ô đã lưu idempotent (in-flight + cooldown). Cấu hình boot: chọn bố cục + app từng ô (dùng WorkspacePrefs theo hồ sơ). HOME giữ trivial. Phối hợp: KHÔNG auto-start app mà cast đang auto-chiếu lên cụm (hỏi `AppLocationRegistry`).
**Exit gate**: build XANH; emulator — kill launcher + relaunch → ô app tự mount lại (sub-agent). **🚗 pending**: cold-boot trên xe. **Handoff**: `arch-stage-6-done.md`.

### Stage 7 — Consolidate + Senior review + Security scan
**Reads** mọi handoff. **1–2 agents + 1 senior reviewer.**
- **Senior review** (sub-agent, model mạnh nhất, role senior Android architect): đọc `docs/specs/kachi-architecture.html` + toàn bộ file đổi; verify từng deliverable B0–B6 (code + test + wired + dùng được); **boundary shape check** (ShellPort↔transport, ViewModel↔View, WindowMutation producer↔consumer, cast↔coordinator); Context7 tech-freshness (androidx.lifecycle/coroutines/turbine mới nhất, API không deprecated); tự patch [P0]–[P3]; **review loop** tới 0 finding P0/P1.
- **Full test**: `./gradlew test` 5 module (seal restore trước/sau nếu chạm).
- **Security scan** (sub-agent, report-only): quét toàn bộ diff (secrets/PII/key/infra). `[BLOCK]` → DỪNG báo owner. `[WARN]` → ghi. CLEAN → commit cuối.
**Exit gate**: full test XANH; senior 0×P0/P1 + scope 100%; scan CLEAN; SDD complete. **Handoff**: `arch-stage-7-done.md` (tổng kết + danh sách 🚗 pending on-car).

---

## ORCHESTRATOR INSTRUCTIONS (Variant A — Kiro CLI)
1. Đọc lại review + TARGET ở trên (stay on track).
2. Chạy Stage N bằng `subagent` (blocking). Prompt sub-agent PHẢI kèm: "Context7 verify dep trước khi thêm; giữ byte-stable lệnh shell; :core cấm android.*; file ≤500 LOC".
3. Sau sub-agent → tự verify exit-gate (chạy build/test/grep/emulator+sub-agent-ảnh). Scope check: mọi deliverable stage = ✅.
4. PASS → **security scan diff** → CLEAN thì **commit local** (message trace stage) → ghi handoff → sang stage sau. **KHÔNG push.**
5. FAIL → tự fix trong context, re-verify, KHÔNG skip. Cùng lỗi ≥3 cách khác nhau vẫn kẹt → ghi handoff + báo (chỉ khi đó mới dừng).
6. **AUTONOMOUS**: KHÔNG hỏi owner giữa chừng cho: chọn cách impl (đã ở TARGET), fix test, ambiguity nhỏ (chọn an toàn, ghi §Nhật ký), review finding, scope gap nhỏ. **CHỈ dừng khi**: security `[BLOCK]`, hoặc hard-blocker lặp lại đã thử ≥3 cách.
7. Việc chạm-xe = đánh dấu 🚗 pending, KHÔNG chặn run.
8. Cuối: verify FINAL EXIT CRITERIA; cập nhật `docs/README.md` (index) + `PROJECT-BACKLOG.md` (ARCH/X1/U*/W1/S1/P7 → trạng thái) + `project-context.md`.

## ERROR RECOVERY
- Sub-agent output thiếu → đọc, xác định phần thiếu, re-run stage scope hẹp.
- Context gần đầy → ghi progress vào handoff, resume từ handoff mới nhất.
- Test fail sau stage → fix trong stage đó trước khi sang.
- Context7 unavailable → dùng version đã biết mới nhất, cờ "unverified" trong SDD.
- Senior review P0/P1 → fix ngay, rerun test, rerun review (loop).
- dadb/emulator hang → `withTimeout` + reconnect + `adb reverse` lại; nếu emulator chết → nêu ở handoff (không tự tạo AVD mới trừ khi cần).

## FINAL EXIT CRITERIA
- [ ] B0–B6 DONE (code + test + wired + emulator-verified off-car).
- [ ] 1 `ShellTransport` duy nhất sở hữu dadb (grep xác nhận 3→1); mọi lệnh cửa sổ serialize qua nó.
- [ ] Guard tests XANH: launcher KHÔNG target display ≥1; state-bền (freeform/`wm`) có ĐÚNG 1 writer.
- [ ] Semantics cast (priority-stop/latest-wins/timeout) GIỮ trong queue hợp nhất (test).
- [ ] Chạm qua input daemon (fallback `input -d`); chạm KHÔNG đi qua queue lệnh.
- [ ] `HomeViewModel` là source-of-truth (`WorkspaceView` hết giữ state); 1 `AppContainer` (gộp SimpleCastRuntime).
- [ ] Auto-start service mount ô lúc boot (emulator relaunch verified; 🚗 cold-boot xe pending).
- [ ] `MainActivity`/layout seal KHÔNG đụng; appId/namespace giữ; `:core` cấm `android.*` (LayeringRulesTest XANH).
- [ ] `docs/specs/kachi-architecture.html` complete (Design + sơ đồ SVG + Tasks + Verification + Nhật ký + Reviewer Log).
- [ ] Context7 tech-freshness: 0 API deprecated trong code mới.
- [ ] Senior review APPROVED (0×P0/P1, scope 100%, boundary shapes khớp).
- [ ] Security scan CLEAN (0 `[BLOCK]`) mỗi commit.
- [ ] `docs/README.md` + `PROJECT-BACKLOG.md` + `project-context.md` cập nhật khớp code.
- [ ] Commit per-stage local (identity Đăng Khôi), CHƯA push (chờ owner).
- [ ] Danh sách 🚗 pending on-car liệt kê rõ ở `arch-stage-7-done.md` (freeform fps/z-order, BydHal thật, state-bền sau reboot vật lý, cold-boot auto-start, coexistence cast trên xe).
