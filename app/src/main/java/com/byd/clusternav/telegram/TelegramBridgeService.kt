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
import com.byd.clusternav.launcher.telegram.TelegramFreshness
import com.byd.clusternav.launcher.telegram.TelegramInlineKeyboard
import com.byd.clusternav.launcher.telegram.TelegramMenu
import com.byd.clusternav.launcher.telegram.MapsShareLink
import com.byd.clusternav.launcher.telegram.TelegramStatusLine
import com.byd.clusternav.launcher.telegram.TelegramUpdate
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceLaunch
import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceYoutubeResolver
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
 * xe đi đúng cổng tốc độ của `VoiceControlDispatch` (chỉ cốp cần 0 km/h), nhạc qua pending khi xe chưa lên (P1-9), bắt `Throwable` (P2-16),
 * phản hồi kết quả THẬT của lệnh về bot (P2-15).
 */
class TelegramBridgeService : Service() {

    private val polling = AtomicBoolean(false)
    @Volatile private var generation = 0

    /** Nonce bảng sống đọc từ prefs (P1-2 bền). Nút của bảng cũ (nonce khác) ⇒ không thi hành (R-nf2). */
    internal fun boardNonce(app: Context): Int = Prefs.telegramBoardNonce(app)
    /**
     * Review 26/10 P2-6: một lần vẽ bảng từng đọc ~11 lượt HAL (dòng trạng thái + bảng con đọc trùng nhau), vượt ngân
     * sách đọc đã đo trên xe (`CarControlPort.readState` KDoc: 33 lượt/phút). Trong [withReadCache] mỗi control chỉ
     * đọc MỘT lần. Chỉ dùng cho pha VẼ (sau khi đã ghi xe) — pha ghi và −/+ luôn đọc tươi. Chỉ luồng polling chạm.
     */
    internal var readCache: HashMap<String, Int?>? = null

    internal inline fun <T> withReadCache(block: () -> T): T {
        val outer = readCache
        if (outer == null) readCache = HashMap()
        try { return block() } finally { if (outer == null) readCache = null }
    }
    @Volatile internal var lastTempStepMs = 0L  // QD-08 debounce −/+ nhiệt
    @Volatile internal var lastFanStepMs = 0L   // QD-08 debounce −/+ quạt (mốc riêng — review 26/10 P2-5)

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
            var failures = 0                                  // P1-2: số lượt lỗi liền nhau (lùi chờ luỹ tiến)
            var backlog = true                                // P0-1: lượt đọc đầu sau một khoảng KHÔNG hỏi = tồn đọng
            while (myGen == generation) {
                val cfg = Prefs.telegramConfig(app)
                if (!cfg.ready) break
                if (cfg.botToken != token) {                  // P1-5: đổi token ⇒ dựng lại
                    token = cfg.botToken; api = TelegramApi(token); failures = 0; backlog = true
                    runCatching { api.setMyCommands(MENU_COMMANDS) }
                }
                // Mở nhạc pending nếu màn đã bật (xe lên) — R8 pha "đặt chờ rồi mở lúc lên xe".
                runCatching { playPendingMusicIfInteractive(app) }
                if (!interactive(app)) { backlog = true; sleep(5_000); continue }   // P1-8: không poll khi màn tắt
                // P1-3: lỗi một lượt KHÔNG thoát vòng (trước đây ném ra ngoài ⇒ service dừng im lặng).
                val updates = runCatching {
                    api.getUpdates(offset, POLL_TIMEOUT_SEC) { ControlRegistry.byId(it) != null }
                }.getOrNull()
                if (updates == null) {                        // P1-2: lỗi ⇒ lùi chờ 5s → 15s → 60s → trần 5 phút, có jitter
                    failures++
                    backlog = true                            // khoảng lỗi = khoảng không hỏi ⇒ lượt tốt kế là tồn đọng
                    sleep(backoffMs(failures))
                    continue
                }
                failures = 0
                val fromBacklog = backlog
                backlog = false
                val nowSec = System.currentTimeMillis() / 1000
                for (u in updates) {
                    if (myGen != generation) return                 // P1-4: tắt giữa batch ⇒ dừng ngay
                    val fresh = Prefs.telegramConfig(app)
                    if (!fresh.ready) return
                    offset = maxOf(offset, u.updateId + 1)
                    Prefs.setTelegramOffset(app, offset)            // P2-10: ghi TRƯỚC khi thi hành (at-most-once)
                    if (!fresh.isAuthorized(u.chatId)) continue      // P3: không log chatId người lạ
                    runCatching { handleUpdate(app, api, fresh, u, fromBacklog, nowSec) }
                        .onFailure { Log.w(TAG, "thi hành update lỗi: ${it.javaClass.simpleName}") }
                }
            }
        } catch (t: Throwable) {                                     // P2-16: Kotlin không checked-exception
            Log.e(TAG, "vòng polling lỗi: ${t.javaClass.simpleName}")
        } finally {
            polling.set(false)
            finish()
        }
    }

    /**
     * MỘT update: cổng tin cũ cho lệnh xe (P0-1) → menu callback hoặc lệnh gõ. Lệnh xe bị chặn vì cũ ⇒ báo thật cho chủ
     * xe ("không làm gì, gửi lại nếu vẫn muốn") thay vì im lặng — đó là câu họ cần để biết lệnh hôm qua không chạy.
     */
    private fun handleUpdate(app: Context, api: TelegramApi, cfg: TelegramConfig, u: TelegramUpdate, fromBacklog: Boolean, nowSec: Long) {
        val menuCb = u.rawCallback?.let { TelegramCallback.parse(it) }
        val carControl = TelegramFreshness.isCarControl(u.command, menuCb?.domain)
        if (!TelegramFreshness.allow(carControl, fromBacklog, u.dateSec, nowSec)) {
            u.callbackId?.let { api.answerCallback(it, "Old command ignored — tap again") }
            u.chatId?.let { api.sendMessage(it, "⏱ Ignored an old car command (sent while Kachi was offline). Send it again if you still want it.") }
            Log.i(TAG, "bỏ lệnh xe cũ (tồn đọng=$fromBacklog)")
            return
        }
        u.chatId?.let { ensureReplyKeyboard(app, api, it) }      // 3.1.20: bàn phím nút dưới ô gõ (một lần / phiên bản)
        if (menuCb != null) {
            if (handlePromptButton(api, u, menuCb)) return          // 🔎 Song · ➕ New ⇒ lời nhắc gõ chữ
            handleMenuCallback(app, api, cfg, u, menuCb)
        } else {
            u.callbackId?.let { api.answerCallback(it) }
            execute(app, api, cfg, u)
        }
    }

    /** P1-2: lùi chờ luỹ tiến có jitter — 5s, 15s, 60s, rồi trần 5 phút. */
    private fun backoffMs(failures: Int): Long {
        val base = when (failures) { 1 -> 5_000L; 2 -> 15_000L; 3 -> 60_000L; else -> 300_000L }
        return base + (Math.random() * base * 0.2).toLong()
    }

    private fun execute(app: Context, api: TelegramApi, cfg: TelegramConfig, u: TelegramUpdate) {
        val chatId = u.chatId ?: return
        runCatching {
            val c0 = u.command
            when (val c = c0) {
                is TelegramCommand.Nav -> {
                    val place = resolvePlace(app, c)
                    if (place == null) {
                        // /home · /office mà chưa ghim ⇒ nói thật, không dẫn bừa tới nơi đầu sổ (review 26/10 P3).
                        val msg = if (c.placeOrQuery.startsWith("__pin")) "📌 No place pinned for this yet — pin one with /places"
                                  else if (MapsShareLink.find(c.placeOrQuery) != null) "🤔 Couldn’t read that Google Maps link — try again or send a location."
                                  else "🤔 Couldn’t find “${c.placeOrQuery}”. Add it in saved places or send a location."
                        api.sendMessage(chatId, msg)
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
                        // QD-17: bảng mới với nonce KẾ TIẾP; chỉ ghi nonce + gỡ bảng cũ khi GỬI ĐƯỢC (như sendMainMenu).
                        val n = Prefs.telegramBoardNonce(app) + 1
                        val pinned = Prefs.telegramPinnedPlaces(app).toSet()
                        val id = api.sendMessageReturningId(chatId, "📍 Saved places — tap name to navigate, 📌 to pin to quick row:",
                            TelegramMenu.placesManage(n, places, pinned))
                        if (id != null) {
                            Prefs.bumpTelegramBoardNonce(app)
                            val oldMsg = Prefs.telegramBoardMsg(app)
                            if (oldMsg != 0L && oldMsg != id) runCatching { api.clearMarkup(chatId, oldMsg) }
                            Prefs.setTelegramBoardMsg(app, id)
                        }
                    }
                }
                TelegramCommand.Cancel -> {
                    PendingNavStore.consume(app)
                    api.sendMessage(chatId, "✖️ Pending destination cleared")
                }
                TelegramCommand.Help -> api.sendMessage(chatId, TelegramInlineKeyboard.HELP)
                TelegramCommand.Menu -> sendMainMenu(app, api, chatId)
                TelegramCommand.Status -> sendMainMenu(app, api, chatId)   // P1-6: /status = menu + bảng trạng thái sống
                is TelegramCommand.Board -> sendBoard(app, api, chatId, c.domain)   // 3.1.20: nút bàn phím
                is TelegramCommand.Unknown -> api.sendMessage(chatId, "🤔 Didn’t get that. Try /menu or /help")
            }
            // R-nf3/QD-17 (review 26/10 P2-6): sau một lệnh gõ có tác dụng, đưa bảng điều khiển xuống CUỐI chat — chỉ khi
            // người dùng ĐÃ có bảng (đã từng /menu), để người chỉ gõ lệnh không bị bot gửi thêm tin.
            val moves = c0 is TelegramCommand.Nav || c0 is TelegramCommand.Music || c0 is TelegramCommand.Control || c0 == TelegramCommand.Cancel
            if (moves && Prefs.telegramBoardMsg(app) != 0L) sendMainMenu(app, api, chatId)
        }.onFailure { Log.w(TAG, "thi hành lệnh Telegram lỗi: ${it.javaClass.simpleName}") }
    }

    /** Tên nơi trong sổ địa chỉ > chỉ số từ nút (`#<i>`) > nơi ghim (`__pin0__`/`__pin1__`) > chữ/toạ độ trực tiếp. */
    private fun resolvePlace(app: Context, c: TelegramCommand.Nav): com.byd.clusternav.launcher.SavedPlace? {
        val places = WorkspacePrefs(app).savedPlaces()
        // P1-6: /home → nơi GHIM #0, /office → nơi GHIM #1. Chỉ ghim THẬT — không lùi về nơi đầu sổ (review 26/10 P3:
        // "/home" từng dẫn tới "Gym" khi chưa ghim). Chưa ghim ⇒ null ⇒ chỗ gọi nhắc ghim qua /places.
        if (c.placeOrQuery == "__pin0__") return explicitPinned(app).getOrNull(0)
        if (c.placeOrQuery == "__pin1__") return explicitPinned(app).getOrNull(1)
        SavedPlaces.find(places, c.placeOrQuery)?.let { return it }
        // 3.1.20: link chia sẻ Google Maps ⇒ mở link rút gọn (chỉ đọc chuyển hướng) rồi lấy tên + địa chỉ / toạ độ.
        if (MapsShareLink.find(c.placeOrQuery) == c.placeOrQuery) {
            val t = MapsShareLink.parse(resolveMapsLink(c.placeOrQuery)) ?: return null
            return SavedPlaces.of(t.name, t.query, t.coords)
        }
        // Toạ độ: share-location (c.coords) HOẶC gõ "/go 21.02, 105.85" (review 26/10 P2-10: trước đây chuỗi gõ
        // không thành lat/lng ⇒ VietMap (cần toạ độ) bị bỏ, điểm đến kẹt thử lại 24h). Chữ trần ⇒ địa chỉ cho Google Maps.
        val coords = c.coords ?: c.placeOrQuery.takeIf { SavedPlaces.parseCoords(it) != null }
        return SavedPlaces.of(c.placeOrQuery, c.placeOrQuery, coords)
    }

    /** Nơi người dùng ĐÃ GHIM (theo thứ tự ghim), không có phần lùi về đầu sổ. */
    private fun explicitPinned(app: Context): List<com.byd.clusternav.launcher.SavedPlace> {
        val all = WorkspacePrefs(app).savedPlaces()
        return Prefs.telegramPinnedPlaces(app).mapNotNull { name -> all.firstOrNull { it.name == name } }
    }

    private fun isSafeUrl(url: String): Boolean =       // P2-12: chỉ http(s), chặn scheme lạ
        runCatching { Uri.parse(url).scheme?.lowercase() in setOf("http", "https") }.getOrDefault(false)

    private fun openUrl(app: Context, url: String): Boolean = runCatching {
        app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    /**
     * Mở nhạc theo TÊN BÀI qua App nhạc mặc định (`voice_music_default_app`), chưa đặt / không cài ⇒ YouTube.
     * App có đường watch (YouTube/YT Music) ⇒ mở thẳng video bài đầu (chuyển bài cả khi đang phát); còn lại / giải hỏng ⇒
     * `MEDIA_PLAY_FROM_SEARCH` + focus audio của [VoiceAppTargets].
     */
    private fun playQuery(app: Context, query: String): Boolean = runCatching {
        val installed = app.packageManager.getInstalledPackages(0).mapNotNull { it.packageName }.toSet()
        val target = VoiceAppTargets.byKey(Prefs.voiceMusicDefaultApp(app).ifBlank { VoiceAppTargets.YOUTUBE })
            ?.takeIf { it.packageIn(installed) != null }
            ?: VoiceAppTargets.byKey(VoiceAppTargets.YOUTUBE)?.takeIf { it.packageIn(installed) != null }
            ?: return@runCatching false
        val pkg = target.packageIn(installed) ?: return@runCatching false
        // 3.1.20 (owner 26/10: "/play 1 triệu like báo đã play nhưng vẫn hát bài cũ"): MEDIA_PLAY_FROM_SEARCH bị app nhạc
        // ĐANG PHÁT bỏ qua. Đi đúng đường của giọng nói (VoiceTargetDispatch.runMediaQuery): giải video_id bài đầu rồi mở
        // URL watch ⇒ app chuyển hẳn sang bài mới. Luồng polling là luồng nền ⇒ tải HTML ở đây được. Giải hỏng ⇒ lùi search.
        VoiceAppIntents.watchHandoff(target, pkg, query) { q -> VoiceYoutubeResolver.firstVideoIdBounded(q) }
            ?.let { h -> if (VoiceAppIntents.send(app, h)) return@runCatching true }
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
                val (text, markup) = withReadCache { renderBoard(app, cb, toast) }   // P2-6: đọc mỗi control một lần
                if (markup != null) api.editMessageText(chatId, msgId, text, markup)
            }
        }.onFailure { Log.w(TAG, "menu callback lỗi: ${it.javaClass.simpleName}") }
    }


    /**
     * Gửi menu chính (lệnh /menu /start, và sau mỗi lệnh gõ — R-nf3) — QD-17 một-bảng-sống: dựng bảng với nonce KẾ
     * TIẾP, gửi ở cuối chat; CHỈ KHI gửi được mới ghi nonce mới + gỡ nút bảng cũ (review 26/10 P3: tăng nonce trước rồi
     * gửi hỏng thì bảng cũ chết mà bảng mới không có). Nút của bảng cũ sau đó mang nonce khác ⇒ không thi hành.
     */
    private fun sendMainMenu(app: Context, api: TelegramApi, chatId: Long) = sendBoard(app, api, chatId, "menu")

    /** Như [sendMainMenu] cho MỘT bảng bất kỳ ([domain] của [renderBoard]) — nút bàn phím mở thẳng bảng đó (3.1.20). */
    internal fun sendBoard(app: Context, api: TelegramApi, chatId: Long, domain: String) {
        val n = Prefs.telegramBoardNonce(app) + 1
        val (text, markup) = withReadCache { renderBoard(app, TelegramCallback(n, domain, "open"), "", n) }   // P2-6
        val newId = api.sendMessageReturningId(chatId, text, markup ?: TelegramMenu.main(n, pinnedPlaces(app))) ?: return
        Prefs.bumpTelegramBoardNonce(app)
        val oldMsg = Prefs.telegramBoardMsg(app)
        if (oldMsg != 0L && oldMsg != newId) runCatching { api.clearMarkup(chatId, oldMsg) }   // gỡ nút bảng cũ
        Prefs.setTelegramBoardMsg(app, newId)
    }

    internal fun runControl(app: Context, c: TelegramCommand.Control): String {
        val arg = c.value ?: 1
        // 3.1.20 (owner 26/10 trên xe): KHÔNG còn cổng số P riêng cho Telegram — xe đậu ở P mà kính/cốp vẫn bị từ chối,
        // và luật của dự án là chỉ CỐP cần 0 km/h. Cổng đó (CtlSafetyPolicy.blocked) nằm SẴN trong VoiceControlDispatch.run
        // bên dưới — cùng một luật cho chạm · giọng · phím · Telegram, không phép thứ hai.
        // Review 26/10 P2-2: câu trả lời của VoiceReadback có thể đến BẤT ĐỒNG BỘ (đọc lại sau ~300 ms trên luồng nền),
        // nên đọc StringBuilder ngay sau run() luôn rỗng ⇒ bot chỉ nói câu chung. Nay chờ ĐÚNG MỘT câu, có trần
        // [CTL_REPLY_WAIT_MS] (luồng polling chờ, không phải luồng UI); quá trần ⇒ nói thật "đã gửi, chưa có xác nhận".
        val reply = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val said = java.util.concurrent.CountDownLatch(1)
        val dispatch = VoiceControlDispatch(
            control = { AppContainer.get(app).carControl },
            state = { HomeUiState(carStatus = AppContainer.get(app).carStatusRepository.status.value) },
            say = { text -> if (reply.compareAndSet(null, text)) said.countDown() },   // câu đầu tiên thắng
            freshCar = { id -> runCatching { AppContainer.get(app).readFresh(id) }.getOrNull() },
            onUi = { block -> block() },
            background = { block -> Thread(block, "KachiTgCtl").start() },
            lang = { com.byd.clusternav.launcher.Lang.EN },   // OQ3: bot nói tiếng Anh
        )
        dispatch.run(VoiceIntent.Control(c.controlId, arg)) { }
        runCatching { said.await(CTL_REPLY_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS) }
        return reply.get() ?: "🚗 ${c.controlId}: sent, no confirmation from the car yet"
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
        /** Trần chờ câu trả lời của xe cho MỘT lệnh (đọc lại sau ~300 ms của VoiceReadback + dư). Cốp/kính chạy mô-tơ
         *  lâu hơn ⇒ quá trần thì nói "đã gửi, chưa có xác nhận" (thật), không bịa ✓. */
        private const val CTL_REPLY_WAIT_MS = 2_500L
        private const val QUERY_PREFIX = "q:"   // pending nhạc dạng tên bài (phân biệt với link http(s))
        /** Domain nút cần công tắc "điều khiển xe" (P0-1). nav/music KHÔNG thuộc ⇒ luôn cho. */
        private val CAR_CONTROL_DOMAINS = TelegramFreshness.CAR_CONTROL_DOMAINS   // một nguồn (:core)

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
