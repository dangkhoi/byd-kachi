# Stage: Data-capture enhancements — DONE

> Branch `feat/speed-limit-badge-hal-hud`. Off-car only (no commit/push, no APK build, main untouched).
> Gate `./gradlew :core:test :app:testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL** (green).

Multi-source data-collection: teammates drive with GMaps + VietMap + Waze + WazeMod all navigating; we
capture and **SEPARATE** each source. Three fixes below.

---

## 1) Source-tag + multi-app accessibility capture

GMaps posts rich nav notifications (already captured); VietMap/Waze/WazeMod post **no** nav notifications and
render the map in a SurfaceView (empty a11y tree) — so their ONLY same-device nav signal is accessibility
**voice-guidance** events. We now tag every row by source package and receive events from all four apps.

**New `nav_access` CSV columns** (was `t_ms,screenRead_m,screenRead_road,screenRead_maneuverHint`):

```
t_ms,pkg,screenRead_m,screenRead_road,screenRead_maneuverHint,text
```

- `pkg` (**new**) — `event.packageName`; separates `com.google.android.apps.maps` /
  `vn.vietmap.live` / `com.waze` / `com.chisadin.wazemod` (+ `app.revanced.android.apps.maps`).
- `text` (**new**) — the announced / window-content voice-guidance string (blank for pure GMaps screen-scan
  rows; `screenRead_m` is `-1` for announcement rows).
- Row shape is built by the pure, unit-tested `NavAccessRow` (:core) — `core/.../navigation/NavAccessRow.kt:25`.

**Accessibility package-filter change (file:line):**

- `app/src/main/res/xml/nav_accessibility_config.xml:21` — `android:packageNames` now
  `com.google.android.apps.maps,app.revanced.android.apps.maps,vn.vietmap.live,com.waze,com.chisadin.wazemod`.
- `app/src/main/res/xml/nav_accessibility_config.xml:15` — `android:accessibilityEventTypes` gains
  `typeAnnouncement` (was only `typeWindowContentChanged|typeWindowStateChanged`) so spoken-guidance events
  are actually delivered.
- `app/src/main/java/com/byd/clusternav/modules/navaccess/NavAccessibilityService.kt:45` — `navPackages`
  = GMaps set + `vn.vietmap.live` + `com.waze` + `com.chisadin.wazemod`; `onAccessibilityEvent` now gates on
  `navPackages` (not `maps`).
- `NavAccessibilityService.kt:142` — new `logEventText(...)` records `TYPE_ANNOUNCEMENT` +
  `TYPE_WINDOW_CONTENT_CHANGED` text for ALL nav packages, source-tagged, with per-package
  consecutive-duplicate collapse (kills the window-content redraw flood). `@Suppress("DEPRECATION")` is
  documented: `TYPE_ANNOUNCEMENT` was deprecated in API 36 only for *senders*; a *receiving* service has no
  replacement.

**Preserved:** GMaps distance ground-truth path is unchanged — the screen scan + `NavScreenScan` +
`TurnDistanceInterpolator.refine()` still run only for GMaps (`pkg !in maps → return` after capture), and the
GMaps row still carries `screenRead_m`/road/hint. All capture is verbose-gated (`NavLog.verbose`, default OFF),
off-thread (`NavAccessLog` single-thread daemon executor), and the capture call is `runCatching`-wrapped.

## 2) VietMap alert extraction fix (place_holder)

VietMap 3.3.2 has **no** `warning_speed_limit_widget_text_view` / `warning_speed_distance_text_view` views
(proven: 2851 on-car view-dumps never contained them). The real per-alert text slot is `place_holder_textView`
(+ `second_place_holder_textView`).

- `core/.../vietmapwidget/VietMapWidgetTextParser.kt:17-18` — added constants `PLACE_HOLDER =
  "place_holder_textView"`, `SECOND_PLACE_HOLDER = "second_place_holder_textView"`. Removed the four dead
  `warning_speed_*` constants (only referenced here + in the extractor + one test, all updated).
  `alertsRequired` now uses the place-holder names; `supportsAlertsShape` follows automatically.
- `app/.../vietmapwidget/VietMapWidgetExtraction.kt` `extractAlerts()` — reads `PLACE_HOLDER` /
  `SECOND_PLACE_HOLDER` into the alert value/distance-text fields (speed-limit-text set to null; VietMap 3.3.2
  has no dedicated slot). **Icon capture unchanged** (`warning_alert_image` / `second_warning_alert_image` via
  `FIRST_ALERT_IMAGE` / `SECOND_ALERT_IMAGE`), still the stable anchor.
- `'--'` = no active alert value → treated as **null** (already handled by the parser's sentinel set; the
  alert is dropped unless its icon is visible). Verified by new tests.

## 3) Screenshot index fix (SegmentShotCapturer)

On-car proof: fission display ids are OPPOSITE Android's. Fixed labeling in
`app/src/main/java/com/byd/clusternav/SegmentShotCapturer.kt:68-72`:

- `fission_screencap -d 0` → `seg-<n>-<ts>-cluster.png`  (CLUSTER composite, OEM + our badge)
- `fission_screencap -d 1` → `seg-<n>-<ts>-main.png`     (MAIN head-unit, foreground nav app)
- `screencap -d 1`         → `seg-<n>-<ts>-cluster-overlay.png` (Android cast surface, unchanged)

Each capture keeps its own `runCatching` (degrade-safe, independent), still verbose-gated + off the io thread.
> Supersedes the (now-wrong) labeling described in `docs/_handoff/stage-waze-b2-done.md`.

---

## Files changed

| File | Change |
|------|--------|
| `core/src/main/kotlin/com/byd/clusternav/navigation/NavAccessRow.kt` | **NEW** — pure nav_access CSV row builder (pkg + text columns) |
| `core/src/test/kotlin/com/byd/clusternav/navigation/NavAccessRowTest.kt` | **NEW** — 5 tests: header/row shape, source separation, quoting |
| `app/src/main/java/com/byd/clusternav/NavAccessLog.kt` | `record(pkg, …, text)`; HEADER delegates to `NavAccessRow` |
| `app/src/main/java/com/byd/clusternav/modules/navaccess/NavAccessibilityService.kt` | `navPackages`; `logEventText` announcement/window-content capture; `scan(pkg)`; source-tagged record |
| `app/src/main/res/xml/nav_accessibility_config.xml` | `packageNames` += 3 apps; `accessibilityEventTypes` += `typeAnnouncement` |
| `core/src/main/kotlin/com/byd/clusternav/vietmapwidget/VietMapWidgetTextParser.kt` | `PLACE_HOLDER`/`SECOND_PLACE_HOLDER` constants; `alertsRequired` updated; old `warning_speed_*` removed |
| `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetExtraction.kt` | `extractAlerts` reads place_holder views (icon capture unchanged) |
| `core/src/test/kotlin/com/byd/clusternav/vietmapwidget/VietMapWidgetTextParserTest.kt` | shape test uses `SECOND_PLACE_HOLDER`; +2 tests (place_holder parse, `'--'` null) |
| `app/src/main/java/com/byd/clusternav/SegmentShotCapturer.kt` | fission `-d 0`=cluster, `-d 1`=main; overlay unchanged |

All changed source files ≤ 500 LOC (max 230).

## Verification

- `./gradlew :core:test :app:testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL**.
- Forced re-run + JUnit XML: `NavAccessRowTest` 5/5, `VietMapWidgetTextParserTest` 8/8,
  `VietMapWidgetProviderIndependenceTest` 6/6 — 0 failures / 0 errors.
- No new compiler warnings from the changed code (the two remaining `recycle()` warnings in
  `NavAccessibilityService` are pre-existing / out of scope).

## Notes for on-car validation

- The exact meaning of the VietMap place-holder value (distance vs. speed-limit vs. label) is preserved as
  raw text in the `text`/distance field for off-car analysis; confirm the field's semantics from a live dump.
- Confirm VietMap/Waze/WazeMod actually emit `TYPE_ANNOUNCEMENT` on this head unit (some ROMs gate it); the
  `text` column + `pkg` will show which sources produce rows.
