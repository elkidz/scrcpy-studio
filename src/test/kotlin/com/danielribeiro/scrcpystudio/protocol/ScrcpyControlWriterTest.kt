package com.danielribeiro.scrcpystudio.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ScrcpyControlWriterTest {

    @Test
    fun serializesTouchEventUsingScrcpyWireLayout() {
        val bytes = ByteArrayOutputStream()
        ScrcpyControlWriter(bytes).injectTouch(
            action = 0,
            pointerId = ScrcpyControlWriter.POINTER_ID_MOUSE,
            x = 123,
            y = 456,
            screenWidth = 800,
            screenHeight = 1280,
            pressure = 1f,
            actionButton = 1,
            buttons = 1,
        )

        val data = bytes.toByteArray()
        assertEquals(32, data.size)
        assertEquals(ScrcpyControlWriter.TYPE_INJECT_TOUCH_EVENT, data[0].toInt())

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        buffer.position(2)
        assertEquals(-1L, buffer.long)
        assertEquals(123, buffer.int)
        assertEquals(456, buffer.int)
        assertEquals(800, buffer.short.toInt() and 0xFFFF)
        assertEquals(1280, buffer.short.toInt() and 0xFFFF)
        assertEquals(0xFFFF, buffer.short.toInt() and 0xFFFF)
        assertEquals(1, buffer.int)
        assertEquals(1, buffer.int)
        assertTrue(buffer.remaining() == 0)
    }
}
