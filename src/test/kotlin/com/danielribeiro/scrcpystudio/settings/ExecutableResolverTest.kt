package com.danielribeiro.scrcpystudio.settings

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ExecutableResolverTest {

    private lateinit var tempDirectory: Path

    @Before
    fun setUp() {
        tempDirectory = Files.createTempDirectory("scrcpy-studio-resolver-test")
    }

    @After
    fun tearDown() {
        Files.walk(tempDirectory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun resolvesConfiguredDirectoryAndSdkAdb() {
        val scrcpyDirectory = Files.createDirectories(tempDirectory.resolve("scrcpy"))
        val sdkDirectory = Files.createDirectories(
            tempDirectory.resolve("sdk").resolve("platform-tools"),
        )
        val scrcpy = Files.createFile(scrcpyDirectory.resolve("scrcpy.exe"))
        val adb = Files.createFile(sdkDirectory.resolve("adb.exe"))

        val resolver = ExecutableResolver(
            osName = "Windows 11",
            environment = mapOf("ANDROID_SDK_ROOT" to sdkDirectory.parent.toString()),
        )
        val paths = resolver.resolve(
            ScrcpySettingsState.State(scrcpyPath = scrcpyDirectory.toString()),
        )

        assertEquals(scrcpy.toAbsolutePath().normalize(), paths.scrcpy)
        assertEquals(adb.toAbsolutePath().normalize(), paths.adb)
    }

    @Test
    fun prefersSdkAdbBeforePathAdb() {
        val sdkDirectory = Files.createDirectories(
            tempDirectory.resolve("sdk").resolve("platform-tools"),
        )
        val pathDirectory = Files.createDirectories(tempDirectory.resolve("path"))
        val sdkAdb = Files.createFile(sdkDirectory.resolve("adb.exe"))
        val scrcpy = Files.createFile(pathDirectory.resolve("scrcpy.exe"))

        val resolver = ExecutableResolver(
            osName = "Windows 11",
            environment = mapOf(
                "ANDROID_SDK_ROOT" to sdkDirectory.parent.toString(),
                "PATH" to pathDirectory.toString(),
            ),
        )
        val paths = resolver.resolve(ScrcpySettingsState.State())

        assertEquals(sdkAdb.toAbsolutePath().normalize(), paths.adb)
        assertEquals(scrcpy.toAbsolutePath().normalize(), paths.scrcpy)
    }

    @Test
    fun resolvesAdbFromConfiguredScrcpyDirectory() {
        val toolDirectory = Files.createDirectories(tempDirectory.resolve("scrcpy-bundle"))
        val scrcpy = Files.createFile(toolDirectory.resolve("scrcpy.exe"))
        val adb = Files.createFile(toolDirectory.resolve("adb.exe"))
        val server = Files.createFile(toolDirectory.resolve("scrcpy-server"))
        val resolver = ExecutableResolver(
            osName = "Windows 11",
            environment = emptyMap(),
        )

        val paths = resolver.resolve(
            ScrcpySettingsState.State(scrcpyPath = scrcpy.toString()),
        )

        assertEquals(adb.toAbsolutePath().normalize(), paths.adb)
        assertEquals(server.toAbsolutePath().normalize(), paths.scrcpyServer)
    }

    @Test
    fun reportsMissingConfiguredExecutable() {
        val resolver = ExecutableResolver(
            osName = "Windows 11",
            environment = emptyMap(),
        )

        assertThrows(ToolResolutionException::class.java) {
            resolver.resolve(
                ScrcpySettingsState.State(
                    scrcpyPath = tempDirectory.resolve("missing-scrcpy.exe").toString(),
                ),
            )
        }
    }
}
