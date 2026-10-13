# kachi-301 — Execution Prompt
> Plan: docs/specs/kachi-301-cast-steps-dialog-ia.html (duyệt 2026-10-13, Pass 1) · Stages: 4 · Deliverables: R1–R18

## TASK
3.1 (205): sửa −/+ khung chiếu cụm (R1–R4, R7) · số âm qua overscan nếu máy ảo A10 chứng minh (R5–R6) · IA hộp thoại `KachiDialog` (R8–R13) · sửa hồi quy hạ nhạc khi nghe của 3.0 (R14–R18). TOÀN BỘ off-car (owner 13/10: không lên xe nữa).

## WORKING DIR
Repo byd-launcher, branch `feat/key-source-split` (HEAD e5f0c122). Không commit trong stage.

## CONTEXT
- Gradle: CHỈ qua `<scratchpad>/gradle-locked.sh` (khoá chung), agent chỉ chạy test focused `--tests`; full suite do điều phối.
- Máy ảo: một luồng một máy ảo. Stage 1: chỉ agent E dùng `clusternav10`.
- Trần 500 dòng/tệp. Chuỗi mới: mỗi agent tệp tài nguyên RIÊNG (`strings_kachi_<agent>301.xml`) ở values + values-en (+ zh-rCN/th/ms theo khuôn dự án).

## OWNER DECISIONS
OQ1 làm R6 nếu máy ảo chứng minh · OQ2 bước 20 + chip 5/20/50 · OQ3 không gỡ hộp chết · OQ4 3.1 (205).

## STAGES (DAG)
### Stage 1 (song song)
| Agent | T | FILES TO WRITE |
|---|---|---|
| V | T9 R14–R18 (mã + test đơn vị) | voice/VoiceMusicDuck.kt · voice/VoiceCapture.kt · core voice/VoiceMusicDuckPolicy.kt + test · VoiceListenWiringContractTest.kt · (mới) voice/VoiceMusicDuck*.kt |
| G | T1 R1–R4 + R7 | SettingsSectionsCastGeometry.kt · ClusterNavBridgeGeometry.kt · SettingsSectionsCast.kt · core CastSessionPin.kt · CastGeometryGuard.kt · SimpleCastCoordinator.kt (498 dòng — chỉ đổi tối thiểu) · (mới) SettingsRepeatTouch.kt · strings_kachi_geom301.xml · test |
| D1 | T2 R8 | (mới) KachiDialog.kt · SettingsDialogs.kt · test hợp đồng mới · strings_kachi_dialog301.xml |
| E | T6 R5 đo máy ảo | docs/diagnostics/emu-overscan-neg-2026-10-13.md (chỉ đo, không mã) |
- Exit gate: test focused xanh mỗi agent + `wc -l` ≤ 500 + grep call site hàm mới.
- Handoff: docs/_handoff/kachi-301-stage-1-done.md

### Stage 2 (song song, đọc stage-1)
| D2 T3 R10 (ProfileChip · SettingsSectionsProfiles) | D3 T4 R11–R12 (askPlace → SettingsPlaceDialog.kt · SettingsNavAutomationDialog.kt · TimeWheel.kt) | D4 T5 R13 (SettingsSectionsKeys · SettingsVoiceNames* · KachiHomeDisclaimer · UpdateFlow · SettingsSectionsCast chỉ chỗ gọi hộp thoại — SAU G) | G2 R6 nếu E xanh |

### Stage 3: full suite 5 module (điều phối) · QA máy ảo (một agent): voice đo trước/sau + E2E, ảnh Sáng/Tối mọi hộp, −/+ ×5 nhanh.
### Final: review Fable (claude-fable-5-1) → vá → quét bảo mật → bump 3.1 (205) → doc/INDEX/BACKLOG/project-context → commit.

## ORCHESTRATOR
Đọc lại spec mỗi stage · kiểm gate bằng lệnh · FAIL ⇒ G4.3 · ghi handoff · chạy stage kế.

## FINAL EXIT
R1–R18 ✅ (R6 có thể "không làm — máy ảo bác", có bằng chứng) · full suite 0 fail mới · review 0 P0–P1 · scan CLEAN · commit.
