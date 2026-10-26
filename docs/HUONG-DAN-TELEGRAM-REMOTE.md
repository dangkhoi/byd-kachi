# Hướng dẫn: Gửi điểm đến và nhạc từ điện thoại qua Telegram

> **Trạng thái**: Current · **Cập nhật**: 2026-10-26 · **Bản**: Kachi 3.1.21 (226) — có trên kênh cập nhật tự động
> **Mục đích**: Từ điện thoại, gửi điểm đến hoặc nhạc cho Kachi qua một bot Telegram. Lên xe là Kachi dẫn đường ngay khi có GPS.
> **Phạm vi**: CHỈ dẫn đường + nhạc. Kachi **không đọc, không gửi, không điều khiển** bất cứ thứ gì của xe qua Telegram.

Mặc định **TẮT**. Không bật thì không có gì chạy.

---

## Phần 1 — Tạo bot Telegram (làm 1 lần, ~3 phút)

### 1.1 Lấy mã bot (token)
1. Mở Telegram, tìm **@BotFather** (có dấu tích xanh).
2. Gõ `/newbot` → đặt tên (vd *Kachi Xe*) → đặt username kết thúc bằng `bot` (vd `kachi_xe_bot`).
3. BotFather trả về một dòng **token** dạng `8123456789:AAH8xZ…k0`. Giữ kín, không gửi cho ai.

### 1.2 Lấy mã chat (chat ID)
Chat ID để Kachi chỉ nghe tin từ bạn.
1. Mở bot vừa tạo → **Start** → gửi đại một tin.
2. Tìm **@userinfobot** → Start → nó trả `Id: 123456789`. Đó là **Mã chat**.

---

## Phần 2 — Khai báo trong Kachi (trên xe, 1 lần)

*Cài đặt › Dẫn đường & cụm đồng hồ › Điều khiển qua Telegram*:

| Dòng | Điền gì |
|---|---|
| **Bật điều khiển qua Telegram** | Gạt BẬT |
| **Mã bot Telegram** | Dán token |
| **Mã chat Telegram** | Dán chat ID |
| **Kiểm tra kết nối** | Bấm — điện thoại nhận *"✅ Kachi connected"* kèm **3 nút dưới ô gõ** |

Khi màn đầu xe sáng, Kachi chờ tin từ Telegram; màn tắt thì ngừng.

---

## Phần 3 — Cách dùng

**Nút dưới ô gõ**: **🧭 Go** (danh sách nơi đã lưu + ➕ New) · **🎵 Music** (bot hỏi tên bài) · **❓ Help**.

### Dẫn đường
| Gửi | Kachi làm |
|---|---|
| Dán **link Google Maps** (nút *Chia sẻ* trong Google Maps → Telegram) | Dẫn bằng Google Maps (app mặc định) |
| Link + `vietmap` (vd `<link> gmaps vietmap`) | Dẫn bằng cả Google Maps lẫn VietMap |
| `/go Công ty` | Nơi trong sổ địa chỉ của Kachi, hoặc địa chỉ chữ — app dẫn đường mặc định |
| `/go Công ty gmaps vietmap` · `/go Công ty gmaps+vietmap` · `/go Công ty + waze` | Dẫn bằng các app ghi ở cuối (`gmaps` · `vietmap` · `waze`) |
| `/go 21.0285, 105.8542` · gửi **vị trí** 📍 | Dẫn tới toạ độ |
| `/go` · `/places` · nút 🧭 Go | Danh sách nơi đã lưu: bấm để dẫn, ➕ New để gõ điểm đến |
| `/cancel` | Xoá điểm đến đang chờ |

- Điểm đến **chỉ bắt đầu dẫn khi xe có GPS**; gửi từ nhà thì lên xe mới dẫn. Hết hạn sau 24 giờ.
- **VietMap chỉ nhận toạ độ.** Link Google Maps và địa chỉ chữ không có toạ độ, nên Kachi tự tra toạ độ từ địa chỉ. Tra không ra thì bỏ VietMap và báo lại; nếu chỉ chọn VietMap thì không đặt điểm đến.

### Nhạc
| Gửi | Kachi làm |
|---|---|
| Dán **link YouTube** | Mở YouTube, phát bài đó ngay (đè bài đang phát) |
| Dán **link YT Music** | Mở YT Music, phát bài đó ngay |
| `/play 1 triệu like` | Tìm bài trên **App nhạc mặc định** (Cài đặt › Giọng nói) rồi phát |
| `/play 1 triệu like youtube` · `… ytmusic` | Tìm và phát trên đúng app đó |
| Nút 🎵 Music | Bot hỏi tên bài, gõ tên là phát |

Màn xe đang tắt thì nhạc được giữ chờ, lên xe mới phát (hết hạn sau 24 giờ).

---

## Giới hạn & lưu ý
- Token Telegram = chìa khoá gửi lệnh dẫn đường/nhạc. Mất điện thoại → tắt công tắc trong Kachi, hoặc `/revoke` token ở @BotFather.
- Chưa đo trên xe thật: chuyển bài khi đang phát, dải tên bài vị trí Giữa trên, bàn phím nút trên Telegram thật.
