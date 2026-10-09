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
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Prefs
import com.byd.clusternav.modules.voicekey.AssistantLauncher
import com.byd.clusternav.modules.voicekey.KeySourceRecorder
import com.byd.clusternav.modules.voicekey.VoiceKeyLearnBus
import com.byd.clusternav.voicekey.KeySample
import com.byd.clusternav.voicekey.KeySourceLookup
import com.byd.clusternav.voicekey.KeySourceReading
import com.byd.clusternav.voicekey.VoiceKeyAction
import com.byd.clusternav.voicekey.VoiceKeyBindings
import com.byd.clusternav.voicekey.VoiceKeyConfig
import com.byd.clusternav.voicekey.KeyLearnTail
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

    // QA 2.87 [P3] — phần còn lại (DOWN lặp + UP) của lần nhấn vừa HỌC: nuốt nốt, không để UP mồ côi tới app media.
    private val learnTail = KeyLearnTail()

    // L7 · KEY-SOURCE-SPLIT tầng 1 — CHỈ ĐO nguồn phím (chữ ký + feature HAL đánh dấu nguồn), không đổi khớp/gán.
    // Sống theo một lần bind: dựng ở onServiceConnected, dừng ở onUnbind/onDestroy.
    private var keySource: KeySourceRecorder? = null

    override fun onServiceConnected() {
        NavAccessibilitySource.connected = true
        A11yOverlayPort.attach(this)   // 2.98 · R7 — lớp che thanh hệ thống/thanh tiêu đề freeform (SlotEscape)
        voiceKeyMatcher.reset()
        learnTail.reset()
        keySource?.stop()
        val app = applicationContext
        keySource = KeySourceRecorder(app) { AppContainer.get(app).halGateway }.also { it.start() }
        // 2.88 · KEY-SOURCE-SPLIT tầng 2 — có dòng gán theo nguồn ⇒ đọc mồi MỘT lượt trên luồng `kachi-keysrc-sync` (chỉ
        // gửi việc, main không chờ) để lần nhấn đầu không trả giá nạp bảng feature-id + getInstance.
        if (VoiceKeyBindings.anySource(Prefs.voiceKeyBindings(app))) keySource?.primeSource()
        Log.i(TAG, "accessibility booster connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        NavAccessibilitySource.connected = false
        A11yOverlayPort.detach(this)
        keySource?.stop()
        keySource = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        A11yOverlayPort.detach(this)
        keySource?.stop()
        keySource = null
        super.onDestroy()
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
        // L7 tầng 1: chép chữ ký NGAY (field nguyên thuỷ — event bị recycle sau khi hàm này trả về); ghi nhật ký ở dưới.
        val sample = if (event.action == KeyEvent.ACTION_DOWN) {
            Log.i(TAG, "onKeyEvent DOWN keycode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
            KeySourceRecorder.sampleOf(event)
        } else null
        val learning = Prefs.voiceKeyLearn(app)

        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> VoiceKeyAction.DOWN
            KeyEvent.ACTION_UP -> VoiceKeyAction.UP
            else -> VoiceKeyAction.OTHER
        }
        // QA 2.87 [P3] — DOWN lặp / UP của CHÍNH lần nhấn vừa học (cờ học đã tắt trên DOWN) ⇒ nuốt nốt. Trước: UP đi đường
        // thường, phím chưa gán ⇒ tới hệ thống ⇒ [ĐO máy ảo `learn88-orphan-up.log` (bằng chứng phiên, ngoài repo)] UP mồ côi bật YT Music.
        val tail = learnTail.swallow(action, event.keyCode, event.downTime)
        // 2.88 · KEY-SOURCE-SPLIT tầng 2 — chỉ đường KHỚP (không đuôi học, không đang học, công tắc bật) mới có thể đọc
        // nguồn đồng bộ. Mọi đường khác ghi nhật ký tầng 1 NGAY, y như 2.87 — TRƯỚC khi bus học phím báo mã (hộp đặt tên
        // phải thấy dòng của lần học). KHÔNG I/O, KHÔNG HAL ở đây: onKeyEvent chạy trên main, framework chỉ chờ 500 ms.
        val matching = !tail && !learning && Prefs.voiceKeyEnabled(app)
        if (sample != null && !matching) keySource?.onDown(sample, learned = learning)
        if (tail) return true

        if (learning) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                Prefs.setVoiceKeyLearn(app, false)
                learnTail.learned(event.keyCode, event.downTime)
                Log.i(TAG, "learned voice keycode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
                VoiceKeyLearnBus.publish(event.keyCode)   // Activity (đang mở màn) hiện dialog đặt tên
            }
            return true   // nuốt trong lúc học để không kích hoạt gì khác
        }

        if (!matching) return super.onKeyEvent(event)

        // F3 (owner 2026-08-24): tra DANH SÁCH gán, không so với một mã nữa. Danh sách rỗng ⇒ mọi phím
        // pass-through (matcher trả IGNORE) ⇒ không nuốt nhầm phím nào của xe.
        val cfg = VoiceKeyConfig(enabled = true, bindings = Prefs.voiceKeyBindings(app))
        // 2.88 — matcher gọi hàm tra nguồn CHỈ trên DOWN đầu của một mã có dòng gán theo nguồn (R3); mọi mã khác đi đường
        // 2.87 y nguyên, không HAL. Số đọc (nếu có) chuyển cho nhật ký tầng 1 để không đọc HAL lần hai (R-nf2).
        var preRead: KeySourceReading? = null
        val decision = voiceKeyMatcher.onKey(cfg, action, event.keyCode, event.downTime) {
            lookupSource(sample).also { preRead = it.reading }
        }
        if (sample != null) keySource?.onDown(sample, learned = false, preRead = preRead)
        // Đích lấy TỪ quyết định (bất biến: fire ⟺ targetSpec != null) — KHÔNG tra lại prefs, tra hai lần
        // có thể ra hai kết quả nếu owner vừa sửa danh sách giữa DOWN và lúc phóng intent.
        val spec = decision.targetSpec
        if (decision.fire && spec != null) {
            Log.i(TAG, "voice-key fire → target=$spec key=${event.keyCode} src=${decision.reason ?: NO_SOURCE_READ}")
            runCatching { AssistantLauncher.launch(app, spec) }
                .onFailure { Log.e(TAG, "assistant launch failed", it) }
        } else if (!decision.consume && decision.reason != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            // R6 — mã có dòng gán theo nguồn mà lần nhấn này KHÔNG khớp dòng nào (vd vô-lăng khi chỉ núm được gán) ⇒ phím đi
            // tiếp; một dòng để anh em gửi log là chốt được ca "núm/vô-lăng làm sai việc".
            Log.i(TAG, "voice-key pass key=${event.keyCode} src=${decision.reason}")
        }
        return if (decision.consume) true else super.onKeyEvent(event)
    }

    /**
     * 2.88 — hàm tra nguồn mà [VoiceKeyMatcher] gọi (chỉ trên DOWN đầu, chỉ khi cần): đọc ĐỒNG BỘ qua bộ ghi, trần 100 ms
     * ([com.byd.clusternav.voicekey.KeySourceResolver]). Bộ ghi chưa chạy / không có chữ ký / lỗi bất ngờ ⇒ KHÔNG BIẾT
     * nguồn (matcher chỉ dùng dòng không nguồn) — tuyệt đối không ném ra khỏi onKeyEvent của dịch vụ giữ phím.
     */
    private fun lookupSource(sample: KeySample?): KeySourceLookup {
        val rec = keySource ?: return KeySourceLookup.NO_LOOKUP
        sample ?: return KeySourceLookup.NO_LOOKUP
        return try {
            rec.lookupSource(sample)
        } catch (e: RuntimeException) {
            Log.w(TAG, "tra nguồn phím hỏng — coi như không biết nguồn", e)
            KeySourceLookup.NO_LOOKUP
        }
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
        /** `src=` của dòng `voice-key fire` khi lần nhấn KHÔNG tra nguồn (mã không có dòng gán theo nguồn). */
        private const val NO_SOURCE_READ = "-"
        private const val THROTTLE_MS = 200L
        private const val MAX_NODES = 250
        private const val MAX_DEPTH = 40
    }
}
