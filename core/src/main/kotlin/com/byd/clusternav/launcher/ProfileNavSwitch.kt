package com.byd.clusternav.launcher

/**
 * 2.97 · R3 (spec `docs/specs/kachi-297-plan.html`) — đổi hồ sơ làm công tắc "Dẫn đường lên cụm đồng hồ" đổi theo (công tắc nằm
 * trong phạm vi hồ sơ, `ProfileScopeCluster`). [ĐO log SL6 07/10 16:51] đổi sang hồ sơ có công tắc TẮT ⇒ HUD/cụm mất dẫn đường
 * mà người lái không biết vì sao. Luật thuần: so trước/sau, ra dòng nhật ký (luôn) và quyết có nhắc người lái không (chỉ khi
 * BẬT → TẮT — bật lên thì người lái thấy ngay, không cần nhắc).
 */
object ProfileNavSwitch {

    enum class Change { SAME, TURNED_ON, TURNED_OFF }

    fun change(before: Boolean, after: Boolean): Change = when {
        before == after -> Change.SAME
        after -> Change.TURNED_ON
        else -> Change.TURNED_OFF
    }

    /** Nhắc người lái: chỉ khi lượt đổi hồ sơ TẮT dẫn đường đang bật. */
    fun shouldNotice(before: Boolean, after: Boolean): Boolean = change(before, after) == Change.TURNED_OFF

    /** Dòng nhật ký ASCII-an toàn (tên hồ sơ là tên người dùng đặt — đã có trong log `KachiProfile`). */
    fun logLine(profile: String, before: Boolean, after: Boolean): String =
        "doi ho so «$profile»: dan duong len cum ${onOff(before)} -> ${onOff(after)} (${change(before, after)})"

    private fun onOff(v: Boolean) = if (v) "BAT" else "TAT"
}
