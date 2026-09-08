# Session handoff — 2026-08-11 (b) · Cast enable toggle + on-car UX fixes + item-3 probes

> **Trạng thái:** RELEASE CANDIDATE **1.05 = `fd63d1a346cc`** đã build + off-car verify + đã cài-test 1 vòng trên xe. **CHƯA commit/push.** HEAD vẫn `d85b9f2` trên `main`, **130 file uncommitted**.
> Owner: Đăng Khôi · dangkhoi. Tiếp nối `docs/_handoff/session-2026-08-11-v1.05-release-candidate.md`.
> Env build: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=$HOME/Library/Android/sdk ANDROID_SDK_ROOT=$HOME/Library/Android/sdk`.
> Spec: `docs/specs/cast-enable-toggle.html` (Changelog + Reviewer Log Pass 0/1/2).

---

## 1. Phiên này làm gì (tất cả OFF-CAR trừ mục §5, chưa commit)

Thêm **master toggle bật/tắt Cluster Cast** + fix theo feedback on-car. Bốn nhóm việc:

### Feature: Cast enable/disable master toggle (spec `cast-enable-toggle.html`)
- Pref bền `SimpleCastPrefs.castEnabled` (:core) + impl SharedPrefs (`"cast_enabled"`) + FakePrefs.
- **BẬT** → openProjection + start `FloatingBubbleService` (nút nổi). **TẮT** → `Stop` + `closeProjection` (cụm về đồng hồ) + stop service, KHÔNG reopen; ẩn UI Cast bên phải (`cast_body` GONE + hint), nút cập nhật vẫn hiện.
- Gate 3 điểm: `MainActivity` (start service), `MainActivityCastController` (openProjection), `FloatingBubbleService.onCreate/onStartCommand` (stopSelf khi tắt, kể cả boot).
- Class mới `CastEnableSwitch.kt`; layout 2 bản thêm `switch_cast_enabled` + `cast_body` + `txt_cast_disabled_hint`.

### Fix on-car #1 — quyền notification (in-app, không cần adb)
- **Root cause:** app CHỈ cấp notification qua dadb `allow_listener` (im lặng), KHÔNG có đường mở màn "Truy cập thông báo" hệ thống → user cài mới (mất khoá dadb ở `filesDir`) bị kẹt.
- **Fix:** nút "Cấp quyền / kết nối lại" → nếu thiếu quyền → dialog + `openNotificationAccessSettings()` (deep-link API30+ → list API29 → app-details fallback); `onResume` tự bind lại khi quay về. `MainActivity` + test `NotificationAccessFlowContractTest`.

### Điều chỉnh theo feedback on-car (3 việc owner chốt)
1. **Default Cast = TẮT** (`getBoolean("cast_enabled", false)` + FakePrefs false + fail-safe `getOrDefault(false)`). Nav-only là mặc định → mở app: cụm native + nav OEM hiện ngay, KHÔNG projection/cong/đen.
2. **Bỏ checkbox `cb_lane` thừa** — cluster-lane theo master switch Navigation+HUD (mặc định bật). `MainActivity` gỡ `laneEnabled`; master enable → `CLUSTER_LANE=true` + speed-sign CLUSTER; + **startup apply** (fix [P1] senior review: user cũ lane=false + listener tạo coordinator sớm sẽ không lên lane); ép `Prefs.setLane(true)` migrate. Layout 2 bản gỡ `cb_lane`.
3. **Nav trên cụm = overlay OEM AMapService** (KHÔNG vẽ overlay riêng — owner chốt). **KHÔNG thêm code.** Xem §4 vì sao + §6 probe.

---

## 2. Candidate 1.05 — lịch sử & bản hiện tại

Trên đĩa có 4 APK `1.05-v105-*` (versionCode 105). **Bản authoritative = cái `vehicle-candidate.json` trỏ tới:**

| sourceId (frag) | Nội dung | sha256 | Trạng thái |
|---|---|---|---|
| `fbd8331ffa8c` | 1.05 gốc (7 việc cast-nav-ux, trước phiên này) | d1d3db74… | superseded |
| `0110755c9bcc` | + Cast enable toggle (default ON lúc đó) | 9712221b… | superseded |
| `75387e864964` | + notification-access in-app fix | 8679803f… | superseded |
| **`fd63d1a346cc`** | **+ default Cast OFF + lane theo master** | **`45a85773fbe27cf848d33f5f1178130f33c7484c4ae4c02d982f36460a82ec71`** | **HIỆN TẠI** |

- `docs/_handoff/vehicle-candidate.json` → `fd63d1a`; `require_candidate` **exit 0**; not-debuggable; **0** `TEST_ADAS_*/TEST_SPEED_LIMIT`.
- Các bản cũ **KHÔNG blocklist** (đều hardened, non-toxic). Bản 1.04 độc hại (`b9a0259e…`) vẫn blocklisted từ phiên trước.
- Rebuild (reproducible):
  ```bash
  python3 scripts/evidence/gen-exact-source.py --out docs/_handoff/v1.05-exact-source.json --label "1.05" --base docs/_handoff/v1.04-exact-source.json   # in sourceId
  ./gradlew clean collectAuthorizedApk -PclusterNavVariant=release -PclusterNavSlice=v105 -PexactSourceId=<sourceId> -PexactSourceManifest=docs/_handoff/v1.05-exact-source.json
  ```
- ⚠️ **Reproducibility note:** manifest của `fd63d1a` được sinh TRƯỚC khi thêm probe script/docs ở §6. APK không đổi (probe không nằm trong APK), nhưng nếu regen manifest bây giờ sẽ ra sourceId khác (đã có thêm file). Cần candidate "sạch" gồm cả probe → rebuild lại (sẽ attest luôn probe). `fd63d1a` vẫn dùng test item 1/2 được.

---

## 3. Cổng chất lượng (off-car)

- **Tests:** core **727** + app **342** + car-integration **28** = **1097, 0 failures.** `lintRelease` 0 error (abortOnError=true). Mọi file ≤500 LOC.
- **Senior review (opus):**
  - Pass 1 (toggle + notif): APPROVED, 0 P0–P1.
  - Pass 2 (default-off + lane): APPROVED — bắt [P1] startup-lane-apply (đã vá), + vài stale comment (đã vá).
- Test mới phiên này: `CastEnableToggleContractTest` (11), `NotificationAccessFlowContractTest` (3); cập nhật `HudOutputHiddenContractTest`, `SpeedSignSourceLifecycleTest`.
- **Security scan CHƯA chạy** — là cổng TRƯỚC commit/push (bắt buộc). Chạy khi owner test xe OK.

---

## 4. Hiểu biết on-car (đắt giá — nền cho quyết định)

- **Cụm = display 1** (`fission_bg_xdjaVirtualSurface`, virtual, owner `com.xdja.containerservice`, layerStack 1). Màn chính = display 0.
- **Cast projection** dùng OEM service `AutoContainer`: open `30(cong giữ km/h)→16→35`, close `18→0`. `openProjection` cũng đặt `ClusterBlackActivity` (nền đen) + `wm size/overscan/density` (NORMAL_DEFAULT) lên display 1.
- **`closeProjection` (18→0) KHÔNG revert kiểu cong (30)** → cụm kẹt cong sau khi tắt. Flip phẳng = opcode **31**, NHƯNG `31`/`0` **re-init container OEM → RESTART màn chính** vài giây (đã gặp; KHÔNG tự động hoá).
- **Nav trên cụm = OEM AMapService**: `ClusterBroadcaster.emitLane` → sendBroadcast (AUTONAVI) → `AmapService` nhận (`mIsGAODENaving=true`) → `sendNaviToCluster` → `send to independent CAN` (发送独立仪表). **App không vẽ nav trên cụm** — chỉ feed OEM. Pipeline verify live OK trên xe.
- **Vì sao "nav ko lên cụm" lúc đầu:** Cast ON (default cũ) → `ClusterBlackActivity` phủ display 1 → che nav. → Đổi default OFF (§1.3) giải quyết ca nav-only.
- **Cast + nav đồng thời:** khi cast app (VietMap), nav overlay OEM lúc hiện lúc bị app che → **z-order do compositor OEM quyết**, app KHÔNG set trực tiếp được (nav overlay không phải window của ClusterNav; window dump display 1 chỉ có app cast + ClusterBlackActivity). Poke opcode để ép = rủi ro restart màn chính. ⇒ item 3 chuyển sang **probe on-car** (§6).
- **Notification qua dadb** cần "Allow USB debugging" 1 lần; **uninstall xoá `filesDir` = mất khoá** → phải cấp lại. Cài đè (`install -r`, cùng chữ ký) giữ nguyên.

---

## 5. Đã test trên xe phiên này (<vehicle-ip>)

- Cài `75387e` (notif fix) qua `install -r` → **quyền notification in-app CHẠY ĐÚNG** (mở màn Notification access, bật ClusterNav, nav lên). ✅
- Default-on cũ → cụm cong+đen che nav; tắt Cast → cong kẹt; chạy tay `service call AutoContainer …31/0` → về phẳng NHƯNG **restart màn chính** (không phải reboot xe, uptime giữ). Owner xác nhận cụm/màn chính đều hồi.
- Cast VietMap → nav overlay OEM hiện, rồi bị che sau khi đổi dẫn đường (z-order OEM).
- **`fd63d1a` (default-off + lane) CHƯA cài-test trên xe** — đây là bước resume chính.

---

## 6. Item-3 & VietMap bubble — probe on-car (chưa làm, để track riêng)

- **Item 3 (nav đè lên cast):** KHÔNG code (OEM-limited). Đã dựng **probe**: `scripts/vehicle/cast-nav-overlay-probe.sh` + runbook.
- **VietMap "bong bóng tốc độ" sang cụm:** bê THẲNG overlay của VietMap = **KHÔNG được** (overlay window thuộc display app chủ tạo; không reparent cross-display từ ngoài). Đường duy nhất cho biển-báo-VietMap-trên-cụm (vì OEM speed-limit read-only): **ClusterNav tự vẽ bubble** (option B) dùng data VietMap widget đã đọc — owner **chưa chốt làm**; nếu làm thì gộp vào track cast/projection + z-order.
- **1 CHỖ DUY NHẤT cho probe on-car:** `docs/diagnostics/oncar-probes-2026-08-11.md` (gộp Probe A speed-limit #3 [chi tiết ở `oncar-speedlimit-test-2026-08-11.md`] + Probe B nav-đè-cast). Tool: `apks/navopen-v4.jar`, `scripts/vehicle/{hud3-speedlimit-v4.sh,cast-nav-overlay-probe.sh}`.

---

## 7. File thay đổi chính (uncommitted)

- **Feature toggle:** `core/.../simplified/SimpleCastModels.kt`, `app/.../simplified/SimpleCastRuntime.kt`, `core/…/CastCoordinatorTestFakes.kt`, `app/.../clustercast/FloatingBubbleService.kt`, `app/.../clustercast/CastEnableSwitch.kt`(mới), `app/.../clustercast/MainActivityCastController.kt`, `app/src/main/res/layout{,-w960dp}/activity_main.xml`.
- **Notif + item1/2:** `app/.../MainActivity.kt`.
- **Tests:** `app/src/test/.../CastEnableToggleContractTest.kt`(mới), `NotificationAccessFlowContractTest.kt`(mới), `HudOutputHiddenContractTest.kt`, `SpeedSignSourceLifecycleTest.kt`.
- **Docs/probe:** `docs/specs/cast-enable-toggle.html`, `docs/diagnostics/oncar-probes-2026-08-11.md`(mới), `scripts/vehicle/cast-nav-overlay-probe.sh`(mới), `docs/_handoff/{v1.05-exact-source.json,vehicle-candidate.json}`.
- (+ WIP cũ từ phiên trước — 7 việc cast-nav-ux, sign-investigation — vẫn nằm trong scope commit chung.)

---

## 8. RESUME HERE — bước tiếp

1. **Cài `fd63d1a` lên xe, test item 1+2** (cài đè, KHÔNG uninstall):
   ```bash
   adb connect <vehicle-ip>:5555
   adb -s <vehicle-ip>:5555 install -r apk/ClusterNav-1.05-v105-fd63d1a346cc-release.apk
   adb -s <vehicle-ip>:5555 shell dumpsys package com.byd.clusternav | grep versionName   # 1.05
   ```
   Kỳ vọng: mở app → **Cast mặc định TẮT** → cụm native + nav OEM hiện ngay (không cong/đen); thẻ Cluster Cast bên phải thu gọn; bật Cast → mới chiếu + nút nổi. Switch "Bật" (Navigation+HUD) bật/tắt lane cụm; không còn checkbox lane thừa.
2. **(Tùy) Probe on-car** theo `docs/diagnostics/oncar-probes-2026-08-11.md`: Probe B (nav đè cast) + Probe A (speed-limit). Báo bước thắng của Probe B → agent wire opcode vào luồng cast.
3. **Test OK → báo agent:** chạy **security scan** (bắt buộc trước push) → **commit scope A** (gom toàn bộ uncommitted thành baseline `release: v1.05 — cast toggle + notif-access + default-off/lane`) → **push**.
   - ⚠ Merge vào `main` vẫn cần on-car PASS + owner authorization (README). Commit identity: `dangkhoi@users.noreply.github.com`.
4. **Nếu cần candidate gồm cả probe files** (reproducibility sạch) → rebuild §2 (regen manifest sẽ attest luôn probe).
5. Nếu test FAIL → mô tả triệu chứng, fix theo root-cause, rebuild candidate, test lại. KHÔNG commit khi chưa PASS.

---

## 9. Chưa quyết / mở
- **Option B** (ClusterNav vẽ bubble tốc-độ/biển-báo trên cụm) — chờ owner chốt; đi cùng track z-order.
- **Item 3** — chờ kết quả Probe B trên xe.
- **Nút "Khôi phục cụm phẳng"** (cho ca lỡ kẹt cong, cảnh báo restart màn chính) — đã bàn, chưa làm.
