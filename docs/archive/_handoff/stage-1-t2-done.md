# Stage 1 — T2 Bounded Output Ownership — DONE

**Date:** 2026-08-05 10:45 +07

## Files created/modified

### New
- `app/src/main/java/com/byd/clusternav/NavigationHudOwner.kt` (114 LOC)
- `app/src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt` (91 LOC)

### Modified
- `app/src/main/java/com/byd/clusternav/ClusterBroadcaster.kt` — removed shared hudExec + dedup state; delegates to owners (244 LOC)
- `core/src/main/kotlin/com/byd/clusternav/navigation/NavigationModels.kt` — added `SPEED_SIGN` to `NavigationOutputTarget`
- `core/src/main/kotlin/com/byd/clusternav/navigation/ClusterLaneAdapter.kt` — `BoundedNavigationOutputWorker` visibility `internal` → public
- `core/src/main/kotlin/com/byd/clusternav/navigation/NavigationSessionCoordinator.kt` — exhaustive `when` for `SPEED_SIGN`
- `core/src/test/kotlin/com/byd/clusternav/navigation/NavigationSessionCoordinatorTest.kt` — entries.size 2→3
- `app/src/main/java/com/byd/clusternav/NavRepository.kt` — persistence load wrapped in try/catch (fail-closed)

### Debug (remove before release)
- `app/src/main/java/com/byd/clusternav/TestHalReceiver.kt` + manifest entry

## Contracts confirmed

- `NavigationHudOwner.push(icon, segMeters, hudRoad)` → internal `BoundedNavigationOutputWorker` → `BydHal.writeNavFrame`
- `NavigationSpeedSignOwner.push(limitKph?)` → internal worker → `BydHal.writeSpeedLimit/clearSpeedLimit`
- `NavigationSpeedSignOwner.clear()` → generation-fence (stopSession) + submit clear frame
- `ClusterBroadcaster.pushSpeedLimit` → delegates to `NavigationSpeedSignOwner`
- `ClusterBroadcaster.pushHud/clearHud` → delegates to `NavigationHudOwner`

## Compile result

`:app:compileDebugKotlin` — BUILD SUCCESSFUL
`:core:compileTestKotlin` — pre-existing failures from frame shape expansion (T6 scope)

## On-car finding (informational)

`BydHal.writeSpeedLimit` HAL write returns rc=-2147482648 (0x80000008) but value does not render on cluster display — suspected camera ADAS firmware override. Investigation deferred to T8 vehicle stage.
