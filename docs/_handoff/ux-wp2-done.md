# WP2 — UX trạng thái control · XONG off-car, CHƯA commit

> **Trạng thái**: code + test xong, 5 module 0 đỏ, đã chụp máy ảo API 29 · **Cập nhật**: 2026-09-20
> **Spec**: `docs/specs/kachi-ux-overhaul.html` §WP2 (R2.1–R2.5) · **Nền**: WP1 (hệ màu glass + 0 viền) đã duyệt
> **Ảnh cho owner duyệt**: `docs/diagnostics/ux-overhaul-2026-09-20/wp2-*.png`
> **KHÔNG commit** (cây làm việc còn giữ cả WP1 chưa commit — theo lời giao).

---

## 1. Owner giao gì → làm gì

| # | Owner | Đã làm |
|---|---|---|
| R2.1 | On/off → icon active/mờ + đổi màu, **bỏ chữ "Bật/Tắt"** | Ô bật/tắt nay hạ **độ đục của icon** khi tắt + nền/mực đổi màu nhấn khi bật. Ô bật/tắt và ô bấm-một-phát **không được dựng `TextView` phụ nào** (bài canh chặn) |
| R2.2 | Nhiều mức (ghế mát/sưởi 2 mức) → **2 vạch nhỏ** | Chữ *"Tắt / Mức 1 / Mức 2"* **biến mất**, thay bằng dải vạch: mức 1 sáng 1 vạch, mức 2 sáng 2, Tắt mờ cả hai + đổi màu nhấn |
| R2.3 | Cốp: mở = màu active, đóng = mặc định | Ô COVER nay **đọc lại xe** (trước WP2 nhánh này trả no-op) ⇒ cốp/kính đang mở thì ô mang màu nhấn |
| R2.4 | Stepper **consistent**: ô giá trị rộng cố định, −/+ thẳng hàng bất kể `22°` hay `4` | Ba cột theo **tỉ lệ 1:2:1** + **sàn bề ngang** suy từ registry ⇒ [ĐO] ba stepper trên màn có ô giá trị **cùng 51px** và nút −/+ **cùng vị trí trong ô** |
| R2.5 | Rà soát **TOÀN BỘ** control tile theo chuẩn chung | Cả **năm** `ControlKind` đi qua **một** cửa duy nhất (`ControlVisuals.of` → `look`), có bài canh đếm chỗ gọi |

---

## 2. ⚠⚠ Lỗi TÌM RA khi rà soát (không nằm trong lời giao)

[ĐO đọc mã] bốn trong năm hàm dựng ô gọi `tint(icon, label, **true**)` — tức **luôn** tô mực *"đang bật"* — ngay
cạnh `applyBg(tile, **false**)` — tức nền *"đang tắt"*. Hai nửa của cùng một ô nói hai điều khác nhau, ở
`tileStep` · `tileCover` · `tileSelect` · `tileButton`.

Không bài canh nào thấy được, vì luật *"ô này coi là đang bật khi nào"* nằm **rải trong năm hàm dựng
`android.view`** mà dự án không có Robolectric để dựng. Đó là lý do WP2 chuyển luật sang `:core` thay vì chỉ
đổi màu ở tầng vẽ.

---

## 3. Kiến trúc — luật ở `:core`, màu/hình ở `:app`

```
:core  ControlVisuals.of(def, value) → ControlVisual(active · ticks · lit · option · valueText)
        │   (thuần, 16 test off-car — cùng ranh giới ChipTone/GroupTone)
:app   ControlTileFactory.look(...)  → applyBg + tint theo `active`, trả ControlVisual cho chỗ gọi
        ├─ ControlLevelBar.build/light  (dải vạch mức)
        └─ TileSize / ReadTile         (kiểu dữ liệu + bộ dựng ô ĐỌC, tách ra vì trần 500 dòng)
```

### Ba quyết định đáng ghi

**(a) SELECT chia HAI ca — và chỉ một ca được thay chữ bằng vạch.**
[ĐO registry] 8 nút SELECT: `seatc`/`seath` là **thang mức**; `ambient_color` · `headlight_mode` ·
`powertrain_mode` · `regen_level` · `screen_rotation` · `camera_view` là **tập lựa chọn không xếp hạng**. Vẽ
*"Xanh lá"* thành *"3 trên 5 vạch"* là **nói sai** — ở đó chữ là thông tin **duy nhất** của ô. Phép phân biệt đọc
**dữ liệu đã có**: `ControlLevels.RAW_BY_LEVEL` (bảng thang mức, vốn bắt buộc phải khai để ghi đúng) ⇒ thêm nút vào
thang là tự có vạch, **không cần cờ thứ hai phải nhớ bật**.

**(b) Số vạch đếm theo `args`, KHÔNG theo `ControlLevels.levelCount`.**
Hai con số **lệch nhau có chủ ý**: `seath` khai 4 mã khung (`[SUY]`, chờ đo trên xe) nhưng chỉ bày **3** lựa chọn.
Vẽ 3 vạch cho một ô chỉ bấm lên tới vạch 2 là hứa một mức không bấm tới được. Có bài canh riêng.

**(c) STEP chỉ sáng khi thang có ĐIỂM 0 THẬT.**
`fan` 0..7 và `vol` 0..30 có mức 0 = im ⇒ >0 là đang có tác dụng. `temp` 17..33 **không có mức tắt nào** ⇒ ô nhiệt
độ giữ nền trung tính mãi, thay vì sáng màu nhấn suốt chuyến chỉ vì *"luôn khác min"*. Đọc `def.min == 0` (dữ liệu
dòng registry), không `if (id == "temp")`.

**(d) Đơn vị ô giá trị hết là nhánh rẽ theo mã.** `if (def.id == "temp") "°" else ""` → `ControlVisuals.stepUnit`
đọc đơn vị của **datum mà nút đọc** (`temp` → `inside_temp` = `°C` → rút về `°` cho vừa ô 84dp). Rút về `°` chứ
không `°C` vì con số đó là **điểm đặt của chính xe**, launcher không đổi °C↔°F cho nó — dán `C` là khẳng định một
thang mà lượt ghi không bảo đảm.

---

## 4. Tệp đổi

**Mới**
- `core/.../launcher/ControlVisuals.kt` (150) — `ControlVisual` + `ControlVisuals` (thuần).
- `core/src/test/.../ControlVisualsTest.kt` (236) — 16 test, phủ đủ 5 kiểu + 2 ca WP2 sinh ra để chặn.
- `app/.../launcher/ControlLevelBar.kt` (71) — dải vạch mức.
- `app/.../launcher/TileSize.kt` (92) — `ActionTile` + `TileSize` + `reserveTwoLines` + `controlIconRes` (dời).
- `app/src/test/.../ControlStateUxContractTest.kt` (215) — 11 bài canh dây nối.
- `scripts/diag/pixelprobe.py` (105) — đo điểm ảnh không cần Pillow (xem §6).

**Sửa**
- `app/.../ControlTileFactory.kt` **539 → 495 dòng** (dưới trần 500 lần đầu kể từ S4·R12).
- `app/.../ReadTile.kt` — nhận thêm `readTileOf` (bộ dựng ô ĐỌC dời về đây).
- `app/.../KachiSpace.kt` +`LEVEL_TICK_W = 10` (kèm suy luận từ `DOCK_TILE_W`).
- `app/src/test/.../CapabilityTileWiringContractTest.kt` — đổi đường dẫn của bài *"ô ĐỌC không gắn chạm"* sang
  `ReadTile.kt` **và kiểm thêm** cửa vào cũ chỉ còn một dòng uỷ quyền (chống mọc bộ dựng thứ hai).

⚠ **Vì sao phải tách tệp**: `ControlTileFactory.kt` đã **539 dòng** (quá trần 500 của CLAUDE.md §4.1) **TRƯỚC khi
WP2 thêm một dòng nào**. Cắt theo VAI, không cắt cho vừa số dòng: kiểu-dữ-liệu (`TileSize.kt`) · bộ-dựng-ô-ĐỌC
(`ReadTile.kt`, tệp vốn đã sở hữu `class ReadTile`) · vẽ-vạch (`ControlLevelBar.kt`).

---

## 5. [ĐO] Test

| Phép đo | Kết quả |
|---|---|
| `./gradlew test --rerun-tasks --continue` | **5 module · 73/73 task · 5123 test · 0 đỏ · 0 lỗi · 0 bỏ qua** (đếm từ JUnit XML) |
| `:core` thuần | **0** import `android.*` |
| Trần 500 dòng | 8/8 tệp lượt này đạt (cao nhất: `ControlTileFactory` 495, `KachiSpace` 496) |
| 0 viền / 0 blur (giữ WP1) | `ControlLevelBar` · `TileSize`: 0 `setStroke`/`setShadowLayer`/`BlurMaskFilter`/`RenderEffect`/`elevation` |

**Thử phá 3/3 đỏ đúng chỗ** (hoàn nguyên kiểm bằng `sha256`):

| Phá | Bài đỏ |
|---|---|
| `isLevelScale` → `false` (`:core`) | `ControlVisualsTest` **4 bài** (ghế mát 2 vạch · số vạch theo args · kẹp chỉ số · một-nguồn-thang-mức) |
| Bỏ sàn bề ngang + trả ô giá trị về `WRAP` | `ControlStateUxContractTest.o gia tri cua stepper co SAN be ngang lay tu registry` |
| Trả COVER về `{}` no-op | `ControlStateUxContractTest.o COVER doc lai trang thai that cua xe` |

---

## 6. [ĐO] Ảnh máy ảo — `emulator-5554` API **29**, 1920×1080 (= cấu hình xe)

Ảnh: `docs/diagnostics/ux-overhaul-2026-09-20/`
`wp2-home-dark-off.png` (mọi ô ở mặc định) · `wp2-home-dark-on.png` (đã bấm) ·
`wp2-dock-dark-on.png` (cắt thanh nút) · `wp2-tiles-dark-on.png` (cắt vùng ô).

Chủ đề kiểm **bằng độ chói** chứ không tin nhãn tệp (bẫy WP1 đã cắn): độ chói trung bình **39.3/255 ⇒ DARK**.

**R2.1 — nền ô tắt → bật** (và chỉ ô được bấm đổi, không phải cả màn vẽ lại):

| Ô | Độ chói | RGB |
|---|---|---|
| `defrost` (bấm) | 35.7 → **77.5** (2.17×) | (29,36,56) → **(64,75,145)** |
| `recirc` (không bấm) | 37.5 → 37.5 (1.00×) | không đổi |
| `lock` (bật sẵn) | 78.7 → 78.7 | (66,76,146) — **khớp màu ô vừa bật** |

**R2.2 — vạch mức** (tìm hàng sáng nhất *bằng máy*, không đoán toạ độ):

| Ô | Vạch 1 | Vạch 2 |
|---|---|---|
| `seatc` **Mức 2** (thanh nút) | max **236** · 86px sáng | max **236** · 86px sáng |
| `seath` **Mức 1** (ô giữa màn, cỡ BIG) | max **236** · 86px sáng | max **122** (mờ) |

**R2.4 — stepper** (đo từ cây trợ năng LIVE):

| Nút | −  | ô giá trị | + | tỉ lệ |
|---|---|---|---|---|
| `temp` `"23°"` (ô giữa màn) | 263px | **526px** | 263px | 0.50 : 1 : 0.50 |
| `fan` `"5"` (thanh nút) | 25px | **51px** | 26px | 0.49 : 1 : 0.51 |
| `vol` `"15"` (thanh nút) | 25px | **51px** | 26px | 0.49 : 1 : 0.51 |

⇒ `"5"` (1 ký tự) và `"15"` (2) và `"23°"` (2+đơn vị) cho **cùng một bố cục**; ba nút `−` của thanh nút nằm ở
`1254 / 1392` — **đúng một bước ô (138px)** ⇒ cùng vị trí trong ô. Đây chính là *"nhiệt và gió cân đối GIỐNG
NHAU"*.

**Quét chữ trạng thái trên cả màn**: `Off · Tắt · On · Bật · Level 1/2 · Mức 1/2` → **KHÔNG CÓ**.
(`Close · Open · Half` còn lại là **nút hành động** của ô COVER, không phải chữ trạng thái.)

⚠ Phiên này **không có công cụ sinh sub-agent**, mà `.kiro/steering/image-reading-subagent.md` cấm đọc ảnh lớn
trong phiên chính ⇒ bằng chứng hình ảnh là **phép đo điểm ảnh** (`scripts/diag/pixelprobe.py`, cùng cách WP1 đã
dùng). **Owner nhìn ảnh là bước xác nhận cuối** — em không tự tuyên bố "đẹp".

Máy ảo đã **trả về cấu hình cũ** sau khi chụp (sao lưu ở phía host, không để `.bak` trong `shared_prefs` — ô đó là
WAL của `SharedPreferencesImpl`, bẫy WP1 đã cắn).

---

## 7. 🚗 Cần XE (không đo được off-car) — và nói rõ vì sao

1. **R2.3 màu ô COVER khi đang mở.** Off-car `CarStatus.controls` **rỗng** ⇒ `refresh` thoát sớm ⇒ máy ảo không thể
   hiện trạng thái mở. Đã khoá bằng test `:core` (`cốp mở ⇒ active`, `kính hé 10% vẫn là đang mở`) + bài canh dây
   nối. Trên xe: mở cốp bằng tay rồi xem ô có sáng màu nhấn không.
2. **Ô `seatc`/`seath` đọc mức THẬT.** Thang `seath` vẫn `[SUY]` 4 mã (`ControlLevels`) — chưa có điểm đo thứ hai.
   Nếu xe trả mã ngoài thang thì ô hiện 0 vạch (im lặng, **không** làm tròn thành "mức 1") — đúng thiết kế.
3. **Vạch mức trên cụm/ngoài trời.** Vạch cao 4dp; cần owner xem trên màn xe thật đủ rõ chưa.

---

## 8. ⚠ Owner quyết — 3 câu hỏi (em KHÔNG tự làm)

**(1) Chữ "Bật/Tắt" của mục ĐỌC — giữ hay đổi?**
Owner nêu ví dụ *"không phải 'Sấy kính: Bật'"*. Ô **BẤM** `defrost` đã xong (icon + màu, 0 chữ). Nhưng datum
**ĐỌC** `defrost_front_state` vẫn in ra chữ *"Bật"/"Tắt"* qua `TelemetryReadout.onOff()` — nó hiện ở **ô đọc** và
**ô con của nhóm**. Em **cố ý không đổi** vì:
- cùng hàm đó là nguồn cho **câu trả lời bằng giọng nói** (*"sấy kính đang bật"*) — ở đó chữ là bắt buộc;
- đổi ô đọc thành icon-sáng-mờ là thiết kế lại **board widget**, tức **WP3**, và lời giao lượt này ghi rõ *"CHỈ
  WP2 — KHÔNG đụng WP3-9"*.
⇒ Xin owner chốt: đưa vào WP3 hay giữ chữ cho mục đọc.

**(2) Hai bề mặt cùng một nút lệch nhau off-car.** Đặt `fan` ở CẢ thanh nút và ô giữa màn: bấm `+` ở thanh nút thì
ô kia **không đổi số** (bảng `ControlTileState` dùng chung đã đúng, nhưng ô kia chỉ vẽ lại khi `CarStatus.controls`
đổi — off-car nó rỗng). Trên xe nhịp poll hoà hai bên trong ~1 s. **Đây là hành vi CÓ TRƯỚC WP2** (từ 1.69), không
phải hồi quy; nhưng nút **không có `readKey`** (vd `readl`) thì lệch **cả trên xe**. Xin owner chốt có vá không.

**(3) `ControlTileState` không lưu bền.** Mức ghế/gió về mặc định sau mỗi lần mở lại launcher (cho tới khi xe trả
số thật). Cũng là hành vi có trước; nêu ra vì WP2 làm nó **nhìn thấy rõ hơn** (vạch mức reset về mờ).

---

## 9. Chưa làm (ngoài phạm vi lượt này)

- WP3–WP9 chưa đụng một dòng.
- **Chưa soát độc lập**: phiên không có công cụ sinh sub-agent ⇒ chưa có reviewer ngoài. Ghi rõ để pha sau soát.
- **Chưa commit, chưa bump version, chưa OTA** (theo lời giao).
