package com.byd.clusternav.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import com.byd.clusternav.NavNotificationListener
import com.byd.clusternav.launcher.trip.TripMusicPlan

/** Ảnh chụp phiên nhạc đang phát cho widget nhạc. Mọi field nullable/rỗng-an-toàn → widget "—" khi không có. */
data class MediaSnapshot(
    val title: String?,
    val artist: String?,
    val albumArt: Bitmap?,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
)

/**
 * Bốn lệnh TRANSPORT mà [VoiceDispatcher] cần — cùng lý do tồn tại với [MediaLike] ngay dưới: [MediaBridge] cầm
 * `Context` nên không dựng được trong một bài kiểm JVM, mà *"lệnh nào đi transport, lệnh nào đi mở app"* lại đúng
 * là thứ phải khoá ([ĐO] `emulator-voice-e2e-2026-09-15.md` §3 L2). Tách bốn hàm ra thì bài kiểm cấp một cầu giả
 * được, còn đường thật vẫn là **một** ([MediaBridge]) — không có cơ chế thứ hai nào sinh ra ở đây.
 */
interface MediaTransport {
    fun play(): Boolean
    fun pause(): Boolean
    fun next(): Boolean
    fun prev(): Boolean
}

/** Trừu tượng 1 phiên nhạc — để [MediaBridge.pick]/[MediaBridge.toSnapshot] test được off-car (controller giả). */
interface MediaLike {
    fun title(): String?
    fun artist(): String?
    fun albumArt(): Bitmap?
    fun positionMs(): Long
    fun durationMs(): Long
    fun playing(): Boolean
}

/**
 * CẦU NHẠC (W1d) — đọc phiên nhạc đang phát qua [MediaSessionManager.getActiveSessions] (dùng CHÍNH
 * NotificationListener của app [NavNotificationListener] làm component đã-được-cấp-quyền) → [MediaSnapshot] +
 * transport (play/pause/next/prev). Feed widget nhạc `w_media`.
 *
 * Degrade-safe (R9): thiếu quyền notification-listener / off-car → [read] trả null ⇒ widget "—"; transport no-op.
 * KHÔNG throw ra ngoài (mọi đường bọc runCatching).
 *
 * Test: [pick] (ưu tiên phiên đang phát) + [toSnapshot] (map field) là THUẦN, test bằng [MediaLike] giả.
 */
class MediaBridge(context: Context) : MediaTransport {

    private val app: Context = context.applicationContext

    /** Controller đang điều khiển (cập nhật ở [read]) — transport bám phiên này. */
    @Volatile private var active: MediaController? = null

    /** Đọc phiên nhạc đang hoạt động → snapshot (hoặc null nếu không có/không quyền). Cập nhật [active] cho transport. */
    fun read(): MediaSnapshot? {
        val controllers = activeControllers() ?: return null
        val chosen = pick(controllers.map { Real(it) })
        if (chosen == null) { active = null; return null }
        active = (chosen as Real).c
        return toSnapshot(chosen)
    }

    /**
     * Transport — trả `true` khi lệnh **thật sự** tới được một phiên nhạc.
     *
     * ## [SOÁT P1] Vì sao trả `Boolean` và vì sao [tx] tự `read()` khi chưa có phiên
     * [active] chỉ được đặt trong [read] (nhịp cập nhật của widget nhạc). Chỗ gọi nào cầm một [MediaBridge] **mới**
     * và bấm transport ngay — đúng hình dạng của [VoiceDispatcher] — sẽ bắn vào `null` và **không có gì xảy ra**,
     * cũng không có gì báo. Tự dò một lần ở [tx] sửa gốc cho **mọi** chỗ gọi thay vì bắt từng chỗ nhớ gọi [read]
     * trước; giá trị trả về cho phép chỗ gọi nói thật ("chưa có phiên nhạc nào") thay vì báo một dấu ✓ rỗng.
     */
    /**
     * Gói của phiên nhạc **đang** được điều khiển, hoặc `null` khi chưa thấy phiên nào.
     *
     * V1.1 — câu *"phát bài X"* (không nêu app) phải đi vào **app người ta đang nghe**, chứ không vào một app
     * mặc định nào đó: mở YouTube Music đè lên Spotify đang phát là hai luồng nhạc cùng lúc. Tự dò một lần nếu
     * chưa ai gọi [read] — cùng lý do với [tx], xem KDoc ở đó.
     */
    fun activePackage(): String? = runCatching {
        if (active == null) read()
        active?.packageName
    }.getOrNull()

    override fun play(): Boolean = tx { it.play() }
    override fun pause(): Boolean = tx { it.pause() }
    override fun next(): Boolean = tx { it.skipToNext() }
    override fun prev(): Boolean = tx { it.skipToPrevious() }

    /**
     * Chuyển một **mã hành động** của widget nhạc (`w_media`) thành lệnh transport. Mã lạ ⇒ không làm gì.
     *
     * Bảng chuyển này trước đây nằm trong [KachiHomeActivity]; đưa về đây vì nó là **kiến thức của cầu nhạc**, không
     * phải của màn hình — và vì màn hình đã sát trần 500 dòng nên mọi thứ không thuộc về nó phải đi. Chỗ gọi giờ chỉ
     * còn `onMedia = media::handle`.
     */
    fun handle(action: String) {
        when (action) {
            "play" -> play()
            "pause" -> pause()
            "next" -> next()
            "prev" -> prev()
        }
    }

    /**
     * F3 (spec shortcuts-autostart R3.4/R3.5) — MỌI phiên đang hoạt động (gói + đang phát?). `null` = KHÔNG ĐỌC ĐƯỢC
     * (chưa có quyền nghe thông báo / lỗi) — khác [read], vốn trả `null` cả khi đơn giản là chưa có phiên nào: chuyến
     * lên xe phải phân biệt *"không ai đang phát"* với *"không biết"* (không biết ⇒ không đè, fail-safe).
     */
    fun sessions(): List<TripMusicPlan.Session>? =
        activeControllers()?.map { c -> TripMusicPlan.Session(c.packageName, Real(c).playing(), acceptsUri(c)) }

    /**
     * L4 · D3 — phiên NÓI là nhận `playFromUri` (bit `ACTION_PLAY_FROM_URI` trong `PlaybackState.actions`). [ĐO máy ảo 03/10
     * `e2e/e5`] YT Music 9.35.54: `actions=2600887` (0x27AFB7) — có bit 0x2000. Không trạng thái ⇒ `false` (không đoán).
     */
    private fun acceptsUri(c: MediaController): Boolean =
        runCatching { (c.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_URI != 0L }.getOrDefault(false)

    /** `play()` vào ĐÚNG phiên của [pkg] (không phải phiên [active] đang được widget theo). `false` = gói không có phiên. */
    fun playPackage(pkg: String): Boolean = onPackage(pkg) { it.play() }

    /**
     * `playFromUri` vào phiên của [pkg]. [url] phải đã qua `TripMusicPlan.safeWatchUrl` (bên gọi kiểm) — [ĐO máy ảo 02/10
     * `trip/tm3u-ytmusic.txt`] YT Music đổi đúng bài, 0 sự kiện cửa sổ; khác ý-định VIEW (che màn nhà, `tm3-ytmusic.txt`).
     */
    fun playFromUri(pkg: String, url: String): Boolean = onPackage(pkg) { it.playFromUri(Uri.parse(url), null) }

    private fun onPackage(pkg: String, block: (MediaController.TransportControls) -> Unit): Boolean = runCatching {
        val c = activeControllers()?.firstOrNull { it.packageName == pkg } ?: return false
        block(c.transportControls)
        true
    }.getOrDefault(false)

    private fun tx(block: (MediaController.TransportControls) -> Unit): Boolean = runCatching {
        if (active == null) read()          // chưa ai dò phiên lần nào (vd cầu giọng nói) — dò đúng một lần
        val controls = active?.transportControls ?: return false
        block(controls)
        true
    }.getOrDefault(false)

    private fun activeControllers(): List<MediaController>? = runCatching {
        val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return null
        msm.getActiveSessions(ComponentName(app, NavNotificationListener::class.java))
    }.getOrNull()

    /** Bọc [MediaController] thật thành [MediaLike] (mọi đọc bọc runCatching → null-safe khi metadata thiếu). */
    private class Real(val c: MediaController) : MediaLike {
        private fun meta(): MediaMetadata? = runCatching { c.metadata }.getOrNull()
        private fun state(): PlaybackState? = runCatching { c.playbackState }.getOrNull()
        override fun title() = runCatching { meta()?.getString(MediaMetadata.METADATA_KEY_TITLE) }.getOrNull()
        override fun artist() = runCatching {
            meta()?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta()?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        }.getOrNull()
        override fun albumArt(): Bitmap? = runCatching {
            meta()?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta()?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        }.getOrNull()
        override fun positionMs() = runCatching { state()?.position ?: 0L }.getOrDefault(0L)
        override fun durationMs() = runCatching { meta()?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L }.getOrDefault(0L)
        override fun playing() = runCatching { state()?.state == PlaybackState.STATE_PLAYING }.getOrDefault(false)
    }

    companion object {
        /** Chọn phiên: ưu tiên phiên ĐANG PHÁT; không có thì phiên đầu; rỗng → null. */
        fun pick(list: List<MediaLike>): MediaLike? = list.firstOrNull { it.playing() } ?: list.firstOrNull()

        /** Map [MediaLike] → [MediaSnapshot]. */
        fun toSnapshot(m: MediaLike): MediaSnapshot = MediaSnapshot(
            title = m.title(), artist = m.artist(), albumArt = m.albumArt(),
            positionMs = m.positionMs(), durationMs = m.durationMs(), playing = m.playing(),
        )
    }
}
