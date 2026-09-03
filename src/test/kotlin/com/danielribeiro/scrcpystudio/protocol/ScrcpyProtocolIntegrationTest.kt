package com.danielribeiro.scrcpystudio.protocol

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.data.AndroidDeviceState
import com.danielribeiro.scrcpystudio.data.AndroidDeviceTransport
import com.danielribeiro.scrcpystudio.process.ProcessRunner
import com.danielribeiro.scrcpystudio.settings.ExecutableResolver
import com.danielribeiro.scrcpystudio.settings.ScrcpySettingsState
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScrcpyProtocolIntegrationTest {

    @Test
    fun receivesAndDecodesAFrameFromConnectedDevice() {
        val scrcpy = System.getenv("SCRCPY_STUDIO_SCRCPY")
        val adb = System.getenv("SCRCPY_STUDIO_ADB")
        val serial = System.getenv("SCRCPY_STUDIO_SERIAL")
        assumeTrue(
            "Set SCRCPY_STUDIO_SCRCPY, SCRCPY_STUDIO_ADB, and SCRCPY_STUDIO_SERIAL to run.",
            !scrcpy.isNullOrBlank() && !adb.isNullOrBlank() && !serial.isNullOrBlank(),
        )

        val settings = ScrcpySettingsState().apply {
            loadState(
                ScrcpySettingsState.State(
                    scrcpyPath = scrcpy.orEmpty(),
                    adbPath = adb.orEmpty(),
                ),
            )
        }
        val repository = ScrcpyProtocolRepository(
            settings = settings,
            executableResolver = ExecutableResolver(),
            processRunner = ProcessRunner(),
        )
        val device = AndroidDevice(
            serial = serial.orEmpty(),
            model = null,
            state = AndroidDeviceState.DEVICE,
            transport = AndroidDeviceTransport.USB,
            rawState = "device",
        )
        val parent = Disposer.newDisposable()
        val frameReceived = CountDownLatch(1)
        var frameWidth = 0
        var frameHeight = 0
        var session: ScrcpyProtocolSession? = null

        try {
            val activeSession = runBlocking {
                repository.startMirror(
                    device = device,
                    parentDisposable = parent,
                    onFrame = {
                        frameWidth = it.width
                        frameHeight = it.height
                        frameReceived.countDown()
                    },
                    onTerminated = {},
                )
            }
            session = activeSession
            activeSession.start()
            assertTrue(
                "The embedded client did not decode a frame in time.",
                frameReceived.await(15, TimeUnit.SECONDS),
            )
            assertTrue(frameWidth > 0)
            assertTrue(frameHeight > 0)
        } finally {
            session?.dispose()
            Disposer.dispose(parent)
        }
    }
}
