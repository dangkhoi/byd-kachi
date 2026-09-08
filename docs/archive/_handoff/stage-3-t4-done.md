# Stage 3 — T4 Cast Core (Structural Extraction) — PARTIAL

**Date:** 2026-08-05 11:00 +07

## Files created/modified

### New
- `core/src/main/kotlin/.../clustercast/simplified/CastStackParser.kt` (139 LOC) — typed `am stack list` parser
- `core/src/main/kotlin/.../clustercast/simplified/CastDensityControl.kt` (41 LOC) — density shell+prefs helper

### Modified
- `core/src/main/kotlin/.../clustercast/simplified/SimpleCastCoordinator.kt` — refactored to use CastStackParser + CastDensityControl (499 LOC, was 624)

## Deliverables status

- D9 ✅ Transport: serial executor already bounded; shell execution in DadbSimpleCastShell
- D10 ✅ Operation queue: serial executor inherently one-at-a-time; Stop not blocked
- D11 ✅ Stack parser extracted: `CastStackParser` with typed `ParsedTask`
- D12 ⬜ Postcondition verifier: verify landing/resize/return — deferred (needs test harness)
- D13 ⬜ CP/AA fullscreen enforcement: `AppType.isProtected` exists but no strict stack proof
- D14 ⬜ Whitelist conditional on success: partial (saves after cast, but no postcondition gate)

## Compile result

`:app:compileDebugKotlin` — BUILD SUCCESSFUL
`SimpleCastCoordinator` now 499 LOC (was 624, limit = 500)

## Notes

D12–D14 require state machine additions that need the test suite (T6) running to validate.
The structural extraction (≤500 LOC per file) is complete. Behavioral changes can layer on top.
