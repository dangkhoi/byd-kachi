# Stage 3 Done — Cast Core + Cast UI

## Cast Core (T4)
### Files created:
- `core/.../simplified/CastMutationOutcome.kt` (57 LOC) — sealed result type
- `core/.../simplified/BoundedCastExecutor.kt` (103 LOC) — bounded queue + timeout + priority stop
- `core/.../simplified/CastPostconditionVerifier.kt` (124 LOC) — verify task presence/absence
- `core/.../simplified/CastDisplayCleaner.kt` (66 LOC) — extracted display cleanup
- `core/src/test/.../CastSafetyTest.kt` (309 LOC) — 18 new tests

### Files modified:
- `SimpleCastCoordinator.kt` (495 LOC) — uses BoundedCastExecutor, postcondition verification, precondition validation
- `SimpleCastModels.kt` (222 LOC) — added CastSlotValidator (CP/AA rejection, occupancy check)
- `CastStackParser.kt` (186 LOC) — typed TaskLookupResult, escaped regex, ambiguity detection
- `CastDensityControl.kt` (48 LOC) — persist only on shell success
- `SimpleCastCoordinatorTest.kt` (397 LOC) — updated for new safety model

### Cast operation contract:
- CastMutationOutcome: Verified / Rejected / Unknown / TimedOut
- CastRejectReason: PROTECTED_FULLSCREEN_STACK_UNPROVEN, AMBIGUOUS_TASK, SLOT_OCCUPIED, INVALID_PACKAGE, INVALID_BOUNDS, DISPLAY_UNAVAILABLE, TRANSPORT_CLOSED
- CP/AA: rejected from CastSlot; fullscreen stack proof required for CastFull
- Stop: priority/preemptive via submitStop()

## Cast UI (T5)
### Files created:
- `BubbleRenderer.kt` (187 LOC) — zones, colors, content descriptions
- `BubbleGestureHandler.kt` (144 LOC) — touch, drag, tap token
- `CastAutostart.kt` (195 LOC) — autostart logic
- `CastGeometryEditor.kt` (180 LOC) — display-aware resize, global DPI
- `CastUILifecycleSafetyTest.kt` (267 LOC) — 14 tests

### Files modified:
- `FloatingBubbleService.kt` (253 LOC, was 591) — service lifecycle only
- `MainActivityCastController.kt` (262 LOC, was 533) — UI binding only
- `BubbleActionDispatcher.kt` (108 LOC) — no raw threads
- `DiagActivity.kt` (116 LOC) — V2 types removed, reads SimpleCastRuntime

### UI fixes:
- Touch zones: 56dp (was 30dp, exceeds 48dp automotive guideline)
- Disabled zones: non-destructive (tap = no-op)
- Tap token: AtomicBoolean gate, duplicate taps rejected
- Listeners: named fields, removed on onDestroy
- Delayed work: cancelled on lifecycle end
- Split DPI: one display-global control
- V2 removed from DiagActivity

## Test count: ~820 + 18 (cast safety) + 14 (UI lifecycle) = ~852 expected
