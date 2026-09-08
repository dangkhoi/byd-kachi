# Stage 2 — T3 Widget Provider Independence — DONE

**Date:** 2026-08-05 10:50 +07

## Files modified

- `core/src/main/kotlin/com/byd/clusternav/vietmapwidget/VietMapWidgetModels.kt` — added per-slot freshness fields to `VietMapWidgetSnapshot` (speedFreshness, alertsFreshness, speedUpdatedAtElapsedMs, alertsUpdatedAtElapsedMs)
- `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt` — `publishSnapshot()` now computes freshness independently per slot; stale speed nullifies speed data while alerts remain fresh (and vice versa)
- `app/src/main/java/com/byd/clusternav/NavNotificationListener.kt` — speed limit pusher uses `speedFreshness` (per-slot) instead of combined `freshness`
- `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt` — BIND_UI_UNAVAILABLE now shows explicit grant instruction with adb command

## Deliverables status

- D5 ✅ Speed and alerts freshness tracked independently via per-slot fields
- D6 ✅ Orphan ID reconciliation already exists in `restoreBoundViews()` (deletes IDs with mismatched/null providers)
- D7 ✅ Speed-sign consumer: stale speed → pushSpeedLimit(null) → NavigationSpeedSignOwner clears
- D8 ✅ BIND_UI_UNAVAILABLE shows exact `adb shell appwidget grantbind` instruction

## Compile result

`:app:compileDebugKotlin` — BUILD SUCCESSFUL (500 LOC bridge at limit)
