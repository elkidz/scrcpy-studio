package com.danielribeiro.scrcpystudio.settings

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ToolValidatorTest {

    private lateinit var tempDirectory: Path

    @Before
    fun setUp() {
        tempDirectory = Files.createTempDirectory("scrcpy-studio-validator-test")
    }

    @After
    fun tearDown() {
        Files.walk(tempDirectory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun rejectsWhenScrcpyAndAdbPointToSameExecutable() {
        val executable = Files.createFile(tempDirectory.resolve("adb.exe"))
        val settings = ScrcpySettingsState.State(
            scrcpyPath = executable.toString(),
            adbPath = executable.toString(),
        )

        val result = ToolValidator().validate(settings)

        assertFalse(result.isValid)
        assertTrue(result.message.contains("same executable"))
    }
}
