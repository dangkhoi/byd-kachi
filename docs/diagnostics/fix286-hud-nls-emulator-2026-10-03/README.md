# FIX286 R-HUD — nguồn thông báo (nguồn duy nhất của HUD) tự gắn lại: bằng chứng máy ảo

> **Trạng thái**: Current · **Cập nhật**: 2026-10-03 · **Loại**: Diagnostics · **Owner**: dangkhoi ·
> **Mục đích**: số đo máy ảo cho R-HUD (S1 tự gắn lại có cổng công tắc · S2 công tắc/nút báo đúng kết quả · S4 dòng tình
> trạng HUD · S9/S10) — mốc đỏ 2.85 và 2.86 trên cùng kịch bản.
> **Spec**: `docs/specs/kachi-286-field-fixes.html` §3.9 R-HUD · §4.7 · §9 luồng R-HUD · **Backlog**: `FIX286-HUD-NLS`
> · Điều tra gốc (ngoài repo, phiên điều phối): `hud-inv.md` + phản biện `hud-ref.md` (H1+).

Mức bằng chứng theo CLAUDE.md §2: `[ĐO]` = output thật của lần chạy này · `[SUY]` = đọc nguồn, chưa chạy · `[CHƯA BIẾT]`.

Máy ảo `emulator-5554` (AVD `clusternav10`, Android 10 / API 29). Bản **vehicleTest dựng từ cây làm việc 03/10** (FIX286
lượt 1 + R-HUD, chưa commit, versionName vẫn 2.85 — không bump), sha256 `d42464ef…e9f1a3` — dựng TRƯỚC lượt dời
`HudWriteRecord` sang `:core` (đổi package, không đổi hành vi [SUY]; spec §9 D-HUD9). Mốc đỏ = `apk/Kachi-2.85-release.apk`
(sha `c3a7b12f…ea3a`). Prefs sao lưu `tar` (adb root) TRƯỚC; trả bằng `am force-stop` → `tar xf` đè tại chỗ → `chown 10163:10163`
→ `chmod 660` → `restorecon` → `am force-stop` (không `rm -rf`) — **9/9 tệp trùng sha**; HOME một lần; cài lại 2.85 (`base.apk`
sha `c3a7b12f…` khớp); `adb unroot`; kênh `tcpip 5555` + `reverse` lên lại; NLS Kachi Live lại. Hàm đo: `hudlib.sh` (thư mục này).

## 0. Giả lập "đã CẤP mà KHÔNG GẮN" — và giới hạn của nó

Bẫy hiện trường H1+ (thẻ kẹt trong `mServicesRebinding` sau lượt BYD giết — AOSP r47 `ManagedServices.java:1121,1143-1158`)
**không dựng lại được** trên máy ảo: `am force-stop` luôn gửi `PACKAGE_RESTARTED` ⇒ NMS `rebindServices`; `kill -9` ⇒ dịch vụ
gắn được khởi động lại cùng tiến trình mới. Giả lập dùng (lệnh trong `hudlib.sh`):

1. `cmd notification disallow_listener <Kachi>` — NMS gỡ duyệt + tháo gắn [ĐO: `Disallowing notification listener …`].
2. Chờ NMS tự ghi bản gương Settings (bất đồng bộ), rồi `settings put secure enabled_notification_listeners` trả bản gương về
   như cũ (có Kachi) **mà không rebind** — NMS không nghe khoá này (r47 `SettingsObserver` chỉ theo dõi badging/light/rate/bubbles,
   `NotificationManagerService.java:1372-1395`) ⇒ "đã cấp" theo bản gương (cổng của Preflight + `NlsHeal`), dump Live KHÔNG có Kachi.
3. `kill -9` tiến trình (giống lượt BYD: không `PACKAGE_RESTARTED`) rồi gọi HOME tường minh (trên máy ảo app khách của ô rơi về
   display 0 và che HOME; trên xe Android tự dựng HOME 0,25–0,33 s sau lượt giết).

**Giới hạn [ĐO]**: NMS ghi đè bản gương ở MỖI lần lưu chính sách (tiến trình mới tạo kênh thông báo…) ⇒ trong vài giây bản gương
lại nói "chưa cấp" và **Preflight tự cấp lại** (`Preflight: thiếu: notif_listener | tự xin` → `Allowing …`) — một lượt M2 bị nhiễm
đúng như vậy (đã chạy lại, có `Preflight: đủ quyền` làm chứng ở lượt dùng). `hold_mirror` ghi lại bản gương liên tục trong
cửa sổ đo. Hình dump của ca hiện trường (duyệt CÓ, Live KHÔNG) chụp riêng bằng `pm disable`/`pm enable` component (chỉ để có
hình, đã trả) — fixture `core/src/test/resources/diagnostics/dumpsys-notification-emulator-2026-10-03-approved-not-live.txt`.
Đường chữa (disallow → allow) đi qua `setPackageOrComponentEnabled` → `rebindServices` ở CẢ HAI hình [SUY nguồn r47 `:470-492`].

## 1. Kết quả

| Ca | Bản | Kịch bản | Kết quả | Mức |
|---|---|---|---|---|
| **B1** mốc đỏ S1 | 2.85 | Công tắc BẬT, NLS rớt, lượt giết kiểu BYD, chờ 45 s + 2 nhịp watchdog | Live = 0 suốt; watchdog chỉ `requestRebind` — `logs/B1-285-no-heal.log` | [ĐO] |
| **M1** công tắc TẮT | 2.86 | Như B1 nhưng `enabled=false` | `nls READY → OFF (không đọc, không ghi)` 15,5 s sau khi tiến trình bật; 2 × `WATCHDOG → OFF`; 0 dòng Disallowing/Allowing; Live = 0 — `logs/M1-off.log` | [ĐO] |
| **M2** S1 READY | 2.86 | Như B1 (BẬT) | Tiến trình bật 04:20:18,08 → 04:20:33,96 `Disallowing` → 1,56 s → `Allowing` → `nls READY → HEALED before=NOT_LIVE after=LIVE fired=true fires=1/3`; `Preflight: đủ quyền` (không nhiễm) — `logs/M2-on-ready.log` | [ĐO] |
| **M3a** một lần mỗi tiến trình | 2.86 | Cùng tiến trình M2, NLS rớt lần hai, 2 nhịp watchdog | `WATCHDOG → HEALED (không đọc, không ghi)` ×2; Live = 0 (chủ ý: đã chữa một lần trong tiến trình này) — `logs/M3a-once.log` | [ĐO] |
| **M3b** S1 watchdog | 2.86 | Tiến trình mới: READY thấy `BOUND`; NLS rớt; 1 nhịp watchdog | `WATCHDOG → HEALED before=NOT_LIVE after=LIVE fired=true fires=1/3` — `logs/M3b-watchdog.log` | [ĐO] |
| **M4c** S2 công tắc | 2.86 | NLS rớt; Cài đặt › Dẫn đường: gạt TẮT (0 lượt NlsHeal) → BẬT | chờ callback tự nhiên ~4,5 s → `nls SWITCH → HEALED before=NOT_LIVE after=LIVE`; toast *"Kết nối lại nguồn dẫn đường: đã gắn lại"* — `logs/M4c-switch.log`, `shots/s2-switch-toast-healed.png` | [ĐO] |
| **M4a** S2 nút | 2.86 (bản trước lượt sửa nhật ký) | NLS rớt; bấm *Kết nối lại nguồn dẫn đường* | `nls BUTTON → HEALED … after=LIVE`; dòng tình trạng đổi sang "đã gắn" — `logs/M4a-button-apk-truoc.log` | [ĐO] |
| **B2** mốc đỏ S2 | 2.85 | Bỏ `adb reverse` (kênh `env PORT_CLOSED`); bấm *Kết nối lại* | `NavConnect: reconnect qua dadb xong` dù không lệnh nào chạy; Live = 0 — `logs/B2-285-deny-xong.log` | [ĐO] |
| **M4b** S2 nút, kênh hỏng | 2.86 | Bỏ `adb reverse`; bấm *Kết nối lại* | `nls BUTTON → SHELL_FAILED before=- after=- fired=false`; toast *"…: kênh lệnh của xe lỗi"*; dòng "Gắn lại gần nhất … : kênh lệnh của xe lỗi" — `logs/M4b-noshell.log`, `shots/s2-button-toast-shell-failed.png` | [ĐO] |
| **S4** dòng tình trạng | 2.86 | Mở Cài đặt › Dẫn đường khi NLS rớt / đã gắn | *"HUD — nguồn thông báo: CHƯA gắn (hệ thống, lúc 04:22:50)"* (đọc dump lúc trang hiện) · *"Ghi tới xe lúc 04:21:40: không mở được thiết bị cụm"* (máy ảo không có HAL) · *"Gắn lại gần nhất lúc … (tự động): đã gắn lại"* · câu cố định *"HUD kính lái chỉ nhận dẫn đường từ Google Maps — VietMap và Waze không lên HUD."* — `shots/s4-hud-rows-*.png` | [ĐO] |

Hẹn NMS 10 s sau `binding died` [ĐO máy ảo, lượt cài đè]: `binding died` 03:58:15,461 → `Not registering … is already bound`
03:58:25,462 — khớp `ON_BINDING_DIED_REBIND_DELAY_MS = 10000` (r47 `ManagedServices.java:97`).

## 2. Lỗi tìm ra trong lượt đo (đã vá trước bản cuối)

- `Run.fired` của lượt bấm tay nói "đã bắn" khi kênh hỏng ngay lệnh đầu (dadb nối lười ⇒ hỏng ở chính lệnh cặp) ⇒ lượt nào cũng
  đọc dump TRƯỚC (một lệnh chỉ-đọc), kênh hỏng thì hỏng ở đó; bài `NlsHealShellTest.kenh hong ngay lenh doc thi CHUA ban`.
- Lượt chỉ-đọc của Cài đặt ghi đè "lượt gắn lại gần nhất" ⇒ tách `lastAction` / `lastTruth`.
- Giả lập: zsh hiểu `$C:c…` là bộ sửa biến ⇒ bản gương đầu tiên hỏng (đã dùng `${C}`); nhiễm Preflight (§0).

## 3. Chưa đo

- Định dạng dump trên ROM BYD (DiLink 3 A10 / DL5 A12) — [CHƯA BIẾT]; nguồn AOSP r47 + r34 cùng khuôn [ĐO nguồn]. Khuôn lạ ⇒
  `UNREADABLE` ⇒ KHÔNG hành động (an toàn), dòng tình trạng nói "không đọc được trạng thái hệ thống (lúc …) — Kachi
  không tự gắn lại" (senior review FIX286 Pass 4 · P3: trước đó ca này rơi vào "chưa kiểm được (kênh lệnh của xe chưa
  sẵn)" — sai nguyên nhân đúng ca OC-HUD1 cần chụp; nay là trạng thái riêng `NavHudStatus.Source.UNREADABLE`).
- HUD SL6 có lên lại sau khi NLS gắn lại — [CHƯA BIẾT] (đường HAL không đổi; H7/H8 của phản biện vẫn mở).
- Ca "đổi hồ sơ" lúc đang chạy với `enabled` theo hồ sơ — không đo (cổng đọc `Prefs.enabled` ngay lúc chạy).
