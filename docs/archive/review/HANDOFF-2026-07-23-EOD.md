# ClusterNav — HANDOFF dự án (2026-07-23, cuối ngày)

> Bản tổng cho phiên sau. Gộp cả 2 track. Đọc file này + `CLAUDE.md` TRƯỚC khi chạm code.
> (Handoff buổi sáng `HANDOFF-2026-07-23.md` để untracked vì chứa IP nội bộ — bản này thay thế, không IP.)

---

## 0. TL;DR — trạng thái & việc kế
1. **Track 1 (app main release v0.60)** — ĐÃ XONG phần offline: dọn sạch cho user + teardown-guard [P0] + test E2E/stress + script verify xe. **184 test off-xe pass.** CHƯA merge main — **chờ chạy `scripts/on-car-verify.sh` trên xe**; FAIL=0 → merge `release/v0.60-cast-hardening` → `main` + đặt APK vào `apk/` (UpdateChecker tự đẩy).
2. **Track 2 (bản debug `com.byd.clusternav.debug`)** — ĐÃ XONG: sửa gốc "log lẫn lộn" + export ra external + isolation. Dùng để **dò lại nguồn nav-signal cho SẠCH** khi có xe (kết luận cũ KHÔNG tin được).
3. **Nav-signal (dò tín hiệu nav từ app khác GMaps)** — **CHƯA CÓ KẾT LUẬN ĐÁNG TIN.** Bản debug track 2 sinh ra để trả lời câu này lần sau cho sạch.
4. Xe cùng mạng (IP hỏi user / `adb devices`). APK release build sẵn ở `apks/ClusterNav-release.apk` (ngoài repo).

---

## 1. Dự án (1 đoạn)
ClusterNav: app Android **no-root** trên đầu xe BYD DiLink (DL3=Android10, DL5=Android12). Hai việc chính:
(a) **Nav-lane lên cụm** — đọc dẫn đường Google Maps (NotificationListener + accessibility) → bắn lên cụm đồng hồ, GIỮ đồng hồ gốc. (b) **Chiếu app lên cụm** — bê app (Vietmap/Waze/CP/AA…) sang virtual display (VD) của cụm qua **dadb** (ADB client thuần JVM, nối `localhost:5555`=uid shell 2000, không cần mạng). Chạy trên xe đang lăn bánh → regression = tài xế phải reboot đầu máy. Ưu tiên: **đúng > an toàn > nhanh**. Chủ dự án: **Đăng Khôi** (lái Seal DL3).

---

## 2. Git & nhánh (QUAN TRỌNG)
| Nhánh | HEAD | Nội dung | Trạng thái |
|-------|------|----------|-----------|
| `main` | `ff01199` (v0.57) | i18n + update checker | nguyên, chưa đụng |
| **`release/v0.60-cast-hardening`** | `bf3d9b7` (v0.60) | **Track 1** — teardown-guard + clean + test + script xe | ✅ offline done · CHƯA merge (chờ verify xe) |
| **`debug/navprobe-clean`** | `da1acf0` (v0.60-debug) | **Track 2** — máy dò nav sạch, applicationId `.debug` | ✅ done · KHÔNG merge main (nhánh nghiên cứu độc lập) |
| `fix/diag-flags-sizecompat` | `2484450` (v0.59) | cũ — đã thay bằng release branch | **BỎ** (không dùng nữa) |

- Git author repo-local: **Đăng Khôi `<dangkhoi@users.noreply.github.com>`** (KHÔNG email công ty — repo PUBLIC). Giữ nguyên.
- Quy trình: mỗi việc → nhánh riêng → PR → merge. **KHÔNG commit thẳng main.**
- `gh` CLI KHÔNG có → PR mở qua web hoặc merge tay sau khi verify.

---

## 3. TRACK 1 — app main release (chiếu cụm ổn định cho anh em)

### 3.1 Đã làm (4 commit trên `release/v0.60-cast-hardening`)
| Commit | Nội dung |
|--------|----------|
| `d26d039` | **Teardown-guard [P0]**: `phoneProjectionSinksOn` (pure) + `guardSinksOffVd` (bê sink CP/AA khỏi VD → display 0, move-stack GIỮ phiên; move fail → KHÔNG teardown = fail-safe) tại warm cmd16 re-project, warm emergency-teardown, `rollback()`; R3-block bê sink; sửa comment R3 quy-kết-sai; post-op divergence; `castLogger` tee log ra `getExternalFilesDir/castlog/`. Bump v0.60. |
| `09cdd80` | **Clean cho user (Mức 1)**: GỠ `modules/navprobe/` (máy dò — tự arm accessibility + ghi noti/màn = **privacy risk**), `navtrace/`, `CollectActivity`+`CollectStore`, `AutotestActivity`, res/xml/navprobe_accessibility, WRITE_EXTERNAL_STORAGE + requestLegacyExternalStorage. GIỮ core + DiagActivity/castLogger (support, chỉ chụp state). |
| `c16b4b5` | **Test E2E + stress off-xe**: `FakeShell`+`FakeDevice` (render `am`/`dumpsys` khớp parser, mutate move-stack) + `CastFlowTest` (15) + `CastStressTest` (5). Seam `guardSinksOffVd`/`divergenceOn`/`applyBounds` private→internal. |
| `bf3d9b7` | **Script verify TRÊN XE**: `scripts/on-car-verify.sh`. |

### 3.2 Test: **184 pass off-xe** (0 fail)
Covered thật: teardown-guard P0, divergence 2-mẫu P0, resize-guard P0 (đo tận wire không đụng màn giữa), scale tier1, warm/cold, isPhoneProjection, ClusterProfile, NavParse, AppScale, stress đổi-app-200-vòng + resize-200-vòng + move-fail-fail-safe.
**Ghi nợ (dùng dadb thật → CHỈ verify trên xe được):** `placeLadder` R1/R2/R3, warm-restore-streak cap, single-flight `scaleApplying`, `applyBounds` tier2/3 (overscan/wm-size).

### 3.3 CÒN LẠI — verify trên xe rồi ship
1. Cài: `adb install -r apks/ClusterNav-release.apk` (v0.60, chữ ký nhóm → không cần gỡ).
2. Chạy: `scripts/on-car-verify.sh <IP-xe>:5555` — tự dò mồ côi (so `am stack list` vs `dumpsys window displays`) + assert clean-release + watch-mode stress 60s; hướng dẫn thao tác chiếu.
3. **Trọng tâm nhìn tận mắt**: chiếu **CP/AA → đổi app / TẮT** cụm KHÔNG kẹt (bug reboot cũ); đổi app+resize liên tục không mồ côi; TẮT → đồng hồ gốc về.
4. **FAIL=0** → merge `release/v0.60-cast-hardening` → `main` + `cp apks/ClusterNav-release.apk apk/ClusterNav-0.60-release.apk` (commit vào main để UpdateChecker `main/apk/` thấy). **FAIL≠0** → đọc `on-car-verify-*/` (snapshot+castlog), sửa, KHÔNG merge.

---

## 4. TRACK 2 — bản debug dò nav (nghiên cứu, cài SONG SONG release)

### 4.1 Đã làm (commit `da1acf0` trên `debug/navprobe-clean`, off main)
- **Sửa GỐC "log lẫn lộn mới/cũ"** (RT2.1): `NavProbe.file()` cũ tái dùng file bất kể version/ngày → hôm nay append vào file 07-22. Nay session-key = `versionName@boot_count` → đổi version HOẶC reboot → **file MỚI**. `shouldReuseFile` pure + 5 test.
- Export navprobe ra `getExternalFilesDir` ở bản DEBUG (adb pull thẳng); release giữ nội bộ. Bật `buildFeatures.buildConfig`.
- Mỗi record kèm `⟨fg=…⟩` (app foreground lúc bắt) + note giới hạn sender (Android giấu app phát broadcast).
- Nút **CÔ LẬP** (`isolateToApp`): force-stop nav khác → chỉ còn app đang mở phát → chốt nguồn.
- Build variant `.debug`: `applicationId com.byd.clusternav.debug`, `versionName 0.60-debug`, label "ClusterNav DEBUG" → cài cạnh release. Fix `NavConnect.COMP` hardcode (bản debug không disallow nhầm listener bản release).
- 164 test pass. Senior review APPROVED. Máy dò 5 kênh (notification/HAL/màn hình/broadcast/MediaSession) còn NGUYÊN ở nhánh này.

### 4.2 CÒN LẠI — dò nguồn nav-signal cho SẠCH
1. `./gradlew assembleDebug` → `adb install -r apks/ClusterNav-debug.apk` (cài CẠNH release, 2 icon).
2. Lái với Vietmap/Waze/CP/AA → `adb pull /sdcard/Android/data/com.byd.clusternav.debug/files/navprobe/` (giờ file MỚI mỗi phiên, KHÔNG lẫn cũ).
3. Bấm **CÔ LẬP** trong màn Máy dò để chốt: app nào phát `AUTONAVI_STANDARD_BROADCAST_SEND`.

---

## 5. NAV-SIGNAL — trạng thái điều tra (đọc kỹ, tránh lặp lỗi)
- **Kết luận phiên trace 23/07 KHÔNG ĐÁNG TIN** (chủ dự án chỉ ra): file navprobe trộn 07-22 + hôm nay → mọi quy kết nguồn nhiễu. Track 2 sinh ra để sửa gốc này.
- Sự thật đo SẠCH (giữ lại): **AA & CP là video-surface** (`uiautomator dump`=null, chỉ pixel); cụm không orphan sau reboot.
- **`AUTONAVI_STANDARD_BROADCAST_SEND`** mang đủ turn-by-turn (NEXT_ROAD_NAME, SEG_REMAIN_DIS, NEW_ICON, ROUTE_REMAIN_DIS/TIME, ETA) — NHƯNG **nguồn CHƯA xác nhận**. Bằng chứng nghiêng về **Vietmap / nav hệ thống** (string tiếng Trung, AutoNavi SDK; không có app Amap độc lập; AA=Google Maps/CP=Apple đều không nói giao thức này), **KHÔNG phải AA/CP**. ⚠ Mình từng gán nhầm "AA breakthrough" lúc 12:07 — ĐÃ đính chính.
- Vietmap/Waze notification = keepalive (không nav data); MediaSession không có nav. → nếu Vietmap phát AUTONAVI thì đó là đường sáng (đảo ngược "Vietmap ngõ cụt").
- Dữ liệu thô: `docs/diagnostics/carlog-2026-07-23-trace/` (gitignored — PII). README + live/aa-1156/FINDINGS.md (có banner đính chính) + live/cp-1221/FINDINGS.md (thí nghiệm cô lập).

---

## 6. BYDMate (tham khảo — KHÔNG phải giả lập xe)
`github.com/AndyShaman/BYDMate` = app THẬT trên DiLink 5 (không emulator). Đáng học: chiếu cụm 2 mode — **Factory** = **VirtualDisplay mirror** (không đổi system setting, **tránh hẳn orphan**) vs **Extended** = freeform trên display cụm (giống ClusterNav hiện tại). → Hướng "Factory/mirror" sạch hơn cách move-stack, cân nhắc refactor sau. Cũng có RBGboost RE giao thức HUD SOME/IP (liên quan nav-lane).

---

## 7. Build & môi trường
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # đổi từ path Windows sang path máy bạn
./gradlew --offline testDebugUnitTest        # 184 test (release branch)
./gradlew --offline assembleRelease          # APK → apks/ClusterNav-release.apk
./gradlew --offline assembleDebug            # bản .debug → apks/ClusterNav-debug.apk
cp /tmp/local.properties.bak local.properties  # TRẢ local.properties về path Windows sau build
```
- `local.properties` repo mặc định = path Windows của chủ dự án; đổi Mac lúc build, trả lại sau. (gitignored)
- Security scan (CLAUDE.md §6) TRƯỚC mọi commit. Repo PUBLIC.

---

## 8. Nợ & việc mở (ưu tiên)
| P | Việc | Ghi chú |
|---|------|---------|
| **P0** | Verify Track 1 trên xe (`on-car-verify.sh`) → merge main → ship v0.60 | ĐANG chờ lên xe |
| P1 | Track 2: dò nguồn AUTONAVI cho sạch (Vietmap? system?) bằng bản debug | isolate + pull external |
| P1 | Quyết hướng cờ `force_resizable_activities` (§6b cũ): 1 cờ global không thắng cả Vietmap-float lẫn CP/AA-fill | quyết định chủ dự án |
| P2 | Tách `ClusterCast.kt` (~1300 LOC, quá ngưỡng 500) | KHÔNG làm sát lúc lên xe |
| P2 | Test on-xe cho luồng deferred (placeLadder/warm-streak/single-flight/applyBounds tier2/3) | qua on-car-verify.sh |
| P3 | Cân nhắc refactor chiếu theo "Factory mode" (VirtualDisplay mirror) như BYDMate | tránh orphan tận gốc |
| P3 | `NavDiag.snapshot/cadence` + `RemoteViewsModule.introspect` mất caller sau Mức-1 (inert, compile OK) | dọn sau nếu muốn |

---

## 9. Bài học phiên này
- **Đừng quy kết khi chưa kiểm** — mình gán "AA phát AUTONAVI" (12:07) rồi thí nghiệm cô lập chứng minh SAI (đứng yên 185 suốt AA+CP nav). Đúng lỗi §2 handoff cũ cảnh báo (v0.59 "regression" cũng là quy kết sai). Trước khi kết luận nguồn: đo delta CÓ CÔ LẬP + grep log THẬT.
- **Log lẫn cũ/mới huỷ hoại kết luận** — navprobe tái dùng file là gốc. Bản debug fix bằng session-key.
- **Bản ship cho user phải sạch privacy** — máy dò tự arm accessibility ghi noti/màn là không được ship; tách hẳn sang bản debug.
- **Không có emulator xe** → test off-xe qua seam `sh` (FakeShell); phần dadb thật → script verify on-xe.
