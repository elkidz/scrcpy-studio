package com.danielribeiro.scrcpystudio.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import javax.swing.JLabel
import javax.swing.JPanel
import java.awt.BorderLayout

class ScrcpyToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        configureScrcpyToolWindowChrome(toolWindow)
        val contentManager = toolWindow.contentManager
        if (contentManager.contentCount > 0) {
            return
        }

        try {
            ScrcpyToolWindowPanel(project, toolWindow)
        } catch (error: Throwable) {
            LOG.error("Failed to create Scrcpy Studio tool window content", error)
            val errorPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8)
                add(
                    JLabel(
                        "Scrcpy Studio failed to load: ${error.message ?: error::class.simpleName}",
                    ),
                    BorderLayout.CENTER,
                )
            }
            val content = ContentFactory.getInstance().createContent(errorPanel, "", false)
            content.isCloseable = false
            contentManager.addContent(content)
            contentManager.setSelectedContent(content, true)
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    private companion object {
        private val LOG = Logger.getInstance(ScrcpyToolWindowFactory::class.java)
    }
}
