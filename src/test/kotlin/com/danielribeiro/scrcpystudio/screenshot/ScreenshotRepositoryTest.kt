package com.danielribeiro.scrcpystudio.screenshot

import com.danielribeiro.scrcpystudio.process.ProcessRunner
import com.danielribeiro.scrcpystudio.settings.ExecutableResolver
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

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

    @Test
    fun savesScaledPreviewAsPng() {
        val directory = Files.createTempDirectory("scrcpy-studio-screenshot-test")
        val preview = directory.resolve("preview.png")
        val output = directory.resolve("saved.png")
        val image = BufferedImage(800, 1_280, BufferedImage.TYPE_INT_ARGB)
        image.graphics.apply {
            color = Color.BLUE
            fillRect(0, 0, image.width, image.height)
            dispose()
        }
        ImageIO.write(image, "png", preview.toFile())

        val repository = ScreenshotRepository(
            settings = ScrcpySettingsState(),
            executableResolver = ExecutableResolver(),
            processRunner = ProcessRunner(),
        )
        val saved = repository.savePreview(
            previewFile = preview,
            outputFile = output,
            resolutionPercent = 50,
        )

        val savedImage = ImageIO.read(saved.toFile())
        assertEquals(400, savedImage.width)
        assertEquals(640, savedImage.height)
        assertTrue(Files.isRegularFile(saved))
        Files.deleteIfExists(preview)
        Files.deleteIfExists(saved)
        Files.deleteIfExists(directory)
    }
}
