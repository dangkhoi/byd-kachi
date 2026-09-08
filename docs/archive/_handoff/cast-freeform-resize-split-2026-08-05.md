# Cast Freeform / Resize / Split — Session Handoff 2026-08-05

> Successful on-car session on **SL6 (<vehicle-ip>)** and **Seal (<vehicle-ip>)**, both BYD DiLink3.0 (Android 10, QKQ1.210910.001).
> Records exactly how the cast resize/split problem was diagnosed and solved — reproducible, no re-discovery needed.

---

## 1. The problem

On first cast test:
- **Cast Full** worked.
- **Cast Left / Right (split)** → app went full-screen instead of the left/right half.
- **Resize** (shrink app via UI) → content scaled internally but window stayed full-screen; earlier broken tests left the display at a tiny `934×240` override ("DPI scaled too big, can't fix").

Root question: why can't we place an app in a specific rectangle (half, or arbitrary box) on the cluster?

---

## 2. Systematic adb investigation

All tested live on SL6 cluster (virtual display id 1, `fission_bg_xdjaVirtualSurface`, physical **1920×720 @ 320dpi**).

| Method | Command | Result |
|--------|---------|--------|
| Per-task bounds | `am task resize <task> 0 0 960 720` | ❌ `IllegalArgumentException: resizeTask not allowed` — rejected while freeform not alive |
| Content inset | `wm overscan 0,0,960,0 -d 1` | ⚠️ inset applied (`content=[0,0][960,720]`) but window `mFrame` stays `[0,0][1920,720]` — Maps ignores inset → right half renders black (hardware clip only) |
| Logical display size | `wm size 960x720 -d 1` | ✅ Works — app relayouts, BUT LogicalDisplay letterboxes CENTERED, keeps aspect → cannot offset to a side, cannot do 2 apps |
| Split-screen windowing | `am start --windowingMode 3` / `4` | ❌ Downgraded to fullscreen on virtual display |

### AOSP-10 truth (confirmed on-car)
- `WindowConfiguration.canResizeTask()` == `(windowingMode == FREEFORM)`. Fullscreen task → `am task resize` always throws.
- Freeform needs `enable_freeform_support=1`, read **only at boot** by `ActivityTaskManagerService.retrieveSettings()` (no ContentObserver). Runtime set has no effect until power-cycle.
- `adb reboot` is blocked on DiLink3 → must be physical ignition off/on.
- Shell uid 2000 (dadb channel the app uses) CAN write `settings put global enable_freeform_support 1` — verified rc=0.

### Breakthrough
After owner power-cycled SL6, re-probe:
```
am task resize 16 0 0 960 720   → NO exception, bounds became [0,0][960,720] ✅
```
**Freeform alive → per-app bounds work → resize (arbitrary rect) + split (2 apps) both possible.**

---

## 3. Solution implemented in code

### 3.1 Enable freeform flags on projection open
`SimpleCastCoordinator.openProjection()` calls `ensureFreeformFlags()`:
```
settings put global enable_freeform_support 1
settings put global force_resizable_activities 1
```
Idempotent; persists; activates next power-cycle. First install needs ONE power-cycle before split/arbitrary-resize. Cast Full + DPI work immediately without it.

### 3.2 Freeform-alive probe
`SimpleCastCoordinator.isFreeformAlive()` — tries `am task resize` on any cluster task; success ⇒ alive.

### 3.3 Resize path (single app) — `resizeActiveTarget(l,t,r,b)`
1. Tier 1: `am task resize <task> l t r b` — exact rect (freeform alive). Persist bounds only on success (R6).
2. Tier 2 fallback (pre-freeform): `wm size WxH -d 1`, W=r-l H=b-t clamped to physical via `queryDisplayPhysicalSize()`. Centered letterbox.
   - `wm size` keeps aspect: same-aspect rect → full-bleed (no visible change); wide-short → full-width shorter; tall-narrow → full-height narrower. Documented pre-freeform limitation.

### 3.4 Split path (two apps) — `AppMover.fitToCluster(...)`
- LEFT → `am task resize <task> 0 0 (W*pct/100) H`
- RIGHT → `am task resize <task> (W*pct/100) 0 W H`
- If rejected and slotSide != null: do NOT fall back to `wm size`/`wm overscan` (display-global, cannot split). Log "split needs freeform (power-cycle)". Prevents the earlier bug where split corrupted whole-display size.

### 3.5 Default bounds = full, user shrinks down (owner decision)
- `SimpleCastModels.NORMAL_DEFAULT.bounds = CastBounds(0,0,1920,720)` (was 0,90,1920,630)
- `CastResizeView` default rect `0,0,1920,720`
- `CastGeometryEditor` setBounds/reset fallbacks top 90→0, bottom clusterHeight-90→clusterHeight

### 3.6 Corrupt-prefs pitfall (fixed on-car)
Earlier broken fallback persisted `config_size=934x240`, `config_density=160`, `config_bounds=0,0,934,217` → every cast re-applied density 160 (zoomed) + wm size 934×240 (tiny) = "DPI too big / resize broken". Cleared saved config → clean defaults.
Lesson: persist geometry only after a verified successful apply; never persist the aspect-limited wm-size fallback as saved bounds without marking provisional.

---

## 4. Files changed (cast geometry)

| File | Change |
|------|--------|
| `core/.../simplified/SimpleCastCoordinator.kt` | `ensureFreeformFlags()` on open; `isFreeformAlive()`; `queryDisplayPhysicalSize()`; `resizeActiveTarget` tier-1 am task resize + tier-2 wm size |
| `core/.../simplified/AppMover.kt` | `fitToCluster` split via am task resize; honest freeform-required log (no display-global fallback for split) |
| `core/.../simplified/SimpleCastModels.kt` | `NORMAL_DEFAULT.bounds=(0,0,1920,720)`; wmSize `1920x720` (letterbox fix) |
| `app/.../CastResizeView.kt` | default rect `0,0,1920,720` |
| `app/.../CastGeometryEditor.kt` | bounds fallbacks top 90→0, bottom clusterHeight-90→clusterHeight |

---

## 5. First-run procedure on a new vehicle

1. Install APK; grant `appwidget grantbind`, `READ_LOGS`, notification listener.
2. Open ClusterNav once → sets freeform flags via shell. Or manually:
   ```
   adb -s <ip>:5555 shell settings put global enable_freeform_support 1
   adb -s <ip>:5555 shell settings put global force_resizable_activities 1
   ```
3. **Power-cycle the vehicle once** (physical ignition off/on; adb reboot blocked).
4. Reconnect, verify freeform alive:
   ```
   adb -s <ip>:5555 shell "am start --display 1 --windowingMode 5 -n '<pkg>/<activity>'"
   adb -s <ip>:5555 shell am task resize <task> 0 0 960 720   # no exception = alive
   ```
5. Cast Full → Cast Left/Right → resize via UI: all work with exact bounds.

Before power-cycle: Cast Full + DPI work; split/arbitrary-resize logs "needs freeform".

---

## 6. Verified state at session end
- SL6: freeform alive after power-cycle; `am task resize` → exact bounds `[0,0][960,720]` confirmed.
- Both vehicles: v1.03 debug + WazeMod installed, permissions + freeform flags set.
- APK: `apk/ClusterNav-1.03-debug.apk`.

## 7. Still open (next session)
- Speed-limit sign injection — see `docs/_handoff/speed-limit-sign-oncar-plan.md`.
- Seal freeform-alive status unconfirmed (flags set; may need its own power-cycle).
- Reset any leftover display override before shipping: `wm size reset -d 1; wm density reset -d 1; wm overscan reset -d 1`.

## 8. Other wins bundled this session
- Letterbox fix: wmSize 1920×800 → 1920×720 (was scaling 0.9 → 96px black bars).
- PiP block: `appops set <maps/youtube> PICTURE_IN_PICTURE deny` on service start, restore on stop.
- WazeMod HLP/1 logcat source (tag WazeHUD) + 2 source spinners (nav vs speed/alert).
- VietMap widget: correct provider class `homewidget.VMOnly*`, auto-bind + persist (bind once forever).
- Bubble zones 70% (56→40dp).
- Distance-assist removed (fought firmware count-down → jumpy numbers).
