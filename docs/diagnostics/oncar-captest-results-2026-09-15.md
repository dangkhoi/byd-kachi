# Kết quả KIỂM TRA TỪNG CHỨC NĂNG trên xe — 2026-09-15 (CapTest lần đầu)

- **Ngày:** 2026-09-15 · **Chủ:** dangkhoi · **Xe:** DiLink3 (DL3), Android 10, Kachi uid 10135 (KHÔNG platform-sign).
- **Nguồn:** owner chạy công cụ *Kiểm tra từng nút xe* trên xe thật rồi báo miệng ("Chốt lại như sau"). Đây là **[ĐO trên xe]** — mức bằng chứng cao nhất cho hành vi HAL thật.
- **Quy ước:** ✅ chạy · ⚠ chạy một phần · ❌ không tác dụng / không đọc được. Owner chốt: **mục KHÔNG nêu = không đọc được thông tin & bấm không tác dụng.**

## Bảng kết quả [ĐO trên xe 2026-09-15]

| Chức năng | id | Loại | Kết quả | Ghi chú owner |
|---|---|---|---|---|
| Kính 4 cửa | `win_lf/win_rf/win_lr/win_rr` (+`windows_all`) | COVER | **MỞ ✅ · ĐÓNG ❌** | "4 kính mở được không đóng được" |
| Lọc ngay | `pm25_clean_now` | BUTTON | ✅ | "bấm lọc ngay ok" |
| PM2.5 (thông số bụi) | (info đọc) | INFO | ❌ | "thông số chưa hiển thị" |
| Sấy kính | `defrost` | TOGGLE | ✅ | "sấy kính ok" |
| % pin | (info) | INFO | ✅ | "hiện đúng" |
| Tổng ODO | (info) | INFO | ✅ | "hiện đúng" |
| Rèm che nắng | `sunshade` | COVER | ⚠ **bấm mở CHÚT XÍU · giữ ❌** | "bấm ăn mà mở chút xíu. Bấm giữ chưa được" |
| Đèn đọc sách | `readl` | TOGGLE | ⚠ **chế độ theo-cửa ✅ · on/off tay ❌** | "chọn chế độ mở cửa tắt/mở đc; chọn on off bên trong ko đc" |
| Số VIN | (info) | INFO | ✅ | "lấy được số VIN" |
| **Mọi mục khác không nêu** | — | — | ❌ | owner: không đọc được + bấm không tác dụng |

## Phân tích gốc rễ + hướng vá (đối chiếu RE + code)

### 1. Kính mở-được-đóng-không — nghi ĐÚNG giá trị state [SUY cao]
- Code: `win_*` → `setBodyWindowCtrlState(window, primary)` với `primary` = index COVER (**Đóng=0, Mở=1**) — `HalBindingTable.writeArgs` dòng 156-157.
- ⇒ Mở gửi `state=1` (chạy), Đóng gửi `state=0` (**no-op**). Mọi lệnh THÂN XE khác trong cùng bảng dùng **1/2** không phải 0/1: `trunk` mở=1/đóng=2, `lock` 2/1, `rain_close` 1/2, `seatc/seath` 2/1.
- **Giả thuyết:** `setBodyWindowCtrlState(window, state)` dùng **state 1=mở, 2=đóng** (hoặc down/up), nên Đóng phải gửi 2. Hiện gửi 0 ⇒ câm.
- **Cần chốt [CHƯA ĐO]:** giá trị state thật của `setBodyWindowCtrlState` trên framework.jar DL3 (Android 10). `com/byd/feature/body/Body.java` (fw-2602/Android 12) dùng API atom `0x0780A01C…` khác thế hệ — KHÔNG chắc khớp DL3.
- **`windows_all`** (`setAllWindowState`) cũng gửi `primary×4` (0/1) ⇒ cùng bệnh nếu đúng convention.

### 2. Rèm che nắng — bấm mở chút, giữ không được [SUY]
- Code: `sunshade` feature `1330642984` → `writeArgs` else-branch `intArrayOf(primary)` (0/1).
- "Mở chút xíu rồi dừng" ⇒ có thể lệnh là **momentary/nhích** (mỗi phát nhích một đoạn), không phải "mở hết". RE fw-2602 `CarSettingsConfig`: `HOLD_SWITCH_FOR_WINDOW_DOWN/UP_VISIBLE` ⇒ có khái niệm **giữ để chạy tiếp** (hold-switch). Nút COVER hiện chỉ bấm-một-phát ⇒ không mô phỏng "giữ".
- **Cần chốt [CHƯA ĐO]:** feature 1330642984 nhận giá trị gì để "mở hết" (0-100%? enum? cần lặp lệnh?).

### 3. Đèn đọc — chế độ theo-cửa được, on/off tay không [SUY]
- Code: `readl` `halDevice=BYDAutoSettingDevice` feature `1330643002` (SET_INSIDE_LIGHT_STATE_SET), TOGGLE gửi 0/1.
- Đèn đọc cabin thường có **≥3 trạng thái**: OFF / ON / theo-cửa (auto). TOGGLE 0/1 chỉ chạm 2 giá trị. Owner thấy "chế độ theo-cửa được, on/off không" ⇒ giá trị 0/1 hiện đang trúng nhánh "theo cửa" chứ không trúng ON/OFF tay.
- **Cần chốt [CHƯA ĐO]:** enum trạng thái đèn đọc (vd 0=off,1=on,2=auto-door?).

### 4. PM2.5 số liệu không hiển thị — thiếu đường ĐỌC [SUY]
- `pm25_clean_now` (ghi) chạy ✅ ⇒ cổng PM2P5 GHI ổn. Nhưng đường ĐỌC giá trị bụi (µg/m³) chưa nối/sai ⇒ `telemetryText` cho PM2.5 trả rỗng.
- **Cần:** method đọc PM2.5 (getPm2p5Value?) trên `BYDAutoPM2p5Device`.

### 5. "Mọi mục khác không tác dụng" — cần đọc log để phân loại
- Rất nhiều control (đèn pha/DRL, gạt mưa, camera 360, khoá trẻ em, ghế nhớ, giới hạn sạc, gương…) không chạy. Đây khớp nợ RE `byd-hal-permission-RE-2026-09-14.md`: 9 control [SUY]/[CHƯA BIẾT] route feature-id có thể sai HOẶC cổng chữ ký thiết bị đích chặn.
- **Cần [CHƯA ĐO]:** `captest-report.txt` + `usage-*.log` trên THẺ (`/sdcard/Android/data/com.byd.launcher/files/kachi-logs/`) — có feature-id + rc/exception từng lệnh ⇒ phân loại "route sai" vs "cổng chữ ký chặn". Owner chưa gửi log; `adb pull` để lấy.

## Việc tiếp (theo kỷ luật §14: chốt giá trị TRƯỚC khi ship)
1. Lấy `kachi-logs/` về (report + usage) → đọc rc/exception mỗi lệnh fail.
2. Mine framework.jar DL3 cho state-enum: `setBodyWindowCtrlState`, `setAllWindowState`, feature 1330642984 (rèm), 1330643002 (đèn đọc), method đọc PM2.5.
3. Vá theo evidence, **không đoán** (đã hứa owner). Ưu tiên KÍNH-ĐÓNG (rõ nhất + an toàn: state 0→2).

---

## CHỐT KÍNH-ĐÓNG — [ĐO trực tiếp trên xe 2026-09-15, bản 1.61, T-BRIDGE lệnh `hal` mới]

Owner báo: *"lần đầu mở, đóng OK, lần 2 thấy mở nhưng không đóng"* → nghi HAL sai. **ĐO bác bỏ giả thuyết đó.**

**Công cụ mới:** thêm lệnh T-BRIDGE `hal` (đọc getter + gọi named-method HAL thô, gated test-mode + `auto_confirm` cho ghi) — `app/.../testbridge/TestBridgeHal.kt`. Vì `ctl` chỉ bắn value đã map (kính mở=1/đóng=2), không đọc được getter cũng không thử state khác. `hal` cho đo tất định (đọc `getWindowOpenPercent` = vị trí THẬT, không cần mắt owner).

**Bằng chứng (kính TT, window=1):**
| Bước | Lệnh | rc | `getWindowOpenPercent(1)` |
|---|---|---|---|
| CK1 mở | `setBodyWindowCtrlState(1,1)` | 0 | **98** |
| CK1 đóng | `setBodyWindowCtrlState(1,2)` | 0 | **0** |
| CK2 mở | `setBodyWindowCtrlState(1,1)` | 0 | **98** |
| CK2 đóng | `setBodyWindowCtrlState(1,2)` | 0 | **0** |

**Cả 4 kính** mở (97-98%) rồi đóng (**0%**) qua state=2. Đường `ctl`→`actByKind`→`cover()` (đúng đường nút UI) cũng đóng OK cả 2 chu kỳ. `getWindowPermitState()`=0 suốt (không đổi sau chu kỳ).

⟹ **[ĐO chốt] `setBodyWindowCtrlState(w,2)` đóng tin cậy MỌI kính, MỌI lần. HAL + writeArgs + actByKind ĐÚNG.** Enum DL3 xác nhận `WINDOW_CLOSE=2` (jadx-tmap `BYDAutoBodyworkDevice.java:373`); OpenBYD `CarControlImpl.setWindow` (app chạy được) gọi y hệt một lệnh, không sequence/permit-check.

### Root-cause thật: BUG CÔNG CỤ CapTest — chỉ test được chiều MỞ
`CapabilityTestPlan.items()` đặt `runArg = HalBindingTable.defaultPrimary(c)` = **1 (mở)** cho MỌI control COVER. Màn "Kiểm tra từng nút xe" chỉ có MỘT nút "Chạy" bắn `runArg` ⇒ với kính luôn gửi **mở**, **không có đường gửi đóng**. Owner mở được nhưng tool không gửi đóng → tưởng *"đóng không được"*. (Kính đóng thật sự vẫn OK — đo ở trên; và thanh nút điều khiển thật/dock có sẵn nút Đóng chạy đúng.)

**Fix (generic, CLAUDE.md §7):** `CapTestConsole.addRunButtons` — control khai ≥2 chiều (`displayArgs` ≥ 2: kính "Đóng"/"Mở", SELECT nhiều mức) render **mỗi chiều một nút** (`arg`=chỉ số). Control một chiều giữ nút "Chạy" đơn. Bản **1.62**.

### YouTube scale bé trong ô — [ĐO 2026-09-15]
`force_resizable_activities=1` + `enable_freeform_support=1` **đã set** trên xe. Root-cause (Explore agent): app fixed-orientation/non-resizable rơi **size-compat mode** (WM vẽ `mSizeCompatScale<1`, pillarbox) vì cờ force_resizable đọc lúc BOOT — **cần ignition off/on mới hiệu lực**. Đường VD→surface của launcher là 1:1, vô can. Fix: (a) một lần tắt/mở nguồn để cờ live; (b) guard độc-lập-reboot: đo `DisplayParse.sizeCompatScale` sau launch, nếu <1 thì resize/recreate VD theo bounds thật. `VdAppHost.kt:104` (VD size==surface), `:152-153` (khoá xoay), `DisplayParse.kt:137-160` (cơ chế size-compat).

### Nợ còn (chưa đo lại): "nhiều số vô nghĩa" (info reads)
Owner: *"nhiều số lấy được nhưng vô nghĩa"*. Chưa sweep raw. Việc tiếp: dùng `hal get` sweep các telemetry PROVEN, so đơn vị/scale/feature-id với thực tế → sửa scale/route sai. Chưa làm turn này.
