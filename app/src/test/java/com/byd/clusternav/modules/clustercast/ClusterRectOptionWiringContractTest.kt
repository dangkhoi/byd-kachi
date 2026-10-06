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
 * (`CastRectFrameKeyTest`, `CastRectSessionCoordinatorTest`, `ClusterRectStaleDisplayTest`, `ClusterThemeBubbleTest`).
 *
 * Khoá: (1) lựa chọn `cast_style` đi từ prefs theo hồ sơ vào `desiredStyle` của coordinator — quên dòng này là màn Cài đặt ghi
 * mà cụm không bao giờ đổi (CLAUDE.md §8: compile xanh ≠ chạy); (2) 2.90 · R3: lớp km/h ĐÃ GỠ hẳn (owner 06/10) — không quay lại;
 * (3) màn Cài đặt chỉ ghi prefs — theme không bao giờ gửi từ Cài đặt; "Áp ngay" kiểm lại trạng thái rồi mới đi `restoreCluster`;
 * (4) khoá mới xếp loại đủ ba bảng; (5) 2.90 · R1/R2/R6: Cài đặt nói lý do bóng nổi + khung trọn cụm, trang sống theo trạng thái.
 */
class ClusterRectOptionWiringContractTest {

    private fun app(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$rel")
    private val runtime by lazy { app("modules/clustercast/simplified/SimpleCastRuntime.kt") }
    private val service by lazy { app("modules/clustercast/FloatingBubbleService.kt") }
    private val section by lazy { app("launcher/SettingsSectionsCastStyle.kt") }
    private val castSection by lazy { app("launcher/SettingsSectionsCast.kt") }
    private val bridge by lazy { app("launcher/ClusterNavBridgeCastStyle.kt") }

    @Test
    fun `lua chon cast_style vao coordinator va doc khong nem`() {
        val create = SourceRoots.body(runtime, "private fun create(app: Context): SimpleCastCoordinator {")
        // 2.90 · R3 — ĐỔI GHIM có lý do: Kachi không vẽ km/h nữa ⇒ lựa chọn đi THẲNG vào lượt mở (không chốt quyền vẽ).
        assertTrue(create.contains("desiredStyle = { prefs.castStyle() }"), "lựa chọn phải tới được lượt mở chiếu: $create")
        assertFalse(runtime.contains("canDrawReadout") || runtime.contains("withReadout"), "chốt km/h đã gỡ")
        assertTrue(create.contains("ProjectionManager(shell, recipe = recipe)"), "runtime tiêm công thức hồ sơ")
        assertFalse(Regex("""ProjectionManager\(shell\)""").containsMatchIn(runtime), "không còn ProjectionManager(shell) trơn")
        val read = SourceRoots.body(runtime, "override fun castStyle(): CastStyle")
        assertTrue(read.contains("CastStyle.parse(sp.all[\"cast_style\"] as? String)"), "đọc không ném khi sai kiểu: $read")
        val write = SourceRoots.body(runtime, "override fun setCastStyle(style: CastStyle)")
        assertTrue(write.contains("putString(\"cast_style\", style.name)"), write)
    }

    /** 2.90 · R3 — lớp km/h của Kachi đã gỡ hẳn: không tệp, không dây nối, không chuỗi. Thử ĐỎ: khôi phục `ClusterSpeedReadoutOverlay`. */
    @Test
    fun `290 - khong con lop km-h cua Kachi`() {
        assertFalse(SourceRoots.moduleSourceRoots().any { Files.exists(it.resolve("com/byd/clusternav/modules/clustercast/ClusterSpeedReadoutOverlay.kt")) })
        listOf(service, section, bridge, runtime).forEach { src ->
            listOf("SpeedReadout", "speedReadout", "kachi_cast_style_rect_no_overlay").forEach {
                assertFalse(src.contains(it), "còn sót `$it`")
            }
        }
    }

    /** 2.90 · R1/R2 — Cài đặt nói: bóng nổi chặn đổi kiểu (tên app + "Áp ngay"), phiên chưa rõ kiểu ⇒ trọn cụm. */
    @Test
    fun `290 - Cai dat noi ly do bong noi va khung tron cum`() {
        val rebuild = SourceRoots.body(section, "fun rebuild()")
        assertTrue(rebuild.contains("bridge.castThemeBlockers()") && rebuild.contains("R.string.kachi_cast_style_blocked"), rebuild)
        assertTrue(rebuild.contains("fullFrame == true") && rebuild.contains("R.string.kachi_cast_style_full_frame"), rebuild)
        assertTrue(SourceRoots.body(bridge, "fun ClusterNavBridge.castThemeBlockers(): List<String>").contains("coordinator.themeBlockers"))
    }

    /**
     * 2.90 · R6 — [ĐO xe 06/10] trang đứng "Đang mở cụm…" sau khi mở xong. Trang gắn bộ nghe trạng thái khi hiện, gỡ khi rời, post về
     * luồng chính rồi mới `refreshStatus()`. Thử ĐỎ: bỏ `liveStatus(statusRow.view)`.
     */
    @Test
    fun `290 - trang Chieu cum song theo trang thai, go khi roi trang`() {
        val castNow = SourceRoots.body(castSection, "private fun castNow(body: LinearLayout)")
        assertTrue(castNow.contains("liveStatus(statusRow.view)"), castNow)
        val live = SourceRoots.body(castSection, "private fun liveStatus(anchor: View)")
        listOf("addOnAttachStateChangeListener(", "bridge.observeCastState", "v.post {", "refreshStatus()", "override fun onViewDetachedFromWindow",
            "unsubscribe?.invoke()", "statusKey()").forEach { assertTrue(live.contains(it), "thiếu `$it`: $live") }
        val obs = SourceRoots.body(app("launcher/ClusterNavBridgeCast.kt"), "fun ClusterNavBridge.observeCastState(")
        assertTrue(obs.contains("c.addStateListener(onChange)") && obs.contains("c.removeStateListener(onChange)"), obs)
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
            "ClusterRectLayout.pin(" to "",
            "ClusterRectLayout.splitFrames(" to "",
            "ClusterRectLayout.slotFrame(" to "",
            "CastStyleApply.offer(" to "",
            "ClusterRectLayout.editFrame(" to "",
            "castStyleApplyOffered()" to "fun ClusterNavBridge.castStyleApplyOffered()",
            "applyCastStyleNow()" to "fun ClusterNavBridge.applyCastStyleNow()",
            "castStyleSession()" to "fun ClusterNavBridge.castStyleSession()",
            "SettingsCastStyleBlock(" to "class SettingsCastStyleBlock(",
            "castThemeBlockers()" to "fun ClusterNavBridge.castThemeBlockers()",
            "observeCastState" to "fun ClusterNavBridge.observeCastState(",
            "liveStatus(" to "private fun liveStatus(",
            "themeBlockers" to "val SimpleCastCoordinator.themeBlockers",
            "fullFrameSession" to "val SimpleCastCoordinator.fullFrameSession",
            "newestSameName(" to "fun newestSameName(",
            "displayGone(" to "fun displayGone(",
            "ClusterBubbleApps::labelOf" to "",
            ".recordKey(" to "",
        ).forEach { (call, def) ->
            val n = Regex(Regex.escape(call)).findAll(all).count() - (if (def.isEmpty()) 0 else Regex(Regex.escape(def)).findAll(all).count())
            assertTrue(n >= 1, "`$call` không có call site ngoài định nghĩa (CLAUDE.md §8)")
        }
    }
}
