package com.music.bitchord

import com.music.bitchord.data.mapBoundedConcurrent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LocalMediaScanConcurrencyTest {
    @Test
    fun boundedMapRunsMetadataWorkConcurrentlyAndPreservesOrder() = runBlocking {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)

        val result = (0 until 12).toList().mapBoundedConcurrent(concurrency = 4) { value ->
            val now = active.incrementAndGet()
            peak.updateAndGet { previous -> maxOf(previous, now) }
            try {
                delay(25)
                value * 10
            } finally {
                active.decrementAndGet()
            }
        }

        assertEquals((0 until 12).map { it * 10 }, result)
        assertTrue("Expected metadata reads to overlap", peak.get() > 1)
        assertTrue("Concurrency limit must be respected", peak.get() <= 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundedMapRejectsNonPositiveConcurrency() = runBlocking {
        listOf(1).mapBoundedConcurrent(concurrency = 0) { it }
    }
}
