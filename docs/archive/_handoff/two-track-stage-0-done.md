# ClusterNav Two-Track Re-baseline — Stage 0 Handoff

> State: **WAITING_FOR_SPEC_APPROVAL**  
> Stage result: **DOCUMENT CLOSURE PASS**  
> Runtime/build/install/commit/push/merge: **NOT AUTHORIZED**  
> Owner identity: **Đăng Khôi · `dangkhoi`**

## Scope completed
Stage 0 executed as documentation/review only. Two independent senior lenses and synthesis identified document findings; accepted findings were patched; focused review loops continued until an independent reviewer returned **PASS · 0 actionable P0–P3**. No runtime, test, build, APK, vehicle, branch, commit, push or merge mutation was performed.

## Source snapshot (provisional; not BASELINE_APPROVAL)
- Branch: `release/v0.60-cast-hardening`
- HEAD: `fd4890c1ffabf4b8cb37f5ccbd5cdb93f0343ae6`
- Current full tracked-diff SHA-256: `1527d2c68c35f4a7b835c33e8181f7c264dce62fa0d4fd4f9c8cc9b2c6ecb781`
- The protected runtime status set is identical to the Stage 0 entry capture. Stage 0 modified only untracked documentation artifacts listed below.
- This is not an approved `EXACT_SOURCE` manifest. Stage 1 must independently hash/classify all tracked and intended-untracked inputs and obtain `BASELINE_APPROVAL`.

## Stage 0 documentation allowlist and final hashes
- `docs/specs/cluster-cast-rebaseline.html` — SHA-256 `b5b3a30ff7295315e65af4d679a99a22eb6615919de3ab6200ada9f414e93001`
- `docs/specs/clusternav-uxui-rebaseline.html` — SHA-256 `6599d5a5370027ad84aeba8be1460b8c5fdb1259ec01a34822781d45aabdc28f`
- `docs/specs/clusternav-two-track-final-plan.html` — SHA-256 `3dbac11cf48f5db39f64890582a28999b077e8f601849219af7a053530886f57`
- `docs/specs/dead-reckon-revalidation.html` — SHA-256 `b2cbb08ddac24e4eb3b9922a4ba9e780e6317b7f3e0af67093dc9f9a2f8dd775`
- `docs/specs/cast-ui-state-v2.schema.json` — SHA-256 `38104e35c9b8072c770a3417f72bd09c36396f7e2958cd8c921674d6117063a8`
- `docs/_handoff/EXECUTE-two-track-rebaseline.md` — SHA-256 `07b5a04eb17612dff9747335495be419930bdccc27decf10c36c97655f761092`
- `docs/_handoff/two-track-stage-0-done.md` — this resumable handoff (self-hash intentionally omitted).

## Canonical contracts closed
- Exactly two persistent runtime tracks: Navigation + HUD, and Cluster Cast.
- Navigation output truth: `OFF | STARTING | EMITTING | DISPLAY_VERIFIED | STALE | FAULT(reason)`.
- Canonical Cast artifact: `docs/specs/cast-ui-state-v2.schema.json`.
- Canonical artifact SHA-256 fixture: `d2c143a5369487fce312bb0a506785a3396280b689ddfd7842557e1f6273ca7b`.
- Artifact checks: 18 fields; 10 enum groups; 20 unique RecoverySubstate mappings; total NextSafeAction→CastAction translation; standalone/both-present Stop precedence; unavailable-reason equality.
- Compensation belongs to the originating transaction and is never replenished by restart/reboot/epoch.
- Destructive sink/orphan recovery requires typed eligibility plus fresh independently observed PARKED; provider absent remains read-only.
- V2 UI/action ownership is session-sticky; requested OFF becomes pending rollback until verified idle/no transaction.
- Diagnostics is bounded per-report read-only utility, not a third persistent runtime/control pipeline.
- Navigation has a non-waivable exact-source → build → install/car gate before Cast Stage 4.
- Dead Reckon remains REMOVE; deferred independent feature review remains NOT STARTED and precedes any R1–R4/provider mutation.

## Validation evidence
- Independent final review: **PASS · 0 actionable P0–P3**.
- Canonical JSON recomputation: PASS, fixture matched exactly.
- Recovery mapping validation: 20/20 enum rows unique and complete; action/reason invariants PASS.
- D8 displayed CastAction set: 12/12 exact artifact match.
- HTML parser: 4/4 documents PASS.
- Required section order/theme persistence/inline SVG/no Mermaid: PASS.
- Identity scan of reviewed artifacts: personal `Đăng Khôi · dangkhoi`; no company identity/email: PASS.
- Evidence honesty: Cast direct V2, UX U1–U24, DR direct/removal and exact-build on-car remain **NOT STARTED**.
- Technology validation: `NO_TECH_CHANGE`; no framework/dependency/API/persistence choice was introduced or changed in Stage 0.
- Build/tests: not run and not authorized; historical 218/218 and 6/6 remain context only.
- Security scan: not triggered because no commit/push is authorized. It remains mandatory before any future separately authorized commit/push.

## Runtime dirty-tree comparison
Entry and exit protected runtime status paths are unchanged:
- `app/build.gradle.kts`
- `app/src/main/java/com/byd/clusternav/ClusterBroadcaster.kt`
- `app/src/main/java/com/byd/clusternav/NavFormat.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/CastShell.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCast.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/WmParse.kt`
- existing modified ClusterNav/Cast unit-test files and existing untracked `CastSwapTest.kt`

No reset, clean, checkout/switch, stash, add, commit or branch mutation occurred.

## Missing required approvals — blocking
The execution prompt requires these six exact out-of-band tokens; none is inferred from `execute`, review PASS or silence:
1. `CAST_REQUIREMENTS_APPROVED`
2. `CAST_DESIGN_APPROVED`
3. `CAST_TASKS_APPROVED`
4. `UX_REQUIREMENTS_APPROVED`
5. `UX_DESIGN_APPROVED`
6. `UX_TASKS_APPROVED`

## Next state
**WAITING_FOR_SPEC_APPROVAL**. Do not start Stage 1 until all six tokens are explicitly recorded. Afterward Stage 1 may create the baseline/public-doc manifest only; it still requires separate `BASELINE_APPROVAL`, then `IMPLEMENTATION_AUTH` before runtime Stage 2.
