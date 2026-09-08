# Handoff — VietMap float/overlay whitelist (`vm-float-whitelist`)

> **Trạng thái**: Done off-car (CHỜ TEST XE) · **Ngày**: 2026-09-06 · **Loại**: Handoff (tạm)
> **Mục đích 1 dòng**: Thêm bản mod VietMap vào whitelist float/overlay của BYD IVI để bóng-trên-cụm không còn dính toast *"Hệ thống IVI không hỗ trợ hoạt động này"*.
> NO git · NO version change (theo yêu cầu task).

## Vấn đề

BYD IVI giữ một CSV toàn cục `settings global byd_float_app_list` = danh sách gói được phép vẽ cửa sổ float/overlay. Gói KHÔNG có trong list bị từ chối overlay kèm toast *"Hệ thống IVI không hỗ trợ hoạt động này"*. Bản mod VietMap (`vn.vietmap.live`) vẽ bóng lên cụm ⇒ cần CẢ HAI: có mặt trong `byd_float_app_list` VÀ `appops SYSTEM_ALERT_WINDOW allow`.

Cơ chế này đã proven trong `AssistantLauncher.setSystemAssistant` (append Google/Gemini + self vào cùng list) — tái dùng đúng công thức đó.

## Thay đổi (files)

| # | File | Thay đổi |
|---|------|----------|
| 1 | `core/.../core/FloatAppList.kt` | **MỚI** — `object FloatAppList { fun merge(current, add) }`. Trích nguyên logic split/trim/lọc-rỗng+`"null"`/append/distinct/join từ AssistantLauncher. Thuần JVM (không Android), ở `:core`. |
| 2 | `app/.../modules/voicekey/AssistantLauncher.kt` | Refactor: merge inline → `FloatAppList.merge(cur, listOf(PKG_GSA, PKG_BARD, app.packageName))` (behavior-identical). Thêm import. |
| 3 | `app/.../Prefs.kt` | Thêm cờ một-lần `vmFloatWhitelistApplied(ctx)` / `setVmFloatWhitelistApplied(ctx, v)` (key `"vm_float_whitelist_applied"`, default false) — kiểu giống `disclaimerShown` / doze guard. |
| 4 | `app/.../VietMapAutostart.kt` | Wire recipe vào TRONG `sessionResult { sh -> ... }` của `runNow`, sau `running`, trước launch/skip: gate `vmBubbleEnabled(app) && !vmFloatWhitelistApplied(app)` → đọc `byd_float_app_list` → `FloatAppList.merge(cur, listOf(PKG))` → `settings put global byd_float_app_list <merged>` → `appops set $PKG SYSTEM_ALERT_WINDOW allow` → set cờ. Bọc `runCatching`. Thêm import. |
| 5 | `core/.../core/FloatAppListTest.kt` | **MỚI** — 9 unit test cho `merge` (empty/null current, dedup, preserve others, append, idempotent, trim, reproduce recipe 3-gói). |
| 6 | `app/.../VmFloatWhitelistWiringTest.kt` | **MỚI** — 4 source-contract test (`:app` không có Robolectric): merge dùng chung ở CẢ HAI caller · VietMapAutostart ghi list + appops · gate bubble+cờ đứng trước lệnh ghi · cờ chỉ set khi thành công + degrade-safe. |

### Recipe (VietMapAutostart.runNow, trong session dadb uid-shell)

```kotlin
if (Prefs.vmBubbleEnabled(app) && !Prefs.vmFloatWhitelistApplied(app)) {
    runCatching {
        val curFloat = sh("settings get global byd_float_app_list").output.trim()
        val mergedFloat = FloatAppList.merge(curFloat, listOf(PKG))   // PKG = vn.vietmap.live
        sh("settings put global byd_float_app_list $mergedFloat")
        sh("appops set $PKG SYSTEM_ALERT_WINDOW allow")
        Prefs.setVmFloatWhitelistApplied(app, true)                   // CHỈ set khi 2 lệnh trên không ném
        Log.i(TAG, "float-whitelist: ... (list=$mergedFloat)")
    }.onFailure { Log.w(TAG, "float-whitelist: áp dụng thất bại, sẽ thử lại lần sau: ${it.message}") }
}
```

## Thiết kế (điểm chốt)

- **DRY**: một helper `FloatAppList.merge` dùng bởi CẢ HAI đường (AssistantLauncher + VietMapAutostart). Không clobber gói khác (append + distinct, existing-first).
- **Degrade-safe**: recipe bọc `runCatching`; hỏng KHÔNG ném ⇒ không chặn logic launch/skip phía dưới. Chạy trong `sessionResult(... BACKGROUND_READ_CAP)` (không chờ+thử-lại — hợp lệ vì đường nền, khớp F6).
- **Một-lần + retry-khi-hỏng**: cờ set NẰM TRONG `runCatching`, SAU 2 lệnh `sh`. Hỏng giữa chừng (vd dadb rớt / emulator không loopback) ⇒ cờ KHÔNG set ⇒ lần autostart kế thử lại. Thành công ⇒ set ⇒ không lặp mỗi autostart.
- **Quyền**: chạy trên dadb uid-shell (cùng phiên `runNow`) nên có quyền ghi `Settings.Global` + `appops`.
- **Giữ nguyên** toàn bộ hành vi runNow cũ: cooldown/inFlight (`tryBeginRun`/`finishRun`), `hasActivityRecord`, foreground-skip, nhánh cast/silent-bg launch.

## Bằng chứng [ĐO]

- **Build**: `./gradlew :app:assembleDebug -q` → `BUILD_EXIT=0`.
- **Full test 5 module** `test --rerun-tasks --continue -Dorg.gradle.parallel=false` → **BUILD SUCCESSFUL**, tổng **1957 test / 0 fail / 0 error / 0 skip** (app · car-integration · core · offcar-planner · vehicle-contracts). `FloatAppListTest` = 9/0; `VmFloatWhitelistWiringTest` = 4/0.
- **Grep EXIT**:
  - `FloatAppList.merge` định nghĩa ở `core/.../FloatAppList.kt:27`, dùng bởi `AssistantLauncher.kt:261` + `VietMapAutostart.kt:144`.
  - `VietMapAutostart.kt`: gate `:141`, `settings put global byd_float_app_list :145`, `appops set $PKG SYSTEM_ALERT_WINDOW allow :146`, `setVmFloatWhitelistApplied(app, true) :147`, `const val PKG = NavApps.VIETMAP_LIVE :23`.
  - `NavApps.VIETMAP_LIVE = "vn.vietmap.live"` ⇒ `appops set $PKG` = `appops set vn.vietmap.live SYSTEM_ALERT_WINDOW allow`.
- **Red/green (test không mù)**: tắt tạm dòng `appops` → `VmFloatWhitelistWiringTest` **1/4 ĐỎ**; khôi phục → **4/4 XANH**. Marker tạm đã gỡ (grep = 0).

## Còn lại / CHỜ TEST XE

- Recipe chạy phía XE (đường dadb uid-shell). Trên emulator không có loopback `localhost:5555` ⇒ session không mở ⇒ recipe không chạy, cờ không set (đúng degrade-safe) — sẽ áp dụng khi lên xe.
- **CHỜ TEST XE**: bật toggle bóng VietMap → mở app/nổ máy → xác nhận (a) không còn toast "IVI không hỗ trợ", (b) bóng VietMap hiện trên cụm, (c) log `VietMapAutostart` in `float-whitelist: ... (list=...)` một lần.
- NO git, NO version change (theo task) — chưa commit, chưa bump versionCode/Name.
