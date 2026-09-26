# Hai lỗi trên xe 2026-09-15 — YouTube co vào giữa · Voice không chạy

> **Trạng thái**: Historical · **Cập nhật**: 2026-09-15 · **Mục đích**: hai lỗi trên xe 2026-09-15 — YouTube co vào giữa · voice không chạy.

- **Ngày:** 2026-09-15 · **Chủ:** dangkhoi · **Xe:** DiLink3 (DL3), Android 10 (API 29), Kachi uid 10135 (KHÔNG platform-sign), bản 1.55 đang cài.
- **Cách lấy:** adb vào xe qua cầu TCP loopback (`No route to host` trực tiếp → bridge `127.0.0.1:5556 → <ip-xe>:5555`). Dump `dumpsys display/window`, logcat, và kéo APK YouTube/GMaps của xe về máy.
- **Mức bằng chứng** (CLAUDE.md §2): **[ĐO]** = đọc dump/log thật; **[SUY]** = suy luận khớp; **[CHƯA BIẾT]** = cần đo thêm.

---

## Lỗi 1 — YouTube (và app "kiểu điện thoại") play bị co vào giữa, không lấp đầy ô — ĐÃ VÁ

### Triệu chứng
Owner: *"youtube khi play bị scale vào giữa, không full"*. Ảnh cụm thật: ô chứa YouTube hiện video nhỏ ở giữa-trên, danh sách phát chiếm cột dọc, hai bên đen.

### Gốc rễ [ĐO]
Ô app dùng **màn ảo** (`VdAppHost` tạo qua `createVirtualDisplay`) làm màn phụ để render app không caption (kiểu Dudu). Dump `dumpsys display` xe:

```
kachi-slot-1 (displayId 28): base 1401 x 748 (ngang), FLAG_PRIVATE, FLAG_NEVER_BLANK, FLAG_OWN_CONTENT_ONLY
  mOverrideDisplayInfo: rotation 3 (270°) → app 748 x 1401 (DỌC)
```
Cửa sổ YouTube trên màn đó: `mBounds=Rect(0,0-748,1401)`, `mRotation=ROTATION_270`, config `port`.

⇒ Màn ảo ô được tạo với cờ `8 | 256` (OWN_CONTENT_ONLY | DESTROY_ON_REMOVAL) — là màn **PRIVATE thường**, KHÔNG phải PRESENTATION. Trên Android 10, màn phụ loại này **XOAY theo hướng app yêu cầu**. YouTube ở khung ~sw598dp (1401×748@200dpi) tự nhận là "điện thoại" và đòi PORTRAIT ⇒ WM xoay màn ảo 270° thành dọc 748×1401. Video 16:9 co vào bề ngang 748px rồi nằm giữa, hai bên đen. **Đây là ROUTE/CỜ sai, KHÔNG phải mật độ (density)** — xem chứng minh dưới.

### Chứng minh cơ chế [ĐO emulator 2026-09-15]
Tạo màn overlay đúng khung xe (`settings put global overlay_display_devices "1401x748/200"` → displayId 50, có `FLAG_PRESENTATION`), rồi `am start --display 50` YouTube:
- **Kết quả: YouTube LANDSCAPE, lấp đầy 1401×748, `mRotation=ROTATION_0`, config `land`** — ở CÙNG sw598dp mà màn ảo ô của xe bị dọc. Khác biệt DUY NHẤT là cờ PRESENTATION. ⇒ density KHÔNG phải nguyên nhân; **cờ PRESENTATION** mới quyết định xoay hay không.

### Bản vá — HAI LẦN (lần đầu SAI, đã sửa)

**❌ 1.56 (FLAG_PRESENTATION) — GÂY LỖI TỆ HƠN, ĐÃ GỠ.** Thêm `FLAG_PRESENTATION` (2) vào màn ảo ô để chống xoay. [ĐO emulator+owner 2026-09-15] màn ô mang PRESENTATION khiến app "tự relaunch" (YouTube: `Shell$HomeActivity`→`WatchWhileActivity`) coi màn ô là **đích không hợp lệ** ⇒ nhảy về display 0, để lại ô **ĐEN THUI** (owner: *"gmaps lên, yt đen thui"*). Tệ hơn lỗi dọc. GỠ hẳn.

**✅ 1.57 (khoá xoay bằng `wm`) — đúng.** Giữ màn ảo ô KHÔNG có cờ PRESENTATION (`8 or 256`) ⇒ app vẫn vẽ vào ô như cũ (hết đen). Chống xoay bằng shell SAU khi tạo VD, TRƯỚC khi mở app (`VdAppHost.maybeLaunch`):
```
wm set-user-rotation lock -d <slotDisplayId> 0
wm set-fix-to-user-rotation -d <slotDisplayId> enabled
```
Khoá hướng màn ô = 0 (ngang) và bỏ qua mọi yêu cầu orientation của app — KHÔNG đổi cờ hiển thị nên không đổi đường composite (không đen). [ĐO emulator API 29 = ABI xe]: sau hai lệnh, WM state của **cả hai** màn ô = `mUserRotationMode=USER_ROTATION_LOCKED · mUserRotation=ROTATION_0 · mFixedToUserRotation=true`, VdAppHost tự áp. Lệnh `set-fix-to-user-rotation -d` nhận trên API 29 (không phải chỉ 30+ như tưởng ban đầu). SpeedBadge revert (bỏ lọc `kachi-slot`) vì màn ô hết là presentation.

### Xác nhận
- **[ĐO emulator]** Hết ĐEN: ô hiện thẻ icon app (không phải đen kịt) khi app không vẽ; màn ô khoá `rotation 0` landscape + `mFixedToUserRotation=true`. Maps chạy đúng trong ô.
- **⚠ Emulator KHÔNG diễn được YouTube trong ô**: bản YouTube emulator có `Shell$HomeActivity` trampoline nhảy về display 0 (khác xe — trên xe `Shell$HomeActivity` **ở lại** ô, đó là nơi thấy lỗi dọc). Nên "video play full ngang trong ô" chỉ xác nhận cuối được trên **xe**.
- **Còn lại (xe):** cài 1.57, mở YouTube vào ô, xem home feed + video có ngang lấp đầy ô không (khoá xoay đã áp ⇒ kỳ vọng ngang).

---

## Lỗi 2 — Voice command không chạy — CHƯA VÁ ĐƯỢC (chờ 1 lượt thu tiếng trên xe), đã thêm CHẨN ĐOÁN

### Đã đo được [ĐO]
- Quyền `RECORD_AUDIO`: **granted=true**.
- Mic **mở bình thường**: `KachiVoiceMic: micro mở bằng nguồn 6 (VOICE_RECOGNITION, 16kHz)`, `VoiceOverlay` vẽ ra. Cổng chặn của phiên nghe (`!hasPermission` → `no_mic`, `!isReady` → `no_model`) **đều qua** ⇒ mô hình sherpa ĐÃ tải trên xe.
- Nền phần cứng mic là **4 kênh** (`platform_set_codec_backend_cfg: ... channels 4`) trong khi app mở mono → framework phải trộn 4→1.
- `.so` sherpa đóng đúng cho **arm64** (ABI xe). `VoiceEngine.build` có log lỗi nếu nạp hỏng — **không có** lỗi đó ⇒ engine dựng được (đã cache trước lượt đo).
- **KHÔNG** có dòng kết quả nhận dạng nào — nhưng lượt đo là kích qua deep-link từ máy, **không ai nói vào mic xe được từ xa**, nên "rỗng" là đúng kỳ vọng của lượt đo này, KHÔNG phải bằng chứng lỗi.

### Kết luận mức chắc
- Engine sherpa **lành** (đóng gói đúng, nạp được, off-car eval 87% — `vn-stt-sherpa-emulator-eval-2026-09-14.md`). ⇒ Lỗi nằm ở **âm thanh mic THẬT trên xe** khi owner nói: nghi (a) mic-array 4 kênh trộn ra mono kém/câm, (b) ồn, (c) mức tín hiệu. **[CHƯA BIẾT]** — không tái hiện được từ xa.

### Đã thêm (để một lượt nói trên xe là chốt được)
1. `VoiceCapture`: log **đỉnh biên độ + RMS** mỗi lượt nghe (`mức micro: đỉnh X/32767 · rms Y · N mẫu`). Đỉnh ~0 = mic câm; đỉnh kịch 32767 liên tục = clip/méo (nghi 4-kênh ghép sai); đỉnh vừa mà ra rỗng = chất lượng/định dạng.
2. `VoiceRecognizer`: log **chữ THÔ sherpa trả về** (kể cả rỗng) — tách "engine ra gì" khỏi "NLU hiểu gì".

### Việc cần trên xe (phiên sau)
Owner nói một câu vào mic xe → đọc 2 dòng log trên. Nếu đỉnh ~0 hoặc clip: sửa đường mic (thử `AudioSource.MIC`, hoặc mở đúng số kênh rồi trộn tay, hoặc chọn 1 kênh sạch bằng `setChannelIndexMask`). Nếu đỉnh vừa mà chữ rỗng: soi định dạng/feature. **KHÔNG ship phán đoán khi chưa có dữ liệu này (CLAUDE.md §14).**

---

## Vật tư kéo về (off-car, scratchpad — KHÔNG commit)
`youtube.apk` (182 MB), `gmaps.apk` (80 MB) — APK YouTube/GMaps của chính xe, để cài vào emulator test. ⚠ [ĐO] không cài đè được lên bản hệ thống của emulator (lệch chữ ký) ⇒ dùng bản YouTube sẵn của emulator để tái hiện cơ chế; APK xe để dành cho AVD sạch nếu cần đúng phiên bản.

---

## Lỗi cài của anh em — "Fail in installation of desktop apps" · và bản APK gọn 1.59

- **Ngày:** 2026-09-15 · **Ai:** một anh em **chép APK vào xe rồi TAP để cài** (không phải `adb install`) → dialog xanh **"Fail in installation of desktop apps"**. Xe **Sealion 6**, **chưa từng cài Kachi**.
- **Bối cảnh:** owner hỏi vì sao APK 53 MB (1.58) và nhờ dựng bản gọn gửi anh em test; đồng thời anh em báo lỗi cài trên.

### Loại bỏ các giả thuyết SAI (nhờ owner chốt dữ kiện)
Owner: *"xe nào cũng đều cài cùng firmware, đều đã adb rồi"* + *"chưa từng cài Kachi"* + *"tap apk trên xe … xong tap install"*. Từ đó:
- **KHÔNG phải lệch chữ ký** — chưa từng có Kachi ⇒ không có gì để đè lệch. (Bỏ giả thuyết signature-mismatch ban đầu.)
- **KHÔNG phải nền tảng / ABI / APK hỏng** — xe owner chạy **đúng APK này** bình thường, mà xe anh em **cùng firmware** ⇒ nếu do APK/nền tảng thì xe owner đã fail. APK cũng cài sạch trên máy ảo arm64 [ĐO].

### Biến số THẬT = ĐƯỜNG CÀI, không phải đời xe [ĐO điều kiện + SUY cơ chế]
- Owner cài qua **adb / OTA** (`pm install -r`) — **chạy**.
- Anh em **TAP tệp APK trên màn xe** → đi qua **trình cài GUI của ROM** — **fail** với "Fail in installation of desktop apps".
- ⇒ Khác biệt DUY NHẤT là **đường cài**. Trình cài GUI của DiLink có cổng chặn mà `pm install` KHÔNG đi qua.
- [SUY] mạnh về **vì sao câu lỗi nói "desktop apps"**: Kachi khai mình là **app launcher/home (desktop)** (manifest có `category HOME`/`DEFAULT`). Trình cài GUI của ROM **từ chối cho một APK tap-vào trở thành app desktop/home** (bảo vệ màn hình chính); còn `pm install` bỏ qua kiểm tra đó. Chưa **[ĐO]** trực tiếp trên Sealion 6 (chưa adb vào máy anh em) — nhưng cách sửa CHẮC vì đường adb của owner chạy trên **cùng firmware**.

### Cách sửa (chắc chắn — không cần đo thêm)
**Cài bằng adb, đừng tap.** Xe đã bật adb sẵn:
```
adb connect <ip-xe>:5555          # đã kết nối rồi thì bỏ qua
adb install -r Kachi-1.59-release.apk
```
`pm install` đi thẳng, bỏ qua cổng GUI ⇒ cài được như máy owner. Muốn biết chính xác trình GUI chặn vì lẽ gì thì đọc dòng `Failure [INSTALL_FAILED_...]` mà lệnh trên in ra — nhưng KHÔNG cần: adb install là xong.

### Bản gọn 1.59 (đã dựng + kiểm) [ĐO 2026-09-15]
- `app/build.gradle.kts`: `abiFilters` còn **`arm64-v8a`** (bỏ `armeabi-v7a`); version **1.59 (60)**. Cơ sở [ĐO]: `carlog-kachi-20260914-2044/00-getprop.txt` — DiLink3.0 `ro.product.cpu.abi=arm64-v8a` (primary 64-bit) ⇒ APK arm64-only cài + nạp lib chắc chắn được (primary khớp; `NO_MATCHING_ABIS` bất khả trên máy này). Sealion 6 là đời **mới hơn** ⇒ cũng 64-bit.
- APK: **36 837 743 B (~35 MB)** ← 53 MB. Chỉ `arm64-v8a` (native 25,8 MB). `0` marker `TEST_`. Cert SHA-256 `9257499b…bb9917` (khớp kênh). targetSdk 37, minSdk 29.
- Smoke máy ảo (`sdk_gphone64_arm64`): `install -r` **Success**, `KachiHomeActivity` resume, `versionName=1.59`, không FATAL/`UnsatisfiedLink`, log usage vẫn ghi ra thẻ.
- Bản 35 MB cũng chép nhanh/ít đứt hơn 53 MB — phụ, không phải nguyên nhân chính (nguyên nhân là đường cài GUI).
