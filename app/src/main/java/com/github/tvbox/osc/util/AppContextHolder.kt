package com.github.tvbox.osc.util

import android.app.Application

/** 单例 Application Context，供 ad/audio 指纹模块等非 Activity 场景使用。 */
object AppContextHolder {
    var context: android.content.Context? = null
        private set

    fun init(context: android.content.Context) {
        this.context = context.applicationContext
    }
}
