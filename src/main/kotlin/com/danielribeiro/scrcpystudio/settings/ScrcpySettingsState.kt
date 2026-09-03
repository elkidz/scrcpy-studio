package com.danielribeiro.scrcpystudio.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.io.File

@Service(Service.Level.APP)
@State(
    name = "ScrcpyStudioSettings",
    storages = [Storage("scrcpy-studio.xml")],
)
class ScrcpySettingsState : PersistentStateComponent<ScrcpySettingsState.State> {

    data class State(
        var scrcpyPath: String = "",
        var adbPath: String = "",
        var recordingDirectory: String = defaultRecordingDirectory(),
        var autoOpenOnDeviceConnect: Boolean = true,
        var autoMirrorOnDeviceConnect: Boolean = true,
        var autoReconnect: Boolean = true,
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
