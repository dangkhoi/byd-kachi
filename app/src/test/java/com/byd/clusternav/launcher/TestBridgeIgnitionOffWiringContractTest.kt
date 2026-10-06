package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * ═══ 2.93 · TEST-MODE-ACC-OFF (⚠ bảo mật) — DÂY NỐI phía `:app`: cửa sổ test-mode đọc dấu tắt máy THẬT ═════════════
 *
 * Phép tính thuần có bài chạy thật ở `:core` (`TestBridgeIgnitionOffTest`). Bài này hỏi câu `:core` không hỏi được: cổng
 * DUY NHẤT của receiver exported (`TestBridgeStore.isOn`) và số phút trên màn Cài đặt (`remainingMinutes`) có THẬT SỰ
 * nhận mốc tắt máy không, mốc đó có phải claim bền của lớp 1 (2.83) không, và khe "claim chưa kịp ghi ở luồng nền" có
 * được lấp ĐỒNG BỘ không (lệnh tới ngay sau lượt BYD giết + Android dựng lại launcher lúc màn tắt).
 * Thử ĐỎ: bỏ `tatMayAt(ctx)` khỏi `isOn` / bỏ dòng gán `nonInteractiveStartAt` trong `install`.
 */
class TestBridgeIgnitionOffWiringContractTest {

    private val store by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeStore.kt") }
    private val bridge by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/testbridge/KachiTestBridge.kt") }
    private val heal by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/A11yLifecycleHeal.kt") }

    @Test
    fun `cong duy nhat va so phut tren Cai dat deu nhan moc tat may`() {
        val isOn = SourceRoots.body(store, "fun isOn(ctx: Context): Boolean")
        assertTrue(isOn.contains("SystemClock.elapsedRealtime(), tatMayAt(ctx))"), isOn)
        val minutes = SourceRoots.body(store, "fun remainingMinutes(ctx: Context): Int")
        assertTrue(minutes.contains("SystemClock.elapsedRealtime(), tatMayAt(ctx))"), minutes)
        // Receiver exported hỏi cổng TRƯỚC mọi nhánh lệnh (không đổi — chỉ khoá lại vì cổng nay có thêm đầu vào).
        assertTrue(bridge.contains("!TestBridgeStore.isOn(app) -> reply.fail(ERR_TEST_MODE_OFF)"))
    }

    @Test
    fun `moc tat may = claim ben cua lop 1, khe chua kip ghi duoc lap dong bo`() {
        val tat = SourceRoots.body(store, "private fun tatMayAt(ctx: Context): Long")
        assertTrue(tat.contains("Prefs.a11yTatMayAt(ctx)") && tat.contains("A11yLifecycleHeal.pendingTatMayAt()"), tat)
        // Claim bền: lớp 1 ghi `commit()` ở lượt tiến trình bật lúc màn tắt (chỗ ghi thật, không phải chỉ đọc).
        assertTrue(SourceRoots.body(heal, "private fun onProcessStart(").contains("Prefs.setA11yTatMayAt(app, startedAt)"))
        // Lấp khe: gán ĐỒNG BỘ trong `install` (luồng chính, trước khi receiver nào chạy), TRƯỚC lượt nộp việc cho luồng nền.
        val install = SourceRoots.body(heal, "fun install(ctx: Context)")
        // Senior review Pass 1: `!= true` — không hỏi được màn (`null`) cũng tính là "có thể vừa tắt máy" ⇒ đóng (fail-closed).
        val set = install.indexOf("if (interactive != true) nonInteractiveStartAt = startedAt")
        val submit = install.indexOf("submit(")
        assertTrue(set in 0 until submit, "gán mốc phải đứng TRƯỚC submit ($set/$submit)")
        assertTrue(heal.contains("internal fun pendingTatMayAt(): Long = nonInteractiveStartAt"))
    }

    /**
     * Senior review Pass 1 · [P2] — claim bền của lớp 1 có cổng "cùng một lần tắt máy" (`tatMayMayRun`: 10 phút, chưa mở-xe ⇒
     * không ghi) và mốc RAM chết theo tiến trình ⇒ lần đóng phải được làm BỀN ngay ở lần bật lúc màn tắt, nếu không tiến trình
     * sau (bật lúc màn sáng) mở lại cửa sổ. Thử ĐỎ: bỏ dòng `closeAfterScreenOffStart` ở `install` / bỏ `commit()` xoá khoá.
     */
    @Test
    fun `bat luc man tat thi dong BEN cua so - mot cho goi, luong nen, xep cuoi`() {
        val install = SourceRoots.body(heal, "fun install(ctx: Context)")
        val close = install.indexOf(
            "if (interactive != true) submit(\"test-mode\") { com.byd.clusternav.launcher.testbridge.TestBridgeStore.closeAfterScreenOffStart(app) }",
        )
        assertTrue(close > install.indexOf("onBootGrace(app, interactive, startedAt)"), "đường mới xếp CUỐI hàng luồng nền (§6): $close")
        val fn = SourceRoots.body(store, "fun closeAfterScreenOffStart(ctx: Context)")
        assertTrue(fn.contains("if (stored(ctx) == null || isOn(ctx)) return"), "chỉ xoá khi cửa sổ ĐÃ đóng theo cổng:\n$fn")
        assertTrue(fn.contains("sp(ctx).edit().remove(KEY_UNTIL).commit()"), "xoá bền, đồng bộ trên luồng nền:\n$fn")
        // Mã đã bỏ chú thích của MỌI gốc mã: đúng 2 lần = một dòng khai + chỗ gọi DUY NHẤT ở `install`.
        val uses = SourceRoots.moduleSourceRoots().sumOf { root ->
            Files.walk(root).use { s ->
                s.filter { it.toString().endsWith(".kt") }.toList()
                    .sumOf { Regex("""\bcloseAfterScreenOffStart\(""").findAll(KotlinSource.stripComments(it.toFile().readText())).count() }
            }
        }
        assertEquals(2, uses, "closeAfterScreenOffStart: một dòng khai + một chỗ gọi (A11yLifecycleHeal.install)")
    }
}
