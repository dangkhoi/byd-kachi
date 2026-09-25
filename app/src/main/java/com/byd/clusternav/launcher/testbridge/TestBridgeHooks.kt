package com.byd.clusternav.launcher.testbridge

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.byd.clusternav.launcher.AppOpener
import com.byd.clusternav.launcher.CarControlPort
import com.byd.clusternav.launcher.ClusterNavBridge
import com.byd.clusternav.launcher.DrawerController
import com.byd.clusternav.launcher.actByKind
import com.byd.clusternav.launcher.HomePanels
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.HomeViewModel
import com.byd.clusternav.launcher.KachiHomeSlots
import com.byd.clusternav.launcher.LayoutPreset
import com.byd.clusternav.launcher.SettingsGroup
import com.byd.clusternav.launcher.VoiceDispatcher
import com.byd.clusternav.launcher.clusterNavBridge
import com.byd.clusternav.launcher.voice.VoiceSession
import com.byd.clusternav.launcher.voice.VoiceWiring
import java.lang.ref.WeakReference

/**
 * ═══ T-BRIDGE · MÓC CỦA MÀN CHÍNH — thứ DUY NHẤT nối receiver với launcher đang chạy ══════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` R6.
 *
 * ## Vì sao phải có một lớp móc, thay vì cho receiver tự dựng lấy
 * Một `BroadcastReceiver` không có gì cả: không `HomeViewModel`, không cây view, không `KachiHomeSlots`. Nó dựng
 * lại được `VoiceDispatcher` (chỉ cần `Context`), nhưng **không** dựng lại được ba thứ mà cầu kiểm thử tồn tại
 * để chạm tới: gắn app vào ô (`KachiHomeSlots.assignApp` còn phải ghi sổ vị trí + đặt khung cửa sổ), mở một
 * phiên nghe (`VoiceSession` là **một** phiên cho cả tiến trình), và đọc `HomeUiState` đang hiển thị. Tự dựng
 * bản thứ hai của chúng chính là "đường thứ hai" mà KDoc [VoiceDispatcher] cấm — và ở đây bản thứ hai còn tệ
 * hơn: nó sẽ đổi state mà **màn hình không biết**, tức lượt đo nói một đằng màn hình hiện một nẻo.
 *
 * ⇒ Màn chính **gắn** móc lúc dựng ([Activity.attachTestBridge]); lượt **tháo** tự nối theo vòng đời nền tảng.
 * Màn chưa chạy ⇒ [KachiTestHooks.get] trả `null` ⇒ lệnh trả lỗi `home_not_running` thay vì âm thầm không làm gì.
 *
 * Mọi lambda ở đây trỏ tới **đúng** thứ mà một cú chạm dùng — không có ngoại lệ nào.
 */
internal class TestBridgeHooks(
    /**
     * Ai gắn móc này — để [KachiTestHooks.detach] không cho màn CŨ gỡ móc của màn MỚI (hai màn Kachi chồng nhau).
     * Giữ **yếu**: [KachiTestHooks] là `object` sống hết tiến trình, một tham chiếu mạnh tới Activity ở đây là rò
     * (lint StaticFieldLeak) nếu lượt tháo vì lý do gì không chạy. Chỉ dùng để SO DANH TÍNH (`===`), không gọi.
     */
    owner: Activity,
    /** Activity còn sống, `null` khi đang đóng — chỗ gọi phải hỏi lại mỗi lần, không chụp sẵn. */
    val activity: () -> Activity?,
    val state: () -> HomeUiState,
    /** Dựng cầu giọng nói với `say`/`confirm` của chính lượt đo — CÙNG bộ dây mà ô "Gõ lệnh chữ" dùng. */
    val dispatcher: (
        say: (String) -> Unit,
        confirm: (String, () -> Unit, () -> Unit) -> Unit,
    ) -> VoiceDispatcher,
    /** Gắn app vào ô (0-based) — đường của ngăn kéo, xem KDoc `VoiceDispatcher.assignAppToSlot`. */
    val assignAppToSlot: (Int, String) -> Boolean,
    /** Xoá nội dung một ô (0-based) — đường của ngăn kéo (`KachiHomeSlots.clearSlot`). */
    val clearSlot: (Int) -> Unit,
    /** Mở app TOÀN MÀN (`KachiHomeSlots.openAppFullscreen`) — không ghi vào ô, không đổi bố cục. */
    val openApp: (String) -> Unit,
    /**
     * Bắn MỘT control qua ĐÚNG cổng [CarControlPort] mà một cú chạm ô nút đi (`CarControlAdapter.actByKind`) —
     * KHÔNG dựng adapter thứ hai. Trả `true` nếu port báo nhận (rc hợp lệ, khác sentinel). Câu chữ HAL thật đọc
     * riêng ở [com.byd.clusternav.launcher.HalWriteProbe] (cầu chụp quanh lượt gọi này).
     */
    val control: (id: String, primary: Int) -> Boolean,
    val switchProfile: (String) -> Unit,
    val setPreset: (LayoutPreset) -> Unit,
    /**
     * V3 · R14 — bật/tắt **nhãn chip** thanh trên, qua ĐÚNG lambda mà ô tích trong Cài đặt đi
     * (`HomeViewModel.setTopStrip`). Không ghi thẳng `WorkspacePrefs`: khoá này theo **hồ sơ** và đang nằm trong
     * `HomeUiState` mà màn hình vẽ ⇒ ghi dưới chân màn hình là một phép đo nói một đằng, màn hiện một nẻo.
     */
    val setTopStripLabels: (Boolean) -> Unit,
    /** Mở một phiên nghe thật — CÙNG đường mà nút mic trên thanh trên dùng. */
    val listen: () -> Unit,
    /** Kênh shell (dadb) đã dò được chưa — chỉ ĐỌC, cầu này không tự chạy lệnh shell nào. */
    val shellUsable: () -> Boolean,
    /** Cầu ClusterNav (cho lệnh `reapply`). */
    val bridge: () -> ClusterNavBridge,
    /** Camera theo xi-nhan: ép một nhịp với xi-nhan giả (trái,phải) — verify E2E overlay off-car (HAL null). */
    val cameraTick: (Boolean, Boolean) -> Unit = { _, _ -> },
) {
    private val ownerRef = WeakReference(owner)

    /** Màn đã gắn móc có đúng là [a] không — đường so danh tính DUY NHẤT, không phơi Activity ra ngoài. */
    fun ownedBy(a: Activity): Boolean = ownerRef.get() === a
}

/**
 * Bản móc DUY NHẤT của cả tiến trình.
 *
 * `object` chứ không phải field của màn: receiver được nền tảng dựng **mới tinh** cho mỗi broadcast, nên nó
 * không có cách nào giữ tham chiếu tới màn. Cùng hình dạng `SlotVdOwner` (một chủ sở hữu cho cả tiến trình), và
 * cùng lý do: thứ cần nhìn thấy nhau nằm ở hai vòng đời khác nhau.
 */
internal object KachiTestHooks {

    private const val TAG = "KachiTest"

    @Volatile
    private var current: TestBridgeHooks? = null

    fun get(): TestBridgeHooks? = current

    fun attach(hooks: TestBridgeHooks) {
        current = hooks
        Log.i(TAG, "hooks attached")
    }

    /**
     * Gỡ móc — **chỉ khi** [owner] đúng là chủ đang giữ.
     *
     * ⚠ [ĐO] hình dạng này đã có thật ở `SlotVdOwner`: màn Kachi cũ mang cờ "đang kết thúc" nhưng `onDestroy` của
     * nó chạy **sau** `onCreate` của màn mới. So chủ sở hữu thì lượt huỷ muộn đó không gỡ mất móc vừa gắn — không
     * so thì cầu kiểm thử chết im lặng đúng sau một lần đổi chủ đề/ngôn ngữ (hai ca dựng lại màn).
     */
    fun detach(owner: Activity) {
        if (current?.ownedBy(owner) == true) {
            current = null
            Log.i(TAG, "hooks detached")
        }
    }
}

/**
 * Gắn móc cho màn chính. Gọi ở `onCreate` — và **chỉ** ở đó: đường tháo tự nối bên trong (xem dưới).
 *
 * ## Vì sao KHÔNG có một `detachTestBridge()` để gọi ở `onDestroy`
 * Một cặp gắn/tháo mà hai nửa nằm ở hai chỗ là một nửa sẽ bị quên — dự án đã có đúng ca đó (`CastShell.evictVd`
 * mất call site, CLAUDE.md §8; và `HomePanels.closeAll()` sống nhiều phiên không ai gọi). Ở đây cái bị quên là
 * một móc trỏ vào Activity đã huỷ, tức cầu kiểm thử sẽ *"chạy"* mà đổi state của một màn không còn trên màn
 * hình. Nối lượt tháo vào **vòng đời của nền tảng** thì không có chỗ nào để quên.
 *
 * Nhận **lambda** cho `slots`/`voice`/`shell` chứ không nhận giá trị: cả ba đều là `lazy`/`@Volatile` của màn
 * chính, và chạm vào chúng ngay lúc `onCreate` sẽ **dựng sẵn** một `VoiceSession` (mô hình giọng, micro) cho một
 * chuyến xe có thể không ai nói câu nào.
 */
internal fun Activity.attachTestBridge(
    viewModel: HomeViewModel,
    slots: () -> KachiHomeSlots,
    voice: () -> VoiceSession,
    drawer: () -> DrawerController,
    panels: () -> HomePanels,
    shell: () -> ((String) -> String)?,
    /** Cổng điều khiển xe của [com.byd.clusternav.AppContainer] — CÙNG cổng mà thanh nút bắn. */
    carControl: CarControlPort,
    cameraTick: (Boolean, Boolean) -> Unit,
) {
    val hooks =
        TestBridgeHooks(
            owner = this,
            activity = { if (isFinishing || isDestroyed) null else this },
            state = { viewModel.uiState.value },
            dispatcher = { say, confirm ->
                VoiceWiring.dispatcher(
                    ctx = this,
                    state = { viewModel.uiState.value },
                    appsByLabel = { VoiceWiring.appsByLabel(this) },
                    // CHÍNH lambda mà phiên nghe dùng (`KachiHomeWiring.voiceSession`) — không phải
                    // `openAppFullscreen`: hai đường đó khác nhau thật (đường này trả về CÓ mở được hay không,
                    // và câu trả lời đó đi thẳng vào lời đáp cho người nói).
                    openApp = { pkg -> AppOpener(this).openByIntent(pkg) },
                    openAppList = { drawer().openAppList() },
                    openSettings = { panels().openSettings(SettingsGroup.SYSTEM) },
                    onSwitchProfile = { name -> viewModel.switchProfile(name) },
                    onListen = { voice().start() },
                    confirm = confirm,
                    say = say,
                    assignAppToSlot = { index, pkg -> slots().assignApp(index, pkg); true },
                    // L7 — CÙNG lambda mà lệnh `preset` của cầu kiểm thử dùng (`setPreset` ngay dưới), để hai
                    // lệnh của cùng một cầu không đi hai đường. ⚠ Khác đường của màn chính đúng MỘT bước: ở đó
                    // `selectPreset` còn bỏ bố cục tự vẽ trước. Cầu kiểm thử cố ý **không** bỏ — nó là bề mặt ĐO,
                    // và một lệnh đo không được tự tay xoá cấu hình của người dùng.
                    onLayout = { preset -> viewModel.setPreset(preset); true },
                )
            },
            assignAppToSlot = { index, pkg -> slots().assignApp(index, pkg); true },
            clearSlot = { index -> slots().clearSlot(index) },
            openApp = { pkg -> slots().openAppFullscreen(pkg) },
            control = { id, primary -> carControl.actByKind(id, primary) },
            switchProfile = { name -> viewModel.switchProfile(name) },
            setPreset = { preset -> viewModel.setPreset(preset) },
            setTopStripLabels = { on ->
                viewModel.setTopStrip(viewModel.uiState.value.topStrip.copy(showLabels = on))
            },
            listen = { voice().start() },
            shellUsable = { shell() != null },
            bridge = { clusterNavBridge() },
            cameraTick = cameraTick,
        )
    KachiTestHooks.attach(hooks)
    val host = application
    host.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityDestroyed(activity: Activity) {
            if (!hooks.ownedBy(activity)) return
            // Tự gỡ CẢ hai thứ: lượt theo dõi vòng đời và móc. Giữ lại bộ theo dõi là giữ một tham chiếu tới
            // Activity đã huỷ trong `Application` — thứ sống tới hết tiến trình.
            host.unregisterActivityLifecycleCallbacks(this)
            KachiTestHooks.detach(activity)
        }

        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    })
}
