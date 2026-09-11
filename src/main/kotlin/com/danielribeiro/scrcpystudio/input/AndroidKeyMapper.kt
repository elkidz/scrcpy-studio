package com.danielribeiro.scrcpystudio.input

import com.danielribeiro.scrcpystudio.protocol.ScrcpyControlWriter
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

object AndroidKeyMapper {

    fun androidKeyCode(event: KeyEvent): Int? {
        val keyCode = event.keyCode
        return when (keyCode) {
            KeyEvent.VK_ENTER -> KEYCODE_ENTER
            KeyEvent.VK_SPACE -> KEYCODE_SPACE
            KeyEvent.VK_TAB -> KEYCODE_TAB
            KeyEvent.VK_BACK_SPACE -> KEYCODE_DEL
            KeyEvent.VK_DELETE -> KEYCODE_FORWARD_DEL
            KeyEvent.VK_ESCAPE -> ScrcpyControlWriter.KEYCODE_BACK
            KeyEvent.VK_LEFT -> KEYCODE_DPAD_LEFT
            KeyEvent.VK_RIGHT -> KEYCODE_DPAD_RIGHT
            KeyEvent.VK_UP -> KEYCODE_DPAD_UP
            KeyEvent.VK_DOWN -> KEYCODE_DPAD_DOWN
            KeyEvent.VK_HOME -> KEYCODE_MOVE_HOME
            KeyEvent.VK_END -> KEYCODE_MOVE_END
            KeyEvent.VK_PAGE_UP -> KEYCODE_PAGE_UP
            KeyEvent.VK_PAGE_DOWN -> KEYCODE_PAGE_DOWN
            KeyEvent.VK_INSERT -> KEYCODE_INSERT
            in KeyEvent.VK_A..KeyEvent.VK_Z ->
                KEYCODE_A + (keyCode - KeyEvent.VK_A)
            in KeyEvent.VK_0..KeyEvent.VK_9 ->
                KEYCODE_0 + (keyCode - KeyEvent.VK_0)
            KeyEvent.VK_MINUS -> KEYCODE_MINUS
            KeyEvent.VK_EQUALS -> KEYCODE_EQUALS
            KeyEvent.VK_OPEN_BRACKET -> KEYCODE_LEFT_BRACKET
            KeyEvent.VK_CLOSE_BRACKET -> KEYCODE_RIGHT_BRACKET
            KeyEvent.VK_BACK_SLASH -> KEYCODE_BACKSLASH
            KeyEvent.VK_SEMICOLON -> KEYCODE_SEMICOLON
            KeyEvent.VK_QUOTE -> KEYCODE_APOSTROPHE
            KeyEvent.VK_COMMA -> KEYCODE_COMMA
            KeyEvent.VK_PERIOD -> KEYCODE_PERIOD
            KeyEvent.VK_SLASH -> KEYCODE_SLASH
            KeyEvent.VK_BACK_QUOTE -> KEYCODE_GRAVE
            else -> null
        }
    }

    fun metastate(event: KeyEvent): Int {
        var meta = 0
        if (event.modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0) {
            meta = meta or META_SHIFT_ON
        }
        if (event.modifiersEx and InputEvent.CTRL_DOWN_MASK != 0) {
            meta = meta or META_CTRL_ON
        }
        if (event.modifiersEx and InputEvent.ALT_DOWN_MASK != 0) {
            meta = meta or META_ALT_ON
        }
        if (event.modifiersEx and InputEvent.META_DOWN_MASK != 0) {
            meta = meta or META_META_ON
        }
        return meta
    }

    fun isCopyShortcut(event: KeyEvent): Boolean =
        event.isControlDown && event.keyCode == KeyEvent.VK_C && !event.isAltDown

    fun isPasteShortcut(event: KeyEvent): Boolean =
        event.isControlDown && event.keyCode == KeyEvent.VK_V && !event.isAltDown

    private const val KEYCODE_0 = 7
    private const val KEYCODE_A = 29
    private const val KEYCODE_COMMA = 55
    private const val KEYCODE_PERIOD = 56
    private const val KEYCODE_TAB = 61
    private const val KEYCODE_SPACE = 62
    private const val KEYCODE_ENTER = 66
    private const val KEYCODE_DEL = 67
    private const val KEYCODE_GRAVE = 68
    private const val KEYCODE_MINUS = 69
    private const val KEYCODE_EQUALS = 70
    private const val KEYCODE_LEFT_BRACKET = 71
    private const val KEYCODE_RIGHT_BRACKET = 72
    private const val KEYCODE_BACKSLASH = 73
    private const val KEYCODE_SEMICOLON = 74
    private const val KEYCODE_APOSTROPHE = 75
    private const val KEYCODE_SLASH = 76
    private const val KEYCODE_PAGE_UP = 92
    private const val KEYCODE_PAGE_DOWN = 93
    private const val KEYCODE_INSERT = 124
    private const val KEYCODE_FORWARD_DEL = 112
    private const val KEYCODE_MOVE_HOME = 122
    private const val KEYCODE_MOVE_END = 123
    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_DOWN = 20
    private const val KEYCODE_DPAD_LEFT = 21
    private const val KEYCODE_DPAD_RIGHT = 22
    private const val META_SHIFT_ON = 0x1
    private const val META_ALT_ON = 0x02
    private const val META_CTRL_ON = 0x1000
    private const val META_META_ON = 0x10000
}
