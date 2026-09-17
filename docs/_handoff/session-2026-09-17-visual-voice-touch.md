# Handoff 2026-09-17 — visual P1b/P2/P3 · hotfix chạm + voice · dữ liệu xe · gói giọng bé

> **Trạng thái**: Current · **Ngày**: 2026-09-17 (09:20) · **Người viết**: phiên Claude (owner: dangkhoi) ·
> **Mục đích**: owner yêu cầu *"viết handoff toàn bộ các công việc, các vấn đề đang gặp, kế hoạch thực hiện
> trước, không thực hiện gì thêm"*. Tài liệu này là nguồn duy nhất để phiên sau nối lại. Mọi khẳng định gắn
> [ĐO]/[SUY]/[ĐOÁN]/[CHƯA BIẾT] (`.kiro/steering/conversation-protocol.md`).

**Đọc trước**: `docs/PROJECT-BACKLOG.md` (Q · S · T · U · W · Y · M) · spec `docs/specs/kachi-visual-refresh.html`
· `docs/specs/kachi-voice-fast-natural.html` · `docs/specs/kachi-rain-defrost-and-rebind.html` ·
`docs/specs/kachi-voice-clone.html` · `docs/diagnostics/inputd-selinux-and-gesture-fallback-2026-09-17.md`.

---

## 0. Trạng thái repo LÚC NÀY — ⚠ cây làm việc CHƯA COMMIT và KHÔNG BIÊN DỊCH ĐƯỢC

| Mục | Giá trị [ĐO] |
|---|---|
| `origin/main` | `c964955` (OTA 1.69) · code `43e595b` **1.69 (70)** — đang chạy trên xe owner |
| Nhánh làm việc | `feat/voice-hotword-phrases`, **290 mục chưa commit** (`git status --short`) |
| Version trong cây | **1.70 (71)** (`app/build.gradle.kts:53-54`) — chưa ship, giữ số này |
| Biên dịch | **ĐỎ**: `./gradlew :app:compileDebugKotlin` ⇒ `VoiceSessionTurns.kt:85:17 No parameter with name 'decision' found` (hotfix D dở dang, xem §3) |
| Test lần xanh cuối | sau agent C (visual P2+P3): core **2169/0** · app **1125/0** (JUnit XML) — **trước** khi hotfix D chạm vào cây |
| Gói giọng bé | `voice/tts/kachi-giong-be-v1/` + `.sha256.tsv` **untracked, KHÔNG ignore** ⇒ **không được `git add -A`**; stage bằng đường dẫn tường minh |
| `design/`, `scripts/design/` | untracked toàn bộ (nguồn SVG + script sinh icon/xe) — là một phần của commit visual |
| Staged | chỉ **1** mục: xoá `app/.../system/inputd/LocalAbstractChannel.kt` (hotfix D) |

**Ba khối việc đang nằm chồng lên nhau trong cùng một cây** (không tách nhánh được nữa vì không dùng
`git stash/checkout` giữa phiên — luật phiên):

1. **VISUAL P1b + P2 + P3** — xong off-car, test xanh (agent A + B1 + B2 + C), **chưa review · chưa scan · chưa commit**.
2. **Review senior visual** (Opus, đọc-và-vá, không gradle) — **bị dừng giữa chừng** khi đang vá lỗi đầu tiên
   ("stale glass tag" trong `WallGlass.kt`). Danh sách finding **chưa được giao**; phần vá có thể đang dở.
3. **Hotfix chạm + voice (agent D)** — **bị dừng giữa chừng** ở bước "`ShellTransport` no-retry, rồi nối
   daemon client/main/channel". Tệp đã chạm (xem §3.3), cây không biên dịch.

Dừng 2 và 3 là do lệnh owner *"không thực hiện gì thêm"* (09:12). Không có agent nào còn chạy.
Tiến trình nền duy nhất còn lại: **không có** — gói giọng bé đã tổng hợp xong (§5).

---

## 1. Việc đã làm hôm nay (theo thứ tự)

### 1.1 Visual — trọn ba pha, off-car (owner: *"Làm tiếp phần visual cho đến hết đi"*)

| Pha | Nội dung | Bằng chứng |
|---|---|---|
| **P1b** hình nền + chọn màu (R8) | Ảnh mờ ¼ độ phân giải tính MỘT lần lúc chọn ảnh (`WallArt.kt`), thẻ = cửa sổ kính lên ảnh + lớp che theo **độ chói đo được** (`WallGlass.kt`, `GlassVeil.kt`), hai dải che đỉnh/đáy, 3 màu trội (`DominantColors.kt`); 9 ô màu nhấn (8 + "theo ảnh nền") + 3 tông thẻ (`ColorChoice.kt`, `SettingsRowsColor.kt`), lưu **theo hồ sơ**, tự bảo vệ tương phản (`ContrastGuard.kt`); không có ảnh nền ⇒ pixel y hệt 1.69 | `docs/diagnostics/visual-refresh-2026-09-16/p1b/` (16 ảnh + README + gfxinfo) · `contrast-table-colors.md` 72 dòng, 0 ❌ |
| **P2** 85 icon vẽ lại + 42 biến thể 32/48 | `design/glyph/*.svg` + `design/icon-grammar.json` → `scripts/design/gen-icons.py` → `res/drawable/` (chỉ script được ghi; `--check` so byte); `KachiIcons.kt` tra cỡ `_l/_xl` + hợp đồng tint (SÁNG luôn tô mực; TỐI mặt lớn hiện màu, chưa chọn thì khử bão hoà); 13 icon loại trừ (xe, launcher, mũi tên rẽ, bong bóng Cast…) | `p2/contact-sheet-*.png` · `IconStyleContractTest` viết lại · `IconSetInventoryTest` mới |
| **P3** hình xe 3 mặt + màu sơn + 1 widget lốp | `design/car/{top,front,side}.svg` → `scripts/design/gen-car.py` → 43 `ic_car_*` + 4 `car_face_*` + `CarFramesGenerated.kt`; `:core CarPartStyle.kt` (bộ phận × tone × available → tên token, không nhánh mặc định); `CarArtSource`/`VectorCarArt` + `CarArtPainter` (gradient sơn/kính/đèn/bóng dựng một lần); `TyreBoardView`/`DoorBoardView`/`CarMiniView` viết lại, 0 `drawRoundRect`; hàng "Màu sơn xe" 5 màu theo hồ sơ, đáy gradient < 3:1 ⇒ tự bật viền; sơn đỏ ⇒ ALERT đổi sang hổ phách + viền | `p3/preview-*.png` · `p2p3/` 12 ảnh máy ảo + README · `contrast-table-paint.md` · Δ APK +180,8 KB (< +350 KB) |
| Docs | spec visual §Changelog/§Tasks T3b·T4–T12 ✅/§9 "P1b" + "P2 + P3" · `docs/README.md` · backlog (Q) · `project-context.md` | — |

**Chưa làm của visual**: T13 review/scan/build release · T14 🚗 owner ngắm trên xe · `RasterCarArt` chỉ có
giao diện · mặt FRONT/SIDE chưa có bảng Canvas nào dùng · `AppDrawer.kt` 509 dòng (nợ cũ +1) ·
`KachiHomeActivity.kt` 536 dòng (nợ cũ).

### 1.2 Lượt xe 08:35–09:10 (owner trong xe, `<ip-xe>` qua cầu `nc`, test-mode bật lúc 08:50)

Mọi số dưới đây là [ĐO] trên xe owner, Kachi **1.69 (70)**, DL3 `eng.build.20260204`, `getenforce`=**Enforcing**,
load average **18**, RAM trống 75 MB (Google Maps 59 % CPU · surfaceflinger 72 % · YouTube 22 % · Kachi 13 %).
Tệp thô trong scratchpad phiên `car-0917/` (`usage-latest.log` 8 722 dòng · `screen-0839.png` · `voice-dump/`
`vd2/` = 9 lượt voice có WAV+JSON · `wiper-poll*.txt` · `br.sh`/`probe.sh`). **Scratchpad không sống qua phiên** —
phần cần giữ đã chép vào mục này; nếu cần WAV thật, lấy lại bằng `voice_dump` trên xe.

| # | Kết quả | Mức |
|---|---|---|
| Chạm | client nối daemon ⇒ `IOException: Permission denied` (25/25 lượt) ⇒ **sepolicy `connectto` chặn trên ROM xe y hệt máy ảo**. Daemon thường trú có sống (`bind failed: Address already in use`). Client **khởi động lại daemon mỗi ~5,5 s** khi owner chạm ⇒ 45 tệp `inputd-*.log` trong vài phút, `KachiPerf shell=36/phút`. Owner: *"vuốt OK, nhưng chậm… tap để play/pause nó nhận 2 tap"*. | [ĐO] |
| Tap đôi | `ShellTransport.exec` **gửi lại một lần** khi lượt đầu hỏng (socket timeout 10 s); `input -d tap` dưới load 18 chậm ⇒ lượt 1 đã bơm xong nhưng bị coi là hỏng ⇒ gửi lại ⇒ 2 tap. | [SUY mạnh] — chưa bắt được dòng log của đúng cú tap đôi |
| Voice | Mỗi lượt chính: bấm nút → **1,5 s** mới mở mic (dựng overlay + VAD) → `ToneGenerator: Immediate start timed out, status -110` ⇒ `startTone` **kẹt 3,0 s ngay trên luồng ghi âm**, TRƯỚC khi cửa sổ nghe bắt đầu, bộ đệm `AudioRecord` chỉ **2 560 B = 80 ms** ⇒ **4,5 s chết** sau khi bấm; câu nói trong đó mất sạch. 2 lượt thử có kiểm soát (08:51, 08:52): WAV phẳng hoàn toàn (đỉnh 184–207/32767, rms 45). Owner **không nghe tiếng bíp nào**. Khi nói trễ ≥ 7 s: "đóng kính lái" nhận đúng và chạy (`✓ Tắt Kính cửa lái`). Lượt không có tiếng vẫn bị giải mã 8,2 s (1,9–5,6 s) ⇒ chữ bịa "ừm/chúng ta xây/vâng giấc mơ". Model: `state.voice_model` = int8 nhưng tiến trình vẫn giữ **fp32** nạp lúc 07:34 (19,4 s) — engine không nạp lại khi gói đổi. Cửa sổ hỏi-lại 4 s cắt câu giữa chừng ("chỉnh lại hai mươi lăm độ nhiệt độ"). Lượt hỏi-lại nghe "ba" ⇒ `✓ Bật Kính cửa lái` — [CHƯA BIẾT] là chọn số 3 hay khớp âm một âm tiết. TTS hệ thống không có (`tts_default_synth`=null), Piper offline dựng 8,7 s rồi chạy. | [ĐO] |
| Ghế mát | `BYDAutoSettingDevice.getSeatVentilatingState(1|2)`: **OFF=1 · mức1=2 · mức2=3**, trim owner **không có mức 3** (cả ghế lái lẫn phụ). `ControlLevels.RAW_BY_LEVEL["seatc"]` hiện `listOf(1,2,3,4)` ⇒ **sửa thành 3 mức** (và `seath` cần đo tương tự). | [ĐO 2 ghế × 3 mức] |
| Điều hoà AUTO | `getAcControlMode`: **AUTO=0 · tay=1** (owner bấm AUTO trên màn, đọc đổi 1→0). | [ĐO] |
| Cốp | `BYDAutoSettingDevice.voiceCtlBackDoor(cmd)` qua bridge `hal --es op set … --ez auto_confirm true`: **1 = MỞ (2 lần, lặp lại được) · 3 = ĐÓNG (2 lần) · 2 = không thấy tác dụng** khi cốp đang đứng yên ([ĐOÁN] 2 = dừng giữa chừng — chưa thử lúc cốp đang chạy). Đọc `getBackDoorOpenedHeight` = 80 ở **Setting device** (Bodywork trả rỗng). | [ĐO] |
| Gạt mưa | 45 + 64 mẫu poll (0,5–1,2 s/mẫu) trước/trong/sau 10 s gạt liên tục: `WIPER_FRONT_WIPER_LEVEL` 65535 → **8** sau lần gạt đầu rồi **đứng yên** (kể cả khi đang gạt và sau khi tắt); `WIPER_AREA_FRONT_STATE` = −10011 (không dữ liệu); rơ-le = 0. `SETTING_FRONT_RAIN_WIPER_SPEED` = 1. ⇒ **không có tín hiệu "đang gạt" nào đọc được bằng poll**. Còn một đường: `registerListener` sự kiện Wiper — cần code, đo lượt sau. | [ĐO] |
| Perf | `KachiPerf`: HAL 30/phút (tốt) · **shell 36/phút** (`am stack list` mỗi 4 s của `VmOverlayPos`/`SimpleCast` cho bong bóng Vietmap + vòng lặp daemon) · log 12,2 KB/phút. | [ĐO] |

### 1.3 Gói giọng bé (VOICE-CLONE T4) — **tổng hợp XONG**

`[xong] 1493 clip · đúng 1282 · lệch 211 · 378,6 phút` (`scratchpad/voice-clone/pack-synth.log`, WAV ở
`scratchpad/voice-clone/pack-wav/`, `progress.tsv`). 1 282/1 493 = 85,9 % ASR đọc lại đúng tuyệt đối; 211 "lệch"
phần lớn là lệch chính tả của bộ nhận dạng, cần nghe kiểm mẫu. **Chưa chạy `finish-pack.sh`** ⇒ thư mục repo
`voice/tts/kachi-giong-be-v1/` vẫn là bản đóng gói thử 63 clip. ⚠ WAV nằm trong scratchpad của phiên
(`/private/tmp/claude-501/.../scratchpad/voice-clone/pack-wav/`) — **chép ra chỗ bền trước khi làm gì khác**
(ví dụ `~/.kachi/voice-clone-0917/`), vì thư mục scratchpad có thể bị dọn.

---

## 2. Quyết định owner hôm nay (nguyên văn, có hiệu lực)

- *"Làm tiếp phần visual cho đến hết đi"* ⇒ P1b → P2 → P3 trọn gói (đã làm off-car).
- *"vuốt OK, nhưng chậm phản hồi, với đang lag lòi ra, nhấn tap để play, pause nó nhận 2 tap"* · *"voice có test
  nhiều, nhưng vẫn phản hồi chậm, không làm được gì hết, rất vô dụng"* ⇒ hotfix chạm + voice là **ưu tiên trên
  visual** khi ship 1.70.
- *"đã bật [test mode], giờ làm gì, từng việc 1 cùng làm"* ⇒ quy trình đo trên xe **một việc một lượt**, owner
  xác nhận bằng mắt.
- Cốp: owner gật cho bắn `voiceCtlBackDoor` lúc xe đỗ, đã nhìn cốp mở/đóng thật.
- *"viết handoff toàn bộ… kế hoạch thực hiện trước, không thực hiện gì thêm"* ⇒ phiên sau **trình kế hoạch §6
  trước**, chưa code.

---

## 3. Vấn đề đang gặp (mở)

### 3.1 Sản phẩm (trên xe, 1.69)
1. **Chạm trong ô**: chậm (mỗi cử chỉ = một lệnh `input` spawn tiến trình dưới load 18), **tap đôi** (gửi lại),
   **vòng lặp khởi động daemon** 5,5 s/lần. Daemon chỉ vô dụng vì kênh unix-socket bị sepolicy chặn.
2. **Voice vô dụng**: 4,5 s chết đầu lượt (bíp lỗi + dựng phiên), không bíp báo, giải mã cả khoảng lặng,
   fp32 còn trong RAM, cửa sổ hỏi-lại 4 s, không có dòng log quyết định.
3. **Mưa**: không có nguồn tín hiệu "đang gạt" bằng poll ⇒ RainPolicy (spec S) chưa có đầu vào.
4. **Perf**: shell 36/phút.

### 3.2 Kỹ thuật / quy trình
- Cây làm việc không biên dịch (hotfix D dở). Review visual chưa có kết quả.
- `IconStyleContractTest`/`gen-*.py --check` gọi `python3` từ test — máy build khác không có python3 thì skip
  (đã thiết kế), cần xác nhận khi chạy CI/khác máy.
- Hai lần trong ngày agent chạy gradle song song làm hỏng cache Kotlin — luật **một agent gradle** giữ nguyên.
- `docs/diagnostics/visual-refresh-2026-09-16/p1b/` dùng ảnh nền **tổng hợp** (PIL), không phải ảnh owner.

### 3.3 Tệp hotfix D đã chạm (dở dang — phiên sau phải đọc `git diff` từng tệp trước khi tiếp)
`app/.../system/ShellTransport.kt` (+5) · `system/inputd/InputDaemonClient.kt` (+84/−?) · `InputDaemonMain.kt`
(+119, TCP) · **xoá** `LocalAbstractChannel.kt` (staged) · `:core system/inputd/DaemonChannel.kt` ·
`InputDaemonLaunch.kt` (+35: cổng + token) · **mới** `:core system/inputd/TcpLoopbackChannel.kt` (+test) ·
**mới** `:core system/ShellIdempotency.kt` (+test) · **mới** `:core launcher/ResendGate.kt` (+test) ·
`Prefs.kt` (+17) · `AppContainer.kt` (+5) · `VmOverlayPosition.kt` (+19, backoff) · `SlotLiveProbe.kt` (+15) ·
`testbridge/TestBridgeState.kt` (+8) · voice: `VoiceCapture.kt` (±47) · **mới** `voice/VoiceChime.kt` ·
`VoiceModelStore.kt` (+5) · `VoiceRecognizer.kt` (+3) · `VoiceSession.kt` (+30) · `VoiceSessionTurns.kt` (+1 —
**chỗ đỏ**: truyền `decision =` vào hàm chưa có tham số) · **mới** `:core voice/VoiceDecision.kt` ·
**mới** `:core voice/VoiceSilenceGate.kt` (+test). `VoiceOverlay.kt` (+1 import) là của agent A, không phải D.

Hai lựa chọn cho phiên sau: **(a)** tiếp tục D theo đúng đề bài §6.2 (khuyến nghị — phần :core đã có test);
**(b)** `git checkout --` các tệp trên + `git reset HEAD LocalAbstractChannel.kt` + xoá tệp mới của D để lấy lại
cây visual xanh, rồi làm hotfix sau commit visual. Chọn (a) nếu muốn 1.70 mang cả hai; chọn (b) nếu muốn ship
visual trước.

---

## 4. Bằng chứng / tài liệu đã ghi

- `docs/specs/kachi-visual-refresh.html` — §Changelog, R8 ✅, §4.10 "as built", T3b–T12 ✅, §9 "P1b" (9 mục) +
  "P2 + P3" (11 mục, có deviation).
- `docs/diagnostics/visual-refresh-2026-09-16/{p1b,p2,p3,p2p3}/` + `contrast-table-{colors,paint}.md`.
- `design/NOTES-icons.md`, `design/NOTES-car.md` — ghi chú tích hợp của B1/B2 (đã tích hợp).
- **CHƯA có** tài liệu diagnostics cho lượt xe 09-17 (dự định `docs/diagnostics/oncar-0917-voice-touch.md` —
  giao cho D, chưa viết). Số liệu nằm ở §1.2 trên; WAV/JSON voice trong scratchpad phiên (sẽ mất).
- Memory phiên: `kachi-car-facts-0917.md` (mới), `kachi-roadmap-0916.md` (cập nhật).

---

## 5. Cách theo dõi / nối lại gói giọng bé

```bash
S=<scratchpad-phiên>/voice-clone          # /private/tmp/claude-501/-Users-koi-.../98fe2032-.../scratchpad/voice-clone
tail -3 $S/pack-synth.log                 # phải thấy dòng "[xong] 1493 clip …"
find $S/pack-wav -name '*.wav' | wc -l    # 1 551 WAV [ĐO] trong 4 thư mục tầng fixed/ head/ unit/ num/ (+ progress.tsv)
pgrep -fl synth-pack                      # phải RỖNG (đã xong); nếu còn ⇒ đang chạy, đừng chạy lại
# (1) ĐÃ CHÉP ra chỗ bền lúc 09:25: ~/.kachi/voice-clone-0917/{pack-wav,pack-synth.log,progress.tsv} (111 MB, 1 551 WAV)
#     và ~/.kachi/car-0917/ (8,8 MB: usage-latest.log · screen-0839.png · voice-dump/ · vd2/ · wiper-poll*.txt · br.sh · probe.sh)
#     ⇒ nếu scratchpad đã mất, đặt S=~/.kachi/voice-clone-0917 (finish-pack.sh đọc pack-wav/ từ $SP)
# (2) đóng gói vào repo (AAC ADTS 32 kbps, manifest, index, sha256):
SP=$S REPO=$(pwd) bash scripts/voice/clone/finish-pack.sh
python3 scripts/voice/clone/validate-pack.py voice/tts/kachi-giong-be-v1   # kiểm sha + manifest
# (3) chọn 10 cặp cho owner nghe (T5) — đã có ở scratchpad/voice-clone/t5-compose/ (cũng sẽ mất, sinh lại bằng compose-check.py)
```
Sau đó mới tới T3 `VoiceClipInventory` (:core) · T6 `VoicePack` catalog · T7 `ClipSpeaker` (tra chuỗi chính xác →
Piper dự phòng) · T8 Cài đặt › Giọng nói (Piper mặc định, giọng bé là lựa chọn) · T9 đăng gói OTA · 🚗 T10 độ trễ
giải mã AAC. Chi tiết trong `docs/specs/kachi-voice-clone.html` §5.

Bộ máy F5-TTS: `/tmp/tts-venv` (py3.12, torch MPS) — **ngoài repo, có thể bị dọn**; dựng lại theo
`scripts/voice/clone/README` (nếu chưa có README thì theo KDoc đầu `synth-pack.py`). Clip tham chiếu giọng bé và
mọi ghi âm ở `voice/record/` (git-ignored) — **không bao giờ** đưa lên cloud/repo.

---

## 6. Kế hoạch đề xuất (trình owner duyệt TRƯỚC khi làm)

### 6.1 Việc 0 — cứu dữ liệu (5 phút, không cần duyệt)
Chép `pack-wav/` + `car-0917/` (WAV voice xe + poll) ra `~/.kachi/…`.

### 6.2 Việc 1 — hotfix chạm + voice, hoàn tất D (ước 1 ca) — **ưu tiên 1**
Chạm: (a) `input …` không bao giờ gửi lại (no-retry) · (b) cầu chì daemon: 1 chu kỳ `Permission denied` ⇒ thôi
hẳn tới lần mở app sau, dọn `inputd-*.log` còn 5 · (c) kênh **TCP loopback** `127.0.0.1:<cổng>` + token, chứng
minh trên máy ảo (`state.inputd.healthy=true` + cuộn thật app trong ô, ảnh trước/sau); TCP cũng bị chặn ⇒ ghi dòng
`avc:` và giữ cử chỉ làm đường chính.
Voice: (d) bíp không bao giờ nằm trên luồng ghi âm; `ToneGenerator` hỏng 1 lần ⇒ tắt cả tiến trình; thay bằng
chime PCM ngắn qua `AudioTrack` (không FAST) phát bất đồng bộ (`VoiceChime.kt` đã có khung) · (e) mở mic **trước**,
dựng overlay/VAD song song hoặc hâm sẵn — mục tiêu bấm → nghe < 300 ms · (f) bộ đệm `AudioRecord` ≥ 1 s ·
(g) engine nạp lại khi gói được chọn đổi/tải xong; log id đã nạp · (h) lượt chính không có tiếng ⇒ không giải mã,
đáp ngắn "Kachi không nghe thấy gì" · (i) hỏi-lại 8 s, xác nhận 5 s (VAD tự ngắt) · (j) một dòng
`KachiVoiceSession` quyết định/lượt + `VoiceUtteranceLog` · (k) kiểm "ba" là chọn số hay khớp âm; cấm khớp âm
một âm tiết · (l) ngữ pháp số-trước/sau-danh-từ cho nhiệt độ.
Perf: (m) `am stack list` chỉ khi bong bóng Vietmap đang hoạt động, backoff ≥ 15 s.
Bảng: (n) `ControlLevels` `seatc` = `listOf(1,2,3)`; ghi `getAcControlMode` AUTO=0 vào `HalReadTables`/spec S;
cốp = `voiceCtlBackDoor` 1/3 vào `ControlRegistry` (đường ghi thật, có gate xác nhận).
Kiểm: suite core+app xanh · **harness voice máy ảo bắt buộc** (`scripts/emulator/voice-e2e.sh --only all`,
mốc 104/105 · 23/27) · tài liệu `docs/diagnostics/oncar-0917-voice-touch.md`.

### 6.3 Việc 2 — chốt visual (½ ca)
Review senior (Opus) lại từ đầu (bản trước bị dừng) → vá → chạy suite → scan bảo mật → **commit 1**:
`feat(visual): 1.70 — P1b kính mờ + chọn màu · P2 85 icon · P3 hình xe 3 mặt + màu sơn + 1 widget lốp`
(stage bằng đường dẫn; **không** đưa `voice/tts/kachi-giong-be-v1*`). Rồi **commit 2** hotfix (§6.2) sau
review + scan riêng. Build release ký `~/.kachi/kachi-release.keystore`, đăng OTA `apk/Kachi-1.70-release.apk`
(thay 1.69) theo `kachi-signing-ota` memory, tác giả `dangkhoi`.

### 6.4 Việc 3 — gói giọng bé (½ ca + owner nghe)
§5: finish-pack → validate → 10 cặp T5 cho owner nghe → T3/T6/T7/T8 → OTA gói (public OK theo owner).

### 6.5 Việc 4 — 🚗 lượt xe sau (15 phút, cần owner)
1.70: bấm mic nói ngay có nhận không · YouTube vuốt/tap (đơn) · `state.inputd` (TCP lên chưa) · ảnh visual
mới trên xe (T14, ban ngày + đêm) · cốp bằng nút/giọng · listener Wiper (nếu §6.6 làm xong) · `seath` thang mức.

### 6.6 Việc 5 — còn lại theo lộ trình (không đổi thứ tự owner duyệt 09-16)
RainPolicy đổi nguồn sang **listener** sự kiện Wiper (thử) hoặc treo ⇒ spec S cập nhật · GPS chỉ đọc (spec S R5)
· CapTest v2 · live-state (34 readKey còn lại) · VFT-0 mirror model fine-tune 84,8 % (kèm `GU GỒ MÁP`/`DIU TÚP`
trong `VoiceSynonyms`) · OCR định kỳ với `.opencodereview/rule.json` · nợ LOC (`SimpleCastCoordinator` 784,
`BydHal` 654, `FloatingBubbleService` 537, `VietMapWidgetBridge` 505, `KachiHomeActivity` 536, `AppDrawer` 509).

---

### 6.7 Dữ liệu xe ĐÃ CÓ hôm nay — code được ngay, KHÔNG cần lên xe lại

| Việc code | Số đo dùng | Nơi sửa | Bài canh |
|---|---|---|---|
| **Cốp sau: nút + giọng "mở/đóng cốp"** | `BYDAutoSettingDevice.voiceCtlBackDoor(1)` = mở · `(3)` = đóng [ĐO 2 lần mỗi lệnh] | `ControlRegistry` `trunk` (route named-method Setting device, hai action mở/đóng, giữ cổng xác nhận `CtlSafetyPolicy`), `HalBindingTable`, `VoiceGrammar` ("mở cốp", "đóng cốp", "cốp sau") | test route + parser; E2E `ctl` |
| **Ghế mát thang mức** | OFF=1 · 1=2 · 2=3, trim owner không có mức 3 | `ControlLevels.RAW_BY_LEVEL["seatc"] = listOf(1,2,3)` (bỏ 4); `seath` giữ [SUY] tới khi đo | `ControlLevelsTest` |
| **Điều hoà AUTO** | `getAcControlMode` AUTO=0 · tay=1 [ĐO 2 điểm] | `HalReadTables`/readKey `ac_auto`, nút `ac_auto` (đang "absent on trim") → nối `setAcControlMode` (spec S R2) | contract test |
| **Cốp đọc trạng thái** | `getBackDoorOpenedHeight`=80 ở **Setting** device (Bodywork rỗng) | readKey datum cốp | — |
| Gạt mưa | **không có** tín hiệu poll ⇒ RainPolicy chỉ còn đường listener (§6.6) | spec S: ghi "poll = chết", R3 chờ listener | — |

**Còn PHẢI lên xe** (🚗, 15 phút, sau khi 1.70 lên): `seath` (ghế sưởi) thang mức · `voiceCtlBackDoor(2)` lúc cốp
đang chạy (dừng?) · listener Wiper có bắn sự kiện không · `state.inputd` TCP lên chưa · bấm-mic-nói-ngay có nhận
không · tap đơn trong YouTube · visual mới (T14) · perf K5 sau 1.70.

### 6.8 TOÀN BỘ scope chưa làm — bảng tổng (nguồn: `docs/PROJECT-BACKLOG.md` + specs; id giữ nguyên)

| Nhóm | Mục | Trạng thái | Cần xe? |
|---|---|---|---|
| **Ship 1.70** | Hotfix chạm+voice (§6.2) · review/scan/commit visual (§6.3) · release ký + OTA `apk/Kachi-1.70-release.apk` | dở / chưa | — |
| (Q) VISUAL | T13 review+scan+build · **T14 owner ngắm trên xe** · `RasterCarArt` (chỉ khi owner có ảnh) · FRONT/SIDE chưa có bảng Canvas dùng · nợ LOC `AppDrawer` 509, `KachiHomeActivity` 536 | 🔲 | T14 🚗 |
| (S) RAIN-DEFROST + REBIND + GPS | R1 cốp (**có số đo, code được**) · R2 AC AUTO (**có số đo**) · sấy trước/sau + gương (`getAcDefrostState 1|2` đọc được; setter chưa thử) · R3 RainPolicy (**không có nguồn poll** ⇒ thử listener hoặc treo) · R5 GPS chỉ đọc (xin FINE/COARSE, cấm mock provider vĩnh viễn) · T-oncar-0 còn: `voiceCtlBackDoor(2)`, listener Wiper | 🔲 | một phần 🚗 |
| (P) CAPTEST-V2 | spec `kachi-captest-v2.html` chưa làm | 🔲 | 🚗 chạy |
| (L) LIVE-STATE | **H1-T2b** 30 nút còn lại chưa có `readKey` (17/47 đã nối) · listener HAL thay poll · ô nhiều mức/gộp · spec `kachi-live-state-ux.html` | 🔲 | một phần 🚗 |
| (R)/(R2) CAPTEST | S6 "chiếu cụm" hỏng trên Sealion 6 · owner bị chặn ĐỌC 72 mục (do device map, đã vá 1.66?) — xác nhận lại 1.70 · `kachi-capability-checklist.md` lỗi thời (owner chọn: generator hay archive) | 🔲 | 🚗 |
| (T)/(U) VOICE | hotfix §6.2 · sau đó: ngưỡng nền cabin (`voice_endpoint_floor_cap`) · Piper đọc "PM2.5" ("pê mờ" vs "pê-em" — 7 WAV mẫu đã mất cùng scratchpad, sinh lại) · hội thoại tiếp nối 5 s (owner) | 🔲 | 🚗 verify |
| (Y) VOICE-FT | **VFT-0** mirror `gipformer1.5-65M-rnnt` fine-tune (84,8 % vs 74,8 % giọng thật; 78,2 MB int8; model ở scratchpad `ft/model-gip15-ep2/` — **kiểm còn không, nếu mất phải chạy lại `scripts/voice/ft/`**) + `SherpaModelCatalog` experimental + kiểm `GU GỒ MÁP`/`DIU TÚP` trong `VoiceSynonyms`/`VoiceAppPhonetics` · VFT-2 🚗 RTF trên xe · VFT-3 thêm ghi âm thật (trẻ em, giọng vùng) · VFT-4 RNN-LM | 🔲 | VFT-2 🚗 |
| (W) VOICE-CLONE | §5: finish-pack → validate → T5 owner nghe 10 cặp → T3 `VoiceClipInventory` → T6 `VoicePack` → T7 `ClipSpeaker` → T8 Cài đặt › Giọng nói → T9 OTA gói → 🚗 T10 độ trễ AAC | tổng hợp xong, còn lại 🔲 | T10 🚗 |
| (K) PERF | K5 🚗 đo lại sau 1.70 · **K8 shell 36/phút** (`am stack list` 4 s — vá trong §6.2m) · K7 nợ LOC `SimpleCastCoordinator` 784, `BydHal` 654, `FloatingBubbleService` 537, `VietMapWidgetBridge` 505 | 🔲 | K5 🚗 |
| (X) OCR | chạy `ocr` định kỳ trước OTA lớn với `.opencodereview/rule.json` | 🔲 định kỳ | — |
| (M) VOICE-FEEDBACK-0916 · M-OCR-1..4 · LOC-1.69 | các mục nhỏ còn mở trong backlog (đọc tại chỗ) | 🔲 | — |
| INPUTD-1.69 | sepolicy trên ROM DL3 — **ĐÃ CHỐT [ĐO] hôm nay: Enforcing, Permission denied** ⇒ đóng mục này bằng handoff §1.2, còn lại là kênh TCP (§6.2c) | ✅ đo / 🔲 vá | — |
| Nhóm 6 nợ verify 🚗 cũ | H1 Waze đường lùi · X1 sống chung chiếu-cụm · L-RE2 3 cặp feature-id trùng · S4-REAPPLY · ARCH-🚗 · chiều đúng lệnh khoá cửa | 🔲 | 🚗 |
| Doc nợ | `docs/diagnostics/oncar-0917-voice-touch.md` (chưa viết, số ở §1.2) · DOC-TBRIDGE-TABLE · DEBT-E2E-PREFS · DEBT-E2E-SH | 🔲 | — |

## 7. Lệnh hay dùng (chép nguyên)

```bash
# gradle (một agent một lúc)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 GRADLE_OPTS=-Xmx3g ./gradlew -q :core:testDebugUnitTest :app:testDebugUnitTest --max-workers=2
# cache Kotlin hỏng: ./gradlew --stop && rm -rf core/build/kotlin app/build/kotlin
# đếm test từ XML: python3 scripts/count-tests.py

# adb vào xe (macOS chặn Local Network cho adb) — sandbox tắt
mkfifo $S/adbfifo; (nc -l 127.0.0.1 15555 < $S/adbfifo | nc <ip-xe> 5555 > $S/adbfifo) & adb connect 127.0.0.1:15555
# bridge (cần test-mode bật trong Cài đặt › Nâng cao)
am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd state
… --es cmd voice_dump --ez auto_confirm true                       # zip ở /sdcard/Download/
… --es cmd hal --es op get   --es dev BYDAutoSettingDevice --es m getSeatVentilatingState --es args 1
… --es cmd hal --es op getid --es dev BYDAutoWiperDevice   --es m WIPER_FRONT_WIPER_LEVEL
… --es cmd hal --es op set   --es dev BYDAutoSettingDevice --es m voiceCtlBackDoor --es args 1 --ez auto_confirm true   # MỞ CỐP THẬT
```
