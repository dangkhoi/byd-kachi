# Dòng thời gian khởi động sau nổ máy — 2026-10-07 (R11, spec `kachi-296-plan.html` §3)

> Nguồn: log xe 07/10 (gitignored) — lần 1 `logs/carlog-1007-restart/` (`kachi-logs/usage-1791380874910.log`, `usage-1791380900015.log`,
> `logcat-now.txt`), lần 2 `logs/carlog-1007-ign2/` (`usage-1791381802071/1826995/1880713.log`, `logcat.txt`), cùng các `usage-*.log`
> cũ hơn (06–07/10) để đối chiếu. Xe chạy 2.95. Không chép nội dung người dùng; mốc giờ là giờ xe.
> Mức bằng chứng (CLAUDE.md §2): **[ĐO]** đọc thẳng từ log/repo · **[SUY]** suy luận khớp log · **[CHƯA BIẾT]**.

## 1. Kết luận một dòng

Chậm và "tranh nhau" **không** nằm ở một lệnh chậm, mà ở chỗ mỗi lần tắt máy Kachi **chạy trọn hai lượt khởi động + hai lượt chiếu cụm**:
tiến trình dựng lại lúc màn TẮT chiếu cụm xong (~22 s) rồi mới bị lượt chữa phím tự force-stop (đã chờ chiếu yên 18 s), tiến trình con
phải **gỡ** cụm của tiến trình trước rồi **mở lại từ đầu** trên cùng kênh shell nối tiếp — đúng lúc người lái vừa bật máy. [ĐO]

## 2. Dòng thời gian — lần 1 (20:47–20:49)

Mốc 0 = màn bật `screen_toggled 1` 20:48:19.315 (ACC_ON 20:48:19.498).

| Mốc | Giờ | so màn bật | Ghi chú |
|---|---|---|---|
| ACC_OFF · màn tắt · BYD giết Kachi | 20:47:53.6 / 54.56 / 53.93 | −25,4 s | `am_kill … stop com.byd.launcher` [ĐO] |
| Tiến trình A (màn tắt) bật | 20:47:54.37 | | `am_proc_start … activity KachiHome`; nhật ký a11y: `STUCK note=tat-may` |
| A: lượt tự chiếu — 5 `appops get … PICTURE_IN_PICTURE` | 55.83→~56.6 | | |
| A: theme 31 → 16 → 35 | 57.32 / 59.42 / 01.52 | | cách nhau ~2,1 s (executor chiếu) |
| A: chờ VD cụm (`dumpsys display` lặp) | 01.5→09.78 | | ~8,3 s — firmware dựng VD [ĐO] |
| A: ClusterBlack · GMaps trái VERIFIED · VietMap phải VERIFIED · Chữ nhật xong | 10.19 / 14.33 / 17.04 / 17.69 | | chiếu trọn trong lúc màn tắt |
| A: `keys-defer … OP_IN_FLIGHT waited=17969 -> SETTLED` → ESCALATE → RESTARTING | 18.03 / 18.79 | −0,5 s | lượt chữa phím chờ chiếu 18 s rồi tự force-stop |
| Tiến trình B bật | 20:48:19.185 | −0,13 s | |
| B: kênh shell UP (`KachiReady up`) | 20.018 | +0,7 s | |
| B: luồng chính `Skipped 99 frames`, `Davey 2054 ms` | 21.05–21.46 | +1,7 s | |
| B: `home adopt` | 22.044 | +2,7 s | |
| B: ô 0 (YouTube) `tile vd=6` | 23.342 | +4,0 s | |
| B: lượt mở chiếu — PiP ×5, `settings put` ×2, `am stack list` | 22.10→26.87 | | trên kênh chung |
| B: **gỡ cụm của A**: `am start --display 0` Settings · VietMap · GMaps, opcode 18 · 0 | 28.63→34.56 | +9,3→+15,2 s | ~6–7,5 s chỉ để dọn việc của A [ĐO] |
| B: theme 31 → 16 → 35 | 36.11 / 38.38 / 40.61 | +16,8→+21,3 s | |
| BOOT_COMPLETED → `KachiAutostart` | 43.46 | +24,1 s | firmware phát trễ [ĐO] |
| B: chép lại model "Hey Kachi" (xoá + 5 131 KB) | 44.97→45.87 | +25,7 s | mọi lần bật (mục 4.2) |
| B: `TIMEOUT: openProjection` (trần 25 s) | 47.96 | +28,6 s | |
| B: lọc PM2.5 · ghế · lấy gió trong | 48.71–48.78 | +29,4 s | HAL, không dùng kênh shell |
| B: GMaps trái VERIFIED · VietMap phải · Chữ nhật xong | 55.00 / — / 57.29 | **+38,0 s** | cụm xong |
| B: nhạc chuyến (`KachiTrip run … RAN`, `ready after 9559ms`) | 20:49:03.57 | +44,3 s | |
| `:wake` mở mic (AudioRecord start) | 20:49:22.99 | +63,7 s | [SUY] trễ vì bộ nghe bị dựng lại sau lượt chép model |

## 3. Dòng thời gian — lần 2 (21:03–21:05)

Mốc 0 = màn bật `power_screen_state [1…]` 21:04:05.287 (ACC_ON 21:04:03.878).

| Mốc | Giờ | so màn bật | Ghi chú |
|---|---|---|---|
| ACC_OFF · màn tắt · BYD giết Kachi | 21:03:20.75 / 21.24 | −44,5 s | |
| Tiến trình A (màn tắt) bật | 21:03:21.48 | | |
| A: chiếu trọn (31/16/35 → ClusterBlack → trái/phải → Chữ nhật) | 23.04→45.25 | | 22 s |
| A: `keys-defer … waited=18224 -> SETTLED` → RESTARTING | 45.54 / 46.29 | | |
| Tiến trình B (màn tắt, "con của lượt chữa") bật | 21:03:46.65 | | |
| B: gỡ cụm của A (Settings/VietMap/GMaps lên display 0, 18 · 0, dọn cụm) | 49.82→55.10 | | ~5,3 s |
| B: theme 31 → 16 → 35 | 55.42 / 57.50 / 59.55 | | |
| Màn bật | 21:04:05.29 | 0 | |
| B: ANR `Broadcast of … SCREEN_ON` | 21:04:17.39 | +12,1 s | luồng chính kẹt ≈ 16 s (bộ thu chỉ chạy lúc 23.24) — **[CHƯA BIẾT] gốc** |
| B: đặt cụm (wm → ClusterBlack → trái VERIFIED → phải VERIFIED) | 09.67→24.56 | +4,4→+19,3 s | |
| B: `home adopt` · ô 0 `tile vd=9` | 24.75 / 29.80 | +19,5 / +24,5 s | |
| Hệ thống giết B (`user request after error`, hậu ANR) | 21:04:39.29 | +34,0 s | |
| Tiến trình C bật (RebindReceiver, BOOT_COMPLETED) | 21:04:40.03 | +34,7 s | |
| C: `home adopt` · ô 0 `tile vd=10` · YouTube vào ô | 44.52 / 46.11 / 49.30 | +39,2 / +40,8 / +44,0 s | |
| C: chép lại model "Hey Kachi" | 47.19→47.55 | +41,9 s | |
| C: gỡ cụm của B (Settings/VietMap/GMaps d0, 18 · 0) | 47.50→51.73 | | |
| C: theme 31 → 16 → 35 → chờ VD → ClusterBlack → trái → phải → Chữ nhật xong | 53.78→21:05:16.90 | **+71,6 s** | |
| C: nhạc chuyến `NOOP SLOT_NOT_READY` (tries=2) | 21:05:14.54 | +69,3 s | |

## 4. Điểm tranh chấp (định lượng)

1. **Lượt chữa phím tắt-máy chờ lượt tự chiếu, rồi giết nó** [ĐO] — `keys-defer waited=17969 / 18224 ms` (lần 1/2), lần 08:32 cùng ngày 15 433 ms.
   Trước khi có lượt chờ (log 06/10) quyết định leo đến ở t≈5,8 s (`ESCALATE t=5795–5815`, `RESTARTING t=6437–6475`). Cái giá:
   ~22 s chiếu bị vứt + ~5–7,5 s tiến trình con gỡ cụm + một lượt mở chiếu mới; lần 1 lượt mở mới chạm trần 25 s. Năm lần nổ máy
   khác 07/10 (12:30 · 12:38 · 12:51 · 13:16 · 13:53) màn bật GIỮA lượt chờ ⇒ `-> PHASE_GONE`, lớp 2 force-stop launcher **6–11 s sau
   khi màn đã sáng** (`STUCK(mo-xe)->RESTARTING so=+6010…+11054`).
2. **Model "Hey Kachi" chép lại ở MỌI lần bật** [ĐO] — bảng ghim (`WakeModelCatalog`, `keywords.txt` 650 B `d40dff2e…`) ≠ tệp đóng
   trong APK (`app/src/main/assets/voice/kws/keywords.txt`, 348 B `286f7400…`) từ 2.05 (`ce62caf` sửa `voice/kws/` + ghim, không
   chép sang `assets/`). Mỗi lần: xoá gói + chép 5 131 KB (0,36–0,9 s I/O) + `VoiceWakeService.sync(reloadModel = true)`.
3. **5 `appops get … PICTURE_IN_PICTURE` trên kênh shell chung** đúng lúc lượt mở chiếu bắt đầu [ĐO] — 0,18–0,37 s/lệnh
   (21:04:42.571→~43.9 ≈ 1,3 s); `app.revanced.android.apps.maps` trả `exit=-1` (không cài). Ba gói ReVanced [SUY] không cài trên xe này.
4. **Ghi lại cờ freeform mỗi lần** [ĐO] — `settings put global enable_freeform_support 1` + `force_resizable_activities 1` ở mỗi lượt mở
   chiếu (`CastGeometryController.ensureFreeformFlags`) VÀ ở `KachiAutostart` (`freeform seed ensured (wrote=true)`) — 4 lệnh shell / lần bật,
   trong khi giá trị đã là 1.
5. **Luồng chính** [ĐO] — `Skipped 99 frames` + `Davey 2054 ms` lúc dựng HOME (lần 1), `Skipped 223 frames` + `Davey 3968 ms` (lần 2,
   tiến trình B) và ANR SCREEN_ON (luồng chính kẹt ≈ 16 s). Gốc **[CHƯA BIẾT]** — vết ANR đã ghi (`Wrote stack traces to tombstoned`)
   nhưng chưa lấy về.
6. Không phải tranh chấp của Kachi: ~8 s từ opcode 35 tới lúc VD cụm xuất hiện (firmware) · ~2,1 s giữa các opcode (executor chiếu) ·
   BOOT_COMPLETED tới tiến trình B sau 24 s [ĐO]. GC nền 0,1–0,9 s mỗi lượt nhưng `paused` ≤ 20 ms — không chặn luồng chính.

## 5. Đã sửa (2.96 R11, off-car, có test)

| # | Sửa | Tệp | Ước lượng tiết kiệm | Mức |
|---|---|---|---|---|
| 1 | **TAT-MAY-CAST-HOLD**: tiến trình bật lúc màn tắt hoãn lượt TỰ MỞ CHIẾU tới khi lớp 1 kết luận (hoặc giữ tiếp nếu lớp 1 đã bắn force-stop — đọc mốc leo bền), trần cứng 12 s, fail-open. Không đổi lệnh nào của chuỗi chiếu / lượt chữa. Dòng `KachiReady cast-hold … -> WAIT` / `held=… -> GO_*` để đo trên xe. | `core/…/navaccess/TatMayCastHoldPlan.kt` · `app/…/TatMayCastHold.kt` · `A11yLifecycleHeal.install` · `BubbleAutostart.open` | Lớp 1 leo ở ~t=6–7 s thay vì ~24 s (−17–18 s); tiến trình con khỏi gỡ cụm (−5–7,5 s) và khỏi lượt mở chiếu thứ hai chồng lên lúc bật máy. Lần 1 ước: cụm xong ~+5 s thay vì +38 s sau màn bật. Ca màn bật sớm: tránh launcher bị force-stop 6–11 s sau khi màn sáng. | [SUY] — 🚗 cần đo |
| 2 | **WAKE-RECOPY-LOOP**: so `keywords.txt` trên đĩa với bản TRONG APK (nguồn chép thật), luật ghim chỉ còn là đường lùi khi không đọc được asset. Không đổi từ khoá nào đang chạy. | `core/…/voice/WakeKeywordsSync.kt` · `ClusterNavBridgeWake.kt` | −0,36–0,9 s I/O + một lần dựng lại bộ nghe `:wake` mỗi lần nổ máy | [ĐO] gốc · [SUY] phần `:wake` |
| 3 | **PIP-QUERY-INSTALLED**: chặn PiP chỉ hỏi gói ĐÃ CÀI (PackageManager, 0 shell), hỏng thì coi như có cài. | `BubblePipGuard.kt` · `FloatingBubbleService.kt` | −0,5–1,0 s thời gian kênh shell ngay lúc mở chiếu (3 gói ReVanced) | [ĐO] thời gian lệnh · [SUY] số gói vắng |

Test: `TatMayCastHoldPlanTest` · `WakeKeywordsSyncTest` (`:core`) · `StartupContentionR11ContractTest` (`:app`).

## 6. Đề xuất lúc viết (thuộc vùng khác) — trạng thái sau khi gộp 2.96 (cập nhật soát Pass 1, 07/10)

- **Bản keywords nào cho "Hey Kachi"** — ✅ **R16**: owner 07/10 chọn phương án A (bản 2.05) ⇒ `voice/kws/keywords.txt` == `assets/` ==
  ghim (`WakeKeywordsAssetPinTest`); sau khi bỏ một dòng trùng `▁CA ▁CHI` tệp còn **619 B** (không phải 650 B), sha `92739249…`.
  Độ nhạy câu gọi trên âm thật: 🚗 / harness T2 [CHƯA BIẾT].
- **Cờ freeform đọc-rồi-ghi** — ✅ **R13**: bản đầu đọc bằng `settings get` qua shell ⇒ vẫn 4 lượt kênh dadb (chỉ đổi ghi thành
  đọc — số lệnh KHÔNG giảm). Soát Pass 1 [P2] vá: đọc **trong tiến trình** `Settings.Global.getString` (`FreeformSeedStore.readGlobal`,
  tiêm vào cả `FreeformSeedPolicy` lẫn `CastGeometryController` qua `SimpleCastCoordinator`) ⇒ **0 lệnh shell** khi cờ đã là 1; đọc
  hỏng/khoá vắng ⇒ ghi như cũ (`FreeformFlagsReadFirstTest` · `FreeformSeedPolicyTest` R13).
- **ANR SCREEN_ON lần 2** — ✅ **R14**: `AnrDropbox.latestFor` lọc mục của chính gói Kachi từ `dumpsys dropbox --print data_app_anr | tail -c 2M`;
  vào báo cáo `ClusterDiag.capture` + nút riêng ở `DiagActivity`. Gốc ANR vẫn [CHƯA BIẾT] — cần một báo cáo thật từ xe.
- **Nhạc chuyến** `ready after 8–9,5 s`, `SLOT_NOT_READY` ở tiến trình C — ✅ **R9/R10** (`TripMusicPlace.viewWhenReady`, chờ ô sống rồi giao
  lại, tối đa 4 lần; phát tiếp mở toàn màn `--ez force_fullscreen true` [ĐO máy ảo]).
