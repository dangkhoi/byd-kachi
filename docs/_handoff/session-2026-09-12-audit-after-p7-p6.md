# Giao lại phiên — Lượt soát độc lập sau P7/P6 (2026-09-12 chiều)

> **Trạng thái**: đã VÁ XONG + test xanh, chờ ghi tài liệu + commit. · **Phạm vi**: soát toàn bộ việc off-car của phiên P7/P6
> (và gia cố các vùng chặng gần: hình nền U4, nhóm G1, i18n U5, hồ sơ). · **[ĐO] 3239 công bố / 0 đỏ** (`--rerun-tasks`,
> 73/73 tác vụ chạy thật, 2 phút 11) — trước lượt soát P7/P6 đóng ở 3191 ⇒ **+48**.

## Vì sao có phiên này
Owner chạy thử thấy giao diện "lộn xộn" + phần chọn app chiếu vào ô "lệch loạn (regression)". Trước khi truy hai việc đó,
đóng nốt **lượt soát độc lập sau P7/P6** đang dở trong cây chưa commit (24 tệp sửa + 5 tệp mới, +737/−59).

⚠ **Tách bạch**: cây chưa commit **KHÔNG đụng** `AppDrawer`/`WorkspaceView`/`LauncherWindows`/`AppOpener`/`DrawerController`
⇒ hồi quy "chọn app vào ô" nằm ở một **mốc đã lưu**, KHÔNG do lượt soát này. Sẽ bisect riêng.

## 20 phát hiện đã vá (1×P0 · 4×P1 · 9×P2 · 6×P3)

### P0 — mất dữ liệu
- **P0-1 · Đổi hồ sơ tài xế xoá VĨNH VIỄN widget của hồ sơ kia.** [ĐO] `emulator-5554`: đặt *Digital clock* vào ô ở hồ sơ
  *Mặc định* ⇒ id 654; đổi sang hồ sơ khác ⇒ 654 **biến khỏi** `dumpsys appwidget`; quay lại ⇒ ô báo *"app đã bị gỡ"*
  trong khi `pm list packages` cho thấy app **vẫn cài**. Gốc ở **KIỂU DỮ LIỆU** không ở nhánh code: id widget do nền tảng cấp
  **cho một HOST** (mọi hồ sơ), nhưng `HomeUiState` chỉ mang dữ liệu **hồ sơ đang dùng** (`WorkspacePrefs.key()` = `"<hồ sơ>__<hậu tố>"`)
  ⇒ lượt dọn rác tính "còn ai dùng" trên một câu trả lời **hẹp hơn sự thật**. Vá: `AppWidgetIds` (thuần) thu id qua **hợp** id
  của MỌI hồ sơ; `PrefsWorkspaceRepository` bắt buộc nạp đủ. Đây đúng **giao điểm P7×P6** mà chặng trước tự cảnh báo "chỗ không ai thử".

### P1 — hành vi sai
- **P1-1 · Hai nút cùng nhóm gửi y hệt một byte.** `headl` ("Đèn pha", TOGGLE) và `headlight_mode` ("Chế độ đèn pha", SELECT)
  khai CÙNG `bindingKey 1276153912` và `writeArgs` sinh y hệt. Là nợ xe đã biết (`COLLISION_PENDING_CAR`) miễn trừ vì "hai mục RỜI".
  **G1 làm lý do đó hết đúng** — nhóm Đèn đặt cả hai vào một ô cạnh nhau cùng icon ⇒ bỏ `headl` khỏi nhóm (giữ `headlight_mode`
  vì nó nói được cả 4 trạng thái, phủ luôn bật/tắt), `headl` vẫn còn là mục rời. Chốt bằng `init` + `SAME_BINDING_OK` (rỗng, có lý do).
  Kèm: chốt chống-bấm-kép phải ở **bảng trạng thái dùng chung**, không phải biến trong thân hàm dựng ô.
- **P1-2 · Hình nền lần mở đầu giải mã ở cỡ view = 0.** `reloadWallpaper` chạy trong `onResume`, mà lượt đo cây view chạy SAU
  ⇒ `width==0`. Vá: cỡ cần lấy theo màn hình.
- **P1-3 · Tắt hình nền thì ảnh quay lại.** Lượt giải mã đang bay về muộn vẫn `setPhoto`. Vá: tăng thẻ giải mã + kiểm lại công tắc lúc ảnh về.
- **P1-4 · Đổi hồ sơ không nạp lại bố cục.** Bố cục lưu theo hồ sơ nhưng đọc một lần lúc khởi động ⇒ sang hồ sơ B vẫn thấy bố cục A.

### P2 — hiệu năng / an toàn nền
- **P2-1** HOME bị app khác che thì ô không tháo ⇒ nhịp cũ vẫn đọc đĩa + giải mã ảnh cho thứ không ai thấy.
- **P2-2** quét thư mục + giải mã ảnh nhiều megapixel **trên thread chính** ngay lúc về HOME ⇒ đứng hình.
- **P2-3** luật "không hex" phải phủ cả **màu dựng bằng số** (`Color.rgb/argb/parseColor`).
- **P2-4** cổng giảm cỡ ảnh: ảnh tỉ lệ lệch không được ra bitmap khổng lồ.
- **P2-5 / P2-6** huỷ lượt giải mã đang bay khi ô đã tháo (tự nhả ảnh) — hai điều bản trước làm sai.
- **P2-7** I/O ảnh tách khỏi lệnh cửa sổ dadb (dadb chặn ~3 s).
- **P2-8** đọc nhạc là lời gọi liên tiến trình — bản trước gọi mỗi ô mỗi nhịp ⇒ đọc một lần mỗi lượt.
- **P2-9** thanh trên dựng lại 3 TextView + tra + tint mỗi nhịp trạng thái xe ⇒ dựng một lần, chỉ đổi chữ.

### P3 — chất lượng / nói-đúng
- **P3-1** dòng phụ nhóm Đèn nói sai ("đèn ngoài + chế độ pha" trong khi có `readl` = đèn đọc trong xe) — sửa cả VI/EN.
- **P3-2** việc nền (bind widget) bị TỪ CHỐI cũng là nhánh thất bại ⇒ phải nhả id đã cấp.
- **P3-3** chú thích cũ ghi lý do "chữ launcher màu sáng" — U5 đã bác (có bảng màu SÁNG, mực là màu đậm) ⇒ sửa tiền đề.
- **P3-4** tên hồ sơ "Mặc định" lọt ra màn tiếng Anh. **KHÔNG dịch hằng khoá** (`DEFAULT_PROFILE` là tiền tố khoá lưu bền;
  dịch = mất sạch cấu hình). Tách khái niệm: **khoá** không đổi/không dịch · **nhãn** thì dịch. Tệp mới `ProfileNames.kt` (:core).
- **P3-5** nhãn widget bên thứ ba trùng tên (2× "Cảnh báo", 2× "Google Play Music"). Cơ chế RW0 chỉ biết mục của dự án ⇒ phép riêng,
  giữ đúng khuôn (gợi ý `·` chỉ ở chỗ trùng). Tệp mới `AppWidgetLabels.kt` (:core).
- **P3-6** (tài liệu) số "4 lớp chặn nhóm khỏi thanh trạng thái" → **5 lớp** (thêm lớp `decode` — đường dữ liệu sửa tay/bản cũ,
  trước đó KHÔNG ai canh, không lọc thì launcher sập khi mở).

## Tệp mới (5)
- `core/.../ProfileNames.kt` + test — P3-4
- `core/.../AppWidgetLabels.kt` + test — P3-5
- `app/.../ProfileKeysWiringContractTest.kt` — dây nối P0-1/P2-2 phía Android

## Còn nợ (KHÔNG che)
- 🚗 chiều đúng lệnh khoá cửa (tier OVERDRIVE) + 3 cặp feature-id trùng (L-RE2) — chưa chạm.
- **UI/UX**: owner báo giao diện lộn xөn (đặc biệt Settings) + hồi quy chọn-app-vào-ô — **việc TIẾP THEO**, bisect + soát ảnh độc lập.
- Chưa đo gì trên xe.
