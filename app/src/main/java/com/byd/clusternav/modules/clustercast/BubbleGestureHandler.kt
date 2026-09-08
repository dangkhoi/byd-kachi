package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.os.Handler
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles all touch gestures for the single-icon floating bubble (R5 / #7).
 *
 * Disambiguates three gestures on the one icon:
 *  - **TAP** (finger down/up within touch slop, no long-press) → [onTap];
 *  - **LONG-PRESS** (held past the system long-press timeout without moving) → [onLongPress];
 *  - **DRAG** (moved past touch slop) → moves the window; suppresses tap AND long-press.
 *
 * Tap/long-press detection is delegated to a [GestureDetector] (the spec's requirement), while the
 * drag itself stays manual so the overlay window follows the finger. Also owns the tap-token gate
 * (only one cast/stop operation in flight) and the coalescing executor.
 */
internal class BubbleGestureHandler(
    private val context: Context,
    private val handler: Handler,
    private val onDragEnd: (x: Int, y: Int) -> Unit,
    private val onWake: () -> Unit,
    private val onTap: () -> Unit = {},
    private val onLongPress: () -> Unit = {},
) {
    /**
     * Tap token: only one cast/stop operation allowed at a time.
     * Set to true when a tap fires an action; cleared when the action completes.
     * Duplicate taps while the token is held are rejected (the caller shows a toast).
     */
    private val tapInFlight = AtomicBoolean(false)

    /** Single-thread executor replaces raw `Thread { }.start()` for tap coalescing. */
    private val tapExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "bubble-tap-exec").apply { isDaemon = true }
    }

    /** Acquire tap token. Returns true if acquired, false if already in-flight. */
    fun acquireTapToken(): Boolean = tapInFlight.compareAndSet(false, true)

    /** Release tap token after operation completes (call from action dispatcher). */
    fun releaseTapToken() {
        tapInFlight.set(false)
    }

    /** Whether a tap operation is currently in flight. */
    val isTapInFlight: Boolean get() = tapInFlight.get()

    /**
     * Submit a tap action on the coalescing executor. Only one action runs at a time.
     * Returns false if the token could not be acquired (duplicate tap rejected).
     */
    fun submitTapAction(name: String, action: () -> Unit): Boolean {
        if (!acquireTapToken()) return false
        tapExecutor.execute {
            try {
                action()
            } finally {
                releaseTapToken()
            }
        }
        return true
    }

    /**
     * Attach the gesture arbiter to EVERY touch surface in the view tree. The single-icon bubble is
     * one view with no children, so in practice this attaches once; the recursion is kept so a
     * future wrapper cannot silently lose drag on a child.
     */
    fun attachDragToEveryTouchSurface(
        view: View,
        root: View,
        layout: WindowManager.LayoutParams,
        manager: WindowManager,
        clampX: (Int, View?) -> Int,
        clampY: (Int, View?) -> Int,
    ) {
        attachDrag(view, root, layout, manager, clampX, clampY)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                attachDragToEveryTouchSurface(view.getChildAt(i), root, layout, manager, clampX, clampY)
            }
        }
    }

    /**
     * Single touch arbiter for one view: distinguishes tap, long-press and drag.
     *
     * Every event is fed to a [GestureDetector], which fires [onTap] (single tap up) and
     * [onLongPress] (held without moving). Movement past [ViewConfiguration.scaledTouchSlop] flips
     * `dragging`, which (a) moves the overlay window and (b) suppresses both tap and long-press —
     * the detector cancels its pending long-press once it sees the scroll, and the `!dragging`
     * guards close the exact-threshold race. All events are consumed (return true) so the View's own
     * click handling never double-fires.
     */
    private fun attachDrag(
        handle: View,
        root: View,
        layout: WindowManager.LayoutParams,
        manager: WindowManager,
        clampX: (Int, View?) -> Int,
        clampY: (Int, View?) -> Int,
    ) {
        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        var dragging = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop

        val detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    // A real tap only — a drag flips `dragging` before the finger lifts.
                    if (!dragging) onTap()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    // Long-press must never fire mid-drag. The detector already cancels a pending
                    // long-press once the finger scrolls past slop; this guards the threshold race.
                    if (!dragging) onLongPress()
                }
            },
        ).apply { setIsLongpressEnabled(true) }

        handle.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onWake()
                    downX = event.rawX
                    downY = event.rawY
                    originX = layout.x
                    originY = layout.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (!dragging && dx * dx + dy * dy <= slop * slop) return@setOnTouchListener true
                    dragging = true
                    layout.x = clampX(originX + dx, root)
                    layout.y = clampY(originY + dy, root)
                    runCatching { manager.updateViewLayout(root, layout) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) onDragEnd(layout.x, layout.y)
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    /** Shutdown executor. Call on service destroy. */
    fun shutdown() {
        tapInFlight.set(false)
        tapExecutor.shutdownNow()
    }
}
