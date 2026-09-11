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

    fun back(action: Int) {
        synchronized(lock) {
            output.writeByte(TYPE_BACK_OR_SCREEN_ON)
            output.writeByte(action)
            output.flush()
        }
    }

    fun pressBack() {
        back(KEY_ACTION_DOWN)
        back(KEY_ACTION_UP)
    }

    fun pressHome() {
        pressKeycode(KEYCODE_HOME)
    }

    fun pressRecents() {
        pressKeycode(KEYCODE_APP_SWITCH)
    }

    fun pressKeycode(keycode: Int) {
        injectKeycode(KEY_ACTION_DOWN, keycode)
        injectKeycode(KEY_ACTION_UP, keycode)
    }

    fun rotateDevice() {
        synchronized(lock) {
            output.writeByte(TYPE_ROTATE_DEVICE)
            output.flush()
        }
    }

    fun injectText(text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_INJECT_TEXT_LENGTH) {
            "Injected text cannot exceed $MAX_INJECT_TEXT_LENGTH bytes."
        }
        synchronized(lock) {
            output.writeByte(TYPE_INJECT_TEXT)
            output.writeInt(payload.size)
            output.write(payload)
            output.flush()
        }
    }

    fun setClipboard(text: String, paste: Boolean) {
        val payload = text.toByteArray(Charsets.UTF_8)
        synchronized(lock) {
            output.writeByte(TYPE_SET_CLIPBOARD)
            output.writeLong(0)
            output.writeBoolean(paste)
            output.writeInt(payload.size)
            output.write(payload)
            output.flush()
        }
    }

    fun getClipboard(copyKey: Int = COPY_KEY_COPY) {
        require(copyKey in 0..2) { "Clipboard copy key must be 0, 1, or 2." }
        synchronized(lock) {
            output.writeByte(TYPE_GET_CLIPBOARD)
            output.writeByte(copyKey)
            output.flush()
        }
    }

    companion object {
        const val TYPE_INJECT_KEYCODE = 0
        const val TYPE_INJECT_TEXT = 1
        const val POINTER_ID_MOUSE = -1L
        const val TYPE_INJECT_TOUCH_EVENT = 2
        const val TYPE_BACK_OR_SCREEN_ON = 4
        const val TYPE_GET_CLIPBOARD = 8
        const val TYPE_SET_CLIPBOARD = 9
        const val TYPE_ROTATE_DEVICE = 11
        const val KEY_ACTION_DOWN = 0
        const val KEY_ACTION_UP = 1
        const val KEYCODE_HOME = 3
        const val KEYCODE_BACK = 4
        const val KEYCODE_VOLUME_UP = 24
        const val KEYCODE_VOLUME_DOWN = 25
        const val KEYCODE_POWER = 26
        const val KEYCODE_APP_SWITCH = 187
        const val COPY_KEY_NONE = 0
        const val COPY_KEY_COPY = 1
        const val COPY_KEY_CUT = 2
        const val DEVICE_MSG_CLIPBOARD = 0

        private const val MAX_U16 = 65_535
        private const val MAX_INJECT_TEXT_LENGTH = 300
    }
}
