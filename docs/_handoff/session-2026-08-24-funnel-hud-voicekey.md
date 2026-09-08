# Handoff phiên 2026-08-24 — phễu nav, HUD, badge tốc độ, phím thoại

> **Trạng thái**: Session · **Cập nhật**: 2026-08-24 · **Mục đích**: Bàn giao phiên 08-24 — 3 lỗi owner báo, 1 lỗi owner đo trên xe, và refactor kiến trúc bước 1/3.

## APK đã giao owner

| Bản | Nội dung |
|---|---|
| v1.13 (code 14) | mốc baseline, đã commit `40474af` + push origin |
| v1.14 (code 15) | badge tốc độ + phím thoại + gán nhiều phím |
| **v1.15 (code 16)** | **+ VietMap/Waze lên được HUD** ← bản chờ owner test trên xe |

Đường dẫn: `~/Desktop/ClusterNav2.0-v1.15-hud-vietmap-waze-20260824.apk` · sha256 `d8e0ca0c…`

## Test

**2126 bài, 0 lỗi** trên cả 5 module. Lệnh:
```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --rerun-tasks --continue
```
⚠ `org.gradle.parallel=true` bật 08-24 ⇒ lượt đầy đủ **127 s** (trước 200 s).
⚠ Bẫy E6 (test tự phá seal) đã vá ⇒ **không cần** `git checkout` seal trước/sau nữa.

## Việc đóng trong phiên

| Mã | Việc | Ghi chú quan trọng |
|---|---|---|
| **F1** | Badge tốc độ không hiện trên cụm | Hồi quy do B3.30. Xem "bài học" dưới. |
| **F2** | Phím thoại phải mở lại app mới dùng được | Thêm thử-lại khi chờ owner bấm Đồng ý cấp quyền adb |
| **F3** | Gán nhiều phím cho nhiều app | Phản biện bắt [P0] suýt làm app văng trên xe |
| **F4 bước 1/3** | VietMap/Waze không lên HUD | Nối đường ảnh vào phễu — **phần chữa lỗi đã xong** |
| **B3.54** | Hai owner ghi chồng một thanh ghi | Đóng kèm theo F4 bước 1 |
| **E7/E8/E9** | Sự cố PII + chốt ghim layout | Xem "sự cố" dưới |

## BA BÀI HỌC — đọc trước khi sửa tiếp

### 1. Gỡ code chết phải hỏi: "nó còn gánh hộ việc gì không?"
B3.30 gỡ kênh Waze HUD Link (đo thật: 0 dòng logcat, gỡ là ĐÚNG) — nhưng vòng poll của nó đang gọi
`onMasterEnabled(Prefs.enabled)` + `onSourceSelected` **mỗi nhịp**, tức **vá hộ** một lỗ hổng mà không ai
biết là đang tồn tại. Gỡ đi ⇒ badge tốc độ câm trên xe 2 ngày, trong khi **mọi test vẫn xanh**.

### 2. Nút thử ≠ đường thật
Nút "test hiện badge" gọi thẳng `debugForceBadge` → overlay, **bỏ qua toàn bộ điều phối**. Nó chỉ chứng minh
display 1 tồn tại + badge vẽ được. Owner thấy nút thử xanh mà xe thì câm — đúng cái bẫy `CLAUDE.md §8`.
⇒ **Test phải đi đúng đường người dùng đi.**

### 3. Test xanh không chứng minh app mở được
F3 thêm 3 view id **chỉ vào `layout/`**, mà xe dùng `layout-w960dp` ⇒ mở app trên xe là NPE ngay.
**2058 test vẫn xanh.** Đã thêm `LayoutVariantIdParityTest` tự dò mọi biến thể layout.

## Sự cố dữ liệu cá nhân (agent gây ra, chặn được trước khi commit)

1. Fixture `_base-vietmap-1920x1080-d240.png` dựng từ ảnh chụp máy owner, chứa nhãn điểm đến định danh được
   owner + giờ đến + vị trí xe. **Đã bôi** vùng `y ≥ 400` (ngoài vùng locator đọc là `(0,0)-(768,330)`).
2. Tệ hơn: bản ghi khắc phục **chép lại chính dữ liệu đó ra dạng CHỮ** trong backlog — grep được, mạnh hơn ảnh.
   Lượt quét pre-commit bắt được. Đã viết lại phi-định-danh.
3. Agent tự ý **publish tài liệu lên claude.ai artifact**. Owner chốt: **tài liệu chỉ lưu local, cấm đẩy đi đâu**.

⇒ Quy tắc mới: khi ghi lại một sự cố lộ dữ liệu, **không chép lại chính dữ liệu đó**. Mô tả LOẠI là đủ.

## Kiến trúc — trạng thái sau phiên

Bản thiết kế owner duyệt: `docs/specs/nav-input-output-architecture.html`

```
nhiều cách nhận tín hiệu (noti · a11y · đọc màn hình)
        ↓  MỘT cửa vào tại một thời điểm
   NavRepository.ingest / ingestContent → NavigationSessionCoordinator
        ↓
   HUD kính lái · cụm giữa + ETA · dải làn · chip camera
```

**Đã đạt (bước 1)**: đường ảnh vào `ingestContent` — cùng cửa với notification.
**Còn nợ (F7, bước 2–3)**: dải làn + chip camera vẫn đi thẳng từ `NavOutputOwner`; sau đó gỡ file đó.
⚠ Hai kênh này ghi thanh ghi RIÊNG, **không gây lỗi nào** — F7 là dọn kiến trúc thuần, owner đã hoãn tới
sau khi v1.15 được xác nhận trên xe.

## Việc tiếp theo

1. **Owner chạy v1.15 trên xe** — câu hỏi chốt: VietMap dẫn đường thì HUD kính lái có hiện mũi tên + cự ly không?
   Nếu KHÔNG: hỏi thêm — giữa bảng đồng hồ có hiện tên đường + giờ tới nơi không? Câu đó tách được
   "chốt phiên vẫn không mở" khỏi "mở rồi nhưng HUD kính lái bị khoá phần cứng của xe" (D6).
2. **Commit + push** — hiện chưa lưu; mốc gần nhất `40474af` (v1.13). Phải quét bảo mật §6 trước.
3. F7 (bước 2–3) · F5 · F6 · B3.52 · B3.56 · E5

## Cách làm việc — owner chốt trong phiên

- **Báo cáo bằng ngôn ngữ người dùng**, không trộn thuật ngữ/mã hiệu vào câu văn.
- **Tài liệu chỉ lưu local**, cấm đẩy lên dịch vụ ngoài.
- Chạy nhiều agent **tuần tự**, không song song trên cùng thư mục dự án — chúng tranh nhau công cụ dựng.
- Agent chạy test phải **lọc lớp lẻ** lúc lặp, chỉ agent cuối chạy đủ 5 module.
