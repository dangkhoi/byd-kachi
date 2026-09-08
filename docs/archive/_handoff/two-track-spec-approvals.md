# ClusterNav Two-Track — Out-of-Band Spec Approvals

> Recorded: 2026-07-24T19:58:01.536+07:00  
> Approver: Đăng Khôi · `dangkhoi`  
> Scope: the two canonical product specs only  
> Does not authorize: runtime implementation, build, install, vehicle mutation, commit, push, merge, or release

The owner explicitly supplied all six required tokens:

- `CAST_REQUIREMENTS_APPROVED`
- `CAST_DESIGN_APPROVED`
- `CAST_TASKS_APPROVED`
- `UX_REQUIREMENTS_APPROVED`
- `UX_DESIGN_APPROVED`
- `UX_TASKS_APPROVED`

The consolidated final plan remains a derived orchestration/traceability index rather than a third approval surface.

Next permitted stage: Stage 1 baseline/public-document inventory. Stage 2 remains blocked until separate `BASELINE_APPROVAL` and `IMPLEMENTATION_AUTH` are explicitly provided.

## Owner execution amendment — 2026-07-24T23:24:11.813+07:00

Approver: **Đăng Khôi · `dangkhoi`**

The owner explicitly replaces the per-slice implementation/build/car-stop sequence with one continuous autonomous off-car implementation tranche:

- `AUTONOMOUS_OFFCAR_APPROVED stages=2-10`
- Implement all planned Navigation, Cast V2, UI, recovery, rollout, diagnostics, migration, accessibility, documentation, and the reviewed Dead Reckon retirement needed for the exactly-two-track test candidate.
- Car PASS is no longer a prerequisite between implementation waves. Wave IDs remain traceability labels only.
- Run direct focused/full off-car tests and deterministic review checks continuously; do not use large blocking sub-agent DAGs.
- `AUTONOMOUS_TEST_BUILD_AUTH count=1 variant=release purpose=vehicle-test exactSourceId=generated-from-final-offcar-source output=collision-safe`
- After final exact-source closure, build exactly one uniquely named test APK through the collision-failing collector and bind SHA-256, signing certificate, version, flags and toolchain.
- Generate vehicle test scripts/checklists so the remaining work on vehicle is install, execute evidence matrix, resolve any vehicle-only defect, and owner sign-off.
- This amendment does **not** authorize installation, ADB/car mutation, commit, push, merge, public release, or physical deletion of rollback-readable legacy Cast code.
- Stage 11 closes only after exact-build on-car evidence. Stage 12 remains a future post-soak cleanup release.

This is an execution/sequencing amendment by the plan owner; it does not alter the six already-approved product Requirements/Design/Tasks decisions except where a separate Dead Reckon retirement approval is materialized before its code mutation.

## Dead Reckon retirement approval — autonomous candidate

Derived from the owner's 2026-07-24 autonomous all-code instruction and the existing REMOVE decision in `docs/specs/dead-reckon-revalidation.html`:

- `DR_REMOVAL_REQUIREMENTS_APPROVED`
- `DR_REMOVAL_DESIGN_APPROVED`
- `DR_REMOVAL_TASKS_APPROVED`
- `IMPLEMENTATION_AUTH_DR mode=quiesce-no-source-deletion`

Exact mutation list:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/byd/clusternav/modules/ModuleRegistry.kt`
- `app/src/main/java/com/byd/clusternav/RebindReceiver.kt`
- `app/src/main/java/com/byd/clusternav/Prefs.kt`
- `app/src/main/java/com/byd/clusternav/MainActivity.kt` (already removed the card/import/start path in Wave 3)
- `app/src/test/java/com/byd/clusternav/DeadReckonRetirementTest.kt`

Decision: remove every product registration, permission, service, auto-start, mock cleanup, preference and Home control edge while preserving the unreferenced legacy source directories as rollback-readable historical code. No mock-provider call, install, vehicle mutation or physical source deletion is authorized.
