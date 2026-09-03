package com.danielribeiro.scrcpystudio.protocol

import java.io.DataOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

class ScrcpyControlWriter(
    output: OutputStream,
) {

    private val output = DataOutputStream(output)
    private val lock = Any()

    fun injectTouch(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1f,
        actionButton: Int = 0,
        buttons: Int = 0,
    ) {
        require(action in 0..0xFF) { "Touch action must fit in one byte." }
        require(screenWidth in 1..0xFFFF) { "Invalid touch screen width: $screenWidth" }
        require(screenHeight in 1..0xFFFF) { "Invalid touch screen height: $screenHeight" }

        synchronized(lock) {
            output.writeByte(TYPE_INJECT_TOUCH_EVENT)
            output.writeByte(action)
            output.writeLong(pointerId)
            output.writeInt(x)
            output.writeInt(y)
            output.writeShort(screenWidth)
            output.writeShort(screenHeight)
            output.writeShort(
                (pressure.coerceIn(0f, 1f) * MAX_U16).roundToInt(),
            )
            output.writeInt(actionButton)
            output.writeInt(buttons)
            output.flush()
        }
    }

    fun back(action: Int = KEY_ACTION_DOWN) {
        synchronized(lock) {
            output.writeByte(TYPE_BACK_OR_SCREEN_ON)
            output.writeByte(action)
            output.flush()
        }
    }

    companion object {
        const val POINTER_ID_MOUSE = -1L
        const val TYPE_INJECT_TOUCH_EVENT = 2
        const val TYPE_BACK_OR_SCREEN_ON = 4
        const val KEY_ACTION_DOWN = 0
        const val KEY_ACTION_UP = 1

        private const val MAX_U16 = 65_535
    }
}
