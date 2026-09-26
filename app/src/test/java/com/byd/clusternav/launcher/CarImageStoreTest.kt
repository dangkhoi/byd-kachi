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

    @Test
    fun `khung nho hon nua anh - sample 2 roi ha tiep ve khung`() {
        val plan = CarImageStore.decodePlan(SRC_W, SRC_H, 320, 640)
        assertEquals(2, plan.sample)
        val (w, h) = CarImageStore.plannedSize(SRC_W, SRC_H, plan)
        // phủ khung (≥ cả hai chiều, = khung ở chiều siết) — cùng ngữ nghĩa WallpaperStore.loadScaled
        assertTrue(w >= 320 && h >= 640 && (w == 320 || h == 640), "cỡ $w×$h phải phủ 320×640 và chạm một cạnh")
        assertTrue(mb(w, h) < 0.9, "≤ 0,9 MiB cho khung 320×640 (cũ: 339×698 = 0,90 MiB; nguyên cỡ 3,61 MiB)")
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
     * Tính chất chung trên lưới khung: bitmap dự kiến PHỦ khung (dung sai 3 px: `inTargetDensity` là số NGUYÊN nên
     * chiều còn lại làm tròn hụt tối đa ≈ tỉ lệ ảnh 2,06 px) và không thừa pixel ngoài tỉ lệ.
     */
    @Test
    fun `moi khung - bitmap du kien phu khung va khong lon hon can thiet`() {
        for (rw in listOf(64, 128, 200, 300, 340, 512, 700)) for (rh in listOf(96, 256, 400, 640, 700, 1000, 1400)) {
            val plan = CarImageStore.decodePlan(SRC_W, SRC_H, rw, rh)
            val (w, h) = CarImageStore.plannedSize(SRC_W, SRC_H, plan)
            val coverW = minOf(rw, SRC_W); val coverH = minOf(rh, SRC_H)
            assertTrue(w + 3 >= coverW && h + 3 >= coverH, "khung $rw×$rh: $w×$h không phủ")
            // chiều siết chạm khung (±3, cùng dung sai làm tròn) ⇒ không thừa pixel ngoài tỉ lệ
            assertTrue(kotlin.math.abs(w - rw) <= 3 || kotlin.math.abs(h - rh) <= 3 || !plan.scaled,
                "khung $rw×$rh: $w×$h thừa (plan=$plan)")
        }
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
