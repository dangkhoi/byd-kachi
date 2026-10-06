# Phương pháp đo tương phản chữ trên ảnh chụp máy ảo (QA)

> **Trạng thái**: Current · **Cập nhật**: 2026-10-06 · **Loại**: Diagnostics (phương pháp đo + bằng chứng phép đo cũ sai) ·
> **Mục đích**: backlog `QA-CONTRAST-METHOD` — chốt MỘT cách đo tỉ lệ tương phản chữ/nền từ ảnh chụp màn hình cho mọi lượt QA
> máy ảo, để không còn báo "dưới 4,5 : 1" oan như QA1 (04/10).
>
> Nhãn: **[ĐO]** = đo trên ảnh/mã · **[SUY]** = suy luận.

## 1. Vì sao cần — phép đo QA1 sai thế nào

[ĐO, spec `specs/kachi-287-look-and-keys.html` mục R-TS1 + bảng QA §6] QA1 (04/10) báo hai chữ chủ đề sáng dưới 4,5 : 1:
chữ *"— · ngoài xe"* (3,18–3,57 : 1) và dấu `−` của nút quạt (3,59–3,82 : 1). Đo lại trên CHÍNH 6 ảnh QA1 bằng cách ở §2:
**4,91–5,88 : 1** (MUT sáng `#4C5869` trên thẻ kính) và **13,57 : 1** (INK trên nút) — cả hai đạt, không sửa mã.

Gốc sai: QA1 lấy **phân vị 98 của khoảng cách màu tới nền** làm "màu chữ". Nét chữ mảnh chiếm rất ít điểm của hộp — dấu
`−` chỉ **9/920 điểm (≈ 1 %)** — nên phân vị 98 rơi vào điểm NỀN/điểm viền khử răng cưa, không phải lõi nét chữ ⇒ màu "chữ"
bị kéo về phía nền ⇒ tỉ lệ thấp giả. Chữ càng mảnh/càng nhỏ, phép đo phân vị càng sai.

## 2. Cách đo chuẩn (bắt buộc cho QA máy ảo)

1. **Hộp chữ**: lấy hộp của đúng view chữ (`uiautomator dump` hoặc `dumpsys activity top` — bounds), KHÔNG lấy cả thẻ.
   Hộp phải chứa nền quanh chữ (≥ vài điểm mỗi phía) để trung vị là nền.
2. **Màu nền** = **trung vị từng kênh** (R, G, B) của MỌI điểm trong hộp. Nét chữ hiếm khi vượt 30 % hộp nên trung vị là nền;
   nếu chữ đậm/lớn chiếm > 40 % hộp (con số to), nới hộp ra hoặc lấy trung vị một dải nền cạnh chữ.
3. **Màu chữ** = **lõi nét chữ**: các điểm có độ chói tương đối (WCAG) **xa nền nhất**. Lấy **trung vị từng kênh của k điểm
   xa nền nhất**, `k = max(1, min(⌊2 % số điểm hộp⌋, ⌊n_lõi / 2⌋))`, với `n_lõi` = số điểm có khoảng cách độ chói tới nền
   ≥ ½ khoảng cách lớn nhất. Nét mảnh ⇒ k nhỏ ⇒ gần như đúng điểm đậm nhất; nét dày ⇒ k = 2 % hộp ⇒ chống nhiễu.
   **KHÔNG** dùng phân vị (p98/p95) của cả hộp, và **KHÔNG** dùng *trung bình 2 % điểm xa nhất* khi nét chiếm < 2 % hộp:
   [ĐO ảnh tổng hợp 06/10 — dấu `−` 9 điểm lõi + 2 điểm viền trong hộp 46×20, mực `#14181E` trên nền `#E6E9EE`] cách
   trên ra **14,63 : 1**; trung bình 2 % (k = 18 > 11 điểm nét) ra **3,86 : 1**; phân vị 98 ra **1,00 : 1** — hai cách sau
   đều "đỏ oan" như QA1.
4. **Tỉ lệ** = `(L_sáng + 0,05) / (L_tối + 0,05)`, `L` = độ chói tương đối WCAG 2.x (tuyến tính hoá sRGB) — cùng công thức
   với fixture `core/src/testFixtures/.../testsupport/Wcag.kt` (`luminance`, `ratio`).
5. **Ghi kèm** trong báo cáo QA: hộp (px) · màu nền trung vị · màu chữ đo được · số điểm nét · tỉ lệ. Dải nhiều ảnh ⇒ ghi
   min–max.
6. **Đối chiếu mã** khi tỉ lệ đo < 4,5 : 1 (chữ thường) / 3 : 1 (chữ lớn, icon): tính lại bằng màu KHAI trong mã
   (`KachiTheme`/`KachiPalette`, trộn kênh trong suốt lên nền bằng `Wcag.over`) — bài canh bảng màu đã khoá phần này.
   Ảnh và mã lệch nhau ⇒ báo cả hai con số; chỉ kết luận "lỗi tương phản" khi CẢ HAI dưới ngưỡng, hoặc khi nền thật trên
   ảnh khác nền giả định trong mã (vd ảnh nền xuyên qua kính).

## 3. Mã tham chiếu (Python, chỉ PIL)

```python
from PIL import Image
from statistics import median

def lum(c):  # WCAG 2.x — cùng ngưỡng 0.03928 với Wcag.luminance
    def ch(v):
        s = v / 255.0
        return s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4
    r, g, b = c[:3]
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)

def text_contrast(png, box):  # box = (x0, y0, x1, y1) của view chữ
    img = Image.open(png).convert("RGB").crop(box)
    px = list(getattr(img, "get_flattened_data", img.getdata)())     # Pillow ≥ 12; getdata bỏ ở Pillow 14
    bg = tuple(int(median(p[i] for p in px)) for i in range(3))       # nền = trung vị
    lb = lum(bg)
    dist = sorted(((abs(lum(p) - lb), p) for p in px), key=lambda t: t[0], reverse=True)
    core = sum(1 for d, _ in dist if d >= dist[0][0] / 2)               # điểm lõi nét
    k = max(1, min(len(px) * 2 // 100, core // 2))
    ink = tuple(int(median(p[i] for _, p in dist[:k])) for i in range(3))
    li = lum(ink)
    hi, lo = max(li, lb), min(li, lb)
    return (hi + 0.05) / (lo + 0.05), bg, ink, k
```

Kiểm mã tham chiếu [ĐO 06/10, ảnh tổng hợp]: nét mảnh 9/920 điểm ⇒ 14,63 : 1 (k = 5); nét dày 25×6 ⇒ 14,63 : 1 (k = 18).
(Senior review 06/10 [ĐO Pillow 12.3.0]: `getdata` báo `DeprecationWarning` — bỏ ở Pillow 14 (2027-10-15) ⇒ mã gọi
`get_flattened_data` (cùng kết quả từng điểm — đã so), lùi về `getdata` trên Pillow < 12; chạy lại ra đúng hai số trên.)

## 4. Giới hạn đã biết

- [SUY] Ảnh chụp máy ảo đi qua bộ dựng của máy ảo (gamma, khử răng cưa) — tỉ lệ đo được là của ẢNH, không phải màn xe.
  Trên xe cần chụp màn hình thật (`screencap`) mới kết luận được cho xe.
- [SUY] Chữ trên ảnh nền (kính thật, ảnh xe) có nền không đều: trung vị cả hộp có thể không đại diện ⇒ báo dải (đo vài
  hộp nền cạnh chữ) thay vì một con số.
