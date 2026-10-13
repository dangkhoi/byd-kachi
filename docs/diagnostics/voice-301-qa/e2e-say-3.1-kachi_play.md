### T1 — chữ → ý định → thi hành (`say`)

| id | lớp | câu | intent | reply | hỏi lại | tác dụng phụ | kết quả |
|---|---|---|---|---|---|---|---|
| t01 | toggle | bật đèn đọc | Control · Bật đèn đọc | ✗ Bật đèn đọc — xe không nhận lệnh |  | - | PASS |
| t02 | toggle | tắt đèn đọc | Control · Tắt đèn đọc | ✗ Tắt đèn đọc — xe không nhận lệnh |  | - | PASS |
| t03 | toggle | bật lọc bụi | Control · Bật lọc bụi | ✗ Bật lọc bụi — xe không nhận lệnh |  | - | PASS |
| t04 | toggle | tắt điều hoà | Control · Tắt gió tự động | ✗ Tắt gió tự động — xe không nhận lệnh |  | - | PASS |
| t05 | toggle | bật sưởi ghế | Control · Bật sưởi ghế lái mức 1 | ✗ Bật sưởi ghế lái mức 1 — xe không nhận lệnh |  | - | PASS |
| t06 | toggle | mở cửa sổ trời | Control · Mở cửa sổ trời | ✗ Mở cửa sổ trời — đã huỷ | Mở cửa sổ trời? ⏎ mở cửa sổ trời — mưa v… | - | PASS |
| t07 | gone | khoá xe | Unknown · Tính năng khoá xe đã bỏ khỏi Kachi — dùng màn hình của xe | Tính năng khoá xe đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t09 | gone | dừng chiếu cụm | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "dừng chi… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "dừng chiếu cụm" |  | - | PASS |
| t10 | cover | mở kính trước trái | Control · Mở kính lái | ✗ Mở kính lái — xe không nhận lệnh |  | - | PASS |
| t11 | cover | đóng kính trước trái | Control · Đóng kính lái | ✗ Đóng kính lái — xe không nhận lệnh |  | - | PASS |
| t12 | cover | mở kính bên lái | Control · Mở kính lái | ✗ Mở kính lái — xe không nhận lệnh |  | - | PASS |
| t13 | cover-confirm | mở hết kính | Macro · Chạy gói Mở hết kính | ✗ Chạy gói Mở hết kính — đã huỷ | Chạy gói Mở hết kính? ⏎ hạ hết kính | - | PASS |
| t14 | macro | đóng hết kính | Macro · Chạy gói Đóng hết kính | Đóng hết kính: xe không nhận lệnh nào |  | - | PASS |
| t15 | step | nhiệt độ hai mươi bốn độ | Control · Đặt nhiệt độ 24 | ✗ Đặt nhiệt độ 24 — xe không nhận lệnh |  | - | PASS |
| t16 | step | đặt nhiệt độ 24 độ | Control · Đặt nhiệt độ 24 | ✗ Đặt nhiệt độ 24 — xe không nhận lệnh |  | - | PASS |
| t17 | step | tăng gió | Control · Tăng gió 1 nấc | ✗ Đặt gió mức 5 — xe không nhận lệnh |  | - | PASS |
| t18 | gone | giảm âm lượng | Unknown · Tính năng âm lượng đã bỏ khỏi Kachi — dùng màn hình của xe | Tính năng âm lượng đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t20 | gone | đặt độ sáng màn 8 | Unknown · Tính năng độ sáng màn đã bỏ khỏi Kachi — dùng màn hình của x… | Tính năng độ sáng màn đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t23 | gone | màu đèn viền xanh lá | Unknown · Tính năng đèn viền đã bỏ khỏi Kachi — dùng màn hình của xe | Tính năng đèn viền đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t24 | button | lọc ngay | Control · Bấm lọc ngay | ✗ Bấm lọc ngay — xe không nhận lệnh |  | - | PASS |
| t26 | gone | mở khoá cửa | Unknown · Tính năng mở khoá cửa đã bỏ khỏi Kachi — dùng màn hình của x… | Tính năng mở khoá cửa đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t28 | gone | rời xe | Unknown · Tính năng gói rời xe đã bỏ khỏi Kachi — dùng màn hình của xe | Tính năng gói rời xe đã bỏ khỏi Kachi — dùng màn hình của xe |  | - | PASS |
| t30 | launcher | mở cài đặt | Launcher · Mở cài đặt | ✓ Đã mở cài đặt |  | - | PASS |
| t31 | launcher | mở ứng dụng | Launcher · Mở ứng dụng | ✓ Đã mở ứng dụng |  | - | PASS |
| t32 | launcher | nói với xe | Launcher · Mở nói với xe | ✓ Đã mở nói với xe |  | - | PASS |
| t33 | read | xem pin | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t34 | read | pin còn bao nhiêu | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t35 | read | nhiệt độ ngoài trời bao nhiêu | Read · Xem Nhiệt ngoài xe | Nhiệt ngoài xe: chưa đọc được |  | - | PASS |
| t36 | read | đọc tầm hoạt động | Read · Xem Tầm hoạt động EV | Tầm hoạt động EV: chưa đọc được |  | - | PASS |
| t37 | read-mismatch | xem đèn đọc | Unknown · Việc đó không đi với thứ đó — thử nêu mức, hoặc đổi động từ:… | Việc đó không đi với thứ đó — thử nêu mức, hoặc đổi động từ: "xem đèn … |  | - | PASS |
| t38 | app | mở YouTube | OpenApp · Mở ứng dụng YouTube | ✓ Đã mở YouTube |  | #0 mResumedActivity: ActivityRecord{d4aff77 u0… | PASS |
| t39 | app | mở Maps | OpenApp · Mở ứng dụng Maps | ✓ Đã mở Maps |  | #0 mResumedActivity: ActivityRecord{e1c89ab u0… | PASS |
| t40 | app | mở ứng dụng YouTube | OpenApp · Mở ứng dụng YouTube | ✓ Đã mở YouTube |  | #0 mResumedActivity: ActivityRecord{4ec7753 u0… | PASS |
| t41 | app-close | đóng YouTube | Unknown · Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app k… | Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app khác đè lên… |  | - | PASS |
| t42 | app-close | tắt YouTube | Unknown · Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app k… | Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app khác đè lên… |  | - | PASS |
| t43 | app-slot | đưa YouTube vào ô hai | OpenApp · Mở ứng dụng YouTube vào ô 2 | ✓ Đã mở YouTube vào ô 2 |  | app:com.google.android.youtube | PASS |
| t44 | app-slot | mở YouTube vào ô số 9 | OpenApp · Mở ứng dụng YouTube vào ô 9 | ✗ Mở ứng dụng YouTube vào ô 9 — bố cục hiện chỉ có 3 ô |  | - | PASS |
| t45 | app | mở bản đồ | OpenApp · Mở ứng dụng Google Maps | ✓ Đã mở Maps |  | - | PASS |
| t46 | media | phát nhạc | Media · Phát nhạc | ✓ Đã phát nhạc |  | package=com.android.server.telecom | PASS |
| t47 | media | dừng nhạc | Media · Dừng nhạc | ✓ Đã dừng nhạc |  | - | PASS |
| t48 | media | bài tiếp theo | Media · Chuyển bài tiếp theo | ✓ Đã chuyển bài tiếp theo |  | - | PASS |
| t49 | media | bài trước | Media · Quay lại bài trước | ✓ Đã quay lại bài trước |  | - | PASS |
| t50 | media | mở nhạc trên YouTube Music | Media · Phát nhạc trên YouTube Music | ✓ Đã phát nhạc trên YouTube Music |  | - | PASS |
| t51 | media-open-vocab | phát bài Diễm Xưa | Media · Tìm bài «Diễm Xưa» | ✗ Tìm bài «Diễm Xưa» — đã huỷ | Tìm bài «Diễm Xưa»? ⏎ đoạn trong ngoặc d… | - | PASS |
| t52 | media-open-vocab | mở bài Diễm Xưa trên YouTube Music | Media · Tìm bài «Diễm Xưa» trên YouTube Music | Tìm bài «Diễm Xưa» trên YouTube Music — đang tìm bài… ⏎ ✓ Đã tìm bài «… |  | #0 mResumedActivity: ActivityRecord{3cf2640 u0… | PASS |
| t53 | nav | dẫn đường đến Bitexco | Nav · Dẫn đường tới Bitexco | ✓ Đã bắt đầu dẫn đường tới Bitexco |  | - | PASS |
| t54 | nav | dẫn đường tới chợ Bến Thành bằng Waze | Nav · Dẫn đường tới chợ Bến Thành trên Waze | ✓ Đã bắt đầu dẫn đường tới chợ Bến Thành trên Waze |  | #0 mResumedActivity: ActivityRecord{49cd14d u0… | PASS |
| t55 | nav | chỉ đường đến sân bay bằng google map | Nav · Dẫn đường tới sân bay trên Google Maps | ✓ Đã bắt đầu dẫn đường tới sân bay trên Google Maps |  | - | PASS |
| t56 | nav | dẫn đường | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "dẫn đườn… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "dẫn đường" |  | - | PASS |
| t57 | profile | đổi sang hồ sơ QA2 | Profile · Đổi sang hồ sơ QA2 | ✗ Đổi sang hồ sơ QA2 — đã huỷ | Đổi sang hồ sơ QA2? ⏎ đổi hồ sơ thay toà… | - | PASS |
| t58 | profile | chuyển sang hồ sơ QA2 | Profile · Đổi sang hồ sơ QA2 | ✓ Đã đổi sang hồ sơ QA2 |  | QA2 | PASS |
| t59 | layout | đổi bố cục 4 ô | Layout · Bố cục 4 ô | ✓ Đã chuyển sang bố cục 4 ô |  | QUAD | PASS |
| t60 | layout | bố cục hai ô | Layout · Bố cục 2 cột | ✓ Đã chuyển sang bố cục 2 cột |  | TWO_COL | PASS |
| t60b | layout | về bố cục 2 hàng | Layout · Bố cục 2 hàng | ✓ Đã chuyển sang bố cục 2 hàng |  | TWO_ROW | PASS |
| t61 | compound | bật đèn đọc và tắt lọc bụi | Control,Control · Bật đèn đọc / Tắt lọc bụi | ✗ Bật đèn đọc — xe không nhận lệnh ⏎ ✗ Tắt lọc bụi — xe không nhận lện… |  | - | PASS |
| t62 | compound-gone | mở khoá cửa rồi bật đèn đọc | Control,Unknown · Bật đèn đọc / Đã bỏ qua vế không hiểu: "mở khoá cửa" | ✗ Bật đèn đọc — xe không nhận lệnh ⏎ Đã bỏ qua vế không hiểu: "mở khoá… |  | - | PASS |
| t63 | unknown | hôm nay trời đẹp quá | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "hôm nay trờ… | Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "hôm nay trời đẹp quá" |  | - | PASS |
| t64 | unknown | kể cho tôi nghe một câu chuyện | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "kể cho tôi … | Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "kể cho tôi nghe một c… |  | - | PASS |
| t65 | unknown | bật abcxyz | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "bật abcx… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "bật abcxyz" — Nếu … |  | - | PASS |
| t66 | unknown | kachi ơi | Unknown · Chưa có câu lệnh nào: "kachi ơi" | Chưa có câu lệnh nào: "kachi ơi" |  | - | PASS |
| t67 | unknown | xem xe | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "xem xe" | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "xem xe" |  | - | PASS |
| t68 | follow-up-off | bật đèn đọc | Control · Bật đèn đọc | ✗ Bật đèn đọc — xe không nhận lệnh |  | - | PASS |
| t69 | chip-labels | bật đèn đọc | Control · Bật đèn đọc | ✗ Bật đèn đọc — xe không nhận lệnh |  | False | PASS |
| t70 | app-vi | mở gu gồ máp | OpenApp · Mở ứng dụng Google Maps | ✓ Đã mở Maps |  | #0 mResumedActivity: ActivityRecord{8a4a38d u0… | PASS |
| t71 | app-vi | mở gu gồ mép | OpenApp · Mở ứng dụng Google Maps | ✓ Đã mở Maps |  | - | PASS |
| t72 | app-vi | mở google map | OpenApp · Mở ứng dụng Google Maps | ✓ Đã mở Maps |  | - | PASS |
| t73 | app-vi | mở du túp | OpenApp · Mở ứng dụng du túp | ✓ Đã mở YouTube |  | #0 mResumedActivity: ActivityRecord{6cfdd8a u0… | PASS |
| t74 | app-vi | mở iu túp | OpenApp · Mở ứng dụng iu túp | ✓ Đã mở YouTube |  | #0 mResumedActivity: ActivityRecord{6cfdd8a u0… | PASS |
| t75 | app-vi | mở việt máp | OpenApp · Mở ứng dụng VietMap | ✓ Đã mở VIETMAP LIVE |  | #0 mResumedActivity: ActivityRecord{d2d3c59 u0… | PASS |
| t76 | app-vi | mở quây | OpenApp · Mở ứng dụng quây | ✓ Đã mở Waze |  | #0 mResumedActivity: ActivityRecord{49cd14d u0… | PASS |
| t77 | app-vi | mở du túp miu dích | OpenApp · Mở ứng dụng YouTube Music | ✓ Đã mở YT Music |  | - | PASS |
| t78 | app-vi | mở bản đồ google | OpenApp · Mở ứng dụng Google Maps | ✓ Đã mở Maps |  | #0 mResumedActivity: ActivityRecord{8a4a38d u0… | PASS |
| t79 | app-phonetic | mở chát gi pi ti | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở chát … | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở chát gi pi ti" … |  | - | PASS |
| t80 | app-phonetic | mở za lô | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở za lô… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở za lô" — Nếu đó… |  | - | PASS |
| t81 | tester | bật điều hoà | Control · Bật gió tự động | ✗ Bật gió tự động — xe không nhận lệnh |  | - | PASS |
| t82 | tester | bật lọc bụi | Control · Bật lọc bụi | ✗ Bật lọc bụi — xe không nhận lệnh |  | - | PASS |
| t83 | tester-clarify | lọc | Unknown · Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "lọc" | Chưa rõ cần làm gì — thử "bật…", "mở…", "xem…": "lọc" |  | - | PASS |
| t84 | tester-mishear | các bụi | Control · Bật lọc bụi | ✗ Bật lọc bụi — xe không nhận lệnh |  | - | PASS |
| t85 | tester-mishear | đọc ngày | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "đọc ngày… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "đọc ngày" |  | - | PASS |
| t86 | nam | bật kiếng lái lên | Control · Mở kính lái | ✗ Mở kính lái — xe không nhận lệnh |  | - | PASS |
| t87 | nam | mở cửa hậu | Control · Mở cốp sau | ✗ Mở cốp sau — xe không nhận lệnh |  | - | PASS |
| t88 | nam | tắt đèn nóc | Control · Tắt đèn đọc | ✗ Tắt đèn đọc — xe không nhận lệnh |  | - | PASS |
| t89 | nam | mở máy lọc không khí | Control · Bật lọc bụi | ✗ Bật lọc bụi — xe không nhận lệnh |  | - | PASS |
| t90 | nam | mở quạt ghế | Control · Bật mát ghế lái mức 1 | ✗ Bật mát ghế lái mức 1 — xe không nhận lệnh |  | - | PASS |
| t91 | nam | tăng nhiệt độ máy lạnh lên | Control · Tăng nhiệt độ 1 nấc | ✗ Đặt nhiệt độ 23 — xe không nhận lệnh |  | - | PASS |
| t92 | step | giảm gió | Control · Giảm gió 1 nấc | ✗ Đặt gió mức 3 — xe không nhận lệnh |  | - | PASS |
| t93 | step | tăng nhiệt độ | Control · Tăng nhiệt độ 1 nấc | ✗ Đặt nhiệt độ 23 — xe không nhận lệnh |  | - | PASS |
| t94 | misspell | mở cấp sau | Control · Mở cốp sau | ✗ Mở cốp sau — xe không nhận lệnh |  | - | PASS |
| t95 | misspell | ở cấp sau | Control · Mở cốp sau | ✗ Mở cốp sau — xe không nhận lệnh |  | - | PASS |
| t96 | misspell | mở góc sau | Control · Mở cốp sau | ✗ Mở cốp sau — xe không nhận lệnh |  | - | PASS |
| t97 | misspell | xem bên | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t98 | misspell | biên còn bao nhiêu | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t99 | misspell | bin còn bao nhiêu | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t100 | misspell | xem tin | Read · Xem Pin (SOC) | Pin (SOC): chưa đọc được |  | - | PASS |
| t101 | misspell | bằng ghế sưởi | Control · Bật sưởi ghế lái mức 1 | ✗ Bật sưởi ghế lái mức 1 — xe không nhận lệnh |  | - | PASS |
| t102 | misspell | bật ghế sửi | Control · Bật sưởi ghế lái mức 1 | ✗ Bật sưởi ghế lái mức 1 — xe không nhận lệnh |  | - | PASS |
| t103 | misspell | dần nhạc | Media · Dừng nhạc | ✓ Đã dừng nhạc |  | - | PASS |
| t104 | misspell | mật độ hai mươi hai độ | Control · Đặt nhiệt độ 22 | ✗ Đặt nhiệt độ 22 — xe không nhận lệnh |  | - | PASS |
| t105 | misspell | mở kim trước trái | Control · Mở kính lái | ✗ Mở kính lái — xe không nhận lệnh |  | - | PASS |
| t106 | misspell | mở kín trước trái | Control · Mở kính lái | ✗ Mở kính lái — xe không nhận lệnh |  | - | PASS |
| t108 | misspell | bật lọt bụi | Control · Bật lọc bụi | ✗ Bật lọc bụi — xe không nhận lệnh |  | - | PASS |
| t109 | confirm | mở cốp | Control · Mở cốp sau | ✗ Mở cốp sau — đã huỷ | Mở cốp sau? ⏎ mở cốp khi xe đang đỗ nơi … | - | PASS |
| t110 | confirm | mở cửa sổ trời | Control · Mở cửa sổ trời | ✗ Mở cửa sổ trời — đã huỷ | Mở cửa sổ trời? ⏎ mở cửa sổ trời — mưa v… | - | PASS |
| t111 | compound-confirm | mở cốp rồi bật đèn đọc | Control,Control · Mở cốp sau / Bật đèn đọc | ✗ Mở cốp sau — đã huỷ, 1 việc sau không chạy | Mở cốp sau? ⏎ mở cốp khi xe đang đỗ nơi … | - | PASS |
| t112 | confirm | mở 4 kính | Control · Mở 4 kính | ✗ Mở 4 kính — đã huỷ | Mở 4 kính? ⏎ hạ HẾT 4 kính — mưa, bụi, h… | - | PASS |
| t113 | toggle | hạ bốn kính | Control · Mở 4 kính | ✗ Mở 4 kính — xe không nhận lệnh |  | - | PASS |
| t114 | confirm-neg | đóng cửa sổ trời | Control · Đóng cửa sổ trời | ✗ Đóng cửa sổ trời — xe không nhận lệnh |  | - | PASS |
| t115 | confirm-neg | mở cửa sổ trời | Control · Mở cửa sổ trời | ✗ Mở cửa sổ trời — xe không nhận lệnh |  | - | PASS |
| t116 | end | bai bai | EndSession · Tạm biệt, hẹn gặp lại | Tạm biệt, hẹn gặp lại |  | - | PASS |
| t117 | end | gút bai | EndSession · Tạm biệt, hẹn gặp lại | Tạm biệt, hẹn gặp lại |  | - | PASS |
| t118 | end | tạm biệt Kachi | EndSession · Tạm biệt, hẹn gặp lại | Tạm biệt, hẹn gặp lại |  | - | PASS |
| t119 | end | cảm ơn nhé | EndSession · Tạm biệt, hẹn gặp lại | Tạm biệt, hẹn gặp lại |  | - | PASS |
| t120 | end | thoát đi | EndSession · Tạm biệt, hẹn gặp lại | Tạm biệt, hẹn gặp lại |  | - | PASS |
| t121 | end | thôi được rồi | EndSession · Tạm biệt, hẹn gặp lại | Tạm biệt, hẹn gặp lại |  | - | PASS |
| t122 | end | xong rồi cảm ơn | EndSession · Tạm biệt, hẹn gặp lại | Tạm biệt, hẹn gặp lại |  | - | PASS |
| t123 | end | không cần nữa | EndSession · Tạm biệt, hẹn gặp lại | Tạm biệt, hẹn gặp lại |  | - | PASS |
| t124 | end | good bye | EndSession · Tạm biệt, hẹn gặp lại | Tạm biệt, hẹn gặp lại |  | - | PASS |
| t125 | end-neg | tắt đi | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "tắt đi" | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "tắt đi" |  | - | PASS |
| t126 | end-neg | đóng đi | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "đóng đi" | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "đóng đi" |  | - | PASS |
| t127 | end-neg | thôi lấy gió ngoài | Control · Tắt lấy gió trong | ✗ Tắt lấy gió trong — xe không nhận lệnh |  | - | PASS |
| t128 | end-neg | đúng rồi | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "đúng rồi… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "đúng rồi" |  | - | PASS |
| t129 | compound-noconn | tắt sưởi ghế phụ tắt gió tự động | Control,Control · Tắt sưởi ghế phụ / Tắt gió tự động | ✗ Tắt sưởi ghế phụ — xe không nhận lệnh ⏎ ✗ Tắt gió tự động — xe không… |  | - | PASS |
| t130 | compound-noconn | sưởi ghế phụ bật gió tự động | Control,Control · Bật sưởi ghế phụ mức 1 / Bật gió tự động | ✗ Bật sưởi ghế phụ mức 1 — xe không nhận lệnh ⏎ ✗ Bật gió tự động — xe… |  | - | PASS |
| t131 | slot-clarify | mở diếp vào ô số hai | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở diếp … | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở diếp vào ô số h… |  | - | PASS |
| t132 | slot-clarify | mở nep leag vào ô số một | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở nep l… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "mở nep leag vào ô … |  | - | PASS |
| t133 | cop-gop | đóng góp | Control · Đóng cốp sau | ✗ Đóng cốp sau — xe không nhận lệnh |  | - | PASS |
| t134 | cop-gop | đóng góp sau | Control · Đóng cốp sau | ✗ Đóng cốp sau — xe không nhận lệnh |  | - | PASS |
| t135 | cop-gop-neg | đóng góp ý kiến | Unknown · Không tìm thấy thứ đó trong xe hay trong ứng dụng: "đóng góp… | Không tìm thấy thứ đó trong xe hay trong ứng dụng: "đóng góp ý kiến" |  | - | PASS |
| t136 | car1010 | đóng kính lái xong bật đèn độc | Control,Control · Đóng kính lái / Bật đèn đọc | ✗ Đóng kính lái — xe không nhận lệnh ⏎ ✗ Bật đèn đọc — xe không nhận l… |  | - | PASS |
| t137 | car1010 | sưởi ghế phụ động | Control · Bật sưởi ghế phụ mức 1 | ✗ Bật sưởi ghế phụ mức 1 — xe không nhận lệnh |  | - | PASS |
| t138 | compound-fallback | đóng lái xong bật đèn đọc | Control,Unknown · Bật đèn đọc / Đã bỏ qua vế không hiểu: "đóng lái" | ✗ Bật đèn đọc — xe không nhận lệnh ⏎ Đã bỏ qua vế không hiểu: "đóng lá… |  | - | PASS |
| t139 | nav-multi | dẫn đường đến chợ Bến Thành bằng Google Maps, … | Nav,Nav,Nav · Dẫn đường tới chợ Bến Thành trên Google Maps / Dẫn đường… | ✓ Đã bắt đầu dẫn đường tới chợ Bến Thành trên Google Maps ⏎ Dẫn đường … |  | - | PASS |
| t140 | nav-multi | dẫn đường đến chợ bến thành bằng google map vi… | Nav,Nav,Nav · Dẫn đường tới chợ bến thành trên Google Maps / Dẫn đường… | ✓ Đã bắt đầu dẫn đường tới chợ bến thành trên Google Maps ⏎ Dẫn đường … |  | - | PASS |
| t141 | nav-by-provider | dẫn đường đến chợ bến thành bằng ways | Nav · Dẫn đường tới chợ bến thành trên Waze | ✓ Đã bắt đầu dẫn đường tới chợ bến thành trên Waze |  | - | PASS |
| t142 | nav-multi-neg | dẫn đường đến bến xe bằng xe máy và waze | Nav · Dẫn đường tới bến xe bằng xe máy và waze | ✓ Đã bắt đầu dẫn đường tới bến xe bằng xe máy và waze |  | - | PASS |

**T1: 135/135 PASS** (0 FAIL)

