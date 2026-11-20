package com.github.tvbox.osc.player.badge

import android.content.Context

import androidx.test.core.app.ApplicationProvider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 守住「一进播放设置就黑屏」这个坑。
 *
 * 之前 BadgeStore 的 prefs 字段声明在 _state 后面，而 _state 的初值要读 prefs，
 * 结果是仓库一被创建就空指针 —— 播放设置页第一行 remember 里就创建它，页面直接黑。
 * 这个用例只做一件事：不预热任何东西，直接 new 一个仓库，状态必须立刻可用。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BadgeStoreInitTest {

    @Test
    fun storeIsUsableRightAfterConstruction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = BadgeStore(context)

        val state = store.state.value
        assertTrue("订阅列表不能是空的", state.subscriptions.isNotEmpty())
        assertTrue(
            "选中的订阅必须真实存在于列表里",
            state.subscriptions.any { it.id == state.selectedId },
        )
        assertFalse("刚创建时不该处于失败态", state.failed)
        store.destroy()
    }

    @Test
    fun storeSurvivesCorruptedPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(BadgeStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(BadgeStore.KEY_SUBS, "{not-json")
            .putString(BadgeStore.KEY_SELECTED, "missing-id")
            .commit()

        val store = BadgeStore(context)
        val state = store.state.value
        assertTrue("存坏了也要退回预置订阅，不能崩", state.subscriptions.isNotEmpty())
        assertTrue(state.subscriptions.any { it.id == state.selectedId })
        store.destroy()
    }
}
