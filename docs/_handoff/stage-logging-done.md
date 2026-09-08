# Stage "logging" — done (Part C: log ALL VietMap signals)

> Spec: `docs/specs/speed-badge-placement-vietmap-logging.html` §4.4 (C1–C4)
> Branch: `feat/speed-limit-badge-hal-hud` · Date: 2026-08-17
> Goal: capture EVERYTHING VietMap emits (known + unknown fields + alert icon images) for tomorrow's
> drive, verbose-gated + degrade-safe, so signals can be evaluated off-car.

## Status: DONE ✅

Both gate suites pass a **real** off-car execution (see Verification):
- `:core:test` — BUILD SUCCESSFUL (full suite)
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (full suite, `--no-build-cache`, task actually executed)

## Files created / modified (this stage only — all in `app/.../vietmapwidget/`)

| File | Type | LOC | Purpose |
|------|------|-----|---------|
| `VietMapSignalLog.kt` | **new** | 172 | CSV sink. Pure `buildRow`/`buildViewsRow` + off-main `log`/`logViews`. Writes `vietmap_signal_*.csv` + `vietmap_views_*.csv`. Mirrors `NavNotifLog`. |
| `VietMapWidgetVerboseLog.kt` | **new** | 84 | Bridge-side orchestrator. 3 self-gated (`NavLog.verbose`) + `runCatching` functions wiring extraction → sinks. Split out so the bridge stays ≤500 LOC. |
| `VietMapWidgetExtraction.kt` | modified | 226 | Added `dumpAllViews`, `saveAlertImagePng`, `alertImageViews`; refactored `capturePixels` → `capturePixelsSized` (shared by hash + PNG). |
| `VietMapWidgetBridge.kt` | modified | 500 | 3 thin verbose hook call-sites (see below). No behavior change to the nav/widget pipeline. |
| `VietMapSignalLogTest.kt` (test) | **new** | 108 | 7 JUnit5 cases: header order/count, row shape, nulls→empty, RFC-4180 escaping, views-row join. |

All files ≤ 500 LOC. **Not touched:** keystore, `local.properties`, `main`. **Not run:** git commit/push, APK build.

## CSV columns & artifacts (all under `getExternalFilesDir(null)` = `/sdcard/Android/data/com.byd.clusternav2/files/`)

**`vietmap_signal_<startTs>.csv`** — 1 row per DISTINCT published snapshot (C1):
```
ts,freshness,providerVersion,currentSpeedKph,speedLimitKph,a1Limit,a1Dist,a1ImgVisible,a1ImgHash,a2Limit,a2Dist,a2ImgVisible,a2ImgHash
```
- `a1*`/`a2*` = first/second alert, order **preserved** by reading `combinedRaw.first/second*` (NOT the published `alerts` list, which is `filterNotNull()`-collapsed and would mis-order a1↔a2).
- Alert limit texts parsed to Int via `VietMapWidgetTextParser.parseSpeedLimit`; distance kept as raw text; image hashes are the SHA-256 the bridge already computes.

**`vietmap_views_<startTs>.csv`** — 1 row per host-view update (C2), captures fields we DON'T parse:
```
ts,dump
```
- `dump` = every `TextView` → `TV:<resEntryNameOrHex>=<text>` and every `ImageView` → `IV:<resEntryNameOrHex>=visible|gone`, joined by ` | ` into one RFC-4180 field. Names resolved via VietMap's `remoteResources.getResourceEntryName(id)`; fallback `0x%08x` hex.

**`diag/vietmap-alert-<hash>.png`** — the alert icon bitmap (C3), written **once per unique hash** (skip if exists), so off-car we can eyeball camera/police/… icons and map hash→type.

## Hook locations (`VietMapWidgetBridge.kt`, final line numbers)

| Line | Call | When | Thread |
|------|------|------|--------|
| `VietMapWidgetBridge.kt:243` | `VietMapWidgetVerboseLog.logAlertIcons(appContext, extraction, view, current, h1, h2)` | inside the alert **hash-merge** callback (`main.post`), after hashes known | main (drawable capture) → encode off `hashExecutor` |
| `VietMapWidgetBridge.kt:259` | `VietMapWidgetVerboseLog.logHostViewTree(appContext, extraction, view)` | end of `onHostViewUpdated`, after `schedulePublish()` (both slots) | main (tree walk) → CSV write off single-thread daemon |
| `VietMapWidgetBridge.kt:322` | `VietMapWidgetVerboseLog.logPublishedSnapshot(appContext, combinedFreshness, next, combinedRaw)` | end of `publishSnapshot`, after `dispatchToListeners` (only when snapshot changed) | main → CSV write off single-thread daemon |

## How dump + PNG are gated (degrade-safe)

- **Verbose gate:** every `VietMapWidgetVerboseLog` function first-lines `if (!NavLog.verbose) return`, so no tree walk / pixel capture / file IO happens unless `NavLog.verbose` (persisted `Prefs.navVerboseLog` OR `BuildConfig.DEBUG`). Bridge call-sites are unconditional 1-liners → the gate lives in one place.
- **Degrade-safe:** each orchestrator function wraps its body in `runCatching`; `VietMapSignalLog.log/logViews` also `runCatching` each append + wrap `io.execute` in `runCatching`. Any failure is swallowed per row — logging can never affect navigation or the widget pipeline.
- **Off the widget/main thread:** CSV opens/appends/flush run on a single-thread daemon executor (`vietmapsignallog`), lazily created only when verbose fires (mirrors `NavNotifLog`). PNG encode+write run on the existing `hashExecutor`. Only the unavoidable view-tree walk and drawable→pixel capture run on main (view access requires it) and are cheap (≤256px icons, ≈dozens of views).

## Verification (off-car, real executions — concurrency caveat below)

- `VietMapSignalLogTest` (7 cases) GREEN in isolation.
- vietmapwidget app package GREEN (no regression from bridge/extraction edits).
- **Full `:core:test`** BUILD SUCCESSFUL (real exec, ~1m).
- **Full `:app:testDebugUnitTest`** BUILD SUCCESSFUL (real exec, `--no-build-cache`, ~1m4s).

⚠️ **Concurrency caveat for the next runner:** this is a **shared worktree** with parallel Part A/B agents building simultaneously. Concurrent Gradle/Kotlin daemons corrupt the shared `build/test-results/` binaries → intermittent `java.io.EOFException` / `NoSuchFileException: in-progress-results-generic.bin` / Kryo `Buffer underflow`, and occasional transient `:core:compileKotlin` failures while core source is mid-edit. These are **NOT real test failures** — proven by: single-class/package runs pass instantly, and full suites pass when a low-contention window opens. Run with `./gradlew --no-daemon` and, if you hit the infra signature, `rm -rf {core,app}/build/test-results` and retry until a clean window.

## Contracts confirmed

- `VietMapSignalLog.HEADER` = the 13 signal columns (unit-tested).
- `VietMapSignalLog.VIEWS_HEADER` = `ts,dump`.
- Uses `com.byd.clusternav.core.CsvEscape` (RFC-4180) for every field.
- `NavLog.verbose` is the single gate (already DEBUG-auto-on).
