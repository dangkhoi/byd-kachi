# ClusterNav 1.05 (versionCode 105) — Senior/Release Review (Pass 6)

- **Reviewer role:** Senior architect / release reviewer
- **Date:** 2026-08-11
- **Scope:** the four delta groups landed AFTER the Pass-5 senior review (which already APPROVED the 7-item Wave 1–4 batch)
- **Spec:** `docs/specs/cast-nav-ux-release-v104.html` (filename kept `v104`; release is now 1.05 — same feature set)
- **Mode:** off-car · **no commit/push** (owner-gated)
- **Verdict:** ✅ **APPROVED** — 0 P0–P1, release green

---

## 1. Scope checklist (4 delta groups)

| # | Delta | Code + wired (evidence) | Test | Status |
|---|-------|--------------------------|------|--------|
| 1 | **Submenu redesign (#7 follow-up)** | `BubbleSubmenuAnchor.offset` (pure `:core`, 34 LOC — card beside bubble: right if `bubbleCenterX ≤ screenW/2` else left, vertically centred, clamped inside screen + margin). `BubbleSubmenuOverlay` rewritten (186 LOC — `LinearLayout` WRAP + `SHOW_DIVIDER_MIDDLE` hairline dividers, rows `dp(ROW_HEIGHT_DP=40)`, `CARD_BG=0xCCFFFFFF` ~80% white, measured `UNSPECIFIED` then placed beside bubble). Drawables `ic_menu_left/right/config`. `FloatingBubbleService.onBubbleLongPress` passes `params.x/y` + `bubbleWidthPx()/HeightPx()`. | `BubbleSubmenuAnchorTest` 4 (`:core`) · `BubbleGestureContractTest` (+beside-bubble/icons/divider asserts) | ✅ |
| 2 | **Deep rescue (DashCast conflict)** | `CastDeepRescueAction` (105 LOC — confirm dialog → background `Thread("deep-rescue")`: (1) `dispatch(Stop())` + `closeProjection()` + `stopOwnServices()` — stand fully down, **no reopen**; (2) `am force-stop` DashCast + xdja helper; (3) reset cluster VD `wm size/density/overscan reset`; every step `runCatching`; final toast via `postUi`). `MainActivityCastController` wiring (coordinator in scope, `stopOwnServices = stopService(FloatingBubbleService)`, binds `R.id.cast_deep_rescue`). Button `cast_deep_rescue` in **both** layouts (`layout/activity_main.xml:176`, `layout-w960dp/activity_main.xml:241`). | `CastDeepRescueContractTest` 5 (`:app`) | ✅ |
| 3 | **P0 mitigation** | `scripts/vehicle/common.sh`: `b9a0259e7174…2bb598` added to `INVALIDATED_CANDIDATE_SHA256S` — **exact SHA-256 of** `apk/ClusterNav-1.04-v104-527589f2d16a-release.apk` (verified with `shasum`). `vehicle-candidate.json` still declares that apk ⇒ guard is fail-safe. | `require_candidate` → **exit 9** (run live) | ✅ |
| 4 | **Version bump** | `app/build.gradle.kts`: `versionCode = 105` / `versionName = "1.05"`. | aapt2 on release APK = `105` / `1.05` | ✅ |

All 4 delta groups: code exists, wired, tested, user-reachable.

---

## 2. Boundary-shape table (read both sides + trace 1 value E2E)

| Contract | Trace | OK |
|----------|-------|----|
| `BubbleSubmenuAnchor.offset` (`:core`, pure) ↔ `BubbleSubmenuOverlay` | `offset(screenW,screenH,anchorX,anchorY,anchorW,anchorH,cardW,cardH,gap,margin): Pair<Int,Int>`; overlay calls with `dm.widthPixels/heightPixels` + anchors + `card.measuredWidth/Height` + `dp(GAP_DP)/dp(EDGE_MARGIN_DP)`, applies returned `(leftMargin,topMargin)` to `FrameLayout.LayoutParams(TOP\|START)`. 10 params type/order-matched. | ✅ |
| `FloatingBubbleService.onBubbleLongPress` ↔ `overlay.show` | `show(anchor?.x ?: 0, anchor?.y ?: 0, bubbleWidthPx(), bubbleHeightPx()){action}` ↔ `show(anchorX,anchorY,anchorW,anchorH,onAction)`. Bubble window uses `gravity = TOP\|START` ⇒ `params.x/y` = true top-left; W/H fall back to `dp(ICON_SIZE_DP)` when the view is not yet measured. No shape gap. | ✅ |
| `CastDeepRescueAction` ↔ `SimpleCastCoordinator` | `dispatch(SimpleCastIntent.Stop())` (`Stop(slot: ClusterSlotSide? = null)` — no-arg ctor valid); `closeProjection(): Unit`; `executeShell(String): ShellResult` with `.success (= exitCode==0)` + `.stdout`. `DisplayParse.clusterDisplayId(String): Int` (`:core`, same package `…modules.clustercast`, so `:app` resolves it). Field-by-field matched. | ✅ |
| Controller wiring ↔ layouts | `coordinator = SimpleCastRuntime.coordinator(...)`; `CastDeepRescueAction(activity, coordinator, background=Thread("deep-rescue").start, postUi, toast=Toast.LENGTH_LONG, stopOwnServices=stopService(FloatingBubbleService)).bind(findViewById(R.id.cast_deep_rescue))`. `R.id.cast_deep_rescue` present in both `activity_main.xml` variants (grep-confirmed). Compile-clean ⇒ id resolves in both. | ✅ |
| `common.sh` sha ↔ `require_candidate` guard | `b9a0259e…` ∈ bash array; `require_candidate` `shasum`s the APK → `for blocked` loop → `exit 9` on match (before the `actual==declared` check). Live: `source common.sh; require_candidate` → `ERROR: invalidated or superseded vehicle candidate is prohibited: b9a0259e…` → **exit 9**. | ✅ |

**5/5 boundaries matched.** Each traced ≥1 value E2E (build compile-check + live `require_candidate`).

---

## 3. Findings

**No `[P0]` / `[P1]` / `[P2]`.**

- **`[P3]` (accepted — no patch).** Deep-rescue stand-down (`dispatch(Stop())` + `closeProjection()`) is enqueued on the coordinator's serial `BoundedCastExecutor` (async), while `am force-stop` + VD reset run synchronously on the `deep-rescue` thread ⇒ strict *completion* ordering of "stand-down before force-stop" is not guaranteed. **Accepted because:** (a) `DadbSimpleCastShell.execute` opens a **fresh** dadb connection per command (`Dadb.create(...).use`), so concurrent shell execution is race-free; (b) the operations are commutative for the final state — Stop/close only RETURN/REFRESH/RESET/close and never re-apply a dirty cast config that could overwrite the VD reset; (c) the design is explicitly best-effort and never reopens (test-locked: `!contains("openProjection")`); (d) it mirrors the existing "clear cluster" button pattern. **No change.**

---

## 4. Tech-freshness

**FRESH.** No new dependency (deltas use Kotlin stdlib + existing Android + `dadb` at the legacy runtime layer). APIs are current: `GradientDrawable`, `LinearLayout.SHOW_DIVIDER_MIDDLE`, `TYPE_APPLICATION_OVERLAY` (guarded `<26` → `TYPE_PHONE`), `AlertDialog`, `Thread`/`runOnUiThread`. AGP 9.3.1 built-in Kotlin, compileSdk 37, JUnit 5 — unchanged. 0 new API/library surface ⇒ Context7 not required.

---

## 5. Release-readiness (fresh evidence)

**Gate command**
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew \
  :core:test :app:testDebugUnitTest :car-integration:test \
  :app:assembleDebug :app:assembleRelease :app:lintRelease --console=plain
```
→ **BUILD SUCCESSFUL**.

**Tests** (`--rerun-tasks`, no cache; counted from `build/test-results/**`):

| Module | Tests | Failures | Errors |
|--------|-------|----------|--------|
| `:core:test` | 727 (+4 `BubbleSubmenuAnchorTest`) | 0 | 0 |
| `:app:testDebugUnitTest` | 328 (+7: `CastDeepRescueContractTest` 5 + `BubbleGestureContractTest`) | 0 | 0 |
| `:car-integration:test` | 28 | 0 | 0 |
| **TOTAL** | **1083** | **0** | **0** |

**Lint** — `lintRelease`: **0 Error / 0 Fatal** (`abortOnError=true`, `checkReleaseBuilds=true` ⇒ BUILD SUCCESSFUL).

**aapt2 — release APK** (`app/build/outputs/apk/release/app-release.apk`):
- `package='com.byd.clusternav'` · **`versionName='1.05'` `versionCode='105'`**
- `application-debuggable` **absent** ⇒ **not debuggable**
- Manifest scan: **0** `TEST_ADAS_*` / `TEST_SPEED_LIMIT`
- `RebindReceiver` `exported=false`, actions only `MY_PACKAGE_REPLACED` / `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` / `com.byd.clusternav.REBIND_WATCHDOG`
- `app/src/main` free of the T10 probe surface (only `MainProbeSurfaceAbsenceTest` asserts its absence; T10 harness confined to `app/src/vehicleTest/`)
- **WARN-1 hardening intact.**

**Code health** — all delta files ≤500 LOC: `BubbleSubmenuAnchor` 34, `CastDeepRescueAction` 105, `BubbleSubmenuOverlay` 186, `MainActivityCastController` 280, `FloatingBubbleService` 493. `:core` Android-free (`LayeringRulesTest` green).

**Docs** — `README.md` bumped: CURRENT STATUS → `OFF-CAR SOURCE 1.05 (versionCode 105)`, Current version → `1.05 (versionCode 105)`; prior 1.04 candidate marked **invalidated / SHA-blocklisted**; archived-apk `⚠️` warning retained (now notes the SHA is blocklisted). Reviewer Log Pass 6 appended (append-only).

---

## 6. Verdict

✅ **APPROVED.** 0 P0–P1 · 4/4 delta groups ✅ · 5/5 boundaries matched · release green (1083 tests 0F/0E; assembleDebug + assembleRelease + lintRelease SUCCESSFUL) · release APK **1.05/105, not debuggable, no test surface** (WARN-1 hardened) · P0 blocklist verified (`require_candidate` exit 9). 1 residual `[P3]` accepted (best-effort ordering; per-command shell is race-free). Review loop exits after 1 pass (no code patch needed for the deltas).

**No commit/push (owner-gated).**

> ⚠️ **On-car status unchanged:** Stage 11 has **NOT** started. The `vehicle-candidate.json` manifest still declares the invalidated 1.04 apk (now blocklisted → `require_candidate` refuses it). A fresh **1.05** candidate must be built via the authorized `collectAuthorizedApk` pipeline (which regenerates the manifest) before any on-car install.
