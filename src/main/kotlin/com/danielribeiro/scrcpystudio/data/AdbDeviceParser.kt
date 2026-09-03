package com.danielribeiro.scrcpystudio.data

object AdbDeviceParser {

    fun parse(output: String): List<AndroidDevice> {
        val lines = output.lineSequence()
            .map(String::trim)
            .toList()
        val headerIndex = lines.indexOfFirst { it == DEVICES_HEADER }
        if (headerIndex < 0) return emptyList()

        return lines
            .drop(headerIndex + 1)
            .filter(String::isNotEmpty)
            .filterNot { it.startsWith("*") }
            .mapNotNull(::parseLine)
            .distinctBy(AndroidDevice::serial)
            .toList()
    }

    private fun parseLine(line: String): AndroidDevice? {
        val tokens = line.split(Regex("\\s+"))
        if (tokens.size < 2) return null

        val serial = tokens[0]
        if (!isDeviceSerial(serial)) return null

        val noPermissions = tokens.getOrNull(1) == "no" && tokens.getOrNull(2) == "permissions"
        val stateToken = if (noPermissions) {
            "no permissions"
        } else {
            tokens[1]
        }
        if (!VALID_STATES.contains(stateToken.lowercase())) return null

        val attributeStart = if (noPermissions) 3 else 2
        val attributes = tokens
            .drop(attributeStart)
            .mapNotNull { token ->
                val separator = token.indexOf(':')
                if (separator <= 0) {
                    null
                } else {
                    token.substring(0, separator) to token.substring(separator + 1)
                }
            }
            .toMap()

        return AndroidDevice(
            serial = serial,
            model = attributes["model"],
            state = parseState(stateToken),
            transport = parseTransport(serial, attributes),
            rawState = stateToken,
        )
    }

    private fun isDeviceSerial(serial: String): Boolean {
        val normalized = serial.removeSuffix(":")
        return normalized.isNotBlank() &&
            !normalized.equals("adb", ignoreCase = true) &&
            !normalized.endsWith(".exe", ignoreCase = true) &&
            !normalized.contains('\\') &&
            !normalized.contains('/')
    }

    private fun parseState(value: String): AndroidDeviceState =
        when (value.lowercase()) {
            "device" -> AndroidDeviceState.DEVICE
            "offline" -> AndroidDeviceState.OFFLINE
            "unauthorized" -> AndroidDeviceState.UNAUTHORIZED
            "bootloader" -> AndroidDeviceState.BOOTLOADER
            "no permissions" -> AndroidDeviceState.NO_PERMISSIONS
            else -> AndroidDeviceState.UNKNOWN
        }

    private fun parseTransport(
        serial: String,
        attributes: Map<String, String>,
    ): AndroidDeviceTransport =
        when {
            attributes.containsKey("usb") -> AndroidDeviceTransport.USB
            serial.matches(IP_SERIAL) || serial.contains("_adb-tls", ignoreCase = true) ->
                AndroidDeviceTransport.WIFI
            else -> AndroidDeviceTransport.UNKNOWN
        }

    private val IP_SERIAL = Regex(
        """^\d{1,3}(?:\.\d{1,3}){3}:\d+$""",
    )

    private const val DEVICES_HEADER = "List of devices attached"

    private val VALID_STATES = setOf(
        "authorizing",
        "bootloader",
        "connecting",
        "device",
        "offline",
        "no permissions",
        "sideload",
        "unauthorized",
        "unknown",
    )
}
