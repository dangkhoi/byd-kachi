# Chạm trong ô: daemon bơm chạm bị sepolicy chặn · đường lùi theo cử chỉ (1.69)

> **Trạng thái**: Current · **Ngày**: 2026-09-17 · **Mục đích**: Ghi bằng chứng cho hai việc của 1.69 — (1) *vì sao* daemon bơm chạm không dùng được (đo trên máy ảo, có dòng `avc:` nguyên văn), (2) đường lùi theo **cử chỉ** đã làm app trong ô **cuộn được** (đo trên máy ảo, có ảnh trước/sau).
> Nguồn-sự-thật là THIẾT BỊ. Mọi mục [ĐO] kèm lệnh tái lập. Máy đo: `emulator-5554` (Android 10 `google_apis`, 1920×1080), Kachi **1.69 (70) vehicleTest**.

---

## 0. Bối cảnh — cái đã biết từ xe

[ĐO xe 2026-09-16] `docs/diagnostics/oncar-trace-2026-09-16b/README.md` §9.1:

- Owner: *"mở YouTube lên, không lướt để scroll được, nó chỉ nhận tap, mà tap thì nó lại không chính xác lắm"* · *"CPU về ổn nhưng vẫn không scroll đc đâu, ko assumption do CPU lag nhé"* (CPU Kachi **0–7 %** lúc đo).
- `adb shell input swipe 1000 850 1000 350 400` vào vùng ô ⇒ app nhận **một cú tap**.
- Mọi lần mở app: `I/Kachi/InputDaemonClient: daemon did not come up; staying on input -d fallback`.
- Đọc mã: đường lùi trước 1.69 bắn `input -d <id> tap x y` cho **cả** DOWN **lẫn** UP, dùng toạ độ **thô của view**, và **không có nhánh MOVE**.
- **Vì sao daemon không lên**: [CHƯA BIẾT] — `launchCmd` ném stderr vào `/dev/null`.

---

## 1. [ĐO] Daemon **CÓ** lên. Chỗ hỏng là lượt **NỐI** — sepolicy chặn

Sau khi 1.69 đổi redirect sang tệp thật, lượt đo đầu tiên đã trả lời được nửa câu hỏi — và trả lời **khác** mọi giả thuyết trong brief (`nohup` thiếu · `app_process` bị chặn · chờ quá ngắn):

```
# nhật ký của chính daemon (shell uid-2000 ghi, app đọc lại)
adb -s emulator-5554 shell cat /sdcard/Android/data/com.byd.launcher/files/kachi-logs/inputd-1789578704753.log
[Kachi/InputDaemon] start localabstract:kachi_input          ← daemon LÊN, socket đã mở

adb -s emulator-5554 shell "ps -A | grep app_process"
shell  1336  1  4331328  40076  __skb_wait_for_more_packets  S app_process   ← còn sống, đang accept
```

Phía client (logcat của Kachi, 1.69 in lượt đầu / lượt đổi lý do / lượt cuối):

```
I/Kachi/InputDaemonClient: launch rc= nohup=true log=…/kachi-logs/inputd-1789578704753.log
I/Kachi/InputDaemonClient: connect #1: IOException: Connection refused     ← daemon chưa kịp bind
I/Kachi/InputDaemonClient: connect #4: IOException: Permission denied      ← bind xong, và ĐÂY mới là bệnh
I/Kachi/InputDaemonClient: connect #25: IOException: Permission denied
I/Kachi/InputDaemonClient: daemon did not come up sau 5000 ms (25 lượt); lý do cuối=IOException: Permission denied; …
```

Nguyên nhân, nguyên văn từ kernel audit:

```
adb -s emulator-5554 shell "logcat -d -b all | grep -i 'avc:' | grep kachi_input"

avc: denied { connectto } for comm="kachi-inputd-li" path=006B616368695F696E707574
     scontext=u:r:untrusted_app:s0:c161,c256,c512,c768
     tcontext=u:r:shell:s0 tclass=unix_stream_socket permissive=0 app=com.byd.launcher
```

`006B616368695F696E707574` = `\0kachi_input` (socket ABSTRACT). `getenforce` ⇒ `Enforcing`.

**Kết luận [ĐO máy ảo]**: một app **untrusted_app** KHÔNG được `connectto` socket unix của miền **shell**. Đây là luật **sepolicy của nền tảng**, không phải lỗi mã: kiến trúc "daemon chạy bằng `app_process` ở uid shell + app nối vào qua localabstract" **không đi qua được** cổng này trên một ROM enforcing bình thường.

Hệ quả kiến trúc: **đường lùi `input -d` là đường chịu tải thật**, không phải lưới an toàn — đúng như chiếc xe đang hành xử suốt 4 lượt đo.

**[CHƯA BIẾT]** ROM DL3 của xe có cùng luật ấy không. Triệu chứng trùng khớp ⇒ **[ĐOÁN]** là có. Chốt bằng **một** lượt đọc trên xe (playbook §6h): `state.inputd.last_error` + tệp `inputd-*.log`.

**Chi tiết phụ [ĐO]**: lần khởi động thứ hai in `bind failed: Address already in use` ⇒ daemon cũ vẫn **thường trú**; nó không chết, nó chỉ không ai nối tới được.

---

## 2. [ĐO] Đường lùi theo cử chỉ — app trong ô **CUỘN ĐƯỢC**

Chuẩn bị (máy ảo cần loopback dadb, xem `kachi-emulator-app-hosting-2026-09-10.md` §Finding 2):

```
adb -s emulator-5554 reverse tcp:5555 tcp:5555         # dadb của app → adbd của chính máy ảo
# bật chế độ kiểm thử (TestBridgeStore) như scripts/emulator/voice-e2e.sh enable_test_mode
adb -s emulator-5554 shell "am broadcast -a com.byd.launcher.TEST -p com.byd.launcher \
    --es cmd slot --ei n 2 --es pkg com.android.settings"
```

[ĐO] ô 2 chiếu Android Settings thật: `dumpsys display` có `kachi-slot-1-*` **1872×461, displayId 2**, và `dumpsys activity` có `com.android.settings/.Settings` trên stack của display 2.

| Ca | Lệnh | Kết quả |
|---|---|---|
| (a) ép đường lùi | `inputd_disabled=true` ⇒ `state.inputd` = `{"healthy":false,"last_error":"","attempts":0,"forced_off":true}` — **không một lệnh shell nào** cho daemon | **ĐẠT** |
| (a) vuốt | `adb shell input swipe 960 1000 960 700 400` (vào vùng ô, màn 1920×1080) | **CUỘN THẬT**: trước = *Search settings · Customize your Pixel · Network&internet · Connected devices · Apps&notifications · Battery · Display*; sau = *Battery · Display · Sound · Storage · Privacy · Location · Security · Accounts* |
| (b) daemon bật lại | bỏ `inputd_disabled`, khởi động lại app ⇒ `state.inputd.last_error = "IOException: Permission denied"`, `attempts=25` | vẫn **CUỘN THẬT** — vì trên máy ảo daemon không bao giờ khoẻ (§1). Đó là một **kết quả**, không phải một lượt chạy hỏng. |
| chạm đơn | `adb shell input tap 300 690` vào một hàng | **MỘT** lần mở: ngăn xếp của ô có đúng một `com.android.settings/.SubSettings`. Trước 1.69 đường lùi bắn `tap` ở cả DOWN lẫn UP. |

Ba chuỗi lệnh đường lùi phát ra (golden-lock `TouchRouterTest`):

```
input -d <display> swipe <x0> <y0> <x1> <y1> <max(60, t1−t0)>   # vuốt (quãng > touchSlop)
input -d <display> swipe <x0> <y0> <x0> <y0> <t1−t0>            # giữ lâu (> longPressTimeout)
input -d <display> tap   <x0> <y0>                              # chạm — MỘT lần
```

---

## 3. Bẫy gặp khi đo (ghi để lượt sau không mất thời gian)

- **`am force-stop` một lần là KHÔNG đủ** để ghi đè `shared_prefs/*.xml` bằng `run-as`: có gì đó dựng lại tiến trình Kachi ngay sau đó, nó nạp prefs **cũ**, và tệp mới ghi xong thì bản trong RAM thắng. [ĐO] `forced_off` vẫn `false` dù tệp trên đĩa đã có khoá. Cách chạy đúng: `force-stop` → ghi tệp → **`force-stop` lần nữa** → `am start`.
- `logcat -c` trả `failed to clear the 'main' log` trên máy ảo này ⇒ đừng dựa vào việc xoá log; lọc theo dấu thời gian.
- Máy ảo **không có** YouTube dùng được cho ca này (ô hiện thẻ); Android Settings là app thay thế tốt: danh sách dài, cuộn rõ, có sẵn mọi máy.

---

## 4. Liên quan

- Spec: `docs/specs/kachi-open-app-correctly.html` **§4.6** (máy trạng thái + nghiệm thu) và **§9** (nhật ký triển khai 2026-09-17).
- Bằng chứng xe: `docs/diagnostics/oncar-trace-2026-09-16b/README.md` §9 · §9.1.
- Playbook lượt xe sau: `docs/diagnostics/oncar-verify-1.63-2026-09-15.md` **§6h**.
- Mã: `:core GestureFallback.kt` · `TouchRouter.kt` · `InputDaemonLaunch.kt` · `:app VdAppHost.kt` · `InputDaemonClient.kt` · `LocalAbstractChannel.kt`.
