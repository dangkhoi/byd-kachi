# RE — AMAP NEW_ICON (0..28) + HUD CAN icon tables + Maneuver-enrichment plan

> **Trạng thái**: Current · **Cập nhật**: 2026-08-14 · **Mục đích**: Bảng RE icon AMAP/HUD CAN + kế hoạch enrich Maneuver (mọi claim trace [RE:file:line]).

> Owner: Đăng Khôi · `dangkhoi` — 2026-08-14. Deep-RE deliverable. **No code changed** by this document.
> Every claim is traced to `[RE:file:line]` (decompiled firmware) or `[src:file:line]` (repo). No assumptions
> except where explicitly tagged **GUESS** with an on-car verification step (per `no-assumptions.md`).

## 0. TL;DR — why the cluster shows fewer / wrong turn arrows

- The BYD AMAP bridge only speaks **29 turn glyphs** (`NEW_ICON` 0..28) and remaps them to a **CAN turn-id
  table (1..49)** via a fixed array. **Neither table has a dedicated `merge`, `fork`, `on-ramp`, `off-ramp`
  or `keep-left/right` glyph.** [RE:AmapService.java:66-67]
- Our neutral `Maneuver` enum deliberately collapses fork/merge into slight [src:Maneuver.kt:16], and the
  classifiers encode `merge → 5 (=SLIGHT_RIGHT / AMAP icon 5)` [src:ManeuverSignature.kt:230],
  `merge_right→5, merge_left→4` [src:IconResource.kt:27]. That is exactly why the owner's *"nhập làn / merge"*
  glyph renders as a **slight/turn-RIGHT** arrow on the cluster.
- **Immediate fix (evidence-based):** since `NEW_ICON 0..28` has **no** merge glyph, `merge` must encode to
  **STRAIGHT** (`AMAP 9 → CAN 11`), not slight-right. Full plan in §7.
- Google's own automotive model *does* separate merge (`MergeToLeft`/`MergeToRight`) [RE:Turn.java:8-9] — so the
  loss happens at the **BYD glyph boundary**, not upstream. We can preserve the richer decision in the enum for
  logging/future, but the pixel it draws is limited to what the CAN table offers.

---

## 1. AMAP `NEW_ICON` 0..28 → glyph table (AUTHORITATIVE)

Two decompiled arrays in the OEM AMAP bridge define this space:

- **Glyph label** per index: `TURN_STRING[]` (Chinese) [RE:AmapService.java:66]
- **AMAP `NEW_ICON` → CAN turn-id** remap: `TurnIdMapToCAN[]` [RE:AmapService.java:67]

```
TurnIdMapToCAN = {0, 0, 1, 2, 3, 5, 7, 8, 9, 11, 45, 13, 24, 46, 47, 48, 49,
                  14, 23, 10, 12, 15, 18, 20, 22, 16, 17, 19, 21}
index:            0  1  2  3  4  5  6  7  8  9  10  11  12  13  14  15  16
                  17  18  19  20  21  22  23  24  25  26  27  28
```

The producer reads `NEW_ICON` from the AUTONAVI broadcast [RE:AmapService.java:358], **guards `0 ≤ icon < 29`**
[RE:AmapService.java:603], applies the roundabout-exit offset or `TurnIdMapToCAN` [RE:AmapService.java:605-614],
then writes the CAN id to `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` (0x43F01010) [RE:AmapService.java:619].

| NEW_ICON | `TURN_STRING` (zh) | Glyph meaning (EN) | → CAN | Used by our enum? |
|:--:|---|---|:--:|:--:|
| 0 | `" "` | none / blank | 0 | — |
| 1 | 自车图标 | ego-car / start-pin | 0 | — (guard: `fromAmapIcon(1)=null` [src:Maneuver.kt]) |
| **2** | 左转图标 | **turn left** | 1 | ✅ TURN_LEFT |
| **3** | 右转图标 | **turn right** | 2 | ✅ TURN_RIGHT |
| **4** | 左前方图标 | **slight / keep left (front-left)** | 3 | ✅ SLIGHT_LEFT |
| **5** | 右前方图标 | **slight / keep right (front-right)** | 5 | ✅ SLIGHT_RIGHT |
| **6** | 左后方图标 | **sharp left (rear-left)** | 7 | ✅ SHARP_LEFT |
| **7** | 右后方图标 | **sharp right (rear-right)** | 8 | ✅ SHARP_RIGHT |
| **8** | 左转掉头图标 | **U-turn (left, RHT)** | 9 | ✅ UTURN |
| **9** | 直行图标 | **straight** | 11 | ✅ STRAIGHT |
| 10 | 到达途经点图标 | arrive at waypoint (via-point) | 45 | ❌ UNUSED |
| **11** | 进入环岛图标 (RHT/CCW) | **enter roundabout (CCW)** | 13 | ✅ ROUNDABOUT |
| 12 | 驶出环岛图标 (RHT/CCW) | exit / drive-out roundabout (CCW) | 24 | ❌ UNUSED |
| 13 | 到达服务区图标 | arrive at service area | 46 | ❌ UNUSED |
| 14 | 到达收费站图标 | arrive at toll station | 47 | ❌ UNUSED |
| **15** | 到达目的地图标 | **arrive at destination** | 48 | ✅ DESTINATION |
| 16 | 进入隧道图标 | enter tunnel | 49 | ❌ UNUSED |
| 17 | 进入环岛图标 (LHT/CW) | enter roundabout (CW) | 14 | ❌ UNUSED |
| 18 | 驶出环岛图标 (LHT/CW) | exit roundabout (CW) | 23 | ❌ UNUSED |
| 19 | 右转掉头图标 (LHT) | U-turn (right, LHT) | 10 | ❌ UNUSED |
| **20** | 顺行图标 | **continue / follow road (顺行)** | 12 | ✅ CONTINUE |
| 21 | 绕环岛左转 (RHT/CCW) | roundabout, turn-left, CCW | 15 | ❌ UNUSED |
| 22 | 绕环岛右转 (RHT/CCW) | roundabout, turn-right, CCW | 18 | ❌ UNUSED |
| 23 | 绕环岛直行 (RHT/CCW) | roundabout, straight, CCW | 20 | ❌ UNUSED |
| 24 | 绕环岛调头 (RHT/CCW) | roundabout, U-turn, CCW | 22 | ❌ UNUSED |
| 25 | 绕环岛左转 (LHT/CW) | roundabout, turn-left, CW | 16 | ❌ UNUSED |
| 26 | 绕环岛右转 (LHT/CW) | roundabout, turn-right, CW | 17 | ❌ UNUSED |
| 27 | 绕环岛直行 (LHT/CW) | roundabout, straight, CW | 19 | ❌ UNUSED |
| 28 | 绕环岛调头 (LHT/CW) | roundabout, U-turn, CW | 21 | ❌ UNUSED |

### 1.1 Merge / fork / ramp / keep / roundabout-exit availability

- **merge** → **NONE.** No `NEW_ICON` glyph exists. Nearest: `9 straight`, `20 continue`, `4/5 slight`.
- **fork** → **NONE dedicated.** Nearest: `4/5 slight` (a Y-split ≈ a slight bias — visually acceptable).
- **on-ramp / off-ramp** → **NONE dedicated.** Nearest: `4/5 slight` (gentle divergence).
- **keep-left / keep-right** → **NONE dedicated.** Same glyph as slight `4/5` (already handled as slight).
- **roundabout-with-exit-number** → **AVAILABLE** but only via the CAN offset path (§2.1): send `NEW_ICON`
  ∈ {11,12,17,18} **plus** `ROUNG_ABOUT_NUM` (1..10) → CAN `25..44`. This is richer than the single
  `ROUNDABOUT` glyph we emit today.
- **U-turn right** (`19`), **exit-roundabout** (`12`/`18`), **enter-roundabout CW** (`17`) are valid but
  **UNUSED** by our enum — free vocabulary if we want to separate them.

---

## 2. HUD / cluster-centre CAN turn-id table (1..49)

`INSTRUMENT_GUIDE_INFO_SIMPLE_SET` = feature id **`0x43F01010`** [RE:Instrument.java: `INSTRUMENT_GUIDE_INFO_SIMPLE_SET = "0x43F01010"`; mapped in RE:InstrumentMapper.java]. Both the windshield HUD and the cluster-centre read this
CAN turn-id; the cluster **lane** instead consumes the AMAP broadcast (which `AmapService` remaps here anyway).
Our repo writes this CAN id directly for the HUD via `Maneuver.toHudIcon()` [src:Maneuver.kt] — see the 1.14
mirror-fix contract [src:HudManeuverEncodingTest.kt].

> **Source of glyph meaning:** there is **no standalone CAN-glyph enum** in the RE cache (grep `HudController`,
> `TURN_ICON` → none). The CAN meanings below are **derived from the OEM's own producer choice** — i.e. which
> `NEW_ICON` the OEM maps *into* each CAN id via `TurnIdMapToCAN` [RE:AmapService.java:67] + the roundabout-exit
> offset [RE:AmapService.java:605-614]. This is authoritative for *"what glyph does the cluster draw for CAN id
> N"* because it is the exact table the shipping firmware uses.

| CAN id | Glyph meaning (derived) | Evidence |
|:--:|---|---|
| 1 | turn left | NEW_ICON 2→1 |
| 2 | turn right | NEW_ICON 3→2 |
| 3 | slight / keep left | NEW_ICON 4→3 |
| **4** | **UNKNOWN — not targeted by AMAP (gap)** | not present in `TurnIdMapToCAN`; **GUESS** = extra left variant → probe on-car (§7.5) |
| 5 | slight / keep right | NEW_ICON 5→5 |
| **6** | **UNKNOWN — not targeted by AMAP (gap)** | not present in `TurnIdMapToCAN`; **GUESS** → probe on-car (§7.5) |
| 7 | sharp left | NEW_ICON 6→7 |
| 8 | sharp right | NEW_ICON 7→8 |
| 9 | U-turn (left) | NEW_ICON 8→9 |
| 10 | U-turn (right) | NEW_ICON 19→10 |
| 11 | straight | NEW_ICON 9→11 |
| 12 | continue / follow (顺行) | NEW_ICON 20→12 |
| 13 | enter roundabout (CCW / RHT) | NEW_ICON 11→13 |
| 14 | enter roundabout (CW / LHT) | NEW_ICON 17→14 |
| 15 | roundabout, turn-left, CCW | NEW_ICON 21→15 |
| 16 | roundabout, turn-left, CW | NEW_ICON 25→16 |
| 17 | roundabout, turn-right, CW | NEW_ICON 26→17 |
| 18 | roundabout, turn-right, CCW | NEW_ICON 22→18 |
| 19 | roundabout, straight, CW | NEW_ICON 27→19 |
| 20 | roundabout, straight, CCW | NEW_ICON 23→20 |
| 21 | roundabout, U-turn, CW | NEW_ICON 28→21 |
| 22 | roundabout, U-turn, CCW | NEW_ICON 24→22 |
| 23 | exit roundabout (CW / LHT) | NEW_ICON 18→23 |
| 24 | exit roundabout (CCW / RHT) | NEW_ICON 12→24 |
| 25..34 | **roundabout, take exit N (N=1..10), CCW/RHT** | `roungAboutNum + 24` when NEW_ICON∈{11,12} [RE:AmapService.java:607] |
| 35..44 | **roundabout, take exit N (N=1..10), CW/LHT** | `roungAboutNum + 34` when NEW_ICON∈{17,18} [RE:AmapService.java:609] |
| 45 | arrive at waypoint (via-point) | NEW_ICON 10→45 |
| 46 | arrive at service area | NEW_ICON 13→46 |
| 47 | arrive at toll station | NEW_ICON 14→47 |
| 48 | arrive at destination | NEW_ICON 15→48 |
| 49 | enter tunnel | NEW_ICON 16→49 |

**Only CAN 4 and 6 are unaccounted for** across 1..49 — every other id is pinned by the OEM table. This matches
the owner's "HUD icons 1..49".

### 2.1 Roundabout-exit offset mechanics [RE:AmapService.java:605-614]

```java
if (roungAboutNum > 0 && roungAboutNum <= 10) {
    if (11==icon || 12==icon)  icon = roungAboutNum + 24;   // CCW enter/exit → CAN 25..34
    else if (17==icon || 18==icon) icon = roungAboutNum + 34; // CW  enter/exit → CAN 35..44
    else icon = TurnIdMapToCAN[icon];
} else {
    icon = TurnIdMapToCAN[icon];
}
```
So the "take the Nth exit" glyphs are reachable **only** by sending `NEW_ICON` 11/12 (or 17/18) together with
`ROUNG_ABOUT_NUM`. Without a valid exit number, `NEW_ICON 11 → CAN 13` (generic "enter roundabout").
`NavFormat.roundaboutExit()` already extracts N from text [src:NavFormat.kt] — see §7.4.

---

## 3. Cross-reference — merge IS first-class upstream (not a BYD glyph)

The co-resident TMap engine bundles Google's automotive `Turn` proto [RE:Turn.java]:

```
Left(0) SlightLeft(1) SharpLeft(2) Right(3) SlightRight(4) SharpRight(5)
Through(6) Reverse(7) MergeToLeft(8) MergeToRight(9) None(10)
```
[RE:Turn.java:1-13]

→ Google models **MergeToLeft / MergeToRight** as distinct maneuvers. The information is lost precisely at the
**BYD glyph boundary** (`NEW_ICON`/CAN have no merge glyph), *not* at the map source. Implication for the plan:
we can carry a distinct `MERGE` decision in the neutral enum (good for trace/logging and any future glyph), but
the drawn pixel is still limited to the CAN table → **straight** is the honest render.

---

## 4. The 38 GMaps `ManeuverRegistry` names + current mapping / verdict

Registry = 38 perceptual signatures ported from Open BYD 2.3 [src:ManeuverRegistry.kt]. Current name→code logic:
`ManeuverSignature.nameToAmap` (cluster/AMAP) [src:ManeuverSignature.kt:218-234] and `nameToHal` (direct-HAL)
[src:ManeuverSignature.kt:238-254]. First-substring-match wins.

Legend: **AMAP** = `nameToAmap` result (NEW_ICON), **HAL** = `nameToHal` result (CAN). Verdict flags the
collapses/mismatches.

| # | Registry name | AMAP | HAL | Verdict |
|:--:|---|:--:|:--:|---|
| 1 | maneuver_depart | 9 | 12 | OK (start = straight; HAL 12=depart glyph) |
| 2 | maneuver_straight | 9 | 11 | OK |
| 3 | maneuver_destination | 15 | 48 | OK |
| 4 | maneuver_destination_left | 15 | 48 | side lost (no dir'l dest glyph) — acceptable |
| 5 | maneuver_destination_right | 15 | 48 | side lost — acceptable |
| 6 | maneuver_fork_left | 4 | 3 | **COLLAPSED → slight-left** (no fork glyph) |
| 7 | maneuver_turn_slight_left | 4 | 3 | OK |
| 8 | maneuver_fork_right | 5 | 5 | **COLLAPSED → slight-right** (no fork glyph) |
| 9 | maneuver_turn_slight_right | 5 | 5 | OK |
| 10 | maneuver_turn_normal_left | 2 | 1 | OK |
| 11 | maneuver_off_ramp_normal_left | **2** | **1** | ⚠ **MISMAPPED → hard turn-left** (should be slight; "normal_left" matches before any ramp rule) |
| 12 | maneuver_turn_normal_right | 3 | 2 | OK |
| 13 | maneuver_off_ramp_normal_right | **3** | **2** | ⚠ **MISMAPPED → hard turn-right** (should be slight) |
| 14 | maneuver_turn_sharp_left | 6 | 7 | OK |
| 15 | maneuver_turn_sharp_right | 7 | 8 | OK |
| 16 | maneuver_u_turn_left | 8 | 9 | OK |
| 17 | maneuver_u_turn_right | **8** | **10** | ⚠ AMAP side lost (→ U-turn *left* 8); HAL keeps right (10). Two outputs disagree. |
| 18 | maneuver_merge | **5** | **11** | ⚠ **BUG: AMAP→slight-right (owner's wrong arrow); HAL→straight.** Two outputs disagree. |
| 19 | maneuver_roundabout_enter_ccw | 11 | 20 | ⚠ AMAP→CAN13(enter); HAL→CAN20(around-straight). Disagree. |
| 20 | ..._ccw_normal_left | 11 | 20 | COLLAPSED → generic roundabout |
| 21 | ..._ccw_normal_right | 11 | 20 | COLLAPSED |
| 22 | ..._ccw_sharp_left | 11 | 20 | COLLAPSED |
| 23 | ..._ccw_sharp_right | 11 | 20 | COLLAPSED |
| 24 | ..._ccw_slight_left | 11 | 20 | COLLAPSED |
| 25 | ..._ccw_slight_right | 11 | 20 | COLLAPSED |
| 26 | ..._ccw_straight | 11 | 20 | COLLAPSED |
| 27 | ..._ccw_u_turn | 11 | 20 | COLLAPSED (roundabout wins over u_turn — intended [src:ManeuverSignature.kt:219]) |
| 28 | maneuver_roundabout_enter_cw | 11 | 20 | COLLAPSED (CW direction lost) |
| 29 | ..._cw_normal_left | 11 | 20 | COLLAPSED |
| 30 | ..._cw_normal_right | 11 | 20 | COLLAPSED |
| 31 | ..._cw_sharp_left | 11 | 20 | COLLAPSED |
| 32 | ..._cw_sharp_right | 11 | 20 | COLLAPSED |
| 33 | ..._cw_slight_left | 11 | 20 | COLLAPSED |
| 34 | ..._cw_slight_right | 11 | 20 | COLLAPSED |
| 35 | ..._cw_straight | 11 | 20 | COLLAPSED |
| 36 | ..._cw_u_turn | 11 | 20 | COLLAPSED |
| 37 | maneuver_roundabout_exit_ccw | 11 | 20 | COLLAPSED (exit shown as enter) |
| 38 | maneuver_roundabout_exit_cw | 11 | 20 | COLLAPSED |

**Summary of real defects (not merely "collapsed but visually fine"):**
- **[P1] merge (#18):** AMAP→5 (slight-right) is the owner's wrong arrow. → fix to STRAIGHT.
- **[P1] off-ramp (#11,#13):** mapped to a 90° hard turn; an off-ramp is a gentle diverge → should be slight.
- **[P2] u_turn_right (#17):** AMAP loses side (→8 left) while HAL keeps 10 — the two outputs disagree.
- **[P2] roundabout family (#19-38):** AMAP path→CAN13, HAL path→CAN20 for the *same* input (disagree); all
  20 variants collapse to one glyph and "exit" shows as "enter". (Direction/around-detail loss is expected;
  the AMAP-vs-HAL disagreement and exit-as-enter are the real issues.)

Other consumers that also collapse to slight (same boundary): Waze keep/exit → slight
[src:WazeHudSource.kt:64-65]; text "keep left/right" → slight [src:NavFormat.kt:53-54].

---

## 5. Current `Maneuver` enum — internal consistency check

`Maneuver` [src:Maneuver.kt] is the neutral "one decision, two outputs" type. For the two outputs to draw the
**same** glyph, the invariant is `toHudIcon(m) == TurnIdMapToCAN[toAmapIcon(m)]`.

| Maneuver | toAmapIcon | `TurnIdMapToCAN[amap]` (expected HUD) | toHudIcon (actual) | Parity |
|---|:--:|:--:|:--:|:--:|
| TURN_LEFT | 2 | 1 | 1 | ✅ |
| TURN_RIGHT | 3 | 2 | 2 | ✅ |
| SLIGHT_LEFT | 4 | 3 | 3 | ✅ |
| SLIGHT_RIGHT | 5 | 5 | 5 | ✅ |
| SHARP_LEFT | 6 | 7 | 7 | ✅ |
| SHARP_RIGHT | 7 | 8 | 8 | ✅ |
| UTURN | 8 | 9 | 9 | ✅ |
| STRAIGHT | 9 | 11 | 11 | ✅ |
| ROUNDABOUT | 11 | **13** | **15** | ❌ cluster=enter-roundabout, HUD=around-turn-left |
| DESTINATION | 15 | 48 | 48 | ✅ |
| CONTINUE | 20 | **12** | **11** | ❌ cluster=follow(顺行), HUD=straight |

- **[P2] ROUNDABOUT parity:** for strict parity `toHudIcon` should be **13** (OEM's own enter-roundabout CAN).
  15 is the "around-roundabout turn-left" glyph. **Locked by test** [src:ManeuverTest.kt: `ROUNDABOUT.toHudIcon()==15`],
  so changing needs a test update + on-car confirm (§7.5).
- **[P3] CONTINUE parity:** `toHudIcon` should be **12** (顺行) for parity; today 11 (straight). Both "keep going"
  — cosmetic. Locked by test.

---

## 6. Icon-decision pipeline & logging (for on-car verification)

Four independent classifier layers feed the decision; each emits a `NEW_ICON` (AMAP) code that becomes a
`Maneuver` via `fromAmapIcon` [src:Maneuver.kt]:
1. **small-icon resource name** → `IconResource.resolve` [src:IconResource.kt]
2. **perceptual signature** (15×15 Hamming ≤18, + NCC fallback) → `ManeuverSignature.classify` [src:ManeuverSignature.kt]
3. **maneuver-text verb** → `NavFormat.maneuverVerbIcon` [src:NavFormat.kt]
4. **pixel-centroid heuristic** → `ArrowClassifier.classify` [src:ArrowClassifier.kt]

Every frame is traced to CSV — columns `small_amap,sig_name,sig_amap,verb_amap,heuristic_amap,final_icon`
[src:NavArrowTrace.kt: `CSV_HEADER`], written by `NavArrowLog.record` [src:NavArrowLog.kt]. **This is the
on-car ground-truth for the fix:** after changing merge/ramp mapping, drive a merge/ramp and confirm
`final_icon` = 9 (straight), not 5, and that the arrow PNG matches.
Pull: `adb pull /sdcard/Android/data/com.byd.clusternav/files/` (grabs `nav_arrow_log_*.csv` + PNG dir).

---

## 7. CONCRETE enrichment plan (do NOT code — ready-to-implement change list)

Two tiers. **Tier A** fixes wrong glyphs (ship first). **Tier B** enriches the neutral enum so distinct
maneuvers are carried/logged even where the drawn glyph must fall back.

### 7.1 New `Maneuver` enum values + their two outputs (values taken from §1/§2 real tables)

> Kotlin `when` in `toAmapIcon`/`toHudIcon` has **no `else`** → adding a value forces a branch in both
> encoders (compile-enforced coverage). Keep the invariant `toHudIcon == TurnIdMapToCAN[toAmapIcon]`.

| New value | Meaning | `toAmapIcon()` | `toHudIcon()` | Why this glyph (real table) | Priority |
|---|---|:--:|:--:|---|:--:|
| `MERGE` | lane merge (no side) | **9** | **11** | no merge glyph in 0..28 → straight. Fixes owner's slight-right bug. | **A (P1)** |
| `RAMP_LEFT` | on/off-ramp left | **4** | **3** | ramp = gentle diverge ≈ slight-left; kills the hard-turn mismap. | **A (P1)** |
| `RAMP_RIGHT` | on/off-ramp right | **5** | **5** | slight-right | **A (P1)** |
| `FORK_LEFT` | Y-split keep left | 4 | 3 | ≈ slight-left (no fork glyph) — alias, improves trace fidelity | B (P3) |
| `FORK_RIGHT` | Y-split keep right | 5 | 5 | ≈ slight-right | B (P3) |
| `KEEP_LEFT` | keep left | 4 | 3 | ≈ slight-left | B (P3) |
| `KEEP_RIGHT` | keep right | 5 | 5 | ≈ slight-right | B (P3) |
| `UTURN_RIGHT` | U-turn right (LHT/rare) | **19** | **10** | uses UNUSED NEW_ICON 19 → CAN 10; makes `UTURN`≡left explicit | B (P2) |
| `ROUNDABOUT_EXIT` | drive out of roundabout | **12** | **24** | uses UNUSED NEW_ICON 12 → CAN 24 | B (P2) |

Notes:
- `MERGE`, `FORK_*`, `KEEP_*`, `RAMP_*` **reuse** codes already owned by `STRAIGHT`(9)/`SLIGHT_*`(4/5). They are
  **encode-only** — see §7.3 (do NOT add them to `fromAmapIcon`).
- `UTURN_RIGHT`(19) / `ROUNDABOUT_EXIT`(12) **extend** the emitted vocabulary → they *do* get a `fromAmapIcon`
  entry and a round-trip test row (§7.3).
- Consider renaming existing `UTURN` → `UTURN_LEFT` for symmetry (optional; touches call sites + tests).

### 7.2 Immediate merge fix (Tier A — the owner's bug)

Because `NEW_ICON 0..28` has **no** merge glyph (§1.1), `merge → STRAIGHT` (`AMAP 9 / CAN 11`) on **both** outputs.

Exact edits:
- **[src:ManeuverSignature.kt:230]** `name.contains("merge") -> 5`  →  `-> 9`  *(cluster stops drawing slight-right)*
- **[src:ManeuverSignature.kt] `nameToHal`** — `merge -> 11` is already straight; **no change** (this is why the
  HUD was already "less wrong" than the cluster).
- **[src:IconResource.kt:27]** `"merge_right" to 5, "merge_left" to 4`  →  `"merge_right" to 9, "merge_left" to 9`
- **[src:IconResource.kt]** the generic `"merge" to 5` entry (in the straight/continue line) → `"merge" to 9`
- **[src:NavFormat.kt]** in `maneuverVerbIcon`, add a merge branch **before** the turn/slight branches so
  `"merge …"`/`"nhập làn"` text → straight, not a stray "left onto" turn match:
  `RE_MERGE = Regex("nhập làn|nhap lan|merge")` → `RE_MERGE.containsMatchIn(t) -> 9`.
  (Trade-off: `merge right`/`merge left` text also → straight. Acceptable: the owner's real case is a
  straight-with-joining-lane; slight would reproduce the bug.)

If Tier B is adopted, route these through the enum instead: classifiers return `Maneuver.MERGE` and encode via
`toAmapIcon()/toHudIcon()` (= 9/11) — same result, cleaner.

### 7.3 Off-ramp fix + `fromAmapIcon` / round-trip impact (Tier A/B)

- **Off-ramp (Tier A, P1):** add ramp rules **before** the `normal_left/right` lines so off-ramps stop becoming
  hard turns:
  - `nameToAmap` [src:ManeuverSignature.kt:~224 (after sharp, before slight/fork)]:
    `name.contains("ramp") && name.contains("left") -> 4` ; `… "right") -> 5`
  - `nameToHal` [src:ManeuverSignature.kt:~246 (before normal_left/right)]:
    `name.contains("ramp") && name.contains("left") -> 3` ; `… "right") -> 5`
  - `IconResource` ramp entries [src:IconResource.kt:28] already = slight (4/5) → **keep** (now consistent with
    signature).
- **`fromAmapIcon` [src:Maneuver.kt]:**
  - **Do NOT** add `MERGE/FORK_*/KEEP_*/RAMP_*` — they reuse 9/4/5, which must invert to the canonical
    `STRAIGHT`/`SLIGHT_LEFT`/`SLIGHT_RIGHT`. The round-trip test iterates `{2,3,4,5,6,7,8,9,11,15,20}` and asserts
    `fromAmapIcon(x).toAmapIcon()==x` [src:ManeuverTest.kt] — this stays green because each of those codes keeps
    exactly one canonical owner. Merge/fork/keep/ramp are **one-way** (encode only); the cluster-lane invariant
    (glyph preserved) is unaffected.
  - **If** `UTURN_RIGHT`(19)/`ROUNDABOUT_EXIT`(12) are added: add `12 -> ROUNDABOUT_EXIT`, `19 -> UTURN_RIGHT`
    to `fromAmapIcon`, and extend the round-trip test list to include 12 and 19. Also update the null-guard test
    (currently asserts `fromAmapIcon(1)=null`, `(28)=null` [src:ManeuverTest.kt]) — 12/19 are no longer "outside
    vocabulary". These two also need classifier rules to actually *emit* 19/12 (`u_turn_right`→19,
    `roundabout_exit`→12) — otherwise they are dead enum values.

### 7.4 Roundabout-with-exit enrichment (Tier B, P2 — richer, optional)

The CAN table has **"take exit N"** glyphs (CAN 25..44) unused by us (§2.1). To reach them the AMAP broadcast
must carry `ROUNG_ABOUT_NUM` (1..10) alongside `NEW_ICON 11/12` (or 17/18). `NavFormat.roundaboutExit()` already
parses N from text [src:NavFormat.kt] — verify the frame builder / `ClusterBroadcaster` actually put
`ROUNG_ABOUT_NUM` into the broadcast (out of scope of this doc — flag for the implementer). There is **no HUD
equivalent** for the exit number (the direct CAN write can send 25..44, but confirm the HUD renders those).

### 7.5 Pre-existing enum parity + on-car probes

- **[P2] ROUNDABOUT** [src:Maneuver.kt / ManeuverTest.kt]: set `toHudIcon` 15 → **13** for parity with the
  cluster (CAN 13 = OEM enter-roundabout). Requires updating `ManeuverTest` (`==15` → `==13`) and the
  "roundabout≠destination" assertions. **Confirm on-car first:** write `GUIDE_INFO_SIMPLE=13` vs `=15` and see
  which draws a proper roundabout on the windshield HUD.
- **[P3] CONTINUE:** `toHudIcon` 11 → **12** (顺行) for parity. Cosmetic; confirm on-car.
- **[GUESS] CAN 4 & 6** (§2): the only unmapped ids in 1..49. **Probe on-car** (with cluster nav active, write
  `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` = 4, then 6, observe the glyph). If either is a merge/ramp/keep glyph, it
  becomes the *dedicated* target for §7.1/§7.2 instead of the straight/slight fallback. Do **not** assume their
  meaning until measured.

### 7.6 Contract checklist (must stay green — [src:ManeuverTest.kt], [src:HudManeuverEncodingTest.kt])

- `toAmapIcon`/`toHudIcon` exhaustive over the enlarged enum (add branches for every new value).
- `toAmapIcon` unchanged for the 11 existing values (test §`toAmapIcon khớp…`).
- `toHudIcon` unchanged for existing values unless §7.5 is adopted (then update the two locked rows).
- Round-trip `{2,3,4,5,6,7,8,9,11,15,20}` unchanged; only extend if 12/19 added (§7.3).
- "mọi cua rẽ KHÔNG encode ra đi-thẳng": do **not** add `MERGE` to that turn list (merge→straight is intended);
  `FORK_*/KEEP_*/RAMP_*`→slight (4/5,3/5) are safe if listed.
- HUD encodes via `Maneuver.toHudIcon()` (not the AMAP code) — unchanged by this plan [src:HudManeuverEncodingTest.kt].

---

## 8. References

- **RE — AMAP bridge (authoritative NEW_ICON/CAN):**
  `~/Library/Caches/clusternav-re/diagnostic-amap/auto/sources/com/example/amapservice/AmapService.java`
  — `TURN_STRING` :66 · `TurnIdMapToCAN` :67 · NEW_ICON read :358 · `<29` guard :603 · roundabout offset
  :605-614 · `GUIDE_INFO_SIMPLE_SET` write :619.
- **RE — feature id + mapper:** `.../sysimg/jadx-DiCarServer/sources/com/byd/feature/instrument/Instrument.java`
  (`INSTRUMENT_GUIDE_INFO_SIMPLE_SET = "0x43F01010"`) · `.../InstrumentMapper.java`.
- **RE — cross-ref (merge first-class):** `.../sysimg/tmap_c1/out/sources/com/skt/tmap/engine/navigation/data/Turn.java`
  (`MergeToLeft(8)`, `MergeToRight(9)`).
- **Repo — neutral enum + encoders:** `core/.../navigation/Maneuver.kt` (`toAmapIcon`/`toHudIcon`/`fromAmapIcon`, header :16).
- **Repo — classifiers:** `core/.../navigation/ManeuverSignature.kt` (`nameToAmap` :218-234 incl. `merge->5` :230;
  `nameToHal` :238-254) · `app/.../IconResource.kt` (`NAME_TO_AMAP` :26-32, merge :27, ramp :28, fork :29) ·
  `core/.../navigation/NavFormat.kt` (`maneuverVerbIcon`, keep-left/right :53-54, `roundaboutExit`) ·
  `core/.../navigation/ArrowClassifier.kt`.
- **Repo — registry (38 names):** `core/.../navigation/ManeuverRegistry.kt` (fork_left :16, fork_right :18).
- **Repo — trace/logging:** `core/.../navigation/NavArrowTrace.kt` (`CSV_HEADER`) · `app/.../NavArrowLog.kt`.
- **Repo — other consumer:** `app/.../modules/wazehud/WazeHudSource.kt` (:64-65 keep/exit → slight).
- **Repo — contracts:** `core/src/test/kotlin/.../ManeuverTest.kt` · `app/src/test/java/.../HudManeuverEncodingTest.kt`.
- **Prior trace:** `docs/diagnostics/research-backlog-2026-08-03.md` :168, :186.

---

## 9. ADDENDUM — owner steering (2026-08-14): tunnel + roundabout-exit + fuller "OpenBYD-level" set

Owner: *"OpenBYD có bộ icon đầy đủ hơn mình; trước app mình còn hiện icon HẦM (sắp vào hầm), giờ không biết còn không; vòng xuyến cần làm chi tiết để biết LỐI RA nào."*

All requested glyphs **exist on the cluster** (§1/§2) and are currently **UNUSED** by our enum — so these are add-backs, not new firmware needs:

| Owner ask | Glyph | NEW_ICON → CAN | Change needed | Emit source (must verify GMaps exposes it) |
|---|---|:--:|---|---|
| **Tunnel** ("sắp vào hầm") | 进入隧道 enter tunnel | **16 → 49** | add `Maneuver.TUNNEL` (toAmap 16 / toHud 49) + classifier rule (icon-name/text `tunnel`/`hầm`) | GMaps small-icon name or notification text. **Owner says app used to show it → likely GMaps DOES expose.** Confirm via NavArrowLog `small_amap`/`sig_name` when passing a tunnel. |
| **Roundabout — which exit** (lối ra N) | exit-roundabout + exit number | 11/12 (+ `ROUNG_ABOUT_NUM` 1..10) → **CAN 25..34 (CCW)** | carry exit N as a DATA field (not a new enum value): `NavFormat.roundaboutExit()` already parses N [src:NavFormat.kt]; **wire `ROUNG_ABOUT_NUM` into the AUTONAVI broadcast** in the frame builder + emit `NEW_ICON 12` (exit) when N known | GMaps roundabout text "lối ra thứ N / exit N". Verify the broadcast currently omits ROUNG_ABOUT_NUM (suspected). |
| Service area | 到达服务区 | 13 → 46 | add `Maneuver.SERVICE_AREA` (13/46) | GMaps rarely emits — tier C |
| Toll station | 到达收费站 | 14 → 47 | add `Maneuver.TOLL` (14/47) | GMaps rarely emits — tier C |
| Waypoint arrival | 到达途经点 | 10 → 45 | add `Maneuver.WAYPOINT` (10/45) | GMaps via-point — tier C |
| U-turn right | 右转掉头 | 19 → 10 | `UTURN_RIGHT` (already §7.1 Tier B) | LHT/rare |

**Priority (owner-driven):** TUNNEL and ROUNDABOUT-EXIT-NUMBER are **Tier A** (explicit owner asks). Service/toll/waypoint = tier C (add the enum values cheaply for completeness/logging, but GMaps seldom provides them).

**⚠ CAVEAT (no-assumptions — the key unknown):** the glyphs are proven available *on the cluster*, but we can only DRAW them if **GMaps EXPOSES** the info to us (small-icon name / notification text / signature). Tunnel + roundabout-exit-number are the two that need an on-car **source-availability check** (drive through a tunnel + a numbered roundabout, read `NavArrowLog` columns). If GMaps doesn't expose it → it's a **source limit, not a cluster limit** (we cannot invent it). This distinguishes "app used to show tunnel" (GMaps exposed it then) vs "collapsed in our refactor" (we dropped it) — the log will tell which.

**TUNNEL regression check:** current `Maneuver` enum has no TUNNEL and `toAmapIcon` never emits 16 → we do NOT show tunnel now. If the owner saw it "before", it was dropped when the neutral-enum refactor narrowed the vocabulary to {2,3,4,5,6,7,8,9,11,15,20}. Add-back is cheap IF GMaps still exposes it.
