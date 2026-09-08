package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TÁCH NỘI DUNG / BỀ MẶT (2026-08-24, docs/diagnostics/nav-io-asis-2026-08-24.html §B; owner chốt OQ1/OQ4).
 *
 * Bối cảnh: HUD kính là bề mặt VẬT LÝ RIÊNG, không tranh cụm với Cast (owner đo: GMaps đang cast vẫn lên HUD).
 * Trước 2026-08-24, nội dung dẫn (thứ HUD kính đọc) bị trói chung `writeNavFrame` + gate `navOnlyMode` với
 * việc dựng BỀ MẶT cụm-centre (`SET_NAVI_SCREEN_STATUS`), nên Cast ON làm HUD kính cũng tắt. Tách:
 *  • NỘI DUNG (GUIDE_INFO_SIMPLE + cự ly + đường + oversea + SDK + SEND_NAVI_STATUS latch) → ghi VÔ ĐIỀU KIỆN.
 *  • BỀ MẶT cụm (SET_NAVI_SCREEN_STATUS) → chỉ khi `writeSurface` (caller truyền = `navOnlyMode`, tức Cast OFF).
 *
 * `writeNavFrame` cần HAL BYDAuto thật (off-car trả sớm "InstrumentDevice null"), nên contract khoá bằng
 * source inspection — cùng khuôn `NavHalContentKeepAliveTest` / `PhysicalHudOwnershipTest`.
 * Đây là đường GMaps ĐÃ PROVEN (CLAUDE.md §6): nhánh Cast OFF (writeSurface=true) phải ghi ĐỦ content+surface
 * y như cũ ⇒ 0 hồi quy. Các assert dưới cũng là mutation-guard (đảo 1 vế là đỏ).
 */
class NavHudSurfaceSplitWiringTest {

    private val hal by lazy { SourceRoots.text("src/main/java/com/byd/clusternav/modules/hal/BydHal.kt") }
    private val owner by lazy { SourceRoots.text("src/main/java/com/byd/clusternav/NavigationHudOwner.kt") }
    private val repo by lazy { SourceRoots.text("src/main/java/com/byd/clusternav/NavRepository.kt") }

    @Test
    fun `writeNavFrame nhan writeSurface mac dinh true (Cast OFF = hanh vi cu)`() {
        assertTrue(
            hal.contains("writeSurface: Boolean = true"),
            "writeNavFrame phải nhận writeSurface (default true ⇒ nhánh không-cast ghi đủ như cũ)",
        )
    }

    @Test
    fun `chi SET_NAVI_SCREEN_STATUS bi gate boi writeSurface (be mat cum)`() {
        assertTrue(
            hal.contains("if (!keepAlive && writeSurface) featureId(\"SET_NAVI_SCREEN_STATUS_SET\")"),
            "SET_NAVI_SCREEN_STATUS (bề mặt cụm) phải gate bởi writeSurface — chỉ dựng khi Cast OFF",
        )
    }

    @Test
    fun `SEND_NAVI_STATUS la NOI DUNG — khong gate writeSurface (OQ1)`() {
        // OQ1 owner chốt: latch "đang dẫn" thuộc nội dung (HUD cần biết đang dẫn) ⇒ chỉ gate !keepAlive.
        assertTrue(
            hal.contains("if (!keepAlive) w(\"INSTRUMENT_SEND_NAVI_STATUS_SET\", 2)"),
            "SEND_NAVI_STATUS phải là nội dung: chỉ gate !keepAlive, KHÔNG dính writeSurface",
        )
    }

    @Test
    fun `GUIDE_INFO_SIMPLE la NOI DUNG — ghi vo dieu kien (khong gate)`() {
        assertTrue(
            hal.contains("w(\"INSTRUMENT_GUIDE_INFO_SIMPLE_SET\", icon)"),
            "mũi tên guidance là nội dung HUD — ghi vô điều kiện (không keepAlive/writeSurface gate)",
        )
    }

    @Test
    fun `owner push nhan writeSurface va truyen xuong writeNavFrame`() {
        assertTrue(
            owner.contains("writeSurface: Boolean = true): OutputSubmission"),
            "NavigationHudOwner.push phải nhận writeSurface",
        )
        assertTrue(
            owner.contains("writeSurface = realPushWriteSurface"),
            "delivery phải truyền writeSurface xuống writeNavFrame",
        )
        assertTrue(
            owner.contains("writeSurface == appliedWriteSurface"),
            "dedup phải tính cả writeSurface ⇒ bật/tắt Cast thì frame được ghi lại (không bị nuốt)",
        )
    }

    @Test
    fun `NavRepository truyen writeSurface = navOnlyMode cho be mat cum`() {
        assertTrue(
            repo.contains("writeSurface = navOnlyMode(appCtx)"),
            "content push phải truyền gate Cast CHỈ cho bề mặt (writeSurface = navOnlyMode)",
        )
    }

    @Test
    fun `NavRepository KHONG con boc noi dung trong gate navOnlyMode`() {
        // Trước tách: cả owner.push bị `if (navOnlyMode(appCtx)) { ... }` bọc ⇒ Cast ON là HUD tắt.
        // Sau tách: nội dung chạy vô điều kiện (khối `run {`), gate chỉ còn ở tham số writeSurface.
        assertFalse(
            repo.contains("if (navOnlyMode(appCtx)) {"),
            "nội dung dẫn KHÔNG được bọc trong gate navOnlyMode nữa (HUD kính phải lên bất kể Cast)",
        )
    }

    @Test
    fun `reapply cluster-mode ton trong writeSurface theo Cast (P2 review 08-24)`() {
        assertTrue(
            owner.contains("fun reapply(writeSurface: Boolean = true)"),
            "reapply phải nhận writeSurface (Cast ON ⇒ không dựng lại bề mặt cụm)",
        )
        assertTrue(
            owner.contains("push(icon, if (seg < 0) -1 else seg, road, writeSurface = writeSurface)"),
            "reapply phải truyền writeSurface xuống push",
        )
        assertTrue(
            repo.contains("hudOwner?.reapply(navOnlyMode(context))"),
            "reapplyClusterMode phải truyền navOnlyMode ⇒ Cast ON không dựng lại bề mặt cụm",
        )
    }
}
