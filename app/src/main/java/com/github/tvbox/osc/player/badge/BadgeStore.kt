package com.github.tvbox.osc.player.badge

import android.content.Context

import com.github.tvbox.osc.net.OkGoHelper
import com.github.tvbox.osc.util.LOG

import java.io.File
import java.io.IOException

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

import okhttp3.Request

import org.json.JSONArray
import org.json.JSONObject

/**
 * 媒体徽章订阅仓库 —— 照 Arcane 的 MediaBadgeStore 搬过来的那一套：
 *
 *  - 订阅可以加、可以删、可以切换（预置在线订阅 + 任意 HTTPS JSON 徽章包）
 *  - 选中的订阅会把规则 JSON 缓存在本地（24 小时内直接用缓存，过期先拉、拉不到再用旧缓存）
 *  - 配图可以整包下载到本地离线目录，播放时优先用本地文件，彻底不依赖当时网络
 *  - 下载过程有进度（已下/总数/失败数），可以中途停、下次接着下
 *
 * 本地布局（都在 filesDir 下，卸载才清）：
 *   badge_packs/<订阅id>.json         规则缓存
 *   badge_packs/<订阅id>-images/      离线配图（文件名 = URL 的 SHA-256）
 */
class BadgeStore(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 注意：prefs 必须在 _state 之前初始化。
    // _state 的初值走 initialState() → readSubscriptions()，那里要用 prefs；
    // 顺序写反的话构造那一刻 prefs 还是 null，整个徽章仓库一创建就空指针，
    // 表现就是进「播放设置」/ 播放页直接黑屏。
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<BadgePackState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var downloadJob: Job? = null
    private var loadedKey: String? = null
    private var currentRules: List<BadgeRule> = emptyList()
    private var lastLoadAt = 0L

    // ---------------------------------------------------------------- 对外状态

    private fun initialState(): BadgePackState = runCatching {
        val subs = readSubscriptions()
        val stored = prefs.getString(KEY_SELECTED, null)
        val selected = if (stored != null && subs.any { it.id == stored }) stored else subs.first().id
        BadgePackState(subscriptions = subs, selectedId = selected)
    }.getOrElse {
        // 存的东西坏了也不能让界面崩掉：退回预置的五条在线订阅
        LOG.e("echo-badge state-init " + it.message)
        val preset = BadgeSubscriptions.all()
        BadgePackState(subscriptions = preset, selectedId = preset.first().id)
    }

    fun ensureLoaded() {
        if (loadJob?.isActive == true) return
        val current = _state.value
        if (loadedKey == current.selectedId && currentRules.isNotEmpty()) return
        // 上次失败就等一会儿再试，否则播放中每秒一次的刷新会一直发请求
        if (loadedKey == current.selectedId && current.failed &&
            System.currentTimeMillis() - lastLoadAt < RETRY_COOLDOWN_MS
        ) {
            return
        }
        reload()
    }

    fun reload() {
        val sub = currentSubscription()
        loadedKey = sub.id
        lastLoadAt = System.currentTimeMillis()
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true, failed = false, error = "")
        loadJob = scope.launch { load(sub) }
    }

    fun select(id: String) {
        val subs = _state.value.subscriptions
        if (subs.none { it.id == id }) return
        if (_state.value.selectedId == id && currentRules.isNotEmpty()) return
        prefs.edit().putString(KEY_SELECTED, id).apply()
        _state.value = _state.value.copy(selectedId = id, download = null)
        MediaBadgeLoader.invalidate()
        reload()
    }

    /** 添加（或覆盖）一条订阅：先真拉一次并解析，成功了才入库并切过去。 */
    suspend fun addSubscription(url: String): Result<BadgeSubscription> =
        withContext(Dispatchers.IO) { addSubscriptionNow(url) }

    private fun addSubscriptionNow(url: String): Result<BadgeSubscription> {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("链接要以 http:// 或 https:// 开头"))
        }
        val text = fetch(clean) ?: return Result.failure(IOException("拉取失败，检查链接或网络"))
        val parsed = runCatching { BadgePackParser.parse(text) }
            .getOrElse { return Result.failure(IllegalArgumentException(it.message ?: "这个包解析不了")) }
        if (parsed.rules.isEmpty()) return Result.failure(IllegalArgumentException("包里没有可用的徽章规则"))

        val id = BadgeSubscriptions.idOf(clean)
        val name = parsed.name.ifBlank { nameFromUrl(clean) }
        val entry = BadgeSubscription(id, name, clean)
        writeCache(id, text)

        val subs = readSubscriptions().toMutableList()
        val index = subs.indexOfFirst { it.id == id }
        if (index >= 0) subs[index] = entry else subs.add(entry)
        writeSubscriptions(subs)

        prefs.edit().putString(KEY_SELECTED, id).apply()
        _state.value = _state.value.copy(subscriptions = subs, selectedId = id, download = null)
        MediaBadgeLoader.invalidate()
        reload()
        return Result.success(entry)
    }

    /** 删除订阅：连它的规则缓存和离线图片一起删；删光了回到预置的在线订阅。 */
    fun removeSubscription(id: String) {
        if (downloadJob?.isActive == true && _state.value.download?.packId == id) pauseImages()
        var subs = readSubscriptions().filterNot { it.id == id }
        if (subs.isEmpty()) subs = BadgeSubscriptions.all()
        writeSubscriptions(subs)
        BadgeImageStore.clearOffline(appContext, id)
        cacheFile(id).delete()

        val current = prefs.getString(KEY_SELECTED, null)
        val next = if (current != null && subs.any { it.id == current }) current else subs.first().id
        prefs.edit().putString(KEY_SELECTED, next).apply()
        _state.value = _state.value.copy(subscriptions = subs, selectedId = next, download = null)
        MediaBadgeLoader.invalidate()
        reload()
    }

    /** 更新订阅规则：丢掉本地缓存重新拉一次（拉不到会回落到旧缓存）。 */
    fun refreshRules() {
        cacheFile(_state.value.selectedId).delete()
        reload()
    }

    // ---------------------------------------------------------------- 离线图片

    fun hasImages(): Boolean = currentRules.any { it.imageURL.startsWith("http") }

    fun imageStats(packId: String, rules: List<BadgeRule>): Pair<Int, Int> {
        val urls = imageUrls(rules)
        var count = 0
        for (url in urls) {
            if (BadgeImageStore.offlineFile(appContext, packId, url)?.isFile == true) count++
        }
        return count to urls.size
    }

    /**
     * 开始下载离线图片。
     *
     * @param refresh true = 全部重下（更新）；false = 只补没下过的（首次下载/继续下载）
     */
    fun startImages(refresh: Boolean) {
        if (downloadJob?.isActive == true) return
        val sub = currentSubscription()
        val urls = imageUrls(currentRules)
        if (urls.isEmpty()) {
            _state.value = _state.value.copy(
                download = BadgeDownloadState(sub.id, refresh, 0, 0, 0, false, emptyList()),
                error = "这个订阅没有配图",
            )
            return
        }

        val pending = urls.filter { url ->
            refresh || BadgeImageStore.offlineFile(appContext, sub.id, url)?.isFile != true
        }
        if (pending.isEmpty()) {
            val stats = imageStats(sub.id, currentRules)
            _state.value = _state.value.copy(
                offlineCount = stats.first,
                offlineTotal = stats.second,
                download = BadgeDownloadState(sub.id, refresh, urls.size, urls.size, 0, false, emptyList()),
                error = "",
            )
            return
        }

        val done = urls.size - pending.size
        _state.value = _state.value.copy(
            error = "",
            download = BadgeDownloadState(sub.id, refresh, done, urls.size, 0, true, pending),
        )

        downloadJob = scope.launch {
            val limit = Semaphore(MAX_PARALLEL)
            val lock = Any()
            var processed = done
            var failures = 0
            val failed = ArrayList<String>()
            try {
                coroutineScope {
                    for (url in pending) {
                        launch {
                            limit.withPermit {
                                val ok = runCatching { downloadImage(sub.id, url) }.getOrDefault(false)
                                synchronized(lock) {
                                    if (!ok) {
                                        failures++
                                        failed.add(url)
                                    }
                                    processed++
                                    publishDownload(sub.id, refresh, processed, urls.size, failures, failed, true)
                                }
                            }
                        }
                    }
                }
            } finally {
                val stats = imageStats(sub.id, currentRules)
                publishDownload(sub.id, refresh, processed, urls.size, failures, failed, false, stats)
                LOG.i("echo-badge images pack=${sub.id} done=${processed}/${urls.size} failed=$failures")
            }
        }
    }

    fun pauseImages() {
        downloadJob?.cancel()
        downloadJob = null
        val d = _state.value.download ?: return
        _state.value = _state.value.copy(download = d.copy(running = false))
    }

    fun clearImages(id: String) {
        if (_state.value.download?.running == true && _state.value.download?.packId == id) {
            pauseImages()
        }
        BadgeImageStore.clearOffline(appContext, id)
        val stats = imageStats(id, if (id == _state.value.selectedId) currentRules else emptyList())
        _state.value = _state.value.copy(
            offlineCount = stats.first,
            offlineTotal = stats.second,
            download = null,
        )
        MediaBadgeLoader.invalidate()
    }

    fun destroy() {
        loadJob?.cancel()
        downloadJob?.cancel()
        scope.cancel()
    }

    // ---------------------------------------------------------------- 内部

    private fun publishDownload(
        packId: String,
        refresh: Boolean,
        processed: Int,
        total: Int,
        failures: Int,
        failed: List<String>,
        running: Boolean,
        stats: Pair<Int, Int>? = null,
    ) {
        val current = _state.value
        val now = BadgeDownloadState(packId, refresh, processed, total, failures, running, ArrayList(failed))
        _state.value = current.copy(
            download = now,
            offlineCount = stats?.first ?: current.offlineCount,
            offlineTotal = stats?.second ?: current.offlineTotal,
        )
    }

    private fun currentSubscription(): BadgeSubscription {
        val st = _state.value
        return st.subscriptions.firstOrNull { it.id == st.selectedId }
            ?: st.subscriptions.firstOrNull()
            ?: BadgeSubscriptions.all().first()
    }

    private suspend fun load(sub: BadgeSubscription) {
        val text = readPackText(sub)
        val parsed = text?.let { runCatching { BadgePackParser.parse(it) }.getOrNull() }
        if (parsed != null && parsed.rules.isNotEmpty()) {
            currentRules = parsed.rules
            val stats = imageStats(sub.id, parsed.rules)
            _state.value = _state.value.copy(
                version = _state.value.version + 1,
                rules = parsed.rules,
                packName = sub.name,
                source = sub.source,
                loading = false,
                failed = false,
                error = "",
                offlineCount = stats.first,
                offlineTotal = stats.second,
            )
            LOG.i("echo-badge loaded pack=${sub.id} rules=${parsed.rules.size} offline=${stats.first}/${stats.second}")
            return
        }

        publishFailure(sub, if (text == null) "订阅读取失败，检查链接或网络" else "徽章包解析失败")
    }

    private fun publishFailure(sub: BadgeSubscription, message: String) {
        currentRules = emptyList()
        _state.value = _state.value.copy(
            version = _state.value.version + 1,
            rules = emptyList(),
            packName = sub.name,
            source = sub.source,
            loading = false,
            failed = true,
            error = message,
            offlineCount = 0,
            offlineTotal = 0,
        )
        LOG.e("echo-badge load-failed pack=" + sub.id + " " + message)
    }

    private suspend fun readPackText(sub: BadgeSubscription): String? {
        if (!sub.isRemote) return null
        val file = cacheFile(sub.id)
        if (file.isFile && !cacheExpired(file)) {
            readText(file)?.let { return it }
        }
        val fetched = fetch(sub.source)
        if (fetched != null) {
            writeCache(sub.id, fetched)
            return fetched
        }
        // 拉不到就退回上次成功拉到的那份，至少播放时还有徽章
        return readText(file)
    }

    private fun downloadImage(packId: String, url: String): Boolean {
        val client = OkGoHelper.getDefaultClient() ?: return false
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "image/*,*/*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP " + response.code)
                val bytes = response.body?.bytes() ?: throw IOException("empty body")
                if (bytes.isEmpty()) throw IOException("empty image")
                if (bytes.size > MAX_IMAGE_BYTES) throw IOException("image too large")
                BadgeImageStore.writeOffline(appContext, packId, url, bytes)
            }
            true
        }.onFailure { LOG.e("echo-badge image-failed " + it.message + " " + url) }.getOrDefault(false)
    }

    private fun imageUrls(rules: List<BadgeRule>): List<String> =
        rules.map { it.imageURL }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()

    // ---------------------------------------------------------------- 存储

    private fun readSubscriptions(): List<BadgeSubscription> {
        val raw = prefs.getString(KEY_SUBS, null)
        if (raw.isNullOrBlank()) {
            val seeded = BadgeSubscriptions.all()
            writeSubscriptions(seeded)
            return seeded
        }
        val parsed = runCatching {
            val array = JSONArray(raw)
            val out = ArrayList<BadgeSubscription>(array.length())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val name = o.optString("name")
                val source = o.optString("source")
                if (id.isBlank() || source.isBlank()) continue
                // 旧版本存过 “raw:xxx” 内置包，现在应用里不带内置包了，直接丢掉
                if (!source.startsWith("http://") && !source.startsWith("https://")) continue
                out.add(BadgeSubscription(id, name.ifBlank { id }, source, false))
            }
            out
        }.getOrDefault(emptyList())
        if (parsed.isEmpty()) {
            val seeded = BadgeSubscriptions.all()
            writeSubscriptions(seeded)
            return seeded
        }
        // 丢过旧内置项的话顺手把干净的列表写回去
        if (parsed.size != runCatching { JSONArray(raw).length() }.getOrDefault(parsed.size)) {
            writeSubscriptions(parsed)
        }
        return parsed
    }

    private fun writeSubscriptions(subs: List<BadgeSubscription>) {
        val array = JSONArray()
        for (sub in subs) {
            array.put(
                JSONObject()
                    .put("id", sub.id)
                    .put("name", sub.name)
                    .put("source", sub.source)
                    .put("builtin", sub.builtin)
            )
        }
        prefs.edit().putString(KEY_SUBS, array.toString()).apply()
    }

    private fun cacheDir(): File {
        val dir = File(appContext.filesDir, DIR_PACKS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cacheFile(id: String): File = File(cacheDir(), id + ".json")

    private fun cacheExpired(file: File): Boolean =
        System.currentTimeMillis() - file.lastModified() > CACHE_TTL_MS

    private fun readText(file: File): String? {
        if (!file.isFile || file.length() <= 0L || file.length() > BadgePackParser.MAX_BYTES) return null
        return runCatching { file.readBytes().toString(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun writeCache(id: String, text: String) {
        runCatching {
            val target = cacheFile(id)
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeBytes(text.toByteArray(Charsets.UTF_8))
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }.onFailure { LOG.e("echo-badge cache-write-failed " + it.message) }
    }

    private fun fetch(url: String): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val client = OkGoHelper.getDefaultClient() ?: return null
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json,text/plain,*/*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP " + response.code)
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) throw IOException("empty body")
                if (body.toByteArray(Charsets.UTF_8).size > BadgePackParser.MAX_BYTES) throw IOException("pack too large")
                body
            }
        }.onFailure { LOG.e("echo-badge fetch-failed " + it.message) }.getOrNull()
    }

    private fun nameFromUrl(url: String): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.").orEmpty()
        return host.ifBlank { "自定义订阅" }
    }

    internal companion object {
        const val PREFS_NAME = "badge_subscriptions_v2"
        const val KEY_SUBS = "subscriptions"
        const val KEY_SELECTED = "selected"
        const val DIR_PACKS = "badge_packs"
        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
        const val MAX_PARALLEL = 4
        const val RETRY_COOLDOWN_MS = 60L * 1000L
    }
}
