# ClusterNav — Rule bắt buộc cho mọi phiên làm việc

> Viết ngày 2026-07-21, sau một phiên làm nhanh-ẩu: nhiều bản vá phải gỡ lại, có bản gây đơ launcher trên
> xe thật, có bản suýt ghim GPS toàn hệ thống. **Mọi rule dưới đây đều sinh ra từ một lỗi CÓ THẬT trong phiên đó**,
> không phải lý thuyết. Đọc trước khi sửa dòng code đầu tiên.

Đây là app chạy trên **xe đang lăn bánh ngoài đường**. Một regression không phải là bug — nó là một người
đang lái phải dừng xe khởi động lại đầu máy. Ưu tiên: **đúng > an toàn > nhanh**. Không có ngoại lệ vì "gấp".

---

## 1. Trước khi sửa: phải có spec

Repo này có `docs/specs/`. Task mới hoặc thay đổi lớn → **viết spec trước, user duyệt rồi mới code**
(xem rule global §1). Phiên 21/07 bỏ qua bước này vì "đang gấp" và trả giá bằng 3 vòng vá-rồi-gỡ.

Vá nóng < 20 dòng, một file, không đổi hành vi → được bỏ spec. Còn lại thì không.

---

## 2. Phân biệt CƠ CHẾ và QUY KẾT — không được trộn

Sai lầm điển hình phiên 21/07: chứng minh được *"`wm overscan` không đổi khung cửa sổ với app khai
`FLAG_LAYOUT_IN_OVERSCAN`"* (đúng, có source), rồi phát biểu luôn *"Android Auto chính là loại đó"* (chưa
có một mẩu bằng chứng nào — grep cả 4 file dump không có chữ `androidauto` nào).

Khi báo cáo, **luôn tách ba mức**:

| Mức | Nghĩa | Được phép nói |
|---|---|---|
| Đã chứng minh | Đọc source AOSP / dump thật | "là như thế" |
| Nhiều khả năng | Suy luận khớp hiện tượng, chưa có dữ liệu trực tiếp | "nghi là", kèm cách chốt |
| Giả thuyết | Mới chỉ hợp lý | "đoán", nêu rõ chưa kiểm |

Không có dữ liệu thì nói **"chưa biết"** và nêu đúng một lệnh/quan sát để chốt. Đoán mò rồi ship là cách
nhanh nhất để sửa nhầm bệnh.

---

## 3. Framework Android: đọc source TRƯỚC khi ship, không dựa trí nhớ

Ba bản vá phải gỡ trong phiên 21/07 đều vì tin trí nhớ về AOSP:

- `MockLoc.pause()` = `setTestProviderEnabled(false)` để "tạm ngưng mà không gỡ" → **sai**: `addTestProvider`
  gỡ provider GPS thật khỏi `mProviders`, **chỉ `removeTestProvider` mới lắp lại**. Nếu ship, COLD_SEED
  (được miễn failsafe) sẽ ghim GPS của cả xe ở một toạ độ đóng băng suốt chuyến.
- Cổng `sats >= 4` để "bỏ qua peek khi còn trong hầm" → **tự khoá vĩnh viễn**: `addTestProvider` đã
  `native_stop()` GNSS nên `sats` đóng băng, mà chỉ peek mới bật lại được engine. Phụ thuộc vòng tròn.
- Tầng `am stack resize` → chết ở `TaskRecord.resolveOverrideConfiguration` (`computeFullscreenBounds()`
  mở đầu bằng `outBounds.setEmpty()`), lại còn kèm `am task resizeable` sửa vĩnh viễn task của app khác.

**Rule:** mọi khẳng định về hành vi framework phải fetch source `android-10.0.0_r47` (và DL5 = Android 12)
rồi trích dẫn `file:line`. Chưa fetch thì chưa được ship.

**Rule đi kèm:** không bao giờ gate một đường phục hồi bằng dữ liệu mà chỉ chính đường đó mới làm mới được.

---

## 4. Lệnh đổi state hệ thống phải có phạm vi TƯỜNG MINH

Lỗi đơ Dudu launcher: `stop()` bê **mọi** stack có `displayId >= 1` về display 0 — không khớp đúng display,
không lọc loại stack, không lọc app. Kéo nhầm stack `home`/`pinned` là `addStackReferenceIfNeeded` ném
exception **sau khi** `removeFromDisplay()` đã chạy → stack mồ côi, launcher không bao giờ được resume,
`am stack list` cũng không thấy → **chỉ còn khởi động lại đầu xe**.

Trước mỗi lệnh `am`/`wm`/`service call`, trả lời được cả bốn:

1. Nhắm đúng **display nào**? (`vd < 1` → không làm gì, không bao giờ quét mù)
2. Nhắm đúng **app nào**? (allow-list, không phải "mọi thứ trừ…")
3. Nhắm đúng **loại stack nào**? (chỉ `standard`; `home`/`recents`/`pinned` là vùng cấm)
4. **Hoàn tác kiểu gì** nếu nửa chừng hỏng?

Không trả lời được câu nào thì chưa được viết lệnh đó.

---

## 5. State đổi ngoài hệ thống thì SỐNG DAI hơn tiến trình

`casting` / `lastDisplayId` nằm trong RAM, chết theo process. Còn `wm density` / `wm overscan` / `wm size`
được WM ghi vào `/data/system/display_settings.xml` theo `uniqueId` của display — **sống qua cả reboot**.
App-op PIP, animation scale, chế độ AutoContainer cũng vậy.

- Mỗi thứ đổi ra ngoài phải có **đường trả lại**, và đường đó phải chạy được cả khi tiến trình đã chết
  (→ ghi marker vào prefs *trước* khi đổi, dọn lúc khởi động).
- **Cấm** quyết định bằng cờ RAM. Kiểm bằng sự thật (`am stack list`, `dumpsys`). Cờ chỉ để hiển thị.
- Guard cứng đặt ở tầng **thi hành** (`applyBounds` từ chối task không đúng VD), không đặt ở tầng UI.

---

## 6. Không đảo thứ tự đường đã chạy tốt ngoài hiện trường

`wm size` được thêm vào và đặt **trước** `wm overscan` — trong khi overscan đang chạy tốt cho CarPlay và
Vietmap. Suýt làm hỏng hai app đang ổn để chữa cho một app.

**Rule:** đường mới **luôn xuống cuối**, và phải **tự đo** xem đường cũ có thật sự hụt không rồi mới leo
(`overscanVerified` đọc lại khung cửa sổ thật). Không hardcode tên gói để rẽ nhánh — để code tự đo và tự chọn.

---

## 7. Generic, không case-by-case

Không `if (pkg == "com.byd.androidauto")`. Khác biệt giữa các app phải lộ ra qua **đo đạc** (app có tôn
trọng inset không? task có ở trên VD không? freeform sống chưa?) rồi rẽ nhánh theo kết quả đo.

Khác biệt giữa các **đời xe** phải nằm trong `ClusterProfile` (tên service, chuỗi lệnh, gợi ý tên VD, kích
cụm), không rải rác trong code. DiLink5 dùng `auto_container` còn DL2/3/4 dùng `AutoContainer` — hardcode
một cái là dòng xe kia câm lặng, không báo lỗi gì.

---

## 8. Sau khi sửa: kiểm HÀM MỚI CÓ ĐƯỢC GỌI KHÔNG

`CastShell.evictVd` viết cẩn thận, có KDoc, compile sạch — và **chưa từng được gọi lần nào**, vì lần viết lại
`applyBounds` theo dải dòng đã nuốt mất call site. Compile xanh không có nghĩa là code chạy.

Sau mỗi lần sửa lớn (đặc biệt là thay theo dải dòng / regex):

```bash
grep -rn "<tênHàmMới>" app/src/main/java/    # phải thấy ÍT NHẤT 1 call site ngoài định nghĩa
```

Và ưu tiên `Edit` với chuỗi khớp chính xác hơn là thay theo số dòng.

---

## 9. Phiên bản: mỗi bản build đã báo cho user = một số hiệu riêng

Phiên 21/07 có **ba bản nội dung khác nhau cùng tên "v0.37"**, cộng thêm một lần đoán nhầm xe đang chạy
0.35 (thực tế 0.36) làm cả buổi chẩn đoán đi sai hướng.

- Sửa code sau khi đã báo APK cho user → **bump versionCode + versionName**, không tái dùng số cũ.
- **Không bao giờ đoán** xe đang chạy bản nào. Đọc từ máy (`dumpsys package … versionName`) hoặc hỏi.
- Version phải hiện trong app (tiêu đề màn Chiếu), trong log phiên, và trong tên file log.

---

## 10. Test là thứ khoá lại bài học, không phải thủ tục

Mỗi lỗi hiện trường đã root-cause → **một test hồi quy** dựng từ dump thật, kèm comment nói rõ nó khoá cái gì.
Xem `StackParseTest.evictableOnVd*` (khoá lỗi đơ launcher), `AppScaleTest.nudgeRect cham san*` (khoá lỗi trôi khung).

Parser (`StackParse`, `DisplayParse`, `AppScale`, `ClusterProfile`) là code **thuần**, test off-device được —
mọi hành vi phụ thuộc chuỗi output của `dumpsys`/`am` phải có fixture lấy nguyên văn từ
`docs/diagnostics/carlog-*/`.

---

## 11. Lấy log từ xe: app tự chụp, không bắt user gõ adb

Cắm CarPlay/Android Auto là đầu xe **tắt WiFi** → adb từ ngoài không vào được, mà đó đúng là lúc cần dữ liệu.
Nhưng app chạy **trên** đầu xe và nối dadb qua `localhost:5555` (loopback, không cần mạng).

⇒ Cần dữ liệu gì thì thêm vào `ClusterDiag`, để app tự chụp và ghi file. Màn `DiagActivity` gom hết phần kỹ
thuật — anh em chỉ cần **chụp màn hình gửi về**. Không hướng dẫn user gõ lệnh.

---

## 12. Nguồn RE có sẵn trong workspace — dùng trước khi đoán

Đừng nói "không tìm được thông tin" trước khi đọc những thứ này (`../` từ repo):

| Thư mục | Nội dung |
|---|---|
| `jadx-dashcast/` `dashcast-src/` | DashCast v1.5.4 đã decompile + source + CHANGELOG rất chi tiết (có log field-test thật theo từng đời DiLink) |
| `jadx-openbyd/` `jadx-openbyd24/` | OpenBYD |
| `jadx-amap/` `jadx-amap2/` `jadx-tmap/` `jadx-kim/` | app nav OEM |
| `firmware/` `BYDUpdatePackage/` `byd-fw-scratch/` | firmware BYD |
| `apks/` | DashCast, ClusterDemo, AmapService, BydAutoTMap… |
| `docs/diagnostics/carlog-*/` | dump thật lấy từ xe |

`dashcast-src/CHANGELOG.md` đặc biệt giá trị: ghi lại kết quả test THẬT trên từng đời DiLink, kèm lệnh và
kết luận (vd: ROM DL5 cắt bỏ `cmd activity set-task-windowing-mode`; `cmd activity task resize` trả exit 0
mà không có tác dụng).

---

## 13. Quy trình mỗi lần chạm code

1. Đọc rule này + spec liên quan trong `docs/specs/`.
2. Root-cause tới tận source, ghi rõ mức bằng chứng.
3. Sửa **generic** (đo đạc, không hardcode tên gói; khác biệt đời xe vào `ClusterProfile`).
4. Thêm test hồi quy.
5. `./gradlew clean assembleRelease testDebugUnitTest` (JAVA_HOME=`/opt/homebrew/opt/openjdk@17`; nhớ trả
   `local.properties` về `sdk.dir` Windows sau khi build — xem memory `clusternav-build-on-mac`).
6. Grep xem hàm mới có call site chưa.
7. Bump version nếu đã từng báo APK cho user.
8. Senior review (Opus, rule global §5) + security scan trước commit (§6).
9. Ghi phát hiện vào `docs/diagnostics/` và cập nhật spec.

---

## 14. Tính năng mới: chứng minh bằng shell thô trên xe thật TRƯỚC, nối dây theo tầng SAU

Viết ngày 2026-07-28, sau phiên refactor V2 suýt lặp lại đúng cái bệnh mà refactor này sinh ra để chữa.
Hai lần trong CÙNG một phiên, một kết luận được dựng lên từ **đọc code/comment cũ hoặc dữ liệu đã lưu trữ**
thay vì từ một phép đo thật trên xe đang chạy — và cả hai lần đều sai:

- Đọc comment cũ của V1 về "cần T3/⊞ escalation cho Android Auto", rồi từ đó suy ra V2 có thể đang thiếu
  cơ chế tương đương cho AA — **chưa hề chạy AA thật trên xe để kiểm**. User sửa thẳng: *"kết luận của AA
  không đúng, nó là hệ quả 1 bug khác... AA và CP đều lên ở chế độ thường trước đó very well"*. Nếu không
  bị chặn lại, kết luận sai này đã đủ để định hướng sai một vòng thiết kế/implement.
- Báo với một sub-agent rằng vài view ID (`txt_lane_status`, `cb_lane`…) thấy trong dump cũ 2026-07-25 là
  "view ID đã biết của VietMap" — **chưa verify lại**. Sự thật (bị agent khác vạch ra): đó là widget màn
  debug của chính ClusterNav (`activity_main.xml`), dump script bị lỗi tự chụp nhầm app tiền cảnh của mình.

Cả hai đều là biến thể của cùng một lỗi: coi trí nhớ/tài liệu cũ như bằng chứng hiện tại, thay vì tách rời
theo mức bằng chứng như §2 đã quy định — dữ liệu cũ tối đa chỉ được ở mức "nghi là", không bao giờ được
thăng lên "đã chứng minh" mà không có phép đo mới.

**Rule — mọi tính năng/tín hiệu/tích hợp MỚI (không phải sửa lại hành vi đã nối dây sẵn) phải đi đúng 4
tầng theo thứ tự, tầng sau chỉ được bắt đầu khi tầng trước đã xanh với bằng chứng THẬT, MỚI:**

1. **Shell/adb thô trên xe thật** — chạy lệnh trực tiếp qua `adb`/`dadb` (loopback `<vehicle-ip>:5555`
   hoặc qua `ClusterDiag`, xem §11), đọc output thật, lưu lại làm evidence trong `docs/diagnostics/`.
   Dump/log cũ (vd thư mục `oncar-signals-*` 2026-07-25) chỉ dùng để **định hướng** cần đo gì tiếp theo,
   KHÔNG được dùng thay cho phép đo mới — dữ liệu cũ có thể đã lỗi thời hoặc bị nhiễm bởi lỗi tooling
   (như vụ view ID ở trên).
2. **`car-integration`** — mã hoá đúng cơ chế vừa chứng minh được ở bước 1 (transport/shell-command layer),
   kèm test khoá đúng chuỗi lệnh/parse thật đã quan sát (xem pattern E2E command-log ở
   `car-integration/src/test/kotlin/.../transport/`).
3. **`core`** — nối policy/logic thuần Kotlin lên trên cơ chế đã mã hoá ở bước 2, test off-device.
4. **`app`/UI** — chỉ hiện ra cho user SAU KHI 1–3 đã xanh.

Không được nhảy cóc: không viết code `core` hay UI cho một khả năng chưa có bằng chứng shell thật ở bước 1.
Không được dùng kết luận suy luận từ code/comment cũ (kể cả code V1 tại §12) làm căn cứ triển khai — nó chỉ
được dùng để gợi ý hướng đo, xem thêm §2 (phân biệt cơ chế và quy kết) và §3 (đọc source thật trước khi
ship). Nếu chưa đo được trên xe thật, trạng thái đúng là **"chưa biết"**, không phải "chắc là".

---

## 15. Có adb + source trong tay thì debug bằng dữ liệu, không dò UI như user thường

Viết ngày 2026-07-29, sau khi user chặn thẳng giữa phiên: *"mò gì vậy, sao phải mở app lòng vòng mà ko tự
debug được? đang làm việc theo cách rất mất thời gian, trong khi mình làm ra app, connect sẵn vào adb xe, mà
cứ làm như user mò mò hên xui, chả hiểu được"*.

Bối cảnh: cast bị khoá (AA/CarPlay/VietMap giống hệt nhau), và thay vì lần thẳng qua source để biết chính
xác field nào khoá, phiên đó đi chụp screenshot → đoán toạ độ nút → tap → chụp lại → lệch cuộn → tap nhầm
hàng app khác → lặp lại — hơn chục vòng, tốn hàng chục phút, có lúc còn suýt bấm nhầm vào một dialog không
liên quan (`anddea.youtube.music`). Trong khi đó, app đã build **debuggable cùng chữ ký** (`adb install -r`
không mất data — xem kỹ thuật ở `docs/diagnostics/next-car-session-plan-2026-07-29.md §8.7`) và toàn bộ
source (`core`, `app`) đã có sẵn để đọc. Không có lý do gì phải mò như một end-user không biết code chạy
thế nào.

**Thứ tự ưu tiên bắt buộc khi debug một hành vi lạ trên xe** (dừng ở bước sớm nhất giải quyết được, không
nhảy thẳng xuống dưới):

1. **Đọc source trước** — grep đúng điều kiện gate (`if`/`when`/`return` quyết định hành vi), lần từ nơi
   hiển thị triệu chứng (VD `ClusterCastActivity.kt:169`) ngược lên tới field/state gốc. Việc này KHÔNG cần
   xe, làm được ngay khi chưa connect.
2. **Đọc state bền trực tiếp** — nếu định dạng đã biết (VD `session.env`, xem `CastSessionStore.kt`), dùng
   `adb shell run-as <pkg> cat <path>` đọc thẳng, so với field/nhánh vừa đọc ở bước 1. Đây là dữ liệu THẬT,
   không suy diễn — và nhanh hơn UI hàng chục lần.
3. **`logcat`/lệnh đo trực tiếp** (`dumpsys`, `am stack list`, `settings get`…) khi state không nằm trong
   file bền mà chỉ tồn tại lúc runtime (RAM, log). Đây cũng là dữ liệu THẬT.
4. **UI (screenshot + tap) là phương án CUỐI CÙNG** — chỉ dùng khi cả 3 bước trên không chạm tới được (state
   chỉ nằm trong field RAM của một Activity, không log, không bền), hoặc khi mục tiêu chính là xác nhận
   *trải nghiệm người dùng thật* (không phải xác nhận cơ chế). Kể cả khi phải dùng UI: chụp **một** ảnh
   ngay trước mỗi thao tác (không tái dùng ảnh cũ để đoán toạ độ), xác định phần tử cần bấm rõ ràng trên ảnh
   đó rồi mới tap — không cuộn-tap-chụp-đoán liên tiếp nhiều vòng hy vọng trúng.

**Vì sao thứ tự này, không phải "cứ thử UI trước cho giống thật"**: mục tiêu của debug là tìm ĐÚNG nguyên
nhân nhanh nhất, không phải mô phỏng lại đúng thao tác user. Việc "làm như user" chỉ có giá trị ở bước
verify cuối (xác nhận sau khi đã hiểu/sửa xong), không phải ở bước điều tra. Priority: 1 → 2 → 3 luôn rẻ
hơn, chính xác hơn, và cho câu trả lời có **file:line** trích dẫn được — đúng tinh thần §2/§3 của tài liệu
này — thay vì một chuỗi ảnh chụp không ai trace lại được vì sao kết luận.

---

## 16. Luật bền nằm ở `.kiro/steering/` — BẮT BUỘC đọc, không phải tham khảo

Viết ngày 2026-08-23. Repo có sẵn 5 file luật ở `.kiro/steering/` từ 08-19, nhưng **CLAUDE.md không hề trỏ
tới chúng** ⇒ mỗi phiên Claude Code chỉ nạp file này và bỏ qua toàn bộ luật kia. Owner phải hỏi
*"xem rule dự án trong .kiro/steering, mang các rule qua"* mới lộ ra. Đó là lỗi index, không phải lỗi luật.

**Thứ tự nạp đầu mỗi phiên** (cả 5, không chọn lọc):

| File | Nội dung | Ràng buộc cứng nhất |
|---|---|---|
| `.kiro/steering/project-context.md` | Tóm tắt luôn-bật: kiến trúc 2 nhánh · bản đồ nguồn nav · HAL/CAN facts · map file | Đọc TRƯỚC khi grep code — tránh đọc lại toàn repo |
| `.kiro/steering/documentation-and-backlog.md` | Kỷ luật doc + backlog + taxonomy 9 loại | `docs/README.md` = INDEX canonical · `docs/PROJECT-BACKLOG.md` = nguồn task DUY NHẤT · code+doc **atomic** cùng commit |
| `.kiro/steering/product-team-workflow.md` | PO → UX → Senior Dev → Dev → QA → Senior Review | Compile pass ≠ done; phải trace E2E từ UI tap → shell → kết quả |
| `.kiro/steering/trace-den-tan-cung.md` | Cấm bỏ cuộc sớm | **Cấm** "A+B đủ rồi, bỏ C". Bỏ tính năng = quyết định của OWNER. "Không thể" phải kèm bằng chứng + điều kiện mở khoá |
| `.kiro/steering/conversation-protocol.md` | Cách nói chuyện + báo cáo: khoa học · scope · progress · status · quality | Gắn **[ĐO]/[SUY]/[ĐOÁN]/[CHƯA BIẾT]** cho mọi khẳng định · cấm làm văn · "xong" phải qua checklist P5 |

**Ba điều rút ra đặt thẳng vào quy trình §13:**

- **§13 bước 0 (mới)**: đọc `project-context.md` + `docs/README.md` + `docs/PROJECT-BACKLOG.md` trước khi
  chạm dòng code đầu tiên.
- **§13 bước 9 (siết)**: "ghi phát hiện vào `docs/diagnostics/`" là **chưa đủ** — phải update **INDEX +
  BACKLOG + project-context** trong CÙNG phiên (R2.1). Doc mồ côi (không có trong index) = không tồn tại.
- **Mọi câu trả lời** đi qua checklist cuối `conversation-protocol.md`, kể cả trả lời ngắn.

Luật ở `.kiro/steering/` và luật ở file này **cùng hiệu lực**. Mâu thuẫn thì lấy cái **nghiêm hơn**, và ghi
lại mâu thuẫn đó vào backlog để owner chốt — không tự chọn cái dễ.
