package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 1.69 · BÀI CANH NỐI DÂY — bộ gom cử chỉ có THẬT SỰ được gọi không ═══════════════════════════════════════
 *
 * `GestureFallback` là code **thuần** ở `:core` và đã có bài riêng (`GestureFallbackTest`). Nhưng CLAUDE.md §8
 * nói đúng cái bẫy đã xảy ra một lần trong repo này (`CastShell.evictVd`: viết cẩn thận, có KDoc, biên dịch
 * sạch — và **chưa từng được gọi**): một máy trạng thái đúng mà không ai gọi thì trên xe vẫn là *"vuốt không
 * scroll được"*. Bài này quét **thân `onTouchEvent`** của [VdAppHost] và khoá bốn điều:
 *
 *  1. đường lùi đi qua `gesture.feed(...)` (có call site thật, không chỉ có định nghĩa);
 *  2. **không còn** `Thread {` nào trên đường chạm — soát OCR: trước 1.69 mỗi cú chạm dựng HAI luồng;
 *  3. toạ độ đưa vào cả hai đường là toạ độ **đã map** (`dx`/`dy`), không phải `e.x`/`e.y` thô;
 *  4. ngưỡng `touchSlop`/`longPressTimeout` lấy từ `ViewConfiguration` tại chỗ gọi, KHÔNG viết cứng ở `:core`.
 *
 * Quét **mã đã bỏ chú thích** ([SourceRoots.codeOf]) nên không ai qua được bài này bằng cách viết tên hàm vào
 * một dòng `//`.
 */
class VdTouchFallbackWiringContractTest {

    private val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/VdAppHost.kt")
    private val touch = SourceRoots.body(src, "override fun onTouchEvent(e: MotionEvent): Boolean")

    @Test
    fun `duong lui cua cham di qua bo gom cu chi`() {
        assertTrue(
            touch.contains("gesture.feed("),
            "onTouchEvent KHÔNG gọi GestureFallback.feed ⇒ bộ gom cử chỉ là mã chết, xe vẫn không vuốt được",
        )
        assertTrue(
            src.contains("GestureFallback("),
            "VdAppHost phải tự dựng bộ gom (một bộ mỗi ô) — thân onTouchEvent: $touch",
        )
    }

    /** Soát OCR: đường lùi chỉ được dùng MỘT bộ thi hành có trần, không dựng luồng mới theo từng sự kiện. */
    @Test
    fun `khong con Thread nao tren duong cham`() {
        assertFalse(
            touch.contains("Thread {") || touch.contains("Thread("),
            "một luồng mới mỗi sự kiện chạm — đúng lỗi [P2] mà lượt soát OCR bắt được: $touch",
        )
        assertTrue(
            touch.contains("TOUCH_FALLBACK.execute"),
            "lệnh của đường lùi phải chạy trên bộ thi hành dùng chung (một luồng, hàng đợi có trần)",
        )
    }

    /** Toạ độ của CẢ HAI đường phải là toạ độ đã map — `e.x/e.y` thô là nửa thứ hai của triệu chứng "tap lệch". */
    @Test
    fun `hai duong dung chung toa do da map`() {
        assertTrue(touch.contains("SlotTouchMapper.toDisplay"), "phải map view→display trước khi gửi đi đâu cả")
        assertTrue(touch.contains("gesture.feed(action, displayId, dx, dy"), "đường lùi phải nhận dx,dy đã map")
        assertTrue(touch.contains("sendTouch(displayId, action, dx, dy"), "đường daemon cũng vậy")
        assertFalse(
            Regex("""\be\.x\.toInt\(\)""").containsMatchIn(touch.substringAfter("val dx")),
            "toạ độ thô KHÔNG được dùng lại sau khi đã map",
        )
    }

    /** `:core` không được biết mật độ màn hình máy nào (CLAUDE.md §7) — hai ngưỡng phải tiêm từ chỗ gọi. */
    @Test
    fun `nguong touch slop va long press lay tu ViewConfiguration`() {
        assertTrue(src.contains("ViewConfiguration.get(context).scaledTouchSlop"))
        assertTrue(src.contains("ViewConfiguration.getLongPressTimeout()"))
        val core = SourceRoots.codeOf("src/main/java/com/byd/clusternav/system/inputd/GestureFallback.kt")
        assertFalse(core.contains("import android."), ":core phải THUẦN — một import android là hết test off-device")
    }
}
