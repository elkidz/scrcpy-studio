package com.danielribeiro.scrcpystudio.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ScrcpyControlWriterTest {

    @Test
    fun serializesHomeKeyPressUsingScrcpyWireLayout() {
        val bytes = ByteArrayOutputStream()

        ScrcpyControlWriter(bytes).pressHome()

        val data = bytes.toByteArray()
        assertEquals(28, data.size)

        val down = ByteBuffer.wrap(data, 0, 14).order(ByteOrder.BIG_ENDIAN)
        assertEquals(ScrcpyControlWriter.TYPE_INJECT_KEYCODE, down.get().toInt())
        assertEquals(ScrcpyControlWriter.KEY_ACTION_DOWN, down.get().toInt())
        assertEquals(ScrcpyControlWriter.KEYCODE_HOME, down.int)
        assertEquals(0, down.int)
        assertEquals(0, down.int)

        val up = ByteBuffer.wrap(data, 14, 14).order(ByteOrder.BIG_ENDIAN)
        assertEquals(ScrcpyControlWriter.TYPE_INJECT_KEYCODE, up.get().toInt())
        assertEquals(ScrcpyControlWriter.KEY_ACTION_UP, up.get().toInt())
        assertEquals(ScrcpyControlWriter.KEYCODE_HOME, up.int)
        assertEquals(0, up.int)
        assertEquals(0, up.int)
    }

    @Test
    fun serializesBackKeyPressUsingScrcpyWireLayout() {
        val bytes = ByteArrayOutputStream()

        ScrcpyControlWriter(bytes).pressBack()

        assertArrayEquals(
            byteArrayOf(
                ScrcpyControlWriter.TYPE_BACK_OR_SCREEN_ON.toByte(),
                ScrcpyControlWriter.KEY_ACTION_DOWN.toByte(),
                ScrcpyControlWriter.TYPE_BACK_OR_SCREEN_ON.toByte(),
                ScrcpyControlWriter.KEY_ACTION_UP.toByte(),
            ),
            bytes.toByteArray(),
        )
    }

    @Test
    fun serializesRotateCommandWithoutPayload() {
        val bytes = ByteArrayOutputStream()

        ScrcpyControlWriter(bytes).rotateDevice()

        assertArrayEquals(
            byteArrayOf(ScrcpyControlWriter.TYPE_ROTATE_DEVICE.toByte()),
            bytes.toByteArray(),
        )
    }

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

    @Test
    fun serializesClipboardPasteUsingScrcpyWireLayout() {
        val bytes = ByteArrayOutputStream()
        ScrcpyControlWriter(bytes).setClipboard("hi", paste = true)

        val data = bytes.toByteArray()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        assertEquals(ScrcpyControlWriter.TYPE_SET_CLIPBOARD, buffer.get().toInt())
        assertEquals(0L, buffer.long)
        assertEquals(1, buffer.get().toInt())
        assertEquals(2, buffer.int)
        assertEquals("hi", String(data, buffer.position(), 2, Charsets.UTF_8))
    }

    @Test
    fun serializesInjectTextUsingScrcpyWireLayout() {
        val bytes = ByteArrayOutputStream()
        ScrcpyControlWriter(bytes).injectText("ok")

        val data = bytes.toByteArray()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        assertEquals(ScrcpyControlWriter.TYPE_INJECT_TEXT, buffer.get().toInt())
        assertEquals(2, buffer.int)
        assertEquals("ok", String(data, buffer.position(), 2, Charsets.UTF_8))
    }
}
