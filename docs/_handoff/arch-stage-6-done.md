# Arch Stage 6 (B6) — DONE

> Local (chưa push). Build XANH; full 5-module 1652 test, 0 fail (+27). 0 cast file đụng. Cold-boot mount = on-car pending.

## Đã làm — auto-start (tôn trọng ràng buộc VD-surface)
- **Ràng buộc**: VdAppHost VD backed bởi SurfaceView của Activity → service headless KHÔNG mount được. Kachi LÀ HOME + B5a restore+mount trong Activity lúc init. Nên service chỉ lo **surface-independent**.
- **`KachiAutostartService` + `KachiAutostart.runBoot`** (:app): (1) seed freeform qua `FreeformSeedStore.forLauncher().ensureSeed()` (tôn trọng FF_USER_REMOVED); (2) set Kachi HOME (`cmd package set-home-activity` chỉ khi chưa); (3) tính+log cast-coordination plan; (4) `am start` đảm bảo HOME lên (che ca MY_PACKAGE_REPLACED) → **Activity mới restore+mount VD slot**. Mọi lệnh qua launcher seam (ko `--display` → gate ALLOW), degrade-safe.
- **`AutostartGate` (pure :core)** — tổng quát hoá guard in-flight (AtomicBoolean CAS) + cooldown 30s của VietMapAutostart (KHÔNG đụng file on-car đó). Service = single-worker CAS + latestStartId + startForeground-first.
- **`LauncherBootPlan` (pure :core)** — partition slot-app thành mount vs skippedToCast (castOwns = `!AppLocationRegistry.isCastable`). Wired REAL vào `LauncherWindows.seedLocations()` (chỉ seed app castable; registry rỗng lúc boot ⇒ y như trước; cast active ⇒ launcher ko đè location cast).
- **Pref** `WorkspacePrefs.launcherAutostart()` (key launcher_autostart, default TRUE) kill-switch. Chưa có UI toggle (ngoài scope B6).
- **Manifest**: `<service .KachiAutostartService specialUse ko-exported>`; KHÔNG thêm receiver (dùng RebindReceiver có sẵn nghe BOOT_COMPLETED + MY_PACKAGE_REPLACED → gọi startForBoot); KHÔNG thêm perm (RECEIVE_BOOT_COMPLETED + FOREGROUND_SERVICE đã có).

## Test (27 mới)
AutostartGateTest 7 (idempotency red-green) · LauncherBootPlanTest 5 (cast-skip) · KachiAutostartServiceWiringTest 15 (source-boundary: cả 2 boot entry start service; service là caller runBoot DUY NHẤT; foreground-first; các step; gated+degrade-safe; KHÔNG VD/VdAppHost trong service; HOME trivial; seedLocations skip cast; pref default-ON; manifest specialUse ko-exported; ko dup perm).

## ⚠ ON-CAR PENDING
- Cold-boot: VD mount slot (cần surface thật + dadb loopback); set-home-activity + am-start chạy qua dadb uid-shell thật (emulator ko loopback → seam trả "" degrade-safe); freeform seed hiệu lực chỉ sau ignition off/on vật lý.

## HAND-OFF cho Stage 7 (consolidate)
- Full 5-module test + senior review loop (đọc spec/handoff, verify từng B0-B6 deliverable + boundary shape + Context7 tech-freshness) + security scan tổng + sync docs: `docs/README.md` index, `docs/PROJECT-BACKLOG.md` (ARCH→done, X1 partial, cast follow-ups on-car), `.kiro/steering/project-context.md` (kiến trúc mới: ShellTransport/dispatcher/registries/FreeformSeedPolicy/input daemon/HomeViewModel/AppContainer/autostart). Viết SDD `docs/specs/kachi-architecture.html` nếu chưa.
- **Follow-ups on-car (gom từ B2-B6)**: cast STOP shared-queue priority + cast-side AppLocationRegistry (B2b); consolidate cast geometry writers vào FreeformSeedPolicy (B3, 12 TODO); daemon inject E2E (B4); hosting/reflow/DecorCaption + cold-boot mount + freeform-persist reboot vật lý.
