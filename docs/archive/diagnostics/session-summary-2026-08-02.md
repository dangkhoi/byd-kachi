# Tổng kết phiên 02/8 — Cluster Cast

## Kết quả đo được (field-proven)

### Cast app thường (VietMap, GMaps)
| Hành vi | Kết quả |
|---------|---------|
| Cast lên cụm vật lý | ✅ Work (3-4s) |
| Stop trả về màn chính | ✅ Work (1-2s) |
| Cast lần 2, 3, ... | ✅ Work |
| Bubble phản ánh state | ✅ (chậm 3-4s) |
| App Home status | ⚠️ "Cần xử lý thủ công" → tự hồi sau 15s |

### CarPlay
| Hành vi | Kết quả |
|---------|---------|
| CP lên cụm (`am stack move-task`) | ✅ 0 crash |
| CP scaling (`wm size 1422x800`) | ✅ Đẹp, iPhone tự scale, đúng tỉ lệ |
| CP + app khác chia đôi | ❌ CP unresizeable, chiếm toàn cụm |
| HOME key trên display 1 | ⚠️ Kill CP task |

### Android Auto
| Hành vi | Kết quả |
|---------|---------|
| AA lên cụm (`am stack move-task`) | ✅ 0 crash |
| AA scaling | ❌ Stream cố định từ phone, không scale theo wm size |
| AA best setting | 1920×1080 — đúng tỉ lệ, mất top/bot (trace later) |
| AA re-negotiate resolution | ❌ Không thay đổi dù set wm size trước connect |

### Display config đã đo
| Setting | CP | AA | App thường |
|---------|----|----|-----------|
| wm size | 1422×800 | 1920×1080 | viewport (TBD) |
| overscan | 10,-120,10,50 (CP) | 0,0,0,0 | TBD |
| density | reset (320 native) | reset | reset |

### Overlay trên cụm
- VietMap bubble tốc độ = **SYSTEM_ALERT_WINDOW** (overlay), KHÔNG phải PiP
- Bubble hiện trên display 0 khi VietMap ở background
- ClusterNav CÓ `SYSTEM_ALERT_WINDOW` permission → khả năng CAO tạo được overlay trên display 1
- **Chưa test bằng code** — cần implement

---

## Root cause đã xác định

### Verification luôn fail
**Nguyên nhân:** `completeVerificationLocked` ghi RECOVERING ngay khi observation Known nhưng terminal=false (appops/geometry/animation không khớp chính xác). Ledger fallback đã implement nhưng bị race với migration refresh.

**Ledger fallback fire đúng ở nhánh RECOVERING** (fix cuối cùng hôm nay) — nhưng chưa rebuild + test lại.

### Cụm không hiện app (projection đóng)
**Nguyên nhân:** `resetSessionForFreshBoot` xoá seal → cast không chạy bootstrap → app lên display ảo nhưng projection vật lý đóng.

**Fix:** `resetSessionForFreshBoot` chỉ xoá session KHÔNG CÓ seal + không có transaction active.

### SCALE_CLUSTER_DENSITY gây hỏng
**Nguyên nhân:** Đổi density display 1 → ảnh hưởng MỌI app trên display đó. Không reset khi crash/stop → frame cũ kẹt.

**Fix:** BỎ SCALE_CLUSTER_DENSITY. Dùng `wm size` thay thế (per-config cho từng loại app).

---

## Kiến trúc mới đề xuất (chủ dự án chốt hướng)

> "Mở app là tự scale màn về chiếu (ko mất kmh), vẽ sẵn ô chờ màu đen, rồi lựa cast full hay trái phải"

### Flow đơn giản:
1. **Mở app** → mở projection (30/16/35) + set `wm size` + `wm overscan` → cụm sẵn sàng (màn cong, giữ km/h)
2. **Bấm cast** → `am start --display 1` hoặc `am stack move-task` → app lên ngay
3. **Stop** → trả app về display 0 (KHÔNG đóng projection) → cụm vẫn sẵn sàng
4. **Tắt app** → đóng projection (18/0) → cụm về đồng hồ

### Bỏ được:
- Bootstrap verification phức tạp (projection mở 1 lần duy nhất)
- Seal/profileExport tracking
- Migration state machine  
- `retireUnprovenClusterClaim`
- Observation-based CAST verification (dùng ledger)

### Config per-app:
- CP: `wm size 1422x800`, overscan 10,-120,10,50
- AA: `wm size 1920x1080`, overscan 0,0,0,0
- App thường: `wm size` theo viewport user chọn (default ~1920×800)
- Mỗi lần cast app loại khác → đổi wm size/overscan cho phù hợp

### Feature mới: HUD overlay trên cụm
- Vẽ SYSTEM_ALERT_WINDOW trên display 1
- Hiện tốc độ, hướng dẫn dẫn đường (đọc từ VietMap notification/broadcast)
- Đè lên app đang chiếu (AA/CP/bất kỳ)

---

## Vấn đề tồn đọng

1. **Verification observation** — vẫn dùng dadb localhost, vẫn cần ADB key trust. Với kiến trúc mới (bỏ verify cho CAST) → ít quan trọng hơn.
2. **App Home status chậm** — "Cần xử lý thủ công" hiện 10-15s trước khi reconcile dọn. Cosmetic.
3. **AA không scale** — stream cố định. Chấp nhận cắt top/bot hoặc tìm cách config autoservice.
4. **`wm size` cần reset khi đổi app** — CP dùng 1422×800, AA dùng 1920×1080. Phải reset trước khi cast app khác.
5. **Overlay trên display 1** — chưa test bằng code.
6. **LocalProcessShellGateway.kt** — dead code, cần xoá.

---

## Việc tiếp theo

### Ưu tiên (scope phiên tới):
1. **CP, AA ổn định** — cast lên/trả về mượt, không kẹt
2. **Cast app thường OK** — VietMap, GMaps lên cụm đúng viewport
3. **Cast 2 app OK** — chia đôi cụm (chưa test bằng app thật, mới verify off-car)
4. **Resize OK** — app thường cho chỉnh size thoải mái, lưu lại sau khi chỉnh

### Quy tắc:
- **CP/AA: fix size** — không cho chỉnh (CP=1422×800, AA=1920×1080)
- **App thường: cast full** → cho chỉnh size tuỳ ý → **lưu lại** preference
- **UI lag** — trạng thái chậm 10-15s, không đúng thực tế. Root cause = reconcile cycle + observation timeout. Cần fix.
- **Resize chưa test thật** — có thể do UI lag nên không verify được. Cần test lại khi UI nhanh.

### KHÔNG làm phiên này:
- ❌ Bong bóng VietMap / HUD overlay trên cụm (để sau)
- ❌ AA scaling research (chấp nhận cắt)

### Thứ tự:
1. Viết spec kiến trúc mới
2. Implement (flow đơn giản: projection sẵn → cast = move app)
3. Fix UI lag (bỏ observation-based verify, dùng ledger → state update ngay)
4. Test on-car: CP → AA → app thường → 2 app → resize
