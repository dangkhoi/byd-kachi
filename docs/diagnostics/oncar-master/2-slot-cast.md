# 2 — APP-VÀO-Ô · CHIẾU CỤM · CỬA SỔ — bảng option + bài test QUYẾT ĐỊNH một buổi

> **Trạng thái**: Historical — RE ô/cast thời tiền-1.83; số đo hiện hành ở `docs/diagnostics/oncar-session-2026-09-26.md` · **Ngày**: 2026-09-19 · **Mục đích**: RE cạn mọi đường đưa app (nhất là **Waze** — app có
> activity trung chuyển) vào **ô màn ảo** trên ROM **DiLink3.0 / Android 10**, cộng đường **chiếu cụm**; mỗi option có
> **lệnh probe riêng** để nếu A hỏng thì B/C/D thử **ngay trong cùng buổi**, không phải hẹn buổi thứ hai.
> **Backlog phủ**: H1 · D-emu · X1 · X2 · U8a · ARCH-🚗.
> **Xây TRÊN** (không RE lại): `waze-into-slot-research-2026-09-14.md` (gate AOSP đã có `file:line`) ·
> `carlog-kachi-20260914-2044/session-findings.md` §X2 · `oncar-trace-2026-09-16.md` · `oncar-verify-1.63-2026-09-15.md` ·
> `oncar-playbook-kachi-1.53.md` §2.4/§2.12/§2.13/§2.15/§2.17 · `dashcast-src/CHANGELOG.md` · `jadx-dashcast` · `jadx-amap`.
> **Nhãn**: `[ĐO source]` = đọc được từ source/dump có `file:line` · `[SUY]` = suy luận từ source, chưa chạy ·
> `[CHƯA BIẾT]` = không có dữ liệu.
> **Không chạy gradle, không commit** trong lượt viết doc này.

---

## 0. Kết luận một trang (đọc cái này trước khi ra xe)

1. **Ô app đã CHẠY trên xe.** `[ĐO source]` `carlog-kachi-20260914-2044/session-findings.md` — *"VietMap chạy trong ô,
   YouTube phát MV trong ô"*; `oncar-trace-2026-09-16.md` §1 — *"YouTube trong `kachi-slot-0` (display 7, 1673×935,
   density 200)"*. ⇒ **Câu hỏi của buổi này KHÔNG phải "ô có chạy không"** mà hẹp hơn: **đúng lớp app có activity
   trung chuyển (Waze) thì làm sao.**
2. **Gate chặn Waze đã biết chính xác** (§2) và nó **chỉ nổ với lời gọi từ uid CỦA APP**, không nổ với lời gọi từ
   shell uid 2000. ⇒ Mọi option bên dưới chỉ là bốn cách khác nhau để **thoát đúng một dòng điều kiện**.
3. **Ba bộ điều kiện then chốt trên xe ĐÃ ĐO XONG, nên ba option rụng trước khi ra xe** (§3: A, B, F) — tiết kiệm
   nửa buổi.
4. **Hai enabler của đường freeform đã BẬT SẴN trên xe**: `[ĐO source]`
   `carlog-kachi-20260914-2044/10-settings-flags.txt` →
   `global/enable_freeform_support 1` · `global/force_resizable_activities 1`.
   Đây là chính hai cờ mà DashCast phải tự bật trên DL5 (`dashcast-src/CHANGELOG.md` v1.2.32). ⇒ **option C có nền.**
5. **Một option MỚI, trần cao nhất, chưa ai thử: nhờ chính ROM tạo display** (§3-G). VD cụm của ROM do
   **uid 1000 (SYSTEM)** làm chủ `[ĐO source]` session-findings §X2 — *"cụm = display 2 `fission_bg_xdjaVirtualSurface`,
   owner com.xdja.containerservice **uid 1000**"* — mà gate chỉ nổ khi `displayOwnerUid != SYSTEM_UID`
   (`ActivityStackSupervisor.java:1096-1106`). ⇒ `[SUY mạnh]` **Waze chiếu lên CỤM thì gate không nổ**, dù Waze vào ô
   Kachi thì nổ. Đây là phép thử rẻ nhất có khả năng đổi cả thiết kế (§4 bước 3).
6. **Một option nữa có cơ chế ĐÃ PROVEN trên đúng ROM này, mà repo chưa hề dùng cho ô** (§3-D):
   `am stack move-task <taskId> <stackId> true` — `[ĐO trên xe 2026-08-01, BYD DiLink3 Android 10]`
   `docs/archive/diagnostics/carplay-move-task-success-2026-08-01.md`: đưa **CarPlay** từ display 0 lên display 1
   (`bounds=[0,0][1920,720]`, `visible=true`, **0 crash**) rồi trả về display 0 an toàn. Repo dùng nó cho **cast/CP/AA**
   (`AppMover.kt:41,112`), **chưa bao giờ cho ô**. Và nó vá đúng chỗ Waze hỏng: **trampoline chỉ nổ một lần** lúc
   cold-start.
7. ⚠ **MỘT LỆNH TRONG ĐỀ BÀI BỊ CẤM.** Option (d) "move-stack" = `am display move-stack` **đã đo là treo
   system_server 3/3 lần trên chính DiLink3**, task biến mất, phiên CarPlay rớt phải cắm lại cáp — `[ĐO source]`
   `core/.../carexec/CarExecClusterProjectionCatalog.kt:52-80` (`risk = MAY_HANG_SYSTEM`, `fieldNote` "CẤM DÙNG") +
   `CarExecClusterLifecycleCatalog.kt:68,85-93` + trace `docs/archive/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md`.
   **ĐỪNG CHẠY.** Đường thay thế an toàn cùng mục đích chính là §3-D ở trên.
8. ⚠ **Doc cũ có hai chỗ đã lạc hậu, sửa ở đây** (§7): playbook §2.12.X2 và `project-context.md` còn ghi fallback cast
   là `am display move-stack`; code **đã đổi** sang `am stack move-task` từ 2026-09-15.

---

## 1. Sự thật nền trên ĐÚNG chiếc xe này (mọi dòng có nguồn)

| Sự thật | Mức | Nguồn |
|---|---|---|
| DiLink3.0, Android **10** (QKQ1.210910.001 **release-keys**), arm64 | `[ĐO source]` | `oncar-session-2026-09-14-findings.md` §Xe |
| Kachi uid **10135** (09-14) / **10138** (09-15) — **KHÔNG** system | `[ĐO source]` | cùng trên; `DisplayParse.kt:185-187` KDoc |
| Kachi chữ ký **177b2fc5** — **KHÔNG** platform; giữ **0** quyền `BYDAUTO_*_SET` | `[ĐO source]` | session-findings §"HAL write" |
| `enable_freeform_support=1`, `force_resizable_activities=1` | `[ĐO source]` | `carlog-…-2044/10-settings-flags.txt` |
| Cụm = **display 2** `fission_bg_xdjaVirtualSurface`, owner `com.xdja.containerservice` **uid 1000**, `FLAG_OWN_CONTENT_ONLY` + `FLAG_PRESENTATION`, 1920×720 @320 | `[ĐO source]` | session-findings §X2; `oncar-trace-2026-09-16.md` §1 |
| VD cụm **chỉ tồn tại SAU khi mở projection** (AutoContainer tạo bất đồng bộ sau profile 35) | `[ĐO source]` | `ClusterDisplayResolver.kt:14-21` |
| **Sau reboot, display 1 = `kachi-slot-0`** (VD của CHÍNH launcher), cụm = display 2 | `[ĐO source]` | `DisplayParse.kt:184-188` KDoc |
| Ô app dùng `VdAppHost` → `createVirtualDisplay(name, w, h, dpi, surface, 8 or 256)` = `OWN_CONTENT_ONLY \| DESTROY_CONTENT_ON_REMOVAL` ⇒ **riêng tư, chủ = uid Kachi** | `[ĐO source]` | `VdAppHost.kt:142-143` |
| Ô app mở bằng shell: `am start --display <vd> --windowingMode 1` | `[ĐO source]` | `VdAppHost.kt:231` (`FreeformLaunch.launchOnDisplayCmd`) |
| Cast mở projection: `service call AutoContainer 2 i32 1000 i32 {30,16,35}`; đóng `{18,0}` | `[ĐO source]` | `ProjectionManager.kt:31-64` |
| Cùng chuỗi 30/16/35 + tên service **PascalCase `AutoContainer`** trên DL3/DL4; DL5 dùng `auto_container` | `[ĐO source]` | `jadx-dashcast/.../ClusterManager.java:21-27,62-63`; CHANGELOG v1.2.27 |
| ROM có service app-level `getSystemService("auto_container"/"AutoContainer")` → `android.os.AutoContainerManager.sendInfo(int,int,String)` | `[ĐO source]` | `jadx-amap/.../AmapService.java:127-129,196-204`; `jadx-amap/sources/android/os/AutoContainerManager.java:13` |
| DL5 fission tạo **HAI** display (`shared_fission_bg_XDJAScreenProjection_0/1`, id 3+4, mỗi cái 1920×720) | `[ĐO source]` | `dashcast-src/CHANGELOG.md` v1.2.56 |
| DL5 **cắt** `set-task-windowing-mode`, `task resize` **no-op im lặng**; **DL3 (API 29) thì `am task resize` khoẻ** | `[ĐO source]` | CHANGELOG v1.2.72 / v1.2.59 / v1.2.26 (*"DL3 … `am task resize` is healthy on API 29"*) |
| `am display move-stack` treo system_server **3/3** trên DiLink3 (NPE `DisplayContent.moveStackToDisplay`, AOSP a10 `DisplayContent.java:2401-2402`, không có bản vá) | `[ĐO source]` | `CarExecClusterProjectionCatalog.kt:52-80` |
| Đường thay thế đang dùng: `am stack move-task <taskId> <stackId> true` (proven cho CP/AA) | `[ĐO source]` | `AppMover.kt:41,112,94-118` |
| Cờ chặn freeform trong code: `embedding = shell != null \|\| SlotAppHost.embeddingUsable(this)`; mọi hàm freeform mở đầu `if (embedding()) return` | `[ĐO source]` | `KachiHomeActivity.kt:173`; `LauncherWindows.kt:129,167,183` |
| `SpeedBadgeOverlay` thử **display 1 TRƯỚC**, chỉ lùi PRESENTATION khi id 1 null | `[ĐO source]` | `SpeedBadgeOverlay.kt:44,138-141` |

---

## 2. Gate — một dòng điều kiện, bốn cửa thoát

`[ĐO source]` `waze-into-slot-research-2026-09-14.md` §1.1 (đã trích AOSP `android-10.0.0_r47` `file:line`):

```
ActivityStackSupervisor.canPlaceEntityOnDisplay()            :349-367
  displayId == DEFAULT_DISPLAY ........................... return TRUE ngay   (:351-354)  ← cửa thoát #1
  → isCallerAllowedToLaunchOnDisplay()
      caller có INTERNAL_SYSTEM_WINDOW ................... return TRUE        (:1084-1091) ← cửa thoát #2
      GATE:  display.getType()==TYPE_VIRTUAL
          && displayOwnerUid != SYSTEM_UID  ............... (bỏ qua GATE nếu chủ = 1000)   ← cửa thoát #3
          && displayOwnerUid != aInfo.applicationInfo.uid
        → (flags & FLAG_ALLOW_EMBEDDED)==0  .............. return FALSE       (:1101-1106)
        → checkPermission(ACTIVITY_EMBEDDING) DENIED ..... return FALSE       (:1107-1113) ← cửa thoát #4 (cần CẢ HAI)
      !display.isPrivate() ............................... return TRUE        (:1115)
      displayOwnerUid == callingUid | uidPresentOnDisplay . return TRUE        (:1131)
```

**Chuỗi thật của Waze** `[ĐO source]` (log nguyên văn ở RE doc §2.3):
`am start --display <vd>` từ **shell uid 2000** ⇒ ✅ lên màn ảo (cửa thoát #2) → 2,4 s sau **Waze tự** mở
`.MainActivity` từ **uid 10144** ⇒ ❌ `Failed to put TaskRecord{…} on display 3` ⇒ **cả task rơi về display 0 toàn màn**.

Hai điều rút ra, quyết định toàn bộ §3:
- Cửa thoát #3 (**chủ display = SYSTEM**) là cửa **duy nhất** phủ được lời gọi của chính app ⇒ option B và G.
- Cửa thoát #1 (**display 0**) không có điều kiện nào ⇒ option C.

---

## 3. BẢNG OPTION — mọi đường đưa Waze vào ô

Cột **Outcome** viết sẵn cả hai nhánh để trên xe chỉ đọc-và-đi, không phải nghĩ.

### A — `allowEmbedded` trên activity đích (app nguyên bản) — ❌ LOẠI TRƯỚC KHI RA XE

| | |
|---|---|
| Cơ chế | Cửa thoát #4, nửa thứ nhất. |
| Trạng thái | `[ĐO source]` `aapt2` trên APK lấy từ máy: `com.waze 5.23.0.2` khai `resizeableActivity=true` ở `<application>`, **0 chỗ** khai `allowEmbedded` (RE doc §2.2). App bên thứ ba ⇒ không sửa được manifest của người khác. |
| Probe xác nhận (1 dòng, chỉ đọc, chạy cho chắc) | `adb shell "dumpsys package com.waze \| grep -icE 'allowEmbedded\|FLAG_ALLOW_EMBEDDED'"` |
| Outcome | `0` → xác nhận loại, **đi tiếp ngay**. `>0` (bản Waze mới đổi) → thử lại C0 (mở Waze vào ô) trước mọi thứ khác. |

### B — Kachi chạy uid system (1000) — ❌ LOẠI, đã đo hai lần

| | |
|---|---|
| Cơ chế | Cửa thoát #3: VD của ô do uid 1000 tạo ⇒ GATE bị bỏ qua ⇒ Waze vào ô **không cần** freeform. Đây là option **quyết định nhất** nếu đúng. |
| Trạng thái | `[ĐO source]` uid **10135** (09-14) / **10138** (09-15); chữ ký **177b2fc5** ≠ platform. |
| Probe (2 dòng, chỉ đọc — vẫn chạy vì rẻ và nó quyết định cả cây) | `adb shell "dumpsys package com.byd.launcher \| grep -E 'userId=\|sharedUser\|pkgFlags'"`<br>`adb shell "dumpsys package com.byd.launcher \| grep -A1 -E 'INTERNAL_SYSTEM_WINDOW\|ACTIVITY_EMBEDDING\|MANAGE_ACTIVITY_STACKS'"` |
| Outcome | `userId=1000` → **mọi option khác BỎ**, làm lại ô bằng đường nhúng thẳng và kết thúc mảng này. `userId=10xxx` → loại B, đi tiếp. |
| B2 (nhánh phụ, chỉ đọc) — đưa Kachi vào `/system/priv-app`? | `adb root` ; `adb shell "mount \| grep -E ' /system '"` ; `adb shell "ls -ld /system/priv-app"` |
| Outcome B2 | `adb root` bị từ chối **hoặc** /system `ro` → đóng hẳn B (`release-keys` production, đúng kỳ vọng). Thành công → ghi lại, **KHÔNG ghi gì vào /system trong buổi này** (không hoàn tác được, ngoài phạm vi). |

### C — Freeform trên **display 0** (cửa thoát #1) — 🔬 PROBE CHÍNH, nền đã sẵn

| | |
|---|---|
| Cơ chế | `canPlaceEntityOnDisplay` trả TRUE **ngay dòng đầu** cho `DEFAULT_DISPLAY` ⇒ trampoline của Waze không bao giờ bị chặn. Đổi lại: có **thanh caption freeform** (~36px, `Sp.CAPTION_INSET` đã trừ sẵn — `LauncherWindows.kt:200`). |
| Vì sao có cơ sở | `[ĐO source]` hai cờ đã BẬT trên xe (§1); `[ĐO source]` máy ảo chạy **đúng với Waze**, bounds đúng từng pixel, và **không bị đẩy đi** sau khi Waze tự mở MainActivity (RE doc §2.4); `[ĐO source]` DL5 cắt các verb này nhưng **DL3/API 29 thì `am task resize` khoẻ** (CHANGELOG v1.2.26). |
| Vì sao hôm nay không chạy | `[ĐO source]` `KachiHomeActivity.kt:173` — hễ dò ra dadb (tức LUÔN trên xe) thì `embedding()` = true ⇒ `LauncherWindows.reflow/placeApp/closeApp` return ngay. ⇒ **Probe không cần bản build mới**: bắn thẳng bằng adb. |
| Probe (5 lệnh, có hoàn tác) | `adb shell settings get global enable_freeform_support` ; `… force_resizable_activities`<br>`adb shell "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --display 0 --windowingMode 5 -n com.waze/.FreeMapAppActivity"`<br>`sleep 6; adb shell "am stack list \| grep -iE 'waze\|mWindowingMode\|displayId'"`  ← **T=+6 s là mốc quan trọng**: trampoline nổ ở ~2,4 s<br>`adb shell "am task resize <taskId> 100 200 900 800"; echo exit=$?`<br>`sleep 2; adb shell "am stack list \| grep -A3 -i waze"` ← **bounds có ĐỔI THẬT không** (DL5 exit 0 mà vô tác dụng) |
| Outcome | **bounds đổi đúng + task vẫn `displayId=0` + `mWindowingMode=freeform`** ⇒ C **ĐẠT** ⇒ đường lùi "ô nào bị đẩy thì chuyển ô đó sang freeform" khả thi; ghi chi phí kiến trúc (RE doc §4.2/§4.4: `embedding()` phải thành trạng thái **theo từng ô**, đụng 5 chỗ). <br>**exit 0 mà bounds KHÔNG đổi** ⇒ DL3 cũng no-op như DL5 ⇒ C **RỤNG**, sang D. <br>**`IllegalArgumentException: resizeTask not allowed`** ⇒ app bị coi `UNRESIZEABLE` ⇒ **KHÔNG kết luận C rụng ngay**: thử đòn bẩy density (D3) trên display 0 rồi đo lại. <br>**`Unknown command`** ⇒ verb bị cắt ⇒ C rụng, sang D. |
| ⚠ Hai bẫy đã `[ĐO trên xe]` (đừng suy diễn lại) | (1) `force_resizable_activities=1` **chỉ tác dụng LÚC LAUNCH, không giúp resize runtime** — `docs/archive/diagnostics/carplay-move-task-success-2026-08-01.md` bước T2b đã thử đúng điều này và **thất bại**. ⇒ thấy resize bị từ chối thì **đừng** đi bật lại cờ đó. (2) `am task resize` bị `UNRESIZEABLE` từ chối **thẳng bằng ngoại lệ** (khác DL5 no-op-im-lặng) ⇒ đọc kỹ stderr để phân biệt **ba** kết cục, không gộp thành "resize hỏng". |
| Phụ (U8a — cùng lệnh, không tốn thêm) | Chụp màn lúc cửa sổ freeform **đang có focus**: `adb exec-out screencap -p > /tmp/u8a-freeform-focus.png`. Câu hỏi treo từ playbook 1.47 U8(a): **ROM có hiện lại thanh trạng thái Android không.** |
| Hoàn tác | `adb shell am force-stop com.waze` ; `adb shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity` |

### D — `am stack move-task` vào stack đã nằm trên VD của ô — 🔬 **cơ chế ĐÃ PROVEN trên xe**, chưa ai thử cho **ô**

| | |
|---|---|
| Cơ chế | `moveTaskToStack` chuyển **TASK vào một stack đã có**, **không** reparent cả stack qua display ⇒ không đi qua `ActivityStarter`/`canPlaceEntityOnDisplay`, và không rơi vào cửa sổ `mDisplayContent=null`. `[ĐO source]` `docs/archive/diagnostics/carplay-move-task-success-2026-08-01.md` §"Bài học 1": `am stack move-task` → `TaskStack.positionChildAt` → gán `task.mStack` **TRƯỚC** `addChild`; còn `am display move-stack` → `DisplayContent.moveStackToDisplay` → gỡ stack khỏi display cũ → NPE. |
| ⚠ Vì sao đây là option mạnh nhất sau G | `[ĐO trên xe 2026-08-01, BYD DiLink3 Android 10]` cùng ROM này: `am stack move-task 15 6 true` đưa **CarPlay** từ display 0 **lên display 1**, `bounds=[0,0][1920,720]`, `visible=true`, **0 crash**, stack cũ tự dọn; rồi `am stack move-task 15 12 true` trả về display 0 **an toàn**. ⇒ cơ chế **không phải giả thuyết**. Repo dùng nó cho **cast/CP/AA** (`AppMover.kt:41,112`) nhưng **chưa bao giờ cho ô**. Và nó vá đúng chỗ Waze hỏng: trampoline chỉ nổ **một lần** lúc cold-start — task đã tồn tại thì không còn ai gọi `canPlaceEntityOnDisplay`. |
| Điều kiện bắt buộc | `[ĐO source]` cùng doc §"Bài học 3": **stack phải TỒN TẠI SẴN** trên display đích — `move-task` chuyển task *vào* một stack, không tạo stack. Cụm/VD rỗng ⇒ phải dựng stack trước (đúng mẹo `AppMover.findOrCreateClusterStack`, `AppMover.kt:133-144`). |
| D1 — sửa SAU khi bị đẩy | 1) mở Waze vào ô như bình thường (Kachi UI) → nó rơi về display 0<br>2) `adb shell "am stack list \| grep -iE 'waze\|Stack #'"` → lấy `taskId` của Waze **và** `stackId` của stack đang nằm trên VD ô (`kachi-slot-*`)<br>3) VD ô chưa có stack ⇒ dựng: `adb shell "am start --display <vdSlot> --windowingMode 1 -n com.android.settings/.Settings"`<br>4) `adb shell "am stack move-task <wazeTaskId> <slotStackId> true"`<br>5) `sleep 2; adb shell "am stack list \| grep -B2 -A4 -i waze"` ; `sleep 10` rồi đọc lại (Waze có thể trampoline lần nữa) |
| D2 — **khởi động ẤM rồi mới chuyển** (dễ thắng nhất) | Mở Waze **bình thường trên display 0 trước**, đợi **vào map** (MainActivity đã tồn tại ⇒ hết trampoline), rồi mới chạy bước 4 của D1. |
| D3 — đòn bẩy **density** nếu app từ chối resize | `[ĐO trên xe]` cùng doc §"Bài học 2": app `RESIZE_MODE_UNRESIZEABLE` từ chối **mọi** `am task resize` (`IllegalArgumentException: resizeTask not allowed`), **nhưng** `wm density <dpi> -d <display>` làm app tự layout lại cho vừa khung → đã chạy cho CarPlay. Công thức doc đó đề xuất: `density_gốc × (cao_đích / cao_nguồn)`.<br>⚠ Với **ô** thì Kachi **đã** làm việc này **không qua shell** — `VdAppHost` đặt density ngay lúc `createVirtualDisplay`/`resize` (`VdAppHost.kt:198-208`, `SlotDensity.forTablet`) ⇒ **đừng** bắn `wm density -d <vdSlot>` tay (nó ghi `display_settings.xml`, sống qua reboot, phải dọn). Chỉ dùng `wm density` khi đo trên **display 0 / cụm**. |
| Outcome | Waze `displayId=<vdSlot>` và **còn ở đó sau 10 s** ⇒ D **ĐẠT** ⇒ đường lùi rẻ nhất (không đổi kiến trúc `embedding()`, chỉ thêm bộ "đo rồi sửa" đúng chỗ RE doc §4.1 đề xuất). Chụp ảnh ô. <br>Task về display 0 / biến mất / ô đen ⇒ D rụng. <br>⚠ **system_server chết** (màn nháy, launcher restart) ⇒ **DỪNG cả nhánh D**, ghi nguyên văn logcat: nghĩa là ranh giới **display 0 ↔ VD-của-app** khác ranh giới **display 0 ↔ VD-của-ROM** đã proven 08-01, và đó là phát hiện lớn nhất phải mang về. |
| Hoàn tác | `am stack move-task <wazeTaskId> <stack-trên-display-0> true` (đường trả về đã proven) hoặc `am force-stop com.waze`; + `am force-stop com.android.settings`; về Kachi. |

### E — `am display move-stack` — 🚫 **CẤM CHẠY**

| | |
|---|---|
| Trạng thái | `[ĐO source]` **treo system_server 3/3 lần trên chính DiLink3** (2026-08-01): NPE `TaskSnapshotController.createTaskSnapshot ← AppWindowToken.initializeChangeTransition ← DisplayContent.moveStackToDisplay`, **task biến mất khỏi hệ thống**, phiên CarPlay rớt **phải cắm lại cáp**. Gốc AOSP a10 `DisplayContent.java:2401-2402`, **không có bản vá ở bất kỳ nhánh release nào**. Kích hoạt khi vượt ranh giới FREEFORM — mà display 0 fullscreen ↔ VD freeform nên **lần nào cũng vượt**. |
| Nguồn | `core/.../carexec/CarExecClusterProjectionCatalog.kt:52-80` (`risk = CandidateRisk.MAY_HANG_SYSTEM`) · `CarExecClusterLifecycleCatalog.kt:68,85-93` · bài canh `AppMoverMoveStackFallbackTest.kt:75-78` chặn repo phát lại lệnh này · trace crash 3/3 lần: `docs/archive/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md` · phân tích AOSP: `docs/archive/diagnostics/carplay-aa-cluster-placement-research-2026-08-01.md`. |
| Probe | **KHÔNG CÓ.** Không chạy. Ai muốn gỡ cấm phải sửa **hai** lỗi cùng lúc (tham số 1 là **stack** id chứ không phải task id — `CarExecClusterProjectionCatalog.kt:55-58`) và phải có phép đo mới. |

### F — cờ display "tin cậy" (`VIRTUAL_DISPLAY_FLAG_TRUSTED` / `ADD_TRUSTED_DISPLAY`) — ❌ N/A trên xe này

| | |
|---|---|
| Trạng thái | `[ĐO source]` RE doc §1.2: `VIRTUAL_DISPLAY_FLAG_TRUSTED` **không tồn tại ở Android 10**; nó là khái niệm A11+, và ở A12 `DisplayManagerService.java:2615-2620` đòi `ADD_TRUSTED_DISPLAY` (signature). Xe = **Android 10** ⇒ khái niệm không có để mà xin. |
| Probe | Không cần lệnh nào trên xe (API level đã `[ĐO]`). Giữ mục này cho **DL5/A12** về sau. |
| Ghi chú kèm | Cùng §1.2: `FLAG_PUBLIC` **không cần quyền** khi đi kèm `OWN_CONTENT_ONLY`, nhưng **không đổi kết quả** — gate xét *type + uid chủ*, không xét cờ. ⇒ đổi cờ `8 or 256` của `VdAppHost.kt:143` là **vô ích**, đừng thử. |

### G — nhờ **chính ROM** tạo display (AutoContainer / fission) — 🔬 OPTION MỚI, trần cao nhất

| | |
|---|---|
| Cơ chế | VD do ROM tạo có chủ **uid 1000** ⇒ cửa thoát #3 ⇒ **mọi app, kể cả Waze, được phép tự mở activity trên đó.** |
| Bằng chứng | `[ĐO source]` cụm = `fission_bg_xdjaVirtualSurface` **owner uid 1000** (session-findings §X2). `[ĐO source]` ROM phơi service app-level `getSystemService("AutoContainer")` → `AutoContainerManager.sendInfo(int,int,String)` (`jadx-amap/.../AmapService.java:127-129`). `[ĐO source]` DL5 fission tạo **HAI** display 1920×720 (CHANGELOG v1.2.56) ⇒ khái niệm "nhiều vùng" có thật trong họ ROM này. |
| **G1 — Waze lên CỤM** (phép thử rẻ nhất, giá trị cao nhất) | Bật Cast (Kachi › Cài đặt › Chiếu màn lên cụm) → chiếu **Waze**.<br>`adb shell "am stack list \| grep -iE 'waze\|displayId'"` ; `adb shell "logcat -d -s ActivityTaskManager \| grep -i 'Failed to put'"` |
| Outcome G1 | Waze `displayId=2` và **không** có dòng `Failed to put` ⇒ `[SUY]` của §0.5 **đúng** ⇒ (a) Waze dùng được ngay qua cụm; (b) chứng minh gate phụ thuộc **chủ display**, mở đường G2. Có `Failed to put` ⇒ giả thuyết sai, ghi lại (quan trọng: nó bác một suy luận, phải ghi). |
| **G2 — ROM tạo được mấy display?** (chỉ ĐỌC, không brute-force) | `adb shell "dumpsys display \| grep -iE 'Display [0-9]+:\|fission\|xdja\|virtual:\|type VIRTUAL\|FLAG_'" > /tmp/g2-displays.txt` (chạy **trước** và **sau** khi mở projection, diff)<br>`adb shell "service list \| grep -iE 'AutoContainer\|auto_container\|xdja\|container\|fission'"`<br>`adb shell getprop ro.build.system.fission_single_os` ← chính prop `AmapService.java:138` đọc<br>`adb shell "dumpsys package com.xdja.containerservice \| grep -E 'userId=\|versionName'"` |
| Outcome G2 | Sau projection có **≥2** display fission/xdja ⇒ mở việc off-car: dùng display của ROM làm **ô** (phủ mọi app, hết cần gate). Chỉ **1** ⇒ ROM DL3 một vùng ⇒ G2 đóng, giữ G1. |
| ⚠ An toàn | **CHỈ** dùng các mã đã proven `30/16/35` (mở) và `18/0` (đóng) — `ProjectionManager.kt:31-64`. **KHÔNG brute-force mã `sendInfo` lạ**: đây là service điều khiển bề mặt cụm trước mặt người lái, và mã DL5 ≠ mã DL3 (CHANGELOG v1.2.27 — DL5 chỉ cần 16, DL3 cần 30→16→35). |

### H — **mod APK** (thêm `allowEmbedded` / bỏ trampoline) — 🔬 probe 2 dòng rồi quyết

| | |
|---|---|
| Tiền lệ | Dự án **đã** ship VietMap mod (`VietMap-3.4.0-mod-cluster`, receiver `posrx`) và roster nav đã có `com.chisadin.wazemod`. ⇒ mod không phải ý tưởng mới ở đây. |
| H1 — thêm `android:allowEmbedded="true"` | `[SUY]` **KHÔNG đủ một mình**: gate đòi **CẢ HAI** — `FLAG_ALLOW_EMBEDDED` trên activity đích **VÀ** caller (= chính Waze) giữ `ACTIVITY_EMBEDDING` (`ASS.java:1101-1113`). Quyền đó `[SUY]` là `signature\|privileged` (trên máy ảo chỉ thấy **shell** giữ nó — RE doc §2.3) ⇒ APK sideload không giữ được. |
| Probe H1 (chỉ đọc) | `adb shell "dumpsys package \| grep -A3 'permission android.permission.ACTIVITY_EMBEDDING'"` → đọc `protectionLevel`<br>`adb shell "pm list packages \| grep -i wazemod"` ; nếu có: `adb shell "dumpsys package com.chisadin.wazemod \| grep -icE 'allowEmbedded\|ACTIVITY_EMBEDDING'"` |
| Outcome H1 | `protectionLevel` có `signature`/`privileged` ⇒ H1 **rụng** (đúng `[SUY]`). Nếu là `normal`/`dangerous` (bất ngờ) ⇒ H1 **sống**, thành đường rẻ nhất cho mọi app: mod manifest + xin quyền. |
| H2 — mod để **bỏ trampoline** | Không cần quyền nào: gate chỉ nổ ở lời gọi **thứ hai**. Nếu bản mod trỏ launcher thẳng vào `MainActivity` (hoặc `FreeMapAppActivity` không mở activity thứ hai) thì gate không bao giờ chạm. `[CHƯA BIẾT]` bản `wazemod` hiện có còn trampoline không. |
| Probe H2 | WazeMod đã cài: đặt **WazeMod** (không phải `com.waze`) vào ô → `adb shell "logcat -d -s ActivityTaskManager \| grep -iE 'wazemod\|Failed to put'"` |
| Outcome H2 | Ở lại trong ô ⇒ **giải quyết xong bằng lựa chọn app**, ghi vào tài liệu người dùng. Vẫn bị đẩy ⇒ H2 cần smali (việc off-car, ngoài buổi này). |

### I — chấp nhận suy giảm (đường ra sản phẩm nếu A–H đều rụng)

| | |
|---|---|
| Nội dung | Waze **không** vào ô; đường dùng được: (a) chiếu Waze lên **cụm** (G1, nếu đạt); (b) mở Waze **toàn màn** qua `AppOpener` (đã chạy); (c) ô hiện **thẻ app + nhãn nói thật lý do**, không im lặng. |
| Vì sao đáng ghi | `[ĐO source]` RE doc §4.3/§4.4: làm đường lùi freeform là **đổi kiến trúc** (`embedding()` cờ toàn cục → trạng thái theo ô, đụng `WorkspaceView.makeSlot` · `LauncherWindows` ×4 · `WorkspaceRenderPlanner` · vòng đời `VdAppHost` · `OverlayHeads`) — đúng vùng vừa sinh P-bug1/P-bug2/P9-bước-3. ⇒ **chỉ trả giá đó khi C hoặc D đã ĐẠT trên xe**, không trả trước. |
| Quyết định cần owner | Nếu chỉ D đạt (không C): làm bộ "đo rồi sửa" nhẹ; nếu chỉ C đạt: trả giá kiến trúc; nếu G2 đạt: **đổi hẳn** nền ô sang display của ROM (rẻ nhất về lâu dài, phủ mọi app). |

---

## 4. THỨ TỰ CHẠY MỘT BUỔI (rẻ→đắt, đọc→ghi; mỗi bước có nhánh đi tiếp)

> Mọi bước là adb thuần ⇒ **không cần bản APK mới** cho §4. Mở một tệp log duy nhất:
> `mkdir -p car-logs/slotcast && exec > >(tee car-logs/slotcast/session.txt) 2>&1`

| # | Bước | Thời lượng | Nhánh |
|---|---|---|---|
| 0 | **Baseline chỉ đọc**: option **B** (2 dòng) + **A** (1 dòng) + **H1** (2 dòng) + `dumpsys display` trước projection | 5' | `userId=1000` ⇒ nhảy thẳng kết thúc mảng (B thắng). `ACTIVITY_EMBEDDING` không phải signature ⇒ ưu tiên H1 lên trước C |
| 1 | **Ô hiện trạng còn tốt không** (không phải Waze): VietMap + YouTube vào ô, `am stack list`, đếm `kachi-slot-*` (H2 §2.17: số VD **phải bằng** số ô) | 10' | Hỏng ⇒ chữa trước, mọi thứ sau vô nghĩa |
| 2 | **Waze vào ô — tái hiện triệu chứng** (mốc `[ĐO]` để so): đặt Waze vào ô, `logcat -s ActivityTaskManager \| grep -i 'Failed to put'`, `am stack list` ở **T+3 s và T+10 s**, 1 ảnh | 10' | **Ở LẠI trong ô** ⇒ 🎉 gate không nổ trên ROM này ⇒ bỏ C/D/G1, chỉ ghi lại. Bị đẩy ⇒ đi tiếp |
| 3 | **G1 — Waze lên CỤM** (phép thử đổi-thiết-kế, rẻ) | 10' | Đạt ⇒ ghi + vẫn chạy 4,5 để có đường trong-ô. Không đạt ⇒ ghi (bác `[SUY]`) |
| 4 | **C — freeform display 0** (5 lệnh + ảnh U8a) | 15' | Đạt ⇒ đánh dấu C-ĐẠT. Không ⇒ ghi nguyên văn (`Unknown command` vs `exit 0 không đổi bounds` là **hai kết luận khác nhau**) |
| 5 | **D2 rồi D1 — move-task vào VD ô** (D2 trước vì dễ thắng hơn) | 15' | ⚠ system_server chết ⇒ **DỪNG nhánh D**, ghi logcat, không thử lại |
| 6 | **G2 — kiểm kê display của ROM** (diff `dumpsys display` trước/sau projection) | 10' | ≥2 fission ⇒ mở việc off-car trần-cao |
| 7 | **H2** (chỉ nếu WazeMod có trên máy) | 5' | — |
| 8 | **Cast/X2 §5** — cả khối | 25' | — |
| 9 | **Dọn** (§6) | 5' | — |

**Cây quyết định sau buổi** (để owner chốt ngay tại xe) — xếp theo **trần giá trị ÷ chi phí**, không theo thứ tự chạy:

| Ưu tiên | Điều kiện | Việc sinh ra | Vì sao xếp đây |
|---|---|---|---|
| 0 | **bước 2 đạt** (Waze ở lại ô) | không làm gì, chỉ ghi | gate không nổ trên ROM này |
| 1 | **G2 đạt** (ROM tạo ≥2 display) | đổi nền ô sang display của ROM | phủ **mọi** app, hết cần gate, rẻ nhất về lâu dài |
| 2 | **D đạt** | bộ "đo rồi sửa" (RE doc §4.1) + đặt task vào stack trên VD ô | cơ chế **đã proven trên xe**; **không** đổi kiến trúc `embedding()` |
| 3 | **C đạt** | trả giá kiến trúc: `embedding()` → trạng thái **theo ô** (5 chỗ) | chạy được nhưng đắt, và đụng vùng vừa sinh P-bug1/P-bug2 |
| 4 | **H1/H2 đạt** | giải bằng **chọn app** (tài liệu người dùng) | 0 dòng code, nhưng chỉ đúng cho Waze |
| 5 | **G1 đạt** (chỉ cụm) | Waze dùng qua **cụm** | không phải "vào ô" nhưng dùng được thật |
| 6 | tất cả rụng | **I** — suy giảm có nói lý do | không im lặng |

---

## 5. CAST / X2 — khối xác nhận (mọi mục đều là 🚗 chưa từng đo sau bản sửa)

### 5.1 Dò display cụm động

```bash
adb shell "dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja|virtual:'"   # = ClusterDisplayResolver.DETECT_CMD
adb shell "logcat -d -s SimpleCast | grep -iE 'cluster display|detect|profile 35|-d [0-9]'" | tail -20
```
- **PASS**: log in ra id cụm THẬT (vd 2) **sau** profile 35; `am stack list` thấy app + `ClusterBlackActivity` ở đúng id đó.
- **FAIL kiểu 1**: `detect … -1` nhiều lần ⇒ VD fission lên chậm hơn ngân sách `AWAIT_ATTEMPTS=12 × 500 ms = 6 s`
  (`ClusterDisplayResolver.kt:36-38`) ⇒ dán log, nâng hằng.
- **FAIL kiểu 2**: dò ra id mà id đó là `kachi-slot-*` ⇒ guard R2 (`DisplayParse.isOwnedVirtualDisplay`) hỏng ⇒ dán
  nguyên `DETECT_CMD`. `[ĐO source]` đây **đã từng xảy ra**: sau reboot display 1 = `kachi-slot-0` (`DisplayParse.kt:184-188`).

### 5.2 `am start --display <cụm>` có bị chặn không — **dự đoán đã đổi**

`[SUY]` Cụm do **uid 1000** làm chủ và `[ĐO source]` dump cụm ghi `FLAG_OWN_CONTENT_ONLY + FLAG_PRESENTATION`
(**không** thấy `FLAG_PRIVATE`) ⇒ theo §2 gate **không nổ** và `:1115 !isPrivate()` trả TRUE ⇒ **R1 am-start lẽ ra
THÀNH CÔNG**. `Permission Denial` ngày 09-14 là do hardcode `--display 1` (**không phải** cụm), không phải do bản chất
VD-của-uid-khác.

```bash
adb shell "logcat -d -s SimpleCast | grep -iE 'R1 did not land|Permission Denial|move-task'"
adb shell "dumpsys display | grep -A3 fission | grep -iE 'FLAG_|owner'"      # cụm có FLAG_PRIVATE không
```
- Không có dòng `R1 did not land` ⇒ `[SUY]` trên **đúng**; fallback R2 chỉ là lưới an toàn.
- Vẫn `Permission Denial` **với id cụm đúng** ⇒ `[SUY]` **sai**; dán `dumpsys display` khối cụm (rất có thể cụm CÓ
  `FLAG_PRIVATE`, hoặc `getOwnerUid()` ≠ 1000 dù process là uid 1000) — đây là dữ liệu quý nhất của khối cast.

### 5.3 Fallback R2 — nhớ: **move-task**, KHÔNG phải move-stack

```bash
adb shell "logcat -d -s SimpleCast | grep -E 'am stack move-task|am display move-stack'"
```
- Thấy `am stack move-task <taskId> <stackId> true` ⇒ đúng đường (`AppMover.kt:112`).
- Thấy `am display move-stack` ⇒ **DỪNG NGAY, tắt cast, báo về**: lệnh bị CẤM (§3-E) và bài canh
  `AppMoverMoveStackFallbackTest` lẽ ra đã chặn ⇒ có bản build sai.

### 5.4 Geometry / DPI + bóng VietMap (gate theo state cast)

Sau khi cast **thật sự bám VD** (state → `CastingFull`, verifier đọc `am stack list`): mục **khung/DPI** phải hiện nút
chỉnh, bộ chỉnh **bóng VietMap** mở khoá. Cả hai gate theo `coordinator.state` / `prefs.castEnabled()` ⇒ hiện = bằng
chứng phụ rằng cast bám thật.
```bash
adb shell "logcat -d | grep -E 'VM_BUBBLE_POS|geometryTargets|slot-density'" | tail -20
```
Kéo bong bóng trong lúc đang chiếu, chụp trước/sau.

### 5.5 SpeedBadge — **nghi gắn nhầm màn, nay có bằng chứng mạnh hơn**

`[ĐO source]` `SpeedBadgeOverlay.kt:44,138-141` thử `dm.getDisplay(1)` **TRƯỚC**, chỉ lùi `CATEGORY_PRESENTATION` khi
id 1 **null**. `[ĐO source]` `DisplayParse.kt:184-188`: sau reboot **display 1 = `kachi-slot-0`** (VD của chính
launcher). ⇒ `[SUY mạnh]` badge tốc độ **gắn vào một ô app của Kachi**, không phải cụm.
```bash
adb shell "dumpsys display | grep -E 'Display 1:' -A6"        # display 1 là gì
adb shell "dumpsys window windows | grep -iE 'SpeedBadge|mDisplayId'" | head -20
```
- display 1 tồn tại và **không** phải cụm ⇒ **xác nhận bug**, mở việc sửa (bỏ hằng, dùng `ClusterDisplayResolver`).
- display 1 vắng ⇒ fallback PRESENTATION cứu ⇒ bug ngủ, vẫn nên sửa.

### 5.6 Mồ côi cửa sổ (X1 / ARCH-🚗) — giữ nguyên §2.12 của playbook 1.53

`scripts/vehicle/kachi/60-cast.sh <ip>:5555` (gọi `on-car-verify.sh` với `PKG=com.byd.launcher`), dò mồ côi ở 5 mốc:
baseline → chiếu app thường → chiếu CP/AA → **đổi app từ CP/AA** (điểm mồ côi cũ) → stress 60 s → sau khi tắt chiếu.
**Kỳ vọng `FAIL=0`.** Không lặp lại chi tiết ở đây.

---

## 6. Dọn bắt buộc trước khi rời xe

```bash
adb shell am force-stop com.waze; adb shell am force-stop com.android.settings
adb shell am force-stop com.chisadin.wazemod 2>/dev/null
# TẮT CHIẾU bằng UI (nút nổi / Cài đặt). Cụm kẹt ⇒ tắt máy xe rồi nổ lại
# + GHI LẠI ĐÚNG CHUỖI THAO TÁC dẫn tới kẹt (dữ liệu quý nhất của buổi)
adb shell "service call AutoContainer 2 i32 1000 i32 18 s16 \"\""   # chỉ khi UI không tắt được
adb shell "service call AutoContainer 2 i32 1000 i32 0  s16 \"\""
adb shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity
adb shell "dumpsys display | grep -c kachi-slot"                    # phải = số ô App, không dư
```
**KHÔNG** để lại: task freeform trên display 0, `am compat` override, ghi vào `/system`.

---

## 7. Đính chính doc cũ (cả hai làm sai kỳ vọng nếu đọc nguyên văn)

| Chỗ ghi | Câu cũ | Sự thật |
|---|---|---|
| `oncar-playbook-kachi-1.53.md` §2.12.X2 | *"rơi về `am display move-stack` (đường proven ClusterCast R2)"* + kỳ vọng log `am display move-stack <stack> 2` | `[ĐO source]` code đã đổi sang **`am stack move-task`** từ quality-review 2026-09-15 (`AppMover.kt:112`, KDoc `:94-106`); `move-stack` bị **CẤM** (§3-E) và nhãn "proven ClusterCast" là **vô căn cứ** vì `ClusterCast` là code chết không chạy tới. Log kỳ vọng đúng: `am stack move-task <taskId> <stackId> true` |
| `.kiro/steering/project-context.md` §5 (mục X2) | *"`AppMover.castToCluster` NORMAL nay rơi về `am display move-stack <stack> <vd>`"* | như trên — mô tả đã lạc hậu |
| `oncar-playbook-kachi-1.53.md` §2.13 (D-emu) | *"Waze/Maps báo does not support launch on secondary displays"* | `[ĐO source]` RE doc §5: chuỗi thật là `Failed to put TaskRecord{…} on display N` (`ASS.java:2436`); **Google Maps KHÔNG bị đẩy** — chỉ Waze, và vì activity trung chuyển |

---

## 8. `[CHƯA BIẾT]` — và lệnh nào đóng được

| Câu hỏi | Đóng bằng |
|---|---|
| DL3 có honour `am task resize` trên display 0 không (DL5 no-op im lặng) | §3-C bước 4-5 (so bounds **trước/sau**, không tin exit code) |
| ROM có hiện lại thanh trạng thái Android khi ô freeform có focus (U8a) | ảnh ở §3-C |
| `am stack move-task` vượt ranh giới display có an toàn trên DL3 không (họ lỗi `move-stack`) | §3-D, có tiêu chí DỪNG |
| ROM DL3 tạo được **mấy** display fission (DL5 = 2) | §3-G2 diff `dumpsys display` |
| `protectionLevel` của `ACTIVITY_EMBEDDING` trên ROM này | §3-H1 dòng 1 |
| `getSystemService("AutoContainer")` có gọi được từ **uid app** (AmapService là app BYD-signed) | `[CHƯA BIẾT]` — cần một bản thử có 3 dòng reflection; **ngoài buổi này** (cần build), ghi làm việc off-car |
| Cụm có `FLAG_PRIVATE` không / `getOwnerUid()` thật của VD cụm | §5.2 dòng 2 |
| Kachi có vào được `/system/priv-app` không | §3-B2 |
| Waze **bản mod** còn trampoline không | §3-H2 |

---

## 9. Nguồn

**Doc trong repo**: `waze-into-slot-research-2026-09-14.md` (§1.1 gate AOSP `file:line`, §1.2 cờ VD, §2.2 manifest,
§2.3 log Waze, §2.4 freeform, §4 đề xuất, §5 đính chính) · `carlog-kachi-20260914-2044/session-findings.md` (§X2 +
bảng SimpleCast/ClusterCast) · `carlog-kachi-20260914-2044/10-settings-flags.txt` · `…/00-display-ids.txt` ·
`oncar-session-2026-09-14-findings.md` · `oncar-trace-2026-09-16.md` §1 · `oncar-verify-1.63-2026-09-15.md` §1 ·
`oncar-playbook-kachi-1.53.md` §2.4/§2.12/§2.13/§2.15/§2.17 ·
**`docs/archive/diagnostics/carplay-move-task-success-2026-08-01.md`** (bằng chứng `[ĐO trên xe]` mạnh nhất của
option D: move-task chạy 2 chiều 0 crash · `force_resizable_activities` chỉ tác dụng lúc launch · đòn bẩy
`wm density`) · `docs/archive/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md` (trace crash 3/3) ·
`docs/archive/diagnostics/carplay-aa-cluster-placement-research-2026-08-01.md` (phân tích NPE, chứng minh AOSP).

**Mã Kachi**: `VdAppHost.kt:142-143,231` · `SlotAppHost.kt:112-131` · `WorkspaceView.kt:337-341` ·
`KachiHomeActivity.kt:173` · `LauncherWindows.kt:129,167,183,200` · `ClusterDisplayResolver.kt:14-21,33-38` ·
`DisplayParse.kt:160-218` · `AppMover.kt:41,94-118,133-144` · `ProjectionManager.kt:31-64` ·
`CarExecClusterProjectionCatalog.kt:52-95` · `CarExecClusterLifecycleCatalog.kt:68,85-93` ·
`SpeedBadgeOverlay.kt:44,132-141` · `AppMoverMoveStackFallbackTest.kt:64-104`.

**RE ngoài**: `jadx-amap/sources/com/example/amapservice/AmapService.java:127-129,138,196-204` ·
`jadx-amap/sources/android/os/AutoContainerManager.java:9-19` ·
`jadx-dashcast/sources/com/byd/dashcast/cluster/display/ClusterManager.java:21-27,62-63,168,280-352` ·
`jadx-dashcast/sources/com/byd/dashcast/fission/FissionClient.java:13-100` (⚠ đây là daemon **của DashCast**
`byd_mirror_daemon`, **không** phải service ROM — VD nó tạo vẫn không thuộc uid 1000 ⇒ không thoát gate) ·
`dashcast-src/CHANGELOG.md` v1.2.26 / v1.2.27 / v1.2.32 / v1.2.41 / v1.2.56 / v1.2.59 / v1.2.72.

**AOSP** (qua RE doc, không đọc lại): `android-10.0.0_r47` `ActivityStackSupervisor.java:349-367,1084-1136,2425-2441` ·
`ActivityRecord.java:1422-1425` · `RootActivityContainer.java:1790-1792` · `ActivityInfo.java:492` ·
`DisplayManagerService.java:1956-2006` · `DisplayContent.java:2401-2402`; `android-12.0.0_r34`
`ActivityTaskSupervisor.java:1108-1123` · `DisplayManagerService.java:2615-2620`.
