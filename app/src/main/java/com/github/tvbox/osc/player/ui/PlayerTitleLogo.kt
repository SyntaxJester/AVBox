package com.github.tvbox.osc.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.tvbox.osc.R
import com.github.tvbox.osc.player.state.PlayerUiState
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.TmdbPoster

/**
 * 播放器片名标识：把 TMDB 的艺术字片名（logo）叠在画面四角之一。
 *
 * 数据来源和详情页标题是同一个 [TmdbPoster.resolveLogo]，所以「使用 TMDB 片名图标」
 * 这一项一关，详情页和播放器这边同时收起，不会出现只关一半的情况。
 */
@Composable
internal fun BoxScope.PlayerTitleLogo(
    state: PlayerUiState,
    badgeBandHeight: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    val epoch by TmdbPoster.configEpoch.collectAsState()
    val position = remember(epoch) { TitleLogoSpec.normalize(readPosition()) }
    val always = remember(epoch) { readAlways() }
    val titleArt = remember(epoch) { TmdbPoster.titleArtEnabled() }

    val visible = TitleLogoSpec.visibleNow(
        position = position,
        titleArtEnabled = titleArt,
        always = always,
        controlsVisible = state.controlsVisible,
    )

    val vod = state.sessionVod
    val name = vod?.name?.takeIf { it.isNotBlank() }
        ?: state.title.takeIf { it.isNotBlank() }
    val year = vod?.year ?: 0
    val altName = vod?.en

    var logoPath by remember(name, year, altName, epoch) {
        mutableStateOf(runCatching { TmdbPoster.cachedLogo(name, year, altName) }.getOrNull())
    }
    LaunchedEffect(name, year, altName, epoch) {
        if (logoPath == null) {
            logoPath = runCatching { TmdbPoster.resolveLogo(name, year, altName) }
                .onFailure { LOG.e("echo-title-logo resolve failed: $it") }
                .getOrNull()
        }
    }

    val path = logoPath
    if (!visible || path.isNullOrEmpty()) return

    val top = TitleLogoSpec.isTop(position)
    val start = TitleLogoSpec.isStart(position)
    val alignment = when {
        top && start -> Alignment.TopStart
        top -> Alignment.TopEnd
        start -> Alignment.BottomStart
        else -> Alignment.BottomEnd
    }
    // 底部还有控制条和媒体徽章时，按它们实际占的高度往上让，三者不会糊在一起
    // （Arcane 也是这个思路：底边留出徽章/状态行的实际高度再往上排）
    val badgeBand = if (state.mediaBadges.isEmpty()) 0.dp else badgeBandHeight
    val logoGap = if (badgeBand > 0.dp) TITLE_LOGO_BADGE_GAP else 0.dp
    val edge = playerEdgePadding()
    val baseBottom = if (bottomInset > 0.dp) bottomInset else badgeBottomPadding()

    AsyncImage(
        model = TmdbPoster.logoUrl(path),
        contentDescription = name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .align(alignment)
            .padding(
                start = if (start) edge else 0.dp,
                end = if (!start) edge else 0.dp,
                top = if (top) playerDim(R.dimen.vs_120) else 0.dp,
                bottom = if (top) 0.dp else baseBottom + badgeBand + logoGap,
            )
            .size(width = playerDim(R.dimen.vs_160), height = playerDim(R.dimen.vs_60))
            .alpha(0.9f),
    )
}

/** 片名标识与底部徽章之间留的空隙，跟 Arcane 一样是固定一个小距离。 */
private val TITLE_LOGO_BADGE_GAP = 12.dp

private fun readPosition(): String = try {
    KV.get(HawkConfig.PLAYER_TITLE_LOGO_POSITION, HawkConfig.PLAYER_TITLE_LOGO_POSITION_DEFAULT)
} catch (e: Exception) {
    LOG.e("echo-title-logo read position failed: $e")
    HawkConfig.PLAYER_TITLE_LOGO_POSITION_DEFAULT
}

private fun readAlways(): Boolean = try {
    KV.get(HawkConfig.PLAYER_TITLE_LOGO_ALWAYS, HawkConfig.PLAYER_TITLE_LOGO_ALWAYS_DEFAULT)
} catch (e: Exception) {
    LOG.e("echo-title-logo read always failed: $e")
    HawkConfig.PLAYER_TITLE_LOGO_ALWAYS_DEFAULT
}
