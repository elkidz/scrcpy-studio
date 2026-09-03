package com.danielribeiro.scrcpystudio.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JFileChooser

class ScrcpySettingsConfigurable : Configurable {

    private val settings = ScrcpySettingsState.getInstance()
    private val validator = ToolValidator()

    private lateinit var scrcpyPathField: JBTextField
    private lateinit var adbPathField: JBTextField
    private lateinit var recordingDirectoryField: JBTextField
    private lateinit var testButton: javax.swing.JButton
    private val statusLabel = JBLabel("Not tested")
    private var testFuture: CompletableFuture<*>? = null

    private val settingsPanel: DialogPanel = panel {
        group("Executables") {
            row("scrcpy:") {
                scrcpyPathField = textField()
                    .columns(COLUMNS_LARGE)
                    .resizableColumn()
                    .component
                button("Browse...") {
                    chooseFile(scrcpyPathField, directoriesOnly = false)
                }
            }
            row("adb (optional):") {
                adbPathField = textField()
                    .columns(COLUMNS_LARGE)
                    .resizableColumn()
                    .component
                button("Browse...") {
                    chooseFile(adbPathField, directoriesOnly = false)
                }
            }
        }

        group("Recording") {
            row("Output directory:") {
                recordingDirectoryField = textField()
                    .columns(COLUMNS_LARGE)
                    .resizableColumn()
                    .component
                button("Browse...") {
                    chooseFile(recordingDirectoryField, directoriesOnly = true)
                }
            }
        }

        row {
            testButton = button("Test configuration") {
                testConfiguration()
            }.component
            cell(statusLabel).resizableColumn()
        }
    }

    override fun getDisplayName(): String = "Scrcpy Studio"

    override fun createComponent(): JComponent = settingsPanel

    override fun isModified(): Boolean {
        val state = settings.getState()
        return scrcpyPathField.text != state.scrcpyPath ||
            adbPathField.text != state.adbPath ||
            recordingDirectoryField.text != state.recordingDirectory
    }

    override fun apply() {
        settings.getState().apply {
            scrcpyPath = scrcpyPathField.text.trim()
            adbPath = adbPathField.text.trim()
            recordingDirectory = recordingDirectoryField.text.trim()
        }
    }

    override fun reset() {
        val state = settings.getState()
        scrcpyPathField.text = state.scrcpyPath
        adbPathField.text = state.adbPath
        recordingDirectoryField.text = state.recordingDirectory
        statusLabel.text = "Not tested"
    }

    override fun disposeUIResources() {
        testFuture?.cancel(true)
        testFuture = null
    }

    private fun testConfiguration() {
        testFuture?.cancel(true)
        testButton.isEnabled = false
        statusLabel.text = "Testing..."

        val candidate = ScrcpySettingsState.State(
            scrcpyPath = scrcpyPathField.text.trim(),
            adbPath = adbPathField.text.trim(),
            recordingDirectory = recordingDirectoryField.text.trim(),
        )

        testFuture = CompletableFuture
            .supplyAsync(
                { validator.validate(candidate) },
                AppExecutorUtil.getAppExecutorService(),
            )
            .whenComplete { result, error ->
                ApplicationManager.getApplication().invokeLater {
                    testButton.isEnabled = true
                    statusLabel.text = when {
                        error != null -> "Test failed: ${error.message ?: "unknown error"}"
                        result != null -> result.message
                        else -> "Test failed."
                    }
                }
            }
    }

    private fun chooseFile(field: JBTextField, directoriesOnly: Boolean) {
        val current = field.text.trim().takeIf(String::isNotEmpty)?.let(::File)
        val chooser = JFileChooser().apply {
            fileSelectionMode = if (directoriesOnly) {
                JFileChooser.DIRECTORIES_ONLY
            } else {
                JFileChooser.FILES_ONLY
            }
            current?.let {
                if (it.isDirectory) {
                    currentDirectory = it
                } else if (it.isFile) {
                    currentDirectory = it.parentFile
                    selectedFile = it
                }
            }
        }

        if (chooser.showOpenDialog(settingsPanel) == JFileChooser.APPROVE_OPTION) {
            field.text = chooser.selectedFile.absolutePath
        }
    }
}
