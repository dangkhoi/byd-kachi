# Ảnh xe chính hãng BYD cho bảng hình xe — bảng ánh xạ (3.01 R23, 13/10)

> Spec `docs/specs/kachi-301-cast-steps-dialog-ia.html` Pass 4 + R23. Chỉ văn bản — KHÔNG có ảnh BYD nào trong docs/repo.
> Owner 13/10: *"cho phép dùng ảnh firmware BYD trong bản kiểm thử"* ⇒ ảnh trích firmware chỉ đóng vào bản `vehicleTest`
> dựng tại máy (thư mục `app/src/vehicleTest/assets/oem-car/` bị `.gitignore`); bản phát hành chỉ ĐỌC lúc chạy từ gói hệ thống.

## 1. Nguồn [ĐO]

| Mục | Giá trị | Bằng chứng |
|---|---|---|
| Gói | `com.byd.carsettings.plugins` (`/system/priv-app/CarSettingsPlugins/`), versionName `1.4.1.61.2604081802.c233905`, target 29 | `aapt2 dump packagename` / `dump badging` trên `firmware/fw-2606/_system_priv-app/CarSettingsPlugins/CarSettingsPlugins.apk` |
| Cách CarSettings đọc | `PluginResourceLoader.init`: `createPackageContext(DeviceUtil.getPluginPkgName(), 3).getResources()` + `getResourceID("drawable", name)` | `carsettings-apk/jadx-carsettings/sources/com/byd/common/carimage/PluginResourceLoader.java:31-38`, `CarBodyImageUtils.java:125-133` |
| Tên gói plugin | `Constants.SETTINGS_PLUGIN_PKG_NAME = "com.byd.carsettings.plugins"` | `com/byd/common/Constants.java:16`, `DeviceUtil.java:17-18` |
| Seal | `CAR_AUTO_TYPE_EK = 138` (= 0x8a = `persist.sys.car.type` của Seal đã đo 14/09, 29/09) | `grep CAR_AUTO_TYPE_EK` trong jadx; `ClusterCarType.kt` KDoc |

## 2. Ảnh nhìn-trên theo đời xe — công thức BYD [ĐO jadx]

`"common_top_view_img_" + DeviceUtil.getDevicePlatform() + "_" + CarModelAndTypeHelper.getCarModelSuffix() + OutsideLightUtil.getCarImgSuffix()`
- `getDevicePlatform()` = `"6125f_ivi"` (`DeviceUtil.java:13-15`)
- `getCarModelSuffix()` = `String.format("0x%x", vehicleId)` (`CarModelAndTypeHelper.java:42-46`)
- `getCarImgSuffix()` = `"_right"` nếu tay lái nghịch, không thì `""` (`OutsideLightUtil.java:126-128`)
- Nơi dùng: `Di4IviAmbientLightFragment.java:62`, `AmbientLight{Color,Area,Brightness}.java`.

⇒ Seal (LHD) = `common_top_view_img_6125f_ivi_0x8a` — resource `0x7f010033`, chỉ có bản `drawable-hdpi-v4` (**không có bản `-night`**),
PNG 840×576 có alpha. 21 đời xe có ảnh này (`0x161 0x47 0x5a 0x66 0x80 0x87 0x89 0x8a 0x8d 0xa0 0xa2 0xad 0xbf 0xdc 0xf4` + vài `_right`).

## 3. Ứng viên đã loại [ĐO — ảnh thu nhỏ chỉ ở thư mục tạm của phiên, không lưu vào repo]

| Ứng viên | Cỡ | Vì sao không dùng |
|---|---|---|
| `setting_roof_*_car_grey` (vd `sa3f`, `hcb`, `0x161`) | 600×1150 / 600×900 / 600×1230 | Đúng là xe nhìn-trên có nóc, nhưng là SUV/đời khác. `SunRoofFragment.initViewVisible` (`SunRoofFragment.java:495-700`) KHÔNG có nhánh cho 138 ⇒ Seal rơi về mặc định `setting_roof_hcb_car_grey` (Han) — không phải Seal |
| `dilink_audio_car_bg_common` (+ `-night`) | 608×944 | Chỉ khoang nội thất mờ (không thân xe, không bánh). Seal dùng bản `common` (`SoundFieldContentCommon.getResNameByCarType`: chỉ 143/175 có ảnh riêng) |
| `common_default_6125f_ivi_0x8a` | 840×576 | Seal góc ¾ phía trước — đẹp nhưng không đặt được 4 kính / 4 bánh đối xứng; để dành cho ô "xe" kiểu Kim (ngoài R23) |
| **`common_top_view_img_6125f_ivi_0x8a`** | 840×576 | **CHỌN** — Seal nhìn-trên bỏ nóc, đúng dáng Seal, đầu xe quay TRÁI |

## 4. Hình học đã đo (dữ liệu `CarArtCatalog.SEAL_TOP`, `:core`)

Đo bằng lưới phóng + hồ sơ độ sáng từng cột trên ảnh gốc (script tạm của phiên, đọc ảnh bằng PIL):
- Mực đục (alpha > 8): x 42–801, y 107–448; thân 128–426 (gương 107/448 ở x ≈ 300–335). Cắt chừa 6 px: `(36, 101)–(807, 454)`.
- Hướng: vô-lăng ở (≈ 340, 330) — nửa DƯỚI ảnh ⇒ sườn TRÁI xe (LHD) ở mép dưới. Xoay 90° thuận ⇒ đầu xe lên, sườn trái sang trái.
- Dải mép cửa (kính hạ vào đây): trái y 386–405, phải y 150–168 ⇒ trục giữa y ≈ 277,25 ⇒ `mirrorSum` 554,5.
- Đường ghép cửa trước↔sau x ≈ 447–452; cửa trước từ x ≈ 290; cửa sau tối hẳn từ x ≈ 595.
- Bánh [SUY, ±20 px]: theo kích thước Seal (dài 4 800, cơ sở 2 920 mm) + vè rộng nhất ⇒ trục trước x ≈ 205, sau x ≈ 650.
- Ảnh không có nóc ⇒ "kính" trên ảnh là DẢI MÉP CỬA, không phải mặt kính; bề rộng ≈ 19 px / 353 px khung (≈ 5 %) — vẫn mảnh như ảnh mặc định (~4 %).

## 5. Đường chạy

- Release: `OemCarArt.fromSystem` = `createPackageContext(PKG, 0).resources.getIdentifier(name, "drawable", PKG)` → `openRawResource`.
  Tên từ `ClusterProfile.carType` (prop / mã dò dadb) + chỉ khi `CarArtCatalog.byKey(name)` có số đo. Cỡ ảnh ≠ 840×576 ⇒ từ chối.
  Hỏng bất kỳ đâu ⇒ ảnh mặc định `assets/car/default-car.png` + hình học mặc định.
- `vehicleTest`: `OemCarArtTestProvider` (provider không xuất) gán `OemCarArt.bundled` ⇒ máy ảo dùng bản trích ở `src/vehicleTest/assets/oem-car/`.
- Manifest chính: `<queries><package android:name="com.byd.carsettings.plugins"/></queries>`.

## 6. Còn mở

- [CHƯA ĐO] trên xe: gói plugin của xe owner có resource này không (fw 2606 có; xe chạy firmware khác ⇒ có thể vắng ⇒ tự lùi ảnh mặc định, log `OemCarArt: car art = default`).
- [SUY] `vehicleId` của CarSettings (`CarInfoManager.getVehicleId`) = `persist.sys.car.type` — trùng 138 ở Seal; đời khác chưa đối chiếu.
- Tay lái nghịch (`_right`) chưa đọc được cờ ⇒ luôn bản LHD. Đêm: ảnh không có bản `-night`.
- Đời xe khác Seal: cần đo thêm một mục `CarArtCatalog` mỗi ảnh (tên ở §2) — chưa đo thì giữ ảnh mặc định.

## R24 — điểm ĐÈN trên ảnh (3.01, owner 13/10 *"có hình xe và chấm tròn hay icon đèn xung quanh, đúng vị trí đèn"*)

Đo trên ảnh gốc (px gốc, sub-agent đọc ảnh có lưới + crop phóng 3–4×; ảnh chỉ ở thư mục tạm của phiên, không vào repo).

| Mốc | Seal 840×576 (đầu xe TRÁI) | ± | Ảnh mặc định 678×1397 (đầu xe LÊN) | ± |
|---|---|---|---|---|
| Đèn trước trái / phải | (97, 378) / (100, 173) — khe thấu kính tối | 6 | (148, 115) / (524, 115) — [SUY] không thấy thấu kính, suy từ viền ca-pô | 25 |
| Mũi xe giữa | (50, 277) | 5 | (336, 58) | 6 |
| Gương trái / phải | (312, 432) / (312, 122) | 8 | (62, 468) / (617, 468) | 8 |
| Đèn hậu trái | (735, 411) — dải LED đỏ | 4 | (177, 1305) — đầu trái nẹp cốp; dùng (160, 1290) [SUY, trong sai số góc hậu] để tách điểm sương mù sau ở ô Đèn | 15 |
| Đuôi giữa | (796, 277) | 4 | (336, 1340) | 5 |
| Khe giữa hai ghế trước | (370, 277) — bệ tì tay/cần số | 10 | (336, 540) | 10 |

Gán đèn (một dữ liệu ⇒ một điểm, `CarArt.kt` · `LightSpots`): cốt = đèn trước-trái · pha = đèn trước-phải · sương mù trước = mũi ·
ban ngày = giữa ca-pô sau hai đèn (Seal x 160, mặc định y 250 — dời từ mép ca-pô sau lượt máy ảo đầu: điểm chồng điểm sương mù ở
ô Đèn 267×329 px ⇒ rơi về lưới icon) · xi-nhan = đèn báo trên gương · đèn hông = đèn hậu trái · sương mù sau = đuôi giữa · đèn đọc =
khe hai ghế trước. `headlight_feedback` (chế độ) không có điểm. Seal: đèn trước/hậu đối xứng quanh trục y ≈ 277 (±101–104 / ±134 px).
