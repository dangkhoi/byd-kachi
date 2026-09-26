# Kachi launcher — Hướng dẫn sử dụng & cấu hình · User & configuration guide

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 (bản **2.73 (174)**, package `com.byd.launcher`) · **Mục đích**: hướng dẫn **dùng + cấu hình** Kachi cho anh em — cài/OTA · HOME · màn hình chính · hồ sơ · **từng nhóm Cài đặt theo đúng đường dẫn menu** · **bảng lệnh giọng nói theo nhóm** · ảnh/hình nền · automation · lấy log · FAQ. VI trước, EN sau.
> Tên nhóm và tên hàng lấy **đúng nguyên văn** từ app (`SettingsCatalogGroups.kt` · `strings_kachi.xml`). Đời ClusterNav 1.x xem `HUONG-DAN.md` (Historical). Danh mục **mọi** chức năng + status trên xe: `kachi-feature-catalog.html`. Kỹ thuật/việc còn mở: `PROJECT-BACKLOG.md`, `CLOSEOUT-2026-09-25.md`.
> Ký hiệu: **🚗** = code xong, **chưa đo trên xe thật** — đừng coi là đã chạy được.

---

## (VI) Tiếng Việt

### 1. Cài lần đầu + cập nhật (OTA)

| Việc | Cách làm |
|---|---|
| **Cài lần đầu** | Tải `apk/Kachi-2.73-release.apk` (nút Raw/Download trên GitHub `dangkhoi/byd-kachi`, nhánh `main`) → cài bằng **adb**: `adb install -r Kachi-2.73-release.apk`. ⚠ Chép vào xe rồi **tap** để cài có thể bị ROM DiLink báo *"Fail in installation of desktop apps"* (Kachi là launcher) — cài bằng adb thì qua. |
| **Cập nhật về sau** | *Cài đặt › Hệ thống & quyền › **Kiểm tra cập nhật*** — app tự tải bản mới từ `apk/` trên `main` rồi cài đè qua dadb loopback, **không cần laptop**. |
| **Tự dò bản mới** | Công tắc *Tự động cập nhật* (cùng mục): mở Kachi thì tự dò; có bản mới sẽ **hỏi trước** khi tải; không có thì im lặng. |
| **Bản rất cũ** | Bản cài trước 1.41 (khoá ký cũ) phải gỡ (`pm uninstall com.byd.launcher`) rồi cài tay một lần. |

### 2. Đặt Kachi làm màn hình chính

- *Cài đặt › Hệ thống & quyền › **Màn hình chính** › **Đặt Kachi làm màn hình chính***. ROM BYD không hiện hộp chọn HOME nên Kachi tự đặt qua adb loopback; bấm Home trên màn → Kachi lên.
- Bỏ: cùng mục, **Bỏ chọn Kachi làm màn hình chính** → về launcher gốc, nổ máy lại vẫn giữ.
- Muốn Kachi tự giành lại HOME sau khi ROM đổi: *Giữ Kachi làm màn hình chính khi nổ máy* (mặc định **tắt**).

### 3. Màn hình chính (bố cục · ô · widget · thanh trên)

| Việc | Đường dẫn · cách làm |
|---|---|
| **Đổi bố cục** | *Cài đặt › Màn hình chính › **Bố cục sẵn***: 1 ô · 2 cột · 2 hàng · 3 ô · 4 ô. Đổi bố cục thì nội dung ô nào giữ nguyên ô đó. |
| **Vẽ bố cục riêng** | *Cài đặt › Màn hình chính › **Bố cục tự vẽ*** rồi **Vẽ bố cục riêng…** — lưới **12 cột × 6 dòng**: kéo giữa khung để dời, kéo góc dưới-phải để đổi cỡ. Khung đè nhau tô **ĐỎ** và nút **Lưu** bị chặn. |
| **Đặt widget vào ô** | Chạm ô trống → chọn thẻ dựng tay (Tổng hợp · Xe · pin · đồng hồ · trình chiếu ảnh…) hoặc widget Android của app khác. |
| **Kéo app vào ô** | Chạm ô trống → **Mở ứng dụng** → chọn app: app chạy **thật** trong ô (màn ảo). App tự đóng thì ô hiện *"App đã đóng — chạm để mở lại"*. Bằng giọng: *"mở YouTube vào ô số 2"*. |
| **Chip thanh trên** | *Cài đặt › Thanh trạng thái & thanh nút › **Chip thanh trạng thái***; **Hiện nhãn trên thanh trên** (tắt = chỉ icon + số, đỡ chật); **Vị trí trên thanh trên** để sắp lại thứ tự. |
| **Thanh nút xe** | Cùng nhóm: **Hiện thanh nút xe** · **Viền đặt thanh nút** (4 viền) · **Nút trên thanh nút xe** (chọn từ danh mục nút) · **Vị trí trên thanh nút xe**. |
| **Sáng/tối · đơn vị · ngôn ngữ** | *Cài đặt › Hiển thị & đơn vị*: **Đơn vị hiển thị** · **Giao diện sáng/tối** · **Màu sắc** · **Ngôn ngữ** (Theo xe / VI / EN) · **Kính thật (làm mờ nền)**. |

### 4. Hồ sơ tài xế

- *Cài đặt › **Hồ sơ tài xế***: **Danh sách hồ sơ** · **Hồ sơ đang dùng** · **Hồ sơ lúc nổ máy** (*Gần nhất* hoặc một hồ sơ cố định) · **Thêm hồ sơ (bản sao)**. Đổi tên và **Xoá** ở nút cạnh từng hồ sơ.
- **Cái gì thuộc hồ sơ**: bố cục, nội dung ô, chip thanh trên, thanh nút, hình nền, giao diện, đơn vị, ngôn ngữ, sổ địa chỉ. **Không** thuộc hồ sơ: nhóm *Hệ thống & quyền*, khung hình khi chiếu lên cụm, *Giới thiệu*.
- **"Cảnh" đã gộp vào hồ sơ** — mỗi cảnh cũ nay là một hồ sơ cùng tên; chỉ còn một khái niệm để nhớ.
- **Xuất / nhập**: *Xuất hồ sơ (backup)* ghi ra `Android/data/com.byd.launcher/files/profiles/`; *Nhập hồ sơ từ file* đọc cùng thư mục. Trùng tên tự thành `<tên> 2`, `<tên> 3`… (không bị từ chối).
- **Đổi nhanh**: chạm chip hồ sơ ở thanh trên, hoặc nói *"chuyển sang hồ sơ X"*.

### 5. Cấu hình theo nhóm Cài đặt

#### 5.1 Tiện nghi xe

| Khối | Hàng / chip | Ghi chú |
|---|---|---|
| **Camera theo xi-nhan** | **Bật camera khi xi-nhan** | Xi-nhan trái → camera trái nổi góc màn; phải → camera phải. Giữ tới khi **đèn xi-nhan tắt** (2.70; [ĐO xe Seal 26/09] trễ 87 ms). |
| | **Hiện camera lên màn cụm** | Overlay hiện trên cụm đồng hồ thay vì màn chính. |
| | **Xi-nhan trái hiện ở** / **Xi-nhan phải hiện ở** | Góc trên-trái / trên-phải, đặt riêng từng bên (mặc định trái TL, phải TR). |
| | **Xi-nhan trái: xoay video** / **Xi-nhan phải: xoay video** | *Không xoay* · *↺ 90°* · *↻ 90°* · *180°*. Mặc định **trái ↺ 90°, phải ↻ 90°** (dải gương cắt từ camera 360 vốn nằm ngang). **Hai bên độc lập** — bên nào còn ngang/lộn đầu thì chỉ chỉnh hàng bên đó. |
| | **Kết xuất camera** | *TextureView (mặc định)* / *SurfaceView (nhẹ hơn, xoay nhờ HAL — có thể không xoay)*. Chỉ đổi khi thấy **giật lúc xe chạy**; không vừa thì trả về mặc định. 🚗 |
| | **Cam xi-nhan trái** / **Cam xi-nhan phải** | id camera **0–5** mỗi bên. Cam không lên thì thử id khác (Sealion 6 dùng **cam 0**). |
| **Lấy gió trong** | **Nổ máy thì tự lấy gió trong** | Xe quên chế độ này mỗi lần khởi động — Kachi tự bật lại. |
| **Ghế mát / sưởi** | **Tự chỉnh ghế theo nhiệt độ** + **Chế độ** (*Làm mát* / *Sưởi*) + **mức từng ghế** | Chạm một ghế trên sơ đồ để đổi mức: **tắt → mức 1 → mức 2**. Mát và sưởi **loại trừ nhau** (theo HAL của xe). |
| **Lọc bụi mịn** | **Tự lọc khi không khí bẩn** · **Lọc ngay một lượt** | Dòng *Bụi mịn hiện tại* hiện mức đọc được; tắt công tắc = chỉ lọc khi bạn bấm. |
| **Tự sấy kính khi mưa** 🚗 | **Mưa thì tự bật sấy kính** + **Sấy kính trước** + **Sấy kính sau + gương** | Đọc cảm biến mưa **mỗi 5 phút**; hết mưa thì tắt — và **chỉ tắt cái Kachi bật**, bạn tự bật thì Kachi không đụng. Chưa gặp buổi mưa thật để xác nhận. |

#### 5.2 Giọng nói

| Khối | Hàng | Ghi chú |
|---|---|---|
| **Nhận dạng giọng nói (tại máy)** | **Tải mô hình tiếng Việt** · **Cập nhật mô hình** · **Gỡ mô hình** | sherpa-onnx + mô hình `zipformer-vi`, **~266 MB**, tải một lần, sha256 ghim. **Tiếng nói không gửi ra mạng.** Xe không có mạng: chép cả cây thư mục gói vào `Android/data/com.byd.launcher/files/sherpa/import/<gói>/` rồi bấm Tải (máy vẫn kiểm sha256). |
| **Giọng đọc offline (tại máy)** | **Tải gói giọng đọc** · **Cập nhật** · **Gỡ** | Gói Piper; **Đọc phản hồi bằng giọng** để Kachi đọc to; **Ưu tiên giọng offline** chỉ có tác dụng khi gói đã cài (chưa cài thì dùng máy đọc của hệ thống). |
| **Hey Kachi** (thử nghiệm) | **"Hey Kachi" — gọi bằng giọng** · **Cách nghe "Hey Kachi": ASR (không cần train)** | **Mặc định tắt** (tốn pin/CPU). Nghe nền khi màn sáng. Nghe nhầm nhiều lần thì **tự tắt** kèm thông báo. Bật lên thì nút mic cũng đi qua tiến trình nghe ⇒ **lần bấm đầu không phải chờ nạp mô hình**. |
| **Xác nhận** | **Hỏi xác nhận trước khi chạy** · **Đọc to câu hỏi xác nhận** | **Mặc định Kachi chạy luôn** (danh sách rỗng). Tích việc nào thì **việc đó** hỏi lại trước khi bắn (cốp · cửa sổ trời · 4 kính…). |
| **Micro** | **Nguồn micro** | *Tự chọn* thử MIC trước rồi tới nguồn có khử ồn. Đổi khi micro nghe kém. |
| **Khác** | **App nhạc mặc định** | Dùng cho *"phát nhạc"* khi bạn không nói tên app. |
| **Lối gọi** | ô *Nói với xe* · **nút mic trên thanh trạng thái** · phím vô-lăng | Phím vô-lăng gán ở *Cài đặt › Phím vô-lăng*, đích **Kachi nghe (tại máy)**. |

> Không có núm VAD / độ nhạy trên UI — trần im lặng nới từ 800 lên **1 200 ms** ở 2.73, nhưng **mặc định giữ 600 ms**; muốn đổi thì qua cầu kiểm thử (`voice_vad_min_silence_ms`), không phải màn Cài đặt.

#### 5.3 Phím vô-lăng

*Cài đặt › **Phím vô-lăng***: **Nhận nút vật lý** (công tắc chính) · **Danh sách gán nút** · **Nút tự học thêm** · **Học phím mới** (bấm nút trên vô-lăng để Kachi học mã) · **Kiểm tra và sửa ngay** (cấp lại quyền + nối lại khi reboot làm mất). Đích gán được: một app, trợ lý của xe, hoặc **Kachi nghe (tại máy)**. Chức năng gốc của nút **không bị mất** — Kachi chỉ nhận đúng tổ hợp đã cấu hình.

#### 5.4 Dẫn đường & cụm đồng hồ

| Hàng | Ghi chú |
|---|---|
| **Dẫn đường lên cụm** | Công tắc chính. Bật lên thì Kachi tự cấp quyền notification (qua dadb) rồi nối nguồn dẫn đường. |
| **Chế độ hiện trên cụm** · **Chạy chữ tên đường** | Bật/tắt cụm-giữa; chạy chữ cho tên đường dài (tắt thì tên đường được viết tắt). |
| **App dẫn đường mặc định** | Dùng khi bạn nói *"dẫn đường tới …"* mà không nêu app. |
| **Kết nối lại nguồn dẫn đường** | Bấm khi cụm im mà app dẫn đường vẫn chạy. |
| **Biển báo tốc độ** · **Giới hạn sắp tới** · **Chip cảnh báo camera** · **Cỡ biển báo** · **Vị trí biển báo** | Kéo-thả để đặt vị trí. |
| **Bong bóng VietMap** · **Vị trí bong bóng** | Hiện bóng VietMap trên cụm. |
| **Sổ địa chỉ** · **Thêm địa chỉ…** | Mỗi mục = *tên + văn bản địa chỉ + lat/lng **tuỳ chọn***, lưu **theo hồ sơ**. Toạ độ **dán tay** từ app bản đồ — Kachi không xin quyền ghi GPS. VietMap chỉ nhận toạ độ; Google Maps nhận cả chữ ⇒ Kachi chọn app **theo dữ liệu của mục**. |
| **Tự dẫn đường theo lịch** 🚗 | Khung giờ × thứ trong tuần × *chỉ khi có GPS* × một điểm trong Sổ địa chỉ × app dẫn đường. Mỗi khung chạy **1 lần/ngày**. Ví dụ 07:30–09:00 T2–T6 → công ty. |

#### 5.5 Chiếu màn lên cụm

**Bật chiếu màn** · **Hiện nút nổi chiếu cụm** · **Tỉ lệ chia đôi** · **Tự chiếu khi nổ máy** (+ **App tự chiếu toàn màn** / **Tự chiếu chia đôi** / **App bên trái** / **App bên phải**) · **Chiếu ngay: toàn màn, trái, phải, dừng** · **Cứu hộ cụm**. CarPlay / Android Auto luôn chiếu **toàn màn**.

#### 5.6 Hệ thống & quyền

**Quyền còn thiếu** (Kachi tự cấp qua dadb, không cần laptop) · **Tự mở Kachi khi nổ máy** · **Chạy dịch vụ nền khi nổ máy** · **Màn hình chính** (đặt/bỏ) · **Giữ Kachi làm màn hình chính khi nổ máy** · **Kiểm tra cập nhật** + **Tự động cập nhật** · **Dừng toàn bộ dẫn đường** · **Khởi động lại launcher** · **Chế độ kiểm thử qua adb**.
**Nâng cao** còn: màn ClusterNav cũ · **Chẩn đoán cụm** · **Kiểm tra từng chức năng xe** · **Gõ lệnh chữ** (thử bộ hiểu ý không cần nói) · **Nhận dạng tệp WAV thử**.
*Giới thiệu*: **Phiên bản và giấy phép** · **Miễn trừ trách nhiệm**.

> **Chế độ kiểm thử qua adb** chỉ bật được **bằng tay trong xe**, **tự tắt sau 60 phút**, và **chết theo lần nổ máy**. Mọi lệnh gửi vào đều ghi nhật ký.

### 6. Bảng lệnh giọng nói theo nhóm

**Cách nói** (đọc trước khi thử):
- Nói **liền một hơi** — ngừng để nghĩ quá ~0,8 s là Kachi coi như hết câu và cắt (mặc định).
- **Huỷ lượt nghe**: **chạm ra ngoài tấm chữ**, hoặc chờ trần **8 s**. Từ **2.73 nút Back KHÔNG huỷ nữa** (nó đi tới app phía sau) — đổi có chủ ý để thanh điều hướng của xe không trồi lên lúc Kachi đọc phản hồi. Đây **không phải lỗi**.
- Ghép được hai việc bằng ***và*** hoặc ***rồi***.
- Số nói bằng chữ hay bằng số đều được (*"hai mươi bốn"* = *"24"*).

| Nhóm | Ví dụ nói được | Kachi làm gì |
|---|---|---|
| **Bật / tắt nút xe** | *"bật đèn đọc"* · *"tắt đèn nóc"* · *"bật lọc bụi"* · *"bật gió tự động"* · *"bật sấy kính trước"* · *"bật sạc không dây"* | Bật/tắt đúng nút, đọc lại *"đã bật …"* |
| **Kính · cốp · cửa sổ trời** | *"mở kính lái"* · *"đóng kính trước trái"* · *"mở kính bên lái"* · *"hạ bốn kính"* · *"mở cốp"* · *"mở cửa sổ trời"* · *"mở rèm che nắng"* | Mở/đóng (có mức 50%). Một số việc **hỏi lại** nếu bạn đã tích trong *Hỏi xác nhận trước khi chạy* |
| **Gói lệnh** | *"mở hết kính"* · *"đóng hết kính"* | Chạy tuần tự nhiều bước |
| **Điều hoà · ghế** | *"đặt nhiệt độ 24 độ"* · *"nhiệt độ hai mươi bốn độ"* · *"tăng nhiệt độ"* · *"tăng gió"* · *"giảm gió"* · *"bật sưởi ghế"* · *"mở quạt ghế"* · *"lọc ngay"* | Đặt/tăng/giảm mức; ghế có 3 mức (tắt · mức 1 · mức 2) |
| **Hỏi thông tin xe** | *"xem pin"* · *"pin còn bao nhiêu"* · *"nhiệt độ ngoài trời bao nhiêu"* · *"đọc tầm hoạt động"* | Đọc **giá trị hiện tại** (**64** loại thông tin đăng ký: pin · lốp · nhiệt độ · quãng đường · giờ…). Đọc được cái gì, xem `kachi-feature-catalog.html` |
| **Mở / đóng app** | *"mở YouTube"* · *"mở bản đồ"* · *"mở ứng dụng YouTube"* · *"đóng YouTube"* | Mở/đóng app; tên nghe lệch nhẹ (*"du túp"*, *"gu gồ máp"*, *"youtubex"*) vẫn khớp |
| **Mở app vào ô** | *"mở YouTube vào ô số 9"* · *"đưa YouTube vào ô hai"* · *"mở vietmap vào ô số 2"* | App chạy trong đúng ô đó. Nói **liền một hơi** — vế *"vào ô số N"* rất dễ bị cắt nếu ngừng giữa câu (2.73 làm chắc hơn, 🚗 chưa chốt bằng giọng thật) |
| **Nhạc / YouTube** | *"phát nhạc"* · *"dừng nhạc"* · *"bài tiếp theo"* · *"bài trước"* · *"đang phát bài gì"* · *"phát bài Diễm Xưa"* · *"mở bài Diễm Xưa trên YouTube Music"* | Điều khiển nhạc; tên bài nhận bằng **lượt nghe thứ hai** trên chính khúc tiếng vừa thu |
| **Dẫn đường** | *"dẫn đường đến Bitexco"* · *"dẫn đường tới chợ Bến Thành bằng Waze"* · *"chỉ đường đến sân bay bằng google map"* | Giao cho đúng app. **Không hỏi xác nhận** — dẫn thẳng |
| **Địa chỉ đã lưu** | *"về nhà"* · *"đến công ty"* · *"đi làm"* | Dẫn tới mục trong **Sổ địa chỉ** của hồ sơ đang dùng |
| **Hồ sơ** | *"chuyển sang hồ sơ Test"* · *"đổi sang hồ sơ Chính"* | Đổi hồ sơ. Tên tiếng Anh vẫn được; **nói thiếu tên** thì Kachi hỏi lại *"Hồ sơ nào — A hay B?"* |
| **Bố cục** | *"bố cục hai ô"* · *"đổi bố cục 4 ô"* · *"về bố cục 2 hàng"* · *"bố cục hai cột"* | Đổi bố cục màn chính của hồ sơ đang dùng |
| **Launcher** | *"mở cài đặt"* · *"mở ứng dụng"* · *"nói với xe"* | Mở màn tương ứng |
| **Câu ghép** | *"bật đèn đọc và tắt lọc bụi"* · *"mở cốp rồi bật đèn đọc"* | Chạy từng vế theo thứ tự; vế nào không hiểu thì bỏ qua vế đó |

**Khi Kachi không hiểu** nó nói rõ *lý do*, không im lặng: chưa rõ cần làm gì (*"hôm nay trời đẹp quá"*) · thiếu đối tượng (*"bật cái đó"*) · việc không đi với thứ đó (*"tăng đèn đọc"*) · hoặc *"… đã bỏ khỏi Kachi — dùng màn hình của xe"* với những thứ đã gỡ (khoá xe · âm lượng · độ sáng màn · mở khoá cửa · đèn viền).

### 7. Ảnh xe · hình nền · trình chiếu ảnh

| Thư mục (trên thẻ, **không cần quyền**) | Dùng cho |
|---|---|
| `Android/data/com.byd.launcher/files/car/` | Ảnh xe top-down (mặc định `seal-3`, không logo) |
| `Android/data/com.byd.launcher/files/wallpapers/` | Hình nền màn chính |
| `Android/data/com.byd.launcher/files/photos/` | Widget trình chiếu ảnh |

Mỗi hàng trong *Cài đặt › Màn hình chính* có nút **Sao chép đường dẫn thư mục** — dán vào trình quản lý tệp / `adb push` rồi chép ảnh vào. Chưa có ảnh riêng thì Kachi **vẽ hình xe bằng vector** (chọn model + màu sơn).

### 8. Automation

| Việc | Đường dẫn | Trạng thái |
|---|---|---|
| **Mưa thì tự bật sấy kính** | *Cài đặt › Tiện nghi xe › Tự sấy kính khi mưa* | 🚗 chưa gặp buổi mưa thật |
| **Tự dẫn đường theo lịch** | *Cài đặt › Dẫn đường & cụm đồng hồ › Tự dẫn đường theo lịch* | 🚗 chưa chạy một khung giờ thật |
| **Tự lấy gió trong khi nổ máy** | *Cài đặt › Tiện nghi xe › Lấy gió trong* | ✅ dùng hằng ngày |
| **Ghế mát/sưởi tự động** · **Tự lọc bụi mịn** | *Cài đặt › Tiện nghi xe* | 🟢 đã xác nhận trên xe |
| **Tự mở Kachi / chạy nền / giữ HOME khi nổ máy** | *Cài đặt › Hệ thống & quyền › Khởi động* | ✅ |

Cả bốn việc tự động đều theo một luật: **chỉ hoàn tác cái mình bật**. Bạn tự bật tay thì Kachi không đụng.

### 9. Khởi động lại launcher

*Cài đặt › Hệ thống & quyền › **Khởi động lại launcher*** — khi launcher có lỗi hiển thị, **không cần khởi động lại đầu xe**. Cụm câm mà app dẫn đường vẫn chạy thì thử **Kết nối lại nguồn dẫn đường** hoặc **Cứu hộ cụm** trước.

### 10. Lấy log / chẩn đoán gửi về khi gặp lỗi

- **Cách dễ nhất**: *Cài đặt › Hệ thống & quyền › Nâng cao › **Chẩn đoán cụm*** — app **tự chụp** mọi thứ cần thiết ra màn. Anh em chỉ cần **chụp ảnh màn hình gửi về**, không phải gõ lệnh nào.
- Log ghi ra thẻ: `/sdcard/Android/data/com.byd.launcher/files/kachi-logs/` — `usage-*.log` (suốt phiên) · `snapshot-*.log` (khi bấm *Chụp log ngay*) · `captest-report.txt`.
- Lấy về: `adb pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ ./kachi-logs/`, hoặc chép cả thư mục bằng trình quản lý tệp.
- **Gửi kèm**: **phiên bản** (*Cài đặt › Giới thiệu*) + mô tả lỗi + ảnh chụp màn. Đừng đoán bản đang chạy — đọc từ màn Giới thiệu.

### 11. Khắc phục sự cố (FAQ)

| Hiện tượng | Thử theo thứ tự này |
|---|---|
| **Cài APK bằng cách tap thì lỗi** *"Fail in installation of desktop apps"* | ROM chặn cài launcher bằng tap. Cài bằng `adb install -r`. |
| **Kachi không nghe / bấm mic không ra gì** | 1) *Cài đặt › Giọng nói › Nhận dạng giọng nói (tại máy)* — mô hình đã cài chưa (dòng *Đã cài … · … từ*)? 2) Quyền micro (*Quyền còn thiếu*). 3) Đổi **Nguồn micro** sang nguồn khác. 4) Thử **Gõ lệnh chữ** ở *Nâng cao*: gõ đúng câu bạn nói — hiểu được ⇒ lỗi ở tầng **nghe**, không hiểu ⇒ lỗi ở tầng **câu**. |
| **Nói đúng mà Kachi hiểu thiếu** (vd rụng *"vào ô số 2"*, rụng tên hồ sơ) | Nói **liền một hơi**, không ngừng giữa câu. 2.73 đã làm chắc hơn cho *"vào ô số N"* và tên app/hồ sơ nghe lệch — 🚗 chưa chốt bằng giọng thật trên xe. |
| **Bấm Back mà lượt nghe không tắt** | Đúng như thiết kế từ 2.73. Huỷ bằng cách **chạm ra ngoài tấm chữ**, hoặc chờ 8 s. |
| **Thanh điều hướng của xe trồi lên lúc Kachi đọc** | Lỗi có ở mọi bản **≤ 2.72**; vá ở 2.73 (🚗 chưa nhìn trên xe). Tạm thời: tắt *Đọc phản hồi bằng giọng*. |
| **Camera xi-nhan không lên** | 1) **Bật camera khi xi-nhan** đã bật chưa. 2) Thử **Cam xi-nhan trái/phải** id khác trong 0–5 (Sealion 6 dùng **cam 0**). 3) Chỉ đo được trên **Seal** và **Sealion 6** — xe khác 🚗 chưa biết. |
| **Camera lên rồi tắt ngay ~1 s** | Lỗi của bản **2.69**. Cập nhật lên **2.70+**. |
| **Video camera bị ngang / lộn đầu** | Chỉnh đúng hàng của bên đó: **Xi-nhan trái: xoay video** hoặc **Xi-nhan phải: xoay video**. Hai bên độc lập. |
| **Video camera giật khi xe chạy** | Thử **Kết xuất camera → SurfaceView**. ⚠ SurfaceView xoay nhờ HAL nên **có thể không xoay được** — không vừa thì trả về *TextureView (mặc định)*. Nguồn giật vẫn đang tìm, chưa kết luận. |
| **Thanh nút xe mất** | *Cài đặt › Thanh trạng thái & thanh nút › **Hiện thanh nút xe***; kiểm luôn **Viền đặt thanh nút** và **Nút trên thanh nút xe** (có thể đang rỗng). |
| **Một nút xe bấm không tác dụng** | Không phải nút nào ROM cũng cho ghi. Tra đúng nút đó trong `kachi-feature-catalog.html` (🟢 chạy thật · ⚠ một phần · ❌ không tác dụng · 🚗 chưa đo), hoặc chạy *Nâng cao › **Kiểm tra từng chức năng xe***. |
| **Chiếu cụm không lên** | 1) **Bật chiếu màn** đã bật chưa. 2) Bấm **Cứu hộ cụm**. 3) **Khởi động lại launcher**. Sau khi nổ máy lại, cụm cần vài giây mới sẵn sàng. |
| **Bấm Home không về Kachi** | *Hệ thống & quyền › Màn hình chính* — dòng trạng thái nói *"Kachi đang là màn hình chính"* hay *"Chưa — hệ thống đang dùng …"*; bấm **Đặt Kachi làm màn hình chính**. Hay bị ROM đổi thì bật **Giữ Kachi làm màn hình chính khi nổ máy**. |
| **OTA lỗi / không thấy bản mới** | 1) Xe phải có mạng lúc dò. 2) Bản trên kênh chỉ là `apk/Kachi-<ver>-release.apk` trên `main` — bản mới hơn chưa đăng thì **không có gì để tải**. 3) Bản cài trước **1.41** khác khoá ký ⇒ phải gỡ rồi cài tay một lần. |
| **Mất một câu Kachi đọc** | Tiến trình đọc (`:tts`) có lúc chết rồi tự lên lại — lỗi đã biết, đã cách ly, không làm sập launcher. |
| **YouTube tự tắt** | [ĐO xe 26/09] YouTube tự crash, **không phải Kachi**. |

### 12. Chỉ chạy trên xe nào · chưa đo gì

| Mục | Trạng thái |
|---|---|
| Camera theo xi-nhan (mở/đóng theo đèn, chiều xoay mặc định) | **🟢 đã đo trên Seal** (2026-09-26) và **Sealion 6** (2.6x). Xe khác 🚗 |
| Nút xe (kính, cốp, đèn, điều hoà, ghế, lọc khí) | Từng nút một status riêng — tra `kachi-feature-catalog.html`. Bằng chứng gồm cả một lượt kiểm trên **Sealion 6** của anh em |
| Khung camera đúng tỉ lệ · **Kết xuất camera** (2.73) | 🚗 chưa đo trên xe |
| Voice: *"vào ô số N"* · tên app mờ · tên hồ sơ (2.73) | 🚗 chỉ đo bằng **phát lại bản thu của xe trên máy** — chưa nói thật trên xe |
| Taskbar không trồi khi Kachi đọc (2.73) | 🚗 gốc bệnh đã đo trên xe, **bản vá chưa nhìn** |
| Mưa → tự sấy kính · Tự dẫn đường theo lịch | 🚗 chưa gặp buổi mưa thật / chưa chạy một khung giờ thật |
| Mức RAM tiết kiệm được của bản 2.73 | 🚗 **chưa biết** — đừng trích số nào |
| GPS / mock location | **Đã gỡ hẳn** 2026-07-27. Quyền location chỉ **ĐỌC**; Kachi không bao giờ là app mock-location |

---

## (EN) English

### 1. First install + OTA

| Task | How |
|---|---|
| **First install** | Download `apk/Kachi-2.73-release.apk` from GitHub `dangkhoi/byd-kachi` (`main`), copy to the car and install with **adb**: `adb install -r Kachi-2.73-release.apk`. Tapping the APK on the head unit may fail with *"Fail in installation of desktop apps"* (it is a launcher); adb bypasses that gate. |
| **Updates** | *Settings › System & permissions › **Check for updates*** — the app fetches the newer `apk/` build and installs it over the dadb loopback; **no laptop**. |
| **Auto check** | The *Auto update* toggle (same place): checks when you open Kachi, **asks first** before downloading, stays silent when there is nothing new. |
| **Very old builds** | Builds installed before 1.41 (old signing key) must be uninstalled once (`pm uninstall com.byd.launcher`). |

### 2. Set Kachi as home

*Settings › System & permissions › **Home screen** › **Set Kachi as home*** (the BYD ROM shows no HOME chooser, so Kachi sets it over the adb loopback). **Unset Kachi as home** in the same place returns to the stock launcher and survives a restart. **Keep Kachi as home screen on engine start** (default off) re-claims HOME once per start if the ROM changed it.

### 3. Home screen (layout · slots · widgets · top bar)

| Task | Path · how |
|---|---|
| **Change layout** | *Settings › Home screen › **Preset layout***: 1 slot · 2 columns · 2 rows · 3 slots · 4 slots. Slot contents stay in their slot. |
| **Draw your own** | *Settings › Home screen › **Custom layout*** then **Draw your own layout…** — a **12 × 6 grid**: drag the middle to move, the bottom-right corner to resize. Overlapping frames turn **RED** and **Save** is blocked. |
| **Put a widget in a slot** | Tap an empty slot → pick a hand-built card (Board · Car · battery · clock · photo slideshow…) or another app's Android widget. |
| **Put an app in a slot** | Tap an empty slot → **Open app** → pick one: it runs **for real** in the slot (virtual display). When it closes the slot reads *"App closed — tap to reopen"*. By voice: *"mở YouTube vào ô số 2"*. |
| **Top-bar chips** | *Settings › Status bar & button bar › **Status-bar chips***; **Show chip labels** (off = icon + value only); **Status-bar item order**. |
| **Car button bar** | Same group: **Show the car bar** · **Button bar edge** (4 edges) · **Buttons on the car bar** · **Car-bar item order**. |
| **Theme · units · language** | *Settings › Display & units*: **Display units** · **Light / dark theme** · **Colours** · **Language** (By-car / VI / EN) · **Real glass (blur the backdrop)**. |

### 4. Driver profiles

- *Settings › **Driver profiles***: **Profile list** · **Active profile** · **Profile on engine start** (last used, or a fixed one) · **Add profile (a copy)**; rename and **Delete** sit next to each profile.
- **What a profile owns**: layout, slot contents, top-bar chips, button bar, wallpaper, theme, units, language, address book. **Not** owned: *System & permissions*, the cluster-cast geometry, *About*.
- **"Scenes" were folded into profiles** — each old scene is now a profile of the same name; one concept instead of two.
- **Export / import**: *Export profile (backup)* writes into `Android/data/com.byd.launcher/files/profiles/`; *Import from file* reads the same folder. Duplicate names become `<name> 2`, `<name> 3`…
- **Quick switch**: the profile chip in the top bar, or say *"chuyển sang hồ sơ X"*.

### 5. Configuration by Settings group

#### 5.1 Car comfort

| Block | Row / chip | Notes |
|---|---|---|
| **Turn-signal camera** | **Camera on turn signal** | Left signal → left camera overlay; right → right. Held until the **signal lamp goes off** (2.70; measured on a Seal 26/09, 87 ms lag). |
| | **Show the camera on the cluster** | Overlay goes to the cluster instead of the main screen. |
| | **Left signal shows at** / **Right signal shows at** | Top-left / top-right, per side (default left TL, right TR). |
| | **Left signal: rotate video** / **Right signal: rotate video** | *No rotation* · *↺ 90°* · *↻ 90°* · *180°*. Defaults **left ↺ 90°, right ↻ 90°** (the mirror crop of the 360 camera is sideways). **The sides are independent** — fix only the row for the side that looks wrong. |
| | **Camera rendering** | *TextureView (default)* / *SurfaceView (lighter, rotates via the HAL — may not rotate)*. Only switch if the video **stutters while driving**; switch back if it looks wrong. 🚗 |
| | **Left signal camera** / **Right signal camera** | Camera id **0–5** per side. If nothing appears, try another id (Sealion 6 uses **cam 0**). |
| **Recirculation** | **Recirculation on engine start** | The car forgets it every start — Kachi turns it back on. |
| **Seat cool / heat** | **Adjust seats by temperature** + **Mode** (*Cool* / *Heat*) + **level per seat** | Tap a seat on the diagram to cycle: **off → level 1 → level 2**. Cool and heat are **mutually exclusive** (per the car's HAL). |
| **PM2.5 filter** | **Purify when the air is dirty** · **Purify now** | A *current dust level* line shows the reading; switch off = purify only when you press. |
| **Auto-defrost when it rains** 🚗 | **Auto-defrost when it rains** + **Front windscreen** + **Rear + mirrors** | Polls the rain sensor **every 5 min**; turns off when the rain stops — and **only what Kachi turned on**. Never confirmed in real rain. |

#### 5.2 Voice

| Block | Row | Notes |
|---|---|---|
| **Speech recognition (on-device)** | **Download / Update / Remove the Vietnamese model** | sherpa-onnx + `zipformer-vi`, **~266 MB**, once, sha256-pinned. **No audio leaves the car.** No network in the car: copy the whole pack tree into `Android/data/com.byd.launcher/files/sherpa/import/<pack>/` and press Download (sha256 still checked). |
| **Offline voice pack (on-device)** | **Download / Update / Remove** | A Piper pack; **Speak replies out loud** makes Kachi talk; **Prefer the offline voice** only matters once the pack is installed. |
| **Hey Kachi** (experimental) | wake-word switch · **Wake engine: ASR (no training needed)** | **Default off** (battery/CPU). Listens while the screen is on. **Auto-disables** with a notice after repeated false accepts. With it on the mic button also goes through the listening process, so **the first press no longer waits for a model load**. |
| **Confirmation** | **Ask before running** · **Read confirmation questions aloud** | **Kachi just runs by default** (empty list). Tick an action and **that** action asks first (trunk · sunroof · all windows…). |
| **Microphone** | **Microphone source** | *Auto* tries MIC first, then a noise-cancelling source. Change it if the mic hears poorly. |
| **Other** | **Default music app** | Used for *"phát nhạc"* when you name no app. |
| **Ways in** | the *Talk to the car* tile · the **mic button** in the status bar · a steering-wheel key | Bind the key under *Settings › Steering-wheel keys*, target **Kachi listens (on-device)**. |

> There is no VAD / sensitivity knob in the UI — the silence ceiling was raised from 800 to **1,200 ms** in 2.73 but the **default stays 600 ms**; changing it goes through the test bridge (`voice_vad_min_silence_ms`), not Settings.

#### 5.3 Steering-wheel keys

*Settings › **Steering-wheel keys***: **Listen to physical buttons** (master switch) · **Button bindings** · **Self-learned buttons** · **Learn a new key** (press the wheel button so Kachi captures the code) · **Check and fix now** (re-grants and rebinds after a reboot drops it). Targets: an app, the car's own assistant, or **Kachi listens (on-device)**. The button's native function is **preserved** — Kachi consumes only the exact configured combo.

#### 5.4 Navigation & cluster

| Row | Notes |
|---|---|
| **Navigation on the cluster** | Master switch; turning it on self-grants notification access (over dadb) and connects the source. |
| **Cluster display mode** · **Scroll long street names** | Centre view on/off; marquee for long road names (off = abbreviated). |
| **Default navigation app** | Used when you say *"dẫn đường tới …"* without naming an app. |
| **Reconnect the navigation source** | Press when the cluster goes quiet while the nav app is still running. |
| **Speed limit badge** · **Upcoming limit** · **Camera alert chip** · **Badge size** · **Badge position** | Drag to position. |
| **VietMap bubble** · **Bubble position** | Shows the VietMap bubble on the cluster. |
| **Address book** · **Add an address…** | Each entry = *name + address text + **optional** lat/lng*, stored **per profile**. Coordinates are **pasted by hand** from a map app — Kachi asks for no GPS write permission. VietMap takes only coordinates, Google Maps takes text ⇒ Kachi picks the app **from the entry's data**. |
| **Scheduled navigation** 🚗 | Time window × weekdays × *only with GPS* × one address-book entry × a nav app. **Once per window per day.** E.g. 07:30–09:00 Mon–Fri → the office. |

#### 5.5 Cluster cast

**Enable casting** · **Show the floating cast button** · **Split ratio** · **Autostart on engine start** (+ **Full-screen autostart app** / **Autostart split view** / **Left-hand app** / **Right-hand app**) · **Cast now: full, left, right, stop** · **Cluster rescue**. CarPlay / Android Auto always cast **full-screen**.

#### 5.6 System & permissions

**Missing permissions** (self-granted over dadb, no laptop) · **Auto-start Kachi on engine start** · **Run background service on engine start** · **Home screen** (set/unset) · **Keep Kachi as home screen on engine start** · **Check for updates** + **Auto update** · **Stop all navigation** · **Restart launcher** · **ADB test mode**.
**Advanced** also holds: the old ClusterNav screen · **Cluster diagnostics** · **Per-feature car capability test** · **Type a command** (test the parser without speaking) · **Recognise a test WAV**.
*About*: **Version and licence** · **Disclaimer**.

> **ADB test mode** can only be switched on **by hand in the car**, **self-expires after 60 min**, and **dies with the ignition cycle**. Every command is journalled.

### 6. Voice commands by group

**How to speak**:
- Say it **in one breath** — a thinking pause over ~0.8 s ends the turn (default).
- **Cancel a turn**: **tap outside the card**, or wait out the **8 s** ceiling. Since **2.73 Back no longer cancels** (it goes to the app behind) — deliberate, so the car's navigation bar stops popping up while Kachi speaks. This is **not a bug**.
- Chain two actions with ***và*** or ***rồi***.
- Numbers work spoken or as digits (*"hai mươi bốn"* = *"24"*).

| Group | Example (Vietnamese, as spoken) | What Kachi does |
|---|---|---|
| **Car toggles** | *"bật đèn đọc"* · *"tắt đèn nóc"* · *"bật lọc bụi"* · *"bật gió tự động"* · *"bật sấy kính trước"* · *"bật sạc không dây"* | Toggles the button and reads back *"đã bật …"* |
| **Windows · trunk · sunroof** | *"mở kính lái"* · *"đóng kính trước trái"* · *"hạ bốn kính"* · *"mở cốp"* · *"mở cửa sổ trời"* · *"mở rèm che nắng"* | Open/close (with a 50 % step). Some actions **ask first** if you ticked them under *Ask before running* |
| **Macros** | *"mở hết kính"* · *"đóng hết kính"* | Runs several steps in order |
| **AC · seats** | *"đặt nhiệt độ 24 độ"* · *"tăng nhiệt độ"* · *"tăng gió"* · *"giảm gió"* · *"bật sưởi ghế"* · *"mở quạt ghế"* · *"lọc ngay"* | Sets / steps a level; seats have 3 levels (off · 1 · 2) |
| **Ask about the car** | *"xem pin"* · *"pin còn bao nhiêu"* · *"nhiệt độ ngoài trời bao nhiêu"* · *"đọc tầm hoạt động"* | Reads the **current value** (**64** registered data points: battery · tyres · temperature · range · time…). See `kachi-feature-catalog.html` for the full list |
| **Open / close an app** | *"mở YouTube"* · *"mở bản đồ"* · *"đóng YouTube"* | Slightly misheard names (*"du túp"*, *"gu gồ máp"*, *"youtubex"*) still resolve |
| **App into a slot** | *"mở YouTube vào ô số 9"* · *"đưa YouTube vào ô hai"* | The app runs in that slot. Say it **in one breath** — the *"vào ô số N"* clause is easy to drop if you pause (2.73 makes it sturdier, 🚗 not yet confirmed by live speech) |
| **Music / YouTube** | *"phát nhạc"* · *"dừng nhạc"* · *"bài tiếp theo"* · *"đang phát bài gì"* · *"phát bài Diễm Xưa"* | Media control; the track name is captured by a **second recognition pass** over the same audio |
| **Navigation** | *"dẫn đường đến Bitexco"* · *"dẫn đường tới chợ Bến Thành bằng Waze"* | Hands off to the right app. **No confirmation** — it navigates straight away |
| **Saved places** | *"về nhà"* · *"đến công ty"* · *"đi làm"* | Navigates to an **Address book** entry of the active profile |
| **Profiles** | *"chuyển sang hồ sơ Test"* · *"đổi sang hồ sơ Chính"* | English names work; **a missing name** makes Kachi ask *"Hồ sơ nào — A hay B?"* |
| **Layouts** | *"bố cục hai ô"* · *"đổi bố cục 4 ô"* · *"về bố cục 2 hàng"* | Changes the active profile's home layout |
| **Launcher** | *"mở cài đặt"* · *"mở ứng dụng"* · *"nói với xe"* | Opens that screen |
| **Compound** | *"bật đèn đọc và tắt lọc bụi"* · *"mở cốp rồi bật đèn đọc"* | Runs each clause in order; an unparsed clause is skipped |

**When Kachi does not understand** it says *why* rather than going silent: unclear intent · missing object · action does not fit that object · or *"… đã bỏ khỏi Kachi — dùng màn hình của xe"* for features that were removed (car lock · volume · screen brightness · door unlock · ambient light).

### 7. Car image · wallpapers · slideshow

| Folder (SD card, **no permission needed**) | Used for |
|---|---|
| `Android/data/com.byd.launcher/files/car/` | Top-down car image (default `seal-3`, no logo) |
| `Android/data/com.byd.launcher/files/wallpapers/` | Home-screen wallpapers |
| `Android/data/com.byd.launcher/files/photos/` | Photo-slideshow widget |

Every row in *Settings › Home screen* has a **Copy folder path** button — paste it into a file manager / `adb push`. With no image of your own, Kachi **draws the car as vector art** (pick the model + paint colour).

### 8. Automation

| Feature | Path | Status |
|---|---|---|
| **Auto-defrost when it rains** | *Settings › Car comfort › Auto-defrost when it rains* | 🚗 never seen real rain |
| **Scheduled navigation** | *Settings › Navigation & cluster › Scheduled navigation* | 🚗 never ran a real window |
| **Recirculation on engine start** | *Settings › Car comfort › Recirculation* | ✅ in daily use |
| **Seat cool/heat · PM2.5 auto-filter** | *Settings › Car comfort* | 🟢 confirmed on a car |
| **Auto-start / background service / keep HOME on engine start** | *Settings › System & permissions › Startup* | ✅ |

All four follow one rule: **only undo what you turned on**. If you flipped it by hand, Kachi leaves it alone.

### 9. Restart the launcher

*Settings › System & permissions › **Restart launcher*** — for display glitches, **without rebooting the head unit**. If the cluster goes quiet while the nav app runs, try **Reconnect the navigation source** or **Cluster rescue** first.

### 10. Logs / diagnostics to send in

- **Easiest**: *Settings › System & permissions › Advanced › **Cluster diagnostics*** — the app **captures everything itself**. Just **send a screenshot**; you never type a command.
- Logs on the SD card: `/sdcard/Android/data/com.byd.launcher/files/kachi-logs/` — `usage-*.log` · `snapshot-*.log` (from *Capture log now*) · `captest-report.txt`.
- Pull with `adb pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ ./kachi-logs/`, or copy the folder with a file manager.
- **Send along**: the **version** (*Settings › About*) + what went wrong + a screenshot. Never guess the running version — read it from the About screen.

### 11. Troubleshooting (FAQ)

| Symptom | Try in this order |
|---|---|
| **Tapping the APK fails** *"Fail in installation of desktop apps"* | The ROM blocks installing a launcher by tap. Use `adb install -r`. |
| **Voice does nothing** | 1) *Settings › Voice › Speech recognition (on-device)* — is the model installed? 2) Microphone permission (*Missing permissions*). 3) Try another **Microphone source**. 4) Use **Type a command** in *Advanced*: if the typed sentence works, the problem is **hearing**, not **understanding**. |
| **Heard, but a clause was dropped** (e.g. *"vào ô số 2"*, a profile name) | Say it **in one breath**. 2.73 hardens both cases — 🚗 not yet confirmed by live speech on a car. |
| **Back does not cancel listening** | By design since 2.73. Cancel by **tapping outside the card**, or wait 8 s. |
| **The car's navigation bar pops up while Kachi speaks** | Present in every build **≤ 2.72**; fixed in 2.73 (🚗 not yet seen on a car). Workaround: turn off *Speak replies out loud*. |
| **Turn-signal camera does not appear** | 1) Is **Camera on turn signal** on? 2) Try another **Left/Right signal camera** id in 0–5 (Sealion 6 uses **cam 0**). 3) Only measured on **Seal** and **Sealion 6** — other cars 🚗 unknown. |
| **Camera appears then closes after ~1 s** | A **2.69** bug. Update to **2.70+**. |
| **Camera video is sideways / upside down** | Fix that side's row only: **Left signal: rotate video** or **Right signal: rotate video**. |
| **Camera video stutters while driving** | Try **Camera rendering → SurfaceView**. ⚠ SurfaceView rotates via the HAL so it **may not rotate** — switch back to *TextureView (default)* if it looks wrong. The cause is still open. |
| **The car button bar is gone** | *Settings › Status bar & button bar › **Show the car bar***; also check **Button bar edge** and **Buttons on the car bar** (it may be empty). |
| **A car button does nothing** | Not every button is writable on every ROM. Look that button up in `kachi-feature-catalog.html` (🟢 works · ⚠ partial · ❌ no effect · 🚗 unmeasured), or run *Advanced › **Per-feature car capability test***. |
| **Casting to the cluster fails** | 1) Is **Enable casting** on? 2) Press **Cluster rescue**. 3) **Restart launcher**. After an engine start the cluster needs a few seconds to be ready. |
| **Home button does not return to Kachi** | *System & permissions › Home screen* — the status line says whether Kachi is home; press **Set Kachi as home**. If the ROM keeps changing it, enable **Keep Kachi as home screen on engine start**. |
| **OTA fails / no new build** | 1) The car needs a network while checking. 2) The channel is only `apk/Kachi-<ver>-release.apk` on `main` — if nothing newer is published there is nothing to fetch. 3) Builds installed before **1.41** use a different signing key ⇒ uninstall and install by hand once. |
| **A spoken reply goes missing** | The speech process (`:tts`) occasionally dies and restarts — a known, isolated fault; it does not take the launcher down. |
| **YouTube closes by itself** | [measured on a car, 26/09] YouTube crashed on its own — **not Kachi**. |

### 12. Car coverage · what is still unmeasured

| Item | Status |
|---|---|
| Turn-signal camera (open/close with the lamp, default rotation) | **🟢 measured on a Seal** (2026-09-26) and **Sealion 6** (2.6x). Other cars 🚗 |
| Car buttons (windows, trunk, lights, AC, seats, purification) | Per-button status — see `kachi-feature-catalog.html`. Evidence includes a tester's full pass on a **Sealion 6** |
| Aspect-correct camera frame · **Camera rendering** (2.73) | 🚗 not measured on a car |
| Voice: *"into slot N"* · fuzzy app names · profile names (2.73) | 🚗 measured only by **replaying the car's own recordings on a host** — never spoken live on a car |
| Navigation bar staying down while Kachi speaks (2.73) | 🚗 the cause was measured on the car, **the fix was not seen** |
| Rain → defrost · Scheduled navigation | 🚗 no real rain session / no real time window yet |
| How much RAM 2.73 actually saves | 🚗 **unknown** — quote no number |
| GPS / mock location | **Removed for good** 2026-07-27. Location permission is **read-only**; Kachi can never act as a mock-location app |
