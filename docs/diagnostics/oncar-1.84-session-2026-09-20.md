# On-car session 1.84 (vc85) — 2026-09-20

> **Trạng thái**: Current · **Xe**: `<car-ip>` (adb_raw.py shell) · **Bản**: OTA lên 1.84 (vc85) đầu phiên.
> Findings on-car → fix off-car (gói 1.85). Mọi khẳng định [ĐO] = có output bridge/logcat thật phiên này.

## 0. THU HOẠCH LỚN NHẤT — cầu test-bridge KHÔNG hề down

[ĐO] "adb hỏng / bridge down" các phiên trước = **DO LỆNH SAI**, không phải BUG2.
- **Implicit** `am broadcast -a com.byd.launcher.TEST` → `result=0`, receiver **KHÔNG fire** (Android 10 chặn implicit broadcast cho receiver manifest).
- **Explicit component** `am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST` → `result=1` + data JSON + receiver CHẠY.
- ⇒ **FIX off-car**: sửa mọi script `scripts/vehicle/kachi/*.sh` sang `-n <component> -a <action>`. Cầu `say`/`hal`/`state`/sweep dùng được hết.
- Toggle "Chế độ kiểm thử qua adb" tự tắt sau 60 phút — phải bật lại mỗi phiên (`test_mode_off` khi tắt).

## 1. RAIN TRIGGER — TÌM RA (cho automation mưa→sấy)

[ĐO] `SETTING_FRONT_RAIN_WIPER_SPEED` (feature-id **1196425250**, device `BYDAutoSettingDevice`):
- **Khô = 1** (4/4 reads sau khi khô)
- **Mưa (tạt nước lên cảm biến sau gương) = 2** (7/7 reads) — càng to càng cao
- **Bistable, live** (về 1 khi khô) — KHÔNG latch.

Trigger automation #1: poll id 1196425250; **>1 = mưa → bật defrost**; =1 = khô.

**Wiper getter khác = CHẾT/latch** (không dùng được):
- `WIPER_FRONT_WIPER_LEVEL` (321912848): 65535 khô → 8 khi gạt, **LATCH** (giữ 8 cả khi tắt).
- `WIPER_RELAY_STATE` (1336934438) + `getWindscreenWiperRelayState`: **0 cả khi đang gạt** (owner xác nhận đang gạt, 8/8 reads đứng im).
- `WIPER_AREA_FRONT_STATE` (540287): -10011 (invalid).
- `AUTO_RAIN_WIPER_SWITCH` = 1, `AUTO_RAIN_WIPER_ONLINE` = 1 (trên `BYDAutoSettingDevice` — cảm biến mưa active/provisioned).
- ⚠ Bài học: weather-API là hướng SAI (owner bác đúng — xe CÓ cảm biến mưa vì có tính năng đóng-kính-khi-mưa). Mò tiếp là ra.

## 2. GPS — CÓ (cho automation nav theo giờ)

[ĐO] `dumpsys location`: VietMap (`vn.vietmap.live`) đang xin `gps ACCURACY_FINE`, "monitoring location: true". Xe có GPS provider + cấp vị trí. ⇒ automation #2 khả thi (Kachi launch nav-intent theo giờ; nav app tự lấy GPS). Kachi KHÔNG đọc được GPS qua HAL (4 datum GPS = X) nhưng framework Android có.

## 3. RE ROUTE GHI — 5 nút owner chọn XÀI

Owner phân loại (12:14): **XÀI** = khóa cửa · camera 360 · góc cam · gió-auto · khóa trẻ em. **BỎ** = gạt-mưa(control) · đèn pha(để sau) · đèn viền · chiếu-cụm/nhạc-cụm/xoay-màn · regen.

| Nút | Kết quả [ĐO] | Route |
|---|---|---|
| **Khóa trẻ em** | ✅ RE xong | `DOOR_LOCK_COMMAND_AREA_CHILDLOCK_LEFT_SET`(1276141584)/`_RIGHT_SET`(1276141586), dev `BYDAutoDoorLockDevice`. rc=0, state toggle. ⚠ **map: `_SET=1`→state2=TẮT · `_SET=2`→state1=BẬT** (ngược trực giác — owner xác nhận vật lý). |
| **Gió-auto** | ✅ RE xong | `AC_CTRL_MODE_SET`(**501219352**), dev `BYDAutoAcDevice` (chữ 'c' thường!). **=0 → AUTO** · **=1 → manual**. rc=0, 2 chiều, owner xác nhận màn AC. Indicator = `AC_WINDLEVEL_MANUAL_SIGN` (0=auto/1=manual). ⚠ KHÔNG phải `AC_WIND_MODE_SET` (=hướng gió) hay `AC_WIND_LEVEL_SET=0` (bị bỏ qua). |
| **Khóa cửa chính** | ❌ CHẶN | `DOOR_LOCK_COMMAND_AREA_LEFT_FRONT`(960495668) setev → **rc=-2147482648 (NOT_PROVISIONED)**, cửa không phản ứng (owner xác nhận không kêu/đèn). Main-lock không có `_SET` variant. Named setter cũng X (CAPTEST). ⇒ trim chặn HAL door-lock. Off-car: đào named `setDoorLockState`/set-all sâu hơn, nhiều khả năng bị chặn an ninh. |
| **Camera 360** | ⏳ chưa test | id `ADAS_AVM_APA_*` (487587872 active, 487587878 switch). Owner: `setAVMSwitchState` (giữ khi ADAS-purge). RE off-car/lần sau. |
| **Góc cam** | ⏳ chưa test | chưa tìm được feature-id góc cam. RE off-car/lần sau. |

**Cầu `hal` syntax** (proven): `--es op getid --es dev <Device> --es m <id|TÊN_HẰNG>` (đọc, không confirm) · `--es op setev --es dev <Device> --es m <id|TÊN> --es args <val> --ez auto_confirm true` (ghi generic). Device đúng lấy từ `device_by_map` trong reply.

## 4. FINDINGS FEATURE (đổi bộ chọn/registry off-car)

- **⚡ Ca-pô = BỎ HẲN**: owner xác nhận xe KHÔNG có ca-pô điện, chỉ cốp sau điện. Gỡ `hood` khỏi bộ chọn/registry.
- **"Điều hòa AUTO" = chỉ GIÓ auto** (xe không có nhiệt-auto). `ac_auto` map sang gió-auto (`AC_CTRL_MODE_SET`), không phải temp.
- **⚡ Bụi mịn NGOÀI xe — data CÓ THẬT**: [ĐO car log] `Pm2p5Controller: onPM2p5ValueChanged in:8 out:22` + `setOutCarText`. Datum `pm25_outside` hiện "—" (X) chỉ vì **chưa nối getter** → wire off-car (đổi NEEDS_CAR→có getter). Callback `onPM2p5ValueChanged(in,out)` → tìm getter kiểu `getPM2p5Value()[1]`/`getOutCarPM2p5`.
- **Nhiệt cabin + nhiệt cài đặt = X nhưng data CÓ** ([ĐO] `AmapService: temp=22`) → read route sai, fix off-car. (Nút "Nhiệt độ" GHI thì OK.)
- **Cốp**: ghi OK (mở/đóng) nhưng đọc trạng thái X (`tailgate_status` không đọc) → cần getter đọc cốp.
- **1.84 features CHẠY trên xe** (owner chấm CAPTEST OK): ghế mát/sưởi SELECT 3 mức ✓ · cốp COVER ✓ · nhiệt độ (setpoint) ✓.
- **"Độ sáng HUD" = BỎ** (owner: hiếm xài, không RE — nghi id-trùng brightness_gear nhưng không cần fix).

## 5. VOICE BUG — session "biến mất" (fix off-car, ưu tiên cao)

[ĐO logcat] khi ASR nghe không rõ → `NO_VERB`:
```
sherpa ra: "chưa nghe rõ" → quyết định: không hiểu: NO_VERB
KachiVoiceSession: pha: chuyển KHÔNG hợp lệ EXECUTING ⇒ DECODING (giữ nguyên)
W TextToSpeech: stop failed: not bound to TTS engine
VoiceOverlay ... (vẽ rồi biến mất)
```
**Gốc (2 tầng):** (1) máy trạng thái pha KẸT ở `EXECUTING`, từ chối chuyển về mở-lại-mic cho clarify → session kết thúc thay vì hỏi lại + giữ mic. (2) TTS not-bound → clarify không đọc được. ⇒ **fix off-car**: NO_VERB/không-rõ phải sang CLARIFY (hợp lệ) + giữ mic tới follow-up timeout, KHÔNG kết thúc; + TTS-not-bound đọc clarify. Đúng owner báo "không được dừng khi chưa xong việc".

Findings voice khác: vietmap-selector ("bằng vietmap" nghe méo "vietna" → đuôi không cắt → rác GMaps) · music-reply "bấm play" sai (đã autoplay).

## 6. HƯỚNG MỚI — AUTOMATION (owner chốt 12:07)

Mục tiêu: (1) **trời mưa → tự bật sấy kính**; (2) **có GPS → tự mở dẫn đường đến công ty theo giờ định sẵn**.
- **#1**: trigger = poll `SETTING_FRONT_RAIN_WIPER_SPEED`>1 (§1) · action = defrost write (CAPTEST OK). Build engine off-car.
- **#2**: GPS có (§2) · action = launch nav-intent tới địa chỉ cty theo lịch. Build off-car.
- Engine cần: scheduler + poll loop + UI cấu hình (địa chỉ cty + giờ + ngưỡng mưa). Off-car.

## 7. VIỆC OFF-CAR (gói 1.85)

1. **Fix scripts** sang explicit `-n` component (§0).
2. **Automation engine** #1 (mưa→sấy, trigger 1196425250) + #2 (GPS→nav theo giờ) + UI cấu hình.
3. **Wire route RE'd**: gió-auto → `AC_CTRL_MODE_SET`(0/1) · khóa trẻ em → `CHILDLOCK_*_SET`. Sửa `ac_auto` control.
4. **Wire `pm25_outside`** getter (data có thật).
5. **Voice bug** (ưu tiên): session-chết (phase + TTS-not-bound) → clarify + giữ mic.
6. **Gỡ `hood`** (không ca-pô điện). Sửa nhiệt-cabin/set-temp read route. Getter đọc cốp.
7. **Voice off-car khác**: vietmap tail-strip vô điều kiện · music-reply bỏ "bấm play".
8. **Khóa cửa chính**: đào named setter (nhiều khả năng trim chặn — ghi rõ nếu bất khả).
9. **Camera 360 + góc cam**: RE route (lần on-car sau / thử off-car từ featmap).

## 8. TRẠNG THÁI SAU LƯỢT OFF-CAR 1.85 (cập nhật 2026-09-20, R2.1 atomic)

Đối chiếu từng việc của §7. **Chưa commit** (chờ owner) — code + test đã xanh off-car.

| §7 | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 1 | **Fix scripts sang `-n` tường minh** | ✅ off-car | Sửa **một chỗ**: `k_test` ở `_common.sh` (cả 11 script của bộ đi qua hàm đó) + biến mới `KACHI_TEST_COMP`. [ĐO] `bash -n` sạch 12/12. Kèm **18 dòng lệnh gõ tay** trong 4 doc chạy-trên-xe (`RUNBOOK.md` · `RUN-STEPS.md` · `4-system.md` · `adb-car-tunnel-macos.md`) — chính những dòng owner copy vào xe. **Khoá bằng 2 bài canh** (`TestBridgeSafetyContractTest`, ở `:app` vì module đó đã khai `scripts/vehicle` làm đầu vào gradle ⇒ chạy lại thật khi script đổi): một bài đòi `-n` + chuỗi thành phần **khớp `KachiTestBridge::class.java.name`**, một bài quét cả bộ không cho dựng lại đường ngầm. |
| 2 | **Automation engine** #1 + #2 + UI | ✅ off-car (🚗 buổi mưa + buổi khung giờ) | `specs/kachi-automation.html` T1–T5. Lượt soát tìm thêm **[P2]**: một lần ghi hỏng làm automation **bỏ cả cơn mưa** im lặng (nhận chủ quyền ở nhịp *ra lệnh*, nhịp sau đọc `sấy=tắt`+`owned=true` = đúng dấu hiệu "người lái tự tắt") ⇒ `RainDefrostOwner.unclaim` + `writeBoth` trả kết quả ghi nút TRƯỚC. |
| 3 | **gió-auto** → `Ac.AC_CTRL_MODE_SET` | ✅ off-car | Bind theo **TÊN hằng** (R11), device `BYDAutoAcDevice`. Giá trị **ĐẢO** (bật→0). Nút đổi nghĩa + **nhãn "Gió tự động"** (xe không có nhiệt-auto, §4). Đọc sang datum mới `ac_wind_auto` (`getAcWindLevelManualSign`, 0=AUTO, `readInverted`). |
| 3 | **khoá trẻ em** → `CHILDLOCK_{LEFT,RIGHT}_SET` | ✅ off-car | Thành **HAI** nút (`child_lock` trái · `child_lock_r` phải) vì một `ControlDef` = một feature-id. Giá trị **ĐẢO** (bật→2). Cụm mơ hồ *"khoá trẻ em"* → nút TRÁI (tiền lệ 1.80 *"mở kính"*). "Cả hai bên" = gói lệnh `ActionMacros` — **chưa làm, xin owner duyệt**. |
| 3 | **khoá cửa chính** | ⛔ BẤT KHẢ (tạm) | KHÔNG wire. rc NOT_PROVISIONED + không có `_SET` + named setter X ⇒ nghi trim chặn. Việc trên xe ghi ở `0-PENDING` §E.4. |
| 4 | **gỡ `hood`** | ✅ off-car | Xoá HẲN: registry · 3 bảng `CtlSafetyPolicy` · synonym + dạng có dấu · diễn giải · mục `HIDDEN_FROM_PICKER` · dòng `KachiTheme.iconRes` · **icon xe sinh** (`gen-car.py` + `manifest.json` + `ic_car_top_hood.xml`) · DENYLIST script sweep. [ĐO] 0 literal `"hood"` sống trong mã sản phẩm; `gen-car.py --check` OK 49 tệp. Ô đã lưu tự rụng (`WorkspaceState.sanitized`, có test riêng). |
| 4 | **`pm25_outside`** | ✅ off-car (🚗 1 lượt đọc) | Không có getter riêng — là **ô [1]** của `getPM2p5Value()`. [ĐO nguồn] javadoc BYD + callback `(value_in, value_out)` + car log `in:8 out:22`. Khôi phục bảng `ARRAY_INDEX`. **Tier giữ NEEDS_CAR** tới khi xe in ra cả hai ô. |
| 4 | **nhiệt cài đặt** (`inside_temp`) | ✅ off-car | Gốc ở **tham số**: `area` 0 không phải vùng ĐỌC (javadoc: 1/2/3/4; 0 = `MAIN_DEPUTY` là *type* của đường GHI) ⇒ sentinel ⇒ "—". Đổi `readArg` 0 → 1. |
| 4 | **nhiệt cabin** (`cabin_temp`) | ⏸ CHƯA sửa (có lý do) | Không thiết bị nào phơi getter nhiệt ĐO trong cabin ⇒ nghi `temp=22` chính là setpoint. TODO ghi tại chỗ + `0-PENDING` §D. Không bịa getter. |
| 4 | **đọc trạng thái cốp** | ⏸ ngoài scope lượt này | `tailgate_status` vẫn `getHatchDoorStatus`; [ĐO xe 2026-09-17] đường đọc thật nghi là `getBackDoorOpenedHeight` trên device Setting — cần một lượt đo trước khi đổi. |
| **5** | **voice: phiên "biến mất" khi không hiểu** | ✅ off-car (🚗 nghe giọng thật) | **HAI** lỗi, không phải một. (a) `:core` `VoiceTurnMachine` **thiếu cạnh `EXECUTING → CLARIFYING`** — sơ đồ KDoc + chú thích chỗ gọi + đường đi thật đều nói nó tồn tại, chỉ bảng dữ liệu bỏ sót ⇒ **cả ba** mốc pha của một lượt hỏi-lại đều trượt (dòng `EXECUTING ⇒ DECODING` trong log là mốc thứ ba). (b) **Gốc thật sự giết phiên**: `askAgain` mở lượt nghe **HAI lần** khi máy đọc chưa nối — hợp đồng `VoiceSpeaker.speak` là *"luôn gọi `onDone`, kể cả khi trả `false`"* ⇒ lượt hai bị `VoiceSingleFlight` chối mic, trả rỗng, rơi vào `endsConversation` và **đóng tấm chữ sau 2,5 s giữa lượt nghe 8 s của lượt một**. Vá bằng chốt `compareAndSet` + `!micOpen()`, cả hai đường qua **một** cổng ⇒ TTS hỏng vẫn mở mic đúng một lần (hỏi lại chạy được **không cần tiếng**). ⚠ ghi ra: `canOpenMic` có **0 chỗ gọi** trong mã sản phẩm — cổng "cứng" hôm nay chỉ là khai báo; xin owner quyết có nối. |
| **7** | **voice: chọn app nav khi ASR bóp méo** | ✅ off-car | Thêm **tầng 3** cho `appAfterMarker`: `bySpokenFuzzy` — **neo 4 ký tự đầu** + lệch ≤ 2 ký tự, hai chuỗi ≥ 5 ký tự, so trên chuỗi **ghép liền** (*"vietna"* = *"viet na"*). ⚠ **KHÔNG** cắt đuôi vô điều kiện như lời giao: `BY_APP_MARKERS` (`bang · tren · voi · qua · dung`) đều là từ Việt thật nằm GIỮA tên địa điểm ⇒ cắt bừa làm *"cầu **Bằng Lăng**"* → *"cầu"*, *"**qua** Thủ Đức"* → rỗng. Lý do + đường an toàn hơn (cắt khi việc cắt làm thân câu khớp **sổ địa chỉ**) ở `_handoff/1.85-stage4.md` §0. |
| **7** | **voice: reply nhạc nhắc "bấm Play"** | ✅ off-car | `handedOver` được gọi từ **hai** đường: **watch** (1.75 — `video_id` → `watch?v=` ⇒ tự phát) và **`deliver`** (`MEDIA_PLAY_FROM_SEARCH` ⇒ [ĐO] dừng ở nút Play). Thêm cờ `autoplay` ở **chỗ gọi**, KHÔNG bỏ câu cũ (nó vẫn đúng cho `deliver`, và với Spotify/Zing thì `deliver` là đường **duy nhất**). |

**[ĐO] bằng chứng off-car của lượt này**: 5 module **5001 test / 0 đỏ** (`test --rerun-tasks --continue`) · 13 test mới (`HalWire0920Test`) chạy thật · **5 phép thử phá đỏ ĐÚNG chỗ** (đảo giá trị ghi gió-auto · đảo giá trị khoá trẻ em · đổi chỉ số mảng về [0] · cho `coerceIntAt` lùi về số thuần · trả `inside_temp` về area 0), mỗi lần hoàn nguyên đã kiểm sha256 trùng khít.

**[ĐO] sau lượt VOICE (§7 việc 5 + 7, `_handoff/1.85-stage4.md`)**: 5 module **5066 test / 0 đỏ / 0 bỏ qua** (+21 so với 5045 của lượt automation) · 21 test mới chạy thật (đếm từ JUnit XML) · **6/6 phép thử phá đỏ ĐÚNG ca**, trong đó phép gỡ cạnh `EXECUTING → CLARIFYING` để **bài canh cũ vẫn XANH** — bằng chứng bộ test trước đây không thể bắt được lỗi này.


**[ĐO] sau lượt ĐÓNG GÓI (stage 5, `_handoff/1.85-done.md`)**: 5 module **5073 test / 0 đỏ / 0 lỗi / 0 bỏ qua** (+7 so với 5066 — 2 bài canh script + 3 bài chủ quyền sấy, ×2 biến thể `:app`) · 73/73 task chạy thật · 2 phép thử phá đỏ đúng ca (hoàn nguyên sha256 khớp) · quét bảo mật **CLEAN** (1 hit duy nhất = IP xe trong một doc handoff, đã redact `<car-ip>`) · `apk/Kachi-1.85-release.apk` **vc86** `sha256 ee5a4e2e…`, [ĐO `aapt2`] không debuggable, 0 bề mặt `HalProbeReceiver`. **CHƯA commit/push** (chờ owner).

**CÒN CHỜ XE (không làm off-car được)**: camera 360 + góc cam (§3 — chưa có feature-id, owner hoãn) · khoá cửa chính (§3 — sentinel `NOT_PROVISIONED`, nghi trim chặn) · `pm25_outside` cần **một** lượt đọc in ra cả hai ô của `getPM2p5Value()` · nhiệt cabin (không có getter nhiệt ĐO) · đọc trạng thái cốp.
