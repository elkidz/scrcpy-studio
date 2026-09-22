package com.danielribeiro.scrcpystudio.recording

data class RecordingOptions(
    val bitrateMbps: Int = DEFAULT_BITRATE_MBPS,
    val resolutionPercent: Int = DEFAULT_RESOLUTION_PERCENT,
    val showTaps: Boolean = DEFAULT_SHOW_TAPS,
    val maxDurationSeconds: Int = MAX_DURATION_SECONDS,
) {
    init {
        require(bitrateMbps in MIN_BITRATE_MBPS..MAX_BITRATE_MBPS) {
            "Recording bitrate must be between $MIN_BITRATE_MBPS and $MAX_BITRATE_MBPS Mbps."
        }
        require(resolutionPercent in SUPPORTED_RESOLUTION_PERCENTS) {
            "Unsupported recording resolution: $resolutionPercent%."
        }
        require(maxDurationSeconds in 1..MAX_DURATION_SECONDS) {
            "Recording duration must be between 1 second and $MAX_DURATION_SECONDS seconds."
        }
    }

    companion object {
        const val DEFAULT_BITRATE_MBPS = 4
        const val DEFAULT_RESOLUTION_PERCENT = 100
        const val DEFAULT_SHOW_TAPS = true
        const val MAX_DURATION_SECONDS = 30 * 60
        const val MIN_BITRATE_MBPS = 1
        const val MAX_BITRATE_MBPS = 16
        val SUPPORTED_RESOLUTION_PERCENTS = listOf(25, 50, 75, 100)
    }
}
