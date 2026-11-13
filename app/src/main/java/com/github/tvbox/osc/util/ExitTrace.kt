package com.github.tvbox.osc.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Environment

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 退出/崩溃取证。
 *
 * 为什么需要它：播放页"闪一下就回首页"这条路上有三类完全不同的原因
 * （页面自己 finish、进程被系统杀掉、原生崩溃），从画面上看是一样的。
 * 这里把每一次"谁让页面退出"写进日志，并在下次启动时把系统记录的
 * 上一次进程退出原因（崩溃 / ANR / 低内存）连同堆栈导出来，
 * 一次就能分辨到底是哪一种。
 */
object ExitTrace {

    private const val TRACE_NAME = "exit_trace.log"

    private const val MAX_BYTES = 512L * 1024L

    private const val CRASH_LOOKBACK_MS = 30L * 60L * 1000L

    private const val DUMP_MARKER = "exit_crash_dumped.marker"

    /** 记录一次页面退出（同时落 logcat 与 filesDir/exit_trace.log）。 */
    @JvmStatic
    fun mark(reason: String) {
        LOG.i("echo-exit $reason")
        try {
            val context = AppContextHolder.context() ?: return
            val file = File(context.filesDir, TRACE_NAME)
            if (file.length() > MAX_BYTES) {
                //noinspection ResultOfMethodCallIgnored
                file.delete()
            }
            val line = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date()) + " " + reason
            file.appendText(line + "\n")
        } catch (ignored: Throwable) {
        }
    }

    /** 上次进程是怎么没的：崩溃/ANR 就把系统留的堆栈导到文件里。 */
    @JvmStatic
    fun captureLastExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5) ?: return
            val marker = File(context.filesDir, DUMP_MARKER)
            val dumped = marker.takeIf { it.isFile }?.readText()?.trim().orEmpty()
            for (info in exits) {
                if (info == null) continue
                if (!isCrash(info.reason)) continue
                if (System.currentTimeMillis() - info.timestamp > CRASH_LOOKBACK_MS) continue
                val id = info.timestamp.toString()
                if (id == dumped) continue
                val trace = readTrace(info)
                val head = "reason=" + reasonName(info.reason) +
                    " desc=" + info.description +
                    " at=" + SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(info.timestamp))
                LOG.e("echo-crash! " + head + "\n" + trace)
                val saved = save(context, "AVBox_crash_" + id + ".txt", head + "\n\n" + trace)
                mark("crash-recorded " + head + " saved=" + saved)
                marker.writeText(id)
                return
            }
        } catch (ignored: Throwable) {
        }
    }

    private fun isCrash(reason: Int): Boolean =
        reason == ApplicationExitInfo.REASON_CRASH ||
            reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
            reason == ApplicationExitInfo.REASON_ANR

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        else -> "OTHER(" + reason + ")"
    }

    private fun readTrace(info: ApplicationExitInfo): String {
        return try {
            val stream = info.traceInputStream ?: return "no trace stream"
            stream.use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty()) "no trace content"
                else String(bytes, Charsets.UTF_8).take(64 * 1024)
            }
        } catch (th: Throwable) {
            "trace unavailable: " + th.message
        }
    }

    private fun save(context: Context, name: String, text: String): String {
        val candidates = ArrayList<File>()
        try {
            candidates.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        } catch (ignored: Throwable) {
        }
        context.getExternalFilesDir(null)?.let { candidates.add(it) }
        candidates.add(context.filesDir)
        for (dir in candidates) {
            try {
                if (!dir.isDirectory && !dir.mkdirs()) continue
                val file = File(dir, name)
                file.writeText(text)
                return file.absolutePath
            } catch (ignored: Throwable) {
                continue
            }
        }
        return "unavailable"
    }
}
