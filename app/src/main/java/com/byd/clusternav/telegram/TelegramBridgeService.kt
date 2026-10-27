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
import com.byd.clusternav.Prefs
import com.byd.clusternav.automation.AutomationService
import com.byd.clusternav.automation.PendingNavStore
import com.byd.clusternav.launcher.SavedPlace
import com.byd.clusternav.launcher.SavedPlaces
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.telegram.MapsShareLink
import com.byd.clusternav.launcher.telegram.MusicLink
import com.byd.clusternav.launcher.telegram.PendingDestinations
import com.byd.clusternav.launcher.telegram.TelegramCallback
import com.byd.clusternav.launcher.telegram.TelegramCommand
import com.byd.clusternav.launcher.telegram.TelegramConfig
import com.byd.clusternav.launcher.telegram.TelegramInlineKeyboard
import com.byd.clusternav.launcher.telegram.TelegramMenu
import com.byd.clusternav.launcher.telegram.TelegramReplyKeyboard
import com.byd.clusternav.launcher.telegram.TelegramUpdate
import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceGeocoder
import com.byd.clusternav.launcher.voice.VoiceLaunch
import com.byd.clusternav.launcher.voice.VoiceYoutubeResolver
import com.byd.clusternav.pendingMusicUrl
import com.byd.clusternav.setPendingMusicUrl
import com.byd.clusternav.telegramConfig
import com.byd.clusternav.telegramOffset
import com.byd.clusternav.setTelegramOffset
import com.byd.clusternav.telegramBoardNonce
import com.byd.clusternav.bumpTelegramBoardNonce
import com.byd.clusternav.telegramPinnedPlaces
import com.byd.clusternav.setTelegramPinnedPlaces
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FGS polling Telegram Bot API — long-poll `getUpdates` (~[POLL_TIMEOUT_SEC] s) khi [TelegramConfig.ready]; mỗi update của
 * đúng chủ xe ([TelegramConfig.isAuthorized]) được thi hành.
 *
 * ## Phạm vi 3.1.21 (owner 26/10): CHỈ dẫn đường + nhạc
 * *"không đọc ghi gì vào xe hết, an toàn data privacy, chỉ làm basic"* ⇒ service này **không đọc HAL, không ghi HAL, không
 * gửi bất kỳ trạng thái xe nào về Telegram**. Nó chỉ: đặt điểm đến chờ (`PendingNavStore`, dẫn khi lên xe + có GPS — vòng
 * `AutomationService`), và mở app nhạc. Bài canh phạm vi ở `TelegramButtonsWiringContractTest` chặn mọi đường xe quay lại.
 *
 * Giữ từ các bản trước: không poll khi màn tắt, con trỏ `offset` bền ghi TRƯỚC khi thi hành (at-most-once), lùi chờ khi
 * lỗi, token không bao giờ vào log ([TelegramApi]).
 */
class TelegramBridgeService : Service() {

    private val polling = AtomicBoolean(false)
    @Volatile private var generation = 0

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
            var offset = Prefs.telegramOffset(app)
            var token = Prefs.telegramConfig(app).botToken
            var api = TelegramApi(token)
            runCatching { api.setMyCommands(MENU_COMMANDS) }
            var failures = 0
            while (myGen == generation) {
                val cfg = Prefs.telegramConfig(app)
                if (!cfg.ready) break
                if (cfg.botToken != token) {
                    token = cfg.botToken; api = TelegramApi(token); failures = 0
                    runCatching { api.setMyCommands(MENU_COMMANDS) }
                }
                runCatching { playPendingMusicIfInteractive(app) }       // nhạc đặt chờ ⇒ mở lúc lên xe
                if (!interactive(app)) { sleep(5_000); continue }        // không poll khi màn tắt
                val updates = runCatching { api.getUpdates(offset, POLL_TIMEOUT_SEC) }.getOrNull()
                if (updates == null) { failures++; sleep(backoffMs(failures)); continue }
                failures = 0
                for (u in updates) {
                    if (myGen != generation) return
                    val fresh = Prefs.telegramConfig(app)
                    if (!fresh.ready) return
                    offset = maxOf(offset, u.updateId + 1)
                    Prefs.setTelegramOffset(app, offset)                 // ghi TRƯỚC khi thi hành (at-most-once)
                    if (!fresh.isAuthorized(u.chatId)) continue
                    runCatching { handleUpdate(app, api, u) }
                        .onFailure { Log.w(TAG, "thi hành update lỗi: ${it.javaClass.simpleName}") }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "vòng polling lỗi: ${t.javaClass.simpleName}")
        } finally {
            polling.set(false)
            finish()
        }
    }

    private fun handleUpdate(app: Context, api: TelegramApi, u: TelegramUpdate) {
        val chatId = u.chatId ?: return
        ensureReplyKeyboard(app, api, chatId)
        val cb = u.rawCallback?.let { TelegramCallback.parse(it) }
        if (u.rawCallback != null) { handlePlacesButton(app, api, u, cb); return }
        // Mọi câu trả lời chữ trơn mang LẠI bàn phím nút (owner 27/10: "chip lúc đầu hiện, bấm xong giờ mất tiêu"):
        // tin `ForceReply` của lời nhắc Song/New thay bàn phím trên máy khách — gửi kèm lại ở tin kế là bàn phím về.
        val say = { text: String -> api.sendMessage(chatId, text, TelegramReplyKeyboard.markup()) }
        when (val c = u.command) {
            is TelegramCommand.Nav -> say(navigate(app, c))
            is TelegramCommand.Music -> say(playMusic(app, c))
            TelegramCommand.ListPlaces -> sendPlaces(app, api, chatId)
            TelegramCommand.AskSong -> api.sendMessage(chatId, TelegramReplyKeyboard.SONG_PROMPT, TelegramReplyKeyboard.forceReply("Song name or link"))
            TelegramCommand.Cancel -> { PendingNavStore.consume(app); say("✖️ Pending destination cleared") }
            TelegramCommand.Help, TelegramCommand.Menu -> say(TelegramInlineKeyboard.HELP)
            is TelegramCommand.Unknown -> say("🤔 Didn’t get that. Paste a Google Maps / YouTube link, or tap ❓ Help")
        }
    }

    // ── Dẫn đường ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Đặt điểm đến chờ (dẫn khi lên xe + có GPS — owner chốt GPS bắt buộc). App cần toạ độ (VietMap — [ĐO RE 23/09]: chỉ nhận
     * lat/lng) mà nơi chỉ có chữ ⇒ tra toạ độ bằng đúng đường của giọng nói ([VoiceGeocoder]); tra hỏng ⇒ bỏ app đó và NÓI RA,
     * không bao giờ đặt một điểm đến mà không app nào dẫn được. Trả câu cho người dùng.
     */
    internal fun navigate(app: Context, c: TelegramCommand.Nav): String {
        val found = resolvePlace(app, c) ?: return if (MapsShareLink.find(c.placeOrQuery) != null)
            "🤔 Couldn’t read that Google Maps link — try again or send a location."
        else "🤔 Couldn’t find “${c.placeOrQuery}”. Send an address, a Google Maps link or a location."
        return setDestination(app, found, c.apps.ifEmpty { listOf(Prefs.voiceNavDefaultApp(app)) })
    }

    internal fun setDestination(app: Context, found: SavedPlace, apps: List<String>): String {
        var place = found
        val needCoords = apps.filter { VoiceAppTargets.byKey(it)?.needsCoords == true }
        if (!place.hasCoords && needCoords.isNotEmpty()) {
            val geo = runCatching { VoiceGeocoder.resolveBounded(app, place.query) }.getOrNull()
            if (geo != null) place = SavedPlaces.of(place.name, place.query, "${geo.lat}, ${geo.lng}") ?: place
        }
        val usable = if (place.hasCoords) apps else apps - needCoords.toSet()
        val dropped = apps - usable.toSet()
        if (usable.isEmpty()) return "🤔 ${dropped.joinToString("+")} needs a map position and “${place.name}” couldn’t be located. Try gmaps, or send a location."
        val dest = PendingDestinations.of(place.name, place.query, usable,
            if (place.hasCoords) "${place.lat}, ${place.lng}" else null) ?: return "🤔 Couldn’t set that destination"
        PendingNavStore.write(app, dest, requireGps = true)
        AutomationService.sync(app)
        val note = if (dropped.isEmpty()) "" else " (${dropped.joinToString("+")} skipped: no map position)"
        return "🧭 “${place.name}” → ${usable.joinToString(" + ")} — navigating when you board (GPS needed)$note"
    }

    /** Tên trong sổ > link chia sẻ Google Maps > toạ độ > chữ địa chỉ (Google Maps tự tra). */
    private fun resolvePlace(app: Context, c: TelegramCommand.Nav): SavedPlace? {
        SavedPlaces.find(WorkspacePrefs(app).savedPlaces(), c.placeOrQuery)?.let { return it }
        if (MapsShareLink.find(c.placeOrQuery) == c.placeOrQuery) {
            val t = MapsShareLink.parse(resolveMapsLink(c.placeOrQuery)) ?: return null
            return SavedPlaces.of(t.name, t.query, t.coords)
        }
        val coords = c.coords ?: c.placeOrQuery.takeIf { SavedPlaces.parseCoords(it) != null }
        return SavedPlaces.of(c.placeOrQuery, c.placeOrQuery, coords)
    }

    private fun sendPlaces(app: Context, api: TelegramApi, chatId: Long, page: Int = 0) {
        val places = WorkspacePrefs(app).savedPlaces()
        val n = Prefs.telegramBoardNonce(app) + 1
        val text = if (places.isEmpty()) "📍 No saved places yet — tap ➕ New or paste a Google Maps link"
                   else "📍 Tap a place to navigate (default app), or ➕ New"
        if (api.sendMessageReturningId(chatId, text, TelegramMenu.places(n, places, page, Prefs.telegramPinnedPlaces(app))) != null) {
            Prefs.bumpTelegramBoardNonce(app)
        }
    }

    /**
     * Nút của bảng nơi đã lưu (sửa TẠI CHỖ cùng một tin): `go <nơi>` ⇒ bảng chọn app · `app <nơi>:<g|v|gv>` ⇒ đặt điểm
     * đến · `pin <nơi>` ⇒ ghim/bỏ ghim · `page <p>` ⇒ danh sách (trang p) · `new` ⇒ hỏi điểm đến. Nonce cũ ⇒ bỏ.
     */
    private fun handlePlacesButton(app: Context, api: TelegramApi, u: TelegramUpdate, cb: TelegramCallback?) {
        val chatId = u.chatId ?: return
        if (cb == null || cb.nonce != Prefs.telegramBoardNonce(app)) {
            u.callbackId?.let { api.answerCallback(it, "Old list — tap 🧭 Go") }
            return
        }
        u.callbackId?.let { api.answerCallback(it) }
        val places = WorkspacePrefs(app).savedPlaces()
        val pins = Prefs.telegramPinnedPlaces(app).filter { n -> places.any { it.name == n } }
        val placeOf = { id: String -> TelegramMenu.placeIndex(id, places)?.let { places[it] } }
        val stale = "📍 Saved places changed — tap 🧭 Go for a fresh list"
        fun edit(text: String, markup: String) { u.messageId?.let { api.editMessageText(chatId, it, text, markup) } }
        fun picker(id: String, p: SavedPlace) = edit("📍 “${p.name}” — navigate with:",
            TelegramMenu.appPicker(cb.nonce, id, Prefs.voiceNavDefaultApp(app), p.name in pins, pins.size < TelegramMenu.MAX_PINS))
        when (cb.action) {
            "new" -> api.sendMessage(chatId, TelegramReplyKeyboard.DEST_PROMPT, TelegramReplyKeyboard.forceReply("Destination"))
            "page" -> edit("📍 Tap a place, then pick the app — or ➕ New",
                TelegramMenu.places(cb.nonce, places, cb.id.toIntOrNull() ?: 0, pins))
            "go" -> placeOf(cb.id)?.let { picker(cb.id, it) } ?: api.sendMessage(chatId, stale, TelegramReplyKeyboard.markup())
            "pin" -> placeOf(cb.id)?.let { p ->
                Prefs.setTelegramPinnedPlaces(app, TelegramMenu.togglePin(pins, p.name))
                picker(cb.id, p)
            } ?: api.sendMessage(chatId, stale, TelegramReplyKeyboard.markup())
            "app" -> {
                val choice = TelegramMenu.appChoice(cb.id)
                val place = choice?.let { placeOf(it.first) }
                api.sendMessage(chatId, if (choice == null || place == null) stale else setDestination(app, place, choice.second),
                    TelegramReplyKeyboard.markup())
            }
        }
    }

    // ── Nhạc ──────────────────────────────────────────────────────────────────────────────────────────

    /** Link ⇒ mở đúng app theo host; tên bài ⇒ tìm rồi phát. Xe chưa lên (màn tắt) ⇒ đặt chờ, mở lúc lên xe. */
    internal fun playMusic(app: Context, c: TelegramCommand.Music): String {
        if (!c.isQuery && !isSafeUrl(c.text)) return "⚠️ Only http(s) links are allowed"
        if (!interactive(app)) {
            Prefs.setPendingMusicUrl(app, if (c.isQuery) QUERY_PREFIX + (c.app?.let { "$it:" } ?: ":") + c.text else c.text)
            return "🎵 Saved — will play when you board"
        }
        return if (c.isQuery) {
            if (playQuery(app, c.text, c.app)) "🎵 Playing “${c.text}”" else "⚠️ No music app to search “${c.text}”"
        } else {
            if (openMusicLink(app, c.text)) "🎵 Playing link" else "⚠️ Couldn’t open that link"
        }
    }

    private fun isSafeUrl(url: String): Boolean =
        runCatching { Uri.parse(url).scheme?.lowercase() in setOf("http", "https") }.getOrDefault(false)

    /**
     * Link YouTube / YT Music ⇒ `VIEW` vào ĐÚNG app theo host (không để hệ thống hỏi chọn app). Mở URL watch là app phát
     * ngay, đè bài đang phát. App không cài ⇒ `VIEW` trơn (trình duyệt).
     */
    private fun openMusicLink(app: Context, url: String): Boolean = runCatching {
        val installed = app.packageManager.getInstalledPackages(0).mapNotNull { it.packageName }.toSet()
        val pkg = MusicLink.appOf(url)?.let { VoiceAppTargets.byKey(it)?.packageIn(installed) }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg != null) i.setPackage(pkg)
        app.startActivity(i)
        true
    }.getOrDefault(false)

    /**
     * Tên bài ⇒ [appKey] (`youtube`/`ytmusic`) hoặc App nhạc mặc định, chưa đặt / không cài ⇒ YouTube. App có đường watch
     * ⇒ giải video bài đầu rồi mở URL watch (chuyển bài cả khi đang phát — `MEDIA_PLAY_FROM_SEARCH` bị app đang phát bỏ qua
     * [owner 26/10]); giải hỏng ⇒ `MEDIA_PLAY_FROM_SEARCH`. Luồng polling là luồng nền ⇒ tải HTML ở đây được.
     */
    private fun playQuery(app: Context, query: String, appKey: String?): Boolean = runCatching {
        val installed = app.packageManager.getInstalledPackages(0).mapNotNull { it.packageName }.toSet()
        val target = listOfNotNull(appKey, Prefs.voiceMusicDefaultApp(app).ifBlank { null }, VoiceAppTargets.YOUTUBE)
            .firstNotNullOfOrNull { k -> VoiceAppTargets.byKey(k)?.takeIf { it.packageIn(installed) != null } }
            ?: return@runCatching false
        val pkg = target.packageIn(installed) ?: return@runCatching false
        VoiceAppIntents.watchHandoff(target, pkg, query) { q -> VoiceYoutubeResolver.firstVideoIdBounded(q) }
            ?.let { h -> if (VoiceAppIntents.send(app, h)) return@runCatching true }
        val launch = target.launch as? VoiceLaunch.Action ?: return@runCatching false
        val i = Intent(launch.action).setPackage(pkg).putExtra(launch.extra, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch.extras.forEach { (k, v) -> i.putExtra(k, v) }
        app.startActivity(i)
        true
    }.getOrDefault(false)

    private fun playPendingMusicIfInteractive(app: Context) {
        val saved = Prefs.pendingMusicUrl(app) ?: return
        if (!interactive(app)) return
        val ok = if (saved.startsWith(QUERY_PREFIX)) {
            val rest = saved.removePrefix(QUERY_PREFIX)
            playQuery(app, rest.substringAfter(':'), rest.substringBefore(':').ifBlank { null })
        } else isSafeUrl(saved) && openMusicLink(app, saved)
        if (ok) Prefs.setPendingMusicUrl(app, null)
    }

    /** `true` = màn đang bật (xe lên). Chỉ đọc trạng thái màn của chính đầu xe, không đọc dữ liệu xe. */
    private fun interactive(app: Context): Boolean =
        runCatching { (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive }.getOrDefault(false)

    private fun backoffMs(failures: Int): Long {
        val base = when (failures) { 1 -> 5_000L; 2 -> 15_000L; 3 -> 60_000L; else -> 300_000L }
        return base + (Math.random() * base * 0.2).toLong()
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
        private const val QUERY_PREFIX = "q:"   // nhạc chờ dạng tên bài: "q:<app>:<tên>" (app rỗng = mặc định)

        /** Gợi ý lệnh (gõ "/") — chỉ dẫn đường + nhạc. */
        private val MENU_COMMANDS = listOf(
            "go" to "Navigate: /go <place> [gmaps vietmap waze]",
            "play" to "Play: /play <song> [youtube | ytmusic]",
            "places" to "Saved places",
            "cancel" to "Clear pending destination",
            "help" to "How to use",
        )
        private const val CHANNEL_ID = "kachi_telegram"
        private const val NOTIFICATION_ID = 1048   // bảng ID FGS: VoiceKeyKeepAliveService.NOTIFICATION_ID KDoc
        private const val POLL_TIMEOUT_SEC = 60

        /** Đồng bộ service với config. Chưa cấu hình ⇒ dừng service. */
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
