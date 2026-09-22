package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorViewModel
import com.danielribeiro.scrcpystudio.settings.PerformanceProfile
import com.danielribeiro.scrcpystudio.settings.RenderBackend
import com.danielribeiro.scrcpystudio.settings.ScrcpyMirrorOptions
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import com.danielribeiro.scrcpystudio.settings.VideoCodec
import com.danielribeiro.scrcpystudio.settings.writeMirrorOptions
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JList

internal object ScrcpyOptionsPopup {

    fun show(anchor: JComponent, viewModel: DeviceMirrorViewModel) {
        val settings = ScrcpySettingsState.getInstance()
        val original = settings.getState()
        val current = ScrcpyMirrorOptions.from(original)

        lateinit var autoMirror: JCheckBox
        lateinit var autoOpenToolWindow: JCheckBox
        lateinit var autoReconnect: JCheckBox
        lateinit var alwaysOnTop: JCheckBox
        lateinit var showTouches: JCheckBox
        lateinit var stayAwake: JCheckBox
        lateinit var hardwareAcceleration: JCheckBox
        lateinit var lowLatency: JCheckBox
        lateinit var powerSaving: JCheckBox
        lateinit var keyboardInput: JCheckBox
        lateinit var clipboardSync: JCheckBox
        val profileBox = labeledCombo(
            items = PerformanceProfile.entries,
            label = { it.label },
        )
        val maxSizeBox = labeledCombo(MAX_SIZE_CHOICES) { it.label }
        val maxFpsBox = labeledCombo(MAX_FPS_CHOICES) { it.label }
        val bitRateBox = labeledCombo(BIT_RATE_CHOICES) { it.label }
        val codecBox = labeledCombo(VideoCodec.entries) { it.label }
        val renderBox = labeledCombo(RenderBackend.entries) { it.label }
        var applyingProfile = false

        fun selectedOptions(): ScrcpyMirrorOptions =
            ScrcpyMirrorOptions(
                alwaysOnTopWhenExternal = alwaysOnTop.isSelected,
                maxSize = maxSizeBox.item.value,
                maxFps = maxFpsBox.item.value,
                showTouches = showTouches.isSelected,
                stayAwake = stayAwake.isSelected,
                videoBitRate = bitRateBox.item.value,
                videoCodec = codecBox.item,
                hardwareAcceleration = hardwareAcceleration.isSelected,
                renderBackend = renderBox.item,
                lowLatency = lowLatency.isSelected,
                powerSaving = powerSaving.isSelected,
                keyboardInput = keyboardInput.isSelected,
                clipboardSync = clipboardSync.isSelected,
                performanceProfile = profileBox.item,
            )

        fun applyProfile(profile: PerformanceProfile) {
            val preset = ScrcpyMirrorOptions.forProfile(profile)
            applyingProfile = true
            try {
                maxSizeBox.item = MAX_SIZE_CHOICES.first { it.value == preset.maxSize }
                maxFpsBox.item = MAX_FPS_CHOICES.first { it.value == preset.maxFps }
                bitRateBox.item = BIT_RATE_CHOICES.first { it.value == preset.videoBitRate }
                lowLatency.isSelected = preset.lowLatency
                powerSaving.isSelected = preset.powerSaving
            } finally {
                applyingProfile = false
            }
        }

        val content = panel {
            group("Automation") {
                row {
                    autoMirror = checkBox(
                        "Automatically start mirroring when a device is connected",
                    ).component
                }
                row {
                    autoOpenToolWindow = checkBox(
                        "Automatically open Scrcpy Studio when a device connects",
                    ).component
                }
                row {
                    autoReconnect = checkBox(
                        "Reconnect and resume mirroring when a device returns",
                    ).component
                }
            }
            group("Display") {
                row("Performance profile:") { cell(profileBox) }
                row("Max resolution:") { cell(maxSizeBox) }
                row("Max FPS:") { cell(maxFpsBox) }
                row("Video bitrate:") { cell(bitRateBox) }
                row("Video codec:") { cell(codecBox) }
                row {
                    comment("H.265 and AV1 apply to the external window. Embedded mirroring stays on H.264.")
                }
                row("Render backend:") { cell(renderBox) }
                row {
                    comment("Used by the external scrcpy window.")
                }
                row { alwaysOnTop = checkBox("Always on top when external").component }
                row { showTouches = checkBox("Show device touches").component }
                row { stayAwake = checkBox("Keep screen awake").component }
                row { hardwareAcceleration = checkBox("Hardware acceleration").component }
                row { lowLatency = checkBox("Low-latency mode").component }
                row { powerSaving = checkBox("Power-saving mode").component }
            }
            group("Input") {
                row { keyboardInput = checkBox("Keyboard input").component }
                row { clipboardSync = checkBox("Copy and paste").component }
            }
            row {
                button("Apply") {
                    settings.getState().apply {
                        autoMirrorOnDeviceConnect = autoMirror.isSelected
                        autoOpenToolWindowOnDeviceConnect = autoOpenToolWindow.isSelected
                        this.autoReconnect = autoReconnect.isSelected
                        writeMirrorOptions(selectedOptions())
                    }
                    viewModel.restartRunningMirrors()
                    if (autoMirror.isSelected) {
                        viewModel.startMirrorsForConnectedDevices()
                    }
                }
            }
        }

        autoMirror.isSelected = original.autoMirrorOnDeviceConnect
        autoOpenToolWindow.isSelected = original.autoOpenToolWindowOnDeviceConnect
        autoReconnect.isSelected = original.autoReconnect
        alwaysOnTop.isSelected = current.alwaysOnTopWhenExternal
        showTouches.isSelected = current.showTouches
        stayAwake.isSelected = current.stayAwake
        hardwareAcceleration.isSelected = current.hardwareAcceleration
        lowLatency.isSelected = current.lowLatency
        powerSaving.isSelected = current.powerSaving
        keyboardInput.isSelected = current.keyboardInput
        clipboardSync.isSelected = current.clipboardSync
        profileBox.item = current.performanceProfile
        maxSizeBox.item = MAX_SIZE_CHOICES.firstOrNull { it.value == current.maxSize } ?: MAX_SIZE_CHOICES[1]
        maxFpsBox.item = MAX_FPS_CHOICES.firstOrNull { it.value == current.maxFps } ?: MAX_FPS_CHOICES[1]
        bitRateBox.item = BIT_RATE_CHOICES.firstOrNull { it.value == current.videoBitRate } ?: BIT_RATE_CHOICES[2]
        codecBox.item = current.videoCodec
        renderBox.item = current.renderBackend
        profileBox.addActionListener {
            if (!applyingProfile) {
                applyProfile(profileBox.item)
            }
        }

        val scroll = JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(JBUI.scale(420), JBUI.scale(480))
        }
        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scroll, content)
            .setTitle("Scrcpy options")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()
            .showUnderneathOf(anchor)
    }

    private data class IntChoice(val value: Int, val label: String)

    private val MAX_SIZE_CHOICES = listOf(
        IntChoice(0, "Original"),
        IntChoice(1920, "1920"),
        IntChoice(1280, "1280"),
        IntChoice(1024, "1024"),
        IntChoice(800, "800"),
    )
    private val MAX_FPS_CHOICES = listOf(
        IntChoice(0, "Unlimited"),
        IntChoice(15, "15"),
        IntChoice(30, "30"),
        IntChoice(60, "60"),
        IntChoice(120, "120"),
    )
    private val BIT_RATE_CHOICES = listOf(
        IntChoice(2_000_000, "2 Mbps"),
        IntChoice(4_000_000, "4 Mbps"),
        IntChoice(8_000_000, "8 Mbps"),
        IntChoice(16_000_000, "16 Mbps"),
    )

    private fun <T> labeledCombo(items: Collection<T>, label: (T) -> String): ComboBox<T> {
        val combo = ComboBox<T>()
        items.forEach(combo::addItem)
        combo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val component = super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus,
                )
                @Suppress("UNCHECKED_CAST")
                text = (value as? T)?.let(label).orEmpty()
                return component
            }
        }
        return combo
    }
}
