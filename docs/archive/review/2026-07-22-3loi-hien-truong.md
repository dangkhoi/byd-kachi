# Kế hoạch sửa 3 lỗi hiện trường — 22/07/2026 (v0.50)

Owner: dangkhoi · Xe: Seal DiLink3 · Bản dính lỗi: **0.47** (đọc từ máy, 8/8 file diag)

## Lỗi người dùng báo

1. Chuyển qua lại giữa hai màn nhiều lần → Vietmap bị scale trên **màn chính**, đơ màn. Khởi động lại xe **vẫn bị**.
2. CarPlay mất ổn định; chỉnh kích thước lúc được lúc báo "app đang không chiếu"; sau đó **cắm CP/AA đều không lên**, phải khởi động lại xe.
3. CP và Vietmap chen nhau trên cụm, đè lên nhau.

## Gốc — ba khuyết tật, không phải ba bệnh

| # | Khuyết tật | Rule đã có | Triệu chứng |
|---|---|---|---|
| A | State đổi ra ngoài hệ thống không có đường về đầy đủ | §5 | 1 |
| B | Mọi "kiểm bằng sự thật" chỉ đọc ActivityManager; hỏng thật nằm ở chỗ **WM và AM lệch nhau** | §5 | 2, 3 |
| C | Đường thất bại đi **bắn thêm lệnh** thay vì dừng an toàn | §4 | 3 → đẻ ra 2 |

### Bằng chứng chịu lực

- `CastShell.kt` ghi `settings put global enable_freeform_support 1` + `force_resizable_activities 1`.
  `Settings.Global` sống qua reboot/gỡ app/xoá data; `grep` toàn repo **không có đường trả lại**.
  `ActivityTaskManagerService.retrieveSettings` đọc cờ **một lần lúc boot** ⇒ **lần khởi động lại không chữa bệnh,
  nó KÍCH HOẠT bệnh**: trước power-cycle mọi yêu cầu freeform trên display 0 bị hạ cấp im lặng; sau đó thành
  cửa sổ nổi thật trên màn hình giữa của tài xế.
- `diag-0722-073807`: WM thấy stack 9/task 13 (CarPlay) trên display 1 với cửa sổ đã vẽ; `am stack list` cùng file
  thấy 7 stack **toàn bộ trên display 0**, không có stack 9. Stack mồ côi ⇒ không lệnh `am`/`wm` nào chạm được.
- `diag-0722-074736`: **cùng block `mDisplayId=1`** có stack 9 (CarPlay) *và* stack 39 (Vietmap) → lỗi 3 có thật.
- `ClusterCast.kt` nhánh warm: app mới không bám được VD thì **bê app cũ trở lại cụm** → đúng lời kể "bấm AA lại
  lên Vietmap", và mỗi vòng cộng một lần tái tạo VD + nhiều `move-stack` → công thức đẻ stack mồ côi.

## Việc làm (generic, đo đạc, không hardcode tên gói)

| # | Việc | Sửa lỗi | Rủi ro |
|---|---|---|---|
| R1 | Cờ freeform có **marker prefs ghi TRƯỚC** + `unseedFreeform()` + nút gỡ trong app | 1 | gỡ xong tier-1 `am task resize` hết tác dụng, tụt về `wm size`/overscan (hai đường vốn chạy tốt) |
| R2 | `reconcileOnStart` quét **mọi app** còn freeform/pinned trên display 0 → ép fullscreen | 1 | thấp |
| R3 | Parser thuần `WmParse` đọc stack theo display từ `dumpsys window displays`; phát hiện **WM≠AM** → **cấm thao tác cụm**, chỉ chừa teardown | 2, 3 | cấm cứng có thể khoá nút TẮT → chừa đường teardown |
| R4 | Nhánh warm thất bại: **dừng an toàn**, không bê app cũ lại; quá 1 lần thì teardown | 3 | đang lái mất dẫn đường trên cụm — vẫn an toàn hơn hai app đè nhau |
| R5 | `restoreFullscreenOnMain` lọc theo **tập display của cụm** (tên chứa xdja/fission), không `>= 1`; verify sau khi ép | 1, SL6 | thấp |
| R6 | `reconcileOnStart` phát hiện rác **density + overscan**, không chỉ `wm size` | rác sống qua reboot | reset lúc khởi động xoá dpi người dùng cố ý giữ; lần chiếu sau áp lại |
| R7 | `autoDiag` chụp cả ở **nhánh warm thất bại** | mù lúc lỗi xảy ra | thấp |

## Không làm lần này

- Vá `com.byd.androidauto` để bật `registerCarService(9, navigationStatus)` — sửa app hệ thống, rủi ro cao.
- Refactor `ClusterCast.kt` (1092 LOC) — nợ kiến trúc, để đợt riêng.

## Nghiệm thu trên xe (chiều 22/07)

1. Gỡ cờ freeform → tắt-mở máy → Vietmap trên màn chính về bình thường.
2. Đổi qua lại Vietmap ↔ CarPlay 10 lần → không app nào kẹt freeform trên màn chính.
3. Tạo tình huống WM≠AM (đổi nhanh liên tục) → app phải **báo đỏ và từ chối**, không bắn thêm lệnh.
4. Cắm CarPlay sau khi dùng ClusterNav → lên bình thường.
5. Chiếu app không lên được → app dừng an toàn, cụm về đồng hồ, app cũ ở màn giữa.
