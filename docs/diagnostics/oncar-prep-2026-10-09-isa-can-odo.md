# Chuẩn bị buổi xe — ISA 0x237 · nghe CAN 0x43F · đọc lại Odo/VIN

> **Trạng thái**: Prep (chưa chạy trên xe) · **Cập nhật**: 2026-10-09 · **Chủ**: dangkhoi
> **Xe đích**: Seal / Sealion 6, DiLink3, fw `2606` (OTA V2.0.4). **Mọi IP dùng `<vehicle-ip>`** — không ghi IP thật.
> **Liên quan**: backlog `ISA-NATIVE-SIGN` (ĐÓNG/BLOCKED 08/10) · `HUD-ROADNAME-OEM-CADENCE` · `VOICE-CLARIFY-SO-NAO` (mới) ·
> `docs/diagnostics/oncar-seal-2026-10-09-hud-isa-power.md` · `firmware/fw-2606/RE-2606-findings.md §1.3`.
> Scripts: `scripts/vehicle/isa-0x237-inject-2606.sh` · `scripts/vehicle/can-0x43f-listen-2606.sh`. Log ra `logs/` (ngoài git).

Ba việc độc lập, chạy được trong một buổi. Mỗi việc nêu rõ **được phép làm gì khi chưa có owner** và **cần owner/điều kiện gì** để sang bước ghi.

---

## B1 — Bơm giá trị biển/đèn "dự báo giới hạn tốc độ" vào cụm (khung CAN `0x237`)

**Mục tiêu**: đẩy một số km/h vào ô GỐC của cụm (đèn "dự báo giới hạn tốc độ" mới của fw 2606) thay vì Kachi tự vẽ — đóng câu còn mở duy nhất của `ISA-NATIVE-SIGN`.

### Cơ chế cụm đọc giá trị — `[ĐO disasm fw 2606]`

`BusinessUi1::updateSpeedLimitValue` @`0x13254c` trong `firmware/fw-2606/_libs/lib64_libBydDataSource.so`:

- Cổng hiện đèn (tất cả phải đúng, nếu không ⇒ ghi item 587=2, 588=0 = ẩn):
  - `0x12D0002A` (BODYWORK mức nguồn, GetData_UINT8) `== 3` (xe ON);
  - cờ nội bộ `+0x2a3 == 1` — cờ này = `GetData_UINT8(0x4320000E)` ghi ở `BusinessUi1::updateSlaFunctionEquipment` @`0x13ae50` (CAN `0x4320000E` = "SLA trang bị");
  - cờ `+0x10cf == 0`.
- Giá trị `v = GetData_UINT8(0x2370002E)`, online `= GetData_UINT8(0x23700000)`; cụm hiện khi `online == 1` và `v−2 ≤ 0x1c` (tức `v ∈ 2..30`):
  - `v ∈ 2..25` ⇒ km/h `= 5·v − 5` (⇒ **`v=11` ⇒ 50 km/h**, `v=21` ⇒ 100);
  - `v ∈ 26..29` ⇒ `10·v − 130`; `v == 30` ⇒ mã đặc biệt 82; còn lại ⇒ ẩn.
- Ghi ra data-item **587/588** (`SetDataItem_INT`). `[ĐO]` chỉ là ghi RAM của tiến trình cụm — **không** `SetConfig_UINT8`/`saveConfigXML` ⇒ **không bền**.

### `TEST_SIMULATE_DOWN 0xAA00020F` làm gì / persistence / restore

- `0xAA00020F` = `BYDAutoTestDevice.TEST_SIMULATE_DOWN_SET` `[ĐO scratchpad dicar Test.java:100; TestMapper.java:72]`. Đường cộng đồng/ClusterDebug: `BroadcastReceiverCAN` → `bufferDataValue` → `BYDAutoTestDevice.getInstance().set({-1442840049=0xAA00020F}, ev)` `[ĐO docs/_handoff/hud-cluster-injection-findings-2026-08-10.md:253,552]`. navopen tương đương: `setbytes test AA00020F <hex,hex,…>` (`NavOpen.java:222-233`, `resolveDevice("test")→BYDAutoTestDevice` `:376`).
- **Nó tới đâu sau HAL**: `[CHƯA BIẾT]`. `lib64_libBydDataSource.so` có `CanHandleSimulatorReadThread` đọc node `spi-simulator` (`/storage/vmserial_0`) rồi `CanReadThreadBase::InputSimulatorData` — tức có đường "bơm khung giả vào luồng đọc CAN của cụm", nhưng **chưa xác nhận** `0xAA00020F` nối vào node này hay vào MCU thật, và khuôn byte chính xác. Khuôn script dùng `[ĐOÁN]` = `[featureId BE4][len1][data]` = `23 70 00 2E 01 <v>` (giống `wholeFrame` cộng đồng, id nằm trong byte).
- **Persistence**: `[SUY mạnh]` chỉ RAM. (a) giá trị cụm đọc = data-item 587/588 ghi RAM (trên); (b) khung `0x237` THẬT đang online `[ĐO xe 08/10]` ⇒ khung thật kế tiếp **ghi đè** giá trị bơm ⇒ phải lặp (`HOLD`); (c) power-cycle/reboot xoá sạch.
- **Restore (chính xác)**: ngừng bơm → bơm **mã ẩn** `0x2370002E = 0` một lần (nhánh "ẩn" ở disasm) → nếu còn: **tắt máy / khởi động lại đầu xe** (khung thật hoặc reboot đưa về giá trị SLA thật). Script làm đúng trình tự này trong `trap`.

### Việc trên xe
- **Chưa cần owner** (chỉ đọc): `VEH=<vehicle-ip>:5555 ./scripts/vehicle/isa-0x237-inject-2606.sh` (thêm `DOORA=1` để đọc bố cục bit 0x237 trong `/collect2/byd_datasource_config.xml`). In: mức nguồn, 0x237 online, giá trị 0x2370002E hiện thời, SLA-trang-bị.
- **Cần owner + ĐỖ** (ghi): `PHASE=inject CODE=11`. Script tự: cổng số P (`getCurrentGear==3`) + tốc độ 0 (`getCurrentSpeed`), **DỪNG nếu đọc không ra** (CLAUDE.md §4 — không ghi khi không chắc đang đỗ); gõ `ISA237` xác nhận; bơm `HOLD` lần; hỏi người nhìn cụm; đọc lại; `trap` dọn.

### Verdict B1
**READY-TO-TEST (đọc an toàn; ghi cần owner + đỗ).** Cơ chế đọc của cụm đã chốt tới `file:line`. Đường bơm là `[ĐOÁN]` (chưa từng chạy trên xe owner) — nên B1 là *thử nghiệm có đường trả*, không phải đường ship. Nếu bơm không lên: nhiều khả năng `0xAA00020F` không đi tới nguồn `GetData_UINT8` của cụm trên trim này (giống mọi id SLA đã inert 08/10) ⇒ kết luận ISA-native vẫn BLOCKED, giữ biển Kachi tự vẽ.

---

## B2 — Nghe khung CAN điều hướng `0x43F` (`sendRegTable` có an toàn không?)

**Câu hỏi owner**: `canmon` của NavOpen cần `sendRegTable` (nạp bảng thanh ghi MCU) — **ghi gì, đâu, bền không, trả thế nào?**

### `sendRegTable` ghi gì / đâu — `[ĐO nguồn]`
- `NavOpen.canmon`/`sendRegTable` → `BYDAutoVehicleDataDevice.sendRegisterTable(3, table)` `[ĐO NavOpen.java:592-630]`.
- `sendRegisterTable(type, table)` `[ĐO framework.jar 2606 → BYDAutoVehicleDataDevice.java:66-83]`: `enforce BYDAUTO_VEHICLE_DATA_SET`; `type 3 (DATA_TYPE_BIG_DATA)` ⇒ `super.set(1048, 0xAA000022 = -1442840542, table)`. **Không** ghi file, **không** `SetConfig`/`saveConfigXML`, **không** sửa `/collect2`. Chỉ nạp "bảng id cần theo dõi" vào **thanh ghi RAM của MCU** (mỗi id = 8 byte `[canid BE4][sub][chan][mode][flag]`).

### Bền không / trả thế nào — `[ĐO nguồn]`
- **Không bền qua tắt máy/ngủ MCU** `[SUY mạnh có cơ sở]`: `CanDataCollect.apk` (persistent, `sharedUserId=android.uid.system`) `[ĐO CanDataHandle.java:303-332 + CanDataCollectService.java:269-287]` tự **nạp lại** bảng của nó mỗi lần MCU thức (`onMcuStatusChanged MCU_WAKE`) hoặc MCU đòi (`onNeedRendRegisterTable`). Bảng sống trong MCU đang thức, mất khi ngủ, và **chủ thật nạp lại** ở lần thức kế ⇒ reboot/tắt máy = về gốc.
- **Đường xoá tường minh**: `CanDataHandle.notify_hal_remove_collect_id_conf` gửi bảng `{00 00 03 D5 00 03 00 03}` `[ĐO CanDataHandle.java:331]`. `canmon` của navopen **tự gửi đúng bảng này** khi kết thúc `[ĐO NavOpen.java:616]`.

### Verdict B2
**SAFE** (có đường trả, chủ thật tự khôi phục, không chạm file/bus, chỉ đăng ký đọc). Script: `scripts/vehicle/can-0x43f-listen-2606.sh` — nghe `0x43F` (mặc định 30 s), `trap` + navopen tự gửi bảng xoá; chạy được cả khi đỗ/đang lái (chỉ đọc). Không cần test-mode (navopen chạy uid shell qua dadb).
> Lưu ý thực thi: nếu `BYDAutoBigDataDevice`/`VehicleDataDevice getInstance` trả `null` (server gate) thì canmon in `BigDataDevice null` và không nghe được gì — đó là giới hạn quyền, không phải rủi ro.

---

## B3 — Đọc lại "Số nào — Odo tổng hay Số VIN?"

**Mục này là gì** `[ĐO backlog + spec]`: lỗi **hỏi lại của trợ lý giọng nói**, không phải HAL. Câu `"mở nep leag vào ô số một"` (tên app ngoại "Netflix" → ASR ra "nep leag") từng bị parser coi là câu hỏi mơ hồ và hỏi lại sai *"Số nào — Odo tổng hay Số VIN?"* — chữ "số" (của "ô số một") kéo vào hai datum `odometer` ("Odo tổng", `BYDAutoStatisticDevice.getTotalMileageValue`) và `vin` ("Số VIN", `BYDAutoBodyworkDevice.getAutoVIN`) `[ĐO TelemetryRegistry.kt:143,422]`.
- Nguồn: `docs/specs/kachi-290-voice-app-names.html:513` (ghi "đã sửa chưa — CHƯA BIẾT, chạy `say` máy ảo một câu để chốt"); cùng họ lỗi với `VoiceClarify.ambiguity` `[ĐO VoiceClarify.kt:210]` đã vá cho câu lốp 09-18 (`VoiceClarifyQuestionTest`, `VoiceLogCases0918Test`).
- **Đây KHÔNG cần xe** — là bài off-car/máy ảo. Nhưng task yêu cầu lệnh đọc cho buổi xe, nên để **lệnh chỉ-đọc** xác nhận hành vi trên xe thật (dưới). Tuyệt đối không ghi.

### Lệnh chỉ-đọc cho buổi xe (qua cầu kiểm thử, cần test-mode BẬT)
Chế độ kiểm thử: Kachi › Cài đặt › Hệ thống & quyền › Nâng cao (người trên xe bật; tự tắt 60 phút). `BRIDGE=com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge`.

```sh
VEH=<vehicle-ip>:5555
ADB=~/Library/Android/sdk/platform-tools/adb
B="com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge"
A="com.byd.launcher.TEST"

# 1) Câu đang nghi: tên app ngoại + mệnh đề ô ⇒ KHÔNG được hỏi "Số nào — Odo/VIN"
"$ADB" -s $VEH shell am broadcast -n $B -a $A --es cmd say --es text "mở nep leag vào ô số một"
# kỳ vọng: reply = OpenApp(...) hoặc câu gợi ý "dạy tên app", KHÔNG phải câu hỏi Odo/VIN
"$ADB" -s $VEH shell am broadcast -n $B -a $A --es cmd say --es text "mở netflix vào ô số hai"

# 2) Câu đọc Odo và VIN THẬT phải vẫn chạy đúng (không regress)
"$ADB" -s $VEH shell am broadcast -n $B -a $A --es cmd say --es text "odo tổng bao nhiêu"
"$ADB" -s $VEH shell am broadcast -n $B -a $A --es cmd say --es text "số khung xe"      # VIN

# 3) (chỉ đọc HAL) đối chiếu giá trị nguồn — không ghi
"$ADB" -s $VEH shell am broadcast -n $B -a $A --es cmd hal --es op get --es dev BYDAutoStatisticDevice --es m getTotalMileageValue
"$ADB" -s $VEH shell am broadcast -n $B -a $A --es cmd hal --es op get --es dev BYDAutoBodyworkDevice --es m getAutoVIN
```

Đọc kết quả ở `data="…"` của `am broadcast` (hoặc `adb pull` tệp trong `files/test/`). `say` đi ĐÚNG đường bộ-hiểu-lệnh của ô *Gõ lệnh chữ* `[ĐO voice-e2e.sh:4,305]` — nên nó xác nhận parser, không phải ASR.

### Verdict B3
**READY (off-car là chính; lệnh xe chỉ để xác nhận).** Nếu câu (1) vẫn ra hỏi Odo/VIN ⇒ ghi lại lỗi còn sống vào backlog `VOICE-CLARIFY-SO-NAO`, **không** vá lẫn vào lô khác. Đề nghị: chạy thử off-car bằng `say` trên máy ảo *trước* buổi xe (không chiếm emulator của agent khác — dùng một AVD rảnh) để biết còn lỗi không.

---

## Tóm tắt điều kiện an toàn

| Việc | Đọc (không owner) | Ghi / rủi ro | Đường trả |
|---|---|---|---|
| B1 ISA 0x237 | ✅ mức nguồn/online/giá trị | ⚠ bơm `0xAA00020F` — cần **owner + ĐỖ** (P, 0 km/h) | bơm mã ẩn 0 → reboot; giá trị chỉ RAM |
| B2 CAN 0x43F | ✅ nghe (đăng ký bảng tạm) | nạp bảng theo dõi RAM MCU | navopen tự gửi bảng xoá + CanDataCollect nạp lại |
| B3 Odo/VIN | ✅ `say` + `hal get` (chỉ đọc) | không ghi | — |
