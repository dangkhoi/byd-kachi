package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * FIX286 · R-SC2 (spec `kachi-286-field-fixes.html` §3.10) — phép đo "app của ô còn sống không" lúc chạm lối tắt. Fixture
 * NGUYÊN VĂN `am stack list` (`core/src/test/resources/diagnostics/`, CLAUDE.md §10):
 *  - `…-2026-10-01-embedded-initial` — VietMap trong màn ảo ô (display 2), máy ảo A10;
 *  - `…-2026-10-02-tm2-detached` — VietMap đã tách ra display 0 (T-M2), màn ảo ô rỗng;
 *  - `…-api34-cluster-…` — định dạng `RootTask id=` (A12+, cùng họ DL5);
 *  - `…-2026-10-03-sc-maps-*` — Google Maps đặt TẠM đè ô widget rồi `am force-stop` (đo của chính FIX286 R-SC, §9).
 *
 * Mỗi ca khoá một hướng sai có thật: coi bản đọc rỗng là "đã đóng" (một lần kênh trả rỗng = giết app đang sống), coi task
 * ở display khác là "đã đóng" (giết app đang toàn màn / đang chiếu cụm), khớp tên gói theo tiền tố, bỏ sót định dạng A12.
 */
class SlotPresenceTest {

    private fun fx(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/diagnostics/am-stack-list-$name.txt")) { "thiếu fixture $name" }
            .bufferedReader().readText()

    private val vietmap = "vn.vietmap.live"
    private val maps = "com.google.android.apps.maps"

    @Test
    fun `app co task tren man ao cua o - IN_SLOT`() {
        assertEquals(SlotPresence.IN_SLOT, SlotPresence.of(fx("emulator-2026-10-01-embedded-initial"), vietmap, vd = 2))
        assertEquals(SlotPresence.IN_SLOT, SlotPresence.of(fx("emulator-2026-10-03-sc-maps-in-slot"), maps, vd = 231))
    }

    /**
     * Đúng ca owner 03/10, đo lại trên máy ảo (§9 FIX286 R-SC): Google Maps trong màn ảo 231 của ô 1 ⇒ `am force-stop` ⇒
     * 1,5 s sau stack của màn ảo biến mất, không còn dòng nào của Maps ⇒ GONE (bản 2.85 vẫn trả `Noop` cho lần chạm này).
     */
    @Test
    fun `ca owner - Maps trong o bi tat - GONE`() =
        assertEquals(SlotPresence.GONE, SlotPresence.of(fx("emulator-2026-10-03-sc-maps-force-stopped"), maps, vd = 231))

    @Test
    fun `app co task o display khac man ao cua o - ELSEWHERE, khong phai GONE`() {
        assertEquals(SlotPresence.ELSEWHERE, SlotPresence.of(fx("emulator-2026-10-01-embedded-initial"), vietmap, vd = 3))
        // T-M2 [ĐO]: app đã tách ra toàn màn display 0 — mở lại vào ô sẽ force-stop đúng app người dùng đang nhìn.
        assertEquals(SlotPresence.ELSEWHERE, SlotPresence.of(fx("emulator-2026-10-02-tm2-detached"), vietmap, vd = 2))
    }

    @Test
    fun `khong con task nao - GONE`() =
        assertEquals(SlotPresence.GONE, SlotPresence.of(fx("emulator-2026-10-01-embedded-initial"), maps, vd = 2))

    @Test
    fun `dinh dang A12 RootTask doc duoc - khong roi ve UNKNOWN`() {
        val a12 = fx("api34-cluster-1920x720-d240")
        assertEquals(SlotPresence.ELSEWHERE, SlotPresence.of(a12, "com.chisadin.wazemod", vd = 2))
        assertEquals(SlotPresence.GONE, SlotPresence.of(a12, maps, vd = 2))
    }

    @Test
    fun `ban doc rong hoac la - UNKNOWN, khong bao gio GONE`() {
        listOf("", "   \n", "Error: unknown command 'stack'", "cmd: Can't find service: activity").forEach {
            assertEquals(SlotPresence.UNKNOWN, SlotPresence.of(it, maps, vd = 2), "bản đọc '$it'")
        }
    }

    @Test
    fun `o khong co man ao hoac goi rong - UNKNOWN`() {
        val ok = fx("emulator-2026-10-01-embedded-initial")
        assertEquals(SlotPresence.UNKNOWN, SlotPresence.of(ok, vietmap, vd = 0), "display 0 không phải màn ảo ô")
        assertEquals(SlotPresence.UNKNOWN, SlotPresence.of(ok, vietmap, vd = -1))
        assertEquals(SlotPresence.UNKNOWN, SlotPresence.of(ok, "", vd = 2))
    }

    @Test
    fun `khop TRON ten goi - com_foo khong dinh com_foobar`() {
        val out = """
            Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
              taskId=1: com.byd.launcher/.Home bounds=[0,0][1920,1080] userId=0 visible=true
            Stack id=7 bounds=[0,0][900,600] displayId=4 userId=0
              taskId=9: com.foobar/.Main bounds=[0,0][900,600] userId=0 visible=true
        """.trimIndent()
        assertEquals(SlotPresence.GONE, SlotPresence.of(out, "com.foo", vd = 4))
        assertEquals(SlotPresence.IN_SLOT, SlotPresence.of(out, "com.foobar", vd = 4))
    }
}
