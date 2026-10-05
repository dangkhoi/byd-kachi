# CLUSTER-RECT-SEAL — chiếu chữ nhật lên cụm Seal mà vẫn giữ km/h: không làm được trên firmware hiện tại

> **Trạng thái**: Current · **Cập nhật**: 2026-10-05 · **Loại**: Diagnostics (nghiên cứu, chỉ đọc) · **Owner**: dangkhoi ·
> **Mục đích**: ghi lại vì sao cụm Seal (10.25", chữ nhật) không chiếu được khung chữ nhật trọn ngang mà vẫn còn số km/h
> gốc, các đường đã thử, và điều kiện mở khoá — để lần sau không nghiên cứu lại từ đầu. **Backlog**: `CLUSTER-RECT-SEAL`
> (mở khoá điều kiện 3 ngày 05/10 ⇒ `CLUSTER-RECT-OPTION`)

Mức bằng chứng theo CLAUDE.md §2. Nguồn RE nằm ngoài repo (`../firmware/fw-2602-diff/`, kho giải nén theme cụm cục bộ
`clusternav-re/sysimg` — `cluster_theme1.rcc`, `cluster_theme2.rcc`, `libBydDataSource.so`); nghiên cứu bằng hai workflow
chỉ đọc ngày 05/10, chưa chạy lệnh nào trên xe.

## 1. Yêu cầu

Owner 05/10: *"Màn hình cụm con Seal là hình chữ nhật, chức năng cast lên cụm đang phải bẻ cong như SL6, HAN để nó hiện
đủ thông tin, để nguyên chữ nhật nó đá mất kmh này kia ra"* → *"có cách nào thẳng mà không đá kmh đi không?"* →
*"Cần chữ nhật thôi, chứ cong như hiện tại tự chỉnh được vị trí app, không cần làm gì thêm phức tạp"*.

## 2. Cơ chế — "cong" hay "chữ nhật" là LỖ của theme cụm

- [ĐO source] Cụm (tiến trình Qt phía host, `libBydCluster`) vẽ app chiếu (màn ảo `xdjaVirtualSurface` 1920×720) ở
  **z = −3**, ảnh nền theme ở z = −2, đồng hồ / ADAS / đèn báo đè trên. Người lái chỉ thấy app qua **lỗ trong suốt** của
  ảnh nền `navi_full_bg` (FULL) / `navi_small_bg` (SMALL).
- [ĐO source] Theme1 (12.3", SL6/HAN): lỗ **thấu kính cong** ≈ (195,174)-(1738,509), km/h nằm dưới lỗ ở
  (1501..1692, 564..630). Theme2 (10.25" Seal / 8.8"): FULL lỗ **chữ nhật** (50,127)-(1870,555).
- [ĐO mã] Kachi gửi cứng `service call AutoContainer 2 i32 1000 i32 {30,16,35}` cho MỌI xe — opcode 30 = ép theme 12.3"
  ⇒ Seal đang hiện đúng hình cong của SL6 (ảnh ghép cụm Seal 14/08 và SL6 18/08 trùng điểm ảnh).

## 3. Vì sao chữ nhật (theme2 FULL) mất km/h

- [ĐO source, cả 3 bản 2506030 · 2511080 · 2602030] `naviSpeedId` trong theme2 `cluster.qml:1976-2010` **không có
  x/y/width/height**; khối con căn giữa vào gốc (0,0) ⇒ con số văng ra ngoài mép trái, chỉ còn chữ **"km/h"** lộ ở góc
  trên trái. **Owner xác nhận trên xe 05/10**: *"bị đá kmh lên trái trên"*; vẽ km/h riêng thì *"vẫn bị lòi cái chữ kmh
  trên trái, không có số"*.
- [ĐO PNG] Chữ "km/h" lạc nằm trên phần nền ĐỤC (y < 100) ở z ≥ 0 — app chiếu (z = −3) không che được. Chỉ ẩn khi
  `speed === -1` (dòng 1993/2006) — app không đặt được.

## 4. Các đường đã xét

| Đường | Kết luận | Bằng chứng |
|---|---|---|
| theme2 SMALL (ô giữa 642×406) qua menu cụm 小屏导航 hoặc opcode 17 | **Chết trên Seal** | [ĐO disasm `libBydDataSource.so`] `updateNaviType@0x12988c` thoát khi `g_productIndex == 1` (DiLink3.0); opcode 17 chỉ ghi bền `SetConfig_UINT8(53,3)` + CAN `0x40C03032`, không đổi bố cục ⇒ **cấm gửi 17** |
| Opcode 29 (8.8") | Chết | theme2 thu cảnh về 1280×480 góc trái, km/h vẫn kẹt (0,0) |
| Opcode 8/9 (kiểu Cổ điển / Tối giản), mph, ngày/đêm, ADAS, ngôn ngữ | Chết | Không cái nào dời `naviSpeedId` |
| Vẽ đè lên cụm từ Android | Chết | Cụm do tiến trình Qt phía host vẽ; Android chỉ chạm màn ảo z = −3 |
| Opcode tự chọn khung (x,y,w,h) | Chết | Bảng nhảy 0..211, mỗi opcode một số nguyên |
| theme2 FULL (opcode 31) + Kachi tự vẽ km/h | Làm được nhưng **còn chữ "km/h" lạc ở góc**; opcode 31 tạo lại màn ảo, khoá 15 s giữa hai lần đổi theme, 11/08 từng làm màn chính khởi động lại vài giây; Kachi treo thì số đứng | owner chê chữ lạc |
| Vá QML qua kênh rcc dịch (nạp trước theme) | Lý thuyết được; **P0**: QML lỗi ⇒ cụm trắng (mất đồng hồ tốc độ + đèn cảnh báo), đường dẫn ghi bền ⇒ lặp mỗi lần nổ máy; ai phía Android gửi lệnh đổi đường dẫn [CHƯA BIẾT] | không đề xuất |
| Root + vá `cluster_theme2.rcc` | Cần root — không có | — |
| Giữ cong, đặt app chữ nhật lọt thấu kính | Làm được, rủi ro ≈ 0 | **owner không cần**: *"cong như hiện tại tự chỉnh được vị trí app"* |

## 5. Điều kiện mở khoá (trace-den-tan-cung)

1. **BYD sửa `naviSpeedId` trong firmware mới** (thêm toạ độ ở theme2 FULL) ⇒ khi đó Kachi chỉ cần bỏ opcode 30 trên xe
   có cụm 10.25" (đo `clu_size` — `/sys/module/max96745/parameters/clu_size`) — việc nhỏ. Cách kiểm: giải nén
   `cluster_theme2.rcc` của firmware mới, xem dòng `naviSpeedId`.
2. **Có root** ⇒ vá `cluster_theme2.rcc`.
3. **Owner chấp nhận chữ "km/h" lạc ở góc** ⇒ đường opcode 31 + Kachi tự vẽ km/h (phải đo trên xe trước, tự ẩn số khi dữ
   liệu cũ, chỉ là tuỳ chọn).
4. **Owner chấp nhận rủi ro P0** của kênh rcc dịch ⇒ phải nghiên cứu thêm ai đặt đường dẫn + đường trả lại trước khi
   chạm.

Không có điều kiện nào ở trên đang đúng ⇒ trạng thái **BLOCKED**, bỏ hay giữ là quyết định của owner.

> **↳ Cập nhật 05/10 (sau buổi xe)**: điều kiện **3 đã đúng** — owner chọn *"ủa chữ nhật làm luôn chứ, thêm option chọn là
> chữ nhật hay bo tròn là OK"* ⇒ việc chuyển sang backlog `CLUSTER-RECT-OPTION` (spec `kachi-289-field-fixes.html` B1b, chi tiết
> §6 dưới). Chữ nhật trọn ngang KÈM số km/h **gốc** của cụm vẫn **BLOCKED** — chỉ điều kiện 1 hoặc 2 gỡ được.

## 6. Đo trên xe owner chiều 05/10 (bổ sung)

Chi tiết + bảng từng bước: `docs/diagnostics/oncar-2026-10-05-slot-cluster.md` §4. Tóm tắt [ĐO ảnh cụm `fission_screencap -d 0`]:

- `31 → 16 → 35` (lệnh PHẢI có `s16 ''`) ⇒ theme 10.25": chiếu Maps **thẳng trọn ngang** nhưng **mất số km/h, lòi "m/h"** ở góc
  trên trái — khớp §3 và lời owner.
- Khung ADAS lớn che ~1/3 bên phải — `13` / gửi lại `16` / gửi lại `35` KHÔNG thu được. **ĐÍNH CHÍNH (05/10, nghiên cứu
  B1b)**: bản trước ghi "phím menu vô-lăng thu được ⇒ vướng ADAS có lối thoát" — **SAI cho theme chữ nhật**. Phím menu chỉ
  thu được ADAS ở theme **cong** (phép đo của buổi xe là ở theme cong). Ở theme2 FULL, phím menu chỉ đổi ADAS sang trạng thái
  nhỏ, **nền trắng lớn bên phải vẫn còn** [ĐO QML fw 2602030 `cluster.qml:47-48`: nền lớn hiện khi
  `adasInterfaceDisplay !== 0`; ĐO owner trên xe 05/10: *"bấm nãy giờ chưa được"*]. Firmware cũ hơn (owner nhớ *"trước làm
  chữ nhật vẫn thu bé được"*) [CHƯA BIẾT]. Không có đòn bẩy phía app (12/13, 32/33, 47/48 vô tác dụng; 41 ghi bền + CAN;
  phím 309 cúp cuộc gọi BT) — chi tiết `oncar-2026-10-05-slot-cluster.md` §4.
- Gửi `30` trả về khi màn ảo cụm còn app ⇒ **framework Android khởi động lại** (`system_server` 15:39:17) — lần thứ hai sau
  11/08 ⇒ backlog `CLUSTER-THEME-SAFE`.
- Kết luận không đổi: chữ nhật trọn ngang vẫn BLOCKED ở km/h (QML OEM). Mở khoá 3 (owner chấp nhận chữ lạc + km/h tự vẽ):
  khung ADAS lớn là CỐ ĐỊNH ở theme chữ nhật ⇒ app phải đặt trong vùng KHÔNG bị nền ADAS che (50,128)-(1285,555), và km/h do
  Kachi vẽ trong vùng đó (góc dưới trái, tránh dải chữ ADAS bật lên x 551..1369).
- **Owner chọn điều kiện 3 (05/10)**: *"ủa chữ nhật làm luôn chứ, thêm option chọn là chữ nhật hay bo tròn là OK"* ⇒ làm ở
  backlog `CLUSTER-RECT-OPTION` (spec `kachi-289-field-fixes.html` B1b) — tuỳ chọn theo hồ sơ, mặc định vẫn Bo tròn.
