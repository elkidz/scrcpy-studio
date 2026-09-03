package com.danielribeiro.scrcpystudio.screenshot

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ScreenshotFileNamer {

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val invalidCharacters = Regex("""[^\p{L}\p{N}._-]+""")

    fun nextFile(
        directory: Path,
        device: AndroidDevice,
        clock: Clock = Clock.systemDefaultZone(),
    ): Path {
        val safeDeviceName = device.displayName
            .replace(invalidCharacters, "-")
            .trim('-')
            .ifBlank { "device" }
        val timestamp = LocalDateTime.now(clock).format(timestampFormatter)
        val baseName = "$safeDeviceName-$timestamp"

        var candidate = directory.resolve("$baseName.png")
        var suffix = 2
        while (Files.exists(candidate)) {
            candidate = directory.resolve("$baseName-$suffix.png")
            suffix++
        }
        return candidate
    }
}
