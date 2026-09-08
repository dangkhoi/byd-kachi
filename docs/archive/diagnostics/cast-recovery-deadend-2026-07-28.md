# Cast kẹt vĩnh viễn ở RECOVERY_PENDING — đo trên xe 2026-07-28

Xe: DiLink3.0 (BYD_AUTO), adb `<vehicle-ip>:5555`, app **v0.73** (versionCode 73).
Người báo: dangkhoi — "cài lên là không chạy được, cast thử CP thì đơ, rồi xong, không có gì xảy ra cả".

## Đã chứng minh trên xe (không phải suy luận)

1. **Transport SỐNG.** Mở màn Cast → gateway mở **114 phiên adb** trong 10 giây
   (`adbd: write thread spawning` → `adb client authorized` → `connection terminated`, mỗi phiên ~100 ms).
   Khoá ADB của app đã được cấp phép: `AdbDebuggingManager: … state = 4, alwaysAllow = true`.
   ⇒ Giả thuyết "chưa cấp phép khoá ADB" **SAI**, đã bác bằng log.

2. **App kẹt ở RECOVERY_PENDING.** Thẻ trạng thái: *"Đang chờ phục hồi · Đang chờ xác nhận từ cụm"*.
   Mọi nút `CHIẾU <app> LÊN CỤM` và toàn bộ hàng chỉnh khung **disabled**.
   Bấm `CHIẾU APPLE CARPLAY LÊN CỤM` → không có thao tác nào được phát (chỉ có 18 phiên adb của
   nhịp quan sát định kỳ; không có `am start --display`, không đổi windowing mode).
   ⇒ "bấm chiếu không có gì xảy ra" là **nút bị khoá**, không phải treo luồng UI.

3. **Đường thoát duy nhất KHÔNG thoát được.** Footer chỉ còn link *"Khắc phục sự cố"*; mở ra 5 nút,
   chỉ **"Thử kết nối lại"** bật, 4 nút kia xám. Bấm nó → chạy 18 phiên adb rồi **trạng thái không đổi**.
   ⇒ Dead-end thật sự: không có thao tác nào trong UI đưa app ra khỏi trạng thái này.

4. **Mâu thuẫn giữa hai bề mặt.** Hành động được bật là `RETRY_CONNECT_BOUNDED`, theo bảng hợp đồng phục hồi
   trong `docs/specs/clusternav-uxui-rebaseline.html` thì nó thuộc substate `TRANSPORT_PREMUTATION_IDLE`
   = *"Chưa kết nối được; **chưa thay đổi cụm**"* — trong khi dòng trạng thái lại nói *"Đang chờ xác nhận
   từ cụm"*. Hai bề mặt nói hai chuyện khác nhau; ít nhất một cái sai.

## Nghi (chưa chốt) — vì sao không bao giờ hội tụ

`HANDOFF-2026-07-27` §1 đã đóng Q1 với kết quả **ÂM TÍNH**: không có tín hiệu chỉ-đọc nào của Android phân
biệt "cụm hiện app" với "cụm hiện đồng hồ". Nếu đường phục hồi cần "xác nhận từ cụm" để chuyển trạng thái
thì nó **đang chờ một quan sát mà nền tảng đã được chứng minh là không tạo ra được** → chờ vĩnh viễn.

Cách chốt: đọc envelope bền (`transaction`, `recoverySubstate`, `ledger`) qua màn Chẩn đoán. **Chưa lấy được**
— `DiagActivity` không export nên `am start` bị từ chối, `uiautomator dump` trả `null root node`, và app là
bản release trên máy `ro.debuggable=0` nên `run-as` không đọc được prefs. Phải mở bằng tay trên xe.

## Lỗi UI đi kèm (cùng đợt dựng lại 27/7)

- Màn Cast có **~800 px trống** giữa danh sách app và footer; phải cuộn qua một màn trắng mới thấy
  "Khắc phục sự cố".
- Hàng 5 nút phục hồi khi bung ra **nằm dưới thanh điều hướng của xe**, phải cuộn thêm mới đọc được.
- Thẻ trạng thái ghi "Chưa chọn app" trong khi danh sách đang hiển thị Android Auto được chọn.

## Luật rút ra

`NoDeadEndStateTest` xanh off-car nhưng UI thật vẫn dead-end: test đang canh *bảng ánh xạ trạng thái → hành
động*, không canh *hành động đó có thật sự đổi được trạng thái không*. Test hồi quy tiếp theo phải khoá:
**mọi trạng thái phục hồi phải có ít nhất một hành động mà sau khi thực thi thành công thì trạng thái ĐỔI**,
và mọi trạng thái phục hồi phải có deadline — hết hạn thì tự rơi về một trạng thái người dùng thao tác được.
