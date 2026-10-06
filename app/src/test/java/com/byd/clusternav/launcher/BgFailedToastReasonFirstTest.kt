package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · TOAST-2LINE-DL5 — câu báo "chạy nền hỏng" đặt MÃ LÝ DO lên ĐẦU ═══════════════════════════════════════════════════
 *
 * [ĐO nguồn AOSP android-12.0.0_r34] toast chữ của app `targetSdk ≥ 31` do SystemUI dựng: `packages/SystemUI/res/layout/
 * text_toast.xml` — TextView `maxLines="2"` + `ellipsize="end"`, khung `maxWidth=@*android:dimen/toast_width` (300 dp,
 * `core/res/res/values/dimens.xml`) trừ lề 16 + 16 dp và icon 24 + 10 dp ⇒ ~234 dp cho chữ 14 sp. Kachi `targetSdk = 37` ⇒ trên
 * DiLink 5 (Android 12) câu dài bị CẮT ở cuối. Câu cũ đặt lý do (`%2$s`) CUỐI ⇒ [SUY] bản EN/MS (≈ 57–63 ký tự + tên app) mất đúng
 * phần lý do — thứ ảnh chụp màn hình cần (2.88 · FIELD-287-SLOT-BG). Nay lý do đứng đầu dòng 1; phần bị cắt (nếu có) là đuôi câu
 * chung. Android 10 (xe owner) toast do app dựng, không giới hạn dòng [ĐO nguồn r47 `transient_notification.xml`] ⇒ đọc đủ như cũ.
 */
class BgFailedToastReasonFirstTest {

    private val locales = listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms")

    private fun text(locale: String): String {
        val xml = SourceRoots.text("src/main/res/$locale/strings_kachi.xml")
        val m = Regex("<string name=\"kachi_sc_bg_failed_why\">(.*?)</string>").find(xml)
        assertNotNull(m, "$locale thiếu kachi_sc_bg_failed_why")
        return m!!.groupValues[1]
    }

    @Test
    fun `ly do dung DAU cau o ca nam thu tieng`() {
        locales.forEach { l ->
            val s = text(l)
            assertTrue(s.startsWith("[%2\$s] "), "$l: lý do phải đứng đầu (dòng 1 của toast 2 dòng): '$s'")
            assertTrue(s.indexOf("%1\$s") > s.indexOf("%2\$s"), "$l: tên app sau lý do: '$s'")
            assertTrue(s.count { it == '%' } == 2, "$l: đúng hai tham số: '$s'")
        }
    }

    /** Mã lý do của đường đang chạy (ô 7 đỗ ẩn) phải ngắn: "[mã] " nằm gọn trong ~1/2 dòng (~31 ký tự Latin @14 sp, 234 dp). */
    @Test
    fun `ma ly do dang dung ngan, nam gon dong 1`() {
        val code = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeSlotActions.kt")
        val m = Regex("const val PARK_NOT_READY = \"([^\"]*)\"").find(code)
        assertNotNull(m, "thiếu PARK_NOT_READY — đường gọi kachi_sc_bg_failed_why đổi? soát lại bài này")
        val reason = m!!.groupValues[1]
        assertTrue(reason.length + 3 <= 20, "'[$reason] ' dài ${reason.length + 3} ký tự — giữ ≤ 20 để tên app cũng lên dòng 1")
        assertTrue(code.contains("say(R.string.kachi_sc_bg_failed_why, pkg, PARK_NOT_READY)"), "đường đang chạy vẫn truyền mã ngắn")
    }
}
