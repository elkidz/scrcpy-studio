package com.danielribeiro.scrcpystudio.data

import com.danielribeiro.scrcpystudio.process.ManagedProcess
import com.danielribeiro.scrcpystudio.process.ProcessRunner
import com.danielribeiro.scrcpystudio.settings.ExecutableResolver
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import java.nio.file.Path

class ScrcpyRepository(
    private val settings: ScrcpySettingsState,
    private val executableResolver: ExecutableResolver,
    private val processRunner: ProcessRunner,
) {

    fun startMirror(
        device: AndroidDevice,
        parentDisposable: com.intellij.openapi.Disposable,
        onOutput: (String) -> Unit = {},
        onTerminated: (exitCode: Int, output: String) -> Unit = { _, _ -> },
    ): ManagedProcess {
        val tools = executableResolver.resolve(settings.getState())
        val command = ScrcpyCommandBuilder(tools.scrcpy).mirror(device)
        return start(
            command = command,
            toolsScrcpy = tools.scrcpy,
            adb = tools.adb,
            parentDisposable = parentDisposable,
            onOutput = onOutput,
            onTerminated = onTerminated,
        )
    }

    fun startRecording(
        device: AndroidDevice,
        outputFile: Path,
        parentDisposable: com.intellij.openapi.Disposable,
        onOutput: (String) -> Unit = {},
        onTerminated: (exitCode: Int, output: String) -> Unit = { _, _ -> },
    ): ManagedProcess {
        val tools = executableResolver.resolve(settings.getState())
        val command = ScrcpyCommandBuilder(tools.scrcpy).record(device, outputFile)
        return start(
            command = command,
            toolsScrcpy = tools.scrcpy,
            adb = tools.adb,
            parentDisposable = parentDisposable,
            onOutput = onOutput,
            onTerminated = onTerminated,
        )
    }

    private fun start(
        command: List<String>,
        toolsScrcpy: Path,
        adb: Path,
        parentDisposable: com.intellij.openapi.Disposable,
        onOutput: (String) -> Unit,
        onTerminated: (exitCode: Int, output: String) -> Unit,
    ): ManagedProcess =
        processRunner.start(
            command = command,
            environment = mapOf("ADB" to adb.toString()),
            workingDirectory = toolsScrcpy.parent,
            parentDisposable = parentDisposable,
            onOutput = onOutput,
            onTerminated = onTerminated,
        )
}
