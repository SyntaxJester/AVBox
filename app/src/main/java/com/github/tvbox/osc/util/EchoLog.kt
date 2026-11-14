package com.github.tvbox.osc.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 全量运行日志。
 *
 * 目标：应用里发生的每一件关键事都留档，崩溃时最后几行也不会丢。
 *
 * 特性：
 *  - 每写一行立刻落盘（同步 flush），进程被杀也不会丢上下文；
 *  - 日志放在用户可以直接拿到的目录（Download/AVBox_logs），不用 root、不用连电脑；
 *  - 内存里同时保留最近 4000 行，应用内的"运行日志"页面直接读内存，秒开；
 *  - 单文件 8MB 自动换新，最多保留 12 个文件，不会把手机撑爆。
 */
object EchoLog {

    private const val DIR_NAME = "AVBox_logs"
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024
    private const val MAX_FILES = 12
    private const val BUFFER_LINES = 4000
    private const val MAX_LINES_PER_SEC = 400

    private val lock = Any()
    private val buffer = ArrayDeque<String>(BUFFER_LINES)

    private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val nameFmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    @Volatile
    private var logDir: File? = null

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var writer: OutputStreamWriter? = null

    @Volatile
    private var started = false

    @Volatile
    private var startedAt: Long = 0L

    @JvmStatic
    fun init(context: Context) {
        if (started) return
        synchronized(lock) {
            if (started) return
            started = true
            startedAt = System.currentTimeMillis()
            try {
                val dir = resolveDir(context)
                logDir = dir
                if (dir != null && !dir.exists()) dir.mkdirs()
                rotateIfNeeded()
                openWriter()
            } catch (th: Throwable) {
                // 日志系统自身绝对不能把 App 拖垮
            }
        }
        section("APP START")
        write("I", "EchoLog", deviceInfo(context))
        write("I", "EchoLog", "log file = " + (logFile?.absolutePath ?: "unavailable"))
    }

    /** 日志所在目录（用户可直接在文件管理器里找到） */
    @JvmStatic
    fun dir(): File? = logDir

    @JvmStatic
    fun currentFile(): File? = logFile

    @JvmStatic
    fun isReady(): Boolean = started

    @JvmStatic
    fun i(tag: String, msg: String) = write("I", tag, msg)

    @JvmStatic
    fun w(tag: String, msg: String) = write("W", tag, msg)

    @JvmStatic
    fun e(tag: String, msg: String) = write("E", tag, msg)

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable?) {
        write("E", tag, msg + if (tr == null) "" else "\n" + stackOf(tr))
    }

    @JvmStatic
    fun crash(tag: String, msg: String, tr: Throwable?) {
        writeRawNoThrottle(timeFmt.format(Date()) + " [E] [" + tag + "] " + msg + "\n" + stackOf(tr))
        flushSync()
    }

    /** 打一条醒目的分隔线，方便在长日志里定位阶段 */
    @JvmStatic
    fun section(name: String) {
        writeRaw("\n========== " + name + " ==========")
    }

    @JvmStatic
    fun tail(maxLines: Int): String {
        synchronized(lock) {
            if (buffer.isEmpty()) return ""
            val from = if (buffer.size <= maxLines) 0 else buffer.size - maxLines
            val sb = StringBuilder()
            var index = 0
            for (line in buffer) {
                if (index++ >= from) {
                    sb.append(line).append('\n')
                }
            }
            return sb.toString()
        }
    }

    @JvmStatic
    fun flushSync() {
        synchronized(lock) {
            try {
                writer?.flush()
            } catch (ignored: Throwable) {
            }
        }
    }

    @JvmStatic
    fun setEnabled(on: Boolean) {
        enabled = on
    }

    @JvmStatic
    fun isEnabled(): Boolean = enabled

    /** 当前正在做什么。崩溃时会被写进日志，一眼看出是哪个模块出的事。 */
    @Volatile
    private var stageName: String = "boot"

    @JvmStatic
    fun stage(name: String) {
        stageName = name
    }

    @JvmStatic
    fun currentStage(): String = stageName

    @Volatile
    private var enabled = true

    // ---------------------------------------------------------------- 内部实现

    private fun write(level: String, tag: String, msg: String) {
        if (!enabled) return
        if (!allowWrite()) return
        val line = timeFmt.format(Date()) + " [" + level + "] [" + tag + "] " + msg
        writeRaw(line)
    }

    // 播放时日志量可能很大，做个限流，避免写盘拖慢播放
    private var windowStartMs = 0L
    private var windowCount = 0
    private var droppedInWindow = 0

    private fun allowWrite(): Boolean {
        val now = System.currentTimeMillis()
        if (now - windowStartMs >= 1000L) {
            if (droppedInWindow > 0) {
                val line = timeFmt.format(Date()) + " [W] [EchoLog] throttled " + droppedInWindow + " lines"
                droppedInWindow = 0
                pushBuffer(line)
                try {
                    writer?.write(line)
                    writer?.write("\n")
                    writer?.flush()
                } catch (ignored: Throwable) {
                }
            }
            windowStartMs = now
            windowCount = 0
        }
        windowCount++
        if (windowCount > MAX_LINES_PER_SEC) {
            droppedInWindow++
            return false
        }
        return true
    }

    private fun writeRawNoThrottle(line: String) {
        synchronized(lock) {
            try {
                if (writer == null) openWriter()
                rotateIfNeeded()
                writer?.write(line)
                writer?.write("\n")
                writer?.flush()
            } catch (th: Throwable) {
                try {
                    closeWriter()
                    openWriter()
                    writer?.write(line)
                    writer?.write("\n")
                    writer?.flush()
                } catch (ignored: Throwable) {
                }
            }
        }
    }

    private fun writeRaw(line: String) {
        synchronized(lock) {
            pushBuffer(line)
            try {
                if (writer == null) return
                rotateIfNeeded()
                writer?.write(line)
                writer?.write("\n")
                writer?.flush()
            } catch (th: Throwable) {
                // 写盘失败就重开一次，仍然失败也不抛
                try {
                    closeWriter()
                    openWriter()
                    writer?.write(line)
                    writer?.write("\n")
                    writer?.flush()
                } catch (ignored: Throwable) {
                }
            }
        }
    }

    private fun pushBuffer(line: String) {
        if (buffer.size >= BUFFER_LINES) {
            buffer.removeFirst()
        }
        buffer.addLast(line)
    }

    private fun resolveDir(context: Context): File? {
        // 1) 公共下载目录：用户用文件管理器就能找到，发给我最方便
        try {
            val public = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(public, DIR_NAME)
            if (dir.exists() || dir.mkdirs()) return dir
        } catch (ignored: Throwable) {
        }
        // 2) 应用外部目录：Android/data/<包名>/files/AVBox_logs
        try {
            val ext = context.getExternalFilesDir(null)
            if (ext != null) {
                val dir = File(ext, DIR_NAME)
                if (dir.exists() || dir.mkdirs()) return dir
            }
        } catch (ignored: Throwable) {
        }
        // 3) 内部目录，最后的兜底
        return try {
            File(context.filesDir, DIR_NAME)
        } catch (ignored: Throwable) {
            null
        }
    }

    private fun openWriter() {
        try {
            val dir = logDir ?: return
            if (!dir.exists()) dir.mkdirs()
            var target = logFile
            if (target == null || shouldRotate(target)) {
                target = newFile(dir)
                logFile = target
            }
            writer = OutputStreamWriter(FileOutputStream(target, true), StandardCharsets.UTF_8)
        } catch (ignored: Throwable) {
            writer = null
        }
    }

    private fun rotateIfNeeded() {
        val current = logFile ?: return
        if (!shouldRotate(current)) return
        try {
            closeWriter()
            prune(dir())
            logFile = newFile(requireNotNull(logDir))
            writer = OutputStreamWriter(FileOutputStream(logFile, true), StandardCharsets.UTF_8)
        } catch (ignored: Throwable) {
        }
    }

    private fun shouldRotate(file: File): Boolean = try {
        file.exists() && file.length() >= MAX_FILE_BYTES
    } catch (ignored: Throwable) {
        false
    }

    private fun newFile(dir: File): File {
        val name = "avbox-" + nameFmt.format(Date()) + ".log"
        return File(dir, name)
    }

    private fun prune(dir: File?) {
        if (dir == null) return
        try {
            val files = dir.listFiles { f -> f != null && f.isFile && f.name.startsWith("avbox-") }
                ?.sortedBy { it.lastModified() } ?: return
            if (files.size <= MAX_FILES) return
            for (i in 0 until files.size - MAX_FILES) {
                files[i].delete()
            }
        } catch (ignored: Throwable) {
        }
    }

    private fun closeWriter() {
        try {
            writer?.flush()
            writer?.close()
        } catch (ignored: Throwable) {
        }
        writer = null
    }

    private fun stackOf(tr: Throwable?): String {
        if (tr == null) return "(no stack)"
        val sw = java.io.StringWriter()
        tr.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString()
    }

    private fun deviceInfo(context: Context): String {
        val sb = StringBuilder()
        sb.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        sb.append(" | android=").append(Build.VERSION.RELEASE)
        sb.append(" (sdk ").append(Build.VERSION.SDK_INT).append(')')
        sb.append(" | abi=").append(Build.SUPPORTED_ABIS.joinToString(","))
        try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            sb.append(" | app=").append(pi.versionName).append(" (").append(pi.versionCode).append(')')
        } catch (ignored: Throwable) {
        }
        val rt = Runtime.getRuntime()
        sb.append(" | heapMax=").append(rt.maxMemory() / 1024 / 1024).append("MB")
        sb.append(" | uptime=").append(System.currentTimeMillis() - startedAt)
        return sb.toString()
    }
}
