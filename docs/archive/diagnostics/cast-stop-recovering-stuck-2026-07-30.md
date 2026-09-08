# Cast VietMap OK, nhưng Stop không "ăn" — transaction kẹt RECOVERING — đo trên xe 2026-07-30

Xe: DiLink3.0 (BYD_AUTO), adb `<vehicle-ip>:5555`, app **0.80** (versionCode 80, bản vừa cài đè 0.77 tối
nay). Người báo: dangkhoi — cast VietMap lên cụm OK, bấm nút nổi để trả về màn chính không có tác dụng, hiện
"Đang xử lý" rồi không có gì xảy ra, VietMap vẫn ở cụm.

Đã dừng đào sâu/vá trực tiếp trên xe theo yêu cầu — chỉ trace lấy bằng chứng, gộp lại đây để làm tiếp ở
công ty. Chưa sửa code, chưa build lại APK cho bug này.

## Bằng chứng thô

- `docs/diagnostics/artifacts/cast-stuck-recovering-2026-07-30-session.env` — `files/cast-v2/session.env`
  đọc qua `adb shell run-as com.byd.clusternav cat`, ngay tại lúc bug đang xảy ra.
- `docs/diagnostics/artifacts/cast-stuck-recovering-2026-07-30-logcat-full.txt` — `adb logcat -d`, buffer
  đầy đủ tại thời điểm đó (6658 dòng, không lọc — lọc theo tag `ClusterCastBubble`/`CastFacade` không thấy
  gì, mọi log Cast-side hiện đi qua đường khác, xem "Việc cần làm" bên dưới).
- `docs/diagnostics/artifacts/cast-stuck-recovering-2026-07-30-version.txt` — xác nhận versionCode 80.

## Giải mã field bền (base64, đã decode)

`stable=` (session cold-bootstrap, vẫn còn nguyên, KHÔNG bị VietMap ghi đè):
```
state=IDLE_VERIFIED, engineVersion=V2, sourcePkg=runtime-bootstrap,
targetClass=seal-dl3-cold-bootstrap-v1, displayIdentity=display-1
```

`tx=` (transaction đang mở cho lần cast VietMap) — **đây là chỗ kẹt**:
```
operationId=b32d746f-0295-4eeb-bb66-2f7ca10e5bfa, epoch=2, operation=CAST, phase=RECOVERING,
targetPkg=vn.vietmap.live, clusterStyle=NORMAL, displayIdentity=display-1, retries=0,
reason="unknown effect: Warning: Activity not started, its current task has been brought to the front\n",
expectedPostcondition="ACTIVE_VERIFIED or ACTIVE_DEGRADED", compensationUsed=false
```

Envelope top-level: `epoch=3` (tổng), `stop=1` (Stop ĐÃ được ghi bền — người dùng bấm ít nhất một lần và
nó có ghi lại, không phải cú bấm bị nuốt).

## Root cause — đã chứng minh (đọc source + đọc state thật, khớp nhau)

1. Lúc phát lệnh chuyển `vn.vietmap.live` lên cụm, `am start`/lệnh tương đương trả về dòng cảnh báo chuẩn
   của Android khi task đích ĐÃ TỒN TẠI sẵn: `"Warning: Activity not started, its current task has been
   brought to the front"`. Đây là dòng **lành, quen thuộc** — `CastShell.kt:29` có sẵn comment xác nhận nó
   đã từng bị cắt mất bởi `take(60)` cũ và đã được sửa để không cắt nữa. Nhưng việc SỬA đó chỉ là không cắt
   dòng log — nó không dạy cho bộ phân loại hiệu ứng (gateway ở `car-integration/.../CastAdbGateway.kt`)
   biết dòng này là một kết quả THÀNH CÔNG.
2. Gateway không nhận ra dòng này → trả `MutationResult.UnknownEffect(reason=...)` →
   `CastExecutor.kt:229` gọi `markRecovery(id, "unknown effect: ${result.reason}")` → transaction chuyển
   `phase=RECOVERING` ngay, dù cửa sổ VietMap trên thực tế ĐÃ lên cụm đúng như mắt thấy.
3. Người dùng bấm nút nổi để Stop → `stopRequested=true` được ghi bền (`stop=1`, khớp việc app xác nhận đã
   ghi). Nhưng transaction đang RECOVERING có `epoch=2` trong khi envelope hiện đã ở `epoch=3` — lệch epoch.
   `CastRecovery.decide()` (`core/.../CastRecovery.kt:24`) có điều kiện chặn cứng:
   ```kotlin
   if (... || loaded.envelope.durableEpoch != tx.epoch || ...) return RecoveryDecision.Manual(COMPENSATION_EXHAUSTED)
   ```
   → recovery tự động không bao giờ chạy được cho transaction này nữa; nó cần xử lý thủ công.
4. Vì recovery không tự chạy, mỗi lần bấm sau đó `stopAckPending()`/`dispatchInFlight` trong
   `FloatingBubbleService.kt` vẫn thấy có việc đang treo (transaction chưa đóng) → chỉ hiện lại
   `"Đang xử lý…"` (đúng dòng người dùng thấy) mà không phát lại gì mới — không phải nút không nhận chạm,
   mà là transaction cũ chưa bao giờ đóng.

**Vì sao epoch lệch (2 vs 3)**: rất có thể vì trước khi cast VietMap, phiên đã bị mình xoá `session.env`
qua adb để gỡ bug RECOVERY_PENDING trước đó trong đêm (xem hội thoại) — thao tác đó không tăng epoch của
transaction VietMap đang mở dở, trong khi cold-bootstrap/relaunch sau đó tăng epoch tổng. Cần xác nhận lại
với dump sạch (không có can thiệp adb ở giữa) để biết đây là do thao tác chẩn đoán đêm nay hay là lỗi có
thật độc lập với nó.

## Mức bằng chứng (theo CLAUDE.md §2)

| Khẳng định | Mức |
|---|---|
| Transaction VietMap kẹt ở RECOVERING vì "unknown effect" đúng dòng cảnh báo trên | Đã chứng minh — đọc trực tiếp từ `session.env` thật |
| `CastAdbGateway` chưa có pattern nhận diện dòng "brought to the front" là thành công | Đã chứng minh — grep không thấy chuỗi này trong `CastAdbGateway.kt` |
| Recovery không tự chạy được vì epoch lệch (2≠3) | Đã chứng minh — đọc trực tiếp field `epoch=` của cả envelope và tx |
| Epoch lệch là DO thao tác xoá session.env giữa đêm (không phải bug độc lập) | Nghi là — chưa có dump "sạch" (không can thiệp adb) để đối chứng |

## Việc cần làm ở công ty

1. Đọc `CastAdbGateway.kt` đầy đủ, tìm đúng nơi phân loại `MutationResult` từ output shell của lệnh
   `am start`/`am startservice`/tương đương dùng để cast. Thêm "brought to the front" (task đã tồn tại,
   được đưa lên trước) vào tập hiệu ứng ĐÃ BIẾT LÀ THÀNH CÔNG cho các bước launch — đây chính xác là kịch
   bản "app đã có task, chỉ cần mang lên cụm" mà luồng cast bình thường gặp phải, không phải lỗi.
2. Xác nhận lại việc epoch lệch có tái diễn với một lần test SẠCH (không xoá `session.env` giữa chừng) hay
   không — nếu vẫn lệch, cần một đường "xin lỗi, chuyển sang recovery thủ công có nút bấm được" thay vì im
   lặng trả "Đang xử lý" mãi (vi phạm CLAUDE.md §5: mọi state đổi ra ngoài phải có đường trả lại, kể cả khi
   epoch không khớp).
3. Không thấy log runtime của Cast trong logcat filter theo tag cũ (`ClusterCastBubble` v.v.) trong lần đo
   này — cần xác nhận `CastOperationLog.record(...)` (thấy trong `CastExecutor.kt:214,218`) ghi đi đâu (có
   thể là in-memory ring buffer đọc qua Diagnostics, không phải Logcat) để lần sau trace nhanh hơn không
   phải giải mã base64 tay.
4. Thêm test hồi quy dựng từ đúng dump này (`CastAdbGatewayTest` hoặc tương đương) khoá lại: dòng "brought
   to the front" khi launch một app đã có task sẵn phải là `MutationResult.Observed`, không phải
   `UnknownEffect`.
5. Riêng biệt: dangkhoi có yêu cầu UX — bong bóng bớt hiệu ứng mờ-tự-động (fade), luôn giữ độ rõ đủ để không
   cảm giác "ẩn hiện khó chịu". Chạm vẫn đăng ký được kể cả lúc mờ (alpha không chặn touch), nhưng cảm giác
   khó chịu là thật — cân nhắc bỏ auto-fade hoặc tăng ngưỡng alpha tối thiểu, làm cùng đợt sửa lỗi trên.

## Trạng thái xe khi rời đi

- Đã `force-stop` + xoá `files/cast-v2/session.env` MỘT LẦN trong đêm (trước lần cast VietMap này) để gỡ
  một bug RECOVERY_PENDING khác — xem phần trên. **Chưa xoá lần nữa** sau khi phát hiện bug này, để giữ
  nguyên bằng chứng cho lần đọc dump ở trên.
- VietMap vẫn đang hiển thị trên cụm (task thật KHÔNG bị ảnh hưởng bởi việc ClusterNav không đóng được
  transaction — chỉ có sổ sách nội bộ của ClusterNav bị kẹt).
- Bản 0.80 (đã cast được VietMap thành công về mặt hình ảnh) hiện đã cài trên xe; commit code redesign tối
  nay (82 file) vẫn đang bị chặn bởi 1 finding [BLOCK] từ scan bảo mật (địa chỉ nhà thật trong
  `next-car-session-plan-2026-07-29.md:118`) — cần quyết định trước khi commit, xem hội thoại.
