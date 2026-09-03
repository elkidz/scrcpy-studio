package com.danielribeiro.scrcpystudio.data

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
    ) {
        executeDeviceCommand(
            serial = serial,
            arguments = listOf("shell", "input", "keyevent", keycode.toString()),
        )
    }

    suspend fun rotateDisplay(
        serial: String,
        rotation: Int,
    ) {
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

    private suspend fun executeDeviceCommand(
        serial: String,
        arguments: List<String>,
    ) {
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
    }
}
