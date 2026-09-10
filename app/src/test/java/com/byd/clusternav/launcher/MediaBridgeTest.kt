package com.byd.clusternav.launcher

import android.graphics.Bitmap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** W1d: [MediaBridge.pick]/[toSnapshot] thuần — chọn phiên đang phát, map field. Controller giả (không Android). */
class MediaBridgeTest {

    private class FakeMedia(
        private val t: String?,
        private val a: String?,
        private val playing: Boolean,
        private val pos: Long = 0,
        private val dur: Long = 0,
    ) : MediaLike {
        override fun title() = t
        override fun artist() = a
        override fun albumArt(): Bitmap? = null
        override fun positionMs() = pos
        override fun durationMs() = dur
        override fun playing() = playing
    }

    @Test fun `pick prefers the playing session`() {
        val list = listOf(FakeMedia("A", "x", false), FakeMedia("B", "y", true))
        assertEquals("B", MediaBridge.pick(list)?.title())
    }

    @Test fun `pick falls back to first when none playing`() {
        val list = listOf(FakeMedia("A", "x", false), FakeMedia("B", "y", false))
        assertEquals("A", MediaBridge.pick(list)?.title())
    }

    @Test fun `pick returns null on empty`() {
        assertNull(MediaBridge.pick(emptyList()))
    }

    @Test fun `toSnapshot maps every field`() {
        val snap = MediaBridge.toSnapshot(FakeMedia("Chạy ngay đi", "Sơn Tùng M-TP", true, 1000L, 3000L))
        assertEquals("Chạy ngay đi", snap.title)
        assertEquals("Sơn Tùng M-TP", snap.artist)
        assertEquals(1000L, snap.positionMs)
        assertEquals(3000L, snap.durationMs)
        assertTrue(snap.playing)
        assertNull(snap.albumArt)
    }
}
