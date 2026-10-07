package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.system.FreeformSeedPolicy

/**
 * Owns per-app freeform geometry: task lookup, resize (full + per-slot), profile
 * persistence and restore (R4/R5/R6), plus the read-only cluster-display stack queries
 * those decisions depend on (task-on-display, fullscreen-stack availability, freeform probe).
 *
 * Extracted from [SimpleCastCoordinator] on 2026-08-05 to keep that file ≤ 500 LOC
 * and to centralize the single hard rule: **persist geometry ONLY after a shell
 * `am task resize` (or `wm size` fallback) reports success.**
 *
 * Pure JVM — depends only on [SimpleCastShell] and [SimpleCastPrefs] (LayeringRulesTest Q1).
 */
internal class CastGeometryController(
    private val shell: SimpleCastShell,
    private val prefs: SimpleCastPrefs,
    // X2 — provider (KHÔNG phải Int cố định): id display cụm được dò động ở coordinator và có thể đổi trong
    // phiên, nên geometry phải đọc giá trị SỐNG mỗi lần dùng thay vì chụp lúc dựng.
    private val displayIdProvider: () -> Int,
    /**
     * 2.90 · R5 — dò lại id cụm LIVE (coordinator `detectClusterDisplay`) khi [queryDisplaySize] thấy display hiện tại đã mất
     * ([CastGeometryGuard.displayGone]); trả id mới hoặc `-1`. Mặc định: không dò (test cũ). Đứng TRƯỚC [log] để lambda cuối của
     * các chỗ gọi cũ vẫn là [log].
     */
    private val redetect: () -> Int = { -1 },
    /**
     * 2.96 · R13 (soát Pass 1 [P2]) — bộ đọc `Settings.Global` TRONG tiến trình theo khoá (`:app`: `FreeformSeedStore.readGlobal`,
     * 0 shell); `null` = đọc qua shell `settings get` (JVM/test). Đứng TRƯỚC [log] để lambda cuối của các chỗ gọi cũ vẫn là [log].
     */
    private val readGlobalSetting: ((String) -> String?)? = null,
    private val log: (String) -> Unit = { println("[CastGeometry] $it") },
) {
    private val displayId: Int get() = displayIdProvider()

    /** Find the taskId for [pkg], preferring the cluster display. Null if not found. */
    fun findTaskIdForPkg(pkg: String): String? {
        val result = shell.execute("am stack list")
        if (!result.success) return null
        // 2.90 · R5: tra theo id SỐNG của coordinator — không ưu tiên `prefs.lastDisplayId()` (giá trị bền, có thể của màn ảo đã
        // dựng lại / tiến trình trước — [ĐO xe 06/10] lượt đọc lại hỏi display 4 khi cụm đã là 9).
        return CastStackParser.findTaskId(result.stdout, pkg, displayId)
    }

    /**
     * Resize the full-screen [pkg]. Tier-1 `am task resize`; tier-2 `wm size` fallback
     * (V0.36 approach) when freeform resize is rejected.
     *
     * R6: persists to the [CastProfile.FULL] profile ONLY on shell success.
     * B1b: [style] = kiểu khung của PHIÊN — Chữ nhật lưu vào khoá `__RECT` ([CastProfile.inStyle]), Bo tròn = khoá cũ.
     * Pass 2 · cluster-r1-3: Chữ nhật KHÔNG có tầng 2 (`wm size` phá toạ độ 1:1) — chỉ `am task resize`.
     *
     * @return true khi một trong hai tầng áp được (V-CLUSTER · VC-R6: chỗ gọi cập nhật bản ghim của phiên CHỈ khi true).
     */
    fun resizeFull(pkg: String, left: Int, top: Int, right: Int, bottom: Int, style: CastStyle = CastStyle.CURVED): Boolean {
        val profile = CastProfile.FULL.inStyle(style)
        val taskId = findTaskIdForPkg(pkg) ?: return false
        val result = shell.execute("am task resize $taskId $left $top $right $bottom")
        if (result.success) {
            persistBounds(pkg, profile, left, top, right, bottom)
            return true
        }
        // Review 2.89 Pass 2 · cluster-r1-3: Chữ nhật cần toạ độ cụm 1:1 (khung vùng trống + hộp km/h) ⇒ KHÔNG đường lùi `wm size`
        // (cùng lẽ [resizeSlot]): `wm size` co cả VD, khung trượt dưới nền ADAS, và lưu `wmSize` vào khoá `__RECT` là mọi phiên Chữ
        // nhật sau của app mất km/h. Hỏng ⇒ không lưu gì, người lái thử lại sau khi freeform sống (sau một lần tắt/mở nguồn).
        if (style == CastStyle.RECT) {
            log("resizeActiveTarget (Chữ nhật): am task resize bị từ chối cho $pkg — KHÔNG lùi wm size, không lưu")
            return false
        }
        // Fallback: change logical display size, keeping height to avoid letterbox.
        val (physW, physH) = queryDisplaySize(preferOverride = false) ?: (1920 to 720)
        val scaleW = (right - left).coerceIn(320, physW)
        val scaleH = (bottom - top).coerceIn(240, physH)
        val sizeResult = shell.execute("wm size ${scaleW}x${scaleH} -d $displayId")
        if (sizeResult.success) {
            log("resizeActiveTarget FALLBACK wm size ${scaleW}x${scaleH} OK")
            val existing = prefs.displayConfigFor(pkg, profile) ?: DisplayConfig.NORMAL_DEFAULT
            prefs.saveDisplayConfig(
                pkg,
                profile,
                existing.copy(wmSize = "${scaleW}x${scaleH}", bounds = CastBounds(left, top, right, bottom)),
            )
            return true
        }
        log("resizeActiveTarget FAILED — both task resize and wm size failed")
        return false
    }

    /**
     * Resize a split slot's [pkg] via `am task resize` and persist to [profile] on success (R5/R6).
     *
     * No `wm size` fallback: split geometry needs per-task bounds (freeform), and `wm size`
     * is display-global — it cannot place two apps in two halves. If the resize is rejected,
     * freeform is not alive (needs a one-time power-cycle) and nothing is persisted.
     *
     * @return true khi `am task resize` thành công (chỗ gọi cập nhật bản ghim của phiên CHỈ khi true).
     */
    fun resizeSlot(pkg: String, profile: CastProfile, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val taskId = findTaskIdForPkg(pkg) ?: return false
        val result = shell.execute("am task resize $taskId $left $top $right $bottom")
        if (result.success) {
            persistBounds(pkg, profile, left, top, right, bottom)
            return true
        }
        log("resizeActiveSlot FAILED — am task resize rejected for $pkg ($profile); freeform likely not alive")
        return false
    }

    /**
     * Áp bản ghi hình học [pinned] ĐÃ GHIM của phiên cho [pkg] — sau một lượt đặt đã xác minh (R6) và ở mọi lượt repin.
     *
     * V-CLUSTER · VC-R6 (thay `applySavedProfile`, hàm cũ đọc lại prefs mỗi lần gọi): hàm này **không đọc prefs**. Prefs là
     * hồ sơ ĐANG DÙNG — đổi hồ sơ giữa phiên rồi repin mà đọc lại prefs là DPI/khung của hồ sơ mới tự nổ lên cụm, không
     * ai bấm (refute B6). Bản ghim được đọc MỘT lần lúc phiên bắt đầu (`CastSessionPin.kt`).
     *
     * Ba chốt trước khi chạm shell, theo thứ tự:
     *  1. `pinned == null` ⇒ không có gì để áp (giữ nguyên no-op của hàm cũ khi chưa lưu gì);
     *  2. **chốt cuối** [CastGeometryGuard.isShellSafe] (VC-R4) — bản ghi đi theo hồ sơ ⇒ theo tệp xuất/nhập: không sạch ⇒ 0 lệnh;
     *  3. khung được KẸP vào khung logic ĐO ĐƯỢC của VD (`wm size -d`, ưu tiên *Override* — khung `am task resize` dùng),
     *     đo hụt ⇒ 1920×720. Một khung lưu cho cụm khác kích thước (tệp nhập từ xe khác) không đẩy task ra ngoài màn.
     *
     * Bounds are per-task (safe per-app). Density is display-global on Android 10 — "last edit wins" for the display.
     *
     * @return B1b — khung (đã kẹp) vừa gửi `am task resize`; `null` = không gửi lệnh khung nào. Chỗ gọi ở cụm Chữ nhật đọc lại
     *   đúng khung này ([verifyFrame]); chỗ gọi cũ bỏ qua giá trị trả.
     */
    fun applyPinned(pkg: String, pinned: DisplayConfig?): CastBounds? {
        if (pinned == null) return null
        if (!CastGeometryGuard.isShellSafe(pinned)) {
            log("applyPinned: TỪ CHỐI cấu hình không sạch của $pkg — 0 lệnh")
            return null
        }
        var asked: CastBounds? = null
        val bounds = pinned.bounds
        if (bounds != null) {
            // 2.90 · R5: đo kích TRƯỚC khi tra task — lượt đo tự dò lại id nếu display hiện tại đã mất, nên lượt tra task và
            // lệnh resize sau đó chạy trên id mới.
            val size = queryDisplaySize(preferOverride = true)
            val taskId = findTaskIdForPkg(pkg)
            if (taskId != null) {
                val (w, h) = size ?: (1920 to 720)
                val b = CastGeometryGuard.clampBounds(bounds, 0, w, h)
                shell.execute("am task resize $taskId ${b.left} ${b.top} ${b.right} ${b.bottom}")
                asked = b
            }
        }
        if (pinned.density != CastGeometryGuard.DENSITY_RESET) {
            shell.execute("wm density ${pinned.density} -d $displayId")
        }
        return asked
    }

    /**
     * B1b · F4b — sau một lượt đặt ở cụm Chữ nhật: ĐỌC LẠI khung thật của task [pkg] trên cụm từ `am stack list`
     * ([CastStackParser.taskBoundsOn]); lệch [want] ⇒ `am task resize` LẠI đúng MỘT lần rồi đọc lại; vẫn lệch ⇒ chỉ log (không
     * vòng lặp, không lệnh khác). [ĐO-gv 05/10] app mở freeform lên màn ảo cụm ra cửa sổ dọc [825,0][1155,720] nếu không
     * resize — sự thật là bản đọc, không phải mã thoát của lệnh (CLAUDE.md §5).
     *
     * Bốn câu CLAUDE.md §4: display = [displayId] SỐNG của coordinator (đã xác minh trước lượt đặt); app = đúng [pkg] (khớp gói
     * chính xác, bỏ pinned); loại = task freeform vừa đặt — chỉ `am task resize` (không bê stack); hoàn tác = lượt đặt/repin
     * sau áp lại bản ghim, tắt chiếu trả app về display 0.
     *
     * @return true khi bản đọc khớp (sai số [SLOP_PX]).
     */
    fun verifyFrame(pkg: String, want: CastBounds): Boolean {
        for (attempt in 0..1) {
            val out = shell.execute("am stack list")
            val got = if (out.success) CastStackParser.taskBoundsOn(out.stdout, pkg, displayId) else null
            if (got != null && near(got, want)) {
                log("khung Chữ nhật $pkg = $got (khớp ${if (attempt == 0) "ngay" else "sau một lần thử lại"})")
                return true
            }
            if (attempt == 1) break
            val taskId = if (out.success) CastStackParser.findTaskId(out.stdout, pkg, displayId) else null
            if (taskId == null) { log("khung Chữ nhật $pkg: không đọc được task trên cụm $displayId — không thử lại"); return false }
            log("khung Chữ nhật $pkg lệch: muốn $want, đọc được ${got ?: "?"} ⇒ resize lại MỘT lần")
            shell.execute("am task resize $taskId ${want.left} ${want.top} ${want.right} ${want.bottom}")
        }
        log("khung Chữ nhật $pkg VẪN lệch $want sau một lần thử lại — để nguyên, người lái chỉnh bằng −/+ trong Cài đặt")
        return false
    }

    private fun near(a: CastBounds, b: CastBounds): Boolean =
        kotlin.math.abs(a.left - b.left) <= SLOP_PX && kotlin.math.abs(a.top - b.top) <= SLOP_PX &&
            kotlin.math.abs(a.right - b.right) <= SLOP_PX && kotlin.math.abs(a.bottom - b.bottom) <= SLOP_PX

    /**
     * Set freeform boot flags. Read only at boot by ATMS.retrieveSettings (no ContentObserver),
     * so they activate after a physical power-cycle. Idempotent — safe to run on every open.
     *
     * 2.96 · R13: đọc trước, chỉ ghi khoá chưa = 1 — dùng chung [FreeformSeedPolicy.seedFlagsReadFirst] (chuỗi lệnh ghi
     * byte-identical, vẫn marker-less như trước). Đọc: có [readGlobalSetting] ⇒ trong tiến trình, 0 shell (soát Pass 1 [P2] —
     * đọc qua shell vẫn tốn 2 lượt kênh dadb lúc mở chiếu); không ⇒ `settings get global <khoá>` qua shell. Đọc hỏng
     * (`null` / `success=false`) ⇒ ghi như cũ (fail-safe).
     *
     * @return các lệnh `settings put` đã thực sự gửi (rỗng = hai cờ đã bật sẵn).
     */
    fun ensureFreeformFlags(): List<String> {
        val wrote = FreeformSeedPolicy.seedFlagsReadFirst(
            read = FreeformSeedPolicy.readerOf(readGlobalSetting) { cmd -> shell.execute(cmd).let { if (it.success) it.stdout else null } },
            write = { cmd -> shell.execute(cmd) },
        )
        log(if (wrote.isEmpty()) "cờ freeform đã bật sẵn — không ghi" else "cờ freeform: ghi ${wrote.size} khoá")
        return wrote
    }

    /**
     * Probe whether freeform is alive by attempting a harmless `am task resize` on a cluster task.
     * Success (or no task) ⇒ freeform may be alive; rejection ⇒ not alive until next power-cycle.
     */
    fun isFreeformAlive(): Boolean {
        val stackResult = shell.execute("am stack list")
        if (!stackResult.success) return false
        val taskId = CastStackParser.parseTasks(stackResult.stdout)
            .firstOrNull { it.displayId == displayId }?.taskId ?: return false
        return shell.execute("am task resize $taskId 0 0 1920 720").success
    }

    /** True if [pkg] has a visible task on [targetDisplayId]. */
    fun isAppOnDisplay(pkg: String, targetDisplayId: Int): Boolean {
        val result = shell.execute("am stack list")
        if (!result.success) return false
        return CastStackParser.isAppOnDisplay(result.stdout, pkg, targetDisplayId)
    }

    /**
     * R4/R7: Verify the cluster display has (or can have) a fullscreen stack — not freeform-only.
     * Guards CP/AA (protected) casts away from a freeform-only display (surfaceflinger crash path).
     * Returns true when unsure (let the mover try) — false only when a freeform-only stack is proven.
     */
    fun verifyFullscreenStackAvailable(): Boolean {
        val stackCheck = shell.execute("am stack list")
        if (!stackCheck.success) return true // cannot verify → let mover try
        val hasStack = Regex("""Stack id=\d+.*displayId=$displayId""").containsMatchIn(stackCheck.stdout)
        if (!hasStack) return true // no stack yet → mover will create fullscreen
        val freeformOnly = stackCheck.stdout.lines().any { line ->
            line.contains("displayId=$displayId") && line.contains("windowingMode=5")
        } && !stackCheck.stdout.lines().any { line ->
            line.contains("displayId=$displayId") && line.contains("windowingMode=1")
        }
        if (freeformOnly) { log("CP/AA REJECTED: only freeform stack on display $displayId"); return false }
        return true
    }

    private companion object {
        /** Sai số đọc lại khung — cùng 2 px của `AppMover.isWindowedOnMain` (làm tròn của WM). */
        const val SLOP_PX = 2
    }

    private fun persistBounds(pkg: String, profile: CastProfile, left: Int, top: Int, right: Int, bottom: Int) {
        val existing = prefs.displayConfigFor(pkg, profile) ?: DisplayConfig.NORMAL_DEFAULT
        prefs.saveDisplayConfig(pkg, profile, existing.copy(bounds = CastBounds(left, top, right, bottom)))
    }

    /**
     * Kích VD cụm từ `wm size -d <displayId>` (CHỈ ĐỌC). [preferOverride] = false ⇒ *Physical* (biên kẹp của đường lùi
     * `wm size`, hành vi cũ); true ⇒ *Override* nếu có — khung logic đang hiệu lực mà `am task resize` dùng
     * ([applyPinned]). Phép parse ở [CastGeometryGuard.parseDisplaySize]. `null` = đo hụt.
     */
    private fun queryDisplaySize(preferOverride: Boolean): Pair<Int, Int>? {
        val id = displayId
        val result = shell.execute("wm size -d $id")
        if (!result.success) return null
        if (CastGeometryGuard.displayGone(result.stdout)) {
            // 2.90 · R5 — display [id] đã mất (màn ảo cụm dựng lại với id mới). Dò lại MỘT lần; id khác ⇒ đo lại trên id đó.
            val fresh = runCatching(redetect).getOrDefault(-1)
            log("wm size -d $id: display đã mất (0x0) ⇒ dò lại cụm: $fresh")
            if (fresh < 1 || fresh == id) return null
            val again = shell.execute("wm size -d $fresh")
            return if (again.success) CastGeometryGuard.parseDisplaySize(again.stdout, preferOverride) else null
        }
        return CastGeometryGuard.parseDisplaySize(result.stdout, preferOverride)
    }
}
