package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * UX-OVERHAUL · WP7 — khoá **DÂY NỐI** của việc ẩn dev/debug UI (owner 2026-09-20: *"ẩn hết đồ dev/debug/log/
 * lấy-info-xe khỏi UI, giữ chức năng chạy qua adb"*).
 *
 * Đây là loại bất biến không nhìn thấy được off-car: mọi thứ vẫn biên dịch và test hành vi vẫn xanh kể cả khi một
 * bề mặt dev **lọt ra** trước cổng — nó chỉ lộ khi có người mở màn Cài đặt trên xe mà chưa bật test-mode. Bốn nhóm,
 * mỗi nhóm là một chỗ dễ trôi:
 *  1. **Cổng DUY NHẤT** — mọi bề mặt dev đứng sau [DevMode.unlocked], không sinh khoá `dev_mode` thứ hai.
 *  2. **Công tắc test-mode dựng TRƯỚC cổng** — nếu không thì không có cách nào bật cổng lên.
 *  3. **Đồ dev nằm SAU cổng** — Diagnostics · VietMap-data · Gõ-lệnh-chữ · CAPTEST · nhật-ký-voice.
 *  4. **Đường thoát THẬT của người dùng ở lại** — hai nút cứu-hộ cast KHÔNG bị gác (cụm đang tối là việc của
 *     người lái, không phải của người viết code).
 */
class DevSurfaceGateContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val sections by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }
    private val cast by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsCast.kt") }
    private val voiceModel by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt") }
    private val devMode by lazy { code("src/main/java/com/byd/clusternav/launcher/DevMode.kt") }

    /** [DevMode] gác bằng CHÍNH cửa sổ test-mode, KHÔNG bằng một khoá `dev_mode` riêng (bẫy hai-bản-sao). */
    @Test
    fun `cong dev gac bang cua so test-mode, khong sinh khoa thu hai`() {
        assertTrue(devMode.contains("TestBridgeStore.remainingMinutes"), "cổng đọc chính cửa sổ test-mode")
        assertTrue(devMode.contains(".getOrDefault(false)"), "đọc hỏng ⇒ ĐÓNG (mặc định fail-safe cho đồ dev)")
        assertEquals(
            0, Regex(""""dev_mode"""").findAll(devMode).count(),
            "không được sinh khoá dev_mode thứ hai — mở cửa thứ hai cho cùng một quyền là bẫy hai-bản-sao",
        )
    }

    /**
     * Trong khối *Nâng cao*: công tắc test-mode dựng TRƯỚC `if (!DevMode.unlocked(context)) return`, và mọi bề mặt
     * dev nằm SAU dòng đó.
     */
    @Test
    fun `khoi Nang cao - test-mode truoc cong, do dev sau cong`() {
        val fn = SourceRoots.body(sections, "private fun system(")
        val bridgeAt = fn.indexOf("testBridge(body)")
        val gateAt = fn.indexOf("if (!DevMode.unlocked(context)) return")
        assertTrue(bridgeAt in 0 until gateAt, "công tắc test-mode phải dựng TRƯỚC cổng dev")
        // Ba đồ dev phải nằm SAU cổng.
        listOf("openDiagnostics()", "openVietMapData()", "CapTestConsole(", "VoiceTextConsole(").forEach {
            val at = fn.indexOf(it)
            assertTrue(at > gateAt, "bề mặt dev '$it' phải nằm SAU cổng DevMode (nếu không nó hiện cho người lái)")
        }
    }

    /** Nhật ký lượt nói + nút Xuất (đồ ĐO) đứng sau cổng; hàng tải mô hình (bề mặt người dùng) thì KHÔNG. */
    @Test
    fun `nhat ky voice sau cong, tai mo hinh truoc cong`() {
        val fn = SourceRoots.body(voiceModel, "private fun logRows(")
        val gateAt = fn.indexOf("if (!DevMode.unlocked(context)) return")
        val checkAt = fn.indexOf("VoiceUtteranceLog.enabled")
        assertTrue(gateAt in 0 until checkAt, "cổng DevMode phải đứng TRƯỚC ô tích nhật ký voice (return sớm)")
        // Bề mặt tải mô hình NGHE/ĐỌC không được đứng sau cổng dev (không nói được với xe thì mất tính năng chính).
        assertTrue(voiceModel.contains("fun build("), "build() là bề mặt người dùng")
    }

    /** Hai nút cứu-hộ cast là đường thoát THẬT của người lái ⇒ KHÔNG gác; chỉ Diagnostics gác. */
    @Test
    fun `cuu ho cast khong gac, chi Diagnostics gac`() {
        val fn = SourceRoots.body(cast, "private fun rescue(")
        val restoreAt = fn.indexOf("restoreCluster()")
        val deepAt = fn.indexOf("deepRescue(")
        val gateAt = fn.indexOf("DevMode.unlocked(context)")
        assertTrue(restoreAt in 0 until gateAt, "nút trả cụm về đồng hồ phải ở TRƯỚC cổng dev (người lái cần)")
        assertTrue(deepAt in 0 until gateAt, "nút dọn sạch cụm cũng ở trước cổng dev")
        assertTrue(gateAt > 0 && fn.indexOf("openDiagnostics()") > gateAt, "chỉ Diagnostics đứng sau cổng dev")
    }
}
