# WazeMod HLP/1 nav+speed — root cause + fix (off-car) 2026-08-05

> Owner-requested "điều tra sâu, review lại code để fix". Fixed off-car; final proof is ON-CAR (not self-certified).
> Protocol verified against https://wazemod.chisadin.id.vn/tai-lieu/esp32 (HLP/1).

## What was broken (root causes)
1. **[dominant] In-process logcat couldn't see Waze's logs.** `WazeHudSource` ran `Runtime.exec("logcat …")` as the APP uid. On Android 7+ an app-uid logcat sees only its OWN logs unless it holds effective `READ_LOGS`; WazeMod logs from another process (com.waze). Code relied on a manual `adb pm grant READ_LOGS` that evidently wasn't effective on the vehicle → read nothing → nav+speed dead.
2. **logcat level filter `WazeHUD:I` too narrow.** Producer's LogSink is a debug stream; `:I` (Info+) can drop debug/verbose lines. Doc's own test uses `adb logcat -s WazeHUD` (all levels).
3. **Speed gated behind `navigating`.** Listener early-returned on `!navigating`, so the speed LIMIT (delivered even while just driving, no route) never fed.

Parser was NOT the bug: tag `WazeHUD`, envelope `{"v":1,"t":"s",...}`, and all fields match the spec.

## Fix
- **Transport → dadb shell (uid 2000).** `WazeHudSource(shell: (String)->String?)` polls `logcat -d -v raw -s WazeHUD:V -t 60` every 900ms via `SimpleCastRuntime.coordinator(app).executeShell`; de-dups by monotonic `ts` (exact-match drop; a lower ts = producer restart = accepted). uid-2000 has full log access → no dependence on the app's READ_LOGS. Best-effort `pm grant READ_LOGS` on the poll thread (belt-and-suspenders, off main → no ANR).
- **Level `:I` → `:V`.**
- **Gate fix** (`NavNotificationListener`): master `Prefs.enabled` only; NAV block gated on `navigating`+navMode+SourceArbiter; SPEED block gated ONLY on `speedSource==SPEED_WAZE && lim>0` (independent of navigating).
- Tests: `WazeHudSourceTest` (10) — real-spec sample parse, full field set, reject wrong-version/non-state, speed-without-route, turn map, pollOnce newest+dedup, noise skip, empty dump, producer-restart recovery, toNavState. `org.json:json:20260719` added test-only (android.jar stub throws on JVM).

Files: `app/.../modules/wazehud/WazeHudSource.kt` (rewritten), `app/.../NavNotificationListener.kt` (startWazeHudSource + listener), `app/build.gradle.kts` (test dep), `app/src/test/.../wazehud/WazeHudSourceTest.kt` (new).

## Verified off-car
- core 653 / app 290 JVM tests, 0 failures · `:app:assembleDebug` OK · all files ≤500 · senior review (opus) APPROVED (found+fixed 1 P1 main-thread I/O, 1 P2 restart-stall).

## REQUIRED config for it to show on-car (user-facing)
1. WazeMod installed; `hud_link_log=true` (default) in its SharedPreferences `waze_hud_gw`.
2. In ClusterNav: **Speed source = Waze** (default is VietMap → speed WON'T show until switched).
3. Nav source = AUTO (default) works, but with GMaps/VietMap also running the SourceArbiter gives the lock to whoever feeds first (6s) — for reliable Waze nav set **PREFER_WAZE**.
4. Master push switch (Prefs.enabled) ON; notification listener bound (the source starts on listener connect).

## On-car verification checklist (NOT done — Stage 11)
- [ ] `adb -s <vehicle-ip>:5555 logcat -d -s WazeHUD:V -t 5` shows `{"v":1,"t":"s",...}` lines (producer alive).
- [ ] With WazeMod driving (no route): cluster speed-limit sign follows `lim`. (speed-source=Waze)
- [ ] With a route: cluster lane arrow/distance/road follow `trn/dst/st2`; ETA from `eta/rmin/rkm`. (nav-source PREFER_WAZE)
- [ ] `logcat -s WazeHudSource:I` shows "polling … every 900ms" and no repeated "poll failed".
- [ ] Confirm `logcat -d -t N -s TAG:V -v raw` args behave on this DiLink3 build (arg validity).
