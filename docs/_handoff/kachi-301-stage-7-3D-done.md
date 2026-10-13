# kachi-301 · stage 7 — R25 widget xe 3D (lưới Seal thật) — DONE (off-car + máy ảo; 🚗 chưa đo xe)

Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` R25 (§4.6, Pass 7, Implementation Log) ·
Chẩn đoán + số đo: `docs/diagnostics/widget-3d-301-2026-10-13.md` · Backlog: `CAR3D-ONCAR-VERIFY`, `CAR3D-MEM-TRIM`, `CAR3D-RHD-PAINT`.

## Files
- `:core` (thuần): `BydAvmModels.kt` (bảng `car.type`→thư mục chép từ jadx AVM), `BydAvmMesh.kt` (kind 0/1/2, cửa sổ trên đệm
  native, sửa trỏ bộ đỉnh theo đoạn tên), `Car3dGlb.kt` (khối đỉnh riêng mỗi bộ phận + `extras.bbox`, ghi vào đệm do bên gọi cấp),
  `Car3dBinding.kt` (catalog Seal thật, `Car3dPaint`, `Car3dBox`, `Car3dNames.visibleAll`, bỏ mô hình mẫu), `Car3dMath.kt`
  (`outward(a, bounds)` elip ngang + luật nóc), `WidgetRegistry.kt` (`w_car3d`, `minSlotDp`, `offered`), `CarDataDemand.kt`,
  `i18n/{zh,th,ms}.tsv`.
- `:app`: `Car3dRenderer.kt`, `Car3dModelSource.kt`, `Car3dWidgetView.kt`, `Car3dTaps.kt`, `Car3dDotsLayer.kt` (mới);
  `WidgetViews.kt` (nhánh `w_car3d` + ô nén), `DrawerController.kt` (bộ chọn Ô chỉ bày widget ô-lớn khi khung đủ),
  `AndroidManifest.xml` (`<queries>` `com.byd.avc`), `build.gradle.kts` (Filament 1.77.3: `filament-android` + `gltfio-android`),
  `res/values*/strings_kachi_car3d301.xml` (`kcar3d_title`), `src/vehicleTest/.../OemCarArtTestProvider.kt` (móc
  `Car3dModelSource.bundled`), `.gitignore` (`app/src/vehicleTest/assets/oem-3d/`).
- Test: `Car3dSealTest.kt` (mới), `Car3dBindingTest.kt`, `BydAvmMeshTest.kt`, đếm widget 10→11 (`WidgetRegistryTest`,
  `CapabilityGroupsTest`, `LangCoverageTest` 270→271), `OemCarArtSourceSetContractTest` (+ca mô hình 3D), `WidgetInPlaceValueContractTest`
  (8→9), `LauncherI18nAllowlists` (`ic-car`).
- KHÔNG đụng tệp cast-geometry.

## Contract / shape
- `WidgetDef(minSlotDp = 420 to 300)`; `WidgetRegistry.offered(wDp, hDp)` (null ⇒ không bày widget ô-lớn).
- `Car3dGlb.write(mesh, styles, textures, partStyles, alloc): ByteBuffer`; node `extras = {"bbox":[minX,minY,minZ,maxX,maxY,maxZ]}`.
- Release: `createPackageContext("com.byd.avc",0).assets` → `resource_pi/<g>/CarModel[/<f>]/{car,wheel}.mesh`; tệp đệm
  `cacheDir/car3d-<hash>.glb` (34,5 MB, `CACHE_VERSION` v3). vehicleTest: `assets/oem-3d/eke/CarModel/eke/…` (gitignore).

## Verified on emulator-5556 (vehicleTest 3.1 (205), QA2, Tối, tiếng Việt, số giả speed 0 P)
- Nạp 98 bộ phận, 12/12 chấm neo; ảnh ¾ trước-trái + ¾ sau-phải: mọi chấm đúng bộ phận (scratchpad `shots/car3d-*.png`, không vào repo).
- logcat: `tap win-lf mức=0 → win_half_lf BẬT` → `ctl id=win_half_lf setBodyWindowCtrlState [1,4]`; lần 2 `→ win_lf BẬT [1,1]`;
  `tap headl → headl BẬT`; `tap door-lf → chỉ xem`; `tap nút → windows_all [1,1,1,1]` / `windows_close_all [2,2,2,2]`;
  `tap trunk → trunk BẬT` (`voiceCtlBackDoor [1]`, gate speed=0 P); ở 30 km/h D: `trunk: MỞ bị chặn`.
- Vuốt 40°→200°: 21 khung; rảnh 30 s: 0 khung, CPU 0 %. PSS 118,7 → 224,1 MB. APK release +6,06 MB native.

## Layout máy ảo
Ô lớn QA2 (`QA2__slot_0`) = `widget:w_car3d` (trước đó `widget:g_lights`; ô Kính `slot_2` không đổi). Trả lại:
`adb -s emulator-5556 shell am force-stop com.byd.launcher; adb -s emulator-5556 shell "run-as com.byd.launcher sed -i 's|QA2__slot_0\">widget:w_car3d<|QA2__slot_0\">widget:g_lights<|' shared_prefs/kachi_workspace.xml"`
Bản sao prefs trước khi đổi: scratchpad `car3d-emu/ws-before-3d.xml`.

## Risks
- Chưa chạy trên xe (GPU thật, /data trống bao nhiêu cho tệp đệm 34,5 MB + tệp tạm 35 MB lần đầu) — `CAR3D-ONCAR-VERIFY`.
- PSS +105 MB trên máy ảo (phần do giả lập GL [CHƯA BIẾT]) — `CAR3D-MEM-TRIM`.
- Chức năng đèn theo vị trí [ĐOÁN]; tay lái nghịch + màu sơn [CHƯA BIẾT].
- Lỗi JNI abort (`Box.set`) không bắt được bằng try/catch — đã tránh; mọi lỗi Java khi dựng/vẽ ⇒ bảng 2D.
