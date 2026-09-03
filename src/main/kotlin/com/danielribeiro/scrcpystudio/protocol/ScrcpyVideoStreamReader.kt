package com.danielribeiro.scrcpystudio.protocol

import java.io.DataInputStream
import java.nio.charset.StandardCharsets

sealed interface ScrcpyVideoPacket {

    data class Session(
        val width: Int,
        val height: Int,
        val clientResized: Boolean,
    ) : ScrcpyVideoPacket

    data class Media(
        val ptsUs: Long,
        val isConfig: Boolean,
        val isKeyFrame: Boolean,
        val payload: ByteArray,
    ) : ScrcpyVideoPacket
}

class ScrcpyVideoStreamReader(
    private val input: DataInputStream,
) {

    fun readDeviceName(): String {
        val data = ByteArray(DEVICE_NAME_FIELD_LENGTH)
        input.readFully(data)
        val length = data.indexOf(0).takeIf { it >= 0 } ?: data.size
        return String(data, 0, length, StandardCharsets.UTF_8)
    }

    fun readCodec(): String {
        val data = ByteArray(CODEC_ID_LENGTH)
        input.readFully(data)
        return String(data, StandardCharsets.US_ASCII)
    }

    fun readPacket(): ScrcpyVideoPacket {
        val firstWord = input.readInt()
        if (firstWord and SESSION_FLAG_WORD != 0) {
            val width = input.readInt()
            val height = input.readInt()
            require(width in 1..MAX_VIDEO_DIMENSION) {
                "Invalid scrcpy video width: $width"
            }
            require(height in 1..MAX_VIDEO_DIMENSION) {
                "Invalid scrcpy video height: $height"
            }
            return ScrcpyVideoPacket.Session(
                width = width,
                height = height,
                clientResized = firstWord and CLIENT_RESIZED_FLAG_WORD != 0,
            )
        }

        val secondWord = input.readInt()
        val flags = (firstWord.toLong() shl 32) or (secondWord.toLong() and UINT32_MASK)
        val packetSize = input.readInt()
        require(packetSize in 1..MAX_PACKET_SIZE) {
            "Invalid scrcpy video packet size: $packetSize"
        }
        val payload = ByteArray(packetSize)
        input.readFully(payload)
        return ScrcpyVideoPacket.Media(
            ptsUs = flags and PTS_MASK,
            isConfig = flags and PACKET_FLAG_CONFIG != 0L,
            isKeyFrame = flags and PACKET_FLAG_KEY_FRAME != 0L,
            payload = payload,
        )
    }

    companion object {
        const val DEVICE_NAME_FIELD_LENGTH = 64
        const val CODEC_ID_LENGTH = 4
        const val MAX_PACKET_SIZE = 16 * 1024 * 1024
        const val MAX_VIDEO_DIMENSION = 16_384

        private const val SESSION_FLAG_WORD = Int.MIN_VALUE
        private const val PACKET_FLAG_CONFIG = 1L shl 62
        private const val PACKET_FLAG_KEY_FRAME = 1L shl 61
        private const val CLIENT_RESIZED_FLAG_WORD = 1
        private const val UINT32_MASK = 0xFFFF_FFFFL
        private const val PTS_MASK = (1L shl 61) - 1
    }
}

private fun ByteArray.indexOf(value: Int): Int {
    for (index in indices) {
        if (this[index].toInt() == value) return index
    }
    return -1
}
