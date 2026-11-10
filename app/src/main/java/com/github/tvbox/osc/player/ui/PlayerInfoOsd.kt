package com.github.tvbox.osc.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.tvbox.osc.R
import com.github.tvbox.osc.player.state.PlayerActions
import com.github.tvbox.osc.player.state.PlayerUiState
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV

private const val OSD_BG_ALPHA = 0.82f
private const val OSD_LABEL_ALPHA = 0.6f
private const val OSD_VALUE_MAX_LINES = 3
private const val OSD_BADGE_BG = 0xFF3F51B5
private val OSD_SAFE_INSET = 40.dp

@Composable
fun BoxScope.PlayerInfoOsd(state: PlayerUiState, actions: PlayerActions, maxWidth: Dp) {
    val visible = state.infoOsdVisible
    val edge = playerEdgePadding()
    val corner = playerDim(R.dimen.vs_8)
    val padH = playerDim(R.dimen.vs_12)
    val padV = playerDim(R.dimen.vs_10)
    val gap = playerDim(R.dimen.vs_5)
    val columnGap = playerDim(R.dimen.vs_30)
    val labelGap = playerDim(R.dimen.vs_15)
    val lineSize = playerTextSize(R.dimen.ts_18)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = LocalTextStyle.current
    val leftLabels = state.infoOsdLeft.map { it.first }
    val rightLabels = state.infoOsdRight.map { it.first }
    val leftLabelWidth = remember(measurer, density, lineSize, baseStyle, leftLabels) {
        with(density) { labelWidthPx(leftLabels, measurer, baseStyle, lineSize).toDp() }
    }
    val rightLabelWidth = remember(measurer, density, lineSize, baseStyle, rightLabels) {
        with(density) { labelWidthPx(rightLabels, measurer, baseStyle, lineSize).toDp() }
    }
    val safeStart = if (landscape) OSD_SAFE_INSET else 0.dp
    val safeTop = if (landscape) 0.dp else OSD_SAFE_INSET
    val contentLimit = (maxWidth - edge - safeStart - padH).coerceAtLeast(0.dp)
    val groupLimit = if (landscape) {
        ((contentLimit - columnGap).coerceAtLeast(0.dp) / 2f)
    } else {
        contentLimit
    }
    val tapModifier = if (visible) {
        Modifier.pointerInput(Unit) { detectTapGestures(onTap = { actions.onInfoOsdClicked() }) }
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(end = edge)
            .widthIn(max = maxWidth - edge)
            .background(
                Color.Black.copy(alpha = OSD_BG_ALPHA),
                RoundedCornerShape(bottomStart = corner, bottomEnd = corner),
            )
            .then(tapModifier)
            .padding(start = safeStart, end = padH, top = safeTop, bottom = padV),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (landscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                OsdGroup(state.infoOsdLeft, leftLabelWidth, labelGap, gap, lineSize, groupLimit)
                OsdGroup(state.infoOsdRight, rightLabelWidth, labelGap, gap, lineSize, groupLimit)
            }
        } else {
            OsdGroup(state.infoOsdLeft, leftLabelWidth, labelGap, gap, lineSize, groupLimit)
            OsdGroup(state.infoOsdRight, rightLabelWidth, labelGap, gap, lineSize, groupLimit)
        }
        state.infoOsdFooter?.let { OsdFooterLine(it, leftLabelWidth, labelGap, lineSize, contentLimit) }
        if (state.mediaBadges.isNotEmpty()) {
            OsdMediaBadges(state.mediaBadges, lineSize, gap)
        }
    }
}

@Composable
private fun OsdMediaBadges(badges: List<com.github.tvbox.osc.player.badge.MediaBadge>, size: TextUnit, gap: Dp) {
    val textColor = Color(0xFFFAFAFA)
    val baseStyle = LocalTextStyle.current
    val badgeStyle = baseStyle.copy(color = textColor, fontSize = size)
    when (KV.get(HawkConfig.MEDIA_BADGES_DISPLAY_MODE, 0)) {
        1 -> Text(
            text = badges.joinToString(" · ") { it.name },
            style = badgeStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            badges.forEach { badge ->
                val imageUrl = badge.imageURL
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = badge.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(playerDim(R.dimen.vs_4)))
                            .padding(horizontal = playerDim(R.dimen.vs_4), vertical = playerDim(R.dimen.vs_2))
                            .height(sizeToDp(size) * 1.2f)
                            .width(sizeToDp(size) * 1.2f),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(OSD_BADGE_BG),
                                RoundedCornerShape(playerDim(R.dimen.vs_4)),
                            )
                            .padding(
                                horizontal = playerDim(R.dimen.vs_8),
                                vertical = playerDim(R.dimen.vs_3),
                            ),
                    ) {
                        Text(
                            text = badge.name,
                            style = badgeStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun sizeToDp(size: TextUnit): Dp {
    val density = LocalDensity.current
    return with(density) { size.toDp() }
}

private fun labelWidthPx(
    labels: List<String>,
    measurer: TextMeasurer,
    baseStyle: TextStyle,
    size: TextUnit,
): Int {
    if (labels.isEmpty()) return 0
    val style = baseStyle.copy(fontSize = size)
    return labels.maxOfOrNull { measurer.measure(it, style, softWrap = false).size.width } ?: 0
}

@Composable
private fun OsdGroup(
    lines: List<Pair<String, String>>,
    labelWidth: Dp,
    labelGap: Dp,
    gap: Dp,
    size: TextUnit,
    groupLimit: Dp,
) {
    if (lines.isEmpty()) return
    val valueLimit = (groupLimit - labelWidth - labelGap).coerceAtLeast(0.dp)
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        lines.forEach { line ->
            Row {
                OsdLabel(line.first, size, Modifier.width(labelWidth))
                Spacer(Modifier.width(labelGap))
                OsdValue(line.second, size, Modifier.widthIn(max = valueLimit), OSD_VALUE_MAX_LINES)
            }
        }
    }
}

@Composable
private fun OsdFooterLine(
    line: Pair<String, String>,
    labelWidth: Dp,
    labelGap: Dp,
    size: TextUnit,
    contentLimit: Dp,
) {
    val valueLimit = (contentLimit - labelWidth - labelGap).coerceAtLeast(0.dp)
    Row {
        OsdLabel(line.first, size, Modifier.width(labelWidth))
        Spacer(Modifier.width(labelGap))
        OsdValue(line.second, size, Modifier.widthIn(max = valueLimit), OSD_VALUE_MAX_LINES)
    }
}

@Composable
private fun OsdLabel(text: String, size: TextUnit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = Color.White.copy(alpha = OSD_LABEL_ALPHA),
        fontSize = size,
        maxLines = 1,
    )
}

@Composable
private fun OsdValue(text: String, size: TextUnit, modifier: Modifier = Modifier, maxLines: Int) {
    Text(
        text = text,
        modifier = modifier,
        color = Color.White,
        fontSize = size,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
