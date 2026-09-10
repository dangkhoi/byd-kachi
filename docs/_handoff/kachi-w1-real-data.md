# W1 — Dữ liệu & điều khiển xe THẬT (registry-driven) — Execution Prompt

> Auto-generated từ plan: `docs/specs/kachi-w1-real-data.html` (DRAFT, OQ đã chốt, gate an toàn ĐÃ BỎ)
> Catalog nguồn: `docs/diagnostics/kachi-capability-catalog-2026-09-10.md`
> Stages: **4** | Deliverables: **W1a–W1f** | Platform: **Kiro CLI (Variant A — subagent DAG, autonomous)**

## TASK
Thay lớp dữ liệu/điều khiển xe GIẢ (`DemoCarData`/`NoCar`) bằng lớp THẬT **registry-driven**, nối tối đa catalog đã harvest (~112 telemetry đọc · ~75 control · ~213 feature-id) qua `BydHal` reflection. Widget + nút tối đa, có tier bằng chứng, **KHÔNG gate an toàn** (owner "tự dùng tự chịu").

## WORKING DIR
`<repo root>` (thư mục checkout byd-launcher trên máy chạy)

## CONTEXT (≤5 dòng)
- App = launcher Kachi (fork ClusterNav), `com.byd.launcher`, đã push `dangkhoi/byd-kachi`. Kiến trúc sạch B0–B6 xong: Domain `:core system/`+`launcher/` (THUẦN) / Data+UI `:app` (UDF + `HomeViewModel`/StateFlow).
- Cổng xe hiện có: `core/.../launcher/LauncherPorts.kt` (`CarDataPort` 6-read, `CarControlPort` toggle/step, `NoCar` off-car). `ControlRegistry.kt` = 20 `ControlDef {TOGGLE,STEP}`, CHƯA có tier/domain.
- 2 cơ chế chạm xe (tái dùng, KHÔNG copy code — MIT clean-room): (1) reflection in-process `BydHal.device(FQN,ctx)` → named-method (proven: ghế/kính/PM2.5/tốc độ) HOẶC `set(int[]{fid}, EventValue)` (raw feature-id — đường Overdrive); (2) daemon uid-2000 CHỈ khi in-process no-op.
- Off-car: adapter thật trả `null` ⇒ UI hiện **"—"** (owner chốt OQ1: KHÔNG demo). HAL null-safe sẵn.

## CONSTRAINTS
- **KHÔNG gate an toàn** (đã gỡ khỏi spec): mọi control ghi THẲNG bất kể speed/gear; KHÔNG `SafetyGate`, KHÔNG chặn, KHÔNG nhãn cảnh báo ADAS. `domain` CHỈ để gom nhóm panel.
- `:core` THUẦN (cấm `android.*`) — khoá bởi `LayeringRulesTest`. Mỗi file ≤ 500 LOC. Degrade-safe (off-car KHÔNG crash).
- **KHÔNG đụng**: seal T11 / `MainActivity` (ClusterNav settings 1386 dòng) / `appId` / logic cluster-cast / input-daemon.
- **MIT clean-room**: document + reimplement trên hạ tầng `BydHal` của repo; KHÔNG copy verbatim mã Overdrive/dashcast; feature-id số + CAN opcode là FACTS (dùng được); ghi attribution vào `CREDITS.md`.
- Evidence tier hiện rõ, KHÔNG bịa: PROVEN dùng bình thường; OVERDRIVE/DASHCAST NỐI + hiện + badge "chưa kiểm trên xe"; off-car/null/`-2147482648` (NOT_PROVISIONED) ⇒ "—" + mờ.
- Test: JVM `:core` + `:app` unit; emulator cho render. Phần chạm HAL thật = 🚗 on-car (grab-list ở spec §9).
- Commit author = `Đăng Khôi <dangkhoi@users.noreply.github.com>` (repo cá nhân — KHÔNG override). **KHÔNG push** (owner quyết).

---

## EXECUTION — 4-STAGE CHAIN

> ⚠️ Orchestrator prompt. KHÔNG implement trực tiếp — chạy từng stage bằng `subagent` (blocking, role `kiro_default`).
> Mỗi sub-agent tự đọc file (KHÔNG paste code vào prompt). Mỗi stage: sub-agent tự chạy Context7 verify TRƯỚC khi code + ghi §Nhật ký triển khai vào spec (SDD = chính `docs/specs/kachi-w1-real-data.html`).
> **Autonomous**: chạy hết 4 stage KHÔNG dừng hỏi. Chỉ DỪNG khi security scan [BLOCK] (Stage 4) hoặc scope-change lớn ngoài plan.

---

### Stage 1 — Capability layer `:core` THUẦN (W1a)
**Sub-agents**: 1 (kiro_default, role core-domain)
- Agent 1: 5 deliverable —
  - `TelemetryRegistry.kt` — `TelemetrySpec(id, label, unit, domain, widgetKind, tier, bindingKey)`; nạp toàn bộ catalog §A (8 domain: energy/charge · drivetrain/speed · climate/air · tyres · body/doors/windows · lights · safety/ADAS · identity), ~112 datum.
  - `ControlRegistry.kt` (MỞ RỘNG, giữ tương thích 20 id + `DockConfig` + test cũ) — thêm `ControlKind` COVER/SELECT/BUTTON; thêm `domain` + `tier` + `args` vào def; nạp hướng catalog §B (~75). **KHÔNG** thêm `safetyClass`.
  - `CarStatus.kt` — data class bất biến, mọi field nullable (null = chưa đọc/không có), phủ telemetry §A.
  - `CarCapabilities.kt` — id nào WIRED + `tier` (UI badge/mờ theo đây).
  - `WidgetKind` + `EvidenceTier{PROVEN,OVERDRIVE,DASHCAST,NEEDS_CAR}` enums (render/badge hints).
- **Context7**: xác nhận không cần dep mới cho `:core` (thuần Kotlin); nếu dùng gì mới → verify + ghi spec.
- **SDD**: ghi quyết định/deviation vào `docs/specs/kachi-w1-real-data.html` §9 + §10 Reviewer Log.
- **Key files đọc**: `core/.../launcher/{LauncherPorts,ControlRegistry,HomeUiState,WidgetRegistry}.kt` · `core/.../{comfort/*,body/*}.kt` · catalog §A/§B/§C · spec §3/§4.
- **Exit gate** (verify bằng lệnh):
  - [ ] `./gradlew :core:test` XANH (gồm test mới cho registry/CarStatus/CarCapabilities).
  - [ ] `TelemetryRegistry` phủ đủ 8 domain; `ControlRegistry` có đủ 5 `ControlKind`; test cũ (`ControlRegistry`/`DockConfig`) vẫn xanh.
  - [ ] `LayeringRulesTest` xanh (0 `android.*` trong `:core`).
  - [ ] Grep xác nhận KHÔNG có `SafetyGate`/`safetyClass` trong code mới.
- **Handoff** → `docs/_handoff/w1-stage1-done.md`: files tạo/sửa · shape `TelemetrySpec`/`ControlSpec`/`CarStatus` (tên field + kiểu + nullable) · `EvidenceTier` enum · `bindingKey` convention · test count.

---

### Stage 2 — HAL binding + data flow `:app` (W1b + W1d media)
**Reads**: `docs/_handoff/w1-stage1-done.md`
**Assumes done**: registry + `CarStatus` + `CarCapabilities` + kinds/tier ở `:core`.
**Sub-agents**: 2 song song
- Agent 1 (hal-binding): 4 deliverable —
  - `HalBindingTable.kt` (:app) — `id → read(dev):Any?` / `write(dev,args):Int(rc)`. Proven = named-method (reuse `SeatComfortApplier`/`Pm25FilterApplier`/`BodyworkControl`/`SpeedProvider`); Overdrive = `BydHal.device().set(int[]{fid}, EventValue)` kiểu `resolveOrFallback("Nested.FIELD", numeric)`. Sentinel `-2147482648`/`-2147482645` ⇒ unavailable.
  - `CarDataAdapter.kt` (:app implements `CarDataPort`, mở rộng đọc → build `CarStatus`).
  - `CarControlAdapter.kt` (:app implements `CarControlPort`) — route toggle/step/cover/select/button qua binding. **KHÔNG gate**.
  - `CarStatusRepository.kt` (:app) — coroutine poll **2 nhịp** (nhanh ~1s: speed/power/ADAS-alert · chậm ~10s: pin/range/tyre/temp/charge) → `StateFlow<CarStatus>`. Degrade-safe.
- Agent 2 (media-bridge): 1 deliverable —
  - `MediaBridge.kt` (:app) — `MediaSessionManager.getActiveSessions(<NotificationListener component>)` → media (title/artist/art/position/state) + transport (play/pause/next/prev). Feed widget nhạc. Degrade-safe off-car.
- **Context7**: verify `kotlinx-coroutines` (1.10.2) StateFlow + `android.media.session.MediaSessionManager`/`MediaController` API hiện hành.
- **Exit gate**:
  - [ ] `./gradlew :app:testDebugUnitTest` XANH (test: binding map named+fid+sentinel→unavailable · adapter route · media parse).
  - [ ] `./gradlew :app:assembleDebug` sạch.
  - [ ] Grep: KHÔNG có logic gate speed/gear trong adapter.
- **Handoff** → `docs/_handoff/w1-stage2-done.md`: files · binding table shape · **contract `CarStatus` flow** (field name + type + nullable — để Stage 3 so khớp) · media contract (field + callback).

---

### Stage 3 — UI renderer generic + wire + Tuỳ biến (W1c + W1e)
**Reads**: `docs/_handoff/w1-stage1-done.md` + `w1-stage2-done.md`
**Assumes done**: registry/tier (:core) + `StateFlow<CarStatus>` + adapter + media (:app).
**Sub-agents**: 2 song song
- Agent 1 (ui-render): 2 deliverable —
  - Widget renderer generic theo `widgetKind` (ring/card/gauge/board/strip) — mở rộng `WidgetViews.kt`/`WidgetRegistry`. Tier badge nhỏ "chưa kiểm trên xe" cho OVERDRIVE/DASHCAST; off-car/null ⇒ **"—"** + mờ.
  - Control tile renderer theo `ControlKind` (toggle/step/cover/select/button) — mở rộng `ControlDockView.kt`. **KHÔNG** gate feedback.
- Agent 2 (wire+customize): 3 deliverable —
  - `AppContainer.kt` — thêm `carData`/`carControl`/`carStatusFlow`; on-car = adapter thật, off-car = adapter thật trả null (⇒ "—"). **BỎ `DemoCarData`** (OQ1).
  - `KachiHomeActivity` + `HomeViewModel`/`HomeUiState` — collect `carStatusFlow` (repeatOnLifecycle) vào state (UDF một chiều); `dock.control = CarControlAdapter`.
  - Màn **Tuỳ biến** — chọn widget/nút từ registry; bộ mặc định spec §4.2 (HOME gọn: năng lượng/PM2.5/tốc độ/lốp/nhạc + dock khí hậu/ghế/kính/cốp/đèn-đọc); persist qua `WorkspacePrefs`. ADAS/HUD/drive gom panel riêng (KHÔNG ẩn — OQ3).
- **Context7**: verify AndroidX `lifecycle` (2.9.0) `repeatOnLifecycle` + StateFlow collect.
- **Exit gate**:
  - [ ] `./gradlew :app:assembleDebug` sạch + full `./gradlew test` (5 module) XANH.
  - [ ] `LayeringRulesTest` xanh; `LayoutVariantIdParityTest` (nếu chạm layout) xanh.
  - [ ] Emulator (nếu chạy được): widget default "—" off-car · tile bấm no-op · tier badge đúng · media đọc app nhạc thật · Tuỳ biến thêm/bớt được. (KHÔNG chạy được ⇒ ghi rõ, verify sau.)
- **Handoff** → `docs/_handoff/w1-stage3-done.md`: files · wiring `AppContainer`→ViewModel→View · danh sách widget/tile render được · bộ mặc định.

---

### Stage 4 — Consolidate: senior review + security scan + docs (W1f)
**Reads**: cả 3 handoff.
**Sub-agents**: 1 senior reviewer (model cao nhất, vd claude-opus) + 1 security scanner + (docs sync do orchestrator).

**Senior review** (spawn riêng):
```
TASK: Senior review + patch + scope completeness + technology freshness + boundary shape
ROLE: Senior architect
FILES: [mọi file W1 đổi qua 3 stage]  · SPEC: docs/specs/kachi-w1-real-data.html
REQUIREMENTS:
1. Đọc spec — liệt kê từng R1–R9 + W1a–f; verify code tồn tại + wired + test + user dùng được.
2. Boundary shape (đọc CẢ 2 đầu, so field-by-field, trace 1 giá trị E2E):
   - HalBindingTable → CarStatusRepository → StateFlow<CarStatus> → HomeViewModel → WidgetViews (tên field/kiểu/nullable/tier).
   - ControlRegistry(kind) ↔ ControlDockView(render) ↔ CarControlAdapter(write) — kind khớp, args khớp.
   - TelemetryRegistry(bindingKey) ↔ HalBindingTable(id) — mọi id có binding hoặc bị đánh dấu NEEDS_CAR.
3. Verify KHÔNG còn SafetyGate/gate speed-gear (owner đã bỏ). Verify off-car trả "—" (không demo).
4. Context7: coroutines/lifecycle/media — version latest? API deprecated?
5. Fix mọi finding [P0]–[P3] trực tiếp; nếu code đổi → rerun test → rerun review (loop tới 0 P0–P1).
6. Report: scope checklist ✅/❌ · tech freshness · boundary shape table · findings+severity · test result.
DO NOT: thêm feature ngoài scope; đụng seal/MainActivity/appId/cast.
```

**Security scan** (spawn riêng, TRƯỚC commit):
- Scan toàn diff W1 (secrets/credentials/PII/internal-infra) — pattern + semantic.
- ⚠ feature-id số + CAN opcode = FACTS (KHÔNG phải secret) — không flag. **Chú ý PII trong fixture/ảnh test** (lịch sử E7/E8: ảnh chụp máy owner từng lộ điểm đến). [BLOCK]/[WARN]/[INFO].

**Docs sync** (orchestrator, R1/R2.6):
- `docs/README.md` INDEX: thêm dòng catalog `kachi-capability-catalog-2026-09-10.md` (Diagnostics, Current) + handoff W1 + trạng thái spec W1.
- `docs/PROJECT-BACKLOG.md`: W1 (#6) → cập nhật trạng thái (✅ off-car / 🚗 on-car grab-list).
- `.kiro/steering/project-context.md`: entry trạng thái W1 (nối data thật registry-driven, off-car "—", no gate).
- `CREDITS.md`: attribution Overdrive-release (MIT © Yash Srivastava) + byd-dashcast (MIT © Cedric Carre) — clean-room.
- Re-index knowledge base sau khi đổi doc lớn.

**Exit gate**:
- [ ] Senior review verdict APPROVED (0 P0–P1, scope 100%).
- [ ] Tech freshness: 0 deprecated API mới.
- [ ] Security scan CLEAN (0 [BLOCK]).
- [ ] Full 5-module `./gradlew test` XANH + `:app:assembleDebug` sạch.
- [ ] Docs synced (index + backlog + context + CREDITS).
- [ ] Commit LOCAL (author Đăng Khôi, KHÔNG push) — HOẶC để owner commit nếu muốn xem trước.

---

## ORCHESTRATOR INSTRUCTIONS (Variant A — Kiro CLI)
1. Đọc lại spec `docs/specs/kachi-w1-real-data.html` (W6 — stay on track).
2. Chạy Stage 1 bằng `subagent` (blocking, role kiro_default). Prompt sub-agent PHẢI có: "Dùng Context7 verify dep TRƯỚC khi code; ghi §Nhật ký vào spec; KHÔNG SafetyGate; :core thuần ≤500 LOC/file".
3. Sub-agent xong → orchestrator TỰ verify exit gate (chạy `./gradlew` trực tiếp) + scope check (W3). PASS → ghi handoff → Stage kế. FAIL → fix trong context, re-verify, KHÔNG skip.
4. Lặp Stage 2 → 3 → 4. Stage 2 & 3 mỗi stage tối đa 2 sub-agent song song.
5. Stage 4: senior review (W4) + security scan (W5) + docs sync. Loop review tới 0 P0–P1.
6. Final: check lại MỌI exit criteria từ spec §6 + §3 (R1–R9).
7. **Autonomous**: KHÔNG dừng hỏi giữa chừng. DỪNG chỉ khi: security [BLOCK] (báo owner rotate/xoá) hoặc scope-change lớn ngoài plan.

## ERROR RECOVERY
- Sub-agent fail/partial → đọc output, xác định item thiếu, re-run stage scope thu hẹp.
- Test fail sau stage → fix TRONG stage đó trước khi proceed.
- Context gần limit → ghi progress vào handoff, resume từ stage kế.
- Context7 unavailable → dùng version best-known, flag "unverified" trong spec.
- Named-method no-op / rc sentinel → thử đường feature-id số; vẫn hụt → đánh dấu `NEEDS_CAR` (grab-list §9), KHÔNG bịa.

## FINAL EXIT CRITERIA (nguồn sự thật = spec §3 + §6)
- [ ] R1 registry-driven (telemetry + control là DATA, thêm 1 dòng = thêm 1 cap).
- [ ] R2 HAL binding có tier (named + feature-id fallback + sentinel handling).
- [ ] R3 evidence tier hiện rõ, KHÔNG bịa số.
- [ ] R4 widget tối đa (catalog §D1) · R5 nút tối đa (catalog §D2).
- [ ] R6 **KHÔNG gate an toàn** (đã bỏ — verify không còn SafetyGate/gate).
- [ ] R7 tuỳ biến + bộ mặc định · R8 live 2 nhịp StateFlow · R9 ranh giới (degrade-safe, :core thuần, ≤500 LOC, MIT clean-room, không đụng seal/cast).
- [ ] Full 5-module test XANH · senior APPROVED · security CLEAN · docs synced.
- [ ] 🚗 On-car (SAU): verify feature-id/method theo trim + đóng grab-list (spec §9) — KHÔNG thuộc off-car burn này.

**Workflow gates**: SDD = spec (complete) · Context7 verified · senior APPROVED (0 P0–P1) · security CLEAN · test xanh sau patch.
