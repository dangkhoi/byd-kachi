package com.byd.clusternav.launcher.voice

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiSpace as Sp
import com.byd.clusternav.launcher.KachiTheme
import com.byd.clusternav.launcher.card
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiType

/**
 * ═══ V1 pha NGHE · TẤM NHỎ Ở GÓC MÀN — *"máy đang nghe · nghe được gì · trả lời gì"* ═════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R11**. Thuần VIEW: không biết micro, không biết Vosk, không biết
 * ý định — nó nhận chữ và vẽ. [VoiceSession] là thứ duy nhất gọi nó.
 *
 * ## Vì sao là CỬA SỔ của hệ thống, không phải một view nhét vào màn chính
 * Phiên nghe mở được từ ba lối (ô *Nói với xe* trên thanh nút · pill mic trên thanh trên · phím vô-lăng), và ở
 * lối thứ ba màn chính có thể đang bị một app chiếm chỗ trong ô chiếu. Một view con của màn chính sẽ nằm **dưới**
 * app đó — tức người lái nói mà không thấy gì. Quyền `SYSTEM_ALERT_WINDOW` thì launcher đã tự cấp từ vòng kiểm
 * (`LauncherRequirements.OVERLAY`), cùng đường mà nút nổi chiếu cụm đang dùng.
 *
 * ## Vì sao PHỦ TOÀN MÀN dù tấm chữ chỉ nằm một góc
 * Cử chỉ huỷ phải chạy được: **chạm ra ngoài**. Cửa sổ `WRAP_CONTENT` không nhận được cú chạm bên ngoài nó. Nền
 * trong suốt nên nhìn vẫn là "một tấm nhỏ ở góc"; và nó chỉ sống tối đa 8 giây (trần cứng ở [VoiceSession]) nên
 * việc nó chặn chạm trong khoảng ấy là **có chủ ý**: đang nghe thì cú chạm tiếp theo nên là *"thôi, không nói
 * nữa"*. Phủ toàn màn cũng là lý do KHÔNG cần `FLAG_WATCH_OUTSIDE_TOUCH`: không có "ngoài" để mà canh.
 *
 * ## Vì sao KHÔNG LẤY TIÊU ĐIỂM (`FLAG_NOT_FOCUSABLE`) — SYS-TASKBAR-VOICE-FOCUS
 * Xem KDoc [show]. Tóm: cửa sổ **có** tiêu điểm là cửa sổ điều khiển thanh hệ thống, nên một overlay focusable
 * kéo taskbar của xe lên mỗi lượt nói. Không lấy tiêu điểm ⇒ app đang chạy giữ nguyên trạng thái thanh hệ thống
 * của nó, overlay không còn là một biến trong bài toán ấy. Giá phải trả: **mất đường thoát bằng Back** (phím Back
 * chỉ tới cửa sổ có tiêu điểm) ⇒ đường thoát là **chạm ra ngoài tấm chữ** (đã có sẵn ở [build]) + trần 8 giây.
 */
class VoiceOverlay(
    private val ctx: Context,
    /** Người dùng muốn thoát — **chạm ra ngoài tấm chữ** (Back không còn tới cửa sổ này, xem KDoc [show]). */
    private val onCancel: () -> Unit,
) {

    private val wm = ctx.getSystemService(WindowManager::class.java)

    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var action: TextView
    private lateinit var wave: VoiceWaveView
    private var root: View? = null

    /** R2/R3 voice-ux — cấp mức âm cho waveform (gọi từ luồng nghe). No-op nếu chưa dựng. */
    fun level(rms: Int) { if (::wave.isInitialized) wave.setLevel(rms) }

    /**
     * R3 — đổi hiển thị theo pha hội thoại (bám `VoiceTurnPhase` ở `VoiceSession`). Overlay SỐNG suốt phiên,
     * chỉ [dismiss] ở CLOSING. LISTENING/CLARIFYING = waveform phập phồng; DECODING/EXECUTING = đứng yên mờ.
     */
    fun setPhase(listening: Boolean) { if (::wave.isInitialized) wave.setListening(listening) }

    /** Đang hiện hay không — [VoiceSession] hỏi để khỏi gỡ hai lần. */
    val showing: Boolean get() = root != null

    /**
     * ═══ SYS-TASKBAR-VOICE-FOCUS — vì sao cửa sổ này KHÔNG được lấy tiêu điểm ════════════════════════════════
     *
     * Triệu chứng owner (buổi xe 2026-09-26): tấm chữ hiện lên thì **không** thấy taskbar, nhưng đúng lúc Kachi
     * **đọc phản hồi** thì taskbar/thanh điều hướng của xe trồi lên và **ở lại** tới khi tấm chữ tắt.
     *
     * ## Chuỗi nhân quả [ĐO buổi xe 26/09 · `docs/diagnostics/perf-oncar-2026-09-26/logcat-stream-2.70.txt`]
     * ```
     * 18:29:15.709 debug_focus Changing focus from KachiHomeActivity to Window{3e5380 u0 com.byd.launcher} display 0
     * 18:29:15.711 StatusBar setSystemUiVisibility display 0  oldVal=970e newVal=8008  diff=1706   ← mất cả bộ cờ
     * 18:29:15.717 BarController.NavigationBar setBarShowingLw show=true                            ← thanh LÊN
     * 18:29:15.746 StatusBar setSystemUiVisibility display 0  oldVal=8008 newVal=970e  diff=1706   ← ta áp lại
     * …
     * 18:29:20.755 debug_focus Changing focus from null to vietmap/MainActivity  displayId=7        ← lệnh "mở vietmap"
     * 18:29:20.756 debug_focus Changing focus from Window{3e5380 u0 com.byd.launcher} to null  displayId=0
     * 18:29:20.758 StatusBar setSystemUiVisibility display 0  oldVal=970e newVal=9708  diff=6      ← rụng 0x2|0x4
     * 18:29:20.760 BarController.NavigationBar setBarShowingLw show=true                            ← thanh LÊN, Ở LẠI
     * 18:30:00.381 …setBarShowingLw show=false                                                      ← 39,6 s sau
     * ```
     * `Window{3e5380 u0 com.byd.launcher}` (tên gói, KHÔNG có tên Activity) = chính cửa sổ này, ở tiến trình
     * `:wake`. Yêu cầu tiêu điểm TTS/âm thanh **không** phải nguyên nhân: lượt xin audio-focus của giọng đọc tới
     * lúc `18:29:20.771`, tức **15 ms SAU KHI** thanh đã lên.
     *
     * ## Cơ chế trong nguồn AOSP `android-10.0.0_r47`
     *  1. `DisplayPolicy.focusChangedLw` (`services/core/java/com/android/server/wm/DisplayPolicy.java:3028-3040`)
     *     đặt `mFocusedWindow = newFocus` rồi gọi NGAY `updateSystemUiVisibilityLw()` — đúng khoảng 2 ms trong log.
     *  2. `updateSystemUiVisibilityLw` (`DisplayPolicy.java:3112-3119`, `:3151-3153`) lấy cờ ẩn thanh hệ thống từ
     *     `winCandidate = mFocusedWindow != null ? mFocusedWindow : mTopFullscreenOpaqueWindowState` — tức **chỉ**
     *     từ cửa sổ đang có tiêu điểm. Lượt cửa sổ này *nhận* tiêu điểm xảy ra TRƯỚC khi giá trị
     *     `systemUiVisibility` của View kịp về tới WM ⇒ recompute với 0 cờ ⇒ `diff=1706`, thanh lên; 37 ms sau
     *     `goImmersive` áp lại ⇒ thanh xuống. Đó là cái **nháy** mà owner không kịp thấy.
     *  3. Lượt *mất* tiêu điểm là cái ở lại: display 0 không còn cửa sổ nào có tiêu điểm
     *     (`taskbar-window-dump.txt`: `mCurrentFocus=null`), vì
     *     `DisplayContent.findFocusedWindowIfNeeded` (`DisplayContent.java:3016-3024`) trả **null** cho mọi màn
     *     không phải màn top-focused khi `WindowManagerService.mPerDisplayFocusEnabled == false`
     *     (`WindowManagerService.java:648-649`, `:1023-1024`) — và pha phản hồi của lệnh *"mở vietmap"* vừa đưa
     *     màn slot (display 7) lên top-focused. Recompute lúc ấy rụng đúng `SYSTEM_UI_CLEARABLE_FLAGS`
     *     (`View.java:3842` = `LOW_PROFILE|HIDE_NAVIGATION|FULLSCREEN` = 0x7; log `diff=6`) qua nhánh
     *     `clearClearableFlagsLw()` (`DisplayPolicy.java:3374-3382`, `:3494-3498`) mà `mForceShowSystemBars`
     *     (`:3280-3290`, cụm docked/freeform đang hiện) bật lên. Không ai áp lại được nữa: cửa sổ duy nhất còn
     *     muốn ẩn thanh là cửa sổ này, mà `onWindowFocusChanged(true)` thì không bao giờ tới lần nữa.
     *
     * ## Vì sao `FLAG_NOT_FOCUSABLE` chữa được, và chữa GENERIC
     * `WindowState.canReceiveKeys` (`WindowState.java:2559-2565`) loại thẳng cửa sổ có `FLAG_NOT_FOCUSABLE` khỏi
     * `findFocusedWindow` ⇒ cửa sổ này **không bao giờ** là `mFocusedWindow`, nên không sinh một lượt
     * `focusChangedLw` nào trên display 0: app/màn chính đang giữ tiêu điểm giữ luôn quyền định trạng thái thanh
     * hệ thống, y như lúc chưa có tấm chữ. Đường thứ hai cũng đóng: `mTopFullscreenOpaqueWindowState` chỉ nhận
     * **cửa sổ app** (`DisplayPolicy.java:2413` `appWindow = attrs.type >= FIRST_APPLICATION_WINDOW`), mà đây là
     * `TYPE_APPLICATION_OVERLAY`. Không hardcode tên gói, không đo app nào cả — chỉ là thôi tham gia.
     *
     * ⚠ ROM này **đúng hình AOSP-10 ở chỗ đó**: dòng log của chính ROM
     * `DPfinishLw attrs.isFullscreen()=… inFullScreenOrSplitScreenSecondaryWindowingMode=…` là BYD chèn thêm vào
     * đúng khối `DisplayPolicy.java:2416-2418` + `:2437-2439`.
     *
     * Giá phải trả + bù: `dispatchKeyEvent` chỉ tới cửa sổ có tiêu điểm ⇒ **mất Back**. Đường thoát còn lại:
     * chạm ra ngoài tấm chữ (cửa sổ phủ toàn màn nên vùng chạm là gần cả màn hình, xem [build]) + trần 8 giây ở
     * [VoiceSession]. Chạm vẫn tới ta bình thường: `FLAG_NOT_FOCUSABLE` chỉ chặn **phím**, và tuy nó bật kèm
     * `FLAG_NOT_TOUCH_MODAL` (`WindowManager.java:1164-1177`) thì "ngoài cửa sổ" ở đây là tập rỗng.
     */
    fun show() {
        if (root != null) return
        val view = build()
        val type =
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // `FLAG_NOT_FOCUSABLE`: xem khối ═══ ở KDoc trên — không lấy tiêu điểm là cách DUY NHẤT để không
            // chạm vào trạng thái thanh hệ thống của app đang chạy. `FLAG_WATCH_OUTSIDE_TOUCH` không cần vì cửa
            // sổ này đã phủ toàn màn (không có "ngoài").
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        )
        // Không làm tối màn phía dưới: người lái vẫn phải thấy đường và thấy app đang chạy.
        lp.dimAmount = 0f
        if (runCatching { wm.addView(view, lp) }.isFailure) return
        root = view
    }

    fun dismiss() {
        val v = root ?: return
        root = null
        runCatching { wm.removeView(v) }
    }

    /**
     * Vẽ một trạng thái.
     *
     * @param titleRes dòng đầu (*"Đang nghe…"* / *"Đã hiểu"* / *"Không nghe rõ"*).
     * @param text dòng thân — chữ partial đang nghe, hoặc câu trả lời. Rỗng ⇒ ẩn hẳn dòng (không để một dòng
     *   trống co giãn làm tấm chữ nhảy cỡ mỗi lần partial đổi).
     * @param actionText nhãn nút gợi ý (vd *"Mở Cài đặt"*), `null` ⇒ không có nút.
     */
    fun render(titleRes: Int, text: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
        if (root == null) return
        // #2 (owner 2026-09-24 "to vật, nhiều chữ lộn xộn"): gọn như Mimi — chỉ MỘT dòng. Có chữ nghe/đáp (`text`)
        // thì hiện chữ đó; chưa có thì hiện gợi ý ngắn của trạng thái (`titleRes`, vd "Thử nói…"/"Đang nghe…").
        val line = if (text.isNotBlank()) text else ctx.getString(titleRes)
        body.text = line
        action.visibility = if (actionText == null) View.GONE else View.VISIBLE
        action.text = actionText.orEmpty()
        action.setOnClickListener { onAction?.invoke() }
    }

    // ── dựng view ────────────────────────────────────────────────────────────────────────────────

    private fun build(): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = KachiTheme.card(ctx, Sp.RADIUS_XL, KachiTheme.BAR_TOP)
            val p = dpi(ctx, Sp.XL)
            setPadding(p, dpi(ctx, Sp.L), p, dpi(ctx, Sp.L))
        }
        // Waveform vòng tròn (kiểu Mimi/Siri) — chỉ báo "đang nghe" DUY NHẤT (không mic-icon, không title lớn).
        wave = VoiceWaveView(ctx).apply { setListening(true) }
        card.addView(wave, LinearLayout.LayoutParams(dpi(ctx, WAVE_DP), dpi(ctx, WAVE_DP)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        // MỘT dòng chữ: gợi ý / partial đang nghe / câu trả lời — gọn (BODY, không SECTION to). Luôn hiện (không
        // ẩn để card khỏi nhảy cỡ); giữ 1 chỗ trống bằng gợi ý mặc định.
        body = TextView(ctx).apply {
            setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
            gravity = Gravity.CENTER; setPadding(0, dpi(ctx, Sp.S), 0, 0)
            maxLines = MAX_BODY_LINES
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        // Nút gợi ý (Mở Cài đặt…) — ẩn khi không có. title/hint CŨ đã bỏ (owner: lộn xộn).
        title = body   // giữ tham chiếu `title` cho code cũ (render dùng body làm dòng chính) — không dựng view riêng.
        action = TextView(ctx).apply {
            setTextColor(c(KachiTheme.ACCENT)); KachiType.apply(this, KachiType.BODY, bold = true)
            minHeight = dpi(ctx, Sp.TOUCH); gravity = Gravity.CENTER
            visibility = View.GONE
        }
        card.addView(action, LinearLayout.LayoutParams(MATCH, WRAP))

        // Lớp phủ trong suốt bắt cú chạm ra ngoài. Xem KDoc lớp về vì sao nó phủ toàn màn, và KDoc [show] về vì
        // sao nó KHÔNG lấy tiêu điểm (nên cũng không có `dispatchKeyEvent`/Back — cố ý, không phải bỏ sót).
        return object : FrameLayout(ctx) {
            /**
             * Cờ bố cục áp một lần lúc cửa sổ được thêm.
             *
             * ⚠ Từ SYS-TASKBAR-VOICE-FOCUS (26/09) bộ cờ này **không còn là đường điều khiển thanh hệ thống** —
             * `DisplayPolicy` chỉ đọc cờ từ cửa sổ CÓ tiêu điểm (`DisplayPolicy.java:3112-3119`), mà cửa sổ này
             * cố ý `FLAG_NOT_FOCUSABLE`. Giữ lại vì nó vẫn còn MỘT việc thật: ba cờ `LAYOUT_*` giữ khung của
             * chính ta bằng cả màn, nên lúc app đang chạy cho thanh hệ thống hiện thì tấm chữ **không** bị đẩy
             * lên 90 px (nhảy chỗ giữa lúc đang nói). Giữ đúng bộ của màn chính (`goImmersiveWindow`) để hai bề
             * mặt không lệch nhau — xem [goImmersive]. Không có `FLAG_DIM_BEHIND`, `dimAmount = 0`: người lái
             * vẫn phải thấy đường.
             *
             * KHÔNG có `onWindowFocusChanged`: cửa sổ không lấy tiêu điểm thì nhánh ấy không bao giờ chạy
             * (CLAUDE.md §8 — hàm không có đường gọi thật thì không được ở lại).
             */
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                goImmersive(this)
            }
        }.apply {
            setBackgroundColor(Color.TRANSPARENT)
            goImmersive(this)
            // Hệ thống đặt lại cờ của View (quệt cạnh, một app khác xin) ⇒ áp lại để khung của ta không co lại.
            // Điều kiện `FULLSCREEN` chưa bật là thứ chặn vòng lặp: lượt áp lại của chính ta bắn listener lần nữa
            // với cờ ĐÃ bật ⇒ nhánh này không chạy tiếp.
            @Suppress("DEPRECATION")
            setOnSystemUiVisibilityChangeListener { vis ->
                if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) goImmersive(this)
            }
            addView(
                card,
                FrameLayout.LayoutParams(dpi(ctx, CARD_MAX_W), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL   // R3 voice-ux: GIỮA, dưới; rộng CỐ ĐỊNH (không nhảy)
                    bottomMargin = dpi(ctx, Sp.XXL)
                },
            )
            setOnTouchListener { _, ev ->
                // Chỉ huỷ khi cú chạm nằm NGOÀI tấm chữ: chạm vào nút "Mở Cài đặt" bên trong phải là bấm nút,
                // không phải huỷ. `ACTION_DOWN` (không phải UP) vì người lái quệt tay là đủ ý "thôi".
                // ⚠ Từ 26/09 đây là đường thoát DUY NHẤT của người dùng (Back đi cùng tiêu điểm — KDoc [show]),
                // nên nó phải ở lại: cửa sổ phủ toàn màn ⇒ vùng chạm huỷ = cả màn trừ tấm chữ.
                if (ev.action == MotionEvent.ACTION_DOWN && !inside(card, ev)) { onCancel(); true } else false
            }
        }
    }

    private fun inside(v: View, ev: MotionEvent): Boolean {
        val x = ev.x.toInt(); val y = ev.y.toInt()
        return x >= v.left && x <= v.right && y >= v.top && y <= v.bottom
    }

    /**
     * Cùng **đúng** bộ cờ mà màn chính dùng (`KachiHomeWiring.goImmersiveWindow`).
     *
     * Từ SYS-TASKBAR-VOICE-FOCUS (26/09) bộ cờ này chỉ còn giữ **khung của chính tấm chữ** bằng cả màn (ba cờ
     * `LAYOUT_*`); phần ẩn thanh hệ thống là việc của cửa sổ CÓ tiêu điểm, mà cửa sổ này cố ý không lấy tiêu điểm
     * (KDoc [show]). Vẫn giữ **nguyên một bộ** với màn chính thay vì rút gọn: hai bề mặt cùng một bộ cờ thì không
     * có ca nào bề mặt này xin một trạng thái khác bề mặt kia nếu ROM một ngày đọc cả cửa sổ không tiêu điểm.
     *
     * Xe chạy Android 10 (API 29) ⇒ `systemUiVisibility`; `WindowInsetsController` là API 30+. Deprecated trên
     * SDK biên dịch nhưng nó là API **duy nhất** có tác dụng trên nền tảng đích — cùng lý do đã ghi ở
     * `goImmersiveWindow` và `ClusterNavActivity`.
     */
    @Suppress("DEPRECATION")
    private fun goImmersive(v: View) {
        v.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private companion object {
        /** Bề rộng tối đa của tấm chữ (dp) — đủ cho một câu trả lời, không lấn nửa màn. */
        const val CARD_MAX_W = 420

        /** Câu trả lời dài (vd gói lệnh báo từng bước) vẫn phải đọc được mà không đẩy tấm chữ cao lên mãi. */
        const val MAX_BODY_LINES = 4

        /** Cỡ vòng tròn waveform (dp) — vừa phải, không lấn chữ. */
        const val WAVE_DP = 72
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
