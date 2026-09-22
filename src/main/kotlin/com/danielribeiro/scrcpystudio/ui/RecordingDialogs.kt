package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.recording.RecordingOptions
import com.danielribeiro.scrcpystudio.session.RecordingStatus
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Path
import java.util.Locale
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

internal class RecordingOptionsDialog(
    project: Project,
    initialDirectory: Path,
    private val outputDirectoryProvider: () -> Path,
    private val onConfigure: () -> Unit,
) : DialogWrapper(project) {

    private val bitrateField = JBTextField(
        RecordingOptions.DEFAULT_BITRATE_MBPS.toString(),
    ).apply {
        columns = 4
    }
    private val resolutionBox = ComboBox<Int>().apply {
        RecordingOptions.SUPPORTED_RESOLUTION_PERCENTS.forEach(::addItem)
        selectedItem = RecordingOptions.DEFAULT_RESOLUTION_PERCENT
    }
    private val showTapsCheckBox = JBCheckBox(
        "Show taps",
        RecordingOptions.DEFAULT_SHOW_TAPS,
    )
    private val savingToLabel = JBLabel()
    private val configureButton = JButton("Configure")
    private var outputDirectory = initialDirectory

    var recordingOptions: RecordingOptions? = null
        private set

    init {
        title = "Screen Recorder Options"
        setOKButtonText("Start Recording")
        setCancelButtonText("Cancel")
        configureButton.apply {
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            foreground = JBColor.namedColor(
                "Link.activeForeground",
                JBColor(ColorFallback.LINK_LIGHT, ColorFallback.LINK_DARK),
            )
            margin = JBUI.emptyInsets()
            addActionListener {
                onConfigure()
                refreshOutputDirectory()
            }
        }
        refreshOutputDirectory()
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            comment("The length of the recording can be up to 30 minutes.")
        }
        row("Bit rate (Mbps):") {
            cell(bitrateField)
        }
        row("Resolution (% of native):") {
            cell(resolutionBox)
        }
        row {
            cell(showTapsCheckBox)
        }
        row("Saving to:") {
            cell(savingToLabel).resizableColumn()
            cell(configureButton)
        }
    }.apply {
        preferredSize = Dimension(JBUI.scale(350), JBUI.scale(150))
    }

    override fun getPreferredFocusedComponent(): JComponent = bitrateField

    override fun doOKAction() {
        val bitrate = bitrateField.text.trim().toIntOrNull()
        if (bitrate == null ||
            bitrate !in RecordingOptions.MIN_BITRATE_MBPS..RecordingOptions.MAX_BITRATE_MBPS
        ) {
            Messages.showErrorDialog(
                "Enter a bitrate from ${RecordingOptions.MIN_BITRATE_MBPS} to " +
                    "${RecordingOptions.MAX_BITRATE_MBPS} Mbps.",
                "Invalid Recording Bitrate",
            )
            return
        }

        recordingOptions = RecordingOptions(
            bitrateMbps = bitrate,
            resolutionPercent = (resolutionBox.selectedItem as? Int)
                ?: RecordingOptions.DEFAULT_RESOLUTION_PERCENT,
            showTaps = showTapsCheckBox.isSelected,
        )
        super.doOKAction()
    }

    override fun getHelpId(): String = "scrcpy.studio.recording.options"

    override fun doHelpAction() {
        Messages.showInfoMessage(
            "Recordings are saved as MP4 files in the selected output directory.",
            "Screen Recorder Options",
        )
    }

    fun refreshOutputDirectory() {
        outputDirectory = outputDirectoryProvider()
        savingToLabel.text = outputDirectory.toAbsolutePath().normalize().toString()
        savingToLabel.toolTipText = savingToLabel.text
    }

    fun selectedOutputDirectory(): Path = outputDirectory

    private object ColorFallback {
        val LINK_LIGHT = java.awt.Color(0x589DF6)
        val LINK_DARK = java.awt.Color(0x2B6CB0)
    }
}

internal class RecordingStatusDialog(
    project: Project,
    device: AndroidDevice,
    private val onStop: () -> Unit,
) : DialogWrapper(project, false) {

    private val elapsedLabel = JBLabel("Recording: 00:00")
    private val stopButton = JButton("Stop Recording")
    private val startedAtNanos = System.nanoTime()
    private var stopping = false
    private val timer = Timer(250) {
        updateElapsedTime()
    }

    init {
        title = "Record Screen - ${device.displayName}"
        setModal(false)
        setResizable(false)
        stopButton.isEnabled = false
        stopButton.addActionListener { requestStop() }
        init()
        timer.start()
    }

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
                    add(elapsedLabel)
                    add(stopButton)
                },
                BorderLayout.CENTER,
            )
        }

    override fun createSouthPanel(): JComponent? = null

    override fun createActions(): Array<Action> = emptyArray()

    override fun doCancelAction() {
        if (stopping) {
            super.doCancelAction()
        } else {
            requestStop()
        }
    }

    fun updateStatus(status: RecordingStatus) {
        when (status) {
            RecordingStatus.STARTING -> {
                stopButton.isEnabled = false
                elapsedLabel.text = "Starting recording..."
            }

            RecordingStatus.RECORDING -> {
                stopButton.isEnabled = true
                updateElapsedTime()
            }

            RecordingStatus.STOPPING -> {
                stopping = true
                stopButton.isEnabled = false
                elapsedLabel.text = "Stopping recording..."
            }

            RecordingStatus.IDLE,
            RecordingStatus.COMPLETED,
            RecordingStatus.FAILED,
            -> closeAfterStop()
        }
    }

    fun closeAfterStop() {
        timer.stop()
        if (isShowing) {
            close(CANCEL_EXIT_CODE)
        }
    }

    override fun dispose() {
        timer.stop()
        super.dispose()
    }

    private fun requestStop() {
        if (stopping) return
        stopping = true
        stopButton.isEnabled = false
        elapsedLabel.text = "Stopping recording..."
        onStop()
    }

    private fun updateElapsedTime() {
        if (stopping) return
        val elapsedSeconds = ((System.nanoTime() - startedAtNanos) / 1_000_000_000L)
            .coerceAtLeast(0)
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        elapsedLabel.text = String.format(
            Locale.ROOT,
            "Recording: %02d:%02d",
            minutes,
            seconds,
        )
    }
}
