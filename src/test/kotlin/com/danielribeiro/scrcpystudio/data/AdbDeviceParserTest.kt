package com.danielribeiro.scrcpystudio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbDeviceParserTest {

    @Test
    fun parsesUsbWifiAndUnauthorizedDevices() {
        val output = """
            List of devices attached
            R5CT12345AB device product:foo model:Pixel_8 device:husky usb:1-1 transport_id:1
            192.168.1.20:5555 device product:bar model:Pixel_7 device:panther transport_id:2
            emulator-5554 unauthorized transport_id:3
        """.trimIndent()

        val devices = AdbDeviceParser.parse(output)

        assertEquals(3, devices.size)
        assertEquals(AndroidDeviceTransport.USB, devices[0].transport)
        assertEquals("Pixel 8", devices[0].displayName)
        assertEquals(AndroidDeviceTransport.WIFI, devices[1].transport)
        assertEquals(AndroidDeviceState.UNAUTHORIZED, devices[2].state)
        assertTrue(!devices[2].canMirror)
    }

    @Test
    fun parsesNoPermissionsState() {
        val devices = AdbDeviceParser.parse(
            """
                List of devices attached
                ABC no permissions usb:1-2
            """.trimIndent(),
        )

        assertEquals(AndroidDeviceState.NO_PERMISSIONS, devices.single().state)
        assertEquals(AndroidDeviceTransport.USB, devices.single().transport)
    }

    @Test
    fun ignoresDaemonAndMalformedLines() {
        val devices = AdbDeviceParser.parse(
            """
                * daemon started successfully
                List of devices attached
                malformed
                device-only
                adb.exe: device offline
                adb: device offline
                C:\tools\adb.exe device
            """.trimIndent(),
        )

        assertTrue(devices.isEmpty())
    }
}
