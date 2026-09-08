# Stage 2 Done — Navigation + Widget

## Navigation (T2)
### Files modified/created:
- `core/src/main/kotlin/com/byd/clusternav/navigation/ClusterLaneAdapter.kt` (237 LOC) — dedup by applied state, no stale FutureTask
- `core/src/main/kotlin/com/byd/clusternav/navigation/OutputDeliveryResult.kt` (54 LOC) — NEW typed result contract
- `app/src/main/java/com/byd/clusternav/NavigationHudOwner.kt` (129 LOC) — applied-state dedup
- `app/src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt` (104 LOC) — applied-state dedup
- `core/src/test/kotlin/com/byd/clusternav/navigation/NavigationOutputOwnershipTest.kt` (326 LOC) — 7 new tests

### Contracts:
- OutputDeliveryResult: attemptedAtEpochMs, completedAtEpochMs?, applied, failure?
- OutputFailureReason: TIMEOUT, TRANSPORT_ERROR, HAL_REJECTED, QUEUE_FULL, GENERATION_STALE
- TypedNavigationFrameDelivery: fun interface for typed HAL results
- Dedup: tracks applied state, not enqueued intent
- Generation fencing: stopSession increments generation, clears queue

## Widget (T3)
### Files modified/created:
- `core/src/main/kotlin/com/byd/clusternav/vietmapwidget/VietMapWidgetModels.kt` (95 LOC) — NEW per-provider snapshot model
- `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt` (493 LOC) — REWRITTEN: per-provider independence
- `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetExtraction.kt` (157 LOC) — NEW extraction + off-main hashing
- `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetClearStateMachine.kt` (144 LOC) — NEW retryable clear
- `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetPrefs.kt` (69 LOC) — commit with retry
- `app/src/test/java/.../VietMapWidgetProviderIndependenceTest.kt` (122 LOC) — 5 tests
- `app/src/test/java/.../VietMapWidgetClearStateMachineTest.kt` (104 LOC) — 4 tests
- `app/src/test/java/.../VietMapWidgetGenerationBindingTest.kt` (126 LOC) — 5 tests

### Contracts:
- VietMapProviderSnapshot<T>: slot, values, updatedAtElapsedMs, freshness, reason, generation
- Per-provider: speed and alerts maintain INDEPENDENT freshness/reason/generation
- SpeedSignClearState: ACTIVE → CLEARING → CLEARED | RETRY_PENDING (exponential backoff)
- Generation-bound callbacks: stale callbacks discarded
- Commit with retry: SharedPreferences.commit() checked, retry once on failure

## Test count: 799 + 7 (nav) + 14 (widget) = ~820 expected (pending compilation)
## Note: TypedNavigationFrameDelivery created but not fully wired into worker (target interface for future migration)
