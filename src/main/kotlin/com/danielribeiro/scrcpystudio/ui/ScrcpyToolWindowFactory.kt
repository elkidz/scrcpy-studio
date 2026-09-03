package com.danielribeiro.scrcpystudio.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ScrcpyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ScrcpyToolWindowPanel(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
        content.setPreferredFocusableComponent(panel)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
