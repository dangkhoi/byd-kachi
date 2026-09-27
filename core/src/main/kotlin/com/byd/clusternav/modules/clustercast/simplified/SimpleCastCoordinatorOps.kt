package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ Thân mở projection + watchdog re-pin của SimpleCastCoordinator ═══
 *
 * Hai thân chạy TRÊN executor của coordinator (`openProjection` bọc try/catch; `repinEscapedCastApps` gate ở caller).
 * Tách THUẦN khỏi `SimpleCastCoordinator.kt` (784 dòng → trần 500, L6-debt 2026-09-27): thân hàm giữ nguyên byte, chỉ đổi
 * `private fun` thành hàm mở rộng `internal` cùng package. Mọi luật (R1/R2 dò VD sống, R3 hậu điều kiện, R4 tiền điều kiện,
 * R6 chỉ persist khi shell OK) ở KDoc từng hàm bên dưới và ở `SimpleCastCoordinator` — không có luật mới ở đây.
 */

/** Thân của [openProjection] — tách ra để mọi `return` sớm vẫn nằm trong `try` bắt-mọi-lối-thoát ở trên. */
internal fun SimpleCastCoordinator.openProjectionBody() {
    run {
        // (1) Dò TRƯỚC khi mở — CHỈ để dọn VD cụm còn sót từ tiến trình trước (projection còn mở). Sau reboot
        //     VD cụm chưa tồn tại (AutoContainer tạo khi mở projection) ⇒ hụt là bình thường ⇒ KHÔNG dọn, KHÔNG
        //     đặt gì theo seed. [ĐO] 2026-09-15: bản cũ dọn + đặt theo seed 1 = `kachi-slot-0` của launcher.
        val preOpenId = detectClusterDisplay()

        if (!prefs.dozeWhitelistApplied()) {
            shell.execute("cmd deviceidle whitelist +vn.vietmap.live")
            prefs.setDozeWhitelistApplied(true)
        }

        // Enable freeform boot flags so per-app bounds (resize + split) work after next power-cycle.
        // These settings are read ONLY at boot by ActivityTaskManagerService.retrieveSettings()
        // (no ContentObserver), so they take effect after a physical ignition off/on.
        // Idempotent — safe to run every open.
        geometry.ensureFreeformFlags()

        if (preOpenId >= 1) cleanDisplay(preOpenId)

        projection.resetState(false)
        val ok = projection.open(preOpenId)
        if (!ok) { setError("Projection open failed"); return@run }

        // (2) Dò SAU khi mở — nguồn sự thật cho MỌI lệnh đặt bên dưới (R1). Đúng thứ tự đường proven cũ
        //     (`ClusterCast.cast()` git HEAD:471-478: castSeq → lặp dò 16×500 ms → đặt app). Hụt ⇒ trả đồng hồ,
        //     báo lỗi, KHÔNG đặt lên seed.
        val vd = detectClusterDisplay(awaitAfterOpen = true)
        if (vd < 1) {
            log("openProjection: không dò thấy VD cụm sau khi mở → đóng projection, không đặt ClusterBlack")
            projection.close(displayId)
            setError("Cluster display not found / Không dò thấy màn cụm")
            return@run
        }
        configurator.apply(vd, DisplayConfig.NORMAL_DEFAULT)

        // Launch + resize black placeholder to keep projection alive.
        // Component MUST be <installed applicationId>/<full class FQN>. The class FQN keeps the code
        // namespace (com.byd.clusternav.*), which now DIFFERS from the applicationId (com.byd.clusternav2).
        // A leading-dot class ('.modules...') would be resolved by am/ComponentName against the PACKAGE part
        // (selfPackage) → 'com.byd.clusternav2.modules...ClusterBlackActivity', a class that does NOT exist
        // (the registered component is 'com.byd.clusternav2/com.byd.clusternav.modules...ClusterBlackActivity').
        // So spell the class in full — never relative — for correct launch under the isolated app.
        shell.execute("am start --display $vd --windowingMode 5" +
            " -n '$selfPackage/com.byd.clusternav.modules.clustercast.ClusterBlackActivity'")
        Thread.sleep(1000)
        val stackResult = shell.execute("am stack list")
        if (stackResult.success) {
            val taskId = CastStackParser.findTaskId(stackResult.stdout, selfPackage, vd)
                ?: CastStackParser.findTaskId(stackResult.stdout, "ClusterBlackActivity", vd)
            if (taskId != null) shell.execute("am task resize $taskId 0 0 1920 720")
        }

        // Adopt external app already on the cluster display (e.g. CP from previous session)
        val adoptResult = shell.execute("am stack list")
        var adopted = false
        if (adoptResult.success) {
            val tasks = CastStackParser.parseTasks(adoptResult.stdout)
            val ext = tasks.firstOrNull { t ->
                t.displayId == vd && t.visible &&
                    t.pkg != selfPackage && !t.pkg.startsWith("com.android.")
            }
            if (ext != null) {
                val appType = AppMover.classifyApp(ext.pkg)
                setState(SimpleCastState.CastingFull(ext.pkg, appType, DisplayConfig.forAppType(appType)))
                adopted = true
            }
        }
        if (!adopted) setState(SimpleCastState.Idle)
    }
}

internal fun SimpleCastCoordinator.doRepinEscapedCastApps() {
    val expected: List<Pair<String, ClusterSlotSide?>> = when (val cur = state) {
        is SimpleCastState.CastingSplit -> buildList {
            cur.left?.let { add(it.pkg to ClusterSlotSide.LEFT) }
            cur.right?.let { add(it.pkg to ClusterSlotSide.RIGHT) }
        }
        is SimpleCastState.CastingFull ->
            if (cur.appType == AppType.NORMAL) listOf(cur.targetPkg to null) else emptyList()
        else -> emptyList()
    }
    if (expected.isEmpty()) return
    // ═══ H2 (PERF 2026-09-16) — một lượt ĐỌC = một lệnh shell, không phải 1+N ═══════════════════════════
    // [ĐO xe 2026-09-16] (`docs/diagnostics/perf-profile-2026-09-16.md` §0): 305 `am stack list` + 304
    // `dumpsys display` trong 47 phút (≈13 lệnh/phút) — mỗi lượt watchdog chạy CẢ HAI, rồi `isAppOnDisplay`
    // lại chạy `am stack list` MỘT LẦN NỮA cho từng gói khi cast chia đôi. Mọi lệnh ấy xếp hàng trên CÙNG
    // một chủ `ShellTransport` với lệnh đặt cửa sổ của màn chính.
    //
    // Lượt ĐỌC (câu hỏi "app còn trên cụm không") nay dùng: id display đã xác minh + **một** `am stack list`
    // chia cho mọi gói (parser đã thuần sẵn). Lượt ĐẶT thì KHÔNG đổi một bước nào — vẫn dò TƯƠI ngay trước
    // khi đặt (bất biến R1, xem chỗ gọi `detectClusterDisplay()` bên dưới).
    val probeVd = liveDisplayId.takeIf { it >= 1 } ?: detectClusterDisplay()
    if (probeVd < 1) { log("repin: display cụm chưa xác minh — bỏ lượt"); return }
    val stackOut = shell.execute("am stack list").let { if (it.success) it.stdout else null }
    if (stackOut == null) { log("repin: không đọc được am stack list — bỏ lượt"); return }
    val now = System.currentTimeMillis()
    for ((pkg, side) in expected) {
        if (pkg == selfPackage) continue
        if (CastStackParser.isAppOnDisplay(stackOut, pkg, probeVd)) { repinMissStreak.remove(pkg); continue }
        // Debounce: require MISSING on two consecutive probes (ignore transient parse gaps and the
        // split-second while Kiki's own launch is in flight).
        val streak = (repinMissStreak[pkg] ?: 0) + 1
        repinMissStreak[pkg] = streak
        if (streak < 2) { log("repin: $pkg not on cluster (streak=$streak) — waiting"); continue }
        if (now < (repinCooldownUntil[pkg] ?: 0L)) { log("repin: $pkg cooling down"); continue }
        // R1 KHÔNG đổi: sắp ĐẶT ⇒ dò TƯƠI id display cụm ngay tại đây (chỉ ở nhánh hiếm này, không phải mỗi
        // nhịp đọc). Hụt ⇒ bỏ lượt, KHÔNG rơi về seed — đúng như đường cũ.
        val vd = detectClusterDisplay()
        if (vd < 1) { log("repin: dò lại không thấy VD cụm trước khi đặt — bỏ lượt"); return }
        log("repin: $pkg escaped cluster → re-cast to slot=$side (keep running task/nav)")
        val leftPercent = prefs.splitRatioLeftPercent()
        val ok = mover.castToCluster(
            pkg = pkg, activity = null, displayId = vd,
            appType = AppType.NORMAL, slotSide = side, leftPercent = leftPercent,
        )
        repinCooldownUntil[pkg] = now + SimpleCastCoordinator.REPIN_COOLDOWN_MS
        repinMissStreak.remove(pkg)
        if (ok != null) {
            geometry.applySavedProfile(pkg, if (side != null) CastProfile.of(side, leftPercent) else CastProfile.FULL)
            log("repin: $pkg re-cast issued (slot=$side)")
        } else {
            log("repin: $pkg re-cast FAILED")
        }
    }
}
