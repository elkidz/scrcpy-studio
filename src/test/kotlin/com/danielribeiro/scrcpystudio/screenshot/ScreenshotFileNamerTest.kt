package com.danielribeiro.scrcpystudio.screenshot

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.data.AndroidDeviceState
import com.danielribeiro.scrcpystudio.data.AndroidDeviceTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ScreenshotFileNamerTest {

    private val device = AndroidDevice(
        serial = "ABC123",
        model = "Pixel/8 Pro",
        state = AndroidDeviceState.DEVICE,
        transport = AndroidDeviceTransport.USB,
        rawState = "device",
    )
    private val clock = Clock.fixed(
        Instant.parse("2026-09-03T12:00:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun createsPngNameFromDeviceAndTimestamp() {
        val directory = Path.of("screenshots")

        val result = ScreenshotFileNamer.nextFile(directory, device, clock)

        assertEquals(
            directory.resolve("Pixel-8-Pro-20260903-120000.png"),
            result,
        )
    }

    @Test
    fun addsSuffixWhenScreenshotAlreadyExists() {
        val directory = Files.createTempDirectory("screenshot-namer")
        try {
            Files.createFile(directory.resolve("Pixel-8-Pro-20260903-120000.png"))

            val result = ScreenshotFileNamer.nextFile(directory, device, clock)

            assertTrue(result.fileName.toString().endsWith("-2.png"))
        } finally {
            Files.deleteIfExists(directory.resolve("Pixel-8-Pro-20260903-120000.png"))
            Files.deleteIfExists(directory.resolve("Pixel-8-Pro-20260903-120000-2.png"))
            Files.deleteIfExists(directory)
        }
    }
}
