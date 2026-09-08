# Stage: Visual badge-placement UI + relocate VietMap source — DONE

> Branch `feat/speed-limit-badge-hal-hud`. Spec: `docs/specs/speed-badge-placement-vietmap-logging.html` §4.2–4.3 (Part A + Part B).
> Builds on `stage-foundation-done.md` (absolute-centre Prefs `badgeCenterX/Y` + `badgeSizeDp`, pure `BadgeLayout.clampCenter/topLeftFromCenter`, and the ONE shared `SpeedBadgeOverlay` / BUG-1 unify). This stage adds the drag-to-place editor and moves the VietMap speed block from the Nav+HUD card into the Cluster Cast card in BOTH layouts.

## Files changed / created (5)
- **NEW** `app/src/main/java/com/byd/clusternav/modules/clustercast/BadgePlacementView.kt` (156 LOC) — custom `View` mirroring `CastResizeView`. Ctor `(context, clusterWidth, clusterHeight, onMoved)`. Draws a **letterboxed** cluster-proxy rect (preserves `clusterW:clusterH` aspect so the badge stays circular) + a draggable badge marker rendered like `SpeedBadgeView` (white fill / red ring / bold "50") at the badge CENTRE (cluster px → view px). Touch DOWN/MOVE drag the centre to the finger, clamped with `BadgeLayout.clampCenter`; ACTION_UP → `onMoved(centerXcluster, centerYcluster)`. Public: `setBadgeCenterCluster(cx,cy)`, `setBadgeSizeCluster(px)` (both `invalidate()`). `requestDisallowInterceptTouchEvent(true)` on grab so it doesn't fight the ScrollView; returns `false` outside the proxy so the page still scrolls.
- **NEW** `app/src/main/java/com/byd/clusternav/modules/clustercast/BadgePlacementController.kt` (131 LOC) — wires the view + size `SeekBar` + preview/reset buttons + the VietMap bind-status line. Extracted so `MainActivityCastController` stays a thin renderer (< 501 LOC guard).
- `app/src/main/java/com/byd/clusternav/modules/clustercast/MainActivityCastController.kt` (298 → 309 LOC) — instantiates + binds `BadgePlacementController`; refreshes bind-status on resume.
- `app/src/main/res/layout/activity_main.xml` (narrow) — moved the VietMap speed block out of Nav+HUD into Cluster Cast (above `cast_recovery_toggle`); split the button row so `btn_nav_stop` stays (full-width).
- `app/src/main/res/layout-w960dp/activity_main.xml` (wide) — same move into the RIGHT column's Cluster Cast ScrollView.
- `offcar-planner/src/test/kotlin/com/byd/clusternav/offcar/ExpansionTransportFenceTest.kt` — **re-pinned** `T11_HASHES["…/layout/activity_main.xml"]` (documented procedure; the narrow layout changed intentionally).

## Layout moves (Part A) + new ids (Part B)
**Moved OUT of the "Navigation + HUD" card (both layouts), same ids kept:**
- label "Nguồn tốc độ + cảnh báo (biển báo · camera)", `spinner_speed_source`, `btn_vietmap_widget_diag`.
- `btn_nav_stop` **stays** in the Nav card — the old 2-button horizontal row was split; `btn_nav_stop` is now full-width and `btn_vietmap_widget_diag` was the only button relocated.

**Inserted into the "Cluster Cast" card, inside `cast_body`, directly ABOVE `cast_recovery_toggle`:**
- a `View` divider + header TextView "Biển báo tốc độ trên cụm"
- the moved speed-source label + `spinner_speed_source`
- **NEW** `txt_speed_source_bind` (TextView, "Nguồn: chưa kết nối")
- `btn_vietmap_widget_diag` (kept id; text now "Kết nối / Dữ liệu VietMap")
- **NEW** `badge_placement_container` (FrameLayout, height 140dp) — hosts `BadgePlacementView`
- row: TextView "Cỡ" + **NEW** `seek_badge_size` (SeekBar)
- row: **NEW** `btn_badge_preview` ("Xem thử trên cụm") + **NEW** `btn_badge_reset` ("Đặt lại")

New view ids added: `txt_speed_source_bind`, `badge_placement_container`, `seek_badge_size`, `btn_badge_preview`, `btn_badge_reset`.
Existing ids preserved (so MainActivity `findViewById` stays valid): `spinner_speed_source`, `btn_vietmap_widget_diag`, `btn_nav_stop`.

### Ordering proof (grep, id declarations)
| id | narrow line | wide line |
|---|---|---|
| `cast_geometry_container` (inside `cast_body`) | 118 | 212 |
| `spinner_speed_source` | 186 | 280 |
| `txt_speed_source_bind` | 188 | 282 |
| `badge_placement_container` | 195 | 289 |
| `seek_badge_size` | 202 | 296 |
| `btn_badge_preview` | 208 | 302 |
| `btn_badge_reset` | 212 | 306 |
| `cast_recovery_toggle` | 218 | 312 |

`cast_geometry_container < spinner_speed_source < cast_recovery_toggle` in BOTH layouts ⇒ the speed block is inside the Cluster Cast card, above the recovery toggle. Exactly one declaration of each moved id per layout (no duplicate left in the Nav card).

## Wiring (file:line)
- `MainActivityCastController.kt:40` — `private lateinit var badgePlacement: BadgePlacementController`
- `MainActivityCastController.kt:117` — `badgePlacement = BadgePlacementController(activity).also { it.bind() }` (after `geometryEditor` in `onCreate`)
- `MainActivityCastController.kt:134` — `if (::badgePlacement.isInitialized) badgePlacement.refreshBindStatus()` (in `onResume`)
- `BadgePlacementController.kt:37` — build `BadgePlacementView` into `R.id.badge_placement_container`, cluster W/H from cast display config (`SimpleCastState.CastingFull/CastingSplit.displayConfig.wmSize`, fallback 1920×720)
- `BadgePlacementController.kt:41–48` — init marker from `Prefs.badgeCenterX/Y` + size; `onMoved` → `clampCenter` → `Prefs.setBadgeCenterX/Y` + `debugRefreshBadgeLayout()`
- `BadgePlacementController.kt:60–71` — `seek_badge_size` maps progress 0..180 → dp 60..240 → `Prefs.setBadgeSizeDp` + `view.setBadgeSizeCluster` + `debugRefreshBadgeLayout()` (guarded `fromUser`)
- `BadgePlacementController.kt:76–77` — `btn_badge_preview` → `NavigationSpeedSignOwner.debugForceBadge(50)`
- `BadgePlacementController.kt:79,85–93` — `btn_badge_reset` → reset Prefs to default centre + `SIZE_DEFAULT_DP`, update view + SeekBar, refresh overlay
- `BadgePlacementController.kt:81,97–110` — `txt_speed_source_bind` reflects `VietMapWidgetBridge.bindingStatuses()` (chưa cài / chưa kết nối / kết nối một phần / đã kết nối)
- `spinner_speed_source` + `btn_vietmap_widget_diag` listeners are **unchanged** in `MainActivity.onCreate` (ids preserved, position-independent `findViewById`).

## Cluster W/H source
`BadgePlacementController.clusterSize()` reads the active cast display config `wmSize` (same accessors `CastGeometryEditor` uses) with fallback **1920×720**. The overlay itself re-clamps against the REAL display-1 size + density at render, so preview and live badge agree on the Seal cluster.

## Fence-hash re-pin — YES (required)
Editing the **narrow** `layout/activity_main.xml` changed its SHA-256, which `ExpansionTransportFenceTest` pins in `T11_HASHES`. Re-pinned per the documented procedure (precedent: 1.28 TASK4; the strings.xml `7e5113f7→4b068200` re-pin):
- `app/src/main/res/layout/activity_main.xml`: `ef73de91…b5ba8` → **`96f4092d176d38dfba38855461b5376ca6c80c72be54511feda53e9719ad39fa`**
- (`layout-w960dp/activity_main.xml` is NOT pinned — no change needed; `strings.xml` pin untouched.)

## GATE result
- `:app:testDebugUnitTest` → **result=SUCCESS total=377 failed=0** (afterSuite reporter). The `BUILD FAILED` banner is the proven-environmental Gradle 9.6.1 report-store `close()` bug — confirmed here as `java.io.EOFException` on the task at report finalization (identical to the foundation stage; no test XML has `failures>0`). Includes the layout-reading contract tests (NavCastUiWiringContractTest incl. the `< 501` renderer-LOC guard, CastEnableToggleContractTest, ClusterModeSelectorContractTest, HeadlessAutostartContractTest, HudOutputHiddenContractTest, DeadReckonRetirementTest) — all green, count unchanged from foundation (no regression, nothing dropped).
- `ExpansionTransportFenceTest` → **result=SUCCESS total=10 failed=0** after re-pin.
- Full `:offcar-planner:test` → **result=SUCCESS total=99 failed=0** (re-pin caused no cascade).

To read true counts under the report-store bug:
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testDebugUnitTest --console=plain --init-script /tmp/testsummary.gradle --rerun-tasks --continue
```
where the init script prints `TESTSUMMARY task=… result=… total=… failed=…` from each root suite via `afterSuite` (fires before the buggy store close). Temp init script removed after verification.

## Constraints honored
- Did NOT touch `keystore.properties` / `release.keystore` / `local.properties` / `main`. No git commit/push, no APK build.
- UI safe/degrade: every `findViewById` in the controller is null-tolerant; preview/refresh go through the degrade-safe overlay (no-op off-car); bind-status read wrapped in `runCatching`.
- Files ≤ 500 LOC: BadgePlacementView 156, BadgePlacementController 131, MainActivityCastController 309. `MainActivity.kt` left untouched (already 640 pre-existing; no wiring added there — extracted a controller instead, per the task).
- All moved ids kept.
