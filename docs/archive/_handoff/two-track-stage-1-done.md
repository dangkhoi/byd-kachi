# ClusterNav Two-Track Re-baseline — Stage 1 Baseline Handoff

> State: **WAITING_FOR_BASELINE_APPROVAL**  
> Stage result: **MANIFEST + PUBLIC QUARANTINE READY FOR OWNER DECISION**  
> Spec approvals: 6/6 recorded in `two-track-spec-approvals.md`  
> Runtime/build/install/commit/push/merge/car: **NOT AUTHORIZED**  
> Owner: **Đăng Khôi · `dangkhoi`**

## 1. Proposed exact-source identity

- Canonical manifest: `docs/_handoff/two-track-stage-1-exact-source.json`
- Manifest file SHA-256 (includes trailing LF): `191e10ac5fc41e55084550304b242e7e471fd9fd6c47d93f54269fea5c64c6cc`
- Proposed `exactSourceId` (SHA-256 of canonical JSON object without trailing LF): `b26006ecb689974d616deb5222778639e22f283f20664abe2e80023a7f2c068e`
- Branch: `release/v0.60-cast-hardening`
- HEAD: `fd4890c1ffabf4b8cb37f5ccbd5cdb93f0343ae6`
- Post-quarantine tracked binary diff: 105,464 bytes
- Tracked diff SHA-256: `1969fa399e7425b924b94e299035846a5112fbfd15f2af6be5e6ca6a29c9831a`
- Intended untracked inputs: 21
- Generated attestations excluded to prevent self-reference: `two-track-stage-1-exact-source.json`, `two-track-stage-1-done.md`.

This identity is a **proposal**, not approved `EXACT_SOURCE`, until the owner supplies `BASELINE_APPROVAL` for this exact ID, classifications and allowlists. Any branch/HEAD/diff byte/intended-path size/hash/membership change causes `BASELINE_DIVERGED` and requires a fresh manifest.

## 2. Stage-entry to post-quarantine comparison

- Stage-entry tracked diff: 83,565 bytes, SHA-256 `1527d2c68c35f4a7b835c33e8181f7c264dce62fa0d4fd4f9c8cc9b2c6ecb781`.
- Post-quarantine tracked diff: 105,464 bytes, SHA-256 `1969fa399e7425b924b94e299035846a5112fbfd15f2af6be5e6ca6a29c9831a`.
- The difference is restricted to approved Stage 1 public documentation: `README.md`, `docs/HUONG-DAN.md`, `docs/diagnostics/CARTEST.md`, and `docs/reference/dashcast-projection-recipe.md`.
- Runtime/build/test dirty paths and their byte hashes remain unchanged from Stage 1 entry.
- No APK/image byte changed.

## 3. Tracked dirty-path classification

| Path | Class | Bytes | Current file SHA-256 |
|---|---|---:|---|
| `.gitignore` | UNRELATED_PRESERVE | 1359 | `8d77d9958ea207cb0e44f753d153a0b9e2b80bf30c765199ebafef65150ad4c0` |
| `LICENSE` | UNRELATED_PRESERVE | 1091 | `b25f5cd218e41e20284d784d7340cf55e90b6458d664dcc47dd75156b9f35429` |
| `README.md` | PLAN_DOC_PUBLIC_QUARANTINE | 3835 | `3ca64d2119f64c5d7c26b77f2cf4b986ba7ece9cc5b305c5e0b580f11da4f524` |
| `app/build.gradle.kts` | BASELINE_INPUT | 4618 | `7da3825e427fae0689ffd60a3c9ae6b51dd318bfc8eb112372f3f9d144b2ee56` |
| `app/src/main/java/com/byd/clusternav/ClusterBroadcaster.kt` | BASELINE_INPUT | 12624 | `f7db7078508d937acdbfc8bb8599f68bf7b4545f6d889b332a24851dd1c62d0d` |
| `app/src/main/java/com/byd/clusternav/NavFormat.kt` | BASELINE_INPUT | 10764 | `764181fd1a822329a6b4d476cd872cef151b31d2a163505323b7de3634834cbe` |
| `app/src/main/java/com/byd/clusternav/modules/clustercast/CastShell.kt` | BASELINE_INPUT | 37422 | `bd7eb44520559a1f57caa647a95b0d7c96a6ddb9d18f3764c633a89950071cf8` |
| `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCast.kt` | BASELINE_INPUT | 103257 | `65b3400ff9424d4bff88004a7b7f1e1de4c7e8daf5abe2ec700906f34dbf555d` |
| `app/src/main/java/com/byd/clusternav/modules/clustercast/WmParse.kt` | BASELINE_INPUT | 11683 | `06f2fb23fc6be9fc5942defe630e1ad3ce469465eea50688de9e067799d4eebc` |
| `app/src/test/java/com/byd/clusternav/NavFormatTest.kt` | BASELINE_INPUT | 8943 | `b89ce4ee5f89c6c41071b800dec11b517cb76b7057339be5c8710bd6dbb4b1f7` |
| `app/src/test/java/com/byd/clusternav/modules/clustercast/CastFlowTest.kt` | BASELINE_INPUT | 10039 | `f7bf42f4d440ae9996ad8eea4c241ea70d7b1b617ca569ba7f20ca4bc9e5040f` |
| `app/src/test/java/com/byd/clusternav/modules/clustercast/CastStressTest.kt` | BASELINE_INPUT | 9316 | `bc7a31173f36d897fae7743702a0003de2b61b1a873027cf7aa4667fe2ee2cc9` |
| `app/src/test/java/com/byd/clusternav/modules/clustercast/FakeShell.kt` | BASELINE_INPUT | 12839 | `c90886905873e65a3b00ece8695780bb433978a6ea387a774029c8977fd48015` |
| `app/src/test/java/com/byd/clusternav/modules/clustercast/WmParseTest.kt` | BASELINE_INPUT | 14669 | `af720b93d97983c7199a87297f475f9f6cbf68927fb7d659d200066910dd19af` |
| `docs/HUONG-DAN.md` | PLAN_DOC_PUBLIC_QUARANTINE | 4662 | `ac43970bd06b623f4377e043ec2cab0c4efbbe3c4719b03eed7c188a649525fb` |
| `docs/diagnostics/CARTEST.md` | PLAN_DOC_PUBLIC_QUARANTINE | 8804 | `92c42610f2d4d890f67dc3827a415bbe94cc1d5bc5ce7741828a72f78687436a` |
| `docs/reference/dashcast-projection-recipe.md` | PLAN_DOC_PUBLIC_QUARANTINE | 6549 | `90cf0e494d6e2153a8ae4237dc2008654866899ec93d86c934c99a4766debd1f` |
| `docs/review/2026-07-21-baseline.md` | BASELINE_INPUT_HISTORICAL | 37176 | `46ab700c2e72263d52840f03c302b3a1269e7c5b95cefc61a796646c97ebd23d` |
| `docs/review/2026-07-22-3loi-hien-truong.md` | BASELINE_INPUT_HISTORICAL | 4506 | `3a2ed7b29857b595efd2764a68fd51637a1e69fccf843041d7c24608fbbe3c7b` |
| `docs/specs/cluster-cast-v036.html` | BASELINE_INPUT_HISTORICAL | 27984 | `8a0d35d00e1539fa81a2a741a1c96e5a5ed7c0736500453ccbe1e86c14ca924a` |
| `docs/specs/dual-track-2026-07-23.html` | BASELINE_INPUT_HISTORICAL | 19663 | `2c85405fd0fd6725608a60f8f6d8389ff134c6e00eb9861ee4ced4e4d32fd3fb` |

## 4. Visible-untracked classification

| Path | Class | Bytes | SHA-256 | EXACT_SOURCE membership |
|---|---|---:|---|---|
| `.kiro/settings/lsp.json` | UNRELATED_PRESERVE | 4336 | `69aaab5b2c07f6f47866c30330cbbbf323f859ca05b039f4afbc9ab72d12de28` | Excluded/preserve |
| `.kiro/steering/RAI.md` | UNRELATED_PRESERVE | 4547 | `45d371a7cfa1d1b37ef88164c74265836d31e2bb2a833057cd7a24c309f63702` | Excluded/preserve |
| `.kiro/steering/identity.md` | UNRELATED_PRESERVE | 386 | `41b6c3b7c261b329d4534c9dd0f1c4e66c07d58e526d202c78ae2cdbd8494815` | Excluded/preserve |
| `apk/ClusterNav-0.63-release.apk` | GENERATED_ARTIFACT | 2832233 | `80cc9bbac957a617d47be35e3730307333c56529132bf2ce66ff56be9395cb1d` | Excluded/preserve |
| `apk/ClusterNav-0.64-release.apk` | GENERATED_ARTIFACT | 2833121 | `b38f4c7ce2151bf1eed48ea31bc054b6e3d4b7bad878409af433c805c21f6d04` | Excluded/preserve |
| `apk/ClusterNav-0.65-release.apk` | GENERATED_ARTIFACT | 2833641 | `99491860ebc9b7a16e5eea8ecdfdaab3788f25253d1899c25f2d54c3ee132ea3` | Excluded/preserve |
| `apk/ClusterNav-0.66-release.apk` | GENERATED_ARTIFACT | 2837569 | `789ccbc066fc4f84066a266649f6a8adba25ca5e25ca2a3cd2a71ded450da0b6` | Excluded/preserve |
| `apk/ClusterNav-0.67-release.apk` | GENERATED_ARTIFACT | 2837357 | `8f5901c2c15cf513b5e64609258726ddaac11ab49a21ff66fc099d33e213f002` | Excluded/preserve |
| `app/src/test/java/com/byd/clusternav/modules/clustercast/CastSwapTest.kt` | BASELINE_INPUT | 19921 | `3eb04a22a9e74724048fccdf20db8f446480078c635b8f6ccf3603ce5bbda1b4` | Included |
| `docs/HISTORICAL-ARTIFACTS.md` | PLAN_DOC | 6730 | `97605696b0324fe27227dc5ca6c16262045274224087c486d88851cf57ed8154` | Included |
| `docs/_handoff/EXECUTE-two-track-rebaseline.md` | PLAN_DOC | 27436 | `810d5ed1b13d0ad008ce974268dc53f9c16373c49c72a5b8d01ac81b36861180` | Included |
| `docs/_handoff/HANDOFF-2026-07-24-v066-freezeproof.md` | BASELINE_INPUT_HISTORICAL | 7435 | `d2053a6381c175d365132922f3ab15148354da63dab261dcd1d83fc4ec9d25c6` | Included |
| `docs/_handoff/research-aosp-wm.md` | BASELINE_INPUT_HISTORICAL | 13277 | `25f91ac401de6688dc3514ba65c4ca2d4c67b24a1769c52f5beb29a3315bb599` | Included |
| `docs/_handoff/research-evidence-audit.md` | BASELINE_INPUT_HISTORICAL | 13837 | `f2369bc22c15096506bb383668a01861ef0cfd09b5451b48a966c7d9e1d592d2` | Included |
| `docs/_handoff/review-errorhandling.md` | BASELINE_INPUT_HISTORICAL | 13860 | `bec24c9866ac92d4c2a8daf9cdddd5ba31aa76483351016b92def693217a39d4` | Included |
| `docs/_handoff/review-projection-cpaa.md` | BASELINE_INPUT_HISTORICAL | 13699 | `918ae75d993707e7dac6acb98bf95a4ec7fbb59deab5df9445d31a88019ffaf1` | Included |
| `docs/_handoff/stage-impl-cpaa-done.md` | BASELINE_INPUT_HISTORICAL | 6805 | `5d08626506be3edf67de641f28f762ba030fcddc304ade48d68919a009ba3e9e` | Included |
| `docs/_handoff/stage-impl-hud-done.md` | BASELINE_INPUT_HISTORICAL | 3794 | `ae60b8bd5a5e8e416b8c83788fb4eb8647a82eba5791663d286838d3cf6fe9ae` | Included |
| `docs/_handoff/stage-impl-swap-done.md` | BASELINE_INPUT_HISTORICAL | 5680 | `bb980c7bde701db4e1967617a34f8e1b74cdee28e86fc8ba16cca3417e98ab93` | Included |
| `docs/_handoff/two-track-spec-approvals.md` | PLAN_DOC | 806 | `f6e7074a15f4686131f8fb2e79f40917abe17c3dd2f0f7df9998d4557de4bf0d` | Included |
| `docs/_handoff/two-track-stage-0-done.md` | PLAN_DOC | 5623 | `956d0cedec16e0574240367605ca00323a7d9c10a2fb8fb800bcd6e737db935b` | Included |
| `docs/_handoff/two-track-stage-1-done.md` | GENERATED_ATTESTATION | — | self-hash omitted | Excluded from EXACT_SOURCE |
| `docs/_handoff/two-track-stage-1-exact-source.json` | GENERATED_ATTESTATION | 4495 | `191e10ac5fc41e55084550304b242e7e471fd9fd6c47d93f54269fea5c64c6cc` | Excluded/preserve |
| `docs/review/HANDOFF-2026-07-23-oncar-freeze.md` | BASELINE_INPUT_HISTORICAL | 14625 | `c4d45f18919a14ec23cc01e17e52cf330d814c4973bfadb41ac170aa2cb306d5` | Included |
| `docs/review/HANDOFF-2026-07-23.md` | BASELINE_INPUT_HISTORICAL | 19065 | `88afb681c09f982837843eea0dd3b1d555df38057e3cbc0c8a4b03d0308f9fd1` | Included |
| `docs/specs/cast-ui-state-v2.schema.json` | PLAN_DOC | 17638 | `38104e35c9b8072c770a3417f72bd09c36396f7e2958cd8c921674d6117063a8` | Included |
| `docs/specs/cluster-cast-rebaseline.html` | PLAN_DOC | 96227 | `b5b3a30ff7295315e65af4d679a99a22eb6615919de3ab6200ada9f414e93001` | Included |
| `docs/specs/clusternav-two-track-final-plan.html` | PLAN_DOC | 40994 | `3dbac11cf48f5db39f64890582a28999b077e8f601849219af7a053530886f57` | Included |
| `docs/specs/clusternav-uxui-rebaseline.html` | PLAN_DOC | 97126 | `6599d5a5370027ad84aeba8be1460b8c5fdb1259ec01a34822781d45aabdc28f` | Included |
| `docs/specs/dead-reckon-revalidation.html` | PLAN_DOC | 33236 | `b2cbb08ddac24e4eb3b9922a4ba9e780e6317b7f3e0af67093dc9f9a2f8dd775` | Included |
| `docs/specs/freeze-proof-cluster-switch.html` | BASELINE_INPUT_HISTORICAL | 79711 | `8538e3d8897b493a6c01ecf5b70bcc6c07738ddce1f32baa95a358cc1139d014` | Included |

Ignored build/APK outputs are inventoried separately in `docs/HISTORICAL-ARTIFACTS.md`; they are `GENERATED_ARTIFACT`, never source identity.

## 5. APK and screenshot quarantine

`docs/HISTORICAL-ARTIFACTS.md` records 8 APK paths and 5 screenshots with immutable byte size/SHA-256, manifest version/package and signing-certificate hashes where available. Every artifact is **historical/unsupported**. Unknown provenance/flags/vehicle evidence is explicit. The v0.67 repository APK and ignored build output are byte-identical, which does not prove current-source provenance.

Existing version-only Gradle collection can overwrite `apk/ClusterNav-0.67-release.apk`; naming a unique path in `BUILD_AUTH_<SLICE>` alone does not prevent it. Stage 2 must modify only allowlisted `app/build.gradle.kts` to remove the unconditional version-only assemble finalizer and implement an explicit collision-failing collector: require slice + exactSourceId, select only the newly built requested variant, write `apk/ClusterNav-<version>-<slice>-<exactSourceId12>-release.apk`, and abort if the destination exists. All `BUILD_AUTH_*` remain blocked until this gate passes; existing APK bytes stay immutable.

## 6. Public-document quarantine result

- `README.md`: now leads with NO-GO, exactly two target tracks, DR REMOVE and withdrawn install/download guidance.
- `docs/HUONG-DAN.md`: now an archived screenshot/reference guide; T1/T3/DR/install/signature/vehicle claims are explicitly withdrawn.
- `docs/diagnostics/CARTEST.md`: archived DO-NOT-EXECUTE banner; DR C1–C4 remain NOT STARTED; physical power-button reboot rule bound.
- `docs/reference/dashcast-projection-recipe.md`: archived research-only banner; commands are not current authorization/evidence.
- Historical APKs and screenshots are hash-bound in `docs/HISTORICAL-ARTIFACTS.md`.

No future current-facing release claim is allowed until docs/screenshots publish atomically against an exact APK SHA/version/signature/flags and exact-build car PASS.

## 7. Proposed per-stage path allowlists

Global default is **deny**. Paths below are exact repository-relative candidates. Reading is not restricted; mutation is. A path absent from the current stage requires a revised owner-approved baseline/allowlist before edit. No wildcard authorizes a sibling file.

Always denied under this execution prompt:
- `.gitignore`, `LICENSE`, `.kiro/**`, every existing `apk/**` byte, ignored build outputs.
- `app/src/main/java/com/byd/clusternav/modules/deadreckon/**`.
- `app/src/main/java/com/byd/clusternav/modules/mockloc/**`.
- `app/src/main/java/com/byd/clusternav/SpeedProvider.kt` and DR-related manifest/provider/pref mutations.
- Physical deletion of legacy Cast paths.

### Stage 1 — completed documentation only
- `README.md`
- `docs/HUONG-DAN.md`
- `docs/diagnostics/CARTEST.md`
- `docs/reference/dashcast-projection-recipe.md`
- `docs/HISTORICAL-ARTIFACTS.md`
- `docs/_handoff/two-track-spec-approvals.md`
- `docs/_handoff/two-track-stage-1-exact-source.json`
- `docs/_handoff/two-track-stage-1-done.md`
### Stage 2 — independent contract foundations
- `app/build.gradle.kts`
- `app/src/test/java/com/byd/clusternav/BuildArtifactNamingTest.kt`
- `app/src/main/java/com/byd/clusternav/navigation/NavigationModels.kt`
- `app/src/main/java/com/byd/clusternav/navigation/NavigationFrameStore.kt`
- `app/src/main/java/com/byd/clusternav/navigation/NavigationSessionCoordinator.kt`
- `app/src/main/java/com/byd/clusternav/navigation/ClusterLaneAdapter.kt`
- `app/src/main/java/com/byd/clusternav/navigation/HudAdapter.kt`
- `app/src/test/java/com/byd/clusternav/navigation/NavigationSessionCoordinatorTest.kt`
- `app/src/test/java/com/byd/clusternav/navigation/NavigationOutputIsolationTest.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastModels.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastSessionStore.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/ObservedStateReader.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPolicy.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPlanner.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/ShellGateway.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastUiStateProjector.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastRolloutRegistry.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastSessionStoreTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastPlannerManifestTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastUiStateProjectorTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/TwoPipelineStaticBoundaryTest.kt`
- `docs/design/navigation-hud-evidence.html`
- `docs/design/cluster-cast-evidence.html`
- `docs/_handoff/two-track-stage-2-done.md`
### Stage 3 — Navigation runtime/UI and Cast dry planner
- `app/src/main/java/com/byd/clusternav/navigation/NavigationModels.kt`
- `app/src/main/java/com/byd/clusternav/navigation/NavigationFrameStore.kt`
- `app/src/main/java/com/byd/clusternav/navigation/NavigationSessionCoordinator.kt`
- `app/src/main/java/com/byd/clusternav/navigation/ClusterLaneAdapter.kt`
- `app/src/main/java/com/byd/clusternav/navigation/HudAdapter.kt`
- `app/src/main/java/com/byd/clusternav/NavNotificationListener.kt`
- `app/src/main/java/com/byd/clusternav/NavRepository.kt`
- `app/src/main/java/com/byd/clusternav/NavState.kt`
- `app/src/main/java/com/byd/clusternav/ClusterBroadcaster.kt`
- `app/src/main/java/com/byd/clusternav/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_navigation.xml`
- `app/src/main/res/layout-w960dp/activity_main.xml`
- `app/src/main/res/layout-w1280dp/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/test/java/com/byd/clusternav/navigation/NavigationSessionCoordinatorTest.kt`
- `app/src/test/java/com/byd/clusternav/navigation/NavigationOutputIsolationTest.kt`
- `app/src/test/java/com/byd/clusternav/TwoTrackHomeContractTest.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/ObservedStateReader.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPolicy.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPlanner.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/ShellGateway.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastDryPlannerTest.kt`
- `docs/design/navigation-hud-evidence.html`
- `docs/design/cluster-cast-evidence.html`
- `docs/_handoff/two-track-stage-3-done.md`
### Stage 4 — Cast Stop/recovery dark mode
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastModels.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastSessionStore.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/ObservedStateReader.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPolicy.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPlanner.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/ShellGateway.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastUiStateProjector.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastRolloutRegistry.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastCoordinator.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastExecutor.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastRecovery.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCast.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/CastShell.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/WmParse.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastRecoveryTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastBlockedIoTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastRestartTest.kt`
- `docs/design/cluster-cast-evidence.html`
- `docs/_handoff/two-track-stage-4-done.md`
### Stage 5 — normal slice and Cast renderer
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastCoordinator.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastExecutor.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastRecovery.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastGeometry.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCastActivity.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt`
- `app/src/main/res/layout/activity_cluster_cast.xml`
- `app/src/main/res/layout/cast_bubble.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastNormalSliceTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastRendererContractTest.kt`
- `docs/design/cluster-cast-evidence.html`
- `docs/_handoff/two-track-stage-5-done.md`
### Stage 6 — CarPlay slice
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPolicy.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPlanner.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastCoordinator.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastExecutor.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastRecovery.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastCarPlaySliceTest.kt`
- `docs/design/cluster-cast-evidence.html`
- `docs/_handoff/two-track-stage-6-done.md`
### Stage 7 — Android Auto slice
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPolicy.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPlanner.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastCoordinator.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastExecutor.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastRecovery.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastAndroidAutoSliceTest.kt`
- `docs/design/cluster-cast-evidence.html`
- `docs/_handoff/two-track-stage-7-done.md`
### Stage 8 — warm matrix/app manager/Bubble
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPolicy.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastPlanner.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastCoordinator.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastExecutor.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCastActivity.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt`
- `app/src/main/java/com/byd/clusternav/Prefs.kt`
- `app/src/main/res/layout/activity_cast_app_manager.xml`
- `app/src/main/res/layout/cast_bubble.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastWarmMatrixTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastBubbleContractTest.kt`
- `docs/design/cluster-cast-evidence.html`
- `docs/_handoff/two-track-stage-8-done.md`
### Stage 9 — geometry/lifecycle/adjustment/diagnostics
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastGeometry.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastCoordinator.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/v2/CastRecovery.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCastActivity.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterDiag.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/DiagActivity.kt`
- `app/src/main/java/com/byd/clusternav/RebindReceiver.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/layout/activity_cast_adjustment.xml`
- `app/src/main/res/layout/activity_cast_diagnostics.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastGeometryTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastLifecycleTest.kt`
- `app/src/test/java/com/byd/clusternav/modules/clustercast/v2/CastDiagnosticsContractTest.kt`
- `docs/design/cluster-cast-evidence.html`
- `docs/_handoff/two-track-stage-9-done.md`
### Stage 10 — support/migration/accessibility/docs
- `app/src/main/java/com/byd/clusternav/MainActivity.kt`
- `app/src/main/java/com/byd/clusternav/Prefs.kt`
- `app/src/main/java/com/byd/clusternav/UpdateChecker.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCastActivity.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterDiag.kt`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/DiagActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/layout-w960dp/activity_main.xml`
- `app/src/main/res/layout-w1280dp/activity_main.xml`
- `app/src/test/java/com/byd/clusternav/TwoTrackMigrationTest.kt`
- `app/src/test/java/com/byd/clusternav/TwoTrackAccessibilityContractTest.kt`
- `README.md`
- `docs/HUONG-DAN.md`
- `docs/HISTORICAL-ARTIFACTS.md`
- `docs/diagnostics/CARTEST.md`
- `docs/reference/dashcast-projection-recipe.md`
- `docs/design/navigation-hud-evidence.html`
- `docs/design/cluster-cast-evidence.html`
- `docs/_handoff/two-track-stage-10-done.md`
### Stage 11 — final review
- Stage 10 must generate `docs/_handoff/two-track-stage-11-allowlist.txt`: UTF-8-byte-sorted, newline-delimited exact paths equal to `(approved Stage 2–10 paths ∩ actually changed paths)`; no comments, globs or narrative entries.
- Stage 10 records SHA-256 of that exact list in `two-track-stage-10-done.md`.
- Until separate `IMPLEMENTATION_AUTH_STAGE11 allowlistSha256=<hash>` is recorded, Stage 11 is report-only.
- After that authorization, Stage 11 may mutate only paths materialized in that list plus `docs/_handoff/two-track-stage-11-done.md`; any additional path is `BASELINE_DIVERGED`/scope reapproval.
- `docs/_handoff/two-track-stage-11-allowlist.txt`
- `docs/_handoff/two-track-stage-11-done.md`
### Stage 12 — deferred deletion
- **Empty allowlist. Planning marker only.**

## 8. Evidence and validation

- Agent 1 provenance report: 29 pre-attestation visible-untracked paths + 2 generated attestations = 31 current visible-untracked paths; tracked diff, APK manifests/signatures, exact-source schema and allowlists.
- Agent 2 public-doc report: `PUBLIC_QUARANTINE_REQUIRED`; quarantine applied to primary surfaces and archive headers.
- `NO_TECH_CHANGE`: no framework, dependency, Android API, persistence, IPC, process model or test technology changed; Context7 was not required.
- Build/tests/install/car: not run and not authorized.
- Security scan: not triggered because commit/push is not authorized; mandatory before any future authorized commit/push.
- Direct V2/UX/DR-removal/car evidence remains **NOT STARTED**.

## 9. Required next decisions

### First blocking decision
To approve this exact manifest/classification/allowlist proposal, reply with:

`BASELINE_APPROVAL exactSourceId=b26006ecb689974d616deb5222778639e22f283f20664abe2e80023a7f2c068e`

Any different ID or requested allowlist change is a revision, not approval.

### Still required before Stage 2
After baseline approval, runtime mutation remains blocked until the owner separately supplies an `IMPLEMENTATION_AUTH` that names Stage 2 and accepts its exact allowlist. Baseline approval alone does not authorize code changes, build, tests, install, commit, push, merge or vehicle action.

## 10. Next state

**WAITING_FOR_BASELINE_APPROVAL**. Do not start Stage 2 and do not create evidence ledgers/source files until the exact baseline decision is recorded.

## 11. Independent Stage 1 senior review

Final verdict: **PASS — 0 actionable P0–P3**.

The reviewer independently recomputed branch/HEAD, 105,464-byte tracked diff and SHA-256, canonical `exactSourceId`, all tracked and intended-untracked hashes, the 31-path untracked partition, all 8 APK and 5 image hashes/metadata, public quarantine, runtime/test preservation, collision-safe build guard requirement, hash-bound Stage 11 scope and deny-by-default rules. No runtime/build/install/test/vehicle/git mutation occurred.

Reviewer-confirmed proposal:

- `exactSourceId=b26006ecb689974d616deb5222778639e22f283f20664abe2e80023a7f2c068e`
- State: `WAITING_FOR_BASELINE_APPROVAL`
- Direct V2/UX/DR-removal/on-car evidence: `NOT STARTED`

## 12. Owner authorization — 2026-07-24T20:37:44.764+07:00

The terminal-wrapped owner message was normalized by removing display separators/whitespace and exactly matched:

- `BASELINE_APPROVAL exactSourceId=b26006ecb689974d616deb5222778639e22f283f20664abe2e80023a7f2c068e`
- `IMPLEMENTATION_AUTH stage=2 allowlist=docs/_handoff/two-track-stage-1-done.md#stage-2`

Authorization scope: Stage 2 exact allowlist only. It does not authorize Stage 3, APK build, install, vehicle mutation, commit, push, merge, release, DR/provider mutation, existing APK overwrite, or legacy deletion.

## 12. Owner autonomous off-car allowlist amendment — 2026-07-24T23:24:11.813+07:00

This owner decision supersedes the per-stage sequencing stops and the blanket DR deferral above while preserving deny-by-default mutation control:

- The active implementation allowlist is the exact union of the listed Stage 2–10 paths.
- Wave IDs are traceability labels; no intermediate car PASS or build token is required to continue off-car work.
- DR/mock-provider paths remain denied until the deferred DR closure review produces an exact path-level removal list; that reviewed list is preauthorized by `AUTONOMOUS_OFFCAR_APPROVED stages=2-10` and must be recorded before mutation.
- Existing `apk/**` bytes remain immutable. Exactly one new collision-safe test APK path generated from the final exactSourceId is authorized; no intermediate APK is authorized.
- Additional final evidence/script paths authorized:
  - `docs/_handoff/two-track-autonomous-progress.md`
  - `docs/_handoff/two-track-stage-2-exact-source.json`
  - `docs/_handoff/two-track-stage-11-allowlist.txt`
  - `docs/_handoff/two-track-vehicle-ready.md`
  - `docs/diagnostics/VEHICLE-TEST-V2.md`
  - `scripts/vehicle/preflight.sh`
  - `scripts/vehicle/install-test-apk.sh`
  - `scripts/vehicle/run-navigation-matrix.sh`
  - `scripts/vehicle/run-cast-matrix.sh`
  - `scripts/vehicle/capture-evidence.sh`
- This amendment does not authorize install, ADB/car mutation, commit, push, merge, public release, or physical deletion of rollback-readable legacy Cast paths.
