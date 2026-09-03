package com.danielribeiro.scrcpystudio.session

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import java.nio.file.Path

enum class MirrorStatus {
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED,
}

enum class MirrorMode {
    EMBEDDED,
    EXTERNAL;

    fun toggled(): MirrorMode = when (this) {
        EMBEDDED -> EXTERNAL
        EXTERNAL -> EMBEDDED
    }
}

enum class RecordingStatus {
    IDLE,
    STARTING,
    RECORDING,
    STOPPING,
    COMPLETED,
    FAILED,
}

enum class ScreenshotStatus {
    IDLE,
    SAVING,
    COMPLETED,
    FAILED,
}

data class RecordingState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val outputFile: Path? = null,
    val errorMessage: String? = null,
)

data class ScreenshotState(
    val status: ScreenshotStatus = ScreenshotStatus.IDLE,
    val outputFile: Path? = null,
    val errorMessage: String? = null,
)

data class MirrorSessionState(
    val device: AndroidDevice,
    val mirrorStatus: MirrorStatus,
    val mirrorMode: MirrorMode = MirrorMode.EMBEDDED,
    val errorMessage: String? = null,
    val modeMessage: String? = null,
    val recording: RecordingState = RecordingState(),
    val screenshot: ScreenshotState = ScreenshotState(),
)
