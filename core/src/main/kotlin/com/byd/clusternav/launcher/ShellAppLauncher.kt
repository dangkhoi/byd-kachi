package com.byd.clusternav.launcher

/**
 * Adapter [AppLauncher] cho XE — mở app THẬT dạng cửa sổ freeform vào đúng ô, tái dùng công thức cast đã proven
 * ([FreeformLaunch] = `am ... --windowingMode 5` + `am task resize`). `sh` chạy 1 lệnh qua dadb uid-shell (như
 * các module ClusterNav). Emulator KHÔNG có dadb loopback → HOME dùng [NoCar]; ON-CAR wire `ShellAppLauncher(dadbShell)`.
 *
 * Thuần JVM (không android.*) → nằm ở :core; lõi lệnh test off-car ở [FreeformLaunchTest]. Đặt cửa sổ thật verify trên xe.
 */
class ShellAppLauncher(
    private val sh: (String) -> String,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : AppLauncher {

    override fun isFreeformAvailable(): Boolean =
        sh("settings get global enable_freeform_support").trim() == "1"

    override fun openInSlot(pkg: String, slot: SlotRect): Boolean {
        val comp = FreeformLaunch.parseComponent(sh(FreeformLaunch.resolveCmd(pkg))) ?: return false
        sh(FreeformLaunch.launchCmd(comp))
        // Poll tới khi task bám display chính (thay sleep(900) mù — app nặng khởi động chậm hơn ⇒ resize trượt ⇒ kẹt fullscreen).
        // Ưu tiên task Ở display chính (tránh resize nhầm task cùng gói trên display khác — bài học AppMover 2026-08-04).
        var taskId: Int? = null
        for (i in 0 until POLL_TRIES) {
            sleep(POLL_STEP_MS)
            val stack = sh("am stack list")
            taskId = FreeformLaunch.parseTaskIdOnDisplay(stack, pkg, FreeformLaunch.MAIN_DISPLAY)
                ?: FreeformLaunch.parseTaskId(stack, pkg)
            if (taskId != null) break
        }
        val id = taskId ?: return false
        var out = sh(FreeformLaunch.resizeCmd(id, slot))
        if (rejected(out)) { sleep(300); out = sh(FreeformLaunch.resizeCmd(id, slot)) }  // task chưa sẵn sàng → thử lại 1 lần
        return !rejected(out)
    }

    /**
     * Đặt LẠI khung cho app ĐANG chạy freeform — CHỈ `am task resize` (không relaunch → không flash/cướp focus,
     * nhanh hơn nhiều). Nếu chưa có task / bị từ chối (đang fullscreen) → [openInSlot] (mở lại freeform rồi resize).
     */
    override fun moveToSlot(pkg: String, slot: SlotRect): Boolean {
        val stack = sh("am stack list")
        val taskId = FreeformLaunch.parseTaskIdOnDisplay(stack, pkg, FreeformLaunch.MAIN_DISPLAY)
            ?: FreeformLaunch.parseTaskId(stack, pkg)
        if (taskId != null && !rejected(sh(FreeformLaunch.resizeCmd(taskId, slot)))) return true
        return openInSlot(pkg, slot)
    }

    private fun rejected(out: String): Boolean =
        out.contains("Error", true) || out.contains("Exception", true) || out.contains("not allowed", true)

    private companion object { const val POLL_TRIES = 12; const val POLL_STEP_MS = 250L }

    override fun closeSlot(pkg: String) {
        val comp = FreeformLaunch.parseComponent(sh(FreeformLaunch.resolveCmd(pkg))) ?: return
        sh(FreeformLaunch.fullscreenCmd(comp))
    }
}
