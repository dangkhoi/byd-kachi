# Lượt xe 2026-09-16 — trace voice trực tiếp · sweep · vòng action · framework thật của xe

- **Ngày:** 2026-09-16 09:00–09:35 · **Chủ:** dangkhoi · **Xe:** DiLink3.0 (Android 10, fingerprint `…eng.build.20260204`), Qualcomm TRINKET 8 lõi 1,8 GHz, RAM 7,6 GB (**trống ~60 MB**) · **Bản:** Kachi **1.64 (65)** qua OTA, sau đó bản thử `1.65-mic` (vehicleTest).
- **Kết nối:** `adb connect <ip-xe>:5555` từ máy soạn thảo; test-mode bật tay ở Cài đặt › Nâng cao (60 phút).
- **Bằng chứng:** `carlog-0916/` (sweep JSON · vòng action · getters · trace voice · ảnh bug ô · thống kê feature-not-in-device) — thư mục **gitignored** (`docs/diagnostics/carlog-*/`, chứa VIN thật trong sweep) ⇒ chỉ có trên máy soạn thảo; số liệu trích vào doc này là bản đã lọc. Mức: mọi số dưới đây là **[ĐO]** trừ khi ghi khác.

## 1. Hiện trạng lúc kết nối (1.64 chạy từ 08:14)
| Mục | [ĐO] |
|---|---|
| HOME | `KachiHome` (alias) resumed ở display 0 — HOME-alias hoạt động sau cài GUI |
| Cast cụm | **PASS**: Google Maps task ở display 2 (`fission_bg_xdjaVirtualSurface` 1920×720), ClusterBlack nằm dưới |
| Ô app | YouTube trong `kachi-slot-0` (display 7, 1673×935, **density 200** — density-lever ăn) |
| Crash | 0 của Kachi; 1 của `com.google.android.youtube:mediaengine` (bản mod) |
| RAM | Kachi RSS 537 MB (Native heap 477 MB = model sherpa fp32); máy còn 56–94 MB |
| TTS | `state.tts`: `kind NONE`, `vi_status -99` — **không có giọng Việt** trên xe ⇒ phản hồi giọng cần gói offline (pha 2) |

## 2. Voice — trace từng mốc (phím vô-lăng → tấm chữ → mic → sherpa → hiểu ý → xác nhận)
Lượt 09:10 (bản 1.64, nguồn mic 6 = VOICE_RECOGNITION):

| Mốc | Δ | Gì |
|---|---|---|
| 09:10:13.7 | — | `VoiceKeyLauncher: mở phiên nghe ok=true`, tấm chữ lên |
| 09:10:14.5 | +0,8 s | mic mở nguồn 6; **đỉnh 4877/32767 · rms 457** (tốt) |
| 09:10:25.7 | +11,2 s | nghe hết trần **8,4 s** (owner nói liên tục, không có khoảng lặng để ngắt) |
| 09:10:28.1 | +2,35 s | sherpa: `"mở kính lái a lô một hai ba bốn năm a lô một hai"` — **nghe đúng** |
| 09:10:31.2 | **+3,1 s** | `lượt 1 (ngữ pháp) nghe được` — **lỗ 3,1 s lặp lại ở mọi lượt** (28.065→31.163 · 43.853→46.951 · 18.511→21.626 · 53.268→56.378), nghi nằm ở `finally` của `VoiceCapture.listen` (stop/release/tone/abandonFocus) — chưa đo |
| 09:10:31.4 | | hiểu "mở kính" = **`windows_all`** (synonym `kinh`) ⇒ CONFIRM *"Hạ hết 4 kính?"* ⇒ mở mic xác nhận 5,4 s |
| 09:10:41.1 | | nghe `"ừ"` ⇒ `confirmAnswer` = **null** (cố ý không nhận ừ/vâng) ⇒ coi là KHÔNG ⇒ huỷ, tấm chữ biến mất, **không nói gì** |

Lượt 09:14–09:15 (cùng bản): 3 phiên liên tiếp **đỉnh 136–255/32767, rms 30–50** (gần câm) ⇒ sherpa ra `"ừm"`; owner báo *"nói bằng mức nói Kiki, Kiki nhận rất tốt"*. `dumpsys audio`: Kiki ghi bằng **`src:MIC`**, Kachi bằng `VOICE_RECOGNITION`; không có `setRecordSilenced(…, silenced:1)` nào; input device duy nhất `AUDIO_DEVICE_IN_BUILTIN_MIC` (+ BACK_MIC).

Lượt 09:30 (bản thử `1.65-mic`, đảo thứ tự nguồn → **MIC trước**):

| Mốc | Gì |
|---|---|
| 09:30:24.6 → 09:30:39.5 | **15 s** từ bấm tới mic mở — **nạp model lần đầu sau khi mở app** (các lượt sau 0,2 s) |
| 09:30:50.8 | mic nguồn **1**: đỉnh 4429 · rms 237 → sherpa `"mở cửa sổ"` ✓ |
| 09:31:02.8 | xác nhận: `"đồng ý"` ⇒ **true** ⇒ lệnh chạy (owner: 4 kính mở) |
| sau đó | `"bật đèn đọc"` chạy đúng (owner xác nhận) — *"mà vô cùng chậm"* |

**Kết luận voice:** engine + mô hình + hiểu ý **chạy trên xe**; ba lỗi thật: (1) **nguồn mic** — VOICE_RECOGNITION cho tiếng gần câm 3/4 lượt, MIC như Kiki thì tốt ⇒ ship MIC trước; (2) **chậm** = 15 s nạp model lần đầu + 8 s nghe cố định + 2,3 s giải mã (2 luồng/8 lõi) + 3,1 s lỗ + 5,4 s chờ xác nhận; (3) **cổng xác nhận bỏ "ừ"** im lặng + "kính lái" không có trong từ vựng ⇒ rơi vào nút gộp CONFIRM. Owner: *"cái nào nguy hiểm lái xe mới hỏi, chứ mở cửa hỏi làm gì? cần document lại cái nào cần đồng ý để tôi chọn"*.

## 3. Sweep (chỉ đọc) + vòng action (44 lệnh `ctl`, cách 4 s)
- Sweep 1.64: info **83/123** đọc được; 40 không: 12 *feature-not-in-device* (§4), 4 GPS (blocked-by-design), 24 còn lại (batt_temp, cell_v, target_soc, gear, cabin/inside/coolant temp, tailgate, mirror_fold, headlight_feedback, ambient ×4, oms/child, lca/rcta, radar_volume, key_bluetooth). ctl route 62/64.
- Vòng action (`carlog-0916/ctl-round.txt`): HAL **accepted** 34/44; **sentinel "absent on trim"**: `ac_auto`, `ambient_power`, `mirror_fold_btn`, `child_lock`; **NoSuchMethod**: `door`/`lock` (`setDoorLockState`), `trunk` (`setHetchDoorStatus`). ⚠ "accepted" ≠ chạy — **owner chưa xác nhận nút nào phản ứng thật** (câu hỏi đang mở).
- Getters (`hal-getters.txt`): `getDrivingTimeValue`=49.8 · `getEnergyFeedback`=2 · `getOperationMode`=2 · `getWindowOpenPercent(1)`=0 (sau khi đóng); rỗng: DrivingMileage, AvgEnergy, EngineCode(NULL), AmbientBrightness, InsideLightState, SunshadeState, DoorLockState.

## 4. Framework THẬT của xe (kéo `/system/framework/framework.jar` 29 MB + `AutoPermission.apk`, jadx) — [ĐO nguồn]
- **"You have no permission to use the feature 0x… with this device N"** = `AbsBYDAutoDevice.checkDeviceFeatures`: feature-id **không nằm trong** `BYDAutoDeviceFeaturesMap.getFeatureIdsFromDevice(deviceType)` — tức Kachi gọi **sai device**, không phải thiếu quyền. 10 804 dòng/47 phút cho 12 datum: bsd_fl/fr_alarm (dev 1038) · drift_mode · motor_front_torque/rpm · motor_rear_rpm (1023) · motor_power · trip_km/hours (1014) · wiper_state (1001) · ambient_front/rear_color (1004). Fix: map feature→device từ chính `BYDAutoDeviceFeaturesMap`, và **ngừng poll** thứ đã bị từ chối.
- `BYDAutoBodyworkDevice` setter: `setAllWindowState(4 int)`, `setBodyWindowCtrlState(area,state)`, `setMoonRoofState`, `setSunshadeState`, `setRainCloseWindow`, `setMoonRoofAndSunshadeStop` — **không có** `setHetchDoorStatus` (bản jadx-tmap cũ có; xe không). `BYDAutoDoorLockDevice`: **không setter**; chỉ `getDoorLockStatus(area)` + `DOOR_LOCK_COMMAND_AREA_*` (960495668/70/72/74/**76 = back**), `DOOR_LOCK_STATE_UNLOCK=1/LOCK=2`, quyền `BYDAUTO_DOOR_LOCK_SET`; đường ghi duy nhất là generic `AbsBYDAutoDevice.set(int[] ids, BYDAutoEventValue)` — **chưa thử**.
- **Feature-id KHÔNG cố định** [ĐO nguồn]: `BYDAutoFeatureIds` gán giá trị trong static-init theo cấu hình xe (`isCanFD`/`isToyota`, vd `AC_AUTO_CLEAN_AIR = (!isCanFD && isToyota) ? 1282416678 : 1301291046`) ⇒ số decimal hardcode từ jadx-tmap có thể không tồn tại trên xe này — đó là gốc của 12 datum "no permission" và có thể của 4 nút "absent on trim". Bảng tĩnh 785 id: `carlog-0916/device-features-map.tsv` (tham khảo). Hướng: bind theo **tên hằng**, resolve bằng reflection lúc chạy + bridge `featmap` dump bảng thật.
- Từ nay RE HAL theo `fw-dl3` (scratchpad `car-0916/framework/fw-dl3`), không theo jadx-tmap.

## 5. Bug mới
- **Ô đo sai kích thước lúc khởi động** khi thanh top/bottom của xe còn hiện (ảnh `carlog-0916/slot-insets-bug.png`): VD ô dựng theo khung đã bị co, YouTube bị đẩy xuống với dải xám trên; bấm Home lần nữa (thanh ẩn) thì đúng. ⇒ đo lại và resize VD khi inset/kích thước workspace đổi.

## 6. Việc off-car sinh ra từ lượt này (backlog V-ONCAR-0916)
1. Voice nhanh: MIC trước · ngắt câu khi ngừng nói (VAD năng lượng) · nạp model sẵn lúc mở app · 4 luồng · mốc giờ từng chặng + bịt lỗ 3,1 s · nhận mọi từ đồng nghĩa YES ở cổng xác nhận + tấm chữ ghi rõ (owner: KHÔNG đọc câu hỏi, CÓ đọc phản hồi) · synonym "kính lái/cửa kính lái/kính tài xế" → `window`.
2. Mục Cài đặt "Lệnh nào cần xác nhận" — owner chốt 09-16: **mặc định KHÔNG hỏi gì cả**, tự bật từng nút khi muốn.
3. 12 datum sai device → map từ `BYDAutoDeviceFeaturesMap`; cache "denied" bỏ poll.
4. RE khoá/cốp qua `set()` generic (feature 9604956xx, quyền SET) — thử trên xe lần sau, có hoàn tác.
5. Bug ô/inset lúc khởi động.
6. int8 (host: encoder 248→66 MB, 25 WAV không giảm; tốc độ trên ARM [CHƯA BIẾT]) — side-load thử lần sau.
7. Status catalog: 4 nút sentinel ❌-trim, lock/trunk ❌-no-setter, 12 datum ⚠ sai device; các nút HAL-accepted chờ owner xác nhận mắt thấy.
