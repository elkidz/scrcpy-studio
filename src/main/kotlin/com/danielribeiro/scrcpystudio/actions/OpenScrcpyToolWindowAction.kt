package com.danielribeiro.scrcpystudio.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.danielribeiro.scrcpystudio.ui.SCRCPY_TOOL_WINDOW_ID
import com.intellij.openapi.wm.ToolWindowManager

class OpenScrcpyToolWindowAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(SCRCPY_TOOL_WINDOW_ID) ?: return
        toolWindow.show {
            toolWindow.activate(null)
        }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }
}
