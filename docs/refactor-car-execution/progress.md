# Refactor Car-Execution — sổ tiến độ

Quy ước: một dòng cho mỗi batch, chỉ ghi khi exit gate đã xanh, kèm bằng chứng cụ thể.
Batch nào chưa xanh thì không được ghi là xong.

| Batch | Nội dung | Trạng thái | Bằng chứng |
|---|---|---|---|
| S0.0 | Thu hồi nhánh "nhận giao dịch treo là thành công" | ✅ 2026-07-27 11:00 | `allowRecovering` = 0 lần xuất hiện; app 562 test xanh |
| B0 | Dựng `:core` trống, nối `:app → :core`, test cô lập classpath | ✅ 2026-07-27 11:16 | `:core` 2 test xanh; app 562 test xanh; 3 file `core/` có trong manifest attestation; `verifyExactSourceIdentity` xanh |
| B1 | Dời nhóm dữ liệu (+ CastAutomation, bắt buộc theo bao đóng) | ✅ 2026-07-27 11:38 | 5 file / 843 LOC trong `:core`; app 562 test xanh, core 2 test xanh; 3 chỗ smart-cast đã bind cục bộ |
| B2 | Port `AtomicBytes` (chính là `Journal` trong spec, đã tồn tại) + tách `CastSessionStore` | ✅ 2026-07-27 | codec+logic sang `:core`, `AndroidAtomicBytes` ở lại `:app`; constructor nhận `File` đã bỏ khỏi core; 1 call site production đảo phụ thuộc |
| B3 | Dời parser + port (`ShellGateway`, `ObservedStateReader`, `CastAmStackParser`, `CastOperationLog`) | ✅ 2026-07-27 | compile sạch ngay, không cần sửa gì |
| B4 | Dời nhóm quyết định (8 file) + projector (5 file) | ✅ 2026-07-27 | 31 file test chuyển sang `:core`; ~10 khai báo mở visibility; `SourceRoots` dùng chung qua test-fixtures |
| B5 | Tạo `:car-integration`; façade; UI thôi chạm máy móc | ✅ 2026-07-27 12:45 | `:car-integration` có `CastPlacementCommands`; `CastFacade` + ratchet test; ratchet 5 → **0**; nhưng review đối kháng cho thấy coupling kiểu vẫn 42 — xem Pass 4 trong spec |
| B6 | `main()` CLI runner | ✅ 2026-07-27 13:20 | chạy `observe` trên thiết bị thật qua adb **không cần APK**: nối trong 2.315 ms, parse bằng `:core`, trả `Unknown(expected exactly one named cluster display)` — đúng vì emulator không có cụm fission. Không có xe thì fail trung thực trong 15 ms, không treo. `:car-integration` từ 0 → 7 test |
| S0 | Giải Q1 — observable cho "cụm đang hiện app" | ⬜ chờ xe | cần dump đầy đủ ở trạng thái chiếu đang mở |

## Ghi chú môi trường

- Plugin marker `org.jetbrains.kotlin.jvm` KHÔNG có trong cache offline của máy build. Kotlin JVM cho
  `:core` được cấp qua `buildscript classpath` bằng artifact thật `kotlin-gradle-plugin:1.9.24`, cộng
  `plugins { java-library }` để có accessor kiểu. Nhờ vậy mọi lệnh Gradle vẫn chạy `--offline`.
- Mọi lệnh Gradle dùng `JAVA_HOME=/opt/homebrew/opt/openjdk@17` và `--offline`.

## Luật của giai đoạn di trú

Chỉ dời file và đổi package. Không sửa hành vi, không sửa lỗi, không đổi tên public API. Mọi thay đổi
hành vi là commit riêng sau khi di trú xong.

## Bài học từ B1 — ảnh hưởng các batch còn lại

**1. Phải tính bao đóng phụ thuộc trước khi dời, không dời theo nhóm chức năng.**
Kiểm ban đầu của tôi chỉ hỏi "file này có đụng 6 file thiết bị không" và trả lời là không, nên tưởng
dời được 4 file. Nhưng `CastModels` tham chiếu `PendingCastIntent`, `AutomationConfig`,
`BootAutomationRequest`, `AutomationOutcome`, `CastIntentOrigin` — tất cả định nghĩa trong
`CastAutomation.kt`. Phải dời kèm, thành 5 file. Từ B2 trở đi: tính bao đóng bằng cách compile thử,
không bằng cách đọc tên nhóm.

**2. Kotlin không smart-cast thuộc tính khai báo ở module khác.**
Sau khi `CastModels` sang `:core`, ba chỗ trong `:app` hết compile được:
`CastPlanner` (`observed.target`, `intent.geometry`) và `CastUiRenderer` (`state.unavailableReason`).
Cách sửa là bind vào biến cục bộ — cùng phép kiểm, cùng giá trị, không đổi hành vi. Các batch sau sẽ
gặp nhiều chỗ như vậy hơn khi nhóm quyết định (B4) đi qua ranh giới; đó là công việc cơ học, compiler
chỉ ra hết, nhưng phải đếm vào chi phí.

**3. Test quét source phải biết cả hai module.**
`CastFieldParityTest.source()` đọc theo `app/src/...` nên chết ngay khi `CastGeometry.kt` sang `core/`.
Đã sửa thành thử cả bốn gốc, có cả chuyển `main/java` → `main/kotlin`. Còn 7 file test khác dùng kiểu
đọc source tương tự — sẽ hỏng dần theo từng batch, cần sửa cùng cách.

## Trạng thái B5 chi tiết (2026-07-27 12:30)

Đã xong:
- `:car-integration` (JVM thuần, dadb) nhận `CastPlacementCommands`. Lằn ranh **thu hẹp so với spec**:
  chỉ code nói với head unit **qua adb** vào module này; code dùng API Android cục bộ (PackageManager,
  DisplayManager, AtomicFile, broadcast) ở lại `:app` vì nó không phải car-execution. 5 file Android ở
  lại là có chủ ý, không phải làm dở.
- Tách thêm hai thứ thuần nằm lẫn trong file Android: `CastDisplayDiscovery` (81 dòng parse dumpsys, giờ
  test được off-car) và `CastSealCommands` (bảng opcode). Hardcode `AutoContainer` **cố ý giữ nguyên** —
  đó là Q5, sửa ở giai đoạn sau.
- `CastFacade` + `CastFacadeBoundaryTest` dạng bánh răng một chiều. Số file chạm máy móc: 5 → 1.
  DiagActivity, CastActivityRefresh, FloatingBubbleService, CastAutomationService đã đi qua façade.

Còn lại để B5 clean:
- `ClusterCastActivity` còn ~35 chỗ gọi `runtime.coordinator/store/gateway`, cần thêm `plan`, `execute`,
  `initialize`, `applyRollout`, `queueLatestTarget`, `resumePendingIntent` vào façade. File đang 497 dòng
  với ngưỡng test 501, nên gần chắc phải tách file trước khi chuyển — đó là lý do nó thành batch riêng.
- Nói thẳng: façade hiện là lớp uỷ nhiệm mỏng. Giá trị nó mua được ngay là **một đường ranh đo được**
  thay vì 14 đường rò; việc thu hẹp thật thuộc S1.

## Patch clean sau review đối kháng (2026-07-27 13:00)

| Phát hiện Pass 4 | Trạng thái |
|---|---|
| F1 `:core` sạch | ✅ 0 import android/dadb |
| F3 transport nằm ở `:app` | ✅ đã tách `CastAdbGateway` sang `:car-integration`, đảo hai phụ thuộc Android thành hàm truyền vào |
| F2 façade không chặn kiểu | 🟡 42 → **38**, máy móc còn 15; ratchet mới đo đúng thứ này |
| adb đi tắt (phát hiện mới, nặng hơn F2) | 🟡 ghim 13 điểm; 8 trong đó ở `ClusterCast.kt` (V1 cũ) |
| F4 app lớn hơn core | ⬜ chưa |
| F5 chiều feature | ⬜ chưa |
| F6 split package | ⬜ chưa |
| F7 nới public | ⬜ chưa, và hôm nay nới thêm 2 chỗ |

Bánh răng cũ (`CastFacadeBoundaryTest`) đo **lời gọi** và đã về 0. Bánh răng mới
(`CastArchitectureRatchetTest`) đo **coupling kiểu** và **điểm mở adb** — hai thứ mà bánh răng cũ không
thấy. Bài học ghi lại để không lặp: đo đúng thứ mình tuyên bố, không đo thứ dễ đo.

## B6 — bằng chứng

```
$ ./gradlew :car-integration:run --args="observe --host 127.0.0.1 --port 5555"
{"capability":"observe","ok":false,"elapsedMs":2315,"endpoint":"127.0.0.1:5555",
 "observation":"Unknown(reason=expected exactly one named cluster display)"}

$ ./gradlew :car-integration:run --args="observe --host 127.0.0.1 --port 5599"   # không có thiết bị
{"capability":"observe","ok":false,"elapsedMs":15,"endpoint":"127.0.0.1:5599",
 "observation":"Unknown(reason=AM_STACK_LIST failed: Connection refused)"}
```

Hai tính chất đáng giá ở đây: runner **không cần APK** để trả lời câu hỏi về xe, và khi thất bại nó nói
**lý do** trong thời gian có biên chứ không treo — đúng thứ mà đường app→build→cài→tap không cho được.

Endpoint được truyền vào (`--host`), mặc định vẫn `localhost:5555` để app không đổi hành vi: trong app,
code này chạy ngay trên head unit.

Runner hiện **chỉ đọc**, và có test chặn việc thêm capability gây biến đổi — vì chưa có observable phân
biệt "cụm đang hiện app" với "cụm đang hiện đồng hồ" (Q1). Thêm lệnh mutate trước khi có Q1 là lặp lại
đúng sai lầm sáng nay: hành động mà không kiểm chứng được kết quả.

## Hạ coupling (2026-07-27 13:40)

42 → 31 kiểu, trong đó vượt tầng thật = 27. Máy móc từ 16 → 9 → còn 4 nhóm điều khiển
(`CastManualTargetReader`, `CastAutomationSettings`, `ExecutionResult`, `CastAppPresentation`).
Thêm 11 phương thức façade, mỗi cái thay một chỗ UI phải tự biết hình dạng bên trong.

Bảy test phải cập nhật vì chúng ghim **đường gọi** cũ như proxy cho hợp đồng. Mỗi lần tôi giữ nguyên ý
nghĩa hợp đồng và chỉ đổi đường gọi, có ghi lý do ngay tại chỗ sửa.

Phép đo cũng phải sửa một lần: ratchet đếm theo package nên gộp cả lớp còn sống trong `:app`. Giờ nó tra
module khai báo. 576 test xanh.

## Track shell/adb theo step × candidate (2026-07-27 13:50) — DONE

Theo chỉ đạo: UI để sau, đi phần đánh giá bằng adb trước, từng candidate cho từng step, đánh cờ OK/FAIL,
rồi E2E chỉ ghép cái đã OK.

Đã có:
- `CarExecCatalog` (`:core`): 6 step × 11 candidate, khai báo bằng Kotlin chứ không phải bash. Mỗi
  candidate có lệnh, điều-gì-chứng-minh-đạt, nguồn verdict (máy đo hay mắt người) và ghi chú field.
- `VerdictLedger` (`:core`): append-only, dòng mới nhất quyết định trạng thái; step OK khi có ≥1 candidate
  OK; `e2eChain` chỉ ghép step đã OK và nêu tên step còn thiếu.
- `CarExecShell` + 4 lệnh runner (`steps`, `run`, `verdict`, `e2e`) trong `:car-integration`.
- `run-on-car.md`: phiếu chạy cho phiên tới, ≤ 10 phút, kèm thứ tự trả lời Q1.

Đã chứng minh trên thiết bị thật (emulator): `run observe.dumpsys` chạy 3 lệnh qua adb, in output; ghi
verdict OK cho observe và FAIL cho place; `e2e` báo `CHƯA ĐỦ` và nêu đúng 5 step còn thiếu.

Hai quyết định thiết kế có lý do:
1. **Chạy và kết luận là hai lệnh riêng.** Máy in ra cái nó thấy, người ghi verdict. Với mở/đóng chiếu
   thì đây là cách duy nhất trung thực; với step đo được thì nó vẫn giữ ledger là chuỗi quyết định có chủ.
2. **Shell thô (`CarExecShell`) tách khỏi gateway của app.** Gateway app chỉ nhận `CommandKind` đã khai
   báo, có fence epoch và deadline — đúng cho app. Runner thì cần chạy lệnh thô để *tìm ra* lệnh nào đúng.
   Có test chặn `:app` tham chiếu `CarExecShell`.

593 test xanh (app 345 + core 234 + car-integration 14).

## Scenario E2E theo feature (2026-07-27 14:00)

Catalog mở rộng: **14 step × 24 candidate**, chia hai feature.

Cluster Cast thêm: `switch` (đổi app khi đang chiếu), `adjust-geometry` (từng cạnh, và đường overscan
của V1), `adjust-dpi`, `set-style` (cong 30 ↔ phẳng 31).
Navigation lần đầu có mặt: `nav-listener`, `nav-source`, `nav-cluster-lane`.

`CarExecScenarios` — 5 kịch bản có tên, mỗi hành động ghi rõ **state phải đúng** và **ai kiểm được**:

| Kịch bản | Nội dung |
|---|---|
| `cast.rotate-a-b-c-a` | chiếu A → về màn chính → chiếu lại A → B → C → A; 3 lần switch; kiểm không sinh orphan |
| `cast.geometry-persist` | chỉnh 4 cạnh + DPI → kiểm render → về màn chính → **chiếu lại và kiểm bounds/density có giữ đúng** |
| `cast.style-toggle` | cong ↔ phẳng khi đang chiếu, và sau Stop |
| `cast.protected-sink` | CarPlay/Android Auto đang giữ: resume, không giết app của người ta |
| `nav.cluster-and-hud` | quyền listener → nguồn phát → làn cụm hiện hướng rẽ |

Luật ráp: kịch bản **không được coi là chạy được** khi còn step chưa có candidate OK, và nó **nêu tên**
step còn thiếu. Nếu cho chạy sớm thì một lỗi ở bước ba sẽ bị hiểu là lỗi cả chuỗi, rồi mất buổi để truy
nguyên đúng thứ đã biết là chưa xong.

Có test ghim yêu cầu nghiệp vụ, không chỉ ghim cấu trúc: `cast.geometry-persist` **phải** có bước kiểm
bounds và density *sau* khi cast lại — không có bước đó thì kịch bản chỉ chứng minh lệnh chạy được, chứ
không chứng minh thiết lập được giữ.

Lệnh mới: `scenarios` (cái nào chạy được), `scenario <id>` (từng bước + state mong đợi + ai kiểm).

## Rà soát cho đủ (2026-07-27 14:15)

Owner nhắc: 5 kịch bản chỉ là ví dụ. Nên tôi lấy **bảng 32 ca canonical** trong
`docs/specs/cluster-cast-rebaseline.html` làm nguồn sự thật thay vì tự nghĩ ra.

Kết quả: **23 step × 36 candidate**, **22 kịch bản**, phủ **32/32 ca**.

Step thêm mới vì 32 ca đòi: `bootstrap-cold`, `probe-target`, `resume-protected`, `return-protected`,
`pip-guard`, `animation-quiesce`, `orphan-inspect`, `target-process`, `power-state`.

Kịch bản thêm mới: cold-first, recast-same, protected-matrix (6 chiều CP/AA), landing-faults,
sink-disconnect, stop-paths, target-process-death, sleep-wake, orphan-heal, geometry-stale, pip-coexist,
target-missing, transport-fault, display-missing, boot-automation, animation-guard, pipeline-independence.

Ba test biến "đủ hay chưa" thành câu hỏi máy trả lời được:
- `phu du 32 ca canonical` — mỗi kịch bản khai `coveredCases`, hợp lại phải bằng 1..32.
- `moi step trong catalog duoc dung boi it nhat mot kich ban` — chặn việc khai step rồi bỏ đó.
- `khong force-stop app duoc bao ve` — kịch bản nào dính CarPlay/AA thì không được chứa step giết tiến
  trình. Đây là ràng buộc sản phẩm, ghim bằng test chứ không bằng ghi nhớ.

Hai candidate tôi ghi thẳng là **CHƯA kiểm**: `power.sleep-wake` (keyevent 223/224 có tác dụng trên head
unit hay không) và `nav.notification-dump` (cờ --noredact có được phép hay không).

## Chạy tuần tự có nối lại, và một điều tôi đã nói sai (2026-07-27 14:20)

### `scenario <id> --run [--from N]`
Chạy tuần tự: bước nào máy đo được thì chạy liền, bước nào phải nhìn cụm thì **dừng**, in ra điều cần
nhìn, kèm đúng câu lệnh ghi verdict và câu lệnh chạy tiếp. Ba tính chất có test:
- step chưa OK → dừng ngay, không chạy bừa;
- lệnh trả exit khác 0 → dừng và gợi ý ghi verdict fail, không đi tiếp;
- `--from N` nối lại được từ giữa chuỗi, nên phiên trên xe bị cắt ngang không phải làm lại từ đầu.

### Rút lại một khẳng định sai của tôi
Tôi đã nói nhiều lần rằng "logic đặt/tháo cụm bị chép lại trong thư mục scripts/vehicle, tạo ra bản hiện
thực thứ hai". **Sai.** Đo lại hôm nay: 9/10 script có **0** dòng lệnh thiết bị; script còn lại
(`run-cast-matrix.sh`) có 6 dòng khớp mẫu, nhưng đó là *assertion tĩnh soi source Kotlin* và chữ mô tả
bước, không phải lệnh chạy trên máy. Rủi ro "hai bản hiện thực" vì thế nhỏ hơn tôi đã trình bày, và
K/F về nó cần đọc lại theo dữ kiện này.

Cái **thật sự** hỏng ở đó là khác: `V2_SRC` trỏ vào `app/.../clustercast/v2`, đường dẫn đã dời khi tách
module — nên bốn assertion tĩnh đang soi một thư mục không tồn tại. Chúng sẽ **xanh mà vô nghĩa**, đúng
loại bẫy đã gặp hai lần trong ngày. Đã sửa: script soi cả ba module và fail thẳng nếu không tìm thấy
source ở đâu cả. Kiểm lại: `am display move-stack` xuất hiện đúng 1 lần, nằm ở `:car-integration`.

## Rà soát và vá (2026-07-27 14:30) — DONE

Sweep đường dẫn: **0** đường dẫn chết trong toàn bộ script, build file và docs sau khi tách module. Chỗ
duy nhất hỏng (`V2_SRC` trong `run-cast-matrix.sh`) đã sửa để soi cả ba module và fail thẳng nếu không
tìm thấy source.

Attestation kiểm lại: manifest phủ **cả ba module** — core 61, car-integration 10, app 12 input untracked;
`verifyExactSourceIdentity` xanh. Rủi ro K2 (đứt chuỗi APK-khớp-source khi tách module) coi như đóng.

`scripts/vehicle/carexec.sh` — vỏ mỏng gọi runner. Không chứa logic thiết bị, từ chối chạy nếu thiếu
`CAR_HOST` cho lệnh cần nói với xe.

### Lỗ tự review tìm ra và đã vá
`adjust-geometry` xuất hiện bốn lần trong `cast.geometry-persist` cho bốn cạnh, nhưng `ScenarioAction`
chưa mang được giá trị riêng — nên **bốn bước gửi cùng một lệnh**, kịch bản chỉ *trông như* đang kiểm bốn
cạnh. Đã thêm `values` cho từng hành động, đặt số cụ thể cho từng cạnh (40px trên, 40px dưới, 30px trái,
30px phải) và density 280, rồi ghi rõ con số phải kiểm sau khi cast lại: bounds `[30,40,1890,680]`,
density `280`. Có hai test chặn tái diễn: step lặp lại phải có bộ giá trị khác nhau, và bước kiểm phải nêu
con số cụ thể chứ không nói chung chung.

609 test xanh (app 345 + core 245 + car-integration 19).

## Hết việc off-car cho track shell/adb (2026-07-27 14:45) — DONE

Rà lại và làm hết những gì có thể làm mà không cần xe:

**1. `RecordedDevice`** — transport phát lại output đã ghi của xe. Đây là mảnh còn thiếu trong ba
transport mà spec hứa; không có nó thì bộ quan sát không kiểm được off-car.

**2. Test parser trên fixture thật — lỗ tôi từng tuyên bố đã đóng mà chưa làm.** Exit gate của B3 ghi
"parser test chạy trên fixture thật", tôi báo B3 xong, nhưng 10 file fixture nằm đó chưa test nào dùng.
Giờ có 6 test đọc thẳng output thật của head unit. Kết quả đáng nói: **parser đúng cả 6**, kể cả trên
`am-stack-list-occupied.txt` — nghĩa là sai lúc 09:33 là do **tôi grep tay**, không phải code sai. Hai
test trong đó còn khoá lại hai kết luận đã có: display cụm luôn tồn tại (nên không phải tín hiệu chiếu),
và `bydAdd-<pkg>` vẫn còn khi chiếu đã đóng.

**3. `plan <id>`** — in đúng chuỗi lệnh sẽ gửi, không gửi gì. Duyệt kịch bản trước khi lên xe.

**4. Tiền kiểm** — 4 test: mọi placeholder đều có cờ CLI tương ứng; mọi kịch bản chạy được với bộ tham số
một phiên bình thường biết được; `plan` không gửi lệnh; file ledger có sẵn header để lần ghi đầu không lỗi.

Lý do dồn hết vào off-car: thời gian trên xe bị giới hạn bởi thứ không ai điều khiển được — 26/7 mất cả
phiên vì ACC standby. "Thiếu một cờ CLI" không được phép là phát hiện tại chỗ.

620 test xanh (app 345 + core 251 + car-integration 24).

### Trên xe chỉ còn đúng ba việc
1. `export CAR_HOST=<ip>` rồi `scripts/vehicle/carexec.sh scenario cast.cold-first --run`
2. Nhìn cụm, nói đúng/sai → `carexec.sh verdict <candidate> ok|fail --note "..."`
3. Chạy tiếp `--from N` cho tới hết; lặp cho các kịch bản khác theo `carexec.sh scenarios`

Kèm một lệnh cho Q1: chụp dump SurfaceFlinger lúc **chiếu đang mở** để so với
`fixtures/sf-FULL-projection-CLOSED.txt` (đã có mốc `numLayers=0` lúc đóng).

## Trả lời dứt điểm: off-car còn gì (2026-07-27 14:55)

Owner hỏi lại hai lần, nên tôi đi kiểm thay vì trả lời theo cảm giác — và tìm ra **4 lỗ mình đã bỏ**:

| Lỗ | Đã xử lý |
|---|---|
| `RecordedDevice` viết rồi để đó, không lệnh nào dùng | `observe --recorded` chạy off-car trên fixture thật |
| Không step nào chụp bằng chứng cho Q1 | thêm step `capture-state` + kịch bản `cast.observable-hunt` |
| Output runner không lưu ra file | `carexec.sh` ghi mọi lần chạy vào `oncar-carexec-<ngày>/` |
| `run <candidate>` chưa có dry-run | `run <candidate> --dry-run` |

`cast.observable-hunt` là kịch bản đi tìm câu trả lời cho Q1 một cách có phương pháp: chụp mốc lúc cụm
hiện đồng hồ → đặt task lên cụm mà **chưa** mở chiếu → chụp lại (đã đo 27/7: `numLayers=2` mà cụm vẫn
đồng hồ, nên tiêu chí cũ sai) → mở chiếu → chụp → đóng chiếu → chụp. Trường nào đổi **đúng lúc** cụm đổi
thì đó là observable cần tìm; trường nào không quay lại giá trị đầu thì loại.

### Còn đúng một thứ off-car không làm được
`observe --recorded` hiện dừng ở `APP_OPS_STATE: không có bản ghi`. Phiên 27/7 không chụp `appops get`,
nên thiếu đúng một fixture. Tôi **không** bịa chuỗi rỗng để nó xanh — rỗng sẽ bị parser hiểu là "đọc được
và không có gì", tức lại biến thiếu dữ liệu thành kết luận sai. Bản ghi này nằm trong step
`capture-state` và sẽ có ngay ở phiên xe tới; sau đó quan sát chạy trọn vẹn off-car.

620 test xanh. Ngoài fixture đó, không còn việc off-car nào cho track shell/adb.

## Quy tắc xếp chỗ + checklist review (2026-07-27 15:35)

Owner hỏi có principle/rule để review liên tục, không nhầm chuồng. Trước đó **chưa có** quy tắc quyết
định — chỉ có 6 bất biến về hành vi (I1–I6) và 7 test ranh giới rời rạc. Đó là lý do trong một ngày tôi
xếp sai chuồng bốn lần.

`layering-rules.md`:
- **Ba câu hỏi quyết định**: cần Android/thiết bị không → nói với head unit qua adb hay dùng API Android
  cục bộ → thuộc feature nào.
- **Bảng xếp chỗ theo loại artifact** (13 dòng): model/hằng số, máy trạng thái, parser, projector, port và
  impl, bảng opcode, transport, Activity, bitmap, fixture, test.
- **Bảy cái bẫy đã mắc**, mỗi cái thành một quy tắc, có ghi nguồn thật.
- **Checklist review** 4 lệnh chạy sau mỗi lần dời.
- **Bảng cưỡng chế**: 9 quy tắc có test giữ, **2 quy tắc ghi thẳng là chưa cưỡng chế được** (feature không
  gọi ngang feature; phán đoán quyết định/dữ liệu).

`LayeringRulesTest` cưỡng chế bốn thứ: `:core` không có Android/dadb, `:car-integration` không có Android,
**số file thuần còn nằm trong `:app` chỉ được giảm** (hiện 7 file / 995 LOC: AppScale, WmParse, StackParse,
DisplayParse, CastOperationStatus, CastActivityRefresh, ModuleRegistry), và tài liệu quy tắc phải tồn tại
kèm cột cưỡng chế.

Ratchet thứ ba là cái đáng giá nhất: nó biến "nhầm chuồng" thành lỗi test ngay lần build sau, thay vì chờ
tình cờ đo lại mới thấy.

## Review B1–B5 bằng checklist (2026-07-27 16:10) — DONE

Áp checklist ngược lại các batch đã làm. Tìm được **năm chỗ nhầm chuồng thật**, sửa hết:

| # | Vi phạm | Quy tắc | Đã sửa |
|---|---|---|---|
| 1 | `CarExecCommands` 276 LOC, **0 lần dùng dadb**, nằm trong `:car-integration` | Q1 | → `:core`, tách kiểu kết quả thành `ShellOutcome` |
| 2 | 5 file navigation nằm ở **gốc package** `:core`, không thuộc cột nào | Q3 | → `navigation/` |
| 3 | `AndroidObservedStateParser` trong module không có Android | P4 | → `DumpObservedStateParser` |
| 4 | Helper test `SourceRoots` nằm trong namespace feature Cast, bị test Navigation import | Q3 | → `com.byd.clusternav.testsupport` |
| 5 | `WmParse`, `StackParse`, `DisplayParse` (comment của chính chúng ghi "PURE") + `AppScale` còn ở `:app` | bảng xếp chỗ | → `:core`, 747 LOC |

**Cả năm đều thuộc nhóm "chưa cưỡng chế"** — chỉ lộ ra vì soi tay theo checklist, không phải vì test đỏ.
Nên bốn trong sáu quy tắc đó **đã thành test**: mọi file `:car-integration` phải dùng dadb; gốc package
`:core` phải rỗng; không tên `*Android*` trong module không có Android; navigation ↮ cast trong `:core`.

Ratchet file-thuần-còn-trong-`:app`: **7 → 3**, và ba file còn lại có lý do ghi rõ trong test
(`CastActivityRefresh`, `CastOperationStatus` là vòng đời UI; `ModuleRegistry` là hạ tầng app) — không phải nợ.

Còn đúng hai quy tắc không tự động được: P2 (chỉ-dời-chỗ, phải đọc diff) và Q2 (quyết định hay dữ liệu).

632 test xanh. Checklist sạch: 0 leak, 0 đường dẫn chết, 0 file lạc chuồng.

## 2026-07-27 chiều — đóng phần off-car

| Việc | Kết quả |
|---|---|
| Đường release sau khi tách module | APK dựng lại được, DEX chứa `cast/platform`, `cast/transport`, `carexec/LocalDeviceShell`; `verifyExactSourceIdentity` xanh |
| E2E emulator | **18/18** sau khi sửa 2 lỗi thật + làm harness sạch (trước đó chỉ 14 phép kiểm chạy được) |
| R13 | Trạng thái nêu đúng cơ sở: "Cửa sổ đã lên cụm · nhìn cụm để xác nhận" |
| R14 | Quét vét cạn ở `:core` + 4 bài kiểm ở `:app`; bắt được 2 ngõ cụt thật |
| Máy móc geometry | 10 chỗ gọi `runtime.adjustment` trong Activity → 0 |
| Transport | `PersistentDeviceShell` vào `:car-integration`; adb ngoài transport 9 → 8 |

### Ba lỗi khoá người dùng tìm được trong ngày

1. `stopRequested` không có recovery substate → tập hành động rỗng (quét vét cạn bắt).
2. Tile chọn app khoá theo trạng thái cụm, dù chọn app không phát lệnh nào ra xe (E2E bắt).
3. `activityActions` so bằng với hai tập cứng, trong lúc chờ trả về đúng `STOP` → **mất Chẩn đoán**; và vỡ im lặng mỗi khi tập phép đổi (đọc mã khi sửa lỗi 2 mới thấy).

### Lỗi đo thứ năm của chính bộ ratchet

Phép đếm coupling chỉ nhìn dòng `import`, nên tên đầy đủ viết thẳng trong biểu thức là vô hình.
Dọn sang import làm con số "tăng" 26 → 27 trong khi coupling vốn đã ở đó. Số thật 28 → hạ về 27
bằng `DraftOutcome`. Bài học đã ghi: **đọc tên bài kiểm, rồi đọc phạm vi nó quét.**

### Ratchet cuối ngày

| | Sáng | Chiều |
|---|---|---|
| coupling kiểu UI | 42 | **27** (phép đo đã trung thực hơn) |
| coupling vượt tầng | 27 | **27** |
| điểm mở adb ngoài transport | 13 | **8** (đều trong `ClusterCast.kt` V1) |
| file thuần còn trong `:app` | 7 | **3** |
| test | 353 | **648** |

### Còn lại off-car

- Đổi tên package cho 26 file `:core` — cố ý chờ xoá V1 để không đổi hai lần.
- Mã Dead Reckon / mock-location (~1.095 LOC) giữ cho rollback.
- Fixture `appops get` phải chụp trên xe (`observe --recorded` dừng ở `APP_OPS_STATE`).
- **verdicts.tsv vẫn 0 dòng** — 20 candidate đã chạy bằng tay trên xe nhưng chưa qua runner.

## 2026-07-27 19:30 — ĐÍNH CHÍNH kết luận Q1

Chủ xe cho biết: **trước khi phiên test bắt đầu, VietMap đã được cast lên cụm** trong lúc chạy xe
về. Nghĩa là chiếu đã MỞ ngay từ đầu phiên, và bản chụp tôi ghi nhãn "S0-idle" thực chất không
phải trạng thái nghỉ.

### Điều này phá mắt xích nào

Kết luận "không tín hiệu nào phân biệt chiếu mở/đóng" dựa vào giả định: `18,0` thật sự đóng chiếu
và `30,16,35` thật sự mở lại, **trong phiên đó**. Bằng chứng duy nhất tôi có là `Parcel(0,0)` —
chỉ nói IPC thành công, không nói cụm đã đổi. Không ai nhìn cụm trong lúc tôi bật/tắt.

Hai cách giải thích đều khớp dữ liệu:

1. Không có tín hiệu nào phân biệt được (kết luận ban đầu).
2. Chiếu **không đổi trạng thái** suốt phiên — nên tôi đã so hai bản chụp của CÙNG một trạng thái,
   và "giống hệt" là hiển nhiên.

Không có cách nào tách hai khả năng đó bằng dữ liệu đang có. **Q1 vẫn MỞ.**

### Đã thử dùng fixture sáng làm đối chứng — không dùng được

Bản `sf-FULL-projection-CLOSED.txt` sáng nay có người xác nhận "về rồi", nhưng lúc đó **không có
task nào trên cụm** (`numLayers=0`, 17 layer `bydAdd`), còn hai bản chiều đều có task
(`numLayers=2`, 10 layer). Khác 1288 dòng là do khác task, không phải do chiếu. Đối chứng sai
biến số nên vô giá trị.

Cũng đã soi phần `activities` khác 40 dòng giữa hai bản: **toàn bộ là timestamp.**

### Phần vẫn đứng vững sau đính chính

- Fixture `appops get` đã chụp được (không phụ thuộc trạng thái chiếu).
- `place.movestack` FAIL thật: `am stack move-task` báo Exception.
- Task nằm trên display 1 với `visible=true` trong khi `18,0` đã trả `Parcel(0,0)` — tiếp tục xác
  nhận "task trên display 1" không phải bằng chứng đang chiếu.
- Logcat `xdja_AutoContainerService` ghi nhận lệnh gửi tới — chứng minh ĐÃ PHÁT LỆNH.
- Lỗi sổ verdict ghi vào file bóng: thật, đã sửa, đã khoá bằng test.

### Việc cần làm để chốt Q1 — 60 giây, cần người nhìn cụm

1. Chủ xe xác nhận đang thấy map trên cụm → chụp snapshot, ghi nhãn bằng chính lời anh.
2. Gửi `18,0` → hỏi anh thấy gì → chụp, ghi nhãn.
3. Gửi `30,16,35` → hỏi anh thấy gì → chụp, ghi nhãn.

Chỉ khi ba nhãn đó do người xác nhận thì phép so mới có nghĩa. Bài học: **không tự dựng "sự thật"
từ một công thức chưa được kiểm trong chính phiên đó.**

## 2026-07-27 19:38–19:40 — Q1 ĐÃ CHỐT (có người xác nhận cả hai đầu)

Làm lại đúng cách đã ghi ở mục đính chính: mỗi trạng thái đều có nhãn do chủ xe xác nhận, và task
nằm y nguyên trên display 1 ở cả hai lần, nên biến duy nhất thay đổi là chiếu OEM.

| Bước | Lệnh | Chủ xe nhìn cụm |
|---|---|---|
| X | task đã đặt + `30,16,35` | **"vietmap lên ok nhé"** (19:38) |
| Y | `18,0` | **"đồng hồ"** (19:40) |

### Kết quả so sánh X vs Y

| Nguồn | Khác |
|---|---|
| `dumpsys display` | 3 dòng, đều là `(N ms ago)` của cảm biến sáng |
| `am stack list` | 0 |
| `SurfaceFlinger --list` | 0 |
| `service list` | 0 |
| `window displays` | 2 dòng nhiễu cảm biến gia tốc |
| `dumpsys activity activities` | 40 dòng, **toàn bộ timestamp** |
| `SurfaceFlinger` đầy đủ (lọc nhịp vẽ) | 8 dòng: đổi slot buffer, `vsync on 0→1`, bộ đếm fps |

**Không có tín hiệu chỉ-đọc nào của Android phân biệt được "cụm hiện app" và "cụm hiện đồng hồ".**
Đã ghim thành bài kiểm `RealFixtureParsingTest.khong the phan biet…` chạy trên chính hai bản chụp
có người xác nhận, để về sau không ai dựng tuyên bố "đã xác minh đang chiếu" trên các tín hiệu này.
Bài kiểm chỉ lọc giá trị thời gian trôi; nếu `numLayers`, `layerStack`, kích thước hay cờ display
đổi theo chiếu thì nó sẽ đổ — tức là đã tìm ra observable.

Sửa một câu tôi nói sai trong lúc làm: hai bản dumpsys **không** bằng nhau đến từng byte; tôi suy từ
kích thước file bằng nhau, đó là suy luận hỏng. Chúng khác đúng 3 dòng mốc thời gian.

### Hệ quả cho sản phẩm

`ACTIVE_VERIFIED` không thể là "đã xác minh đang chiếu" — nó chỉ là "cửa sổ đã nằm trên display cụm".
Chữ đã đổi chiều nay là đúng, và giờ có bằng chứng đóng.

### Lỗi mới, do chủ xe phát hiện: Stop có thể để app MỒ CÔI

Sau `18,0`: *"đồng hồ, và vietmap cũng ko ở màn chính luôn, đi đâu mất tiêu rồi"*. Task vẫn trên
display 1 nhưng cụm đã về đồng hồ → app không hiện ở cả hai màn.

Thang Stop của V2 có `return-normal` trước khi đóng chiếu, nên đường thường không bị. Nhưng nhánh
`TargetClass.UNKNOWN_PROTECTED` **không có bước trả về nào** — Stop ở lớp đó tự tạo ra đúng trạng
thái mồ côi. Đã vá bằng `RETURN_PROTECTED_GENTLY` (`am start --display 0 -n <comp>`): ít rủi ro nhất,
không đổi windowing mode, không force-stop — và chính lệnh đó vừa cứu VietMap trên xe.

Bài kiểm mới quét **mọi** `TargetClass`: nếu thang có đóng chiếu thì phải có bước trả về, và bước đó
phải đứng TRƯỚC. Đã kiểm ngược bằng cách tạm gỡ bản vá → bài kiểm đổ đúng chỗ `UNKNOWN_PROTECTED`.
