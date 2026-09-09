package com.byd.clusternav.system

import android.content.Context

/** Kết quả của [WindowCommandDispatcher.dispatch]. */
sealed class DispatchResult {
    /** Đã validate ownership + đã gửi xuống transport. [output] = stdout của lệnh. */
    data class Dispatched(val output: String) : DispatchResult()

    /** BỊ CHẶN ở cổng ownership (cross-boundary / display không chủ). KHÔNG gửi xuống transport. */
    data class Rejected(val reason: String) : DispatchResult()
}

/**
 * Cổng DUY NHẤT để phát một [WindowMutation] xuống head unit CHO NHÁNH LAUNCHER — validate SỞ HỮU DISPLAY
 * ([DisplayOwnershipRegistry]) TRƯỚC khi dispatch, rồi cập nhật VỊ TRÍ APP ([AppLocationRegistry]).
 *
 * ── Vì sao (Stage B2b) ──────────────────────────────────────────────────────────────────────────────────────
 * Two-track split: launcher sở hữu display 0 + các VirtualDisplay của ô; cast sở hữu cụm (display 1). B2a đã
 * dựng policy THUẦN (:core). B2b wire nó vào runtime: MỌI lệnh cửa sổ của launcher đi qua đây → nếu nhắm display
 * mà launcher KHÔNG sở hữu (vd cụm) → [DispatchResult.Rejected] + log, KHÔNG chạm transport. Nhờ vậy về mặt CẤU
 * TRÚC không một op launcher nào chạm được display ≥ 1 (trừ VD của chính nó đã đăng ký).
 *
 * ── one connection ──────────────────────────────────────────────────────────────────────────────────────────
 * Dispatch qua [runCommand] = `ShellTransport.run(cmd, priority)` (owner thread duy nhất, B1). Priority lấy từ
 * [WindowMutation.priority]. Giữ process-singleton [ownership] + [locations] (B5 gộp vào AppContainer).
 *
 * :app (cần [Context]/[android.util.Log]); policy + registry là :core thuần. [runCommand] tách rời để test JVM
 * off-device được (không cần dadb).
 */
class WindowCommandDispatcher internal constructor(
    private val runCommand: (String, MutationPriority) -> String,
    val ownership: DisplayOwnershipRegistry = DisplayOwnershipRegistry(),
    val locations: AppLocationRegistry = AppLocationRegistry(),
    private val log: (String) -> Unit = {},
) {

    /**
     * Validate [mutation] do [issuer] phát rồi dispatch. REJECT (cross-boundary / display không chủ) → log +
     * [DispatchResult.Rejected], KHÔNG chạm transport. ALLOW → `runCommand(render, priority)` rồi cập nhật
     * [locations] theo loại mutation.
     */
    fun dispatch(mutation: WindowMutation, issuer: DisplayOwner): DispatchResult =
        when (val v = ownership.validate(mutation, issuer)) {
            is ValidationResult.Reject -> {
                log("REJECT $issuer ${mutation::class.simpleName} @display=${mutation.targetDisplayId}: ${v.reason}")
                DispatchResult.Rejected(v.reason)
            }
            ValidationResult.Allow -> {
                val output = runCommand(mutation.render(), mutation.priority)
                applyLocation(mutation)
                DispatchResult.Dispatched(output)
            }
        }

    /**
     * Seam `(String) -> String` cho các adapter launcher chạy chuỗi lệnh THÔ ([ShellAppLauncher] reflow,
     * [com.byd.clusternav.launcher.VdAppHost]). Mỗi lệnh được suy display đích từ cờ `--display N` (không có →
     * [WindowMutation.NO_DISPLAY]), bọc [WindowMutation.Raw], dispatch với issuer = [DisplayOwner.LAUNCHER].
     * Lệnh nhắm cụm/VD chưa-đăng-ký → REJECT → trả "" (KHÔNG chạy) = cổng an toàn cấu trúc. Byte của chuỗi lệnh
     * KHÔNG đổi (Raw.render trả nguyên chuỗi) → golden test giữ nguyên.
     */
    fun launcherSeam(): (String) -> String = { cmd ->
        when (val r = dispatch(WindowMutation.Raw(cmd, displayOf(cmd)), DisplayOwner.LAUNCHER)) {
            is DispatchResult.Dispatched -> r.output
            is DispatchResult.Rejected -> ""
        }
    }

    /** Đăng ký VirtualDisplay [id] của launcher (ô app) → thuộc [DisplayOwner.LAUNCHER]. */
    fun registerLauncherVirtualDisplay(id: Int) = ownership.registerVirtualDisplay(id)

    /** Gỡ đăng ký VirtualDisplay [id] (ô đóng / host release). */
    fun unregisterLauncherVirtualDisplay(id: Int) = ownership.unregisterVirtualDisplay(id)

    /** Đặt [pkg] vào display [displayId] (+ ô [slot] nếu ở màn launcher) — dùng ở biên openInSlot của launcher. */
    fun place(pkg: String, displayId: Int, slot: Int?) = locations.place(pkg, displayId, slot)

    /** Gỡ [pkg] khỏi mọi vị trí — dùng ở biên closeSlot/clearSlot của launcher. */
    fun remove(pkg: String) = locations.remove(pkg)

    /**
     * Cập nhật [locations] TỪ mutation đã dispatch (thô, KHÔNG có index ô): mở app trên display → [place]
     * (slot = null); fullscreen/force-stop → [remove]. Biên openInSlot của launcher biết index ô nên gọi
     * [place]/[remove] TRỰC TIẾP (chính xác hơn) — hai đường KHÔNG xung đột vì khác code-path.
     */
    private fun applyLocation(mutation: WindowMutation) {
        when (mutation) {
            is WindowMutation.LaunchOnDisplay -> pkgOf(mutation.component)?.let { locations.place(it, mutation.displayId, null) }
            is WindowMutation.Fullscreen -> pkgOf(mutation.component)?.let { locations.remove(it) }
            is WindowMutation.ForceStop -> locations.remove(mutation.pkg)
            is WindowMutation.ResizeTask -> Unit // chỉ có taskId, không suy được pkg
            is WindowMutation.Raw -> Unit // chuỗi thô — biên launcher tự gọi place/remove khi biết pkg + ô
        }
    }

    private fun pkgOf(component: String): String? =
        component.substringBefore('/').trim().ifEmpty { null }

    companion object {
        /** Cờ `--display N` trong một chuỗi lệnh `am`; không có → [WindowMutation.NO_DISPLAY]. */
        private val DISPLAY_FLAG = Regex("""--display\s+(\d+)""")

        private fun displayOf(cmd: String): Int =
            DISPLAY_FLAG.find(cmd)?.groupValues?.get(1)?.toIntOrNull() ?: WindowMutation.NO_DISPLAY

        /**
         * Dựng một dispatcher chạy trên [transport] cho [com.byd.clusternav.AppContainer] (chủ đồ thị DI). AppContainer
         * giữ DUY NHẤT một instance (lazy) → factory này chỉ được gọi một lần cho cả tiến trình.
         */
        internal fun createOwned(transport: ShellTransport): WindowCommandDispatcher =
            WindowCommandDispatcher(
                runCommand = { cmd, priority -> transport.run(cmd, priority) },
                log = { msg -> android.util.Log.i("Kachi/WinDispatch", msg) },
            )

        /**
         * Process-wide dispatcher — NAY UỶ QUYỀN về [com.byd.clusternav.AppContainer] (đồ thị DI, B5), chạy trên chủ
         * [ShellTransport] DUY NHẤT của container. Thread-safe. Caller cũ (FreeformSeed, KachiHomeActivity…) không đổi.
         */
        fun get(context: Context): WindowCommandDispatcher =
            com.byd.clusternav.AppContainer.get(context).windowDispatcher
    }
}
