# Bảng đo tương phản — VISUAL-REFRESH P1 (§6.4)

> SINH BẰNG MÁY từ `KachiPalette` bởi `SurfaceContrastContractTest.sinh bang do tuong phan cua tai lieu`.
> **Không sửa tay** — sửa bảng màu rồi chạy lại `:app:testDebugUnitTest`.

## Bảng TỐI

| Cặp | Vai | Sàn | Đo được | Kết |
|---|---|---|---|---|
| `INK trên surfFrom` | chữ chính, đỉnh gradient | 4.5 | **12.57** | ✅ |
| `INK trên surfTo` | chữ chính, đáy gradient | 4.5 | **16.23** | ✅ |
| `MUT trên surfFrom` | nhãn phụ, đỉnh | 4.5 | **6.12** | ✅ |
| `MUT trên surfTo` | nhãn phụ, đáy | 4.5 | **7.91** | ✅ |
| `MUT2 trên surfTo` | mực mờ nhất, đáy | 4.5 | **7.39** | ✅ |
| `INK trên fieldSunken` | chữ trong ô lõm | 4.5 | **17.50** | ✅ |
| `INK trên surfOnFrom` | chữ trên thẻ BẬT, đỉnh | 4.5 | **7.92** | ✅ |
| `INK trên surfOnTo` | chữ trên thẻ BẬT, đáy | 4.5 | **12.33** | ✅ |
| `surfLine trên surfFrom` + `surfFrom ÷ bg` | tách thẻ khỏi nền (**viền ≥ 3.0 HOẶC bước ≥ 1.15**) | 3.0 / 1.15 | viền **2.15** · bước **1.35** | ✅ |
| `surfOnFrom ÷ surfFrom` | bước sáng BẬT↔thường | 1.2 | **1.59** | ✅ |
| `slot ÷ bg` + `lineStrong trên slot` | bậc 1 · khay ô làm việc trên nền màn (**bước ≥ 1.12 HOẶC viền ≥ 3.0**) | 1.12 / 3.0 | bước **1.14** · viền **3.19** | ✅ |
| `surfFrom ÷ slot` | bậc 2 · thẻ nội dung trên khay | 1.15 | **1.19** | ✅ |
| `surfEdge trên surfFrom` | mép sáng đọc ra là **mặt vát** (bảng SÁNG miễn: đỉnh đã trắng) | 2.20 | **3.06** | ✅ |
| `INK trên slot` | chữ ô nhóm, đỉnh khay | 4.5 | **14.91** | ✅ |
| `MUT2 trên slotTo` | nhãn nhóm mờ nhất, đáy khay | 4.5 | **7.51** | ✅ |
| `lineStrong trên bg` | mốc cũ phải giữ | 3.0 | **3.13** | ✅ |
| `emptyLine trên emptyFill` | mốc cũ phải giữ | 3.0 | **3.68** | ✅ |
| `ON_ACCENT trên gradFrom` | mốc cũ phải giữ | 4.5 | **4.83** | ✅ |
| `INK trên surfFromOverArt (ảnh sáng)` | P1b · chữ chính trên ảnh | 4.5 | **6.65** | ✅ |
| `MUT trên surfFromOverArt (ảnh sáng)` | P1b · nhãn phụ — CẦN scrim ở P1b | 4.5 | **3.24** | ❌ |
| `INK trên surfFromOverArt (ảnh tối)` | P1b · chữ chính trên ảnh | 4.5 | **14.06** | ✅ |
| `MUT trên surfFromOverArt (ảnh tối)` | P1b · nhãn phụ — CẦN scrim ở P1b | 4.5 | **6.85** | ✅ |

### Sắc lĩnh vực TỐI — mực TỆ NHẤT trên thẻ đã tint

| Lĩnh vực | Mã tint | Nền đỉnh | Nền đáy | Mực tệ nhất | Bước sáng |
|---|---|---|---|---|---|
| ENERGY | `#1a34d399` | `#243b40` | `#122626` | **4.69** | 1.22× |
| DRIVETRAIN | `#1a7b5cff` | `#2b2f4b` | `#1a1a31` | **5.16** | 1.11× |
| CLIMATE | `#1a29d3ee` | `#233b49` | `#11262f` | **4.64** | 1.23× |
| TYRES | `#1a94a3b8` | `#2e3644` | `#1c212a` | **4.81** | 1.18× |
| BODY | `#1aaeb8c8` | `#313845` | `#1f232b` | **4.67** | 1.22× |
| LIGHTS | `#1afbbf24` | `#393935` | `#27241b` | **4.59** | 1.24× |
| IDENTITY | `#1a4c7dff` | `#27324b` | `#151d31` | **5.06** | 1.13× |
| INFOTAINMENT | `#1af59e0b` | `#383532` | `#262118` | **4.82** | 1.18× |

## Bảng SÁNG

| Cặp | Vai | Sàn | Đo được | Kết |
|---|---|---|---|---|
| `INK trên surfFrom` | chữ chính, đỉnh gradient | 4.5 | **18.17** | ✅ |
| `INK trên surfTo` | chữ chính, đáy gradient | 4.5 | **16.31** | ✅ |
| `MUT trên surfFrom` | nhãn phụ, đỉnh | 4.5 | **7.22** | ✅ |
| `MUT trên surfTo` | nhãn phụ, đáy | 4.5 | **6.49** | ✅ |
| `MUT2 trên surfTo` | mực mờ nhất, đáy | 4.5 | **5.75** | ✅ |
| `INK trên fieldSunken` | chữ trong ô lõm | 4.5 | **14.34** | ✅ |
| `INK trên surfOnFrom` | chữ trên thẻ BẬT, đỉnh | 4.5 | **10.40** | ✅ |
| `INK trên surfOnTo` | chữ trên thẻ BẬT, đáy | 4.5 | **11.84** | ✅ |
| `surfLine trên surfFrom` + `surfFrom ÷ bg` | tách thẻ khỏi nền (**viền ≥ 3.0 HOẶC bước ≥ 1.15**) | 3.0 / 1.15 | viền **3.71** · bước **1.13** | ✅ |
| `surfOnFrom ÷ surfFrom` | bước sáng BẬT↔thường | 1.2 | **1.75** | ✅ |
| `slot ÷ bg` + `lineStrong trên slot` | bậc 1 · khay ô làm việc trên nền màn (**bước ≥ 1.12 HOẶC viền ≥ 3.0**) | 1.12 / 3.0 | bước **1.02** · viền **4.12** | ✅ |
| `surfFrom ÷ slot` | bậc 2 · thẻ nội dung trên khay | 1.15 | **1.16** | ✅ |
| `surfEdge trên surfFrom` | mép sáng đọc ra là **mặt vát** (bảng SÁNG miễn: đỉnh đã trắng) | 2.20 | **1.00** | ✅ |
| `INK trên slot` | chữ ô nhóm, đỉnh khay | 4.5 | **15.72** | ✅ |
| `MUT2 trên slotTo` | nhãn nhóm mờ nhất, đáy khay | 4.5 | **5.26** | ✅ |
| `lineStrong trên bg` | mốc cũ phải giữ | 3.0 | **4.20** | ✅ |
| `emptyLine trên emptyFill` | mốc cũ phải giữ | 3.0 | **3.07** | ✅ |
| `ON_ACCENT trên gradFrom` | mốc cũ phải giữ | 4.5 | **5.74** | ✅ |
| `INK trên surfFromOverArt (ảnh sáng)` | P1b · chữ chính trên ảnh | 4.5 | **18.17** | ✅ |
| `MUT trên surfFromOverArt (ảnh sáng)` | P1b · nhãn phụ — CẦN scrim ở P1b | 4.5 | **7.22** | ✅ |
| `INK trên surfFromOverArt (ảnh tối)` | P1b · chữ chính trên ảnh | 4.5 | **11.31** | ✅ |
| `MUT trên surfFromOverArt (ảnh tối)` | P1b · nhãn phụ — CẦN scrim ở P1b | 4.5 | **4.50** | ❌ |

### Sắc lĩnh vực SÁNG — mực TỆ NHẤT trên thẻ đã tint

| Lĩnh vực | Mã tint | Nền đỉnh | Nền đáy | Mực tệ nhất | Bước sáng |
|---|---|---|---|---|---|
| ENERGY | `#1404684c` | `#ebf3f0` | `#dce8eb` | **5.12** | 1.13× |
| DRIVETRAIN | `#145b3ee0` | `#f2effc` | `#e3e4f7` | **5.09** | 1.13× |
| CLIMATE | `#14026e83` | `#ebf3f5` | `#dce8ef` | **5.13** | 1.12× |
| TYRES | `#145a6779` | `#f2f3f4` | `#e3e8ee` | **5.20** | 1.11× |
| BODY | `#144f5b6d` | `#f1f2f3` | `#e2e7ee` | **5.15** | 1.12× |
| LIGHTS | `#147d5200` | `#f4f1eb` | `#e6e6e5` | **5.13** | 1.13× |
| IDENTITY | `#142f5ae0` | `#eef2fc` | `#dfe7f7` | **5.16** | 1.12× |
| INFOTAINMENT | `#14a5480a` | `#f7f0eb` | `#e9e5e6` | **5.13** | 1.13× |

