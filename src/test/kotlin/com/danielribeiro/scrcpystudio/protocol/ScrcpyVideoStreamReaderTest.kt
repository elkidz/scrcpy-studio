package com.danielribeiro.scrcpystudio.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

class ScrcpyVideoStreamReaderTest {

    @Test
    fun readsMetadataSessionAndMediaPackets() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            val name = "OUKITEL RT3 Plus".toByteArray(StandardCharsets.UTF_8)
            output.write(name)
            output.write(ByteArray(64 - name.size))
            output.write("h264".toByteArray(StandardCharsets.US_ASCII))

            output.writeInt(Int.MIN_VALUE or 1)
            output.writeInt(800)
            output.writeInt(1280)

            val pts = 123_456L
            output.writeLong(
                pts or (1L shl 61),
            )
            output.writeInt(3)
            output.write(byteArrayOf(1, 2, 3))
        }

        val reader = ScrcpyVideoStreamReader(
            DataInputStream(ByteArrayInputStream(bytes.toByteArray())),
        )

        assertEquals("OUKITEL RT3 Plus", reader.readDeviceName())
        assertEquals("h264", reader.readCodec())
        assertEquals(
            ScrcpyVideoPacket.Session(800, 1280, clientResized = true),
            reader.readPacket(),
        )

        val media = reader.readPacket() as ScrcpyVideoPacket.Media
        assertEquals(123_456L, media.ptsUs)
        assertFalse(media.isConfig)
        assertTrue(media.isKeyFrame)
        assertArrayEquals(byteArrayOf(1, 2, 3), media.payload)
    }
}
