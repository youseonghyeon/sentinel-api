package com.seonghyeon.sentinelapi.service

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class FeedbackRateLimiter(
    private val maxPerWindow: Int = 5,
    private val windowMillis: Long = 60_000L,
) {
    private data class Bucket(var windowStart: Long, var count: Int)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryAcquire(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val bucket = buckets.compute(key) { _, existing ->
            if (existing == null || now - existing.windowStart >= windowMillis) {
                Bucket(now, 1)
            } else {
                existing.count += 1
                existing
            }
        }!!
        if (buckets.size > 10_000) prune(now)
        return bucket.count <= maxPerWindow
    }

    private fun prune(now: Long) {
        buckets.entries.removeIf { now - it.value.windowStart >= windowMillis * 2 }
    }

    fun reset() {
        buckets.clear()
    }
}
