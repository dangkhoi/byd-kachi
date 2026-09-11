# Phiên 2026-09-11 (chiều-3) — SOÁT XÉT ĐỘC LẬP TOÀN PHẦN + vá 5 nhóm

> **Trạng thái**: Current · **Cập nhật**: 2026-09-11 · **Mục đích**: giao lại lượt soát độc lập toàn bộ việc làm từ đêm qua (gói 2 → nay) và 5 nhóm bản vá, cho phiên sau.

## Vì sao có phiên này

Owner yêu cầu: *"trước khi làm tiếp, cần review độc lập lại toàn bộ các việc đã làm từ đêm qua đến giờ, vì làm rất nhiều việc, cần đảm bảo kiến trúc, codebase, không lệch lạc."*

Phạm vi: `6a18447^..HEAD` = **10 commit tính năng, 60 tệp, +6882/−259** (gói 2 → gói 3 → U1 → P8 → P9 ×3 bước → U4 ×2 → 2 lượt vá sau soát).

## Cách làm

4 luồng soát **độc lập**, chia 2 đợt (mỗi đợt 2 tác nhân — tránh tranh khoá gradle và tránh quá tải như các lượt soát hỏng trước), **chỉ báo cáo, không sửa**:

| Luồng | Phạm vi | Kết quả |
|---|---|---|
| A | Kiến trúc & ranh giới tầng | 20 phát hiện (6×P1) |
| B | Đúng đắn: thread · vòng đời · rò rỉ · ngoại lệ | 15 phát hiện (3×P1) |
| C | Code **chưa từng được soát**: U1 · P8 · gói 3 | 17 phát hiện (**1×P0** · 2×P1) |
| D | Chất lượng test + độ khớp tài liệu ↔ code | 17 test có vấn đề · 8 mutation không bắt được · 11 chỗ tài liệu lệch |

Sau đó **tôi tự kiểm lại 8 phát hiện nặng nhất** bằng cách đọc chính mã nguồn trước khi vá — và điều đó là cần thiết: trong lượt tự kiểm chính tôi có **1 dương-tính-giả** (chuỗi `0.3` khớp `0.34f` là *tỉ lệ vẽ*, không phải ngưỡng lốp).

## Nhóm vá 1 — P0: gói "Rời xe" KHÔNG khoá xe

`ControlRegistry.kt:84` (`lock` "Khoá xe") và `:108` (`door` "Mở cửa") khai **CÙNG** `bindingKey = BYDAutoDoorlockDevice.setDoorLockState`; `HalBindingTable.writeArgs` không có nhánh cho cả hai ⇒ cùng rơi vào `else -> intArrayOf(primary)` ⇒ **cùng một byte cho hai nghĩa đối nghịch**. `ActionMacros.kt:183` kết `mac_leave` bằng `MacroStep("lock", 1)` = đúng byte của `MacroStep("door", 1)`.

- **Chứng minh được không cần xe**: hai nhãn đối nghịch gửi byte y hệt ⇒ ít nhất một cái sai.
- Vá theo **giá trị tài liệu dự án**, không tự nghĩ: khoá=2 / mở khoá=1 (`launcher-hal-re-overdrive-2026-09-08.md` §7 + `kachi-capability-catalog-2026-09-10.md` §196). Kèm `rain_close` (tài liệu ON=1/OFF=2 — code cũ gửi **0** khi tắt, giá trị không có trong tài liệu).
- `writeArgs` chuyển lên **companion** (thuần) ⇒ kiểm được off-car.
- **Luật mới khoá cả họ** (`ControlWriteArgsTest`): *hai mã dùng chung một lệnh xe thì tham số phải KHÁC nhau*, trừ danh sách cho phép có ghi lý do.
- Luật đó **bắt thêm 3 cặp** dùng chung feature-id nhưng nghĩa khác: `cam`/`camera_view` · `headl`/`headlight_mode` · `brightness_gear`/`hud_brightness`. Không tự chọn tham số (tự nghĩ giá trị chính là nguyên nhân P0 này) ⇒ khai vào `COLLISION_PENDING_CAR` kèm lý do + test **chống mục rữa** + backlog `L-RE2` 🚗.

⚠ Đây là **lần thứ HAI** cùng họ lỗi "nhãn hứa việc A, gửi lệnh việc B" (lần 1: nút "Kính 50%"). Test lần 1 chỉ chặn nhãn chứa `%` — chặn *hiện tượng*; lần này chặn *nguyên nhân*.

## Nhóm vá 2 — 4 lỗi P1 hành vi

1. **Ô TRỘN bị dựng lại mỗi nhịp trạng thái xe.** `WorkspaceRenderPlan.hasReadContent` trả true nếu ô có **bất kỳ** mục đọc ⇒ ô trộn (mục đọc + nút/gói lệnh/ảnh) bị tháo/gắn mỗi giây ⇒ **mất cú bấm**; chốt chống-bấm-kép nằm **trong View** nên dựng lại là mở cửa cho lượt thứ hai (hai lượt "đóng hết kính" chồng nhau); widget trình chiếu bị đặt lại vòng quay (ảnh đứng một tấm) + giải mã ảnh trên thread chính mỗi giây.
   Vá: `WidgetViews.refreshRead` **thay tại chỗ đúng ô con là mục ĐỌC** (mỗi ô con mang `WidgetTag`), giữ nguyên view của nút và widget tự-lo-nội-dung; chốt sang `ControlTileState.beginRun/endRun` theo **mã gói**; `renderInternal` thử làm mới tại chỗ TRƯỚC khi tháo view.
2. **Tắt hình nền thì ảnh QUAY LẠI.** `reloadWallpaper` chỉ tăng thẻ QUÉT; lượt giải mã đang bay về sau vẫn `wall.setPhoto(...)`. Vá **hai lớp**: tăng thẻ giải mã khi tắt + kiểm lại công tắc lúc ảnh về (lớp 2 còn bắt cả ca đổi hồ sơ).
3. **Cấp quyền trợ năng có thể XOÁ của app khác.** Kênh shell lỗi trả **chuỗi rỗng** (không ném) mà bản cũ coi rỗng = "danh sách rỗng" ⇒ ghi danh sách chỉ-có-Kachi. Nay phân biệt *rỗng thật* (`"null"`) với *không đọc được* (null/rỗng/chuỗi lạ không có token đúng dạng) — không đọc được thì **tuyệt đối không ghi danh sách**, chỉ được bật cờ.
4. **Nhịp/lệnh nền treo sau khi màn huỷ.** Gom mọi việc nền của màn chính về **một cửa** `submitBg` (bỏ qua nếu đã huỷ + `runCatching`), `LauncherWindows.cancelPending()` gỡ lượt đã hẹn, `onDestroy` đặt cờ `destroyed` **trước** khi tắt thread nền, Runnable 600 ms có tên để gỡ được.

Kèm P1 của luồng C: `EvidenceTier.needsBadge` từng trả `false` cho `NEEDS_CAR` ⇒ 4 nút yếu nhất bộ **không có cảnh báo**; nay `!= PROVEN`.

## Nhóm vá 3 — đóng bẫy hai bản sao (kiến trúc)

`customLayout` (2 bản: màn chính + `WorkspaceView`) · `unitPrefs` (**4 bản**: màn chính · khung làm việc · thanh nút · bảng Tuỳ biến) · `wallpaper` nay là field của **`HomeUiState`**. 3 đường ghi bền đi qua intent ViewModel (`setCustomLayout`/`setUnitPrefs`/`setWallpaperPrefs`); tầng UI **0** lần gọi `workspaceRepository.set*`. `PrefsWorkspaceRepository.load()` nạp cả 3 ⇒ ca **đổi hồ sơ tự đúng** (trước phải nhớ nạp lại bằng tay ở tầng UI, và đã từng quên).

Lý do tránh né cũ ("thêm field vào state là xáo trộn bộ quyết-định-dựng-lại đang bị test hơn 1000 tổ hợp khoá") **không đúng**: bộ đó nhận `WorkspaceState`, không nhận `HomeUiState`.

## Nhóm vá 4 — bộ test từng không đáng tin như con số nói

- [ĐO] **44** phép cắt vùng trong test canh dùng `substringAfter/Before`; ≥5 bài có **mốc kết không nằm sau mốc đầu** ⇒ Kotlin trả nguyên phần còn lại ⇒ bài đó quét tới **hết tệp** ⇒ assert `contains` gần như **không thể đỏ**. Nay tất cả đi qua `SourceRoots.body()` — **tự nổ** nếu mốc không tồn tại. Khi chuyển, **14 bài đỏ ngay** ⇒ bằng chứng chúng từng xanh nhờ quét tràn.
- ⚠ Chính helper của tôi sai **3 lần** trước khi đúng: (a) tham số mặc định `= false` bị đọc thành thân-biểu-thức; (b) `!=` bị đọc thành gán; (c) mốc tự kết bằng `{`. Ghi lại vì loại lỗi này rất dễ lặp.
- Sửa **3 assert không thể đỏ** + **2 assert dùng sai toán tử** (`&&` chỗ cần `||`, và một vế là mã chết vì ưu tiên toán tử) + **1 test khoá hành vi SAI** (`needsBadge` đòi `NEEDS_CAR == false`) + **1 tiền đề khoá hiện trạng sai** (đòi phải CÒN nhãn trùng).
- Thêm test cho **3 chỗ trước đây 0 test**: phép kẹp trình vẽ bố cục (tách sang `:core GridEditorLogic` — chỗ này từng có lỗi **SẬP** `coerceIn` trần<sàn mà không bài nào canh; nay quét >500 tổ hợp khung hỏng, không được ném) · **trần ô ở giá trị MẶC ĐỊNH** (mọi bài cũ truyền `cap` tường minh nên đổi mặc định thành 99 không ai đỏ) · **hành vi cấp quyền trợ năng** (8 ca).
- Thêm guard bảo vệ chính bản vá ô-trộn (`WidgetRefreshInPlaceContractTest`).
- Sửa một lỗi thật phát hiện qua luồng D: `setPhotoSource` so danh sách ảnh **theo SỐ LƯỢNG** ⇒ xoá 1 + thêm 1 ⇒ coi như không đổi ⇒ widget giữ danh sách cũ. Nay so theo **nội dung**.

## Nhóm vá 5 — đính chính tài liệu

| Tài liệu nói | Thực tế (đo được) |
|---|---|
| 2652 / 2654 test | **công bố 2699** (mọi biến thể) · **phân biệt 2048** — `:app` chạy 2 biến thể trên **cùng 84 lớp** ⇒ 651 test đếm hai lần |
| trạng thái xe đổi "2 nhịp/giây" | **1 giây** (`CarStatusRepository.fastMs = 1_000`, nhịp chậm 10 s) — đã sửa cả KDoc trong code |
| gói lệnh "tự đặt được ở 3 vùng" | **1/3 vùng trọn vẹn**: thanh trên còn 3 chip viết cứng; ngăn kéo không bày hành động cho ô giữa màn |
| RW0 "còn (a) Tuỳ biến chưa bày mã đọc" | (a) **đã xong** từ gói 2; còn (b) thanh trên · (c) nhãn ngắn · (d) ngăn kéo |
| "toàn bộ đọc/giải mã ảnh đã vá" | chỉ **hình nền**; widget trình chiếu **vẫn** giải mã trên thread chính |
| 121 datum · 26 mục có đơn vị | **123** datum · **27** mục |
| spec dynamic-grid "Bước 1/3" · 2 spec "Draft" | P9 xong cả 3 bước · 2 spec DONE |
| P8 "lặp tới khi đủ mới vào launcher" | **KHÔNG chặn launcher** (đúng như test đã khoá) |

## Vòng dọn nợ cùng ngày (owner: *"đảm bảo clean hết rồi mới làm tiếp"*)

Vá thêm **9 mục P2/P3** — sau vòng này danh sách "còn tồn off-car" chỉ còn việc **tính năng**, không còn nợ chất lượng:

| Nợ | Cách vá |
|---|---|
| widget trình chiếu giải mã ảnh **trên thread chính** | thread nền **riêng dùng chung mọi ô** (`DECODER`, ưu tiên thấp, daemon) + thẻ thế hệ + nhả ảnh nếu lượt cũ về muộn / ô đã tháo |
| widget giải mã lượt đầu ở **cỡ 1×1** | chưa có cỡ thật thì **không** giải mã; `onSizeChanged` nạp ở cỡ đúng |
| `winExec` gộp lệnh cửa sổ (dadb chặn ~3 s) với I/O ảnh | tách `ioExec` + cửa `submitIo`; `submitOn` là chỗ duy nhất biết luật "đã huỷ thì thôi" |
| `MediaBridge.read()` gọi mỗi ô mỗi nhịp | đọc **một lần mỗi lượt** render/dựng lại |
| thanh trên dựng lại 3 chip mỗi nhịp | dựng một lần, sau đó chỉ đổi **chữ** |
| Back không đóng bảng vẽ | thêm nhánh `layoutPanel` vào `onBackPressed` |
| trình vẽ không xử lý nhiều ngón | chỉ theo `activePointer` |
| 13/64 nút thiếu icon riêng | vẽ **3 icon mới** (`ic_mirror` · `ic_drive` · `ic_adas`) + nối bảng ánh xạ |
| nhãn ô hành động cắt cứng | 2 dòng + `ellipsize` |
| `notice()` là mã chết ở sản phẩm | thêm chế độ `notice(coreOnly = true)` cho HOME |

⚠ **[ĐO] một bản vá của tôi bị test bắt đúng lúc**: cho HOME gọi `notice()` **không tham số** ⇒ test đỏ, vì `notice()` nói RỘNG hơn `missingCore` ⇒ launcher sẽ báo cả mục nhỏ mỗi lần mở, trái thiết kế. Sửa bằng cách **thêm tham số** thay vì đổi ngữ nghĩa.

⚠⚠ **TAI NẠN CỦA CHÍNH TÔI — ghi để không lặp**: một phép thay bằng **regex `DOTALL`** (`[^;]*?` khớp qua nhiều dòng) đã **ăn mất 6 test** trong `PermissionPreflightWiringContractTest`, mà **bộ test vẫn xanh** vì phần còn lại vẫn biên dịch. Tìm ra nhờ **đếm `@Test` so với `HEAD`** — không phải nhờ test đỏ. Phục hồi từ git rồi sửa thủ công từng chỗ.
**Luật mới**: không dùng regex nhiều dòng để sửa test; sau mỗi lượt sửa hàng loạt, đối chiếu số `@Test` với `HEAD`.

## Số đo cuối

- **`./gradlew test --continue` → 2699 test, 0 đỏ** (5 module; phân biệt 2048).
- Kiến trúc lõi giữ nguyên: `:core` thuần (1 kết quả `import android` duy nhất — trong chú thích) · **0** lần chạm display ≥ 1 · **0** ghi `wm size/density` · **0** `Dadb.create`/`Runtime.exec` ngoài `ShellTransport`.

⚠ **Quét bảo mật trước commit nêu thêm một điểm cùng họ — đã vá luôn**: sau khi sửa P0, nút `door` (TOGGLE nhãn *"Mở cửa"*) có mặt **TẮT = khoá xe** mà nhãn không nói ⇒ đúng họ *"nhãn hứa việc A, trạng thái kia làm việc B"*. Sửa: `door` thành **NÚT BẤM một chiều "Mở khoá cửa"** (không có mặt tắt để nói dối, luôn gửi 1), và `lock` đổi nhãn thành **"Khoá / mở khoá"** vì nay tắt nó **thật sự mở khoá** (trước bản vá nó gửi `0` = giá trị không có trong tài liệu nên gần như chắc chắn bị xe bỏ qua). ⚠ **Chưa từng chạy trên xe** — cả hai mã đều ở mức chưa-kiểm nên ô mang chấm cảnh báo.

## Tách tệp về đúng trần dòng

**TÁCH TỆP để về đúng trần 500 dòng (luật sức khoẻ mã):** `KachiHomeActivity` phình lên **678 dòng** sau vòng vá ⇒ cắt 3 đường khớp: **`WallpaperController`** (hình nền + trình chiếu, 161 dòng) · **`HomePanels`** (2 bảng phủ: Tuỳ biến + bảng vẽ bố cục, 114 dòng) · **`PermissionPreflight.runAndReport`** (vòng kiểm quyền về đúng lớp của nó). Activity còn **498 dòng** và trở lại đúng vai composition-root. Cả 3 nhận **cổng vào bằng lambda** như `LauncherWindows`/`DrawerController` đã làm; hành vi giữ y nguyên từng dòng. ⚠ [ĐO] khi tách, **15 bài canh đỏ** vì mốc quét không còn — nhưng chúng **nổ đúng chỗ** (`không tìm thấy '<mốc>'`) thay vì âm thầm xanh, tức helper mới làm đúng việc; đã trỏ sang tệp mới. Các tệp còn > 500 dòng đều là **ClusterNav kế thừa** (`MainActivity` 1386 — đang bị niêm phong byte-seal, `ClusterCast` 1285…), ngoài phạm vi launcher.

## Còn tồn — KHÔNG che

Sau vòng dọn, **hết nợ chất lượng off-car**; còn lại là việc tính năng và nợ cần xe:

- **🚗 cần xe**: chiều đúng của lệnh khoá cửa (mã tier OVERDRIVE) · 3 cặp feature-id trùng (`L-RE2`) · ca "ô TRỘN 1 nhịp/giây" chỉ khoá bằng test, chưa đo trên xe · toàn bộ nợ 🚗 nhóm 6 của backlog.
- **Off-car còn làm**: RW0 vùng **thanh trên** + nhãn ngắn datum + **ngăn kéo chưa bày hành động** cho ô giữa màn · U5 đa ngôn ngữ · W5 camera 360 · S1 màn Cài đặt · P7 tự khởi động · P6.
- **Chưa commit** tới thời điểm ghi tệp này (xem mục cuối nếu đã commit sau đó).
