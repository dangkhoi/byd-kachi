#!/usr/bin/env python3
# Sinh ~1000 ca test giọng nói ĐỜI THẬT (owner 2026-09-24 "ngàn ca"). In ra TSV: id<TAB>câu<TAB>kind_mong_đợi.
# Template × biến thể (lịch sự, đọc tắt số nhà, tên đường thật) — KHÔNG gõ tay 1000 dòng.
import random, sys
random.seed(20260924)

def num_vn(n):  # số → đọc tiếng Việt (0..9999) rút gọn kiểu người nói
    ones=["không","một","hai","ba","bốn","năm","sáu","bảy","tám","chín"]
    if n<10: return ones[n]
    if n<100:
        c,d=divmod(n,10); s=("mười" if c==1 else ones[c]+" mươi")
        if d: s+=(" lăm" if d==5 and c>0 else " "+("mốt" if d==1 and c>1 else ones[d]))
        return s
    if n<1000:
        h,r=divmod(n,100); s=ones[h]+" trăm"
        if r: s+=((" lẻ "+ones[r]) if r<10 else " "+num_vn(r))
        return s
    th,r=divmod(n,1000); s=ones[th]+" nghìn"
    if r: s+=" "+(num_vn(r) if r>=100 else "không trăm "+num_vn(r) if r>=10 else "không trăm lẻ "+ones[r])
    return s

STREETS=["hoàng văn thái","điện biên phủ","nguyễn trãi","lý thường kiệt","cách mạng tháng tám",
    "nguyễn văn cừ","huỳnh tấn phát","trần hưng đạo","lê lợi","nguyễn huệ","phạm văn đồng",
    "võ văn kiệt","xa lộ hà nội","ba tháng hai","cộng hòa","hoàng diệu","tô hiến thành"]
PLACES=["chợ bến thành","sân bay tân sơn nhất","bệnh viện chợ rẫy","công viên tao đàn","ga sài gòn",
    "vincom đồng khởi","landmark tám mốt","đại học bách khoa","chợ lớn","nhà thờ đức bà","bến xe miền đông"]
POLITE_PRE=["","làm ơn ","cho tôi ","giúp tôi ","làm ơn cho tôi "]
POLITE_POST=["",""," nhé"," giúp tôi"," đi"]
NAV_VERB=["dẫn đường đến","dẫn tới","dẫn đến","chỉ đường tới","đưa tôi đến","dẫn đường tới"]

CONTROLS=[  # (nhãn nói, kind)
 ("bật đèn đọc","Control"),("tắt đèn đọc","Control"),("mở điều hòa","Control"),("tắt điều hòa","Control"),
 ("bật ghế mát","Control"),("tắt ghế mát","Control"),("bật sưởi ghế","Control"),("mở kính","Control"),
 ("đóng kính","Control"),("mở cửa sổ trời","Control"),("đóng cửa sổ trời","Control"),("đóng cốp","Control"),
 ("bật lọc bụi","Control"),("tắt lọc bụi","Control"),("bật đèn pha","Control"),("tắt đèn pha","Control"),
 ("tắt đèn ban ngày","Control"),("tăng nhiệt độ","Control"),("giảm nhiệt độ","Control"),("tăng gió","Control"),
 ("giảm gió","Control"),("tăng âm lượng","Control"),("giảm âm lượng","Control"),("mở hết kính","Macro"),("đóng hết kính","Macro")]
READS=[("nhiệt độ bao nhiêu","Read"),("pin còn bao nhiêu","Read"),("xem tốc độ","Read"),("bụi mịn bao nhiêu","Read"),
 ("nhiệt độ ngoài trời bao nhiêu","Read"),("mức xăng còn bao nhiêu","Read"),("quãng đường đã chạy bao nhiêu","Read"),
 ("còn bao nhiêu cây số","Read"),("tốc độ hiện tại thế nào","Read"),("nhiệt độ trong xe thế nào","Read")]
MEDIA=[("mở nhạc sơn tùng","Media"),("phát bài hạ còn vương nắng","Media"),("mở nhạc trẻ","Media"),("dừng nhạc","Media"),
 ("bài tiếp theo","Media"),("bài trước","Media"),("tạm dừng","Media"),("phát nhạc trịnh","Media"),("mở nhạc trên youtube","Media")]
LAUNCH=[("mở youtube","Launcher"),("mở cài đặt","Launcher"),("mở danh sách ứng dụng","Launcher")]
END=[("tạm biệt","EndSession"),("xong rồi","EndSession"),("cảm ơn","EndSession"),("thôi","EndSession"),
 ("đủ rồi","EndSession"),("thoát","EndSession"),("cảm ơn nhé","EndSession"),("dừng lại","EndSession")]

rows=[]
def add(s,k): rows.append((s.strip(),k))

# 1) Control × lịch sự (25×5×3 ~ 375, lấy mẫu)
for lbl,k in CONTROLS:
    for pre in POLITE_PRE:
        for post in random.sample(POLITE_POST,2):
            add(pre+lbl+post,k)
# 2) Nav địa chỉ số nhà (verb × số × đường)
for _ in range(160):
    v=random.choice(NAV_VERB); n=random.choice([random.randint(1,99),random.randint(100,999),random.randint(1000,9999)])
    st=random.choice(STREETS); add(f"{v} số {num_vn(n)} đường {st}","Nav")
# 3) Nav địa danh
for _ in range(90):
    add(f"{random.choice(NAV_VERB)} {random.choice(PLACES)}","Nav")
# 4) Read/Media/Launcher/End × lịch sự
for pool in (READS,MEDIA):
    for lbl,k in pool:
        for pre in random.sample(POLITE_PRE,3): add(pre+lbl,k)
for lbl,k in LAUNCH:
    for pre in POLITE_PRE: add(pre+lbl,k)
for lbl,k in END:
    for post in POLITE_POST: add(lbl+post,k)

random.shuffle(rows)
rows=rows[:1000]
for i,(s,k) in enumerate(rows): print(f"g{i:04d}\t{s}\t{k}")
