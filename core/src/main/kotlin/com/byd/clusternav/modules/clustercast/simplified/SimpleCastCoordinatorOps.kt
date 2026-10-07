package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ Thân mở projection + watchdog re-pin của SimpleCastCoordinator ═══
 *
 * Hai thân chạy TRÊN executor của coordinator (`openProjection` bọc try/catch; `repinEscapedCastApps` gate ở caller).
 * Tách THUẦN khỏi `SimpleCastCoordinator.kt` (784 dòng → trần 500, L6-debt 2026-09-27): thân hàm giữ nguyên byte, chỉ đổi
 * `private fun` thành hàm mở rộng `internal` cùng package. Mọi luật (R1/R2 dò VD sống, R3 hậu điều kiện, R4 tiền điều kiện,
 * R6 chỉ persist khi shell OK) ở KDoc từng hàm bên dưới và ở `SimpleCastCoordinator` — không có luật mới ở đây.
 */

/**
 * 2.89 · B4 DISPLAY-OWNER-DYNAMIC — báo id màn ảo cụm cho cổng sở hữu display của launcher ([SimpleCastCoordinator.onCastDisplay]):
 * [id] ≥ 1 = vừa dò LIVE (cùng lượt dò `ClusterDisplayResolver` mà cổng theme dùng); `< 1` = dò hụt hoặc đã đóng chiếu ⇒ `null`.
 * Gọi ở MỌI lượt [SimpleCastCoordinator.detectClusterDisplay] + sau mỗi lượt đóng chiếu thành công. Không bao giờ ném: hỏng ở
 * bên nhận chỉ log — đường chiếu đang chạy trên xe không được gãy vì sổ sở hữu (CLAUDE.md §6).
 */
internal fun SimpleCastCoordinator.publishCastDisplay(id: Int) {
    try {
        onCastDisplay(id.takeIf { it >= 1 })
    } catch (e: RuntimeException) {
        log("B4: báo id cụm $id cho cổng sở hữu display lỗi (${e.javaClass.simpleName}: ${e.message}) — chiếu đi tiếp")
    }
}

/**
 * Như `verifiedClusterDisplay` nhưng cho đường **HOÀN TÁC** (đóng projection / reset density): nếu chưa xác minh
 * thì dò TƯƠI một lần thay vì bỏ luôn. (Tách THUẦN khỏi `SimpleCastCoordinator.kt` 2.96 — trần 500 dòng; thân giữ nguyên.)
 *
 * Vì sao ([SOÁT 2026-09-15 · P2], CLAUDE §5 "mỗi thứ đổi ra ngoài phải có đường trả lại"): `wm size/overscan/
 * density` mà [DisplayConfigurator] đặt lên VD cụm được WM ghi vào `/data/system/display_settings.xml` theo
 * `uniqueId` ⇒ **sống qua cả reboot**. Nếu lần dò gần nhất hụt (shell chớp) mà ta bỏ luôn bước reset thì cụm giữ
 * override vĩnh viễn, chỉ còn `deepRescue` gỡ được. Dò tươi giữ nguyên bất biến R1/R2 (vẫn qua owner-guard, hụt
 * thì vẫn KHÔNG đặt gì) mà tăng hẳn cơ hội hoàn tác đúng chỗ.
 */
internal fun SimpleCastCoordinator.undoTargetDisplay(tag: String): Int? {
    val id = liveDisplayId
    if (id >= 1) return id
    val fresh = detectClusterDisplay()
    if (fresh >= 1) return fresh
    log("$tag: bỏ qua — dò lại vẫn không thấy VD cụm, không nhắm seed=$displayId")
    return null
}

/** B1a — kiểu người lái chọn cho lượt mở (MỘT lần đọc; lỗi ⇒ Bo tròn, đường đang chạy). */
internal fun SimpleCastCoordinator.desiredStyleOnce(): CastStyle = runCatching(desiredStyle).getOrDefault(CastStyle.CURVED)

/**
 * B1a — dò lại công thức một lần mỗi tiến trình (trên executor; shell đang dùng vì người lái vừa bật chiếu): `:app` đọc
 * `car.type` qua dadb khi tiến trình không đọc được prop. Chỉ thay khi projection CHƯA mở ([ProjectionManager.refreshRecipe]).
 */
internal fun SimpleCastCoordinator.probeRecipeOnce() {
    val probe = recipeProbe ?: return
    if (!recipeProbed.compareAndSet(false, true)) return
    val next = runCatching { probe(shell) }.getOrNull() ?: return
    if (next != projection.recipe && projection.refreshRecipe(next)) log("công thức chiếu dò lại: $next")
}

/**
 * 2.90 · R9 — [openProjectionBody] + TRẢ cụm LUÔN chạy ([ClusterThemeGuard.resumeLayers], idempotent): lượt mở có DỌN lớp phủ trước
 * opcode theme thì mọi lối ra (thành công, bỏ theme, DỪNG, dò hụt, ngắt) đều gắn lại lớp của Kachi trên id cụm dò SAU khi mở và gửi
 * `VM_BUBBLE_VIS` theo công tắc. `liveDisplayId` = lượt dò gần nhất của chính lượt mở này (`-1` khi hụt ⇒ lớp tự chọn display).
 */
internal fun SimpleCastCoordinator.openProjectionGuarded() {
    try {
        openProjectionBody()
    } finally {
        themeGuard.resumeLayers(liveDisplayId)
    }
}

/** Thân của [openProjection] — tách ra để mọi `return` sớm vẫn nằm trong `try` bắt-mọi-lối-thoát ở trên. */
internal fun SimpleCastCoordinator.openProjectionBody() {
    run {
        // (1) Dò TRƯỚC khi mở — CHỈ để dọn VD cụm còn sót từ tiến trình trước (projection còn mở). Sau reboot
        //     VD cụm chưa tồn tại (AutoContainer tạo khi mở projection) ⇒ hụt là bình thường ⇒ KHÔNG dọn, KHÔNG
        //     đặt gì theo seed. [ĐO] 2026-09-15: bản cũ dọn + đặt theo seed 1 = `kachi-slot-0` của launcher.
        val preOpenId = detectClusterDisplay()

        // Enable freeform boot flags so per-app bounds (resize + split) work after next power-cycle.
        // These settings are read ONLY at boot by ActivityTaskManagerService.retrieveSettings()
        // (no ContentObserver), so they take effect after a physical ignition off/on.
        // Idempotent — safe to run every open.
        geometry.ensureFreeformFlags()

        if (preOpenId >= 1) cleanDisplay(preOpenId)

        projection.resetState(false)
        probeRecipeOnce()
        // CLUSTER-THEME-SAFE (2.89, P0): opcode theme chỉ đi qua cổng [ClusterThemeGuard] + kế hoạch [ClusterStylePlan] —
        // gửi khi CHƯA có màn ảo cụm (mức A) hoặc — mức B (2.95: mọi đời `AutoContainer`) — màn ảo có 0 task + 0 cửa sổ (mức B, sau khi
        // gỡ ClusterBlack của chính Kachi), cách lần trước ≥ 15 s (sổ bền); còn lại bỏ theme, 16/35 đi tiếp hoặc DỪNG khi không
        // chứng minh được kiểu cụm (KDoc [ClusterStylePlan]).
        themeGuard.beginOpen()
        val ok = projection.open(preOpenId, themeGuard, desiredStyleOnce())
        if (!ok) {
            // Review 2.89 Pass 1 · safety-1: kế hoạch DỪNG lượt mở ⇒ 0 lệnh 16/35; câu lỗi mang lý do. Trạng thái Error ⇒ người
            // lái bật lại / `BubbleAutostart` thử lại (Error ⇒ openProjection).
            val gate = projection.abortedOn?.let { op ->
                " — theme $op: ${themeGuard.lastVerdict ?: "?"} · ${projection.lastPlan?.why ?: ""}"
            }.orEmpty()
            setError("Projection open failed$gate")
            return@run
        }

        // 2.89 · B2 VM-PREREQ-TRUTH: cờ một-lần `doze_whitelist_applied` (đặt cả khi lệnh hỏng, không bao giờ đọc lại) để VietMap
        // cài lại mất miễn pin mãi ⇒ hộp thoại "IVI không hỗ trợ" mỗi lần khởi động. Nay: đọc sự thật → áp phần thiếu → đọc lại
        // (CLAUDE.md §5), cùng MỘT hàm với móc kênh sẵn sàng + `VietMapAutostart`. Hỏng ⇒ log, lượt mở chiếu đi tiếp.
        // Review 2.89 Pass 3 · vietmap-dock-r2-6: SAU khi projection mở thành công (30/16/35 không còn đứng chờ khoá 2 s của lượt
        // nền lúc nổ máy) — trùng khoảng AutoContainer đang dựng màn ảo cụm (bước dò ngay dưới vốn phải chờ), cùng executor, cùng
        // tổng thời gian của lượt mở như trước (chỉ đổi thứ tự). Bên `:app` không chờ khoá (lượt nền đang đọc CÙNG sự thật).
        try {
            appPrereqs(shell)
        } catch (e: RuntimeException) {
            log("điều kiện nền app (B2): lỗi ${e.message} — mở chiếu đi tiếp")
        }

        // (2) Dò SAU khi mở — nguồn sự thật cho MỌI lệnh đặt bên dưới (R1). Đúng thứ tự đường proven cũ
        //     (`ClusterCast.cast()` git HEAD:471-478: castSeq → lặp dò 16×500 ms → đặt app). Hụt ⇒ trả đồng hồ,
        //     báo lỗi, KHÔNG đặt lên seed.
        // 2.90 · R5: lượt mở vừa GỬI opcode theme trên một màn ảo có sẵn ⇒ màn ảo dựng lại với id MỚI ([ĐO xe 06/10] 4 → 9) —
        // bỏ qua id cũ tới khi thấy id khác (hết lượt mà chỉ thấy id cũ ⇒ nhận nó: màn ảo không dựng lại).
        val sentTheme = projection.lastPlan?.themeOp != null
        val vd = detectClusterDisplay(awaitAfterOpen = true, exclude = if (sentTheme) preOpenId else -1)
        if (vd < 1) {
            log("openProjection: không dò thấy VD cụm sau khi mở → đóng projection, không đặt ClusterBlack")
            projection.close(displayId, themeGuard)
            setError("Cluster display not found / Không dò thấy màn cụm")
            return@run
        }
        // Dấu "đã gửi theme X, màn ảo cụm = vd" — chỉ để ĐỠ lượt gửi trùng lần sau (không bao giờ là lý do để gửi).
        themeGuard.bindVd(vd)
        configurator.apply(vd, DisplayConfig.NORMAL_DEFAULT)

        // Launch + resize black placeholder to keep projection alive (chuỗi lệnh nguyên văn — `CastPlaceholder.kt`).
        placeClusterPlaceholder(vd)

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
                // V-CLUSTER · VC-R6: nhận lại app đang nằm trên cụm = BẮT ĐẦU một phiên ⇒ ghim bản ghi FULL của hồ sơ đang
                // dùng (một lượt đọc) để repin sau này có đúng thứ để áp — trước đây repin đọc lại prefs mỗi lần.
                val pinned = if (appType == AppType.NORMAL) pinFull(ext.pkg, appType).pinned else null
                setState(SimpleCastState.CastingFull(ext.pkg, appType, DisplayConfig.forAppType(appType), pinned = pinned))
                adopted = true
                // B1b — đường MỚI chỉ cho phiên Chữ nhật (Bo tròn giữ nguyên: nhận lại, không resize — CLAUDE.md §6): app nhận lại
                // có thể đang trọn cụm, nằm dưới nền ADAS lớn luôn hiện ⇒ đặt vào khung phiên + đọc lại.
                if (appType == AppType.NORMAL && frameStyle == CastStyle.RECT) applySessionPin(ext.pkg, pinned)
            }
        }
        if (!adopted) setState(SimpleCastState.Idle)
    }
}

/** Một ô repin phải giữ: gói · nửa (`null` = toàn cụm) · bản ghim của PHIÊN (V-CLUSTER · VC-R6). */
private class RepinTarget(val pkg: String, val side: ClusterSlotSide?, val pinned: DisplayConfig?)

/**
 * V-CLUSTER · VC-R6 (sửa refute B6/C2): lượt repin KHÔNG đọc prefs. Tỉ lệ lấy từ `CastingSplit.leftPercent`, hình học từ
 * bản ghim của từng ô ([CastGeometryController.applyPinned]). Đổi hồ sơ giữa lúc chiếu chỉ đổi prefs; app bị kéo khỏi
 * cụm sau đó được đặt lại ĐÚNG như phiên đang có — không phải theo hồ sơ vừa chọn.
 */
internal fun SimpleCastCoordinator.doRepinEscapedCastApps() {
    val cur = state
    val expected: List<RepinTarget> = when (cur) {
        is SimpleCastState.CastingSplit -> buildList {
            cur.left?.let { add(RepinTarget(it.pkg, ClusterSlotSide.LEFT, it.pinned)) }
            cur.right?.let { add(RepinTarget(it.pkg, ClusterSlotSide.RIGHT, it.pinned)) }
        }
        is SimpleCastState.CastingFull ->
            if (cur.appType == AppType.NORMAL) listOf(RepinTarget(cur.targetPkg, null, cur.pinned)) else emptyList()
        else -> emptyList()
    }
    if (expected.isEmpty()) return
    val leftPercent = (cur as? SimpleCastState.CastingSplit)?.leftPercent ?: CastProfile.DEFAULT_PERCENT
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
    com.byd.clusternav.system.StackListSnapshot.record(stackOut)   // 2.96 R18 — nhịp đo ô dùng lại bản đọc này (KDoc ở đó)
    if (stackOut == null) { log("repin: không đọc được am stack list — bỏ lượt"); return }
    val now = System.currentTimeMillis()
    for (target in expected) {
        val pkg = target.pkg
        val side = target.side
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
        log("repin: $pkg escaped cluster → re-cast to slot=$side pct=$leftPercent (bản ghim phiên, keep running task/nav)")
        val ok = mover.castToCluster(
            pkg = pkg, activity = null, displayId = vd,
            appType = AppType.NORMAL, slotSide = side, leftPercent = leftPercent,
        )
        repinCooldownUntil[pkg] = now + SimpleCastCoordinator.REPIN_COOLDOWN_MS
        repinMissStreak.remove(pkg)
        if (ok != null) {
            applySessionPin(pkg, target.pinned)   // B1b: Chữ nhật ⇒ + đọc lại khung
            log("repin: $pkg re-cast issued (slot=$side)")
        } else {
            log("repin: $pkg re-cast FAILED")
        }
    }
}

/**
 * V-CLUSTER · VC-R7 — thân của [SimpleCastCoordinator.closeOrphanProjection]: tiến trình MỚI vừa chốt `cast_enabled`
 * BẬT→TẮT, mà tiến trình trước có thể đã để lại projection đang mở trên cụm ([CHƯA BIẾT] VD có sống qua tắt máy không —
 * OC-7). Không dọn thì cụm có HAI CHỦ: HUD thấy Cast TẮT nên ghi op 39, trong khi mặt chiếu cũ vẫn đứng đó.
 *
 * ## Vì sao KHÔNG đi `closeProjectionSync` như spec §11.4.3 phác
 * [ĐO code] `ProjectionManager.close` là no-op khi `isOpen == false` (`ProjectionManager.kt:56`), mà tiến trình mới luôn
 * có `isOpen = false` ⇒ đường đó chỉ reset `wm` rồi báo Off, projection cũ vẫn mở. Nên ghép lại từ ba mảnh ĐÃ CHẠY TRÊN
 * XE (CLAUDE.md §6 — không dựng đường mới):
 *  • `configurator.reset` — bước hoàn tác `wm` của `closeProjectionSync`;
 *  • `cleanDisplay` — đúng lượt dọn *"projection còn sót từ tiến trình trước"* của `openProjectionBody`: trả task thật về
 *    display 0 (rồi tự gửi 18 → 0). ⚠ Nó RETURN SỚM khi VD chỉ còn placeholder `ClusterBlack` (không gửi 18/0);
 *  • `projection.resetState(true)` + `projection.close` — VD dò LIVE thấy = projection ĐANG MỞ (sự thật đo được, không cờ
 *    RAM, CLAUDE.md §5), rồi đúng chuỗi đóng 18 → 0. Gửi 18/0 lần hai sau `cleanDisplay` là điều `closeProjection()` (công
 *    tắc TẮT, chạy trên xe) vốn đã làm.
 *
 * ## Bốn câu CLAUDE.md §4 (senior review 2.84 — trả lời bằng mã, file:line)
 *  1. **Display**: chỉ id dò LIVE — `detectClusterDisplay()` (`SimpleCastCoordinator.kt:70-85`) → `ClusterDisplayResolver.resolve`
 *     (`ClusterDisplayResolver.kt:46-50`: grep `fission|xdja`, trả `-1` khi hụt HOẶC là VD của chính launcher, không
 *     bao giờ 0, không seed); `vd < 1` ⇒ return trước mọi lệnh ghi (dưới đây), và `CastDisplayCleaner.kt:24` chặn lần nữa.
 *  2. **App**: allow-list NGƯỢC — mọi task trên đúng VD đó TRỪ `com.android.*` · `ProjectionApps.STACK_SKIP_PKGS` (launcher3,
 *     systemui, CarPlay) · placeholder `ClusterBlack` (`CastStackParser.kt:197-208`); cùng bộ lọc lượt dọn trước khi mở
 *     (`openProjectionBody` :31) đã chạy trên xe. Không có gói nào bị nhắm theo tên (CLAUDE.md §7).
 *  3. **Stack**: chỉ task của stack loại `standard` — task của stack home/recents/assistant bị loại
 *     (`CastStackParser.nonStandardStackTasks`, dùng chung `StackParse`); việc bê là ở mức TASK (`am stack move-task`,
 *     `CastDisplayCleaner.kt:46`), KHÔNG `am display move-stack` ⇒ không stack nào (nhất là home) bị đổi display. Đích là stack
 *     standard, không pinned, id > 0 của display 0 (`CastStackParser.findTargetStackOnDisplay0`). Task trong stack pinned vẫn
 *     được bê ở mức task — lý do [ĐO AOSP] ở KDoc `tasksToClean`.
 *  4. **Hoàn tác**: chính nó LÀ đường hoàn tác của phiên cũ; `wm size/overscan/density reset` trên VD đó đi trước (khi VD
 *     còn), cùng lẽ `undoTargetDisplay` — override `wm` sống qua reboot trong `display_settings.xml` (CLAUDE.md §5). Hỏng
 *     giữa chừng: `isOpen` giữ `true` (sự thật đo được) ⇒ công tắc TẮT/BẬT tường minh sau đó đi đường thường.
 *
 * ⚠ Lúc chạy thực tế [SUY từ ĐO 29/09]: BYD giết Kachi mỗi lần tắt máy và Android dựng lại tiến trình ~0,3 s sau
 * (`FloatingBubbleService` `START_STICKY`) ⇒ lượt chốt + lượt dọn này chạy NGAY LÚC TẮT MÁY, khi VD của phiên vừa rồi
 * nhiều khả năng còn sống — tức đây là đường thường gặp chứ không phải ca hiếm. [CHƯA BIẾT] VD có sống qua lần tắt máy
 * không (OC-7).
 *
 * Chỉ chạy khi trạng thái còn `Off` (tiến trình mới, chưa ai mở chiếu): nếu người lái đã kịp BẬT lại thì lượt mở đó đã tự
 * dọn VD cũ, không được đóng chồng lên phiên mới.
 */
internal fun SimpleCastCoordinator.closeOrphanProjectionBody() {
    if (state != SimpleCastState.Off) { log("closeOrphan: bỏ — trạng thái $state (đã có phiên mới)"); return }
    val vd = detectClusterDisplay()
    if (vd < 1) { log("closeOrphan: không dò thấy VD cụm — không có projection mồ côi, 0 lệnh ghi"); return }
    log("closeOrphan: VD cụm $vd còn sống từ tiến trình trước mà Cast vừa chốt TẮT ⇒ reset wm + dọn + đóng projection")
    configurator.reset(vd)
    cleanDisplay(vd)
    projection.resetState(true)
    if (!projection.close(vd, themeGuard)) {
        log("closeOrphan: đóng projection HỎNG — giữ isOpen=true (sự thật), lượt BẬT/TẮT sau đi đường thường")
        return
    }
    // CLUSTER-THEME-SAFE bước 3 — đường mới xuống CUỐI: ClusterBlack của tiến trình trước không được nằm lại trên màn ảo cụm.
    themeGuard.removePlaceholder("closeOrphan")
    publishCastDisplay(-1)   // B4: projection mồ côi đã đóng ⇒ không display nào còn thuộc CAST
}
