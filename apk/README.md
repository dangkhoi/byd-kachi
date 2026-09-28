# apk/ — kênh OTA của Kachi

> **Trạng thái**: Current · **Cập nhật**: 2026-09-28 · **Tệp mới nhất trong thư mục**: `Kachi-2.81-release.apk` vc182 (43 875 077 B, sha256 `65738e50…4c9e`) — **CHƯA đăng OTA**; bản đang phục vụ trên kênh `main` (và đang chạy trên xe owner) vẫn là `Kachi-2.79-release.apk` vc180 (43 837 401 B, sha256 `8277ebef…0f460`) [ĐO `shasum -a 256`]. `Kachi-2.80-release.apk` vc181 **đã bị xoá** khỏi thư mục (rút vì lượt soát tìm ra 2 × [P0]) — dòng nhật ký của nó giữ lại bên dưới. Đăng 2.81 xong thì xoá 2.79 để giữ đúng luật "chỉ một tệp mới nhất" · **Mục đích**: Thư mục APK phát hành để app **tự cập nhật qua mạng (OTA)** xuống xe — cùng cơ chế ClusterNav 2.0 đã dùng.

**(VI)** App trên xe (`UpdateChecker`) hỏi GitHub Contents API thư mục này trên nhánh `main` của repo `dangkhoi/byd-kachi`,
tìm tệp **`Kachi-<ver>-release.apk`** có phiên bản lớn hơn bản đang cài, tải về rồi cài qua dadb loopback (`pm install -r`)
— không cần ADB/laptop, không cần bấm qua trình cài đặt hệ thống.

- Chỉ để **một** tệp mới nhất (bản cũ xoá đi cho repo nhẹ; lịch sử vẫn trong git).
- Tên tệp **đúng khuôn** `Kachi-<major.minor[.patch]>-release.apk` — tên có lát cắt của bộ thu T10 (`…-<slice>-<sourceId>-release.apk`)
  KHÔNG phải bản OTA và bị bỏ qua (`UpdateCheckerTest`).
- Ký bằng **khoá riêng của Kachi** (từ 1.41, L2 — `~/.kachi/kachi-release.keystore` + `keystore.properties` gitignored;
  fingerprint SHA-256 `92:57:49:9B:61:69:D7:AC:A2:F0:27:D7:0F:1F:D8:E1:B8:13:7A:B4:F2:F3:44:2F:00:B0:08:4A:26:BB:99:17`).
  Bản Kachi cài trước 1.41 (ký khoá cũ / debug) **không** cập nhật đè được — gỡ rồi cài tay một lần, sau đó OTA bình thường.
- **2.81 (182) — 2026-09-28** (`Kachi-2.81-release.apk`, 43875077 B, sha256 `65738e50a5c851df7194e8b83f3d74a5fa96fe28ec6a645701e4bb6781a04c9e`, **thay hẳn 2.80 — 2.80 đã bị xoá khỏi thư mục này**). Gồm mọi thứ của 2.80 cộng ba nhóm sửa. **(a) Lượt soát kiến trúc sư trên phần phím** đóng 2×[P0]: cổng *không giết khi đang mở app* trước đây chỉ nhìn màn chính, trong khi [ĐO xe 15/09 + 18/09] app khách nằm trên **màn ảo của chính mình** (display 1 từng là ô của Kachi, cụm ở display 2, bản đồ trong ô ở display 5) ⇒ đúng ca nó sinh ra để chặn thì nó cho đi thẳng qua; và cổng *người dùng có cần không* dựa vào `accBooster` (mặc định BẬT) trong khi hai vòng cứu hộ chỉ chạy khi phím-thoại BẬT (mặc định TẮT) ⇒ máy vừa cài mới có thể tự giết launcher mà **không vòng nào lắp lại**. Kèm 3×[P1] (chặn ký tự shell trong lệnh tự giết · bỏ qua lượt grant đã hết giờ · nhật ký bền nay ĐỌC ĐƯỢC trong màn Chẩn đoán, trước đó chỉ ghi mà không ai xem được). **(b) CAM-SL6-RIGHT — cắt dải cho mọi góc camera**: [ĐO owner 28/09] chọn số camera khác hai góc Gương thì có hình nhưng ra nguyên khung ghép. Gốc: `CameraPanoCrop.cropFor` mở đầu bằng `view.crop`, chỉ hai góc Gương mang sẵn rect. Sửa: sáu góc kia nay tính là ảnh ghép, và **cỡ nguồn + tâm quang giải chung một lần với vùng cắt** — lượt phản biện chỉ ra sửa lẻ phần cắt thì hình vừa bị kéo bẹp (thiếu tỉ lệ nguồn) vừa cong lệch (tâm quang ra mép dải). Thêm hàng *dải hình* từng bên (Tự động · Nguyên khung · 1–4). **(c) Nhãn camera bỏ phán bừa**: 8 tên kiểu *Trước-trái* thay bằng **số 1–8** — owner: *“mình cũng đâu có biết là nó cam nào đâu mà phán cho người ta”*; thứ tự enum nay bị test khoá để con số báo về không trôi nghĩa. **[ĐO 28/09] thứ tự dải khung ghép trên SL6 = `sau · trái · phải · trước`, TRÙNG Seal** ⇒ một bố cục cho cả hai đời xe. Test **4 707 · 0 lỗi** (đủ 5 module) · lint 0 lỗi. **CHƯA đăng OTA** (chờ quét bảo mật).
- **2.80 (181) — 2026-09-28** (`Kachi-2.80-release.apk`, 43859677 B, sha256 `e7167ced3b6df620ba13f54d4ff901e8a14f57cc79fe787cda4c0431a03e95b4`, **bản thử nội bộ, CHƯA đăng OTA** tới khi xong lượt soát + quét bảo mật). Hai việc. **(1) Tự chữa mối nối phím vô-lăng bị kẹt** — gốc là lỗ hổng AOSP `android-10.0.0_r47`: `AccessibilityManagerService.java:4114-4117` đẩy component NGƯỢC vào `mBindingServices` khi mối nối đứt, mà `:1630-1631` lại `continue` bỏ qua mọi component trong tập đó — dòng ấy đứng TRÊN cả `bindLocked()` (`:1642`) lẫn `unbindLocked()` (`:1645`) ⇒ park VĨNH VIỄN. [ĐO xe 28/09] gỡ khỏi `enabled_accessibility_services` mà mục kẹt vẫn còn ⇒ đường toggle cũ VÔ HIỆU ở ca này; `am force-stop` + lắp lại thì vào Bound thật. Nay có thang 3 nấc, 3 cổng giữ (đúng ca kẹt · màn chính chưa có app người khác · mỗi đợt ngủ dài một lần) và nhật ký bền ghi mỗi lần trạng thái đổi. Bỏ câu báo sai *“xe đang tải cao”*. **(2) Khối *Nếu camera không hiện*** — hai hàng chọn góc nhìn từng bên (8 góc có nhãn), bấm chip là hình bật lên NGAY để xem thử, không phải ra đường bật xi-nhan; owner trên Sealion 6 không mở được cam phải. [ĐO máy ảo 28/09] khối hiện đủ 8 chip, mặc định đúng hai bên, bấm không làm chết tiến trình, overlay bật thật (đen vì máy ảo không có camera AVM). Test 4 505 · 0 lỗi · lint 0 lỗi.
- **2.79 (180) — 2026-09-27 khuya** (`Kachi-2.79-release.apk`, 43 837 401 B, sha256 `8277ebeffaaafe827f3a8209989c601c3b1de11d9a72294b35a22584b650f460`, owner chốt trên xe: *"3 options, có cái được cái mất, làm tối đa, user thích chọn gì thì chọn"*): **ba hình chiếu lên cụm, mỗi hình tới hạn của nó** — **Chữ nhật** `571×428`, **gấp 2,5 lần diện tích** bản trước (`360×270`), mất 0 % tầm nhìn · **Theo cụm** `641×428` (trái) / `647×428` (phải), mép ngoài **bám ĐƯỜNG CONG thật** của kính (2.78 chỉ là vạch thẳng: log `cong=89/89/89` → nay `46/41/89`), dùng phép **phóng để lấp** 112–113 % nên **không còn khe đen**, đổi lại mất 11–12 % tầm nhìn (owner đã duyệt mức 20 %) · **Tròn** đường kính `428` = trọn chiều cao dải (trước 360). **Mốc trên của dải nâng 136 → 132** [ĐO ±4 px] — đo đáy đường kẻ phân cách của thanh hệ thống thay vì mốc nội dung ⇒ cả ba hình cao thêm 4 px, hai bảng mép cong dựng lại ở mốc mới. Đường overlay trên **màn chính không đổi một byte** (có bài canh). Test **4 642 / 0**, lint 0. ⚠ Hai ngoại lệ báo thẳng: nguồn rộng hơn nửa dải (nguyên khung ghép 5,33:1, không phải view gương) mất 61 % bề ngang để giữ chiều cao tối đa; *theo cụm* + vùng gương **hẹp** không xoay mất 23–25 %, vượt hạn 20 % — hình học, chờ owner quyết có kẹp trần không. Thay 2.78.
- **2.78 (179) — 2026-09-27 tối muộn** (`Kachi-2.78-release.apk`, 43 837 405 B, sha256 `845289d4992f96a7cc9bcef74051a3f665bbec96e084ff8697a32592758b08c3`, sửa theo mắt owner ngay trên xe sau khi cài 2.77): (1) **không mất video trên cụm** — 2.77 đẩy cửa sổ ra điểm XA NHẤT của kính rồi mặt nạ cắt mất tới **71/318 px** ở hàng đáy (*"bám nhưng cắt rát quá"*); nay đặt ở điểm **TRONG CÙNG** trên đúng dải hàng cửa sổ chiếm ⇒ **0 px bị cắt**, bài test khoá bằng bất biến chứ không bằng con số. (2) **đo mép PHẢI của kính** (bảng 9 mẫu, sai số nội suy 7,7 px, loại 1 trong 3 ảnh vì neo dồn một phía) ⇒ cửa sổ phải nới 1780 → 1800, hết khoảng thừa (*"không bám còn thừa 1 khoảng"*). (3) **không ép hình trên cụm** — gỡ phép tự thăng chữ nhật → theo cụm; chọn gì ra nấy. (4) **mép trong mờ dần** thay vì cắt cứng (*"ko là 1 vạch thẳng nhìn nó như sẹo"*), bề rộng = bo góc của hồ sơ, chỉ áp cho hình theo cụm. (5) **chữ nhật/tròn trên cụm cân đối hai bên**, có test khoá. Test **4 638 / 0**, lint 0. ⚠ Hai mép vẫn là **đường thẳng** sát kính, không cong theo từng hàng — muốn cong thật phải phóng ảnh rồi cắt rìa (cố ý mất rìa), câu hỏi mở cho owner. Thay 2.77.
- **2.77 (178) — 2026-09-27 tối** (`Kachi-2.77-release.apk`, 43 837 401 B, sha256 `2f41abeb81360683342985a3b22bab1aa38d78929f1f31d0a5bf0dee8c8b699f`, **bản đóng sau buổi xe closing**: (1) owner *"nhiều option quá rối cho người dùng… bỏ hết phần nâng cao đi, bỏ luôn nguồn"* ⇒ **gỡ khối *Nâng cao (kỹ thuật)* và tuỳ chọn nguồn một camera**; màn Camera còn đúng **10 hàng người lái**; 15 khoá kỹ thuật vẫn ghi/đọc được qua cầu kiểm thử, giá trị tốt nhất đã chốt trong hồ sơ xe (dải hẹp · GL · F55 K100 S130 · cam 1 · dải trái 1 phải 2). Lý do bỏ một camera là **số đo**, không phải cảm tính: cùng cảnh, cạnh 686 so với 351, tỉ lệ chi tiết ngang/dọc 0,30 so với 0,19 ⇒ một kênh chỉ kéo ngang chứ không thêm điểm ảnh. Bản đồ kênh đo được (1 sau · 2 trái · 3 phải · 4 trước) giữ lại trong tài liệu. (2) **mặt nạ camera trên cụm bám ĐƯỜNG CONG của kính** (bảng 9 mẫu đo từ 3 ảnh cụm thật) — owner: *"shape nó không theo cạnh trái cong của cụm"*. Vòng hợp nhất bắt **[P1]**: đường cong của MỘT tấm kính đã thành mặc định cho MỌI đời cụm, mà từ bản này nó **cắt pixel thật** (tới 122 px) ⇒ chỉ đời đã đo mới mang bảng cong, đời khác nhận bản thẳng an toàn. Test **4 631 / 0**, lint 0, thay 2.76).
- **2.76 (177) — 2026-09-27 chiều** (`Kachi-2.76-release.apk`, 43 874 333 B, sha256 `7a4b182b435e05aa05731e4be668131f554b8fac675e737e14ad9dba09be9794`, **đợt đóng nợ off-car cuối trước khi owner đi công tác một tháng**, 7 làn song song + 1 làn nợ: **camera** màn Cài đặt hai tầng (11 hàng người lái · 16 móc đo chỉ hiện khi bật chế độ kiểm thử, không xoá khoá nào) · mặc định theo hồ sơ xe (Seal DL3 = bộ đo trên xe 27/09, xoay 0 theo nghiên cứu ngành) · nguồn *Một camera* tự lùi về toàn cảnh khi HAL từ chối · **hình theo cụm** kẹp trong dải giữa đo từ ảnh cụm thật · **lật gương** từng bên (mặc định tắt, chờ đo CAM-M1) · **giọng nói** làn ghi HAL tuần tự cho câu ghép · ghép vế sau khi vế trước đã đủ nghĩa · khớp tên app bằng tiền tố ≥ 4 ký tự khi câu có mệnh đề ô · **thanh trên** mã thô → chữ theo bảng hằng OEM · cửa/cửa sổ trời đổi hình mở-đóng · **mức ghế nằm trong hình** (1/2 làn sưởi · 1/2 bông tuyết, bỏ số — yêu cầu chót của owner) · **bố cục** 4 nút thanh trên cùng hộp, khối thẻ lốp về tâm xe, ảnh xe người dùng giải mã fit · **nợ mã** 17 tệp > 500 dòng tách thuần ⇒ 0 tệp vượt trần, một cửa `SysProps` thay 3 bản sao reflection, danh mục sinh lại 165 dòng. Review Opus Pass 1: **15 phát hiện, 14 vá** (đáng kể: hình chữ nhật trên cụm chưa bao giờ bị kẹp ⇒ mất 93/360 px · lật gương không lật tâm quang · lượt nói chốt trước khi ghi xong · mã 255 "không xác định" của cửa bị đọc thành "đang mở"). Test **4 646 / 0** (5 module), lint **0**, 0 tệp > 500 dòng, chuỗi APK 0 dấu vết. Runbook xe closing `docs/diagnostics/oncar-runbook-2.76.md`, thay 2.75).
- **2.75 (176) — 2026-09-27 trưa** (`Kachi-2.75-release.apk`, 43 791 047 B, sha256 `c11274beb6cc734c079777f559c0865b9ac8a18c179c57abc6d2b7e2c9681ceb`, đóng off-car ngay sau buổi xe 27/09 theo số đo thật: VOICE (cắt im lặng đầu câu pre-roll 300 ms · app + "vào ô số N" không động từ · "mở vietmap hai" · lượt nối bật open-turn + có bản thu · chửi→sưởi · log `hỏi lại:`) · UX8 (chip *Lấy gió*/*Cảm biến PM2.5* đổi HÌNH không chữ · `auto n` thường trên chip) · CAMERA ([P0] đổi bên xi-nhan không đóng `AVMCamera` ⇒ `BufferQueue abandoned` 16/s — sửa `closeSession(keepPano)` · núm *Dịch khung* pan_x/pan_y có dấu theo bên · nguồn *Một kênh* `camera_source=CHANNEL` (tuỳ chọn, mặc định toàn cảnh) · trần vẽ GL 15 fps · xoay ±90 chứng minh KHÔNG cong) · TRIP (`TripTimeSmoother`: HAL bậc 0,1 h ⇒ phút nội suy không lùi; `hoursToHm` floor) · review Opus Pass 2 APPROVED · test 4 541/0 · lint 0 · runbook xe `docs/diagnostics/oncar-runbook-2.75.md`, thay 2.74).
- **2.74 (175) — 2026-09-27** (`Kachi-2.74-release.apk`, 43 768 607 B, sha256 `67105b12e80eee72010bb86aa1803dd51aacd3e05bd383a0a058406c409a8d04`, đêm 26→27/09: UX1–UX7 (avatar · widget lốp + 14 bề mặt tổng hợp · danh sách lệnh nói trong Cài đặt · gió AUTO · chip Ghế lái/Ghế phụ · khe chip) · VOICE-OPEN-TURN (câu dở ⇒ giữ mic ≤ 1,2 s) · camera: `camera_frame` PNG thô, chip Bề rộng vùng gương/Dải/Hình tròn/Kênh HAL, **Nắn méo (GL)** thử nghiệm (đo máy ảo, chưa đo xe, mặc định vẫn TextureView) · README/hướng dẫn/danh mục/audit doc; review Opus Pass 1 APPROVED (1 P0 + 7 P1 vá), 4472 test/0 đỏ, lint 0; runbook xe `docs/diagnostics/oncar-runbook-2.74.md`)
- **2.73 (174) — 2026-09-26** (`Kachi-2.73-release.apk`, 43 534 851 B, sha256 `c08d5b1701d421ab8de944a11db96a12a93838ec957de4496a7a104cbeaff8c7`, đợt off-car 8 việc sau buổi xe: overlay giọng nói không lấy tiêu điểm (taskbar không trồi; Back không huỷ) · hotword "vào ô số N" + hồ sơ tên tiếng Anh + khớp mờ tên app · VAD trần 1200 · log 34→≈9 KB/phút · khung camera đúng tỉ lệ crop sau xoay + chip Kết xuất camera · `libkachimem.so` mallopt cho `:wake` (NDK r30); review Opus Pass 3 APPROVED, 4235 test/0 đỏ, lint 0; runbook xe `docs/diagnostics/oncar-runbook-2.73.md`)
- **2.72 (173) — 2026-09-26** (`Kachi-2.72-release.apk`, 43 483 553 B, sha256 `3762a2b708ee3a86aef3a8179fa76d163c7d07f294a0d4e8d91f80e00089e73d`, + lưới an toàn: helper HAL chết lúc đèn bật ⇒ báo OFF, camera không treo (review Pass 2); = 2.70 hotfix camera giữ tới khi đèn tắt (xe: helper báo trạng thái, không nháy) + 2 dòng Cài đặt xoay video trái/phải độc lập; đã test trên xe Seal ở bản 2.70 (buổi 26/09); cert `92:57:…:99:17` không đổi, không debuggable)
- **2.70 (171) — 2026-09-26** (hotfix `CameraHold`, chỉ cài trực tiếp trên xe owner, không đăng kênh)
- **2.69 (170) — 2026-09-26** (`Kachi-2.69-release.apk`, 43 483 993 B, sha256 `3b4fcd092558cda0a796d7d083c97d67ebf4fe3056b2d473c99614d054dba34b`, từ `:wake` làm được ô/bố cục (relay + ack 1,5/4 s), một chủ sở hữu phiên `:wake`, harness voice 106/106 ×3, tách test 761 dòng; review Opus Pass 4; cert `92:57:…:99:17` không đổi, không debuggable)
- **2.68 (169) — 2026-09-26** (`Kachi-2.68-release.apk`, 43 467 609 B, sha256 `efe739c0fd70c3543c2f5c8d7f1635d4a786295fa8c84e87630729940580e006`, CLOSE-2/3/3b/5/7/8/10 + RES-CLEAN: nút mic đi `:wake` khi Hey Kachi bật (ảnh chụp ngữ pháp cross-process), tách 4 tệp >500, coroutines 1.11.0, 41 resource gỡ, bộ ca voice 106/106; review Opus 3 pass; cert `92:57:…:99:17` không đổi, không debuggable)
- **2.67 (168) — 2026-09-26** (`Kachi-2.67-release.apk`, 43 345 718 B, sha256 `942cbf3a18272334b9482513afe59b85e3a6935011a7e763e4cbfd0da9577bb8`, CAM-ROT: xoay video camera xi-nhan theo bên + chip Cài đặt "Xoay video" (6 chế độ, có "Theo bên, ngược lại" làm đường hoàn tác trên xe); cert `92:57:…:99:17` không đổi, không debuggable; 2.66 đã bị thay trên kênh nhưng giữ dòng nhật ký dưới)
- **2.66 (167) — 2026-09-26** (`Kachi-2.66-release.apk`, 43 342 022 B, sha256 `d5b37eb728d2bd1e902608b692a36b4b2df5d055d292c707515076efa8c910e1`, CLOSE-1 đóng dự án: profiling/hardening/lint 0/doc — `docs/CLOSEOUT-2026-09-25.md`; apksigner cert SHA-256 `92:57:…:99:17` không đổi, không debuggable, 0 bề mặt test)
- **2.65 (166) — 2026-09-25** (`Kachi-2.65-release.apk`, 43 260 174 B, sha256 `9f6224b731e07acd6ef4cf8d1f226faf6df80b0fab09831fcd6a0247a13585c1`, commit `93dc1b4`, thay 2.64). Bản cuối trước phiên đóng dự án (CLOSE-1). Nội dung 2.55→2.65 (11 bump trong ngày 2026-09-25: tách layer hình xe · gỡ 7 datum chết · camera SL6 picker + crop luôn áp · voice overlay độc lập · nút Khởi động lại launcher · import hồ sơ trùng tên · nút Đổi tên/Xoá thẳng hàng) — chi tiết từng sha ở `docs/PROJECT-BACKLOG.md` (mục "CHUỖI BUMP 2.55 → 2.65").
- **1.85 → 2.64 — 2026-09-20 → 2026-09-25**: nhật ký kênh không ghi từng bản ở đây (mỗi bản đều đã đăng lên `main` rồi bị bản sau thay); tra `docs/PROJECT-BACKLOG.md` (ghi chú theo ngày) + `git log -- apk/`.
- **1.78 (79) — 2026-09-18** (`Kachi-1.78-release.apk`, sha256 `bcfe70c2…da21`, thay 1.77). **Sửa "phím gán không ăn"
  dưới tải nặng** [ĐO on-car live <car-ip>, load 14]: `NavAccessibilityService` rớt bind dưới áp lực CPU/RAM →
  phím chết; Kachi tự-rebind THUA vì `GRANT_TIMEOUT_MS=9s` (rebind qua dadb, dưới load 14 dadb chậm > 9s → cắt giữa
  chừng). Nới **9s → 20s** ⇒ rebind hoàn tất dưới tải ("Sửa ngay"/OFF→ON ăn kể cả khi lag). [ĐO] toggle a11y trực
  tiếp bind lại NGAY cả khi load 14 ⇒ cơ chế đúng, chỉ thiếu thời gian. **[ĐO] CPU root** (không phải Kachi — chỉ
  2%): Google Maps 61% (chạy trong 1 Ô Kachi) + BYD cdr 39% + surfaceflinger 34% (3 display) + VietMap 18% ⇒ 8 lõi
  bão hoà. 5 module 0 đỏ. 🚗 owner test phím sau khi update.
- **1.77 (78) — 2026-09-18** (`Kachi-1.77-release.apk`, sha256 `f3671580…2508`, thay 1.76). **"Hey Kachi" — lớp Android
  (FGS micro nền) để gọi voice rảnh tay, mặc định TẮT.** `VoiceWakeService` (foreground-service, gate màn-sáng) +
  `VoiceWakeListener` (mic → cổng RMS → controller đã test → KWS) + `VoiceWakeKws` (sherpa KeywordSpotter,
  degrade RMS-only khi chưa có model). **Lá chắn CPU**: load-guard (hệ nóng → dừng), cổng năng lượng (im → không
  chạy KWS), cầu chì false-accept (nghe nhầm nhiều → tự tắt), một-mic (VoiceSingleFlight + yield cho nút lệnh).
  **Senior review độc lập bắt + vá 4 P0** (busy-loop CPU khi mic lỗi · bật wake làm chết nút mic · re-arm ăn cầu
  chì · orphan double-mic) + 11 lỗi khác. **[ĐO] 5 module 0 đỏ** (core 2255, 4743 test). ⚠ **CÒN CHỜ**: model KWS
  owner đăng OTA (`sherpa-onnx-kws-zipformer-gigaspeech-3.3M` ~4MB) — chưa có ⇒ bật lên chỉ chạy chế độ đo baseline
  (mic + gating, KHÔNG bắt câu gọi). 🚗 owner bật thử để đo CPU trên xe (không đẩy load / không kẹt).
- **1.76 (77) — 2026-09-18** (`Kachi-1.76-release.apk`, sha256 `6a95968e…6461`, thay 1.75). **Sửa BUG owner báo: bỏ
  chọn Kachi làm màn hình chính KHÔNG trả về launcher khác, vẫn kẹt Kachi.** Gốc kép: (a) chưa có đường un-set;
  (b) tự-khởi-động re-assert Kachi làm HOME mỗi lần nổ máy khi `keepHomeOnBoot() || homeChosen()`, mà `homeChosen`
  đặt một lần không bao giờ xoá → bỏ chọn kiểu gì boot sau cũng bị giành lại. Nay Cài đặt › Màn hình chính có nút
  **"Bỏ chọn Kachi làm màn hình chính"**: xoá cả hai marker → tắt alias HOME → `set-home-activity` về launcher khác.
  Kèm **lõi an toàn "Hey Kachi"** (chưa nối, inert): load-guard/cổng năng lượng/controller (chống hang CPU). **[ĐO]
  5 module 0 đỏ** (core 2245). 🚗 owner test xe: bỏ chọn → về launcher stock, reboot vẫn giữ (không quay lại Kachi).
- **1.75 (76) — 2026-09-18** (`Kachi-1.75-release.apk`, sha256 `3a92fd0a…81577`, thay 1.74). Nhạc: "phát bài …"
  trên YouTube/YT Music nay **tìm ra + phát LUÔN** (owner: *"phải play luôn"*) — làm đúng cơ chế Kiki nhưng
  on-device: tải HTML trang tìm kiếm → bóc `video_id` bài đầu → mở `watch?v=<id>` (mở URL watch thì app tự phát).
  Giải hỏng/mạng treo → **lùi** về `MEDIA_PLAY_FROM_SEARCH` với TÊN bài (không kẹt, không regression). ⚠ scrape ⇒
  mong manh theo markup YouTube — có đường lùi. **[ĐO] 5 module 0 đỏ** (core 2229). 🚗 owner test xe: "phát bài
  [tên] trên YT Music / YouTube" tự phát không.
- **1.74 (75) — 2026-09-18** (`Kachi-1.74-release.apk`, 38,0 MB, sha256 `abe05f3e…8286e`, thay 1.73). Voice dẫn
  đường + kính 50% (owner yêu cầu). **(1) App dẫn đường MẶC ĐỊNH**: Cài đặt › Dẫn đường có mục *"App dẫn đường mặc
  định"* (Google Maps / VietMap / Waze, mặc định **Google Maps**). Nói *"dẫn đường …"* KHÔNG nêu app → dùng app
  mặc định; **có** nêu tên → đúng app đó. **BỎ fallback chéo**: VietMap geocode hỏng KHÔNG còn tự nhảy sang Google
  Maps nữa — mở CHÍNH app đã chọn + báo *"chưa tra được điểm đến"* (owner: *"cái nào ra cái đó thôi"*). **(2) Kính
  50%**: *"mở một nửa kính"* / *"mở kính 50%"* nay hạ kính tới **~nửa** (HAL `WINDOW_OPEN_HALF`, đã đo per-window;
  ca cả-4-kính chờ xác nhận trên xe) thay vì mở hết. **[ĐO] 5 module 0 đỏ** (core 2226 · app · car-int 61 · offcar
  99 · contracts 22). 🚗 owner test xe: chọn app mặc định · «dẫn bằng vietmap» không nhảy GMaps · «mở một nửa kính».
- **1.73 (74) — 2026-09-17** (`Kachi-1.73-release.apk`, 38,0 MB, sha256 `953b5510…b5e4c`, thay 1.72). Sửa
  NHẬN-SỐ + câu HỎI từ log xe 81 lượt owner báo (*"chỉnh máy lạnh 24 độ không hiểu, hỏi đi hỏi lại"*, *"nhận diện
  số đang tệ"*). **(A) số nhiệt độ**: «tăng/giảm nhiệt độ 24 độ» trước bị hiểu **±24** (cộng vào 22 = kẹt trần 33)
  → nay **ĐẶT = 24** (số trong dải 17..33 = setpoint); gió/âm lượng giữ tương đối (không phá «giảm âm lượng 2 nấc»).
  **(B) câu hỏi map sai datum**: «chỉ số **bụi mịn** là bao nhiêu» ra `Số` (datum `gear` do chữ *"số"*) → nay **bụi
  mịn** (datum dài nhất thắng + bỏ cụm dẫn *"chỉ số"*); «nhiệt độ / máy lạnh bao nhiêu độ» trước RỖNG/`Âm lượng` →
  nay **nhiệt AC**. **(C)** «tắt bụi mịn» trước MỞ Google Maps → nay **tắt máy lọc**. **[ĐO] 5 module 0 đỏ** (core
  2224 · app 1147/1153 · car-int 61 · offcar 99 · contracts 22). 🚗 owner test xe. **Còn nợ (log)**: «chế độ lái»→đèn
  pha · «xi nhan»→đèn ngày (feature đã gỡ, khớp mờ lạc) · «một nửa kính» (chưa có lệnh ghi ½) · «xăng» (BEV chưa map).
- **1.72 (73) — 2026-09-17** (`Kachi-1.72-release.apk`, 38,0 MB, sha256 `37e8b4e8…6939d`, thay 1.71). Sửa
  END-TO-END 3 lỗi voice→app owner báo từ log xe (159 lượt): **(1) Google Maps chưa dẫn** — `geo:0,0?q=` chỉ MỞ
  màn kết quả; nay `google.navigation:q=<địa chỉ>` (dẫn turn-by-turn, Google tự geocode, không cần Nominatim).
  Và mặc định (không nêu app) nay ưu tiên GMaps dẫn-bằng-chữ thay VietMap (VietMap buộc geocode → kẹt "đang tra
  điểm đến" khi mạng xe treo). **(2) VietMap đơ** — geocode hỏng/timeout nay **lùi về Google Maps dẫn bằng chữ**
  (luôn có dẫn) thay mở VietMap trơn; thêm timeout cứng 7 s chống on-device Geocoder treo vô hạn. **(3) YouTube
  search không phát** — `ACTION_SEARCH` (chỉ mở ô tìm) → `MEDIA_PLAY_FROM_SEARCH` + focus (phát theo tìm), fallback
  ACTION_SEARCH. **[ĐO] 5 module 0 đỏ** (core 2220 · app 1141/1153 · car-int 61 · offcar 99 · contracts 22). 🚗
  owner test xe: GMaps dẫn thật · VietMap không đơ (hoặc tự lùi GMaps) · YouTube phát. **Còn nợ (parser)**: «mở
  &lt;tên ca sĩ&gt; trên youtube» (không có từ "bài/hát") rớt tên; app-hint đầu câu ("dùng google map…") chưa bắt.
- **1.71 (72) — 2026-09-17** (`Kachi-1.71-release.apk`, 38,0 MB, sha256 `c1f4fdd5…9064a`, thay 1.70). Sửa 2 việc
  owner báo, **từ gốc IA (không hotfix)**: (1) **Trạng thái xe đọc REALTIME** — ô điều khiển (nhiệt độ · gió · lấy
  gió trong · cốp…) trước hiển thị mức MẶC ĐỊNH trong RAM (nhiệt 22 · gió 4), chỉ đổi khi bấm trên launcher; nay
  đọc giá trị THẬT của xe theo nhịp poll (`CarStatus.controls` + `CarDataAdapter.readState`, cửa sổ ân hạn 2,5 s
  chống nháy sau khi bấm). Đặt 24°C ở màn BYD gốc ⇒ launcher hiện 24 (nhịp chậm ≤10 s). Chỉ đọc thứ ĐANG HIỆN
  (không phá tối ưu K1). (2) **Màu sáng sủa hơn** — thang bề mặt (16 vai nền) trước là hex đặt tay rời rạc, mood
  "tối tăm" nằm rải; nay derive từ MỘT recipe `SurfaceRamp` (nền deep-indigo ấm `#141b30` thay near-black lạnh
  `#0a0d13`, gradient thẻ sâu hơn có sức sống). Tương phản chữ WCAG giữ nguyên (test khoá). Đây là **thử 1
  version** — 🚗 owner ngắm màu trên xe + đo realtime (đặt nhiệt/gió ở màn xe, xem launcher đổi theo). **[ĐO] 5
  module 0 đỏ** (core 2219 · app 1139/1151 · car-int 61 · offcar 99 · contracts 22).
- **1.70 (71) — 2026-09-17** (`Kachi-1.70-release.apk`, 38,0 MB, sha256 `be34ddc6…6617b`, thay 1.69). VOICE
  tái kiến trúc từ đầu (owner: quan trọng nhất, chưa bao giờ ổn thực tế): máy trạng thái tường minh `VoiceTurnState`
  (8 pha, bất biến chống-loop CHỈ pha nghe mở mic) thay chùm cờ race; VAD hâm sẵn 1 lần/tiến trình (bỏ nghẽn
  nạp-mỗi-lượt của "bấm 1,5 s mới nghe"); chime PCM async (bỏ chặn 3 s). Sửa 2 lỗi owner báo: overlay tắt giữa
  câu → sống tới hết câu đọc; **Piper nói chậm lại** (0.9, núm `voice_tts_speed` chỉnh trên xe). **Chọn giọng
  phản hồi**: số 1 Piper (mặc định) · số 2 giọng bé (clone, tự lùi Piper khi thiếu clip). Chạm trong ô: TCP
  loopback + token (thay unix-socket bị SELinux chặn) + cầu chì daemon + `input` no-retry. Cốp bằng giọng
  (`voiceCtlBackDoor` MỞ=1/ĐÓNG=3, [ĐO xe 09-17]); ghế mát 3 mức; AC AUTO đọc được nhưng GHI vẫn chặn (thiếu
  feature-id trên xe). **Model G** fine-tune (gipformer-vi-ft-ep2, MIT, experimental) + nút chọn — [ĐO benchmark
  270 câu giọng thật: G 177 vs ship 167]; mặc định vẫn giữ, owner quyết trên xe. Visual P1b/P2/P3. 🚗 chưa đo
  trên xe: độ trễ nói→nghe→chạy, overlay không cắt giữa câu, chạm YouTube, chọn model G, cốp/ghế/AC.
- **1.69 (70) — 2026-09-17** (`Kachi-1.69-release.apk`, 37,7 MB, sha256 `a3ad5ed7…4db1fe`, thay 1.66). Voice: hết vòng lặp
  "ừ/ừm" và mic chồng phiên (gốc của "YouTube không lướt được" + "nói xong 5–6 s mới chạy"), ngắt câu bằng Silero VAD
  (asset 0,64 MB trong APK) + cắt đuôi im lặng, số THẬT trước khi tăng/giảm (17/47 nút), tên app kiểu Việt
  ("gu gồ máp", "du túp", app lạ như ChatGPT tự sinh), chịu lỗi chính tả (cốp/cấp, đọc/độc, pin/bên…), hỏi lại
  "lọc bụi hay lọc ngay", xuất nhật ký voice (Cài đặt › Voice; bridge `voice_dump` cần `auto_confirm`), Piper đọc
  "Kachi" đúng. Vuốt trong ô app: dự phòng theo cử chỉ (daemon chạm bị SELinux chặn trên xe). Visual: bề mặt 3 tầng +
  tint lĩnh vực, icon hoa anh đào, bỏ vạch sáng đỉnh nút. Gỡ toàn bộ ADAS/an toàn + 19 mục owner đánh NO (còn 47 nút ·
  100 thông tin). Mô hình NGHE **không đổi** (zipformer-vi; giọng thật owner 28/30). **Gói giọng ĐỌC** Piper vẫn tải
  trong app một lần. Cấu hình cũ trỏ mã đã gỡ tự dọn khi mở app (log `KachiWorkspace [dọn ô]`). 🚗 chưa đo trên xe:
  độ trễ nói→chạy, vuốt trong ô, cốp/AC AUTO (spec S), ngưỡng VAD (`voice_endpoint_floor_cap` chỉnh qua bridge).
- [ĐO 2026-09-13 17:55] Lần ba: 1.43 → 1.44 cùng đường (deep-link `open_settings_group=system` → Kiểm tra cập nhật) — `versionName=1.44`, launcher resume.
- [ĐO 2026-09-13 17:04] Lần hai: 1.42 → 1.43 qua Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật — dialog "New version: v1.43" → cài → `versionName=1.43`, KachiHomeActivity resume.
- [ĐO 2026-09-13] Đã kiểm end-to-end trên máy ảo: 1.41 → 1.42 (thấy bản mới, tải, cài qua dadb, tự mở lại sau 5 s). Máy ảo cần
  `adb tcpip 5555` + `adb reverse tcp:5555 tcp:5555` để app nối được loopback; xe thật có adbd mạng sẵn.
- ⚠ **Từ 1.59 APK còn ~35 MB** ([ĐO] 53 MB ở 1.58 → 35 MB): V2 pha NGHE mang `libsherpa-onnx-jni.so` +
  `libonnxruntime.so`. Từ 1.59 **chỉ chở `arm64-v8a`** (bỏ `armeabi-v7a`, −18 MB) — mọi dump xe chỉ thấy lib
  arm64, đầu xe DiLink 3/4/5 (Android 10/12) đều SoC 64-bit. Nếu một đời DiLink 32-bit-only báo lỗi cài
  `INSTALL_FAILED_NO_MATCHING_ABIS` → thêm lại `armeabi-v7a` trong `app/build.gradle.kts` (xem chú thích ở đó).
- ⚠ **Mô hình nhận dạng KHÔNG nằm trong APK.** `zipformer-vi-2025-04-20` (sherpa-onnx, ~266 MB) tải riêng **một
  lần** vào `filesDir/sherpa/` qua *Cài đặt › Hệ thống & quyền › Nâng cao › Nhận dạng giọng nói (tại máy)*. Lý do:
  nhét vào APK thì **mỗi bản vá một dòng chữ** cũng bắt người dùng tải lại cả mô hình. Gói được ghim **sha256 +
  kích thước** (xem `VoiceModelManifest`), và có đường **gỡ** ngay cạnh nút tải.
- ⚠ **Cài tay báo "Fail in installation of desktop apps"**: xảy ra khi **CHÉP APK vào xe rồi TAP để cài** — trình
  cài GUI của ROM DiLink từ chối một APK tap-vào trở thành app **launcher/home (desktop)**. Cách sửa CHẮC: cài
  bằng **adb**, đừng tap → `adb install -r Kachi-<ver>-release.apk` (`pm install` bỏ qua cổng GUI này; đây cũng là
  đường OTA dùng). Xem `docs/diagnostics/oncar-bugs-2026-09-15.md`.
- ⚠ **(Trường hợp khác) signature mismatch**: nếu xe đang có bản Kachi ký **khoá khác** (bản trước 1.41 / debug),
  đè lên sẽ `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ⇒ `pm uninstall com.byd.launcher` một lần rồi cài lại.
- Build: `./gradlew :app:assembleRelease` (cần `keystore.properties` ở gốc repo; thiếu ⇒ build release fail có chủ ý),
  rồi `cp app/build/outputs/apk/release/app-release.apk apk/Kachi-<ver>-release.apk`, commit + push lên `main`.

**(EN)** The on-car app polls this folder (GitHub Contents API, branch `main`, repo `dangkhoi/byd-kachi`) for a newer
`Kachi-<ver>-release.apk`, downloads it and installs over the dadb loopback (`pm install -r`). Keep a single latest file,
signed with Kachi's own key (from 1.41). Builds signed with an older key must be uninstalled once before OTA works.
