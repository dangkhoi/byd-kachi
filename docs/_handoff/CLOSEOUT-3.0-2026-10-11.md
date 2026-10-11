# CLOSEOUT Kachi 3.0 — trạng thái cuối của dự án

> **Trạng thái**: Current · **Cập nhật**: 2026-10-11 · **Chủ**: dangkhoi · **Mục đích**: chốt trạng thái cuối khi đóng dự án Kachi
> (byd-launcher) ở bản **3.0 (204)** — bản gì đã ra, cái gì xong, cái gì còn mở (kèm mức bằng chứng), và cách mở lại dự án.
> Owner 11/10: *"A luôn đi, đóng dự án"* ⇒ 3.0 lên kênh OTA **không qua buổi xe của owner**; anh em test trên xe của họ.
> Nhãn bằng chứng (`.kiro/steering/conversation-protocol.md`): **[ĐO]** đo thật · **[SUY]** suy luận khớp dữ liệu · **[ĐOÁN]** · **[CHƯA BIẾT]**.
> Tài liệu này thay vai "điểm đọc đầu" của `docs/CLOSEOUT-2026-09-27.md` (2.79) và `_handoff/closeout-plan-2026-09-29.md`.

---

## 1. Bản cuối: 3.0 (204)

| Mục | Giá trị |
|---|---|
| Tệp kênh OTA | `apk/Kachi-3.0-release.apk` (thay `Kachi-2.98-release.apk`; 2.99 chưa từng lên kênh) |
| Kích thước · sha256 | 46 867 920 B · `c28a9e62e283e932e118159f9e75cd2ef0fa5fb949566fa925d8d03f762db824` [ĐO 11/10, bản dựng `bebc45aa`] |
| aapt2 | `com.byd.launcher` · versionCode **204** · versionName **3.0** · không debuggable [ĐO] |
| Chứng chỉ ký | SHA-256 `92:57:49:9B:…:26:BB:99:17` — trùng 2.98 trên `origin/main` (apksigner) ⇒ OTA đè được [ĐO] |
| Bề mặt manifest | receivers = `RebindReceiver` · `KachiTestBridge` · `ProfileInstallReceiver` — y hệt 2.98; 0 component exported mới, 0 quyền mới [ĐO aapt2 so 2.98] |
| Mã nguồn | nhánh `feat/key-source-split`, HEAD mã `bebc45aa` (3.0 (204)); commit đăng do điều phối làm |
| Kiểm off-car | bộ test **7 599 / 0 đỏ** [ĐO JUnit XML 11/10] — core 5 118 · app 2 280 (1 bỏ qua có sẵn) · car-integration 80 · vehicle-contracts 22 · offcar-planner 99 (core + 3 module chạy lại `--rerun-tasks`, offcar-planner chạy riêng); `lintRelease` 0 lỗi (612 cảnh báo có sẵn); `assembleRelease` xanh |
| QA máy ảo | QA máy ảo emulator-5556 kachi_play bản RELEASE [ĐO 11/10]: gỡ vehicleTest, cài 2.98 release (sha `b713b943…`) làm HOME, hai hồ sơ (Mặc định + QA2), ô YouTube/Waze/VietMap → `adb install -r` 3.0 (OTA giả lập) ⇒ HOME giữ, hồ sơ + 3 ô giữ nguyên, `kachi_workspace` 43 khoá không đổi byte-giá-trị, `clusternav_prefs` chỉ thêm 3 khoá bằng đúng giá trị mặc định (`acc_booster` · `hud` · `interpolate`) + mốc chạy; công tắc R18 hiện BẬT (chưa từng lưu ⇒ mặc định mới), daemon nhận bảng và `esc moved com.waze` ngay sau nâng cấp; *Kiểm tra cập nhật* ⇒ "up to date (v3.0)" khi kênh còn 2.98 (không mời hạ, không chấm); câu 3 app ⇒ 3 deep-link (lượt đầu VietMap mở trơn vì tra toạ độ máy ảo quá hạn 7 s lúc chuyển sang Nominatim — lượt hai đúng `vietmaplive://…lat=10.77…`); *"đóng lái xong bật đèn đọc"* ⇒ BẬT đèn + bỏ vế dở; *"… bằng way"* ⇒ Waze; hạ nhạc (mô hình side-load): YouTube phát, STREAM_MUSIC 10 → 2 khi nghe → 10 sau, YouTube phát suốt; vặn núm 6 giữa lượt ⇒ KEEP_USER giữ 6; 0 crash Kachi (tiến trình 17153 sống suốt từ lúc nâng cấp, `logcat -b crash` rỗng), 0 ANR Kachi (một ANR của Google Maps trên máy ảo, không phải Kachi); máy ảo để lại ở 3.0 release, Kachi HOME |
| Trên xe | ⚠ **chưa chạy 3.0 trên xe** (owner quyết OTA luôn). 2.99 (202) bản thử cài tay đã chạy trên Seal 10/10 (log buổi xe là nguồn của 3.0 R1–R5) |

**Nội dung 3.0 = 2.99 R1–R10 + 3.0 R1–R5** (chi tiết + số đo: `docs/specs/kachi-299-plan.html`, `docs/specs/kachi-300-plan.html`;
tin nhắn người dùng: `docs/_handoff/tin-nhan-anh-em-3.0.md`):

- **2.99**: YouTube phát tiếp hết bài sang bài kế (khuôn Mix `watch?v=<id>&list=RD<id>`, 7/7 máy ảo) — đổi lại mất toàn màn tự động ·
  ngày trên thanh trên = mực chip · câu ô *"vào ô số N"* không hỏi Odo/VIN · *"đóng cốp"* nghe ra *"đóng góp"* vẫn đóng (cổng đỗ giữ) ·
  hotword `YOUTUBE` · log `usage-*.log` nối lại + `usage-wake-*.log` · câu ghép vế dở không dán động từ chéo (*"đóng lái xong bật đèn đọc"*) ·
  công tắc R18 *Kéo app thoát ô về lại ô* **mặc định BẬT** (máy đã lưu giá trị giữ nguyên) · công cụ test-only `setarr`/`setisa` (sau công tắc Chế độ kiểm thử).
- **3.0**: dẫn đường một câu ra nhiều app (`VoiceNavProviders.expand`) · tên app Anh (Waze `way/ways`, hotword `WAZE`/`GOOGLE MAPS`/`VIETMAP`) ·
  **hạ nhạc còn 20 % khi Kachi nghe, trả mức cũ** (dấu bền chống tiến trình chết) · tấm chữ giọng nói −81 % CPU `:wake` (`VoiceWaveMotion`).

---

## 2. XONG vs CÒN MỞ

### 2.1 XONG — mã + test + đo off-car; chờ anh em xác nhận trên xe

| Việc | Bằng chứng | Còn cho xe |
|---|---|---|
| Dẫn đường nhiều app (R1/R2) | [ĐO máy ảo] 3 START đúng deep-link, 0 mở trơn | 🚗 câu kiểm trên xe; Waze `q=` có dẫn thẳng (OQ1) |
| Tên app Anh (R3) | [ĐO host 324 WAV] 16 lên · 0 tụt; harness 0 tụt | 🚗 giọng thật |
| Hạ nhạc 20 % (R4) | [ĐO máy ảo] 10 → 2 → 10; vặn núm giữ; dừng nhạc vẫn dừng; tiến trình chết ⇒ trả mức | 🚗 **[CHƯA BIẾT] núm âm lượng media BYD có theo `STREAM_MUSIC` không** — không theo thì nhạc không nhỏ |
| CPU tấm chữ (R5) | [ĐO máy ảo bisect 7 bản] không bản nào hồi quy độ trễ; CPU 4 080 → 760 ms/phiên | 🚗 "hiện lên tý tắt" chưa tái hiện [CHƯA BIẾT] |
| YouTube sang bài kế (2.99 R1) | [ĐO máy ảo] 7/7 | 🚗 trên xe trong ô |
| R18 bật sẵn (2.99 R10) | [ĐO xe Seal 10/10] Waze `esc moved` ×2, 0 NPE — **1 app · 1 xe** | 🚗 YouTube/VietMap/GMaps với R18 bật |
| Giọng R3–R9 (2.99) | [ĐO harness + test hồi quy từ log xe 10/10] | 🚗 |

### 2.2 CÒN MỞ — để cho tương lai (không làm trong 3.0)

| Backlog | Trạng thái cuối | Mức | Điều kiện mở lại |
|---|---|---|---|
| `ISA-NATIVE-SIGN` — giới hạn tốc độ bản đồ vào ô biển GỐC của cụm/HUD | **BLOCKED — chỉ còn đường coding ECU ADAS.** Xe 08/10: ghi `setIsaMap*` đủ kiểu ⇒ HAL `ok`, cụm/HUD không hiện. Xe 10/10: bơm TestDevice `0x237` ⇒ cụm hiện biển **XÁM** (ô "dự báo"), HUD không — chỉ là giả lập cục bộ của cụm, không ra bus. Cổng `0x4320002A` (map-ISA) là byte cấu hình ADAS. **Xe 10/10 21:1x (2.99, đỗ P, owner duyệt):** ghi `setev 0x4320002A=2` ⇒ HAL trả sentinel `-2147482648`, đọc lại vẫn **1** (ECU từ chối ghi từ phần mềm); `setisa` gói 12 trường đúng khuôn (50 km/h = mã 10) ⇒ rc=0 nhưng cụm/HUD **không hiện gì**; đã gửi gói xoá, cổng vẫn 1 | [ĐO xe 08/10 + 10/10 (cả ghi cổng lẫn `setisa` đúng khuôn)] · cổng coding [ĐO nguồn fw 2606, `re-map-speedlimit-2606-2026-10-10.md` §8.d/§11] | Chỉ còn coding ECU ADAS `0x4320002A=2` bằng đồ nghề chẩn đoán (ngoài app) — owner quyết; kể cả mở được, biển lên HUD vẫn [CHƯA BIẾT] |
| Biển **ĐỎ** TSR trên cụm (khung `0x2B4`, `updateTrafficSymbol`) | **Chưa làm.** Framework không có id `_SET` nào trên `0x2B4`; IVI không đọc được `0x2B400020` | [ĐO nguồn] | Chỉ khi có đường ghi bus mới (ngoài framework) — hiện không có |
| `HUD-ROADNAME-OEM-CADENCE` — tên đường lên HUD (SL6) | Kachi ghi tên đường theo nhịp OEM từ 2.98 R9; SL6 HUD **chưa từng** xác nhận hiện tên | [CHƯA BIẾT] — chờ ảnh HUD của anh em SL6 | Ảnh HUD từ anh em (tin nhắn 3.0 mục 3) |
| `CAN-NAV-0X43F-LISTEN` — công cụ nghe CAN | 10/10 chạy **0 khung**: navopen `buildRegTable` sai khuôn so với CanDataCollect (`[id][subid][kênh MCU][mode 2][op 1]`) | [ĐO xe + nguồn, `oncar-seal-2026-10-10-r18-sla-can.md` §3] | Sửa navopen (kênh/mode + in mã trả) rồi đo lại; script nay ≤ 110 s/lượt + kiểm xoá bảng |
| OQ1 spec 300 — Waze `q=<chữ>&navigate=yes` trên xe | máy ảo dẫn được; xe mới chứng minh `ll=` | [CHƯA BIẾT] | Không dẫn thẳng ⇒ đổi một dòng bảng sang đường toạ độ |
| VietMap trong câu nhiều app, lần tra toạ độ ĐẦU | QA 11/10: máy không có Geocoder ⇒ chuyển sang Nominatim và lượt đầu quá hạn 7 s ⇒ VietMap mở trơn; lượt hai dẫn đúng | [ĐO máy ảo] · trên xe [CHƯA BIẾT] | Nếu anh em báo VietMap hay chỉ mở trơn ở lần đầu ⇒ xem lại hạn tra toạ độ / làm ấm trước |
| OQ3 spec 300 — app nằm trên khi nhiều app | hôm nay = app mở xong sau cùng | owner | Owner chọn |
| OQ4 spec 300 — nhiều app + *"rồi"* lệnh khác | một `Nav` mang cả đuôi (y hệt 2.99) | [ĐO off-car] | Nới cổng 1 = đường mới, cần owner |
| OQ1 spec 299 — YouTube vừa toàn màn vừa sang bài | đo 6 cách, không cách nào đáng tin | [ĐO máy ảo] | Chỉ nếu owner chấp nhận chạm giả theo toạ độ |
| `HEADER-INK-OVER-BRIGHT-WALL` (OQ2 spec 299) | Tối + ảnh rất sáng + trong suốt cao: chip/ngày 3,7 : 1 | [ĐO ảnh máy ảo] | Owner quyết thẩm mỹ |
| `VOICE-HOTWORD-SHORT-LABEL-DELETION` [P2] | dòng `<động từ> LÁI` làm rụng *"kính"* (host 6 lên/0 tụt khi bỏ) | [ĐO host] | Owner + 🚗 (đổi tệp hotword mọi người dùng) |
| Các dòng 🚗 / BACKLOG khác (camera, ô, chiếu cụm, DL5, i18n, lốp…) | giữ nguyên trạng thái trong `docs/PROJECT-BACKLOG.md` | theo từng dòng | Không việc nào chặn 3.0 |

**Giới hạn thật đã biết (không phải lỗi chờ sửa):** BYD giết Kachi mỗi lần tắt máy (không `PACKAGE_RESTARTED`) ⇒ app nguội lúc nổ máy ·
OTA không lùi bản (chỉ lên bản cao hơn) · máy ảo không giả lập được HAL xe, cụm, HUD, mã phím 328 · câu ghép dài có thể rụng vế giữa
do giới hạn mô hình (`VOICE-DROPPED-MIDDLE`, [ĐOÁN]).

---

## 3. Cách mở lại dự án

**Đọc trước (theo `CLAUDE.md` §16):** `CLAUDE.md` → `.kiro/steering/*.md` (5 tệp) → `docs/README.md` (INDEX duy nhất) →
`docs/PROJECT-BACKLOG.md` (nguồn task duy nhất) → tài liệu này → spec bản cuối `docs/specs/kachi-300-plan.html` §9b.

**Dựng + kiểm (macOS):**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew clean :core:test :app:testDebugUnitTest :app:lintRelease assembleRelease
./gradlew :car-integration:test :vehicle-contracts:test
./gradlew :offcar-planner:test --rerun-tasks     # chạy riêng: ExpansionTransportFenceTest đỏ khi chạy chung build song song
python3 scripts/count-tests.py                   # đếm từ JUnit XML, không tin grep console
```

- `adb`/`emulator` không ở PATH: `~/Library/Android/sdk/platform-tools/adb`. Máy ảo chạy song song làm test chiếu cụm chậm/đỏ ngẫu nhiên ⇒ `kill -STOP <pid qemu>` khi chạy full test, `kill -CONT` sau.
- Nhiều agent cùng cây ⇒ chỉ một luồng chạy Gradle; cache Kotlin hỏng ("already registered") ⇒ `./gradlew --stop` + xoá `*/build/kotlin`.
- `doc niêm phong` (`docs/README.md` mục 🔒): sửa một byte ⇒ `:offcar-planner:test` đỏ — khôi phục bytes, không đổi hằng hash.

**Ký + OTA:** khoá ký riêng ngoài repo `~/.kachi/kachi-release.keystore` + `keystore.properties` (gitignored; thiếu ⇒ `assembleRelease` fail có chủ ý).
Đăng = bump `versionCode`/`versionName` → `assembleRelease` → chép `app/build/outputs/apk/release/app-release.apk` thành
`apk/Kachi-<ver>-release.apk`, `git rm` bản cũ (chỉ giữ MỘT tệp) → kiểm `aapt2 dump badging` (vc/version, không debuggable) +
`apksigner verify --print-certs` (cert = bản trước) + so receivers → quét bảo mật → commit → `git push origin HEAD:main`
(merge `origin/main` trước nếu main có commit docs-only). App dò GitHub Contents API `apk/` trên `main` và cài qua dadb loopback.
Chi tiết + nhật ký từng bản: `apk/README.md`. Bản chưa muốn đăng ⇒ đặt tên ngoài khuôn (`Kachi-<ver>-CHUA-DANG.apk`, bị ignore).

**adb vào xe:** xe nghe `<vehicle-ip>:5555` khi đầu xe nối Wi-Fi; macOS chặn Local Network cho adb ⇒ cầu `nc` về loopback
`127.0.0.1:15555` (`docs/diagnostics/adb-car-tunnel-macos.md`). Không ghi IP xe vào repo. Cắm CarPlay/Android Auto là đầu xe tắt Wi-Fi ⇒
lấy dữ liệu qua `ClusterDiag`/`kachi-logs` do app tự chụp (`CLAUDE.md` §11). Buổi xe: thấy sập lần đầu là dừng; lệnh đổi state hệ thống phải
trả lời đủ 4 câu của `CLAUDE.md` §4.

**Máy ảo:** `clusternav10` (google_apis, kênh shell/dadb chạy được — cần `adb tcpip 5555` + `adb reverse tcp:5555 tcp:5555`) cho harness giọng nói;
`kachi_play` (Play image, YouTube 21.35 chạy được) cho YouTube/âm lượng. Muốn thử nâng cấp thật: gỡ bản vehicleTest/debug (khác chữ ký) rồi cài
bản release cũ, sau đó `adb install -r` bản mới.

**Harness:** `scripts/emulator/voice-e2e.sh` (T1 chữ → ý định, T2 WAV → nghe; ca ở `voice-cases.tsv`, sinh WAV `voice-wavgen.sh`; cần
vehicleTest + run-as) · `voice-teach-e2e.sh` · `voice-wake-e2e.sh` · `perf-snapshot.sh` · `camera-*-e2e.sh` · cầu kiểm thử `KachiTestBridge`
(`am broadcast -a com.byd.launcher.TEST … cmd=say|hal|…`, chỉ chạy khi bật *Chế độ kiểm thử qua adb*, 60′, tự tắt khi tắt máy) ·
âm lượng R4: `docs/diagnostics/voice-300-r4-duck/vol.sh`. Script xe: `scripts/vehicle/` (ghi ADAS/CAN chỉ khi đỗ P, 0 km/h, owner có mặt).

**Nguồn RE trong workspace** (`CLAUDE.md` §12): `../jadx-*`, `../dashcast-src/CHANGELOG.md`, `../firmware/`, dump xe `docs/diagnostics/carlog-*/`.

---

## 3b. Hai mục tiêu chờ firmware mới — dẫn đường lên HUD · giới hạn tốc độ lên cụm + HUD

Owner 11/10: giữ hai mục tiêu này để **mỗi lần BYD đẩy firmware mới** thì kiểm lại xem firmware đó có mở đường phần mềm không.
Trạng thái ở fw 2606 (OTA V2.0.4): cả hai **chưa mở được bằng phần mềm** — chi tiết đo/RE và phần coding đã ghi ở các tệp dưới, không chép lại ở đây.

| Mục tiêu | Trạng thái fw 2606 | Tệp tham chiếu |
|---|---|---|
| **1. Dẫn đường (mũi tên · khoảng cách · tên đường) lên HUD kính lái Seal** | HUD Seal không hiện dẫn đường; SL6 (cùng fw) hiện mũi tên + khoảng cách, chưa có tên đường. Khoá nằm ở cấu hình xe/HUD, đầu xe không ghi được [ĐO + SUY, xem tệp] | `docs/diagnostics/hud-coding-recipe-2026-08-19.md` · `hud-provisioning-compare-2026-08-19.md` · `factory-hud-nav-RE-avenues-2026-08-19.md` · `docs/decisions/0002-hud-nav-coding-locked.md` · `oncar-seal-2026-10-09-hud-isa-power.md` (đọc kèm phần đính chính 138/162 = mã dòng xe) |
| **2. Giới hạn tốc độ theo bản đồ lên cụm + HUD** | Ghi cấu hình từ đầu xe bị ECU từ chối; gói ISA đúng khuôn không hiện [ĐO xe 10/10]. HUD không có đường IVI nào nhận biển [ĐO nguồn] | `docs/diagnostics/re-map-speedlimit-2606-2026-10-10.md` (§1–§11) · `oncar-seal-2026-10-10-r18-sla-can.md` · `oncar-prep-2026-10-09-isa-can-odo.md` · backlog `ISA-NATIVE-SIGN` · `ISA-MAP-SENDARR` |

**Khi có firmware mới — kiểm theo thứ tự (chỉ đọc trước, đo xe sau, đúng CLAUDE.md §14):**
1. Lấy firmware mới vào `../firmware/fw-<số>/`, so với fw 2606 bằng cách đã dùng trong `re-map-speedlimit-2606-2026-10-10.md` (so chuỗi framework/dex, thư viện cụm, DiCarServer, CarSetting).
2. Tìm thứ **mới xuất hiện**: hàm/khoá ghi cho cấu hình bản đồ của ADAS · dịch vụ HUD có nhận dữ liệu dẫn đường hoặc biển tốc độ · trang Cài đặt mới bật hai tính năng · ghi chú phát hành nhắc HUD/ISA.
3. Có thứ mới ⇒ đo trên xe theo quy trình cũ (đọc trước; ghi chỉ khi đỗ P + owner duyệt + có đường trả). Công cụ test sẵn trong app: cầu kiểm thử `setisa`/`setarr`/`getid` (sau công tắc Chế độ kiểm thử).
4. Không có gì mới ⇒ ghi một dòng vào backlog "fw <số>: chưa mở" và dừng.
5. Đường ngoài app (đại lý/đồ nghề chẩn đoán chính hãng) là quyết định của owner, không làm trong repo.

## 4. Luật đứng (giữ khi mở lại)

- **Spec trước, code sau** (`docs/specs/<slug>.html`, template `_template.html`); mỗi khẳng định gắn nhãn bằng chứng; tính năng mới đi 4 tầng
  shell thô trên xe → `car-integration` → `core` → UI (`CLAUDE.md` §14). Generic, không `if (pkg == …)`; khác đời xe vào `ClusterProfile`.
- **Mỗi bản sửa chứng minh không nặng thêm** (luồng/nhịp/listener/lệnh shell, đo trước–sau).
- **Senior review** (Fable 5.1) + **quét bảo mật trước MỌI commit/push** — không ngoại lệ; **không bao giờ `--no-verify`**
  (hook pre-commit của repo chặn cả chuỗi tiêu đề khoá PEM trong tài liệu — viết lại câu).
- **Riêng tư**: chỉ dùng nơi công cộng làm ví dụ (chợ Bến Thành, Bitexco); không địa chỉ/toạ độ/tên đường gần nhà-công ty owner, không IP xe
  (`<vehicle-ip>`), không đường dẫn máy cá nhân, không VIN; log xe chứa dữ liệu xe để ngoài git. Danh tính trong repo: handle `dangkhoi`.
- **Phiên bản**: mỗi bản đã báo cho người dùng = một số hiệu riêng; không đoán xe đang chạy bản nào (đọc `dumpsys package`).
- Code + doc **atomic** cùng commit; doc mới phải vào `docs/README.md`, việc mới vào `docs/PROJECT-BACKLOG.md`.

---

## 5. Nhật ký

- 2026-10-11 — tạo khi chuẩn bị đăng 3.0 (204) và đóng dự án; số đo §1 từ bản dựng `bebc45aa` cùng ngày.
