# F2 — Phím thoại câm ở lần mở app đầu: cổng cấp quyền adb loopback

> **Trạng thái**: Current · **Cập nhật**: 2026-08-24 · **Mục đích**: chứng minh cơ chế làm phiên dadb
> `localhost:5555` hỏng **im lặng** ở lần đầu, và ghi lại bản vá "phân loại lý do + chờ có giãn cách".

**Owner báo (08-24)**: *"start app vẫn chưa hold mic gọi gemini/kiki được, phải tắt, mở lại thì mới xin
được quyền và mới sử dụng được."*

---

## 1. Mức bằng chứng — đọc trước khi dùng tài liệu này

| Khẳng định | Mức |
|---|---|
| dadb 2.0.0 ném gì / chờ bao lâu khi khoá chưa được cấp quyền | **[ĐO]** — đọc bytecode jar thật, có offset |
| `LocalDeviceShell` gộp mọi lỗi thành `null` và không thử lại | **[ĐO]** — source + grep |
| Cổng cấp quyền adb **chính là** thứ làm câm phím mic ở lần mở đầu | **[SUY]** — khớp triệu chứng, **chưa đo trên xe** |
| adbd trên xe có bật `ro.adb.secure` hay không | **[CHƯA BIẾT]** — xem §5 |

Bản vá được thiết kế để **đúng ở cả hai khả năng** (có cổng cấp quyền hay không): nó chỉ thêm phân loại
lý do + vòng chờ cho hai đường owner-chủ-động, và **không đổi** đường nào khác.

---

## 2. [ĐO] dadb 2.0.0 làm gì khi khoá chưa được cấp quyền

Giải nén `~/.gradle/caches/.../dadb-2.0.0.jar`, đọc `AdbConnection$Companion.connect(AdbReader, AdbWriter,
AdbKeyPair, Closeable)`:

```
javap -p -c -constants dadb/AdbConnection\$Companion.class
```

| offset | việc |
|---|---|
| 0–8 | `writeConnect()` → `readMessage()` |
| 10–17 | máy trả `AUTH` (`1213486401` = `'AUTH'`) ⇒ vào nhánh xác thực |
| 24–36 | không có keypair ⇒ ném `AdbAuthException("Authentication required but no key pair was provided")` |
| 64–79 | ký token, `writeAuth(2, chữ-ký)`, `readMessage()` |
| 85–108 | vẫn `AUTH` ⇒ `writeAuth(3, khoá-công-khai)` rồi **`readMessage()`** ← chỗ hệ thống bung hộp thoại "Cho phép gỡ lỗi USB?"; adbd **không trả lời gì** tới khi người dùng bấm |
| 110–142 | không phải `CNXN` mà là `AUTH` ⇒ `AdbAuthException("Device rejected authentication (unauthorized)")` |
| 143–160 | còn lại ⇒ `AdbConnectException("Unexpected server response: …")` |

Và `DadbImpl`:

| việc | bằng chứng |
|---|---|
| `socket.setSoTimeout(socketTimeout)` rồi `socket.connect(addr, connectTimeout)` | `javap -c dadb/DadbImpl.class`, `newConnection()` offset 29 + 42 |
| `Dadb.create(host, port, keys)` ⇒ `create$default(..., mask 56)` ⇒ **connectTimeout=0, socketTimeout=0, keepAlive=false** | `javap -c dadb/Dadb$Companion.class` |
| lỗi socket lúc connect bọc thành `AdbConnectException("Failed to connect to $host:$port", e)` | cùng chỗ, offset 49–70 |
| lỗi IO lúc bắt tay bọc thành `AdbConnectException("Connection handshake failed", e)` | `connect$dadb`, offset 60–86 |
| **nối LƯỜI** — `connection()` chỉ chạy ở lệnh đầu tiên; `supportsFeature()` cũng ép nối | `javap -c dadb/DadbImpl.class`, `supportsFeature` offset 7 |

**Hệ quả 1** — `socketTimeout = 0` = đọc vô hạn ⇒ khi hộp thoại đang treo, `readMessage()` ở offset 104
**treo vĩnh viễn**, không ném lỗi nào. Một vòng thử-lại đặt lên trên đường này sẽ **không bao giờ chạy tới**
nếu không đặt hạn đọc.

**Hệ quả 2** — nối lười nghĩa là lỗi xác thực nổ **bên trong** block lệnh, không phải lúc `Dadb.create()`.
Thử lại mà không ép bắt tay trước sẽ **phát lại các lệnh đã gửi**.

**Hệ quả 3** (thấy ở vòng phản biện, xem §4.3) — ép bắt tay trước CHƯA đủ. Hạn đọc áp cho **mọi** lần đọc,
kể cả lần đọc kết quả một lệnh shell đã gửi đi rồi. Lỗi lúc đó cũng là `SocketTimeoutException`, cũng phân
loại thành `AWAITING_APPROVAL`, và nếu vòng lặp không có cờ riêng thì nó sẽ **gửi lại lệnh**. Phải chặn
bằng một cờ "đã phát lệnh", không thể chặn bằng thứ tự bắt tay.

---

## 3. [ĐO] Vì sao app không tự lành

`car-integration/src/main/kotlin/com/byd/clusternav/carexec/LocalDeviceShell.kt` (trước 08-24):

```kotlin
fun <T> session(keys: AdbKeyPair, block: …): T? = runCatching {
    Dadb.create(HOST, PORT, keys).use { adb -> block { … } }
}.getOrNull()
```

- `runCatching{}.getOrNull()` gộp **mọi** thất bại thành một chữ `null`: chưa cấp quyền / cổng 5555 đóng /
  đứt giữa chừng nhìn giống hệt nhau.
- `grep -n "retry\|repeat\|attempt\|unauthorized" LocalDeviceShell.kt` → **0 kết quả** (exit 1).
- `AssistantLauncher.launchViaVoiceAssistKey` chỉ log `"dadb session null (5555 chưa mở / key chưa allow)"`
  — **owner không thấy gì**, vì đó là logcat.

Khớp triệu chứng: hỏng một lần là bỏ hẳn tới khi owner tắt/mở lại app **[SUY]**.

---

## 4. Bản vá (08-24)

### 4.1 Tầng transport — `:car-integration`

File mới `carexec/LocalShellRetryPolicy.kt`:

- `LocalShellFailure` — 5 lý do: `AWAITING_APPROVAL` (adbd im lặng = đang chờ bấm) · `AUTH_REJECTED`
  (adbd trả `AUTH` lần nữa) · `PORT_CLOSED` (không có gì lắng nghe 5555) · `IO_ERROR` · `UNKNOWN`.
- `LocalShellFailures.classify` — lần theo chuỗi `cause` (trần 8 mức, chặn chuỗi vòng), ưu tiên theo mức
  ĐẶC THÙ: `AdbAuthException` → `SocketTimeoutException` → `ConnectException` → `IOException`.
  Phân biệt bằng **kiểu**, không bằng chuỗi thông báo.
- `LocalShellRetry` — chính sách; `NONE` (mặc định) = **nguyên hành vi cũ**, `AWAIT_ADB_APPROVAL` =
  **4 lần × giãn 1/2/4 s, hạn đọc 6 s ⇒ ~31 s đồng hồ tường**, ép bắt tay trước; `budgetMs = 30 s` là
  **lưới an toàn**, không phải lịch (xem §4.3 mục 2).
- `LocalShellResult` — `Ok(value, attempts)` / `Failed(reason, attempts, cause, commandDispatched)`.
- `LocalShellSessions.run` — vòng lặp, nhận connector/đồng hồ/giấc ngủ tiêm vào ⇒ test off-car được.

`LocalDeviceShell` thêm `sessionResult(keys, retry, onProgress, block)`; `session()` cũ nay uỷ quyền cho nó
với `LocalShellRetry.NONE`. Khi `socketTimeoutMs <= 0` vẫn gọi **đúng overload 3 tham số** như trước —
CLAUDE.md §6: đường đang chạy tốt không đổi vì một suy luận tương-đương.

### 4.2 Tầng app — chỉ 2 đường owner-chủ-động

| Đường | Chính sách | Lý do |
|---|---|---|
| `AssistantLauncher.launchViaVoiceAssistKey` (giữ phím mic) | `AWAIT_ADB_APPROVAL` | owner đang đứng trước màn hình ⇒ bấm được ngay |
| `AssistantLauncher.setSystemAssistant` (chọn trợ lý trong app) | `AWAIT_ADB_APPROVAL` | như trên |
| `VietMapAutostart` · `UpdateChecker` · `NavConnect` · `ClusterDiag` · `VietMapWidgetDiagActivity` | **giữ `NONE`** | chạy lúc khởi động máy / trong FGS boot / dump dài — chờ 30 s ở đó là hại |

Thêm ở đường phím mic:
- **chốt đơn-luồng** `voiceAssistInFlight` — vòng chờ ~31 s dài hơn hẳn debounce 1.5 s; không có chốt thì
  bấm mic vài lần sinh vài phiên dadb ⇒ vài hộp thoại chồng nhau.
- **Toast cho owner**: một lần lúc bắt đầu chờ ("Bấm Cho phép/Allow, tích luôn-cho-phép…"), một lần khi bỏ
  cuộc kèm **lý do** (chưa cấp quyền / cổng 5555 chưa bật / không nối được), và một lần khi cú bấm bị nuốt
  vì đang có vòng chờ chạy dở (§4.3 mục 5).

---

### 4.3 Vòng phản biện 08-24 — 6 chỗ phải sửa lại trước khi nhận

Bản vá đầu tiên xanh 2058 test nhưng nghiệm thu **KHÔNG ĐẠT**. Sáu điểm dưới đây đã sửa; mỗi điểm có test
riêng và mỗi test đã được **thử làm đỏ** (§6).

1. **[P1] Vòng thử lại phát lại lệnh đã gửi.** Một `runCatching` bọc cả `open` + `handshake` + `block`, nên
   lỗi ở lần đọc kết quả lệnh không phân biệt được với lỗi lúc mở. Hậu quả cụ thể: tài xế nhận nhiều lần
   `KEYCODE_VOICE_ASSIST` từ MỘT cú bấm; công thức 12 lệnh của `setSystemAssistant` chạy lại từ đầu.
   **Sửa**: cờ `dispatched` đặt **trước** khi gọi `connection.shell(...)`; `if (dispatched) break` là điều
   kiện dừng ĐẦU TIÊN của vòng lặp. Đây là chỗ *an toàn* thắng *đủ tính năng*: mất khả năng thử lại cho ca
   "hỏng giữa chừng" để đổi lấy việc **không bao giờ** phát lại lệnh lên xe đang chạy.
2. **[P1] Lịch chờ thật khác lịch mà test/doc khẳng định.** `budgetMs` tính bằng thời gian trôi thật, còn
   đồng hồ giả của test chỉ nhích trong `sleepMs` ⇒ trần 30 s không bao giờ chạm trong test. Trên xe, mỗi
   lần hỏng tốn đúng hạn đọc 6 s, nên `attempts = 5` chỉ chạy được **4** lần và giãn cách 8 s **không bao
   giờ dùng tới** (kéo theo `maxBackoffMs = 8 s` là con số chết).
   **Sửa**: `attempts = 4`, bỏ `maxBackoffMs`; đồng hồ giả trong test **tiến đúng `socketTimeoutMs` mỗi lần
   hỏng** ⇒ lịch trong test = lịch trên xe. `budgetMs` giữ lại làm lưới an toàn và có test riêng đi qua
   nhánh đó (`tran thoi gian cat vong cho truoc khi het luot thu`).
3. **[P2] Hỏng sau khi đã gửi lệnh thì nói sai với owner.** Gửi được lệnh nghĩa là bắt tay adb ĐÃ XONG, mà
   toast lại bảo "chưa cấp quyền gỡ lỗi USB". **Sửa**: `Failed.commandDispatched` → đường mic nói "đã gửi
   lệnh nhưng xe không trả lời kịp"; `setSystemAssistant` nói thêm "công thức mới chạy được một phần" (khe
   giữa `voice_interaction_service ''` và lần đặt lại là lúc trợ lý hệ thống đang **rỗng**).
4. **[P3] `Thread.start()` ném ⇒ chốt đơn-luồng kẹt vĩnh viễn** (CAS đã chiếm chốt, `finally` nằm TRONG
   runnable nên không chạy) ⇒ phím mic câm tới khi kill process. **Sửa**: `runCatching { worker.start() }`
   + nhả chốt khi hỏng.
5. **[P2/P3] Cú bấm bị nuốt: im lặng + còn bị phạt thêm debounce.** Mốc `lastVoiceAssistEmitMs` được cập
   nhật TRƯỚC chốt đơn-luồng ⇒ mỗi cú bấm bị nuốt vẫn đẩy mốc, cộng thêm tới 1,5 s chết sau khi vòng chờ
   kết thúc; và owner không nhận được gì ngoài một dòng log trong suốt ~31 s. **Sửa**: chỉ đóng dấu mốc sau
   khi CAS thành công; toast "đang thử nối vào xe…" **đúng một lần mỗi vòng** (`voiceAssistBusyNoticed`).
6. **[P3] Ngắt luồng trong block bị nuốt.** `setSystemAssistant` có `Thread.sleep(300)` nằm trong block;
   `runCatching` nuốt `InterruptedException` và việc ném nó đã xoá cờ ngắt. **Sửa**:
   `LocalShellFailures.wasInterrupted` → đặt lại cờ ngắt rồi dừng vòng lặp.
   ⚠ Bản sửa ĐẦU của chính mục này tự đẻ ra một lỗ: đặt `if (dispatched) break` **trước** chỗ khôi phục cờ
   ngắt, mà ca thật (`sh(...)` xong rồi mới `sleep` và bị ngắt) đi đúng nhánh `dispatched` ⇒ cờ ngắt vẫn
   mất. Đã đảo lại: **kiểm ngắt + đặt lại cờ TRƯỚC**, rồi mới tới các nhánh dừng. Test dựng đúng hình dạng
   của công thức (gửi lệnh rồi mới ném `InterruptedException`) và đã thử làm đỏ theo đúng thứ tự sai.

**Đã cân nhắc, GIỮ NGUYÊN có chủ ý** (ghi ra để lần sau khỏi tranh luận lại):

- `AUTH_REJECTED` vẫn nằm trong `retryOn`. Phản biện đúng ở chỗ: owner bấm "Từ chối" xong bị hỏi thêm tối
  đa 3 lần. Nhưng nếu ROM BYD trả `AUTH` ngay khi thấy khoá lạ (thay vì im lặng chờ người bấm) thì đó
  CHÍNH LÀ chữ ký của ca F2 — bỏ thử lại là bỏ luôn bản vá cho ca mình đang chữa, trong khi **chưa ai đo**
  ROM trả gì. Cái giá bị chặn trên (≤3 lần hỏi, ~31 s, owner đang đứng trước xe) nên chọn giữ. Chốt bằng
  phép đo ở §5 (thêm: bấm Từ chối một lần rồi xem logcat `VoiceKeyLauncher`).
- Mỗi lần thử là một kết nối MỚI, nên nếu owner bấm "Cho phép" mà **không tích "luôn cho phép"** thì quyền
  chỉ sống với đúng kết nối đang treo. Không có cách nào giữ kết nối đó qua lần thử sau mà vẫn bỏ được
  hạn đọc, nên câu chữ toast đã sửa để **nhắc tích ô đó**. Việc "mở lại kết nối trong lúc hộp thoại đang
  treo có bung thêm hộp thoại nữa không" là **[CHƯA BIẾT]** — thêm vào danh sách đo ở §5.

---

## 5. [CHƯA BIẾT] — cần một phép đo trên xe

Hai tài liệu trong repo mâu thuẫn nhau về việc adbd trên xe có đòi xác thực hay không:

- `docs/HUONG-DAN.md:20,63` — hướng dẫn owner *"Nếu xe hiện hộp thoại Allow USB debugging, bấm Allow"*
  ⇒ có cổng cấp quyền.
- `docs/diagnostics/b3-cluster-arrow-e2e-emulator-1920x720-2026-08-21.md:51` — *"trên xe adbd chạy root"*
  ⇒ build eng; nếu `ro.adb.secure=0` thì **không có** hộp thoại nào cả.

Chốt bằng đúng 3 lệnh trên xe (không cần app):

```
adb shell getprop ro.adb.secure
adb shell getprop ro.secure
adb shell ls -l /data/misc/adb/adb_keys
```

- `ro.adb.secure=1` ⇒ cơ chế ở §2/§3 đúng, bản vá này chữa đúng bệnh.
- `ro.adb.secure=0` ⇒ **bác** quy kết; lúc đó phải tìm nguyên nhân khác cho "lần mở đầu câm" (ứng viên kế
  tiếp: dịch vụ Hỗ trợ chưa **bound** nên `onKeyEvent` chưa chạy — `NavConnect.grantAccessibility` chỉ
  được gọi trong listener của công tắc Nav+HUD, tức **chỉ khi công tắc ĐỔI trạng thái**, xem
  `MainActivity.kt` nhánh `onCheckedChangeListener`). Bản vá vẫn giữ nguyên giá trị vì nó biến im-lặng
  thành có-lý-do, nhưng backlog F2 phải mở lại.

### 5.1 Ứng viên khác cho "lần mở đầu câm" — [SUY], ghi lại để khỏi phải tìm lại

Hai chỗ dưới đây cũng chỉ chạy **một lần / chỉ khi có thay đổi**, nên đều có thể góp phần vào triệu chứng.
Chưa đụng tới trong bản vá này vì nằm ngoài phạm vi F2 (và `MainActivity` đang có người khác sửa cho F3):

1. `MainActivity` — `setSystemAssistant` được gọi trong `onItemSelected` của spinner đích, mà nhánh đó có
   `if (vkTgtFirstCallback) { …; if (pos == vkTgtInitialPos) return }`. Tức **mở lại app với lựa chọn cũ
   thì không gọi lại** — đúng chủ ý "một lần", nhưng nếu lần gọi DUY NHẤT đó rơi vào lúc chưa được cấp
   quyền adb thì trợ lý hệ thống **không bao giờ được đặt**, và `input keyevent 231` sau này sẽ mở nhầm
   surface. Bản vá 08-24 làm lần gọi đó **chờ được owner bấm**, nên xác suất hỏng giảm hẳn — nhưng nếu
   owner bỏ qua hộp thoại thì vẫn kẹt tới khi đổi lại lựa chọn trong app.
2. `MainActivity` — `NavConnect.grantAccessibility` nằm trong listener của công tắc Nav+HUD, cũng **chỉ
   chạy khi công tắc ĐỔI trạng thái**. Dịch vụ Hỗ trợ chưa `bound` ⇒ `onKeyEvent` không chạy ⇒ phím mic
   câm hoàn toàn, không liên quan gì tới dadb.

Sau khi có phép đo: đổi hàng "cơ chế" ở §1 từ **[SUY]** sang **[ĐO]** hoặc **BÁC**, và cập nhật F2.

---

## 6. Test khoá bài học

| Test | Khoá gì |
|---|---|
| `car-integration/.../LocalShellApprovalRetryTest` (13 test) | vòng chờ THẬT, dùng **chính hằng chính sách** + **chính lệnh `input keyevent 231`** của production, transport/đồng hồ/giấc ngủ tiêm vào. Đồng hồ giả **tiến đúng hạn đọc mỗi lần hỏng** ⇒ lịch trong test = lịch trên xe. Khoá thêm: không phát lại lệnh · trần thời gian · giữ cờ ngắt |
| `app/.../VoiceKeyAdbApprovalWiringTest` (6 test) | đường phím mic có nối vào vòng chờ không; owner có được báo lý do không (và có nói ĐÚNG lý do khi đã gửi được lệnh không); chốt đơn-luồng nhả được cả khi `start()` ném; cú bấm bị nuốt có được báo và không bị phạt debounce; **và cả 5 đường dùng chung khác KHÔNG bị kéo theo** |

**Phép thử làm-đỏ (bắt buộc, đã chạy)** — test chưa từng đỏ là test mù:

| Gỡ cái gì | Kết quả |
|---|---|
| `AWAIT_ADB_APPROVAL` → `LocalShellRetry()` (mô phỏng trước-vá) | **5/8 đỏ** ở `:car-integration`; 3 test còn xanh đúng như thiết kế (cổng-đóng, phân loại thuần, chính-sách-NONE) |
| gỡ `retry=`/`onProgress=` ở đường phím mic + vô hiệu chốt đơn-luồng + kéo `VietMapAutostart` sang `sessionResult` | **4/5 đỏ** ở `:app` |
| **(vòng phản biện)** gỡ `if (dispatched) break` + gỡ khối khôi phục cờ ngắt | **2/13 đỏ** — `khong bao gio phat lai lenh da gui sang xe`, `bi ngat trong block sau khi da gui lenh` |
| **(vòng phản biện)** giữ nguyên hai khối nhưng **đảo thứ tự** (`dispatched` kiểm trước `wasInterrupted`) | **1/13 đỏ** — `bi ngat trong block sau khi da gui lenh`; đây là bẫy tự gây ra trong chính vòng sửa này, xem mục 6 |
| **(vòng phản biện)** trả `attempts = 5` + `maxBackoffMs = 8_000` như bản đầu | **2/13 đỏ** — `may tu choi khoa mai — bo cuoc dung 4 lan`, `chinh sach cho-cap-quyen…` |
| **(vòng phản biện)** trả `:app` về bản đầu (dấu debounce trước CAS, không toast bận, `worker.start()` trần, bỏ nhánh `commandDispatched`) | **4/6 đỏ** |

Mọi lần đều khôi phục và chạy lại xanh.

⚠ Một assertion suýt thành test mù: bản đầu chỉ kiểm `contains("result.commandDispatched")`, mà chuỗi đó
cũng xuất hiện trong dòng `Log.e` ⇒ gỡ hết nhánh xử lý vẫn xanh. Đã siết thành `contains("if (result.commandDispatched)")`
và chỉ khi đó phép thử làm-đỏ mới ra 4/6.

---

## 7. Rủi ro đã biết, chưa vá (đã vào backlog)

- **Hạn đọc 6 s áp cho mọi lệnh trong phiên** của hai đường đã bật chính sách. An toàn hiện tại vì cả hai
  chỉ chạy `input keyevent` / `settings put` / `appops set` (đều dưới 1 s). Thêm lệnh chậm vào hai đường
  này phải xem lại con số. Nếu hạn đọc nổ **giữa** công thức của `setSystemAssistant` thì phiên dừng tại
  đó và **không chạy lại** (§4.3 mục 1) ⇒ trợ lý hệ thống có thể ở trạng thái nửa vời; owner được toast
  báo "chạy được một phần, bật lại công tắc". Đây là đánh đổi có chủ ý: thà dừng nửa chừng có báo còn hơn
  phát lại 12 lệnh lên xe đang chạy.
- **`LocalShellFailures.classify` không phân biệt được connect-timeout với read-timeout** (cùng
  `SocketTimeoutException`). Hiện vô hại vì connector luôn truyền `connectTimeout = 0` nên chỉ lần ĐỌC mới
  đẻ ra ngoại lệ này — nhưng ai đặt connect-timeout khác 0 sẽ làm sai phân loại. Đã ghi ⚠ ngay trên
  `classify`.
- **`LocalDeviceShell$DadbLoopbackConnector` / `$DadbConnection` không có test** (mọi test tiêm connector
  giả). Đáng kể nhất: `handshake()` dựa vào **tác dụng phụ** của `supportsFeature` để ép nối — [ĐO] đúng
  với dadb 2.0.0 (`supportsFeature` → `connection()`), nhưng nếu dadb đổi sang cache/lazy thì cờ
  `eagerHandshake` thành no-op mà test vẫn xanh. Hậu quả khi đó chỉ là **mất khả năng thử lại** (lỗi auth
  nổ trong block ⇒ `dispatched` đã bật ⇒ dừng), KHÔNG phải phát lại lệnh — nên xếp mức rủi ro thấp.
- **Các đường `NONE` vẫn có thể treo vĩnh viễn** nếu hộp thoại cấp quyền đang chờ (`socketTimeout = 0`).
  Nặng nhất là `VietMapAutostart.runNow` chạy **đồng bộ** trong `BootSetupService` — nhưng đổi nó là điều
  CẤM của F2, nên chỉ ghi nhận, chưa vá. Xem F4 trong backlog.
