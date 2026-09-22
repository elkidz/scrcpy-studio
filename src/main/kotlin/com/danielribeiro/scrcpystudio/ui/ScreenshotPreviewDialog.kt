package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Image
import java.awt.RenderingHints
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants

internal class ScreenshotPreviewDialog(
    project: Project,
    device: AndroidDevice,
    initialDirectory: Path,
    private val outputDirectoryProvider: () -> Path,
    private val onConfigure: () -> Unit,
    private val onRecapture: () -> Unit,
    private val onSave: (resolutionPercent: Int) -> Unit,
    private val onCancel: () -> Unit,
) : DialogWrapper(project, false) {

    private val recaptureButton = JButton("Recapture")
    private val copyButton = JButton("Copy to Clipboard")
    private val resolutionBox = ComboBox(RESOLUTION_PERCENTS.toTypedArray())
    private val savingToLabel = JBLabel()
    private val configureButton = JButton("Configure")
    private val metadataLabel = JBLabel()
    private val previewLabel = JBLabel("Capturing screenshot...").apply {
        horizontalAlignment = SwingConstants.CENTER
        verticalAlignment = SwingConstants.CENTER
    }
    private val previewScroll = JScrollPane(previewLabel)
    private var outputDirectory = initialDirectory
    private var currentImage: BufferedImage? = null

    init {
        title = "Screenshot of ${device.displayName}"
        setModal(false)
        setOKButtonText("Save")
        setCancelButtonText("Cancel")
        resolutionBox.selectedItem = DEFAULT_RESOLUTION_PERCENT
        recaptureButton.addActionListener {
            setCapturing()
            onRecapture()
        }
        copyButton.isEnabled = false
        copyButton.addActionListener { copyImageToClipboard() }
        configureButton.apply {
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            foreground = JBColor.namedColor(
                "Link.activeForeground",
                JBColor(java.awt.Color(0x589DF6), java.awt.Color(0x2B6CB0)),
            )
            margin = JBUI.emptyInsets()
            addActionListener {
                onConfigure()
                refreshOutputDirectory()
            }
        }
        previewScroll.border = JBUI.Borders.empty()
        previewScroll.preferredSize = Dimension(JBUI.scale(300), JBUI.scale(430))
        refreshOutputDirectory()
        setOKActionEnabled(false)
        init()
    }

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            add(
                JPanel(BorderLayout()).apply {
                    add(
                        JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
                            add(recaptureButton)
                            add(copyButton)
                        },
                        BorderLayout.NORTH,
                    )
                    add(
                        JPanel(BorderLayout()).apply {
                            add(metadataLabel, BorderLayout.NORTH)
                            add(previewScroll, BorderLayout.CENTER)
                        },
                        BorderLayout.CENTER,
                    )
                },
                BorderLayout.CENTER,
            )
            add(
                panel {
                    row("Resolution (% of native):") {
                        cell(resolutionBox)
                    }
                    row("Saving to:") {
                        cell(savingToLabel).resizableColumn()
                        cell(configureButton)
                    }
                },
                BorderLayout.SOUTH,
            )
        }

    override fun getPreferredFocusedComponent(): JComponent = recaptureButton

    override fun doOKAction() {
        if (!isOKActionEnabled) return
        val resolution = (resolutionBox.selectedItem as? Int)
            ?: DEFAULT_RESOLUTION_PERCENT
        onSave(resolution)
        super.doOKAction()
    }

    override fun doCancelAction() {
        onCancel()
        super.doCancelAction()
    }

    override fun getHelpId(): String = "scrcpy.studio.screenshot"

    override fun doHelpAction() {
        Messages.showInfoMessage(
            "Recapture refreshes the preview. Save writes the PNG to the configured directory.",
            "Screenshot",
        )
    }

    fun setCapturing() {
        currentImage = null
        previewLabel.icon = null
        previewLabel.text = "Capturing screenshot..."
        metadataLabel.text = ""
        recaptureButton.isEnabled = false
        copyButton.isEnabled = false
        setOKActionEnabled(false)
    }

    fun setImage(file: Path) {
        try {
            val image = ImageIO.read(file.toFile())
                ?: throw IllegalStateException("The screenshot is not a valid PNG.")
            currentImage = image
            previewLabel.text = null
            previewLabel.icon = createPreviewIcon(image)
            metadataLabel.text = metadata(file, image)
            recaptureButton.isEnabled = true
            copyButton.isEnabled = true
            setOKActionEnabled(true)
        } catch (error: Exception) {
            showError(error.message ?: "Unable to display the screenshot.")
        }
    }

    fun showError(message: String) {
        currentImage = null
        previewLabel.icon = null
        previewLabel.text = "Unable to capture screenshot."
        metadataLabel.text = message
        recaptureButton.isEnabled = true
        copyButton.isEnabled = false
        setOKActionEnabled(false)
    }

    fun refreshOutputDirectory() {
        outputDirectory = outputDirectoryProvider()
        savingToLabel.text = outputDirectory.toAbsolutePath().normalize().toString()
        savingToLabel.toolTipText = savingToLabel.text
    }

    fun selectedOutputDirectory(): Path = outputDirectory

    private fun copyImageToClipboard() {
        currentImage?.let { image ->
            CopyPasteManager.getInstance().setContents(ImageTransferable(image))
        }
    }

    private fun createPreviewIcon(image: BufferedImage): javax.swing.Icon {
        val maxWidth = JBUI.scale(270)
        val maxHeight = JBUI.scale(410)
        val scale = minOf(
            1.0,
            maxWidth.toDouble() / image.width,
            maxHeight.toDouble() / image.height,
        )
        val width = (image.width * scale).toInt().coerceAtLeast(1)
        val height = (image.height * scale).toInt().coerceAtLeast(1)
        val preview = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = preview.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            graphics.drawImage(image, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return javax.swing.ImageIcon(preview)
    }

    private fun metadata(file: Path, image: BufferedImage): String =
        String.format(
            Locale.ROOT,
            "%,dx%,d PNG (%d-bit color) %.2f MB",
            image.width,
            image.height,
            image.colorModel.pixelSize,
            Files.size(file) / 1_000_000.0,
        )

    private class ImageTransferable(
        private val image: Image,
    ) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> =
            arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
            flavor == DataFlavor.imageFlavor

        override fun getTransferData(flavor: DataFlavor): Any =
            if (isDataFlavorSupported(flavor)) {
                image
            } else {
                throw UnsupportedFlavorException(flavor)
            }
    }

    private companion object {
        val RESOLUTION_PERCENTS = listOf(25, 50, 75, 100)
        const val DEFAULT_RESOLUTION_PERCENT = 100
    }
}
