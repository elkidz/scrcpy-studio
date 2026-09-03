package com.danielribeiro.scrcpystudio.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ProcessResult(
    val exitCode: Int,
    val output: String,
)

data class BinaryProcessResult(
    val exitCode: Int,
    val output: ByteArray,
    val errorOutput: String,
)

class ManagedProcess internal constructor(
    val command: List<String>,
    internal val handler: OSProcessHandler,
    private val output: StringBuilder,
    private val outputLimit: Int,
) : Disposable {

    val isRunning: Boolean
        get() = !handler.isProcessTerminated

    fun stop() {
        if (isRunning) {
            handler.destroyProcess()
        }
    }

    fun outputSnapshot(): String = synchronized(output) {
        output.toString()
    }

    override fun dispose() {
        stop()
    }

    internal fun appendOutput(text: String) {
        synchronized(output) {
            if (output.length >= outputLimit) return
            val remaining = outputLimit - output.length
            output.append(text.take(remaining))
        }
    }
}

class ProcessRunner(
    private val outputLimit: Int = DEFAULT_OUTPUT_LIMIT,
) {

    fun start(
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
        workingDirectory: Path? = null,
        parentDisposable: Disposable? = null,
        onOutput: (String) -> Unit = {},
        onTerminated: (exitCode: Int, output: String) -> Unit = { _, _ -> },
    ): ManagedProcess {
        require(command.isNotEmpty()) { "A process command cannot be empty." }

        val commandLine = GeneralCommandLine(command.first())
            .withParameters(command.drop(1))
            .withEnvironment(environment)
        workingDirectory?.let { commandLine.withWorkDirectory(it.toFile()) }

        val handler = OSProcessHandler(commandLine)
        val output = StringBuilder()
        val managedProcess = ManagedProcess(
            command = command.toList(),
            handler = handler,
            output = output,
            outputLimit = outputLimit,
        )
        val listener = object : ProcessListener {
            override fun startNotified(event: ProcessEvent) = Unit

            override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) = Unit

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text = event.text
                managedProcess.appendOutput(text)
                onOutput(text)
            }

            override fun processTerminated(event: ProcessEvent) {
                val capturedOutput = managedProcess.outputSnapshot()
                onTerminated(event.exitCode, capturedOutput)
                handler.removeProcessListener(this)
            }
        }

        handler.addProcessListener(listener)
        parentDisposable?.let { Disposer.register(it, managedProcess) }
        handler.startNotify()
        return managedProcess
    }

    suspend fun execute(
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
        workingDirectory: Path? = null,
    ): ProcessResult = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            lateinit var managedProcess: ManagedProcess
            try {
                managedProcess = start(
                    command = command,
                    environment = environment,
                    workingDirectory = workingDirectory,
                    onTerminated = { exitCode, output ->
                        managedProcess.dispose()
                        if (continuation.isActive) {
                            continuation.resume(ProcessResult(exitCode, output))
                        }
                    },
                )
                continuation.invokeOnCancellation {
                    managedProcess.stop()
                }
            } catch (error: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    suspend fun executeBinary(
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
        workingDirectory: Path? = null,
    ): BinaryProcessResult = withContext(Dispatchers.IO) {
        require(command.isNotEmpty()) { "A process command cannot be empty." }

        val process = ProcessBuilder(command)
            .apply {
                directory(workingDirectory?.toFile())
                environment().putAll(environment)
            }
            .start()

        try {
            coroutineScope {
                val output = async(Dispatchers.IO) {
                    process.inputStream.use { it.readBytes() }
                }
                val errorOutput = async(Dispatchers.IO) {
                    process.errorStream.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val exitCode = process.waitFor()
                BinaryProcessResult(
                    exitCode = exitCode,
                    output = output.await(),
                    errorOutput = errorOutput.await(),
                )
            }
        } catch (error: CancellationException) {
            process.destroyForcibly()
            throw error
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    companion object {
        private const val DEFAULT_OUTPUT_LIMIT = 128 * 1024
    }
}
