# HANDOFF — On-car FREEZE khi ĐỔI APP trên cụm (2026-07-23 tối, ~18:00–23:00)

> Đọc file này + `CLAUDE.md`/`workflow.md` TRƯỚC khi chạm code. Mục tiêu phiên sau: **giải cứu bug treo head unit khi SWITCH app trên cụm.**
> Bối cảnh: đi test Track 1 (cast-hardening v0.60) trên xe thật → phát hiện + đánh vật cả tối với 1 bug P0 (treo phải reboot). Đã fix được PHẦN KHÓ NHẤT nhưng còn 1 nút thắt.

---

## 0. TL;DR (đọc cái này trước)

- **Cast 1 app (Vietmap) lên cụm = OK, KHÔNG treo.** ✅ (đường COLD, tạo VD mới tinh)
- **ĐỔI APP (Vietmap→Maps) = TREO head unit** (phải reboot). Đây là bug đang giải.
- Đã thử 6 bản (v0.60→v0.65). Freeze **biến hình qua 2 lớp NPE khác nhau** (xem §3). Fix tăng-dần-trên-xe KHÔNG hội tụ → **phiên sau fix OFFLINE cho tới nơi rồi test 1 lần.**
- **Đã chứng minh (validated trên xe):** đặt app MỚI lên VD **đang sống** (không huỷ VD) = KHÔNG treo. **Nút thắt cuối = bê app CŨ khỏi VD** (`am display move-stack old→0`) ném `createTaskSnapshot` NPE → flaky + có thể treo.
- **Hướng fix ưu tiên phiên sau: "bring-to-front only" (KHÔNG move app cũ ra)** — xem §5.

---

## 1. Môi trường / setup (BẮT BUỘC biết)

| | |
|---|---|
| Xe (adb WiFi) | **`<CAR_ADB_IP>:5555`**. `adb connect <CAR_ADB_IP>:5555`. Cùng WiFi. |
| ⚠ **`adb reboot` KHÔNG hoạt động** | uptime không giảm sau lệnh. **Chỉ reboot bằng GIỮ NÚT NGUỒN head unit ~10-15s.** adb-over-network sống lại sau reboot (reconnect được). |
| Quyền shell | uid 2000 (KHÔNG root). `stop/start` framework = "must be root" (fail). `setprop persist.*` = fail. `pm clear`/`am force-stop`/`am display move-stack` = OK. |
| Build | `export JAVA_HOME=/opt/homebrew/opt/openjdk@17` · SDK `$HOME/Library/Android/sdk` · aapt2 `build-tools/34.0.0` · `./gradlew --offline testDebugUnitTest assembleRelease` |
| APK output | `collectApks` → **`ClusterNav/apk/ClusterNav-<ver>-<variant>.apk`** (versioned, trong repo). `apk/ClusterNav-*-release.apk` được git track (UpdateChecker). debug apk gitignored. |
| local.properties | Mac path (`$HOME/Library/Android/sdk`), gitignored. **Quy ước "restore Windows path" ĐÃ BỎ — Mac-only.** |
| Chụp cụm | `adb -s <dev> shell screencap -d 1 -p /sdcard/x.png` → pull → xem (VD cụm = 1920×720). `-d 1` bắt đúng cụm. |

### Định danh app / cụm
- Vietmap = `vn.vietmap.live/.MainActivity` · Google Maps = `app.revanced.android.apps.maps/com.google.android.maps.MapsActivity` (nav = `.driveabout.app.DestinationActivity`)
- CarPlay = `com.byd.carplay.ui/.VideoActivity` · Android Auto = `com.byd.androidauto/...AAPVideoActivity`
- **VD cụm** = `fission_bg_xdjaVirtualSurface`, **displayId 1**, 1920×720, `FLAG_OWN_CONTENT_ONLY`, owner `com.xdja.containerservice`. Chỉ tồn tại KHI đang chiếu (tạo lúc cast, mất khi gauges). Màn chính = display 0 (1920×1080).

---

## 2. TRÌNH TỰ TỐI NAY — từng lần trace + fix

### Preflight (18:07)
- Xe đang v0.59, cụm có **cửa sổ CP mồ côi kẹt SẴN** (com.byd.carplay.ui/.VideoActivity, WM thấy/AM không) — từ phiên trước, tắt/mở máy nhiều vòng KHÔNG dọn được (vì ignition không cold-boot head unit; và `am stack remove`/force-stop/restart container đều bất lực). **Chỉ reboot cứng (nút nguồn) mới sạch.**
- Cài v0.60 release (đè v0.59, chữ ký nhóm OK). Scrub navprobe accessibility stale.

### v0.60 (teardown-guard) — WARM-SWITCH TREO ❌ (freeze lần 1, ~18:52)
- Guard tiền-kiểm mồ côi HOẠT ĐỘNG: cast Vietmap bị TỪ CHỐI vì cụm còn mồ côi → báo đúng "cần tắt máy xe". ✅ (validated)
- Reboot cứng → cụm sạch (gauges, VD biến mất).
- Cast Vietmap (cold) = OK. **Đổi sang Maps (warm-switch) → TREO.** 
- NPE: `AppWindowToken.loadAnimation → DisplayContent.getDisplayInfo()` **null**, trong `handleChangingApps` → **LẶP vô hạn** → treo, chết touch, cụm đen. Evidence: `oncar-track1-P0-warmswitch-freeze-20260723-185754/wm-npe-trace.txt` (96 blocks).
- Root (nghĩ lúc đó): warm-switch bắn **cmd16 (CMD_PROJECT) TÁI TẠO VD** trong lúc token app đang transition → DisplayContent null.

### v0.61 (bỏ cmd16 khỏi warm) — VẪN TREO ❌
- Gỡ `sh(wp.svcCall(CMD_PROJECT))` khỏi warm path. Cài, test → **vẫn treo.** ⇒ cmd16 không phải thủ phạm DUY NHẤT.

### v0.62 (warm, dời restoreFullscreenOnMain) — KHÔNG kịp test on-car
- Dời `restoreFullscreenOnMain(old)` xuống sau khi app mới bám VD. Chưa test (pivot sang cold-only theo ý chủ dự án).

### v0.63 (cold-only, bỏ hẳn warm) — commit `af21ae5` — CŨNG TREO ❌ (freeze lần 2, ~19:20-21:56)
- Chủ dự án đề xuất: bỏ warm, đổi app = **STOP(dọn cụm về gauges, HUỶ VD) → COLD-cast app mới** (lean, cụm luôn 1 app). Đã code `cleanClusterForReCast()` (teardown VD) + gate bằng `isWarm`. Senior-review fix [P2] điều kiện.
- Port cold-only sang nhánh debug (commit `176e68c`). Gôm APK vào `apk/` (commit `fd4890c`/`92a6d91`).
- On-car: cast Vietmap OK. **Đổi Maps → castlog "✅ Maps trên cụm" RỒI TREO.** Maps nháy lên cụm rồi **bounce về màn chính (scale sai)**. NPE = **getDisplayInfo** (giống lần 1). ⇒ **teardown [18,0] HUỶ VD** trong lúc app transition = cùng lớp NPE. Evidence: `oncar-coldswitch-freeze-20260723-220338/`.
- ⇒ Kết luận: **KHÔNG phải warm-vs-cold. Là HUỶ/TÁI TẠO VD lúc switch** (warm=cmd16, cold=teardown) khi app đang transition → getDisplayInfo NPE loop.

### VALIDATE hypothesis bằng adb thủ công (~22:15) — ✅ ĐÚNG
- Cast Vietmap (cold). Rồi **bằng adb**: `am start --display 1 --windowingMode 5 -n <maps>` (đặt Maps lên VD **đang sống**, Vietmap còn đó) → **NPE=0, KHÔNG treo.** 2 app cùng trên VD. Rồi `am display move-stack <vietmap> 0` → NPE=0. Cụm hiện Maps.
- ⇒ **CHỨNG MINH: giữ VD SỐNG + đặt app mới TRƯỚC = KHÔNG treo.** Freeze do huỷ VD.

### v0.64 (hot-swap) — uncommitted — FREEZE FIX ✅ nhưng move-off flaky
- Thêm `hotSwapOnVd()`: đặt app mới lên VD sống (R1 am start → R2 move-stack) → **KHÔNG teardown/cmd16** → rồi bê app cũ ra. cast() switch (isWarm) → hotSwap; first-cast → cold.
- On-car: **đổi Vietmap→Maps = NPE=0, KHÔNG TREO ✅✅✅** — castlog "✅ Đổi sang Maps (hot-swap, VD giữ sống)". Cụm hiện Maps. **FREEZE (P0) GIẢI QUYẾT.**
- NHƯNG: **Vietmap KHÔNG về màn chính** — cả Vietmap+Maps kẹt trên VD (Vietmap nhảy lên top). Bước bê-app-cũ-ra fail.

### v0.65 (hot-swap + move-off verify/retry) — uncommitted (version hiện tại) — MOVE-OFF VẪN LỖI ❌ (freeze lần 3, ~22:58)
- Fix bước ③: retry move-off tới khi app cũ rời VD; chỉ restoreFullscreen SAU khi rời.
- Root nút thắt tìm được: `am display move-stack <old> 0` ném **`TaskSnapshotController.createTaskSnapshot → DisplayContent.getRotation()` null**, trong `initializeChangeTransition` (onConfigurationChanged khi task đổi display) — **NPE một-lần** (không loop) → reparent KHỰNG → app cũ kẹt lại VD → `restoreFullscreenOnMain` kéo nó lên front cụm.
- On-car v0.65: Maps lên OK, **Vietmap vẫn không về**, 2 app scale bé ở màn chính, user nhấn home → **màn chính đơ.** Retry 4× (mỗi lần ném exception) không giúp, có thể làm tệ hơn. Evidence: `oncar-v065-freeze-225937/`.

---

## 3. HAI LỚP NPE (root cause — quan trọng nhất)

Head unit này (AOSP 10, DiLink custom + xdja container VD) **cực mong manh với cross-display transition dính VD cụm**. Mọi thao tác đụng VD lúc app transition → DisplayContent null → NPE:

| # | NPE | Ở đâu | Trigger | Trạng thái |
|---|-----|-------|---------|-----------|
| **A** | `DisplayContent.getDisplayInfo()` null @ `AppWindowToken.loadAnimation` → `handleChangingApps` | app-transition ANIMATION | **HUỶ/TÁI TẠO VD** (cmd16 warm / teardown[18,0] cold) trong lúc app transition | **LẶP → treo.** ✅ FIX bằng hot-swap (giữ VD sống). |
| **B** | `DisplayContent.getRotation()` null @ `TaskSnapshotController.createTaskSnapshot` → `initializeChangeTransition` → `onConfigurationChanged` | change-transition SNAPSHOT | **`am display move-stack <app> 0`** (bê app khỏi VD = task đổi display = config change) | **Một-lần** (không loop) → move-off KHỰNG/flaky. ❌ CHƯA fix. |

- COLD-cast lần đầu KHÔNG dính vì VD tạo MỚI TINH (không token cũ).
- v0.35 (chủ xe nói "switch mượt") ít thao tác hơn — có thể may mắn né, hoặc app cũ để mặc kệ (nhỏ trên màn chính) không ai để ý.

---

## 4. ĐÃ CHẮC CHẮN (đừng phí thời gian làm lại)

- ✅ Cast 1 app (Vietmap) từ gauges/VD-trống → OK, không treo (cold path).
- ✅ **Đặt app MỚI lên VD ĐANG SỐNG (place, không huỷ VD) → KHÔNG treo** (validated adb + v0.64 on-car). 2 app cùng VD cũng không treo.
- ✅ STOP (trả gauges) → chạy được (proven).
- ❌ **Bê app CŨ khỏi VD (`am display move-stack old→0`) = NPE B (createTaskSnapshot)** → nút thắt.
- ❌ HUỶ/TÁI TẠO VD lúc switch = NPE A (đã né bằng hot-swap).
- Phụ: app từng cast bị **lưu freeform bounds** → mở lại ở màn chính bị **scale bé** (leftover). App GL (Google Maps) hay **bounce khỏi VD** + để lại **GL SurfaceView surface kẹt** trên cụm (force-stop Maps mới sạch).

---

## 5. HƯỚNG FIX PHIÊN SAU (ưu tiên #1)

Mục tiêu: SWITCH app **KHÔNG dính cả NPE A lẫn B**. Cả A (huỷ VD) và B (move app ra) đều là thao tác cần TRÁNH.

### ⭐ #1 — "BRING-TO-FRONT ONLY" (ít thao tác nhất, né cả A+B)
Switch = **chỉ đặt app mới lên top VD sống, KHÔNG bê app cũ ra, KHÔNG teardown, KHÔNG evict.**
- App cũ nằm SAU (ẩn), app mới trên top (hiện). Cụm hiện app mới.
- Không `move-stack old→0` → **né NPE B.** Không huỷ VD → **né NPE A.**
- Đánh đổi: app tích tụ trên VD (chỉ top hiện). STOP dọn hết (stop path proven). Resize target = app top (lastCastApp).
- Rủi ro cần xử: (a) đảm bảo app mới GIỮ top (Z-order không lật — app cũ đừng re-focus); (b) `evictVd` hiện tại DÙNG move-stack → cũng dính NPE B → **phải bỏ evict hoặc đổi cách**; (c) app GL cũ (Maps) để lại surface — chấp nhận ẩn sau, hoặc xử riêng.
- Đây chính là `hotSwapOnVd` HIỆN TẠI **BỎ hẳn bước ③ (move-off) + ④ (evictVd)**.

### #2 — force-stop app cũ thay move-stack
Thay `am display move-stack old→0` bằng `am force-stop <oldApp>` → cửa sổ app cũ biến mất do process chết (KHÔNG change-transition/snapshot) → né NPE B. Cost: mất phiên app cũ (nav state). Với switch có thể chấp nhận. **CHƯA test — cần validate adb: force-stop app đang trên VD có ném NPE không.**

### #3 — Stop-rồi-cast như 2 thao tác settle riêng
STOP hẳn (verify VD biến mất + transition idle) → chờ → COLD cast. v0.63 làm back-to-back (settle 1.2s) nên treo; tách + verify-idle đủ lâu CÓ THỂ hết. Chậm. Ít ưu tiên.

### Việc phụ nên fix kèm
- **Scale bé khi mở lại ở màn chính**: app lưu freeform bounds; khi bê về display 0 phải ép fullscreen (windowingMode 1) + reset bounds. `restoreFullscreenOnMain` có làm nhưng flaky do NPE B.
- **GL surface kẹt (Google Maps)**: cân nhắc force-stop app GL cũ khi rời cụm.

---

## 6. GIT STATE (quan trọng — quyết định commit)

- **`release/v0.60-cast-hardening`** (nhánh Track 1):
  - Committed: `af21ae5` (v0.63 cold-only), `fd4890c` (collectApks→apk/).
  - **UNCOMMITTED (working tree):** `ClusterCast.kt` (có `hotSwapOnVd` = v0.64 + v0.65 move-off retry) + `build.gradle.kts` (versionName 0.65). ← **Hot-swap fix ĐANG UNCOMMITTED.**
  - Quyết định phiên sau: hot-swap ĐÃ validate né NPE A (freeze) nhưng move-off (NPE B) chưa xong. Có thể: (a) sửa hotSwapOnVd sang hướng #1/#2 rồi mới commit; hoặc (b) commit v0.65 làm mốc (freeze-fix) rồi iterate. `cleanClusterForReCast` đã bị thay bởi `hotSwapOnVd` (không còn trong code).
- **`debug/navprobe-clean`** (Track 2): đang ở **cold-only (v0.63 port)** — CHƯA có hot-swap. Khi fix xong switch → port lại sang debug (navprobe ⟂ cast, chỉ mang ClusterCast.kt+DisplayParse.kt+ClusterDiag.kt+FloatingBubbleService.kt).
- **`main`**: `ff01199` (v0.57) NGUYÊN — chưa merge gì (chưa có gì verify đủ để merge).
- APK built: `apk/ClusterNav-0.63/0.64/0.65-release.apk` + `0.63-debug.apk`.

## 7. EVIDENCE (folders trong repo root — gitignored, có log xe/PII)

| Folder | Nội dung |
|--------|----------|
| `oncar-track1-P0-warmswitch-freeze-20260723-185754/` | v0.60 warm freeze. `wm-npe-trace.txt` (NPE A, 96 blocks), `FINDING.md`, castlog |
| `oncar-trace-warmswitch-freeze-20260723-192532/` | v0.61. **`OFFLINE-FIX-NOTES.md`** (phân tích đầy đủ warm→cold), logcat-full, window-displays, castlog, git-history |
| `oncar-coldswitch-freeze-20260723-220338/` | v0.63 cold freeze. `castlog-coldswitch.txt` (clean→teardown→cold→bounce→NPE A), `npe-trace.txt` |
| `oncar-v065-freeze-225937/` | v0.65. `castlog.txt` (hot-swap + "⚠ không bê được: Exception"=NPE B), `npe.txt` |

## 8. KẾ HOẠCH PHIÊN SAU (fresh)
1. Đọc file này + `OFFLINE-FIX-NOTES.md` + castlogs.
2. Quyết hướng fix (ưu tiên #1 bring-to-front). Sửa `hotSwapOnVd` trong `ClusterCast.kt`: BỎ bước ③ move-off + ④ evictVd (né NPE B). Cân nhắc #2 (force-stop) cho việc dọn app cũ nếu cần.
3. (Nếu được) validate adb TRƯỚC khi build (như lần validate hot-swap 22:15 — rẻ, chắc).
4. Build v0.66 → cài (`adb install -r apk/ClusterNav-0.66-release.apk`) → test đổi app nhiều vòng: KHÔNG treo + app mới hiện đúng + (mong) app cũ không kẹt gây rối.
5. Test resize trên cụm (đúng target, lưu). Rồi CP/AA.
6. FAIL=0 → commit + port debug + (khi verify đủ) merge main.

**Nhớ:** reboot = NÚT NGUỒN (adb reboot hỏng). Cast 1 app không treo — dùng tạm được. ĐỪNG đoán-thử nhiều lần trên xe; validate adb trước, fix offline, test 1 phát.
