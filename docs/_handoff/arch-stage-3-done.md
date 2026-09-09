# Arch Stage 3 (B3) — DONE

> Local (chưa push). Build XANH; :core 921 + :app 501 = 1422 unit tests, 0 fail. Cast suites (84) + golden (11) XANH. Brick-vector: launcher side CLOSED (guard proven-live).

## Đã làm — FreeformSeedPolicy = single sanctioned writer
- **`:core system/FreeformSeedPolicy.kt`** (PURE) + **`:app system/FreeformSeedStore.kt`** (MarkerStore qua SharedPreferences `clusternav_state`/`freeform_state` — **CHUNG marker với cast**). API: ensureSeed/unseed/seedMarked/writeDisplaySize/writeDisplayDensity/resetDisplayAll. Marker discipline: **commit-trước-mutate**, `FF_USER_REMOVED` terminal, `resetDisplayAll` = clearer DUY NHẤT. Lệnh **byte-identical**: `settings put global enable_freeform_support 1` / `force_resizable_activities 1`; `wm size {W}x{H} -d {id}`; `wm density {dpi} -d {id}`; reset.
- **Launcher**: gỡ hằng `FreeformLaunch.freeformFlagCmds` (chỉ test dùng, KHÔNG runtime). Đường sanctioned = `FreeformSeedStore.forLauncher(ctx)` → policy → launcherSeam → 1 ShellTransport. Sẵn cho B6.
- **Guard B siết**: assertion mới "launcher chỉ ghi state bền qua FreeformSeedPolicy" — **proven-live** (chèn writer rogue → guard FAIL 2/4 → gỡ → xanh).

## Consolidate cast (table)
- ✅ **CONSOLIDATED**: `CastShell.ensureFreeformSeed/unseedFreeform/freeformSeedMarked` → delegate FreeformSeedPolicy (byte-identical, marker giữ, latch RAM giữ; cast suites xanh).
- ⏳ **DEFERRED (byte-UNTOUCHED, TODO on-car, 12 marker greppable)**: `CastGeometryController.ensureFreeformFlags` (seed LIVE marker-less — thêm marker = đổi hành vi, không verify được) + TOÀN BỘ `wm size/density` geometry (CastGeometryController/DisplayConfigurator/CastDensityControl/CastShell.forceDisplaySize/ClusterCast dead) + CarExecClusterProjectionCatalog (T10 non-runtime).

## ⚠ ON-CAR PENDING (state bền — PHẢI reboot vật lý, KHÔNG nhận adb reboot)
1. Seed/unseed hiệu lực sau ignition off/on; marker sống qua reboot/reinstall.
2. CastShell consolidated path byte-behaves y hệt on-car (freeform sống sau power-cycle).
3. Consolidate DEFERRED sites vào FreeformSeedPolicy sau khi cluster E2E verify được.
4. `wm size/density` persist `display_settings.xml` + resetDisplayAll clears, qua reboot.

## HAND-OFF cho Stage 4 (B4 — input daemon)
- Chạm hiện tại: `VdAppHost.onTouchEvent` fire `input -d $displayId tap` (ACTION_DOWN+UP, double-fire, ~75ms/event lag). B4: daemon thường trú `injectInputEvent` qua socket (`localabstract`, uid-2000 `INJECT_EVENTS`), `MotionEvent.setDisplayId`; fallback `input -d`. **Chạm KHÔNG đi qua queue lệnh** (ShellTransport) — chỉ lifecycle daemon qua queue.
- Daemon = dexed jar chạy `CLASSPATH=... app_process / <Main>` (lệnh lifecycle). Emulator loopback dadb ko nối phiên này → daemon start (dùng dadb) chỉ verify unit; E2E on-car/khi loopback lên.
- Golden + cast strings vẫn phải byte-stable (chạy LauncherCommandGoldenTest + cast suites).
