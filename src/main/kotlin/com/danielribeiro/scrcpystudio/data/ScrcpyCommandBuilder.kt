package com.danielribeiro.scrcpystudio.data

import java.nio.file.Path

class ScrcpyCommandBuilder(
    private val scrcpyExecutable: Path,
) {

    fun mirror(device: AndroidDevice): List<String> = buildList {
        add(scrcpyExecutable.toString())
        add("--serial")
        add(device.serial)
        add("--window-title")
        add(windowTitle(device))
        add("--window-borderless")
    }

    fun record(device: AndroidDevice, outputFile: Path): List<String> = buildList {
        add(scrcpyExecutable.toString())
        add("--serial")
        add(device.serial)
        add("--no-window")
        add("--no-playback")
        add("--no-control")
        add("--record")
        add(outputFile.toString())
        add("--record-format")
        add("mp4")
    }

    fun windowTitle(device: AndroidDevice): String = windowTitleFor(device.serial)

    companion object {
        fun windowTitleFor(serial: String): String = "Scrcpy Studio - $serial"
    }
}
