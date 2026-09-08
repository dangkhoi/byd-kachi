package com.byd.clusternav.navigation

import com.byd.clusternav.navigation.TurnDistancePlausibility.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B-III — khoá [TurnDistancePlausibility].
 *
 * Chuỗi mẫu trong test mô phỏng nhịp THẬT đã đo trên xe 08-22: a11y publish mỗi
 * `WINDOW_ENUM_THROTTLE_MS` = 800ms, giá trị dạng `navBarDistance='140 m'` / `navBarStreetLine='Quang Trung'`
 * (xem NavViewIdSource.kt:22-27). Tốc độ là tham số của [TurnDistancePlausibility.accept] nên test không cần
 * HAL.
 */
class TurnDistancePlausibilityTest {

    private val waze = "com.chisadin.wazemod"
    private val vietmap = NavApps.VIETMAP_LIVE
    private val road = "Quang Trung"

    /** Đưa guard qua warmup bằng 3 mẫu giảm đều, trả lại mốc thời gian của mẫu cuối. */
    private fun TurnDistancePlausibility.warmUp(
        pkg: String = waze,
        start: Int = 300,
        step: Int = 20,
        t0: Long = 1_000L,
        dt: Long = 800L,
        speed: Double? = 10.0,
    ): Long {
        var t = t0
        repeat(3) { i ->
            accept(pkg, start - i * step, road, t, speed)
            if (i < 2) t += dt
        }
        return t
    }

    // ── (c) WARMUP — nguồn vừa chọn phải chứng minh mình ─────────────────────────────────────────────────

    /**
     * T1 — khoá yêu cầu (c): nguồn vừa được chọn phải qua K mẫu HỢP LÝ LIÊN TIẾP mới được lái ô cự-ly.
     * Không có nó thì mẫu ĐẦU TIÊN của một app/tuyến bất kỳ đã lái được cụm.
     */
    @Test
    fun `warmup — K-1 mau dau KHONG lai cum, mau thu K moi ACCEPT`() {
        val g = TurnDistancePlausibility()
        val d1 = g.accept(waze, 300, road, 1_000L, 10.0)
        val d2 = g.accept(waze, 280, road, 1_800L, 10.0)
        val d3 = g.accept(waze, 260, road, 2_600L, 10.0)
        assertEquals(Verdict.WARMUP, d1.verdict)
        assertEquals(TurnDistancePlausibility.UNKNOWN, d1.meters)
        assertEquals(Verdict.WARMUP, d2.verdict)
        assertEquals(TurnDistancePlausibility.UNKNOWN, d2.meters)
        assertEquals(Verdict.ACCEPT, d3.verdict)
        assertEquals(260, d3.meters)
    }

    /**
     * T2 — khoá chữ "LIÊN TIẾP": một mẫu nhảy tăng chen vào phải làm ĐẾM LẠI TỪ ĐẦU, không phải
     * "đủ K mẫu bất kỳ". Nếu ai đó đổi `goodStreak = 0` thành "giữ nguyên" khi reject, test này đỏ.
     */
    @Test
    fun `warmup — mot mau nhay tang chen vao lam dem lai tu dau`() {
        val g = TurnDistancePlausibility()
        g.accept(waze, 300, road, 1_000L, 10.0)                     // streak 1
        g.accept(waze, 280, road, 1_800L, 10.0)                     // streak 2
        val bad = g.accept(waze, 500, road, 2_600L, 10.0)           // nhảy tăng → streak về 0
        val r1 = g.accept(waze, 260, road, 3_400L, 10.0)
        val r2 = g.accept(waze, 240, road, 4_200L, 10.0)
        val r3 = g.accept(waze, 220, road, 5_000L, 10.0)
        assertEquals(Verdict.REJECT_RISE, bad.verdict)
        assertEquals(Verdict.WARMUP, r1.verdict)
        assertEquals(Verdict.WARMUP, r2.verdict)
        assertEquals(Verdict.ACCEPT, r3.verdict)
        assertEquals(220, r3.meters)
    }

    // ── (a) NHẢY TĂNG ────────────────────────────────────────────────────────────────────────────────────

    /** T3 — khoá yêu cầu (a): đang tiến tới ĐÚNG một điểm rẽ (cùng tên đường) thì cự ly phải GIẢM. */
    @Test
    fun `rise — cung ten duong, tang qua dung sai thi REJECT_RISE va khong ra so`() {
        val g = TurnDistancePlausibility()
        val t = g.warmUp()
        val d = g.accept(waze, 900, road, t + 800, 10.0)
        assertEquals(Verdict.REJECT_RISE, d.verdict)
        assertEquals(TurnDistancePlausibility.UNKNOWN, d.meters)
    }

    /**
     * T4 — khoá chống-QUÁ-NHẠY. App hiển thị bước 100m ở tầm km, nên "1.2 km" → "1.3 km" là nhiễu làm tròn
     * bình thường; nếu guard bắt cả ca này thì ô cự-ly mù suốt đoạn đường dài.
     */
    @Test
    fun `rise — tang TRONG dung sai max(30m, 10 phan tram) van ACCEPT`() {
        val g = TurnDistancePlausibility()
        g.accept(waze, 1200, road, 1_000L, 10.0)
        g.accept(waze, 1180, road, 1_800L, 10.0)
        val ok = g.accept(waze, 1160, road, 2_600L, 10.0)
        assertEquals(Verdict.ACCEPT, ok.verdict)
        // dung sai = max(30, 10% × 1160) = 116 → 1250 nằm trong (chênh 90).
        val d = g.accept(waze, 1250, road, 3_400L, 10.0)
        assertEquals(Verdict.ACCEPT, d.verdict)
        assertEquals(1250, d.meters)
    }

    /**
     * T5 — khoá "mỗi ngã rẽ KHÔNG được mất ô cự-ly": đổi tên đường = maneuver mới, cự ly tăng là HỢP LỆ và
     * phải ra số NGAY (không bắt warmup lại) — đúng lúc tài xế cần cự ly nhất.
     */
    @Test
    fun `rise — DOI ten duong thi tang la HOP LE, ACCEPT ngay khong re-warmup`() {
        val g = TurnDistancePlausibility()
        val t = g.warmUp()
        val d = g.accept(waze, 1500, "Nguyen Van Linh", t + 800, 10.0)
        assertEquals(Verdict.ACCEPT, d.verdict)
        assertEquals(1500, d.meters)
    }

    /**
     * T6 — khoá "không kẹt vĩnh viễn": reroute THẬT vẫn giữ nguyên tên đường, nên nhảy tăng dai dẳng phải
     * được NHẢ (đối chiếu `NavArrivalGuard.releaseAfterRejects` — cùng bài học, khác đường).
     */
    @Test
    fun `rise — nhay tang DAI DANG duoc nha sau releaseAfterRejects roi warmup lai`() {
        val g = TurnDistancePlausibility()
        var t = g.warmUp()
        val r1 = g.accept(waze, 900, road, t + 800, 10.0); t += 800
        val r2 = g.accept(waze, 900, road, t + 800, 10.0); t += 800
        val released = g.accept(waze, 900, road, t + 800, 10.0); t += 800
        assertEquals(Verdict.REJECT_RISE, r1.verdict)
        assertEquals(Verdict.REJECT_RISE, r2.verdict)
        assertEquals(Verdict.WARMUP, released.verdict, "nhả nhưng phải chứng minh lại, không ACCEPT thẳng")
        g.accept(waze, 880, road, t + 800, 10.0); t += 800
        val back = g.accept(waze, 860, road, t + 800, 10.0)
        assertEquals(Verdict.ACCEPT, back.verdict)
        assertEquals(860, back.meters)
    }

    // ── (b) ĐÓNG BĂNG ────────────────────────────────────────────────────────────────────────────────────

    /** T7 — khoá yêu cầu (b): xe đã đi hết quãng giới hạn mà cự ly đứng im ⇒ nguồn không còn cập nhật. */
    @Test
    fun `frozen — cu ly dung im trong khi xe da chay du quang gioi han thi REJECT_FROZEN`() {
        val g = TurnDistancePlausibility()
        g.accept(waze, 400, road, 1_000L, 30.0)                       // anchor 400 → ngưỡng max(150, 40) = 150
        val f1 = g.accept(waze, 400, road, 4_000L, 30.0)              // quãng 30×0.95×3 = 85.5 m
        val f2 = g.accept(waze, 400, road, 7_000L, 30.0)              // cộng dồn 171 m > 150
        assertNotEquals(Verdict.REJECT_FROZEN, f1.verdict)
        assertEquals(Verdict.REJECT_FROZEN, f2.verdict)
        assertEquals(TurnDistancePlausibility.UNKNOWN, f2.meters)
    }

    /**
     * T8 — khoá lại bài học bug α-β (TurnDistanceInterpolator.kt:9-13, user báo "dừng đèn đỏ mà km vẫn giảm"):
     * dừng đèn đỏ thì cự ly ĐỨNG IM là hợp lý, không được sinh phán quyết sai.
     */
    @Test
    fun `frozen — xe DUNG (duoi nguong movingMin) thi dung im la HOP LY`() {
        val g = TurnDistancePlausibility()
        var last = g.accept(waze, 400, road, 1_000L, 0.5)
        var t = 1_000L
        repeat(6) {
            t += 3_000L
            last = g.accept(waze, 400, road, t, 0.5)
            assertNotEquals(Verdict.REJECT_FROZEN, last.verdict, "xe đứng ⇒ không bao giờ là đóng băng")
        }
        assertEquals(Verdict.ACCEPT, last.verdict)
        assertEquals(400, last.meters)
    }

    /**
     * T9 — khoá GIỚI HẠN đã công bố: `speedMps == null` (HAL tốc độ câm) ⇒ luật (b) TẮT HOÀN TOÀN. Nếu ai đó
     * lén thêm fallback "đếm theo thời gian trôi" thì test này đỏ — đó đúng là cái bẫy của bug α-β.
     */
    @Test
    fun `frozen — speedMps null thi luat dong-bang TAT hoan toan`() {
        val g = TurnDistancePlausibility()
        g.accept(waze, 400, road, 1_000L, null)
        var t = 1_000L
        repeat(10) {
            t += 3_000L
            assertNotEquals(Verdict.REJECT_FROZEN, g.accept(waze, 400, road, t, null).verdict)
        }
    }

    /** T10 — khoá đường THOÁT khỏi đóng băng: cự ly đổi ⇒ re-anchor và đếm quãng lại từ 0. */
    @Test
    fun `frozen — cu ly doi thi re-anchor va dem quang lai tu 0`() {
        val g = TurnDistancePlausibility()
        g.accept(waze, 400, road, 1_000L, 30.0)
        g.accept(waze, 400, road, 4_000L, 30.0)            // quãng dồn 85.5 m
        val moved = g.accept(waze, 380, road, 7_000L, 30.0)
        assertEquals(Verdict.ACCEPT, moved.verdict)
        // Nếu quãng KHÔNG reset thì 85.5 + 85.5 = 171 > 150 ⇒ mẫu này đã là REJECT_FROZEN.
        val after = g.accept(waze, 380, road, 10_000L, 30.0)
        assertEquals(Verdict.ACCEPT, after.verdict)
        assertEquals(380, after.meters)
    }

    // ── Nhịp đọc / vòng đời ──────────────────────────────────────────────────────────────────────────────

    /**
     * T11 — khoá CHỐNG TỰ-ĐẦU-ĐỘC. Owner tick 4Hz (`HudKeepAlivePolicy.DEFAULT_INTERVAL_MS` = 250ms) nhưng a11y
     * chỉ publish ~1.25Hz (`WINDOW_ENUM_THROTTLE_MS` = 800ms) ⇒ CÙNG một mẫu bị đọc lại 3-4 lần. Thiếu chốt này
     * thì quãng-đóng-băng tự cộng và warmup tự đầy: guard vừa từ chối bậy vừa "chứng minh" bậy.
     */
    @Test
    fun `repeat — doc lai CUNG mot mau khong doi state va tra NGUYEN meters`() {
        val g = TurnDistancePlausibility()
        g.accept(waze, 400, road, 1_000L, 30.0)
        g.accept(waze, 390, road, 4_000L, 30.0)
        val accepted = g.accept(waze, 380, road, 7_000L, 30.0)
        assertEquals(Verdict.ACCEPT, accepted.verdict)
        repeat(4) {
            val again = g.accept(waze, 380, road, 7_000L, 30.0)
            assertEquals(Verdict.REPEAT, again.verdict)
            assertEquals(380, again.meters, "keep-alive phải re-assert ĐÚNG số cũ")
            assertFalse(again.blankDistance)
        }
        // 4 lần đọc lặp KHÔNG được cộng vào quãng đóng băng: một mẫu mới cùng giá trị chỉ mới 85.5 m < 150 m.
        assertEquals(Verdict.ACCEPT, g.accept(waze, 380, road, 10_000L, 30.0).verdict)
    }

    /** T12 — khoá "mất mạch thì không được tin tiếp": hai mẫu cách nhau quá [NavViewIdSource.FRESH_MS]. */
    @Test
    fun `continuity — gap qua continuityMs thi episode moi va warmup lai`() {
        val g = TurnDistancePlausibility()
        val t = g.warmUp()
        val d = g.accept(waze, 240, road, t + 4_000L + 1, 10.0)
        assertEquals(Verdict.WARMUP, d.verdict)
        assertEquals(TurnDistancePlausibility.UNKNOWN, d.meters)
        assertTrue(d.blankDistance, "đang hiện số mà mất mạch ⇒ phải xoá ô cự-ly ĐÚNG một lần")
    }

    /**
     * T13 — khoá yêu cầu (c) ở mức NGUỒN + chống spam lệnh HAL: đổi package ⇒ episode mới, và lệnh xoá ô
     * cự-ly chỉ bắn ĐÚNG MỘT LẦN (cạnh xuống), không phải mỗi tick.
     */
    @Test
    fun `switch — doi pkg thi episode moi va blankDistance dung MOT lan`() {
        val g = TurnDistancePlausibility()
        val t = g.warmUp()
        val first = g.accept(vietmap, 500, "Le Loi", t + 800, 10.0)
        val second = g.accept(vietmap, 480, "Le Loi", t + 1_600, 10.0)
        assertEquals(Verdict.WARMUP, first.verdict)
        assertTrue(first.blankDistance)
        assertEquals(Verdict.WARMUP, second.verdict)
        assertFalse(second.blankDistance, "không spam lệnh xoá mỗi tick")
    }

    /**
     * T14 — khoá "guard chỉ xoá cái CHÍNH NÓ viết ra": chưa từng ACCEPT thì tuyệt đối không phát lệnh xoá,
     * nếu không nó giẫm lên ô cự-ly do đường notification GMaps ghi (CLAUDE.md §6).
     */
    @Test
    fun `blank — chua tung ACCEPT thi KHONG BAO GIO blank`() {
        val g = TurnDistancePlausibility()
        assertFalse(g.accept(waze, 300, road, 1_000L, 10.0).blankDistance)
        assertFalse(g.accept(waze, 900, road, 1_800L, 10.0).blankDistance)   // REJECT_RISE
        assertFalse(g.noSample().blankDistance)
    }

    /** T15 — khoá degrade-safe khi nguồn im: xoá một lần rồi thôi; mẫu quay lại sau khi mất mạch → warmup. */
    @Test
    fun `noSample — blank mot lan roi thoi, mau tro lai thi warmup`() {
        val g = TurnDistancePlausibility()
        val t = g.warmUp()
        val n1 = g.noSample()
        val n2 = g.noSample()
        assertEquals(Verdict.NO_SAMPLE, n1.verdict)
        assertTrue(n1.blankDistance)
        assertFalse(n2.blankDistance)
        // Mẫu chỉ "biến mất" khi đã quá FRESH_MS ⇒ khi quay lại, mạch đã đứt theo đúng luật timestamp.
        val back = g.accept(waze, 200, road, t + 4_000L + 1, 10.0)
        assertEquals(Verdict.WARMUP, back.verdict)
    }

    /** T16 — khoá vòng đời: owner gọi reset khi nhả frame; phiên sau phải chứng minh lại từ đầu. */
    @Test
    fun `reset — xoa sach episode, streak va trang thai dang-hien`() {
        val g = TurnDistancePlausibility()
        val t = g.warmUp()
        g.reset()
        val d = g.accept(waze, 260, road, t, 10.0)
        assertEquals(Verdict.WARMUP, d.verdict)
        assertFalse(d.blankDistance, "reset đã xoá trạng thái đang-hiện ⇒ không có lệnh xoá lạc lõng")
    }

    /**
     * T17 — TEST TÀI LIỆU HOÁ GIỚI HẠN, cố ý assert điều "xấu".
     *
     * Guard KHÔNG cứu được ca "đúng app, sai tuyến": tuyến cũ (user mở từ sáng, đã đi lệch) vẫn phát ra chuỗi
     * mẫu hoàn hảo — cự ly giảm đều theo tốc độ, tên đường đổi ở mỗi ngã rẽ — vì không field nào của
     * [NavViewIdSource] mang DANH TÍNH TUYẾN. Test này tồn tại để người đọc code sau không tưởng ca đó đã được
     * cứu. Muốn cứu thật thì cần destination/route-id (Open Question trong spec), không phải siết ngưỡng.
     */
    @Test
    fun `KHONG cuu duoc dung-app-sai-tuyen — chuoi mau tuyen CU van duoc ACCEPT`() {
        val g = TurnDistancePlausibility()
        var t = 1_000L
        var m = 800
        var last = g.accept(waze, m, "Duong Cu", t, 15.0)
        repeat(5) {
            t += 800; m -= 12
            last = g.accept(waze, m, "Duong Cu", t, 15.0)
        }
        assertEquals(Verdict.ACCEPT, last.verdict)
        assertEquals(m, last.meters)
        // Kể cả khi tuyến cũ rẽ sang đường khác, guard vẫn không có cơ sở nào để nghi ngờ.
        val turned = g.accept(waze, 1200, "Duong Cu Khac", t + 800, 15.0)
        assertEquals(Verdict.ACCEPT, turned.verdict)
    }

    /**
     * T19 — khoá quá tải LƯỜI (sửa 08-22 vòng 2): tốc độ chỉ được đọc khi THẬT SỰ cần.
     *
     * Ở `:app` lambda này là `SpeedProvider.mpsOrNull()` = một lời gọi reflection xuống HAL. `NavOutputOwner`
     * tick 4 Hz còn a11y publish ~1,25 Hz, nên phần lớn tick dừng ở bước chống-đọc-lặp ([Verdict.REPEAT]) và
     * không cần tốc độ; nhánh cần nó là (b) đóng-băng. Truyền GIÁ TRỊ (`speed()`) thì Kotlin đánh giá tham số
     * TRƯỚC khi vào hàm ⇒ đốt ~3 lời gọi HAL/giây vô ích suốt cả chuyến.
     *
     * Test đỏ = ai đó đã đổi call site về dạng truyền giá trị, hoặc kéo lời gọi lên trước các bước 1–3.
     */
    @Test
    fun `toc do chi duoc doc khi that su can (khong goi o REPEAT)`() {
        val g = TurnDistancePlausibility()
        var reads = 0
        val speed: () -> Double? = { reads++; 10.0 }

        // Mẫu MỚI: được phép đọc tốc độ (mẫu đầu là episode mới nên thực ra cũng chưa cần, xem dưới).
        g.accept(waze, 300, road, 1_000L, speed)
        val afterFirst = reads

        // Đọc LẶP đúng mẫu đó 3 lần (đúng nhịp owner 4 Hz trên một mẫu a11y 1,25 Hz) ⇒ REPEAT, KHÔNG được đọc.
        repeat(3) {
            assertEquals(Verdict.REPEAT, g.accept(waze, 300, road, 1_000L, speed).verdict)
        }
        assertEquals(afterFirst, reads, "REPEAT không được chạm tới nguồn tốc độ")

        // Mẫu hụt (meters < 0) cũng không cần tốc độ.
        g.accept(waze, -1, road, 1_800L, speed)
        assertEquals(afterFirst, reads, "mẫu parse hụt không được chạm tới nguồn tốc độ")

        // Episode MỚI (mẫu đầu tiên của một nguồn) cũng không cần: chưa có mẫu trước để tính quãng đi.
        assertEquals(0, afterFirst, "mẫu đầu của một episode chưa có gì để tính quãng ⇒ chưa cần tốc độ")

        // Còn nhánh THẬT SỰ cần thì phải đọc: mẫu tiếp theo trong cùng episode đi qua bước tính quãng đi.
        g.accept(waze, 280, road, 2_600L, speed)
        assertTrue(reads > afterFirst, "nhánh tính quãng đi PHẢI đọc tốc độ thật")
    }

    /** Quá tải nhận GIÁ TRỊ vẫn phải cho ra y hệt quá tải nhận LAMBDA (mọi test cũ ở trên là bằng chứng). */
    @Test
    fun `qua tai gia-tri va qua tai lambda cho ket qua giong het`() {
        val a = TurnDistancePlausibility()
        val b = TurnDistancePlausibility()
        var t = 1_000L
        var m = 400
        repeat(6) {
            val da = a.accept(waze, m, road, t, 12.0)
            val db = b.accept(waze, m, road, t) { 12.0 }
            assertEquals(da, db, "hai quá tải phải tương đương tuyệt đối")
            t += 800; m -= 15
        }
    }

    /** T18 — khoá degrade-safe khi parse hụt MỘT mẫu: không ra số, nhưng cũng không phá chuỗi đang xây. */
    @Test
    fun `meters am — tra UNKNOWN va KHONG pha chuoi warmup`() {
        val g = TurnDistancePlausibility()
        g.accept(waze, 300, road, 1_000L, 10.0)                 // streak 1
        g.accept(waze, 280, road, 1_800L, 10.0)                 // streak 2
        val miss = g.accept(waze, -1, road, 2_600L, 10.0)
        assertEquals(Verdict.NO_SAMPLE, miss.verdict)
        assertEquals(TurnDistancePlausibility.UNKNOWN, miss.meters)
        val back = g.accept(waze, 260, road, 3_400L, 10.0)
        assertEquals(Verdict.ACCEPT, back.verdict, "mẫu hụt không được làm mất công 2 mẫu trước")
        assertEquals(260, back.meters)
    }
}
