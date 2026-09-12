# Widget Android bên thứ ba — đường ràng buộc & hai lỗi render (T4)

> **Đính chính 2026-09-12 (sau lượt soát độc lập)**: dấu ngăn giữa id và nhà cung cấp đã đổi `|` → `@` vì `|` **trùng dấu ngăn TRƯỜNG** của chuỗi cảnh ⇒ cảnh chứa widget bên thứ ba bị bỏ IM LẶNG lúc đọc (lỗi P0). Số đo trong tài liệu này thu TRƯỚC bản vá đó; đường bind và cách khai cỡ không đổi.

> **Trạng thái**: Current · **Ngày**: 2026-09-12 · **Máy**: `emulator-5554` (Android 10, google_apis, 1920×1080@240dpi)
> **Mục đích**: ghi lại **bằng chứng lệnh** cho câu hỏi *"đặt được widget của app khác vào ô giữa màn không?"* (P6 · T4),
> và hai lỗi render chỉ lộ ra khi **đo pixel**. Spec: `docs/specs/kachi-scenes-and-widgets.html` §3.2 · OQ2.

## 1. Kết luận

**Đường (A) — ràng buộc được bằng quyền, làm đầy đủ.** Nhưng KHÔNG phải bằng quyền `BIND_APPWIDGET` như spec dự đoán:
nền tảng có **đường thứ hai** không đi qua hệ thống quyền — một *bind-grant* riêng của `AppWidgetService`, cấp bằng shell.
Đây **chính là đường dự án đã dùng và đã proven trên xe** cho badge tốc-độ VietMap
(`LocalDeviceShell.grantAppWidgetBind`, bản 1.13 — xem `docs/specs/vietmap-widget-bridge.html`).

⇒ **Không cần hộp thoại hệ thống** `ACTION_APPWIDGET_BIND`, nên không vướng tiền lệ IVI khoá
(*"Hệ thống IVI không hỗ trợ hoạt động này"*, P8).

## 2. Bốn phép đo — nguyên văn

### (1) Quyền `BIND_APPWIDGET` có tồn tại, mức gì

```
$ adb -s emulator-5554 shell pm list permissions -g -d | grep -i appwidget
(rỗng — exit 1)
```
`-d` chỉ liệt kê quyền **dangerous**; `BIND_APPWIDGET` không phải loại đó nên không hiện. Đọc bằng `-f`:

```
$ adb -s emulator-5554 shell pm list permissions -f | grep -A5 -i BIND_APPWIDGET
+ permission:android.permission.BIND_APPWIDGET
  package:android
  label:null
  description:null
  protectionLevel:signature|privileged
```
⇒ **đúng như spec**: `signature|privileged`.

### (2) Launcher có quyền đó không

```
$ adb -s emulator-5554 shell dumpsys package com.byd.launcher | grep -A100 "requested permissions"
    requested permissions:
      …
      android.permission.BIND_APPWIDGET      ← CÓ XIN
      …
    install permissions:
      android.permission.SYSTEM_ALERT_WINDOW: granted=true
      android.permission.FOREGROUND_SERVICE: granted=true
      android.permission.RECEIVE_BOOT_COMPLETED: granted=true
      android.permission.INTERNET: granted=true
                                            ← KHÔNG có BIND_APPWIDGET
```
⇒ manifest **đã khai** (kế thừa từ ClusterNav) nhưng **chưa được cấp**.

### (3) `pm grant` — ĐÓNG

```
$ adb -s emulator-5554 shell pm grant com.byd.launcher android.permission.BIND_APPWIDGET
Security exception: Permission android.permission.BIND_APPWIDGET requested by com.byd.launcher
is not a changeable permission type

java.lang.SecurityException: Permission android.permission.BIND_APPWIDGET requested by
com.byd.launcher is not a changeable permission type
	at com.android.server.pm.permission.BasePermission.enforceDeclaredUsedAndRuntimeOrDevelopment(BasePermission.java:429)
	at com.android.server.pm.permission.PermissionManagerService.grantRuntimePermission(PermissionManagerService.java:2096)
	…
exit status: 255
```
⇒ `pm grant` chỉ cấp được quyền **runtime**. Đường này **không mở**, đúng như spec cảnh báo.

### (4) `appwidget grantbind` — MỞ

```
$ adb -s emulator-5554 shell appwidget grantbind --package com.byd.launcher --user 0
(không in gì — exit 0)

$ adb -s emulator-5554 shell cmd appwidget grantbind --package com.byd.launcher --user 0
No shell command implementation.          ← biến thể `cmd` KHÔNG có trên ROM này

$ adb -s emulator-5554 shell dumpsys appwidget | sed -n '/^Grants:/,$p'
Grants:
  [0] user=0 package=com.byd.launcher     ← TRƯỚC đó mục này RỖNG
```

⚠ Ghi lại: **`cmd appwidget` không tồn tại** trên ROM này, chỉ có binary rời `appwidget`.
`LocalDeviceShell.grantAppWidgetBind` đã thử **cả hai** dạng nên nó vẫn đúng — nhưng đừng chỉ dùng dạng `cmd`.

### (bổ sung) Máy có nhà cung cấp widget nào

```
$ adb -s emulator-5554 shell dumpsys appwidget | head -60
Providers:
  [0]  com.android.chrome/…BookmarkThumbnailWidgetProvider
  [1]  com.android.chrome/…SearchWidgetProvider
  [2]  com.google.android.apps.docs/…CakemixAppWidgetProvider
  [3]  com.google.android.calendar/…CalendarAppWidgetProvider
  [4]  com.google.android.calendar/…MonthViewWidgetProvider
  [5]  com.google.android.deskclock/…AnalogAppWidgetProvider
  [6]  com.google.android.deskclock/…DigitalAppWidgetProvider
  [7]  com.google.android.gm/…GmailWidgetProvider
  [8..11] com.google.android.googlequicksearchbox/… (4 cái)
  [12,13] com.google.android.music/… (2 cái)
  [14] com.google.android.apps.youtube.music/…MusicWidgetProvider
  [15..18] vn.vietmap.live/… (4 cái, updatePeriodMillis=100)
⇒ 19 nhà cung cấp.
```

## 3. ⚠⚠ HostId phải KHÁC cầu VietMap — rủi ro có thật, đo được

```
$ adb -s emulator-5554 shell dumpsys appwidget | sed -n '/^Hosts:/,/^Grants:/p'
Hosts:
  [0] hostId=HostId{user:0, app:10149, hostId:22093, pkg:com.byd.clusternav2}   widgets.size=0
  [1] hostId=HostId{user:0, app:10145, hostId:22093, pkg:com.byd.launcher}      widgets.size=3  ← VietMap
  [2] hostId=HostId{user:0, app:10145, hostId:19265, pkg:com.byd.launcher}      widgets.size=1  ← T4
```

`22093` = `0x564D` (`VietMapWidgetBridge.HOST_ID`) đang giữ **3 widget thật** của badge tốc-độ. Nếu T4 dùng lại số đó
thì `AppWidgetHost.appWidgetIds` trả về **cả 3 id của VietMap**, và lượt dọn rác lúc khởi động
(`AppWidgetIds.unused`) sẽ **xoá chúng** vì bố cục launcher không dùng tới ⇒ **badge tốc-độ chết không ai hiểu vì sao**.
⇒ T4 dùng `0x4B41` (= 19265, "KA"). Khoá bằng `AppWidgetWiringContractTest.hostId khac hostId cua cau VietMap`.

## 4. ⚠⚠ HAI lỗi render — "mọi dấu hiệu nói chạy được" mà ô thì TRỐNG

Đây là phần đáng nhớ nhất của T4. Sau khi ràng buộc thành công, **ba nguồn bằng chứng độc lập đều dương**:

```
$ adb shell dumpsys appwidget | sed -n '/^Widgets:/,/^Hosts:/p'
  [3] id=650
    host=HostId{…hostId:19265, pkg:com.byd.launcher}
    provider=ProviderId{…com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider}
    views=android.widget.RemoteViews@9c57124        ← nhà cung cấp ĐÃ đẩy nội dung

cây view: AppWidgetHostView [654,180][1896,864] pkg=com.byd.launcher
            └ LinearLayout  pkg=com.google.android.deskclock     ← view của app KHÁC nằm trong ô
                └ TextView  text="12:47"                          ← GIỜ THẬT
                └ TextView  text="SAT, SEP 12"                    ← NGÀY THẬT

prefs: slot_2 = aw:650@com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider
```

**Nhưng ảnh chụp ô thì gần như trống**:

| Phép đo vùng ô 1242×684 | Chưa vá | Đã vá |
|---|---|---|
| màu nền chiếm | **99.84%** `(23,26,32)` | — |
| độ sáng cao nhất | **110** | **255** |
| điểm sáng > 220 | **0** | **3 276** |
| `TextView` giờ | **4×1 px** | **503×243 px** |
| `TextView` ngày | **8×1 px** | **194×33 px** |

### Lỗi 1 — host không khai cỡ cho nhà cung cấp
`AppWidgetHostView` **không tự nói cỡ**. Provider đo/chọn layout theo cỡ host khai qua `updateAppWidgetSize`; không khai
thì nó dùng 0 và cây RemoteViews **co về ~0** — hiện ra đúng như một ô chưa gán gì.
⇒ Vá bằng lớp `SlotHostView` khai cỡ trong `onSizeChanged` (KHÔNG ở `createView`: lúc dựng view chưa ai biết ô rộng bao
nhiêu, và bố cục tự vẽ P9 cho mỗi khung một cỡ).

### Lỗi 2 — `Bundle.EMPTY` không ghi được
Bản vá đầu truyền `Bundle.EMPTY`. `updateAppWidgetSize` **ghi** các khoá `OPTION_APPWIDGET_*` vào chính bundle được
truyền, mà `Bundle.EMPTY` là bundle **không đổi được**:

```
$ adb logcat -d | grep KachiAppWidget
W KachiAppWidget: khai cỡ widget lỗi: UnsupportedOperationException
```
⇒ cỡ không bao giờ tới nhà cung cấp ⇒ ô **vẫn trống y như chưa vá**. Lỗi này **đội lốt "bản vá không ăn"**, nên nó sẽ
đẩy người sửa đi tìm sai chỗ. Tìm ra được chỉ vì nhánh lỗi **có ghi log kèm tên ngoại lệ** — `runCatching` im lặng thì
mất dấu. Vá: truyền `Bundle()` mới.

### Bài học
`dumpsys` nói *"đã ràng buộc"*, cây view nói *"nội dung có đó"*, prefs nói *"đã lưu"* — **cả ba đều đúng** mà ô vẫn
trống. Chỉ phép đo **pixel** bác được. Đây là **lần thứ tư** của dự án: mã đúng + test xanh ≠ đã giao được
(trước: nút "Kính 50%" · 4 gói lệnh không ai chạm tới · ô nhóm Lốp render y widget cũ).

## 5. Nghiệm thu đã đo

| Việc | Bằng chứng |
|---|---|
| Bind qua đường người dùng | chạm ô → ngăn kéo → mục "Widgets from other apps" → "Digital clock" ⇒ `id=650` trên host `19265` |
| Widget vẽ **nội dung thật** | `TextView 503×243 px text="12:47"` + `194×33 px text="SAT, SEP 12"`; 3 276 điểm sáng > 220 |
| Lưu bền | `slot_2 = aw:650\|com.google.android.deskclock/…DigitalAppWidgetProvider` |
| Sống qua chết tiến trình | `force-stop` ⇒ PID **23597 → 23763**; mở lại: `id=650` còn nguyên, **3 816** điểm sáng > 220 |
| Thu hồi id khi thoát ô | thay bằng thẻ dựng tay ⇒ host `19265` `widgets.size` **1 → 0**; VietMap **vẫn 3** |
| Có đường đổi/bỏ | nút ⇄ ở đầu ô (tâm ≈ `1279,129`) mở lại ngăn kéo cho đúng ô đó |
| Mục chọn đúng chỗ | header ở `y=374–429`, **sau** Nhóm/Thẻ dựng tay/mục lẻ, **trước** "Applications"; lưới **4 cột** (app 6 cột) |

## 6. Còn tồn / cần đo trên xe 🚗

- **Chưa đo gì trên xe.** Bind-grant đi qua **kênh shell dadb**, thứ chỉ có trên xe (máy ảo dùng `adb` trực tiếp).
  Trên xe kênh đó là `launcherSeam()`; nếu `probe()` thất bại thì T4 nói *"chưa nối được kênh hệ thống"* và **không**
  để lại ô trống.
- **Chỉ đo 1 widget, 1 nhà cung cấp** (DeskClock digital). 19 nhà cung cấp còn lại chưa thử; widget có `collection`
  (Gmail, Calendar list) dùng `RemoteViewsService` — đường khác, chưa đo.
- **Chưa đo ca nhà cung cấp bị gỡ giữa lúc đang dùng** (thẻ `deadWidgetCard`). Chỉ khoá bằng test.
- **Chưa đo nhịp 1 giây của xe**: luật "ô widget bên thứ ba không dựng lại theo nhịp trạng thái xe" chỉ khoá được bằng
  test — off-car `statusChanged` luôn `false` nên **không quan sát được**, đúng cái bẫy đã làm phép đo `w_photos` sai.
- **OQ2 vẫn để owner quyết**: T4 chạy được off-car, nhưng nếu trên xe bind-grant bị từ chối thì mục chọn vẫn hiện mà
  mọi lần chạm đều trả lời *"xe không cho đặt widget của app khác"*. Lúc đó nên **ẩn hẳn mục** (đọc `Grants:` một lần)
  hay giữ nguyên câu nói thật? Cần một lượt đo trên xe trước khi quyết.
