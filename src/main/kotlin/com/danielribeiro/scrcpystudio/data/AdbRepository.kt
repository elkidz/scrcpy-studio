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
}
