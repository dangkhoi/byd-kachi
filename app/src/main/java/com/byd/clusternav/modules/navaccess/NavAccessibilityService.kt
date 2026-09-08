package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.navigation.ScreenTextItem
import com.byd.clusternav.navigation.NavScreenReading
import com.byd.clusternav.navigation.NavScreenScan
import com.byd.clusternav.navigation.NavApps
import com.byd.clusternav.navigation.TurnDistanceInterpolator
import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.byd.clusternav.Prefs
import com.byd.clusternav.modules.voicekey.AssistantLauncher
import com.byd.clusternav.modules.voicekey.VoiceKeyLearnBus
import com.byd.clusternav.voicekey.VoiceKeyAction
import com.byd.clusternav.voicekey.VoiceKeyConfig
import com.byd.clusternav.voicekey.VoiceKeyMatcher

/**
 * BOOSTER TẦNG 1 (chỉ Google Maps) + NÚT VẬT LÝ → TRỢ LÝ.
 *
 * ── ĐỌC DẪN ĐƯỜNG VIETMAP/WAZE ĐÃ GỠ (2026-08-28) ────────────────────────────────────────────────────
 * Toàn bộ đường ĐỌC dẫn đường của VietMap/Waze qua a11y (duyệt cửa sổ mọi-display, dò view-id kiểu OpenBYD,
 * parse content-desc Flutter, đo bbox mũi tên/camera cho screen-capture) đã bị GỠ theo quyết định owner
 * (chậm/lag/thiếu data). File này chỉ còn hai việc, tất cả device-agnostic:
 *   1. **onKeyEvent** — nút vật lý → trợ lý giọng nói (key event KHÔNG bị `packageNames` lọc).
 *   2. **Booster cự-ly Google Maps** — đọc UI GMaps ĐANG HIỆN để lấy cự ly tới rẽ CHÍNH XÁC, TƯƠI hơn noti,
 *      rồi TINH CHỈNH interpolator (`TurnDistanceInterpolator.refine`) + nuôi `NavAccessibilitySource`
 *      (`ClusterBroadcaster.freshScreenRead` đọc lại). GMaps KHÔNG có view-id sạch → dò theo MẪU CHỮ (cự ly
 *      m/km) + TOẠ ĐỘ (thẻ rẽ ở NỬA TRÊN màn). Chỉ là booster: KHÔNG tự khởi tạo nav (refine bỏ qua khi chưa
 *      có anchor noti). KHÔNG root, chỉ xin quyền hỗ trợ.
 *
 * VietMap speed badge đi qua widget (gói `vietmapwidget`, AppWidgetHost — KHÔNG qua a11y), không đụng ở đây.
 *
 * KEEP/KILL: xoá module = xoá modules/navaccess/ + dòng Registry + <service> trong Manifest + res/xml/nav_accessibility_config.xml.
 */
class NavAccessibilityService : AccessibilityService() {

    private var lastProcessed = 0L
    /** Chỉ GMaps: nhánh quét cự-ly-trên-màn (ground truth). `NavApps.ALL` cũng chỉ còn GMAPS từ 2026-08-28. */
    private val maps = NavApps.GMAPS

    // T3: nút vật lý → trợ lý giọng nói. Matcher thuần ở :core; service chỉ map KeyEvent + phóng intent.
    private val voiceKeyMatcher = VoiceKeyMatcher()

    override fun onServiceConnected() {
        NavAccessibilitySource.connected = true
        voiceKeyMatcher.reset()
        Log.i(TAG, "accessibility booster connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        NavAccessibilitySource.connected = false
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    /**
     * T3 — nút vật lý → trợ lý giọng nói. Chỉ chạy khi service được cấp quyền hỗ trợ + config
     * `canRequestFilterKeyEvents` + flag `flagRequestFilterKeyEvents` (xem nav_accessibility_config.xml).
     *
     * KHÔNG thay chức năng gốc: chỉ trả true (nuốt phím) cho mã phím CÓ TRONG danh sách gán của người dùng
     * — quyết định ở [VoiceKeyMatcher] (:core). Phím khác → super (pass-through).
     * "Học phím": nếu bật, ghi lại keycode nút vừa bấm (trên DOWN) rồi tự tắt cờ.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event ?: return super.onKeyEvent(event)
        val app = applicationContext

        // CHẨN ĐOÁN Bug 1 (owner 2026-09-01): log MỌI phím tới đây (chỉ DOWN, thưa). onKeyEvent được gọi ⟺ service
        // ĐANG bound + có cờ filter key. Dùng để chốt trên xe: (a) nút 305 (xoay màn) có TỚI accessibility không —
        // nếu bấm 305 mà KHÔNG có dòng này ⇒ hệ thống nuốt trước, KHÔNG map được (khác 328 mic tới được); (b) sau lái
        // xe bấm nút mà KHÔNG có dòng nào ⇒ service mất bound (rebind chưa phục hồi). Xem logcat tag "NavAccess".
        if (event.action == KeyEvent.ACTION_DOWN) {
            Log.i(TAG, "onKeyEvent DOWN keycode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
        }

        if (Prefs.voiceKeyLearn(app)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                Prefs.setVoiceKeyLearn(app, false)
                Log.i(TAG, "learned voice keycode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
                VoiceKeyLearnBus.publish(event.keyCode)   // Activity (đang mở màn) hiện dialog đặt tên
            }
            return true   // nuốt trong lúc học để không kích hoạt gì khác
        }

        if (!Prefs.voiceKeyEnabled(app)) return super.onKeyEvent(event)

        // F3 (owner 2026-08-24): tra DANH SÁCH gán, không so với một mã nữa. Danh sách rỗng ⇒ mọi phím
        // pass-through (matcher trả IGNORE) ⇒ không nuốt nhầm phím nào của xe.
        val cfg = VoiceKeyConfig(enabled = true, bindings = Prefs.voiceKeyBindings(app))
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> VoiceKeyAction.DOWN
            KeyEvent.ACTION_UP -> VoiceKeyAction.UP
            else -> VoiceKeyAction.OTHER
        }
        val decision = voiceKeyMatcher.onKey(cfg, action, event.keyCode, event.downTime)
        // Đích lấy TỪ quyết định (bất biến: fire ⟺ targetSpec != null) — KHÔNG tra lại prefs, tra hai lần
        // có thể ra hai kết quả nếu owner vừa sửa danh sách giữa DOWN và lúc phóng intent.
        val spec = decision.targetSpec
        if (decision.fire && spec != null) {
            Log.i(TAG, "voice-key fire → target=$spec key=${event.keyCode}")
            runCatching { AssistantLauncher.launch(app, spec) }
                .onFailure { Log.e(TAG, "assistant launch failed", it) }
        }
        return if (decision.consume) true else super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        // GMaps-only booster: chỉ đọc cự-ly ground-truth của Google Maps. VietMap/Waze KHÔNG còn đọc qua a11y.
        if (pkg !in maps) return
        if (!Prefs.enabled(applicationContext) || !Prefs.accBooster(applicationContext)) return
        val now = SystemClock.elapsedRealtime()
        NavAccessibilitySource.lastEventAt = now
        if (now - lastProcessed < THROTTLE_MS) return         // GMaps bắn event dày -> tiết lưu 200ms
        lastProcessed = now

        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        runCatching { scan(root, now) }.onFailure { Log.e(TAG, "scan failed", it) }
        runCatching { root.recycle() }
    }

    /**
     * Gom mọi node có text + toạ độ rồi giao phần QUYẾT ĐỊNH cho [NavScreenScan] trong `:core`.
     *
     * Trước 2026-07-27 heuristic chia dải trên/đáy, chọn token cự ly và chọn tên đường nằm ngay tại đây,
     * nên đúng đoạn quyết định con số tài xế thấy trên cụm lại không có bài kiểm nào. Ở đây giờ chỉ còn
     * việc đi cây `AccessibilityNodeInfo` và ghi kết quả — hai thứ thật sự cần Android.
     */
    private fun scan(root: AccessibilityNodeInfo, now: Long) {
        val items = ArrayList<Triple<String, Int, Int>>(64)
        val screen = Rect(); root.getBoundsInScreen(screen)
        collect(root, items, 0)
        if (items.isEmpty()) return

        val reading = NavScreenScan.scan(
            items.map { ScreenTextItem(it.first, it.second, it.third) },
            screen.height(),
        )

        if (reading.road.isNotEmpty()) NavAccessibilitySource.road = reading.road
        if (reading.bottomInfo.isNotEmpty()) NavAccessibilitySource.bottomInfo = reading.bottomInfo

        if (reading.turnMeters != NavScreenReading.UNKNOWN_METERS) {
            NavAccessibilitySource.turnMeters = reading.turnMeters
            NavAccessibilitySource.lastReadAt = now
            // Ghi đè anchor bằng cự ly đọc trên màn; refine tự bỏ qua nếu noti chưa mở nav.
            TurnDistanceInterpolator.refine(reading.turnMeters, now)
            NavAccessibilitySource.refines++
        }
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        out: ArrayList<Triple<String, Int, Int>>,
        depth: Int,
    ) {
        node ?: return
        if (out.size >= MAX_NODES || depth > MAX_DEPTH) return
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrEmpty() && t.length <= 80) {
            val r = Rect(); node.getBoundsInScreen(r)
            out.add(Triple(t, r.top, r.left))
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            collect(c, out, depth + 1)
            runCatching { c.recycle() }
        }
    }

    companion object {
        private const val TAG = "NavAccess"
        private const val THROTTLE_MS = 200L
        private const val MAX_NODES = 250
        private const val MAX_DEPTH = 40
    }
}
