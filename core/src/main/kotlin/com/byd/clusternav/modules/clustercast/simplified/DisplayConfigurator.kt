package com.byd.clusternav.modules.clustercast.simplified

/**
 * Configures the cluster display's wm size and overscan per app-type.
 *
 * Tracks current applied config to avoid redundant shell commands.
 * Field-proven values measured on vehicle 2026-08-02.
 */
class DisplayConfigurator(
    private val shell: SimpleCastShell,
    private val log: (String) -> Unit = { println("[DisplayConfigurator] $it") },
) {
    @Volatile
    var currentConfig: DisplayConfig? = null
        private set

    /**
     * Applies display config for the given app type.
     * Skips if the same config is already applied.
     *
     * @return true on success, false on shell failure.
     */
    fun apply(displayId: Int, config: DisplayConfig): Boolean {
        if (config == currentConfig) return true
        // V-CLUSTER · VC-R4 — CHỐT CUỐI ngay trước nội suy: ba chuỗi dưới đây đi NGUYÊN VĂN vào shell, và từ V-CLUSTER
        // chúng có thể đến từ một tệp hồ sơ người khác gửi. Lượt đọc đã kiểm (lớp 3), nhưng chốt ở TẦNG THI HÀNH mới là
        // chốt không đường vòng (CLAUDE.md §5): cấu hình không sạch ⇒ từ chối cả lượt, 0 lệnh.
        if (!CastGeometryGuard.isShellSafe(config)) {
            log("TỪ CHỐI cấu hình không sạch trước shell: size='${config.wmSize.take(24)}' — 0 lệnh wm")
            return false
        }

        releasedOn = -1   // 3.1 · R26: sắp ép hình học lại (kể cả hỏng giữa chừng) ⇒ lượt gỡ kế tiếp phải chạy thật
        // Set wm size
        val sizeResult = shell.execute("wm size ${config.wmSize} -d $displayId")
        if (!sizeResult.success) return false

        // Set overscan
        val overscanResult = shell.execute("wm overscan ${config.overscan} -d $displayId")
        if (!overscanResult.success) return false

        // Set density (reset or specific value)
        if (config.density != "reset") {
            val densityResult = shell.execute("wm density ${config.density} -d $displayId")
            if (!densityResult.success) return false
        } else {
            val densityResult = shell.execute("wm density reset -d $displayId")
            if (!densityResult.success) return false
        }

        currentConfig = config
        return true
    }

    /**
     * 3.1 · R6 — đổi RIÊNG overscan của [displayId] (bước "Dịch nội dung"). Đi qua đây — không gọi `wm overscan` trần — để
     * [currentConfig] luôn nói đúng thứ đang trên cụm: nếu không, lượt [apply] kế tiếp cùng cấu hình sẽ "bỏ qua vì trùng" và
     * độ dịch của app trước sống sang app sau. Chốt sạch ở tầng thi hành như [apply]; `displayId < 1` ⇒ từ chối (CLAUDE.md §4).
     */
    fun applyOverscan(displayId: Int, overscan: String): Boolean {
        if (displayId < 1 || CastGeometryGuard.overscan(overscan) != overscan) {
            log("TỪ CHỐI overscan '${overscan.take(24)}' -d $displayId — 0 lệnh wm")
            return false
        }
        if (!shell.execute("wm overscan $overscan -d $displayId").success) return false
        currentConfig = currentConfig?.copy(overscan = overscan)
        return true
    }

    /**
     * Resets all display settings to default.
     */
    fun reset(displayId: Int): Boolean {
        val r1 = shell.execute("wm size reset -d $displayId")
        val r2 = shell.execute("wm overscan reset -d $displayId")
        val r3 = shell.execute("wm density reset -d $displayId")
        currentConfig = null
        releasedOn = -1
        return r1.success && r2.success && r3.success
    }

    /** Display mà [releaseGeometry] đã gỡ ép hình học từ sau lượt [apply]/[reset] gần nhất (`-1` = chưa). Chỉ để bớt lệnh lặp. */
    @Volatile
    private var releasedOn: Int = -1

    /**
     * 3.1 · R26 — luật V1 `CastShell.resetDisplayGeometry` (khôi phục): task đã tự quản khung (`am task resize` ăn = freeform
     * sống) ⇒ GỠ ép hình học mức display (`wm size reset` + `wm overscan reset`), **GIỮ DPI** người lái chọn.
     * [ĐO xe 13/10 `oncar-2026-10-13/07-after-reset.txt`]: đúng hai lệnh này trả Waze từ `parent=[0,0][1920,520]` về 720.
     *
     * Bốn câu CLAUDE.md §4: (1) display = [displayId] ≥ 1 do coordinator đưa (`liveDisplayId` đã xác minh) — `< 1` ⇒ 0 lệnh;
     * (2) app: chỉ gọi khi phiên là app THƯỜNG (CP/AA giữ `wm size`/overscan đã đo của chúng — chỗ gọi chặn); (3) loại: lệnh mức
     * display, không chạm stack/task; (4) hoàn tác: trạng thái đích CHÍNH là mặc định của màn — không có gì phải trả; lượt
     * [apply] kế tiếp (chiếu app khác/CP) đặt lại cấu hình của nó vì [currentConfig] = `null`.
     *
     * Lặp lại trên cùng display mà chưa có [apply]/[reset] nào xen giữa ⇒ 0 lệnh (mỗi cú −/+ không tốn thêm 2 lệnh).
     * [applyOverscan] ("Dịch nội dung") KHÔNG xoá dấu này: nó chỉ đổi TOP, không ép lại hình học.
     */
    fun releaseGeometry(displayId: Int): Boolean {
        if (displayId < 1) { log("releaseGeometry: TỪ CHỐI display $displayId — 0 lệnh"); return false }
        if (releasedOn == displayId) return true
        val size = shell.execute("wm size reset -d $displayId")
        // A11+ đã gỡ `wm overscan` (V1: `hasOverscan`) — hỏng ở đây không phải lỗi của lượt gỡ.
        val overscan = shell.execute("wm overscan reset -d $displayId")
        currentConfig = null
        if (size.success) releasedOn = displayId
        log("gỡ ép hình học display $displayId (task tự quản khung, giữ DPI): size ${size.success} · overscan ${overscan.success}")
        return size.success
    }

    // Review 2.89 Pass 2 · cluster-r1-2: `resolveConfig` (đọc thẳng khoá Bo tròn, không biết kiểu khung của phiên) đã gỡ — lượt chia
    // đôi đọc qua `slotDisplayConfig` (`CastSessionPin.kt`), lượt FULL qua `pinFull`. Không để hai đường đọc song song.
}
