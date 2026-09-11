package com.danielribeiro.scrcpystudio.settings

data class ScrcpyMirrorOptions(
    val alwaysOnTopWhenExternal: Boolean,
    val maxSize: Int,
    val maxFps: Int,
    val showTouches: Boolean,
    val stayAwake: Boolean,
    val videoBitRate: Int,
    val videoCodec: VideoCodec,
    val hardwareAcceleration: Boolean,
    val renderBackend: RenderBackend,
    val lowLatency: Boolean,
    val powerSaving: Boolean,
    val keyboardInput: Boolean,
    val clipboardSync: Boolean,
    val performanceProfile: PerformanceProfile,
) {

    fun effectiveMaxFps(): Int {
        val fps = maxFps.coerceAtLeast(0)
        return if (powerSaving && (fps == 0 || fps > POWER_SAVING_MAX_FPS)) {
            POWER_SAVING_MAX_FPS
        } else {
            fps
        }
    }

    fun effectiveBitRate(): Int {
        val bitrate = videoBitRate.coerceAtLeast(MIN_BIT_RATE)
        return if (powerSaving) {
            minOf(bitrate, POWER_SAVING_BIT_RATE)
        } else {
            bitrate
        }
    }

    fun toClientArgs(): List<String> = buildList {
        if (alwaysOnTopWhenExternal) {
            add("--always-on-top")
        }
        val maxSize = maxSize.coerceAtLeast(0)
        if (maxSize > 0) {
            add("--max-size")
            add(maxSize.toString())
        }
        val fps = effectiveMaxFps()
        if (fps > 0) {
            add("--max-fps")
            add(fps.toString())
        }
        if (showTouches) {
            add("--show-touches")
        }
        if (stayAwake) {
            add("--stay-awake")
        }
        add("--video-bit-rate")
        add(effectiveBitRate().toString())
        add("--video-codec")
        add(videoCodec.id)
        if (!hardwareAcceleration) {
            add("--render-driver")
            add("software")
        } else {
            renderBackend.driver?.let { driver ->
                add("--render-driver")
                add(driver)
            }
        }
        if (lowLatency) {
            add("--video-buffer")
            add("0")
        }
        if (!keyboardInput) {
            add("--keyboard=disabled")
        }
        if (!clipboardSync) {
            add("--no-clipboard-autosync")
        }
    }

    fun toServerArgs(): List<String> = buildList {
        add("video_codec=${VideoCodec.H264.id}")
        val maxSize = maxSize.coerceAtLeast(0)
        if (maxSize > 0) {
            add("max_size=$maxSize")
        }
        val fps = effectiveMaxFps()
        if (fps > 0) {
            add("max_fps=$fps")
        }
        add("video_bit_rate=${effectiveBitRate()}")
        if (stayAwake) {
            add("stay_awake=true")
        }
        if (showTouches) {
            add("show_touches=true")
        }
    }

    companion object {
        const val POWER_SAVING_MAX_FPS = 15
        const val POWER_SAVING_BIT_RATE = 2_000_000
        const val MIN_BIT_RATE = 100_000

        fun from(state: ScrcpySettingsState.State): ScrcpyMirrorOptions =
            ScrcpyMirrorOptions(
                alwaysOnTopWhenExternal = state.alwaysOnTopWhenExternal,
                maxSize = state.maxSize,
                maxFps = state.maxFps,
                showTouches = state.showTouches,
                stayAwake = state.stayAwake,
                videoBitRate = state.videoBitRate,
                videoCodec = VideoCodec.fromId(state.videoCodec),
                hardwareAcceleration = state.hardwareAcceleration,
                renderBackend = RenderBackend.fromId(state.renderBackend),
                lowLatency = state.lowLatency,
                powerSaving = state.powerSaving,
                keyboardInput = state.keyboardInput,
                clipboardSync = state.clipboardSync,
                performanceProfile = PerformanceProfile.fromId(state.performanceProfile),
            )

        fun forProfile(profile: PerformanceProfile): ScrcpyMirrorOptions {
            val defaults = from(ScrcpySettingsState.State(performanceProfile = profile.id))
            return when (profile) {
                PerformanceProfile.QUALITY -> defaults.copy(
                    performanceProfile = profile,
                    maxSize = 0,
                    maxFps = 60,
                    videoBitRate = 16_000_000,
                    lowLatency = false,
                    powerSaving = false,
                )

                PerformanceProfile.BALANCED -> defaults.copy(
                    performanceProfile = profile,
                    maxSize = 1920,
                    maxFps = 30,
                    videoBitRate = 8_000_000,
                    lowLatency = false,
                    powerSaving = false,
                )

                PerformanceProfile.LOW_LATENCY -> defaults.copy(
                    performanceProfile = profile,
                    maxSize = 1280,
                    maxFps = 60,
                    videoBitRate = 8_000_000,
                    lowLatency = true,
                    powerSaving = false,
                )
            }
        }
    }
}

fun ScrcpySettingsState.State.writeMirrorOptions(options: ScrcpyMirrorOptions) {
    alwaysOnTopWhenExternal = options.alwaysOnTopWhenExternal
    maxSize = options.maxSize
    maxFps = options.maxFps
    showTouches = options.showTouches
    stayAwake = options.stayAwake
    videoBitRate = options.videoBitRate
    videoCodec = options.videoCodec.id
    hardwareAcceleration = options.hardwareAcceleration
    renderBackend = options.renderBackend.id
    lowLatency = options.lowLatency
    powerSaving = options.powerSaving
    keyboardInput = options.keyboardInput
    clipboardSync = options.clipboardSync
    performanceProfile = options.performanceProfile.id
}
