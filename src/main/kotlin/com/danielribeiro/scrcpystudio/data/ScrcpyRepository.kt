package com.danielribeiro.scrcpystudio.data

import com.danielribeiro.scrcpystudio.process.ManagedProcess
import com.danielribeiro.scrcpystudio.process.ProcessRunner
import com.danielribeiro.scrcpystudio.settings.ExecutableResolver
import com.danielribeiro.scrcpystudio.settings.ScrcpyMirrorOptions
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import com.danielribeiro.scrcpystudio.recording.RecordingOptions
import java.nio.file.Paths
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
        val state = settings.getState()
        val tools = executableResolver.resolve(state)
        val command = ScrcpyCommandBuilder(
            scrcpyExecutable = tools.scrcpy,
            options = ScrcpyMirrorOptions.from(state),
        ).mirror(device)
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
        options: RecordingOptions = RecordingOptions(),
        maxSize: Int? = null,
        parentDisposable: com.intellij.openapi.Disposable,
        onOutput: (String) -> Unit = {},
        onTerminated: (exitCode: Int, output: String) -> Unit = { _, _ -> },
    ): ManagedProcess {
        val tools = executableResolver.resolve(settings.getState())
        val command = ScrcpyCommandBuilder(tools.scrcpy)
            .record(
                device = device,
                outputFile = outputFile,
                options = options,
                maxSize = maxSize,
            )
        return start(
            command = command,
            toolsScrcpy = tools.scrcpy,
            adb = tools.adb,
            parentDisposable = parentDisposable,
            gracefulOnDispose = true,
            onOutput = onOutput,
            onTerminated = onTerminated,
        )
    }

    fun stopStaleExternalMirror(device: AndroidDevice) {
        runCatching {
            val scrcpyExecutable = executableResolver
                .resolve(settings.getState())
                .scrcpy
                .fileName
                .toString()
            val expectedTitle = ScrcpyCommandBuilder.windowTitleFor(device.serial)
            ProcessHandle.allProcesses().forEach { process ->
                val info = process.info()
                val command = info.command().orElse("")
                val arguments = info.arguments().orElse(emptyArray()).toList()
                val commandLine = info.commandLine().orElse("")
                if (command.isNotBlank() &&
                    Paths.get(command).fileName.toString()
                        .equals(scrcpyExecutable, ignoreCase = true) &&
                    (arguments.windowTitle() == expectedTitle ||
                        commandLine.contains(expectedTitle))
                ) {
                    process.destroy()
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            }
        }
    }

    private fun start(
        command: List<String>,
        toolsScrcpy: Path,
        adb: Path,
        parentDisposable: com.intellij.openapi.Disposable,
        gracefulOnDispose: Boolean = false,
        onOutput: (String) -> Unit,
        onTerminated: (exitCode: Int, output: String) -> Unit,
    ): ManagedProcess =
        processRunner.start(
            command = command,
            environment = mapOf("ADB" to adb.toString()),
            workingDirectory = toolsScrcpy.parent,
            parentDisposable = parentDisposable,
            gracefulOnDispose = gracefulOnDispose,
            onOutput = onOutput,
            onTerminated = onTerminated,
        )

    private fun List<String>.windowTitle(): String? =
        indexOf("--window-title")
            .takeIf { it >= 0 && it + 1 < size }
            ?.let { this[it + 1] }
}
