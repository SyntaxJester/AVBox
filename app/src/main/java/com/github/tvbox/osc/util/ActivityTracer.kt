package com.github.tvbox.osc.util

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 页面生命周期追踪：谁被打开、谁被关掉、关的时候是谁调用的，全记下来。
 * 闪退/自动返回首页这类问题，看这一个日志就能定位是页面自己结束的还是进程没了。
 */
object ActivityTracer {

    @JvmStatic
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                EchoLog.i("UI", "+ create " + name(activity) + " task=" + activity.taskId)
            }

            override fun onActivityStarted(activity: Activity) {
                EchoLog.i("UI", "+ start " + name(activity))
            }

            override fun onActivityResumed(activity: Activity) {
                EchoLog.i("UI", "+ resume " + name(activity))
            }

            override fun onActivityPaused(activity: Activity) {
                EchoLog.i("UI", "- pause " + name(activity))
            }

            override fun onActivityStopped(activity: Activity) {
                EchoLog.i("UI", "- stop " + name(activity))
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
                EchoLog.i(
                    "UI",
                    "- destroy " + name(activity)
                        + " finishing=" + activity.isFinishing
                        + " changingConfigurations=" + activity.isChangingConfigurations,
                )
            }
        })
        EchoLog.i("UI", "activity tracer installed")
    }

    private fun name(activity: Activity): String = activity.javaClass.simpleName
}
