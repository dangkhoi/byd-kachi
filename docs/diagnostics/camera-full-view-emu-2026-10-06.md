# CAMERA-FULL-VIEW (2.92) — đo trên máy ảo: ba kiểu hình × hai hình khung × hai bên trên khung xe thật, Nắn thẳng trước = sau từng điểm ảnh, nút Khung thô

> **Trạng thái**: Current · **Cập nhật**: 2026-10-06 · **Loại**: Diagnostics (đo máy ảo) · **Owner**: dangkhoi ·
> **Mục đích**: bằng chứng [ĐO máy ảo] cho spec [`specs/kachi-292-camera-full-view.html`](../specs/kachi-292-camera-full-view.html)
> (§6 V-emu · R1–R5 · R8) + hai phát hiện làm đổi phạm vi (nút Khung thô KHÔNG tới được trên bản phát hành — §5; vòng ảnh lớn
> hơn giả định — §6). **Backlog**: `CAMERA-FULL-VIEW` (+ `CAM-RAW-DIAG-REACH` · `ONCAR-CAM-FULL-VIEW`).
> Nghiên cứu nền: [`camera-rear-coverage-2026-10-06.md`](camera-rear-coverage-2026-10-06.md).

Mức bằng chứng theo CLAUDE.md §2 / conversation-protocol P1. Ảnh nguồn là **một khung tĩnh** chụp trên xe ⇒ mọi con số ở đây nói về
**ĐƯỜNG ỐNG** (prefs → kế hoạch phiên → uniform → shader → cửa sổ) và về hình học trên khung thật ấy; KHÔNG nói về mắt owner lúc lái,
GPU của đầu xe, hay đường `TextureView` (máy ảo không có camera AVM — §7). Phần ấy là 🚗 (spec §6 V-oncar).

## 1. Môi trường

| Mục | Giá trị | Mức |
|---|---|---|
| Máy ảo | AVD `clusternav10` (API 29, 1920×1080), `emulator-5554`, GL = bộ dịch GLES của máy ảo (GPU máy chủ) | [ĐO] `camera_frame.gl` |
| Bản NỀN | `vehicleTest` dựng từ worktree tách đúng `d320ece` (2.91, 193) | [ĐO] |
| Bản MỚI | `vehicleTest` dựng từ cây làm việc nhánh `feat/292-camera-full-view` (`versionName` vẫn 2.91 — chưa bump, chưa giao APK) | [ĐO] |
| Ảnh nguồn | `camera_synth file:e2e-car-frame.png` — khung HAL 5120×960 chụp trên Seal 27/09 (bản `scripts/emulator/camera-dewarp-e2e.sh --strip-png` đã đẩy vào thư mục ngoài của Kachi, mtime 27/09 09:59); bộ bơm giải mã 1/2 (2560×480) rồi kéo về 5120×960 | [ĐO] log `synth: png 2560x480→5120x960 (1/2)` |
| Prefs | bộ Seal: `render GL` · `span STRIP` · độ nắn 100 % · F 55 % · K 100 % · S 130 % · tâm/dịch 0 · xoay 0/0 · lật tắt · màn chính · góc TL/TR | [ĐO] `prefs_set` → `read_back` |
| Cách chụp | (a) `camera_frame 540x405` / `540x540` = `getBitmap` của `TextureView` = khung **RA** của shader ở cỡ tự nhiên của cửa sổ; (b) `screencap` cả màn | [ĐO] |
| Dữ liệu Kachi | sao lưu TRƯỚC khi cài bản thử, trả lại SAU — §8 | [ĐO] |

## 2. R2 — *Nắn thẳng* trước = sau, từng điểm ảnh

Cùng prefs, cùng khung bơm vào; bản NỀN 2.91 rồi bản MỚI (khoá `camera_projection` + `camera_zoom` chưa có ⇒ đọc thành *Nắn thẳng*
· 100 %). So `camera_frame` bằng PIL `ImageChops.difference`:

| Hình | Bên | Cỡ | Điểm khác | Lệch lớn nhất / kênh | sha256 dữ liệu điểm ảnh (16 ký tự) — NỀN = MỚI |
|---|---|---|---|---|---|
| Chữ nhật | trái | 540×405 | **0** | 0 · 0 · 0 · 0 | `91e3504135a03b86` |
| Chữ nhật | phải | 540×405 | **0** | 0 · 0 · 0 · 0 | `a7872dc5a86d3e93` |
| Tròn | trái | 540×540 | **0** | 0 · 0 · 0 · 0 | `b9458c3ae0240be9` |
| Tròn | phải | 540×540 | **0** | 0 · 0 · 0 · 0 | `9c7cacab8b5b9b02` |

[ĐO máy ảo] Bản MỚI in `kiểu=STRAIGHT … κ=1,00 … vừa=(0,000,0,000)` ⇒ shader đi nhánh cũ (`uFit.x = 0`), `θ = 1·atan(r, 1·F)`.
Phạm vi của phép so: bốn tổ hợp trên **một** GPU. Phần còn lại khoá bằng test thuần: `CameraViewPlanTest` (mười uniform cũ bằng hệt
`CameraGlUniforms.of` của 2.91 trên 288 tổ hợp) + `CameraDewarpKappaTest` (κ = 1 từng bit so với bản chép công thức cũ, 4×3×21×21
điểm). Trên GPU xe [SUY mạnh]: nhân với uniform 1,0 là phép chính xác ở mọi định dạng float, và đầu vào `atan` y hệt ⇒ cùng kết quả.

## 3. Ba kiểu × hai hình × hai bên

Uniform đọc nguyên văn từ dòng `overlay show … nắn=` (`CameraGlUniforms.describe`). **Đen** = điểm có R, G, B ≤ 8 trong phần
NHÌN THẤY của khung RA (chữ nhật: cả ảnh; tròn: đĩa nội tiếp), cùng một script cho mọi ô.

| Bên | Hình | Kiểu | Vùng nội dung · F · κ · dịch · `uFit` | Đen | Mức |
|---|---|---|---|---|---|
| trái | Chữ nhật | Nắn thẳng | dải 0,25–0,50 · 0,2488 · 1 · (0; 0) · (0; 0) | 0,3 % | [ĐO máy ảo] |
| trái | Chữ nhật | Thẳng rộng | dải 0,25–0,50 · **0,4523** · **1,5** · (**−0,2**; 0) · (1; 1) | 0,3 % | [ĐO máy ảo] |
| trái | Chữ nhật | Gương cầu | dải 0,25–0,50 · độ nắn **0** · (0; 0) · (1; 1) | 2,8 % | [ĐO máy ảo] |
| trái | Tròn | Nắn thẳng | ô vuông 0,28125–0,46875 · 0,3317 · 1 · (0; 0) · (0; 0) | 0,4 % | [ĐO máy ảo] |
| trái | Tròn | Thẳng rộng | **trọn dải** 0,25–0,50 · 0,4523 · 1,5 · (−0,2; 0) · (**1,250; 1,667**) | 20,4 % | [ĐO máy ảo] |
| trái | Tròn | Gương cầu | trọn dải · độ nắn 0 · (1,250; 1,667) | 40,6 % | [ĐO máy ảo] |
| phải | Chữ nhật | Nắn thẳng / Thẳng rộng / Gương cầu | dải 0,50–0,75; Thẳng rộng dịch (**+0,2**; 0) | 0,2 / 0,3 / 2,6 % | [ĐO máy ảo] |
| phải | Tròn | Nắn thẳng / Thẳng rộng / Gương cầu | như bên trái, gương dấu dịch | 0,1 / 20,8 / 40,5 % | [ĐO máy ảo] |
| trái | Tròn | Gương cầu · **thu phóng 125 %** | `uFit` (1,000; 1,333) | 15,2 % | [ĐO máy ảo] |
| trái | Chữ nhật | Thẳng rộng · **thu phóng 80 %** | `uFit` (1,250; 1,250) | 9,9 % | [ĐO máy ảo] |

Đối chiếu với thiết kế:

- `uFit` tròn (1,250; 1,667) = đúng công thức spec §4.3 với khung 960×960 và nội dung 1280×960: σ = 1/√((1280/960)² + 1) = 0,6 ⇒
  (960/(1280·0,6), 960/(960·0,6)) [ĐO máy ảo = ĐO tính]. Thu phóng 125 % ⇒ chia 1,25 ⇒ (1,000; 1,333) ✓; 80 % ⇒ (1,25; 1,25) ✓.
- Gương cầu tròn: chữ nhật nội tiếp 0,8 × 0,6 đường kính ⇒ phần đĩa ngoài chữ nhật = 1 − 0,48/(π/4) = 38,9 %, cộng góc tối của chính vòng
  ảnh (2,7 % của chữ nhật × 0,611) ≈ **40,5 %** — khớp 40,5–40,6 % đo được [ĐO tính] ⇒ không cắt điểm ảnh nào của dải (R3).
- Dấu dịch Thẳng rộng: trái −0,2, phải +0,2 = về phía ĐUÔI mỗi bên (`CameraDewarpPrefs.panXSign`) ⇒ đối xứng gương ✓.
- Thẳng rộng + Tròn: đen dồn ở **chỏm trên/dưới** (một phần ba trên 30,5–30,9 % · giữa 5,8–6,0 % · dưới 30,6–32,6 %) — nơi mặt phẳng
  đã nắn cần nguồn vượt quá bề cao dải; lệch về phía mũi xe (trái: nửa phải 24,7 % vs nửa trái 16,2 %; phải: ngược lại 24,2 / 17,5 %)
  vì khung dịch về đuôi. Chữ nhật không bị (0,3 %). Ghi thành spec OQ5 — chưa đổi thiết kế khi chưa có mắt owner.

Nhìn bằng mắt trên khung thật (mô tả — ảnh KHÔNG vào repo, §9):

- **Nắn thẳng** = 2.91: cột giữa đứng thẳng; chiếc xe đỗ phía đuôi bị kéo giãn ở mép và chỉ còn một nửa — đúng triệu chứng *"cắt hơi lố"*.
- **Thẳng rộng**: cột giữa vẫn thẳng; thấy trọn chiếc xe ấy và dãy xe sau nó; khoảng một phần tư khung phía đuôi là vùng θ ≈ 76–96° —
  kéo giãn mạnh, có mảng bóng loáng nghi là thân xe nhà [SUY].
- **Gương cầu**: trọn dải, cong đều kiểu mắt cá, bốn góc tối của vòng ảnh; chữ nhật khớp tỉ lệ (không viền), tròn có viền đen.

## 4. Cài đặt + áp ngay khi khung đang hiện

- [ĐO máy ảo] *Cài đặt › Tiện nghi xe*, ngay dưới *Hình khung camera*: tiêu đề **KIỂU HÌNH CAMERA**, hàng chip *Kiểu hình*
  [**Nắn thẳng**] [Thẳng rộng (thấy xa)] [Gương cầu (thấy hết)] (khoá chưa có ⇒ *Nắn thẳng* sáng), dòng ghi chú ba kiểu, thanh
  *Thu phóng* 100 %. Ô tích *Nắn hình* cũ không còn.
- [ĐO máy ảo] Khung trái đang hiện (Nắn thẳng) → chạm *Gương cầu (thấy hết)* ⇒ khung dựng lại **ngay**, dòng log mới
  `xi-nhan LEFT … kiểu=FISHEYE nội-dung=0.25, 0.0, 0.5, 1.0` (16:55:58), không cần bật xi-nhan lần nữa (R1 · `reapplyIfShowing`).
- [ĐO máy ảo] Tiêu đề hàng hình khung vẫn ghi *"(tròn = thấy trọn vòng ảnh, chưa nắn méo)"* — sai từ khi GL nắn cả khung tròn (log
  bản NỀN: `hình=ROUND … amount=1,000`) và nay mâu thuẫn hàng *Kiểu hình*. Đã đổi sau lượt chụp, 5 thứ tiếng: *"chỉ đổi viền — muốn
  thấy trọn ảnh, chưa nắn méo, thì chọn kiểu «Gương cầu» bên dưới"* (bài `CameraSpanShapeWiringContractTest` đổi theo) — [ĐO mã],
  chưa chụp lại.

## 5. R8 — nút *📷 Khung thô trái / phải*

- [ĐO máy ảo] Màn *Chẩn đoán* (mở bằng `am start` — bản `vehicleTest`, adb root của lượt đo): hai nút nằm giữa *Cổng theme (chỉ đọc)*
  và *⬇ Kiểm tra cập nhật*. Mỗi lần chạm: dòng trạng thái *"Đang chụp khung thô…"* → 2–3 s → dòng trạng thái + Toast:
  *"Đã lưu khung thô 5120×960: …/kachi-logs/camera-frame-<giờ>-raw-left.png"* · *"Thư viện ảnh: Pictures/Kachi/camera-frame-<giờ>-raw-left.png"*
  · dòng uniform của phiên đang treo (`kiểu=… srcRect=… κ=… vừa=…`).
- [ĐO máy ảo] Hai PNG 5120×960 (trọn khung HAL bốn dải) trong `kachi-logs/` + hai hàng MediaStore `RELATIVE_PATH = Pictures/Kachi/`
  (`content query`). Lượt xem thử tự đóng sau khi chụp; không pref camera nào đổi.
- **⚠ [ĐO mã] Trên bản PHÁT HÀNH (OTA) không có đường tới màn này**: owner 21/09 gỡ nút *Chẩn đoán* khỏi Cài đặt
  (`SettingsSectionsCast.kt:467-470`, `SettingsSections.kt:305-320`, bài `DevSurfaceGateContractTest` cấm dựng lại, gác hay không gác),
  `ClusterNavBridge.openDiagnostics()` 0 chỗ gọi, và `am start` màn không-export bị hệ thống từ chối trên bản phát hành [ĐO xe 29/09,
  `HUONG-DAN-KACHI.md` đầu tệp]. ⇒ Nút chỉ dùng được với bản `vehicleTest`/debuggable (`am start`) hoặc shell root. Nghiên cứu §5.3 ghi
  *"Cài đặt › Hệ thống › Nâng cao › Chẩn đoán"* là **cũ** (đã đính chính tại chỗ). Mở được trên bản phát hành = quyết định của owner
  (đảo luật 21/09) ⇒ spec OQ4, backlog `CAM-RAW-DIAG-REACH`; mã chụp (`CameraRawCapture`) dùng lại được cho bất kỳ lối vào nào owner chọn.

## 6. Vòng ảnh trên khung thật 27/09 — đo thô

Trên bản ĐỦ phân giải 5120×960 của khung bơm vào (độ sáng > 24/255): hàng giữa và cột giữa của từng dải 1280×960; bốn đường chéo từ góc
vào tâm dải, điểm sáng đầu tiên ⇒ khoảng cách tới tâm dải (800 px = sáng ngay tại góc). Chỉ in số, không lưu điểm ảnh.

| Dải | Hàng giữa sáng (x / 1280) | Cột giữa sáng (y / 960) | Mép tối tính từ tâm dải: TL · TR · BL · BR (px) | Mức |
|---|---|---|---|---|
| 0 sau | 0–1279 | 0–959 | sáng tới cả bốn góc | [ĐO] |
| 1 trái | 0–1265 | 0–959 | **799** (sáng tới góc) · 544 · 641 · 562 | [ĐO] |
| 2 phải | 0–1277 | 0–959 | 613 · **797** (sáng tới góc) · 637 · 641 | [ĐO] |
| 3 trước | 0–1279 | 0–959 | 786 · sáng tới ba góc còn lại | [ĐO] |

- [SUY] Ở hai dải gương, mép vòng ảnh cách tâm dải ≈ 544–641 px — **lớn hơn** nửa bề cao (480) và sát nửa bề ngang (640): vòng ảnh bị cắt
  trên/dưới, gần chạm mép trái/phải. Khớp giả thuyết H1 của nghiên cứu (K·S ≈ 376 px/rad ⇒ r(95°) ≈ 623 px), **không** khớp giả định
  *"vòng ảnh ≈ bề cao khung"* (Ø 960) của bộ số mặc định (`CameraDewarp.kt` KDoc, đã ghi lại).
- [SUY] Góc trên phía ĐUÔI (TL ở dải trái, TR ở dải phải) sáng tới tận góc ở cả hai dải — đối xứng gương. [CHƯA BIẾT] đó là ảnh hay
  lóa; ngưỡng một mức sáng ⇒ sai số cỡ chục px. Chốt bằng khung thô mới + đo tâm/bán kính (spec OQ2).
- **U1 của nghiên cứu (H1 hay H2) — câu "giữa chiều cao, mép đuôi là ảnh hay cung tối?"**: dải ngang y 400–560, tính từ mép đuôi vào:

  | Dải | 0–19 px | 20–39 px | 40–139 px | 141 px đầu (vùng Nắn thẳng không lấy) | giữa dải (x 560–719) |
  |---|---|---|---|---|---|
  | trái (đuôi −x) | TB 52 · 98 % sáng | TB 96 · 100 % | TB 61–96 · ≥ 96 % | TB 79 · **98,6 %** sáng | TB 93 |
  | phải (đuôi +x) | TB 26 · 62 % | TB 35 · 95 % | TB 62–83 · ≥ 86 % | TB 63 · **89,9 %** sáng | TB 101 |

  [ĐO trên khung 27/09] ở giữa cao, 141 px đầu phía đuôi **là ảnh**, chỉ 20–40 px sát mép tối dần (dải phải 0–19 px còn 62 % điểm sáng)
  ⇒ mép vòng ảnh nằm khoảng 0–20 px trong mép dải — đúng dự đoán H1 (*"vòng 190° kết thúc ở x ≈ 17 px"*, nghiên cứu §2.2); H2 (cung tối từ 141 px) bị bác trên khung này [SUY: một
  khung, ngưỡng sáng, chưa thấy cung]. Hệ quả cho 2.92: vùng *Thẳng rộng* kéo vào (θ 76–96°) có ảnh thật, không phải viền đen.
- [ĐO nhìn khung 27/09] Thân xe nhà: mảng xám bóng lớn ở góc **dưới-phải** dải trái và **dưới-trái** dải phải (đối xứng gương), một dải
  bóng mảnh chạy dọc đáy về phía đuôi. X3 của nghiên cứu (§2.3, hai ghi chép trái nhau) vẫn để mở — không đổi hướng đuôi (−x trái /
  +x phải, ba nguồn độc lập §2.1, trong đó vệt `KinexBlindSpot` [ĐO RE]).
- [ĐO] Khung 27/09 mà nghiên cứu K8/U4 tưởng đã mất **còn một bản đủ phân giải** trong máy ảo: thư mục ngoài của Kachi,
  `files/e2e-car-frame.png` (5 895 181 B, 27/09 09:59) — đã trả về đúng chỗ sau lượt đo (§8).

## 7. Giới hạn — chưa đo được ở máy ảo

| Hạng mục | Vì sao | Thay bằng | Còn lại |
|---|---|---|---|
| Đường `TV` (Gương cầu bằng ma trận, Thẳng rộng → Gương cầu) | bộ bơm ảnh tổng hợp chỉ có ở đường GL; máy ảo không có camera AVM | `CameraViewPlanTest` (tỉ lệ TV, ma trận) — [ĐO test thuần]; viền đen = nền đen của khung [SUY] | 🚗 |
| Đường `SV` | thiết kế: giữ nguyên, bỏ qua kiểu + thu phóng | đọc mã | — |
| Trên cụm (*Theo cụm* → chữ nhật, `fitInside`) | lượt này chỉ đo màn chính | `CameraViewPlanTest` (frameShape CLUSTER → RECT, `place(fitInside)`) | 🚗 |
| Xoay ±90 / 180 với kiểu trọn dải | lượt này chỉ đo xoay 0 | test góc nội dung nằm trong khung ở mọi hình × xoay × bên | 🚗 |
| GPU xe | khác GPU máy ảo | §2 [SUY mạnh] | 🚗 V-oncar 5 |

## 8. Trả máy ảo về như lúc nhận

| Bước | Kết quả | Mức |
|---|---|---|
| Lúc nhận | 2.89 (191) bản phát hành, adbd không root, `reverse tcp:5555`, máy ảo đang dừng (SIGSTOP) | [ĐO] |
| Sao lưu | `tar` thư mục dữ liệu (user + user_de) + thư mục ngoài; sha256 từng tệp `shared_prefs`; kéo APK gốc | [ĐO] |
| Dọn | xoá hai ảnh thử khỏi MediaStore + `Pictures/Kachi/`; xoá tệp dump UI | [ĐO] `content query` còn 0 |
| Trả | cài lại đúng APK 2.89 gốc (hạ bản); tráo nguyên thư mục dữ liệu từ bản sao lưu + `restorecon`; giải nén lại thư mục ngoài | [ĐO] |
| So | `shared_prefs` trùng sha từng tệp, trừ `clusternav_prefs.xml` — khác đúng một khoá `a11y_proc_start_elapsed` (app tự ghi mỗi lần tiến trình khởi động; dịch vụ trợ năng được hệ thống dựng lại sau force-stop) | [ĐO] `diff` |
| Còn lại | adbd không root · `reverse tcp:5555` nối lại · `KachiHome` ở tiền cảnh · máy ảo dừng lại (SIGSTOP); `lastUpdateTime` của gói đổi (cài lại — không tránh được) | [ĐO] |

## 9. Riêng tư

Khung 27/09 chụp ở hầm đỗ xe có xe của người khác (một dải thấy biển số). Ảnh chụp màn/khung RA của lượt này chỉ nằm trong thư mục tạm
của phiên làm việc, **không** vào repo; tài liệu này chỉ mô tả, không chép biển số hay chi tiết nhận dạng nào.
