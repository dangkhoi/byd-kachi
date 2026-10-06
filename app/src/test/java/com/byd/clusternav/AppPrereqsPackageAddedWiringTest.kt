package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · VM-PREREQ-PKG-ADDED — DÂY NỐI của bộ thu `ACTION_PACKAGE_ADDED` ═════════════════════════════════════════════════
 *
 * Luật phạm vi thuần ở `AppPrereqAddedTest` (`:core`). Bài này khoá phần `:app`: (1) đăng ký MỘT lần ở lượt SẴN (kênh shell đã dùng
 * được), phát tin hệ thống ⇒ `RECEIVER_NOT_EXPORTED`; (2) `onReceive` trên luồng chính KHÔNG shell — đẩy sang `kachi-app-prereqs`;
 * (3) phạm vi qua `AppPrereqPlan.forAddedPackage` rồi đi CÙNG `runScope` (một hàm quyết, dấu bền trước lệnh ghi, cổng thi hành
 * của phiên nền) — không đường ghi thứ hai.
 *
 * Lỗi khoá (B2-OQ5, spec 2.89): VietMap cài lại lúc Kachi đang sống ⇒ ROM xoá miễn pin [ĐO nguồn r47
 * `DeviceIdleController.java:585-592`] ⇒ lần mở đầu tiên hiện hộp "IVI không hỗ trợ" tới lượt chữa kế.
 */
class AppPrereqsPackageAddedWiringTest {

    private val prereqs = SourceRoots.codeOf("src/main/java/com/byd/clusternav/AppPrereqs.kt")

    private fun order(src: String, vararg tokens: String) {
        var at = -1
        tokens.forEach { t ->
            val i = src.indexOf(t, at + 1)
            assertTrue(i > at, "thiếu hoặc sai thứ tự: `$t`\n$src")
            at = i
        }
    }

    @Test
    fun `dang ky mot lan o luot SAN, phat tin he thong khong xuat`() {
        order(SourceRoots.body(prereqs, "fun onReady(ctx: Context) {"), "listenPackageAdded(app)", "exec.execute")
        val fn = SourceRoots.body(prereqs, "private fun listenPackageAdded(app: Context) {")
        order(
            fn,
            "if (!addedListening.compareAndSet(false, true)) return",
            "IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme(\"package\") }",
            "ContextCompat.registerReceiver(app, PackageAddedReceiver(), f, ContextCompat.RECEIVER_NOT_EXPORTED)",
        )
        assertTrue(fn.contains("addedListening.set(false)"), "đăng ký hỏng ⇒ lượt SẴN sau thử lại, không câm mãi")
        assertEquals(1, Regex("listenPackageAdded\\(app\\)").findAll(prereqs).count(), "một lối đăng ký")
    }

    @Test
    fun `onReceive chi lay ten goi roi day sang luong nen - khong shell tren luong chinh`() {
        val rx = SourceRoots.body(prereqs, "override fun onReceive(context: Context, intent: Intent) {")
        order(rx, "intent.action != Intent.ACTION_PACKAGE_ADDED", "intent.data?.schemeSpecificPart", "onPackageAdded(")
        listOf("LocalDeviceShell", "sessionResult", "facts(", "runScope(", "ensureAll").forEach {
            assertFalse(rx.contains(it), "'$it' trong onReceive — luồng chính không được chạm shell/PackageManager")
        }
        assertTrue(SourceRoots.body(prereqs, "fun onPackageAdded(ctx: Context, pkg: String, replacing: Boolean) {")
            .contains("exec.execute { guarded(\"package-added(\$pkg)\") { runAdded(app, pkg, replacing) } }"))
    }

    @Test
    fun `pham vi qua luat thuan roi di CUNG runScope - khong duong ghi thu hai`() {
        val fn = SourceRoots.body(prereqs, "private fun runAdded(app: Context, pkg: String, replacing: Boolean) {")
        order(fn, "AppPrereqPlan.forAddedPackage(facts(app, castPrefs(app)), pkg)", "if (scope.isEmpty())", "runScope(app, trigger,")
        assertEquals(2, Regex(Regex.escape("AppPrereqPlan.ensureAll(")).findAll(prereqs).count(), "vẫn MỘT hàm quyết cho phạm vi + mở chiếu")
        assertFalse(Regex("""==\s*"[a-z]+\.[a-z.]+"""").containsMatchIn(prereqs), "không so tên gói cứng (CLAUDE.md §7)")
    }
}
