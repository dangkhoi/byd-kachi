# Buổi xe 05/10 — chạy nền / tắt ô trên ROM BYD · cụm chữ nhật · ADAS · đổi theme làm khởi động lại framework

> **Trạng thái**: Current · **Cập nhật**: 2026-10-05 · **Loại**: Diagnostics (đo trên xe) · **Owner**: dangkhoi ·
> **Mục đích**: ghi nguyên số đo của buổi xe 05/10 (Seal DiLink 3.0, firmware 2602030, Kachi 2.88 (189), adb qua cầu nc —
> IP xe không ghi) để làm căn cứ cho bản sửa 2.89 và cho `CLUSTER-RECT-SEAL`. Ảnh chụp cụm / màn hình chỉ lưu ngoài repo.
> **Backlog**: `FIELD-287-SLOT-BG` · `FIELD-288-SWAP-UNDER` · `SLOT-PARK-HIDDEN` · `SLOT-CLOSE-SETTLE` · `CLUSTER-THEME-SAFE` ·
> `CLUSTER-RECT-SEAL` · `SLOT-PLACE-KEEPS-MUSIC`

Mức bằng chứng theo CLAUDE.md §2.

## 1. Chạy nền / đặt app vào ô — gốc lỗi

- [ĐO log] Nút *chạy nền* trên ô YouTube: `KachiBehind: mở giữ chỗ hỏng — java.lang.NullPointerException: Attempt to invoke
  virtual method 'boolean com.android.server.wm.ActivityDisplay.hasSplitScreenPrimaryStack()' on a null object reference`
  tại `BehindHomeRunner$AndroidAnchor.start` ⇒ `slot-back … → không mở được giữ chỗ` ⇒ câu báo 2.88
  *"YouTube chưa chạy ngầm được (KEPT_UNDER · không mở được giữ chỗ)"*.
- [ĐO log] Đặt VietMap vào ô đang chạy YouTube: `evict vd=4 A=com.google.android.youtube B=vn.vietmap.live → không mở được
  giữ chỗ` ⇒ YouTube nằm DƯỚI VietMap trong cùng màn ảo ⇒ tắt VietMap thì YouTube lòi lên (lỗi owner báo).
- [ĐO decompile `services.jar` fw 2602030] NPE ném ở `ActivityStackSupervisor.handleNonResizableTaskIfNeeded`:
  `actualStack.getDisplay().hasSplitScreenPrimaryStack()` — gọi từ cuối `ActivityStarter.startActivityUnchecked` với
  `mTargetStack`, ở nhánh `mDoResume = false` (khoá `android.activity.avoidMoveToFront`). Vì sao `getDisplay()` trả null
  [CHƯA BIẾT]; máy ảo AOSP không bị.

## 2. Kéo app sang màn hình khác ⇒ activity bị dựng lại

- [ĐO] K7 (`am start --display 0 --windowingMode 1 -f 0x20000000 -a MAIN -c LAUNCHER -n <comp>`) + K12 (về Home) đưa YouTube
  từ ô (màn ảo 1872×956) ra sau màn nhà: 0,39 s + 0,30 s; task sang display 0 `visible=false`. Ô đen tới nhịp đo kế (~5 s)
  rồi `APP_DIED → Clear`.
- [ĐO `logcat -b events` + `dumpsys audio`] Hai lần (14:59, 15:06): `reparentToDisplay` ⇒ `am_relaunch_resume_activity`
  ⇒ destroy/create (cùng pid) ⇒ player YouTube `stopped` + `released` ~2 s sau, không phát lại — dù tài khoản có
  Premium (owner). Gốc = dựng lại activity khi đổi display, KHÔNG phải thiếu Premium.
- [ĐO] Cùng lúc ROM chặn YouTube tự khởi service: `ssc_skip startServiceLocked 10116 want to start 10116` — tác động
  [CHƯA BIẾT].
- ⇒ Hướng 2.89: **"ô 7"** (owner: *"giả lập 1 ô số 7 … nhét các app chạy nền vào đó"*) — giữ app trong CHÍNH màn ảo của
  nó, đổi mặt vẽ sang ẩn cùng cỡ; không đổi display/size ⇒ không dựng lại [SUY mạnh — bản thử 2.89-thử1].

## 3. Tắt app trên ô

- [ĐO log] `tắt vn.vietmap.live vd=4 → STILL_THERE gửi=[17] đọc-lại=5` (cửa sổ 5 × 250 ms); 27 s sau nhịp đo mới thấy
  app rời ⇒ `am stack remove` trên xe chậm hơn cửa sổ đọc lại ⇒ báo nhầm "chưa tắt được".
- Mở app đang chạy nền vào ô ⇒ `am force-stop` + `am start` ⇒ app khởi động lại (đường mở ô golden).

## 4. Cụm — theme chữ nhật (10.25") trên Seal

| Bước | Kết quả [ĐO ảnh `fission_screencap -d 0`] |
|---|---|
| Kachi chiếu (theme 12.3" cong) | Maps trong thấu kính, km/h + kW + số P đủ; ô ADAS nhỏ góc dưới phải |
| Tắt chiếu trong Kachi | Cụm thường, vẫn theme 12.3" (tắt chiếu KHÔNG trả theme) |
| `service call AutoContainer 2 i32 1000 i32 N` (thiếu `s16 ''`) | `Parcel(fffffffc …)` = EX_NULL_POINTER, KHÔNG làm gì — lệnh PHẢI có tham số chuỗi `s16 ''` như Kachi gửi |
| `31 → 16 → 35` (có `s16 ''`) | Theme 10.25": thanh trên trọn ngang, **"m/h" lạc góc trên trái, mất số km/h**; màn ảo cụm mới = display 8 |
| `am start --display 8` Maps | Chiếu **thẳng trọn ngang** (≈ y 125–560), **khung ADAS lớn** đè ~1/3 bên phải |
| `13` (ẩn ADAS) · gửi lại `16` · gửi lại `35` | Không đổi gì |
| `30` để trả về | Service `AutoContainer` biến mất, adb rớt; **`system_server` khởi động lại lúc 15:39:17** (pid 653 → 30734, uptime không reset) — màn chính khởi động lại; sau đó cụm về theme 12.3" |

- [ĐO] Khung ADAS to/nhỏ là TRẠNG THÁI CỦA CỤM: owner bấm **phím menu vô-lăng** ⇒ ADAS lớn thu về ô nhỏ ~190×120 góc dưới
  phải; Android chỉ thấy `WindowManager: handleHangupAction keycode: 309`, không lệnh AutoContainer / feature nào. Phép đo
  này là ở theme **cong** (12.3", theme1).
- **ĐÍNH CHÍNH (05/10, sau nghiên cứu B1b)** — bản trước ghi *"ở theme chữ nhật, ADAS lớn [SUY mạnh] cũng thu được bằng phím
  menu"*: **SAI**. Ở theme chữ nhật (theme2 FULL) phím menu **KHÔNG** thu nhỏ được khung ADAS lớn — [ĐO QML fw 2602030]
  theme2 hiện nền ADAS lớn khi `adasInterfaceDisplay !== 0` (`cluster.qml:47-48`) ⇒ phím chỉ chuyển ADAS sang trạng thái
  nhỏ, nền trắng lớn bên phải vẫn còn; [ĐO owner trên xe 05/10] *"bấm nãy giờ chưa được"*. Lời owner *"trước làm chữ nhật vẫn
  thu bé được ADAS"* là firmware cũ hơn — firmware cũ hành xử thế nào thì [CHƯA BIẾT]. Theme1 (cong) chỉ hiện khung lớn khi
  `adasInterfaceDisplay === 1` ⇒ thu được bằng phím (ECU đổi trang) hoặc tự xin nhỏ sau 5 s khi đang chiếu [ĐO disasm
  `requestToShowAdasWindow(5)` @0x12c740; việc tự nhỏ 5 s trên xe CHƯA ĐO]. **Không có đòn bẩy phía app** [ĐO]: opcode
  12/13, 32/33, 47/48 không tác dụng; 41 ghi `adasInterfaceDisplay` KÈM ghi bền `theme_index/navi_type` + CAN ⇒ cấm
  (`ProjectionRecipe.FORBIDDEN_OPS`); bơm phím 309 [ĐO `PhoneWindowManager.java:3486-3490`] không thu ADAS mà cúp cuộc gọi
  Bluetooth ⇒ cấm. Hệ quả B1b: ở Chữ nhật khung app mặc định là vùng KHÔNG bị nền ADAS che (50,128)-(1285,555).
- [ĐO lần 2, sau 11/08] **Đổi theme (30/31) khi màn ảo cụm còn app ⇒ framework Android khởi động lại.** Kachi gửi `30` mỗi
  lần bắt đầu chiếu ⇒ cần chốt cứng: chỉ gửi lệnh theme khi `am stack list` không còn task nào trên màn ảo cụm
  (CLAUDE.md §4) — backlog `CLUSTER-THEME-SAFE`.

### 4.1 Số đo 05/10 chép từ bản giao việc (trước đây chỉ có trong bản giao việc — nghiên cứu B1b gắn nhãn [ĐO-gv])

Cùng buổi xe chiều 05/10, owner đo; chép nguyên ý vào đây để có căn cứ trong repo (CLAUDE.md §2: dữ liệu chỉ ở bản giao việc
không được coi là đã chứng minh tới khi nằm trong `docs/diagnostics/`).

- [ĐO xe 05/10 — bản giao việc] Chữ ký lần sập: SurfaceFlinger abort `DEAD_OBJECT` ở `HWComposer::getActiveConfig` qua
  `onHandleDestroyed`, sau đó `system_server` khởi động lại. Có **hai** lần: một lần màn ảo cụm còn Maps (15:39:17, bảng trên)
  và một lần màn ảo chỉ còn `ClusterBlackActivity` của Kachi ⇒ "chỉ còn placeholder" KHÔNG phải là trống.
- [ĐO xe 05/10 — bản giao việc] Tắt máy / nổ máy lại ⇒ cụm Seal về **theme2 gốc (chữ nhật 10.25")**; theme ép bằng opcode
  KHÔNG sống qua một lần nổ máy.
- [ĐO xe 05/10 — bản giao việc] `16 → 35` KHÔNG kèm opcode theme (lần mở đầu sau nổ máy) ⇒ cụm hiện **chữ nhật** — tức kiểu
  gốc của Seal `car.type=138` là chữ nhật.
- [ĐO xe 05/10 — bản giao việc] Màn ảo cụm 1920×720, 320 dpi (override 240 khi Kachi chiếu). App mở freeform lên đó ra cửa sổ
  DỌC [825,0][1155,720] nếu không resize ⇒ phải `am task resize … 0 0 1920 720` (hoặc khung mong muốn) sau mỗi lượt mở —
  đúng việc DashCast làm (`Phase4TaskVerbs` `resizeTask(0,0,W,H)` [ĐO source]). B1b: ở Chữ nhật, sau mỗi lượt đặt đọc lại
  bounds từ `am stack list`, lệch thì resize lại một lần rồi log.

## 5. Khác

- Chip lốp 2.88: `TYRE raw p=4/4 c=1,1,1,1|ps=-,-,-,-|lk=-,-,-,-|sys=- → G,G,G,G src=cluster` ⇒ 4 số xanh lấy từ màu cụm ✅
  (lớp 1 nguồn chính).
- Bo góc ô: hình app trên màn ảo KHÔNG bị cắt bo (góc trên trái vuông); góc trên phải lộ nền đen (0,0,0) của màn ảo qua
  góc bo của chính tấm danh sách phát YouTube.
- Bộ đệm `logcat` của xe rất nhỏ (trôi trong vài phút) ⇒ muốn bằng chứng phải bật `logcat` trực tiếp trước khi tái hiện.
- Aurora Store bản BYD (`com.aurora.store.byd`) giữ gói tải về trong bộ nhớ riêng — adb không đọc được (Permission denied).
