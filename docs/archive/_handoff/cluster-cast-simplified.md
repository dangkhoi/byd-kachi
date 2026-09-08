# Cluster Cast Simplified — Execution Prompt

> Auto-generated from plan: docs/specs/cluster-cast-simplified.html
> Owner-approved: 2026-08-02T23:51 · Autonomous mode — không dừng hỏi
> Stages: 3 | Total deliverables: 7 tasks + APK

## TASK
Implement simplified Cluster Cast architecture (projection-first, move-app) replacing the complex V2 state machine. Output: APK ready for on-car test tomorrow.

## WORKING DIR
`<project-root>`

## CONTEXT
- **core module**: pure Kotlin JVM, no Android, no dadb. State models + coordinators + tests here.
- **app module**: Android, has dadb, shell gateway, UI (bubble, home panel). Wiring + platform here.
- **Existing Cast V2**: `core/src/main/kotlin/.../modules/clustercast/v2/` — 57 files, ~2200+ LOC core.
- **Owner decision**: Keep existing V2 code for now (don't delete), add new simplified module alongside. Wire UI to new coordinator. V2 can be removed after on-car validation.
- **On-car tasks (T7)**: git-ignored, not implemented here. Focus = off-car code + tests + APK build.

## KEY CONSTRAINTS
- Files ≤500 LOC
- No `os.environ` / scattered config
- Two-pipeline independence (Nav+HUD vs Cast) must be preserved
- CP/AA = full cluster only, no split, no resize controls
- App thường = full or split (left/right), resizable, 1 config per app
- Mở app = open projection immediately
- Any bubble tap while casting = stop (for CP/AA full mode)
- Per-slot stop for split mode (app thường)
- Shell gateway reuse: `com.byd.clusternav.cast.platform.LocalProcessShellGateway` + dadb
- Package: `com.byd.clusternav.modules.clustercast.simplified`

## FIELD-PROVEN COMMANDS (from session 2026-08-02)
```
# Open projection (Seal DL3)
service call SurfaceFlinger 1035 i32 <displayId> i32 30   # curved
service call SurfaceFlinger 1035 i32 <displayId> i32 16   # keepKmh
service call SurfaceFlinger 1035 i32 <displayId> i32 35   # activate

# Close projection
service call SurfaceFlinger 1035 i32 <displayId> i32 18   # close
service call SurfaceFlinger 1035 i32 <displayId> i32 0    # refresh

# Display config
wm size <W>x<H> -d <displayId>
wm overscan <left>,<top>,<right>,<bottom> -d <displayId>
wm size reset -d <displayId>
wm overscan reset -d <displayId>
wm density reset -d <displayId>

# Cast normal app
am start --display <displayId> --windowingMode 5 -n <pkg>/<activity>

# Cast CP/AA (move task to stack on display 1)
am stack move-task <taskId> <stackId> true

# Return app to main
am start --display 0 -n <pkg>/<activity>    # normal
am stack move-task <taskId> <stackOnD0> true # CP/AA
```

## DISPLAY CONFIGS (measured on vehicle)
| Type | wm size | overscan | Density |
|------|---------|----------|---------|
| CarPlay | 1422x800 | 10,-120,10,50 | reset (320) |
| Android Auto | 1920x1080 | 0,0,0,0 | reset (320) |
| Normal (default) | 1920x800 | 0,0,0,0 | reset (320) |

## EXECUTION — 3-STAGE CHAIN

---

### Stage 1: Core models + coordinator + tests (pure Kotlin JVM in :core)

**New package**: `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/`

**Deliverables**:
1. `SimpleCastModels.kt` (≤150 LOC) — AppType enum, DisplayConfig, SlotState, SimpleCastState sealed interface (Off/Opening/Idle/CastingFull/CastingSplit/Stopping/Error)
2. `ProjectionManager.kt` (≤120 LOC) — open/close projection command sequences, idempotent
3. `DisplayConfigurator.kt` (≤120 LOC) — per-app-type wm size/overscan commands, skip-if-same
4. `AppMover.kt` (≤150 LOC) — cast-to-cluster / return-to-main per app-type, classify app
5. `SimpleCastCoordinator.kt` (≤200 LOC) — state machine (OFF→IDLE→CASTING_FULL/CASTING_SPLIT), single executor, delegates to above 3, exposes immutable state
6. `SimpleCastPrefs.kt` (≤80 LOC) — interface for per-app display config persistence

**Tests** (in `core/src/test/kotlin/.../simplified/`):
- `SimpleCastCoordinatorTest.kt` — all transitions, invalid rejection, concurrent safety
- `ProjectionManagerTest.kt` — command sequences, idempotency
- `DisplayConfiguratorTest.kt` — per-type config, skip-if-same
- `AppMoverTest.kt` — correct commands per app-type
- `IntegrationTest.kt` — full flow: open→cast→stop→cast-split→stop-one→close

**Interface for shell** (dependency inversion — core cannot import dadb):
```kotlin
interface SimpleCastShell {
    fun execute(command: String): ShellResult
}
data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)
```

**Key files to read first**:
- `core/src/main/kotlin/.../v2/CastSealCommands.kt` — existing seal command constants
- `core/src/main/kotlin/.../v2/CastModels.kt` — existing types (don't duplicate, reference)
- `core/src/main/kotlin/.../v2/CastGeometry.kt` — existing display config logic
- `core/src/main/kotlin/.../modules/clustercast/DisplayParse.kt` — display discovery

**Exit gate**:
- [ ] All 5+ new source files compile
- [ ] `./gradlew :core:test` passes (including new tests)
- [ ] Each file ≤500 LOC (verify with wc -l)
- [ ] No import of android.*, dadb.* in new :core files

---

### Stage 2: App module wiring — connect UI to SimpleCastCoordinator

**Deliverables**:
1. `SimpleCastRuntime.kt` (app module, ≤200 LOC) — Android implementation of SimpleCastShell using existing dadb/ShellGateway, lifecycle management, projection auto-open on app start
2. Wire bubble (FloatingBubbleService) to use SimpleCastCoordinator:
   - Tap when idle → cast foreground app (reuse existing `foregroundPackage()`)
   - Tap when casting full (CP/AA) → stop
   - Tap slot (left/right) when split → stop that slot
3. Wire Home panel to display SimpleCastState, hide resize for CP/AA
4. `CastFacade` updated or bypassed to route through simplified coordinator
5. Increment versionCode in `app/build.gradle.kts`

**Key files to modify**:
- `app/src/main/java/.../modules/clustercast/CastFacade.kt`
- `app/src/main/java/.../modules/clustercast/CastBubbleControl.kt`
- `app/src/main/java/.../modules/clustercast/MainActivityCastController.kt`
- `app/src/main/java/.../cast/platform/CastAndroidRuntime.kt`

**Exit gate**:
- [ ] `./gradlew :app:compileReleaseKotlin` passes
- [ ] `./gradlew :core:test` still green
- [ ] Bubble tap dispatches to SimpleCastCoordinator (trace via log)
- [ ] Home panel renders SimpleCastState correctly
- [ ] CP/AA: no resize UI shown
- [ ] App thường split: both slots independent

---

### Stage 3: Build APK + Senior Review

**Deliverables**:
1. Build release APK: `./gradlew :app:assembleRelease`
2. Record APK SHA-256 + version in `docs/_handoff/vehicle-candidate.json`
3. Senior review (scope + tech freshness + boundary shape)
4. Security scan before any commit

**Exit gate**:
- [ ] APK exists at expected path
- [ ] `./gradlew :core:test` all green
- [ ] `./gradlew :app:compileReleaseKotlin` green
- [ ] Senior review: 0 P0–P1 findings
- [ ] Security scan: CLEAN
- [ ] All spec deliverables ✅ (R1–R11 from spec)

---

## ORCHESTRATOR INSTRUCTIONS (Kiro CLI — has sub-agent tool)

1. **Đọc lại spec** `docs/specs/cluster-cast-simplified.html` trước khi bắt đầu mỗi stage
2. Implement Stage 1 trực tiếp (small batches per execution-reliability.md):
   - Read 3-5 existing files → write 1-2 new files → run `./gradlew :core:test` → fix → next batch
3. Sau Stage 1 pass → proceed Stage 2 (same pattern: read → write → compile → fix)
4. Stage 3: build APK, review, scan
5. **KHÔNG dùng large blocking sub-agent** (workspace rule). Work in direct small batches.
6. **KHÔNG hỏi user** — autonomous execution, self-decide all ambiguities (safe option)
7. **On-car tasks (T7) = git-ignored** — don't implement, don't block on them

## SCOPE CHECKLIST (from spec)
- [ ] R1: Mở app = projection mở ngay (onCreate)
- [ ] R2: CP/AA = full cluster only
- [ ] R3: CP/AA casting → any tap = stop
- [ ] R3a: App thường split left/right
- [ ] R3b: Stop per-slot independent
- [ ] R4: Tắt Cast = close projection
- [ ] R5: Per-app-type display config (CP/AA/Normal constants)
- [ ] R6: Cast repeatable (no kẹt)
- [ ] R7: UI ≤2s update (ledger-based, no observation verify)
- [ ] R8: CP/AA no resize controls
- [ ] R9: 1 config per app, system fits to slot
- [ ] R10: No freeze, no orphan (simple error → stay at last known state)
- [ ] R11: Two pipeline independence preserved

## ERROR RECOVERY
- Gradle fail → read error, fix, retry (max 3 per file)
- Test fail → read output, fix code, rerun
- Context approaching limit → save progress in handoff, tell user resume point
- Ambiguity → choose safest option, document in code comment
