# HANDOFF — v0.66 FREEZE-PROOF đổi-app + tên đường HUD (autonomous 2026-07-24, đêm)

> Làm autonomous đêm 23→24/07 theo quy trình: research sâu → plan → senior-review-plan → multi-agent implement → senior-review-impl. **Off-car** (xe tắt). Kết quả: code + 208 test pass + 2 vòng senior review APPROVED. **On-car validation là cổng BẮT BUỘC trước khi tin "hết freeze" / merge main.**

---

## 0. TL;DR cho buổi sáng

- **Bug freeze (P0):** đổi app trên cụm → treo head unit. Root: mọi thao tác kích `AppWindowToken.initializeChangeTransition` (AOSP10) → NPE A (getDisplayInfo loop→treo) hoặc NPE B (createTaskSnapshot). 2 trigger: **huỷ VD** + **move-stack task khỏi VD**.
- **Fix v0.66:** đổi app = đặt app mới lên VD **đang sống** → bê app cũ ra bằng **`am force-stop` + relaunch fullscreen ở màn chính** (đường process-death KHÔNG kích change-transition → né CẢ 2 NPE). **KHÔNG bao giờ** huỷ VD, **KHÔNG** `move-stack …0` trên đường switch.
- **Đảm bảo invariant:** cụm luôn đúng 1 app · app cũ về màn chính fullscreen · config size/vị trí/DPI nhắm đúng app trên cụm (`lastCastApp=target`).
- **HUD tên đường:** đẩy tên **viết tắt** (`fitRoadName`, "Trần Trọng Kim"→"T.T.Kim") thay tên đầy đủ → hết tràn buffer → HUD hiện được tên (không chỉ mũi tên).
- **208 test off-car PASS.** 2 senior review APPROVED (0 P0/P1). APK: `apk/ClusterNav-0.66-release.apk`.
- ⚠ **CHƯA validate trên xe.** FakeShell không tái tạo được WM NPE thật. **Phải test xe theo §3 trước khi tin + merge.**
- ⚠ **Đánh đổi đã chốt (OQ1):** force-stop app cũ = **mất state app cũ** (Maps đang nav bị reset khi đổi đi). Ưu tiên KHÔNG-treo > giữ-state. Nếu sáng thấy không chấp nhận được → xem OQ1 (occlude-first, v0.67).

---

## 1. Tài liệu (đọc theo thứ tự)

| File | Nội dung |
|------|----------|
| `docs/specs/freeze-proof-cluster-switch.html` | **PLAN + SDD** (source of truth): Requirements R1-R8, Design D1-D4, Tasks T1-T6, Verification, Open Questions, **Reviewer Log Pass 1/2/3** (plan review + 2 impl notes + senior impl review). |
| `docs/_handoff/research-aosp-wm.md` | AOSP10 WM internals: vì sao force-stop an toàn, move-stack/huỷ-VD không. |
| `docs/_handoff/research-evidence-audit.md` | Audit evidence + primitive catalog + holes H1-H8. |
| `docs/_handoff/stage-impl-swap-done.md` · `stage-impl-hud-done.md` | Chi tiết từng stage implement. |
| `docs/review/HANDOFF-2026-07-23-oncar-freeze.md` | Nhật ký freeze saga v0.60→v0.65 (đêm trước). |

## 2. Đã đổi gì (v0.66)

**Track 1 — freeze-proof swap:**
- `CastShell.kt`: **+`returnAppToMain(adb,sh,app,vd,log):Boolean`** — bê 1 app khỏi VD về display0 fullscreen. app thường→`am force-stop`+relaunch; app giữ-phiên (CarPlay/AA, nhận qua `isKeepSession||isPhoneProjection`)→đường nhẹ, KHÔNG force-stop. Trả true CHỈ khi off-VD ∧ d0 ∧ fullscreen. **KHÔNG bao giờ `move-stack …0`.**
- `CastShell.kt`: **+`swapOnVd(...)`** (lõi shell Context-free, để unit-test được) — ②đặt B lên VD sống →(gate B landed)→ ③`returnAppToMain(old)` [guard `oldApp!=target`, re-check B còn trên VD trước khi bê old] → ④evict app lạ qua returnAppToMain → ⑤re-assert B lên top → re-pick B.
- `CastShell.evictVd`: viết lại — route mỗi stray qua `returnAppToMain` (không move-stack), giữ filter `evictableOnVd` (home/pinned an toàn).
- `ClusterCast.hotSwapOnVd`: gọi `swapOnVd` cho ②③④⑤, giữ ①density + ⑥applyBounds/setLastCastApp/setCasting/lastDisplayId/divergence.
- `stop()`/`rollback()` move-stack **GIỮ NGUYÊN** (teardown context — VD chết đằng nào, non-fatal).

**Track 2 — HUD tên đường:**
- `NavFormat.kt`: `fitRoadName(road, maxUnits=ROAD_MAX_UNITS)` (tham số hoá) + NFC-normalize đầu hàm. **+`HUD_ROAD_MAX_UNITS=7`** (= ROAD_MAX_UNITS đo thực; KHÔNG dùng 8 tới khi trace buffer HUD thật — tràn = firmware bỏ tên = đúng bug).
- `ClusterBroadcaster.pushHud`: đẩy `fitRoadName(lastCleanRoad, HUD_ROAD_MAX_UNITS)` (viết tắt) thay tên đầy đủ; dedup theo bản viết tắt.

**Test:** `CastSwapTest.kt` (11) + `NavFormatTest.kt` (+13) + FakeShell mở rộng (force-stop/am-start/resolve + FakeDadb + startNoLandPkgs). **Tổng 208 PASS.**

## 3. ON-CAR VALIDATION (BẮT BUỘC — làm sáng mai)

> Reboot = **NÚT NGUỒN** (adb reboot HỎNG). Xe `<CAR_ADB_IP>:5555`.

1. Reboot cứng → baseline sạch: `adb -s <CAR_ADB_IP>:5555 shell "cat /proc/uptime"` (thấp) + NPE=0 + gauges.
2. **(RẺ, CHẮC — validate adb TRƯỚC khi tin) đường force-stop bằng tay:**
   - Cast Vietmap (app). `am start --display 1 --windowingMode 5 -n <maps-comp>` (đặt Maps lên VD sống).
   - `am force-stop vn.vietmap.live` → `am start --display 0 --windowingMode 1 -n <vietmap-comp>`.
   - CHECK: `logcat | grep -c "Unhandled exception"` = **0** (không treo); `am stack list` → cụm (display1) **chỉ Maps**; Vietmap ở display0 fullscreen; **màn chính touch được**.
   - Nếu bước này NPE=0 + không treo → kiến trúc đúng. Nếu treo → dừng, đọc lại (force-stop khác giả định).
3. Cài `adb install -r apk/ClusterNav-0.66-release.apk` → đổi app **nhiều vòng 2 chiều** (Vietmap↔Maps↔Waze): mỗi lần NPE=0, cụm đúng 1 app, app cũ về màn chính fullscreen chạy được.
4. Resize trên cụm (đúng target, lưu). Bấm **Home/Back** sau vài lần đổi — KHÔNG treo (kiểm H4 freeform-bé đã hết).
5. Đổi **TỪ** CarPlay/AA đi (kiểm R5/OQ3: phiên chiếu-điện-thoại không chết bất ngờ; nếu CP/AA còn nằm sau trên VD thì chấp nhận, KHÔNG treo).
6. HUD (nếu có xe hội có HUD mới): xem tên đường có hiện + đúng viết tắt không; đo buffer thật → chỉnh `HUD_ROAD_MAX_UNITS` (OQ2).

**Chỉ khi §3 FAIL=0 mới: commit (nếu chưa) → port debug → merge main.**

## 4. Git state
- Branch `release/v0.60-cast-hardening`. v0.66 = **v0.65 hot-swap (đêm trước) + v0.66 freeze-proof rewrite (đêm nay)**.
- Trạng thái commit: xem cuối file (điền lúc chạy) — nếu security-scan sạch, đã commit vào **feature branch này** (KHÔNG merge main; on-car gate chặn merge).
- **Debug branch** (`debug/navprobe-clean`): CHƯA có v0.66. Port (mechanical) sau khi validate: `git checkout release/v0.60-cast-hardening -- app/src/main/java/com/byd/clusternav/modules/clustercast/ClusterCast.kt app/src/main/java/com/byd/clusternav/modules/clustercast/CastShell.kt app/src/main/java/com/byd/clusternav/NavFormat.kt app/src/main/java/com/byd/clusternav/ClusterBroadcaster.kt` + test files → build debug.
- `main`: `ff01199` (v0.57) NGUYÊN.

## 5. Open Questions (chốt sáng mai)
- **OQ1 state-loss:** force-stop app cũ mất nav-state. Chấp nhận cho v0.66. Muốn giữ → v0.67 "occlude-first" (research med-conf, cần validate xe).
- **OQ2 HUD buffer:** `HUD_ROAD_MAX_UNITS=7` tạm; cần trace buffer thật trên xe có HUD mới rồi chỉnh.
- **OQ3 keep-session switch-away:** đổi TỪ CP/AA đi — có thể còn CP/AA sau B trên VD (R2 vi phạm mềm), KHÔNG treo. Chấp nhận.
