package com.byd.clusternav

import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.modules.hal.BydHal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression lock cho 2 root-cause hạ tầng đọc HAL (docs/diagnostics/hal-binding-remediation-2026-09-15.md §A/§B):
 *
 *  • **§A** — API đọc feature THẬT là `get(int[], Class)` 2-arg trả `BYDAutoEventValue`
 *    (`../jadx-tmap/sources/android/hardware/bydauto/AbsBYDAutoDevice.java:84`; OpenBYD gọi
 *    `dev.get(new int[]{id}, Integer.TYPE).intValue` — `CarControlImpl.java:239-240`). Bản cũ dò `get(int[])`
 *    1-arg (không tồn tại) ⇒ `hasSyncGet` false với MỌI device ⇒ mọi telemetry route Feature = "—".
 *  • **§B** — getter trả `int[]` (`getPM2p5Level/Value` — `BYDAutoPM2p5Device.java:84,92`; `getAllRadarProbeStates`
 *    — `BYDAutoRadarDevice.java:64`) bị `.toString()` ra `"[I@hash"` ⇒ `coerceInt` null.
 *
 * Off-car không có HAL thật → fake device Kotlin thuần bắt chước đúng CHỮ KÝ đã đo (public để reflection gọi).
 * Red-green: đổi `hasSyncGet` về dò 1-arg, hoặc `callGetter` về `toString()` → các test này ĐỎ.
 */
class BydHalFeatureGetTest {

    /** Bản sao hình dạng `android.hardware.bydauto.BYDAutoEventValue` (field public, default = sentinel). */
    class FakeEventValue {
        @JvmField var intValue: Int = BydHal.EV_INVALID_INT
        @JvmField var floatValue: Float = BydHal.EV_INVALID_FLOAT
        @JvmField var bufferDataValue: ByteArray? = null
    }

    /** Device có `get(int[], Class<?>)` 2-arg đúng chữ ký `AbsBYDAutoDevice.java:84` + vài getter kiểu mảng. */
    class FakeDevice {
        var lastIds: IntArray = intArrayOf()
        var lastType: Class<*>? = null
        // Tên field KHÔNG được trùng tên getter dưới (`val floats` sinh `getFloats(): Map` → reflection `methods`
        // thứ tự không tất định → test flaky). Ghi nhớ: đã dính 1 lần khi chạy full-suite.
        val intById = mutableMapOf<Int, Int>()
        val floatById = mutableMapOf<Int, Float>()

        fun get(ids: IntArray, type: Class<*>): FakeEventValue? {
            lastIds = ids; lastType = type
            val id = ids.firstOrNull() ?: return null
            if (id == THROWS) throw SecurityException("no permission device 1008")
            if (id == RETURNS_NULL) return null
            return FakeEventValue().also { ev ->
                intById[id]?.let { ev.intValue = it }
                floatById[id]?.let { ev.floatValue = it }
            }
        }

        fun getPM2p5Level(): IntArray = intArrayOf(3)
        fun getAllRadarProbeStates(): IntArray = intArrayOf(0, 1, 2, 3, 4, 0, 0, 1)
        fun getFloats(): FloatArray = floatArrayOf(2.5f)
        fun getEmpty(): IntArray = intArrayOf()
        fun getScalar(): Int = 42

        companion object { const val THROWS = 0x7F00_0001; const val RETURNS_NULL = 0x7F00_0002 }
    }

    /** Device KHÔNG có get 2-arg (chỉ có `get(int[])` 1-arg như bản code cũ tưởng) → phải degrade về null. */
    class NoSyncGetDevice {
        fun get(ids: IntArray): Any = FakeEventValue().also { it.intValue = 99 }
    }

    // ── §A ────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `hasSyncGet detects the real 2-arg get intArray Class signature`() {
        assertTrue(BydHal.hasSyncGet(FakeDevice()))
    }

    @Test fun `hasSyncGet rejects a device that only has the imaginary 1-arg get intArray`() {
        assertFalse(BydHal.hasSyncGet(NoSyncGetDevice()))
        assertNull(BydHal.tryGet(NoSyncGetDevice(), 1))
        assertNull(BydHal.readFeature(NoSyncGetDevice(), 1))
    }

    @Test fun `tryGet calls get with id array and Integer TYPE exactly like OpenBYD`() {
        val dev = FakeDevice().apply { intById[0x4C10E015] = 3 }
        val ev = BydHal.tryGet(dev, 0x4C10E015)
        assertTrue(ev is FakeEventValue)
        assertEquals(3, (ev as FakeEventValue).intValue)
        assertEquals(listOf(0x4C10E015), dev.lastIds.toList())
        assertEquals(Integer.TYPE, dev.lastType)
    }

    @Test fun `readFeature yields int= string that core coerceInt parses`() {
        val dev = FakeDevice().apply { intById[100] = 27 }
        val raw = BydHal.readFeature(dev, 100)
        assertEquals(27, HalBindingTable.coerceInt(raw), "raw=$raw")
    }

    @Test fun `readFeature exposes floatValue for core coerceDouble`() {
        val dev = FakeDevice().apply { floatById[200] = 12.5f }
        val raw = BydHal.readFeature(dev, 200, java.lang.Float.TYPE)
        assertEquals(java.lang.Float.TYPE, dev.lastType)
        assertEquals(12.5, HalBindingTable.coerceDouble(raw), "raw=$raw")
    }

    @Test fun `readFeature maps EventValue sentinel (feature not provisioned) to null, never a fake number`() {
        // HAL trả object với field mặc định INVAILD_INT/INVALID_FLOAT (BYDAutoEventValue.java:5,7,12-13) khi
        // feature không có trên trim — đưa -999999999 lên UI là con số BỊA ⇒ phải là null.
        assertNull(BydHal.readFeature(FakeDevice(), 300))
    }

    /**
     * [SOÁT 2026-09-15 · P1] `BYDAutoEventValue` khởi tạo CẢ HAI field bằng sentinel, nên một feature kiểu float hợp lệ
     * vẫn mang `intValue = INVAILD_INT`. Nếu `readFeature` in số thô thì `coerceInt` (ưu tiên `int=`) đọc ra
     * **-999999999** và ô hiện một con số BỊA — đúng bệnh "số vô nghĩa" mà §A sinh ra để chữa (bộ lọc sentinel của
     * :core chỉ biết rc `-2147482648/-2147482645`). Khoá: ô sentinel bị RÚT, ô hợp lệ đọc được cả int lẫn double.
     */
    @Test fun `readFeature strips the sentinel half so core never shows a fabricated number`() {
        val floatOnly = BydHal.readFeature(FakeDevice().apply { floatById[201] = 26.5f }, 201, java.lang.Float.TYPE)
        assertFalse(floatOnly!!.contains(BydHal.EV_INVALID_INT.toString()), "raw=$floatOnly")
        assertEquals(26.5, HalBindingTable.coerceDouble(floatOnly))
        assertEquals(27, HalBindingTable.coerceInt(floatOnly), "float-only datum đọc bằng readInt phải làm tròn, không phải sentinel")

        val intOnly = BydHal.readFeature(FakeDevice().apply { intById[202] = 7 }, 202)
        assertFalse(intOnly!!.contains("-1.0E9"), "raw=$intOnly")
        assertEquals(7, HalBindingTable.coerceInt(intOnly))
        assertEquals(7.0, HalBindingTable.coerceDouble(intOnly), "không được trả -1.0E9 cho ô float sentinel")
    }

    @Test fun `readFeature degrades to null when HAL throws or returns null`() {
        assertNull(BydHal.readFeature(FakeDevice(), FakeDevice.THROWS))
        assertNull(BydHal.readFeature(FakeDevice(), FakeDevice.RETURNS_NULL))
    }

    @Test fun `firstReadable walks ids through the 2-arg path`() {
        val dev = FakeDevice().apply { intById[7] = 5 }
        val r = BydHal.firstReadable(dev, listOf("A" to 300, "B" to 7))
        // id 300 trả sentinel nhưng vẫn là object non-null (tryGet không lọc sentinel) → firstReadable chọn nó;
        // lọc sentinel là việc của readFeature (đường telemetry). Ở đây chỉ khoá: đi qua get 2-arg, không ném.
        assertEquals("A", r?.first)
        assertTrue(r?.second?.startsWith("int=") == true, "got ${r?.second}")
    }

    // ── §B ────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `callGetter unwraps a 1-element int array to the element so coerceInt reads it`() {
        val raw = BydHal.callGetter(FakeDevice(), "getPM2p5Level")
        assertEquals("3", raw)
        assertEquals(3, HalBindingTable.coerceInt(raw))
    }

    @Test fun `callGetter keeps all elements of a multi-element array for readIntList (8 radar zones)`() {
        val raw = BydHal.callGetter(FakeDevice(), "getAllRadarProbeStates")
        assertEquals("[0, 1, 2, 3, 4, 0, 0, 1]", raw)
        assertFalse(raw!!.contains("[I@"), "must not be Object.toString() of an int[]")
    }

    @Test fun `callGetter handles float and empty arrays and leaves scalars untouched`() {
        assertEquals("2.5", BydHal.callGetter(FakeDevice(), "getFloats"))
        assertEquals("[]", BydHal.callGetter(FakeDevice(), "getEmpty"))
        assertEquals("42", BydHal.callGetter(FakeDevice(), "getScalar"))
    }
}
