package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * V-CLUSTER · VC-R6 — bài canh TĨNH cho phần ghim phiên (đọc source đã bỏ chú thích, cắt thân bằng [SourceRoots.body]).
 *
 * Bài hành vi ở [CastSessionPinTest] bắt đúng các ca đã dựng; bài này bắt HÌNH DẠNG để một đường đọc prefs mới không lẻn
 * vào lượt áp tự động ở một ca chưa ai dựng (đúng cách lỗi B6 đã sinh ra: `applySavedProfile` đọc prefs ở MỌI chỗ gọi).
 */
class CastSessionPinWiringTest {

    private val dir = "src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified"
    private fun code(name: String) = SourceRoots.codeOf("$dir/$name")

    /** Lượt repin (vòng 2 s, không ai bấm) KHÔNG được đọc prefs — mọi thứ nó áp phải là bản ghim của phiên. */
    @Test
    fun `repin chi dung ban ghim, khong doc prefs`() {
        val body = SourceRoots.body(code("SimpleCastCoordinatorOps.kt"), "internal fun SimpleCastCoordinator.doRepinEscapedCastApps()")
        assertFalse(body.contains("prefs."), "repin đọc prefs = hình học của hồ sơ MỚI tự nổ lên cụm (refute B6)")
        // B1b: lượt áp đi qua `applySessionPin` (= `geometry.applyPinned` + đọc lại khung ở cụm Chữ nhật) — hàm đó cũng không
        // đọc prefs (bài dưới).
        assertTrue(body.contains("applySessionPin(pkg, target.pinned)"), "repin phải áp bản ghim của ô")
        val apply = SourceRoots.body(code("CastStyleSessionOps.kt"), "internal fun SimpleCastCoordinator.applySessionPin(")
        assertFalse(apply.contains("prefs."), "applySessionPin không được đọc prefs")
        assertTrue(apply.contains("geometry.applyPinned(pkg, pinned)"), "applySessionPin = đúng hàm áp của đường cũ")
        assertTrue(body.contains(".leftPercent"), "tỉ lệ của repin lấy từ phiên (CastingSplit.leftPercent)")
    }

    /** Hàm áp duy nhất của đường tự động: không đọc prefs, chốt `isShellSafe` TRƯỚC lệnh đầu tiên, kẹp khung đo được. */
    @Test
    fun `applyPinned khong doc prefs, chot isShellSafe truoc shell, kep khung`() {
        val src = code("CastGeometryController.kt")
        val body = SourceRoots.body(src, "fun applyPinned(pkg: String, pinned: DisplayConfig?)")
        assertFalse(body.contains("prefs."), "applyPinned không được đọc prefs")
        val gate = body.indexOf("CastGeometryGuard.isShellSafe(pinned)")
        val firstShell = body.indexOf("shell.execute(")
        assertTrue(gate in 0 until firstShell, "chốt cuối phải đứng TRƯỚC lệnh shell đầu tiên")
        assertTrue(body.contains("CastGeometryGuard.clampBounds("), "khung phải kẹp theo khung đo được")
        assertFalse(src.contains("fun applySavedProfile("), "đường cũ đọc prefs mỗi lần gọi phải đã gỡ, không để song song")
    }

    /** Ba điểm bắt đầu phiên đọc hồ sơ QUA hàm ghim; ô thứ hai dùng tỉ lệ của phiên (mặt thứ hai của C2). */
    @Test
    fun `bat dau phien ghim mot lan, o thu hai dung ti le phien`() {
        val intents = code("SimpleCastCoordinatorIntents.kt")
        val full = SourceRoots.body(intents, "internal fun SimpleCastCoordinator.handleCastFull(")
        assertTrue(full.contains("pinFull(intent.pkg, intent.appType)"), "FULL ghim qua pinFull")
        assertFalse(full.contains("resolveConfig("), "FULL đọc prefs đúng MỘT lần (pinFull), không đọc thêm lượt resolve")
        val slot = SourceRoots.body(intents, "internal fun SimpleCastCoordinator.handleCastSlot(")
        assertTrue(slot.contains("sessionLeftPercent(afterWait)"), "tỉ lệ ô = tỉ lệ phiên nếu đang chia")
        assertFalse(slot.contains("splitRatioLeftPercent()"), "ô thứ hai không được đọc lại tỉ lệ ở prefs (C2)")
        assertTrue(slot.contains("leftPercent = leftPercent"), "CastingSplit mới mang tỉ lệ của phiên")
        val stop = SourceRoots.body(intents, "internal fun SimpleCastCoordinator.handleStop(")
        assertTrue(stop.contains("current.copy(left = remainingLeft, right = remainingRight)"), "dừng một nửa giữ tỉ lệ phiên")
    }

    /** Chỉnh tay: ô nhớ theo tỉ lệ PHIÊN; DPI chia đôi không đọc tỉ lệ ở prefs. */
    @Test
    fun `chinh tay luu theo ti le phien`() {
        val pin = code("CastSessionPin.kt")
        val resizeSlot = SourceRoots.body(pin, "internal fun SimpleCastCoordinator.resizeSlotBody(")
        // B1b: ô nhớ còn theo kiểu khung của PHIÊN (`frameStyle`) — Chữ nhật lưu khoá `__RECT`.
        assertTrue(resizeSlot.contains("CastProfile.of(side, current.leftPercent, frameStyle)"))
        assertFalse(resizeSlot.contains("splitRatioLeftPercent"))
        val density = SourceRoots.body(code("CastDensityControl.kt"), "fun setForSplit(")
        assertTrue(density.contains("split.leftPercent"))
        assertFalse(density.contains("splitRatioLeftPercent"), "DPI chia đôi lưu dưới tỉ lệ đang có trên cụm, không theo prefs")
        assertFalse(pin.contains("shell.execute("), "tệp ghim không chứa lệnh shell nào — lệnh ở Controller/DensityControl")
    }
}
