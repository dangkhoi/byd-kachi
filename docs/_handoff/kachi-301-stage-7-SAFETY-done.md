# kachi-301 · stage 7 · SAFETY → một luật mở cốp cho chạm · giọng nói · phím — DONE off-car, chưa commit

Owner 13/10: *"vậy làm theo luật mới hết đi cho chắc ăn"* — giọng nói và phím vô-lăng dùng CÙNG luật fail-safe của cú chạm
(stage 7 LIGHTS §1). Tới bản này giọng nói/phím dùng `blockedAtSpeed` (không đọc được tốc độ ⇒ CHO mở).

## Luật (một phép duy nhất `:core` `CtlSafetyPolicy.blocked(id, arg, motion)`)
| tốc độ | số | MỞ cốp (`arg > 0`, mã thuộc `REQUIRES_STATIONARY`) |
|---|---|---|
| > 0 | bất kỳ | CHẶN |
| = 0 | bất kỳ | cho |
| không đọc được | P (`"P"` / HAL `3`) | cho |
| không đọc được | R/N/D/`255`/không đọc | CHẶN |
| — | — | ĐÓNG luôn cho, không tốn lượt đọc |

`blockedAtSpeed` + `blockedOnTap` đã XOÁ (gộp thành `blocked`). `motion` gọi lười như trước.

## Một phép đọc — `CtlSafetyPolicy.readMotion(fresh, snapshot)`
Đọc `speed` tươi → lùi ảnh chụp → `CarStatusTap.apply` (số giả vehicleTest); số `gear` chỉ đọc khi không có tốc độ. Dời nguyên
từ `TileSafetyGate.motion` xuống `:core`. Nơi dùng:
- chạm: `TileSafetyGate.motion(ctx)` = `readMotion(fresh = c::readFresh, snapshot = { c.carStatusRepository.status.value })`
- giọng nói + phím: `VoiceControlDispatch.motion()` = `readMotion(fresh = freshCar, snapshot = { state().carStatus })`
  (phím: `KeyCtlDispatch` dựng `VoiceControlDispatch` với `freshCar = readFresh`, `state` = ảnh chụp repo — cùng nguồn với chạm;
  phiên giọng nói: `freshCar` của `VoiceWiring` = `readFresh`).
- `VoiceControlDispatch.speedKmh()` XOÁ; `KeyCtlPlan.of(..., motion = controls::motion)` thay `speedKmh =`.

## Câu từ chối
`VoiceReply.notWhileMoving` nay = **"Dừng xe để mở cốp"** (`Strings.t` + 3 dòng `i18n/{zh,th,ms}.tsv` thay dòng cũ "chỉ mở được
khi xe đang dừng") — trùng chữ `kctl_stop_to_open_trunk` ở 5 ngôn ngữ (bài khoá). Kênh sẵn có: giọng nói `say` (đọc lên), phím
`say` = toast của `KeyCtlDispatch`. Không thêm kênh mới. Bỏ tiền tố "✗ Mở cốp — …" để đúng câu cổng chạm.

## Tệp đổi
- `:core` main: `CtlSafetyPolicy.kt` (blocked + readMotion, xoá 2 phép cũ) · `KeyCtlPlan.kt` (`motion`) · `voice/VoiceReply.kt` ·
  `resources/i18n/{zh,th,ms}.tsv` (1 dòng mỗi tệp).
- `:app` main: `TileSafetyGate.kt` · `ControlTileFactory.kt` (1 dòng `blocked(`) · `VoiceControlDispatch.kt` · `modules/voicekey/KeyCtlDispatch.kt`.
- test: `CtlSafetyPolicyTest` · `CtlTrunkTapSafetyTest` · `KeyCtlPlanTest` · `ControlLastSentTest` · `KeyCtlSafetyTest` ·
  `VoiceActGateReadbackTest` · `VoiceDispatcherSafetyTest` · `KeyCtlWiringContractTest` · `TrunkTapGateContractTest`.
- Không chạm: Car3d*/BydAvm*/build.gradle/WidgetRegistry/Camera*/HalSignal*/TurnSignal*.

## Test đổi có trích owner 13/10 (từng khoá fail-open)
- `CtlSafetyPolicyTest.blockedAtSpeed …` → `blocked chan dung MO cop khi chua chung minh xe dung…` (`null` nay CHẶN).
- `VoiceActGateReadbackTest.khong doc duoc toc do thi van cho mo` → `khong doc duoc toc do thi chi so P moi cho mo` (P/3 mở;
  D/R/N/255/null chặn + câu cổng chạm) + mới `toc do doc duoc thang so`.
- `KeyCtlPlanTest.lenh hong…` (`speed = null ⇒ MỞ`) → P ⇒ MỞ · D/null ⇒ MEMORY_SAFE; `noSpeed` → xe đỗ (0, P); `plan` mặc định 0 km/h.
- `KeyCtlSafetyTest` Rig mặc định xe đỗ (0, P); câu từ chối = câu cổng chạm; mới `mo cop bang phim khi khong doc duoc toc do thi
  chi P moi mo` + `dao cop tu tri nho khi khong biet chuyen dong thi DONG`.
- `VoiceDispatcherSafetyTest` Rig: xe đỗ (bài canh cổng HỎI-LẠI, không canh chuyển động).
- Mới: `CtlSafetyPolicyTest.readMotion…` · `TrunkTapGateContractTest.ba duong cung mot phep quyet va mot phep doc` (cấm
  `blockedAtSpeed`/`blockedOnTap`/`fun speedKmh()` quay lại) · `…cau tu choi giong noi trung chu cau cong cham o ca 5 ngon ngu`.

## Kết quả [ĐO XML, gradle-locked.sh]
- core (`*CtlSafety* *CtlTrunk* *KeyCtl* *ControlLastSent* *LangCoverage* *LayeringRules* *VoiceSpokenLang* *I18n* *Trunk*
  *VoiceReply*`): **131 / 0 / 0**.
- app (`*CtlSafety* *Trunk* *TileSafety* *Voice*Control* *VoiceControlDispatch* *KeyCtl* *KeySource* *LauncherI18n* *LayeringRules*
  *VoiceActGate* *VoiceWriteLane* *VoiceDispatcher* *ControlTile* *VoiceFast*`): **146 / 0 / 0**. Full suite CHƯA chạy.
- Thử ĐỎ: `VoiceControlDispatch` về fail-open (`speedKmh > 0` only) ⇒ `VoiceActGateReadbackTest.khong doc duoc toc do thi chi so P
  moi cho mo` + `KeyCtlSafetyTest.mo cop bang phim khi khong doc duoc toc do thi chi P moi mo` ĐỎ (2/30); khôi phục ⇒ xanh.
- Lượt app đầu có 1 đỏ không thuộc phần này: `LauncherI18nContractTest` bắt chuỗi Việt cứng ở `Car3dTaps.kt` (tệp agent 3D) —
  lượt sau đã xanh (agent kia sửa song song) [ĐO].
- Call site §8: `CtlSafetyPolicy.blocked(` 3 (gated · VoiceControlDispatch.run · KeyCtlPlan) · `readMotion(` 2 · `controls::motion` 1.

## Còn mở
- 🚗 [CHƯA ĐO] trên xe: số `gear` qua `readFresh("gear")` khi `speed` hụt (tốc độ PROVEN nên ca này hiếm). Giá fail-safe: xe đỗ mà
  mất CẢ tốc độ lẫn số ⇒ "Dừng xe để mở cốp" dù đang đỗ — owner đã chọn.
- Không bump version (orchestrator). Spec §9 đã thêm một dòng; backlog chưa ghi (một chủ spec — orchestrator).
