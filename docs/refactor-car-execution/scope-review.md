# Soát lại scope cả dự án — 2026-07-27

Số trong tài liệu này đều đo bằng lệnh, không nhớ lại. Cách đo ghi kèm để đo lại được.

## 1. Dự án đang có ba đường, không phải hai

`README` nói hai đường (Navigation + HUD, Cluster Cast). Từ hôm nay có đường thứ ba đang ở
vòng đánh giá: **biển báo giới hạn tốc độ**. Cần sửa README khi đường này qua được vòng khám phá,
chứ không sửa trước — nói có một tính năng chưa chứng minh được là đúng loại tuyên bố quá mức mà
dự án này đang tránh.

## 2. Mã nguồn: hơn một phần ba KHÔNG thuộc sản phẩm đích

| Phần | File | Dòng | Thuộc sản phẩm đích? |
|---|---:|---:|---|
| Cast V1 + UI ⚠️ | 24 | 5 970 | **gộp nhóm SAI — xem mục 13**: V1 thật sự chỉ 2.110 dòng, phần còn lại là UI đang dùng |
| Cast V2 | 27 | 5 145 | có |
| app shell, prefs, Home | 28 | 2 770 | có |
| Navigation + HUD | 25 | 2 473 | có |
| car-exec (đánh giá) | 10 | 2 242 | công cụ, không xuất xưởng |
| ~~Dead Reckon / mock-location~~ | ~~6~~ | ~~1 096~~ | **đã xoá hẳn 2026-07-27** |
| HAL / infra | 2 | 264 | có |
| ~~vd_map (thử nghiệm cũ)~~ | ~~2~~ | ~~185~~ | **đã xoá 2026-07-27** |
| **Tổng** | **124** | **20 145** | |

**Sau khi xoá Dead Reckon: 6 155 dòng (32%) còn nằm ngoài sản phẩm đích** (bản đầu là 7 251 / 36%). Trong đó engine V1 là phần lớn nhất và cũng là phần
đang giữ 8 điểm mở adb ngoài module transport. Không xoá được trước khi V2 chạy trên xe — nhưng
phải xoá ngay sau đó, không để trôi.

`vd_map` (185 dòng, 2 file) là thứ nhỏ nhất và không ai nhắc tới nữa: nên quyết dứt điểm giữ hay bỏ.

## 3. Test: lệch mạnh giữa hai đường chính

| Vùng | Số test |
|---|---:|
| Cluster Cast | 419 |
| parser dùng chung (AppScale/StackParse/WmParse/DisplayParse) | 88 |
| car-exec (đánh giá) | 55 |
| **Navigation + HUD** | **42** |
| kiến trúc / bằng chứng | 17 |
| còn lại | 41 |
| **Tổng** | **662** |

Navigation + HUD có 2 473 dòng mà chỉ 42 test, trong khi Cast có 419. Đây là **lệch scope thật**:
đường mà chủ xe dùng hàng ngày lại được kiểm ít hơn nhiều so với đường đang bị chặn. Chưa hỏng
không có nghĩa là đúng — nó nghĩa là chưa ai kiểm.

Cách đo: đếm theo **tên class**, không theo tên đầy đủ. Lần đầu tôi đếm theo tên đầy đủ và ra
"Navigation 603 test" — vì package `com.byd.clusternav` chứa chữ `nav` nên khớp mọi class. Cùng họ
với năm lỗi đo trước đó.

## 4. Bằng chứng trên xe: 6 trong 31 step

| Tính năng | Step đã có ít nhất một candidate OK |
|---|---|
| CLUSTER_CAST | 6 / 21 |
| SPEED_SIGN | 0 / 7 (mới khai hôm nay) |
| NAVIGATION | 0 / 3 |

Đã chứng minh, có người xác nhận: đặt app lên cụm, mở chiếu `30,16,35`, hạ chiếu `18,0`, đọc
dumpsys, chụp trạng thái, nhận diện profile. Một FAIL thật: `am stack move-task` báo Exception.

Navigation 0/3 không có nghĩa nó không chạy — nghĩa là **chưa có bằng chứng máy đọc được** cho nó,
giống hệt tình trạng Cast trước hôm nay.

## 5. Cái đã đóng, và cái nó buộc phải đổi

**Q1 đã đóng, kết quả âm tính.** Không tín hiệu chỉ-đọc nào ở tầng Android phân biệt "cụm hiện app"
và "cụm hiện đồng hồ". Có người xác nhận cả hai đầu, ghim bằng test trên hai fixture thật.

Hệ quả không tránh được: xác minh chia hai hạng — *đo được* và *người xác nhận*. Đã mô hình hoá
trong `ClusterAttestation`. Chưa nối UI (đúng thứ tự hai đường ray).

## 6. Vấn đề đang chặn đường app

V2 chỉ phát `30,16,35` khi `durable envelope pristine epoch 0`, tức **chỉ lần chạy đầu sau khi cài**.
Sau khi Dừng hoặc tắt máy, lần chiếu sau chỉ đặt task và cụm nằm im ở đồng hồ. Đây là nguyên nhân
"cast không lên", nằm ở một dòng điều kiện.

Chưa sửa, và **cố ý chưa sửa**: luật đúng phụ thuộc kết quả step `reissue-policy` ngày mai. Nếu phát
lại lúc cụm đang có app không treo máy thì đường app đơn giản hẳn.

## 7. Nợ còn lại, xếp theo thứ tự nên trả

| Việc | Vì sao chưa làm |
|---|---|
| Xoá engine V1 (5 970 dòng, 8 điểm adb) | phải chờ V2 chạy trên xe |
| Sửa điều kiện phát chuỗi mở chiếu | chờ kết quả `reissue-policy` |
| Nối `AttestationNeed` vào bộ chiếu trạng thái | lõi xong, UI theo sau |
| Test cho Navigation + HUD | chưa ai kiểm; nợ lớn nhất về chất lượng |
| Đổi tên package 26 file `:core` | chờ xoá V1 để không đổi hai lần |
| Quyết `vd_map` giữ hay bỏ | chưa ai nhắc |
| ~~`NavRealtimeModule` đổi tốc độ ra km/h~~ | **đã soi 21:07** — chỉ là màn chẩn đoán trong app, không tính gì và không đẩy lên cụm/HUD. Không trùng với đồng hồ của xe |
| Fixture profile cho `observe --recorded` | chặn chuỗi quan sát off-car |
| ~~Dead Reckon / mock 1 096 dòng~~ | **xong** — xoá hẳn 2026-07-27 theo quyết định của chủ dự án |

## 8. Bài học vận hành, ghi lại vì đã tái diễn

Hôm nay có **ba** lỗi kiểu "báo xong mà không xong" (sổ verdict ghi vào file bóng, `--note` bị cắt
âm thầm, emulator chạy trên trạng thái sót) và **sáu** lỗi đo của chính bộ ratchet/kiểm kê (đếm
call thay vì ranh giới, đếm theo package, quét sai module, đếm trong transport, chỉ đếm dòng
`import`, và đếm test theo tên đầy đủ).

Quy tắc rút ra, đã áp dụng: **mọi tuyên bố thành công phải có một phép đọc lại độc lập.** Chính
`wc -l` trên sổ verdict là thứ giữ cho phiên xe hôm nay không mất trắng.

## 9. Nợ ghi thêm 2026-07-27 21:00 — km/h trong `NavRealtimeModule`

Chủ dự án hỏi `SpeedProvider` có bỏ được chưa. **Chưa**, vì còn ba người dùng trong mã sống:
`ClusterBroadcaster` (nội suy cự ly tới điểm rẽ), `NavRealtimeModule` (đổi ra km/h), và
`ClusterCast.kt` của V1.

Nhân đó tách rõ hai chuyện hay bị lẫn:

| | Ai làm | Có trùng với xe? |
|---|---|---|
| Nội suy **cự ly tới điểm rẽ** giữa hai lần notification | ClusterNav | Không. Chỉ ĐỌC tốc độ, không ghi gì vào xe. Đang dùng hàng ngày để hạ lag notification |
| Suy **vị trí** khi mất GPS (Dead Reckon + mock) | ClusterNav, **đã bỏ hẳn** | Có. `CARTEST.md` mốc C1 ghi: nếu mock không đè được thì head unit dùng GPS OEM riêng → dừng hướng này. Xe còn có thể tự DR từ CAN |

**Nợ cần soi:** `NavRealtimeModule` đổi `SpeedProvider.mps()` ra km/h ở hai chỗ (dòng 34 và 76).
Nếu số đó chỉ để **hiển thị** thì cụm đã có đồng hồ tốc độ của xe, và hiển thị lại là dư — trùng lặp
thật, khác loại với chuyện nội suy. Nếu nó dùng để **tính** (ngưỡng, đếm ngược) thì giữ.

**Đã soi 21:07 — nợ đóng.** `NavRealtimeModule` là màn chẩn đoán: `selfTest` in một dòng "đọc tốc độ =
N km/h", và `read()` vẽ một khung monospace gồm tốc độ, cự ly thô từ notification, anchor, giá trị nội
suy đang đẩy lên cụm và ETA. Nó **không tính gì** bằng km/h và **không đẩy** số đó lên cụm hay HUD — chỉ
để so trên đường khi bật/tắt nội suy. Nên không trùng với đồng hồ tốc độ của xe. Không phải bỏ.

## 10. Chốt phần off-car — 2026-07-27 21:30

### Đã xong tối nay

| Việc | Kết quả |
|---|---|
| Xoá Dead Reckon / mock-location | 1.096 dòng biến khỏi cây; bài kiểm ĐẢO CHIỀU thành "phải biến mất" |
| Nợ test Navigation | 42 → **96** bài kiểm |
| `SpeedReading` tách sang `:core` | bất biến "không đọc được ≠ 0" lần đầu có kiểm |
| `NavScreenScan` tách sang `:core` | heuristic đọc màn hình lần đầu có kiểm (10 bài) |
| `RECONNECT_SOURCE` | **lỗi thật**: khai trong enum mà không nơi nào cấp → nguồn mất cập nhật là không có nút nào |
| `disabledReasons` | **lỗi thật**: map luôn rỗng → nút mờ không giải thích được |
| Đường ra cụm/HUD | 3 bài kiểm dựng lại đúng ca đã treo Cast: `submit` không chặn, hàng đầy báo FAULT có lý do, `close` không treo |
| `RecordedDevice` mapping sai | `PROFILE_STATE` từng trỏ vào file settings → thông điệp trách oan "định dạng"; đã sửa + bài kiểm hình dạng fixture |
| CRLF | 0 file còn CRLF trong `:app` và `:core` |

**696 bài kiểm xanh** (app 364 · core 318 · car-integration 14). `core leak: 0`, `car-int leak: 0`.

### Off-car còn lại — đều BỊ CHẶN hoặc CẦN QUYẾT ĐỊNH, không phải bị bỏ

| Việc | Chặn bởi |
|---|---|
| Xoá engine V1 (5.970 dòng, 8 điểm adb) | V2 phải chạy được trên xe trước |
| Sửa điều kiện phát `30,16,35` | chờ kết quả `reissue-policy` mai — sửa trước là đoán |
| Nối `AttestationNeed` vào bộ chiếu trạng thái | phần lõi xong; UI làm cuối theo đúng hai đường ray |
| Đổi tên package 26 file `:core` | chờ xoá V1 để không đổi hai lần |
| ~~`vd_map`~~ | **xoá 21:35** theo quyết định của chủ dự án: "chạy được cast rồi không dùng nữa" |
| Fixture `am get-current-user` | cần một lệnh trên xe, đã dặn trong `run-on-car.md` |

### Cố ý KHÔNG làm, kèm lý do

- **5 file thuần còn trong `:app`** (`CastOperationStatus`, `CastActivityRefresh`, `CastActivityWork`,
  `ModuleRegistry`, `NavAccessibilitySource`): dời bây giờ là churn không thêm giá trị kiểm nào; ratchet
  đang canh để con số không tăng.
- **`NavDiag` (50 dòng) không có bài kiểm**: là vòng đệm chẩn đoán, sai thì hậu quả chỉ là màn debug hiển
  thị lệch. Viết test cho nó chỉ để con số đẹp hơn.
- **Chữa ca luồng giao kẹt** (dựng lại executor): chưa có bằng chứng ngoài đời rằng nó xảy ra; giới hạn
  đã ghi ngay tại chỗ cấp `RETRY_*` để không hứa điều làm không được.

## 11. Xoá `vd_map` và cả chuỗi transport chỉ nó dùng — 21:40

Chủ dự án: *"chạy đc cast rồi ko dùng vd_map này nữa, xoá đi"*.

`vd_map` chiếu app map vào một `VirtualDisplay` gắn `SurfaceView` để hiện ở **màn giữa** — không liên
quan cụm. Nó chưa dùng được thật: chú thích của chính nó ghi *"v1: CHỦ YẾU để HIỂN THỊ… Chạm cần
INJECT_EVENTS (app thường bị chặn)"*, tức map hiện ra mà không bấm được. Nó cũng là **ngoại lệ kiến trúc
duy nhất** của dự án (module duy nhất cần `<activity>` riêng trong Manifest).

Xoá nó kéo theo một chuỗi, và đây là phần đáng ghi: **`DadbBridge` không còn ai dùng**, vì `vd_map` là
người dùng duy nhất. Nên xoá luôn `DadbBridge` và `PersistentDeviceShell` — cái transport tôi vừa tạo
buổi chiều cùng ngày để chứa nó. Giữ một transport không ai gọi chỉ là nợ chờ mục.

| Xoá | Dòng |
|---|---:|
| `modules/vdmap/` (2 file) | 185 |
| `modules/hal/DadbBridge.kt` | 39 |
| `carexec/PersistentDeviceShell.kt` | 61 |
| dòng `ModuleRegistry` + khối `<activity>` Manifest | 12 |

Cây: **19.048 → 18.953 dòng**, 124 → 116 file. `car-integration` còn 5 file / 802 dòng. 696 bài kiểm
xanh, không đổi số — cả ba phần xoá đều không có bài kiểm nào, đúng như tình trạng của chúng.

Mã ngoài sản phẩm đích giờ chỉ còn engine V1 (5.970 dòng), và nó chặn bởi việc V2 phải chạy được trên xe.

## 12. Checklist kiến trúc 22:20 — hai lỗi của chính tôi

Chạy đủ 5 bước. Bốn bước máy đạt (`core leak 0`, `car-int leak 0`, attestation phủ ba module, 3/3 đường
dẫn còn sống). Phần mắt người bắt được hai thứ, cả hai do tôi làm trong cùng buổi:

**1. 8 file test nằm sai chuồng** — kiểm lớp `:core` nhưng đặt trong `app/src/test`, trong đó hai file tôi
viết tối nay. Đã dời; app 364 → 262, core 318 → 421, tổng giữ 697. Đã biến thành luật máy giữ
(`LayeringRulesTest`), kiểm ngược bằng cách dời tạm một file → luật đổ và gọi tên.

Vòng hai của cùng luật: bốn file trong số đó nằm ở **gốc package** `com.byd.clusternav` trong khi chủ thể
ở `com.byd.clusternav.navigation`. Đã đưa vào đúng package.

**2. Vi phạm P2 "migration only moves"** — lần tách `SpeedReading` không chỉ dời chỗ. Bản gốc chặn bằng
`if (kmh < 0 || kmh > 400) return null`; với `NaN` thì **cả hai phép so đều false** nên `NaN` lọt qua và
`lastGoodMps` thành `NaN` vĩnh viễn, khiến mọi phép so ngưỡng ở tầng trên (kể cả `speed < 2.0`) đều false.
Tôi đã thêm chặn `NaN`/vô cực trong lúc tách — sửa hành vi lẫn vào một lần dời file.

Bản vá đúng và có bài kiểm, nên giữ; nhưng đã **khai báo tường minh** trong KDoc của `SpeedReading` thay vì
để nó lẫn vào diff. Bài học: P2 không phải luật hình thức — chính tôi vi phạm ngay lần tách đầu tiên trong
buổi, và không có bước checklist thì nó đi vào lịch sử như "chỉ tách file".

## 13. ĐÍNH CHÍNH con số "mã ngoài sản phẩm đích" — 22:35

Tôi đã nói **5.970 dòng V1** nhiều lần tối nay, kể cả trong commit message và mục 2 của tài liệu này.
**Sai.** Con số đó là bucket `Cast V1 + UI` trong script kiểm kê đầu tiên — nó gộp cả **Cast UI đang dùng**
vào cùng nhóm với engine V1. Lỗi phân loại của chính tôi, và tôi lặp lại nó thay vì kiểm.

Đo lại bằng cách tìm file nào **chỉ V1 dùng**:

| | File | Dòng |
|---|---:|---:|
| Engine V1 thật sự (`ClusterCast`, `CastShell`, `ClusterProfile`, `ClusterDiag`) | 4 | **2.110** |
| Cast UI + façade — **giữ, đang dùng** | 16 | 3.113 |

Nên phần chờ xoá là **2.110 dòng**, không phải 5.970. Cây hiện 18.953 dòng, tức mã ngoài sản phẩm đích còn
**11%**, không phải 31%.

Bài học lặp lại lần thứ bảy trong ngày: **một con số đến từ script phân loại chưa được kiểm thì chưa phải
sự thật.** Sáu lỗi trước đều là phép đo (đếm call thay vì ranh giới, đếm theo package, quét sai module,
đếm trong transport, chỉ đếm dòng `import`, đếm test theo tên đầy đủ); lần này là phép **gộp nhóm**.
