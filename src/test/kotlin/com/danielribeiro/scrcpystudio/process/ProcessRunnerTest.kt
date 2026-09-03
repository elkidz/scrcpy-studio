package com.danielribeiro.scrcpystudio.process

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ProcessRunnerTest {

    @Test
    fun capturesJavaProcessOutputAndExitCode() = runBlocking {
        val javaExecutable = javaExecutablePath()

        val result = ProcessRunner().execute(
            command = listOf(javaExecutable.toString(), "-version"),
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("version", ignoreCase = true))
    }

    @Test
    fun reportsNonZeroExitCode() = runBlocking {
        val result = ProcessRunner().execute(
            command = listOf(javaExecutablePath().toString(), "-invalid-option"),
        )

        assertTrue(result.exitCode != 0)
        assertTrue(result.output.isNotBlank())
    }

    @Test
    fun capturesBinaryProcessOutputAndSeparateErrorStream() = runBlocking {
        val result = ProcessRunner().executeBinary(
            command = listOf(javaExecutablePath().toString(), "-version"),
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.errorOutput.contains("version", ignoreCase = true))
    }

    private fun javaExecutablePath() =
        Files
            .isRegularFile(
                javaHome().resolve("bin").resolve("java.exe"),
            )
            .let {
                if (it) {
                    javaHome().resolve("bin").resolve("java.exe")
                } else {
                    javaHome().resolve("bin").resolve("java")
                }
            }

    private fun javaHome() = java.nio.file.Paths.get(System.getProperty("java.home"))
}
