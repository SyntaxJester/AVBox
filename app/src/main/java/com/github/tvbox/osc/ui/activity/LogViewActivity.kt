package com.github.tvbox.osc.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent

import androidx.compose.ui.platform.ComposeView

import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.ui.page.RuntimeLogScreen
import com.github.tvbox.osc.ui.theme.AVBoxTheme
import com.github.tvbox.osc.ui.theme.enableTransparentEdgeToEdge

/**
 * 运行与诊断页（原来的「运行日志」）。
 *
 * 页面本体在 [RuntimeLogScreen]，行为和结构照 Arcane 的运行日志搬：
 * 开关、自动滚动、复制、清空、导出，外加一屏设备/内核/订阅状态。
 */
class LogViewActivity : BaseActivity() {

    companion object {
        @JvmStatic
        fun start(context: Context) {
            try {
                val intent = Intent(context, LogViewActivity::class.java)
                if (context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ignored: Throwable) {
            }
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                RuntimeLogScreen(onNavigateBack = { finish() })
            }
        }
    }
}
