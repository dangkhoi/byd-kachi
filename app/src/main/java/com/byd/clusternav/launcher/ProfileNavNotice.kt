package com.byd.clusternav.launcher

import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * 2.97 · R3 — cầu một-lần giữa lượt đổi hồ sơ (`PrefsWorkspaceRepository.switchProfile`, mọi lối: chip · giọng nói · cầu kiểm thử)
 * và màn chính (`KachiHomeActivity.render` hiện dòng nhắc). Cờ RAM CHỈ để hiển thị (CLAUDE.md §5) — quyết định nằm ở luật thuần
 * [ProfileNavSwitch]; mất cờ (tiến trình chết) thì chỉ mất một dòng nhắc, công tắc trên đĩa vẫn đúng.
 */
object ProfileNavNotice {
    private const val TAG = "KachiProfile"
    private val pending = AtomicReference<String?>(null)

    /** Ghi nhật ký trước/sau; BẬT → TẮT ⇒ giữ tên hồ sơ chờ màn chính nhắc. */
    fun record(profile: String, before: Boolean, after: Boolean) {
        Log.i(TAG, ProfileNavSwitch.logLine(profile, before, after))
        pending.set(if (ProfileNavSwitch.shouldNotice(before, after)) profile else null)
    }

    /** Lấy RA (một lần) tên hồ sơ cần nhắc; `null` = không có gì. */
    fun take(): String? = pending.getAndSet(null)
}
