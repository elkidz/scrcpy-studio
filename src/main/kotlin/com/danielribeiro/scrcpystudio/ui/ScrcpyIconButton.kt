package com.danielribeiro.scrcpystudio.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent

private val iconButtonSize = JBUI.size(24, 24)

class ScrcpyIconButton internal constructor(
    val component: JButton,
) {

    var isEnabled: Boolean
        get() = component.isEnabled
        set(value) {
            component.isEnabled = value
        }

    var isVisible: Boolean
        get() = component.isVisible
        set(value) {
            component.isVisible = value
        }

    fun update(
        icon: Icon,
        tooltip: String,
    ) {
        component.icon = icon
        component.toolTipText = tooltip
        component.setAccessibleName(tooltip)
    }
}

internal fun createScrcpyIconButton(
    icon: Icon,
    tooltip: String,
    action: () -> Unit,
): ScrcpyIconButton = ScrcpyIconButton(
    ScrcpyHoverIconButton(
        icon = icon,
        tooltip = tooltip,
        action = action,
    ),
)

internal fun updateScrcpyIconButton(
    button: ScrcpyIconButton,
    icon: Icon,
    tooltip: String,
) {
    button.update(icon, tooltip)
}

internal fun createScrcpyToolbarSeparator(): JComponent =
    object : JComponent() {
        init {
            isOpaque = false
            preferredSize = JBUI.size(9, 24)
            minimumSize = preferredSize
            maximumSize = preferredSize
        }

        override fun paintComponent(g: Graphics) {
            val graphics = g.create() as Graphics2D
            graphics.color = JBColor.namedColor("Separator.separatorColor", JBColor.GRAY)
            val x = width / 2
            val inset = JBUI.scale(4)
            graphics.fillRect(x, inset, JBUI.scale(1), height - inset * 2)
            graphics.dispose()
        }
    }

private class ScrcpyHoverIconButton(
    icon: Icon,
    tooltip: String,
    private val action: () -> Unit,
) : JButton(icon) {

    private var hovered = false
    private var pressed = false

    init {
        isFocusable = false
        isBorderPainted = false
        isContentAreaFilled = false
        margin = JBUI.emptyInsets()
        border = JBUI.Borders.empty(2)
        preferredSize = iconButtonSize
        minimumSize = iconButtonSize
        maximumSize = iconButtonSize
        toolTipText = tooltip
        addActionListener { action() }
        addMouseListener(
            object : MouseAdapter() {
                override fun mouseEntered(event: MouseEvent) {
                    hovered = true
                    repaint()
                }

                override fun mouseExited(event: MouseEvent) {
                    hovered = false
                    pressed = false
                    repaint()
                }

                override fun mousePressed(event: MouseEvent) {
                    if (isEnabled) {
                        pressed = true
                        repaint()
                    }
                }

                override fun mouseReleased(event: MouseEvent) {
                    pressed = false
                    repaint()
                }
            },
        )
        setAccessibleName(tooltip)
    }

    override fun paintComponent(graphics: Graphics) {
        if (isEnabled && (hovered || pressed)) {
            val background = graphics.create() as Graphics2D
            background.color = when {
                pressed -> JBColor.namedColor(
                    "ActionButton.pressedBackground",
                    JBColor(Color(0x4C4C4C), Color(0xD0D0D0)),
                )

                else -> JBColor.namedColor(
                    "ActionButton.hoverBackground",
                    JBColor(Color(0x3A3A3A), Color(0xE8E8E8)),
                )
            }
            val arc = JBUI.scale(4)
            background.fillRoundRect(0, 0, width, height, arc, arc)
            background.dispose()
        }
        super.paintComponent(graphics)
    }
}

private fun JButton.setAccessibleName(name: String) {
    getAccessibleContext().accessibleName = name
}
