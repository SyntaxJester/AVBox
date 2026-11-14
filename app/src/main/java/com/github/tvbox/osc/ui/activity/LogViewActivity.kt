package com.github.tvbox.osc.ui.activity

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.github.tvbox.osc.util.CrashGuard
import com.github.tvbox.osc.util.EchoLog
import java.io.File

/**
 * 运行日志页：把 App 里发生的事全看得到，遇到问题一键导出给我。
 */
class LogViewActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: TextView
    private lateinit var info: TextView
    private lateinit var scroll: ScrollView

    private var autoRefresh = true

    private val refreshTask = object : Runnable {
        override fun run() {
            if (autoRefresh) {
                render()
                handler.postDelayed(this, 1500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        render()
    }

    override fun onResume() {
        super.onResume()
        autoRefresh = true
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    override fun onPause() {
        super.onPause()
        autoRefresh = false
        handler.removeCallbacks(refreshTask)
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111318"))
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }

        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Button(this).apply {
            text = "返回"
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = "运行日志"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val share = Button(this).apply {
            text = "导出"
            setOnClickListener { exportLog() }
        }
        titleBar.addView(back)
        titleBar.addView(title, LinearLayout.LayoutParams(dp(0), ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleBar.addView(spacer)
        titleBar.addView(share)
        root.addView(titleBar)

        info = TextView(this).apply {
            setTextColor(Color.parseColor("#8AB4F8"))
            textSize = 11f
            setPadding(0, dp(4), 0, dp(6))
        }
        root.addView(info)

        scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.parseColor("#0A0C10"))
        }
        content = TextView(this).apply {
            setTextColor(Color.parseColor("#D5D8DE"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setTextIsSelectable(true)
        }
        scroll.addView(content)
        root.addView(scroll)

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        toolbar.addView(Button(this).apply {
            text = "刷新"
            setOnClickListener {
                render()
                scroll.fullScroll(View.FOCUS_DOWN)
            }
        })
        toolbar.addView(Button(this).apply {
            text = "跟着最新"
            setOnClickListener {
                autoRefresh = true
                handler.removeCallbacks(refreshTask)
                handler.post(refreshTask)
                toast("已开启自动刷新")
            }
        })
        root.addView(toolbar)

        return root
    }

    private fun render() {
        val text = EchoLog.tail(3000)
        val lastCrash = CrashGuard.lastCrash()
        val dir = EchoLog.dir()?.absolutePath ?: "未初始化"
        val lines = text.count { it == '\n' }
        val sb = StringBuilder()
        sb.append("日志目录: ").append(dir).append('\n')
        sb.append("当前文件: ").append(EchoLog.currentFile()?.name ?: "-")
        sb.append("   行数: ").append(lines)
        if (!lastCrash.isNullOrEmpty()) {
            sb.append("\n本次运行拦截过异常: ").append(lastCrash)
        }
        info.text = sb.toString()
        content.text = if (text.isBlank()) "（暂无日志）" else text
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun exportLog() {
        try {
            EchoLog.flushSync()
            val src = EchoLog.currentFile()
            if (src == null || !src.exists()) {
                toast("还没有日志文件")
                return
            }
            val stamp = System.currentTimeMillis()
            var target: File? = null
            // 优先放到公共下载目录，用户在文件管理器里一眼就能找到
            try {
                val download = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val f = File(download, "AVBox-log-$stamp.txt")
                src.copyTo(f, overwrite = true)
                if (f.exists() && f.length() > 0) target = f
            } catch (ignored: Throwable) {
            }
            // 没权限就退回应用自己的外部目录，一样能拷出来
            if (target == null) {
                val ext = getExternalFilesDir(null) ?: filesDir
                val f = File(ext, "AVBox-log-$stamp.txt")
                src.copyTo(f, overwrite = true)
                target = f
            }
            val path = target?.absolutePath ?: "导出失败"
            toast("已导出到 " + path)
        } catch (th: Throwable) {
            toast("导出失败: " + th.message)
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        @JvmStatic
        fun start(activity: Activity) {
            try {
                activity.startActivity(Intent(activity, LogViewActivity::class.java))
            } catch (ignored: Throwable) {
            }
        }
    }
}
