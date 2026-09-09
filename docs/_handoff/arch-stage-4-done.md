# Arch Stage 4 (B4) — DONE (input daemon)

> Local (chưa push). Build XANH (`:app:assembleDebug`); `:core` 935 + `:app` 506 = **1441 unit test, 0 fail**.
> Golden (LauncherCommandGoldenTest 11) + guards (LauncherWindowing 5 · PersistentWriter 4 · Layering 9 · CoreIsolation 2) + cast suites (31 file) XANH. Chạm KHÔNG hồi quy: fallback `input -d` byte-khớp bản trước B4.

## Đã làm — input-daemon thường trú (scrcpy-style, KHÔNG build jar riêng)
Chạm vào app nhúng trong ô giờ đi qua **daemon `injectInputEvent` thường trú** (đường dữ liệu RIÊNG = socket), fallback về `input -d` cũ khi daemon chưa/không lên.

### Thành phần
- **:core `system/inputd/` (PURE, no-android — Layering/CoreIsolation xanh):**
  - `InputWireProtocol.kt` + `TouchFrame` — khung dây CỐ ĐỊNH 18 byte big-endian `[version:1][type:1][displayId:4][action:4][x:4][y:4]`; `encode`/`decode` (decode → null cho khung rác: sai độ dài/version/type ⇒ daemon bỏ qua, không sập).
  - `SlotTouchMapper.kt` — map view→display; ĐỒNG NHẤT khi VD cùng cỡ surface (⇒ daemon dùng cùng toạ độ với fallback), scale khi khác cỡ, guard chia-0.
  - `InputDaemonLaunch.kt` — dựng lệnh app_process (golden). `MAIN_CLASS="com.byd.clusternav.system.inputd.InputDaemonMain"`, `DEFAULT_SOCKET="kachi_input"`.
  - `TouchRouter.kt` — `fallbackTapCmd(display,x,y)="input -d <d> tap <x> <y>"` (byte-lock) + `shouldFallback(routed)=!routed`.
  - `DaemonChannel.kt` — PORT (connect/write/close, ByteArray) → adapter thật ở :app, giả trong test.
- **:app `system/inputd/` (đều import android → pure-files-in-app vẫn 0):**
  - `InputDaemonMain.kt` — chạy qua app_process uid-2000: `LocalServerSocket` ABSTRACT → đọc `readFully(18)` → `EventInjector`. Bind lỗi→thoát sạch; khung rác→bỏ qua; client rớt→vòng accept lại (reconnect). <=500 LOC.
  - `EventInjector.kt` — reflection `InputManager.getInstance()`(≤12)/`InputManagerGlobal.getInstance()`(13+) → `injectInputEvent(event, ASYNC=0)` + `MotionEvent.setDisplayId(display)`; ghép DOWN/UP theo downTime per-display (một tap đúng, hết double-fire) + MOVE.
  - `InputDaemonClient.kt` — `sendTouch(display,action,x,y):Boolean` (true=đi socket, false=caller fallback); LIFECYCLE (start daemon) qua `launchShell` seam (ShellTransport queue) — DỮ LIỆU (mỗi chạm) qua socket, KHÔNG qua queue; health/reconnect có throttle (cooldown); degrade-safe. Mọi phụ thuộc thiết bị TIÊM → test JVM.
  - `LocalAbstractChannel.kt` — adapter `DaemonChannel` bằng `android.net.LocalSocket` namespace ABSTRACT.

### Lệnh khởi động (chính xác)
`CLASSPATH=<apk> nohup app_process / com.byd.clusternav.system.inputd.InputDaemonMain <socket> </dev/null >/dev/null 2>&1 &`
- APK path: `context.applicationInfo.sourceDir` (base.apk, /data/app world-readable → shell uid đọc được).
- `nohup … &` + redirect = chạy nền ⇒ `ShellTransport.run` (blocking, 1 owner-thread) TRẢ VỀ NGAY, KHÔNG treo hàng đợi cửa sổ/cast bởi tiến trình không-kết-thúc.
- KHÔNG `--display` ⇒ không rò cụm; lifecycle đi qua queue là được phép, chạm thì KHÔNG.

### Wire vào VdAppHost/WorkspaceView
- `VdAppHost(..., inputClient: InputDaemonClient? = null)`: DOWN/UP → `inputClient?.sendTouch(map(x,y))`; `shouldFallback` → `Thread{ sh(TouchRouter.fallbackTapCmd(display,x,y)) }` (BYTE-KHỚP cũ). MOVE = daemon-only (fallback cũ không có MOVE → no-op khi daemon tắt = y hệt cũ). Lưu `dispW/dispH` lúc tạo/resize VD để map. GIỮ NGUYÊN `launchOnDisplayCmd`/`am force-stop $p`/resolve template (LauncherWindowingGuardTest xanh).
- `WorkspaceView`: tạo LƯỜI **1 client THƯỜNG TRÚ dùng chung MỌI ô** (mỗi khung tự mang displayId), truyền vào mỗi `VdAppHost`; hâm nóng qua `ensureStarted()` sau khi launch app.

### Test (đủ 4 mảnh pure + client)
- `InputWireProtocolTest` (5) round-trip + rác; `SlotTouchMapperTest` (3) identity/scale/guard; `InputDaemonLaunchTest` (4) chuỗi launch byte-exact; `TouchRouterTest` (2) fallback byte-exact + quyết định; `InputDaemonClientTest` (5, :app): down→false + CHỈ lệnh launch trên queue; up→true + khung đi SOCKET (không queue); resident reuse; write-fail→down→fallback; throttle.

## ⚠ ON-CAR PENDING (E2E daemon KHÔNG verify được phiên này — dadb loopback DOWN trên emulator)
1. Daemon lên thật khi start qua dadb (app_process nạp class từ APK CLASSPATH) + socket ABSTRACT nối được **app-uid → shell-uid** (rủi ro SELinux — chưa verify; fallback che).
2. `injectInputEvent` + `setDisplayId` bơm đúng vào VD của ô trên xe.
3. Chạm mượt hơn `input -d` (đo độ trễ) + MOVE (kéo/cuộn) hoạt động.
4. **Bất kể (1)-(3): khi daemon KHÔNG lên, chạm chạy y hệt trước B4** (`input -d tap`, byte-khớp) ⇒ KHÔNG hồi quy — đã khoá bằng TouchRouter golden + guard.

## HAND-OFF cho Stage tiếp (B5 — AppContainer)
- Gộp `InputDaemonClient` (process-singleton) vào AppContainer cùng `ShellTransport`/`WindowCommandDispatcher` (hiện tạo trong `WorkspaceView`; B5 dời vào object-graph). `launchShell` nên là seam dispatcher (đã vậy). Golden + cast + guard vẫn phải byte-stable.
