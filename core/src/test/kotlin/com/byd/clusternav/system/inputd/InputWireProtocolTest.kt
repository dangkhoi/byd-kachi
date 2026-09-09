package com.byd.clusternav.system.inputd

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * [InputWireProtocol] — khung cố định 18 byte: encode↔decode round-trip đúng (kể cả toạ độ âm / lớn), độ dài
 * đúng, layout byte đúng, và [InputWireProtocol.decode] trả null cho mọi khung rác (sai độ dài / version / type).
 */
class InputWireProtocolTest {

    @Test
    fun `frame is exactly 18 bytes`() {
        assertEquals(18, InputWireProtocol.FRAME_BYTES)
        assertEquals(18, InputWireProtocol.encode(TouchFrame(1, 0, 10, 20)).size)
    }

    @Test
    fun `encode then decode round-trips every field`() {
        val cases = listOf(
            TouchFrame(displayId = 0, action = 0, x = 0, y = 0),
            TouchFrame(displayId = 7, action = 1, x = 1919, y = 719),
            TouchFrame(displayId = 2, action = 2, x = -5, y = -9999),
            TouchFrame(displayId = Int.MAX_VALUE, action = Int.MIN_VALUE, x = 123456, y = -123456),
        )
        for (c in cases) {
            assertEquals(c, InputWireProtocol.decode(InputWireProtocol.encode(c)), "round-trip failed for $c")
        }
    }

    @Test
    fun `byte layout is version, type, then four big-endian ints`() {
        val bytes = InputWireProtocol.encode(TouchFrame(displayId = 0x01020304, action = 1, x = 2, y = 3))
        assertArrayEquals(
            byteArrayOf(
                1, 1,                    // version, type
                0x01, 0x02, 0x03, 0x04,  // displayId big-endian
                0, 0, 0, 1,              // action
                0, 0, 0, 2,              // x
                0, 0, 0, 3,              // y
            ),
            bytes,
        )
    }

    @Test
    fun `decode rejects a frame of the wrong length`() {
        assertNull(InputWireProtocol.decode(ByteArray(0)))
        assertNull(InputWireProtocol.decode(ByteArray(17)))
        assertNull(InputWireProtocol.decode(ByteArray(19)))
    }

    @Test
    fun `decode rejects a wrong version or type byte`() {
        val ok = InputWireProtocol.encode(TouchFrame(1, 0, 10, 20))
        val badVersion = ok.copyOf().also { it[0] = 9 }
        val badType = ok.copyOf().also { it[1] = 9 }
        assertNull(InputWireProtocol.decode(badVersion), "wrong version must be rejected")
        assertNull(InputWireProtocol.decode(badType), "wrong type must be rejected")
    }
}
