# ClusterNav Autonomous Resume Checkpoint

Owner: **Đăng Khôi · `dangkhoi`**  
Current orchestrator state: `CONTINUE`  
Authorized terminal state: `WAITING_FOR_VEHICLE_TEST`

## Resume contract

Continue Stages/Waves 2–10 off-car under the already approved two-track plan. Preserve the dirty tree and historical APK bytes. Do not reset, clean, switch branches, discard files, install, run vehicle ADB/dadb tests, commit, push, merge, or operate on `main`. Build exactly one final release-variant `vehicle-test` APK only after complete off-car validation and a newly computed exact-source identity.

## Last verified boundary

- `CastAndroidRuntime.kt` exists and compiles; each shell operation owns and closes its DADB session.
- Cast V2 has no `am display move-stack` path.
- Navigation HUD delivery now uses a local road value and cannot touch lane cache symbols.
- Executor thrown mutation/compensation calls are fenced as durable unknown effects; compensation budget survives recreation.
- Activity owns V2 mutation dispatch; Bubble and diagnostics are read-only projections.
- Boot/update lifecycle no longer invokes legacy Cast auto-cast, watchdog mutation, or reconcile.
- A cancelled lifecycle edit left a partial side effect; it was inspected, repaired, and the focused Cast V2 suite passed (`BUILD SUCCESSFUL`).
- RAI MCP setup check was attempted on resume and was blocked by an HTTP 403/Cloudflare response; local steering remains authoritative.

## Immediate next bounded work

1. Add pure lifecycle migration, runtime parser, canonical Android renderer, and diagnostics contract tests.
2. Run focused Cast V2 + Navigation boundary suites.
3. Complete geometry adjustment/app-manager/accessibility/resource contracts and candidate docs.
4. Run full off-car suite, direct partitioned senior review, boundary/static checks, and sensitive-data scan.
5. Compute final exact-source identity, make the one authorized collision-safe test APK, generate vehicle scripts/checklists, set local orchestrator state to `WAITING_FOR_VEHICLE_TEST`, and stop.

## Watchdog semantics

The local macOS launchd watchdog resumes this exact Kiro session only after its process lock is gone or stale. It uses a second atomic run lock to prevent concurrent agents. It deliberately does not kill or inject into a still-running TUI because Kiro documentation does not define a safe external prompt-injection mechanism for an active session.
