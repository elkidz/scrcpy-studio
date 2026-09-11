package com.danielribeiro.scrcpystudio.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrcpyMirrorOptionsTest {

    @Test
    fun balancedProfileUsesCurrentEmbeddedDefaults() {
        val options = ScrcpyMirrorOptions.forProfile(PerformanceProfile.BALANCED)

        assertEquals(1920, options.maxSize)
        assertEquals(30, options.maxFps)
        assertEquals(8_000_000, options.videoBitRate)
        assertFalse(options.lowLatency)
        assertTrue(options.toServerArgs().contains("max_size=1920"))
        assertTrue(options.toServerArgs().contains("video_codec=h264"))
    }

    @Test
    fun powerSavingCapsFpsAndBitrate() {
        val options = ScrcpyMirrorOptions.from(
            ScrcpySettingsState.State(
                maxFps = 60,
                videoBitRate = 8_000_000,
                powerSaving = true,
            ),
        )

        assertEquals(15, options.effectiveMaxFps())
        assertEquals(2_000_000, options.effectiveBitRate())
        assertTrue(options.toClientArgs().containsAll(listOf("--max-fps", "15")))
        assertTrue(options.toClientArgs().containsAll(listOf("--video-bit-rate", "2000000")))
    }

    @Test
    fun externalWindowOptionsIncludeAlwaysOnTopAndLowLatency() {
        val options = ScrcpyMirrorOptions.from(
            ScrcpySettingsState.State(
                alwaysOnTopWhenExternal = true,
                lowLatency = true,
                keyboardInput = false,
                clipboardSync = false,
                hardwareAcceleration = false,
            ),
        )
        val args = options.toClientArgs()

        assertTrue(args.contains("--always-on-top"))
        assertTrue(args.containsAll(listOf("--video-buffer", "0")))
        assertTrue(args.contains("--keyboard=disabled"))
        assertTrue(args.contains("--no-clipboard-autosync"))
        assertTrue(args.containsAll(listOf("--render-driver", "software")))
        assertFalse(args.contains("--no-hw-decoding"))
    }
}
