package com.danielribeiro.scrcpystudio.protocol

import org.jcodec.codecs.h264.H264Decoder
import org.jcodec.common.model.ColorSpace
import org.jcodec.common.model.Picture
import org.jcodec.scale.AWTUtil
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService

class H264FrameDecoder {

    private var decoder: H264Decoder? = null
    private var outputBuffer: ByteArrayBuffer? = null

    @Synchronized
    fun startSession(width: Int, height: Int) {
        closeDecoder()
        val alignedWidth = alignToMacroblock(width)
        val alignedHeight = alignToMacroblock(height)
        outputBuffer = ByteArrayBuffer(
            Picture.create(alignedWidth, alignedHeight, ColorSpace.YUV420).data,
        )
    }

    @Synchronized
    fun setCodecConfiguration(payload: ByteArray) {
        require(payload.isNotEmpty()) { "The scrcpy H.264 configuration packet is empty." }
        closeDecoder()
        decoder = H264Decoder.createH264DecoderFromCodecPrivate(
            ByteBuffer.wrap(payload),
        )
    }

    @Synchronized
    fun decode(payload: ByteArray): BufferedImage? {
        val activeDecoder = decoder ?: return null
        val buffer = outputBuffer ?: return null
        val picture = activeDecoder.decodeFrame(
            ByteBuffer.wrap(payload),
            buffer.data,
        ) ?: return null
        return AWTUtil.toBufferedImage(picture)
    }

    @Synchronized
    fun close() {
        closeDecoder()
        outputBuffer = null
    }

    private fun closeDecoder() {
        val activeDecoder = decoder ?: return
        decoder = null
        runCatching {
            // JCodec 0.2.5 creates a daemon pool internally but exposes no
            // lifecycle method. Close that pool so every stopped IDE session
            // releases its decoder threads.
            val pool = H264Decoder::class.java
                .getDeclaredField("tp")
                .apply { isAccessible = true }
                .get(activeDecoder) as? ExecutorService
            pool?.shutdownNow()
        }
    }

    private class ByteArrayBuffer(
        val data: Array<ByteArray>,
    )

    private fun alignToMacroblock(value: Int): Int =
        ((value + MACROBLOCK_SIZE - 1) / MACROBLOCK_SIZE) * MACROBLOCK_SIZE

    private companion object {
        const val MACROBLOCK_SIZE = 16
    }
}
