package com.github.tvbox.osc.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// 与 WindowSize.LARGE_SCREEN_MIN_WIDTH_DP 同值，放 util 避免 util→ui 依赖
internal const val TABLET_MIN_WIDTH_DP = 600

internal const val VALUE_LAYOUT_HORIZONTAL = "horizontal"

internal const val VALUE_LAYOUT_VERTICAL = "vertical"

@Suppress("UNUSED_PARAMETER")
internal fun resolveHomeLayoutVertical(stored: String?, smallestWidthDp: Int): Boolean = when (stored) {
    VALUE_LAYOUT_HORIZONTAL -> false
    VALUE_LAYOUT_VERTICAL -> true
    // 默认“普通”模式（竖版海报）；只有用户主动选了“沉浸”才走横版
    else -> true
}

object HomeSettings {

    enum class HomeLayout { Horizontal, Vertical }

    private const val KEY_LAYOUT = "home_layout"

    private val defaultLayout: HomeLayout =
        if (resolveHomeLayoutVertical(null, smallestWidthDp())) HomeLayout.Vertical else HomeLayout.Horizontal

    private val mutableLayout = MutableStateFlow(current())

    val layoutFlow: StateFlow<HomeLayout> = mutableLayout

    fun current(): HomeLayout = when (KV.get(KEY_LAYOUT, "")) {
        VALUE_LAYOUT_HORIZONTAL -> HomeLayout.Horizontal
        VALUE_LAYOUT_VERTICAL -> HomeLayout.Vertical
        else -> defaultLayout
    }

    fun setLayout(layout: HomeLayout) {
        KV.put(
            KEY_LAYOUT,
            if (layout == HomeLayout.Vertical) VALUE_LAYOUT_VERTICAL else VALUE_LAYOUT_HORIZONTAL,
        )
        mutableLayout.value = layout
    }

    private fun smallestWidthDp(): Int =
        AppContextHolder.context()?.resources?.configuration?.smallestScreenWidthDp ?: 0

    // 首页站点/订阅源列表：双列(true) / 单列(false)，默认双列
    private const val KEY_SOURCE_GRID = "source_list_grid"

    private val mutableSourceGrid = MutableStateFlow(sourceGrid())

    val sourceGridFlow: StateFlow<Boolean> = mutableSourceGrid

    fun sourceGrid(): Boolean {
        val stored: String = KV.get(KEY_SOURCE_GRID, "1")
        return stored != "0"
    }

    fun setSourceGrid(grid: Boolean) {
        KV.put(KEY_SOURCE_GRID, if (grid) "1" else "0")
        mutableSourceGrid.value = grid
    }
}
