package com.danielribeiro.scrcpystudio.presentation

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.protocol.ScrcpyVideoFrame
import com.danielribeiro.scrcpystudio.session.MirrorSessionState
import com.danielribeiro.scrcpystudio.session.ScrcpySessionService
import com.intellij.openapi.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Path

data class DeviceMirrorUiState(
    val devices: List<AndroidDevice> = emptyList(),
    val sessions: Map<String, MirrorSessionState> = emptyMap(),
    val lastError: String? = null,
)

class DeviceMirrorViewModel(
    private val service: ScrcpySessionService,
) : Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _selectedSerial = MutableStateFlow<String?>(null)
    val selectedSerial: StateFlow<String?> = _selectedSerial.asStateFlow()
    val videoFrames: SharedFlow<ScrcpyVideoFrame> = service.videoFrames

    val uiState: StateFlow<DeviceMirrorUiState> = combine(
        service.devices,
        service.sessions,
        service.lastError,
    ) { devices, sessions, lastError ->
        DeviceMirrorUiState(
            devices = devices,
            sessions = sessions,
            lastError = lastError,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = DeviceMirrorUiState(),
    )

    fun startMonitoring() {
        service.startMonitoring()
    }

    fun stopMonitoring() {
        service.stopMonitoring()
    }

    fun refreshDevices() {
        service.refreshDevices()
    }

    fun selectDevice(serial: String?) {
        _selectedSerial.value = serial
    }

    fun startMirror(serial: String) {
        service.startMirror(serial)
    }

    fun stopMirror(serial: String) {
        service.stopMirror(serial)
    }

    fun sendTouch(
        serial: String,
        action: Int,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1f,
        actionButton: Int = 0,
        buttons: Int = 0,
    ) {
        service.sendTouch(
            serial = serial,
            action = action,
            x = x,
            y = y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pressure = pressure,
            actionButton = actionButton,
            buttons = buttons,
        )
    }

    fun sendBack(serial: String) {
        service.sendBack(serial)
    }

    fun sendHome(serial: String) {
        service.sendHome(serial)
    }

    fun sendRecents(serial: String) {
        service.sendRecents(serial)
    }

    fun rotate(serial: String) {
        service.rotate(serial)
    }

    fun takeScreenshot(serial: String, outputFile: Path) {
        service.takeScreenshot(serial, outputFile)
    }

    fun toggleMirrorMode(serial: String) {
        service.toggleMirrorMode(serial)
    }

    fun startRecording(serial: String, outputFile: Path) {
        service.startRecording(serial, outputFile)
    }

    fun stopRecording(serial: String) {
        service.stopRecording(serial)
    }

    override fun dispose() {
        scope.cancel()
    }
}
