package com.danielribeiro.scrcpystudio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

class ScrcpyCommandBuilderTest {

    private val device = AndroidDevice(
        serial = "192.168.1.20:5555",
        model = "Pixel_8",
        state = AndroidDeviceState.DEVICE,
        transport = AndroidDeviceTransport.WIFI,
        rawState = "device",
    )
    private val builder = ScrcpyCommandBuilder(Paths.get("tools", "scrcpy.exe"))

    @Test
    fun mirrorSelectsDeviceAndUsesStableWindowTitle() {
        val command = builder.mirror(device)

        assertEquals("tools${java.io.File.separator}scrcpy.exe", command[0])
        assertTrue(command.containsAll(listOf("--serial", device.serial, "--window-borderless")))
        assertEquals(
            listOf("--window-title", "Scrcpy Studio - ${device.serial}"),
            command.dropWhile { it != "--window-title" }.take(2),
        )
    }

    @Test
    fun recordingDisablesPlaybackAndWritesMp4() {
        val command = builder.record(device, Paths.get("recordings", "capture.mp4"))

        assertTrue(command.containsAll(listOf("--serial", device.serial)))
        assertTrue(command.contains("--no-window"))
        assertTrue(command.contains("--no-playback"))
        assertTrue(command.contains("--no-control"))
        assertTrue(command.contains("--record-format"))
        assertEquals(
            Paths.get("recordings", "capture.mp4").toString(),
            command[command.indexOf("--record") + 1],
        )
    }
}
