# Cluster Cast Simplified — Execution Prompt (Orchestrator)

> Auto-generated from plan: docs/specs/cluster-cast-simplified.html
> Stages: 2 | Total deliverables: 9 | Variant A (Kiro CLI sub-agent)

## TASK
Complete Stage 2 wiring: connect SimpleCastCoordinator to FloatingBubbleService and MainActivityCastController. Rebuild APK. All off-car scope from spec.

## WORKING DIR
`<project-root>`

## CONTEXT
- Stage 1 DONE: core/src/main/kotlin/.../simplified/ has 5 files (models, projection, configurator, mover, coordinator). 18 tests pass.
- SimpleCastRuntime.kt exists in app module — process singleton, compiles.
- Projection auto-open already wired in MainActivityCastController.onCreate().
- versionCode=90, APK builds successfully.
- FloatingBubbleService.onPrimaryTap() currently dispatches to CastFacade. Must redirect to SimpleCastCoordinator.
- JAVA_HOME=/opt/homebrew/opt/openjdk@17

## CONSTRAINTS
- execution-reliability.md: each sub-agent ≤5 files, ≤1 boundary, ≤90s expected
- Files ≤500 LOC
- CP/AA = full only, any tap = stop. Normal = split capable.
- 1 config per app (Option A)
- Do NOT delete existing V2 code — add parallel path

## REMAINING DELIVERABLES
1. Wire FloatingBubbleService.onPrimaryTap → SimpleCastCoordinator (cast foreground / stop)
2. Wire FloatingBubbleService.dispatchHalfZone → SimpleCastCoordinator (split slots)
3. Wire MainActivityCastController refresh → SimpleCastState for status display
4. Hide resize controls when casting CP/AA (appType.isProtected)
5. Wire closeProjection on app exit (onDestroy)
6. Rebuild + verify APK
