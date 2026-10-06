### T1 — chữ → ý định → thi hành (`say`)

| id | lớp | câu | intent | reply | hỏi lại | tác dụng phụ | kết quả |
|---|---|---|---|---|---|---|---|
| t01 | toggle | bật đèn đọc | Control · Bật Đèn đọc | ✗ Bật Đèn đọc — xe không nhận lệnh |  | - | PASS |
| t02 | toggle | tắt đèn đọc | Control · Tắt Đèn đọc | ✗ Tắt Đèn đọc — xe không nhận lệnh |  | - | PASS |
| t03 | toggle | bật lọc bụi | Control · Bật Lọc bụi | ✗ Bật Lọc bụi — xe không nhận lệnh |  | - | PASS |
| t04 | toggle | tắt điều hoà | Control · Tắt Gió tự động | ✗ Tắt Gió tự động — xe không nhận lệnh |  | - | PASS |
| t05 | toggle | bật sưởi ghế | Control · Sưởi ghế lái: Mức 1 | ✗ Sưởi ghế lái: Mức 1 — xe không nhận lệnh |  | - | PASS |
| t06 | toggle | mở cửa sổ trời | Control · Bật Cửa sổ trời | ✗ Bật Cửa sổ trời — đã huỷ | Bật Cửa sổ trời? ⏎ mở cửa sổ trời — mưa … | - | PASS |
| t07 | gone | khoá xe | Unknown · Tính năng khoá xe đã bỏ khỏi Kachi — dùng màn hình của xe | Tính năng khoá xe đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t09 | gone | dừng chiếu cụm | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "dừng chi… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "dừng chiếu cụm" |  | - | PASS |
| t10 | cover | mở kính trước trái | Control · Bật Kính lái | ✗ Bật Kính lái — xe không nhận lệnh |  | - | PASS |
| t11 | cover | đóng kính trước trái | Control · Tắt Kính lái | ✗ Tắt Kính lái — xe không nhận lệnh |  | - | PASS |
| t12 | cover | mở kính bên lái | Control · Bật Kính lái | ✗ Bật Kính lái — xe không nhận lệnh |  | - | PASS |
| t13 | cover-confirm | mở hết kính | Macro · Chạy gói Mở hết kính | ✗ Chạy gói Mở hết kính — đã huỷ | Chạy gói Mở hết kính? ⏎ hạ hết kính | - | PASS |
| t14 | macro | đóng hết kính | Macro · Chạy gói Đóng hết kính | Đóng hết kính: xe không nhận lệnh nào |  | - | PASS |
| t15 | step | nhiệt độ hai mươi bốn độ | Control · Đặt Nhiệt độ = 24 | ✗ Đặt Nhiệt độ = 24 — xe không nhận lệnh |  | - | PASS |
| t16 | step | đặt nhiệt độ 24 độ | Control · Đặt Nhiệt độ = 24 | ✗ Đặt Nhiệt độ = 24 — xe không nhận lệnh |  | - | PASS |
| t17 | step | tăng gió | Control · Tăng Gió 1 nấc | ✗ Đặt Gió = 5 — xe không nhận lệnh |  | - | PASS |
| t18 | gone | giảm âm lượng | Unknown · Tính năng âm lượng đã bỏ khỏi Kachi — dùng màn hình của xe | Tính năng âm lượng đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t20 | gone | đặt độ sáng màn 8 | Unknown · Tính năng độ sáng màn đã bỏ khỏi Kachi — dùng màn hình của x… | Tính năng độ sáng màn đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t23 | gone | màu đèn viền xanh lá | Unknown · Tính năng đèn viền đã bỏ khỏi Kachi — dùng màn hình của xe | Tính năng đèn viền đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t24 | button | lọc ngay | Control · Bấm Lọc ngay | ✗ Bấm Lọc ngay — xe không nhận lệnh |  | - | PASS |
| t26 | gone | mở khoá cửa | Unknown · Tính năng mở khoá cửa đã bỏ khỏi Kachi — dùng màn hình của x… | Tính năng mở khoá cửa đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t28 | gone | rời xe | Unknown · Tính năng gói rời xe đã bỏ khỏi Kachi — dùng màn hình của xe | Tính năng gói rời xe đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t30 | launcher | mở cài đặt | Launcher · Mở Cài đặt | ✓ Mở Cài đặt |  | - | PASS |
| t31 | launcher | mở ứng dụng | Launcher · Mở Ứng dụng | ✓ Mở Ứng dụng |  | - | PASS |
| t32 | launcher | nói với xe | Launcher · Mở Nói với xe | ✓ Mở Nói với xe |  | - | PASS |
| t33 | read | xem pin | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t34 | read | pin còn bao nhiêu | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t35 | read | nhiệt độ ngoài trời bao nhiêu | Read · Xem Nhiệt ngoài xe | Nhiệt ngoài xe: chưa đọc được |  | - | PASS |
| t36 | read | đọc tầm hoạt động | Read · Xem Tầm hoạt động EV | Tầm hoạt động EV: chưa đọc được |  | - | PASS |
| t37 | read-mismatch | xem đèn đọc | Unknown · Việc đó không đi với thứ đó — thử nêu mức, hoặc đổi động từ:… | Việc đó không đi với thứ đó — thử nêu mức, hoặc đổi động từ: "xem đèn … |  | - | PASS |
| t38 | app | mở YouTube | OpenApp · Mở ứng dụng YouTube | ✓ Mở ứng dụng YouTube |  | #0 mResumedActivity: ActivityRecord{2c3bb1 u0 … | PASS |
| t39 | app | mở Maps | OpenApp · Mở ứng dụng Maps | ✓ Mở ứng dụng Maps |  | #0 mResumedActivity: ActivityRecord{fcdfa3 u0 … | PASS |
| t40 | app | mở ứng dụng YouTube | OpenApp · Mở ứng dụng YouTube | ✓ Mở ứng dụng YouTube |  | #0 mResumedActivity: ActivityRecord{2c3bb1 u0 … | PASS |
| t41 | app-close | đóng YouTube | Unknown · Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app k… | Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app khác đè lên… |  | - | PASS |
| t42 | app-close | tắt YouTube | Unknown · Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app k… | Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app khác đè lên… |  | - | PASS |
| t43 | app-slot | đưa YouTube vào ô hai | OpenApp · Mở ứng dụng YouTube vào ô 2 | ✓ Mở ứng dụng YouTube vào ô 2 |  | app:com.google.android.youtube | PASS |
| t44 | app-slot | mở YouTube vào ô số 9 | OpenApp · Mở ứng dụng YouTube vào ô 9 | ✗ Mở ứng dụng YouTube vào ô 9 — bố cục hiện chỉ có 3 ô |  | - | PASS |
| t45 | app | mở bản đồ | OpenApp · Mở ứng dụng Google Maps | ✓ Mở ứng dụng Maps |  | - | PASS |
| t46 | media | phát nhạc | Media · Phát nhạc | ✓ Phát nhạc — đã mở YouTube Music; chưa có phiên nhạc nào để điều khiể… |  | package=com.android.server.telecom | PASS |
| t47 | media | dừng nhạc | Media · Dừng nhạc | ✗ Dừng nhạc — chưa có phiên nhạc nào — mở app nhạc rồi nói lại |  | - | PASS |
| t48 | media | bài tiếp theo | Media · Chuyển bài tiếp theo | ✓ Chuyển bài tiếp theo |  | - | PASS |
| t49 | media | bài trước | Media · Quay lại bài trước | ✓ Quay lại bài trước |  | - | PASS |
| t50 | media | mở nhạc trên YouTube Music | Media · Phát nhạc trên YouTube Music | ✓ Phát nhạc trên YouTube Music |  | - | PASS |
| t51 | media-open-vocab | phát bài Diễm Xưa | Media · Tìm bài «Diễm Xưa» | ✗ Tìm bài «Diễm Xưa» — đã huỷ | Tìm bài «Diễm Xưa»? ⏎ đoạn trong ngoặc d… | - | PASS |
| t52 | media-open-vocab | mở bài Diễm Xưa trên YouTube Music | Media · Tìm bài «Diễm Xưa» trên YouTube Music | Tìm bài «Diễm Xưa» trên YouTube Music — đang tìm bài… ⏎ ✓ Tìm bài «Diễ… |  | #0 mResumedActivity: ActivityRecord{1976910 u0… | PASS |
| t53 | nav | dẫn đường đến Bitexco | Nav · Dẫn đường tới Bitexco | ✓ Dẫn đường tới Bitexco |  | - | PASS |
| t54 | nav | dẫn đường tới chợ Bến Thành bằng Waze | Nav · Dẫn đường tới chợ Bến Thành trên Waze | ✓ Dẫn đường tới chợ Bến Thành trên Waze |  | #0 mResumedActivity: ActivityRecord{fb2d366 u0… | PASS |
| t55 | nav | chỉ đường đến sân bay bằng google map | Nav · Dẫn đường tới sân bay trên Google Maps | ✓ Dẫn đường tới sân bay trên Google Maps |  | - | PASS |
| t56 | nav | dẫn đường | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "dẫn đườn… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "dẫn đường" |  | - | PASS |
| t57 | profile | đổi sang hồ sơ Mặc định 2 | Profile · Đổi sang hồ sơ Mặc định 2 | ✗ Đổi sang hồ sơ Mặc định 2 — đã huỷ | Đổi sang hồ sơ Mặc định 2? ⏎ đổi hồ sơ t… | - | PASS |
| t58 | profile | chuyển sang hồ sơ Mặc định 2 | Profile · Đổi sang hồ sơ Mặc định 2 | ✓ Đổi sang hồ sơ Mặc định 2 |  | Mặc định 2 | PASS |
| t59 | layout | đổi bố cục 4 ô | Layout · Bố cục 4 ô | ✓ Bố cục 4 ô |  | QUAD | PASS |
| t60 | layout | bố cục hai ô | Layout · Bố cục 2 cột | ✓ Bố cục 2 cột |  | TWO_COL | PASS |
| t60b | layout | về bố cục 2 hàng | Layout · Bố cục 2 hàng | ✓ Bố cục 2 hàng |  | TWO_ROW | PASS |
| t61 | compound | bật đèn đọc và tắt lọc bụi | Control,Control · Bật Đèn đọc / Tắt Lọc bụi | ✗ Bật Đèn đọc — xe không nhận lệnh ⏎ ✗ Tắt Lọc bụi — xe không nhận lện… |  | - | PASS |
| t62 | compound-gone | mở khoá cửa rồi bật đèn đọc | Control,Unknown · Bật Đèn đọc / Đã bỏ qua vế không hiểu: "mở khoá cửa" | ✗ Bật Đèn đọc — xe không nhận lệnh ⏎ Đã bỏ qua vế không hiểu: "mở khoá… |  | - | PASS |
| t63 | unknown | hôm nay trời đẹp quá | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "hôm nay trờ… | Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "hôm nay trời đẹp quá" |  | - | PASS |
| t64 | unknown | kể cho tôi nghe một câu chuyện | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "kể cho tôi … | Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "kể cho tôi nghe một c… |  | - | PASS |
| t65 | unknown | bật abcxyz | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "bật abcx… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "bật abcxyz" — Nếu … |  | - | PASS |
| t66 | unknown | kachi ơi | Unknown · Chưa có câu lệnh nào: "kachi ơi" | Chưa có câu lệnh nào: "kachi ơi" |  | - | PASS |
| t67 | unknown | xem xe | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "xem xe" | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "xem xe" |  | - | PASS |
| t68 | follow-up-off | bật đèn đọc | Control · Bật Đèn đọc | ✗ Bật Đèn đọc — xe không nhận lệnh |  | - | PASS |
| t69 | chip-labels | bật đèn đọc | Control · Bật Đèn đọc | ✗ Bật Đèn đọc — xe không nhận lệnh |  | False | PASS |
| t70 | app-vi | mở gu gồ máp | OpenApp · Mở ứng dụng Google Maps | ✓ Mở ứng dụng Maps |  | #0 mResumedActivity: ActivityRecord{879e915 u0… | PASS |
| t71 | app-vi | mở gu gồ mép | OpenApp · Mở ứng dụng Google Maps | ✓ Mở ứng dụng Maps |  | - | PASS |
| t72 | app-vi | mở google map | OpenApp · Mở ứng dụng Google Maps | ✓ Mở ứng dụng Maps |  | - | PASS |
| t73 | app-vi | mở du túp | OpenApp · Mở ứng dụng du túp | ✓ Mở ứng dụng YouTube |  | #0 mResumedActivity: ActivityRecord{2c3bb1 u0 … | PASS |
| t74 | app-vi | mở iu túp | OpenApp · Mở ứng dụng iu túp | ✓ Mở ứng dụng YouTube |  | #0 mResumedActivity: ActivityRecord{2c3bb1 u0 … | PASS |
| t75 | app-vi | mở việt máp | OpenApp · Mở ứng dụng VietMap | ✓ Mở ứng dụng VIETMAP LIVE |  | #0 mResumedActivity: ActivityRecord{6b1b892 u0… | PASS |
| t76 | app-vi | mở quây | OpenApp · Mở ứng dụng quây | ✓ Mở ứng dụng Waze |  | #0 mResumedActivity: ActivityRecord{fb2d366 u0… | PASS |
| t77 | app-vi | mở du túp miu dích | OpenApp · Mở ứng dụng YouTube Music | ✓ Mở ứng dụng YT Music |  | - | PASS |
| t78 | app-vi | mở bản đồ google | OpenApp · Mở ứng dụng Google Maps | ✓ Mở ứng dụng Maps |  | #0 mResumedActivity: ActivityRecord{879e915 u0… | PASS |
| t79 | app-phonetic | mở chát gi pi ti | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở chát … | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở chát gi pi ti" … |  | - | PASS |
| t80 | app-phonetic | mở za lô | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở za lô… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở za lô" — Nếu đó… |  | - | PASS |
| t81 | tester | bật điều hoà | Control · Bật Gió tự động | ✗ Bật Gió tự động — xe không nhận lệnh |  | - | PASS |
| t82 | tester | bật lọc bụi | Control · Bật Lọc bụi | ✗ Bật Lọc bụi — xe không nhận lệnh |  | - | PASS |
| t83 | tester-clarify | lọc | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "lọc" | Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "lọc" |  | - | PASS |
| t84 | tester-mishear | các bụi | Control · Bật Lọc bụi | ✗ Bật Lọc bụi — xe không nhận lệnh |  | - | PASS |
| t85 | tester-mishear | đọc ngày | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "đọc ngày… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "đọc ngày" |  | - | PASS |
| t86 | nam | bật kiếng lái lên | Control · Bật Kính lái | ✗ Bật Kính lái — xe không nhận lệnh |  | - | PASS |
| t87 | nam | mở cửa hậu | Control · Mở Cốp sau | ✗ Mở Cốp sau — xe không nhận lệnh |  | - | PASS |
| t88 | nam | tắt đèn nóc | Control · Tắt Đèn đọc | ✗ Tắt Đèn đọc — xe không nhận lệnh |  | - | PASS |
| t89 | nam | mở máy lọc không khí | Control · Bật Lọc bụi | ✗ Bật Lọc bụi — xe không nhận lệnh |  | - | PASS |
| t90 | nam | mở quạt ghế | Control · Mát ghế lái: Mức 1 | ✗ Mát ghế lái: Mức 1 — xe không nhận lệnh |  | - | PASS |
| t91 | nam | tăng nhiệt độ máy lạnh lên | Control · Tăng Nhiệt độ 1 nấc | ✗ Đặt Nhiệt độ = 23 — xe không nhận lệnh |  | - | PASS |
| t92 | step | giảm gió | Control · Giảm Gió 1 nấc | ✗ Đặt Gió = 3 — xe không nhận lệnh |  | - | PASS |
| t93 | step | tăng nhiệt độ | Control · Tăng Nhiệt độ 1 nấc | ✗ Đặt Nhiệt độ = 23 — xe không nhận lệnh |  | - | PASS |
| t94 | misspell | mở cấp sau | Control · Mở Cốp sau | ✗ Mở Cốp sau — xe không nhận lệnh |  | - | PASS |
| t95 | misspell | ở cấp sau | Control · Mở Cốp sau | ✗ Mở Cốp sau — xe không nhận lệnh |  | - | PASS |
| t96 | misspell | mở góc sau | Control · Mở Cốp sau | ✗ Mở Cốp sau — xe không nhận lệnh |  | - | PASS |
| t97 | misspell | xem bên | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t98 | misspell | biên còn bao nhiêu | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t99 | misspell | bin còn bao nhiêu | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t100 | misspell | xem tin | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t101 | misspell | bằng ghế sưởi | Control · Sưởi ghế lái: Mức 1 | ✗ Sưởi ghế lái: Mức 1 — xe không nhận lệnh |  | - | PASS |
| t102 | misspell | bật ghế sửi | Control · Sưởi ghế lái: Mức 1 | ✗ Sưởi ghế lái: Mức 1 — xe không nhận lệnh |  | - | PASS |
| t103 | misspell | dần nhạc | Media · Dừng nhạc | ✓ Dừng nhạc |  | - | PASS |
| t104 | misspell | mật độ hai mươi hai độ | Control · Đặt Nhiệt độ = 22 | ✗ Đặt Nhiệt độ = 22 — xe không nhận lệnh |  | - | PASS |
| t105 | misspell | mở kim trước trái | Control · Bật Kính lái | ✗ Bật Kính lái — xe không nhận lệnh |  | - | PASS |
| t106 | misspell | mở kín trước trái | Control · Bật Kính lái | ✗ Bật Kính lái — xe không nhận lệnh |  | - | PASS |
| t108 | misspell | bật lọt bụi | Control · Bật Lọc bụi | ✗ Bật Lọc bụi — xe không nhận lệnh |  | - | PASS |
| t109 | confirm | mở cốp | Control · Mở Cốp sau | ✗ Mở Cốp sau — đã huỷ | Mở Cốp sau? ⏎ mở cốp khi xe đang đỗ nơi … | - | PASS |
| t110 | confirm | mở cửa sổ trời | Control · Bật Cửa sổ trời | ✗ Bật Cửa sổ trời — đã huỷ | Bật Cửa sổ trời? ⏎ mở cửa sổ trời — mưa … | - | PASS |
| t111 | compound-confirm | mở cốp rồi bật đèn đọc | Control,Control · Mở Cốp sau / Bật Đèn đọc | ✗ Mở Cốp sau — đã huỷ, 1 việc sau không chạy | Mở Cốp sau? ⏎ mở cốp khi xe đang đỗ nơi … | - | PASS |
| t112 | confirm | mở 4 kính | Control · Bật 4 kính | ✗ Bật 4 kính — đã huỷ | Bật 4 kính? ⏎ hạ HẾT 4 kính — mưa, bụi, … | - | PASS |
| t113 | toggle | hạ bốn kính | Control · Bật 4 kính | ✗ Bật 4 kính — xe không nhận lệnh |  | - | PASS |
| t114 | confirm-neg | đóng cửa sổ trời | Control · Tắt Cửa sổ trời | ✗ Tắt Cửa sổ trời — xe không nhận lệnh |  | - | PASS |
| t115 | confirm-neg | mở cửa sổ trời | Control · Bật Cửa sổ trời | ✗ Bật Cửa sổ trời — xe không nhận lệnh |  | - | PASS |

**T1: 108/108 PASS** (0 FAIL)

### T2 — tiếng → nhận dạng → ý định (`wav`)

| id | câu gốc | heard | ngữ pháp (lượt 1) | tự do (lượt 2) | intent | khớp |
|---|---|---|---|---|---|---|
| w01 | mở YouTube | mở youtube | mở youtube |  | OpenApp · Mở ứng dụng YouTube | ✅ |
| w02 | bật đèn đọc | bật đèn đọc | bật đèn đọc |  | Control · Bật Đèn đọc | ✅ |
| w03 | tắt đèn đọc | tắt đèn đọc | tắt đèn đọc |  | Control · Tắt Đèn đọc | ✅ |
| w04 | mở kính trước trái | mở kính trước trái | mở kính trước trái |  | Control · Bật Kính lái | ✅ |
| w05 | đóng kính trước trái | đóng kính trước trái | đóng kính trước trái |  | Control · Tắt Kính lái | ✅ |
| w06 | nhiệt độ hai mươi bốn độ | nhiệt độ hai mươi bốn độ | nhiệt độ hai mươi bốn độ |  | Control · Đặt Nhiệt độ = 24 | ✅ |
| w07 | dẫn đường đến Bitexco | dẫn đường đến bico | dẫn đường đến bico | dẫn đường đến bico | Nav · Dẫn đường tới bico | ≈ |
| w08 | phát nhạc | phát nhạc | phát nhạc |  | Media · Phát nhạc | ✅ |
| w09 | dừng nhạc | dừng nhạc | dừng nhạc |  | Media · Dừng nhạc | ✅ |
| w10 | đưa YouTube vào ô số hai | đưa youtube vào ô số hai | đưa youtube vào ô số hai |  | OpenApp · Mở ứng dụng YouTube vào ô 2 | ✅ |
| w11 | mở khoá cửa | mở khóa cửa | mở khóa cửa |  | Unknown · Tính năng mở khoá cửa đã bỏ khỏi Kachi — dùn… | ✅ |
| w12 | xem pin | xem pin | xem pin |  | Read · Xem Pin (SOC) | ✅ |
| w13 | tăng âm lượng | tăng âm lửa | tăng âm lửa |  | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng… | ≈ |
| w14 | giảm nhiệt độ | giảm nhiệt độ | giảm nhiệt độ |  | Control · Giảm Nhiệt độ 1 nấc | ✅ |
| w15 | bật sưởi ghế | bật sưởi ghế | bật sưởi ghế |  | Control · Sưởi ghế lái: Mức 1 | ✅ |
| w16 | đóng hết kính | đóng hết kính | đóng hết kính |  | Macro · Chạy gói Đóng hết kính | ✅ |
| w17 | mở cài đặt | mở cài đặt | mở cài đặt |  | Launcher · Mở Cài đặt | ✅ |
| w18 | bài tiếp theo | bài tiếp theo | bài tiếp theo |  | Media · Chuyển bài tiếp theo | ✅ |
| w20 | lọc ngay | lọc ngay | lọc ngay |  | Control · Bấm Lọc ngay | ✅ |
| w21 | hôm nay trời đẹp quá | hôm nay trời đẹp quá | hôm nay trời đẹp quá |  | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem… | ✅ |
| w22 | pin còn bao nhiêu | còn bao nhiêu | còn bao nhiêu |  | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng… | ≈ |
| w23 | bật lọc bụi và tắt đèn đọc | bật lọc bụi và tắt đèn đọc | bật lọc bụi và tắt đèn đọc |  | Control,Control · Bật Lọc bụi / Tắt Đèn đọc | ✅ |
| w24 | dẫn đường tới chợ Bến Thành bằng Waze | dẫn đường tới chợ bến thành bằng loa e | dẫn đường tới chợ bến thành bằng l… | dẫn đường tới chợ bến th… | Nav · Dẫn đường tới chợ bến thành bằng loa e | ≈ |
| w25 | đổi sang hồ sơ Chính | đổi sang hồ sơ chính | đổi sang hồ sơ chính |  | Unknown · Việc đó không đi với thứ đó — thử nêu mức, h… | ✅ |
| w26 | bật đèn đọc | bật đèn đọc | bật đèn đọc |  | Control · Bật Đèn đọc | ✅ |
| w27 | xem pin | xem pin | xem pin |  | Read · Xem Pin (SOC) | ✅ |
| w28 | lọc ngay | lọc ngay | lọc ngay |  | Control · Bấm Lọc ngay | ✅ |

**T2: 23/27 nghe ĐÚNG NGUYÊN VĂN** (xem cột intent cho ca nghe lệch mà ý định vẫn đúng)

