package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.launcher.ProfileScope
import com.byd.clusternav.launcher.ProfileScopeCluster
import com.byd.clusternav.launcher.ProfileSharePolicy
import com.byd.clusternav.launcher.PrefType
import com.byd.clusternav.launcher.SettingsCatalog
import com.byd.clusternav.launcher.SettingsGroup
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * B1b · CLUSTER-RECT-OPTION (spec `docs/specs/kachi-289-field-fixes.html` §B1b · C.3 mục 12–13) — DÂY NỐI phía `:app`.
 *
 * `:app` không có Robolectric ⇒ canh MÃ đã bỏ chú thích ([SourceRoots.codeOf]); luật thuần chạy thật ở `:core`
 * (`SpeedReadoutPolicyTest`, `CastRectFrameKeyTest`, `CastRectSessionCoordinatorTest`).
 *
 * Khoá: (1) lựa chọn `cast_style` đi từ prefs theo hồ sơ vào `desiredStyle` của coordinator — quên dòng này là màn Cài đặt ghi
 * mà cụm không bao giờ đổi (CLAUDE.md §8: compile xanh ≠ chạy); (2) lớp km/h có call site, chỉ gắn lên id cụm ĐÃ XÁC MINH qua
 * `createDisplayContext`, không bao giờ display 1/0 gõ cứng (lỗi của `SpeedBadgeOverlay`); (3) màn Cài đặt chỉ ghi prefs — theme
 * không bao giờ gửi từ Cài đặt; "Áp ngay" kiểm lại trạng thái rồi mới đi `restoreCluster`; (4) khoá mới xếp loại đủ ba bảng.
 */
class ClusterRectOptionWiringContractTest {

    private fun app(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$rel")
    private val runtime by lazy { app("modules/clustercast/simplified/SimpleCastRuntime.kt") }
    private val overlay by lazy { app("modules/clustercast/ClusterSpeedReadoutOverlay.kt") }
    private val service by lazy { app("modules/clustercast/FloatingBubbleService.kt") }
    private val section by lazy { app("launcher/SettingsSectionsCastStyle.kt") }
    private val castSection by lazy { app("launcher/SettingsSectionsCast.kt") }
    private val bridge by lazy { app("launcher/ClusterNavBridgeCastStyle.kt") }

    @Test
    fun `lua chon cast_style vao coordinator va doc khong nem`() {
        val create = SourceRoots.body(runtime, "private fun create(app: Context): SimpleCastCoordinator {")
        // Review 2.89 Pass 3 · cluster-r2-5 — ĐỔI GHIM có lý do: lựa chọn đi qua chốt "vẽ được km/h" (Chữ nhật không quyền vẽ ⇒ Bo tròn).
        assertTrue(create.contains("desiredStyle = { desiredStyleFor(app, prefs.castStyle()) }"), "lựa chọn phải tới được lượt mở chiếu: $create")
        val guard = SourceRoots.body(runtime, "private fun desiredStyleFor(app: Context, chosen: CastStyle): CastStyle")
        assertTrue(guard.contains("CastStyleApply.withReadout(chosen, canDrawReadout(app))") && guard.contains("Log.w("), guard)
        assertTrue(SourceRoots.body(runtime, "fun canDrawReadout(context: Context): Boolean").contains("Settings.canDrawOverlays("))
        assertTrue(create.contains("ProjectionManager(shell, recipe = recipe)"), "runtime tiêm công thức hồ sơ")
        assertFalse(Regex("""ProjectionManager\(shell\)""").containsMatchIn(runtime), "không còn ProjectionManager(shell) trơn")
        val read = SourceRoots.body(runtime, "override fun castStyle(): CastStyle")
        assertTrue(read.contains("CastStyle.parse(sp.all[\"cast_style\"] as? String)"), "đọc không ném khi sai kiểu: $read")
        val write = SourceRoots.body(runtime, "override fun setCastStyle(style: CastStyle)")
        assertTrue(write.contains("putString(\"cast_style\", style.name)"), write)
    }

    @Test
    fun `lop km-h co call site va dong cung dich vu chieu`() {
        val create = SourceRoots.body(service, "override fun onCreate()")
        assertTrue(create.contains("ClusterSpeedReadoutOverlay(applicationContext, coordinator).also { it.start() }"), create)
        val destroy = SourceRoots.body(service, "override fun onDestroy()")
        assertTrue(destroy.contains("speedReadout?.close()"), "phải gỡ cửa sổ + dừng luồng khi dịch vụ chết")
    }

    @Test
    fun `lop km-h chi gan len id cum da xac minh, khong cham, khong focus, luong rieng`() {
        listOf(
            "coordinator.speedReadoutInputs()", "SpeedReadoutPolicy.visible(", "createDisplayContext(display)",
            "dm.getDisplay(vd)", "TYPE_APPLICATION_OVERLAY", "FLAG_NOT_FOCUSABLE", "FLAG_NOT_TOUCHABLE",
            "HandlerThread(", "SpeedProvider.mpsOrNull()", "SpeedReadoutPolicy.face(", "ClusterRectLayout.SPEED_BOX",
            "onDisplayRemoved", "removeStateListener", "thread.quitSafely()",
        ).forEach { assertTrue(overlay.contains(it), "lớp km/h thiếu `$it`") }
        listOf("getDisplay(1)", "getDisplay(0)", "DISPLAY_CATEGORY_PRESENTATION", "CLUSTER_DISPLAY_ID", "Thread.sleep(")
            .forEach { assertFalse(overlay.contains(it), "lớp km/h không được `$it` (display đoán / chặn luồng)") }
        val step = SourceRoots.body(overlay, "private fun step(): Boolean")
        assertTrue(step.indexOf("SpeedReadoutPolicy.visible(") < step.indexOf("SpeedProvider.mpsOrNull()"),
            "chỉ đọc HAL khi đang hiện")
        assertFalse(overlay.contains("lastGoodKmh = 0") || overlay.contains("?: 0"), "không bao giờ giả 0")
    }

    /**
     * Review 2.89 Pass 3 · cluster-r2-4 — luật "không giữ số cũ" và gỡ khi trạng thái đổi KHÔNG chờ luồng HAL: canh gác chạy trên
     * LUỒNG CHÍNH (`main.postDelayed(watchdog`), quyết bằng `SpeedReadoutPolicy.watchdog` (thuần, `:core`), không gọi HAL; bộ nghe
     * trạng thái kiểm luật hiện trên luồng chính trước khi xếp một nhịp worker. Thử ĐỎ: bỏ `main.post { checkVisible() }`.
     */
    @Test
    fun `Pass 3 - canh gac luong chinh tach khoi luong HAL`() {
        val dog = SourceRoots.body(overlay, "private val watchdog = object : Runnable {")
        assertTrue(dog.contains("SpeedReadoutPolicy.watchdog(lastGoodAtMs, lastRenderAtMs, SystemClock.elapsedRealtime())"), dog)
        assertTrue(dog.contains("SpeedReadoutPolicy.NO_VALUE, dim = true") && dog.contains("detach("), dog)
        assertFalse(dog.contains("SpeedProvider") || dog.contains("worker."), "canh gác không chạm HAL / luồng worker")
        val listener = SourceRoots.body(overlay, "private val stateListener: (SimpleCastState) -> Unit = { _ ->")
        assertTrue(listener.indexOf("main.post { checkVisible() }") in 0 until listener.indexOf("worker.post { step() }"), listener)
        assertFalse(SourceRoots.body(overlay, "private fun checkVisible()").contains("SpeedProvider"))
        assertTrue(SourceRoots.body(overlay, "private fun render(").contains("lastRenderAtMs = SystemClock.elapsedRealtime()"))
        assertTrue(SourceRoots.body(overlay, "private fun attachOrThrow(").contains("main.postDelayed(watchdog, SpeedReadoutPolicy.TICK_MS)"))
        assertTrue(SourceRoots.body(overlay, "private fun detach(").contains("main.removeCallbacks(watchdog)"))
        assertTrue(overlay.contains("@Volatile private var lastGoodAtMs"), "đọc chéo luồng")
    }

    /** Review 2.89 Pass 3 · cluster-r2-5 — Cài đặt nói lý do khi chọn Chữ nhật mà chưa có quyền vẽ (lượt mở dùng Bo tròn). */
    @Test
    fun `Pass 3 - Cai dat noi ly do Chu nhat ha ve Bo tron`() {
        val rebuild = SourceRoots.body(section, "fun rebuild()")
        assertTrue(rebuild.contains("if (!bridge.castStyleReadoutDrawable()) box.addView(rows.note(context.getString(R.string.kachi_cast_style_rect_no_overlay)))"))
        assertTrue(SourceRoots.body(bridge, "fun ClusterNavBridge.castStyleReadoutDrawable(): Boolean").contains("SimpleCastRuntime.canDrawReadout(app)"))
    }

    @Test
    fun `man Cai dat chi ghi prefs, an khi doi xe khong cho, Ap ngay kiem lai trang thai`() {
        assertTrue(castSection.contains("styleBlock.build(body)") && castSection.contains("styleBlock.rebuild()"),
            "hàng phải được DỰNG trong nhóm Chiếu cụm và dựng lại theo trạng thái")
        val rebuild = SourceRoots.body(section, "fun rebuild()")
        assertTrue(rebuild.indexOf("bridge.castStyleOffered()") in 0 until rebuild.indexOf("rows.chipRow("), "ẩn hẳn khi đời xe không cho")
        listOf("bridge.setCastStyle(", "R.string.kachi_cast_style_rect_note", "R.string.kachi_cast_style_apply_note",
            "bridge.castStyleApplyOffered()", "bridge.applyCastStyleNow()")
            .forEach { assertTrue(section.contains(it), "màn thiếu `$it`") }
        listOf(section, bridge).forEach { src ->
            listOf("executeShell", "service call", "i32 ", "openProjection(", "dispatch(").forEach {
                assertFalse(src.contains(it), "Cài đặt không được gửi lệnh tới cụm (`$it`)")
            }
        }
        val set = SourceRoots.body(bridge, "fun ClusterNavBridge.setCastStyle(style: CastStyle)")
        assertTrue(set.contains("coordinator.prefs.setCastStyle(style)") && !set.contains("restoreCluster"), "ghi = CHỈ prefs")
        val now = SourceRoots.body(bridge, "fun ClusterNavBridge.applyCastStyleNow(): Boolean")
        assertTrue(now.indexOf("CastStyleApply.stateAllows(castState())") in 0 until now.indexOf("restoreCluster()"),
            "kiểm lại trạng thái NGAY lúc bấm, rồi mới restoreCluster (lượt mở qua cổng theme)")
        assertTrue(bridge.contains("ClusterProfile.resolveCached(app).supportsStyle"), "hiện theo hồ sơ đời xe (car.type 138)")
    }

    @Test
    fun `khoa cast_style xep loai du ba bang va co muc Cai dat`() {
        assertEquals(ProfileScope.Scope.PROFILE, ProfileScope.scopeOf("cast_style"))
        assertEquals(PrefType.STRING, ProfileScopeCluster.DECLARED_TYPES["cast_style"])
        assertTrue(ProfileSharePolicy.shareable("cast_style"))
        assertEquals("simple_cast_prefs", SettingsCatalog.CLUSTERNAV_KEYS["cast_style"])
        assertEquals(SettingsGroup.CAST, SettingsCatalog.groupOf("cast_style"))
    }

    /** CLAUDE.md §8 — mọi hàm mới của B1b có ÍT NHẤT một call site ngoài định nghĩa (quét mã đã bỏ chú thích cả hai module). */
    @Test
    fun `ham moi deu co call site`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.joinToString("\n") { p ->
            p.toFile().readText().replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
                .lines().joinToString("\n") { it.substringBefore("//") }
        }
        mapOf(
            "applySessionPin(" to "fun SimpleCastCoordinator.applySessionPin(",
            "verifyFrame(" to "fun verifyFrame(",
            "taskBoundsOn(" to "fun taskBoundsOn(",
            "speedReadoutInputs()" to "fun SimpleCastCoordinator.speedReadoutInputs()",
            "ClusterRectLayout.pin(" to "",
            "ClusterRectLayout.splitFrames(" to "",
            "ClusterRectLayout.slotFrame(" to "",
            "CastStyleApply.offer(" to "",
            "CastStyleApply.withReadout(" to "",
            "castStyleReadoutDrawable()" to "fun ClusterNavBridge.castStyleReadoutDrawable()",
            "SpeedReadoutPolicy.watchdog(" to "",
            "ClusterRectLayout.editFrame(" to "",
            "castStyleApplyOffered()" to "fun ClusterNavBridge.castStyleApplyOffered()",
            "applyCastStyleNow()" to "fun ClusterNavBridge.applyCastStyleNow()",
            "castStyleSession()" to "fun ClusterNavBridge.castStyleSession()",
            "SettingsCastStyleBlock(" to "class SettingsCastStyleBlock(",
            "ClusterSpeedReadoutOverlay(" to "class ClusterSpeedReadoutOverlay(",
            ".recordKey(" to "",
        ).forEach { (call, def) ->
            val n = Regex(Regex.escape(call)).findAll(all).count() - (if (def.isEmpty()) 0 else Regex(Regex.escape(def)).findAll(all).count())
            assertTrue(n >= 1, "`$call` không có call site ngoài định nghĩa (CLAUDE.md §8)")
        }
    }
}
