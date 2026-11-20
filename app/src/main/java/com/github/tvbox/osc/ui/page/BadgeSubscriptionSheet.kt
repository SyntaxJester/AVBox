package com.github.tvbox.osc.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.player.badge.BadgePackState
import com.github.tvbox.osc.player.badge.BadgeSubscription
import com.github.tvbox.osc.player.badge.MediaBadgeLoader
import com.github.tvbox.osc.ui.components.AVBoxAlertDialog
import com.github.tvbox.osc.ui.components.AVBoxBottomSheet
import kotlinx.coroutines.launch

/**
 * 媒体徽章订阅管理（照 Arcane 的 MediaBadgeSettings）。
 *
 * 能做的事：切换订阅、添加 HTTPS 订阅链接、删除订阅、
 * 更新订阅规则、把整包配图下载成本地离线图（可暂停、可继续、可清空）。
 */
@Composable
fun BadgeSubscriptionSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MediaBadgeLoader.store(context) }
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()

    var showAdd by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<BadgeSubscription?>(null) }

    AVBoxBottomSheet(onDismissRequest = onDismiss, title = "媒体徽章订阅") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已保存订阅 · ${state.subscriptions.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showAdd = true }, enabled = !adding) {
                Text(if (adding) "添加中…" else "＋ 添加订阅")
            }
        }

        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        state.subscriptions.forEach { sub ->
            BadgeSubscriptionRow(
                sub = sub,
                state = state,
                onSelect = { store.select(sub.id) },
                onRefresh = { store.refreshRules() },
                onDownload = { store.startImages(refresh = false) },
                onUpdateImages = { store.startImages(refresh = true) },
                onPause = { store.pauseImages() },
                onClearImages = { store.clearImages(sub.id) },
                onDelete = { pendingDelete = sub },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        if (state.failed && state.error.isNotBlank()) {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    if (showAdd) {
        TextEditDialog(
            title = "添加徽章订阅",
            initialText = "",
            onDismiss = { showAdd = false },
            onConfirm = { url ->
                if (url.isBlank()) return@TextEditDialog
                adding = true
                message = "正在读取徽章包…"
                scope.launch {
                    val result = store.addSubscription(url)
                    adding = false
                    message = result.fold(
                        onSuccess = { "已添加并启用：" + it.name },
                        onFailure = { "添加失败：" + (it.message ?: "未知错误") },
                    )
                }
                showAdd = false
            },
        )
    }

    pendingDelete?.let { target ->
        AVBoxAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除订阅？") },
            text = { Text("会删除「" + target.name + "」以及它在本机保存的离线图片。") },
            confirmButton = {
                TextButton(onClick = {
                    store.removeSubscription(target.id)
                    pendingDelete = null
                    message = "已删除：" + target.name
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun BadgeSubscriptionRow(
    sub: BadgeSubscription,
    state: BadgePackState,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onUpdateImages: () -> Unit,
    onPause: () -> Unit,
    onClearImages: () -> Unit,
    onDelete: () -> Unit,
) {
    val selected = state.selectedId == sub.id
    val download = state.download
    val running = download?.running == true && download.packId == sub.id
    val count = if (selected) state.offlineCount else 0
    val total = if (selected) state.offlineTotal else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sub.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sub.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Text(
                        text = offlineText(running, download, count, total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                TextButton(onClick = onRefresh) { Text("更新规则") }
            }
            if (selected) {
                if (running) {
                    TextButton(onClick = onPause) { Text("暂停") }
                } else if (count <= 0) {
                    TextButton(onClick = onDownload, enabled = total > 0) { Text("下载离线图") }
                } else if (count < total) {
                    TextButton(onClick = onDownload) { Text("继续下载") }
                } else {
                    TextButton(onClick = onUpdateImages) { Text("更新离线图") }
                }
            }
            if (selected && count > 0) {
                TextButton(onClick = onClearImages) { Text("清空离线图") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

private fun offlineText(
    running: Boolean,
    download: com.github.tvbox.osc.player.badge.BadgeDownloadState?,
    count: Int,
    total: Int,
): String {
    if (running && download != null) {
        val base = "正在下载 " + download.processed + "/" + download.total + " 张"
        return if (download.failures > 0) base + " · " + download.failures + " 张失败" else base
    }
    if (total <= 0) return "这个订阅没有配图"
    if (count <= 0) return "离线 0/" + total + " 张"
    val failed = download?.failures ?: 0
    return "离线 " + count + "/" + total + " 张" + if (failed > 0) " · " + failed + " 张失败" else ""
}
