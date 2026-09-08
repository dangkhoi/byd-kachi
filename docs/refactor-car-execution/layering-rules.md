# Quy tắc tách layer / core / feature — dùng làm checklist review

Mọi quy tắc dưới đây **đều sinh ra từ một lần nhầm thật** trong ngày 2026-07-27, có ghi nguồn. Cột cuối
nói rõ cái nào máy cưỡng chế được, cái nào còn phải tự giữ — để không ai (kể cả tôi) tưởng là đã an toàn.

## 1. Quy trình quyết định — ba câu hỏi

Đặt một file vào đâu thì hỏi đúng ba câu, theo thứ tự:

**Q1. Code này lúc chạy có cần Android hoặc thiết bị không?**
- Không → **`:core`**
- Có, và nó **gửi lệnh qua adb tới head unit** → **`:car-integration`**
- Có, và nó **gọi API Android cục bộ của app** (Context, PackageManager, DisplayManager, AtomicFile,
  View, Service, Bitmap) → **`:app`**

**Q1b. Nếu nó cần một *kiểu* của Android nhưng không cần *hành vi* nào của Android → tách port.**
Đây là dạng lỗi tinh vi nhất đã gặp: `ManeuverSignature` (226 LOC) và `ArrowClassifier` (66 LOC) là thuật
toán thuần, chỉ cần `width`, `height` và mảng ARGB, nhưng nhận `android.graphics.Bitmap` và gọi
`android.util.Log`. Theo Q1 thì "cần Android" nên được ở `:app` — hệ quả thật là **292 dòng logic chưa
từng có một test nào**. Cách chữa: khai báo cái tối thiểu ở `:core` (`PixelFrame`), để lớp bọc ở `:app`,
và biến log thành seam mặc định không làm gì.

**Phân biệt với trường hợp KHÔNG tách:** nếu code chỉ *mang* đối tượng nền tảng đi tới chỗ dùng nó thật
(`NotificationParser`, `NavState` chuyển Bitmap tới chỗ vẽ), thì port sẽ làm mất chính đối tượng cần vẽ —
để ở `:app`. Ranh giới là: **đọc thuộc tính** thì tách được, **truyền tiếp để nền tảng dùng** thì không.

**Q2. Nó là quyết định, dữ liệu, hay trình bày?**
Quyết định và dữ liệu đều thuộc `:core`. "Trình bày" nghĩa là *mô hình* hiển thị (projector) thì vẫn
`:core`; chỉ khi nó chạm View/Activity mới là `:app`.

**Q3. Nó thuộc feature nào?** cast / navigation / shared. Feature không gọi ngang feature; cần dùng chung
thì đẩy xuống *shared của layer thấp hơn*, không gọi sang cột bên cạnh.

## 2. Bảng xếp chỗ theo loại

| Loại | Chuồng | Ghi chú |
|---|---|---|
| model, enum, data class, hằng số | `:core` | kể cả hằng số đang nằm trong lớp lưu trữ Android |
| máy trạng thái, planner, journal, policy | `:core` | |
| parser output thiết bị | `:core` | parse chuỗi là logic thuần, dù chuỗi đến từ thiết bị |
| projector → mô hình hiển thị | `:core` | không chạm View |
| khai báo port (interface) | `:core` | |
| impl port dùng adb | `:car-integration` | |
| impl port dùng API Android | `:app` | |
| bảng opcode, profile đời máy | `:core` | dữ liệu, không phải hành vi |
| transport (dadb), CLI runner | `:car-integration` | |
| Activity, Service, View, drawable | `:app` | |
| ảnh/bitmap (`android.graphics`) | `:app` | |
| fixture output thật của xe | `docs/refactor-car-execution/fixtures` | được git track để test chạy được sau khi clone |
| test của code `:core` | `:core` | `internal` chỉ thấy trong cùng module |
| helper test dùng chung | `:core` testFixtures, **namespace trung lập** | không được nằm trong package của feature nào |
| layout, drawable, values (`res/`) | `:app` | tài nguyên Android, không có chỗ khác |
| tài nguyên/tham số của runner | `:core` (catalog) | lệnh gửi xuống xe là dữ liệu, không phải script |

## 3. Bảy cái bẫy đã mắc, mỗi cái thành một quy tắc

**B1. Xét theo *việc code làm*, không theo *file nó đang nằm*.**
`CastDisplayDiscovery` (81 dòng parse dumpsys) và `CastSealCommands` (bảng opcode) nằm lẫn trong
`CastAndroidRuntime.kt`, và hai parser nữa cũng vậy. Cả bốn đều 0 lần dùng Context. Nằm trong file Android
nên không ai test được mà không dựng cả runtime.

**B2. Hằng số trong lớp lưu trữ vẫn là dữ liệu.**
`Prefs.AUTO`/`PREFER_GMAPS` sống trong lớp SharedPreferences, khiến `SourceArbiter` — bộ quyết định thuần —
phải phụ thuộc Android chỉ vì hai con số. Tách thành `NavSourceMode`, giữ giá trị cũ để dữ liệu đã lưu vẫn
đọc đúng, để lại alias nên không caller nào phải đổi.

**B3. "Nói với thiết bị" là hai việc khác nhau.**
Gửi lệnh qua adb tới head unit ≠ gọi API Android cục bộ. Ban đầu tôi định dời cả 5 file thiết bị vào
`:car-integration`; đúng ra chỉ 1 file thuộc đó.

**B4. `internal` xuyên module buộc phải mở public.**
Dời code sang module khác làm ~14 khai báo phải public. Trước khi dời, hỏi: *nên dời consumer xuống, hay
dời type lên?* Mở public là chi phí thật, không phải thủ tục.

**B5. Comment nhắc tên ≠ phụ thuộc code.**
Bốn trong năm "phụ thuộc" của nhóm nav hoá ra chỉ là chữ trong comment. Luôn xem dòng thật trước khi kết
luận bao đóng.

**B6. Phép đo phải đo đúng thứ nó tuyên bố.**
Bốn lần trong một ngày phép đo của tôi đếm sai đối tượng: ratchet tên "UI coupling" quét cả `:core`;
"điểm adb ngoài car-integration" đếm cả trong car-integration; coupling đếm theo package nên gộp lớp
app-internal; bánh răng đầu đo *lời gọi* mà tôi trình bày như đo *ranh giới*. Quy tắc: đọc tên test, rồi
đọc phạm vi quét, xem có khớp.

**B7. Test quét source phải tìm được ở mọi module.**
Sau khi tách, mọi test đọc file theo đường dẫn cứng đều chết hoặc — tệ hơn — **xanh mà vô nghĩa** vì soi
thư mục không tồn tại. Dùng resolver chung.

## 3b. Bốn quy tắc thủ tục, không phải xếp chỗ

**P1. Hướng phụ thuộc chỉ đi xuống.** `:app → :car-integration → :core`, không có chiều ngược. Gradle
cưỡng chế điều này bằng cách không cấp dependency ngược — mạnh hơn mọi test.

**P2. Giai đoạn di trú chỉ dời chỗ.** Không sửa hành vi, không sửa lỗi, không tối ưu, không đổi tên vì
thấy tên xấu. Trộn vào là mất luôn ý nghĩa của câu "toàn bộ test xanh". Đổi hành vi là commit riêng.

**P3. File lai thì tách, đừng dời cả cục.** Quy trình: trích phần thuần ra file mới ở `:core`, để lại phần
Android với tên cũ, và ghi trong comment vì sao tách. Bốn khối logic thuần đã được cứu ra khỏi
`CastAndroidRuntime.kt` theo cách này.

**P4. Tên nói sai thì phải đổi, kể cả đang giữa di trú.** `CastAndroidGateway` sau khi sang module không có
Android thì cái tên thành lời nói dối; đã đổi thành `CastAdbGateway`. Đây là ngoại lệ duy nhất của P2, vì
để lại tên sai thì người đọc sau sẽ xếp chỗ sai theo.

## 4. Checklist review — chạy sau mỗi lần dời, trước mỗi commit

```bash
# 1. ba module vẫn sạch và mọi test xanh
./gradlew --offline testDebugUnitTest :core:test :car-integration:test

# 2. :core không có Android/dadb  ·  :car-integration không có Android
echo "core leak:    $(grep -rlE '^import (android|androidx|dadb)\.' core/src/main/kotlin | wc -l | tr -d ' ')  (phải 0)"
echo "car-int leak: $(grep -rlE '^import android' car-integration/src/main/kotlin | wc -l | tr -d ' ')  (phải 0)"

# 3. còn file thuần nào nằm sai chuồng trong :app không (ratchet, chỉ được giảm)
./gradlew --offline testDebugUnitTest --tests '*LayeringRulesTest*'

# 4. attestation vẫn phủ mọi module (rủi ro K2: mất chuỗi APK-khớp-source)
python3 scripts/evidence/gen-exact-source.py --out /tmp/check.json --label check
python3 -c "import json;d=json.load(open('/tmp/check.json'));
import collections;print(collections.Counter(e['path'].split('/')[0] for e in d['intendedUntracked']))"

# 5. đường dẫn chết sau khi dời
python3 - <<'PY'
import glob,re,os
dead=0; checked=0
for p in glob.glob("scripts/**/*.sh",recursive=True)+glob.glob("*/build.gradle.kts"):
    for m in re.findall(r'(?:app|core|car-integration)/src/[A-Za-z0-9_/.\-]+', open(p).read()):
        checked += 1
        if not os.path.exists(m.rstrip('."')): print("CHET:", p, m); dead += 1
print(f"duong dan kiem tra: {checked}, chet: {dead}")
PY
```

Mọi bước phải **in ra một dòng kết quả**, kể cả khi đạt. Một kiểm tra im lặng khi đạt thì không phân biệt
được với một kiểm tra không chạy — đó đúng là cách bốn assertion tĩnh của `run-cast-matrix.sh` xanh suốt
trong khi đang soi một thư mục không tồn tại.

## 5. Cái nào máy giữ, cái nào tôi phải tự giữ

| Quy tắc | Cưỡng chế bởi |
|---|---|
| `:core` không có Android/dadb | `CoreIsolationTest` (classpath) + compile |
| `:car-integration` không có Android | `LayeringRulesTest` |
| File thuần không được nằm lại `:app` | `LayeringRulesTest` (ratchet, chỉ giảm) |
| UI không chạm máy móc Cast | `CastFacadeBoundaryTest` (0) |
| Coupling UI ↔ tầng dưới chỉ được giảm | `CastArchitectureRatchetTest` |
| Mở adb chỉ trong `:car-integration` | `CastArchitectureRatchetTest` (13, chỉ giảm) |
| Navigation không import Cast | `NavigationOutputIsolationTest` |
| Vỏ shell không chứa logic thiết bị | `WrapperContractTest` |
| Transport không biết Android | `CastAdbGatewayContractTest` |
| Hướng phụ thuộc chỉ đi xuống (P1) | Gradle — không cấp dependency ngược |
| Feature không gọi ngang feature (Q3) | `LayeringRulesTest` — navigation ↮ cast trong `:core` |
| Mọi file phải thuộc một cột feature (Q3) | `LayeringRulesTest` — gốc package `:core` phải rỗng |
| Mọi file trong `:car-integration` phải dùng dadb (Q1) | `LayeringRulesTest` |
| Tên không nói sai về chuồng (P4) | `LayeringRulesTest` — không `*Android*` trong module không có Android |
| **Q2: quyết định/dữ liệu thuộc `:core`** | **chưa cưỡng chế** — phán đoán người |
| **Bảng xếp chỗ §2 (16 dòng)** | **một phần** — 8 dòng có test; phần còn lại vẫn là phán đoán. Đừng đọc bảng đó như một bảo đảm toàn phần |
| **P2 chỉ-dời-chỗ** | **chưa cưỡng chế** — phải đọc diff |
| **P3 tách file lai** | **chưa cưỡng chế** — phán đoán người |

## 6. Ghi chú về độ tin cậy của chính tài liệu này

Sau lần review B1–B5 ngày 27/7, bốn quy tắc trước đây "chưa cưỡng chế" đã thành test — và chúng thành test
**vì đã bị vi phạm thật**: 276 dòng logic thuần nằm trong module transport, 5 file navigation nằm ở gốc
package không thuộc cột nào, một lớp tên `AndroidObservedStateParser` trong module không có Android, và một
helper test nằm trong namespace của feature khác. Cả bốn chỉ lộ ra khi soi tay theo checklist. Bài học:
mỗi lần review tay bắt được lỗi thì phải hỏi ngay "cái này ghim thành test được không", vì lần sau sẽ không
ai soi.

Còn lại hai quy tắc thật sự không tự động được: P2 (chỉ-dời-chỗ, phải đọc diff) và Q2 (quyết định hay dữ
liệu, phải hiểu ý nghĩa code).

Trong 14 dòng có cưỡng chế, chỉ **4** cái là ratchet (số chỉ được giảm): coupling kiểu UI (31), coupling
vượt tầng (27), điểm mở adb ngoài transport (13), file thuần còn trong `:app` (7). Số còn lại là kiểm nhị
phân, dễ thoả mãn hình thức.

(Con số này chính tôi đã ghi sai một lần: tài liệu từng nói 3 ratchet trong khi có 4 — bỏ sót
`crossLayerCoupling`. Phát hiện khi đối chiếu tài liệu với code, không phải khi viết.) Còn 6 quy tắc **không có gì
cưỡng chế**, và kinh nghiệm một ngày cho thấy tỉ lệ tôi tự giữ đúng những thứ không được cưỡng chế là
không cao — bốn lần xếp sai chuồng, bốn lần phép đo sai đối tượng, ba lần nói "xong" khi chưa xong.

Nên cách dùng tài liệu này: chạy §4 sau **mỗi** lần dời, và với các dòng "chưa cưỡng chế" thì đừng tin
lời tôi, hãy hỏi bằng chứng.

## 6. Bổ sung 2026-07-27 — luật mới do checklist thủ công tìm ra

**Test của logic `:core` phải nằm trong `:core`.** Checklist bắt 8 file test sai chuồng, trong đó **hai
file do chính tôi viết cùng tối** (`NavigationNoDeadEndTest`, `NavigationWedgedWorkerTest`).

Hại thật sự, không phải chuyện thẩm mỹ: bộ test riêng của `:core` không phủ chính lớp của nó, nên có thể
phá `:core` mà chỉ biết khi chạy bộ test Android — và tệ hơn, một test như thế có thể lỡ phụ thuộc Android
mà không ai thấy, làm mất luôn ý nghĩa "core chạy được không cần thiết bị".

Tiêu chí máy dùng: test trong `:app` mà không import Android và không dùng khai báo nào của riêng `:app`
thì nó đang kiểm `:core`. Ngoại lệ phải **kể tên kèm lý do** — hiện có đúng một:
`BuildArtifactNamingTest` (đối tượng kiểm là build script *của* `:app`).

Sau khi dời: app 364 → 261 test, core 318 → 421, tổng vẫn 696 — không mất bài nào.

Đã kiểm ngược bằng cách dời tạm `NavParseTest` sang `:app` → luật đổ đúng chỗ và **gọi tên file**.

Bảng enforcement cập nhật: luật này giờ **máy giữ**, không còn nằm ở phần "tôi phải tự giữ".
