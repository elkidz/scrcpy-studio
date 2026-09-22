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
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsConfigurable
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import com.intellij.ide.BrowserUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JPanel

class MirrorSessionPanel(
    private val project: Project,
    private val viewModel: DeviceMirrorViewModel,
    initialDevice: AndroidDevice,
    private val toolWindow: ToolWindow,
) : JPanel(BorderLayout()), Disposable {

    private var device = initialDevice
    val serial: String
        get() = device.serial

    private val errorLabel = JBLabel()
    private val modeLabel = JBLabel()
    private var currentState = MirrorSessionState(device, MirrorStatus.STOPPED)
    private var recordingDialog: RecordingStatusDialog? = null
    private var screenshotDialog: ScreenshotPreviewDialog? = null
    private var screenshotPreviewFile: Path? = null
    private val discardedScreenshotPreviews = mutableSetOf<Path>()
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
            -> showRecordingOptions()
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
        showScreenshotPreview()
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
    private val windowModeButton = createScrcpyIconButton(
        icon = AllIcons.Actions.MoveToWindow,
        tooltip = "Open Scrcpy Studio in a floating window",
    ) {
        toggleToolWindowMode()
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
        val errorMessage = state.errorMessage
            ?: state.recording.errorMessage
            ?: state.screenshot.errorMessage
        errorLabel.text = errorMessage.orEmpty()
        errorLabel.isVisible = errorMessage != null
        val externalMode = state.mirrorMode == MirrorMode.EXTERNAL &&
            state.mirrorStatus == MirrorStatus.RUNNING
        modeLabel.text = state.modeMessage
            ?: if (externalMode) "External scrcpy window" else ""
        modeLabel.isVisible = state.modeMessage != null || externalMode
        when (state.recording.status) {
            RecordingStatus.STARTING,
            RecordingStatus.RECORDING,
            RecordingStatus.STOPPING,
            -> recordingDialog?.updateStatus(state.recording.status)

            RecordingStatus.IDLE,
            RecordingStatus.COMPLETED,
            RecordingStatus.FAILED,
            -> {
                recordingDialog?.closeAfterStop()
                recordingDialog = null
            }
        }
        when (state.screenshot.status) {
            ScreenshotStatus.SAVING -> screenshotDialog?.setCapturing()
            ScreenshotStatus.COMPLETED -> {
                val outputFile = state.screenshot.outputFile
                if (outputFile != null && outputFile == screenshotPreviewFile) {
                    screenshotDialog?.setImage(outputFile)
                }
                cleanUpDiscardedScreenshot(outputFile)
            }

            ScreenshotStatus.FAILED -> {
                screenshotDialog?.showError(
                    state.screenshot.errorMessage ?: "Unable to capture the screenshot.",
                )
                cleanUpDiscardedScreenshot(state.screenshot.outputFile)
            }

            ScreenshotStatus.IDLE -> Unit
        }

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
            state.recording.status !in setOf(
                RecordingStatus.STARTING,
                RecordingStatus.STOPPING,
            )

        val isRunning = state.mirrorStatus == MirrorStatus.RUNNING
        val canControl = state.device.canMirror && isRunning
        powerButton.isEnabled = canControl
        volumeDownButton.isEnabled = canControl
        volumeUpButton.isEnabled = canControl
        rotateButton.isEnabled = canControl
        backButton.isEnabled = canControl
        homeButton.isEnabled = canControl
        recentsButton.isEnabled = canControl
        screenshotButton.isEnabled = canControl
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
        updateWindowModeButton()

        openOutputButton.isVisible = state.recording.outputFile?.let {
            Files.isRegularFile(it)
        } == true
        openScreenshotButton.isVisible = state.screenshot.outputFile?.let {
            Files.isRegularFile(it)
        } == true
        footer.isVisible = errorLabel.isVisible ||
            modeLabel.isVisible ||
            openOutputButton.isVisible ||
            openScreenshotButton.isVisible

        mirrorHost.update(state)
        revalidate()
        repaint()
    }

    override fun dispose() {
        recordingDialog?.closeAfterStop()
        recordingDialog = null
        cancelScreenshotPreview()
        mirrorHost.dispose()
    }

    private fun createToolbar(): JPanel =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyBottom(4)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
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
                    add(optionsButton.component)
                    add(settingsButton.component)
                },
                BorderLayout.WEST,
            )
            add(windowModeButton.component, BorderLayout.EAST)
        }

    private fun createFooter(): JPanel =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(4)
            isVisible = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                    errorLabel.foreground = JBColor.RED
                    modeLabel.foreground = JBColor.namedColor(
                        "Label.infoForeground",
                        JBColor(Color(0x589DF6), Color(0x589DF6)),
                    )
                    add(modeLabel)
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

    private fun toggleToolWindowMode() {
        val nextType = if (toolWindow.type == ToolWindowType.FLOATING) {
            ToolWindowType.DOCKED
        } else {
            ToolWindowType.FLOATING
        }
        toolWindow.setType(nextType, null)
        updateWindowModeButton()
    }

    private fun updateWindowModeButton() {
        val isFloating = toolWindow.type == ToolWindowType.FLOATING
        updateScrcpyIconButton(
            button = windowModeButton,
            icon = AllIcons.Actions.MoveToWindow,
            tooltip = if (isFloating) {
                "Dock Scrcpy Studio"
            } else {
                "Open Scrcpy Studio in a floating window"
            },
        )
    }

    private fun showRecordingOptions() {
        val configuredDirectory = configuredRecordingDirectory()
        val dialog = RecordingOptionsDialog(
            project = project,
            initialDirectory = configuredDirectory,
            outputDirectoryProvider = ::configuredRecordingDirectory,
        ) {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, ScrcpySettingsConfigurable::class.java)
        }
        if (!dialog.showAndGet()) return

        val options = dialog.recordingOptions ?: return
        val outputDirectory = dialog.selectedOutputDirectory()
        val outputFile = RecordingFileNamer.nextFile(
            directory = outputDirectory,
            device = device,
        )
        val statusDialog = RecordingStatusDialog(
            project = project,
            device = device,
        ) {
            viewModel.stopRecording(device.serial)
        }
        recordingDialog?.closeAfterStop()
        recordingDialog = statusDialog
        statusDialog.show()
        viewModel.startRecording(
            serial = device.serial,
            outputFile = outputFile,
            options = options,
        )
    }

    private fun configuredRecordingDirectory(): Path =
        ScrcpySettingsState.getInstance()
            .getState()
            .recordingDirectory
            .takeIf(String::isNotBlank)
            ?.let(Paths::get)
            ?: Paths.get(System.getProperty("user.home"), "Videos", "Scrcpy Studio")

    private fun showScreenshotPreview() {
        val dialog = ScreenshotPreviewDialog(
            project = project,
            device = device,
            initialDirectory = configuredScreenshotDirectory(),
            outputDirectoryProvider = ::configuredScreenshotDirectory,
            onConfigure = {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, ScrcpySettingsConfigurable::class.java)
            },
            onRecapture = ::captureScreenshotPreview,
            onSave = ::saveScreenshotPreview,
            onCancel = ::cancelScreenshotPreview,
        )
        screenshotDialog = dialog
        captureScreenshotPreview()
        dialog.show()
    }

    private fun captureScreenshotPreview() {
        screenshotPreviewFile?.let {
            Files.deleteIfExists(it)
        }
        val temporary = Files.createTempFile("scrcpy-studio-screenshot-", ".png").also {
            Files.deleteIfExists(it)
        }
        screenshotPreviewFile = temporary
        viewModel.takeScreenshot(device.serial, temporary)
    }

    private fun saveScreenshotPreview(resolutionPercent: Int) {
        val previewFile = screenshotPreviewFile ?: return
        val outputFile = ScreenshotFileNamer.nextFile(
            directory = configuredScreenshotDirectory(),
            device = device,
        )
        screenshotDialog = null
        screenshotPreviewFile = null
        viewModel.saveScreenshotPreview(
            serial = device.serial,
            previewFile = previewFile,
            outputFile = outputFile,
            resolutionPercent = resolutionPercent,
        )
    }

    private fun cancelScreenshotPreview() {
        screenshotDialog?.let {
            if (it.isShowing) {
                it.close(DialogWrapper.CANCEL_EXIT_CODE)
            }
        }
        screenshotDialog = null
        screenshotPreviewFile?.let {
            discardedScreenshotPreviews.add(it)
            Files.deleteIfExists(it)
        }
        screenshotPreviewFile = null
    }

    private fun cleanUpDiscardedScreenshot(outputFile: Path?) {
        outputFile ?: return
        if (discardedScreenshotPreviews.remove(outputFile)) {
            Files.deleteIfExists(outputFile)
        }
    }

    private fun configuredScreenshotDirectory(): Path =
        ScrcpySettingsState.getInstance()
            .getState()
            .screenshotDirectory
            .takeIf(String::isNotBlank)
            ?.let(Paths::get)
            ?: Paths.get(System.getProperty("user.home"), "Pictures", "Scrcpy Studio")
}
