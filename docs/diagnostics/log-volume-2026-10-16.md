# Lượng log `usage-*.log` lúc đỉnh — LOG-VOLUME (3.1.10, 16/10)

> Owner 16/10: *"xử hết mọi thứ có thể"* — log Kachi ghi ra thẻ nhảy 11–58 KB/phút, có phút > 100.
> Mức bằng chứng: **[ĐO]** đo/đọc hôm nay · **[SUY]** suy từ mã + số đo · **[ĐOÁN]** chưa kiểm · **[CHƯA BIẾT]**.
> Backlog: `LOG-VOLUME-SPIKES`. Mã: `core …/launcher/SessionLogGate.kt` (mới) · `app …/launcher/KachiLog.kt` (nối) ·
> 3 dòng nav hạ mức D (`NavigationHudOwner`, `NavNotificationListener`, `ClusterBroadcaster`).

## 1. Dữ liệu

- Logcat liên tục của 3 chuyến (lọc đúng pid tiến trình chính Kachi, đổi sang `logcat -v time` — đúng thứ
  `KachiLog.startCapture` đọc): 16/10 sáng (46 phút, 3.1.9) · 15/10 tối (52 phút) · 15/10 sáng (39 phút) [ĐO].
- Tệp `usage-*.log` của chính xe 16/10 (sau bộ tiết chế 3.1.9) — dùng để xếp hạng và đối chiếu: phát lại logcat thô
  qua luật 3.1.9 ra 09:52 = 99,7 KB/phút, tệp thật ghi 112 (tệp có thêm 2 phút đầu trước khi logcat liên tục bắt đầu) [ĐO].
- `KachiPerf log=` của phiên 16/10: 47 cửa sổ, min 7,0 · trung vị 30,6 · max 111,3 KB/phút [ĐO].

## 2. Ai chiếm byte trong phút đỉnh [ĐO, tệp usage 16/10, 7 phút đỉnh, 575 KB]

| Nguồn (mức/tag) | KB | dòng | Vì sao bộ tiết chế nguyên văn không chặn |
|---|---|---|---|
| `I/NavigationHudOwner` `cluster-nav … seg=…` | 97,8 | 245 | dòng ~400 B, `seg` đếm lùi mỗi giây ⇒ mỗi dòng một chuỗi mới |
| `D/AbsBYDAutoDevice` `set featureID is 43f0… intValue is N` | 59,8 | 650 | thư viện BYD log mỗi lần Kachi đẩy khung dẫn đường lên cụm; cự ly đổi liên tục |
| `D/Car3d` `đợt vẽ N khung (tổng N) · … µs/khung` | 55,0 | 528 | số khung + µs đổi mỗi đợt |
| `E/FrameEvents` `updateAcquireFence: Did not find frame.` | 41,9 | 530 | Y NGUYÊN, nhưng luật cũ cho W/E đi thẳng |
| `D/BYDAutoInstrumentDevice` `sendSimpleGuidanceInfo … is N` | 35,1 | 278 | cự ly dẫn đường |
| `I/NavListener` `nav dist='N m' …` | 30,6 | 232 | cự ly / ETA |
| `I/ClusterBroadcaster` `emit lane … road='<cửa sổ chạy chữ>'` | 28,9 | 269 | dòng log in CỬA SỔ chạy chữ (đổi mỗi 700 ms) dù khoá log là tên đầy đủ |
| `I/om.byd.launche` GC của ART | 29,4 | 158 | số byte giải phóng mỗi lượt khác nhau |
| `D/BYDAutoSpeedDevice` `getCurrentSpeed: speed is N.N` | 16,3 | 203 | tốc độ |
| camera (`KachiCamera` · `C-JNIBMMCamera` · `AVMCamera`) | ~50 | ~450 | sự kiện mở/đóng camera xi-nhan — đúng là sự kiện, giữ |

Ba họ gốc [ĐO + SUY từ mã]: (1) **số đo đổi liên tục** (cự ly/tốc độ/µs) — mỗi dòng là chuỗi mới; (2) **nhịp tim 10 s**
của mọi câu lặp y nguyên (sàn ≈ 6 dòng/phút × vài chục câu thư viện HAL); (3) **W/E lặp y nguyên** đi thẳng.

## 3. Bản sửa — một cổng, ở đúng chỗ ghi duy nhất (`SessionLogGate`)

Theo **mức log**, không theo tag/tên gói:

| Mức | Luật | So với 3.1.9 |
|---|---|---|
| F/A | luôn ghi | như cũ |
| I | nguyên văn, 10 s, `[+N lặp]` | **không đổi** — `KachiCtl` ctl/ctl-after, `KachiHalEvent`, `KachiHalSignal`, `KachiKey`, WAKE, cốp… y hành vi cũ (bài `muc I giu nguyen hanh vi cu`) |
| W/E | nguyên văn, 10 s: bản đầu ghi + xả ngay, bản sao thành số đếm | mới — `FrameEvents` ~500 bản/chuyến thành vài chục dòng có đếm |
| D/V | khoá **khuôn** (mọi số đứng riêng → `#`, giữ token chữ-số như `43f01018`/`0x0950000F`): y hệt dòng ghi cuối ⇒ 60 s; chỉ khác **số đo** (thập phân hoặc 2–7 chữ số) ⇒ ≤ 1 dòng/10 s, ghi giá trị hiện tại kèm `[trước đó: +M tương tự]`; khác chỗ khác (số 1 chữ số = trạng thái, ≥ 8 chữ số = id, chữ) ⇒ ghi ngay | đổi trạng thái kể cả **quay về giá trị cũ** (A → B → A) nay luôn hiện — lỗ của bộ nguyên văn đã đóng ở mức D |

- **Vòng poll nhiều danh tính** (≥ 3 thân khác nhau cùng khuôn ghi trong 10 s, vd `getDoorState area is: 1..6`): thân
  đã ghi trong 10 s bị gộp — đúng bằng luật 3.1.9, không tệ hơn. Hai thân luân phiên (A ↔ B) vẫn ghi (có thể là lật thật).
- **Vết gọi nhiều dòng** [ĐO tệp usage 09-17 → 10-06]: `logcat -v time` in đủ đầu dòng trên MỌI dòng `\tat …` — KDoc cũ
  của `KachiLog.throttled` ("dòng phụ không mốc") sai ⇒ dòng vết gọi của I/D đã đi qua bộ tiết chế từng dòng. Nay: dòng
  cùng đầu dòng sau W/E/F/A, hoặc bắt đầu `\t`/`Caused by:`, là dòng tiếp ⇒ luôn ghi, dòng vừa giữ của cùng bản ghi
  được thả ra trước (đúng thứ tự, số đếm hoàn lại).
- **Hạ mức ở nguồn** (3 dòng nav đã log-khi-đổi, nhưng `seg`/cự ly đổi mỗi giây): `Log.i` → `Log.d`; `ClusterBroadcaster`
  in tên đường đầy đủ thay cho cửa sổ chạy chữ (cửa sổ là hàm tất định của tên + thời gian).
- **Chi phí đường nóng** [SUY từ mã]: O(độ dài dòng) như cũ (+1 lượt quét ký tự dựng khuôn cho dòng D, +1 khi thân đổi),
  vòng 8 thân/khuôn, 2 bảng LRU trần 512. Không luồng, không hẹn giờ. Bộ nhớ ước [ĐO đếm trên log 16/10]: ~300 khuôn D,
  vòng ~45 K ký tự.

## 4. Trước / sau — phát lại log thật qua cổng trong JVM [ĐO off-car]

Fixture `core/src/test/resources/log/session-log-spikes-2026-10-16.log.gz` (4 cửa sổ 3 phút quanh phút đỉnh, ẩn danh:
chữ trong nháy → từ giả ổn định, `bufferDataValue` → dãy giả, IPv4 → `10.0.0.N`); `SessionLogGateReplayTest` bỏ phút
khởi động của cổng ở cả hai bên. "Sau" = cổng + 3 dòng nav hạ mức (fixture là log 3.1.9 nên bài đổi mức đúng 3 tag ấy;
`ClusterBroadcaster` trong fixture vẫn mang cửa sổ chạy chữ ⇒ "sau" là cận trên).

| Cửa sổ | Trước (3.1.9) | Chỉ cổng | Cổng + hạ mức nav |
|---|---|---|---|
| 16/10 09:53–09:55 | 59,6 | 41,2 | **28,2** |
| 16/10 10:02–10:04 | 63,6 | 40,7 | **34,2** |
| 15/10 18:23–18:25 | 82,9 | 55,0 | **44,8** |
| 15/10 07:56–07:58 | 82,8 | 46,2 | **40,3** |
| TB 4 đỉnh | 72,2 | — | **36,9** (−49 %) |

Cả chuyến (logcat liên tục, phát lại một lần qua bài tạm, cùng mã cổng) [ĐO], KB/phút:

| Chuyến | Trước TB · max | Sau TB · max | 5 phút đỉnh (trước → sau) |
|---|---|---|---|
| 16/10 sáng, 46′ | 37,3 · 99,7 | **21,0 · 52,5** | 99,7→52,5 · 85,4→38,1 · 70,3→36,8 · 69,3→31,4 · 67,8→38,2 |
| 15/10 tối, 52′ | 52,9 · 114,7 | **29,9 · 58,2** | 114,7→58,2 · 108,0→51,9 · 103,2→53,7 · 99,3→46,9 · 93,0→50,3 |
| 15/10 sáng, 39′ | 55,9 · 122,2 | **29,0 · 55,3** | 122,2→52,8 · 118,0→55,3 · 110,9→47,7 · 93,9→41,4 · 83,5→44,0 |

Giữ thông tin [ĐO, bài trên fixture]: mọi câu chữ khác nhau mức I/W/E/F/A đều còn ≥ 1 lần
(`khong mat cau chu nao o muc I W E F A`); mọi đổi trạng thái mức D mà chuỗi chưa xuất hiện trong 10 s đều được ghi
(`moi doi trang thai muc D deu duoc ghi`). Đỏ khi gỡ: thay cổng bằng luật 3.1.9 ⇒ "sau" = "trước" = 72,2 ⇒ bài trượt [ĐO].

Trên xe: **[CHƯA ĐO]** — chuyến sau đọc `KachiPerf log=` (mục tiêu K5 < 20 KB/phút lúc yên; lúc dẫn đường + camera kỳ
vọng ≈ ½ con số 3.1.9).

## 5. Còn lại sau cổng (top theo tag, cửa sổ đỉnh) — nguồn KHÔNG sửa ở phiên này

| Nguồn | KB/2 phút | Ghi chú |
|---|---|---|
| `D/BYDAutoLightDevice` · `…BodyworkDevice` · `…TyreDevice` · `…SettingDevice` · `…AcDevice` | 3–9 mỗi tag | thư viện BYD log MỖI getter; nhịp do poll trong `CarDataAdapter` (ngoài phạm vi phiên này). Vòng poll nhiều danh tính đã về sàn 3.1.9 (mỗi danh tính 1 dòng/10 s). Giảm tiếp chỉ bằng bớt poll (HAL theo sự kiện) [SUY] |
| `D/AbsBYDAutoDevice` `set featureID 43f0… ` | ~6–7 | mỗi feature dẫn đường ≤ 1 dòng/10 s; nguồn là nhịp ghi khung cụm |
| `I/om.byd.launche` GC | 4–9 | số lượt GC = cường độ cấp phát (perf), không phải lỗi log; giữ |
| `I/KachiCamera` · `I/OpenGLRenderer Davey!` | 2–6 | sự kiện thật (camera xi-nhan, khung trễ) — giữ |
| `E/FrameEvents updateAcquireFence` | — (đã gộp) | dòng framework (libgui) trên một `Surface` của tiến trình ta; bề mặt nào sinh [CHƯA BIẾT] — nghi camera GL / 3D [ĐOÁN] |
| `D/Car3d đợt vẽ …` · `I/Car3d dots …` | đã gộp / nhỏ | `Car3dWidgetView` (ngoài phạm vi phiên này) |

## 6. Giới hạn đã biết

- Mức D lặp y nguyên hiện lại sau 60 s (cũ 10 s): mốc thời gian thưa hơn với câu đứng yên; số đếm `[+N lặp]` giữ đủ.
- Vòng poll ≥ 3 danh tính: một trạng thái quay về giá trị đã ghi < 10 s trước có thể bị gộp — y như 3.1.9 [SUY từ mã].
- Mức I giữ luật cũ ⇒ lỗ A → B → A 10 s của bộ nguyên văn còn ở mức I (như 3.1.9; dòng I của Kachi thường mang `#seq`).
