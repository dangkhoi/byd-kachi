package com.byd.clusternav.modules.clustercast

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Deep-rescue WIRING contract (owner 2026-08-11 — DashCast conflict clean). The action is pure Android
 * glue (AlertDialog, shell I/O, stopService) so — like [BubbleGestureContractTest] — it is locked by
 * reading the source. On-car proof (recovering an actual DashCast jam) is noted for the owner; the
 * conflict is NOT reproduced off-car (reflash risk).
 *
 * ⚠ 2026-09-13 (S3 · R3): `CastDeepRescueAction` (bản của màn ClusterNav cũ) đã xoá cùng màn đó. Việc này nay
 * có **một** đường duy nhất: `ClusterNavBridge.deepRescue` + nút ở nhóm *Chiếu cụm*, còn câu tổng kết (GỠ
 * DashCast + tắt/mở lại nguồn) do tầng Settings ghép từ tài nguyên — nên bài đọc ba tệp đó thay vì lớp cũ.
 */
class CastDeepRescueContractTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun source(relative: String): String =
        app("src/main/java/com/byd/clusternav/$relative").toFile().readText()

    private val castBridge by lazy { source("launcher/ClusterNavBridgeCast.kt") }

    /**
     * CHỈ thân `deepRescue` — không phải cả tệp. Tệp cầu còn giữ đường cứu hộ THƯỜNG (`restoreCluster`, CÓ mở
     * lại chiếu sau 2 s); quét cả tệp thì assert "không được mở lại" luôn xanh vì nó thấy `openProjection` của
     * hàm kia — đúng kiểu "quét tràn = test giả" mà `SourceRoots.body` sinh ra để chặn.
     */
    private val action by lazy {
        com.byd.clusternav.testsupport.SourceRoots.body(
            com.byd.clusternav.testsupport.KotlinSource.stripComments(castBridge),
            "fun ClusterNavBridge.deepRescue(",
        )
    }
    private val section by lazy { source("launcher/SettingsSectionsCast.kt") }
    private val stringsVi by lazy { app("src/main/res/values/strings_kachi.xml").toFile().readText() }
    private val stringsEn by lazy { app("src/main/res/values-en/strings_kachi.xml").toFile().readText() }

    @Test
    fun `force-stops DashCast and the xdja cluster helper`() {
        assertTrue(action.contains("am force-stop"), "force-stops the conflicting app")
        // Hai gói nằm ở hằng dùng chung `CAST_CONFLICT_PACKAGES` (cùng tệp) — chuyển về đó 2026-09-13 khi
        // `CastDeepRescueAction` bị xoá, để chỉ còn MỘT danh sách.
        assertTrue(action.contains("CAST_CONFLICT_PACKAGES"), "dùng hằng dùng chung, không chép tay")
        assertTrue(castBridge.contains("com.byd.dashcast"), "targets DashCast")
        assertTrue(castBridge.contains("com.xdja.clusterdemo"), "targets the xdja cluster helper")
    }

    @Test
    fun `stands our own cast fully down and does NOT reopen the projection`() {
        assertTrue(action.contains("SimpleCastIntent.Stop()"), "stops our own cast")
        assertTrue(action.contains("closeProjection()"), "closes our projection")
        assertTrue(action.contains("app.stopService(Intent(app, FloatingBubbleService"), "stops our own bubble service")
        assertTrue(!action.contains("openProjection"), "does NOT reopen (leave the cluster on native gauges)")
    }

    @Test
    fun `resets the cluster virtual display to defaults`() {
        // 2026-09-15 R2 (regression cast rơi slot-VD display 1): deepRescue KHÔNG được dùng parser thô nữa — phải qua
        // ClusterDisplayResolver.resolve(out, selfPackage) có owner-guard (cụm không bao giờ là VD của chính launcher),
        // và chỉ reset khi id >= 1 (không bao giờ display 0 = màn giữa).
        assertTrue(action.contains("ClusterDisplayResolver.resolve("), "targets the cluster VD id VIA the owner-guarded resolver")
        assertTrue(action.contains("BuildConfig.APPLICATION_ID"), "owner-guard: passes own package so a launcher-owned slot VD is never treated as the cluster")
        assertTrue(!action.contains("DisplayParse.clusterDisplayId"), "raw parser bypasses the owner-guard — forbidden here")
        assertTrue(action.contains("if (vd >= 1)"), "resets only a real cluster id (>= 1) — never display 0 / never -1")
        assertTrue(
            action.contains("wm size reset") && action.contains("wm density reset") && action.contains("wm overscan reset"),
            "resets VD size/density/overscan",
        )
    }

    @Test
    fun `is honest — never promises firmware-level recovery`() {
        // Câu tổng kết nay là tài nguyên (cầu không mang chữ — xem KDoc BridgeMsg), nên bài đọc CẢ HAI bản dịch:
        // bỏ sót một bản là chỉ người dùng ngôn ngữ kia mất lời cảnh báo, và không ai soát bản đó thấy.
        assertTrue(stringsEn.contains("power-cycle"), "final message tells the user to power-cycle (EN)")
        assertTrue(stringsVi.contains("tắt/mở lại nguồn xe"), "final message tells the user to power-cycle (VI)")
        assertTrue(stringsEn.contains("UNINSTALL DashCast"), "tells the user to uninstall DashCast (EN)")
        assertTrue(stringsVi.contains("GỠ DashCast"), "tells the user to uninstall DashCast (VI)")
    }

    @Test
    fun `the cast settings group carries the deep-rescue button behind a confirmation`() {
        assertTrue(section.contains("bridge.deepRescue("), "the Cluster-cast group calls the bridge action")
        assertTrue(section.contains("SettingsDialogs.confirm("), "asks again — force-stopping other apps is not undoable")
        assertTrue(
            section.indexOf("R.string.kachi_cast_deep_confirm") > 0,
            "the confirmation says what is about to happen (resource, both languages)",
        )
    }
}
