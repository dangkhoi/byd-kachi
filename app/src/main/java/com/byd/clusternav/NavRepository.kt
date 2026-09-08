package com.byd.clusternav

import com.byd.clusternav.navigation.NavParse
import com.byd.clusternav.navigation.SourceArbiter
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.byd.clusternav.navigation.ClusterLaneAdapter
import com.byd.clusternav.navigation.HudAdapter
import com.byd.clusternav.navigation.InteractionContext
import com.byd.clusternav.navigation.Maneuver
import com.byd.clusternav.navigation.NavContentBuilder
import com.byd.clusternav.navigation.NavFormat
import com.byd.clusternav.navigation.NavigationFrame
import com.byd.clusternav.navigation.NavigationFrameContent
import com.byd.clusternav.navigation.NavigationFrameDelivery
import com.byd.clusternav.navigation.NavigationFramePersistence
import com.byd.clusternav.navigation.NavigationOutputTarget
import com.byd.clusternav.navigation.NavigationPermission
import com.byd.clusternav.navigation.NavigationSessionCoordinator
import com.byd.clusternav.navigation.NavigationSourceIdentity
import com.byd.clusternav.navigation.NavigationUiState
import com.byd.clusternav.navigation.PersistentNavigationFrameStore
import com.byd.clusternav.navigation.StoredNavigationSession
import java.util.concurrent.CopyOnWriteArrayList

/** Authoritative Navigation runtime facade. UI consumers remain read-only observers. */
object NavRepository {
    @Volatile var state: NavState = NavState()
        private set

    private val listeners = CopyOnWriteArrayList<(NavState) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    @Volatile private var coordinator: NavigationSessionCoordinator? = null

    /** Owns the cluster center-nav HAL writer (Giữa+ETA). Created with the coordinator; stopped on nav stop. */
    @Volatile private var hudOwner: NavigationHudOwner? = null

    private const val TAG = "NavRepository"

    fun connect(context: Context, permission: NavigationPermission): NavigationSessionCoordinator = synchronized(lock) {
        coordinator ?: createCoordinator(context.applicationContext).also { runtime ->
            coordinator = runtime
            runtime.setPermission(permission)
            runtime.rehydrate()
            runtime.setOutputEnabled(NavigationOutputTarget.CLUSTER_LANE, Prefs.lane(context))
            runtime.setOutputEnabled(NavigationOutputTarget.HUD, Prefs.hud(context))
        }
    }

    fun setPermission(context: Context, permission: NavigationPermission) {
        connect(context, permission).setPermission(permission)
    }

    /**
     * CỬA VÀO của đường THÔNG BÁO (Google Maps). Vỏ mỏng: dựng khung bằng [NavContentBuilder.fromNotification]
     * (phép DỜI CHỖ nguyên văn của biểu thức từng nằm ở đây — CLAUDE.md §6, khoá bằng `GmapsContentGoldenTest`)
     * rồi đưa vào phễu qua [ingestFrame].
     *
     * `publish` ở đây đẩy CHÍNH `NavState` gốc (nó mang bitmap mũi tên `arrow` mà `ClusterBroadcaster` đọc).
     */
    fun ingest(context: Context, packageName: String, displayName: String?, value: NavState) {
        ingestFrame(
            context, packageName, displayName,
            NavContentBuilder.fromNotification(
                maneuverIcon = value.maneuverIcon,
                maneuverText = value.maneuverText,
                distance = value.distance,
                road = value.road,
                eta = value.eta,
                maneuver = value.maneuver,
            ),
        )
        publish(value)
    }

    /**
     * DỪNG PHIÊN **chỉ khi** [packageName] đúng là nguồn đang giữ phiên. Trả `true` nếu đã dừng.
     *
     * Vì sao không nhả vô điều kiện (bài học F1 — gỡ một kênh chết kéo theo thứ nó gánh hộ): [stop] còn kéo
     * theo `ClusterBroadcaster.stop` + `hudOwner.stop()` + `SourceArbiter.clear()`.
     */
    fun stopIfSource(context: Context, packageName: String): Boolean {
        val runtime = synchronized(lock) { coordinator } ?: return false
        val current = runtime.snapshot().source
        if (current.sessionId == null || current.identity?.packageName != packageName) return false
        stop(context)
        return true
    }

    /** Phần dùng CHUNG của hai cửa vào: nối coordinator → mở/chuyển phiên → nhận khung. */
    private fun ingestFrame(
        context: Context,
        packageName: String,
        displayName: String?,
        content: NavigationFrameContent,
    ): NavigationFrame {
        val runtime = connect(context, NavigationPermission.GRANTED)
        val source = NavigationSourceIdentity(packageName, displayName?.takeIf(String::isNotBlank))
        val current = runtime.snapshot().source
        if (current.sessionId == null || current.identity != source) runtime.startSession(source)
        return runtime.acceptFrame(source, content)
    }

    fun setOutputEnabled(context: Context, target: NavigationOutputTarget, enabled: Boolean) {
        connect(context, permission()).setOutputEnabled(target, enabled)
        if (!enabled && target == NavigationOutputTarget.HUD) ClusterBroadcaster.stopHud(context)
        if (target == NavigationOutputTarget.CLUSTER_LANE) {
            // Cluster center-nav HAL owner follows the cluster-lane toggle (same surface concern).
            if (enabled) hudOwner?.start() else { ClusterBroadcaster.stopLane(context); hudOwner?.stop() }
        }
    }

    fun stop(context: Context) {
        synchronized(lock) { coordinator }?.stopSession()
        ClusterBroadcaster.stop(context)
        hudOwner?.stop()
        SourceArbiter.clear()
        publish(NavState())
    }

    /** I4 (1.14): áp NGAY chế độ hiển thị cụm vừa đổi ở UI (re-assert qua owner). No-op nếu chưa có phiên/owner. */
    fun reapplyClusterMode(context: Context) {
        // Bề mặt cụm chỉ dựng khi Cast OFF (invariant tách 08-24, [P2] review) — truyền navOnlyMode xuống reapply.
        hudOwner?.reapply(navOnlyMode(context))
    }

    fun snapshot(context: Context, interaction: InteractionContext = InteractionContext.UNKNOWN): NavigationUiState =
        connect(context, permission()).also { it.refreshFreshness() }.snapshot(interaction)

    /** Compatibility for demo-only UI; production notification ingestion uses [ingest]. */
    fun update(value: NavState) = publish(value)
    fun clear() = publish(NavState())
    fun hasListeners(): Boolean = listeners.isNotEmpty()
    fun addListener(listener: (NavState) -> Unit) { listeners += listener; main.post { listener(state) } }
    fun removeListener(listener: (NavState) -> Unit) { listeners -= listener }

    private fun createCoordinator(context: Context): NavigationSessionCoordinator {
        val appCtx = context.applicationContext
        val owner = NavigationHudOwner(appCtx).also { it.start() }
        hudOwner = owner
        val lane = ClusterLaneAdapter(NavigationFrameDelivery { frame ->
            // TASK 6 (closeout 1.28) — naviState prime: VERIFIED NO-GAP, KHÔNG cần prime broadcast riêng.
            // Thứ tự per-frame ĐÃ là broadcast-first + naviState=1 LATCH, chứng minh bằng code (không đoán):
            //  • emitLane() dưới đây phát broadcast AUTONAVI (EXTRA_STATE=1) ĐỒNG BỘ ngay trên luồng delivery —
            //    AmapEmissionArbiter.sourceFrame gọi thẳng sink→handleEmission→sendFrame→sendBroadcast; CHỈ nhịp
            //    tim 400ms mới postDelayed qua main Handler, KHÔNG phải frame đầu lúc boot.
            //  • lệnh push HAL bên dưới CHỈ enqueue frame vào worker đơn-luồng "hud-hal-delivery"
            //    (ThreadPoolExecutor.execute) → HAL writeNavFrame chạy SAU trên luồng worker.
            // ⇒ sendBroadcast(naviState=1) happens-before HAL frame được submit, MỌI frame kể cả frame đầu.
            // naviState=1 là cờ session latch (giữ tới STOP) + heartbeat 400ms re-gửi, nên transient cross-process
            // OEM (nếu có) tự khỏi trong 1 nhịp. KHÔNG bịa 'open' broadcast (no-assumptions: ngữ nghĩa broadcast
            // OEM chưa đo được → không đoán). Chi tiết verify: docs/specs/clusternav-closeout-1.28.html §TASK 6.
            // IS_BYD_MAP=true (OpenBYD-style, 2026-08-16): OpenBYD gửi broadcast IS_BYD_MAP=true (nav "BYD map") + ghi
            // HAL guidance liên tục — GIẢ THUYẾT combo này là trigger để HUD kính lái mirror nav (M1 lâu nay). RE §3:
            // IS_BYD_MAP=true+TYPE=1 VẪN render cụm (không hỏng cụm strip). Probe — revert 1 dòng nếu on-car hỏng.
            ClusterBroadcaster.emitLane(context, frame.toNavState(), byd = true)
            // TÁCH nội dung / bề mặt (2026-08-24, docs/diagnostics/nav-io-asis-2026-08-24.html §B; owner chốt OQ1/OQ4):
            //  • NỘI DUNG dẫn (icon/cự ly/đường/ETA/oversea/SDK + SEND_NAVI_STATUS latch) = thứ HUD KÍNH đọc →
            //    ghi VÔ ĐIỀU KIỆN theo nguồn đã chọn (SourceArbiter), ĐỘC LẬP Cast (HUD là bề mặt riêng; owner đo
            //    GMaps đang cast vẫn lên HUD). ⇒ owner.push LUÔN chạy, KHÔNG còn gate navOnlyMode.
            //  • BỀ MẶT CỤM "Giữa+ETA" (SET_NAVI_SCREEN_STATUS) tranh display cụm với Cast ⇒ chỉ dựng khi
            //    navOnlyMode (Cast OFF) — truyền writeSurface = navOnlyMode(appCtx) xuống writeNavFrame.
            //  Cast OFF ⇒ writeSurface=true ⇒ ghi ĐỦ content+surface = HÀNH VI CŨ (0 hồi quy, khoá bằng test).
            //  Cast ON  ⇒ writeSurface=false ⇒ chỉ content (HUD lên), cụm để Cast chiếm. Broadcast dải làn KHÔNG đổi.
            run {
                // I1 (1.14): đường HUD (INSTRUMENT_GUIDE_INFO_SIMPLE) đọc bảng CAN (toHudIcon: trái=1, phải=2), KHÔNG
                // phải AMAP (broadcast/cụm ở emitLane). Fallback 11 = đi thẳng.
                // Vòng xuyến CÓ số lối ra (text "lối ra thứ N") → ÉP HUD icon = CAN 24+N (25..34 = vòng-xuyến-lối-ra-N,
                // CCW/RHT) để HUD hiện SỐ nhánh — mirror cụm (AmapFrameBuilder ép NEW_ICON 11 + ROUNG_ABOUT_NUM). icon
                // này chảy vào CẢ 2 họ (domestic 0x43F01010 + oversea 0x1F701010) trong writeNavFrame. Frame khác: toHudIcon.
                val exitText = frame.content.maneuverText?.takeIf { it.isNotBlank() } ?: frame.content.roadName.orEmpty()
                val exitN = NavFormat.roundaboutExit(exitText)
                // 08-23 — GỠ "mượn mũi tên từ screen-capture" (thêm 08-22, sống đúng một ngày). Khung
                // notification chỉ dùng mũi tên của CHÍNH notification; không có thì về 11 = đi thẳng, y như
                // hành vi đã chạy ngoài hiện trường trước 08-22.
                //
                // VÌ SAO GỠ (phản biện đã chứng minh từ source, không phải cảm tính):
                //  1. Nhánh mượn KHÔNG BAO GIỜ bắn được cho VietMap — ca duy nhất nó sinh ra để chữa. Nó chỉ
                //     chạy TRÊN một frame notification, mà đúng lúc đó `NavNotificationListener` vừa gọi
                //     `SourceArbiter.shouldFeed(pkg, …)` (kênh DATA mặc định) ⇒ `lastDataByPkg[pkg]` vừa được
                //     đóng mốc ⇒ mọi publish kênh IMAGE của `ScreenCaptureNavSource` bị chặn
                //     (`SourceArbiter.shouldFeed` — IMAGE thua DATA còn tươi) ⇒ `ScreenCaptureSignal.arrow`
                //     rỗng hoặc cũ ⇒ `hudIcon` luôn rơi về 11. Code chết mang hình dạng tính năng.
                //  2. Ép nó chạy được (mở kênh IMAGE cho app đang có DATA tươi) sẽ đánh thức `NavOutputOwner.tick`
                //     ⇒ HAI owner cùng ghi INSTRUMENT_GUIDE_INFO_SIMPLE_SET trên xe đang chạy. Đó là quyết định
                //     kiến trúc, không phải một bản vá (CLAUDE.md §1).
                // OWNER CHỐT (08-23): VietMap đi HẲN đường screen-capture (mũi tên + cự ly cùng một kênh, cùng
                // một package), KHÔNG merge vào khung notification ⇒ cơ chế mượn không còn lý do tồn tại.
                // Xem docs/PROJECT-BACKLOG.md B3.42.
                val hudIcon = if (exitN in 1..10) 24 + exitN else frame.content.maneuver?.toHudIcon() ?: 11
                owner.push(
                    icon = hudIcon,
                    segMeters = frame.content.distanceMeters ?: -1,
                    hudRoad = frame.content.roadName.orEmpty(),
                    // FULL DATA HUD (2026-08-15): ETA + thời gian/quãng đường còn lại (writeNavFrame ghi domestic + oversea).
                    routeSeconds = frame.content.routeRemainingSeconds ?: -1,
                    routeMeters = frame.content.routeRemainingMeters ?: -1,
                    arrivalClock = frame.content.arrivalClock,
                    writeSurface = navOnlyMode(appCtx),   // nội dung LUÔN ghi; chỉ bề mặt cụm gate theo Cast OFF
                )
            }
        })
        val hud = HudAdapter(NavigationFrameDelivery { ClusterBroadcaster.emitHud(context, it.toNavState()) })
        return NavigationSessionCoordinator(
            PersistentNavigationFrameStore(PreferencesPersistence(context)), lane, hud,
        )
    }

    /** TWO-TRACK gate: cluster center-nav HAL write only when the Cast master switch is OFF (persisted flag,
     *  a user config — not live cast-control state), keeping the Navigation and Cast tracks decoupled. */
    private fun navOnlyMode(appContext: Context): Boolean = runCatching {
        !SimpleCastRuntime.coordinator(appContext).prefs.castEnabled()
    }.getOrDefault(true)

    private fun NavigationFrame.toNavState() = NavState(
        active = true,
        distance = content.distanceMeters?.let { "$it m" }.orEmpty(),
        road = content.roadName.orEmpty(),
        maneuverText = content.maneuverText.orEmpty(),
        // maneuver TRUNG LẬP là nguồn sự thật; maneuverIcon (AMAP) suy ra cho làn cụm (khứ hồi chính xác).
        maneuverIcon = content.maneuver?.toAmapIcon() ?: content.maneuverCode ?: -1,
        maneuver = content.maneuver,
        eta = buildString {
            content.arrivalClock?.let { append(it) }
            content.routeRemainingMeters?.let { m -> if (isNotEmpty()) append(" · "); append(NavParse.formatMeters(m)) }
            content.routeRemainingSeconds?.let { s -> if (isNotEmpty()) append(" · "); append(NavParse.formatSeconds(s)) }
        },
        updatedAt = receivedAtEpochMs,
    )

    private fun publish(value: NavState) {
        state = value
        main.post { listeners.forEach { it(value) } }
    }

    private fun permission(): NavigationPermission =
        if (NavNotificationListener.connected) NavigationPermission.GRANTED else NavigationPermission.UNKNOWN

    private class PreferencesPersistence(context: Context) : NavigationFramePersistence {
        private val prefs = context.getSharedPreferences("navigation_session_v2", Context.MODE_PRIVATE)
        override fun load(): StoredNavigationSession? {
            return try {
                val id = prefs.getString("sessionId", null) ?: return null
                val pkg = prefs.getString("sourcePackage", null) ?: return null
                val source = NavigationSourceIdentity(pkg, prefs.getString("sourceDisplay", null))
                val sequence = prefs.getLong("sequence", 0L)
                val frame = if (sequence > 0) NavigationFrame(
                    id, source, sequence, prefs.getLong("receivedAt", 0L),
                    NavigationFrameContent(
                        prefs.getInt("maneuverCode", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                        prefs.getString("maneuverText", null),
                        prefs.getInt("distanceMeters", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                        prefs.getString("roadName", null),
                        prefs.getLong("eta", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE },
                        prefs.getInt("routeRemainingMeters", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                        prefs.getInt("routeRemainingSeconds", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                        prefs.getString("arrivalClock", null),
                        maneuver = prefs.getString("maneuver", null)
                            ?.let { name -> runCatching { Maneuver.valueOf(name) }.getOrNull() }
                            ?: prefs.getInt("maneuverCode", Int.MIN_VALUE)
                                .takeUnless { it == Int.MIN_VALUE }?.let { Maneuver.fromAmapIcon(it) },
                    ),
                ) else null
                StoredNavigationSession(id, source, prefs.getLong("startedAt", 0L), frame)
            } catch (e: Exception) {
                // Fail closed: corrupted persistence must not crash Home. Clear and return empty.
                android.util.Log.e("NavPersistence", "load corruption — clearing", e)
                runCatching { prefs.edit().clear().commit() }
                null
            }
        }

        override fun save(session: StoredNavigationSession) {
            val frame = session.latestFrame
            check(prefs.edit().clear()
                .putString("sessionId", session.sessionId)
                .putString("sourcePackage", session.source.packageName)
                .putString("sourceDisplay", session.source.displayName)
                .putLong("startedAt", session.startedAtEpochMs)
                .putLong("sequence", frame?.sequence ?: 0L)
                .putLong("receivedAt", frame?.receivedAtEpochMs ?: 0L)
                .putInt("maneuverCode", frame?.content?.maneuverCode ?: Int.MIN_VALUE)
                .putString("maneuver", frame?.content?.maneuver?.name)
                .putString("maneuverText", frame?.content?.maneuverText)
                .putInt("distanceMeters", frame?.content?.distanceMeters ?: Int.MIN_VALUE)
                .putString("roadName", frame?.content?.roadName)
                .putLong("eta", frame?.content?.etaEpochMs ?: Long.MIN_VALUE)
                .putInt("routeRemainingMeters", frame?.content?.routeRemainingMeters ?: Int.MIN_VALUE)
                .putInt("routeRemainingSeconds", frame?.content?.routeRemainingSeconds ?: Int.MIN_VALUE)
                .putString("arrivalClock", frame?.content?.arrivalClock)
                .commit()) { "failed to persist Navigation session" }
        }

        override fun clear() { check(prefs.edit().clear().commit()) { "failed to clear Navigation session" } }
    }
}
