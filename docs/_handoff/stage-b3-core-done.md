# Stage b3-core — DONE (pure :core slice, off-car green)

> Task: Implement the PURE `:core` logic for **B3 screen-capture nav** (the off-car-lockable part per spec §2.2 / R-nf5).
> Spec: `<repo>/docs/specs/waze-vietmap-screen-capture.html` (§4.3 4-case, §4.4 bounds 3-tier, §4.5 image-proc, R1–R6).
> Branch: `feat/speed-limit-badge-hal-hud` · Date: 2026-08-19 · **No commit/push. README.md untouched.**

## Gate (GREEN)
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:test --console=plain    # BUILD SUCCESSFUL
```
- **594 tests, 0 failures, 0 errors** across 60 classes (was 560; **+34 new**).
- New: `CaptureRouterTest` = **19**, `VietMapCameraMatcherTest` = **8**, `SourceArbiterChannelTest` = **7**.
- `:core` stays pure Kotlin — **no Android imports** added (verified: only imports are `com.byd.clusternav.*` + `kotlin.math`).

## Files created (all under `<repo>/core/src/main/kotlin/com/byd/clusternav/navigation/`)
| File | Package | What |
|------|---------|------|
| `screencapture/CaptureModels.kt` | `…navigation.screencapture` | enums + data models |
| `screencapture/CaptureRouter.kt` | `…navigation.screencapture` | `CaptureRouter` + `CaptureCalibration` |
| `screencapture/VietMapCameraMatcher.kt` | `…navigation.screencapture` | camera template-match (grid+NCC) |
| `screencapture/PixelFrameOps.kt` | `…navigation.screencapture` | pure `crop()` |
| `NavChannel.kt` | `…navigation` | `enum NavChannel { DATA, IMAGE }` |
| **modified** `SourceArbiter.kt` | `…navigation` | channel-aware feed (data > image) |

Tests: `<repo>/core/src/test/kotlin/com/byd/clusternav/navigation/screencapture/{CaptureRouterTest,VietMapCameraMatcherTest}.kt` + `…/navigation/SourceArbiterChannelTest.kt`.

---

## EXACT public API the `:app` layer calls

### Package `com.byd.clusternav.navigation.screencapture`

```kotlin
// ── CaptureModels.kt ─────────────────────────────────────────────
enum class CaptureCase { FULL_MAIN, HALF_MAIN_SPLIT, CLUSTER_CAST, NOT_ACTIVE }

enum class CaptureTarget {
    ARROW, CAMERA;
    companion object { fun forPackage(pkg: String): CaptureTarget }   // vn.vietmap.live→CAMERA else ARROW
}

enum class BoundsSource { A11Y_DYNAMIC, FIXED_CALIBRATED, NONE }

data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int          // right-left
    val height: Int         // bottom-top
    fun isEmpty(): Boolean  // width<=0 || height<=0
    fun offsetX(dx: Int): CropRect
    fun clampTo(region: CropRect): CropRect   // intersect; non-overlap → empty rect at region corner
    companion object { val EMPTY: CropRect }  // (0,0,0,0)
}

data class DisplayGeometry(
    val displayW: Int,
    val displayH: Int,
    val mainDisplayId: Int = 0,
    val clusterDisplayId: Int = 1,
) { val fullRect: CropRect }

// NOTE: slotSide reuses com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide (LEFT/RIGHT);
//       null = not split. leftPercent semantics == AppMover.fitToCluster (divider = displayW*leftPercent/100).
data class AppLocation(
    val pkg: String,
    val displayId: Int,
    val isFullscreen: Boolean,
    val slotSide: ClusterSlotSide? = null,
    val leftPercent: Int = 50,
    val foreground: Boolean = true,
    val navFresh: Boolean = true,
)

data class CaptureBounds(val rect: CropRect, val capturedAtMs: Long)  // a11y-published bounds + monotonic stamp

data class CapturePlan(
    val case: CaptureCase,
    val target: CaptureTarget,
    val bounds: CropRect,          // bounds.isEmpty() ⇒ caller drops the frame
    val boundsSource: BoundsSource,
)

// ── CaptureRouter.kt ─────────────────────────────────────────────
object CaptureRouter {
    const val A11Y_FRESH_MS: Long   // 1500

    /** null ⇒ GATE CLOSED (navFresh=false) → do NOT capture. Otherwise a CapturePlan. */
    fun route(
        loc: AppLocation,
        geom: DisplayGeometry,
        a11y: CaptureBounds? = null,
        now: Long = 0L,
        freshMs: Long = A11Y_FRESH_MS,
    ): CapturePlan?

    fun selectCase(loc: AppLocation, geom: DisplayGeometry): CaptureCase
    fun computeBounds(
        case: CaptureCase, loc: AppLocation, geom: DisplayGeometry, target: CaptureTarget,
        a11y: CaptureBounds?, now: Long, freshMs: Long,
    ): Pair<CropRect, BoundsSource>
    fun halfOffsetX(case: CaptureCase, loc: AppLocation, geom: DisplayGeometry): Int
    fun appRegion(case: CaptureCase, loc: AppLocation, geom: DisplayGeometry): CropRect
}

object CaptureCalibration {
    val WAZE_ARROW_OPENBYD: CropRect     // CropRect(26,218,208,298) — OpenBYD seed, tune on-car (OQ2)
    val VIETMAP_CAMERA_SEED: CropRect    // CropRect(0,120,160,280) — placeholder, tune on-car (OQ4)
    fun fixedBounds(target: CaptureTarget, geom: DisplayGeometry): CropRect?
}

// ── PixelFrameOps.kt ─────────────────────────────────────────────
object PixelFrameOps {
    // src is com.byd.clusternav.navigation.PixelFrame; returns cropped ArrayPixelFrame, or null if invalid/empty.
    fun crop(src: PixelFrame, rect: CropRect): PixelFrame?
}

// ── VietMapCameraMatcher.kt ──────────────────────────────────────
class CameraTemplate(val name: String, val fill: FloatArray)   // equals/hashCode by name+fill

data class CameraMatch(
    val hasCamera: Boolean,
    val score: Float,
    val templateName: String?,
    val speedKmh: Int? = null,        // null this round (OQ4 OCR on-car)
    val distanceMeters: Int? = null,  // null this round
) { companion object { val NONE: CameraMatch } }

class VietMapCameraMatcher(
    templates: List<CameraTemplate>,
    minScore: Float = DEFAULT_MIN_SCORE,
) {
    fun match(frame: PixelFrame?): CameraMatch
    companion object {
        const val GRID: Int                     // 15
        const val DEFAULT_MIN_SCORE: Float      // 0.62f
        val BUILTIN: List<CameraTemplate>       // emptyList() — real templates = OQ4 on-car
        fun signatureOf(frame: PixelFrame?): FloatArray?          // GRID*GRID premultiplied-luminance grid
        fun templateFrom(name: String, frame: PixelFrame): CameraTemplate?
    }
}
```

### Package `com.byd.clusternav.navigation` (arbiter integration)

```kotlin
enum class NavChannel { DATA, IMAGE }

object SourceArbiter {
    const val STALE_MS: Long   // 6000 (unchanged)
    // channel defaults to DATA → ALL existing 3-arg callers keep old behavior + record data timestamp.
    // The screen-capture source calls with NavChannel.IMAGE; it is suppressed while DATA is fresh (≤ STALE_MS).
    fun shouldFeed(pkg: String, mode: Int, now: Long, channel: NavChannel = NavChannel.DATA): Boolean
    fun isDataFresh(pkg: String, now: Long): Boolean
    // unchanged: val activeSource: String?; fun release(pkg): Boolean; fun clear(); fun isFresh(now): Boolean
}
```

---

## How `:app` wires it (T4/T5/T7 next stage)
1. Build `AppLocation` from `am stack list` (displayId/fullscreen/slot/leftPercent), a11y foreground, `SourceArbiter.activeSource`/`isFresh` (navFresh).
2. `CaptureRouter.route(loc, geom, a11yBounds, SystemClock.elapsedRealtime())`.
   - `null` → gate closed, skip.
   - `plan.case` → pick transport (spec §4.6 hybrid: Case 1/2 = mirror/`fission_screencap -d 1`; Case 3 = `fission_screencap -d 0`; Case 4 = offscreen VD).
   - `plan.bounds.isEmpty()` → drop frame; else crop the captured `Bitmap` to `plan.bounds` (or use `PixelFrameOps.crop` on a `PixelFrame`).
3. `plan.target == ARROW` → `ManeuverSignature.classifyManeuver/classify/classifyHal(croppedPixelFrame)` (REUSED, unchanged).
   `plan.target == CAMERA` → `vietMapCameraMatcher.match(croppedPixelFrame)` (seed templates via `templateFrom` from on-car captures, OQ4).
4. Feed result through `SourceArbiter.shouldFeed(pkg, mode, now, NavChannel.IMAGE)` — respects data > image.
5. Wrap all capture/classify in `runCatching`; off-main-thread; verbose-gated diag save (R-nf1..4) — all `:app`.

## Pure-testable (LOCKED off-car) vs on-car (still owed)
- **Locked now:** case selection (4 + gate + boundary), bounds 3-tier (a11y-fresh vs fixed), Case-2 half offset L/R + clamp, camera template-match (synthetic fixture positive / blank + disjoint negative / threshold gate), `data > image` priority + STALE, crop→PixelFrame→`ManeuverSignature.classify` path.
- **On-car (open questions, unchanged):** OQ1 MediaProjection auto-grant via dadb; OQ2 real fixed rects + a11y bounds on cluster; OQ3 capture rate/perf; OQ4 real VietMap camera templates (+ speed/dist OCR); OQ5 Case-4 4b offscreen-render feasibility; OQ6 transport A/B/hybrid final. Real calibration values in `CaptureCalibration` are seeds to tune on-car.

## Docs updated (R2.6)
- `<repo>/docs/specs/waze-vietmap-screen-capture.html` — §1 Changelog Pass 1 + §9 Implementation Log (decisions/deviations). Spec status stays "Chờ duyệt" for the `:app`/on-car parts; only the pure `:core` slice landed.

## Notes for reviewer / next stage
- `ClusterSlotSide` reused from `com.byd.clusternav.modules.clustercast.simplified` (already `:core`, pure) — no new slot enum.
- No exported test surface, no Android, no new third-party deps (R-nf5/R-nf6 respected at `:core` level).
- `SourceArbiter` change is additive + backward-compatible (default channel = DATA); existing `:app` `shouldFeed(pkg, mode, now)` calls compile and behave identically.
