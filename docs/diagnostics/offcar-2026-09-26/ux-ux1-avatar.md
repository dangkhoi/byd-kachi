# UX1 · R1 — chữ đại diện hồ sơ "không nằm trọn trong vòng"

Spec: `docs/specs/kachi-274-ux-voice-camera.html` §3 R1 · lane UX1-AVATAR · off-car 2026-09-26 · bản gốc lỗi: 2.70 trên xe.

---

## 1. Nguyên nhân gốc

**Chữ KHÔNG lệch trong đĩa — ĐĨA lệch trong nút.** [ĐO ảnh owner + mã]

- [ĐO] đo pixel trên ảnh header (1920×1080, density 240 = 1,5×): nền pill `134×51px` = `89×34dp` (khớp
  `KachiBars.HEADER_BTN` = 34), đĩa gradient `33×33px` = `22dp` (khớp `KachiBars.HEADER_AVATAR` = 22), chữ bên
  trong đĩa có **khe trái = khe phải = 11px**, tâm chữ = tâm đĩa. ⇒ phần vẽ chữ đúng, **không sửa gì ở đó**.
- [ĐO mã] `KachiTopStrip.profileChip()` khai lề trong `setPadding(XS=4, 0, M=12, 0)` — 12dp bên phải là **khe dẫn
  sang chữ TÊN hồ sơ**. Chữ tên đã `View.GONE` từ 2.55 (`KachiTopStrip.kt:434`, quyết định owner 2026-09-25
  *"chỉ icon hồ sơ"*) nhưng **lề của nó không ai trả lại** ⇒ chip rộng `4 + 22 + 12 = 38dp`, đĩa chỉ 22dp nằm sát
  bên trái ⇒ **tâm đĩa lệch trái 4dp (6px @240dpi)** so với tâm nền pill. Cộng thêm `gravity` chỉ
  `CENTER_VERTICAL` (chỉ căn dọc) nên chỗ dư ngang **dồn hết về mép trái**.
- [ĐO mã] chip chỉ khai `minimumHeight`, **không** khai `minimumWidth` — khác hẳn ba pill chỉ-icon bên cạnh
  (`KachiTopStrip.kt:251` khai cả hai) ⇒ bề ngang của nút là *hệ quả của một con số còn sót*, không phải một quyết định.
- [ĐO mã] `setProfile()` đặt `contentDescription` lên **chính chữ tên đang `GONE`**. View `GONE` thì TalkBack không
  duyệt ⇒ **nút hồ sơ không có nhãn nào** kể từ 2.55. Đây là lỗi nặng hơn cái lệch 4dp (vỡ đúng bất biến mà bài canh
  `TopStripSurfaceContractTest` dựng cho nút chỉ-icon), nên được vá cùng lượt.
- [ĐO mã] `lpFor(HeaderItem.PROFILE)` cấp `marginStart = Sp.SLOT_GAP` (9dp) với lý do ghi thẳng trong chú thích:
  *"nó là vật duy nhất có CHỮ nên cần tách khỏi hàng icon"* — **cùng một di sản thời-có-chữ** như lề 12dp.
- [SUY] phần "mắt đọc ra chữ không tròn vòng": đĩa 22dp trong nút 34dp còn 6dp trống trên/dưới; lệch 4dp ngang
  trên một vật tròn đọc ra thành *"chữ không nằm giữa vòng"*. Chốt bằng ảnh máy ảo ở §4.
- [ĐO mã] chỉ **một** chỗ vẽ chữ-cái-đầu trong đĩa trong cả repo (`KachiTopStrip` ← `ProfileNames.initial`); màn
  Hồ sơ trong Cài đặt chỉ in chữ trơn, `ProfileChip.kt` (bộ chọn) không vẽ đĩa ⇒ sửa một chỗ là hết, không bản sao.

### Cơ chế framework (bắt buộc theo CLAUDE.md §3 — đã fetch source, không dựa trí nhớ)

[ĐO AOSP `android-10.0.0_r47`, `core/java/android/widget/LinearLayout.java`]

| dòng | mã | hệ quả |
|---|---|---|
| `:1325` | `mTotalLength += mPaddingLeft + mPaddingRight;` | chiều dài đo được **đã gồm** lề trong |
| `:1330` | `widthSize = Math.max(widthSize, getSuggestedMinimumWidth());` | `minimumWidth` nở nút khi nội dung hẹp hơn |
| `:1736` | `childLeft = mPaddingLeft + (right - left - mTotalLength) / 2;` | `CENTER_HORIZONTAL` chia đều chỗ DƯ |
| `:1785` | `childTop = paddingTop + ((childSpace - childHeight) / 2) …` | `CENTER_VERTICAL` cùng phép cộng theo trục dọc |

Áp số: lề `4 + 4`, đĩa `22` ⇒ `mTotalLength = 30dp`; `max(30, 34) = 34dp`; `childLeft = 4 + (34 − 30)/2 = 6dp` ⇒ đĩa
chiếm `6..28dp`, **tâm đĩa 17 = tâm nút 17**, đúng cả hai trục. Nền pill (`GradientDrawable` không `setSize`) có
`getMinimumWidth() = 0` nên `getSuggestedMinimumWidth()` = 34dp như ở ba pill đã chạy ngoài hiện trường.

---

## 2. Bản vá

Thuần tầng View — **không** lệnh `am`/`wm`/HAL, nên không chạm §4/§5; không đảo thứ tự đường nào đang chạy (§6);
không rẽ nhánh theo tên hồ sơ/gói nào (§7).

| # | Chỗ | Đổi gì | Vì sao |
|---|---|---|---|
| 1 | `KachiTopStrip.kt:417` | `gravity = Gravity.CENTER` (thay `CENTER_VERTICAL`) | chỗ dư NGANG chia đều hai bên |
| 2 | `KachiTopStrip.kt:421` | thêm `minimumWidth = dp(Bars.HEADER_BTN)` | nút chỉ-icon phải khai cả hai chiều, đúng như `pill()` |
| 3 | `KachiTopStrip.kt:448-455` | `syncProfilePad()` mới: lề cuối = `Sp.M` nếu chữ tên **còn chiếm chỗ** (`!= GONE`), ngược lại `Sp.XS` — `INVISIBLE` vẫn được đo ([ĐO AOSP `LinearLayout.java:1148`] phép đo bỏ qua ĐÚNG con `GONE`) | chip **tự ĐO** cái nó đang chứa; owner bật lại chữ tên thì lề tự về, không phải sửa lần nữa (§7) |
| 4 | `KachiTopStrip.kt:465` | `contentDescription` dời từ chữ tên `GONE` sang **chính nút** (`profileChipView`), giữ nguyên khoá chuỗi `kachi_profile_chip_desc` (VI+EN đã có) | view `GONE` không vào cây a11y ⇒ nhãn để ở đó không bao giờ đọc được; nút chỉ còn MỘT chữ cái của đĩa để đọc thay [SUY] |
| 5 | `KachiTopStrip.kt:187-193` | bỏ nhánh riêng của `HeaderItem.PROFILE` trong `lpFor` ⇒ dùng `pillLp()` (khe `Sp.S` = 8dp như ba pill) | khe 9dp là di sản thời chip còn CHỮ; nay nó là nút chỉ-icon thứ tư, khe riêng chỉ làm hàng nút lệch nhịp |
| 6 | `ProfileNames.kt:52-56` | `initial()`: **hoa TRƯỚC rồi mới cắt**, cắt theo **code point** (bắt cặp surrogate), `trim()` đầu tên | `"ßeta".take(1).uppercase()` = `"SS"` (hai chữ trong đĩa 22dp); tên mở đầu bằng emoji ⇒ nửa cặp surrogate ⇒ ô tofu. **Đây là làm cứng ca biên [P3], KHÔNG phải nguyên nhân cái lệch owner thấy** |
| 7 | KDoc `profileChip` · `HEADER_AVATAR` · `syncProfilePad` | ghi lại lượt **đảo chiều** (S4·R7 đòi VẼ tên → V5 bỏ), sửa hai câu đã sai (*"đích chạm ≥ Sp.TOUCH"* trong khi mã dùng `HEADER_BTN` 34dp; *"initial = take(1)"*) | code + doc atomic (§16 R2.1); câu KDoc sai là cái bẫy cho lượt sửa sau |

Không làm: không đổi `HEADER_AVATAR`/`HEADER_BTN` (owner đã chốt 70 % ở WP5·R5.2), không đụng cách **vẽ** đĩa/chữ,
không bỏ nền pill, không thêm khoá chuỗi mới.

`KachiTopStrip.kt` sau vá (kể cả lượt review): **487/500 dòng** (§4.1 còn 13 dòng — ai thêm việc vào tệp này phải tách theo vai trước).
`SettingsScreenWiringContractTest.kt`: **489/500** (sửa tại chỗ, không thêm bài mới vào đó).

---

## 3. Test (đã chạy, xanh)

| Bài | Tệp | Khoá cái gì |
|---|---|---|
| `dia ho so dong tam voi nut va ten van doc duoc` (mới) | `app/…/TopStripSurfaceContractTest.kt` | nút khai cả hai chiều; `Gravity.CENTER`; **không còn** `CENTER_VERTICAL`; **không** viết cứng lề `(XS,0,M,0)`; `syncProfilePad` phải ĐỌC `profileNameView.visibility`; nhãn TalkBack nằm trên **nút**, không trên view `GONE`; và (lượt review) hai lề của ca chỉ-đĩa phải là CÙNG hằng `Sp.XS` — lệch `d` thì tâm đĩa lệch `d/2` |
| `dia ho so va le doi xung nam trong dich cham` (mới) | `app/…/BarOrderWiringContractTest.kt` | số học: `HEADER_AVATAR + 2×XS = 30 ≤ HEADER_BTN = 34` ⇒ nâng đĩa/nới lề mà quên đích chạm là đỏ ngay |
| `chu dau chi MOT ky tu du va hoa truoc khi cat` (mới) | `core/…/ProfileNamesTest.kt` | `initial("ßeta") == "S"`; emoji ra **một code point đủ** (2 `Char`); `"  test"` → `"T"`; `""` không ném |
| `chu dau avatar theo nhan da dich` (mở rộng) | `core/…/ProfileNamesTest.kt` | thêm `"Đi làm"` → `"Đ"` (chữ có dấu vẫn MỘT ký tự) |
| `chip ho so con chạm-de-doi nhung het giu-de-tao` (**sửa tại chỗ**) | `app/…/SettingsScreenWiringContractTest.kt` | bài cũ đòi `profileChip()` còn chuỗi `profileNameView` với lý do *"chip phải hiện TÊN"* — quyết định đó đã bị owner đảo 2026-09-25, bài **xanh nhờ chuỗi còn sót chứ không nhờ hành vi** (§10). Nay đòi đúng cái còn thật: tên hồ sơ phải còn dưới dạng nhãn TalkBack trên nút |

**Kết quả** (đếm từ `build/test-results/**/*.xml`, không tin console):

- `:core:test` — `ProfileNamesTest` 6/6 · `ProfileNamesSummaryTest` 5/5 · `TopStripTest` 26/26 → 37 xanh, 0 đỏ.
- `:app:testDebugUnitTest` — `TopStripSurfaceContractTest` 9 · `TopStripWiringContractTest` 9 ·
  `SettingsScreenWiringContractTest` 21 · `BarOrderWiringContractTest` 9 · `SpacingScaleContractTest` 10 ·
  `ControlHeightContractTest` 4 · `LauncherI18nContractTest` 11 · `LauncherActionTileWiringContractTest` 6 ·
  `OpenAppWiringContractTest` 12 · `ProfileLayoutLinkContractTest` 5 · `TypeScaleContractTest` 3 ·
  `ZeroBorderContractTest` 5 → **104 xanh, 0 đỏ**.
- **Kiểm ngược (mutation)**: trả mã về đúng hình dạng cũ (bỏ `minimumWidth`, `CENTER_VERTICAL`, lề `(XS,0,M,0)`,
  `contentDescription` trên view `GONE`) ⇒ hai bài mới/đã sửa **ĐỎ** đúng chỗ, rồi phục hồi và xanh lại. Bài canh
  bắt được lỗi thật, không phải bài luôn-xanh.
- §8: `syncProfilePad` có **2** call site production (`profileChip()` và `setProfile()`) — đã grep, không phải hàm mồ côi.

---

## 4. Lượt kiểm THỊ GIÁC còn nợ (máy ảo, rồi xe) — [CHƯA BIẾT]

Hình học ở đây chốt bằng **số học + source AOSP**, chưa có ảnh của bản đã vá. Lượt visual pass phải kiểm đúng
những điểm sau (đo bằng máy, **không** bằng mắt: lọc pixel nền pill và pixel đĩa accent ở góc trên-trái):

1. **Nền pill chip hồ sơ = 51×51px** (34×34dp @1,5×) — trước vá là **57×51px**. Nếu ảnh "trước" không ra 57×51 với
   khe trái 6px / khe phải 18px thì **chẩn đoán sai, dừng bản vá** (đây là phép thử phủ định của §1).
2. **Khe trái = khe phải (±1px)** và `|tâm đĩa − tâm nền pill| ≤ 1px` theo **cả hai** trục.
3. Chữ trong đĩa vẫn khe trái = khe phải (±1px) — bản vá **không** được làm xê dịch phần vẽ chữ.
4. Ba hồ sơ khác nhau: mặc định (`M`/`D` theo ngôn ngữ), tên tiếng Việt có dấu (`Đi làm` ⇒ `Đ`), tên do người dùng
   đặt bắt đầu bằng chữ thường (⇒ phải ra chữ HOA).
5. **Hàng nút trên header**: chip hồ sơ nay cách nút bên cạnh `Sp.S` (8dp) thay vì 9dp ⇒ xem 4 nút có đọc ra một
   nhịp đều; và `fitChips()` được trả thêm 4dp bề ngang ⇒ xem không chip nào đổi từ đủ-chữ sang `…` hay ngược lại.
6. **TalkBack**: bật đọc màn, chạm nút hồ sơ ⇒ phải đọc *"Hồ sơ đang dùng: <tên>. Chạm để đổi hồ sơ."* (VI) /
   *"Current profile: …"* (EN). Trước vá: **không đọc gì**.
7. Ảnh trước/sau lưu vào thư mục phiên và **trỏ từ `docs/README.md`** (§16 R2.1 — doc mồ côi = không tồn tại).
   ⚠ Cắt **chỉ dải header** trước khi commit: `.gitignore:127-128` bỏ cả thư mục ảnh máy ảo vì ảnh full-screen có
   render xe (quét bảo mật), nên ảnh nguyên màn sẽ bị bỏ lại lần nữa.

## 5. Ghi chú cho điều phối

- Chưa bump version, chưa commit (đúng luật phân làn của phiên này). Bản vá này thuộc 2.74.
- Số đếm test ở §3 lấy từ **hai lượt chạy xanh** (cả hai sau khi phục hồi mã từ lượt mutation). Lượt gộp cuối
  **không chạy được** vì một tệp của làn khác đang dở: `core/…/voice/VoiceCommandCatalog.kt:316` có KDoc chứa
  chuỗi `*"song"*/*"music"*` — dãy `*/` đóng khối chú thích sớm ⇒ `:core:compileKotlin` đỏ cho **mọi** làn.
  Không phải tệp của làn này nên không sửa; xem `for_coordinator`.
- Ảnh header dùng làm mốc [ĐO] ở §1 nằm trong thư mục ảnh máy ảo **đang bị `.gitignore` bỏ** (`.gitignore:128`) và
  không pin vào commit nào ⇒ trích dẫn nó là *bằng chứng đã đo lại được trong phiên*, không phải artifact truy ra
  được từ repo. Số đo đã được đo lại độc lập hai lượt (scout + skeptic) và khớp `34/22dp` trên **cả hai** trục.
