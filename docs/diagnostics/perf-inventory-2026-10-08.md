# Rà hiệu năng 4 trạng thái + `display_settings.xml` phình — 2026-10-08 (2.98 R4 + R6, spec `kachi-298-plan.html` §3)

> Mốc so: `perf-inventory-2026-10-07.md` (2.96 R18). Máy ảo `kachi_play` (emulator-5556, Android 10), bản **2.97 (200) release**
> (`apk/Kachi-2.97-release.apk`) cho số đo hiệu năng; bản `vehicleTest` của cây 2.98-WIP cho R4 + `run-as`. Log xe đã kéo về
> (gitignored `logs/`) chỉ đếm tag/tên, không chép nội dung. Mức bằng chứng (CLAUDE.md §2): **[ĐO]** · **[SUY]** · **[CHƯA BIẾT]**.
> JSON: `docs/diagnostics/perf-r6-2026-10-08/`.

## 1. Kết luận

- **R4** — mỗi lần dựng màn ảo ô (mỗi lần nổ máy, vì BYD giết Kachi khi tắt máy) để lại **một mục vĩnh viễn ~124 B** trong
  `/data/system/display_settings.xml` vì tên màn ảo có dấu thời gian [ĐO]. Log xe 17/09→08/10: **≥ 347 tên khác nhau** (6–70/ngày)
  ⇒ tệp của xe đã ≥ ~43 KB và mỗi lệnh `wm` theo màn ghi lại cả tệp [SUY]. **Đã sửa**: tên ổn định `kachi-slot-<ô>` ⇒ 6 lần khởi
  động nguội liên tiếp **+0 B** (trước: +124 B mỗi lần) [ĐO].
- **R6** — chạy thường và standby của 2.97 **không xấu hơn** 2.96 R18 (thức 2,93/s vs 3,62/s khi màn bật; 0,57–0,62/s vs 0,68/s
  khi màn tắt) [ĐO]. Không thấy rò bộ nhớ/luồng qua 80 lần dựng lại bố cục [ĐO]. Lãng phí rõ nhất còn lại: dòng log `emit lane`
  ~75 dòng/phút khi dẫn đường (**đã sửa**) và alarm WAKEUP 60 s đánh thức máy để **không làm gì** (đề xuất, cần owner).

## 2. R4 — gốc từ AOSP

| Khẳng định | Nguồn (android10-release, raw.githubusercontent aosp-mirror) | Mức |
|---|---|---|
| uniqueId màn ảo = `"virtual:" + pkg + "," + uid + "," + name + "," + uniqueIndex` | `services/core/java/com/android/server/display/VirtualDisplayAdapter.java:91-96` | [ĐO source] |
| `uniqueIndex` = max index các màn ảo **đang sống** cùng tiền tố + 1 (0 nếu không có) | `VirtualDisplayAdapter.java:160-178` `getNextUniqueIndex` | [ĐO source] |
| Mục cài đặt theo display khoá bằng `displayInfo.uniqueId` (cấu hình mặc định) | `services/core/java/com/android/server/wm/DisplayWindowSettings.java:677-684` `getIdentifier` | [ĐO source] |
| `setUserRotation` / `setFixedToUserRotation` tạo mục + `writeSettings()` ghi **lại cả tệp** | `DisplayWindowSettings.java:208-214`, `:258-264`, `:566-574` | [ĐO source] |
| Mục chỉ bị gỡ khi trở về rỗng (`isEmpty`); **không có đường gỡ khi display bị huỷ** | `DisplayWindowSettings.java:566-569`, `:667-674` | [ĐO source] |
| Display mới cùng uniqueId được áp lại khoá xoay ngay lúc thêm | `DisplayWindowSettings.java:395-413` `applySettingsToDisplayLocked` | [ĐO source] · tác dụng trên xe [SUY] |
| DL5 (Android 12) cũng khoá theo uniqueId | `wm/DisplayWindowSettingsProvider.java:196-203` (android12-release) | [ĐO source] |

Vì sao có dấu thời gian: thêm từ 1.53 (`4a0709f`, H2 sở hữu màn ảo theo ô) — không có ghi chú lý do; `SlotVdLedger.adopt` coi
**cùng tên = cùng màn ảo** (chỉ đổi khoá, không giải phóng) ⇒ tên phải khác nhau giữa các màn **đang sống**, nhưng không cần khác
nhau qua thời gian [ĐO mã].

### Sửa (generic, không đổi lệnh nào — CLAUDE.md §6)

- `core/…/launcher/SlotVdName.kt` (mới): `pick(slot, live)` ⇒ `kachi-slot-<ô>`; trùng tên một màn Kachi đang sống (màn Kachi đời
  trước chưa nhả — H2·1; app đỗ ô 7 mang tên ô cũ) ⇒ `-g<n>` nhỏ nhất còn trống. Tập tên hữu hạn = số ô × số màn cùng ô sống đồng thời.
- `SlotVdOwner.liveNames()` (mới) · `VdAppHost` gọi `SlotVdName.pick(slot, SlotVdOwner.liveNames())`. Hai lệnh khoá xoay **giữ y
  nguyên, cùng thứ tự**.
- `TestBridgeState.VD_NAME_PREFIX` vẫn `kachi-slot-` (gương của `SlotVdName.PREFIX`); `grep -o 'kachi-slot-[0-9]*'` của script
  xe vẫn đếm đúng số ô.

### Đo trên máy ảo [ĐO]

| Bản | Lượt | `display_settings.xml` | uniqueId |
|---|---|---|---|
| 2.96 release (tên có dấu thời gian) | 5 lần `force-stop` + mở lại | 7 190 → 7 314 → 7 438 → 7 562 → 7 686 → 7 810 (**+124 B/lần**) | `…,kachi-slot-0-<ms>,0` mới mỗi lần |
| 2.98-WIP vehicleTest (tên ổn định) | 6 lần như trên | 7 920 ×6 (**+0**) — mục đầu tiên +110 B một lần | `virtual:com.byd.launcher,10154,kachi-slot-0,0` mọi lần |

Sau sửa, `dumpsys window displays`: màn ô `mUserRotationMode=USER_ROTATION_LOCKED mFixedToUserRotation=true`, YouTube resumed
trong ô (display 14) ⇒ khoá chống "YouTube co vào giữa" còn hiệu lực [ĐO].

### Mục cũ đã có (đường trả lại — §5): KHÔNG tự dọn

Tệp `0600 system:system` ⇒ shell (uid 2000) không đọc/sửa được [ĐO `cat: Permission denied`]. Phương án cho owner:
1. **Không làm gì** (đề xuất): sau sửa tệp ngừng phình; mục cũ chỉ tốn ~124 B mỗi mục lúc đọc khi boot và lúc ghi lại tệp.
2. Dọn bằng API công khai: tạo lại màn ảo đúng tên cũ (uniqueIndex 0) rồi `wm set-user-rotation free -d <id>` +
   `wm set-fix-to-user-rotation -d <id> default` ⇒ mục rỗng ⇒ hệ thống tự gỡ (`:566-569`). Cần biết tên cũ — chỉ lấy được từ
   `kachi-logs` (dòng `KachiVd tạo màn ảo …`), không đầy đủ; tốn 1 màn ảo + 2 lệnh shell/mục. Chi phí > lợi ích.
3. Sửa tệp trực tiếp: **cấm** (tệp hệ thống).

## 3. R6 — bảng 4 trạng thái (máy ảo, màn chính + YouTube trong ô, không chiếu cụm)

| Trạng thái | Chỉ số | 2.96 R18 (07/10) | 2.97 (08/10) | Ghi chú |
|---|---|---|---|---|
| Khởi động nguội | `am start -W` TotalTime | — (chưa đo trên máy ảo) | 289 · 270 · 290 · 275 · 305 · 385 ms | [ĐO] |
| | `KachiReady summary screen_on->tile` | — | 3 067 · 2 972 · 3 289 ms | ô có màn ảo; `proc->up` 284–352 ms [ĐO] |
| | `home adopt` (so với tiến trình) | — | 3 212 · 3 227 · 3 398 ms | [ĐO] |
| Chạy thường (600 s) | CPU % 1 lõi | 0,11 | 0,20 | |
| | PSS / RSS KB | 65 364 / 138 756 (tiến trình mới) | 56 487 / 106 276 | |
| | Luồng | 40 | 41 | |
| | Lần thức/giây | 3,62 | **2,93** | main 0,67 · DefaultDispatch 0,42 · RenderThread 0,40 · Okio_Watchdog 0,35 · kachi-window-sh 0,27 |
| | Lệnh shell/phút (`KachiPerf`) | — (xe: 19 → ~15) | 4,0 | máy ảo: chỉ `SlotLiveProbe`, không chiếu |
| | Dòng log của app | — | 58 / 10 phút | |
| Standby (màn tắt, 600 s) | CPU % | 0,00 | 0,01 | |
| | PSS / RSS KB | 59 947 / 120 824 | 51 951 / 95 912 | |
| | Luồng | 38 | 39 | |
| | Lần thức/giây | 0,68 | **0,57 · 0,62 · 0,50** (3 lượt) | `kachi-home-guard` 0,20 · main 0,22. Một lượt lẻ 5,32/s (`kachi-shortcut-icons` 3,13/s) không lặp lại được qua 3 lượt — gốc [CHƯA BIẾT] |
| | Alarm | 60 s WAKEUP | 60 s WAKEUP, **23 lần thức** / ~23 phút | mỗi lần chỉ log `watchdog alarm no-op: FGS keep-alive đang chạy` [ĐO] |
| | Dòng log của app | — | 33 / 10 phút (3/phút = alarm no-op) | |
| Lâu dài | `display_settings.xml` | +124 B / lần dựng ô | **+0** (sửa R4) | §2 |
| | 80 lần dựng lại bố cục trong tiến trình (ONE↔TWO_COL) | — | PSS 57,9 → 56,8 → 62,8 → 60,7 MB · Java heap 3,7–3,9 MB · luồng 41 cố định · màn ảo cùng display (đỗ/lấy lại ô 7) | không rò [ĐO] |
| | Prefs (máy ảo) | — | lớn nhất `kachi_workspace.xml` 8,3 KB; tổng 52 KB | máy ảo ít dữ liệu ⇒ chỉ đúng cho kiểm kê nguồn (§4) |
| | Tệp ngoài | — | `kachi-logs/` 1,8 MB · `test/` 50 tệp (trần 50) · `wallpapers/` 0,5 MB | |

Khác biệt PSS phụ thuộc tuổi tiến trình (xem 2.96 §5.1) — không so tuyệt đối.

## 4. Kiểm kê thứ phình theo thời gian (nguồn, kèm trần)

Đủ trần [ĐO mã]: `usage-*.log` 8 MiB/tệp + tổng 150 MiB (`DiagStorageCap`, chạy ở `KachiHomeActivity.onCreate` + NLS nối) ·
`inputd-*.log` giữ 5 · `diag-*.txt` giữ 20 · `test/*.json` giữ 50 · ảnh camera giữ 10 · nhật ký vòng `filesDir/diag/*.log` 200 dòng ·
`voice-log/` 30 lượt / 30 MiB · cache ảnh nền 2/nguồn, 128 khoá · sổ `behind_marks` 16 · tên dạy 4/app, 120/hồ sơ · app gần đây 12 ·
địa điểm 20 · lối tắt 256 · `LogLineThrottle` 512 khoá · listener/receiver đều có cặp gỡ hoặc chốt một-lần · `winExec`/`ioExec`
`shutdownNow` ở `onDestroy`.

Không trần / thiếu sót (xếp theo giá trị):

| # | Mục | Bằng chứng | Mức | Đề xuất · chi phí/lợi |
|---|---|---|---|---|
| A | Log `emit lane` theo cửa sổ chạy chữ | `ClusterBroadcaster.kt` khoá `icon|seg|emitRoad`; cửa sổ dịch mỗi 700 ms. Log SL6 08/10 `usage-1791445040262.log`: **2 797** dòng, cùng icon/seg chỉ khác `road` | [ĐO] | **ĐÃ SỬA** (§5) |
| B | Alarm `REBIND_WATCHDOG` 60 s `ELAPSED_REALTIME_WAKEUP` thức máy rồi no-op khi FGS keep-alive đang chạy | `dumpsys alarm`: 23 wakeups; logcat mỗi phút `watchdog alarm no-op` | [ĐO máy ảo] | Không hẹn alarm (hoặc hẹn không-WAKEUP / 15 phút) khi watchdog trong tiến trình sống; giữ alarm khi FGS chết. Lợi: −60 lần thức SoC/giờ ở standby. Rủi ro: đây là lưới phụ của đường tự chữa đã chạy ngoài xe (§6) ⇒ **cần owner** + đo pin xe |
| C | Nhịp 10 s màn chính (`KachiHomeActivity.tick` → HAL) khi màn chính RESUMED mà màn xe tắt | Log xe qua đêm `logs/20261008/x/usage-1791382173009.log`: `HAL đọc=385/phút` 9,6 h, D-log SDK BYD ~25 dòng/10 s (7,1 MB/đêm, sát trần 8 MiB ⇒ mất log phần sau) | [ĐO log xe] · màn xe thật sự tắt hay không [CHƯA BIẾT] | Cổng `ScreenInteractive` như R18 cho nhịp này. Đụng yêu cầu realtime 1 Hz của owner (09-21) ⇒ **cần owner**; chốt bằng `KachiPerf` + `dumpsys power` trên xe |
| D | Mỗi lệnh shell log 2 dòng (`SimpleCast shell:` + `shell OK`) | ~500 dòng / 37 phút (log SL6) | [ĐO log xe] | **ĐÃ SỬA** (owner 08/10 *"làm đi"*, §5): `ShellLogGate` — lệnh chỉ-đọc (danh sách cho phép `am stack list`/`dumpsys <dịch vụ dump thuần — soát Fable Pass 1 [P2]: theo danh sách dịch vụ, không cả họ>`/`appops get`/`settings get`/`getprop`, không nối lệnh) chỉ log khi kết quả đổi · hỏng (mức W) · nhịp sống 10 phút, kèm số lượt đã lược; lệnh đổi trạng thái log đầy đủ như cũ |
| E | Khoá prefs hình học chiếu `config_{size,overscan,density,bounds}_<pkg>…`, `scale-*:<pkg>` không bao giờ gỡ (kể cả app đã gỡ), lại nhân bản vào `__cn__` mỗi hồ sơ | `SimpleCastRuntime.kt:303-315`, `CastAppCatalog.kt:143-163` | [ĐO mã] · cỡ trên xe [CHƯA BIẾT] | **ĐÃ SỬA** (§5): dọn một lần mỗi tiến trình (luồng nền, trễ 30 s), ân hạn 30 ngày (cài lại mod khác chữ ký), PackageManager hỏng ⇒ không gỡ gì. Ảnh chụp `__cn__` trong hồ sơ KHÔNG dọn (dữ liệu hồ sơ, xuất/nhập được) |
| F | `nav_notif_{log,raw}_*.csv` không trần từng tệp, writer không đóng | `NavNotifLog.kt:34-41`, `NavNotifRawLog.kt:39-46`; chỉ khi `NavLog.verbose` | [ĐO mã] | **ĐÃ SỬA** (§5): `FileByteBudget` 8 MiB/tệp, quá thì đóng + xoay tệp mới; trần tổng vẫn `DiagStorageCap` |
| G | `DiagStorageCap` chỉ chạy lúc mở màn chính / NLS nối | `KachiHomeActivity.kt:231`, `NavNotificationListener.kt:121` | [ĐO mã] | **ĐÃ SỬA** (§5): thêm một lượt lúc tiến trình bật (`StartupHousekeeping`), không nhịp định kỳ mới — trong một tiến trình mọi nguồn đã có trần riêng |
| H | Bản APK OTA để lại ở `filesDir/update/` sau khi cài; ảnh `Pictures/Kachi/`, `Download/kachi-voice-*.zip` không dọn | `UpdateChecker.kt:95-147` | [ĐO mã] | **ĐÃ SỬA** (§5) cho APK OTA: lúc tiến trình bật, xoá tệp có versionCode ≤ bản đang cài, hoặc tệp tải dở (không đọc được, cũ > 10 phút); không biết bản đang cài ⇒ giữ hết. `Pictures/Kachi/`, `Download/kachi-voice-*.zip` chưa làm |
| I | `NavConnect.doGrantResultWithTimeout`: hết 30 s thì nhả single-flight dù worker còn kẹt I/O dadb ⇒ watchdog 30 s có thể đẻ thêm luồng kẹt | `NavConnect.kt:190-203` | [SUY] (chưa thấy trên log) | **KHÔNG SỬA — không có thật** [ĐO bytecode dadb 2.0.0 + okio 2.10.0]: phiên grant luôn có hạn đọc socket 30 s (`USER_READ_CAP`/`BACKGROUND_READ_CAP`, `DadbImpl` gọi `setSoTimeout`); sau `interrupt()` mọi lượt đọc/chờ kế tiếp ném ngay (`okio.Timeout.throwIfReached` kiểm `isInterrupted`; `MessageQueue` chờ bằng `Condition.await`) ⇒ worker bị bỏ sống thêm ≤ ~30 s ⇒ tối đa ~1 worker cũ song song, không tích luỹ. Còn một kẽ nhỏ: `finally` của worker cũ nhả cờ của lượt mới (an toàn nhờ dấu thế hệ + cổng interrupted trước force-stop) — ghi để owner biết |

## 5. Đã sửa trong lượt này

| Sửa | Tệp | Test | Hiệu quả |
|---|---|---|---|
| R4 tên màn ảo ô ổn định | `core/…/launcher/SlotVdName.kt` (mới) · `app/…/launcher/VdAppHost.kt` · `SlotVdOwner.kt` (`liveNames`) · `testbridge/TestBridgeState.kt` (KDoc) | `:core` `SlotVdNameTest` (5, gồm mô phỏng 200 lần dựng × 3 ô: ≤ 6 tên, mọi màn cũ được trả ra để `release`) · `:app` `perf/PerfR6WiringContractTest` · `TestBridgeSafetyContractTest` (cập nhật) | +124 B/lần dựng ô → 0 [ĐO máy ảo] |
| R6-A khoá log `emit lane` theo tên đường đầy đủ | `app/…/ClusterBroadcaster.kt` | `PerfR6WiringContractTest` | ~75 → vài dòng/phút khi dẫn đường + chạy chữ [SUY từ log xe; khung gửi cụm không đổi] |
| R6-D log lệnh shell chỉ-đọc theo thay đổi | `core/…/simplified/ShellLogGate.kt` (mới) · `app/…/simplified/SimpleCastRuntime.kt` | `:core` `ShellLogGateTest` (7) · `:app` `perf/PerfR6FollowupWiringContractTest` | log xe 26/09: 8 719 lượt `am stack list` × 2 dòng → lần đầu + khi stack đổi + 1 nhịp/10 phút [SUY mô phỏng: 37 phút đứng yên 555 lượt → 4 lần ghi] |
| R6-E dọn khoá chiếu của app đã gỡ (ân hạn 30 ngày) | `core/…/simplified/CastPrefsPrune.kt` (mới) · `app/…/housekeeping/CastPrefsHousekeeping.kt` (mới, sổ ở prefs `kachi_housekeeping`) | `CastPrefsPruneTest` (7) · wiring | vài trăm B/app, không phình theo số app từng chiếu |
| R6-F trần 8 MiB/tệp `nav_notif_{log,raw}_*.csv` | `core/…/core/FileByteBudget.kt` (mới) · `NavNotifLog.kt` · `NavNotifRawLog.kt` | `FileByteBudgetTest` (4) · wiring | chỉ khi verbose |
| R6-G bộ dọn chẩn đoán chạy cả lúc tiến trình bật | `app/…/housekeeping/StartupHousekeeping.kt` (mới) · `KachiApplication.kt` | wiring | tiến trình dựng lại lúc màn tắt cũng được dọn |
| R6-H xoá APK OTA đã cài | `core/…/core/UpdateApkSweep.kt` (mới) · `app/…/housekeeping/UpdateApkHousekeeping.kt` (mới) · `UpdateChecker.UPDATE_DIR` · `PackageQueries.archiveInfo` | `UpdateApkSweepTest` (5) · wiring | ~45 MB bộ nhớ trong mỗi lần OTA |

## 6. Cần đo trên xe (không làm được ở máy ảo)

- R4: sau OTA, `stat -c %s /data/system/display_settings.xml` trước/sau 3 lần nổ máy phải bằng nhau; `dumpsys display | grep uniqueId`
  thấy `kachi-slot-<ô>,0`.
- R6-A: đếm `emit lane` trong `usage-*.log` một chuyến có dẫn đường.
- B/C: số lần thức + `HAL đọc=` khi màn xe tắt mà tiến trình sống.
