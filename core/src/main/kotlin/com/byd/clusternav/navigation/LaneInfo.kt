package com.byd.clusternav.navigation

/**
 * Một LÀN trong dải lane-guidance (T1, spec `b3-full-nav-capture`). [arrows] = các hướng đi được từ làn này
 * (1 làn có thể vừa thẳng vừa rẽ phải → nhiều phần tử); [recommended] = làn NÊN đi theo lộ trình (vẽ SÁNG),
 * false = làn MỜ (không theo lộ trình). arrows rỗng = làn không rõ hướng.
 */
data class Lane(val arrows: List<Maneuver>, val recommended: Boolean)

/**
 * Dải lane-guidance đã đọc từ nav app (Waze/VietMap) — danh sách làn theo thứ tự TRÁI → PHẢI.
 *
 * VD owner: 4 làn khi bản đồ bảo ĐI THẲNG ⇒
 * `LaneInfo([Lane([TURN_LEFT], false), Lane([STRAIGHT], true), Lane([STRAIGHT], true), Lane([TURN_RIGHT], false)])`
 * → overlay vẽ 4 mũi tên, làn 2+3 sáng (recommended), làn 1+4 mờ.
 *
 * Rỗng = không có lane-guidance (không vẽ). THUẦN (không Android) → test off-car.
 */
data class LaneInfo(val lanes: List<Lane>) {
    fun isEmpty(): Boolean = lanes.isEmpty()
    val count: Int get() = lanes.size

    companion object {
        val EMPTY = LaneInfo(emptyList())
    }
}
