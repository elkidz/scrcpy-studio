package com.danielribeiro.scrcpystudio.session

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorModeTest {

    @Test
    fun togglesBetweenEmbeddedAndExternalModes() {
        assertEquals(MirrorMode.EXTERNAL, MirrorMode.EMBEDDED.toggled())
        assertEquals(MirrorMode.EMBEDDED, MirrorMode.EXTERNAL.toggled())
    }
}
