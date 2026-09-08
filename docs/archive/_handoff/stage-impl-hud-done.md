# Stage handoff — impl HUD street-name (T4 + T5-HUD)

> Session: `impl_hud_streetname` · 2026-07-24 · off-car · continuation of Pass-2 impl wave.
> Spec: `docs/specs/freeze-proof-cluster-switch.html` (D4, T4, T5, F7, F8).
> Stage-1 (freeze swap) files UNTOUCHED: `ClusterCast.kt`, `CastShell.kt`, `FakeShell.kt`, `CastSwapTest.kt`.

## Files changed

| File | Change |
|------|--------|
| `app/src/main/java/com/byd/clusternav/NavFormat.kt` | `fitRoadName` parameterized + NFC-normalize; added `HUD_ROAD_MAX_UNITS`. |
| `app/src/main/java/com/byd/clusternav/ClusterBroadcaster.kt` | `pushHud` pushes abbreviated `hudRoad`, dedup on it. |
| `app/src/test/java/com/byd/clusternav/NavFormatTest.kt` | +13 tests (T5 HUD) + `assertFalse` import. |
| `docs/specs/freeze-proof-cluster-switch.html` | Reviewer Log "Pass 2 — impl (HUD)". |

`BydHal.kt` — READ ONLY (confirmed `writeNavFrame` writes `road.toByteArray(Charsets.UTF_16LE)` into `INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET`). Not modified.

## Functions / symbols changed

- `NavFormat.fitRoadName(road)` → **`NavFormat.fitRoadName(road: String, maxUnits: Int = ROAD_MAX_UNITS): String`**
  - Default arg keeps existing cluster callers (marquee/`roadWindow` path) unchanged (backward-compat).
  - NFC-normalizes raw `road` (`java.text.Normalizer.Form.NFC`) at the top of the function — before
    `cleanRoadName`, length measurement, `take()`, and dotted-form building (F8). Dotted algorithm and
    `KEEP_CLASS` handling unchanged. Output length is always `<= maxUnits`.
- `NavFormat.HUD_ROAD_MAX_UNITS = 7` — **new** `const val` (F7). Comment forbids bumping to 8 until the real
  HUD buffer is traced on a car with the new HUD (overflow = firmware drops the name = the bug being fixed).
- `ClusterBroadcaster.pushHud(ctx, s, seg)` — computes
  `val hudRoad = NavFormat.fitRoadName(lastCleanRoad, NavFormat.HUD_ROAD_MAX_UNITS)`, passes `hudRoad` (not
  full `lastCleanRoad`) to `BydHal.writeNavFrame`, and dedups + stores `lastHudRoad` on `hudRoad`.
  `clearHud` reset of `lastHudRoad = " "` kept consistent.

## Tests added (13, in `NavFormatTest.kt`)

1. `fitRoadName 'Trần Trọng Kim' @7 → dotted 'T.T.Kim' (len 7)`
2. `fitRoadName 'Võ Nguyên Giáp' @7 → acronym 'VNG' (dotted 'V.N.Giáp' là 8 > 7)`
3. `fitRoadName 'Võ Nguyên Giáp' @8 → dotted 'V.N.Giáp'`
4. `fitRoadName 'Nguyễn Hữu Cảnh' @7 → acronym 'NHC'`
5. `fitRoadName 'Quốc lộ 1A' → 'QL1A' (viết tắt loại đường, cả @7 và default)`
6. `fitRoadName 'hầm Thủ Thiêm' @7 → giữ từ-loại 'hầm TT'`
7. `fitRoadName single word — ngắn giữ nguyên, dài thì cắt`
8. `fitRoadName empty-blank → empty`
9. `fitRoadName NFC-normalizes NFD input — length correct (không viết tắt oan)`  ("Hà Nội": NFD 9 units → measured as NFC 6 → not abbreviated)
10. `fitRoadName NFC-normalizes NFD input — take(1) giữ dấu khi viết tắt`  ("Ông Ích Khiêm" NFD → "ÔÍK", not "OIK")
11. `fitRoadName byte-budget ≤ 2×maxUnits (UTF-16LE, đúng buffer BYTE của cụm-HUD)`
12. `fitRoadName default-arg == gọi tường minh ROAD_MAX_UNITS (backward-compat cụm)`
13. `HUD_ROAD_MAX_UNITS = 7 = ROAD_MAX_UNITS (F7 — an toàn theo đo xe, KHÔNG 8)`

## Test count

- Command: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ; ./gradlew --offline testDebugUnitTest`
- Result: **BUILD SUCCESSFUL** — total **207** tests, **0** failures, **0** errors, **0** skipped.
- Baseline before this stage: 194 → +13 new HUD tests = 207. `NavFormatTest` suite: 12 → 25.

## Not done (out of this session's scope)

- T6 (version bump / release+debug build / commit / on-car validation).
- On-car validation of the actual HUD buffer size (OQ2) — required before raising `HUD_ROAD_MAX_UNITS` past 7.
