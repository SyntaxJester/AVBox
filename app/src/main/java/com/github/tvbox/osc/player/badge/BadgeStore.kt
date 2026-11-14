package com.github.tvbox.osc.player.badge

import android.content.Context

import com.github.tvbox.osc.net.OkGoHelper
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV
import com.github.tvbox.osc.util.LOG

import java.io.File
import java.io.IOException

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import okhttp3.Request

class BadgeStore(private val appContext: Context) {

    private data class Selection(val option: Int, val customUrl: String) {
        val key: String get() = option.toString() + "|" + customUrl
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(BadgePackState())
    val state: StateFlow<BadgePackState> = _state.asStateFlow()

    private var job: Job? = null
    private var loadedKey: String? = null

    fun ensureLoaded() {
        if (job?.isActive == true) return
        val selection = currentSelection()
        if (loadedKey == selection.key && _state.value.rules.isNotEmpty()) return
        reload()
    }

    fun reload() {
        val selection = currentSelection()
        loadedKey = selection.key
        job?.cancel()
        val current = _state.value
        _state.value = current.copy(loading = true, failed = false)
        job = scope.launch { load(selection) }
    }

    fun destroy() {
        job?.cancel()
        job = null
        scope.cancel()
    }

    private fun currentSelection(): Selection {
        val option = runCatching { KV.get(HawkConfig.MEDIA_BADGES_SELECTED_LINK, BadgePacks.DEFAULT_OPTION) }
            .getOrDefault(BadgePacks.DEFAULT_OPTION)
        val custom = runCatching { KV.get(HawkConfig.MEDIA_BADGES_CUSTOM_LINK, "") }.getOrDefault("")
        val safeOption = if (option in BadgePacks.OPTION_XINYUE..BadgePacks.OPTION_CUSTOM) option else BadgePacks.DEFAULT_OPTION
        return Selection(safeOption, custom?.trim().orEmpty())
    }

    private fun load(selection: Selection) {
        val remote = if (selection.option == BadgePacks.OPTION_CUSTOM) {
            selection.customUrl
        } else {
            BadgePacks.remoteUrl(selection.option).orEmpty()
        }
        val cacheFile = cacheFile(selection)
        var text: String? = null
        var source = ""

        if (remote.isNotEmpty()) {
            val cached = readText(cacheFile)
            if (cached != null && !cacheExpired(cacheFile)) {
                text = cached
                source = remote
            }
            if (text == null) {
                val fetched = fetch(remote)
                if (fetched != null) {
                    text = fetched
                    source = remote
                    writeText(cacheFile, fetched)
                }
            }
            if (text == null) {
                val stale = readText(cacheFile)
                if (stale != null) {
                    text = stale
                    source = remote
                }
            }
        }

        if (text == null) {
            val rawName = BadgePacks.localRaw(selection.option) ?: BadgePacks.fallbackRaw()
            text = readRaw(rawName)
            if (text != null) source = "raw/" + rawName
        }
        if (text == null) {
            val fallback = BadgePacks.fallbackRaw()
            text = readRaw(fallback)
            if (text != null) source = "raw/" + fallback
        }

        if (text == null) {
            LOG.e("echo-badge pack-unavailable option=" + selection.option)
            publish(BadgePackState(_state.value.version, emptyList(), "", "", false, true))
            return
        }

        val result = try {
            BadgePackParser.parse(text)
        } catch (e: Throwable) {
            LOG.e("echo-badge parse-failed source=" + source + " " + e.message)
            publish(BadgePackState(_state.value.version, emptyList(), "", source, false, true))
            return
        }

        publish(
            BadgePackState(
                version = _state.value.version + 1,
                rules = result.rules,
                packName = result.name,
                source = source,
                loading = false,
                failed = false,
            )
        )
        LOG.i("echo-badge loaded source=" + source + " rules=" + result.rules.size)
    }

    private fun publish(value: BadgePackState) {
        _state.value = value
    }

    private fun cacheFile(selection: Selection): File {
        val dir = File(appContext.filesDir, "badge_packs")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, BadgePacks.cacheFileName(selection.option, selection.customUrl) + ".json")
    }

    private fun cacheExpired(file: File): Boolean {
        val age = System.currentTimeMillis() - file.lastModified()
        return age > CACHE_TTL_MS
    }

    private fun readText(file: File): String? {
        if (!file.isFile || file.length() <= 0L || file.length() > BadgePackParser.MAX_BYTES) return null
        return runCatching { file.readBytes().toString(Charsets.UTF_8) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun writeText(file: File, text: String) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(text.toByteArray(Charsets.UTF_8))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }.onFailure { LOG.e("echo-badge cache-write-failed " + it.message) }
    }

    private fun readRaw(name: String): String? {
        val id = appContext.resources.getIdentifier(name, "raw", appContext.packageName)
        if (id == 0) return null
        return runCatching {
            appContext.resources.openRawResource(id).use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun fetch(url: String): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val client = OkGoHelper.getDefaultClient() ?: return null
        return runCatching {
            val request = Request.Builder().url(url).header("Accept", "application/json,text/plain,*/*").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP " + response.code)
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) throw IOException("empty body")
                if (body.toByteArray(Charsets.UTF_8).size > BadgePackParser.MAX_BYTES) throw IOException("pack too large")
                body
            }
        }.onFailure { LOG.e("echo-badge fetch-failed " + it.message) }.getOrNull()
    }

    private companion object {
        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
