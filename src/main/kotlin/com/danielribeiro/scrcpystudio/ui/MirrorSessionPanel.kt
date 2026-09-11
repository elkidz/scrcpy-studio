package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorViewModel
import com.danielribeiro.scrcpystudio.recording.RecordingFileNamer
import com.danielribeiro.scrcpystudio.screenshot.ScreenshotFileNamer
import com.danielribeiro.scrcpystudio.session.MirrorMode
import com.danielribeiro.scrcpystudio.session.MirrorSessionState
import com.danielribeiro.scrcpystudio.session.MirrorStatus
import com.danielribeiro.scrcpystudio.session.RecordingStatus
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsConfigurable
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import com.intellij.ide.BrowserUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.filechooser.FileNameExtensionFilter

class MirrorSessionPanel(
    private val project: Project,
    private val viewModel: DeviceMirrorViewModel,
    initialDevice: AndroidDevice,
) : JPanel(BorderLayout()), Disposable {

    private var device = initialDevice
    val serial: String
        get() = device.serial

    private val errorLabel = JBLabel()
    private var currentState = MirrorSessionState(device, MirrorStatus.STOPPED)
    private val powerButton = createScrcpyIconButton(
        icon = ScrcpyIcons.DevicePower,
        tooltip = "Power",
    ) {
        viewModel.sendPower(device.serial)
    }
    private val volumeDownButton = createScrcpyIconButton(
        icon = ScrcpyIcons.DeviceVolumeDown,
        tooltip = "Volume down",
    ) {
        viewModel.sendVolumeDown(device.serial)
    }
    private val volumeUpButton = createScrcpyIconButton(
        icon = ScrcpyIcons.DeviceVolumeUp,
        tooltip = "Volume up",
    ) {
        viewModel.sendVolumeUp(device.serial)
    }
    private val startStopButton = createScrcpyIconButton(
        icon = AllIcons.Actions.Execute,
        tooltip = "Start mirroring",
    ) {
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
    private val recordButton = createScrcpyIconButton(
        icon = ScrcpyIcons.DeviceRecord,
        tooltip = "Start recording",
    ) {
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
    private val rotateButton = createScrcpyIconButton(
        icon = ScrcpyIcons.DeviceRotate,
        tooltip = "Rotate the device display",
    ) {
        viewModel.rotate(device.serial)
    }
    private val screenshotButton = createScrcpyIconButton(
        icon = ScrcpyIcons.DeviceScreenshot,
        tooltip = "Save a PNG screenshot",
    ) {
        chooseScreenshotFile()
    }
    private val backButton = createScrcpyIconButton(
        icon = ScrcpyIcons.DeviceBack,
        tooltip = "Navigate back on the device",
    ) {
        viewModel.sendBack(device.serial)
    }
    private val homeButton = createScrcpyIconButton(
        icon = ScrcpyIcons.DeviceHome,
        tooltip = "Navigate to the device home screen",
    ) {
        viewModel.sendHome(device.serial)
    }
    private val recentsButton = createScrcpyIconButton(
        icon = ScrcpyIcons.DeviceRecents,
        tooltip = "Open recent apps on the device",
    ) {
        viewModel.sendRecents(device.serial)
    }
    private val modeButton = createScrcpyIconButton(
        icon = AllIcons.Actions.SwapPanels,
        tooltip = "Switch mirror view",
    ) {
        viewModel.toggleMirrorMode(device.serial)
    }
    private val optionsButton = createScrcpyIconButton(
        icon = ScrcpyIcons.ScrcpyOptions,
        tooltip = "Scrcpy options",
    ) {
        showScrcpyOptions()
    }
    private val settingsButton = createScrcpyIconButton(
        icon = AllIcons.General.GearPlain,
        tooltip = "Open Scrcpy Studio settings",
    ) {
        ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, ScrcpySettingsConfigurable::class.java)
    }
    private val openOutputButton = createScrcpyIconButton(
        icon = AllIcons.Actions.ShowViewer,
        tooltip = "Open recording",
    ) {
        currentState.recording.outputFile?.let { BrowserUtil.browse(it.toUri()) }
    }
    private val openScreenshotButton = createScrcpyIconButton(
        icon = AllIcons.Actions.ShowViewer,
        tooltip = "Open screenshot",
    ) {
        currentState.screenshot.outputFile?.let { BrowserUtil.browse(it.toUri()) }
    }
    private val mirrorHost = EmbeddedMirrorHost(viewModel, device)
    private val footer = createFooter()

    init {
        border = JBUI.Borders.empty(4)
        add(createToolbar(), BorderLayout.NORTH)
        add(mirrorHost, BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)
        updateDevice(device)
        update(currentState)
    }

    fun updateDevice(updatedDevice: AndroidDevice) {
        require(updatedDevice.serial == device.serial) {
            "A device tab cannot change its serial number."
        }
        device = updatedDevice
    }

    fun update(state: MirrorSessionState) {
        currentState = state
        errorLabel.text = state.errorMessage.orEmpty()
        errorLabel.isVisible = state.errorMessage != null

        val startStopTooltip = when (state.mirrorStatus) {
            MirrorStatus.STARTING,
            MirrorStatus.RUNNING,
            -> "Stop mirroring"

            MirrorStatus.STOPPING -> "Stopping mirroring..."
            MirrorStatus.STOPPED,
            MirrorStatus.FAILED,
            -> "Start mirroring"
        }
        updateScrcpyIconButton(
            button = startStopButton,
            icon = when (state.mirrorStatus) {
                MirrorStatus.STARTING,
                MirrorStatus.RUNNING,
                -> AllIcons.Actions.Close

                MirrorStatus.STOPPING -> AllIcons.Actions.Suspend
                MirrorStatus.STOPPED,
                MirrorStatus.FAILED,
                -> AllIcons.Actions.Execute
            },
            tooltip = startStopTooltip,
        )
        startStopButton.isEnabled = state.mirrorStatus != MirrorStatus.STOPPING &&
            state.device.canMirror

        val recordTooltip = when (state.recording.status) {
            RecordingStatus.STARTING,
            RecordingStatus.RECORDING,
            -> "Stop recording"

            RecordingStatus.STOPPING -> "Stopping recording..."
            RecordingStatus.IDLE,
            RecordingStatus.COMPLETED,
            RecordingStatus.FAILED,
            -> "Start recording"
        }
        updateScrcpyIconButton(
            button = recordButton,
            icon = when (state.recording.status) {
                RecordingStatus.STARTING,
                RecordingStatus.RECORDING,
                -> AllIcons.Actions.Close

                RecordingStatus.STOPPING -> AllIcons.Actions.Suspend
                RecordingStatus.IDLE,
                RecordingStatus.COMPLETED,
                RecordingStatus.FAILED,
                -> ScrcpyIcons.DeviceRecord
            },
            tooltip = recordTooltip,
        )
        recordButton.isEnabled = state.mirrorStatus == MirrorStatus.RUNNING &&
            state.recording.status != RecordingStatus.STOPPING

        val isRunning = state.mirrorStatus == MirrorStatus.RUNNING
        val canControl = state.device.canMirror && isRunning
        val canUseHardwareKeys = state.device.canMirror
        powerButton.isEnabled = canUseHardwareKeys
        volumeDownButton.isEnabled = canUseHardwareKeys
        volumeUpButton.isEnabled = canUseHardwareKeys
        rotateButton.isEnabled = canControl
        backButton.isEnabled = canControl
        homeButton.isEnabled = canControl
        recentsButton.isEnabled = canControl
        screenshotButton.isEnabled = state.device.canMirror
        modeButton.isEnabled = state.device.canMirror &&
            state.mirrorStatus !in setOf(MirrorStatus.STARTING, MirrorStatus.STOPPING)
        val modeTooltip = if (state.mirrorMode == MirrorMode.EMBEDDED) {
            "Switch to the external scrcpy window"
        } else {
            "Switch to the embedded scrcpy view"
        }
        updateScrcpyIconButton(
            button = modeButton,
            icon = AllIcons.Actions.SwapPanels,
            tooltip = modeTooltip,
        )

        openOutputButton.isVisible = state.recording.outputFile?.let {
            Files.isRegularFile(it)
        } == true
        openScreenshotButton.isVisible = state.screenshot.outputFile?.let {
            Files.isRegularFile(it)
        } == true
        footer.isVisible = errorLabel.isVisible ||
            openOutputButton.isVisible ||
            openScreenshotButton.isVisible

        mirrorHost.update(state)
        revalidate()
        repaint()
    }

    override fun dispose() {
        mirrorHost.dispose()
    }

    private fun createToolbar(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            border = JBUI.Borders.emptyBottom(4)
            add(powerButton.component)
            add(volumeDownButton.component)
            add(volumeUpButton.component)
            add(createScrcpyToolbarSeparator())
            add(rotateButton.component)
            add(createScrcpyToolbarSeparator())
            add(backButton.component)
            add(homeButton.component)
            add(recentsButton.component)
            add(createScrcpyToolbarSeparator())
            add(screenshotButton.component)
            add(recordButton.component)
            add(createScrcpyToolbarSeparator())
            add(modeButton.component)
            add(startStopButton.component)
            add(optionsButton.component)
            add(settingsButton.component)
        }

    private fun createFooter(): JPanel =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(4)
            isVisible = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                    errorLabel.foreground = JBColor.RED
                    add(errorLabel)
                },
                BorderLayout.CENTER,
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                    add(openOutputButton.component)
                    add(openScreenshotButton.component)
                },
                BorderLayout.EAST,
            )
        }

    private fun showScrcpyOptions() {
        ScrcpyOptionsPopup.show(optionsButton.component, viewModel)
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
            .takeIf { Files.isDirectory(it) }
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
}
