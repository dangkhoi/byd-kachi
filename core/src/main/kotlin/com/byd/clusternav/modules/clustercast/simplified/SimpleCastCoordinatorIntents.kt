package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ Ba bộ xử lý ý định của SimpleCastCoordinator ═══
 *
 * Nhận `SimpleCastIntent` đã xếp lên executor nối tiếp (`dispatch`/`handleIntent`) và đặt/gỡ app trên VD cụm.
 * Tách THUẦN khỏi `SimpleCastCoordinator.kt` (784 dòng → trần 500, L6-debt 2026-09-27): thân hàm giữ nguyên byte, chỉ đổi
 * `private fun` thành hàm mở rộng `internal` cùng package. Mọi luật (R1/R2 dò VD sống, R3 hậu điều kiện, R4 tiền điều kiện,
 * R6 chỉ persist khi shell OK) ở KDoc từng hàm bên dưới và ở `SimpleCastCoordinator` — không có luật mới ở đây.
 */

internal fun SimpleCastCoordinator.handleCastFull(intent: SimpleCastIntent.CastFull) {
    // R4: Precondition validation (fail-fast before any shell command)
    val rejectReason = CastSlotValidator.validateCastFull(intent.pkg, intent.appType, projection.isOpen)
    if (rejectReason != null) {
        log("CastFull REJECTED: $rejectReason for ${intent.pkg}")
        setError("Cast rejected: $rejectReason")
        return
    }

    val current = state
    // If projection is still opening, wait and retry once
    if (current == SimpleCastState.Opening) {
        Thread.sleep(1500) // projection open takes ~1.1s
        if (state != SimpleCastState.Idle) return // still not ready — give up
    }
    val afterWait = state
    // Only cast from IDLE or replace current full cast
    if (afterWait != SimpleCastState.Idle && afterWait !is SimpleCastState.CastingFull) {
        return // invalid transition — ignore
    }

    // R1: dò LIVE ngay trước khi đặt (VD có thể đã bị tái tạo với id khác). Hụt/guard ⇒ từ chối, không đặt.
    val vd = detectClusterDisplay()
    if (vd < 1) { setError("Cast rejected: cluster display unresolved / chưa dò thấy màn cụm"); return }

    // R4: For protected apps, verify we will land in fullscreen stack (not freeform)
    if (intent.appType.isProtected && !geometry.verifyFullscreenStackAvailable()) {
        setError("Cast rejected: ${CastRejectReason.PROTECTED_FULLSCREEN_STACK_UNPROVEN}")
        return
    }

    // If currently casting something else full, stop it first
    if (afterWait is SimpleCastState.CastingFull) {
        returnApp(afterWait.targetPkg, afterWait.appType)
    }

    // If app is ALREADY on the cluster, just adopt state — don't re-cast (prevents infinite loop).
    // BUT still restore the saved size + DPI: previously this branch used a fresh NORMAL_DEFAULT
    // config and returned BEFORE applySavedProfile, so a resized app lost its size on every
    // re-cast where it happened to already be on the cluster (owner bug #1, 2026-08-12).
    //
    // V-CLUSTER · VC-R6: đây là Ý ĐỊNH CHIẾU của người lái ⇒ đọc hồ sơ ĐANG DÙNG — MỘT lần ([pinFull]) cho cả hai nhánh,
    // rồi ghim vào trạng thái. Mọi lượt áp tự động sau đó (repin) dùng bản ghim, không đọc lại prefs (refute B6).
    val pin = pinFull(intent.pkg, intent.appType)
    if (geometry.isAppOnDisplay(intent.pkg, vd)) {
        setState(SimpleCastState.CastingFull(intent.pkg, intent.appType, pin.config, pinned = pin.pinned))
        if (intent.appType == AppType.NORMAL) {
            geometry.applyPinned(intent.pkg, pin.pinned)
        }
        return
    }

    val config = pin.config
    if (!configurator.apply(vd, config)) {
        setError("Display config failed")
        return
    }

    val castTaskId = mover.castToCluster(
        pkg = intent.pkg,
        activity = null,
        displayId = vd,
        appType = intent.appType,
    )
    if (castTaskId != null) {
        // R3: Postcondition verification — use verifier instead of simple isAppOnDisplay
        val outcome = verifier.verifyCastFull(intent.pkg, vd)
        when (outcome) {
            is CastMutationOutcome.Verified -> {
                val savedTaskId = if (castTaskId > 0) castTaskId else outcome.taskId
                setState(SimpleCastState.CastingFull(intent.pkg, intent.appType, config, savedTaskId, pin.pinned))
                // R6: Apply the PINNED FULL-profile bounds + density ONLY after verified landing
                if (intent.appType == AppType.NORMAL) {
                    geometry.applyPinned(intent.pkg, pin.pinned)
                }
            }
            else -> {
                // Postcondition failed — do NOT commit state, do NOT persist prefs
                log("postcondition FAIL: $outcome")
                setError("Cast failed: app did not land on cluster")
            }
        }
    } else {
        val msg = if (intent.appType.isProtected) {
            "Open ${intent.appType.name} app first / Mở app trước rồi chiếu"
        } else {
            "Cast failed / Không chiếu được"
        }
        setError(msg)
    }
}

internal fun SimpleCastCoordinator.handleCastSlot(intent: SimpleCastIntent.CastSlot) {
    // R4: Precondition — rejects CP/AA and occupied slots
    val rejectReason = CastSlotValidator.validateCastSlot(
        pkg = intent.pkg,
        side = intent.side,
        currentState = state,
        projectionOpen = projection.isOpen,
    )
    if (rejectReason != null) {
        log("CastSlot REJECTED: $rejectReason for ${intent.pkg} side=${intent.side}")
        setError("Slot rejected: $rejectReason")
        return
    }

    val current = state
    // If projection is still opening, wait and retry once
    if (current == SimpleCastState.Opening) {
        Thread.sleep(1500)
        if (state != SimpleCastState.Idle) return
    }
    val afterWait = state
    if (afterWait != SimpleCastState.Idle && afterWait !is SimpleCastState.CastingSplit) {
        return // can only split from idle or existing split
    }

    // R1: dò LIVE ngay trước khi đặt. Hụt/guard ⇒ từ chối, không đặt.
    val vd = detectClusterDisplay()
    if (vd < 1) { setError("Slot rejected: cluster display unresolved / chưa dò thấy màn cụm"); return }

    // V-CLUSTER · VC-R6 + sửa refute C2: tỉ lệ = tỉ lệ CỦA PHIÊN nếu đang chia (ô thứ hai KHÔNG đọc lại prefs — đổi hồ
    // sơ giữa hai lượt chiếu ô là hai nửa chồng nhau/hở), chưa chia thì đọc hồ sơ đang dùng MỘT lần. Bản ghi của ô ghim
    // theo đúng tỉ lệ đó.
    val leftPercent = sessionLeftPercent(afterWait)
    val config = configurator.resolveConfig(intent.pkg, AppType.NORMAL, prefs)
    val slot = SlotState(intent.pkg, config, pinned = pinSlot(intent.pkg, intent.side, leftPercent))

    // Split mode: display config (wm size/overscan) is DISPLAY-GLOBAL on Android.
    if (afterWait is SimpleCastState.CastingSplit) {
        // Don't re-apply display config — would affect the existing app
    } else {
        if (!configurator.apply(vd, config)) {
            setError("Display config failed for slot")
            return
        }
    }

    val ok = mover.castToCluster(
        pkg = intent.pkg,
        activity = null,
        displayId = vd,
        appType = AppType.NORMAL,
        slotSide = intent.side,
        leftPercent = leftPercent,
    )
    if (ok == null) {
        setError("Cast to slot failed")
        return
    }

    // R3: Postcondition verification for split
    val outcome = verifier.verifyCastSplit(intent.pkg, vd, intent.side)
    when (outcome) {
        is CastMutationOutcome.Verified -> {
            val newState = when {
                afterWait is SimpleCastState.CastingSplit && intent.side == ClusterSlotSide.LEFT ->
                    afterWait.copy(left = slot)
                afterWait is SimpleCastState.CastingSplit && intent.side == ClusterSlotSide.RIGHT ->
                    afterWait.copy(right = slot)
                intent.side == ClusterSlotSide.LEFT ->
                    SimpleCastState.CastingSplit(left = slot, right = null, leftPercent = leftPercent)
                else ->
                    SimpleCastState.CastingSplit(left = null, right = slot, leftPercent = leftPercent)
            }
            setState(newState)
            // R6: Restore the PINNED profile geometry (bounds + DPI) ONLY after verified landing.
            // If no profile is saved (pinned null), the ratio-default bounds from AppMover.fitToCluster stand.
            geometry.applyPinned(intent.pkg, slot.pinned)
        }
        else -> {
            log("CastSlot postcondition FAIL: $outcome")
            setError("Slot cast failed: app did not land")
        }
    }
}

internal fun SimpleCastCoordinator.handleStop(intent: SimpleCastIntent.Stop) {
    val current = state
    when {
        current is SimpleCastState.CastingFull -> {
            setState(SimpleCastState.Stopping)
            returnApp(current.targetPkg, current.appType, current.taskId)
            if (current.appType.isProtected) {
                undoTargetDisplay("stop.densityReset")?.let { shell.execute("wm density reset -d $it") }
            } else {
                refreshCluster()
            }
            setState(SimpleCastState.Idle)
        }
        current is SimpleCastState.CastingSplit && intent.slot != null -> {
            val slotToStop = when (intent.slot) {
                ClusterSlotSide.LEFT -> current.left
                ClusterSlotSide.RIGHT -> current.right
            }
            if (slotToStop != null) {
                returnApp(slotToStop.pkg, AppType.NORMAL)
            }
            // Determine remaining after stopping slot
            val remainingLeft = if (intent.slot == ClusterSlotSide.LEFT) null else current.left
            val remainingRight = if (intent.slot == ClusterSlotSide.RIGHT) null else current.right
            if (remainingLeft == null && remainingRight == null) {
                setState(SimpleCastState.Idle)
            } else {
                // `copy` giữ tỉ lệ của PHIÊN (V-CLUSTER · C2): nửa còn lại vẫn đứng ở đúng tỉ lệ đó trên cụm.
                setState(current.copy(left = remainingLeft, right = remainingRight))
            }
        }
        current is SimpleCastState.CastingSplit && intent.slot == null -> {
            setState(SimpleCastState.Stopping)
            current.left?.let { returnApp(it.pkg, AppType.NORMAL) }
            current.right?.let { returnApp(it.pkg, AppType.NORMAL) }
            refreshCluster()
            setState(SimpleCastState.Idle)
        }
        else -> {}
    }
}
