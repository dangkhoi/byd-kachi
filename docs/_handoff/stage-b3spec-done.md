# Stage b3spec — DONE

> Task: Write the SPEC (NOT code) for **B3 — screen-capture nav source (Waze arrow + VietMap camera)**, learning from OpenBYD.
> Working dir: `<repo root>` (worktree byd-cluster-2-wt-speed-limit-badge-hal-hud)
> Date: 2026-08-19 · Status: **Chờ duyệt (KHÔNG implement)** · DOCS ONLY.

## Deliverable
- **`docs/specs/waze-vietmap-screen-capture.html`** — Kiro-style consolidated spec (380 lines, self-contained, Apple-2026 style, light/dark, inline SVG diagrams).
- This handoff. **README.md untouched. No code. No commit/push.**

## Gate check (all pass)
- File exists ✅
- Well-formed ✅ — Python `html.parser` tag-balance: 0 unclosed, 0 errors; both `<svg>` blocks parse as valid XML via `minidom`; 0 raw non-entity `&`.
- Sections per `_template.html` order ✅ — Changelog · Context · Requirements · Design · Tasks · Verification · Open Questions · References · **Nhật ký triển khai (Implementation Log, empty)** · Reviewer Log.
- 4 CASE covered ✅ — each appears 3× (case tag in gate diagram heading, in the 4-case SVG, and in its own detail sub-heading).
- Status pill = "Chờ duyệt — KHÔNG implement" ✅.

## Sections (what each contains)
1. **Changelog** — Pass 0 2026-08-19, origin = backlog B3.1–B3.4, learning OpenBYD `WazeArrowCaptureService`.
2. **Context** — the REAL problem (Waze HLP/1 needs a BT peer; VietMap camera not guaranteed in widget → arrow/camera only exist as PIXELS); table of reusable in-repo blocks (`ManeuverSignature`, `PixelFrame`/`BitmapPixelFrame`, dadb `fission_screencap` from `SegmentShotCapturer`, `SourceArbiter`, `NavAccessibilitySource`); a §2.2 summary of exactly what OpenBYD does (consent/lifecycle/routing/bounds/processArrowPixels/perf); a gate card (on-car-heavy; trace-den-tan-cung applies).
3. **Requirements** — R1..R6 functional (Waze arrow via image, VietMap camera via template-match, 4-case, MediaProjection consent, isNavigationActive gate, arbiter wiring) + R-nf1..6 (degrade-safe, off-main-thread, perf, verbose/storage, :core-pure, no exported test surface).
4. **Design** — 2 inline-SVG diagrams (pipeline + 4-case routing) + per-case detail + bounds tiers + image-processing + transport A/B comparison + Context7 note.
5. **Tasks** — T1..T10 DAG table (off-car pure-core first, on-car last), each tagged with which case.
6. **Verification** — V-unit (off-car, lockable now), V-gate, V-oncar per case (physical-power-button reboot rule), V-perf, V-lifecycle/degrade.
7. **Open Questions** — OQ1 MediaProjection auto-grant via dadb, OQ2 bounds detection (a11y vs fixed; a11y on cluster?), OQ3 perf/rate, OQ4 VietMap camera icon templates, OQ5 Case-4 offscreen-render feasibility, OQ6 transport choice.
8. **References** — OpenBYD files + all in-repo files cited by path.
9. **Nhật ký triển khai** — empty (chưa implement), with instructions.
10. **Reviewer Log** — empty (draft).

## How the 4 CASES are addressed (owner requirement)
Each case has: **detect (where the app is)** → **bounds (which crop region)** → **capture path**.
- **CASE 1 (full main):** detect task fullscreen on Android display 0 (via `am stack list`/a11y window); bounds via a11y `getBoundsInScreen` (preferred) or fixed calibrated rect (OpenBYD `getArrowBounds`); capture = AUTO_MIRROR VirtualDisplay of D0 → ImageReader → crop, **or** `fission_screencap -d 1` (fission -d1 = MAIN) → crop.
- **CASE 2 (half main, split L/R):** detect freeform/split task + `slotSide`+`leftPercent` (reuse `AppMover.fitToCluster` model); bounds = same as C1 but **offset by the half** (a11y absolute coords auto-correct; clamp to app's half to reject the other app); capture = mirror D0 / `fission_screencap -d 1` then crop the correct half.
- **CASE 3 (on cluster, cast):** **flagged the key architecture delta** — owner's "PixelCopy from cast SurfaceView" is OpenBYD's model where the cast target renders into an OpenBYD-owned SurfaceView; **our cast is move-stack/fresh-launch freeform on the OEM VirtualDisplay (`ProjectionManager`/`AppMover`), so there is NO app-owned SurfaceView**. Proposed path for the current architecture = capture the cluster display directly via **`fission_screencap -d 0`** (fission -d0 = CLUSTER, proven) / `screencap -d 1`, then crop. The literal PixelCopy-from-SurfaceView parity is documented as a **future branch** gated on adopting a DashCast-Path-B SurfaceView-backed VD (+ maybe platform signing) — per trace-den-tan-cung, not hand-waved away.
- **CASE 4 (not active anywhere):** detect nav fresh (running in background) but app not foreground on D0 nor on cluster; capture = create an **offscreen VirtualDisplay** (surface = ImageReader) and get the nav app to render into it — **4a** AUTO_MIRROR of a display that still holds the app window (simplest, learns OpenBYD mirroring VD), or **4b** `am start --display <vdId> --windowingMode 5` to render the app offscreen (owner's "somewhere the app still renders while captured"). Marked hardest (B3.4); if an app refuses to render into a secondary VD → record evidence + unlock condition, degrade silently (don't silently drop the feature).

## Key technical grounding (from reading source)
- OpenBYD `WazeArrowCaptureService`: `ImageReader.newInstance(w,h,RGBA_8888,2)`, `VirtualDisplay` flag `16` (`AUTO_MIRROR`), `getArrowBounds()`=`Rect(26,218,208,298)` fixed, `laneContainerBounds` a11y-set; `processArrowPixels` → `wm0.b/d` = **our `ManeuverSignature`**.
- Our proven capture (no MediaProjection consent): `fission_screencap -d 0` = cluster, `-d 1` = main (fission ids OPPOSITE Android); `screencap -d 1` = Android cast/overlay layer — all via `SimpleCastRuntime.coordinator(ctx).executeShell(...)` over the dadb loopback, gated by `NavLog.verbose`.
- `ManeuverSignature.classify()/classifyHal()/classifyManeuver()` accept a `PixelFrame`; `BitmapPixelFrame` bridges a cropped `Bitmap`.
- `SourceArbiter` PREFER_WAZE (`com.waze`/`com.chisadin.wazemod`) / PREFER_VIETMAP (`vn.vietmap.live`), STALE 6 s — image source plugs in here; **data channel (HLP/1, widget) always beats image (image = fallback)**.

## Next step (for owner / next stage)
- Owner reviews spec → approve prompt `"Approve spec waze-vietmap-screen-capture"` (single out-of-band gate; NOT stored in HTML).
- OQ1/OQ3 need on-car experiments before the transport (A vs B vs hybrid) is finalized.
- No implementation until approved (autonomous-mode: this is a plan-phase deliverable).
