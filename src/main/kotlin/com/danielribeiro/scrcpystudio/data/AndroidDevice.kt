package com.danielribeiro.scrcpystudio.data

enum class AndroidDeviceState {
    DEVICE,
    OFFLINE,
    UNAUTHORIZED,
    BOOTLOADER,
    NO_PERMISSIONS,
    UNKNOWN,
}

enum class AndroidDeviceTransport {
    USB,
    WIFI,
    UNKNOWN,
}

data class AndroidDevice(
    val serial: String,
    val model: String?,
    val state: AndroidDeviceState,
    val transport: AndroidDeviceTransport,
    val rawState: String,
) {
    val displayName: String
        get() = model?.takeIf(String::isNotBlank)?.replace('_', ' ') ?: serial

    val canMirror: Boolean
        get() = state == AndroidDeviceState.DEVICE
}
