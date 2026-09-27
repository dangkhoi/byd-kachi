> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 · **Mục đích**: Dàn bài của hướng dẫn sử dụng Kachi có hình cho người lái (bản 2.77). Xếp theo thứ tự người mới học, không theo thứ tự mã nguồn. Đây là DÀN BÀI — chưa viết nội dung.

> ⛔ **ẢNH XE THẬT ĐÃ BỊ GỠ KHỎI REPO (27/09 tối)** — 7 ảnh chép vào `docs/guide/img/` chứa **biển số xe người khác**, bản đồ sống chỉ đúng vị trí + giờ, số nhà, và avatar tài khoản cá nhân; chúng đã lọt lên repo CÔNG KHAI một lần và được gỡ bằng ghi đè lịch sử. `.gitignore` nay chặn `car-*.png` · `cum-*.png` · `camera-frame-*.png` · `cluster-fb-*.png` **độc lập đường dẫn**. ⇒ Mọi ảnh 🚗 trong tệp này phải **chụp lại** với: app dẫn đường ĐÓNG, **không đăng nhập tài khoản nào**, và không có xe khác trong khung — hoặc che biển số/tên đường/avatar trước khi dùng. Ảnh cũ giữ ngoài repo, không bao giờ commit.


## Quy ước dùng trong tệp này

- `shot: <tên tệp>` — ảnh chụp từ máy ảo, danh sách chụp chi tiết ở `SHOTLIST.md`.
- 🚗 — chỉ chụp được TRÊN XE THẬT; kèm tên ảnh đã có sẵn (chép từ scratchpad phiên 27/09 vào `docs/guide/img/`).
- ⚠ CHƯA CÓ BẢN KÊ — mục này sáu agent kê hôm nay KHÔNG đi qua; phải kê từ mã nguồn trước khi viết.
- Mọi nhãn trong hướng dẫn phải trích từ `app/src/main/res/values/strings_kachi.xml` (hoặc hai bộ đăng ký Kotlin thuần `core/.../WidgetRegistry.kt`, `core/.../CapabilityGroups.kt`, `core/.../LauncherRequirements.kt`) — không tự đặt chữ, không chép từ `docs/HUONG-DAN-KACHI.md` (tài liệu đó đã cũ: còn quảng cáo 5 màn dev bị gỡ ngày 2026-09-21).
- Ảnh 🚗 có sẵn chụp ở bản 2.74–2.76. Trước khi dán vào bài phải đối chiếu lại với 2.77 (riêng phần camera chắc chắn khác: 2.77 bỏ khối "Nâng cao (kỹ thuật)" và hàng "Nguồn").

## Ảnh trên xe đã có (chép vào `docs/guide/img/`)

| Tệp đích | Nguồn (scratchpad phiên 27/09) | Dùng cho |
|---|---|---|
| `car-man-hinh-that.png` | `car-0927/car-home-274.png` | §2, §4 — màn chính thật: chip sống, YouTube nhúng trong ô, bảng lốp 2.6/2.8 bar, thanh nút dọc bên trái |
| `car-thanh-tren-chip-that.png` | cắt từ ảnh trên | §6 — chip có số thật |
| `car-o-lop-that.png` | cắt từ ảnh trên | §5 — bảng lốp có số thật |
| `car-camera-tron-xi-nhan.png` | `car-0927/car-round.png` | §8 — ô camera tròn nổi trên màn chính lúc xi-nhan trái |
| `car-khung-anh-camera-tho.png` | `car-0927/camera-frame-20260927-095818.png` | §8 — khung ảnh thô AVM (4 vòng mắt cá) → giải thích vì sao cần lật/xoay/nắn |
| `car-cum-chieu-1..3.png` | `cluster-276/zoom-c0.png`, `zoom-c1.png`, `zoom-c2.png` | §9 — ảnh chụp cụm đồng hồ thật đang hiện app dẫn đường |
| `car-cum-framebuffer.png` | `car-0927/cluster-fb-d1.png` | §9 — ảnh cụm chụp thẳng từ máy (nét, không loá) |

---

## §1. Kachi là gì và nó thay cái gì

- **Mục tiêu**: 1 trang. Người đọc hiểu Kachi thay màn hình chính của đầu xe; bấm HOME là về Kachi; mọi app cũ vẫn còn.
- Nội dung: Kachi làm gì (gom app + thông tin xe + nút điều khiển vào một màn) · không làm gì (không thay hệ thống xe, không cảnh báo an toàn, không phải sản phẩm của BYD — trích nguyên câu miễn trừ trong app) · cần biết trước: **"—" nghĩa là CHƯA ĐỌC ĐƯỢC, không phải lỗi**.
- Diagram **D1**: hai hộp cạnh nhau "Màn hình chính của xe (DiLink)" → mũi tên phím HOME → "Kachi", kèm nhánh "app cũ vẫn mở được từ Kachi".
- shot: `man-hinh-chinh-tong-quan.png` · 🚗 `car-man-hinh-that.png` (đặt cạnh nhau: máy ảo cho rõ bố cục, ảnh xe cho thấy số thật).

## §2. Màn hình chính — ba dải

- **Mục tiêu**: nhìn một ảnh là biết màn chia làm ba phần và mỗi phần để làm gì.
- Rows phải phủ: thanh trạng thái trên (giờ · ngày · chip xe · nút Nói với xe / Ứng dụng / Cài đặt / chip hồ sơ) · vùng ô làm việc (1–6 ô) · thanh nút xe (4 viền hoặc ẩn) · hình nền.
- Ba câu phải nói thẳng: chip KHÔNG bấm được · nền KHÔNG có giữ-lâu-để-đổi (mọi cấu hình đi qua nút Cài đặt) · thanh trên không ẩn được cả dải.
- Diagram **D2**: khung màn chia ba dải có nhãn, ghi rõ dải nào đặt lại chỗ được ở đâu.
- shot: `man-hinh-chinh-tong-quan.png`, `thanh-trang-thai-tren.png`, `vung-o-lam-viec.png`, `thanh-nut-xe.png` · 🚗 `car-man-hinh-that.png`.

## §3. Sắp màn theo ý mình — bố cục và hình nền

- **Mục tiêu**: đổi được số ô và đặt được hình nền.
- Rows: câu nhắc hồ sơ đầu trang · **Bố cục sẵn** (1 ô · 2 cột · 2 hàng · 3 ô · 4 ô · Tự vẽ) · "Vẽ bố cục riêng…" (Thêm khung · Xoá khung đang chọn · Về bố cục sẵn · Lưu · Đóng · dòng trạng thái · dòng lỗi) · **Hình nền** (Dùng ảnh làm hình nền · Cách thêm ảnh · Sao chép đường dẫn thư mục · Đổi ảnh mỗi · Cách phủ · Làm tối ảnh) · mục "Widget Trình chiếu ảnh" với đường dẫn RIÊNG.
- Phải nói: đổi bố cục CHỈ ở đây (thanh trên không còn nút bố cục) · bố cục "3 ô" lệch 1,55 : 1 nên **không vẽ lại được** bằng lưới 12×6 · bố cục tự vẽ có thể bị bỏ qua và lý do in ngay dưới "Bố cục màn hình" · hai thư mục ảnh khác nhau.
- Diagram **D3**: vẽ hộp 6 bố cục sẵn đúng tỉ lệ (đặc biệt 3 ô = 1,55 : 1, cột phải chia đôi) + một ô lưới 12 × 6 cho phần tự vẽ, trần 6 khung.
- shot: `cai-dat-man-hinh-chinh.png`, `ve-bo-cuc.png`.

## §4. Đưa app và thông tin vào ô

- **Mục tiêu**: chương QUAN TRỌNG NHẤT — đây là chỗ người dùng bí nhất.
- Rows: bảng "Đặt widget hoặc mở app vào ô này" (câu hướng dẫn · Ứng dụng · Widget của app khác · Nhóm — xem cả cụm cùng lúc · Thẻ dựng tay · Từng mục riêng · nút đáy **Đặt N mục / Bỏ widget** · câu "Ô chứa tối đa 8 mục…") · bảng "Mở ứng dụng" (Gần đây · Tất cả ứng dụng).
- Ba thao tác trên ô: chạm ô TRỐNG (chạm đâu cũng được) · nút **⇄** giữa mép trên ô đã có nội dung · giữ-lâu-rồi-kéo = đổi chỗ hai ô.
- Phải nói: **không có nút ✕** — muốn làm trống ô thì ⇄ → bỏ chọn hết → nút đáy đổi thành "Bỏ widget" · chạm thân ô đang có app là MỞ LẠI app đó, không mở ngăn kéo · một app chỉ nằm ở một ô (ô cũ tự trống) · "Mở ứng dụng" trên thanh trên mở app TOÀN MÀN, không gắn vào ô, về bằng phím HOME.
- Diagram **D4**: sơ đồ quyết định — ô trống / ô có nội dung → đường nào dẫn tới ngăn kéo, đường nào đổi chỗ, đường nào làm trống.
- shot: `ngan-keo-dat-vao-o.png`, `the-dung-tay.png`, `nhom-kha-nang.png`, `o-tung-muc-rieng.png`, `ngan-keo-tran-8-muc.png`, `ngan-keo-mo-ung-dung.png`, `vung-o-lam-viec.png` · 🚗 `car-man-hinh-that.png` (ô có app chạy thật).

## §5. Các ô thông tin — đọc gì, bấm gì

- **Mục tiêu**: catalogue có hình cho 9 thẻ dựng tay + 8 nhóm + mục lẻ; mở đầu bằng quy ước "—".
- Rows/mục phải phủ:
  - 9 **thẻ dựng tay**: Năng lượng · Áp suất lốp · Không khí · Đồng hồ + thời tiết · Đang phát · Trạng thái xe · Tốc độ · Bảng tổng hợp · Trình chiếu ảnh.
  - 8 **nhóm**: Lốp · Kính · Cửa & khoang · Đèn · Khí hậu & không khí · Năng lượng · Sức khỏe pin · Chuyến đi. (Số thành viên từng nhóm: KHÔNG viết tay — kiểm lại `CapabilityGroups.kt` lúc viết.)
  - **Từng mục riêng**: bốn dáng vẽ (vòng đo · số to · viên chữ · dải) · huy hiệu con mắt (chỉ xem) / bàn tay (bấm được) · viên "chưa kiểm trên xe".
  - Ô nén: 1 mục = bản đầy đủ; 2–8 mục = lưới thẻ nén; "Bảng tổng hợp" nén chỉ còn chữ "Tổng hợp".
- Phải nói: trong ô nhóm chạm con số KHÔNG làm gì — lệnh chỉ đi từ HÀNG NÚT ở đáy, và chỉ Kính · Cửa & khoang · Đèn mới có hàng nút · nút giữa ô nhạc luôn mang hình ▶, bấm lúc đang phát là TẠM DỪNG · cốp bấm được nhưng không đọc lại được · xe dự án không có cửa sổ trời nhưng nút "Nóc" vẫn còn · trần 8 mục/ô · đơn vị đổi chung ở Cài đặt.
- Diagram **D9**: ba tầng của một ô nhóm (đầu ô · ô con chỉ xem · hàng nút) + bảng nghĩa màu nền ô con (xám / accent / hổ phách / đỏ).
- shot: `o-nang-luong.png`, `o-khong-khi.png`, `o-toc-do.png`, `o-trang-thai-xe.png`, `o-bang-tong-hop.png`, `o-ap-suat-lop.png`, `o-dang-phat.png`, `o-dong-ho-thoi-tiet.png`, `o-trinh-chieu-anh.png`, `ben-trong-o-nhom.png`, `o-cua-va-khoang.png`, `o-nhieu-widget.png` · 🚗 `car-o-lop-that.png` (bắt buộc: bản máy ảo chỉ có "—").

## §6. Thanh trạng thái trên & thanh nút xe

- **Mục tiêu**: chọn thông tin hiện cạnh đồng hồ, chọn nút nào nằm trên thanh nút và đặt thanh ở viền nào.
- Rows: bộ chọn chip ("Đang bật · N/16" · nút ◀ ▶ trên từng ô đang bật · các khối theo lĩnh vực) · "Hiện nhãn trên thanh trên" · "Vị trí trên thanh trên" (6 vật) · "Nút mic trên thanh trạng thái" · "Hiện thanh nút xe" · "Viền đặt thanh" (Dưới/Trái/Phải/Trên) · "Chọn nút trên thanh… (N đang bật)" (khối Launcher · Nhóm · Từng mục riêng · nút **Áp dụng (N)**) · "Vị trí trên thanh nút xe" · dòng cảnh báo ⚠ nhóm An toàn/Động lực/Giải trí.
- Phải nói: trần chip là **16** (lấy đúng con số app in ra, đừng lấy trong KDoc) · thanh trên chỉ nhận mục XEM · thanh nút KHÔNG có trần · bỏ tích rồi Áp dụng là nút rời khỏi thanh · nút mic cần CẢ công tắc bật CẢ mô hình nghe đã tải.
- Diagram **D5**: khung màn với 4 viền đặt thanh nút + trạng thái ẩn (vùng ô lấp trọn màn).
- shot: `cai-dat-thanh-tren-va-thanh-nut.png`, `chon-nut-thanh-nut-xe.png`, `thanh-nut-xe.png`, `cai-dat-nut-mic-thanh-tren.png` · 🚗 `car-thanh-tren-chip-that.png`.

## §7. Nói với xe

- **Mục tiêu**: nói được một câu trong 3 phút, kể cả khi chưa cài gì.
- Thứ tự bắt buộc: **(1) tải mô hình nghe → (2) mới nói được**. Chưa tải thì nút mic biến mất — nói ngay câu này ở đầu chương.
- Rows: Cài đặt › Giọng nói — "Hey Kachi" (+ cách nghe ASR/KWS + dòng trạng thái model câu gọi) · Nhận dạng giọng nói tại máy (trạng thái + nút Tải/Cập nhật/Gỡ + ghi chú bỏ nạp sẵn) · Giọng đọc offline (trạng thái + nút + ghi chú chép USB) · Đọc phản hồi bằng giọng · Ưu tiên giọng offline · **Hỏi xác nhận trước khi chạy** (4 kính · Cốp sau · Cửa sổ trời · Mở hết kính · Đổi hồ sơ · Mở bài vừa đọc · Đọc to câu hỏi xác nhận) · Nguồn micro (4 chip) · App nhạc mặc định (5 chip) · **Câu lệnh nói được**.
- Bốn lối vào lượt nói: ô "Nói với xe" trên thanh nút · nút mic thanh trên · phím vô-lăng gán đích "Kachi nghe (tại máy)" · "Hey Kachi".
- Tấm chữ khi nghe: vòng sóng âm · một dòng chữ (Đang chuẩn bị… → Đang nghe… → chữ nghe được → Đã nghe + câu trả lời) · "Nói tiếp…" · hỏi lại khi chưa hiểu · hộp Xác nhận (nút "Đồng ý", nói "đồng ý"/"huỷ") · chạm ra ngoài để thôi · **phím Back KHÔNG huỷ được**.
- Danh sách câu: in theo 13 nhóm do máy sinh (Năng lượng & sạc · Động lực & tốc độ · Khí hậu & không khí · Lốp · Thân xe·cửa·kính · Đèn · Danh tính·khoá · Giải trí·cụm·HUD · Nhạc · Dẫn đường · Ứng dụng·ô·bố cục · Hồ sơ tài xế). **Không chép tay cả danh sách vào bài** — in 8–10 câu hay dùng, rồi chỉ user vào màn "Câu lệnh nói được" trong app (nó luôn khớp với thứ xe hiểu).
- Phải nói: "Hey Kachi" mặc định TẮT, là thử nghiệm, chỉ nghe khi màn sáng, tự tắt nếu nghe nhầm nhiều · không đổi được câu gọi sang "OK Kachi" · chưa đóng được app bằng giọng.
- Diagram **D6**: dải thời gian một lượt nói (bíp → 8 s nghe → trả lời → 1,2 s → "Nói tiếp…" 5 s, tối đa 5 lượt nối) và nhánh hỏi-lại/xác-nhận.
- Diagram **D11**: hai hộp "cái tai" (mô hình nghe, vài trăm MB, có sha256, chép USB được) và "cái miệng" (gói giọng đọc) → điều kiện để nút mic hiện.
- shot: `cai-dat-giong-noi.png`, `cau-lenh-noi-duoc.png`, `noi-voi-xe-dang-nghe.png`, `noi-voi-xe-xac-nhan.png`, `cai-dat-phim-vo-lang-kachi-nghe.png`.

## §8. Tiện nghi xe — camera xi-nhan, ghế, không khí, mưa → sấy

- **Mục tiêu**: mọi hàng của nhóm "Tiện nghi xe" bản **2.77**.
- **8.1 Camera theo xi-nhan** (đúng 10 hàng của 2.77, KHÔNG có "Nâng cao (kỹ thuật)", KHÔNG có hàng "Nguồn"):
  Bật camera khi xi-nhan · Hiện camera lên màn cụm · Xi-nhan trái hiện ở · Xi-nhan phải hiện ở · Xi-nhan trái: xoay video · Xi-nhan phải: xoay video · Xi-nhan trái: lật gương · Xi-nhan phải: lật gương · Hình khung camera (Chữ nhật / Tròn / Theo cụm) · Nắn hình (bớt cong ống kính).
  Phải nói: mặc định TẮT — không bật thì xi-nhan không thấy gì · mỗi bên đặt độc lập · chip có tác dụng ở LƯỢT XI-NHAN TIẾP THEO · "Theo cụm" chỉ khác khi đang hiện trên cụm · tay gương của ảnh CHƯA đo được trên xe nên "Lật gương" để người lái tự bật khi thấy ngược.
  Diagram **D7**: (a) hai ô camera ↔ bốn lựa chọn góc màn, mũi tên chéo cho thấy trái-có-thể-hiện-phải; (b) chuỗi xử lý ảnh: khung thô → lật gương → xoay → nắn → hình khung.
  shot: `cai-dat-camera-xi-nhan.png` · 🚗 `car-camera-tron-xi-nhan.png` (bắt buộc — máy ảo không lên hình) · 🚗 `car-khung-anh-camera-tho.png` (giải thích vì sao phải chỉnh).
- **8.2 Mưa thì tự bật sấy kính**: công tắc chính + Sấy kính trước + Sấy kính sau & gương + dòng "chỉ tắt cái sấy do Kachi bật". Phải nói: bỏ tích cả hai ô con = tắt thật; hai ô con bị làm mờ chứ không ẩn.
- **8.3 Nổ máy thì tự lấy gió trong**: bật là áp NGAY; tắt chỉ ngừng tự bật, không tắt chế độ đang chạy trên xe.
- **8.4 Ghế mát / sưởi**: công tắc tự chỉnh theo nhiệt độ · chip Làm mát/Sưởi · sơ đồ ghế chạm để đổi mức (tắt → M1 → M2).
- **8.5 Lọc bụi mịn**: Tự lọc khi không khí bẩn · dòng "Bụi mịn hiện tại: …" + đồng hồ · nút **Lọc ngay một lượt** (chạy bất kể công tắc).
- Phải nói chung: gần như toàn bộ chương này **cần xe**; máy ảo luôn "chưa đọc được", sơ đồ luôn vẽ 2 ghế · và phân biệt rõ: nút trên **thanh nút xe** = làm ngay một lần, trang này = đặt LUẬT tự động.
- shot: `cai-dat-tien-nghi-xe.png`.

## §9. Dẫn đường, cụm đồng hồ và chiếu màn ⚠ CHƯA CÓ BẢN KÊ

- **Mục tiêu**: chỉ đường trên cụm, sổ địa chỉ, tự dẫn đường theo lịch, chiếu app lên cụm.
- Đã có bản kê: **Tự dẫn đường theo lịch** (danh sách lịch · công tắc từng hàng · nút Sửa · Thêm lịch… · trần 8 lịch) và hộp thêm/sửa (Bật lịch này · Khung giờ HH:mm · 7 ô thứ · Chỉ dẫn khi đã có GPS · Dẫn tới · Bằng app · Lưu · Xoá). Phải nói: cần ít nhất một địa chỉ trong Sổ địa chỉ trước · hai lý do "sao nó không dẫn" (sai thứ / đang chờ GPS) · địa chỉ theo HỒ SƠ còn lịch theo XE.
- ⚠ Chưa kê: nhóm **Dẫn đường & cụm đồng hồ** (Sổ địa chỉ, chỉ đường, biển báo tốc độ, bong bóng) và nhóm **Chiếu màn lên cụm** (đưa app lên cụm, chia đôi, tự chiếu) — cần một lượt kê từ `SettingsSectionsNav*.kt` / `SettingsSectionsCast*.kt` trước khi viết.
- shot: `cai-dat-tu-dan-duong-theo-lich.png`, `hop-them-lich-dan-duong.png`, `cai-dat-so-dia-chi.png` ⚠, `cai-dat-dan-duong-cum.png` ⚠, `cai-dat-chieu-len-cum.png` ⚠ · 🚗 `car-cum-chieu-1..3.png`, `car-cum-framebuffer.png`.

## §10. Hiển thị & đơn vị · Phím vô-lăng ⚠ CHƯA CÓ BẢN KÊ

- Nhóm **Hiển thị & đơn vị** (đơn vị đo · sáng/tối · ngôn ngữ) và nhóm **Phím vô-lăng** (gán nút vật lý) chưa được kê hôm nay; mới chỉ biết một đích gán là "Kachi nghe (tại máy)".
- Vẫn phải có trong hướng dẫn vì §5 nhắc tới đơn vị (bar/psi/kPa, °C/°F) và §7 nhắc tới phím vô-lăng.
- shot: `cai-dat-hien-thi-don-vi.png` ⚠, `cai-dat-phim-vo-lang.png` ⚠.

## §11. Hồ sơ tài xế

- **Mục tiêu**: hiểu hồ sơ giữ cái gì và đổi hồ sơ là đổi cả bộ.
- Rows: câu mở đầu trang · thẻ từng hồ sơ (chạm để đổi) · Đổi tên hồ sơ · Xoá · **HỒ SƠ LÚC NỔ MÁY** (chip "Gần nhất" hoặc tên một hồ sơ) · Thêm hồ sơ (bản sao của…) · Xuất hồ sơ (backup) · Nhập hồ sơ từ file · hộp "Đổi hồ sơ tài xế" từ chip trên thanh trên.
- Phải nói: nút Xoá không hiện ở hồ sơ đang dùng và hồ sơ cuối cùng, xoá KHÔNG hỏi lại · "Thêm hồ sơ" tạo BẢN SAO · xuất/nhập KHÔNG có hộp chọn tệp (ghi/đọc đúng một thư mục, đường dẫn chỉ hiện trong thông báo chớp) · máy tiếng Anh: đổi tên "Mặc định" mà không sửa gì thì cố tình không làm gì.
- Diagram **D8**: hai cột "Theo HỒ SƠ" (bố cục, nội dung ô, chip, thanh nút, giao diện, đơn vị, ngôn ngữ, hình nền, sổ địa chỉ, Tự mở khi nổ máy) vs "Theo XE" (Hồ sơ lúc nổ máy, Giữ Kachi làm màn hình chính, Chạy dịch vụ ở nền, Tự động cập nhật, chiếu lên cụm, mọi thứ Giọng nói, mưa→sấy, lịch dẫn đường, mô hình giọng đã tải).
- shot: `cai-dat-ho-so-tai-xe.png`, `hop-doi-ho-so.png`, `hop-hoi-ten-ho-so.png`.

## §12. Giữ Kachi chạy tốt — quyền, khởi động, cập nhật

- **Mục tiêu**: đặt Kachi làm màn hình chính, hiểu danh sách quyền, cập nhật app.
- Rows: **Quyền** (chỉ hiện khi THIẾU; 8 hàng có thể gặp: Kênh điều khiển cửa sổ · Cho phép cửa sổ tự do · Là màn hình chính · Vẽ trên màn khác · Đọc thông báo · Trợ năng · Micro · Định vị — mỗi hàng có câu "Thiếu thì: …") · **Màn hình chính** (dòng trạng thái + nút Đặt/Bỏ chọn + Giữ Kachi làm màn hình chính khi nổ máy) · **Khởi động** (Tự mở khi nổ máy · Chạy dịch vụ dẫn đường/cụm ở nền) · **Bảo trì** (Tự động cập nhật · Kiểm tra cập nhật · Dừng toàn bộ dẫn đường · Khởi động lại launcher) · **Nâng cao** (chỉ còn "Chế độ kiểm thử qua adb") · **Giới thiệu** (phiên bản · tên gói · giấy phép · miễn trừ).
- Phải nói: "Là màn hình chính" xuất hiện hai lần (hàng quyền chỉ báo — nút thật ở mục Màn hình chính) · "Bỏ chọn" tắt luôn công tắc giữ-home · chữ TRÊN NÚT "Kiểm tra cập nhật" chính là chỗ báo kết quả · cập nhật cần mạng (cắm CarPlay/AA là đầu xe tắt WiFi) · "Khởi động lại launcher" chạy ngay, màn nhá — không phải crash · Chế độ kiểm thử tự tắt sau 60 phút.
- Diagram **D10**: bản đồ 11 nhóm Cài đặt theo đúng thứ tự rail, để người đọc biết đi đâu tìm gì.
- shot: `cai-dat-he-thong-va-quyen.png`, `hop-co-ban-moi.png`, `cai-dat-gioi-thieu.png`.

## §13. Tra cứu nhanh — "tưởng lỗi mà không phải"

- Bảng hai cột: hiện tượng → lời giải thích + chỗ sửa. Lấy từ phần gotchas của sáu bản kê, chọn những cái người lái gặp thật:
  1. Ô hiện "—" khắp nơi → chưa đọc được dữ liệu xe, không phải lỗi.
  2. Không xoá được nội dung một ô → ⇄ → bỏ chọn hết → "Bỏ widget".
  3. Chạm ô có app lại mở app chứ không mở ngăn kéo → dùng nút ⇄.
  4. Không tìm thấy nút đổi bố cục trên thanh trên → Cài đặt › Màn hình chính.
  5. Đặt app vào ô mới thì ô cũ trống → đúng như thiết kế.
  6. Bật công tắc mic mà không thấy nút mic → chưa tải mô hình nghe.
  7. Xi-nhan mà không có camera → chưa bật "Bật camera khi xi-nhan"; đổi chip rồi phải chờ lượt xi-nhan sau.
  8. Hình camera ngược tay → bật "Lật gương" đúng bên.
  9. Lịch không dẫn → sai thứ, hoặc đang chờ GPS.
  10. Đổi hồ sơ tưởng mất hết cấu hình → xem bảng D8.
  11. Bỏ ảnh mà không thấy → nhầm giữa thư mục ảnh nền và thư mục Trình chiếu ảnh.
  12. Nút ▶ của ô nhạc vẫn là ▶ khi đang phát → bấm là tạm dừng.
  13. Bảng "Cửa & khoang" không bao giờ có chấm cốp → xe không có cảm biến cốp.
  14. Màn nhá sau khi bấm "Khởi động lại launcher" → đúng.
- Kèm một mục "Báo lỗi thế nào": đọc số bản ở Cài đặt › Giới thiệu (đừng đoán), chụp màn hình gửi về.

---

**Tổng: 13 mục · 47 ảnh chụp máy ảo · 9 ảnh trên xe (🚗) · 11 diagram (D1–D11).**
