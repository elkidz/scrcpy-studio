package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.session.MirrorSessionState
import com.danielribeiro.scrcpystudio.session.MirrorStatus
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon
import kotlin.math.roundToInt

internal const val SCRCPY_TOOL_WINDOW_ID = "Scrcpy Studio"

internal fun configureScrcpyToolWindowChrome(toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
    toolWindow.setToHideOnEmptyContent(false)
}

internal fun hasRunningMirror(
    devices: List<AndroidDevice>,
    sessions: Map<String, MirrorSessionState>,
): Boolean = devices.any { device ->
    device.canMirror && sessions[device.serial]?.mirrorStatus == MirrorStatus.RUNNING
}

internal fun scrcpyStudioToolWindowIcon(mirroring: Boolean): Icon =
    if (mirroring) {
        ScrcpyStudioStatusIcon()
    } else {
        ScrcpyIcons.ScrcpyStudio
    }

private class ScrcpyStudioStatusIcon(
    private val scaleFactor: Float = 1f,
) : ScalableIcon {
    private val base = ScrcpyIcons.ScrcpyStudio

    override fun getScale(): Float = scaleFactor

    override fun scale(scaleFactor: Float): Icon =
        if (this.scaleFactor == scaleFactor) this else ScrcpyStudioStatusIcon(scaleFactor)

    override fun getIconWidth(): Int = (base.iconWidth * scaleFactor).roundToInt()

    override fun getIconHeight(): Int = (base.iconHeight * scaleFactor).roundToInt()

    override fun paintIcon(component: java.awt.Component?, graphics: Graphics, x: Int, y: Int) {
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.translate(x, y)
            graphics2D.scale(scaleFactor.toDouble(), scaleFactor.toDouble())
            base.paintIcon(component, graphics2D, 0, 0)
            val dotSize = if (base.iconWidth >= 20) 6 else 5
            val dotX = base.iconWidth - dotSize
            val dotY = base.iconHeight - dotSize
            graphics2D.color = JBColor(Color(0x3CCB70), Color(0x3CCB70))
            graphics2D.fillOval(dotX, dotY, dotSize, dotSize)
            graphics2D.color = JBColor(Color.WHITE, Color(0x202124))
            graphics2D.drawOval(dotX, dotY, dotSize - 1, dotSize - 1)
        } finally {
            graphics2D.dispose()
        }
    }
}
