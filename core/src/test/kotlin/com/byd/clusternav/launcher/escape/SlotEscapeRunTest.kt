package com.byd.clusternav.launcher.escape

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R7 — chuỗi lệnh của MỘT lượt, với shell ghi âm trả nguyên văn bản đọc máy ảo 09/10 (xem KDoc [SlotEscapePlanTest]).
 * Khoá: dấu bền ghi TRƯỚC lệnh đổi chế độ; thử mã 59 (chỉ đọc) trước mã 89; đọc lại không khớp ⇒ trả mode 1 + xoá dấu; không
 * lệnh nào giết app; không có dấu ⇒ 0 lệnh.
 */
class SlotEscapeRunTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture core/src/test/resources/diagnostics/$name.txt")

    private val slot = PxRect(19, 89, 1901, 985)
    private val homes = listOf("com.byd.launcher/com.byd.clusternav.launcher.KachiHome")
    private val waze = "com.waze"

    /** Shell ghi âm: mỗi lần `am stack list` trả bản kế trong [lists] (bản cuối lặp lại); mã 59 trả [bounds]. */
    private class Rec(
        private val lists: List<String>,
        private val bounds: String,
        private val journal: MutableList<String>,
        /** R10 — đầu ra lệnh có `am task focus` (đã qua rào: [ĐO máy ảo] in đúng dòng này) · `dumpsys activity recents`. */
        private val focus: String? = "Setting focus to task 301",
        private val recents: String = "",
    ) : (String) -> String {
        val calls = ArrayList<String>()
        private var i = 0
        override fun invoke(c: String): String {
            calls += c; journal += "sh:$c"
            return when {
                c == "am stack list" -> lists[minOf(i++, lists.size - 1)]
                c.startsWith("service call activity_task 59 ") -> bounds
                c.startsWith("service call activity_task 89 ") -> "Result: Parcel(00000000    '....')"
                "am task focus" in c -> focus ?: throw java.io.IOException("kênh rớt")
                c == SlotEscapePlan.RECENTS_CMD -> recents
                else -> ""
            }
        }
    }

    private class Store(var list: List<EscapeMarker> = emptyList(), val journal: MutableList<String>, val ok: Boolean = true) : SlotEscapeRun.MarkerStore {
        override fun read() = list
        override fun write(list: List<EscapeMarker>): Boolean { journal += "write:${list.map { it.pkg }}"; if (ok) this.list = list; return ok }
    }

    private fun run(sh: Rec, store: Store, tops: SlotEscapeRun.TopMemory = SlotEscapeRun.TopMemory()) =
        SlotEscapeRun(sh, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, store, tops = tops)

    @Test
    fun `nhan - dau ghi truoc ma 89, thu ma 59 truoc, doc lai khop`() {
        val j = ArrayList<String>()
        val sh = Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-waze-fullscreen-top"), fixture("am-stack-list-emulator-2026-10-09-r7-waze-freeform-top")),
            fixture("parcel-emulator-2026-10-09-r7-bounds-fullscreen"), j)
        val st = Store(journal = j)
        val r = run(sh, st).adopt(0, waze, waze, slot, 240)
        assertTrue(r.ok, r.line)
        assertTrue(r.onTop, "app đang ở đỉnh ⇒ lệnh nhận toTop ⇒ bên gọi được che ngay")
        assertEquals(301, r.task)
        assertEquals(
            listOf("am stack list", "service call activity_task 59 i32 301", "service call activity_task 89 i32 301 i32 5 i32 1",
                "am task resize 301 19 89 1901 985", "am stack list"),
            sh.calls,
        )
        val w = j.indexOf("write:[com.waze]"); val mode = j.indexOf("sh:service call activity_task 89 i32 301 i32 5 i32 1")
        assertTrue(w in 0 until mode, "dấu phải ghi TRƯỚC lệnh đổi chế độ: $j")
        assertEquals(listOf(EscapeMarker(waze, 0, 301, slot)), st.list)
    }

    @Test
    fun `nhan - doc lai khong khop thi tra mode 1 va xoa dau`() {
        val j = ArrayList<String>()
        val full = fixture("am-stack-list-emulator-2026-10-09-r7-waze-fullscreen-top")
        val sh = Rec(listOf(full, full), fixture("parcel-emulator-2026-10-09-r7-bounds-fullscreen"), j)
        val st = Store(journal = j)
        val r = run(sh, st).adopt(0, waze, waze, slot, 240)
        assertFalse(r.ok)
        assertEquals("service call activity_task 89 i32 301 i32 1 i32 0", sh.calls.last())
        assertTrue(st.list.isEmpty(), "trả xong ⇒ dấu xoá")
    }

    @Test
    fun `nhan - ma 59 khong tra khung hoac khong ghi duoc dau thi khong lenh ghi nao`() {
        val j = ArrayList<String>()
        val sh = Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-waze-fullscreen-top")), fixture("parcel-emulator-2026-10-09-r7-bounds-notask"), j)
        assertFalse(run(sh, Store(journal = j)).adopt(0, waze, waze, slot, 240).ok)
        assertTrue(sh.calls.none { it.contains("activity_task 89") || it.startsWith("am task resize") }, "${sh.calls}")

        val sh2 = Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-waze-fullscreen-top")), fixture("parcel-emulator-2026-10-09-r7-bounds-fullscreen"), j)
        assertFalse(run(sh2, Store(journal = j, ok = false)).adopt(0, waze, waze, slot, 240).ok)
        assertTrue(sh2.calls.none { it.contains("activity_task 89") }, "${sh2.calls}")

        val sh3 = Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-waze-fullscreen-top")), "", j)
        assertFalse(run(sh3, Store(journal = j)).adopt(0, waze, "com.other", slot, 240).ok)
        assertEquals(listOf("am stack list"), sh3.calls, "ô không còn hiện gói ⇒ chỉ một lượt đọc")
    }

    @Test
    fun `nhan - ma 89 nem ngoai le thi khong resize va xoa dau`() {
        val j = ArrayList<String>()
        val full = fixture("am-stack-list-emulator-2026-10-09-r7-waze-fullscreen-top")
        val sh = object : (String) -> String {
            val calls = ArrayList<String>()
            override fun invoke(c: String): String { calls += c; return when {
                c == "am stack list" -> full
                c.startsWith("service call activity_task 59 ") -> fixture("parcel-emulator-2026-10-09-r7-bounds-fullscreen")
                c.startsWith("service call activity_task 89 ") -> "Result: Parcel(ffffffff 0000004a '....J...')"
                else -> "" } }
        }
        val st = Store(journal = j)
        val r = SlotEscapeRun(sh, TaskBinderCodes.ANDROID_10_R47, null, homes, st).adopt(0, waze, waze, slot, 240)
        assertFalse(r.ok)
        assertTrue(sh.calls.none { it.startsWith("am task resize") }, "${sh.calls}")
        assertTrue(st.list.isEmpty())
    }

    @Test
    fun `doi chieu - khong co dau thi 0 lenh`() {
        val sh = Rec(listOf(""), "", ArrayList())
        assertEquals(SlotEscapeRun.Reconciled.NOTHING, run(sh, Store(journal = ArrayList())).reconcile({ waze }, { true }, { slot }, refront = true))
        assertFalse(run(sh, Store(journal = ArrayList())).claims(0, waze))
        assertTrue(sh.calls.isEmpty())
    }

    @Test
    fun `doi chieu - man nha vua len thi dua app dang quan len tren, khung doi thi keo lai`() {
        val j = ArrayList<String>()
        val sh = Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-freeform-under-home")), "", j)
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), j)
        val moved = PxRect(19, 120, 1901, 985)   // vd bật thanh trên ⇒ ô dời xuống
        val r = run(sh, st, SlotEscapeRun.TopMemory().apply { focusWorks = true }).reconcile({ waze }, { true }, { moved }, refront = true)
        assertEquals("am task resize 301 19 120 1901 985", sh.calls[1])
        assertTrue(sh.calls[2].contains("am task focus 301"), "R10: focus đúng task của bản đọc: ${sh.calls[2]}")
        assertEquals(3, sh.calls.size, "focus đã xác nhận trong tiến trình ⇒ không đọc lại: ${sh.calls}")
        assertEquals(listOf(EscapeMarker(waze, 0, 301, moved)), r.onTop)
        assertEquals(moved, st.list.single().rect)
    }

    @Test
    fun `doi chieu - o doi app thi tra, Home truoc khi app dang noi tren man nha`() {
        val j = ArrayList<String>()
        val sh = Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-waze-freeform-top")), "", j)
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), j)
        val r = run(sh, st).reconcile({ "com.google.android.youtube" }, { true }, { slot }, refront = false)
        assertEquals(listOf(waze), r.released)
        assertTrue(sh.calls[1].contains("android.intent.category.HOME"), sh.calls[1])
        assertEquals("service call activity_task 89 i32 301 i32 1 i32 0", sh.calls[2])
        assertTrue(st.list.isEmpty())
        assertTrue(sh.calls.none { it.contains("stack remove") || it.contains("force-stop") })
    }

    /** Soát Pass 7: Kachi không là home mặc định ⇒ KHÔNG `GO_HOME` (mở launcher BYD đè Kachi) — trả chỉ còn mode 1, dấu vẫn xoá. */
    @Test
    fun `doi chieu - Kachi khong la home mac dinh thi tra khong Home truoc`() {
        val j = ArrayList<String>()
        val sh = Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-waze-freeform-top")), "", j)
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), j)
        val r = SlotEscapeRun(sh, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, st, homeAllowed = false)
            .reconcile({ "com.google.android.youtube" }, { true }, { slot }, refront = false)
        assertEquals(listOf(waze), r.released)
        assertEquals(listOf("am stack list", "service call activity_task 89 i32 301 i32 1 i32 0"), sh.calls)
        assertTrue(sh.calls.none { it.contains("android.intent.category.HOME") }, "${sh.calls}")
        assertTrue(st.list.isEmpty())
    }

    @Test
    fun `doi chieu - task mat ma o con hien thi bao gone, giu dau`() {
        val noWaze = fixture("am-stack-list-emulator-2026-10-09-r7-waze-freeform-top").lines().filterNot { it.contains("com.waze") }.joinToString("\n")
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), ArrayList())
        val r = run(Rec(listOf(noWaze), "", ArrayList()), st).reconcile({ waze }, { true }, { slot }, refront = true)
        assertEquals(listOf(waze), r.gone.map { it.pkg })
        assertEquals(1, st.list.size, "Android còn nhớ freeform theo app ⇒ giữ dấu để lần sau trả")
    }

    @Test
    fun `host hoi - chi dung khi task freeform con song`() {
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), ArrayList())
        assertTrue(run(Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-freeform-under-home")), "", ArrayList()), st).claims(0, waze))
        assertFalse(run(Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-released-under-home")), "", ArrayList()), st).claims(0, waze))
        val none = Rec(listOf(""), "", ArrayList())
        assertFalse(run(none, st).claims(1, waze))
        assertTrue(none.calls.isEmpty(), "dấu của ô khác ⇒ 0 lệnh")
    }

    /**
     * Owner 09/10 #2 — có lớp che (lift 65 px @240dpi): xin khung cao hơn ô một thanh tiêu đề; hệ DỜI xuống dưới đỉnh ổn định
     * ([ĐO máy ảo] dời giữ cỡ) ⇒ học 36, xin lại `[19,36][1901,985]` (đáy = đáy ô), đọc lại khớp. Lần nhận sau xin thẳng khung đã trừ.
     */
    @Test
    fun `nhan co lop che - hoc dinh on dinh roi xin lai khung, lan sau 5 lenh`() {
        val j = ArrayList<String>()
        val top = fixture("am-stack-list-emulator-2026-10-09-r7-waze-freeform-top")
        val moved = top.replace("bounds=[19,89][1901,985]", "bounds=[19,36][1901,997]")
        val fit = top.replace("bounds=[19,89][1901,985]", "bounds=[19,36][1901,985]")
        val sh = Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-waze-fullscreen-top"), moved, fit), fixture("parcel-emulator-2026-10-09-r7-bounds-fullscreen"), j)
        val st = Store(journal = j)
        val tops = SlotEscapeRun.TopMemory()
        val r = SlotEscapeRun(sh, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, st, liftPx = 65, tops = tops).adopt(0, waze, waze, slot, 240)
        assertTrue(r.ok, r.line)
        assertEquals(PxRect(19, 36, 1901, 985), r.rect)
        assertEquals(36, tops.minTop)
        assertEquals(
            listOf("am stack list", "service call activity_task 59 i32 301", "service call activity_task 89 i32 301 i32 5 i32 1",
                "am task resize 301 19 24 1901 985", "am stack list", "am task resize 301 19 36 1901 985", "am stack list"),
            sh.calls,
        )
        assertEquals(PxRect(19, 36, 1901, 985), st.list.single().rect, "dấu giữ khung TASK đã đặt")

        val sh2 = Rec(listOf(fixture("am-stack-list-emulator-2026-10-09-r7-waze-fullscreen-top"), fit), fixture("parcel-emulator-2026-10-09-r7-bounds-fullscreen"), j)
        assertTrue(SlotEscapeRun(sh2, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, Store(journal = j), liftPx = 65, tops = tops)
            .adopt(0, waze, waze, slot, 240).ok)
        assertEquals(5, sh2.calls.size, "đã học ⇒ không thêm lệnh: ${sh2.calls}")
        assertEquals("am task resize 301 19 36 1901 985", sh2.calls[3])
    }

    /** Đối chiếu so khung TASK THẬT trên chính bản đọc (0 lệnh thêm): dấu cũ (khung = ô) + có lớp che ⇒ kéo lên; khớp ⇒ 0 resize. */
    @Test
    fun `doi chieu co lop che - keo khung task len, khop thi khong lenh`() {
        val under = fixture("am-stack-list-emulator-2026-10-09-r7-freeform-under-home")
        val tops = SlotEscapeRun.TopMemory().apply { minTop = 36 }
        val sh = Rec(listOf(under), "", ArrayList())
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), ArrayList())
        SlotEscapeRun(sh, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, st, liftPx = 65, tops = tops)
            .reconcile({ waze }, { true }, { slot }, refront = false)
        assertEquals(listOf("am stack list", "am task resize 301 19 36 1901 985"), sh.calls)
        assertEquals(PxRect(19, 36, 1901, 985), st.list.single().rect)

        val sh2 = Rec(listOf(under.replace("bounds=[19,89][1901,985]", "bounds=[19,36][1901,985]")), "", ArrayList())
        SlotEscapeRun(sh2, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, st, liftPx = 65, tops = tops)
            .reconcile({ waze }, { true }, { slot }, refront = false)
        assertEquals(listOf("am stack list"), sh2.calls, "khung thật đã khớp ⇒ không resize")

        // Chưa học (tiến trình mới): dấu xin [19,24] mà hệ đặt [19,36][1901,997] ⇒ học từ chính bản đọc, kéo về [19,36][1901,985].
        val fresh = SlotEscapeRun.TopMemory()
        val sh3 = Rec(listOf(under.replace("bounds=[19,89][1901,985]", "bounds=[19,36][1901,997]")), "", ArrayList())
        val st3 = Store(listOf(EscapeMarker(waze, 0, 301, PxRect(19, 24, 1901, 985))), ArrayList())
        SlotEscapeRun(sh3, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, st3, liftPx = 65, tops = fresh)
            .reconcile({ waze }, { true }, { slot }, refront = false)
        assertEquals(36, fresh.minTop)
        assertEquals("am task resize 301 19 36 1901 985", sh3.calls[1])
    }

    /**
     * Soát Pass 9 [P2] — tiến trình MỚI (BYD giết Kachi mỗi lần tắt máy ⇒ `TopMemory` rỗng), dấu `[19,36]` đang đúng khung thật: phải
     * gợi lại đỉnh 36 từ dấu và KHÔNG kéo (trước vá: xin lại `[19,24]` ⇒ hệ dời `[19,36][1901,997]` — đáy lòi khỏi ô tới mốc kế).
     */
    @Test
    fun `doi chieu Pass 9 - tien trinh moi goi lai dinh on dinh tu dau, khong lenh thua`() {
        val learnt = PxRect(19, 36, 1901, 985)
        val under = fixture("am-stack-list-emulator-2026-10-09-r7-freeform-under-home").replace("bounds=[19,89][1901,985]", "bounds=[19,36][1901,985]")
        val tops = SlotEscapeRun.TopMemory()
        val sh = Rec(listOf(under), "", ArrayList())
        val st = Store(listOf(EscapeMarker(waze, 0, 301, learnt)), ArrayList())
        val r = SlotEscapeRun(sh, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, st, liftPx = 65, tops = tops)
            .reconcile({ waze }, { true }, { slot }, refront = false)
        assertEquals(listOf("am stack list"), sh.calls, "gợi được đỉnh ⇒ khung muốn = dấu ⇒ 0 resize")
        assertEquals(36, tops.minTop)
        assertEquals(listOf(EscapeMarker(waze, 0, 301, learnt)), st.list, "dấu giữ nguyên")
        assertTrue(r.onTop.isEmpty(), "task dưới màn nhà, không đưa lên (refront=false)")
        // Không gợi khi: không lớp che · khung thật ≠ dấu · đỉnh dấu = khung lý tưởng (24) hay = đỉnh ô (89).
        assertNull(EscapeFit.seedMinTop(learnt, learnt, slot, 0))
        assertNull(EscapeFit.seedMinTop(learnt, PxRect(19, 36, 1901, 997), slot, 65))
        assertNull(EscapeFit.seedMinTop(PxRect(19, 24, 1901, 985), PxRect(19, 24, 1901, 985), slot, 65))
        assertNull(EscapeFit.seedMinTop(slot, slot, slot, 65))
    }

    /**
     * Soát Pass 9 [P2] — hệ TỪ CHỐI khung xin theo cách không học được (kẹp cỡ): khung thật ≠ khung muốn mãi. Mỗi mốc đối chiếu
     * KHÔNG được bắn lại resize (Pass 8 so với khung thật ⇒ một resize vô ích mỗi onResume/slots/panel, mãi).
     */
    @Test
    fun `doi chieu Pass 9 - he tu choi khung thi khong resize lap`() {
        val clamped = fixture("am-stack-list-emulator-2026-10-09-r7-freeform-under-home").replace("bounds=[19,89][1901,985]", "bounds=[19,89][1901,900]")
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), ArrayList())
        repeat(3) {
            val sh = Rec(listOf(clamped), "", ArrayList())
            val r = run(sh, st).reconcile({ waze }, { true }, { slot }, refront = false)
            assertEquals(listOf("am stack list"), sh.calls, "lượt ${it + 1}: không resize")
            assertTrue(r.line.contains("hệ giữ [19,89][1901,900]"), r.line)
        }
        assertEquals(slot, st.list.single().rect)
    }

    /**
     * Soát Pass 9 [P2] — tiến trình mới, dấu CŨ (khung = ô, ghi khi chưa có lớp che), nay có lớp che: kéo lên `[19,24]`, hệ dời xuống
     * ⇒ đọc lại MỘT lần trong cùng lượt, học 36, xin lại `[19,36][1901,985]` (đáy = đáy ô) — không chờ mốc kế. Lượt sau: 1 đọc, 0 resize.
     */
    @Test
    fun `doi chieu Pass 9 - keo khung roi hoc ngay trong luot, chi mot lan moi tien trinh`() {
        val under = fixture("am-stack-list-emulator-2026-10-09-r7-freeform-under-home")
        val moved = under.replace("bounds=[19,89][1901,985]", "bounds=[19,36][1901,997]")
        val tops = SlotEscapeRun.TopMemory()
        val sh = Rec(listOf(under, moved), "", ArrayList())
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), ArrayList())
        val r = SlotEscapeRun(sh, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, st, liftPx = 65, tops = tops)
            .reconcile({ waze }, { true }, { slot }, refront = false)
        assertEquals(
            listOf("am stack list", "am task resize 301 19 24 1901 985", "am stack list", "am task resize 301 19 36 1901 985"),
            sh.calls,
        )
        assertEquals(36, tops.minTop)
        assertTrue(tops.probed)
        assertEquals(PxRect(19, 36, 1901, 985), st.list.single().rect, "dấu = khung đã sửa (lớp che theo đó)")
        assertTrue(r.line.contains("learned top 36 refit [19,36][1901,985]"), r.line)

        val fit = under.replace("bounds=[19,89][1901,985]", "bounds=[19,36][1901,985]")
        val sh2 = Rec(listOf(fit), "", ArrayList())
        SlotEscapeRun(sh2, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, st, liftPx = 65, tops = tops)
            .reconcile({ waze }, { true }, { slot }, refront = false)
        assertEquals(listOf("am stack list"), sh2.calls)

        // Không học được (hệ kẹp cỡ) ⇒ vẫn chỉ MỘT lần đọc lại mỗi tiến trình; lượt kế không tốn thêm.
        val tops2 = SlotEscapeRun.TopMemory()
        val clamp = under.replace("bounds=[19,89][1901,985]", "bounds=[19,24][1901,900]")
        val st2 = Store(listOf(EscapeMarker(waze, 0, 301, slot)), ArrayList())
        val sh3 = Rec(listOf(under, clamp), "", ArrayList())
        SlotEscapeRun(sh3, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, st2, liftPx = 65, tops = tops2)
            .reconcile({ waze }, { true }, { slot }, refront = false)
        assertEquals(listOf("am stack list", "am task resize 301 19 24 1901 985", "am stack list"), sh3.calls)
        assertNull(tops2.minTop); assertTrue(tops2.probed)
        val sh4 = Rec(listOf(clamp), "", ArrayList())
        SlotEscapeRun(sh4, TaskBinderCodes.ANDROID_10_R47, "com.byd.avc/", homes, st2, liftPx = 65, tops = tops2)
            .reconcile({ waze }, { true }, { slot }, refront = false)
        assertEquals(listOf("am stack list"), sh4.calls, "dấu đã = khung muốn ⇒ không resize, không đọc lại")
    }

    // ── R10 — đưa lại lên bằng `am task focus` (evidence `emu-slot-escape-app-sweep-2026-10-09.md` §2) ─────────────────────────────

    private val under = "am-stack-list-emulator-2026-10-09-r7-freeform-under-home"
    private val top = "am-stack-list-emulator-2026-10-09-r7-waze-freeform-top"

    @Test
    fun `R10 focus lan dau doc lai xac nhan dinh, lan sau khong doc them, khong am start`() {
        val tops = SlotEscapeRun.TopMemory()
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), ArrayList())
        val sh = Rec(listOf(fixture(under), fixture(top)), "", ArrayList())
        val r = run(sh, st, tops).reconcile({ waze }, { true }, { slot }, refront = true)
        assertEquals(1, r.onTop.size, r.line)
        assertEquals(true, tops.focusWorks)
        assertEquals(3, sh.calls.size, sh.calls.toString())
        assertTrue(sh.calls[1].contains("am task focus 301") && sh.calls[2] == "am stack list", sh.calls.toString())
        val sh2 = Rec(listOf(fixture(under)), "", ArrayList())
        run(sh2, st, tops).reconcile({ waze }, { true }, { slot }, refront = true)
        assertEquals(2, sh2.calls.size, "đã xác nhận ⇒ 1 đọc + 1 focus: ${sh2.calls}")
        (sh.calls + sh2.calls).forEach { assertFalse(it.contains("am start --display"), "không chồng instance: $it") }
    }

    @Test
    fun `R10 focus khong tac dung va intent goc khong MAIN LAUNCHER thi khong dua len`() {
        val tops = SlotEscapeRun.TopMemory()
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), ArrayList())
        // Đọc lại sau focus vẫn thấy task DƯỚI màn nhà ⇒ ROM có lệnh mà không có tác dụng.
        val sh = Rec(listOf(fixture(under)), "", ArrayList(), recents = "  * Recent #0: TaskRecord{5e7e5b0 #301 A=com.waze U=0 StackId=129 sz=2}\n" +
            "    intent={flg=0x10000000 cmp=com.waze/com.waze.FreeMapAppActivity}\n")
        val r = run(sh, st, tops).reconcile({ waze }, { true }, { slot }, refront = true)
        assertTrue(r.onTop.isEmpty(), "không lên ⇒ không che: ${r.line}")
        assertEquals(false, tops.focusWorks)
        assertEquals(SlotEscapePlan.RECENTS_CMD, sh.calls.last())
        sh.calls.forEach { assertFalse(it.contains("am start --display"), "intent gốc không MAIN/LAUNCHER ⇒ cấm am start: $it") }
        // Lượt sau: đã biết focus hỏng ⇒ không thử lại focus, đi thẳng đường lùi.
        val sh2 = Rec(listOf(fixture(under)), "", ArrayList(), recents = fixture("dumpsys-activity-recents-emulator-2026-10-09-r10").replace("#381 ", "#301 "))
        val r2 = run(sh2, st, tops).reconcile({ waze }, { true }, { slot }, refront = true)
        assertFalse(sh2.calls.any { "am task focus" in it }, sh2.calls.toString())
        assertTrue(sh2.calls.last().contains("am start --display 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.waze/com.waze.FreeMapAppActivity"), sh2.calls.last())
        assertEquals(1, r2.onTop.size)
    }

    @Test
    fun `R10 rao man nha camera khong cho chay thi khong danh dau hong, khong che`() {
        val tops = SlotEscapeRun.TopMemory()
        val st = Store(listOf(EscapeMarker(waze, 0, 301, slot)), ArrayList())
        val sh = Rec(listOf(fixture(under)), "", ArrayList(), focus = "")
        val r = run(sh, st, tops).reconcile({ waze }, { true }, { slot }, refront = true)
        assertTrue(r.onTop.isEmpty())
        assertNull(tops.focusWorks, "rào không cho chạy không phải bằng chứng lệnh hỏng")
        assertEquals(2, sh.calls.size, sh.calls.toString())
    }
}
