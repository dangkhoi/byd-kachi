# UX-OVERHAUL · WP7 + WP8 + WP9 + S7 — hoàn tất scope + verify emulator

> **Trạng thái**: DONE off-car + verify emulator API 29 · **Ngày**: 2026-09-21 · **Spec**: `docs/specs/kachi-ux-overhaul.html` §v8
> **CHƯA commit/push · CHƯA FF main/OTA** (chờ owner OK — spec OQ2). APK build sẵn: `apk/Kachi-1.86-release.apk`.

## 0. Bối cảnh — phiên trước hết token giữa chừng
Working tree để lại **5 test đỏ** (không phải regression thật, mà là dở dang):
1. 3 voice-wiring test grep `KachiTestBridge.kt` tìm `featmap`/`prefs_set`/`voice_dump` — WP7 đã dời chúng sang `TestBridgeNoHome.kt` ⇒ nối 2 file trong test (`bridge` var) + cập nhật chuỗi `voice_dump`.
2. `VoiceIntentParser.kt` 501 > 500 code-line ⇒ tách `media`/`mediaSearch`/`nav`/`SEARCH_HEADS` → `VoiceMediaNavParse.kt` (`:core`, 4 call site đổi).
3. `GroupTileWiringContractTest` kỳ vọng shape cũ: `g_battery` [5,4]→[4] & CARD [3,3,2]→[3]; `g_doors` CARD [3,3,1]→[3,2,2].
`CastProfileDensityTest` là **FLAKE** (đỏ dưới tải song song, xanh khi chạy riêng) — không phải regression.

## 1. WP7 — dọn dev/debug UI (đã có sẵn, xác nhận + bổ sung bài canh)
Cổng DUY NHẤT `DevMode.unlocked(context)` (= cửa sổ test-mode `TestBridgeStore.remainingMinutes>0`, fail-safe ĐÓNG).
Gác 3 chỗ: `SettingsSections.system()` (khối NÂNG CAO), `VoiceModelSettings.logRows()`, `SettingsSectionsCast.rescue()` (chỉ Diagnostics gác; 2 nút cứu-hộ THẬT của user KHÔNG gác). HAL probe ở `src/vehicleTest/` (không có trong release). featmap/prefs_set/voice_dump chỉ qua adb.
**Bổ sung**: `app/.../DevSurfaceGateContractTest.kt` (4 ca).
**Verify emulator**: NÂNG CAO chỉ có checkbox "Chế độ kiểm thử qua adb" (unchecked), pane kết thúc ngay sau — 0 nút dev.

## 2. WP8 — purge (đã có sẵn ở registry, xác nhận + bổ sung bài canh)
Icon xoá + id gỡ khỏi registry. `cast` chỉ `HIDDEN_FROM_PICKER` (pick vẫn non-null — nút nổi/voice/subsystem giữ). tailgate_status/sunroof_pos/sunshade_pct GIỮ.
**Bổ sung**: `WorkspaceStateTest.sanitized bo het cac ma purge cua luot UX-OVERHAUL WP8` — duyệt từng mã purge (ambient×9, gps×4, cell×5, motor_front/rear_rpm+front_torque [motor_power GIỮ], steering_deg/slope_deg/wheel_speed, wiper_state/tailgate_position/mirror_fold, engine×3+coolant_temp, hud×2/regen_level/wiper) → `pick()==null` + rụng khỏi ô đã lưu.
**Verify emulator**: picker đúng 8 nhóm, không Ambient/hood, widget grid không tile Cast.

## 3. WP9 — giọng bé OTA (làm mới)
- Đóng gói: `voice/tts/kachi-giong-be-v1.zip` (`zip -X -0`, 1550 tệp, 10,849,310 B, sha256 `3a9390171afb4d3df2ccfe69f0739efb3f7f7cbfcd55a52c56a45ca4dce81ddd`).
- `KachiClipVoiceCatalog.OTA_PACK` = `VoicePack` MỘT tệp `.zip` đã ghim (`ZIP_BYTES`/`ZIP_SHA256`), `downloadable=true`, `isArchivePack()=true`.
- `VoiceModelStore.install`: sau Verifying, nếu archive → `unzipInPlace` (`java.util.zip.ZipInputStream`, guard: `requireSafe` + `canonicalPath.startsWith(destRoot)`, mkdirs parent, bỏ isDirectory, trả lý do không ném) → `zip.delete()` → renameTo đưa **cây clip** vào `filesDir/clip-voice/kachi-giong-be-v1/`.
- UI: `ClipVoiceRow.kt` (tách khỏi `VoiceModelSettings` vì chạm 517>500), readiness = `index.tsv`+`num.tsv` ĐÃ BUNG (KHÔNG dùng `isReady(pack)` vì `.zip` đã xoá). 3 chuỗi VI/EN.
- Bài canh: `ClipVoicePackContractTest` (:core) + `ClipVoiceWiringContractTest` (:app).
**Verify emulator**: section "Kachi child voice (optional)" + "Download the voice pack" + selector "Child feedback voice (experimental)" hiện đúng.

## 4. S7 — finalize
- Full 5 module: **tests=5151 · failures=0** (`./gradlew test --rerun-tasks --continue`).
- Senior review độc lập (sub-agent) + security scan: 1 **[BLOCK]** = `docs/diagnostics/ux-overhaul-2026-09-20/wp3b-home-dark.png` (render ảnh xe BYD bản quyền) → **gitignore** file đó + `voice/tts/kachi-giong-be-v1/` (chỉ commit `.zip`+sha, không commit 1546 .aac đã bung). 2 [WARN]: giọng bé cần owner sign-off (owner đã duyệt phát hành công khai) + audio trùng (đã xử). 0 secret/IP/path.
- Bump `versionCode` 86→87, `versionName` 1.85→1.86.
- `apk/Kachi-1.86-release.apk` (38,176,954 B, sha256 `aa87cca2a878a872a0f2f32a89b82ef813b11c86abb6bea49caec8185401bbfa`); aapt2: `com.byd.launcher` vc87 vn1.86, **không debuggable**, **không probe surface**.

## 5. Còn lại / cần owner quyết

## 4b. WP-C — hướng dẫn chọn folder + ảnh xe DEFAULT (owner yêu cầu 2026-09-21)
Owner: *"phải cho user chọn được folder hoặc có hướng dẫn"* + *"lấy images/ làm default, xử lý transparent"*.
- **Ảnh xe default bundled**: `images/seal-3.png` (AI-gen, owner xác nhận KHÔNG logo/nhãn hiệu — clean) → `scripts/design/gen-default-car.py`
  (flood-fill xoá nền checkerboard giả từ biên + autocrop + downscale + lề trong suốt) → `app/src/main/assets/car/default-car.png` (678×1397, alpha thật, 57.4% nền xoá). Vào APK: `assets/car/default-car.png`.
- **Fallback**: `CarImageStore.loadFeathered` — thư mục `files/car/` trống ⇒ giải mã asset default (scaled) rồi feather;
  `hasUserImage()` phân biệt user/default; `signature()` = `"default:..."` khi trống (cache nạp lại đúng). Logcat 0 lỗi decode.
- **Hướng dẫn (hướng C)**: cả hai bề mặt (Cài đặt › Hiển thị&đơn vị › HÌNH XE, và › Màn hình chính › Hình nền) nay có
  **các bước 1/2/3** (cắm USB / chép ảnh vào thư mục / mở lại) + đường dẫn đầy đủ + nút **"Sao chép đường dẫn thư mục"**
  (clipboard) + Toast xác nhận. Hình xe hiện "Đang dùng hình xe mặc định" khi chưa có ảnh user.
- **Verify emulator**: HÌNH XE block hiện đúng (default + 3 bước + path không cắt + nút copy); wallpaper tương tự. Sửa
  thứ tự: footnote màu dời lên trước khối hình xe (tránh đọc như giải thích hình xe — họ lỗi U12/U13).
- ⚠ **Board hình xe (TyreBoardView/CarMiniView) chỉ render trong ô ĐỦ LỚN** (ngưỡng mini-vs-board CÓ TỪ TRƯỚC, không
  phải WP-C): ô nhỏ → mini-card (icon 24dp), ô lớn → board có hình xe. Off-car khó dựng ô board-size qua UI-driving;
  asset decode đã proven sạch. 🚗 xem hình xe thật trên board cần ô lớn / dữ liệu xe.
- 5 chuỗi mới ×2 ngôn ngữ (`kachi_car_image_title/default/steps`, `kachi_copy_path`, `kachi_path_copied`, `kachi_wall_steps`);
  xoá orphan `kachi_car_image_hint`. Full 5 module: **0 đỏ**. APK cập nhật lại `apk/Kachi-1.86-release.apk`.

## 5. Còn lại / cần owner quyết
- **Commit + push** nhánh `feat/voice-hotword-phrases` (chờ owner OK). Stage TƯỜNG MINH theo tệp — KHÔNG `git add .` (ảnh/clip untracked).
- **FF main + OTA 1.86**: chờ owner (OQ2).
- 🚗 **cần xe**: nhìn thật trên cụm (hình xe/màu, nút 34dp khi lái, thanh dọc 83dp) · tải giọng bé OTA + phát trên xe · glass giả vs thật đo CPU/fps.
- **WP9 R9.3** (giọng nữ miền Nam) = research task riêng, không chặn version.
