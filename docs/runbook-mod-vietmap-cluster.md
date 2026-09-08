# RUNBOOK — Mod VietMap: dời bong bóng dẫn đường ra CỤM

> **Trạng thái**: Runbook (thủ tục lặp lại) · **Cập nhật**: 2026-08-28 · **Mục đích**: hướng dẫn vá (mod) APK
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
