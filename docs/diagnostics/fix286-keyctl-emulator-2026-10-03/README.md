# FIX286 · R-KC — phím vật lý gán nút xe: bằng chứng máy ảo (2026-10-03)

> **Trạng thái**: Current · Diagnostics · **Ngày**: 2026-10-03 · **Mục đích**: bằng chứng đo trên máy ảo cho nhóm R-KC của
> spec `docs/specs/kachi-286-field-fixes.html` §3.11 / §4.9 / §9 (phím vật lý gán MỌI nút xe — yêu cầu owner 03/10).

Máy đo: `emulator-5554` (AVD API 29, không có HAL xe). APK: `vehicleTest` dựng từ cây làm việc (gốc `c45d326` + FIX286
lượt 1 + R-KC), cài `install -r` (cùng chữ ký, giữ dữ liệu). Mọi số dưới đây là **[ĐO máy ảo]**; phần trên xe là
**[CHƯA BIẾT]** tới OC-KC1–3 (spec §6.2). Kịch bản ở `tools/` (đường dẫn máy đã thay bằng `<scratchpad>`/`<home>`).

## 1 · Đường phím trên máy ảo (tầng 1 §14)

| Phép đo | Kết quả | Tệp |
|---|---|---|
| `input keyevent 88` (sự kiện TIÊM) | **0** dòng `NavAccess onKeyEvent` | `logs/s1.log` |
| `sendevent /dev/input/event11 1 165 …` (thiết bị `qwerty2`) | `onKeyEvent DOWN keycode=88 (KEYCODE_MEDIA_PREVIOUS)` | `logs/s1.log` |
| `sendevent … 115` / `114` | `keycode=24 (VOLUME_UP)` / `25 (VOLUME_DOWN)` tới `onKeyEvent` | `logs/s2.log` |

Nguồn AOSP cho dòng 1–2 (D-VK6 nâng từ [SUY] lên [ĐO nguồn]): `frameworks/native/services/inputflinger/InputDispatcher.cpp`
r47 `notifyKey` `:2646` gọi `filterInputEvent` ở `:2694-2698` (bộ lọc của Hỗ trợ); `injectInputEvent` `:2842-2884` chỉ
`interceptKeyBeforeQueueing`, không `filterInputEvent`. r34 (`services/inputflinger/dispatcher/InputDispatcher.cpp`):
`:3739/:3791` vs `:3997-4052`. Hạn 500 ms của `onKeyEvent`: `KeyEventDispatcher.java:51,152-153,262-270` (r47 = r34).

## 2 · Học phím + Thêm gán qua UI THẬT (uiautomator)

1. Cài đặt › Nút vật lý › *Học phím mới…* → `sendevent … 115` ⇒ `learned voice keycode=24` → hộp *Đặt tên* “VOLUME UP”
   (`shots/learnvu-1.png`) → *Lưu* ⇒ `voicekey_custom_buttons` = “VOLUME UP (mã 24)”. Tương tự 114 ⇒ “VOLUME DOWN (mã 25)”.
2. *Thêm gán…* → “VOLUME UP (mã 24)” → **bước 2** 8 mục: *Mở ứng dụng · trợ lý · Kính · Cửa & khoang · Đèn · Năng lượng &
   sạc · Khí hậu & không khí · Thân xe · cửa · kính · Giải trí · cụm · HUD* (`shots/bindup-2.png`) → *Khí hậu & không khí*
   → **bước 3** (`shots/bindup-3.png`: Bật/Tắt Lọc bụi · Mát ghế lái: mức kế tiếp/Tắt/Mức 1/Mức 2 · Nhiệt độ ±1 · Gió ±1 · …)
   → *Gió +1* ⇒ prefs `{"k":24,"t":"ctl:fan:+1"}`; VOLUME DOWN ⇒ `ctl:fan:-1` (`logs/s4.log`).
3. Danh sách gán hiện “VOLUME UP (mã 24) — Gió +1” / “VOLUME DOWN (mã 25) — Gió −1” (`shots/binddn-4.png`).

## 3 · Bấm

| Ca | Kết quả | Tệp |
|---|---|---|
| Một lần VOLUME UP (màn chính) | `onKeyEvent DOWN 24` → `voice-key fire → target=ctl:fan:+1` → `KeyCtl phím → ctl:fan:+1 ×1 (ngay)` (cùng ms); `ctl-writes.log` +14 ms: `ctl id=fan m=feature:0x1de0000c args=[5] rc=-`; toast “✗ Đặt Gió = 5 — xe không nhận lệnh” (máy ảo không HAL — đúng câu của giọng nói) | `logs/s5.log` · `shots/press1-toast.png` |
| Núm chậm: 20 lần `sendevent` (mỗi lần ~430 ms — `sendevent` một tiến trình/lệnh) | 20 lệnh HAL (mỗi sự kiện rơi vào cửa sổ riêng — đúng luật ≤ 1 lệnh/400 ms) | `logs/s6.log` |
| **Núm nhanh**: 30 sự kiện / 1,9 s (ghi thẳng `struct input_event` 24 B lên `/dev/input/event11`, 6 đợt × 5) | **5 lệnh HAL** (×1 ngay · ×9 · ×10 · ×5 · ×5 gộp) — tổng **30 nấc**; âm lượng nhạc **8 → 8** (phím gán bị nuốt) | `logs/s7.log` · `logs/burst30-*.{log,json}` |
| Gán đè VOLUME DOWN = *Bật/tắt Kính lái (đảo)* | máy ảo `readState` = `null` ⇒ **0** lệnh `win_lf`; toast “Không đọc được Kính lái — gán Bật / Tắt riêng” (bản đầu 2 dòng `flip-toast.png` ⇒ rút gọn `flip2-toast.png`) | `logs/s8.log` · `logs/s9.log` |
| Gán đè VOLUME DOWN = *Mở Cốp sau* | `ctl id=trunk m=named:voiceCtlBackDoor args=[1]` — máy ảo không có tốc độ ⇒ cổng tốc độ cho qua (fail-open như giọng nói). Ca “xe đang chạy ⇒ từ chối” khoá bằng `KeyCtlSafetyTest` | `logs/s8.log` · `logs/trunk-*` |

(`args=[5]`/`[7]` lặp lại: máy ảo ghi HAL hỏng ⇒ `VoiceControlDispatch` không cập nhật mức lạc quan ⇒ mốc RAM giữ 4, kẹp max 7.)

## 4 · Harness giọng nói (memory: đổi đường phím ⇒ chạy lại)

`scripts/emulator/voice-e2e.sh --only all` trên cùng APK: **T1 108/108 PASS · T2 23/27** — trùng từng ca với mốc FIX286
(`voice-final` của senior review Pass 3), 0 ca đổi kết quả (`logs/voice-e2e-report.md`).

## 5 · Phá thử (đột biến mã → bài đỏ)

`tools/mut.log` + `tools/mut2.log`: 12/12 đột biến hợp lệ ĐỎ (M1–M5, M6b, M7, M8, M9b, M10–M12). Ghi thật: M6 bản đầu chỉ
đỏ vì lỗi biên dịch (không tính, thay M6b); **M9 bản đầu SỐNG** (bài canh của phép đọc-tươi chỉ ghim chuỗi) ⇒ dời luật xuống
`:core` `CarDataDemand.Holder.withSoloIfIdle` có bài chạy thật ⇒ M9b/M10/M11/M12 đỏ.

## 6 · Trả máy ảo

`tar xf` đè tại chỗ + chown 10163/chmod 660/restorecon (lần đầu hỏng vì harness đã `unroot` adbd — `logs/s10.log`; làm lại
dưới root — `logs/s11.log`), HOME một lần, cài lại `apk/Kachi-2.85-release.apk` (sha `c3a7b12f…` khớp `base.apk`), khoá Hỗ
trợ trả đúng chuỗi sao lưu, âm lượng nhạc 15, `adb unroot`. Prefs: 8/9 sha khớp; `clusternav_prefs.xml` lệch đúng khoá
mốc `a11y_proc_start_elapsed` (Hỗ trợ gắn lại dựng tiến trình — cùng ghi nhận của luồng R-SC).

## 7 · Chưa đo / còn mở

- Núm âm lượng / phím 305 trên **xe** có tới `onKeyEvent` không, nhịp sự kiện — **[CHƯA BIẾT]** (OC-KC1; “Học phím mới” là phép đo).
- Đường đọc cốp — **[CHƯA BIẾT]** (OC-KC3: `getDoorState(6)` qua cầu `hal`) ⇒ hôm nay không có “Đảo cốp” (OQ14).
- Cổng tốc độ cốp với số tốc độ thật — chỉ bài chạy thật off-car (`KeyCtlSafetyTest`), chưa đo trên xe (OC-KC2).
