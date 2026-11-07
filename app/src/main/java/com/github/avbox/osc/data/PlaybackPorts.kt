package com.github.avbox.osc.data

import com.github.avbox.osc.bean.VodInfo

object PlaybackPorts {

    var currentVod: (() -> VodInfo?)? = null

    var isLiveMode: (() -> Boolean)? = null

    var discardStartedContentOf: ((List<String>) -> Unit)? = null

    var onProgressKeysCleared: ((List<String>) -> Unit)? = null
}
