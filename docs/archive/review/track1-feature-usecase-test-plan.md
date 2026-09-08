# Track 1 (app main release v0.60) — Feature / Usecase inventory + Test plan

> Sau khi dọn Mức-1 (gỡ navprobe/navtrace/collect/autotest). Mục tiêu: E2E test từng usecase + stress/load
> TRƯỚC release. Off-device qua **FakeShell** (mô phỏng `am`/`dumpsys`), vì không có emulator xe (xem §BYDMate).

## BYDMate làm giả lập xe? — KHÔNG
BYDMate là app THẬT trên đầu xe (đọc BMS/autoservice), không phải emulator PC → không giả lập xe được.
Không có emulator BYD DiLink sẵn (cụm XDJA/AutoContainer/HAL proprietary). → test off-xe = FakeShell harness.
(Tham khảo hay: BYDMate "Factory mode" chiếu cụm bằng VirtualDisplay **mirror** — tránh hẳn orphan; hướng sạch hơn cho sau.)

## FEATURES (release)
| # | Feature | Module/file | Core? |
|---|---------|-------------|-------|
| F1 | Nav-lane lên cụm (booster GMaps: đọc noti+màn → HAL/broadcast cụm, giữ đồng hồ) | NavNotificationListener, navaccess, NavRealtime, RemoteViews, ClusterBroadcaster, NotificationParser, NavParse | ✅ |
| F2 | Chiếu app lên cụm (VD projection R1/R2/R3 + teardown-guard) | clustercast/ClusterCast, CastShell, StackParse, WmParse, DisplayParse | ✅ |
| F3 | Scale/DPI per-app (chỉnh cạnh + DPI) | clustercast/AppScale, applyBounds/applyScaleLive | ✅ |
| F4 | GPS-hầm dead-reckon (mock location) | deadreckon, mockloc | ✅ |
| F5 | Đa-model ClusterProfile (auto-detect + override) | clustercast/ClusterProfile | ✅ |
| F6 | Nút nổi (bubble) chạm chiếu | clustercast/FloatingBubbleService | ✅ |
| F7 | i18n VI/EN + UpdateChecker (GitHub) | Lang, UpdateChecker | ✅ |
| F8 | Chẩn đoán support (chụp state → file) | clustercast/DiagActivity, ClusterDiag, castLogger | support |
| F9 | Audio cue dẫn đường / dash vehicle data / vdmap | navaudiocue, dash, vdmap | phụ |

## USECASES (luồng user) + cách test
> Trọng tâm test = F2/F3 (chiếu + resize) theo yêu cầu. Seam: `sh:(String)->String` (fake được).

| UC | Luồng | Test type | Seam / cách |
|----|-------|-----------|-------------|
| UC1 | Cast COLD (chưa chiếu) → castSeq → dò VD → placeLadder → applyBounds | flow (FakeShell) | qua StackParse.isWarm=false + placeLadder rung + applyBounds |
| UC2 | Cast WARM (đang chiếu, đổi app) → bê app cũ → guard sink → cmd16 → đặt app mới | flow | StackParse.isWarm=true; **guardSinksOffVd** trước cmd16 |
| UC3 | **Teardown-guard**: sink CP/AA trên VD → bê về display 0 (giữ phiên) TRƯỚC huỷ/tái tạo VD | flow (P0) | guardSinksOffVd: có sink→move; move fail→false(fail-safe); keepPkg loại; verify sau |
| UC4 | R1/R2/R3 ladder: R1 am start bám? → R2 move-stack? → R3 force-stop (chặn cho sink) | flow | placeLadder qua FakeShell (rung nào bám tuỳ state) |
| UC5 | STOP: bê stack khỏi VD → reset display → teardownSeq → trả đồng hồ | flow | stop()/rollback() logic (guard + teardownSeq) |
| UC6 | Rollback khi cast fail giữa chừng | flow | rollback(): guard sink + resetDisplayAll + teardownSeq |
| UC7 | Scale tiers: freeform sống→`am task resize`; hụt→`wm overscan`; app phớt inset→`wm size` | flow | **applyBounds** qua FakeShell (freeform alive/not) |
| UC8 | Scale GUARD P0: task KHÔNG ở VD → TỪ CHỐI resize (không đụng màn giữa); vd<1 → bỏ | unit (P0) | applyBounds guard |
| UC9 | Divergence (WM↔AM lệch = orphan) → CHẶN mọi thao tác cụm (2-mẫu chống false-positive) | flow (P0) | **divergenceOn**: orphan 2 mẫu→chặn; 1 mẫu→qua; sạch→null |
| UC10 | reconcileOnStart / watchdogTick: dọn floating trên màn giữa + app chiếu chết | flow | StackParse.floatingOnMain + watchdog logic |
| UC11 | isPhoneProjection nhận diện CP/AA (không hardcode) | unit | ClusterCast.isPhoneProjection |
| UC12 | ClusterProfile resolve theo đời xe (DL2/3/4/5) | unit | ClusterProfileTest (có) + bổ sung |
| UC13 | Nav parse: distance/road/eta từ noti GMaps (VI/EN, các format) | unit | NavParse/NotificationParser (có) + bổ sung |
| UC14 | AppScale bounds/overscan/forcedSize theo % + per-cạnh | unit | AppScaleTest (có) + bổ sung |

## STRESS / LOAD (yêu cầu: đổi app + resize liên tục)
| SL | Kịch bản | Assert |
|----|----------|--------|
| SL1 | Đổi app qua lại N lần (warm) trên FakeDevice | không sinh stack mồ côi; guard bê sink mỗi lần; state hội tụ 1 app trên VD |
| SL2 | Resize liên tục N lần (applyBounds) giá trị đổi | luôn đúng tier; không đụng task ≠ VD; output nhất quán |
| SL3 | guardSinksOffVd gọi lặp (idempotent) | sink đã bê rồi → true ngay, không lệnh thừa |
| SL4 | Divergence xen giữa chuỗi đổi app | chặn đúng lúc orphan, mở lại khi sạch |
| SL5 | move-stack fail ngẫu nhiên giữa stress | fail-safe: không teardown khi guard fail |

## SEAMS cần mở (internal + @VisibleForTesting) — chỉ để test, không đổi public API
- `ClusterCast.guardSinksOffVd(sh, vd, keepPkg, log)` — private → internal
- `ClusterCast.divergenceOn(sh, vd)` / `sampleDivergence` — private → internal
- `ClusterCast.applyBounds(sh, vd, e, scale, w, h)` — private → internal
- (đã public: `phoneProjectionSinksOn`, StackParse/WmParse/DisplayParse/AppScale)
- Ladder R1/R2/R3 (`placeLadder`) + warm-streak + single-flight: dùng `adb: dadb.Dadb` (resolveComp) → CHƯA fake được off-device không refactor lớn → test gián tiếp qua StackParse + verify trên xe. Ghi nợ.

## HARNESS
`FakeShell` = `(String)->String` + `FakeDevice` state model: danh sách stack theo display, freeform-alive flag, VD size, cờ inject-fail. Đáp `am stack list` / `dumpsys display` / `dumpsys window displays` / `am display move-stack` (mutate) / `am task resize` (accept nếu freeform-alive) / `wm ...`. Fixture định dạng NGUYÊN VĂN Android 10.

## Script verify trên xe
`scripts/on-car-verify.sh [ip:port]` — TỰ ĐỘNG dò mồ côi (WM↔AM) + assert clean-release + watch-mode stress; HƯỚNG DẪN thao tác chiếu trong app. Chạy trước khi merge main.
