package com.music.bitchord.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore

/**
 * Runs expensive per-file work concurrently without letting a large music folder
 * create an unbounded number of simultaneous metadata reads.
 *
 * Results keep the same order as the source list, which keeps Local Music stable
 * between scans even though individual files finish at different times.
 */
internal suspend fun <T, R> List<T>.mapBoundedConcurrent(
    concurrency: Int,
    transform: suspend (T) -> R,
): List<R> {
    require(concurrency > 0) { "concurrency must be positive" }
    if (isEmpty()) return emptyList()

    val permits = Semaphore(concurrency)
    return coroutineScope {
        map { item ->
            async {
                permits.acquire()
                try {
                    transform(item)
                } finally {
                    permits.release()
                }
            }
        }.awaitAll()
    }
}

/**
 * Small coroutine-safe cache that collapses simultaneous cold requests into a
 * single load. This matters for Local Music because the phone UI and Android
 * Auto can ask for the catalog at nearly the same time after a folder changes.
 */
internal class SingleFlightCache<T> {
    private val mutex = Mutex()

    @Volatile
    private var cached: T? = null

    suspend fun getOrLoad(loader: suspend () -> T): T {
        cached?.let { return it }

        mutex.lock()
        try {
            cached?.let { return it }
            return loader().also { cached = it }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun reload(loader: suspend () -> T): T {
        mutex.lock()
        try {
            return loader().also { cached = it }
        } finally {
            mutex.unlock()
        }
    }

    fun invalidate() {
        cached = null
    }
}
