# adb vào xe từ macOS khi `adb connect` báo `No route to host`

> **Trạng thái**: Current · **Ngày**: 2026-09-18 (đo 2026-09-16, dùng lại 2026-09-17) · **Mục đích**: hướng dẫn
> một-trang để vào xe qua adb TCP khi adb (hoặc Python) trên Mac báo `No route to host` dù xe đang ở cùng mạng.
> IP xe không ghi vào repo — thay `<ip-xe>` bằng IP thật lúc dùng (xe phát hotspot/đầu xe nối Wi-Fi điện thoại).

## 1. Triệu chứng và nguyên nhân [ĐO 2026-09-16]

| Lệnh | Kết quả |
|---|---|
| `adb connect <ip-xe>:5555` | `failed to connect to '<ip-xe>:5555': No route to host` |
| `python3 -c "socket.create_connection(('<ip-xe>',5555))"` | `OSError: No route to host` |
| `nc -z -w 2 <ip-xe> 5555` | **thông** (`succeeded!`) |
| `ping <ip-xe>` | thông |

`nc` (binary Apple ký) đi được mà `adb`/`python3` (binary tải về) không đi được, trên cùng máy cùng mạng ⇒
[SUY mạnh] **macOS Privacy › Local Network** chưa cấp cho tiến trình cha (terminal / Claude Code / IDE). Không
phải lỗi xe, không phải lỗi mạng: xe vẫn nghe cổng 5555.

## 2. Cách 1 — sửa gốc (một lần, cần tay người)

System Settings › **Privacy & Security › Local Network** › bật cho app đang chạy terminal (Terminal / iTerm /
Claude Code / VS Code…). Nếu app không có trong danh sách: chạy một lệnh mạng LAN từ app đó (ví dụ `adb connect`)
để macOS hiện hộp thoại xin quyền, rồi Allow. Sau đó `adb connect <ip-xe>:5555` chạy thẳng, không cần cầu.

## 3. Cách 2 — cầu `nc` về loopback (chạy được ngay, đã dùng hai lượt xe)

Ý tưởng: `nc` được phép ra LAN, adb chỉ cần nói chuyện với `127.0.0.1` (không bị chặn).

```bash
S=/tmp/kachi-adb            # thư mục tạm bất kỳ
mkdir -p $S; rm -f $S/adbfifo; mkfifo $S/adbfifo
# cầu: lắng nghe 127.0.0.1:15555, chuyển hai chiều tới xe
(nc -l 127.0.0.1 15555 < $S/adbfifo | nc <ip-xe> 5555 > $S/adbfifo) > /dev/null 2>&1 &
sleep 1
adb connect 127.0.0.1:15555        # → connected to 127.0.0.1:15555
adb -s 127.0.0.1:15555 shell getprop ro.build.fingerprint
```

Từ đây mọi lệnh dùng `adb -s 127.0.0.1:15555 …` (shell · pull · exec-out screencap · logcat · am broadcast).

**Giới hạn của cầu** (đều đã gặp):
- Cầu giữ **đúng một** kết nối TCP. adb rớt (xe tắt Wi-Fi khi cắm CarPlay/AA, hoặc `adb disconnect`) ⇒ hai `nc`
  thoát ⇒ phải dựng lại **cả khối** (fifo + hai `nc`) rồi `adb connect` lại. Đừng chỉ `adb connect` lại.
- Dựng lại thì `pkill -f "nc -l 127.0.0.1 15555"` trước, và `adb disconnect 127.0.0.1:15555` để adb quên
  phiên cũ.
- Trong Claude Code: chạy Bash **tắt sandbox** (sandbox chặn socket LAN của cả `nc`).
- Mỗi lệnh đi thêm hai hop `nc` ⇒ chậm hơn vài chục ms, không ảnh hưởng đo đạc.

## 4. Kiểm nhanh khi vẫn không vào được

```bash
nc -z -w 2 <ip-xe> 5555 && echo xe-nghe-5555 || echo xe-KHONG-nghe    # bước 1: xe có mở adb TCP không
adb kill-server; adb start-server                                       # bước 2: adb server treo
pgrep -fl "nc -l 127.0.0.1 15555"                                       # bước 3: cầu còn sống không
adb devices                                                             # bước 4: thấy 127.0.0.1:15555 device?
```
- `xe-KHONG-nghe`: xe chưa bật adb TCP (Kachi bật qua Cài đặt › Nâng cao, hoặc `setprop service.adb.tcp.port
  5555` từ phiên adb trước), hoặc xe đổi IP (hotspot cấp IP mới) — hỏi owner IP hiện tại.
- `unauthorized`: xe hiện hộp thoại RSA — owner bấm "Allow" trên màn xe.
- Đang cắm CarPlay/Android Auto: đầu xe **tắt Wi-Fi** ⇒ không có adb từ ngoài (CLAUDE.md §11); lấy log bằng
  app tự chụp (`DiagActivity`, bridge `voice_dump`), không cố adb.

## 5. Sau khi vào được — lệnh hay dùng (test-mode bật trong Cài đặt › Nâng cao)

```bash
A="adb -s 127.0.0.1:15555"
$A shell "dumpsys package com.byd.launcher | grep -E 'versionName|versionCode'"
$A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd state"
$A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd voice_dump --ez auto_confirm true"
$A exec-out screencap -p > screen.png
$A shell "ls -t /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ | head"
```
JSON của bridge trả nhiều dòng trong `data="…"` ⇒ parse bằng Python (`re.search(r'data="(.*)"', t, re.S)`),
xem `br.sh` trong handoff `docs/_handoff/session-2026-09-17-visual-voice-touch.md` §7.
