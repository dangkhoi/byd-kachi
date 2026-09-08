# Master Maneuver Mapping — GMaps → Cụm (AMAP NEW_ICON) → HUD (CAN)

> Owner: Đăng Khôi · `dangkhoi` — 2026-08-15. Off-car deliverable. **No code changed** by this doc.
> Mục tiêu owner (2026-08-15): map TỐI ĐA off-car từ nguồn chuẩn; lái xe chỉ để bắt case sót.
> Mapping BẮT BUỘC 3 cột `GMaps → cụm → HUD` vì cụm (AMAP NEW_ICON) và HUD (CAN) là 2 không gian id KHÁC NHAU.

## 0. Nguồn (traced, no-assumptions)

- **Cột TRÁI (GMaps maneuver — taxonomy chính chủ Google):**
  - `androidx.car.app.navigation.model.Maneuver.Type` (Car App Library 1.0.0) — 50 hằng số, có
    roundabout CW/CCW + `getRoundaboutExitNumber()` + `getRoundaboutExitAngle()`.
    [web: developer.android.com/reference/androidx/car/app/navigation/model/Maneuver — fetched 2026-08-15]
  - Directions API `maneuver` string (~20 giá trị): `turn-slight-left/right`, `turn-sharp-left/right`,
    `turn-left/right`, `uturn-left/right`, `straight`, `ramp-left/right`, `merge`, `fork-left/right`,
    `keep-left/right`, `ferry`, `ferry-train`, `roundabout-left`, `roundabout-right`.
    Số lối ra nằm trong `html_instructions` ("take the **Nth** exit"). [web: developers.google.com/maps/documentation/directions]
- **Cột GIỮA + PHẢI (firmware — đã RE):** AMAP `NEW_ICON` 0..28 + `TurnIdMapToCAN[]` + CAN 1..49.
  [doc: re-maneuver-icon-tables-2026-08-14.md §1/§2] · [RE:AmapService.java :66-67, :605-614]

- **VN = right-hand traffic (RHT)** → xe chạy phải, vòng xuyến lưu thông **ngược chiều kim đồng hồ (CCW)**.
  Directions API cho RHT trả `roundabout-right`; firmware `NEW_ICON 11 = 进入环岛 (RHT/CCW) → CAN 13`.
  ⇒ vòng xuyến VN = **NEW_ICON 11 → CAN 13**. (LHT/CW là hiếm ở VN — vẫn map ở dưới cho đủ.)

- **Bất biến 2 cột (mặc định):** `HUD_CAN == TurnIdMapToCAN[cụm_AMAP]`. Khai riêng từng cột, KHÔNG suy 1 cột ra cột kia.
  Ngoại lệ có chủ đích ghi rõ ở cột "note" (vd exit-N reach CAN 25..44 qua offset, không qua TurnIdMapToCAN).

---

## 1. BẢNG MASTER (3 cột)

Ký hiệu cột: **cụm** = AMAP `NEW_ICON` (0..28) ghi vào broadcast AUTONAVI; **HUD** = CAN turn-id ghi vào
`INSTRUMENT_GUIDE_INFO_SIMPLE_SET` (0x43F01010).

### 1.1 Rẽ tại giao lộ (turn) — có glyph đầy đủ
| GMaps (Directions · Maneuver.Type)                 | cụm | HUD | note |
|----------------------------------------------------|:---:|:---:|------|
| `turn-left` · TYPE_TURN_NORMAL_LEFT (7)            |  2  |  1  | 45–135° |
| `turn-right` · TYPE_TURN_NORMAL_RIGHT (8)          |  3  |  2  | |
| `turn-slight-left` · TYPE_TURN_SLIGHT_LEFT (5)     |  4  |  3  | 10–45° |
| `turn-slight-right` · TYPE_TURN_SLIGHT_RIGHT (6)   |  5  |  5  | |
| `turn-sharp-left` · TYPE_TURN_SHARP_LEFT (9)       |  6  |  7  | 135–175° |
| `turn-sharp-right` · TYPE_TURN_SHARP_RIGHT (10)    |  7  |  8  | |
| `uturn-left` · TYPE_U_TURN_LEFT (11)               |  8  |  9  | quay đầu trái (RHT) |
| `uturn-right` · TYPE_U_TURN_RIGHT (12)             | 19  | 10  | ⚠ enum hiện chỉ có `UTURN`(=trái). Thêm `UTURN_RIGHT` (NEW_ICON 19→CAN 10, đang UNUSED). |

### 1.2 Đi thẳng / giữ làn / khởi hành
| GMaps                                              | cụm | HUD | note |
|----------------------------------------------------|:---:|:---:|------|
| `straight` · TYPE_STRAIGHT (36)                    |  9  | 11  | |
| `keep-left` · TYPE_KEEP_LEFT (3)                   |  4  |  3  | không có glyph "keep" → dùng chếch-trái |
| `keep-right` · TYPE_KEEP_RIGHT (4)                 |  5  |  5  | |
| (đổi tên đường) · TYPE_NAME_CHANGE (2)             | 20  | 12  | 顺行 continue |
| `depart` · TYPE_DEPART (1)                         |  9  | 11  | bắt đầu = đi thẳng ra đường |

### 1.3 Ngã ba chữ Y (fork) — KHÔNG có glyph fork → dùng chếch
| GMaps                                              | cụm | HUD | note |
|----------------------------------------------------|:---:|:---:|------|
| `fork-left` · TYPE_FORK_LEFT (25)                  |  4  |  3  | ≈ chếch-trái |
| `fork-right` · TYPE_FORK_RIGHT (26)                |  5  |  5  | |

### 1.4 Nhập làn (merge) — KHÔNG có glyph merge → ĐI THẲNG  *(fix bug owner "merge → rẽ phải")*
| GMaps                                              | cụm | HUD | note |
|----------------------------------------------------|:---:|:---:|------|
| `merge` · TYPE_MERGE_SIDE_UNSPECIFIED (29)         |  9  | 11  | 0..28 không có glyph merge |
| TYPE_MERGE_LEFT (27)                               |  9  | 11  | side không vẽ được → đi thẳng |
| TYPE_MERGE_RIGHT (28)                              |  9  | 11  | |

### 1.5 Đường dẫn cao tốc (ramp)
| GMaps                                              | cụm | HUD | note |
|----------------------------------------------------|:---:|:---:|------|
| `ramp-left` slight · TYPE_ON/OFF_RAMP_SLIGHT_LEFT (13/21)   | 4 | 3 | tách nhẹ ≈ chếch-trái |
| `ramp-right` slight · (14/22)                      |  5  |  5  | |
| TYPE_ON/OFF_RAMP_NORMAL_LEFT (15/23)               |  4  |  3  | ⚠ **QĐ #1** (xem §3): chếch (RE doc) hay rẽ thường 2→1? |
| TYPE_ON/OFF_RAMP_NORMAL_RIGHT (16/24)              |  5  |  5  | ⚠ hoặc 3→2 |
| TYPE_ON_RAMP_SHARP_LEFT (17)                       |  6  |  7  | sharp → sharp |
| TYPE_ON_RAMP_SHARP_RIGHT (18)                      |  7  |  8  | |
| TYPE_ON_RAMP_U_TURN_LEFT (19)                      |  8  |  9  | |
| TYPE_ON_RAMP_U_TURN_RIGHT (20)                     | 19  | 10  | |

### 1.6 Vòng xuyến (roundabout) — VN = RHT/CCW
| GMaps                                              | cụm | HUD | note |
|----------------------------------------------------|:---:|:---:|------|
| `roundabout-right` (RHT) · TYPE_ROUNDABOUT_ENTER_CCW (45) | 11 | **13** | ⭐ VÀO vòng xuyến. **FIX bug HUD 15→13** (§ re-doc §5). |
| …+ lối ra N · TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW (34) `getRoundaboutExitNumber()` | 11 **+ ROUNG_ABOUT_NUM=N** | **25..34** | ⚠ **QĐ #2**: cần nhét ROUNG_ABOUT_NUM vào broadcast. N lấy từ text ("lối ra thứ N") — `NavFormat.roundaboutExit()`. |
| ra vòng xuyến · TYPE_ROUNDABOUT_EXIT_CCW (46)      | 12  | 24  | drive-out (khi step bắt đầu trong vòng xuyến) |
| `roundabout-left` (LHT) · TYPE_ROUNDABOUT_ENTER_CW (43) | 17 | 14 | hiếm ở VN |
| ra vòng xuyến CW · TYPE_ROUNDABOUT_EXIT_CW (44)    | 18  | 23  | |
| CW + lối ra N · TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW (32) | 17/18 **+num** | **35..44** | |

### 1.7 Đến nơi (destination)
| GMaps                                              | cụm | HUD | note |
|----------------------------------------------------|:---:|:---:|------|
| (arrive) · TYPE_DESTINATION (39)                   | 15  | 48  | |
| TYPE_DESTINATION_LEFT/RIGHT/STRAIGHT (41/42/40)    | 15  | 48  | side mất (không có glyph dest có hướng) — chấp nhận |

### 1.8 Owner add-backs (glyph CÓ trên cụm, GMaps có expose hay không cần verify on-car)
| GMaps / nguồn                                      | cụm | HUD | note |
|----------------------------------------------------|:---:|:---:|------|
| **HẦM** (icon-name/text `tunnel`/`hầm`)            | 16  | 49  | owner ask; add `Maneuver.TUNNEL`. ⚠ **QĐ #3**: verify GMaps expose (NavArrowLog `small_amap`/`sig_name`). |
| khu dịch vụ (service area)                         | 13  | 46  | GMaps hiếm — tier C |
| trạm thu phí (toll)                                | 14  | 47  | GMaps hiếm — tier C |
| điểm dừng (waypoint arrival)                       | 10  | 45  | via-point — tier C |

### 1.9 Không map được (source/cluster limit)
| GMaps                                              | cụm | HUD | note |
|----------------------------------------------------|:---:|:---:|------|
| `ferry` / `ferry-train` · TYPE_FERRY_* (37,38,47–50) | 9 | 11 | ❌ 0..28 KHÔNG có glyph phà → fallback đi thẳng. Rất hiếm. |

---

## 2. Runtime — mapping này áp dụng thế nào (không đổi kiến trúc)

Bảng master là "đáp án đúng" SAU KHI đã nhận diện được maneuver. App vẫn nhận diện từ **notification GMaps**:
- **TÊN resource small-icon** (chính) → `IconResource.NAME_TO_AMAP` → NEW_ICON. [src:IconResource.kt]
- **TEXT** (phụ) → `NavFormat.maneuverVerbIcon` + **số lối ra** `NavFormat.roundaboutExit()`. [src:NavFormat.kt]
- **Perceptual signature** (fallback) → `ManeuverSignature.classify`. [src:ManeuverSignature.kt]
- NEW_ICON → `Maneuver` (`fromAmapIcon`) → `toAmapIcon` (cụm) + `toHudIcon` (HUD). [src:Maneuver.kt]

⇒ Việc rebuild = cập nhật `NAME_TO_AMAP` (tên GMaps thật) + `Maneuver` enum/`toHudIcon`/`toAmapIcon` cho khớp bảng §1,
KHÔNG dựa lái xe. `IconResource.lastName` (hiện trên MainActivity) chỉ để **bắt tên GMaps lạ** khi anh em gặp lỗi.

---

## 3. Điểm cần owner quyết (decision points)

- **QĐ #1 — ramp góc thường (NORMAL):** map `4/5` (chếch, theo RE doc off-ramp fix — ramp là tách làn nhẹ)
  HAY `2/3` (rẽ thường 45–135°, đúng góc Google)? *Đề xuất: chếch (4/5)* — trên/dưới cao tốc là tách/nhập, không phải cua giao lộ.
- **QĐ #2 — số lối ra vòng xuyến:** để đạt CAN 25..34 phải nhét `ROUNG_ABOUT_NUM` (1..10) vào broadcast AUTONAVI
  (frame builder) + emit NEW_ICON 12 khi biết N. Việc to hơn 1 dòng; cần verify broadcast hiện đang bỏ trống num.
  Nếu chưa làm: vòng xuyến vẫn ra glyph "vào vòng xuyến" chung (CAN 13) — vẫn đúng, chỉ thiếu "lối ra thứ mấy".
- **QĐ #3 — HẦM & TUNNEL/service/toll:** thêm enum + classifier rule, nhưng chỉ vẽ được NẾU GMaps expose tên/text.
  Cần 1 lần verify on-car (đi qua hầm/vòng xuyến có số, đọc `NavArrowLog`).

---

## 4. Việc triển khai (sau khi owner chốt §3)

1. **`Maneuver.kt`**: thêm `UTURN_RIGHT`(19/10), `ROUNDABOUT_EXIT`(12/24), `TUNNEL`(16/49) [+ tier C]; **sửa `ROUNDABOUT.toHudIcon` 15→13**;
   giữ bất biến `toHudIcon==TurnIdMapToCAN[toAmapIcon]` (trừ exit-N).
2. **`IconResource.NAME_TO_AMAP`**: đối chiếu với tên drawable GMaps thật (RE APK GMaps trên xe HOẶC gom qua `lastName`).
3. **Tests**: cập nhật `ManeuverTest` (row `ROUNDABOUT.toHudIcon`==13), thêm bảng-driven test map từng GMaps→(cụm,HUD) khoá cả 2 cột.
4. (QĐ #2) `ClusterBroadcaster`/frame builder: wire `ROUNG_ABOUT_NUM`.
5. Verify off-car (test) + on-car (`NavArrowLog` CSV: `small_amap,sig_name,sig_amap,verb_amap,heuristic_amap,final_icon`).

## 5. References
- Google: `androidx.car.app.navigation.model.Maneuver` (Type consts 0..50, getRoundaboutExitNumber/Angle). [web 2026-08-15]
- Google: Directions API `maneuver` string enum + html_instructions "take Nth exit". [web 2026-08-15]
- Firmware: re-maneuver-icon-tables-2026-08-14.md §1 (NEW_ICON 0..28 + TurnIdMapToCAN) · §2 (CAN 1..49) · §2.1 (exit offset 25..44).
- Repo: `core/.../navigation/Maneuver.kt`, `app/.../IconResource.kt`, `core/.../navigation/NavFormat.kt`, `ManeuverSignature.kt`, `NavArrowLog.kt`.
