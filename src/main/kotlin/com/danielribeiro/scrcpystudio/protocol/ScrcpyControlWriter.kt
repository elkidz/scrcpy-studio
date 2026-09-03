package com.danielribeiro.scrcpystudio.protocol

import java.io.DataOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

class ScrcpyControlWriter(
    output: OutputStream,
) {

    private val output = DataOutputStream(output)
    private val lock = Any()

    fun injectKeycode(
        action: Int,
        keycode: Int,
        repeat: Int = 0,
        metastate: Int = 0,
    ) {
        require(action in 0..0xFF) { "Key action must fit in one byte." }
        require(keycode >= 0) { "Keycode cannot be negative." }
        require(repeat >= 0) { "Key repeat cannot be negative." }
        require(metastate >= 0) { "Key metastate cannot be negative." }

        synchronized(lock) {
            output.writeByte(TYPE_INJECT_KEYCODE)
            output.writeByte(action)
            output.writeInt(keycode)
            output.writeInt(repeat)
            output.writeInt(metastate)
            output.flush()
        }
    }

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

    fun home(action: Int = KEY_ACTION_DOWN) {
        injectKeycode(
            action = action,
            keycode = KEYCODE_HOME,
        )
    }

    fun recents(action: Int = KEY_ACTION_DOWN) {
        injectKeycode(
            action = action,
            keycode = KEYCODE_APP_SWITCH,
        )
    }

    fun rotateDevice() {
        synchronized(lock) {
            output.writeByte(TYPE_ROTATE_DEVICE)
            output.flush()
        }
    }

    companion object {
        const val TYPE_INJECT_KEYCODE = 0
        const val POINTER_ID_MOUSE = -1L
        const val TYPE_INJECT_TOUCH_EVENT = 2
        const val TYPE_BACK_OR_SCREEN_ON = 4
        const val TYPE_ROTATE_DEVICE = 11
        const val KEY_ACTION_DOWN = 0
        const val KEY_ACTION_UP = 1
        const val KEYCODE_HOME = 3
        const val KEYCODE_BACK = 4
        const val KEYCODE_APP_SWITCH = 187

        private const val MAX_U16 = 65_535
    }
}
