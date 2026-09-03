package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorViewModel
import com.danielribeiro.scrcpystudio.recording.RecordingFileNamer
import com.danielribeiro.scrcpystudio.screenshot.ScreenshotFileNamer
import com.danielribeiro.scrcpystudio.session.MirrorMode
import com.danielribeiro.scrcpystudio.session.MirrorSessionState
import com.danielribeiro.scrcpystudio.session.MirrorStatus
import com.danielribeiro.scrcpystudio.session.RecordingStatus
import com.danielribeiro.scrcpystudio.session.ScreenshotStatus
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.filechooser.FileNameExtensionFilter

class MirrorSessionPanel(
    private val viewModel: DeviceMirrorViewModel,
    initialDevice: AndroidDevice,
) : JPanel(BorderLayout()), Disposable {

    private var device = initialDevice
    val serial: String
        get() = device.serial

    private val deviceNameLabel = JBLabel()
    private val serialLabel = JBLabel()
    private val statusLabel = JBLabel()
    private val modeMessageLabel = JBLabel()
    private val errorLabel = JBLabel()
    private val outputLabel = JBLabel()
    private val screenshotLabel = JBLabel()
    private val startStopButton = JButton()
    private val recordButton = JButton()
    private val rotateButton = actionButton("Rotate", "Rotate the device display") {
        viewModel.rotate(device.serial)
    }
    private val screenshotButton = actionButton("Screenshot", "Save a PNG screenshot") {
        chooseScreenshotFile()
    }
    private val backButton = actionButton("Back", "Navigate back on the device") {
        viewModel.sendBack(device.serial)
    }
    private val homeButton = actionButton("Home", "Navigate to the device home screen") {
        viewModel.sendHome(device.serial)
    }
    private val recentsButton = actionButton("Recents", "Open recent apps on the device") {
        viewModel.sendRecents(device.serial)
    }
    private val modeButton = actionButton("External", "Switch mirror view") {
        viewModel.toggleMirrorMode(device.serial)
    }
    private val openOutputButton = JButton("Open recording")
    private val openScreenshotButton = JButton("Open screenshot")
    private val mirrorHost = EmbeddedMirrorHost(viewModel, device)
    private var currentState = MirrorSessionState(device, MirrorStatus.STOPPED)

    init {
        border = JBUI.Borders.empty(4)
        add(createHeader(), BorderLayout.NORTH)
        add(mirrorHost, BorderLayout.CENTER)
        add(createFooter(), BorderLayout.SOUTH)
        updateDevice(device)
        update(currentState)
    }

    fun updateDevice(updatedDevice: AndroidDevice) {
        require(updatedDevice.serial == device.serial) {
            "A device tab cannot change its serial number."
        }
        device = updatedDevice
        deviceNameLabel.text = updatedDevice.displayName
        serialLabel.text = updatedDevice.serial
    }

    fun update(state: MirrorSessionState) {
        currentState = state
        statusLabel.text = statusText(state)
        modeMessageLabel.text = state.modeMessage.orEmpty()
        modeMessageLabel.isVisible = state.modeMessage != null
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
            state.device.canMirror

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

        val isRunning = state.mirrorStatus == MirrorStatus.RUNNING
        val canControl = state.device.canMirror && isRunning
        rotateButton.isEnabled = canControl
        backButton.isEnabled = canControl
        homeButton.isEnabled = canControl
        recentsButton.isEnabled = canControl
        screenshotButton.isEnabled = state.device.canMirror
        modeButton.isEnabled = state.device.canMirror &&
            state.mirrorStatus !in setOf(MirrorStatus.STARTING, MirrorStatus.STOPPING)
        modeButton.text = if (state.mirrorMode == MirrorMode.EMBEDDED) {
            "External"
        } else {
            "Embedded"
        }
        modeButton.toolTipText = if (state.mirrorMode == MirrorMode.EMBEDDED) {
            "Switch to the external scrcpy window"
        } else {
            "Switch to the embedded scrcpy view"
        }

        outputLabel.text = state.recording.outputFile?.toString().orEmpty()
        openOutputButton.isVisible = state.recording.outputFile?.let {
            Files.isRegularFile(it)
        } == true
        screenshotLabel.text = when (state.screenshot.status) {
            ScreenshotStatus.SAVING -> "Saving screenshot..."
            ScreenshotStatus.COMPLETED -> state.screenshot.outputFile?.toString().orEmpty()
            ScreenshotStatus.FAILED -> state.screenshot.errorMessage.orEmpty()
            ScreenshotStatus.IDLE -> ""
        }
        screenshotLabel.foreground = if (state.screenshot.status == ScreenshotStatus.FAILED) {
            JBColor.RED
        } else {
            JBColor.GRAY
        }
        openScreenshotButton.isVisible = state.screenshot.outputFile?.let {
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
                    add(deviceNameLabel)
                    add(serialLabel)
                    add(statusLabel)
                    add(modeMessageLabel)
                },
                BorderLayout.NORTH,
            )
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                    add(rotateButton)
                    add(screenshotButton)
                    add(backButton)
                    add(homeButton)
                    add(recentsButton)
                    add(modeButton)
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
                BorderLayout.CENTER,
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
                    add(screenshotLabel)
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply {
                    openOutputButton.addActionListener {
                        currentState.recording.outputFile?.let { BrowserUtil.browse(it.toUri()) }
                    }
                    add(openOutputButton)
                    openScreenshotButton.addActionListener {
                        currentState.screenshot.outputFile?.let { BrowserUtil.browse(it.toUri()) }
                    }
                    add(openScreenshotButton)
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

    private fun chooseScreenshotFile() {
        val directory = Paths.get(
            System.getProperty("user.home"),
            "Pictures",
            "Scrcpy Studio",
        )
        val currentDirectory = directory
            .takeIf(Files::isDirectory)
            ?: Paths.get(System.getProperty("user.home"))
        val suggestedFile = ScreenshotFileNamer.nextFile(directory, device)

        val chooser = JFileChooser(currentDirectory.toFile()).apply {
            dialogTitle = "Save device screenshot"
            selectedFile = suggestedFile.toFile()
            fileFilter = FileNameExtensionFilter("PNG image (*.png)", "png")
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return

        val selected = chooser.selectedFile.toPath().let(::ensurePngExtension)
        viewModel.takeScreenshot(device.serial, selected)
    }

    private fun ensureMp4Extension(file: Path): Path =
        if (file.fileName.toString().endsWith(".mp4", ignoreCase = true)) {
            file
        } else {
            file.resolveSibling("${file.fileName}.mp4")
        }

    private fun ensurePngExtension(file: Path): Path =
        if (file.fileName.toString().endsWith(".png", ignoreCase = true)) {
            file
        } else {
            file.resolveSibling("${file.fileName}.png")
        }

    private fun actionButton(
        text: String,
        tooltip: String,
        action: () -> Unit,
    ): JButton = JButton(text).apply {
        toolTipText = tooltip
        accessibleContext.accessibleName = tooltip
        addActionListener { action() }
    }

    private fun statusText(state: MirrorSessionState): String =
        when (state.mirrorStatus) {
            MirrorStatus.STARTING -> "Starting"
            MirrorStatus.RUNNING -> when (state.recording.status) {
                RecordingStatus.STARTING -> "Preparing recording"
                RecordingStatus.RECORDING -> "Recording"
                RecordingStatus.STOPPING -> "Finishing recording"
                else -> if (state.mirrorMode == MirrorMode.EXTERNAL) {
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
