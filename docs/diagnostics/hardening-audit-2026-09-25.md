# Audit hardening (catch nuốt im · main-thread · rò · FGS · uncaught) — 2026-09-25

> **Trạng thái**: Current · **Cập nhật**: 2026-09-25 · **Mục đích**: finding [P0]–[P3] có file:line cho R4 của spec `docs/specs/kachi-closeout-hardening.html`; phần vá ghi ở §9 spec.

# T3h-audit — Hardening audit (report-only) · spec `docs/specs/kachi-closeout-hardening.html` R4

Ngày 2026-09-25 · branch `feat/voice-hotword-phrases` · HEAD `93dc1b4` · phạm vi `app/src/main`, `core/src/main`, `car-integration/src/main` (545 tệp .kt, 92 700 dòng — [ĐO] `find | xargs wc -l`).
Phương pháp: grep định hướng (`/usr/bin/grep`, không ugrep) → đọc nguyên văn từng tệp trên đường hạ tầng → chỉ ghi finding đã đọc tới `file:line`. Không sửa tệp, không chạy gradle.
Nhãn bằng chứng theo `conversation-protocol.md` P1: **[ĐO]** = đọc source/grep phiên này · **[SUY]** = suy luận từ source đã đọc.

---

## Tổng

| P0 | P1 | P2 | P3 |
|---|---|---|---|
| 0 | 3 | 8 | 9 |

**10 finding đắt nhất (xếp theo mức):**

| # | Mức | Finding | file:line |
|---|---|---|---|
| F10 | P1 | Chạm ô điều khiển ⇒ HAL binder **đồng bộ trên main thread** | `ControlTileFactory.kt:107,141,143,192,226,241` |
| F4 | P1 | `AdbKeys.ensure` ném ngoài `runCatching` trên `Thread` trần ⇒ **chết tiến trình launcher** khi OTA | `UpdateChecker.kt:132` ← `UpdateFlow.kt:71-87` |
| F1 | P1 | `ShellTransport.run` nuốt IM mọi lỗi 2 lượt ⇒ `""` giả làm "không có dữ liệu" | `system/ShellTransport.kt:99-100` |
| F23 | P2 | Không có `Thread.setDefaultUncaughtExceptionHandler`; ≥25 `Thread{}` trần | `KachiApplication.kt` (0 hit grep) |
| F22 | P2 | `:wake` dựng **AppContainer thứ hai** (HAL gateway + repo + scope) qua `VoiceWiring` | `voice/VoiceWiring.kt:135,162` ← `VoiceWakeService.kt:253` |
| F18 | P2 | `VoiceWakeService.sync` gọi `startForegroundService` **không bọc**; `startForeground` không bọc | `voice/VoiceWakeService.kt:130,354` |
| F14 | P2 | Hẹn 2 s `openProjection` không huỷ ⇒ mở lại chiếu **sau khi user đã TẮT cast** | `ClusterNavBridgeCast.kt:177` |
| F5 | P2 | Mọi lỗi HAL read/write nuốt im, không log dù một lần | `BydHalGateway.kt:110,117,132,143,152` · `BydHal.kt:80,196,249` |
| F6 | P2 | Ghi `.version` thất bại im ⇒ `needsUpdate()` **luôn true** ⇒ tải lại 61 MB mỗi lần bấm | `voice/VoiceModelStore.kt:188` |
| F2 | P2 | `LocalDeviceShell.run/runAll` nuốt im + **không hạn đọc** | `carexec/LocalDeviceShell.kt:90-99` |

---

## 1 · `catch`/`runCatching` nuốt IM trên đường hạ tầng

Tệp đã đọc nguyên văn (24): `LocalDeviceShell` · `DadbShell` · `ShellTransport` · `ShellChannelGate` · `PrioritySerialExecutor` · `ClusterNavBridge` + `…Cast/Keys/Msg/Home/Reapply/Geometry/Automation/Wake` (9) · `BydHal` · `BydHalGateway` · `KachiLog` · `PhotoStore` · `VoiceModelStore` · `UpdateChecker` · `UpdateFlow` · `net/HttpConn` · `AdbKeys` · `KachiApplication` · `AppContainer` · `NavConnect` (đoạn 95-125, 136-165, 275-310).
[ĐO] `catch (x: Exception|Throwable|RuntimeException)` = 45 site, **tất cả** có `Log.*` hoặc trả sealed-result (ví dụ `T10SessionEngine`, `NavigationSessionCoordinator`) — không có `catch {}` trần. Rủi ro nằm ở `runCatching` (826 site); dưới đây chỉ những site trên đường shell/HAL/IO/mạng đã đọc.

### F1 [P1] `ShellTransport.run` — mọi lệnh cửa sổ/cast hỏng thành `""` im lặng
`app/src/main/java/com/byd/clusternav/system/ShellTransport.kt:99-100`
```kotlin
fun run(cmd: String, priority: MutationPriority = MutationPriority.NORMAL): String =
    runCatching { exec(cmd, priority).allOutput }.getOrDefault("")
```
- Vì sao nguy: [ĐO] đây là đường **duy nhất** của `WindowCommandDispatcher.createOwned` (`runCommand = transport.run`, `WindowCommandDispatcher.kt:113`), `DadbShell.run/seam`, `LauncherWindows`, `VdAppHost`. `exec` đã thử 2 lần, ném → `run` đổi thành `""` **không một dòng log**. Consumer đọc `""` như "không có dữ liệu": [SUY] `VdAppHost.kt:262 appRunning()` = `pidof` rỗng ⇒ "app chưa lên" ⇒ bắn `am start` lần hai; `resolveComponent` rỗng ⇒ đoán `$p/.MainActivity`. Một adbd wedge cho ra chuỗi relaunch + task lạc mà logcat trắng — đúng họ lỗi §2 CLAUDE.md (không có dữ liệu để chốt).
- Vá: giữ hợp đồng `""`, thêm log + đếm:
  ```kotlin
  runCatching { exec(cmd, priority).allOutput }.getOrElse {
      Log.w("ShellTransport", "run FAILED (${it.javaClass.simpleName}: ${it.message}) cmd=${cmd.take(80)}")
      KachiPerf.add(KachiPerf.Counter.SHELL_FAIL); ""   // counter mới cạnh SHELL_CMD
  }
  ```
- Test hồi quy: tiêm `attempt` seam ném 2 lần (fake `Dadb`), gọi `run` → assert trả `""`, sink log được gọi **đúng 1 lần**, `SHELL_FAIL` +1. Test JVM (`ShellTransport` cần tách `conn()` thành lambda tiêm được — hiện `private`).

### F2 [P2] `LocalDeviceShell.run` / `runAll` — nuốt im **và** không hạn đọc
`car-integration/src/main/kotlin/com/byd/clusternav/carexec/LocalDeviceShell.kt:90-99`
```kotlin
fun run(keys: AdbKeyPair, command: String): String? = runCatching {
    Dadb.create(HOST, PORT, keys).use { adb -> (adb.shell(command).allOutput ?: "").trim() }
}.getOrNull()
```
- Vì sao nguy: [ĐO] `Dadb.create` 3-arg = socket timeout 0 (KDoc chính tệp này :156-161 + `ShellTransport.kt:59-64`) ⇒ adbd câm là luồng gọi treo vĩnh viễn; lỗi thì trả `null` không phân loại — trong khi `sessionResult`/`LocalShellFailures.classify` ngay dưới đã có bộ phân loại. Call site còn sống: `grantAppWidgetBind` (:278) ⇒ `AppWidgetSlotHost` chỉ biết "không nối được", không biết là chờ Allow-USB hay cổng đóng.
- Vá: thân `run/runAll` = `sessionResult(keys, LocalShellRetry.BACKGROUND_READ_CAP, onProgress) { sh -> … }` rồi map `Failed → null`; thêm tham số `onFailure: (LocalShellFailure) -> Unit = {}` để tầng app truyền `Log.w`. Không đổi chữ ký trả về.
- Test hồi quy: `LocalShellSessions`-style test với connector giả ném `SocketTimeoutException` → `run` trả `null` **và** `onFailure(AWAITING_APPROVAL)` được gọi; connector treo → trả null sau ≤ `socketTimeoutMs` (đo bằng đồng hồ giả).

### F3 [P2] `UpdateChecker.download` — lỗi mạng thành `null` im + rò kết nối
`app/src/main/java/com/byd/clusternav/UpdateChecker.kt:92-112`
```kotlin
fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File? = runCatching {
    …
    conn.disconnect()
    out.takeIf { it.length() > 0 }
}.getOrNull()
```
- Vì sao nguy: UI chỉ hiện "tải thất bại" (`UpdateFlow.kt:81`); nguyên nhân (DNS/TLS/302 sang http bị `HttpConn.open` `require` từ chối/đứt giữa chừng) không ghi đâu; `disconnect()` không nằm `finally` ⇒ ném là rò socket + tệp `.apk` cụt để lại trong `files/update/` (chỉ dọn ở lần tải sau).
- Vá: `try { … } finally { conn.disconnect() }` + `.getOrElse { Log.w(TAG, "download failed ${it.javaClass.simpleName}: ${it.message}"); out.delete(); null }`.
- Test hồi quy: tách `copyStream(input, output, total, onProgress)` thuần; tiêm `InputStream` ném ở byte thứ N → hàm bọc trả `null`, tệp không còn, log 1 dòng.

### F4 [P1] `UpdateChecker.install` — `AdbKeys.ensure` ngoài `runCatching`, chạy trên `Thread` trần
`app/src/main/java/com/byd/clusternav/UpdateChecker.kt:131-132`
```kotlin
UpdateRelaunch.schedule(app) // arm BEFORE install
val outcome = LocalDeviceShell.installApk(AdbKeys.ensure(app), apk, "-r", …)
```
- Vì sao nguy: [ĐO] `AdbKeys.generate` (`AdbKeys.kt:36`) `check(rename…)` ném `IllegalStateException`; `AdbKeyPair.generate` ném `IOException` khi `filesDir` đầy/không ghi được. Chỗ gọi `UpdateFlow.doUpdate` (`UpdateFlow.kt:71-87`) là `Thread({ … }, "update-download").start()` **không có catch ngoài** ⇒ ngoại lệ thoát ⇒ **launcher (HOME) chết** giữa lúc lái; `UpdateRelaunch` đã hẹn trước đó nên còn tự mở lại, nhưng mọi ô VD + a11y binding mất. Cùng khuôn đã được vá ở `ClusterNavBridgeHome.kt:244-247` (bọc `AdbKeys.ensure` → `NoShellChannel(UNKNOWN)`), chỗ này chưa.
- Vá: 
  ```kotlin
  val keys = runCatching { AdbKeys.ensure(app) }.getOrElse {
      UpdateRelaunch.cancel(app); return installMessage(LocalInstallOutcome.NoShellChannel(LocalShellFailure.UNKNOWN), apk.absolutePath)
  }
  ```
- Test hồi quy: tách `install(keys: () -> AdbKeyPair, installApk: (…)->LocalInstallOutcome, relaunch: Relaunch)`; `keys` ném → outcome `NoShellChannel(UNKNOWN)`, `relaunch.cancel` được gọi, không ném.

### F5 [P2] HAL gateway — mọi lỗi đọc/ghi nuốt im, không log dù một lần
`app/src/main/java/com/byd/clusternav/launcher/BydHalGateway.kt:110` (`device()`), `:117` (`getter`), `:127-132` (`namedInt`), `:139-143` (`featureGet`), `:146-152` (`featureSet`), `:161-173` (`localGet`)
```kotlin
override fun getter(deviceFqn: String, method: String, arg: Int?): String? {
    KachiPerf.add(KachiPerf.Counter.HAL_READ)
    return runCatching { device(deviceFqn)?.let { BydHal.callGetter(it, method, arg) } }.getOrNull()
}
```
và tầng dưới `modules/hal/BydHal.kt:80` (`device`), `:196` (`callGetter`), `:249` (`tryGet`) cũng `getOrNull()`.
- Vì sao nguy: [ĐO] KDoc chính tệp :42-50 thừa nhận: binder chết ⇒ "mọi ô hiện —" tới 5 phút, **không đường log nào**. `HalWriteProbe` chỉ ghi đường GHI; đường ĐỌC (1 400 lượt/phút) không có tín hiệu lỗi nào ngoài "—". Chẩn đoán trên xe (§11 CLAUDE.md) phụ thuộc logcat ⇒ ca này không chẩn được.
- Vá (generic, log-once theo khoá): trong `BydHalGateway` thêm `private val logged = ConcurrentHashMap.newKeySet<String>()`; ở mỗi `getOrNull()` đổi thành `.getOrElse { if (logged.add("$deviceFqn#$method")) Log.w(TAG, "HAL $deviceFqn#$method: ${BydHal.root(it)}"); null }`; xoá khoá khỏi `logged` khi `device()` resolve lại thành công (cùng chỗ `deviceCache[fqn] = Handle`). Tương tự `BydHal.callGetter:196` trả về lý do qua một `onError` seam thay vì `getOrNull`.
- Test hồi quy: `BydHalGatewayLogOnceTest` — fake device có getter ném `RuntimeException("dead")`; gọi `getter` 100 lần → 100 `null`, sink log **1** lần; sau khi `device()` resolve lại → log lại được 1 lần nữa.

### F6 [P2] `VoiceModelStore.install` — ghi `.version` thất bại im ⇒ nút "Cập nhật" vĩnh viễn
`app/src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt:188`
```kotlin
runCatching { File(dest, VERSION_FILE).writeText(pack.version.toString()) }
Log.i(TAG, "gói sẵn sàng: …")
onStep(Step.Done(pack.files.size))
```
- Vì sao nguy: [SUY] ghi hỏng (thẻ đầy ngay sau khi rename 61 MB — ca thật, `spaceError` chỉ kiểm trước khi tải) ⇒ `installedVersion()` (:97-98) = 0 ⇒ `needsUpdate()` (:104-105) = `true` mãi ⇒ Cài đặt hiện "Cập nhật", mỗi lần bấm là `remove` + tải lại 61 MB, rồi lại hỏng ở đúng dòng này. Vòng lặp không log.
- Vá: ghi `.version` **vào `out` (staging) trước `renameTo`** (cùng tính chất 1 "không thư mục nửa vời"); thất bại ⇒ `out.deleteRecursively(); onStep(Step.Failed(Lang.t("không ghi được .version", …)))`.
- Test hồi quy: JVM temp dir, `out` set read-only sau khi chép tệp giả → `install` phát `Step.Failed`, thư mục đích **không** tồn tại.

### F7 [P3] Nuốt im nhỏ, cần một dòng `Log.w`
- `PhotoStore.kt:157-160 folder()` `getOrNull()` — vắng thẻ/`SecurityException` ⇒ widget trống không lý do.
- `ShellChannelGate.kt:281 ShellApprovalProbe.probe`: `runCatching { AdbKeys.ensure(ctx) }.getOrNull() ?: return LocalShellFailure.UNKNOWN` — gộp "sinh khoá hỏng" vào "hạn chế môi trường" (đúng ca F4 nói sai bệnh).
- `ClusterNavBridgeCast.kt:57,60-62,84,99` — 6 `runCatching { … }` trần quanh `startForegroundService(FloatingBubbleService)` / `dispatch(Stop)` / `closeProjection` / `stopService`; [SUY] Android 12 (DL5) chặn FGS từ nền ⇒ toast "Đã bật Cluster Cast" nhưng nút nổi không bao giờ hiện, im lặng.
- `KachiHomeActivity.kt:209,213` — `runCatching { KachiLog.startCapture }` / `DiagStorageCap.enforce` trần (hai hàm bên trong đã tự log ⇒ chấp nhận, chỉ cần `.onFailure{Log.w}` cho đối xứng).
- Vá chung: `.onFailure { Log.w(TAG, "<việc>: ${it.javaClass.simpleName}: ${it.message}") }`. Không cần test riêng.

### F8 [P3] `KachiApplication.isBackgroundVoiceProcess` fail-safe ngược chiều rẻ
`app/src/main/java/com/byd/clusternav/KachiApplication.kt:54` — `runCatching { Application.getProcessName() }.getOrNull() ?: return false` ⇒ đọc tên tiến trình hỏng thì **coi là launcher** ⇒ nạp 74 MB model + VAD ở `:wake`/`:tts` — đúng regression cổng này sinh ra để chặn. Ngược lại (trả `true`) chỉ hoãn `AppContainer.get` (mọi field `by lazy`, `AppContainer.get` tự dựng khi cần) ⇒ rẻ hơn. minSdk 29 nên thực tế hiếm; vá 1 chữ. Test: không cần.

---

## 2 · Gọi chặn (shell/IO/mạng/HAL) trên luồng chính

Tệp đã đọc (≈25): `KachiHomeActivity` (toàn bộ) · `KachiHomeWiring` :340-462 · `ClusterNavBridge*` (9) · `ControlTileFactory` :100-135,185-250,275-290,350-362 · `CarControlAdapter` · `WakeOnWriteControl` · `HalBindingTable` (grep) · `SeatComfortApplier`/`Pm25FilterApplier`/`RecircApplier`/`VietMapAutostartService`/`VmOverlayPosition`/`NavConnect`/`NavRepository`/`NavigationHudOwner`/`SimpleCastCoordinator` (các hàm bridge gọi) · `FloatingBubbleService` · `BubblePipGuard` · `VoiceWakeService` · `UpdateFlow` · `SlotAppHost`/`VdAppHost`/`LauncherWindows` (đoạn) · `ShellChannelGate`.
[ĐO] `runBlocking`: 0 hit. `Thread.sleep`: 40 hit, **không** hit nào trên main (tất cả trong `Thread{}`/executor/coroutine `delay`). Bridge → applier: `grantAccessibilityDetailed:153` (Thread) · `applySeat:58` (Thread) · `Pm25 enable/disable/cleanNow:83,93,102` (Thread) · `startForAppOpen` (FGS) · coordinator `openProjection/closeProjection/dispatch/applySplitRatioLive/resizeActiveTarget/setDensity*` (`executor.submit`) · `NavRepository` HAL write trên worker (`NavigationHudOwner.kt:70`) — **đều off-main ✓**. Còn lại:

### F10 [P1] Ô điều khiển xe: HAL binder **đồng bộ trên main** ở mỗi cú chạm
`app/src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt:104-108`
```kotlin
tile.setOnClickListener {
    state.touch(def.id)
    val nv = !state.isOn(def.id); state.setOn(def.id, nv)
    look(def, tile, icon, label, on(def)); control().toggle(def.id, nv)
}
```
Cùng khuôn: `:141` `control().readState(def.id)` (ĐỌC HAL khi bấm −/+), `:143 step`, `:192 coverLevel`, `:226 select`, `:241 press`.
- Chuỗi gọi tới main [ĐO]: `setOnClickListener` (main) → `control()` = `container.carControl` (`KachiHomeActivity.kt:249,267`) → `WakeOnWriteControl.toggle` (`WakeOnWriteControl.kt:30`) → `CarControlAdapter.toggle` (`core/…/CarControlAdapter.kt:14`) → `HalBindingTable.write` (`:123-133`) → `BydHalGateway.namedInt/featureSet` (`:127,146`) → `BydHal.callNamedInt`/`setInt` (`BydHal.kt:168,111`) → `Method.invoke` → binder BYDAuto `set(int[], EventValue)`. Không có hop thread nào. Đường gói lệnh (`:280 MacroExec.submit`) và đường giọng nói (`VoiceDispatcher`) đã off-main — chỉ chạm ô đơn là còn.
- Vì sao nguy: binder HAL trên xe dưới tải ([ĐO] memory 09-17: SELinux chạm, voice chết 4,5 s) ⇒ giật/ANR màn HOME ngay lúc người lái chạm. `WakeOnWriteControl.wake` cũng chạy `forgetAbsent` trên main (rẻ).
- Vá (≤10 dòng, generic): thêm seam `exec: (() -> Unit) -> Unit` vào `ControlTileFactory` (mặc định = `MacroExec.submit` đã có, hoặc một `ControlExec` single-thread daemon); mọi `control().X(…)` thành `exec { port.X(…) }`; riêng `readState` ở `:141` đọc từ `state`/`car.controls` (ảnh chụp đã có) thay vì gọi HAL. UI lạc quan (`state.setOn` + `look`) giữ nguyên trên main.
- Test hồi quy: `ControlTileFactoryThreadTest` (JVM/Robolectric) — fake `CarControlPort` ghi `Thread.currentThread()`; tiêm `exec` giả; click ô toggle/step/cover/select/press → assert port **không** được gọi trực tiếp trong click mà qua `exec` (đếm `exec` = 1 mỗi cú, port = 0 trước khi chạy `exec`).

### F11 [P3] `:wake` — `VoiceWakeListener.stop()` join ≤ 700 ms trên main của `:wake`
`voice/VoiceWakeService.kt:147,164,280,287` → `VoiceWakeListener.kt:105-118` (`t?.join(JOIN_MS)`). KDoc đã nhận (:143-144). Tiến trình không UI, chấp nhận; ghi để StrictMode khỏi báo lạ.

### F12 [P3] Stat đĩa trên main lặp lại
- `KachiHomeActivity.kt:231` `voicePillEnabled = { Prefs.voiceMicPill(this) && VoiceModelStore.isReady(this) }` → `isReady` = N×`File.isFile/length()` (`VoiceModelStore.kt:90-94`), gọi ở `refreshVoicePill` mỗi `onResume`/đóng lớp phủ.
- `SettingsVoiceSection.kt:47-58` poll 1,5 s `bridge.wakeModelStatus()` → `ClusterNavBridgeWake.kt:178` `isReady` (5 stat) trên main.
- Vá: `VoiceModelStore` giữ `@Volatile readyCache: Map<String, Boolean>` cập nhật ở `install/remove` + một stat nền ở lần đầu; UI đọc cache.
- Test: chạy StrictMode (mục 7) — hai chỗ này phải hết `DiskReadViolation`.

### F13 [P3] `UpdateFlow.doUpdate` giữ `Activity` suốt lượt tải trên `Thread` trần
`UpdateFlow.kt:71-87` — closure giữ `activity` + `setStatus` (view) tới khi tải xong (40 MB, phút); `runOnUiThread` sau `onDestroy` vẫn chạy `setStatus` lên view đã tháo. Vá: `WeakReference<Activity>` + `if (a.isDestroyed) return@runOnUiThread` như `start()` đã làm (:21). Test: không (leak thủ công).

---

## 3 · Receiver / Handler / listener / coroutine sống lâu hơn chủ

Tệp đã đọc (20): `VoiceWakeService` · `KachiHomeActivity` · `FloatingBubbleService` · `BubbleAutostart` :45-85 · `ShellChannelGate` · `PhotoWidgetView` :35-95 · `SlotAppHost` :55-100 · `LauncherWindows` :95-160 · `VoiceSession` :375-410 · `KachiTestBridge` :175-285 · `AmapEmissionArbiter` :25-45,190-205 · `VietMapWidgetBridge` :60-110,285-300 · `SettingsVoiceSection` :40-70 · `VoiceKeyKeepAliveService` · `ClusterNavActivity` (grep lifecycle) · `KachiHomeWiring` :388-410 · `CarStatusRepository` :95-140 · `VdAppHost.release` · `SlotLiveProbe` (API) · `RebindReceiver`.

[ĐO] `registerReceiver(`: **1** site (`VoiceWakeService.kt:117`) ↔ `unregisterReceiver` `:288` trong `onDestroy` ✓. `postDelayed(`: 36 site; 34 có `removeCallbacks`/`removeCallbacksAndMessages(null)` khớp lifecycle hoặc cờ `destroyed`/`running`/`stopped` (đã đối chiếu từng site). `addStateListener` ↔ `removeStateListener` cặp đủ (`FloatingBubbleService:139/181`, `BubbleAutostart:85,92,100-101,137,146`). `SlotLiveProbe.watch` ↔ `unwatch` ở `VdAppHost.release:375` ✓. `lifecycleScope` (`KachiHomeWiring:388,400`) treo vào `LifecycleRegistry` tự quản nhận `ON_DESTROY` (`KachiHomeActivity:503`) ✓; `CarStatusRepository.stop()` trong `finally` ✓; `AppContainer.carScope` sống theo tiến trình (chủ ý). Còn 2 site chưa khớp:

### F14 [P2] `restoreCluster` hẹn `openProjection` 2 s, không huỷ, không gate công tắc
`app/src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeCast.kt:173-178`
```kotlin
fun ClusterNavBridge.restoreCluster() {
    coordinator.dispatch(SimpleCastIntent.Stop()); coordinator.closeProjection()
    toast(BridgeMsg.CLUSTER_RESET_REOPENING)
    Handler(Looper.getMainLooper()).postDelayed({ coordinator.openProjection() }, 2_000)
}
```
- Vì sao nguy: [SUY] bấm "Trả cụm về đồng hồ" rồi trong 2 s gạt TẮT Cluster Cast (`setCastEnabled(false)` :59-63 → `closeProjection`) ⇒ hẹn giờ vẫn nổ ⇒ `openProjection` **giành lại cụm sau khi người dùng đã tắt** — vi phạm §4/§5 CLAUDE.md (đường đổi state hệ thống không có hoàn tác, quyết định bằng cờ RAM đã cũ). Handler mới mỗi lần, không có tham chiếu để `removeCallbacks`.
- Vá: giữ `private val reopenTask = Runnable { if (coordinator.prefs.castEnabled()) coordinator.openProjection() }` + `handler` MỘT bản trong `ClusterNavBridge`; `restoreCluster` = `handler.removeCallbacks(reopenTask); handler.postDelayed(reopenTask, 2_000)`; `setCastEnabled(false)` gọi `handler.removeCallbacks(reopenTask)`.
- Test hồi quy: tách luật thuần `CastReopenPolicy.shouldReopen(castEnabledNow)`; test fake coordinator + scheduler giả: `restoreCluster()` → `setCastEnabled(false)` → tiến 2 s → `openProjection` **không** được gọi; không tắt → được gọi 1 lần.

### F15 [P3] `KachiTestBridge.armTimeout` — Handler mới, giữ `PendingResult` tới hết `CAP_MS` dù đã trả lời
`launcher/testbridge/KachiTestBridge.kt:182-186`. Đường kiểm thử, bị chặn bởi công tắc 60 phút; vá: giữ `Runnable` và `removeCallbacks` trong `reply.ok/fail`.

### F16 [P3] `startLearn` để lambda giữ handler Activity trên bus singleton khi màn `recreate()` giữa lúc học
`ClusterNavBridgeKeys.kt:188-193` `VoiceKeyLearnBus.setListener { code -> ui(Runnable { onLearned(code) }) }`; `SettingsSectionsKeys.kt:192,212` gọi `stopLearn` khi học xong / đóng section, nhưng `KachiHomeActivity.onDestroy:525 panels.closeAll()` không gọi `bridge.stopLearn()` ⇒ đổi ngôn ngữ (`recreate()` :362) đang học ⇒ bus giữ Activity cũ tới lần học sau. Vá: `HomePanels.closeAll()` gọi `bridge.stopLearn()`. Test: `startLearn` → `closeAll()` → assert `VoiceKeyLearnBus` không còn listener (thêm `hasListener()` cho test).

---

## 4 · Service: `stopSelf`/`stopForeground`, FGS 5 s, start từ nền

Tệp đã đọc (12): `BootSetupService` (:37-60,130-145) · `VoiceKeyKeepAliveService` · `KachiAutostartService` (:21-75) · `VietMapAutostartService` (:22-90,115-125) · `automation/AutomationService` (:39-90,156-176) · `FloatingBubbleService` · `CastAutomationService` · `voice/VoiceWakeService` · `voice/PiperTtsService` · `RebindReceiver` · `KachiHomeWiring.ensureCastBubble` · `CastBubbleControl` (grep).
[ĐO] 6 FGS `specialUse` + `FloatingBubbleService` đều `startForegroundOnce()` (runCatching + `Log.e`) **trước** mọi `return`/`stopSelf(startId)`; `stopForeground(STOP_FOREGROUND_REMOVE)` ở 5 service một-lần; `AutomationService.sync` bọc `startForegroundService` (:157-176); `RebindReceiver` dùng `goAsync()` + Thread và bọc `startForegroundService` (:134-138,152-156) ✓. `PiperTtsService` bound-only, không FGS, `onDestroy` nhả engine ✓. Còn:

### F18 [P2] `VoiceWakeService` — FGS duy nhất không đi qua `startForegroundOnce`; `sync()` không bọc `startForegroundService`
`voice/VoiceWakeService.kt:130`
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(NOTIF_ID, buildNotification())
```
và `:351-357`
```kotlin
fun sync(ctx: Context, reloadModel: Boolean = false) {
    …
    if (Build.VERSION.SDK_INT >= O) ctx.startForegroundService(i) else ctx.startService(i)   // KHÔNG runCatching
} else { runCatching { ctx.stopService(i) } }
```
(so với `listenNow:371-373` **có** bọc).
- Vì sao nguy: [SUY] DL5 = Android 12: `startForegroundService` từ nền ném `ForegroundServiceStartNotAllowedException`. `sync()` được gọi từ `WakeModelFetch.run` (`ClusterNavBridgeWake.kt:117`, luồng daemon `kachi-kws-fetch`, có thể sau khi app rời tiền cảnh) và `KachiAutostart.runBoot:119` (trong FGS — được phép). Ở nhánh daemon, ngoại lệ thoát `catch (t: Throwable)`? — [ĐO] `run()` :119 **có** bắt `Throwable` ⇒ không sập, nhưng "Hey Kachi" im lặng không bật. Ở `onStartCommand`, `startForeground` với type `microphone` không bọc: API 34+ ném `SecurityException` khi thiếu RECORD_AUDIO ⇒ sập `:wake` (không kéo launcher, nhưng START_STICKY dựng lại ⇒ vòng crash-restart). Xe hiện API 29/31 nên hôm nay là [P2] phòng ngừa, không phải lỗi đang chạy.
- Vá: dùng đúng khuôn 6 service kia: `private fun startForegroundOnce(): Boolean = runCatching { startForeground(...); true }.getOrElse { Log.e(TAG,"startForeground denied",it); false }`; `if (!startForegroundOnce()) { stopSelf(startId); return START_NOT_STICKY }`; `sync()` bọc `runCatching{…}.onFailure{ Log.w }` như `listenNow`.
- Test hồi quy: `VoiceWakeServiceSyncTest` — `Context` giả có `startForegroundService` ném `IllegalStateException` → `sync` không ném, log 1 dòng.

### F19 [P3] `FloatingBubbleService` START_STICKY + `onCreate` `stopSelf()` sớm khi Cast OFF (`:113-115`)
Sau LMK hệ dựng lại rồi service tự tắt ⇒ nhấp thông báo; `RebindReceiver.startOptedInBubble` đã gate `castEnabled` (:106-111) nên chỉ còn ca sticky-restart. Chấp nhận; nếu muốn sạch: trả `START_NOT_STICKY` khi `!castEnabledNow()` ở `onStartCommand` (đã làm :152) — đủ.

---

## 5 · `!!` trên giá trị đến từ hệ thống

[ĐO] 30 site (`grep '!!'` trừ comment). Đã đọc ngữ cảnh từng site: 
- **Có bảo vệ, không phải finding**: `UpdateChecker:79,84` (null-check ngay trước, `var` nên không smart-cast); `WorkspacePrefs:194,203,219` (`getString(key, defaultNonNull)` + `runCatching`); `VoiceTargetDispatch:150`, `ScheduledNavApplier:165` (`place.hasCoords` = `lat != null && lng != null`, `SavedPlaces.kt:37`); `VoiceReply:79` (`(i.value ?: 0) >= 2 &&` ngắn mạch); `ContrastGuard:67,95`, `KachiPaletteDerive:106` (vòng ≥1 lượt luôn gán `best`); `SpeedSignLifecycleCoordinator:53` (bất biến constructor); `SettingsSectionsNav:275-354`, `SettingsSectionsCast:210` (view field gán trong cùng builder, không phải dữ liệu hệ thống); `SettingsSectionsCast:341 it.side!!` chỉ chạy khi `targets.size > 1` ⇔ `CastingSplit` ⇒ `side` non-null (`ClusterNavBridgeGeometry:50-58`); `ClusterDiag:142` là chuỗi log.
- **Có thể null thật**:

### F21 [P3] `CarExecCommands.kt:238` — id candidate đọc từ ledger bền, catalog có thể đã bỏ
`core/src/main/kotlin/com/byd/clusternav/carexec/CarExecCommands.kt:232-238`
```kotlin
val candidateId = ledger.okCandidateFor(action.stepId)
if (candidateId == null) { … return@buildString }
val candidate = CarExecCatalog.candidate(candidateId)!!.second
```
- Vì sao nguy: ledger là tệp ghi từ lần chạy trước; catalog đổi tên/xoá candidate ⇒ NPE. [ĐO] chỉ có call site `car-integration/…/CarExecCli.kt` (CLI off-car) ⇒ không chạy trên xe ⇒ P3.
- Vá: `?: run { appendLine("$number. ${action.stepId} — ledger trỏ candidate '$candidateId' không còn trong catalog; xoá dòng đó rồi chạy lại"); return@buildString }`.
- Test: fixture ledger có id lạ → output chứa câu trên, không ném.

---

## 6 · Tiến trình riêng `:tts` / `:wake`

Tệp đã đọc (9): `AndroidManifest.xml` :160-330 · `KachiApplication` · `AppContainer` · `voice/PiperTtsService` · `voice/VoiceWakeService` · `voice/VoiceWakeListener` · `voice/RemotePiperSpeaker` (grep death-handling :32-34,124-136,222,262,314) · `voice/VoiceWiring` :118-170 · `SherpaTtsSpeaker` (grep).

[ĐO] Hai tiến trình: `:tts` = `PiperTtsService` (bound-only, không exported) · `:wake` = `VoiceWakeService` (FGS `microphone`, START_STICKY). SIGSEGV native ở một trong hai chỉ giết tiến trình đó; launcher bắt bằng `onServiceDisconnected`/`onBindingDied`/`onNullBinding` (`RemotePiperSpeaker.kt:124-136` → `onRemoteGone` mở mọi sổ + unbind) ✓; `:wake` chết ⇒ hệ dựng lại (sticky), launcher không biết cũng không cần ✓. `KachiApplication.onCreate:25` gate theo `Application.getProcessName()` (static, API 28) so với `PROCESS_SUFFIX` của hai service ⇒ `:tts`/`:wake` **không** dựng `AppContainer`, **không** `VoiceEngine.preload` (74 MB), **không** `VoiceVad.preload` ✓. Còn:

### F22 [P2] `:wake` dựng lại **cả đồ thị AppContainer** ở câu lệnh đầu tiên
`voice/VoiceWakeService.kt:240-273 buildSession()` → `VoiceWiring.dispatcher(ctx = app, …)` → `voice/VoiceWiring.kt:135,162`
```kotlin
): VoiceDispatcher = VoiceDispatcher(
    control = { AppContainer.get(ctx).carControl },
    …
    freshCar = { id -> runCatching { AppContainer.get(ctx).refreshForRead(id) }.getOrNull() },
```
- Vì sao nguy: [ĐO] `AppContainer.get` là singleton **theo tiến trình** (`AppContainer.kt:146-151`): trong `:wake` nó dựng `BydHalGateway` (+ `systemBypassContext` reflection, cache device riêng), `HalBindingTable`, `CarDataAdapter`, `CarStatusRepository` + `carScope` (pool IO) — bản thứ hai của mọi thứ launcher đã có; `VoiceSession` còn nạp `VoiceEngine` (74 MB int8) + `VoiceVad` **trong `:wake`** khi phiên nghe mở — đúng RAM ×2 mà cổng ở `KachiApplication` viết ra để chặn ([ĐO tombstone 09-18: SEGV_MAPERR dưới áp lực RAM]), chỉ là hoãn tới lần "Hey Kachi" đầu. `refreshForRead` trong `:wake` luôn trả `null` (`carDemand` không ai `set`) ⇒ câu hỏi dữ liệu xe từ wake đọc ảnh chụp **rỗng** (mọi field null ⇒ "—") — hành vi khác hẳn đường nút mic.
- Vá (owner chốt, hai hướng generic): (a) `:wake` chỉ phát hiện câu gọi rồi giao **phiên** cho tiến trình launcher qua một service/Activity launcher-process (đường `EXTRA_START_VOICE` :244 vẫn còn) — overlay vẫn dựng được từ service launcher-process; (b) `VoiceWiring.dispatcher` nhận seam `control`/`freshCar` và `:wake` truyền proxy Messenger về container của launcher. Tối thiểu ngay: `AppContainer.build` thêm `check(!isBackgroundVoiceProcess())` ở build debug để lộ mọi đường mới.
- Test hồi quy: contract test kiểu `LayeringRulesTest`: quét source `voice/VoiceWakeService.kt` + đồ thị import bắc cầu (`VoiceWiring`) → cấm chuỗi `AppContainer.get`; hoặc test Robolectric giả `getProcessName()=":wake"` → `AppContainer.get` ném.

### F23 [P2] Không có `Thread.setDefaultUncaughtExceptionHandler`; ≥25 `Thread { }` trần
[ĐO] `grep UncaughtExceptionHandler` = 0 hit trong 3 module. `Thread({ … }).start()` trần (không catch ngoài) ít nhất: `UpdateFlow.kt:18,71` · `ClusterNavBridge.kt:448,479` (`pm25Level` → `readLevel` có runCatching ✓, nhưng `ui(...)` không) · `ClusterNavBridgeHome.kt:80-94` (`disableHomeEntry`/`otherHomeComponent` ngoài runCatching) · `VdAppHost.kt:225` (`Thread.sleep` ném `InterruptedException` nếu ai `interrupt`) · `LocalDeviceShell` KDoc :275 tự ghi "bên gọi chạy trong `Thread { }` TRẦN … ngoại lệ lọt ra là giết tiến trình".
- Vì sao nguy: một ngoại lệ bất kỳ trên luồng phụ = **launcher chết** = HOME + mọi ô VD + a11y binding mất, phím vô-lăng chết tới khi rebind (đúng chuỗi `PiperTtsService` KDoc :25-28 mô tả). Không có handler ⇒ cũng không có dòng `KachiLog` nào được flush trước khi chết.
- Vá (≤10 dòng, `KachiApplication.onCreate`, chỉ tiến trình launcher):
  ```kotlin
  val prev = Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler { t, e ->
      Log.e("KachiCrash", "uncaught on ${t.name}", e)
      val bestEffort = t !== Looper.getMainLooper().thread && t.name.startsWith(BEST_EFFORT_PREFIXES)  // bridge-/update-/kachi-/pm25-/seat-
      if (!bestEffort) prev?.uncaughtException(t, e)   // main + luồng lạ: vẫn chết như cũ, nhưng đã có log
  }
  ```
  Kèm đặt tên mọi `Thread{}` best-effort theo tiền tố (đa số đã có tên).
- Test hồi quy: JVM — cài handler với `prev` giả; ném trên `Thread("bridge-x")` → `prev` **không** gọi, sink log 1; ném trên `Thread("other")` → `prev` gọi 1.

---

## 7 · StrictMode

[ĐO] `grep -rn StrictMode app/src/main core/src/main car-integration/src/main app/build.gradle.kts` = **0 hit**. Build types (`app/build.gradle.kts:109-121`): `release` (`isDebuggable=false`) · `vehicleTest` (`initWith(release)`, `isDebuggable=true`, chạy **trên xe**) · `debug` mặc định AGP. ⇒ Chưa bật ở đâu.

**Đề xuất chỗ bật** — `KachiApplication.onCreate`, ngay sau cổng `isBackgroundVoiceProcess()` (:25) và **trước** `AppContainer.get(this)`:
```kotlin
if (BuildConfig.DEBUG && BuildConfig.BUILD_TYPE == "debug") {   // KHÔNG bật ở vehicleTest (xe thật)
    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
        .detectDiskReads().detectDiskWrites().detectNetwork().detectCustomSlowCalls()
        .penaltyLog().build())
    StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
        .detectLeakedClosableObjects().detectLeakedRegistrationObjects().detectActivityLeaks()
        .penaltyLog().build())
}
```
Kèm hai `StrictMode.noteSlowCall(...)` để `detectCustomSlowCalls` gắn nhãn đúng chỗ: `BydHalGateway.namedInt/featureSet/getter` (`"hal:$deviceFqn#$method"`) và `ShellTransport.attempt` (`"shell:${cmd.take(40)}"`) — chỉ nổ khi gọi trên main (đúng F10).

**Cách chạy để đọc vi phạm** (máy ảo `clusternav10`, memory `clusternav-build-on-mac`):
```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:installDebug
adb logcat -c && adb logcat -s StrictMode:* | grep -A14 'StrictMode policy violation'
```
Kịch bản chạm để có bằng chứng cho F10/F12: mở HOME → chạm 1 ô toggle ở thanh nút (kỳ vọng `DiskReadViolation`/`CustomSlowCall hal:*` tại `ControlTileFactory.kt:107`) → mở Cài đặt › Giọng nói, đợi 3 s (kỳ vọng `DiskReadViolation` tại `VoiceModelStore.isReady` mỗi 1,5 s). Nhiễu dự kiến: `SharedPreferences` nạp lần đầu trong `onCreate` (bọc bằng `StrictMode.allowThreadDiskReads()` quanh `Prefs` init nếu quá ồn). Nếu cần đo trên xe với `vehicleTest`: thêm công tắc ẩn `Prefs.strictModeDebug` (mặc định tắt) thay vì gắn theo build type — `penaltyLog` không đổi hành vi, chỉ tốn vài dòng log.

---

## Ngoài scope, đã thấy, để backlog (không tính finding)
- `VoiceWakeService.kt:117 registerReceiver` không có `RECEIVER_NOT_EXPORTED` — SCREEN_ON/OFF là broadcast hệ thống nên API 34+ không ném; lint targetSdk 37 sẽ nhắc.
- `ClusterNavBridge.restartLauncher:201` `Process.killProcess` sau 300 ms — chủ ý (owner 09-25).
- `SettingsSectionsCast`/`Nav` dùng `!!` cho view lateinit-kiểu — đổi sang `lateinit var` sẽ sạch hơn, không phải lỗi.
