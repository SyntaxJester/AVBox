package com.github.tvbox.osc.player.badge

import android.content.Context
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads the media badge pack JSON (built-in or custom link) and matches
 * title text against filter patterns to produce badge display names.
 *
 * The JSON schema (as shipped in res/raw/media_badges.json):
 * {
 *   "groups": [{"id":"...","name":"..."}],
 *   "filters": [{"id":"...","groupId":"...","name":"...","pattern":"...","imageURL":"..."}]
 * }
 */
object MediaBadgeLoader {

    private val cache = AtomicReference<BadgePack?>(null)
    private val cacheKey = AtomicReference<String?>(null)

    data class BadgePack(
        val filters: List<BadgeFilter>,
    )

    data class BadgeFilter(
        val id: String,
        val name: String,
        val pattern: String,
        val regex: Regex,
    )

    /** Clears the cached pack so a new link takes effect on next call. */
    fun invalidate() {
        cache.set(null)
        cacheKey.set(null)
    }

    /**
     * Returns the key used for caching: either a URL or the built-in marker.
     */
    private fun selectedLinkKey(context: Context): String {
        val link = KV.get<Int>(HawkConfig.MEDIA_BADGES_SELECTED_LINK, HawkConfig.MEDIA_BADGE_LINK_1)
        val custom = KV.get<String>(HawkConfig.MEDIA_BADGES_CUSTOM_LINK, "")
        return when (link) {
            HawkConfig.MEDIA_BADGE_LINK_1 -> "https://media-badges.app.workbuddy.host/xinyue4capyplayer.json"
            HawkConfig.MEDIA_BADGE_LINK_2 -> "https://streambridge-studio-markers.vercel.app/badges-noframe.json"
            HawkConfig.MEDIA_BADGE_LINK_3 -> "https://raw.githubusercontent.com/zxxlxj/rex-m/main/xijing-club/pppboom.json"
            HawkConfig.MEDIA_BADGE_LINK_CUSTOM -> if (custom.isNotBlank()) custom else ""
            else -> "" // built-in
        }
    }

    /**
     * Synchronously returns badge names matched for [title].
     * Loads the JSON (built-in or selected link) on first use and caches it.
     */
    fun badgeNames(context: Context, title: String): List<String> {
        val key = selectedLinkKey(context)
        val cached = cache.get()
        if (cached != null && cacheKey.get() == key) {
            return match(cached, title)
        }
        // First call (or link changed): load synchronously so badges show now.
        return try {
            val pack = load(context.applicationContext, key)
            cache.set(pack)
            cacheKey.set(key)
            match(pack, title)
        } catch (e: Throwable) {
            LOG.e("media-badge load-failed: " + e.message)
            // Fall back to built-in pack if a selected link failed.
            if (key.isNotBlank()) {
                try {
                    val fallback = load(context.applicationContext, "")
                    cache.set(fallback)
                    cacheKey.set(key)
                    match(fallback, title)
                } catch (_: Throwable) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    private fun load(context: Context, key: String): BadgePack {
        val text = if (key.isBlank()) {
            readRaw(context, R.raw.media_badges)
        } else {
            fetch(key)
        }
        return parse(text)
    }

    private fun readRaw(context: Context, resId: Int): String {
        context.resources.openRawResource(resId).use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                return reader.readText()
            }
        }
    }

    private fun fetch(url: String): String {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        client.newCall(
            okhttp3.Request.Builder().url(url).build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw IllegalStateException("empty body")
        }
    }

    private fun parse(text: String): BadgePack {
        val root = JSONObject(text)
        val arr = root.optJSONArray("filters") ?: return BadgePack(emptyList())
        val out = ArrayList<BadgeFilter>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val name = o.optString("name")
            val pattern = o.optString("pattern")
            if (id.isEmpty() || name.isEmpty() || pattern.isEmpty()) continue
            try {
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                out.add(BadgeFilter(id, name, pattern, regex))
            } catch (_: Throwable) {
                // Skip invalid patterns.
            }
        }
        return BadgePack(out)
    }

    private fun match(pack: BadgePack, title: String): List<String> {
        if (title.isBlank() || pack.filters.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (f in pack.filters) {
            if (f.regex.containsMatchIn(title) && seen.add(f.name)) {
                out.add(f.name)
            }
        }
        return out
    }
}
