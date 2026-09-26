# Findings — log VOICE bản 1.79 trên xe (2026-09-19, 30 case)

> **Trạng thái**: Historical · **Cập nhật**: 2026-09-19 · **Mục đích**: findings từ log voice bản 1.79 trên xe (2026-09-19, 30 ca).

> **Nguồn**: `logs/20260919/kachi-voice-…zip` (VoiceWavProbe, 30 phiên unique). **Chỉ có `.wav` + `.json` (parse + reply), KHÔNG có logcat** ⇒ thấy được ASR nghe gì + intent parse ra gì + câu reply, NHƯNG **không thấy app nav nào được bắn** (which app / geocode) — đó là chỗ A còn cần logcat.
> **[ĐO] CONFIRM xe đang 1.79**: `silence_ms` = **608–624ms** ở gần hết case (không còn 150–174 của 1.76) ⇒ **fix endpoint 150→600 ĐÃ chạy trên xe**.

## B — VOICE COMMAND NGẮN vs DÀI (owner: "gmaps ngắn ok, dài không work") — CHỐT
**Endpoint fix (600ms) ĐÃ chạy đúng — câu dài KHÔNG bị cắt nữa. "Dài không work" = ASR NGHE SAI địa chỉ dài + câu rất dài chạm trần 8s.**
- **NGẮN chạy tốt**: "dẫn đường đến tòa nhà bitco"→Nav(tòa nhà bitco)✓ · "1294 võ văn kiệt"→Nav✓ · "cơ quan"→NavigateSaved✓.
- **DÀI bị ASR bóp méo** (endpoint bắt đủ, sp=3000-6000ms, nhưng model garble): người nói *"số 164 xẹt 11 đường Mạn Thiện"* → nghe **"một trăm sáu mươi bốn `sẹt` mười một đường `man thiện`"** · các case khác: "mang thiển" · "ma đang thiệ" · "sẹt". GMaps nhận địa chỉ rác → không ra đường ⇒ "dài không work".
- **RẤT DÀI chạm trần 8s**: `55-968` heard "…164 sẹt 11 đường man thiện" ep=**8000** sil=**0** (nói liên tục, không có 600ms im → cắt ở trần cứng).
- **Phụ**: `47-067` "dẫn đường đến" (cụt) sp=1164 → ngừng >600ms SAU chữ "đến" TRƯỚC khi nói địa chỉ → endpoint bắn → KHÔNG HIỂU (user pacing).
→ **Gốc B = giới hạn MODEL** (zipformer-vi bóp méo địa chỉ đường phố dài/hiếm) + **trần 8s**, KHÔNG phải endpoint. Endpoint đã đúng.
→ **Options**: (a) NGẮN/landmark/saved-place chạy tốt → khuyên user **lưu địa chỉ hay đi** ("dẫn đường tới nhà/công ty"); (b) địa chỉ đường phố dài = khó cho ASR on-device → cần **cloud ASR cho điểm đến** (khi có mạng) hoặc model lớn hơn; (c) nới trần 8s cho câu nav (địa chỉ dài cần >8s) — cân nhắc.

## A — VIETMAP / MAP (owner: "hỏi đường vietmap chưa work")
1. **Parse "dùng vietmap dẫn đường đến X" ĐÃ ĐÚNG** (`00-898`): tách app-selector "dùng vietmap" ở ĐẦU câu → Nav(1294 võ văn kiệt) → "✓ Dẫn đường tới…". Fix parser 1.79 chạy. **NHƯNG** WavProbe không ghi app đích ⇒ **không chốt được vietmap có thật dẫn không** (geocode → toạ độ → vietmaplive://). Owner báo "chưa work" ⇒ [SUY] geocode hỏng (xe không GMS → Nominatim) HOẶC rơi về gmaps. **CẦN LOGCAT** (ActivityTaskManager START) để chốt — chỗ duy nhất còn thiếu của A.
2. **"mở ứng dụng vietmap" → mở NGĂN KÉO, không mở VietMap** (`00-641` nghe "vietmap line"→launcher_apps · `07-387` "vietp line"→launcher_apps · `02-116` "vietma"→KHÔNG HIỂU). Gốc: **ASR bóp méo tên app tự chế "vietmap"** → không khớp app-launch-by-name → rơi về "mở ứng dụng" (drawer). **Fix off-car**: thêm biến thể mờ "vietmap"/"vietma"/"việt map"/"vietp"/"vietmap line" vào synonym mở-app.

## Phụ (line-by-line, off-car fix được):
- **`38-259` "mở điều hòa hai mươi lăm độ" → Control(ac_auto=1)** (mất "25 độ") → phải **đặt inside_temp=25**. Parse "điều hòa X độ" đi nhầm sang ac_auto (còn ac_auto ghi hỏng trên xe).
- **`44-477`/`03-849` "tìm bài hát X" → KHÔNG HIỂU** nhưng "mở/phát bài hát X" → Media(QUERY)✓ ⇒ thêm **"tìm"** làm động từ nhạc.
- **Music resolver CHẠY**: "mở bài hát X" → Media(QUERY) → "đang tìm bài…" (resolver 1.79 UA desktop kích hoạt). Có PHÁT không = cần logcat/xe.
- **`20-447` "khóa cửa"→lock ✗ · `24-472` "bật gạt mưa"→wiper ✗** = "xe không nhận lệnh" (HAL lock/wiper hỏng trên trim — nợ cũ).
- **"mã youtube" → OpenApp(YouTube)✓** (nghe "mã" vẫn khớp mờ YouTube).

## Việc rút ra
- **B**: endpoint OK; "dài" là model — không sửa được off-car thêm; khuyên saved-place + cân nhắc nới trần nav + cloud-ASR điểm đến. 🚗 (không code thêm off-car).
- **A**: (a) thêm fuzzy "vietmap" cho mở-app **(off-car làm được)**; (b) geocode/dispatch vietmap **cần logcat** (WavProbe không đủ) 🚗.
- **Quick off-car**: "tìm bài hát" verb · "điều hòa X độ"→temp · fuzzy vietmap app-name. Gộp vào bản sau (cùng Hey Kachi).
