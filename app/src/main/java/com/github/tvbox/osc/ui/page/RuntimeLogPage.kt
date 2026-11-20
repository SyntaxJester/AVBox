package com.github.tvbox.osc.ui.page

import android.os.Build
import android.os.StatFs
import android.widget.Toast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.github.tvbox.osc.R
import com.github.tvbox.osc.player.PlayerHelper
import com.github.tvbox.osc.player.badge.MediaBadgeLoader
import com.github.tvbox.osc.ui.components.AppTopBarScaffold
import com.github.tvbox.osc.ui.components.SettingsCard
import com.github.tvbox.osc.ui.components.SettingsCardPosition
import com.github.tvbox.osc.ui.components.SettingsGroup
import com.github.tvbox.osc.ui.components.SettingsRow
import com.github.tvbox.osc.ui.components.SettingsSwitchRow
import com.github.tvbox.osc.ui.components.TopBarActionBox
import com.github.tvbox.osc.ui.theme.cardContainer
import com.github.tvbox.osc.util.CrashGuard
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.EchoLog
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV

import kotlinx.coroutines.delay

/**
 * 运行与诊断。
 *
 * 结构和行为照 Arcane 的 RuntimeLogSettings 搬：
 *  - 一个「开启日志」总开关，关掉就完全不再记录
 *  - 「自动滚动」开关，有新日志时自动跟到最新一行
 *  - 复制日志 / 清空日志 / 导出文件 三个动作
 *  - 日志倒序显示最后 500 行，等宽字体，按级别着色（E 红 / W 黄 / 其余常规）
 *
 * 另外加了上半部分「运行状态」：设备、系统、内核、解码、渲染、徽章订阅、
 * 内存、存储、日志文件、最近一次被拦下的异常 —— 出问题时照着这一屏说就行。
 */
private const val MAX_VISIBLE_LINES = 500

@Composable
fun RuntimeLogScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var loggingOn by remember {
        mutableStateOf(KV.get(HawkConfig.RUNTIME_LOG_ENABLED, HawkConfig.RUNTIME_LOG_ENABLED_DEFAULT))
    }
    var autoScroll by remember {
        mutableStateOf(KV.get(HawkConfig.RUNTIME_LOG_AUTOSCROLL, HawkConfig.RUNTIME_LOG_AUTOSCROLL_DEFAULT))
    }
    var lines by remember { mutableStateOf(EchoLog.lines(MAX_VISIBLE_LINES)) }
    var ticks by remember { mutableStateOf(0) }

    val badgeState by MediaBadgeLoader.store(context).state.collectAsState()

    // 日志是别处写的，这边只按变更计数追上最新的，不重复整段刷新
    LaunchedEffect(loggingOn) {
        var lastRevision = EchoLog.revision()
        var loop = 0
        while (true) {
            delay(700)
            val revision = EchoLog.revision()
            if (revision != lastRevision) {
                lastRevision = revision
                lines = EchoLog.lines(MAX_VISIBLE_LINES)
            }
            // 内存/存储这类数值没必要每 0.7 秒重算，几秒刷一次就够
            loop++
            if (loop % 5 == 0) ticks++
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(lines.size, autoScroll) {
        if (autoScroll && lines.isNotEmpty()) {
            listState.scrollToItem(lines.size - 1)
        }
    }

    val pageScroll = rememberScrollState()
    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.runtime_log_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            TopBarActionBox(
                R.drawable.ic_arrow_left,
                stringResource(R.string.common_back),
                onClick = onNavigateBack,
            )
        },
    ) { topPad, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(pageScroll)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(topPad + 8.dp))

            val diag = remember(ticks / 5, badgeState.selectedId, loggingOn) { diagnostics(context, badgeState.selectedId, badgeState.subscriptions) }

            SettingsGroup(title = stringResource(R.string.runtime_diag_group)) {
                SettingsCard(SettingsCardPosition.SINGLE) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        for (row in diag) {
                            DiagLine(row.first, row.second)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            SettingsGroup(title = stringResource(R.string.runtime_log_group)) {
                SettingsCard(SettingsCardPosition.FIRST) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.runtime_log_enable),
                        subtitle = stringResource(R.string.runtime_log_enable_subtitle),
                        leadingIconRes = R.drawable.ic_play_prewarm,
                        checked = loggingOn,
                        onCheckedChange = { checked ->
                            loggingOn = checked
                            KV.put(HawkConfig.RUNTIME_LOG_ENABLED, checked)
                            EchoLog.setEnabled(checked)
                            EchoLog.i("RuntimeLog", if (checked) "logging enabled" else "logging disabled")
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.runtime_log_autoscroll),
                        subtitle = stringResource(R.string.runtime_log_autoscroll_subtitle),
                        leadingIconRes = R.drawable.ic_play_scale,
                        checked = autoScroll,
                        onCheckedChange = { checked ->
                            autoScroll = checked
                            KV.put(HawkConfig.RUNTIME_LOG_AUTOSCROLL, checked)
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.runtime_log_copy),
                        subtitle = stringResource(R.string.runtime_log_copy_subtitle),
                        leadingIconRes = R.drawable.ic_play_cache,
                        enabled = lines.isNotEmpty(),
                        onClick = if (lines.isEmpty()) null else {
                            {
                                val text = EchoLog.tail(MAX_VISIBLE_LINES)
                                clipboard.setText(AnnotatedString(text))
                                toast(context, context.getString(R.string.runtime_log_copied))
                            }
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.MIDDLE) {
                    SettingsRow(
                        title = stringResource(R.string.runtime_log_export),
                        subtitle = stringResource(R.string.runtime_log_export_subtitle),
                        leadingIconRes = R.drawable.ic_settings_about,
                        onClick = {
                            val file = EchoLog.exportTo(context)
                            if (file == null) {
                                toast(context, context.getString(R.string.runtime_log_export_failed))
                            } else {
                                toast(context, context.getString(R.string.runtime_log_exported, file.absolutePath))
                            }
                        },
                    )
                }
                SettingsCard(SettingsCardPosition.LAST) {
                    SettingsRow(
                        title = stringResource(R.string.runtime_log_clear),
                        subtitle = stringResource(R.string.runtime_log_clear_subtitle),
                        leadingIconRes = R.drawable.ic_play_tunnel,
                        enabled = lines.isNotEmpty(),
                        onClick = if (lines.isEmpty()) null else {
                            {
                                EchoLog.clear()
                                lines = emptyList()
                                toast(context, context.getString(R.string.runtime_log_cleared))
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LogPanel(
                lines = lines,
                listState = listState,
                loggingOn = loggingOn,
            )

            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun LogPanel(
    lines: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    loggingOn: Boolean,
) {
    val file = EchoLog.currentFile()?.name ?: "-"
    val emptyText = if (loggingOn) {
        stringResource(R.string.runtime_log_empty)
    } else {
        stringResource(R.string.runtime_log_off)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.cardContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.runtime_log_count, lines.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = file,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (lines.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        itemsIndexed(lines) { _, line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                ),
                                color = lineColor(line),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun lineColor(line: String): androidx.compose.ui.graphics.Color = when {
    line.contains("[E]") -> MaterialTheme.colorScheme.error
    line.contains("[W]") -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun DiagLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun diagnostics(
    context: android.content.Context,
    selectedId: String,
    subscriptions: List<com.github.tvbox.osc.player.badge.BadgeSubscription>,
): List<Pair<String, String>> {
    val version = DefaultConfig.getAppVersionName(context) ?: "-"
    val versionCode = try {
        context.packageManager.getPackageInfo(context.packageName, 0).let { info ->
            @Suppress("DEPRECATION")
            info.versionCode
        }
    } catch (ignored: Throwable) {
        -1
    }
    val kernel = PlayerHelper.getPlayerName(KV.get(HawkConfig.PLAY_TYPE, 2))
    val render = PlayerHelper.getRenderName(KV.get(HawkConfig.PLAY_RENDER, 1))
    val decode = KV.get(HawkConfig.EXO_DECODE, "硬解码") // i18n: keep
    val badgeName = subscriptions.firstOrNull { it.id == selectedId }?.name ?: "-"

    val runtime = Runtime.getRuntime()
    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    val maxMb = runtime.maxMemory() / 1024 / 1024

    val freeGb = try {
        val stat = StatFs(context.filesDir.absolutePath)
        val bytes = stat.availableBytes
        String.format(java.util.Locale.US, "%.1f GB", bytes.toDouble() / 1024 / 1024 / 1024)
    } catch (ignored: Throwable) {
        "-"
    }

    val crash = CrashGuard.lastCrash()?.lineSequence()?.firstOrNull()?.take(120) ?: "-"
    val logFile = EchoLog.currentFile()?.name ?: "-"

    return listOf(
        context.getString(R.string.runtime_diag_app) to "$version ($versionCode)",
        context.getString(R.string.runtime_diag_device) to "${Build.MANUFACTURER} ${Build.MODEL}",
        context.getString(R.string.runtime_diag_system) to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        context.getString(R.string.runtime_diag_kernel) to kernel,
        context.getString(R.string.runtime_diag_decode) to decode,
        context.getString(R.string.runtime_diag_render) to render,
        context.getString(R.string.runtime_diag_badge) to badgeName,
        context.getString(R.string.runtime_diag_memory) to "$usedMb / $maxMb MB",
        context.getString(R.string.runtime_diag_storage) to freeGb,
        context.getString(R.string.runtime_diag_logfile) to logFile,
        context.getString(R.string.runtime_diag_lastcrash) to crash,
    )
}

private fun toast(context: android.content.Context, text: String) {
    try {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    } catch (ignored: Throwable) {
    }
}
