# Off-car 2026-09-26 — SYS-TASKBAR-VOICE-FOCUS · SHELL-19 · LOG-41KB

> **Trạng thái**: Current · **Loại**: diagnostics (điều tra + bản vá) · **Cập nhật**: 2026-09-26
> **Nguồn vào**: `docs/diagnostics/perf-oncar-2026-09-26/` — `logcat-stream-2.70.txt` · `taskbar-window-dump.txt` ·
> `after-oncar-2.69.md` · `kachi-logs/usage-*.log` (**cục bộ, gitignore — KHÔNG commit**).
> **Việc gốc**: `docs/_handoff/session-2026-09-26-oncar-wrapup.md` §4 hàng 1 và hàng 5.
> Mọi khẳng định gắn nhãn `[ĐO]` / `[SUY]` / `[ĐOÁN]` / `[CHƯA BIẾT]` theo `.kiro/steering/conversation-protocol.md` P1.
> Nguồn AOSP trích trong tài liệu này đều lấy ở tag **`android-10.0.0_r47`** (`platform/frameworks/base`).

---

## 0. Kết luận một dòng mỗi việc

| # | Việc | Kết luận | Bản vá |
|---|---|---|---|
| 1 | **SYS-TASKBAR-VOICE-FOCUS** | Không phải TTS. Taskbar lên vì **tấm chữ giọng nói lấy tiêu điểm cửa sổ**, và ở lại vì lượt nó **mất** tiêu điểm để display 0 không còn cửa sổ nào giữ cờ ẩn thanh. | `VoiceOverlay` → `FLAG_NOT_FOCUSABLE`, thoát bằng chạm-ra-ngoài. |
| 2 | **SHELL-19** | 19,0 lệnh/phút = **15** (`am stack list` mỗi 4 s, watchdog giữ-cụm) + **≈4** (`am stack list` của nhịp đo ô, đã lùi tới trần 15 s). Cả hai đều **đã có cổng**; không tìm thấy lệnh nào chạy vô ích. | Không cắt (xem §3.3 — cắt thêm là đổi độ trễ phục hồi ⇒ quyết định của owner). |
| 3 | **LOG-41KB** | **≈90 % byte** là `Log.d` của **thư viện HAL BYD** trong tiến trình ta, một dòng mỗi lượt getter, do nhịp đọc ô điều khiển **1 Hz mà owner yêu cầu**. Không cắt được nhịp, nhưng **dòng lặp y nguyên từng chữ** thì không cần xuống thẻ. | Tiết chế theo **nội dung** ở chỗ ghi thẻ: [ĐO mô phỏng] **34,3 → 8,6 KB/phút**. |

---

## 1. SYS-TASKBAR-VOICE-FOCUS — gốc thật

### 1.1 Hiện tượng (owner, buổi xe 26/09)
Tấm chữ giọng nói hiện lên thì **không** thấy taskbar; đúng lúc Kachi **đọc phản hồi** thì taskbar/thanh điều
hướng của xe trồi lên và **ở lại tới khi tấm chữ tắt**.

### 1.2 Bằng chứng [ĐO — `logcat-stream-2.70.txt`, buổi xe 26/09]

```
18:29:15.709 W/WindowManager debug_focus Changing focus from …/KachiHomeActivity
                             to Window{3e5380 u0 com.byd.launcher}   displayId=0
18:29:15.711 D/StatusBar     setSystemUiVisibility displayId=0 vis=8008 mask=ffffffff oldVal=970e newVal=8008 diff=1706
18:29:15.717 D/BarController.NavigationBar setBarShowingLw show=true        ← thanh LÊN
18:29:15.723 D/BarController.NavigationBar setBarShowingLw show=false       ← 6 ms sau, xuống
18:29:15.746 D/StatusBar     setSystemUiVisibility displayId=0 … oldVal=8008 newVal=970e diff=1706
      ⇒ NHÁY 37 ms lúc tấm chữ NHẬN tiêu điểm — đúng lý do owner "không thấy taskbar lúc nó hiện"

18:29:20.755 W/WindowManager debug_focus Changing focus from null to vn.vietmap.live/MainActivity displayId=7
18:29:20.756 W/WindowManager debug_focus Changing focus from Window{3e5380 u0 com.byd.launcher} to null displayId=0
18:29:20.758 D/StatusBar     setSystemUiVisibility displayId=0 vis=9708 mask=ffffffff oldVal=970e newVal=9708 diff=6
18:29:20.760 D/BarController.NavigationBar setBarShowingLw show=true        ← thanh LÊN
18:29:20.771 D/AS.AudioService requestAudioFocus callingPackageName=com.byd.launcher …   ← TTS xin sau 15 ms
…
18:30:00.377 W/WindowManager debug_focus Changing focus from null to Window{…} displayId=0  ← lượt nói KẾ
18:30:00.381 D/BarController.NavigationBar setBarShowingLw show=false       ← 39,6 s SAU mới xuống
```

Đọc ra được năm điều, tất cả `[ĐO]`:

1. `Window{3e5380 u0 com.byd.launcher}` — **tên gói, KHÔNG có tên Activity** ⇒ đúng cửa sổ overlay
   (`taskbar-window-dump.txt` cũng chỉ thấy `ty=APPLICATION_OVERLAY … package=com.byd.launcher`), ở tiến trình
   `:wake` (`D/ViewRootImpl[](22962) … VoiceOverlay$build$5`).
2. **TTS không phải nguyên nhân**: thanh đã lên ở `.760`, lượt xin audio-focus của giọng đọc tới `.771` — **sau
   15 ms**.
3. **Không có cửa sổ thứ hai, không có Toast, không `show()` lại**: id cửa sổ `3e5380` giữ nguyên suốt lượt; điều
   duy nhất đổi ở pha phản hồi là **tiêu điểm**.
4. Khung cửa sổ của chính tấm chữ đi từ `0,0-1920,1080` → `0,0-1920,990` (90 px = thanh điều hướng) và **ở lại**
   990 tới khi tấm chữ tắt.
5. `diff` của hai lượt **khác nhau về bản chất**: lượt *nhận* tiêu điểm rụng `0x1706` (cả bộ cờ), lượt *mất* tiêu
   điểm rụng đúng `0x6`.

### 1.3 Cơ chế trong nguồn AOSP `android-10.0.0_r47`

| Bước | Nguồn | Điều nó nói |
|---|---|---|
| a | `services/core/java/com/android/server/wm/DisplayPolicy.java:3028-3040` (`focusChangedLw`) | Đổi tiêu điểm ⇒ `mFocusedWindow = newFocus` rồi **gọi ngay** `updateSystemUiVisibilityLw()`. Đúng khoảng 2 ms trong log. |
| b | `DisplayPolicy.java:3112-3119`, `:3151-3153` | `winCandidate = mFocusedWindow != null ? mFocusedWindow : mTopFullscreenOpaqueWindowState`, rồi `PolicyControl.getSystemUiVisibility(win, null)` — cờ ẩn thanh hệ thống đọc **chỉ** từ cửa sổ đang có tiêu điểm. |
| c | `DisplayPolicy.java:3194-3196` | Kết quả gửi đi bằng `statusBar.setSystemUiVisibility(displayId, visibility, …, 0xffffffff, …)` — khớp `mask=ffffffff` trong log. |
| d | `core/java/android/view/View.java:3842` | `SYSTEM_UI_CLEARABLE_FLAGS = LOW_PROFILE \| HIDE_NAVIGATION \| FULLSCREEN` = `0x7`. `diff=6` = rụng đúng `HIDE_NAVIGATION\|FULLSCREEN`. |
| e | `DisplayPolicy.java:3374-3382` + `:3494-3498` (`clearClearableFlagsLw`) | Nhánh duy nhất rụng đúng bộ `0x7` mà **giữ** `IMMERSIVE_STICKY` + ba cờ `LAYOUT_*` (`newVal=9708` vẫn còn `0x1700`). Nó mở khi `mForceShowSystemBars`. |
| f | `DisplayPolicy.java:3280-3290` | `mForceShowSystemBars = dockedStackVisible \|\| freeformStackVisible \|\| resizing \|\| …` — Kachi đặt app vào ô nên cụm docked/freeform **đang hiện** trên xe. |
| g | `DisplayContent.java:3016-3024` (`findFocusedWindowIfNeeded`) | Trả **null** cho mọi display không phải màn top-focused khi `mPerDisplayFocusEnabled == false`. |
| h | `WindowManagerService.java:648-649`, `:1023-1024` | `mPerDisplayFocusEnabled` đọc từ `config_perDisplayFocusEnabled` (mặc định **false**). |
| i | `WindowState.java:2559-2565` (`canReceiveKeys`) | Cửa sổ mang `FLAG_NOT_FOCUSABLE` bị loại thẳng khỏi `findFocusedWindow`. |
| j | `DisplayPolicy.java:2413`, `:2437-2439` | `mTopFullscreenOpaqueWindowState` chỉ nhận **cửa sổ app** (`appWindow = attrs.type >= FIRST_APPLICATION_WINDOW`) — `TYPE_APPLICATION_OVERLAY` không vào được. |
| k | `core/java/android/view/WindowManager.java:1164-1177` | `FLAG_NOT_FOCUSABLE` chỉ chặn **phím**, và bật kèm `FLAG_NOT_TOUCH_MODAL` (chạm **ngoài** cửa sổ đi xuống dưới). |

**ROM này đúng hình AOSP-10 ở đúng chỗ đó** `[ĐO]`: dòng log của chính ROM
`V/WindowManager DPfinishLw attrs.isFullscreen()=… inFullScreenOrSplitScreenSecondaryWindowingMode=… / Fullscreen
window: Window{…}` là BYD chèn thêm vào **đúng khối** `DisplayPolicy.java:2416-2418` + `:2437-2439`.

### 1.4 Chuỗi nhân quả đầy đủ

1. Tấm chữ được thêm vào và **lấy tiêu điểm** (nó cố ý focusable từ vòng 18/09, để nhận Back).
2. Lượt **nhận** tiêu điểm: `focusChangedLw` (a) tính lại (b) khi giá trị `systemUiVisibility` của View còn chưa
   về tới WM ⇒ tính bằng 0 cờ ⇒ thanh lên; 37 ms sau `goImmersive` áp lại ⇒ thanh xuống. Đây là **cái nháy**
   `[ĐO §1.2]`, và là lý do triệu chứng *trông như* chỉ xảy ra lúc đọc phản hồi.
3. Pha phản hồi của một lệnh có hành động (*"mở vietmap"*) mở app trên **màn của ô** (display 7/8/9) ⇒ màn đó
   thành top-focused ⇒ theo (g)+(h) display 0 bị đặt tiêu điểm **null** (`taskbar-window-dump.txt`:
   `mCurrentFocus=null`, `mFocusedApp=…youtube…`).
4. `focusChangedLw(overlay, null)` tính lại (b) với cửa sổ dự bị, và nhánh (e)+(f) rụng đúng `0x7` ⇒ **thanh lên**.
5. Không ai áp lại được: cửa sổ duy nhất còn muốn ẩn thanh trên display 0 là tấm chữ, mà
   `onWindowFocusChanged(true)` không bao giờ tới lần nữa ⇒ thanh **ở lại 39,6 s**, tới lượt nói kế tiếp.

`[SUY]` Nhánh nào trong (e) mở — `denyTransientNav` hay `mForceShowSystemBars` — thì cả hai đều đòi
`mForceShowSystemBars`, nên với xe đang có app trong ô thì kết quả là một. Chưa cần phân biệt để chữa: bước (4)
chỉ tồn tại **vì** bước (1).

### 1.5 Bản vá
`app/src/main/java/com/byd/clusternav/launcher/voice/VoiceOverlay.kt`

- `FLAG_NOT_FOCUSABLE` thêm vào tham số cửa sổ (giữ `FLAG_LAYOUT_NO_LIMITS`, `dimAmount = 0f`, không
  `FLAG_DIM_BEHIND`). Theo (i)+(j) cửa sổ này **không còn là một biến** trong bài toán thanh hệ thống: app/màn
  chính đang giữ tiêu điểm giữ luôn trạng thái thanh của nó, y như lúc chưa có tấm chữ. **Cả hai** triệu chứng
  (nháy lúc hiện + thanh ở lại lúc phản hồi) cùng mất vì cùng một lý do.
- Gỡ `dispatchKeyEvent`/`KEYCODE_BACK`, `onWindowFocusChanged`, `isFocusableInTouchMode`: cửa sổ không lấy tiêu
  điểm thì **không nhánh nào trong ba nhánh ấy chạy được** (CLAUDE.md §8 — và để lại còn tệ hơn mã chết: nó làm
  người sau tin rằng Back vẫn là đường thoát).
- **Đường thoát**: chạm ra ngoài tấm chữ (`setOnTouchListener` + `inside(card, ev)`, đã có từ trước) — cửa sổ phủ
  toàn màn nên vùng huỷ là cả màn trừ tấm chữ; cộng trần 8 giây ở `VoiceSession`. Không cần
  `FLAG_WATCH_OUTSIDE_TOUCH` (không có "ngoài"), không dùng accessibility, không bơm phím.
- `goImmersive` **giữ nguyên bộ cờ**: ba cờ `LAYOUT_*` còn một việc thật (khung tấm chữ bằng cả màn ⇒ thanh hiện
  thì tấm chữ không nhảy lên 90 px giữa lúc đang nói), và giữ đúng một bộ với màn chính thì không có ca nào hai
  bề mặt xin hai trạng thái khác nhau.

### 1.6 Đổi hành vi người dùng — nói rõ
| Trước | Sau |
|---|---|
| Back huỷ phiên nghe | Back đi tới app đang chạy (hành vi hệ thống bình thường); huỷ phiên = **chạm ra ngoài tấm chữ** hoặc chờ trần 8 s |
| Taskbar trồi lên khi Kachi đọc phản hồi, ở lại tới khi tấm chữ tắt | `[SUY]` không còn — 🚗 phải nhìn để lên `[ĐO]` |

`[SUY]` KDoc của `VoiceSession` (dòng ~194, ~199-200) còn nhắc *"chạm ra ngoài / Back"* — tệp ấy thuộc lượt khác
đang sửa song song, **không** chạm ở lượt này; cần một câu đính chính ở đó (ghi vào báo cáo cho owner). Không có
chuỗi giao diện nào nhắc Back (`grep` `strings_kachi.xml` ⇒ 0 dòng).

### 1.7 Test
`app/src/test/java/com/byd/clusternav/launcher/voice/VoiceOverlayImmersiveContractTest.kt` (đổi chiều bài 18/09):

- `cua so KHONG lay tieu diem va do la lua chon co y` — `FLAG_NOT_FOCUSABLE` **phải có**; `dispatchKeyEvent` ·
  `KEYCODE_BACK` · `onWindowFocusChanged` · `isFocusableInTouchMode` **phải không còn**.
- `cham ra ngoai van la duong thoat va chi ngoai tam chu` — đường thoát còn lại không được rụng theo.
- Hai bài cũ giữ nguyên ý: bộ cờ trùng màn chính · không `FLAG_DIM_BEHIND` · `dimAmount = 0f` · trần 500 dòng.

Bài canh quét **source** (dự án không có Robolectric; thanh hệ thống của BYD không có trên máy ảo) và mọi phép
cắt vùng đi qua `SourceRoots.body` — **nổ** nếu mốc không còn, nên không có ca "quét tràn cả tệp".

---

## 2. LOG-41KB — 41,2 KB/phút đi đâu

Cửa sổ đo: `kachi-logs/usage-*.log` của bản 2.69 (tiến trình chính), khoảng **yên** `18:11:30 → 18:16:30`
(2 094 dòng / 171,6 KB / 5 phút = **34,3 KB/phút**; đúng sàn mà bộ đếm báo 34–41 KB/phút).

| tag | byte/5 phút | dòng/phút | ai ghi | nhịp |
|---|---:|---:|---|---|
| `BYDAutoAcDevice` | 71 832 | 197 | **thư viện HAL BYD** | `getTemprature` ×2 dòng + `getAcWindLevel`, 1 Hz |
| `BYDAutoSettingDevice` | 36 736 | 66 | **thư viện HAL BYD** | `getSeatVentilatingState`, 1 Hz |
| `BYDAutoBodyworkDevice` | 25 628 | 60 | **thư viện HAL BYD** | `getWindowOpenPercent`, 1 Hz |
| `BYDAutoInstrumentDevice` | 10 890 | 24 | **thư viện HAL BYD** | nhịp chậm 10 s |
| `BYDAutoPM2p5Device` | 8 340 | 18 | **thư viện HAL BYD** | nhịp chậm 10 s |
| `BYDAutoStatisticDevice` | 5 430 | 12 | **thư viện HAL BYD** | nhịp chậm 10 s |
| `SimpleCast` | 8 850 | 30 | ta | `shell: am stack list` + `shell OK: exit=0`, mỗi 4 s |
| `ViewRootImpl[KachiHomeActivity]` | 4 110 | 6 | framework | `debug_draw` |
| `VmOverlayPos` | 1 805 | 3,8 | ta | `gửi VM_BUBBLE_POS x=… y=…` **cùng toạ độ**, mỗi 16 s |
| `NavRebind` | 1 140 | 2 | ta | báo động watchdog no-op |
| `KachiPerf` | 845 | 1 | ta | dòng số/phút |

**`[ĐO]` ≈90 % byte (158 856 / 171 566) là `Log.d` của thư viện HAL BYD** — nó tự ghi một (đôi khi hai) dòng cho
**mỗi lượt getter**, trong tiến trình ta, nên `logcat --pid` của ta bắt hết. 378 dòng/phút so với `HAL đọc=312/phút`
của bộ đếm ⇒ khớp (một số getter in hai dòng).

**Không cắt được nhịp sinh ra chúng**: nhịp 1 Hz của ô điều khiển là **owner yêu cầu** — `CarDataAdapter.readFast`,
*"#5 (owner 2026-09-21 «không realtime»): giá trị Ô ĐIỀU KHIỂN (nhiệt/gió/…) đọc ở nhịp NHANH (1 s) để người lái
chỉnh trên màn AC gốc thì launcher đổi trong ~1 s"*. Và ta không gọi được `Log` của thư viện khác để nó im.

### 2.1 Bản vá — tiết chế theo **nội dung** ở chỗ ghi thẻ
`core/…/launcher/LogLineThrottle.kt` (thuần) + `app/…/launcher/KachiLog.kt` (`throttled`, nối vào vòng ghi của
`startCapture`).

Luật: một dòng **y nguyên từng chữ** (khoá = phần sau dấu thời gian) đã ghi trong `10 s` thì bỏ; lần ghi kế mang
hậu tố `[+N lặp]`. Hai ngoại lệ có chủ đích:
- **W/E/F/A không bao giờ bị bỏ** — đó đúng là tập dòng mà tệp log tồn tại để cứu, và một cảnh báo lặp 100 lần
  **là** thông tin.
- **Giá trị đổi thì qua ngay**: `… seat ventilating is 3` → `… is 1` là một chuỗi **khác** ⇒ chưa từng thấy ⇒ ghi
  ngay, dù cách dòng trước 1 ms. Đây là thứ giữ nguyên giá trị chẩn đoán của log HAL (thang ghế OFF=1/1=2/2=3 của
  buổi 17/09 suy ra được **chính** từ những lần đổi ấy) — cũng là lý do **không** lọc theo mức (`*:I`) hay theo tag.

Trần LRU 512 khoá: khoá bị đẩy ra ⇒ lần sau "chưa từng thấy" ⇒ **ghi** (mất số đếm, không bao giờ mất dòng).
Đồng hồ lùi ⇒ coi như hết cửa sổ ⇒ ghi (không để một cú chỉnh giờ khoá log vĩnh viễn).

### 2.2 Trước / sau `[ĐO mô phỏng trên chính tệp log thật của xe]`

| Cửa sổ | Trước | Sau (`windowMs = 10 s`) | Cắt |
|---|---:|---:|---:|
| Yên, 5 phút (`18:11:30→18:16:30`) | 34,3 KB/phút · 2 094 dòng | **8,6 KB/phút · 494 dòng** | −75 % byte |
| Cao điểm 1 phút, có camera (`18:07:30→18:08:30`) | 127,6 KB/phút · 1 566 dòng | **48,1 KB/phút** | −62 % byte |

(Hậu tố `[+N lặp]` cộng thêm ≈11 byte cho mỗi dòng được ghi lại ⇒ `[SUY]` sàn thật ≈ **9 KB/phút**, dưới trần
K5 *log < 20 KB/phút*.) Test `LogLineThrottleTest.nhip 1 Hz cua thu vien HAL bi cat khoang 90 phan tram` dựng lại
đúng vòng 8 dòng đo được và khoá con số: 480 dòng → 42 (**92 %**).

### 2.3 Cắt thêm: `VmOverlayPos`
`VmOverlayPosition.send` **vẫn bắn broadcast như trước** (bản mod VietMap dựng lại bong bóng giữa chuyến thì chính
lượt bắn lặp ấy đưa nó về chỗ owner đã chỉnh — K8/1.70). Chỉ **dòng log** đi qua một `ResendGate(5 phút)`: toạ độ
đổi (người dùng kéo) ghi ngay, toạ độ cũ 5 phút một dòng ⇒ 3,8 → 0,2 dòng/phút. Dùng lại `ResendGate` chứ không
viết bộ đếm thứ hai (CLAUDE.md §4.1).

**Không** demote `SimpleCast`/`VmOverlayPos` từ `I` xuống `D`: `logcat --pid` bắt **mọi mức**, nên đổi mức không
giảm một byte nào xuống thẻ — chỉ tiết chế theo nội dung mới giảm. Ghi ra đây để lượt sau đừng thử lại.

---

## 3. SHELL-19 — 19,0 lệnh/phút đi đâu

### 3.1 Bộ đếm đếm cái gì
`KachiPerf.Counter.SHELL_CMD` tăng ở **`ShellTransport.attempt`** — tức mỗi lệnh **thật sự rời tiến trình** qua
dadb (`localhost:5555`), kể cả lượt thử lại. Đó là **chủ duy nhất** của kết nối lệnh cửa sổ + cast, một hàng đợi
một worker ⇒ mỗi lệnh là một lượt chặn trên hàng đợi dùng chung. `LOG_BYTES` tăng ở `KachiLog.startCapture` (byte
ghi xuống thẻ).

### 3.2 Bảng quy kết

| Chỗ gọi | lệnh/phút | Lệnh | Vì sao | Bằng chứng | Việc |
|---|---:|---|---|---|---|
| `SimpleCastCoordinator.repinEscapedCastApps` ← `FloatingBubbleService.refresh` (nhịp 2 s) | **15,0** `[ĐO]` | `am stack list` | Watchdog giữ-cụm: một trigger ngoài (Kiki mở GMaps) kéo app đang chiếu ra khỏi cụm ⇒ phải đo mới biết. Cổng `REPIN_PROBE_MIN_INTERVAL_MS = 4 s` ⇒ chạy mỗi lượt-tick-thứ-hai. | 75 dòng `I/SimpleCast: shell: am stack list` trong 5 phút, cách nhau đúng 4,00 s | **giữ** — K7 = DONE-chấp nhận (owner 26/09); xem §3.3 |
| `SlotLiveProbe` (nhịp `SlotLiveness.probePeriodMs`, 5 s → lùi tới trần **15 s**) | **≈4,0** `[SUY]` | `am stack list` | *"App trong ô còn sống không"* — màn ảo chết thì `SurfaceView` giữ khung cuối nên phải đo. Đã có cổng K8: kết quả đứng yên ⇒ lùi 5 → 10 → 15 s. | Số dư khớp **chính xác**: 19,0 − 15,0 = 4,0 = 60/15 s. Đường này **không ghi log mỗi nhịp** nên không thấy trong tệp usage — đó cũng là lý do nó "vô hình". | **giữ** — đã ở trần backoff |
| `VmOverlayPosition.applyOnOpen` | **0** | — | `sendBroadcast` (binder), **không** qua shell ⇒ không vào bộ đếm này. Đã có `ResendGate(15 s)` + `InstalledPackageGate(60 s)`. | `VmOverlayPosition.kt` — không có `ShellTransport` | — |
| `HalHelperLauncher` (camera) | 0 khi đứng yên | `wc -c` · `pkill` · `app_process` | Chỉ lúc `CameraSignalController` khởi/bảo đảm helper, không có nhịp. | không có dòng `shell:` nào khác trong cửa sổ đo | — |
| `ShellChannelGate` (dò kênh) | 0 khi đứng yên | `echo kachi_ok` | Đi đường `LocalDeviceShell` riêng (có hạn đọc), **không** qua `ShellTransport` ⇒ không vào bộ đếm. | KDoc `ShellChannelGate` | — |

**Tổng `[ĐO]`+`[SUY]` = 19,0/phút** — khớp con số bộ đếm, không còn số dư không giải thích được.

### 3.3 Không cắt — và vì sao (không phải vì khó)
Cả hai nguồn **đã** có cổng, và cả hai đo **đúng thứ chỉ đo mới biết được** (không có tín hiệu hệ thống nào bắn về
khi app rời cụm hoặc chết trong ô). Cắt thêm chỉ còn hai đường, **cả hai đổi hành vi thấy được**:

1. Giãn `REPIN_PROBE_MIN_INTERVAL_MS` 4 s → 8/15 s: độ trễ phục hồi *"app bị kéo khỏi cụm"* đi từ ≈8 s
   (2 nhịp hụt × 4 s) lên ≈20–30 s. `[SUY]` Đây là đường đã chạy tốt ngoài hiện trường và K7 là **DONE-chấp
   nhận** ⇒ CLAUDE.md §6 + `trace-den-tan-cung.md`: **quyết định của owner**, không tự cắt.
2. Bỏ nhịp đo khi màn xe tắt (`isInteractive`): không giúp con số 19/phút lúc đang lái (màn sáng), chỉ giúp lúc
   đỗ. `[ĐOÁN]` giá trị thấp, chưa làm.

**Hỏi owner (một câu)**: có đổi 4 s → 8 s ở watchdog giữ-cụm không (19 → 11,5 lệnh/phút, đổi lấy độ trễ phục hồi
8 s → 16 s)? Chưa có câu trả lời thì giữ 4 s.

Ghi chú `[ĐO]`: mỗi `am stack list` mất **70–90 ms** (`shell:` → `shell OK:` trong log) ⇒ 19/phút ≈ **2,5 %** thời
gian của hàng đợi, không phải nút cổ chai; rủi ro thật là **tranh chỗ** với lệnh đặt cửa sổ, mà `ShellTransport`
đã có hàng đợi ưu tiên (STOP/RESCUE vượt NORMAL) cho đúng ca đó.

---

## 4. Còn cần xe 🚗

| # | Đo gì | Lệnh / quan sát | Đạt khi |
|---|---|---|---|
| 1 | Taskbar lúc Kachi đọc phản hồi | Nói *"mở vietmap"* rồi **nhìn**; kèm `dumpsys window \| grep -E "mCurrentFocus\|NavigationBar"` | Thanh điều hướng **không** trồi lên; `mCurrentFocus` là app/màn chính (không phải overlay) trong suốt lượt nói |
| 2 | Đường thoát mới | Chạm ra ngoài tấm chữ lúc đang nghe | Phiên huỷ ngay (như trước); Back thì đi tới app đang chạy — **đúng như thiết kế**, không phải lỗi |
| 3 | `log=… KB/phút` sau bản vá | Cài bản mới, đứng yên màn chính 5 phút, đọc dòng `I/KachiPerf` | `log < 20 KB/phút` (kỳ vọng ≈9); và trong `usage-*.log` phải **thấy** hậu tố `[+N lặp]` + **vẫn thấy** mọi lần đổi giá trị HAL |
| 4 | `shell=… /phút` | cùng dòng `I/KachiPerf` | vẫn ≈19,0 (bản vá này **không** nhắm shell) — nếu lệch nhiều thì có nguồn thứ ba chưa biết |
| 5 | Chốt `[SUY]` 4 lệnh/phút của nhịp đo ô | Đóng hết ô App (chỉ để widget) rồi đọc lại `shell=` | Phải xuống ≈15,0/phút — đúng thì quy kết ở §3.2 lên `[ĐO]` |
| 6 | CPU idle 2.7x lúc **yên** | theo playbook `diagnostics/perf-profile-2026-09-16.md` §6 | `< 2 %` (8 lõi) |

---

## 5. Tệp đã đổi

| Tệp | Việc |
|---|---|
| `app/src/main/java/com/byd/clusternav/launcher/voice/VoiceOverlay.kt` | `FLAG_NOT_FOCUSABLE`; gỡ Back/`onWindowFocusChanged`/`isFocusableInTouchMode`; KDoc gốc-bệnh + trích dẫn AOSP |
| `app/src/test/java/com/byd/clusternav/launcher/voice/VoiceOverlayImmersiveContractTest.kt` | Đổi chiều bài canh 18/09 + hai bài mới |
| `core/src/main/kotlin/com/byd/clusternav/launcher/LogLineThrottle.kt` | **mới** — tiết chế dòng log theo nội dung (thuần) |
| `core/src/test/kotlin/com/byd/clusternav/launcher/LogLineThrottleTest.kt` | **mới** — 7 bài, gồm ca 1 Hz thật của thư viện HAL |
| `app/src/main/java/com/byd/clusternav/launcher/KachiLog.kt` | `throttled()` + nối vào vòng ghi thẻ |
| `app/src/test/java/com/byd/clusternav/launcher/KachiLogThrottleTest.kt` | **mới** — 4 bài (W/E không bị bỏ · khoá bỏ dấu thời gian · giá trị đổi qua ngay · dòng hỏng định dạng) |
| `app/src/main/java/com/byd/clusternav/VmOverlayPosition.kt` | Cổng **log** cho lượt bắn lặp (lượt bắn không đổi) |
| `app/src/test/java/com/byd/clusternav/VmOverlayPosLogGateTest.kt` | **mới** — 2 bài (log qua cổng · lượt bắn KHÔNG qua cổng) |

---

## 6. Việc để lại (không thuộc quyền sửa của lượt này)

| Việc | Vì sao để lại | Ai/cái gì mở khoá |
|---|---|---|
| KDoc `VoiceSession` (≈dòng 194 · 199-200) còn nói *"chạm ra ngoài / **Back**"* là đường thoát | Tệp `app/…/voice/VoiceSession.kt` đang do lượt VOICE sửa song song — CLAUDE.md §2 *"một chủ cho một tệp tại một thời điểm"* | một câu đính chính trong lượt VOICE |
| `docs/specs/kachi-voice-command.html` dòng 358 (+ dòng 559, nhật ký `[ĐO]` cũ) còn ghi tấm chữ hiện chữ *"Chạm ra ngoài hoặc **bấm Back** để huỷ"* | Dòng 559 là **nhật ký đo cũ** (không sửa lịch sử); dòng 358 là mô tả hành vi ⇒ cần một dòng ở §Requirements nói Back **không còn** là đường thoát. Chuỗi hint ấy thật ra đã bị owner bỏ khỏi giao diện từ 24/09 (`grep` `strings_kachi.xml` ⇒ 0 dòng) | chủ spec voice |
| `SHELL-19`: giãn cổng watchdog giữ-cụm 4 s → 8 s (19 → 11,5 lệnh/phút, đổi lấy độ trễ phục hồi 8 → 16 s) | Đổi hành vi thấy được trên đường đã chạy tốt ngoài hiện trường; K7 = DONE-chấp nhận | **owner** (§3.3) |
| Ghi ba mục này vào `docs/PROJECT-BACKLOG.md` + `docs/README.md` (R2.1) | Hai tệp ấy do lượt khác giữ trong phiên này | lượt tổng hợp cuối phiên |
