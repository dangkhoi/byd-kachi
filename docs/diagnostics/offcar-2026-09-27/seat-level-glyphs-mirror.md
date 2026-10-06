# L7 — Mức ghế nằm trong HÌNH · Lật gương camera · Nối dải cụm vào hồ sơ (2.76)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 (chiều) · **Mục đích**: ghi lại làn cuối của 2.76 — yêu cầu chót của owner trước khi đi công tác (*"icon kiểu 1 bông tuyết, 2 bông tuyết, 1 sưởi, 2 sưởi trên icon luôn mà không cần số 1-2"*), cộng tuỳ chọn **lật gương** sinh ra từ nghiên cứu hướng ảnh 27/09, và nối `ClusterBandSpec` vào hồ sơ xe (yêu cầu chéo L2→L1 còn thiếu). Nghiên cứu nền: `research-side-camera-orientation-2026-09-27.md`.

## 1. Mức ghế nằm trong hình

**Bối cảnh** [ĐO xe 09-17, memory `kachi-car-facts-0917`]: ghế Seal có đúng **ba** trạng thái (TẮT · 1 · 2; mã HAL 1/2/3). Trước 2.76 chip và ô nút đọc *"Ghế lái · 2"* — một con số cạnh một hình ghế không nói gì.

**Cơ chế** (không phải nhánh theo mã — CLAUDE.md §7): `CapabilityIcons.LEVEL` là **bảng khai** `hình khái niệm → [hình mức 1, hình mức 2]`, tra bằng `forLevel(icon, level)`. Bốn họ khai hôm nay: `ic-seat-{heat,vent}-{left,right}`. Hình **mức 2 chính là hình khái niệm cũ** (hai dấu — đã có từ UX5), hình **mức 1** là tệp mới `_1` (một dấu).

- Nguồn: `design/glyph/seat_{heat,vent}_{left,right}[_1].svg` → `scripts/design/gen-icons.py` → `res/drawable/ic_*.xml`. Không sửa tay tệp sinh.
- Ba bề mặt cùng một cửa tra, nên **không bao giờ nói ba mức khác nhau**: chip (`TopStripChips`, 2 chỗ), ô nút (`KachiIcons.byLevel` ← `ControlTileFactory`), ô đọc (`DatumIconView`).
- Chip: có hình cho mức ⇒ **bỏ con số**, chữ chỉ còn nhãn ghế. Mức **ngoài bảng** (vd nếu đời xe khác có nấc 3) ⇒ `forLevel` trả `null` ⇒ **số quay lại** — không vẽ bừa một mức chưa đo.
- Ghế lái giữ **chấm vô-lăng** (L4) nên vẫn phân biệt được với ghế phụ khi hai chip đứng cạnh nhau.
- `KachiIcons.byLevel` chỉ gọi `setImageResource` **khi id đổi thật** (ghi vào `tag`): ô nút được vẽ lại theo nhịp trạng thái xe 1 Hz, mà `setImageResource` luôn giải mã lại drawable.

**[ĐO mắt, ảnh dựng từ chính tệp sinh]** `*-{24,48}-dark.png` (48 tệp của bộ `icons-276-seat`, ngoài repo; nền tối như chip thật): ở **24 px** một làn nhiệt ↔ hai làn, một bông tuyết ↔ hai bông tuyết phân biệt được ngay, không cần phóng to. Ảnh dán cạnh nhau: `icons-276-seat/sheet2.png`.

## 2. Lật gương (`camera_mirror_left/right`)

**Vì sao có**: nghiên cứu 27/09 §6.2 — mọi hệ trong ngành nhìn rõ đều theo **tay gương kính** (thân xe ở mép trong), nhưng **tay của ảnh HAL trên xe này là [CHƯA BIẾT]**: Kinex mặc định không lật, nên hoặc HAL đã lật sẵn, hoặc Kinex sai chiều. Không đoán ⇒ thêm công tắc, **mặc định TẮT**, và một phép đo trên xe (🚗 CAM-M1) để chốt.

**Đặt ở đâu trong chuỗi biến hình**: **sau crop, trước xoay** — `rot(mirror(src))`. Đây là lật **ảnh nguồn**, tức sửa *"ảnh HAL ngược tay"* đúng chỗ nó sai.
- TV: `CameraOverlayTransform.matrix(..., mirror)` nhân thêm `scale(−1,1)` quanh tâm khung ở **bước 1b**. **Không** dùng `View.scaleX = −1`: lật sau xoay là `mirror(rot(src))`, mà với ±90 thì `rot∘mirror = mirror∘rot⁻¹` ⇒ hai đường cho hai ảnh khác nhau; ngoài ra `scaleX` không test được off-car.
- GL: đảo dấu bề ngang của `uSrcRect` (đọc lại bằng `CameraGlUniforms.mirror`), nên lật **hợp với xoay/pan** đúng như một camera lắp ngược.
- `describe()` in giá trị hiệu dụng ⇒ log tự đủ nghĩa.
- Khoá ở **tầng người lái** (`CameraSettingsIa.USER_KEYS`, hai hàng *Lật gương trái/phải*), `prefs_set` + `read_back` qua cầu kiểm thử.

## 3. Nối dải cụm vào hồ sơ xe
`ClusterProfile.band = ClusterBandSpec.SEAL_DL3` (ngoài `export()/parse()`), đọc qua `CameraDefaults.band(ctx)`, `CameraSignalController` truyền vào `overlay.show(band = …)`. Trước đó dải giữa là hằng của `:core` ⇒ đời xe khác không đổi được.

## 4. Test
`CameraMirrorTest` (:core — lật hợp với xoay/pan, lật hai lần = không lật, dấu `uSrcRect`), `CameraMirrorWiringContractTest` (:app — pref → controller → uniforms/TV), `TopStripSeatChipTest` (:core — mọi mức × sưởi/mát × lái/phụ ra hình thật, mức 0 ⇒ hình trung tính + tông INACTIVE, mức ngoài bảng ⇒ số quay lại), `TopStripStateIconTest`/`IconSetInventoryTest` cập nhật, `gen-icons.py --check` byte-identical.

## 5. 🚗 Kiểm trên xe (runbook 2.76)
- **SEAT**: sưởi ghế lái 1 rồi 2, mát ghế phụ 1 rồi 2 ⇒ chip và ô đổi **số dấu trong hình**, không còn số; hai ghế phân biệt được ở 20 dp.
- **CAM-M1**: người đứng sau-trái xe ~3 m; ảnh trái (xoay 0) phải cùng tay với gương kính ⇒ giữ tắt, ngược ⇒ bật *Lật gương* cả hai bên và ghi lại làm mặc định hồ sơ.
- **CAM-CL1**: dải cụm nay lấy từ hồ sơ — nếu lệch, sửa một chỗ `ClusterBandSpec.SEAL_DL3`.

## 6. Nợ còn lại
- Mức ≥ 3 (nếu đời xe khác có) chưa có hình — rơi về số, đúng luật *"chưa đo thì không vẽ"*.
- Mặc định `camera_mirror` còn [CHƯA BIẾT] tới CAM-M1; nếu phải bật, cân nhắc đưa vào `ClusterProfile` thay vì pref tay.
- Ảnh chụp trên máy ảo cho chip ghế: máy ảo **không có HAL** nên mức luôn `null` ⇒ chỉ dựng được hình từ tệp sinh (mục 1), không chụp được chip thật. Chốt bằng mắt trên xe.

---

## 7. Sau soát Opus 2026-09-27 (Pass 1) — bản vá [P1] của phần LẬT GƯƠNG
§2 hứa lật *"hợp với xoay/pan đúng như một camera lắp ngược"*. Đường GL **chưa** giữ lời hứa ấy: `CameraGlUniforms.of` đảo dấu
bề ngang `uSrcRect` (bước 4) nhưng lấy `uCenter` từ crop **chưa lật**, trong khi shader nắn quanh `uCenter` ở không gian local
**trước** bước 4 ⇒ trục nắn nằm ở `c` thay vì `1 − c`. Với crop gương hẹp (`narrowCrop`, `c = 1,25`) lệch **1,5 lần bề ngang
ô**: ảnh trông đã lật nhưng *"một bên thẳng, bên kia còng"* — đúng triệu chứng owner [ĐO]-bác cho `cx = −10 %` ngày 27/09.
Vô hình trên hồ sơ Seal hôm nay vì `span = STRIP` cho `c = 0,5` (`1 − c = c`), nên §4 (bài `CameraMirrorTest` chạy với
`amount 0`) không thể thấy.

**Vá**: `CameraDewarp.centerInCrop(..., flipH, flipV)` trả `1 − c` khi lật; chỉ **tâm hình học** lật — núm tay `centerXPct`
cộng SAU phép này và được dò trên ảnh ĐÃ lật nên giữ nguyên, cùng lẽ với `panXSign` (dấu theo BÊN, không theo phép lật).
**Bài mới** `CameraMirrorTest.GL co nan thi lat guong la anh soi cua ban khong lat, o crop hep` chạy với `amountPct = 100`
(bộ owner duyệt) trên cả `narrowCrop` và `stripCrop`, ghim bất biến *"điểm nguồn của bản lật tại `u` = điểm nguồn của bản
không lật tại `1 − u`"* — đã kiểm: bài này **ĐỎ** trên bản chưa vá, XANH sau vá. Ba bài lật cũ giữ `amount 0` (bài 2 có một
đẳng thức *nếu và chỉ nếu* chỉ đúng khi phép ánh xạ là affine, bài 4 so với ma trận TV vốn không có phép nắn).
