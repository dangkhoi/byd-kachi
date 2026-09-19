# PLAYBOOK LÊN XE — Kachi 1.53 (54)
> ⚠ **2026-09-19 — SUPERSEDED cho 1.79 bởi [`oncar-master/RUNBOOK.md`](oncar-master/RUNBOOK.md)** (một buổi đóng hết 🚗). Doc này giữ làm tham chiếu chi tiết. **CẤM `am display move-stack`** ([ĐO] treo system_server 3/3 trên DiLink3; code đã đổi `am stack move-task` 2026-09-15) — mọi chỗ nhắc move-stack ở dưới là LỊCH SỬ.


> **Loại**: Diagnostics (on-car playbook) · **Trạng thái**: Current · **Cập nhật**: 2026-09-14 · **Chủ**: dangkhoi
> **Mục đích**: lên xe là BẮT ĐẦU ĐO NGAY — không phải mở code ra dò xem tính năng nằm ở đâu.
> **Bộ script đi kèm**: `scripts/vehicle/kachi/` (chạy `run-all.sh`, hoặc từng bước một).
> **Bộ script nay TỰ CHỤP BẰNG CHỨNG** (owner 2026-09-14: *"chuẩn bị toàn bộ script test automation trên xe
> thông qua adb, log liếc các loại"*): mỗi bước tự mở logcat nền · tự chụp màn sau mỗi thao tác · tự diff
> `dumpsys` trước/sau · tự ghi thời gian từng lệnh — xem §1.2.
> **Bằng chứng đổ về**: `docs/diagnostics/carlog-kachi-<ngày>-<giờ>/` (đã nằm trong `.gitignore`).

## Quy ước (bắt buộc đọc — CLAUDE.md §2)

| Nhãn | Nghĩa | Được phép viết |
|---|---|---|
| **[ĐO]** | đọc được từ máy / dump thật của chính buổi này | "là như thế" |
| **[SUY]** | suy luận khớp hiện tượng, chưa có dữ liệu trực tiếp | "nghi là" + cách chốt |
| **[ĐOÁN]** | mới chỉ hợp lý | "đoán", nói rõ chưa kiểm |
| **[CHƯA BIẾT]** | không có dữ liệu | *phải* ghi đúng bốn chữ này, KHÔNG được suy |

**Ô trống trong biên bản = CHƯA ĐO.** Điền bừa một dấu tick còn tệ hơn để trống: lần sau không ai biết mục đó
đã đo thật chưa. Dữ liệu buổi cũ (kể cả `docs/diagnostics/oncar-*` 08-13…08-19) chỉ dùng để **định hướng cần đo
gì**, KHÔNG thay được phép đo mới (CLAUDE.md §14).

**An toàn**: xe **ĐỖ**, số **P**, **phanh tay**. Mục 2.11/2.12 thao tác lên **cụm đồng hồ trước mặt người lái** —
không bao giờ chạy khi xe lăn bánh. Mọi bước GHI trong script đều hỏi trước và in sẵn lệnh hoàn tác (CLAUDE.md §4).

---

## 0. CHUẨN BỊ Ở NHÀ (làm xong hết trước khi ra xe)

### 0.1 Vật tư

| Thứ | Kiểm bằng | Ghi chú |
|---|---|---|
| APK **1.53** release | `ls -l apk/Kachi-1.53-release.apk` | ⚠ **CHƯA CÓ trong repo lúc viết** — `apk/` đang giữ 1.49 (27 MB). Dựng + chép trước khi đi: `./gradlew :app:assembleRelease` rồi `cp app/build/outputs/apk/release/app-release.apk apk/Kachi-1.53-release.apk`. Ký khoá Kachi riêng (L2) |
| **Mạng cho mô hình giọng** | hotspot điện thoại, hoặc mạng của xe | Mô hình Vosk **32 MB tải riêng** (không nằm trong APK). **KHÔNG có đường cài từ tệp cục bộ** — [ĐO] `VoiceModelStore.install` chỉ nhận HTTPS qua `HttpConn.open`. Ước lượng: 10 Mbps ≈ 30 s · 4 Mbps ≈ 70 s · 1 Mbps ≈ 4,5 phút + ~15 s giải nén |
| 3 tệp WAV đối chứng | `70-voice.sh` tự sinh nếu máy có `say` + `afconvert` | `say -v Linh -o /tmp/k.aiff "bật đèn đọc"` → `afconvert -f WAVE -d LEI16@16000 -c 1 /tmp/k.aiff wav/01.wav`. **Không commit** (`scripts/vehicle/kachi/wav/.gitignore`) |
| `adb` chạy được | `adb version` | dùng bản trong Android SDK của máy; không cần cài gì trên xe |
| repo ở máy mang theo | `git status` sạch | script đọc `:core` để sinh bảng datum — **phải có repo**, không chỉ APK |
| IP xe | **HỎI LẠI OWNER TẠI CHỖ** | KHÔNG hardcode vào script/tài liệu (repo public) |
| Điện thoại có CarPlay/AA | | cần cho mục 2.12 |
| Điện thoại quay video màn | | mục 2.16 — đối chứng cho độ trễ "nhả nút → có chữ" mà logcat đo được |
| **Người thứ hai** | | mục 2.16 pha 3: **người ngồi ghế phụ** nói và bấm khi xe lăn bánh, KHÔNG phải người lái |
| App nav để test (T1) | VietMap đã cài · GMaps zin | GMaps/YouTube bản mới còn nợ (backlog T1) |

### 0.2 Bản nào cài trên xe — quyết định TRƯỚC KHI đi

- **Mặc định: giữ bản `release`** (1.53). Đây là bản sẽ ship, phải test đúng nó.
- **Bản `release` KHÔNG debuggable** ⇒ **không `adb shell run-as`** ⇒ **không đọc/không sao lưu được
  `/data/data/com.byd.launcher/shared_prefs/*`**. [ĐO] `app/build.gradle.kts:76` `isDebuggable = false`.
- Nếu buổi test **cần bằng chứng mức prefs** (ví dụ muốn chứng minh khoá nào đi theo hồ sơ): có build type
  **`vehicleTest`** — [ĐO] `app/build.gradle.kts:80-86`: `initWith(release)` + `isDebuggable = true` + **cùng khoá
  ký release**. Cùng chữ ký + cùng versionCode ⇒ `adb install -r` đè lên bản release **không mất dữ liệu**, và
  mở được `run-as`. Build: `./gradlew :app:assembleVehicleTest` (JAVA_HOME=`/opt/homebrew/opt/openjdk@17`).
  ⚠ Cuối buổi **cài lại bản release** — bản debuggable không được để lại trên xe.

### 0.3 Sao lưu trước khi chạm (cái gì lấy được, cái gì KHÔNG)

| Muốn lưu | Bản release lấy được? | Lệnh / đường |
|---|---|---|
| prefs của app (`shared_prefs/*.xml`) | ❌ không (không debuggable) | chỉ được nếu cài `vehicleTest`: `adb shell run-as com.byd.launcher cat shared_prefs/<tệp>.xml` |
| Ảnh chụp cấu hình hiện tại | ✅ | **chụp màn từng nhóm Cài đặt** — đây là bản sao lưu THẬT của buổi này |
| Tệp app tự ghi (diag + castlog) | ✅ thường được | `adb pull /sdcard/Android/data/com.byd.launcher/files/` (diag/ · castlog/) |
| Trạng thái hệ thống | ✅ | `dumpsys display` · `dumpsys window displays` · `am stack list` · `settings get …` |
| Hành vi runtime | ✅ | `logcat` (danh sách tag ở §3) |
| Phiên bản đang cài | ✅ | `dumpsys package com.byd.launcher \| grep versionName` — **CLAUDE.md §9: đọc, không đoán** |

**Chụp chẩn đoán của app (`ClusterDiag`)**: [ĐO] `ClusterCast.autoDiag` — nó chạy **TỰ ĐỘNG sau MỖI lần chiếu
thành công**, *không có nút "chụp ngay" riêng*. Kết quả ghi `…/files/diag/diag-<MMdd-HHmmss>.txt`, giữ 20 tệp gần
nhất, mở đầu bằng phần **TÓM TẮT** (VD cụm đo được · khung cửa sổ · size/dpi · có cửa sổ mồ côi không).
⇒ Muốn có tệp diag thì **phải chiếu ít nhất một lần** (mục 2.12).

**Màn Chẩn đoán** (`DiagActivity`) mở từ: **Cài đặt › Hệ thống & quyền › Nâng cao › Chẩn đoán**
([ĐO] `SettingsSections.kt:330`). Nó `exported="false"`; manifest ghi rõ ClusterNavActivity phải `exported=true`
vì **DiLink3 chặn shell-uid mở activity không export** ⇒ `am start` vào `DiagActivity` **[CHƯA BIẾT] có chạy
không trên ROM này** — cứ mở bằng tay trong app, đừng phụ thuộc adb.

### 0.4 Chạy thử bộ script khi CHƯA có xe

```bash
for f in scripts/vehicle/kachi/*.sh; do bash -n "$f" || echo "LỖI CÚ PHÁP: $f"; done
KACHI_OUT=/tmp/kachi-dry KACHI_TARGET=dummy:5555 bash scripts/vehicle/kachi/20-datums.sh   # sinh bảng datum off-car
ls -l apk/Kachi-*-release.apk            # 40-ota.sh đọc bản ĐÍCH từ tên tệp này, không từ hằng số (§9)
```

---

## 0.5 CÓ GÌ MỚI TỪ 1.47 → 1.53 (bảy thứ, mỗi thứ một mục đo)

Bản 1.47 là bản mà playbook cũ được viết cho. Từ đó tới 1.53 có **bốn lượt đăng OTA** và một lượt chưa đăng —
mỗi lượt thêm một thứ **chưa từng chạm xe lần nào**. Danh sách này là lý do buổi test 1.53 dài hơn buổi 1.47,
không phải một mục "changelog cho vui".

| Bản | Việc | Đo ở mục | Vì sao KHÔNG bỏ qua được |
|---|---|---|---|
| 1.45 | **U12** — bộ chỉnh vị trí bong bóng VietMap **luôn hiện** (mờ + khoá + câu nhắc khi chưa chiếu) | §2.7 | Điều kiện chỉnh **không đổi**: chỉ kéo được khi đang chiếu thật ⇒ máy ảo không có cụm, off-car không đo được |
| 1.46 | **U13** — hồ sơ nói rõ nó giữ bố cục nào (câu "lưu cho hồ sơ «X»" + tóm tắt trên thẻ hồ sơ) | §2.8 | Tóm tắt sai một con số là người dùng chọn nhầm hồ sơ; chỉ đọc được bằng mắt trên xe |
| 1.47 (48) | **S4 — hồ sơ là TẤT CẢ**: gộp cảnh vào hồ sơ, mọi cấu hình theo hồ sơ, thanh trên chỉ còn chip hồ sơ | §2.8 | Migration cảnh→hồ sơ chạy **đúng một lần**, đúng lượt nâng cấp. Lỡ lượt đó là mất bằng chứng vĩnh viễn |
| 1.48 | **V1 pha CHỮ** — gõ lệnh chữ ở *Cài đặt › Hệ thống & quyền › Nâng cao › Gõ lệnh chữ*, thi hành qua đường đã có | §2.16 A | 10 câu spec §6.1 mới chỉ chạy trên máy ảo; nút xe thật chưa từng bị một câu lệnh chạm vào |
| 1.49 (50) | **V1 pha NGHE** — Vosk **tại máy**, ngữ pháp kín sinh từ danh mục, bấm-để-nói, nút mic trên thanh trên. APK nhảy 9 → **27 MB** (2 ABI); mô hình **32 MB tải riêng** · **U14** — nút ⇄ đổi app trên ô làm kín đáo (chỉ icon 20dp, bỏ viền) | §2.16 · §2.17 | Toàn bộ pha nghe mới chỉ đo bằng 3 tệp WAV trên máy ảo. **Giọng người thật + tiếng ồn xe là thứ off-car không mô phỏng được** — đó đúng là T18 |
| 1.53 (54) | **V1.1 — nói được tên**: "vào ô số N" · tên bài hát · điểm đến · "bằng YT Music / VietMap / GMaps / Waze" (ghép hai lượt nhận dạng: ngữ pháp + từ vựng mở) · **H2** — vòng đời ô chiếu app: hết rò màn ảo `kachi-slot-*`, ô có nhãn "App đã đóng" khi app trong ô chết | §2.16 · §2.17 | `vietmaplive://` và `google.navigation:ll=` đều đang ở mức **AWAITING_CAR** trong `VoiceAppTargets` — chúng dựng từ nguồn Kiki đã decompile, chưa lần nào chạy trên xe |
| — | **H1** — nghiên cứu "app bị đẩy khỏi màn ảo của ô (Waze)": đã có **cơ chế** ([ĐO] AOSP 10 `ActivityStackSupervisor.java:1096-1106`), **chưa** có đường lùi trên xe | §2.15 | Cả thiết kế đường lùi phụ thuộc đúng một số đo: **Kachi trên xe chạy uid nào**. Là `system` ⇒ bỏ hẳn H1, tiết kiệm cả một vòng implement |

**Đọc bảng này như một danh sách nợ, không phải một lời khoe**: sáu trong bảy dòng đang ở trạng thái *"xong
off-car, chờ xe"*. Buổi test này là lượt duy nhất biến chúng thành [ĐO] — hoặc thành một danh sách lỗi thật.

---

## 1. KẾT NỐI + BASELINE (15 phút đầu, chỉ ĐỌC)

```bash
cd <repo>
scripts/vehicle/kachi/run-all.sh <ip-xe>:5555        # chạy cả bộ, dừng ở mọi chỗ cần tay
# hoặc từng bước:
scripts/vehicle/kachi/00-connect.sh  <ip-xe>:5555
scripts/vehicle/kachi/10-baseline.sh <ip-xe>:5555
```

`00-connect.sh` trả lời đúng 4 câu và ghi vào `connect.txt`:

1. adb vào được xe chưa;
2. **Kachi đang cài bản nào** (versionName + versionCode, đọc từ máy — không đoán);
3. xe đời nào (`ro.product.model` · `ro.build.version.release` — DL3 ≈ Android 10, DL5 ≈ Android 12) và **ABI CPU** (`ro.product.cpu.abilist` — APK 1.49 chỉ đóng `arm64-v8a` + `armeabi-v7a` cho Vosk; [CHƯA BIẾT] đời DiLink nào là 32-bit);
4. **VD cụm là display mấy** (ĐO qua `dumpsys display`, khớp `fission|xdja`; không lấy cờ RAM).

`10-baseline.sh` chụp: `am stack list` · `dumpsys window windows` · focus · `dumpsys activity activities` ·
cờ `enable_freeform_support`/`force_resizable_activities` · 5 điều kiện quyền · danh sách tệp app tự ghi ·
1 ảnh màn · logcat gốc. **Không có baseline thì cuối buổi không ai chứng minh được cái gì đã đổi.**

### 1.1 BẬT CẦU KIỂM THỬ — việc đầu tiên sau khi nối được adb

`10-baseline.sh` mục **[G]** dừng lại ở đây và chờ. Đường bật:

> **Kachi › Cài đặt › Hệ thống & quyền › Nâng cao › «Chế độ kiểm thử qua adb»**

- Công tắc **tự tắt sau 60 phút** — buổi test dài thì bật lại; script tự nói khi cầu im.
- Bật xong, phần lớn thao tác tay biến mất: đổi hồ sơ, mở phiên nghe, chạy một câu lệnh chữ, đọc cấu hình
  hiện tại, chạy WAV thử — tất cả gọi được từ máy tính:

```bash
adb shell am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd state
adb shell am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd say --es text 'bật đèn đọc'
adb shell am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd profile --es name 'Đi làm'
# kết quả: setResultData (một dòng) + JSON ở /sdcard/Android/data/com.byd.launcher/files/test/
```

- ✅ **[ĐO 2026-09-14]**: `KachiTestBridge` **đã có trong mã** (spec `docs/specs/kachi-test-bridge.html`) và chạy
  thật trên máy ảo với bản 1.53/54 — 13 lệnh + 6 ca lỗi, kể cả cổng `test_mode_off` và cổng CONFIRM. ⚠ Hai điều
  **chưa đo**: (a) bản nằm ở `apk/` có thể còn cũ hơn mã; (b) **shell của ROM BYD** gửi được vào receiver
  `exported` hay không (máy ảo mới chỉ chứng minh với shell của emulator). Vì vậy mọi bước GIỮ NGUYÊN **đường tay
  đi kèm** — cầu im thì script in đúng việc cần làm bằng tay và chạy tiếp. Trước khi đi: kiểm bằng một lượt
  `--es cmd state`; trả `"error":"test_mode_off"` nghĩa là **cầu sống nhưng công tắc chưa bật**, còn không có kết
  quả nào mới là bản trên xe chưa có cầu.
- ⚠ **TẮT công tắc trước khi rời xe.** Một cửa thi hành lệnh mở vĩnh viễn trên xe đang chạy là một lỗ, không
  phải một tiện nghi. `run-all.sh` in lại nhắc này ở dòng cuối.

### 1.2 BỐN THỨ MỌI BƯỚC TỰ CHỤP (không cần nhớ, không cần gõ)

| Thứ | Tệp sinh ra | Vì sao |
|---|---|---|
| **logcat nền** `-v time`, lọc theo ~25 tag của 1.53 | `logcat-<bước>.txt` | Chạy **suốt** bước rồi mới đóng. Bắt được cả những dòng nổ ra lúc không ai nhìn màn hình — đúng loại bằng chứng mà `logcat -d` cuối bước đã trôi mất |
| **ảnh màn sau mỗi thao tác** | `shot-<bước>-NN.png` | CLAUDE.md §15: *"chụp một ảnh NGAY TRƯỚC mỗi thao tác, không tái dùng ảnh cũ"*. Đánh số tăng dần, chạy lại bước không ghi đè |
| **diff `dumpsys` trước/sau** (stack · display kể cả `kachi-slot-*` · activity · cửa sổ · quyền) | `diff-<bước>.txt` | Câu hỏi *"bước này đã đổi gì trong hệ thống"* trả lời được bằng máy thay vì bằng trí nhớ |
| **thời gian từng lệnh (ms)** | `session-notes.txt` (dòng `⏱`) | Sai số ~10–30 ms — đủ để phân biệt *"1 giây hay 8 giây"*, **không** đủ để so 20 ms. Đừng trích nó như một số đo hiệu năng tinh |

**Và một điều CẤM**: không `input tap <x> <y>` ở bất kỳ đâu trong bộ này. Màn giữa của xe là **1920×720**, máy
ảo dựng kịch bản là 1080×2340 — một toạ độ đúng ở nhà là một cú chạm nhầm hàng trên xe (CLAUDE.md §15 đã trả
giá bằng hơn chục vòng chụp-đoán-tap). Đường đúng: **deep link** `--es open_settings_group <nhóm>` cho màn Cài
đặt, **cầu kiểm thử** cho mọi thứ còn lại, và **tay người** cho đúng những việc mà máy không làm hộ được (bật
công tắc, nói vào micro, tắt máy xe).

> Nếu bước 1 hỏng: **DỪNG**, đừng đi tiếp bằng suy đoán. Xem §5 (bẫy) — 90% là CarPlay/AA đang cắm (đầu xe tắt
> WiFi) hoặc adbd không nghe 5555.

---

## 2. THỨ TỰ TEST — RỦI RO TĂNG DẦN

Nguyên tắc xếp thứ tự: **đọc trước → ghi trong app → ghi ra hệ thống → chạm cụm cuối cùng**. Một lần cụm kẹt là
phải tắt máy xe; đừng để nó chặn 16 mục còn lại. Ngoại lệ có chủ ý: **OTA (2.5) đặt sớm**, vì mọi mục sau phải
chạy trên ĐÚNG bản định ship, và migration cảnh→hồ sơ chỉ chạy **một lần**, đúng lượt nâng cấp đó.

⚠ Bảng dưới xếp theo **RỦI RO**, không theo số hiệu: bốn mục mới (2.14–2.17) nằm **giữa 2.11 và 2.12** vì rủi
ro của chúng thấp hơn bước chiếu-cụm. Số hiệu giữ nguyên thứ tự *ra đời* để mọi biên bản/backlog đã trỏ tới
"§2.12" không trỏ nhầm sang mục khác — đổi số là cách nhanh nhất làm hỏng một tham chiếu đã có.

| Mục | Nội dung | Rủi ro | Script |
|---|---|---|---|
| 2.1 | S2 — quyền + vòng kiểm (đọc) | đọc | `10-baseline.sh` |
| 2.2 | W1 — datum NEEDS_CAR + L-RE/L-RE2 (đọc) | đọc | `20-datums.sh` |
| 2.3 | U7/U9 — màu trạng thái thật trên bảng lốp/cửa/radar | đọc + mở cửa | tay |
| 2.4 | U8(a) · U10 · U11 — quan sát giao diện | đọc | tay |
| 2.5 | **L2 — OTA trên xe thật** | ghi (cài đè app) | `40-ota.sh` |
| 2.6 | S2 — Settings ghi thật (quyền qua dadb · ghế · PM2.5 · badge/bubble) | ghi (HAL + overlay) | tay |
| 2.7 | U12 — bộ chỉnh bong bóng luôn hiện | ghi (prefs) | tay |
| 2.8 | **U13/S4 — đổi hồ sơ áp applier thật** + V-mig + P7 | ghi (HAL + reboot vật lý) | `30-profiles.sh` |
| 2.9 | W5 — đo phím vô-lăng theo ngữ cảnh camera | đọc (+ mở cam) | `50-keys.sh` |
| 2.10 | W2 — kính ½ · gạt mưa bảo trì · đèn đọc tất cả | **ghi vào THÂN XE** | tay |
| 2.11 | S3 — 4 tính năng chuyển từ màn cũ | ghi (khung/DPI cụm) | tay + `60-cast.sh` |
| 2.14 | V1 — năng lực giọng của đầu xe (đọc) + K1–K3 Kiki | đọc | `70-voice.sh` [B] |
| 2.15 | H1 — Waze/app có activity trung chuyển vào ô | đọc + 1 lần mở app | tay |
| 2.16 | **V1 — GIỌNG NÓI THẬT trong xe (T18/T25)** | ghi nhẹ + **pha 3 khi xe lăn bánh** | `70-voice.sh` |
| 2.17 | **H2 — vòng đời màn ảo của ô** | trung bình | `90-collect.sh` [A2] + tay |
| 2.18 | **HAL grab-list — quét TỪNG control (id↔route↔device↔nhận/chặn)** | **ghi vào THÂN XE** (có denylist) | `71-hal-sweep.sh` |
| 2.12 | **X1/ARCH-🚗 — sống chung chiếu-cụm** | **cao nhất** | `60-cast.sh` |
| 2.13 | D-emu — app từ chối màn phụ | trung bình | tay |

> **Thứ tự CHẠY trong `run-all.sh`** (khác thứ tự đánh số của tài liệu, có chủ ý):
> `00 → 10 → 20 → 40 (OTA) → 30 (hồ sơ) → 50 (phím) → 70 (giọng) → 60 (cụm) → 90 (gom)`.
> **Giọng (70) đứng TRƯỚC cụm (60)** vì nó cần ba trạng thái xe khác nhau (tắt máy · nổ máy · lăn bánh) mà bước
> cụm không chạy chung được — và nếu cụm kẹt phải tắt máy thì 25 câu đo giọng mất trắng.

---

### 2.1 — S2 · Vòng kiểm quyền (ĐỌC)

- **Mục tiêu**: 5 điều kiện của `PermissionPreflight` trên xe thật đang ở trạng thái nào.
- **Điều kiện**: adb nối được; chưa cần mở app.
- **Bước**: `10-baseline.sh` (mục [C]) → `10-permissions.txt`. Sau đó mở app đọc dòng trạng thái ở
  **Cài đặt › Hệ thống & quyền**.
- **Quan sát / kỳ vọng**: dòng trạng thái trong app phải **khớp** với `settings get` ngoài shell. Toast
  *"Kênh điều khiển cửa sổ…"* chỉ được nổ **một lần mỗi phiên tiến trình** (U8(b) đã vá — `AtomicBoolean`).
- **Pass**: khớp + toast không lặp mỗi lần mở Home. **Fail**: app nói "đủ" trong khi `settings get` nói thiếu
  (hoặc ngược lại) ⇒ chép cả hai vế vào biên bản.
- **Mang về**: `10-permissions.txt`, ảnh màn Hệ thống & quyền.
- **Hoàn tác**: không có gì để hoàn (chỉ đọc).

### 2.2 — W1 · 10 datum NEEDS_CAR + L-RE / L-RE2 (ĐỌC)

- **Mục tiêu**: đóng danh sách nợ "chưa kiểm trên xe" bằng **số thật**, không bằng cảm giác.
- **Sự thật kỹ thuật phải biết trước** [ĐO từ source]: giá trị datum đọc qua **reflection trong tiến trình app**
  (`BydHalGateway` → `BydHal`). **KHÔNG có đường shell nào đọc hộ** — `HalGateway.settingGet` hiện trả `null`,
  và adb không gọi được `android.hardware.bydauto.*`. ⇒ **giá trị đọc bằng MẮT trên màn Kachi.**
- **Bước**:
  1. `20-datums.sh` → sinh `20-datums.md` (123 datum · 64 nút · phân loại đường nối · **10 datum NEEDS_CAR**
     xếp riêng ở §1 — sinh bằng máy từ `:core`, không chép tay).
  2. Trên xe: mở **bộ chọn ô** (ngăn kéo) → xem giá trị hiện trên từng thẻ; hoặc đặt datum cần đo vào một ô.
  3. Điền cột **Giá trị đo**. Màn hiện `—` ⇒ ghi `null` (HAL trả null), **không để trống**.
- **Kỳ vọng**: 10 datum NEEDS_CAR = `target_soc` · `coolant_temp` · `tyre_t_fl/fr/rl/rr` ·
  `gps_lat/lon/elevation/heading`. Bốn GPS + `target_soc` [ĐO] đang là `BindingRoute.None` (chưa có đường đọc)
  ⇒ **ra `null` là ĐÚNG bản này**, không phải lỗi; ghi `null (None route)`.
- **L-RE2 — 3 cặp dùng chung feature-id** (script tự liệt kê ở `20-datums.md §4`): `cam`/`camera_view` (3001) ·
  `headl`/`headlight_mode` (1276153912) · `brightness_gear`/`hud_brightness` (1276174360).
  ⚠ **CHỈ ĐỌC / QUAN SÁT. KHÔNG tự chọn tham số** — tự nghĩ giá trị chính là nguyên nhân bug W2-P0.
- **Pass**: ≥ 1 datum NEEDS_CAR ra số thật, và mọi ô còn lại đều có chữ (số hoặc `null`). **Fail**: bỏ trống.
- **Mang về**: `20-datums.md` đã điền + ảnh lưới bộ chọn.
- **Hoàn tác**: không (chỉ đọc). *Ngoại lệ*: nếu dùng nhánh navopen tuỳ chọn → `adb shell rm -f /data/local/tmp/navopen.jar`.

### 2.3 — U7/U9 · Màu trạng thái THẬT trên bảng lốp / cửa & khoang / radar

- **Mục tiêu**: trả nợ [CHƯA BIẾT] của spec `kachi-car-boards.html` — off-car mọi bánh `UNKNOWN`, dự án **cấm dữ
  liệu giả**, nên màu trạng thái chưa từng được nhìn thấy thật.
- **Điều kiện**: xe đỗ, máy nổ (có nguồn HAL). Ba bảng đặt sẵn vào ô: **Lốp** · **Cửa & khoang** · **Cảm biến đỗ**.
- **Bước** (an toàn, không phá gì):
  1. **Cửa**: mở lần lượt cửa lái → cửa phụ → cốp → nắp sạc. Sau mỗi lần mở, chụp bảng *Cửa & khoang*.
  2. **Kính**: hạ một kính ~½ → xem ô phần trăm có nhảy không (thang 0–100 của
     `tailgate_position`/`sunroof_pos`/`sunshade_pct` [CHƯA BIẾT] — đây đúng là phép đo cần).
  3. **Lốp**: **KHÔNG xì lốp**. Cách quan sát an toàn để thấy màu "non": đọc số kPa/psi của 4 bánh ở nhiệt độ
     nguội rồi so lại sau ~15 phút chạy (áp suất tăng theo nhiệt) — hoặc đơn giản **ghi nhận màu ở áp suất
     bình thường** và xác nhận nó KHÔNG phải màu cảnh báo. Muốn thấy ngưỡng non thật thì chờ dịp thay lốp/bơm
     ở tiệm, **đừng tự xả**.
  4. **Radar**: cho một người đứng lùi dần phía sau xe (số P, phanh tay, người kia ngoài tầm bánh) → xem 8 vùng
     có đổi màu theo khoảng cách không.
- **Kỳ vọng**: mở một cửa ⇒ đúng **một** bộ phận đổi vùng tô + câu kết luận đổi từ *"đã đóng"* sang câu nêu
  bộ phận; **ca-pô cố ý KHÔNG vẽ** (không có datum nào đọc — spec OQ2).
- **Pass/Fail**: sai bộ phận (mở cửa lái mà sáng cửa phụ) = **[P0] bản đồ vị trí sai**, chụp ảnh + ghi ngay.
- **Mang về**: 1 ảnh/trạng thái (đóng hết · từng cửa · cốp · kính ½), giá trị 4 bánh, ảnh radar.
- **Hoàn tác**: đóng lại hết cửa/kính/cốp.

### 2.4 — U8(a) · U10 · U11 (quan sát giao diện)

- **U8(a)** — [CHƯA BIẾT] cần chốt: **ROM DiLink có hiện lại thanh trạng thái Android khi cửa sổ freeform của
  một ô có focus không?** Bước: đặt một app vào ô (chế độ freeform) → chạm vào ô đó → **chụp nguyên màn** →
  nhìn mép trên. Ghi đúng một trong hai: "có thanh trạng thái" / "không".
- **U10** — dấu chấm "chưa kiểm" nay chỉ còn **một** hàm vẽ (`PickerBadge.dot`, màu xám `MUT2`) ở 3 bề mặt:
  bộ chọn · thanh nút · góc tiêu đề ô nhóm. Bề mặt **thứ tư** `WidgetViews.badgeView` (chip CHỮ) **cố ý còn hổ
  phách**, chờ owner. Bước: chụp 3 bề mặt, xác nhận không còn chấm hổ phách lạc ở đâu khác.
- **U11** — câu lỗi OTA phải nói **đúng nguyên nhân**. Đo trong mục 2.5: nếu cài hụt, câu chữ phải phân biệt
  "không nối được kênh điều khiển" (⇒ hướng dẫn bấm **Cho phép**) với "hệ thống từ chối gói cài".
- **Mang về**: ảnh + câu chữ nguyên văn. **Hoàn tác**: không.

### 2.5 — L2 · OTA TRÊN XE THẬT (bản đang cài → bản trong `apk/`)

- **Mục tiêu**: chứng minh đường tự cập nhật chạy trên **mạng của xe**, không phải trên máy ảo.
- **Điều kiện**: xe có mạng ra `api.github.com`; adbd loopback sống; bản trên xe **ký cùng khoá Kachi** (từ 1.41).
- **Bước**: `scripts/vehicle/kachi/40-ota.sh <ip>:5555`
  1. đọc bản đang cài;
  2. đọc **bản đích từ tên tệp `apk/Kachi-*-release.apk`** của repo (§9: không đoán, không hằng số trong script),
     rồi hỏi kênh `dangkhoi/byd-kachi@main/apk` (từ máy bạn) và **so version bằng đúng regex của `UpdateChecker`**;
  3. mở thẳng **Cài đặt › Hệ thống & quyền › Bảo trì › Kiểm tra cập nhật** trên xe;
  4. bấm tải + cài; sau đó script đọc lại `versionName`.
- **Kỳ vọng**: dialog báo bản mới → cài qua dadb (`pm install -r`) → app tự mở lại sau ~5 s → `versionName` =
  đúng bản trong `apk/`. Script tự cảnh báo nếu bản cài được **khác** bản đích (kênh đăng lệch với repo).
- ⚠ **Từ 1.49 APK nặng ~27 MB** (trước 9 MB — `libvosk.so` cho hai ABI). Trên mạng 4G của xe, lượt tải này lâu
  hơn hẳn mọi lượt OTA trước: **ghi lại thời gian thật**, đó là số quyết định "OTA còn dùng được trên xe không".
- **Ca đặc biệt**: xe **đã** ở đúng bản đích ⇒ nút chỉ báo "đã mới nhất" (đó là PASS của đường *kiểm tra*, chưa phải của
  đường *cài*). Muốn test cả đường cài: cài tay bản thấp hơn trước (`adb install -r apk/Kachi-1.46-release.apk`
  nếu còn giữ) rồi chạy lại.
- **Fail hay gặp**: `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ⇒ bản trên xe ký **khoá cũ/debug** ⇒ phải **gỡ rồi cài
  tay 1 lần** (⚠ gỡ là **mất prefs**: chụp cấu hình trước khi gỡ).
- **Mang về**: `40-ota-result.txt` (before/after) · câu chữ nguyên văn app trả lời · `40-logcat-ota.txt`.
- **Hoàn tác**: cài lại bản cũ bằng tay (`adb install -r`) nếu bản mới hỏng nặng.

### 2.6 — S2 · Settings IA v2 ghi THẬT (V-oncar)

- **Mục tiêu**: 4 lời hứa của spec `kachi-settings-ia-v2.html` §6 (V-oncar).
- **Bước + kỳ vọng**:

| # | Việc | Kỳ vọng | Bằng chứng |
|---|---|---|---|
| a | **Tự cấp quyền qua dadb** từ Settings mới | bấm "Cấp quyền/Sửa ngay" ⇒ trạng thái chuyển đủ; lần đầu có hộp **Cho phép gỡ lỗi USB** → bấm Cho phép | ảnh trước/sau + `logcat -s Preflight` |
| b | **Ghế** (`seat_comfort_*`) | đổi mức ⇒ ghế phản ứng thật | `logcat -s SeatComfort` phải có `áp xong: N ghế, mode=…` |
| c | **Lọc bụi PM2.5** | bật ⇒ điều hoà đổi trạng thái | `logcat -s Pm25Filter` có `bật lọc PM2.5 xong` + `read level=…` |
| d | **Biển báo tốc độ + bong bóng** | hiện đúng vị trí trên cụm khi đang chiếu | ảnh cụm |

- ⚠ `SeatComfort`/`Pm25Filter` in `SettingDevice null (off-car / no HAL)` ⇒ **HAL không trả lời** — đó là dữ liệu
  quan trọng, chép nguyên văn, đừng bỏ qua.
- **Hoàn tác**: trả ghế/lọc bụi về trạng thái ban đầu (đã ghi ở baseline).

### 2.7 — U12 · Bộ chỉnh vị trí bong bóng LUÔN HIỆN

- **Mục tiêu**: xác nhận vá U12 — stepper + kéo-thả **luôn dựng**, khi chưa bật chiếu thì **mờ 0.4 + khoá +
  câu nhắc lý do** (không bị ẩn mất như IA v2 bản đầu).
- **Bước**: (1) tắt chiếu → mở nhóm Dẫn đường ⇒ bộ chỉnh phải **thấy được nhưng mờ + có câu giải thích**;
  (2) bật chiếu → bộ chỉnh sáng lên, **kéo bong bóng trong lúc ĐANG chiếu**, nhả tay;
  (3) đóng app, mở lại ⇒ vị trí còn nguyên.
- **Kỳ vọng**: `logcat -s VmOverlayPos` có `gửi VM_BUBBLE_POS x=… y=…`; khi cast OFF thì đúng dòng
  `bỏ gửi vị trí: Cluster Cast OFF (cụm chưa live)` — **đó là hành vi đúng, không phải lỗi**.
- **Mang về**: 2 ảnh (mờ/sáng) + log. **Hoàn tác**: kéo về vị trí cũ.

### 2.8 — U13 / S4 · HỒ SƠ TÀI XẾ LÀ TẤT CẢ (mục lớn nhất của bản 1.47, vẫn chưa ai đo trên xe)

- **Mục tiêu**: chứng minh **đổi hồ sơ = áp applier THẬT**, không chỉ ghi prefs.
- **Cơ chế** [ĐO từ `ClusterNavBridgeReapply.kt`]: `switchProfile` → chụp hồ sơ cũ → đặt con trỏ → áp hồ sơ mới →
  `broadcastLang` → `reapplyAll()` gọi 8 applier: `nav.master` · `nav.clusterMode` ·
  `badge.enabled/upcoming/alertChip/layout` · `bubble.pos` · `seat` · `pm25`.
- ⚠⚠ **Đọc log cho đúng**: tag `ClusterNavReapply` **chỉ ghi khi một applier NÉM** (`Log.w` trong `step()`).
  **Im lặng = không ai ném, KHÔNG phải "không chạy".** Bằng chứng *dương* nằm ở log của chính applier:
  `SeatComfort` · `Pm25Filter` · `VmOverlayPos` · `NavigationSpeedSign` · `NavRepository`.
- **Bước**: `scripts/vehicle/kachi/30-profiles.sh <ip>:5555` — bước này nay **tự chụp prefs trước/sau** qua cầu
  kiểm thử (`--es cmd prefs`), nên câu hỏi *"khoá nào đi theo hồ sơ"* trả lời được bằng **diff hai tệp JSON** chứ
  không bằng cách nhớ 6 thứ. Cầu im ⇒ quay về đúng đường tay cũ (ghi 6 thứ bằng mắt).
  1. **V-mig** — mở *Cài đặt › Hồ sơ tài xế*, chụp danh sách. Xe từng chạy ≤1.46 và **có cảnh** ⇒ mỗi cảnh phải
     thành **một hồ sơ cùng tên**, và hồ sơ nổ máy trỏ đúng cảnh khởi động cũ. Xe **không có cảnh** ⇒ ghi đúng
     "xe không có cảnh" (đó là kết quả hợp lệ, không phải fail).
  2. **V-switch** — ghi 6 thứ trước khi đổi (chủ đề · đơn vị · hình nền · bố cục+nội dung ô · thanh nút · ngôn
     ngữ), chạm **chip hồ sơ** trên thanh trên, chọn hồ sơ khác, script cắt logcat trước/sau và tính độ trễ theo
     mốc dòng applier đầu ↔ cuối.
  3. **Chip 8 + thanh trên** — chỉ còn chip hồ sơ + icon lưới (Apps) + icon bánh răng (Cài đặt); **không còn 5
     nút đổi bố cục**; tối đa 8 chip.
  4. **P7** — tắt máy xe **vật lý** rồi nổ lại (ARCH-🚗 mục 6: **không nhận `adb reboot` làm bằng chứng**).
- **Kỳ vọng chi tiết**:
  - đổi theo hồ sơ: bố cục · nội dung ô · thanh nút · chip · lưới · **chủ đề · đơn vị · hình nền · ngôn ngữ** +
    ảnh chụp cấu hình ClusterNav (biển báo · bong bóng · phím · ghế 0 · lọc bụi · chế độ cụm).
  - **KHÔNG** đổi theo hồ sơ, và đó là **đúng thiết kế**: `cast_enabled` (theo XE — S4-OQ2; **cụm không được tối
    đi/sáng lên vì một cú chạm chip**) · 5 khoá `autostart_*` + `headless_autostart` + `recirc_on_start_enabled`
    (nghĩa là *"khi nổ máy"*) · `theme_choice` (đúng ở lần mở sau) · **`seat_level_1..3`** (nợ S4-SEAT — ghế
    2/3/4 không đổi là **đúng bản này**).
- **Pass**: có dòng applier mới + 6 thứ đổi đúng + cụm **không** nhấp nháy. **Fail**: cụm tối đi/sáng lên khi
  chạm chip ⇒ đúng lỗi S4-OQ2, báo ngay.
- **Mang về**: `30-applier-lines.txt` · `30-switch-timing.txt` · ảnh trước/sau · ảnh danh sách hồ sơ · ảnh
  thanh trên · ảnh sau cold-boot.
- **Hoàn tác**: đổi về hồ sơ ban đầu (chạm chip lần nữa). Mọi cấu hình của hồ sơ cũ **được chụp lại** khi rời nó,
  nên quay về là đủ.

### 2.9 — W5 · Đo phím vô-lăng theo ngữ cảnh camera (TẦNG 1 của CLAUDE.md §14)

- **Mục tiêu**: trả lời 3 câu **bằng số** trước khi code (spec `kachi-camera-context-keys.html` §6):
  Q1 mã phím thật · Q2 phím có tới `onKeyEvent` khi **app cam tiền cảnh** không · Q3 `camera_view` có ăn khi cam
  đang mở không.
- **Bước**: `scripts/vehicle/kachi/50-keys.sh <ip>:5555` — `getevent -lp` → `getevent -lt` (bắt 25 s, bấm 4 phím,
  mỗi phím 1 nhấp + 1 lần giữ) → `logcat -s NavAccess` ở màn thường → **lặp lại khi cam đang mở** → chụp
  `mCurrentFocus` để lấy **package app cam thật**.
- **Đường đo quyết định** [ĐO `NavAccessibilityService.kt:79`]: service log **MỌI** phím DOWN dưới dạng
  `onKeyEvent DOWN keycode=<n> (<tên>)` ⇒ có dòng = phím tới app; không có dòng = **app cam/hệ nuốt trước**.
  Điều kiện: dịch vụ **Hỗ trợ** của Kachi phải đang bật.
- **[CHƯA BIẾT]**: `getevent` có bị chặn trên ROM này không (SELinux / không root). Script tự ghi nhận và nói rõ
  rằng đường logcat vẫn đủ để gán phím.
- **Pass**: điền kín `50-keys-table.md`. **Fail mềm** (Q2 = không): đó vẫn là một kết quả hợp lệ ⇒ **không được
  viết code ngữ cảnh**, ghi "chưa làm được + điều kiện mở khoá" (trace-den-tan-cung.md).
- **Hoàn tác**: đóng app camera.

### 2.10 — W2 · Ba hành động còn nợ trên thân xe

- **Mục tiêu**: kính ½ (tất cả) · **mã bảo trì gạt mưa** · đèn đọc (tất cả).
- ⚠ Đây là mục **ghi vào thân xe**. Chỉ chạy khi owner có mặt và đồng ý từng nút một.
- **Lưu ý từ code**: nút "Kính cửa lái" [ĐO] chỉ **đóng/mở**, **không có nửa** — chưa có đường GHI phần trăm
  (chỉ có đường ĐỌC `getWindowOpenPercent`). Nếu owner muốn "kính ½" thật thì đây là dữ liệu phải mang về, không
  phải thứ tự chế ra tham số.
- **Bước**: bấm từng nút, ghi: xe phản ứng gì · có tiếng cảnh báo không · log gì (`logcat -s Bodywork ActionMacro`).
- **Hoàn tác**: mỗi nút bấm một lần trả về trạng thái cũ (đóng kính, tắt đèn đọc, thoát chế độ gạt mưa theo đúng
  cách của xe).

### 2.11 — S3 · Bốn tính năng chuyển từ màn ClusterNav cũ

Màn cũ đã **gỡ hẳn** (spec `kachi-remove-legacy-screen.html`). Bốn thứ phải còn sống trong Kachi:

| # | Tính năng | Ở đâu | Kỳ vọng | Ghi chú |
|---|---|---|---|---|
| a | **Khung + DPI khi đang chiếu** | Cài đặt › Chiếu màn lên cụm (stepper + chip) | chỉnh 4 mép + DPI ⇒ khung trên cụm co đúng | **V-oncar còn nợ** — máy ảo không có cụm nên khối này bị ẩn |
| b | **Preview cụm nhúng** | cùng nhóm | vẽ đúng khung đang chiếu | |
| c | **Học phím mới** | Cài đặt › Phím vô-lăng | bấm phím ⇒ hộp đặt tên hiện ngay | cùng đường với 2.9 |
| d | **Tự khởi động nền (headless)** | Cài đặt › Hệ thống & quyền › Khởi động | bật ⇒ sau cold-boot dịch vụ lên mà không cần mở app | đo cùng lượt reboot ở 2.8 |

- **Hoàn tác**: ghi lại 4 mép + DPI **trước khi chỉnh** rồi đặt lại đúng số cũ. ⚠ CLAUDE.md §5: `wm density`/
  `wm size`/`wm overscan` được WM ghi vào `/data/system/display_settings.xml` theo `uniqueId` ⇒ **sống qua cả
  reboot**. Không trả lại là để lại rác vĩnh viễn trên xe.

### 2.12 — X1 / ARCH-🚗 · SỐNG CHUNG VỚI CHIẾU-CỤM (rủi ro cao nhất)

- **Mục tiêu**: bản 1.53 không tái sinh bug P0 **"cửa sổ mồ côi"** (WM thấy / AM không ⇒ launcher không bao giờ
  resume ⇒ **chỉ tắt máy xe mới sạch**).
- **Bước**: `scripts/vehicle/kachi/60-cast.sh <ip>:5555` → gọi lại `scripts/on-car-verify.sh` với
  `PKG=com.byd.launcher` (tệp đó nay nhận PKG qua biến môi trường; **hành vi không đổi**). Nó tự dò mồ côi ở
  5 mốc: baseline → chiếu app thường → chiếu CP/AA → đổi app từ CP/AA (**điểm mồ côi cũ**) → stress 60 s → sau
  khi tắt chiếu.
- **Ánh xạ câu nhắc** (script cũ còn viết "ClusterNav"): *"Mở ClusterNav → tick app nav → CHIẾU LÊN CỤM"* =
  **Kachi › Cài đặt › Chiếu màn lên cụm**; *"bấm TẮT — TRẢ ĐỒNG HỒ"* = nút tắt chiếu/nút nổi.
- **Kỳ vọng**: `FAIL=0`, mọi mốc "cụm SẠCH"; sau khi tắt chiếu, đồng hồ gốc về và `am stack list` trên VD sạch.
- **Quan sát kèm** (không lệnh nào thay được): dừng-chiếu có mượt không · đổi app có giật không · trọng tài task
  có nhường đúng không · U8(a) thanh trạng thái · U12 kéo bong bóng trong lúc chiếu.
- **Mang về**: `60-oncar-verify/` (snapshot từng mốc) · `from-car/castlog/` · `from-car/diag/` (ClusterDiag tự
  chụp sau mỗi lần chiếu) · ảnh cụm.
- **Hoàn tác**: **TẮT CHIẾU → TRẢ ĐỒNG HỒ**. Cụm kẹt ⇒ tắt máy xe rồi nổ lại (và ghi đúng chuỗi thao tác đã dẫn
  tới kẹt — đó là dữ liệu quý nhất của cả buổi).

#### 2.12.X2 🚗 — Regression cast: chiếu app KHÔNG lên cụm (bản sửa off-car 2026-09-14, CHỜ ĐO)

> Bối cảnh [ĐO] phiên tối 14/09 (`docs/diagnostics/carlog-kachi-20260914-2044/session-findings.md §X2`): cụm =
> **display 2** `fission_bg_xdja`, mà SimpleCast hardcode `--display 1` + không có đường lùi khi VD của uid khác
> chặn `am start` (`Permission Denial … launchDisplayId`). Đã sửa: dò display động (fission/xdja) + rơi về
> `am display move-stack` (đường proven ClusterCast R2). **Chưa đo trên xe** — buổi kế xác nhận:

- **Dò display**: bật Cast → `logcat -s SimpleCast` phải thấy dòng `cluster display: 1 → 2 (dò fission/xdja)`
  (hoặc số cụm thật). Nếu vẫn `= 1` mà cụm là 2 ⇒ lệnh dò hỏng — chụp `dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja'`.
- **Chiếu lên được**: Kachi › Cài đặt › Chiếu màn lên cụm → chiếu VietMap (hoặc Maps). Kỳ vọng: app hiện **trên
  cụm**, `am stack list` có task của app trên `displayId=2` (KHÔNG còn nằm lại display 0). Nếu am-start bị chặn,
  log phải có `cast R1 did not land … → R2 move-stack fallback` rồi `am display move-stack <stack> 2`.
- **Geometry + bóng VietMap tự hiện**: sau khi cast bám VD, mục **khung/DPI** (§2.11a) phải hiện nút chỉnh, và
  bộ chỉnh **bóng VietMap** (§2.7) mở khoá. Cả hai gate theo state cast ⇒ chỉ lên khi cast THẬT bám VD.
- **SpeedBadge (audit đi kèm)**: `dumpsys display` — ghi lại **display 1 có tồn tại không**. Biển báo tốc độ
  (`SpeedBadgeOverlay`) còn hardcode `CLUSTER_DISPLAY_ID=1` (fallback PRESENTATION cứu khi display 1 vắng). Nếu
  display 1 TỒN TẠI nhưng ≠ cụm ⇒ badge gắn nhầm màn → cần sửa (mở việc mới, không nằm trong X2 code lần này).
- **Hoàn tác**: TẮT CHIẾU → TRẢ ĐỒNG HỒ (như §2.12).

### 2.13 — D-emu · App TỪ CHỐI màn phụ

- **Mục tiêu**: đóng mục [GIỚI HẠN] chỉ verify được trên xe. Trên máy ảo, Waze/Maps báo *"does not support launch
  on secondary displays"* rồi **nhảy toàn màn**.
- **Bước**: đưa Waze (hoặc GMaps) vào một ô trên Home của Kachi → quan sát.
- **Kỳ vọng**: ghi đúng một trong ba: "vào ô bình thường" · "nhảy toàn màn" · "báo lỗi <nguyên văn>".
- **Hoàn tác**: gỡ app khỏi ô.

---

### 2.14 — V1 · Đo năng lực GIỌNG NÓI của đầu xe (ĐỌC, 10 phút — tầng 1 của CLAUDE.md §14 cho voice command)

- **Mục tiêu**: 4 số đo quyết định hướng V1 (xem backlog V1): app thường ghi âm được không · có dịch vụ nhận dạng
  giọng của hệ thống không · có TTS tiếng Việt không · CPU/RAM còn bao nhiêu cho ASR tại máy.
- **Bước** (adb, chỉ đọc):
  ```bash
  adb shell "pm list packages | grep -i 'google\|gms\|speech\|tts\|iflytek\|baidu\|byd.*voice'"
  adb shell settings get secure voice_recognition_service
  adb shell settings get secure tts_default_synth; adb shell "pm list packages | grep tts"
  adb shell dumpsys media.audio_flinger | grep -i -A3 "input\|record"     # có input stream/mic nào cho app thường không
  adb shell dumpsys audio | grep -i "mic\|record\|input"
  adb shell "cat /proc/cpuinfo | grep -c processor; cat /proc/meminfo | head -3"
  adb shell dumpsys package com.byd.launcher | grep -i "RECORD_AUDIO"          # quyền có xin/cấp được không (1.47 CHƯA xin; từ 1.49 CÓ xin — xem §2.16)
  ```
- **Thao tác tay**: mở app Ghi âm/Recorder có sẵn (nếu có) nói 3 giây rồi phát lại ⇒ mic hoạt động cho app thường
  [ĐO]; giữ nút mic vô-lăng khi Kachi ở tiền cảnh ⇒ `logcat -s NavAccess` phải thấy keycode (đã proven).
- **Kỳ vọng / ghi nhận**: từng dòng `[ĐO]` có/không; nếu không có RecognitionService ⇒ chỉ còn đường ASR tại máy (Vosk);
  nếu không có TTS VI ⇒ phản hồi bằng âm báo + chữ; CPU < 4 lõi hoặc RAM trống < 500 MB ⇒ dùng mô hình Vosk nhỏ + ngữ pháp.
- **Mang về**: `carlog/voice-capability.txt` — **`70-voice.sh` mục [B] tự sinh tệp này**, không phải gõ tay bảy
  lệnh ở trên (danh sách trên giữ lại để biết tệp gồm những gì). Kèm 1 ảnh màn ghi âm. **Hoàn tác**: không.

- **K1–K3 (Kiki, xem `kiki-car-RE-2026-09-14.md` §8 — lệnh nguyên văn ở đó)**: K1 bản Kiki + trạng thái giấy phép
  trên xe (`dumpsys package ai.zalo.kiki.car | grep version`); **K2** chốt dấu hỏi lớn nhất: `am start` vào
  `CarMainActivity` với extra `text_command` có thi hành lệnh chữ thật không (đọc §8.2 trước — chỉ lệnh vô hại
  như "mấy giờ rồi"); K3 `KikiAutoWakeService` (action `ai.zalo.kiki.car.autowake`) có bật được từ ngoài không.
  Mang về: `carlog/kiki-K1K2K3.txt` + ảnh màn Kiki sau K2.

### 2.15 — H1 · Waze/app có activity trung chuyển vào ô (ĐỌC + 1 lần mở app — sau nghiên cứu 2026-09-14)

- **Mục tiêu**: chốt 3 điều để quyết định đường lùi "màn ảo bị đẩy ⇒ cửa sổ tự do trên màn chính": (1) Kachi trên xe chạy uid nào
  (`dumpsys package com.byd.launcher | grep userId` — nếu là system thì gate màn ảo bị bỏ qua, không cần đường lùi); (2) ROM DiLink
  có cho cửa sổ tự do (`windowingMode 5`) trên display 0 không và `am task resize` có tác dụng không (DL5: [ĐO dashcast] no-op);
  (3) Waze mở vào ô rồi có bị đẩy ra như máy ảo không.
- **Bước**: 9 lệnh nguyên văn ở `waze-into-slot-research-2026-09-14.md` §7 (đọc trước; mọi lệnh `am` có hoàn tác `am force-stop`).
- **Mang về**: `carlog/h1-waze-slot.txt` + logcat `ActivityTaskManager` quanh lúc mở + 1 ảnh. **Hoàn tác**: force-stop Waze, về Kachi.


### 2.16 — V1 · GIỌNG NÓI THẬT TRONG XE (T18 + T25 — mục dài nhất của buổi này)

- **Mục tiêu**: bốn số mà off-car **không thể** cho, và cả V1 đang chờ chúng:
  (1) **tỉ lệ nghe đúng** bằng giọng người ở ba mức ồn; (2) **độ trễ** mic-mở → có chữ;
  (3) **CPU/RAM** một phiên nghe chiếm (Vosk giải mã **tại máy** — đây là cái giá của nó);
  (4) **lượt 2 (từ vựng mở)** có đọc ra tên bài / điểm đến không (V1.1, R15–R17).
- **Điều kiện**: bản 1.53 đã cài; quyền + mô hình xong (bước A/A2 dưới); có người thứ hai cho pha 3.
- **Script**: `scripts/vehicle/kachi/70-voice.sh <ip>:5555` (chạy được riêng từng pha: `PHASES="1"`).

**A. Quyền RECORD_AUDIO** — script tự kiểm và tự cấp (có cổng xác nhận):

```bash
adb shell dumpsys package com.byd.launcher | grep -i RECORD_AUDIO     # phải thấy granted=true
adb shell pm grant com.byd.launcher android.permission.RECORD_AUDIO   # hoàn tác: pm revoke …
```

Đường trong app cũng làm đúng lệnh đó: [ĐO] `PermissionPreflight` khai `pm grant $PKG
android.permission.RECORD_AUDIO` cho điều kiện `MICROPHONE` — tức nút **"Cấp quyền/Sửa ngay"** ở
*Cài đặt › Hệ thống & quyền* và lệnh adb là **một đường**, không phải hai. Không có hộp hỏi quyền của Android
vì lệnh đi từ uid shell.

**B. Tải mô hình 32 MB** — *Cài đặt › Hệ thống & quyền › **Nâng cao** › Nhận dạng giọng nói (tại máy)*
→ **Tải mô hình tiếng Việt**.

- ⚠ **KHÔNG có đường cài từ tệp cục bộ.** [ĐO] `VoiceModelStore.install` chỉ đi qua `HttpConn.open`, mà cửa đó
  `require(url.startsWith("https://"))`; `adb push` vào `filesDir` thì bản release **không debuggable** nên
  shell không ghi được. ⇒ **Phải có mạng**: mạng của xe, hoặc **hotspot điện thoại** (nhanh hơn và đo được).
- Ước lượng thời gian cho 32 MB nén (+51 MB giải ra, cần ~105 MB trống): **10 Mbps ≈ 30 s · 4 Mbps ≈ 70 s ·
  1 Mbps ≈ 4,5 phút**, cộng ~10–20 s giải nén + rút từ điển. **Ghi thời gian thật vào biên bản** — nó quyết
  định câu "người dùng thật có chịu ngồi đợi không".
- **Kiểm bằng gì**: hàng Cài đặt đổi thành **"Đã cài vosk-model-small-vn-0.4 · 19529 từ · 51 MB trên đĩa"** và
  nút thành **Gỡ mô hình**. ⚠ Màn Cài đặt **không in chuỗi sha256** — sha được kiểm **trong lúc cài** (hàng
  hiện *"Đang kiểm gói (sha256)…"*); bằng chứng cài xong đúng là **con số 19529 từ**, không phải một chuỗi sha
  chép tay. Gói sai sha ⇒ app tự xoá sạch và quay về "Chưa cài" (không để lại thư mục nửa vời).
- **Pill mic**: [ĐO] `KachiTopStrip.refreshVoicePill` — nút mic trên thanh trên **chỉ hiện khi mô hình đã cài**.
  Vắng nút mic lúc chưa cài là **đúng thiết kế**, không phải lỗi. Chụp một ảnh trước và một ảnh sau.

**C. WAV đối chứng (không cần micro)** — chạy TRƯỚC khi nói, để sau này tách được *"mô hình nghe kém"* khỏi
*"micro/ồn kém"*. Script tự sinh 3 tệp bằng `say -v Linh` + `afconvert` rồi đẩy lên xe:

```bash
say -v Linh -o /tmp/k.aiff "bật đèn đọc"
afconvert -f WAVE -d LEI16@16000 -c 1 /tmp/k.aiff scripts/vehicle/kachi/wav/01.wav
adb push scripts/vehicle/kachi/wav/01.wav /sdcard/Download/kachi-voice-test.wav
```

⚠ Khuôn **bắt buộc**: **PCM không nén · 1 kênh · 16000 Hz · 16-bit**. `VoiceWavProbe.readHeader` từ chối mọi
khuôn khác và **nói rõ sai chỗ nào** — cố ý không có bộ chuyển đổi trong app, vì tự lấy mẫu lại nghĩa là phép
đo chạy qua một tầng mà phiên nghe thật KHÔNG có. Tệp cũng tìm được ở `/sdcard/Android/data/com.byd.launcher/files/`
(đọc được **không cần quyền bộ nhớ** — đường chạy được ở mọi ROM).

**D. Nói THẬT — ba pha, ồn tăng dần** (script dẫn từng câu, tự đọc logcat, tự hỏi Y/N):

| Pha | Trạng thái xe | Số câu | Ai nói |
|---|---|---|---|
| 1 | **ĐỖ, TẮT MÁY**, cửa đóng | 10 câu chuẩn (spec §6.1) | người chạy test |
| 2 | **NỔ MÁY + điều hoà** mức thường, vẫn đỗ (P + phanh tay) | 10 câu, có V1.1 | người chạy test |
| 3 | **ĐANG LĂN BÁNH** | 5 câu **vô hại** (đọc số + nhiệt độ + âm lượng + dẫn đường) | ⚠ **người ngồi ghế phụ**, KHÔNG phải người lái |

Mỗi câu ghi đúng 6 ô vào `voice-accuracy.csv`: `pha · câu · nghe được · ý · đúng? · ms`.

- **"nghe được"** lấy tự động từ `logcat -s KachiVoiceSession` dòng `lượt 1 (ngữ pháp) nghe được: "…"`.
- **"ms"** = hiệu hai **mốc logcat của chính máy**: `KachiVoiceMic: micro mở bằng nguồn N` → `nghe được`.
  Đây là số đo thật, không dính thời gian người bấm Enter. **Video điện thoại quay màn** dùng làm đối chứng cho
  phần *người thấy* (nhả nút → chữ hiện trên màn) — hai số này khác nhau và cả hai đều đáng ghi.
- **"đúng?"** do người vận hành gõ `y/N` sau mỗi câu — máy không tự chấm được *"xe có làm đúng việc không"*.

Câu bắt buộc có trong bộ (mỗi câu khoá một nhánh khác nhau, đừng bỏ câu nào):

| Câu | Khoá điều gì | Kỳ vọng |
|---|---|---|
| `bật đèn đọc và tắt đèn pha` | **câu ghép** — hai vế chạy lần lượt | hai dòng phản hồi, đúng thứ tự |
| `mở khoá cửa` | **cổng xác nhận** — việc rủi ro phải hỏi lại | hộp hỏi; **im lặng KHÔNG bao giờ là đồng ý** ([ĐO] `VoiceSession`) |
| `đặt nhiệt độ hai mươi hai` | **số bằng chữ + nở thanh điệu** | = 22, kể cả khi mô hình nghe "hái mươi hai" |
| `mở youtube vào ô hai` | **V1.1 — ô số N** (R15) | YouTube vào đúng **ô 2**, không nhảy toàn màn |
| `phát bài diễm xưa bằng youtube music` | **V1.1 — tên bài + app đích** | YT Music mở đúng kết quả, **dừng ở nút Play** (không tự phát) |
| `dẫn đường tới … bằng vietmap` | **VietMap deep link** | ⚠ đây là câu quan trọng nhất của T25 — xem khối ⚠ dưới |
| `dẫn đường tới … bằng google maps` | **GMaps trên bản của xe** | `geo:0,0?q=` là đường CHÍNH đã [ĐO]; `google.navigation:ll=` mới ở mức **AWAITING_CAR** |
| `dẫn đường tới … bằng waze` | Waze `waze://?q=…&navigate=yes` | [ĐO] resolve đúng trên máy ảo; **phần tính tuyến chưa ai đo** |

⚠ **VietMap — đo cho đúng câu hỏi.** [ĐO từ nguồn Kiki đã decompile] VietMap **nhận TOẠ ĐỘ, không nhận chữ**:
`vietmaplive://companion/navigation?lat=…&lng=…&poiName=…`. Kachi phải giải tên → toạ độ trước (Geocoder của
máy, rồi mới tới mạng). Vì thế câu hỏi cần trả lời có **ba** mức, đừng gộp làm một:
1. app VietMap có **lên tiền cảnh** không? (đã [ĐO] trên máy ảo — có);
2. nó có **nhận điểm đến** không (tên hiện lên màn)?
3. nó có **tính ra TUYẾN và dẫn đường thật** không? ← đây mới là thứ chưa ai biết.
Ghi đúng ba dòng, đừng viết "chạy được"/"không chạy".

**E. CPU / RAM trong lúc nghe** (script tự chạy, nhưng nhớ nói một câu DÀI lúc đó):

```bash
adb shell "top -n 1 | grep byd.launcher"
adb shell "dumpsys meminfo com.byd.launcher | grep TOTAL"    # so trước / trong khi nghe
```

Mô hình nạp bằng **mmap** (`KachiVoiceEngine: nạp mô hình … trong 114–125 ms` trên máy ảo) ⇒ phần lớn RAM là
**bộ nhớ tệp**, không phải heap. **Đọc dòng `TOTAL`, đừng đọc mỗi `Dalvik Heap`** rồi kết luận "nhẹ".

**F. Phím vô-lăng "Kachi nghe"** — *Cài đặt › Phím vô-lăng* → gán đích **"Kachi nghe (tại máy)"**.
⚠ **KHÔNG gán phím 328**: [ĐO] nút mic 328 đang thuộc **Kiki** và đang chạy tốt trên xe — CLAUDE.md §6 cấm đảo
thứ đang chạy tốt ngoài hiện trường. Gán **tạm một phím KHÁC** (lấy mã từ mục 2.9), đo xong thì **trả lại**.
Đây cũng chính là dữ liệu để đóng **OQ2** của spec voice.

- **Pass**: `voice-accuracy.csv` kín 25 dòng (hoặc ghi rõ dòng nào chưa đo và vì sao).
- **Fail mềm hợp lệ**: tỉ lệ thấp **cũng là kết quả** — nó chỉ sang nhánh §4 (đổi mô hình / thêm cụm từ), không
  phải một buổi test hỏng. Thứ KHÔNG được làm: bỏ trống ô rồi nhớ mang máng.
- **Mang về**: `voice-accuracy.csv` · `voice-capability.txt` · `logcat-70.txt` · `70-logcat-utterances.txt` ·
  `70-meminfo-*.txt` · `70-top-listening.txt` · `shot-70-*.png` · `diff-70.txt` · video quay màn.
- **Hoàn tác**: `pm revoke … RECORD_AUDIO` nếu owner không muốn giữ quyền · **Gỡ mô hình** trong Cài đặt (trả
  lại 51 MB) · xoá WAV thử: `adb shell rm -f /sdcard/Download/kachi-voice-test.wav
  /sdcard/Android/data/com.byd.launcher/files/kachi-voice-test.wav` · trả phím vô-lăng về đích cũ.

### 2.17 — H2 · VÒNG ĐỜI MÀN ẢO CỦA Ô (rò `kachi-slot-*` + khung đóng băng)

- **Mục tiêu**: hai lỗi đã vá off-car ở 1.53 có thật sự hết trên ROM của xe không.
- **Bất biến phải giữ** [ĐO `SlotVdOwner`]: **mỗi ô nhiều nhất MỘT màn ảo sống**.

**H2·1 — không rò màn ảo.** Đếm và so với số ô App đang có trên Home:

```bash
adb shell "dumpsys display | grep -o 'kachi-slot-[0-9]*' | sort -u"
adb shell "dumpsys display | grep -o 'kachi-slot-[0-9]*' | sort -u | wc -l"   # = số ô App
adb shell "dumpsys activity activities | grep -c KachiHomeActivity"           # màn Kachi đang sống
adb logcat -s KachiVd                    # mỗi lần tạo/giải phóng đều có một dòng, kèm tên VD
```

Đếm lại sau **từng** việc dưới đây (mỗi việc một lượt đếm, đừng gộp):
1. **đổi bố cục** (đổi số ô);
2. **đổi hồ sơ** (chip hồ sơ — màn Kachi dựng lại toàn bộ ô);
3. **reboot** (tốt nhất là tắt máy vật lý, cùng lượt với §2.8 P7).

- **Pass**: số VD **duy nhất** = số ô App ở cả ba lượt. **Fail**: nhiều hơn ⇒ đúng bug H2·1
  ([ĐO] trước vá trên máy ảo: **4** VD cho **2** ô, 2 cái ở `state OFF`) ⇒ chép `kachi-vd.txt` + `logcat -s
  KachiVd` nguyên văn, **đừng vá tại chỗ**.
- ⚠ Đọc `KachiVd` cho đúng: dòng giải phóng mang **mã lý do ASCII** (`slot-taken` · `slot-released` ·
  `owner-gone`) — cố ý không dịch, để hai máy khác ngôn ngữ grep được bằng MỘT chuỗi.

**H2·2 — khung không còn đóng băng.** App trong ô chết thì `SurfaceView` giữ **khung hình cuối** ⇒ ô trông còn
sống, mà **không có tín hiệu hệ thống nào** báo về (màn ảo còn, mặt vẽ còn, chỉ task biến mất) ⇒ phải **đo**:

```bash
adb shell am force-stop <gói app đang nằm trong ô>
```

- **Kỳ vọng**: trong **≤ 10 giây** ô đổi sang nhãn **"App đã đóng — chạm để mở lại"**, và **chạm vào mở lại
  được**. Con số 10 s không phải ước lượng: [ĐO] `SlotLiveness.PROBE_PERIOD_MS` = 5 s × `DEFAULT_MISSES` = 2.
- **Quan sát kèm**: nhịp đo **ngưng khi màn Kachi khuất** (mở một app toàn màn ⇒ không còn lệnh dò nào chạy).
  Cách kiểm: mở app toàn màn, đợi 30 s, xem `logcat -s KachiVd` có im không. Im = **đúng thiết kế** (nếu không
  thì app đốt một lượt dadb mỗi 5 giây suốt chuyến đi).
- **Pass**: nhãn hiện ≤ 10 s + chạm mở lại được + nhịp ngưng khi khuất. **Fail**: khung đứng hình quá 10 s, hoặc
  chạm không mở lại được, hoặc nhịp vẫn chạy khi màn khuất.
- **Mang về**: `kachi-vd.txt` (do `90-collect.sh` mục [A2] sinh) · ảnh ô trước/sau khi force-stop · `logcat -s KachiVd`.
- **Hoàn tác**: chạm vào ô để mở lại app; không có gì ghi ra ngoài hệ thống.

### 2.18 — HAL grab-list · QUÉT TỪNG CONTROL (id↔route↔device↔nhận/chặn) — `71-hal-sweep.sh`

> **Vì sao có bước này** ([ĐO] 2026-09-14): HAL write **KHÔNG all-or-nothing** — named-method
> (`setBodyWindowCtrlState`/`setAutoCleanAirState`/`setSeatVentilatingState`) CHẠY; feature-id có cái chạy (gió
> `1de0000c`) cái chặn (đèn đọc `0x4f50003a` device 1004 *“no permission”*); AC nhiệt độ *“xe không nhận lệnh”*.
> Phải đo **TỪNG** control rồi ghi lại — đây chính là grab-list §9 của spec `kachi-test-bridge.html`.

**Điều kiện**: cầu kiểm thử SỐNG (bật *Cài đặt › Hệ thống & quyền › Nâng cao › Chế độ kiểm thử qua adb* — §1.1).
Không có cầu ⇒ script tự dừng (không có đường tay cho việc bắn 60+ control).

**Cách chạy**:
```
scripts/vehicle/kachi/71-hal-sweep.sh <ip-xe>:5555          # hỏi hiệu-quả-vật-lý (Y/N) sau mỗi control
AUTO=1  scripts/vehicle/kachi/71-hal-sweep.sh <ip-xe>:5555   # chỉ ghi accepted+hal_line, không hỏi
SKIP="fan temp" …                                           # bỏ thêm vài control
```
Cơ chế: mỗi control bắn qua cầu lệnh `ctl` (đi ĐÚNG applier `CarControlAdapter.actByKind`), đọc kết quả HAL
**trong tiến trình** (`HalWriteProbe`). CSV `carlog/hal-sweep.csv` cột: `id · label · route · device · value ·
accepted · hal_line · ghi_chu_owner`. TOGGLE tự bật→tắt để khôi phục.

**⚠ DENYLIST (không bao giờ tự bắn — owner tự bấm tay)**: `lock, door, trunk, hood, sunroof, sunshade, window,
windows_all, win_lf/rf/lr/rr` (= `CtlSafetyPolicy.CONFIRM_REQUIRED`). Đây là control mở/khoá **thân xe**; bắn mù
qua broadcast là hạ kính / mở cửa khi đang lăn bánh.

- **Pass**: CSV có đủ 64 dòng; cột `route`/`device` khớp `ControlRegistry`; control CHẠY thật (owner nhìn mắt)
  ⇒ `accepted:true` + `hal_line:rc=0`; control bị chặn ⇒ `hal_line` mang chuỗi *“no permission…”*.
- **Fail / cần điều tra**: `accepted:true` mà xe KHÔNG phản ứng (rc=0 no-op âm thầm) — ghi vào cột `ghi_chu_owner`;
  hoặc `route:none` (control chưa map — cần đóng bindingKey trong grab-list §9).
- **Mang về**: `carlog/hal-sweep.csv` → cập nhật `ControlRegistry.bindingKey`/`domain` theo cột route/device thật.
- **Hoàn tác**: TOGGLE đã tự khôi phục; STEP/SELECT owner tự chỉnh lại; control denylist chưa bao giờ bị chạm.
- **[ĐO] emulator-5554 (1.54/55)**: script chạy trọn — 64 control, bắn 52, bỏ 12 denylist; off-car mọi dòng
  `accepted:False · hal_line:off_car` (chứng minh cơ chế; giá trị THẬT phải đo trên xe).

### 2.19 — S5 · ĐẶT KACHI LÀM MÀN HÌNH CHÍNH + sống qua reboot (TẦNG 1 §14 đã xanh, giờ verify UI + P7)

- **Mục tiêu**: (a) nút *Đặt Kachi làm màn hình chính* trong app chạy đúng đường đã proven; (b) **P7** — HOME
  có **sống qua reboot vật lý** không (quyết định công tắc *giữ khi nổ máy* có cần bật mặc định hay không).
- **Bằng chứng đã có [ĐO] DiLink3.0 2026-09-14 (shell thô, tầng 1)**:
  ```
  # component đích (thay bằng applicationId thật nếu khác)
  COMP=com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity
  cmd package set-home-activity "$COMP"                                             # ⇒ Success
  cmd package resolve-activity --brief -a android.intent.action.MAIN \
      -c android.intent.category.HOME                                              # ⇒ $COMP (hoặc packageName=com.byd.launcher)
  # bấm nút Home vật lý → Kachi lên
  ```
  Lệnh chạy từ shell uid 2000; kênh dadb loopback của app chạy **cùng uid** ⇒ nút trong app dùng đúng đường này.
- **Bước UI**: mở **Cài đặt › Hệ thống & quyền › Màn hình chính**. Nếu dòng trạng thái hổ phách *"Chưa — hệ
  thống đang dùng &lt;gói&gt;"* ⇒ bấm **Đặt Kachi làm màn hình chính**. Kỳ vọng: nút đổi *"Đang đặt…"* rồi câu
  *"Đã đặt — bấm Home để về Kachi"*, dòng trạng thái chuyển xanh, nút biến mất. Kênh shell chưa cấp ⇒ câu *"Cần
  kênh shell — xem hàng Kênh điều khiển cửa sổ ở trên"* (cấp "Cho phép gỡ lỗi USB" rồi thử lại).
- **P7 — reboot vật lý**: đặt HOME xong → **tắt máy bằng nút nguồn** (không `am`/`reboot` mềm) → nổ lại → bấm Home.
  - Kachi vẫn lên ⇒ ROM GIỮ HOME qua reboot ⇒ công tắc *giữ khi nổ máy* để **mặc định TẮT** là đúng.
  - Kachi KHÔNG lên (ROM reset về launcher gốc) ⇒ bật công tắc *Giữ Kachi làm màn hình chính khi nổ máy* rồi
    reboot lại; kỳ vọng lần này Kachi lên (đường khởi động `KachiAutostart` đặt lại HOME một lần). Ghi kết quả.
- **Pass**: nút đặt được HOME (resolve khớp), bấm Home về Kachi; ghi rõ HOME có/không sống qua reboot.
- **Fail / điều tra**: `set-home-activity` in `Success` mà `resolve-activity` vẫn launcher khác ⇒ chép nguyên
  output resolve (app cũng hiện nó ở câu *Failed*); hoặc một launcher khác giành lại HOME sau vài giây.
- **Mang về**: output hai lệnh trên (trước/sau), ảnh hàng *Màn hình chính*, kết quả reboot vật lý (câu trả lời P7).
- **Hoàn tác**: đặt lại launcher cũ nếu owner muốn: `cmd package set-home-activity <gói-launcher-cũ>/<activity>`.

## 3. THÔNG TIN BẮT BUỘC MANG VỀ

| # | Thông tin | Lấy bằng | Tệp đích (trong `carlog-kachi-*`) |
|---|---|---|---|
| 1 | Phiên bản Kachi đang cài (name+code) | `adb shell dumpsys package com.byd.launcher \| grep -E 'versionName\|versionCode'` | `connect.txt` · `00-package-kachi.txt` |
| 2 | Model / ROM / Android | `adb shell getprop` | `connect.txt` · `00-getprop.txt` |
| 3 | **VD cụm** + kích thước/DPI thật | `adb shell dumpsys display` · `dumpsys window displays` | `00-display.txt` · `00-window-displays.txt` |
| 4 | Khung hệ thống lúc chưa chạm | `am stack list` · `dumpsys window windows` | `10-am-stack-list.txt` · `10-window-windows.txt` |
| 5 | 5 điều kiện quyền | `settings get secure …` · `appops get` · `cmd package resolve-activity` | `10-permissions.txt` |
| 6 | Cờ freeform / resizable | `settings get global enable_freeform_support` … | `10-settings-flags.txt` |
| 7 | **Bảng 123 datum + 64 nút** có cột giá trị đã điền | `20-datums.sh` + đọc trên màn | `20-datums.md` |
| 8 | Dòng applier khi đổi hồ sơ + độ trễ | `logcat -s SeatComfort Pm25Filter VmOverlayPos …` | `30-applier-lines.txt` · `30-switch-timing.txt` |
| 9 | Kết quả OTA before/after + câu chữ nguyên văn | `40-ota.sh` | `40-ota-result.txt` · `40-logcat-ota.txt` |
| 10 | Bảng phím W5 (scancode · keycode · tới app?) | `getevent -lp/-lt` + `logcat -s NavAccess` | `50-getevent-*.txt` · `50-keys-table.md` |
| 11 | Package app camera thật | `dumpsys window \| grep mCurrentFocus` khi cam mở | `50-focus-camera.txt` |
| 12 | Dò mồ côi 5 mốc + castlog | `60-cast.sh` → `on-car-verify.sh` | `60-oncar-verify/` · `from-car/castlog/` |
| 13 | Tệp ClusterDiag tự chụp | `adb pull /sdcard/Android/data/com.byd.launcher/files/diag` | `from-car/diag/` |
| 14 | Logcat đầy đủ + lát theo tag | `adb logcat -d -v threadtime` | `90-logcat-full.txt` · `90-logcat-kachi-tags.txt` |
| 15 | Ảnh: mỗi nhóm Cài đặt · 3 bảng xe · thanh trên · cụm khi chiếu · freeform | chụp màn (app hoặc `adb exec-out screencap -p`) | `*.png` |
| 17 | **Năng lực giọng của đầu xe** (gói TTS/ASR · `voice_recognition_service` · CPU/RAM · ABI · luồng mic) | `70-voice.sh` [B] | `voice-capability.txt` |
| 18 | **Tỉ lệ nghe đúng bằng giọng thật, 3 pha ồn** — cột: `pha · câu · nghe được · ý · đúng? · ms` | `70-voice.sh` [D] | `voice-accuracy.csv` |
| 19 | Logcat NGUYÊN VĂN từng câu nói (cả lượt 1 ngữ pháp lẫn lượt 2 từ vựng mở) | `70-voice.sh` | `70-logcat-utterances.txt` |
| 20 | CPU/RAM trước và trong khi nghe | `top` · `dumpsys meminfo` | `70-top-listening.txt` · `70-meminfo-*.txt` |
| 21 | **Màn ảo của ô**: tên + số lượng `kachi-slot-*` · số màn Kachi sống · nhật ký `KachiVd` | `90-collect.sh` [A2] | `kachi-vd.txt` |
| 22 | H1: Kachi chạy **uid nào** + Waze có ở lại ô không | `waze-into-slot-research…md §7` | `h1-waze-slot.txt` |
| 23 | K1–K3 Kiki (bản · `text_command` · autowake) | `kiki-car-RE-2026-09-14.md §8` | `kiki-K1K2K3.txt` |
| 24 | **Tự động, mọi bước**: logcat nền · ảnh sau mỗi thao tác · diff dumpsys · thời gian lệnh | bộ script tự sinh | `logcat-<bước>.txt` · `shot-<bước>-NN.png` · `diff-<bước>.txt` · `session-notes.txt` |
| 25 | JSON của cầu kiểm thử (state · prefs · profiles · diag) | `k_test` trong `_common.sh` | `test/*.json` · `from-car/test/` |
| 16 | **Biên bản pass/fail 42 dòng** | `90-collect.sh` sinh, điền tay | `README.md` |

**Tag logcat cần nhớ**: `ClusterNavReapply` (chỉ lỗi) · `SeatComfort` · `Pm25Filter` · `VmOverlayPos` ·
`NavigationSpeedSign` · `NavRepository` · `NavAccess` · `Preflight` · `ClusterNavBridge` · `CastLifecycle` ·
`ClusterCastBubble` · `KachiAutostart` · `KachiAutostartSvc` · `UpdateRelaunch` · `KachiAppWidget` · `RecircOnStart`.

**Tag MỚI của 1.49–1.53** (bộ script đã đưa sẵn vào logcat nền, `_common.sh` → `KACHI_LOG_TAGS`):

| Tag | Lớp | Dòng đáng giá nhất |
|---|---|---|
| `KachiVoiceSession` | `VoiceSession` | `lượt 1 (ngữ pháp) nghe được: "…"` · `lượt 2 (tự do) … ⇒ "…"` · `câu trả lời xác nhận` |
| `KachiVoiceRec` | `VoiceRecognizer` | `ngữ pháp: N mục (cụm giữ … bỏ … từ mô hình không có …)` |
| `KachiVoiceEngine` | `VoiceRecognizer.VoiceEngine` | `nạp mô hình <đường dẫn> trong N ms` — **cũng là cách gián tiếp biết mô hình đã cài** trên bản release |
| `KachiVoiceMic` | `VoiceCapture` | `micro mở bằng nguồn N (đệm … byte)` · `micro không vào được trạng thái ghi — ROM từ chối?` |
| `KachiVoiceModel` | `VoiceModelStore` | `mô hình sẵn sàng: … (19529 từ)` · `nguồn <url> hỏng: …` |
| `KachiVoiceWav` · `KachiVoiceIntents` · `KachiVoiceGeo` | WAV probe · mở app đích · giải toạ độ | đường V1.1 |
| `KachiVd` | `SlotVdOwner` · `SlotLiveProbe` | `tạo màn ảo kachi-slot-… — ô N · display M · chủ … · đang sống K` |
| `KachiTest` | `KachiTestBridge` | `cmd cmd=… …` (nguyên văn lệnh nhận được) · `AUTO-CONFIRM: <câu hỏi>` · `reply cmd=… ok=… ms=… file=… json=…` |

---

## 4. CÂY QUYẾT ĐỊNH SAU BUỔI TEST

| Kết quả đo | → Làm gì ngay |
|---|---|
| **Có cửa sổ mồ côi** ở bất kỳ mốc nào (2.12) | **[P0] chặn ship.** Giữ nguyên `60-oncar-verify/` + `castlog`, dựng test hồi quy từ dump thật (CLAUDE.md §10) trước khi vá |
| Đổi hồ sơ **không** có dòng applier nào, dù hai hồ sơ khác cấu hình ghế/lọc bụi | [P1] `reapplyAll` không được gọi → lần theo `PrefsWorkspaceRepository.switchProfile:184` |
| `cast_enabled` **đổi** theo hồ sơ (cụm nhấp nháy) | [P0] ảnh chụp cũ trên đĩa còn mang khoá này → kiểm `ProfileScope.DEVICE_KEYS` + bộ lọc của `applyClusterNav` |
| Migration mất cảnh / mất widget | [P0] đúng họ bug P7×P6 đã từng gặp — **đừng vá vội**, chụp prefs (cần bản `vehicleTest`) rồi mới sửa |
| OTA hụt vì **chữ ký** | gỡ + cài tay 1 lần, ghi vào `apk/README.md`; kiểm câu chữ U11 có nói đúng nguyên nhân không |
| OTA hụt vì **kênh shell** | [P1] kiểm hộp "Cho phép gỡ lỗi USB" + `service.adb.tcp.port`; câu lỗi phải là nhánh `NoShellChannel` |
| W5 Q2 = **phím KHÔNG tới app** khi cam mở | **KHÔNG code** bảng ngữ cảnh (§14 tầng 1 chưa xanh). Ghi "chưa làm được + điều kiện mở khoá" vào backlog W5 |
| W5 Q2 = có, Q1 có mã | mở tầng 2: `car-integration` (lệnh + E2E command-log) → `:core` → UI |
| ≥1 datum NEEDS_CAR ra **số thật** | hạ tier `NEEDS_CAR` → `PROVEN` trong registry + ghi bằng chứng vào spec; đây là cách duy nhất danh sách nợ W1 ngắn lại |
| Datum ra `null` vì `BindingRoute.None` | **không phải lỗi** — ghi vào grab-list "cần đường đọc mới", đừng sửa registry bằng id đoán |
| L-RE2: hai nút cùng id cho hai hiệu ứng khác nhau | tách được ⇒ **phải xoá khỏi** `ControlWriteArgsTest.COLLISION_PENDING_CAR` (có test chống mục rữa) |
| U8(a) có thanh trạng thái | thiết kế ô freeform phải tính chiều cao đó → mở mục backlog UI |
| Bảng lốp/cửa sai bộ phận | [P0] bản đồ `CarPart` sai — sửa ở `:core` (`GroupBoard.doorPlan`), tầng vẽ cấm biết mã `door_` |
| **Giọng thật ≥ 80% đúng** ở pha 1+2 (§2.16) | V1 **đủ cứng để đi tiếp**: mở nhánh **wake word** ("Kachi ơi" — không cần bấm nút) + **TTS đọc câu trả lời** (OQ3 của spec voice). Ghi tỉ lệ vào spec §6 rồi mới viết dòng code đầu |
| Giọng thật **50–79%** | KHÔNG mở wake word (nói hụt 1/4 lần mà không có nút bấm là tệ hơn không có). Việc đúng: **bù cụm từ** vào `VoiceSynonyms`/`VoicePhrases` cho đúng những câu trượt — dữ liệu để bù nằm ở cột *"nghe được"* của CSV |
| Giọng thật **< 50%** | Mô hình nhỏ không đủ cho cabin xe. Hai nhánh, chọn bằng số CPU/RAM đo được ở [E]: **(a)** đổi sang mô hình Vosk lớn hơn nếu còn chỗ; **(b)** ASR đám mây — nhưng đó là **quyết định của owner** (đổi lời hứa *"nhận dạng tại máy, không gửi ra mạng"*), không phải của người viết code |
| Pha 3 (lăn bánh) tụt **> 30 điểm** so với pha 1 | Tiếng ồn lốp/gió là biến quyết định ⇒ nhánh kỹ thuật là **khử ồn / đổi `AudioSource`** (đo `micro mở bằng nguồn N`), KHÔNG phải đổi mô hình |
| Câu **cần xác nhận** chạy thẳng, không hỏi lại | **[P0] chặn ship** — `VoiceSession` hứa *"im lặng không bao giờ là đồng ý"*. Dựng test hồi quy từ đúng câu đó trước khi vá |
| **VietMap `vietmaplive://` dẫn đường THẬT** | Hạ `coordEvidence` VietMap từ `AWAITING_CAR` → `MEASURED` trong `VoiceAppTargets`, ghi bằng chứng vào spec. **Giữ nguyên** đường chữ của GMaps/Waze (CLAUDE.md §6 — không đảo thứ đang chạy tốt) |
| VietMap chỉ **mở app**, không có tuyến | Ghi đúng ba mức đã đo (§2.16 khối ⚠) rồi mở mục backlog *"tìm cửa khác của VietMap"* — **không** tự nghĩ thêm tham số URI (đúng bài học W2-P0) |
| **GMaps `google.navigation:ll=` chạy** trên bản của xe | Đổi đúng một dòng `coordEvidence` của GMaps; đường chữ `geo:` **vẫn là đường chính** cho tới khi có lý do đo được để đổi |
| Mô hình **không tải nổi** trên mạng xe (rớt / quá lâu) | Bật **đường lùi** đã khai sẵn: tải `vosk-model-small-vn-0.4.zip` lên GitHub Release `model-vn-0.4` của `dangkhoi/byd-kachi` (TODO(owner) trong `VoiceModelManifest`). Tên tệp giữ NGUYÊN, sha phải khớp — không khớp thì app tự từ chối, đúng ý |
| **Kachi chạy uid `system`** (§2.15) | **BỎ HẲN H1**: gate màn ảo (`ActivityStackSupervisor:1096-1106`) bị bỏ qua khi chủ màn là SYSTEM ⇒ không cần đường lùi freeform nào. Đóng dòng H1 trong backlog kèm bằng chứng `dumpsys package \| grep userId` |
| Kachi chạy uid thường **và** Waze rơi khỏi ô | H1 còn sống: đi tiếp thiết kế ở `waze-into-slot-research-2026-09-14.md` §3–§4, nhưng **chỉ sau khi** đo được freeform trên display 0 của ROM này |
| **Số `kachi-slot-*` > số ô App** (§2.17) | [P1] H2·1 chưa hết trên ROM xe ⇒ dựng test hồi quy từ `kachi-vd.txt` thật (đúng cách `SlotVdLedgerTest` đã dựng từ dump máy ảo), rồi mới vá |
| Ô đứng hình **> 10 s** sau `force-stop` | [P1] H2·2: kiểm `SlotLiveProbe` có bị `pause()` nhầm không, và lệnh `am stack list` có bị xếp hàng sau lệnh đặt cửa sổ (~3 s) không |
| Cầu kiểm thử **không có trong bản trên xe** | Không phải lỗi của buổi test: chạy toàn bộ theo đường tay (bộ 1.47 vốn thế). Ghi vào backlog để bản sau mang cầu lên — buổi sau sẽ nhanh hơn hẳn |
| Mọi mục đạt | cập nhật §5 `project-context.md` + đổi 🚗 → 🟢 trong Nhóm 6 backlog, **trong cùng phiên** (R2.1) |

**Bước bắt buộc cuối phiên (CLAUDE.md §13.9 + §16)**: ghi phát hiện vào `docs/diagnostics/` **và** cập nhật
`docs/README.md` (INDEX) + `docs/PROJECT-BACKLOG.md` + `.kiro/steering/project-context.md`. Doc mồ côi = không tồn tại.

---

## 5. BẪY ĐÃ BIẾT (mỗi cái đều từng làm mất thời gian thật)

1. **Cắm CarPlay/Android Auto ⇒ đầu xe TẮT WiFi** ⇒ adb từ ngoài rớt **đúng lúc cần dữ liệu nhất**. Cách sống
   chung: app tự chụp qua **dadb loopback** (`ClusterDiag`, chạy tự động sau mỗi lần chiếu) → rút CP/AA ra rồi
   `adb pull` sau. Đừng cố giữ adb khi đang cắm.
2. **adb rớt giữa chừng** (xe ngủ, đổi mạng): `adb connect <ip>:5555` lại; script `run-all.sh` chạy lại được từng
   bước (`ONLY="30 60"`), thư mục carlog cũ được dùng lại trong 8 giờ.
3. **Không root, SELinux**: `getevent` có thể bị từ chối ⇒ dùng đường `logcat -s NavAccess`. `sendevent`/bơm phím
   giả **[CHƯA BIẾT] không dùng** ở buổi này — đo phím thật do người bấm.
4. **Bản release không debuggable** ⇒ `run-as` báo *"not debuggable"*. Cần prefs thì cài `vehicleTest` (cùng khoá,
   không mất dữ liệu), **và nhớ cài lại release trước khi rời xe**.
5. **`am start` vào activity `exported=false`**: DiLink3 chặn shell-uid (manifest đã ghi bài học này). Mở
   `DiagActivity` bằng tay trong app, đừng phụ thuộc adb.
6. **`/sdcard/Android/data/<pkg>/` trên Android 12 (DL5)** có thể chặn shell ⇒ lấy bằng app Quản lý tệp trên xe.
7. **`wm density/size/overscan` sống qua reboot** (`/data/system/display_settings.xml` theo `uniqueId`) — chỉnh gì
   phải ghi số cũ và trả lại.
8. **Đừng dùng `adb reboot` để chứng minh P7/ARCH-🚗 mục 6** — chỉ tắt máy **vật lý** mới tính.
9. **`ClusterNavReapply` im lặng không có nghĩa là applier không chạy** (nó chỉ log khi ném). Đọc log của applier.
10. **`versionName` phải đọc từ máy** — một lần đoán nhầm 0.35/0.36 đã làm hỏng cả buổi chẩn đoán (CLAUDE.md §9).
11. **Hộp "Cho phép gỡ lỗi USB"** ở lần đầu app mở kênh dadb: không bấm ⇒ đường cài/cấp quyền **đứng im không báo
    lỗi rõ** (bytecode dadb: `socketTimeout=0`).
12. **Ba nhóm ô gần trùng tên PM2.5** (mức bụi · trị số · cảm biến) — khi điền bảng datum, đọc kỹ `id`, đừng đọc nhãn.
13. **Đừng bấm thử nút của L-RE2 rồi suy ra tham số.** Tự nghĩ giá trị chính là nguyên nhân W2-P0 (một byte cho
    hai nghĩa đối nghịch: khoá xe / mở cửa).
14. **Chụp một ảnh NGAY TRƯỚC mỗi thao tác tay** (CLAUDE.md §15) — không tái dùng ảnh cũ để đoán toạ độ.
15. **Mô hình giọng 32 MB tải bằng 4G của xe** — không có đường cài từ tệp cục bộ ([ĐO] `VoiceModelStore` chỉ
    nhận HTTPS; `filesDir` của bản release không `adb push` vào được). Dùng **hotspot điện thoại** nếu mạng xe
    yếu, và nhớ: lượt tải hụt **không** để lại thư mục nửa vời — app tự dọn rồi báo "Chưa cài".
16. **`adb shell input text` KHÔNG gõ được tiếng Việt** — nó chỉ bơm ASCII, dấu bị nuốt hoặc thành ký tự lạ. ⇒
    Câu lệnh chữ phải đi qua **cầu kiểm thử** (`--es cmd say --es text '…'`, chuỗi đã bọc nháy) hoặc **gõ tay**
    trên màn. Đừng "thử `input text` xem sao" rồi kết luận bộ phân tích hỏng.
17. **WAV thử phải đúng khuôn 16 kHz · mono · PCM16** — sai khuôn thì `VoiceWavProbe` từ chối và nói rõ sai chỗ
    nào (cố ý **không** có bộ chuyển đổi trong app: tự lấy mẫu lại là cho phép đo chạy qua một tầng mà phiên
    nghe thật không có). `say` của macOS ra AIFF ⇒ **phải** qua `afconvert -f WAVE -d LEI16@16000 -c 1`.
18. **KHÔNG `input tap <x> <y>`** ở bất kỳ đâu: xe **1920×720**, máy ảo 1080×2340 — toạ độ đúng ở nhà là cú chạm
    nhầm hàng trên xe. Deep link `--es open_settings_group` + cầu kiểm thử + tay người, không có đường thứ tư.
19. **Đừng gán phím 328 cho "Kachi nghe"** — [ĐO] nút mic 328 đang thuộc **Kiki** và đang chạy tốt trên xe.
    CLAUDE.md §6: không đảo thứ đang chạy tốt ngoài hiện trường. Gán tạm phím khác, đo xong trả lại.
20. **Công tắc "Chế độ kiểm thử qua adb" tự tắt sau 60 phút** — nửa buổi tự nhiên script "không chạy được nữa"
    thường là chuyện này, không phải lỗi. Và **nhớ tắt nó trước khi rời xe**.
21. **`ClusterNavReapply` im lặng** và **`KachiVd` im lặng khi màn Kachi khuất** là **hai hành vi ĐÚNG** dễ bị
    đọc nhầm thành "không chạy". Cái đầu chỉ ghi khi applier ném; cái sau ngưng nhịp đo có chủ ý để khỏi đốt
    một lượt dadb mỗi 5 giây suốt chuyến đi.

---

## 6. Nguồn / liên quan

- Luật: `CLAUDE.md` §2 §4 §5 §9 §11 §13 §14 §15 · `.kiro/steering/*` (5 tệp).
- Backlog: `docs/PROJECT-BACKLOG.md` **Nhóm 6** (ARCH-🚗 · L-RE2 · S4-OQ2 · S4-SEAT · S4-REAPPLY) + mọi dòng 🚗.
- Spec: `kachi-voice-command.html` (**§5 T18/T25** · §6 V-oncar + 10 câu §6.1 · §7 OQ1–OQ3) ·
  `kachi-profiles-are-everything.html` (§6 V-switch/V-mig) · `kachi-camera-context-keys.html` (§6 kịch bản
  đo phím) · `kachi-car-boards.html` (nợ màu trạng thái) · `kachi-remove-legacy-screen.html` +
  `kachi-settings-ia-v2.html` (V-oncar) · `kachi-icon-set-v2.html` · `apk/README.md` (OTA + mô hình 32 MB).
- Nghiên cứu nền cho §2.15/§2.17: `waze-into-slot-research-2026-09-14.md` (**§7 = 9 lệnh H1 nguyên văn**, §5 =
  khung đóng băng) · `kiki-car-RE-2026-09-14.md` (**§8 = K1–K3**, §8.2 đọc trước khi chạy K2).
- Mã chỉ để ĐỌC khi cần tra lại cơ chế: `voice/VoiceModelStore.kt` + `:core` `VoiceModelManifest.kt` (URL · sha ·
  nơi lưu) · `voice/VoiceWavProbe.kt` (khuôn WAV) · `voice/VoiceCapture.kt` `VoiceSession.kt` (mic · trần 8 s ·
  cổng xác nhận) · `:core` `VoiceAppTargets.kt` (bảng đích + cột **đã đo hay chưa**) · `PermissionPreflight.kt`
  (`pm grant RECORD_AUDIO`) · `SlotVdOwner.kt` `SlotLiveProbe.kt` `:core` `SlotLiveness.kt` (H2) ·
  `KachiTopStrip.kt` (nút mic gate bởi mô hình).
- Playbook cũ (khuôn + bẫy): `oncar-plan-2026-08-19.md` · `oncar-session-2026-08-16.md` ·
  `oncar-handoff-voicekey-2026-08-14.md`.
- Script: `scripts/vehicle/kachi/*` (00 · 10 · 20 · 30 · 40 · 50 · **70 (mới — giọng nói)** · 60 · 90 ·
  `run-all.sh` · `_common.sh` với bộ trợ giúp tự động hoá) · `scripts/on-car-verify.sh` · `scripts/vehicle/carexec.sh`.
- Playbook bản trước: `oncar-playbook-kachi-1.47.md` (giữ để tra lại, **đừng chạy** — nó không có §2.16/§2.17).
