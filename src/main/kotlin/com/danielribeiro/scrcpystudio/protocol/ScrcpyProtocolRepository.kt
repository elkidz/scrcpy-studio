package com.danielribeiro.scrcpystudio.protocol

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.process.ManagedProcess
import com.danielribeiro.scrcpystudio.process.ProcessRunner
import com.danielribeiro.scrcpystudio.settings.ExecutableResolver
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import com.intellij.openapi.Disposable
import kotlinx.coroutines.delay
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Path
import kotlin.random.Random

class ScrcpyProtocolRepository(
    private val settings: ScrcpySettingsState,
    private val executableResolver: ExecutableResolver,
    private val processRunner: ProcessRunner,
) {

    suspend fun startMirror(
        device: AndroidDevice,
        parentDisposable: Disposable,
        onFrame: (java.awt.image.BufferedImage) -> Unit,
        onTerminated: (Throwable?) -> Unit,
        onOutput: (String) -> Unit = {},
    ): ScrcpyProtocolSession {
        val tools = executableResolver.resolve(settings.getState())
        val server = tools.scrcpyServer
            ?: throw ScrcpyProtocolException(
                "scrcpy-server was not found beside ${tools.scrcpy.fileName}. " +
                    "Use the official scrcpy distribution containing the server file.",
            )
        val version = resolveScrcpyVersion(tools.scrcpy)
        if (version.substringBefore('.').toIntOrNull() != SUPPORTED_PROTOCOL_MAJOR) {
            throw ScrcpyProtocolException(
                "Embedded mirroring supports scrcpy 4.x; found scrcpy $version.",
            )
        }
        val listener = ServerSocket(0)
        listener.soTimeout = CONNECT_TIMEOUT_MS.toInt()
        val port = listener.localPort
        val scid = Random.nextInt(1, Int.MAX_VALUE)
        val socketName = "scrcpy_${scid.toString(16).padStart(8, '0')}"
        val remoteServerPath = "/data/local/tmp/scrcpy-server-$socketName.jar"

        var reverseCreated = false
        var serverPushed = false
        var serverProcess: ManagedProcess? = null
        var videoSocket: Socket? = null
        var controlSocket: Socket? = null
        var protocolSession: ScrcpyProtocolSession? = null

        try {
            runAdb(
                adb = tools.adb,
                serial = device.serial,
                arguments = listOf(
                    "push",
                    server.toString(),
                    remoteServerPath,
                ),
            )
            serverPushed = true
            runAdb(
                adb = tools.adb,
                serial = device.serial,
                arguments = listOf(
                    "reverse",
                    "localabstract:$socketName",
                    "tcp:$port",
                ),
            )
            reverseCreated = true

            val serverCommand = buildServerCommand(
                adb = tools.adb,
                serial = device.serial,
                serverVersion = version,
                scid = scid,
                remoteServerPath = remoteServerPath,
            )
            serverProcess = processRunner.start(
                command = serverCommand,
                workingDirectory = tools.scrcpy.parent,
                parentDisposable = parentDisposable,
                onOutput = onOutput,
                onTerminated = { exitCode, output ->
                    protocolSession?.serverTerminated(exitCode, output)
                },
            )

            val activeServerProcess = checkNotNull(serverProcess)
            videoSocket = acceptWithRetry(listener, activeServerProcess)
            controlSocket = acceptWithRetry(listener, activeServerProcess)
            val activeVideoSocket = checkNotNull(videoSocket)
            val activeControlSocket = checkNotNull(controlSocket)
            activeVideoSocket.soTimeout = HANDSHAKE_TIMEOUT_MS.toInt()

            val videoReader = ScrcpyVideoStreamReader(
                DataInputStream(
                    BufferedInputStream(activeVideoSocket.getInputStream()),
                ),
            )
            // The first socket carries the fixed-width device name before the
            // video codec id. The name is intentionally consumed even though
            // the IDE already knows the selected device.
            videoReader.readDeviceName()
            val codec = videoReader.readCodec()
            if (codec != CODEC_H264) {
                throw ScrcpyProtocolException(
                    "The scrcpy server selected unsupported video codec: $codec",
                )
            }
            activeVideoSocket.soTimeout = 0

            val session = ScrcpyProtocolSession(
                deviceSerial = device.serial,
                videoSocket = activeVideoSocket,
                controlSocket = activeControlSocket,
                videoReader = videoReader,
                serverProcess = activeServerProcess,
                adb = tools.adb,
                reverseSocketName = socketName,
                remoteServerPath = remoteServerPath,
                processRunner = processRunner,
                onFrame = onFrame,
                onTerminated = onTerminated,
            )
            protocolSession = session
            videoSocket = null
            controlSocket = null
            serverProcess = null
            listener.close()
            return session
        } catch (error: Throwable) {
            listener.closeQuietly()
            videoSocket?.closeQuietly()
            controlSocket?.closeQuietly()
            serverProcess?.dispose()
            if (serverPushed) {
                runCatching {
                    runAdb(
                        adb = tools.adb,
                        serial = device.serial,
                        arguments = listOf("shell", "rm", "-f", remoteServerPath),
                    )
                }
            }
            if (reverseCreated) {
                runCatching {
                    runAdb(
                        adb = tools.adb,
                        serial = device.serial,
                        arguments = listOf(
                            "reverse",
                            "--remove",
                            "localabstract:$socketName",
                        ),
                    )
                }
            }
            throw error
        }
    }

    private suspend fun resolveScrcpyVersion(scrcpy: Path): String {
        val result = processRunner.execute(
            listOf(scrcpy.toString(), "--version"),
            workingDirectory = scrcpy.parent,
        )
        if (result.exitCode != 0) {
            throw ScrcpyProtocolException(
                "Unable to determine the scrcpy version: ${result.output.trim()}",
            )
        }
        return VERSION_PATTERN.find(result.output)?.groupValues?.get(1)
            ?: throw ScrcpyProtocolException(
                "Could not parse the scrcpy version from: ${result.output.trim()}",
            )
    }

    private suspend fun runAdb(
        adb: Path,
        serial: String,
        arguments: List<String>,
    ) {
        val command = buildList {
            add(adb.toString())
            add("-s")
            add(serial)
            addAll(arguments)
        }
        val result = processRunner.execute(command, workingDirectory = adb.parent)
        if (result.exitCode != 0) {
            val details = result.output.trim()
            throw ScrcpyProtocolException(
                buildString {
                    append("ADB command failed (exit code ")
                    append(result.exitCode)
                    append("): ")
                    append(command.joinToString(" "))
                    if (details.isNotEmpty()) {
                        append(". ")
                        append(details)
                    }
                },
            )
        }
    }

    private fun buildServerCommand(
        adb: Path,
        serial: String,
        serverVersion: String,
        scid: Int,
        remoteServerPath: String,
    ): List<String> = listOf(
        adb.toString(),
        "-s",
        serial,
        "shell",
        "CLASSPATH=$remoteServerPath",
        "app_process",
        "/",
        "com.genymobile.scrcpy.Server",
        serverVersion,
        "scid=${scid.toString(16).padStart(8, '0')}",
        "tunnel_forward=false",
        "video=true",
        "audio=false",
        "control=true",
        "send_dummy_byte=false",
        "send_device_meta=true",
        "send_stream_meta=true",
        "send_frame_meta=true",
        "cleanup=true",
        "video_codec=h264",
        "max_size=1920",
        "max_fps=30",
    )

    private suspend fun acceptWithRetry(
        listener: ServerSocket,
        serverProcess: ManagedProcess,
    ): Socket {
        var lastError: IOException? = null
        repeat(CONNECT_ATTEMPTS) {
            if (!serverProcess.isRunning) {
                throw ScrcpyProtocolException(
                    "The scrcpy server stopped before opening its socket: " +
                        serverProcess.outputSnapshot().trim(),
                )
            }
            try {
                return listener.accept().apply { tcpNoDelay = true }
            } catch (error: SocketTimeoutException) {
                lastError = error
                delay(CONNECT_RETRY_DELAY_MS)
            } catch (error: IOException) {
                lastError = error
                delay(CONNECT_RETRY_DELAY_MS)
            }
        }
        throw ScrcpyProtocolException(
            "Timed out waiting for the scrcpy server socket.",
            lastError,
        )
    }

    companion object {
        private const val CODEC_H264 = "h264"
        private const val SUPPORTED_PROTOCOL_MAJOR = 4
        private const val CONNECT_TIMEOUT_MS = 250L
        private const val HANDSHAKE_TIMEOUT_MS = 10_000L
        private const val CONNECT_RETRY_DELAY_MS = 100L
        private const val CONNECT_ATTEMPTS = 80
        private val VERSION_PATTERN =
            Regex("""(?m)\bscrcpy\s+([0-9]+(?:\.[0-9]+)+)\b""", RegexOption.IGNORE_CASE)
    }
}

class ScrcpyProtocolException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

private fun Socket.closeQuietly() {
    runCatching { close() }
}

private fun ServerSocket.closeQuietly() {
    runCatching { close() }
}
