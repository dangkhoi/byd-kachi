# Hướng dẫn: Điều khiển Kachi từ xa qua Telegram

> **Trạng thái**: Current · **Cập nhật**: 2026-10-26 · **Bản**: Kachi 3.1.19 (224) — bản cá nhân, KHÔNG OTA
> **Mục đích**: Từ điện thoại, gửi điểm đến / mở nhạc / điều khiển xe qua một bot Telegram. Lên xe là Kachi tự dẫn đúng đường ngay khi có GPS; đang ngồi sau cũng bấm điều khiển mà không phiền tài xế.

APK: `apk/Kachi-3.1.19-CHUA-DANG.apk` (sha256 `52d1b7ba…`, chỉ nằm trên máy dựng — **không** vào git, tên tệp cố ý không khớp kênh OTA). Bản nén để chuyển đi: `~/Desktop/Kachi-3.1.19-CHUA-DANG.zip` (24 MB, giải nén ra đúng tệp APK trên). Cài đè bản cũ bằng `adb install -r` — **giữ nguyên dữ liệu** (cùng khoá ký).

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
| **Cho phép điều khiển xe qua Telegram** | **Mặc định BẬT.** Gạt TẮT nếu chỉ muốn dẫn đường + nhạc — khi tắt, mọi lệnh và nút điều khiển xe (`/ac`, bảng AC/Seat/More…) đều bị từ chối |

Xong. Khi màn đầu xe đang sáng, Kachi giữ một kết nối chờ tin với Telegram (mỗi lượt chờ tối đa 60 giây, có tin là nhận ngay). Màn tắt thì ngừng hỏi. Mạng lỗi hoặc token sai thì lùi chờ 5 giây → 15 giây → 1 phút → 5 phút rồi thử lại.

> **Lệnh xe gửi lúc Kachi không nghe sẽ bị BỎ.** Telegram giữ tin chưa nhận tới 24 giờ. Nếu bạn bấm nút hay gõ lệnh **điều khiển xe** (cốp, kính, điều hoà, ghế…) lúc xe tắt / mất mạng, lần sau Kachi nhận lại thì **không làm**, chỉ nhắn *"Ignored an old car command…"* — để cốp không tự bung lên lúc bạn đã quên. Lệnh gõ chữ cũ hơn 2 phút cũng bị bỏ. **Điểm đến và nhạc thì KHÔNG bị bỏ** — gửi từ nhà, lên xe mới dẫn/phát là đúng ý.
>
> Điểm đến chờ lưu theo **hồ sơ tài xế đang dùng**: đổi sang hồ sơ khác trước khi lên xe thì điểm đến đó không chạy.

> **Lưu ý GPS**: điểm đến gửi qua Telegram **chỉ bắt đầu dẫn khi xe đã bắt được GPS**. Vừa nổ máy / trong hầm chưa có định vị → Kachi **CHỜ**, có GPS lúc nào dẫn lúc đó (không dẫn mù).

---

## Phần 3 — Cách dùng (gửi lệnh cho bot)

> **Cách DỄ NHẤT: gõ `/menu`** — bot hiện **bảng nút bấm**, mọi thứ chọn bằng cách nhấn, không phải nhớ lệnh (bản 3.1.16+). Đầu bảng là **trạng thái sống** (xe nối/GPS/điều hoà/ghế/điểm đến/nhạc). Bố cục:
> - Hàng nhanh: 🏠 **nơi ghim 1** · 🏢 **nơi ghim 2** · ❄️ **Cool 24°** (bật AUTO + 24° + quạt 4 một chạm)
> - Nhóm: 🧭 Navigate · ❄️ AC · 🎵 Music · 🪑 Seat · 🌫 More · 🔄 Refresh (vẽ lại trạng thái)
> - **AC**: bấm 20°/22°/24°/26° (hoặc −/+), quạt Off/Low/Mid/High, AUTO, sấy kính
> - **Music**: ⏮ ⏸/▶️ ⏭ điều khiển nhạc **đang phát** (bản 3.1.16+)
> - **Seat**: Cool 1/2 · Heat 1/2 · Tắt (ghế chỉ có 3 mức Tắt/1/2) — cho ghế lái + phụ
> - **More**: đèn đọc · lọc bụi PM2.5 · mở/đóng cốp · mở/đóng hết kính (cốp/kính chỉ mở khi xe ở số P)
> - Gõ **`/places`** để ghim nơi vào hàng nhanh (nút 📌). Gõ **`/`** để Telegram gợi ý 9 lệnh.
>
> Sau mỗi lần bấm, dòng **↳** dưới trạng thái là **câu trả lời của chính xe** (vd *"Đã đặt Nhiệt độ = 24"*). Cốp/kính chạy mô-tơ lâu nên có thể chỉ ra *"sent, no confirmation from the car yet"* — nghĩa là lệnh đã gửi, chưa đọc lại được kết quả.
>
> Mọi nút là **lệnh tuyệt đối** (bấm "24°" là đúng 24°, bấm lại vẫn 24° — an toàn khi mạng chập chờn). Cốp/kính chỉ mở khi xe ở số P, dù bấm nút. Sau mỗi lệnh gõ, bot đưa lại bảng nút xuống cuối chat (nếu bạn đã mở `/menu`).

Nhắn thẳng vào bot trên Telegram. **Lệnh bắt đầu bằng `/`**, tiếng Anh; **tên nơi để tiếng Việt thoải mái**.

### 3.1 Dẫn đường
| Gửi | Kachi làm |
|---|---|
| `/go Công ty` | Đặt điểm đến **"Công ty"** (lấy từ **sổ địa chỉ đã lưu** trong Kachi). Lên xe + có GPS → dẫn ngay bằng **app dẫn đường mặc định**. Thắng lịch dẫn-tự-động của chuyến đó. |
| `/go Công ty vietmap` | Dẫn bằng **VietMap**. Chọn app ở cuối lệnh: `gmaps` / `vietmap` / `waze`. |
| `/go Công ty gmaps+vietmap` | Dẫn bằng **nhiều app cùng lúc** (ngăn bằng `+`). |
| `/go` hoặc `/places` | Bot gửi **danh sách nơi đã lưu**: bấm tên nơi là dẫn, bấm 📌 là ghim/bỏ ghim vào hàng nhanh (tối đa 2). |
| `/go 21.0285, 105.8542` | Dẫn tới toạ độ trực tiếp (VietMap cũng nhận được vì có toạ độ). |
| `/home` · `/office` | Dẫn tới nơi **đã ghim** số 1 / số 2 (ghim bằng `/places` → 📌). Chưa ghim thì bot nhắc ghim, không tự chọn nơi khác. |
| `/go 1 Đại Cồ Việt Hà Nội` | Địa chỉ chữ không có trong sổ → **Google Maps** tự tra rồi dẫn. ⚠ **VietMap không nhận địa chỉ chữ** (chỉ nhận toạ độ): gõ `/go <địa chỉ chữ> vietmap`, hoặc đặt VietMap làm app mặc định, thì bot vẫn trả "Set …" nhưng **lên xe sẽ không dẫn**. Với địa chỉ chữ hãy dùng `gmaps` (hoặc `gmaps+vietmap`), hoặc gửi toạ độ / vị trí. |
| Gửi **vị trí** (ghim 📎 → Location) | Dẫn tới đúng điểm đó. |

> **Sổ địa chỉ dùng chung**: Telegram đọc **đúng sổ địa chỉ của Kachi** (Nhà / Công ty / … bạn đã lưu trong Cài đặt). **KHÔNG phải khai lại** — lưu một nơi trong Kachi là gọi được từ Telegram.
> **Chọn app**: chỉ 3 alias `gmaps` · `vietmap` · `waze`, đặt ở **cuối** lệnh. Gõ sai → Kachi coi là một phần tên nơi (không đoán bừa). Không ghi app → dùng app mặc định (đổi ở Cài đặt → *App dẫn đường mặc định*).

### 3.2 Nhạc
| Gửi | Kachi làm |
|---|---|
| `/play https://youtube.com/...` | Mở nhạc từ **link** — hệ tự chọn app theo URL: `youtube.com` → YouTube, `music.youtube.com` → YT Music. Xe đang chạy → mở ngay; chưa lên → đặt chờ. |
| `/play Chúng ta của hiện tại` | Gửi **tên bài** (không phải link) → tìm trên **App nhạc mặc định** (Cài đặt → Giọng nói → *App nhạc mặc định*); chưa đặt → mặc định YouTube. |

Link chỉ nhận `http(s)`. Tên bài mở ở màn tìm kiếm của app nhạc (bấm Play nếu app không tự phát).

### 3.3 Điều khiển xe *(cần bật "Cho phép điều khiển xe")*
| Gửi | Kachi làm |
|---|---|
| `/ac on` / `/ac off` | Bật/tắt gió tự động |
| `/defrost on` | Sấy kính |
| `/fan 3` | Quạt gió mức 3 |
| `/temp 22` | Nhiệt độ 22°C |
| `/seat 2` | **Mát** ghế lái mức 2 (`/seat 0` tắt). Sưởi ghế và ghế phụ: dùng bảng **🪑 Seat** trong `/menu` |
| `/readlight on` | Đèn đọc sách |
| `/filter on` | Lọc bụi PM2.5 |

**An toàn**: cốp, cửa kính, cửa sổ trời **chỉ mở khi xe đang ở số P** (đỗ hẳn) — dừng đèn đỏ ở số D cũng **không** mở, vì người bấm trên điện thoại không thấy xe. Không đọc được số thì cũng không mở. **Đóng** thì luôn được. Và các bộ phận chuyển động này **bắt buộc nói rõ** `open`/`close` (vd `/trunk open`), không nhận lệnh mơ hồ.

### 3.4 Khác
| Gửi | Kachi làm |
|---|---|
| `/menu` | Mở bảng nút bấm (kèm trạng thái sống) |
| `/status` | Như `/menu` — xem nhanh trạng thái xe |
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

- **Bản cá nhân, KHÔNG OTA** — chỉ cài tay bằng `adb install -r apk/Kachi-3.1.19-CHUA-DANG.apk` (hoặc giải nén file zip trên Desktop rồi cài).
- **VietMap + địa chỉ chữ chưa dẫn được** (xem bảng 3.1) — điểm đến đó nằm chờ, thử lại mỗi phút trong 24 giờ mà không báo. Dùng `gmaps` hoặc gửi toạ độ.
- ⚠ **Bản 3.1.18 sập khi mở Cài đặt › Dẫn đường & cụm đồng hồ** (lỗi màu ở mục Telegram) — đã sửa ở 3.1.19, cài đè bản mới.
- **Chưa đo trên xe thật** — mọi thứ đã kiểm off-car + máy ảo + test xanh; hành vi GPS/polling/điều khiển trên đầu xe BYD cần chính bạn test thực tế.
- Token Telegram = chìa khoá điều khiển xe. Để lộ = người khác điều khiển được. Mất điện thoại → vào Cài đặt Kachi tắt "Bật điều khiển qua Telegram", hoặc `/revoke` token ở @BotFather.
- Poll 60s tốn chút pin + mạng nền khi xe bật màn. Không cần thì tắt công tắc.
