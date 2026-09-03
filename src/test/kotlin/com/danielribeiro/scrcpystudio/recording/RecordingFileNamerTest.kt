package com.danielribeiro.scrcpystudio.recording

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.data.AndroidDeviceState
import com.danielribeiro.scrcpystudio.data.AndroidDeviceTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RecordingFileNamerTest {

    @Test
    fun createsSafeTimestampedMp4AndAvoidsExistingFile() {
        val directory = Files.createTempDirectory("scrcpy-studio-recording-test")
        val device = AndroidDevice(
            serial = "serial",
            model = "Pixel/8",
            state = AndroidDeviceState.DEVICE,
            transport = AndroidDeviceTransport.USB,
            rawState = "device",
        )
        val clock = Clock.fixed(
            Instant.parse("2026-09-02T15:00:00Z"),
            ZoneOffset.UTC,
        )

        val first = RecordingFileNamer.nextFile(directory, device, clock)
        Files.createFile(first)
        val second = RecordingFileNamer.nextFile(directory, device, clock)

        assertEquals("Pixel-8-20260902-150000.mp4", first.fileName.toString())
        assertEquals("Pixel-8-20260902-150000-2.mp4", second.fileName.toString())
        assertNotEquals(first, second)
        deleteRecursively(directory)
    }

    private fun deleteRecursively(path: Path) {
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
