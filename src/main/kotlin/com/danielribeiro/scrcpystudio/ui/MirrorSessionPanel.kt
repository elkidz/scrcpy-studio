package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorViewModel
import com.danielribeiro.scrcpystudio.recording.RecordingFileNamer
import com.danielribeiro.scrcpystudio.session.MirrorMode
import com.danielribeiro.scrcpystudio.session.MirrorSessionState
import com.danielribeiro.scrcpystudio.session.MirrorStatus
import com.danielribeiro.scrcpystudio.session.RecordingStatus
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.filechooser.FileNameExtensionFilter

class MirrorSessionPanel(
    private val viewModel: DeviceMirrorViewModel,
    private val device: AndroidDevice,
) : JPanel(BorderLayout()), Disposable {

    private val statusLabel = JBLabel()
    private val errorLabel = JBLabel()
    private val outputLabel = JBLabel()
    private val startStopButton = JButton()
    private val recordButton = JButton()
    private val openOutputButton = JButton("Open recording")
    private val mirrorHost = EmbeddedMirrorHost(viewModel, device)
    private var currentState = MirrorSessionState(device, MirrorStatus.STOPPED)

    init {
        border = JBUI.Borders.empty(4)
        add(createHeader(), BorderLayout.NORTH)
        add(mirrorHost, BorderLayout.CENTER)
        add(createFooter(), BorderLayout.SOUTH)
        update(currentState)
    }

    fun update(state: MirrorSessionState) {
        currentState = state
        statusLabel.text = statusText(state)
        errorLabel.text = state.errorMessage.orEmpty()
        errorLabel.isVisible = state.errorMessage != null

        startStopButton.text = when (state.mirrorStatus) {
            MirrorStatus.STARTING,
            MirrorStatus.RUNNING,
            -> "Stop mirroring"

            MirrorStatus.STOPPING -> "Stopping..."
            MirrorStatus.STOPPED,
            MirrorStatus.FAILED,
            -> "Start mirroring"
        }
        startStopButton.isEnabled = state.mirrorStatus != MirrorStatus.STOPPING &&
            (state.mirrorStatus != MirrorStatus.RUNNING || state.device.canMirror)

        recordButton.text = when (state.recording.status) {
            RecordingStatus.STARTING,
            RecordingStatus.RECORDING,
            -> "Stop recording"

            RecordingStatus.STOPPING -> "Stopping recording..."
            RecordingStatus.IDLE,
            RecordingStatus.COMPLETED,
            RecordingStatus.FAILED,
            -> "Start recording"
        }
        recordButton.isEnabled = state.mirrorStatus == MirrorStatus.RUNNING &&
            state.recording.status != RecordingStatus.STOPPING

        outputLabel.text = state.recording.outputFile?.toString().orEmpty()
        openOutputButton.isVisible = state.recording.outputFile?.let {
            Files.isRegularFile(it)
        } == true

        mirrorHost.update(state)
        revalidate()
        repaint()
    }

    override fun dispose() {
        mirrorHost.dispose()
    }

    private fun createHeader(): JPanel =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(4)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
                    add(JBLabel(device.displayName))
                    add(JBLabel(device.serial))
                    add(statusLabel)
                },
                BorderLayout.WEST,
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply {
                    startStopButton.addActionListener {
                        when (currentState.mirrorStatus) {
                            MirrorStatus.RUNNING,
                            MirrorStatus.STARTING,
                            -> viewModel.stopMirror(device.serial)

                            MirrorStatus.STOPPING -> Unit
                            MirrorStatus.STOPPED,
                            MirrorStatus.FAILED,
                            -> viewModel.startMirror(device.serial)
                        }
                    }
                    add(startStopButton)

                    recordButton.addActionListener {
                        when (currentState.recording.status) {
                            RecordingStatus.STARTING,
                            RecordingStatus.RECORDING,
                            -> viewModel.stopRecording(device.serial)

                            RecordingStatus.STOPPING -> Unit
                            RecordingStatus.IDLE,
                            RecordingStatus.COMPLETED,
                            RecordingStatus.FAILED,
                            -> chooseRecordingFile()
                        }
                    }
                    add(recordButton)
                },
                BorderLayout.EAST,
            )
        }

    private fun createFooter(): JPanel =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(4)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                    errorLabel.foreground = JBColor.RED
                    add(errorLabel)
                    add(outputLabel)
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply {
                    openOutputButton.addActionListener {
                        currentState.recording.outputFile?.let { BrowserUtil.browse(it.toUri()) }
                    }
                    add(openOutputButton)
                },
                BorderLayout.EAST,
            )
        }

    private fun chooseRecordingFile() {
        val configuredDirectory = ScrcpySettingsState.getInstance()
            .getState()
            .recordingDirectory
            .takeIf(String::isNotBlank)
            ?.let(Paths::get)
            ?: Paths.get(System.getProperty("user.home"), "Videos", "Scrcpy Studio")
        val currentDirectory = configuredDirectory
            .takeIf { Files.isDirectory(it) }
            ?: Paths.get(System.getProperty("user.home"))
        val suggestedFile = RecordingFileNamer.nextFile(
            directory = configuredDirectory,
            device = device,
        )

        val chooser = JFileChooser(currentDirectory.toFile()).apply {
            dialogTitle = "Save scrcpy recording"
            selectedFile = suggestedFile.toFile()
            fileFilter = FileNameExtensionFilter("MP4 video (*.mp4)", "mp4")
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return

        val selected = chooser.selectedFile.toPath().let(::ensureMp4Extension)
        viewModel.startRecording(device.serial, selected)
    }

    private fun ensureMp4Extension(file: Path): Path =
        if (file.fileName.toString().endsWith(".mp4", ignoreCase = true)) {
            file
        } else {
            file.resolveSibling("${file.fileName}.mp4")
        }

    private fun statusText(state: MirrorSessionState): String =
        when (state.mirrorStatus) {
            MirrorStatus.STARTING -> "Starting"
            MirrorStatus.RUNNING -> when (state.recording.status) {
                RecordingStatus.STARTING -> "Preparing recording"
                RecordingStatus.RECORDING -> "Recording"
                RecordingStatus.STOPPING -> "Finishing recording"
                else -> if (state.mirrorMode == MirrorMode.EXTERNAL_FALLBACK) {
                    "External window"
                } else {
                    "Mirroring"
                }
            }

            MirrorStatus.STOPPING -> "Stopping"
            MirrorStatus.STOPPED -> "Stopped"
            MirrorStatus.FAILED -> "Failed"
        }
}
