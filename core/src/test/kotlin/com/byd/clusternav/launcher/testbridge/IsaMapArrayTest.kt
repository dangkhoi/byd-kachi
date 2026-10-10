package com.byd.clusternav.launcher.testbridge

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ T-BRIDGE · KHOÁ PHÂN TÍCH + KIỂM MẢNG ISA-BẢN-ĐỒ ════════════════════════════════════════════════════════
 *
 * Nguồn: `docs/diagnostics/re-map-speedlimit-2606-2026-10-10.md` §1.2 (12 ô) + §6.2 (tải thử T1 + mảng trả).
 *
 * Khoá lại đúng lỗi mà §5 của RE doc ghi lại: lượt 08/10 gửi `50` THÔ vào ô 5-bit (đúng là mã `10`). Bài
 * `so_tran_bit` dưới đây đỏ nếu [IsaMapArray.parseIsa] lại nhận `50` ở ô 0 — tức nếu ai đó gỡ lớp kiểm dải.
 */
class IsaMapArrayTest {

    // ── Bảng độ rộng + hằng tham chiếu khớp RE doc §1.2 / §6.2 ──────────────────────────────────

    @Test fun `12 o, do rong bit khop RE doc §1·2`() {
        assertEquals(12, IsaMapArray.FIELD_COUNT)
        assertArrayEquals(intArrayOf(5, 2, 2, 2, 13, 13, 5, 5, 3, 8, 2, 4), IsaMapArray.FIELD_BITS)
        assertEquals(12, IsaMapArray.FIELD_NAMES.size, "mỗi ô một tên cho lời đáp đọc được")
        // max = 2^bits − 1
        assertEquals(31, IsaMapArray.maxValue(0), "ô tốc độ 5-bit")
        assertEquals(8191, IsaMapArray.maxValue(4), "ramp distance 13-bit")
        assertEquals(255, IsaMapArray.maxValue(9), "country code 8-bit")
        assertEquals(15, IsaMapArray.maxValue(11), "map status 4-bit")
    }

    @Test fun `tai thu T1 va mang tra la hang tham chieu dung RE doc §6·2`() {
        assertArrayEquals(intArrayOf(10, 2, 2, 0, 8191, 2, 31, 31, 2, 255, 1, 1), IsaMapArray.SAMPLE_T1)
        assertArrayEquals(intArrayOf(31, 2, 2, 0, 8191, 0, 0, 0, 0, 255, 1, 0), IsaMapArray.RESTORE)
        // Và cả hai mảng tham chiếu phải tự vượt qua kiểm dải của chính nó.
        assertTrue(IsaMapArray.parseIsa(IsaMapArray.SAMPLE_T1.joinToString(",")) is IsaMapArray.Parsed.Ok)
        assertTrue(IsaMapArray.parseIsa(IsaMapArray.RESTORE.joinToString(",")) is IsaMapArray.Parsed.Ok)
    }

    // ── setisa: parse 12 ô hợp lệ ───────────────────────────────────────────────────────────────

    @Test fun `parseIsa nhan dung chuoi tai thu T1`() {
        val p = IsaMapArray.parseIsa("10,2,2,0,8191,2,31,31,2,255,1,1")
        assertTrue(p is IsaMapArray.Parsed.Ok)
        assertArrayEquals(IsaMapArray.SAMPLE_T1, (p as IsaMapArray.Parsed.Ok).values)
    }

    @Test fun `parseIsa bo qua khoang trang va o rong cuoi`() {
        val p = IsaMapArray.parseIsa(" 10 , 2 ,2,0,8191,2,31,31,2,255,1,1 ")
        assertTrue(p is IsaMapArray.Parsed.Ok, "khoảng trắng quanh token phải được cắt")
    }

    // ── setisa: từ chối sai độ dài / không-số / tràn dải ────────────────────────────────────────

    @Test fun `parseIsa tu choi sai so o`() {
        assertEquals(IsaMapArray.ERR_LEN + "11", err(IsaMapArray.parseIsa("10,2,2,0,8191,2,31,31,2,255,1")))
        assertEquals(IsaMapArray.ERR_LEN + "13", err(IsaMapArray.parseIsa("10,2,2,0,8191,2,31,31,2,255,1,1,9")))
        assertEquals(IsaMapArray.ERR_LEN + "0", err(IsaMapArray.parseIsa("")))
    }

    @Test fun `parseIsa tu choi token khong phai so`() {
        val code = err(IsaMapArray.parseIsa("10,x,2,0,8191,2,31,31,2,255,1,1"))
        assertTrue(code.startsWith(IsaMapArray.ERR_NAN), "mã lỗi = $code")
        assertTrue(code.endsWith("x"), "phải nối token sai vào mã: $code")
    }

    @Test fun `parseIsa tu choi o tran bit — 50 tho vao o 5-bit (khoa lỗi 08-10)`() {
        // 50 = 0b110010 > 31 (max ô 0) ⇒ phải bị từ chối, KHÔNG âm thầm cắt bit.
        val code = err(IsaMapArray.parseIsa("50,2,2,0,8191,2,31,31,2,255,1,1"))
        assertTrue(code.startsWith(IsaMapArray.ERR_RANGE + "0"), "mã lỗi = $code")
        assertTrue(code.contains("=50>max31"), "phải nói rõ ô 0 = 50 vượt 31: $code")
    }

    @Test fun `parseIsa tu choi o am`() {
        val code = err(IsaMapArray.parseIsa("-1,2,2,0,8191,2,31,31,2,255,1,1"))
        assertTrue(code.startsWith(IsaMapArray.ERR_RANGE + "0"), "ô âm phải bị từ chối: $code")
    }

    @Test fun `parseIsa tu choi country code 256`() {
        val code = err(IsaMapArray.parseIsa("10,2,2,0,8191,2,31,31,2,256,1,1"))
        assertTrue(code.startsWith(IsaMapArray.ERR_RANGE + "9"), "ô 9 (country) 8-bit, 256 vượt 255: $code")
    }

    // ── setarr: generic, không kiểm dải ─────────────────────────────────────────────────────────

    @Test fun `parseArray generic nhan do dai bat ky`() {
        val p = IsaMapArray.parseArray("1,2,3")
        assertTrue(p is IsaMapArray.Parsed.Ok)
        assertArrayEquals(intArrayOf(1, 2, 3), (p as IsaMapArray.Parsed.Ok).values)
    }

    @Test fun `parseArray cho phep gia tri lon va am (khong kiem dai)`() {
        val p = IsaMapArray.parseArray("999,-5,0")
        assertTrue(p is IsaMapArray.Parsed.Ok, "setarr generic không áp dải ISA")
        assertArrayEquals(intArrayOf(999, -5, 0), (p as IsaMapArray.Parsed.Ok).values)
    }

    @Test fun `parseArray tu choi rong`() {
        assertEquals(IsaMapArray.ERR_EMPTY, err(IsaMapArray.parseArray("")))
        assertEquals(IsaMapArray.ERR_EMPTY, err(IsaMapArray.parseArray("  ")))
    }

    @Test fun `parseArray tu choi token khong phai so`() {
        val code = err(IsaMapArray.parseArray("1,abc,3"))
        assertTrue(code.startsWith(IsaMapArray.ERR_NAN) && code.endsWith("abc"), "mã = $code")
    }

    private fun err(p: IsaMapArray.Parsed): String = (p as IsaMapArray.Parsed.Err).code
}
