package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.session.ScrcpySessionService
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ScrcpyToolWindowController(
    private val project: Project,
) : Disposable {

    private val service = project.getService(ScrcpySessionService::class.java)
    private val settings = ScrcpySettingsState.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        observeMirrorState()
        observeDeviceConnections()
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun observeMirrorState() {
        scope.launch {
            combine(service.devices, service.sessions, ::hasRunningMirror)
                .distinctUntilChanged()
                .collect(::updateToolWindowIcon)
        }
    }

    private fun observeDeviceConnections() {
        scope.launch {
            service.deviceConnectionEvents.collect { diff ->
                if (settings.getState().autoOpenToolWindowOnDeviceConnect &&
                    diff.connected.any { it.canMirror }
                ) {
                    showToolWindow()
                }
            }
        }
    }

    private fun updateToolWindowIcon(mirroring: Boolean) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ToolWindowManager.getInstance(project)
                .getToolWindow(SCRCPY_TOOL_WINDOW_ID)
                ?.setIcon(scrcpyStudioToolWindowIcon(mirroring))
        }
    }

    private fun showToolWindow() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(SCRCPY_TOOL_WINDOW_ID)
                ?: return@invokeLater
            toolWindow.show {
                toolWindow.activate(null)
            }
        }
    }
}
