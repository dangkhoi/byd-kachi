package com.byd.clusternav.launcher.voice

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * ═══ [P0-1d/e] BÀI CANH CHỐT MỘT-MICRO + CẦU CHÌ ═════════════════════════════════════════════════════════════
 *
 * Khoá lại lỗi hiện trường [ĐO xe 2026-09-16] `voice-1.68-real.txt`: **309** lượt mở micro trong 12 phút từ chỉ
 * **7** phiên thật, với **4** lượt mở chồng nhau trong 300 ms cho ra bốn kết quả `sherpa ra` mâu thuẫn.
 *
 * Bơm đồng hồ giả ([VoiceSingleFlight.acquire] nhận `nowMs`) nên cửa sổ 60 giây kiểm được trong vài mili-giây —
 * đó chính là lý do lớp ấy giữ THUẦN, không gọi `SystemClock`/`Log`.
 */
class VoiceSingleFlightTest {

    @BeforeEach
    fun clean() = VoiceSingleFlight.reset()

    // ── (d) Một micro tại một thời điểm ──────────────────────────────────────────────────────────

    @Test
    fun `luot thu hai bi chan khi luot dau chua nha, va biet ai dang giu`() {
        assertEquals(VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.acquire("chinh", 0L))
        val busy = VoiceSingleFlight.acquire("hoi-thoai", 10L)
        assertTrue(busy is VoiceSingleFlight.Grant.Busy, "lượt hai phải bị chắn, nhận: $busy")
        // Nhật ký phải nói được AI chắn AI — không thì trên xe chỉ thấy "một lượt biến mất".
        assertEquals("chinh", (busy as VoiceSingleFlight.Grant.Busy).holder)
        VoiceSingleFlight.release()
        assertEquals(VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.acquire("hoi-thoai", 20L))
    }

    @Test
    fun `nha thua khong ne, va khong mo cong cho ai`() {
        VoiceSingleFlight.release()
        VoiceSingleFlight.release()
        assertFalse(VoiceSingleFlight.busy())
        assertEquals(VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.acquire("chinh", 0L))
        assertTrue(VoiceSingleFlight.busy())
    }

    /**
     * **Ca thật của bản ghi**: 4 lượt xin cùng lúc ⇒ đúng MỘT được cấp.
     *
     * Cờ cũ (`VoiceSession.capturing`, một `AtomicBoolean` đặt/xoá quanh lời gọi) qua được bài này một cách may
     * rủi ở lượt xin, nhưng hỏng ở lượt **nhả**: lượt xong trước xoá cờ cho cả bốn. Bài dưới đây bắt đúng chỗ đó
     * bằng cách bắt mọi lượt được cấp phải tự nhả rồi mới đếm.
     */
    @Test
    fun `bon luot xin cung luc thi dung mot luot duoc cap`() {
        val start = CountDownLatch(1)
        val done = CountDownLatch(4)
        val granted = AtomicInteger(0)
        repeat(4) {
            Thread {
                start.await()
                if (VoiceSingleFlight.acquire("luot-$it", 0L) == VoiceSingleFlight.Grant.Ok) granted.incrementAndGet()
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS), "các luồng phải xong")
        assertEquals(1, granted.get(), "đúng MỘT lượt được mở micro — 4 lượt chồng nhau là ca thật trên xe")
        // Và cầu chì chỉ tính lượt ĐƯỢC CẤP, không tính 3 lượt bị chắn (chúng đã vô hại).
        assertEquals(1, VoiceSingleFlight.opensInWindow(0L))
    }

    // ── (e) Cầu chì theo phút ────────────────────────────────────────────────────────────────────

    @Test
    fun `cau chi chan luot thu 13 trong mot phut`() {
        repeat(VoiceSingleFlight.MAX_OPENS_PER_MINUTE) { i ->
            assertEquals(VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.acquire("n$i", i.toLong()), "lượt $i")
            VoiceSingleFlight.release()
        }
        val fused = VoiceSingleFlight.acquire("qua-tay", 100L)
        assertTrue(fused is VoiceSingleFlight.Grant.Fused, "lượt thứ 13 phải bị cầu chì, nhận: $fused")
        assertEquals(
            VoiceSingleFlight.MAX_OPENS_PER_MINUTE,
            (fused as VoiceSingleFlight.Grant.Fused).opens,
            "câu lỗi phải nói ra con số, để nhật ký trên xe đọc được ngay",
        )
        // Bị cầu chì thì KHÔNG được coi là đang giữ micro — nếu không, một lần cháy cầu chì khoá luôn cả chốt.
        assertFalse(VoiceSingleFlight.busy())
    }

    @Test
    fun `cua so truot — qua 60 giay thi lai mo duoc`() {
        repeat(VoiceSingleFlight.MAX_OPENS_PER_MINUTE) { i ->
            VoiceSingleFlight.acquire("n$i", i.toLong()); VoiceSingleFlight.release()
        }
        assertTrue(VoiceSingleFlight.acquire("x", 59_999L) is VoiceSingleFlight.Grant.Fused, "vẫn trong cửa sổ")
        // Mốc đầu tiên (t=0) rơi ra khỏi cửa sổ ĐÚNG tại 60_000 — biên đóng, không phải 60_001 (lệch-một).
        assertEquals(VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.acquire("x", 60_000L))
    }

    @Test
    fun `nhip 26 luot mot phut cua ban ghi that bi chan lai con 12`() {
        // [ĐO xe] 309 lượt / 12 phút ≈ 26 lượt/phút. Bơm đúng nhịp ấy trong một phút và đếm số lượt LỌT.
        var ok = 0
        repeat(26) { i ->
            if (VoiceSingleFlight.acquire("loop-$i", i * 2_300L) == VoiceSingleFlight.Grant.Ok) ok++
            VoiceSingleFlight.release()
        }
        assertTrue(
            ok <= VoiceSingleFlight.MAX_OPENS_PER_MINUTE + 1,
            "nhịp 26 lượt/phút phải bị cắt về quanh trần ${VoiceSingleFlight.MAX_OPENS_PER_MINUTE}, lọt $ok",
        )
        assertTrue(ok >= VoiceSingleFlight.MAX_OPENS_PER_MINUTE, "nhưng không được cắt quá tay: lọt $ok")
    }

    @Test
    fun `mot phien hoi thoai dai HOP LE van chay du — cau chi khong chan nham`() {
        // 1 lượt chính + 5 lượt nối (trần `MAX_FOLLOW_UPS`) = 6, cách nhau ~6 giây: phải lọt HẾT.
        repeat(6) { i ->
            assertEquals(
                VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.acquire("hop-le-$i", i * 6_000L),
                "một phiên hội thoại dài hợp lệ không được chạm cầu chì (lượt $i)",
            )
            VoiceSingleFlight.release()
        }
    }

    // ── "seri ngu" — team 2026-09-22: nói gì cũng không hiểu, phải tắt voice mở lại ──────────────

    /**
     * **Ca thật team báo**: cầu chì cháy (12 lượt auto trong 60 s từ một câu nghe nhầm qua vòng hỏi-lại) rồi
     * **KHẸT** — mọi lượt sau nhận `Fused` ⇒ chuỗi rỗng ⇒ *"không nghe thấy"* tới khi tắt voice mở lại.
     *
     * Sau vá: lượt do NGƯỜI chủ động mở ([VoiceSingleFlight.LABEL_COMMAND] = `"chinh"`) **luôn được cấp**, kể
     * cả khi cầu chì đã cháy — nên một cú bấm nút mic mới không bao giờ bị *"seri ngu"*.
     */
    @Test
    fun `cau chi chay roi — nut mic cua nguoi bam VAN duoc cap`() {
        // Vòng auto tự nuôi đốt hết quỹ (nhãn KHÔNG phải "chinh").
        repeat(VoiceSingleFlight.MAX_OPENS_PER_MINUTE) { i ->
            VoiceSingleFlight.acquire("hoi-lai-$i", i.toLong()); VoiceSingleFlight.release()
        }
        assertTrue(
            VoiceSingleFlight.acquire("hoi-lai-x", 100L) is VoiceSingleFlight.Grant.Fused,
            "vòng auto phải bị cầu chì",
        )
        // Nhưng người bấm nút mic (lượt "chinh") thì KHÔNG bị chặn — đây là vá "seri ngu".
        assertEquals(
            VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.acquire("chinh", 200L),
            "lượt do người chủ động mở không bao giờ bị cầu chì từ chối",
        )
    }

    /** Lượt "chinh" vẫn GHI một suất — để các lượt AUTO sau nó trong cùng phiên vẫn bị đếm và chặn được. */
    @Test
    fun `luot chinh van ghi mot suat — khong mo cua cho vong auto lach cau chi`() {
        assertEquals(VoiceSingleFlight.Grant.Ok, VoiceSingleFlight.acquire("chinh", 0L))
        VoiceSingleFlight.release()
        assertEquals(1, VoiceSingleFlight.opensInWindow(0L), "lượt chinh phải ghi vào quỹ, không miễn đếm")
        // 11 lượt auto tiếp theo lấp đầy quỹ (1 + 11 = 12), lượt auto thứ 13 bị chặn đúng như trước.
        repeat(VoiceSingleFlight.MAX_OPENS_PER_MINUTE - 1) { i ->
            VoiceSingleFlight.acquire("hoi-lai-$i", (i + 1).toLong()); VoiceSingleFlight.release()
        }
        assertTrue(
            VoiceSingleFlight.acquire("hoi-lai-tran", 50L) is VoiceSingleFlight.Grant.Fused,
            "vòng auto vẫn phải bị chặn ở trần — lượt chinh không được mở đường lách cho nó",
        )
    }
}
