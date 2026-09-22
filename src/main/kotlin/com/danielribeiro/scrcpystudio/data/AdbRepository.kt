package com.danielribeiro.scrcpystudio.data

import com.danielribeiro.scrcpystudio.process.ProcessResult
import com.danielribeiro.scrcpystudio.process.ProcessRunner
import com.danielribeiro.scrcpystudio.settings.ExecutableResolver
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState

class AdbCommandException(
    val command: List<String>,
    val exitCode: Int,
    val commandOutput: String,
) : IllegalStateException(
    buildString {
        append("adb exited with code $exitCode")
        if (commandOutput.isNotBlank()) {
            append(": ")
            append(commandOutput.trim().lineSequence().take(5).joinToString(" "))
        }
    },
)

class AdbRepository(
    private val settings: ScrcpySettingsState,
    private val executableResolver: ExecutableResolver,
    private val processRunner: ProcessRunner,
) {

    suspend fun listDevices(): List<AndroidDevice> {
        val tools = executableResolver.resolve(settings.getState())
        val command = listOf(tools.adb.toString(), "devices", "-l")
        val result = processRunner.execute(command)
        if (result.exitCode != 0) {
            throw AdbCommandException(command, result.exitCode, result.output)
        }
        return AdbDeviceParser.parse(result.output)
    }

    suspend fun sendKeyevent(
        serial: String,
        keycode: Int,
    ): Unit {
        executeDeviceCommand(
            serial = serial,
            arguments = listOf("shell", "input", "keyevent", keycode.toString()),
        )
    }

    suspend fun rotateDisplay(
        serial: String,
        rotation: Int,
    ): Unit {
        require(rotation in 0..3) { "Display rotation must be between 0 and 3." }
        executeDeviceCommand(
            serial = serial,
            arguments = listOf(
                "shell",
                "settings",
                "put",
                "system",
                "accelerometer_rotation",
                "0",
            ),
        )
        executeDeviceCommand(
            serial = serial,
            arguments = listOf(
                "shell",
                "settings",
                "put",
                "system",
                "user_rotation",
                rotation.toString(),
            ),
        )
    }

    suspend fun displaySize(serial: String): AndroidDisplaySize? {
        val result = executeDeviceCommand(
            serial = serial,
            arguments = listOf("shell", "wm", "size"),
        )
        val sizes = DISPLAY_SIZE_PATTERN.findAll(result.output).mapNotNull { match ->
            val width = match.groupValues[2].toIntOrNull()
            val height = match.groupValues[3].toIntOrNull()
            if (width == null || height == null) {
                null
            } else {
                ParsedDisplaySize(
                    isPhysical = match.groupValues[1] == "Physical",
                    size = AndroidDisplaySize(width = width, height = height),
                )
            }
        }.toList()
        return sizes.firstOrNull { it.isPhysical }?.size ?: sizes.firstOrNull()?.size
    }

    private suspend fun executeDeviceCommand(
        serial: String,
        arguments: List<String>,
    ): ProcessResult {
        val tools = executableResolver.resolve(settings.getState())
        val command = buildList {
            add(tools.adb.toString())
            add("-s")
            add(serial)
            addAll(arguments)
        }
        val result = processRunner.execute(
            command = command,
            workingDirectory = tools.adb.parent,
        )
        if (result.exitCode != 0) {
            throw AdbCommandException(command, result.exitCode, result.output)
        }
        return result
    }

    private data class ParsedDisplaySize(
        val isPhysical: Boolean,
        val size: AndroidDisplaySize,
    )

    private companion object {
        val DISPLAY_SIZE_PATTERN = Regex(
            pattern = """(?m)^(Physical|Override) size:\s*(\d+)x(\d+)\s*$""",
        )
    }
}

data class AndroidDisplaySize(
    val width: Int,
    val height: Int,
) {
    val maxDimension: Int
        get() = maxOf(width, height)

    fun scaledMaxDimension(percent: Int): Int =
        (maxDimension * percent / 100f).toInt().coerceAtLeast(1)
}
