# VISUAL-REFRESH P1 — bằng chứng đo (2026-09-16 · Kachi 1.68 · 69 → **Pass 5: 1.69 · 70**)

> ⚠⚠ **PASS 5 (2026-09-17 · 1.69 · 70) — `after/` ĐÃ CHỤP LẠI LẦN THỨ HAI.** Owner nhìn 1.68 trên xe:
> *"làm bóng ở đầu mỗi nút nhìn kỳ lắm, không đẹp đâu, với nó có 1 cái gạch trên top đấy nhé, bug rồi"*.
> Hai lớp ánh sáng ở đỉnh thẻ (dải mờ dần 35 % + nét đỉnh ĐẶC 1–2dp) đã **gỡ hẳn** khỏi `KachiTheme.surface()`;
> hai vai màu `surfEdge`/`surfOnEdge` xoá khỏi `KachiPalette`. Chiều nổi nay nằm hết trong **chuyển sắc dọc**
> ([ĐO] đỉnh ÷ đáy **1.29×** ở bảng TỐI, xem `contrast-table.md`) + ba bậc nền→khay→thẻ + hairline; gradient và
> **sắc lĩnh vực** ở thẻ lớn giữ nguyên. Chi tiết + lý do chọn *bỏ hẳn* thay vì *hạ ≤ 8 %*:
> spec `../../specs/kachi-visual-refresh.html` §1 (Pass 5) và §9 mục 14–17.
>
> Bộ `after/` hôm nay là của **Pass 5**; `before/` vẫn là 1.67 · 68 (không chụp lại). Bảng số của mục 0 và mục 2
> dưới đây là số **Pass 4** — giữ nguyên làm lịch sử đo; hai dòng đã hết đúng:
> *mép sáng 3.06×* (nay **không còn lớp mép sáng**) và dòng `surfEdge trên surfFrom` của `contrast-table.md`
> (nay là `surfFrom ÷ surfTo`).


> ⚠ **Thư mục này đã được CHỤP LẠI ở lượt soát Pass 4** (cùng ngày, cùng số hiệu 1.68 · 69 — bản này chưa
> đăng APK nào nên không phải tách số, CLAUDE.md §9). Thư mục `before/` **giữ nguyên** (bản 1.67 · 68);
> `after/` và bốn ảnh icon là của bản **sau Pass 4**. Vì sao phải chụp lại: xem mục 0.

Spec: [`../../specs/kachi-visual-refresh.html`](../../specs/kachi-visual-refresh.html) — T1 · T2 · T3 · T4 (phần đo
gradient) · R7 · T11 (phần P1). Máy đo: AVD `emulator-5554`, **API 29 / Android 10**, 1920×1080.

---

## 0 · Vì sao phải làm lại — [ĐO] lượt P1 KHÔNG chạm vào bề mặt lớn nhất màn hình

Parent soát ảnh và gọi đúng tên: đổi mà **nhìn không ra ở khoảng cách lái xe**. Phép đo chốt nguyên nhân
(điểm ảnh, không phải cảm nhận) — so `before/home-4o-toi.png` với `after/home-4o-toi.png` của lượt P1:

| Điểm đo | TRƯỚC P1 | SAU P1 | Kết |
|---|---|---|---|
| **giữa ô làm việc** (800,320) | `(23,26,32)` | `(23,26,32)` | **không đổi MỘT byte** |
| giữa ô con "Trong xe" (1050,366) | `(28,33,43)` | `(23,28,38)` | có đổi |

Ô làm việc là **bề mặt lớn nhất màn hình**, và nó nằm ngoài lượt P1 vì bảng rà của spec §9 đi từ chuỗi
`KachiTheme.card(` — còn `WorkspaceView.makeSlot` dựng `GradientDrawable` **thẳng tại chỗ**, nên nó không
bao giờ xuất hiện trong danh sách 26 chỗ gọi. Một cuộc rà theo *tên hàm* chỉ thấy những chỗ đã dùng tên hàm đó.

Pass 4 làm hai việc: (a) nối ô làm việc vào hệ chất liệu (`SurfaceTone.WELL` — khay tối hơn thẻ một bậc, mang
sắc lĩnh vực của chính nội dung trong ô); (b) đẩy từng lực của chất liệu lên tới sát sàn tương phản. Số mới:

| Lực | P1 | Pass 4 | Sàn bài canh |
|---|---|---|---|
| bước sáng thẻ/nền (TỐI) | 1.23× | **1.35×** | 1.15× |
| mép sáng so với mặt thẻ (TỐI) | 1.7× | **3.06×** | 2.20× (bài mới) |
| bậc khay/nền · thẻ/khay (TỐI) | — · **1.00×** | 1.14× · **1.19×** | 1.12× · 1.15× (bài mới) |
| thẻ BẬT so với thẻ thường (TỐI) | 1.28× | **1.59×** | 1.20× |
| ô LÕM so với thẻ (TỐI) | 1.23× | **1.39×** | — |
| sắc lĩnh vực (TỐI · SÁNG) | 1.07–1.15× | **1.11–1.24×** · 1.11–1.13× | 1.10× (bài mới) |

Giá phải trả, ghi rõ: **bốn vai mực của bảng TỐI sáng lên** và **sáu vai mực của bảng SÁNG đậm lên** một bậc —
bắt buộc, vì nền sáng lên và tint đậm lên thì mực cũ tụt dưới 4.5:1. Ở bảng tối mực sáng hơn luôn tốt hơn trên
mọi nền; ở bảng sáng mực đậm hơn cũng vậy. Chi tiết ở `KachiPalette.kt`, số ở `contrast-table.md`.

---

## 1 · Ảnh máy ảo TRƯỚC / SAU — 20 ảnh

| Màn | TRƯỚC | SAU |
|---|---|---|
| Màn chính · bố cục **1 ô** · TỐI | `before/home-1o-toi.png` | `after/home-1o-toi.png` |
| Màn chính · bố cục **2 cột** · TỐI | `before/home-2cot-toi.png` | `after/home-2cot-toi.png` |
| Màn chính · bố cục **4 ô** · TỐI | `before/home-4o-toi.png` | `after/home-4o-toi.png` |
| Cài đặt › Thanh trạng thái & thanh nút · TỐI | `before/settings-topstrip-toi.png` | `after/settings-topstrip-toi.png` |
| Bộ chọn nút thanh xe · mục **Khí hậu** · TỐI | `before/picker-climate-toi.png` | `after/picker-climate-toi.png` |
| …và **đúng 5 màn ấy ở chủ đề SÁNG** (`*-sang.png`) | `before/` | `after/` |

Cùng một AVD · cùng hồ sơ *Mặc định* · cùng dữ liệu giả (xe không nối, mọi datum `—`).

⚠ **Pass 4 đổi CÁCH đi tới màn cần chụp, không đổi màn được chụp**: bố cục và chủ đề nay đặt bằng **dữ liệu**
(sửa `shared_prefs/kachi_workspace.xml` qua `run-as` rồi khởi động lại app) thay vì mò qua Cài đặt — CLAUDE.md
§15 xếp "đọc/ghi state bền" trên "mò UI", và cách này khiến ma trận chạy lại được y hệt. Hai màn còn lại
(Cài đặt · bộ chọn nút) vẫn phải đi bằng UI vì chúng **là** thứ cần chụp; đường đi cố định trong `ui2.sh`.

⚠ Ảnh `picker-climate-*` của Pass 4 có ô *Khí hậu & không khí* đang **BẬT** (chạm một cái rồi thoát, KHÔNG bấm
*Áp dụng* ⇒ cấu hình không đổi) — để thấy tone ACTIVE cạnh tám ô NEUTRAL trong cùng một khung hình.

⚠ **Pass 5 đổi hai thứ trong khung hình, ghi ra để không ai tưởng là hồi quy**: nội dung ô đặt lại đúng cặp
của Pass 4 (ô0 = nhóm *Kính*, ô1 = nhóm *Khí hậu*) và `top_strip_labels=false`; nhóm *Khí hậu* nay **13 ô**
(H1·T2 thêm 6 datum ở cùng gói 1.69) còn nhóm *Năng lượng* còn **6 ô** (lượt (V) FEATURE-FILTER gỡ 5 ô sạc)
— cả hai là của **lượt khác**, không phải của Pass 5.

**Nhìn cái gì (Pass 5)**: đỉnh mỗi tile/chip/ô bộ chọn **không còn một vạch sáng** nào; ngoài ra vẫn như cũ —
**Nhìn cái gì**: có đọc ra **ba bậc** không (nền màn → khay ô làm việc → thẻ nội dung); khay có mang **sắc lĩnh
vực** của nội dung không (ô *Khí hậu* ngả lam-xanh, ô *Kính* trung tính); thẻ có **lồi** khỏi khay không (chuyển
sắc dọc + nét đỉnh + hairline); chữ còn đọc được không; ô đang chọn có **khác hẳn** ô chưa chọn không; ô lõm
(ô nhập giọng nói, chip tắt) có **bị đổi nhầm** sang bề mặt lồi không.

## 2 · Bảng đo tương phản — [`contrast-table.md`](contrast-table.md)

**Sinh bằng máy** từ `KachiPalette` bởi `SurfaceContrastContractTest`, chạy mỗi lượt `:app:testDebugUnitTest`
(AC6.6 — chép tay là cách bảng màu và tài liệu lệch nhau). Đừng sửa tay tệp đó.

Bốn dòng đáng nhớ (số của **Pass 4**):
- chữ chính trên hai đầu chuyển sắc: **12.57 / 16.23** (tối) · **18.17 / 16.31** (sáng);
- bước sáng thẻ BẬT ↔ thẻ thường: **1.59×** (tối) · **1.74×** (sáng) ⇒ trạng thái chọn nhìn ra được;
- mực **tệ nhất** trên thẻ đã phủ sắc lĩnh vực: **4.59** (tối) · **5.13** (sáng) — vẫn trên sàn 4.5;
- ba mốc cũ **không xấu đi**: `lineStrong` 3.13/4.20 · `emptyLine` 3.68/3.07 · trắng trên `gradFrom` 4.83/5.74.

Bảng nay có thêm năm dòng cho **ba bậc** (`slot ÷ bg` · `surfFrom ÷ slot` · `surfEdge trên surfFrom` · mực trên
hai đầu khay) — ba bài canh mới khoá đúng ba con số đó, xem `SurfaceContrastContractTest`.

## 3 · `gfxinfo` — ⚠ máy ảo KHÔNG phân giải được ngưỡng ±1.5 ms

Kịch bản cố định (`perf.sh`): mở màn chính → Cài đặt → nhóm *Thanh trạng thái & thanh nút* → cuộn 10 lần xuống +
10 lần lên → đi qua 3 nhóm khác → cuộn lại 20 lần → thoát. ≥ 776 khung mỗi lượt (spec đòi ≥ 300).

| Lượt | Bản | Khung | Janky | 50th | 90th | 95th | 99th |
|---|---|---|---|---|---|---|---|
| trước | 1.67 (68) | 786 | 37.40 % | 6 ms | **44 ms** | 65 ms | 105 ms |
| sau · lượt 1 | 1.68 (69) | 824 | 39.44 % | 7 ms | **48 ms** | 77 ms | 125 ms |
| sau · lượt 2 | 1.68 (69) | 801 | 20.35 % | 5 ms | **31 ms** | 46 ms | 81 ms |
| sau · lượt 3 | 1.68 (69) | 776 | 40.72 % | 7 ms | **57 ms** | 77 ms | 117 ms |

**Kết luận [ĐO] (lượt P1)**: ba lượt của **CÙNG một bản** cho 90th = **31 / 48 / 57 ms** (biên độ **26 ms**) và janky
20.35–40.72 %. Nhiễu giữa các lượt **lớn gấp ~6 lần** hiệu số trước/sau (44 → 48 ms) ⇒ trên máy ảo này phép đo
**không kết luận được gì** về ngưỡng +1.5 ms của §6.2. Đây là **mở rộng** của điều `perf-profile-2026-09-16.md:212-213`
đã ghi (máy ảo đứng yên chỉ vẽ 19–26 khung): kịch bản cuộn cho đủ khung, nhưng phương sai vẫn nuốt tín hiệu.

**Lý lẽ cơ chế** (không thay phép đo, chỉ để biết chờ đợi gì): `surface()` thêm **1–2 lớp `GradientDrawable`** cho
mỗi thẻ, dựng **một lần lúc dựng View** (không dựng trong `onDraw`/nhịp trạng thái), và **0** blur / **0** shadow
layer / **0** elevation — `SurfaceMaterialContractTest` quét cả tầng `launcher/` và đỏ nếu một trong bốn thứ đó
quay lại. Con số thật phải đo **trên xe** (T14 🚗).

Tệp thô: `gfxinfo-before.txt` · `gfxinfo-after.txt`.

### 3b · Pass 4 — dụng cụ đo ỔN ĐỊNH hơn, nhưng vẫn không có "trước" để so

`gfxinfo-pass4.txt`: ba lượt liên tiếp của bản sau Pass 4, kịch bản `perf-pass4` (cuộn nhiều hơn, ≥ 629 khung
mỗi lượt) cho 90th = **21 / 23 / 20 ms**, biên độ **3 ms** — so với biên độ **26 ms** của kịch bản §3. Tức là
kịch bản mới **dùng được** làm dụng cụ, còn kịch bản cũ thì không.

Nhưng **không có lượt "trước" đo bằng chính dụng cụ này** (bản trước Pass 4 đã bị ghi đè trên máy ảo, và luật
phiên này cấm `git stash/checkout` để dựng lại), nên kết luận của §3 **giữ nguyên**: máy ảo không chốt được
ngưỡng ±1.5 ms, số thật ở T14 🚗.

**Cơ chế sau Pass 4** (để biết chờ đợi gì): mỗi bề mặt lồi nay có **3–4 lớp** thay vì 2–3 — thêm một
`GradientDrawable` tô ĐẶC làm nét đỉnh. Lớp này là một hình chữ nhật bo góc cao 1–2dp, **dựng một lần lúc dựng
View**, và vẫn **0** blur / **0** shadow / **0** elevation (`SurfaceMaterialContractTest` quét cả tầng
`launcher/`). Ô làm việc đổi từ 1 lớp sang 3–4 lớp — đó là chỗ tốn thêm nhiều nhất, và cũng là chỗ **chỉ vẽ lại
khi dựng lại ô**, không vẽ theo nhịp trạng thái 1 Hz.

## 4 · Icon app = hoa anh đào (R7) + phép đo chốt gradient (T4)

| Ảnh | Nói gì |
|---|---|
| `app-icon-settings.png` | icon ở cỡ lớn (App info, API 29) |
| `gradient-probe.png` | chính icon ấy phóng to — **thấy rõ chuyển sắc toả** hồng đậm → hồng phấn trên cánh, và chuyển sắc chéo tím than → mận ở nền |
| `app-icon-36dp.png` | **thu nhỏ đúng 36×36 px** (AC7.2 — kiểm bằng ảnh thu nhỏ, không kiểm bằng mắt trên màn lớn) |
| `app-icon-36dp-zoom.png` | ảnh 36 px phóng NEAREST ×8 để soi từng điểm ảnh |

**[ĐO] T4 — `<aapt:attr name="android:fillColor"><gradient>` render ĐÚNG trên API 29.** Thấy chuyển sắc, không phải
khối đặc ⇒ **mức tả thực (1)** của §4.8 khả thi; §4.8 nâng từ [ĐOÁN] lên [ĐO].
Phép đo chạy trên **chính icon app** thay vì một `ic_probe_gradient.xml` rời — lý do ở spec §9 mục 5.

**[ĐO] AC7.2 — lượt P1 KHÔNG đạt, Pass 4 vẽ lại hình và đạt.**
Lượt P1 tự ghi ra rằng bông hoa *"hơi ngả sang 10 cánh nhọn"* ở 36 px và đề cách chữa là *"giảm độ sâu khía ở bản
24dp"*. **Cả hai vế của câu đó đều sai**, và Pass 4 chứng minh bằng phép thử:
- ảnh 36 px là bản **108 thu nhỏ** ⇒ sửa bản 24dp (chỉ dùng cho smallIcon thông báo) không đổi nó **một điểm ảnh**;
- thủ phạm không phải độ sâu khía mà là **thung lũng giữa hai cánh quá nông** — cánh béo tới mức viền ngoài chỉ
  còn là một đường tròn có 10 vết răng cưa, và mắt đọc một đường tròn có răng là **bánh răng**. [ĐO] lượt thử đầu
  của Pass 4 làm nông khía đúng như README cũ dặn ⇒ nó ra bánh răng **rõ hơn**.

Hình nay **sinh bằng tham số** (5 cánh, R = 28 trên khung 108, nở rộng nhất ở 0.60 R với nửa bề rộng 0.42 R, đầu
cánh thắt còn 0.21 R ở 0.97 R, khía thụt về 0.84 R) ⇒ hai cánh kề nhau chỉ gặp nhau ở ~0.55 R, viền ngoài có
**năm** thung lũng sâu. [ĐO] `app-icon-36dp-zoom.png` của Pass 4: đọc ra **năm cánh + nhị vàng** ở 36 px.
Khung mực 108: x 27.94..80.06 · y 27.60..77.89 (vùng an toàn 21..87 ✅); khung mực 24: 2.79..21.21 ×
2.66..20.45, cạnh lớn nhất 18.43 (`IconGeometryContractTest` đòi 2..22 · ≥ 16 ✅) — **không bài canh nào phải nới**.

**[CHƯA VERIFY]**: hình smallIcon trong **thông báo** chưa chụp được — phiên máy ảo này không có service nào của
Kachi đang chạy nên không có thông báo để bày. Phần kiểm được đã kiểm bằng máy: tệp 24dp là **một tông `#FFFFFF`**,
khung 24×24, khung mực 2.15–21.85 (`IconStyleContractTest` + `IconGeometryContractTest` xanh) — đúng hợp đồng
smallIcon (hệ thống tô lại màu, chỉ nhận hình đơn sắc).

## 5 · Δ kích thước APK — ⚠ KHÔNG đo được ở phiên này

`app-vehicleTest.apk`: **40 978 459 B** (artefact có sẵn lúc bắt đầu phiên) → **39 837 982 B** (1.68).
Hiệu số **−1 140 477 B** **KHÔNG** phải của P1: artefact đầu có **trước** lượt ADAS-PURGE (21 tệp vector + mã đi
kèm đã xoá), nên nó đo chủ yếu cái purge.

P1 tự nó đổi **3 tệp vector** (`launcher_fg` · `launcher_bg` · `ic_launcher`) + ~200 dòng Kotlin ⇒ xa dưới trần
**+350 KB** của AC5.5, nhưng **con số chính xác phải đo lại** ở lượt build sạch sau khi purge đã commit. Ghi ra
thay vì báo một con số không truy được về nguyên nhân.

## 6 · Cách chạy lại

Kịch bản nằm trong thư mục scratch của phiên (không commit). Pass 4 dùng ba cái, và **ghi lại cách dựng ở đây**
để lần sau không phải nghĩ lại:

- **ma trận màn chính** — vòng `for t in NIGHT DAY` × `for p in ONE TWO_COL QUAD`; mỗi vòng:
  `am force-stop` → `run-as com.byd.launcher sed -i` hai khoá `Mặc định__theme_mode` / `Mặc định__preset` trong
  `shared_prefs/kachi_workspace.xml` → `monkey -c HOME` → **chờ 11 s** (toast *"Kênh điều khiển cửa sổ…"* sống
  ~9 s; chụp sớm hơn là ảnh dính toast) → `exec-out screencap -p`.
- **hai màn UI** — đường cố định: chạm `(1655,66)` mở Cài đặt → `(256,296)` nhóm *Thanh trạng thái & thanh nút*
  → chụp → 3 lần `swipe 1150 800 → 1150 200` → `(634,895)` nút *Chọn nút trên thanh…* → `swipe 960 800 → 960 520`
  → `(739,433)` bật ô *Khí hậu* → chụp → BACK ×2 (**không** bấm *Áp dụng*).
- **gfxinfo** — `perf-pass4` ở §3b.

Bảng tương phản thì **không** cần kịch bản — nó sinh ra mỗi lượt
`JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest`.

⚠ `uiautomator dump` trên AVD này trả về **cây của cửa sổ phía sau** khi bảng phủ của launcher đang mở (bảng là
một View trong cùng activity, không phải cửa sổ riêng) ⇒ tra toạ độ bằng nó sẽ ra toạ độ của màn chính. Pass 4
soi toạ độ **trên chính ảnh chụp ngay trước mỗi thao tác**, đúng như CLAUDE.md §15 dặn cho nhánh UI.
