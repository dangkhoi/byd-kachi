# Handoff — phiên 2026-09-11 (chiều-2): trình vẽ bố cục

> **Trạng thái**: Session · **Ngày**: 2026-09-11 · **Mục đích**: giao lại điểm dừng của bước 2 bố cục động.

## Kết quả một dòng

Đã push (`72bfb37`). Full 5 module **2621 / 0 lỗi** (+19). **0 tệp XML** bị đụng. Cây git sạch.

Anh giờ **tự vẽ được bố cục riêng** thay vì chỉ chọn trong 5 bố cục sẵn — và bố cục tự vẽ **chạy thật ngay**, không phải bản nháp chờ bước sau.

## Việc đầu tiên không phải vẽ, mà là gom nguồn

Trước khi viết một dòng giao diện nào, tôi đếm xem có bao nhiêu chỗ đang tự suy ra "bao nhiêu ô, ô nằm ở đâu". Kết quả: **sáu chỗ** — ba ở màn chính, và **ba ở bộ sắp cửa sổ app**.

Nếu tôi bỏ sót một chỗ thì hậu quả là: **màn hình vẽ theo bố cục mới, nhưng cửa sổ app lại đặt theo bố cục cũ** — app nằm lệch khỏi ô. Đây đúng là hình dạng của lỗi P-bug2 (lỗi từng ảnh hưởng cả xe) mà tôi đã sửa ở gói 1.

Nên tôi gom về **một nguồn duy nhất**, và thêm **test quét mã nguồn** chặn việc suy ra ở chỗ khác quay lại. Chặn bằng test, không bằng lời nhắc trong tài liệu.

## Luật lùi an toàn

Bố cục tự vẽ chỉ thắng khi nó **dùng được thật**. Ba ca lùi về bố cục sẵn:

| Ca | Vì sao |
|---|---|
| Dữ liệu hỏng hoặc rỗng | Anh vẫn có màn hình dùng được, không phải màn trắng |
| Khung đè nhau | Trình vẽ đã chặn lúc lưu — đây là lưới an toàn cho dữ liệu cũ |
| **Nhiều khung hơn trần** | Ca này **có thật**: bản sau nới trần rồi anh hạ cấp bản. Bố cục 6 khung sẽ lùi về bố cục sẵn chứ không làm mất ô |

Và nó **nói lý do** chứ không im lặng bỏ qua.

## Phạm vi tôi cố ý giới hạn

Trần ô hiện tại là **4**. Tôi **giữ nguyên** trần đó ở bước này. Nhờ vậy bố cục anh vẽ chạy được ngay với toàn bộ đường chiếu app, sắp cửa sổ và kéo-thả đang sống — mà **không phải chạm** phần rủi ro nhất còn lại. Nới trần để bước sau.

Trình vẽ **nói thẳng** trần này thay vì im lặng làm mờ nút Thêm.

## Đo được gì

| Phép đo | Kết quả |
|---|---|
| Bố cục tự vẽ có chạy thật? | **3/3 khung đúng từng pixel** cả ngang lẫn dọc; cột trái **773px** so với **1132px** của bố cục sẵn ⇒ khác rõ |
| Kéo đổi cỡ | "phủ kín màn" → "còn **4 ô trống**" (đúng 2 cột × 2 dòng) |
| Kéo cho đè nhau | "**khung 1 và khung 2 đè lên nhau**", nút Lưu bị làm mờ, hai khung tô đỏ, khung thứ ba giữ màu |
| Về bố cục sẵn | Cột trái về **1132px** và khoá lưu bị xoá |
| Thử phá | 4 phép, **đỏ đúng chỗ cả 4** |

## Hai lỗi nhìn thấy được, chỉ lộ khi đọc ảnh

Test xanh hết mà vẫn có hai lỗi:

1. **Nền bảng trong mờ nên giao diện launcher lọt xuyên qua** — tiêu đề đè chữ ngày ở thanh trên, nút Đóng đè nút "Cài đặt", thanh nút xe nằm ngay dưới hàng nút của bảng. Đây là bề mặt làm-một-việc, thấy màn chính phía sau không được gì ⇒ **nền đục**.
2. **Khung vẽ giật cỡ** khi dòng lỗi hiện/mất — giật ngay giữa lúc đang kéo. ⇒ **giữ chỗ** cho dòng lỗi.

Cái đầu còn có tác dụng phụ tốt cho quyền riêng tư: nền đục che hết thanh trên nên ảnh chụp gửi lên repo công khai không lọt nội dung màn chính. Bản đầu thì đã lọt.

## ⚠ Ba lần phép đo của tôi sai trước khi code sai — trong cùng một ngày

1. Hai khung chụp giống nhau từng byte ⇒ tôi tưởng widget không đổi ảnh; thật ra **màn máy ảo ngủ**.
2. So khung pixel ra "0/3 khớp" ⇒ tôi lấy bề rộng **cả màn 1920** trong khi vùng làm việc rộng **1878**, lệch 21px. Tính lại thì **3/3 đúng từng pixel**.
3. Kiểm "đã về bố cục sẵn" ra "CHƯA" ⇒ bộ lọc của tôi bắt **khung chứa** thay vì ô.

**Luật**: khi số đo nói code sai, **kiểm phép đo trước**. Cả ba lần code đều đúng.

Và một lỗi do chính tôi gây khi đo: tôi đọc tệp cấu hình trên máy ảo rồi đẩy ngược lên **mà không kiểm lệnh đọc có thành công** — lệnh thất bại (hai máy ảo cùng cắm nên adb không biết chọn máy), tệp rỗng, và tôi **xoá luôn cấu hình**. Chỉ là dữ liệu thử, nhưng cách làm sai; nay có chốt kiểm trước khi đẩy.

## Còn tồn

- **Ô lưới không vuông** (cao hơn rộng ~12%). Đây là **hệ quả toán học**, không phải lỗi vẽ: lưới 12/6 = 2.0 còn màn 16:9 = 1.78. Muốn ô vuông phải đổi lưới (ví dụ 16×9), mà đổi thì **mất** tính chất 4 bố cục sẵn khớp đúng từng pixel. Tôi giữ 12×6.
- **Tối đa 4 khung** — bước sau nới.
- **Chưa đổi được thứ tự khung** trong trình vẽ. Thứ tự quyết định app nào vào khung nào khi sắp lại, nên đây là thứ anh sẽ muốn.
- **Chưa có lượt soát độc lập phần code.** Phần nhìn thì có tác nhân độc lập đọc ảnh, và chính nó tìm ra hai lỗi ở trên.

## Chờ owner

Bảy câu cũ, cộng hai câu của phiên này:

1. **Ô lưới không vuông** — chấp nhận, hay muốn tôi đổi lưới (đánh đổi: mất tính chất bố cục sẵn khớp từng pixel)?
2. **Thứ tự khung** — có cần đổi được ngay không, hay để bước sau?

## Thứ tự burn còn lại

**P9 bước 3** (nới trần ô + hồ sơ — **rủi ro cao nhất còn lại**, nhưng đã giảm nhiều: nguồn duy nhất đã gom, phép chứng minh 100 tổ hợp đã có, và bố cục tự vẽ đã chạy thật ở trần 4) → S1 (dựng lại màn Cài đặt, owner đã duyệt) · W5 → U5 (đa ngôn ngữ) · T1 · P6.

## Tài liệu

`docs/specs/kachi-dynamic-grid.html` §Bước 2 — B2.1 gom nguồn · B2.2 lùi an toàn · B2.5 số đo · B2.6 hai lỗi đọc ảnh · B2.8 nhật ký
