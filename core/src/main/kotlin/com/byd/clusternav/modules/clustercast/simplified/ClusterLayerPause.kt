package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.StackEntry

/**
 * ═══ 2.90 · R9 — "DỌN CỤM / TRẢ CỤM" cho lượt đổi theme (thuần, không chạy lệnh) ═════════════════════════════════════════
 *
 * Owner duyệt 06/10 (spec `kachi-290-cluster-rect-fix.html` §4.4): trước opcode theme, gỡ các lớp phủ CÓ THỂ gỡ được trên màn ảo
 * cụm — lớp của chính Kachi (badge tốc độ, trong tiến trình) và bóng nổi của bản mod VietMap (lệnh `VM_BUBBLE_VIS show=false`, mod
 * tự `removeView`) — rồi đọc lại. Luật gửi theme KHÔNG đổi ([ClusterThemePlan]: chỉ gửi khi 0 task + 0 cửa sổ). TRẢ luôn chạy
 * (cả khi hỏng/bỏ): gắn lại lớp của Kachi trên id cụm MỚI, `show` = true trừ khi người lái ĐÃ CHỦ ĐỘNG ẩn bóng (`vm_bubble_hidden`, 2.90).
 *
 * Vì sao lớp của Kachi tính là "cửa sổ lạ": [ĐO nguồn A10 r47] `WindowState.getWindowTag()` (`WindowState.java:3629-3635`) — tiêu
 * đề rỗng ⇒ `mAttrs.packageName`; `toString()` (`:3638-3646`) in `Window{… u0 <tag>}` ⇒ badge (không đặt tiêu đề) hiện là gói trần
 * của Kachi — [ClusterThemePlan.isPlaceholderComp] không nhận (không có `/`) ⇒ FOREIGN.
 *
 * Generic (CLAUDE.md §7): không tên gói nào ở đây — gói của Kachi là tham số, app bóng nổi tra [ClusterBubbleApps].
 */
object ClusterLayerPause {

    /** Lượt dọn được phép: [ownWindows] cửa sổ phủ của Kachi + nhãn app bóng nổi ([bubbleApps]) đang trên màn ảo cụm. */
    data class Pause(val ownWindows: Int, val bubbleApps: List<String>)

    /**
     * Có DỌN được không: CHỈ khi 0 task lạ (placeholder ClusterBlack không tính — nhánh gỡ trong tiến trình có sẵn lo) và MỌI cửa sổ
     * lạ trên [vds] hoặc là cửa sổ phủ của [selfPkg] (tên = gói trần) hoặc của app bóng nổi đã biết. Không có gì để dọn, đọc hỏng,
     * hay có thứ khác ⇒ `null` (luật cũ của [ClusterThemePlan] quyết — FOREIGN, không gửi).
     */
    fun pausable(tasks: List<StackEntry>?, windows: List<ClusterThemePlan.WindowOnDisplay>?, vds: Set<Int>, selfPkg: String): Pause? {
        if (tasks == null || windows == null || vds.isEmpty() || selfPkg.isBlank()) return null
        if (tasks.any { it.displayId in vds && !ClusterThemePlan.isPlaceholderComp(it.comp, selfPkg) }) return null
        val foreign = windows.filter { it.displayId in vds && !ClusterThemePlan.isPlaceholderComp(it.name, selfPkg) }
        if (foreign.isEmpty()) return null
        var own = 0
        val apps = LinkedHashSet<String>()
        for (w in foreign) {
            if (w.name == selfPkg) { own++; continue }
            apps += w.name.takeIf { '/' !in it }?.let(ClusterBubbleApps::labelOf) ?: return null
        }
        return Pause(own, apps.toList())
    }

    /** Bản đọc lại sau DỌN: trên [vds] không còn task / cửa sổ nào ngoài placeholder ClusterBlack của [selfPkg]. */
    fun cleared(tasks: List<StackEntry>, windows: List<ClusterThemePlan.WindowOnDisplay>, vds: Set<Int>, selfPkg: String): Boolean =
        tasks.none { it.displayId in vds && !ClusterThemePlan.isPlaceholderComp(it.comp, selfPkg) } &&
            windows.none { it.displayId in vds && !ClusterThemePlan.isPlaceholderComp(it.name, selfPkg) }

    /** Quyết định SAU lượt dọn vẫn là [ClusterThemePlan.Reason.BUBBLE] ⇒ bản mod không làm theo `VM_BUBBLE_VIS` (mod cũ). */
    fun oldMod(after: ClusterThemePlan.Decision): Boolean =
        after is ClusterThemePlan.Decision.Skip && after.reason == ClusterThemePlan.Reason.BUBBLE

    /**
     * TRẢ: [reattachOn] = id cụm sống để gắn lại lớp của Kachi (`-1` = chưa dò được ⇒ lớp tự chọn display, không bao giờ hằng);
     * [bubbleShow] = giá trị `show` cần gửi (`null` = app bóng nổi không cài ⇒ không gửi) — `true` trừ khi [hiddenByUser].
     */
    data class Resume(val reattachOn: Int, val bubbleShow: Boolean?)

    fun resume(liveId: Int, hiddenByUser: Boolean, installed: Boolean): Resume =
        Resume(if (liveId >= 1) liveId else -1, if (installed) !hiddenByUser else null)

    /**
     * Giá trị `show` cho mọi lượt gửi ngoài DỌN/TRẢ (đổi công tắc, nhịp làm tươi, sau khi tự mở VietMap): ẩn khi đang dọn.
     *
     * An toàn hiện trường (review 2.90): [hiddenByUser] là cờ `vm_bubble_hidden` (mặc định false) — KHÔNG phải công tắc
     * `vm_bubble_enabled` (mặc định false, nghĩa cũ = tự mở VietMap). Trước 2.90 bản mod LUÔN hiện bóng; người chưa từng bật
     * công tắc mà nâng cấp lên 2.90 + mod mới phải vẫn thấy bóng ⇒ chỉ ẩn khi người lái chủ động TẮT ở 2.90+.
     */
    fun bubbleWanted(hiddenByUser: Boolean, paused: Boolean): Boolean = !hiddenByUser && !paused
}

/**
 * 2.90 · R9 — bộ thi hành DỌN/TRẢ do `:app` cấp ([ClusterThemeGuard] gọi trên executor của coordinator). Không được ném. Mặc định
 * [NONE] (JVM/test, hoặc khi `:app` không nối) = không có lớp nào để gỡ, không có app bóng nổi ⇒ hành vi trước Pass 3.
 */
interface ClusterLayerPort {
    /** Gỡ lớp phủ của Kachi khỏi màn ảo cụm (trong tiến trình) và CHẶN gắn lại tới [resumeOwn]. */
    fun pauseOwn()

    /** Bỏ chặn, gắn lại lớp của Kachi — trên [clusterId] (≥ 1) hoặc tự chọn display khi `-1`. */
    fun resumeOwn(clusterId: Int)

    /** App bóng nổi (bản mod VietMap) có cài không. */
    fun bubbleInstalled(): Boolean

    /** Người lái đã CHỦ ĐỘNG tắt "Hiện bong bóng VietMap trên cụm" (2.90+, cờ `vm_bubble_hidden`). */
    fun bubbleHiddenByUser(): Boolean

    /** Gửi `VM_BUBBLE_VIS` với [show]. */
    fun sendBubble(show: Boolean)

    /**
     * 2.93 wave 2A · VM-BUBBLE-OLDMOD-MEMO — dấu cài đặt của app bóng nổi ([BubbleOldModMemo.token]: mã phiên bản + lần cài);
     * `null` = không cài / không đọc được ⇒ không dùng sổ. Mặc định `null` (JVM/test cũ: hành vi 2.90).
     */
    fun bubbleInstallToken(): String? = null

    /** Sổ "mod cũ đã chứng minh" thô ([BubbleOldModMemo.KEY], phạm vi XE); `null` = chưa có. */
    fun oldModMemo(): String? = null

    /** Ghi sổ ([value] `null` = xoá). `true` = đã chạm đĩa. Mặc định không có nơi lưu ⇒ `false`. */
    fun writeOldModMemo(value: String?): Boolean = false

    companion object {
        val NONE: ClusterLayerPort = object : ClusterLayerPort {
            override fun pauseOwn() = Unit
            override fun resumeOwn(clusterId: Int) = Unit
            override fun bubbleInstalled() = false
            override fun bubbleHiddenByUser() = false
            override fun sendBubble(show: Boolean) = Unit
        }
    }
}
