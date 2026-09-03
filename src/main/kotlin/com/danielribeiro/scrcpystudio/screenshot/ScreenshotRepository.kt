package com.danielribeiro.scrcpystudio.screenshot

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.process.ProcessRunner
import com.danielribeiro.scrcpystudio.settings.ExecutableResolver
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
