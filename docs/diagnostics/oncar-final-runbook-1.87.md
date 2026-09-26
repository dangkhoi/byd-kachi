# Runbook test-xe VÒNG CUỐI — Kachi 1.87 (88)

> **Trạng thái**: Historical — đã chạy 2026-09-21; khớp lại với dòng index · **Ngày**: 2026-09-21 · **Mục đích**: đi HẾT một vòng trên xe, cái nào hỏng thì sửa
> luôn — vòng test cuối trước khi baseline đóng dự án. Bản 1.87 đã đăng OTA (main `f56c508`, `apk/Kachi-1.87-release.apk`).
>
> **Cách đọc**: mỗi bước ghi **LÀM GÌ** · **KẾT QUẢ MONG ĐỢI** · **AI** (EM = agent chuẩn bị/sửa off-car · OWNER =
> bấm/nhìn/nói trên xe). Cột **KẾT QUẢ THẬT / GHI CHÚ** để owner điền lúc test; hỏng thì chụp/nhớ để EM sửa.
>
> **Chuẩn bị**: xe đã bật máy, Kachi là màn hình chính. Nếu chưa 1.87: mở Kachi → nó tự dò OTA (Nav+HUD bật) hoặc
> `adb install -r apk/Kachi-1.87-release.apk`. Kiểm version ở Cài đặt › Giới thiệu.

---

## 0 · OTA + khởi động

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 0.1 | Nổ máy, chờ Kachi tự lên | Kachi là màn chính, hiện ngay (không kẹt splash) | OWNER | |
| 0.2 | Cài đặt › Giới thiệu → xem phiên bản | **1.87 (88)** | OWNER | |
| 0.3 | Nhìn tổng thể màn chính | KHÔNG còn **chấm tròn nhỏ** ở góc nút/ô/mục nào (đã bỏ hẳn) | OWNER | |

## 1 · Màn hình chính + ô/widget

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 1.1 | Mở picker (⇄ trên ô / ＋ ô trống) | Lưới nhóm + thẻ dựng tay + app; **KHÔNG có chấm amber** ở tile nào | OWNER | |
| 1.2 | Đặt widget **Lốp** (ô lớn) | Ô hiện **hình xe** (top-down, nền trong, đầu trên) + 4 bánh quanh xe | OWNER | |
| 1.3 | Đặt widget **Trạng thái xe** (ô lớn) | Hình xe mini hiện đúng hướng, không méo/lộn | OWNER | |
| 1.4 | Đổi bố cục (1 ô / 2 / 3 / 4) | App đang mở sắp lại đúng ô, không reset | OWNER | |
| 1.5 | Mở một app vào ô (từ Ứng dụng) | App vào ô, cuộn/chạm trong ô được (vuốt cuộn thật) | OWNER | |

## 2 · Ảnh xe / hình nền / trình chiếu (3 folder RIÊNG)

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 2.1 | Cài đặt › Hiển thị & đơn vị › **Hình xe** | Nói "Đang dùng hình xe mặc định" + 3 bước + nút **Sao chép đường dẫn** (`files/car/`) | OWNER | |
| 2.2 | Thay ảnh xe: chép ảnh top-down vào `files/car/` (USB/Files) | Mở lại màn chính → widget lốp/xe dùng ảnh MỚI | OWNER | |
| 2.3 | Cài đặt › Màn hình chính › **Hình nền** | Bước rõ + nút sao chép đường dẫn `files/wallpapers/` | OWNER | |
| 2.4 | Cài đặt › Màn hình chính › **Widget Trình chiếu ảnh** | Nói RÕ folder KHÁC (`files/photos/`) + nút sao chép | OWNER | |
| 2.5 | Bỏ ảnh vào `photos/` + đặt widget Trình chiếu ảnh | Ảnh trong `photos/` hiện + đổi vòng; KHÔNG lẫn với ảnh nền | OWNER | |

## 3 · Cài đặt › **Giọng nói** (menu MỚI, tách riêng)

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 3.1 | Mở rail Cài đặt | Có nhóm **"Giọng nói"** riêng (giữa *Tiện nghi xe* và *Hệ thống*) | OWNER | |
| 3.2 | Vào nhóm Giọng nói | Thấy: Hey Kachi · tải mô hình nghe · gói giọng đọc · giọng bé · đọc phản hồi · **App nhạc mặc định** · hỏi-xác-nhận · nguồn micro | OWNER | |
| 3.3 | Chọn **App nhạc mặc định** = YT Music (hoặc app đang cài) | Chip đổi + lưu | OWNER | |
| 3.4 | Nói "phát nhạc [tên bài]" (KHÔNG nêu app), không có nhạc đang phát | Mở đúng **app nhạc mặc định** vừa chọn | OWNER | |
| 3.5 | Đang phát nhạc app A → nói "phát bài X" (không nêu app) | Tiếp tục ở **app A** (không mở app default đè lên) | OWNER | |

## 4 · Voice — dẫn đường + sổ địa chỉ

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 4.1 | Cài đặt › Dẫn đường: thêm địa chỉ **Nhà** (dán lat,lng nếu có) | Lưu vào sổ (theo hồ sơ) | OWNER | |
| 4.2 | Nói "dẫn đường về nhà" | Mở app dẫn đường (mặc định đã chọn) tới đúng địa chỉ Nhà | OWNER | |
| 4.3 | Nói "dẫn đường tới [nơi] bằng vietmap" | Mở đúng VietMap (app được nêu) | OWNER | |
| 4.4 | Nói câu thường có chữ "nhà" (vd "nhà hàng gần đây") | KHÔNG bị hiểu nhầm thành dẫn-về-Nhà | OWNER | |

## 5 · Voice — điều khiển xe + câu trả lời

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 5.1 | Nói vài lệnh: "bật đèn đọc", "mở kính lái", "tăng nhiệt độ 24 độ" | Chạy đúng; câu trả lời KHÔNG còn đuôi "chưa kiểm trên xe" | OWNER | |
| 5.2 | Nói câu HỎI: "nhiệt độ đang bao nhiêu" | ĐỌC số, không bắn lệnh ghi | OWNER | |
| 5.3 | Bật "Hey Kachi" (nhóm Giọng nói) rồi gọi | (thử nghiệm) mở phiên nghe — ghi lại nhận hay không | OWNER | |

## 6 · Chiếu cụm + **bóng VietMap (bug vừa sửa)**

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 6.1 | Bật công tắc bóng VietMap (Cài đặt › Chiếu màn lên cụm hoặc Dẫn đường) | Autostart VietMap | OWNER | |
| 6.2 | **QUAN TRỌNG** — để autostart tự chạy (mạng chậm càng tốt để tái hiện bug cũ) | Kachi **chờ VietMap vào MAP thật** (không hạ khi còn ở flash) → **bóng LÊN cụm** mà KHÔNG phải bấm tay | OWNER | |
| 6.3 | Nếu bóng vẫn không lên | Ghi lại: VietMap dừng ở màn nào bao lâu → EM chỉnh SETTLE/timeout | OWNER→EM | |
| 6.4 | Chỉnh vị trí bóng (kéo-thả) | Bóng dời đúng chỗ | OWNER | |
| 6.5 | Chiếu một app lên cụm (nút nổi) rồi trả về | Lên/về cụm mượt | OWNER | |

## 7 · Tiện nghi + các nút xe (đi 1 lượt)

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 7.1 | Bấm lần lượt nút thanh nút xe + ô control (khoá/kính/cốp/đèn/ghế/điều hoà/lọc bụi/lấy gió…) | Nút nào chạy / không chạy — **ghi lại cái hỏng** | OWNER→EM | |
| 7.2 | Ghế mát/sưởi tự động, lọc bụi PM2.5, lấy gió khi nổ máy | Chạy như mong đợi | OWNER | |
| 7.3 | Mở "Chế độ kiểm thử qua adb" (Cài đặt › Hệ thống › Nâng cao) | Sau khi bật, các công cụ dev/chẩn đoán MỚI hiện; tắt thì ẩn | OWNER | |

## 8 · Giọng bé OTA (WP9)

| # | LÀM GÌ | KẾT QUẢ MONG ĐỢI | AI | KẾT QUẢ THẬT |
|---|--------|------------------|----|--------------|
| 8.1 | Cài đặt › Giọng nói → "Giọng Kachi bé" → **Tải** | Tải 1 file .zip (~10 MB) + tự giải nén trên xe | OWNER | |
| 8.2 | Chọn "Giọng phản hồi = giọng bé" + nói một lệnh | Câu trả lời đọc bằng giọng bé (câu lạ lùi về Piper) | OWNER | |

---

## Xử lý khi có mục HỎNG
- Owner ghi lại (chụp màn / nhớ hiện tượng) → báo EM.
- EM sửa **off-car** ngay trong buổi nếu tái hiện được (đa số UI/logic); cái cần đo HAL/xe thật thì EM chuẩn bị bản
  `vehicleTest` + đầu dò để owner chạy tại chỗ.
- Sửa xong → build bản mới → OTA → owner test lại đúng mục đó.

## Cột mốc kết thúc
Đi hết 8 mục, mọi mục PASS hoặc đã có cách xử → **baseline đóng dự án** (cập nhật PROJECT-BACKLOG + project-context).

---

## KẾT QUẢ ON-CAR 2026-09-21 (xe <car-ip>, DiLink3.0, đổ xe) — phần EM verify qua adb_raw.py

**Kết nối**: macOS adb bị chặn (`No route to host` dù cổng 5555 mở) — dùng `scripts/vehicle/kachi/adb_raw.py <car-ip> 5555` (pure-python, ký adbkey). ⚠ adbd churn (`host-xx already offline`) = BUG2 (Kachi giữ dadb loopback) — chập chờn nhưng dùng được.

| Mục | Kết quả EM verify | Trạng thái |
|---|---|---|
| 0.2 version | dumpsys: **1.87 (88)** cài OK (push+pm install qua adb_raw; run-as chặn nên pm install chạy shell riêng) | ✅ |
| 0.3 + 1.1 badge | Screenshot xe (đọc sub-agent, zoom 3×): **0 chấm amber** mọi tile dock/topbar | ✅ |
| — telemetry | state bridge: mọi chip số THẬT, 0 "—" (93%/539km · 32°C · 18µg · 14.9kWh · ghế mát mức 2) — HAL read live | ✅ |
| — app-in-slot | YouTube live-host trong ô (thumbnail thật), `embedded=true shell_usable=true` | ✅ |
| 5.1 voice reply | bridge `say "bật đèn đọc"` → `"✓ Bật Đèn đọc"` — **KHÔNG còn đuôi "chưa kiểm trên xe"** | ✅ |
| — quyền | `permissions.all_ok=true` "đủ quyền" | ✅ |
| — cast/cụm | cụm = `fission_bg_xdja` VD 1920×720 (đúng records); cast_enabled=true; FloatingBubbleService chạy | ✅ |
| — test-mode | đang BẬT (48′ còn lại) → bridge chạy được | ✅ |
| 2.4 folder photos | pref `vm_bubble_enabled=true`; 3 folder tách (code) | ✅ code |

**CHƯA verify được qua adb (cần OWNER bấm/nhìn/nổ máy)**:
- **§6.2 bóng VietMap (bug vừa sửa)** — cần một **CHU KỲ NỔ MÁY** để chạy đúng boot-autostart. `am start` tay không kích boot-path; release không log autostart. VietMap process từng chạy (pid 2767) rồi nền (không activity). ⇒ **Owner: tắt máy → nổ lại → nhìn cụm xem bóng VietMap có TỰ lên KHÔNG cần bấm** (mạng chậm càng tốt để soi bug cũ). Bóng không lên → ghi VietMap dừng màn nào bao lâu.
- **§3 nhóm Voice** — owner mở Cài đặt, xem rail có nhóm "Giọng nói" riêng + chọn App nhạc mặc định.
- **§7 nút xe** — owner bấm 1 lượt, ghi nút hỏng.
- **§8 giọng bé** — owner Tải trong Cài đặt › Giọng nói.

## BUG on-car 2026-09-21 (owner báo giữa buổi) — CHẨN ĐOÁN + XỬ

### BUG A — binding phím TẠCH (regression dai dẳng, gặp suốt)
[ĐO adb xe] service Kachi (label "ClusterNav — booster đọc", `NavAccessibilityService`, capabilities=9 = FILTER_KEY_EVENTS) **ENABLED nhưng KHÔNG Bound**; `/proc/loadavg`=**10.17** (8 lõi bão hoà). Đúng gốc 1.78: tải CPU cao → a11y rớt bind → `onKeyEvent` không bắt → phím chết. **FIX LIVE (đã làm)**: toggle `enabled_accessibility_services` (gỡ Kachi rồi thêm lại + `accessibility_enabled=1`) qua adb → service **BOUND lại** (xác nhận capabilities=9 giữ, load 8.2). ⇒ Owner **thử phím vô-lăng** — phải ăn lại.
- **FIX CODE (off-car)**: rebind timeout 20s (1.78) VẪN thua ở load>10. Cần (1) retry rebind bền bỉ hơn + (2) tự phát hiện "enabled-nhưng-không-bound" rồi tự toggle a11y ép bind (đúng thao tác live vừa làm) thay vì chỉ nút "Sửa ngay" thủ công.

### BUG B — Hey Kachi KHÔNG phản hồi
[ĐO adb xe] `voice_wake_enabled=true` NHƯNG `VoiceWakeService` KHÔNG chạy + `files/kws/` TRỐNG (model KWS chưa tải). Gốc: owner bật wake trên **1.84** (lúc model KWS CHƯA đăng repo → tải 404); model `voice/kws/` nay ĐÃ có trên repo (5 tệp, raw HTTP 200) từ lượt này. `setWakeEnabled(true)` CÓ gọi `WakeModelFetch.ensure` (tải model) nhưng chỉ chạy khi GẠT công tắc / boot — `pm install -r` phiên này không kích lại. **FIX LIVE cho owner**: Cài đặt › Giọng nói › **Hey Kachi → TẮT rồi BẬT lại** ⇒ tải model ~5 MB + dựng service; chờ tải xong rồi gọi "Hey Kachi".
- **LƯU Ý**: KWS gigaspeech ĐA NGỮ — records 1.81 ghi "bật = đo baseline, chưa chắc bắt câu gọi giọng Việt". Đây là **thử nghiệm**; tải xong mà vẫn không nhận ⇒ vấn đề độ chính xác KWS (không phải wiring), cần fine-tune keyword — việc riêng.

## 1.88 (89) 2026-09-21 — sửa tiếp 2 bug + thêm chỉ báo tải model

### Bug 1 tiếp — "lãng đãng" → "lãng"
[ĐO] parser ĐÚNG (gõ chữ → `«lãng đãng»`, YouTube phát đúng "Đen – Lãng đãng"). Gốc = **ASR nghe hụt âm cuối "đãng"** khi NÓI (giới hạn model zipformer-vi; tên bài là từ-vựng-mở nên không sửa được bằng lớp chính tả). Cần fine-tune model — backlog.

### Bug 2 — Hey Kachi: 3 lớp lỗi đã gỡ
1. **keywords.txt token sai vocab**: `Cannot find ID for token KA/CHI... Encode keywords failed` — token phiên âm không có trong tokens.txt của model gigaspeech. Sửa: chỉ dùng token có thật, verify từng token ∈ tokens.txt.
2. **nhãn @ đa từ**: `@HEY KACHI` (2 từ) → sherpa coi "KACHI" là token → encode fail. Sửa: nhãn 1 từ `@HeyKachi`/`@Kachi`.
3. **stale keywords kẹt**: `isReady` chỉ check presence (không sha) → keywords.txt cũ kẹt. Sửa: `WakeModelFetch.ensure` so sha keywords.txt với ghim, lệch → remove + tải lại (tự chữa trên boot/toggle).

### Chỉ báo tải model (owner: "không biết đã tải xong chưa")
Thêm dòng trạng thái dưới công tắc Hey Kachi: **Chưa tải / Đang tải …% / Sẵn sàng** (màu MUT2/AMBER/GREEN, tự refresh 1.5s). `WakeModelState` enum + `wakeModelStatus()`.

### ⚠ CÒN VƯỚNG — GitHub raw CDN cache
[ĐO xe 17:22] xe tải keywords.txt vẫn ra **223B/be924070 (bản @HEY KACHI cũ)** dù repo raw + APK pin đều **219B/bfff4fa8** — **CDN edge cache** giữ bản cũ ~5 phút. Self-heal ĐANG chạy đúng (từ chối file lệch ghim, sẽ thử lại). ⇒ Sau khi CDN hết cache (vài phút), lần trigger kế (boot/toggle/reinstall) sẽ tải khớp + KWS nạp được. **Owner: gạt Hey Kachi off→on lại sau ~5–10 phút**, hoặc nổ máy lại.

## 1.89 (90) 2026-09-21 — Hey Kachi model ĐÓNG THEO APK (owner: nhét 5MB vào APK, bỏ OTA)

- 5 tệp KWS (~5MB) chuyển vào `app/src/main/assets/voice/kws/` ⇒ đóng theo APK (APK 39→43MB). Update app = update luôn model, KHÔNG còn tải OTA / dính CDN cache.
- `WakeModelFetch.run` đổi từ `VoiceModelStore.install` (mạng) → `copyFromAssets` (chép assets→filesDir, sherpa cần path thật). 0 mạng, model luôn khớp phiên bản app.
- Keyword nhạy hơn (threshold 0.15/0.18, boost 2.5/2.2) + biến thể "Kachi" giọng Việt (ga/co) + "Kachi" trần.
- Cầu chì false-accept: latch vĩnh viễn → **tự clear sau 60s** (`fusedUntil`) — chống "nổ 1 lần rồi im hoài" khi thử nhiều lần.
- Dòng trạng thái dưới công tắc Hey Kachi: Chưa nạp / Đang chuẩn bị …% / Sẵn sàng.
- [ĐO] full 5 module xanh. Owner xác nhận wake nổ được nhiều lần (khó nghe chút vì model tiếng Anh). Fine-tune tiếng Việt = backlog.

## KẾT LUẬN Hey Kachi 2026-09-21 (owner: gác, chờ fine-tune model)

**Wiring đã HOÀN CHỈNH — không còn bug kỹ thuật:**
- Model đóng theo APK (assets, 0 mạng/CDN), copyFromAssets khi bật.
- Bộ nghe (`VoiceWakeService`) tự dựng lại khi mở Kachi (onResume sync) — hết chết ngầm sau app kill/reinstall. [ĐO] `:wake` proc lên + KWS `newFromFile` nạp encoder/decoder/joiner từ `files/kws/` + **0 encode-fail**.
- Cầu chì false-accept tự-clear 60s.
- Keyword: threshold 0.15/0.18, boost 2.5/2.2, biến thể "Kachi" giọng Việt + "Kachi" trần.
- Dòng trạng thái tải dưới công tắc.

**VẪN không gọi lên được** dù wiring OK ⇒ **GỐC = độ chính xác KWS**: model `sherpa-onnx-kws-zipformer-gigaspeech` là **tiếng ANH đa ngữ**, keyword là phiên âm tiếng Anh của "Kachi" ⇒ không bắt được "Kachi" phát âm giọng Việt. Đây KHÔNG phải bug sửa được bằng code — cần **fine-tune / thay model KWS tiếng Việt** (thu mẫu "Hey Kachi"/"Kachi" giọng Việt, train keyword model). = **BACKLOG (việc lớn, off-car)**.

⇒ **Owner GÁC Hey Kachi** tới khi fine-tune. Tính năng vẫn để đó (default TẮT / owner tự tắt), không chặn gì.
