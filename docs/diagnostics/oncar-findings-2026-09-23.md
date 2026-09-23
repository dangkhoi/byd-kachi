# On-car findings 2026-09-23 — bóng VietMap · HOME restart · voice dẫn đường

> Điều tra trên xe `172.20.10.8` (adb_raw.py, macOS chặn adb LAN) · Kachi **2.14 (vc115)** lúc điều tra ·
> **KHÔNG fix gì trong buổi này** (chỉ finding) · cuối buổi đã cài **2.15 (vc116)** lên xe (Hey Kachi ASR +
> camera xi-nhan, đều mặc định TẮT/ASR). Bằng chứng là log thật, đánh dấu [ĐO]/[SUY].
>
> Topology xe: màn chính = display 0 (1920×1080) · cụm = display 1 `fission_bg_xdja` (1920×720) · ô chiếu app
> = VD `kachi-slot-0` (display 4).

## 1. Bóng VietMap hiện MÀN CHÍNH, không sang cụm — ✅ GỐC RÕ
- **[ĐO]** Cửa sổ bóng `Surface(name=bydAdd-vn.vietmap.live)` ở **display 0**, frame `[1339,100][1710,258]`, containing `[0,0][1920,1080]` (kích thước màn chính).
- **[ĐO]** Kachi VẪN gửi đúng: `VmOverlayPos: gửi VM_BUBBLE_POS x=1339 y=100 → vn.vietmap.live` (cast ON).
- **[ĐO] GỐC**: VietMap trên xe = **3.4.0, KHÔNG phải bản mod-cluster**. `dumpsys package vn.vietmap.live` → **0** receiver `posrx`/`VM_BUBBLE`/`VMBluetoothService` (chỉ receiver gốc). Cài 2026-09-01.
- **Cơ chế**: Kachi chỉ *gửi toạ độ*; **mod** phải nhận broadcast rồi dựng bóng đúng display cụm + gravity/flag fix. Bản đang cài thiếu receiver → broadcast rơi vào hư không, bóng dựng theo cơ chế gốc = màn chính.
- **Hành động**: cài lại **APK VietMap mod-cluster** (bản có `VMBluetoothService$posrx`). KHÔNG phải lỗi code Kachi.

## 2. Về HOME từ app fullscreen → launcher + YouTube-trong-ô restart — ⚠ CHƯA reproduce bằng adb
- **[ĐO]** Thử reproduce (mở YouTube fullscreen → HOME, nhiều lần): PID Kachi (20210) + YouTube (3365) **KHÔNG đổi**, VD ô `kachi-slot-0` **KHÔNG bị gỡ/tạo lại**, không crash/ANR/proc-died. `configChanges` khai đủ (orientation|screenSize|screenLayout|density|smallestScreenSize) nên config change thường không recreate.
- **[SUY] Nghi phạm code**: `KachiHomeActivity.render()` gọi `recreate()` khi `ThemeHost.sync(state)` trả true (dòng 352) hoặc `LangHost.changed`. `sync`→`applyTheme` trả true khi palette đổi: (a) đổi ngày↔đêm khi `themeMode=AUTO`, (b) `ColorChoice.FROM_ART` (màu nhấn theo ảnh nền) mà `WallArtStore.accentDominant()` trả khác giữa các lần đọc. `uiMode` (night) KHÔNG có trong configChanges ⇒ hệ đổi ngày/đêm cũng ép recreate. `recreate()` = launcher dựng lại + ô YouTube recreate theo.
- **Cần thêm**: owner cho biết themeMode (Theo xe/Sáng/Tối) + màu nhấn có theo ảnh nền không; và thao tác đúng kịch bản (app fullscreen loại gì, nút Home nào) trong lúc bắt live log.

## 3. Voice dẫn đường sai — ✅ 3 GỐC RÕ
### 3a. "dẫn đến công ty 1" (ô đã lưu) → GMaps, không tra sổ
- **[ĐO]** log: `START Intent { VIEW dat=google.navigation:q=công ty một pkg=com.google.android.apps.maps }`.
- **Nhãn lưu**: owner xác nhận là **"Công ty 1"** (số) → `norm` = `["cong","ty","1"]`. ASR nghe "công ty **một**" → `["cong","ty","mot"]`.
- **[SUY từ `VoicePlaces.match`]** GỐC: match khớp token CHÍNH XÁC (`norm(nhãn)==các_từ`), **không chuẩn hoá chữ số↔chữ đọc** ("1"↔"một"), **không fuzzy**. → miss → rơi ra điểm-đến-mở → GMaps.

### 3b. "67 hoàng văn thái" → GMaps `q=sáu bảy hồ văn thái`
- **[ĐO]** log: `google.navigation:q=sáu bảy hồ văn thái`.
- **Hai lỗi cộng dồn**: (1) "67" ASR ra "**sáu bảy**" và **gửi nguyên chữ** ra GMaps (không gộp/chuyển về "67") — cùng họ 3a, chiều **chữ→số**; (2) "hoàng" nghe hụt thành "**hồ**" (mất âm cuối) — giới hạn model zipformer-vi, backlog **ASR-VI-TONE**.

### 3c. Phản hồi CHẬM hơn version trước
- **[ĐO]** mọi phiên: `duong=vad ... cua_so=5000ms cat_con=5000ms` — VAD nghe **hết trần 5 giây**, không chốt endpoint sớm khi người dừng nói. `mic mở` nhanh (53–77ms) nhưng chờ đủ 5s window mới giải mã.
- **[SUY]** Version trước dùng `VoiceEndpointer` RMS (endpoint ~600ms) chốt câu sớm bằng năng lượng; bản này đường VAD không chốt sớm ⇒ luôn ăn đủ 5s ⇒ chậm. Nghi phạm cho "trước nhanh, giờ chậm".
- **Cần đo thêm**: 1 phiên đọc log `KachiVoiceTiming` NGAY (buffer bị `radar_267_status_set`/`mcu_log` spam cuộn rất nhanh) để xem có `tieng_dut`/endpoint không.

## Việc off-car (lát làm)
1. **VoicePlaces**: chuẩn hoá **chữ số ↔ chữ đọc** cả 2 chiều (dùng `VoiceLexicon` bảng số VI) trước khi so token, cho cả tra sổ (3a) lẫn địa chỉ tự do (3b số nhà); + **fuzzy** (`VoicePhoneticMatch`) sau khi khớp chính xác thất bại — như các câu lệnh khác đã có.
2. **VAD endpoint** (3c): chốt câu sớm khi hết tiếng (khôi phục hành vi RMS-endpoint bản trước), thay vì ăn hết trần 5s.
3. **ASR-VI-TONE** (3b "hoàng"→"hồ"): giới hạn model — việc lớn, để riêng.

## Ghi chú
- Camera 360 của xe ĐANG chạy (log `AVMCamera`/`NormalCamClient`/`radar_267_status_set`) — nguồn tham chiếu khi test camera-xi-nhan của 2.15 trên xe.

## 4. Hey Kachi ASR (2.15) — nói 10 lần lên 1 — ✅ GỐC RÕ (khớp mờ chưa đủ rộng)
- **[ĐO] Engine ASR CHẠY THẬT** trong `:wake` (pid 25502): `KachiVoiceRec: sherpa ra: ...` — mô hình tiếng Việt nạp được, KHÔNG phải KWS-null. Wiring OK.
- **[ĐO] Chuỗi ASR khi nói "Hey Kachi"** (cùng 1 lần, cửa sổ cuộn lớn dần):
  - `"hay ca"` → trượt · `"hay kach hai"` → trượt · `"hai kach hai kach"` → trượt · `"chi hai ca chi"` → **WAKE khớp** (có "ca"+"chi" liền).
- **GỐC** (`WakeAsrMatcher`): model tách "kachi" thành mảnh **"kach" / "ca" / "chi"** và chèn "hai"/"hay" vào giữa. Matcher hiện tại trượt vì:
  1. **HEAD set thiếu "kach"** (mảnh model hay ra nhất) — chỉ có ka/ca/ga/co/cu/kha/gha/cha.
  2. Đòi HEAD **liền ngay trước** TAIL; model chèn từ vào giữa ("kach **hai**", "hai **kach**") ⇒ cặp gián đoạn.
  3. `oneWordKachi` đòi độ dài 4–6 + endsWith chi/chy/ti ⇒ "kach" (endsWith "ch") trượt.
- **Hướng sửa off-car**: (a) thêm "kach"/"kact"/"cach" vào HEAD + coi "kach"/"kachi" là 1-từ-khớp; (b) nới cặp HEAD…TAIL cho phép ≤1 từ đệm ở giữa (hoặc: chỉ cần TEXT chứa CẢ một HEAD và một TAIL trong ≤MAX_WORDS, không cần liền kề); (c) cân nhắc chỉ cần thấy "kach"/"kachi" là đủ. ⚠ Nới rộng phải đo lại false-accept (mục B2 runbook).

## 5. Voice LOOP + phiên lệnh "đang nghe mà không hiểu" (2.15) — ✅ GỐC RÕ
- **[ĐO] HAI tiến trình cùng nghe mic**: launcher (pid 25333, phiên lệnh) và `:wake` (pid 25502) nghe SONG SONG.
- **[ĐO]** owner nói "năm mươi phần trăm kính lái" cho phiên lệnh → 25333 nghe ĐÚNG; NHƯNG `:wake` (25502) cùng lúc ra "tính nào năm mươi phần trăm...", "năm mươi phần trăm là hai" (= nghe LẠI câu phiên lệnh).
- **[ĐO]** `KachiHomeActivity (has extras)` START **3 lần trong ~80s** (09:52:46 · 09:53:22 · 09:54:07) từ uid launcher = 3 lần **fireWake**.
- **[ĐO]** phiên lệnh nhiều lượt `hết trần ... doan=0 ... bỏ giải mã: không có tiếng` (cua_so 8200ms) = mic mở mà không nghe được gì → *"đang nghe mà không hiểu"*.
- **GỐC**: khớp mờ ASR-window **false-accept cao** — câu thường có "ca"/"chi"/"kach" bị khớp → fireWake → mở phiên lệnh → `:wake` nghe LẠI câu đó → khớp lần nữa → **LOOP**. `:wake` không NHƯỜNG mic thật khi phiên lệnh chạy. Đây là mặt trái cố hữu của chạy ASR liên tục trên MỌI tiếng + khớp mờ rộng.
- **Overlay không hiện mà có tiếng**: [SUY] fireWake mở-đóng phiên nhanh do loop nên overlay không kịp/bị đè; bíp vẫn phát.

## 6. Camera theo xi-nhan (2.15) — bật rồi xi-nhan trái KHÔNG hiện gì — ✅ GỐC RÕ (lỗi wiring scaffold)
- **[ĐO]** Camera của xe TỒN TẠI + gọi được: 2 camera framework (Facing Back), HAL `android.hardware.bydauto.panorama.IBYDAutoPanoService` **phản hồi** `service call` từ uid shell (tx1-7 getter trả Parcel; set-method cần HIDL token nên `service call` thô không gọi được). 3 app cam BYD (`cameramanager`/`auto_camera`/`bydcamera`). Sensor name theo vị trí ([ĐO] `mm-camera CAMERA_SENSOR_NAME:rear` khi cam sau stream).
- **[ĐO]** Owner bật feature + xi-nhan trái: MCU log `turn light sound val 1↔2` (xi-nhan ĐANG active), NHƯNG **0 dòng** `KachiCamera`/`PanoramaHal`/`xi-nhan` — controller KHÔNG mở camera.
- **[ĐO]** Kachi (pid 20210/25333) **KHÔNG đọc datum light nào** (grep light từ pid Kachi rỗng).
- **GỐC**: `CameraSignalController.tick` đọc `carStatus.lights.leftTurn/rightTurn`, nhưng `light_left_turn`/`light_right_turn` **KHÔNG được poll** — `CarDataDemand.controlsOf` chỉ poll datum/nút **ĐANG HIỆN trên màn**; xi-nhan không phải widget đang hiện ⇒ không vào demand ⇒ `lights.leftTurn` **luôn null** ⇒ `turnOf(false,false)=NONE` ⇒ không mở camera. **Lỗi thiết kế scaffold đêm qua**: wire controller đọc `carStatus.lights` mà quên lights không được poll (chỉ poll cái đang hiện).
- **Chưa trả lời được "Kachi mở camera lên màn được không"** vì controller chưa bao giờ chạy tới `PanoramaHal.open` (kẹt ở tầng đọc xi-nhan). HAL panorama thì gọi được (service sống).
- **Hướng sửa off-car**: (a) `CameraSignalController` phải **tự đọc datum xi-nhan trực tiếp** (đăng ký `light_left_turn`/`light_right_turn` vào demand qua `CarDataDemand.withDemand`, HOẶC gọi thẳng `BYDAutoLightDevice.getLightStatus` qua `BydHalGateway.getter` mỗi tick) — KHÔNG dựa vào `carStatus.lights` (vốn null vì không hiện); (b) sau khi controller chạy tới `open()`, mới đo được `PanoramaHal` rc + overlay + tín hiệu LVDS (runbook A5 option A–J).
0. **WakeAsrMatcher CÂN BẰNG** (mục 4 + 5) — mâu thuẫn: mục 4 cần NỚI (nghe được "kachi"), mục 5 cần SIẾT (chống false-accept/loop). Hướng: (a) đòi CẢ head+tail của "kachi" trong cụm ngắn (không khớp "ca"/"chi" lẻ); (b) `:wake` NGƯNG nghe thật khi phiên lệnh mở (chặn nghe-lại = nguồn loop); (c) thêm gate tin-cậy trước fireWake; (d) đo lại false-accept sau mỗi lần nới.

## CAMERA — phương án A (owner chốt 2026-09-23)
Off-car làm ngay, KHÔNG mò: (1) sửa `CameraSignalController` đọc xi-nhan ĐÚNG nguồn — gọi thẳng `BYDAutoLightDevice.getLightStatus` qua `BydHalGateway.getter` mỗi tick (KHÔNG dựa `carStatus.lights` vốn null vì không được poll); (2) thêm lệnh test-bridge `camera left/right/off` để lên xe test KHÔNG cần bật xi-nhan thật + thử nhanh 10 option LVDS; (3) build 2.16 → owner cài → lên xe chạy test lấy kết quả.
Bắt buộc cần xe (giới hạn vật lý): HAL panorama có cho uid app thường (Kachi 10138) gọi không — [ĐO] `com.byd.cameramanager` = uid system 1000; LVDS có đổ vào SurfaceView app thường không (runbook A5 option A–J). Kinex proven app-thường ⇒ khả năng cao được.

## 7. Command LẤY GIÓ (recirc) — ✅ CONFIRM ĐÚNG cả 2 chiều (2026-09-23, qua test-bridge say + auto_confirm)
- `recirc` = feature-id **501219355** (`BYDAutoAcDevice`), đọc `getAcCycleMode`. INLOOP=1 (trong) / OUTLOOP=0 (ngoài).
- **[ĐO]** "lấy gió ngoài" → parse `Tắt Lấy gió trong` → `AirConditioningService onAcCycleModeChanged mode=0` → đọc lại `getAcCycleMode=0` ✅
- **[ĐO]** "lấy gió trong" → parse `Bật Lấy gió trong` → `onAcCycleModeChanged mode=1` → đọc lại `=1` ✅ (app điều hoà BYD `CycleModeButton` cũng đổi theo).
- **Kết luận**: command recirc + giá trị INLOOP/OUTLOOP ĐÚNG, xe chuyển thật cả 2 chiều.

## 8. PM2.5 auto-filter — an toàn + chốt hành vi (2026-09-23)
- **[ĐO] KHÔNG đụng recirc/lấy gió**: `Pm25FilterApplier` chỉ gọi `setAutoCleanAirState`/`setQuickCleanAirState`/`enablePurificationFunctionPrompt` trên `BYDAutoPM2p5Device` (device 1008). Recirc là `BYDAutoAcDevice` feat 501219355 — device khác, không liên quan.
- **[ĐO]** hiện bụi level 1 (Excellent), `setAutoCleanAirState(1) rc=0`, KHÔNG bắn quick-clean (< ngưỡng HEAVY=5). Ngưỡng lọc-ngay = HEAVY(5)/thang 1–6.
- **RỦI RO**: nếu bụi ≥5 kéo dài, poll 45s bắn `setQuickCleanAirState(1)` **mỗi 45s liên tục** = lọc liên tục/chồng lệnh.
- **CHỐT (owner 2026-09-23, off-car)**: giữ poll ĐỌC mức mỗi **45s**; khi ĐÃ bắn lọc-ngay thì **COOLDOWN 10 phút** — trong 10p đó vẫn đọc mức 45s/lần nhưng KHÔNG bắn quick-clean lại; hết 10p, nếu vẫn bẩn thì lọc lại. Cần thêm state `lastQuickCleanAt` trong `Pm25FilterApplier` + chặn `setQuickCleanAirState` khi `now - lastQuickCleanAt < 10p`.

## 9. VOICE UX kém (owner 2026-09-23) — nhiều điểm, làm off-car
Owner mô tả:
1. **Start không có tín hiệu READY**: không lời chào / không bíp báo "được nói rồi". Cần: bíp/earcon (hoặc "Kachi nghe đây") NGAY khi mic mở.
2. **Phản hồi CHẬM**: text overlay hiện trước, RẤT lâu mới nghe voice feedback. (Liên quan mục 3c — VAD nghe hết trần 5s không chốt sớm + TTS trễ.)
3. **Overlay chập chờn**: lúc còn lúc mất mà vẫn nghe tiếng → trạng thái không khớp âm thanh.
4. **Overlay không giống UI trợ lý AI**: cần **hiển thị tần số/waveform nhảy** khi đang nghe (biết máy đang nghe), feedback đều.
5. **Nói 2-3 lượt bị VĂNG RA**, không biết còn nghe không (overlay mất) — phiên hội thoại chết ngầm.
6. **Vào conversation là LOOP** — hỏi lại rồi loop.
- **Cần**: bổ sung **golden dataset CONVERSATION** (đa lượt): vd "mở kính" → "mở kính nào ạ?" → "kính lái nhé" → "ok đã mở kính lái". Để test off-car luồng hỏi-lại nhiều lượt không loop/không chết.
- Việc off-car: (a) earcon READY khi mic mở; (b) overlay waveform/tần số + trạng thái đồng bộ âm thanh; (c) overlay SỐNG suốt phiên hội thoại, báo rõ "đang nghe/đang xử lý/đã xong"; (d) chốt endpoint sớm (3c) để feedback nhanh; (e) sửa loop conversation + golden multi-turn.

## 10. Voice endpoint chậm (mục 3c) — phân tích: chỉnh VAD threshold TRÊN XE
- **[ĐO log]** phiên chốt SỚM OK khi VAD thấy đoạn (vd "50% kính lái": tieng_dut=2600ms cua_so=3200ms). Phiên CHẬM là `doan=0` (VAD không tạo đoạn) → chạy hết trần MAX_LISTEN_MS=8s rồi flush.
- **GỐC**: VAD `THRESHOLD=0.5` (điểm lưới Silero mặc định) quá cao cho mic xe / giọng nhỏ ⇒ nhiều câu không tạo đoạn ⇒ hết trần.
- **KHÔNG đổi default mù** (hạ threshold dễ false-accept trong cabin ồn — đúng cảnh báo mục 3c). Pref `voice_vad_threshold` ĐÃ CÓ (chỉnh trên xe không cần build).
- **On-car**: chỉnh `voice_vad_threshold` 0.5 → 0.40 → 0.35, đo hit (chốt sớm) vs false (cắt lời). Chốt giá trị tốt rồi mới đổi default. `MIN_SILENCE_MS=600` có thể hạ 450 nếu chốt còn chậm.

## ✅ ĐÃ SỬA OFF-CAR (build Kachi-2.17 vc118, feat branch, CHƯA OTA) — 2026-09-23
- Voice UX (2.16): waveform vòng tròn (VoiceWaveView) + earcon 3-tông (VoiceChime) + overlay GIỮA-DƯỚI bám phase + VAD threshold 0.5→0.35 + golden conversation test.
- **Mục 3a/3b số↔chữ**: VoiceNumberNorm (:core) — "công ty một"↔"Công ty 1" (VoicePlaces.match chuẩn 2 vế), "sáu bảy hồ văn thái"→"67 hồ văn thái" (runNav normalizeSpokenNumbers). Test 6 ca.
- **Mục 6 camera**: CameraSignalController.tick() TỰ đọc xi-nhan qua getLightStatus(4/5) — hết phụ thuộc carStatus.lights null.
- **Mục 8 PM2.5**: cooldown 10 phút sau mỗi lần lọc tự động; nút Lọc ngay giữ chủ động.
- **R7**: wake mở overlay ĐỘC LẬP (voiceSession riêng ở VoiceWakeService), KHÔNG kéo KachiHomeActivity lên đè app.
- CÒN cần xe: earcon nghe rõ + waveform mượt + overlay-từ-service không bị ROM chặn + camera LVDS-vào-surface (runbook A5) + Hey Kachi matcher (chờ golden dataset thu on-car) + FQN BYDAutoLightDevice verify.

## 11. Bind phím vô-lăng vẫn tạch + "Sửa ngay" không ăn (team báo 2026-09-23) — SỬA MỘT PHẦN
- **GỐC [ĐO trước, backlog BIND-SELFHEAL]**: dưới CPU load cao (>10), chuỗi toggle rebind qua dadb có NHIỀU round-trip (remove → sleep máy chủ → re-add → enable → verify) — dadb chậm giữa các lượt ⇒ bị cắt GIỮA toggle / vượt timeout 20s ⇒ rebind thất bại. [ĐO cũ] toggle a11y TRỰC TIẾP (settings, không dadb) bind lại NGAY cả khi load 14.
- **SỬA (2.20, off-car [SUY])**: gộp remove + sleep + re-add + enable thành **MỘT lệnh shell** chạy trên xe (`NavConnect.forceRebindIfNeeded`) — 1 round-trip dadb thay 4. Nếu lệnh lọt vào xe thì cả chuỗi chạy trên xe bất kể client đọc timeout ⇒ hết cửa "chỉ remove landed" + giảm điểm treo dưới load.
- **⚠ CHƯA verify trên xe** (xe offline). Nếu ROOT là dadb KHÔNG establish nổi dưới load (không chỉ chậm giữa lệnh) thì gộp round-trip giúp NHƯNG có thể chưa đủ.
- **"Sửa ngay" không ăn** có thể là ROOT KHÁC — cần log on-car khi bấm: `grantAccessibility TIMEOUT` (dadb treo) hay `force-rebind xong bound=false` (toggle không bind). Chạy runbook §D + đọc log `NavConnect`/`Preflight` khi bấm Sửa ngay.

## 12. ON-CAR 2.20 test (2026-09-23 15:49, ~20 phút) — kết quả
- **Bind phím: ✅ ĂN** — owner xác nhận phím vô-lăng bấm ăn. [ĐO] `dumpsys accessibility`: NavAccessibilityService BOUND, capabilities=9 (=RETRIEVE_WINDOW_CONTENT + FILTER_KEY_EVENTS). Lúc load 16 chưa bound → sau bound (fix 2.20 + load hạ). "Đang hoạt động" = thật. Overlay xấu = để sau.
- **Voice "công ty 1": ✅** — ASR nghe "công ty 1" (ĐÚNG số), ra GMaps vì sổ chưa lưu mục đó (không phải bug — owner OK).
- **Giữ state theme (#10): ✅ KHÔNG RESTART** — [ĐO] PID Kachi 10832→10832 (không đổi) qua đổi Sáng→Tối. NHƯNG restyle THIẾU: chỉ ô dock TẮT + thanh trên đổi tối; CÒN SÁNG: (1) khay dock (nền tray DockAreaLayout), (2) nền tổng WallView (gradient sáng — nghi hình nền ảnh hoặc wall không đọc palette), (3) khe + dải khung ô (WorkspaceView.makeSlot "chỗ thứ 27"). → off-car mở rộng applyThemeInPlace phủ hết. Bonus: tone ACTIVE bảng tối chữ trắng 1.8:1 (dưới ngưỡng) — cùng họ lỗi cũ.
- **Golden Hey Kachi: ✅ THU ĐƯỢC** → `scripts/voice/data/wake-golden-oncar-2026-09-23.txt` (39 dòng). ASR "Hey Kachi" ra: "hay kach hay kach", "cá chì", "hay ca chê", "các chị ơi", "kacha cá ok"... Model ra mảnh "kach" NHIỀU (matcher thiếu) + chèn "hay/hai" giữa → cặp head+tail gián đoạn. Câu thường KHÔNG khớp (tốt). → off-car chỉnh WakeAsrMatcher: thêm "kach"/"cha", cho từ đệm giữa head-tail, "cá chì"/"cách"/"các chị" là biến thể.

## 13. NGHE LOG PHIÊN LÁI (2026-09-23 18:30, 2.20, không action gì vào xe)
Load 15–18.7 (đang lái). Không crash/ANR Kachi. 3 finding:
- **a11y BIND RỚT dưới load cao**: [ĐO] `NavConnect: accessibility force-rebind xong: bound=FALSE` (18:31:49, load 18.68). Watchdog REBIND_WATCHDOG chạy đều, toggle CHẠY nhưng hệ **KHÔNG bind lại** ⇒ phím vô-lăng chết lúc này. Fix 2.20 (gộp lệnh) giảm round-trip NHƯNG root sâu hơn = **dưới load cực cao hệ không bind service** (không phải dadb treo — lệnh xong, đọc bound=false). Hướng: (a) chờ+retry nhiều nhịp khi bound=false; (b) GIẢM TẢI CPU (xem dưới) để hệ bind kịp.
- **`:wake` (Hey Kachi ASR) ngốn 39% CPU** [ĐO dumpsys cpuinfo]: `39% com.byd.launcher:wake`. [ĐO log] `KachiVoiceRec: sherpa ra` liên tục mỗi ~1.5–2s ("ừm"/"ờ"/câu nền) — decode model 74MB 2 lần/giây trong cabin ồn. Vượt xa ngưỡng runbook B3 (<15%). Góp phần load 18 + có thể làm a11y bind rớt (hệ đói CPU). **Hướng off-car: tăng DECODE_EVERY_MS 500→1000+ · thêm RMS-gate (chỉ decode khi có tiếng đủ to) · duty-cycle.** ⚠ Đây là mặt trái ASR-window đã lường; cần siết.
- **surfaceflinger 76% + com.byd.cdr 42%** — compositing 3 display + recorder BYD (không phải Kachi; đã biết).

## 14. GOLDEN Hey Kachi MẺ 2 (2026-09-23 19:35, đậu xe) → scripts/voice/data/wake-golden-oncar-2026-09-23-drive.txt
24 dòng. ASR "Hey Kachi" ra biến thể MỚI mà WakeAsrMatcher hiện MISS (chỉ 1 WAKE khớp "cay hay ca chí"):
- **"kat"/"katy"**: "hay kat hay kat", "kach hay kat", "ke hay katy hay" — TAIL/HEAD chưa có "kat"/"katy".
- **"cay"/"ky"**: "cay hay ca chí", "hay cay ca", "ok cay ky ca" — "cay" (ca+y), "ky".
- **"cá chí"/"ca chế"**: "hay ca chế", "ca sĩ cá" — "chí"→chi, "chế"→che (đã có).
- **"các chị"/"cả chị"**: "các chị hay", "cả chị à cả chị ơ", "các chị ơi ok".
- → off-car chỉnh matcher: thêm "kat"/"kach"/"katy" (kach+t), "cay"/"ky" HEAD, "cả chị"; đo lại trên CẢ 2 mẻ golden (39+24 dòng). ⚠ cân bằng false-accept ("ca sĩ", "cay" có thể trong câu thường).
