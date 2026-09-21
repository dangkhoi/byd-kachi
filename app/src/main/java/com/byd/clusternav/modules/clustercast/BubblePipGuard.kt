package com.byd.clusternav.modules.clustercast

import android.util.Log
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator

/**
 * ═══ Chặn PiP của GMaps/YouTube trong lúc dịch vụ nổi chạy — tách khỏi [FloatingBubbleService] ═══════════════════
 *
 * **Vì sao ở tệp riêng (WP6, 2026-09-20).** KDoc của [FloatingBubbleService] tự khai nó *"chỉ sở hữu: vòng đời dịch
 * vụ, quản cửa sổ, nối cử chỉ→hành động, bộ nghe trạng thái"* — mà nó lại đang giữ thêm cả khối này; tệp cũng đã
 * **537 dòng > trần 500** (CLAUDE.md §4.1) trước lượt này. Khối PiP là chỗ cắt an toàn nhất: nó không đọc một
 * trường nào của dịch vụ, chỉ cần một [SimpleCastCoordinator] để bắn lệnh shell. Lượt tách này **không sửa một
 * bước nào** — cùng lệnh, cùng thứ tự, cùng cách bọc lỗi.
 *
 * ## Bệnh nó chữa
 * GMaps/YouTube tự vào Picture-in-Picture làm bộ dò *"app nào đang ở tiền cảnh"* của nút nổi đọc sai. Chặn bằng
 * `appops … PICTURE_IN_PICTURE deny` trong lúc dịch vụ sống, và **trả lại đúng chế độ cũ** khi dịch vụ chết.
 *
 * ⚠⚠ Hai tính chất KHÔNG được làm mất khi sửa tệp này (đều là [P2] của lượt soát ngoài 2026-09-16 —
 * `OcrReviewContractTest` canh):
 *  1. [previousModes] là **`ConcurrentHashMap`**, không phải `mutableMapOf`. Luồng `"pip-block"` GHI trong khi
 *     luồng `"pip-restore"` DUYỆT + XOÁ — hai luồng rời nhau, không khoá. `ConcurrentModificationException` sẽ giết
 *     luồng trả-lại GIỮA CHỪNG ⇒ `deny` nằm lại **VĨNH VIỄN** (state ngoài tiến trình, sống qua cả reboot —
 *     CLAUDE.md §5: mỗi thứ đổi ra ngoài phải có đường trả lại chạy được).
 *  2. [restore] lấy danh sách ra bằng `remove` **nguyên tử trên luồng gọi** rồi mới phát lệnh ở luồng nền ⇒ không
 *     duyệt bản đồ dùng chung trong lúc luồng kia còn ghi · mỗi gói trả lại đúng MỘT lần · một lệnh hỏng ở gói này
 *     không nuốt luôn gói sau (trước đây một ngoại lệ ở gói đầu bỏ mặc gói còn lại ở `deny`).
 */
internal class BubblePipGuard {

    private val previousModes = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Ghi nhớ chế độ hiện tại rồi `deny` PiP cho các gói đã biết, trên luồng nền (lệnh shell). Cũng dẹp luôn cửa
     * sổ PiP đang mở nếu có.
     */
    fun block(coordinator: SimpleCastCoordinator) {
        Thread({
            PIP_BLOCK_PACKAGES.forEach { pkg ->
                val prev = queryMode(coordinator, pkg)
                if (prev != null && prev != "deny") {
                    previousModes[pkg] = prev
                    coordinator.executeShell("appops set $pkg PICTURE_IN_PICTURE deny")
                    Log.i(TAG, "blocked PiP for $pkg (was: $prev)")
                }
            }
            runCatching { coordinator.dismissPipOnDisplay(0) }
        }, "pip-block").start()
    }

    /** Trả lại chế độ cũ — xem tính chất (2) ở KDoc lớp: lấy ra nguyên tử TRƯỚC, phát lệnh ở luồng nền SAU. */
    fun restore(coordinator: SimpleCastCoordinator) {
        val pending = PIP_BLOCK_PACKAGES.mapNotNull { pkg -> previousModes.remove(pkg)?.let { pkg to it } }
        if (pending.isEmpty()) return
        Thread({
            pending.forEach { (pkg, mode) ->
                runCatching { coordinator.executeShell("appops set $pkg PICTURE_IN_PICTURE $mode") }
                    .onFailure { Log.w(TAG, "restore PiP for $pkg failed", it) }
                Log.i(TAG, "restored PiP for $pkg → $mode")
            }
        }, "pip-restore").start()
    }

    private fun queryMode(coordinator: SimpleCastCoordinator, pkg: String): String? {
        val result = coordinator.executeShell("appops get $pkg PICTURE_IN_PICTURE")
        if (!result.success) return null
        // Output: "PICTURE_IN_PICTURE: allow" hoặc "No operations."
        val match = Regex("PICTURE_IN_PICTURE:\\s*(\\w+)").find(result.stdout)
        return match?.groupValues?.get(1) ?: "allow" // không đặt bao giờ ⇒ mặc định của nền tảng là allow
    }

    private companion object {
        const val TAG = "ClusterCastBubble"
        val PIP_BLOCK_PACKAGES = listOf(
            "com.google.android.apps.maps",
            "app.revanced.android.apps.maps",
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "app.revanced.android.apps.youtube",
        )
    }
}
