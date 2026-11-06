    fun refreshCacheSizeIfStale() {
        if (SystemClock.elapsedRealtime() - cacheSizeRefreshedAt < CACHE_SIZE_REFRESH_MIN_INTERVAL_MS) return
        refreshCacheSize()
    }

                cacheSizeText()
            }
            applyCacheSizeText(text)
            onCleared()
        }
    }