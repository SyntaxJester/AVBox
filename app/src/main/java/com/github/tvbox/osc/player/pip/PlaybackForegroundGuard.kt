package com.github.tvbox.osc.player.pip

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build

object PlaybackForegroundGuard {

    @JvmStatic
    fun isAppInForeground(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val appManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcesses = appManager.runningAppProcesses
            return runningAppProcesses.any {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    it.processName == context.packageName
            }
        }
        return true
    }
}
