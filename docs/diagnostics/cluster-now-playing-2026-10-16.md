# Tên bài hát lên cụm (Seal, fw 2606) — vì sao YouTube / YT Music không bao giờ hiện

> **Loại:** RE + điều tra off-car (KHÔNG sửa code) · **Ngày:** 2026-10-16 · **Câu hỏi owner:** *"khi nhạc phát, có
> bắn được tên bài hát chạy trên cụm không nhỉ, có đường đó mà lâu nay ko thấy tên bài hát hiện, cả youtube và yt
> music đều không thấy"*.
> **Phạm vi bằng chứng:** đọc source dịch ngược fw 2606 (`../firmware/fw-2606/`), disasm `libBydDataSource.so`, giải
> nén QML cụm (`cluster_theme1/2.rcc`), logcat xe cũ 26/09. **Chưa có phép đo mới trên xe** — mọi kết luận về hành vi
> TRÊN XE hôm nay tối đa là [SUY]; §5 liệt kê lệnh đo để chốt (§14 tầng 1, chỉ đọc trước).
> Mức bằng chứng: **[ĐO]** = đọc source/disasm/dump thật · **[SUY]** = suy luận khớp, chưa đo trực tiếp ·
> **[ĐOÁN]** = mới hợp lý · **[CHƯA BIẾT]** = chưa có dữ liệu.

---

## 0. TL;DR

- **Đường đó CÓ, và nó đã chạy generic cho mọi app có MediaSession** — không phải whitelist [ĐO source]. App hệ thống
  `com.byd.mediacontroller` (uid system) bám phiên của app đang giữ audio focus, rồi ghi thẳng tên bài vào
  `INSTRUMENT_MUSIC_INFO_SET` (0x43FB1008).
- **Chỗ chặn nằm ở CỤM, theo "nguồn nhạc"** [ĐO disasm + QML]: MediaController gán YouTube / YT Music (và mọi app
  ngoài bảng của BYD) là nguồn **26**. Thư viện vẽ cụm `libBydDataSource.so` chỉ hiện ô nhạc khi nguồn ∈
  {1…19 trừ 8, 22, 29, 30, 171}. **26 không nằm trong đó**, nên ô bị ẩn, và theme cũng không có icon `mediaIcon29`.
  ⇒ Tên bài *có thể* đã được gửi, nhưng cụm vứt đi. Chuyện YouTube/YTM có thật sự đi hết đường này trên xe hay
  không: [SUY], chốt bằng M2 ở §5.
- **Bẫy:** gọi SDK công khai `BYDAutoInstrumentDevice.sendMusicName()` / `sendMusicInfo()` / `sendMusicSource()` trên
  fw 2606 là **no-op trả 0**, vì `MediaStateDelegate.isFocusOwner()` bị cứng `return false` [ĐO dex]. Muốn ghi thật thì
  phải ghi raw `set(int[], BYDAutoEventValue)`, như cách MediaController làm.
- **Kachi hiện KHÔNG có đường nào đẩy nhạc lên cụm** [ĐO grep]. Kachi đã đọc được tên bài (MediaBridge), và đã có
  sẵn đường ghi HAL instrument qua bypass-context (đường nav dùng).
- **Hướng sửa generic (đề xuất, chưa làm):** ghi đè **một số nguyên** `INSTRUMENT_MUSIC_SOURCE_SET` (0x33F00030) =
  **11** (`MUSIC_SOURCE_OTHERS` của chính SDK BYD, có icon `mediaIcon14`), rồi gửi lại tên bài. Làm việc này chỉ khi
  phép đo cho thấy nguồn hiện tại bị cụm loại. Trước đó phải qua M0–M5 (chỉ đọc), rồi W1 (một lần ghi thử, có đường
  hoàn tác).

---

## 1. Chuỗi cơ chế (file:line)

Đường dẫn nguồn tương đối repo. Nguồn Java là output `jadx` 1.5.6 của APK/JAR trong `../firmware/fw-2606/`
(dựng lại: `jadx -d <out> <apk>` · `jadx --single-class <lớp> -d <out> framework.jar`). Số dòng là dòng của file
dịch ngược. Riêng dex dùng `dexdump -d` (build-tools 36), số dòng lấy từ bảng debug `line=`.

```
App nhạc (YouTube/YTM) ─ MediaSession + AudioFocus
        │  (1) MediaSessionManager.getActiveSessions + BYD AudioManager.getCurrentAudioFocusPackage()
        ▼
com.byd.mediacontroller (system uid)  MediaTaskManager → DefaultMediaControllerCallback → OtherClient
        │  (2) source = 26 cho app lạ ; sendMusicName / sendMusicSource / sendMusicState
        ▼
BYDAutoInstrumentDevice.set(int[]{id}, BYDAutoEventValue)   (raw, KHÔNG qua MediaStateDelegate)
        │  (3) khung CAN: 0x43FB1008 tên bài (UTF-16LE ≤255 B) · 0x43E0000A trạng thái · 0x33F00030 nguồn
        ▼
libBydDataSource.so  BusinessUi1::updateMediaInfoDisplay   (4) CỔNG theo nguồn ⇒ mediaInfoNeedDisplay / IconId / String
        ▼
QML cụm (theme1 / theme2)  cluster.qml createMediaInfo → alwaysDisplay/MediaInfo.qml   (5) vẽ icon + chữ chạy
```

### (1) Ai được chọn: phiên của app đang giữ audio focus. Không có whitelist.

| Sự thật | Nguồn | Mức |
|---|---|---|
| `com.byd.mediacontroller` 1.5.0.0 (versionCode 10500000) có trong `/system/app`, `sharedUserId=android.uid.system` | `../firmware/fw-2606/meta/packages.txt:90` · manifest APK | [ĐO] |
| Tiến trình này CHẠY trên xe và in log `D/MediaController: MediaTaskManager: …` (26/09: phiên `com.android.server.telecom`, `com.byd.mediacenter`) | `docs/diagnostics/perf-oncar-2026-09-26/logcat-stream-2.70.txt:104327-107047` | [ĐO cũ — chỉ định hướng, §14] |
| Gói focus lấy từ `AudioManager.getCurrentAudioFocusPackage()` (API BYD thêm), kèm broadcast đổi focus `EXTRA_PACKAGE` | `MediaController/…/ability/impl/MediaFocusAbilityImpl.java:29-40,61-63` | [ĐO] |
| Chọn controller có `getPackageName()` == gói focus. Chỉ lọc `android`, `telecom`, DAB, `t4clauncher`, `com.byd.youtube`/`tver`/`unext`. **YouTube Google không bị lọc** | `MediaController/…/local/MediaTaskManager.java:46-54,126-128,147-266` | [ĐO] |
| Mọi gói khác `com.byd.mediacenter` đều đi `OtherClient` | `MediaController/…/local/DefaultMediaControllerCallback.java:84-105,128-161` | [ĐO] |

### (2) `OtherClient` gửi tên + nguồn cho MỌI app lạ. App lạ nhận nguồn 26.

- `onMetadataChanged` → `sendMusicName(getTitle(md))` · `sendSingerName` · `sendMusicSource(getInstrumentSource())`
  — `MediaController/…/local/OtherClient.java:97-133` [ĐO].
- `getInstrumentSource()`: AA→17, CarPlay→16, Spotify/vivid→18, car.media/Amazon→19, FLO→22, TuneIn→29,
  Stingray→30, **còn lại → 26** — `OtherClient.java:168-188` [ĐO]. YouTube (`com.google.android.youtube`) và YT Music
  (`com.google.android.apps.youtube.music`) không có trong bảng ⇒ **26** [ĐO source].
- `MediaInfoSender` ghi raw: `mInstrumentDevice.set(new int[]{INSTRUMENT_MUSIC_INFO_SET}, ev)` với
  `ev.bufferDataValue = UTF-16LE(title)` cắt 255 byte — `MediaController/…/local/MediaInfoSender.java:186-198`; nguồn
  `:135-146`; trạng thái `:155-170`; mã hoá `…/utils/UnicodeUtils.java:15-23` [ĐO].
- **Khử trùng lặp:** `sendMusicSource` bỏ qua nếu `mInstrumentSource == i` (`MediaInfoSender.java:136`),
  `sendMusicName` bỏ qua nếu trùng tên cũ (`:187`). ⇒ Khi MediaController đã ghi 26 thì nó **không ghi lại** cho tới
  khi nguồn tính ra đổi giá trị [ĐO]. Chi tiết này quyết định tính khả thi của phương án A (§4).
- Cờ tắt toàn cục `sys.byd.mediacenter.forbidsend` (mặc định false) — `MediaInfoSender.java:95`. Không có trong
  `../firmware/fw-2606/meta/getprop.txt` ⇒ false [ĐO dump; giá trị trên xe hôm nay: M0].

### (3) Feature-id trên xe

| Tên | id thập phân | hex | Nguồn |
|---|---|---|---|
| `INSTRUMENT_MUSIC_INFO_SET` (tên bài, buffer) | 1140527112 | 0x43FB1008 | featmap xe 16/09 `docs/diagnostics/oncar-trace-2026-09-16b/featmap-20260916.json` [ĐO cũ] · DiCarServer `../firmware/fw-2602-diff/jadx-l3-new/sources/com/byd/feature/instrument/Instrument.java:602` |
| `INSTRUMENT_MUSIC_STATE_SET` (1 phát · 2 dừng · 3 stop) | 1138753546 | 0x43E0000A | như trên `:607` |
| `INSTRUMENT_MUSIC_SOURCE_SET` (nguồn) | 871366704 | 0x33F00030 | như trên `:605` |
| `INSTRUMENT_MUSIC_PLAYBACK_PROGRESS_SET` | 1138753552 | 0x43E00010 | featmap 16/09 |
| `INSTRUMENT_MUSIC_INFO_RESULT` (đọc) | 1108021264 | 0x420B1010 | featmap 16/09; `getMusicInfoResult()` đọc id này (jadx framework 2606 `BYDAutoInstrumentDevice.java:1469-1474`) |
| `INSTRUMENT_MUSIC_SOURCE` (đọc) | 970981412 | 0x39E00024 | featmap 16/09. Ý nghĩa [CHƯA BIẾT] |

**SDK công khai bị rút ruột trên fw 2606** [ĐO dex]: `sendMusicName/Info/State/Source/PlaybackProgress` kiểm
`BYDAUTO_INSTRUMENT_SET` rồi đi `MediaStateDelegate`
(jadx framework 2606 `BYDAutoInstrumentDevice.java:1420-1467,1756-1772`). Ở đó
`isFocusOwner()` = `const/4 v0,0; return v0` (dexdump `framework.jar` classes.dex, `MediaStateDelegate.isFocusOwner`
`line=94`) ⇒ không ghi gì, **trả 0**. Một app gọi SDK này sẽ thấy rc=0 mà cụm không đổi: đúng kiểu "im lặng sai"
(G0-4). Bộ stub SDK ở `../jadx-openbyd/`, `../jadx-tmap/` có thêm `sendThreeLineLyrics`, nhưng framework 2606 không có
hàm này [ĐO grep jadx 2606].

### (4) CỔNG ở cụm — `libBydDataSource.so` (fw 2606) `BusinessUi1::updateMediaInfoDisplay(unsigned)` @0x1207d8

Disasm: `objdump -d --no-show-raw-insn ../firmware/fw-2606/_libs/lib64_libBydDataSource.so` (objdump Xcode đọc được
ELF aarch64, cùng cách `cluster-rect-adas-shrink-2026-10-06.md`).

| Địa chỉ | Việc | Mức |
|---|---|---|
| 0x1208b4–0x120948 | Đọc tín hiệu CAN: `0x43E00035` (phone) → +0x1171, `0x43E0000E` (radio) → +0x1173, **`0x43E0000A` (music state) → +0x1174**, **`0x33F00030` (nguồn) → +0x1170**, cùng `0x43E00034`, `0x12D0002A` | [ĐO] |
| 0x120958 | log `CLUSTER_LOG` (prio 4): `"updateMediaInfoDisplay m_can43ePhoneStatus=%d, …m_can33fMediaSource=%d"`. Chỉ in khi biến mức log toàn cục ≤ 1 | [ĐO]; mặc định mức log [CHƯA BIẾT] |
| 0x120d60 | nguồn == 8 (VIDEO) ⇒ ẩn | [ĐO] |
| 0x120d94 | music state ∉ {1,2} ⇒ ẩn | [ĐO] |
| 0x120da4 | `nguồn−1 < 19` ⇒ **cho hiện** (nguồn 1…19) | [ĐO] |
| 0x120db0–0x120dc8 | nguồn 20…30: chỉ cho khi bit `(1<<nguồn) & 0x60400000` ⇒ **chỉ 22, 29, 30**. **26 ⇒ ẩn** | [ĐO] |
| 0x120dcc | nguồn > 30: chỉ `0xAB` (171) | [ĐO] |
| 0x120dd4 | thêm cờ +0x1179 ≠ 0 ⇒ ẩn tạm. Cờ được bật ở nhánh phone (0x120aa8) và xoá bởi `updateRadioMusicStatus` @0x120fb4 (hẹn giờ 0x120b20) ⇒ nghi là "vừa có cuộc gọi" | [ĐO] cơ chế · ý nghĩa [SUY] |
| 0x120eb0–0x120f14 | nhánh HIỆN: `mediaInfoNeedDisplay`=1, `mediaInfoIconId = nguồn + 3` | [ĐO] |
| chuỗi `"ICON_MUSIC change, clear mediaInfoString!!!"` | icon đổi ⇒ xoá chuỗi tên ⇒ đổi nguồn xong phải **gửi lại tên** | [ĐO chuỗi] · vị trí gọi chưa lần [SUY] |

### (5) QML cụm (giải nén `cluster_theme1.rcc` / `cluster_theme2.rcc`, Qt qres v3, zlib)

- `qml/cluster.qml:523-535` (theme1) · `qml10_25/cluster.qml:484-496` (theme2): chỉ dựng `MediaInfo.qml` khi
  `DataSource.mediaInfoNeedDisplay === UI_DISPLAY && DataSource.powerGear === 3` (xe ON) [ĐO].
- `alwaysDisplay/MediaInfo.qml:28-34`: icon `qrc:/images/MediaInfo/{day,night}/mediaIcon<IconId>.png`, chữ
  `mediaString = DataSource.mediaInfoString` (`:164`). Ô đặt ở x=780 y=148 rộng 386 [ĐO].
- Icon có trong cả hai theme: `mediaIcon` 4,5,6,7,8,9,10,12,13,14,15,16,17,19,20,21,22,25,32,33,174 [ĐO liệt kê
  rcc]. Đúng bằng (nguồn + 3) của tập được phép ở (4). **Không có 29 (= 26 + 3)** ⇒ hai bằng chứng độc lập cùng khớp.
- Khi bị ẩn, cả **icon lẫn tên đều không hiện** (component bị `destroy()`). Nên triệu chứng "không thấy gì" khớp với
  cổng (4), không khớp với "thiếu font" hay "chuỗi rỗng" [SUY].

---

## 2. Vì sao YouTube và YT Music không hiện

| # | Giả thuyết | Mức | Ghi chú |
|---|---|---|---|
| H1 | MediaController gán nguồn **26** ⇒ cụm ẩn ô nhạc | Cơ chế **[ĐO source + disasm + QML]** · việc nó xảy ra trên xe hôm nay **[SUY]** | Chốt bằng M2 + M4. Đây là nghi phạm số 1 |
| H2 | MediaController không chọn được phiên YouTube (focus ≠ gói YouTube, hoặc phiên không nằm trong `getActiveSessions`) | [ĐOÁN] | Log 26/09 không có dòng nào về YouTube, nhưng lúc đó YouTube không phát. Chốt bằng M2/M3 |
| H3 | Ô nhạc bị màn chiếu của Kachi trên cụm đè lên (cast / ô đen / Chữ nhật full 1920×720) | [CHƯA BIẾT] | QML vẽ ô ở (780,148). Lớp nào nằm trên lớp nào chưa đo. Chốt bằng M1/M6 lúc tắt chiếu |
| H4 | Cờ ẩn tạm +0x1179 (sau cuộc gọi) | [SUY] chỉ tạm thời | Không giải thích được việc "lâu nay không bao giờ thấy" |
| H5 | `forbidsend` = true trên xe | [ĐOÁN] khả năng thấp | Dump 2606 không có prop này. Chốt bằng M0 |

Đối chứng nên làm: nguồn BT (6), USB/local (1), CarPlay (16), AA (17) đều nằm trong tập được phép. Nếu phát nhạc
Bluetooth từ điện thoại mà cụm **hiện** tên bài ⇒ widget sống, H1 gần như chắc chắn. Nếu **cũng không hiện** ⇒
nguyên nhân là H3 hoặc một cổng khác chưa thấy, chứ không phải nguồn (M1).

---

## 3. Kachi hiện có gì

| Sự thật | Nguồn | Mức |
|---|---|---|
| Kachi KHÔNG có đường nào ghi `INSTRUMENT_MUSIC_*`, `sendMusic*`, 0x43FB1008, 0x33F00030 | grep `app/src/main`, `core/src/main`, `car-integration/src/main` | [ĐO] |
| Kachi đọc tên / nghệ sĩ / ảnh bìa / vị trí của phiên đang phát qua `getActiveSessions(NavNotificationListener)` | `app/src/main/java/com/byd/clusternav/launcher/MediaBridge.kt:52-95,145-160,229-242` (widget `w_media`, YouTube phát tiếp, `TripMusic*`) | [ĐO code] · tên YouTube có đọc được trên xe: [SUY] (tài liệu trip cũ) |
| Kachi đã ghi HAL instrument (device 1007) qua bypass-context, cả int lẫn bytes | `app/src/main/java/com/byd/clusternav/modules/hal/BydHal.kt:55-78` (bypass) · `:111-120` `setInt`/`setBytes` · `:262-275` `cachedSetInt`/`cachedSetBytes` · `:300-380` `writeNavFrame` | [ĐO code] · rc thật của 0x33F/0x43FB trên Seal [CHƯA BIẾT] |
| Cầu kiểm thử có `hal getid` (chỉ đọc) và `hal setev` (ghi int, cổng `auto_confirm`) | `app/src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeHal.kt:47-87` | [ĐO code] |

Không app nào trong bộ RE (`../jadx-dashcast`, `../jadx-openbyd*`, `../jadx-kim`, `../jadx-kiki`) ghi nhạc lên cụm
[ĐO grep]. Không có tiền lệ bên thứ ba.

---

## 4. Phương án cho Kachi

### A. Sửa nguồn (đề xuất): ghi `INSTRUMENT_MUSIC_SOURCE_SET` = 11, rồi gửi lại tên bài

- **Ý tưởng:** không làm lại việc MediaController đã làm, chỉ đổi đúng con số khiến cụm vứt tên bài. Nguồn 11 là
  `MUSIC_SOURCE_OTHERS` trong chính SDK BYD (`../jadx-openbyd/sources/android/hardware/bydauto/instrument/BYDAutoInstrumentDevice.java:553`),
  nằm trong tập được phép và có icon `mediaIcon14`. Icon đó trông thế nào: chưa xem ảnh [CHƯA BIẾT].
  **Không dùng 18** (icon Spotify — sai sự thật với user).
- **Trình tự khi phiên đang phát đổi metadata / nguồn:** (1) ghi source=11 → (2) ghi tên `UTF-16LE ≤255 B`
  (0x43FB1008, vì icon đổi thì chuỗi bị xoá) → (3) ghi state 1/2 (0x43E0000A). Ghi raw qua
  `BydHal.cachedSetInt/cachedSetBytes`, **không** qua SDK `sendMusicName` (no-op, §1(3)). Chỉ ghi khi giá trị đổi.
- **Vì sao không bị giằng co:** nhờ khử trùng lặp ở `MediaInfoSender.java:136,187`, MediaController không ghi lại 26
  khi bài đổi (vẫn tính ra 26 = giá trị nó nhớ). Nó chỉ ghi lại khi gói focus đổi sang một nguồn khác ⇒ lúc đó Kachi
  ghi lại sau một nhịp ngắn. Đây là [SUY] từ code; W1 sẽ đo thật.
- **Generic, không hardcode gói (§7):** cụm chấp nhận nguồn nào là dữ liệu của firmware cụm, nên để trong
  `ClusterProfile` (tập {1…19 trừ 8, 22, 29, 30, 171} cho fw 2606 theme1/2). Chưa biết đời xe khác dùng tập nào.
  Kachi KHÔNG chép bảng gói → nguồn của BYD. Quyết định "cần sửa hay không" nên đi từ phép đo:
  - nếu M4 cho thấy `INSTRUMENT_MUSIC_SOURCE` (0x39E00024) hoặc `INSTRUMENT_MUSIC_INFO_RESULT` (0x420B1010) đọc ngược
    được nguồn / kết quả cụm ⇒ chỉ ghi đè khi đọc ngược ra giá trị ngoài tập được phép;
  - nếu không đọc ngược được ⇒ chỉ ghi đè cho phiên **không phải của gói hệ thống BYD** (phân biệt bằng cờ
    `ApplicationInfo.FLAG_SYSTEM`, không bằng tên gói), và chỉ khi phiên đó đang PLAYING.
- **Chi phí (luật perf):** khoảng 3 binder `set` mỗi lần đổi bài. Không thêm luồng, không thêm nhịp lặp. Bám
  `MediaBridge.watchPackages` sẵn có ⇒ không thêm listener phiên thứ hai.
- **An toàn theo CLAUDE.md §4:**
  1. *Display nào?* Không đụng display/WM. Đây là ghi tín hiệu HAL device 1007, không có `am`/`wm`.
  2. *App nào?* Không đụng task/stack của app nào. Chỉ đọc phiên của app đang giữ focus.
  3. *Loại stack nào?* Không áp dụng (không lệnh stack).
  4. *Hoàn tác?* Ghi lại source=26 (đúng giá trị MediaController đang nhớ), hoặc state=3. Khi hết phiên / tắt tính
     năng ⇒ ghi state=3 (MediaController tự làm vậy khi STOPPED, `OtherClient.java:149-154`). Tín hiệu CAN sống trong
     RAM của bus/cụm: nghi là không bền qua tắt máy [SUY, chưa đo] ⇒ không cần marker prefs như §5. Nếu đo ra là bền
     thì phải thêm marker.
- **Rủi ro:**
  - (r1) Nhãn sai lệch: icon "Khác" thay vì logo YouTube — chấp nhận được.
  - (r2) Giằng co với MediaController lúc đổi focus ⇒ icon chớp. Thấy chớp thì dừng, báo.
  - (r3) 0x33F00030 có thể còn được ECU/HUD khác đọc [CHƯA BIẾT]. Thấy hành vi lạ thì hoàn tác ngay.
  - (r4) ECU từ chối khung như vụ ISA 0x4320002A ⇒ W1 cho đáp án.
  - (r5) Kachi bị BYD giết lúc tắt máy (memory 29/09), nên lượt khởi động sau phải tự ghi lại.
- **Thứ tự triển khai theo §14:** tầng 1 (M0–M5, rồi W1 trên xe) → tầng 2 `car-integration` (khoá chuỗi
  id/bytes đã thấy) → tầng 3 `core` (chính sách: tập nguồn trong `ClusterProfile`, khi nào ghi) → tầng 4 công tắc UI.
  **Chưa được viết code trước khi W1 xanh.**

### B. Kachi tự vẽ tên bài lên bề mặt Kachi đang có trên cụm

- Chỉ có tác dụng khi Kachi đang chiếu / giữ ô trên cụm. Thêm một lớp UI trên display cụm ⇒ ngược luật
  "sửa từ gốc, không thêm lớp" (memory 09/10). Có rủi ro chồng lên ô nhạc OEM khi A chạy. **Không đề xuất**, trừ khi
  W1 cho thấy khung 0x33F bị chặn.

### C. Sửa bảng nguồn của MediaController / gói hệ thống

Không làm được: app uid system, ký khoá nền tảng. Không xét.

### D. Gọi SDK `sendMusicName` / `sendMusicInfo`

Vô dụng trên fw 2606: trả 0, không ghi (§1(3)). **Ghi vào bảng bẫy**, tránh lần sau có người "thử nhanh" rồi kết luận sai.

---

## 5. Đo trên xe để chốt từng [CHƯA BIẾT]

`<A>` = adb qua cầu nc (`docs/diagnostics/adb-car-tunnel-macos.md`). Cầu kiểm thử Kachi phải bật (công tắc 60 phút).
Xe **ON** (QML cần `powerGear==3`). Dừng ở lần bất thường đầu tiên (memory "thấy sập lần đầu là dừng").

**Tầng 1 — CHỈ ĐỌC**

- **M0 — danh tính & cờ**
  ```
  <A> shell "dumpsys package com.byd.launcher | grep versionName; getprop ro.build.display.id; getprop persist.sys.car.type"
  <A> shell "dumpsys package com.byd.mediacontroller | grep -E 'versionName|enabled='; getprop sys.byd.mediacenter.forbidsend"
  ```
  Kỳ vọng: mediacontroller 1.5.0.0, forbidsend rỗng/false (loại H5).
- **M1 — đối chứng widget còn sống.** Phát nhạc **Bluetooth** từ điện thoại (nguồn 6), Kachi **tắt chiếu cụm**. Nhìn
  cụm, hoặc chụp `<A> shell "fission_screencap -d 0 …"` như `cluster-rect-adas-shrink-2026-10-06.md`. Hiện tên ⇒
  widget sống. Không hiện ⇒ nghi H3 / cổng khác, **chưa** đi W1.
- **M2 — MediaController có bắt YouTube không.** Phát YT Music (rồi YouTube), chờ đổi bài một lần:
  ```
  <A> shell "logcat -d -v time | grep -E 'MediaController|OtherClient' | grep -E 'focus package|onMediaControllerChange|sendMusic(Name|Source|State) result|onMetadataChanged' | tail -40"
  ```
  Kỳ vọng nếu H1 đúng: `registerSessionCallbacks: current focus package name: com.google.android.apps.youtube.music`,
  `onMediaControllerChange: … com.google.android.apps.youtube.music`, `sendMusicName result: 0`. Không có các dòng này
  ⇒ H2 (đường chưa hề chạy).
- **M3 — phiên & focus thật**
  ```
  <A> shell "dumpsys media_session | grep -E 'package=|state=PlaybackState|description=' | head -30"
  <A> shell "dumpsys audio | sed -n '/Audio Focus stack/,/^$/p'"
  ```
- **M4 — đọc ngược (cầu kiểm thử, chỉ đọc).** Đọc lúc YTM đang phát, rồi lúc phát Bluetooth:
  ```
  <A> shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd hal --es op getid --es dev BYDAutoInstrumentDevice --es m 970981412"
  <A> shell "am broadcast … --es m 1108021264"
  ```
  So hai lúc với nhau: nếu `0x39E00024` ra 26 lúc YTM và 6 lúc BT ⇒ đó là đọc ngược nguồn, phương án A dùng làm phép
  đo. Nếu ra sentinel hoặc hằng số ⇒ không dùng được.
- **M5 — log cụm** (có cổng mức log, có thể im):
  ```
  <A> shell "logcat -d | grep CLUSTER_LOG | grep -E 'updateMediaInfoDisplay|Music Status|ICON_MUSIC' | tail -20"
  ```
  Có dòng `m_can33fMediaSource=26` + `hide Music Status` ⇒ H1 được **[ĐO]** trực tiếp.
- **M6 — H3.** Lặp M1 khi Kachi **đang chiếu cụm**. BT hiện lúc tắt chiếu mà mất lúc chiếu ⇒ có thêm lỗi chồng lớp,
  tách thành việc riêng.

**Tầng 1b — MỘT lượt ghi thử (cần owner gật; chỉ làm khi M1 + M2 xanh)**

- **W1 — đổi nguồn tay.** YTM đang phát, cụm không có ô nhạc:
  ```
  <A> shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd hal --es op setev --es dev BYDAutoInstrumentDevice --es m 871366704 --es args 11 --ez auto_confirm true"
  ```
  Rồi bấm **bài kế** trên vô-lăng. MediaController sẽ gửi tên mới (không ghi lại nguồn vì vẫn tính ra 26 = giá trị
  nó nhớ). Kỳ vọng: ô nhạc hiện tên bài + icon 14. Ghi lại rc và chụp cụm.
  - **Hoàn tác:** `… --es m 871366704 --es args 26 --ez auto_confirm true` ⇒ trả đúng giá trị MediaController đang
    nhớ. Hoặc chỉ cần dừng phát.
  - **§4:** không display, không app, không stack. Một feature int trên device 1007. Có đường trả về.
  - **Không đọc được ý nghĩa nếu:** rc ≠ 0 / sentinel ⇒ ECU hoặc HAL chặn ⇒ phương án A chết, ghi lại rc.

---

## 6. Cần làm tiếp (doc này KHÔNG sửa file nào khác)

- Thêm dòng vào `docs/README.md` (INDEX) và `docs/PROJECT-BACKLOG.md`, vd `CLUSTER-NOW-PLAYING`, trạng thái 🚗 chờ
  M0–M5/W1. Thêm bẫy "SDK `sendMusic*` = no-op trả 0 trên fw 2606" vào `.kiro/steering/project-context.md`. Lượt này
  chưa làm vì phạm vi chỉ cho phép tạo một file.
- Lượt RE sau (off-car): lần chỗ gọi `"ICON_MUSIC change, clear mediaInfoString"`, và kiểm tập nguồn của
  `libBydDataSource.so` 2602/2506 (đời firmware khác) trước khi đưa tập vào `ClusterProfile`.
