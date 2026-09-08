# Session handoff — 2026-08-06 PM (v1.04 release baseline: HUD arrow + bubble + VietMap alerts + update-button)

> Off-car dev + one on-car diagnostic session. **Reviewed + scanned, but NOT committed/pushed yet** (gated on
> WARN-1 decision + push authorization). Next task: **build the release candidate, install on the car, and test.**
> Env: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17` before gradle. Repo/remote: `dangkhoi/byd-cluster` (main).
> Vehicle ADB: `adb connect <vehicle-ip>:5555` (Wi-Fi). `adb reboot` is blocked on DiLink3 → physical ignition off/on.

## State
- Branch `main`, **3 commits ahead of `origin/main` (unpushed)** + this session's **uncommitted** working-tree changes:
  - `3eebe84` docs(handoff) · `2c3d348` feat(update,widget) · `e0a49c4` feat(cast,nav)  ← all from the prior session.
- **Version bumped to 1.04 (versionCode 104).**
- Full JVM suite GREEN: **core 659 + app 290 + car 11 = 960, 0 failures**; `:app:assembleDebug` OK.
- **Senior review (opus): APPROVED**, 0 P0–P1. Self-patched [P2] dead `DiagActivity` update methods, [P3] stale `BubbleRenderer` comments; re-ran tests green.
- **Security scan (opus): NO BLOCK** (no secret/key/PII/raw-IP/machine-path introduced). Verdict **NEEDS_CONFIRMATION** — 3 pre-existing WARNs (see "Remaining ship steps").

## What shipped this session (off-car verified; on-car partially)
1. **HUD turn-arrow fix — neutral `Maneuver` enum.** Root cause: HUD re-derived the turn from notification TEXT while the cluster read the classified icon → every turn showed "đi thẳng". Now the frame carries `NavigationFrameContent.maneuver: Maneuver?` (the single decision) and each output is a PURE ENCODER: cluster `Maneuver.toAmapIcon()`, HUD `Maneuver.toHudIcon()`. Enum granularity = AMAP vocab → `fromAmapIcon(x).toAmapIcon()==x` → **cluster byte-identical**. Files: `core/navigation/Maneuver.kt` (new) + `NavigationModels.kt`; app `NavState.kt`, `NavRepository.kt`, `ClusterBroadcaster.kt` (bydIcon), `WazeHudSource.kt` (hlpTurnToManeuver). Tests: `core/ManeuverTest.kt` (new), updated `WazeHudSourceTest`.
2. **Floating bubble** (`BubbleRenderer.kt`): text 7sp→**13sp**; zones **square + 38dp** (owner "80%", `ZONE_MIN_DP` 48→38, intentionally below 48dp guideline); **drag fix** — `paintDisabled` keeps `isEnabled=true` + marks disabled via `view.tag` (Android only dispatches OnTouchListener when ENABLED, so disabled zones couldn't be dragged); `isZoneDisabled` reads tag; taps still gated. Tests `BubbleAccessibilityTest`/`CastUILifecycleSafetyTest` updated to 38dp.
3. **VietMap widget `UNSUPPORTED_SHAPE` fix** (`VietMapWidgetExtraction.kt` `extractAlerts`): the sticky-alert widget's **no-active-alert placeholder** (`place_holder_textView`='--') lacks the per-alert text views the old strict extractor required → returned null → UNSUPPORTED → **dragged the combined snapshot to UNAVAILABLE and masked a working speed slot**. Now anchors on the always-present alert IMAGES; per-alert text optional (absent = no alert). **Verified on-car** (mock-GPS dump): SPEED slot `reason=null` (currentSpeed tracked = 31 while mock-driving), ALERTS `reason=null`.
4. **Waze** `hlpTurnToManeuver` maps the HLP turn enum → neutral `Maneuver` directly (kills the old magic-int mismatch, e.g. roundabout≠destination).
5. **"⬇ Kiểm tra cập nhật" moved to the main screen** (above "Khắc phục sự cố"): shared `UpdateFlow.kt` (new); `btn_check_update` added to **both** `layout/` and `layout-w960dp/`; `MainActivity` wires it **null-safe**; `DiagActivity` routes to `UpdateFlow`. Also **fixed a crash** (btn_check_update NPE on the head-unit `layout-w960dp` variant) and **removed a pre-existing malformed stray XML line** in `layout-w960dp` (would break a clean release aapt).
6. **v1.04** version bump.

## Removed before release (test-only — do NOT re-add to a release build)
- `MockGps` + `MockGpsActivity` + `ACCESS_FINE_LOCATION` (manifest) + `TEST_WIDGET_DUMP` + the VietMap widget debug dump/tracer (`debugDumpBoundViews`/`debugReport`/`dumpTreeOnFailure`) + the wrong `NAME_FALLBACKS`.
- These were the on-car diagnostic tooling (mock GPS at Vinhomes → VietMap populated its widget → dumped the real widget tree → found the alerts-placeholder bug). Recoverable from this session's history if needed for future indoor nav testing.

## Remaining ship steps (in order) — pending owner decision
1. **WARN-1 decision** (security scan): `RebindReceiver` is `exported=true` with `TEST_ADAS_PROBE/WRITE/READ/MASS/TEST_SPEED_LIMIT` in the **release** manifest → runtime ADAS/instrument-write attack surface. **Pre-existing (from 1.03), documented, NOT a data leak, NOT in this diff.** Options: **(a)** acknowledge + ship (note as pre-release follow-up), or **(b)** move the `TEST_ADAS_*/TEST_SPEED_LIMIT` handlers to `app/src/debug/` (exported off in release) before a true public release. (WARN-2 raw vehicle IPs in `CLAUDE.md:213` + `docs/refactor-car-execution/verdicts.tsv`, WARN-3 `<company-email>` in git reflog — both pre-existing + already on origin/main, don't block this push; redact/rewrite as a separate task.)
2. **Commit v1.04** (scan already done this session; include WARN-1 as a pre-release follow-up note).
3. **Push `origin/main`** (owner-authorized baseline). Note: this is a **source baseline**, not an on-car-passed release.
4. **Build the exact-source release candidate** (per `app/build.gradle.kts`):
   - `python3 scripts/evidence/gen-exact-source.py …` → writes `docs/_handoff/<manifest>.json` with the canonical `exactSourceId`.
   - `./gradlew collectAuthorizedApk -PclusterNavVariant=release -PclusterNavSlice=<slice> -PexactSourceId=<64hex> -PexactSourceManifest=docs/_handoff/<manifest>.json`
   - → produces `apk/ClusterNav-1.04-<slice>-<id>-release.apk` + `docs/_handoff/vehicle-candidate.json`. Build verifies pkg/versionCode + **not debuggable** via aapt2. Requires `keystore.properties` + `release.keystore` (present locally, gitignored) + `ANDROID_HOME`.

## NEXT TASK — install the release on the car + test
1. `adb connect <vehicle-ip>:5555`
2. `adb -s <vehicle-ip>:5555 install -r apk/ClusterNav-1.04-<...>-release.apk`
   - If `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (currently a debug-signed build is installed) → `adb uninstall com.byd.clusternav` first (⚠ clears Cast durable state), then install.
3. Verify: `adb -s <vehicle-ip>:5555 shell dumpsys package com.byd.clusternav | grep versionName` → **1.04**.
4. Test checklist → see [`oncar-test-session-2026-08-06.md`](oncar-test-session-2026-08-06.md). **Release gate = Cast (full/split/resize/profiles) + autostart + HUD arrows.**
   - **HUD arrows** (headline fix, still UNVERIFIED on-car): bật HUD toggle (mặc định tắt) + GMaps route qua 1 khúc rẽ → HUD hiện mũi tên đúng (log `NavigationHudOwner: HUD icon=` → trái=1, phải=2… KHÔNG kẹt 11). **Cần GPS**: ra trời quang cho fix thật. Release KHÔNG có mock GPS.
   - **VietMap speed-limit → cụm**: cần đường CÓ biển giới hạn. Extraction đã proven; nhưng feature HAL render biển vẫn là investigation mở (`speed-limit-sign-oncar-plan.md`, cần harness TEST_ADAS_* = chỉ có ở debug).
   - **Bubble**: chữ to hơn, ô vuông, kéo được ở mọi trạng thái.
   - Waze/alerts: cần GPS + tuyến.

## Open / unverified (honest gates)
- **HUD arrow (turns) NOT verified on-car** — needs a GPS route. Off-car proven (960 tests + review); on-car pending.
- **VietMap speed-limit→cluster RENDER** unproven — extraction proven on-car; the BYD HAL feature that draws the sign is still an open probe (`docs/_handoff/speed-limit-sign-oncar-plan.md`).
- **WARN-1**: guard the exported ADAS test harness (move to debug source set) before any *true public release APK*.
- **Mock GPS removed from release** — to test nav indoors on a release build, use real GPS (open sky) OR recover the mock injector (this session's history) into a **debug** build. Proven technique: `adb shell appops set com.byd.clusternav android:mock_location allow` + `MockGps.addTestProvider(GPS)` + `setTestProviderLocation` (drive sim), triggered via `am start` (broadcasts are `ssc_skip`'d on DiLink3). Vinhomes CP ≈ 10.7945, 106.7212. Always `removeTestProvider` (stop) or it blocks the car's real GPS.

## Verify command
`export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew :core:test :app:testDebugUnitTest :car-integration:test :app:assembleDebug`  → expect **960 tests, 0 failures**.
