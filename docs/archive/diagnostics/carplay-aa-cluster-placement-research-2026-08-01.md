# CarPlay/AA lên cụm: mổ xẻ NPE `move-stack` tới tận source AOSP, và đường vòng khả thi

Ngày viết: 2026-08-01. Tiếp nối trực tiếp `docs/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md`
(đo thật trên DiLink3, Android 10 / SDK 29, CarPlay cắm dây sống, lặp 3/3).

Tài liệu này **không có phép đo mới trên xe** — toàn bộ là đọc source AOSP thật đã fetch về + đối chiếu
với dump cũ có sẵn trong repo. Theo CLAUDE.md §2/§3, mọi khẳng định đều gắn nhãn mức bằng chứng:

- **[CM]** — Đã chứng minh: đọc được nguyên văn trong source đã fetch, hoặc thấy trong dump thật.
- **[NK]** — Nhiều khả năng: suy luận khớp bằng chứng, chưa verify trực tiếp.
- **[ĐOÁN]** — Giả thuyết, chưa kiểm.

---

## TL;DR

1. **[CM] Crash là một race có thật trong AOSP 10, nằm ở đúng 2 dòng liên tiếp của
   `DisplayContent.moveStackToDisplay`.** Stack bị gỡ khỏi display cũ (→ `TaskStack.mDisplayContent = null`),
   rồi gắn vào display mới; **cơn sóng đổi cấu hình chạy TRONG lúc gắn, trước khi `mDisplayContent` được
   phục hồi**. Bất kỳ code nào đọc `task.getDisplayContent()` trong cửa sổ đó đều nhận `null`.

2. **[CM] Điều kiện kích hoạt là VƯỢT RANH GIỚI FREEFORM**, không phải animation.
   `AppWindowToken.shouldStartChangeTransition` chỉ trả `true` khi
   `(chế độ cũ == FREEFORM) != (chế độ mới == FREEFORM)`. Dump thật của xe cho thấy
   **display 0 = `fullscreen`, display 1 (cụm) = `freeform`** → mọi stack đi từ màn chính sang cụm đều
   vượt ranh giới → luôn kích hoạt change-transition → luôn chụp snapshot → luôn NPE. Điều này giải thích
   **vì sao 3/3 lần đều crash** chứ không phải "lúc được lúc không".

3. **[CM] Không có cách tắt đường snapshot đó từ shell trên Android 10.** Cả 3 công tắc đều là
   **tài nguyên build-time**, đọc một lần trong constructor, không có mặt ở `settings`/`device_config`.
   Việc đặt 3 animation scale = 0 (đã thử, vô hiệu) là **cơ chế hoàn toàn khác** — nó không đụng tới
   `mDisableTransitionAnimation`.

4. **[CM] Bug đã được sửa ở upstream, nhưng chỉ từ Android 11**, bằng một cuộc refactor cấu trúc
   (`WindowContainer.mReparenting` + gọi `onDisplayChanged` TRƯỚC `onParentChanged`). **Không có bản
   Android 10 nào chứa bản vá này** — đã kiểm 1 tag + 7 nhánh release. Không vá ROM thì không sửa được tại chỗ.

5. **[CM] CÓ đường vòng, và nó không cần đụng ROM: đừng chuyển cả STACK qua display, hãy chuyển
   TASK vào một stack đã nằm sẵn trên display 1.** Tức là `am stack move-task` thay cho
   `am display move-stack`. Đường này **về mặt cấu trúc không thể rơi vào cửa sổ null** — chứng minh ở §4.1.
   Nó cũng **không đi qua `ActivityStarter`**, nên không dính rào `not exported`.
   → **Đây là mũi chính cần thử ở phiên xe tới.** Chưa chạy thật lần nào ⇒ mức **[NK]**, không phải [CM].

6. **[CM] Sản phẩm tiền nhiệm KHÔNG có lời giải để kế thừa.** Quét toàn bộ `../dashcast-src/CHANGELOG.md`
   (459 KB) và `../jadx-dashcast/` (45 MB): **0 lần** nhắc CarPlay, **0 lần** dùng `move-stack`. DashCast
   đặt app lên cụm bằng `setLaunchDisplayId` + `startActivity` — đường đó **về nguyên tắc không thể** đặt
   được một activity không-exported. Xem §3.1. *(Và cả DashCast lẫn OpenBYD đều chứa một lời gọi
   `moveTaskToDisplay` qua reflection **luôn ném `NoSuchMethodException`** — API đó không tồn tại ở bất kỳ
   đời AOSP nào. Đừng đọc nó như bằng chứng.)*

7. **[CM] Một kết luận cũ trong repo cần đính chính.** `docs/_handoff/research-aosp-wm.md:168` viết
   *"`am stack move-task` → same reparent/change semantics → same risk"*. **Không đúng** — và chính chỗ này
   là khác biệt giữa "CarPlay bất khả thi" và "CarPlay có đường". Xem §3.4.

---

## 0. Nguồn và phương pháp

Toàn bộ source được `curl` về dạng raw (không qua tóm tắt), rồi đọc trực tiếp bằng số dòng thật:

```
https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-10.0.0_r47/...
https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r48/...
https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-12.0.0_r34/...
```

File đã fetch (android-10.0.0_r47, `services/core/java/com/android/server/`):
`wm/TaskSnapshotController.java` (495 dòng), `wm/AppWindowToken.java` (3259),
`wm/DisplayContent.java` (5315), `wm/TaskStack.java` (1967), `wm/ActivityStack.java` (5771),
`wm/RootActivityContainer.java` (2452), `wm/ActivityTaskManagerService.java` (7450),
`wm/WindowContainer.java` (1404), `wm/ConfigurationContainer.java` (619), `wm/Task.java` (809),
`wm/TaskRecord.java`, `wm/ActivityDisplay.java`, `wm/ActivityStackSupervisor.java`,
`wm/WindowManagerService.java` (7789), `am/ActivityManagerShellCommand.java` (3177),
cộng `packages/Shell/AndroidManifest.xml`.

**Cảnh báo quan trọng — [CM]: số dòng trong stack trace của xe KHÔNG khớp AOSP r47 thuần.**
Framework trên xe **đã bị vendor sửa**. Nhưng độ lệch trong từng file lại **rất nhất quán**, đủ để ánh xạ
từng frame về đúng call site ngữ nghĩa (xem §1.1). Đây là điều kiện tiên quyết để mọi phân tích bên dưới
có giá trị — nếu ánh xạ sai thì kết luận sai.

---

## 1. Cơ chế crash — chứng minh từ source

### 1.1 Bảng ánh xạ frame → AOSP r47 thuần

Mỗi dòng: frame trong crash thật ↔ dòng tương ứng trong r47 thuần ↔ độ lệch.

| # | Frame (ROM xe) | Dòng ROM | Câu lệnh tương ứng trong r47 thuần | Dòng r47 | Lệch |
|---|---|---|---|---|---|
| 18 | `ActivityManagerShellCommand.runDisplayMoveStack` | 2519 | `mTaskInterface.moveStackToDisplay(stackId, displayId);` | 2521 | −2 |
| 17 | `ActivityTaskManagerService.moveStackToDisplay` | 3622 | `mRootActivityContainer.moveStackToDisplay(stackId, displayId, ON_TOP);` | 3403 | +219 |
| 16 | `RootActivityContainer.moveStackToDisplay` | 970 | `stack.reparent(activityDisplay, onTop, false /* displayRemoved */);` | 967 | +3 |
| 15 | `ActivityStack.reparent` | 922 | `mTaskStack.reparent(activityDisplay.mDisplayId, mTmpRect2, onTop);` | 891 | +31 |
| 14 | `TaskStack.reparent` | 630 | `targetDc.moveStackToDisplay(this, onTop);` | 628 | **+2** |
| 13 | `DisplayContent.moveStackToDisplay` | 2489 | `mTaskStackContainers.addStackToDisplay(stack, onTop);` | 2402 | +87 |
| 12 | `DisplayContent$TaskStackContainers.addStackToDisplay` | 4337 | `addChild(stack, onTop);` | 4187 | **+150** |
| 11 | `DisplayContent$TaskStackContainers.addChild` | 4395 | `addChild(stack, addIndex);` | 4245 | **+150** |
| 10 | `WindowContainer.addChild` | 273 | `child.setParent(this);` | 269 | **+4** |
| 9 | `WindowContainer.setParent` | 174 | `onParentChanged();` | 170 | **+4** |
| 8 | `TaskStack.onParentChanged` | 1013 | `super.onParentChanged();` | 1006 | +7 |
| 7 | `WindowContainer.onParentChanged` | 183 | `super.onParentChanged();` | 179 | **+4** |
| 6 | `ConfigurationContainer.onParentChanged` | 554 | `onConfigurationChanged(parent.mFullConfiguration);` | 554 | **0** |
| 5 | `TaskStack.onConfigurationChanged` | 735 | `super.onConfigurationChanged(newParentConfig);` | 733 | **+2** |
| 4 | `WindowContainer.onConfigurationChanged` | 167 | `super.onConfigurationChanged(newParentConfig);` | 163 | **+4** |
| 3 | `ConfigurationContainer.onConfigurationChanged` | 144 | `child.onConfigurationChanged(mFullConfiguration);` | 144 | **0** |
| 2 | `AppWindowToken.onConfigurationChanged` | 1756 | `initializeChangeTransition(mTmpPrevBounds);` | 1707 | **+49** |
| 1 | `AppWindowToken.initializeChangeTransition` | 1816 | `mWmService.mTaskSnapshotController.createTaskSnapshot(` | 1767 | **+49** |
| 0 | `TaskSnapshotController.createTaskSnapshot` | 298 | *(không ánh xạ được — xem §1.6)* | — | — |

**[CM] Vì sao ánh xạ này đáng tin, không phải trùng hợp:** độ lệch **nhất quán trong từng file**, ở
nhiều frame độc lập:

- `WindowContainer.java`: **+4** ở **bốn** frame khác nhau (#4, #7, #9, #10).
- `DisplayContent.java`: **+150** ở **hai** frame khác nhau (#11, #12).
- `AppWindowToken.java`: **+49** ở **hai** frame khác nhau (#1, #2).
- `ConfigurationContainer.java`: **0** ở **hai** frame khác nhau (#3, #6) — file này vendor không đụng.
- `TaskStack.java`: **+2** ở #5 và #14, **+7** ở #8 — độ lệch tăng đơn điệu theo vị trí trong file, đúng
  như khi vendor chèn thêm code rải rác.

Xác suất để 4 frame độc lập trong cùng một file đều rơi đúng vào call site có ý nghĩa với cùng một độ lệch
một cách ngẫu nhiên là không đáng kể. Kết luận: ánh xạ đúng, chuỗi gọi dưới đây là chuỗi thật.

### 1.2 Chuỗi gọi thật (r47 thuần, đọc từ dưới lên)

```
am display move-stack <stackId> 1
 └─ ActivityManagerShellCommand.runDisplayMoveStack           (:2516-2523)
 └─ ActivityTaskManagerService.moveStackToDisplay             (:3395)  ⟵ enforce INTERNAL_SYSTEM_WINDOW
 └─ RootActivityContainer.moveStackToDisplay                  (:937)
 └─ ActivityStack.reparent                                    (:880)
 └─ TaskStack.reparent                                        (:621)
 └─ DisplayContent.moveStackToDisplay                         (:2390)  ◀── Ổ BỆNH
      prevDc.mTaskStackContainers.removeChild(stack);         (:2401)  ① mDisplayContent := null
      mTaskStackContainers.addStackToDisplay(stack, onTop);   (:2402)  ②
 └─ TaskStackContainers.addStackToDisplay                     (:4185)
      addStackReferenceIfNeeded(stack);                       (:4186)
      addChild(stack, onTop);                                 (:4187)  ③ SÓNG CONFIG NỔ Ở ĐÂY
      stack.onDisplayChanged(DisplayContent.this);            (:4188)  ④ mDisplayContent mới CHỈ được đặt Ở ĐÂY
```

### 1.3 Cửa sổ null — chính xác nó mở ra ở đâu **[CM]**

**Bước ① — gỡ khỏi display cũ ⇒ null hoá.**

`WindowContainer.removeChild` (`WindowContainer.java:288-294`):

```java
void removeChild(E child) {
    if (mChildren.remove(child)) {
        onChildRemoved(child);
        child.setParent(null);          // ← 291
    } else { ... }
}
```

`setParent(null)` → `TaskStack.onParentChanged()` (`TaskStack.java:1005-1022`):

```java
void onParentChanged() {
    super.onParentChanged();
    if (getParent() != null || mDisplayContent == null) {
        return;
    }
    EventLog.writeEvent(EventLogTags.WM_STACK_REMOVED, mStackId);
    ...
    mDisplayContent = null;             // ← 1021  ⚑ TỪ ĐÂY TRỞ ĐI STACK KHÔNG CÓ DISPLAY
    mWmService.mWindowPlacerLocked.requestTraversal();
}
```

Lúc gỡ này **không có** sóng config, vì `ConfigurationContainer.onParentChanged`
(`ConfigurationContainer.java:548-557`) chặn khi cha là null:

```java
void onParentChanged() {
    final ConfigurationContainer parent = getParent();
    // Removing parent usually means that we've detached this entity to destroy it or to attach
    // to another parent. In both cases we don't need to update the configuration now.
    if (parent != null) {                                    // ← 552
        onConfigurationChanged(parent.mFullConfiguration);    // ← 554
        onMergedOverrideConfigurationChanged();
    }
}
```

**Bước ③ — gắn vào display mới ⇒ sóng config nổ trong khi `mDisplayContent` vẫn null.**

`addChild` → `WindowContainer.setParent` (`WindowContainer.java:168-171`):

```java
final protected void setParent(WindowContainer<WindowContainer> parent) {
    mParent = parent;
    onParentChanged();                  // ← 170
}
```

Lần này `getParent() != null` ⇒ `ConfigurationContainer.onParentChanged:554` chạy
⇒ `TaskStack.onConfigurationChanged` (`TaskStack.java:731-755`):

```java
public void onConfigurationChanged(Configuration newParentConfig) {
    final int prevWindowingMode = getWindowingMode();
    super.onConfigurationChanged(newParentConfig);   // ← 733  ⚑ SÓNG LAN XUỐNG CON Ở ĐÂY
    updateSurfaceSize(getPendingTransaction());
    final int windowingMode = getWindowingMode();
    final boolean isAlwaysOnTop = isAlwaysOnTop();

    if (mDisplayContent == null) {                   // ← 741  ⚑ AOSP BIẾT VỀ CỬA SỔ NÀY
        return;
    }
    ...
}
```

**Đây là bằng chứng đanh nhất.** Dòng 741 là một guard `mDisplayContent == null` **do chính AOSP viết** —
tức là tác giả framework **biết** rằng `onConfigurationChanged` có thể chạy khi stack chưa có display.
Nhưng guard đó đặt **SAU** `super.onConfigurationChanged()` ở dòng 733 — mà chính dòng 733 mới là dòng lan
sóng xuống toàn bộ cây con. Guard bảo vệ được code của riêng `TaskStack`, **không bảo vệ được con cháu nó**.

**Bước ④ — phục hồi, nhưng đã muộn.** `stack.onDisplayChanged(...)` ở `DisplayContent.java:4188` mới là chỗ
gán lại `mDisplayContent` (`WindowContainer.onDisplayChanged`, `:515-524`: `mDisplayContent = dc;`), và nó
chạy **sau** `addChild` ở dòng 4187. Cửa sổ null kéo dài trọn vẹn bước ③.

### 1.4 Vì sao `AppWindowToken` sống sót mà `Task` thì chết — bất đối xứng `getDisplayContent()` **[CM]**

Đây là mảnh giải thích vì sao NPE rơi **đúng** vào `createTaskSnapshot` chứ không phải sớm hơn.

| Lớp | `getDisplayContent()` trả về gì | Trong cửa sổ null |
|---|---|---|
| `WindowContainer` (`:526-528`) | `return mDisplayContent;` (trường của chính nó) | — |
| `TaskStack` | *không override* → dùng trường riêng | **`null`** (đã bị xoá ở bước ①) |
| `Task` (`Task.java:143-145`) | `return mStack != null ? mStack.getDisplayContent() : null;` | **`null`** (uỷ quyền cho TaskStack) |
| `AppWindowToken` | *không override* → dùng trường riêng | **KHÔNG null** — còn trỏ display **cũ** |

Vì sao `AppWindowToken.mDisplayContent` còn sống? Vì thứ duy nhất cập nhật trường đó cho cây con là
`WindowContainer.onDisplayChanged` (`:515-524`, có vòng lặp đệ quy xuống `mChildren`) — mà hàm này
**chưa hề được gọi** (nó ở bước ④). Bước ① chỉ xoá trường của **riêng** `TaskStack`.

Hệ quả, đọc theo đúng thứ tự thực thi:

```java
// AppWindowToken.java:1711-1721
private boolean shouldStartChangeTransition(int prevWinMode, int newWinMode) {
    if (mWmService.mDisableTransitionAnimation
            || !isVisible()
            || getDisplayContent().mAppTransition.isTransitionSet()   // ← 1714 KHÔNG NPE (trỏ display cũ)
            || getSurfaceControl() == null) {
        return false;
    }
    return (prevWinMode == WINDOWING_MODE_FREEFORM) != (newWinMode == WINDOWING_MODE_FREEFORM);
}

// AppWindowToken.java:1736-1774
private void initializeChangeTransition(Rect startBounds) {
    mDisplayContent.prepareAppTransition(...);                        // ← 1737 KHÔNG NPE (trỏ display cũ)
    ...
    Task task = getTask();
    if (mThumbnail == null && task != null && !hasCommittedReparentToAnimationLeash()) {
        SurfaceControl.ScreenshotGraphicBuffer snapshot =
                mWmService.mTaskSnapshotController.createTaskSnapshot(
                        task, 1 /* scaleFraction */);                 // ← 1767 ĐƯA `task` VÀO ⇒ NỔ BÊN TRONG
        ...
    }
}
```

Tóm lại: **thủ phạm là việc `task` bị truyền qua ranh giới**. `AppWindowToken` có bản sao cũ nên đi lọt
hai guard đầu; nhưng nó đưa `task` — thứ uỷ quyền cho `TaskStack` đã bị null hoá — vào
`TaskSnapshotController`. Đúng một object null, đúng một chỗ. Không có kịch bản nào khác khớp được
stack trace này.

### 1.5 Điều kiện kích hoạt: vượt ranh giới FREEFORM **[CM]**

Dòng cuối `shouldStartChangeTransition` (`AppWindowToken.java:1720`) là công tắc thật:

```java
// Only do an animation into and out-of freeform mode for now. Other mode
// transition animations are currently handled by system-ui.
return (prevWinMode == WINDOWING_MODE_FREEFORM) != (newWinMode == WINDOWING_MODE_FREEFORM);
```

Nghĩa là: **nếu chế độ cửa sổ không băng qua ranh giới freeform thì `initializeChangeTransition`
KHÔNG BAO GIỜ được gọi** → không chụp snapshot → không NPE. Cửa sổ null vẫn mở, nhưng không ai bước vào.

Nơi gọi (`AppWindowToken.java:1706-1707`):

```java
} else if (shouldStartChangeTransition(prevWinMode, winMode)) {
    initializeChangeTransition(mTmpPrevBounds);
}
```

### 1.6 Dump thật xác nhận: display 1 là display **freeform** **[CM]**

Đây là mảnh ghép giải thích **vì sao 3/3 lần đều crash**, chứ không phải ngẫu nhiên.

`docs/diagnostics/carlog-2026-07-23-trace/external/diag/diag-0722-074736.txt:436-438` — `am stack list` thật:

```
Stack id=39 bounds=[0,0][1920,720] displayId=1 userId=0
 configuration={... winConfig={ mBounds=Rect(0, 0 - 1920, 720) mAppBounds=Rect(0, 0 - 1920, 720)
   mWindowingMode=freeform mDisplayWindowingMode=freeform mActivityType=standard ...}}
  taskId=6: vn.vietmap.live/vn.vietmap.live.MainActivity ... visible=true
```

Đối chiếu display 0 (`docs/diagnostics/carlog-2026-07-21/01-stacklist.txt:1-2`):

```
Stack id=28 bounds=[0,0][1920,1080] displayId=0 userId=0
 configuration={... mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen ...}
```

Thống kê toàn bộ dump trong `docs/diagnostics/`: `mDisplayWindowingMode=fullscreen` 683 lần,
`=freeform` 18 lần — và **mọi** lần `freeform` đều thuộc về display 1.

⇒ **[CM] Chuỗi nhân quả hoàn chỉnh:**

```
CarPlay ở display 0, stack = fullscreen
  → am display move-stack <id> 1
  → cha mới = TaskStackContainers của display 1, config có mDisplayWindowingMode=freeform
  → prevWinMode = fullscreen(1), winMode = freeform(5)
  → (prev==FREEFORM) != (new==FREEFORM)  ⇒  true
  → shouldStartChangeTransition = true
  → initializeChangeTransition → createTaskSnapshot(task)
  → task.getDisplayContent() == null  (cửa sổ §1.3)
  → .getRotation()  ⇒  NullPointerException
```

Và điều này cũng giải thích **vì sao app thường không bao giờ crash**: chúng được đặt bằng
`am start --display 1 --windowingMode 5`, tức là **dựng stack thẳng trên display 1** — không hề có
reparent xuyên display, không hề chạm `moveStackToDisplay`. Chỉ những app bị chặn `am start` (CarPlay) mới
bị đẩy vào con đường chết.

### 1.7 Vì sao tắt animation scale không cứu được **[CM]**

Guard đầu tiên là `mWmService.mDisableTransitionAnimation`. Nguồn của nó
(`WindowManagerService.java:612` khai báo, `:1021-1022` gán, trong constructor WMS):

```java
mDisableTransitionAnimation = context.getResources().getBoolean(
        com.android.internal.R.bool.config_disableTransitionAnimation);
```

Đây là **tài nguyên build-time**, đọc **một lần** lúc dựng WMS. Nó **không liên quan gì** tới
`Settings.Global.window_animation_scale` / `transition_animation_scale` / `animator_duration_scale`
(3 cái này đi vào `mWindowAnimationScaleSetting` v.v., là hệ số thời lượng animation, không phải cờ
bật/tắt đường change-transition).

⇒ Kết quả đo đêm qua ("tắt cả 3 scale = 0, crash y hệt") **hoàn toàn khớp với source**. Không phải
đo sai — đó là kết quả đúng. Và nó cũng có nghĩa: **guard này không có mặt shell nào để bấm**.
Muốn bật chỉ có RRO overlay hoặc vá ROM.

### 1.8 Về `createTaskSnapshot:298` và `getRotation()` — phần vendor đã sửa

**[CM] Sự thật kiểm được:** trong AOSP 10 thuần, `TaskSnapshotController.createTaskSnapshot(Task, float)`
nằm ở dòng **249-268**, dài 20 dòng, và **không có bất kỳ lời gọi `getRotation()` nào** trong toàn file
(grep cả file: chỉ có một chữ `DisplayContent` duy nhất, ở `:136` `onTransitionStarting`).

Đã kiểm để loại trừ khả năng "bản Android 10 khác":

| Nhánh/tag đã fetch | Số dòng | Có `getRotation` trong file? |
|---|---|---|
| `android-10.0.0_r1` | 495 | không |
| `android-10.0.0_r47` | 495 | không |
| `android10-release` | 495 | không |
| `android10-qpr1-release` | 495 | không |
| `android10-qpr2-release` | 495 | không |
| `android10-qpr3-release` | 495 | không |
| `android10-d4-release` | 495 | không |
| `android10-gsi` | 495 | không |
| `android10-mainline-release` | 495 | không |
| `android-11.0.0_r48` | 625 | **có** — `:334 builder.setRotation(activity.getTask().getDisplayContent().getRotation());` |
| `android-12.0.0_r34` | 693 | **có** — `:309` (y hệt) |

`diff android-10.0.0_r1 android-10.0.0_r47` ⇒ **giống hệt từng byte**.

**[NK] Diễn giải:** ROM của xe đã **backport (hoặc tự viết) phần đọc rotation từ nhánh R vào
`createTaskSnapshot` của Android 10**. Đây là lý do duy nhất còn lại giải thích được một lời gọi
`DisplayContent.getRotation()` bên trong `createTaskSnapshot` trên một máy SDK 29, và nó khớp với
độ lệch +30..+50 dòng quan sát được ở các file khác.

**[NK, tin cậy cao] Object null là `task.getDisplayContent()`.** Lý do: trong toàn bộ cây container tại
thời điểm đó, **thứ duy nhất được chứng minh là null** (§1.3) chính là `TaskStack.mDisplayContent`, và
`Task.getDisplayContent()` uỷ quyền thẳng cho nó (`Task.java:143-145`). `task` lại đúng là tham số chính
của `createTaskSnapshot`. Các ứng viên khác (`appWindowToken.getDisplayContent()`,
`mainWindow.getDisplayContent()`) đều trả trường riêng, không bị xoá.
*Không thể nâng lên [CM] vì không đọc được source ROM của xe.* Muốn chốt: `adb pull` + baksmali
`services.jar` trên xe rồi đọc `TaskSnapshotController` — một lệnh, không cần CarPlay đang chạy.

**Điểm cần nhớ:** dù mảnh này ở mức [NK], **nó không ảnh hưởng tới kết luận hay đường vòng**. Cửa sổ null
(§1.3) và điều kiện kích hoạt freeform (§1.5) đều là [CM] trên AOSP thuần, và cả hai đều **độc lập** với
việc vendor đã sửa gì trong `TaskSnapshotController`. Đường vòng ở §4.1 né **cửa sổ null**, chứ không né
riêng `createTaskSnapshot`.

---

## 2. Upstream đã sửa chưa?

**[CM] Rồi — nhưng chỉ từ Android 11, và bằng refactor cấu trúc, không phải một bản vá nhỏ.**

`android-11.0.0_r48`, `WindowContainer.java:330-356` — hàm `reparent()` mới:

```java
final DisplayContent prevDc = oldParent.getDisplayContent();
final DisplayContent dc = newParent.getDisplayContent();

mReparenting = true;                       // ← 340
oldParent.removeChild(this);
newParent.addChild(this, position);
mReparenting = false;                      // ← 343

// Relayout display(s)
dc.setLayoutNeeded();
if (prevDc != dc) {
    onDisplayChanged(dc);                  // ← 348  TRƯỚC
    prevDc.setLayoutNeeded();
}
getDisplayContent().layoutAndAssignWindowLayersIfNeeded();

// Send onParentChanged notification here is we disabled sending it in setParent for
// reparenting case.
onParentChanged(newParent, oldParent);     // ← 355  SAU
```

và `WindowContainer.setParent` (`:358-372`):

```java
final protected void setParent(WindowContainer<WindowContainer> parent) {
    final WindowContainer oldParent = mParent;
    mParent = parent;
    if (mParent != null) {
        mParent.onChildAdded(this);
    }
    if (!mReparenting) {                                          // ← 365
        if (mParent != null && mParent.mDisplayContent != null
                && mDisplayContent != mParent.mDisplayContent) {
            onDisplayChanged(mParent.mDisplayContent);            // ← 368  TRƯỚC
        }
        onParentChanged(mParent, oldParent);                      // ← 371  SAU
    }
}
```

Ba thay đổi độc lập, mỗi cái đều đủ để giết bug:

1. **Cờ `mReparenting`** (`:145`, `:340/343`, dùng ở `:485/515/559`): trong lúc reparent, `removeChild`
   **không gọi** `setParent(null)` nữa (`:559 if (!child.mReparenting)`). Container **không bao giờ**
   rơi vào trạng thái tháo rời ⇒ **`mDisplayContent` không bao giờ bị null hoá**.
2. **Đảo thứ tự**: `onDisplayChanged(dc)` chạy **trước** `onParentChanged(...)`. Khi sóng config nổ,
   display mới đã nằm sẵn.
3. **Accessor được gia cố**: `Task.getDisplayContent()` (A11 `Task.java:2601-2606`) không còn trả null cứng
   mà rơi về `super.getDisplayContent()`:
   ```java
   DisplayContent getDisplayContent() {
       // TODO: Why aren't we just using our own display content vs. parent's???
       final ActivityStack stack = getStack();
       return stack != null && stack != this
               ? stack.getDisplayContent() : super.getDisplayContent();
   }
   ```
   (so với A10 `Task.java:143-145`: `return mStack != null ? mStack.getDisplayContent() : null;`)

Ngoài ra A11 đã dời hẳn change-transition từ `AppWindowToken` (per-activity) lên `Task`
(`A11 Task.java:1937-1938, 1962, 1970`), và `AppWindowToken` bị xoá (gộp vào `ActivityRecord`).

**Về commit cụ thể — [chưa chốt được].** Tôi đã tìm nhưng **không xác định được change-id/hash cụ thể**
qua công cụ tìm kiếm hiện có. Tôi **không bịa** một hash ra. Bằng chứng tôi có là **chênh lệch source giữa
hai release tag đã fetch nguyên văn** (r47 vs 11.0.0_r48), đủ để kết luận bug tồn tại ở A10 và biến mất ở
A11. Ai cần truy commit: `git log android-10.0.0_r47..android-11.0.0_r48 -- services/core/java/com/android/server/wm/WindowContainer.java`
trên mirror AOSP, tìm commit thêm trường `mReparenting`.

**⇒ Kết luận [CM]: đây là bug upstream của Android 10, không phải lỗi ClusterNav, và không có bản vá nào
cho Android 10. DiLink3 sẽ mang bug này vĩnh viễn trừ khi vá ROM.** Mọi lời giải phải là **né**, không thể
là **sửa**.

---

## 3. Đối chiếu kho RE trong workspace

### 3.1 DashCast — sản phẩm tiền nhiệm **chưa bao giờ giải bài này**

**[CM] Kết quả âm tính quan trọng nhất của cả tài liệu:**

| Từ khoá | Số lần trong `../dashcast-src/CHANGELOG.md` (459 KB) | Trong `../jadx-dashcast/` (45 MB) |
|---|---|---|
| `carplay` / `CarPlay` | **0** | **0** |
| `move-stack` | **0** | **0** |
| `moveStackToDisplay` | **0** | 1 (chỉ là chuỗi thông báo lỗi cho user) |
| `moveTaskToStack` / `positionTaskInStack` | **0** | **0** |
| `NullPointerException` / `TaskSnapshot` | **0** | — |

⇒ **DashCast chưa từng cast CarPlay, và chưa từng dùng `am display move-stack`.** Không có kinh nghiệm
hiện trường nào để kế thừa cho bài toán này. Đây là câu trả lời dứt điểm cho câu hỏi "sản phẩm trước
giải thế nào": **nó không giải.**

**[CM] Cơ chế đặt app lên cụm của DashCast là LAUNCH, không phải reparent.**
`../jadx-dashcast/sources/com/byd/dashcast/cluster/ClusterService.java`:

```java
:494    makeBasic.setLaunchDisplayId(i);
:501    ClusterService.this.startActivityViaIAM(launchIntentForPackage, makeBasic);
...
:655    String str2 = "am force-stop " + str + " 2>&1; am start --display " + i
            + " --windowingMode 5 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "
            + (component.getPackageName() + "/" + component.getClassName())
            + " --activity-clear-task 2>&1";
```

Tức là: `ActivityOptions.setLaunchDisplayId()` + `startActivity` (DL3), fallback shell `am start --display N`
(DL5). **Cả hai đều đi qua `ActivityStarter`** ⇒ đều sẽ đâm vào rào `not exported` (§4.7) với CarPlay.
Thêm nữa nó dùng `getLaunchIntentForPackage` + `category.LAUNCHER` — mà `com.byd.carplay.ui.VideoActivity`
không phải activity launcher. **Cách của DashCast về nguyên tắc không thể đặt CarPlay lên cụm.**

**[CM] ⚠️ Bẫy: `moveTaskToDisplay` trong DashCast là CODE CHẾT.**
`../jadx-dashcast/sources/com/byd/dashcast/app/BootDisplayCleanup.java:39-60`:

```java
Object invoke = Class.forName("android.app.ActivityTaskManager")
        .getMethod("getService", new Class[0]).invoke(null, new Object[0]);
...
invoke.getClass().getMethod("moveTaskToDisplay", Integer.TYPE, Integer.TYPE)
        .invoke(invoke, Integer.valueOf(i), 0);            // ← :50
...
} catch (Exception e) {
    AppLogger.w(TAG, "Could not move " + str + " to Display 0: " + e.getMessage());
    return false;
}
```

Đã kiểm `IActivityTaskManager.aidl` nguyên văn ở cả ba đời:

| Phương thức | A10 (r47) | A11 (r48) | A12 (r34) |
|---|---|---|---|
| `moveTaskToDisplay(int,int)` | **không có** | **không có** | **không có** |
| `moveRootTaskToDisplay(int,int)` | không có | không có | **có** (`:207`) |
| `moveStackToDisplay(int,int)` | có (`:223`) | có (`:242`) | *(đổi tên thành `moveRootTaskToDisplay`)* |
| `moveTaskToStack(int,int,boolean)` | **có (`:234`)** | **có (`:254`)** | *(đổi tên → `moveTaskToRootTask`, `:209`)* |
| `positionTaskInStack(int,int,int)` | **có (`:314`)** | — | — |

⇒ **[CM] `moveTaskToDisplay` không tồn tại trong bất kỳ đời AOSP nào.** Lời gọi ở
`BootDisplayCleanup.java:50` **luôn** ném `NoSuchMethodException`, và bị `catch (Exception)` nuốt gọn
thành một dòng warning. Đây đúng là cái bẫy CLAUDE.md §8 cảnh báo: *code viết cẩn thận, compile sạch,
chưa bao giờ chạy*. **Không được đọc dòng changelog nhắc `moveTaskToDisplay` như bằng chứng rằng API đó có thật.**

**[CM] DashCast có ghi nhận hữu ích về việc ROM cắt verb** (`CHANGELOG.md`, mục v1.2.72-beta): trên **DL5**
`set-task-windowing-mode` **bị gỡ**, `task resize` **no-op im lặng** (exit 0, không tác dụng),
`set-display-windowing-mode` **bị gỡ**, `--activity-launch-bounds` là **unknown option**.
⇒ Củng cố T1 trong kế hoạch test: **phải kiểm verb có tồn tại không, không được giả định.**

### 3.2 OpenBYD — cùng một lời gọi chết

`../jadx-openbyd/sources/com/sr/openbyd/proxy/CarControlImpl.java:786-830` dò hai tên qua reflection,
`moveRootTaskToDisplay` trước rồi `moveTaskToDisplay`, và trả `"ERROR: moveTaskToDisplay method not found"`
nếu không thấy. Trên **DL3 (A10) cả hai đều không tồn tại** ⇒ luôn rơi vào nhánh ERROR.
(Trên DL5/A12 thì `moveRootTaskToDisplay` có thật — nhưng đó chính là `moveStackToDisplay` đổi tên, tức là
**đúng cái đường đang crash**, chỉ khác đời máy.)
`jadx-openbyd24` giống hệt. Không có code đặt CarPlay lên cụm ở cả hai.

### 3.3 Kho của chính repo — `move-task` **CHƯA TỪNG ĐƯỢC THỬ**

**[CM] `CarExecCatalog.kt` không có candidate `move-task`/`positiontask` nào.** Grep toàn repo
(`*.kt`, `*.java`, `*.md`) cho `move-task` / `positiontask` / `moveTaskToStack`: chỉ có **một** hit tiền
tồn tại, ở `docs/_handoff/research-aosp-wm.md:168` (xem §3.4). Không có candidate, không có field note,
không có log. **Đường ở §4.1 là đường mới hoàn toàn — chưa từng chạy, cũng chưa từng bị loại bằng phép đo.**

Hai candidate `move-stack` đang có trong catalogue:

```kotlin
// CarExecCatalog.kt:186-194
StepCandidate(
    id = "place.movestack",
    purpose = "Đường leo thang khi hai cách trên không bám",
    commands = listOf("am display move-stack {taskId} {display}"),
    evidence = "task chuyển sang display cụm mà không tạo orphan",
    verdictSource = VerdictSource.MEASURED,
    risk = CandidateRisk.READ_ONLY,
    fieldNote = "Chưa cần dùng lần nào trong bốn ca đã chứng minh; giữ làm escalation",
)

// CarExecCatalog.kt:686-694
StepCandidate(
    id = "return.movestack-main",
    commands = listOf("am display move-stack {taskId} 0"),
    evidence = "task về display 0 mà không tạo orphan (đây là lệnh từng gây NPE ở V1)",
    verdictSource = VerdictSource.MEASURED,
    risk = CandidateRisk.READ_ONLY,
    fieldNote = "V1: move-stack một task freeform đang hiện từng gây half-reparent",
)
```

Ba vấn đề, cả ba đều [CM]:

1. **🎯 Field note V1 xác nhận độc lập phân tích ở §1.5.** *"move-stack một task **freeform** đang hiện
   từng gây half-reparent"* — hai điều kiện trong ghi chú này (**freeform** + **đang hiện**) chính là hai
   guard trong `AppWindowToken.shouldStartChangeTransition`: vượt ranh giới freeform (`:1720`) và
   `isVisible()` (`:1713`). Ghi chú này được viết **trước** khi có phân tích source này, từ một ca hiện
   trường khác. Hai nguồn độc lập trỏ cùng một chỗ ⇒ độ tin cậy của §1.5 tăng đáng kể.
2. **`risk = CandidateRisk.READ_ONLY` là sai nghiêm trọng.** Lệnh này làm **sập system_server** và làm
   **mất task khỏi hệ thống**, cần rút/cắm cáp mới hồi. Nó là mức phá huỷ cao nhất trong catalogue, không
   phải READ_ONLY. Cả hai entry đều gắn sai. Đây là rủi ro thật: ai đó đọc catalogue sẽ tưởng chạy thử
   vô hại.
3. **Truyền `{taskId}` vào chỗ cần `<STACK_ID>`.** Help của AOSP (`ActivityManagerShellCommand.java:3127`)
   ghi `move-stack <STACK_ID> <DISPLAY_ID>`, và `runDisplayMoveStack` (`:2517-2518`) parse tham số 1 thành
   `stackId`. Truyền taskId vào ⇒ hoặc trúng nhầm một stack không liên quan, hoặc ném
   `IllegalArgumentException: moveStackToDisplay: Unknown stackId=` (`RootActivityContainer.java:945`).
   *(Đường sản phẩm thật `CastPlacementCommands.kt` dùng `sourceStack(...)` nên đúng — lỗi này chỉ ở
   catalogue probe.)*

### 3.4 ⚠️ Đính chính một kết luận cũ trong repo

`docs/_handoff/research-aosp-wm.md:168` (phân tích trước, cùng bug, gọi là "NPE B") viết:

> `am stack move-task` / windowing-mode changes → same reparent/change semantics → same risk.

**[CM] Kết luận này không đúng, và đây là điểm mấu chốt của cả tài liệu.** Tài liệu cũ đúng ở phần lớn
nội dung (ba guard, việc animation scale vô dụng, hướng "occlude-first") nhưng đã **suy rộng** từ
`move-stack` sang `move-task` mà không lần tới hai dòng quyết định:

- `TaskStack.addTask` (`TaskStack.java:547-548`): `task.mStack = this;` chạy **TRƯỚC** `addChild(task, null);`
- stack đích của `move-task` **đã gắn display từ trước** ⇒ `mDisplayContent` của nó **chưa bao giờ null**

Hai đường có "reparent semantics" giống nhau ở mức mô tả, nhưng **khác nhau ở đúng chỗ quyết định**:
`move-stack` tháo rời chính cái container mang tham chiếu display (`TaskStack`), còn `move-task` chỉ di
chuyển con giữa hai container **đều đang gắn display**. Chi tiết chứng minh ở §4.1.

Hệ quả thực tế: tài liệu cũ kết luận *"cách duy nhất giữ được state là occlude-first"* (mục #2 bảng xếp
hạng) và xếp `destroy + relaunch` (mất phiên) lên #1. Nếu §4.1 đúng thì **có một đường vừa giữ state vừa
không đi qua cửa sổ null** — tốt hơn cả hai. Phải đo ở T3 để chốt.

*(Ghi theo CLAUDE.md §2: đính chính này ở mức [CM] về **cơ chế source**, nhưng vẫn [NK] về **hành vi thật
trên ROM đã bị vendor sửa** — chỉ T3 mới nâng được lên [CM].)*

---

## 4. Đường vòng — đánh giá từng hướng

### 4.1 ★ KHUYẾN NGHỊ: chuyển **TASK** vào stack có sẵn trên display 1, không chuyển **STACK** qua display

Đây chính xác là hướng #4 trong câu hỏi đặt ra ("tạo stack đích trên display 1 TRƯỚC rồi chỉ move TASK").
**[CM] Câu trả lời: đúng, và nó né được `moveStackToDisplay` hoàn toàn.**

Lệnh: `am stack move-task <TASK_ID> <STACK_ID> true`

**Chứng minh nó không thể rơi vào cửa sổ null — ba khoá độc lập:**

**Khoá 1 — stack đích đã gắn sẵn display, không bao giờ null.**
Đường đi: `runStackMoveTask` (`ActivityManagerShellCommand.java:2525-2544`)
→ `ATMS.moveTaskToStack` (`:2551-2583`) → `TaskRecord.reparent` → `Task.reparent`
(`Task.java:214-248`):

```java
getParent().removeChild(this);                                    // ← 237  gỡ TASK khỏi stack cũ
stack.addTask(this, position, showForAllUsers(), moveParents);    // ← 238  gắn vào stack MỚI
```

Ở đây `stack` là một `TaskStack` **đang nằm trên display 1**, tức `stack.mDisplayContent != null`.
Không có ai gọi `TaskStack.onParentChanged` với `getParent()==null`, nên **không có dòng
`mDisplayContent = null` nào chạy**. Cửa sổ null của §1.3 chưa từng mở ra.

**Khoá 2 — `task.mStack` được gán TRƯỚC khi `addChild` nổ sóng config.**
`TaskStack.addTask` (`TaskStack.java:536-552`):

```java
// Add child task.
task.mStack = this;      // ← 547   gán trước
addChild(task, null);    // ← 548   sóng config nổ sau
```

⇒ khi `AppWindowToken.onConfigurationChanged` → `createTaskSnapshot(task)` chạy,
`Task.getDisplayContent()` = `mStack.getDisplayContent()` = **display 1, khác null**. Snapshot chụp bình
thường, không NPE. (Tức là: **ngay cả khi change-transition VẪN kích hoạt**, nó cũng chỉ chạy đúng như
thiết kế, không crash.)

**Khoá 3 — lúc gỡ khỏi stack cũ không hề có sóng config.**
`TaskStack.removeChild(Task)` (`TaskStack.java:709-713`): `super.removeChild(task); task.mStack = null;`
— `super.removeChild` gọi `setParent(null)`, mà `ConfigurationContainer.onParentChanged:552` chặn khi cha
null (§1.3). Không có ripple ⇒ không có ai đọc `getDisplayContent()` trong khoảng đó.

**Không dính rào `not exported` — [CM].**
Rào đó nằm ở `ActivityStackSupervisor.checkStartAnyActivityPermission` (`:1031-1043`):

```java
} else if (!aInfo.exported) {
    msg = "Permission Denial: starting " + intent.toString()
            + " from " + callerApp + " (pid=" + callingPid + ", uid=" + callingUid + ")"
            + " not exported from uid " + aInfo.applicationInfo.uid;      // ← 1035
}
Slog.w(TAG, msg);
throw new SecurityException(msg);                                          // ← 1043
```

Hàm này chỉ nằm trên đường **`ActivityStarter`** (`am start`). `moveTaskToStack` **không đi qua
`ActivityStarter`** — nó chỉ enforce `MANAGE_ACTIVITY_STACKS` (`ATMS.java:2552`).

**Shell có đủ quyền — [CM].** `packages/Shell/AndroidManifest.xml` (android-10.0.0_r47):

```
:76   <uses-permission android:name="android.permission.INTERNAL_SYSTEM_WINDOW" />
:91   <uses-permission android:name="android.permission.ACCESS_SURFACE_FLINGER" />
:138  <uses-permission android:name="android.permission.MANAGE_ACTIVITY_STACKS" />
:141  <uses-permission android:name="android.permission.ACTIVITY_EMBEDDING" />
```

(`INTERNAL_SYSTEM_WINDOW` ở :76 cũng chính là lý do `am display move-stack` **vào được tới** WM rồi mới
crash — nó qua được `ATMS.java:3396`.)

**Không có rào display — [CM].** `TaskRecord.reparent` gọi `canBeLaunchedOnDisplay(toStack.mDisplayId)`
(`TaskRecord.java:1608-1611`) với `-1/-1/null`, và `ActivityStackSupervisor.isCallerAllowedToLaunchOnDisplay`
(`:1067-1075`) mở đầu bằng:

```java
if (callingPid == -1 && callingUid == -1) {
    if (DEBUG_TASKS) Slog.d(TAG, "Launch on display check: no caller info, skip check");
    return true;                                                          // ← 1074
}
```

**Các cửa có thể chặn — phải biết trước để đọc đúng kết quả test:**

| Cửa | Nguồn | Hành vi khi không qua | An toàn? |
|---|---|---|---|
| stack đích phải là loại `standard`/`undefined` | `ATMS.java:2570-2573` | ném `IllegalArgumentException` **trước** mọi mutate | ✅ |
| thiết bị phải hỗ trợ multi-window / multi-display / freeform | `ActivityStackSupervisor.java:1942-1959` | ném `IllegalArgumentException` **trước** mọi mutate | ✅ |
| **task không resizeable + stack đích ở multi-window** | `ActivityStackSupervisor.java:1963-1972` | **trả `prevStack`** ⇒ `TaskRecord.reparent` thấy `toStack == sourceStack` ⇒ `return false` ⇒ **im lặng không làm gì** | ✅ nhưng **vô ích** |

Cửa thứ ba là cửa cần né. Nguyên văn (`getReparentTargetStack`, `:1961-1972`):

```java
// Leave the task in its current stack or a fullscreen stack if it isn't resizeable and the
// preferred stack is in multi-window mode.
if (inMultiWindowMode && !task.isResizeable()) {
    Slog.w(TAG, "Can not move unresizeable task=" + task + " to multi-window stack=" + stack
            + " Moving to a fullscreen stack instead.");
    if (prevStack != null) {
        return prevStack;              // ← 1967  ⇒ no-op
    }
    stack = stack.getDisplay().createStack(
            WINDOWING_MODE_FULLSCREEN, stack.getActivityType(), toTop);
}
```

**⇒ Vì thế stack mồi trên display 1 phải là FULLSCREEN, không phải freeform.** `inMultiWindowMode` là
false với fullscreen ⇒ nhánh trên bị bỏ qua hoàn toàn, không cần quan tâm CarPlay có resizeable hay không.

**[CM] Và tạo được stack fullscreen trên một display freeform.** `ActivityDisplay.isWindowingModeSupported`
(`:802-808`):

```java
if (windowingMode == WINDOWING_MODE_UNDEFINED
        || windowingMode == WINDOWING_MODE_FULLSCREEN) {
    return true;                       // ← 807  fullscreen LUÔN được chấp nhận
}
```

⇒ `am start --display 1 --windowingMode 1 -n <app exported>` sẽ dựng một stack **fullscreen** trên
display 1 dù `mDisplayWindowingMode=freeform`.

**Thưởng thêm [CM]:** nếu stack mồi là fullscreen và stack CarPlay hiện cũng fullscreen, thì
`prevWinMode == winMode == fullscreen` ⇒ `shouldStartChangeTransition` (§1.5) trả **false** ⇒
`initializeChangeTransition` **không chạy** ⇒ **không chụp snapshot chút nào**. Hai lớp bảo vệ chồng nhau:
kể cả nếu tôi sai ở Khoá 2, lớp này vẫn chặn.

**Mức tin cậy tổng thể: [NK] cao.** Cơ chế là [CM] từ source; nhưng **chưa chạy thật trên xe lần nào**,
và ROM đã chứng minh là có sửa framework (§1.1), nên vẫn phải đo.

### 4.2 `am stack positiontask` — cùng đường, khác cửa vào

`ATMS.positionTaskInStack` (`:3874-3907`) có nhánh:

```java
if (task.getStack() == stack) {
    stack.positionChildAt(task, position);
} else {
    // Reparent to new stack.
    task.reparent(stack, position, REPARENT_LEAVE_STACK_IN_PLACE, !ANIMATE,
            !DEFER_RESUME, "positionTaskInStack");                          // ← 3905
}
```

⇒ **[CM]** cũng đổ về `TaskRecord.reparent`, hưởng nguyên ba khoá của §4.1. Điểm khác: `!ANIMATE` và
`REPARENT_LEAVE_STACK_IN_PLACE`. Dùng làm **biến thể dự phòng** nếu `move-task` no-op vì lý do khác.

### 4.3 Ẩn/đưa CarPlay xuống nền rồi mới `move-stack`

Cơ sở: `AppWindowToken.java:1713` — `|| !isVisible()` ⇒ `shouldStartChangeTransition` trả false.

**[CM] về cơ chế** (guard tồn tại thật). **[ĐOÁN] về hiệu quả thực tế**: phải đúng lúc `isVisible()` trả
false ngay tại thời điểm sóng config chạy. Rủi ro **cao**: vẫn đi thẳng qua cửa sổ null; sai một nhịp là
mất phiên CarPlay, phải rút/cắm cáp. Thêm nữa, chưa biết dịch vụ CarPlay của BYD có tự huỷ projection khi
bị đẩy xuống nền không. **Xếp cuối bảng test.**

### 4.4 `mDisableTransitionAnimation` — ❌ LOẠI

**[CM]** Build-time resource (`WindowManagerService.java:1021-1022`), đọc một lần trong constructor.
Không có `settings`/`device_config`/`setprop` nào chạm tới. Chỉ RRO overlay hoặc vá ROM.

### 4.5 Tắt task-snapshot toàn cục — ❌ LOẠI

Câu hỏi đặt ra có nhắc `setPersistentTaskSnapshotsEnabled` / `mIsRunning` / `disableTaskSnapshots`.
**[CM] Trên Android 10 không tồn tại mặt shell nào như vậy.** Toàn bộ đường tắt snapshot trong
`TaskSnapshotController` là:

```java
// :321-323
private boolean shouldDisableSnapshots() {
    return mIsRunningOnWear || mIsRunningOnTv || mIsRunningOnIoT;
}
```

Ba biến này lấy từ `PackageManager.hasSystemFeature(...)` trong constructor (`:122-127`) — feature của
build, không đổi runtime. Chúng được kiểm ở `:150` và `:453`, **không** ở `createTaskSnapshot`.
Hệ số `mFullSnapshotScale` (`:128`) cũng là resource build-time
(`config_highResTaskSnapshotScale`). Grep cả file: không có `Settings.`, không có `SystemProperties`,
không có `DeviceConfig`. **Không có công tắc nào để bấm.**

### 4.6 Ép display 1 về fullscreen — ❌ LOẠI (và sẽ phá cast thường)

Nếu `mDisplayWindowingMode` của display 1 là `fullscreen` thì không vượt ranh giới ⇒ `move-stack` sống.
Nhưng: **[CM]** trong `ActivityManagerShellCommand` android-10 **không có verb nào** đặt windowing mode cho
một *display* (grep `-i windowing` toàn file: chỉ có `--windowingMode` của `am start` và tham số của
`am stack info`; `DisplayContent.setWindowingMode` ở `:2107` không có đường shell). Hơn nữa cụm **đang cần**
freeform để ClusterNav đặt bounds tuỳ ý cho app thường — đổi nó đi là phá thứ đang chạy tốt, đúng cái
CLAUDE.md §6 cấm.

### 4.7 `am start -n` cho activity không exported — ❌ LOẠI vĩnh viễn

**[CM]** `ActivityStackSupervisor.java:1031-1043` (đã trích ở §4.1). Không có cờ shell nào vượt qua; muốn
qua phải có `START_ANY_ACTIVITY`, mà `packages/Shell/AndroidManifest.xml` **không khai** quyền này (đã grep).
Khớp 100% với quan sát trên xe. Đóng hồ sơ hướng này.

### 4.8 Chiếu gương (mirror) thay vì di chuyển — hướng khác hệ, chưa loại

Ý tưởng: giữ CarPlay nguyên trên display 0, dùng `MediaProjection` bắt hình display 0 rồi vẽ lên một
`Presentation` đặt ở display 1.

- **[CM]** Không đụng WM reparent ⇒ tuyệt đối không dính bug này.
- **[ĐOÁN] Rủi ro lớn chưa kiểm:** (a) `MediaProjection` cần dialog đồng ý của user mỗi phiên;
  (b) nếu cửa sổ CarPlay đặt `FLAG_SECURE` thì vùng đó sẽ **đen thui** — chưa đo, và đây là rủi ro có thật
  với app chiếu điện thoại; (c) display 0 bị chiếm — chủ xe không dùng được màn chính cho việc khác;
  (d) bắt + vẽ lại tốn CPU/GPU trên đầu máy yếu.
- **Chưa đủ dữ kiện để xếp hạng.** Nếu §4.1 chạy được thì không cần tới hướng này. Nếu §4.1 hỏng, đây là
  ứng viên chính cho vòng nghiên cứu sau — nhưng phải theo đúng CLAUDE.md §14: chứng minh bằng shell thô
  trước (ví dụ: kiểm `FLAG_SECURE` bằng `dumpsys window windows | grep -A5 carplay` tìm cờ
  `PRIVATE_FLAG_...`/`FLAG_SECURE`) rồi mới viết dòng code đầu tiên.

### 4.9 Vá ROM — ngoài phạm vi

Về mặt kỹ thuật chỉ cần đảo `:4187` và `:4188` trong `DisplayContent$TaskStackContainers.addStackToDisplay`.
Nhưng đây là xe đang lăn bánh ngoài đường; không đề xuất.

---

## 5. Kế hoạch test trên xe — xếp theo (khả năng ăn × độ an toàn)

**Nguyên tắc chung:** **KHÔNG chạy `am display move-stack` như bước thăm dò nữa.** Đã đủ 3/3 bằng chứng
giống hệt nhau; mỗi lần chạy là một lần chủ xe phải rút/cắm cáp. Chỉ T6 mới được chạm tới nó, và chỉ khi
mọi thứ khác đã hỏng và chủ dự án đồng ý trước.

Chuẩn bị: cáp CarPlay trong tầm tay; ghi log toàn phiên; ghi lại `versionName` thật của app đang chạy
(CLAUDE.md §9 — **không đoán**).

---

**T0 — Chụp trạng thái nền.** *Rủi ro: không. Chỉ đọc.*

```bash
adb shell am stack list > /tmp/stacks-before.txt
adb shell dumpsys window displays | sed -n '/mDisplayId=1/,/^$/p'
adb shell settings get global force_resizable_activities
adb shell settings get global enable_freeform_support
adb shell dumpsys package com.byd.clusternav | grep versionName
```

*Thành công nếu:* thấy stack CarPlay ở `displayId=0` với `mWindowingMode=fullscreen`; thấy display 1 với
`mDisplayWindowingMode=freeform`. → **xác nhận lại §1.6 bằng phép đo MỚI** (dump cũ 2026-07-23 chỉ ở mức
[NK] theo CLAUDE.md §14). Ghi lại `<CARPLAY_TASK_ID>` và `<CARPLAY_STACK_ID>`.

---

**T1 — Verb `move-task` có tồn tại trên ROM này không?** *Rủi ro: không.*

```bash
adb shell am stack
```

*Thành công nếu:* trong help có dòng `move-task <TASK_ID> <STACK_ID> [true|false]`.

> CLAUDE.md §12 ghi rõ ROM DL5 đã **cắt bỏ** `cmd activity set-task-windowing-mode`. Vendor có cắt verb.
> **Phải kiểm, không được giả định.** Nếu verb bị cắt → toàn bộ §4.1 sụp, nhảy thẳng T5/T6.

---

**T2 — Dựng stack mồi FULLSCREEN trên display 1.** *Rủi ro: thấp.*

```bash
adb shell am start --display 1 --windowingMode 1 -n com.byd.clusternav/.MainActivity
adb shell am stack list | grep -B2 "displayId=1"
```

*Thành công nếu:* xuất hiện stack mới `displayId=1` với **`mWindowingMode=fullscreen`** (KHÔNG phải
freeform). Ghi lại `<SEED_STACK_ID>`.
*Nếu nó ra freeform:* `validateWindowingMode` đã hạ cấp — ghi lại nguyên văn, rồi vẫn tiếp T3 nhưng biết
trước là có thể vướng cửa `inMultiWindowMode` (§4.1) và sẽ phải qua T4.
*Rủi ro:* đưa ClusterNav lên cụm có thể làm gián đoạn nội dung cụm đang hiển thị. Dùng chính app của mình
nên hồi phục dễ; **không** dùng app của người khác làm mồi.

---

**T3 — ★ MŨI CHÍNH: chuyển task CarPlay vào stack mồi.** *Rủi ro: thấp (xem §4.1).*

```bash
adb shell am stack move-task <CARPLAY_TASK_ID> <SEED_STACK_ID> true
adb shell am stack list
```

*Thành công nếu:* `taskId` CarPlay nằm dưới stack `displayId=1`, **và cụm hiện CarPlay SỐNG** — kiểm bằng
cách nhờ chủ xe đổi màn trên điện thoại (bản đồ ↔ nhạc) và xem cụm có đổi theo không. Đây là phép thử
phân biệt **surface sống** với **snapshot đóng băng** — đúng triệu chứng đã gặp đêm qua.
*No-op (lệnh trả 0, nhưng task vẫn ở display 0):* đúng nhánh `getReparentTargetStack:1967`. → T4.
*Có exception in ra:* **chép nguyên văn**. Theo §4.1, mọi nhánh ném đều ném **trước** khi mutate ⇒ trạng
thái không hỏng ⇒ CarPlay vẫn sống. Kiểm lại bằng `am stack list` rồi mới đi tiếp.

---

**T4 — Nếu T3 no-op: bật `force_resizable` rồi thử lại.** *Rủi ro: trung bình — nhớ trả lại.*

```bash
adb shell settings put global force_resizable_activities 1
adb shell am stack move-task <CARPLAY_TASK_ID> <SEED_STACK_ID> true
adb shell am stack list
# BẮT BUỘC dọn sau khi đo xong:
adb shell settings put global force_resizable_activities 0
```

Cơ sở: `TaskRecord.isResizeable` (`:1581-1584`) = `mService.mForceResizableActivities || ...`.
**Cảnh báo [NK]:** `mForceResizableActivities` chỉ được gán trong `ATMS.retrieveSettings`
(`:736`, `:750`) — tôi **chưa xác minh được** trên Android 10 có observer nào đọc lại lúc runtime hay
phải khởi động lại. Nếu đặt xong mà `move-task` vẫn no-op y hệt ⇒ nhiều khả năng setting chưa có hiệu lực.
**Đừng reboot xe giữa chuyến để thử.** Ghi nhận rồi đi tiếp.

---

**T5 — Biến thể `positiontask`.** *Rủi ro: thấp, ngang T3.*

```bash
adb shell am stack positiontask <CARPLAY_TASK_ID> <SEED_STACK_ID> 0
adb shell am stack list
```

Chỉ chạy khi T3 và T4 đều no-op. Cùng đường `TaskRecord.reparent` (§4.2), khác cờ animate.

---

**T6 — PHƯƠNG ÁN CUỐI, RỦI RO CAO: ẩn CarPlay rồi `move-stack`.**
*Rủi ro: **cao** — nhiều khả năng mất phiên CarPlay, phải rút/cắm cáp. **Hỏi chủ dự án trước khi chạy.***

```bash
adb shell am start -n com.android.launcher3/.Launcher      # đẩy CarPlay xuống nền
adb shell am stack list | grep -i -A1 carplay              # PHẢI thấy visible=false rồi mới đi tiếp
adb shell am display move-stack <CARPLAY_STACK_ID> 1
```

Cơ sở: `AppWindowToken.java:1713` `|| !isVisible()`. Chỉ chạy khi T1–T5 đều hỏng, và **chỉ khi bước grep
ở giữa thật sự cho `visible=false`** — nếu vẫn `visible=true` thì **dừng**, đừng chạy dòng thứ ba.

---

**T7 — Dọn dẹp (luôn chạy, kể cả khi bỏ dở giữa chừng).**

```bash
adb shell settings put global force_resizable_activities 0
adb shell am stack list > /tmp/stacks-after.txt
diff /tmp/stacks-before.txt /tmp/stacks-after.txt
```

Đối chiếu để chắc chắn không để lại stack mồi mồ côi trên display 1 (bài học CLAUDE.md §4/§5: state đổi ra
ngoài phải có đường trả lại). Nếu còn stack mồi rỗng: `am stack remove <SEED_STACK_ID>` — **chỉ khi nó rỗng**,
tuyệt đối không remove stack đang chứa task CarPlay.

---

### Bảng xếp hạng

| # | Thử nghiệm | Khả năng ăn | An toàn | Nếu hỏng thì mất gì |
|---|---|---|---|---|
| T1 | `am stack` có verb `move-task`? | — (chỉ để biết) | ✅ tuyệt đối | không |
| T0 | Chụp nền + xác nhận display 1 freeform | — | ✅ tuyệt đối | không |
| T2 | Dựng stack mồi fullscreen trên display 1 | cao | ✅ cao | nội dung cụm bị chiếm tạm thời |
| **T3** | **`move-task` vào stack mồi** | **cao [NK]** | **✅ cao [CM]** | không (no-op hoặc ném trước mutate) |
| T4 | T3 + `force_resizable=1` | trung bình | ⚠️ vừa (setting toàn hệ thống) | phải nhớ trả lại |
| T5 | `positiontask` | trung bình | ✅ cao | như T3 |
| T6 | Ẩn rồi `move-stack` | thấp [ĐOÁN] | ❌ thấp | **phiên CarPlay, phải rút/cắm cáp** |

---

## 6. Ý nghĩa cho sản phẩm

1. **[CM] `CommandKind.MOVE_STACK_TO_CLUSTER` đang phát ra một lệnh làm sập system_server trên DiLink3.**
   Không phải "đôi khi không mượt" — trên đúng cấu hình này (display 0 fullscreen → display 1 freeform,
   app đang hiện) nó **luôn** vượt ranh giới freeform, nên **luôn** nổ. Đây rất có thể là gốc rễ thật của
   chuỗi báo cáo lịch sử "cast CarPlay lúc được lúc không".

2. **Trước khi sửa code phải có spec.** Đây là thay đổi cơ chế đặt cửa sổ, không phải vá nóng < 20 dòng
   ⇒ CLAUDE.md §1 + §14 áp dụng đầy đủ: **T0–T3 phải xanh với bằng chứng shell thật trước**, rồi mới
   `car-integration` → `core` → UI. Không viết code cho §4.1 khi chưa có output thật của T3.

3. **Khi tới lượt code, giữ đúng tinh thần CLAUDE.md §6/§7:** đường mới (`move-task`) **xuống cuối thang**,
   không đảo lên trước đường `am start --windowingMode 5` đang chạy tốt cho app thường; và **rẽ nhánh bằng
   đo đạc**, không bằng `if (pkg == "com.byd.carplay.ui")` — điều kiện đúng là *"`am start` vừa bị
   `SecurityException` không-exported"*, một sự kiện quan sát được, áp dụng cho mọi app protected
   (CarPlay hôm nay, Android Auto có thể ngày mai).

4. **Test hồi quy (CLAUDE.md §10):** thêm fixture `am stack list` có `mDisplayWindowingMode=freeform` ở
   display 1 và `fullscreen` ở display 0, khoá lại quy tắc *"không bao giờ phát `am display move-stack`
   khi hai display khác nhau về ranh giới freeform"*. Đây là bài học rẻ nhất để khoá lại vĩnh viễn.

5. **Sửa ngay `CarExecCatalog.kt` (không cần chờ xe, không cần spec — đây là sửa nhãn sai, không đổi hành vi):**
   hai candidate `place.movestack` (`:186-194`) và `return.movestack-main` (`:686-694`) đang gắn
   `risk = CandidateRisk.READ_ONLY` **cho một lệnh làm sập system_server**. Phải nâng lên mức phá huỷ cao
   nhất, kèm field note trỏ về tài liệu này. Ngoài ra cả hai truyền `{taskId}` vào tham số `<STACK_ID>`
   (§3.3) — sai kiểu tham số. Rủi ro hiện tại: ai đó đọc catalogue tưởng lệnh vô hại rồi bấm thử trên xe thật.

6. **[CM] Đường task-based còn sống ở đời sau, đường stack-based thì không.** Đối chiếu AIDL:
   `moveStackToDisplay` **biến mất** ở Android 12 (DL5), đổi tên thành `moveRootTaskToDisplay`; còn
   `moveTaskToStack` sống tiếp dưới tên `moveTaskToRootTask` (`A12 IActivityTaskManager.aidl:209`).
   Nghĩa là hướng §4.1 không chỉ né được bug A10 mà còn **ánh xạ thẳng sang DL5** — thêm một lý do kiến
   trúc để chọn nó, ngoài lý do an toàn.

5. **Về Android Auto: [CHƯA BIẾT].** Toàn bộ tài liệu này đo trên CarPlay. Chưa có phép đo nào cho thấy
   AA cũng không-exported hay cũng crash y hệt. CLAUDE.md §14 đã ghi rõ một lần suy diễn sai về AA trong
   quá khứ. **Không suy rộng.** Cần một lượt T0/T1 riêng cho AA.

---

## 7. Việc còn treo

| Việc | Vì sao cần | Cần xe? |
|---|---|---|
| **Chạy T0–T3** | Quyết định CarPlay/AA có cast được hay không — **việc quan trọng nhất** | ✅ |
| Sửa `risk` + kiểu tham số của 2 candidate `move-stack` trong `CarExecCatalog.kt` (§6.5) | Đang gắn `READ_ONLY` cho lệnh làm sập system_server | ❌ |
| Cập nhật `docs/_handoff/research-aosp-wm.md:168` theo §3.4 | Kết luận cũ đang chặn nhầm đường khả thi duy nhất | ❌ |
| `adb pull` + baksmali `services.jar` → đọc `TaskSnapshotController` của ROM | Nâng §1.8 từ [NK] lên [CM]; xác nhận object null | ⚠️ cần adb, không cần CarPlay |
| Kiểm `FLAG_SECURE` của cửa sổ CarPlay (`dumpsys window windows`) | Chốt tính khả thi của §4.8 nếu §4.1 hỏng | ✅ |
| Lượt T0/T1 riêng cho Android Auto | Chưa có phép đo nào cho AA; CLAUDE.md §14 cấm suy rộng từ CarPlay | ✅ |

### Đã hoàn tất trong phiên này

- ✅ §1 — cơ chế crash, chứng minh tới `file:line` trên AOSP r47 đã fetch nguyên văn.
- ✅ §1.1 — ánh xạ số dòng ROM ↔ AOSP thuần, xác nhận framework xe đã bị vendor sửa.
- ✅ §1.6 — đối chiếu dump thật: display 1 = freeform, display 0 = fullscreen.
- ✅ §2 — xác định bản vá upstream (A11), xác nhận không có bản vá nào cho A10 (1 tag + 7 nhánh đã kiểm).
- ✅ §3 — quét xong `../dashcast-src/`, `../jadx-dashcast/`, `../jadx-openbyd{,24}/`, `CarExecCatalog.kt`,
  `docs/`. Kết quả chính là **âm tính** (không có lời giải để kế thừa) + một đính chính (§3.4).
- ❌ **Chưa có phép đo mới nào trên xe** — toàn bộ §4/§5 vẫn ở mức [NK], phải qua T0–T3 mới lên [CM].
