# loosen-spacing — done

> **Trạng thái**: Done · **Ngày**: 2026-09-06 · **Task**: loosen-spacing (owner báo on-car sau v1.34: padding / margin / line-height quá chật — các phần tử "dính dính vào nhau").

## Vấn đề & cách sửa

v1.34 thu MỌI khoảng cách DỌC còn ~70% trong khi CHỮ đã khôi phục về cỡ gốc v1.32 ⇒ chữ đủ to nhưng khe hở
quá hẹp + không có giãn dòng giữa các dòng wrap. Đây KHÔNG phải re-scale mù: nâng padding / margin / gap DỌC
từ ~70% lên **~88–92%** cỡ gốc v1.32 (thoải mái, vẫn NGẮN HƠN v1.32 một chút — v1.32 vốn quá cao), thêm
`lineSpacingMultiplier` cho chữ nhiều dòng. Cỡ chữ (sp) + metric NGANG GIỮ NGUYÊN. Fix viền 2 nét v1.33 giữ.
Không thêm/gỡ/đổi @+id (parity 87).

## Bảng old → new

### `res/values/dimens.xml` (token dùng chung)
| Token | v1.32 | v1.34 (cũ) | **mới** | ghi chú |
|---|---|---|---|---|
| `gap` (khoảng thẻ↔thẻ) | 14dp | 10dp | **13dp** | ~93% v1.32 |
| `touch_min` (min height control/tap) | 56dp | 40dp | **46dp** | theo chỉ thị |
| `text_title/status/body/label` | 24/16/15/13sp | (giữ) | **KHÔNG đổi** | chữ giữ nguyên |
| `card_pad` · `screen_pad` · `radius` | 16/20/16dp | (giữ) | **KHÔNG đổi** | ngang/cosmetic |

### `res/values/styles.xml` (metric DỌC + line-height)
| Style | Thuộc tính | v1.34 (cũ) | **mới** | v1.32 |
|---|---|---|---|---|
| Cockpit.Row | minHeight | 41dp | **52dp** | 58 |
| Cockpit.Row | paddingTop/Bottom | 9dp | **12dp** | 13 |
| Cockpit.Row.Subtitle | layout_marginTop | 1dp | **2dp** | 2 |
| Cockpit.Row.Subtitle | lineSpacingMultiplier | — | **1.2** | — |
| Cockpit.Text.Hint | lineSpacingMultiplier | — | **1.2** | — |
| Cockpit.HeroCard | paddingTop/Bottom | 13dp | **17dp** | 18 |
| Cockpit.Button.Compact.* (×3) | minHeight | 27dp | **34dp** | 38 |
| Cockpit.Button.Compact.* (×3) | paddingTop/Bottom | 5dp | **7dp** | 7 |
| Cockpit.StatusPill | minHeight | 20dp | **26dp** | 28 |
| Cockpit.StatusPill | paddingTop/Bottom | 3dp | **4dp** | 4 |
| Cockpit.SegmentButton | minHeight | 31dp | **38dp** | 44 |
| Cockpit.ListRow | paddingTop/Bottom | 6dp | **8dp** | 8 |
| Cockpit.Spinner | paddingTop/Bottom | 4dp | **6dp** | 6 |
| Cockpit.Spinner.Item | paddingTop/Bottom | 7dp | **9dp** | 10 |
| Cockpit.Spinner.DropDownItem | paddingTop/Bottom | 8dp | **11dp** | 12 |
| Cockpit.Pill | paddingTop/Bottom | 3dp | **4dp** | 4 |

`lineSpacingMultiplier` 1.2 CHỈ thêm vào 2 style chữ NHIỀU DÒNG (Cockpit.Text.Hint = dòng trạng thái + đoạn
mô tả · Cockpit.Row.Subtitle = tóm tắt tính năng). KHÔNG thêm cho tiêu đề/nhãn một dòng (Title/Subtitle/Value/
SectionHeader/HeroDist/HeroStreet/Row.Title/ListRowLabel).

### `SegmentedControlView.kt` (custom view hardcode vertical text padding)
| Vị trí | v1.34 (cũ) | **mới** | ghi chú |
|---|---|---|---|
| track inset dọc `pv` | 2dp | **3dp** | mockup `.seg{padding:3px}` |
| segment span padding dọc | 4dp | **5dp** | mockup `.seg span{6px}`, ~90% |

4 custom view còn lại (SpeedDial / ClusterPreview / Pm25Gauge / SeatDiagram) vẽ đồ hoạ, KHÔNG hardcode
vertical TEXT padding ⇒ không đụng.

### `res/layout/activity_main.xml` + `res/layout-w960dp/activity_main.xml` (inline dp DỌC)
Chỉ đổi `layout_marginTop/Bottom` · `paddingTop/Bottom` · `minHeight` literal. dp NGANG (rộng · paddingStart/End
· marginStart/End · drawablePadding) + chiều cao vùng vẽ (45/98/140dp) + custom-view vuông (42/50dp) GIỮ NGUYÊN.

| v1.34 (cũ) | **mới** | v1.32 (gốc) | # đổi / mỗi biến thể |
|---|---|---|---|
| 3dp | **4dp** | 4 | 8 |
| 4dp | **5dp** | 6 | 9 |
| 6dp | **7dp** | 8 | 12 |
| 7dp | **9dp** | 10 | 9 |
| 8dp | **11dp** | 12 | 10 |
| 10dp | **13dp** | 14 | 8 |
| minHeight 34dp | **44dp** | 48 | 1 (`cast_recovery_toggle`) |

= 57 giá trị/biến thể, GIỐNG HỆT ở cả 2 biến thể (portrait + w960dp). Map = round(v1.32 × 0.9), floor tại
giá trị v1.34 (chỉ nới, không siết), cap tại v1.32 (vẫn ngắn hơn v1.32).

## Seal (T11)
- Bản dọc `res/layout/activity_main.xml` bị byte-seal → RE-PIN lần **26 (nới spacing dọc — hết dính)**.
- Hash cũ (lần 25): `046d23b0…03fd8d98`
- **Hash mới (lần 26): `0678a33d50cf0c5b2c4137617230e24861814b4ffa2666225371ee89bf650091`**
- KDoc lần-26 + hằng cập nhật trong `offcar-planner/.../ExpansionTransportFenceTest.kt`. Bản rộng
  `layout-w960dp` KHÔNG pin (chỉ đổi giá trị, không đụng seal).

## Verify [ĐO]
- `./gradlew :app:assembleDebug -q` → **exit 0** (resource XML compile sạch).
- `./gradlew :app:testDebugUnitTest :offcar-planner:test --rerun-tasks --continue -Dorg.gradle.parallel=false`
  → **BUILD SUCCESSFUL**, exit 0.
  - `:offcar-planner:test` = **99 tests, 0 fail, 0 error** (gồm `ExpansionTransportFenceTest` 10/0 ⇒ seal re-pin đúng).
  - `:app:testDebugUnitTest` = **481 tests, 0 fail, 0 error** (gồm `LayoutVariantIdParityTest` + `CollapseTagParityContractTest` ⇒ parity 87 xanh).
  - Tổng **580 test, 0 fail**.
- grep: `lineSpacingMultiplier` có mặt trong `styles.xml` (Cockpit.Text.Hint + Cockpit.Row.Subtitle);
  `gap`=13dp, `touch_min`=46dp trong `dimens.xml`.

## Ngoài scope (KHÔNG làm)
- KHÔNG đổi cỡ chữ, metric ngang, kích thước custom-view/vùng vẽ, viền, @+id.
- KHÔNG git / KHÔNG bump version (theo yêu cầu task).
