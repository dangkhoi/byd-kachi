# Camera 2.76 — IA hai tầng · mặc định theo hồ sơ xe · lùi CHANNEL → PANO (làn L1, off-car 2026-09-27)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-29 (ghi chú RÚT vạch chuẩn ở §10) · trước đó 2026-09-27 · **Mục đích**: đóng ba yêu cầu R1/R2/R3 của spec
> `docs/specs/kachi-276-closing.html` sau buổi xe 27/09 — (1) màn *Tiện nghi xe › Camera* chỉ còn thứ người lái cần,
> móc đo ẩn sau cổng chế độ kiểm thử; (2) mặc định camera đi theo **hồ sơ xe** (`ClusterProfile`), bộ Seal DL3 là
> bộ owner đã duyệt trên xe, bỏ hằng kênh ghim trên `CamView` ([P3] review Pass 2); (3) nguồn *Một camera* mà đầu máy
> không lên thì **lùi về toàn cảnh đúng một lần**, có log, có dấu trong Cài đặt; `camera_frame` mang ghi chú
> `anamorphic x4`. Liên quan: `../research-side-camera-orientation-2026-09-27.md` §6 · `../offcar-2026-09-26/camera-dewarp-gl.md`
> · backlog dòng `ONCAR-2026-09-27` + `2.75 (176)`.
>
> ⚠ **ĐỌC §8 TRƯỚC §2/§5.** Buổi xe **chiều 27/09** (bản 2.76 (177) trên xe) đã **gỡ hẳn** hai thứ mà §2.1/§2.3 mô tả:
> khối *Nâng cao (kỹ thuật)* và **toàn bộ** nguồn *Một camera* (`camera_source` · `camera_hal_mode` · máy lùi
> CHANNEL → PANO). §1–§7 giữ nguyên làm **lịch sử của 2.76** (append-only, cùng lối §Reviewer Log); §8 là trạng thái
> **hiện tại** của 2.77 và là chỗ giữ **bản đồ kênh camera** như một số đo.

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

---

## 8. 2.77 (2026-09-27, chiều — sau buổi xe closing): GỠ tầng *Nâng cao* + GỠ hẳn nguồn *Một camera*

> Đây là **trạng thái hiện tại**. §2.1 (hai tầng) và §2.3 (lùi CHANNEL → PANO) là lịch sử của 2.76 — không còn mã nào
> thi hành chúng.

### 8.1 Owner nói gì (nguyên văn, trên xe 2026-09-27 chiều)

- *"bỏ cái 1 cam ra, nhiều option quá rối cho người dùng, bỏ luôn ở phần kỹ thuật"*
- *"không biết chỉnh đâu, nên chốt theo cái nào best là được, bỏ hết phần nâng cao đi, bỏ luôn nguồn vì chốt là toàn
  cảnh khung ghép rồi"*

### 8.2 Phép ĐO khép lại câu hỏi *"một camera có nét hơn không"* — KHÔNG

| # | Số đo | Mức | Nguồn |
|---|---|---|---|
| M1 | Cùng cỡ cảnh, **hai khung thô** chụp cùng hiện trường: **năng lượng cạnh 686 (dải ghép) vs 351 (một kênh)** | [ĐO] | buổi xe 27/09 chiều, backlog `ONCAR-2026-09-27 CHIỀU` |
| M2 | Tỉ lệ chi tiết **ngang/dọc 0,30 (ghép) vs 0,19 (một kênh)** ⇒ khung một kênh bị **KÉO NGANG** nhiều hơn từ **cùng** dữ liệu cảm biến | [ĐO] | cùng nguồn |
| M3 | Cảm giác *"rõ hơn"* của owner đến từ **khung rộng hơn + ảnh mượt do phóng to**, không từ điểm ảnh thật | [SUY từ M1+M2] | — |
| M4 | Vào số **R** khi Kachi đang giữ một kênh ⇒ camera lùi zin **chạy bình thường**, không xung đột | [ĐO] | cùng nguồn (giữ lại làm kiến thức, không còn đường code) |

⇒ Nguồn một-kênh **không mang thêm thông tin**, chỉ thêm một lựa chọn cho người lái phải hiểu. Bỏ.

### 8.3 BẢN ĐỒ KÊNH CAMERA — số đo được GIỮ LẠI làm kiến thức (không còn mã nào đọc)

**[ĐO 27/09, owner xác nhận từng kênh]** `AVMCamera.addPreviewSurface(surface, n)`:

| `n` | Hướng |
|---|---|
| 1 | **sau** |
| 2 | **trái** |
| 3 | **phải** |
| 4 | **trước** |

Tức hồ sơ Seal của 2.76 gán *trái = 2 · phải = 3* là **ĐÚNG** (§1 B4 chỉ [ĐO] được hai kênh giữa; hai kênh còn lại từng
là [SUY], nay là [ĐO]). Đây là chỗ **duy nhất** còn giữ bảng này: `CameraProfileDefaults.channelLeft/channelRight`,
`CameraSignalPolicy.HAL_MODE_*` và tham số `halMode` của `AvmCamera.open` đều đã xoá. Muốn dựng lại nguồn một-kênh thì
phải quay lại đây đọc bảng — và đọc luôn §8.2 để biết vì sao nó bị bỏ.

### 8.4 Gỡ những gì (mã)

| Thứ | Trước (2.76) | Sau (2.77) |
|---|---|---|
| Hàng trên màn *Tiện nghi xe › Camera* | 11 (10 + *Nguồn* có điều kiện) + khối gập **16 mục** sau cổng chế độ kiểm thử | **10**, một tầng, không cổng |
| `camera_source` · `camera_hal_mode` | pref + hàng chip + `prefs_set` | **XOÁ hẳn** (cả danh sách trắng) |
| Danh sách trắng `prefs_set` | 42 khoá | **40** — lần đầu danh sách này **co lại** |
| Khoá `camera_*` | 27 (11 user + 16 tech) | **25** (10 `USER_KEYS` + 15 `NO_UI_KEYS`) |
| Tệp xoá | — | `core/…/camera/CameraChannel.kt` · `app/…/PrefsCameraChannel.kt` · `app/…/launcher/ClusterNavBridgeCamera.kt` |
| Bài test xoá | — | `CameraChannelSourceTest` · `CameraChannelFallbackTest` · `CameraChannelFallbackWiringContractTest` |
| `CameraPanoCrop.contentWidth` · tham số `channel` của `cropFor`/`sourceCentre`/`cameraGlUniforms` · `CameraFrameShot.channel` · `"note":"anamorphic x4"` | có | **gỡ** (một tham số luôn `false` là mã chết) |
| `AvmCamera.open` | `(cameraId, surface, halMode)` + nhánh đo đọc `rc` + `channelRefused` | `(cameraId, surface)` — **chỉ** vòng dò `0..3` của 2.73 |
| 38 chuỗi VI + 38 chuỗi EN của khối gập / hàng *Nguồn* / 16 hàng đo | có | **xoá** (i18n mồ côi) |

### 8.5 15 khoá KHÔNG có UI — ẩn hàng, **không** xoá khoá

`camera_render` · `camera_span` · `camera_strip_left/right` · `camera_circle_scale` · `camera_cam_left/right` ·
`camera_gl_texmatrix` · tám núm `camera_dewarp_{cx,cy,k,focal,scale,pan_x,pan_y}` — vẫn ghi/đọc qua `prefs_set` của cầu
kiểm thử, giá trị đã đặt trên xe **vẫn có hiệu lực**, mặc định là bộ hồ sơ xe (Seal: `STRIP · GL · F 55 · K 100 ·
S 130 · tâm 0,0 · dịch 0,0`, dải trái 1 / phải 2, cameraId 1).

Vì sao giữ: đó là **đường chẩn đoán** CLAUDE.md §15 bước 2/3 (đọc/ghi state bền rẻ hơn mò UI hàng chục lần), và là bộ
số owner đã dò **ba buổi xe**. Vì sao vẫn gỡ UI: owner *"không biết chỉnh đâu"* ⇒ một hàng người lái không dùng được
là một hàng gây rối, kể cả khi nó nằm sau một cổng. Xoá khoá thì mất cả hai thứ trên.

**[ĐO B0 27/09 chiều]** prefs camera của owner (F55/K100/S130/GL/STRIP, xoay ↺90) **giữ nguyên qua nâng cấp** ⇒ luật
*pref thắng mặc định hồ sơ* đã chứng minh trên xe ⇒ gỡ UI không đổi một pixel nào trên xe của owner.

### 8.6 Hợp đồng test sau 2.77

- `CameraSettingsIaTest` (`:core`, 4 bài) — `USER_KEYS` = **đúng 10 khoá đúng thứ tự** · `NO_UI_KEYS` = 15 · hai danh
  sách rời nhau và hợp lại = **đúng 25** khoá `camera_*` của danh sách trắng · `camera_source`/`camera_hal_mode`
  **không** ở danh sách nào **và không** ghi được nữa.
- `CameraSettingsIaWiringContractTest` (`:app`, 3 bài) — canh **sự VẮNG MẶT**: mọi mảnh của khối gập / hàng *Nguồn* /
  15 getter không-UI phải không có trong `SettingsSectionsCamera.kt`; `build()` chỉ còn **một** lượt dựng; tệp
  `ClusterNavBridgeCamera.kt` không tồn tại; 38 chuỗi đã xoá khỏi **cả hai** tệp chữ.
- `CameraSpanShapeWiringContractTest` · `CameraGlWiringContractTest` · `CameraFrameAndRenderWiringContractTest` — đổi
  từ *"hàng này phải có"* sang *"hàng này phải VẮNG, mà `prefs_set` phải CÒN"*.
- `TestBridgeCommandTest` — `WRITABLE_PREFS_KEYS.size` **42 → 40**, kèm hai `assertTrue(… !in …)` cho hai khoá đã xoá.

### 8.7 🚗 Kiểm trên xe (thay CAM-F1…F4 của §5)

- **CAM-G1** — *Cài đặt › Tiện nghi xe › Camera*: **đếm đúng 10 hàng**, **không** có khối *Nâng cao (kỹ thuật)*, **không**
  có hàng *Nguồn*. Bật chế độ kiểm thử rồi mở lại: vẫn **đúng 10 hàng** (không còn cổng nào để mở thêm).
- **CAM-G2** — `prefs_set --es key camera_dewarp_k --es text 105` ⇒ `ok` + `read_back 105`; `prefs_set camera_render TV`
  ⇒ `ok`, lượt xi-nhan sau vẽ bằng TextureView ⇒ **đường chẩn đoán còn sống dù không còn hàng**.
- **CAM-G3** — `prefs_set --es key camera_source --es text CHANNEL` ⇒ phải **lỗi** `bad_prefs_key` (không còn ghi được);
  cùng vậy với `camera_hal_mode`.
- **CAM-G4** — xi-nhan trái/phải: log `KachiCamera` **không còn** `halMode=`/`nguồn=`; ảnh vẫn lên đúng như 2.76
  (khung ghép, cắt dải, GL) ⇒ gỡ nguồn không đổi hình.
- **CAM-G5** — `read_back camera_span`/`camera_render`/`camera_dewarp_focal` trả đúng giá trị owner đã đặt trên xe
  (STRIP / GL / 55) ⇒ nâng cấp không reset bộ số.

### 8.8 Nợ mở ra từ lượt gỡ này

- ~~`docs/catalog/features.json` + `docs/kachi-feature-catalog.html` còn mục *"Nguồn «một camera» … LÙI VỀ TOÀN CẢNH
  (2.76 R3)"* và các mục của tầng kỹ thuật~~ ⇒ **ĐÃ ĐÓNG ở lượt gộp 2.77 (điều phối)**: mục R3 **xoá** (tính năng
  không còn), mục IA viết lại thành *"MỘT tầng — đúng 10 hàng"*, mục hồ sơ bỏ `kênh 2/3` khỏi bộ số và mục *theo cụm*
  nhận thêm phần mép cong ⇒ `features.json` **165 → 164** mục, `kachi-feature-catalog.html` dựng lại
  (`SOURCE_DATE_EPOCH` ghim 2026-09-27 để không sinh diff giả). Grep `LÙI VỀ TOÀN CẢNH` trong HTML = **0**.
- Việc 2.77 còn lại của camera (ngoài phạm vi làn này): **mặt nạ bám đường cong của cụm** (§ONCAR-2026-09-27 CHIỀU
  CAM-CL2 — hình *CHỮ NHẬT* trên cụm co thành ô vuông nhỏ, owner *"bé tý… không hề theo hình cụm"*), và `setPreviewSize`
  vì **ĐỘ NÉT** (không vì jank — CAM-B6 đã đóng nhánh jank: 11,15 % → 0,81 % nhờ trần 15 fps).
- 🚗 **G7** (dấu núm dịch khung) **không còn đo được qua UI** — núm đã gỡ; đo qua `prefs_set camera_dewarp_pan_x` nếu
  owner còn muốn chốt dấu.

---

## 9. 2.81 (2026-09-28): người lái tự CHỌN camera + dải hình — 10 → 14 hàng

### 9.1 Vì sao mở ra cho người lái chọn

Owner trên **Sealion 6** (khác đời với Seal đã đo): *"trên sealion 6 không mở được cam phải (cam trái ok - khi xinhan
ấy)"*. Mỗi góc nhìn mang **hai** số — `CamView.outputState` (bảo HAL xuất hình nào) và `CamView.cameraId` (mở camera
nào) — và cặp đúng **khác nhau theo đời xe**: [ĐO từ ảnh owner] khung ghép fisheye là `id 1` trên Seal nhưng `id 0`
trên SL6. Theo CLAUDE.md §7, khác biệt đời xe **không được** rải `if` trong mã; hoặc vào `ClusterProfile`, hoặc để
người lái tự dò. Chưa đo đủ để đặt vào hồ sơ xe ⇒ **mở cho người lái dò**.

### 9.2 Bốn hàng mới

| Khoá | Chip | Ghi chú |
|---|---|---|
| `camera_view_left` / `camera_view_right` | **1 … 8** | Số là **vị trí trong `CameraSignalPolicy.VIEWS_ALL`** (1-based), **cố ý KHÔNG đặt tên**. Owner: *"các label mình để nó cũng ko chuẩn đâu… vì mình cũng đâu có biết là nó cam nào đâu mà phán cho người ta"*. |
| `camera_pano_left` / `camera_pano_right` | Tự động · Nguyên khung · **1 … 4** | Sáu góc **không phải** Gương trước đây không được cắt dải ⇒ chọn được camera mà hình vẫn ra nguyên khung ghép. |

Bấm chip là **đóng-mở lại phiên camera** ⇒ hình bật lên ngay để xem thử (dò mà không thấy hình thì dò kiểu gì).

### 9.3 Lỗi thật đã sửa cùng lượt

`CameraPanoCrop.cropFor` trước đây chỉ trả **vùng cắt**. Sửa lẻ phần cắt thì hình vừa **bẹp** (thiếu tỉ lệ nguồn) vừa
**cong lệch** (tâm quang rơi ra mép dải) ⇒ nay trả **cỡ nguồn + tâm quang + vùng cắt cùng một lần**. Hai bẫy nữa một
lượt soát đối kháng bắt được trước khi ship: (a) góc **Gương** đã mang `crop` dựng sẵn ⇒ phải **bỏ qua** dải truyền
vào ở **mọi** chỗ dùng, không chỉ chỗ chọn rect; (b) `CameraGlUniforms.sourceCentre` phải khoá theo **crop đang thật
sự dùng**, không theo `view.crop`.

### 9.4 🚗 Kiểm trên xe

- **CAM-SL6-1** — trên SL6, hàng *thử camera số* bên phải: bấm lần lượt 1→8, ít nhất một số ra hình. ✅ **[ĐO 28/09]**
  owner xác nhận *"đã test trên xe SL6 vụ camera, OK ngon lành"*; giá trị đúng = **camera 3 cả hai bên, dải Tự động**.
- **CAM-SL6-2** — giá trị ấy **chưa** vào `ClusterProfile` vì chưa có chuỗi `getprop` nhận dạng SL6 ⇒ backlog
  `CAM-SL6-PROFILE`. Đoán chuỗi model là trái CLAUDE.md §14.

---

## 10. 2.82 (2026-09-28, cùng ngày): vạch chuẩn khoảng cách — 14 → **16 hàng**

> **⚠ RÚT 29/09 — owner dẹp vạch trên xe.** Nguyên văn: *"… mớ vạch vẽ ra cho vui vậy chứ có ý nghĩa gì với đời đâu
> hả?"* (12:00:47) rồi *"ò, dẹp vạch đi"* (12:02:39). Đo trên xe 29/09 (2.82 cài tay): GUIDE-1 **đạt** hai bên (nấc n
> nằm đúng n/10 chiều cao khung, lệch ≤ 1 px) [ĐO ảnh]; GUIDE-2: với hình **xoay 90°** (mặc định Seal) khoảng cách ra
> xe bên cạnh chạy **ngang** khung ⇒ vạch ngang **sai trục** [ĐO ảnh + SUY hình học]; hình không xoay thì đúng trục,
> owner canh bên phải nấc 7 ≈ mép trong vạch vàng ~20 cm, bên trái giữa 7–8 [ĐO owner]. 2.82 **không đăng OTA** ⇒ vạch
> chưa từng lên kênh; mã gỡ ở **2.83** (việc D). Khi gỡ: `USER_KEYS` **16 → 14**, tổng khoá camera ghi được qua
> `prefs_set` **31 → 29**, danh sách trắng cầu kiểm thử **46 → 44**; ba bài canh phải đổi theo (`CameraSettingsIaTest`,
> `CameraSettingsIaWiringContractTest`, `TestBridgeCommandTest`) + dòng CAM-F1 của runbook. Mã gỡ 2.83 đã nằm trong cây làm
> việc (29/09, chưa commit) với đúng 14/29/44 [ĐO test `CameraSettingsIaTest`, `TestBridgeCommandTest`]; `HEAD` (2.82) còn 16/31/46 cho tới commit đó. Bản phát hành (2.81) có **14** hàng.
> Nội dung §10 dưới đây giữ nguyên làm lịch sử. Chi tiết: `docs/specs/kachi-camera-distance-guide.html` (trạng thái RÚT)
> và `docs/diagnostics/oncar-2026-09-29-findings.md`. Cùng buổi, AVM 360 **đóng hẳn** [ĐO]: `pano_sdk.txt` bị cấm với uid
> shell, không có `panorama_online` ở settings chuẩn lẫn `content://carsettings/global`.

### 10.1 Triệu chứng và nguyên nhân

Owner trên xe: *"camera nó tạo cảm giác xe mình rất xa xe bên cạnh, trong khi cách tầm 30cm thôi, nên khó phán đoán"*.
Nguyên nhân là **hình học**, không phải lỗi mã: ống mắt cá nén mạnh nhất đúng vùng 0–1 m ⇒ 30 cm và 1,5 m chiếm gần
như cùng số điểm ảnh theo chiều dọc.

### 10.2 Ba đường "làm hình thật hơn" — đều ĐÓNG, có bằng chứng

| Đường | Kết quả | Bằng chứng |
|---|---|---|
| Mượn hình chim-bay (AVM) của xe | **ĐÓNG** | [ĐO] owner mò hết 8 chế độ: *"mò từ 1 đến 8 ko có cái nào là xe ghép sẵn cả"*. HAL `android.hardware.AVMCamera` chỉ trả **4 dải mắt cá thô** 5120×960; phần ghép nằm trong app AVM của hãng. |
| Ghép cam trước + cam sau | **ĐÓNG** | [ĐO] HAL cấp **một** surface một lúc cho tiến trình này; bốn dải là bốn **tâm quang khác nhau** ⇒ ghép đúng cần homography theo từng xe + bù cao độ = dựng lại chính AVM của hãng. |
| Cắt hẹp vùng gần (zoom 1/3 dưới) | **ĐÓNG** — thử thật rồi gỡ | [ĐO] owner: *"nhìn kỳ lắm, trả lại đi"*. Mất ngữ cảnh hai đầu (không còn thấy thân xe làm mốc) ⇒ **khó** đọc hơn dù tỉ lệ nén đỡ hơn. |

⇒ Cả ba đều nhằm **sửa cái hình**, mà cái hình bị hình học ống kính ràng. Đường còn lại: **đổi thứ người lái đọc**.

### 10.3 Hai hàng mới

| Khoá | Chip | Mặc định |
|---|---|---|
| `camera_guide_left` / `camera_guide_right` | **Tắt** · 1 … 9 | **Tắt** — máy chưa ai canh thì vẽ sẵn một vạch chưa canh còn tệ hơn không vẽ, vì người lái sẽ tin nó |

Nấc `n` ⇒ vạch ở `n/10` chiều cao khung (0,1…0,9 — không nấc nào dính mép). Vạch **trắng có viền đen** (mẹo phụ đề):
vạch trơn biến mất trên nền sáng đúng lúc cần nhất. Nằm **trên** video, **dưới** nhãn *"Camera trái/phải"*, và
**không ăn chạm**.

**Cố ý KHÔNG** in số mét cạnh vạch: quy đổi nấc→mét phụ thuộc chiều cao gắn camera, góc chúc, đời xe — **chưa đo cái
nào** (CLAUDE.md §2). Spec đầy đủ: `docs/specs/kachi-camera-distance-guide.html`.

### 10.4 Số hàng người lái: 10 → 14 → **16**

`CameraSettingsIa.USER_KEYS.size == 16`, tổng khoá camera ghi được qua `prefs_set` = **31** (16 hàng + 15 khoá không-UI).
Ba bài canh số đếm: `CameraSettingsIaTest` (`:core`), `CameraSettingsIaWiringContractTest` (`:app`),
`TestBridgeCommandTest` (danh sách trắng **46**). Đổi danh sách ⇒ phải đổi **cả ba** + mục này + dòng CAM-F1 của runbook.
