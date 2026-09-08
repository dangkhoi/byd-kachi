# Cold-bootstrap seed tự hạ xuống RECOVERY_PENDING, không có đường quay lại — đo trên xe 2026-07-30

Xe: DiLink3.0 (BYD_AUTO), adb `<vehicle-ip>:5555`, app **0.80** vừa cài đè `0.77`. Triệu chứng: mở app, bấm
nút nổi không "ăn" gì cả; màn Home hiện tiêu đề "Cần xử lý thủ công" và dòng trạng thái "Chưa nhận diện
được trạng thái cụm". Mọi hành động Cast/Stop đều bị khoá, chỉ còn "Mở Chẩn đoán" (read-only, không sửa
được gì).

Đây là lỗi ĐẦU TIÊN gặp trong phiên test sáng nay — xảy ra TRƯỚC lỗi Stop-không-ăn-với-VietMap (xem
`cast-stop-recovering-stuck-2026-07-30.md`), và là lỗi khác cơ chế, dù triệu chứng bề mặt giống nhau
(cùng câu "Chưa nhận diện được trạng thái cụm").

## Bằng chứng thô

`adb shell run-as com.byd.clusternav cat files/cast-v2/session.env` đọc ngay lúc bug đang xảy ra, field
`stable=` giải mã base64:

```
state=RECOVERY_PENDING, engineVersion=V2, sourcePkg=runtime-bootstrap,
targetClass=seal-dl3-cold-bootstrap-v1, displayIdentity=display-1,
target=null, protectedResidue=null,
baseline="0,0,1920,720,320,android-user-0", lastVerifiedAtEpochMillis=1785326866773
```
`epoch=4`, `stop=0` (chưa ai bấm Stop lần này — khoá xảy ra trước khi kịp thao tác gì).

## Root cause — đã chứng minh (đọc source, khớp state thật)

1. `sourcePkg=runtime-bootstrap` / `targetClass=seal-dl3-cold-bootstrap-v1` là hai chuỗi CHỈ được ghi bởi
   bước cold-bootstrap (`core/.../v2/CastCoordinator.kt:388` và `core/.../v2/CastColdBootstrap.kt:359`),
   và CẢ HAI nơi đó ghi `StableState.IDLE_VERIFIED` — một phiên "giả định" để có định danh cụm trước khi
   cast thật lần đầu, kèm khung hình ĐOÁN TRƯỚC (`baseline` ở trên).
2. Lượt tự-kiểm-tra kế tiếp — `revalidateStable()` (`app/.../cast/platform/CastAndroidLifecycle.kt:117-123`)
   — lấy hai mẫu quan sát thật, so với khung hình đoán ở bước 1; không khớp thì hạ thẳng xuống
   `RECOVERY_PENDING`:
   ```kotlin
   state = if (converged) stable.state else StableState.RECOVERY_PENDING
   ```
   Với một phiên MỚI SEED (chưa từng cast thật), gần như chắc chắn khung hình đoán không khớp quan sát
   thật lần đầu — nên gần như MỌI lần cài mới/xoá dữ liệu đều đi qua nhánh hạ cấp này.
3. `RECOVERY_PENDING` không có transaction (`tx=null`) rơi vào `CastRuntimeUi.render()` (dòng ~30-38): chỉ
   nhận diện được 2 kiểu phục hồi hợp lệ — `phoneSessionConnected==true` (điện thoại đang nối) hoặc
   `phoneSessionConnected==false && destructiveRecoveryEligible==true`. Một phiên cold-bootstrap giả định
   (không phải app thật, không `activeTarget`) không khớp cả hai điều kiện đó → `recovery=null`.
4. `CastUiStateProjector.project()` với `recovery=null`, không transaction, `destructiveRecoveryEligible`
   null-hoặc-false đều rơi cùng một chỗ: `stableConverged` không có nhánh nào xử lý `RECOVERY_PENDING`
   (chỉ IDLE_VERIFIED/ACTIVE_VERIFIED/ACTIVE_DEGRADED có nhánh riêng) → `failClosed(input)` →
   `UnavailableReason.CONTRACT_UNMAPPED` → khoá hết Cast/Stop, chỉ còn Chẩn đoán.

Đáng chú ý: code ĐÃ CÓ một bản vá đúng triệu chứng này từ 2026-07-26
(`CastLifecycleMigration.migratePristine`, comment tại dòng 24-29 của `CastAndroidLifecycle.kt`, viết:
*"That renders as 'Cần xử lý thủ công · contract unmapped' with every control disabled, and it never
recovers... Adopting the observation as a reclaimable session keeps the claim truthful... leaving Stop
available"*) — nhưng bản vá đó chỉ chặn nhánh `migratePristine` (lần đầu chạy trong khi cụm ĐANG có app),
KHÔNG chặn nhánh cold-bootstrap-seed-rồi-tự-hạ-cấp ở trên, dùng sourcePkg khác
(`runtime-migration-unowned` so với `runtime-bootstrap`).

## Mức bằng chứng (CLAUDE.md §2)

| Khẳng định | Mức |
|---|---|
| Phiên kẹt là do cold-bootstrap seed IDLE_VERIFIED rồi bị `revalidateStable` hạ xuống RECOVERY_PENDING | Đã chứng minh — đọc trực tiếp `session.env` + đối chiếu 2 nơi ghi `sourcePkg` |
| RECOVERY_PENDING dạng này không có đường phục hồi trong `CastRuntimeUi`/`CastUiStateProjector` | Đã chứng minh — đọc source, không có nhánh nào khớp |
| Gần như MỌI lần cài mới/xoá dữ liệu đều gặp lại lỗi này (khung hình đoán trước hiếm khi khớp lần đầu) | Nghi là — chỉ quan sát được đúng 1 lần, chưa lặp lại nhiều lần để xác nhận tần suất |

## Cách xử lý tối nay (workaround, KHÔNG PHẢI code fix)

Trên xe: `am force-stop com.byd.clusternav` → `run-as com.byd.clusternav rm files/cast-v2/session.env` →
`am start -n com.byd.clusternav/.MainActivity`. Xoá sổ sách bền của riêng app (tự tạo lại được, không mất
dữ liệu người dùng khác), app khởi động lại COLD_PRISTINE thật (không còn field `stable=` nào), cast được
lại bình thường. Đã áp dụng 1 lần, xác nhận hết kẹt bằng cách đọc lại `session.env` sau đó.

**Đây không phải sửa lỗi** — cùng cơ chế demoting vẫn còn nguyên, lần cài đè kế tiếp (hoặc bất kỳ khi nào
`revalidateStable` gặp một seed chưa từng verify) nhiều khả năng lặp lại.

## Việc cần làm (gộp cùng task #18/#10)

1. Cho cold-bootstrap seed KHÔNG bắt buộc đúng khung hình đoán ngay từ lần verify đầu tiên — hoặc seed nó
   thẳng bằng khung hình QUAN SÁT ĐƯỢC tại chỗ (thay vì hằng số đoán trước `0,0,1920,720,320,...`), hoặc
   cho `revalidateStable` một lần "ân hạn" đầu tiên trước khi hạ cấp.
2. Nếu vẫn chấp nhận việc hạ cấp xảy ra, thì `CastRuntimeUi.render()`/`CastUiStateProjector` cần một nhánh
   thứ ba nhận ra "đây là cold-bootstrap seed chưa từng cast thật" (qua `sourcePkg=="runtime-bootstrap"`)
   và cho về thẳng `COLD_PRISTINE`/`IDLE_VERIFIED` thay vì `failClosed` — đúng tinh thần bản vá
   `migratePristine` 2026-07-26 đã làm cho trường hợp anh em của nó.
3. Thêm test hồi quy dựng từ đúng dump này: seed cold-bootstrap + hai mẫu quan sát không khớp khung đoán →
   không được rơi vào `CONTRACT_UNMAPPED`.
