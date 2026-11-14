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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay

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
        PlayerSlideVisibility(
            visible = state.controlsVisible,
            fromTop = false,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBottomBar(state, actions)
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
        // 媒体徽章显示在左下角，调整位置和间距
        if (state.mediaBadges.isNotEmpty()) {
            PlayerOverlayMediaBadges(
                badges = state.mediaBadges,
                displayMode = state.badgesDisplayMode,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = playerEdgePadding(), bottom = playerDim(R.dimen.vs_140)),
            )
        }
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
    displayMode: Int,
    modifier: Modifier = Modifier,
) {
    if (displayMode == 2) return
    val items = badges.take(com.github.tvbox.osc.player.badge.BadgeMatcher.MAX_RESULTS)
        .filter { it.name.isNotBlank() || it.imageURL.isNotBlank() }
    if (items.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { badge ->
            if (displayMode == 0 && badge.imageURL.isNotBlank()) {
                MediaBadgeImage(badge)
            } else {
                MediaBadgeChip(badge)
            }
        }
    }
}

@Composable
private fun MediaBadgeImage(badge: com.github.tvbox.osc.player.badge.MediaBadge) {
    var failed by remember(badge.imageURL) { mutableStateOf(false) }
    if (failed) {
        MediaBadgeChip(badge)
        return
    }
    AsyncImage(
        model = badge.imageURL,
        contentDescription = badge.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.height(26.dp),
        onError = { failed = true },
    )
}

@Composable
private fun MediaBadgeChip(badge: com.github.tvbox.osc.player.badge.MediaBadge) {
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

    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(5.dp))
            .then(
                if (border != null) {
                    Modifier.border(0.5.dp, border, RoundedCornerShape(5.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = badge.name,
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
