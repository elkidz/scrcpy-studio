package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorUiState
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorViewModel
import com.danielribeiro.scrcpystudio.session.MirrorSessionState
import com.danielribeiro.scrcpystudio.session.MirrorStatus
import com.danielribeiro.scrcpystudio.session.ScrcpySessionService
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsConfigurable
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.awt.FlowLayout
import javax.swing.JPanel

class ScrcpyToolWindowPanel(
    private val project: Project,
    toolWindow: ToolWindow,
) : Disposable {

    private val service = project.getService(ScrcpySessionService::class.java)
    private val viewModel = DeviceMirrorViewModel(service)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val contentManager = toolWindow.contentManager
    private val sessionPanels = linkedMapOf<String, MirrorSessionPanel>()
    private val sessionContents = linkedMapOf<String, Content>()
    private val emptyDevicesLabel = JBLabel("Connect an Android device to get started.")
    private var emptyContent: Content? = null
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
        })
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
    }

    private fun updateDeviceTabs(state: DeviceMirrorUiState) {
        val devices = state.devices.sortedBy { it.displayName }
        val connectedSerials = devices.mapTo(mutableSetOf(), AndroidDevice::serial)
        updatingContents = true
        try {
            val removedSerials = sessionContents.keys.filterNot(connectedSerials::contains)
            removedSerials.forEach { serial ->
                sessionContents.remove(serial)?.let { content ->
                    contentManager.removeContent(content, true)
                }
                sessionPanels.remove(serial)
            }

            if (devices.isEmpty()) {
                showEmptyContent()
                return
            }

            hideEmptyContent()
            if (!autoStartedOnOpen) {
                autoStartedOnOpen = true
                viewModel.startMirrorsForConnectedDevices()
            }
            devices.forEachIndexed { index, device ->
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
                val currentIndex = contentManager.getIndexOfContent(content)
                if (currentIndex != index) {
                    contentManager.removeContent(content, false)
                    contentManager.addContent(content, index)
                }
            }

            val selectedSerial = viewModel.selectedSerial.value
                ?.takeIf(connectedSerials::contains)
                ?: (contentManager.selectedContent?.component as? MirrorSessionPanel)?.serial
                ?: devices.first().serial
            viewModel.selectDevice(selectedSerial)
            selectSessionTab(selectedSerial)
        } finally {
            updatingContents = false
        }
    }

    private fun createDeviceContent(device: AndroidDevice): Content {
        val panel = MirrorSessionPanel(project, viewModel, device)
        sessionPanels[device.serial] = panel
        val content = ContentFactory.getInstance().createContent(panel, device.displayName, false)
        content.isCloseable = false
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

    private fun showEmptyContent() {
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
}
