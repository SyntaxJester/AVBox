package com.github.tvbox.osc.player.ui

import android.content.res.Configuration
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.DimenRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.player.state.ParamsChoice
import com.github.tvbox.osc.player.state.PlayerActions
import com.github.tvbox.osc.player.state.PlayerUiState
import com.github.tvbox.osc.util.LOG
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PlayerOverlay(
    state: PlayerUiState,
    actions: PlayerActions,
    decodeChoice: ParamsChoice,
    gestureHandler: VideoGestureHandler,
    gestureSession: (width: Int, height: Int, screenWidth: Int, downY: Float) -> VideoGestureSession,
    onTapPending: () -> Unit,
) {
    val screenWidthPx = LocalContext.current.resources.displayMetrics.widthPixels
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .videoGestureLayer(
                handler = gestureHandler,
                sessionProvider = { size, downY ->
                    val sizeW = size.width
                    val sizeH = size.height
                    if (sizeW <= 0 || sizeH <= 0) {
                        null
                    } else {
                        gestureSession(sizeW, sizeH, screenWidthPx, downY)
                    }
                },
                onTapPending = onTapPending,
            ),
    ) {
        val iconBox = playerIconBox(maxWidth - playerEdgePadding() * 2)
        PlayerTipLayer(state)
        PlayerSlideVisibility(
            visible = state.topLeftVisible || (state.topRightVisible && !state.previewMode),
            fromTop = true,
        ) {
            PlayerTopBar(state, actions)
        }
        // 底部控制条的实际高度：徽章和片名都按它往上让，谁都不会压住进度条和按钮行。
        // 竖屏比横屏多一行动作图标，高度是变的，所以实测而不是写死。
        var bottomBarHeight by remember { mutableStateOf(0.dp) }
        val overlayDensity = LocalDensity.current
        PlayerSlideVisibility(
            visible = state.controlsVisible,
            fromTop = false,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBottomBar(
                state = state,
                actions = actions,
                modifier = Modifier.onSizeChanged {
                    val h = with(overlayDensity) { it.height.toDp() }
                    if (h != bottomBarHeight) bottomBarHeight = h
                },
            )
        }
        PlayerSlideHint(state)
        PlayerSeekHint(state)
        PlayerFadeVisibility(
            visible = state.loadingVisible && !state.tipVisible,
            enterMs = PLAYER_OSD_IN_MS,
            exitMs = if (state.tipVisible) 0 else PLAYER_OSD_OUT_MS,
        ) {
            PlayerLoadingLayer(state)
        }
        PlayerNetSpeedCenter(state)
        PlayerSideButtons(state, actions, iconBox)
        PlayerFadeVisibility(
            visible = state.infoOsdVisible,
            enterMs = PLAYER_OSD_IN_MS,
            exitMs = PLAYER_OSD_OUT_MS,
        ) {
            PlayerInfoOsd(state, actions, maxWidth)
        }
        // 媒体徽章：跟着控制条一起显隐（Arcane 的 showTransport 行为）。
        // 收起时只淡出、不撤布局，片名标识的让位高度不会跟着跳。
        var badgeBandHeight by remember { mutableStateOf(0.dp) }
        val badgeAlpha by animateFloatAsState(
            targetValue = if (state.controlsVisible) 1f else 0f,
            animationSpec = tween(if (state.controlsVisible) PLAYER_OSD_IN_MS else PLAYER_OSD_OUT_MS),
            label = "mediaBadgeAlpha",
        )
        if (state.mediaBadges.isNotEmpty()) {
            PlayerOverlayMediaBadges(
                badges = state.mediaBadges,
                sizePercent = state.badgesSizePercent,
                onBandHeightChanged = { if (it != badgeBandHeight) badgeBandHeight = it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = playerEdgePadding(),
                        bottom = overlayBottomInset(bottomBarHeight),
                    )
                    .alpha(badgeAlpha),
            )
        } else if (badgeBandHeight != 0.dp) {
            LaunchedEffect(Unit) { badgeBandHeight = 0.dp }
        }
        // 播放器片名标识（TMDB 艺术字），位置与显隐由「外观与设置」里的两项决定；
        // 底部徽章占多高、控制条占多高，片名标识就往上让多少，三者永不重叠。
        PlayerTitleLogo(
            state = state,
            badgeBandHeight = if (state.mediaBadges.isEmpty()) 0.dp else badgeBandHeight,
            bottomInset = overlayBottomInset(bottomBarHeight),
        )
        PlayerSpeedBoostHint(state)

        state.selectDialog?.let { dialogState ->
            PlayerSelectDialog(
                dialogState = dialogState,
                onDismiss = { state.selectDialog = null },
            )
        }

        state.paramsSheet?.let { sheet ->
            PlayerParamsSheet(
                sheet = sheet,
                decodeChoice = decodeChoice,
                tab = state.paramsTab,
                onTabSelected = { state.paramsTab = it },
                osdVisible = state.infoOsdVisible,
                onToggleOsd = actions::onInfoOsdClicked,
                slideFromEnd = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
                onDismiss = { state.paramsSheet = null },
            )
        }

        state.danmuSettingSheet?.let { sheet ->
            DanmuSettingSheet(sheet) { state.danmuSettingSheet = null }
        }
        state.danmuSearchSheet?.let { sheet ->
            DanmuSearchSheet(sheet) { state.danmuSearchSheet = null }
        }
        state.subtitleSheet?.let { sheet ->
            SubtitleSheet(sheet) { state.subtitleSheet = null }
        }
        state.subtitleSearchSheet?.let { sheet ->
            SubtitleSearchSheet(sheet) { state.subtitleSearchSheet = null }
        }
        state.castSheet?.let { sheet ->
            CastSheet(sheet) { state.castSheet = null }
        }
    }

    LaunchedEffect(state.seekHintVisible, state.seekHintText) {
        if (state.seekHintVisible) {
            delay(1000)
            actions.hideSeekHint()
        }
    }
    LaunchedEffect(state.slideHintVisible, state.slideHintText) {
        if (state.slideHintVisible) {
            delay(1000)
            actions.hideSlideHint()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { actions.refreshSystemInfo() }
                .onFailure { LOG.e("echo-osd-refresh-failed: $it") }
            delay(1000)
        }
    }
}

private const val PLAYER_DESIGN_WIDTH = 1280f

@Composable
private fun playerMmScale(): Float {
    val container = LocalWindowInfo.current.containerSize
    val longEdgePx = if (container.width > 0 && container.height > 0) {
        maxOf(container.width, container.height).toFloat()
    } else {
        val conf = LocalConfiguration.current
        maxOf(conf.screenWidthDp, conf.screenHeightDp) * LocalDensity.current.density
    }
    return if (longEdgePx > 0f) longEdgePx / PLAYER_DESIGN_WIDTH else 1f
}

private fun rawMm(resources: Resources, @DimenRes id: Int): Float? {
    val tv = TypedValue()
    try {
        resources.getValue(id, tv, true)
    } catch (e: Resources.NotFoundException) {
        return null
    }
    if (tv.type != TypedValue.TYPE_DIMENSION) return null
    if ((tv.data and TypedValue.COMPLEX_UNIT_MASK) != TypedValue.COMPLEX_UNIT_MM) return null
    return TypedValue.complexToFloat(tv.data)
}

@Composable
internal fun playerDim(@DimenRes id: Int): Dp {
    val res = LocalContext.current.resources
    val raw = rawMm(res, id)
    val px = if (raw != null) raw * playerMmScale() else res.getDimension(id)
    val pxInt = if (px == 0f) 0 else px.roundToInt().coerceAtLeast(1)
    return with(LocalDensity.current) { pxInt.toFloat().toDp() }
}

@Composable
internal fun playerTextSize(@DimenRes id: Int): TextUnit {
    val res = LocalContext.current.resources
    val raw = rawMm(res, id)
    val px = if (raw != null) raw * playerMmScale() else res.getDimension(id)
    val safePx = if (px.isFinite()) px.coerceAtLeast(0f) else 0f
    return with(LocalDensity.current) { safePx.toSp() }
}

@Composable
internal fun badgeBottomPadding(): Dp = playerDim(R.dimen.vs_140)

/**
 * 画面底部元素（媒体徽章、片名标识）离屏幕底边该留多少。
 *
 * 以前这里写死 [badgeBottomPadding]，但底部控制条竖屏比横屏多一行动作图标，
 * 高度超过这个固定值时，徽章就会压在进度条和按钮上。现在按控制条实测高度往上让：
 * 实测不到（还没布局完）或控制条比设计值矮，就用原来的设计值兜底，位置跟以前一致。
 */
@Composable
internal fun overlayBottomInset(controlsHeight: Dp): Dp {
    val fallback = badgeBottomPadding()
    if (controlsHeight <= 0.dp) return fallback
    return maxOf(fallback, controlsHeight + playerDim(R.dimen.vs_10))
}

@Composable
internal fun playerEdgePadding(): Dp =
    if (LocalConfiguration.current.screenWidthDp >= 600) 48.dp else 16.dp

@Composable
internal fun PlayerMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    textColor: Color = Color.White,
    @DimenRes textSizeId: Int = R.dimen.ts_20,
) {
    var pressed by remember { mutableStateOf(false) }
    val buttonModifier = modifier
        .pointerInput(onClick, onLongClick) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { onClick() },
                onLongPress = onLongClick?.let { cb -> { cb() } },
            )
        }
        .background(
            if (pressed) Color.White.copy(alpha = 0.16f) else Color.Transparent,
            RoundedCornerShape(playerDim(R.dimen.vs_5)),
        )
        .padding(horizontal = playerDim(R.dimen.vs_10), vertical = playerDim(R.dimen.vs_5))
    Text(
        text = text,
        color = textColor,
        fontSize = playerTextSize(textSizeId),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = if (pressed) FontWeight.Bold else FontWeight.Medium,
        modifier = buttonModifier,
    )
}

internal const val ICON_TO_BOX_RATIO = 0.55f

private const val PILL_MAX_ICONS = 8

@Composable
internal fun playerIconBox(availableWidth: Dp): Dp {
    val gap = playerDim(R.dimen.vs_8)
    return minOf(
        playerDim(R.dimen.vs_70),
        (availableWidth - gap * (PILL_MAX_ICONS + 1)) / PILL_MAX_ICONS,
    ).coerceAtLeast(1.dp)
}

@Composable
private fun PlayerOverlayMediaBadges(
    badges: List<com.github.tvbox.osc.player.badge.MediaBadge>,
    sizePercent: Int,
    onBandHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = badges.take(com.github.tvbox.osc.player.badge.BadgeMatcher.MAX_RESULTS)
        .filter { it.name.isNotBlank() || it.imageURL.isNotBlank() }
    // 这部片没有徽章时把占位高度清 0，
    // 片名标识就会落回原来的位置，不会被一段虚拟的空高顶着
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBandHeightChanged(0.dp) }
        return
    }

    val density = LocalDensity.current
    val percent = MediaBadgeSpec.normalize(sizePercent)
    val gap = MediaBadgeSpec.gapDp(percent).dp
    val maxWidth = MediaBadgeSpec.maxWidthDp(percent).dp

    BadgeStripFit(
        modifier = modifier.onSizeChanged {
            onBandHeightChanged(with(density) { it.height.toDp() })
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { badge ->
                if (badge.imageURL.isNotBlank()) {
                    MediaBadgeImage(badge, percent, maxWidth)
                } else {
                    MediaBadgeChip(badge, percent, maxWidth)
                }
            }
        }
    }
}

/**
 * 一整条徽章比画面还宽时，整条等比缩小（照 Arcane 的做法：不换行、不裁切）。
 */
@Composable
private fun BadgeStripFit(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(
            androidx.compose.ui.unit.Constraints(
                minWidth = 0,
                maxWidth = androidx.compose.ui.unit.Constraints.Infinity,
                minHeight = 0,
                maxHeight = androidx.compose.ui.unit.Constraints.Infinity,
            )
        )
        if (placeable == null) {
            layout(0, 0) {}
        } else {
            val available = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
            val fit = MediaBadgeSpec.fitScale(placeable.width, available)
            val width = minOf(placeable.width, available).coerceAtLeast(1)
            val height = (placeable.height * fit).roundToInt().coerceAtLeast(1)
            layout(width, height) {
                placeable.placeWithLayer(0, 0) {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = fit
                    scaleY = fit
                }
            }
        }
    }
}

@Composable
private fun MediaBadgeImage(
    badge: com.github.tvbox.osc.player.badge.MediaBadge,
    percent: Int,
    maxWidth: Dp,
) {
    val context = LocalContext.current
    // 离线图优先：订阅里下过整包图，就完全不看网络
    val offline = remember(badge.imageURL, badge.packId) {
        com.github.tvbox.osc.player.badge.BadgeImageStore.offlineFile(context, badge.packId, badge.imageURL)
    }
    if (offline != null) {
        MediaBadgeOfflineImage(badge, offline, percent, maxWidth)
        return
    }
    MediaBadgeRemoteImage(badge, percent, maxWidth)
}

/**
 * 订阅下载到本地的离线图（svg/png/webp 都走 Coil，能解 SVG）。
 */
@Composable
private fun MediaBadgeOfflineImage(
    badge: com.github.tvbox.osc.player.badge.MediaBadge,
    file: java.io.File,
    percent: Int,
    maxWidth: Dp,
) {
    var failed by remember(file) { mutableStateOf(false) }
    if (failed) {
        MediaBadgeChip(badge, percent, maxWidth)
        return
    }
    AsyncImage(
        model = file,
        contentDescription = badge.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .height(MediaBadgeSpec.heightDp(percent).dp)
            .widthIn(max = maxWidth),
        onError = { failed = true },
    )
}

/**
 * 自定义徽章包里的远程图片：原地址失败后退到 jsDelivr 镜像，再失败才退回文字。
 */
@Composable
private fun MediaBadgeRemoteImage(
    badge: com.github.tvbox.osc.player.badge.MediaBadge,
    percent: Int,
    maxWidth: Dp,
) {
    val candidates = remember(badge.imageURL) {
        com.github.tvbox.osc.player.badge.BadgeImageStore.mirrorCandidates(badge.imageURL)
    }
    var index by remember(badge.imageURL) { mutableStateOf(0) }
    if (index >= candidates.size) {
        MediaBadgeChip(badge, percent, maxWidth)
        return
    }
    AsyncImage(
        model = candidates[index],
        contentDescription = badge.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .height(MediaBadgeSpec.heightDp(percent).dp)
            .widthIn(max = maxWidth),
        onError = { index += 1 },
    )
}

@Composable
private fun MediaBadgeChip(
    badge: com.github.tvbox.osc.player.badge.MediaBadge,
    percent: Int,
    maxWidth: Dp,
) {
    val background = com.github.tvbox.osc.player.badge.BadgeColor.parse(badge.tagColor)
        ?.let { Color(it) }
        ?.takeIf { it.alpha > 0.01f }
        ?: Color.Black.copy(alpha = 0.5f)
    val foreground = com.github.tvbox.osc.player.badge.BadgeColor.parse(badge.textColor)
        ?.let { Color(it) }
        ?.takeIf { it.alpha > 0.01f }
        ?: Color.White
    val border = com.github.tvbox.osc.player.badge.BadgeColor.parse(badge.borderColor)
        ?.let { Color(it) }
        ?.takeIf { it.alpha > 0.01f }
    val corner = MediaBadgeSpec.cornerDp(percent).dp

    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .background(background, RoundedCornerShape(corner))
            .then(
                if (border != null) {
                    Modifier.border(0.5.dp, border, RoundedCornerShape(corner))
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = MediaBadgeSpec.padHorizontalDp(percent).dp,
                vertical = MediaBadgeSpec.padVerticalDp(percent).dp,
            ),
    ) {
        Text(
            text = badge.name,
            color = foreground,
            fontSize = MediaBadgeSpec.fontSizeSp(percent).sp,
            lineHeight = MediaBadgeSpec.lineHeightSp(percent).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
