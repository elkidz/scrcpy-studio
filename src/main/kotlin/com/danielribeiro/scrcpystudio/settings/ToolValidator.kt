package com.danielribeiro.scrcpystudio.settings

import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class ToolValidationResult(
    val isValid: Boolean,
    val message: String,
    val paths: ToolPaths? = null,
)

class ToolValidator(
    private val executableResolver: ExecutableResolver = ExecutableResolver(),
    private val timeoutSeconds: Long = 10,
) {

    fun validate(settings: ScrcpySettingsState.State): ToolValidationResult {
        val paths = try {
            executableResolver.resolve(settings)
        } catch (error: ToolResolutionException) {
            return ToolValidationResult(isValid = false, message = error.message.orEmpty())
        }

        if (paths.scrcpy == paths.adb) {
            return ToolValidationResult(
                isValid = false,
                message = "scrcpy and adb resolve to the same executable. Configure both tools separately.",
                paths = paths,
            )
        }

        val scrcpyVersion = run(paths.scrcpy, "--version")
        if (scrcpyVersion.exitCode != 0) {
            return ToolValidationResult(
                isValid = false,
                message = "scrcpy could not be started: ${scrcpyVersion.describe()}",
                paths = paths,
            )
        }

        val adbDevices = run(paths.adb, "devices")
        if (adbDevices.exitCode != 0) {
            return ToolValidationResult(
                isValid = false,
                message = "adb could not list devices: ${adbDevices.describe()}",
                paths = paths,
            )
        }

        val versionText = scrcpyVersion.output
            .lineSequence()
            .firstOrNull { it.contains("scrcpy", ignoreCase = true) }
            ?.trim()
            .orEmpty()
        if (versionText.isEmpty()) {
            return ToolValidationResult(
                isValid = false,
                message = "The configured scrcpy executable did not report a scrcpy version.",
                paths = paths,
            )
        }

        return ToolValidationResult(
            isValid = true,
            message = buildString {
                append("$versionText; adb is ready.")
                if (paths.scrcpyServer == null) {
                    append(
                        " scrcpy-server was not found beside scrcpy; " +
                            "embedded mirroring will use the external-window fallback.",
                    )
                } else {
                    append(" scrcpy-server is ready for embedded mirroring.")
                }
            },
            paths = paths,
        )
    }

    private fun run(executable: Path, vararg arguments: String): CommandResult {
        val command = buildList {
            add(executable.toString())
            addAll(arguments)
        }

        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                CommandResult(
                    exitCode = null,
                    output = "",
                    timedOut = true,
                )
            } else {
                CommandResult(
                    exitCode = process.exitValue(),
                    output = process.inputStream.bufferedReader().use { it.readText() },
                    timedOut = false,
                )
            }
        } catch (error: Exception) {
            CommandResult(
                exitCode = null,
                output = error.message.orEmpty(),
                timedOut = false,
            )
        }
    }

    private data class CommandResult(
        val exitCode: Int?,
        val output: String,
        val timedOut: Boolean,
    ) {
        fun describe(): String = when {
            timedOut -> "the command timed out"
            output.isBlank() -> "exit code ${exitCode ?: "unknown"}"
            else -> output.trim().lineSequence().take(3).joinToString(" ")
        }
    }
}
