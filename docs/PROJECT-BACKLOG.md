# byd-launcher — Project Backlog

> **Trạng thái**: Current · **Cập nhật**: 2026-09-10 · **Fork** của ClusterNav 2.0 (`byd-cluster-2`) → phát triển dần thành LAUNCHER xe BYD (`com.byd.launcher`, cài song song). Fork từ ClusterNav **v1.38** (versionCode 39). Spec: `docs/specs/launcher-foundation.html`.
>
> **🎯 Owner chốt 2026-09-10 (tối): dự án CHỈ làm LAUNCHER.** ClusterNav 2.0 = nền tảng **DÙNG LẠI** (gom vào mục Cài đặt), đã ổn định trên xe → **KHÔNG track backlog cho nó nữa**; toàn bộ backlog kế thừa đã tách sang `docs/archive/clusternav2-backlog-inherited-2026-09-10.md`. Batch việc mới của owner ở **§L2** (mô hình hiển thị Read/Write + 8 mục: hình nền, thanh action, lấy gió trong, áp suất lốp, vòng kiểm quyền, camera 360, bố cục động).
>
> **🚀 2026-09-10 — ĐÃ PUSH LÊN `github.com/dangkhoi/byd-kachi` (PUBLIC, MIT).** Toàn bộ lịch sử Kachi (fork + P1–P5 + ARCH B0–B6 + Stage 7 SDD) đã lên remote; senior review APPROVED (full 5-module **2202/0**). Danh tính scrub (token công việc cũ) → **dangkhoi** ở cả content LẪN history (`git-filter-repo`) ⇒ **mọi SHA cũ ghi trong backlog (`4918ead`/`38fdb25`/`493b150`…) ĐÃ ĐỔI** (HEAD mới `508e512`); "CHƯA push" ở các dòng dưới nay đọc là **đã push**. Còn nợ: verify **trên xe** (ARCH-🚗 · X1 cast-side · P2/P3/P4 🚗). **W1** off-car DONE + **committed `b2c85f7`** (ĐÃ PUSH 2026-09-10 tối) — nối dữ liệu/điều khiển BydHal thật thay DemoCarData/NoCar; còn 🚗 verify trên xe + on-car grab-list (5 NEEDS_CAR). **2026-09-10 (chiều)**: test emulator → lộ bug host-app-lúc-mở (P-bug2) + giới hạn display phụ (D-emu). **2026-09-10 (tối): GÓI 1 "mở app cho đúng" (P-bug2·U3·U2) DONE off-car, senior APPROVED, commit `3a31205` ĐÃ PUSH** — 5 module **2343/0**; backlog tái phân nhóm 6 chủ đề + bỏ track ClusterNav. Xem `docs/_handoff/session-2026-09-10-emulator-launcher-hosting.md` + `docs/diagnostics/kachi-emulator-app-hosting-2026-09-10.md`.

> **Quy ước status 2 trục** · **Làm (code)**: ✅ xong · 🔨 đang dở · 🔲 chưa làm · ❌ bác/revert · 📋 ngoài tầm/để sau · **Xe (verify)**: 🟢 đã xác nhận trên xe · 🚗 code xong, chờ xe · — không cần xe.
> **Tài liệu gốc**: kế hoạch `docs/specs/kachi-workspace-launcher.html` · kiến trúc `docs/specs/kachi-architecture.html` · dữ liệu xe `docs/specs/kachi-w1-real-data.html` · bản kê khả năng `docs/diagnostics/kachi-capability-catalog-2026-09-10.md` · prototype `docs/prototypes/kachi-workspace.html`.
> **Tái phân nhóm 2026-09-10 (tối)** theo yêu cầu owner: gom theo **CHỦ ĐỀ** (6 nhóm) thay vì theo phiên/phase, để thấy ngay việc nào cùng một mạch. ID cũ (P*, U*, W*, L*, X1, S1, ARCH…) **giữ nguyên** để trace lịch sử.
> **OQ đã chốt (2026-09-08)**: (1) KHÔNG xoay dọc, chỉ ngang; (2) THAY hẳn launcher gốc (bật sau khi mở-app-vào-ô chạy ổn); (3) KHÔNG khoá thao tác/video nào khi xe chạy. Multi-app = **freeform multi-window** (KHÔNG PIP; VirtualDisplay là đường đang dùng cho ô) — cơ chế đã field-proven trong cluster-cast, KHÔNG cần native/AOSP.

---

## Nhóm 1 — NỀN TẢNG & KIẾN TRÚC

| ID | Việc | Làm | Xe | Ghi chú |
|----|------|-----|-----|---------|
| L0 | Fork sang `byd-launcher` (appId `com.byd.launcher`, cài SONG SONG, OTA trỏ repo mới, bỏ APK ClusterNav) | ✅ | — | [ĐO] full test **1983/0**; debug APK `package=com.byd.launcher` vc=39; ClusterNav v1.38 KHÔNG đụng |
| ARCH | Kiến trúc sạch 3 tầng — Domain (`:core system/`) / Data (`:app`) / UI (UDF + ViewModel + StateFlow); tách god-activity; 1 `ShellTransport` (gộp 3→1 chủ dadb); policy thuần (`WindowMutation`/`DisplayOwnershipRegistry`/`AppLocationRegistry`/`FreeformSeedPolicy`/`PrioritySerialExecutor`); input daemon riêng; `AppContainer` manual-DI | ✅ | — | **DONE + PUSHED 2026-09-10** (`byd-kachi` public, MIT; HEAD `508e512` sau scrub danh tính + rewrite history ⇒ SHA cũ `4918ead`..`38fdb25` đã đổi). Full 5-module **~1664/0**, senior APPROVED, **0 file logic cast bị đụng**. SDD `kachi-architecture.html` |
| L2 | **Tên app → "Kachi"** — nhãn app + tiêu đề màn + tiêu đề thông báo ✅ (2026-09-08); **CÒN**: icon launcher riêng · khoá ký + kênh cập nhật riêng | 🔨 | 🚗 | seal `strings.xml` re-pin `8300437c…`→`45fa51a8…` (owner duyệt); test **1983/0**. namespace/appId KHÔNG đổi (giữ liên tục OTA/chữ ký) |
| L-RE | Nợ giải mã mã lệnh xe: giá trị SỐ feature-id theo trim, ngữ nghĩa `set(featureId,…)`, tầng shell đặc quyền | 🔨 | 🚗 | **PHẦN LỚN ĐÃ THU 2026-09-10** → catalog ≈187 feature-id số. Còn lại = verify số/method **theo trim TRÊN XE** (catalog §E) |
| X1 | **Sống chung với chiếu-cụm** (display 1) — chung dadb `5555` + Window Manager toàn cục; launcher = display 0 + VD riêng, cast = display 1, launcher **CẤM chạm display 1** | 🔨 | 🚗 | **Launcher-side XONG** (4 luật: 1 transport · cổng chủ quyền · 1 nơi ghi state bền · trọng tài task). **CÒN cast-side** → gộp vào `ARCH-🚗`. ⚠ Đua density/windowing trên màn cụm = **kẹt cả hai, phải nạp lại firmware** |

## Nhóm 2 — KHUNG WORKSPACE & MỞ APP (hosting)

| ID | Việc | Làm | Xe | Ghi chú |
|----|------|-----|-----|---------|
| P1 | Vỏ **HOME** (`CATEGORY_HOME`, ngang) + máy tính bố cục 1/2/3/4 ô + model ô + lưu cấu hình + lối vào "Cài đặt" | ✅ 09-08 | — | ✅ chạy emulator (ảnh `kachi-p1-emulator.png`); `:core` 13 test xanh |
| P2 | **Ngăn kéo app** + mở app vào ô — dùng lại máy cast (`--windowingMode 5` + `am task resize` theo ô) | ✅ 09-09 | 🚗 | ✅ E2E emulator: ngăn kéo app THẬT → đặt vào ô; `FreeformLaunch`+`ShellAppLauncher` (8 test) · fps/z-order thật = xe |
| P3.1 | **Chiếu app THẬT vào ô** qua VirtualDisplay + shell (kiểu Dudu, không caption, không letterbox) — sideload, KHÔNG cần ký hệ thống | ✅ 09-09 | 🚗 | [ĐO] emulator: GMaps/Clock/VietMap render trong ô trên **display phụ**; chạm forward được. Chạm mượt (daemon thường trú) = việc sau |
| P4.1 | **Nhiều widget trong 1 ô** (1..8, lưới 1/2/3 · 2+2 · 2+3 · 3+3 · 3+4 · 4+4) + chọn nhiều từ ngăn kéo | ✅ 09-09 | — | [ĐO] emulator 5 widget → lưới 2+3 |
| P-bug1 | Cập nhật **tăng dần** (thêm app ô khác KHÔNG mở lại app cũ) + bỏ vòng thử lại khi mở app (hết nháy/nhảy) | ✅ 09-09 | 🚗 | [ĐO] emulator: 2 app đúng ô, không nhảy |
| **P-bug2** | **App trong ô KHÔNG hiện lúc mở** — phải đổi bố cục mới hiện. **ẢNH HƯỞNG CẢ XE** | ✅ off-car 09-10 | 🚗 | **DONE** — spec `docs/specs/kachi-open-app-correctly.html`. Sửa: `applyEmbedSeam` gắn **NGUYÊN KHỐI** 4 kênh + đóng private (viết lại lỗi cũ ⇒ **KHÔNG BIÊN DỊCH ĐƯỢC**) + tách luật render sang `:core WorkspaceRenderPlanner` (test hành vi thật, không quét chữ; kèm test tương-đương-hành-vi-cũ >1000 tổ hợp). **[ĐO] máy ảo**: ô đã lưu app, mở nguội KHÔNG chạm gì ⇒ màn ảo ô **0 → có ngay**, task app đúng cỡ ô, ảnh xác nhận Maps render bản đồ THẬT trong ô. Mutation 3 phép đỏ/chặn đúng chỗ |
| **U3** | **Nút "mở toàn màn" + đường mở app kiểu thường** (owner vướng nhất: *"mở 1 app bình thường không được"*) | ✅ off-car 09-10 | 🚗 | **DONE** — nút **"Ứng dụng"** trên thanh trên (OQ1 duyệt) → ngăn kéo chế độ **mở-thường** (bỏ hẳn mục widget) + mục **Gần đây** (≤12, mới-nhất-trước, **KHÔNG cần quyền nào** — cố ý không dùng quyền truy-cập-sử-dụng vì màn cài đặt trên xe khoá; OQ5). **[ĐO]**: mở app ⇒ đúng `[0,0][1920,1080]`; bố cục + ô **không đổi**; chạy được cả khi CHƯA có kênh shell. ⚠ **Sai lệch spec đã sửa theo SỐ ĐO**: đảo thứ tự 2 đường — đường **API (khung `null`) là CHÍNH**, shell còn là lưới an toàn (đường shell đưa app *từng nằm trong ô* về khung ô cũ = SAI). OQ2/OQ3 owner chốt **không cần** |
| U2 | Bớt nháy khi đưa app đang chạy về ô (KHÔNG mở lại app) | ✅ off-car 09-10 | 🚗 | **DONE mức tối thiểu** (OQ4): đường **không-làm-mới** của `placeApp` dùng `moveToSlot` (chỉ đặt lại khung, tự lùi an toàn nếu app chưa chạy); đường **đặt-mới giữ nguyên**; **`reflow` KHÔNG đụng** (nó cố ý mở lại để nâng cửa sổ lên trước launcher — đổi mù sẽ làm app tụt sau launcher). ⚠ [ĐO] bán kính HẸP: khi có kênh shell thì mọi lệnh freeform bị bỏ qua ⇒ chỉ áp cho ca KHÔNG nhúng |
| **P9** | **Bố cục ĐỘNG 12 cột × 6 dòng** — user tự vẽ từng khung theo lưới, gán app / widget / action mặc định + chọn app tự mở, lưu thành **hồ sơ** riêng ⇒ mỗi người một launcher | 🔲 | 🚗 | **KHẢ THI** (xem §Đánh giá khả thi). Nền có sẵn: chiếu app vào khung BẤT KỲ đã proven + sắp lại app khi đổi bố cục đã chạy; 5 bố cục hiện tại = **trường hợp riêng** của lưới. MỚI: model lưới + trình vẽ khung + kiểm tra (không đè/không hở/khung tối thiểu) + nới trần 4 ô → N ô + nối hồ sơ. Rủi ro: UX trình vẽ + mượt/RAM khi nhiều app |
| D-emu | **[GIỚI HẠN] App-vào-ô cho app TỪ CHỐI màn phụ = chỉ verify được TRÊN XE** | 📋 | 🚗 | Waze/Maps báo *"does not support launch on secondary displays"* → nhảy toàn màn. Kachi **sideload** (không ký hệ thống) + màn ảo **riêng tư** ⇒ không ép được. Trên xe = launcher hệ thống ký nền tảng (như Dudu) mới host. Nhóm hỗ trợ đa-màn (Clock…) chạy được trên emulator |

## Nhóm 3 — DỮ LIỆU & ĐIỀU KHIỂN XE (Read / Write)

> **Định nghĩa nền (owner chốt 2026-09-10)** — mọi "khả năng xe" chia **2 loại**:
> - **READ** = thông tin ĐỌC từ xe (tốc độ, điện năng, áp suất lốp…) → chỉ hiển thị, **KHÔNG bấm được**.
> - **WRITE** = HÀNH ĐỘNG vào xe (mở kính, lấy gió trong…) → **bấm được**.
>
> Cả hai đặt được ở **3 vùng**: **thanh trên** · **widget giữa màn** · **thanh tác vụ**. **Trạng thái luôn cập nhật realtime.** ⇒ gom về MỘT loại "ô khả năng" chung (hiện tại thanh điều khiển = action và widget = đọc đang TÁCH nhau).

| ID | Việc | Làm | Xe | Ghi chú |
|----|------|-----|-----|---------|
| W1 | **Nối dữ liệu + điều khiển xe THẬT** (thay dữ liệu mẫu) — lớp khả năng ở `:core` (**121 datum / 8 nhóm** + **65 nút / 5 kiểu**) + cầu HAL ở `:app` + UI render theo kiểu tile | ✅ off-car | 🚗 | **[DONE 2026-09-10]** senior APPROVED · full 5-module **2302/0** · APK debug sạch. **Gate an toàn ĐÃ BỎ** (owner "tự dùng tự chịu"). **118/123 datum đã nối giá trị**; còn **5 chờ xe** (4 GPS + mục tiêu sạc — không có đường đọc) → danh sách lấy trên xe. Commit `b2c85f7` (**đã push**) |
| **RW0** | **Ô khả năng HỢP NHẤT** — một tile chung render được cả READ (không bấm) lẫn WRITE (bấm), đặt ở thanh trên / widget / thanh tác vụ, cập nhật realtime | 🔨 **2/3 vùng** | 🚗 | **NỀN cho W2·W4·W5.** ✅ **T1** lớp tra cứu khả năng + lớp đơn vị + quyết định lốp (`:core`, **35 test mới**, 1087/0) · ✅ **T3** thanh nút NHẬN trạng thái xe + vẽ được ô ĐỌC · ✅ **T4** bộ dựng ô DÙNG CHUNG (`ControlTileFactory`) ⇒ ô giữa màn nhận được HÀNH ĐỘNG. [ĐO] 5 module **2394/0** · APK sạch · **thử phá 5 phép đỏ đúng chỗ** · máy ảo: thanh nút hiện 4 ô đọc (off-car "—", đơn vị bar) + ô giữa màn "Lấy gió trong" **bấm được** · **không nháy**: bật nút + bấm +2 rồi chờ 12 nhịp trạng thái xe vẫn giữ nguyên. **CÒN**: (a) màn **Tuỳ biến chưa liệt kê mã ĐỌC** ⇒ người dùng chưa tự thêm áp suất lốp vào thanh (phải gieo cấu hình bằng tay để đo) — cần đổi `CustomizePanel` sang `CapabilityCatalog.byDomain()`; (b) **thanh trên** chưa nhận mã khả năng (vùng thứ 3); (c) nhãn ngắn cho datum (đang cắt chữ). Spec §9 |
| **W2** | **Đủ hoá thanh action + lớp GỘP LỆNH** — 7 action owner nêu: cốp · đèn ban ngày · đèn đọc (tất cả) · kính ½ tất cả · kính mở hết · bảo trì gạt mưa · "mở cửa + tắt/mở đèn" | 🔨 **lớp gộp lệnh ✅ off-car 09-11** | 🚗 | **Lớp gộp lệnh DONE** — spec `kachi-action-macros.html`. `ActionMacros` (:core thuần, KHÔNG luồng): bước có thứ tự · chờ giữa bước (400 ms) · **một bước hỏng KHÔNG chết cả gói** · mức bằng chứng = **THẤP NHẤT** trong các bước. 4 gói định sẵn: mở/đóng hết kính (**gộp 4 nút kính riêng tier PROVEN thay vì nút gộp tier OVERDRIVE**) · mở cửa+đèn đọc (yêu cầu #7) · rời xe. Gói lệnh là một **khả năng** ⇒ tự đặt được ở 3 vùng nhờ nền RW0. [ĐO] máy ảo: 3 gói hiện đúng, bấm chạy đủ 4/6 bước, chống bấm kép (3 lần bấm → 1 lượt), app không treo. **Tình trạng 7 mục**: cốp + đèn ban ngày = **khỏi làm** (đã có) · kính mở hết + mở-cửa-kèm-đèn = **✅ xong** · ⚠ **kính ½ tất cả = KHÔNG làm được off-car** ([ĐO] không có đường GHI phần trăm, chỉ có đường ĐỌC `getWindowOpenPercent`; thiếu đúng lệnh **dừng giữa hành trình** → spec §4.5) · bảo trì gạt mưa = **chưa có mã** → 🚗 · đèn đọc "tất cả" = chưa rõ bật 1 hay tất cả → 🚗. ⚠⚠ **[ĐO] nhãn nút SAI đã sửa**: nút mang nhãn "Kính 50%" ghi **ĐÚNG CÙNG lệnh** với kính cửa lái (`setBodyWindowCtrlState(1,state)` — không có nửa) → đổi thành **"Kính cửa lái"** + test khoá **cả họ** (bất kỳ nhãn nút chứa "%" ⇒ đỏ) |
| **W3** | **Tự lấy gió trong khi khởi động** — thêm ô tick ở Cài đặt / Tiện ích, nổ máy thì tự áp | ✅ off-car 09-10 | 🚗 | **DONE** — bám ĐÚNG mẫu ghế/lọc-bụi: khoá `Prefs` mặc định TẮT + `RecircApplier.applyOnStart` + gọi từ `BootSetupService` (đặt SAU 2 bộ đã proven). Ô tick ở **bảng Tuỳ biến dựng bằng code** ⇒ 0 file XML bị sửa (không cần xin đóng dấu lại). ⚠⚠ **KHÔNG hứa nó chạy**: mã `recirc` tier OVERDRIVE = **CHƯA kiểm trên xe owner** (khác ghế/lọc-bụi đã chạy thật) ⇒ UI mang cảnh báo chưa-kiểm. [ĐO] máy ảo: pref lưu bền, nhật ký báo *"HAL không nhận lệnh"* = suy giảm an toàn đúng |
| **W4** | **Widget áp suất lốp** — bảng 4 bánh (áp suất + nhiệt) | ✅ off-car 09-10 | 🚗 | **DONE** — `TyreBoard` (:core, thuần) quyết định non/căng/lệch + `TyreBoardView` (:app, Canvas) vẽ hình xe nhìn từ trên, **từng bánh một số riêng**. ⚠ Đính chính: widget cũ ĐÃ hiện 4 bánh; cái sai thật là nó có **ngưỡng cứng 2.2 viết tại chỗ** = ngưỡng THỨ BA của dự án, lệch với :core — đã xoá. [ĐO] bơm số giả: 240 kPa→**34.8 psi**, 235→34.1, 185→**26.8 + tô hồng-đỏ** (đúng ngưỡng non<2.0); nhiệt 28→**82°F** theo lựa chọn người dùng. Ngưỡng vẫn là số **tôi tự chọn** (OQ1 chờ owner) |
| **W5** | **Camera 360 + tự gán phím vô-lăng theo ngữ cảnh** — mở cam xe thì: tăng âm = cam trước · giảm âm = cam sau · trái/phải vô-lăng = cam trái/phải · giữ nút âm = hiện thân xe trong suốt (kiểu Dudu) | 🔲 | 🚗 | Nút cam + chọn góc **ĐÃ CÓ**. Phần MỚI = **tự gán phím khi app cam lên trước** (dùng lại bộ bắt phím của tính năng gán-phím), nhả khi thoát. App cam = app mặc định của xe |

## Nhóm 4 — GIAO DIỆN & CÁ NHÂN HOÁ

| ID | Việc | Làm | Xe | Ghi chú |
|----|------|-----|-----|---------|
| P3 | **Thanh điều khiển 4 viền** + tuỳ biến + nối ghế/lọc bụi (đã proven) | ✅ 09-09 | 🚗 | ✅ 4 viền, công tắc + tăng/giảm, icon vector (emulator) · hành động thật = xe |
| P4 | **Widget trong ô** (đồng hồ/nhạc + pin/lốp/bụi/tốc độ/trạng thái xe) + thanh trạng thái trên | ✅ 09-09 | 🚗 | ✅ vòng đo/bảng kính/nhạc/tốc độ (emulator) · dữ liệu xe thật = W1 |
| P5 | **Hồ sơ tài xế** + sáng/tối + kéo-thả sắp xếp | ✅ 09-09 | — | ✅ hồ sơ (tạo/đổi) + kéo-thả đổi ô + gỡ ô + chế độ màu (`:core`, 3 test). ⚠ bảng màu NGÀY đủ + nút gạt còn thiếu → P6 |
| U1 | **Bộ icon riêng cho các mục khả năng** | ✅ off-car 09-11 | — | **DONE** — spec `kachi-capability-icons.html`. [ĐO] gốc: 123 mục đọc chỉ dùng **7 icon** vì lấy theo NHÓM; và **59 tệp icon nhưng chỉ 29 được nối** (30 icon đã vẽ mà không dùng tới được). Sửa: `CapabilityIcons` (:core) tra theo **khái niệm** — khớp mã → tiền tố → lùi về icon nhóm, **KHÔNG sửa 123 dòng registry**; vẽ **6 icon mới** (đường · pin · dây an toàn · cảm biến vùng · vị trí · vô-lăng). **[ĐO] 7 → 29 icon phân biệt** (gấp 4,1×); mọi nhóm từ 1 icon lên 2–7 icon; [ĐO] ảnh máy ảo: 30 ô nhóm năng lượng dùng 6 icon khác nhau, **0 ô trống icon, 0 icon lỗi**. ⚠ **Còn tồn**: phân biệt theo HỌ chưa theo từng ô (tia sét vẫn dùng cho 11 ô); cặp đối lập (nhiệt cao/thấp, giờ/phút) chưa phân biệt → OQ1. **Việc phát sinh đã sửa luôn**: gói 2 gộp đọc+hành động vào một lưới làm lộ **18 nhãn trùng** (+1 ca trùng cùng loại: widget "Tốc độ" vs mục đọc "Tốc độ") ⇒ thêm gợi ý loại (· xem / · bấm / · thẻ) **CHỈ ở chỗ trùng**, nhãn GỐC không đổi; test khoá: 0 nhãn hiển thị còn trùng |
| **U4** | **Tự đổi hình nền** — (a) hình nền **toàn app**; (b) **widget trình chiếu**: user chọn sẵn bộ ảnh, định kỳ tự đổi | 🔲 | — | off-car thuần: lưu danh sách ảnh + chu kỳ đổi + bộ đếm; ghép vào ô như một widget |
| **U5** | **Đa ngôn ngữ CHUẨN cho launcher** — hiện launcher chỉ có tiếng Việt | 🔲 | — | **Owner chốt 2026-09-10: việc RIÊNG, không nhập gói 2.** [ĐO] hiện trạng: **0** thư mục ngôn ngữ chuẩn (chỉ có nền tối + bề rộng màn) · **24** chuỗi trong tài nguyên · cơ chế dịch hiện tại = ghi cặp Việt–Anh tại chỗ dùng + bộ quét cây giao diện (94 cặp) · **launcher dùng 0 lần** (phần ClusterNav cũ: 216 lần) · nhãn **195 mục khả năng** ở `:core` chỉ có tiếng Việt. Cách hiện tại ổn cho 2 tiếng + 1 người dùng; KHÔNG ổn khi phát cho người khác hoặc thêm tiếng thứ 3 (quên bọc chuỗi thì không có gì báo). Cần: bộ chữ có **test khoá thiếu bản dịch** + phủ nhãn registry. ⚠ tệp tài nguyên bị niêm phong ⇒ phải tính đường không đụng seal |
| **W-unit** | **Người dùng CHỌN đơn vị** (áp suất · nhiệt độ · khoảng cách · tốc độ · tiêu thụ · mô-men · chiều dài) | ✅ off-car 09-10 | 🚗 | **Owner thêm 2026-09-10**: *"mấy cái có đơn vị thì cho user chọn, đừng ép"*. Làm TRONG gói 2 vì ô khả năng hợp nhất là chỗ DUY NHẤT định dạng giá trị. [ĐO] **61/123** mục có đơn vị, **26** mục có lựa chọn hợp lý (°C×12 · km×6 · kPa×4 · km/h×2 · 3 lẻ); 34 mục không có lựa chọn (%, độ, vôn, vòng/phút…). Chọn theo **LOẠI đại lượng** (7 lựa chọn) chứ không từng mục. Mặc định = y như hiện tại. [ĐO] sửa luôn **tự mâu thuẫn**: registry khai kPa nhưng widget lốp tự chia 100 hiện bar. Spec `kachi-unified-capability-tile.html` §4.6 |
| P6 | (tuỳ chọn) widget bên thứ ba · **cảnh (scenes)** · nút **Chiếu-cụm** gọi lại pipeline cast · bảng màu NGÀY đủ + nút gạt | 🔲 | 🚗 | phần nào làm được trên emulator · cast = xe. ⚠ "cảnh" ở đây trùng ý với lớp gộp lệnh của **W2** ⇒ làm ở W2 thì P6 nhẹ đi |

## Nhóm 5 — HỆ THỐNG & VẬN HÀNH

| ID | Việc | Làm | Xe | Ghi chú |
|----|------|-----|-----|---------|
| **P8** | **Vòng kiểm quyền lúc mở launcher** — khởi động thì quét ĐỦ mọi quyền: đủ → im lặng; thiếu → hiện đúng quyền thiếu + xin lại, **lặp tới khi đủ** mới vào launcher | 🔲 | 🚗 | Gán phím vô-lăng (kế thừa) **hay mất quyền sau khi khởi động lại**. Hiện **chưa có** vòng kiểm quyền tập trung. Gom mọi quyền (đọc thông báo · trợ năng · vẽ trên màn · kênh shell…) vào một màn khởi động. Làm sớm còn giúp **giảm nhiễu khi test trên xe** |
| S1 | **Dựng lại màn Cài đặt** theo menu mới: Dẫn đường+HUD · Màn cụm (chiếu/biển báo/bóng VietMap) · Gán phím vật lý · Tiện ích (ghế/lọc bụi) · Hệ thống (ngôn ngữ/màu/khắc phục/cập nhật) + phần riêng của Kachi (cài đặt/quyền/trạng thái/giới thiệu) | 🔲 | — | owner **DUYỆT** 2026-09-09. Dựng vỏ **MỚI**, KHÔNG đụng màn cũ 1386 dòng đang bị niêm phong. Cần spec riêng |
| P7 | **Tự khởi động** — chọn bố cục + app từng ô cho lúc nổ máy; máy lên → launcher lên → **app vào đúng ô, sẵn sàng dùng** | 🔲 | 🚗 | nền ~80% (đã lưu bố cục + app ⇒ khôi phục lúc khởi động + gắn bộ chiếu). Cần: cấu hình khởi động rõ ràng + đặt Kachi làm HOME trên xe + gắn chắc khi máy nguội + **không trùng app đang chiếu lên cụm** |
| T1 | App thật để test: VietMap mod ✅ đã cài; GMaps mới (cần root máy ảo) + YouTube (cần bản cài) — owner chốt "tính sau" | 🔨 | — | tạm dùng VietMap + GMaps zin. ⚠ GMaps/YouTube trên máy ảo hiện là bản 2019 (nền Google cũ) |

## Nhóm 6 — NỢ VERIFY TRÊN XE (không code thêm, chỉ cần một buổi test)

| ID | Việc | Làm | Xe | Ghi chú |
|----|------|-----|-----|---------|
| ARCH-🚗 | Checklist kiến trúc hoãn: (1) dừng-chiếu dùng **hàng đợi chung** + trọng tài task **phía cast**; (2) gộp **12 chỗ ghi hình học** của cast về một nơi; (3) chạm mượt qua daemon (độ trễ + kéo); (4) hosting/sắp-lại/khung tiêu đề (mượt + thứ tự lớp); (5) gắn app vào ô khi **máy nguội**; (6) bố cục freeform **bền sau khi tắt máy VẬT LÝ** (không nhận lệnh khởi động lại mềm làm bằng chứng) | 🔲 | 🚗 | Nguồn: `docs/_handoff/arch-stage-7-done.md` + SDD §5.1/§6/§7 |
| — | Nợ 🚗 rải rác của nhóm 2–5: mở app vào ô thật (P2/P3.1/P-bug2/U2) · hành động xe thật (P3/W1/W2/W3/W5) · số liệu xe thật (P4/W4) · tự khởi động (P7) · sống chung chiếu-cụm (X1) · **danh sách lấy trên xe** cho 5 datum + mã bảo trì gạt mưa + verify số theo trim (L-RE) | 🔲 | 🚗 | **ĐÂY LÀ NÚT THẮT LỚN NHẤT** của dự án: off-car gần cạn, phần lớn việc đã xong chỉ chờ xe xác nhận |

---

## Đánh giá khả thi — bố cục động 12 × 6 (P9)

**Kết luận: KHẢ THI.** Ba phần khó nhất đã có sẵn và đã chạy:
1. **Chiếu app vào một khung bất kỳ** — đã proven (app render trong ô trên màn phụ).
2. **Đổi bố cục thì sắp lại app đang mở** cho đúng khung mới, không reset — đã chạy.
3. **Hồ sơ người dùng + lưu cấu hình bền** — đã có từ P5.

Máy tính bố cục hiện tại vốn đã **sinh ra hình chữ nhật** từ một bố cục; 5 bố cục sẵn chỉ là **trường hợp riêng** của lưới. Lưới 12 × 6 dùng đúng phép toán đó, chỉ đổi nguồn khung từ "chọn 1 trong 5" sang "user tự vẽ".

**Phần thật sự mới** (theo bậc, làm được off-car trừ bước cuối):
| Bậc | Việc | Rủi ro |
|-----|------|--------|
| 1 | Model lưới + kiểm tra hợp lệ (không đè · không hở · khung tối thiểu · trong biên 12×6) — thuần logic, test trước | Thấp |
| 2 | **Trình vẽ khung** (kéo chọn ô lưới → thành khung) | **Cao — tốn công nhất, là UX chứ không phải kỹ thuật lõi** |
| 3 | Gán app / widget / action cho từng khung + nới trần **4 ô → N ô** | Trung (đụng model ô đang dùng khắp nơi) |
| 4 | Lưu thành hồ sơ + chọn app tự mở | Thấp (dùng lại nền P5/P7) |
| 5 | Chạy thật nhiều app cùng lúc | **Cần đo trên xe** (mượt/RAM) |

---

## Đề xuất THỨ TỰ BURN (owner chốt)

| Ưu tiên | Gói burn | Gồm | Vì sao |
|---------|----------|-----|--------|
| **1** ▶ **ĐANG LÀM** | **"Mở app cho đúng"** — spec `docs/specs/kachi-open-app-correctly.html` (**Draft, chờ owner duyệt · 5 OQ**) | P-bug2 · U3 · U2 | Cùng MỘT mạch (mở/đặt app). P-bug2 là **BUG ảnh hưởng cả xe**, đã biết gốc + có bản sửa đã kiểm ⇒ rẻ nhất. U3 là chỗ **owner đang vướng nhất**. Sửa bug trước tính năng. ⚠ Spec lộ 1 phát hiện: đường freeform bị bỏ qua khi đang nhúng ⇒ **U2 hẹp hơn tưởng** (OQ4) |
| **2** | **"Read/Write hợp nhất"** | RW0 → W4 · W3 | RW0 là **nền cho 4 việc** (W2·W4·W5 + widget sau này) ⇒ làm trước thì mấy việc sau rẻ hẳn. W4 (áp suất lốp, dữ liệu đã đủ) + W3 (lấy gió trong, action đã có) là **2 ca chứng minh nhanh** cho mô hình |
| **3** | **"Đủ hoá điều khiển"** | W2 (kèm lớp gộp lệnh) · W5 | Phụ thuộc RW0. Lớp gộp lệnh dùng lại cho cả "cảnh" ở P6 ⇒ làm ở đây thì P6 nhẹ đi. Riêng **bảo trì gạt mưa** cần lấy mã trên xe → làm phần còn lại trước, chừa 1 chỗ trống |
| **4** | **"Vào launcher cho ngon"** | P8 · S1 | P8 (vòng kiểm quyền) nên làm **TRƯỚC buổi test xe lớn** vì mất quyền là nguồn nhiễu chính khi test. S1 là chỗ chứa ô tick của W3 + màn quyền của P8 |
| **5** | **Bố cục động** | P9 | To nhất + đụng model ô ⇒ **cần spec riêng**, và nên làm SAU RW0 (khung phải chứa được cả widget lẫn action) |
| **6** | Lấp chỗ trống | U1 · U4 · T1 · P6 | Nhẹ, độc lập, chen vào lúc chờ xe |
| **Song song** | **Buổi test trên xe** | Nhóm 6 | Không cần code. Đây là **nút thắt lớn nhất** — off-car gần cạn, một buổi test mở khoá cả loạt 🚗 |

**Khuyến nghị làm ngay: gói 1** — nhỏ, off-car kiểm được, chữa đúng chỗ owner vướng, và dọn đường trước khi lên xe.

---

> **Cạm bẫy emulator (phiên sau)**: (1) **KHÔNG dùng bản máy ảo có Play Store để test app-vào-ô** — bản đó khoá cứng, kênh shell bị hỏi quyền lặp mãi, không sửa được; Play Store CHỈ để cài app, test ô thì dùng bản `google_apis` + copy app sang. (2) Chỉ chạy **1 máy ảo** (2 cái vừa lag vừa bẫy cổng: máy thứ 2 nằm ở cổng khác nên lệnh chuyển cổng trỏ nhầm). (3) GMaps/YouTube trên bản `google_apis` là bản 2019 (nền Google cũ). Công thức 1 máy ảo sạch ở `docs/diagnostics/kachi-emulator-app-hosting-2026-09-10.md`.

---

## ClusterNav 2.0 — nền tảng DÙNG LẠI (KHÔNG track backlog)

> **Owner chốt 2026-09-10: dự án này CHỈ làm LAUNCHER.** ClusterNav 2.0 là nền tảng **dùng lại** (gom vào mục Cài đặt của launcher), đã ổn định trên xe — **KHÔNG theo dõi backlog cho nó nữa**. Toàn bộ backlog kế thừa (các bản ship v1.32 → v1.38 + phát hiện trên xe + mục A–F + pipeline đọc-màn-hình đã gỡ) đã chuyển sang lưu trữ: `docs/archive/clusternav2-backlog-inherited-2026-09-10.md` (giữ làm ký ức; git history là bản ghi đầy đủ).
