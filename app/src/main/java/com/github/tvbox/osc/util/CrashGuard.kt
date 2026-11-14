package com.github.tvbox.osc.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast

/**
 * 崩溃守卫。
 *
 * 解决两件事：
 *  1. 记录 —— 任何未捕获异常都把完整堆栈写进 EchoLog，并立刻落盘；
 *     进程如果被系统杀掉（native 崩溃 / ANR / 低内存），下次启动时把系统的退出原因也补进日志。
 *  2. 拦截 —— 播放页抛异常时不再让进程直接死（表现出来就是"一闪退回首页"），
 *     而是把异常吃掉、停掉播放、留在当前页，让用户还能继续操作。
 *
 * 连续崩溃超过阈值会自动放行给系统处理，避免死循环。
 */
object CrashGuard {

    private const val TAG = "CrashGuard"
    private const val MAX_RECOVER_IN_WINDOW = 5
    private const val RECOVER_WINDOW_MS = 60_000L

    private var recoverCount = 0
    private var windowStart = 0L

    @Volatile
    private var lastCrashSummary: String? = null

    @Volatile
    private var installed = false

    @JvmStatic
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        logPreviousExit(app)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            onUncaught(app, thread, throwable, previous)
        }
        EchoLog.i(TAG, "crash guard installed")
    }

    /** 上次进程为什么没的（系统记录），启动时补进日志 */
    private fun logPreviousExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val list = am.getHistoricalProcessExitReasons(context.packageName, 0, 3)
            if (list.isNullOrEmpty()) {
                EchoLog.i(TAG, "previous exit: none recorded")
                return
            }
            for (info in list) {
                val reason = reasonText(info.reason)
                EchoLog.section("PREVIOUS EXIT")
                EchoLog.e(
                    TAG,
                    "reason=" + reason
                        + " status=" + info.status
                        + " importance=" + info.importance
                        + " timestamp=" + info.timestamp
                        + " description=" + (info.description ?: "")
                )
                try {
                    val trace = info.traceInputStream
                    if (trace != null) {
                        val text = trace.bufferedReader().use { it.readText() }
                        if (text.isNotBlank()) {
                            EchoLog.e(TAG, "system trace:\n" + text.take(200_000))
                        }
                    }
                } catch (ignored: Throwable) {
                }
            }
        } catch (th: Throwable) {
            EchoLog.e(TAG, "read previous exit failed", th)
        }
    }

    private fun reasonText(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH(Java异常)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE(native崩溃)"
        ApplicationExitInfo.REASON_ANR -> "ANR(无响应)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY(内存不足被杀)"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED(信号终止)"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF(主动退出)"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED(用户操作)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "UNKNOWN(" + reason + ")"
    }

    private fun onUncaught(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        previous: Thread.UncaughtExceptionHandler?,
    ) {
        val threadName = thread.name
        val mainThread = Looper.getMainLooper().thread
        val isMain = thread === mainThread

        try {
            EchoLog.section("UNCAUGHT EXCEPTION")
            EchoLog.e(TAG, "thread=" + threadName + " main=" + isMain)
            EchoLog.crash(TAG, "fatal: " + throwable.javaClass.name + ": " + throwable.message, throwable)
            EchoLog.i(TAG, "state: " + Snapshot.describe())
            EchoLog.flushSync()
        } catch (ignored: Throwable) {
        }

        lastCrashSummary = throwable.javaClass.simpleName + ": " + (throwable.message ?: "")

        // 崩溃前正在跑的模块，日志里有据可查，同时把这个模块自动关掉，避免下次再崩
        try {
            val stage = EchoLog.currentStage()
            EchoLog.e(TAG, "crash stage = " + stage)
            selfHeal(stage)
        } catch (ignored: Throwable) {
        }

        // 无论是否恢复，都强制归档崩溃日志，确保用户能拿到完整堆栈
        try {
            archiveCrashLog()
        } catch (ignored: Throwable) {
        }

        if (!isMain) {
            // 子线程异常：记录后收掉这条线程即可，不让整个 App 陪葬
            try {
                Log.e(TAG, "worker thread crashed, contained: " + threadName, throwable)
            } catch (ignored: Throwable) {
            }
            return
        }

        // 主线程异常：能不能救，取决于短时间内崩了几次
        if (!allowRecover()) {
            EchoLog.e(TAG, "too many crashes in window, hand over to system")
            EchoLog.flushSync()
            previous?.uncaughtException(thread, throwable)
            return
        }

        // 先做最小限度的清理（此刻还在主线程上，能直接调）
        try {
            Snapshot.stopPlayback()
        } catch (ignored: Throwable) {
        }

        // 主线程的 Looper 已经停了，post 一条恢复动作，然后把 Looper 续上
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    notifyUser(context)
                } catch (ignored: Throwable) {
                }
            }
        } catch (ignored: Throwable) {
        }

        EchoLog.i(TAG, "main looper restarting after fatal")
        EchoLog.flushSync()

        while (true) {
            try {
                Looper.loop()
                // 正常退出说明有人在退出 Looper，直接结束
                break
            } catch (t: Throwable) {
                EchoLog.crash(TAG, "crash inside restarted looper", t)
                EchoLog.flushSync()
                if (!allowRecover()) {
                    previous?.uncaughtException(thread, t)
                    return
                }
            }
        }
    }

    /**
     * 一次崩溃之后的自愈：把刚刚出事的那个增强模块关掉，
     * 下次进播放不会再踩同一个坑。用户想再开，设置里随时能打开。
     */
    private fun selfHeal(stage: String) {
        try {
            when {
                stage.startsWith("danmu") -> {
                    DanmuHelper.setOpen(false)
                    EchoLog.w(TAG, "self-heal: 弹幕已自动关闭（设置 → 播放设置 可重新打开）")
                }
                stage.startsWith("preload") -> {
                    KV.put(HawkConfig.PRELOAD_NEXT_EPISODE, false)
                    EchoLog.w(TAG, "self-heal: 下一集预加载已关闭")
                }
                stage.startsWith("prewarm") -> {
                    KV.put(HawkConfig.KERNEL_PREWARM, false)
                    EchoLog.w(TAG, "self-heal: 播放内核预热已关闭")
                }
                stage.startsWith("osd") || stage.startsWith("badge") -> {
                    KV.put(HawkConfig.MEDIA_BADGES_SHOW, false)
                    EchoLog.w(TAG, "self-heal: 播放器角标显示已关闭")
                }
                stage.startsWith("progress") -> {
                    EchoLog.w(TAG, "self-heal: 进度保存模块异常")
                }
                else -> EchoLog.w(TAG, "self-heal: 无对应模块, stage=" + stage)
            }
        } catch (th: Throwable) {
            EchoLog.e(TAG, "self-heal failed", th)
        }
    }

    /** 把崩溃那一刻的日志另存一份，文件名带时间，用户一眼就能找到哪份是出事那次 */
    private fun archiveCrashLog() {
        try {
            val dir = EchoLog.dir() ?: return
            val src = EchoLog.currentFile() ?: return
            if (!src.exists()) return
            val stamp = java.text.SimpleDateFormat("MMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
            val target = java.io.File(dir, "crash-" + stamp + ".log")
            src.copyTo(target, overwrite = true)
            EchoLog.w(TAG, "崩溃日志已另存: " + target.absolutePath)
        } catch (th: Throwable) {
            EchoLog.e(TAG, "archive crash log failed", th)
        }
    }

    private fun allowRecover(): Boolean {        val now = System.currentTimeMillis()
        if (now - windowStart > RECOVER_WINDOW_MS) {
            windowStart = now
            recoverCount = 0
        }
        recoverCount++
        return recoverCount <= MAX_RECOVER_IN_WINDOW
    }

    private fun notifyUser(context: Context) {
        val text = "已拦截一次播放异常，已记录日志（设置 → 运行日志）"
        try {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        } catch (ignored: Throwable) {
        }
    }

    @JvmStatic
    fun lastCrash(): String? = lastCrashSummary

    @JvmStatic
    fun clearLastCrash() {
        lastCrashSummary = null
    }

    /** 崩溃那一刻的状态快照，写日志用 */
    object Snapshot {

        fun describe(): String {
            val sb = StringBuilder()
            sb.append("\nactivity=").append(currentActivityName())
            try {
                val engine = com.github.tvbox.osc.player.PlaybackService.peek()
                if (engine == null) {
                    sb.append("\nplayback engine = null")
                } else {
                    val vv = engine.player()
                    sb.append("\nplayback: state=").append(vv.playState)
                    sb.append(" pos=").append(vv.currentPosition)
                    sb.append(" dur=").append(vv.duration)
                    sb.append(" url=").append(vv.currentUrl ?: "-")
                    val vod = engine.controller().vod()
                    sb.append("\nvod: ").append(vod?.name ?: "-")
                    sb.append(" flag=").append(vod?.playFlag ?: "-")
                    sb.append(" index=").append(vod?.playIndex ?: -1)
                }
            } catch (th: Throwable) {
                sb.append("\nplayback state unreadable: ").append(th.message)
            }
            try {
                val rt = Runtime.getRuntime()
                sb.append("\nmemory: used=").append((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024)
                    .append("MB max=").append(rt.maxMemory() / 1024 / 1024).append("MB")
            } catch (ignored: Throwable) {
            }
            return sb.toString()
        }

        fun currentActivityName(): String {
            return try {
                val app = AppManager.getInstance()
                if (!app.isActivity()) "-" else app.currentActivity().javaClass.name
            } catch (ignored: Throwable) {
                "-"
            }
        }

        fun stopPlayback() {
            try {
                val engine = com.github.tvbox.osc.player.PlaybackService.peek() ?: return
                engine.releasePlayer()
                EchoLog.i(TAG, "playback released after crash")
            } catch (th: Throwable) {
                EchoLog.e(TAG, "release playback failed", th)
            }
        }
    }
}
