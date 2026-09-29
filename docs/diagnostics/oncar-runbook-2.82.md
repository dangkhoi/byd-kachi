# Runbook on-car Kachi 2.82 — buổi xe 29/09/2026

> **Trạng thái**: **Đã chạy 29/09 11:07–12:18 — khối KẾT QUẢ ngay dưới header**; Historical sau khi kết quả đã vào backlog `ONCAR-2026-09-29`. Trước buổi xe: Draft (đã vá vòng phản biện 1; vòng 2 đối chiếu đủ 78 phát hiện và đổi mốc ngày; vòng 3 sáng 29/09 vá 13 phát hiện: cổng crash §10.1, toast KEY-0, trần usage log, CAM-B5 trực tiếp…; vòng 4 vá 6: CAM-KEEP, SL6-BASE lên bước 9, chụp hỏng thì `>|`, giữ bản lùi 2.81 trước `git rm`, bản sao lời đáp cầu trong usage log, câu hỏi phím-thoại §2.1) · **Cập nhật**: 29/09/2026 khoảng 10:30, sáng buổi xe · **Chủ**: dangkhoi · **Nơi đặt**: `docs/diagnostics/oncar-runbook-2.82.md` (tệp này không bị `.gitignore` nuốt; bằng chứng thô thì bị, xem §9)
> **Commit cùng tệp này** (`.kiro/steering/documentation-and-backlog.md:21,24`): một dòng index trong `docs/README.md` (khu diagnostics, cạnh `oncar-runbook-2.76.md` ở `docs/README.md:290`; dòng 2.76 đổi trạng thái sang Superseded theo R2.4, `.kiro/steering/documentation-and-backlog.md:27`) + trỏ tới runbook này ở `docs/PROJECT-BACKLOG.md` (A11Y-BIND-STUCK · SCHED-PROFILE · CAM-GUIDE) + quét bảo mật trước commit (CLAUDE global §6). Doc không có trong index coi như không tồn tại.
> **Xe**: Seal DiLink 3, Android 10, đang chạy **2.81 (182)** do owner tự cài chiều 28/09: báo lúc 15:24, tiến trình 2.81 đầu tiên lên khoảng 15:13 [ĐO transcript: tin nhắn 15:24:47; `ps` lúc 15:36:48 cho PID 25121 ETIME 23:38]. Đó là trạng thái tới tối 28/09; sáng nay phải đọc lại từ máy ở B0 (CLAUDE.md §9) · **Bản kiểm**: **2.82 (183)** cài tay, **KHÔNG qua OTA**
> **Mục đích**: (1) lần đầu tiên xem đường tự chữa phím vô-lăng có chạy ngoài hiện trường sau **một đêm standby thật** hay không; (2) nghiệm thu 2.82 trên xe rồi mới đăng OTA. Owner (tối 28/09): *"không OTA nhé, mai test trên xe xong ok mới OTA"*; "mai" là hôm nay 29/09.
> **Thời lượng**: khoảng 93 phút trên xe (§0.1). Chỉ có 60 phút thì làm bước 1 → 11 rồi dừng ở dòng đang làm; phần chưa làm ghi "chưa đo" vào backlog, **không** suy ra kết quả. Bỏ bước nào thuộc §10.1 thì buổi này **không** đủ điều kiện OTA.
> Mức bằng chứng (CLAUDE.md §2 · `.kiro/steering/conversation-protocol.md`): **[ĐO]** đọc source/dump/xe thật · **[SUY]** suy luận khớp, chưa đo trực tiếp · **[ĐOÁN]** mới hợp lý · **[CHƯA BIẾT]**. Dữ liệu 28/09 chỉ dùng để **định hướng**; kết luận hôm nay phải có số đo **mới** (CLAUDE.md §14).

## KẾT QUẢ BUỔI XE 29/09 (11:07–12:18) — thêm sau buổi xe, phần runbook bên dưới giữ nguyên

> Chi tiết + tên tệp bằng chứng: [`docs/diagnostics/oncar-2026-09-29-findings.md`](oncar-2026-09-29-findings.md). Bằng chứng thô nằm ngoài repo; bảng dưới chỉ trích tên tệp. Không ghi IP, đích dẫn đường, ảnh, getprop ngoài 10 khoá được phép.

| Bước / ID | Kết quả | Mức |
|---|---|---|
| KEY-0 | Lên xe phím đã chết; không tìm thấy màn Chẩn đoán (owner 11:08:56) | [ĐO owner] |
| KEY-1/2 | 11:09:44: Kachi trong `Binding services`, vắng khối Bound, không `ServiceRecord`. Tiến trình sống 16:44:04 — **sinh ra từ lượt giết lúc tắt máy 28/09 18:25:41**, không phải "sống liên tục". PSS chính 56 817 kB (+17,1 %), `:wake` 137 341 kB | [ĐO] `k1-*` |
| KEY-3 | `A11yJournal` 11:07:25 `BOUND → NOT_BOUND (wake)` → 11:07:31 `NOT_BOUND → STUCK` | [ĐO] `usage-night.log` |
| KEY-4 | `am start` DiagActivity bị AMS từ chối, màn không mở; dòng lỗi nguyên văn **không lưu** (bị `tail -3` cắt) | [ĐO] từ chối · [SUY mạnh] `not exported` |
| KEY-5 / SCHED-0 | Lịch sha1 `13eeda82691d` (138 ký tự, 4 luật); MOC vắng; cả hai hồ sơ THIẾU lịch — đúng kỳ vọng | [ĐO] `sched0-*` |
| KEY-6 | Rơi **hàng 5c** của cây §3.6 (tự chữa nhận ra kẹt nhưng `nấc NONE, KHÔNG leo` vì cổng ô đóng tự nhiên) — cộng một **nguyên nhân chưa có trong cây**: kẹt sinh ra từ **lượt giết cả gói của BYD lúc tắt máy** (không `PACKAGE_RESTARTED`), không phải từ một cú đứt trên tiến trình còn sống | [ĐO] — xem báo cáo §gốc rễ |
| C1 (tắt/bật 11:20) | Bắt đầu khi **đã kẹt**; lượt giết lặp lại, tự chữa lại NONE | [ĐO] `c1-*`, `usage-cycle1.log:307` |
| KEY-7 *Sửa ngay* 11:25:54 | Phím chữa được (`PACKAGE_RESTARTED` #179, `booster connected` 11:25:58, phím 328 ăn 11:27:34) — nhưng launcher không tự lên, và **Kachi tự mở lại** YouTube thành cửa sổ freeform trên màn chính (`launchedFromPackage=com.byd.launcher`, dự phòng lúc kênh shell chưa lên — không phải app ô rơi xuống); HOME một lần là xong | [ĐO] `fix-*` · owner |
| C2 (tái hiện) | Sạch 11:33:22 → tắt máy → giết 11:33:25 → STUCK 11:34:29 → `bound=0 binding=1 svcrec=0`. Owner 11:36:21: *"phím mất bind rồi nhé"* | [ĐO] `poll-cycle2*.txt`, `c2-*` |
| INST | 11:39:13 `install -r` → `booster connected` 11:39:41 → `STUCK → BOUND` 11:39:46 (nhãn `sau-chua-ON` là giả — cài đè dọn, không lượt chữa nào chạy) → phím 328 11:47:29 | [ĐO] `usage-282-120941.log`, `inst-poll.txt` |
| SL6-BASE | Seal: `ro.product.model=BYD AUTO`, `ro.product.name=DiLink3.0`; `persist.sys.car.type=138` = `persist.sys.vehicle_40d_code` ⇒ SL6 chỉ cần `getprop persist.sys.car.type` — nhưng owner bỏ hướng này | [ĐO] |
| SCHED-1 | **Đạt** (0)–(3): sha1 trước = sau, `migrated_nav_schedule_v1=true`, cả hai hồ sơ có lịch; (4) chưa đo | [ĐO] `sched1-*` |
| CAM-KEEP | 26 khoá `camera_*` trùng khớp trước/sau cài | [ĐO] |
| GUIDE-1 | **Đạt** hai bên (n/10 khung, lệch ≤ 1 px) | [ĐO ảnh] |
| GUIDE-2 | Hình xoay 90° ⇒ vạch ngang sai trục; không xoay ⇒ phải nấc 7 ≈ 20 cm, trái giữa 7–8. Owner: *"ò, dẹp vạch đi"* | [ĐO] + [SUY hình học] |
| CAM-B5 | 3 lượt trái ⇄ phải rồi `none`: 0 `BufferQueue has been abandoned`, 0 crash | [ĐO] `camb5-logcat.txt` |
| CAM-SEAL | Xi-nhan thật trái/phải đúng dải, trên cụm; cấu hình owner `camera_view_right=MIRROR_LEFT` | [ĐO] `final-logcat-120941.txt` |
| AVM-PANO | `pano_sdk.txt` Permission denied; không có `panorama_online` ⇒ đóng hẳn | [ĐO] `avm-*` |
| GMAPS-POP | Hộp "Thoát chế độ đi theo chỉ dẫn?" khoá nút "Có" với `flg=0x10000000`; `-f 0x10008000` (thêm `CLEAR_TASK`) ⇒ đổi đích thẳng, owner xác nhận | [ĐO] + [ĐO owner] |
| SCHED-2/3 · SCHED-4 · GUIDE-3…5 · CAM-F16 · CAM-PREVIEW-HOLD · TRIP-HAL · PKGS · tệp `crash-*` sau cài | **chưa đo** (SCHED-2/3 owner bỏ) | — |
| §10 OTA | **Không áp dụng — 2.82 không đăng**; gom vào 2.83 (A–E), spec `docs/specs/kachi-283-key-heal-acc-off.html` | [SUY] phiên đề xuất 12:01:24, owner không phản đối và duyệt kế hoạch 2.83 (12:34:54) — không có câu owner nói thẳng về OTA 2.82 |

**Lỗi công cụ của phiên (ghi thẳng):** (1) đếm Bound bằng `grep "Bound services"` **một dòng** ⇒ báo sai "không Bound" (AOSP in mỗi dịch vụ Bound thêm ra một dòng riêng; owner bắt lỗi *"đang check sai chỗ rồi"*) — hàm `a11y_read` của runbook này grep nhãn trên cả tệp nên không dính, phiên đã không dùng nó; (2) chạy `logcat -c` trước CAM-B5, trái §0.2 #4 — log buổi sáng đã lưu trước nên không mất bằng chứng a11y; (3) chỉ sai đường tới màn Chẩn đoán (nút gỡ từ 21/09 — chính runbook đã ghi ở §1.3, cuối tệp); (4) KEY-4 không `tee` ra tệp; (5) SL6-BASE chụp trọn `getprop` thay vì 3 lệnh (dữ liệu thừa giữ ngoài repo).

**Đọc phần dưới thế nào:** §3.3 (dự đoán từ mã, *"chưa từng đo"*) đã bị số đo thật vượt qua — xem KEY-6 ở trên; §10.1–10.3 (đăng OTA) không áp dụng vì 2.82 không đăng ([SUY] phiên đề xuất, owner không phản đối).

---

## 0. Một trang đầu

§0 đọc một mình là đủ để bắt đầu; mỗi bước trỏ tới mục chi tiết. Bối cảnh hôm qua (§1) đã dời xuống cuối tệp, ngay trước §11.

### 0.1 Thứ tự bắt buộc

Khối A phải xong **trước** khối B. Lý do: cài đè 2.82 dọn trạng thái kẹt, nên sẽ xoá bằng chứng của đêm qua (28→29/09). `onPackageUpdateFinished` gọi `mBindingServices.removeIf(<gói>)` rồi gắn lại [ĐO AOSP `AccessibilityManagerService.java:398-415`; hành vi riêng của ROM: CHƯA BIẾT]. Cài đè cũng giết cả 3 tiến trình, nên mất luôn mẫu vật bộ nhớ.

| # | Khối | Việc | Ai | Phút |
|---|---|---|---|---|
| 0a | **Hỏi trước tiên** | Từ lúc đỗ tối 28/09 tới giờ anh đã làm gì với xe: đã bật/lái chưa (mấy giờ; **bây giờ** xe đang bật hay tắt) · đã bấm 328/305 chưa (ăn/chết) · đã bấm *Sửa ngay*, gạt *Nhận nút vật lý*, bấm *Khởi động lại launcher* chưa · đã khởi động lại đầu xe chưa · đã cài gì chưa. Tra bảng Z0–Z5 ở **§2.0** để biết phép đo phím còn giá trị tới đâu. **Xe đang bật sẵn ⇒ bỏ KEY-0, tạo `env.zsh` rồi làm KEY-1 ngay** | anh + Claude | 2 |
| 0 | Trước khi bật xe (sáng nay) | §2.1: kiểm APK, tạo `env.zsh` (§2.3), hỏi anh các câu ở §2.1 mục 3, **chốt mạng adb** (điểm phát điện thoại hay của xe; nếu là điện thoại thì GIỮ Wi-Fi + điểm phát điện thoại bật suốt buổi). **Không cắm cáp** CarPlay/AA | anh + Claude | 5 |
| 1 | **A · KEY-0** | Bật xe, ghi **T0** (lúc màn sáng) và giờ bấm Start. **Không chạm gì 90 s**. Chụp ảnh điện thoại màn nhà (ô có app không). T0 + 90 s bấm **một lần phím 328**, ghi ăn/không | anh | 2 |
| 2 | A · KEY-1 | Nối adb (§2.2). **Chụp vòng đệm logcat trước mọi lệnh khác**, rồi mở logcat chạy suốt buổi | Claude | 3 |
| 3 | A · KEY-2 | Bộ dump **chỉ đọc**: tiến trình, hai đồng hồ, accessibility, services, stack, display, alarm, meminfo ×3, procstats, phiên bản | Claude | 4 |
| 4 | A · KEY-3 | Kéo usage log (`kachi-logs/usage-*.log` từ 28/09 15:00) về và lọc dòng phím | Claude | 3 |
| 5 | A · KEY-4 | Thử mở màn Chẩn đoán bằng adb, ghi nguyên văn kết quả | Claude | 1 |
| 6 | A · KEY-5 | ⚠ Đọc cảnh báo đầu KEY-5 trước. Anh bật **Chế độ kiểm thử** (đi tay trong Cài đặt) → đọc prefs a11y + **SCHED-0** (chụp lịch) + prefs camera | anh bật, Claude đọc | 5 |
| 7 | A · KEY-6 | Tra **cây quyết định §3.6**, ghi kết luận có mức bằng chứng | Claude | 3 |
| 8 | A · KEY-7 | *(Chỉ khi KEY-6 ra "kẹt mà không tự chữa", và anh đồng ý)* bấm **Kiểm tra / Sửa ngay** rồi đo lại | anh + Claude | 5 |
| 9 | B · INST + SL6-BASE | Cài 2.82 bằng `install -r` (§4), đọc lại versionCode. Ngay sau đó SL6-BASE: ba lệnh `getprop` chỉ đọc (§7) | Claude | 4 |
| 10 | B · SCHED-1 + CAM-KEEP | Nâng cấp phải giữ nguyên lịch (§5). **Không đạt thì không OTA**. Ngay sau đó CAM-KEEP: `diff` khoá `camera_*` trước/sau INST (§5) | Claude + anh | 5 |
| 10b | B | Gia hạn cầu kiểm thử: anh gạt *Chế độ kiểm thử qua adb* TẮT → BẬT (đừng nhầm với *Nhận nút vật lý*). `br --es cmd state` phải cho `test_mode_minutes_left` ≥ 40 (§2.5) | anh + Claude | 1 |
| 11 | C · GUIDE-1, 2 + CAM-B5 | Vạch chuẩn: nấc 1→9, canh mốc 30 cm / 1 m; đổi trái ⇄ phải 3 lần cho CAM-B5 (§6) | anh + Claude | 17 |
| 12 | D | Kiểm cửa sổ kiểm thử trước (còn < 25 phút ⇒ gạt lại). SCHED-2, 3 · GUIDE-3..5 · CAM-F16 · CAM-SEAL · CAM-PREVIEW-HOLD (§6; bỏ thì tóm tắt ghi "chưa đo") | | 21 |
| 13 | E | Kiểm cửa sổ kiểm thử lần nữa. Đo ngắn: AVM-PANO · GMAPS-POP · TRIP-HAL · PKGS · SCHED-4 · dọn (§5, §6, §8) | | 14 |
| 14 | F | Mang về (§9) → quyết OTA (§10) | anh quyết; Claude chỉ chuẩn bị | 5 |

Bước 1 → 14 khoảng **93 phút**; bước 1 → 11 khoảng **53 phút**. Chỉ có 60 phút thì làm 1 → 11 (vẫn gồm CAM-B5, điều kiện 4 của §10.1, và SL6-BASE ở bước 9), phần còn lại ghi "chưa đo". Bỏ bước nào thuộc §10.1 thì buổi này **không** đủ điều kiện OTA. Bước 0a luôn làm trước, kể cả khi thiếu giờ.

### 0.2 Năm điều KHÔNG làm

1. **Trước khi xong KEY-2..KEY-5**, không làm bất kỳ việc nào sau đây, vì mỗi việc đều phá bằng chứng của đêm:
   - bấm *Kiểm tra / Sửa ngay*;
   - gạt *Nhận nút vật lý* TẮT→BẬT;
   - cài APK (kể cả OTA, kể cả nút *Kiểm tra cập nhật*);
   - khởi động lại đầu xe.

   Hai thao tác đầu là `userAsked=true`. Nếu đang kẹt, chúng force-stop ngay và bỏ qua mọi cổng [ĐO mã `AccessibilityHealGates.kt:103`, `ClusterNavBridgeKeys.kt:48,81`].

   **Cả buổi** (không chỉ trước KEY-5), không bấm *Khởi động lại launcher* hay *Dừng toàn bộ dẫn đường*. Hai nút này nằm ở *Cài đặt › Hệ thống & quyền › Bảo trì*, **cùng màn và ngay trên** công tắc Chế độ kiểm thử mà anh phải cuộn tới ở KEY-5 (`SettingsSections.kt:278-284` so với `:304-305`). Nút *Khởi động lại launcher* gọi `Process.killProcess` sau 300 ms (`ClusterNavBridge.kt:206-214`), nên mất PID/ETIME và mẫu bộ nhớ, sinh dấu hiệu F giả, app trong ô rơi thành mảng đen. Theo đúng cơ chế AOSP đang điều tra (`:4114-4117`: mối nối đang gắn bị đứt thì component bị đẩy vào `mBindingServices`), giết tiến trình giữa lúc dịch vụ đang gắn còn có thể **tự gây ra** ca kẹt [SUY].
2. **Không `am force-stop com.byd.launcher` trơn**, cả buổi.
   - `onHandleForceStop` gỡ dịch vụ khỏi danh sách enabled rồi **ghi bền** [ĐO AOSP `:454-482`, `:475-477`], nên phím chết cho tới khi có người lắp lại.
   - Cũng cấm: `pm clear`, `pm disable-user`/`enable`, tự gõ `settings put secure enabled_accessibility_services`, `am stack move/remove`, `wm size/density/overscan`, và mọi lệnh quét kiểu "mọi display ≥ 1" (CLAUDE.md §4, §5).
3. **Không mở app khách nào trước khi xong KEY-0..KEY-2.** Tính cả phím **305** (trợ lý bên thứ ba), CarPlay/AA, app trong ô, **kể cả app do lịch tự mở** (§2.1 mục 3).
   - Một app khách đang hiện trên màn chính hoặc trong ô sẽ làm đường tự chữa trả `NONE` [ĐO mã `AccessibilityHealGates.kt:104`, `StackParse.kt:205-210`].
4. **Không `logcat -c` cả buổi.** Vòng đệm là bằng chứng. Thay vào đó dùng tệp logcat chạy liên tục và lọc theo giờ.
5. **Không gõ lệnh hay thao tác màn khi xe đang lăn bánh** (CLAUDE.md §11).
   - Làm hết phần adb khi xe đỗ. **Không cắm cáp** CarPlay/AA: cắm vào thì đầu xe tắt Wi-Fi và mất adb (CLAUDE.md §11).
   - Mạng adb xem §2.1 mục 4. Nếu Mac và xe cùng vào điểm phát điện thoại thì **không** tắt Wi-Fi/điểm phát điện thoại.
   - **Không push `main`** (push `main` = đăng OTA cho **mọi** xe bật tự cập nhật, kể cả SL6 của anh em) khi chưa đủ **sáu** điều kiện ở §10.1. Điều kiện 5 là câu OK của anh, nói **sau** khi đã nghe cảnh báo ở §10.2 bước 0. Ngoại lệ duy nhất: commit **chỉ tài liệu** ở §10.3 (không đụng tệp APK nên không đổi kênh), và chỉ khi anh đồng ý.

---

## 2. Chuẩn bị

### 2.0 Xe đã được dùng từ tối 28/09 chưa? (bước 0a, hỏi TRƯỚC mọi thứ)

Runbook viết đêm 28→29/09 với giả định sáng nay anh lên xe khi xe còn nguyên trạng từ lúc đỗ. Sáng nay anh có thể đã dùng xe trước khi đọc tệp này. Hỏi anh năm câu, ghi nguyên câu trả lời kèm giờ vào tệp tóm tắt (dòng `0a`):

1. Từ lúc đỗ tối 28/09 tới giờ, xe đã **bật hay lái** chưa? Bật lúc mấy giờ, tắt lúc mấy giờ? **Bây giờ** xe đang bật hay tắt?
2. Đã bấm **phím 328 / 305** chưa? Lúc mấy giờ, phím ăn hay chết?
3. Đã bấm *Kiểm tra / Sửa ngay*, gạt *Nhận nút vật lý*, hay bấm *Khởi động lại launcher* chưa? Lúc mấy giờ?
4. Đã **khởi động lại đầu xe** chưa?
5. Đã **cài** gì chưa (2.82 hay app khác)? Đã mở app dẫn đường, CarPlay/AA, hay app trong ô chưa?

Trạng thái kẹt chỉ nằm trong RAM của `system_server`: `mBindingServices` là một `HashSet` của `UserState` [ĐO AOSP `AccessibilityManagerService.java:3986`]. Vì vậy mỗi thao tác phá nó theo một cách khác nhau:

| Ca | Anh đã làm | Trạng thái kẹt (Binding, DEAD) | Còn đọc được | Phép đo phím còn giá trị tới đâu | Làm gì |
|---|---|---|---|---|---|
| **Z0** | Chưa đụng gì | Nguyên | Tất cả | Đủ | §0.1 từ bước 0 |
| **Z1** | Chỉ bật/lái (có thể đã bấm 328/305, mở app) | **Còn**, trừ khi đường tự chữa đã tự chạy lúc bật xe. Đó chính là thứ cần đo | Usage log trên thẻ (`usage-<ms>.log`, `KachiLog.kt:66`) giữ dòng của đêm lẫn của lần bật sáng nay; mốc `a11y_forcestop_elapsed`; ETIME | Câu 1–3 của §3.1 vẫn trả lời được. **Mất** phép thử P ở T0 + 90 s: thay bằng lời anh kể ở câu 2, ghi [ĐO owner, nhớ lại]. Lần bật đầu đã quá khoảng 30 phút ⇒ `am_kill` nhiều khả năng đã trôi khỏi đệm events (khoảng 32 phút, [ĐO 28/09]). `Force stopping` nằm ở đệm **system** (`Slog.i`, [ĐO AOSP `ActivityManagerService.java:4642`]), thời gian giữ [CHƯA BIẾT] ⇒ đọc `key1-logcat-buffers.txt` rồi mới kết luận. App anh mở lúc lái có thể đã đóng cổng ô (5c): ghi là **nhiễu do sử dụng**, không phải phát hiện thiết kế. PSS vẫn so được nếu ETIME cho thấy tiến trình sống từ hôm qua | Xe **đang bật** ⇒ bỏ KEY-0, tạo `env.zsh` (§2.3), nối adb và làm KEY-1 **ngay**. Xe **đang tắt** ⇒ KEY-0 như thường (lần bật tới là một đợt thức mới), ghi thêm giờ bật đầu tiên sáng nay là **T0'**. Đọc thêm ghi chú Z1 ở §3.6 (F-đêm) |
| **Z2** | Bấm *Sửa ngay* hoặc gạt *Nhận nút vật lý* | Nếu đang kẹt lúc bấm: force-stop rồi lắp lại ngay, bỏ qua mọi cổng (`userAsked=true`, `AccessibilityHealGates.kt:103`) ⇒ **mất**. Không kẹt lúc bấm ⇒ chỉ toggle hoặc không làm gì (§3.7), Binding không đổi | Usage log: các dòng trước giờ bấm (`STUCK (grant-tu-dong)` của đường tự động). Dòng lúc bấm (`STUCK (grant-tay)`) **thường KHÔNG có**: nhật ký chỉ ra logcat khi trạng thái **đổi** (`A11yBindJournalStore.kt:69,82`), mà lúc kẹt mỗi nhịp watchdog đã để lại STUCK. Dòng W `a11y KẸT … tự force-stop` **giống hệt** cho tay và tự động (`NavConnect.kt:302`) ⇒ tách chỉ bằng **giờ**: W force-stop cách giờ anh bấm ≤ vài giây ⇒ của lần bấm tay. Giờ bấm là [ĐO owner, nhớ lại] ⇒ ghi kèm sai số | K vẫn lấy được từ usage log. F và S sau giờ bấm là **của lần bấm tay**, không tính vào V-oncar-1. Đường tự động chỉ được công nhận khi usage log có W `a11y KẸT … tự force-stop` **trước** giờ bấm. Không so PSS | KEY-1..KEY-3, KEY-8. KEY-2 chỉ mô tả trạng thái sau khi bấm. Rồi sang khối B |
| **Z3** | Bấm *Khởi động lại launcher* | Giết tiến trình **không** gỡ mục kẹt [ĐO AOSP, §1.2 hàng PERF-STANDBY-FRESH], và còn có thể tự tạo ca kẹt [SUY, §0.2 ①] | Usage log (có tệp mới từ giờ bấm) | ETIME và PSS mất giá trị; mọi dấu F sau giờ bấm là do thao tác. Còn kẹt hay không thì KEY-2 vẫn đo được | Ghi giờ bấm, đi tiếp như Z1 |
| **Z4** | Khởi động lại đầu xe | `system_server` mới ⇒ Binding và DEAD của đêm **mất**; hai đồng hồ R, U cũng đếm lại từ lần khởi động này | Usage log tới lúc tắt máy | Chỉ còn usage log: K/F/S đọc từ các dòng **trước** giờ khởi động lại (đủ log ⇒ hàng 2b [ĐO]; thiếu ⇒ "chưa kết luận"). Giờ máy khởi động ở K2-1 sẽ trùng giờ anh khởi động lại, nên hàng 0 §3.6 **không** áp dụng cho đêm qua | KEY-1..KEY-3, KEY-8, rồi khối B |
| **Z5** | Cài 2.82 (bằng bất kỳ cách nào) | `removeIf` khi thay gói ⇒ **mất** [ĐO AOSP `:398-415`]; cả 3 tiến trình đã chết | Usage log tới lúc cài | Phím: K/F/S chỉ đọc từ usage log trước giờ cài (đủ log ⇒ hàng 2b [ĐO]); KEY-2 mô tả trạng thái sau cài, note `sau-chua-*` do cài sinh ra là giả (§3.5). **SCHED-0 trên 2.81 không còn làm được** vì phép di trú đã chạy: SCHED-1 chỉ kiểm được tiêu chí (0)(1)(2); (3)(4) so với ảnh hoặc trí nhớ của anh về danh sách lịch, ghi [ĐO owner] | B0 sẽ ra 183 · 2.82. Bỏ INST, ghi rõ trong tóm tắt |

Hai điều đúng trong mọi ca:
- Dùng xe **không** làm mất nhật ký bền. Usage log nằm trên thẻ, chỉ bị `DiagStorageCap` dọn khi cả cây vượt khoảng 150 MB (KEY-3). `a11y-bind.log` giữ 200 dòng nhưng bản phát hành không đọc được (1.3 #2).
- Chỉ bấm *Kiểm tra cập nhật* thì không cài gì khi xe còn chạy 2.81: kênh đang là 2.81, và mã chỉ báo có bản mới khi bản trên kênh lớn hơn bản đang cài [ĐO `UpdateChecker.kt:87`].

### 2.1 Trước khi bật xe (sáng nay, 5 phút)

Ca Z1 mà xe đang bật: chỉ làm mục 2 (tạo `env.zsh`) và mục 4 (chốt mạng) rồi sang KEY-1 ngay; các câu ở mục 3 hỏi sau KEY-2.

1. APK sẵn ở máy:
   - `shasum -a 256 apk/Kachi-2.82-CHUA-DANG.apk` phải ra `146411e8…2ec0`.
   - `apk/Kachi-2.81-release.apk` giữ lại để lùi.
2. Tạo `env.zsh` (§2.3). Nếu anh chạy lệnh ở terminal thật thì mở hai terminal ở gốc repo (terminal 1 cho logcat liên tục, terminal 2 để gõ lệnh) và `source` tệp đó ở **cả hai**.
3. Hỏi owner:
   - Hồ sơ đang dùng có app nào trong ô không?
   - **(Bắt buộc, cần cho hàng 0 §3.6)** Tối 28/09 xe tắt (đỗ) lần cuối lúc mấy giờ? Sau 15:39 ngày 28/09 anh có tắt hẳn hay khởi động lại đầu xe không? (Sáng nay đã bật lại chưa thì hỏi ở §2.0.)
   - Phím-thoại có đang bật không? (Hỏi theo trí nhớ. **Không** bật xe hay kéo rèm để xem: bật xe là mất T0 (mục 4), kéo rèm là chạm màn, trái KEY-0 bước 2 và 4. Sự thật đọc ở K2-4 và `voicekey_enabled` ở KEY-5.)
   - Có luật *Tự dẫn đường theo lịch* nào đang bật với khung giờ trùm giờ lên xe sáng nay không? Có thì lên xe **ngoài** khung đó; không dời được thì ghi lại. **Không** sửa luật trước SCHED-0 (SCHED-0 cần lịch nguyên trạng). Ca Z1 mà anh đã bật xe trong khung đó ⇒ lịch có thể đã tự mở app dẫn đường: tìm `KachiAutoNav` ở KEY-3. Lý do [ĐO mã]: ngay trên 2.81, `AutomationService` gọi `ScheduledNavApplier.tick` mỗi 60 s (`git show 8d0d161:…/automation/AutomationService.kt:114,225`), luật tới lượt thì mở app dẫn đường (tag `KachiAutoNav`, `ScheduledNavApplier.kt:51,79`). App đó `visible=true` trên display 0 ⇒ cổng ô đóng, đường tự chữa trả NONE mà không ai chạm gì (`AccessibilityHealGates.kt:104`).
   - Mac và đầu xe nối nhau qua mạng nào (mục 4)?
4. Mạng adb:
   - [ĐO transcript] Ngày 28/09 có 28 lệnh cầu `nc <ip-xe> 5555` nhắm dải địa chỉ mặc định của Personal Hotspot iPhone, 8 lệnh nhắm dải khác ⇒ nhiều khả năng Mac và đầu xe cùng vào **điểm phát của điện thoại** [SUY]. `adb-car-tunnel-macos.md:5` ghi cả hai cách nối; `oncar-runbook-2.69.md:15` ghi "WiFi laptop = hotspot xe".
   - Qua điểm phát điện thoại ⇒ **giữ** điện thoại trong xe, Wi-Fi và điểm phát **bật suốt buổi**. Qua điểm phát của xe ⇒ Mac vào điểm phát xe như buổi 2.69.
   - **Không cắm cáp** CarPlay/AA: đầu xe tắt Wi-Fi, mất adb (CLAUDE.md §11). CarPlay/AA không dây trên Seal: [CHƯA BIẾT]. Nếu lo thì chỉ tắt **Bluetooth** điện thoại **sau** khi `adb devices` đã ra `device`, rồi chạy lại `adb devices` ngay.
   - App chiếu lên màn chính cũng là **app khách**, nên đóng cổng tự chữa [SUY mã].
   - **Không** bật xe chỉ để thử mạng: lần bật đầu tiên sáng nay chính là KEY-0 (T0); bật rồi tắt sẽ tạo một đợt thức ngắn trước T0 [SUY]. Mạng chỉ kiểm được khi xe đã thức (`nc -z` ở §2.2, sau KEY-0).

### 2.2 Nối adb qua cầu nc (Mac)

Nguồn: `docs/diagnostics/adb-car-tunnel-macos.md:31-38`, memory `kachi-adb-car-tunnel`. `adb` không nằm trong PATH. Nếu Claude Code chạy các lệnh này thì Bash phải **tắt sandbox**.

```zsh
source docs/diagnostics/oncar-2026-09-29/env.zsh
mkdir -p $S; rm -f $S/adbfifo; mkfifo $S/adbfifo
(nc -l 127.0.0.1 15555 < $S/adbfifo | nc <ip-xe> 5555 > $S/adbfifo) > /dev/null 2>&1 &
sleep 1
~/Library/Android/sdk/platform-tools/adb connect 127.0.0.1:15555     # → connected to 127.0.0.1:15555
~/Library/Android/sdk/platform-tools/adb devices                      # phải thấy 127.0.0.1:15555  device
```

- Cầu chạy nền bằng `&` như các buổi 16–17/09 [ĐO memory `kachi-adb-car-tunnel`]; Claude cũng có thể gọi khối dựng cầu bằng `run_in_background: true`. Nếu công cụ chặn `sleep` chạy tiền cảnh thì tách `adb connect` sang lượt Bash kế tiếp.
- `NO_CLOBBER` (§2.3) không chặn `> /dev/null` và `> $S/adbfifo` (FIFO) [ĐO thử zsh 5.9 trên Mac này].
- Cầu giữ đúng **một** kết nối TCP. Rớt thì dựng lại **cả khối**: `adb disconnect 127.0.0.1:15555; pkill -f "nc -l 127.0.0.1 15555"; rm -f $S/adbfifo`, rồi chạy lại khối trên và **mở lại logcat liên tục** (KEY-1, tệp mới).
- `unauthorized` ⇒ anh bấm Allow trên màn xe.
- `nc -z -w 2 <ip-xe> 5555` không thông ⇒ xe đổi IP hoặc chưa bật adb TCP.
- Cách gốc: *System Settings › Privacy & Security › Local Network*, bật cho app terminal.

### 2.3 `env.zsh`: hàm, biến và thư mục bằng chứng (tạo trước khi bật xe, ở gốc repo)

Claude Code **không giữ** hàm và biến giữa các lượt Bash (mỗi lượt là một shell mới). Vì vậy mọi hàm nằm trong một tệp, và:
- **Mỗi lượt Bash của Claude mở đầu bằng** `source docs/diagnostics/oncar-2026-09-29/env.zsh;` (cwd = gốc repo, sandbox tắt).
- Anh chạy ở terminal thật thì `source` một lần ở **mỗi** terminal.
- Lệnh chạy suốt buổi (logcat liên tục) Claude gọi bằng `run_in_background: true`, không dùng `&` trong một lượt tiền cảnh.

Tệp nằm trong `$O`, bị `.gitignore:63` (`oncar-*/`) loại [ĐO `git check-ignore`]. Dùng **hàm**, không gán lệnh vào biến (zsh không tách từ). macOS không có `timeout`.

```zsh
mkdir -p docs/diagnostics/oncar-2026-09-29
cat >| docs/diagnostics/oncar-2026-09-29/env.zsh <<'EOF'
# env.zsh — nạp ở ĐẦU MỖI lượt Bash (Claude) và một lần ở mỗi terminal (anh).
O=${0:A:h}                 # đường tuyệt đối của thư mục chứa tệp này (zsh) — không ghi tên máy vào repo
S=/tmp/kachi-adb
setopt NO_CLOBBER          # '>' từ chối ghi đè tệp đã có; cố ý ghi đè thì dùng '>|'. '>>' vào tệp CHƯA có cũng bị từ chối
adbx() { ~/Library/Android/sdk/platform-tools/adb -s 127.0.0.1:15555 "$@"; }
xe()   { adbx shell "$@"; }
br()   { xe am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST "$@"; }

# Đo lại bộ ba của KEY-2 vào tệp MỚI có giờ — không bao giờ đè bản dump của đêm.  dùng: remeasure key7 | key2b | inst
remeasure() {
  local t=$1-$(date +%H%M%S)
  xe dumpsys accessibility > $O/$t-acc.txt
  xe dumpsys activity services com.byd.launcher > $O/$t-svc.txt
  xe ps -A -o PID,ETIME,NAME | grep com.byd.launcher > $O/$t-ps.txt
  echo "đã ghi $O/$t-{acc,svc,ps}.txt"
}

# Đọc một cặp dump accessibility + services.  dùng: a11y_read $O/key2-acc.txt $O/key2-svc.txt
a11y_read() {
  echo "== Binding (nguyên văn mọi mục):"; grep -o 'Binding services:.*' $1
  echo -n "== KẸT (mục của mình trong Binding; 1 = KẸT, 0 = không): "
  grep 'Binding services:' $1 | grep -c 'com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService'
  echo -n "== Có trong Bound (label=ClusterNav): "; grep -o 'label=ClusterNav[^,]*' $1 | head -1; echo
  echo -n "== Dòng ConnectionRecord DEAD của mình (0 = sạch): "
  grep 'ConnectionRecord' $2 | grep 'NavAccessibilityService' | grep -c ' DEAD '
  echo "== ServiceRecord:"
  awk '/ServiceRecord\{.*NavAccessibilityService\}/{f=1;print;next} /\* ServiceRecord\{/{f=0} f' $2 | grep -E 'ServiceRecord|app=|requested=|DEAD'
}

# Đọc một khoá trong lời đáp 'prefs' (chuỗi JSON lồng vẫn in trọn).  dùng: pref_get $O/key5-prefs-cn.txt voicekey_bindings
pref_get() { python3 - "$1" "$2" <<'PY'
import json, re, sys
t = open(sys.argv[1], encoding='utf-8').read()
m = re.search(r'data="(.*)"', t, re.S)
if not m: sys.exit('KHONG co data= : lenh hong hoac cau chua nhan, xem tep')
d = json.loads(m.group(1))
if not d.get('ok'): sys.exit('ok = %s | error = %s' % (d.get('ok'), d.get('error')))
v = d.get('values') or {}
print(sys.argv[2], '=', v[sys.argv[2]] if sys.argv[2] in v else '<vang>')
PY
}

# Kiểm lịch: lịch sống (clusternav_prefs) + mốc + ảnh từng hồ sơ (kachi_workspace).  dùng: sched_check <tệp lời đáp prefs>
sched_check() { python3 - "$1" <<'PY'
import json, re, sys, hashlib
t = open(sys.argv[1], encoding='utf-8').read()
m = re.search(r'data="(.*)"', t, re.S)
if not m: sys.exit('KHONG co data= : lenh hong hoac cau chua nhan, xem tep')
d = json.loads(m.group(1))
print('ok =', d.get('ok'), '| error =', d.get('error'), '| file =', d.get('file'))
v = d.get('values') or {}
h = lambda s: hashlib.sha1(s.encode()).hexdigest()[:12]
U = {'\\': '\\', 'n': '\n', 'r': '\r', 'p': '|', 'c': ','}
def unesc(s):
    o, i = [], 0
    while i < len(s):
        if s[i] == '\\' and i + 1 < len(s): o.append(U.get(s[i+1], s[i+1])); i += 2
        else: o.append(s[i]); i += 1
    return ''.join(o)
if d.get('file') == 'clusternav_prefs':
    r = v.get('nav_automation_rules')
    print('LICH SONG', 'VANG' if r is None else 'sha1 = %s dai %d' % (h(str(r)), len(str(r))))
if 'migrated_nav_schedule_v1' in v: print('MOC =', v['migrated_nav_schedule_v1'])
elif d.get('file') == 'kachi_workspace': print('MOC vang')
SUF = '____cn__clusternav_prefs'
for k in sorted(v):
    if not k.endswith(SUF): continue
    f = [l.split('|', 2) for l in str(v[k]).split('\n')]
    r = [x for x in f if len(x) == 3 and x[1] == 'nav_automation_rules']
    if not r: print('ANH', k[:-len(SUF)], 'THIEU lich')
    elif r[0][0] == 'n': print('ANH', k[:-len(SUF)], 'NULL lich (tuong minh)')
    else: print('ANH', k[:-len(SUF)], 'CO lich sha1 =', h(unesc(r[0][2])))
PY
}
EOF
source docs/diagnostics/oncar-2026-09-29/env.zsh; echo "O=$O"
```

Vì sao các hàm này như vậy (mỗi dòng đều đã chạy thử trên tệp giả đúng dạng, zsh 5.9 + python3, tối 28/09):
- `NO_CLOBBER`: các bước đo lại (KEY-7, §3.6 hàng 6, §4) trước đây ghi `>` vào đúng `key2-*.txt`, tức đè mất bản dump của đêm. Nay đo lại chỉ qua `remeasure`, tên tệp có giờ; `>` vô tình vào tệp cũ thì zsh báo `file exists`.
- `a11y_read` sửa ba lệnh đọc sai của bản trước [ĐO thử]: (1) `grep -o 'Binding services:{[^}]*}'` dừng ở `}` đầu nên chỉ in **mục đầu** của `{{a/b}, {c/d}}` (dạng thật: fixture `AccessibilityBindingStuckTest.kt:27-29`); (2) `grep -c 'NavAccessibilityService.*DEAD'` luôn ra 0 (1.3 #11); (3) `grep -A12` hụt dòng `requested=…`, vốn nằm ở dòng +14/+15 sau header [ĐO transcript: ServiceRecord thật của `NavNotificationListener` cùng gói có `requested=` ở dòng +15].
- `pref_get`: `voicekey_bindings` là chuỗi JSON lồng, đã thoát `\"` (`TestBridgeJson.kt:61-67`, `Prefs.kt:137`); `grep '"voicekey_bindings":"[^"]*'` chỉ in được `[{\`.
- `sched_check`: xem §5.

Cách gọi cầu kiểm thử:
- Cần **cả `-n` lẫn `-a`**: receiver kiểm action (`KachiTestBridge.kt:57`). Chỉ có `-a` thì trả `result=0` rỗng và lặng lẽ không chạy [ĐO xe 20/09].
- Lời đáp có dạng `result=0, data="{…}"` (`TestBridgeReply.kt:120`); lỗi thì `result=1`, `data` mang `"ok":false,"error":"<mã>"` (`TestBridgeReply.kt:47`). Lệnh `prefs` trả `"file"` và `"values"` (`TestBridgeNoHome.kt:27`). Bản JSON cũng được lưu ở `/sdcard/Android/data/com.byd.launcher/files/test/`, và 3 000 ký tự đầu vào logcat tag `KachiTest` (`TestBridgeReply.kt:57,123`).
- Tên extra:
  - lệnh `--es cmd`;
  - đối số `--es name` (không có `arg`);
  - giá trị của `prefs_set` là `--es text`;
  - số ô là `--ei n`.
- Không có `prefs_get`; lệnh đọc là `prefs --es file <tệp>`.
- Getter HAL không tham số thì **không** truyền `--es args`.
- Nếu công cụ chặn `sleep` chạy tiền cảnh, tách lệnh có `sleep` thành hai lượt.
- **Chụp hỏng thì chụp lại bằng `>|`.** Vì `NO_CLOBBER`, mỗi lệnh `br … > $O/<tệp>.txt` chỉ ghi được một lần; chạy lại bằng `>` sẽ báo `file exists` [ĐO thử zsh trên Mac này]. Lần chụp nào ra `ok = False` hoặc `KHONG co data=` (từ `pref_get`/`sched_check`), hay lời đáp mang `"ok":false` / `"error":"<mã>"` (vd `test_mode_off` vì công tắc chưa kịp bật, `home_not_running`), hoặc tệp rỗng vì cầu rớt: sửa nguyên nhân rồi chạy lại **đúng** lệnh đó với `>|` (ghi đè có chủ đích tệp lỗi).
  - Luật này **chỉ** cho tệp lời đáp `br …`. Dump KEY-2 không bao giờ ghi đè; đo lại thì dùng `remeasure` (lý do `NO_CLOBBER`, dưới khối `env.zsh`).
  - Riêng SCHED-1: trước khi chụp lại, `br --es cmd state` phải trả ok, tức màn nhà đã lên. `state` cần màn nhà, còn `prefs` thì không (`KachiTestBridge.kt:141-146`, `TestBridgeNoHome.kt:27`) [ĐO mã] ⇒ `prefs` chạy trước khi màn nhà lên có thể đọc dữ liệu **trước** phép di trú (§5) [SUY].

### 2.4 Đọc phiên bản đang chạy (B0; không đoán, CLAUDE.md §9)

**B0 = dòng đầu của KEY-2** (`key2-version.txt`). **Không** chạy riêng trước KEY-1: KEY-1 phải là lệnh đầu tiên.

- Kỳ vọng `versionCode=182`, `versionName=2.81`, `lastUpdateTime=2026-09-28 15:1x` hoặc sớm hơn [SUY từ ETIME: tiến trình 2.81 đầu tiên lên khoảng 15:13].
- Nếu khác: dừng lại, ghi lại, hỏi owner. Cây quyết định §3.6 chỉ viết cho 2.81. Ra 183 · 2.82 ⇒ ca Z5 (§2.0).

### 2.5 Chế độ kiểm thử (chỉ bật được bằng tay)

- **Đi tay**: *Cài đặt › Hệ thống & quyền* › cuộn qua khối *Bảo trì* › *Nâng cao* › **Chế độ kiểm thử qua adb** (`SettingsSections.kt:304-305`, `strings_kachi.xml:522,526`).
  - Khi cuộn qua *Bảo trì*: vuốt ở chỗ không có nút, **không chạm** *Kiểm tra cập nhật* · *Dừng toàn bộ dẫn đường* · *Khởi động lại launcher* (`SettingsSections.kt:278-284`, 0.2 ①). Trên màn này **chỉ** chạm công tắc Chế độ kiểm thử.
- **Không dùng deep link `am start -n …/KachiHomeActivity`**.
  - Mã deep link có sẵn [ĐO `KachiHomeWiring.kt:262-266`; `KachiHomeActivity` là `singleTask`, `onNewIntent` → `openSettingsGroup`, `KachiHomeActivity.kt:341-345`, `AndroidManifest.xml:119-121`].
  - Nhưng intent `-n …/KachiHomeActivity` không mang `CATEGORY_HOME` nên nhiều khả năng dựng một instance **thứ hai** thay vì gửi `onNewIntent` cho instance HOME [SUY: AOSP bỏ qua task khác loại activity khi tìm task để dùng lại]. Transcript 28/09 (khoảng 09:55) có một bản `am stack list` với `taskId=80: com.byd.launcher/…KachiHomeActivity` tồn tại song song task home `…/KachiHome` [ĐO transcript; nguồn xe hay máy ảo: chưa chốt].
  - Mỗi `onCreate` đều chạy `PermissionPreflight.runAndReport`, hàm này gọi `grantAccessibility` tự động khi phím-thoại bật mà chưa gắn (`PermissionPreflight.kt:288-293`; gọi từ `KachiHomeActivity.kt:327`, `KachiHomeWiring.kt:470,491`) ⇒ tự kích một lượt chữa.
  - Buộc phải dùng deep link thì nhắm alias HOME [SUY, chưa chạy trên xe], rồi kiểm có sinh instance thứ hai không:
    ```zsh
    xe am start -a android.intent.action.MAIN -c android.intent.category.HOME -n com.byd.launcher/com.byd.clusternav.launcher.KachiHome --es open_settings_group system
    xe am stack list | grep -c KachiHomeActivity      # > 0 ⇒ đã sinh instance thứ hai: ghi lại
    ```
- Sau đêm qua cửa sổ chắc chắn đã đóng [SUY: 60 phút `elapsedRealtime`]. Mọi lệnh sẽ trả `test_mode_off` cho tới khi anh bật.
- Cửa sổ dài **60 phút tính từ lúc bật** (`TestBridgeWindow.kt:29,41`). Gạt TẮT → BẬT là mở cửa sổ mới 60 phút tính từ lúc đó (`TestBridgeStore.kt:45-49`). Bật ở KEY-5 thì hết hạn khoảng T0 + 73 phút, giữa khối D/E ⇒ gia hạn ở bước 10b, kiểm lại trước D và trước E.
- Kiểm bằng `br --es cmd state | grep -o '"test_mode_minutes_left":[0-9]*'`.
- **Bật ở KEY-5, không bật sớm hơn.** Mở Cài đặt là thao tác UI; các dump chỉ-đọc phải xong trước.

---

## 3. PHÍM VÔ-LĂNG (A11Y-BIND-STUCK) — trọng tâm

### 3.1 Mục tiêu buổi sáng

Trả lời bốn câu, mỗi câu kèm mức bằng chứng:
1. Đêm qua (28→29/09) **có kẹt** không?
2. Nếu có, đường tự chữa của 2.81 **có tự chạy** và **có chữa được** không? (V-oncar-1, V-oncar-2.)
3. Nếu không tự chạy thì **cổng nào** chặn?
4. Launcher sống qua đêm thì bộ nhớ ra sao? (PERF-STANDBY-FRESH.)

### 3.2 Cơ chế đã ship ở 2.81 (tóm tắt, đủ để đọc log)

Mã phím giữa 2.81 và 2.82 **không đổi**, trừ note `sau-chua-*` [ĐO `git diff 8d0d161 bfbb728` trên `NavConnect.kt` và `core/…/navaccess/`: rỗng].

**Watchdog** (`VoiceKeyKeepAliveService`, FGS, chạy trong tiến trình chính) [ĐO `git show 8d0d161:…/VoiceKeyKeepAliveService.kt`]:
- Nhịp đầu sau **5 s**, rồi **mỗi 30 s** (`:158,168`). Nhịp dùng `postDelayed`, tức đồng hồ uptime, nên **không chạy khi máy ngủ sâu** [SUY].
- Phím-thoại TẮT ⇒ `return`, không hẹn lại (`:55`) ⇒ **không có tự chữa nào cả**.
- Mỗi nhịp:
  1. Tính `woke` = lượng ngủ sâu tăng ≥ **2 giờ** so với nhịp trước (`:46-50,165`). Nếu `woke` thì nhả quota: `a11y_forcestop_elapsed = -1` (`:62`).
  2. Hỏi `AccessibilityManager` xem dịch vụ đã gắn chưa.
  3. Ghi nhật ký `BOUND`/`NOT_BOUND` với note `wake` hoặc `watchdog` (`:66-70`).
  4. Chưa gắn ⇒ log W `a11y KHÔNG bound (vừa thức=…) → re-grant (in-process watchdog)` (`:72`), rồi gọi `grantAccessibility` với `userAsked=false`.

**Lượt grant** (`NavConnect.kt`):
- Trần **30 s** (`:61,184-197`), single-flight (`:200`). Binder báo đã gắn ⇒ bỏ shell (`:206-209`).
- Chưa gắn ⇒ `grantViaShell` (`:214-234`), rồi **TOGGLE** (`:322-377`): chờ 1,2 s → gỡ → 0,8 s → thêm lại → poll 6 × 1 s.
- Toggle hụt ⇒ `escalateIfStuck` (`:258-305`):
  1. Đọc `dumpsys accessibility`, ghi nhật ký `STUCK` hoặc `NOT_BOUND` với note `grant-tu-dong` hoặc `grant-tay`.
  2. Đọc `dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja|virtual:'` và `am stack list` (cổng ô).
  3. Gọi `healStep`. Cổng **không** xét màn hình sáng/tối hay có người trong xe (`:280-290`).

**Thứ tự cổng** (`AccessibilityHealGates.kt:100-106`, return sớm):

| Thứ tự | Điều kiện | Nấc |
|---|---|---|
| 1 | Đã gắn | NONE |
| 2 | Phím-thoại TẮT và không phải người bấm | NONE |
| 3 | Không kẹt trong Binding | TOGGLE (đã chạy rồi ⇒ trả `NOT_BOUND`) |
| 4 | Người bấm (`userAsked`) | **FORCE_STOP**, bỏ qua mọi cổng dưới |
| 5 | Có app khách `visible=true` trên display 0 hoặc trên VD của Kachi (đọc hỏng ⇒ cũng coi là có) | NONE |
| 6 | Đã leo trong đợt thức này (`a11y_forcestop_elapsed` ∈ [0, now]) | NONE |
| 7 | Còn lại | **FORCE_STOP** |

**Khi leo** (`:295-304`):
1. Ghi mốc bằng `commit()` **trước** khi bắn (`Prefs.kt:474-475`).
2. Log W `a11y KẸT trong Binding services → tự force-stop + lắp lại; giao diện khởi động lại một nhịp`.
3. Bắn lệnh tách rời: `nohup sh -c 'am force-stop com.byd.launcher ; sleep 4 ; settings put secure enabled_accessibility_services "<danh sách cũ>:<mình>" ; settings put secure accessibility_enabled 1' &` (`AccessibilityRebind.kt:194-214`).

Cổng không leo ⇒ log I `a11y chưa bound (kẹt=…, không app khách=…, ô của mình=…, tay=…) → nấc …, KHÔNG leo` (`NavConnect.kt:292`).

**Đường tự động khác**, tất cả `userAsked=false` nên đi qua đủ cổng:
- `BootSetupService.kt:65` (boot);
- `RebindReceiver.kt:60` (alarm `ELAPSED_REALTIME_WAKEUP` mỗi 60 s, `:206,227-230`; no-op khi watchdog in-process đang sống, `:55-63`);
- `PermissionPreflight.kt:292` (mỗi lần `KachiHomeActivity` dựng/lên kênh shell, qua `KachiHomeActivity.kt:327`, `KachiHomeWiring.kt:470,491`; log tag `Preflight`);
- `ClusterNavBridge.kt:133` (**chỉ** khi gạt *Dẫn đường + HUD* sang BẬT, nằm trong `setNavEnabled` `:112`);
- `ClusterNavBridgeKeys.kt:194` (*Học phím mới*, khi booster chưa được cấp).

### 3.3 Dòng thời gian dự kiến nếu đêm qua kẹt [SUY mã, chưa từng đo]

| Mốc | Sự kiện | Nội dung dòng log mong đợi (tag) |
|---|---|---|
| T0 | Xe thức | — |
| T0 + ≤ 30 s | Nhịp watchdog đầu sau khi thức | W (VoiceKeyKeepAlive) `a11y KHÔNG bound (vừa thức=true)` · I (A11yJournal) `a11y BOUND → NOT_BOUND (wake)` |
| + 8–12 s | Toggle hụt | I (NavConnect) `… toggle ép rebind` · I (NavConnect) `accessibility force-rebind xong: bound=false` |
| + vài giây | Phát hiện kẹt, cổng mở | I (A11yJournal) `a11y NOT_BOUND → STUCK (grant-tu-dong)` · W (NavConnect) `a11y KẸT trong Binding services → tự force-stop …` |
| + 0–1 s | Tiến trình bị giết | hệ thống: `Force stopping com.byd.launcher …` (đệm **system**, `Slog.i` [ĐO AOSP `ActivityManagerService.java:4642`]; thời gian giữ [CHƯA BIẾT]) · `am_kill` (events) [SUY định dạng] |
| + 4 s | Lắp lại, home dựng lại (nháy) | `am_proc_start … com.byd.launcher` · I (NavAccess) `accessibility booster connected` [ĐO nội dung dòng 28/09 09:53:17] |
| + 5 s | Nhịp đầu của tiến trình mới | I (A11yJournal) `a11y STUCK → BOUND (watchdog)` |

**Định dạng dòng**: mọi tệp logcat của buổi này (`key1-logcat-buffers.txt`, `logcat-live-*.txt`) và usage log (`KachiLog.kt:67`: `logcat -v time --pid=…`) đều ở dạng `-v time`, tức `09-29 07:01:02.100 I/NavAccess( 3920): accessibility booster connected`. Sau tag là `(<pid>):`, **không** phải `: `. Log 28/09 trích trong runbook là dạng threadtime (`I NavAccess: …`). ⇒ **Grep theo nội dung thông điệp, đừng grep `Tag: `.**

Tổng từ T0 tới khi phím ăn lại: **dưới khoảng 1 phút** [SUY]. Vì vậy phím chỉ được thử ở **T0 + 90 s**. Có thể chậm hơn (hàng 2c §3.6): mỗi lượt grant có trần 30 s và bỏ lượt trùng (`NavConnect.kt:61,200`), watchdog 30 s một nhịp.

### 3.4 Các bước đo buổi sáng

#### KEY-0 · Lên xe (anh, không cần adb, khoảng 2 phút)

Làm khi §2.0 ra Z0, hoặc Z1–Z3 mà xe đang tắt. Xe đang bật sẵn thì bỏ KEY-0 (P lấy từ lời anh kể, [ĐO owner, nhớ lại]) và làm KEY-1 ngay.

1. Bật xe. Ghi **T0** = lúc màn đầu xe **sáng** (có thể là lúc mở khoá, trước khi bấm Start [ĐOÁN]), chính xác tới phút. Ghi thêm giờ bấm Start. KEY-2 sẽ đo **W** = giờ thức thật theo máy.
2. **Không chạm màn, không bấm phím, không mở app nào trong 90 s.** Nếu trong 90 s đó màn nhà chớp hay dựng lại, ghi giờ lại: đó là dấu hiệu F thấy bằng mắt.
   - Toast *"Mối nối phím đang kẹt ở mức hệ thống…"* (`strings_kachi.xml:279`) **chỉ** có ở đường bấm tay (*Sửa ngay* / gạt *Nhận nút vật lý*): chỉ hai chỗ đó đổi `RESTARTING` thành toast (`ClusterNavBridgeKeys.kt:48-53,62-67,78-89`); watchdog, alarm và Preflight gọi grant **không** có callback UI [ĐO mã; `git show 8d0d161:…/VoiceKeyKeepAliveService.kt:73`, `NavConnect.kt:302-304`]. Thấy toast này trong KEY-0 ⇒ ghi giờ, coi là **có thao tác** (Z2, §2.0), **không** tính V-oncar-1.
3. Chụp ảnh điện thoại màn nhà (**không lộ biển số hay vị trí**). Mục đích: ô có app không, có cửa sổ đen không.
4. *(Bỏ bước nhìn thông báo.)* Chữ "Phím-thoại đang bật" là **nội dung** thông báo FGS, chỉ đọc được khi kéo rèm, tức là chạm màn, trái bước 2. Kênh khai `IMPORTANCE_MIN` (`git show 8d0d161:…/VoiceKeyKeepAliveService.kt:115`) nhưng AOSP nâng thông báo FGS lên `IMPORTANCE_LOW` (`NotificationManagerService.java:4738-4757`, android-10.0.0_r47); SystemUI BYD có hiện biểu tượng hay không: [CHƯA BIẾT]. Phím-thoại bật hay không đọc ở K2-4 (ServiceRecord `VoiceKeyKeepAliveService`) và `voicekey_enabled` (KEY-5).
5. **T0 + 90 s**: bấm **phím 328 (giữ mic vô-lăng) một lần**. Không nói lệnh gì.
   - Lớp nghe của Kachi hiện lên ⇒ **P = ăn**.
   - Không có gì ⇒ **P = chết**. Ghi giờ bấm.
   - Nghe là overlay không kéo launcher [ĐO log 28/09 `VoiceKeyLauncher: mở phiên nghe Kachi (headless overlay, không kéo launcher)`], nên không phải app khách.
   - [ĐOÁN] Khi dịch vụ Hỗ trợ chưa gắn, 328 có thể rơi về trình xử lý mặc định của ROM (`NavAccessibilityService.kt:76`: 328 tới được hệ thống). Nếu bấm 328 mà hiện giao diện hay app của BYD thì ghi tên và giờ, rồi bấm Back ngay. Ở 5c, app khách xuất hiện **sau** giờ bấm là do phép thử gây ra, không phải trạng thái sáng dậy.
6. **Chưa** bấm phím 305, **chưa** mở Cài đặt.

**Không có adb:**
- Giữ nguyên xe và nối adb càng sớm càng tốt. Trạng thái trong `system_server` (Binding, ConnectionRecord) và usage log trên thẻ không mất vì chờ, **nhưng**:
  - vòng đệm events chỉ giữ khoảng **32 phút** [ĐO 28/09]. Quá T0 + 25 phút mà chưa có KEY-1 thì ghi "`am_kill`: mất do chờ". `Force stopping` nằm ở đệm system (thời gian giữ [CHƯA BIẾT]) nên vẫn grep ở KEY-1 rồi mới kết luận; ngoài ra F dựa vào ETIME, usage log, mốc prefs;
  - nếu đang kẹt mà cổng đóng thì cứ 30 s watchdog ghi một cặp dòng nhật ký và toggle settings một lần ⇒ nhật ký bền (200 dòng, `A11yBindJournal.kt:32`) bị cuốn sau khoảng 50 phút (§3.5).
- Sau **15 phút** vẫn không nối được mà anh cần lái:
  1. chụp ảnh màn *Cài đặt › Phím vô-lăng* (có dòng trạng thái; chỉ mở màn thì chỉ đọc, §3.7);
  2. bấm *Kiểm tra / Sửa ngay* (`userAsked=true`, đang kẹt thì force-stop ngay, `ClusterNavBridgeKeys.kt:78-89`);
  3. ghi `KEY-0: không có adb, đã Sửa ngay lúc hh:mm`.

  Khi nối được sau này vẫn làm KEY-3, vì `usage-<ms>.log` nằm trên thẻ (`KachiLog.kt:66`). Phần mất: Binding services, ETIME, mẫu bộ nhớ ⇒ hàng §3.6 tối đa đạt [SUY].

#### KEY-1 · Chụp vòng đệm logcat **trước mọi lệnh khác** (khoảng 1 phút)

```zsh
source docs/diagnostics/oncar-2026-09-29/env.zsh
adbx logcat -d -b all -v time > $O/key1-logcat-buffers.txt                    # chụp TRƯỚC
```

Logcat chạy suốt buổi. Claude gọi bằng Bash `run_in_background: true`; anh thì chạy ở terminal 1. Mỗi lần dựng lại cầu là một tệp mới, không xoá tệp cũ:

```zsh
source docs/diagnostics/oncar-2026-09-29/env.zsh; adbx logcat -b main,system,events,crash -v time > $O/logcat-live-$(date +%H%M%S).txt
```

Mọi lệnh đọc về sau dùng `$O/logcat-live-*.txt`.

Đọc nhanh:

```zsh
grep -aE 'Force stopping com.byd.launcher|am_kill.*byd.launcher|am_proc_start.*byd.launcher|am_proc_died.*byd.launcher' $O/key1-logcat-buffers.txt
grep -aE 'A11yJournal|NavConnect|VoiceKeyKeepAlive|NavAccess|KachiAutoNav|Preflight' $O/key1-logcat-buffers.txt | tail -60
```

Vòng đệm trên xe rất ngắn:
- events khoảng **32 phút** [ĐO 28/09];
- main khoảng **30 s** (`oncar-runbook-2.75.md:16`), nên nhiều khả năng đã trôi;
- system (chứa `Force stopping`, `ActivityManagerService.java:4642`): [CHƯA BIẾT].

Nguồn chính vì thế là usage log ở KEY-3.

#### KEY-2 · Bộ dump chỉ đọc (khoảng 4 phút)

Mọi lệnh ở đây chỉ đọc, không đổi state.

```zsh
xe dumpsys package com.byd.launcher | grep -E 'versionName|versionCode|lastUpdateTime' > $O/key2-version.txt   # = B0
xe ps -A -o PID,ETIME,NAME | grep com.byd.launcher > $O/key2-ps.txt
xe dumpsys meminfo com.byd.launcher       > $O/key2-meminfo-main.txt   # dòng đầu có "Uptime: U Realtime: R"
xe dumpsys meminfo com.byd.launcher:wake  > $O/key2-meminfo-wake.txt
xe dumpsys meminfo com.byd.launcher:tts   > $O/key2-meminfo-tts.txt
{ xe date; xe cat /proc/uptime; xe "dumpsys power | grep -m1 mLastWakeTime"; xe "dumpsys alarm | grep -m2 -iE 'nowELAPSED|nowRTC'"; } > $O/key2-clock.txt
xe dumpsys accessibility                  > $O/key2-acc.txt
xe dumpsys activity services com.byd.launcher > $O/key2-svc.txt
{ xe settings get secure enabled_accessibility_services; xe settings get secure accessibility_enabled; } > $O/key2-enabled.txt
xe am stack list                          > $O/key2-stack.txt
xe "dumpsys display | grep -iE 'Display [0-9]+:|fission|xdja|virtual:'" > $O/key2-display.txt   # ĐÚNG lệnh app dùng (ClusterDisplayResolver.DETECT_CMD)
xe dumpsys alarm                          > $O/key2-alarm.txt
xe dumpsys procstats --hours 24           > $O/key2-procstats.txt
```

Lệnh đọc (máy Mac). Mã K2-x dùng trong bảng dưới:

```zsh
# K2-1 Hai đồng hồ
head -3 $O/key2-meminfo-main.txt; cat $O/key2-clock.txt
# K2-2 Tiến trình
cat $O/key2-ps.txt
# K2-3 Kẹt? · Đã gắn? · DEAD · ServiceRecord
a11y_read $O/key2-acc.txt $O/key2-svc.txt
# K2-4 Watchdog FGS
grep -c 'ServiceRecord.*VoiceKeyKeepAliveService' $O/key2-svc.txt
# K2-5 Danh sách enabled
cat $O/key2-enabled.txt
# K2-6 Cổng ô
grep -E 'Stack id=|visible=true' $O/key2-stack.txt; cat $O/key2-display.txt
# K2-7 Bộ nhớ
grep -E '^ *TOTAL' $O/key2-meminfo-*.txt
# K2-8 Alarm WAKEUP của Kachi
grep -n 'com.byd.launcher' $O/key2-alarm.txt | head -20
```

| Hỏi | Mã | Kỳ vọng / nghĩa | Mức của cách đọc |
|---|---|---|---|
| Hai đồng hồ | K2-1 | `Uptime: U Realtime: R` (ms). **Ngủ sâu = R − U**. Mốc 28/09 15:39: R = 99 201 263 · U = 29 781 235 ⇒ 19,28 g. **Giờ máy khởi động** = bây giờ − R: `date -r $(( $(date +%s) - R/1000 ))` (thay R bằng số). Dùng ở hàng 0 §3.6. **Không** so R với mốc 99,2 triệu ms của 15:39 để kết luận reboot (1.3 #5). **Giờ thức thật W** (theo elapsed) = `mLastWakeTime` + (R − U), vì `mLastWakeTime` tính theo uptime [SUY: đúng khi không ngủ lại sau lần thức; công thức U ở `PROJECT-BACKLOG.md:17`]. Đổi W ra giờ đồng hồ: bây giờ − (R − W)/1000 s | Header `Uptime: … Realtime: …`: [ĐO transcript, 3 mẫu có thật, hai số bằng nhau ⇒ nhiều khả năng máy ảo]; trên xe [CHƯA BIẾT]. Dự phòng [ĐO xe 28/09]: `/proc/uptime` trường 1 ≈ R (lệch 367 ms); `mLastWakeTime` X + "Y ms ago" ⇒ U = X + Y |
| Tiến trình sống từ bao giờ | K2-2 | Mốc 17:53 28/09: PID 3920, lên khoảng 15:57. PID vẫn 3920 và ETIME trùm từ 15:57 28/09 ⇒ **sống liên tục từ hôm qua**. ETIME < (giờ đo − 17:53 28/09) nhưng > (R − W)/1000 s ⇒ khởi động lại **trước** lần thức này (xét F-đêm §3.6). ETIME < (R − W)/1000 s ⇒ khởi động lại **sau** lần thức (F) | [ĐO dùng 28/09] |
| Kẹt? | K2-3 | Dòng "KẸT" = **1** ⇒ **KẸT**, bất kể có mục gói khác đứng cạnh (sáng 28/09 phím 305 của `com.byd.vrassistant.xf` chết cùng lúc [SUY cùng cơ chế]). `Binding services:{}]` = sạch. Chép nguyên văn dòng Binding vào tóm tắt | [ĐO fixture `AccessibilityBindingStuckTest.kt:27-29`: dạng `{{a/b}, {c/d}}`] |
| Đã gắn? | K2-3 | Có `label=ClusterNav…` = mình nằm trong Bound | [ĐO fixture `:45`]. **Đừng** dùng `grep -c com.byd.launcher` trên dòng Bound: ROM chỉ in nhãn, lệnh 28/09 trả 0 cả khi đã gắn |
| DEAD | K2-3 | Đếm **dòng** (một ConnectionRecord in ở nhiều khối, nên số dòng ≥ số bản ghi). 0 = sạch. Sáng 28/09 khi kẹt: 5. Mốc 17:53: [CHƯA BIẾT] (1.3 #11) | [ĐO `PROJECT-BACKLOG.md:7`; dạng dòng: transcript] |
| ServiceRecord | K2-3 | Mốc: `requested=true received=true hasBound=true`. Vắng ServiceRecord hoặc vắng dòng `requested=` ⇒ như ca kẹt 28/09 | [ĐO 28/09] |
| Watchdog FGS sống | K2-4 | ≥ 1 = sống (header in ở cả danh sách ngắn lẫn khối chi tiết). 0 = FGS chết hoặc phím-thoại tắt | [SUY] |
| Danh sách enabled | K2-5 | Có mục của mình, `accessibility_enabled` = 1. Mất mục của mình ⇒ nửa sau lệnh tách rời không chạy (hàng 6 §3.6) | [ĐO mã] |
| Cổng ô | K2-6 | Liệt kê mọi task `visible=true` có gói **≠ `com.byd.launcher`** nằm trên displayId 0, **hoặc** trên display có dòng `virtual:com.byd.launcher,` / `owner com.byd.launcher (`. Có ≥ 1 ⇒ **cổng đóng** (NONE). `am stack list` rỗng hoặc display đọc hỏng ⇒ cũng đóng | [ĐO mã `StackParse.kt:205-210`, `DisplayParse.kt:197-212`]. Trên xe display 0 còn app hệ thống nào `visible=true` không: [CHƯA BIẾT], đây là lần đầu soi |
| Bộ nhớ | K2-7 | So §3.8 | — |
| Alarm WAKEUP | K2-8 | Kachi đặt alarm `ELAPSED_REALTIME_WAKEUP` 60 s (REBIND watchdog), nên có thể là nguồn dark-wake cắt đêm thành nhiều mẩu ngắn hơn 2 giờ (ô 5d). Chép số `wakeups` và dòng alarm REBIND của Kachi vào tóm tắt | Alarm: [ĐO mã `RebindReceiver.kt:206,227-230`]; dạng dòng `dumpsys alarm` trên ROM này: [CHƯA BIẾT] |

#### KEY-3 · Usage log app tự ghi: đường thay thế cho nhật ký bền (khoảng 3 phút)

App tự ghi logcat **của chính nó** (`--pid`, `-v time`) ra thẻ, mỗi lần tiến trình lên thì một tệp `usage-<ms>.log`, trần **8 MB** (`KachiLog.kt:32`):
- khởi động ở `KachiHomeActivity.kt:212`, ghi ở `KachiLog.kt:60-67`;
- dòng W/E **không bao giờ** bị bộ tiết chế bỏ (`KachiLog.kt`, `throttled` luật 1).

Watchdog, `NavConnect` và `NavAccessibilityService` đều chạy trong tiến trình chính (manifest không khai `android:process`), nên dòng của chúng có trong tệp này [ĐO mã].

Chỉ kéo usage log từ 28/09 15:00 (lúc cài 2.81): cả cây ngoài được phép tới khoảng 150 MB (có cả ảnh `camera_frame`), kéo hết qua cầu nc trên điểm phát thì dễ vượt 3 phút.

```zsh
xe du -sk /sdcard/Android/data/com.byd.launcher/files /sdcard/Android/data/com.byd.launcher/files/kachi-logs > $O/key3-du.txt; cat $O/key3-du.txt
xe "ls -lt /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ | head -30" > $O/key3-ls.txt; cat $O/key3-ls.txt
mkdir -p $O/kachi-logs
for f in $(xe "ls /sdcard/Android/data/com.byd.launcher/files/kachi-logs/" | tr -d '\r' | grep -E '^usage-[0-9]+\.log$'); do
  ms=${${f#usage-}%.log}
  (( ms >= 1790582400000 )) && adbx pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/$f $O/kachi-logs/
done                                        # 1790582400000 = 28/09 15:00 giờ VN
ls -l $O/kachi-logs/                        # tệp ≥ 8388608 B ⇒ nhiều khả năng đã chạm trần (xem "Giới hạn")
for f in $O/kachi-logs/usage-*.log; do echo $f; tail -1 $f; done   # giờ + pid dòng cuối mỗi tệp
grep -aHE 'A11yJournal|VoiceKeyKeepAlive|NavConnect|KachiAutoNav|Preflight|accessibility booster connected|onKeyEvent DOWN keycode=328|voice-key fire|KachiCrash' $O/kachi-logs/usage-*.log > $O/key3-a11y-lines.txt
tail -80 $O/key3-a11y-lines.txt
```

Grep ở dòng cuối lọc theo **nội dung** (§3.3). Mẫu cũ `NavAccess: (accessibility booster|…)` không bao giờ khớp dạng `I/NavAccess( 3920): …`, nên mất đúng dòng S và dòng phím 328 [ĐO thử trên tệp giả: mẫu cũ bắt 2/7 dòng, mẫu mới 7/7].

Cách đọc:
- Tên tệp là mốc ms lúc **màn nhà** của tiến trình đó bật luồng chụp (`KachiHomeActivity.kt:212` → `KachiLog.kt:66`, tạo trong luồng chụp), tức **≥** giờ tiến trình lên; thứ tự tên vẫn là thứ tự thời gian. Một tiến trình cũng có thể sinh tệp thứ hai nếu luồng chụp đã dừng (`capturing = false`, `KachiLog.kt:93`) rồi màn nhà dựng lại ⇒ so pid trong dòng log.
- **Luật**: phân loại F và F-đêm theo **ETIME trước** (K2-2); tên tệp chỉ là **cận trên** của giờ tiến trình lên. Hai dấu hiệu mâu thuẫn ⇒ ETIME thắng.
- **Tệp có mốc tên sau W** ⇒ tiến trình đã khởi động lại sau lần thức sáng nay. Tệp cuối của tiến trình cũ phải chứa lý do.
- **Tệp có mốc tên nằm giữa 1790592780000 (28/09 17:53) và W** ⇒ tiến trình khởi động lại trong đêm hoặc chuyến tối: xét F-đêm (§3.6).
- Các dòng cần tìm được liệt kê ở §3.3 và §3.5. Dòng `KachiAutoNav: luật … tới lượt` cho biết lịch có tự mở app dẫn đường không (§2.1 mục 3).

Giới hạn:
- Usage log chỉ có khi màn nhà đã lên trong tiến trình đó [ĐO mã].
- **Trần 8 MB đếm ký tự, không đếm byte** [ĐO mã]: bộ đếm cộng `toWrite.length + 1` (ký tự UTF-16, `KachiLog.kt:80`) và chỉ ngắt **sau** khi đã vượt (`:86`), trong khi chữ có dấu tốn 2–3 byte UTF-8 ⇒ tệp chạm trần **lớn hơn** 8 388 608 B, gần như không bao giờ bằng đúng. Tệp ≥ 8 388 608 B ⇒ nhiều khả năng đã chạm trần. Tệp của tiến trình đang sống (pid trong dòng = PID ở K2-2) mà dòng cuối có giờ **trước** mốc đang soi (W, giờ bấm 328) ⇒ usage log **không phủ** khoảng đó; nếu P = ăn mà dòng cuối trước giờ bấm thì luồng chụp đã dừng (phím ăn thì phải có `onKeyEvent DOWN keycode=328`, `NavAccessibilityService.kt:78-79`), do chạm trần hoặc lỗi (`usage capture stopped`, `KachiLog.kt:92`). Khi đó vắng dòng STUCK/booster **không** có nghĩa là "không kẹt", cũng **không** phải 5f "parser đọc hỏng". Khả năng PID 3920 (sống từ 15:57 28/09) chạm trần: thấp theo tốc độ log khoảng 9 KB/phút (`PROJECT-BACKLOG.md:58`) nhưng không loại trừ [SUY].
- `DiagStorageCap` [ĐO mã]: cả cây external bị cắt về khoảng **150 MB**, xoá tệp **cũ nhất** trước (`DiagStorageCap.kt:12-17`, `StorageCapPlanner.kt:17`), chạy **mỗi lần màn nhà dựng lại** (`KachiHomeActivity.kt:216`), kể cả lần dựng lại do tự chữa hay do INST. ⇒ Kéo ở KEY-3, **trước** KEY-7 và INST. `key3-du.txt` cho thư mục `files` ≥ 150000 kB ⇒ nhiều khả năng tệp 28/09 đã bị dọn; ghi lại.
- Nhịp tim 1 giờ của nhật ký bền **không** ra logcat, nên không có trong usage log (`A11yBindJournalStore.kt:82`).

#### KEY-4 · Thử mở màn Chẩn đoán (1 phút, chỉ đọc — xem ⚠)

⚠ **Cùng điều kiện với cảnh báo đầu KEY-5**: nếu `key2-stack.txt` có app khách `visible=true` trên display 0, **hoặc** dòng "KẸT" ở K2-3 = 1, thì ghi **kết luận sơ bộ KEY-6** từ KEY-2/3 **trước** khi chạy lệnh dưới. Lý do [SUY mã]: nếu `am start` mở được, `DiagActivity` (gói Kachi) phủ app khách trên display 0 ⇒ app khách thành `visible=false`, cổng ô mở (`StackParse.kt:205-210`, `AccessibilityHealGates.kt:104`), và nhịp watchdog kế tiếp có thể tự FORCE_STOP. Xác suất thấp vì nhiều khả năng bị `Permission Denial` (dưới).

```zsh
xe am start -n com.byd.launcher/com.byd.clusternav.modules.clustercast.DiagActivity 2>&1 | tee $O/key4-diag-am-start.txt
```

- **Kỳ vọng** [SUY mạnh]: `Permission Denial … not exported`.
  - Uid 2000 đã bị chặn đúng lỗi này trên DiLink3 [ĐO 2026-08-01, `docs/archive/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md:65-67`; `AndroidManifest.xml:153-156`].
  - Chú thích `AndroidManifest.xml:89` ("adb vẫn am start được") sai trên ROM user.
- **Nếu mở được**: đây là activity của chính Kachi, `refresh()` chỉ đọc (`DiagActivity.kt:151-208`).
  0. Ghi giờ **G4**. Mọi dấu F xuất hiện sau G4 xử lý như "F sau KEY-5 (cổng mở do thao tác)", **không** tính vào V-oncar-1.
  1. Chụp `adbx exec-out screencap -p > $O/key4-diag.png`.
  2. Cuộn tới khối `── phím vô-lăng · nhật ký gắn dịch vụ Hỗ trợ ──`, chụp tiếp (hiện 20 dòng cuối cùng dòng `đã tự khởi động lại để chữa (mốc elapsed): …`).
  3. Bấm Back.

#### KEY-5 · Bật Chế độ kiểm thử → prefs a11y + SCHED-0 + prefs camera (khoảng 5 phút)

⚠ **Trước khi anh mở Cài đặt**: nếu `key2-stack.txt` có app khách `visible=true` trên display 0, **hoặc** dòng "KẸT" ở K2-3 = 1, thì ghi **kết luận sơ bộ KEY-6** từ KEY-2/3 **trước**. Lý do [SUY mã]: đưa màn nhà lên để mở Cài đặt làm app khách thành `visible=false`, cổng ô mở, và nhịp watchdog kế tiếp (≤ 30 s) có thể tự FORCE_STOP (`AccessibilityHealGates.kt:104`, `StackParse.kt:205-210`); nếu ô có app thì app rơi thành cửa sổ đen. **Ghi giờ mở Cài đặt (G5).** Mọi dấu F xuất hiện sau G5 ghi là **"F sau KEY-5 (cổng mở do thao tác)"**, không tính vào V-oncar-1.

Anh bật công tắc bằng tay theo §2.5 (không dùng deep link `-n …/KachiHomeActivity`). Sau đó:

```zsh
br --es cmd state > $O/key5-state.txt; grep -o '"test_mode_minutes_left":[0-9]*' $O/key5-state.txt
br --es cmd prefs --es file clusternav_prefs > $O/key5-prefs-cn.txt
grep -oE '"(a11y_forcestop_elapsed|a11y_deep_sleep_ms|voicekey_enabled)":[^,}]*' $O/key5-prefs-cn.txt
pref_get $O/key5-prefs-cn.txt voicekey_bindings        # chuỗi JSON lồng in trọn
```

Tên khoá không bị che: bộ che so theo **đoạn**, và `voicekey` ≠ `key` (`TestBridgeState.kt:315-319`). Tất cả nằm trong tệp `clusternav_prefs` (`Prefs.kt:19`).

| Khoá | Nghĩa | Mức |
|---|---|---|
| `voicekey_enabled` | `false` ⇒ **mọi** đường tự động bị tắt theo thiết kế (cổng 2). Không phải lỗi của bản vá | [ĐO mã] |
| `a11y_forcestop_elapsed` | `-1` = chưa leo từ lần `woke` gần nhất; **cũng có thể** là `woke` sáng nay vừa nhả mốc của một lần leo trong đêm (F-đêm §3.6). Giá trị ≥ 0 là mốc `elapsedRealtime` (ms) lúc leo. Nằm trong [W, R] (W: K2-1) ⇒ **đã leo trong đợt thức này**. Nhỏ hơn W ⇒ leo từ trước và **không** được nhả khi thức ⇒ nghi `woke` không bật. **Lớn hơn R** ⇒ mốc ghi trước một lần reboot; cổng coi như chưa leo | [ĐO mã `Prefs.kt:460-475`, `VoiceKeyKeepAliveService.kt:62` (2.81), `AccessibilityHealGates.kt:57-58`] |
| `a11y_deep_sleep_ms` | Lượng ngủ sâu mà nhịp watchdog gần nhất thấy. ≈ (R − U) hiện tại ⇒ watchdog **đã chạy** sau lần ngủ cuối. Lệch nhiều giờ ⇒ watchdog **không chạy** kể từ khi thức | [ĐO mã `:46-50`] |
| `voicekey_bindings` | Phải có `"k":328` | [ĐO xe 28/09 15:29: `voice-key fire → target=__KACHI_VOICE__ key=328` ⇒ 328 đã gán]; sáng nay kiểm lại |

Ngay sau đó chạy **SCHED-0** (§5) và chụp prefs camera (§6). Hai việc này dùng chung cửa sổ 60 phút.

#### KEY-6 · Tra cây quyết định §3.6 và ghi kết luận (khoảng 3 phút)

Ghi vào tệp tóm tắt ngoài `$O` (§9) theo mẫu:

`KEY-6: Z=… (§2.0) · hàng # · P=… · K=… · F=… (ghi rõ F-sáng / F-đêm / F lúc bật sớm T0' / F sau KEY-5) · S=… · T0=… · T0'=… · W=… · G4=… (nếu KEY-4 mở được) · G5=… · cổng=… · mức=[ĐO/SUY]`

#### KEY-7 · Nút *Kiểm tra / Sửa ngay*: chỉ sau KEY-2..KEY-6, xem §3.7

#### KEY-8 · Vụ tiến trình khởi động lại khoảng 15:57 hôm 28/09 (off-car, 1 phút, từ KEY-3)

```zsh
grep -aHE '^09-28 15:[3-5]' $O/kachi-logs/usage-*.log | grep -aE 'KẸT|force-stop|A11yJournal|KachiCrash|FATAL' | head -40
```

- Có W `a11y KẸT … tự force-stop` quanh 15:5x ⇒ **ca kẹt thật đầu tiên đã được tự chữa hôm qua**, sau một chặng ngắn. Ghi [ĐO], vì đó cũng là bằng chứng cho V-oncar-1/2.
- Giờ máy khởi động (K2-1) ≈ 15:56 28/09 ⇒ vụ này là **reboot**, không phải tự chữa; ghi [ĐO].
- Không có tệp nào của khoảng giờ đó ⇒ [CHƯA BIẾT], ghi lại.

### 3.5 Đọc nhật ký bền `a11y-bind.log`: sự thật và nghĩa từng note

**Tệp**: `/data/user/0/com.byd.launcher/files/diag/a11y-bind.log` (`A11yBindJournalStore.kt:25-26,41`). Giữ 200 dòng cuối (`A11yBindJournal.kt:32`).

**Ghi khi nào**:
- chỉ ghi khi trạng thái **đổi**;
- hoặc khi đã quá 1 giờ kể từ lần sửa tệp gần nhất (nhịp tim, chỉ xét lúc máy thức) (`:29,68-69`).

**Đường menu**: bản phát hành **không có**, xem 1.3 #2. Chỉ đọc được nếu KEY-4 mở được màn Chẩn đoán. Còn lại thì chỉ đọc được dòng **đổi trạng thái** qua logcat tag `A11yJournal` trong usage log (KEY-3).

**Dạng dòng** (`A11yBindJournal.kt:71-75`):

```text
2026-09-28T10:20:00 state=STUCK up=3600s sleep=32400s pid=12738 note=grant-tu-dong
```

- `up` = uptime (s).
- `sleep` = ngủ sâu tích luỹ (s). `elapsedRealtime` = `up + sleep`.

Dạng trên logcat (`-v time`): `I/A11yJournal(<pid>): a11y <trước> → <sau> (<note>)`.

| note | Ai ghi | Nghĩa |
|---|---|---|
| `wake` | watchdog | Nhịp đầu sau một đợt ngủ ≥ 2 giờ; quota vừa được nhả |
| `watchdog` | watchdog | Nhịp thường. **Ở 2.81 thì nhịp đầu sau tự chữa cũng mang note này** |
| `grant-tu-dong` | `NavConnect.kt:268-272` | Lượt grant tự động, toggle đã hụt, rồi thấy STUCK hoặc NOT_BOUND |
| `grant-tay` | cùng chỗ | Như trên nhưng do bấm *Sửa ngay* hoặc gạt BẬT |
| `sau-chua-ON` / `sau-chua-VAN-TAT` | **chỉ 2.82** (`VoiceKeyKeepAliveService.kt:73-77`) | Nhịp đầu của tiến trình mới khi mốc ≥ 0. Mốc chỉ xoá khi `woke` ⇒ **mọi** lần khởi động lại trong cùng đợt thức (kể cả do cài 2.82) cũng ghi note này. Phải so pid và giờ. `VAN-TAT` có thể chỉ là bind chậm hơn 5 s ⇒ chờ dòng kế tiếp |

⚠ **Tràn nhật ký** [SUY mã]: kẹt mà cổng đóng thì cứ 30 s có một cặp `NOT_BOUND` + `STUCK`, tức khoảng 240 dòng mỗi giờ, và lịch sử bị cuốn sau khoảng 50 phút. Toggle cũng ghi settings mỗi 30 s. **Nếu ra hàng 5 thì đo nhanh.**

### 3.6 Cây quyết định (2.81; bảng, không vẽ)

Các dấu hiệu dùng trong bảng (W, R, U lấy ở K2-1; G5 là giờ mở Cài đặt ở KEY-5):

- **P**: phím 328 ăn khi bấm ở T0 + 90 s (KEY-0).
- **K**: bằng chứng **kẹt**. Một trong các dấu hiệu sau:
  - usage log hoặc logcat có `A11yJournal … → STUCK (grant-tu-dong)`;
  - W `a11y KẸT trong Binding services`;
  - dòng "KẸT" ở K2-3 = 1 lúc đo.
- **F**: bằng chứng **tự force-stop trong đợt thức này**. Một trong các dấu hiệu sau:
  - `Force stopping com.byd.launcher` (đệm system, thời gian giữ [CHƯA BIẾT]) / `am_kill … byd.launcher` (đệm events, khoảng 32 phút);
  - `a11y_forcestop_elapsed` ∈ [W, R];
  - ETIME < (R − W)/1000 s;
  - có `usage-<ms>.log` với mốc tên sau W;
  - dấu hiệu thấy bằng mắt ở KEY-0 (màn nhà chớp/dựng lại). Toast *"Mối nối phím đang kẹt…"* **không** phải dấu F tự động: nó chỉ có ở đường bấm tay (KEY-0 bước 2) ⇒ thấy nó là Z2.
  - Dấu F xuất hiện **sau G5** (hoặc sau G4 nếu KEY-4 mở được màn Chẩn đoán) ghi là "F sau KEY-5 (cổng mở do thao tác)", **không** tính vào V-oncar-1.
- **F-đêm**: tiến trình khởi động lại **giữa 17:53 28/09 và W**. Một trong:
  - ETIME < (giờ đo − 17:53 28/09) nhưng > (R − W)/1000 s;
  - có `usage-<ms>.log` với mốc tên nằm giữa 1790592780000 (17:53 28/09) và W, mà tệp liền trước có W `a11y KẸT trong Binding services → tự force-stop`.
  - Tên `usage-<ms>.log` chỉ là **cận trên** của giờ tiến trình lên (KEY-3, "Cách đọc"): tự chữa trong đêm mà màn nhà chỉ dựng lại khi xe thức sáng nay [ĐOÁN] thì tên tệp rơi sau W trong khi ETIME cho thấy tiến trình lên từ đêm. Xếp F / F-đêm theo **ETIME trước**; mâu thuẫn ⇒ ETIME thắng.

  Ca Z1 (§2.0), xe đã bật sớm hơn sáng nay rồi tắt: lần tự chữa lúc bật sớm (nếu có) cũng rơi vào khoảng này. Tách bằng giờ trong usage log so với giờ anh kể; ghi là "F lúc bật sớm (T0')", không phải "trong đêm". Hai lần bật cách nhau dưới 2 giờ thì lần sau không nhả quota (`woke` cần ngủ sâu tăng ≥ 2 giờ giữa hai nhịp, `A11yBindJournal.kt:53-54`) ⇒ mốc ∈ [0, R] mà không có `vừa thức=true` là đúng thiết kế (cổng 6), **không** phải 5d [SUY mã].

  Vì sao có ca này [SUY mã, chưa từng đo]: watchdog và alarm WAKEUP 60 s chạy mỗi khi CPU thức, kể cả những nhịp thức ngắn trong đêm (`RebindReceiver.kt:227-230`), và cổng không xét màn hình hay người ngồi trong xe (`NavConnect.kt:280-290`). Nếu leo trong đêm thì lượt `woke` sáng nay xoá mốc về −1 (`git show 8d0d161:…/VoiceKeyKeepAliveService.kt:62`), dòng `Force stopping` đã trôi khỏi vòng đệm, và không dấu hiệu F nào ở trên bật.
- **S**: **sau chữa đã gắn**:
  - nội dung `accessibility booster connected` sau F (hoặc sau F-đêm);
  - `A11yJournal … STUCK → BOUND`;
  - `label=ClusterNav` có trong Bound và `hasBound=true`.

| # | P | K | F | S · khác | Kết luận (mức) | Làm tiếp | Chụp / gửi về |
|---|---|---|---|---|---|---|---|
| 0 | — | — | — | Giờ máy khởi động (K2-1) **sau** giờ tắt xe tối qua (§2.1) | Đêm qua **không phải standby**, không nói gì về bản vá [ĐO nếu R đọc được]. Máy khởi động **trước** giờ tắt xe ⇒ đêm nằm trọn trong lần boot này: kiểm thêm (R − U) ≥ 2 giờ rồi đi tiếp hàng 1–8 | Ghi lại, đo bộ nhớ làm mốc mới, cài 2.82 | key2-clock, key2-ps, key2-meminfo-main |
| 1 | ăn | không | không | PID/ETIME cho thấy sống liên tục từ trước 17:53 28/09 (PID 3920) · Binding `{}` · DEAD 0 dòng | **Đêm qua không kẹt** [ĐO]. Bản vá: **chưa kết luận được** | So PSS §3.8 (hợp lệ vì tiến trình sống qua đêm) → cài 2.82 | ps, acc, svc, meminfo ×3, prefs |
| 1b | ăn | không | không | usage log có `→ NOT_BOUND (wake)` rồi `force-rebind xong: bound=true`; Binding `{}` | Đêm qua rơi vào **trạng thái A**, nấc toggle chữa được ⇒ **V-oncar-5 đạt** [ĐO] | So PSS §3.8 → cài 2.82 | key3-a11y-lines, acc, svc |
| 2 | ăn | có | có | S có | **Bản vá chạy thật ngoài hiện trường** [ĐO] (V-oncar-1 + V-oncar-2 về cơ chế) | Đo giá khởi động lại: giờ dòng W `tự force-stop` → `booster connected` → màn nhà lên. **Không** so bộ nhớ (cả 3 tiến trình đã mới). Xem ảnh KEY-0 có cửa sổ đen không | Đoạn log từ STUCK tới `booster connected`, ps, prefs, acc/svc, stack, ảnh màn nhà |
| 2b | ăn | có (log đêm) | F-đêm | S có (`STUCK → BOUND` hoặc `booster connected` trong tệp mới) | **Bản vá chạy thật, trước khi lên xe sáng nay** (trong đêm, chuyến tối, hoặc lần bật sớm T0' của ca Z1; giờ lấy từ log) [ĐO nếu đủ log] | Ghi giờ dòng W `tự force-stop`. **Không** so bộ nhớ | Như hàng 2 |
| 2c | chết | có | có | S có, nhưng giờ `booster connected` **sau** giờ bấm | Bản vá chạy thật nhưng **chậm hơn 90 s** [ĐO] | Ghi ba mốc: W `tự force-stop` · `booster connected` · giờ bấm. Bấm lại 328 một lần, ghi kết quả P' | Như hàng 2 |
| 3 | ăn | log đã trôi / không có usage log | mốc ∈ [W, R] + ETIME ngắn | Binding `{}` | **[SUY mạnh]** đã tự chữa | Muốn nâng lên [ĐO] thì cần đọc được nhật ký (§3.9) | Như hàng 2 |
| 4 | ăn | không | ETIME < (giờ đo − 17:53 28/09) nhưng mốc = −1 | — | **Trước khi nghĩ tới LMK/crash, loại F-đêm**: mốc −1 còn có nghĩa là `woke` sáng nay đã nhả mốc của một lần leo trong đêm (`VoiceKeyKeepAliveService.kt:62` bản 2.81). ETIME > (R − W)/1000 s ⇒ xét hàng 2b. ETIME < (R − W)/1000 s mà mốc −1 ⇒ khởi động lại sau lần thức mà không leo ⇒ lý do khác (LMK, crash). **Không kết luận** | Lệnh T4 dưới bảng | Như trên |
| 5 | chết | có (K2-3 KẸT = 1) | không | — | **Đường tự động không leo.** Tìm cổng theo thứ tự rẻ (ô dưới). **Chụp hết trước** rồi mới tới KEY-7 | 5a · 5b · 5c · 5d · 5e · 5f | Toàn bộ KEY-2 + nguyên văn `key2-acc.txt` |
| 6 | chết | có | có | vẫn chưa gắn | Tương đương `VAN-TAT`. **Chờ 60 s rồi `remeasure key2b`** + `a11y_read` trên hai tệp vừa ghi, mới kết luận | Mục của mình còn trong enabled không (nửa sau lệnh tách rời có chạy không)? Binding có lại mục ngay không (kẹt lại)? Màn nhà và FGS watchdog lên lại chưa? | Như trên + `key2b-*` |
| 7 | chết | không (Binding `{}`) | — | không có trong Bound | Trạng thái A mà toggle **không** chữa được (V-oncar-5 hỏng) | Tìm `force-rebind xong: bound=false` và `grantAccessibility TIMEOUT` | Như trên |
| 8 | chết | — | — | Có trong Bound (`label=ClusterNav`, `hasBound=true`) | **Không phải lỗi này** | Lệnh T8 dưới bảng sau khi bấm lại. `voicekey_bindings` có `"k":328` (KEY-5) | Như trên |

Lệnh dùng trong bảng:

```zsh
# T4 (hàng 4): tiến trình chết vì gì
grep -aE 'am_proc_died|am_kill|am_low_memory' $O/key1-logcat-buffers.txt
grep -a KachiCrash $O/kachi-logs/usage-*.log
# T8 (hàng 8): phím 328 có tới dịch vụ không
grep -a 'onKeyEvent DOWN keycode=328' $O/logcat-live-*.txt
```

Ghi chú hàng 0 [ĐOÁN]: PID tiến trình chính rơi từ 25121 (15:36 28/09) xuống 3920 (lên khoảng 15:57), gợi ý đầu xe có thể đã reboot lúc đó (1.3 #5). Vì vậy **không** dùng tiêu chí "R nhỏ hơn 99,2 triệu ms" của bản trước: nếu reboot lúc 15:56 thì lúc 09:30–10:00 sáng nay R chỉ khoảng 17,5–18 giờ dù cả đêm là standby thật, và hàng 0 sẽ bỏ phí đúng đêm cần đo.

**Chẩn đoán hàng 5**. Đi theo thứ tự, dừng ở bước đầu tiên khớp:

| Mã | Dấu hiệu | Nguồn |
|---|---|---|
| 5a | `voicekey_enabled` = false ⇒ cổng 2, đúng thiết kế | KEY-5 |
| 5b | Không có ServiceRecord `VoiceKeyKeepAliveService`, hoặc `a11y_deep_sleep_ms` cũ nhiều giờ ⇒ watchdog không chạy | KEY-2, KEY-5 |
| 5c | Log I `… không app khách=false … → nấc NONE, KHÔNG leo`, và `key2-stack.txt` có app khách `visible=true` ⇒ **cổng ô**. Ghi rõ **gói nào, display nào**: app trong ô, CarPlay/AA, hay app hệ thống BYD trên display 0. Có dòng `KachiAutoNav: luật … tới lượt` **trước** dòng `KHÔNG leo` ⇒ app khách do **lịch** tự mở: ghi là **nhiễu** của buổi đo, không phải phát hiện thiết kế. App khách xuất hiện **sau** giờ bấm 328 ⇒ do phép thử (KEY-0 bước 5) | KEY-2, KEY-3 |
| 5d | Mốc ∈ [0, R] mà không có dòng `vừa thức=true` ⇒ quota không được nhả. Nghi dark-wake: đêm có nhịp thức ngắn, nên lần thức sáng tăng < 2 giờ (`woke` chỉ bật khi ngủ sâu tăng ≥ ngưỡng **giữa hai nhịp**, `A11yBindJournal.kt:53-54`). Ứng viên số 1 là alarm WAKEUP 60 s của chính Kachi (`RebindReceiver.kt:227-230`) [ĐO mã]. `a11y_deep_sleep_ms` ≈ R − U mà không có dòng `vừa thức=true` ⇒ các nhịp watchdog trong đêm đã chia nhỏ giấc ngủ [SUY]. Ghi số wakeups (K2-8) vào tóm tắt | KEY-2, KEY-3, KEY-5 |
| 5e | `grantAccessibility TIMEOUT` hoặc `qua dadb NÉM` ⇒ lượt grant không chạy hết | KEY-3 |
| 5f | Không có dòng STUCK nào dù Binding có mục ⇒ parser đọc hỏng. So `key2-acc.txt` với fixture `AccessibilityBindingStuckTest.kt`. **Trước đó** loại ca usage log không phủ khoảng giờ đang soi (KEY-3, "Giới hạn": chạm trần / luồng chụp dừng) | KEY-2, KEY-3 |

Chỉ **hàng 2, 2b, 2c** (và hàng 3 ở mức [SUY]) chứng minh bản vá; hàng 1b chứng minh nấc toggle. Hàng 1, 0, 4 không nói gì về bản vá. Tần suất kẹt mỗi đêm: [CHƯA BIẾT].

### 3.7 KEY-7 · Nút *Kiểm tra / Sửa ngay*: chỉ sau khi đã chép đủ bằng chứng

**Đường đi (đi tay)**: *Cài đặt › Phím vô-lăng › khối "Nút vật lý → app" › **Kiểm tra / Sửa ngay*** (`SettingsSectionsKeys.kt:57-58`, `strings_kachi.xml:462`). Không dùng deep link `-n …/KachiHomeActivity` (§2.5).

- Chỉ **mở** màn thì chỉ đọc (dòng trạng thái `strings_kachi.xml:459-461`), vô hại [ĐO mã].
- Nút gọi `NavConnect.grantAccessibilityDetailed(reset = true)`, tức `userAsked=true` (`ClusterNavBridgeKeys.kt:78-89`). Nút cũng gọi `reapplyGeminiAssistant()`, nhưng hàm này chỉ chạy khi có gán Gemini (`:291-300`).

| Trạng thái lúc bấm | Kỳ vọng [SUY mã] | Đo lại |
|---|---|---|
| Đã gắn | Không chạy shell (`NavConnect.kt:206-209`). Toast `strings_kachi.xml:282` "Phím-thoại đã sẵn sàng." | Không cần |
| **Kẹt** (hàng 5) | **FORCE_STOP ngay**, bỏ qua cổng ô và quota (`AccessibilityHealGates.kt:103`). Mốc được ghi (`NavConnect.kt:301`). Toast `strings_kachi.xml:279` *"Mối nối phím đang kẹt ở mức hệ thống. Đang tự chữa…"* có thể **không kịp hiện**, vì tiến trình bị giết ngay sau `sh(cmd)` [ĐOÁN] | Chờ 15 s → `remeasure key7` → `a11y_read` trên hai tệp `key7-<giờ>-*` vừa ghi + bấm 328. Đạt khi dòng "KẸT" = 0, có `label=ClusterNav`, DEAD 0 dòng, pid mới, phím ăn |
| Kẹt **và** ô có app | Như trên, và app trong ô **rơi thành cửa sổ đen** phủ màn nhà (T10 chưa làm) [ĐO 28/09 với force-stop tay] | Chụp ảnh. **Cách thoát [ĐO 28/09]**: mở lại **đúng** app đang nằm trong ô, **ưu tiên** chạm biểu tượng app đó; dùng adb thì `xe 'am start -n <gói>/<activity>'` (nháy **đơn** ở Mac; gói lấy từ `key2-stack.txt`). Activity có `$` thì viết `\$` bên trong nháy đơn, vd `xe 'am start -n com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell\$HomeActivity'` [ĐO transcript 28/09: lệnh chạy được có `\$`]; không trích thì zsh ở Mac rồi sh trên xe bung `$HomeActivity` thành rỗng. App vẽ lại thì khối đen hết (`StackParse.kt:182-184`: đen "tới khi mở lại app đó"). Chạm ô hoặc bấm Home: chưa đo, chỉ thử sau. Không `am stack move/remove`. Đây là **V-oncar-4b** |
| Chưa gắn, không kẹt (A) | Toggle. Toast `strings_kachi.xml:278` nếu vẫn chưa gắn | `remeasure key7` |

Sau KEY-7 thì mẫu bộ nhớ và mọi dấu hiệu F/S là **của lần bấm tay**, không còn là của đường tự động. Ghi rõ như vậy.

### 3.8 So mốc bộ nhớ (PERF-STANDBY-FRESH)

| Tiến trình | Mốc 28/09 17:53 (2.81, tiến trình sống 1 g 56 ph) | Nguồn |
|---|---|---|
| `com.byd.launcher` | TOTAL PSS **48 514 kB** (Native Heap 25 712, Dalvik Heap 3 180) | [ĐO `PROJECT-BACKLOG.md:11`] |
| `:wake` | **176 286 kB**, số của **một** tiến trình, nhiều khả năng `:wake` (1.3 #4) | Một tiến trình: [ĐO transcript]; là `:wake`: [SUY] |
| `:tts` | chưa có mốc | [CHƯA BIẾT] |

```zsh
grep -E '^ *TOTAL|Native Heap|Dalvik Heap' $O/key2-meminfo-main.txt
grep -E '^ *TOTAL' $O/key2-meminfo-wake.txt $O/key2-meminfo-tts.txt
```

- **Chỉ so được khi PID/ETIME cho thấy tiến trình sống từ hôm qua** (hàng 1, 1b hoặc hàng 5). Hàng 2/2b/2c/3/4 thì tiến trình đã mới.
- Ngưỡng "leo đáng kể" chưa ai chốt. Đề xuất [ĐOÁN] để anh quyết: tăng > **+50 %** ⇒ có cơ sở nghi rò; trong **±20 %** ⇒ bình thường. Hai mốc đo ở trạng thái khác nhau (17:53 sau hai chuyến ngắn; sáng nay sau một đêm), nên chỉ đọc được xu hướng.
- `key2-procstats.txt` cho thời gian chạy và PSS min/avg/max 24 giờ. Chép dòng của ba tiến trình.

### 3.9 Tiêu chí kết luận, giới hạn, và cần gì để chốt

**"Bản vá chạy thật ngoài hiện trường"** [ĐO] đòi **đủ bốn**:
1. K: có bằng chứng kẹt sau đêm.
2. F (hoặc F-đêm): có bằng chứng tự force-stop, **không** do người bấm, và không phải "F sau KEY-5".
3. S: sau đó đã gắn thật (`label=ClusterNav` + `hasBound=true`, hoặc `booster connected`).
4. P: phím 328 ăn ở T0 + 90 s, **hoặc** ăn ở lần bấm lại ngay sau S (hàng 2c; ghi rõ là lần bấm lại).

Thiếu một trong bốn ⇒ **"chưa kết luận được"**, kèm lý do.

**Giới hạn thật:**
1. **Đêm qua không kẹt (hàng 1) thì không chứng minh được gì.**
   - Lỗi tự tái hiện "hầu hết các lần" [ĐO owner] nhưng không phải mọi đêm.
   - Cần thêm đêm, và mỗi sáng làm lại KEY-0..KEY-3 (khoảng 10 phút).
2. **Nhật ký bền không đọc được trên bản phát hành.**
   - Usage log chỉ giữ dòng đổi trạng thái và có thể bị dọn. Nhịp tim, và thời điểm đứt qua đêm, **chỉ nằm trong `a11y-bind.log`**.
   - Muốn chốt V-oncar-1 đúng như spec thì cần **một đường đọc**. Đây là quyết định của anh, off-car, sẽ thành **2.83 (184)** (CLAUDE.md §9). Ba hướng:
     - (a) đưa 20 dòng cuối và mốc vào lời đáp `state`/`diag` của cầu kiểm thử (cầu vốn chỉ mở bằng công tắc tay);
     - (b) trả hàng *Chẩn đoán* về sau cổng Chế độ kiểm thử — `DevSurfaceGateContractTest.kt:78` phải đổi theo;
     - (c) sau buổi đo, cài đè bản `vehicleTest` (debuggable, cùng khoá) rồi `run-as … cat files/diag/a11y-bind.log`. Có sẵn bản build đó không: [CHƯA BIẾT]; cách này cũng là cài đè.
3. **Cổng ô có thể đóng mỗi sáng** nếu hồ sơ có app trong ô, hoặc CarPlay/AA chiếu lên màn chính, hoặc lịch tự mở app dẫn đường [SUY mã, 1.3 #9, §2.1 mục 3]. Khi đó đường tự động **không bao giờ** chạy, và phím chỉ sống lại khi bấm tay.
   - Nếu hàng 5c xảy ra (không do lịch, không do phép thử) thì đây là **phát hiện thiết kế**. Ghi vào backlog để anh chốt (OQ4 của spec), không vá tại xe.
4. **V-oncar-3** (không giết lần hai trong cùng đợt thức) chỉ quan sát được nếu kẹt **hai lần** trong một đợt thức.
5. **V-oncar-5** (trạng thái A được toggle chữa) chỉ quan sát được nếu A xảy ra (hàng 1b). Không có cách tạo A an toàn [CHƯA BIẾT].
6. **V-oncar-6** (cố tình làm đứt bind): cái gì hạ ServiceRecord (OQ1) còn [CHƯA BIẾT], và lần thử 28/09 bị chặn quyền. **Không làm hôm nay** trừ khi anh quyết.

### 3.10 Ánh xạ nợ spec (`docs/specs/kachi-a11y-bind-stuck-autofix.html:583-588`)

| Spec | Buổi 29/09 | Ghi chú |
|---|---|---|
| V-oncar-1 (qua một đêm) | KEY-0..6 | Đọc nhật ký thay bằng usage log + dump. Đạt chỉ khi rơi vào hàng 2, 2b hoặc 2c |
| V-oncar-2 (đúng nấc) | KEY-2, KEY-3 | Tiêu chí thật: toggle **rồi** leo (1.3 #6). Sau đó `label=ClusterNav` trong Bound + ServiceRecord `received=true hasBound=true` |
| V-oncar-3 (không lặp) | Không chủ động | Tiêu chí đã sửa ở 1.3 #7 |
| V-oncar-4 (a) tự động + ô có app ⇒ NONE · (b) bấm tay ⇒ có giết | 5c · KEY-7 | (a) là **hàng 5c tự nhiên**. (b) chỉ khi anh đồng ý |
| V-oncar-5 | Không chủ động | Đạt nếu rơi vào hàng 1b |
| V-oncar-6 | Không | Anh quyết (OQ2, mặc định: không cần) |

---

## 4. Cài 2.82 (không OTA): INST

**Điều kiện trước khi cài**:
- KEY-0..KEY-6 đã xong (và KEY-7 nếu làm);
- `kachi-logs` đã kéo (KEY-3; INST dựng lại màn nhà ⇒ `DiagStorageCap` chạy);
- SCHED-0 đã chụp;
- prefs camera đã chụp (chính là `sched0-cn.txt`; so ở CAM-KEEP, §5).

**Về ô**: nếu ô đang có app, cài đè giết Kachi. Nhiều khả năng app trong ô rơi thành cửa sổ đen như 28/09 [SUY]. Chấp nhận, chụp lại. **Cách thoát [ĐO 28/09]**: mở lại đúng app đang nằm trong ô, **ưu tiên** chạm biểu tượng; dùng adb thì `xe 'am start -n <gói>/<activity>'` (nháy đơn, gói lấy từ `key2-stack.txt`; activity có `$` thì viết `\$`, xem §3.7); app vẽ lại thì khối đen hết. Chạm ô hoặc bấm Home: chưa đo. **Không** dùng `am stack move/remove`.

**Bốn câu CLAUDE.md §4**:
1. Display nào? Không chạm display.
2. App nào? Chỉ gói `com.byd.launcher`.
3. Loại stack nào? Không chạm stack.
4. Hoàn tác thế nào? Lùi bằng `install -r -d` (dưới đây).

```zsh
xe dumpsys package com.byd.launcher | grep -E 'versionName|versionCode|lastUpdateTime'      # TRƯỚC: 182 / 2.81
adbx install -r apk/Kachi-2.82-CHUA-DANG.apk                                                  # → Success
xe "date '+%m-%d %H:%M:%S'; date +%s" > $O/inst-time.txt; cat $O/inst-time.txt              # I = giờ INST theo đồng hồ XE: dòng 1 dạng logcat, dòng 2 epoch s (§10.1 mục 3); ghi I vào tóm tắt
xe dumpsys package com.byd.launcher | grep -E 'versionName|versionCode|lastUpdateTime' | tee $O/inst-version-sau.txt   # SAU: 183 / 2.82
```

- Ngay sau khi đọc lại versionCode: chạy **SL6-BASE** (§7, ba lệnh `getprop`, 1 phút). Chỉ đọc, không cần cầu kiểm thử hay màn nhà, nên không đổi thứ tự chụp bằng chứng; đặt ở đây để lộ trình 60 phút (bước 1 → 11) không bỏ nó.
- `-r` là cài đè cùng chữ ký, nên dữ liệu giữ nguyên [ĐO apksigner: 2.81 và 2.82 cùng cert `9257…9917`; đã làm cách này ở 2.75].
- Tên tệp nào cũng cài được bằng adb. Chỉ OTA mới cần đúng khuôn (`UpdateChecker.kt:45`).
- Sau khi cài, **bấm Home**. Kachi phải vẫn là màn chính (marker `homeChosen` áp lại sau nâng cấp, theo memory `kachi-home-alias-gui-install`).
- Kiểm cầu còn mở: `br --es cmd state`. Nếu `test_mode_off` thì bật lại. Ghi kết quả vào 1.3 #3.
- Đo nhanh sau cài (**đây là mốc mới**, không so với sáng nay):
  - `remeasure inst`, rồi `a11y_read` trên hai tệp `inst-<giờ>-*` vừa ghi;
  - bấm 328 ⇒ phải ăn.
  - 2.82 có thể ghi `sau-chua-*` cho nhịp đầu nếu mốc ≥ 0. Đó là **giả**, vì do cài gây ra, không do tự chữa (§3.5).

**Lùi về 2.81** (nếu 2.82 hỏng nặng trên xe):

```zsh
adbx install -r -d apk/Kachi-2.81-release.apk        # -d BẮT BUỘC (183 → 182)
# Đã đăng OTA thì tệp trên không còn (§10.2 bước 1 chạy `git rm`) ⇒ dùng bản sao §10.2 bước 1 đã giữ trong $O:
adbx install -r -d $O/Kachi-2.81-rollback.apk
# Thiếu cả bản sao ⇒ lấy lại từ git TRƯỚC rồi chạy dòng trên (sha256 phải ra 65738e50…4c9e):
git show bfbb728:apk/Kachi-2.81-release.apk >| $O/Kachi-2.81-rollback.apk; shasum -a 256 $O/Kachi-2.81-rollback.apk
```

- [ĐO AOSP] ROM user cho phép lệnh này từ adb:
  - `-d` bật `INSTALL_REQUEST_DOWNGRADE` (`PackageManagerShellCommand.java:2402-2403`);
  - uid shell được `INSTALL_ALLOW_DOWNGRADE` (`PackageInstallerService.java:502-503,633-636`).
- Trên xe **chưa từng chạy** [CHƯA BIẾT].
- Dữ liệu 2.82 để lại tương thích với 2.81 [SUY]: phép di trú chỉ **thêm** khoá vào ảnh hồ sơ (`WorkspacePrefsProfile.kt:229-257`).

---

## 5. Lịch theo hồ sơ (SCHED-0..4)

**Sự thật từ mã** [ĐO]:
- Lịch sống nằm trong `clusternav_prefs`, khoá `nav_automation_rules` và `nav_automation_fired` (`PrefsAutomation.kt:29-30,366-367`).
- Ảnh chụp theo hồ sơ nằm trong `kachi_workspace`, khoá `<hồ sơ>____cn__clusternav_prefs`:
  - `keyOf` = `"${p}__$suffix"` (`WorkspacePrefs.kt:152`);
  - suffix = `"__cn__clusternav_prefs"` (`ProfileScope.kt:42,392`);
  - ⇒ **4 gạch dưới**.
- Giá trị ảnh chụp là các dòng `<thẻ>|<khoá>|<giá trị>`, thẻ `n` = null, `s` = chuỗi; giá trị chuỗi được thoát `\\ \n \r \p \c` (`PrefSnapshot.kt:76-90,141-146`).
- Mốc `migrated_nav_schedule_v1` nằm trong `kachi_workspace`.
- Phép di trú (`WorkspacePrefsProfile.kt:229-257`) chạy khi `PrefsWorkspaceRepository` được dựng, tức lần đầu màn nhà lên sau khi cài. Nó:
  - **không đụng giá trị sống**;
  - chỉ điền khoá còn vắng, và ghi cả `null` **tường minh** (`n|nav_automation_rules|`) khi lịch sống vắng;
  - đặt mốc trong cùng lượt ghi.
- Các bài test hiện có chỉ canh chữ nguồn. **SCHED-1 là lần đầu phép di trú chạy trên dữ liệu thật** [ĐO `NavScheduleProfileScopeWiringTest.kt:22-28`].

**Hàm `sched_check`** nằm trong `env.zsh` (§2.3). Nó in:
- `ok = … | error = … | file = …`. `ok = False` hoặc `KHONG co data=` ⇒ lệnh hỏng (vd `test_mode_off`, `home_not_running`), **không** phải kết quả;
- với `clusternav_prefs`: `LICH SONG sha1 = X dai N` (hoặc `VANG`);
- với `kachi_workspace`: `MOC = …` (hoặc `MOC vang`), và mỗi hồ sơ một dòng `ANH <hồ sơ> CO lich sha1 = Y` / `NULL lich (tuong minh)` / `THIEU lich`. Y băm giá trị **đã giải thoát**, nên so thẳng được với X.

Bản trước in `CO lich` cả khi ảnh chỉ chứa `n|nav_automation_rules|`, và im lặng rc 0 khi gặp `test_mode_off` [ĐO thử trên dữ liệu giả, tối 28/09]. Bản này đã chạy thử trên cùng dữ liệu giả: in `NULL lich (tuong minh)`, `ok = False | error = test_mode_off`, và sha1 ảnh khớp sha1 lịch sống.

| ID | Mục tiêu | Bước | Đạt khi | Chụp | Phút |
|---|---|---|---|---|---|
| **SCHED-0** (trên 2.81, ở KEY-5) | Có cái để so | Khối SCHED-0 dưới bảng. Anh chụp ảnh màn *Cài đặt › Dẫn đường & cụm đồng hồ › Tự dẫn đường theo lịch* (`SettingsCatalogEntries.kt:154`) | `ok = True` cả hai tệp. Có `LICH SONG sha1`. `MOC vang`. Kỳ vọng ảnh hồ sơ `THIEU lich` [SUY từ lịch sử mã] | 3 tệp + ảnh màn Lịch | 3 |
| — | Nếu `LICH SONG VANG` (lịch rỗng) | Tạo **một luật thử** trên 2.81 **trước khi cài**, để **TẮT** (không đặt khung giờ bật). Không có luật thì SCHED-1 đạt một cách vô nghĩa. Rồi **chạy lại cả ba lệnh chụp SCHED-0** bằng khối "SCHED-0 chụp lại" dưới bảng (ghi đè có chủ đích) và chụp lại ảnh màn Lịch. SCHED-1 so với bản chụp **sau** khi tạo luật | — | ảnh | 2 |
| **SCHED-1** (quan trọng nhất) | Nâng cấp phải giữ nguyên lịch | Sau INST, màn nhà đã lên: khối SCHED-1 dưới bảng. Rồi trên UI đổi qua **từng** hồ sơ, mở màn Lịch | (0) `ok = True` cả hai tệp · (1) `MOC = True` · (2) **mọi** hồ sơ trong `sched0-profiles.txt` có dòng `ANH <hồ sơ> CO lich sha1 = X`, với X = `LICH SONG sha1` của `sched1-cn.txt`; có dòng `NULL lich` / `THIEU lich` hoặc sha1 khác ⇒ **KHÔNG đạt** · (3) `LICH SONG sha1` y hệt SCHED-0 · (4) hồ sơ nào cũng hiện đúng danh sách lịch như ảnh SCHED-0 | 2 tệp + ảnh màn Lịch mỗi hồ sơ | 5 |
| **CAM-KEEP** | Nâng cấp giữ nguyên prefs camera (§6) | Ngay sau SCHED-1: lệnh `diff` trong khối CAM-KEEP dưới bảng, trên `sched0-cn.txt` và `sched1-cn.txt` (khoá `camera_*` nằm trong `clusternav_prefs`, `Prefs.kt:19`, `PrefsAutomation.kt:30`). Ca Z5 (§2.0): không có SCHED-0 trên 2.81 ⇒ ghi "chưa đo" | Output **rỗng**, hoặc chỉ có dòng `> "camera_guide_…"` (khoá mới của 2.82, chỉ được xuất hiện ở bên sau). Có bất kỳ dòng `<` nào ⇒ **KHÔNG đạt**: một khoá của 2.81 đã mất hoặc đổi giá trị | output `diff` | 0 |
| **SCHED-2** | Hai hồ sơ giữ hai lịch khác nhau | Tạo hồ sơ "Trip" ở bảng hồ sơ (hồ sơ mới là bản sao hồ sơ đang dùng rồi tự chuyển sang, `WorkspacePrefsProfile.kt:119`). Sửa lịch của Trip (khung giờ **không** trùm giờ hiện tại). Khối SCHED-2 dưới bảng. Đổi về hồ sơ gốc rồi chụp lần hai | `LICH SONG sha1` của `sched2-trip-cn.txt` ≠ của `sched2-goc-cn.txt`, và sha1 của `sched2-goc-cn.txt` = SCHED-1 (lịch sống đổi theo mỗi lần chuyển, `PrefsWorkspaceRepository.kt:206-209`) | 2 ảnh + 2 tệp | 5 |
| **SCHED-3** | Tệp xuất có lịch | Bấm *Xuất hồ sơ (backup)*. Khối SCHED-3 dưới bảng. Kiểm **ngay trên máy**, **không kéo tệp .kachi về** (tệp chứa sổ địa chỉ) | Tệp **mới nhất** (giờ sửa bằng lúc vừa bấm Xuất, tên = hồ sơ đang dùng; ký tự lạ trong tên thành `_`, `ProfileIoStore.kt:28-31`) cho ≥ 1. Tệp cũ hơn của hồ sơ khác ra 0 là bình thường (nút Xuất chỉ ghi hồ sơ đang dùng, `HomePanels.kt:187`). Muốn kiểm cả Trip thì đổi sang Trip rồi Xuất thêm một lần | output lệnh | 2 |
| **SCHED-4** (ưu tiên thấp) | Phép di trú không chạy lại | Cài lại **chính APK 2.82** (`adbx install -r apk/Kachi-2.82-CHUA-DANG.apk`). **Không** dùng `am force-stop` (0.2 ②). Khối SCHED-4 dưới bảng | `MOC = True`. Dòng `ANH Trip` và `ANH <hồ sơ gốc>` vẫn khác sha1 | 2 tệp | 3 |
| SCHED-dọn | Không để lại thứ tự bắn | Sau SCHED-4: hỏi anh có giữ hồ sơ Trip không; không giữ thì xoá ở bảng hồ sơ. Luật thử (nếu đã tạo ở SCHED-0) thì xoá | Anh xác nhận | — | 1 |

```zsh
# SCHED-0 (trên 2.81)
br --es cmd profiles > $O/sched0-profiles.txt
br --es cmd prefs --es file clusternav_prefs > $O/sched0-cn.txt
br --es cmd prefs --es file kachi_workspace > $O/sched0-ws.txt
sched_check $O/sched0-cn.txt; sched_check $O/sched0-ws.txt

# SCHED-0 chụp lại (khi vừa tạo luật thử, hoặc khi lần chụp đầu hỏng — §2.3 "Chụp hỏng thì chụp lại"; '>|' = cố ý ghi đè)
br --es cmd profiles >| $O/sched0-profiles.txt
br --es cmd prefs --es file clusternav_prefs >| $O/sched0-cn.txt
br --es cmd prefs --es file kachi_workspace >| $O/sched0-ws.txt
sched_check $O/sched0-cn.txt; sched_check $O/sched0-ws.txt

# SCHED-1 (sau INST)
br --es cmd prefs --es file kachi_workspace > $O/sched1-ws.txt
br --es cmd prefs --es file clusternav_prefs > $O/sched1-cn.txt
sched_check $O/sched1-cn.txt; sched_check $O/sched1-ws.txt

# CAM-KEEP (ngay sau SCHED-1, chỉ chạy trên Mac): mọi khoá camera_* của 2.81 phải còn nguyên giá trị sau INST
diff <(grep -oE '"camera_[a-z_]+":("[^"]*"|[^,}]*)' $O/sched0-cn.txt | sort) <(grep -oE '"camera_[a-z_]+":("[^"]*"|[^,}]*)' $O/sched1-cn.txt | sort)

# SCHED-2 (đang ở Trip đã sửa lịch, rồi sau khi đổi về hồ sơ gốc)
br --es cmd prefs --es file clusternav_prefs > $O/sched2-trip-cn.txt; sched_check $O/sched2-trip-cn.txt
br --es cmd prefs --es file clusternav_prefs > $O/sched2-goc-cn.txt;  sched_check $O/sched2-goc-cn.txt

# SCHED-3 (ảnh ClusterNav nằm LỒNG trong tệp xuất nên '|' thành '\p'; dòng null 'n\p…' không khớp)
xe "ls -lt /sdcard/Android/data/com.byd.launcher/files/profiles/ | head -5"
xe "grep -cF 's\pnav_automation_rules\p' '/sdcard/Android/data/com.byd.launcher/files/profiles/<tên hồ sơ đang dùng>.kachi'"

# SCHED-4 (sau khi cài lại chính APK 2.82)
br --es cmd prefs --es file kachi_workspace > $O/sched4-ws.txt; sched_check $O/sched4-ws.txt
```

Mẫu SCHED-3: tệp xuất là header + `PrefSnapshot` của các hậu tố hồ sơ, trong đó ảnh ClusterNav là **một chuỗi** được thoát thêm một lần (`WorkspacePrefsProfile.kt:368-374`) [ĐO mã + thử trên tệp giả: hồ sơ có lịch ra 1, hồ sơ `null` ra 0; `grep -c nav_automation_rules` cũ ra 1 cả hai].

**Rủi ro:**
- Lịch **có thể tự bắn dẫn đường**, ngay cả trên 2.81 lúc sáng: `AutomationService` chạy mỗi 60 s (`AutomationService.kt:114`), điều kiện ở `ScheduledNavApplier.kt:55-90`. Cài xong hoặc đổi hồ sơ trong khung giờ đi làm thật của anh thì GMaps có thể tự mở. Đó là hành vi đúng, nhưng làm nhiễu cổng ô (5c) — xem §2.1 mục 3.
- Đổi hồ sơ giữa khung giờ có thể bắn lần hai trong ngày (spec Q1 [CHƯA BIẾT]). SCHED-2 làm **ngoài** mọi khung giờ thật.
- **Không đạt SCHED-1 ⇒ không OTA.** Nhập tay lại lịch từ ảnh SCHED-0 và ghi backlog [P1].

---

## 6. Camera: vạch chuẩn (CAM-GUIDE-ONCAR) và các kiểm kèm

**Bối cảnh cần biết** [ĐO mã + prefs dump 28/09 11:25Z]:
- Overlay chỉ hiện khi `camera_signal_enabled=true` (`CameraSignalController.kt:139`). Xe owner đang `true`.
- `camera` và `camera_frame` cần màn nhà đang chạy, nếu không sẽ trả `home_not_running`.
- **`prefs_set` không bật xem thử.** Sau mỗi lần `prefs_set` phải chạy `br --es cmd camera --es name none` rồi `br --es cmd camera --es name right` (hoặc `left`). Ngược lại, bấm chip trên UI thì có xem thử ngay (`ClusterNavBridgeAutomation.kt:227-262`).
- Xe owner có `camera_cam_left/right = 1`. Khoá này không có hàng UI và **ghi đè cameraId của chip góc nhìn** (`PrefsAutomation.kt:350-352`, `CameraSignalController.kt:189-190`).
- Prefs camera hiện tại:
  - `camera_pos_right=TL`
  - `camera_rot_left/right = L90/R90`
  - `camera_shape=CLUSTER`
  - `camera_on_cluster=false`
  - `camera_pano_*=AUTO`
  - `camera_span=STRIP`
- **Chụp prefs camera trước và sau INST** (dùng chung tệp `sched0-cn.txt` / `sched1-cn.txt`). Mọi khoá `camera_*` phải giữ nguyên qua nâng cấp: so bằng **CAM-KEEP** (§5, lệnh `diff` ngay sau SCHED-1).

**Vạch** [ĐO mã]:
- Nấc n đặt vạch ở y = n/10 × chiều cao cửa sổ overlay (`CameraGuide.kt:66`).
- Vạch **ngang**, trắng 2dp, viền đen, nằm trên video, dưới nhãn (`CameraGuideLineView.kt:38-71`).
- Cửa sổ không ăn chạm.
- Log **không in** vị trí vạch, và `camera_frame` cũng không thấy vạch. Vì vậy đo bằng **screencap** (màn chính) và **ảnh điện thoại** (cụm).

**Chuẩn bị**:
- Xe đỗ, số P, phanh tay.
- Hàng 9/10 nằm ở *Cài đặt › Tiện nghi xe › Camera theo xi-nhan* (đi tay; deep link xem §2.5).
- Trước khối D, kiểm cửa sổ kiểm thử: `br --es cmd state | grep -o '"test_mode_minutes_left":[0-9]*'`; còn < 25 phút ⇒ anh gạt TẮT → BẬT.
- Log: `grep -aE 'KachiCamera|KachiTest' $O/logcat-live-*.txt | tail` (không `logcat -c`).

```zsh
br --es cmd prefs_set --es key camera_guide_right --es text 5          # OFF hoặc 1..9; giá trị lạ → bad_prefs_value
br --es cmd camera --es name none; sleep 2; br --es cmd camera --es name right
adbx exec-out screencap -p > $O/guide1-r5.png                           # màn chính; màn cụm: xem GUIDE-5
```

| ID | Mục tiêu | Bước | Đạt khi | Chụp | Phút |
|---|---|---|---|---|---|
| **GUIDE-1** | Vạch đi đúng từng nấc | Mỗi bên, nấc 1→9: bấm chip, hoặc dùng vòng `prefs_set` + `none`/`right` như trên | Vạch đi xuống đều khoảng 10 % mỗi nấc, không nhảy, không dính mép, không che nhãn. Đo y/H trên ảnh ≈ n/10 (máy ảo: nấc 5 ⇒ ≈0,49 [ĐO]) | 9 ảnh mỗi bên + dòng `overlay show … khung=WxH` (`CameraOverlayView.kt:224`) | 8 |
| **GUIDE-2** | Vạch giúp đoán khoảng cách | Đỗ cạnh mốc đo bằng thước (**30 cm**, thêm một mốc khoảng **1 m**). Chọn nấc cho vạch trùng chân mốc. Dời mốc xa hơn rồi gần hơn | Anh nói đúng "gần hơn / xa hơn mốc" nhờ vạch. **Ghi nấc đã chọn cho mỗi bên**, và **thân xe nằm ở mép nào của khung** (trên / dưới / trái / phải) | Ảnh overlay mỗi mốc + ảnh hiện trường **không lộ biển số** | 7 |
| **CAM-B5** | Không hồi quy [P0] trên 2.82 (đã PASS 27/09 trên 2.76). **Làm ở bước 11**, cùng GUIDE-1/2 | Đổi **trực tiếp** trái → phải 3 lần rồi `none` (khối CAM-B5 dưới bảng). **Không** chen `none` giữa hai bên như vòng GUIDE-1: gốc [P0] chỉ xảy ra khi đổi bên không qua NONE (`PROJECT-BACKLOG.md:66,106`; cách làm ở `oncar-runbook-2.76.md:22`, `oncar-runbook-2.75.md:30`). Tuỳ chọn: thêm một lượt xi-nhan thật trái → phải | Chờ 30 s sau `none`, lệnh grep CAM-B5 ra **rỗng** | Giờ bắt đầu/kết thúc + output grep | 2 |
| GUIDE-3 | Nhìn rõ trên nền sáng và nền tối | Tường hoặc bê tông nắng; nhựa đường bóng râm hoặc hầm. Sáng không có đêm thì ghi "đo một phần" | Vạch rõ ở cả hai nền, viền đen nổi trên nền sáng | 2 ảnh mỗi bên | 4 |
| GUIDE-4 | Không ăn chạm | Có vạch trên màn, kéo bản đồ bên dưới vùng overlay | App bên dưới nhận chạm | Ghi đạt/không | 1 |
| GUIDE-5 | Vạch trên cụm | Bật `camera_on_cluster` (hàng 2, hoặc `prefs_set camera_on_cluster true`), thử lần lượt Chữ nhật / Tròn / Theo cụm | Vạch có mặt trên cụm, không bị mặt nạ cắt mất. Hình Tròn cắt hai đầu vạch là chấp nhận | **Ảnh điện thoại màn cụm** (👁) mỗi hình: `anh-GUIDE5-{rect,round,cluster}.jpg`. Tuỳ chọn: khối GUIDE-5 dưới bảng. **Xong phải trả** `camera_on_cluster=false`, `camera_shape=CLUSTER` | 5 |
| CAM-F16 | IA đúng | Đếm các hàng khối camera | **16 hàng**, đúng thứ tự: bật · cụm · vị trí T/P · xoay T/P · thử camera số T/P · **vạch chuẩn T/P** · dải hình T/P · lật gương T/P · hình khung · nắn hình (`SettingsSectionsCamera.kt:54-170`) | 1 ảnh | 1 |
| CAM-SEAL | Seal không hồi quy khi để chip mặc định | Xi-nhan thật hai bên | Hình như 2.79. Log `camera MIRROR_* camId=1 (def=1)` | Log | 2 |
| CAM-PREVIEW-HOLD | Xem thử có treo trên màn không | Bấm một chip, **không** chạm cần xi-nhan, chờ 30 s | Overlay tự tắt ⇒ đạt. Còn nằm đó ⇒ [ĐO] mâu thuẫn với R3 spec source-picker; ghi backlog. Đóng bằng `br --es cmd camera --es name none` | Ghi | 1 |

```zsh
# CAM-B5 — đổi TRỰC TIẾP trái → phải, KHÔNG chen `none` giữa hai bên. Công cụ chặn `sleep` thì tách lượt,
# mỗi lượt một lệnh camera, giữ đúng thứ tự left, right, left, right, left, right, none
xe date +%T; for i in 1 2 3; do br --es cmd camera --es name left; sleep 3; br --es cmd camera --es name right; sleep 3; done; br --es cmd camera --es name none; xe date +%T
# CHỜ 30 s sau `none` rồi mới grep — rỗng mới đạt (lọc theo tên hàng đợi SurfaceTexture; dòng của Netflix là báo động giả)
grep -a 'BufferQueue has been abandoned' $O/logcat-live-*.txt | grep SurfaceTexture

# GUIDE-5 (tuỳ chọn) — chụp cụm bằng máy. Lệnh nào đúng trên xe này hôm nay: [CHƯA BIẾT]
xe "fission_screencap -d 0 -p /data/local/tmp/c0.png"; xe ls -l /data/local/tmp/c0.png   # lỗi / 0 byte ⇒ bỏ, dùng ảnh điện thoại
adbx pull /data/local/tmp/c0.png $O/guide5-<hình>-fission-d0.png                           # chỉ dùng nếu ảnh ra đồng hồ cụm
```

Vì sao không dùng `screencap -d 1` cho cụm:
- `screencap -d` nhận **physical** display id; màn ảo không chụp được (máy ảo: tệp 0 byte, "Invalid physical display ID", `b3-cluster-arrow-e2e-emulator-1920x720-2026-08-21.md:20-22,128-130`).
- Trên xe, sau reboot display 1 là ô `kachi-slot-0` của chính Kachi, cụm là display 2 (`fission_bg_xdjaVirtualSurface`) [ĐO xe 15/09, `StackParse.kt:192-195`] ⇒ `-d 1` chụp nhầm ô hoặc ra 0 byte. `HANDOFF-2026-07-23-oncar-freeze.md:28` ("`-d 1` bắt đúng cụm") là dữ liệu tháng 7, trước khi có ô.
- `fission_screencap -d 0` = cụm theo `docs/archive/diagnostics/oncar-handoff-2026-08-13.md:29,57` (help ghi ngược), nhưng `docs/archive/_handoff/cluster-hud-injection-STATE.md:73` ghi `-d 1` ⇒ [CHƯA BIẾT]; ảnh ra đồng hồ cụm mới dùng.
- Tệp 0 byte hay ảnh sai màn **không** có nghĩa là không có vạch.

**Trả lại trước khi rời xe** (CLAUDE.md §4 câu 4):

```zsh
br --es cmd prefs_set --es key camera_guide_left  --es text <nấc anh chọn ở GUIDE-2, hoặc OFF>
br --es cmd prefs_set --es key camera_guide_right --es text <nấc anh chọn ở GUIDE-2, hoặc OFF>
br --es cmd prefs_set --es key camera_on_cluster --es text false        # nếu GUIDE-5 chưa trả
br --es cmd prefs_set --es key camera_shape --es text CLUSTER           # nếu GUIDE-5 chưa trả
xe rm -f /data/local/tmp/c0.png

# CUỐI CÙNG, sau MỌI lệnh br của buổi: anh gạt *Chế độ kiểm thử qua adb* TẮT. Receiver exported (AndroidManifest.xml:302-318),
# cổng thật chỉ là công tắc, và cửa sổ còn mở tới 60 phút kể cả lúc anh lái về (TestBridgeStore.disable chỉ gọi từ công tắc, SettingsSections.kt:428)
br --es cmd state | grep -o '"error":"[a-z_]*"'        # phải ra "error":"test_mode_off"
# Nếu anh đồng ý: xoá bản JSON đầy đủ của các lệnh `prefs` buổi này (có lịch + sổ địa chỉ, §9).
# Tên tệp <yyyyMMdd-HHmmss-SSS>-<lệnh>.json (TestBridgeReply.kt:80-82). Liệt kê TRƯỚC, chỉ xoá tệp mang ngày hôm nay
xe "ls -lt /sdcard/Android/data/com.byd.launcher/files/test/ | head -60"
xe "rm -f /sdcard/Android/data/com.byd.launcher/files/test/20260929-*-prefs.json"
# 3 000 ký tự đầu của mỗi lời đáp cầu cũng nằm trong kachi-logs/usage-*.log trên xe (§9). KHÔNG xoá (bằng chứng buổi sau);
# nếu sau này kéo hoặc chia sẻ kachi-logs thì coi các tệp đó là riêng tư, chỉ để trong $O
```

**Hai rủi ro phải để mắt** [SUY đọc mã]:
- Vạch ngang chỉ đo được **khe hở ngang** tới xe bên cạnh khi thân xe mình nằm ở mép **trên hoặc dưới** khung. Xe owner xoay L90/R90 nên nhiều khả năng đúng như vậy. Nếu ở một bên thân xe nằm ở mép trái/phải khung thì **dừng lại, báo anh**: cần tính năng mới (vạch dọc), không vá tại xe.
- Mỗi bên chỉ có **một khoá vạch**, trong khi cách ánh xạ ảnh khác nhau theo hình khung và theo màn:
  - hình Tròn cắt ô vuông giữa (`CameraPanoCrop.kt:163-164`);
  - *Theo cụm* phóng 112 % nên mất 11–12 %.

  Ở GUIDE-5 phải so **cùng mốc** trên cả hai màn, và ghi anh canh ở cấu hình nào.

**Owner chốt (qua chat, không cần xe)**: CAM-SCOPE-PROFILE. Họ khoá `camera_*` theo DEVICE hay theo PROFILE, xếp **cả họ một lượt**. Đề xuất sẵn trong `PROJECT-BACKLOG.md:447`.

---

## 7. SL6: CAM-SL6-PROFILE (mốc phía Seal)

- Owner đã chốt giá trị đúng cho SL6: `camera_view_left` = `camera_view_right` = `FRONT_LEFT` (chip "3"), `camera_pano_*` = AUTO [ĐO owner 28/09].
- Chưa đưa vào `ClusterProfile` được. `detectSeed` trả `SEAL_DL3` cho mọi xe không phải DL5 (`ClusterProfile.kt:228-244`), và `ro.product.model` của Seal = `BYD AUTO` [ĐO 27/09] ⇒ chuỗi model nhiều khả năng **không** tách được hai đời xe [SUY].
- Không đoán chuỗi nhận dạng rồi viết vào mã (CLAUDE.md §2, §14).

**SL6-BASE** (1 phút, trên Seal, chỉ đọc; chạy ở **bước 9**, ngay sau INST, §0.1; ca Z5 bỏ INST thì vẫn chạy ở chỗ bước 9). Chạy đúng bộ lệnh này để sau này có cái đặt cạnh SL6:

```zsh
xe "getprop ro.product.model; getprop ro.product.name; getprop ro.product.device; getprop ro.product.brand; getprop ro.product.manufacturer; getprop ro.build.product; getprop ro.build.display.id; getprop ro.build.fingerprint" > $O/sl6base-seal-product.txt
xe "getprop vehicle.config.cam_sort; getprop vehicle.config.pano_cam; getprop vehicle.config.pano_l_cam" > $O/sl6base-seal-cam.txt
xe "getprop | grep -iE 'ro\.product|dilink'" > $O/sl6base-seal-backlog-cmd.txt     # cùng ý lệnh ở PROJECT-BACKLOG.md:441, dạng ERE
```

- **Không** chụp `getprop` đầy đủ vào repo: có thể chứa serial hoặc VIN. Ba tệp trên chỉ nằm trong `$O`. Chỉ được commit **một** khoá phân biệt đã chọn.
- Phía SL6 chạy **đúng** lệnh thứ ba (dạng `-iE`) để hai tệp so được với nhau. `PROJECT-BACKLOG.md:441` đang ghi dạng BRE `'ro.product\|dilink'`; `\|` trong BRE không chắc được toybox hỗ trợ [SUY] ⇒ sửa backlog theo dạng ERE trong commit tóm tắt.
- Hướng generic đáng cân nhắc [SUY]: Seal có `cam_sort=rear:0;pano_h:1;` [ĐO carlog 14/09]. Nếu SL6 cho `pano_h:0` thì lấy cameraId từ `cam_sort`, không cần nhận tên xe (CLAUDE.md §7). Cần giá trị của SL6 và anh duyệt.
- Ghi backlog: `ClusterDiag` chưa tự chụp `ro.product.*` / `cam_sort` (trái tinh thần CLAUDE.md §11). **Không làm hôm nay.**

---

## 8. Các phép đo ngắn khác

Trước khối E, kiểm cửa sổ kiểm thử lần nữa (§2.5).

| ID | Mục tiêu | Bước | Đạt khi / ghi gì | Chụp | Phút |
|---|---|---|---|---|---|
| **AVM-PANO** | Chốt xe chạy PI hay APA, cánh cửa [CHƯA BIẾT] cuối của AVM-360 (`PROJECT-BACKLOG.md:13`) | Khối AVM-PANO dưới bảng | Nội dung tệp là `PI` hoặc `APA` [ĐO RE `SDKControler.readSDK()`]. Permission denied thì ghi lại và thử đường phụ (5 = APA). **Ra APA thì báo anh ngay**: đó là track mới, không làm trong buổi. [SUY mạnh] là PI | Output | 2 |
| **GMAPS-POP** | Popup đổi điểm đến là cửa sổ riêng hay view, nằm ở display nào | Xe đỗ. GMaps dẫn tới A và chiếu lên cụm như thường ngày. Ra lệnh "dẫn đường đến <điểm đã lưu B>" bằng mic, hoặc `br --es cmd say --es text "'dẫn đường đến <B>'"`. **Đúng lúc hộp thoại hiện**: `xe dumpsys window windows > $O/gmaps-popup-windows.txt`. **Dump xong**: dừng chiếu bằng đường thường của Kachi (bong bóng / nút dừng chiếu) để GMaps về màn chính, rồi trả lời hộp thoại ở đó. Không tự `am stack move`. Không thoát được thì ghi lại và báo anh, **không** thử lệnh khác (CLAUDE.md §4 câu 4) | Đọc off-car: các khối `Window{… maps…}` (số lượng, `mDisplayId`, `ty=`, `fl=`), và `mCurrentFocus`. Có cửa sổ Maps thứ hai ⇒ cửa sổ riêng; chỉ có cửa sổ activity ⇒ nằm trong cây view [SUY tên trường Android 10]. Anh chụp ảnh màn cụm để lấy chữ trên nút | Dump + ảnh (có bản đồ sống, **chỉ để trong `$O`**) | 5 |
| TRIP-HAL (tuỳ chọn) | Chip trip = số HAL | Xe đỗ, đầu chuyến: `br --es cmd hal --es op get --es dev BYDAutoInstrumentDevice --es m getCurrentJourneyDriveMileage` · `… --es m getCurrentJourneyDriveTime`. Lái vài km, **đỗ lại** rồi đo lần hai | Số HAL = số trên chip. Chip "0.0" mà HAL > 0, hoặc chip "—" mà HAL trả số ⇒ lỗi thật (`TelemetryReadout.kt:82-92`) | Output + ảnh chip | 2 |
| PKGS (tuỳ chọn, phase-2 voice) | Biết app ngoại nào **thật sự** đã cài | `xe pm list packages -3 > $O/pkgs.txt` | Chỉ để trong `$O` (dữ liệu riêng tư) | Tệp | 1 |
| DOC | Lệch tài liệu | Không cần xe | Sửa sau buổi, cùng commit tóm tắt: kênh OTA 2.79→2.81/2.82 (1.3 #10); `apk/README.md:16` (câu 2.81 "nay bị 2.82 thay; tệp đã xoá" — sai nếu không OTA, §10.3); `PROJECT-BACKLOG.md:11` ("0 DEAD" → [CHƯA BIẾT], 1.3 #11); `PROJECT-BACKLOG.md:441` (lệnh SL6 dạng ERE, §7); `README.md:53` "còn nợ dò số camera SL6"; nhãn *Chẩn đoán* ở `A11yBindJournalStore.kt:20` và `A11yBindStuckWiringContractTest.kt:146` | — | — |

```zsh
# AVM-PANO — chỉ đọc
xe "ls -ld /collect2 /collect2/autovideo /collect2/autovideo/sdk; cat /collect2/autovideo/sdk/pano_sdk.txt; ls /collect2/autovideo/"
xe "ls -l /system/lib64/libVehiclePano.so /system/lib64/libavmJniGL.so /system/lib64/libbmmcamera_jni.so"
# đường phụ khi permission denied: tìm authority, rồi đọc khoá panorama_online (5 = APA)
xe "dumpsys package providers" | grep -i carsetting                                          # → <authority>
xe "content query --uri content://<authority>/<bảng> --where \"name='panorama_online'\""     # <bảng> [CHƯA BIẾT]: thử system trước
```

Không làm hôm nay: V-oncar-6, L-RE2, CAM-HAL-MODE-6, OQ2 chip 3–8 (khung ghép hay ảnh đơn), CAM-HOLD phần helper chết (tuỳ chọn, chỉ khi dư giờ).

---

## 9. Gửi về gì, đặt tên thế nào

**Nơi đặt**:
- Mọi dữ liệu thô → `docs/diagnostics/oncar-2026-09-29/` (`$O`). Thư mục này bị `.gitignore:63` (`oncar-*/`) loại, **đúng ý**.
- **Tuyệt đối không** chép ảnh hay dump sang `docs/guide/` hay bất kỳ thư mục tracked nào (sự cố lộ ảnh 27/09, `PROJECT-BACKLOG.md:80`).
- Bản tóm tắt thì **tracked**, đặt **ngoài** thư mục đó: `docs/diagnostics/oncar-findings-2026-09-29.md`.

**Dữ liệu riêng tư**: `sched*-ws.txt` là trọn tệp `kachi_workspace`, gồm cả sổ địa chỉ theo hồ sơ (`<hồ sơ>__saved_places`, `WorkspacePrefs.kt:371-374`); bộ che chỉ che theo tên đoạn `key/token/secret/…` nên **không** che địa chỉ (`TestBridgeState.kt:300-322`). `sched*-cn.txt`, `key5-prefs-cn.txt` chứa lịch; `logcat-live-*.txt` chứa 3 000 ký tự đầu của mỗi lời đáp cầu (`TestBridgeReply.kt:57,123`). Bản JSON đầy đủ cũng nằm lại trên xe ở `files/test/` (giữ 50 tệp, `TestBridgeReply.kt:98-102,118`); dọn ở khối "Trả lại trước khi rời xe" (§6) nếu anh đồng ý. Bản sao thứ ba cũng trên xe: 3 000 ký tự đầu của mỗi lời đáp cầu nằm trong `kachi-logs/usage-*.log`, vì receiver chạy trong tiến trình chính (`AndroidManifest.xml:312-318` không khai `android:process`), dòng `Log.i` lời đáp (`TestBridgeReply.kt:58,123`) rơi vào luồng chụp `logcat --pid` của chính tiến trình đó (`KachiLog.kt:66-67`), và mỗi dòng khác nhau (ms, tên tệp) nên bộ tiết chế không bỏ (khoá tiết chế = phần sau dấu thời gian, `KachiLog.kt:106-107,119-124`) [ĐO mã]. 3 000 ký tự đầu của lời đáp `kachi_workspace` có trúng khoá địa chỉ hay không: [CHƯA BIẾT], tuỳ thứ tự khoá. **KHÔNG xoá** các tệp đó (đó là bằng chứng cho các buổi sau, §3.9 giới hạn 1); kéo hoặc chia sẻ `kachi-logs` về sau thì coi là riêng tư, chỉ để trong `$O`. ⇒ Các tệp này **chỉ** nằm trong `$O`; **không** dán vào chat, tóm tắt hay backlog. Tệp tóm tắt chỉ chép output của `sched_check` (sha1, độ dài, MOC) và `pref_get`.

| Nhóm | Tệp trong `$O` | Nguồn |
|---|---|---|
| Môi trường | `env.zsh` | §2.3 |
| Phiên bản | `key2-version.txt` (= B0), `inst-version-sau.txt`, `inst-time.txt` (= I), `Kachi-2.81-rollback.apk` (chỉ khi đăng OTA) | KEY-2, §4, §10.2 |
| Vòng đệm + live | `key1-logcat-buffers.txt`, `logcat-live-HHMMSS.txt` (mỗi lần dựng cầu một tệp) | KEY-1 |
| Dump chỉ đọc | `key2-{ps,clock,acc,svc,enabled,stack,display,alarm,procstats}.txt`, `key2-meminfo-{main,wake,tts}.txt` | KEY-2 |
| Usage log | `kachi-logs/usage-*.log` (từ 28/09 15:00), `key3-{du,ls,a11y-lines}.txt` | KEY-3, KEY-8 |
| Chẩn đoán | `key4-diag-am-start.txt` (+ `key4-diag*.png` nếu mở được) | KEY-4 |
| Prefs | `key5-state.txt`, `key5-prefs-cn.txt`, `sched{0,1}-{cn,ws}.txt`, `sched0-profiles.txt`, `sched2-{trip,goc}-cn.txt`, `sched4-ws.txt` | KEY-5, §5 |
| Đo lại | `key2b-HHMMSS-{acc,svc,ps}.txt`, `key7-HHMMSS-{acc,svc,ps}.txt`, `inst-HHMMSS-{acc,svc,ps}.txt` | §3.6 hàng 6, §3.7, §4 |
| Camera | `guide1-{l,r}{1..9}.png`, `guide2-*.png`, `guide5-<hình>-fission-d0.png` (tuỳ chọn) | §6 |
| Khác | `sl6base-seal-{product,cam,backlog-cmd}.txt`, `gmaps-popup-windows.txt`, `pkgs.txt` | §7, §8 |
| Ảnh điện thoại | `anh-<ID>-<n>.jpg` (vd `anh-KEY0-1.jpg`, `anh-SCHED0-1.jpg`, `anh-GUIDE5-{rect,round,cluster}.jpg`) | Không lộ biển số hay vị trí trên ảnh hiện trường |

**Tệp tóm tắt** ghi, mỗi mục một dòng:
- ca Z (§2.0) và câu trả lời bước 0a;
- T0 (và T0' nếu ca Z1), giờ bấm Start, W, G4 (nếu KEY-4 mở được), G5, I (giờ INST);
- P;
- hàng cây quyết định + mức bằng chứng;
- R, U, ngủ sâu, giờ máy khởi động, số wakeups (K2-8);
- ETIME;
- PSS ba tiến trình so với mốc;
- cổng ô (gói, display; do lịch / do phép thử / tự nhiên);
- kết quả SCHED-1..4, CAM-KEEP (output `diff`, hoặc "chưa đo"), GUIDE-1..5 (nấc chọn mỗi bên), CAM-B5, AVM-PANO;
- mục chưa đo.

Cùng phiên phải cập nhật `docs/README.md` (index), `docs/PROJECT-BACKLOG.md` và `.kiro/steering/project-context.md` (R2.1).

---

## 10. Sau buổi: đăng OTA, hoặc không

### 10.1 Điều kiện để đăng (đủ cả sáu)

1. SCHED-1 đạt.
2. Sau INST, phím 328 ăn, và dòng "KẸT" = 0 + `label=ClusterNav` có trong Bound (`a11y_read` trên `inst-*`).
3. GUIDE-1 đạt, không crash **sau INST** (khối dưới: không có khối crash/ANR nào của Kachi mang giờ ≥ I, không có tệp `crash-*` mốc ≥ I).
4. CAM-B5 rỗng.
5. Anh nói OK, **sau** khi đã nghe cảnh báo ở §10.2 bước 0.
6. Quét bảo mật sạch.

```zsh
# §10.1 mục 3 — chỉ xét SAU INST. I ở $O/inst-time.txt (§4): dòng 1 = giờ xe dạng logcat, dòng 2 = epoch s
cat $O/inst-time.txt
# (a) logcat sống (main,system,events,crash): Java crash, native crash, ANR, KachiCrash. Chỉ xét khối có giờ ≥ dòng 1
grep -aE -A2 'FATAL EXCEPTION|Fatal signal|KachiCrash|ANR in com\.byd\.launcher|am_anr.*com\.byd\.launcher' $O/logcat-live-*.txt
# (b) tệp crash-<ms>-<pid>.log sinh SAU I (tệp cũ hơn I là của trước buổi này, KHÔNG tính)
i=$(sed -n 2p $O/inst-time.txt | tr -d '\r')
for f in $(xe "ls /sdcard/Android/data/com.byd.launcher/files/kachi-logs/" | tr -d '\r' | grep -E '^crash-[0-9]+-[0-9]+\.log$'); do
  ms=${${f#crash-}%%-*}; (( ms >= i * 1000 )) && echo "CRASH SAU INST: $f"
done
```

Vì sao như vậy [ĐO mã]:
- Usage log kéo ở KEY-3 là của **trước** INST (2.81), nên `grep KachiCrash` trên đó không nói gì về 2.82.
- Tệp `crash-<ms>-<pid>.log` (`KachiLog.kt:179`) không bị xoá theo phiên; `DiagStorageCap` chỉ dọn khi cả cây vượt khoảng 150 MB. Liệt kê trơn thì một tệp cũ bất kỳ cũng chặn OTA oan ⇒ chỉ tính tệp có ms ≥ I.
- `KachiCrashHandler` chỉ bắt ngoại lệ Java chưa bắt (`KachiApplication.kt:26-29`). Crash native (SIGSEGV trong sherpa/GL) và ANR không đi qua đó ⇒ phải đọc logcat sống (`Fatal signal`, `ANR in`, `am_anr`).
- Đệm crash và main chứa **mọi** app ⇒ mỗi khối khớp phải xác định là của Kachi: dòng `Process: com.byd.launcher…` ngay sau `FATAL EXCEPTION`, hoặc pid khớp `inst-*-ps.txt` / dòng `am_proc_start … com.byd.launcher`. Không chắc ⇒ coi là của Kachi và hỏi anh, không tự bỏ qua.
- Dòng 2 của `inst-time.txt` không phải số (toybox `date +%s` trên ROM này: [CHƯA BIẾT]) ⇒ đổi dòng 1 ra epoch bằng tay rồi so.
- Cầu từng rớt (có > 1 tệp `logcat-live-*`) thì đệm main (khoảng 30 s) có thể đã mất đoạn giữa hai tệp ⇒ tuỳ chọn: kéo thêm `usage-*.log` có ms ≥ I (cùng vòng lặp KEY-3, đổi mốc) rồi `grep -a KachiCrash`.

Phần phím (hàng §3.6) **không** chặn OTA: mã phím 2.82 = 2.81 (§3.2), và 2.81 đã đang trên kênh.

### 10.2 Quy trình đăng (push `main` **chính là** đăng)

0. **Nói rõ với anh trước, rồi mới làm bước 1**: push `main` nghĩa là **mọi xe** bật tự cập nhật đều nhận 2.82, **kể cả SL6 của anh em**. Anh quyết; Claude chỉ chuẩn bị.
1. Giữ bản lùi **trước**, rồi đổi tên và gỡ bản cũ. `git rm` xoá luôn tệp cục bộ mà §4 dùng để lùi, trong khi buổi xe có thể chưa xong:
   ```zsh
   cp apk/Kachi-2.81-release.apk $O/Kachi-2.81-rollback.apk      # $O (env.zsh) bị .gitignore:63 loại, *.apk bị .gitignore:5 loại
   mv apk/Kachi-2.82-CHUA-DANG.apk apk/Kachi-2.82-release.apk     # khớp whitelist .gitignore:8 → hết bị loại
   git rm apk/Kachi-2.81-release.apk                               # kênh chỉ giữ MỘT tệp (apk/README.md)
   git add apk/Kachi-2.82-release.apk
   ```
2. Kiểm lại tệp:
   ```zsh
   shasum -a 256 apk/Kachi-2.82-release.apk                                   # 146411e813bf…2ec0
   JAVA_HOME=/opt/homebrew/opt/openjdk@17 ~/Library/Android/sdk/build-tools/36.0.0/apksigner verify --print-certs apk/Kachi-2.82-release.apk   # SHA-256 9257…9917 (thiếu JAVA_HOME thì im lặng)
   ~/Library/Android/sdk/build-tools/36.0.0/aapt2 dump badging apk/Kachi-2.82-release.apk | grep -E "versionCode|debuggable"   # 183 · 2.82 · không debuggable
   ~/Library/Android/sdk/build-tools/36.0.0/aapt2 dump strings apk/Kachi-2.82-release.apk | grep -c TEST_                      # phải 0 (memory kachi-signing-ota)
   ```
3. Sửa `apk/README.md`:
   - dòng 3: tệp đang phục vụ = `Kachi-2.82-release.apk` vc183;
   - mục 2.82: bỏ chữ "CHƯA ĐĂNG KÊNH";
   - mục 2.81: câu "nay bị 2.82 thay; tệp đã xoá khỏi thư mục" **đã có sẵn** ở `apk/README.md:16` — chỉ kiểm lại.

   Cùng commit sửa luôn:
   - `README.md:28-29,53`;
   - `docs/README.md`;
   - `docs/PROJECT-BACKLOG.md:3`;
   - `.kiro/steering/project-context.md:15`;
   - tệp tóm tắt buổi xe.
4. **Quét bảo mật** bằng sub-agent Opus (CLAUDE.md global §6, không có ngoại lệ):
   - phạm vi: `git diff --cached` + tệp mới (đọc trọn) + ruột APK;
   - có [BLOCK] ⇒ dừng;
   - có [WARN] ⇒ hỏi anh.
5. Commit, message trace về ID (vd `OTA 2.82 (183): nghiệm thu xe 29/09 — SCHED-1 · GUIDE · CAM-B5`), rồi push như 28/09 [ĐO transcript: `git push origin HEAD:main`]:
   ```zsh
   git push origin HEAD:main
   git push origin HEAD          # tuỳ chọn: nhánh feat trên origin đang ở 8d0d161
   ```
   Mã nguồn 2.82 đã ở trên `main` (`bfbb728`), nên lần push này chỉ mang APK và tài liệu.
6. Kiểm kênh:
   ```zsh
   curl -sIL https://github.com/dangkhoi/byd-kachi/raw/main/apk/Kachi-2.82-release.apk | grep -iE '^HTTP|content-length'   # 200 · 43877149
   curl -s "https://api.github.com/repos/dangkhoi/byd-kachi/contents/apk?ref=main" | grep '"name"'                       # chỉ Kachi-2.82-release.apk + README.md
   ```
   Trên xe: *Cài đặt › Hệ thống & quyền › Bảo trì › Kiểm tra cập nhật* ⇒ báo đã mới nhất.

### 10.3 Nếu KHÔNG OK

- **Không** đổi tên APK, **không** đưa APK lên `main`. 2.82 nằm yên ở `apk/Kachi-2.82-CHUA-DANG.apk`. Kênh vẫn là 2.81.
- Sửa `apk/README.md:16` (mục 2.81): bỏ câu "nay bị 2.82 thay; tệp đã xoá khỏi thư mục", thay bằng "đang phục vụ trên kênh `main`", trong commit tóm tắt buổi xe. Câu đó đang sai trên `main` công khai (1.3 #10).
- Commit tóm tắt (**chỉ tài liệu**, gồm cả sửa `apk/README.md:16`) được push lên `main` **sau** quét bảo mật (CLAUDE global §6) và **khi anh đồng ý**. Lần push đó không đổi kênh OTA: kênh chỉ chọn tệp khớp `Kachi-<ver>-release.apk` trong `apk/` (`UpdateChecker.kt:45,80-83`), mà commit không đụng tệp APK nào [ĐO mã]. Đây là ngoại lệ duy nhất của §0.2 ⑤.
- Ghi vào tệp tóm tắt và backlog: ID hỏng, bằng chứng (tên tệp trong `$O`), mức bằng chứng, và có cần xe nữa không.
- Cần sửa mã ⇒ **bump 2.83 (184)**. Không dựng lại dưới số 2.82 (CLAUDE.md §9).
- SCHED-1 hỏng ⇒ nhập tay lại lịch từ ảnh SCHED-0. Cân nhắc lùi 2.81 (`install -r -d`, §4, chưa từng chạy trên xe).
- Xe đang chạy 2.82 cài tay mà kênh là 2.81 thì OTA **không** kéo xuống [ĐO mã `UpdateChecker.kt:82-87`: chỉ báo có bản mới khi bản trên kênh lớn hơn bản đang cài, `cmp(bestVer, cur) > 0`].

---

## 1. Phụ lục: hôm qua (28/09) đã làm gì

Dời xuống cuối để §0 đọc được một mình. Số mục giữ nguyên, nên các chỗ trỏ "1.3 #N" trong tệp vẫn đúng.

### 1.1 Dòng thời gian (giờ VN)

| Giờ | Việc | Mức · nguồn |
|---|---|---|
| 09:24 | Owner đang trên xe (chạy **2.79**) thì phím vô-lăng chết. Chết cả **328 → Kachi** lẫn **305 → Kiki** | [ĐO transcript] |
| 09:35–09:59 | Điều tra thẳng trên xe. Dịch vụ Hỗ trợ **kẹt trong `Binding services`**, có **5 `ConnectionRecord … DEAD`**. Tiến trình launcher sống liên tục 10 g 13 ph, tức không phải crash. Chữa tay bằng force-stop rồi lắp lại thì phím sống. Tác dụng phụ: app trong ô (YouTube) rơi thành mảng đen phủ màn nhà; mở lại đúng YouTube thì app vẽ lại và khối đen hết | [ĐO `PROJECT-BACKLOG.md:7`; phần thoát mảng đen: transcript] |
| 10:17 | Owner chốt: *"cài mới thì OK, chỉ là để xe qua đêm, sáng lên chạy, thì nó mới hang"*. Tìm ra gốc AOSP | [ĐO transcript] |
| 10:27 | Owner đề xuất *"xe tắt thì tắt, xe khởi động thì khởi động lại launcher fresh"*. Báo SL6 không mở được cam phải | [ĐO transcript] |
| 11:17 | Dựng **2.80 (181)**. Lượt soát Opus bắt **2×[P0] + 3×[P1]** nên **rút 2.80**. Bản này chưa từng lên OTA | [ĐO `apk/README.md`, `git log --all`] |
| 12:15–12:57 | Dựng **2.81 (182)**, quét bảo mật CLEAN, commit `8d0d161`, push `main` ⇒ **2.81 lên OTA** | [ĐO git reflog + `git ls-tree origin/main apk/`] |
| 12:42 | Owner báo SL6 OK: *"trái phải gì đều là camera 3, tự động dải hết"* | [ĐO transcript] |
| 13:02–13:24 | Owner báo popup GMaps. Tái hiện trên máy ảo **thất bại** | [ĐO transcript] |
| ≈15:13 → 15:24 | Owner tự cài 2.81 lên Seal (tiến trình 2.81 đầu tiên lên khoảng 15:13), báo lúc 15:24. Chip trip hiện 0 một lúc rồi tự lên | [ĐO transcript: ETIME + tin nhắn; `PROJECT-BACKLOG.md:19`] |
| 15:35–15:42 | Đo ngủ sâu: **19 g 17 ph / 27 g 34 ph** (khoảng 70 %) | [ĐO `PROJECT-BACKLOG.md:17`] |
| 17:53 | Chụp **mốc gốc**: Binding `{}`, ServiceRecord `received=true hasBound=true`, PSS tiến trình chính 48 514 kB. Số dòng DEAD lúc 17:53: **[CHƯA BIẾT]** (lệnh đếm đặt chữ DEAD sai thứ tự nên không thể khớp, xem 1.3 #11) | [ĐO `PROJECT-BACKLOG.md:11`; phần DEAD: transcript] |
| 17:56–18:48 | Camera "trông xa": dò AVM 360, dịch ngược `AutoVideo.apk` ⇒ **đóng hướng**. Thử cắt hẹp thì owner gỡ (*"nhìn kỳ lắm, trả lại đi"*). `pano_sdk.txt` **chưa đọc được** vì xe rời mạng | [ĐO `PROJECT-BACKLOG.md:13,15`] |
| 19:16–21:56 | Owner: *"làm đc gì off-car thì làm đi"*. Làm vạch chuẩn, lịch theo hồ sơ, note `sau-chua-*`. Soát Opus **APPROVED** sau khi vá 3×[P1] + 2×[P2]. Dựng APK 2.82, chạy E2E vạch trên máy ảo | [ĐO transcript] |
| 22:36–22:43 | Owner: *"Dọn tên TRƯỚC rồi mới đẩy"* và *"không OTA nhé"*. Commit `998c7f4` (dọn danh tính) và `bfbb728` (2.82), push `main` **chỉ mã nguồn**. Kênh `apk/` trên `main` vẫn là **2.81** (blob `adc7fd4` = tệp cục bộ) | [ĐO `git ls-tree origin/main apk/` + `git hash-object`, chạy lại tối 28/09] |

### 1.2 Bảng hạng mục

| Hạng mục | Vấn đề (owner) | Gốc · mức | Đã làm | Trạng thái | Nợ xe (29/09) |
|---|---|---|---|---|---|
| **A11Y-BIND-STUCK** | Để xe qua đêm, sáng ra phím vô-lăng chết | Gốc [ĐO AOSP `android-10.0.0_r47`]: `AccessibilityManagerService.java:4114-4117` đẩy component ngược vào `mBindingServices` khi mối nối đứt; `:1630-1631` `continue` đứng trên cả `bindLocked()` `:1642` lẫn `unbindLocked()` `:1645` ⇒ **park vĩnh viễn**. Lối ra duy nhất đã [ĐO xe 28/09]: force-stop rồi lắp lại. Cái gì làm đứt mối nối qua đêm (spec OQ1): [CHƯA BIẾT] | 2.81: thang **NONE/TOGGLE/FORCE_STOP**, bốn cổng, lệnh tách rời, nhật ký bền `a11y-bind.log`, watchdog nhận ra "xe vừa thức" (ngủ sâu ≥ 2 giờ) thì nhả quota. 2.82 thêm note `sau-chua-ON/VAN-TAT` | Ship 2.81 (OTA + xe owner). Đường tự động **chưa từng gặp một ca kẹt thật** | **§3**: V-oncar-1..5 |
| SLEEP-PROVEN | Xe standby, 3–4 ngày mới tắt hẳn | [ĐO] ngủ sâu thật, 3 nguồn khớp nhau | Chỉ điều tra | Đóng | Đo lại hai đồng hồ (KEY-2) |
| PERF-STANDBY-FRESH | Sợ launcher sống mãi qua standby sẽ quá tải | Rò bộ nhớ: [CHƯA BIẾT]. Riêng khởi động lại tiến trình thì **không** gỡ được mục kẹt [ĐO AOSP] | Chỉ bản có điều kiện: force-stop khi thức dậy **mà đang kẹt**. Khởi động lại vô điều kiện mỗi sáng: **chưa làm** | Mở (`PROJECT-BACKLOG.md:9`) | So PSS với mốc (§3.8) |
| CAM-SL6-RIGHT | SL6 không mở được cam phải | [ĐO] ROM SL6 không nhận lệnh xuất hình 2 | 2.81: chọn **camera số 1–8** + **dải hình** mỗi bên | Owner đã test SL6 OK (camera 3 + Tự động) | CAM-SL6-PROFILE cần `getprop` của SL6 (§7) |
| CAM-GUIDE | Camera tạo cảm giác xe bên cạnh rất xa, dù thật ra chỉ cách 30 cm | [SUY hình học] ống mắt cá nén vùng 0–1 m | 2.82: `camera_guide_left/right`, Tắt/1..9, mặc định Tắt, nấc n ⇒ n/10 chiều cao. [ĐO máy ảo] nấc 5 cho vạch ở ≈0,49, không crash khi thiếu HAL | Ship 2.82, **chưa lên xe** | **§6** GUIDE-1..5 |
| CAM-SPAN narrow | (thử trên xe) | Hãng nắn theo mặt đất rồi mới cắt | Thử rồi owner gỡ, trả về STRIP | Đóng, không ship mã | — |
| AVM chim-bay | Ghép sẵn 360 như app hãng | [ĐO RE] ảnh ghép vẽ trong tiến trình app hãng, `getBuffer` trả mảng rỗng, cần chữ ký nền tảng | Đóng hướng | Đóng | `pano_sdk.txt` (PI hay APA) — §8 |
| SCHED-PROFILE | Lịch dẫn đường phải theo hồ sơ | [ĐO mã] trước 2.82 lịch theo XE | 2.82: DEVICE→PROFILE + `migrateNavScheduleOnce` (mốc `migrated_nav_schedule_v1`). [P1] mất lượt dẫn im lặng đã vá và khoá test | Ship 2.82, **chưa lên xe**. Phép di trú **chưa từng chạy trên SharedPreferences thật** | **§5** SCHED-1..4 |
| TRIP-ZERO | Chip trip km/giờ hiện 0 | [ĐO mã] "—" nghĩa là đọc hụt, 0 nghĩa là HAL trả 0. "Chuyến đếm lại sau khi nổ máy": [SUY] | Không sửa | Đóng | Tuỳ chọn TRIP-HAL (§8) |
| GMAPS-POPUP | Voice dẫn điểm khác khi đang dẫn thì GMaps hỏi đổi điểm đến, popup trên cụm không bấm được | [CHƯA BIẾT] popup là cửa sổ riêng hay view, nằm ở display nào | Tái hiện máy ảo thất bại (đã xoá Maps hệ thống khỏi AVD `clusternav10`). Chưa có mục backlog | Mở | GMAPS-POP (§8) |
| Dọn danh tính | — | — | `998c7f4`: tên thật và email → handle `dangkhoi` trong 93 tệp; `seal-nav-hud-speed-sign-offcar.html` niêm phong theo byte | Xong | — |

Số liệu bản dựng [ĐO shasum/aapt2 tối 28/09]:
- 2.81 = 43 875 077 B, sha256 `65738e50…4c9e`, **đang trên OTA**.
- 2.82 = 43 877 149 B, sha256 `146411e813bfbb095f565d3f9efefac1df170a5b4563426ce3f1f3c1ed162ec0`, **cục bộ** `apk/Kachi-2.82-CHUA-DANG.apk`, bị `.gitignore:5` loại.
- Cả hai cùng cert SHA-256 `92:57:…:99:17`, không debuggable.
- Test 2.82: 6 277/0, lint 0.

### 1.3 Đính chính so với ghi chép trong phiên (runbook làm theo cột "Sự thật")

| # | Ghi chép cũ | Sự thật | Mức |
|---|---|---|---|
| 1 | 2.81 ghi note `sau-chua-ON/VAN-TAT` | **Chỉ có ở 2.82**. 2.81 chỉ ghi `wake`/`watchdog` (watchdog) và `grant-tu-dong`/`grant-tay` (lượt grant). Sáng nay (trước INST) **không có** dòng `sau-chua-*` | [ĐO `git show 8d0d161:…/VoiceKeyKeepAliveService.kt:69`] |
| 2 | Nhật ký đọc ở *Cài đặt › Chiếu cụm › Chẩn đoán* | Nút đã gỡ từ 21/09 (`SettingsSectionsCast.kt:441-445`). `openDiagnostics()` có 0 chỗ gọi. `DiagActivity` `exported="false"` (`AndroidManifest.xml:90-93`). Bản phát hành không cho `run-as`, và `allowBackup="false"` (`:80`). ⇒ **Không có đường nào đọc `a11y-bind.log` trên xe**. Dùng đường thay thế: usage log (KEY-3) | [ĐO mã]; `am start` bị chặn: [SUY mạnh], xem KEY-4 |
| 3 | Chế độ kiểm thử tắt khi launcher khởi động lại | Theo mã, cửa sổ chỉ phụ thuộc `boot_id` và `elapsedRealtime` + 60 phút (**tính cả lúc ngủ**), nên sống qua `install -r` | [ĐO `TestBridgeWindow.kt:29,41-52`]; trên xe [CHƯA BIẾT], chốt bằng `br --es cmd state` |
| 4 | Mốc "`:wake` + `:tts` 176 286 kB" | Lệnh 17:53 nối thêm `head -2` sau `grep -E '^ *TOTAL'` ⇒ chắc chắn là số của **một** tiến trình. Là `:wake`: `dumpsys meminfo` chỉ khớp đối số đầu (09-26 đo `:wake` 180 114 kB). `:tts` **chưa có mốc** | Một tiến trình: [ĐO transcript 17:53]; là `:wake`: [SUY AOSP `ProcessList.java:3092-3111`] |
| 5 | Tiến trình chính sống liên tục từ lúc cài 2.81 | 15:36 PID 25121 (ETIME 23:38) → 17:53 PID 3920 (ETIME 1:56:30) ⇒ **khởi động lại khoảng 15:57** mà phiên không làm gì. Lý do: [CHƯA BIẾT]. PID rơi từ 25 nghìn xuống 3920 gợi ý cả đầu xe đã khởi động lại [ĐOÁN]; cũng có thể chính là đường tự chữa → KEY-8 | [ĐO transcript] |
| 6 | Spec V-oncar-2 "ở B phải bỏ toggle" | Mã **luôn toggle trước**, chỉ leo khi toggle thất bại (`NavConnect.kt:232,322-377`) | [ĐO mã] |
| 7 | Spec V-oncar-3 hứa câu "đã thử một lần trong lần nổ máy này" | Câu đó **không tồn tại**. Phép kiểm đúng: đường tự động không giết lần hai (`:105`), bấm tay thì vẫn giết (`:103`) | [ĐO spec Pass 2 + mã] |
| 8 | Spec §4.1 ④ "OTA không gỡ được B" | Mâu thuẫn AOSP `:398-415` (`removeIf` khi thay gói) | [ĐO AOSP]; ROM [CHƯA BIẾT] |
| 9 | Tiền đề "lúc xe thức thì ô còn rỗng" (R4) | Launcher sống qua standby, không có mã nào gỡ app khỏi ô khi tắt màn. Ô có app thì sáng ra **vẫn `visible=true`** ⇒ cổng đóng | [SUY mã], KEY-2 đo |
| 10 | `README.md:28-29`, `docs/README.md:76-77`, `PROJECT-BACKLOG.md:3`, `.kiro/steering/project-context.md:15` ghi kênh OTA là 2.79. `apk/README.md:16` (đã lên `main`) ghi trước câu 2.81 "nay bị 2.82 thay; tệp đã xoá khỏi thư mục" | Kênh là **2.81** từ 12:57 28/09 (`git ls-tree origin/main apk/` vẫn là `Kachi-2.81-release.apk`, blob `adc7fd4`). Câu ở `apk/README.md:16` sai cho tới khi 2.82 được đăng. Sửa sau buổi xe (§10.2 bước 3 / §10.3) | [ĐO git] |
| 11 | Mốc 17:53 "**0** ConnectionRecord mang cờ DEAD" (`PROJECT-BACKLOG.md:11`) | Lệnh đo là `grep -c 'NavAccessibilityService.*DEAD'`, trong khi ROM in chữ DEAD **trước** tên component: `ConnectionRecord{a1bb0bc u0 CR FGSA DEAD com.byd.launcher/…NavAccessibilityService:@47cf4af}` ⇒ lệnh luôn ra 0, kể cả lúc kẹt. DEAD lúc 17:53: [CHƯA BIẾT]. Phần mốc còn giá trị: Binding `{}` + `received=true hasBound=true` | [ĐO transcript: dump kẹt sáng 28/09 + lệnh lúc 17:53] |

---

## 11. Nguồn

**Spec**
- `docs/specs/kachi-a11y-bind-stuck-autofix.html` (§4.1, §4.8, §6.2 `:583-588`, OQ1/OQ2/OQ4 `:599`)
- `docs/specs/kachi-camera-distance-guide.html`
- `docs/specs/kachi-camera-source-picker.html`
- `docs/specs/kachi-profile-scope-nav-schedule.html`
- `docs/specs/kachi-test-bridge.html`

**Mã: phím**
- `app/src/main/java/com/byd/clusternav/VoiceKeyKeepAliveService.kt` (2.81 = `git show 8d0d161:`: `:46-77,158-168`)
- `app/src/main/java/com/byd/clusternav/NavConnect.kt:61,71-88,184-234,258-305,322-377`
- `core/src/main/kotlin/com/byd/clusternav/modules/navaccess/AccessibilityHealGates.kt:57-58,91-107`
- `core/src/main/kotlin/com/byd/clusternav/modules/navaccess/AccessibilityRebind.kt:194-214`
- `core/src/main/kotlin/com/byd/clusternav/modules/navaccess/A11yBindJournal.kt:32,44-54,71-85`
- `app/src/main/java/com/byd/clusternav/modules/navaccess/A11yBindJournalStore.kt:16-26,29,41,60-88`
- `app/src/main/java/com/byd/clusternav/Prefs.kt:19,132,137,460-490`
- `app/src/main/java/com/byd/clusternav/RebindReceiver.kt:55-63,206,227-230`
- `app/src/main/java/com/byd/clusternav/launcher/PermissionPreflight.kt:258,284-293`
- `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/StackParse.kt:67-92,180-195,205-212`
- `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/DisplayParse.kt:197-212`
- `core/src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/ClusterDisplayResolver.kt:33`
- `app/src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeKeys.kt:40-106,188-196,291-300`
- `app/src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt:112-133,204-215`
- `app/src/main/java/com/byd/clusternav/launcher/SettingsSections.kt:272-305`
- `app/src/main/java/com/byd/clusternav/launcher/SettingsSectionsKeys.kt:53-58,82`
- `app/src/main/java/com/byd/clusternav/modules/clustercast/DiagActivity.kt:151-208`
- `app/src/main/java/com/byd/clusternav/launcher/SettingsSectionsCast.kt:441-445`
- `app/src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeSystem.kt:71`
- `app/src/main/AndroidManifest.xml:80,88-93,117-121,142-148,153-156,209,245,262,312,323`
- `app/src/main/java/com/byd/clusternav/launcher/KachiLog.kt:18-21,32,55-100`
- `app/src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt:212,216,314-345`
- `app/src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt:262-266,460-492`
- `app/src/main/java/com/byd/clusternav/DiagStorageCap.kt:12-17,52-67` · `core/src/main/kotlin/com/byd/clusternav/core/StorageCapPlanner.kt:17`
- `app/src/main/res/values/strings_kachi.xml:276-282,459-462,519-522`
- `core/src/test/kotlin/com/byd/clusternav/modules/navaccess/AccessibilityBindingStuckTest.kt:27-29,45-47,80`

**Mã: cầu kiểm thử, lịch, camera, OTA**
- `app/src/main/java/com/byd/clusternav/launcher/testbridge/KachiTestBridge.kt:50-62,430-440,468`
- `app/src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeReply.kt:47,53-66,98-126`
- `app/src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeNoHome.kt:27`
- `app/src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeState.kt:300-335`
- `app/src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeStore.kt:40-50`
- `core/src/main/kotlin/com/byd/clusternav/launcher/testbridge/TestBridgeWindow.kt:29,41-60`
- `core/src/main/kotlin/com/byd/clusternav/launcher/testbridge/TestBridgeJson.kt:44-68`
- `core/src/main/kotlin/com/byd/clusternav/launcher/SettingsCatalogGroups.kt` (id `keys`/`car`/`system`/`nav`)
- `app/src/main/java/com/byd/clusternav/automation/AutomationService.kt:114,223-225` (cả bản 2.81)
- `app/src/main/java/com/byd/clusternav/automation/ScheduledNavApplier.kt:51,55-94`
- `app/src/main/java/com/byd/clusternav/launcher/WorkspacePrefsProfile.kt:229-266,368-374`
- `app/src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt:152,371-374,497`
- `app/src/main/java/com/byd/clusternav/launcher/ProfileIoStore.kt:18-31` · `HomePanels.kt:184-187`
- `core/src/main/kotlin/com/byd/clusternav/launcher/ProfileScope.kt:42,392,408-413`
- `core/src/main/kotlin/com/byd/clusternav/launcher/PrefSnapshot.kt:37-61,71-90,136-150`
- `app/src/main/java/com/byd/clusternav/UpdateChecker.kt:45,80-87`
- camera: `CameraGuide.kt`, `CameraGuideLineView.kt`, `CameraOverlayView.kt`, `CameraSignalController.kt`, `CameraPanoCrop.kt`, `SettingsSectionsCamera.kt` (dòng trích theo luồng camera, chưa đọc lại hết trong lượt này)

**AOSP `android-10.0.0_r47`**
- `AccessibilityManagerService.java:398-415,454-482,1630-1645,4114-4117`
- `AccessibilityServiceConnection.java:209-210,253,265`
- `ProcessList.java:3092-3111`
- `ActivityManagerService.java:4642,12195-12208`
- `NotificationManagerService.java:4738-4757` (thông báo FGS `IMPORTANCE_MIN` được nâng lên `LOW`)
- `PackageManagerShellCommand.java:2402-2403`
- `PackageInstallerService.java:502-503,633-636`

**Tài liệu và memory**
- `docs/PROJECT-BACKLOG.md:3,7,9,11,13,15,17,19,23,80,440-447`
- `apk/README.md` (`:3`, `:16`)
- `docs/diagnostics/adb-car-tunnel-macos.md:5,31-38`
- `docs/diagnostics/oncar-runbook-2.69.md:15`, `oncar-runbook-2.75.md:16`, `oncar-runbook-2.76.md` (khuôn; các dòng "tắt sau mỗi lần launcher khởi động lại" và "chụp Chẩn đoán" **đã cũ**)
- `docs/diagnostics/b3-cluster-arrow-e2e-emulator-1920x720-2026-08-21.md:18-22,128-130`
- `docs/archive/diagnostics/oncar-handoff-2026-08-13.md:29,57` · `docs/archive/_handoff/cluster-hud-injection-STATE.md:73` · `docs/archive/review/HANDOFF-2026-07-23-oncar-freeze.md:28`
- `docs/archive/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md:63-67`
- `.kiro/steering/documentation-and-backlog.md:21,24`
- memory: `kachi-adb-car-tunnel`, `kachi-signing-ota`, `kachi-276-closing-state`, `kachi-home-alias-gui-install`
- transcript phiên 28/09 (các mốc giờ ghi ở §1.1; dạng `ConnectionRecord … DEAD`; ServiceRecord `NavNotificationListener`; `taskId=80`; lệnh `git push origin HEAD:main`; dải địa chỉ cầu nc)
