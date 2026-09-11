package com.danielribeiro.scrcpystudio.data

import com.danielribeiro.scrcpystudio.recording.RecordingOptions
import com.danielribeiro.scrcpystudio.settings.ScrcpyMirrorOptions
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import java.nio.file.Path

class ScrcpyCommandBuilder(
    private val scrcpyExecutable: Path,
    private val options: ScrcpyMirrorOptions = ScrcpyMirrorOptions.from(ScrcpySettingsState.State()),
) {

    fun mirror(device: AndroidDevice): List<String> = buildList {
        add(scrcpyExecutable.toString())
        add("--serial")
        add(device.serial)
        add("--window-title")
        add(windowTitle(device))
        addAll(options.toClientArgs())
    }

    fun record(
        device: AndroidDevice,
        outputFile: Path,
        options: RecordingOptions = RecordingOptions(),
        maxSize: Int? = null,
    ): List<String> = buildList {
        add(scrcpyExecutable.toString())
        add("--serial")
        add(device.serial)
        add("--no-window")
        add("--no-playback")
        if (!options.showTaps) {
            add("--no-control")
        }
        add("--record")
        add(outputFile.toString())
        add("--record-format")
        add("mp4")
        add("--video-bit-rate")
        add("${options.bitrateMbps}M")
        add("--time-limit")
        add(options.maxDurationSeconds.toString())
        maxSize?.takeIf { it > 0 }?.let {
            add("--max-size")
            add(it.toString())
        }
        if (options.showTaps) {
            add("--show-touches")
        }
    }

    fun windowTitle(device: AndroidDevice): String = windowTitleFor(device.serial)

    companion object {
        fun windowTitleFor(serial: String): String = "Scrcpy Studio - $serial"
    }
}
