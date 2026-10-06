# Camera xi-nhan · lấy thêm phía SAU xe từ mắt cá gương — vì sao khung hiện tại chỉ thấy đoạn bánh trước → bánh sau, và cách mở ra (off-car 2026-10-06)

> **Trạng thái**: Current · **Cập nhật**: 2026-10-06 (research + thiết kế off-car: đọc mã HEAD `d320ece` + tài liệu 26–29/09;
> **không** sửa mã, **không** xe, **không** gradle) · **Mục đích**: trả lời owner 06/10 — *"cái camera điểm mù ấy, mình đang cho
> nó ở thân xe từ bánh trước cho đến chưa hết bánh sau, trong khi gương cầu fisheye lúc demo nó nhìn hẳn ra sau rất xa, rất ok,
> mình cắt rồi nên thấy có đoạn ngắn từ bánh sau lên bánh trước, phí lắm, có lấy thêm được ngoài sau không?"* (kèm *"1 cái trái
> thôi nó đã cho hình rất rộng rãi và sâu, mình cắt hơi lố"*): đường ảnh hôm nay, phần *sau xa* nằm đâu trong khung thô, cài đặt
> hiện có làm được gì ngay, phương án, đo gì trước, kế hoạch 2.92/2.93.
> Liên quan: `electro-camera-RE-2026-09-26.md` · `research-side-camera-orientation-2026-09-27.md` ·
> `offcar-2026-09-26/camera-dewarp-math.md` · `offcar-2026-09-26/camera-dewarp-gl.md` (§B dịch khung, §F một kênh) ·
> `offcar-2026-09-26/camera-frame-capture.md` · `offcar-2026-09-27/camera-ia-profile.md` (§8) · `oncar-runbook-2.75.md` §4 ·
> backlog `ONCAR-2026-09-27` (F1 · G7) · `CAM-DEWARP-PAN` · `CAM-CHANNEL`.

Nhãn: **[ĐO mã]** đọc mã HEAD `d320ece` (`file:line`) · **[ĐO tính]** số tính lại trong phiên này từ đúng công thức
`CameraDewarp.kt:301-317` + `CameraGlUniforms.kt:173-224` với hằng của mã, không giả định vật lý (script §9) · **[SUY]** suy
luận — gồm **mọi số đo cũ 27–29/09** (luật `conversation-protocol.md` P1.1: dữ liệu phiên trước tối đa là [SUY]) ·
**[ĐOÁN]** · **[CHƯA BIẾT]**. Góc vật lý θ ghi "@376" = quy đổi theo giả thuyết f-θ thật ≈ **376 px/rad** (§2.2) ⇒ luôn [SUY].

---

## 0. Kết luận

| # | Kết luận | Mức |
|---|---|---|
| K1 | **Có lấy thêm được.** Phần *sau xa* **đã nằm trong** dải mà Kachi đang lấy (`STRIP` = trọn 1280×960 của gương), ở **mép phía đuôi** của dải: gương trái x ≈ 0–141 px (pano x 1280–1421), gương phải x ≈ 1139–1280 px (pano x 3699–3840). Thứ vứt nó đi là **phép chiếu phối cảnh thẳng** của đường `GL`, không phải vùng cắt, không phải xoay | px: [ĐO tính]; "có nội dung ảnh ở đó": [SUY — "kín khung góc tối" 27/09] |
| K2 | Phối cảnh thẳng `r = F·tanθ` không vẽ được θ ≥ 90° và với F 55 % khung dừng ở **θ_model 76,0°** (giữa mép) / 78,7° (góc). Gần mép, độ phóng xuyên tâm **7,2×** (tâm 0,44×) ⇒ ¼ bề dài khung phía đuôi chỉ chứa vành 63–76°; **50 %** khung là nửa phía TRƯỚC gương; phần θ ≥ 76° chiếm **0,1 %** khung | [ĐO tính] |
| K3 | Quy ra vật lý: với camera AVM chúc xuống 60–75° thì 76° ≈ **bánh sau nằm sát mép khung**, bánh trước lọt trong — đúng câu owner; xe làn bên cạnh cách ≥ 5 m phía sau rơi vào 74–89° ⇒ **ngoài khung** | [ĐOÁN — tư thế lắp chưa đo] |
| K4 | Dịch khung / phóng / tiêu cự **không chữa được** trong mô hình thẳng: `pan_x −15` chỉ tới 79° và dồn 15 % khung vào dải 76–79° phóng 7× — đúng *"có dịch ra sau tý mà sau đó méo lắm"* owner đo 27/09 (G7); `scale 150` tới 88° nhưng 9,7 % khung đen + cong | [ĐO tính] + [SUY cũ G7] |
| K5 | **Hôm nay, không build**: *Cài đặt › Tiện nghi xe › Camera theo xi-nhan › Nắn hình* **TẮT** ⇒ thấy tới mép vòng ảnh (gương cầu thô, 11 % khung cho vùng ≥ 76°). Qua cầu adb: `camera_dewarp_amount 50` ⇒ tới ~87° | [ĐO tính] |
| K6 | **Chữa đúng, rẻ (2.92)**: thêm **một** tham số họ phép chiếu `κ` — `r = κ·F·tan(θ/κ)` (κ = 1 là hôm nay **từng bit**, κ = 2 là stereographic) — cộng dịch khung về đuôi, đóng gói thành chip *Tầm nhìn: Sát thân xe · Xa ra sau*. Bộ ứng viên tới ~95° với **0 % đen**, 20–30 % khung cho vùng sau xa, độ phóng ≤ 3,1× (§4.3) | công thức: [ĐO tính]; bộ số: [ĐOÁN] tới khi owner nhìn |
| K7 | **Bản "gương ảo" (2.93)**: camera ảo quay về sau quanh trục ĐỨNG của thế giới + phối cảnh thẳng ⇒ đường thẳng vẫn thẳng, sau xa nằm giữa khung. Cần hiệu chỉnh từ khung thô: góc chúc δ, f-θ, tâm, equidistant hay equisolid | [SUY] |
| K8 | **Đo trước**: ảnh khung thô 27/09 **không còn trên Mac**; trên xe thì [CHƯA BIẾT] (bị dọn bởi `KEEP_PNG = 10` và/hoặc `DiagStorageCap` 150 MB xoá-cũ-nhất). Kachi **chưa có** đường chụp khung thô trong app (chỉ cầu adb) ⇒ đề xuất nút trong *Chẩn đoán* + bản sao ra Thư viện ảnh (§5) | [ĐO mã] |

---

## 1. Đường ảnh hôm nay (Q1)

### 1.1 Chuỗi bước

| Bước | Hôm nay (Seal, xe owner) | Mã | Mức |
|---|---|---|---|
| 1 · Nguồn | `AVMCamera` id 1 = `pano_h`, buffer **5120×960** = 4 dải dọc **1280×960**, thứ tự **0 sau · 1 TRÁI · 2 PHẢI · 3 trước**; mỗi dải một vòng mắt cá **ĐỨNG** (đất dưới, chân trời ngang), *"kín khung góc tối"* | `CameraPanoCrop.kt:37-60`, `CameraSignalPolicy.kt:378-381` | hằng [ĐO mã]; bố cục + hình dạng [SUY — số đo cũ Seal 27/09, SL6 28/09] |
| 2 · Dải theo bên | trái → dải 1 (pano x 1280–2559), phải → dải 2 (2560–3839) | `CameraPanoCrop.kt:106,149` | [ĐO mã] |
| 3 · Vùng cắt | `camera_span` hồ sơ Seal = `STRIP` ⇒ **trọn dải** | `CameraPanoCrop.kt:165,171-174`, `ClusterProfile.kt:171-178` | [ĐO mã] |
| 4 · Nắn (`camera_render = GL`) | phối cảnh thẳng quanh tâm dải: `F 55 % · K 100 % · S 130 % · độ nắn 100 % · tâm 0 · dịch 0` | `CameraDewarp.kt:301-317`, `CameraDewarpShader.kt:118-142`, `CameraGlUniforms.kt:173-224` | [ĐO mã]; bộ số trên xe owner [SUY — B0 27/09: pref owner giữ qua nâng cấp] |
| 5 · Xoay | xe owner: trái **↺90** (đuôi xuống); hồ sơ mặc định 0/0, pref thắng | `CameraDewarp.kt:264-279` (affine, chạy TRƯỚC nắn) | [ĐO mã]; pref [SUY cũ] |
| 6 · Cửa sổ | màn chính: vùng **495×495** ⇒ khung **371×495** (↺90) / 495×371 (0°); *Tròn*: ô vuông 960×960 giữa dải ⇒ tròn 495; cụm *Theo cụm*: **565×424** (0°) trong dải 140,136–1780,560 | `CameraOverlayView.kt:116,438-442`, `CameraOverlayFrame.fit/tall/cover`, `CameraPanoCrop.kt:194-199` | công thức [ĐO mã]; số px [SUY — log 27/09] |
| 7 · Ngoài ô ⇒ đen | mẫu rơi ngoài `[0,1]²` **của vùng cắt** ⇒ đen đặc ⇒ **không bao giờ lấy pixel ngoài vùng cắt** | `CameraDewarpShader.kt:137-142`, `CameraDewarp.kt:486` | [ĐO mã] |

Xoay trên xe owner [ĐO tính]: ↺90 ánh xạ **mép dưới** khung → mép đuôi của dải (`sample(0.5, 1, −90)` = strip (140,6; 480)) và **mép phải**
khung → đáy dải (thân xe). Tức *"đuôi dưới, thân xe bên phải"* — cùng một khung với rot 0, chỉ quay.

### 1.2 Vùng cắt và `uSrcRect` theo từng lựa chọn [ĐO mã + ĐO tính]

| `camera_span` / hình | Crop pano x | Strip-local px | `uSrcRect` (x,y,w,h) trước `textureT` | K suy ra (nửa-bề-ngang/rad) | Ghi chú |
|---|---|---|---|---|---|
| `NARROW` trái (2.36–2.73, = kinex) | 0,25–0,35 | 0–512 | (0,25; 0; 0,10; 1) | 1,1308 | tâm quang `cx = 1,25` (ngoài ô) |
| `NARROW` phải | 0,65–0,75 | 768–1280 | (0,65; 0; 0,10; 1) | 1,1308 | |
| **`STRIP` trái (Seal)** | 0,25–0,50 | 0–1280 | (0,25; 0; 0,25; 1) | **0,4523** | tâm 0,5 |
| `STRIP` phải | 0,50–0,75 | 0–1280 | (0,50; 0; 0,25; 1) | 0,4523 | |
| *Tròn* 100 % trái / phải | 0,28125–0,46875 / 0,53125–0,71875 | **160–1120** | (0,28125; 0; 0,1875; 1) / (0,53125; …) | 0,6031 | **cắt 160 px mỗi đầu dải** |

Sau đó `textureT` (`CameraGlUniforms.kt:138-139`) đổi thành `(x, 1−y, w, −h)`; lật gương ⇒ `w` âm (`CameraDewarp.kt:405-415`).
`K = (960 / bề ngang ô) / 1,658063` — giả định vòng ảnh Ø = bề cao ảnh 960 px, ống 190° (`CameraDewarp.kt:147-154`,
`CameraGlUniforms.kt:200-206`).

### 1.3 Mô hình nắn và tám núm [ĐO mã]

```
local  = rot(dst) + pan                         // xoay quanh tâm ô, rồi dịch cửa sổ (ô CHƯA xoay)
p      = ((a − cx)·2, (b − cy)·2/aspect)        // đơn vị = nửa bề ngang ô, đẳng hướng
theta  = atan(|p|, F)                            // ĐÍCH = phối cảnh thẳng: r_dst = F·tan θ  (θ < 90° luôn luôn)
r_src  = (K·S)·theta                             // NGUỒN = mắt cá đẳng khoảng: r = f·θ
out    = mix(local, c + dir·r_src, amount)       // ngoài [0,1]² của vùng cắt ⇒ đen
```

| Núm (prefs) | Có hàng UI? | Làm gì | Seal hôm nay |
|---|---|---|---|
| `camera_dewarp_amount` | ✅ ô *Nắn hình* (100/0) | trộn ảnh thô ↔ ảnh nắn (giữa 0–100 không phải mô hình quang học nào) | 100 |
| `camera_dewarp_focal` | ❌ (cầu adb) | `F` = độ phóng khung ra; nửa-FOV = atan(1/F) | 55 % ⇒ F 0,2488 |
| `camera_dewarp_k` | ❌ | f-θ của ống (mô hình) | 100 % ⇒ 0,4523 |
| `camera_dewarp_scale` | ❌ | nhân vào K ⇒ với sâu hơn vào vòng ảnh | 130 % ⇒ K·S = **0,588 = 376,3 px/rad** |
| `camera_dewarp_cx/cy` | ❌ | dời **trục** nắn ⇒ một bên thẳng một bên còng (owner bác 27/09) | 0 |
| `camera_dewarp_pan_x/y` | ❌ | trượt cửa sổ trong mặt phẳng chiếu, trục giữ nguyên (dấu x theo bên, `CameraDewarpPrefs.kt:129`) | 0 |

### 1.4 Vì sao phần sau xa bị cắt — từng nghi phạm

| Nghi phạm | Phán | Bằng chứng |
|---|---|---|
| Vùng cắt | **Không** (với `STRIP`): trọn dải vào ô. *Có* với *Tròn* (cắt x < 160) và với nửa trên/dưới vòng ảnh mà HAL đã cắt (dải cao 960 < Ø vòng) | [ĐO mã] · [SUY cũ] |
| Xoay + tỉ lệ cửa sổ | **Không**: xoay affine trước nắn, cửa sổ đúng tỉ lệ vùng cắt sau xoay ⇒ không mất pixel nào | [ĐO mã]; [SUY cũ: L90 = rot 0 xoay lại, lệch 3,11/255] |
| **Phối cảnh thẳng** | **Có — thủ phạm chính**: (a) θ ≥ 90° không có chỗ trên khung (`forwardSrcToDst` trả `null`, `CameraDewarp.kt:350-356`); (b) F 55 % ⇒ mép khung dừng ở θ_model 76,0° ⇒ pixel cách tâm > **499,4 px** (x < 140,6 bên trái) **không bao giờ được lấy**; (c) độ phóng `F·sec²θ / (K·S)` ⇒ tâm 0,44×, 63° 2,05×, 76° **7,22×** | [ĐO tính] |
| Tiêu cự / phóng | góp phần: chính cặp F55/S130 quyết con số 76° | [ĐO tính] |
| Tâm ở giữa khung | góp phần: **50 %** khung dành cho nửa phía TRƯỚC gương | [ĐO tính] |

**Hàng giữa khung, nửa phía đuôi** (u = 0 là mép đuôi; trên xe owner ↺90 = mép dưới) [ĐO tính]:

| u | 0,00 | 0,10 | 0,20 | 0,25 | 0,30 | 0,40 | 0,45 | 0,50 |
|---|---|---|---|---|---|---|---|---|
| strip x (px) | 140,6 | 162,3 | 196,8 | 222,6 | 258,2 | 385,2 | 496,2 | 640 |
| cách tâm (px) | 499,4 | 477,7 | 443,2 | 417,4 | 381,8 | 254,8 | 143,8 | 0 |
| θ @376 | 76,0° | 72,7° | 67,5° | 63,6° | 58,1° | 38,8° | 21,9° | 0° |

⇒ 25 % bề dài khung phía đuôi (u 0–0,25) chỉ chứa 82 px nguồn (vành 63,6–76°). Góc khung lấy tới (226; 170) và (226; 790)
— hình gối, nên ở hàng trên/dưới mép đuôi còn lùi vào tới x ≈ 226 [ĐO tính].

**Tỉ lệ bề dài khung theo dải góc** (hàng giữa, θ @376) [ĐO tính]:

| Cấu hình | θ 0–45 | 45–63 | 63–76 | **76–90** | **90+** | nửa trước | đen |
|---|---|---|---|---|---|---|---|
| **Hôm nay** F55 S130 κ1 | 12,4 % | 12,0 % | 25,4 % | **0,1 %** | 0 | 50 % | 0 |

### 1.5 Quy ra vật lý — vì sao owner thấy đúng "bánh trước → chưa hết bánh sau" [ĐOÁN]

Giả định (đều [ĐOÁN]): camera cao 1,0 m, chúc xuống δ so với phương ngang, bánh trước 0,9 m trước gương, trục sau 2,0 m sau
gương, tâm bánh thấp hơn camera 0,65 m và lùi vào 0,25 m; xe làn bên tâm cách gương 2,5 m ra ngoài, cao hơn mặt đường 0,7 m.
Góc θ tới trục quang:

| Mục tiêu | δ 45° | δ 60° | δ 75° |
|---|---|---|---|
| tâm bánh trước | 75,6° | 67,4° | 60,3° |
| tâm bánh sau | 82,3° | 78,1° | 74,6° |
| góc cản sau | 86,7° | 84,2° | 82,1° |
| xe làn bên 5 m sau | 69,3° | 74,4° | 80,4° |
| … 10 m | 78,9° | 81,6° | 84,8° |
| … 20 m | 84,4° | 85,7° | 87,3° |
| … 50 m | 87,7° | 88,3° | 88,9° |

Với δ 60–75° (thường gặp ở camera AVM): mép khung 76° ≈ **bánh sau**, bánh trước lọt trong — khớp mô tả owner; **mọi thứ ở làn bên
từ ~5 m về sau** (74–89°) nằm ngoài khung. Chốt δ bằng khung thô (§5, M1).

---

## 2. Phần "sau xa" nằm ở đâu trong khung thô (Q2)

### 2.1 Theo từng camera

| Camera | Dải | Hướng đuôi | Phần bị bỏ phía đuôi (θ > 76°) | Vùng xe làn bên 5–50 m [ĐOÁN mount, @376] | Mức |
|---|---|---|---|---|---|
| Gương **trái** | 1 (pano x 1280–2559) | **−x** (x → 0) | strip x ∈ [0; 140,6) ở giữa cao, [0; 226) ở hàng trên/dưới ⇒ **pano x 1280–1421** | strip x ≈ 55–210, y ≈ 250–465 (nửa trên-giữa, phía ngoài) | hướng: [SUY cũ — runbook 2.75 §4 + G7]; px: [ĐO tính] |
| Gương **phải** | 2 (pano x 2560–3839) | **+x** | strip x ∈ (1139,4; 1280] ⇒ **pano x 3699–3840** | strip x ≈ 1070–1225, y ≈ 250–465 (đối xứng gương) | như trên; bên phải chưa ai đối chứng mắt (G7 chỉ đo bên trái) |

Ba nguồn độc lập nói cùng hướng đuôi: (1) `oncar-runbook-2.75.md` §4 *"về phía đuôi = −x ở dải trái, +x ở dải phải"* [SUY cũ, khung
thô `camera-frame-20260927-095818.png`]; (2) G7 27/09: `pan_x −15` ở gương trái ⇒ owner *"có dịch ra sau tý"* [SUY cũ]; (3) hai vệt
`NARROW` của kinex/Kachi 2.36–2.73 (`x[0,25..0,35]`, `x[0,65..0,75]`) đối xứng gương và đều nằm ở **đầu phía đuôi** của dải —
app kinex gắn nhãn đúng tính năng này là `KinexBlindSpot` (`jadx-kinex/…/Y0/C0094o.java:70,73,318-343`; với camera đơn 1280 nó
lấy `[0..0,35]` / `[0,65..1]`, `:85,88`) [ĐO RE]. ⇒ **Vệt `NARROW` cũ chính là vùng điểm mù nhìn ra sau** (θ ≈ 20° → mép vòng,
ảnh thô, không nắn) — rất có thể là thứ owner gọi là *"gương cầu fisheye lúc demo nhìn hẳn ra sau rất xa"* [ĐOÁN]. 2.74 đổi
sang `STRIP` + nắn quanh tâm dải (owner duyệt 27/09 *"thẳng, tự nhiên, thấy 2 bánh"*) và đánh đổi đúng vùng ấy.

### 2.2 Dải có chứa tới chân trời phía sau không — phụ thuộc f-θ thật

| Giả thuyết | f-θ (px/rad) | Mép dải (r = 640 px) ứng với | Căn cứ | Hệ quả |
|---|---|---|---|---|
| **H1** (nhiều khả năng) | ≈ 376 | **97°** — vòng ảnh 190° kết thúc ở x ≈ 17 px | owner thấy *thẳng* ở S130 (đường thẳng chỉ thẳng khi K·S = f-θ thật) + "kín khung góc tối" (vòng chạm hai mép trái/phải, bị cắt trên/dưới) | dải chứa trọn phần sau xa tới quá chân trời (76° → 97°) |
| H2 (cận xấu nhất) | tới ~480 | 76° | "góc tối" chỉ đòi bán kính vòng < 800 px | dải KHÔNG chứa gì thêm phía đuôi; trọn vòng chỉ có ở nguồn *Một kênh* (`addPreviewSurface(surface, 2/3)`, đã gỡ 2.77 — `camera-ia-profile.md` §8.3) |

H2 mâu thuẫn với lần duyệt S130 (ở f-θ 480, đường thẳng cần S ≈ 166 %, mà owner chê S ≥ 140 là *"nặng"*) ⇒ nghiêng H1 [SUY].
**Chốt bằng MỘT cái nhìn vào khung thô**: ở giữa chiều cao dải, mép đuôi là ảnh hay là cung tối? (§5 M1, câu 1). Dù H1 hay H2, có
**141 px nội dung ảnh** ở mỗi đầu dải mà khung hiện tại không bao giờ lấy [ĐO tính + SUY "kín khung"].

### 2.3 Hai ghi chép mâu thuẫn về vị trí thân xe — cần một cái nhìn

`oncar-runbook-2.75.md` §4 và research §4 ghi *thân xe ở góc **dưới-trái** dải trái*; `camera-cluster-band.md:78` và KDoc
`CameraDewarpPrefs.kt:110-117` ghi *thân xe ở mép **PHẢI** ô gương trái*, cùng dẫn "khung thô 27/09". Khớp nhau nếu câu thứ hai
tả **khung đã xoay ↺90** (đáy dải → mép phải khung) [SUY]. Không ảnh hưởng hướng đuôi (§2.1). [CHƯA BIẾT] cho tới khi xem lại
khung thô; nếu đúng là tả khung xoay thì sửa hai chỗ kia cho khỏi trái nhau (R2.4).

---

## 3. Cài đặt hiện có làm được gì ngay hôm nay (Q3)

Đường: **Cài đặt › Tiện nghi xe › khối *Camera theo xi-nhan*** (`SettingsCatalogGroups.kt:73-76`, `SettingsSectionsCar.kt:77`).
Xem thử ngay không cần ra đường: đổi xong thì **chạm lại** chip *"Xi-nhan trái: dải hình › Tự động"* — mỗi lần chạm đều gọi
`previewSide` (`SettingsRows.kt:168`, `ClusterNavBridgeAutomation.kt:224-228`) và khung giữ tới sự kiện xi-nhan kế tiếp
(`CameraHold`) [ĐO mã].

| # | Đường | Đặt gì | Kết quả (hàng giữa, @376) | Đánh đổi | Mức |
|---|---|---|---|---|---|
| A | UI | *Nắn hình (bớt cong ống kính)* → **TẮT** | thấy tới mép vòng ảnh; θ ≥ 76° chiếm **11 %** bề dài khung ≈ **54 px** trên khung 495 (hôm nay ≈ 0 px); độ phóng 1,0 đều | cong kiểu gương cầu; vẫn 50 % khung cho nửa trước | [ĐO tính] |
| B | UI | *Hình khung* giữ **Chữ nhật** | — | *Tròn* cắt x < 160 ⇒ chỉ tới ~72–73°, tệ hơn hôm nay | [ĐO mã + tính] |
| C | UI | *Xoay* để nguyên ↺90 | — | xoay không cắt gì | [ĐO mã] |
| D | UI (thử nghiệm) | *Xi-nhan trái: dải hình* → **1** (= dải 0, camera **sau** xe) | nhìn thẳng ra sau từ đuôi xe, cả hai làn | góc nhìn khác hẳn (không thấy hông xe); chưa ai xem dải 0 qua bộ nắn Seal | [SUY] |
| E | cầu adb | `camera_dewarp_amount 50` | tới **86,7°**; θ ≥ 76° = 9,4 % ≈ 47 px; phóng ≤ 1,74× | trộn không phải mô hình quang học ⇒ cong vừa | [ĐO tính] |
| F | cầu adb | `camera_span NARROW` + `camera_dewarp_amount 0` | đúng vệt điểm mù 2.36–2.73 (x 0–512, θ 20° → mép vòng) | khung chỉ 264 px theo trục xe (495×264 ở ↺90) | [ĐO mã + tính] |
| — | cầu adb, **không nên** | `pan_x −15…−30` · `scale 140–150` · `focal 25–40` | 79–88° | phóng 7× ở mép (owner bác 27/09) · 3,4–11,8 % khung đen · cong | [ĐO tính] + [SUY cũ] |

Lệnh cho E/F (bật *Chế độ kiểm thử qua adb* trong Cài đặt trước; cờ là `--es name`/`--es key`/`--es text`, theo
`camera-frame-capture.md` §2):

```bash
BR="adb shell am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST"
$BR --es cmd prefs_set --es key camera_dewarp_amount --es text 50     # E; trả read_back
$BR --es cmd camera --es name left                                    # giữ khung trái tới lệnh none
$BR --es cmd camera --es name none
$BR --es cmd prefs_set --es key camera_span --es text NARROW          # F (kèm amount 0)
# hoàn tác về bộ Seal:
$BR --es cmd prefs_set --es key camera_dewarp_amount --es text 100
$BR --es cmd prefs_set --es key camera_span --es text STRIP
```

Kết luận Q3: **không** cài đặt nào hôm nay cho được *vừa* sau xa đủ to *vừa* vùng gần thẳng; A là cách duy nhất không cần adb.

---

## 4. Phương án (Q4)

### 4.1 Ngành và app khác làm gì

| Ai | Cách làm | Mức |
|---|---|---|
| Kinex (`KinexBlindSpot`) | vệt **phía đuôi** 40 % dải, ảnh thô không nắn; xoay 90/270 thì cắt y cho đủ 4:3 ⇒ 640×480 | [ĐO RE `Y0/C0094o.java:318-343`] |
| Electro 1.13.0 | nắn phối cảnh thẳng **quanh tâm ô** y như Kachi; một bố cục co nguồn vào `0,03..0,97` để giấu viền; **không** có chế độ nhìn ra sau | [ĐO RE §3.2–3.3, `shaders-full-local.md`] |
| Volvo CMS · Orlaco MirrorEye | **hai ô xếp chồng**: ô trên = xa (hẹp, Class II), ô dưới = rộng (gần, Class IV); Volvo thêm zoom-out + tự pan | [ĐO research §2] |
| MB MirrorCam | **pan** khi vào cua | [ĐO research §2] |
| Lexus DSM · Audi virtual mirror | xi-nhan ⇒ **nới góc phía ngoài** (ISO 16505 "temporary modified view") | [ĐO/SUY research §2–3] |
| Honda LaneWatch · Hyundai BVM | camera **riêng, quay sẵn ra sau** ⇒ ảnh gần phối cảnh thẳng | [ĐO research §2] |
| BYD OEM 360 (转向联动) | ảnh nhìn xuống, chỉ < 30 km/h — không có view điểm mù tốc độ cao | [ĐO ảnh research §4] |

Điểm chung: muốn thấy sau xa thì **trục nhìn phải hướng ra sau** (camera riêng, hoặc pan/nới góc về phía ngoài-sau). Camera AVM
của BYD chúc xuống, nên Kachi phải **dựng** trục ấy bằng phép chiếu.

### 4.2 Xếp hạng

| Hạng | Phương án | Làm gì | Công | Rủi ro | Đo gì trước |
|---|---|---|---|---|---|
| **1 (2.92)** | **Họ phép chiếu κ + dịch về đuôi** — preset *Xa ra sau* | shader: `theta = uKappa·atan(pLen, uKappa·uFocal)` (κ = 1 ≡ hôm nay); preset `{κ, F, pan}` theo hồ sơ xe; chip 2 lựa chọn | ≈ 1 ngày mã + test (thêm 1 uniform, 1 pref người lái, 3 pref chỉnh qua adb, i18n 5 thứ tiếng) | thấp: opt-in, κ = 1 khoá bằng test bit-exact, không đụng HAL; giá GPU +2 phép nhân/điểm ảnh [SUY] | không bắt buộc; khung thô giúp chốt bộ số nhanh hơn (replay trên máy ảo, §5.5) |
| 2 (2.93) | **Gương ảo**: camera ảo quay về sau quanh **trục đứng thế giới** + phối cảnh thẳng | tia ra → xoay theo (yaw, pitch) thế giới → θ bằng `atan(length(xy), z)` (chạy được cả θ > 90°) → mắt cá | 1,5–2 ngày + công cụ hiệu chỉnh | trung bình: sai δ ⇒ chân trời nghiêng (quay quanh trục ảnh thay vì trục thế giới cho độ nghiêng `asin(sinψ·sinδ)` = **38°** ở ψ 60°, δ 45° [ĐO tính]); sai mô hình ống ⇒ đường cong ở 75–95° | **bắt buộc**: δ, f-θ, tâm, equidistant/equisolid từ khung thô có mốc (M1) |
| 3 | **Hai ô** gần + xa (kiểu Volvo/Orlaco) | ô xa = PA 1/2, ô gần = view hôm nay; một lượt vẽ, rẽ nhánh theo vùng | +1–2 ngày trên PA 1/2 | trung bình (UI); khung màn chính 371×495 chia đôi thì nhỏ — hợp với dải cụm 1640×424 hơn | owner chọn bố cục |
| 4 | Dải **camera sau** (dải 0) cho phần xa | đã chọn được bằng UI (§3 D); muốn nửa trái/phải thì thêm crop nửa dải | 0 – nửa ngày | thấp; góc nhìn từ đuôi, không thấy hông xe | owner nhìn thử D |
| 5 | Dựng lại nguồn **Một kênh** (trọn vòng) | chỉ khi M1 cho thấy H2 (dải cắt vòng ở phía đuôi) | ≈ 1 ngày (mã đã xoá 2.77) | mật độ điểm ảnh thấp hơn [SUY cũ: năng lượng cạnh 351 vs 686] | M1 câu 1 |
| 6 | Tự nới góc sau N giây giữ xi-nhan (kiểu Lexus) | chuyển *Sát* → *Xa* khi xi-nhan > N s | nửa ngày trên PA 1 | UX cần owner duyệt | sau khi PA 1 được duyệt |
| ✗ | chỉ `pan` / `scale` / `focal` trong mô hình thẳng | — | — | đã đo + tính: bung 7× ở mép hoặc khung đen | — |

### 4.3 Bộ ứng viên cho preset *Xa ra sau* (PA 1) [ĐO tính trên công thức; chọn bộ nào = ĐOÁN tới khi owner nhìn]

Ràng buộc lọc: hàng giữa không có điểm đen, toàn khung đen ≤ 2 %, mép đuôi ≥ 93° @376. K 100 %, S 130 % giữ nguyên bộ Seal.

| κ | F % | pan_x % (× dấu theo bên) | Mép đuôi | Mép trước | Đen | θ ≥ 76° chiếm | Nửa trước | Phóng ở 10° / 85° | Trên khung 495 (↺90) |
|---|---|---|---|---|---|---|---|---|---|
| 1,5 | 100 | −20 | 96,2° | 62,2° | 0 | 28,6 % | 30 % | 0,78 / 2,55 | ≈ 142 px cho sau xa, vẫn thấy bánh trước |
| 1,5 | 120 | −30 | 94,6° | 39,3° | 0 | 30,3 % | 20 % | 0,94 / 3,06 | ≈ 150 px |
| 2,0 | 140 | −20 | 95,7° | 50,7° | 0 | 20,6 % | 30 % | 1,09 / 1,98 | ≈ 102 px, phóng đều nhất |
| 2,0 | 160 | −30 | 95,7° | 30,9° | 0 | 23,5 % | 20 % | 1,24 / 2,26 | ≈ 116 px |
| *so:* hôm nay | 55 | 0 | 76,0° | 76,0° | 0 | 0,1 % | 50 % | 0,44 / — | ≈ 0 px |
| *so:* Nắn hình TẮT | — | 0 | 97,4° | 97,4° | 0 | 11,0 % | 50 % | 1,00 / 1,00 | ≈ 54 px |

Đề xuất khởi điểm: **κ 1,5 · F 100 % · pan −20 %** (giữ được bánh trước, sau xa gấp ~2,6 lần *Nắn hình TẮT*). Với κ > 1 đường
thẳng qua tâm vẫn thẳng, đường khác thành cung nhẹ; κ = 2 là bảo giác (vật xa giữ đúng hình, chỉ to hơn ~2×).

---

## 5. Đo trước khi chốt (CLAUDE.md §14 tầng 1 · §11)

### 5.1 Ảnh khung thô 27/09 còn trên xe không? [ĐO mã → kết luận CHƯA BIẾT]

| Cơ chế dọn | Luật | Ảnh 27/09 |
|---|---|---|
| `TestBridgeCameraFrame.prune` | mỗi lần **chụp mới** giữ 10 tệp `camera-frame-*.png` mới nhất (`TestBridgeCameraFrame.kt:63,213-219,243`) | 27/09 đã chụp ≈ 10–12 khung (F1 ×2, `gl-left-*` ×2, `hal-mode-1..4` ×4, M1 ×2, …) ⇒ khung 09:58:18 có thể đã bị đẩy ra **ngay trong ngày** [SUY] |
| `DiagStorageCap` | mỗi lần mở HOME (`KachiHomeActivity.kt:231`, `force = true`) và khi dịch vụ thông báo khởi động (`NavNotificationListener.kt:121`): nếu **cả cây** `getExternalFilesDir` > **150 MB** thì xoá **cũ nhất trước** (`DiagStorageCap.kt`, `StorageCapPlanner.DEFAULT_CAP_BYTES`) | mỗi lần nổ máy một `usage-*.log` (trần 8 MB/phiên, `KachiLog.kt:32`) ⇒ sau 9 ngày có thể vượt trần ⇒ PNG 27/09 (cũ nhất) đi trước [SUY] |
| Gỡ app | xoá cả thư mục | OTA `install -r` không xoá [SUY] |

Câu trả lời thật: một lệnh `ls` (M0). Dòng `DiagStorageCap: pruned N file(s) …` (Log.i) nằm trong `usage-*.log` ⇒ biết đã dọn chưa.

### 5.2 Buổi xe — thứ tự (đậu xe, WiFi bật, adb qua cầu `nc` như các buổi trước)

```bash
D=/sdcard/Android/data/com.byd.launcher/files/kachi-logs
adb shell ls -la $D | grep -E 'camera-frame-|usage-'           # M0: còn PNG 27/09 không
adb shell "grep -h DiagStorageCap $D/usage-*.log | tail -5"      # M0: cap đã dọn chưa
adb pull $D ./car-1006/kachi-logs/                               # kéo hết về (gitignored)

# M1: chụp lại khung THÔ hai bên (cần "Chế độ kiểm thử qua adb" BẬT trong Cài đặt)
BR="adb shell am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST"
$BR --es cmd state
$BR --es cmd camera --es name left ; sleep 2
$BR --es cmd camera_frame --es name raw        # 5120×960 qua FBO, content=raw_fbo (đường GL)
$BR --es cmd camera_frame                       # khung ĐÃ NẮN, để so vùng đang lấy
$BR --es cmd camera --es name right ; sleep 2
$BR --es cmd camera_frame --es name raw
$BR --es cmd camera --es name none
adb pull $D ./car-1006/kachi-logs/
```

**Cảnh chụp M1** (để một khung trả lời được nhiều câu): xe đỗ song song vạch làn/ô đỗ; một người hoặc cọc ở làn bên cạnh, lần lượt
**3 · 5 · 10 · 20 m** sau gương (đếm ô đỗ hoặc thước); mỗi vị trí một lượt `camera_frame --es name raw`.

**Đọc trên khung thô (off-car)**:
1. Giữa chiều cao dải, mép đuôi là ảnh hay cung tối? ⇒ H1/H2 (§2.2) ⇒ có cần nguồn *Một kênh* không.
2. Bán kính + tâm vòng ảnh (cung tối ở góc/ trên-dưới) ⇒ f-θ, tâm thật (thay giả định Ø 960 của `DEFAULT_K`).
3. Toạ độ px của mốc 3/5/10/20 m ⇒ khớp equidistant hay equisolid (G4 còn treo từ 27/09).
4. Đỉnh chân trời phía ngoài ⇒ góc chúc δ (`y_chân trời = 480 − f·δ`) — bắt buộc cho PA 2.
5. Thân xe ở góc nào của dải thô ⇒ đóng mâu thuẫn §2.3.

**A/B cùng buổi, không build** (§3): hôm nay ↔ *Nắn hình TẮT* ↔ `amount 50` ↔ `NARROW + thô`; owner chọn *kiểu nhìn* nào chấp
nhận được ⇒ quyết κ (độ cong chấp nhận) cho preset.

### 5.3 Kachi chưa chụp được khung thô mà không cần adb [ĐO mã]

> **Đính chính 2026-10-06 (CAMERA-FULL-VIEW, [ĐO mã])**: đường *"Cài đặt › Hệ thống › Nâng cao › Chẩn đoán"* dưới đây đã **không còn**
> từ owner 21/09 (`SettingsSectionsCast.kt:467-470`, `DevSurfaceGateContractTest`), và `am start` màn không-export bị từ chối trên bản
> phát hành [ĐO xe 29/09] ⇒ hai nút đề xuất ở đây chỉ tới được với bản `vehicleTest`/debuggable. Mở cho bản phát hành = quyết định
> owner (spec `kachi-292-camera-full-view.html` OQ4 · backlog `CAM-RAW-DIAG-REACH`). Khung 27/09 (K8/U4) còn một bản đủ phân giải trong
> máy ảo — `camera-full-view-emu-2026-10-06.md` §6.

`DiagActivity.kt:58-100` (Cài đặt › Hệ thống › Nâng cao › Chẩn đoán) chỉ có Làm mới · Sao chép báo cáo · Cổng theme · Kiểm tra cập nhật ·
badge; `ClusterDiag.kt` không có gì về camera. Đường duy nhất là `camera_frame` của cầu kiểm thử (adb + công tắc kiểm thử).
Mà khi cắm CarPlay/AA đầu xe tắt WiFi (CLAUDE.md §11) ⇒ đúng lúc cần thì không vào được.

**Đề xuất (theo §11 — app tự chụp)**: hai nút *📷 Khung thô trái / phải* trong *Chẩn đoán*:
1. mở bên đó bằng đúng đường `previewSide` (cần công tắc *Bật camera khi xi-nhan*; tắt thì báo chữ, không tự bật);
2. chờ khung đầu (`glStats` > 0 hoặc `available`, trần 3 s);
3. GL ⇒ `grabRawFrame(5120, 960)` (FBO, `CameraGlRenderer.grabRaw`); TV ⇒ `captureFrame(5120, 960)` (`getBitmap` đọc layer = khung gốc);
4. ghi PNG vào `kachi-logs/` **và** chép một bản qua `MediaStore` vào `Pictures/Kachi/` — **ngoài** cây `getExternalFilesDir`
   nên `DiagStorageCap` không xoá, mở được bằng Thư viện ảnh / chép ra USB;
5. in lên màn: đường dẫn, cỡ, dòng `CameraGlUniforms.describe()` của phiên đang treo ⇒ một ảnh chụp màn hình gửi về là đủ ngữ cảnh;
6. đóng phiên bằng `closeSession` có sẵn.
Phạm vi đổi state (CLAUDE.md §4): chỉ mở đúng luồng `AVMCamera` mà một lượt xi-nhan vẫn mở, trên màn chính, của chính Kachi;
hoàn tác = đường dỡ đã có; vào số R khi đang giữ camera không xung đột [SUY cũ M4 27/09]. Phần nén+ghi dùng chung với
`TestBridgeCameraFrame.save` (tách một hàm chung — DRY, §4.1).

### 5.4 Chơi lại khung thật trên máy ảo (MAX off-car)

Có PNG 5120×960 rồi thì **mọi** bộ số của §4.3 chỉnh được off-car: `camera_synth --es name file:<tên>` bơm khung thật qua đúng đường
GL (`CameraSynthFeeder.fromFile`, `camera-dewarp-gl.md` §D) + `scripts/emulator/camera-dewarp-e2e.sh` chụp so. Xe chỉ còn để owner
duyệt bằng mắt.

---

## 6. Kế hoạch tối thiểu (Q5) — spec trước (CLAUDE.md §1): `docs/specs/kachi-292-camera-far-rear.html`

### 6.1 2.92 (194) — PA 1 + chụp khung trong app

| # | Việc | Tệp | Ghi chú |
|---|---|---|---|
| T1 | `DewarpParams.kappa` (mặc định 1, kẹp [1; 8]); `mapDstToSrc` dùng `κ·atan2(r, κF)`; nghịch đảo đóng `r_dst = κF·tan(r_src/(K·S·κ))`; `reachableSrcRadius = K·S·κ·π/2`; `FORMULA` thêm κ | `core/…/camera/CameraDewarp.kt` | tệp đang **489 dòng** ⇒ tách `forwardSrcToDst/solveDstRadius/effectiveSrcRadius/idealEquidistantSource` sang `CameraDewarpInverse.kt` (tách thuần) để ≤ 500 |
| T2 | `uniform float uKappa;` + dòng θ; `UNIFORMS` += `uKappa` | `core/…/camera/CameraDewarpShader.kt` | κ = 1 ⇒ cùng phép tính |
| T3 | `VALUE_UNIFORMS` += `uKappa`; `of(kappaPct)`; `describe()` in κ | `core/…/camera/CameraGlUniforms.kt` | |
| T4 | Miền `KAPPA_PCT` 100..800, mặc định 100, bước 25; `apply(kappaPct)` | `core/…/camera/CameraDewarpPrefs.kt` | |
| T5 | Mã tầm nhìn `REACH_NEAR`/`REACH_FAR` (chuỗi, như `SHAPE_*`), `defaultReach() = NEAR` | `core/…/camera/CameraSignalPolicy.kt` | mặc định KHÔNG đổi (CLAUDE.md §6) |
| T6 | Preset xa theo hồ sơ: `farKappaPct/farFocalPct/farPanXPct` (khởi điểm 150/100/−20 [ĐOÁN]); `NEUTRAL` cùng giá trị (opt-in, không đổi pixel nào khi `NEAR`) | `core/…/camera/CameraProfileDefaults.kt`, `app/…/clustercast/ClusterProfile.kt:171-178` | |
| T7 | Prefs: `camera_reach` (người lái) + `camera_far_kappa/_focal/_pan_x` (chỉnh qua adb; vắng ⇒ hồ sơ); `cameraGlUniforms` chọn bộ theo tầm | `app/…/PrefsCameraDewarp.kt`, `PrefsAutomation.kt` | |
| T8 | `FAR` ⇒ ép đường `GL` cho phiên ấy (κ chỉ có trong shader); log `overlay show … tầm=FAR κ=…` | `app/…/camera/CameraSignalController.kt:229-246` | ghi rõ trong spec: đời xe chưa đo GL thì là opt-in |
| T9 | `uKappa` location + `glUniform1f` | `app/…/camera/CameraGlRenderer.kt:349-401` | tệp **490 dòng** ⇒ gom gán uniform vào một hàm/tệp phụ nếu vượt 500 |
| T10 | Chip *Tầm nhìn: Sát thân xe · Xa ra sau* **cuối** khối (sau *Nắn hình*), chạm ⇒ `previewSide(left)` | `app/…/SettingsSectionsCamera.kt`, `ClusterNavBridgeAutomation.kt` | |
| T11 | Xếp khoá: `USER_KEYS` 14 → 15, `NO_UI_KEYS` 15 → 18; `ProfileScopeCluster` (`camera_reach` = sở thích hồ sơ, 3 khoá far = quang học theo xe); `ProfileSharePolicy`; `TestBridgeWritableKeys` + `TestBridgePrefsSet` (kiểm miền + `read_back`) | `core/…/camera/CameraSettingsIa.kt`, `core/…/ProfileScopeCluster.kt:68-101,162-164`, `core/…/ProfileSharePolicy.kt:79-80`, `core/…/testbridge/TestBridgeWritableKeys.kt`, `app/…/testbridge/TestBridgePrefsSet.kt` | các bài đếm khoá phải sửa theo |
| T12 | Chuỗi VI · EN · zh · th · ms (tiêu đề, phụ đề, 2 chip, nút Chẩn đoán) | `app/src/main/res/values*/strings_kachi.xml` | `LangCoverageTest` |
| T13 | Nút *📷 Khung thô trái/phải* (§5.3) | mới `app/…/clustercast/DiagCameraCapture.kt` + 2 nút ở `DiagActivity.kt`; tách phần nén/ghi chung khỏi `TestBridgeCameraFrame.kt` | ngoài cây bị dọn: `MediaStore` `Pictures/Kachi/` |
| T14 | Bump `versionCode` 194 / `versionName` "2.92"; spec + doc này + INDEX + BACKLOG + project-context | — | R2.1 |

**Bài test (khoá bài học, CLAUDE.md §10)**:

| Bài | Khoá điều gì |
|---|---|
| `kappa 1 trung tung bit voi cong thuc cu` (lưới 81 điểm × 16 tổ hợp xoay/hình) | không đổi một pixel nào khi `NEAR` |
| `kappa 2 la stereographic dong` | `r_dst = 2F·tan(θ/2)`; nghịch đảo khứ hồi κ ∈ {1; 1,5; 2} |
| `kappa 2 bao giac o 90 do` | phóng xuyên tâm = phóng tiếp tuyến tại θ 90° |
| `bo Seal NEAR chi toi 76 do` | ghim nguyên nhân phàn nàn 06/10: mép đuôi hàng giữa = x 140,6 |
| `preset FAR toi mep vong anh, hang giua khong den` | mép đuôi ≥ 93° (K·S 0,588), 0 điểm đen hàng giữa, đen toàn khung ≤ 2 % |
| GLSL ↔ Kotlin: `FORMULA`, `UNIFORMS == declaredUniforms`, `VALUE_UNIFORMS` | `CameraDewarpTest`, `CameraGlUniformsTest` |
| Dây nối `:app`: `uKappa` gán theo tên; chip gọi `previewSide`; `FAR` ⇒ `GL`; nút Chẩn đoán → `grabRawFrame` → PNG + MediaStore | `CameraGlWiringContractTest`, `CameraSettingsIaWiringContractTest` (+ bài mới) |
| Đếm khoá | `CameraSettingsIaTest`, `ProfileScopeClusterTest`, `TestBridgeCommandTest`, `CameraProfileDefaultsTest`, `LangCoverageTest` |

Thử làm đỏ (P5.3): đổi `uKappa·atan(pLen, uKappa·uFocal)` thành `atan(pLen, uFocal)` ⇒ bài `preset FAR …` phải đỏ.

**Kiểm trên xe (2.92)**:

| # | Làm | Đạt khi |
|---|---|---|
| V1 | (trước khi code, trên 2.91) §5.2 M0 → M1 → A/B | có ≥ 2 khung thô mỗi bên; owner chọn kiểu nhìn |
| V2 | *Tầm nhìn: Xa ra sau* → xem thử trái/phải; chỉnh `camera_far_kappa/_focal/_pan_x` bằng `prefs_set` tới khi vừa mắt, ghi `read_back` | owner: thấy từ hông xe ra tới xe làn bên ≥ 20 m sau |
| V3 | Chạy thật, xe vượt bên trái/phải | thấy xe từ xa tới sát; `gfxinfo` giật không tệ hơn CAM-B6 (0,81 %) |
| V4 | Vào số R khi khung đang hiện | camera lùi zin chạy bình thường |
| V5 | Nút *📷 Khung thô* trong Chẩn đoán, **không** adb | PNG có trong `kachi-logs/` và trong Thư viện ảnh › Kachi |

### 6.2 2.93 — PA 2 *Gương ảo* (sau khi M1 cho δ, f-θ, tâm, mô hình ống)

Uniform `uViewRot` (mat3 thế giới → camera, dựng ở `:core` từ δ của hồ sơ + yaw/pitch của preset), θ = `atan(length(ray.xy), ray.z)`;
δ/f-θ/tâm vào `ClusterProfile` (Seal), không rải trong mã (CLAUDE.md §7). Tuỳ chọn: hai ô gần+xa trên dải cụm (PA 3).

---

## 7. Còn chưa biết

| # | Câu | Mức | Chốt bằng |
|---|---|---|---|
| U1 | Dải pano có giữ trọn vòng ảnh ở phía đuôi (H1) hay cắt (H2)? | [SUY] nghiêng H1 | M1 câu 1 |
| U2 | f-θ thật, tâm vòng ảnh, equidistant/equisolid | [CHƯA BIẾT] (giả định Ø 960/190° của mã đã cũ: khung thô có từ 27/09 mà chưa ai đo vòng) | M1 câu 2–3 |
| U3 | Góc chúc δ của camera gương Seal | [CHƯA BIẾT] | M1 câu 4 |
| U4 | Ảnh 27/09 còn trên xe không | [CHƯA BIẾT] | M0 |
| U5 | *"Demo"* owner nhớ là vệt `NARROW` cũ, dải thô (*Nắn hình* tắt), hay app khác | [ĐOÁN] vệt `NARROW` | hỏi owner một câu, hoặc A/B §5.2 |
| U6 | Owner chấp nhận độ cong của κ 1,5–2 không | [CHƯA BIẾT] | V2 |
| U7 | Hướng đuôi bên phải (+x) đúng bằng mắt | [SUY] (đối xứng gương 0,715) | V2 bên phải |

---

## 8. Phát hiện ngoài phạm vi (KHÔNG làm — đưa vào backlog)

| # | Phát hiện | Mức | Đề xuất |
|---|---|---|---|
| X1 | `DiagStorageCap` dọn **cả cây** `getExternalFilesDir` theo cũ-nhất-trước, mà cây ấy chứa cả **ảnh trình chiếu của người dùng** (`PhotoStore.kt:31`, `photos/`), hình nền (`WallpaperStore.kt:34`), ảnh xe (`CarImageStore.kt:245`), tệp hồ sơ xuất (`ProfileIoStore.kt:27`), thư mục nạp gói giọng (`VoiceModelStore.kt:229`) ⇒ khi log vượt 150 MB, ảnh người dùng (thường cũ nhất) bị xoá trước log | [SUY — đọc mã, chưa tái lập] | chỉ dọn thư mục chẩn đoán (`kachi-logs/`, `diag/`, CSV), loại trừ thư mục dữ liệu người dùng; bài canh |
| X2 | KDoc `CameraDewarp.kt:77-79` vẫn ghi *"Chưa có một khung 5120×960 nào chụp từ xe"* — cũ từ 27/09 | [ĐO mã] | sửa khi làm T1 |
| X3 | Hai ghi chép thân xe trái ngược (§2.3) | [SUY] | xem lại khung thô rồi sửa một bên (R2.4) |

---

## 9. Tái lập số [ĐO tính]

Bản dịch thẳng của `CameraDewarp.sample` + bộ Seal; chạy `python3 -I <tệp>.py`. Không đọc tệp nào, không mạng.

```python
import math
W, H = 1280.0, 960.0                       # một dải; x = 0 là mép ĐUÔI của dải gương trái
K0 = (H / W) / math.radians(95.0)          # DewarpParams.derive: vòng Ø = 960, ống 190°  -> 0.452335
ASP = W / H
def P(f=55, k=100, s=130, a=1.0, pan=0.0): return dict(F=K0*f/100, G=K0*k/100*s/100, a=a, pan=pan)
def rot(u, v, deg):                        # CameraDewarp.rotateDstToLocal (bội 90)
    d = deg % 360
    if d == 0: return u, v
    c, s = {90: (0, 1), 180: (-1, 0), 270: (0, -1)}[d]; qx, qy = u - .5, v - .5
    return .5 + c*qx + s*qy, .5 + c*qy - s*qx
def sample(u, v, deg, p, kappa=1.0):       # kappa = 1 -> đúng công thức hôm nay
    a, b = rot(u, v, deg); a += p['pan']
    px, py = (a - .5)*2, (b - .5)*2/ASP; r = math.hypot(px, py)
    if p['a'] > 1e-4 and r > 1e-6:
        th = kappa*math.atan2(r, kappa*p['F']); rs = p['G']*th
        a += (.5 + px/r*rs*.5 - a)*p['a']; b += (.5 + py/r*rs*.5*ASP - b)*p['a']
    return None if not (0 <= a <= 1 and 0 <= b <= 1) else (a*W, b*H)
print(sample(0, .5, 0, P()))               # (140.6, 480.0): mép đuôi khung hôm nay
print(sample(.5, 1, -90, P()))             # ↺90: mép dưới khung = mép đuôi dải
print(sample(0, .5, 0, P(f=100, pan=-.2), kappa=1.5))   # preset ứng viên: x ≈ 8,0 px (≈ 96,2° @376)
```

`θ @376 = (640 − x) / 376,3` rad. Bảng §1.4/§4.3 = quét `u` hàng giữa (2001 điểm) và toàn khung (241×241) bằng đúng hàm trên.

---

## 10. Việc cùng phiên cho người điều phối (R2.1)

- `docs/README.md`: thêm dòng cho tài liệu này (Current, 2026-10-06).
- `docs/PROJECT-BACKLOG.md`: `CAM-FAR-REAR` (PA 1 · 2.92, TODO, chờ spec + duyệt) · `CAM-RAW-DIAG` (nút khung thô trong Chẩn đoán, TODO) ·
  `CAM-VIRTUAL-MIRROR` (PA 2 · 2.93, BLOCKED bởi M1) · `ONCAR-CAM-RAW-1006` (M0/M1/A-B, 🚗) · X1 `DIAG-CAP-USERDATA` (P1/P2 tuỳ owner) ·
  X2/X3 (P3).
- `.kiro/steering/project-context.md`: một dòng — *camera gương: phần sau xa nằm ở 141 px đầu phía đuôi mỗi dải; view `GL` hôm nay dừng ở
  θ 76° do phối cảnh thẳng; hướng chữa = họ phép chiếu κ / gương ảo*.
