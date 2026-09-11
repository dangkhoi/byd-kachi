# Handoff — phiên 2026-09-11 (tối): nới trần ô, bố cục động xong cả 3 bước

> **Trạng thái**: Session · **Ngày**: 2026-09-11 · **Mục đích**: giao lại điểm dừng của bước 3.

## Kết quả một dòng

Đã push (`116f30d`). Full 5 module **2628 / 0 lỗi**. Bố cục động **xong cả ba bước**: anh vẽ được tới **6 khung** và nó chạy thật.

## Cách tôi làm phần rủi ro nhất: chứng minh trước khi sửa

Bộ quyết-định-dựng-lại so số ô đang dựng với số ô của **bố cục sẵn**. Với bố cục tự vẽ 6 khung (bố cục sẵn 3 ô) nó thấy lệch ⇒ trả **"dựng lại tất cả"** ở **mọi** lần vẽ. Trên xe trạng thái đổi **2 nhịp/giây**, nghĩa là ô bị tháo/gắn 2 lần mỗi giây ⇒ **app đang chiếu bị nhả/gắn liên tục** và anh **mất cú bấm**. Đúng loại lỗi đã tốn nhiều phiên để sửa.

Nên tôi **viết test chứng minh lỗi đó có thật trước**, rồi mới sửa. Cách sửa giữ nguyên hành vi cũ khi không truyền số ô mới, để bộ test hơn 1000 tổ hợp đang canh chỗ đó **không bị xáo trộn**.

## Vì sao trần là 6

Trần bị chặn bởi **cỡ ô dùng được**, không phải bởi lưới. Trên màn 1920×1080: **6 ô** thì mỗi ô ~640×360, **còn đặt được app**; **8 ô** thì ~480×270, app trong ô nhỏ tới mức vô dụng. Lưới 12×6 về lý thuyết cho tới 36 khung — con số đó vô nghĩa.

## ⚠ Một lỗi SẬP mà test không bắt được

Nới trần xong, **bố cục mặc định của launcher** vẫn ghim cứng 4 ô ⇒ ném lỗi **ngay lúc nạp lớp** ⇒ **launcher sập ở lần chạy đầu**, tức đúng lúc anh chưa có cấu hình nào. Không test nào bắt được vì lớp đó nằm phía Android.

Tôi tìm ra vì **đọc mã sản phẩm trước khi sửa test**. Nếu chỉ sửa hai test đang đỏ thì đã đẩy đi một lỗi sập.

Sửa **tận gốc**: thêm hàm dựng không cần biết trần ô, và **đưa bố cục mặc định về phần thuần** để kiểm được off-car — trước đây nó nằm chỗ không test nào chạm tới, và đó chính là chỗ suýt sập.

## ⚠ Lỗi thứ hai, và tôi tự sập bẫy khi sửa nó

Bấm nút **bố cục sẵn** trong khi đang dùng bố cục tự vẽ: dựng lại **toàn bộ ô** nhưng **màn hình không đổi gì**. Anh tưởng nút hỏng, còn app trong ô bị nhả/gắn vô ích. Sửa: **chọn bố cục sẵn = bỏ bố cục tự vẽ** — hành động tường minh phải có tác dụng, và đây cũng là đường về bố cục sẵn không cần mở bảng vẽ.

Nhưng lần sửa đầu tôi xoá bố cục ở **một** chỗ (biến của màn chính) mà quên khung vẽ giữ **bản sao riêng** ⇒ đo được: cấu hình đã xoá mà màn hình **vẫn 6 khung**. Đúng loại lỗi hai-bản-sao mà cả bước 2 đi dọn. Nay ép mọi thay đổi qua **một đường duy nhất**, kèm test **đếm số chỗ ghi**.

## Đo được gì

| Phép đo | Kết quả |
|---|---|
| Bố cục 6 khung | **6/6 khung đúng từng pixel** |
| Có dựng lại ô liên tục? | **0 lần** trong 30 giây; số view = số ô = 6 mọi lần vẽ |
| Bấm bố cục sẵn | Bỏ bố cục tự vẽ, **4 ô** hiện đúng vị trí |
| Dữ liệu cũ | **Tự tương thích**, không cần chuyển đổi |
| Thử phá | 3 phép, **đỏ đúng chỗ cả 3** |

## Còn tồn

- **Ca 2 nhịp/giây chỉ khoá bằng test, chưa đo trên xe.** Off-car trạng thái xe gần như không đổi (tôi đo được 3 lần vẽ trong 30 giây) nên không quan sát được nhịp thật. Phần quyết định thì đã khoá bằng test đúng ca đó.
- **Chưa đo 6 ô mà mỗi ô chứa app thật** — mỗi ô chứa app cần một màn ảo riêng, tức 6 màn ảo. Đo được với widget; app trong ô là việc trên xe.
- **Chưa đổi được thứ tự khung.** Thứ tự quyết định app nào vào khung nào khi sắp lại, nên đây là thứ anh sẽ muốn.
- **Chưa có lượt soát độc lập phần code** cho cả ba bước.

## Chờ owner

1. **Trần 6 khung** có hợp không (tôi tự chọn, lý do ở trên)?
2. **Ô lưới không vuông** — chấp nhận, hay đổi lưới (đánh đổi: mất tính chất bố cục sẵn khớp từng pixel)?
3. **Thứ tự khung** có cần đổi được không?

Cộng bảy câu cũ: ngưỡng lốp · gói "Rời xe" khoá xe khi đóng kính hỏng · nhịp chờ 400ms · icon tới từng ô · vòng kiểm quyền có lặp không · thanh trên có cần tăng độ đục khi bật hình nền · widget và nền dùng chung chu kỳ hay tách.

## Thứ tự burn còn lại

S1 (dựng lại màn Cài đặt, owner đã duyệt 09-09) · W5 (camera 360) → U5 (đa ngôn ngữ) · T1 · P6.

Bố cục động đã xong nên **việc lớn kế tiếp là S1**.

## Tài liệu

`docs/specs/kachi-dynamic-grid.html` §Bước 3 — B3.1 chứng minh rủi ro · B3.3 lỗi sập · B3.4 bẫy hai-bản-sao · B3.5 số đo
