package com.github.tvbox.osc.player.badge

import android.content.Context

import androidx.collection.LruCache

import kotlinx.coroutines.flow.StateFlow

object MediaBadgeLoader {

    private const val CACHE_SIZE = 128

    private val cache = LruCache<String, List<MediaBadge>>(CACHE_SIZE)

    @Volatile
    private var store: BadgeStore? = null

    fun store(context: Context): BadgeStore {
        val current = store
        if (current != null) return current
        synchronized(this) {
            val cached = store
            if (cached != null) return cached
            val created = BadgeStore(context.applicationContext)
            store = created
            return created
        }
    }

    fun state(context: Context): StateFlow<BadgePackState> = store(context).state

    fun badgeItems(context: Context, title: String?, technical: String?): List<MediaBadge> {
        val badgeStore = store(context)
        badgeStore.ensureLoaded()
        val pack = badgeStore.state.value
        if (pack.rules.isEmpty()) return emptyList()

        val key = pack.version.toString() + "\u0000" + title.orEmpty() + "\u0000" + technical.orEmpty()
        cache.get(key)?.let { return it }

        val matched = BadgeMatcher.matches(title, technical, pack.rules)
        val items = matched.map { rule ->
            MediaBadge(
                name = rule.name,
                imageURL = rule.imageURL,
                type = MediaBadge.TYPE_PACK,
                group = rule.group,
                technical = BadgeMatcher.isTechnicalGroup(rule.group, rule.category),
                textColor = rule.textColor,
                tagColor = rule.tagColor,
                borderColor = rule.borderColor,
                style = rule.style,
            )
        }
        cache.put(key, items)
        return items
    }

    fun previewItems(context: Context, sampleTitle: String, sampleTechnical: String): List<MediaBadge> =
        badgeItems(context, sampleTitle, sampleTechnical)

    fun invalidate() {
        cache.evictAll()
        store?.reload()
    }

    fun destroy() {
        store?.destroy()
        store = null
        cache.evictAll()
    }
}
