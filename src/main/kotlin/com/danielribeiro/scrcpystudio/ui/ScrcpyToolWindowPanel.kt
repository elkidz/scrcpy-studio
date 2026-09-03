package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorUiState
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorViewModel
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
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.ListCellRenderer

class ScrcpyToolWindowPanel(
    project: Project,
    @Suppress("UNUSED_PARAMETER") private val toolWindow: ToolWindow,
) : SimpleToolWindowPanel(true, true), Disposable {

    private val service = project.getService(ScrcpySessionService::class.java)
    private val viewModel = DeviceMirrorViewModel(service)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val deviceListModel = DefaultListModel<AndroidDevice>()
    private val deviceList = JBList(deviceListModel)
    private val sessionTabs = JTabbedPane()
    private val sessionPanels = linkedMapOf<String, MirrorSessionPanel>()
    private val emptySessionsLabel = JBLabel("Select a device and start mirroring.")
    private val sessionCards = JPanel(CardLayout())
    private val statusLabel = JBLabel("Looking for Android devices...")
    private val startSelectedButton = JButton("Start mirroring")
    private val refreshButton = JButton("Refresh")
    private var latestState = DeviceMirrorUiState()
    private var disposed = false

    init {
        deviceList.selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
        deviceList.cellRenderer = DeviceCellRenderer()
        deviceList.preferredSize = Dimension(250, 100)
        deviceList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                viewModel.selectDevice(deviceList.selectedValue?.serial)
                updateStartButton()
                selectSessionTab(deviceList.selectedValue?.serial)
            }
        }

        refreshButton.addActionListener { viewModel.refreshDevices() }
        startSelectedButton.addActionListener {
            deviceList.selectedValue?.let { viewModel.startMirror(it.serial) }
        }

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

        val devicesPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            add(JBLabel("Connected devices"), BorderLayout.NORTH)
            add(JBScrollPane(deviceList), BorderLayout.CENTER)
            add(
                JPanel(GridLayout(1, 1)).apply {
                    border = JBUI.Borders.emptyTop(4)
                    add(startSelectedButton)
                },
                BorderLayout.SOUTH,
            )
        }

        emptySessionsLabel.horizontalAlignment = JBLabel.CENTER
        emptySessionsLabel.foreground = JBColor.GRAY
        sessionCards.add(emptySessionsLabel, EMPTY_CARD)
        sessionCards.add(sessionTabs, TABS_CARD)

        val splitPane = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            devicesPanel,
            sessionCards,
        ).apply {
            resizeWeight = 0.28
            dividerLocation = 260
        }

        setContent(
            JPanel(BorderLayout()).apply {
                add(toolbar, BorderLayout.NORTH)
                add(splitPane, BorderLayout.CENTER)
            },
        )

        observeState()
        viewModel.startMonitoring()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        viewModel.stopMonitoring()
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
        latestState = state
        updateDevices(state.devices)
        updateSessions(state)
        statusLabel.text = state.lastError ?: when (state.devices.size) {
            0 -> "No devices found"
            1 -> "1 device connected"
            else -> "${state.devices.size} devices connected"
        }
        updateStartButton()
    }

    private fun updateDevices(devices: List<AndroidDevice>) {
        val selectedSerial = viewModel.selectedSerial.value
            ?: deviceList.selectedValue?.serial
        deviceListModel.removeAllElements()
        devices.forEach(deviceListModel::addElement)

        val selectedIndex = devices.indexOfFirst { it.serial == selectedSerial }
        if (selectedIndex >= 0) {
            deviceList.selectedIndex = selectedIndex
        } else if (devices.isNotEmpty() && deviceList.selectedIndex < 0) {
            deviceList.selectedIndex = 0
            viewModel.selectDevice(devices.first().serial)
        }
    }

    private fun updateSessions(state: DeviceMirrorUiState) {
        val activeSerials = state.sessions.keys
        val removedSerials = sessionPanels.keys.filterNot(activeSerials::contains)
        removedSerials.forEach { serial ->
            sessionPanels.remove(serial)?.let {
                sessionTabs.remove(it)
                it.dispose()
            }
        }

        state.sessions.values
            .sortedBy { it.device.displayName }
            .forEach { session ->
                val panel = sessionPanels.getOrPut(session.device.serial) {
                    val newPanel = MirrorSessionPanel(viewModel, session.device)
                    sessionTabs.addTab(session.device.displayName, newPanel)
                    newPanel
                }
                panel.update(session)
                val tabIndex = sessionTabs.indexOfComponent(panel)
                if (tabIndex >= 0) {
                    sessionTabs.setTitleAt(tabIndex, session.device.displayName)
                }
            }

        val card = sessionCards.layout as CardLayout
        if (sessionPanels.isEmpty()) {
            card.show(sessionCards, EMPTY_CARD)
        } else {
            card.show(sessionCards, TABS_CARD)
            selectSessionTab(viewModel.selectedSerial.value)
        }
    }

    private fun selectSessionTab(serial: String?) {
        val panel = serial?.let(sessionPanels::get) ?: return
        val index = sessionTabs.indexOfComponent(panel)
        if (index >= 0 && sessionTabs.selectedIndex != index) {
            sessionTabs.selectedIndex = index
        }
    }

    private fun updateStartButton() {
        val device = deviceList.selectedValue
        val session = device?.serial?.let(latestState.sessions::get)
        startSelectedButton.isEnabled = device?.canMirror == true &&
            session?.mirrorStatus !in setOf(
            MirrorStatus.STARTING,
            MirrorStatus.RUNNING,
            MirrorStatus.STOPPING,
        )
    }

    private class DeviceCellRenderer : JPanel(BorderLayout()), ListCellRenderer<AndroidDevice> {
        private val nameLabel = JBLabel()
        private val detailsLabel = JBLabel()

        init {
            border = JBUI.Borders.empty(5, 6)
            isOpaque = true
            add(nameLabel, BorderLayout.NORTH)
            add(detailsLabel, BorderLayout.SOUTH)
        }

        override fun getListCellRendererComponent(
            list: JList<out AndroidDevice>,
            value: AndroidDevice,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            nameLabel.text = value.displayName
            detailsLabel.text = "${value.serial} · ${value.rawState}"
            background = if (isSelected) list.selectionBackground else list.background
            nameLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
            detailsLabel.foreground = if (isSelected) {
                list.selectionForeground
            } else {
                JBColor.GRAY
            }
            return this
        }
    }

    private companion object {
        const val EMPTY_CARD = "empty"
        const val TABS_CARD = "tabs"
    }
}
