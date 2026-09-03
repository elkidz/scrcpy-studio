package com.danielribeiro.scrcpystudio.protocol

import com.danielribeiro.scrcpystudio.process.ManagedProcess
import com.danielribeiro.scrcpystudio.process.ProcessRunner
import com.intellij.openapi.Disposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.IOException
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

class ScrcpyProtocolSession internal constructor(
    private val deviceSerial: String,
    private val videoSocket: Socket,
    private val controlSocket: Socket,
    private val videoReader: ScrcpyVideoStreamReader,
    private val serverProcess: ManagedProcess,
    private val adb: Path,
    private val reverseSocketName: String,
    private val remoteServerPath: String,
    private val processRunner: ProcessRunner,
    private val onFrame: (BufferedImage) -> Unit,
    private val onTerminated: (Throwable?) -> Unit,
) : Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val decoder = H264FrameDecoder()
    private val controlWriter = ScrcpyControlWriter(controlSocket.getOutputStream())
    private val stopped = AtomicBoolean(false)
    private val terminationNotified = AtomicBoolean(false)
    private val started = AtomicBoolean(false)

    val isActive: Boolean
        get() = !stopped.get()

    fun start() {
        if (stopped.get()) return
        check(started.compareAndSet(false, true)) {
            "The scrcpy protocol session has already been started."
        }

        scope.launch {
            readVideo()
        }
        scope.launch {
            drainDeviceMessages()
        }
    }

    fun sendTouch(
        action: Int,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1f,
        actionButton: Int = 0,
        buttons: Int = 0,
    ) {
        if (stopped.get()) return
        try {
            controlWriter.injectTouch(
                action = action,
                pointerId = ScrcpyControlWriter.POINTER_ID_MOUSE,
                x = x,
                y = y,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                pressure = pressure,
                actionButton = actionButton,
                buttons = buttons,
            )
        } catch (error: IOException) {
            finish(error)
        }
    }

    fun sendBack() {
        if (stopped.get()) return
        try {
            controlWriter.back()
        } catch (error: IOException) {
            finish(error)
        }
    }

    fun sendHome() {
        if (stopped.get()) return
        try {
            controlWriter.home()
        } catch (error: IOException) {
            finish(error)
        }
    }

    fun sendRecents() {
        if (stopped.get()) return
        try {
            controlWriter.recents()
        } catch (error: IOException) {
            finish(error)
        }
    }

    fun rotateDevice() {
        if (stopped.get()) return
        try {
            controlWriter.rotateDevice()
        } catch (error: IOException) {
            finish(error)
        }
    }

    internal fun serverTerminated(exitCode: Int, output: String) {
        if (stopped.get()) return
        val details = output.trim().takeLast(512)
        finish(
            ScrcpyProtocolException(
                buildString {
                    append("The scrcpy server exited with code ")
                    append(exitCode)
                    if (details.isNotEmpty()) {
                        append(": ")
                        append(details)
                    }
                },
            ),
        )
    }

    override fun dispose() {
        if (!stopped.compareAndSet(false, true)) return

        scope.cancel()
        closeQuietly(videoSocket)
        closeQuietly(controlSocket)
        decoder.close()
        serverProcess.dispose()

        cleanupScope.launch {
            removeReverse()
            removeRemoteServer()
        }
    }

    private suspend fun readVideo() {
        try {
            while (!stopped.get()) {
                when (val packet = videoReader.readPacket()) {
                    is ScrcpyVideoPacket.Session -> {
                        decoder.startSession(packet.width, packet.height)
                    }

                    is ScrcpyVideoPacket.Media -> {
                        if (packet.isConfig) {
                            decoder.setCodecConfiguration(packet.payload)
                        } else {
                            decoder.decode(packet.payload)?.let(onFrame)
                        }
                    }
                }
            }
            finish(null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            finish(error)
        }
    }

    private suspend fun drainDeviceMessages() {
        val input = runCatching {
            controlSocket.getInputStream()
        }.getOrNull() ?: return
        val buffer = ByteArray(8 * 1024)

        try {
            while (!stopped.get()) {
                if (input.read(buffer) == -1) {
                    finish(null)
                    return
                }
            }
        } catch (error: IOException) {
            if (!stopped.get()) {
                finish(error)
            }
        }
    }

    private fun finish(error: Throwable?) {
        if (!terminationNotified.compareAndSet(false, true)) return
        dispose()
        onTerminated(error)
    }

    private suspend fun removeReverse() {
        runCatching {
            processRunner.execute(
                listOf(
                    adb.toString(),
                    "-s",
                    deviceSerial,
                    "reverse",
                    "--remove",
                    "localabstract:$reverseSocketName",
                ),
            )
        }
    }

    private suspend fun removeRemoteServer() {
        runCatching {
            processRunner.execute(
                listOf(
                    adb.toString(),
                    "-s",
                    deviceSerial,
                    "shell",
                    "rm",
                    "-f",
                    remoteServerPath,
                ),
            )
        }
    }

    private fun closeQuietly(socket: Socket) {
        runCatching { socket.close() }
    }
}
