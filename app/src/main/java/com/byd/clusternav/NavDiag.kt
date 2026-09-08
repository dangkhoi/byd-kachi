package com.byd.clusternav

import android.app.Notification
import android.os.SystemClock

/**
 * INSTRUMENTATION lõi (chỉ để chẩn đoán, KHÔNG nằm trên đường render): ring-buffer mỗi lần noti dẫn đường
 * được xử lý + giữ ref Notification THÔ gần nhất. Dùng để chứng minh giả thuyết then chốt: "notification GMaps
 * vẫn cập nhật khi bị YouTube che" + đo NHỊP cập nhật thật (thô tới đâu) + cho module RemoteViews-introspection.
 *
 * KEEP/KILL: NavNotificationListener.handle() ghi 1 lần (NavDiag.record + NavDiag.lastRaw). Xoá = xoá 2 dòng đó
 * + file này + 3 module navtrace/navremoteviews. Không ảnh hưởng feed cụm.
 */
object NavDiag {
    data class Hit(val atMs: Long, val pkg: String, val title: String, val text: String, val sub: String, val big: String, val hasIcon: Boolean)

    private const val CAP = 48
    private val ring = ArrayDeque<Hit>(CAP)

    @Volatile var lastRaw: Notification? = null     // Notification THÔ gần nhất (cho RemoteViews-introspection)
    @Volatile var lastRawPkg: String = ""

    @Synchronized
    fun record(pkg: String, title: String, text: String, sub: String, big: String, hasIcon: Boolean) {
        if (ring.size >= CAP) ring.removeFirst()
        ring.addLast(Hit(SystemClock.elapsedRealtime(), pkg, title, text, sub, big, hasIcon))
    }

    /** Snapshot mới→cũ (cho UI/autotest). */
    @Synchronized
    fun snapshot(): List<Hit> = ring.toList().asReversed()

    /** Nhịp cập nhật gần đây: (số mẫu trong cửa sổ, khoảng cách trung bình ms). Để biết noti thô/dày tới đâu. */
    @Synchronized
    fun cadence(windowMs: Long = 60000L): Pair<Int, Long> {
        val now = SystemClock.elapsedRealtime()
        val recent = ring.filter { now - it.atMs <= windowMs }
        if (recent.size < 2) return recent.size to -1L
        val span = recent.last().atMs - recent.first().atMs
        return recent.size to (span / (recent.size - 1))
    }

    fun sinceLastMs(): Long {
        val last = synchronized(this) { ring.lastOrNull()?.atMs } ?: return -1L
        return SystemClock.elapsedRealtime() - last
    }

    @Synchronized
    fun clear() { ring.clear(); lastRaw = null; lastRawPkg = "" }
}
