package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.76 · R2 — mặc định camera theo HỒ SƠ XE, phần thuần ═══════════════════════════════════════════════════════
 *
 * Bộ số của Seal nằm ở `:app` (`ClusterProfile.SEAL_DL3_CAMERA`, bài `ClusterProfileCameraDefaultsTest`); ở đây ghim
 * (a) hồ sơ TRUNG TÍNH = đúng từng literal 2.75 (đời xe chưa đo không đổi một pixel — CLAUDE.md §6), (b) `sane()`
 * đưa từng trường sai về trung tính chứ không kẹp/ném, (c) bản đồ kênh chỉ "có" khi CẢ hai bên đo.
 */
class CameraProfileDefaultsTest {

    private val P = CameraSignalPolicy
    private val D = CameraDewarpPrefs

    /** Từng trường của NEUTRAL = từng literal mà 2.75 đang dùng — đổi một cái là đổi hành vi xe chưa đo. */
    @Test fun `NEUTRAL bang dung literal 2 75`() {
        val n = CameraProfileDefaults.NEUTRAL
        assertEquals(P.SPAN_NARROW, n.span)
        assertEquals(P.RENDER_TEXTURE, n.render)
        assertEquals(D.AMOUNT_DEFAULT, n.amountPct); assertEquals(100, n.amountPct)
        assertEquals(D.PCT_DEFAULT, n.focalPct); assertEquals(D.PCT_DEFAULT, n.kPct); assertEquals(D.PCT_DEFAULT, n.scalePct)
        assertEquals(D.CENTER_DEFAULT, n.centerXPct); assertEquals(D.CENTER_DEFAULT, n.centerYPct)
        assertEquals(D.PAN_DEFAULT, n.panXPct); assertEquals(D.PAN_DEFAULT, n.panYPct)
        assertEquals(P.ROTATE_LEFT, n.rotation(left = true), "2.75: trái ↺ — [ĐOÁN] cũ, giữ cho xe chưa đo")
        assertEquals(P.ROTATE_RIGHT, n.rotation(left = false))
        assertEquals(CameraProfileDefaults.CHANNEL_UNKNOWN, n.channel(left = true))
        assertEquals(CameraProfileDefaults.CHANNEL_UNKNOWN, n.channel(left = false))
        assertFalse(n.hasChannelMap)
        assertEquals(n, n.sane(), "trung tính đã sạch — sane() không đổi gì")
    }

    /** Bộ trung tính đưa vào phép hợp uniform cho ra ĐÚNG bộ 2.75 (100 % = base). */
    @Test fun `NEUTRAL vao uniform ra dung base`() {
        val n = CameraProfileDefaults.NEUTRAL
        val u = CameraGlUniforms.of(
            crop = CameraPanoCrop.stripCrop(1), srcCentreX = CameraPanoCrop.stripCentre(1).toFloat(), srcCentreY = 0.5f,
            streamW = 5120, streamH = 960, rotationDeg = 0,
            amountPct = n.amountPct, focalPct = n.focalPct, kPct = n.kPct, scalePct = n.scalePct,
            centerXPct = n.centerXPct, centerYPct = n.centerYPct, panXPct = n.panXPct, panYPct = n.panYPct,
        )
        val base = CameraGlUniforms.of(
            crop = CameraPanoCrop.stripCrop(1), srcCentreX = CameraPanoCrop.stripCentre(1).toFloat(), srcCentreY = 0.5f,
            streamW = 5120, streamH = 960, rotationDeg = 0,
        )
        assertEquals(base, u)
    }

    /** Trường sai ⇒ về trung tính TỪNG trường (không kẹp về biên, không ném, không kéo trường khác theo). */
    @Test fun `sane dua tung truong sai ve trung tinh`() {
        val bad = CameraProfileDefaults(
            span = "rác", render = "gl", amountPct = 150, focalPct = 10, kPct = 401, scalePct = 130,
            centerXPct = -999, centerYPct = 5, panXPct = 60, panYPct = -50, rotLeft = "SIDE", rotRight = "0",
            channelLeft = 9, channelRight = 3,
        ).sane()
        val n = CameraProfileDefaults.NEUTRAL
        assertEquals(n.span, bad.span); assertEquals(n.render, bad.render)
        assertEquals(n.amountPct, bad.amountPct, "150 % ngoài miền ⇒ 100, không kẹp về 100 'vì tình cờ'")
        assertEquals(n.focalPct, bad.focalPct); assertEquals(n.kPct, bad.kPct)
        assertEquals(130, bad.scalePct, "trường hợp lệ giữ nguyên")
        assertEquals(n.centerXPct, bad.centerXPct); assertEquals(5, bad.centerYPct)
        assertEquals(n.panXPct, bad.panXPct); assertEquals(-50, bad.panYPct)
        assertEquals(n.rotLeft, bad.rotLeft, "mã cũ SIDE không phải một góc ⇒ trung tính")
        assertEquals(P.ROTATE_NONE, bad.rotRight)
        assertEquals(CameraProfileDefaults.CHANNEL_UNKNOWN, bad.channelLeft, "kênh 9 ngoài VIEW_CHANNEL_1..4 ⇒ chưa đo")
        assertEquals(3, bad.channelRight)
        assertFalse(bad.hasChannelMap, "một bên chưa đo ⇒ cả cặp coi là chưa đo")
    }

    /** `isChannel`: chỉ `1..HAL_MODE_MAX`; `0` (VIEW_DEFAULT) và AUTO (−1) không phải kênh đơn. */
    @Test fun `isChannel dong dung mien VIEW_CHANNEL`() {
        assertFalse(CameraProfileDefaults.isChannel(CameraProfileDefaults.CHANNEL_UNKNOWN))
        assertFalse(CameraProfileDefaults.isChannel(P.HAL_MODE_AUTO))
        assertFalse(CameraProfileDefaults.isChannel(P.HAL_MODE_MIN), "0 = VIEW_DEFAULT = khung ghép")
        (1..P.HAL_MODE_MAX).forEach { assertTrue(CameraProfileDefaults.isChannel(it), "kênh $it") }
        assertFalse(CameraProfileDefaults.isChannel(P.HAL_MODE_MAX + 1))
    }
}
