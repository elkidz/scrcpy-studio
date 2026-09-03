package com.danielribeiro.scrcpystudio.session

import com.danielribeiro.scrcpystudio.data.AndroidDevice

data class DeviceConnectionDiff(
    val connected: List<AndroidDevice>,
    val disconnected: List<AndroidDevice>,
)

class DeviceConnectionTracker {

    private var previousDevices: Map<String, AndroidDevice>? = null

    fun update(currentDevices: List<AndroidDevice>): DeviceConnectionDiff {
        val currentBySerial = currentDevices.associateBy(AndroidDevice::serial)
        val previous = previousDevices
        previousDevices = currentBySerial

        if (previous == null) {
            return DeviceConnectionDiff(emptyList(), emptyList())
        }

        val connected = currentDevices.filter { device ->
            val previousDevice = previous[device.serial]
            previousDevice == null || (!previousDevice.canMirror && device.canMirror)
        }
        val disconnected = previous.values.filter { device ->
            currentBySerial[device.serial] == null ||
                (device.canMirror && currentBySerial[device.serial]?.canMirror == false)
        }
        return DeviceConnectionDiff(
            connected = connected,
            disconnected = disconnected,
        )
    }

    fun reset() {
        previousDevices = null
    }
}
