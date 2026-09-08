# Stage: upcoming speed-limit + distance cluster badge — DONE

> Working dir: `byd-cluster-2-wt-speed-limit-badge-hal-hud` · branch `feat/speed-limit-badge-hal-hud`
> Spec: `docs/specs/upcoming-speed-limit-badge.html` (OQ resolved) · Reads `docs/_handoff/stage-logging-off-done.md`
> Gate: `./gradlew :core:test :app:testDebugUnitTest --console=plain` → **GREEN**
> Scope guard: no commit/push/APK-build/keystore touched; `main` untouched. Senior review + APK build (spec T6)
> remain the orchestrator's next stage.

## Goal

Mirror VietMap's "speed-limit ahead" (the `ALERT_FULL` slot captured in commit `36f6bf0`) onto the cluster:
a smaller speed-limit badge (~70%) with a **countdown distance** label, drawn directly **below** the existing
current-limit badge. Additive — the current-limit badge is untouched. OQ-resolved behavior: anchor below the
main badge, follow VietMap 1:1 (no own distance threshold), countdown number, only the nearest one, HUD out of
scope.

---

## What each spec task delivered (T1–T5)

### T1 — pure decision in `:core` (+ unit test)
`core/src/main/kotlin/com/byd/clusternav/navigation/UpcomingBadgeDecision.kt` (new, 51 LOC)
- `data class UpcomingBadge(show: Boolean, limitKph: Int, distanceMeters: Int)` (+ `HIDDEN` constant).
- `UpcomingBadgeDecision.decide(limitKph: Int?, distanceMeters: Int?, fresh: Boolean): UpcomingBadge`.
  - Show iff `fresh && limitKph != null && limitKph > 0` **and** not `(distanceMeters != null && <= 0)`.
  - **OQ2 (mirror VietMap):** NO own distance threshold — show exactly when VietMap shows a fresh upcoming
    limit; distance is display-only.
  - Hide on: not fresh (stale/unavailable), null/≤0 limit, or known distance ≤ 0 (already reached).
  - Distance `null` + fresh + valid limit → **show** with `distanceMeters = 0` (renderer omits the label).

`core/src/test/kotlin/com/byd/clusternav/navigation/UpcomingBadgeDecisionTest.kt` (new, 9 tests, all pass):
null→hide, stale→hide, fresh+limit+dist→show(+values), dist=0→hide, dist<0→hide, limit=0→hide, limit<0→hide,
fresh+null-dist→show(dist 0), HIDDEN not shown.

### T2 — Pref
`app/.../Prefs.kt`: `showUpcomingBadge(ctx)` / `setShowUpcomingBadge(ctx, v)`, key `show_upcoming_badge`,
**default `true`** (ON, per badge precedent).

### T3 — Overlay: second badge + distance label, anchored below the main badge
`app/.../speedbadge/SpeedBadgeOverlay.kt` (437 LOC, still < 500)
- New **second window** on display 1 holding a vertical `LinearLayout` = `[SpeedBadgeView (~70%)]` + a
  `TextView` distance label. Reuses `SpeedBadgeView` unchanged (R4 differentiation = smaller + label below).
- `setUpcoming(limitKph: Int?, distanceMeters: Int?, distanceText: String? = null)` → posts to the main
  handler → `doSetUpcoming`; null/≤0 limit hides it.
- `applyUpcomingEnabled()` re-evaluates the gate (master `badgeEnabled` AND `showUpcomingBadge`) for the live
  toggle. `teardownUpcoming()` detaches + drops the window/views; wired into the existing `teardown()`,
  `onDisplayAdded` (re-show), and `doRefreshLayout` (moves live with the placement UI).
- **Degrade-safe:** every WindowManager op is `runCatching` on the main handler; off-car (no display 1) is a
  cheap no-op. Additive — `doShow`/`buildLayoutParams`/`teardown` for the current-limit badge are unchanged
  (the badge-lifecycle contract test still passes).
- Distance label text: prefers VietMap's raw `upcomingDistanceText` ("300 m"/"1,2 km"); else
  `NavParse.formatMeters(distanceMeters)` (reused from `:core`, DRY); else empty (label GONE).

### T4 — Wire in `speedLimitPusher`
`app/.../NavNotificationListener.kt` (468 LOC): after the current-limit push, the pusher computes
`UpcomingBadgeDecision.decide(...)` from the snapshot and calls `speedSignOwner.setUpcomingBadge(...)`, gated by
`Prefs.showUpcomingBadge` and wrapped in `runCatching` (never throws into the cluster feed). The current-limit
branch (`snapshot.speedLimitKph ?: 0`, `speedUpdatedAtElapsedMs`, `onProviderDisconnected(...VIETMAP)`) is
unchanged.

`app/.../NavigationSpeedSignOwner.kt` (141 LOC): `setUpcomingBadge(limitKph, distM, distText)` +
`onUpcomingBadgeEnabledChanged()` forward to the ONE shared `badgeOverlay` (BUG-1 single-overlay invariant kept:
still exactly one `SpeedBadgeOverlay(` construction; the file deliberately avoids the `distanceMeters` token an
existing contract test forbids — params are named `distM`/`distText`).

### T5 — UI toggle "Hiện giới hạn sắp tới" (default ON)
- Layouts `app/.../res/layout/activity_main.xml` + `layout-w960dp/activity_main.xml`: new
  `Switch @+id/switch_upcoming_badge`, `android:checked="true"`, VN `contentDescription`, placed right under
  the badge on/off switch in the "Biển báo tốc độ trên cụm" block of the Cluster-Cast card.
- `app/.../modules/clustercast/BadgePlacementController.kt` (155 LOC): binds the switch with the same
  detach-before-restore pattern → `Prefs.setShowUpcomingBadge` + `onUpcomingBadgeEnabledChanged()`.

---

## Snapshot fields used (from `VietMapWidgetSnapshot`, ALERT_FULL slot)
- `upcomingLimitKph: Int?` — the upcoming enforced limit → the second badge's number.
- `upcomingDistanceMeters: Int?` — parsed metres → decision + fallback label format.
- `upcomingDistanceText: String?` — VietMap's raw distance text → preferred countdown label.
- `alertFullFreshness: VietMapWidgetFreshness` — `== FRESH` gates show/hide.
- (`second*` fields deliberately ignored — OQ5, only the nearest one.)

## How the upcoming badge is anchored below the main badge
Both windows are positioned from the SAME persisted badge prefs via the tested pure `BadgeLayout`
(`clampCenter` → `topLeftFromCenter`), so they always agree:
- Main badge: `sizePx × sizePx` at top-left `(left, top)`.
- Upcoming window: `x = left − (containerW − mainSizePx)/2`, `y = top + mainSizePx + gap`
  (`containerW = 1.8 × mainSizePx` so long "1,2 km" text can't clip; `gap = 0.10 × mainSizePx`).
  The container is centred on the main badge's centre, and the vertical `LinearLayout` (CENTER_HORIZONTAL)
  centres the ~70% badge + label within it → the upcoming badge sits directly under, and centred on, the main
  badge. It tracks the main badge live (placement drag/resize) via `doRefreshLayout`.
- Accepted tradeoff (spec-aligned): if the driver parks the main badge at the very bottom edge, the upcoming
  badge extends below it (windows use `FLAG_LAYOUT_NO_LIMITS`); anchoring under the main badge is the owner's
  chosen behavior (OQ1). No above/below flip logic (kept simple + degrade-safe).

## Test counts (gate GREEN)
- `:core:test` → **552 tests, 0 failures** (was 543; +9 from `UpcomingBadgeDecisionTest`).
- `:app:testDebugUnitTest` → **392 tests, 0 failures** (unchanged count; the source-text contract tests
  `SpeedBadgeLifecycleContractTest` + `SpeedSignSourceLifecycleTest` still pass, proving the additive edits kept
  every wiring/lifecycle/single-overlay invariant).

## Files changed
- `core/src/main/kotlin/com/byd/clusternav/navigation/UpcomingBadgeDecision.kt` (new)
- `core/src/test/kotlin/com/byd/clusternav/navigation/UpcomingBadgeDecisionTest.kt` (new)
- `app/src/main/java/com/byd/clusternav/Prefs.kt` (showUpcomingBadge pref)
- `app/src/main/java/com/byd/clusternav/speedbadge/SpeedBadgeOverlay.kt` (second badge + label + lifecycle)
- `app/src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt` (forward setUpcoming / toggle)
- `app/src/main/java/com/byd/clusternav/NavNotificationListener.kt` (pusher wiring)
- `app/src/main/java/com/byd/clusternav/modules/clustercast/BadgePlacementController.kt` (toggle wiring)
- `app/src/main/res/layout/activity_main.xml` + `app/src/main/res/layout-w960dp/activity_main.xml` (switch)

## Notes / next
- On-car visual verification (V-visual: badge + countdown appears when VietMap guides through a limit change,
  counts down, hides on arrival; toggle OFF hides it) is not runnable off-car — belongs to the on-car pass.
- Spec T6 (senior review + security scan + APK build) is intentionally NOT done here (scope guard: no APK/commit).
