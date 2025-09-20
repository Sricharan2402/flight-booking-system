package com.flightbooking.utils

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

class CachePerformanceMonitor {

    private val logger = LoggerFactory.getLogger(CachePerformanceMonitor::class.java)

    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val totalCacheTime = AtomicLong(0)
    private val totalDbTime = AtomicLong(0)

    fun recordCacheHit(responseTimeMs: Long) {
        cacheHits.incrementAndGet()
        totalCacheTime.addAndGet(responseTimeMs)
        logger.debug("CACHE_HIT recorded response_time_ms={} total_hits={}", responseTimeMs, cacheHits.get())
    }

    fun recordCacheMiss(responseTimeMs: Long) {
        cacheMisses.incrementAndGet()
        totalDbTime.addAndGet(responseTimeMs)
        logger.debug("CACHE_MISS recorded response_time_ms={} total_misses={}", responseTimeMs, cacheMisses.get())
    }

    fun getMetrics(): CacheMetrics {
        val hits = cacheHits.get()
        val misses = cacheMisses.get()
        val total = hits + misses

        return CacheMetrics(
            cacheHits = hits,
            cacheMisses = misses,
            totalRequests = total,
            hitRatio = if (total > 0) (hits.toDouble() / total) * 100 else 0.0,
            averageCacheTimeMs = if (hits > 0) totalCacheTime.get().toDouble() / hits else 0.0,
            averageDbTimeMs = if (misses > 0) totalDbTime.get().toDouble() / misses else 0.0,
            totalCacheTimeMs = totalCacheTime.get(),
            totalDbTimeMs = totalDbTime.get()
        )
    }

    fun reset() {
        cacheHits.set(0)
        cacheMisses.set(0)
        totalCacheTime.set(0)
        totalDbTime.set(0)
        logger.info("CACHE_MONITOR_RESET")
    }

    inline fun <T> monitorCacheOperation(
        isCacheHit: Boolean,
        operation: () -> T
    ): T {
        val result: T
        val timeMs = measureTimeMillis {
            result = operation()
        }

        if (isCacheHit) {
            recordCacheHit(timeMs)
        } else {
            recordCacheMiss(timeMs)
        }

        return result
    }
}

data class CacheMetrics(
    val cacheHits: Long,
    val cacheMisses: Long,
    val totalRequests: Long,
    val hitRatio: Double,
    val averageCacheTimeMs: Double,
    val averageDbTimeMs: Double,
    val totalCacheTimeMs: Long,
    val totalDbTimeMs: Long
) {
    fun logSummary() {
        val logger = LoggerFactory.getLogger(CacheMetrics::class.java)
        logger.info("📊 CACHE_METRICS_SUMMARY: " +
                "hits={} misses={} total={} hit_ratio={}% " +
                "avg_cache_time={}ms avg_db_time={}ms",
            cacheHits, cacheMisses, totalRequests,
            String.format("%.1f", hitRatio),
            String.format("%.1f", averageCacheTimeMs),
            String.format("%.1f", averageDbTimeMs))
    }
}