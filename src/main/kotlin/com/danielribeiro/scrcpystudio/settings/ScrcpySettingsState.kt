package com.danielribeiro.scrcpystudio.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.io.File

@State(
    name = "ScrcpyStudioSettings",
    storages = [Storage("scrcpy-studio.xml")],
)
class ScrcpySettingsState : PersistentStateComponent<ScrcpySettingsState.State> {

    data class State(
        var scrcpyPath: String = "",
        var adbPath: String = "",
        var recordingDirectory: String = defaultRecordingDirectory(),
        var autoMirrorOnDeviceConnect: Boolean = true,
        var autoReconnect: Boolean = true,
        var alwaysOnTopWhenExternal: Boolean = false,
        var maxSize: Int = 1920,
        var maxFps: Int = 30,
        var showTouches: Boolean = false,
        var stayAwake: Boolean = false,
        var videoBitRate: Int = 8_000_000,
        var videoCodec: String = VideoCodec.H264.id,
        var hardwareAcceleration: Boolean = true,
        var renderBackend: String = RenderBackend.DEFAULT.id,
        var lowLatency: Boolean = false,
        var powerSaving: Boolean = false,
        var keyboardInput: Boolean = true,
        var clipboardSync: Boolean = true,
        var performanceProfile: String = PerformanceProfile.BALANCED.id,
    )

    private var currentState = State()

    override fun getState(): State = currentState

    override fun loadState(state: State) {
        currentState = state
    }

    companion object {
        fun getInstance(): ScrcpySettingsState =
            ApplicationManager.getApplication().getService(ScrcpySettingsState::class.java)

        private fun defaultRecordingDirectory(): String =
            File(
                System.getProperty("user.home"),
                "Videos${File.separator}Scrcpy Studio",
            ).path
    }
}

enum class PerformanceProfile(val id: String, val label: String) {
    QUALITY("quality", "Quality"),
    BALANCED("balanced", "Balanced"),
    LOW_LATENCY("low_latency", "Low Latency"),
    ;

    companion object {
        fun fromId(id: String): PerformanceProfile =
            entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

enum class VideoCodec(val id: String, val label: String) {
    H264("h264", "H.264"),
    H265("h265", "H.265"),
    AV1("av1", "AV1"),
    ;

    companion object {
        fun fromId(id: String): VideoCodec =
            entries.firstOrNull { it.id == id } ?: H264
    }
}

enum class RenderBackend(val id: String, val label: String, val driver: String?) {
    DEFAULT("default", "Default", null),
    DIRECT3D("direct3d", "Direct3D", "direct3d"),
    OPENGL("opengl", "OpenGL", "opengl"),
    SOFTWARE("software", "Software", "software"),
    ;

    companion object {
        fun fromId(id: String): RenderBackend =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
