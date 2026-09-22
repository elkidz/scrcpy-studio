package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorUiState
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorViewModel
import com.danielribeiro.scrcpystudio.session.MirrorSessionState
import com.danielribeiro.scrcpystudio.session.MirrorStatus
import com.danielribeiro.scrcpystudio.session.ScrcpySessionService
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsConfigurable
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.awt.FlowLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

class ScrcpyToolWindowPanel(
    private val project: Project,
    private val toolWindow: ToolWindow,
) : Disposable {

    private val service = project.getService(ScrcpySessionService::class.java)
    private val viewModel = DeviceMirrorViewModel(service)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val contentManager = toolWindow.contentManager
    private val sessionPanels = linkedMapOf<String, MirrorSessionPanel>()
    private val sessionContents = linkedMapOf<String, Content>()
    private val emptyDevicesLabel = JBLabel("Connect an Android device to get started.")
    private var emptyContent: Content? = null
    private val closingSerials = mutableSetOf<String>()
    private var latestState = DeviceMirrorUiState()
    private var updatingContents = false
    private var autoStartedOnOpen = false
    private var disposed = false

    init {
        Disposer.register(contentManager, this)
        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (disposed || updatingContents) return
                val panel = event.content.component as? MirrorSessionPanel ?: return
                viewModel.selectDevice(panel.serial)
            }

            override fun contentRemoved(event: ContentManagerEvent) {
                if (disposed || updatingContents) return
                val panel = event.content.component as? MirrorSessionPanel ?: return
                sessionContents.remove(panel.serial)
                sessionPanels.remove(panel.serial)
                closingSerials.add(panel.serial)
                viewModel.stopMirror(panel.serial)
            }
        })
        toolWindow.setTitleActions(listOf(createAddDeviceAction()))
        showEmptyContent()
        observeState()
        viewModel.startMonitoring()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        viewModel.dispose()
        uiScope.cancel()
        sessionPanels.clear()
        sessionContents.clear()
        emptyContent = null
        closingSerials.clear()
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
        closingSerials.removeAll { serial ->
            state.devices.none { it.serial == serial } ||
                state.sessions[serial]?.mirrorStatus in setOf(
                    MirrorStatus.STOPPED,
                    MirrorStatus.FAILED,
                )
        }
        updateDeviceTabs(state)
    }

    private fun updateDeviceTabs(state: DeviceMirrorUiState) {
        val devices = state.devices.sortedBy { it.displayName }
        val mirroredDevices = devices.filter { device ->
            device.serial !in closingSerials &&
                state.sessions[device.serial]?.mirrorStatus in MIRRORING_STATUSES
        }
        val mirroredSerials = mirroredDevices.mapTo(mutableSetOf(), AndroidDevice::serial)
        updatingContents = true
        try {
            val removedSerials = sessionContents.keys.filterNot(mirroredSerials::contains)
            if (!autoStartedOnOpen) {
                autoStartedOnOpen = true
                viewModel.startMirrorsForConnectedDevices()
            }
            if (mirroredDevices.isEmpty()) {
                viewModel.selectDevice(null)
                showEmptyContent(devices)
            } else {
                mirroredDevices.forEach { device ->
                    val content = sessionContents.getOrPut(device.serial) {
                        createDeviceContent(device)
                    }
                    val panel = sessionPanels.getValue(device.serial)
                    panel.updateDevice(device)
                    panel.update(
                        state.sessions[device.serial]
                            ?.copy(device = device)
                            ?: MirrorSessionState(
                                device = device,
                                mirrorStatus = MirrorStatus.STOPPED,
                            ),
                    )
                    content.displayName = device.displayName
                    content.tabName = device.displayName
                    content.description = device.serial
                }
                hideEmptyContent()
            }

            removedSerials.forEach { serial ->
                sessionContents.remove(serial)?.let { content ->
                    contentManager.removeContent(content, true)
                }
                sessionPanels.remove(serial)
            }

            if (mirroredDevices.isEmpty()) {
                return
            }

            mirroredDevices.forEachIndexed { index, device ->
                val content = sessionContents.getValue(device.serial)
                val currentIndex = contentManager.getIndexOfContent(content)
                if (currentIndex != index) {
                    contentManager.removeContent(content, false)
                    contentManager.addContent(content, index)
                }
            }
            val selectedSerial = viewModel.selectedSerial.value
                ?.takeIf(mirroredSerials::contains)
                ?: (contentManager.selectedContent?.component as? MirrorSessionPanel)?.serial
                ?: mirroredDevices.first().serial
            viewModel.selectDevice(selectedSerial)
            selectSessionTab(selectedSerial)
        } finally {
            updatingContents = false
        }
    }

    private fun createDeviceContent(device: AndroidDevice): Content {
        val panel = MirrorSessionPanel(project, viewModel, device, toolWindow)
        sessionPanels[device.serial] = panel
        val content = ContentFactory.getInstance().createContent(panel, device.displayName, false)
        content.isCloseable = true
        content.description = device.serial
        content.setPreferredFocusableComponent(panel)
        content.setDisposer(panel)
        contentManager.addContent(content)
        return content
    }

    private fun selectSessionTab(serial: String?) {
        val content = serial?.let(sessionContents::get) ?: return
        if (contentManager.selectedContent != content) {
            contentManager.setSelectedContent(content, true)
        }
    }

    private fun showEmptyContent(devices: List<AndroidDevice> = emptyList()) {
        emptyDevicesLabel.text = if (devices.isEmpty()) {
            "Connect an Android device to get started."
        } else {
            "Select + to start mirroring a connected device."
        }
        if (emptyContent != null) return
        val emptySettingsButton = createScrcpyIconButton(
            icon = AllIcons.General.GearPlain,
            tooltip = "Open Scrcpy Studio settings",
        ) {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, ScrcpySettingsConfigurable::class.java)
        }
        emptyDevicesLabel.horizontalAlignment = JBLabel.CENTER
        emptyDevicesLabel.foreground = JBColor.GRAY
        val emptyState = JPanel(FlowLayout(FlowLayout.CENTER, 6, 6)).apply {
            border = JBUI.Borders.empty(8)
            add(emptyDevicesLabel)
            add(emptySettingsButton.component)
        }
        val content = ContentFactory.getInstance().createContent(emptyState, "", false)
        content.isCloseable = false
        contentManager.addContent(content)
        contentManager.setSelectedContent(content, true)
        emptyContent = content
    }

    private fun hideEmptyContent() {
        emptyContent?.let { contentManager.removeContent(it, true) }
        emptyContent = null
    }

    private fun createAddDeviceAction(): AnAction =
        object : AnAction(
            "Add device mirror",
            "Start mirroring a connected device",
            AllIcons.General.Add,
        ) {
            override fun actionPerformed(event: AnActionEvent) {
                showDevicePicker(event.inputEvent?.component as? JComponent)
            }

            override fun update(event: AnActionEvent) {
                event.presentation.isEnabled = true
            }
        }

    private fun showDevicePicker(anchor: JComponent?) {
        val devices = latestState.devices
            .filter { device ->
                device.canMirror &&
                    latestState.sessions[device.serial]?.mirrorStatus !in MIRRORING_STATUSES
            }
            .sortedBy(AndroidDevice::displayName)
        if (devices.isEmpty()) return

        val list = JBList(devices).apply {
            selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
            selectedIndex = 0
            cellRenderer = object : DefaultListCellRenderer() {
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
                    val device = value as? AndroidDevice
                    text = device?.let { "${it.displayName} (${it.serial})" }.orEmpty()
                    return component
                }
            }
        }
        val popup = JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setTitle("Start mirroring")
            .setItemChoosenCallback {
                list.selectedValue?.let { viewModel.startMirror(it.serial) }
            }
            .createPopup()
        if (anchor != null) {
            popup.showUnderneathOf(anchor)
        } else {
            popup.showInFocusCenter()
        }
    }

    private companion object {
        val MIRRORING_STATUSES = setOf(
            MirrorStatus.STARTING,
            MirrorStatus.RUNNING,
            MirrorStatus.STOPPING,
        )
    }
}
