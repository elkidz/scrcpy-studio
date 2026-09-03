package com.danielribeiro.scrcpystudio.session

import com.danielribeiro.scrcpystudio.data.AndroidDevice
import com.danielribeiro.scrcpystudio.data.AndroidDeviceState
import com.danielribeiro.scrcpystudio.data.AndroidDeviceTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceConnectionTrackerTest {

    private val device = AndroidDevice(
        serial = "USB-1",
        model = "Pixel_8",
        state = AndroidDeviceState.DEVICE,
        transport = AndroidDeviceTransport.USB,
        rawState = "device",
    )

    @Test
    fun firstScanOnlyEstablishesBaseline() {
        val diff = DeviceConnectionTracker().update(listOf(device))

        assertTrue(diff.connected.isEmpty())
        assertTrue(diff.disconnected.isEmpty())
    }

    @Test
    fun reportsNewDeviceAndReturnFromUnauthorized() {
        val tracker = DeviceConnectionTracker()
        tracker.update(
            listOf(
                device.copy(state = AndroidDeviceState.UNAUTHORIZED, rawState = "unauthorized"),
            ),
        )

        val diff = tracker.update(listOf(device))

        assertEquals(listOf(device), diff.connected)
        assertTrue(diff.disconnected.isEmpty())
    }

    @Test
    fun reportsDisconnectedDevice() {
        val tracker = DeviceConnectionTracker()
        tracker.update(listOf(device))

        val diff = tracker.update(emptyList())

        assertTrue(diff.connected.isEmpty())
        assertEquals(listOf(device), diff.disconnected)
    }
}
