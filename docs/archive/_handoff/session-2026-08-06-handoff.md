# Session handoff — 2026-08-06 (Cast profiles/bubble + boot autostart + Waze HLP + Update UI + widget self-grant)

> Off-car work only. Everything below is JVM-test + build verified; **no on-car (Stage 11) verification done — do not treat any of it as vehicle-PASS.**
> Env: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17` before gradle. Repo/remote: `dangkhoi/byd-cluster` (main).

## State
- Branch `main`, **ahead of `origin/main` by 2 commits, NOT pushed**:
  - `e0a49c4` — Cast (T1–T7) + Waze HLP fix + v1.03 simplified-cast migration.
  - `2c3d348` — restore GitHub check-for-update UI + VietMap widget self-grant.
- Working tree clean. Full JVM suite green: **core 653 + app 290 + car 11 = 954, 0 failures**; `:app:assembleDebug` OK.
- Verify command: `./gradlew :core:test :app:testDebugUnitTest :car-integration:test :app:assembleDebug`.

## What shipped this session (off-car)
1. **Cast bubble/profiles/split (spec `docs/specs/cast-freeform-resize-split.html`, T1–T4):**
   - Autostart split single-owner (`FloatingBubbleService`), sequential L→R (right only after `CastingSplit(left)`), atomic one-shot — fixes white-bubble/state-wipe.
   - Bubble = 3 equal horizontal translucent zones Trái·Phải·Full (label FULL="Full", `ZONE_MIN_DP=48`, translucent fills).
   - Split ratios reduced to 50/50, 30/70, 70/30 (legacy → 50).
   - 7 per-app geometry profiles (`CastProfile` FULL + L/R×{50,30,70}) storing bounds+DPI; per-slot resize UI in split; restore on re-cast. DPI is display-global in split (OS limit) — "last edit wins"; bounds are per-task.
2. **Boot autostart (T7, R10/D6 in same spec):** `openProjection()` idempotent; `FloatingBubbleService` opens projection on boot when autostart enabled (bounded retry 5×/3s for adb-loopback timing) and owns projection lifecycle; `MainActivityCastController.onDestroy` skips close when autostart on. Autostart-off boot = unchanged (gauges preserved).
3. **Waze HLP/1 nav+speed (handoff `docs/_handoff/waze-hlp-nav-speed-fix-2026-08-05.md`):** read logcat via privileged dadb shell (uid 2000) not app-uid in-process (couldn't see Waze logs); poll+dedup by `ts`; level `:I`→`:V`; speed limit no longer gated on `navigating`. Parser verified against official HLP/1 spec (correct). `org.json` added test-only.
4. **Check-for-update UI restored:** `DiagActivity` "⬇ Kiểm tra cập nhật" → GitHub Contents API `apk/` → confirm → download → install via dadb `pm install -r` → restart. Reachable via cast panel → "Chẩn đoán".
5. **VietMap widget self-grant:** on `BIND_UI_UNAVAILABLE`, app self-runs `appwidget grantbind --package com.byd.clusternav --user 0` via `LocalDeviceShell.grantAppWidgetBind` (dadb, off-main) then retries bind once. Manual message = last-resort fallback. Flipped the manual-only R12 policy per owner decision (spec `docs/specs/vietmap-widget-bridge.html` R2 + callout).

## Reviews / gates done
- Senior review (opus) APPROVED for cast T1–T4, boot T7, Waze fix (patched a P2 atomic race + P1 main-thread I/O + P2 restart-stall). Widget/update change: self-reviewed (small), tests green.
- Pre-commit security scans CLEAN for both commits (vehicle IPs redacted to `<vehicle-ip>` in 2 handoff docs; keystore gitignored; no secrets/PII).

## NEXT SESSION — on-car (Stage 11), highest value first
1. **Push?** Owner hasn't authorized push to `main` yet. If shipping via update-check, must `git push origin main` (pushing to main needs explicit owner OK; scan already covers the diff).
2. **Release APK for update-check:** `apk/` newest release is `1.02-release` (1.03 is debug-only). To exercise the updater, bump version, `assembleRelease`, drop `apk/ClusterNav-<ver>-release.apk`, commit+push main. Filename version must be > installed; same signing key (`release.keystore`) or `-r` fails.
3. **Verify on SL6/Seal (all pending):**
   - Cast: autostart split → both halves cast, bubble 2 zones BLUE; per-slot resize persists per profile across re-cast; boot (no app open) → autostart casts (needs freeform alive = one power-cycle after flags, + overlay perm + app opened once for BOOT_COMPLETED delivery).
   - Waze: `adb -s <vehicle-ip>:5555 logcat -d -s WazeHUD:V -t 5` shows `{"v":1,"t":"s",...}`; set speed-source=Waze → limit shows while driving; nav-source PREFER_WAZE → lane/dist/road. Confirm `logcat -t -s TAG:V` arg validity on DiLink3.
   - Widget: "Bind hai nguồn VietMap" → self-grant + bind, `NOT_BOUND`→`Fresh` (needs loopback up; grantbind honored by uid 2000).
   - Update: Chẩn đoán → Kiểm tra cập nhật → detects/downloads/installs (needs public repo + release APK pushed + loopback).
4. **Deferred:** T5 (CP/AA geometry editor via wm size/overscan/density — safe, display-global) — owner default No; freeform is unsafe for CP/AA (surfaceflinger crash).

## Gotchas / notes
- All privileged actions go through dadb loopback `localhost:5555` (uid shell 2000): Cast am/wm/pm, freeform `settings put global`, Waze logcat read, APK install, widget grantbind. If loopback is down at boot, these degrade (bubble shows, but cast/grant/read fail).
- `VietMapWidgetBridge.kt` is 541 LOC (>500) — pre-existing v1.03 debt, not from this session; candidate for a split later.
- `CastBubbleProjection.kt` 541-ish, `simplified/SimpleCastCoordinator.kt` 498 — watch the 500 line on further edits.
