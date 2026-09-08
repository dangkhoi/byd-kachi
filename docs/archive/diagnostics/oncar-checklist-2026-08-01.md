# Checklist lên xe — sáng 2026-08-01 (bản 0.89)

Mục tiêu buổi test: **chứng minh nền tảng cast đã ổn định**, tức chiếu/trả nhiều lần liên tiếp mà
KHÔNG lần nào phải đụng adb hay xoá dữ liệu app. Đêm 31/7 việc này không làm nổi — phải `pm clear` 5 lần.

Mọi thứ dưới đây làm được **chỉ bằng tay trên xe**, không cần laptop. Phần cần adb đánh dấu rõ là tuỳ chọn.

---

## 0. Chuẩn bị (1 phút)

- [ ] Cài APK 0.89, mở ClusterNav, xem tiêu đề có ghi `v0.89` không (CLAUDE.md §9 — không đoán bản).
- [ ] Cụm đang hiện **đồng hồ gốc**. Nếu đang hiện app cũ → bấm **Khắc phục sự cố → Trả cụm về đồng hồ (cứu hộ)**.

> Nếu bước dọn này chạy được: đó **đã là** một kết quả quan trọng — đêm qua không có nút nào làm được việc đó.

---

## 1. Bài chính: 10 lượt chiếu/trả liên tiếp — tiêu chí nghiệm thu

Đây là bài quan trọng nhất. Làm đủ 10 lượt, xen kẽ 2 app (VietMap và GMaps).

Mỗi lượt:
1. Mở app trên màn chính.
2. Chạm **ô lớn phía trên** của nút nổi.
3. Nhìn **màn cụm vật lý** — app có hiện lên thật không? (không tính app hiện trên màn giữa)
4. Chạm lại ô lớn → app phải về màn chính, cụm về đồng hồ.

| Lượt | App | Cụm hiện app? | Trả về OK? | Phải đụng adb? | Ghi chú |
|---|---|---|---|---|---|
| 1 | VietMap | | | | |
| 2 | GMaps | | | | |
| 3 | VietMap | | | | |
| 4 | GMaps | | | | |
| 5 | VietMap | | | | |
| 6 | GMaps | | | | |
| 7 | VietMap | | | | |
| 8 | GMaps | | | | |
| 9 | VietMap | | | | |
| 10 | GMaps | | | | |

**ĐẠT** = 10/10 cụm hiện đúng app, 10/10 trả về được, **0 lần** phải đụng adb hoặc xoá dữ liệu.

Nếu có lượt nào hỏng: ghi rõ **lượt thứ mấy**, **app nào**, **chữ hiện trên màn app**, rồi bấm cứu hộ và chạy tiếp
— đừng dừng cả bài, vì cần biết nó hỏng 1/10 hay 5/10.

---

## 2. Bài kiểm gốc rễ: app nguội (cái làm hỏng đêm qua)

Đêm qua VietMap vừa mở nguội xong bị cast luôn → verification đo hụt → kẹt. Nay đã sửa (chờ 3s thay vì 0.5s).

- [ ] **Tắt hẳn VietMap** (vuốt khỏi recents, hoặc buộc dừng trong Cài đặt).
- [ ] Mở lại VietMap, **chiếu ngay lập tức** (không đợi nó vẽ xong).
- [ ] Cụm có hiện VietMap không? Có báo lỗi "cần phục hồi" giả không?

Đây là ca đã hỏng chắc chắn đêm qua — nếu nay chạy được thì bản vá timeout đúng.

---

## 3. Bài kiểm cứu hộ (thay cho `pm clear`)

Cố tình làm kẹt rồi tự thoát bằng nút, không dùng adb:

- [ ] Đang chiếu một app → **tắt/buộc dừng chính app đó** trong khi nó đang trên cụm.
- [ ] Mở ClusterNav xem nó báo gì.
- [ ] Bấm **Khắc phục sự cố → Trả cụm về đồng hồ (cứu hộ)**.
- [ ] Cụm có về đồng hồ gốc không? App có báo đúng kết quả không?

⚠️ Chú ý cách app nói: **"Đã trả… cụm đã về đồng hồ"** = xong thật. **"CHƯA xong: …"** = chưa xong,
đừng tin là đã sạch. Hai câu này cố ý khác nhau — đêm qua app từng báo xong trong khi cụm vẫn nguyên.

- [ ] Sau khi cứu hộ, **chiếu lại được ngay** không? (không cần khởi động lại app)

---

## 4. Nút nổi

- [ ] **Kéo được** nút nổi đi khắp màn hình chưa? (đêm qua bị dính một chỗ — đã sửa)
- [ ] Kéo xong thả ra: nó có **giữ nguyên vị trí mới** sau khi mở lại app không?
- [ ] Kéo nút nhưng **không** vô tình kích hoạt chiếu chứ? (kéo ≠ chạm)
- [ ] Nút giờ **to hơn, có 3 ô**: ô lớn trên + 2 ô nhỏ dưới. Ô nào đang có app thì **tô đặc**.
- [ ] **Luật mới (chủ dự án chốt 01/8):** đang có app trên cụm thì chạm ô NÀO cũng là **trả về màn chính**,
      cả ba ô đều tô đặc. Không còn ô khoá nào. Kiểm: đang chiếu full → chạm ô nhỏ trái → app phải về màn chính.
- [ ] **Cụm rỗng → chạm ô nhỏ trái** → app đang mở phải lên **nửa TRÁI** của cụm, nửa phải để trống.
- [ ] Mở app khác → chạm ô nhỏ **phải** → app đó lấp nốt nửa phải. Hai app hiện song song.
- [ ] Đổi tỉ lệ (50-50 / 30-70 / 70-30) ở panel Cast trên Home rồi làm lại → tỉ lệ có đúng không?

---

## 5. Autostart

- [ ] Bật ô tick "Tự khởi động", chọn một app.
- [ ] Thoát hẳn ClusterNav, mở lại.
- [ ] App đã chọn có tự lên cụm không?

---

## 6. Bong bóng VietMap (PIP) — kiểm cái đã sửa đêm qua

Đêm qua app-op `PICTURE_IN_PICTURE` của VietMap bị đặt `deny` sót lại từ một transaction kẹt, làm bong bóng
tốc độ của VietMap câm lặng. Đã sửa để không bao giờ nhận trạng thái bị chặn làm mốc.

- [ ] Trên **màn chính**: mở VietMap rồi bấm Home → bong bóng tốc độ của VietMap có tự hiện không?
- [ ] Nếu KHÔNG hiện: (tuỳ chọn, cần adb) `adb shell appops get vn.vietmap.live PICTURE_IN_PICTURE`
      — nếu ra `deny`/`ignore` thì bản vá chưa đủ, báo lại.

---

## 7. KHÔNG làm trong buổi này

- ❌ **Đừng chiếu CarPlay / Android Auto.** Đã đo: đường duy nhất cho app loại này (`am display move-stack`)
  làm **sập system_server** 3/3 lần, task biến mất, phải cắm lại cáp. Đây là bug framework Android 10,
  không vá được từ phía app. Đường thay thế (`am stack move-task`) đã tìm ra nhưng **chưa đo** — để buổi riêng.
  Chi tiết: `docs/diagnostics/carplay-aa-cluster-placement-research-2026-08-01.md`.
- ⚠️ Chiếu 2 app: nay bấm được (mục 9), nhưng CHƯA từng chạy trên xe lần nào. Test nó SAU khi mục 1 xanh — đừng để nó làm hỏng bài nghiệm thu chính.

---

## 8. Nếu cần lấy log (tuỳ chọn, cần laptop)

```
adb connect <vehicle-ip>:5555
adb shell run-as com.byd.clusternav cat files/cast-v2/session.env
adb shell am stack list | grep -E "Stack id=|taskId="
```

Trong `session.env`, trường `stable=` cho biết phiên đến từ đường nào:
- `runtime-bootstrap` + `seal-dl3-cold-bootstrap-v1` → **đúng**, đã chạy đủ opcode mở cụm.
- `runtime-migration` + `~` → đường tắt cũ; sau bản vá này nó vẫn có thể xuất hiện lúc mới mở app,
  nhưng lần cast đầu tiên **phải** chạy bootstrap thật rồi mới đặt app.

---

## 9. Chiếu 2 app — nay BẤM ĐƯỢC

Phép đo em định nhờ anh làm sáng nay **không cần nữa**. Nghĩ lại thì em đã chặn nhầm trục: chia cụm là
quyết định theo chiều **NGANG** (1920 chia tại một ranh giới — đã đo, `am stack list` đọc lại đúng
`0 / 960 / 1920`), còn trục **DỌC** là thứ hệ thống tự phát, app không chọn và cũng không cần chọn. Hai app
chỉ cần không đè nhau theo chiều ngang là đủ.

Nên giờ: cụm rỗng vẫn cắt ô được (lấy khung từ display), và phép xác minh chỉ ràng buộc **dải ngang**, còn
dọc thì nhận đúng thứ hệ thống trả về. Chạy được với cả hai khả năng (`[0,0][960,720]` lẫn `[0,90][960,810]`)
— có test khoá cả hai.

**Cách dùng:** cụm rỗng → chạm ô trái (hoặc phải) → app đang mở vào nửa đó → mở app khác → chạm ô còn lại.
Trả về thì chạm đúng ô của app đó, hoặc "Trả cụm về đồng hồ" để dọn cả hai.

Vẫn CHƯA đo được (không chặn việc dùng): app nào nằm bên nào khi cả hai cùng `visible=true` — xác minh chứng
minh "đúng hai app, đúng hai dải ngang", chưa chứng minh "A đúng là bên trái". Xấu nhất là hai app đổi chỗ
nhìn thấy được, chạm lại một lần là xong.

---

## Tóm tắt: những gì đã đổi trong 0.89

| Sửa | Triệu chứng đêm qua |
|---|---|
| Luôn gửi 3 opcode mở cụm (bỏ cổng `adopt` sai) | Cast "thành công" nhưng cụm vật lý không đổi gì |
| Chặn đường tắt migration bỏ qua bootstrap | Cùng triệu chứng trên, tầng thứ hai của cùng gốc rễ |
| Chờ xác minh 0.5s → 3s + nghỉ thật giữa 2 mẫu | Báo "cần phục hồi" giả dù cast đã thành công |
| Nút "Trả cụm về đồng hồ" (cứu hộ) | Phải `pm clear` 5 lần mới thoát kẹt |
| Sửa kéo nút nổi (hỏng do refactor 9d70f62) | Nút nổi dính một chỗ |
| Nút nổi 3 ô + là bản đồ trạng thái cụm | Không nhìn ra cụm đang có gì |
| App-op PIP luôn có đường trả lại | Bong bóng tốc độ VietMap câm lặng |
| Quan sát được bounds theo task | Nền cho chỉnh kích thước + chiếu 2 app |
| Chiếu 2 app: chia ô theo dải NGANG, dọc nhận theo hệ thống | Trước đó bị chặn vì đòi biết dải dọc — thứ không điều khiển được |
| Luật chạm: ô có app ⇒ trả về, ô trống ⇒ chiếu | Trước đó có ô khoá, hai câu trả lời khác nhau cho cùng một câu hỏi |
