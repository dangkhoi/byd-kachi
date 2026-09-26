# Runbook lên xe — Kachi 2.07 (versionCode 108)

> **Trạng thái**: Superseded — thay bởi `docs/diagnostics/oncar-runbook-2.73.md` · **Cập nhật**: 2026-09-22 · **Mục đích**: danh sách việc LÀM TRÊN XE cho bản 2.07, từng việc một — làm gì · kết quả mong đợi (PASS/FAIL) · ghi lại gì.
>
> Đọc cùng: `docs/diagnostics/oncar-master/RUNBOOK.md` (chi tiết RE 4 mảng) + `scripts/vehicle/kachi/` (script tự chụp bằng chứng). Runbook này là **bản gọn ưu tiên** cho những gì còn lại sau 2.07.
>
> **Đã đóng, KHÔNG làm nữa:** D5 cast-side (đã chạy ngon trên xe) · D6 HUD kính zin (gate firmware `0x38B00030`, cần tool coding — **bỏ hẳn**).

---

## 0. Chuẩn bị (một lần đầu buổi)

| Bước | Làm | PASS |
|---|---|---|
| 0.1 | Xe đang bản nào? Cài đặt › Hệ thống › kiểm phiên bản | Ghi lại version cũ |
| 0.2 | Cập nhật lên **2.07** (OTA tự tải, hoặc side-load `apk/Kachi-2.07-release.apk`) | Mở app thấy versionName 2.07 |
| 0.3 | Nếu cần đọc log/prefs: side-load bản **vehicleTest** cùng số (debuggable) — nhớ cài lại release trước khi rời xe | `adb`/`run-as` chạy được |
| 0.4 | Cắm adb (dây hoặc wireless). Chạy `scripts/vehicle/kachi/00-connect.sh` | Kết nối OK |

---

## A. NGHIỆM THU 2 VIỆC VỪA SHIP (2.07) — ưu tiên cao nhất

### A1 — Vá "seri ngu" (nút mic không kẹt)
| Bước | Làm | PASS | FAIL → ghi |
|---|---|---|---|
| A1.1 | Bật voice, nói một câu **cố ý cho nghe nhầm** 5–7 lần liên tiếp (vd "tắt kính lái" nói nhanh/nhỏ) | Sau nhiều lần nhầm, **vẫn bấm mic lại được** và nghe được | Nếu "nói gì cũng không hiểu, phải tắt mở lại" → CHƯA hết, ghi log `logcat -s KachiVoice VoiceSingleFlight` |
| A1.2 | Ngay sau chuỗi nhầm đó, bấm mic + nói 1 câu **chuẩn** ("mở kính lái") | Hiểu + làm đúng ngay, không phải chờ/không phải restart | |

### A2 — Dock cuộn khi nhiều nút
| Bước | Làm | PASS | FAIL → ghi |
|---|---|---|---|
| A2.1 | Cài đặt › Thanh nút: thêm **nhiều nút** hơn chiều dài thanh (viền DƯỚI) | Thanh nút **cuộn ngang** được, nút tràn bấm tới được (không cắt cụt) | Ghi số nút + ảnh |
| A2.2 | Đổi viền thanh sang **TRÁI/PHẢI**, thêm nhiều nút hơn chiều cao | Thanh nút **cuộn dọc** được | |
| A2.3 | Để lại **ít nút** (không tràn) | Thanh vẫn lấp trọn/căn như cũ, không đổi hình | |

---

## B. NGHIỆM THU CÁC BẢN TRƯỚC CHƯA ĐO TRÊN XE (2.00–2.05)

### B1 — Phím vô-lăng "chắc ăn" (tự lành binding)
| Bước | Làm | PASS |
|---|---|---|
| B1.1 | Bật "Nút vật lý → Trợ lý", gán phím | Phím mở trợ lý |
| B1.2 | **Reboot bằng nút nguồn vật lý** (không `adb reboot`), nổ lại, KHÔNG mở app | Phím vẫn hoạt động (tự bind lại) |
| B1.3 | Chạy tải cao (mở nhạc + GMaps) một lúc rồi bấm phím | Phím vẫn ăn (watchdog nền heal) |

### B2 — Hey Kachi dễ nổ hơn (2.05)
> ⚠ **CHẶN: `keywords.txt` mới phải được re-upload lên GitHub Release `voice/kws/`** — chưa upload thì máy vẫn tải bản cũ, đo B2 vô nghĩa. Kiểm trước: máy đã tải đúng bản mới chưa (kích thước 650B, sha `d40dff2e…`).

| Bước | Làm | PASS | Ghi |
|---|---|---|---|
| B2.1 | Bật "Hey Kachi", nói "Hey Kachi" giọng bình thường ×10 | Nổ ≥7/10 | Ghi tỉ lệ nổ |
| B2.2 | Nói các biến thể ("hai kachi", "ca chi"...) | Nổ được | Ghi biến thể nào ăn/không |
| B2.3 | Mở nhạc (tải 10–17) rồi gọi "Hey Kachi" | Vẫn nổ (VoiceLoadGuard nới 12/9) | Đo CPU nếu nhiễu |

### B3 — Giọng đọc + kính + ghế (1.94–2.04)
| Bước | Làm | PASS |
|---|---|---|
| B3.1 | Nghe câu trả lời của Kachi | Tốc độ 0.8 — nghe rõ, không quá nhanh |
| B3.2 | "mở kính lái" / "mở kính phụ" / "đóng cả cụm" (windows_close_all) | Từng kính đúng, đóng cả 4 |
| B3.3 | "mở nửa kính lái" (win_half_lf) | Kính mở ~50% |
| B3.4 | "mát ghế lái" / "mát ghế phụ" / "sưởi ghế phụ" | Đúng ghế, đúng chế độ (ghế phụ seatID 2) |
| B3.5 | Nút "Cập nhật gói giọng đọc" + "Cập nhật mô hình nghe" | Hiện đúng 3 trạng thái; bấm cập nhật chạy |

---

## C. CHẤT LƯỢNG NGHE THẬT (quyết định có đầu tư C1/C2/C5 không)

> Đây là bước **thu dữ liệu để quyết**, không phải sửa. Bật diag-log (bản có `-PdiagLog`) để ghi transcript.

| Bước | Làm | Ghi lại |
|---|---|---|
| C.1 | Nói 25 câu thông dụng (bảng `oncar-master/3-voice.md`), 3 mức ồn (im/nhạc nhỏ/nhạc to + chạy) | Tỉ lệ nghe ĐÚNG mỗi mức |
| C.2 | Đặc biệt các câu team báo hay nhầm ("tắt/đóng kính lái") | Nghe ra chữ gì (transcript thật) — để biết model sai ở đâu |
| C.3 | Câu địa chỉ dài / tên đường | Nghe được không (giới hạn model) |
| C.4 | Xuất log: tắt công tắc diag hoặc broadcast `EXPORT_LOGS` → `/sdcard/Download/ClusterNavLog` | Kéo log về phân tích |

**Quyết định sau C:** nếu tỉ lệ nghe đúng < ~70% ở mức thường → cần **C2 (model nghe tốt hơn)**; nếu Hey Kachi < 70% nổ → cần **C1 (KWS-VI train)**. Có số rồi mới đầu tư (train/port là việc nặng).

---

## D. RE / ĐO HAL (mở khoá dữ liệu xe — làm khi có thời gian)

> Chi tiết lệnh ở `docs/diagnostics/oncar-master/1-hal.md`. Cần bản vehicleTest + HAL probe.

| # | Việc | Làm | Ghi |
|---|---|---|---|
| D2 | 10 datum sentinel còn "—" | Sweep getter cho ev_mileage_km · trip_km/hours/kwh · sunroof_pos · volt_12v_level · tailgate_status · batt_temp · gear · cabin_temp | Getter/feature-id nào trả số thật |
| D3 | ~27 nút chưa nối đọc | Dò readKey/method theo trim owner | Bảng mã đọc được |
| D4 | Feature-id động (`BYDAutoFeatureIds`) | Verify tên hằng trên ROM xe (dump `BYDAutoDeviceFeaturesMap`) | Tên → số đúng chưa |
| D8 | PM2.5 auto-lọc + camera 360 | Bụi cao → tự lọc? Nút camera 360 (`setAVMSwitchState`) có ăn? | rc + hành vi thật |
| A2-vm | Bóng VietMap trên cụm | Bật bóng, kéo-thả vị trí | Bóng lên đúng chỗ |

---

## E. GHI CHÚ

- **Mọi lệnh xe (adb/shell/HAL) = op TRÊN XE** — off-car không đo được (dadb loopback + tiến trình app).
- **Reboot bắt buộc bằng nút nguồn vật lý** khi bài đòi reboot thật (`adb reboot` không tương đương).
- Bất cứ gì FAIL → ghi: bước nào · làm gì · thấy gì · log tag liên quan → báo về để sửa off-car.
- Script tự chụp bằng chứng: `scripts/vehicle/kachi/run-all.sh` (logcat nền + screenshot + diff dumpsys + thời gian mỗi lệnh).
