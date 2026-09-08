# Stage `cdesc` — content-description fallback for VietMap/Waze nav capture — DONE

> Branch: `feat/speed-limit-badge-hal-hud` · WT: `byd-cluster-2-wt-speed-limit-badge-hal-hud`
> Gate: `./gradlew :core:test :app:testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL** (core 533 tests, app 392 tests, 0 failures / 0 errors). No commit/push, no APK build, keystore/local.properties/main untouched.

## Problem (evidence)

Live `uiautomator` dump on the car (VietMap navigating home): VietMap's a11y tree has 54 nodes, **text non-empty = 0**, but **content-desc non-empty = 14** carrying ALL the nav:

- `Sau đó (122m)\n50m Trần Trọng Kim` — turn dist + road
- `0\nkm/h\n60` — current speed + limit
- `18:21\n197m\nNhà (Park 3...)` — ETA + dist + dest

GMaps by contrast has **text non-empty = 7** (`60 m`, `Trần Trọng Kim`, `3 phút`…). `logEventText` only read `event.text`, which is empty for VietMap → the afternoon drive captured 575 GMaps rows and **0 VietMap/Waze rows**. VietMap is not opaque; we read the wrong attribute.

## Files changed

### 1. NEW `core/src/main/kotlin/com/byd/clusternav/navigation/NavDescJoin.kt` (36 LOC, pure `:core`)
`object NavDescJoin` (L19) with `fun join(descriptions: List<String>): String` (L30):
flatten each newline run (`Regex("\\s*[\\r\\n]+\\s*")`, L24) to a single space, `trim`, drop blanks, keep first occurrence of duplicates in order (`distinct()`), join with `" | "`. Pure/off-Android → unit-tested.

### 2. NEW `core/src/test/kotlin/com/byd/clusternav/navigation/NavDescJoinTest.kt` (61 LOC, 8 tests)
Pins: pipe-join order · newline→space flatten · CRLF/repeated-newline collapse · dedupe first-occurrence · flatten-before-dedupe · blank/whitespace drop · empty→"" · the real 3-desc VietMap sample → `Sau đó (122m) 50m Trần Trọng Kim | 0 km/h 60 | 18:21 197m Nhà (Park 3...)`.

### 3. EDIT `app/src/main/java/com/byd/clusternav/modules/navaccess/NavAccessibilityService.kt` (230 → 294 LOC; +69 / −8)
- **L9** — `import com.byd.clusternav.navigation.NavDescJoin`.
- **L57** — new field `private val lastDescWalkAt = HashMap<String, Long>(8)` (per-package walk throttle clock).
- **L156–186** — `logEventText` rewritten (see below).
- **L189–197** — new `collectContentDescriptions(source: AccessibilityNodeInfo?): String` (null/degrade-safe caller; recycles `source`).
- **L206–216** — new `gatherDescriptions(node, out, depth)` (bounded recursive walk; recycles children).
- **L290** — new const `DESC_WALK_THROTTLE_MS = 150L`.
- Comment accuracy fixes (navPackages L41–46, GMaps-scan path L127–129, `logEventText` KDoc L150–154): the old "empty (SurfaceView) a11y tree" claim is corrected — VietMap/Waze DO expose a content-desc tree; only GMaps lays out the readable distance token `NavScreenScan` parses.

## How the fallback + walk + throttle work

`logEventText` (unchanged front: still gated to `TYPE_ANNOUNCEMENT` / `TYPE_WINDOW_CONTENT_CHANGED`, still verbose-gated by the caller in `onAccessibilityEvent`):

1. Compute `eventText` from `event.text` exactly as before (join + trim).
2. **Branch on `eventText`:**
   - `eventText.isNotEmpty()` → `text = eventText` (GMaps/any text source — **fast-path, no throttle, no walk**).
   - else (VietMap/Waze, empty text) → **throttle check**: if `now - lastDescWalkAt[pkg] < 150ms` → `return` (skip walk on dense redraws). Otherwise stamp `lastDescWalkAt[pkg] = now` and `text = collectContentDescriptions(event.source)`.
3. Shared tail unchanged: `if (text.isEmpty()) return`; per-package collapse `if (lastText[pkg] == text) return`; else store + `NavAccessLog.record(ctx, pkg, NavAccessRow.NO_METERS, "", "", text)`.

`collectContentDescriptions(source)`: `source == null → ""`. Wraps the walk in `runCatching{…}.getOrDefault("")` (degrade-safe — any failure yields empty), then recycles `source` (`runCatching { source.recycle() }`, the obtaining site, mirroring how the GMaps scan recycles `rootInActiveWindow`).

`gatherDescriptions(node, out, depth)`: bounded recursive DFS reusing the existing `MAX_NODES = 250` / `MAX_DEPTH = 40` caps; adds each `contentDescription?.trim()` that is non-empty and `≤ 120` chars (same cap as `collect()`); recycles every obtained child (`runCatching { c.recycle() }`) exactly like `collect()`. The distinct-in-order join + newline flatten is delegated to the pure `NavDescJoin.join` (`:core`, tested).

**Throttle scope:** `lastDescWalkAt` is a plain `HashMap`, read/written only on the a11y (main) callback thread → no lock. It gates the WALK only; the `event.text` fast-path is never throttled.

## Why GMaps is unaffected

- GMaps events always carry `event.text` (evidence: 7 non-empty). So the `eventText.isNotEmpty()` branch is taken → the fallback (throttle + walk) is **never reached** for GMaps. It never touches `lastDescWalkAt` and never walks `event.source`.
- The GMaps on-screen distance **ground-truth path** below `if (pkg !in maps) return` (`rootInActiveWindow` → `scan()` → `NavScreenScan` → `TurnDistanceInterpolator.refine`) is byte-for-byte unchanged.
- The 150ms walk throttle and 200ms scan throttle (`THROTTLE_MS`) are independent knobs; only the new one is added and it lives entirely inside the empty-text branch.

## Test counts

| Suite | Tests | Failures | Errors |
|---|---|---|---|
| `:core:test` | 533 (incl. new `NavDescJoinTest` = 8) | 0 | 0 |
| `:app:testDebugUnitTest` | 392 | 0 | 0 |

Only warnings emitted are the pre-existing `AccessibilityNodeInfo.recycle()` "deprecated in Java" notices (same ones `collect()`/`scan()` already produce; API 33+ no-op, still needed on the car's older API for the minSdk 29 path).

## Constraints honored
- No touch to keystore / local.properties / main. No git commit/push. No APK build.
- `NavAccessibilityService.kt` = 294 LOC (< 500). Pure helper extracted to `:core` **with** a unit test; the `AccessibilityNodeInfo` walk stays Android-bound (no unit test, as specified).
- `NavAccessLog.record(...)` call shape unchanged; `lastText[pkg]` collapse preserved.

## Follow-ups (not in scope, for owner)
- On-car re-test with Nav+HUD verbose on: confirm VietMap/Waze now produce rows in `nav_access_log_*.csv` (expect the 3 desc groups joined with ` | `).
- `NavAccessLog.kt` KDoc still says the non-GMaps sources "draw the map in a SurfaceView, so announcements are their only same-device nav signal" — now partially outdated (content-desc is the richer signal). Left untouched this stage to keep the diff tight; flag for a doc pass.
- Optional: a `:core` parser that splits the joined VietMap desc string into structured turn-dist / road / speed / limit / ETA fields (currently captured raw as telemetry `text`).
