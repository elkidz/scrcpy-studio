package com.danielribeiro.scrcpystudio.screenshot

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class ScreenshotRepositoryTest {

    @Test
    fun buildsBinaryAdbScreenshotCommandWithoutShellExpansion() {
        val command = ScreenshotRepository.buildCommand(
            adb = Path.of("tools", "adb.exe"),
            serial = "USB device/1",
        )

        assertEquals(
            listOf(
                Path.of("tools", "adb.exe").toString(),
                "-s",
                "USB device/1",
                "exec-out",
                "screencap",
                "-p",
            ),
            command,
        )
    }
}
