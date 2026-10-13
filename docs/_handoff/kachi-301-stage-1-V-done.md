# kachi-301 · stage 1 · nhánh V (R14–R17) — sửa hồi quy voice 3.0 R4 (hạ nhạc 20 %) — XONG off-car

> Spec `docs/specs/kachi-301-cast-steps-dialog-ia.html` §2.4 V1–V4 · R14–R18. Không commit. Không dùng máy ảo (agent khác giữ).
> Nhãn: [ĐO] chạy lệnh lượt này · [SUY] suy từ mã · [CHƯA BIẾT trên ROM BYD].

## Tệp đã đổi (chỉ trong danh sách được giao)

| Tệp | Dòng | Đổi |
|---|---|---|
| `app/src/main/java/com/byd/clusternav/launcher/voice/VoiceMusicDuck.kt` | 372 | hàng đợi nối tiếp lười; acquire không chặn; release chờ có trần; recover trên cùng hàng; đọc lại có chờ; giữ dấu khi "chưa đổi"; đường cong dB |
| `app/src/main/java/com/byd/clusternav/launcher/voice/VoiceCapture.kt` | 456 | `acquire` dời xuống SAU `startRecording` + kiểm `RECORDSTATE_RECORDING` (cờ `ducked`); `release` dời từ `closeRecord` lên `finally` sau `closeRecord`, gác `if (ducked)` |
| `core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceMusicDuckPolicy.kt` | 137 | `DUCK_DB`, `duckTarget` (+ `pickByDb` private), `decide` dung sai dốc |
| `core/src/test/kotlin/com/byd/clusternav/launcher/voice/VoiceMusicDuckPolicyTest.kt` | 159 | +5 test (dốc · áp trễ · dB ×3), sửa 1 kỳ vọng cũ |
| `app/src/test/java/com/byd/clusternav/launcher/VoiceListenWiringContractTest.kt` | 492 | khoá R14 (thứ tự), R15 (một hàng đợi, trần 600 ms), R16 (không xoá dấu khi chưa đổi) |
| `docs/diagnostics/voice-300-r4-duck-2026-10-10.md` | 123 | thêm §6 "3.1 fix" (số §5 giữ nguyên) |

Không đụng `KachiApplication.kt` (lời gọi `recoverAtStartup(this)` giữ nguyên), `BydHalGateway.kt`, `VoiceLoopGuardWiringContractTest` (needle `closeRecord(record, tailBeep = beep)` vẫn khớp).

## Thiết kế

- **Luồng/hàng (R14/R15)**: `ThreadPoolExecutor(0, 1, 30 s, LinkedBlockingQueue)` daemon `KachiVoiceDuck` — một worker, FIFO, sinh khi có việc, chết sau 30 s rảnh ⇒ 0 luồng thường trực. `acquire` = `leases++` dưới `lock` + (lượt đầu) xếp việc HẠ; `release` = `leases--` + (lượt cuối) xếp việc TRẢ rồi `Future.get(600 ms)`; quá trần/ngắt ⇒ log, đi tiếp (việc trả vẫn chạy sau việc hạ). Xếp hàng xảy ra dưới `lock` ⇒ thứ tự trên hàng = thứ tự đổi lượt ⇒ trả không bao giờ trước hạ. `recoverAtStartup` cũng xếp vào hàng này (bỏ `Thread` riêng). `lowered`/đường cong chỉ đụng trên luồng hàng. FileLock liên tiến trình, dấu `AtomicFile` ghi TRƯỚC khi hạ, recover pid chết, không bao giờ ném — giữ nguyên.
- **Thứ tự trong `listenGranted` (R14)**: bíp → mở mic → VAD → `startRecording` → kiểm ghi → log `mic mở sau` → `VoiceMusicDuck.acquire(ctx); ducked = true`. `finally`: `ep.close()` → `closeRecord` → `if (ducked) VoiceMusicDuck.release(ctx)` (trước khi `Heard` về bên thi hành ⇒ lệnh âm lượng bằng giọng chạy sau release).
- **Đọc lại (R16)**: sau `setStreamVolume(tgt)` đọc lại mỗi 25 ms tới 250 ms chừng nào còn == mức cũ (chỉ trên luồng hàng). Chưa đổi ⇒ GIỮ dấu. Đổi ra mức trong `(tgt, cur)` (giữa dốc/kẹp) ⇒ ghi `set = after`; ghi không được ⇒ hoàn tác ngay như 3.0.
- **Luật trả (`decide`, thuần ở core)**: dấu vô lý (`original ≤ set` / âm) ⇒ STALE; `current ≤ 0` hoặc `> original` ⇒ KEEP_USER; `set < current < original` ⇒ KEEP_USER (vặn TO); `current ≤ set` hoặc `== original` ⇒ RESTORE (quá 24 h ⇒ STALE). `== original` ⇒ RESTORE là CÓ CHỦ Ý: đặt lại đúng mức đang có là vô hại và đè một lệnh hạ còn treo (áp trễ) — 3.0 xoá dấu ở đây ⇒ nhạc hạ muộn không ai trả.
- **Chọn mức theo dB (R17)**: `duckTarget(current, min, dbByIndex)` — chỉ số trong `[max(min,1), current)` có dB gần nhất `dB(current) − 13,98`; hoà ⇒ chỉ số thấp; null/thiếu/ref không hữu hạn/không ứng viên hữu hạn/đường cong phẳng (< 0,01 dB) ⇒ lùi `target()` (luật chỉ số 3.0). App đọc `getStreamVolumeDb(MUSIC, i, dev)` cho i ∈ 0..max (IAE ⇒ NaN), `dev` = `TYPE_BUILTIN_SPEAKER` nếu có trong `getDevices(OUTPUTS)`, không thì thiết bị ra đầu tiên; ghi nhớ RAM theo `dev × max`.

## Kiểm API

- [ĐO] `javap -cp ~/Library/Android/sdk/platforms/android-37.0/android.jar android.media.AudioManager` ⇒ `public float getStreamVolumeDb(int, int, int);`. `data/api-versions.xml:35047` `since="28"`; `minSdk` 29 ⇒ không cần gác phiên bản.
- [ĐO đọc AOSP `android-10.0.0_r47` `media/java/android/media/AudioManager.java:1053-1071`] ném IAE khi stream không public / chỉ số ngoài `[min,max]` / thiết bị không hợp lệ; trả `-inf` khi ≤ `VOLUME_MIN_DB`; mỗi lời gọi tự gọi `getStreamMaxVolume` + `getStreamMinVolume` (binder) ⇒ lý do ghi nhớ đường cong.

## Test (đếm từ XML `build/test-results`, timestamp 2026-10-13T02:42–02:44Z)

| Lớp | tests | failures | errors |
|---|---|---|---|
| `VoiceMusicDuckPolicyTest` (core) | 15 | 0 | 0 |
| `VoiceListenWiringContractTest` (app) | 17 | 0 | 0 |
| `VoiceLoopGuardWiringContractTest` (app) | 10 | 0 | 0 |
| `VoiceFastNaturalWiringContractTest` (app, chạm `closeRecord`/mốc giờ) | 15 | 0 | 0 |

`:app:compileDebugKotlin` BUILD SUCCESSFUL (qua `gradle-locked.sh`). Test mới core: `am luong ap theo doc — doc lai giua doc…`, `ap tre chua toi — muc van bang goc…`, `20 phan tram do to = -14 dB…`, `theo dB cung khong bao gio ve 0…`, `may khong cho duong cong ⇒ lui ve luat chi so cu`. Kỳ vọng cũ đổi có chủ ý: `current = 10` (== gốc) KEEP_USER → RESTORE (lý do ở trên).

**Bằng chứng đỏ [ĐO]**: tạm thay `decide` về luật 3.0 (`if (current != marker.set) return KEEP_USER` …) ⇒ `:core:test` 15 test, **1 hỏng đúng** `am luong ap theo doc…` (`expected: <RESTORE> but was: <KEEP_USER>`); trả lại bản sửa ⇒ 15/15 xanh (`grep -c "current != marker.set"` = 0).

Chỗ gọi (CLAUDE.md §8) [ĐO grep]: `duckTarget` 1 (VoiceMusicDuck.lowerLocked) · `settledVolume` 1 · `volumeCurve` 1 · `musicDeviceType` 1 · `pickByDb` 1 (trong `duckTarget`) · `enqueue` 3 chỗ xếp việc · `acquire`/`release` ở `VoiceCapture.listenGranted` · `recoverAtStartup` ở `KachiApplication:49`.

Lưu ý môi trường: trong lúc chạy, `:core` (SimpleCastCoordinator) và `:app` (ProfileChip `kprof_pick_subtitle`) của agent khác tạm không biên dịch; đã chờ tới khi xanh rồi mới chạy — không sửa tệp của ai.

## Rủi ro còn lại

- [SUY] `release` quá trần 600 ms (AudioService/HAL chậm) ⇒ lệnh giọng *"giảm âm lượng"* có thể chạy trước việc trả; việc trả thấy mức ≤ `set` ⇒ đè lệnh người dùng về mức gốc.
- [SUY] lúc trả mà dốc đang ở giữa (`set < current < original`, không ai vặn) ⇒ KEEP_USER ⇒ nhạc ở mức giữa. Thu hẹp bằng chờ đọc lại 250 ms (ghi `set` = mức đầu tiên thấy đổi).
- [SUY] người dùng vặn NHỎ hơn nữa trong ≤ 8 s nghe ⇒ bị trả về gốc (spec R16 chấp nhận). Dấu sót lúc nổ máy mà BYD đặt lại mức ≤ `set` ⇒ trả về mức trước khi tắt máy.
- [SUY] lượt nghe rất ngắn (< 250 ms) ⇒ `release` có thể chờ việc hạ đang đọc lại (tối đa ~250 ms + binder, vẫn dưới trần 600).
- [CHƯA BIẾT trên ROM BYD] thiết bị ra của nhạc (loa trong/bus) và `getStreamVolumeDb` có phản ánh tiếng thật không; HAL có áp trễ/dốc thật không (V2 vẫn là [SUY mã]). Đường cong phẳng/lỗi ⇒ tự lùi luật chỉ số 3.0. Lệnh chốt: `logcat -s KachiVoiceDuck KachiVoiceTiming` (`mic mở sau` · `hạ nhạc … (luồng nền)` · `hạ nhạc khi nghe: A → B` · `trả nhạc N ms`).
- [CHƯA ĐO] R18 (máy ảo YouTube trước/sau, `voice-e2e.sh`) chưa chạy ở chặng này — cần chặng có máy ảo.
