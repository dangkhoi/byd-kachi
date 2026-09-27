# R4 · HÌNH "THEO CỤM" — camera chỉ vẽ trong dải giữa của cụm (2.76, làn L2)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 · **Mục đích**: owner (3 ảnh cụm + framebuffer display 1, 10:50–11:01):
> *"header top và bottom là của hệ thống, không vẽ vào được, chỉ vẽ được khúc giữa như gmaps đang hiện"* ⇒ đo **dải giữa**
> bằng số từ framebuffer + ảnh chụp, đưa vào một hồ sơ (`ClusterBandSpec`), thêm hình khung `CLUSTER` ("theo cụm") để
> overlay camera trên cụm nằm **trọn** trong dải ấy. Spec `docs/specs/kachi-276-closing.html` R4 · OQ2.
> Bằng chứng cục bộ (gitignored): `scratchpad/car-0927/cum/cum-{0,1,2}.png` (1600×1200), `scratchpad/car-0927/cluster-fb-d1.png`
> (1920×720), `scratchpad/car-0927/logcat-274-0956.txt`; ảnh máy ảo `scratchpad/cluster-276/`.

---

## 1. Bối cảnh — sự thật đo được trước khi sửa

| # | Sự thật | Mức | Nguồn |
|---|---|---|---|
| F1 | Display chiếu = `fission_bg_xdjaVirtualSurface` **1920×720**, density 320, `FLAG_PRESENTATION`, owner `com.xdja.containerservice`, displayId **1** | [ĐO] | logcat 27/09 09:57:24 `DisplayManagerService` |
| F2 | Lượt 10:50:42 (ảnh `cum-1`): `overlay show corner=TL side=LEFT cluster=true rot=-90 hình=RECT kết xuất=GL khung=371x495 **vùng=495x495**` | [ĐO] | logcat 10:50:42.582 |
| F3 | `495 = 0,5 × 990` = `displayMetrics.heightPixels` của **màn CHÍNH** (`CameraOverlayView.box()` đo `appCtx`, không đo display cụm) ⇒ cửa sổ trên VD 720 px cao nằm ở `y = 0,06×990 = 59 … 554`, x = `57 + (495−371)/2 = 119 … 490` | [SUY từ F2 + code] | `CameraOverlayView.kt` `box()` bản 2.75 |
| F4 | Cửa sổ Google Maps trên VD do **chính Kachi** đặt: `am task resize 71 20 60 1920 560` (nội dung 124…560 sau caption 64 px) — **không** phải dải của hệ thống | [ĐO] | logcat 09:57:47 `SimpleCast` |
| F5 | Framebuffer display 1: caption freeform 0…124 (xám 31–32), nội dung gmaps `x ≥ 20`, `y 124…559`, đen từ 560 và `x < 20` | [ĐO PIL] | `cluster-fb-d1.png` quét hàng/cột (ngưỡng lum 40) |
| F6 | Trên ảnh cụm, phần fb `y < ~131` và `y ≳ 567` **không nhìn thấy** (thanh trên *10:50AM · Standard · P · NORMAL* và thanh dưới *0 kW · 54% · 309 km · 0 km/h* của hệ thống đè lên); nội dung fb tới 559 vẫn thấy trọn | [ĐO homography, §2] | `cum-0/1/2` |
| F7 | Hệ thống còn vẽ **trong dải**: mũi tên xi-nhan xanh + biểu tượng (P) ở góc trên của mỗi cửa sổ (trái `x≈477–502, y≈161–187`; phải `x≈1518–1620, y≈127–165`), cột biển-30/ADAS ở `x ≳ 1798, y≈165–330` (phải), icon cửa/ghế ở `x≈117–150, y≈533–573` (trái, phần lớn dưới dải) | [ĐO homography] | `cum-1/2/0` |
| F8 | Viền kính cụm là đường **xiên/cong**: trái `x≈68` (đỉnh dải) → `≈115–125` (đáy); phải `≈1849` (đỉnh) → `≈1819` (đáy) | [ĐO homography] | `cum-0/2` |
| F9 | Máy ảo: `settings put global overlay_display_devices 1920x720/320` ⇒ `DisplayDeviceInfo{"Overlay #1" … 1920 x 720 … type OVERLAY … FLAG_PRESENTATION}`, displayId 1, cửa sổ soi `(0,0)(960x360)` alpha 0,8 trên màn chính | [ĐO] | `dumpsys display/window` emulator-5554 |
| F10 | AOSP 10 (`android-10.0.0_r47`): `OverlayDisplayAdapter.java:352` `mInfo.flags = FLAG_PRESENTATION`; `DisplayManager.java:290-295` `DISPLAY_CATEGORY_PRESENTATION` gom WIFI/HDMI/**OVERLAY**/VIRTUAL ⇒ `clusterCtx()` chọn overlay display **cùng cơ chế** chọn VD XDJA trên xe | [ĐO source] | googlesource |

## 2. Số đo dải giữa — phương pháp + kết quả

**Phương pháp.** Không có framebuffer của thanh trên/dưới (chúng không nằm trong display 1), nên mép của chúng phải suy
từ ảnh chụp. Với mỗi ảnh, dựng **homography** ảnh → framebuffer (DLT bình phương tối thiểu, 7–12 điểm neo, pure python
`scratchpad/cluster-276/band_fit.py`): điểm neo là các chi tiết gmaps có toạ độ fb đọc bằng PIL (mic thanh tìm `830,172`,
chip *Trạm xăng* `1750,172`, pin *Melbourne* `1366,412`, chip 31° `845,422`, viên *Khám phá* `215,494`, logo *Google Maps*
`948,532`…) **và** hai góc dưới của cửa sổ camera (toạ độ fb biết chính xác từ F2/F3: trái `119,554`–`490,554`, phải
`1430,554`–`1801,554`). Sau đó chiếu các điểm biên nhìn thấy trên ảnh (mép trên của cửa sổ camera nơi nó *ló ra* dưới
thanh trên; đỉnh chữ *0 kW* / *0 km/h*; viền kính; mép cột icon) về fb.

| Ảnh | Điểm neo · sai số lớn nhất | Mép dưới thanh trên (fb y) | Chữ thanh dưới bắt đầu (fb y) | Khác |
|---|---|---|---|---|
| `cum-2` (phải) | 7 · **3,8 px** | **130–131** | **578** | mũi tên xi-nhan `1518,127`; (P) tới `1620,165`; biển 30 mép trái `1798` (y 194–266); kính phải `1819` (đáy) / `1849` (đỉnh) |
| `cum-1` (trái) | 8 · 20,7 px | 148–165 (neo kém, bỏ) | 588–604 (bỏ) | mũi tên xi-nhan `477–502, 161–187`; kính trái `115` (đáy) |
| `cum-0` (không camera) | 12 · 62 px (1 neo lệch) | 131–140 | 567–572 | kính trái `68` (đỉnh) / `121` (đáy); kính phải `1890` (đỉnh) / `1854` (giữa); icon cửa `117–150 × 533–573` |

**Kết luận [ĐO ±8 px]** (lấy ảnh có sai số nhỏ nhất làm chuẩn, hai ảnh kia làm kiểm tra chéo):

- Thanh trên che tới **y ≈ 131** (135 ± 5 tính cả cum-0); thanh tìm gmaps có mép trên ở **136** và **nhìn thấy trọn** ⇒ 136 là hàng đầu tiên chắc chắn vẽ được.
- Nội dung tới **559** nhìn thấy trọn (đáy cửa sổ camera 554 và đáy gmaps 560 đều thấy); chữ thanh dưới từ **567–578** ⇒ 560 là biên an toàn.
- Mép trái vẽ được: kính **≈ 121** ở đáy dải (xiên ra 68 ở đỉnh) ⇒ lùi **140** (lề ≥ 15).
- Mép phải vẽ được: cột icon hệ thống từ **≈ 1798**, kính 1819 (đáy) ⇒ lùi **1780** (lề ≥ 18, đối xứng với 140).
- **Dải Seal DL3 = `[140,136) … (1780,560)` = 1640×424 px** — `ClusterBandSpec.SEAL_DL3`.
- Bán kính bo **24 px [SUY — chọn]**: không có cung tròn nào để đo (viền là đường xiên, gmaps vuông góc); 24 px ≈ bo góc khung launcher ở 240 dpi, đủ để góc cửa sổ không nhọn sát kính.
- Hai thứ hệ thống vẽ **trong** dải và **đè lên** cửa sổ (F7): mũi tên xi-nhan + (P) ở góc trên-trong của mỗi cửa sổ. Không né (né là mất 40 px của 424; gmaps cũng bị đè y vậy). Ghi để owner không tưởng là lỗi vẽ.

## 3. Cơ chế

1. **`:core` `CameraClusterBand.kt`** (thuần, 135 dòng): `ClusterBandSpec(refW, refH, left, top, right, bottom, radiusPx)` +
   `SEAL_DL3`; `band(displayW, displayH, spec)` co giãn theo trục về display thật rồi kẹp (display chưa đo ⇒ số gốc);
   `place(band, atLeft, streamW, streamH, crop, rotationDeg, …)` đặt cửa sổ ở **đầu trái/phải** dải, cao trọn dải, tỉ lệ
   qua **cùng** `CameraOverlayFrame.fit` của màn chính (trần rộng = nửa dải); `effectiveShape(shape, onCluster)` — *theo
   cụm* trên màn chính ⇒ **RECT**; `SHAPE_CLUSTER` = alias `CameraSignalPolicy.SHAPE_CLUSTER` (L1 đã thêm vào `SHAPES`
   ⇒ `Prefs.cameraShape`, chip Cài đặt, `prefs_set camera_shape` đều nhận, không cần yêu cầu chéo làn).
2. **`CameraPanoCrop.cropFor`**: quy `CLUSTER → RECT` tường minh trước mọi phép so ⇒ crop y hệt chữ nhật (hình này chỉ đổi cửa sổ).
3. **`:app` `CameraOverlayView`**:
   - `clusterCtx()` (thay `clusterWm()`) trả **ngữ cảnh display cụm**; `Live.ctx` giữ nó ⇒ `box()`/`geometry()` đo
     `displayMetrics` trên **display đang treo** — sửa F3 (vùng 495 do đo nhầm màn chính). Trên màn chính `Live.ctx = appCtx` ⇒ đường 2.73 không đổi một số nào.
   - `val cluster = dctx != null` — "trên cụm" là **sự thật** (có display cụm), không phải pref (CLAUDE.md §5); `val shape = effectiveShape(shape, cluster)` đứng **trước** `val round = …` của 2.73.
   - `geometry(st)` = MỘT cửa cho cả `show()` lẫn `onStreamMeasured()`: nhánh cụm ⇒ `band()` + `place()` + `bandLayoutParams()` (toạ độ **tuyệt đối** trên display cụm, `TOP|START`, cùng loại cửa sổ/cờ 2.73); còn lại ⇒ `box()` + `frameOf()` + `layoutParams()` nguyên văn.
   - Bo góc: `g.radiusPx` (hồ sơ, co theo display) ở nhánh cụm, `KachiSpace.RADIUS_XL` ở nhánh cũ — **cùng một** `ViewOutlineProvider`.
   - `show(..., band: ClusterBandSpec = ClusterBandSpec.SEAL_DL3)` — điểm nối cho hồ sơ xe (yêu cầu chéo làn §6).
   - Dòng log `overlay show` in thêm `dải=x0,y0-x1,y1 tại=x,y display=WxH` (nhánh cụm) hoặc `vùng=WxH` (nhánh cũ) — buổi xe chỉ cần một ảnh logcat.
4. `CameraVideoLayer`: **không đổi** (TextureView lấp kín cửa sổ; crop/xoay do ma trận/shader như cũ).

## 4. Quyết định

| # | Quyết định | Lý do / bằng chứng |
|---|---|---|
| D1 | Dải là **hồ sơ** (`ClusterBandSpec`), Seal là mặc định `:core`; đời xe khác (SL6 1920×800) tự co theo trục + kẹp | CLAUDE.md §7; cỡ display thật đọc lúc chạy |
| D2 | *Theo cụm* trên màn chính **thoái về RECT** (OQ2 chốt), không ROUND | ROUND đổi cả crop (ô vuông) ⇒ owner sẽ tưởng pref hình bị đổi; RECT = cửa sổ 2.73, không bất ngờ |
| D3 | Cửa sổ ở đầu trái/phải dải **theo pref góc từng bên** (`camera_pos_left/right` TL/TR), mặc định cùng bên | giữ đúng quyền chọn góc của R4 spec cũ; "mép phía xe hướng vào trong" tự thoả: thân xe ở mép PHẢI ô gương trái / mép TRÁI ô gương phải [ĐO khung thô 27/09] ⇒ đặt cùng bên là mép thân xe hướng vào giữa, **không cần lật ảnh** |
| D4 | Cửa sổ cao **trọn dải** (424), rộng theo tỉ lệ (4:3 đứng ⇒ 565; ±90 ⇒ 318; chưa biết cỡ ⇒ 424 vuông), trần nửa dải | owner: *"ảnh nằm trọn dải giữa"*; hai bên không bao giờ chạm nhau |
| D5 | **Sửa nguồn `displayMetrics`** cho MỌI hình trên cụm (không chỉ CLUSTER) | F2/F3 là lỗi đo nhầm display, có bằng chứng; RECT/ROUND trên cụm nay vùng = 0,5×720 = 360 (trước 495) — ghi để owner biết vì sao khung nhỏ hơn 2.75 |
| D6 | Không né mũi tên xi-nhan/(P) của hệ thống trong dải | F7: mất 40/424 px; gmaps cũng bị đè; là chỉ báo xi-nhan, không gây hiểu nhầm |
| D7 | Không ép `camera_on_cluster`/`CLUSTER` làm mặc định | mặc định thuộc R2 (L1, hồ sơ); làn này chỉ cấp giá trị + hình học |

## 5. Test

- `:core` `CameraClusterBandTest` (8 bài): dải Seal ngoài mọi vùng đo (F6–F8) · co giãn 1920×800 / kẹp display nhỏ / chưa đo ⇒ số gốc · spec vô lý ném · 4:3 dính đầu trái/phải 565×424 · **64 tổ hợp** (bên × 4 crop × 4 góc × biết/chưa biết cỡ) luôn trong dải, ≤ nửa dải, bo ≤ nửa cạnh · bán kính co/kẹp · thoái RECT trên màn chính + `isShape(CLUSTER)` · crop CLUSTER == RECT (2 bên × 2 bề rộng) ≠ ROUND.
- `:app` `CameraClusterBandWiringContractTest` (5 bài, canh dây nối CLAUDE.md §8): quy hình theo display THẬT trước `val round` · `displayMetrics` của display đang treo + `band()`/`place()`/`bandLayoutParams()` toạ độ tuyệt đối · `onStreamMeasured` dùng cùng `geometry()` (đúng 2 call site) · `cropFor` quy tường minh · script e2e dựng overlay display + ép cả hai bên + chấm bằng `dumpsys window`.
- Bài cũ giữ nguyên xanh: `CameraSpanShapeWiringContractTest` (literal `val round = shape == …`, 1 `ViewOutlineProvider`), `CameraFrameAndRenderWiringContractTest` (`layoutParams` 2.73, `SQUARE_RATIO`).
- Kết quả chạy (XML `build/test-results`): `CameraClusterBandTest` **8/0 fail** · `CameraPanoCropTest` **12/0** (đếm tổ hợp nay theo `SHAPES.size` — L1 thêm CLUSTER) · `CameraOverlayFrameTest` 10/0 · `CameraClusterBandWiringContractTest` **5/0** · `CameraSpanShapeWiringContractTest` 9/0 · `CameraFrameAndRenderWiringContractTest` 9/0.
- ⚠ Ngoài làn: `CameraGlUniformsTest` (2 bài, dòng 249/321) và `CameraChannelSourceTest` (dòng 234) đếm "2 hình × …" = 80 ⇒ nay 120 vì `SHAPES` có 3 mã — kỳ vọng phải đổi sang `SHAPES.size` (làn L1, cùng lý do với `CameraPanoCropTest`).

## 6. Yêu cầu chéo làn (L1 — `ClusterProfile.kt`, `CameraSignalController.kt`)

1. `ClusterProfile`: thêm field `val band: ClusterBandSpec = ClusterBandSpec.SEAL_DL3` (import `com.byd.clusternav.launcher.camera.ClusterBandSpec`); `SEAL_DL3`/`GENERIC_FALLBACK` giữ mặc định; `DL5` cũng Seal-like tới khi đo (🚗). **Không** đưa vào `export()/parse()` (định dạng share nhóm giữ nguyên).
2. `CameraSignalController` chỗ `overlay.show(…)`: thêm `band = ClusterProfile.resolve(appCtx).band,` (đọc một lần cạnh `corner`, không mỗi khung). Khi chưa nối, mặc định tham số = Seal ⇒ hành vi hôm nay không đổi.
3. (Đã có) `CameraSignalPolicy.SHAPES` chứa `SHAPE_CLUSTER` — L1 làm; chip *theo cụm* (`kachi_camera_shape_cluster`) thuộc R1 của L1.

## 7. 🚗 Kiểm trên xe (runbook 2.76, một dòng)

- **CAM-CL1**: Cài đặt › Camera › hình **theo cụm** + chiếu lên cụm → bật xi-nhan trái rồi phải ⇒ ảnh nằm **trọn dải giữa**, không đè header/footer, dính đầu trái/phải, mũi tên xi-nhan hệ thống đè ở góc trên-trong là bình thường; logcat `overlay show … hình=CLUSTER … dải=140,136-1780,560 tại=140,136` (trái) / `tại=1215,136` (phải). FAIL nếu thấy ảnh bị cắt trên/dưới ⇒ chụp ảnh, đọc `dải=` để chỉnh `ClusterBandSpec.SEAL_DL3` (±8 px là sai số phép đo).

## 8. Kết quả chạy + bằng chứng máy ảo

`scripts/emulator/camera-cluster-e2e.sh --skip-build` (emulator-5554, AVD clusternav10, APK vehicleTest 2.76) — **ĐẠT** 13:21:

| Bước | Số đọc từ `dumpsys window` (display 1) / logcat | Kết quả |
|---|---|---|
| overlay display | `"Overlay #1" 1920 x 720 … type OVERLAY … FLAG_PRESENTATION`, displayId 1 | dựng được, `clusterCtx()` chọn đúng |
| xi-nhan trái | `overlay show corner=TL side=LEFT cluster=true rot=0 hình=CLUSTER kết xuất=GL khung=565x424 dải=140,136-1780,560 tại=140,136 display=1920x720` · khung cửa sổ `[140,136]-[705,560]` | **TRONG DẢI** |
| xi-nhan phải | `… corner=TR side=RIGHT cluster=true … khung=565x424 … tại=1215,136 …` · khung `[1215,136]-[1780,560]` | **TRONG DẢI** |
| thoái màn chính (`camera_on_cluster 0`, vẫn `camera_shape CLUSTER`) | `overlay show corner=TL side=LEFT cluster=false rot=0 hình=RECT … khung=540x405 vùng=540x540` | thoái đúng RECT, display 0 |

Ảnh (cục bộ, không vào repo — `scratchpad/cluster-276/e2e/`): `cluster-left.png`, `cluster-right.png` = cửa sổ soi của overlay
display phóng ×2 về 1920×720, kẻ **đỏ** = thanh trên/dưới + cột icon phải đo từ xe, **xanh** = dải `[140,136)-(1780,560)`, **vàng** =
khung cửa sổ đọc từ `dumpsys`; ảnh tổng hợp (lưới cầu fisheye) nằm trọn trong khung vàng, khung vàng nằm trọn trong xanh, không
chạm đỏ. `screen-main.png` = lượt thoái trên màn chính. Kèm `windows-left/right/main.txt`, `overlay-show-*.txt`.
Ảnh đo xe: `scratchpad/cluster-276/zoom-*.png` (cắt phóng 3 ảnh cụm), `band_fit.py` (homography).

## 9. Nợ còn lại

- Số dải là của **một** xe (Seal DL3); SL6/DL5 chỉ có phép co theo trục [SUY] — cần một ảnh cụm + `dumpsys display` của xe thứ hai.
- Thanh dưới: biên 560–567 chưa chắc (không có chi tiết tối nào nằm đúng đó trong 3 ảnh) — nếu 🚗 thấy hở đáy, nới `bottom` tới 566.
- Hệ thống đè mũi tên xi-nhan/(P) lên cửa sổ (F7): chấp nhận (D6); nếu owner khó chịu, cách rẻ nhất là hạ `top` riêng cho cửa sổ camera thêm 40 px (mất 10 % chiều cao).

---

## 10. Sau soát Opus 2026-09-27 (Pass 1) — bản vá [P1] của làn này
Làn này đo dải rồi **chỉ áp cho một trong ba hình**: `geometry()` đi qua `band()/place()` khi `camera_shape = CLUSTER`, còn
CHỮ NHẬT (**hình mặc định** — `CameraSignalPolicy.defaultShape`) và TRÒN trên cụm vẫn dùng vùng vuông đo bằng % chiều cao
display. Trên cụm 1920×720 vùng ấy bắt đầu ở `y = 43` trong khi thanh trên phủ tới `y ≈ 136` ⇒ **93/360 px bị cắt**; 2.75 cắt
77/495, tức bản 2.76 làm **nặng hơn** đúng triệu chứng owner báo (vì D5 sửa metrics sang display cụm). Owner đang bật *Hiện
lên cụm* với hình RECT — [ĐO logcat 27/09 10:50:42] `hình=RECT … cluster=true` — nên đây là đường THẬT của xe, không phải một
cấu hình giả định.

**Vá**: `CameraClusterBand.boxIn(band, side, atLeft)` (`:core`, thuần, test bằng số) — vùng **vẫn là ô vuông** kiểu 2.73, chỉ
lấy `y0`, đầu trái/phải và trần cạnh từ dải; `CameraOverlayView.box` gọi nó khi `st.onCluster` (góc PHẢI đổi sang
`displayW − x1` vì `LayoutParams.x` là độ lệch kể từ góc mà `gravity` chọn). Hằng `CLUSTER_TOP_RATIO` — cùng KDoc sai *"cụm
không có thanh trên"* mà chính F6 của làn này bác — đã gỡ. Đường màn CHÍNH không đổi một byte. Bài mới:
`CameraClusterBandTest.vung vuong 2 73 tren cum bi kep vao dai o ca hai goc` (mọi tổ hợp bên × crop × xoay × chưa-biết-cỡ đều
`band.contains(cửa sổ)`, có cả phép **căn giữa vùng** của `layoutParams`), `CameraClusterBandWiringContractTest` (f).
🚗 kiểm mới: **CAM-CL2** trong `oncar-runbook-2.76.md` (hình *Chữ nhật* + *Hiện lên cụm*).

**Sửa kèm ([P2] bằng chứng)**: KDoc `CameraClusterBand` từng nói *"lề mỗi phía ≥ 15 px so với mép gần nhất đo được"* cho cả
bốn mép — chỉ đúng ở hai mép NGANG (trái +19 · phải +18). Dọc: dưới **+7** (§9 còn ngỏ 560–567), trên **≈ +5** theo chuẩn
130–131 và **0** nếu lấy biên trên 140 ⇒ `136` không phải một lề mà là **hàng đầu tiên nhìn thấy trọn** (§2). Lời nhắc trong
`CameraClusterBandTest` cũng sửa theo (nó đang tự phản: *"thanh trên đo 130–140 ⇒ dải không được bắt đầu sớm hơn 136"*).

**Bản sao số dải trong `scripts/emulator/camera-cluster-e2e.sh`** ([P3], không đổi script): bài canh nay dựng chuỗi
`BAND_L=…; BAND_T=…; BAND_R=…; BAND_B=…` **từ `ClusterBandSpec.SEAL_DL3`** và đòi nó có trong script, cộng một assert cho cỡ
overlay display = cỡ tham chiếu của hồ sơ ⇒ nới `bottom` lên 566 như §9 dự tính sẽ đỏ ngay ở `:app`, không phải đợi một lượt
chạy máy ảo bằng tay.
