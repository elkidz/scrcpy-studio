package com.danielribeiro.scrcpystudio.session

import com.danielribeiro.scrcpystudio.data.AdbRepository
import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.data.ScrcpyRepository
import com.danielribeiro.scrcpystudio.process.ManagedProcess
import com.danielribeiro.scrcpystudio.process.ProcessRunner
import com.danielribeiro.scrcpystudio.protocol.ScrcpyProtocolException
import com.danielribeiro.scrcpystudio.protocol.ScrcpyProtocolRepository
import com.danielribeiro.scrcpystudio.protocol.ScrcpyProtocolSession
import com.danielribeiro.scrcpystudio.protocol.ScrcpyVideoFrame
import com.danielribeiro.scrcpystudio.settings.ExecutableResolver
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ScrcpySessionService(
    @Suppress("UNUSED_PARAMETER") private val project: Project,
) : Disposable {

    private val settings = ScrcpySettingsState.getInstance()
    private val executableResolver = ExecutableResolver()
    private val processRunner = ProcessRunner()
    private val adbRepository = AdbRepository(settings, executableResolver, processRunner)
    private val scrcpyRepository = ScrcpyRepository(settings, executableResolver, processRunner)
    private val protocolRepository = ScrcpyProtocolRepository(
        settings = settings,
        executableResolver = executableResolver,
        processRunner = processRunner,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshMutex = Mutex()

    private val _devices = MutableStateFlow<List<AndroidDevice>>(emptyList())
    val devices: StateFlow<List<AndroidDevice>> = _devices.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, MirrorSessionState>>(emptyMap())
    val sessions: StateFlow<Map<String, MirrorSessionState>> = _sessions.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _videoFrames = MutableSharedFlow<ScrcpyVideoFrame>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val videoFrames: SharedFlow<ScrcpyVideoFrame> = _videoFrames.asSharedFlow()

    private val mirrorProcesses = ConcurrentHashMap<String, ManagedProcess>()
    private val protocolSessions = ConcurrentHashMap<String, ScrcpyProtocolSession>()
    private val recordingProcesses = ConcurrentHashMap<String, ManagedProcess>()
    private val startingMirrorJobs = ConcurrentHashMap<String, Job>()
    private var monitoringJob: Job? = null

    fun startMonitoring() {
        if (monitoringJob?.isActive == true) return

        monitoringJob = scope.launch {
            while (isActive) {
                refreshDevicesNow()
                delay(DEVICE_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    fun refreshDevices() {
        scope.launch {
            refreshDevicesNow()
        }
    }

    fun startMirror(serial: String) {
        val job = scope.launch(
            context = Dispatchers.IO,
            start = CoroutineStart.LAZY,
        ) {
            try {
                currentCoroutineContext().ensureActive()
                if (mirrorProcesses[serial]?.isRunning == true ||
                    protocolSessions[serial]?.isActive == true
                ) {
                    return@launch
                }

                val devices = refreshDevicesNow() ?: return@launch
                val device = devices.firstOrNull { it.serial == serial }
                if (device == null) {
                    _lastError.value = "The selected device is no longer connected."
                    return@launch
                }
                if (!device.canMirror) {
                    updateSession(
                        device = device,
                        mirrorStatus = MirrorStatus.FAILED,
                        errorMessage = "The device is ${device.rawState} and cannot be mirrored.",
                    )
                    return@launch
                }

                updateSession(
                    device = device,
                    mirrorStatus = MirrorStatus.STARTING,
                    mirrorMode = MirrorMode.EMBEDDED,
                )
                var candidateProtocolSession: ScrcpyProtocolSession? = null
                var protocolSessionRegistered = false
                try {
                    val protocolSession = protocolRepository.startMirror(
                        device = device,
                        parentDisposable = this@ScrcpySessionService,
                        onFrame = { image: BufferedImage ->
                            _videoFrames.tryEmit(
                                ScrcpyVideoFrame(
                                    serial = device.serial,
                                    image = image,
                                ),
                            )
                        },
                        onTerminated = { error ->
                            handleProtocolTerminated(device, error)
                        },
                    )
                    candidateProtocolSession = protocolSession
                    currentCoroutineContext().ensureActive()
                    if (!protocolSession.isActive) {
                        throw ScrcpyProtocolException(
                            "The embedded scrcpy session ended during startup.",
                        )
                    }
                    protocolSessions[serial] = protocolSession
                    protocolSessionRegistered = true
                    updateSession(
                        device = device,
                        mirrorStatus = MirrorStatus.RUNNING,
                        mirrorMode = MirrorMode.EMBEDDED,
                    )
                    protocolSession.start()
                } catch (protocolError: CancellationException) {
                    if (protocolSessionRegistered) {
                        protocolSessions.remove(serial)?.dispose()
                    } else {
                        candidateProtocolSession?.dispose()
                    }
                    throw protocolError
                } catch (error: Exception) {
                    if (protocolSessionRegistered) {
                        protocolSessions.remove(serial)?.dispose()
                    } else {
                        candidateProtocolSession?.dispose()
                    }
                    startExternalFallback(device, protocolError = error)
                }
            } finally {
                currentCoroutineContext()[Job]?.let { job ->
                    startingMirrorJobs.remove(serial, job)
                }
            }
        }
        if (startingMirrorJobs.putIfAbsent(serial, job) == null) {
            job.start()
        } else {
            job.cancel()
        }
    }

    private fun startExternalFallback(
        device: AndroidDevice,
        protocolError: Exception,
    ) {
        if (mirrorProcesses[device.serial]?.isRunning == true) return
        try {
            val process = scrcpyRepository.startMirror(
                device = device,
                parentDisposable = this@ScrcpySessionService,
                onTerminated = { exitCode, output ->
                    handleMirrorTerminated(device, exitCode, output)
                },
            )
            if (!process.isRunning) {
                throw IllegalStateException("The external scrcpy process ended immediately.")
            }
            mirrorProcesses[device.serial] = process
            updateSession(
                device = device,
                mirrorStatus = MirrorStatus.RUNNING,
                mirrorMode = MirrorMode.EXTERNAL_FALLBACK,
            )
        } catch (fallbackError: Exception) {
            updateSession(
                device = device,
                mirrorStatus = MirrorStatus.FAILED,
                errorMessage = buildString {
                    append("Embedded scrcpy failed: ")
                    append(protocolError.message ?: "unknown error")
                    append(". External fallback failed: ")
                    append(fallbackError.message ?: "unknown error")
                },
                mirrorMode = MirrorMode.EMBEDDED,
            )
        }
    }

    fun stopMirror(serial: String) {
        scope.launch(Dispatchers.IO) {
            val startingJob = startingMirrorJobs.remove(serial)
            val device = _devices.value.firstOrNull { it.serial == serial }
                ?: _sessions.value[serial]?.device
            if (device == null) {
                startingJob?.cancel()
                return@launch
            }
            updateSession(device, MirrorStatus.STOPPING)

            startingJob?.cancel()
            stopRecordingInternal(serial)
            val process = mirrorProcesses.remove(serial)
            val protocolSession = protocolSessions.remove(serial)
            if (process == null && protocolSession == null) {
                updateSession(device, MirrorStatus.STOPPED)
            } else {
                process?.dispose()
                protocolSession?.dispose()
                updateSession(device, MirrorStatus.STOPPED)
            }
        }
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
        protocolSessions[serial]?.sendTouch(
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
        protocolSessions[serial]?.sendBack()
    }

    fun startRecording(serial: String, outputFile: Path) {
        scope.launch(Dispatchers.IO) {
            val session = _sessions.value[serial]
            if (session == null || session.mirrorStatus != MirrorStatus.RUNNING) {
                _lastError.value = "Start mirroring before recording."
                return@launch
            }
            if (recordingProcesses[serial]?.isRunning == true) return@launch

            val normalizedOutput = outputFile.toAbsolutePath().normalize()
            try {
                normalizedOutput.parent?.let(Files::createDirectories)
                if (Files.exists(normalizedOutput)) {
                    throw IllegalStateException("The recording file already exists.")
                }

                updateRecording(
                    serial = serial,
                    recording = RecordingState(
                        status = RecordingStatus.STARTING,
                        outputFile = normalizedOutput,
                    ),
                )

                val process = scrcpyRepository.startRecording(
                    device = session.device,
                    outputFile = normalizedOutput,
                    parentDisposable = this@ScrcpySessionService,
                    onTerminated = { exitCode, output ->
                        handleRecordingTerminated(
                            serial = serial,
                            outputFile = normalizedOutput,
                            exitCode = exitCode,
                            output = output,
                        )
                    },
                )
                if (process.isRunning) {
                    recordingProcesses[serial] = process
                    updateRecording(
                        serial = serial,
                        recording = RecordingState(
                            status = RecordingStatus.RECORDING,
                            outputFile = normalizedOutput,
                        ),
                    )
                }
            } catch (error: Exception) {
                updateRecording(
                    serial = serial,
                    recording = RecordingState(
                        status = RecordingStatus.FAILED,
                        outputFile = normalizedOutput,
                        errorMessage = error.message ?: "Unable to start recording.",
                    ),
                )
            }
        }
    }

    fun stopRecording(serial: String) {
        scope.launch(Dispatchers.IO) {
            val recording = _sessions.value[serial]?.recording ?: return@launch
            if (recording.status != RecordingStatus.RECORDING) return@launch
            updateRecording(serial, recording.copy(status = RecordingStatus.STOPPING))
            stopRecordingInternal(serial)
        }
    }

    override fun dispose() {
        stopMonitoring()
        startingMirrorJobs.values.forEach(Job::cancel)
        scope.cancel()
        mirrorProcesses.values.forEach(ManagedProcess::stop)
        protocolSessions.values.forEach(ScrcpyProtocolSession::dispose)
        recordingProcesses.values.forEach(ManagedProcess::stop)
        mirrorProcesses.clear()
        protocolSessions.clear()
        recordingProcesses.clear()
        startingMirrorJobs.clear()
    }

    private suspend fun refreshDevicesNow(): List<AndroidDevice>? =
        refreshMutex.withLock {
            try {
                val devices = adbRepository.listDevices()
                _devices.value = devices
                _lastError.value = null
                devices
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _lastError.value = error.message ?: "Unable to read Android devices."
                null
            }
        }

    private fun handleMirrorTerminated(
        device: AndroidDevice,
        exitCode: Int,
        output: String,
    ) {
        mirrorProcesses.remove(device.serial)?.dispose()
        stopRecordingInternal(device.serial)

        val wasStopping = _sessions.value[device.serial]?.mirrorStatus == MirrorStatus.STOPPING
        val failed = !wasStopping && exitCode != 0
        updateSession(
            device = device,
            mirrorStatus = if (failed) MirrorStatus.FAILED else MirrorStatus.STOPPED,
            errorMessage = if (failed) {
                describeProcessFailure("scrcpy", exitCode, output)
            } else {
                null
            },
        )
    }

    private fun handleProtocolTerminated(
        device: AndroidDevice,
        error: Throwable?,
    ) {
        val wasTracked = protocolSessions.remove(device.serial) != null
        if (!wasTracked &&
            _sessions.value[device.serial]?.mirrorStatus != MirrorStatus.RUNNING
        ) {
            return
        }

        stopRecordingInternal(device.serial)

        val wasStopping = _sessions.value[device.serial]?.mirrorStatus == MirrorStatus.STOPPING
        if (wasStopping) {
            updateSession(
                device = device,
                mirrorStatus = MirrorStatus.STOPPED,
                mirrorMode = MirrorMode.EMBEDDED,
            )
        } else {
            startExternalFallback(
                device = device,
                protocolError = error as? Exception
                    ?: ScrcpyProtocolException("The embedded scrcpy session ended."),
            )
        }
    }

    private fun handleRecordingTerminated(
        serial: String,
        outputFile: Path,
        exitCode: Int,
        output: String,
    ) {
        recordingProcesses.remove(serial)?.dispose()
        val hasOutput = runCatching {
            Files.isRegularFile(outputFile) && Files.size(outputFile) > 0
        }.getOrDefault(false)
        val successful = hasOutput && (exitCode == 0 || exitCode == -1)
        updateRecording(
            serial = serial,
            recording = RecordingState(
                status = if (successful) RecordingStatus.COMPLETED else RecordingStatus.FAILED,
                outputFile = outputFile,
                errorMessage = if (successful) {
                    null
                } else {
                    describeProcessFailure("Recording", exitCode, output)
                },
            ),
        )
    }

    private fun stopRecordingInternal(serial: String) {
        recordingProcesses.remove(serial)?.dispose()
    }

    private fun updateSession(
        device: AndroidDevice,
        mirrorStatus: MirrorStatus,
        errorMessage: String? = null,
        mirrorMode: MirrorMode? = null,
    ) {
        _sessions.update { current ->
            val previous = current[device.serial]
            current + (
                device.serial to MirrorSessionState(
                    device = device,
                    mirrorStatus = mirrorStatus,
                    mirrorMode = mirrorMode ?: previous?.mirrorMode ?: MirrorMode.EMBEDDED,
                    errorMessage = errorMessage,
                    recording = previous?.recording ?: RecordingState(),
                )
                )
        }
    }

    private fun updateRecording(serial: String, recording: RecordingState) {
        _sessions.update { current ->
            val session = current[serial] ?: return@update current
            current + (serial to session.copy(recording = recording))
        }
    }

    private fun describeProcessFailure(
        processName: String,
        exitCode: Int,
        output: String,
    ): String {
        val details = output.trim()
            .lineSequence()
            .filter(String::isNotBlank)
            .toList()
            .takeLast(5)
            .joinToString(" ")
        return buildString {
            append("$processName exited with code $exitCode")
            if (exitCode == WINDOWS_CONTROL_C_EXIT_CODE) {
                append(" (Windows reported CTRL+C termination, 0xC000013A)")
            }
            append(".")
            if (details.isNotEmpty()) {
                append(" ")
                append(details)
            }
        }
    }

    companion object {
        private const val DEVICE_REFRESH_INTERVAL_MS = 1_500L
        private const val WINDOWS_CONTROL_C_EXIT_CODE = -1_073_741_510
    }
}
