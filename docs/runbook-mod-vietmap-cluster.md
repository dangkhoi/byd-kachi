# RUNBOOK — Mod VietMap: dời bong bóng dẫn đường ra CỤM

> **Trạng thái**: Runbook (thủ tục lặp lại) · **Cập nhật**: 2026-10-05 (§11 VietMap 3.4.3 — port smali, APK chưa dựng;
> backlog `VIETMAP-343-MOD`) · trước đó 2026-08-28 · **Mục đích**: hướng dẫn vá (mod) APK
> VietMap để bong bóng nav render THẲNG trên cụm (chỉ-cụm, không phải copy 2 màn). Lặp lại mỗi khi VietMap ra bản
> mới. Chốt của owner 2026-08-28: đi đường **mod** (bỏ hướng no-mod mirror 2-màn vì không có value).

## 0. TRẠNG THÁI THẬT (đọc trước)
- **[ĐO] Trên emulator-5556 (API29)**: mod chạy — bong bóng dời sang display phụ (cụm-equiv display 1); receiver
  chỉnh vị trí + fix mapping **1-1 XÁC NHẬN** (§10).
- **[ĐO] Trên xe — CHẠY khi Cluster Cast ON**: khi bật Cluster Cast (cụm "sống", display phụ thành PRESENTATION
  thật) → bong bóng nav mod **LÊN ĐƯỢC cụm**. Đây là đường chốt của owner (không cần daemon mirror 2-màn).
- **[SUY] Vì sao cần Cast ON**: cụm xe (`fission_bg_xdjaVirtualSurface`, VIRTUAL, owner `com.xdja.containerservice`)
  chỉ nhận `addView` khi Cluster Cast đã dựng bề mặt; lúc đó `getDisplay(cụm)` trả display hợp lệ để bong bóng bám.
- ⇒ Runbook đủ để **re-mod nhanh** mỗi bản VietMap mới: §2 decode · §3 tìm target · §4 redirect display · §10
  receiver vị trí + **fix mapping** · §5–7 build/ký/gộp/cài. Bước từng-mò-lâu = **§3 tìm target** (obfuscation) +
  **§10 fix mapping** (gravity/flags) — đã ghi đủ smali thật bên dưới.
- **Từ 3.4.3 (05/10) đọc §11 TRƯỚC**: cách chọn màn cụm của §4 (`getDisplay(1)/(2)`) không còn tin được trên xe có ô Kachi
  (ô là màn private, có thể chiếm id 1 — cơ chế ở §11.4) ⇒ 3.4.3 chọn theo TÊN; `classes.dex` đã đầy 65 536 method; không
  dùng APK của Aurora bản BYD.

## 1. Công cụ (đã có trên máy)
- `apktool` (brew, 3.0.3) — decode/build smali
- JDK 17 (`/opt/homebrew/opt/openjdk@17`) — `keytool`, chạy jar
- Android build-tools 34.0.0 — `zipalign`, `apksigner`
- `APKEditor.jar` (1.4.9, `/tmp/APKEditor.jar`) — gộp split → 1 APK universal
- APK gốc: xapk **arm64** (APKCombo — `apk-ref/…apkcombo.com.xapk`). ⚠ APKPure xapk từng chỉ có armeabi-v7a → fail arm64-only.

## 2. Decode
```bash
mkdir -p /tmp/vmmod && cd /tmp/vmmod
unzip -o "<xapk>" -d xapk           # xapk = zip chứa base.apk + config.*.apk
apktool d -f -o dec xapk/*.apk       # hoặc apktool d base.apk (file base trong xapk)
```

## 3. TÌM TARGET (QUAN TRỌNG — obfuscation đổi tên mỗi bản!)
Bản 3.4.0: bong bóng nav = class `Lvn/vietmap/live/b;` (+ `Lta/c;`), được cấp `WindowManager` từ
**`VMBluetoothService.onCreate()`** qua `getSystemService("window")`. Bản mới TÊN CÓ THỂ KHÁC → tìm lại:

1. **Tìm class vẽ overlay** (addView kiểu TYPE_APPLICATION_OVERLAY = 2038):
   ```bash
   grep -rlE "Landroid/view/WindowManager;->addView" dec/smali*   # các class addView
   grep -rn "const/16 [pv][0-9]*, 0x7f6" dec/smali*               # 0x7f6 = 2038 (TYPE_APPLICATION_OVERLAY)
   ```
2. **Tìm Service cấp WindowManager cho nó**: class `extends Landroid/app/Service;`, trong `onCreate()` gọi
   `getSystemService("window")` rồi `new <overlay>(Context, WindowManager)`:
   ```bash
   grep -rn 'const-string v[0-9]*, "window"' dec/smali* | grep -i service
   ```
3. **PHÂN BIỆT bong bóng NAV với overlay MiMi (trợ lý)**: MiMi = `Lb/b;` (MimiFloatingView) qua `Lc/o;->c()`
   trong `smali/c.1/o.smali` — **ĐỪNG patch cái này**. Nav bubble đi từ service Bluetooth/nav
   (`VMBluetoothService` ở 3.4.0). Nếu nghi ngờ: cài bản chưa vá, `dumpsys window | grep vn.vietmap.live` khi
   ĐANG DẪN ĐƯỜNG → cửa sổ overlay hiện nội dung nav (mũi tên/cự ly) chính là target.

## 4. Patch — chèn redirect display TRƯỚC `getSystemService("window")`
Trong `onCreate()` của service target: đổi khối lấy WindowManager sang lấy từ **display cụm**. Tăng `.locals`
đủ (bản 3.4.0 lên `.locals 3`). Smali THẬT đã dùng (3.4.0):
```smali
    const-string v0, "display"
    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/hardware/display/DisplayManager;
    const/16 v1, 0x1                       # thử cụm = display 1 (xe) trước
    invoke-virtual {v0, v1}, Landroid/hardware/display/DisplayManager;->getDisplay(I)Landroid/view/Display;
    move-result-object v1
    if-nez v1, :mod_have_disp
    const/16 v2, 0x2                       # rồi display 2 (emulator API34)
    invoke-virtual {v0, v2}, Landroid/hardware/display/DisplayManager;->getDisplay(I)Landroid/view/Display;
    move-result-object v1
    :mod_have_disp
    if-eqz v1, :mod_fallback_wm
    invoke-virtual {p0, v1}, Landroid/content/Context;->createDisplayContext(Landroid/view/Display;)Landroid/content/Context;
    move-result-object v0
    const-string v1, "window"
    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    goto :mod_done_wm
    :mod_fallback_wm
    const-string v0, "window"
    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    :mod_done_wm
    check-cast v0, Landroid/view/WindowManager;
    # … tiếp: new-instance <nav-bubble>, invoke-direct {…, p0, v0} (giữ nguyên phần gốc)
```
> **Tốt hơn (khuyến nghị bản sau)**: thay `getDisplay(1)/(2)` bằng dò `getDisplays(CATEGORY_PRESENTATION)` → id != 0
> (ổn định mọi thiết bị). Xem `docs/diagnostics/vietmap-cluster-surfacecontrol-mirror-2026-08-27.md`.

## 5. Build lại
```bash
apktool b dec -o /tmp/vmmod/base-mod.apk
```

## 6. Ký (keystore tự tạo — dùng lại cùng key để cài đè giữ login)
```bash
export PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH
keytool -genkeypair -v -keystore /tmp/vmmod/mod.keystore -alias vmmod \
  -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12 \
  -storepass modmodmod -keypass modmodmod -dname "CN=vmmod"
ZA=~/Library/Android/sdk/build-tools/34.0.0/zipalign
AS=~/Library/Android/sdk/build-tools/34.0.0/apksigner
"$ZA" -f 4 /tmp/vmmod/base-mod.apk /tmp/vmmod/base-mod-aligned.apk
"$AS" sign --ks /tmp/vmmod/mod.keystore --ks-pass pass:modmodmod --ks-key-alias vmmod /tmp/vmmod/base-mod-aligned.apk
```

## 7. Gộp split → 1 APK universal (cài 1-file) + cài
VietMap là app split (base + config.arm64_v8a + hdpi + vi/en). Hai cách:
- **install-multiple** (nhiều file): ký TẤT CẢ split cùng key rồi
  `adb install-multiple -r base-mod-signed.apk config.arm64_v8a.apk config.hdpi.apk config.vi.apk config.en.apk`
- **APKEditor** (1 file universal):
  ```bash
  java -jar /tmp/APKEditor.jar merge -i /tmp/vmmod/xapk -o /tmp/vmmod/universal.apk
  # rồi ký universal.apk như §6, adb install -r universal.apk
  ```
Cấp quyền overlay + verify:
```bash
adb shell appops set vn.vietmap.live SYSTEM_ALERT_WINDOW allow
adb shell monkey -p vn.vietmap.live -c android.intent.category.LAUNCHER 1
# dẫn đường → kiểm bong bóng ở display nào:
adb shell dumpsys window windows | grep -A1 "u0 vn.vietmap.live}"   # mong mDisplayId = display cụm
```

## 8. LÊN CỤM XE — việc mở (vì sao mod cũ chưa tới cụm xe + cách sửa)
`addView` vào cụm xe (`xdja`) bị từ chối. 3 hướng thử (theo thứ tự dễ→khó):
1. **Xác minh + retry**: cụm xe có thể chỉ "sống" khi đang cast/projection. Trên xe: `dumpsys display | grep -i
   presentation` xem cụm có tồn tại + FLAG_PRESENTATION khi VietMap chạy; nếu `getDisplay(1)` trả null lúc
   onCreate → mod cần **đăng ký DisplayListener + addView lại khi display cụm xuất hiện** (mod one-shot hiện tại bỏ lỡ).
2. **Đổi target display bằng `getDisplays(PRESENTATION)`** (§4 khuyến nghị) — nếu cụm xe id khác 1.
3. **Trỏ bong bóng vào virtual display của daemon** (kết hợp daemon SurfaceControl): daemon `app_process` uid shell
   tạo VD (đã proven createDisplay được ở uid shell), mod trỏ bong bóng vào VD đó (friendly, addView chạy), daemon
   route VD → cụm. Né hẳn bức tường xdja. Xem `docs/diagnostics/vietmap-cluster-surfacecontrol-mirror-2026-08-27.md`
   + `tools/vietmap-cluster-mirror/`.

## 9. Mỗi bản VietMap mới → lặp
1. Tải xapk arm64 mới (APKCombo). 2. Decode (§2). 3. **Tìm lại target** (§3 — tên obfuscated đổi!). 4. Patch (§4).
5. Build/ký/gộp/cài (§5–7). 6. Verify display (§7). Giữ cùng `mod.keystore` để cài đè không mất login.
Từ 3.4.3 thêm: nguồn APK Google ký (§11.1) · đếm method id mỗi dex TRƯỚC khi chèn (§11.3) · đối chiếu bảng đổi tên (§11.2)
· chọn màn theo tên + listener (§11.4–11.5) · các bước dựng §11.7.

## Giới hạn
Thử nghiệm sở thích, KHÔNG cam kết an toàn lái xe. Mod phá chữ ký gốc VietMap (cài như app tự-ký, cạnh app gốc
hoặc thay — tự chịu rủi ro cập nhật/CI của VietMap).

## 10. (Item 4) Inject receiver chỉnh VỊ TRÍ bong bóng qua broadcast (UI ClusterNav)
Cho phép ClusterNav bắn `am broadcast` để dời bong bóng trên cụm (spec `docs/specs/vietmap-overlay-position-ui.html`).
Bong bóng có sẵn setter **`Lvn/vietmap/live/b;->E(II)V`** (set LayoutParams x/y + `updateViewLayout`). Service giữ
bong bóng ở field `Lvn/vietmap/live/VMBluetoothService;->s` + getter `g()Lvn/vietmap/live/b;`.

1. **Thêm class receiver** `smali_classes2/vn/vietmap/live/VMBluetoothService$posrx.smali`:
   - `super Landroid/content/BroadcastReceiver;`, field `a:Lvn/vietmap/live/VMBluetoothService;`.
   - `onReceive`: đọc `getIntExtra("x"/"y",-1)`; nếu cả hai ≥0 → `a.g()` lấy bong bóng (null-check) → **FIX MAPPING
     TOẠ ĐỘ TUYỆT ĐỐI** (bắt buộc, xem dưới) → `E(x,y)` (setter x/y + `updateViewLayout`).

   **FIX MAPPING (quan trọng — nếu bỏ, toạ độ lệch ~775px trục X + ~245px trục Y):** bong bóng gốc dùng
   `gravity=TOP|CENTER_HORIZONTAL` + không có `FLAG_LAYOUT_NO_LIMITS` ⇒ `E(x,y)` bị hiểu là **offset từ tâm**
   + bị chèn theo status-bar inset ⇒ lệch. Sửa: trước khi gọi `E`, lấy LayoutParams của bong bóng qua
   **`Lvn/vietmap/live/b;->q()Landroid/view/WindowManager$LayoutParams;`** rồi ép:
   - `gravity = 0x33` (= `TOP|LEFT` = Gravity.TOP 0x30 | Gravity.LEFT 0x03) ⇒ x/y tính từ **góc trên-trái**.
   - `flags |= 0x200` (= `FLAG_LAYOUT_NO_LIMITS`) ⇒ cho phép đặt ra ngoài vùng an toàn, bỏ inset.
   Sau 2 dòng này, `E(x,y)` = đặt **góc trên-trái bong bóng đúng pixel (x,y)** trên cụm (1-1). Smali THẬT (3.4.0):
   ```smali
   .method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
       .locals 6
       if-eqz p2, :cond_done
       const-string v0, "x"
       const/4 v1, -0x1
       invoke-virtual {p2, v0, v1}, Landroid/content/Intent;->getIntExtra(Ljava/lang/String;I)I
       move-result v0
       const-string v2, "y"
       invoke-virtual {p2, v2, v1}, Landroid/content/Intent;->getIntExtra(Ljava/lang/String;I)I
       move-result v2
       if-ltz v0, :cond_done
       if-ltz v2, :cond_done
       iget-object v3, p0, Lvn/vietmap/live/VMBluetoothService$posrx;->a:Lvn/vietmap/live/VMBluetoothService;
       invoke-virtual {v3}, Lvn/vietmap/live/VMBluetoothService;->g()Lvn/vietmap/live/b;
       move-result-object v3
       if-eqz v3, :cond_done
       invoke-virtual {v3}, Lvn/vietmap/live/b;->q()Landroid/view/WindowManager$LayoutParams;   # getter LayoutParams
       move-result-object v4
       if-eqz v4, :cond_apply
       const/16 v5, 0x33                                                                        # TOP|LEFT
       iput v5, v4, Landroid/view/WindowManager$LayoutParams;->gravity:I
       iget v5, v4, Landroid/view/WindowManager$LayoutParams;->flags:I
       or-int/lit16 v5, v5, 0x200                                                                # FLAG_LAYOUT_NO_LIMITS
       iput v5, v4, Landroid/view/WindowManager$LayoutParams;->flags:I
       :cond_apply
       invoke-virtual {v3, v0, v2}, Lvn/vietmap/live/b;->E(II)V                                  # đặt x/y + updateViewLayout
       :cond_done
       return-void
   .end method
   ```
   > ⚠ Bản VietMap mới: tên `b`/`E`/`q`/`g`/`s` có thể đổi (obfuscation). Tìm lại: `E(II)V` = method 2 tham số int
   > trong class bong bóng gọi `updateViewLayout`; `q()` = getter trả `WindowManager$LayoutParams` (grep
   > `->q()Landroid/view/WindowManager$LayoutParams;`); nếu không có getter, đọc field LayoutParams trực tiếp.
2. **Đăng ký trong `VMBluetoothService.onCreate`** (SAU `iput-object … ->s`): bump `.locals` đủ (vd 3→6), rồi:
   `new posrx(p0)` → `new IntentFilter("com.byd.clusternav.VM_BUBBLE_POS")` → `registerReceiver(rx, filter)`
   (2-arg, Android 10 tự EXPORTED — API33+ mới cần cờ RECEIVER_EXPORTED).
3. Rebuild/ký/gộp/cài (§5–7). **Test E2E cần VietMap ĐÃ LOGIN** (VMBluetoothService chỉ chạy + tạo bong bóng sau
   login) → cài đè `install -r` cùng key để **giữ login**, rồi dẫn đường cho bong bóng dựng lại.
   - **[ĐO 2026-08-28] mapping 1-1 XÁC NHẬN trên emulator-5556**: gửi x=100/500/1200 → `left`=100/500/1200;
     y=100 → `top`=100 (trước khi thêm NO_LIMITS lệch +245 trục Y; trước khi bỏ convert center-offset lệch −775 trục X).
     Bong bóng dời đúng pixel, owner xác nhận "nhìn OK".

**Phương pháp calibration (đo mapping thật, không đoán):**
```bash
ADB=~/Library/Android/sdk/platform-tools/adb; E="-s emulator-5556"
# gửi 1 toạ độ đã biết:
$ADB $E shell am broadcast -a com.byd.clusternav.VM_BUBBLE_POS -p vn.vietmap.live --ei x 500 --ei y 300
# đọc frame THẬT của cửa sổ overlay bong bóng:
$ADB $E shell dumpsys window windows | grep -iE "u0 vn.vietmap.live}" -A22 | grep mFrame
# mong mFrame=[left,top][right,bottom] với left≈500, top≈300 (1-1). Lệch ⇒ chỉnh gravity/flags/offset ở posrx.
```

**Pipeline build/ký/gộp THẬT đã dùng (repeatable):**
```bash
# nguồn decode: /tmp/vmdecode (apktool d) — sau khi sửa smali:
apktool b /tmp/vmdecode -o /tmp/vmbuild/base-mod3.apk
cp /tmp/vmbuild/base-mod3.apk /tmp/vmmerge/base.apk          # thư mục merge chứa base + config.* splits
java -jar /tmp/APKEditor.jar merge -i /tmp/vmmerge -o /tmp/vmbuild/universal-mod3.apk -f
export PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH
ZA=~/Library/Android/sdk/build-tools/34.0.0/zipalign; AS=~/Library/Android/sdk/build-tools/34.0.0/apksigner
"$ZA" -f 4 /tmp/vmbuild/universal-mod3.apk /tmp/vmbuild/universal-mod3-al.apk
"$AS" sign --ks /tmp/vmbuild/mod2.keystore --ks-pass pass:modmodmod --ks-key-alias vmmod /tmp/vmbuild/universal-mod3-al.apk
$ADB $E install -r /tmp/vmbuild/universal-mod3-al.apk        # -r + cùng key = giữ login
```
- **APK mod hiện hành** (có receiver + fix mapping): `/tmp/vmbuild/universal-mod3-al.apk`, giao owner ở
  `~/Desktop/ClusterNav-oncar-test/VietMap-3.4.0-mod-cluster.apk`. Keystore **`/tmp/vmbuild/mod2.keystore`**
  (pass `modmodmod`, alias `vmmod`) — GIỮ để cài đè bản sau không mất login.
- **Phía ClusterNav** bắn broadcast này: `VmOverlayPosition.kt` (`send()` gate `castOn()`, `applyOnOpen()`,
  `setAbsoluteTopLeft()`, preset `presetRightHalf()`), UI kéo-thả `VmBubblePlacementView.kt`, prefs `vm_bubble_x/y`
  (px góc-trên-trái tuyệt đối). Áp lại vị trí 3 nơi: `MainActivity.onResume`, nút "Áp dụng", vòng refresh
  `FloatingBubbleService` (mỗi chu kỳ khi Cast ON).

## 11. VietMap 3.4.3 (2026-10-05) — port mod

> **Trạng thái**: smali đã port + soát (thư mục làm việc ngoài repo); **APK CHƯA dựng** — bước `apktool b` của workflow bị
> bộ phân loại quyền từ chối ⇒ **owner tự dựng** theo §11.7. Mod chưa chạy trên máy ảo hay xe [CHƯA BIẾT]. Mod KHÔNG nằm
> trong APK Kachi: Kachi chỉ gửi broadcast `com.byd.clusternav.VM_BUBBLE_POS` (hợp đồng §10 không đổi). Backlog
> `VIETMAP-343-MOD`. Mức bằng chứng theo CLAUDE.md §2; số dòng `tệp:dòng` của framework xe là của bản dịch ngược (jadx).

Ký hiệu chỗ giữ (không ghi đường dẫn máy): `<XAPK>` thư mục giải nén xapk Google · `<AURORA>` thư mục split Aurora ·
`<MOD>` cây apktool đã sửa smali · `<OUT>` thư mục ra · `<BT>` Android build-tools 34 · `<KS>` keystore mod (alias `vmmod`,
cùng khoá các bản mod trước — §6) · `<APKEDITOR>` tệp jar APKEditor.

### 11.1 Nguồn APK — dùng bản Google ký, KHÔNG dùng Aurora bản BYD
- **[ĐO]** xapk APKPure 3.4.3 = `base` + `config.arm64_v8a` + `config.en` + `config.mdpi`; cả 4 cùng chữ ký Google
  (`684cedb4…`); split arm64 có 13 thư viện (`libapp.so`, `libflutter.so`…). Cài 4 split trên AVD Android 10 arm64 ⇒ mở
  được, không crash, giao diện tiếng Việt, tới màn onboarding. (Ghi chú §1 "APKPure chỉ có armeabi-v7a" là của 3.4.0.)
- **[ĐO]** Aurora Store bản BYD (`com.aurora.store.byd`): `base.apk` bị **đóng gói lại kèm bộ giả chữ ký** (`com.kangrio`),
  ký CN=AuroraStore (`c908e973…`) ⇒ KHÔNG lấy base hay split có mã của Aurora.
- xapk Google KHÔNG có split `vi` (base mặc định "Navigate home") ⇒ mượn **split chỉ-tài-nguyên** của Aurora:
  `split_config.vi.apk` (có "Chỉ đường về nhà") + `split_config.hdpi.apk`. **[ĐO]** cả hai chỉ có manifest +
  `resources.arsc` (+ `res/`), `hasCode=false`, không `.dex`/`.so`. APKEditor gộp rồi ký lại bằng `<KS>` ⇒ chữ ký Aurora
  không còn trong APK ra. Không có split `vi` ⇒ chữ phía Android (lối tắt, thông báo) ra tiếng Anh.
- Hệ quả cài: mod (CN=vmmod) khác khoá bản đang cài (Google/Aurora) ⇒ phải **gỡ** trước ⇒ đăng nhập lại [SUY] + ROM xoá
  miễn tối ưu pin của gói [ĐO nguồn — spec `kachi-289-field-fixes.html` B2-E5]; Kachi 2.89 tự miễn lại + cấp
  `SYSTEM_ALERT_WINDOW` theo sự thật (`VM-PREREQ-TRUTH`).

### 11.2 Bảng đổi tên 3.4.0 → 3.4.3 [ĐO decode base Google]
| Thành phần | 3.4.0 | 3.4.3 | Vai |
|---|---|---|---|
| lớp bóng dẫn đường | `Lvn/vietmap/live/b;` | không đổi | — |
| cửa sổ áp suất lốp · MiMi · view MiMi | `Lta/c;` · `Lc/o;` · `Lb/b;` | không đổi (`Lb/b;` lệch một id tài nguyên) | — |
| field bóng của service · getter | `VMBluetoothService->s` · `g()` | không đổi | `g()` NÉM khi `s` null |
| đặt vị trí | `E(II)V` | `L(II)V` | gán x/y + `updateViewLayout` |
| getter LayoutParams | `q()` | `u()` | §10 fix mapping |
| hiện bóng | `G()` | `O()` | `addView` + cờ đang hiện |
| khởi tạo | `i()` | `m()` | inflate + dựng LayoutParams — KHÔNG `addView` (không phải "hiện") |
| ẩn bóng | `t()` | `x()` | gỡ qua `WindowManagerGlobal` |
| làm mới | `h()` | `l()` | — |
| getter WindowManager | `s()` | `w()` | — |
| (đổi theo cùng lượt, mod không dùng) | `j()` · `I()` | `n()` · `Q()` | — |

### 11.3 Giới hạn 65 536 method id của một dex
- **[ĐO `dexdump`]** `classes.dex` của 3.4.3 gốc đã đủ **65 536 / 65 536** method id (`classes2.dex` 52 982). Thêm MỘT tham
  chiếu vào lớp nằm ở `classes.dex` (lời gọi `ModClusterDisplay.find` trong `Lc/o;`) ⇒ 65 537 ⇒ `apktool b` không dựng được.
  So sánh: 3.4.0 gốc 65 531, mod 3.4.0 65 531 / 52 731.
- **Cách**: dời nguyên tệp `smali/c.1/o.smali` (`Lc/o;`) sang một thư mục dưới `smali_classes2/` (apktool dựng mỗi thư mục
  `smali_classesN` thành `classesN.dex`; đường dẫn tệp trong thư mục không đổi tên lớp) ⇒ 65 528 / 53 021 [ĐO đếm bằng
  script, khớp `dexdump` trên 4 dex thật]. Mọi lớp mod MỚI đặt ở `smali_classes2/`. Chạy được sau khi dời: [SUY — chưa dựng].
- Bản sau: đếm `method_ids_size` từng dex TRƯỚC khi chèn (`dexdump -f classes.dex | grep method_ids_size`).

### 11.4 Chọn màn cụm theo TÊN, không theo id
- **[ĐO nguồn framework xe, dịch ngược]** `DisplayManager` chỉ trả / liệt kê màn PRIVATE cho app sở hữu hoặc đã có cửa sổ
  trên đó (`DisplayManagerService.java:446-451, 460-469`; `Display.java:521-522`). **[ĐO mã Kachi]** ô Kachi là màn ảo
  private (cờ `8 | 256`, không PUBLIC — `VdAppHost.kt:134`, `StagingDisplay` cùng cờ). **[ĐO log xe 26/09]** màn cụm
  `fission_bg_xdjaVirtualSurface` (chủ `com.xdja.containerservice`) là PUBLIC: `FLAG_PRESENTATION, FLAG_OWN_CONTENT_ONLY`,
  không `FLAG_PRIVATE`.
- **[ĐO xe 15/09]** sau khởi động nguội display 1 = `kachi-slot-0`, cụm = display 2 (id cụm từng thấy 1 / 2 / 8) ⇒
  `getDisplay(1)/(2)` của mod 3.4.0 (§4) trả null với VietMap ⇒ bóng về màn chính; hoặc VietMap đang ở trong ô ⇒ trúng ô
  Kachi [SUY từ cơ chế trên].
- Mod 3.4.3: lớp mới `Lvn/vietmap/live/ModClusterDisplay;` — `find(Context)` duyệt `DisplayManager.getDisplays()`, lấy
  display id ≠ 0 có tên (chữ thường, `Locale.ROOT`) chứa `xdja` hoặc `fission` — CÙNG từ khoá Kachi
  `DisplayParse.clusterDisplayId`; không khớp ⇒ `null` ⇒ giữ WindowManager mặc định (màn chính, như gốc). Tên màn Kachi
  (`kachi-slot-*`, `kachi-stage-*`) không bao giờ khớp. Không còn `getDisplay(<số>)` nào trong mã mod.
- [SUY] DiLink 5 có hai màn `shared_fission_bg_XDJAScreenProjection_0/_1` ⇒ `find` lấy màn Android liệt kê trước.
- [SUY từ log xe cũ] màn cụm có từ lúc khởi động ⇒ bóng lên cụm kể cả khi Cluster Cast TẮT (giống 3.4.0). Chốt bằng
  `dumpsys display | grep -E "Display [0-9]+:|xdja|mState"` lúc Cast TẮT rồi BẬT.

### 11.5 Chỗ chèn trong service + DisplayListener + rào try/catch
- `VMBluetoothService.onCreate` (`.locals 3 → 6`): WindowManager = `createDisplayContext(find(this)).getSystemService("window")`;
  `null` / không phải `WindowManager` / ném ⇒ WindowManager mặc định (đường gốc). Khối bọc `catch Throwable` (kể cả
  `VerifyError`/`NoClassDefFoundError` của lớp mod) ⇒ hỏng thì về đường gốc, không giết service. Cửa sổ lốp `Lta/c;` nhận
  CÙNG WindowManager đầu tiên như bóng (như 3.4.0); listener không dời nó.
- Hai field mới `modRx` (bộ nhận vị trí) + `modDl` (`ModClusterDisplay`) để `onDestroy` gỡ được. Sau khi bóng dựng xong:
  `registerDisplayListener(modDl, Handler(Looper.getMainLooper()))` rồi `sync()` một lần (đóng khe giữa `find` và lúc đăng
  ký). `onDisplayAdded/Changed/Removed` ⇒ `sync()`: id màn đích khác id màn hiện của bóng (`w().getDefaultDisplay()`) ⇒
  `b.modMove(wm)`; mất cụm mà bóng không ở display 0 ⇒ về màn mặc định.
- `sync()` = vỏ bắt `RuntimeException` quanh `syncImpl()` — callback chạy luồng chính, ném ở đó là chết cả app đang dẫn đường.
- Method mới `b.modMove(WindowManager)`: ẩn bằng `x()` gốc (bắt `IllegalArgumentException`/`IllegalStateException`) → đổi
  field `b` (WindowManager riêng của bóng) → hiện lại bằng `O()` CHỈ khi trước đó đang hiện; màn mới từ chối
  (`BadTokenException`/`InvalidDisplayException`/`IllegalStateException`) ⇒ về WindowManager mặc định, thử `O()` một lần.
  Chỉ bắt ngoại lệ WindowManager, mọi thứ khác ném tiếp. [ĐO nguồn xe] `addView` lại view đang gỡ dở được Android xử lý
  (`WindowManagerGlobal.java:266-267`); `createDisplayContext` chỉ ném khi display null (`ContextImpl.java:1880-1889`).
- `onDestroy`: gỡ listener + receiver TRƯỚC phần dọn gốc, bọc `catch Throwable` (hỏng không được bỏ qua phần dọn gốc).
- MiMi (`Lc/o;->c()`): cửa sổ MiMi cũng đi qua `find()` — quyết MỘT lần lúc tạo, không dời sau. [SUY] cụm mất sau đó ⇒ hiện
  MiMi / cửa sổ lốp có thể ném `InvalidDisplayException` không bắt (3.4.0 cũng vậy).

### 11.6 Bộ nhận vị trí (`posrx`) — cờ 0x2
- Cùng hợp đồng §10: action `com.byd.clusternav.VM_BUBBLE_POS`, extra int `x`/`y` = góc trên-trái tuyệt đối; trên `u()` ép
  `gravity = 0x33` + `flags |= 0x200` rồi `L(x, y)`.
- Đọc thẳng field `s` có kiểm null (KHÔNG gọi `g()` — ném khi `s` null); thân `onReceive` bọc catch (receiver chạy luồng chính).
- `registerReceiver(rx, filter, 0x2)` — `0x2` = `RECEIVER_EXPORTED`. **[ĐO nguồn AMS xe, dịch ngược]** Android 10 chỉ đọc bit
  `0x1` (`flags & 1`) ⇒ trên xe không đổi gì. Bắt buộc từ Android 14 khi `targetSdk ≥ 34` (VietMap 3.4.3 target 36) — thiếu là
  ném lúc đăng ký trên máy ảo API 34+. Không khai receiver trong manifest (cả 3.4.0 lẫn 3.4.3 đăng ký trong mã).
- Cửa sổ chạm vô hình mới của 3.4.3 không nhận cờ `0x200` ⇒ trên cụm có thể lệch so với bóng; cụm không có chạm ⇒ [SUY] vô hại.

### 11.7 Dựng + kiểm + cài (owner chạy)
1. Đóng gói base: `apktool b <MOD> -o <OUT>/base-mod.apk` (lỗi cú pháp / verifier của smali chỉ lộ ở bước này — chưa từng chạy).
2. Thư mục gộp `<OUT>/merge`: `base.apk` (= base-mod) + `config.arm64_v8a.apk`, `config.en.apk`, `config.mdpi.apk` từ
   `<XAPK>` + `config.vi.apk`, `config.hdpi.apk` chép từ split Aurora chỉ-tài-nguyên. DỪNG nếu split Aurora có mã:
   `unzip -l <AURORA>/split_config.<x>.apk | grep -E '\.dex$|\.so$'` phải rỗng.
3. Gộp: `java -jar <APKEDITOR> m -i <OUT>/merge -o <OUT>/universal.apk -f` (APKEditor — bản phát hành chính thức của
   REAndroid trên GitHub).
4. Căn + ký: `<BT>/zipalign -p -f 4 <OUT>/universal.apk <OUT>/universal-al.apk` rồi
   `<BT>/apksigner sign --ks <KS> --ks-pass pass:<mật-khẩu-khoá-mod> --ks-key-alias vmmod --out <OUT>/VietMap-3.4.3-mod-cluster.apk <OUT>/universal-al.apk`.
5. Kiểm: `apksigner verify --print-certs` (CN=vmmod) · `aapt2 dump badging` (package `vn.vietmap.live`, native-code
   `arm64-v8a`) · `unzip -l … | grep kangrio` phải RỖNG · `aapt2 dump strings … | grep "Chỉ đường về nhà"` phải có ·
   ghi sha256 + kích thước.
6. Máy ảo TRƯỚC xe: `VMBluetoothService` chạy ngay khi mở app (trước đăng nhập) ⇒ đường `onCreate` của mod thử được không
   cần tài khoản; bóng chỉ hiện khi đang dẫn đường (cần đăng nhập). [CHƯA BIẾT] tên màn phụ của máy ảo có chứa
   `xdja`/`fission` không — nếu không, bóng ở màn chính là ĐÚNG hành vi; xem tên bằng `dumpsys display` trước khi kết luận.
7. Xe: gỡ VietMap đang cài (khác khoá) → cài APK mod → đăng nhập → mở Cast cụm → dẫn đường. Kiểm
   `dumpsys window windows | grep -A1 "u0 vn.vietmap.live}"` (mDisplayId = id màn `fission`/`xdja`), rồi kéo vị trí từ Kachi
   (broadcast §10) xem bóng theo đúng pixel.
