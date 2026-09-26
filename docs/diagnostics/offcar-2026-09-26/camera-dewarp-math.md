# Nắn fisheye camera gương — TOÁN, bộ số mặc định, hợp đồng uniform (off-car 2026-09-26)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-26 (off-car, **chưa lên xe**) · **Mục đích**: chốt phần **thuần**
> của phương án B (nắn fisheye bằng shader): công thức, đơn vị, bộ số mặc định và **vì sao** là bộ số ấy, hợp đồng
> uniform cho agent làm đường kết xuất, và kế hoạch kiểm trên máy ảo. Không một dòng nào ở đây cần xe — nhưng cũng
> **không một con số nào ở đây được coi là sự thật về xe** cho tới khi có một khung `5120×960` chụp từ đầu xe.
>
> Liên quan: `diagnostics/electro-camera-RE-2026-09-26.md` (§2.1 bố cục dải · §3.2 toàn văn shader E · §3.3 toán ·
> §3.4 bảng uniform · §6.2 phương án B · §7 Q6/Q13/Q17), `diagnostics/offcar-2026-09-26/camera-aspect-and-render-path.md`
> (khung đúng tỉ lệ + đường kết xuất), `diagnostics/camera-lag-analysis-2026-09-26.md` (nguồn giật),
> `specs/camera-turn-signal-hal-socket.html` (R7 xoay · R8).
>
> Mã: `core/src/main/kotlin/com/byd/clusternav/launcher/camera/CameraDewarp.kt` ·
> `CameraDewarpShader.kt` · `CameraDewarpTestPattern.kt` · test `core/src/test/.../camera/CameraDewarpTest.kt`.

---

## 0. Bốn dòng kết luận

1. Công thức nắn là **[ĐO]** — đọc nguyên văn từ `.rodata` của Electro (RE §3.2): đích **phối cảnh thẳng**
   (`r = F·tan θ`), nguồn **fisheye đẳng khoảng** (`r = f·θ`), nối bằng `atan` rồi `mix`.
2. Bốn con số `F`/`K`/`SCALE`/`AMOUNT` của Electro là **[CHƯA BIẾT]** (nằm trong bytecode VMP, RE §7 Q6) ⇒ Kachi
   **không copy số**, mà suy bộ mặc định từ hình học và đưa cả bốn ra chip Cài đặt.
3. Bộ mặc định là **[ĐOÁN]**: dựa trên "ống kính đẳng khoảng 190°, vòng ảnh ≈ bề cao khung". Cả hai giả định chỉ
   chốt được bằng **một khung `5120×960` chụp từ xe**.
4. Kachi sửa **ba** chỗ Electro làm thiếu: có hiệu chỉnh tỉ lệ khung, tâm quang tham số hoá (kiểu kinex), soi gương
   có hiệu lực ở nhánh pano. Và dùng **uniform** thay vì nướng số vào nguồn GLSL (Electro phải biên dịch lại
   program mỗi lần đổi tham số — RE §3.5).

---

## 1. Ba không gian toạ độ — đừng trộn

| Không gian | Ký hiệu | Miền | Là gì |
|---|---|---|---|
| **dst** | `(u, v)` | `[0,1]²` | toạ độ trên ô ra (`vTexCoord` của quad). Chưa xoay |
| **local** | `(a, b)` | `[0,1]²` ở ca thường | dst **sau khi xoay**. Phép nắn làm việc ở đây, và `uSrcRect` ánh xạ nó ra texture ⇒ **local vừa là đích vừa là nguồn** (đúng thiết kế Electro, RE §3.2 bước 4) |
| **p-space** | `p` | thực | local dời về tâm quang, quy đổi **đẳng hướng**. Đơn vị = **nửa bề ngang ô**: `1.0` = nửa bề ngang ô theo pixel |

Mọi bán kính — `F`, `K·SCALE`, `r_dst`, `r_src` — đều **trong đơn vị nửa-bề-ngang-ô**. Đây là chỗ dễ sai nhất của cả
tính năng, nên nói lại: `F = 0.452` **không** phải "0,452 pixel" mà là "0,452 × nửa bề ngang của ô đang vẽ".

---

## 2. Công thức

```
p      = ( (a − cx)·2 , (b − cy)·2 / aspect )        // p-space, đẳng hướng
r_dst  = |p| ;   dir = p / r_dst
theta  = atan( r_dst / F )                           // đích phối cảnh thẳng ⇒ r_dst = F·tan θ
r_src  = (K · SCALE) · theta                         // nguồn fisheye đẳng khoảng ⇒ r_src = f·θ
proj   = ( cx + dir.x·r_src·0.5 , cy + dir.y·r_src·0.5·aspect )
out    = mix( local , proj , clamp(amount, 0, 1) )
```

`aspect` = bề ngang/bề cao ô **theo pixel NGUỒN**. `aspect == 1` ⇒ công thức thu về **đúng** Electro, không lệch một
phép nhân nào (bài `aspect 1 trung khit nguyen van Electro` ghim điều đó).

Ba điều phải nói kèm:

- **Chiều ánh xạ là đích → nguồn** (kiểu lấy mẫu), không phải nguồn → đích.
- `theta = atan2(r_dst, F)` chỉ bằng `atan(r_dst/F)` khi **`F > 0`** (RE §3.3 ràng buộc 4) — `enabled` canh chỗ này.
- `amount` là một phép **TRỘN**, không phải mô hình: `r_eff = (1−a)·r_dst + a·K·SCALE·atan(r_dst/F)`. Chỉ `a == 1`
  mới thật sự là equidistant; ở giữa thì **không thuộc mô hình quang học có tên nào**. Vì thế mặc định là `1.0` —
  thanh trượt tồn tại để owner *hạ* xuống nếu nắn quá tay, không phải để đi tìm một mô hình khác.

### 2.1 Ví dụ bằng số — ô = trọn một dải `1280×960`, bộ mặc định

`aspect = 4/3`, `F = K = 0,452335`, `SCALE = 1`, `amount = 1`, tâm `(0.5, 0.5)`. Vòng ảnh giả định bán kính `480 px`.

| Điểm ra `(u,v)` | `r_dst` | `theta` | `r_src` | Nguồn `(a,b)` | Pixel nguồn | Lệch tâm | `r/480` | `θ` kiểm lại |
|---|---|---|---|---|---|---|---|---|
| giữa cạnh phải `(1, 0.5)` | `1.000` | `65,66°` | `0,51838` | `(0,75919, 0,5)` | `(971,8 , 480,0)` | `331,8 px` | `0,6912` | `0,6912·95° = 65,66°` ✅ |
| giữa cạnh dưới `(0.5, 1)` | `0.750` | `58,91°` | `0,46504` | `(0,5 , 0,81003)` | `(640,0 , 777,6)` | `297,6 px` | `0,6201` | `0,6201·95° = 58,91°` ✅ |
| góc dưới-phải `(1, 1)` | `1.250` | `70,11°` | `0,55347` | `(0,72139, 0,72139)` | `(923,4 , 692,5)` | `354,2 px` | `0,7380` | `0,7380·95° = 70,11°` ✅ |

Cột cuối là **phép tự kiểm**: bán kính pixel chia bán kính vòng ảnh, nhân `θmax = 95°`, phải ra lại đúng `theta` —
đó chính là định nghĩa "đẳng khoảng". Hai cột `Nguồn (a,b)` ở hàng góc bằng nhau là do `aspect` quy đổi lại trục y;
theo **pixel** thì lệch là `(283,4 , 212,6)`, tỉ lệ `4:3`, tức đúng hướng tia — không bị ellipse như Electro.

Với `amount = 0.5`, điểm `(1, 0.5)` cho `a = 0,87959` thay vì `0,75919` (trộn nửa đường giữa `1.0` và `0,75919`).

### 2.2 Vành ngoài của vòng ảnh KHÔNG với tới được — và đó không phải lỗi

Với `amount = 1`, `theta < 90°` luôn ⇒ `r_src < K·SCALE·π/2 = 0,71053` (≈ `454,7 px`). Vòng ảnh tới `480 px`, tức
**vành 25 px ngoài cùng không có điểm ra nào chiếu tới** — vì đó là các tia ≥ 90°, mà phối cảnh thẳng thì `tan 90° = ∞`.
Muốn thấy vùng ấy thì phải đổi mô hình đích (không còn là rectilinear), không phải đổi số.
`CameraDewarp.forwardSrcToDst` trả **`null`** cho các điểm này, và bài `amount 1 chi voi toi tia 90 do` khoá lại.

---

## 3. Bộ số mặc định — suy từ đâu, và ở mức bằng chứng nào

Hai giả định, **cả hai là [ĐOÁN]**:

1. Ống kính **đẳng khoảng**, FOV toàn phần **190°** ⇒ `θmax = 95° = 1,658063 rad`.
2. **Vòng ảnh ≈ bề cao khung** ⇒ trên dải `1280×960`, bán kính vòng ảnh `480 px` = `0,75` đơn vị nửa-bề-ngang.

```
K = 0,75 / 1,658063 = 0,452335        (bán kính nguồn trên mỗi radian)
SCALE = 1
F = K · SCALE = 0,452335              ⇒ độ phóng tại TÂM = 1
amount = 1
tâm = (0,5 , 0,5) cho ô = trọn một dải
```

**Vì sao chọn `F = K·SCALE`**: khi ấy `r_src ≈ r_dst` ở gần tâm ⇒ chính giữa khung, ảnh nắn và ảnh thô **trùng nhau**,
nên thanh Độ nắn chỉ đổi vùng biên. Owner kéo thanh trượt mà tâm hình không nhảy là điều kiện để "chỉnh bằng mắt"
có nghĩa. Hệ quả hình học: nửa-FOV ngang của khung ra `= atan(1/0,452335) = 65,66°` (FOV ngang ≈ **131°**), và ở góc
ô `4:3` phép nắn với tới tia `70,1°`, tức `354 px` — **trong** vòng ảnh `480 px`, không lọt vào vùng đen.

### 3.1 ⚠ `K` và `F` TỈ LỆ NGHỊCH với bề ngang ô

Cả hai đo bằng nửa-bề-ngang-ô, nên **thu ô còn một nửa thì `K` và `F` phải gấp đôi**. Đổi crop mà quên đổi hai số này
là cách chắc chắn nhất để nắn sai. Đừng bê hằng — gọi `DewarpParams.derive(rectWidthPx, rectHeightPx, …)`.
Bài `K va F ti le nghich voi be ngang o` khoá quy tắc này.

Hệ quả cho crop gương hiện tại của Kachi (`x[0.25, 0.35]` của ảnh `5120×960`, tức ô `512×960`):

```
derive(rectWidthPx = 512, rectHeightPx = 960, imageCircleDiameterPx = 960)
  ⇒ K = F = 960/512 / 1,658063 = 1,13084        (đúng 2,5× bộ của dải đầy — vì 1280/512 = 2,5)
  ⇒ aspect = 512/960 = 0,5333
  ⇒ tâm: centerInCrop(0.375, 0.5, crop) = (1,25 , 0,5)   ← NGOÀI ô, và đó là bình thường
```

Tâm nằm ngoài ô vì tâm quang của dải 1 (`x = 0,375`, kinex `Y0/C0094o.java:76`) không nằm trong crop `[0.25, 0.35]`.
Electro ghim tâm ở `(0.5, 0.5)` nên **không làm được ca này**; Kachi theo kinex (RE §6.2).

### 3.2 Khung PNG chụp từ xe sẽ đổi những gì

| Đo được gì từ khung `5120×960` | Đổi tham số nào | Đổi thế nào |
|---|---|---|
| Bán kính vòng ảnh thật (viền tối bắt đầu ở đâu) | `K` | `K = (R_px / nửa-bề-ngang-ô) / θmax`. Nếu vòng ảnh **không** bằng bề cao khung thì `0,452335` sai theo đúng tỉ lệ ấy |
| Tâm quang thật của mỗi dải (tâm vòng ảnh, không phải tâm dải) | `centerX/centerY` | `centerInCrop(tâm_thật, crop)`. Kinex nói tâm dải 1 = `0,375` — đó là **[SUY]**, khung thật mới chốt |
| Ống kính là đẳng khoảng hay **equisolid** (RE §3.3 cảnh báo) | `K` (xấp xỉ) hoặc mô hình | Equisolid `r = 2f·sin(θ/2)` lệch khỏi equidistant chủ yếu ở vùng biên; nếu biên vẫn cong sau khi nắn thì đó là dấu hiệu. Sửa đúng thì cần thêm một mô hình — **ngoài scope 2.74**, ghi backlog |
| Dải nào là gương trái/phải (RE §7 Q1/Q2) | không đổi tham số nắn | đổi `crop` trong `CamView` |
| FOV thật của ống kính (nếu tra được thông số) | `K` | `derive(lensFovDeg = …)` |

---

## 4. Chip Cài đặt — owner chỉnh gì bằng mắt

Năm chip, đúng bộ tham số của Electro cộng tâm (RE §6.2). Tất cả đi bằng **uniform** ⇒ kéo thanh trượt **không** phải
biên dịch lại shader.

| Chip | Tham số | Mặc định | Tăng lên thì sao | Dấu hiệu đang sai |
|---|---|---|---|---|
| **Độ nắn** | `amount` | `1,0` | `0` = y ảnh thô 2.73; `1` = nắn đủ | Nắn quá tay (mép bị kéo dãn) ⇒ hạ xuống `0,6–0,8` |
| **Tiêu cự** | `focal` (`F`) | `0,452` | `F` **lớn** = FOV ra **hẹp** (phóng to, ít méo còn lại); `F` nhỏ = rộng, kéo mạnh | Vật ở giữa to/nhỏ bất thường; nhãn hiện `atan(1/F)` = nửa-FOV để owner đọc được bằng độ |
| **Phóng** | `scale` (`SCALE`) | `1,0` | `> 1` = **với sâu hơn** vào fisheye ⇒ thấy **rộng hơn** (thu nhỏ). Ngược trực giác, phải ghi rõ trên UI | Viền đen ở góc ⇒ đã với ra ngoài vòng ảnh, hạ xuống |
| **K** | `k` | `0,452` | hệ số f-theta của ống kính. Sai `K` ⇒ **đường thẳng vẫn cong** dù đã nắn | Chỉnh `K` tới khi cột đèn/vạch kẻ đường thành thẳng — đây là chip "đúng/sai", ba chip kia là "thẩm mỹ" |
| **Tâm** | `centerX/centerY` | theo crop | dời quang tâm | Nắn **lệch** (một bên thẳng, bên kia còng) ⇒ tâm sai, không phải `K` sai |

Thứ tự chỉnh khuyên dùng trên xe: **tâm → K → tiêu cự → phóng → độ nắn**. Chỉnh `K` trước `F` vì `K` quyết định
"thẳng hay không", `F` chỉ quyết định "rộng hay hẹp".

---

## 5. Hợp đồng cho agent làm đường kết xuất

`CameraDewarpShader.program()` trả `(vertex, fragment)`. `CameraDewarpShader.UNIFORMS` là tập tên phải gán — và
`declaredUniforms()` đọc lại từ chính văn bản GLSL để bài test so hai bên (thêm uniform mà quên khai = đỏ).

| Uniform | Kiểu | Gán bằng gì |
|---|---|---|
| `uTex` | `samplerExternalOES` | texture OES của `SurfaceTexture` |
| `uTexMatrix` | `mat4` | `SurfaceTexture.getTransformMatrix`. **RE §7 Q17 chưa biết** ma trận thật của camera id 1 ⇒ shader áp **vô điều kiện**, và `:app` truyền **ma trận đơn vị** khi đo ra rằng không nên áp. Không có cờ, không có nhánh |
| `uSrcRect` | `vec4 (x,y,w,h)` | `CameraDewarp.srcRect(crop, flipH, flipV)` từ `CamView.crop`. **`w`/`h` âm = soi gương** |
| `uRotation` | `float` độ | `CameraSignalPolicy.rotationDegrees`, **dương = cùng chiều kim đồng hồ trên màn** |
| `uAmount`, `uFocal`, `uK`, `uScale` | `float` | `DewarpParams` **đã `clamped()`** |
| `uAspect` | `float` | `abs(uSrcRect.z)·texW / (abs(uSrcRect.w)·texH)` — **pixel NGUỒN**, trị tuyệt đối khi soi gương |
| `uCenter` | `vec2` | `CameraDewarp.centerInCrop(...)`. **Được phép ngoài `[0,1]`** |

Attribute: `aPosition` (`vec4`), `aTexCoord` (`vec2`) — một quad toàn khung, `TRIANGLE_STRIP` 4 đỉnh.

**Quy ước xoay — đọc kỹ chỗ này.** Shader xoay trong **không gian ô đã chuẩn hoá**, không phải pixel. Đó không phải
lựa chọn tuỳ ý: nhân ba bước của `CameraOverlayTransform` (crop → xoay quanh tâm view → bù tỉ lệ `(vw/vh, vh/vw)` khi
xoay ±90) thì phần xoay+bù **rút gọn đúng thành phép xoay chuẩn hoá** — `90° ⇒ (a,b) = (dy, 1−dx)`,
`180° ⇒ (1−dx, 1−dy)`, `270° ⇒ (1−dy, dx)`. Nhờ vậy đường shader và đường ma trận hiện tại **cùng một hình học**,
không phải hai thứ trông giống nhau. Chỉ **bội của 90** là đẳng hình; góc lẻ sẽ xiên ô không vuông và đẩy góc ra
ngoài `[0,1]` ⇒ shader trả đen (bài `xoay goc le thi xien o khong vuong` ghi đúng sự thật ấy, không giả vờ tổng quát).

**Điểm ngoài ô ⇒ đen đặc `vec4(0,0,0,1)`**, không để sampler clamp: clamp sẽ kéo vành pixel biên thành vệt, trông như
hình thật nhưng là bịa. Phía Kotlin, `CameraDewarp.sample(...)` trả `null` cho đúng các điểm ấy.

**Ba việc còn nợ phía kết xuất** (RE §6.2, không thuộc phần thuần này):
`glGetIntegerv(GL_MAX_TEXTURE_SIZE)` cho texture rộng 5120 (**RE §7 Q13 — bắt buộc trước khi bật**) · giới hạn nhịp
vẽ kiểu `busySkip` · `dumpsys gfxinfo … framestats` trước/sau khi bật cờ, vì phương án này đụng đúng vùng đang điều
tra giật.

---

## 6. Kế hoạch kiểm trên máy ảo (cho agent kế tiếp)

`CameraDewarpTestPattern` sinh một khung fisheye **tổng hợp** (`IntArray` ARGB): lưới thẳng của thế giới bị bẻ cong,
chân trời lệch tâm cũng cong, vòng đánh dấu ở `45°`, vành tối ngoài vòng ảnh, bốn dải khác màu + nhãn `0..3`. Mặc
định `1280×960` cho một dải, `5120×960` cho khung 4-in-1.

1. `:app` dựng một `SurfaceTexture` **không** gắn camera, đẩy `pano()` vào `Surface` của nó (một `Bitmap` →
   `Canvas.drawBitmap` là đủ), rồi chạy đúng program của `CameraDewarpShader` với uniform theo bảng §5.
2. Đọc ngược khung ra (`glReadPixels`), và với **mỗi** pixel mẫu so với `CameraDewarp.sample(...)` tính trên CPU:
   lệch cho phép ≈ 1 px (nội suy tuyến tính của sampler) + 1 mức lượng tử màu.
3. Ba phép kiểm có giá trị nhất, đều bằng số:
   - **Đúng công thức**: sai số GPU-vs-CPU ở trên.
   - **Đường thẳng thành thẳng**: nắn bằng `CameraDewarpTestPattern.lensParams(spec)` ⇒ các vạch lưới trong khung ra
     phải là cột/hàng thẳng (đo độ lệch tối đa theo px).
   - **Viền đen**: điểm ngoài ô ra đúng `#000000` chứ không phải vệt kéo (so với `CORNER = #050505` của ảnh nguồn —
     hai màu cố tình khác nhau để phân biệt được "ngoài vòng ảnh" với "shader trả đen").
4. Đo `GL_MAX_TEXTURE_SIZE` **trên máy ảo trước**, để biết cần log gì trên xe (RE §7 Q13).
5. **Không** dùng ảnh tổng hợp để "xác nhận" tham số: nó được sinh bằng chính mô hình đang kiểm, nên nó chỉ chứng
   minh *cài đặt* đúng, **không** nói gì về ống kính thật. Tham số chốt bằng khung chụp từ xe (§3.2).

---

## 7. Test đã có (off-car, không GPU)

`core/src/test/kotlin/com/byd/clusternav/launcher/camera/CameraDewarpTest.kt` — **23 ca, xanh** (đếm từ XML kết quả, `failures=0 errors=0 skipped=0`).
Khoá những thứ này:

| Nhóm | Khoá cái gì |
|---|---|
| `amount 0 thi khong doi mot pixel nao` | cờ tắt ⇒ **đúng** đường 2.73, không lệch một bit |
| `tam quang anh xa ve chinh no` · `tam ngoai o la ca THAT cua crop guong` | tâm về tâm, kể cả tâm ngoài `[0,1]` |
| `ban kinh nguon tang don dieu…` · `amount 1 chi voi toi tia 90 do` | đơn điệu ⇒ nghịch đảo duy nhất; vành ngoài không với tới được |
| `mo hinh ong kinh dang khoang thuan nghich` | `r = f·θ` thuận nghịch, và tia `95°` rơi **đúng** vành vòng ảnh `480 px` — tức hệ số `K` khớp đúng giả định đã khai |
| `khu hoi dich to nguon roi ve dich` | khứ hồi `< 1e-3` trên lưới, 3 bộ tham số × 3 tỉ lệ |
| `duong thang van thang` · `nan anh tong hop bang lensParams…` | đường thẳng của thế giới vẫn thẳng sau khi nắn |
| `dong theta tron theo pixel o ca 1280x960 va 960x1280` | hiệu chỉnh tỉ lệ khung — chỗ Electro thiếu |
| `aspect 1 trung khit nguyen van Electro` | **không trôi khỏi RE**: `aspect = 1` trùng khít bản viết lại nguyên văn shader E |
| `mac dinh khop dung phep suy hinh hoc` · `K va F ti le nghich voi be ngang o` | bộ mặc định = phép suy, không phải hằng bê về |
| `kep tham so ve mien dung duoc` | `NaN`/`∞`/âm/quá tầm đều bị kẹp, `F ≤ 0` tắt phép nắn |
| `xoay boi cua 90 khop CameraOverlayTransform` · `xoay goc le thi xien…` | quy ước xoay khớp đường đang chạy; góc lẻ thì nói thật |
| `soi guong di bang be rong am cua uSrcRect` · `sample tra null…` | lật không cần uniform mới; ngoài ô ⇒ đen |
| `nguon shader ghim dung cac token…` · `tap uniform va attribute…` | GLSL giữ đúng `atan(`/`mix(`/`clamp(`, đúng một `atan`, không `pow/asin/sqrt`, không còn chỗ chèn `<PARAM_F>`; tập uniform đọc từ chính văn bản shader |
| `anh pano 4 dai…` · `vong danh dau tron…` · `chan troi lech tam thi CONG…` | ảnh kiểm tra: cỡ, màu dải, số pixel nhãn (đếm **đúng** con số), góc tối, vòng tròn tròn theo pixel, số vạch lưới khớp mô hình, chân trời lệch tâm thì cong / qua tâm thì thẳng |

`LayeringRulesTest` (9 ca, xanh) chứng minh ba tệp mới vẫn là **JVM thuần** — không `import android.*`, không `dadb`.

---

## 8. Còn chưa biết (không được phát biểu như đã biết)

| # | Chưa biết | Chốt bằng |
|---|---|---|
| D1 | Vòng ảnh thật có bằng bề cao khung không, tâm ở đâu | 🚗 một khung `5120×960` chụp qua `ClusterDiag` (CLAUDE.md §11) |
| D2 | Ống kính equidistant hay equisolid | 🚗 cùng khung ấy: nắn xong biên còn cong không |
| D3 | `F`/`K`/`SCALE`/`AMOUNT` của Electro | RE §7 Q6 — **hoặc bỏ qua**, Kachi tự chỉnh bằng mắt rẻ hơn |
| D4 | Ma trận `getTransformMatrix` thật của camera id 1 | 🚗 RE §7 Q17 — log một dòng từ Kachi |
| D5 | `GL_MAX_TEXTURE_SIZE` của GPU đầu xe với texture rộng 5120 | 🚗 RE §7 Q13 — **bắt buộc trước khi bật cờ** |
| D6 | Thêm một lượt GPU có làm giật hơn không | 🚗 `framestats` trước/sau, `diagnostics/camera-lag-analysis-2026-09-26.md` |
| D7 | Dải nào là gương trái/phải | 🚗 RE §7 Q1/Q2 — cùng khung của D1 |
