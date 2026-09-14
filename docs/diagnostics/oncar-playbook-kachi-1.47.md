# PLAYBOOK LÊN XE — Kachi 1.47 (48)

> **Loại**: Diagnostics (on-car playbook) · **Trạng thái**: Current · **Cập nhật**: 2026-09-14 · **Chủ**: dangkhoi
> **Mục đích**: lên xe là BẮT ĐẦU ĐO NGAY — không phải mở code ra dò xem tính năng nằm ở đâu.
> **Bộ script đi kèm**: `scripts/vehicle/kachi/` (chạy `run-all.sh`, hoặc từng bước một).
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
| APK 1.47 release | `ls -l apk/Kachi-1.47-release.apk` | đã có sẵn trong repo (8.9 MB). Ký khoá Kachi riêng (L2) |
| `adb` chạy được | `adb version` | dùng bản trong Android SDK của máy; không cần cài gì trên xe |
| repo ở máy mang theo | `git status` sạch | script đọc `:core` để sinh bảng datum — **phải có repo**, không chỉ APK |
| IP xe | **HỎI LẠI OWNER TẠI CHỖ** | KHÔNG hardcode vào script/tài liệu (repo public) |
| Điện thoại có CarPlay/AA | | cần cho mục 2.12 |
| App nav để test (T1) | VietMap đã cài · GMaps zin | GMaps/YouTube bản mới còn nợ (backlog T1) |

### 0.2 Bản nào cài trên xe — quyết định TRƯỚC KHI đi

- **Mặc định: giữ bản `release`** (1.47). Đây là bản sẽ ship, phải test đúng nó.
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
```

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
3. xe đời nào (`ro.product.model` · `ro.build.version.release` — DL3 ≈ Android 10, DL5 ≈ Android 12);
4. **VD cụm là display mấy** (ĐO qua `dumpsys display`, khớp `fission|xdja`; không lấy cờ RAM).

`10-baseline.sh` chụp: `am stack list` · `dumpsys window windows` · focus · `dumpsys activity activities` ·
cờ `enable_freeform_support`/`force_resizable_activities` · 5 điều kiện quyền · danh sách tệp app tự ghi ·
1 ảnh màn · logcat gốc. **Không có baseline thì cuối buổi không ai chứng minh được cái gì đã đổi.**

> Nếu bước 1 hỏng: **DỪNG**, đừng đi tiếp bằng suy đoán. Xem §5 (bẫy) — 90% là CarPlay/AA đang cắm (đầu xe tắt
> WiFi) hoặc adbd không nghe 5555.

---

## 2. THỨ TỰ TEST — RỦI RO TĂNG DẦN

Nguyên tắc xếp thứ tự: **đọc trước → ghi trong app → ghi ra hệ thống → chạm cụm cuối cùng**. Một lần cụm kẹt là
phải tắt máy xe; đừng để nó chặn 20 mục còn lại. Ngoại lệ có chủ ý: **OTA (2.5) đặt sớm**, vì mọi mục sau phải
chạy trên ĐÚNG bản định ship, và migration cảnh→hồ sơ chỉ chạy **một lần**, đúng lượt nâng cấp đó.

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
| 2.12 | **X1/ARCH-🚗 — sống chung chiếu-cụm** | **cao nhất** | `60-cast.sh` |
| 2.13 | D-emu — app từ chối màn phụ | trung bình | tay |

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

### 2.5 — L2 · OTA TRÊN XE THẬT (bản đang cài → 1.47)

- **Mục tiêu**: chứng minh đường tự cập nhật chạy trên **mạng của xe**, không phải trên máy ảo.
- **Điều kiện**: xe có mạng ra `api.github.com`; adbd loopback sống; bản trên xe **ký cùng khoá Kachi** (từ 1.41).
- **Bước**: `scripts/vehicle/kachi/40-ota.sh <ip>:5555`
  1. đọc bản đang cài;
  2. hỏi kênh `dangkhoi/byd-kachi@main/apk` (từ máy bạn) và **so version bằng đúng regex của `UpdateChecker`**;
  3. mở thẳng **Cài đặt › Hệ thống & quyền › Bảo trì › Kiểm tra cập nhật** trên xe;
  4. bấm tải + cài; sau đó script đọc lại `versionName`.
- **Kỳ vọng**: dialog báo bản mới → cài qua dadb (`pm install -r`) → app tự mở lại sau ~5 s → `versionName=1.47`.
- **Ca đặc biệt**: xe **đã** ở 1.47 ⇒ nút chỉ báo "đã mới nhất" (đó là PASS của đường *kiểm tra*, chưa phải của
  đường *cài*). Muốn test cả đường cài: cài tay bản thấp hơn trước (`adb install -r apk/Kachi-1.46-release.apk`
  nếu còn giữ) rồi chạy lại.
- **Fail hay gặp**: `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ⇒ bản trên xe ký **khoá cũ/debug** ⇒ phải **gỡ rồi cài
  tay 1 lần** (⚠ gỡ là **mất prefs**: chụp cấu hình trước khi gỡ).
- **Mang về**: `40-ota-result.txt` (before/after) · câu chữ nguyên văn app trả lời · `40-logcat-ota.txt`.
- **Hoàn tác**: cài lại bản cũ bằng tay (`adb install -r`) nếu 1.47 hỏng nặng.

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

### 2.8 — U13 / S4 · HỒ SƠ TÀI XẾ LÀ TẤT CẢ (mục lớn nhất của bản 1.47)

- **Mục tiêu**: chứng minh **đổi hồ sơ = áp applier THẬT**, không chỉ ghi prefs.
- **Cơ chế** [ĐO từ `ClusterNavBridgeReapply.kt`]: `switchProfile` → chụp hồ sơ cũ → đặt con trỏ → áp hồ sơ mới →
  `broadcastLang` → `reapplyAll()` gọi 8 applier: `nav.master` · `nav.clusterMode` ·
  `badge.enabled/upcoming/alertChip/layout` · `bubble.pos` · `seat` · `pm25`.
- ⚠⚠ **Đọc log cho đúng**: tag `ClusterNavReapply` **chỉ ghi khi một applier NÉM** (`Log.w` trong `step()`).
  **Im lặng = không ai ném, KHÔNG phải "không chạy".** Bằng chứng *dương* nằm ở log của chính applier:
  `SeatComfort` · `Pm25Filter` · `VmOverlayPos` · `NavigationSpeedSign` · `NavRepository`.
- **Bước**: `scripts/vehicle/kachi/30-profiles.sh <ip>:5555`
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

- **Mục tiêu**: bản 1.47 không tái sinh bug P0 **"cửa sổ mồ côi"** (WM thấy / AM không ⇒ launcher không bao giờ
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
  adb shell dumpsys package com.byd.launcher | grep -i "RECORD_AUDIO"          # quyền có xin/cấp được không (bản 1.47 CHƯA xin)
  ```
- **Thao tác tay**: mở app Ghi âm/Recorder có sẵn (nếu có) nói 3 giây rồi phát lại ⇒ mic hoạt động cho app thường
  [ĐO]; giữ nút mic vô-lăng khi Kachi ở tiền cảnh ⇒ `logcat -s NavAccess` phải thấy keycode (đã proven).
- **Kỳ vọng / ghi nhận**: từng dòng `[ĐO]` có/không; nếu không có RecognitionService ⇒ chỉ còn đường ASR tại máy (Vosk);
  nếu không có TTS VI ⇒ phản hồi bằng âm báo + chữ; CPU < 4 lõi hoặc RAM trống < 500 MB ⇒ dùng mô hình Vosk nhỏ + ngữ pháp.
- **Mang về**: `carlog/voice-capability.txt` (gộp output trên) + 1 ảnh màn ghi âm. **Hoàn tác**: không có (chỉ đọc).

- **K1–K3 (Kiki, xem `kiki-car-RE-2026-09-14.md` §8 — lệnh nguyên văn ở đó)**: K1 bản Kiki + trạng thái giấy phép
  trên xe (`dumpsys package ai.zalo.kiki.car | grep version`); **K2** chốt dấu hỏi lớn nhất: `am start` vào
  `CarMainActivity` với extra `text_command` có thi hành lệnh chữ thật không (đọc §8.2 trước — chỉ lệnh vô hại
  như "mấy giờ rồi"); K3 `KikiAutoWakeService` (action `ai.zalo.kiki.car.autowake`) có bật được từ ngoài không.
  Mang về: `carlog/kiki-K1K2K3.txt` + ảnh màn Kiki sau K2.

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
| 16 | **Biên bản pass/fail 30 dòng** | `90-collect.sh` sinh, điền tay | `README.md` |

**Tag logcat cần nhớ**: `ClusterNavReapply` (chỉ lỗi) · `SeatComfort` · `Pm25Filter` · `VmOverlayPos` ·
`NavigationSpeedSign` · `NavRepository` · `NavAccess` · `Preflight` · `ClusterNavBridge` · `CastLifecycle` ·
`ClusterCastBubble` · `KachiAutostart` · `KachiAutostartSvc` · `UpdateRelaunch` · `KachiAppWidget` · `RecircOnStart`.

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

---

## 6. Nguồn / liên quan

- Luật: `CLAUDE.md` §2 §4 §5 §9 §11 §13 §14 §15 · `.kiro/steering/*` (5 tệp).
- Backlog: `docs/PROJECT-BACKLOG.md` **Nhóm 6** (ARCH-🚗 · L-RE2 · S4-OQ2 · S4-SEAT · S4-REAPPLY) + mọi dòng 🚗.
- Spec: `kachi-profiles-are-everything.html` (§6 V-switch/V-mig) · `kachi-camera-context-keys.html` (§6 kịch bản
  đo phím) · `kachi-car-boards.html` (nợ màu trạng thái) · `kachi-remove-legacy-screen.html` +
  `kachi-settings-ia-v2.html` (V-oncar) · `kachi-icon-set-v2.html` · `apk/README.md` (OTA).
- Playbook cũ (khuôn + bẫy): `oncar-plan-2026-08-19.md` · `oncar-session-2026-08-16.md` ·
  `oncar-handoff-voicekey-2026-08-14.md`.
- Script: `scripts/vehicle/kachi/*` · `scripts/on-car-verify.sh` · `scripts/vehicle/carexec.sh`.
