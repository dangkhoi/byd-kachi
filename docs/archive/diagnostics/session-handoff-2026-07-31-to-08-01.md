# Handoff phiên 31/7 tối → 01/8 sáng — Cluster Cast

Đọc file này trước khi chạm code hoặc lên xe. Nó thay cho việc đọc lại cả phiên.

**Trạng thái khi bàn giao:** APK **0.89** đã build (`app/build/outputs/apk/release/app-release.apk`),
**912 test xanh** (573 core + 309 app + 30 car-integration), **CHƯA COMMIT GÌ** (HEAD vẫn là `6fe4807`,
47 file thay đổi/chưa track). Chưa cài lên xe — xe đã ngắt kết nối từ ~2h sáng.

---

## 1. Một câu tóm tắt

Cast "thành công" ở tầng WindowManager suốt cả đêm trong khi cụm vật lý vẫn hiện đồng hồ native, và chủ xe
phải `pm clear` **5 lần**. Gốc rễ: app có **nhiều đường vào** cùng đoán trạng thái cụm, và đường bị dùng
nhiều nhất lại **bỏ qua chính ba lệnh mở cụm**. Đã sửa tận gốc, cộng 8 lỗi khác tìm ra dọc đường (4 trong
số đó do review bắt được **trước khi ship** — mỗi cái đều đủ sức phá hỏng buổi test sáng nay).

---

## 2. Gốc rễ thật — và vì sao mất cả đêm mới thấy

Triệu chứng: `am stack list` báo app đúng trên display cụm, `screencap -d 1` **chụp ra ảnh app vẽ đầy đủ**,
mà cụm vật lý vẫn là đồng hồ. Cả đêm đi tìm ở tầng đặt cửa sổ — sai chỗ.

Sự thật (ĐÃ CHỨNG MINH, đo trực tiếp qua adb):

> **Display ảo tồn tại ≠ đường chiếu đã nối tới màn hình vật lý.**
> `fission_bg_xdjaVirtualSurface` (displayId=1) do `com.xdja.containerservice` giữ **thường trực** — có mặt
> suốt đêm kể cả khi cụm hiện đồng hồ. App đặt lên nó vẽ thật vào buffer. Nhưng chỉ ba opcode
> `service call AutoContainer 2 i32 1000 i32 30 / 16 / 35` mới bật đường ra màn hình. Gửi tay ba lệnh đó →
> cụm chuyển sang hiện GMaps **ngay lập tức**. Gửi `18` rồi `0` → về đồng hồ.

Mà `CastColdBootstrap.run` tính `adopt = discoverClusterDisplayId(...) != null` rồi **bỏ qua cả ba opcode
khi display đã tồn tại** — trên xe này nó *luôn* tồn tại. Nên ba lệnh gần như **không bao giờ được gửi**.

Tầng thứ hai của cùng gốc rễ: `CastLifecycleMigration.migratePristine` thấy cụm rảnh thì ghi luôn
`IDLE_VERIFIED` (reason `runtime-migration`, `profileExport = null`) mà **không gửi lệnh nào**; mọi cast sau
đó đi nhánh `executeOrdinary` nên bootstrap thật không bao giờ chạy.

Hai trường phân biệt hai đường **đã có sẵn** trong `session.env`, chỉ là chưa ai dùng để chặn:

```
stable=IDLE_VERIFIED|V2|runtime-migration |~                         |display-1|…  ← đường tắt
stable=IDLE_VERIFIED|V2|runtime-bootstrap |seal-dl3-cold-bootstrap-v1|display-1|…  ← đường thật
```

---

## 3. Đã sửa gì (và test nào khoá lại)

| # | Sửa | Test khoá |
|---|---|---|
| 1 | **Luôn gửi thang opcode mở cụm** — bỏ cổng `adopt` sai | `CastColdBootstrapTest.an existing clean cluster display still dispatches the full seal ladder` (đảo ngược có chủ đích test cũ) |
| 2 | **Chặn đường tắt migration** bỏ qua bootstrap | `CastManualIntentTest.migration claim without a real bootstrap runs the bootstrap ladder…` |
| 3 | **Chờ xác minh 0.5s → 3s**, tự co theo hạn còn lại, + nghỉ thật giữa hai mẫu | `production verification delay gives the mutation ladder real settle time` |
| 4 | **Nút "Trả cụm về đồng hồ" (cứu hộ)** — thay cho `pm clear` | `CastClearClusterTest` (7 test, dựng từ dump 2-occupant thật) |
| 5 | **Sửa kéo nút nổi** (hỏng do chính commit `9d70f62`) | `BubbleDragGestureTest` (3 test — trước đó **không có test nào** phủ drag) |
| 6 | **Nút nổi 3 ô** + là bản đồ trạng thái cụm | `CastBubbleProjectionTest` (+8) |
| 7 | **App-op PIP luôn có đường trả lại**; cold-bootstrap không nhận trạng thái đang-bị-chặn làm baseline | `CastPipResidueTest` |
| 8 | **Quan sát bounds theo task** (nền cho resize + 2 app) | `CastTaskBoundsObservationTest` |
| 9 | **Chiếu 2 app** — mô hình ô, planner, transport theo rect ô, xác minh chấp nhận 2 occupant | `CastClusterSlotTest` (~20 test), `CastHalfZoneWiringTest` (7) |
| 10 | **Nhãn rủi ro `am display move-stack`**: `READ_ONLY` → `MAY_HANG_SYSTEM` | `CandidateRiskLabelTest` (vá luôn lỗ lưới để lần sau bắt được) |

---

## 4. Bốn lỗi P0 review bắt được TRƯỚC KHI SHIP

Đáng ghi lại vì cả bốn đều "code đúng, test xanh, mà vẫn hỏng trên xe".

1. **Cổng một-chế-độ không bắt sau mỗi lần Dừng.** Thang STOP ghi `createdByBuild = "runtime"`, không phải
   `"runtime-migration"` — nên **lượt chiếu thứ 2 trở đi lại rơi vào đúng bug cũ**. Sẽ phá đúng bài test 10
   lượt. Đã sửa: cổng chỉ hỏi `profileExport == null`, ba guard trạng thái lo phần còn lại.
2. **Stop từ `IDLE_VERIFIED` (không có target) ghim journal ngay bước đầu.** `RETURN_NORMAL_TO_MAIN` dựng
   lệnh từ package rỗng → `null` → gateway Rejected → RECOVERING vĩnh viễn. Sửa: `pkg == null → NO_OP`, khớp
   quy ước 5 CommandKind anh em.
3. **`sessionConfirmed` vô tình đổi luôn hành vi CHẠM bong bóng**, không chỉ màu — cú chạm lúc RECOVERING
   thành im lặng hoặc phát cast mới lên cụm bẩn. Sửa: tách `projecting` (vẽ) khỏi `stopOnTap` (chạm).
4. **Trả app từ cụm đang chia đôi làm mất cả hai app + kẹt sổ.** `activeTarget` là `null` khi hai app cùng
   `visible=true`, nên Stop chạy với target rỗng: đồng hồ về nhưng hai app còn nằm vô hình trên cụm.

---

## 5. CarPlay / Android Auto — ĐỪNG CHIẾU

**Đã chứng minh, 3/3 lần:** `am display move-stack` (đường **duy nhất** cho app không-exported như CarPlay)
làm **sập system_server**, task biến mất khỏi hệ thống, phải cắm lại cáp.

Truy tới đúng hai dòng AOSP `DisplayContent.java:2401-2402`: stack bị gỡ khỏi display cũ (`mDisplayContent
= null`) rồi sóng đổi cấu hình nổ **trong lúc** gắn vào display mới, trước khi con trỏ mới kịp gán. Kích
hoạt khi **vượt ranh giới FREEFORM** — display 0 fullscreen, cụm freeform, nên lần nào cũng vượt. Tắt
animation vô dụng (`mDisableTransitionAnimation` là resource build-time). **Android 10 không có bản vá** ở
bất kỳ nhánh release nào; Android 11 sửa bằng refactor cấu trúc.

`am start -n` cũng không dùng được: activity **không exported** (SecurityException).

**Đường vòng khả thi (mức: nghi là, chưa đo):** `am stack move-task` — chuyển TASK vào stack đã nằm sẵn trên
display đích, không chuyển cả stack qua display, nên không rơi vào cửa sổ null (`TaskStack.java:547-548` gán
`task.mStack` **trước** `addChild`). Kế hoạch test T0–T7 đã xếp sẵn.

Chi tiết: `docs/diagnostics/carplay-aa-cluster-placement-research-2026-08-01.md` (1056 dòng) và
`carplay-move-stack-npe-crash-2026-08-01.md`.

DashCast (sản phẩm tiền nhiệm) **chưa bao giờ giải được bài này** — quét 459KB changelog + 45MB decompile:
0 lần nhắc CarPlay, 0 lần dùng `move-stack`. Không có gì để kế thừa.

---

## 6. Chiếu 2 app — bài học đáng nhớ nhất về mặt thiết kế

Ban đầu bị chặn vì đòi biết "dải nội dung" theo chiều **dọc** trước khi chia ô — mà chiều dọc là thứ hệ
thống tự áp (đo được lệch 90px đêm 31/7, nhưng dump 21/7 lại cho `overscan (536,224,88,336)`, tức **không
suy ra được**). Suýt phải nhờ chủ xe đo tay.

Câu hỏi của chủ dự án ("nhấn nút trái thì chiếu 1 góc bên trái đúng không?") làm lộ ra là **chặn nhầm trục**:

> Chia cụm là quyết định theo chiều **NGANG**. Hai app chỉ cần không đè nhau theo chiều ngang là đủ. Chiều
> **DỌC** là thứ app không chọn và cũng không cần chọn.

Và bằng chứng còn chặt hơn thế: các số `0 / 960 / 1920` gõ tay đêm 31/7 **chính là toạ độ ngang của khung
display**, và `am stack list` đọc lại đúng như vậy — nên cắt ô theo chiều ngang từ khung display là **đã
chứng minh**, kể cả khi cụm rỗng. Còn `90 / 810` nằm *ngoài* dải `[0,720]` nên không nói gì về chiều dọc.

Kết quả: phép đo không cần nữa, thiết kế **chịu được cả hai đáp án** của chiều dọc, có test khoá cả hai.

---

## 7. Còn nợ / chưa đo

| Việc | Trạng thái |
|---|---|
| **T5 — nghiệm thu 10 lượt chiếu/trả trên xe** | ⛔ Cần xe. **Chặn mọi thứ khác.** |
| Chiếu 2 app trên xe thật | ⚠️ Bấm được nhưng **chưa chạy lần nào** |
| Bong bóng PIP **trên cụm** | ⛔ Không có lệnh nào đẩy task xuống nền trên display phụ; 2 ứng viên (`am stack move-task … false`, `input -d 1 keyevent 3`) **chưa đo**. Viết code trước là lỗi §14. |
| App nào nằm bên nào khi 2 app cùng visible | Xác minh chứng minh "đúng 2 app, đúng 2 dải ngang", **chưa** chứng minh "A là bên trái". Xấu nhất: đổi chỗ, chạm lại là xong. |
| CarPlay/AA | ⛔ Xem §5 |
| `isDebuggable = true` trong release | ⚠️ Vẫn bật (cần `run-as` để chẩn đoán). **Tắt trước khi phát hành thật.** |
| Commit + security scan | ❌ Chưa làm. 47 file đang chờ. |

Chưa đo, không chặn dùng: dải dọc hệ thống trả về cho rect display-space; liệu dump có luôn in `bounds=`
cho task vừa đặt; provenance của `observed.geometry` khi cụm không rỗng (vẫn là regex qua
`wmDisplays + displays`, chưa chứng minh là của cụm chứ không phải màn giữa).

---

## 8. Tài liệu sinh ra trong phiên

| File | Nội dung |
|---|---|
| `docs/specs/cast-one-mode-and-three-zone-bubble.html` | **Spec chính.** R1–R8, B1–B4, T1–T10, + Reviewer Log Pass 1 & Pass 2 |
| `docs/specs/cast-secondary-app-corner-overlay.html` | Spec ý tưởng bong bóng góc (viết trước, phần lớn đã bị §6 thay thế) |
| `docs/diagnostics/oncar-checklist-2026-08-01.md` | **Checklist sáng nay** — bảng 10 lượt, cách đọc câu app nói |
| `docs/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md` | Crash NPE, bằng chứng + hậu quả |
| `docs/diagnostics/carplay-aa-cluster-placement-research-2026-08-01.md` | Truy AOSP tới file:line, đường vòng, kế hoạch T0–T7 |

---

## 9. Việc tiếp theo, đúng thứ tự

1. **Cài 0.89, chạy mục 1 checklist** (10 lượt chiếu/trả, 2 app khác nhau). Tiêu chí: **0 lần** đụng adb.
   Lỗi P0 #1 chỉ lộ từ lượt thứ 2 — chiếu một lần rồi kết luận là chưa test gì cả.
2. Xanh rồi mới thử chiếu 2 app (mục 9 checklist).
3. Security scan + commit (CLAUDE.md §6, không skip).
4. Tắt `isDebuggable`.
5. Bong bóng PIP trên cụm: đo trước, code sau.
6. CarPlay/AA: chạy T0–T7 trong tài liệu nghiên cứu, **khi xe đỗ**.

---

## 10. Ba bài học đáng ghi vào CLAUDE.md nếu tái phạm

1. **"Trạng thái tồn tại" ≠ "trạng thái đang hoạt động."** Cổng `adopt` sai vì đánh đồng display ảo tồn tại
   với đường chiếu đang mở. Trước khi tối ưu bằng cách bỏ qua một bước, phải chứng minh bước đó **thừa**,
   không phải chỉ "có vẻ đã làm rồi".
2. **Ràng buộc đúng trục.** Cả tính năng 2 app bị chặn vì đòi biết một đại lượng mà app **không điều khiển**.
   Khi bế tắc vì thiếu dữ liệu, hỏi lại: dữ liệu đó có thật sự cần cho quyết định đang làm không?
3. **Refactor gỡ mất call site thì test tĩnh không bắt được.** Drag hỏng từ `9d70f62`, compile xanh, 299 test
   xanh, nút đứng yên trên xe cả tuần — vì **không test nào chạm tới nó**. §8 đã cảnh báo đúng lớp lỗi này.
