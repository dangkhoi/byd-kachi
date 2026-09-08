package com.byd.clusternav

import com.byd.clusternav.navigation.Maneuver
import com.byd.clusternav.testsupport.KotlinSource
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * I1 (1.14) contract. INSTRUMENT_GUIDE_INFO_SIMPLE_SET (the windshield-HUD simple-nav feature) reads the
 * CAN turn-id table (Maneuver.toHudIcon: left=1, right=2), NOT the AMAP NEW_ICON table (Maneuver.toAmapIcon:
 * left=2, right=3 — used by the cluster lane via the AUTONAVI broadcast). Feeding the AMAP code into the
 * CAN feature shifts every turn by one enum slot → the HUD renders left↔right mirrored (owner report:
 * "rẽ trái → rẽ phải", 100%). The cluster stays correct because it is driven by the separate broadcast.
 *
 * Runtime needs Android, so — like the other boundary tests — this locks the wiring by reading the source.
 */
class HudManeuverEncodingTest {

    private fun app(rel: String): Path {
        val cur = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(cur.resolve("src"))) cur.resolve(rel) else cur.resolve("app").resolve(rel)
    }

    private val navRepo by lazy {
        app("src/main/java/com/byd/clusternav/NavRepository.kt").toFile().readText()
    }

    /**
     * Ý ĐỊNH KHOÁ: icon HUD phải mã hoá qua bảng CAN ([Maneuver.toHudIcon], trái=1/phải=2), KHÔNG qua bảng
     * AMAP NEW_ICON (bảng đó đảo trái/phải trên HUD).
     *
     * 08-23: quyết định icon quay lại NẰM TRONG `NavRepository` sau khi gỡ `CaptureArrowFallback` — nên test
     * cũng quay về bám một file, đúng như trước 08-22.
     */
    @Test
    fun `HUD write encodes via CAN toHudIcon, not the AMAP maneuverCode`() {
        assertTrue(
            navRepo.contains("frame.content.maneuver?.toHudIcon() ?: 11"),
            "the HUD icon must encode via Maneuver.toHudIcon() (CAN turn-id table), fallback 11 = straight",
        )
        // Quét đúng DÒNG quyết định icon HUD (không quét cả file: `toNavState()` dùng `toAmapIcon()` cho LÀN
        // cụm qua broadcast AUTONAVI — đó là bảng ĐÚNG cho làn, chỉ sai nếu chảy vào HUD).
        val hudIconLine = navRepo.lineSequence().first { it.contains("val hudIcon =") }
        assertFalse(
            hudIconLine.contains("toAmapIcon()"),
            "the HUD icon decision must NOT use the AMAP NEW_ICON table (it mirrors L/R on the HUD)",
        )
        assertFalse(
            navRepo.contains("icon = frame.content.maneuverCode"),
            "owner.push must NOT feed the AMAP maneuverCode into the CAN HUD feature (that mirrors L/R)",
        )
    }

    /**
     * KHOÁ HỒI QUY (08-23) — **đường notification KHÔNG mượn gì từ kênh screen-capture**.
     *
     * VÌ SAO PHẢI KHOÁ, chứ không chỉ xoá code: cơ chế mượn (`CaptureArrowFallback`, 08-22) trông rất hợp lý
     * trên giấy nên rất dễ được thêm lại. Phản biện đã chứng minh nó KHÔNG BAO GIỜ bắn được cho VietMap —
     * nhánh mượn chỉ chạy trên một frame notification, mà đúng lúc đó `NavNotificationListener` vừa gọi
     * `SourceArbiter.shouldFeed` (kênh DATA) ⇒ kênh IMAGE của cùng app bị chặn ⇒ `ScreenCaptureSignal.arrow`
     * không có gì để mượn — và ép nó chạy được thì HAI owner cùng ghi INSTRUMENT_GUIDE_INFO_SIMPLE_SET.
     * Owner chốt VietMap đi HẲN đường screen-capture (08-23).
     *
     * Test này ĐỎ = ai đó vừa nối lại kênh ảnh vào đường notification GMaps — đường đang chạy NGOÀI HIỆN
     * TRƯỜNG (CLAUDE.md §6). Muốn làm thì phải có spec, không phải một bản vá.
     */
    @Test
    fun `duong notification KHONG doc kenh screen-capture`() {
        assertFalse(navRepo.contains("CaptureArrowFallback"), "cơ chế mượn mũi tên-capture đã gỡ, không thêm lại")
        assertFalse(
            stripComments(navRepo).contains("ScreenCaptureSignal"),
            "NavRepository (đường notification) không được đọc tín hiệu kênh ảnh",
        )
        // Và nguồn duy nhất của mũi tên vẫn là chính notification (hoặc số lối ra vòng xuyến).
        assertTrue(
            navRepo.contains("val hudIcon = if (exitN in 1..10) 24 + exitN else frame.content.maneuver?.toHudIcon() ?: 11"),
            "icon HUD chỉ đến từ maneuver của notification / số lối ra vòng xuyến",
        )
    }

    // §4.1 DRY + FAIL-OPEN: bản regex chép tay ở đây (2 bản) coi `//` trong string literal là comment ⇒
    // nuốt luôn phần thi hành đứng sau trên cùng dòng ⇒ guard mù mà vẫn xanh. Đã chuyển sang scanner
    // có trạng thái dùng chung, có test riêng (`KotlinSourceTest`).
    private fun stripComments(src: String): String = KotlinSource.stripComments(src)

    /**
     * Track B (2026-08-14): the enriched Maneuver values must encode to the correct CAN turn-id on the HUD
     * (values from RE docs/diagnostics/re-maneuver-icon-tables-2026-08-14.md §2 = TurnIdMapToCAN[toAmapIcon]).
     * The HUD write path (asserted above) feeds these through Maneuver.toHudIcon(), so locking the codes here
     * guards the windshield-HUD glyph for every new maneuver the classifiers can now emit.
     */
    @Test
    fun `Track B new maneuver values encode to the correct CAN HUD ids`() {
        assertEquals(11, Maneuver.MERGE.toHudIcon())            // no merge glyph → straight
        assertEquals(3, Maneuver.RAMP_LEFT.toHudIcon())         // ramp ≈ slight-left
        assertEquals(5, Maneuver.RAMP_RIGHT.toHudIcon())
        assertEquals(3, Maneuver.FORK_LEFT.toHudIcon())
        assertEquals(5, Maneuver.FORK_RIGHT.toHudIcon())
        assertEquals(3, Maneuver.KEEP_LEFT.toHudIcon())
        assertEquals(5, Maneuver.KEEP_RIGHT.toHudIcon())
        assertEquals(10, Maneuver.UTURN_RIGHT.toHudIcon())      // U-turn right (CAN 10)
        assertEquals(24, Maneuver.ROUNDABOUT_EXIT.toHudIcon())  // drive out of roundabout (CAN 24)
        assertEquals(49, Maneuver.TUNNEL.toHudIcon())           // enter tunnel (CAN 49)
        assertEquals(46, Maneuver.SERVICE_AREA.toHudIcon())     // service area (CAN 46)
        assertEquals(47, Maneuver.TOLL.toHudIcon())             // toll station (CAN 47)
        assertEquals(45, Maneuver.WAYPOINT.toHudIcon())         // waypoint arrival (CAN 45)
    }
}
