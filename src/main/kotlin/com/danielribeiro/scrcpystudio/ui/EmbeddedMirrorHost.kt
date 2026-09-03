package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.presentation.DeviceMirrorViewModel
import com.danielribeiro.scrcpystudio.session.MirrorMode
import com.danielribeiro.scrcpystudio.session.MirrorSessionState
import com.danielribeiro.scrcpystudio.session.MirrorStatus
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.roundToInt

class EmbeddedMirrorHost(
    private val viewModel: DeviceMirrorViewModel,
    private val device: AndroidDevice,
) : JPanel(), Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val latestFrame = AtomicReference<BufferedImage?>(null)
    private val repaintTimer = Timer(REPAINT_INTERVAL_MS) {
        val nextFrame = latestFrame.get()
        if (nextFrame !== displayedFrame) {
            displayedFrame = nextFrame
            repaint()
        }
    }
    private var displayedFrame: BufferedImage? = null
    private var currentState = MirrorSessionState(device, MirrorStatus.STOPPED)

    init {
        isOpaque = true
        background = Color.BLACK
        border = JBUI.Borders.empty(4)
        preferredSize = Dimension(420, 680)
        isFocusable = true

        scope.launch {
            viewModel.videoFrames
                .filter { it.serial == device.serial }
                .collect { latestFrame.set(it.image) }
        }
        repaintTimer.start()

        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                requestFocusInWindow()
                sendTouch(event, ACTION_DOWN, actionButton(event.button))
            }

            override fun mouseReleased(event: MouseEvent) {
                sendTouch(event, ACTION_UP, actionButton(event.button))
            }

            override fun mouseDragged(event: MouseEvent) {
                sendTouch(event, ACTION_MOVE)
            }
        }
        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
        addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(event: KeyEvent) {
                    if (event.keyCode == KeyEvent.VK_ESCAPE) {
                        viewModel.sendBack(device.serial)
                    }
                }
            },
        )
    }

    fun update(state: MirrorSessionState) {
        currentState = state
        if (state.mirrorStatus != MirrorStatus.RUNNING ||
            state.mirrorMode != MirrorMode.EMBEDDED
        ) {
            latestFrame.set(null)
            displayedFrame = null
        }
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.color = Color.BLACK
            graphics2D.fillRect(0, 0, width, height)

            if (currentState.mirrorMode == MirrorMode.EXTERNAL_FALLBACK &&
                currentState.mirrorStatus == MirrorStatus.RUNNING
            ) {
                drawCenteredMessage(graphics2D, "scrcpy is running in its external window.")
                return
            }

            if (currentState.mirrorStatus != MirrorStatus.RUNNING ||
                currentState.mirrorMode != MirrorMode.EMBEDDED
            ) {
                drawCenteredMessage(graphics2D, statusMessage())
                return
            }

            val image = displayedFrame
            if (image == null) {
                drawCenteredMessage(graphics2D, statusMessage())
                return
            }

            graphics2D.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            val bounds = imageBounds(image)
            graphics2D.drawImage(
                image,
                bounds.left,
                bounds.top,
                bounds.width,
                bounds.height,
                null,
            )
        } finally {
            graphics2D.dispose()
        }
    }

    override fun dispose() {
        repaintTimer.stop()
        scope.cancel()
        latestFrame.set(null)
        displayedFrame = null
    }

    private fun sendTouch(
        event: MouseEvent,
        action: Int,
        actionButton: Int = 0,
    ) {
        if (currentState.mirrorStatus != MirrorStatus.RUNNING ||
            currentState.mirrorMode != MirrorMode.EMBEDDED
        ) {
            return
        }
        val image = displayedFrame ?: return
        val point = devicePoint(event, image) ?: return
        viewModel.sendTouch(
            serial = device.serial,
            action = action,
            x = point.first,
            y = point.second,
            screenWidth = image.width,
            screenHeight = image.height,
            actionButton = actionButton,
            buttons = buttons(event),
        )
    }

    private fun imageBounds(image: BufferedImage): ImageBounds {
        val availableWidth = (width - insets.left - insets.right).coerceAtLeast(1)
        val availableHeight = (height - insets.top - insets.bottom).coerceAtLeast(1)
        val scale = minOf(
            availableWidth.toDouble() / image.width,
            availableHeight.toDouble() / image.height,
        )
        val drawWidth = (image.width * scale).roundToInt().coerceAtLeast(1)
        val drawHeight = (image.height * scale).roundToInt().coerceAtLeast(1)
        return ImageBounds(
            left = insets.left + (availableWidth - drawWidth) / 2,
            top = insets.top + (availableHeight - drawHeight) / 2,
            width = drawWidth,
            height = drawHeight,
        )
    }

    private fun devicePoint(
        event: MouseEvent,
        image: BufferedImage,
    ): Pair<Int, Int>? {
        val bounds = imageBounds(image)
        if (event.x !in bounds.left..(bounds.left + bounds.width) ||
            event.y !in bounds.top..(bounds.top + bounds.height)
        ) {
            return null
        }
        val x = ((event.x - bounds.left) * image.width.toDouble() / bounds.width)
            .roundToInt()
            .coerceIn(0, image.width - 1)
        val y = ((event.y - bounds.top) * image.height.toDouble() / bounds.height)
            .roundToInt()
            .coerceIn(0, image.height - 1)
        return x to y
    }

    private fun buttons(event: MouseEvent): Int {
        var result = 0
        if (event.modifiersEx and MouseEvent.BUTTON1_DOWN_MASK != 0) {
            result = result or BUTTON_PRIMARY
        }
        if (event.modifiersEx and MouseEvent.BUTTON2_DOWN_MASK != 0) {
            result = result or BUTTON_TERTIARY
        }
        if (event.modifiersEx and MouseEvent.BUTTON3_DOWN_MASK != 0) {
            result = result or BUTTON_SECONDARY
        }
        return result
    }

    private fun actionButton(button: Int): Int =
        when (button) {
            MouseEvent.BUTTON1 -> BUTTON_PRIMARY
            MouseEvent.BUTTON2 -> BUTTON_TERTIARY
            MouseEvent.BUTTON3 -> BUTTON_SECONDARY
            else -> 0
        }

    private fun drawCenteredMessage(graphics: Graphics2D, message: String) {
        graphics.color = JBColor.GRAY
        val metrics: FontMetrics = graphics.fontMetrics
        val x = (width - metrics.stringWidth(message)) / 2
        val y = (height - metrics.height) / 2 + metrics.ascent
        graphics.drawString(message, x, y)
    }

    private fun statusMessage(): String =
        when (currentState.mirrorStatus) {
            MirrorStatus.STARTING -> "Starting embedded scrcpy..."
            MirrorStatus.RUNNING -> "Waiting for video frames..."
            MirrorStatus.STOPPING -> "Stopping scrcpy..."
            MirrorStatus.STOPPED -> "Start mirroring to display the device."
            MirrorStatus.FAILED -> currentState.errorMessage ?: "scrcpy failed."
        }

    private data class ImageBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val REPAINT_INTERVAL_MS = 33
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val BUTTON_PRIMARY = 1
        const val BUTTON_SECONDARY = 2
        const val BUTTON_TERTIARY = 4
    }
}
