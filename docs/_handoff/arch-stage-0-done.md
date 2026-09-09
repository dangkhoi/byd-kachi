# Arch Stage 0 (B0) — DONE

> Commit `4918ead` (local, chưa push). Build + unit tests GREEN: :core 873, :app 494 (1367 total, 0 fail). 17 test mới.

## Đã làm
Safety net test-only (KHÔNG đụng production): byte-lock các lệnh shell proven + 2 guard test.

**Test mới (:core, package com.byd.clusternav.launcher):**
- `LauncherCommandGoldenTest.kt` (9 test) — EXACT byte-lock `FreeformLaunch` (launchCmd/resizeCmd/resolveCmd/fullscreenCmd/freeformFlagCmds) + `ShellAppLauncher` (openInSlot/moveToSlot/closeSlot/isFreeformAvailable emitted sequence).
- `LauncherWindowingGuardTest.kt` (5 test) — Guard A + golden-string source-pin cho VdAppHost/SlotAppHost.
- `PersistentWindowStateWriterGuardTest.kt` (3 test) — Guard B.

## Lệnh proven đã chốt byte (Stage sau PHẢI giữ y)
- `am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --display <id> --windowingMode 5 -n '<comp>'` (freeform seed)
- `am start --display <id> --windowingMode 1 -f 0x20000000 -a … -c … -n '<comp>'` (fullscreen)
- `am task resize <task> <l> <t> <r> <b>`
- `cmd package resolve-activity --brief -a MAIN -c LAUNCHER <pkg>`
- `settings put global enable_freeform_support 1` / `force_resizable_activities 1`
- Cast builders (AppMover/CastGeometryController/DisplayConfigurator/CastDensityControl/…) — đã lock sẵn (CastSwapTest/SimpleCastCoordinatorTest/…).

## Guard results
- **Guard A (launcher không target display≥1): PASS.** Builders thuần target display 0. VdAppHost/SlotAppHost emit `--display <dynamic>` = id VirtualDisplay TỰ TẠO (secondary riêng để render app trong ô, KHÔNG phải cụm/display 1) — không có literal cluster target. Pinned + flag Stage 1.
- **Guard B (single-writer state bền): documented MULTIPLE writers hôm nay** (chờ B3 gộp về 1):
  - freeform flags: `FreeformLaunch.kt` (hằng, CHƯA wire runtime), `CastGeometryController.kt`, `CastShell.kt`, `CarExecClusterProjectionCatalog.kt`
  - `wm size`: `CastGeometryController.kt`, `DisplayConfigurator.kt`, `CastShell.kt`
  - `wm density`: `CastGeometryController.kt`, `DisplayConfigurator.kt`, `CastDensityControl.kt`, `CastShell.kt`, `ClusterCast.kt`, `CarExecClusterProjectionCatalog.kt`
  - (reads/`wm * reset`/`settings get` đã loại đúng.)

## HAND-OFF cho Stage 1 (B1 — gộp transport)
- **3 đường dadb cần gộp về 1 `ShellTransport`**: `app/.../launcher/DadbShell.kt` · `app/.../modules/clustercast/…DadbSimpleCastShell` · `app/.../modules/clustercast/CastShell.kt` (adb path cũ). Mỗi cái mở kết nối `localhost:5555` riêng.
- `DadbShell.run()` HIỆN không synchronized (chỉ `conn()`) — nguồn đua; consumer serialize sẽ fix.
- **Lệnh inline cần route qua builder thuần** (để byte-lock được, khớp Guard/golden): `VdAppHost.maybeLaunch()` (`am start --display $displayId --windowingMode 1 …` + `am force-stop $p` + resolve + `input -d $displayId tap`) và `SlotAppHost.tryShellStart()` (`am start --display $vd --windowingMode 1 -n '$comp'`) → dùng `FreeformLaunch.launchCmd(comp, vdId)`.
- **KHÔNG regress golden strings** — chạy `LauncherCommandGoldenTest` sau mọi thay đổi.
- Seam hiện tại là `(String)->String` (probe `DadbShell` OK → `ShellAppLauncher`, else `IntentAppLauncher`). Giữ seam, thay implementation bên dưới.
