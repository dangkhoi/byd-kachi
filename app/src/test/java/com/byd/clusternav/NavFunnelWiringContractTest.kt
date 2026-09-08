package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Đường Google Maps (notification → cụm) — CONTRACT lock, CẬP NHẬT 2026-08-28.
 *
 * ⚠ Đường ẢNH (VietMap/Waze) đã bị GỠ; các khẳng định về "một cửa vào cho đường ảnh" (`ingestContent`,
 * `NavOutputOwner` nối phễu) đã xoá theo. Còn lại là hai bất biến của đường GMaps đã proven ngoài hiện
 * trường (CLAUDE.md §6):
 *
 *  1. **Đường Google Maps KHÔNG bị đụng** — `handle()` vẫn vào đúng `NavRepository.ingest(...)` như cũ, và
 *     KHÔNG được đan một mẩu nào của đường ảnh (đã gỡ) vào.
 *  2. **`ingest` chỉ còn là vỏ** — mọi phép tính đã dời sang `NavContentBuilder.fromNotification`; nếu ai đó
 *     tính lại một trường ngay trong `NavRepository` thì bảng vàng `GmapsContentGoldenTest` (`:core`) không
 *     canh được nữa, vì nó chỉ canh builder.
 */
class NavFunnelWiringContractTest {

    private val repo by lazy { SourceRoots.text("src/main/java/com/byd/clusternav/NavRepository.kt") }
    private val listener by lazy { SourceRoots.text("src/main/java/com/byd/clusternav/NavNotificationListener.kt") }

    /** Thân của một `fun <name>(` cho tới dấu đóng `\n    }` ở cấp 4-space. */
    private fun functionBody(text: String, name: String): String {
        val start = text.indexOf("fun $name(")
        assertTrue(start >= 0, "không tìm thấy fun $name")
        val rest = text.substring(start)
        val end = rest.indexOf("\n    }")
        assertTrue(end > 0, "không tìm thấy dấu đóng của fun $name")
        return rest.substring(0, end)
    }

    // ── 1. Đường Google Maps ────────────────────────────────────────────────────────────────────────

    /** Call site DUY NHẤT của đường notification phải còn NGUYÊN VĂN, đủ 4 đối số. */
    @Test
    fun `duong notification van goi ingest y nguyen`() {
        assertTrue(
            listener.contains("NavRepository.ingest(applicationContext, sbn.packageName, null, state)"),
            "đường Google Maps phải giữ đúng call site cũ",
        )
        assertTrue(
            listener.contains("val MAPS_PACKAGES = com.byd.clusternav.navigation.NavApps.NOTIFICATION"),
            "roster kênh notification không được đổi trong việc này",
        )
    }

    /**
     * `handle()` là đường DATA (Google Maps); nó KHÔNG được biết tới bất kỳ mảnh nào của đường ẢNH đã gỡ.
     * Nếu nó bắt đầu đọc tín hiệu ảnh thì ta quay lại đúng cái mớ mà 08-22/08-23 đã phải gỡ (B3.42).
     */
    @Test
    fun `handle() khong duoc doc mot manh nao cua duong anh`() {
        val body = functionBody(listener, "handle")
        listOf("ScreenCaptureSignal", "NavViewIdSource", "NavOutputDecision", "NavOutputOwner", "NavContentBuilder")
            .forEach { name ->
                assertFalse(body.contains(name), "handle() (đường DATA) không được biết tới $name")
            }
    }

    /** CLAUDE.md §7 — khác biệt giữa app phải lộ ra qua ĐO, không qua tên gói cứng trong listener. */
    @Test
    fun `listener khong duoc re nhanh theo ten goi`() {
        listOf("if (pkg ==", "if (packageName ==", "if (sbn.packageName ==")
            .forEach { pattern -> assertFalse(listener.contains(pattern), "cấm rẽ nhánh theo tên gói: $pattern") }
    }

    // ── 2. `ingest` chỉ còn là vỏ ───────────────────────────────────────────────────────────────────

    @Test
    fun `NavRepository ingest khong con tu tinh truong nao`() {
        val body = functionBody(repo, "ingest")
        assertTrue(
            body.contains("NavContentBuilder.fromNotification("),
            "ingest phải dựng khung qua builder dùng chung",
        )
        assertFalse(body.contains("NavParse."), "ingest không được tự parse — đã dời sang builder")
        assertFalse(body.contains("NavigationFrameContent("), "ingest không được tự dựng content")
        assertTrue(body.contains("publish(value)"), "vẫn phải đẩy CHÍNH NavState gốc cho UI (mang bitmap mũi tên)")
    }

    // ── 3. `ingestContent` (đường ảnh) đã GỠ ────────────────────────────────────────────────────────

    /** Đường ảnh đã gỡ hẳn: `NavRepository` không còn phơi `ingestContent`. `stopIfSource` (KEEP) vẫn còn. */
    @Test
    fun `NavRepository khong con ingestContent, van giu stopIfSource`() {
        assertFalse(repo.contains("fun ingestContent("), "đường vào của khung ảnh đã gỡ")
        assertTrue(repo.contains("fun stopIfSource("), "stopIfSource vẫn giữ (nhả phiên theo chủ)")
        val body = functionBody(repo, "stopIfSource")
        assertTrue(
            body.contains("current.identity?.packageName != packageName"),
            "phải so danh tính nguồn đang giữ phiên trước khi dừng",
        )
    }
}
