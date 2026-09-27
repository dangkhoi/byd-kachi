# Camera 2.76 — IA hai tầng · mặc định theo hồ sơ xe · lùi CHANNEL → PANO (làn L1, off-car 2026-09-27)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 · **Mục đích**: đóng ba yêu cầu R1/R2/R3 của spec
> `docs/specs/kachi-276-closing.html` sau buổi xe 27/09 — (1) màn *Tiện nghi xe › Camera* chỉ còn thứ người lái cần,
> móc đo ẩn sau cổng chế độ kiểm thử; (2) mặc định camera đi theo **hồ sơ xe** (`ClusterProfile`), bộ Seal DL3 là
> bộ owner đã duyệt trên xe, bỏ hằng kênh ghim trên `CamView` ([P3] review Pass 2); (3) nguồn *Một camera* mà đầu máy
> không lên thì **lùi về toàn cảnh đúng một lần**, có log, có dấu trong Cài đặt; `camera_frame` mang ghi chú
> `anamorphic x4`. Liên quan: `../research-side-camera-orientation-2026-09-27.md` §6 · `../offcar-2026-09-26/camera-dewarp-gl.md`
> · backlog dòng `ONCAR-2026-09-27` + `2.75 (176)`.

Nhãn: **[ĐO]** số đo trên xe/log thật · **[SUY]** suy từ số đo, chưa đo trực tiếp · **[ĐOÁN]** · **[CHƯA BIẾT]**.

---

## 1. Bối cảnh / số đo dùng làm căn cứ

| # | Sự thật | Mức | Nguồn |
|---|---|---|---|
| B1 | Owner 27/09: *"có quá nhiều setting dùng cho việc test ở màn setting camera, cần bỏ ra… chỉ để lại setting mà user cần, đi theo xe, hoặc hiển thị"*; hỏi *"có chuyển mặc định sang một kênh không"* | [ĐO lời owner] | spec §Context |
| B2 | Màn camera 2.75 bày **28 khoá** phẳng (`camera_*` trong danh sách trắng `prefs_set`) — 9 núm nắn, dải, bề rộng, kênh HAL, cameraId, ma trận texture | [ĐO mã] | `TestBridgeWritableKeys.kt` |
| B3 | Bộ tham số owner **duyệt bằng mắt trên xe** (trái, xoay 0): `span=STRIP · render=GL · amount=100 · focal=55 · k=100 · scale=130 · cx=cy=0` — *"thẳng, tự nhiên, thấy 2 bánh"*; bác `cx/cy ≠ 0` (cong), `scale ≥ 140` ("heavy") | [ĐO 27/09 10:30–11:30] | backlog `ONCAR-2026-09-27` |
| B4 | Kênh HAL: `addPreviewSurface(surface, n)` với `n = 2` = gương **TRÁI**, `n = 3` = gương **PHẢI** (cột E4/E3); buffer vẫn 5120×960, khung một camera kéo ngang ×4 | [ĐO 27/09 11:16] | backlog `2.75 (176)` |
| B5 | Khung HAL thô = 4 dải fisheye **ĐỨNG** (mặt đất dưới); research §6.1: 18/20 hệ CMS/BVM ngành hiện **đứng**, Kinex mặc định 0 ⇒ khuyến nghị mặc định xoay 0 hai bên, **không ghi đè** pref owner đã chọn (↺90) | [ĐO ảnh + tài liệu] | `research-side-camera-orientation-2026-09-27.md` §4, §6 |
| B6 | Adreno 610, `GL_MAX_TEXTURE_SIZE = 16384` ⇒ đường GL chạy được trên Seal (Q13 chốt) | [ĐO CAM-B1] | backlog `ONCAR-2026-09-27` |
| B7 | HAL đẩy ~34 khung/giây khi đã chạy (chu kỳ 29 ms); helper HAL đứt–nối lại 1,7 s; một phiên xi-nhan thật 09:58:15 → 09:58:26 (11 s) | [ĐO CAM-B4 · E9 · usage log] | backlog · `camera-dewarp-gl.md` §G |
| B8 | Ở 2.75, `addPreviewSurface(surface, n)` trả `rc = false` ⇒ `AvmCamera` rơi **im** về đường một tham số (khung ghép) trong khi tầng vẽ vẫn chia bề ngang cho 4 | [ĐO mã `AvmCamera.open` 2.75] | `AvmCamera.kt` |
| B9 | Khung đầu của một phiên CHANNEL tới sau bao lâu | **[CHƯA BIẾT]** | 🚗 CAM-F2 |

---

## 2. Cơ chế

### 2.1 IA hai tầng (R1)

Hợp đồng thuần ở `:core` **`CameraSettingsIa`** (`USER_KEYS` **11** · `TECH_KEYS` 16 · hợp = đúng **27** khoá `camera_*` của danh
sách trắng `prefs_set`, rời nhau — `CameraSettingsIaTest`). `:app` **`SettingsSectionsCamera.kt`** (tách khỏi
`SettingsSectionsCar.kt`, 480 → 266 dòng) dựng theo đúng danh sách ấy:

| Tầng | Hàng trên màn | Khoá | Cổng |
|---|---|---|---|
| Người lái (`cameraUser`) | Bật camera khi xi-nhan · Hiện lên cụm · Góc trái/phải · Xoay trái/phải · Hình khung (chip **sinh từ `SHAPES`**: chữ nhật · tròn · **theo cụm** — ô CLUSTER của làn L2) · **Nắn hình** (ô tích = `camera_dewarp_amount` 100/0) · **Lật gương trái/phải** (L7) · **Nguồn** (toàn cảnh / một camera) | 11 | luôn; hàng *Nguồn* chỉ khi `bridge.cameraChannelSupported()` |
| Kỹ thuật (`cameraTech` trong khối gập *Nâng cao (kỹ thuật) · 16 mục*) | Kết xuất TV/SV/GL · Bề rộng vùng gương · Dải trái/phải · Kênh xem HAL · cameraId trái/phải · ma trận texture · 8 núm nắn (tâm → K → tiêu cự → phóng → độ nắn → dịch) | 16 (`camera_circle_scale` chỉ có `prefs_set`, không hàng — như 2.75) | **`TestBridgeStore.isOn(context)`** — cùng cổng 60 phút của cầu kiểm thử, không cờ mới; tắt ⇒ không dựng (không mờ) |

Khoá **không xoá**: `prefs_set`/`read_back` nguyên; giá trị đã đặt trên xe vẫn có hiệu lực khi hàng ẩn. Nhãn
*"(mặc định)"* bỏ khỏi chip TextureView/Vệt hẹp/Chữ nhật — mặc định nay theo hồ sơ, một nhãn cố định sẽ sai cho xe khác.

### 2.2 Mặc định theo hồ sơ xe (R2)

```
Prefs.cameraSpan/Render/Rotation(left)          (PrefsAutomation, 3 dòng đổi fallback)
Prefs.cameraDewarp{Amount,Focal,K,Scale,Cx,Cy,PanX,PanY}   (PrefsCameraDewarp)
        │  khoá VẮNG ⇒ fallback = CameraDefaults.of(ctx).<trường>   (khoá đã đặt ⇒ THẮNG, getX(key, fallback))
        ▼
CameraDefaults.of(ctx) = ClusterProfile.resolveCached(ctx).camera.sane()     (:app, đệm theo tiến trình; xoá khi save/clearOverride)
        ▼
ClusterProfile.camera : CameraProfileDefaults (:core)
   SEAL_DL3  → SEAL_DL3_CAMERA  = STRIP · GL · 100/55/100/130 · cx0 cy0 · pan0/0 · rot 0/0 · kênh 2/3      [ĐO B3 B4 B5 B6]
   DL5 · GENERIC_FALLBACK · id lạ (parse) → NEUTRAL = đúng literal 2.75 (NARROW · TV · 100 · trái ↺/phải ↻ · kênh chưa đo)
```

- `camera` **không** vào chuỗi export/parse (chuỗi share là *"chiếu cụm thế nào"*); `parse` gán theo `id` seed
  (`cameraFor`) ⇒ round-trip seed giữ bộ đo, id lạ ⇒ trung tính.
- Kênh per-side: `CameraSignalPolicy.channelFor(source, halModePref, profileChannel)` — **bỏ** `CamView.channel = 2/3`
  ([P3] Pass 2 đóng). Hồ sơ chưa đo ⇒ `CHANNEL_UNKNOWN` ⇒ `channelFor` về AUTO **và** `channelActive = false` ⇒ tầng
  hình học **không** chia bề ngang cho 4 trên một khung thật ra là khung ghép (ca sai im lặng của 2.75).
- `hasChannelMap` (cả hai gương đo) ⇒ Cài đặt mới bày hàng *Nguồn*. Một bên đo, một bên chưa = chưa.
- `CameraSignalPolicy.kt` 497 → 486 dòng: `usesChannel/channelFor/channelActive/frameNote` sang `CameraChannel.kt`
  làm **phần mở rộng** của object (call site `CameraSignalPolicy.x(...)` không đổi chữ).

### 2.3 Lùi CHANNEL → PANO (R3)

Máy trạng thái thuần **`CameraChannelFallback`** (`:core`): `onSessionStart(forcedPano)` · `onHalResult(channel,
channelRefused)` · `onFirstFrameBudget(channel, frames)` · `sourceFor(pref)`; `fellBack` chốt sau lượt đầu, chỉ mở lại
ở phiên do **xi-nhan** mở.

`CameraSignalController`: lượt dựng phiên tách thành **`openSession(turn, forcedPano)`** — dùng chung cho xi-nhan và
cho phép lùi (bộ uniform/crop bất biến theo phiên ⇒ phải dựng lại, không đổi giữa hai khung). Hai điểm hỏi máy:

1. Ngay sau `avm.open` (trong callback Surface): `AvmCamera.channelRefused` (mới — `true` khi xin kênh đơn 1..4 mà
   `rc != true`) ⇒ `REBUILD_PANO` ⇒ `fallbackToPano(turn, session, "rc=false")`.
2. Sau `FIRST_FRAME_BUDGET_MS = 3 000` ms (**[SUY]** từ B7: > 1,7 s ca chậm đã thấy, < một lượt rẽ 11 s; `HOLD_MS`
   1,2 s là việc khác): `framesOf(overlay.glStats())` = `0` ⇒ `"no-frame"`. Chuỗi rỗng (đường TV/SV không có bộ đếm)
   ⇒ `null` ⇒ **không** lùi theo số không có.

`fallbackToPano` **`post` về main** (chỗ gọi đầu nằm trong `onSurfaceTextureAvailable` của chính lớp video sắp bị dỡ —
tái nhập `hide()` là huỷ `SurfaceTexture` đang phát callback), kiểm `session == sessionId && current == turn` (hẹn giờ
của phiên cũ không lùi nhầm phiên mới), **một dòng `Log.w` "LÙI VỀ TOÀN CẢNH"**, ghi dấu
`Prefs.setCameraChannelFallback(reason)`, rồi `closeSession(keepPano = true)` → `openSession(turn, forcedPano = true)` —
đúng đường đổi bên 27/09 (HAL trước, cửa sổ sau). Phiên xi-nhan mới xoá dấu. Cài đặt: dưới hàng *Nguồn*, khi đang
chọn CHANNEL và dấu ≠ rỗng ⇒ ghi chú *"Lần xi-nhan gần nhất đã lùi về toàn cảnh (rc=false|no-frame)"*.

`camera_frame`: `CameraFrameShot.channel` (từ `Shown`, quyết lúc dựng) ⇒ lời đáp thêm `note = "anamorphic x4"`
(`CameraSignalPolicy.frameNote`, ×`CameraPanoCrop.STRIPS`). `CameraFrameShot` tách ra tệp riêng (pure move) để
controller ở 478/500.

---

## 3. Quyết định (kèm mức bằng chứng)

| # | Quyết định | Vì sao | Mức |
|---|---|---|---|
| Q1 | Nguồn PANO/CHANNEL ở tầng **người lái**, mặc định vẫn **PANO** | Owner hỏi trực tiếp ⇒ là lựa chọn owner nắm; đổi mặc định sang CHANNEL là đảo đường đang chạy để chữa thứ chưa đo hết (CLAUDE.md §6) — chốt tại 🚗 CAM-F1 | [ĐO B1] + luật §6 |
| Q2 | Nắn = **một ô tích** 100/0; 8 núm sau cổng kỹ thuật | Bộ số đã duyệt và nay là mặc định hồ sơ; người lái chỉ còn "có nắn hay không" | [ĐO B3] |
| Q3 | Cổng tầng kỹ thuật = `TestBridgeStore.isOn` | Cùng cổng 60 phút, bật bằng tay trong xe; không sinh cờ mới / khoá mới cho `SettingsCoverageContractTest` | luật spec R1 |
| Q4 | Xe chưa đo kênh ⇒ hàng *Nguồn* **vắng** (không mờ) | Chip *Một camera* trên xe chưa biết kênh nào là kênh nào là chip nói dối; và `channelActive=false` chặn ca `/4` sai | [SUY] từ B4/B8 |
| Q5 | Seal DL3 rot mặc định **0/0**; pref ↺90 owner đã đặt **giữ** | research §6.1 (18/20 hệ đứng, khung HAL đứng); pref thắng ⇒ xe owner không đổi một pixel tới khi owner tự đổi | [ĐO B5]; chốt 🚗 CAM-C2 |
| Q6 | NEUTRAL = đúng literal 2.75 (kể cả trái ↺/phải ↻ [ĐOÁN] cũ) | Đời xe chưa đo không đổi hành vi; bộ Seal không chép sang xe khác (CLAUDE.md §7, §6) | luật |
| Q7 | `detectSeed` "byd" ⇒ SEAL_DL3 ⇒ mọi BYD DL3 nhận bộ Seal | Cùng DiLink3 + XDJA (hồ sơ chiếu cụm đã dùng chung từ 07/2026); khác ống kính ⇒ owner xe đó chỉnh 8 núm sau cổng, giá trị đặt thắng | [SUY]; 🚗 xe thứ hai |
| Q8 | Ngân sách khung đầu 3 000 ms | B7; **chưa đo trực tiếp** ⇒ hằng có test khoá + 🚗 CAM-F2 siết | [SUY] |
| Q9 | Lùi **đúng một lần/phiên**, phiên PANO hỏng thật thì dừng | Không dựng phiên vô hạn trên xe đang lăn bánh | luật an toàn |
| Q10 | Dấu "đã lùi" là **pref** (`camera_channel_fallback`), không cờ RAM | Owner mở Cài đặt sau khi đỗ — phiên đã đóng; cờ chỉ để hiển thị (CLAUDE.md §5) | luật |
| Q11 | Thêm ô `SHAPE_CLUSTER = "CLUSTER"` vào `SHAPES` (cuối) | L2 đã tham chiếu `CameraSignalPolicy.SHAPE_CLUSTER` (`CameraClusterBand.kt:31`); trên màn chính thoái về RECT | phối hợp L2 |

---

## 4. Test (off-car, JUnit; `bash …/gradle-locked.sh :core:test :app:testDebugUnitTest --tests …`)

| Bài | Ghim gì | Số |
|---|---|---|
| `core CameraProfileDefaultsTest` | NEUTRAL = từng literal 2.75; NEUTRAL → uniform = base; `sane()` sửa từng trường, không kẹp; `isChannel` đóng miền 1..4 | 4 |
| `core CameraChannelFallbackTest` | **AC R3**: rc=false ⇒ đúng 1 `REBUILD_PANO`, phiên PANO không lùi lần 2 dù HAL vẫn nói không / 0 khung; phiên xi-nhan mới đặt lại; `frames=null` không lùi; `framesOf` đọc chuỗi stats thật; ngân sách 3 000 ms | 5 |
| `core CameraSettingsIaTest` | **AC R1**: `USER_KEYS` đúng **11** khoá (danh sách đủ, có thứ tự); tech 16; rời nhau; hợp = đúng tập `camera_*` của danh sách trắng | 4 |
| `core CameraChannelSourceTest` (sửa) | `channelFor(profileChannel)`, `channelActive`, kênh không còn trên `CamView`, `frameNote`; 16 tổ hợp xoay đi qua cả 3 hình | 11 |
| `core CameraSignalPolicyTest` · `CameraGlUniformsTest` (sửa) | `SHAPES = RECT·ROUND·CLUSTER`; đếm tổ hợp theo `SHAPES.size` | 16 · 18 |
| `app ClusterProfileCameraDefaultsTest` | **AC R2**: Seal DL3 = bộ đo B3/B4/B5 → uniform F=0,55·base, K=base, S=1,30·base, rot 0; DL5/generic/id lạ/đời giả = NEUTRAL, không bản đồ kênh; camera không vào export, round-trip theo id | 4 |
| `app CameraSettingsIaWiringContractTest` | mỗi khoá user có hàng trong `cameraUser`, tech trong `cameraTech`; cổng `if (TestBridgeStore.isOn(context)) cameraAdvanced(body)` đúng 1 call site; hàng Nguồn theo `cameraChannelSupported` + dấu; `SettingsSectionsCar` chỉ uỷ quyền; 8 chuỗi mới VI+EN; bỏ nhãn "(mặc định)" | 5 |
| `app CameraChannelFallbackWiringContractTest` | `AvmCamera.channelRefused` đặt lại mỗi `open`; controller hỏi máy đúng 2 chỗ; `fallbackToPano` post/kiểm phiên/log/dấu/`closeSession` trước `openSession(forcedPano=true)` (1 call site); `stop()` huỷ hẹn; `camera_frame` note | 3 |
| Sửa 4 bài canh cũ (`CameraSpanShape` 9 · `CameraGl` 17 · `CameraFrameAndRender` 9 · `CameraRotation` 5) | trỏ sang `SettingsSectionsCamera.kt`, `openSession`, `CameraDefaults.of(ctx).*`, `profileChannel` | 40 |

Kết quả: **`:core:test` 2 946 · 0 fail** (302 lớp) · `:app` targeted (`*Camera* *Settings* *ClusterProfile* *Layering*
*TestBridge* *TypeScale* *Goi2* *I18n* *ClusterNav*) **237 · 1 fail** — bài đỏ duy nhất `LauncherI18nContractTest.0 chuoi
tieng Viet viet cung` do **2 literal trong `CameraOverlayView.kt` của làn L2** (`"dải=…"`, `"vùng=…"`), không thuộc làn này
(xem §6). Trần 500 dòng: `CameraSignalController` 478 · `CameraSignalPolicy` 486 · mọi tệp mới < 260.

---

## 5. 🚗 Kiểm trên xe (mỗi dòng một phép, ≤ 2 phút)

- **CAM-F1** — Bật chế độ kiểm thử; *Cài đặt › Tiện nghi xe*: **11 hàng** camera (gồm **2 ô *Lật gương* của L7**; 10 nếu hồ sơ xe chưa có bản đồ kênh ⇒ hàng *Nguồn* vắng) + khối *Nâng cao (kỹ thuật) · 16 mục*; tắt chế độ kiểm thử, mở lại ⇒ khối biến mất, `prefs_set --es key camera_dewarp_k --es text 105` vẫn `ok` + `read_back 105`.
- **CAM-F2** — `prefs_set camera_source CHANNEL`, xi-nhan trái với `camera_render=GL` ⇒ log `KachiCamera … nguồn=CHANNEL halMode=2`, có hình trọn vòng; ghi `logcat` mốc `addPreviewSurface … rc=true` → dòng `GL uTexMatrix khung đầu` để **đo khung đầu thật** (siết `FIRST_FRAME_BUDGET_MS` 3 000 nếu < 1 s).
- **CAM-F3** — `prefs_set camera_hal_mode 4` + CHANNEL (kênh sau, HAL có thể từ chối/không hình) ⇒ trong ≤ 3 s thấy **1** dòng `LÙI VỀ TOÀN CẢNH (rc=false|no-frame)`, hình khung ghép lên, **không** dòng thứ hai; Cài đặt hàng Nguồn có ghi chú *"đã lùi…"*; `prefs_set camera_hal_mode -1`.
- **CAM-F4** — `camera_frame` khi CHANNEL ⇒ lời đáp có `"note":"anamorphic x4"`; khi PANO ⇒ `"note":""`.
- **CAM-C2** (research §6) — `prefs_set camera_rot_left 0` + `camera_rot_right 0`, đứng cạnh cột đèn: cột **thẳng đứng**, thân xe góc dưới-trong ⇒ owner chốt giữ 0 hay đặt lại ↺90 (pref thắng, không tự đổi).
- **CAM-F5** — Xe **không** chạm pref span/render từ 2.74 (nếu có): `read_back camera_span`/`camera_render` phải trả `STRIP`/`GL` (mặc định hồ sơ Seal) — nếu owner đã `prefs_set` thì trả đúng giá trị đã đặt.

---

## 6. Nợ còn lại

- **[CHƯA BIẾT] khung đầu CHANNEL** — `FIRST_FRAME_BUDGET_MS` là [SUY] (§3 Q8); đo ở CAM-F2 rồi siết.
- **Ngân sách khung đầu chỉ đo được ở đường GL** (`frames=N`); TV/SV không có bộ đếm ⇒ chỉ nhánh `rc=false` lùi được. Mở bộ đếm cho TV cần chạm `CameraVideoLayer.onSurfaceTextureUpdated` (hợp đồng "trống" của bài `CameraFrameAndRenderWiringContractTest`) ⇒ để owner quyết.
- **`camera_mirror`** — công tắc ĐÃ ship ở 2.76 (L7: hai khoá `camera_mirror_left/right` trong `USER_KEYS`, mặc định TẮT). Còn ngỏ là **giá trị mặc định** cho hồ sơ Seal: chờ 🚗 CAM-M1 đo tay gương thật. (Bản trước của dòng này viết *"ngoài phạm vi 2.76"* — sai kể từ khi L7 vào cùng phiên, [P2] soát Opus 27/09.)
- **Đời xe thứ hai**: DL5/generic đi NEUTRAL; khi có số đo, thêm `camera = CameraProfileDefaults(...)` vào seed tương ứng + `cameraFor(id)`; `detectSeed` hiện gộp mọi BYD DL3 vào bộ Seal (§3 Q7).
- **Cross-lane (L2)**: `CameraOverlayView.kt` có 2 literal tiếng Việt trong chuỗi log (`"dải=…"`, `"vùng=…"`) làm `LauncherI18nContractTest` đỏ — thêm vào `allowed` kèm lý do "nhật ký" hoặc chuyển sang ASCII.
- `HUONG-DAN`/README/backlog/spec là của điều phối — dòng đề xuất ở phần trả về của làn.

---

## 7. Sau soát Opus 2026-09-27 (Pass 1) — ba bản vá của R3 + số hàng người lái
R3 sinh ra để đóng *ca sai im lặng* của 2.75; soát chéo tìm ra đúng ba lỗ nó chưa bịt:

1. **[P1] `channelRefused` im khi ROM không có `addPreviewSurface(Surface,int)`** — nhánh đo bị bỏ (`add == null`), hàm rơi về
   đường một-tham-số (khung **GHÉP**) mà cờ vẫn `false` ⇒ máy trạng thái nhận *"HAL đồng ý"*, và ngân sách khung đầu cũng
   không cứu (khung ghép CÓ khung). Vá: gán thêm **SAU** nhánh một-tham-số + `Log.w` (`AvmCamera.kt`), khoá bằng thứ tự dòng
   trong `CameraChannelFallbackWiringContractTest`. [ĐO] xe owner đi nhánh hai-tham-số nên đây là lưới cho trim/ROM khác —
   hình dạng ấy là **[ĐOÁN]**, chưa có dump nào.
2. **[P1] đường GL tự rơi về `TextureView` để lại `frames=0` vĩnh viễn** — `CameraGlRenderer.stats()` là bản in trường thuần,
   `start()` trả `null` thì luồng vẽ chưa từng chạy mà chuỗi vẫn `frames=0 …` ⇒ sau 3 s máy lùi một phiên **ĐANG CÓ HÌNH**
   (đường rơi là đúng đường TV 2.73) và in dấu `no-frame` trong khi bệnh thật là EGL. Đúng thứ §2.3 cấm: *lùi theo một số
   liệu không có*. Vá: `glStats()` trả `glFellBack=1` — không có chữ `frames=` ⇒ `framesOf` = `null` = *không đếm được*
   (cùng sự thật với đường TV/SV), và **không** trả rỗng để `camera_frame` còn phân biệt *EGL rơi* với *đường TV*.
3. **[P2] phiên "đã lùi" vẫn truyền `camera_hal_mode` đã ghim** — `channelFor` trả thẳng pref khi nguồn là PANO, nên ở nhánh
   `no-frame` (HAL **nhận** kênh mà không đẩy khung) phiên mới xin lại **đúng kênh vừa chết**, lại không có khung, mà
   `fellBack` đã chốt ⇒ hết đường phục hồi: ô gương đen suốt lượt rẽ trong khi log nói *"LÙI VỀ TOÀN CẢNH"* (CLAUDE.md §2 —
   một dòng log nói đã chữa trong khi chưa chữa gì). Vá: `forcedPano ⇒ HAL_MODE_AUTO`, tức 🚗 **CAM-F3** nay mới ra được
   *"hình khung ghép lên"* như §5 hứa cho **cả hai** nguyên nhân, không chỉ `rc=false`.

**Số hàng người lái 9 → 11** (L7 thêm hai khoá *Lật gương*): §2.1, §5 CAM-F1, runbook `oncar-runbook-2.76.md`, KDoc
`SettingsCameraSection` và KDoc `CameraSettingsIaTest` đều đã sửa; tổng khoá `camera_*` 25 → **27**. CAM-F1 là một phép **ĐẾM
HÀNG** owner làm trên xe trong 2 phút: số sai ở đó không chỉ là doc lệch, nó làm phép kiểm mất hiệu lực (đếm ra 11 mà runbook
nói 9 ⇒ không biết là lỗi hay là đúng).
