package com.byd.clusternav.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.byd.clusternav.NavNotificationListener

/** Ảnh chụp phiên nhạc đang phát cho widget nhạc. Mọi field nullable/rỗng-an-toàn → widget "—" khi không có. */
data class MediaSnapshot(
    val title: String?,
    val artist: String?,
    val albumArt: Bitmap?,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
)

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
class MediaBridge(context: Context) {

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

    fun play() = tx { it.play() }
    fun pause() = tx { it.pause() }
    fun next() = tx { it.skipToNext() }
    fun prev() = tx { it.skipToPrevious() }

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

    private fun tx(block: (MediaController.TransportControls) -> Unit) {
        runCatching { active?.transportControls?.let(block) }
    }

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
