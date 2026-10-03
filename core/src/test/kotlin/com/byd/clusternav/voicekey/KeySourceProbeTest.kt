package com.byd.clusternav.voicekey

import com.byd.clusternav.launcher.FakeHalGateway
import com.byd.clusternav.launcher.HalFeatureRead
import com.byd.clusternav.launcher.HalGateway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L7 · KEY-SOURCE-SPLIT tầng 1 — khoá ĐẦU DÒ nguồn phím ở dạng dữ liệu + phép đọc thuần + nhãn kết luận.
 *
 * Bài học được khoá (CLAUDE.md §7/§10):
 *  - đầu dò là DỮ LIỆU (bảng mã phím, tên feature, nghĩa từng giá trị) — không `if` theo xe;
 *  - feature-id phân giải theo TÊN trên xe đang chạy — cùng tên mang hai số trên hai cấu hình (`isCanFD`);
 *  - mỗi lý do hụt ra một mã RIÊNG (không gộp về "—") vì tầng 2 rẽ theo lý do (vd ném quyền ⇒ lùi hal-helper);
 *  - giá trị ngoài {1, 2} KHÔNG bị ép thành vô-lăng/núm — hiện nguyên số.
 */
class KeySourceProbeTest {

    private val spec = KeySourceProbes.AUDIO_VOLUME_CTRL_MODE
    private val audioFqn = "android.hardware.bydauto.audio.BYDAutoAudioDevice"

    /** Đồng hồ giả tăng [step] mỗi lần đọc — đo được readMs/ageMs tất định. */
    private fun clock(start: Long, step: Long = 3): () -> Long {
        var t = start - step
        return { t += step; t }
    }

    /** Gateway ghi lại (fqn, id) mà phép đọc thật sự gọi + trả [result]. */
    private class Recording(
        private val names: Map<String, Int>,
        private val result: HalFeatureRead,
        private val mapPresent: Boolean = true,
    ) : HalGateway by FakeHalGateway() {
        val calls = mutableListOf<Pair<String, Int>>()
        override fun featureIdByName(constName: String): Int? = names[constName]
        override fun featureMapAvailable(): Boolean = mapPresent
        override fun featureRead(deviceFqn: String, id: Int): HalFeatureRead {
            calls += deviceFqn to id
            return result
        }
    }

    // ── Bảng đầu dò (dữ liệu) ────────────────────────────────────────────────────────────────────

    @Test
    fun `bang dau do - am luong 291 292 va ban giu 307 308 dung AUDIO_VOLUME_CTRL_MODE`() {
        listOf(291, 292, 307, 308).forEach { assertSame(spec, KeySourceProbes.forKey(it), "mã $it") }
        assertEquals("BYDAutoAudioDevice", spec.deviceClass)
        assertEquals("AUDIO_VOLUME_CTRL_MODE", spec.featureName)
        assertEquals(mapOf(1 to KeySourceKind.CONSOLE_KNOB, 2 to KeySourceKind.STEERING_WHEEL), spec.values)
        assertTrue(spec.budgetMs in 1 until 500, "trần đọc phải dưới hạn 500 ms của KeyEventDispatcher")
    }

    @Test
    fun `phim ngoai bang khong ton luot HAL nao`() {
        listOf(328, 304, 312, 24, 25, 87, 88).forEach { assertNull(KeySourceProbes.forKey(it), "mã $it") }
    }

    @Test
    fun `them doi xe = them DONG du lieu, khong them nhanh`() {
        val other = KeySourceProbeSpec("BYDAutoMultimediaDevice", "SOME_KEY_SOURCE", mapOf(5 to KeySourceKind.STEERING_WHEEL), setOf(305))
        val table = KeySourceProbes.ALL + other
        assertSame(other, KeySourceProbes.forKey(305, table))
        assertSame(spec, KeySourceProbes.forKey(291, table))
    }

    // ── Phép đọc thuần ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `doc theo TEN tren xe dang chay - hai cau hinh hai so, cung mot dau do`() {
        // [ĐO nguồn fw] nhiều hằng BYDAutoFeatureIds gán theo isCanFD — bind theo số là sai từ gốc.
        listOf(-1728053170, 0x12345678).forEach { id ->
            val gw = Recording(mapOf("AUDIO_VOLUME_CTRL_MODE" to id), HalFeatureRead.Value("int=1 float=- buf=-"))
            val r = KeySourceProbes.read(spec, gw, clock(1_000), keyEventTime = 990)
            assertEquals(listOf(audioFqn to id), gw.calls, "đọc đúng device FQN + id phân giải trên xe")
            assertEquals(1, r.value)
            assertNull(r.failure)
            assertEquals(3, r.readMs)
            assertEquals(13, r.ageMs, "từ eventTime của phím (990) tới lúc đọc xong (1003)")
        }
    }

    @Test
    fun `doc qua FakeHalGateway mac dinh - featureRead boc featureGet`() {
        val id = -1728053170
        val gw = FakeHalGateway(
            features = mapOf(id to "int=2 float=- buf=-"),
            featureNames = mapOf("AUDIO_VOLUME_CTRL_MODE" to id),
            featureMapPresent = true,
        )
        assertEquals(2, KeySourceProbes.read(spec, gw, clock(0), 0).value)
        val empty = FakeHalGateway(featureNames = mapOf("AUDIO_VOLUME_CTRL_MODE" to id), featureMapPresent = true)
        assertEquals(KeySourceFailure.EMPTY, KeySourceProbes.read(spec, empty, clock(0), 0).failure)
    }

    @Test
    fun `moi ly do hut ra mot ma rieng`() {
        val names = mapOf("AUDIO_VOLUME_CTRL_MODE" to 7)
        fun failureOf(gw: HalGateway) = KeySourceProbes.read(spec, gw, clock(0), 0)
        assertEquals(KeySourceFailure.NO_FRAMEWORK, failureOf(Recording(emptyMap(), HalFeatureRead.Empty, mapPresent = false)).failure)
        assertEquals(KeySourceFailure.NO_FEATURE, failureOf(Recording(emptyMap(), HalFeatureRead.Empty)).failure)
        assertEquals(KeySourceFailure.NO_DEVICE, failureOf(Recording(names, HalFeatureRead.NoDevice)).failure)
        assertEquals(KeySourceFailure.EMPTY, failureOf(Recording(names, HalFeatureRead.Empty)).failure)
        val denied = failureOf(Recording(names, HalFeatureRead.Failed("SecurityException")))
        assertEquals(KeySourceFailure.READ_ERROR, denied.failure)
        assertEquals("SecurityException", denied.errorClass)
        // [ĐO fw `AbsBYDAutoDevice.get` :320-336] feature không thuộc device ⇒ intValue = -2147482648.
        assertEquals(KeySourceFailure.NOT_PROVISIONED, failureOf(Recording(names, HalFeatureRead.Value("int=-2147482648 float=- buf=-"))).failure)
        val bad = failureOf(Recording(names, HalFeatureRead.Value("int=- float=- buf=4")))
        assertEquals(KeySourceFailure.BAD_VALUE, bad.failure)
        assertEquals("int=- float=- buf=4", bad.errorClass)
    }

    @Test
    fun `khong tim duoc ten thi KHONG goi HAL`() {
        val gw = Recording(emptyMap(), HalFeatureRead.Value("int=1"))
        KeySourceProbes.read(spec, gw, clock(0), 0)
        assertTrue(gw.calls.isEmpty())
    }

    // ── Kết luận hiển thị ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `nhan ket luan - 1 nut yen ngua, 2 vo lang, so la, hong, chua xong, khong do`() {
        fun v(value: Int?, failure: KeySourceFailure? = null) =
            KeySourceProbes.verdict(KeySourceReading(spec, value, failure))
        assertEquals(KeySourceVerdict.Source(KeySourceKind.CONSOLE_KNOB, 1), v(1))
        assertEquals(KeySourceVerdict.Source(KeySourceKind.STEERING_WHEEL, 2), v(2))
        assertEquals(KeySourceVerdict.UnknownValue(0), v(0), "0 KHÔNG được ép thành vô-lăng/núm")
        assertEquals(KeySourceVerdict.UnknownValue(3), v(3))
        assertEquals(KeySourceVerdict.Failed(KeySourceFailure.TIMEOUT, null), v(null, KeySourceFailure.TIMEOUT))
        assertEquals(KeySourceVerdict.Failed(KeySourceFailure.EMPTY, null), v(null))
        assertEquals(KeySourceVerdict.Pending, KeySourceProbes.verdict(null))
        assertEquals(KeySourceVerdict.NotMeasured, KeySourceProbes.verdict(KeySourceReading.NOT_MEASURED))
    }

    @Test
    fun `ma ly do la ASCII co dinh - grep duoc trong usage log va doc duoc tren anh chup`() {
        val codes = KeySourceFailure.values().map { it.code }
        assertEquals(codes.toSet().size, codes.size, "mã trùng ⇒ không phân biệt được lý do")
        codes.forEach { assertTrue(it.matches(Regex("[a-z_]+")), "mã '$it'") }
    }
}
