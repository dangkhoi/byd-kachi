package com.byd.clusternav.system

import android.content.Context
import android.os.PowerManager

/**
 * 2.96 · R18 — MỘT chỗ hỏi "màn có đang tương tác không" cho các nhịp nền mới thưa lại khi standby
 * ([com.byd.clusternav.system.StandbyCadence]). `null` = không hỏi được ⇒ chỗ gọi coi như BẬT (hành vi cũ, fail-safe).
 * Một binder `PowerManager.isInteractive` — rẻ hơn mọi việc nó cho phép bỏ qua.
 */
object ScreenInteractive {
    fun read(ctx: Context): Boolean? = try {
        ctx.getSystemService(PowerManager::class.java)?.isInteractive
    } catch (e: RuntimeException) {
        null
    }
}
