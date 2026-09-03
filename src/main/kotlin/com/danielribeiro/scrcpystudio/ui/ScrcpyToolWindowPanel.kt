package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorUiState
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorViewModel
import com.danielribeiro.scrcpystudio.session.MirrorSessionState
import com.danielribeiro.scrcpystudio.session.MirrorStatus
import com.danielribeiro.scrcpystudio.session.ScrcpySessionService
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTabbedPane

class ScrcpyToolWindowPanel(
    private val project: Project,
    @Suppress("UNUSED_PARAMETER") private val toolWindow: ToolWindow,
) : SimpleToolWindowPanel(true, true), Disposable {

    private val service = project.getService(ScrcpySessionService::class.java)
    private val viewModel = DeviceMirrorViewModel(service)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessionTabs = JTabbedPane()
    private val sessionPanels = linkedMapOf<String, MirrorSessionPanel>()
    private val emptyDevicesLabel = JBLabel("Connect an Android device to get started.")
    private val sessionCards = JPanel(CardLayout())
    private val statusLabel = JBLabel("Looking for Android devices...")
    private val refreshButton = JButton("Refresh")
    private var disposed = false

    init {
        sessionTabs.tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
        sessionTabs.addChangeListener {
            if (!disposed) {
                (sessionTabs.selectedComponent as? MirrorSessionPanel)
                    ?.let { panel -> viewModel.selectDevice(panel.serial) }
            }
        }

        refreshButton.addActionListener { viewModel.refreshDevices() }

        val settingsButton = JButton("Settings").apply {
            addActionListener {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, ScrcpySettingsConfigurable::class.java)
            }
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
            border = JBUI.Borders.empty(2)
            add(refreshButton)
            add(settingsButton)
            add(statusLabel)
        }

        emptyDevicesLabel.horizontalAlignment = JBLabel.CENTER
        emptyDevicesLabel.foreground = JBColor.GRAY
        sessionCards.add(emptyDevicesLabel, EMPTY_CARD)
        sessionCards.add(sessionTabs, TABS_CARD)

        setContent(
            JPanel(BorderLayout()).apply {
                add(toolbar, BorderLayout.NORTH)
                add(sessionCards, BorderLayout.CENTER)
            },
        )

        observeState()
        viewModel.startMonitoring()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        viewModel.dispose()
        uiScope.cancel()
        sessionPanels.values.forEach(MirrorSessionPanel::dispose)
        sessionPanels.clear()
    }

    private fun observeState() {
        uiScope.launch {
            viewModel.uiState.collect { state ->
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed) {
                        render(state)
                    }
                }
            }
        }
    }

    private fun render(state: DeviceMirrorUiState) {
        updateDeviceTabs(state)
        statusLabel.text = state.lastError ?: when (state.devices.size) {
            0 -> "No devices found"
            1 -> "1 device connected"
            else -> "${state.devices.size} devices connected"
        }
    }

    private fun updateDeviceTabs(state: DeviceMirrorUiState) {
        val devices = state.devices.sortedBy { it.displayName }
        val connectedSerials = devices.mapTo(mutableSetOf(), AndroidDevice::serial)
        val removedSerials = sessionPanels.keys.filterNot(connectedSerials::contains)
        removedSerials.forEach { serial ->
            sessionPanels.remove(serial)?.let {
                sessionTabs.remove(it)
                it.dispose()
            }
        }

        devices.forEach { device ->
            val panel = sessionPanels.getOrPut(device.serial) {
                val newPanel = MirrorSessionPanel(viewModel, device)
                sessionTabs.addTab(device.displayName, newPanel)
                newPanel
            }
            panel.updateDevice(device)
            panel.update(
                state.sessions[device.serial]
                    ?.copy(device = device)
                    ?: MirrorSessionState(
                        device = device,
                        mirrorStatus = MirrorStatus.STOPPED,
                    ),
            )
            val tabIndex = sessionTabs.indexOfComponent(panel)
            if (tabIndex >= 0) {
                sessionTabs.setTitleAt(tabIndex, device.displayName)
                sessionTabs.setToolTipTextAt(tabIndex, device.serial)
            }
        }

        val card = sessionCards.layout as CardLayout
        if (devices.isEmpty()) {
            card.show(sessionCards, EMPTY_CARD)
        } else {
            card.show(sessionCards, TABS_CARD)
            val selectedSerial = viewModel.selectedSerial.value
                ?.takeIf(connectedSerials::contains)
                ?: (sessionTabs.selectedComponent as? MirrorSessionPanel)?.serial
                ?: devices.first().serial
            viewModel.selectDevice(selectedSerial)
            selectSessionTab(selectedSerial)
        }
    }

    private fun selectSessionTab(serial: String?) {
        val panel = serial?.let(sessionPanels::get) ?: return
        val index = sessionTabs.indexOfComponent(panel)
        if (index >= 0 && sessionTabs.selectedIndex != index) {
            sessionTabs.selectedIndex = index
        }
    }

    private companion object {
        const val EMPTY_CARD = "empty"
        const val TABS_CARD = "tabs"
    }
}
