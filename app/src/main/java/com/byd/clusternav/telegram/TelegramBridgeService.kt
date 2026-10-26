package com.byd.clusternav.telegram

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.byd.clusternav.AppContainer
import com.byd.clusternav.Prefs
import com.byd.clusternav.automation.AutomationService
import com.byd.clusternav.automation.PendingNavStore
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.CtlSafetyPolicy
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.launcher.SavedPlaces
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.VoiceControlDispatch
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.telegram.PendingDestinations
import com.byd.clusternav.launcher.telegram.TelegramCallback
import com.byd.clusternav.launcher.telegram.TelegramCommand
import com.byd.clusternav.launcher.telegram.TelegramConfig
import com.byd.clusternav.launcher.telegram.TelegramInlineKeyboard
import com.byd.clusternav.launcher.telegram.TelegramMenu
import com.byd.clusternav.launcher.telegram.TelegramStatusLine
import com.byd.clusternav.launcher.telegram.TelegramUpdate
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceLaunch
import com.byd.clusternav.pendingMusicUrl
import com.byd.clusternav.setPendingMusicUrl
import com.byd.clusternav.telegramConfig
import com.byd.clusternav.telegramOffset
import com.byd.clusternav.setTelegramOffset
import com.byd.clusternav.telegramPinnedPlaces
import com.byd.clusternav.telegramBoardNonce
import com.byd.clusternav.bumpTelegramBoardNonce
import com.byd.clusternav.telegramBoardMsg
import com.byd.clusternav.setTelegramBoardMsg
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FGS polling Telegram Bot API — spec `kachi-telegram-remote.html` T9. Long-poll `getUpdates` (~[POLL_TIMEOUT_SEC]s)
 * khi [TelegramConfig.ready]; mỗi update hợp lệ ([TelegramConfig.isAuthorized]) được thi hành. Vòng đời khuôn
 * [com.byd.clusternav.VietMapAutostartService] / [AutomationService].
 *
 * Senior review (2026-10-26) đã vá: không poll khi màn tắt (P1-8), đọc config+token+generation MỖI vòng và BỎ
 * batch khi tắt (P1-4/P1-5), con trỏ `offset` LƯU BỀN ghi TRƯỚC khi thi hành ⇒ at-most-once (P2-10), lệnh thân
 * xe từ Telegram đi cổng tốc độ NGOÀI (P0-2), nhạc qua pending khi xe chưa lên (P1-9), bắt `Throwable` (P2-16),
 * phản hồi kết quả THẬT của lệnh về bot (P2-15).
 */
class TelegramBridgeService : Service() {

    private val polling = AtomicBoolean(false)
    @Volatile private var generation = 0

    /** Nonce bảng sống đọc từ prefs (P1-2 bền). Nút của bảng cũ (nonce khác) ⇒ không thi hành (R-nf2). */
    internal fun boardNonce(app: Context): Int = Prefs.telegramBoardNonce(app)
    @Volatile internal var lastStepMs = 0L     // QD-08 debounce −/+

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startForegroundOnce()) { stopSelf(startId); return START_NOT_STICKY }
        val app = applicationContext
        if (!Prefs.telegramConfig(app).ready) { finish(); return START_NOT_STICKY }
        if (polling.compareAndSet(false, true)) {
            val myGen = ++generation
            Thread({ loop(app, myGen) }, "kachi-telegram").apply { isDaemon = true }.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        generation++
        super.onDestroy()
    }

    private fun loop(app: Context, myGen: Int) {
        try {
            var offset = Prefs.telegramOffset(app)            // P2-10: con trỏ bền, không mất khi BYD giết app
            var token = Prefs.telegramConfig(app).botToken
            var api = TelegramApi(token)
            runCatching { api.setMyCommands(MENU_COMMANDS) }  // QD-21: đăng ký gợi ý lệnh một lần khi vòng bắt đầu
            while (myGen == generation) {
                val cfg = Prefs.telegramConfig(app)
                if (!cfg.ready) break
                if (cfg.botToken != token) { token = cfg.botToken; api = TelegramApi(token); runCatching { api.setMyCommands(MENU_COMMANDS) } }  // P1-5
                // Mở nhạc pending nếu màn đã bật (xe lên) — R8 pha "đặt chờ rồi mở lúc lên xe".
                playPendingMusicIfInteractive(app)
                if (!interactive(app)) { sleep(5_000); continue }   // P1-8: không poll khi màn tắt
                val updates = api.getUpdates(offset, POLL_TIMEOUT_SEC) { ControlRegistry.byId(it) != null }
                for (u in updates) {
                    if (myGen != generation) return                 // P1-4: tắt giữa batch ⇒ dừng ngay
                    val fresh = Prefs.telegramConfig(app)
                    if (!fresh.ready) return
                    offset = maxOf(offset, u.updateId + 1)
                    Prefs.setTelegramOffset(app, offset)            // P2-10: ghi TRƯỚC khi thi hành (at-most-once)
                    if (!fresh.isAuthorized(u.chatId)) continue      // P3: không log chatId người lạ
                    // Menu callback (nút bấm bảng mới) đi đường riêng; lệnh text + callback cũ qua execute.
                    val menuCb = u.rawCallback?.let { TelegramCallback.parse(it) }
                    if (menuCb != null) {
                        handleMenuCallback(app, api, fresh, u, menuCb)
                    } else {
                        u.callbackId?.let { api.answerCallback(it) }
                        execute(app, api, fresh, u)
                    }
                }
            }
        } catch (t: Throwable) {                                     // P2-16: Kotlin không checked-exception
            Log.e(TAG, "vòng polling lỗi: ${t.javaClass.simpleName}")
        } finally {
            polling.set(false)
            finish()
        }
    }

    private fun execute(app: Context, api: TelegramApi, cfg: TelegramConfig, u: TelegramUpdate) {
        val chatId = u.chatId ?: return
        runCatching {
            when (val c = u.command) {
                is TelegramCommand.Nav -> {
                    val place = resolvePlace(app, c)
                    if (place == null) {
                        api.sendMessage(chatId, "🤔 Couldn’t find “${c.placeOrQuery}”. Add it in saved places or send a location.")
                        return@runCatching
                    }
                    val apps = c.apps.ifEmpty { listOf(Prefs.voiceNavDefaultApp(app)) }
                    val dest = PendingDestinations.of(place.name, place.query, apps,
                        if (place.hasCoords) "${place.lat}, ${place.lng}" else c.coords) ?: return@runCatching
                    // GPS mặc định BẮT BUỘC (owner chốt): chỉ dẫn khi có định vị. Chưa có fix ⇒ CHỜ, không dẫn mù.
                    PendingNavStore.write(app, dest, requireGps = true)
                    AutomationService.sync(app)
                    // P2-15: nói đúng sự thật — đã ĐẶT, sẽ dẫn khi lên xe (hoặc nhịp 60s nếu đang chạy).
                    api.sendMessage(chatId, "🧭 Set “${place.name}” — will navigate when you board (priority over schedule).")
                }
                is TelegramCommand.Music -> {
                    if (c.isQuery) {                                 // tên bài ⇒ App nhạc mặc định (hoặc YouTube), search
                        if (interactive(app) && playQuery(app, c.text)) {
                            api.sendMessage(chatId, "🎵 Playing “${c.text}”")
                        } else {
                            Prefs.setPendingMusicUrl(app, QUERY_PREFIX + c.text)   // lên xe mới search
                            AutomationService.sync(app)
                            api.sendMessage(chatId, "🎵 Saved “${c.text}” — will play when you board")
                        }
                    } else {
                        if (!isSafeUrl(c.text)) { api.sendMessage(chatId, "⚠️ Only http(s) links are allowed"); return@runCatching }
                        if (interactive(app) && openUrl(app, c.text)) {
                            api.sendMessage(chatId, "🎵 Opening music now")
                        } else {
                            Prefs.setPendingMusicUrl(app, c.text)    // P1-9: xe chưa lên ⇒ đặt chờ, mở lúc lên xe
                            AutomationService.sync(app)
                            api.sendMessage(chatId, "🎵 Saved — will play when you board")
                        }
                    }
                }
                is TelegramCommand.Control -> {
                    if (!cfg.controlEnabled) {
                        api.sendMessage(chatId, "🔒 Car control is off. Enable it in Kachi settings.")
                        return@runCatching
                    }
                    // P0-2: bộ phận chuyển động từ Telegram phải NÓI RÕ mở/đóng (không động từ ngầm — ngang VoiceBareCover).
                    if (CtlSafetyPolicy.needsConfirm(c.controlId) && c.value == null) {
                        api.sendMessage(chatId, "⚠️ ${c.controlId}: send “/${c.controlId} open” or “/${c.controlId} close”")
                        return@runCatching
                    }
                    val reply = runControl(app, c)                  // P2-15: kết quả THẬT (✓ / "chỉ mở khi dừng")
                    api.sendMessage(chatId, reply)
                }
                TelegramCommand.ListPlaces -> {
                    val places = WorkspacePrefs(app).savedPlaces()
                    if (places.isEmpty()) api.sendMessage(chatId, "📍 No saved places yet. Add some in Kachi settings.")
                    else {
                        val n = Prefs.bumpTelegramBoardNonce(app)   // bảng mới (QD-17)
                        val oldMsg = Prefs.telegramBoardMsg(app)
                        if (oldMsg != 0L) runCatching { api.clearMarkup(chatId, oldMsg) }
                        val pinned = Prefs.telegramPinnedPlaces(app).toSet()
                        val id = api.sendMessageReturningId(chatId, "📍 Saved places — tap name to navigate, 📌 to pin to quick row:",
                            TelegramMenu.placesManage(n, places, pinned))
                        if (id != null) Prefs.setTelegramBoardMsg(app, id)
                    }
                }
                TelegramCommand.Cancel -> {
                    PendingNavStore.consume(app)
                    api.sendMessage(chatId, TelegramInlineKeyboard.ackText(c))
                }
                TelegramCommand.Help -> api.sendMessage(chatId, TelegramInlineKeyboard.HELP)
                TelegramCommand.Menu -> sendMainMenu(app, api, chatId)
                TelegramCommand.Status -> sendMainMenu(app, api, chatId)   // P1-6: /status = menu + bảng trạng thái sống
                is TelegramCommand.Unknown -> api.sendMessage(chatId, "🤔 Didn’t get that. Try /menu or /help")
            }
        }.onFailure { Log.w(TAG, "thi hành lệnh Telegram lỗi: ${it.javaClass.simpleName}") }
    }

    /** Tên nơi trong sổ địa chỉ > chỉ số từ nút (`#<i>`) > nơi ghim (`__pin0__`/`__pin1__`) > chữ/toạ độ trực tiếp. */
    private fun resolvePlace(app: Context, c: TelegramCommand.Nav): com.byd.clusternav.launcher.SavedPlace? {
        val places = WorkspacePrefs(app).savedPlaces()
        // P1-6: /home → nơi ghim #0, /office → nơi ghim #1 (hàng nhanh); không ghim ⇒ 2 nơi đầu sổ (pinnedPlaces).
        if (c.placeOrQuery == "__pin0__") return pinnedPlaces(app).getOrNull(0)
        if (c.placeOrQuery == "__pin1__") return pinnedPlaces(app).getOrNull(1)
        // Nút inline gửi "#<index>" (P2-17) ⇒ giải index trong CÙNG danh sách đã render.
        if (c.placeOrQuery.startsWith("#")) {
            val i = c.placeOrQuery.drop(1).toIntOrNull() ?: return null
            return places.getOrNull(i)
        }
        SavedPlaces.find(places, c.placeOrQuery)?.let { return it }
        // Toạ độ (share-location / "/go 21,105") vẫn dẫn được; chữ trần không trong sổ ⇒ coi là địa chỉ cho Google Maps.
        return SavedPlaces.of(c.placeOrQuery, c.placeOrQuery, c.coords)
    }

    private fun isSafeUrl(url: String): Boolean =       // P2-12: chỉ http(s), chặn scheme lạ
        runCatching { Uri.parse(url).scheme?.lowercase() in setOf("http", "https") }.getOrDefault(false)

    private fun openUrl(app: Context, url: String): Boolean = runCatching {
        app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    /**
     * Mở nhạc theo TÊN BÀI qua App nhạc mặc định (`voice_music_default_app`), chưa đặt / không cài ⇒ YouTube.
     * Dùng đúng đường `MEDIA_PLAY_FROM_SEARCH` + focus audio của [VoiceAppTargets] (hợp đồng nền tảng để PHÁT).
     */
    private fun playQuery(app: Context, query: String): Boolean = runCatching {
        val installed = app.packageManager.getInstalledPackages(0).mapNotNull { it.packageName }.toSet()
        val target = VoiceAppTargets.byKey(Prefs.voiceMusicDefaultApp(app).ifBlank { VoiceAppTargets.YOUTUBE })
            ?.takeIf { it.packageIn(installed) != null }
            ?: VoiceAppTargets.byKey(VoiceAppTargets.YOUTUBE)?.takeIf { it.packageIn(installed) != null }
            ?: return@runCatching false
        val pkg = target.packageIn(installed) ?: return@runCatching false
        val launch = target.launch as? VoiceLaunch.Action ?: return@runCatching false
        val i = Intent(launch.action).setPackage(pkg)
            .putExtra(launch.extra, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch.extras.forEach { (k, v) -> i.putExtra(k, v) }
        app.startActivity(i)
        true
    }.getOrDefault(false)

    private fun playPendingMusicIfInteractive(app: Context) {
        val saved = Prefs.pendingMusicUrl(app) ?: return
        if (!interactive(app)) return
        val ok = if (saved.startsWith(QUERY_PREFIX)) playQuery(app, saved.removePrefix(QUERY_PREFIX))
                 else isSafeUrl(saved) && openUrl(app, saved)
        if (ok) Prefs.setPendingMusicUrl(app, null)   // mở một lần rồi xoá
    }

    /** `true` = màn đang bật (xe lên). P1-8 + điều kiện mở nhạc. */
    private fun interactive(app: Context): Boolean =
        runCatching { (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive }.getOrDefault(false)

    /**
     * Xử nút bấm menu (QD-05/16/17/18/25). Thứ tự: (1) đóng vòng xoay + toast NGAY (<1s, R-nf4); (2) nonce gate —
     * nút bảng cũ ⇒ toast "bảng cũ", KHÔNG thi hành (R-nf2); (3) thi hành qua đường có sẵn; (4) vẽ lại bảng
     * (một-bảng-sống: sửa tại chỗ tin chứa nút).
     */
    private fun handleMenuCallback(app: Context, api: TelegramApi, cfg: TelegramConfig, u: TelegramUpdate, cb: TelegramCallback) {
        val chatId = u.chatId ?: return
        val cbId = u.callbackId
        runCatching {
            // (2) nonce gate — chỉ nút của bảng SỐNG mới thi hành (R-nf2).
            if (cb.nonce != boardNonce(app)) {
                cbId?.let { api.answerCallback(it, "Old panel — send /menu") }
                return@runCatching
            }
            // P0-1: owner TẮT công tắc điều khiển xe ⇒ nút điều khiển (ac/seat/misc) không thi hành (nav/music vẫn cho).
            if (cb.domain in CAR_CONTROL_DOMAINS && !cfg.controlEnabled) {
                cbId?.let { api.answerCallback(it, "🔒 Car control is off (enable in Kachi settings)") }
                return@runCatching
            }
            // (1) R-nf4: đóng vòng xoay NGAY (<1s), TRƯỚC khi chờ xe trả lời.
            cbId?.let { api.answerCallback(it) }
            // (3) thi hành qua đường có sẵn; kết quả thật về (4).
            val toast = dispatchMenu(app, cb)
            // (4) vẽ lại đúng bảng tại chỗ (một-bảng-sống) kèm kết quả. markup null ⇒ không đổi bảng.
            val msgId = u.messageId
            if (msgId != null) {
                val (text, markup) = renderBoard(app, cb, toast)
                if (markup != null) api.editMessageText(chatId, msgId, text, markup)
            }
        }.onFailure { Log.w(TAG, "menu callback lỗi: ${it.javaClass.simpleName}") }
    }


    /**
     * Gửi menu chính (lệnh /menu /start) — QD-17 một-bảng-sống: tăng nonce bền, GỠ NÚT bảng cũ, gửi bảng mới ở
     * cuối chat, nhớ message_id mới. Nút của bảng cũ nay nonce khác ⇒ không thi hành.
     */
    private fun sendMainMenu(app: Context, api: TelegramApi, chatId: Long) {
        val n = Prefs.bumpTelegramBoardNonce(app)
        val oldMsg = Prefs.telegramBoardMsg(app)
        if (oldMsg != 0L) runCatching { api.clearMarkup(chatId, oldMsg) }   // gỡ nút bảng cũ (còn text, không bấm được)
        val newId = api.sendMessageReturningId(chatId, statusText(app), TelegramMenu.main(n, pinnedPlaces(app)))
        if (newId != null) Prefs.setTelegramBoardMsg(app, newId)
    }

    internal fun runControl(app: Context, c: TelegramCommand.Control): String {
        val arg = c.value ?: 1
        // P0-2: cổng tốc độ NGOÀI (rộng cho mọi bộ phận bung/che tầm nhìn khi lệnh từ điện thoại).
        val motion = { CtlSafetyPolicy.readMotion({ id -> runCatching { AppContainer.get(app).readFresh(id) }.getOrNull() }) {
            AppContainer.get(app).carStatusRepository.status.value
        } }
        if (CtlSafetyPolicy.blockedExternal(c.controlId, arg, motion)) {
            return "🚗 ${c.controlId}: only opens when the car is stopped"
        }
        val captured = StringBuilder()
        val dispatch = VoiceControlDispatch(
            control = { AppContainer.get(app).carControl },
            state = { HomeUiState(carStatus = AppContainer.get(app).carStatusRepository.status.value) },
            say = { captured.append(it) },            // P2-15: bắt câu trả lời, không nuốt vào log
            freshCar = { id -> runCatching { AppContainer.get(app).readFresh(id) }.getOrNull() },
            onUi = { block -> block() },
            background = { block -> Thread(block, "KachiTgCtl").start() },
            lang = { Strings.current },
        )
        dispatch.run(VoiceIntent.Control(c.controlId, arg)) { }
        return captured.toString().ifBlank { "🚗 ${c.controlId} ${c.value ?: "toggle"}" }
    }

    private fun finish() = runCatching { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    private fun sleep(ms: Long) = runCatching { Thread.sleep(ms) }

    private fun startForegroundOnce(): Boolean = runCatching {
        startForeground(NOTIFICATION_ID, notification()); true
    }.getOrElse { Log.e(TAG, "startForeground denied", it); false }

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Kachi Telegram", NotificationManager.IMPORTANCE_MIN)
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Kachi")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    companion object {
        private const val TAG = "KachiTelegram"
        private const val QUERY_PREFIX = "q:"   // pending nhạc dạng tên bài (phân biệt với link http(s))
        /** Domain nút cần công tắc "điều khiển xe" (P0-1). nav/music KHÔNG thuộc ⇒ luôn cho. */
        private val CAR_CONTROL_DOMAINS = setOf("ac", "seat", "misc")

        /** 9 lệnh gợi ý (QD-21, OQ3 tiếng Anh) — chỉ loại nút-không-làm-được / phím tắt một phát. */
        private val MENU_COMMANDS = listOf(
            "menu" to "Open control panel",
            "home" to "Navigate home",
            "office" to "Navigate to office",
            "go" to "Navigate to a saved place: /go gym",
            "play" to "Play music: /play <song | link>",
            "ac" to "Set AC: /ac 24 or /ac off",
            "cancel" to "Clear pending destination",
            "status" to "Show car status",
            "places" to "Manage saved places",
        )
        private const val CHANNEL_ID = "kachi_telegram"
        private const val NOTIFICATION_ID = 1048   // bảng ID FGS: VoiceKeyKeepAliveService.NOTIFICATION_ID KDoc
        private const val POLL_TIMEOUT_SEC = 60

        /** Đồng bộ service với config. Chưa cấu hình ⇒ dừng service (P1-4: mirror AutomationService.sync). */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext
            if (!Prefs.telegramConfig(app).ready) {
                runCatching { app.stopService(Intent(app, TelegramBridgeService::class.java)) }
                return
            }
            runCatching {
                val i = Intent(app, TelegramBridgeService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(i) else app.startService(i)
            }.onFailure { Log.w(TAG, "start bridge lỗi", it) }
        }
    }
}
