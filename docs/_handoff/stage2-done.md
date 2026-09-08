# stage2-ui — DONE (2026-09-06)

ClusterNav UI stage. Three tasks: T1 hero nav wiring, T2 shrink UI to ~70%, T3 card doubled-border fix.
All off-car. Build green, tests green, seal re-pinned, id parity 87 preserved.

## Verification (this run)

- Build: `./gradlew :app:assembleDebug -q` → **exit 0**.
- Tests: `./gradlew :app:testDebugUnitTest :offcar-planner:test :core:test --rerun-tasks --continue -Dorg.gradle.parallel=false` → **BUILD SUCCESSFUL**.
  - `:app:testDebugUnitTest` — **467 tests, 0 failures, 0 errors**.
  - `:offcar-planner:test` — **99 tests, 0 failures, 0 errors** (incl. re-pinned seal).
  - `:core:test` — **807 tests, 0 failures, 0 errors** (incl. new `HeroArrowTest`).
- Gate tests (each 0 fail): `ExpansionTransportFenceTest` 10 · `LayoutVariantIdParityTest` 2 · `L2CockpitUiWiringContractTest` 10 · `LayeringRulesTest` 9 · `HeroArrowTest` 5.
- `@+id` parity: narrow **87**, wide **87**, id sets identical (`diff` empty) — **0 id add/remove/rename**.
- grep confirms wiring: `MainActivity.updateHeroStrip` sets `hero_road` ← `NavRepository.state.road`, `hero_nav_icon` ← `heroArrowRes(state.maneuver)` (via core `toHeroArrow`).

## T1 — HERO nav wiring (MainActivity.updateHeroStrip)

Fixed 3 bugs, all reads pure + `runCatching` on the state snapshot, degrade to placeholder off-car:
- (a) `hero_road` now ← `NavRepository.state.road` (street/next-road) when navigating; falls back to the source-status line (`navStatusText`) only when not navigating / blank. (Was always the status string.)
- (b) `hero_nav_icon` now ← the turn maneuver arrow: `state.maneuver` (neutral `Maneuver`) → `heroArrowRes()` → an existing `res/drawable` arrow. Degrades to the static `ic_turn_right_g` placeholder when no maneuver / not navigating / off-car. (Was a static placeholder, never wired.)
- (c) `hero_dist` kept, gated on `nav.active` — **confirmed correct**: `NotificationParser.parse` returns `NavState(active = true, …)` and `NavRepository.stop()` publishes `NavState()` (active=false), so `active` is exactly how NavRepository marks an active session.

**Mapping added (pure, unit-tested):**
- `core/.../navigation/HeroArrow.kt` — `enum HeroArrow { LEFT, RIGHT, STRAIGHT, UTURN }` + `fun Maneuver.toHeroArrow()` (exhaustive `when`, Android-free).
- `MainActivity.heroArrowRes(Maneuver): Int` — maps the bucket to `R.drawable.ic_turn_left / ic_turn_right / ic_turn_straight` (UTURN reuses the left glyph — RHT/VN convention, matching `Maneuver.toHudIcon`'s uturn→left fold; no dedicated u-turn drawable exists).
- Test `core/.../navigation/HeroArrowTest.kt` (5 tests) — placed in `:core` (not `:app`) because it exercises only `:core` logic; the app test in `:app` (`LayoutVariantIdParityTest`/`LayeringRulesTest`) forbids core-only tests in `:app`.
- App-side wiring regression added to `L2CockpitUiWiringContractTest` (source-text contract: `hero_nav_icon`/`hero_road`/`heroArrowRes`/`toHeroArrow`).

## T2 — Shrink UI to ~70%

Shared-first, then inline. `touch_min` floored at 40dp (tap target).
- `res/values/dimens.xml` — text/pad/gap/radius tokens ×0.7: title 24→17, status 16→11, body 15→11, label 13→9, card_pad 16→11, screen_pad 20→14, gap 14→10, radius 16→11, touch_min 56→**40** (floor).
- `res/values/styles.xml` — 65 literal `dp/sp` scaled ×0.7 (HeroDist 34→24, HeroCard padding 18→13 / elevation 14→10, Group elevation 10→7, Row minHeight 58→41 + pad, Compact buttons 38→27, IconTile 34→24, Spinner/StatusPill/SegmentButton/Pill, HeroLead/Subtitle 12→8, HeroStreet 14→10).
- Custom-view defaults/intrinsics ×0.7: `SpeedDialView` 60→42 + ring 3→2; `ClusterPreviewView` 140×54→98×38, radius 12→8, label cap 11→8; `SegmentedControlView` text 12.5→9sp, seg pad 13/6→9/4, track pad 3→2; `SeatDiagramView` default 260×150→182×105; `Pm25GaugeView` default 72→50.
- Both layouts (`res/layout/activity_main.xml` + `res/layout-w960dp/activity_main.xml`) — **128 inline `dp/sp` values each** scaled ×0.7 via `scripts/shrink-ui-70.py` (comment-aware, skips `0dp` weights, `1dp` dividers stay 1dp). Applied to BOTH for consistency; NO id added/removed/renamed.

**Seal re-pin (T11 narrow layout):**
- `offcar-planner/.../ExpansionTransportFenceTest.kt`:
  - `T11_HASHES["app/src/main/res/layout/activity_main.xml"]`: `dfd93e64…b37230` → **`1a7c90f7e499909a7e8b3ad577c024eeec3421bafa37c19b574758abd636f921`** (`lần 24 (thu UI 70%)` KDoc added).
  - `strings.xml` hash **unchanged** (`8300437c…e4cdd`) — strings.xml not touched.
  - Wide layout `layout-w960dp/activity_main.xml` is **not** hash-pinned, so no re-pin needed (only its inline sizes were scaled).

## T3 — Card doubled-line-above-border fix

Removed the top-highlight `<item>` layer (which did not follow the 20dp rounded corners → doubled line) from all three `*_bg` layer-lists; kept gradient + stroke + rounded corners + elevation:
- `res/drawable/hero_card_bg.xml`, `res/drawable/group_bg.xml`, `res/drawable/card_bg.xml`.
- Verified no other drawable retains a `gravity="top"` highlight layer. `@color/card_highlight` is now unused (harmless; left defined).

## Files changed (stage2-ui only)

Modified: `MainActivity.kt` · `ui/SpeedDialView.kt` · `ui/ClusterPreviewView.kt` · `ui/SegmentedControlView.kt` · `comfort/SeatDiagramView.kt` · `comfort/Pm25GaugeView.kt` · `res/values/dimens.xml` · `res/values/styles.xml` · `res/layout/activity_main.xml` · `res/layout-w960dp/activity_main.xml` · `res/drawable/hero_card_bg.xml` · `res/drawable/group_bg.xml` · `res/drawable/card_bg.xml` · `app/src/test/.../L2CockpitUiWiringContractTest.kt` · `offcar-planner/.../ExpansionTransportFenceTest.kt`.

Added: `core/.../navigation/HeroArrow.kt` · `core/src/test/.../navigation/HeroArrowTest.kt` · `scripts/shrink-ui-70.py`.

> NOTE: `git status` also shows unrelated concurrent work (`modules/hal/BydHal.kt`, `com/byd/clusternav/body/*`, `HalProbe*`, `BydHalCallNamedIntTest.kt`, vehicleTest manifest, `docs/PROJECT-BACKLOG.md`, several `docs/diagnostics/*`). These are **NOT** part of stage2-ui and were left untouched; the suite is green with them present.

## Not done / notes
- ~70% is a visual change with no automated size assertion; verified by uniform ×0.7 scaling of tokens/styles/views/inline values (build + parity + seal green). On-car visual confirmation still pending.
- Styles.xml design-system comments still cite the pre-shrink mockup sizes as rationale (e.g. "38dp min touch height"); left as historical mockup reference.
- NO git, NO version change (per task).
