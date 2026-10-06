# RUNBOOK — Mod VietMap: bong bóng dẫn đường lên CỤM (làm lại cho mọi bản VietMap)

> **Trạng thái**: Runbook (thủ tục lặp lại) · **Cập nhật**: 2026-10-06 (viết lại toàn bộ: bản mod hiện hành 3.4.3 v2 —
> lệnh ẩn/hiện `VM_BUBBLE_VIS`; đường nhanh + đường đầy đủ; 3.4.0 dồn vào §10 Lịch sử) · trước đó 2026-10-05 (§11 cũ — port
> 3.4.3), 2026-08-28 (3.4.0) · **Loại**: Runbook · **Owner**: dangkhoi
>
> **Mục đích**: một người bảo trì sau này, cầm một bản VietMap MỚI, làm lại được bản mod từ đầu tới lúc cài lên xe — không
> phải mò lại. Mod KHÔNG nằm trong APK Kachi: Kachi chỉ gửi broadcast tới gói `vn.vietmap.live` (hợp đồng §6, §7).
> Owner 2026-10-06: *"viết 1 cái tài liệu mod vietmap, để sau này dùng, thực tế có 1 tài liệu như thế trước đây, tìm và cập
> nhật nhé"*. Mức bằng chứng theo CLAUDE.md §2: [ĐO] / [SUY] / [ĐOÁN] / [CHƯA BIẾT]. Spec liên quan:
> `docs/specs/kachi-290-cluster-rect-fix.html` (R8–R10, §4.3–§4.5) · `docs/specs/kachi-289-field-fixes.html` (B2 · VM-PREREQ-TRUTH).

**Ký hiệu chỗ giữ** (repo PUBLIC — không ghi đường dẫn máy, mật khẩu, IP xe):

| Ký hiệu | Nghĩa |
|---|---|
| `<WORK>` | thư mục làm việc ngoài repo (scratchpad) |
| `<XAPK>` | thư mục giải nén xapk Google (base + `config.*`) |
| `<SPLITS>` | thư mục split chỉ-tài-nguyên mượn từ nguồn khác (§3) |
| `<SRC>` | cây `apktool d` đang sửa smali |
| `<OUT>` | thư mục ra |
| `<BT>` | Android build-tools 34.0.0 (`zipalign`, `apksigner`, `aapt2`, `dexdump`) |
| `<KS>` | keystore mod (PKCS12, alias `vmmod`) — **GIỮ**, mất là mất đường `install -r` giữ đăng nhập |
| `<mật-khẩu-khoá-mod>` | mật khẩu `<KS>` (chỉ owner giữ, không bao giờ ghi vào repo) |
| `<APKEDITOR>` | tệp jar APKEditor (bản phát hành chính thức REAndroid trên GitHub) |
| `<CỤM>` | id display của màn ảo cụm lúc đo (đọc bằng `dumpsys display`, KHÔNG đoán — id đổi 1/2/4/8/9…) |

---

## 0. Trạng thái (đọc trước)

| Bản | Có gì | Trạng thái |
|---|---|---|
| **3.4.3 v2 — HIỆN HÀNH** (dựng 06/10) | = v1 + receiver `VM_BUBBLE_VIS` (ẩn/hiện, §6.4) + wrapper `O()/x()`, `t()/l()`, `c()` | Máy ảo smoke ok (mở app, service chạy, không `VerifyError`) · **ẩn/hiện trên xe 🚗 CHƯA ĐO** |
| 3.4.3 v1 (05–06/10) | chọn màn cụm theo TÊN (`ModClusterDisplay`) + DisplayListener dời bóng + `posrx` (`VM_BUBBLE_POS`) + MiMi theo màn cụm + dời `Lc/o;` sang `classes2` | **[ĐO xe 06/10] bóng lên cụm ✅** — 3 cửa sổ `TYPE_APPLICATION_OVERLAY` của `vn.vietmap.live` trên màn ảo cụm, thường trực kể cả khi tắt chiếu (`oncar-2026-10-06-cluster-rect.md` F5) |
| 3.4.0 mod (08/2026) | redirect `getDisplay(1)/(2)` + `posrx` | Lịch sử §10 — cách chọn màn KHÔNG còn dùng được trên xe có ô Kachi |

- Chữ ký mod: `CN=vmmod`, cert SHA-256 **`a8f4a1e6…`** (bản Google: `684cedb4…`; Aurora BYD: `c908e973…`).
- v2: versionName `3.4.3` (versionCode 179094172), native-code `arm64-v8a`, không có `com.kangrio`.
- [ĐO 06/10 `apksigner verify --print-certs`] v1 và v2 **cùng cert** `a8f4a1e6…` ⇒ `install -r` v2 đè v1 giữ đăng nhập. Bản sau vẫn **so cert trước khi `install -r`** (§8.1); khác ⇒ `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- Việc 🚗 còn mở: (a) `VM_BUBBLE_VIS show=false` gỡ hết cửa sổ trên `<CỤM>`, `show=true` gắn lại; (b) công tắc bóng Kachi TẮT/BẬT;
  (c) đổi theme cụm có VietMap chạy: log `dọn cụm … VM_BUBBLE_VIS show=false → theme … → trả cụm … show=true` (spec 2.90 V-oncar e–h).

---

## 1. Tổng quan mod — sửa gì, vì sao

VietMap gốc vẽ bóng dẫn đường (overlay `TYPE_APPLICATION_OVERLAY` = 2038) lên màn chính. Mod đổi **WindowManager** của bóng sang
WindowManager của màn ảo cụm, rồi mở hai "cửa" cho Kachi điều khiển:

| Bản vá | Lớp/tệp (3.4.3) | Mục đích | Chi tiết |
|---|---|---|---|
| P1 chọn màn cụm | `Lvn/vietmap/live/ModClusterDisplay;` (mới) | tìm màn cụm theo TÊN, theo dõi thêm/gỡ màn | §6.1 |
| P2 redirect + dời | `VMBluetoothService.onCreate/onDestroy`, `b.modMove` | bóng + cửa sổ lốp dựng trên màn cụm; màn dựng lại ⇒ dời bóng | §6.2 |
| P3 vị trí | `VMBluetoothService$posrx` | `VM_BUBBLE_POS` x/y → góc trên-trái tuyệt đối | §6.3 |
| P4 ẩn/hiện (v2) | `VMBluetoothService$visrx`, `ModVis`, wrapper ở `b`, `ta/c`, `c/o` | `VM_BUBBLE_VIS show` → gỡ/gắn lại MỌI cửa sổ phủ | §6.4 |
| P5 MiMi | `Lc/o;->c()` | cửa sổ trợ lý MiMi theo màn cụm | §6.5 |
| P6 receiver | đăng ký trong mã, cờ `0x2` | nhận broadcast từ Kachi (app khác) | §6.6 |
| P7 rào lỗi | mọi khối mod | mod hỏng ⇒ về đường gốc, không giết app đang dẫn đường | §6.7 |

---

## 2. Công cụ + quyền Claude Code

- `apktool` 3.0.3 (brew) — decode/build smali. Bước `apktool b` là nơi DUY NHẤT lộ lỗi cú pháp smali + vượt 65 536 method.
- `APKEditor` (REAndroid, ≥ 1.4.9) — gộp split → 1 APK universal (`m`).
- Android build-tools **34.0.0** — `zipalign`, `apksigner`, `aapt2`, `dexdump`.
- **JDK 17** (`JAVA_HOME` = openjdk@17 của Homebrew) — `keytool`, chạy jar.
- `jadx` (tuỳ chọn) — đọc Java để hiểu logic trước khi sửa smali.

**Claude Code auto-mode**: bộ phân loại quyền có thể CHẶN việc đọc/dựng mã bên thứ ba đã dịch ngược (05/10 chặn `apktool b`;
06/10 chặn script Python đếm method trong dex). Owner đã cho chạy bằng các luật quyền sau (ghi ở `settings.local.json` của phiên,
thay `<WORK>` bằng thư mục thật, KHÔNG commit đường dẫn máy):

```json
{
  "permissions": {
    "allow": [
      "Read(<WORK>/**)",
      "Edit(<WORK>/**)",
      "Bash(apktool:*)",
      "Bash(bash <WORK>/build-mod.sh)"
    ]
  }
}
```

Gom mọi bước dựng vào MỘT script (`build-mod.sh`, §4) để chỉ cần một luật `Bash(bash …)`. Đếm method dùng `dexdump` (§5.4) thay
cho script tự viết. Bị chặn thì DỪNG, đưa owner lệnh để tự chạy — không tìm đường vòng.

---

## 3. Nguồn APK — luật cứng

1. **Chỉ dùng APK Google ký** (cert `684cedb4…`): xapk **arm64** của APKPure / APKCombo. [ĐO 05/10] xapk APKPure 3.4.3 =
   `base` + `config.arm64_v8a` + `config.en` + `config.mdpi`, cả 4 cùng cert Google. (3.4.0 từng gặp xapk APKPure chỉ có
   `armeabi-v7a` ⇒ xe arm64-only không chạy — luôn kiểm có `config.arm64_v8a`.)
   ```bash
   <BT>/apksigner verify --print-certs <XAPK>/<tệp>.apk | grep -i sha-256   # phải ra 684cedb4… cho MỌI split
   ```
2. **KHÔNG BAO GIỜ dùng bản Aurora Store BYD** (`com.aurora.store.byd`): [ĐO 05/10] `base.apk` bị đóng gói lại, ký
   `CN=AuroraStore` (`c908e973…`) và **chèn `com.kangrio` SpoofAppComponentFactory** (bộ giả chữ ký). Không lấy base hay split có mã
   của nó.
3. **Split thiếu** (vd xapk Google không có `config.vi` ⇒ chữ phía Android ra tiếng Anh; không có `hdpi`) được mượn từ nguồn khác
   **chỉ khi split đó thuần tài nguyên** — manifest + `resources.arsc` (+ `res/`), `hasCode=false`, không `.dex`/`.so`:
   ```bash
   unzip -l <SPLITS>/split_config.<x>.apk | grep -E '\.dex$|\.so$'      # PHẢI rỗng, không rỗng ⇒ DỪNG
   ```
   APKEditor gộp rồi ký lại bằng `<KS>` ⇒ chữ ký nguồn mượn không còn trong APK ra. 3.4.3 đã mượn `config.vi` (có "Chỉ đường về
   nhà") + `config.hdpi`.

---

## 4. ĐƯỜNG NHANH — vá nhỏ trên bản mod đang chạy

Dùng khi VietMap KHÔNG đổi bản, chỉ thêm/sửa một bản vá (đúng cách v2 được dựng từ v1). Bản mod hiện hành ĐÃ universal (đã gộp
split) và đã có P1–P3, P5–P7 ⇒ không cần gộp lại, không cần tìm lại target.

```bash
# 0. Nguồn = APK mod đã thử ngoài xe (ghi sha256 của nó vào ghi chú phiên)
apktool d -f -o <SRC> <APK-mod-hiện-hành>.apk
# Kiểm bản vá cũ còn đủ TRƯỚC khi sửa:
grep -rl "ModClusterDisplay" <SRC>/smali_classes2 | head -1
grep -rn "com.byd.clusternav.VM_BUBBLE_POS" <SRC>/smali_classes2      # chỉ ở VMBluetoothService.smali
# 1. Sửa smali (lớp mới đặt ở smali_classes2/ — §5.4)
# 2. Dựng — gói thành <WORK>/build-mod.sh:
```

```bash
#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME=<JDK17>; export PATH="$JAVA_HOME/bin:$PATH"
BT=<BT>; SRC=<SRC>; OUT=<OUT>; KS=<KS>
rm -rf "$OUT"; mkdir -p "$OUT"
apktool b "$SRC" -o "$OUT/unsigned.apk"
"$BT/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"          # -p: căn trang .so (bắt buộc với extractNativeLibs=false)
"$BT/apksigner" sign --ks "$KS" --ks-key-alias vmmod \
  --ks-pass pass:<mật-khẩu-khoá-mod> --key-pass pass:<mật-khẩu-khoá-mod> \
  --out "$OUT/signed.apk" "$OUT/aligned.apk"
# Kiểm — hỏng một dòng là DỪNG:
"$BT/apksigner" verify --print-certs "$OUT/signed.apk" | grep -q "CN=vmmod"
B="$("$BT/aapt2" dump badging "$OUT/signed.apk")"
echo "$B" | grep -q "versionName='<bản VietMap>'"
echo "$B" | grep -E "^native-code:" | grep -q "arm64-v8a"
! echo "$B" | grep -q "com.kangrio"
unzip -p "$OUT/signed.apk" classes2.dex | LC_ALL=C grep -a -q "com.byd.clusternav.VM_BUBBLE_VIS"
unzip -p "$OUT/signed.apk" classes2.dex | LC_ALL=C grep -a -q "com.byd.clusternav.VM_BUBBLE_POS"
shasum -a 256 "$OUT/signed.apk"
```

Đặt tên ra `VietMap-<bản>-mod-cluster-v<N>.apk` — mỗi bản giao owner một số `v<N>` riêng (CLAUDE.md §9), ghi sha256 + cert.
Mật khẩu: tốt nhất đọc từ biến môi trường (`--ks-pass env:VMMOD_PASS`) thay vì gõ trong script.

---

## 5. ĐƯỜNG ĐẦY ĐỦ — bản VietMap MỚI

### 5.1 Gộp split → universal, rồi decode
```bash
mkdir -p <WORK>/merge && unzip -o <xapk> -d <XAPK>
cp <XAPK>/<base>.apk <WORK>/merge/base.apk
cp <XAPK>/config.arm64_v8a.apk <XAPK>/config.*.apk <WORK>/merge/      # + split chỉ-tài-nguyên đã kiểm ở §3
java -jar <APKEDITOR> m -i <WORK>/merge -o <WORK>/universal.apk -f
apktool d -f -o <SRC> <WORK>/universal.apk
```
(Gộp TRƯỚC rồi decode một lần — dựng ra là một APK duy nhất, đường nhanh §4 áp được cho các lần vá sau. 3.4.3 làm ngược lại —
decode base, dựng base-mod rồi mới gộp — cũng được, nhưng mỗi lần vá phải gộp lại.)

### 5.2 Tìm lại target (obfuscation đổi tên mỗi bản)
Tên LỚP ổn định qua 3.4.0 → 3.4.3; tên METHOD đổi. Tìm theo HÀNH VI, không theo tên:

| Thành phần | Cách tìm | 3.4.0 | 3.4.3 |
|---|---|---|---|
| service cấp WindowManager cho bóng | `extends Landroid/app/Service;` có `const-string …, "window"` trong `onCreate` rồi `new-instance Lvn/vietmap/live/b;` | `VMBluetoothService` | không đổi |
| lớp bóng dẫn đường | lớp gọi `WindowManager;->addView` + `0x7f6` (2038) | `Lvn/vietmap/live/b;` | không đổi |
| cửa sổ áp suất lốp · MiMi · view MiMi | `new Lta/c;(Context, WindowManager)` trong `onCreate` · plugin Flutter `Lc/o;` · `Lb/b;` | `Lta/c;` · `Lc/o;` · `Lb/b;` | không đổi |
| field bóng · getter | `iput-object … ->s:Lvn/vietmap/live/b;` · getter **ném khi null** | `s` · `g()` | `s` · `g()` |
| đặt vị trí | method `(II)V` của `b` gán x/y + `updateViewLayout` | `E(II)V` | **`L(II)V`** |
| getter LayoutParams | `()Landroid/view/WindowManager$LayoutParams;` | `q()` | **`u()`** |
| hiện bóng | `addView` + cờ đang hiện (`k`) | `G()` | **`O()`** |
| khởi tạo | inflate + dựng LayoutParams, KHÔNG `addView` (không phải "hiện") | `i()` | **`m()`** |
| ẩn bóng | gỡ qua `WindowManagerGlobal` / `removeView` | `t()` | **`x()`** |
| làm mới | — | `h()` | **`l()`** |
| getter WindowManager | `()Landroid/view/WindowManager;` | `s()` | **`w()`** |
| (đổi cùng lượt, mod không dùng) | — | `j()` · `I()` | `n()` · `Q()` |
| cửa sổ lốp hiện · gỡ | `addView` · `removeView` trong `Lta/c;` | — | `t()` · `l()` |
| MiMi hiện | private `c()` trong `Lc/o;`, gọi từ `onMethodCall` | `c()` | `c()` |

```bash
grep -rlE "Landroid/view/WindowManager;->addView" <SRC>/smali*
grep -rn "const/16 [pv][0-9]*, 0x7f6" <SRC>/smali*
grep -rn 'const-string v[0-9]*, "window"' <SRC>/smali* | grep -i service
grep -n "\.method" <SRC>/smali*/vn/vietmap/live/b.smali                 # đối chiếu chữ ký với bảng trên
```
Nghi ngờ thì cài bản CHƯA vá, dẫn đường, `dumpsys window windows | grep -A3 "u0 vn.vietmap.live}"` — cửa sổ có mũi tên/cự ly chính là
bóng. **Đừng nhầm MiMi với bóng** (MiMi là trợ lý giọng nói).

Sau mỗi bản vá, grep tên MỚI xem có call site không (CLAUDE.md §8) — vd `grep -rn "modO()V" <SRC>/smali*`.

### 5.3 Áp bản vá — thứ tự
P1 `ModClusterDisplay` → P2 `onCreate`/`onDestroy` + `b.modMove` → P3 `posrx` → P5 MiMi → P4 `visrx`/`ModVis` + wrapper. Mỗi bước
dựng thử bằng `apktool b` (bắt lỗi sớm). Hợp đồng từng bản vá ở §6 — chép logic, đổi tên method theo bảng §5.2.

### 5.4 Giới hạn 65 536 method id của một dex
- [ĐO `dexdump`] `classes.dex` của 3.4.3 gốc đã **65 536 / 65 536** (`classes2.dex` 52 982). Thêm MỘT tham chiếu mới vào lớp ở
  `classes.dex` (lời gọi `ModClusterDisplay.find` trong `Lc/o;`) ⇒ 65 537 ⇒ `apktool b` hỏng. (3.4.0 gốc: 65 531.)
- **Cách**: mọi lớp mod MỚI đặt dưới `smali_classes2/`; lớp gốc nào phải thêm tham chiếu mà đang ở `smali/` thì **dời nguyên tệp**
  sang `smali_classes2/` (apktool dựng mỗi thư mục `smali_classesN` thành `classesN.dex`; đường dẫn trong thư mục không đổi tên lớp).
  3.4.3 dời `smali/c.1/o.smali` (`Lc/o;`) ⇒ 65 528 / 53 021. v2 không sửa gì ở `smali/` ⇒ `classes.dex` không đổi; `classes2`
  thêm ≤ 15 method ref.
- Bản mới: đếm TRƯỚC khi chèn:
  ```bash
  unzip -o <WORK>/universal.apk 'classes*.dex' -d <WORK>/dex
  for d in <WORK>/dex/classes*.dex; do echo "$d $(<BT>/dexdump -f "$d" | grep method_ids_size)"; done
  ```
  `apktool b` thành công = cả hai dex đều dưới trần (smali hỏng cứng nếu vượt).

### 5.5 Dựng + ký + kiểm
Đúng §4 bước 2 (script), nguồn là `<SRC>` của §5.1. Thêm khi đổi bản:
`aapt2 dump strings <OUT>/signed.apk | grep "Chỉ đường về nhà"` phải có (split `vi` đã vào) · ghi sha256 + kích thước.
Rồi máy ảo (§8.3) TRƯỚC xe.

---

## 6. Các bản vá — mục đích + hợp đồng

### 6.1 P1 · `ModClusterDisplay` — chọn màn cụm theo TÊN
- **Vì sao không theo id**: [ĐO nguồn framework xe, dịch ngược] `DisplayManager` chỉ trả/liệt kê màn PRIVATE cho app sở hữu hoặc đã
  có cửa sổ trên đó (`DisplayManagerService.java:446-451, 460-469`; `Display.java:521-522`). Ô Kachi là màn ảo private (cờ `8|256`,
  `VdAppHost.kt`). Màn cụm `fission_bg_xdjaVirtualSurface` (chủ `com.xdja.containerservice`) là PUBLIC (`FLAG_PRESENTATION,
  FLAG_OWN_CONTENT_ONLY`). [ĐO xe 15/09] sau khởi động nguội display 1 = `kachi-slot-0`, cụm = 2 (từng thấy 1/2/4/8/9) ⇒
  `getDisplay(1)` của 3.4.0 trả null hoặc trúng ô Kachi.
- **Hợp đồng**: `find(Context)` duyệt `getDisplays()`, lấy id ≠ 0 có tên (chữ thường, `Locale.ROOT`) chứa `xdja` hoặc `fission` —
  CÙNG từ khoá Kachi `DisplayParse.clusterDisplayId`; không khớp ⇒ `null` ⇒ giữ WindowManager mặc định (màn chính = như gốc). Tên
  màn Kachi (`kachi-slot-*`, `kachi-stage-*`) không bao giờ khớp. Không còn `getDisplay(<số>)` nào trong mã mod.
- Lớp này đồng thời là `DisplayListener`: `onDisplayAdded/Changed/Removed` ⇒ `sync()` (§6.2).
- [SUY] DiLink 5 có `shared_fission_bg_XDJAScreenProjection_0/_1` ⇒ `find` lấy màn Android liệt kê trước.

### 6.2 P2 · redirect trong service + dời bóng khi màn dựng lại
- `VMBluetoothService.onCreate` (`.locals 3 → 6`): WindowManager = `createDisplayContext(find(this)).getSystemService("window")`;
  `null` / không phải `WindowManager` / ném ⇒ WindowManager mặc định. Cửa sổ lốp `Lta/c;` nhận CÙNG WindowManager đầu tiên.
- Field mới `modRx` (posrx), `modDl` (`ModClusterDisplay`), `modVis` (visrx — v2) để `onDestroy` gỡ được.
- Sau khi bóng dựng: `registerDisplayListener(modDl, Handler(Looper.getMainLooper()))` rồi `sync()` một lần (đóng khe giữa `find` và
  lúc đăng ký). `sync()` = vỏ bắt `RuntimeException` quanh `syncImpl()`: id màn đích ≠ id màn hiện của bóng (`w().getDefaultDisplay()`)
  ⇒ `b.modMove(wm)`; mất cụm mà bóng không ở display 0 ⇒ về màn mặc định.
- `b.modMove(WindowManager)`: gỡ bằng `modX()` (bắt `IllegalArgumentException`/`IllegalStateException`) → đổi field WindowManager
  riêng của bóng → `modO()` CHỈ khi trước đó đang hiện; màn mới từ chối (`BadTokenException`/`InvalidDisplayException`/
  `IllegalStateException`) ⇒ về WindowManager mặc định, thử `modO()` một lần. Chỉ bắt ngoại lệ WindowManager. (v1 gọi `x()`/`O()`;
  v2 gọi thân gốc `modX()`/`modO()` để không đụng sổ "muốn hiện" — §6.4.)
- [ĐO nguồn xe] `addView` lại view đang gỡ dở được Android xử lý (`WindowManagerGlobal.java:266-267`); `createDisplayContext` chỉ ném
  khi display null (`ContextImpl.java:1880-1889`).
- `onDestroy`: gỡ listener + 2 receiver TRƯỚC phần dọn gốc, bọc `catch Throwable`.

### 6.3 P3 · `posrx` — vị trí bóng
- **Hợp đồng**: action `com.byd.clusternav.VM_BUBBLE_POS`, gói tường minh `vn.vietmap.live`, extra int `x`, `y` = **góc trên-trái
  tuyệt đối** (px) trên màn cụm; thiếu/âm ⇒ bỏ qua.
- Trên LayoutParams (`u()`): ép `gravity = 0x33` (`TOP|LEFT`) + `flags |= 0x200` (`FLAG_LAYOUT_NO_LIMITS`) rồi `L(x, y)`. Thiếu hai
  dòng này: bóng gốc `TOP|CENTER_HORIZONTAL` + inset ⇒ lệch ~−775 px trục X, ~+245 px trục Y [ĐO máy ảo 08/2026, 3.4.0].
- Đọc thẳng field `s` có kiểm null (KHÔNG gọi `g()` — ném khi `s` null); thân `onReceive` bọc catch (chạy luồng chính).
- [SUY] cửa sổ chạm vô hình của 3.4.3 không nhận cờ `0x200` ⇒ có thể lệch so với bóng; cụm không có chạm ⇒ vô hại.

### 6.4 P4 · `visrx` + `ModVis` — ẩn/hiện (v2)
- **Hợp đồng**: action `com.byd.clusternav.VM_BUBBLE_VIS`, gói tường minh `vn.vietmap.live`, extra **boolean `show`** (thiếu extra ⇒
  bỏ qua). Đăng ký cạnh posrx, cùng cờ `0x2`, gỡ ở `onDestroy`. `VM_BUBBLE_POS` không đổi.
- **Vì sao mod tự gỡ mà không dùng appop**: [ĐO nguồn r47, spec 2.90 §4.3] appop `SYSTEM_ALERT_WINDOW ignore` chỉ ẨN (`hideLw`),
  cửa sổ + layer vẫn nằm trên màn ảo cụm, `dumpsys window windows` vẫn liệt kê ⇒ cổng theme cụm vẫn đếm. Chỉ tiến trình VietMap
  `removeView` được cửa sổ của nó.
- **Mẫu wrapper** (không có cờ "đang gọi nội bộ" nào có thể kẹt sau ngoại lệ): điểm vào gốc thành vỏ mỏng giữ sổ, thân gốc dời sang
  `mod*` (giữ nguyên từng byte + `.locals`, chỉ đổi tên). Mã mod (ẩn/hiện, `modMove`) gọi thẳng `mod*` nên không đụng sổ.

  | Cửa sổ | Vỏ hiện | Vỏ gỡ | Thân gốc |
  |---|---|---|---|
  | bóng `b` (+ ô chạm `b$a` + vùng kéo-đóng `n`) | `O()`: `wanted=true`; đang ẩn ⇒ return; `modO()` | `x()`: `wanted=false`; `modX()` | `modO()` · `modX()` |
  | lốp `ta/c` | `t()`: `tyreWanted=true`; đang ẩn ⇒ return; `modT()` | `l()`: `tyreWanted=false`; `modL()` | `modT()` · `modL()` |
  | MiMi `c/o` → cửa sổ `Lb/b` | `c()` (private): `mimi=this`; `mimiWanted=true`; đang ẩn ⇒ return; `modC()` | nhánh Dart "8": `mimiWanted=false` trước `D.k()` | `modC()` · `modHide()` (mới) |

  ```smali
  .method public final O()V
      .locals 1
      const/4 v0, 0x1
      sput-boolean v0, Lvn/vietmap/live/ModVis;->wanted:Z
      sget-boolean v0, Lvn/vietmap/live/ModVis;->hidden:Z
      if-eqz v0, :cond_0
      return-void
      :cond_0
      invoke-virtual {p0}, Lvn/vietmap/live/b;->modO()V
      return-void
  .end method
  ```
- **Ẩn** (`apply(svc,false)`): `hidden=true`, rồi ba bước, mỗi bước try/catch(Throwable) riêng: `svc.s.modX()` (gỡ vùng kéo-đóng, mọi ô
  chạm, bóng) · `svc.t.modL()` (lốp) · `ModVis.mimi.modHide()` (MiMi). Trong lúc ẩn: vỏ `O()/t()/c()` của VietMap chỉ ghi "muốn hiện"
  rồi return; `b.g()` (thêm vùng kéo-đóng) return sớm (chặn runnable đăng trước khi ẩn); `modMove` thấy `k=false` ⇒ chỉ đổi
  WindowManager.
- **Hiện** (`apply(svc,true)`): `hidden=false`, rồi CHỈ khôi phục cái VietMap đang muốn: `wanted` ⇒ `new ModClusterDisplay(svc).sync()`
  (đổi WindowManager sang cụm hiện tại) rồi `modO()` · `tyreWanted` ⇒ `modT()` · `mimiWanted` ⇒ `modC()`. Idempotent nhờ cờ gốc
  `k`/`d`/`f` (không `addView` đôi). Đang không dẫn đường ⇒ `show=true` không làm hiện gì — ĐÚNG.
- **Luồng**: receiver chỉ đọc extra rồi `ModVis.post()` → `Handler(Looper.getMainLooper()).post(Runnable)`; mọi thao tác cửa sổ trên
  luồng chính.
- **`hidden` chỉ trong RAM (static)**: sống qua service khởi động lại trong cùng tiến trình; tiến trình VietMap chết ⇒ lần sau bóng
  HIỆN lại ⇒ **Kachi phải gửi lại** `show=false` khi đang muốn ẩn (§7).
- Không bao giờ nhận `VM_BUBBLE_VIS` ⇒ `hidden=false` mãi ⇒ mọi vỏ chỉ ghi sổ rồi gọi thân gốc ⇒ hành vi y như v1.
- Rủi ro đã biết: `modX` ném giữa chừng ⇒ `k` có thể kẹt `true` (giống gốc với cùng lỗi); `ModVis.mimi` giữ tham chiếu tĩnh tới
  plugin Flutter.

### 6.5 P5 · MiMi theo màn cụm
`Lc/o;->c()` lấy WindowManager qua `ModClusterDisplay.find()` — quyết MỘT lần lúc tạo, không dời sau (listener không dời MiMi/lốp).
[SUY] cụm mất sau đó ⇒ hiện MiMi/lốp có thể ném `InvalidDisplayException` không bắt (3.4.0 cũng vậy). Lời gọi này là lý do phải dời
`Lc/o;` sang `classes2` (§5.4).

### 6.6 P6 · receiver cờ `0x2` (exported)
`registerReceiver(rx, filter, 0x2)` — `0x2` = `RECEIVER_EXPORTED`: broadcast tới từ Kachi (app khác, uid khác) nên receiver phải
exported. [ĐO nguồn AMS xe, dịch ngược] Android 10 chỉ đọc bit `0x1` ⇒ trên xe không đổi gì; nhưng từ Android 14 với
`targetSdk ≥ 34` (VietMap 3.4.3 target 36) thiếu cờ là NÉM lúc đăng ký trên máy ảo API 34+. Không khai receiver trong manifest
(đăng ký trong mã, sống theo service). Kachi luôn gửi với gói tường minh `-p vn.vietmap.live` ⇒ không lọt sang app khác.

### 6.7 P7 · rào try/catch
- Khối redirect `onCreate`, đăng ký mỗi receiver, `onDestroy` mod: `catch Throwable` (kể cả `VerifyError`/`NoClassDefFoundError` của
  lớp mod) ⇒ hỏng thì về đường gốc, không giết service.
- `onReceive` (posrx, visrx), `ModVis.post/run`, mỗi lời gọi vào mã VietMap trong `apply`: try/catch(Throwable).
- Callback DisplayListener (`sync`): bắt `RuntimeException` — ném trên luồng chính là chết cả app đang dẫn đường.
- Nhãn mới duy nhất trong từng method; vỏ dùng `.locals 1`; khối chèn trong `onCreate` chỉ dùng thanh ghi rảnh (3.4.3: `v3–v5`;
  `v0` = WindowManager và `v2` còn được mã gốc dùng sau — giữ nguyên).

---

## 7. Phía Kachi của hợp đồng

| Kachi | Tệp | Gửi gì, khi nào |
|---|---|---|
| Vị trí | `VmOverlayPosition.kt` | `VM_BUBBLE_POS x/y` (prefs `vm_bubble_x/y`, px góc trên-trái) khi kéo-thả/Áp dụng + vòng làm tươi khi Cast ON |
| Ẩn/hiện | `VmBubbleVisibility.kt` | `VM_BUBBLE_VIS show = bubbleWanted(vm_bubble_hidden, đang dọn cụm)` |

- **Công tắc bóng**: chỉ ẨN khi người lái chủ động tắt — prefs `vm_bubble_hidden`, **mặc định `false` ⇒ hiện** như trước 2.90 [ĐO mã].
  Không gate theo Cast ON (bóng nằm trên cụm cả khi tắt chiếu, F5).
- **Điểm gửi** (`force` = bỏ cổng gửi lặp 15 s): đổi công tắc (force) · cuối `VietMapAutostart.runNow` (force — VietMap vừa mở) · áp
  lại hồ sơ (force) · trả cụm sau đổi theme (force) + lưới an toàn 20 s · dò được id cụm mới (`SimpleCastRuntime`, qua cổng) · nhịp 2 s
  `FloatingBubbleService` CHỈ **giữ ẩn** (`keepHidden`: gửi lại `show=false` qua cổng 15 s — bù `hidden` mất khi tiến trình VietMap
  chết); không gửi lặp `show=true`.
- **Đổi theme cụm** (spec 2.90 R9, §4.4): cổng theme thấy trên màn ảo cụm chỉ còn lớp Kachi + bóng app đã biết ⇒ DỌN (gỡ badge Kachi
  trong tiến trình + `show=false`) ⇒ đọc lại ≤ 6×250 ms tới khi sạch ⇒ luật cũ (0 task + 0 cửa sổ) mới gửi theme ⇒ TRẢ trong `finally`
  (id cụm MỚI, badge gắn lại, `show` theo công tắc).
- **Mod không có receiver** (3.4.0, 3.4.3 v1): broadcast tường minh rơi im lặng, không ném. Cổng theme thấy bóng VẪN còn sau dọn ⇒
  bỏ theme lý do BUBBLE, Cài đặt nói *"bản mod VietMap cũ chưa hỗ trợ ẩn bóng — tắt VietMap rồi Áp ngay"*
  (`ClusterThemeGuard.lastBubbleOldMod`). VietMap không cài ⇒ 0 broadcast (cache 60 s).

---

## 8. Cài trên xe + kiểm

### 8.1 Cùng khoá hay khác khoá
```bash
adb shell pm path vn.vietmap.live                                    # lấy đường base đang cài
adb pull <đường-base> <WORK>/installed.apk
<BT>/apksigner verify --print-certs <WORK>/installed.apk | grep -i sha-256
```
- **Cùng khoá mod** (`a8f4a1e6…`) ⇒ `adb install -r <OUT>/signed.apk` — giữ đăng nhập, widget, miễn pin.
- **Khác khoá** (Google Play `684cedb4…`, Aurora `c908e973…`, hoặc mod ký khoá khác) ⇒ PHẢI gỡ trước (`adb uninstall
  vn.vietmap.live`) ⇒ mất đăng nhập MỘT lần (đăng nhập lại). Gỡ-rồi-cài còn:
  - **xoá mọi app widget VietMap** (r47 `AppWidgetServiceImpl` xoá widget của provider khi gỡ gói — spec 2.90 §4.5). Kachi 2.90 tự
    lành: `VietMapWidgetRestorePlan` thấy id chết + provider có mặt ⇒ xoá id, bind lại (cần quyền bind đã cấp; thiếu ⇒ màn Chẩn đoán
    widget). Kachi < 2.90 giữ id chết ⇒ badge giới hạn tốc độ câm (gốc sáng 06/10).
  - **xoá miễn tối ưu pin** của gói ⇒ hộp "IVI không hỗ trợ" (§9). Kachi 2.89+ (`AppPrereqs`, VM-PREREQ-TRUTH) đọc sự thật và tự
    miễn lại.

### 8.2 Quyền
```bash
adb shell appops set vn.vietmap.live SYSTEM_ALERT_WINDOW allow        # vẽ nổi — Kachi 2.89+ tự cấp khi bóng bật
adb shell cmd deviceidle whitelist +vn.vietmap.live                   # miễn pin — Kachi tự làm; tay chỉ khi Kachi chưa chạy
```
Mở VietMap, đăng nhập (bóng chỉ hiện khi đang dẫn đường, cần đăng nhập; `VMBluetoothService` thì chạy ngay khi Dart gọi kênh).

### 8.3 Kiểm (máy ảo TRƯỚC, rồi xe)
```bash
adb logcat -d | grep -E "VerifyError|vn.vietmap.live" | tail            # lớp mod hỏng ⇒ VerifyError lúc service khởi động
adb shell dumpsys display | grep -E "Display [0-9]+:|xdja|fission"       # lấy <CỤM> — KHÔNG đoán id
# dẫn đường, rồi:
adb shell dumpsys window windows | grep -E "Window #|mDisplayId=" | grep -A1 "u0 vn.vietmap.live}"
#   ⇒ các cửa sổ overlay của vn.vietmap.live có mDisplayId=<CỤM> (xe 06/10: 3 cửa sổ)
adb shell am broadcast -a com.byd.clusternav.VM_BUBBLE_VIS -p vn.vietmap.live --ez show false
adb shell dumpsys window windows | grep -c "u0 vn.vietmap.live}"         # ⇒ cửa sổ overlay trên <CỤM> = 0
adb shell am broadcast -a com.byd.clusternav.VM_BUBBLE_VIS -p vn.vietmap.live --ez show true
#   ⇒ bóng (đang dẫn đường) + lốp/MiMi (nếu VietMap đang muốn) quay lại trên <CỤM>
adb shell am broadcast -a com.byd.clusternav.VM_BUBBLE_POS -p vn.vietmap.live --ei x 500 --ei y 300
adb shell dumpsys window windows | grep -A22 "u0 vn.vietmap.live}" | grep mFrame   # ⇒ left≈500, top≈300
```
[CHƯA BIẾT] tên màn phụ của máy ảo có chứa `xdja`/`fission` không — không có thì bóng ở màn chính là ĐÚNG hành vi; xem `dumpsys
display` trước khi kết luận. Trên xe mà Wi-Fi tắt (đang CarPlay/AA) thì đi adb loopback / `ClusterDiag` (CLAUDE.md §11), không bắt
người dùng gõ lệnh. Thấy sập (pid `system_server`/`surfaceflinger` đổi) là DỪNG, không thử lại.

---

## 9. Hộp "Hệ thống IVI không hỗ trợ hoạt động này"

Không phải lỗi mod, không phải quyền bong bóng (spec 2.89 B2 · VM-PREREQ-TRUTH):
- [ĐO nguồn ROM] chuỗi chỉ có ở CarSetting, hiện bởi `com.byd.systemsettings.unsupport.UnsupportActivity` (hộp BydDialog nút OK) — đích
  DUY NHẤT của `android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (ROM không có `com.android.settings` ⇒ không có hộp chọn app ⇒
  người dùng KHÔNG có đường giao diện nào để tự miễn pin).
- [ĐO smali] VietMap gọi đúng ý-định đó ở MỌI lần `MainActivity.onCreate` khi `isIgnoringBatteryOptimizations` = false (giống từng byte
  ở 3.4.0 gốc, mod 3.4.0, 3.4.3).
- ⇒ Gói chưa được miễn pin (vừa cài lại, ROM xoá miễn pin khi gỡ) ⇒ hộp hiện mỗi lần mở. Chữa: `cmd deviceidle whitelist
  +vn.vietmap.live` — Kachi 2.89+ tự làm theo sự thật (đọc → áp phần thiếu → đọc lại), không còn cờ một-lần. Mod KHÔNG vá đoạn này
  (để nguyên hành vi gốc).

---

## 10. Lịch sử — 3.4.0 (08/2026)

Giữ lại sự thật còn dùng; cách làm cụ thể đã thay bằng §5–§6.
- Bóng cũng là `Lvn/vietmap/live/b;`, cấp WindowManager từ `VMBluetoothService.onCreate`; setter vị trí `E(II)V`, getter LayoutParams
  `q()`, field/getter `s`/`g()`. MiMi = `Lb/b;` qua `Lc/o;->c()` — 3.4.0 KHÔNG đụng MiMi.
- Redirect 3.4.0: `getDisplay(1)` rồi `getDisplay(2)` (máy ảo API 34) → `createDisplayContext` → `getSystemService("window")`, null ⇒
  WindowManager mặc định. **Bỏ** từ 3.4.3: id 1 có thể là ô private của Kachi, id cụm trôi (§6.1).
- [ĐO máy ảo 08/2026] mapping vị trí 1-1 sau fix gravity/`NO_LIMITS`: x=100/500/1200 → `left`=100/500/1200, y=100 → `top`=100.
- [ĐO xe 08/2026] bóng lên cụm khi Cluster Cast BẬT. [ĐO xe 06/10, 3.4.3 v1] bóng nằm trên cụm cả khi tắt chiếu (màn cụm có từ lúc đầu
  máy khởi động) — giả thuyết 08/2026 "chỉ khi Cast ON" là của cách chọn theo id.
- Hướng bỏ: mirror 2 màn / daemon SurfaceControl (owner 2026-08-28: "không có value") — `docs/diagnostics/vietmap-cluster-surfacecontrol-mirror-2026-08-27.md`,
  `tools/vietmap-cluster-mirror/`. Spec UI vị trí: `docs/specs/vietmap-overlay-position-ui.html`.
- 3.4.0 cài kiểu `install-multiple` (ký mọi split cùng khoá) hoặc APKEditor universal; từ 3.4.3 chỉ dùng universal.

**Số mục cũ → mới** (tài liệu khác còn trỏ số cũ): §1 công cụ → §2 · §2 decode → §5.1 · §3 tìm target → §5.2 · §4 redirect → §6.1–6.2
· §5–§7 dựng/ký/gộp/cài → §4, §5.5, §8 · §8 lên cụm xe → §10 · §10 receiver vị trí → §6.3 · **§11 (3.4.3)**: 11.1 → §3 · 11.2 → §5.2 ·
11.3 → §5.4 · 11.4 → §6.1 · 11.5 → §6.2, §6.5, §6.7 · 11.6 → §6.3, §6.6 · 11.7 → §5.5, §8.

## Giới hạn
Thử nghiệm sở thích, KHÔNG cam kết an toàn lái xe. Mod phá chữ ký gốc VietMap (cài như app tự ký — không nhận cập nhật từ cửa hàng;
mỗi bản VietMap mới phải mod lại theo §5). Không phát hành APK mod công khai; khoá `<KS>` và mật khẩu chỉ owner giữ.
