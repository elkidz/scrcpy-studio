package com.danielribeiro.scrcpystudio.settings

import java.io.File
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

data class ToolPaths(
    val scrcpy: Path,
    val adb: Path,
    val scrcpyServer: Path?,
)

class ToolResolutionException(message: String) : IllegalStateException(message)

class ExecutableResolver(
    private val osName: String = System.getProperty("os.name"),
    environment: Map<String, String> = System.getenv(),
) {

    private val isWindows = osName.startsWith("Windows", ignoreCase = true)
    private val pathEntries = environment["PATH"]
        ?.split(File.pathSeparator)
        ?.filter(String::isNotBlank)
        .orEmpty()
        .mapNotNull(::safePath)
    private val sdkRoots = buildList {
        environment["ANDROID_SDK_ROOT"]?.let(::safePath)?.let(::add)
        environment["ANDROID_HOME"]?.let(::safePath)?.let(::add)

        if (isWindows) {
            environment["LOCALAPPDATA"]
                ?.let(::safePath)
                ?.resolve("Android")
                ?.resolve("Sdk")
                ?.let(::add)
        } else {
            safePath(environment["HOME"])
                ?.resolve("Library")
                ?.resolve("Android")
                ?.resolve("sdk")
                ?.let(::add)
            safePath(environment["HOME"])
                ?.resolve("Android")
                ?.resolve("Sdk")
                ?.let(::add)
        }
    }.distinct()

    fun resolve(settings: ScrcpySettingsState.State): ToolPaths {
        val scrcpy = resolveConfigured(settings.scrcpyPath, "scrcpy")
            ?: resolveOnPath("scrcpy")
            ?: throw ToolResolutionException(
                "scrcpy was not found. Configure the scrcpy executable in Settings | Tools | Scrcpy Studio.",
            )

        val adb = resolveConfigured(settings.adbPath, "adb")
            ?: resolveSibling(scrcpy, "adb")
            ?: resolveFromSdk()
            ?: resolveOnPath("adb")
            ?: throw ToolResolutionException(
                "adb was not found. Configure adb or install the Android SDK platform-tools.",
            )

        return ToolPaths(
            scrcpy = scrcpy,
            adb = adb,
            scrcpyServer = resolveServer(scrcpy),
        )
    }

    private fun resolveConfigured(value: String, baseName: String): Path? {
        if (value.isBlank()) return null

        val configured = safePath(value)
            ?: throw ToolResolutionException("The configured $baseName path is invalid.")

        if (isRegularFile(configured)) return configured.toAbsolutePath().normalize()

        if (Files.isDirectory(configured)) {
            return executableNames(baseName)
                .asSequence()
                .map(configured::resolve)
                .firstOrNull(::isRegularFile)
                ?.toAbsolutePath()
                ?.normalize()
                ?: throw ToolResolutionException(
                    "The configured directory does not contain $baseName: $value",
                )
        }

        throw ToolResolutionException("The configured $baseName executable does not exist: $value")
    }

    private fun resolveFromSdk(): Path? =
        sdkRoots.asSequence()
            .map { it.resolve("platform-tools") }
            .flatMap { directory ->
                executableNames("adb").asSequence().map(directory::resolve)
            }
            .firstOrNull(::isRegularFile)
            ?.toAbsolutePath()
            ?.normalize()

    private fun resolveSibling(executable: Path, baseName: String): Path? =
        executable.parent
            ?.let { directory ->
                executableNames(baseName)
                    .asSequence()
                    .map(directory::resolve)
                    .firstOrNull(::isRegularFile)
            }
            ?.toAbsolutePath()
            ?.normalize()

    private fun resolveServer(executable: Path): Path? =
        executable.parent
            ?.let { directory ->
                listOf("scrcpy-server", "scrcpy-server.jar")
                    .asSequence()
                    .map(directory::resolve)
                    .firstOrNull(::isRegularFile)
            }
            ?.toAbsolutePath()
            ?.normalize()

    private fun resolveOnPath(baseName: String): Path? =
        pathEntries.asSequence()
            .flatMap { directory ->
                executableNames(baseName).asSequence().map(directory::resolve)
            }
            .firstOrNull(::isRegularFile)
            ?.toAbsolutePath()
            ?.normalize()

    private fun executableNames(baseName: String): List<String> =
        if (isWindows) listOf("$baseName.exe", baseName) else listOf(baseName)

    private fun isRegularFile(path: Path): Boolean =
        runCatching { Files.isRegularFile(path) }.getOrDefault(false)

    private fun safePath(value: String?): Path? {
        if (value.isNullOrBlank()) return null
        return try {
            Paths.get(value)
        } catch (_: InvalidPathException) {
            null
        }
    }
}
