package com.danielribeiro.scrcpystudio.ui

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.data.AndroidDeviceState
import com.danielribeiro.scrcpystudio.data.AndroidDeviceTransport
import com.danielribeiro.scrcpystudio.session.MirrorSessionState
import com.danielribeiro.scrcpystudio.session.MirrorStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrcpyToolWindowUiTest {

    private val device = AndroidDevice(
        serial = "USB-1",
        model = "Pixel_8",
        state = AndroidDeviceState.DEVICE,
        transport = AndroidDeviceTransport.USB,
        rawState = "device",
    )

    @Test
    fun badgeRequiresAConnectedRunningMirror() {
        assertFalse(hasRunningMirror(listOf(device), emptyMap()))
        assertTrue(
            hasRunningMirror(
                devices = listOf(device),
                sessions = mapOf(
                    device.serial to MirrorSessionState(
                        device = device,
                        mirrorStatus = MirrorStatus.RUNNING,
                    ),
                ),
            ),
        )
    }

    @Test
    fun badgeIgnoresDisconnectedDeviceState() {
        val disconnected = device.copy(
            state = AndroidDeviceState.OFFLINE,
            rawState = "offline",
        )
        assertFalse(
            hasRunningMirror(
                devices = listOf(disconnected),
                sessions = mapOf(
                    device.serial to MirrorSessionState(
                        device = device,
                        mirrorStatus = MirrorStatus.RUNNING,
                    ),
                ),
            ),
        )
    }
}
