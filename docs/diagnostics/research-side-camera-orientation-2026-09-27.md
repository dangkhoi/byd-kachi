# Camera gương xi-nhan: ngành để ảnh ĐỨNG hay XOAY 90°? — research 2026-09-27

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 (off-car, 5 góc research, **không sửa mã**) · **Mục đích**: trả lời
> bằng chứng cứ *"ngành làm gì"* cho câu hỏi owner đặt trên xe 27/09 — đang xoay **L90** để hình "dọc theo thân xe, đuôi
> ở dưới" nhưng lo **cột nhà/cột đèn nằm ngang**. Doc này chỉ **đưa chứng cứ + khuyến nghị**; đổi mặc định là quyết định
> của owner (steering `trace-den-tan-cung`). Liên quan: backlog `ONCAR-2026-09-27` F1 (khung thô 4 dải ĐỨNG),
> `oncar-runbook-2.75.md` CAM-C2, `electro-camera-RE-2026-09-26.md` §5 K11/K12, `offcar-2026-09-26/camera-view-electro-a.md`.

Nhãn: **[ĐO]** = ảnh/manual/source đọc trực tiếp · **[SUY]** = mô tả chữ khớp, chưa thấy ảnh · **[ĐOÁN]** · **[CHƯA BIẾT]**.

---

## 1. Câu hỏi

| # | Câu | Vì sao cần |
|---|---|---|
| Q1 | Hệ camera gương/điểm mù của ngành hiện ảnh **ĐỨNG** (chân trời ngang) hay **XOAY 90°** (thân xe dọc theo mép)? | Kachi 2.67–2.74 mặc định L90/R90, code tự gắn `[ĐOÁN — CHƯA đo trên xe]` (`CameraSignalPolicy.kt:100-103`) |
| Q2 | Đuôi xe / thân xe nằm ở **mép nào** của khung? | Owner muốn "đuôi ở dưới"; ngành đặt ở đâu? |
| Q3 | Ảnh có **lật gương** (mirror) không? | Kachi **không có** cờ lật (grep `cameraMirror/cameraFlip` = 0) |
| Q4 | **Hình khung + vị trí** (tròn trong cụm? chữ nhật ngang?) | Kachi có khung tròn góc IVI (2.74) và dự tính chiếu cụm 1920×720 |

Bối cảnh đo thật [ĐO F1 27/09]: khung HAL `5120×960` = 4 dải fisheye **đứng** (mặt đất dưới, thân xe góc dưới-trong);
xoay ±90 của 2.67–2.73 "làm hình nghiêng (ảnh vốn đứng)"; owner sau đó chọn ↺90 đuôi dưới làm mong muốn cuối.

---

## 2. Bảng chứng cứ theo hãng (chỉ giữ [ĐO] và [SUY] có mô tả chính thức)

| Hãng | Sản phẩm | Đặt ở đâu | Hướng ảnh | Đuôi/thân xe ở đâu | Mirror | Hình | Khi nào hiện | Mức | Nguồn |
|---|---|---|---|---|---|---|---|---|---|
| Hyundai | BVM — Tucson NX4 2025 (manual fig.) | Cụm: **thay đồng hồ** bên đang xi-nhan | ĐỨNG | thân xe mép **trong** dọc, đường sát bánh sau **dưới**, xe tới từ chân trời trên | có | **tròn** (cỡ đồng hồ) | xi-nhan; tắt khi hết | [ĐO] | ownersmanual.hyundai.com NX4/2025 `id54b128f042b` |
| Genesis | BVM — G70 IK 2026 | Cụm, thay đồng hồ | ĐỨNG | như Hyundai | có | tròn | xi-nhan | [ĐO] | ownersmanual.genesis.com IK/2026 `id42a5010304a` |
| Kia | BVM — EV9/EV6 (chỉ text manual) | Cụm | ĐỨNG (cùng phần mềm HMG) | — | ? | tròn/chữ nhật | xi-nhan | [SUY] | kia.com ev9-manual t00757 |
| Honda | LaneWatch 2013–22 (2 ảnh thật + manual) | Màn giữa, **toàn màn** | ĐỨNG, ngang | thân xe mép **trái** (trong) cho cam phải; 3 vạch khoảng cách **ngang** | có | chữ nhật ngang | xi-nhan phải / nút | [ĐO] | commons `2016_Honda_Odyssey_LaneWatch_screen.jpg`; Honda MY20 Ridgeline LaneWatch PDF |
| Lexus | Digital Side-View Monitor ES (2018) | Màn 5" chân cột A, mỗi bên | ĐỨNG | thân xe mép trong; xe theo sau giữa chân trời | có | chữ nhật ngang | luôn; xi-nhan ⇒ **mở rộng góc**, không xoay | [ĐO] | global.toyota newsroom 24544476 |
| Mercedes Trucks | MirrorCam Actros (2 ảnh cabin) | Màn **dọc** 720×1920 cột A | ĐỨNG trong cửa sổ dọc (main trên / wide dưới) | trailer mép trong từ dưới lên chân trời | có | chữ nhật dọc, chia 2 | luôn; **pan** khi vào cua | [ĐO] | commons `Truck_digital_mirror_left/right.jpg` |
| Stoneridge-Orlaco | MirrorEye (bus brochure · Peregrine MP manual · truck sheet) | Màn dọc 12,3"/15" cột A | ĐỨNG, **2 ô đứng xếp chồng** (Class II trên, IV dưới) | thân xe mép trong; xa ở đỉnh ô trên | có; manual liệt kê "Mirrored/Flipped image" = **LỖI phải dừng xe** | chữ nhật dọc | luôn | [ĐO] | tromanind MirrorEye PDF; kwd-corp UM0972270A01 |
| Volvo Trucks | CMS MIRCMS fact sheet 2024 | Màn dọc cột A | ĐỨNG, main trên / wide dưới | trailer mép **gần tài xế**; vạch 25/50 m **ngang** | có | chữ nhật dọc | luôn; zoom-out + auto-pan | [ĐO] | stpi.it.volvo.com 333502169 |
| Audi | Virtual mirror e-tron (panel thấy, ảnh sống chưa) | OLED 1280×800 đỉnh cửa | ĐỨNG | — | có (mô tả "như gương") | chữ nhật ngang, viền lệch giả gương | luôn; xi-nhan ⇒ **nới góc phía ngoài** (Turnview, NHTSA ANPRM) | [SUY] | audi-technology-portal; 84 FR 54533 |
| Hyundai | DSM Ioniq 6 (ảnh showroom) | OLED chân cột A + BVM tròn trong cụm | ĐỨNG | — | ? | hình gương (thang cong) | luôn (DSM) / xi-nhan (BVM) | [ĐO hướng] | commons `Hyundai_IONIQ_6_…(26).jpg` |
| Tesla | Blind Spot Camera S/X 2025.14 (ảnh release note) · 3/Y góc màn giữa | Cụm bên đang xi-nhan · góc dưới-trái màn giữa | ĐỨNG, ngang | xe tới từ xa (trên/giữa) lớn dần xuống dưới; làn kề phía **ngoài** | có (suy từ hình học) | chữ nhật ngang bo góc | xi-nhan / chạm icon | [ĐO] | notateslaapp 2025.14 PNG; cleantechnica 2022-09-17 |
| Rivian | BSM Live Camera View 2024.19 | Cụm, bên xi-nhan, **thay widget trái** | ĐỨNG, ngang | thân xe tối mép trong/dưới | ? | chữ nhật ngang bo góc | xi-nhan | [ĐO] | riviantrackr 2024-19 |
| Li Auto | HUD 数字后视镜 OTA 5.1.0 | **HUD** (xe không cụm), cạnh mũi tên nav | ĐỨNG, ngang | xe kề giữa khung lớn dần | ? | chữ nhật ngang | xi-nhan | [ĐO] | 163.com J00M9B2C0518D9G1 |
| NIO | HUD ảnh điểm mù ES6 Banyan | HUD bên xi-nhan | ĐỨNG, ngang | — | ? | chữ nhật ngang | xi-nhan | [ĐO] | ảnh 丁当号 (Bing images) |
| Zeekr | 侧盲点画面 001/7X/X | HUD hoặc màn giữa (chọn) | ĐỨNG | — | ? | — | xi-nhan ở D; <20 km/h 360, >20 cam sau-bên | [SUY] | m.pcauto.com.cn 1044068/2053020 |
| XPeng | 360 đổi sang cam sau-bên khi xi-nhan | Trong UI 360 màn giữa | ĐỨNG | — | ? | ô camera của app 360 | xi-nhan khi 360 mở | [SUY] | xpeng.guru/article/dashcam |
| Lucid | Air blind-spot video | Cụm Glass Cockpit | ĐỨNG | — | ? | — | xi-nhan | [SUY] | greencarreports 1138294 |
| Ford | Trailer Side View F-150 2022+ | SYNC màn giữa, chia 50/50 | ĐỨNG | — | ? | chữ nhật | xi-nhan, có trailer | [SUY] | f150gen14 thread 20777 |
| Brandmotion · Rydeen · BOYO · Rostra · Garmin BC50 | Kit aftermarket analog / màn gương | Đầu radio (AV-in) hoặc màn gương | ĐỨNG — **không sản phẩm nào có nút xoay 90°**, chỉ mirror/normal (+ Garmin lật dọc) | — | chọn được | chữ nhật ngang toàn màn / split | xi-nhan (giữ ~12 s / delay) | [ĐO Garmin, Brandmotion trigger] · [SUY ảnh] | garmin webhelp BC50; brandmotion 9002-2904v2 PDF; rydeenmobile |

Chưa kiểm / không so được (một dòng): Polestar 3 không có cam sau-bên (dùng 360); Nissan chỉ radar BSW; Mercedes xe con chỉ radar;
Mobileye Shield+ / INNOVV không có video; Denza/Han/Tang/Yangwang chưa có ảnh nào.

---

## 3. Chuẩn / quy định

| Nguồn | Điều khoản đọc được | Nói gì về hướng ảnh | Mức |
|---|---|---|---|
| UN R46 (04 series, OJ L 237/2014) | 15.2.4.2 (Class II) / 15.2.4.3 (Class III): trường nhìn "bounded by a plane … passing through the outermost point of the vehicle on the driver's side"; 6.2.3.1 CMS "render this image without the need for interpretation"; 6.2.2.2 blooming ≤15 %, contrast ISO 15008 | Mép **trong** trường nhìn hợp pháp = **chính thân xe**; không có điều nào cho phép/nhắc ảnh xoay | [ĐO] EUR-Lex CELEX:42014X0808(02); Rev.7 (16.1.x CMS) **[CHƯA BIẾT]** (PDF gated) |
| ISO 16505:2019 | TOC: 6.2.1 Default view · 6.2.3 Temporary modified view · 6.5 Magnification factor · 6.6 Magnification aspect ratio · 6.7 Monitor integration; Intro: dựa trên "properties of conventional … mirror systems" | Chuẩn quản **độ phóng đại/tỉ lệ/latency**, không phải xoay; xi-nhan = "temporary modified view" (nới góc) | [ĐO TOC/intro] iTeh sample; thân điều khoản **[CHƯA BIẾT]** (trả phí) |
| FMVSS 111 S5.2.1 + ANPRM 84 FR 54533 (2019) | Gương phẳng unit-mag bắt buộc; "line of sight may be partially obscured by rear body or fender contours"; Audi Turnview = "aspherical zone … on the outer side" | Thân xe **được** che mép trong; xi-nhan ⇒ nới phía **ngoài**, không xoay | [ĐO] public-inspection.federalregister.gov 2019-22769 |
| TfL Bus CMS spec 10/2022 | "Images for the offside and nearside FOV shall be presented on the respective side of the driver"; "non-continuous images … clearly separated" | Trái hiện bên trái, phải bên phải; ô xếp chồng tách rõ; không nói xoay | [ĐO] foi.tfl.gov.uk FOI-1423-2223 |
| BASt F 112b (2015/16) | Fig. 8/13: màn ngang, chân trời phẳng, thân xe dải tối mép **trong**, vùng aspherical mép ngoài; 22/24 chọn vị trí cột A; "information about the left side should always be displayed on the left-hand side" | ĐỨNG + lật gương; vị trí gần gương thật | [ĐO hình] nw-verlag F112b PDF |
| Bernhard & Hecht, Human Factors 63(3) 2021 | "vehicle's back should always be visible" — thấy đuôi xe **xoá** sai lệch ước lượng khoảng cách do cao độ camera | Phải giữ **thân xe trong khung** làm mốc | [ĐO PDF] uni-mainz 2021bernhard-ups-downs-cms |
| Nottingham 2021 on-road | ảnh "flipped horizontally … to correspond with the reflected image visible in the conventional mirror" | Lật gương là **chuẩn ngầm** | [SUY] trích search, PDF bị chặn |
| NHTSA DOT HS 812 582 (2018) | wide-FOV nhồi vào màn nhỏ ⇒ vật xa **bé/khó nhận** ("minification"); màn thấp làm liếc ngắn | Cảnh báo cho khung nhỏ góc IVI | [ĐO text] nhtsa.gov 13640 |
| HLDI 31-20 (2014) LaneWatch | giảm claim **không** có ý nghĩa thống kê (−2,5 %, CI −7,8…+3,1) vs radar BSM −14 % | Cam ở màn giữa yếu hơn cảnh báo tại gương (nhiễu: chỉ bên phụ) | [ĐO] iihs.org bulletin |

Kết: mọi văn bản coi CMS là **thay gương**: thân xe = mép trong, xa = chân trời, xi-nhan = nới góc phía ngoài. **Không** văn bản nào bàn hay cho phép ảnh xoay 90° / kiểu bản đồ.

---

## 4. Hệ BYD + app RE cục bộ

| Nguồn | Hướng ảnh | Chi tiết | Mức |
|---|---|---|---|
| **HAL DiLink** `AVMCamera` id 1, khung thô 27/09 (`strips-lr.png`, F1) | **ĐỨNG** native | 4 dải 1280×960 fisheye, chân trời ngang, thân xe/cửa ở góc **dưới-trong** mỗi dải (trái: dưới-trái; phải: dưới-phải). HAL có `VIEW_DECUSSATION_HFLIP=6 / VFLIP=7` (`DiLinkCameraConstants.java:53-54`, fw-2602 jadx-l3-new) nhưng **0 caller** trong mọi dump | [ĐO ảnh + hằng]; HAL đã lật gương chưa **[CHƯA BIẾT]** |
| **OEM DiLink 360** (Dolphin, ảnh Yiche; cùng app Seal/Han/Song) | ĐỨNG, nhưng là cam AVM **nhìn xuống** | Trái = top-down, phải = một cam fisheye ngang, thân xe cong theo **mép trên**; 转向联动 chỉ bật khi <30 km/h, gear≠R ⇒ OEM **không có** view điểm mù tốc độ cao | [ĐO ảnh] news.yiche.com 83780983 |
| **Kinex** `com.lexwah.kinex` | **ĐỨNG mặc định** | `Y0/Y.java:78-88` rotation 0/flipH false trừ khi bật "custom orientation"; crop `x[0,25..0,35]`/`[0,65..0,75]` (`C0094o.java:70,73`, y hệt Kachi); ô 512×960 **dọc không xoay**; xoay 90/270 ⇒ 640×480; trigger `light.onLightOn` type 4/5 | [ĐO] `../jadx-kinex/sources/…` |
| **Electro** `br.com.rory.electro` 1.13.0 | **XOAY** trong preset | 2 ô: trái 90 / phải 270 (`p056o/C0849x.java:24-25`); chữ thập: 270/90/0/180 = xe nhìn từ trên (`C0825a0.java:22-25`); nhưng rotate/flip là **thuộc tính từng ô do user** (`ic_side_camera_rotate_left/right`), dải nào vào ô nào nằm trong `libelectropkg.so` | [ĐO preset]; preset mặc định của feature **[CHƯA BIẾT]** |
| **BYD Extend** `byd-turnsignal-cameraview` (Sea Lion 07, DL5) | ĐỨNG, chỉ xoay **−30/+30** để cân chân trời | `CameraDefaults.java`: REAR_LEFT rot −30 · REAR_RIGHT +30 · `mirrorHorizontally=true` cả hai · khung 1,62:1 ngang · trái x=0 / phải x=1 | [ĐO source] |
| **BYDMate** | ĐỨNG mặc định; "Vertical camera window" xoay 90 là **tuỳ chọn**, tác giả thừa nhận có thể ngược chiều | trái → cụm, phải → ô nhỏ màn giữa; viền cam khi radar thấy xe | [ĐO README] |
| **DashCast · OpenBYD · Kiki** | không render camera gương | DashCast 0 hit camera; OpenBYD chỉ `sendCameraGuidanceInfo`; Kiki gọi app OEM LEFT/RIGHT | [ĐO grep] |
| **Kachi 2.74** | **XOAY** mặc định L90/R90 | `CameraSignalPolicy.kt:100-103` tự ghi `[ĐOÁN — CHƯA đo trên xe]`; **không** có cờ lật; F1 27/09: xoay làm "hình nghiêng (ảnh vốn đứng)" | [ĐO code] |

---

## 5. Tổng hợp

| Hướng | Hệ có [ĐO] (gộp theo họ sản phẩm) | Đếm |
|---|---|---|
| **ĐỨNG** | Hyundai BVM · Genesis BVM · Honda LaneWatch · Lexus DSM · MB Trucks MirrorCam · Orlaco/Stoneridge MirrorEye · Volvo CMS · Ioniq 6 DSM · Tesla · Rivian · Li Auto · NIO · BYD OEM 360 · BYD Extend · BYDMate · HAL DiLink native · Kinex · BASt prototype | **18 ĐO** (+11 [SUY] cùng chiều: Kia, Audi, Zeekr, XPeng, Lucid, Ford, Continental, Brandmotion, Rydeen/BOYO/Rostra, Nottingham, NHTSA prototype) |
| **XOAY 90°** | Electro (preset cụm, user chỉnh được) · Kachi 2.74 (tự nhận ĐOÁN) | **2 ĐO** — cả hai đều là app cộng đồng BYD chiếu cụm, không có hãng xe/chuẩn nào |

- **Mặc định ngành = ĐỨNG, chân trời ngang** [ĐO 18/20]: xe tới **từ chân trời đi xuống**, thân xe là **mép trong dọc**, mặt đất sát bánh **ở dưới**. Cửa sổ "dọc" của xe tải là **hai ô đứng xếp chồng**, không phải ảnh xoay. Xi-nhan ⇒ hiện/nới góc/pan, **không** xoay.
- **Lật gương** [ĐO 9 hệ thấy được: Hyundai, Genesis, Honda, Lexus, MB Trucks, Orlaco, Volvo, BASt, BYD Extend]: mọi hệ nhìn rõ đều theo tay gương kính (thân xe mép trong). Orlaco coi ảnh **không** lật là lỗi phải dừng xe. Kinex mặc định không lật [ĐO] ⇒ hoặc HAL đã lật, hoặc Kinex sai chiều — **[CHƯA BIẾT]**, đo được (§6).
- **Hai ngoại lệ** xoay: Electro xoay để dựng bố cục "xe nhìn từ trên" trên cụm (ô 0,5×1 dọc); Kachi kế thừa ý "dọc theo thân xe". Không ngoại lệ nào có phép đo người dùng hay quy định đứng sau. Lo ngại của owner (cột đèn nằm ngang) chính là hệ quả trực tiếp của xoay ảnh vốn đứng [ĐO F1].
- HF: giữ **thân xe trong khung** (Bernhard & Hecht [ĐO]); trái hiện bên trái/phải bên phải (BASt, TfL [ĐO]); khung nhỏ + FOV rộng ⇒ vật xa bé (NHTSA [ĐO]).

---

## 6. Khuyến nghị cho Kachi

> **KHUYẾN NGHỊ (owner quyết):** (1) Mặc định xoay **0 (đứng)** cho **cả hai bên** — khớp HAL native, Kinex, và 18/20 hệ [ĐO]; **giữ** L90/R90/180 làm tuỳ chọn từng bên, **không** ghi đè giá trị owner đã tự chọn (chỉ đổi `defaultRotation`, migration giữ pref hiện có). (2) Thêm tuỳ chọn **lật ngang** (`camera_mirror`, mặc định theo kết quả đo CAM-M1), vì mọi hệ [ĐO] đều theo tay gương và Kachi hiện không có cờ này. (3) Không cắt mất **thân xe** khỏi khung (mốc khoảng cách), thân xe ở mép **trong**. (4) Khung **tròn góc IVI**: nội dung đứng bên trong (đúng kiểu BVM Hyundai); **chiếu cụm 1920×720**: một ô **chữ nhật ngang** đặt ở nửa bên đang xi-nhan (Tesla/Rivian/Hyundai), không dùng ô dọc xoay. (5) Đo trước khi chốt: CAM-C2 viết lại như dưới.

| # | Việc | Ghi chú |
|---|---|---|
| 6.1 | `CameraSignalPolicy.defaultRotation` → `"0"` hai bên; giữ `ROTATIONS`; `migrateLegacy` không đụng pref đã đặt | Owner 27/09 đã chọn ↺90 trên xe ⇒ pref của xe owner **giữ nguyên** cho tới khi owner tự đổi sau CAM-C2 |
| 6.2 | Pref `camera_mirror` (bool, mỗi bên hoặc chung) → GL: đảo trục u trong quad/texcoord (Kinex làm bằng 8 bộ đỉnh, `RunnableC0171e.java:113-122`) | Mặc định **[CHƯA BIẾT]** tới khi đo CAM-M1; **không** dùng `camera_hal_mode=6` làm mặc định (HAL toàn hệ, CAM-HAL-MODE-6 owner quyết) |
| 6.3 | Khung tròn: giữ; nội dung 0°, tâm vòng ảnh; thân xe góc dưới-trong phải còn thấy | Hyundai vẽ tốc độ **trong** vòng — gợi ý sau: km/h nhỏ trong khung |
| 6.4 | Chiếu cụm 1920×720 (dải giữa): ô ngang ≈ 4:3→16:10, cao ~720, đặt trái/phải theo bên; **không** bịt cả dải | Tesla S/X chiếm ~40 % cụm bên xi-nhan; Rivian thay widget bên đó |
| 6.5 | Vạch khoảng cách ngang (Honda 3 vạch / Volvo 25-50 m) | **Hoãn**: cần hiệu chỉnh trên xe; ghi backlog, không làm mù |

**CAM-C2 viết lại (đo trên xe, ~5 phút, `camera_render=GL`, bộ tham số owner duyệt F55/K100/S130):**

| Bước | Làm | Kì vọng nếu "ĐỨNG" đúng | Ghi |
|---|---|---|---|
| C2-a | Cả hai bên xoay **0**, xi-nhan trái rồi phải, đứng yên cạnh cột đèn/tường | cột **thẳng đứng**, chân trời ngang, thân xe góc **dưới-trong**, mặt đất dưới | screenshot 2 bên + log `overlay show … rot=0` |
| C2-b | Đổi **L90/R90** cùng cảnh | cột nằm **ngang** (xác nhận nỗi lo owner) | so 2 ảnh, owner chọn bằng mắt |
| C2-c (**CAM-M1**) | Người đứng **sau-trái** xe ~3 m, sát thân xe; nhìn ảnh trái xoay 0 | Nếu HAL đã lật gương: người ở **giữa/ngoài**, thân xe mép **phải** (trong). Nếu chưa lật: thân xe mép **trái** | quyết định mặc định `camera_mirror`; đối chiếu gương kính thật cùng lúc |
| C2-d | Xe chạy 40 km/h, xe khác vượt bên trái | xe tới **từ trên** (chân trời) lớn dần **xuống dưới-trong** | xác nhận "đuôi ở đâu" theo cách ngành |

---

## 7. Lỗ hổng chưa kiểm

- **UN R46 Rev.7 16.1.x** (CMS Class I–IV) và **ISO 16505 thân điều khoản** (6.2.1/6.4/6.7): chưa đọc ⇒ chưa xác nhận có câu "as seen in a mirror" hay không; luật "tay gương" ở trên là **quan sát sản phẩm**, không phải trích quy định. SAE J3155 chưa mở.
- Chưa có ảnh: Kia BVM, Audi ảnh sống, Ford, Zeekr, XPeng, Lucid, Continental, kit aftermarket (Amazon/Walmart/ManualsLib 403).
- **HAL DiLink đã lật gương chưa** — [CHƯA BIẾT], đo CAM-M1. Electro: preset mặc định + dải→ô nằm trong `libelectropkg.so`.
- **OEM BYD Seal**: chỉ thấy ảnh Dolphin; Seal DiLink không có bằng chứng view sau-bên nào; Seal 05 DM-i có cam chắn bùn sau nhưng chưa thấy UI.
- Không tìm được nghiên cứu HF nào **so trực tiếp** ảnh đứng vs xoay 90° cho phát hiện điểm mù — bằng chứng chỉ có về vị trí màn, cao độ cam, FOV, mốc thân xe. Nếu owner vẫn thích xoay sau CAM-C2, đó là lựa chọn UX chưa có dữ liệu bác bỏ, chỉ **trái quy ước ngành**.
- Mọi khẳng định "Tesla lật gương", "Rivian thân xe mép trong" là **suy từ một ảnh nhỏ** ([SUY]), không phải văn bản hãng.
