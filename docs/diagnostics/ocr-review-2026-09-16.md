# Chạy `open-code-review` (Alibaba OCR) trên Kachi — kết quả + nhận xét chất lượng tool (2026-09-16)

- **Chủ:** dangkhoi · **Tool:** `@alibaba-group/open-code-review` v1.12.4 (npm, Apache-2.0, 30,9k ⭐) · **Chế độ dùng:** *delegation mode* (`ocr delegate preview` / `ocr delegate rule <files>`): OCR chọn tệp + cấp bộ luật theo ngôn ngữ, **Claude (Opus) làm reviewer** đọc code tại HEAD `9edc828`. Chế độ LLM riêng của OCR (`ocr review`/`ocr scan` gọi API Claude/OpenAI/Qwen, WebUI xem lại phiên) **chưa đánh giá** — không có API key trong phiên này.
- **Phạm vi:** toàn bộ mã đã commit: 431 tệp Kotlin main (:core/:app/car-integration) + 74 script (py/sh), chia 5 nhóm review song song; test files bị OCR loại mặc định (`excluded: default_path`).
- **Cách chấm:** chỉ ghi finding đã đọc-xác-minh (không nit suy đoán), trần 15 P3/nhóm; mỗi P0–P2 kèm patch `git apply --check` sạch, **chưa áp** khi có agent khác đang sửa cùng tệp (9 finding "in-flight" thuộc hotfix voice).

## 1. Kết quả tổng

| Nhóm | Tệp | P1 | P2 | P3 | Patch |
|---|---|---|---|---|---|
| G1 :core launcher | 66 | 1 | 2 | 12 | 9 |
| G2 :core còn lại (voice, cast, carexec, bridge) | 144 | 1 | 7 | 15 | 10 |
| G3 :app launcher UI | 120 | 1 | 4 | 15 | 6 |
| G4 :app hệ thống + car-integration | 101 | 1 | 7 | 15 | 7 |
| G5 scripts (py/sh) | 74 | 4 | 13 | 15 | 14 |
| **Tổng** | **505** | **8** | **33** | **72** | **46** |

P0: 0. Bảng chi tiết: `findings-all.json` (scratchpad, 113 dòng) — mỗi dòng có file:line, luật, bằng chứng, đề xuất, độ tin cậy.

## 2. Findings P1 (8) — đã xác minh đọc code

- **G1** `core/src/main/kotlin/com/byd/clusternav/launcher/CarDataDemand.kt:124` — Launcher action tile on the dock silently disables the whole H1 read gate
- **G2** `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/BoundedCastExecutor.kt:44` — Mọi exception trong tác vụ cast bị nuốt im lặng vào Future không ai đọc
- **G3** `app/src/main/java/com/byd/clusternav/launcher/SlotAppHost.kt:96` — release() re-opens the retry gate instead of closing it — up to 6 s of activity launches onto a released ActivityView
- **G4** `app/src/main/java/com/byd/clusternav/UpdateFlow.kt:25` — OTA: `r.downloadUrl!!` NPE tren main thread khi kenh co ban moi nhung thieu link tai
- **G5** `scripts/vehicle/nav-icon-sweep.sh:46` — `timeout` does not exist on macOS — every navopen write in the sweep silently no-ops
- **G5** `scripts/vehicle/hud3-speedlimit.sh:78` — Restore clobbers the saved fusion value with a hardcoded 1, and ISLA is restored to a value never read
- **G5** `scripts/vehicle/hud1-nav-hud.sh:44` — HUD/nav settings are written even when their original value could not be read, and the restore then skips them
- **G5** `scripts/vehicle/kachi/71-hal-sweep.sh:78` — WRITE sweep fires 60+ state-changing HAL controls on a real car with no k_confirm gate

## 3. Findings P2 (33) — tóm tắt theo họ

- **generator/non-determinism** (2): `feature-catalog.py:131` · `render_checklist.py:395`
- **coroutines** (1): `CarStatusRepository.kt:71`
- **injection** (1): `ShellAppLauncher.kt:19`
- **correctness/regex-injection (CLAUDE.md §4 "nhắm đúng app nào")** (1): `AppMover.kt:190`
- **correctness/crash (OCR §1 null-safety & edge cases)** (1): `AppScale.kt:141`
- **error-handling/over-narrow-catch** (1): `ClusterLaneAdapter.kt:120`
- **concurrency/queue-starvation (OCR §4)** (1): `SimpleCastCoordinator.kt:148`
- **OCR §1 null-safety (`!!`)** (1): `CarExecCommands.kt:167`
- **OCR §7 performance-pitfalls (Regex trong vòng lặp)** (1): `CarExecCommands.kt:198`
- **correctness/logic (dữ liệu và nhãn tươi-cũ lệch nhau)** (1): `VietMapWidgetTextParser.kt:204`
- **android/system-command-scope** (1): `VdAppHost.kt:189`
- **android/thread-per-event** (1): `VdAppHost.kt:281`
- **android/dialog-after-destroy** (1): `CapTestConsole.kt:250`
- **android/main-thread-blocking** (1): `AppDrawerApps.kt:35`
- **resource-management/socket-leak** (1): `LocalAbstractChannel.kt:18`
- **kotlin/dead-code + correctness** (1): `VietMapWidgetBridge.kt:205`
- **concurrency/shared-mutable-state** (1): `FloatingBubbleService.kt:57`
- **correctness/infinite-loop** (1): `BydHal.kt:628`
- **correctness/lateinit** (1): `FloatingBubbleService.kt:172`
- **security/command-injection** (1): `LocalDeviceShell.kt:270`
- **correctness/resource-lifecycle** (1): `CarExecShell.kt:35`
- **correctness/restore-to-constant** (1): `71-hal-sweep.sh:128`
- **bash/trap-does-not-break-loop** (1): `probe-gps-tunnel.sh:49`
- **correctness/env-vs-shell-variable** (1): `matrix.sh:139`
- **bash/and-or-fallthrough** (1): `voice-e2e.sh:351`
- **python/None-from-DictReader** (1): `analyze_drive_logs.py:73`
- **python/encoding** (1): `rcc_extract.py:117`
- **python/silent-broad-except** (1): `rcc_extract.py:86`
- **python/dict-access-without-missing-key-handling** (1): `gen-variants.py:195`
- **missing-restore-path** (1): `nav-icon-sweep.sh:55`
- **correctness/stale-lock** (1): `resume-clusternav.sh:35`
- **correctness/unanchored-grep** (1): `auto-smoke-test.sh:121`

## 4. Trạng thái áp patch

- ✅ **G5 scripts: 14/14 patch áp** (2026-09-16 tối; `bash -n`/`py_compile` sạch): `timeout` không có trên macOS (nav-icon-sweep), restore ghi đè giá trị đã lưu (hud3), thiếu đường khôi phục (hud1), cổng `k_confirm` cho HAL write sweep (71), Ctrl-C không dừng (gps-tunnel), `EVIDENCE_DIR` không export (matrix), `None` crash (analyze_drive_logs), generator không tất định (ngày giờ), encoding + nuốt lỗi (rcc_extract), khoá kẹt (orchestrator), khớp version theo tiền tố (auto-smoke).
- ⏳ **G1–G4 Kotlin: 32 patch chờ áp** sau khi hotfix voice 1.69 nhả gradle (áp → `:core:test` + `:app:testDebugUnitTest` → review). 9 finding in-flight chuyển thẳng cho hotfix.

### 4.1 Việc B — 29 patch Kotlin (G1 + G2 + phần G3/G4), áp 2026-09-16

[ĐO] `gl.sh` (lock dùng chung), đếm test từ JUnit XML. 27/29 `git apply` sạch, 2 áp TAY (xung đột với cây đang sửa).
Ngoài 29 patch còn **3 việc "patchable:false"** được xử tại chỗ và **2 việc bị TỪ CHỐI** (ghi rõ lý do + `file:line`).

| # | Patch | Sev | Trạng thái | Ghi chú |
|---|---|---|---|---|
| G1-1 | `CarDataDemand` — mã `launcher_*` trên thanh nút tắt cổng H1 | P1 | ✅ áp | + 2 test, đã thử ĐỎ |
| G1-2 | `CarStatusRepository` — lượt đọc HAL nằm trong `StateFlow.update` (CAS ⇒ gọi lại lambda) | P2 | ✅ áp | `publish{}` một khoá |
| G1-3 | `ShellAppLauncher` — tên gói vào lệnh shell không lọc | P2 | ✅ áp | khuôn tên gói Android |
| G1-4 | `HalAbsentCache.record` — `remove` ngoài khoá của nhánh kia | P3 | ✅ áp | một `compute` cho cả hai nhánh |
| G1-5 | `KachiPerf` — `%.1f` theo locale máy | P3 | ✅ áp | `Locale.ROOT` |
| G1-6 | `CapTestCodec` — lọc `\n` mà không lọc `\r`; `total()` dựng 190 object | P3 | ✅ áp | ⚠ `total()` KHÔNG có chỗ gọi nào (§8) |
| G1-7 | `ActionMacro.tier` — `maxByOrNull{ordinal}` → `maxOrNull()` | P3 | ✅ áp | enum đã `Comparable` |
| G1-8 | `EffectiveLayout` — hai `custom!!` | P3 | ✅ áp | |
| G1-9 | `AppWidgetLabels` — dựng lại danh sách ứng viên mỗi lần | P3 | ✅ áp | |
| G2-01 | `BoundedCastExecutor` — exception chìm vào `Future` không ai `get()` | P1 | ✅ áp | thêm `onFailure` (mặc định giữ nguyên hành vi 1.68) + `schedule()` |
| G2-02 | `AppMover` — tên gói chưa `Regex.escape` ⇒ có thể lấy nhầm taskId | P2 | ✅ áp | |
| G2-03 | `AppScale.nudgeEdge` — `coerceIn` dải rỗng ⇒ ném | P2 | ✅ áp | sửa thêm vị trí KDoc bị patch đẩy lệch |
| G2-04 | `ClusterLaneAdapter` — chỉ bắt `RuntimeException` ⇒ kẹt EMITTING | P2 | ✅ áp | |
| G2-05 | `SimpleCastCoordinator.setError` — `Thread.sleep(3000)` khoá worker cast | P2 | ✅ áp | đổi luồng: hồi lỗi chạy trên `TIMEOUT_SCHEDULER` |
| G2-06 | `CarExecCommands.planScenario` — `!!` trên `CarExecCatalog.step` | P2 | ✅ áp | |
| G2-07 | `CastStackParser` — Regex dựng lại mỗi dòng | P3 | ✅ áp | |
| G2-08 | `TestBridgeJson` — `"%04x".format` theo locale | P3 | ✅ áp | |
| G2-09 | `ArrowClassifier` — `fmt`/`TAG` mã chết | P3 | ✅ áp | |
| G2-10 | `VoicePhrases` — hai `!!` trong `build()` | P3 | ✅ áp | |
| G3-1 | `SlotAppHost.release()` — mở lại cổng thử lại (≈6 s mở app lên VD đã nhả) | P1 | ✅ áp | + test, đã thử ĐỎ |
| G3-4 | `CapTestConsole` — `AlertDialog` bung từ luồng nền, không canh vòng đời | P2 | ✋ áp TAY | theo đúng khuôn `VoiceTextConsole.ask` đã có |
| G3-5 | `WorkspaceView` — biến cục bộ `first` chết | P3 | ✅ áp | |
| G3-6 | `CapabilityTileGrid` — hai `!!` trên biến bắt | P3 | ✋ áp TAY | ⚠ lý do trong finding **sai**: `cols == 0` ném `ArithmeticException` ở `i % cols` TRƯỚC, không phải NPE ⇒ thêm cổng `cols <= 0` để phần "không vẽ gì" thành sự thật |
| G4-1 | `UpdateFlow` — `r.downloadUrl!!` NPE trên main thread | P1 | ✅ áp | + 2 test, đã thử ĐỎ |
| G4-3 | `VietMapWidgetBridge` — cổng "thế hệ" là mã chết (so field với chính nó) | P2 | ✅ áp | + test, đã thử ĐỎ |
| G4-4 | `FloatingBubbleService` — `pipPreviousModes` CME + `onStartCommand` deref `lateinit` | P2 | ✅ áp | + 2 test, 1 đã thử ĐỎ |
| G4-5 | `BydHal.root` — lặp vô hạn trên chuỗi `cause` có vòng 2 mắt xích | P2 | ✅ áp | + test, đã thử ĐỎ |
| G4-6 | `LocalDeviceShell.grantAppWidgetBind` — `$pkg` thẳng vào lệnh uid-2000 | P2 | ✅ áp | |
| G4-7 | `CarExecShell.run` — giữ lại phiên dadb ĐÃ CHẾT | P2 | ✅ áp | |

**Ngoài patch — 3 finding `patchable:false` đã xử:**

| # | Chỗ | Sev | Đã làm |
|---|---|---|---|
| 42 | `AppDrawerApps.kt:35` | P2 | Icon đổi từ `Drawable` dựng sẵn sang **hàm** `() -> Drawable?`; `tile()` bung trên pool 2 luồng daemon rồi gắn qua `post` ⇒ khung đầu của ngăn kéo không còn chờ N lượt bung icon. Một dòng đổi theo ở `AppDrawer.kt:150`. |
| 53 | `AndroidTtsSpeaker.kt:147` | P3 | `onInit` về trước khi field `tts` được gán ⇒ ghi `initMissed` rồi chạy nốt ở `available()`. ⚠ Ô cờ phải khai **TRƯỚC** `tts` (thứ tự khởi tạo field) — khai sau là NPE bị `runCatching` của `tts` nuốt, mất luôn cả máy đọc. |
| 22 | `VietMapWidgetTextParser.kt:204` | P2 | **TỪ CHỐI vá.** Không phải lỗi: `speedFreshness`/`alertsFreshness` là nguồn sự thật per-slot, `freshness` gộp chỉ là *"legacy combined field"* (KDoc `worst()` nói thẳng). Người tiêu thụ THẬT trên cụm — `NavNotificationListener.kt:211` — đã gate bằng `snapshot.speedFreshness == FRESH`. Ép rỗng payload khi gộp ≠ FRESH sẽ **xoá biển giới hạn tốc độ đang tươi** chỉ vì slot cảnh báo nguội ⇒ hồi quy trên dữ liệu an toàn. Việc còn lại: chốt với owner + thêm bài canh hợp đồng (vào backlog). |

**Từ chối (2):** finding 22 (trên) và phần lý lẽ của G3-6 (ArithmeticException chứ không phải NPE — đã áp bản sửa đúng).

## 5. Nhận xét chất lượng tool (đúng cái đã dùng, không suy diễn phần chưa dùng)

**Cái OCR đóng góp trong delegation mode = (a) chọn tệp/diff, (b) bộ luật theo ngôn ngữ.** Trí tuệ review hoàn toàn là của model host. Đánh giá theo hai phần đó:

| Tiêu chí | Nhận xét | Mức |
|---|---|---|
| Cài đặt / tích hợp | `npm i -g` 20 s, CLI rõ, `delegate preview` liệt kê tệp + số dòng, `delegate rule` gộp luật theo nhóm ngôn ngữ; có plugin Claude Code (`/plugin marketplace add alibaba/open-code-review`), Codex/Cursor. Tài liệu delegation mode mỏng. | ★★★★☆ |
| Chọn tệp | Đúng: bỏ test theo mặc định, hỗ trợ diff/branch/full-scan/`--exclude`. Không có cách chia lô cho repo lớn ⇒ phải tự chia 5 nhóm. | ★★★★☆ |
| Bộ luật Kotlin (system) | 9 mục chung (null-safety, dead code, conciseness, collections/Sequence, coroutines, class design, resources, interop, val/template). **Cả 4 nhóm Kotlin đều kết luận giống nhau**: chỉ ~3 mục cho tín hiệu thật trên codebase này (`!!`/null-safety, dead code, lifecycle executor/resources); các mục conciseness/data-sealed/Sequence/@Jvm* = 0 finding vì code đã idiom; lời khuyên `Sequence` **sai** cho registry nhỏ; "if→when" va vào quyết định có lý do (`GroupBoard.worst`). | ★★☆☆☆ |
| Bộ luật Python | Tốt hơn Kotlin: *silent except*, *boundary/None/missing key* bắt 5–6 lỗi thật; *resource `with`* ồn (tự miễn "script ngắn"). | ★★★☆☆ |
| Bộ luật default (.sh) | Quá chung ("có xử lý exception không?"); mọi lỗi bash thật (`a && b || c`, `set -e`, quoting, `timeout` không có trên macOS, thiếu đường khôi phục trạng thái xe) đến từ checklist tự thêm và từ **so với script mẫu đúng trong repo**. | ★☆☆☆☆ |
| Không có | luật Android (lifecycle/main-thread/Drawable/leak), luật shell-injection cho code dựng lệnh `am/wm/input`, luật "hàm mới phải có call site" — các nguồn P1/P2 thật của dự án này đều ngoài bộ luật OCR. Không đọc CLAUDE.md/quy ước repo. | — |
| Đầu ra | JSON/SARIF/WebUI chỉ ở chế độ LLM riêng; delegation mode không định dạng đầu ra ⇒ tự đặt schema findings. | ★★☆☆☆ |

**Số đo tỉ lệ đóng góp (agent tự ước, đồng nhất giữa 4 nhóm Kotlin):** ~35 % finding truy được về một mục luật OCR (null-safety, dead code, regex-trong-vòng-lặp, executor lifecycle), ~65 % từ review đúng/thread-safety/lifecycle + đối chiếu quy ước repo — tức phần OCR không cung cấp.

**Kết luận:** OCR delegation mode là **khung chạy review có kỷ luật** (chọn tệp, luật nền, tích hợp agent) chứ không phải nguồn trí tuệ; với repo này giá trị chính đến từ việc ép một lượt đọc **toàn bộ** 505 tệp theo cùng một khuôn — đó là thứ senior review theo batch không làm (review theo batch chỉ nhìn diff). 8 P1 tìm được đều nằm ở code **cũ, ngoài các batch gần đây** (cast executor nuốt lỗi, update flow NPE, socket rò khi daemon chạm không lên, retry sau release, script xe không khôi phục trạng thái) ⇒ đáng chạy định kỳ (mỗi tháng / trước OTA lớn). **Đã làm ngay:** `.opencodereview/rule.json` với 4 bộ luật riêng (app Android lifecycle/lệnh hệ thống §4–§5/§8 · :core registry-codec-locale-regex-executor · scripts/*.sh restore-path/k_confirm/macOS/sibling-exemplar · scripts/*.py encoding/None/determinism) — [ĐO] `ocr rules check` trả `Source: Project`, `ocr delegate rule` gộp thành "Rule Group 1: project / app/src/main/**/*.kt" + luật hệ thống. Khuyến nghị: (1) giữ delegation mode + **bộ luật riêng của repo** (Android lifecycle, shell-injection §4, call-site §8, restore-path §5) ghi vào `.opencodereview/rule.json` để OCR gộp vào spec; (2) chưa cần chế độ LLM riêng; (3) không dùng phần khuyên `Sequence`/`when`/sealed cho :core.

## 6. Bảng đầy đủ 113 finding

| # | Nhóm | Sev | Tệp:dòng | Luật | Tiêu đề | Patch |
|---|---|---|---|---|---|---|
| 1 | G1 | P1 | `core/src/main/kotlin/com/byd/clusternav/launcher/CarDataDemand.kt:124` | correctness | Launcher action tile on the dock silently disables the whole H1 read gate | ✅ |
| 2 | G2 | P1 | `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/BoundedCastExecutor.kt:44` | correctness/error-handling (OCR §4 coroutines-and-concurrency, §6 resource-management) | Mọi exception trong tác vụ cast bị nuốt im lặng vào Future không ai đọc | ✅ |
| 3 | G3 | P1 | `app/src/main/java/com/byd/clusternav/launcher/SlotAppHost.kt:96` | android/view-lifecycle-release | release() re-opens the retry gate instead of closing it — up to 6 s of activity launches onto a released ActivityView | ✅ |
| 4 | G4 | P1 | `app/src/main/java/com/byd/clusternav/UpdateFlow.kt:25` | kotlin/null-safety + correctness | OTA: `r.downloadUrl!!` NPE tren main thread khi kenh co ban moi nhung thieu link tai | ✅ |
| 5 | G5 | P1 | `scripts/vehicle/nav-icon-sweep.sh:46` | cross-platform/silent-failure | `timeout` does not exist on macOS — every navopen write in the sweep silently no-ops | ✅ |
| 6 | G5 | P1 | `scripts/vehicle/hud3-speedlimit.sh:78` | bash/and-or-fallthrough + missing-restore | Restore clobbers the saved fusion value with a hardcoded 1, and ISLA is restored to a value never read | ✅ |
| 7 | G5 | P1 | `scripts/vehicle/hud1-nav-hud.sh:44` | missing-restore-path | HUD/nav settings are written even when their original value could not be read, and the restore then skips them | ✅ |
| 8 | G5 | P1 | `scripts/vehicle/kachi/71-hal-sweep.sh:78` | project-rule/CLAUDE.md-§4-confirm-gate | WRITE sweep fires 60+ state-changing HAL controls on a real car with no k_confirm gate | ✅ |
| 9 | G1 | P2 | `core/src/main/kotlin/com/byd/clusternav/launcher/CarStatusRepository.kt:71` | coroutines | Side-effecting HAL sweep runs inside MutableStateFlow.update, which re-invokes its lambda on contention | ✅ |
| 10 | G1 | P2 | `core/src/main/kotlin/com/byd/clusternav/launcher/ShellAppLauncher.kt:19` | injection | Package name is interpolated unquoted into shell commands run as uid 2000 | ✅ |
| 11 | G2 | P2 | `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/AppMover.kt:190` | correctness/regex-injection (CLAUDE.md §4 "nhắm đúng app nào") | Tên gói nội suy thẳng vào Regex, không Regex.escape → có thể lấy nhầm taskId rồi move-task sai task | ✅ |
| 12 | G2 | P2 | `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/AppScale.kt:141` | correctness/crash (OCR §1 null-safety & edge cases) | nudgeEdge() ném IllegalArgumentException khi khung đã lưu bị suy biến (coerceIn với dải rỗng) | ✅ |
| 13 | G2 | P2 | `core/src/main/kotlin/com/byd/clusternav/navigation/ClusterLaneAdapter.kt:120` | error-handling/over-narrow-catch | BoundedNavigationOutputWorker chỉ bắt RuntimeException → Throwable khác làm cổng kẹt ở EMITTING vĩnh viễn | ✅ |
| 14 | G2 | P2 | `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinator.kt:148` | concurrency/queue-starvation (OCR §4) | setError() xếp một tác vụ Thread.sleep(3000) vào chính executor 1-luồng → khoá worker 3 s và âm thầm vứt lệnh cast kế tiếp | ✅ |
| 15 | G2 | P2 | `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecCommands.kt:167` | OCR §1 null-safety (`!!`) | planScenario() dùng `!!` trên CarExecCatalog.step() → NPE khi kịch bản trỏ tới stepId không còn trong catalog | ✅ |
| 16 | G2 | P2 | `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecCommands.kt:198` | OCR §7 performance-pitfalls (Regex trong vòng lặp) | Regex biên dịch lại trong vòng lặp nóng ở bốn chỗ (parse `am stack list` mỗi 4 s, missingPlaceholders ba tầng lặp) | ✅ |
| 17 | G2 | P2 | `core/src/main/kotlin/com/byd/clusternav/vietmapwidget/VietMapWidgetTextParser.kt:204` | correctness/logic (dữ liệu và nhãn tươi-cũ lệch nhau) | composeSnapshot() có thể trả về snapshot gắn nhãn UNAVAILABLE/STALE nhưng vẫn mang giá trị giới hạn tốc độ đã parse | — |
| 18 | G3 | P2 | `app/src/main/java/com/byd/clusternav/launcher/VdAppHost.kt:189` | android/system-command-scope | Slot launch thread issues `am force-stop` + `am start --display` after the slot's VirtualDisplay has been released | ✅ |
| 19 | G3 | P2 | `app/src/main/java/com/byd/clusternav/launcher/VdAppHost.kt:281` | android/thread-per-event | Two unbounded, non-daemon Threads created per tap on the touch fallback path | ✅ |
| 20 | G3 | P2 | `app/src/main/java/com/byd/clusternav/launcher/CapTestConsole.kt:250` | android/dialog-after-destroy | AlertDialog shown from a background-thread callback with no Activity-alive guard (BadTokenException) | ✅ |
| 21 | G3 | P2 | `app/src/main/java/com/byd/clusternav/launcher/AppDrawerApps.kt:35` | android/main-thread-blocking | Whole app list + every launcher icon decoded synchronously on the UI thread when the drawer opens | — |
| 22 | G4 | P2 | `app/src/main/java/com/byd/clusternav/system/inputd/LocalAbstractChannel.kt:18` | resource-management/socket-leak | Ro file-descriptor moi luot `connect()` hong — LocalSocket dung xong khong bao gio duoc dong | ✅ |
| 23 | G4 | P2 | `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt:205` | kotlin/dead-code + correctness | Cong kiem 'the he' cua callback widget la MA CHET — dieu kien khong bao gio dung | ✅ |
| 24 | G4 | P2 | `app/src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt:57` | concurrency/shared-mutable-state | `pipPreviousModes` (HashMap tran) bi hai luong rieng ghi va duyet ⇒ CME ⇒ PiP cua GMaps/YouTube ket o `deny` | ✅ |
| 25 | G4 | P2 | `app/src/main/java/com/byd/clusternav/modules/hal/BydHal.kt:628` | correctness/infinite-loop | `BydHal.root()` lap VO HAN tren chuoi `cause` co vong — nam tren duong ghi nav ~4 lan/giay | ✅ |
| 26 | G4 | P2 | `app/src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt:172` | correctness/lateinit | `onStartCommand` goi `showBubble()` deref lateinit chua khoi tao khi `onCreate` da dung som | ✅ |
| 27 | G4 | P2 | `car-integration/src/main/kotlin/com/byd/clusternav/carexec/LocalDeviceShell.kt:270` | security/command-injection | `grantAppWidgetBind` noi thang `$pkg` vao lenh shell uid-2000 khong he validate | ✅ |
| 28 | G4 | P2 | `car-integration/src/main/kotlin/com/byd/clusternav/carexec/CarExecShell.kt:35` | correctness/resource-lifecycle | `CarExecShell.run` giu lai phien dadb DA CHET ⇒ moi buoc con lai cua kich ban hong voi cung mot loi | ✅ |
| 29 | G5 | P2 | `scripts/vehicle/kachi/71-hal-sweep.sh:128` | correctness/restore-to-constant | TOGGLE 'restore' always writes 0 instead of the value the control had before the sweep | — |
| 30 | G5 | P2 | `scripts/vehicle/probe-gps-tunnel.sh:49` | bash/trap-does-not-break-loop | Ctrl-C prints 'stopped by operator' but the sampling loop keeps running | ✅ |
| 31 | G5 | P2 | `scripts/vehicle/matrix.sh:139` | correctness/env-vs-shell-variable | Generated sign-off rows always have an empty evidence column | ✅ |
| 32 | G5 | P2 | `scripts/emulator/voice-e2e.sh:351` | bash/and-or-fallthrough | `--only wav` reports 'model not ready' for any run_t2 failure | ✅ |
| 33 | G5 | P2 | `scripts/analysis/analyze_drive_logs.py:73` | python/None-from-DictReader | AttributeError on truncated drive logs — csv.DictReader yields None for missing trailing columns | ✅ |
| 34 | G5 | P2 | `scripts/docs/feature-catalog.py:131` | generator/non-determinism | Today's date is baked into the committed catalog HTML, so regeneration always produces a diff | ✅ |
| 35 | G5 | P2 | `scripts/vehicle/render_checklist.py:395` | generator/non-determinism | Wall-clock timestamp baked into the committed carexec checklist HTML | ✅ |
| 36 | G5 | P2 | `scripts/re/rcc_extract.py:117` | python/encoding | Corpus written with the locale encoding — UnicodeEncodeError under LC_ALL=C and on Windows | ✅ |
| 37 | G5 | P2 | `scripts/re/rcc_extract.py:86` | python/silent-broad-except | zlib inflate failures silently drop a resource from the corpus with no counter and no message | ✅ |
| 38 | G5 | P2 | `scripts/voice/gen-variants.py:195` | python/dict-access-without-missing-key-handling | Bare KeyError when the registry gains a ControlKind or templates.tsv lacks a kind | ✅ |
| 39 | G5 | P2 | `scripts/vehicle/nav-icon-sweep.sh:55` | missing-restore-path | nav-screen mode 0x4C10E015 is written with no prior read and never restored | ✅ |
| 40 | G5 | P2 | `scripts/orchestrator/resume-clusternav.sh:35` | correctness/stale-lock | A killed run leaves the run-lock directory behind and wedges the orchestrator forever | ✅ |
| 41 | G5 | P2 | `scripts/vehicle/auto-smoke-test.sh:121` | correctness/unanchored-grep | Candidate-version assertion passes on a longer version string | ✅ |
| 42 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/HalAbsentCache.kt:60` | thread-safety | The "read succeeded" branch mutates the map outside the lock the miss branch uses | ✅ |
| 43 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/KachiPerf.kt:85` | correctness | Perf report line formats floats with the machine's default locale | ✅ |
| 44 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/CapabilityTest.kt:100` | correctness | CapTestCodec.encode strips \n and \t from a note but not \r, while decodeAll splits on \r | ✅ |
| 45 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/HalBindingTable.kt:444` | performance | Regex objects are compiled on every HAL parse and every route classification | in-flight |
| 46 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/ControlRegistry.kt:457` | collections | byId is a linear scan over the registry and is called several times per datum read | in-flight |
| 47 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/HalBindingTable.kt:425` | null-safety | `.let` applied to a nullable receiver without `?.` reads as a bug even though it is correct | in-flight |
| 48 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/WorkspaceGrid.kt:244` | null-safety | `custom!!` after a `usable()` guard the compiler cannot see through | ✅ |
| 49 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/ActionMacros.kt:69` | conciseness | maxByOrNull { it.ordinal } restates what Comparable already guarantees for an enum | ✅ |
| 50 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/CapabilityTest.kt:85` | performance | total() materialises ~190 CapTestItem objects just to read a size | ✅ |
| 51 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/AppWidgetLabels.kt:54` | performance | Candidate-function list rebuilt on every call to hint() | ✅ |
| 52 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/LauncherCatalog.kt:424` | performance | Enum values() copies its backing array on every call; entries does not | ✅ |
| 53 | G1 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/ActionMacros.kt:151` | error-handling | runCatching around the macro step emitter swallows Error as well as Exception | ✅ |
| 54 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/BoundedCastExecutor.kt:47` | concurrency/state | activeFuture bị ghi đè mà không hủy Future cũ → submitStop chỉ hủy được tác vụ mới nhất | ✅ |
| 55 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/BoundedCastExecutor.kt:90` | android/main-thread | shutdown() chặn luồng gọi tới 2 giây (awaitTermination) | ✅ |
| 56 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinator.kt:618` | CLAUDE.md §8 (hàm mới có được gọi không) | SimpleCastCoordinator.shutdown() / SimpleCastRuntime.shutdown() không có call site nào | — |
| 57 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/testbridge/TestBridgeJson.kt:71` | i18n/locale (OCR §9 string handling) | `"%04x".format(...)` dùng locale mặc định → JSON hỏng dưới locale chữ số không phải Latin | ✅ |
| 58 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/navigation/ArrowClassifier.kt:61` | OCR §1 Dead Code | private fun fmt() và private const TAG không được dùng ở đâu | ✅ |
| 59 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoicePhrases.kt:119` | OCR §1 null-safety (`!!`) | Hai chỗ `!!` trong build() (dù đã được kiểm trước đó) | ✅ |
| 60 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/navigation/ClusterLaneAdapter.kt:153` | concurrency/resource-leak | Deadline được lên lịch SAU executor.execute() → tác vụ nhanh không bao giờ hủy được deadline | ✅ |
| 61 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/DisplayParse.kt:47` | correctness/parsing | appWindowDisplay/appWindowFrame nhận diện block cửa sổ bằng contains("$pkg/") — khớp cả tên gói là hậu tố | ✅ |
| 62 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/navigation/ManeuverSignature.kt:27` | concurrency/shared-mutable-state | `var note: (String) -> Unit` là biến toàn cục có thể ghi, không @Volatile, đọc/ghi từ luồng capture | ✅ |
| 63 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceEndpointer.kt:98` | correctness/state-machine | Không bao giờ vào ENDED nếu người dùng không nói gì (pha WAITING không có timeout) | in-flight |
| 64 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/carexec/T10SessionEngine.kt:167` | error-handling/over-narrow-catch | strictPlan/strictHandoff chỉ bắt IllegalArgumentException — exception khác của loader thoát ra khỏi start() | ✅ |
| 65 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/voicekey/VoiceKeyMatcher.kt:48` | concurrency/thread-safety | firedDownTime là HashMap thường, bị ghi từ luồng sự kiện phím mà không đồng bộ | ✅ |
| 66 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/voice/SherpaModelCatalog.kt:231` | correctness/fail-fast-inconsistency | default() dùng first{} (ném) trong khi byId() dùng firstOrNull{} (degrade) — một lỗi đánh máy DEFAULT_ID thành crash | in-flight |
| 67 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecCommands.kt:35` | correctness/order-dependence | resolve() thay placeholder tuần tự → giá trị chứa "{…}" bị thay tiếp bởi cặp sau | ✅ |
| 68 | G2 | P3 | `core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceClarify.kt:109` | correctness/tokenization | combine() so khớp carry đa-từ bằng deaccent() không tokenize | in-flight |
| 69 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/GridEditorView.kt:103` | android/multitouch-pointer-tracking | Drag tracks `activePointer` but reads coordinates from pointer index 0, and ignores ACTION_POINTER_UP | ✅ |
| 70 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt:471` | android/viewmodel-store-lifecycle | ViewModelStore cleared only when isFinishing, but it is never retained across recreate() either | ✅ |
| 71 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/PhotoWidgetView.kt:126` | android/view-post-after-detach | Decoded Bitmap is only recycled inside a View.post() block that may never run after detach | ✅ |
| 72 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt:293` | kotlin/dead-code | Dead local `first` — computed and never read | ✅ |
| 73 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/CapabilityTileGrid.kt:59` | kotlin/null-safety-bang | Two `!!` on a captured `var` in the shared tile-grid builder | ✅ |
| 74 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/SettingsSectionsNav.kt:260` | kotlin/null-safety-bang | Seven `!!` dereferences of nullable UI fields immediately after assignment | ✅ |
| 75 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/SettingsSectionsCast.kt:325` | kotlin/null-safety-bang | `it.side!!` inside the geometry slot chip row | ✅ |
| 76 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/VoiceTargetDispatch.kt:128` | kotlin/null-safety-bang | `place.lat!! / place.lng!!` guarded only by a separate `hasCoords` flag | ✅ |
| 77 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt:194` | kotlin/null-safety-bang | `sp.getString(key, DEFAULT)!!` three times where the Elvis form is free | ✅ |
| 78 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/voice/VoiceWiring.kt:46` | kotlin/collection-semantics | appsByLabel collapses duplicate app labels silently — the voice command can open the wrong package | in-flight |
| 79 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/voice/AndroidTtsSpeaker.kt:147` | kotlin/initialization-order | onInit can fire before the `tts` field is assigned, leaving the speaker permanently un-inited | — |
| 80 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/voice/VoiceGeocoder.kt:125` | kotlin/concurrency-race | Nominatim rate-limit throttle is a non-atomic read-modify-write on a @Volatile long | ✅ |
| 81 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/KachiLog.kt:64` | android/process-lifecycle | logcat child process is destroyed only on the size-cap path | ✅ |
| 82 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/voice/VoiceWavProbe.kt:117` | kotlin/binary-parsing | readPcm drops the odd trailing byte of a read but still subtracts the full count, misaligning later samples | in-flight |
| 83 | G3 | P3 | `app/src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt:222` | android/thread-per-event | Macro tile spawns a raw non-daemon Thread per tap | in-flight |
| 84 | G4 | P3 | `app/src/main/java/com/byd/clusternav/system/ShellTransport.kt:86` | resilience/retry-without-backoff | `exec` thu lai NGAY lap tuc, khong gian cach, khong jitter | ✅ |
| 85 | G4 | P3 | `app/src/main/java/com/byd/clusternav/system/inputd/EventInjector.kt:54` | reflection/silent-failure | `resolveInputManager` bo qua duong Android 13+ khi `getInstance()` tra ve null, va nuot moi loi phan chieu khong log | ✅ |
| 86 | G4 | P3 | `app/src/main/java/com/byd/clusternav/system/inputd/InputDaemonClient.kt:66` | concurrency/check-then-act | `ensureStarted` kiem-roi-dat khong nguyen tu tren co `starting` | ✅ |
| 87 | G4 | P3 | `app/src/main/java/com/byd/clusternav/system/inputd/InputDaemonClient.kt:111` | resource-management/executor-lifecycle | `close()` khong shutdown hai executor cua rieng no | ✅ |
| 88 | G4 | P3 | `app/src/main/java/com/byd/clusternav/system/ShellTransport.kt:104` | dead-code (CLAUDE.md §8) | `ShellTransport.close()` khong co call site nao | — |
| 89 | G4 | P3 | `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetExtraction.kt:132` | dead-code + executor-lifecycle | `VietMapWidgetExtraction.close()` khong co call site ⇒ `hashExecutor` khong bao gio shutdown | — |
| 90 | G4 | P3 | `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetBridge.kt:242` | performance/thread-churn | Dung `Thread { hashFuture.get() }` tran cho MOI luot callback ALERTS | ✅ |
| 91 | G4 | P3 | `app/src/main/java/com/byd/clusternav/UpdateChecker.kt:95` | security/path-from-remote-input | Ten tep APK tai ve lay tu `download_url` cua may chu, khong validate | ✅ |
| 92 | G4 | P3 | `app/src/main/java/com/byd/clusternav/UpdateChecker.kt:79` | kotlin/null-safety | Hai `!!` tren `bestVer` trong vong doc danh sach APK | ✅ |
| 93 | G4 | P3 | `app/src/main/java/com/byd/clusternav/vietmapwidget/VietMapWidgetPrefs.kt:50` | resilience/retry-without-backoff | `commitWithRetry` thu lai NGAY, khong gian cach | ✅ |
| 94 | G4 | P3 | `app/src/main/java/com/byd/clusternav/NavNotifRawLog.kt:39` | resource-management/file-lifecycle | BufferedWriter cua hai bo log CSV khong bao gio dong, va co the ghi vao tep `DiagStorageCap` da xoa | — |
| 95 | G4 | P3 | `app/src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt:64` | concurrency/inconsistent-locking | `shutdown()` doi `instance` ma khong lay khoa ma `coordinator()` dung | ✅ |
| 96 | G4 | P3 | `app/src/main/java/com/byd/clusternav/modules/hal/BydHal.kt:620` | error-handling/silent-catch | `exemptHiddenApis()` nuot moi loi, khong mot dong log | ✅ |
| 97 | G4 | P3 | `app/src/main/java/com/byd/clusternav/Prefs.kt:403` | performance/hot-path | `badgeCenterX/Y` chay `migrateBadgeIfNeeded` MOI lan doc, tren duong ve overlay | ✅ |
| 98 | G4 | P3 | `app/src/main/java/com/byd/clusternav/AmapEmissionArbiter.kt:212` | concurrency/lock-scope | `emitLocked` goi `sink(...)` TRONG khoa — sink phat `sendBroadcast` + dong bo cham dia | — |
| 99 | G5 | P3 | `scripts/orchestrator/resume-clusternav.sh:42` | security/unattended-agent | Unattended agent run with --trust-all-tools guarded only by prose in the prompt | — |
| 100 | G5 | P3 | `scripts/vehicle/hud-carservice-probe.sh:20` | portability/hardcoded-path | navopen jar default hardcodes one machine's workspace layout | ✅ |
| 101 | G5 | P3 | `scripts/vehicle/hud-carservice-probe.sh:22` | bash/unquoted-variable | $ADB used unquoted — breaks on any adb path containing a space | ✅ |
| 102 | G5 | P3 | `scripts/analysis/analyze_drive_logs.py:133` | python/dead-code | `hdr` is built and never used | ✅ |
| 103 | G5 | P3 | `scripts/analysis/analyze_drive_logs.py:94` | python/boundary | Hand-rolled --samples parsing crashes on a trailing or non-numeric value | ✅ |
| 104 | G5 | P3 | `scripts/voice/synth-corpus.py:163` | python/zero-division | `--per-id 0` raises ZeroDivisionError | ✅ |
| 105 | G5 | P3 | `scripts/voice/mishear-table.py:212` | python/boundary | manifest.tsv unpack requires exactly 10 fields | ✅ |
| 106 | G5 | P3 | `scripts/voice/mishear-table.py:282` | correctness/silent-fallback | An unmatched --primary silently falls back to the last hotword config | ✅ |
| 107 | G5 | P3 | `scripts/voice/mishear-table.py:327` | comment-code-mismatch | The documented 'does the reference parse to the right id' check is not implemented | — |
| 108 | G5 | P3 | `scripts/voice/gen-variants.py:220` | python/boundary | os.makedirs("") when --out is a bare filename | ✅ |
| 109 | G5 | P3 | `scripts/voice/hotword-matrix.py:87` | correctness/key-collision | Two hotword files with the same basename silently overwrite each other's column | ✅ |
| 110 | G5 | P3 | `scripts/vehicle/run-cast-matrix.sh:32` | bash/word-splitting | Static Cast assertions depend on word-splitting a space-joined path string | ✅ |
| 111 | G5 | P3 | `scripts/re/verify-reproducibility.py:329` | python/uncaught-exception | StopIteration escapes the error handling in main() | ✅ |
| 112 | G5 | P3 | `scripts/vehicle/hud3-recon.sh:42` | project-rule/CLAUDE.md-§4-confirm-gate | `ac 1000 2` lights every cluster warning lamp with no confirmation gate | ✅ |
| 113 | G5 | P3 | `scripts/vehicle/install-test-apk.sh:18` | bash/pipefail-sigpipe | `grep -q` in a pipefail pipeline can make a successful install look failed | ✅ |
