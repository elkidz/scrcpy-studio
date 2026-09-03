package com.danielribeiro.scrcpystudio.protocol

import java.awt.image.BufferedImage

data class ScrcpyVideoFrame(
    val serial: String,
    val image: BufferedImage,
)
