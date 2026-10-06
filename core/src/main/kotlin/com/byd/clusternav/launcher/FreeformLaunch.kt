package com.byd.clusternav.launcher

/**
 * Bộ DỰNG LỆNH cửa sổ app của launcher (thuần JVM, test off-car). Tái dùng công thức ĐÃ PROVEN trên xe trong cluster-cast
 * ([com.byd.clusternav.modules.clustercast.simplified.AppMover] / CastGeometryController): phân giải component, `am task
 * resize` theo khung ([SlotRect]), đưa app về toàn màn, mở app lên màn ảo của ô.
 *
 * 2.93 · SLOT-DEAD-OPENSLOT (spec `kachi-293-wave2a.html` §4.4): `launchCmd` (mở freeform display chính) + `parseTaskId` (regex
 * toàn cục) GỠ cùng `ShellAppLauncher.openInSlot/moveToSlot` — 0 chỗ gọi sản phẩm. [parseTaskIdOnDisplay] GIỮ (bộ đo ô
 * `SlotLiveProbe` dùng). Các bộ dựng chạy qua dadb uid-shell (ON-CAR). KHÔNG import android.* → test JVM thuần.
 */
object FreeformLaunch {
    const val MAIN_DISPLAY = 0

    /** Đặt khung task về đúng ô [slot]. `am task resize` nhận left top right bottom — KHÔNG phải w/h. */
    fun resizeCmd(taskId: Int, slot: SlotRect): String =
        "am task resize $taskId ${slot.left} ${slot.top} ${slot.right} ${slot.bottom}"

    /** Resolve launcher component của [pkg]. */
    fun resolveCmd(pkg: String): String =
        "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg"

    /**
     * Component LAUNCHER của [pkg] qua kênh [sh] ([resolveCmd]: dòng CUỐI có `/` và tên gói) — `null` = không phân giải
     * được (kênh ném · gói không có activity LAUNCHER). Chuyển nguyên thân từ `VdAppHost.resolveComponent` (2.89-thử1, trần
     * 500 dòng của tệp đó), cùng chuỗi lệnh byte-khớp. CHẶN (dadb) — chỉ luồng nền.
     */
    fun resolveComponent(pkg: String, sh: (String) -> String): String? {
        val out = runCatching { sh(resolveCmd(pkg)) }.getOrDefault("")
        return out.trim().lines().lastOrNull { it.contains("/") && it.contains(pkg) }
    }

    /** App có tiến trình chưa — `pidof` rỗng ⇒ chưa lên (retry mở-lại trên cold boot, #12). Chuyển nguyên thân từ `VdAppHost`. */
    fun appRunning(pkg: String, sh: (String) -> String): Boolean =
        runCatching { sh("pidof $pkg").trim().isNotEmpty() }.getOrDefault(false)

    /**
     * force-stop [pkg] (process death). KHÔNG nhắm display nào. Byte-KHỚP chuỗi inline mà VdAppHost
     * (`am force-stop $p`) và KachiHomeActivity (`am force-stop $pkg`) đang dùng — nay tập trung tại đây để
     * B2 [com.byd.clusternav.system.WindowMutation.ForceStop] / B4 gọi lại một chuỗi DUY NHẤT (DRY).
     */
    fun forceStopCmd(pkg: String): String = "am force-stop $pkg"

    /** Đưa [component] về FULLSCREEN display chính (đóng ô) — công thức R6 cast (FLAG_ACTIVITY_SINGLE_TOP=0x20000000). */
    fun fullscreenCmd(component: String, displayId: Int = MAIN_DISPLAY): String =
        "am start --display $displayId --windowingMode 1 -f 0x20000000" +
            " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n '$component'"

    /**
     * `am start` mở [component] trên MỘT [displayId] cụ thể (thường là VirtualDisplay của ô, id ≥ 1) với
     * [windowingMode]. Tái tạo BYTE-CHÍNH-XÁC chuỗi mà [com.byd.clusternav.launcher.VdAppHost] /
     * [com.byd.clusternav.launcher.SlotAppHost] dựng inline TRƯỚC Stage B1 — nay chúng gọi builder này để golden
     * test khoá byte tại đây thay vì source-pin từng file.
     *   • [withLauncherCategory] = true  → thêm `-a android.intent.action.MAIN -c android.intent.category.LAUNCHER`
     *     (đường VdAppHost).
     *   • [withLauncherCategory] = false → bỏ (đường SlotAppHost).
     * ⚠ [displayId] ở đây là display ĐÍCH do host tạo (VirtualDisplay phụ để render app trong ô) — KHÔNG phải cụm.
     */
    fun launchOnDisplayCmd(
        component: String,
        displayId: Int,
        windowingMode: Int,
        withLauncherCategory: Boolean = true,
    ): String {
        val category = if (withLauncherCategory) " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER" else ""
        return "am start --display $displayId --windowingMode $windowingMode$category -n '$component'"
    }

    // ── Freeform boot flags MOVED to the single sanctioned writer (Stage B3) ──
    // The launcher's freeform need is served ONLY by [com.byd.clusternav.system.FreeformSeedPolicy] (SEED_CMDS +
    // 3-state marker discipline). Keeping a second copy of these command strings here would make FreeformLaunch a
    // rogue persistent-state writer — PersistentWindowStateWriterGuardTest asserts NO launcher file writes them.
    // Command strings are byte-identical to the former `freeformFlagCmds` constant (locked by FreeformSeedPolicyTest).

    /**
     * Lấy taskId của [pkg] NHƯNG chỉ trong stack thuộc [displayId] — mirror công thức PROVEN
     * [com.byd.clusternav.modules.clustercast.simplified.AppMover] `findTaskIdOnDisplay` (fix on-car
     * 2026-08-04: regex global từng khớp NHẦM task cùng gói trên display khác ⇒ resize sai cửa sổ).
     * Duyệt từng dòng: gặp "Stack id=… displayId=N" thì nhớ N; chỉ khớp taskId của [pkg] khi N == [displayId].
     * Trả null nếu không thấy trên display đó (định dạng stack khác — A12 `RootTask id=` — cũng ra null: [SUY], backlog DL5).
     */
    fun parseTaskIdOnDisplay(stackList: String, pkg: String, displayId: Int): Int? {
        val stackHeader = Regex("Stack id=\\d+.*displayId=(\\d+)")
        val taskLine = Regex("taskId=(\\d+):[^\\n]*" + Regex.escape(pkg))
        var current = -1
        for (line in stackList.lineSequence()) {
            val sm = stackHeader.find(line)
            if (sm != null) { current = sm.groupValues[1].toIntOrNull() ?: -1; continue }
            if (current == displayId) {
                taskLine.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    /** Component từ output [resolveCmd]: dòng cuối có '/' và không có khoảng trắng. */
    fun parseComponent(resolveOutput: String): String? =
        resolveOutput.lineSequence().map { it.trim() }.lastOrNull { it.contains("/") && !it.contains(" ") }
}
