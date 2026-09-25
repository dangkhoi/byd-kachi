package com.byd.clusternav.launcher

import com.byd.clusternav.Prefs
import com.byd.clusternav.automation.AutomationService
import com.byd.clusternav.automation.ScheduledNavApplier
import com.byd.clusternav.launcher.automation.NavAutomationBook
import com.byd.clusternav.launcher.automation.ScheduledNavRule
import com.byd.clusternav.navAutomationRules
import com.byd.clusternav.rainDefrostEnabled
import com.byd.clusternav.setNavAutomationRules
import com.byd.clusternav.setRainDefrostEnabled
import com.byd.clusternav.cameraSignalEnabled
import com.byd.clusternav.setCameraSignalEnabled
import com.byd.clusternav.cameraOnCluster
import com.byd.clusternav.setCameraOnCluster
import com.byd.clusternav.cameraPos
import com.byd.clusternav.setCameraPos
import com.byd.clusternav.cameraCamId
import com.byd.clusternav.setCameraCamId
import com.byd.clusternav.setCameraSignalEnabled

/**
 * ═══ AUTOMATION trên cầu Settings (hàm mở rộng của [ClusterNavBridge]) ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R1 · R2 · R4. Cùng khuôn `ClusterNavBridgeWake`/`…Home`/`…Keys`: màn
 * Cài đặt KHÔNG ghi `Prefs.set` trực tiếp (`SettingsScreenWiringContractTest` cấm — state trên màn và state bền
 * phải đi qua MỘT cửa). Tách tệp vì `ClusterNavBridge.kt` đã 499 dòng (trần 500, CLAUDE.md §4.1).
 *
 * ## ⚠⚠ Mỗi lượt GHI phải kèm một lượt `AutomationService.sync` — đây là phần dễ quên nhất
 * [ĐO] S4 · T2: **0 chỗ nào trong toàn dự án** đăng ký `registerOnSharedPreferenceChangeListener`. Nghĩa là ghi
 * prefs xong là *"đúng trên đĩa mà không có gì đang chạy biết"*. Với hai automation này, hậu quả cụ thể:
 *  • bật công tắc mà không `sync` ⇒ động cơ nền **không lên** tới lần nổ máy sau (người dùng kết luận nó hỏng);
 *  • tắt công tắc mà không `sync` ⇒ vòng nhịp **vẫn chạy** và còn ghi HAL sau khi đã tắt;
 *  • thêm luật đầu tiên mà không `sync` ⇒ sổ có luật nhưng không ai đánh giá nó.
 * Vì thế `sync` nằm **trong** hai setter dưới đây, không phải một bước chỗ gọi phải nhớ.
 */

/** AUTOMATION #1 — công tắc "Tự sấy kính khi mưa" (theo XE, mặc định TẮT). */
fun ClusterNavBridge.rainDefrost(): Boolean = Prefs.rainDefrostEnabled(app)

/**
 * Đặt công tắc #1 rồi đồng bộ động cơ nền NGAY (xem ⚠ ở KDoc tệp).
 *
 * Nhánh TẮT: [AutomationService.sync] cũng là chỗ **quên ký ức** R1.5 (`RainDefrostApplier.reset`) — thiếu bước
 * đó thì tắt-lúc-đang-mưa rồi bật-lại-lúc-đã-khô sẽ tắt cái sấy mà người lái có thể vừa tự bật.
 */
fun ClusterNavBridge.setRainDefrost(on: Boolean) {
    Prefs.setRainDefrostEnabled(app, on)
    AutomationService.sync(app)
}

/** Camera theo xi-nhan (owner 2026-09-22, mặc định TẮT) — công tắc đi qua cầu như mọi mục Cài đặt. */
fun ClusterNavBridge.cameraSignal(): Boolean = Prefs.cameraSignalEnabled(app)
fun ClusterNavBridge.setCameraSignal(on: Boolean) {
    Prefs.setCameraSignalEnabled(app, on)
    com.byd.clusternav.automation.AutomationService.sync(app)   // camera chạy trong FGS nền (cả khi lái) — bật/tắt phải đồng bộ service
}
fun ClusterNavBridge.cameraOnCluster(): Boolean = Prefs.cameraOnCluster(app)
fun ClusterNavBridge.setCameraOnCluster(on: Boolean) = Prefs.setCameraOnCluster(app, on)

/**
 * Góc hiện overlay camera cho từng bên xi-nhan (spec `camera-turn-signal-hal-socket.html` R3 · R4) — `"TL"`/`"TR"`.
 *
 * Hai hàm đọc RIÊNG (không một hàm nhận `left: Boolean`) vì chipRow trong Cài đặt cần **một biểu thức cho một
 * hàng**, đúng khuôn `cameraCamLeft`/`cameraCamRight` ngay trên. Lượt GHI thì dùng chung [setCameraPos] — ghi là
 * một việc có tham số, đọc là hai hàng trên màn.
 *
 * KHÔNG kèm `AutomationService.sync`: đây là *chỗ hiện*, không phải công tắc bật/tắt động cơ nền. Lượt rẽ sau đọc
 * lại pref (vòng nhịp gọi `Prefs.cameraPos` mỗi lần dựng overlay) nên giá trị mới ăn ngay mà không cần đánh thức
 * gì — khác `setCameraSignal`, nơi thiếu `sync` là tính năng không lên.
 */
fun ClusterNavBridge.cameraPosLeft(): String = Prefs.cameraPos(app, left = true)
fun ClusterNavBridge.cameraPosRight(): String = Prefs.cameraPos(app, left = false)
fun ClusterNavBridge.setCameraPos(left: Boolean, v: String) = Prefs.setCameraPos(app, left, v)
fun ClusterNavBridge.cameraCamLeft(): Int = Prefs.cameraCamId(app, left = true, 0)
fun ClusterNavBridge.cameraCamRight(): Int = Prefs.cameraCamId(app, left = false, 1)
fun ClusterNavBridge.setCameraCamLeft(v: Int) = Prefs.setCameraCamId(app, left = true, v)
fun ClusterNavBridge.setCameraCamRight(v: Int) = Prefs.setCameraCamId(app, left = false, v)

/** AUTOMATION #2 — sổ luật dẫn-đường-theo-lịch, đã giải mã (rỗng = chưa có luật nào). */
fun ClusterNavBridge.navRules(): List<ScheduledNavRule> =
    NavAutomationBook.decode(Prefs.navAutomationRules(app))

/**
 * Ghi **cả sổ** luật đã chốt, rồi dọn dấu đã-dẫn mồ côi + đồng bộ động cơ.
 *
 * Một cổng nhận cả danh sách (không phải cặp `onAdd`/`onDelete`) vì phép thêm/sửa/xoá là hàm **thuần** ở `:core`
 * ([NavAutomationBook.upsert]/[NavAutomationBook.remove]) — cùng khuôn `onSavedPlaces`. Hai đường ghi cho cùng
 * một bảng là chỗ để hai đường lệch nhau (bài học `unitPrefs` ×4 bản).
 *
 * `pruneFired` chạy **sau** lượt ghi: `NavAutomationBook.newId` cấp lại `id` đã rảnh, nên một dấu đã-dẫn mồ côi
 * (`r1=<hôm nay>` sau khi xoá `r1`) sẽ đóng dấu cho **luật mới vừa tạo** ⇒ luật ấy không chạy hôm nay, im lặng.
 */
fun ClusterNavBridge.setNavRules(rules: List<ScheduledNavRule>) {
    Prefs.setNavAutomationRules(app, NavAutomationBook.encode(rules))
    ScheduledNavApplier.pruneFired(app)
    AutomationService.sync(app)
}

/**
 * Bật/tắt NHANH một lịch (owner 2026-09-24) — user active/inactive tuỳ trường hợp mà không phải mở hộp Sửa.
 * Đi qua [NavAutomationBook.setEnabled] (thuần) rồi [setNavRules] (cổng ghi CHUNG — không mở đường ghi thứ hai).
 */
fun ClusterNavBridge.setNavRuleEnabled(id: String, enabled: Boolean) =
    setNavRules(NavAutomationBook.setEnabled(navRules(), id, enabled))
