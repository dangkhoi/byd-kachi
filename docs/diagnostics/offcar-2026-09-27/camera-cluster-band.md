# R4 · HÌNH "THEO CỤM" — camera chỉ vẽ trong dải giữa của cụm (2.76), rồi ôm ĐƯỜNG CONG của kính (2.77)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 tối (§12 = 2.78, mép PHẢI cong + không ép hình + mép trong mờ) · **Mục đích**: owner (3 ảnh cụm + framebuffer display 1, 10:50–11:01):
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

---

## 11. 2026-09-27 (chiều, sau buổi xe) — MÉP NGOÀI CONG của kính: đo, mô hình, mặt nạ (2.77, làn L2)

> Owner nhìn camera đang chiếu trên cụm: *"này nhìn OK, **nhưng shape nó không theo cạnh trái cong của cụm**"*; và
> trước đó, với hình CHỮ NHẬT: *"bé tý, bo các góc tròn, không hề theo hình cụm gì cả"*. Phần này trả lời cả hai.

### 11.1 Đường cong ở ĐÂU — không phải trong framebuffer

| # | Sự thật | Mức | Nguồn |
|---|---|---|---|
| G1 | Bộ đệm cụm là **chữ nhật phẳng 1920×720**, không có mặt nạ cong nào trong đó (ảnh chụp màn display 1 lúc camera đang hiện) | [ĐO] | `scratchpad/car-0927pm/cum-d1.png` |
| G2 | ⇒ đường cong owner thấy là **vùng sáng vật lý** của kính/viền cụm ⇒ `screencap` **không** đo được nó; chỉ ẢNH CHỤP cụm mới đo được | [SUY từ G1, chắc] | — |
| G3 | Biên vùng sáng là hình **thấu kính**: cạnh trên/dưới cong, hai đầu trái/phải xiên + bo | [ĐO] | `car-0927/cum/cum-{0,1,2}.png` |

### 11.2 Phương pháp — dò biên NGAY TRONG không gian framebuffer

Dùng lại **đúng** phép của §2 (homography DLT bình phương tối thiểu, pure python + Pillow; máy này **không có numpy**
nên mọi phép là python thuần — cùng lý do §2 phải làm thế), nhưng đảo chiều dùng: sau khi khớp `ảnh → fb` từ các
điểm neo gmaps của §2, khớp luôn chiều ngược `fb → ảnh` trên **cùng** bộ neo, rồi **quét từng hàng `y` của
framebuffer**: đi từ ngoài vào, hàng nào có 12 điểm ảnh liên tiếp sáng hơn ngưỡng thì đó là mép. Quét trong không
gian fb (thay vì quét trong ảnh rồi chiếu điểm biên) cho thẳng bảng `x` theo `y` — đúng thứ mã cần.

Hai cái bẫy đã gặp và cách vượt:
- **Ngưỡng 60 là quá thấp**: quanh mép sáng có **quầng loé** trên viền đen, độ sáng bò từ 38 → 61 rồi mới nhảy vọt
  lên ~210. Ngưỡng 60 bắt vào quầng ⇒ mép lệch ra ngoài 10–20 px. Dùng **110** (giữa quầng và nội dung).
- **Quét từ giữa ra thì vấp nội dung tối** (ảnh camera, chữ đen trong bản đồ) ⇒ quét **từ ngoài vào** + đòi 12 điểm
  liên tiếp.

Kiểm bằng mắt: vẽ lại các điểm biên dò được lên chính ảnh chụp (`scratchpad/mask-277/check-cum0*.png`) — bám sát.

### 11.3 Kết quả — mép trái là một đường **"<"**, không phải cung tròn

`cum-0` (10 neo, sai số lớn nhất **11,0 fb px**) là ảnh chuẩn: neo trải cả bề ngang (x 75…1750), và nó là ảnh **không
có** cửa sổ camera che mép. `cum-1` (8 neo, 20,7 px) và `cum-2` (7 neo, 3,8 px nhưng neo chỉ ở **nửa phải**) làm kiểm
chéo — `cum-2` không dùng được cho mép TRÁI (ngoại suy).

| fb `y` | 136 | 189 | 242 | 295 | 348 | 401 | 454 | 507 | 560 |
|---|---|---|---|---|---|---|---|---|---|
| mép trái `cum-0` | 42 | 18 | 19 | 31 | 46 | 62 | 82 | 101 | 123 |
| mép trái `cum-1` | 88 | 44 | 32 | 35 | 41 | 50 | 61 | 74 | 89 |
| **mô hình (ngoài cùng)** | **42** | **18** | **19** | **31** | **41** | **50** | **61** | **74** | **89** |

Hình dạng: `x ≈ 42` ở đỉnh dải → **xa nhất `x ≈ 16–18` quanh `y ≈ 210`** → vào lại `x ≈ 89–123` ở đáy dải. Tức **không
đơn điệu** (ra rồi vào), nên:

- **khớp parabol**: sai **72,8 px** ⇒ loại;
- **cung tròn**: không có bán kính nào đỡ được cả hai nhánh (nhánh trên dốc ≈ −0,30 px/px, nhánh dưới ≈ +0,31 nhưng
  `cum-1` cho +0,19) ⇒ loại;
- **bảng 9 mẫu chia đều + nội suy tuyến tính**: sai số so với phép dò **từng hàng** là **5,5 px** ⇒ **chọn**. Đây là
  `ClusterBandSpec.leftEdge`.

**Sai số giữa các ảnh: tới 46 px**, và nó là một **xu hướng tuyến tính theo `y`** (cum-1 lệch +46 ở đỉnh, −36 ở đáy;
cum-2 so với cum-0 lệch −43 ở đỉnh, +17 ở đáy) ⇒ [SUY] méo xuyên tâm của ống kính điện thoại (homography không mô tả
được) chứ không phải kính cụm khác nhau. **Chốt hướng lệch, có lý do**: lấy ước lượng **NGOÀI CÙNG** ở mỗi hàng
(`min(cum-0, cum-1)`), vì lệch RA chỉ làm mất vài px ảnh **sau viền** (không ai thấy), còn lệch VÀO để lại **đúng khe
đen** mà owner đang chê. Lề thêm = **0**.

### 11.4 Mép PHẢI vẫn thẳng — cũng là một phép đo  ⚠ **ĐÃ BỊ §12 BÁC (2.78)**

> Kết luận dưới đây SAI ở đúng một chỗ: cột icon ADAS **không chặn** cửa sổ, hệ thống chỉ **vẽ đè** lên nó (D6).
> [ĐO xe 27/09 tối] owner: *"bên phải không bám, còn thừa 1 khoảng"*. Số đo mép phải + mô hình: **§12**.

Kính phải trong dải: `x ≈ 1876` (y 136) → `1906` (y 216) → `1802` (y 556). Nhưng **cột icon hệ thống (biển 30 / ADAS)
bắt đầu từ `x ≈ 1798`** (F7) và nằm **trái hơn kính ở MỌI hàng của dải** ⇒ thứ chặn mép phải là cột icon, và cột icon
thì **thẳng**. Nới mép phải ra tới kính = **đè lên biển báo tốc độ / ADAS** trên một chiếc xe đang chạy ⇒ **không
đổi**; `rightEdge` không tồn tại, `right = 1780` giữ nguyên. (Nếu owner muốn bên phải cũng ôm kính: xem 🚗 CAM-CL4.)

### 11.5 Cơ chế

1. **`:core`** `ClusterBandSpec.leftEdge: List<Int>` (rỗng = **đời cụm chưa đo** ⇒ tường thẳng `left`, hành vi 2.76
   y nguyên); `CameraClusterBand.leftEdge()` co giãn theo trục ngang + kẹp `0..band.x0`; `leftEdgeAt()` nội suy;
   `maskLeftAt()` = mép **có mực** sau mặt nạ; `insideBand()` = bất biến an toàn thay cho `band.contains(rect)`.
2. **`place()`**: đầu TRÁI trượt ra tới `min(leftEdge)` — **bề rộng và chiều cao không đổi một px** (nới bề rộng là
   kéo giãn ảnh, đúng thứ `CameraOverlayFrame.fit` sinh ra để tránh); phần thừa do mặt nạ cắt. Đầu PHẢI không đổi.
3. **`:app`** `CameraOverlayMask.kt` (mới, 105 dòng): `CameraGlassFrame` cắt bằng `Canvas.clipPath` trong `draw()`,
   `glassMask()` = chữ nhật bo góc **giao** đa giác cong. **Không** dùng `ViewOutlineProvider`: [ĐO source] AOSP
   `android-10.0.0_r47` `Outline.canClip()` là đúng một dòng `return mMode != MODE_CONVEX_PATH;` còn `setConvexPath()`
   đặt `mMode = MODE_CONVEX_PATH` ⇒ outline mang đường bất kỳ **không cắt được**. `labelFor` dời sang tệp này để
   `CameraOverlayView.kt` ở dưới trần 500 dòng (492).
4. Dòng log `overlay show` thêm `cong=<đỉnh>/<giữa>/<đáy>` — đây là mép **có mực** (`maskLeftAt`) ở ba hàng của
   cửa sổ, không phải số thô trong bảng ⇒ đọc một dòng logcat là biết ảnh bắt đầu ở đâu.
5. **Guard cứng ở tầng thi hành** (CLAUDE.md §5): `place()` tự kiểm `insideBand()` và **rơi về tường thẳng 2.76**
   nếu một hồ sơ/cỡ display lạ đẩy cửa sổ ra khỏi dải — mất đường cong, không mất camera.

### 11.6 Quyết định

| # | Quyết định | Lý do / bằng chứng |
|---|---|---|
| D8 | Mô hình = **bảng 9 mẫu**, không phải cung tròn/parabol | 11.3: parabol sai 72,8 px; bảng sai 5,5 px |
| D9 | Lấy ước lượng **ngoài cùng** giữa các ảnh, lề 0 | lệch RA = mất vài px sau viền; lệch VÀO = đúng khe đen owner chê |
| D10 | Mép phải **giữ thẳng 1780** | 11.4 — cột icon ADAS chặn trước kính ở mọi hàng; đè biển 30 là regression an toàn |
| D11 | **CHỮ NHẬT trên CỤM ⇒ *theo cụm*** (`effectiveShape`) | owner: *"bé tý… không hề theo hình cụm"* vs *"này nhìn OK"*; hai hình **cùng crop** (`cropFor` quy `CLUSTER→RECT`) ⇒ chỉ đổi CỬA SỔ, không đổi một điểm ảnh nội dung. Chữ nhật là hình **mặc định** ⇒ đường mặc định phải là đường tốt |
| D12 | **TRÒN trên cụm giữ nguyên** (vẫn đi `boxIn`) | "tròn" là lựa chọn cố ý về NỘI DUNG (crop ô vuông); quy nó sang hình dải là đổi cả ảnh — đúng cái bẫy D2 tránh |

### 11.6b Vì sao đủ an toàn để ship cho một chiếc xe đang chạy

Cái thật sự đổi là **vị trí** cửa sổ (`x` 140 → 18), không phải một cơ chế vẽ mới. Và ở **mọi** hàng của dải, đường
cong đo được nằm ở `x ≥ 18` ⇒ mép trái của cửa sổ luôn ở NGOÀI hoặc ĐÚNG viền kính ⇒ **chính tấm viền vật lý đã cắt**
phần thừa. Nếu `clipPath` vì lý do nào đó không ăn trên ROM xe (`SurfaceView`, một bản HWUI khác…), thứ owner thấy
vẫn là ảnh chạy sát viền — **không** phải một cửa sổ tràn ra ngoài. Mặt nạ là lớp thứ hai (giữ nền đen + bo góc không
liếm vào phần kính còn nhìn thấy), không phải thứ duy nhất giữ đúng hình.

Ba mép còn lại (trên, dưới, phải) **không đổi một px** so với bản 2.76 mà owner đã duyệt trên xe ⇒ bất biến [P1] của
2.76 (không đè thanh trên/dưới, không đè cột ADAS) còn nguyên, có bài test đo từng hàng.

**Owner sẽ thấy gì**: hình *Chữ nhật* (mặc định) và *Theo cụm* trên cụm nay **giống hệt nhau** — một khung cao trọn
dải giữa, mép trái **ôm đúng đường cong của kính** thay vì dừng ở một đường dọc cách viền 51…122 px. Chip trong *Cài
đặt › Camera* vẫn ghi "Chữ nhật" (tên chip thuộc làn khác) — **ghi nợ**, không phải lỗi.

### 11.7 Test

- `:core` `CameraClusterBandTest` +5 bài: bảng Seal trong khoảng cho phép + hình "<" + bước không gắt · co giãn/kẹp/
  **thoái về tường thẳng khi hồ sơ chưa đo** · nội suy đúng ở hàng mẫu + kẹp ngoài dải + luôn trong `0..band.x0` ·
  **64 tổ hợp** (bên × 4 crop × 4 góc xoay × biết/chưa biết cỡ): cỡ và `y` **bằng đúng** bản không cong, `insideBand`,
  và **mọi hàng** của cửa sổ có `maskLeftAt ≥ leftEdgeAt` (không bao giờ vẽ ra ngoài kính) · spec cong vô lý ném.
  Bài `theo cum thoai…` đổi tên + thêm phép quy D11.
- `:app` `CameraClusterBandWiringContractTest` +1 bài (mặt nạ dựng từ `Placement`, `clipPath`, không `FrameLayout`
  trần, không `setConvexPath`) + 2 assert mới trong bài (b)/(c) + 1 assert ghim `BAND_EL` của script.
- Máy ảo: `scripts/emulator/camera-cluster-e2e.sh` chấm mép trái theo `min(leftEdge)` và **vẽ đường cong** (xanh lơ)
  lên ảnh bằng chứng. Chạy 27/09 19:25 (emulator-5554, overlay display 1920×720) — **ĐẠT**: trái
  `[18,136]-[583,560]`, phải `[1215,136]-[1780,560]`, thoái màn chính `hình=RECT`.
- **Phép cắt được chứng minh bằng điểm ảnh** (không chỉ bằng số cửa sổ): nhãn *"Left camera"* nằm ở góc trên-trái
  cửa sổ nên chữ **L** bị chính mặt nạ xén. Đo điểm sáng trái nhất từng hàng trên ảnh máy ảo: `y=156 ⇒ x=32`
  (mô hình 32,9) · `y=160 ⇒ 30` (31,1) · `y=164 ⇒ 30` (29,2) ⇒ lệch ≤ **1,9 px**. Ảnh:
  `scratchpad/car-0927pm/mask-277/mask-clip-proof.png` (xanh = mô hình, đỏ = đo được). Đây là bằng chứng THẬT rằng
  `Canvas.clipPath` ăn vào nội dung của cây view trên display cụm — không phải suy luận.

### 11.8 🚗 Kiểm trên xe (runbook 2.77)

- **CAM-CL3**: Cài đặt › Camera › hình **Chữ nhật** (mặc định) *hoặc* **Theo cụm** + *Hiện lên cụm* → bật xi-nhan
  **trái** ⇒ mép trái của ảnh **bám đường cong của kính**, không còn khe đen dọc; logcat
  `overlay show … hình=CLUSTER … dải=140,136-1780,560 tại=18,136 cong=42/41/89`. **FAIL** nếu (a) còn khe đen ⇒ chụp
  ảnh, giảm các số trong `ClusterBandSpec.SEAL_DL3.leftEdge` (kéo ra), hoặc (b) ảnh **bị cắt** thấy rõ ở mép trái ⇒
  tăng các số ấy (lùi vào). Một ảnh chụp cụm + dòng logcat là đủ để chỉnh.
- **CAM-CL4** (câu hỏi, không phải phép đo): bên **phải** vẫn là mép thẳng vì cột biển-30/ADAS chặn trước kính
  (11.4). Owner muốn bên phải cũng ôm kính không, biết là sẽ **đè lên biển báo tốc độ**?

### 11.9 Lượt GỘP hai làn (điều phối 2.77) — hai thứ sửa ở gốc

**(1) [P1] Đường cong của MỘT miếng kính đang là mặc định cho MỌI đời cụm.** Làn này khai bảng `leftEdge` [ĐO trên
kính Seal DL3] vào chính `ClusterBandSpec.SEAL_DL3`, mà `ClusterProfile.band` lại lấy bộ ấy làm **giá trị mặc định
của tham số** — đúng theo quyết định 2.76, khi bộ ấy chỉ còn là 4 số **ĐẶT** cửa sổ (đặt lệch thì nhìn thấy ngay).
Từ 2.77 bảng ấy **CẮT** điểm ảnh (`glassMask` → `Canvas.clipPath`, tới **122 px** bên trái. Hệ quả: `ClusterProfile.DL5`
và `GENERIC_FALLBACK` (đường tới được **thật**: `detectSeed("BYD AUTO","byd","byd","dilink5")` trả `DL5`) cùng mọi
chuỗi hồ sơ owner dán qua `parse()` sẽ bị **xén ảnh theo miếng kính của xe khác, âm thầm** — đúng loại lỗi
CLAUDE.md §7 cấm (khác biệt đời xe phải nằm trong hồ sơ, không nằm trong một mặc định dùng chung).

Vá: thêm `ClusterBandSpec.SEAL_DL3_NO_CURVE` (`= SEAL_DL3.copy(leftEdge = emptyList())`) và `ClusterProfile.bandFor(id)`
**cùng khuôn với `cameraFor(id)`** đã có — chỉ `seal_dl3` mang bảng cong, mọi id khác nhận tường thẳng; mặc định của
tham số `band` đổi sang bản không-cong (quên khai ⇒ nhận cái an toàn), `SEAL_DL3` khai tường minh bộ có cong, và
`parse()` gán `band = bandFor(id)`. **Bốn số đặt cửa sổ vẫn dùng chung** (quyết định 2.76 không đổi) — chỉ đường
**CẮT** là theo đời. Rơi về = mất đường cong, **không** mất camera.

Khoá lại bằng 3 bài, cả 3 **đã thử-đỏ** (đổi mặc định về `ClusterBandSpec.SEAL_DL3` ⇒ 3/14 đỏ):
`ClusterProfileCameraDefaultsTest.duong cong kinh chi cho doi DA DO, doi khac tuong thang 2 76` (theo **hành vi**:
`leftEdge` rỗng cho DL5/generic/id-lạ/`detectSeed` DL5, có cho Seal) · `…tuong thang thi cua so o x0 va khong co mat na`
(`place()` trả đúng `band.x0` = 140 và `maskLeftAt` = tường thẳng ⇒ `glassMask` sẽ trả `null`) ·
`CameraClusterBandWiringContractTest` (mặc định là `SEAL_DL3_NO_CURVE`, `bandFor` tồn tại, và `parse` vẫn **không**
đọc dải từ chuỗi owner dán).

**(2) KDoc nhắc lại một lệnh KHÔNG TỒN TẠI.** `CameraSettingsIa` (làn L1) viết *"chỉ còn đường `prefs_set`/`prefs_get`"*
— nhưng audit 27/09 đã chốt **`prefs_get` chưa bao giờ tồn tại**, lệnh đọc thật là `prefs --es file clusternav_prefs`
(`TestBridgeNoHome.kt:26`), và đã gỡ tên ấy khỏi hai runbook. Vì 15 khoá ẩn-UI nay **chỉ** còn đường đó, một KDoc sai
tên lệnh là đúng thứ làm owner gõ nhầm trong xe ⇒ sửa tại chỗ, kèm lý do.

### 11.10 Nợ ghi nhận, KHÔNG sửa ở lượt gộp

- **Chip *"Chữ nhật"* nói không hết sự thật**: trên cụm, `effectiveShape` nâng CHỮ NHẬT → *theo cụm*, nên hai chip
  *Chữ nhật* và *Theo cụm* cho **cùng một kết quả** trên cụm (khác nhau chỉ còn trên màn chính). Owner sẽ thấy hai
  lựa chọn làm một việc — đúng thứ *"nhiều option quá rối"* mà 2.77 sinh ra để chữa. **Không gộp chip ở lượt này**:
  pref `camera_shape` của owner đang là một trong hai mã, và bỏ một chip khỏi `SHAPES` là đổi UI **chưa đo** (hành vi
  của `chipRow` khi giá trị lưu bền không còn trong danh sách chưa có bài test) trong một bản owner thử sau ít phút.
  Việc đúng cho lượt sau: hoặc gộp còn 2 chip (*Chữ nhật* · *Tròn*) vì CHỮ NHẬT trên cụm nay đã là hình đẹp, hoặc đổi
  nhãn để nói rõ *theo cụm* chỉ có nghĩa trên cụm. Cần owner chốt.


---

## 12. 2026-09-27 (tối, sau buổi xe) — MÉP PHẢI cũng ôm kính · không ép hình · mép trong mờ dần (2.78, làn L3)

> Owner nhìn 2.77 đang chiếu trên cụm và nói ba việc: (a) *"xi nhan trái bám nhưng **cắt rát quá, bị mất nhiều**"*;
> (b) *"**bên phải không bám, còn thừa 1 khoảng**"*; (c) *"khi chiếu camera lên cụm, user vẫn có thể chọn chữ
> nhật/tròn/theo cụm nhé, **không ép**"*, *"phần cạnh bên phải thêm tý blur ra ngoài cho nó smooth, **ko là 1 vạch
> thẳng nhìn nó như sẹo**"*, *"tròn và chữ nhật thì **canh đều, cân đối 2 bên** trái phải cả trên cụm"*.
> (a) đã xử trước (đặt ở điểm TRONG CÙNG thay vì điểm xa nhất). Mục này là (b) + (c).

### 12.1 Vì sao 2.77 kết luận sai về mép phải

§11.4 viết *"mép PHẢI vẫn thẳng — cũng là một phép đo"*, lý lẽ: cột icon hệ thống (biển 30 / ADAS) bắt đầu ở
`x ≈ 1798`, **trái hơn kính ở mọi hàng**, nên thứ chặn là cột icon chứ không phải kính. Bằng chứng xác nhận owner
đúng nằm ngay trong log của lượt xe: `overlay show corner=TR side=RIGHT cluster=true … tại=1462,136
cong=1462/1462/1462` — ba mẫu `cong=` **bằng nhau và bằng `p.x`**, tức bên phải không có mô hình cong nào, chỉ là
tường thẳng `band.x1 − w`. Cái §11.4 bỏ qua: cột icon **không chặn cửa sổ**, nó chỉ được hệ thống **vẽ đè lên**
(quyết định D6 đã chấp nhận điều đó với mũi tên xi-nhan) — nên không có lý do gì để mép phải dừng ở `1780`.

### 12.2 Phương pháp — **đúng** phép của §11.2, đổi mỗi hướng gộp

Chạy lại `scratchpad/mask-277/edge2.py` (homography DLT thuần python + Pillow, quét từng hàng fb từ ngoài vào,
ngưỡng `110`, đòi 12 điểm liên tiếp) nhưng lấy cột `R` thay cột `L`, rồi lấy 9 mẫu ở **cùng** các hàng của bảng
trái (`y = 136, 189, …, 560`) — script `scratchpad/mask-277/rightedge.py`.

| Ảnh | Neo · sai số lớn nhất | Dùng cho mép phải? |
|---|---|---|
| `cum-2` | 7 · **3,8 fb px** | **Có** — neo nằm ở nửa PHẢI (fb x 1195…1801), đúng vùng cần |
| `cum-0` | 10 · 11,0 fb px | **Có** — neo trải cả bề ngang |
| `cum-1` | 8 · 20,7 fb px | **Không** — neo dồn về nửa trái ⇒ ngoại suy sang phải lệch tới **185 px** (có hàng trả 2035, ngoài cả display). Đối xứng với việc §11.3 loại `cum-2` khỏi mép TRÁI |

### 12.3 Số đo — mép phải là đường **")"**

| fb `y` | 136 | 189 | 242 | 295 | 348 | 401 | 454 | 507 | 560 |
|---|---|---|---|---|---|---|---|---|---|
| `cum-0` | 1876 | 1904 | 1904 | 1895 | 1879 | 1863 | 1843 | 1823 | 1800 |
| `cum-2` | 1833 | 1871 | 1876 | 1872 | 1866 | 1856 | 1845 | 1833 | 1817 |
| **mô hình (trong cùng)** | **1833** | **1871** | **1876** | **1872** | **1866** | **1856** | **1843** | **1823** | **1800** |

- **Sai số nội suy tuyến tính 9 mẫu vs phép dò từng hàng: 7,7 px** (bảng trái: 5,5 px).
- **Lệch giữa hai ảnh: tới 44 px**, và lại là xu hướng tuyến tính theo `y` (`cum-0` lệch **+43** ở đỉnh, **−17** ở
  đáy) — cùng dấu hiệu méo xuyên tâm ống kính điện thoại như §11.3, không phải kính cụm khác nhau.
- Kiểm bằng mắt: `scratchpad/mask-277/checkright-cum-{0,2}.png` (vẽ bảng lên chính ảnh chụp) — bám sát mép sáng ở
  `cum-2`, và nằm **hơi vào trong** ở `cum-0` (đúng hướng an toàn đã chọn).

### 12.4 ⚠ Hướng làm tròn NGƯỢC với mép trái — cùng một lý do

| | An toàn (không mất video) là | Gộp nhiều ảnh | Nghĩa của phép gộp |
|---|---|---|---|
| Mép TRÁI | `x` **lớn** hơn (vào trong) | `min` | ước lượng **NGOÀI** cùng |
| Mép PHẢI | `x` **nhỏ** hơn (vào trong) | `min` | ước lượng **TRONG** cùng |

Cùng công thức `min`, ngược ý nghĩa — vì "ra ngoài" ở hai mép là hai chiều ngược nhau. Lý do vẫn là luật owner
27/09: *"không để mất video là OK, mỹ thuật nhưng thực dụng"*. Lệch VÀO chỉ để hở vài px kính (không ai đo được),
lệch RA thì đẩy điểm ảnh video ra **sau viền đục** — mất thật.

### 12.5 Kết quả thực tế — nới được **20 px**, và đó là sự thật của miếng kính

Cửa sổ *theo cụm* cao **trọn dải** nên [innermostRight] lấy `min` trên cả 9 mẫu = **1800** (hàng đáy), ⇒ mép phải
cửa sổ đi từ `1780` → `1800`. Bên trái nới được nhiều hơn (`140` → `89`, 51 px) chỉ vì kính trái ở hàng đáy vào ít
hơn. **Không phải thiếu sót**: kéo mép phải ra tới chỗ kính rộng nhất (`1876`) sẽ khiến mặt nạ cắt tới **76 px**
video ở hàng đáy — đúng lỗi *"cắt rát"* mà bản tối nay vừa sửa ở mép trái.

⚠ Hệ quả cần nói thẳng: **sau 2.78 hai mép của cửa sổ vẫn là hai đường THẲNG đứng** (`maskLeftAt`/`maskRightAt`
bằng đúng mép cửa sổ ở mọi hàng, bài `:core` khoá điều đó), chỉ là **sát kính hơn**. Muốn viền video **cong theo
kính ở từng hàng** mà không để hở thì buộc phải **phóng ảnh để lấp** (zoom-to-fill) rồi cắt phần thừa — tức cố ý
mất rìa ảnh, đúng thứ owner vừa bác. Đây là một **lựa chọn của owner**, không phải giới hạn kỹ thuật: OQ mới.

Cột icon `1798` (F7): điểm trong cùng `1800` trùng nó **trong sai số ±8 px** của chính phép đo, nên cửa sổ dừng
gần như đúng chỗ biển-30 bắt đầu; hàng nào hệ thống vẽ biển báo thì nó **đè lên** camera (D6), không ngược lại.

### 12.6 Ba quyết định của owner 27/09 tối (cùng làn)

| # | Quyết định | Vì sao / làm ở đâu |
|---|---|---|
| D10 | **Không ép hình trên cụm** — gỡ nhánh thăng `RECT → CLUSTER` của 2.77 | Owner: *"user vẫn có thể chọn chữ nhật/tròn/theo cụm nhé, không ép"*. `effectiveShape` nay chỉ còn MỘT phép quy (hạ *theo cụm* → chữ nhật trên màn chính). Lời chê *"bé tý"* của 2.77 được chữa bằng `boxIn` (kẹp vào dải), không phải bằng đổi hình sau lưng user |
| D11 | Mép **TRONG** của hình *theo cụm* **mờ dần**, bề rộng = `radiusPx` (24 px ở cỡ tham chiếu ⇒ 4–8 % bề rộng cửa sổ), kẹp ≤ `w/4` | Owner: *"ko là 1 vạch thẳng nhìn nó như sẹo"*. Không thêm hằng mới: bo góc và dải mờ cùng một cỡ "mềm mép" nên khớp nhau ở hai góc trong; co theo display như mọi số khác. Mép NGOÀI vẫn cắt cứng (ngoài nó là viền đục, không ai thấy đường cắt). Thi hành: `CameraGlassFrame` vẽ vào một `saveLayer` rồi tô `LinearGradient` với `PorterDuff.DST_IN` ⇒ chỉ nhân alpha, không đổi một điểm màu nào |
| D12 | CHỮ NHẬT/TRÒN trên cụm **cân đối hai bên** | Owner: *"canh đều, cân đối 2 bên trái phải cả trên cụm"*. `boxIn` vốn soi gương; bài `chu nhat va tron tren cum can doi hai ben` khoá lại bằng số **đúng công thức của `:app`** (`box` + `layoutParams`, nhớ rằng góc phải dùng `gravity = END` nên `x` là độ lệch kể từ mép phải display) |

### 12.7 Cơ chế (2.78)

1. **`:core`** `ClusterBandSpec.rightEdge` (song song `leftEdge`, mỗi mẫu trong `right..refW`); `CameraClusterBand`
   thêm `rightEdge()` (co giãn + kẹp `band.x1..displayW`), `rightEdgeAt()`, `maskRightAt()`, `innermostRight()`
   (lấy `min` trên đúng dải hàng cửa sổ chiếm), `outerInkAt()` (mép có mực của bên cửa sổ đứng — cho dòng log),
   `fadePx()`; `Placement` mang thêm `rightEdge` + `atLeft`; `insideBand()` nay cho phép `x + w` tới `max(rightEdge)`.
   Phép nội suy gộp về **một** hàm `edgeAt(…, fallback)` dùng chung hai mép (DRY).
2. **`place()`**: `x = innermostRight(...) − w` ở nhánh `!atLeft`. Guard cứng `insideBand` giữ nguyên — hồ sơ/cỡ
   display lạ ⇒ rơi về tường thẳng **hai bên** (xoá cả hai bảng), mất đường cong chứ không mất camera.
3. **`:app`** `CameraOverlayMask.glassMask` cắt cả hai mép qua `halfPlane(p, edge, atLeft)`; `glassFade(p)` +
   `CameraGlassFrame(mask, fade)`; `CameraOverlayView.geometry` truyền `rightEdge = edgeRight` và in `cong=` bằng
   `outerInkAt` (trước là `maskLeftAt` — trên cửa sổ bên PHẢI nó in ra ba số vô nghĩa, chính là `1462/1462/1462`).
4. `scripts/emulator/camera-cluster-e2e.sh`: thêm `BAND_ER`, chấm điểm bên phải theo `max(rightEdge)` (trước chấm
   `x1 <= 1780` ⇒ 2.78 sẽ FAIL oan), vẽ cả hai đường cong lên ảnh chứng cứ.

### 12.8 Test — đếm từ `build/test-results/**/TEST-*.xml`: `:core` **2952 bài / 0 lỗi**, `:app` **1501 / 0**

Trong đó `CameraClusterBandTest` **20/0** (2.77: 14), `CameraClusterBandWiringContractTest` **9/0** (2.77: 8),
`ClusterProfileCameraDefaultsTest` **6/0**.

- `cua so cong om kinh hai ben, mat na khong cat pixel nao` — **64 tổ hợp** (bên × 4 crop × 4 góc × biết/chưa biết
  cỡ) × **mọi hàng** của cửa sổ: `maskLeftAt == p.x` và `maskRightAt == p.x + p.w`; bề rộng/cao và `y` **không đổi**
  so với bản tường thẳng; `insideBand`; cộng ca cửa sổ **thấp hơn dải** (chỉ hàng bị chiếm mới tính).
- `bang mep phai Seal nam trong khoang cho phep` · `mep phai co gian va thoai ve tuong thang khi chua do` ·
  `noi suy mep phai dung o hang mau va kep ngoai dai` · `doi cum chua do van ra tuong thang hai ben` ·
  `dai mo nam o mep trong va bang ban kinh bo` · `chu nhat va tron tren cum can doi hai ben` ·
  `theo cum thoai ve chu nhat tren man chinh, nhung KHONG ep hinh tren cum`.
- **Đỏ trước – xanh sau** (bài chính): đảo `place()` về `band.x1 − w` ⇒ `cua so cong om kinh hai ben…` ĐỎ với
  *"đầu phải đặt ở điểm TRONG CÙNG ==> expected: <1030> but was: <960>"*; trả lại ⇒ 20/20 xanh.

### 12.9 🚗 Kiểm trên xe (2.78)

- **CAM-CL5**: Cài đặt › Camera › hình **theo cụm** + *Hiện lên cụm* → xi-nhan **phải** ⇒ ảnh sát kính phải hơn
  2.77 (mép phải cửa sổ `1800` thay vì `1780`), **không hàng nào bị cắt**, mép TRÁI của cửa sổ (mép quay vào giữa)
  **mờ dần** chứ không phải một vạch thẳng; xi-nhan **trái** đối xứng (mép ngoài `89`, mép phải mờ). Logcat:
  `overlay show … hình=CLUSTER … tại=1235,136 cong=1800/1800/1800` (phải) / `tại=89,136 cong=89/89/89` (trái) —
  **ba số bằng nhau là ĐÚNG** (mặt nạ không cắt hàng nào). FAIL nếu còn khe đen rộng hơn ngón tay ở mép ngoài, hoặc
  thấy mất ảnh ở mép ngoài ⇒ chụp `cong=` gửi về.
- **CAM-CL6**: đổi hình sang **Chữ nhật** rồi **Tròn**, vẫn *Hiện lên cụm* ⇒ phải RA ĐÚNG hình đã chọn (2.77 tự
  đổi sang *theo cụm*), và khoảng hở tới mép trái dải = khoảng hở tới mép phải dải (đặt cân đối). Logcat phải in
  `hình=RECT` / `hình=ROUND` kèm `cluster=true`.
