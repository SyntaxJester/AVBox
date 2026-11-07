package com.github.avbox.osc.player

class PlayerCodecStats private constructor() {

    companion object {
        @JvmField
        @Volatile
        var videoDecoderName: String = ""
    }
}
