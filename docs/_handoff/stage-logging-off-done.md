# Stage: data-collection logging OFF by default + storage cap — DONE

> Working dir: `byd-cluster-2-wt-speed-limit-badge-hal-hud` · branch `feat/speed-limit-badge-hal-hud`
> Gate: `./gradlew :core:test :app:testDebugUnitTest --console=plain` → **GREEN**
> Scope guard: no commit/push/APK-build/keystore touched; `main` untouched.

## Goal

Normal use must collect **NO** data (verbose logging + diagnostic screenshots were force-on in the
data-collection build via `|| BuildConfig.DEBUG`, filling the car's storage with 7 GB+ of `nav_arrow_pngs` +
`diag` screenshots + CSVs). Data-collection is now **opt-in** (default OFF), and an always-on storage cap
(~150 MB) is a defensive backstop so the app can never fill car storage.

---

## 1. Verbose now controlled ONLY by a persisted pref, DEFAULT FALSE

`app/src/main/java/com/byd/clusternav/NavLog.kt:30`
```kotlin
verbose = Prefs.navVerboseLog(ctx)   // was: Prefs.navVerboseLog(ctx) || BuildConfig.DEBUG
```
Removed the `|| BuildConfig.DEBUG` auto-on. `Prefs.navVerboseLog` already defaults to **false**
(`Prefs.kt`, key `nav_verbose_log`). So a fresh/normal install → `NavLog.verbose = false` → every diagnostic
writer early-returns → **no logs, no PNGs, no screenshots**. Class KDoc updated to mention the new visible switch.

No unit test asserted on `BuildConfig.DEBUG`, so the removal is safe (verified by grep + green gate).

## 2. Visible settings switch "Thu thập dữ liệu chẩn đoán (log + ảnh)" (default OFF)

- Layout: `app/src/main/res/layout/activity_main.xml:79` — new `Switch @+id/switch_diag_logging` in the
  Navigation+HUD card (after the marquee checkbox), with a Vietnamese `contentDescription`.
- Wiring: `MainActivity.kt:210` — checked state set from `Prefs.navVerboseLog` **before** attaching the
  listener (so opening the app never fires it → no spurious toast/scan); listener calls `setDiagLogging(on)`.
- `MainActivity.setDiagLogging(on)` `MainActivity.kt:410` — single source of truth: persists
  `Prefs.setNavVerboseLog`, mirrors live `NavLog.verbose = on`, trims storage (`DiagStorageCap.enforce(force=true)`)
  when turning ON, and toasts BẬT/TẮT.
- Hidden long-press kept + kept in sync: `MainActivity.kt:65` — the long-press on the version label now
  ROUTES THROUGH the switch (`sw.isChecked = !sw.isChecked` → the switch listener does the work), with a direct
  fallback if the view is somehow absent. One toggle, one source of truth, both controls always agree.

## 3. ALL diagnostic writers verified gated on `NavLog.verbose`

Confirmed by reading each writer + tracing every disk-write (`grep .compress/.bufferedWriter/FileOutputStream/
writeText/outputStream` across `app/src/main`). No writer needed new gating — all already gate:

| Writer | Output | Gate |
|---|---|---|
| `NavNotifLog.record` | `nav_notif_log_*.csv` | `if (!NavLog.verbose) return` |
| `NavNotifRawLog.record` | `nav_notif_raw_*.csv` | `if (!NavLog.verbose) return` (+ caller `if (NavLog.verbose)` in listener:296) |
| `NavAccessLog.record` | `nav_access_log_*.csv` | `if (!NavLog.verbose) return` (+ `NavAccessibilityService` gates desc collection at :123/:230) |
| `VietMapSignalLog.log/logViews` | `vietmap_signal_*.csv` / `vietmap_views_*.csv` | `if (!NavLog.verbose) return` |
| `NavDistanceLog.ensure/record` | `nav_log_*.csv` | `if (!NavLog.verbose) return` |
| `NavArrowLog.record` | `nav_arrow_log_*.csv` **+ `nav_arrow_pngs_*` bitmap PNG dumps** | `if (!NavLog.verbose) return` (CSV + PNG both inside `recordLocked`, only reached past the gate) |
| `SegmentShotCapturer.onSegmentChange` | `diag/seg-*-{main,cluster,cluster-overlay}.png` screenshots | `if (!NavLog.verbose) return` (+ caller gate in listener:428) |
| `VietMapWidgetVerboseLog` → `VietMapWidgetExtraction.saveAlertImagePng`/`dumpAllViews` | `diag/vietmap-alert-*.png` + view dumps | all 3 entry fns self-gate `if (!NavLog.verbose) return`; the PNG/dump helpers are ONLY called from these gated fns |

Out-of-scope writers (NOT per-frame nav data-collection; still bounded by the cap): `ClusterCast` `castlog/`
(cast-action TEE, already self-limits to 5 files) and `ClusterDiag` `diag/` (one-shot "Chẩn đoán" button).
The OTA APK is in INTERNAL `filesDir/update` (`UpdateChecker`), not the external dir — never a cap candidate.

## 4. Storage cap (defensive, always-on even when verbose) — ~150 MB, delete OLDEST first

**Pure planner (unit-tested, `:core`)** — `core/src/main/kotlin/com/byd/clusternav/core/StorageCapPlanner.kt`
- `DEFAULT_CAP_BYTES = 150 MB` (`:17`).
- `selectForDeletion(entries, capBytes)` (`:32`): given `List<Entry(id, sizeBytes, lastModifiedMs)>` + cap,
  returns the ids to delete **oldest-first** until the survivors fit `<= cap`; empty when already under cap;
  stops at the fewest deletions needed. Defensive edges: negative sizes floored to 0, 0-byte entries never
  selected (they can't reduce the total), ties break by `id`, negative cap evicts everything.

**Android helper (`:app`)** — `app/src/main/java/com/byd/clusternav/DiagStorageCap.kt`
- `enforce(ctx, force=false)` (`:54`): throttled to once / `MIN_INTERVAL_MS = 60 s` unless `force`; posts to a
  single-thread daemon Executor. `enforceLocked` recursively lists files under `getExternalFilesDir(null)`,
  maps to `StorageCapPlanner.Entry`, deletes the selected ids, then prunes now-empty dirs. Every step wrapped
  in `runCatching` — **never throws into nav**, all off the main/nav/notification thread.

**Call sites**
- Session start (defensive, unconditional): `NavNotificationListener.onListenerConnected` `:121` —
  `DiagStorageCap.enforce(app, force = true)` right after `NavLog.init`. Runs even when Nav+HUD/verbose is OFF,
  so it trims data a previous verbose session left behind (this is the "always-on even when verbose" backstop).
- Periodic during logging: `ClusterBroadcaster.sendFrame` `:125` — `if (NavLog.verbose) DiagStorageCap.enforce(ctx)`.
  The ~4 Hz frame path pays only a volatile read; the actual scan is throttled to once/60 s + off-thread, so a
  single long collection drive can't exceed the cap between session-start trims.
- Opportunistic: `MainActivity.setDiagLogging` `:413` — `enforce(force = true)` when the switch/long-press turns
  data-collection ON, trimming leftovers before a collection drive begins.

Note (tradeoff): a data-collection drive that produces **> 150 MB** will have its oldest files trimmed even
before the owner pulls them — this is the intended "never fill storage" behavior; pull promptly. Under 150 MB
is preserved untouched.

---

## Test counts (gate GREEN)

- New: `core/src/test/kotlin/com/byd/clusternav/core/StorageCapPlannerTest.kt` — **10 tests, 0 fail** (under/at/
  over cap, multiple-oldest, fewest-deletions, id tie-break, negative-size floor, empty input, negative cap,
  default = 150 MB).
- `:core:test` → **543 tests, 0 failures**.
- `:app:testDebugUnitTest` → **392 tests, 0 failures**.
- `HudKeepAliveWiringTest` still green (ClusterBroadcaster edit adds no `NavigationHudOwner`/`HudKeepAlivePolicy` string).

## Files changed
- `core/src/main/kotlin/com/byd/clusternav/core/StorageCapPlanner.kt` (new)
- `core/src/test/kotlin/com/byd/clusternav/core/StorageCapPlannerTest.kt` (new)
- `app/src/main/java/com/byd/clusternav/DiagStorageCap.kt` (new)
- `app/src/main/java/com/byd/clusternav/NavLog.kt` (gate change)
- `app/src/main/java/com/byd/clusternav/MainActivity.kt` (switch wiring + long-press routing + `setDiagLogging`)
- `app/src/main/res/layout/activity_main.xml` (visible switch)
- `app/src/main/java/com/byd/clusternav/NavNotificationListener.kt` (cap at session start)
- `app/src/main/java/com/byd/clusternav/ClusterBroadcaster.kt` (periodic cap during logging)
