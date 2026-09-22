package com.danielribeiro.scrcpystudio.screenshot

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.process.ProcessRunner
import com.danielribeiro.scrcpystudio.settings.ExecutableResolver
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import kotlin.math.roundToInt

class ScreenshotRepository(
    private val settings: ScrcpySettingsState,
    private val executableResolver: ExecutableResolver,
    private val processRunner: ProcessRunner,
) {

    suspend fun capture(
        device: AndroidDevice,
        outputFile: Path,
    ): Path {
        val tools = executableResolver.resolve(settings.getState())
        val target = outputFile.toAbsolutePath().normalize()
        if (Files.exists(target)) {
            throw IllegalStateException("The screenshot file already exists.")
        }
        target.parent?.let(Files::createDirectories)

        val result = processRunner.executeBinary(
            command = buildCommand(tools.adb, device.serial),
            workingDirectory = tools.adb.parent,
        )
        if (result.exitCode != 0) {
            throw IllegalStateException(
                buildString {
                    append("adb screenshot failed with exit code ")
                    append(result.exitCode)
                    if (result.errorOutput.isNotBlank()) {
                        append(": ")
                        append(result.errorOutput.trim())
                    }
                },
            )
        }
        if (!result.output.startsWith(PNG_SIGNATURE)) {
            throw IllegalStateException("adb returned invalid PNG screenshot data.")
        }

        val parent = target.parent
            ?: throw IllegalStateException("The screenshot path must have a parent directory.")
        val temporary = Files.createTempFile(parent, ".scrcpy-screenshot-", ".tmp")
        try {
            Files.write(temporary, result.output)
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return target
    }

    fun savePreview(
        previewFile: Path,
        outputFile: Path,
        resolutionPercent: Int,
    ): Path {
        require(resolutionPercent in 1..100) {
            "Screenshot resolution must be between 1% and 100%."
        }
        val source = previewFile.toAbsolutePath().normalize()
        val target = outputFile.toAbsolutePath().normalize()
        if (!Files.isRegularFile(source)) {
            throw IllegalStateException("The screenshot preview is no longer available.")
        }
        if (Files.exists(target)) {
            throw IllegalStateException("The screenshot file already exists.")
        }
        val parent = target.parent
            ?: throw IllegalStateException("The screenshot path must have a parent directory.")
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".scrcpy-screenshot-save-", ".tmp")
        try {
            if (resolutionPercent == 100) {
                Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING)
            } else {
                val image = ImageIO.read(source.toFile())
                    ?: throw IllegalStateException("The screenshot preview is not a valid PNG.")
                val width = (image.width * resolutionPercent / 100f)
                    .roundToInt()
                    .coerceAtLeast(1)
                val height = (image.height * resolutionPercent / 100f)
                    .roundToInt()
                    .coerceAtLeast(1)
                val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val graphics = scaled.createGraphics()
                try {
                    graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    graphics.drawImage(image, 0, 0, width, height, null)
                } finally {
                    graphics.dispose()
                }
                if (!ImageIO.write(scaled, "png", temporary.toFile())) {
                    throw IllegalStateException("PNG encoding is not available.")
                }
            }
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return target
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        internal fun buildCommand(
            adb: Path,
            serial: String,
        ): List<String> = listOf(
            adb.toString(),
            "-s",
            serial,
            "exec-out",
            "screencap",
            "-p",
        )

        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}
