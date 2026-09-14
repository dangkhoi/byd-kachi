package com.byd.clusternav.launcher.testbridge

import com.byd.clusternav.launcher.SettingsCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ T-BRIDGE · BÀI CANH BỘ PHÂN TÍCH LỆNH ═══════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` §Verification. Đây là lý do bộ phân tích nằm ở `:core`: 13 lệnh ×
 * các ca thiếu/sai đối số là thứ không ai kiểm hết bằng cách cắm máy rồi gõ `am broadcast`.
 */
class TestBridgeCommandTest {

    private val files = setOf("kachi_workspace", "clusternav_prefs", "simple_cast_prefs")

    private fun parse(vararg extras: Pair<String, Any?>) =
        TestBridgeCommands.parse(mapOf(*extras), files)

    private fun ok(vararg extras: Pair<String, Any?>): TestBridgeCommand {
        val r = parse(*extras)
        assertTrue(r is TestBridgeParse.Ok, "mong lệnh hợp lệ, nhận: $r")
        return (r as TestBridgeParse.Ok).cmd
    }

    private fun err(vararg extras: Pair<String, Any?>): String {
        val r = parse(*extras)
        assertTrue(r is TestBridgeParse.Err, "mong lỗi, nhận: $r")
        return (r as TestBridgeParse.Err).code
    }

    // ── Bảng lệnh tự nhất quán ──────────────────────────────────────────────────────────────────

    @Test
    fun `ma lenh khong trung nhau va khong rong`() {
        assertEquals(TestBridgeCommands.NAMES.size, TestBridgeCommands.NAMES.toSet().size, "mã lệnh trùng nhau")
        assertTrue(TestBridgeCommands.NAMES.all { it.isNotBlank() })
        // Chốt chống bảng bị xoá sạch mà bài vẫn xanh.
        assertTrue(TestBridgeCommands.NAMES.size >= 13, "bảng lệnh chỉ còn ${TestBridgeCommands.NAMES.size} mục")
    }

    @Test
    fun `moi lenh trong bang deu phan tich duoc khi du doi so`() {
        // Đi hết bảng bằng MÁY, không chép tay 13 ca: thêm một lệnh mà quên đối số thì bài này đỏ tại chỗ khai.
        TestBridgeCommands.SPECS.forEach { spec ->
            val extras = mutableMapOf<String, Any?>(TestBridgeCommands.EXTRA_CMD to spec.name)
            spec.required.forEach { key ->
                extras[key] = when (key) {
                    TestBridgeCommands.EXTRA_SLOT -> 2
                    TestBridgeCommands.EXTRA_FILE -> files.first()
                    else -> "x"
                }
            }
            val r = TestBridgeCommands.parse(extras, files)
            assertTrue(r is TestBridgeParse.Ok, "lệnh ${spec.name} đủ đối số mà vẫn lỗi: $r")
        }
    }

    @Test
    fun `moi lenh thieu MOT doi so bat buoc deu bao dung ten doi so do`() {
        TestBridgeCommands.SPECS.filter { it.required.isNotEmpty() }.forEach { spec ->
            spec.required.forEach { omitted ->
                val extras = mutableMapOf<String, Any?>(TestBridgeCommands.EXTRA_CMD to spec.name)
                spec.required.filter { it != omitted }.forEach { key ->
                    extras[key] = when (key) {
                        TestBridgeCommands.EXTRA_SLOT -> 2
                        TestBridgeCommands.EXTRA_FILE -> files.first()
                        else -> "x"
                    }
                }
                val r = TestBridgeCommands.parse(extras, files)
                assertTrue(r is TestBridgeParse.Err, "lệnh ${spec.name} thiếu $omitted mà vẫn qua")
                assertEquals(
                    TestBridgeCommands.ERR_MISSING + omitted, (r as TestBridgeParse.Err).code,
                    "mã lỗi phải NÊU TÊN đối số thiếu — script không được phải đoán",
                )
            }
        }
    }

    // ── Ca hỏng ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `khong co cmd hoac cmd la thi tu choi, khong doan bua`() {
        assertEquals(TestBridgeCommands.ERR_NO_CMD, err())
        assertEquals(TestBridgeCommands.ERR_NO_CMD, err(TestBridgeCommands.EXTRA_CMD to "   "))
        assertEquals(TestBridgeCommands.ERR_UNKNOWN_CMD, err(TestBridgeCommands.EXTRA_CMD to "rm_rf"))
    }

    /** `--es n 2` (chuỗi thay vì số) là lỗi gõ HAY GẶP — nó phải ra "thiếu n", không phải một ô số 0. */
    @Test
    fun `so o sai kieu bi coi la THIEU, khong bi ep ve 0`() {
        assertEquals(
            TestBridgeCommands.ERR_MISSING + TestBridgeCommands.EXTRA_SLOT,
            err(
                TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.SLOT,
                TestBridgeCommands.EXTRA_SLOT to "2",
                TestBridgeCommands.EXTRA_PKG to "com.foo",
            ),
        )
    }

    @Test
    fun `so o phai tu 1 tro len — 0 va am bi tu choi`() {
        listOf(0, -1, -9).forEach { n ->
            assertEquals(
                TestBridgeCommands.ERR_BAD_SLOT,
                err(
                    TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.SLOT_CLEAR,
                    TestBridgeCommands.EXTRA_SLOT to n,
                ),
                "ô $n phải bị từ chối",
            )
        }
    }

    /**
     * Tên tệp prefs phải nằm trong danh sách CHO PHÉP.
     *
     * Đây là cổng duy nhất chặn *"đọc bất kỳ tệp prefs nào của app"* — mà trong đó có thể có tệp của tính năng
     * chưa ai xếp loại. Danh sách truyền từ ngoài vào nên nó luôn là danh sách thật của danh mục.
     */
    @Test
    fun `ten tep prefs la bi tu choi`() {
        assertEquals(
            TestBridgeCommands.ERR_BAD_PREFS_FILE,
            err(
                TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.PREFS,
                TestBridgeCommands.EXTRA_FILE to "../../data/secret",
            ),
        )
        assertEquals(files.first(), ok(
            TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.PREFS,
            TestBridgeCommands.EXTRA_FILE to files.first(),
        ).file)
    }

    /** Danh sách tệp cho phép phải khớp danh mục thật — nếu không, lệnh `prefs` đọc được một tệp chưa ai khai. */
    @Test
    fun `danh muc that co du ba tep ma bai nay dung lam mau`() {
        val real = SettingsCatalog.PREFS_FILES.keys + SettingsCatalog.CLUSTERNAV_PREFS_FILES.keys
        files.forEach { assertTrue(it in real, "tệp mẫu '$it' không còn trong danh mục — bài này đang đo hư không") }
    }

    // ── Ca thường ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `say nhan cau chu va co auto confirm TAT theo mac dinh`() {
        val cmd = ok(
            TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.SAY,
            TestBridgeCommands.EXTRA_TEXT to "bật đèn đọc",
        )
        assertEquals("bật đèn đọc", cmd.text)
        assertTrue(!cmd.autoConfirm, "KHÔNG có cờ ⇒ phải là TẮT: cổng xác nhận không được tự mở")
    }

    @Test
    fun `auto confirm chi bat khi noi ro`() {
        val cmd = ok(
            TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.SAY,
            TestBridgeCommands.EXTRA_TEXT to "mở khoá cửa",
            TestBridgeCommands.EXTRA_AUTO_CONFIRM to true,
        )
        assertTrue(cmd.autoConfirm)
    }

    @Test
    fun `wav chay duoc khong can path`() {
        val cmd = ok(TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.WAV)
        assertEquals("", cmd.path)
        assertEquals(TestBridgeCommands.WAV, cmd.name)
    }

    @Test
    fun `so o giu nguyen 1-based, khong tu tru o tang nay`() {
        val cmd = ok(
            TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.SLOT,
            TestBridgeCommands.EXTRA_SLOT to 2,
            TestBridgeCommands.EXTRA_PKG to "com.google.android.youtube",
        )
        // Phép đổi 1-based → 0-based nằm ở ĐÚNG MỘT chỗ (tầng thi hành). Trừ ở cả hai nơi là lệch một ô.
        assertEquals(2, cmd.slot)
        assertEquals("com.google.android.youtube", cmd.pkg)
    }

    // ── ctl (bắn một control) ───────────────────────────────────────────────────────────────────

    @Test
    fun `ctl can id, thieu id bao dung ten doi so`() {
        assertEquals(
            TestBridgeCommands.ERR_MISSING + TestBridgeCommands.EXTRA_ID,
            err(TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.CTL),
        )
    }

    @Test
    fun `ctl KHONG kiem id ton tai o tang phan tich, de tang thi hanh liet ke ma hop le`() {
        // Cùng luật `pkg`/`profile`: phép kiểm ngữ nghĩa dồn về tầng thi hành (runCtl) để lời đáp liệt kê được.
        val cmd = ok(TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.CTL, TestBridgeCommands.EXTRA_ID to "readl")
        assertEquals("readl", cmd.id)
        assertNull(cmd.v, "không truyền v ⇒ null, để tầng thi hành chọn mặc định theo kind")
        assertTrue(!cmd.autoConfirm)
    }

    @Test
    fun `ctl v la null khi vang, giu nguyen so khi truyen ke ca 0`() {
        assertEquals(
            0,
            ok(
                TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.CTL,
                TestBridgeCommands.EXTRA_ID to "win_lf",
                TestBridgeCommands.EXTRA_V to 0,
            ).v,
            "v=0 (đóng) phải giữ 0, KHÔNG bị coi là 'không truyền'",
        )
        assertEquals(
            3,
            ok(
                TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.CTL,
                TestBridgeCommands.EXTRA_ID to "drive_mode",
                TestBridgeCommands.EXTRA_V to 3,
            ).v,
        )
    }

    @Test
    fun `ctl auto_confirm doc duoc`() {
        assertTrue(
            ok(
                TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.CTL,
                TestBridgeCommands.EXTRA_ID to "door",
                TestBridgeCommands.EXTRA_AUTO_CONFIRM to true,
            ).autoConfirm,
        )
    }

    @Test
    fun `khoang trang thua o ten hoi so va ten goi bi cat`() {
        val cmd = ok(
            TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.PROFILE,
            TestBridgeCommands.EXTRA_ARG to "  Mặc định  ",
        )
        assertEquals("Mặc định", cmd.arg)
        assertNotNull(cmd.name)
    }
}
