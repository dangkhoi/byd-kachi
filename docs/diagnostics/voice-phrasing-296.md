# Câu kết thúc + câu phản hồi giọng nói — 2.96 R12

> **Trạng thái**: Current · **Ngày**: 2026-10-07 · **Spec**: `docs/specs/kachi-296-plan.html` §3 R12 / T7
> Owner 07/10: *"thêm cả cái voice để bye bye Kachi khi xử lý xong việc, đang không nghe được rõ… bai bai, gút bai,
> tạm biệt, cảm ơn, thoát đi… các message khi xử lý nó chưa tự nhiên, 'Tắt kính lái' → Đóng kính lại, 'Sưởi ghế
> phụ: Tắt' → đã tắt sưởi ghế phụ"*.

Mức bằng chứng: mọi câu dưới đây **[ĐO]** bằng unit test `:core` (`VoicePhrasing296Test`, `VoiceEndWordsTest`) và
— cho phần ý định/preview — bằng harness `scripts/emulator/voice-e2e.sh` trên máy ảo (xem mục E2E cuối tệp).
Giọng đọc thật (Piper/hệ thống) trên xe: **[CHƯA BIẾT]** — chưa nghe trên xe.

## 1. Câu kết thúc phiên (`VoiceEndWords`)

**Trước (2.95)** — 15 cụm (bỏ dấu): `bye` · `bai` · `tạm biệt` · `xong` · `xong rồi` · `xong việc` · `thôi` · `đủ rồi` ·
`đóng lại` · `tắt đi` (**dòng chết** — `stripCourtesy` cắt `đi` trước nên không bao giờ khớp) · `cảm ơn` · `cảm ơn
nhé` · `không cần nữa` · `thoát` · `dừng lại`; từ đệm `ơi/ờ/à/nhé/rồi/ok/okay/ừ/ừm/vậy/thế`. Không nối được nhiều cụm,
không gọi tên Kachi được, và **không cụm nào có trong tệp hotword** ⇒ mô hình hay nghe trượt (*"đang không nghe được rõ"*).

**Sau (2.96)** — bảng có dấu viết tay `VoiceEndWords.SPOKEN` (nguồn chung cho so khớp + hotword):

| Nhóm | Cụm |
|---|---|
| tạm biệt | tạm biệt · bai · bye · bai bai · bye bye · bái bai · bay bay · gút bai · gút bái · gút bay · good bye · goodbye · hẹn gặp lại |
| xong / đủ | xong · xong rồi · xong việc · xong hết · là xong · đủ rồi · hết rồi · kết thúc |
| thôi | thôi · thôi được rồi · thôi không cần · không cần · không cần nữa · không cần đâu |
| cảm ơn | cảm ơn · cám ơn · cảm ơn nhiều · cám ơn nhiều · thank you · thanks · thanh kiu |
| thoát | thoát · thoát ra (+ *"thoát đi"* qua cắt lịch sự) · đóng lại · dừng lại |

- Từ đệm thêm: `nha · oke · dạ · nhiều · đi · nào`. Tên gọi bỏ qua ở mọi vị trí: `Kachi · ka chi · ca chi · cát chi ·
  kha chi · bạn · em` ⇒ *"tạm biệt Kachi"*, *"cảm ơn bạn nhé"*.
- **Nối nhiều cụm**: *"xong rồi cảm ơn nhé"*, *"ok cảm ơn tạm biệt"*, *"thôi được rồi cảm ơn Kachi"*.
- **Vẫn chỉ khớp CẢ CÂU**: *"cảm ơn bật đèn đọc"*, *"tạm biệt rồi mở nhạc"*, *"thôi lấy gió ngoài"* ⇒ KHÔNG kết thúc.
- **Cố ý không thêm** (va chạm lệnh thật): *"đóng / đóng đi / tắt / tắt đi"* trơn (thiếu đối tượng ⇒ hỏi lại *"Đóng gì?"*),
  *"dừng"* trơn (= tạm dừng nhạc), *"chào"* (người ta cũng nói *"chào Kachi"* để bắt đầu), *"đúng rồi"* (= đồng ý).
- **Hotword**: 23 dòng có dấu (`VoiceEndWords.HOTWORDS`, viết HOA ở tầng chuẩn hoá) vào `SherpaPhraseHotwords.phrases()`:
  `TẠM BIỆT · BAI BAI · BÁI BAI · BAY BAY · GÚT BAI · GÚT BÁI · GÚT BAY · HẸN GẶP LẠI · XONG RỒI · XONG VIỆC · XONG HẾT ·
  LÀ XONG · ĐỦ RỒI · HẾT RỒI · KẾT THÚC · THÔI ĐƯỢC RỒI · THÔI KHÔNG CẦN · KHÔNG CẦN · CẢM ƠN · CÁM ƠN · THOÁT RA · ĐÓNG
  LẠI · DỪNG LẠI` (dòng một từ bị tầng lọc bỏ; dạng nối dài *"cảm ơn nhiều"* cố ý không đưa vào vì `dropPrefixes` sẽ xoá
  mất dòng ngắn *"CẢM ƠN"*). Dạng Latin tiếng Anh (*good bye, thank you*) không vào hotword (mô hình VN không phát ra).
  ⚠ Hiệu quả bias trên âm thật: **[CHƯA BIẾT]** — cần lượt WAV/xe (ca `say` của harness đi tầng CHỮ, không qua ASR).
- Câu đáp khi kết thúc: `Tạm biệt` ⇒ **`Tạm biệt, hẹn gặp lại`** (EN `Bye` ⇒ `Bye, see you`). Không đại từ — giữ giọng
  trung tính như mọi câu khác của Kachi.

## 2. Câu phản hồi sau khi làm lệnh — trước → sau

Tấm chữ hiện dòng có ✓/✗; giọng đọc nói câu ghép bởi `VoiceFeedbackPhrase.merge`.

| Lệnh | Tấm chữ 2.95 | Giọng 2.95 | Tấm chữ 2.96 | Giọng 2.96 |
|---|---|---|---|---|
| đóng kính lái (xe đọc lại khớp) | ✓ Tắt Kính lái | Đã tắt kính lái | ✓ Đã đóng kính lái | Đã đóng kính lái |
| đóng kính lái (kính còn đang chạy / chưa đọc lại được) | ✓ Tắt Kính lái | Đã tắt kính lái | ✓ Đang đóng kính lái | Đang đóng kính lái |
| mở kính phụ / 4 kính / cửa sổ trời | ✓ Bật Kính phụ | Đã bật kính phụ | ✓ Đang mở kính phụ (khớp: Đã mở…) | Đang mở kính phụ |
| tắt sưởi ghế phụ | ✓ Sưởi ghế phụ: Tắt | Đã sưởi ghế phụ: Tắt | ✓ Đã tắt sưởi ghế phụ | Đã tắt sưởi ghế phụ |
| sưởi ghế phụ mức 1 | ✓ Sưởi ghế phụ: Mức 1 | Đã sưởi ghế phụ: Mức 1 | ✓ Đã bật sưởi ghế phụ mức 1 | Đã bật sưởi ghế phụ mức 1 |
| mát ghế lái mức 2 | ✓ Mát ghế lái: Mức 2 | Đã mát ghế lái: Mức 2 | ✓ Đã bật mát ghế lái mức 2 | Đã bật mát ghế lái mức 2 |
| gió mức 3 | ✓ Đặt Gió = 3 | Đã đặt gió 3 | ✓ Đã đặt gió mức 3 | Đã đặt gió mức 3 |
| nhiệt độ 24 | ✓ Đặt Nhiệt độ = 24 | Đã đặt nhiệt độ 24 | ✓ Đã đặt nhiệt độ 24 | Đã đặt nhiệt độ 24 |
| tăng gió 2 nấc | ✓ Tăng Gió 2 nấc | Đã tăng gió 2 nấc | ✓ Đã tăng gió 2 nấc | Đã tăng gió 2 nấc |
| gió về AUTO (nấc đáy) | ✓ Gió: AUTO | **Đã gió: AUTO** | ✓ Đã để gió ở AUTO | Đã để gió ở AUTO |
| bật đèn đọc | ✓ Bật Đèn đọc | Đã bật đèn đọc | ✓ Đã bật đèn đọc | Đã bật đèn đọc |
| nút "Đóng tất cả kính" | ✓ Bấm Đóng tất cả kính | Đã bấm đóng tất cả kính | ✓ Đang đóng tất cả kính | Đang đóng tất cả kính |
| gói "Mở hết kính" | ✓ Chạy gói Mở hết kính | Đã chạy gói mở hết kính | ✓ Đã mở hết kính | Đã mở hết kính |
| mở YouTube | ✓ Mở ứng dụng YouTube | Đã mở ứng dụng YouTube | ✓ Đã mở YouTube | Đã mở YouTube |
| mở YouTube vào ô 2 | ✓ Mở ứng dụng YouTube vào ô 2 | Đã mở ứng dụng YouTube vào ô 2 | ✓ Đã mở YouTube vào ô 2 | Đã mở YouTube vào ô 2 |
| bố cục 2 cột | ✓ Bố cục 2 cột | **Đã bố cục 2 cột** | ✓ Đã chuyển sang bố cục 2 cột | Đã chuyển sang bố cục 2 cột |
| dẫn đường tới Bitexco | ✓ Dẫn đường tới Bitexco | Đã dẫn đường tới Bitexco | ✓ Đã bắt đầu dẫn đường tới Bitexco | Đã bắt đầu dẫn đường tới Bitexco |
| mở cài đặt | ✓ Mở Cài đặt | Đã mở cài đặt | ✓ Đã mở cài đặt | Đã mở cài đặt |
| mở / tắt camera sau | ✓ Mở Camera sau | Đã mở camera sau | ✓ Đã mở camera sau | Đã mở camera sau |
| câu ghép: bật đèn đọc + tắt sưởi ghế phụ | (2 dòng) | Đã bật đèn đọc, **sưởi ghế phụ: Tắt** | (2 dòng) | Đã bật đèn đọc, tắt sưởi ghế phụ |
| câu ghép: bật đèn đọc + đóng kính (đang chạy) | (2 dòng) | Đã bật đèn đọc, tắt kính lái | (2 dòng) | Đã bật đèn đọc, đang đóng kính lái |
| xe từ chối "đóng kính lái" | ✗ Tắt Kính lái — xe không nhận lệnh | Chưa tắt kính lái, xe không nhận lệnh | ✗ Đóng kính lái — xe không nhận lệnh | Chưa đóng kính lái, xe không nhận lệnh |
| hỏi lại (nếu đã tích) | Bật Cửa sổ trời? | — | Mở cửa sổ trời? | — |
| kết thúc phiên | Tạm biệt | Tạm biệt | Tạm biệt, hẹn gặp lại | Tạm biệt, hẹn gặp lại |

**Giữ nguyên có chủ ý** (đã tự nhiên, hoặc câu đúng mức bằng chứng): `Đã gửi Nhiệt độ 24 — xe báo 23` (lệch đọc lại) ·
`… — chưa kiểm trên xe` (đuôi hedge theo mức bằng chứng) · các ca làm được MỘT PHẦN (*"✓ Phát nhạc — đã mở YouTube; chưa
có phiên nhạc nào…"*, *"✓ Dẫn đường tới X — VietMap chưa nhận điểm đến…"*) vẫn dùng câu xem-trước — đổi sang *"Đã phát
nhạc"* sẽ tự mâu thuẫn với đuôi câu · câu đọc số (*"Pin (SOC): 80 %"*) · *"Đã huỷ"* · câu không hiểu/hỏi lại.

## 3. Luật (vì sao viết thế)

- **Động từ theo nghĩa nút, không theo loại ô** — TOGGLE của bộ phận mô-tơ (`CtlSafetyPolicy.MOVES_SLOWLY`: kính,
  cửa sổ trời, cốp, rèm) đọc Mở/Đóng; SELECT có lựa chọn 0 = *"Tắt"* đọc Bật…mức N / Tắt…; STEP thang ≤ 10 nấc đọc
  *"mức N"*; BUTTON/gói có nhãn đã mở bằng một động từ của `VoiceGrammar.VERBS` không chồng *"Bấm"/"Chạy gói"*. Không
  có `if (id == …)` nào (CLAUDE.md §7).
- **Không hứa hơn thứ code biết** — `doneConfirmed` (xe đọc lại khớp) ⇒ *"Đã"*; `done` cho bộ phận mô-tơ (chưa đọc lại
  được, hoặc còn đang chạy sau 300 ms) ⇒ *"Đang"*; bộ phận đổi tức thì ⇒ *"Đã"* như 2.95 (+ đuôi hedge nếu mức bằng
  chứng thấp). Camera: controller đã báo kết quả (`Outcome`) ⇒ *"Đã"*.
- **Câu quá khứ dựng ở `:core`** (`VoiceReplyDone`), `merge` nhận lời dẫn sẵn có (*"Đã "*/*"Đang "*) và gộp lời dẫn
  trùng trong câu ghép — hết lỗi ghép mù *"Đã" + câu danh từ*.
- Nhãn đứng sau động từ hạ chữ đầu (*"Đóng kính lái"*), chữ viết tắt giữ nguyên (`VoiceFeedbackPhrase.decap`).
- Tiếng Anh: chỉ đổi động từ kính (*Close/Open*) + chữ thường nhãn; khuôn *"Done: …"* giữ. ZH/TH/MS: 3 mẫu mới
  (`Đặt {0} mức {1}` · `Đặt {0} {1}` · `Đã để {0} ở {1}`) + câu tạm biệt mới đã dịch trong `core/src/main/resources/i18n/*.tsv`.

## 4. Kiểm

- Unit: `:core:test` **4834 / 0** · `:app:testDebugUnitTest` **2205 / 0** (đếm từ JUnit XML, 2026-10-07). Bài mới:
  `VoicePhrasing296Test` (12 ca) · `VoiceEndWordsTest` (+4 ca: biến thể owner, va chạm, parser → `EndSession`, hotword
  sống sót qua tầng lọc tệp thật).
- E2E T1 (`scripts/emulator/voice-e2e.sh --only say`, AVD `clusternav10`, bản vehicleTest dựng từ nhánh này) **[ĐO]**:
  **120/121 PASS**; 13 ca mới t116–t128 (9 `end` + 4 `end-neg`) đều PASS. Ca đỏ duy nhất t39 *"mở Maps"* = side-effect
  `resumed:` hết hạn 8 s (không liên quan câu chữ: lời đáp ✓ Đã mở Maps đúng); chạy lại riêng ⇒ PASS.
- **Chưa đo**: T2 (WAV → ASR) cho câu kết thúc — hiệu quả bias của 23 dòng hotword trên âm thật **[CHƯA BIẾT]**; giọng
  đọc trên xe.

## 5. Còn để ngỏ (không làm trong lượt này)

- *"thôi lấy gió ngoài"* ⇒ *"Tắt lấy gió trong"* (đúng nghĩa nhưng vòng vo) — câu tự nhiên là *"Chuyển sang lấy gió
  ngoài"*; cần dữ liệu cặp-lựa-chọn cho nút recirc, không làm bằng `if (id == "recirc")`.
- Câu đọc số (*"Pin (SOC): 80 %"*) giữ dạng nhãn: giá trị.
