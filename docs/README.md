# ClusterNav 2.0 — Docs Index (INDEX canonical)

> **Trạng thái**: Current · **Cập nhật**: 2026-08-24 · **Mục đích**: Bản đồ MỌI tài liệu hiện hành theo 9-loại taxonomy (R4). Không có trong index = archive/stale, KHÔNG authoritative (R0).

**(VI)** Đây là **nguồn map tài liệu duy nhất** của repo. Đọc file này trước → rồi mở doc cụ thể. Task = `PROJECT-BACKLOG.md`. Luật bền = `../.kiro/steering/`.
**(EN)** This is the repo's **single documentation map**. Read this first → then open the specific doc. Tasks live in `PROJECT-BACKLOG.md`; durable rules in `../.kiro/steering/`.

**Legend — trạng thái:** `Current` = hiện hành/authoritative · `Session` = handoff phiên (tạm) · `Historical` = lineage/context, giữ tại chỗ, KHÔNG authoritative · `Pending` = sẽ tạo (stage khác).

**9-loại taxonomy (R4):** 1) Index · 2) Backlog · 3) Rules/Steering · 4) Overview · 5) Spec · 6) Diagnostics · 7) Guide · 8) ADR · 9) Handoff · (+ `archive/` = trạng thái doc bị thay thế).

## 🔒 Doc niêm phong (byte-sealed) — ĐỌC TRƯỚC MỌI ĐỢT DỌN DOC

Một số doc là **bằng chứng đã niêm phong**, không phải văn bản sống. Hash SHA-256 từng byte của chúng bị chốt cứng trong test (`offcar-planner .../LegacyBaselineIdentityTest.kt` + `ExpansionTransportFenceTest.kt`) và trong `docs/diagnostics/hud-sign-re/offcar-boundary-revisions.json`. **Thêm dù chỉ một dòng** → digest lệch → `:offcar-planner:test` đỏ, kéo theo cả `:vehicle-contracts`/`:core` ở lượt chạy sau.

**Phạm vi — mức niêm phong khác nhau, [ĐO] từng cái:**
- **Chốt hash từng byte** (sửa 1 ký tự = đỏ ngay): 12 file `docs/diagnostics/hud-sign-re/*` + `native/libbydcluster-diff.json` + `docs/specs/seal-nav-hud-speed-sign-offcar.html` = **13 file cha**, hash liệt kê trong `LegacyBaselineIdentityTest.PARENT_ARTIFACT_SHA256` và `ExpansionTransportFenceTest.PARENT_ARTIFACT_HASHES`; digest gộp nằm cả trong `ExpansionRegistry.LegacyBaselineIdentity.PARENT_BASELINE_SHA256` lẫn `offcar-boundary-revisions.json`.
- **Chốt bằng tái sinh** (phải trùng đúng bytes mà generator tạo ra): 12 file `docs/diagnostics/hud-sign-re/expansion/*` — sửa tay = đỏ; `offcar-boundary-revisions.json` tự kiểm `selfSha256`.
- **Chốt một phần**: `docs/specs/seal-hud-sign-candidate-expansion.html` — khối `CURRENT X0–X5` + bảng truy vết bị test đọc (`ExpansionTransportFenceTest`, `ExpansionTraceabilityTest`); phần văn xuôi không bị chốt.
- **Chỉ chốt TÊN trong bản kê, KHÔNG chốt file**: `docs/specs/seal-hud-sign-vehicle-test-t10.html` — chuỗi đường dẫn nằm trong `SOURCE_SEAL_INPUT` của `offcar-boundary-revisions.json`, mà JSON đó bị chốt hash (`revisionSha256` + `selfSha256`, `ExpansionTransportFenceTest.kt:97` chốt `assertEquals(155, source.size)`) ⇒ **sửa bản kê = đỏ**. Nhưng [ĐO 2026-08-23] **xoá hẳn file khỏi đĩa ⇒ `:offcar-planner:test` vẫn 99/1, KHÔNG test nào đỏ** — không có assert tồn-tại nào cho đường dẫn này (khác `CURRENT_PATHS`, vốn có `Files.isRegularFile`). ⇒ Cứ coi như đọc-only cho an toàn, nhưng **đừng trông cậy test bắt được**.

**Miễn trừ có chủ ý** (KHÔNG phải nợ doc): các file này **không nhận** header R2.2 (`> **Trạng thái**: …`) và **không nhận** §Nhật ký R2.6. Metadata đó sống ở **dòng index trong file này** và ở `PROJECT-BACKLOG.md`.

**Nếu test báo "SEALED PARENT FILE(S) DRIFTED"**: khôi phục bytes (`git checkout <commit-trước-khi-sửa> -- <path>`). **CẤM** đi hướng ngược lại (chỉnh hằng số hash cho khớp bytes hiện tại) — đó mới là phá niêm phong. Lịch sử: 2 file bị doc-refactor E0–E4 sửa (commit `c7409b8`, `69fa2fe`) → đẻ ra 5 fail bị 3 agent gọi nhầm là "nợ có sẵn"; đã khôi phục ở **E6a** (2026-08-23).

Thông điệp lỗi in **đích danh đường dẫn + hash sealed vs on-disk**, và [ĐO 2026-08-23] phủ **đủ 5/5** test nhạy niêm phong
(`LegacyBaselineIdentityTest` ×2, `ExpansionTransportFenceTest` ×2, `ExpansionTraceabilityTest` ×1) — trước đó 3 trong 5 chỉ
in digest vô danh (`expected: <5b49a5ea…> but was: <2329ccc6…>`, `array contents differ at index [29]`) và chính sự vô danh đó
đã đốt 3 lượt điều tra.

**⚠ BYTES ĐÚNG HIỆN CHỈ NẰM Ở INDEX + CÂY LÀM VIỆC, CHƯA VÀO `HEAD`** (2026-08-23 — E6a chưa được commit).
[ĐO] `git show HEAD:docs/diagnostics/hud-sign-re/README.md | shasum -a 256` = `61a7ba11…` (lệch), worktree = `f854683a…` (đúng).
⇒ mọi `git checkout` từ HEAD, `git stash`, clone mới hay CI đều lấy lại bytes lệch và cho **99 test / 5 fail**.
Lệnh khôi phục chuẩn, chạy được ở **mọi** cây kể cả cây sạch:

```bash
git checkout cc5ae64 -- docs/diagnostics/hud-sign-re/README.md docs/specs/seal-nav-hud-speed-sign-offcar.html
```

`cc5ae64` là commit cuối còn giữ đúng bytes niêm phong của **cả hai** file. Chỉ dùng `git checkout -- <path>` (không nêu commit)
khi index đang giữ bytes đúng — sau một `git reset` thì index == HEAD và lệnh đó sẽ **kéo bytes lệch trở lại**.

---

## 1) Index

| Doc | Mục đích | Trạng thái | Cập nhật |
|-----|----------|-----------|----------|
| [`README.md`](README.md) (file này) | Map canonical mọi doc hiện hành theo 9-loại + §🔒 doc niêm phong (byte-sealed) | Current | 2026-08-23 |

## 2) Backlog

| Doc | Mục đích | Trạng thái | Cập nhật |
|-----|----------|-----------|----------|
| [`PROJECT-BACKLOG.md`](PROJECT-BACKLOG.md) | Nguồn DUY NHẤT cho task (ID · việc · trạng thái · ngày) | Current | 2026-08-19 |

## 3) Rules / Steering

| Doc | Mục đích | Trạng thái | Cập nhật |
|-----|----------|-----------|----------|
| [`../.kiro/steering/documentation-and-backlog.md`](../.kiro/steering/documentation-and-backlog.md) | Kỷ luật doc + backlog + 9-loại taxonomy (bắt buộc mọi phiên) | Current | 2026-08-19 |
| [`../.kiro/steering/product-team-workflow.md`](../.kiro/steering/product-team-workflow.md) | Quy trình làm việc như một team (PO→UX→Dev→QA→Review) | Current | ~ |
| [`../.kiro/steering/project-context.md`](../.kiro/steering/project-context.md) | Tóm tắt luôn-bật (kiến trúc + nguồn nav + HAL/CAN + map file + trạng thái) | Current | 2026-08-23 |
| [`../.kiro/steering/trace-den-tan-cung.md`](../.kiro/steering/trace-den-tan-cung.md) | Cấm bỏ cuộc sớm — trace tới root cause; bỏ tính năng là quyết định của OWNER | Current | 2026-08-20 |
| [`../.kiro/steering/conversation-protocol.md`](../.kiro/steering/conversation-protocol.md) | Cách báo cáo: mức bằng chứng [ĐO]/[SUY]/[ĐOÁN] · scope · progress · status · quality · cấm làm văn | Current | 2026-08-23 |

> Steering toàn cục (không nằm trong repo): `~/.kiro/steering/` (workflow, pre-commit-security, PROMPT_TEMPLATE, image-reading-subagent). Chỉ tham chiếu, không sửa ở đây.

## 4) Overview

| Doc | Mục đích | Trạng thái | Cập nhật |
|-----|----------|-----------|----------|
| [`../README.md`](../README.md) | Landing dự án — 2 nhánh, trạng thái 1.0/1.30, cài đặt/OTA (VI+EN) | Current | 2026-08-17 |
| [`CLOSEOUT-2026-08-16.md`](CLOSEOUT-2026-08-16.md) | Đánh giá đóng dự án 1.30 — 6 bản sửa cuối + giới hạn đã biết | Current | 2026-08-16 |
| [`HISTORICAL-ARTIFACTS.md`](HISTORICAL-ARTIFACTS.md) | Hồ sơ cách ly artifact lịch sử (APK/ảnh cũ) + cổng release | Current | 2026-08-16 |

## 5) Spec — `specs/*.html` (duyệt TRƯỚC khi code)

### Current — authoritative

| Doc | Mục đích | Trạng thái | Cập nhật |
|-----|----------|-----------|----------|
| [`specs/ui-design-system.html`](specs/ui-design-system.html) | **Cockpit UI Design System** — từ điển component dùng-lại `@style/Cockpit.*` (Card/Text/Button/IconTile/Switch/Spinner/SeekBar/StatusPill/SegmentButton/ListRow) + token (colors/dimens) + reskin dropdown/Spinner/SeekBar qua theme+adapter+drawable mới (KHÔNG sửa layout byte-seal). Reuse rules: UI mới phải lắp từ Cockpit.* | Current | 2026-09-05 |
| [`specs/seat-comfort-auto.html`](specs/seat-comfort-auto.html) | **Ghế: làm mát / sưởi tự động** — chế độ toàn cục (làm mát ↔ sưởi) + mức từng ghế (Tắt/Mức 1/Mức 2), hiện 2 ghế (Seal) / 4 ghế (Han), tự áp ~5s sau start qua HAL device 1023. Off-car xanh; 2 ẩn số on-car (giá trị 2/3 vs 1/2; feature-id ghế sau Han SUY) | Current | 2026-09-03 |
| [`specs/pm25-auto-filter.html`](specs/pm25-auto-filter.html) | **Tự lọc bụi mịn (PM2.5)** — một công tắc (mặc định TẮT) ngay dưới card ghế; BẬT → xe tự lọc LIÊN TỤC không popup qua `BYDAutoAcDevice` (reflection: enablePurificationFunctionPrompt(0)+setAutoCleanAirState(1)), áp ~5s sau start/boot; hiện mức bụi hiện tại (device 1008 `getPM2p5Level`, off-car "—"). Off-car xanh; 4 ẩn số on-car | Current | 2026-09-04 |
| [`specs/vietmap-overlay-position-ui.html`](specs/vietmap-overlay-position-ui.html) | **Item 4 (CLOSING 08-28)**: UI chỉnh VỊ TRÍ bong bóng VietMap trên cụm qua broadcast → mod receiver `posrx` (fix mapping 1-1). Kéo-thả + preset + persistence. Xem runbook `runbook-mod-vietmap-cluster.md` §10 | Current | 2026-08-28 |
| [`specs/upcoming-speed-limit-badge.html`](specs/upcoming-speed-limit-badge.html) | Vẽ "giới hạn sắp tới + cự ly" (VietMap) lên cụm (active — A9/B2) | Current | 2026-08-18 |
| [`specs/waze-vietmap-screen-capture.html`](specs/waze-vietmap-screen-capture.html) | Screen-capture nav (Waze arrow + VietMap camera) học OpenBYD — B3, **Chờ duyệt**, 4 case | ⚠ Lịch sử (CLOSING 08-28 gỡ screen-capture nav) | 2026-08-19 |
| [`specs/b3-full-nav-capture.html`](specs/b3-full-nav-capture.html) | **B3 FULL**: đọc mũi tên+làn+camera+text (Waze/VietMap/GMaps) + menu chọn nguồn + bắn cụm/HUD + overlay cụm (như speed badge). **Draft/chờ duyệt** | Current | 2026-08-23 |
| [`specs/b3-data-flow.html`](specs/b3-data-flow.html) | **B3 DATA FLOW**: input→process→output (đường nào, data nào, vẽ gì/thế nào) — 2 sơ đồ SVG. Reference | Current | 2026-08-20 |
| [`specs/b3-glyph-locator-cast-invariant.html`](specs/b3-glyph-locator-cast-invariant.html) | **B3.27**: dò glyph BẤT BIẾN với cấu hình cast (user chỉnh dpi/size/vị-trí, cast 1 hoặc 2 app) — thay mọi rect crop cố định. §4.2 + Reviewer Log Pass 2/3 = bộ cổng hiện hành (B3.47 vòng 3 + 3b) | Current | 2026-08-23 |
| [`specs/nav-input-output-architecture.html`](specs/nav-input-output-architecture.html) | **Kiến trúc luồng nav — owner DUYỆT 08-24**: nhiều cách nhận tín hiệu → MỘT cửa vào → các đầu ra. Vẽ cả chỗ code đã trôi + 3 bước quay về | Current | 2026-08-24 |
| [`specs/b3-53-fixed-rect-ncc.html`](specs/b3-53-fixed-rect-ncc.html) | **B3.53**: nhánh NCC MỀM chấm crop lấy bằng rect KHÔNG-phải-mũi-tên ra SAI HƯỚNG — bảng số 2 ứng viên + vá `classifyStrict` (tier rect cố định) + vá `CaptureBounds.target` (tier a11y, Pass 2) + bảng giá phủ sóng (off-car xanh, chưa on-car) | Current | 2026-08-23 |
| [`specs/b-multiapp-nav-safety.html`](specs/b-multiapp-nav-safety.html) | **Scope B**: an toàn khi >1 app dẫn — một-package-một-khung · dwell chống nhảy nguồn · guard hợp lý hoá cự ly · cổng kênh DATA/IMAGE | Current | 2026-08-22 |
| [`specs/speed-limit-cluster-hud-oncar-ready.html`](specs/speed-limit-cluster-hud-oncar-ready.html) | Speed-limit cluster badge + HAL port + HUD probe (on-car ready) | Current | 2026-08-17 |
| [`specs/speed-badge-placement-vietmap-logging.html`](specs/speed-badge-placement-vietmap-logging.html) | UI đặt vị trí badge + dời nguồn VietMap + log toàn tín hiệu | Current | 2026-08-17 |
| [`specs/v2-accessibility-navsource-handoff.html`](specs/v2-accessibility-navsource-handoff.html) | a11y NavScreenSource đa app (GMaps/Waze/VietMap) | Current | 2026-08-17 |
| [`specs/waze-vietmap-signal-revival.html`](specs/waze-vietmap-signal-revival.html) | Revival Waze-Mod + VietMap widget + speed-limit signal (lên 1.30) | Current | 2026-08-17 |
| [`specs/clusternav-two-track-final-plan.html`](specs/clusternav-two-track-final-plan.html) | Kế hoạch 2 nhánh chốt + evidence gates | Current | 2026-07-26 |
| [`specs/cluster-cast-rebaseline.html`](specs/cluster-cast-rebaseline.html) | Hợp đồng Cast canonical (re-baseline) | Current | 2026-07-26 |
| [`specs/clusternav-uxui-rebaseline.html`](specs/clusternav-uxui-rebaseline.html) | UX 2-card + hợp đồng Navigation (re-baseline) | Current | 2026-07-30 |
| [`specs/dead-reckon-revalidation.html`](specs/dead-reckon-revalidation.html) | Quyết định REMOVE Dead-Reckon + review debt | Current | 2026-07-27 |
| [`specs/notif-grant-docs-voicekey-1.13.html`](specs/notif-grant-docs-voicekey-1.13.html) | Spec 1.13: notification-grant · docs · voice-key | Current | 2026-08-13 |
| [`specs/clusternav-closeout-1.28.html`](specs/clusternav-closeout-1.28.html) | Spec đóng dự án 1.30 (nền của CLOSEOUT) | Current | 2026-08-16 |
| [`specs/_template.html`](specs/_template.html) | Template spec Kiro-style (10 section chuẩn + §9 Nhật ký triển khai) — starting point cho spec mới (workflow.md §3) | Current | 2026-08-19 |

### Historical / lineage — giữ tại chỗ, context-only (R0: không authoritative)

> Các spec dưới đây mô tả build/điều tra lịch sử. Giữ nguyên trong `specs/` làm lineage; **không** phải baseline hiện hành trừ khi một spec Current ở trên promote lại.

- [`specs/cluster-cast-simplified.html`](specs/cluster-cast-simplified.html) — Cast simplified architecture (tiền thân của rebaseline)
- [`specs/cast-architecture-cleanup.html`](specs/cast-architecture-cleanup.html) — gỡ v2 stack, gộp 1 path simplified
- [`specs/cast-simplified-active-app-toggle.html`](specs/cast-simplified-active-app-toggle.html) — nút nổi = chiếu app đang mở
- [`specs/cast-boot-recovery-and-app-manager-entrypoint.html`](specs/cast-boot-recovery-and-app-manager-entrypoint.html) — boot recovery + App Manager
- [`specs/cast-enable-toggle.html`](specs/cast-enable-toggle.html) — master enable/disable Cast
- [`specs/cast-freeform-resize-split.html`](specs/cast-freeform-resize-split.html) — freeform resize/split/per-app
- [`specs/cast-one-mode-and-three-zone-bubble.html`](specs/cast-one-mode-and-three-zone-bubble.html) — một chế độ + nút nổi 3 ô
- [`specs/cast-recovery-honesty-and-multi-occupant.html`](specs/cast-recovery-honesty-and-multi-occupant.html) — trung thực khi kẹt + đa-chủ
- [`specs/cast-resize-dpi-bubble-fixes.html`](specs/cast-resize-dpi-bubble-fixes.html) — resize persistence · DPI · bubble toggle
- [`specs/cast-secondary-app-corner-overlay.html`](specs/cast-secondary-app-corner-overlay.html) — overlay góc cụm cho app phụ
- [`specs/cast-nav-ux-release-v104.html`](specs/cast-nav-ux-release-v104.html) — Cast+Nav UX polish v1.04
- [`specs/cluster-cast-v036.html`](specs/cluster-cast-v036.html) — v0.36 ladder chiếu, chặn PIP
- [`specs/cluster-cast-v070-manual-cold-intent.html`](specs/cluster-cast-v070-manual-cold-intent.html) — v0.70 manual cold intent
- [`specs/cluster-cast-v071-product-completion.html`](specs/cluster-cast-v071-product-completion.html) — v0.71 product completion
- [`specs/cluster-nav-4mode-restore.html`](specs/cluster-nav-4mode-restore.html) — khôi phục AMAP nav-trên-cụm 4 mode
- [`specs/clusternav-v102-review-remediation.html`](specs/clusternav-v102-review-remediation.html) — v1.03 review remediation
- [`specs/clusternav-v103-remediation.html`](specs/clusternav-v103-remediation.html) — v1.03 remediation + letterbox
- [`specs/dual-track-2026-07-23.html`](specs/dual-track-2026-07-23.html) — kế hoạch 2 nhánh v0.60 (07-23)
- [`specs/freeze-proof-cluster-switch.html`](specs/freeze-proof-cluster-switch.html) — đổi app cụm chống freeze v0.66
- [`specs/hud-keepalive-interp-log-1.15.html`](specs/hud-keepalive-interp-log-1.15.html) — 1.15 HUD keep-alive + interp log
- [`specs/nav-cluster-op39-selfdiagnose.html`](specs/nav-cluster-op39-selfdiagnose.html) — op39 "Giữa + ETA" self-diagnose
- [`specs/nav-oncar-fixes-1.14.html`](specs/nav-oncar-fixes-1.14.html) — 1.14 on-car fixes
- [`specs/seal-hud-sign-candidate-expansion.html`](specs/seal-hud-sign-candidate-expansion.html) — HUD/sign candidate expansion
- [`specs/seal-hud-sign-vehicle-test-t10.html`](specs/seal-hud-sign-vehicle-test-t10.html) — kế hoạch T10 HUD + biển tốc độ
- [`specs/seal-nav-hud-speed-sign-offcar.html`](specs/seal-nav-hud-speed-sign-offcar.html) — 🔒 **BYTE-SEALED** — Seal nav HUD + speed sign off-car. Không có §Nhật ký trong file (R2.6 miễn trừ — xem §Doc niêm phong bên dưới); nhật ký của spec này ghi ở `PROJECT-BACKLOG.md`
- [`specs/vietmap-widget-bridge.html`](specs/vietmap-widget-bridge.html) — VietMap widget bridge POC
- [`specs/voicekey-rework-1.19.html`](specs/voicekey-rework-1.19.html) — 1.19 voice-key UX rework
- [`specs/windshield-hud-enable.html`](specs/windshield-hud-enable.html) — bật dẫn đường trên HUD kính
- [`specs/cast-ui-state-v2.schema.json`](specs/cast-ui-state-v2.schema.json) — schema JSON hỗ trợ (artifact)

## 6) Diagnostics — `diagnostics/*` (finding có BẰNG CHỨNG + ngày)
### Current

| Doc | Mục đích | Trạng thái | Cập nhật |
|-----|----------|-----------|----------|
| [`diagnostics/byd-pm25-airclean-RE-2026-09-04.md`](diagnostics/byd-pm25-airclean-RE-2026-09-04.md) | **RE PM2.5 + lọc không khí AC** — device 1008 `getPM2p5Level()[0]` (INVALID=0..SERIOUS=6) + method GHI `BYDAutoAcDevice` (enablePurificationFunctionPrompt/setAutoCleanAirState/setQuickCleanAirState) CHỈ trên ROM ⇒ reflection + lý do; 4 ẩn số on-car | Current | 2026-09-04 |
| [`diagnostics/vietmap-cluster-surfacecontrol-mirror-2026-08-27.md`](diagnostics/vietmap-cluster-surfacecontrol-mirror-2026-08-27.md) | **Mirror bong bóng VietMap → cụm qua SurfaceControl daemon (no-mod)** — [ĐO] present pixel-thật (diff 0.0) + bám vị trí động trên Android 10 (API29) uid shell; phát hiện BLAST(API34) vs BufferQueue(API29); ẩn số = cụm xe `xdja` có nhận layer không. Code+jar+script: `tools/vietmap-cluster-mirror/` | Current | 2026-08-27 |
| [`diagnostics/nav-io-asis-2026-08-24.html`](diagnostics/nav-io-asis-2026-08-24.html) | **Bản đồ nav I/O — Phần A HIỆN TRẠNG (as-is) + Phần B ĐỀ XUẤT to-be** (HTML trực quan). A: 6 nguồn đầu vào + 4 luồng + 7 bề mặt đầu ra + §8 chỗ "lộn xộn" (HUD kính đang trói chung `writeNavFrame`+gate `navOnlyMode` với cụm-centre; `emitHud` stub; VietMap/Waze phụ thuộc screen-capture). B (chờ owner review): nguyên tắc độc lập/dùng chung + **ma trận 3 nhóm theo Cast** (BẤT KỂ / CHỈ-không-cast / CHỈ-cast) + tách content-write (HUD, vô điều kiện) khỏi surface-write (cụm, gate) + OQ1–4. file:line | Current | 2026-08-24 |
| [`diagnostics/b3-emulator-e2e-2026-08-20.md`](diagnostics/b3-emulator-e2e-2026-08-20.md) | **Test B3 end-to-end trên emulator (Waze/VietMap/GMaps dẫn thật)**: Waze rẽ-TRÁI classify ĐÚNG `amap=2` end-to-end; môi trường lặp lại (adb root/960×720/verbose); bug B3.10/B3.11/B3.12 + RESUME POINT | Current | 2026-08-20 |
| [`diagnostics/vietmap-glyph-gate-measurement-2026-08-23.md`](diagnostics/vietmap-glyph-gate-measurement-2026-08-23.md) | **Cổng dò glyph VietMap — chẩn đoán + bản vá đã đo lại** (§7 vòng 3, §7.7 vòng 3b): cổng cũ trả **icon POI bản đồ ở 60/87 khung** thay vì im lặng ⇒ thay trần dp bằng tỉ số + **neo trái** (dò đúng 27→86, đảo mồi 60→0); vòng 3b đóng thêm 3 đường false-positive — `windowRect=null` ⇒ mũi tên app KHÁC (Hamming 0) · dpi khai ≤124 ⇒ icon status bar ở 87/87 · rơi về rect Waze cố định ⇒ 2 ca SAI HƯỚNG | Current | 2026-08-23 |
| [`diagnostics/vietmap-dark-keepalive-a11y-2026-08-24.md`](diagnostics/vietmap-dark-keepalive-a11y-2026-08-24.md) | **VietMap "tắt đen nhiều đoạn" — chẩn gốc emulator + fix keep-alive a11y (F4b)**: `NavOutputOwner` chỉ ra khung khi glyph mũi tên tươi; glyph hết tươi ⇒ nhả phiên (tắt đen) dù a11y cự-ly/đường còn tươi. Fix: giữ khung bằng HƯỚNG-LẦN-CUỐI + a11y tươi, chặn cự-ly-giảm-đơn-điệu + baseline (im lặng>sai hướng). 2154 test/0 fail; APK v1.17 | Current | 2026-08-24 |
| [`diagnostics/b3-cluster-arrow-e2e-emulator-1920x720-2026-08-21.md`](diagnostics/b3-cluster-arrow-e2e-emulator-1920x720-2026-08-21.md) | Mốc B3.e2e — chuỗi "đọc cụm → đẩy HUD" thông off-car ở đúng 1920×720 (log thật) | Current | 2026-08-21 |
| [`diagnostics/hal-register-latch-on-identity-switch-2026-08-22.md`](diagnostics/hal-register-latch-on-identity-switch-2026-08-22.md) | HAL latch khi đổi danh tính nguồn — nền cho bất biến MỘT-PACKAGE-MỘT-KHUNG (SB.1) | Current | 2026-08-22 |
| [`diagnostics/voicekey-adb-approval-first-open-2026-08-24.md`](diagnostics/voicekey-adb-approval-first-open-2026-08-24.md) | **F2 — phím thoại câm ở lần mở app đầu**: [ĐO] bytecode dadb 2.0.0 (adbd im lặng chờ bấm "Cho phép gỡ lỗi USB" + `socketTimeout=0` ⇒ treo vĩnh viễn; nối LƯỜI ⇒ thử lại sẽ phát lại lệnh) · vá = phân loại lý do + chờ có giãn cách (**4 lần × 1/2/4 s, ~31 s**) CHỈ cho 2 đường owner-chủ-động · **§4.3 = vòng phản biện: 6 điểm phải sửa, đứng đầu là "cấm phát lại lệnh đã gửi"** · quy kết còn ở mức [SUY], §5 nêu đúng 3 lệnh để chốt trên xe | Current | 2026-08-24 |
| [`diagnostics/8hare-gemini-voicekey-vietmap-autostart-2026-08-21.md`](diagnostics/8hare-gemini-voicekey-vietmap-autostart-2026-08-21.md) | Voice-key → Gemini (keyevent 231, recipe 8hare) + VietMap autostart headless-friendly | Current | 2026-08-21 |
| [`diagnostics/multi-app-nav-source-channels-2026-08-20.md`](diagnostics/multi-app-nav-source-channels-2026-08-20.md) | Ma trận kênh nguồn (GMaps/VietMap/Waze nền vs visible) + khung two-track (data không phải overlay) + bác lừa/clone overlay | Current | 2026-08-20 |
| [`diagnostics/VEHICLE-TEST-V2.md`](diagnostics/VEHICLE-TEST-V2.md) | Checklist thử trên xe + ma trận Stage 11 (execution NOT STARTED) | Current | 2026-07-26 |
| [`diagnostics/factory-hud-nav-RE-avenues-2026-08-19.md`](diagnostics/factory-hud-nav-RE-avenues-2026-08-19.md) | Tổng hợp MỌI đường RE để HUD **zin** hiện nav + xếp hạng khả thi×chi phí (nghi phạm `0x38B00030` chưa bác; HUD Taobao ≠ zin) | Current | 2026-08-19 |
| [`diagnostics/hud-coding-recipe-2026-08-19.md`](diagnostics/hud-coding-recipe-2026-08-19.md) | Recipe coding HUD-nav + kết luận cuối: transport CAN-inject no-root ĐÃ mở; 3 bí mật (diag-ID/security-key/DID cụm) ở MCU firmware/ODX dealer, cần nguồn ngoài | Current | 2026-08-19 |
| [`diagnostics/oncar-plan-2026-08-19.md`](diagnostics/oncar-plan-2026-08-19.md) | **Plan lên xe 2026-08-19**: C1 cài APK B-batch + C6 probe HUD-nav + test B-batch/C2; sẵn-sàng đã verify + cây quyết định | Current | 2026-08-19 |
| [`diagnostics/hud-provisioning-compare-2026-08-19.md`](diagnostics/hud-provisioning-compare-2026-08-19.md) | HUD-compare owner vs anh em: app đẩy nav lên bus đúng (HUD **Taobao** anh em hiện nav). ⚠ kết luận "`0x38B00030` bị bác" **đã đính chính** — Taobao ≠ zin, cờ chưa bác (xem `factory-hud-nav-RE-avenues`) | Current | 2026-08-19 |
| [`diagnostics/arrow-validation-teammate-2026-08-18.md`](diagnostics/arrow-validation-teammate-2026-08-18.md) | Xác thực 18/18 mũi tên (blind bitmap vs answer-key) | Current | 2026-08-18 |
| [`diagnostics/distance-interpolation-validation-2026-08-18.md`](diagnostics/distance-interpolation-validation-2026-08-18.md) | Kiểm cự ly 2 bên + nội suy km→turn (a11y screenRead) | Current | 2026-08-18 |
| [`diagnostics/oncar-session-2026-08-16.md`](diagnostics/oncar-session-2026-08-16.md) | Tổng hợp + TODO phiên on-car 2026-08-16 | Current | 2026-08-16 |
| [`diagnostics/app-code-updates-2026-08-16.md`](diagnostics/app-code-updates-2026-08-16.md) | Sửa CODE rút từ phiên on-car 2026-08-16 (file:line + acceptance) | Current | 2026-08-16 |
| [`diagnostics/nav-icon-mapping-2026-08-16.html`](diagnostics/nav-icon-mapping-2026-08-16.html) | Mapping icon dẫn đường (GMaps → cụm AMAP / HUD CAN) | Current | 2026-08-16 |
| [`diagnostics/nav-output-architecture-2026-08-16.html`](diagnostics/nav-output-architecture-2026-08-16.html) | Kiến trúc 2 đường ra Navigation + bảng field | Current | 2026-08-16 |
| [`diagnostics/re-maneuver-icon-tables-2026-08-14.md`](diagnostics/re-maneuver-icon-tables-2026-08-14.md) | Bảng RE icon AMAP/HUD CAN + enrich Maneuver | Current | 2026-08-14 |
| [`diagnostics/gemini-assistant-voicekey-oncar-2026-08-13.md`](diagnostics/gemini-assistant-voicekey-oncar-2026-08-13.md) | Thủ tục on-car Gemini trợ lý + nút mic → Gemini | Current | 2026-08-13 |
| [`diagnostics/hud-sign-re/README.md`](diagnostics/hud-sign-re/README.md) | 🔒 **BYTE-SEALED** — Entry-doc workspace RE HUD + speed-sign (T0–T9): corpus, evidence, expansion (đại diện cho cả thư mục `diagnostics/hud-sign-re/`, gồm các artifact niêm phong: [`candidate-report.html`](diagnostics/hud-sign-re/candidate-report.html) · [`hud-cluster-field-checklist.html`](diagnostics/hud-sign-re/hud-cluster-field-checklist.html) · [`expansion/candidate-expansion-report.html`](diagnostics/hud-sign-re/expansion/candidate-expansion-report.html) · [`expansion/vehicle-session-checklist.html`](diagnostics/hud-sign-re/expansion/vehicle-session-checklist.html) — **không** nhận header/index-row riêng vì thuộc bộ byte-sealed, xem §🔒). Header trạng thái/mục đích **ở dòng này**, KHÔNG được thêm vào trong file (xem §Doc niêm phong bên dưới) | Current | 2026-08-18 |

### Historical / context — giữ tại chỗ

- [`diagnostics/oncar-handoff-voicekey-2026-08-14.md`](diagnostics/oncar-handoff-voicekey-2026-08-14.md) — handoff on-car voice-key 1.19 (đã qua)
- [Cụm-centre render root cause (08-21)](diagnostics/cluster-centre-render-rootcause-2026-08-21.md) — vì sao HAL guide rc=0 mà centre trống; đường đúng = NaviInfo flatbuffer qua AutoContainer.sendInfo2(4) trên cụm fission. Current, 2026-08-21.
- [8hare RE → voice-key Gemini + VietMap autostart (08-21)](diagnostics/8hare-gemini-voicekey-vietmap-autostart-2026-08-21.md) — keyevent 231 + full assistant recipe; VietMap pidof-guard + headless-boot. Verified off-car (emulator). Current, 2026-08-21.
- `diagnostics/artifacts/` — evidence logcat/env cast 2026-07-30 (đọc-only); gồm [`artifacts/carexec-checklist.html`](diagnostics/artifacts/carexec-checklist.html) (checklist đo trên xe cho refactor car-execution — Historical)
- Scripts diagnostics (tooling, không phải doc): `diagnostics/nav-log.ps1`, `diagnostics/nav-debug.ps1`, `diagnostics/autotest.ps1`, `diagnostics/cluster-cast-test.ps1`

## 7) Guide — `HUONG-DAN-*` (user / anh em, song ngữ)

| Doc | Mục đích | Trạng thái | Cập nhật |
|-----|----------|-----------|----------|
| [`HUONG-DAN.md`](HUONG-DAN.md) | Hướng dẫn dùng ClusterNav (bật Nav+HUD, quyền, cluster mode, voice-key) | Current | 2026-08-16 |
| [`HUONG-DAN-LAY-LOG.md`](HUONG-DAN-LAY-LOG.md) | Lấy log + ảnh sau lái thử (Cách A không máy tính / B dùng máy tính) | Current | 2026-08-17 |
| [`HUONG-DAN-LAY-LOG-DIAG.md`](HUONG-DAN-LAY-LOG-DIAG.md) | **Lấy log bản DIAG v1.28** (cho anh em): log bật-sẵn + tự xuất ra `Download/ClusterNavLog`; thu content-desc VietMap/Waze + ảnh mũi tên để tìm thông tin hướng rẽ còn thiếu | Current | 2026-08-26 |
| [`HUONG-DAN-LAY-LOG-WINDOWS.html`](HUONG-DAN-LAY-LOG-WINDOWS.html) | Lấy log ClusterNav bằng máy Windows | Current | 2026-08-19 |
| [`HUONG-DAN-THU-DATA-HUD.html`](HUONG-DAN-THU-DATA-HUD.html) | Thu thập data HUD (so sánh provisioning xe anh em) | Current | 2026-08-18 |
| [`runbook-mod-vietmap-cluster.md`](runbook-mod-vietmap-cluster.md) | **RUNBOOK mod VietMap dời bong bóng ra cụm** (lặp mỗi bản mới): §3 TÌM target obfuscated + §4 smali redirect display + §10 **inject receiver chỉnh VỊ TRÍ + FIX MAPPING 1-1** (gravity TOP\|LEFT + NO_LIMITS, smali thật) + §5–7 build/ký/gộp/cài + pipeline THẬT (mod2.keystore→universal-mod3). Lên cụm XE khi Cluster Cast ON. | Current | 2026-08-28 |

## 8) ADR — `decisions/NNNN-*.md` (quyết định KIẾN TRÚC xuyên suốt)

| Doc | Mục đích | Trạng thái | Cập nhật |
|-----|----------|-----------|----------|
| [`decisions/README.md`](decisions/README.md) | Index ADR + hướng dẫn định dạng (Context·Decision·Consequences·Status·Date; khi nào mở ADR) | Current | 2026-08-19 |
| [`decisions/0001-nav-source-strategy.md`](decisions/0001-nav-source-strategy.md) | Chiến lược nguồn nav per-app (GMaps notif · VietMap widget+a11y · Waze screen-capture) + adapter trung lập | Current | 2026-08-19 |
| [`decisions/0002-hud-nav-coding-locked.md`](decisions/0002-hud-nav-coding-locked.md) | HUD kính ZIN nav = gate firmware (nghi phạm `0x38B00030` NOT provisioned, CHƯA bác), không phải bug app; xe anh em là HUD Taobao (đường độc lập) | Current | 2026-08-19 |
| [`decisions/0003-datacollection-logging-default-off.md`](decisions/0003-datacollection-logging-default-off.md) | Thu thập dữ liệu (log + ảnh) mặc định OFF + storage cap ~150 MB | Current | 2026-08-19 |

## 9) Handoff — `_handoff/*.md` (tóm tắt phiên, tạm)

> Handoff = ghi chép phiên (tạm/lịch sử theo R4). Đây là bộ handoff phiên hiện hành của nhánh `feat/speed-limit-badge-hal-hud`; bản cũ đã ở `archive/_handoff/`.

| Doc | Mục đích | Trạng thái | Cập nhật |
|-----|----------|-----------|----------|
| [`_handoff/stage-e1-done.md`](_handoff/stage-e1-done.md) | Handoff E1 — refactor docs 9-loại + tạo INDEX (file report này) | Session | 2026-08-19 |
| [`_handoff/stage-e2-done.md`](_handoff/stage-e2-done.md) | Handoff E2 — tạo `../.kiro/steering/project-context.md` (steering luôn-bật) | Session | 2026-08-19 |
| [`_handoff/stage-b1b2-done.md`](_handoff/stage-b1b2-done.md) | Handoff B1/B2 — autostart VietMap + design badge "giới hạn sắp tới" | Session | 2026-08-19 |
| [`_handoff/stage-b3spec-done.md`](_handoff/stage-b3spec-done.md) | Handoff B3 — spec screen-capture (4 case) trước khi code | Session | 2026-08-19 |
| [`_handoff/stage-b4-done.md`](_handoff/stage-b4-done.md) | Handoff B4 — diagnostics hygiene (screenRead INVALID khi stale) | Session | 2026-08-19 |
| [`_handoff/stage-b3-core-done.md`](_handoff/stage-b3-core-done.md) | Handoff B3 core — NavGlyphLocator + registry mực + roster NavApps | Session | 2026-08-22 |
| [`_handoff/session-2026-08-24-funnel-hud-voicekey.md`](_handoff/session-2026-08-24-funnel-hud-voicekey.md) | Handoff 08-24 — phễu nav bước 1/3 · HUD VietMap/Waze · badge tốc độ · phím thoại · 3 bài học + sự cố PII | Session | 2026-08-24 |
| [`_handoff/.b3-app-plan.md`](_handoff/.b3-app-plan.md) | Kế hoạch nhánh :app của B3 (nháp phiên) | Session | 2026-08-22 |
| [`_handoff/re-hud-track1-hudservice.md`](_handoff/re-hud-track1-hudservice.md) | RE HUD track 1 — HudService | Session | 2026-08-21 |
| [`_handoff/re-hud-track2-firmware-gate.md`](_handoff/re-hud-track2-firmware-gate.md) | RE HUD track 2 — cổng firmware | Session | 2026-08-21 |
| [`_handoff/re-hud-track3-coding-path.md`](_handoff/re-hud-track3-coding-path.md) | RE HUD track 3 — đường coding | Session | 2026-08-21 |
| [`_handoff/re-hud-track4-hal-can.md`](_handoff/re-hud-track4-hal-can.md) | RE HUD track 4 — HAL/CAN | Session | 2026-08-21 |
| [`_handoff/re-hud-track5-priorart.md`](_handoff/re-hud-track5-priorart.md) | RE HUD track 5 — prior art | Session | 2026-08-21 |
| [`_handoff/re-hud-coding-path.md`](_handoff/re-hud-coding-path.md) | RE HUD — tổng hợp đường coding | Session | 2026-08-21 |
| [`_handoff/re-roadname-gap.md`](_handoff/re-roadname-gap.md) | RE — khoảng trống tên đường trên HUD | Session | 2026-08-21 |
| [`_handoff/re-showroom-ref.md`](_handoff/re-showroom-ref.md) | RE — tham chiếu showroom | Session | 2026-08-21 |
| [`_handoff/stage-e4-done.md`](_handoff/stage-e4-done.md) | Handoff E4 — template §Nhật ký triển khai + `decisions/` ADR (README + 3 ADR) | Session | 2026-08-19 |
| [`_handoff/off-car-plan-2026-08-17.md`](_handoff/off-car-plan-2026-08-17.md) | Tổng hợp phiên on-car 08-17 + kế hoạch off-car | Session | 2026-08-17 |
| [`_handoff/progress-2026-08-18-findings-offcar-oncar.md`](_handoff/progress-2026-08-18-findings-offcar-oncar.md) | Findings đã implement off-car + kế hoạch verify on-car | Session | 2026-08-18 |
| [`_handoff/data-collection-drive-guide-2026-08-18.md`](_handoff/data-collection-drive-guide-2026-08-18.md) | Hướng dẫn chạy thu data (cho anh em) | Session | 2026-08-18 |
| [`_handoff/morning-handoff-2026-08-18.md`](_handoff/morning-handoff-2026-08-18.md) | Handoff sáng 08-18 (chạy autonomous đêm 08-17) | Session | 2026-08-18 |
| [`_handoff/stage-upcoming-badge-done.md`](_handoff/stage-upcoming-badge-done.md) | Stage: upcoming speed-limit + distance badge — DONE | Session | 2026-08-18 |
| [`_handoff/stage-logging-off-done.md`](_handoff/stage-logging-off-done.md) | Stage: data-collection logging OFF mặc định + storage cap — DONE | Session | 2026-08-18 |
| [`_handoff/stage-cdesc-done.md`](_handoff/stage-cdesc-done.md) | Stage: content-description fallback (VietMap/Waze) — DONE | Session | 2026-08-18 |
| [`_handoff/stage-rawnotif-done.md`](_handoff/stage-rawnotif-done.md) | Stage: raw-notif capture 5 gói nav — DONE | Session | 2026-08-18 |
| [`_handoff/stage-vmalert-done.md`](_handoff/stage-vmalert-done.md) | Stage: VMAlert capture (upLimit/upDist) — DONE | Session | 2026-08-18 |
| [`_handoff/stage-capture-done.md`](_handoff/stage-capture-done.md) | Stage: data-capture enhancements — DONE | Session | 2026-08-18 |
| [`_handoff/stage-badge-done.md`](_handoff/stage-badge-done.md) | Stage: badge lifecycle fix + toggle — DONE | Session | 2026-08-18 |
| [`_handoff/stage-waze-b2-done.md`](_handoff/stage-waze-b2-done.md) | Stage: Waze logcat tag fix + Track B2 screenshot | Session | 2026-08-17 |
| [`_handoff/stage-logging-done.md`](_handoff/stage-logging-done.md) | Stage: log ALL VietMap signals — DONE | Session | 2026-08-17 |
| [`_handoff/stage-ui-done.md`](_handoff/stage-ui-done.md) | Stage: visual badge-placement UI + relocate VietMap source | Session | 2026-08-17 |
| [`_handoff/stage-foundation-done.md`](_handoff/stage-foundation-done.md) | Stage: badge foundation + BUG-1 unify — DONE | Session | 2026-08-17 |
| [`_handoff/handoff-2026-08-17-2.0-isolation-cast-cleanup-research.md`](_handoff/handoff-2026-08-17-2.0-isolation-cast-cleanup-research.md) | Handoff: 2.0 isolation + cast cleanup + speed/HUD research | Session | 2026-08-17 |
| [`_handoff/handoff-2026-08-17-repo-split-and-revival.md`](_handoff/handoff-2026-08-17-repo-split-and-revival.md) | Handoff: tách repo base + revive Waze/VietMap signal | Session | 2026-08-17 |
| [`_handoff/next-session-A-coding-2026-08-16.md`](_handoff/next-session-A-coding-2026-08-16.md) | Handoff A — coding (update app) nguồn phiên 08-16 | Session | 2026-08-16 |
| [`_handoff/next-session-B-research-hud-2026-08-16.md`](_handoff/next-session-B-research-hud-2026-08-16.md) | Handoff B — research HUD kính (owner vs Sealion 6) | Session | 2026-08-16 |
| [`_handoff/hud-cluster-injection-findings-2026-08-10.md`](_handoff/hud-cluster-injection-findings-2026-08-10.md) | HUD/cluster injection — on-car findings & handoff | Session | 2026-08-10 |

---

## Design / Evidence & Research / Reference (context — giữ tại chỗ, ngoài 9-loại chính)

> Các thư mục làm việc cũ, giữ làm bằng chứng/nghiên cứu lineage. Context-only (R0: không authoritative).

- [`design/navigation-hud-evidence.html`](design/navigation-hud-evidence.html) — bằng chứng Stage 2 nhánh Navigation + HUD
- [`design/cluster-cast-evidence.html`](design/cluster-cast-evidence.html) — bằng chứng Stage 2 nhánh Cluster Cast
- [`research/gps-dead-reckon-tunnel.html`](research/gps-dead-reckon-tunnel.html) — nghiên cứu mất GPS trong hầm (Dead-Reckon đã REMOVE)
- [`reference/dashcast-projection-recipe.md`](reference/dashcast-projection-recipe.md) — recipe cast lịch sử (ARCHIVED; thay bằng `specs/cluster-cast-rebaseline.html`)
- [`refactor-car-execution/index.html`](refactor-car-execution/index.html) — workspace refactor car-execution (spec/progress/fixtures/evidence — lịch sử). **Đại diện cho cả thư mục**, gồm: [`spec.html`](refactor-car-execution/spec.html) · [`progress.md`](refactor-car-execution/progress.md) · [`coupling.md`](refactor-car-execution/coupling.md) · [`layering-rules.md`](refactor-car-execution/layering-rules.md) · [`run-on-car.md`](refactor-car-execution/run-on-car.md) · [`scope-review.md`](refactor-car-execution/scope-review.md) · [`evidence/2026-07-27-night-build.md`](refactor-car-execution/evidence/2026-07-27-night-build.md) · [`evidence/2026-07-27-owner-correction.md`](refactor-car-execution/evidence/2026-07-27-owner-correction.md) — tất cả **Historical** (nỗ lực 2026-07-27, thời GPS Dead-Reckon; giữ tại chỗ làm lineage, R0 không authoritative)
- `images/` — ảnh minh hoạ dùng trong guide/README (assets, không phải doc)

## Archive — `archive/` (trạng thái: doc bị thay thế; giữ history, KHÔNG authoritative)

> **(VI)** Toàn bộ doc lịch sử đã nghỉ hưu nằm trong [`archive/`](archive/) — gồm `archive/diagnostics/`, `archive/review/`, `archive/_handoff/`. Không liệt kê chi tiết từng file ở đây; lịch sử git được giữ nguyên. **Không** dùng làm authoritative.
> **(EN)** All retired historical docs live under [`archive/`](archive/) (`archive/diagnostics/`, `archive/review/`, `archive/_handoff/`). Not enumerated here; git history preserved. Not authoritative.
