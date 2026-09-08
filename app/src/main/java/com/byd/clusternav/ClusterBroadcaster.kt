package com.byd.clusternav

import com.byd.clusternav.navigation.NavFormat
import com.byd.clusternav.navigation.NavParse
import com.byd.clusternav.navigation.SourceArbiter
import com.byd.clusternav.navigation.TurnDistanceInterpolator
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * FEEDER — vòng đời đẩy nav lên cụm: emit / stop / reset cờ kẹt + nhịp tim giữ làn + self-heal.
 * KHÔNG dựng wire format ở đây (xem AmapFrameBuilder) và KHÔNG parse chuỗi (xem NavParse) —
 * lớp này chỉ điều phối + sendBroadcast. KHÔNG root, KHÔNG quyền, KHÔNG chiếm màn.
 */
object ClusterBroadcaster {
    private const val TAG = "ClusterBroadcaster"

    // Existing cluster heartbeat remains exactly 400 ms; freshness is monotonic and session-scoped.
    private const val HEARTBEAT_MS = 400L
    private const val STALE_MS = 180_000L
    // I2 (1.14): bước cuộn marquee TÍNH THEO THỜI GIAN cho ĐỀU + MƯỢT (bản cũ theo scrollTick tăng không đều
    // theo emission = dựt). ~700ms/ký tự = chậm, đều; firmware chỉ re-render mỗi lần gửi (~400ms) nên đây là
    // độ mượt tối đa khả dĩ với ô ký-tự cố định.
    private const val MARQUEE_STEP_MS = 700L
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private data class LanePayload(
        val context: Context,
        val state: NavState,
        val byd: Boolean,
    )

    private data class SessionFrame(
        val sourceId: String,
        val sessionId: String,
        val sequence: Long,
    )

    private val sourceLock = Any()
    private var selectedSource: String? = null
    private var sessionSerial = 0L
    private var sessionSelectedAtEpochMs = 0L
    private var sourceSequence = 0L
    private var lastCleanRoad = ""
    private var roadShownAtMs = 0L    // I2 (1.14): mốc thời gian đường hiện tại xuất hiện → offset marquee tính từ đây (đều, bắt đầu từ đầu tên)
    // D4 (closeout 1.28): last-emitted icon|seg|road for log-on-change — the ~4/sec heartbeat re-emits identical
    // frames; only Log.i when this key changes (kills per-frame spam, keeps a low-rate signal). Main-thread only.
    private var lastEmitLaneKey: String? = null
    private val hudController = HudMirrorController()

    private val emissionArbiter by lazy {
        AmapEmissionArbiter(
            scheduler = HandlerAmapEmissionScheduler(handler),
            sink = ::handleEmission,
            monotonicNowMs = SystemClock::elapsedRealtime,
            heartbeatMs = HEARTBEAT_MS,
        )
    }

    /** Called at the accepted source boundary; the next frame starts a new monotonic Amap session. */
    fun selectSource(sourceId: String) {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        val sessionId = synchronized(sourceLock) {
            if (selectedSource == sourceId) return
            selectedSource = sourceId
            sessionSerial++
            sessionSelectedAtEpochMs = System.currentTimeMillis()
            sourceSequence = 0L
            "amap-$sessionSerial"
        }
        emissionArbiter.beginSession(sourceId, sessionId)
    }

    /** Deliver one fresh source frame to the canonical AmapService lane. */
    fun emitLane(ctx: Context, s: NavState, byd: Boolean = false) {
        if (!s.active) { stopLane(ctx); return }
        val identity = synchronized(sourceLock) {
            if (selectedSource == null) {
                selectedSource = "legacy-navigation"
                sessionSerial++
                sessionSelectedAtEpochMs = System.currentTimeMillis()
            }
            if (s.updatedAt > 0L && s.updatedAt < sessionSelectedAtEpochMs) {
                Log.i(TAG, "drop old source frame updatedAt=${s.updatedAt} sessionStart=$sessionSelectedAtEpochMs")
                return
            }
            sourceSequence++
            SessionFrame(checkNotNull(selectedSource), "amap-$sessionSerial", sourceSequence)
        }
        emissionArbiter.beginSession(identity.sourceId, identity.sessionId)

        val clean = NavFormat.cleanRoadName(s.road)
        val seg = NavParse.parseMeters(s.distance)
        if (seg > 0) {
            if (Prefs.interpolate(ctx)) {
                TurnDistanceInterpolator.anchor(
                    seg, "$clean|${s.maneuverText}", SystemClock.elapsedRealtime(), SpeedProvider.mps(),
                )
            }
        } else {
            runCatching { TurnDistanceInterpolator.clearAnchor() }
        }
        emissionArbiter.sourceFrame(
            sourceId = identity.sourceId,
            sessionId = identity.sessionId,
            sourceSequence = identity.sequence,
            observedAtMs = SystemClock.elapsedRealtime(),
            freshForMs = STALE_MS,
            payload = LanePayload(ctx.applicationContext, s, byd),
        )
    }

    /** Dựng frame với tên đường (marquee cuộn theo THỜI GIAN, hoặc TĨNH = tên đầy đủ) rồi gửi. Dùng chung cho emit + nhịp tim. */
    private fun sendFrame(ctx: Context, s: NavState, byd: Boolean) {
        // Storage cap (defensive, periodic): while verbose data-collection is ON this ~4 Hz path writes a CSV
        // row + (on change) an arrow PNG every frame, so opportunistically trim the diagnostics dir to the cap.
        // enforce() is throttled to once/60s + runs off-thread, so the nav frame pays only a volatile read here;
        // no-op when verbose is OFF (nothing is being written). This bounds a single long drive between the
        // session-start trims (NavNotificationListener.onListenerConnected).
        if (NavLog.verbose) DiagStorageCap.enforce(ctx)
        // marquee ON (mặc định 1.14): cuộn cửa sổ ROAD_MAX_UNITS ký tự, offset theo THỜI GIAN từ lúc đường
        // xuất hiện (roadShownAtMs) → đều/mượt, bắt đầu từ đầu tên. marquee OFF: gửi tên RÚT GỌN
        // (NavFormat.fitRoadName, ví dụ "Trần Trọng Kim" → "T.T.Kim") thay vì để firmware hard-cut tên đầy đủ.
        // KHÔNG dùng scrollTick (tăng không đều theo emission = dựt — bug bản cũ owner báo).
        val road = if (Prefs.marquee(ctx)) {
            val tick = if (roadShownAtMs > 0L)
                ((SystemClock.elapsedRealtime() - roadShownAtMs) / MARQUEE_STEP_MS).toInt()
            else 0
            NavFormat.roadWindow(lastCleanRoad, tick, NavFormat.ROAD_MAX_UNITS)
        } else NavFormat.fitRoadName(lastCleanRoad)
        // PROJECT nội suy: cự ly trừ dần giữa 2 noti (hạ lag). Truyền tốc-độ-HAL THÔ vào project() — interpolator
        // tự ưu tiên closingRate (tự khử over-read) và chỉ dùng speed×SPEEDO_CORRECTION khi chưa có closingRate.
        // speed vẫn cần để clamp closingRate ([0, 1.2×v+3]) chống delta-noti lỗi. Tắt nội suy -> parse thô.
        val rawMeters = NavParse.parseMeters(s.distance)
        val hasDist = rawMeters > 0    // <= 0 coi như KHÔNG CÓ cự ly (chỉ hướng / đã tới)
        val rawSeg = if (hasDist) {
            if (Prefs.interpolate(ctx)) TurnDistanceInterpolator.project(SpeedProvider.mps(), SystemClock.elapsedRealtime())
            else rawMeters
        } else -1
        // R-1: rawSeg==0 (nội suy CHẠM rẽ) PHẢI forward 0 → buildGuidanceFrame thấy seg==0 → gửi SEG=-1 (trống, đúng ý).
        // Nếu map 0→-1 ở đây, builder RE-PARSE cự ly thô từ noti → nhảy NGƯỢC về số noti cũ (30→15→vọt lại 50) đúng lúc tới rẽ.
        val segOverride = if (rawSeg >= 0) NavParse.quantizeDisplay(rawSeg) else -1
        // v1.03: arrow classification happens before ingest in NavNotificationListener.
        // The frame's maneuverIcon is already the final classified code. Do not read
        // NavRepository.state.arrow here — it may be from a different sequence.
        val withArrow = s
        val frame = AmapFrameBuilder.buildGuidanceFrame(withArrow, byd, road, segOverride, hasDist) ?: return
        send(ctx, frame)
        // D4 (closeout 1.28): log-on-change — the ~4/sec heartbeat re-emits identical frames; only Log.i when the
        // emitted icon|seg|road changes. Kills per-frame spam, keeps a low-rate signal (W/E logs stay
        // unconditional). sendFrame runs single-threaded on the main looper (Handler) → no lock needed.
        val emitIcon = frame.getIntExtra("NEW_ICON", -1)
        val emitSeg = frame.getIntExtra("SEG_REMAIN_DIS", -1)
        val emitRoad = frame.getStringExtra("NEXT_ROAD_NAME")
        val emitKey = "$emitIcon|$emitSeg|$emitRoad"
        if (emitKey != lastEmitLaneKey) {
            lastEmitLaneKey = emitKey
            Log.i(TAG, "emit lane icon=$emitIcon seg=$emitSeg raw='${s.distance}' road='$emitRoad' byd=$byd")
        }
    }

    /** HUD remains UNKNOWN in T7: record request/source truth and perform zero physical writes. */
    fun emitHud(@Suppress("UNUSED_PARAMETER") ctx: Context, @Suppress("UNUSED_PARAMETER") s: NavState) {
        hudController.requestEnabled(true)
        emissionArbiter.latestToken()?.let(hudController::onCanonicalFrame)
    }

    fun stopHud(@Suppress("UNUSED_PARAMETER") ctx: Context) {
        hudController.onOutputDisabled()
    }

    @Deprecated("Use stopHud")
    fun onHudOff(ctx: Context) = stopHud(ctx)

    /** Stop Cluster lane only; HUD physical ownership is untouched. */
    fun stopLane(ctx: Context) {
        val (source, session) = synchronized(sourceLock) {
            if (sessionSerial == 0L) sessionSerial = 1L
            val stoppedSource = selectedSource ?: "legacy-navigation"
            val stoppedSession = "amap-$sessionSerial"
            selectedSource = null
            sourceSequence = 0L
            sessionSerial++
            stoppedSource to stoppedSession
        }
        emissionArbiter.stop(source, session, LanePayload(ctx.applicationContext, NavState(), false))
        Log.i(TAG, "stop lane")
    }

    fun stop(ctx: Context) {
        stopLane(ctx)
        stopHud(ctx)
        Log.i(TAG, "stop navigation")
    }

    private fun handleEmission(emission: AmapEmission<LanePayload>) {
        val payload = emission.payload
        when (emission.kind) {
            AmapEmissionKind.SESSION_RESET -> {
                lastCleanRoad = ""
                roadShownAtMs = 0L
                lastEmitLaneKey = null
                sendResetFrames(payload.context)
            }
            AmapEmissionKind.SOURCE_FRAME -> {
                val cleanRoad = NavFormat.cleanRoadName(payload.state.road)
                if (cleanRoad != lastCleanRoad) {
                    lastCleanRoad = cleanRoad
                    roadShownAtMs = SystemClock.elapsedRealtime()   // I2: reset mốc marquee → cuộn lại từ đầu tên mới
                }
                sendFrame(payload.context, payload.state, payload.byd)
                hudController.onCanonicalFrame(emission.token)
            }
            AmapEmissionKind.HEARTBEAT -> {
                sendFrame(payload.context, payload.state, payload.byd)
                hudController.onCanonicalFrame(emission.token)
            }
            AmapEmissionKind.FORCED_REPLAY -> {
                sendFrame(payload.context, payload.state, payload.byd)
                hudController.onCanonicalFrame(emission.token)
            }
            AmapEmissionKind.STOP -> {
                lastCleanRoad = ""
                roadShownAtMs = 0L
                lastEmitLaneKey = null
                TurnDistanceInterpolator.reset()
                SourceArbiter.clear()
                sendResetFrames(payload.context)
                hudController.onStop()
            }
        }
    }

    /** Field-proven Amap reset pair; only the emission-arbiter sink may call this sender. */
    private fun sendResetFrames(ctx: Context) {
        send(ctx, AmapFrameBuilder.buildStateFrame(
            AmapFrameBuilder.KEY_TYPE_STATE, AmapFrameBuilder.STATE_STOP, true,
        ))
        send(ctx, AmapFrameBuilder.buildStateFrame(
            AmapFrameBuilder.KEY_TYPE_STATE, AmapFrameBuilder.STATE_STOP, false,
        ))
        Log.i(TAG, "Amap reset/stop 10019 STATE=9 true→false")
    }

    private fun send(ctx: Context, intent: Intent) {
        runCatching { ctx.sendBroadcast(intent) }
            .onFailure { Log.e(TAG, "sendBroadcast failed", it) }
    }
}
