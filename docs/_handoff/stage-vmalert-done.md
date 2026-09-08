# Stage vmalert — done

> Capture VietMap's **upcoming/enforced speed-limit change ahead + distance** by binding
> `VMAlertWidgetProvider` (the full-alert widget). Also fixes the long-standing
> `warning_speed_*` "always null" bug — we were reading those views off the wrong widget
> (the sticky provider, which does not have them).
>
> Branch: `feat/speed-limit-badge-hal-hud` · WT: `byd-cluster-2-wt-speed-limit-badge-hal-hud`
> Not committed / not pushed / no APK built (per constraints). `main` untouched.

## Slot/provider change + why

The `ALERTS` slot maps to `vn.vietmap.live.homewidget.VMOnlyStickyAlertWidgetProvider`
(confirmed in `VietMapWidgetSlotExt.kt`). On-car that widget only ever exposed
`warning_alert_image` + `place_holder_textView`='--' (no numeric upcoming limit/distance),
because the sticky provider does not contain the `warning_speed_*` views.

**Decision: ADD a new slot `ALERT_FULL → VMAlertWidgetProvider`** (the least-risk option per
task guidance "if unsure, ADD a new slot so nothing existing breaks"). Rationale:

- The existing sticky-alert capture (cấm-dừng/đỗ icon hashing via `warning_alert_image` +
  `place_holder_textView`) is **completely untouched** — `ALERTS` still binds the sticky provider.
- `ALERT_FULL` has its **own independent freshness/generation** (per-provider model), so an
  unavailable/idle full-alert widget can **never** drag down the speed or sticky-alert slots.
  Verified by unit test `composeSnapshot never lets an unavailable full-alert mask the working
  speed slot`.
- The legacy combined `VietMapWidgetSnapshot.freshness` / `updatedAtElapsedMs` stay defined by
  **speed + sticky-alerts only** (backward-compat); `ALERT_FULL` projects purely into the new
  additive `upcoming*` fields under `alertFullFreshness`.

## New snapshot fields (additive, all defaulted)

`VietMapWidgetSnapshot` (core `VietMapWidgetModels.kt`):

| field | type | meaning |
|-------|------|---------|
| `upcomingLimitKph` | `Int?` | enforced speed-limit for the camera/zone ahead (km/h) |
| `upcomingDistanceMeters` | `Int?` | distance to that limit in metres (derived) |
| `upcomingDistanceText` | `String?` | raw distance text as shown ("300 m" / "1,2 km") |
| `secondUpcomingLimitKph` | `Int?` | second queued upcoming limit |
| `secondUpcomingDistanceMeters` | `Int?` | second queued distance in metres |
| `secondUpcomingDistanceText` | `String?` | second queued raw distance text |
| `alertFullFreshness` | `VietMapWidgetFreshness` | ALERT_FULL provider freshness (independent) |
| `alertFullUpdatedAtElapsedMs` | `Long?` | last ALERT_FULL update (elapsedRealtime) |

`VietMapWidgetRawValues` gains raw text carriers: `upcomingSpeedLimitText`,
`upcomingDistanceText`, `secondUpcomingSpeedLimitText`, `secondUpcomingDistanceText`.

New pure DTOs in core: `VietMapProviderState`, `VietMapComposedSnapshot`,
`VietMapUpcomingSpeedLimit`.

## New CSV columns

`vietmap_signal_<ts>.csv` header went from 13 → **17** columns (4 appended, order preserved):

```
...,a2ImgVisible,a2ImgHash,upLimit,upDist,up2Limit,up2Dist
```

`upLimit`/`up2Limit` = parsed Int km/h; `upDist`/`up2Dist` = raw distance text (RFC-4180 escaped).
Still gated on `NavLog.verbose`, written off-thread on the `vietmapsignallog` daemon executor,
degrade-safe per row. `vietmap_views_*.csv` (raw dump) already captures the `warning_speed_*`
views for free (unchanged).

## Data flow (E2E trace, verified by unit test)

VMAlertWidgetProvider posts RemoteViews `warning_speed_limit_widget_text_view="60"`,
`warning_speed_distance_text_view="300 m"` →
`onHostViewUpdated(ALERT_FULL)` → `extractAlertFull` → `VietMapWidgetRawValues(upcomingSpeedLimitText="60", upcomingDistanceText="300 m")` →
`alertFullSnapshot` (updatedAt=now) → `publishSnapshot` (freshness=FRESH) →
`VietMapWidgetTextParser.composeSnapshot` → `parseUpcomingSpeedLimit("60","300 m")` = (60, 300, "300 m") →
snapshot `upcomingLimitKph=60, upcomingDistanceMeters=300, upcomingDistanceText="300 m", alertFullFreshness=FRESH` →
DiagActivity shows `Upcoming: 60 km/h @ 300 m (FRESH)`; CSV row `...,60,300 m,,`.

## Files changed (file:line anchors)

Core (`:core`):
- `core/.../vietmapwidget/VietMapWidgetModels.kt:11` — add `ALERT_FULL` slot; `:34-56` upcoming
  snapshot fields; `:76-80` upcoming raw fields; `:83-113` `VietMapProviderState` /
  `VietMapComposedSnapshot` / `VietMapUpcomingSpeedLimit`.
- `core/.../vietmapwidget/VietMapWidgetTextParser.kt:26-40` — `WARNING_SPEED_*` view names +
  `alertFullRequired`; `:80-81` `supportsAlertFullShape`; `:150-163` `parseUpcomingSpeedLimit`;
  `:165-231` pure `composeSnapshot` + `worst()` (moved the combination logic out of the bridge).

App (`:app`):
- `app/.../vietmapwidget/VietMapWidgetSlotExt.kt:16-17` — `ALERT_FULL → VMAlertWidgetProvider`.
- `app/.../vietmapwidget/VietMapWidgetExtraction.kt:99-119` — `extractAlertFull()`.
- `app/.../vietmapwidget/VietMapWidgetBridge.kt:47-51` — `alertFullSnapshot`; `:261-273` ALERT_FULL
  branch; `:284-311` refactored `publishSnapshot` delegating to `composeSnapshot`; `:313-314`
  `providerState`; `clearRuntimeValues` resets alertFull. (499 LOC — under 500.)
- `app/.../vietmapwidget/VietMapWidgetPrefs.kt:31-37` — `clearAll` iterates all slots.
- `app/.../vietmapwidget/VietMapSignalLog.kt:29-32` HEADER; `:66-105` `buildRow`; `:120-152` `log`.
- `app/.../vietmapwidget/VietMapWidgetVerboseLog.kt:72-76` — pass upcoming into CSV `log`.
- `app/.../vietmapwidget/VietMapWidgetDiagActivity.kt` — count-neutral copy (3 slots now) +
  render `Upcoming`/`Upcoming 2` lines for on-car verification.

## Tests

Gate (JAVA_HOME=openjdk@17): `./gradlew :core:test :app:testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL**.
- `:core:test` — **525** tests, 0 failures, 0 errors.
- `:app:testDebugUnitTest` — **388** tests, 0 failures, 0 errors.

New/updated tests:
- `VietMapWidgetTextParserTest` 8 → **13** (+5): full-alert view names/shape;
  `parseUpcomingSpeedLimit` (limit int + m/km + sentinel collapse + out-of-range/unknown-unit);
  `composeSnapshot` upcoming field mapping + combined-updatedAt ignores full-alert + combinedRaw
  preserves sticky capture; unavailable full-alert never masks speed; stale full-alert hides upcoming.
- `VietMapSignalLogTest` 6 → **7** (+1) and updated the 13→17 column assertions (header, buildRow,
  null-render, RFC-4180 escape, new "upcoming columns default to empty").

## Degrade-safety / constraints honored

- VMTPMS never bound. If `VMAlertWidgetProvider` is missing → `PROVIDER_MISSING` (skipped, no crash);
  if the view tree lacks the anchor views → `extractAlertFull` returns null → `UNSUPPORTED_SHAPE`
  isolated to the ALERT_FULL slot. No effect on speed / sticky-alerts.
- Files ≤ 500 LOC (bridge 499). No touch to keystore.properties / release.keystore / local.properties.
- No git commit/push, no APK build, `main` untouched.

## On-car follow-up (next drive)

- Confirm `VMAlertWidgetProvider` binds and `warning_speed_limit_widget_text_view` /
  `warning_speed_distance_text_view` populate (Diag screen "Upcoming" line + `vietmap_signal_*.csv`
  `upLimit/upDist`). The `vietmap_views_*.csv` dump will show whether the widget keeps posting
  updates while idle (affects whether ALERT_FULL stays FRESH between alerts).
