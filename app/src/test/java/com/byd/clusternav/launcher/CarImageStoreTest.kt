package com.byd.clusternav.launcher

import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ CLOSEOUT 2026-09-25 · R3 — ẢNH XE: giải mã ĐÚNG khung + kho DÙNG CHUNG (audit RAM §8) ══════════════════════
 *
 * Phần thuần của [CarImageStore] (không Android): kế hoạch giải mã, khoá cache, LRU. Ba lớp vẽ xe (lốp · cửa · mini)
 * đi qua đúng các hàm này, nên khoá ở đây = khoá số bản bitmap sống + cỡ mỗi bản.
 *
 * Mốc ảnh MẶC ĐỊNH đóng theo APK: 678×1397 (= 3,79 MB thập phân = 3,61 MiB ARGB_8888 nếu giải mã nguyên cỡ; số MB
 * trong bài này là MiB, cùng đơn vị `dumpsys meminfo`).
 */
class CarImageStoreTest {

    private companion object {
        const val SRC_W = 678
        const val SRC_H = 1397
        const val BPP = 4L
        fun mb(w: Int, h: Int): Double = w.toLong() * h * BPP / 1_048_576.0
    }

    // ── khoá cache ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `bucket gom len bac 32 va khong am`() {
        assertEquals(0, CarImageStore.bucket(0))
        assertEquals(0, CarImageStore.bucket(-7))
        assertEquals(32, CarImageStore.bucket(1))
        assertEquals(32, CarImageStore.bucket(32))
        assertEquals(64, CarImageStore.bucket(33))
        assertEquals(640, CarImageStore.bucket(640))
    }

    @Test
    fun `cung nguon va cung bac cua khung thi cung khoa, khac nguon hoac khac bac thi khac khoa`() {
        val a = CarImageStore.cacheKey("default:car/default-car.png", 300, 630)
        val b = CarImageStore.cacheKey("default:car/default-car.png", 320, 640)   // cùng bậc (320×640)
        assertEquals(a, b, "đổi cỡ trong cùng bậc 32 px KHÔNG được sinh khoá mới (tránh giải mã lại khi cỡ dao động)")
        assertEquals(320 to 640, a.w to a.h, "khoá mang cỡ ĐÃ gom — cũng là cỡ yêu cầu khi giải mã")
        assertTrue(a != CarImageStore.cacheKey("default:car/default-car.png", 321, 640), "sang bậc khác ⇒ khoá khác")
        assertTrue(a != CarImageStore.cacheKey("/x/car/me.png|123", 300, 630), "đổi ảnh (dấu vết tệp) ⇒ khoá khác")
    }

    // ── kế hoạch giải mã ───────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Ca XẤU NHẤT của đường cũ (chỉ `inSampleSize`): khung 340×700 — `sampleSize` = 1 vì 1397/2 = 698 < 700 ⇒ giải mã
     * NGUYÊN CỠ 678×1397 = 3,61 MiB cho một khung cần 0,90 MiB (×4). Đường mới hạ tiếp bằng `inScaled` về đúng khung.
     */
    @Test
    fun `anh mac dinh vao khung 340x700 - duong cu 3,61 MiB, ke hoach moi ~0,9 MiB`() {
        val plan = CarImageStore.decodePlan(SRC_W, SRC_H, 340, 700)
        assertEquals(1, plan.sample, "sampleSize luỹ thừa 2 không giảm được (698 < 700) — đúng là ca đường cũ hụt")
        assertTrue(plan.scaled, "phải có bậc hai (inScaled) để hạ về khung")
        val (w, h) = CarImageStore.plannedSize(SRC_W, SRC_H, plan)
        assertTrue(w in 339..341 && h in 699..701, "cỡ dự kiến $w×$h phải ≈ 340×700")
        val oldMb = mb(SRC_W / plan.sample, SRC_H / plan.sample)
        val newMb = mb(w, h)
        assertTrue(oldMb > 3.5 && newMb < 1.0, "cũ ${"%.2f".format(oldMb)} MiB → mới ${"%.2f".format(newMb)} MiB")
    }

    /**
     * ⚠ 2026-09-26 — bài này ĐỔI ngữ nghĩa từ **cover** sang **fit**, vì ảnh xe được vẽ letterbox
     * (`CarImageLayer.fitRect`) nên pixel cover cấp thêm ở chiều rộng hơn **không bao giờ được vẽ**.
     * Khung 320×640: cover cho `320×659` = 210 880 px (0,80 MiB) → fit cho `311×640` = 199 040 px (0,76 MiB).
     */
    @Test
    fun `khung nho hon nua anh - sample 2 roi ha tiep VUA LOT khung`() {
        val plan = CarImageStore.decodePlan(SRC_W, SRC_H, 320, 640)
        assertEquals(2, plan.sample)
        val (w, h) = CarImageStore.plannedSize(SRC_W, SRC_H, plan)
        // lọt khung (≤ cả hai chiều, = khung ở chiều siết) — KHÁC WallpaperStore.loadScaled, xem KDoc CarImageStore.fitWidth
        assertTrue(w <= 320 && h <= 640 && (w == 320 || h == 640), "cỡ $w×$h phải LỌT 320×640 và chạm một cạnh")
        assertEquals(311 to 640, w to h, "fit: 311×640 = 199 040 px; cover cũ là 320×659 = 210 880 px")
        assertTrue(mb(w, h) < 0.8, "≤ 0,8 MiB cho khung 320×640 (cover: 0,80 MiB; nguyên cỡ 3,61 MiB)")
    }

    /**
     * Ca [ĐO] mà nợ RAM được viết ra từ đó (KDoc `CarImageStore.DecodePlan`): khung **210×554** của bảng lốp.
     * cover `269×554` = 149 026 px → fit `210×432` = 90 720 px ⇒ **1,64×** pixel đã trả cho không.
     */
    @Test
    fun `khung 210x554 cua bang lop - cover 149026 px, fit 90720 px`() {
        val plan = CarImageStore.decodePlan(SRC_W, SRC_H, 210, 554)
        val (w, h) = CarImageStore.plannedSize(SRC_W, SRC_H, plan)
        assertEquals(210 to 432, w to h, "fit phải chạm cạnh RỘNG (210) chứ không phải cạnh CAO")
        assertEquals(149_026, 269 * 554, "số cover ghi trong KDoc — để ai đổi doc thì đổi cả đây")
        assertTrue(w * h * 1.6 < 269 * 554, "fit phải tiết kiệm ≥ 1,6× pixel so với cover (${w * h} vs ${269 * 554})")
    }

    @Test
    fun `anh nho hon khung thi khong phong to, khong sample`() {
        val plan = CarImageStore.decodePlan(200, 400, 320, 640)
        assertEquals(CarImageStore.DecodePlan(1, 0, 0), plan)
        assertEquals(200 to 400, CarImageStore.plannedSize(200, 400, plan))
    }

    @Test
    fun `khung hoac anh khong hop le thi ke hoach mac dinh, khong nem`() {
        assertEquals(CarImageStore.DecodePlan(1, 0, 0), CarImageStore.decodePlan(0, 0, 320, 640))
        assertEquals(CarImageStore.DecodePlan(1, 0, 0), CarImageStore.decodePlan(SRC_W, SRC_H, 0, 640))
    }

    /**
     * Tính chất chung trên lưới khung: bitmap dự kiến **LỌT** khung và **chạm** đúng một cạnh (dung sai 1 px —
     * `inTargetDensity` là số nguyên nên chiều còn lại lệch ≤ 1 px sau khi làm tròn nửa lên).
     *
     * ⚠ Dung sai siết từ 3 px xuống 1 px cùng lượt đổi cover→fit: với `+ 0.5f` thì chiều siết chạm ĐÚNG khung, nên
     * 3 px là chỗ trống để một lượt sửa sau lặng lẽ cấp thừa/hụt pixel mà bài này vẫn xanh.
     */
    @Test
    fun `moi khung - bitmap du kien LOT khung va cham dung mot canh`() {
        for (rw in listOf(64, 128, 200, 300, 340, 512, 700)) for (rh in listOf(96, 256, 400, 640, 700, 1000, 1400)) {
            val plan = CarImageStore.decodePlan(SRC_W, SRC_H, rw, rh)
            val (w, h) = CarImageStore.plannedSize(SRC_W, SRC_H, plan)
            if (!plan.scaled) {
                // ảnh nhỏ hơn khung ở CẢ HAI chiều ⇒ không phóng to, giữ nguyên cỡ đã sample
                assertTrue(w <= SRC_W && h <= SRC_H, "khung $rw×$rh: $w×$h lớn hơn ảnh gốc mà không hề hạ cỡ")
                continue
            }
            assertTrue(w <= rw + 1 && h <= rh + 1, "khung $rw×$rh: $w×$h TRÀN khung ⇒ pixel không bao giờ được vẽ")
            assertTrue(kotlin.math.abs(w - rw) <= 1 || kotlin.math.abs(h - rh) <= 1,
                "khung $rw×$rh: $w×$h không chạm cạnh nào ⇒ lớp vẽ phải phóng ảnh lên (plan=$plan)")
        }
    }

    /**
     * Cỡ bitmap mà đường COVER (`WallpaperStore.loadScaled`, trước 2.76 dùng cho ảnh NGƯỜI DÙNG) cho ra — dựng lại
     * đúng hai bậc của nó: `sampleSize` rồi `scaledWidth` (maxOf), làm tròn như `BitmapFactory` (nửa lên).
     */
    private fun coverSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Pair<Int, Int> {
        val s = WallpaperStore.sampleSize(srcW, srcH, reqW, reqH)
        val sw = srcW / s; val sh = srcH / s
        val tw = WallpaperStore.scaledWidth(sw, sh, reqW, reqH)
        if (tw <= 0) return sw to sh
        val f = tw.toFloat() / sw
        return (sw * f + 0.5f).toInt() to (sh * f + 0.5f).toInt()
    }

    /**
     * 2.76 · R11 (3) — ẢNH NGƯỜI DÙNG: trước/sau khi đổi từ cover sang fit, đo bằng số.
     *
     * Ca xấu nhất là ca có thật: ảnh chụp điện thoại (12 MP dọc 3000×4000, hoặc ảnh xe cắt nền 1080×1920) bỏ vào
     * `car/`. Với khung lốp 210×554 (ô thật) và khung xe mini, cover cấp chiều rộng THỪA mà `CarImageLayer.fitRect`
     * (letterbox) không bao giờ vẽ. Ảnh mặc định (678×1397) đã fit từ closeout; đây là phần còn nợ.
     */
    @Test
    fun `anh NGUOI DUNG - cover (truoc 2,76) vs fit (nay) tren khung lop that`() {
        // 3000×4000 vào 210×554: sample 4 ⇒ 750×1000; cover maxOf(0,28; 0,554) ⇒ 415×553; fit minOf ⇒ 210×280.
        val cover = coverSize(3000, 4000, 210, 554)
        val fitPlan = CarImageStore.decodePlan(3000, 4000, 210, 554)
        val fit = CarImageStore.plannedSize(3000, 4000, fitPlan)
        assertEquals(415 to 553, cover, "đường cũ (cover) cho ảnh điện thoại 3000×4000")
        assertEquals(210 to 280, fit, "đường mới (fit) — chạm cạnh RỘNG của khung, không tràn")
        val ratio = cover.first.toLong() * cover.second / (fit.first.toLong() * fit.second).toDouble()
        assertTrue(ratio > 3.8 && ratio < 4.0, "cover tốn ${"%.2f".format(ratio)}× pixel so với fit (KDoc nói 3,9×)")
        assertTrue(fit.first <= 210 && fit.second <= 554, "fit phải LỌT khung")
        assertTrue(cover.first > 210, "cover TRÀN khung theo chiều rộng — đó là pixel không bao giờ được vẽ")
        // Ảnh xe cắt nền tỉ lệ 9:16 (1080×1920) vào cùng khung: cover 311×553 vs fit 210×373 ⇒ 2,2×.
        val cover2 = coverSize(1080, 1920, 210, 554)
        val fit2 = CarImageStore.plannedSize(1080, 1920, CarImageStore.decodePlan(1080, 1920, 210, 554))
        assertEquals(311 to 553, cover2)
        assertEquals(210 to 373, fit2)
        // Ảnh NGANG (4000×3000) vào khung DỌC: cover siết theo chiều cao (738×554) — fit theo chiều rộng (210×158) ⇒ 12,3×.
        val cover3 = coverSize(4000, 3000, 210, 554)
        val fit3 = CarImageStore.plannedSize(4000, 3000, CarImageStore.decodePlan(4000, 3000, 210, 554))
        assertEquals(738 to 554, cover3)
        assertEquals(210 to 158, fit3)
        assertTrue(fit3.first <= 210 && fit3.second <= 554)
    }

    @Test
    fun `fitWidth lay minOf va khong phong to, khong nem khi tham so xau`() {
        assertEquals(0, CarImageStore.fitWidth(200, 400, 320, 640), "ảnh nhỏ hơn khung ⇒ không hạ, cũng không phóng")
        assertEquals(0, CarImageStore.fitWidth(320, 640, 320, 640), "vừa khít thì không hạ")
        assertEquals(0, CarImageStore.fitWidth(0, 0, 320, 640))
        assertEquals(0, CarImageStore.fitWidth(339, 698, 0, 640))
        assertEquals(0, CarImageStore.fitWidth(-5, -5, 320, 640))
        // 339×698 vào 210×554: minOf(210/339, 554/698) = 0,619 ⇒ 210 (cover lấy maxOf ⇒ 269)
        assertEquals(210, CarImageStore.fitWidth(339, 698, 210, 554))
        assertEquals(269, WallpaperStore.scaledWidth(339, 698, 210, 554), "hình NỀN vẫn cover — hai hàm, hai câu hỏi")
    }

    // ── LRU dùng chung ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `ba lop cung khoa - giai ma DUNG MOT lan, nhan cung mot ban`() {
        val lru = CarImageStore.SharedLru<CarImageStore.CacheKey, String>(CarImageStore.MAX_SHARED)
        val loads = AtomicInteger()
        val key = CarImageStore.cacheKey("default:car/default-car.png", 300, 650)
        val got = (1..3).map { lru.getOrLoad(key) { loads.incrementAndGet(); "bmp#${loads.get()}" } }
        assertEquals(1, loads.get(), "3 lớp (lốp · cửa · mini) cùng khung ⇒ 1 lượt giải mã")
        assertSame(got[0], got[1]); assertSame(got[1], got[2])
    }

    @Test
    fun `nap that bai thi khong cat, lan sau hoi lai`() {
        val lru = CarImageStore.SharedLru<String, String>(3)
        val loads = AtomicInteger()
        assertNull(lru.getOrLoad("k") { loads.incrementAndGet(); null })
        assertEquals(0, lru.size())
        assertEquals("ok", lru.getOrLoad("k") { loads.incrementAndGet(); "ok" })
        assertEquals(2, loads.get())
    }

    @Test
    fun `vuot toi da thi bo ban CU NHAT theo lan dung, khong phai theo lan cat`() {
        val lru = CarImageStore.SharedLru<String, String>(3)
        lru.put("a", "A"); lru.put("b", "B"); lru.put("c", "C")
        lru.get("a")                       // a vừa được dùng ⇒ b là cũ nhất
        lru.put("d", "D")
        assertEquals(3, lru.size())
        assertNull(lru.get("b"), "b (cũ nhất theo LẦN DÙNG) phải bị đẩy")
        assertEquals(listOf("c", "a", "d"), lru.keys(), "thứ tự truy cập: c, a, d (đọc a đã làm mới a)")
    }

    @Test
    fun `hai luot nap song song cung khoa - ban cat truoc thang, ban sau bi bo`() {
        val lru = CarImageStore.SharedLru<String, String>(3)
        // mô phỏng: trong lúc load() của luồng 1 chạy, luồng 2 đã cất "first"
        val got = lru.getOrLoad("k") { lru.put("k", "first"); "second" }
        assertEquals("first", got, "không được có hai bản cho một khoá — bản đã trong kho thắng")
        assertEquals("first", lru.get("k"))
    }

    @Test
    fun `MAX_SHARED bang so bang dung anh xe`() {
        // 3 bảng (TyreBoardView/CarImageView · DoorBoardView · CarMiniView) ⇒ tối đa 3 cỡ khung khác nhau cùng sống.
        assertEquals(3, CarImageStore.MAX_SHARED)
    }
}

/**
 * ═══ CLOSE-5 · THỬ-LẠI THEO ĐỒNG HỒ KHI NẠP HỎNG (backlog 2026-09-26, review P3) ═════════════════════════════════
 *
 * Trước: `CarImageLayer.ensure()` ghi `loadedKey` cả khi nạp HỎNG ⇒ chặn được vòng nạp vô hạn nhưng placeholder MÃI
 * tới khi khung/tệp đổi. Khoá bài học ở phần THUẦN [CarImageStore.LoadRetry] (lớp vẽ không dựng được off-car — stub
 * android.jar ném ở `Handler`): giãn 1 s → ×2 → trần 60 s, nạp được ⇒ quên sạch. Cùng khuôn `HalAbsentCache`.
 */
class CarImageLoadRetryTest {

    @Test
    fun `chua hong lan nao thi thu ngay`() {
        val r = CarImageStore.LoadRetry()
        assertTrue(r.shouldTry(0))
        assertTrue(r.shouldTry(Long.MAX_VALUE))
        assertEquals(0, r.failures)
        assertEquals(0L, r.nextRetryAt)
    }

    /** Ca chính của backlog: hỏng 3 lần ⇒ lần 4 CHỈ sau mốc (1 s + 2 s + 4 s), không phải mỗi khung vẽ. */
    @Test
    fun `nap hong 3 lan - lan thu 4 chi sau moc 7 s, khong phai moi khung ve`() {
        val r = CarImageStore.LoadRetry(firstRetryMs = 1_000, maxRetryMs = 60_000)
        assertEquals(1_000L, r.recordFailure(nowMs = 0))          // mốc 1 000
        assertTrue(!r.shouldTry(999) && r.shouldTry(1_000), "lần 2 đúng sau 1 s")
        assertEquals(2_000L, r.recordFailure(nowMs = 1_000))      // mốc 3 000
        assertTrue(!r.shouldTry(2_999) && r.shouldTry(3_000), "lần 3 đúng sau thêm 2 s")
        assertEquals(4_000L, r.recordFailure(nowMs = 3_000))      // mốc 7 000
        assertEquals(3, r.failures)
        assertEquals(7_000L, r.nextRetryAt)
        for (t in listOf(3_000L, 3_001L, 5_000L, 6_999L)) assertTrue(!r.shouldTry(t), "t=$t chưa tới mốc ⇒ giữ placeholder")
        assertTrue(r.shouldTry(7_000), "đúng mốc ⇒ được nạp lại")
    }

    @Test
    fun `nap duoc thi quen sach - thu ngay, dem lai tu dau`() {
        val r = CarImageStore.LoadRetry(firstRetryMs = 1_000, maxRetryMs = 60_000)
        repeat(4) { r.recordFailure(nowMs = it * 1_000L) }
        assertTrue(!r.shouldTry(3_001), "đang nguội")
        r.reset()
        assertTrue(r.shouldTry(3_001), "sau reset thử ngay, không đợi mốc cũ")
        assertEquals(0, r.failures)
        assertEquals(1_000L, r.recordFailure(nowMs = 3_001), "hỏng lại sau reset ⇒ đếm lại từ 1 s, không giữ nhịp giãn cũ")
    }

    @Test
    fun `gian gap doi nhung co tran 60 s va khong tran so`() {
        val r = CarImageStore.LoadRetry()
        assertEquals(0L, r.delayMs(0))
        assertEquals(1_000L, r.delayMs(1))
        assertEquals(2_000L, r.delayMs(2))
        assertEquals(4_000L, r.delayMs(3))
        assertEquals(32_000L, r.delayMs(6))
        assertEquals(60_000L, r.delayMs(7), "64 s bị kẹp về trần 60 s")
        assertEquals(60_000L, r.delayMs(40), "số mũ lớn không tràn Long, vẫn đúng trần")
        assertEquals(60_000L, r.delayMs(Int.MAX_VALUE))
        assertEquals(CarImageStore.RETRY_FIRST_MS to CarImageStore.RETRY_MAX_MS, 1_000L to 60_000L, "mặc định = khuôn HalAbsentCache")
    }
}
