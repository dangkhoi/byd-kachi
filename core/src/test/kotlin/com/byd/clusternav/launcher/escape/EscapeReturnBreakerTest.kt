package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.escape.EscapeReport.Scope
import com.byd.clusternav.launcher.escape.EscapeReturnGuard.After
import com.byd.clusternav.launcher.escape.EscapeReturnGuard.Why
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R18 — cầu chì daemon. Khoá bài học 08-01 (`docs/archive/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md`): NPE
 * NGUYÊN VĂN của `am display move-stack` trên DiLink3 phải ngắt BỀN, task mất sau lệnh cũng vậy; đua (`IllegalArgumentException`
 * của `RootActivityContainer.moveStackToDisplay`) chỉ ngắt gói; bão thoát (> 10/60 s) ngắt gói; không bao giờ tự đóng lại.
 */
class EscapeReturnBreakerTest {

    /** Thông điệp NGUYÊN VĂN 08-01 (DiLink3, CarPlay → màn cụm). */
    private val npe0801 = "Attempt to invoke virtual method 'int com.android.server.wm.DisplayContent.getRotation()' on a null object reference"

    @Test
    fun `NPE 08-01 ngat ben tat ca`() {
        val b = EscapeReturnBreaker()
        val r = b.onMoveThrew("com.waze", "java.lang.NullPointerException", npe0801)
        assertEquals(Scope.PERSIST, r.scope)
        assertTrue(r.persist)
        assertEquals(Why.TRIPPED_ALL, b.blocks("vn.vietmap.live", 0L), "mọi gói, không riêng Waze")
        assertEquals(Scope.PERSIST, EscapeReturnBreaker.classify("java.lang.IllegalStateException", "at TaskSnapshotController.createTaskSnapshot(…)"))
    }

    @Test
    fun `task mat sau lenh ngat ben, chua toi o chi ngat goi`() {
        val b = EscapeReturnBreaker()
        assertNull(b.onAfter("com.waze", After.IN_SLOT))
        assertEquals(Scope.PKG, b.onAfter("com.waze", After.ELSEWHERE)?.scope)
        assertEquals(Why.TRIPPED_PKG, b.blocks("com.waze", 0L))
        assertNull(b.blocks("vn.vietmap.live", 0L))
        assertEquals(Scope.PERSIST, b.onAfter("vn.vietmap.live", After.LOST)?.scope)
        assertEquals(Why.TRIPPED_ALL, b.blocks("vn.vietmap.live", 0L))
    }

    @Test
    fun `dua IllegalArgumentException chi ngat goi, loi co che ngat tat ca`() {
        val b = EscapeReturnBreaker()
        assertEquals(Scope.PKG, b.onMoveThrew("com.waze", "java.lang.IllegalArgumentException", "Trying to move stack=… to its current displayId=13").scope)
        assertNull(b.blocks("com.google.android.gm", 0L))
        assertEquals(Scope.ALL, b.onMoveThrew("com.google.android.gm", "java.lang.SecurityException", "Requires INTERNAL_SYSTEM_WINDOW").scope)
        assertEquals(Why.TRIPPED_ALL, b.blocks("com.google.android.gm", 0L))
    }

    @Test
    fun `bao thoat qua 10 lan trong 60 s ngat goi, nhip that cua Waze thi khong`() {
        val b = EscapeReturnBreaker()
        // [ĐO máy ảo 09/10] Waze 2–3 lần/phút khi tìm đường: 3 lần/phút trong 5 phút ⇒ không bao giờ ngắt.
        for (i in 0 until 15) { assertNull(b.blocks("com.waze", i * 20_000L)); assertNull(b.onMoved("com.waze", i * 20_000L)) }
        val s = EscapeReturnBreaker()
        for (i in 0 until 9) assertNull(s.onMoved("x.loop", i * 1_000L))
        val trip = s.onMoved("x.loop", 9_000L)
        assertEquals(Scope.PKG, trip?.scope)
        assertEquals(Why.TRIPPED_PKG, s.blocks("x.loop", 9_500L))
        assertEquals(Why.TRIPPED_PKG, s.blocks("x.loop", 600_000L), "không tự đóng lại trong đời daemon")
        assertNull(s.blocks("com.waze", 9_500L))
    }

    /**
     * KHOÁ soát Pass 12 [P2]: báo cáo bền được GIỮ để daemon phát lại cho client sau (Kachi mới nối cùng daemon chưa có dấu bền);
     * giữ đúng lần đầu (chữ ký thật), ngắt gói/ngắt tất cả không bền thì không có gì để phát lại.
     */
    @Test
    fun `bao cao ben duoc giu de phat lai, dung lan dau`() {
        val b = EscapeReturnBreaker()
        assertNull(b.persisted)
        b.onMoveThrew("com.waze", "java.lang.IllegalArgumentException", "đua")
        b.onMechanismError("register:SecurityException")
        assertNull(b.persisted, "ngắt gói / ngắt tất cả không bền ⇒ không phát lại")
        val first = b.onMoveThrew("com.waze", "java.lang.NullPointerException", npe0801)
        assertEquals(first, b.persisted)
        b.onAfter("vn.vietmap.live", After.LOST)
        assertEquals(first, b.persisted, "lần sau không ghi đè chữ ký lần đầu")
    }

    @Test
    fun `dau ben theo ban cai`() {
        val v = EscapeReturnBreaker.persistValue(201, "NullPointerException:…")
        assertTrue(EscapeReturnBreaker.persistActive(v, 201))
        assertFalse(EscapeReturnBreaker.persistActive(v, 202), "bản cài khác ⇒ thử lại một lần")
        assertFalse(EscapeReturnBreaker.persistActive(null, 201))
        assertFalse(EscapeReturnBreaker.persistActive("rác", 201))
    }
}
