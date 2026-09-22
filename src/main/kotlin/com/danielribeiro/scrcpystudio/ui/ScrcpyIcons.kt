package com.danielribeiro.scrcpystudio.ui

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object ScrcpyIcons {
    @JvmField
    val DevicePower: Icon = load("/icons/devicePower.svg")

    @JvmField
    val DeviceVolumeDown: Icon = load("/icons/deviceVolumeDown.svg")

    @JvmField
    val DeviceVolumeUp: Icon = load("/icons/deviceVolumeUp.svg")

    @JvmField
    val DeviceRotate: Icon = load("/icons/deviceRotate.svg")

    @JvmField
    val DeviceBack: Icon = load("/icons/deviceBack.svg")

    @JvmField
    val DeviceHome: Icon = load("/icons/deviceHome.svg")

    @JvmField
    val DeviceRecents: Icon = load("/icons/deviceRecents.svg")

    @JvmField
    val DeviceScreenshot: Icon = load("/icons/deviceScreenshot.svg")

    @JvmField
    val DeviceRecord: Icon = load("/icons/deviceRecord.svg")

    @JvmField
    val ScrcpyOptions: Icon = load("/icons/scrcpyOptions.svg")

    @JvmField
    val ScrcpyStudio: Icon = load("/icons/scrcpyStudio.svg")

    private fun load(path: String): Icon =
        IconLoader.getIcon(path, ScrcpyIcons::class.java)
}
