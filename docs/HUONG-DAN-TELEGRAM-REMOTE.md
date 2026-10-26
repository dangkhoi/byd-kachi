# Hướng dẫn: Điều khiển Kachi từ xa qua Telegram

> **Trạng thái**: Current · **Cập nhật**: 2026-10-26 · **Bản**: Kachi 3.1.15 (220) — bản cá nhân, KHÔNG OTA
> **Mục đích**: Từ điện thoại, gửi điểm đến / mở nhạc / điều khiển xe qua một bot Telegram. Lên xe là Kachi tự dẫn đúng đường ngay khi có GPS; đang ngồi sau cũng bấm điều khiển mà không phiền tài xế.

APK: `apk/Kachi-3.1.15-release.apk` (sha256 `d323da8a…87daf`). Cài đè bản cũ bằng `adb install -r` — **giữ nguyên dữ liệu** (cùng khoá ký).

---

## Phần 1 — Tạo bot Telegram (làm 1 lần, ~3 phút)

### 1.1 Tạo bot, lấy **mã bot (token)**
1. Mở Telegram, tìm **@BotFather** (tài khoản chính thức, có dấu tích xanh).
2. Gõ `/newbot` → đặt tên hiển thị (vd *Kachi Xe*) → đặt username kết thúc bằng `bot` (vd `kachi_xe_bot`).
3. BotFather trả về một dòng **token** dạng:
   ```
   8123456789:AAH8xZ...dài...k0
   ```
   Đây là **Mã bot Telegram** — copy giữ lại. (Token này là chìa khoá điều khiển xe — **không chia sẻ cho ai**.)

### 1.2 Lấy **mã chat (chat ID)** của chính bạn
Chat ID để Kachi **chỉ nghe lệnh từ bạn**, bỏ qua người lạ nhắn vào bot.
1. Trong Telegram, tìm bot vừa tạo (theo username) → bấm **Start** → gửi đại một tin, vd `hi`.
2. Tìm **@userinfobot** → bấm Start → nó trả về `Id: 123456789`. Số đó là **Mã chat Telegram** của bạn.
   - *(Cách khác: mở trình duyệt vào `https://api.telegram.org/bot<TOKEN>/getUpdates` sau khi đã nhắn bot — tìm `"chat":{"id":123456789` trong kết quả.)*

> Giữ cả 2: **token** + **chat ID**. Điền vào Kachi ở Phần 2.

---

## Phần 2 — Khai báo trong Kachi (trên xe, 1 lần)

Mở **Cài đặt Kachi → nhóm *Dẫn đường* → mục *Điều khiển qua Telegram***:

| Dòng | Điền gì |
|---|---|
| **Bật điều khiển qua Telegram** | Gạt **BẬT** |
| **Mã bot Telegram** | Dán token ở bước 1.1 |
| **Mã chat Telegram** | Dán chat ID ở bước 1.2 |
| **Kiểm tra kết nối** | Bấm — Kachi gửi 1 tin thử tới Telegram. Điện thoại nhận được = OK |
| **Cho phép điều khiển xe qua Telegram** | Gạt BẬT **nếu** muốn dùng `/ac`, `/defrost`… (để TẮT thì chỉ dẫn đường + nhạc) |

Xong. Kachi tự chạy một dịch vụ nền hỏi Telegram **mỗi 60 giây** khi xe đang ở màn hình chính, và một lần nữa mỗi khi lên xe.

> **Lưu ý GPS**: điểm đến gửi qua Telegram **chỉ bắt đầu dẫn khi xe đã bắt được GPS**. Vừa nổ máy / trong hầm chưa có định vị → Kachi **CHỜ**, có GPS lúc nào dẫn lúc đó (không dẫn mù).

---

## Phần 3 — Cách dùng (gửi lệnh cho bot)

Nhắn thẳng vào bot trên Telegram. **Lệnh bắt đầu bằng `/`**, tiếng Anh; **tên nơi để tiếng Việt thoải mái**.

### 3.1 Dẫn đường
| Gửi | Kachi làm |
|---|---|
| `/go Công ty` | Đặt điểm đến **"Công ty"** (lấy từ **sổ địa chỉ đã lưu** trong Kachi). Lên xe + có GPS → dẫn ngay bằng app mặc định (gmaps/vietmap). Thắng lịch dẫn-tự-động của chuyến đó. |
| `/go` (không kèm tên) | Bot gửi lại **danh sách nút bấm** các nơi đã lưu — bấm 1 nút là chọn, khỏi gõ. |
| `/go 21.0285, 105.8542` | Dẫn tới toạ độ trực tiếp. |
| `/go 1 Đại Cồ Việt Hà Nội` | Địa chỉ lạ không có trong sổ → vẫn dẫn (Google Maps tự tra). |
| Gửi **vị trí** (ghim 📎 → Location) | Dẫn tới đúng điểm đó. |

> **Sổ địa chỉ dùng chung**: Telegram đọc **đúng sổ địa chỉ của Kachi** (Nhà / Công ty / … bạn đã lưu trong Cài đặt). **KHÔNG phải khai lại** — lưu một nơi trong Kachi là gọi được từ Telegram.

### 3.2 Nhạc
| Gửi | Kachi làm |
|---|---|
| `/play https://youtube.com/...` | Xe đang chạy → mở nhạc **ngay**. Xe chưa lên → đặt chờ, phát khi lên xe. |

Chỉ nhận link `http(s)`.

### 3.3 Điều khiển xe *(cần bật "Cho phép điều khiển xe")*
| Gửi | Kachi làm |
|---|---|
| `/ac on` / `/ac off` | Bật/tắt gió tự động |
| `/defrost on` | Sấy kính |
| `/fan 3` | Quạt gió mức 3 |
| `/temp 22` | Nhiệt độ 22°C |
| `/seat 2` | Ghế (mát/sưởi) mức 2 |
| `/readlight on` | Đèn đọc sách |
| `/filter on` | Lọc bụi PM2.5 |

**An toàn**: cốp, cửa kính, cửa sổ trời **chỉ mở khi xe đang DỪNG** — dù lệnh từ Telegram. Và các bộ phận chuyển động này **bắt buộc nói rõ** `open`/`close` (vd `/trunk open`), không nhận lệnh mơ hồ.

### 3.4 Khác
| Gửi | Kachi làm |
|---|---|
| `/cancel` | Xoá điểm đến đang chờ |
| `/help` | Bot gửi lại danh sách lệnh |

---

## Phần 4 — Luồng dùng điển hình

**Sáng, trước khi ra xe (đang ở nhà):**
1. Nhắn bot `/go Công ty`, rồi `/play <link nhạc>`.
2. Bot trả *"🧭 Set 'Công ty' — will navigate when you board"*.
3. Ra xe nổ máy → Kachi bắt GPS → **tự mở dẫn đường tới Công ty** + nhạc phát. Không chạm màn.

**Ngồi ghế sau, không lái:**
- `/temp 24`, `/ac on`, `/play ...` — điều khiển trực tiếp, không phiền tài xế.

---

## Giới hạn & lưu ý

- **Bản cá nhân, KHÔNG OTA** — chỉ cài tay bằng `adb install -r apk/Kachi-3.1.15-release.apk`.
- **Chưa đo trên xe thật** — mọi thứ đã kiểm off-car + máy ảo + test xanh; hành vi GPS/polling/điều khiển trên đầu xe BYD cần chính bạn test thực tế.
- Token Telegram = chìa khoá điều khiển xe. Để lộ = người khác điều khiển được. Mất điện thoại → vào Cài đặt Kachi tắt "Bật điều khiển qua Telegram", hoặc `/revoke` token ở @BotFather.
- Poll 60s tốn chút pin + mạng nền khi xe bật màn. Không cần thì tắt công tắc.
