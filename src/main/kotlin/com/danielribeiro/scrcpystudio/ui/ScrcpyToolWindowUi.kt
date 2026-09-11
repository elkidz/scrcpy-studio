package com.danielribeiro.scrcpystudio.ui

import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi

internal const val SCRCPY_TOOL_WINDOW_ID = "Scrcpy Studio"

internal fun configureScrcpyToolWindowChrome(toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
}
